package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
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
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidAssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.scripts.marketconditions.ExileHegLeagueInterdiction;

/**
 * Hegemony-Persean League INTERDICTION RAIDS -- PASS 2 of docs/EXILES_HEG_LEAGUE_WAR.md
 * (sec 12 row 2; mechanism sec 4.1, geography sec 2/R6, guardrails sec 5, bounty sec 4.7).
 *
 * <p><b>This is what makes the TENSION state real.</b> Pass 1 shipped a director whose six-state
 * crisis cycle turned over and printed debug lines while launching nothing. This manager reads
 * that state machine and, during the states sec 3.2.2 says carry interdiction -- <b>TENSION</b>
 * ("low-rate interdiction raids ... along the border") and <b>ESCALATION</b> ("multiple
 * interdiction ops") -- sends presence ops from one side's ports into the other's space.
 *
 * <p><b>It reads the director; the director does not know it exists</b>, and neither knows about
 * the Ashen, Black Fleet or Omicron directors (CLAUDE.md sec 6, spec sec 3.1). Coordination with the
 * siblings is entirely through shared read-only state: {@link ExileLiveOps} for the dogpile check,
 * {@link ExileUnrestGuard} for Battered/Emergency-Measures, {@link ExileProtectedFactions} for the
 * refugee canon shield.
 *
 * <h2>Target selection (sec 2, ruling R6)</h2>
 * <ol>
 * <li><b>Ownership is queried LIVE, every cycle</b> -- never hardcoded (R6c). Under Nexerelin core
 *     worlds change hands; a market that does simply leaves the pool.</li>
 * <li><b>R6a -- capital exemption is SYSTEM level.</b> Every market in an {@code exemptSystems}
 *     system (Aztlan, Thule) is untargetable. Exempt systems remain valid op SOURCES.</li>
 * <li><b>R6b -- the five refugee strain markets are never victims</b>
 *     ({@code excludedMarkets}), on top of the unconditional {@link ExileProtectedFactions}
 *     check.</li>
 * <li><b>R6c -- the rest of the enemy's markets are a pool, distance-falloff weighted</b> from the
 *     source (the Black Fleet pattern), so ops concentrate where the two powers' spaces meet, with
 *     author allow/deny lists for tuning. Robust against the pending custom-system placement
 *     retune, because nothing is a fixed list of targets.</li>
 * </ol>
 *
 * <p><b>Blockade plumbing is untouched.</b> The author's lore-driven blockade whitelist (R6b) is an
 * outstanding deliverable that gates Passes 4 and 6. Interdiction does not wait on it and does not
 * read it.
 *
 * <h2>The feasibility gate</h2>
 * A private copy of the sibling idiom, per ruling S4 -- <b>do not extract a shared helper</b>
 * (rejected on the merits, Ashen sec 14.6: the differences between directors' gates are
 * playtest-earned rulings). Commerce tier, so station strength is excluded and the multiplier is
 * loose. <b>It ships with an instrumentation log line</b> and every FP number in this pass is a
 * guess until that line has been read: the project has been burned guessing FP (the
 * {@code assaultFp: 800} lesson -- measure before tuning).
 */
public class ExileHegLeagueInterdictionManager implements EveryFrameScript {

    /** Quest/runtime override, beating the config default (the sibling convention). */
    public static final String RAIDS_ENABLED_KEY = "$exiles_hl_interdiction_enabled";
    /**
     * Anti-hotspot flag, set on a target SYSTEM's memory when an op ENDS (see
     * {@link ExileHegLeagueRaidIntel#notifyEnded()}); {@link #pickTarget} skips flagged systems.
     *
     * <p>End-start rather than launch-start, deliberately (sec 9 asks for the choice to be made
     * deliberately): an op outlives 30-60 days easily, so a launch-start flag expires while the
     * raiders are still on station.
     */
    public static final String SYSTEM_COOLDOWN_KEY = "$exiles_hl_op_cooldown";

    /**
     * Assemble stage with a PER-FLEET FP CAP -- and, unlike the Ashen version it is copied from,
     * <b>no source concealment</b> (sec 3.4: Hegemony and League home systems are public
     * knowledge, so {@code isSourceKnown()} is left alone).
     *
     * <p><b>Why the cap exists.</b> {@code AssembleStage.getFP()} opens with
     * {@code if (spawnFP >= getLargeSize(true) + getFPMedium())} and carves {@code getLargeSize()}
     * -- the faction's {@code approximateMaxFPPerFleet} -- as ONE fleet. At a Core-faction roster
     * that takes almost the whole op into a single very large fleet, which then clears the
     * strength-ratio bar in {@code TacticalModule.pickEncounterOption} against an orbital station
     * and bee-lines into it. Four smaller fleets do not. This was diagnosed and fixed once already
     * in the Ashen build; the fix is ported rather than rediscovered.
     *
     * <p>Named static class (NOT anonymous) so XStream serialization stays clean -- the Black Fleet
     * precedent.
     */
    public static class ExileHegLeagueAssembleStage extends PirateRaidAssembleStage {
        /** Per-fleet FP ceiling; &lt;= 0 restores vanilla sizing. */
        protected float maxFleetFp;

        public ExileHegLeagueAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint,
                                           float maxFleetFp) {
            super(raid, gatheringPoint, null);
            this.maxFleetFp = maxFleetFp;
        }

        /** The source is public knowledge (sec 3.4) -- but this override is also LOAD-BEARING:
         *  the vanilla implementation dereferences the pirate-base intel we pass as null
         *  (crash found in live test 2026-08-27: NPE in getArrowData rendering the intel map).
         *  Ashen's concealment override dodged the same null by accident; ours must exist even
         *  though we conceal nothing. */
        @Override
        public boolean isSourceKnown() {
            return true;
        }

        /** Caps the "one big fleet" carve that opens {@code getFP()}. */
        @Override
        protected float getLargeSize(boolean limitToSpawnFP) {
            float base = super.getLargeSize(limitToSpawnFP);
            if (maxFleetFp > 0f && base > maxFleetFp) return maxFleetFp;
            return base;
        }

        /** Caps the PATROL_LARGE branch too. Without this the remainder after the big carve can
         *  still come back out as a single ~85 FP (x1.25 jitter) fleet, undoing half the point. */
        @Override
        protected float getFPLarge() {
            float base = super.getFPLarge();
            if (maxFleetFp > 0f && base > maxFleetFp) return maxFleetFp;
            return base;
        }

        /**
         * Never type a fleet PATROL_LARGE. Fleet SIZE and fleet TYPE are decided separately:
         * {@code getFP()} sets the FP while {@code pickNextType()} picks the type the faction's
         * {@code fleetTypeNames} maps to a display name. Counter-intuitively, capping
         * {@code getLargeSize} LOWERS vanilla's PATROL_LARGE threshold, so the size cap alone makes
         * "Armada"-class names MORE frequent, not less. Suppressing the type here is the actual
         * fix -- a 150 FP destroyer-capped interdiction squadron is not an armada.
         *
         * <p>Mirrors vanilla's own alternating picker with the LARGE option removed, so
         * consecutive fleets still vary.
         */
        @Override
        protected String pickNextType() {
            WeightedRandomPicker picker = new WeightedRandomPicker();
            if (!FleetTypes.PATROL_SMALL.equals(prevType)) {
                picker.add(FleetTypes.PATROL_SMALL);
            }
            if (!FleetTypes.PATROL_MEDIUM.equals(prevType) && spawnFP >= AssembleStage.FP_MEDIUM) {
                picker.add(FleetTypes.PATROL_MEDIUM);
            }
            prevType = (String) picker.pick();
            if (prevType == null) prevType = FleetTypes.PATROL_SMALL;
            return prevType;
        }
    }

    // ------------------------------------------------------------------
    // Saved state (primitives only). Live ops are DERIVED from the intel manager -- nothing to
    // prune, nothing to keep in sync, and the count stays correct while raids are disabled.
    // ------------------------------------------------------------------

    /** Days accumulated toward the next launch attempt. Only accrues in a raiding state, so ops
     *  do not silently queue up behind a summit and all fire at once when TENSION resumes. */
    protected float daysSinceLaunch = 0f;
    /** Rolled per attempt from the CURRENT state's cadence; &lt;= 0 means "roll one". */
    protected float nextLaunchDays = -1f;
    /** HL-2: the state {@link #nextLaunchDays} was rolled for; -1 = unknown (fresh or older save). */
    protected int nextLaunchState = -1;

    // --- Transient runtime ---
    protected transient IntervalUtil tick;
    protected transient Random random;
    /** Condition statics are pushed from config once per load, not per tick. */
    protected transient boolean conditionTunablesApplied;
    /** One-shot geography resolution report -- the first-boot sanity check for R6's id lists. */
    protected transient boolean geographyLogged;
    /** Pass 8a test pin {@code testing.interdictionNow}: one forced attempt per game load. */
    protected transient boolean testLaunchTried;

    public ExileHegLeagueInterdictionManager() {
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
        geographyLogged = false;
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

        // THE INTERVAL GATE COMES FIRST, deliberately (CLAUDE.md sec 7). Everything below --
        // including resolving the director, which walks the sector's script list -- runs at most
        // twice a day rather than every frame, and nothing on the per-frame path allocates.
        float days = Global.getSector().getClock().convertToDays(amount);
        tick.advance(days);
        if (!tick.intervalElapsed()) return;
        float elapsed = tick.getIntervalDuration();

        // The director owns the config object; one parse, one timeScale application, one source
        // of truth. Before its first tick there is nothing to read, which is also a free "wait
        // until the world is up" guard.
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director == null) return;
        ExileHegLeagueConfig config = director.getConfig();
        if (config == null) return;

        if (!conditionTunablesApplied) {
            conditionTunablesApplied = true;
            applyConditionTunables(config);
        }
        if (!geographyLogged) {
            geographyLogged = true;
            logGeographyResolution(config);
        }

        if (!raidsEnabled(config)) return;
        // HL-1: a disabled, delayed or idle war launches nothing (and the clock does not accrue).
        if (!director.isActive()) return;

        int state = director.getState();
        if (!isRaidingState(state)) return;

        daysSinceLaunch += elapsed;
        if (nextLaunchDays <= 0f) {
            nextLaunchDays = rollCadence(config, state);
            nextLaunchState = state;
        } else if (nextLaunchState != state) {
            // HL-2: a roll made for the other raiding state is the wrong length -- a ~90-day
            // TENSION roll carried into a 45-day ESCALATION meant roughly half of escalations
            // launched nothing. Re-roll for the new state and carry the PROGRESS proportionally
            // (60 of 90 days -> two-thirds of the new roll), so a state change neither resets the
            // clock nor fires an instant launch. -1 (older save) just adopts the current state.
            float newDays = rollCadence(config, state);
            if (nextLaunchState != -1) {
                float progress = daysSinceLaunch / nextLaunchDays;
                if (progress > 1f) progress = 1f;
                daysSinceLaunch = progress * newDays;
                Global.getLogger(ExileHegLeagueInterdictionManager.class).info(
                        "HL interdiction cadence re-rolled for state " + state + ": "
                                + Math.round(newDays) + "d, progress carried "
                                + Math.round(progress * 100f) + "%");
            }
            nextLaunchDays = newDays;
            nextLaunchState = state;
        }
        // TEST PIN (Pass 8a): one attempt per game load skips the cadence wait, so a raider can
        // be found without waiting out a 90-day roll. Feasibility and the caps still apply.
        boolean testNow = config.testInterdictionNow && !testLaunchTried;
        if (daysSinceLaunch < nextLaunchDays && !testNow) return;
        if (testNow) {
            testLaunchTried = true;
            Global.getLogger(ExileHegLeagueInterdictionManager.class).info(
                    "Exiles Heg-League TEST PIN: interdictionNow -- launch attempt without the"
                            + " cadence wait");
        }

        daysSinceLaunch = 0f;
        nextLaunchDays = rollCadence(config, state);
        nextLaunchState = state;

        tryLaunch(director, config, state);
    }

    /**
     * sec 3.2.2: TENSION carries "low-rate interdiction raids ... along the border" and ESCALATION
     * carries "multiple interdiction ops". The other four states do not.
     *
     * <p><b>Conservative reading, flagged in sec 14.3.</b> RETALIATION is explicitly "ONE
     * proportionate punitive op" -- a surgical strike or planetary blockade, i.e. Pass 3/Pass 6
     * work, not interdiction. POLITICAL_INTERVENTION is the summit. DE_ESCALATION is where "ops
     * stop". INCIDENT is instant and has no dwell to raid in.
     */
    protected boolean isRaidingState(int state) {
        return state == ExileHegLeagueDirector.STATE_TENSION
                || state == ExileHegLeagueDirector.STATE_ESCALATION;
    }

    protected float rollCadence(ExileHegLeagueConfig config, int state) {
        float cadence = config.interdictionCadenceDays;
        if (state == ExileHegLeagueDirector.STATE_ESCALATION) {
            cadence *= config.interdictionEscalationCadenceMult;
        }
        return ExileHegLeagueDirector.jitterCadence(cadence, random);
    }

    /** Config default, overridable at runtime by quests via the memory flag. */
    protected boolean raidsEnabled(ExileHegLeagueConfig config) {
        return ExileHegLeagueDirector.switchEnabled(RAIDS_ENABLED_KEY, config.interdictionEnabled);
    }

    /** Push the config's condition tunables onto the condition's statics -- the established
     *  sibling arrangement, which is what makes the whole condition player-adjustable without
     *  touching Java. Values arrive already timeScale'd by the config loader. */
    protected void applyConditionTunables(ExileHegLeagueConfig config) {
        ExileHegLeagueInterdiction.DURATION_DAYS = config.interdictionConditionDays;
        ExileHegLeagueInterdiction.ACCESSIBILITY_PENALTY = config.interdictionAccessibilityPenalty;
        ExileHegLeagueInterdiction.IMPORT_HIT_COUNT = config.interdictionImportHitCount;
        ExileHegLeagueInterdiction.IMPORT_HIT_UNITS = config.interdictionImportHitUnits;
        ExileHegLeagueInterdictionStage.CONDITION_APPLY_DAY = config.interdictionConditionDelayDays;
        ExileHegLeagueInterdictionStage.suppressStationAttacks =
                config.interdictionSuppressStationAttacks;
        ExileHegLeagueInterdictionStage.OBJECTIVE_KEEP_OUT =
                config.interdictionObjectiveStationKeepOut;
    }

    // ------------------------------------------------------------------
    // Launch
    // ------------------------------------------------------------------

    /**
     * Budget rolls BEFORE target selection: the feasibility gate needs to know what this op can
     * plausibly take on. (The Black Fleet's budget-blind picks suicided into defended systems
     * until that order was fixed -- do not reorder these two lines.)
     */
    protected void tryLaunch(ExileHegLeagueDirector director, ExileHegLeagueConfig config,
                             int state) {
        ExileHegLeagueConfig.Side actorSide = pickActorSide(config, state);
        if (actorSide == null) return;

        String actorId = actorSide.factionId;
        String victimId = ExileHegLeagueConfig.HEGEMONY_ID.equals(actorId)
                ? ExileHegLeagueConfig.PERSEAN_ID : ExileHegLeagueConfig.HEGEMONY_ID;

        FactionAPI actor = Global.getSector().getFaction(actorId);
        FactionAPI victim = Global.getSector().getFaction(victimId);
        if (actor == null || victim == null) return;
        // The director's own premise check (sec 3.3) already idles on a lapsed war, and B3 re-asserts
        // hostility. This is the cheap belt-and-braces so an op can never launch into a peace.
        if (!actor.isHostileTo(victim)) return;

        MarketAPI source = pickSource(actorId);
        if (source == null) {
            // HL-13 (bug sweep, 2026-10-06): a failed attempt is now always visible in the log.
            Global.getLogger(ExileHegLeagueInterdictionManager.class).info(
                    "Exiles Heg-League interdiction attempt: no source market (actor=" + actorId + ")");
            return;
        }

        float fp = config.interdictionSpawnFpMin
                + random.nextFloat()
                        * (config.interdictionSpawnFpMax - config.interdictionSpawnFpMin);

        StarSystemAPI target = pickTarget(config, actor, victimId, source, fp);
        if (target == null) {
            Global.getLogger(ExileHegLeagueInterdictionManager.class).info(
                    "Exiles Heg-League interdiction attempt: no eligible target (actor=" + actorId
                            + ", FP=" + (int) fp + ") -- every candidate filtered (cooldown, live op,"
                            + " exemption, player colony, or the feasibility lines above)");
            return;
        }

        launchOp(director, config, actorSide, actor, victimId, source, target, fp, state);
    }

    /**
     * Which side acts this cycle. Weighted by the per-side {@code opWeights.interdiction} (sec 9),
     * which is where sec 3.2.3's asymmetry lives for this op type: the League is
     * commerce-focused and leans hard on interdiction, the Hegemony prefers blockades and
     * production strikes and contributes fewer of these. A side already at its concurrent-op cap
     * is simply not offered.
     */
    protected ExileHegLeagueConfig.Side pickActorSide(ExileHegLeagueConfig config, int state) {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        addSideIfAvailable(picker, config, config.hegemony, state);
        addSideIfAvailable(picker, config, config.persean, state);
        return (ExileHegLeagueConfig.Side) picker.pick();
    }

    protected void addSideIfAvailable(WeightedRandomPicker picker, ExileHegLeagueConfig config,
                                      ExileHegLeagueConfig.Side side, int state) {
        if (side == null || side.interdictionWeight <= 0f) return;
        if (countActive(side.factionId) >= maxConcurrentOpsFor(config, side, state)) return;
        picker.add(side, side.interdictionWeight);
    }

    /** ESCALATION uses the side's own {@code maxConcurrentOps} (sec 9); TENSION uses the shared
     *  low-rate ambient cap, because sec 3.2.2 calls the ambient war "low-rate". */
    protected int maxConcurrentOpsFor(ExileHegLeagueConfig config, ExileHegLeagueConfig.Side side,
                                      int state) {
        if (state == ExileHegLeagueDirector.STATE_ESCALATION) {
            return side.maxConcurrentOps;
        }
        return config.interdictionTensionMaxConcurrentPerSide;
    }

    /** Live ops for one side, derived from the intel manager rather than a bookkeeping list.
     *  Pass null to count both sides. */
    protected int countActive(String actorFactionId) {
        int n = 0;
        List ops = Global.getSector().getIntelManager().getIntel(ExileHegLeagueRaidIntel.class);
        for (int i = 0; i < ops.size(); i++) {
            ExileHegLeagueRaidIntel op = (ExileHegLeagueRaidIntel) ops.get(i);
            if (op.isEnded() || op.isEnding()) continue;
            // Pass 3: the lookup above returns subclasses too, and a live surgical strike must not
            // eat an interdiction slot. Without this the strike manager would silently throttle
            // Pass-2 behaviour that has already been signed off in a live test.
            if (!op.isInterdictionOp()) continue;
            if (actorFactionId != null && !actorFactionId.equals(op.getActorFactionId())) continue;
            n++;
        }
        return n;
    }

    /** True if one of THIS director's operations -- of any type -- is already live in the system.
     *  Extended by Pass 4: blockades ride a different pipeline (BlockadeFGI, not RaidIntel) and
     *  would otherwise be invisible here, so a raid could sail into a system this same director is
     *  blockading.
     *
     *  <p><b>Pass 6 needed NO change here.</b> A planetary squeeze rides RaidIntel like the other
     *  two raid types -- {@link ExileHegLeagueSqueezeIntel} extends
     *  {@link ExileHegLeagueRaidIntel}, and {@code getIntel(Class)} returns subclasses -- so the
     *  first loop already sees it. Adding a squeeze-specific scan would double-count. (Contrast
     *  {@link #countActive}, which DOES need the {@code isInterdictionOp()} filter for exactly the
     *  same reason.) */
    protected boolean hasLiveOpIn(StarSystemAPI system) {
        return ExileHegLeagueGeography.hasLiveOpIn(system); // delegate since 2026-11-08 (tier 3)
    }

    /**
     * A market of the acting side with a functional spaceport -- ops stage from somewhere real.
     * <b>Exempt systems are valid SOURCES</b> (R6a is a targeting rule, not a basing rule), so
     * Chicomoztoc and Kazeron can and should send fleets.
     */
    protected MarketAPI pickSource(String actorFactionId) {
        return ExileHegLeagueGeography.pickSource(actorFactionId, random);
    }

    /**
     * The pool, filtered and distance-weighted (R6). Every rejection reason below is a spec
     * ruling, not a heuristic -- read sec 2 before changing any of them.
     */
    protected StarSystemAPI pickTarget(ExileHegLeagueConfig config, FactionAPI actor,
                                       String victimFactionId, MarketAPI source, float fp) {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        List seen = new ArrayList(); // StarSystemAPI

        // Cross-director dogpile check (sec 2). Read-only shared STATE, which is the whole reason
        // the settled architecture needs no central controller. Asked once per launch, never per
        // frame, and with null so every faction's live ops count -- this director has no ops of
        // its own to exclude at the moment it is choosing where to send one.
        List busySystems = ExileLiveOps.getSystemsWithActiveRaids(null);

        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m.isHidden() || !m.isInEconomy()) continue;
            // LIVE ownership query (R6c) -- never a hardcoded list. A market that changed hands
            // silently leaves the pool.
            if (!victimFactionId.equals(m.getFactionId())) continue;
            if (!m.getFaction().isHostileTo(actor)) continue;
            if (!(m.getContainingLocation() instanceof StarSystemAPI)) continue;

            StarSystemAPI system = (StarSystemAPI) m.getContainingLocation();
            if (seen.contains(system)) continue;
            seen.add(system);

            // R6a: SYSTEM-level capital exemption. Aztlan and Thule entire, minor markets riding
            // along -- and this also protects both sides' main ship production, per sec 1.2.
            if (isExemptSystem(config, system)) continue;

            // Player colonies are code-exempt by the Exiles convention: no NPC director op is ever
            // pointed at a system the player holds a market in.
            if (!Misc.getMarketsInLocation(system, Factions.PLAYER).isEmpty()) continue;

            if (system.getMemoryWithoutUpdate().getBoolean(SYSTEM_COOLDOWN_KEY)) continue;
            if (hasLiveOpIn(system)) continue;
            if (busySystems.contains(system)) continue;

            // Is there anything in here we are actually allowed to interdict? R6b + the
            // unconditional refugee shield + the Battered/Emergency-Measures guard, all evaluated
            // per market rather than per system so a system with one strain market and two
            // ordinary ones stays a legal target.
            if (!hasEligibleVictimMarket(config, system, victimFactionId)) continue;

            // COMMERCE-tier feasibility, own copy of the idiom (S4 -- no shared helper).
            // Raiders hunt shipping and never fight ground battles or stations, so station
            // strength is EXCLUDED and the multiplier is loose. getEnemyStrength sums LIVE patrol
            // strength, so it cannot be predicted offline -- hence the instrumentation.
            //
            // sec 14.14 Option A: the gate is in STRENGTH units -- defence is WarSimScript
            // effective strength, so the op's side is converted with vanilla's
            // Misc.getAdjustedStrength(fp, source) (see ExileHegLeagueConfig.feasibilityGate).
            if (config.interdictionFeasibilityStrengthMult > 0f) {
                float defense = WarSimScript.getEnemyStrength(actor, system);
                float gate = config.feasibilityGate(fp, source,
                        config.interdictionFeasibilityStrengthMult);
                String how = config.describeGate(fp, source,
                        config.interdictionFeasibilityStrengthMult);
                if (defense > gate) {
                    Global.getLogger(ExileHegLeagueInterdictionManager.class).info(
                            "Exiles Heg-League interdiction feasibility: " + system.getBaseName()
                                    + " REJECTED (defense " + (int) defense + " > gate "
                                    + (int) gate + " = " + how + "), actor="
                                    + actor.getId() + ", victim=" + victimFactionId);
                    continue;
                }
                Global.getLogger(ExileHegLeagueInterdictionManager.class).info(
                        "Exiles Heg-League interdiction feasibility: " + system.getBaseName()
                                + " eligible (defense " + (int) defense + " <= gate " + (int) gate
                                + " = " + how
                                + "), actor=" + actor.getId() + ", victim=" + victimFactionId);
            }

            // R6c: closer is likelier, so ops concentrate where the powers' spaces meet, without
            // ever hard-coding a border that the pending system-placement retune could invalidate.
            float distLY = Misc.getDistanceLY(
                    source.getPrimaryEntity().getLocationInHyperspace(),
                    system.getLocation());
            picker.add(system, 1f / (1f + distLY * config.distanceFalloff));
        }

        return (StarSystemAPI) picker.pick();
    }

    /** At least one market in the system that this op may legally squeeze.
     *  Body moved to {@link ExileHegLeagueGeography} in Pass 3 (three callers); this stays as a
     *  delegate so every Pass-2 call site and behaviour is byte-identical. */
    protected boolean hasEligibleVictimMarket(ExileHegLeagueConfig config, StarSystemAPI system,
                                              String victimFactionId) {
        return ExileHegLeagueGeography.hasEligibleVictimMarket(config, system, victimFactionId);
    }

    /** Body moved to {@link ExileHegLeagueGeography} in Pass 3; delegate, behaviour unchanged. */
    protected boolean isEligibleVictimMarket(ExileHegLeagueConfig config, MarketAPI m,
                                             String victimFactionId) {
        return ExileHegLeagueGeography.isEligibleVictimMarket(config, m, victimFactionId);
    }

    /**
     * R6b, reachable from the action stage without a config handle.
     *
     * <p>Deny list ONLY -- the author allow-list is a target-SELECTION knob and deliberately does
     * not suppress the condition on an otherwise ordinary market inside a system that was already
     * chosen. Strain markets are the ones that must never take the hit.
     */
    public static boolean isExcludedMarket(String marketId) {
        if (marketId == null) return false;
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director == null || director.getConfig() == null) return false;
        return director.getConfig().excludedMarkets.contains(marketId);
    }

    /**
     * R6a. Matched against both the system id and its base name, case-insensitively: vanilla
     * systems are created by display name and looked up by lowercase id in starmap.json, and an
     * exemption that silently fails to match would quietly make a capital targetable. The
     * one-shot {@link #logGeographyResolution} report exists so a typo is visible on the first
     * boot rather than in a playtest.
     */
    protected boolean isExemptSystem(ExileHegLeagueConfig config, StarSystemAPI system) {
        return ExileHegLeagueGeography.isExemptSystem(config, system);
    }

    /** One line per configured geography id, saying whether it resolved to something live.
     *  Cheap, once per load, and it turns a silent typo into a first-boot log line. */
    protected void logGeographyResolution(ExileHegLeagueConfig config) {
        StringBuffer sb = new StringBuffer("Exiles Heg-League geography: exemptSystems ");
        List systems = Global.getSector().getStarSystems();
        for (int i = 0; i < config.exemptSystems.size(); i++) {
            String id = (String) config.exemptSystems.get(i);
            boolean found = false;
            for (int k = 0; k < systems.size(); k++) {
                StarSystemAPI s = (StarSystemAPI) systems.get(k);
                if (id != null && (id.equalsIgnoreCase(s.getId())
                        || id.equalsIgnoreCase(s.getBaseName()))) {
                    found = true;
                    break;
                }
            }
            sb.append(id).append(found ? "=OK " : "=UNRESOLVED ");
        }
        sb.append("| excludedMarkets ");
        for (int i = 0; i < config.excludedMarkets.size(); i++) {
            String id = (String) config.excludedMarkets.get(i);
            boolean found = Global.getSector().getEconomy().getMarket(id) != null;
            sb.append(id).append(found ? "=OK " : "=UNRESOLVED ");
        }
        sb.append("| allowedMarkets ").append(
                config.allowedMarkets.isEmpty() ? "(empty = all)" : config.allowedMarkets.toString());

        // Pass 4: the author's two blockade whitelists (R6b, sec 14.9.1). The planetary list is
        // PASS 6's, reported here only so a typo in it is visible on the first boot of the build
        // that transcribed it. Expect ancyra_market=UNRESOLVED under a start that never completes
        // the Galatia tutorial -- it is created outside the economy and joins on completion.
        sb.append(" | blockadeWhitelistSystems ");
        for (int i = 0; i < config.blockadeWhitelistSystems.size(); i++) {
            String id = (String) config.blockadeWhitelistSystems.get(i);
            boolean found = false;
            for (int k = 0; k < systems.size(); k++) {
                StarSystemAPI s = (StarSystemAPI) systems.get(k);
                if (id != null && (id.equalsIgnoreCase(s.getId())
                        || id.equalsIgnoreCase(s.getBaseName()))) {
                    found = true;
                    break;
                }
            }
            sb.append(id).append(found ? "=OK " : "=UNRESOLVED ");
        }
        sb.append("| planetaryBlockadeWhitelistMarkets ");
        for (int i = 0; i < config.planetaryBlockadeWhitelistMarkets.size(); i++) {
            String id = (String) config.planetaryBlockadeWhitelistMarkets.get(i);
            boolean found = Global.getSector().getEconomy().getMarket(id) != null;
            sb.append(id).append(found ? "=OK " : "=UNRESOLVED ");
        }

        Global.getLogger(ExileHegLeagueInterdictionManager.class).info(sb.toString());
    }

    /** The biggest eligible victim market in the system: the one named on the intel and, if
     *  {@code loiterOnFocusMarket} is on, the loiter token. */
    protected MarketAPI pickFocusMarket(ExileHegLeagueConfig config, StarSystemAPI system,
                                        String victimFactionId) {
        return ExileHegLeagueGeography.pickFocusMarket(config, system, victimFactionId); // 2026-11-08
    }

    /** The vanilla pirate-raid structure with the interdiction action stage swapped in. */
    protected void launchOp(ExileHegLeagueDirector director, ExileHegLeagueConfig config,
                            ExileHegLeagueConfig.Side actorSide, FactionAPI actor,
                            String victimFactionId, MarketAPI source, StarSystemAPI target,
                            float fp, int state) {
        MarketAPI focus = pickFocusMarket(config, target, victimFactionId);
        if (focus == null) return;

        // sec 3.2.3 asymmetry: the Hegemony's methodical ops sit longer, the League's are shorter
        // and lighter. Same stage, different numbers -- no forked code, ever.
        float presenceDays = Math.max(1f,
                config.interdictionPresenceDays * actorSide.presenceDaysMult);

        ExileHegLeagueRaidIntel raid = new ExileHegLeagueRaidIntel(
                target, actor, victimFactionId, config.interdictionMaxShipSize,
                config.interdictionBountyPerFrigate, config.wearinessPerFpDestroyed,
                config.interdictionSystemCooldownDays);

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
                actor, target.getCenter());
        if (gather == null || raidJump == null) return;

        ExileHegLeagueAssembleStage assemble = new ExileHegLeagueAssembleStage(
                raid, gather, config.interdictionMaxFleetFp);
        assemble.addSource(source);
        assemble.setSpawnFP(fp);
        assemble.setAbortFP(fp * config.interdictionAbortFraction);
        raid.addStage(assemble);

        TravelStage travel = new TravelStage(raid, gather, raidJump, false);
        travel.setAbortFP(fp * config.interdictionAbortFraction);
        raid.addStage(travel);

        ExileHegLeagueInterdictionStage action = new ExileHegLeagueInterdictionStage(
                raid, target, presenceDays, actor.getId(), victimFactionId, focus.getId(),
                config.interdictionLoiterOnFocusMarket);
        action.setAbortFP(fp * config.interdictionAbortFraction);
        raid.addStage(action);
        raid.setInterdictionStage(action);

        raid.addStage(new ReturnStage(raid));

        // Hidden until the raiders arrive. NOTE: the hiding comes from the intel's constructor
        // calling setHidden(true) -- the addIntel boolean is forceNoMessage and only suppresses
        // the notification (a correction learned the hard way during the Ashen build).
        Global.getSector().getIntelManager().addIntel(raid, true);

        // *** THE MEASUREMENT LINE (sec 5 "instrument before tuning"). ***
        // Every FP number in this pass is a guess until this line has been read across a run:
        // spawn FP, the measured live defense of the system that was actually chosen, and the
        // gate that admitted it. Tune spawnFpMin/Max and feasibilityMult FROM THIS, not from
        // intuition -- the assaultFp:800 lesson.
        float measuredDefense = WarSimScript.getEnemyStrength(actor, target);
        Global.getLogger(ExileHegLeagueInterdictionManager.class).info(
                "Exiles Heg-League INTERDICTION LAUNCHED: " + source.getName() + " -> "
                        + target.getNameWithLowercaseType()
                        + " | actor=" + actor.getId() + " victim=" + victimFactionId
                        + " | FP=" + (int) fp
                        + " defense=" + (int) measuredDefense
                        + " gate=" + (int) config.feasibilityGate(fp, source,
                                config.interdictionFeasibilityStrengthMult)
                        + " (" + config.describeGate(fp, source,
                                config.interdictionFeasibilityStrengthMult) + ")"
                        + " | maxShipSize=" + config.interdictionMaxShipSize
                        + " maxFleetFp=" + (int) config.interdictionMaxFleetFp
                        + " presenceDays=" + (int) presenceDays
                        + " | state=" + ExileHegLeagueDirector.getStateName(state)
                        + " tension=" + director.getTension()
                        + " | focus=" + focus.getName());

        director.debug("Heg-League interdiction launched: " + actor.getDisplayName() + " -> "
                + target.getBaseName() + " (" + (int) fp + " FP, focus " + focus.getName()
                + ", " + (int) presenceDays + "d).");
    }
}
