package data.scripts.exiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/**
 * Hegemony-Persean League SURGICAL STRIKES -- PASS 3 of docs/EXILES_HEG_LEAGUE_WAR.md
 * (sec 12 row 3; mechanism sec 4.3, geography sec 2/R6, guardrails sec 5, bounty sec 4.7).
 *
 * <p><b>This is what makes the RETALIATION state real.</b> Passes 1 and 2 shipped a state machine
 * that named an offender and a victim and then let the cycle run on to the summit without the
 * victim ever answering. sec 3.2.2 state 3 says the victim "launches ONE proportionate punitive op
 * against the offender", and this manager is that one op: a strike group sails from the victim's
 * space into the offender's and puts a military industry offline.
 *
 * <p><b>Two states, one op type</b> (sec 4.3: "used as the RETALIATION-state punitive op AND as
 * counter-strikes during ESCALATION"):
 * <ul>
 * <li><b>RETALIATION</b> -- exactly ONE strike per crisis cycle, mounted by the cycle's victim
 *     against the cycle's offender, sized by tension. Keyed on the director's cycle counter rather
 *     than a boolean, so a save/load or a forced state change cannot re-arm it.</li>
 * <li><b>ESCALATION</b> -- counter-strikes on a cadence, either side, capped per side. Switchable
 *     off on its own key.</li>
 * </ul>
 *
 * <p><b>It reads the director; the director does not know it exists</b> (CLAUDE.md sec 6, spec
 * sec 3.1), and neither knows about the Ashen, Black Fleet or Omicron directors. Coordination is
 * entirely through shared read-only state: {@link ExileLiveOps}, {@link ExileUnrestGuard},
 * {@link ExileProtectedFactions}, and the geography predicates in
 * {@link ExileHegLeagueGeography}.
 *
 * <h2>The feasibility gate is NOT interdiction's</h2>
 * Both are the same five-line idiom and they are deliberately kept apart (ruling S4, Ashen doc
 * sec 14.6 -- the differences between directors' gates are playtest-earned and merging them
 * invites a later "consistency fix" that silently undoes one). The difference here is real:
 * interdiction hunts shipping and never fights a station, so its gate EXCLUDES station strength;
 * a strike has to go through the station, so its gate INCLUDES it. Same instrumentation
 * discipline: every FP number in this pass is a guess until the measurement line has been read.
 */
public class ExileHegLeagueStrikeManager implements EveryFrameScript {

    /** Quest/runtime override, beating the config default (the sibling convention). */
    public static final String STRIKES_ENABLED_KEY = "$exiles_hl_strike_enabled";

    // --- Saved state (primitives only) ---
    /** Days accumulated toward the next ESCALATION counter-strike attempt. */
    protected float daysSinceEscalationStrike = 0f;
    protected float nextEscalationStrikeDays = -1f;

    // --- Transient runtime ---
    protected transient IntervalUtil tick;
    protected transient Random random;
    /** Pass 6: the planetary-blockade condition's statics are pushed from config once per load,
     *  not per tick. This manager is the squeeze's only launcher, and the only Heg-League script
     *  with a tick that has any business owning that push. */
    protected transient boolean squeezeConditionTunablesApplied;

    public ExileHegLeagueStrikeManager() {
        initTransients();
    }

    protected Object readResolve() {
        initTransients();
        return this;
    }

    protected void initTransients() {
        tick = null;
        random = new Random();
        squeezeConditionTunablesApplied = false;
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

        // PASS 6: push the squeeze condition's tunables once per load, BEFORE the enabled gate.
        // A condition restored from a save has to read config values whether or not strikes are
        // currently switched on, because its apply() re-runs on every economy reapply.
        if (!squeezeConditionTunablesApplied) {
            squeezeConditionTunablesApplied = true;
            ExileHegLeagueSqueezeManager.applyConditionTunables(config);
        }

        if (!strikesEnabled(config)) return;
        // HL-1: a disabled, delayed or idle war launches nothing (and the clock does not accrue).
        if (!director.isActive()) return;

        int state = director.getState();

        // The ESCALATION clock does NOT accrue outside ESCALATION, so counter-strikes cannot
        // silently queue behind a summit and all fire at once when the surge resumes. Same
        // discipline as the Pass-2 launch clock.
        if (state != ExileHegLeagueDirector.STATE_ESCALATION) {
            daysSinceEscalationStrike = 0f;
        }

        if (state == ExileHegLeagueDirector.STATE_RETALIATION) {
            tryLaunchRetaliation(director, config);
            return;
        }

        if (state == ExileHegLeagueDirector.STATE_ESCALATION) {
            if (!config.strikeEscalationCounterStrikes) return;
            daysSinceEscalationStrike += elapsed;
            if (nextEscalationStrikeDays <= 0f) {
                nextEscalationStrikeDays = rollEscalationCadence(config);
            }
            if (daysSinceEscalationStrike < nextEscalationStrikeDays) return;
            daysSinceEscalationStrike = 0f;
            nextEscalationStrikeDays = rollEscalationCadence(config);
            tryLaunchCounterStrike(director, config);
            return;
        }

        // Every other state launches nothing.
    }

    protected float rollEscalationCadence(ExileHegLeagueConfig config) {
        float cadence = config.strikeEscalationCadenceDays;
        return ExileHegLeagueDirector.jitterCadence(cadence, random);
    }

    /** Config default, overridable at runtime by quests via the memory flag. */
    protected boolean strikesEnabled(ExileHegLeagueConfig config) {
        return ExileHegLeagueDirector.switchEnabled(STRIKES_ENABLED_KEY, config.strikeEnabled);
    }

    // ------------------------------------------------------------------
    // RETALIATION -- ONE op per cycle (sec 3.2.2 state 3)
    // ------------------------------------------------------------------

    /**
     * The victim answers.
     *
     * <p><b>Launch is at state ENTRY, not at the end of the lag.</b> Conservative reading, flagged
     * in the sec 14.4 build log: the RETALIATION dwell already IS the per-side response lag
     * (Hegemony 20d, League 10d -- confirmed live in the Pass-2 long run), and the op then has to
     * assemble and cross the sector on top of it, so the answer materialises well after the
     * incident either way. Launching at state exit would need an exit hook the director does not
     * have, and would race the state change.
     *
     * <p>Guarded on the director's cycle counter so it fires exactly once per crisis cycle, and so
     * a save/load or a {@code testing.forceState} pin cannot re-arm it.
     */
    protected void tryLaunchRetaliation(ExileHegLeagueDirector director,
                                        ExileHegLeagueConfig config) {
        if (!config.strikeRetaliationEnabled) return;
        // Cheapest guard first -- this runs twice a day for the whole 10-20 day state.
        if (director.getLastRetaliationCycle() == director.getCyclesCompleted()) return;

        String actorId = director.getVictimFactionId();   // the wronged side answers
        String targetId = director.getOffenderFactionId();

        // Claim the cycle whatever happens below. A retaliation that finds no legal target is a
        // contained crisis, not a retry loop: without this the manager would re-scan the whole
        // economy twice a day for the rest of the state.
        director.setLastRetaliationCycle(director.getCyclesCompleted());

        if (actorId == null || targetId == null) {
            // Only reachable when RETALIATION was entered without an incident -- i.e. under
            // testing.forceState. Logged so a tester is never left wondering why nothing sailed.
            Global.getLogger(ExileHegLeagueStrikeManager.class).info(
                    "Exiles Heg-League RETALIATION skipped: no incident parties this cycle"
                            + " (victim=" + actorId + ", offender=" + targetId + ")."
                            + " Expected only under testing.forceState -- let the cycle reach"
                            + " RETALIATION through an INCIDENT to test the real path.");
            return;
        }

        // PASS 6 (decision D80.1): the victim answers with ONE punitive op, but now there are two
        // kinds. Roll which, weighted per side, then try it -- and on an empty pool try the other
        // kind exactly once before declaring the crisis contained.
        boolean squeezeFirst = rollRetaliationIsSqueeze(config, actorId);
        boolean launched = launchRetaliationOp(director, config, actorId, targetId, squeezeFirst,
                "RETALIATION");
        if (!launched && config.planetaryRetaliationFallbackToOtherType) {
            // THE FALLBACK, and why it matters more than the weights (D80.1):
            // tryLaunchRetaliation() claims the cycle before it attempts anything (Pass 3,
            // deliberately), so a roll that lands on a pool with no legal target would otherwise
            // cost the cycle its ENTIRE punitive response. The League's squeeze pool in
            // particular is seven Hegemony markets of which the most defended will routinely fail
            // feasibility. One extra economy scan per cycle at most, and the "one punitive op per
            // cycle" invariant is preserved exactly -- there is no second attempt of the SAME
            // type, and never two ops.
            Global.getLogger(ExileHegLeagueStrikeManager.class).info(
                    "Exiles Heg-League RETALIATION: no eligible "
                            + (squeezeFirst ? "squeeze" : "strike") + " target; falling back to a "
                            + (squeezeFirst ? "strike" : "squeeze") + " (actor=" + actorId
                            + ", target=" + targetId + ")");
            launched = launchRetaliationOp(director, config, actorId, targetId, !squeezeFirst,
                    "RETALIATION fallback");
        }
        if (!launched) {
            Global.getLogger(ExileHegLeagueStrikeManager.class).info(
                    "Exiles Heg-League RETALIATION found no eligible target: actor=" + actorId
                            + " target=" + targetId + " cycle=" + director.getCyclesCompleted()
                            + " -- the crisis passes without a punitive op");
            director.debug("Heg-League retaliation: no eligible target this cycle.");
        }
    }

    /**
     * Which weapon the wronged side reaches for this cycle (D80.1).
     *
     * <p>Weighted by the ACTOR's own {@code opWeights.surgical} vs {@code opWeights.planetary}: a
     * planetary squeeze is trade disruption aimed at one colony, which is the League's line, while
     * the Hegemony's punitive instinct is to take a shipyard offline. 1 : 3 each way, mirroring
     * interdiction exactly, so each side's RETALIATION reads as a different KIND of anger without
     * a line of forked code. The Hegemony still squeezes roughly one cycle in four: rare, not
     * forbidden.
     *
     * <p>A side with both weights at zero, or a config where squeezes are switched off entirely,
     * falls back to a strike -- the Pass-3 behaviour, unchanged.
     */
    protected boolean rollRetaliationIsSqueeze(ExileHegLeagueConfig config, String actorId) {
        if (!ExileHegLeagueSqueezeManager.squeezesEnabled(config)) return false;
        ExileHegLeagueConfig.Side side = config.sideFor(actorId);
        if (side == null) return false;
        float surgical = Math.max(0f, side.surgicalWeight);
        float planetary = Math.max(0f, side.planetaryWeight);
        if (planetary <= 0f) return false;
        if (surgical <= 0f) return true;
        float r = random.nextFloat() * (surgical + planetary);
        boolean squeeze = r < planetary;
        // Q81's measurement line (added after the Run-2 read, 2026-09-20): without it the
        // strike-vs-squeeze split could only be inferred from which op launched.
        Global.getLogger(ExileHegLeagueStrikeManager.class).info(
                "Exiles Heg-League RETALIATION roll: " + (squeeze ? "SQUEEZE" : "STRIKE")
                        + " (actor=" + actorId + ", planetary " + planetary + " vs surgical "
                        + surgical + ", r=" + r + ")");
        return squeeze;
    }

    /** One punitive op of the chosen type. Both branches return "did an op actually launch". */
    protected boolean launchRetaliationOp(ExileHegLeagueDirector director,
                                          ExileHegLeagueConfig config, String actorId,
                                          String targetId, boolean squeeze, String reason) {
        if (squeeze) {
            return ExileHegLeagueSqueezeManager.launchSqueeze(
                    director, config, actorId, targetId, reason, random);
        }
        return launchStrike(director, config, actorId, targetId, reason);
    }

    // ------------------------------------------------------------------
    // ESCALATION counter-strikes (sec 4.3)
    // ------------------------------------------------------------------

    /**
     * Either side, on a cadence, capped per side.
     *
     * <p><b>Flagged deviation.</b> sec 12 row 4 gives "ESCALATION becomes real" to Pass 4's
     * blockades, but sec 4.3 names counter-strikes during ESCALATION as a use of THIS op type, and
     * Pass 2 already launches interdiction there. Shipping the path now means it is exercised by
     * this pass's test matrix instead of arriving untested inside Pass 4; it has its own switch
     * ({@code escalationCounterStrikes}) if Sean would rather hold it.
     */
    protected void tryLaunchCounterStrike(ExileHegLeagueDirector director,
                                          ExileHegLeagueConfig config) {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        addSideIfAvailable(picker, config, config.hegemony);
        addSideIfAvailable(picker, config, config.persean);
        ExileHegLeagueConfig.Side actorSide = (ExileHegLeagueConfig.Side) picker.pick();
        if (actorSide == null) return;

        String actorId = actorSide.factionId;
        String targetId = ExileHegLeagueConfig.HEGEMONY_ID.equals(actorId)
                ? ExileHegLeagueConfig.PERSEAN_ID : ExileHegLeagueConfig.HEGEMONY_ID;
        if (!launchStrike(director, config, actorId, targetId, "ESCALATION counter-strike")) {
            // HL-13 (bug sweep, 2026-10-06): a failed attempt is now always visible in the log.
            Global.getLogger(ExileHegLeagueStrikeManager.class).info(
                    "Exiles Heg-League ESCALATION counter-strike attempt: nothing launched (actor="
                            + actorId + ", target=" + targetId + ") -- no source, or every market"
                            + " filtered (exemption, cooldown, live op, no strikeable industry,"
                            + " or feasibility above)");
        }
    }

    protected void addSideIfAvailable(WeightedRandomPicker picker, ExileHegLeagueConfig config,
                                      ExileHegLeagueConfig.Side side) {
        if (side == null || side.surgicalWeight <= 0f) return;
        if (countActive(side.factionId) >= config.strikeEscalationMaxConcurrentPerSide) return;
        picker.add(side, side.surgicalWeight);
    }

    /** Live strike ops for one side, derived from the intel manager rather than a bookkeeping
     *  list -- nothing to prune and nothing to keep in sync. Pass null to count both sides. */
    protected int countActive(String actorFactionId) {
        int n = 0;
        List ops = Global.getSector().getIntelManager().getIntel(ExileHegLeagueStrikeIntel.class);
        for (int i = 0; i < ops.size(); i++) {
            ExileHegLeagueStrikeIntel op = (ExileHegLeagueStrikeIntel) ops.get(i);
            if (op.isEnded() || op.isEnding()) continue;
            if (actorFactionId != null && !actorFactionId.equals(op.getActorFactionId())) continue;
            n++;
        }
        return n;
    }

    /** True if one of THIS director's operations -- of any type -- is already live in the
     *  system. Cross-op-type on purpose: two Exiles ops fighting over one system is the collision
     *  the check exists for.
     *
     *  <p><b>Pass 6 needed NO change here.</b> {@link ExileHegLeagueSqueezeIntel} extends
     *  {@link ExileHegLeagueRaidIntel} and {@code getIntel(Class)} returns subclasses, so the
     *  first loop already sees a live squeeze. Adding a second scan would double-count. Note that
     *  {@link #countActive} above deliberately scans {@code ExileHegLeagueStrikeIntel} instead, so
     *  a squeeze does not consume a strike's ESCALATION concurrency slot either. */
    protected boolean hasLiveOpIn(StarSystemAPI system) {
        return ExileHegLeagueGeography.hasLiveOpIn(system); // delegate since 2026-11-08 (tier 3)
    }

    // ------------------------------------------------------------------
    // Launch
    // ------------------------------------------------------------------

    /**
     * Build and post one strike op. Budget rolls BEFORE target selection, because the feasibility
     * gate needs to know what this op can plausibly take on (the Black Fleet's budget-blind picks
     * suicided into defended systems until that order was fixed -- do not reorder).
     *
     * @return true if an op was actually launched.
     */
    protected boolean launchStrike(ExileHegLeagueDirector director, ExileHegLeagueConfig config,
                                   String actorId, String targetId, String reason) {
        FactionAPI actor = Global.getSector().getFaction(actorId);
        FactionAPI target = Global.getSector().getFaction(targetId);
        if (actor == null || target == null) return false;
        // Belt-and-braces so an op can never launch into a peace; the director idles on a lapsed
        // premise and B3 re-asserts hostility, but this is one comparison.
        if (!actor.isHostileTo(target)) return false;

        MarketAPI source = ExileHegLeagueGeography.pickSource(actorId, random);
        if (source == null) return false;

        float fp = rollFp(director, config);

        MarketAPI victimMarket = pickStrikeTarget(config, actor, targetId, source, fp);
        if (victimMarket == null) return false;
        if (!(victimMarket.getContainingLocation() instanceof StarSystemAPI)) return false;
        StarSystemAPI system = (StarSystemAPI) victimMarket.getContainingLocation();

        // PASS 6: the BASE roll is kept alongside the extended figure. The stage carries both, and
        // the relief budget the recovery manager receives is exactly their difference -- which is
        // what makes sec 4.6's promise ("a full, uninterrupted convoy schedule lands the effective
        // downtime at design-normal") true BY CONSTRUCTION rather than by a second knob that can
        // drift. Same distribution as before: one roll, then the multiplier.
        float baseDisruptDays = rollBaseDisruptDays(config);
        int disruptDays = (int) Math.max(1f,
                baseDisruptDays * config.strikeExtendedDisruptMult);

        // THE STRIKE PACKAGE tier, snapshotted at LAUNCH (author ruling 2026-08-28): the fleet
        // was armed when it sailed, so a summit cooling the meter mid-flight does not soften a
        // strike already underway -- and the stage never needs a live director reference at
        // impact. Thresholds of 0 (the shipped default) mean the full package on every strike.
        float tensionAtLaunch = director.getTension();
        int packageTier = 1;
        if (tensionAtLaunch >= config.strikeGroundDefenseBucketTension) packageTier = 2;
        if (tensionAtLaunch >= config.strikeEconomicBucketTension) packageTier = 3;

        ExileHegLeagueStrikeIntel raid = new ExileHegLeagueStrikeIntel(
                system, actor, targetId, config.strikeMaxShipSize,
                config.strikeBountyPerFrigate, config.wearinessPerFpDestroyed,
                config.strikeSystemCooldownDays);

        // Gather at the jump point nearest the source.
        JumpPointAPI gather = null;
        float min = Float.MAX_VALUE;
        List points = source.getContainingLocation().getEntities(JumpPointAPI.class);
        for (int i = 0; i < points.size(); i++) {
            JumpPointAPI curr = (JumpPointAPI) points.get(i);
            float dist = Misc.getDistance(
                    source.getPrimaryEntity().getLocation(), curr.getLocation());
            if (dist < min) {
                min = dist;
                gather = curr;
            }
        }
        SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(
                actor, system.getCenter());
        if (gather == null || raidJump == null) return false;

        // Pass 2's assemble stage, reused as-is: the per-fleet FP cap and the PATROL_LARGE
        // suppression solve the same two problems here, and its isSourceKnown() override is
        // LOAD-BEARING (null pirate-base intel -> NPE in getArrowData; the 2026-08-27 crash).
        ExileHegLeagueInterdictionManager.ExileHegLeagueAssembleStage assemble =
                new ExileHegLeagueInterdictionManager.ExileHegLeagueAssembleStage(
                        raid, gather, config.strikeMaxFleetFp);
        assemble.addSource(source);
        assemble.setSpawnFP(fp);
        assemble.setAbortFP(fp * config.strikeAbortFraction);
        raid.addStage(assemble);

        TravelStage travel = new TravelStage(raid, gather, raidJump, false);
        travel.setAbortFP(fp * config.strikeAbortFraction);
        raid.addStage(travel);

        ExileHegLeagueStrikeStage action = new ExileHegLeagueStrikeStage(
                raid, system, victimMarket.getId(), actorId, targetId, disruptDays,
                packageTier, config.strikeEconomicIndustries, config.strikeOrbitDays,
                config.strikeUnrestPoints, config.strikeMaxDays, baseDisruptDays);
        action.setAbortFP(fp * config.strikeAbortFraction);
        raid.addStage(action);
        raid.setStrikeStage(action);

        raid.addStage(new ReturnStage(raid));

        // Hidden until the strike group arrives -- the sibling convention (sec 3.4). The hiding
        // comes from the intel constructor's setHidden(true); the addIntel boolean is
        // forceNoMessage and only suppresses the notification.
        Global.getSector().getIntelManager().addIntel(raid, true);

        // *** THE MEASUREMENT LINE (sec 5 "instrument before tuning"). ***
        // Every FP number in this pass is a guess until this line has been read across a run. It
        // prints the op's FP, the measured live defense of the chosen system INCLUDING the target
        // market's station, and the gate that admitted it. Tune strike.spawnFpMin/Max and
        // feasibilityMult FROM THIS, not from intuition -- the assaultFp:800 lesson.
        float enemy = WarSimScript.getEnemyStrength(actor, system);
        float station = ExileStationStrength.get(victimMarket.getFaction(), system,
                victimMarket.getPrimaryEntity());
        Global.getLogger(ExileHegLeagueStrikeManager.class).info(
                "Exiles Heg-League SURGICAL STRIKE LAUNCHED: " + source.getName() + " -> "
                        + victimMarket.getName() + " in " + system.getNameWithLowercaseType()
                        + " | actor=" + actorId + " struck=" + targetId
                        + " | FP=" + (int) fp
                        + " defense=" + (int) (enemy + station)
                        + " (enemy=" + (int) enemy + " + station=" + (int) station + ")"
                        + " gate=" + (int) config.feasibilityGate(fp, source,
                                config.strikeFeasibilityStrengthMult)
                        + " (" + config.describeGate(fp, source,
                                config.strikeFeasibilityStrengthMult) + ")"
                        + " | maxShipSize=" + config.strikeMaxShipSize
                        + " maxFleetFp=" + (int) config.strikeMaxFleetFp
                        + " disruptDays=" + disruptDays
                        + " (base=" + (int) baseDisruptDays + " x "
                        + config.strikeExtendedDisruptMult + ")"
                        + " packageTier=" + packageTier
                        + " | reason=" + reason
                        + " state=" + ExileHegLeagueDirector.getStateName(director.getState())
                        + " tension=" + director.getTension());

        director.debug("Heg-League " + reason + ": " + actor.getDisplayName() + " -> "
                + victimMarket.getName() + " (" + (int) fp + " FP, " + disruptDays + "d).");
        return true;
    }

    /**
     * "Sized by tension" (sec 3.2.2 state 3). The base band is rolled, then scaled by how far the
     * meter has climbed from its floor toward its cap: at the floor the strike is the base size, at
     * the cap it is {@code 1 + tensionFpScale} times that.
     */
    protected float rollFp(ExileHegLeagueDirector director, ExileHegLeagueConfig config) {
        float fp = config.strikeSpawnFpMin
                + random.nextFloat() * (config.strikeSpawnFpMax - config.strikeSpawnFpMin);
        float span = config.tensionCap - config.tensionFloor;
        if (span > 0f && config.strikeTensionFpScale > 0f) {
            float t = (director.getTension() - config.tensionFloor) / span;
            if (t < 0f) t = 0f;
            if (t > 1f) t = 1f;
            fp *= 1f + config.strikeTensionFpScale * t;
        }
        return fp;
    }

    /**
     * The DESIGN-NORMAL disruption roll, before {@code extendedDisruptMult}.
     *
     * <p><b>Pass 6 replaced {@code rollDisruptDays()} with this.</b> The old method returned
     * {@code base * extendedDisruptMult} in one step, which was fine while nothing needed the base;
     * the recovery convoys need exactly the difference between the two, so {@code launchStrike}
     * now rolls the base here and multiplies inline. Same single roll, same distribution, one
     * fewer method -- the multiplied form had no remaining caller and was not worth keeping as
     * documentation.
     *
     * <p>sec 4.6's extended-baseline lever left 1.0 in the same commit that landed the convoys;
     * see the config comment for the invariant it now lives under.
     */
    protected float rollBaseDisruptDays(ExileHegLeagueConfig config) {
        return config.strikeDisruptDaysMin
                + random.nextFloat()
                        * (config.strikeDisruptDaysMax - config.strikeDisruptDaysMin);
    }

    /**
     * The strike pool: markets of the target side that carry a military industry, filtered by every
     * sec 2/R6 rule and weighted by distance from the source.
     *
     * <p>Returns a MARKET, not a system: a strike is aimed at one colony's installations, unlike
     * interdiction, which is aimed at a system's approaches.
     */
    protected MarketAPI pickStrikeTarget(ExileHegLeagueConfig config, FactionAPI actor,
                                         String targetFactionId, MarketAPI source, float fp) {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);

        // Cross-director dogpile check (sec 2). Read-only shared STATE -- the reason the settled
        // architecture needs no central controller. Asked once per launch, never per frame.
        List busySystems = ExileLiveOps.getSystemsWithActiveRaids(null);
        List rejectedSystems = new ArrayList(); // StarSystemAPI whose fleets alone failed the gate
        Map enemyBySystem = new HashMap();      // StarSystemAPI -> Float, measured once per system

        // sec 14.14 Option A: the gate is in STRENGTH units, converted with vanilla's
        // Misc.getAdjustedStrength(fp, source). It depends only on the source and the budget, so it
        // is computed once per pass rather than per candidate.
        boolean gateOn = config.strikeFeasibilityStrengthMult > 0f;
        float gate = config.feasibilityGate(fp, source, config.strikeFeasibilityStrengthMult);
        String gateHow = config.describeGate(fp, source, config.strikeFeasibilityStrengthMult);

        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m.isHidden() || !m.isInEconomy()) continue;
            // LIVE ownership query (R6c) -- never a hardcoded list.
            if (!targetFactionId.equals(m.getFactionId())) continue;
            if (!m.getFaction().isHostileTo(actor)) continue;
            if (m.getPrimaryEntity() == null) continue;
            if (!(m.getContainingLocation() instanceof StarSystemAPI)) continue;

            StarSystemAPI system = (StarSystemAPI) m.getContainingLocation();

            // R6a: SYSTEM-level capital exemption. Aztlan and Thule entire -- which is also what
            // keeps both sides' main ship production out of the war (sec 1.2).
            if (ExileHegLeagueGeography.isExemptSystem(config, system)) continue;
            // Player colonies are code-exempt by the Exiles convention.
            if (!Misc.getMarketsInLocation(system, Factions.PLAYER).isEmpty()) continue;
            if (system.getMemoryWithoutUpdate().getBoolean(
                    ExileHegLeagueInterdictionManager.SYSTEM_COOLDOWN_KEY)) continue;
            if (busySystems.contains(system)) continue;
            if (hasLiveOpIn(system)) continue;

            // R6b strain markets, the refugee shield, the author allow-list and the
            // Battered/Emergency-Measures guard, all per market.
            if (!ExileHegLeagueGeography.isEligibleVictimMarket(config, m, targetFactionId)) {
                continue;
            }
            // The whole point of the op: there has to be something military to take offline.
            if (!hasStrikeableIndustry(config, m)) continue;

            // ASSAULT-tier feasibility, own copy of the idiom (S4 -- no shared helper). Unlike
            // interdiction's, this one INCLUDES the target's station strength, because a strike has
            // to go through it.
            //
            // The enemy fleet strength is a SYSTEM figure and is measured once per system; the
            // station is a MARKET figure and is measured per market. The 2026-09-19 run-3 read
            // found the old shape gating a whole system on the FIRST market's station: Valhalla
            // passed on "station 0" and the picker then launched at Ragnar Complex, whose 240
            // station put it over its own gate (584 > 428) -- the strike failed. So: one line per
            // system when the fleets alone exceed the gate, otherwise one line per market.
            if (gateOn) {
                if (rejectedSystems.contains(system)) continue;
                float enemy;
                Float cachedEnemy = (Float) enemyBySystem.get(system);
                if (cachedEnemy == null) {
                    enemy = WarSimScript.getEnemyStrength(actor, system);
                    enemyBySystem.put(system, Float.valueOf(enemy));
                    if (enemy > gate) {
                        rejectedSystems.add(system);
                        Global.getLogger(ExileHegLeagueStrikeManager.class).info(
                                "Exiles Heg-League strike feasibility: " + system.getBaseName()
                                        + " REJECTED (enemy " + (int) enemy
                                        + " alone > gate " + (int) gate + " = " + gateHow
                                        + "), actor="
                                        + actor.getId() + ", target=" + targetFactionId);
                        continue;
                    }
                } else {
                    enemy = cachedEnemy.floatValue();
                }
                float station = ExileStationStrength.get(m.getFaction(), system,
                        m.getPrimaryEntity());
                float defense = enemy + station;
                if (defense > gate) {
                    Global.getLogger(ExileHegLeagueStrikeManager.class).info(
                            "Exiles Heg-League strike feasibility: " + m.getName() + " in "
                                    + system.getBaseName()
                                    + " REJECTED (defense " + (int) defense + " = enemy "
                                    + (int) enemy + " + station " + (int) station + " > gate "
                                    + (int) gate + " = " + gateHow + "), actor="
                                    + actor.getId() + ", target=" + targetFactionId);
                    continue;
                }
                Global.getLogger(ExileHegLeagueStrikeManager.class).info(
                        "Exiles Heg-League strike feasibility: " + m.getName() + " in "
                                + system.getBaseName()
                                + " eligible (defense " + (int) defense + " = enemy "
                                + (int) enemy + " + station " + (int) station + " <= gate "
                                + (int) gate + " = " + gateHow + "), actor=" + actor.getId()
                                + ", target=" + targetFactionId);
            }

            // R6c: closer is likelier, so ops concentrate where the powers' spaces meet without a
            // border ever being hardcoded.
            float distLY = Misc.getDistanceLY(
                    source.getPrimaryEntity().getLocationInHyperspace(),
                    system.getLocation());
            picker.add(m, 1f / (1f + distLY * config.distanceFalloff));
        }

        return (MarketAPI) picker.pick();
    }

    /**
     * Does this market carry a military industry this strike would be allowed to hit? Mirrors
     * {@code ExileHegLeagueStrikeStage.pickIndustries} without disturbing it -- the stage owns the
     * choice, this owns the "is it worth sailing" question.
     */
    protected boolean hasStrikeableIndustry(ExileHegLeagueConfig config, MarketAPI market) {
        // The bar is a standing MILITARY industry: bucket (a) is in every package tier, so a
        // market with nothing military left is not worth a punitive op even though a tier-3
        // package could still touch its war economy. (2026-08-28 package ruling.)
        List industries = market.getIndustries();
        for (int i = 0; i < industries.size(); i++) {
            Industry ind = (Industry) industries.get(i);
            if (ind == null || ind.getSpec() == null) continue;
            String id = ind.getId();
            if (Industries.POPULATION.equals(id)) continue;
            if (ind.isDisrupted()) continue;
            if (Industries.GROUNDDEFENSES.equals(id) || Industries.HEAVYBATTERIES.equals(id)
                    || Industries.PATROLHQ.equals(id) || Industries.MILITARYBASE.equals(id)
                    || Industries.HIGHCOMMAND.equals(id)) return true;
            if (ind.getSpec().hasTag("military") || ind.getSpec().hasTag("command")
                    || ind.getSpec().hasTag("patrol")
                    || ind.getSpec().hasTag("grounddefenses")) return true;
        }
        return false;
    }
}
