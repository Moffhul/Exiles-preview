package data.scripts.exiles;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
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
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction.FGBlockadeParams;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI.GenericRaidParams;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidAssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.scripts.marketconditions.ExileCrusadeHarassment;

/**
 * Ashen Crusade interdiction raids -- PASS 4 of docs/EXILES_ASHEN_CRUSADE_WAR.md §6.1/§6.3.
 *
 * <p>During Phases 2 (Interdiction) and 3 (Blockade) the Crusade sends presence-only raids
 * against Tri-Tachyon's systems: they hold the approaches, strangle shipping via
 * {@link ExileCrusadeHarassment}, and withdraw when the presence window closes or they are
 * beaten below the abort threshold. Phase 3 will additionally attach a blockade fleet in
 * Pass 6, which is planned around vanilla's own {@code BlockadeFGI} (§6.4) rather than
 * anything built here.
 *
 * <p><b>Structure follows {@link BlackFleetRaidManager} deliberately</b> -- the pure vanilla
 * raid pipeline (RaidIntel + Pirate raid stages, cribbed from {@code PirateBaseIntel.startRaid}),
 * so the eventual generalized Sector Destabilization Director (CLAUDE.md §6) can absorb this as
 * config rather than code. Ashen is the third sibling on that pipeline, which is precisely what
 * makes Pass 5's rule-of-three extraction worth doing.
 *
 * <p><b>Target discipline (ruling J5): Tri-Tachyon only.</b> Only the four configured markets'
 * systems are ever considered, so the pirate and independent markets sharing Hybrasil are never
 * touched -- the Crusade dislikes pirates but will not spend ships and crews on them.
 *
 * <p><b>Feasibility is the commerce tier's, not the assault tier's</b>: raiders hunt shipping
 * and never fight ground battles, so station strength is EXCLUDED from the estimate and the
 * multiplier is loose. Hybrasil is kept in the candidate pool on Sean's ruling (2026-08-01)
 * even though its Tri-Tachyon garrison will usually veto it early -- with only three target
 * systems, removing one outright costs more variety than it buys safety, and the gate opens
 * naturally as the war matures and FP climbs.
 *
 * <p><b>FP is deliberately capped below Omicron's Hybrasil threshold.</b> See
 * {@link AshenInterdictionStage} for the full escalation ladder; the short version is that
 * spawn FP tops out well under the 350 that would pull Culann's strategic detachment, so that
 * confrontation stays reserved for the Phase-4 strike.
 *
 * <p>Silent to the player except through its effects (§3.4): ops post as a hidden "System
 * Bounty" that reveals on arrival.
 */
public class AshenInterdictionManager implements EveryFrameScript {

    public static final String CONFIG_PATH = "data/config/exiles/director_config.json";
    public static final String CONFIG_KEY = "exileashencrusade";
    public static final String FACTION_ID = "exileashencrusade";
    /** Quest override, beating the config default (the Black Fleet convention). */
    public static final String RAIDS_ENABLED_KEY = "$exiles_ashen_interdiction_enabled";
    /** Set on a target SYSTEM's memory when an op ENDS (see {@link AshenRaidIntel#notifyEnded});
     *  pickTarget skips flagged systems. With only three candidates this is what stops the
     *  Crusade grinding one system forever. Concurrency is a separate guard --
     *  {@link #hasLiveOpIn}. */
    public static final String SYSTEM_COOLDOWN_KEY = "$exiles_ashen_op_cooldown";
    /** Prefix for the Phase-4 wave flags; the wave number is appended. See
     *  {@link #hasPhase4WaveLaunched} for why these are flags rather than derived state. */
    public static final String PHASE4_WAVE_KEY = "$exiles_ashen_culann_wave";

    /**
     * Assemble stage with an UNDISCLOSED source and a PER-FLEET FP CAP.
     *
     * <p>Undisclosed source: showing it would print Providence's location on the intel, which is
     * recon the player should earn. Named static class (NOT anonymous) so XStream serialization
     * stays clean -- the Black Fleet precedent.
     *
     * <p><b>The per-fleet cap (2026-08-02, after live observation).</b> Sean saw every raid arrive
     * as one very large fleet plus one small one, with the big fleet making a bee-line for the
     * Tri-Tachyon station and being beaten off. That split is structural, not random.
     * {@code AssembleStage.getFP()} opens with
     * <pre>if (spawnFP &gt;= getLargeSize(true) + getFPMedium()) -&gt; carve getLargeSize() as ONE fleet</pre>
     * and {@code getLargeSize()} is the source faction's {@code approximateMaxFPPerFleet} -- for
     * Ashen's roster, around 130. At a 130-190 FP budget the very first carve therefore takes
     * almost the whole op into a single fleet, every time.
     *
     * <p>Capping the two size getters splits the same budget into ~4 fleets of &lt;= maxFleetFp.
     * Both are {@code protected} in vanilla, so this needs no reimplementation of the chunking
     * loop -- the rest of {@code getFP}/{@code pickNextType} runs unchanged.
     *
     * <p><b>This is also the fix for the bee-line</b>, which is why no fleet-AI flag is set here.
     * Decompiling {@code campaign.ai.TacticalModule.pickEncounterOption} shows engagement against
     * a stronger opponent is decided by a strength ratio gated on commander personality (reckless
     * 0.25, aggressive 0.33, else 0.5) -- a ~130 FP fleet clears that bar against a station, four
     * ~50 FP fleets do not. (The same decompile ruled OUT
     * {@code MEMORY_KEY_MAKE_HOLD_VS_STRONGER}: it is read only when the counterparty is the
     * PLAYER, so it would have been a no-op here. Do not re-propose it.)
     */
    public static class HiddenSourceAssembleStage extends PirateRaidAssembleStage {
        /** Per-fleet FP ceiling; <= 0 restores vanilla sizing. */
        protected float maxFleetFp;
        /**
         * Let {@link #pickNextType()} produce {@code PATROL_LARGE} -- i.e. fleets named "Armada".
         *
         * <p><b>Default false, which is the 2026-08-02 behaviour</b> (commit 674572e): 45 FP
         * interdiction raiders calling themselves Armadas was absurd, and this class builds every
         * Ashen op, so the suppression was written unconditional.
         *
         * <p><b>Phase 4 needs it back</b> (Sean, 2026-08-13). Its 400 FP capital groups are meant
         * to read as "Ashen Crusade Armada" -- the engine's own composition from Ashen's
         * {@code fleetTypeNames}, with no naming code at all. Left unconditional, the climax of the
         * war would have arrived calling itself a "Fury".
         */
        protected boolean allowLargeType = false;

        /**
         * Exact number of fleets to carve, or <= 0 to leave vanilla's chunking alone.
         *
         * <p><b>Added 2026-08-15 after the second playtest, and it replaces a fix of mine that did
         * not work.</b> Sean saw 2 Knights of Gilead armadas and 4 Ashen ones where the config
         * asked for 1 and 2. The cause is that {@code maxFleetFp} is a CEILING, not a divisor:
         * {@code AssembleStage.getFP()} carves {@code getLargeSize()} only while
         * {@code spawnFP >= getLargeSize() + FP_MEDIUM}, and the remainder then falls through to a
         * type-sized chunk (small 20 / medium 45 / large 85, randomised +/-25%). So the tail of a
         * budget always fragments, no matter what the ceiling is -- 375 FP under a 394 ceiling
         * became roughly 300 + 75, which is the two Knights fleets.
         *
         * <p>My earlier {@code fleetCapFor(fp, n) = fp/n * 1.05} assumed the ceiling would divide
         * the budget. It cannot: the last chunk is always below the ceiling by construction, so it
         * always falls into the fragmenting branch. Dividing has to be done here instead.
         */
        protected int fleetCount = 0;

        public HiddenSourceAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint,
                                         float maxFleetFp) {
            this(raid, gatheringPoint, maxFleetFp, false, 0);
        }

        public HiddenSourceAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint,
                                         float maxFleetFp, boolean allowLargeType, int fleetCount) {
            super(raid, gatheringPoint, null);
            this.maxFleetFp = maxFleetFp;
            this.allowLargeType = allowLargeType;
            this.fleetCount = fleetCount;
        }

        /**
         * Carve the budget into exactly {@code fleetCount} equal fleets.
         *
         * <p>Mirrors vanilla's remainder absorption so the last fleet swallows any rounding rather
         * than leaving a sliver that spawns a further route.
         */
        @Override
        protected float getFP(String type) {
            if (fleetCount <= 0) return super.getFP(type);

            float each = origSpawnFP / (float) fleetCount;
            if (each > spawnFP) each = spawnFP;
            spawnFP -= each;
            if (spawnFP < getFPSmall() * 0.5f) {
                each += spawnFP;
                spawnFP = 0f;
            }
            return each;
        }

        @Override
        public boolean isSourceKnown() {
            return false;
        }

        /** Caps the "one big fleet" carve that opens {@code getFP()}. */
        @Override
        protected float getLargeSize(boolean limitToSpawnFP) {
            float base = super.getLargeSize(limitToSpawnFP);
            if (maxFleetFp > 0f && base > maxFleetFp) return maxFleetFp;
            return base;
        }

        /** Caps the PATROL_LARGE branch too. Without this, the remainder after the big carve can
         *  still come back out as a single ~85 FP (x1.25 jitter) fleet, undoing half the point. */
        @Override
        protected float getFPLarge() {
            float base = super.getFPLarge();
            if (maxFleetFp > 0f && base > maxFleetFp) return maxFleetFp;
            return base;
        }

        /**
         * Never type a fleet PATROL_LARGE -- interdiction raiders are Lances and Furies, not
         * Armadas (Sean, 2026-08-02).
         *
         * <p><b>This, not {@code maxFleetFp}, is what controls the name.</b> Fleet SIZE and fleet
         * TYPE are decided separately: {@code getFP()} sets the FP, while
         * {@code pickNextType()} picks the type that Ashen's {@code fleetTypeNames} maps to a
         * display name (patrolSmall -> "Lance", patrolMedium -> "Fury", patrolLarge -> "Armada").
         *
         * <p>Worth knowing because it is counter-intuitive: vanilla's first branch returns
         * PATROL_LARGE whenever {@code spawnFP >= getLargeSize(true) + getFPMedium()}, and
         * capping {@code getLargeSize} LOWERED that threshold from ~175 to ~95 -- so the size cap
         * added on 2026-08-02 is what started producing Armadas, and lowering the cap further
         * makes it slightly more frequent, not less. Suppressing the type here is the actual fix;
         * the FP cap governs how big they are, nothing more.
         *
         * <p>Mirrors vanilla's own alternating picker with the LARGE option removed, so
         * consecutive fleets still vary.
         */
        @Override
        protected String pickNextType() {
            // Phase 4's armadas: EVERY fleet is an Armada, not an alternating mix.
            //
            // Vanilla's picker deliberately alternates (it will not repeat prevType), so letting it
            // run would give "Ashen Crusade Armada" followed by "Ashen Crusade Fury" -- and the
            // whole point of wave 3 is that capital groups arrive. With fleetCount now fixing the
            // SIZE of each fleet, the type only decides the NAME, so pinning it is safe.
            if (allowLargeType && fleetCount > 0) return FleetTypes.PATROL_LARGE;
            if (allowLargeType) return super.pickNextType();

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

    // --- Saved state ---
    // None beyond the interval: live ops are derived from the intel manager (countActive).

    // --- Transient config/runtime (reloaded each launch, so tuning edits land on restart) ---
    protected transient boolean configLoaded;
    protected transient IntervalUtil interval;
    protected transient Random random;
    /** ASH-17: the setpiece checks (rehearsal, Culann waves) retry on this, not every frame. */
    protected transient IntervalUtil setpieceTick;
    /** ASH-17: the last rehearsal blocking reason logged, so a weeks-long block logs once. */
    protected transient String rehearsalBlockLogged;

    protected transient boolean enabled = true;
    protected transient float cadenceDays = 30f;
    protected transient int maxActive = 1;
    protected transient int spawnFpMin = 130;
    protected transient int spawnFpMax = 190;
    protected transient float abortFraction = 0.5f;
    protected transient float presenceDays = 30f;
    protected transient int maxShipSize = 3;
    protected transient float feasibilityMult = 0.95f; // STRENGTH units since ASH-8
    protected transient float systemCooldownDays = 30f;
    protected transient float bountyPerFrigate = 3000f;
    /** War score at or above which raiders press onto the docks. Ratified 0.25. */
    protected transient float boldWarScore = 0.25f;
    /** Per-fleet FP ceiling -- see {@link HiddenSourceAssembleStage}. 0 restores vanilla sizing. */
    protected transient float maxFleetFp = 45f;
    protected transient List targetMarketIds; // String

    // --- Pass 7: Phase 4, the Culann strike (§7). Defaults mirror director_config.json. ---
    protected transient boolean culannEnabled = true;
    protected transient String culannMarketId = "culann";
    /** Days INTO Phase 4 that each wave launches. Wave 3 gets the widest gap on purpose --
     *  it is the beat that matters most, and the gap absorbs arrival jitter. */
    protected transient float culannSweepDay = 0f;
    protected transient float culannBlockadeDay = 4f;
    protected transient float culannArmadaDay = 9f;
    /**
     * Wave 1: light fleets that disrupt the defenders rather than threatening them.
     *
     * <p><b>Count is explicit, and the per-fleet cap is derived from it</b> -- see
     * {@link #fleetCapFor}. Expressing the cap directly (as this did until 2026-08-15) makes FP and
     * fleet COUNT the same dial, so raising strength silently splits the wave into more, smaller
     * fleets instead of making it heavier.
     */
    protected transient float culannSweepFp = 270f;
    protected transient int culannSweepFleets = 6;
    protected transient int culannSweepMaxShipSize = 2;
    /** Wave 2: one entry per fleet, so two entries = the ratified two-fleet cap. Also the only
     *  ship-size lever on the blockade pipeline -- vanilla builds these fleets off these numbers
     *  and the faction roster, with no maxShipSize to set. */
    protected transient List culannBlockadeFleetSizes; // Integer
    /**
     * Wave 3: FP split explicitly across two ops, and <b>fleet counts are hard, not emergent</b>
     * (Sean, 2026-08-15).
     *
     * <p>The playtest caught the old arrangement out. With the cap expressed as a flat
     * {@code maxFleetFp} of 400, a {@code screenFp} of 1000 produced <b>three</b> screening fleets
     * and a run of 5 armada fleets against an agreed cap of 3 -- raising the dial bought quantity
     * where Sean wanted quality. Counts are now the contract and the per-fleet cap is derived, so
     * {@code assaultFp} and {@code screenFp} are pure strength dials: double the FP and each fleet
     * doubles, rather than the group splitting in two.
     */
    protected transient float culannAssaultFp = 800f;
    protected transient int culannAssaultFleets = 2;
    protected transient float culannScreenFp = 375f;
    protected transient int culannScreenFleets = 1;
    protected transient int culannArmadaMaxShipSize = 4;
    /** COMPLETE name -- no faction prefix is prepended (Sean, 2026-08-13). */
    protected transient String culannScreenFleetName = "Knights of Gilead Holy Armada";
    protected transient int culannUnrestPoints = 2;
    /** culannStrike.maxDays: the armada's window over Culann; restored by the stage after vanilla's
     *  arrival roll (inherited from the rehearsal fix, 2026-09-19). */
    protected transient float culannMaxDays = 45f;

    // --- Phase-3 blockade (Pass 6a, §6.4). Own kill switch: this is the component that will
    //     eventually hijack market interaction, so it must be switchable off independently of
    //     the interdiction raids it rides along with. ---
    protected transient boolean blockadeEnabled = true;
    /** Vanilla's own default. NOT flat -- BlockadeFGI scales it by relative fleet strength
     *  (0 / half / full). Exposed so it is tunable without deviating from vanilla by default. */
    protected transient float blockadeAccessibilityPenalty = 0.6f;
    protected transient float blockadePrepDays = 7f;
    /** Upper bound only: {@link AshenBlockade#shouldAbort()} ends it with the raid. */
    protected transient float blockadePayloadDays = 45f;
    /** Fleet sizes on vanilla's 1-10 scale. First/largest becomes the named Hussar anchor;
     *  the rest are pickets carrying NPCHassler. */
    protected transient List blockadeFleetSizes; // Integer
    /** Route-damage break-off fraction. 0.67 since 2026-09-28 (Sean's ruling, superseding §6.4's
     *  50%) to agree with vanilla's live group abort -- survivors below 33% of spawned FP, which
     *  vanilla DOES enforce in FleetGroupIntel.advance (the old "no vanilla abort" note was wrong). */
    protected transient float blockadeAbortFraction = 0.67f;
    /** FALSE by default: the vanilla flag behind this is PLAYER-scoped, not faction-scoped, and
     *  with it true the blockade hunted the player instead of holding station. See launchBlockade. */
    protected transient boolean blockadeMakeFleetsHostile = false;

    // --- Pass 6c: the Phase-3 rehearsal (§6.4) ---
    protected transient boolean rehearsalEnabled = true;
    /** Ordered, escalating: index 0 early, index 1 once war score crosses the threshold. */
    protected transient List rehearsalTargetIds; // String
    protected transient float rehearsalFpMin = 160f;
    protected transient float rehearsalFpMax = 220f;
    /** 3 = cruisers. Higher than interdiction's 2 ON PURPOSE -- see loadConfig. */
    protected transient int rehearsalMaxShipSize = 3;
    protected transient float rehearsalMaxFleetFp = 90f;
    protected transient int rehearsalDisruptDays = 30;
    protected transient int rehearsalUnrestPoints = 2;
    /** rehearsal.maxDays: how long the assault group holds over the target before an offscreen op
     *  autoresolves. Restored by the stage after vanilla's arrival roll overwrites it (2026-09-19). */
    protected transient float rehearsalMaxDays = 40f;

    public AshenInterdictionManager() {
        initTransients();
    }

    protected Object readResolve() {
        initTransients();
        return this;
    }

    protected void initTransients() {
        configLoaded = false;
        interval = null;
        random = new Random();
        targetMarketIds = null;
        setpieceTick = null;
        rehearsalBlockLogged = null;
    }

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void advance(float amount) {
        if (Global.getSector() == null || Global.getSector().isPaused()) return;
        if (Global.getSector().isInNewGameAdvance()) return;
        if (!configLoaded) loadConfig();
        if (random == null) random = new Random();
        if (interval == null) {
            interval = new IntervalUtil(cadenceDays * 0.75f, cadenceDays * 1.25f);
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        interval.advance(days);

        AshenWarDirector director = AshenWarDirector.get();
        if (director == null) return;

        // Pass 6c: Phase 3 mounts a rehearsal, and it is checked OUTSIDE the raid cadence.
        //
        // FIXED 2026-08-09 after the live test. This used to sit behind intervalElapsed(), so
        // the rehearsal did not sail until the 30-day interdiction interval next came round --
        // cycle 0 launched at t=470291 against a Phase 3 that ended at t=471327, i.e. as the
        // phase was already closing. The rehearsal is one deliberate operation per phase, not a
        // cadence-driven raid, so it wants to go as soon as Phase 3 opens.
        //
        // ASH-17 (2026-10-06): both setpiece checks run on a quarter-day tick, not every frame.
        // The old "int compare" defence held only AFTER a launch; while a rehearsal was blocked
        // (a live raid in its system, no source) it re-ran target and source picking every frame
        // for weeks. A few hours of latency is invisible against a phase measured in months.
        if (setpieceTick == null) setpieceTick = new IntervalUtil(0.2f, 0.3f);
        setpieceTick.advance(days);
        boolean setpieceDue = setpieceTick.intervalElapsed();

        if (setpieceDue && raidsEnabled() && director.getPhase() == AshenWarDirector.PHASE_BLOCKADE) {
            tryLaunchRehearsal(director);
        }

        // Pass 7: Phase 4 runs its own wave schedule, OUTSIDE the raid cadence -- for the same
        // reason the rehearsal does. The timing of the waves IS the setpiece, and a 30-day
        // interval would shred it (that is Finding 2 from 2026-08-09, where the rehearsal sat
        // behind this interval and sailed as its phase was closing). The quarter-day setpiece
        // tick above is fine-grained enough to keep the waves together.
        if (setpieceDue && director.getPhase() == AshenWarDirector.PHASE_ASSAULT) {
            advanceCulannStrike(director);
        }

        if (!interval.intervalElapsed()) return;
        if (!raidsEnabled()) return;
        if (!isRaidingPhase(director.getPhase())) return;
        if (countActive() >= maxActive) return;

        tryLaunch(director);
    }

    /**
     * Interdiction runs in <b>Phase 2 only</b> (amended 2026-08-09, §6.4).
     *
     * <p>It used to include Phase 3. The rehearsal replaced it there: Phase 2 is the raiding
     * phase, and the rehearsal brings its own screen fleets for the patrol problem, so running
     * both would have stacked three concurrent Ashen op types against two target systems.
     *
     * <p>This is also what removes Crusade Harassment from Phase 3 -- that condition is applied
     * by {@code AshenInterdictionStage}, so no Phase-3 raid means no Phase-3 harassment, and the
     * invisible -4 supplies/fuel stack with the Black Fleet's Corsair Activity (§14.5) cannot
     * occur there. Vanilla {@code Blockaded} carries Phase 3 instead, and is accessibility-only.
     */
    protected boolean isRaidingPhase(int phase) {
        return phase == AshenWarDirector.PHASE_INTERDICTION;
    }

    /** Config default, overridable at runtime by quests via the memory flag. */
    protected boolean raidsEnabled() {
        Object flag = Global.getSector().getMemoryWithoutUpdate().get(RAIDS_ENABLED_KEY);
        if (flag instanceof Boolean) {
            return ((Boolean) flag).booleanValue();
        }
        return enabled;
    }

    /** Live ops, derived from the intel manager rather than a bookkeeping list -- nothing to
     *  prune, nothing to keep in sync, and the count stays correct while raids are disabled
     *  (the Black Fleet review-item-6 lesson). */
    protected int countActive() {
        int n = 0;
        List ops = Global.getSector().getIntelManager().getIntel(AshenRaidIntel.class);
        for (int i = 0; i < ops.size(); i++) {
            AshenRaidIntel op = (AshenRaidIntel) ops.get(i);
            if (op.isEnded() || op.isEnding()) continue;
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Launch
    // ------------------------------------------------------------------

    /** Budget rolls BEFORE target selection: the feasibility gate needs to know what this op
     *  can plausibly take on (the Black Fleet's budget-blind picks suicided into defended
     *  systems until this order was fixed). */
    protected void tryLaunch(AshenWarDirector director) {
        MarketAPI source = pickSource();
        if (source == null) return;

        float fp = spawnFpMin + random.nextFloat() * (float) (spawnFpMax - spawnFpMin);
        fp *= director.getFpMult();

        StarSystemAPI target = pickTarget(source, fp);
        if (target == null) {
            // ASH-18 (bug sweep, 2026-10-06): one line per failed cadence attempt.
            Global.getLogger(AshenInterdictionManager.class).info(
                    "Ashen interdiction attempt: no eligible target (FP=" + (int) fp + ") -- every"
                            + " system filtered (cooldown, live op, player colony, ownership, or the"
                            + " feasibility lines above)");
            return;
        }

        launchOp(director, source, target, fp);
    }

    /** An Ashen market with a functional spaceport -- raids stage from somewhere real, and a
     *  Crusade that has lost its ports has lost its reach. */
    protected MarketAPI pickSource() {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market.isHidden() || !market.isInEconomy()) continue;
            if (!FACTION_ID.equals(market.getFactionId())) continue;
            Industry spaceport = Misc.getSpaceport(market);
            if (spaceport == null || !spaceport.isFunctional()) continue;
            picker.add(market, (float) market.getSize());
        }
        return (MarketAPI) picker.pick();
    }

    /** A system holding one of the configured Tri-Tachyon target markets that this op can
     *  plausibly contest. Player-colony systems are code-exempt; systems on the anti-hotspot
     *  cooldown are skipped. Closer to the source is NOT weighted -- there are only three
     *  candidates and the Crusade is picking a war target, not the nearest opportunity. */
    protected StarSystemAPI pickTarget(MarketAPI source, float fp) {
        FactionAPI ashen = Global.getSector().getFaction(FACTION_ID);
        if (ashen == null || targetMarketIds == null) return null;

        // UNITS (ASH-8, 2026-10-06): the gate below compares STRENGTH with strength, as the
        // Heg-League gates do (doc sec 14.14). WarSimScript defense is effective strength; vanilla
        // converts op FP with Misc.getAdjustedStrength(fp, source). k = strength per FP, logged
        // once per pass (it is per source and budget). No source = raw FP (k 1).
        float opStrength = fp;
        if (source != null && fp > 0f) {
            float adjusted = Misc.getAdjustedStrength(fp, source);
            opStrength = adjusted;
            float k = adjusted / fp;
            Global.getLogger(AshenInterdictionManager.class).info(
                    "Exiles units [Ashen interdiction]: source=" + source.getName()
                            + " fp=" + (int) fp + " adjustedStrength=" + (int) adjusted
                            + " k=" + (Math.round(k * 100f) / 100f)
                            + " feasibilityMult=" + feasibilityMult
                            + (feasibilityMult > 0f ? " requiredOdds="
                                    + (Math.round(1f / feasibilityMult * 100f) / 100f) + ":1"
                                    : " (gate disabled)"));
        }

        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        List seen = new ArrayList(); // StarSystemAPI

        for (int i = 0; i < targetMarketIds.size(); i++) {
            MarketAPI m = Global.getSector().getEconomy()
                    .getMarket((String) targetMarketIds.get(i));
            if (m == null || m.isHidden() || !m.isInEconomy()) continue;
            // The target list is Tri-Tachyon's; if a market has changed hands it is no longer
            // this war's business.
            if (!Factions.TRITACHYON.equals(m.getFactionId())) continue;
            if (!m.getFaction().isHostileTo(ashen)) continue;
            if (!(m.getContainingLocation() instanceof StarSystemAPI)) continue;

            StarSystemAPI system = (StarSystemAPI) m.getContainingLocation();
            if (seen.contains(system)) continue;
            seen.add(system);

            if (!Misc.getMarketsInLocation(system, Factions.PLAYER).isEmpty()) continue;
            if (system.getMemoryWithoutUpdate().getBoolean(SYSTEM_COOLDOWN_KEY)) continue;
            // Never two Crusade ops in one system at once (fix 2026-08-02). Two overlapping
            // raids both apply Crusade Harassment to the same market, and whichever withdraws
            // FIRST removes it -- leaving the market un-harassed while the second op's raiders
            // are still visibly working its lanes. Observed in the maxActive=3 test; the same
            // lifecycle collision ruling E.c called out for shared condition ids between
            // directors, which applies just as well to two ops of the SAME director.
            //
            // Checked against live intel rather than the cooldown flag, so it is correct for
            // any maxActive the player sets and self-corrects if an op ends early.
            if (hasLiveOpIn(system)) continue;

            // COMMERCE-tier feasibility: raiders loiter and stop hulls, they do not fight
            // stations, so station strength is excluded and the multiplier is loose.
            //
            // getEnemyStrength sums LIVE Tri-Tachyon patrol strength in the system (station-mode
            // fleets are skipped by vanilla), so it is dynamic -- it cannot be predicted offline
            // and it fluctuates with whatever patrols happen to be home. Hence the logging: four
            // test sessions produced zero Hybrasil raids, and guessing at the multiplier without
            // knowing the actual numbers just burns test runs.
            if (feasibilityMult > 0f) {
                float defense = WarSimScript.getEnemyStrength(ashen, system);
                float gate = opStrength * feasibilityMult;
                if (defense > gate) {
                    Global.getLogger(AshenInterdictionManager.class).info(
                            "Ashen interdiction feasibility: " + system.getBaseName()
                                    + " REJECTED (defense " + (int) defense
                                    + " > gate " + (int) gate + " = strength " + (int) opStrength
                                    + " (" + (int) fp + " FP) x " + feasibilityMult + ")");
                    continue;
                }
                Global.getLogger(AshenInterdictionManager.class).info(
                        "Ashen interdiction feasibility: " + system.getBaseName()
                                + " eligible (defense " + (int) defense
                                + " <= gate " + (int) gate + ")");
            }

            picker.add(system, 1f);
        }

        return (StarSystemAPI) picker.pick();
    }

    /** True if one of this director's operations is already live in the system. */
    /**
     * Has Phase-4 wave {@code n} already sailed?
     *
     * <p><b>A memory flag, not derived from the intel manager -- and that is a correction.</b> The
     * first version of this scanned live {@code AshenRaidIntel} for a wave marker, in keeping with
     * the derive-don't-store discipline used everywhere else in this class. It was <i>wrong</i>:
     * ended intel is removed after {@code getBaseDaysAfterEnd()} (3 days), so a sweep that
     * finished inside the 180-day phase would stop being visible and the wave would relaunch --
     * repeatedly. Wave 2 was worse still, since a blockade is a {@code BlockadeFGI} and cannot
     * carry an {@code AshenRaidIntel} marker at all.
     *
     * <p>Derivation only works when the derived-from thing outlives the question being asked. Here
     * it does not, so this is one of the cases where a flag is correct rather than lazy.
     *
     * <p>Set with an expiry of the full Phase-4 window, so a <i>repelled</i> strike -- which
     * returns roughly ten months later off the preparation floor -- finds them long cleared and
     * runs its waves again.
     */
    protected boolean hasPhase4WaveLaunched(int n) {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(PHASE4_WAVE_KEY + n);
    }

    protected void markPhase4WaveLaunched(int n, float expireDays) {
        Global.getSector().getMemoryWithoutUpdate().set(
                PHASE4_WAVE_KEY + n, true, Math.max(1f, expireDays));
    }

    protected boolean hasLiveOpIn(StarSystemAPI system) {
        List ops = Global.getSector().getIntelManager().getIntel(AshenRaidIntel.class);
        for (int i = 0; i < ops.size(); i++) {
            AshenRaidIntel op = (AshenRaidIntel) ops.get(i);
            if (op.isEnded() || op.isEnding()) continue;
            if (op.getSystem() == system) return true;
        }
        return false;
    }

    /** The Tri-Tachyon market the raiders focus on: the biggest one in the system. It is both
     *  the loiter target once the war matures and the "Target" line on the intel. */
    protected MarketAPI pickFocusMarket(StarSystemAPI system) {
        MarketAPI best = null;
        int max = -1;
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m.isHidden() || m.isPlayerOwned()) continue;
            if (!Factions.TRITACHYON.equals(m.getFactionId())) continue;
            if (m.getSize() > max) {
                max = m.getSize();
                best = m;
            }
        }
        return best;
    }

    /** The vanilla pirate-raid structure with the interdiction action stage swapped in. */
    protected void launchOp(AshenWarDirector director, MarketAPI source, StarSystemAPI target,
                            float fp) {
        FactionAPI ashen = Global.getSector().getFaction(FACTION_ID);
        if (ashen == null) return;

        MarketAPI focus = pickFocusMarket(target);
        if (focus == null) return; // no Tri-Tachyon market left to interdict

        AshenRaidIntel raid = new AshenRaidIntel(target, ashen, maxShipSize,
                Factions.TRITACHYON, bountyPerFrigate, systemCooldownDays);

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
                ashen, target.getCenter());
        if (gather == null || raidJump == null) return;

        // NOTE: the system cooldown is NOT set here. It starts when the op ENDS -- see
        // AshenRaidIntel.notifyEnded(). Setting it at launch meant it expired mid-op, because
        // an op outlives 30-45 days easily (travel + presenceDays + return).

        HiddenSourceAssembleStage assemble =
                new HiddenSourceAssembleStage(raid, gather, maxFleetFp);
        assemble.addSource(source);
        assemble.setSpawnFP(fp);
        assemble.setAbortFP(fp * abortFraction);
        raid.addStage(assemble);

        TravelStage travel = new TravelStage(raid, gather, raidJump, false);
        travel.setAbortFP(fp * abortFraction);
        raid.addStage(travel);

        AshenInterdictionStage action = new AshenInterdictionStage(
                raid, target, presenceDays, focus.getId(), boldWarScore);
        action.setAbortFP(fp * abortFraction);
        raid.addStage(action);

        raid.addStage(new ReturnStage(raid));

        // The op stays off the intel board until the raiders arrive. NOTE the hiding comes from
        // AshenRaidIntel's constructor calling setHidden(true) -- this boolean is
        // forceNoMessage, which only suppresses the notification (corrected 2026-08-10).
        Global.getSector().getIntelManager().addIntel(raid, true);

        // Phase 3 only: a blockade rides along with the raid. Launched AFTER the raid intel is
        // registered, because AshenBlockade.shouldAbort() looks for a live companion op and would
        // otherwise abort itself on its very first advance.
        launchBlockade(director, source, target, focus);

        director.debug("Ashen interdiction raid launched against " + target.getBaseName()
                + " (" + (int) fp + " FP, focus " + focus.getName()
                + ", bold=" + (director.getWarScore() >= boldWarScore) + ").");
        Global.getLogger(AshenInterdictionManager.class).info(
                "Ashen interdiction raid launched: " + source.getName() + " -> "
                        + target.getNameWithLowercaseType() + ", FP=" + (int) fp
                        + " (warScore " + director.getWarScore()
                        + ", fpMult " + director.getFpMult()
                        + "), focus=" + focus.getName()
                        + ", bold=" + (director.getWarScore() >= boldWarScore));
    }

    // ------------------------------------------------------------------
    // Pass 6c -- Phase 3: the Rehearsal (§6.4)
    // ------------------------------------------------------------------

    /**
     * Mount the one Phase-3 rehearsal for this war cycle.
     *
     * <p>Exactly one per Phase 3, keyed on the director's cycle counter rather than a boolean, so
     * it cannot be re-armed by a save/load or a forced phase change. {@code cyclesCompleted}
     * increments on leaving Phase 3, so the next Phase 3 always sees a higher value.
     */
    protected void tryLaunchRehearsal(AshenWarDirector director) {
        // Cheapest guards first -- this runs on the quarter-day setpiece tick during Phase 3 (see
        // advance()), and after the one launch the int compare below short-circuits the phase.
        if (!rehearsalEnabled) return;
        if (director.getLastRehearsalCycle() == director.getCyclesCompleted()) return;

        MarketAPI target = pickRehearsalTarget(director);
        if (target == null || !(target.getContainingLocation() instanceof StarSystemAPI)) {
            logRehearsalBlock("no rehearsal target in the economy");
            return;
        }
        StarSystemAPI system = (StarSystemAPI) target.getContainingLocation();

        // SAME-SYSTEM only, not "any live Ashen op" (narrowed 2026-08-09).
        //
        // The broad guard was a second cause of the late launches in the live test: interdiction
        // raids launched in Phase 2 comfortably outlive the phase -- travel plus 30 days of
        // presence plus the return leg -- so a blanket countActive() check held the rehearsal
        // hostage to a raid working a completely different system. The collision that actually
        // matters is two Ashen ops in one system fighting over the same conditions, which is the
        // exact case hasLiveOpIn() was added for in Pass 4.
        if (hasLiveOpIn(system)) {
            logRehearsalBlock("a live Ashen op is already in " + system.getBaseName());
            return;
        }

        MarketAPI source = pickSource();
        if (source == null) {
            logRehearsalBlock("no Ashen market with a functional spaceport");
            return;
        }

        rehearsalBlockLogged = null;
        director.setLastRehearsalCycle(director.getCyclesCompleted());
        launchRehearsal(director, source, system, target);
    }

    /** ASH-17: one log line per blocking reason, not one per retry. */
    protected void logRehearsalBlock(String reason) {
        if (reason.equals(rehearsalBlockLogged)) return;
        rehearsalBlockLogged = reason;
        Global.getLogger(AshenInterdictionManager.class).info(
                "Ashen rehearsal waiting: " + reason + " (retrying every quarter day)");
    }

    /**
     * Tibicena, then Port Tse -- escalating, not randomised (§6.4).
     *
     * <p>Gated on <b>war score</b>, not on cycle count or on the previous rehearsal's success.
     * Cycle count would de-escalate on a third cycle; success-gating would hand the player a
     * switch that stops escalation outright. War score gives Sean's "why move on to 2 if they
     * can't get 1 right" for free anyway -- a failed rehearsal costs score, which delays crossing
     * the threshold, which keeps the Crusade at Tibicena. Same intuition, no brittle gate.
     *
     * <p>Falls back to the first configured target if the escalated one is gone (destroyed,
     * decivilized, or taken by another faction), and skips any that has left the economy.
     */
    protected MarketAPI pickRehearsalTarget(AshenWarDirector director) {
        if (rehearsalTargetIds == null || rehearsalTargetIds.isEmpty()) return null;

        int index = 0;
        if (director.isRehearsalEscalated() && rehearsalTargetIds.size() > 1) index = 1;

        MarketAPI market = getMarketById((String) rehearsalTargetIds.get(index));
        if (market != null) return market;

        for (int i = 0; i < rehearsalTargetIds.size(); i++) {
            market = getMarketById((String) rehearsalTargetIds.get(i));
            if (market != null) return market;
        }
        return null;
    }

    protected static MarketAPI getMarketById(String id) {
        if (id == null || Global.getSector() == null) return null;
        MarketAPI market = Global.getSector().getEconomy().getMarket(id);
        if (market == null || !market.isInEconomy()) return null;
        return market;
    }

    /**
     * Build the rehearsal op: assemble, travel, crack the station, return -- plus the Hussar
     * blockade holding the jump points.
     *
     * <p><b>No launch feasibility gate</b>, unlike interdiction (Sean, 2026-08-09). The targets
     * are fixed and the operation is deliberate, so the Crusade commits what the job needs rather
     * than declining to sail. What keeps it from suiciding is FP scaled by war score plus the
     * damage-abort, and {@code AshenRehearsalStage.canBeat()} still checks the odds at the moment
     * of attack.
     *
     * <p><b>Station attacks are NOT suppressed here.</b> That is the whole difference from an
     * interdiction raid: the assault group is <i>meant</i> to crack the station, while the Hussar
     * blockade fleets are still held off it by {@code AshenBlockade.suppressStationTargeting()}.
     * The two halves of one operation want opposite settings.
     */
    protected void launchRehearsal(AshenWarDirector director, MarketAPI source,
                                   StarSystemAPI system, MarketAPI target) {
        FactionAPI ashen = Global.getSector().getFaction(FACTION_ID);
        if (ashen == null) return;

        float fp = rehearsalFpMin + random.nextFloat() * (rehearsalFpMax - rehearsalFpMin);
        fp *= director.getFpMult();

        AshenRaidIntel raid = new AshenRaidIntel(system, ashen, rehearsalMaxShipSize,
                Factions.TRITACHYON, bountyPerFrigate, systemCooldownDays);

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
                ashen, system.getCenter());
        if (gather == null || raidJump == null) return;

        HiddenSourceAssembleStage assemble =
                new HiddenSourceAssembleStage(raid, gather, rehearsalMaxFleetFp);
        assemble.addSource(source);
        assemble.setSpawnFP(fp);
        assemble.setAbortFP(fp * abortFraction);
        raid.addStage(assemble);

        TravelStage travel = new TravelStage(raid, gather, raidJump, false);
        travel.setAbortFP(fp * abortFraction);
        raid.addStage(travel);

        AshenRehearsalStage action = new AshenRehearsalStage(
                raid, system, target.getId(), rehearsalDisruptDays, rehearsalUnrestPoints,
                rehearsalMaxDays);
        action.setAbortFP(fp * abortFraction);
        raid.addStage(action);
        // The intel reports the war-score outcome on end, whatever path the op took -- see
        // AshenRaidIntel.reportRehearsalOutcome(). This is what marks it as a rehearsal op.
        raid.setRehearsalStage(action);

        raid.addStage(new ReturnStage(raid));

        // Announced early, origin concealed (Sean, 2026-08-09): the player gets a chance to
        // intercept, while HiddenSourceAssembleStage.isSourceKnown() keeps Providence off the
        // board. Amends the §3.4 messaging table for this op only -- interdiction raids stay
        // hidden until arrival.
        Global.getSector().getIntelManager().addIntel(raid, false);

        launchBlockade(director, source, system, target);

        director.debug("Ashen REHEARSAL launched against " + target.getName()
                + " (" + (int) fp + " FP, escalated=" + director.isRehearsalEscalated() + ").");
        Global.getLogger(AshenInterdictionManager.class).info(
                "Ashen rehearsal launched: " + source.getName() + " -> " + target.getName()
                        + " in " + system.getNameWithLowercaseType() + ", FP=" + (int) fp
                        + " (warScore " + director.getWarScore()
                        + ", escalated=" + director.isRehearsalEscalated()
                        + ", cycle " + director.getCyclesCompleted() + ")");
    }

    // ------------------------------------------------------------------
    // Pass 7 -- Phase 4, the Culann strike (§7). Choreography ratified 2026-08-13.
    // ------------------------------------------------------------------

    /**
     * The setpiece, in three waves over roughly ten days.
     *
     * <p><b>No saved state.</b> "Days since the strike began" comes from the phase clock and "has
     * this wave sailed" from the intel manager -- the same derive-don't-store discipline the rest
     * of this manager uses for concurrency. A stored wave counter would be a second source of
     * truth, and would desync the first time a phase is forced or a save is loaded mid-strike.
     */
    protected void advanceCulannStrike(AshenWarDirector director) {
        if (!culannEnabled || director.hasPhase4Succeeded()) return;

        MarketAPI culann = getMarketById(culannMarketId);
        if (culann == null) return;
        if (!(culann.getContainingLocation() instanceof StarSystemAPI)) return;
        StarSystemAPI system = (StarSystemAPI) culann.getContainingLocation();

        float daysIn = director.getPhase4Days() - director.getPhaseDaysRemaining();

        if (daysIn >= culannSweepDay && !hasPhase4WaveLaunched(1)) {
            launchCulannSweep(director, system, culann);
        }
        if (daysIn >= culannBlockadeDay && !hasPhase4WaveLaunched(2)) {
            launchCulannBlockade(director, system, culann);
        }
        if (daysIn >= culannArmadaDay && !hasPhase4WaveLaunched(3)) {
            launchCulannArmadas(director, system, culann);
        }
    }

    /**
     * Wave 1 -- the sweep. Six light fleets, opening the operation.
     *
     * <p>Meant to <b>disrupt the defenders rather than threaten them</b>, so it is an ordinary
     * interdiction op pointed at a system interdiction would never pick: Hybrasil is permanently
     * rejected by the feasibility gate (measured defense 1278-1483 against a gate of ~300). It is
     * therefore launched directly, on the same "deliberate operation, no launch gate" principle
     * Sean ruled for rehearsals.
     *
     * <p>Crusade Harassment lands on Tri-Tachyon only -- {@code applyConditions()} already filters
     * on faction, so Hybrasil's pirate and independent markets are untouched, per ruling J5.
     */
    protected void launchCulannSweep(AshenWarDirector director, StarSystemAPI system,
                                     MarketAPI culann) {
        FactionAPI ashen = Global.getSector().getFaction(FACTION_ID);
        if (ashen == null) return;

        AshenRaidIntel raid = buildCulannOp(ashen, system, culannSweepFp,
                culannSweepFleets, culannSweepMaxShipSize, false);
        if (raid == null) return;

        AshenInterdictionStage loiter = new AshenInterdictionStage(
                raid, system, presenceDays, culann.getId(), boldWarScore);
        loiter.setAbortFP(culannSweepFp * abortFraction);
        raid.addStage(loiter);
        raid.addStage(new ReturnStage(raid));
        raid.setPhase4Wave(1);
        markPhase4WaveLaunched(1, director.getPhase4Days());

        // Hidden: the assault op carries the one visible listing for the whole operation.
        Global.getSector().getIntelManager().addIntel(raid, true);
        Global.getLogger(AshenInterdictionManager.class).info(
                "ASHEN CULANN STRIKE wave 1 (interdiction sweep) launched: "
                        + (int) culannSweepFp + " FP in " + culannSweepFleets + " fleet(s) into "
                        + system.getBaseName());
    }

    /**
     * Wave 2 -- both gates shut. Two fleets, one per jump point.
     *
     * <p>{@code SpreadBlockadeAction} exists for exactly this: vanilla weights the primary gate
     * 1.0 and the rest 1/N, and Hybrasil has two. <b>They enter together</b> at whichever jump
     * point is nearest Providence and one crosses to the far gate -- {@code FleetGroupIntel} drives
     * a whole group from a single {@code RouteData}, so separate arrivals would need two blockade
     * FGIs contending for one system's {@code Blockaded} condition, which is the collision already
     * fixed once with {@code hasLiveOpIn()}. Sean informed 2026-08-13; accepted as is.
     */
    protected void launchCulannBlockade(AshenWarDirector director, StarSystemAPI system,
                                        MarketAPI culann) {
        MarketAPI source = pickSource();
        if (source == null) return;

        launchBlockade(director, source, system, culann, culannBlockadeFleetSizes);
        markPhase4WaveLaunched(2, director.getPhase4Days());

        // ASH-18 (bug sweep, 2026-10-06): launchBlockade returns silently when blockades are
        // switched off, so say which happened instead of always claiming a launch. The wave is
        // marked either way (unchanged behaviour).
        Global.getLogger(AshenInterdictionManager.class).info(
                "ASHEN CULANN STRIKE wave 2 (gate blockade) "
                        + (blockadeEnabled
                                ? "launched: " + culannBlockadeFleetSizes.size() + " fleets holding "
                                        + system.getBaseName() + "'s jump points"
                                : "SKIPPED: blockade.enabled is false (wave marked as done)"));
    }

    /**
     * Wave 3 -- the capitals, in two operations. <b>This is the wave that makes Phase 4 function</b>
     * (the other two are theatre around it), and the beat Sean cares most about.
     *
     * <p>The assault op carries {@link AshenCulannStrikeStage} and cracks Culann. The screen is a
     * <i>separate loitering op</i>, and that separation is the whole trick: scripting one fleet to
     * hunt another is the expensive, brittle path, because vanilla raid stages target markets and
     * not fleets. With the assault locked onto Culann, the loitering armada is the natural thing
     * for Tri-Tachyon's Capital Assurance Reserve to engage -- the screening reads as intended
     * without a line of targeting code. Being its own op is also what gives the screen its own
     * name for free.
     */
    protected void launchCulannArmadas(AshenWarDirector director, StarSystemAPI system,
                                       MarketAPI culann) {
        FactionAPI ashen = Global.getSector().getFaction(FACTION_ID);
        if (ashen == null) return;

        // --- The assault. Engine-composed "Ashen Crusade Armada" names, hence allowLargeType. ---
        AshenRaidIntel assault = buildCulannOp(ashen, system, culannAssaultFp,
                culannAssaultFleets, culannArmadaMaxShipSize, true);
        if (assault != null) {
            AshenCulannStrikeStage strike = new AshenCulannStrikeStage(
                    assault, system, culann.getId(), culannUnrestPoints, culannMaxDays);
            strike.setAbortFP(culannAssaultFp * abortFraction);
            assault.addStage(strike);
            assault.addStage(new ReturnStage(assault));
            // Marks it as the op whose outcome opens or closes the Phase-4 branch.
            assault.setRehearsalStage(strike);
            assault.setPhase4Wave(3);

            // THE ONE VISIBLE ENTRY (Sean, 2026-08-13). Four ops would read as four unrelated
            // events; §7.0 wants the strike announced at launch rather than on arrival, so this
            // op speaks for the whole operation and the rest stay hidden.
            assault.setHidden(false);
            Global.getSector().getIntelManager().addIntel(assault, false);
        }

        // --- The screen. One fleet, loitering, explicitly named. ---
        AshenRaidIntel screen = buildCulannOp(ashen, system, culannScreenFp,
                culannScreenFleets, culannArmadaMaxShipSize, true);
        if (screen != null) {
            AshenInterdictionStage loiter = new AshenInterdictionStage(
                    screen, system, presenceDays, culann.getId(), boldWarScore);
            loiter.setAbortFP(culannScreenFp * abortFraction);
            loiter.setFleetName(culannScreenFleetName);
            // NO CRUSADE HARASSMENT (Sean, 2026-08-15). The screen is a military body, not an
            // economic one, and the playtest showed it applying the condition to both Hybrasil
            // markets. With the sweep applying the same condition to the same markets, the two
            // ops would share it -- and the Pass-4 finding is that the first to withdraw removes
            // it while the other is still live. hasLiveOpIn() normally prevents that, but Phase 4
            // runs several ops in one system by design and bypasses the guard.
            loiter.setApplyHarassment(false);
            screen.addStage(loiter);
            screen.addStage(new ReturnStage(screen));
            screen.setPhase4Wave(4); // distinct marker; wave 3 covers "the armadas have gone"
            Global.getSector().getIntelManager().addIntel(screen, true);
        }

        markPhase4WaveLaunched(3, director.getPhase4Days());

        // NOTE: no player message here. The capitals are the beat that matters most, and at THIS
        // moment they are still an assemble and a transit away from Hybrasil. The announcement
        // fires on actual arrival -- see AshenCulannStrikeStage.announceArrival().
        Global.getLogger(AshenInterdictionManager.class).info(
                "ASHEN CULANN STRIKE wave 3 (armadas) launched against " + culann.getName()
                        + ": assault " + (int) culannAssaultFp + " FP in " + culannAssaultFleets
                        + " fleet(s), screen \"" + culannScreenFleetName + "\" "
                        + (int) culannScreenFp + " FP in " + culannScreenFleets + " fleet(s)");
        director.debug("Ashen CULANN STRIKE: the armadas have sailed.");
    }

    /**
     * The shared skeleton every Phase-4 wave sits on: assemble at a concealed source, travel, and
     * nothing else. Callers add their own action stage and a {@link ReturnStage}.
     *
     * <p>Split out because three waves want the identical opening and only differ in what they do
     * on arrival -- and because building it once means the jump-point resolution is verified once.
     *
     * @param allowLargeType let the engine type these fleets PATROL_LARGE, i.e. "Armada".
     * @return the op, or null if no source or route could be resolved.
     */
    /**
     * @param fleets exact number of fleets to carve the budget into. Passed to the assemble stage
     *               rather than converted into an FP ceiling: a ceiling cannot divide a budget,
     *               because vanilla's last chunk is always below the ceiling and falls into the
     *               fragmenting branch. See {@code HiddenSourceAssembleStage.fleetCount}.
     */
    protected AshenRaidIntel buildCulannOp(FactionAPI ashen, StarSystemAPI system, float fp,
                                           int fleets, int maxShipSizeForOp,
                                           boolean allowLargeType) {
        MarketAPI source = pickSource();
        if (source == null) return null;

        AshenRaidIntel raid = new AshenRaidIntel(system, ashen, maxShipSizeForOp,
                Factions.TRITACHYON, bountyPerFrigate, systemCooldownDays);

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
                ashen, system.getCenter());
        if (gather == null || raidJump == null) return null;

        HiddenSourceAssembleStage assemble = new HiddenSourceAssembleStage(
                raid, gather, fp, allowLargeType, fleets);
        assemble.addSource(source);
        assemble.setSpawnFP(fp);
        assemble.setAbortFP(fp * abortFraction);
        raid.addStage(assemble);

        TravelStage travel = new TravelStage(raid, gather, raidJump, false);
        travel.setAbortFP(fp * abortFraction);
        raid.addStage(travel);

        return raid;
    }

    /**
     * Phase-3 blockade (§6.4, Pass 6a): a Hussar Blockade Fleet and pickets accompany the raid,
     * hold the system's Tri-Tachyon market, and leave when the raid does.
     *
     * <p>No target selection of its own -- it inherits the raid's already-feasibility-gated system
     * and its focus market. That is deliberate: a second targeting path is a second thing that can
     * disagree with the first, and §6.4's "least-defended Tri-Tachyon market" resolves to the same
     * market anyway, since Magec and Mayasura hold exactly one Tri-Tachyon market each.
     *
     * <p>Everything after construction is vanilla's: fleet creation, travel, the {@code Blockaded}
     * condition, intel, and cleanup on both {@code notifyEnding()} and abort.
     */
    protected void launchBlockade(AshenWarDirector director, MarketAPI source,
                                  StarSystemAPI target, MarketAPI focus) {
        launchBlockade(director, source, target, focus, null);
    }

    /**
     * @param fleetSizesOverride Phase 4's own fleet list, or null to use the Phase-3 configuration.
     *                           One entry per fleet, so Phase 4's two entries ARE its two-fleet cap.
     *                           Ship composition comes from vanilla's spawn path off these numbers
     *                           -- there is no separate ship-size lever on this pipeline, which is
     *                           why heavier gate-holders means bigger entries, not a size cap.
     */
    protected void launchBlockade(AshenWarDirector director, MarketAPI source,
                                  StarSystemAPI target, MarketAPI focus, List fleetSizesOverride) {
        if (!blockadeEnabled) return;
        // Phases 3 AND 4 (widened for Pass 7). Phase 3 escorts a rehearsal; Phase 4 shuts
        // Hybrasil's two gates so Culann cannot be reinforced while the armadas work.
        int phase = director.getPhase();
        if (phase != AshenWarDirector.PHASE_BLOCKADE && phase != AshenWarDirector.PHASE_ASSAULT) {
            return;
        }
        if (source == null || target == null || focus == null) return;

        // playerTargeted = false is the switch that makes this an NPC-vs-NPC operation (§12 O3):
        // it gates intel updates and keeps the player's fleet out of the defender-strength sum.
        GenericRaidParams raidParams = new GenericRaidParams(random, false);
        raidParams.factionId = FACTION_ID;
        raidParams.source = source;
        raidParams.prepDays = blockadePrepDays;
        raidParams.payloadDays = blockadePayloadDays;
        // FALSE, and this is load-bearing (live test 2026-08-06). makeFleetsHostile calls
        // FleetCreatorMission.triggerMakeHostile(), which sets MemFlags.MEMORY_KEY_MAKE_HOSTILE
        // -- "$cfai_makeHostile". That is the PLAYER-scoped campaign-fleet-AI family (siblings:
        // MAKE_HOSTILE_TO_PLAYER_TRADE_FLEETS, AVOID_PLAYER_SLOWLY, ALLOW_PLAYER_BATTLE_JOIN_TOFF;
        // §14.4 Finding A established the same for MAKE_HOLD_VS_STRONGER). With it true, the
        // blockade flew past the station it was blockading and hunted the player instead.
        // Vanilla agrees: KnightsOfLuddTakeoverExpedition sets this false even though it targets
        // the player's own colony. Ashen is already hostile to Tri-Tachyon by faction relations,
        // so nothing is lost -- the raid stages never set this flag either, which is why raiders
        // ignore the player and the blockade did not.
        raidParams.makeFleetsHostile = blockadeMakeFleetsHostile;
        raidParams.noun = "blockade";
        raidParams.forcesNoun = "blockading forces";
        raidParams.raidParams.where = target;
        raidParams.raidParams.allowedTargets.add(focus);

        List sizes = fleetSizesOverride != null ? fleetSizesOverride : blockadeFleetSizes;
        for (int i = 0; i < sizes.size(); i++) {
            raidParams.fleetSizes.add(sizes.get(i));
        }

        FGBlockadeParams bParams = new FGBlockadeParams();
        bParams.where = target;
        bParams.targetFaction = Factions.TRITACHYON;
        bParams.specificMarket = focus;
        bParams.accessibilityPenalty = blockadeAccessibilityPenalty;
        bParams.patrolText = "blockading " + focus.getName();

        AshenBlockade blockade = new AshenBlockade(raidParams, bParams);
        blockade.setAbortDamageFraction(blockadeAbortFraction);
        // ASH-4 / ASH-5 (ruled 2026-10-06): the pickets leave EVERY market station alone, for the
        // rehearsal escort and the Culann wave-2 blockade alike -- blockades stop hulls, not stations.
        blockade.setSuppressEveryStation(true);
        // ONE OPERATION, ONE LISTING. The blockade and the op it escorts are a single operation to
        // the player, but they are two intel objects on two pipelines, so they listed separately,
        // arrived at different times, and the blockade then reported FAILED when it withdrew with
        // the (successful) op. The rehearsal intel speaks for both -- see
        // AshenRaidIntel.createSmallDescription(). Fleets stay visible in space; only the
        // duplicate listing goes.
        //
        // *** setHidden() IS THE MECHANISM, NOT the addIntel flag. *** Corrected 2026-08-10 after
        // the first attempt failed in game: IntelManagerAPI declares
        // addIntel(IntelInfoPlugin, boolean forceNoMessage) -- that boolean suppresses the
        // NOTIFICATION, not the entry. AshenRaidIntel is hidden because its constructor calls
        // setHidden(true); the addIntel(raid, true) in launchOp() was only ever silencing the
        // message. Hiding must be set on the intel itself.
        blockade.setHidden(true);
        Global.getSector().getIntelManager().addIntel(blockade, true);

        director.debug("Ashen blockade launched against " + focus.getName()
                + " in " + target.getBaseName() + ".");
        Global.getLogger(AshenInterdictionManager.class).info(
                "Ashen blockade launched: " + source.getName() + " -> " + focus.getName()
                        + " in " + target.getNameWithLowercaseType()
                        + ", sizes=" + sizes // the list ACTUALLY used, override included
                        + ", penalty=" + blockadeAccessibilityPenalty);
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    protected void loadConfig() {
        configLoaded = true;
        applyDefaults();

        float timeScale = 1f;
        try {
            JSONObject root = Global.getSettings().loadJSON(CONFIG_PATH);
            JSONObject faction = root.optJSONObject(CONFIG_KEY);
            if (faction == null) return;
            // Shared with the director so ONE knob compresses the whole war (§9.1).
            timeScale = (float) faction.optDouble("timeScale", 1d);
            if (timeScale <= 0f) timeScale = 1f;

            JSONArray targets = faction.optJSONArray("targetMarkets");
            if (targets != null && targets.length() > 0) {
                targetMarketIds = new ArrayList();
                for (int i = 0; i < targets.length(); i++) {
                    targetMarketIds.add(targets.getString(i));
                }
            }

            JSONObject cfg = faction.optJSONObject("interdiction");
            if (cfg != null) {
                enabled = cfg.optBoolean("enabled", enabled);
                cadenceDays = (float) cfg.optDouble("cadenceDays", cadenceDays);
                maxActive = cfg.optInt("maxActive", maxActive);
                spawnFpMin = cfg.optInt("spawnFpMin", spawnFpMin);
                spawnFpMax = cfg.optInt("spawnFpMax", spawnFpMax);
                abortFraction = (float) cfg.optDouble("abortFraction", abortFraction);
                presenceDays = (float) cfg.optDouble("presenceDays", presenceDays);
                maxShipSize = cfg.optInt("maxShipSize", maxShipSize);
                feasibilityMult = (float) cfg.optDouble("feasibilityMult", feasibilityMult);
                systemCooldownDays = (float) cfg.optDouble(
                        "systemCooldownDays", systemCooldownDays);
                bountyPerFrigate = (float) cfg.optDouble("bountyPerFrigate", bountyPerFrigate);
                boldWarScore = (float) cfg.optDouble("boldWarScore", boldWarScore);
                maxFleetFp = (float) cfg.optDouble("maxFleetFp", maxFleetFp);

                AshenInterdictionStage.CONDITION_APPLY_DAY = (float) cfg.optDouble(
                        "conditionDelayDays", AshenInterdictionStage.CONDITION_APPLY_DAY);
                AshenInterdictionStage.suppressStationAttacks = cfg.optBoolean(
                        "suppressStationAttacks", AshenInterdictionStage.suppressStationAttacks);
                ExileCrusadeHarassment.DURATION_DAYS = (float) cfg.optDouble(
                        "conditionDays", ExileCrusadeHarassment.DURATION_DAYS);
                ExileCrusadeHarassment.ACCESSIBILITY_PENALTY = (float) cfg.optDouble(
                        "accessibilityPenalty", ExileCrusadeHarassment.ACCESSIBILITY_PENALTY);
                ExileCrusadeHarassment.IMPORT_HIT_COUNT = cfg.optInt(
                        "importHitCount", ExileCrusadeHarassment.IMPORT_HIT_COUNT);
                ExileCrusadeHarassment.IMPORT_HIT_UNITS = cfg.optInt(
                        "importHitUnits", ExileCrusadeHarassment.IMPORT_HIT_UNITS);
            }

            JSONObject blk = faction.optJSONObject("blockade");
            if (blk != null) {
                blockadeEnabled = blk.optBoolean("enabled", blockadeEnabled);
                blockadeAccessibilityPenalty = (float) blk.optDouble(
                        "accessibilityPenalty", blockadeAccessibilityPenalty);
                blockadePrepDays = (float) blk.optDouble("prepDays", blockadePrepDays);
                blockadePayloadDays = (float) blk.optDouble("payloadDays", blockadePayloadDays);
                blockadeAbortFraction = (float) blk.optDouble(
                        "abortFraction", blockadeAbortFraction);
                AshenBlockade.suppressStationAttacks = blk.optBoolean(
                        "suppressStationAttacks", AshenBlockade.suppressStationAttacks);
                blockadeMakeFleetsHostile = blk.optBoolean(
                        "makeFleetsHostile", blockadeMakeFleetsHostile);

                JSONArray sizes = blk.optJSONArray("fleetSizes");
                if (sizes != null && sizes.length() > 0) {
                    blockadeFleetSizes = new ArrayList();
                    for (int i = 0; i < sizes.length(); i++) {
                        blockadeFleetSizes.add(new Integer(sizes.getInt(i)));
                    }
                }
            }

            // Pass 6c. NOTE the deliberate contradiction with interdiction.maxShipSize above:
            // that one is pinned at 2 (destroyers) SPECIFICALLY so raiders cannot take a
            // Tri-Tachyon station. The rehearsal wants exactly that capability, so it runs 3.
            // Do not "fix" one to match the other -- they encode opposite intents.
            JSONObject reh = faction.optJSONObject("rehearsal");
            if (reh != null) {
                rehearsalEnabled = reh.optBoolean("enabled", rehearsalEnabled);
                rehearsalFpMin = (float) reh.optDouble("spawnFpMin", rehearsalFpMin);
                rehearsalFpMax = (float) reh.optDouble("spawnFpMax", rehearsalFpMax);
                rehearsalMaxShipSize = reh.optInt("maxShipSize", rehearsalMaxShipSize);
                rehearsalMaxFleetFp = (float) reh.optDouble("maxFleetFp", rehearsalMaxFleetFp);
                rehearsalDisruptDays = reh.optInt("disruptDays", rehearsalDisruptDays);
                rehearsalUnrestPoints = reh.optInt("unrestPoints", rehearsalUnrestPoints);
                rehearsalMaxDays = (float) reh.optDouble("maxDays", rehearsalMaxDays);

                // JANINO: this array must NOT be named "targets" -- one is already declared in
                // an enclosing block of this method, and Janino rejects the shadowing that javac
                // permits. Same family as the "cast every List.get()" rule in CLAUDE.md §7.
                JSONArray rehTargets = reh.optJSONArray("targetMarketIds");
                if (rehTargets != null && rehTargets.length() > 0) {
                    rehearsalTargetIds = new ArrayList();
                    for (int i = 0; i < rehTargets.length(); i++) {
                        rehearsalTargetIds.add(rehTargets.getString(i));
                    }
                }
            }

            // Pass 7 -- Phase 4, the Culann strike (§7).
            JSONObject cul = faction.optJSONObject("culannStrike");
            if (cul != null) {
                culannEnabled = cul.optBoolean("enabled", culannEnabled);
                culannMarketId = cul.optString("targetMarket", culannMarketId);

                culannSweepDay = (float) cul.optDouble("sweepDay", culannSweepDay);
                culannBlockadeDay = (float) cul.optDouble("blockadeDay", culannBlockadeDay);
                culannArmadaDay = (float) cul.optDouble("armadaDay", culannArmadaDay);

                culannSweepFp = (float) cul.optDouble("sweepFp", culannSweepFp);
                culannSweepFleets = cul.optInt("sweepFleets", culannSweepFleets);
                culannSweepMaxShipSize = cul.optInt("sweepMaxShipSize", culannSweepMaxShipSize);

                JSONArray culSizes = cul.optJSONArray("blockadeFleetSizes");
                if (culSizes != null && culSizes.length() > 0) {
                    culannBlockadeFleetSizes = new ArrayList();
                    for (int i = 0; i < culSizes.length(); i++) {
                        culannBlockadeFleetSizes.add(new Integer(culSizes.getInt(i)));
                    }
                }

                culannAssaultFp = (float) cul.optDouble("assaultFp", culannAssaultFp);
                culannAssaultFleets = cul.optInt("assaultFleets", culannAssaultFleets);
                culannScreenFp = (float) cul.optDouble("screenFp", culannScreenFp);
                culannScreenFleets = cul.optInt("screenFleets", culannScreenFleets);
                culannArmadaMaxShipSize = cul.optInt(
                        "armadaMaxShipSize", culannArmadaMaxShipSize);
                culannScreenFleetName = cul.optString("screenFleetName", culannScreenFleetName);

                culannUnrestPoints = cul.optInt("unrestPoints", culannUnrestPoints);
                culannMaxDays = (float) cul.optDouble("maxDays", culannMaxDays);

                // Pushed into the stage's statics rather than carried per-instance, the same way
                // the three Ashen managers already configure their conditions. The stage is
                // constructed deep inside a launch and does not want six more constructor args.
                AshenCulannStrikeStage.heavyBatteriesDays = cul.optInt(
                        "heavyBatteriesDays", AshenCulannStrikeStage.heavyBatteriesDays);
                AshenCulannStrikeStage.spaceportDays = cul.optInt(
                        "spaceportDays", AshenCulannStrikeStage.spaceportDays);
                AshenCulannStrikeStage.refiningDays = cul.optInt(
                        "refiningDays", AshenCulannStrikeStage.refiningDays);
                AshenCulannStrikeStage.orbitalWorksDays = cul.optInt(
                        "orbitalWorksDays", AshenCulannStrikeStage.orbitalWorksDays);
                AshenCulannStrikeStage.militaryBaseDays = cul.optInt(
                        "militaryBaseDays", AshenCulannStrikeStage.militaryBaseDays);
            }
        } catch (Exception e) {
            Global.getLogger(AshenInterdictionManager.class).warn(
                    "Ashen interdiction config load failed; using code defaults", e);
        }

        if (timeScale != 1f) {
            cadenceDays *= timeScale;
            presenceDays *= timeScale;
            systemCooldownDays *= timeScale;
            AshenInterdictionStage.CONDITION_APPLY_DAY *= timeScale;
            ExileCrusadeHarassment.DURATION_DAYS *= timeScale;
            blockadePrepDays *= timeScale;
            blockadePayloadDays *= timeScale;
        }

        interval = new IntervalUtil(cadenceDays * 0.75f, cadenceDays * 1.25f);
    }

    protected void applyDefaults() {
        targetMarketIds = new ArrayList();
        targetMarketIds.add("culann");
        targetMarketIds.add("eochu_bres");
        targetMarketIds.add("tibicena");
        targetMarketIds.add("port_tse");

        // Rehearsal targets, ORDERED: Tibicena first, Port Tse once the war matures. Both carry
        // mining, an orbitalstation and ground defenses, so the fleet-generating industry is the
        // only real difference -- patrolhq spawns 2 small fleets, militarybase 2 small + 1 medium
        // -- which is what makes Port Tse the harder target despite being the smaller market.
        rehearsalTargetIds = new ArrayList();
        rehearsalTargetIds.add("tibicena");
        rehearsalTargetIds.add("port_tse");

        // One named anchor plus two pickets (Sean, 2026-08-05 -- the KOLT shape, which is also
        // what makes NPCHassler worth having). Smaller than KOLT's 10/4/4/3/3: the Crusade is
        // detaching a squadron from a raid, not mounting a holy armada.
        blockadeFleetSizes = new ArrayList();
        blockadeFleetSizes.add(new Integer(6));
        blockadeFleetSizes.add(new Integer(3));
        blockadeFleetSizes.add(new Integer(3));

        // Phase 4: TWO fleets, one per Hybrasil jump point -- the ratified cap, and heavier than
        // the Phase-3 entries because these hold a defended system rather than escort a rehearsal.
        // Defaulted here as well as in config so a missing block cannot NPE the wave-2 launch.
        culannBlockadeFleetSizes = new ArrayList();
        culannBlockadeFleetSizes.add(new Integer(7));
        culannBlockadeFleetSizes.add(new Integer(7));
    }
}
