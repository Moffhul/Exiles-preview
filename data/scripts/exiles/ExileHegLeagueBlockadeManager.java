package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction.FGBlockadeParams;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI.GenericRaidParams;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.scripts.marketconditions.ExileHegLeagueBlockadeCollateral;

/**
 * Hegemony-Persean League SYSTEM BLOCKADES -- PASS 4 of docs/EXILES_HEG_LEAGUE_WAR.md
 * (sec 12 row 4; mechanism sec 4.2, plan sec 14.9, geography sec 2/R6, guardrails sec 5).
 *
 * <p><b>ESCALATION ONLY.</b> A blockade is the surge's signature act: it shuts a whole system's
 * jump points for weeks, which is far too blunt for the ambient border war. One attempt per side
 * fires the moment ESCALATION begins, then further attempts on {@code cadenceDays} for as long as
 * the window lasts. Blockades launched late in the window outlive the state and end on their own
 * payload timer -- the same behaviour strikes have, and deliberate: a bureaucracy that has
 * stood down does not teleport its fleets home.
 *
 * <p><b>The entry salvo is keyed on the director's cycle counter</b>, stored as a saved int, so
 * neither a save/load nor a {@code testing.forceState: 4} pin can re-arm it. A FORCED escalation
 * also has no incident parties at all, which this manager must tolerate -- unlike the retaliation
 * strike it never asks who wronged whom. The acting side is a weighted choice
 * ({@code opWeights.blockade}, Hegemony 3 : League 1), which is where sec 3.2.3's asymmetry lives
 * for this op type: the whitelisted system map is near parity at 3 : 2, so the weight is the real
 * driver of who blockades more.
 *
 * <p><b>It reads the director; the director does not know it exists</b> (CLAUDE.md sec 6), and
 * neither knows about the Ashen, Black Fleet or Omicron directors. Coordination is entirely
 * through shared read-only state: {@link ExileLiveOps}, {@link ExileUnrestGuard},
 * {@link ExileProtectedFactions} and {@link ExileHegLeagueGeography}.
 *
 * <h2>Targets come from the author's whitelist, not from the open pool</h2>
 * Unlike interdiction and strikes, a blockade may only be aimed at a system on
 * {@code blockadeWhitelistSystems} (R6b, transcribed in sec 14.9.1). Blockading a capital system
 * would mean all-out war, and several otherwise-legal systems are wrong for lore reasons the code
 * cannot infer. Ownership inside those systems is still queried LIVE (R6c), so the list names
 * acceptable war zones rather than owners, and an ownership flip moves a system between the two
 * sides' pools by itself.
 *
 * <h2>The feasibility gate SCALES UP before it gives up (D3)</h2>
 * Blockades are uncommon, so rather than skip a launch this manager first tries a bigger
 * formation: the configured {@code fleetSizes} are multiplied uniformly in 0.1 steps up to
 * {@code fleetSizesMaxMult} until the estimated strength clears the system's live defence. Only if
 * the cap still fails is the candidate rejected, and then it is LOGGED. Every FP number in this
 * pass is a guess until the measurement lines have been read -- do not tune from intuition (sec 5;
 * the {@code assaultFp: 800} lesson).
 */
public class ExileHegLeagueBlockadeManager implements EveryFrameScript {

    /** Quest/runtime override, beating the config default (the sibling convention). */
    public static final String BLOCKADES_ENABLED_KEY = "$exiles_hl_blockade_enabled";

    // --- Saved state (primitives only). Live ops are DERIVED from the intel manager. ---
    /** The director cycle whose ESCALATION entry has already fired its salvo; -1 = none yet. */
    protected int lastEscalationEntryCycle = -1;
    /** Days accumulated toward the next in-window attempt. */
    protected float daysSinceAttempt = 0f;
    /** Rolled per attempt; &lt;= 0 means "roll one". */
    protected float nextAttemptDays = -1f;

    // --- Transient runtime ---
    protected transient IntervalUtil tick;
    protected transient Random random;
    /** Condition statics are pushed from config once per load, not per tick. */
    protected transient boolean conditionTunablesApplied;

    public ExileHegLeagueBlockadeManager() {
        initTransients();
    }

    protected Object readResolve() {
        initTransients();
        return this;
    }

    protected void initTransients() {
        tick = null;
        random = new Random();
        conditionTunablesApplied = false;
    }

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public void advance(float amount) {
        if (Global.getSector() == null || Global.getSector().isPaused()) return;
        if (Global.getSector().isInNewGameAdvance()) return;

        if (tick == null) tick = new IntervalUtil(0.5f, 1f);
        if (random == null) random = new Random();

        // THE INTERVAL GATE COMES FIRST (CLAUDE.md sec 7): resolving the director walks the
        // sector's script list, so it runs at most twice a day and nothing on the per-frame path
        // allocates.
        float days = Global.getSector().getClock().convertToDays(amount);
        tick.advance(days);
        if (!tick.intervalElapsed()) return;
        float elapsed = tick.getIntervalDuration();

        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director == null) return;
        ExileHegLeagueConfig config = director.getConfig();
        if (config == null) return;

        if (!conditionTunablesApplied) {
            conditionTunablesApplied = true;
            applyConditionTunables(config);
        }

        if (!blockadesEnabled(config)) return;
        // HL-1: a disabled, delayed or idle war launches nothing (and the clock does not accrue).
        if (!director.isActive()) return;

        if (director.getState() != ExileHegLeagueDirector.STATE_ESCALATION) {
            // The clock does NOT accrue outside ESCALATION, so attempts cannot silently queue
            // behind a quiet stretch and all fire at once when the surge resumes. Same discipline
            // as the Pass-2 and Pass-3 launch clocks.
            daysSinceAttempt = 0f;
            nextAttemptDays = -1f;
            return;
        }

        int cycle = director.getCyclesCompleted();
        if (lastEscalationEntryCycle != cycle) {
            // Claim the cycle whatever happens below: the entry salvo is once per ESCALATION,
            // and a save/load or a forced state must not re-arm it.
            lastEscalationEntryCycle = cycle;
            daysSinceAttempt = 0f;
            nextAttemptDays = rollCadence(config);
            if (config.blockadeLaunchAtEscalationEntry) {
                tryLaunchEntrySalvo(director, config);
            }
            return;
        }

        daysSinceAttempt += elapsed;
        if (nextAttemptDays <= 0f) nextAttemptDays = rollCadence(config);
        if (daysSinceAttempt < nextAttemptDays) return;
        daysSinceAttempt = 0f;
        nextAttemptDays = rollCadence(config);

        tryLaunchWeighted(director, config);
    }

    protected float rollCadence(ExileHegLeagueConfig config) {
        return ExileHegLeagueDirector.jitterCadence(config.blockadeCadenceDays, random);
    }

    /** Config default, overridable at runtime by quests via the memory flag. */
    protected boolean blockadesEnabled(ExileHegLeagueConfig config) {
        return ExileHegLeagueDirector.switchEnabled(BLOCKADES_ENABLED_KEY, config.blockadeEnabled);
    }

    /** Push config onto the condition's and the blockade's statics -- the established sibling
     *  arrangement, which is what makes them player-adjustable without touching Java. */
    protected void applyConditionTunables(ExileHegLeagueConfig config) {
        ExileHegLeagueBlockadeCollateral.DURATION_DAYS = config.blockadeCollateralExpiryDays;
        ExileHegLeagueBlockade.suppressStationAttacks = config.blockadeSuppressStationAttacks;
    }

    // ------------------------------------------------------------------
    // Launch decisions
    // ------------------------------------------------------------------

    /** ESCALATION entry: ONE attempt for each side that is under its cap and has a weight. */
    protected void tryLaunchEntrySalvo(ExileHegLeagueDirector director,
                                       ExileHegLeagueConfig config) {
        attemptForSide(director, config, config.hegemony, "ESCALATION entry");
        attemptForSide(director, config, config.persean, "ESCALATION entry");
    }

    /** In-window cadence: ONE attempt by one weighted side. */
    protected void tryLaunchWeighted(ExileHegLeagueDirector director,
                                     ExileHegLeagueConfig config) {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        addSideIfAvailable(picker, config, config.hegemony);
        addSideIfAvailable(picker, config, config.persean);
        ExileHegLeagueConfig.Side side = (ExileHegLeagueConfig.Side) picker.pick();
        if (side == null) return;
        attemptForSide(director, config, side, "ESCALATION cadence");
    }

    protected void addSideIfAvailable(WeightedRandomPicker picker, ExileHegLeagueConfig config,
                                      ExileHegLeagueConfig.Side side) {
        if (side == null || side.blockadeWeight <= 0f) return;
        if (countActive(side.factionId) >= maxActivePerSide(config)) return;
        picker.add(side, side.blockadeWeight);
    }

    /** sec 3.2.4's "simultaneous blockades" is the only difference a General Engagement makes
     *  here. */
    protected int maxActivePerSide(ExileHegLeagueConfig config) {
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director != null && director.isGeneralEngagement()) {
            return config.blockadeGeneralEngagementMaxActivePerSide;
        }
        return config.blockadeMaxActivePerSide;
    }

    protected void attemptForSide(ExileHegLeagueDirector director, ExileHegLeagueConfig config,
                                  ExileHegLeagueConfig.Side side, String reason) {
        if (side == null || side.blockadeWeight <= 0f) return;
        if (countActive(side.factionId) >= maxActivePerSide(config)) return;
        if (!launchBlockade(director, config, side, reason)) {
            // HL-13 (bug sweep, 2026-10-06): a failed attempt is now always visible in the log.
            Global.getLogger(ExileHegLeagueBlockadeManager.class).info(
                    "Exiles Heg-League blockade attempt: nothing launched (actor=" + side.factionId
                            + ", reason=" + reason + ") -- no source, or every system filtered"
                            + " (whitelist, cooldown, live op, player colony, or feasibility above)");
        }
    }

    /** Live blockades for one side, derived from the intel manager rather than a bookkeeping
     *  list -- nothing to prune and nothing to keep in sync. Pass null to count both sides. */
    protected int countActive(String actorFactionId) {
        int n = 0;
        List ops = Global.getSector().getIntelManager().getIntel(ExileHegLeagueBlockade.class);
        for (int i = 0; i < ops.size(); i++) {
            ExileHegLeagueBlockade op = (ExileHegLeagueBlockade) ops.get(i);
            if (op.isEnded() || op.isEnding() || op.isAborted() || op.isFailed()) continue;
            if (actorFactionId != null
                    && !actorFactionId.equals(op.getActorFactionId())) continue;
            n++;
        }
        return n;
    }

    /** True if one of THIS director's operations -- of ANY type -- is already live in the system.
     *  Cross-op-type on purpose: two Exiles ops fighting over one system is the collision this
     *  exists for.
     *
     *  <p><b>Pass 6 needed NO change here.</b> {@code ExileHegLeagueSqueezeIntel} extends
     *  {@link ExileHegLeagueRaidIntel} and {@code getIntel(Class)} returns subclasses, so the
     *  first loop already sees a live planetary squeeze -- which is what guarantees a system
     *  blockade and a squeeze can never coexist in one system. A second scan would double-count. */
    protected boolean hasLiveOpIn(StarSystemAPI system) {
        return ExileHegLeagueGeography.hasLiveOpIn(system); // delegate since 2026-11-08 (tier 3)
    }

    // ------------------------------------------------------------------
    // Target selection
    // ------------------------------------------------------------------

    /** One feasibility-resolved candidate. Named static class, never anonymous. */
    public static class BlockadeTarget {
        public StarSystemAPI system;
        /** Uniform multiplier applied to the configured fleet sizes to clear the gate (D3). */
        public float fleetSizesMult = 1f;
        public float estimate;
        public float defense;
        public float gate;
    }

    /**
     * The whitelist, filtered and distance-weighted. Every rejection below is a spec ruling.
     *
     * <p>The distance falloff is the same shape interdiction and strikes use, written out again
     * rather than shared: the three pickers agree today and are allowed to diverge, and ruling S4
     * is explicit that a shared "consistency" helper across director pickers is rejected on the
     * merits.
     */
    protected BlockadeTarget pickTarget(ExileHegLeagueConfig config, FactionAPI actor,
                                        String victimFactionId, MarketAPI source,
                                        int basePoints) {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);

        // Cross-director dogpile check (sec 2). Read-only shared STATE, asked once per launch
        // attempt and never per frame. Null so every faction's live ops count.
        List busySystems = ExileLiveOps.getSystemsWithActiveRaids(null);

        List systems = Global.getSector().getStarSystems();
        for (int i = 0; i < systems.size(); i++) {
            StarSystemAPI system = (StarSystemAPI) systems.get(i);
            if (system == null) continue;

            // R6b: the author's whitelist is the WHOLE map for this op type.
            if (!ExileHegLeagueGeography.isBlockadeWhitelistedSystem(config, system)) continue;
            // R6a belt-and-braces. No capital is on the whitelist today; if one is ever added by
            // hand, this still refuses it.
            if (ExileHegLeagueGeography.isExemptSystem(config, system)) continue;
            // Player colonies are code-exempt by the Exiles convention: a player market in the
            // system takes the SYSTEM off the map rather than making the player a blockade victim.
            if (!Misc.getMarketsInLocation(system, Factions.PLAYER).isEmpty()) continue;
            // LIVE ownership (R6c): is there anything here this side may legally squeeze?
            if (!ExileHegLeagueGeography.hasEligibleVictimMarket(config, system,
                    victimFactionId)) continue;
            if (system.getMemoryWithoutUpdate().getBoolean(
                    ExileHegLeagueInterdictionManager.SYSTEM_COOLDOWN_KEY)) continue;
            if (hasLiveOpIn(system)) continue;
            if (busySystems.contains(system)) continue;

            BlockadeTarget candidate = resolveFeasibility(config, actor, victimFactionId,
                    system, basePoints);
            if (candidate == null) continue;

            // R6c: closer is likelier, so a side's blockades favour the war zones nearest the
            // port they sailed from.
            float distLY = Misc.getDistanceLY(
                    source.getPrimaryEntity().getLocationInHyperspace(),
                    system.getLocation());
            picker.add(candidate, 1f / (1f + distLY * config.distanceFalloff));
        }

        return (BlockadeTarget) picker.pick();
    }

    /**
     * D3 -- the gate, and the scale-up that precedes giving up.
     *
     * <p>{@code getApproximateStrengthForTotalDifficultyPoints} is vanilla's own conversion from
     * the difficulty points {@code fleetSizes} carries into the "effective strength" units
     * {@code WarSimScript.getEnemyStrength} returns, so the two sides of the comparison are
     * measured the same way. <b>Station strength is deliberately excluded</b>: gate-holders never
     * fight a station, and {@link ExileHegLeagueBlockade} actively keeps them off it.
     *
     * <p>Note the whitelist's own gotcha (sec 14.9.1 item 2): {@code getEnemyStrength} counts
     * EVERYTHING hostile to the blockader, so Isirah's pirate patrols and the Kapteyn
     * battlestation inflate that system's defence against a Hegemony blockade. The log line says
     * by how much. Do not pre-tune around it.
     *
     * @return a resolved candidate, or null if even the scaled-up formation cannot clear the gate.
     */
    protected BlockadeTarget resolveFeasibility(ExileHegLeagueConfig config, FactionAPI actor,
                                                String victimFactionId, StarSystemAPI system,
                                                int basePoints) {
        BlockadeTarget target = new BlockadeTarget();
        target.system = system;
        target.fleetSizesMult = 1f;
        target.defense = WarSimScript.getEnemyStrength(actor, system);
        target.estimate = FleetGroupIntel.getApproximateStrengthForTotalDifficultyPoints(
                actor.getId(), basePoints);
        target.gate = target.estimate * config.blockadeFeasibilityMult;

        if (config.blockadeFeasibilityMult <= 0f) {
            // 0 = vanilla behaviour, no gate at all. The knob exists for exactly that comparison.
            return target;
        }

        float maxMult = config.blockadeFleetSizesMaxMult;
        if (maxMult < 1f) maxMult = 1f;
        float mult = 1f;
        boolean passed = false;
        while (mult <= maxMult + 0.0001f) {
            int points = Math.round(basePoints * mult);
            if (points < 1) points = 1;
            float estimate = FleetGroupIntel.getApproximateStrengthForTotalDifficultyPoints(
                    actor.getId(), points);
            float gate = estimate * config.blockadeFeasibilityMult;
            target.fleetSizesMult = mult;
            target.estimate = estimate;
            target.gate = gate;
            if (gate >= target.defense) {
                passed = true;
                break;
            }
            mult += 0.1f;
        }

        if (!passed) {
            Global.getLogger(ExileHegLeagueBlockadeManager.class).info(
                    "Exiles Heg-League blockade feasibility: " + system.getBaseName()
                            + " REJECTED (defense " + (int) target.defense + " > gate "
                            + (int) target.gate + " even at fleetSizes x" + maxMult
                            + "), actor=" + actor.getId() + ", victim=" + victimFactionId);
            return null;
        }

        Global.getLogger(ExileHegLeagueBlockadeManager.class).info(
                "Exiles Heg-League blockade feasibility: " + system.getBaseName()
                        + " eligible (defense " + (int) target.defense + " <= gate "
                        + (int) target.gate + " = estimate " + (int) target.estimate + " x "
                        + config.blockadeFeasibilityMult + " at fleetSizes x"
                        + target.fleetSizesMult + "), actor=" + actor.getId()
                        + ", victim=" + victimFactionId);
        return target;
    }

    /** The biggest eligible victim market in the system: the one the patrol text names. It does
     *  NOT decide where the fleets sit -- the payload action holds the gates. */
    protected MarketAPI pickFocusMarket(ExileHegLeagueConfig config, StarSystemAPI system,
                                        String victimFactionId) {
        return ExileHegLeagueGeography.pickFocusMarket(config, system, victimFactionId); // 2026-11-08
    }

    // ------------------------------------------------------------------
    // Launch
    // ------------------------------------------------------------------

    /**
     * Build and post one blockade. Ported from {@code AshenInterdictionManager.launchBlockade()}
     * and parameterised -- copied, not called (fourth-sibling discipline, S1/S4).
     *
     * @return true if an operation was actually launched.
     */
    protected boolean launchBlockade(ExileHegLeagueDirector director, ExileHegLeagueConfig config,
                                     ExileHegLeagueConfig.Side actorSide, String reason) {
        String actorId = actorSide.factionId;
        String victimId = ExileHegLeagueConfig.HEGEMONY_ID.equals(actorId)
                ? ExileHegLeagueConfig.PERSEAN_ID : ExileHegLeagueConfig.HEGEMONY_ID;

        FactionAPI actor = Global.getSector().getFaction(actorId);
        FactionAPI victim = Global.getSector().getFaction(victimId);
        if (actor == null || victim == null) return false;
        // Belt-and-braces so an op can never launch into a peace: the director idles on a lapsed
        // premise and B3 re-asserts hostility, but this is one comparison.
        if (!actor.isHostileTo(victim)) return false;

        MarketAPI source = ExileHegLeagueGeography.pickSource(actorId, random);
        if (source == null) return false;

        int basePoints = 0;
        for (int i = 0; i < config.blockadeFleetSizes.size(); i++) {
            basePoints += ((Integer) config.blockadeFleetSizes.get(i)).intValue();
        }
        if (basePoints <= 0) return false;

        BlockadeTarget target = pickTarget(config, actor, victimId, source, basePoints);
        if (target == null) return false;
        StarSystemAPI system = target.system;

        MarketAPI focus = pickFocusMarket(config, system, victimId);
        if (focus == null) return false;

        // D3: the formation the gate actually admitted. Rounded per entry, never below one point.
        List fleetSizes = new ArrayList(); // Integer
        for (int i = 0; i < config.blockadeFleetSizes.size(); i++) {
            int base = ((Integer) config.blockadeFleetSizes.get(i)).intValue();
            int scaled = Math.round(base * target.fleetSizesMult);
            if (scaled < 1) scaled = 1;
            fleetSizes.add(new Integer(scaled));
        }

        // D7: reuse the existing per-side multiplier rather than adding a blockadeDaysMult. Same
        // fiction -- "longer op durations" is the Hegemony's whole sec 3.2.3 line.
        float payloadDays = Math.max(1f, config.blockadePayloadDays * actorSide.presenceDaysMult);

        // playerTargeted = false is the switch that makes this an NPC-vs-NPC operation (sec 12 O3):
        // it gates intel updates and keeps the player's fleet out of the defender-strength sum.
        GenericRaidParams raidParams = new GenericRaidParams(random, false);
        raidParams.factionId = actorId;
        raidParams.source = source;
        raidParams.prepDays = config.blockadePrepDays;
        raidParams.payloadDays = payloadDays;
        // FALSE, and this is LOAD-BEARING (Ashen live test 2026-08-06). makeFleetsHostile calls
        // FleetCreatorMission.triggerMakeHostile(), setting MemFlags.MEMORY_KEY_MAKE_HOSTILE --
        // the PLAYER-scoped campaign-fleet-AI family. With it true, the Ashen blockade flew past
        // the station it was blockading and hunted the player instead. Vanilla agrees:
        // KnightsOfLuddTakeoverExpedition sets it false even though it targets the player's own
        // colony. Both belligerents are already hostile to each other by faction relations, so
        // nothing is lost.
        raidParams.makeFleetsHostile = false;
        raidParams.noun = "blockade";
        raidParams.forcesNoun = "blockading forces";
        raidParams.raidParams.where = system;
        raidParams.raidParams.allowedTargets.add(focus);
        for (int i = 0; i < fleetSizes.size(); i++) {
            raidParams.fleetSizes.add(fleetSizes.get(i));
        }

        FGBlockadeParams bParams = new FGBlockadeParams();
        bParams.where = system;
        bParams.targetFaction = victimId;
        // specificMarket only decides the patrol text here: the payload action holds the gates.
        bParams.specificMarket = focus;
        bParams.accessibilityPenalty = config.blockadeAccessibilityPenalty;
        bParams.patrolText = "blockading " + system.getNameWithLowercaseTypeShort();

        ExileHegLeagueBlockade blockade = new ExileHegLeagueBlockade(
                raidParams, bParams, actorId, victimId);
        blockade.setAbortDamageFraction(config.blockadeAbortFraction);
        blockade.setBountyPerFrigate(config.blockadeBountyPerFrigate);
        blockade.setWearinessPerFpDestroyed(config.wearinessPerFpDestroyed);
        blockade.setSystemCooldownDays(config.blockadeSystemCooldownDays);
        blockade.setCollateral(config.blockadeCollateralEnabled,
                config.blockadeCollateralAccessibilityPenalty,
                config.blockadeCollateralScalesWithTier);

        // D6: hidden while preparing and travelling; the blockade reveals itself the moment it
        // holds the gates. *** setHidden() IS THE MECHANISM, not the addIntel flag *** -- that
        // boolean is forceNoMessage and only suppresses the notification (learned the hard way
        // during the Ashen build).
        if (config.blockadeHiddenUntilArrival) {
            blockade.setHidden(true);
        }
        Global.getSector().getIntelManager().addIntel(blockade, true);

        // *** THE MEASUREMENT LINE (sec 5 "instrument before tuning"). ***
        // Every number in this pass is a guess until this line has been read across a run: the
        // formation that actually sailed, the strength estimate for it, the system's measured live
        // defence, and the gate that admitted it. Read it against the "blockade HOLDING" line,
        // which prints the penalty tier the blockade went on to earn.
        Global.getLogger(ExileHegLeagueBlockadeManager.class).info(
                "Exiles Heg-League BLOCKADE LAUNCHED: " + source.getName() + " -> "
                        + system.getNameWithLowercaseType()
                        + " | actor=" + actorId + " victim=" + victimId
                        + " | fleetSizes=" + fleetSizes
                        + " mult=x" + target.fleetSizesMult
                        + " estimate=" + (int) target.estimate
                        + " defense=" + (int) target.defense
                        + " gate=" + (int) target.gate
                        + " | payloadDays=" + (int) payloadDays
                        + " prepDays=" + (int) config.blockadePrepDays
                        + " | reason=" + reason
                        + " state=" + ExileHegLeagueDirector.getStateName(director.getState())
                        + " tension=" + director.getTension()
                        + " | focus=" + focus.getName());

        director.debug("Heg-League blockade launched: " + actor.getDisplayName() + " -> "
                + system.getBaseName() + " (" + fleetSizes + ", " + (int) payloadDays + "d).");
        return true;
    }
}
