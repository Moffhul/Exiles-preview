package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import data.scripts.marketconditions.ExileHegLeagueReliefEffort;

/**
 * Hegemony-Persean League STRIKE-TRIGGERED RECOVERY CONVOYS -- PASS 6 of
 * docs/EXILES_HEG_LEAGUE_WAR.md sec 4.6 (ruling R16, decisions D80.5-D80.7).
 *
 * <p><b>This is the other half of {@code surgicalStrike.extendedDisruptMult}, and the reason it
 * was allowed to leave 1.0.</b> A landed strike now writes a deliberately LONGER wound than the
 * design-normal band; the victim's government notices, argues about it, and -- after a
 * bureaucratic lag -- sends a civilian relief convoy from a nearby same-faction port. The convoy
 * sits in orbit for {@code unloadDays} and drips the struck industries' disruption timers back
 * down by exactly the extension the strike added.
 *
 * <h2>The invariant, and why the budget is not a config day-count</h2>
 * The relief budget is computed at STRIKE time as {@code rolledBaseDays x (extendedDisruptMult -
 * 1)} -- handed in by {@link ExileHegLeagueStrikeStage} as the difference between the wound it
 * wrote and the design-normal roll it came from. That makes sec 4.6's locked promise ("a full,
 * uninterrupted convoy schedule lands the effective downtime at design-normal") true BY
 * CONSTRUCTION, and it survives any later change to {@code disruptDaysMin/Max} or
 * {@code extendedDisruptMult} without a second knob drifting out of sync. At the shipped 45-90 x
 * 2.0 the wound is 90-180 days and the budget is 45-90.
 *
 * <p>The best case is therefore design-normal and the worst case is the full extension. Nothing
 * can ever heal an industry BELOW the design-normal band -- {@code minRemainingDays} is the hard
 * floor under every drip, and {@code recoveryDaysMult} is clamped to 1.0 at config load.
 *
 * <h2>Denial is the point</h2>
 * Nothing is pre-paid and nothing is written on death, so a convoy killed mid-unload denies
 * exactly the remainder -- which falls out for free rather than needing a mechanism (sec 4.6).
 * <b>One convoy per strike: destroyed means denied</b>, and no replacement is ever sent for that
 * wound (D80.5 item 7 -- a ruling, not an oversight; making relief retry would invert sec 4.6's
 * whole point). Pass 8's player interception and Pass 7's Black Fleet proxy both hang off this.
 *
 * <h2>Shape</h2>
 * The {@link AshenReliefManager} shape, copied and not extracted (measured, sec 14.11.2): an
 * {@code EveryFrameScript} with an {@link IntervalUtil} gate, a bounded list of named static saved
 * state, a TRAVEL -&gt; ORBIT -&gt; DEPARTING phase machine with per-phase timeouts so nothing can
 * be orphaned, direct {@code FleetFactoryV3} spawning (D80.5a -- {@code RouteManager} would
 * abstract the fleet away, and Pass 7 needs a real, findable one to intercept), and a dead-fleet
 * branch that drops the op without writing anything. The two managers differ in what they do at
 * orbit (a one-shot condition vs a daily drip), in their launch trigger (a cadence vs an event
 * ledger) and in their end-of-op bookkeeping; a shared base would have to parameterise all three,
 * which is the Pass-5b arithmetic again. <b>Revisit at a THIRD convoy, not before.</b>
 *
 * <p><b>It reads the director for config and nothing else</b>, and the director does not know it
 * exists (CLAUDE.md sec 6). It never calls another director; the one hook it writes for the
 * Black Fleet side -- the {@code $exiles_hl_relief_convoy} fleet flag, with the destination in
 * {@link ExileMidanDirective#RELIEF_DEST_KEY} -- is read by the BF ambush and nothing else, so the
 * decoupling seam stays one-way. (A sector-memory "relief target" key was also written until the
 * 2026-11-08 complexity review; nothing ever read it.)
 *
 * <p><b>Midan's proxy (Pass 7 step 7f, war doc sec 6.2).</b> Each new record offers the Midan module
 * a chance to write {@link ExileMidanDirective} naming its market; convoys carry their destination
 * ({@link ExileMidanDirective#RELIEF_DEST_KEY}) so the Black Fleet director can find the one a
 * directive names. This manager clears the directive when that convoy starts home, dies or is
 * dropped, and listens to its own convoys' battles: a convoy that dies in a fight against the Black
 * Fleet (the player not among its attackers) while a directive names its destination is logged
 * {@code Exiles Midan PROXY KILL}. Attribution (tension, backfire) is NOT built -- deferred by Sean
 * 2026-11-01 to the post-Pass-7 complexity review; the log line is its measurement.
 *
 * <p><b>Silent to the player</b> (D80.7): no intel, ever. The convoy is visible in space, the
 * industry tooltip's falling disruption timer is the tell, and a proximity-gated campaign message
 * fires on arrival when the player is there to see it. An intel entry per convoy would put 2-8
 * low-stakes listings on the board per campaign year for an event whose whole payload is a number
 * ticking down; sec 3.4 reserves intel for state transitions the player should feel.
 */
public class ExileHegLeagueRecoveryManager implements EveryFrameScript, FleetEventListener {

    /** The Black Fleet's faction id -- vocabulary only; this class never touches its director. */
    protected static final String BLACK_FLEET = "exileblackfleet";

    /** Quest/runtime override, beating the config default (the sibling convention). */
    public static final String RECOVERY_ENABLED_KEY = "$exiles_hl_recovery_enabled";

    /**
     * Fleet memory flag on a relief convoy. Reserved for Pass 8's interception dialogue (sec
     * 14.11.4); since Pass 7 step 7f the Black Fleet's ambush reader also uses it (with
     * {@link ExileMidanDirective#RELIEF_DEST_KEY}) to find the convoy a directive names.
     */
    public static final String RELIEF_FLEET_FLAG = "$exiles_hl_relief_convoy";
    protected static final int OP_TRAVEL = 0;
    protected static final int OP_ORBIT = 1;
    protected static final int OP_DEPARTING = 2;

    /**
     * One outstanding wound waiting for -- or currently receiving -- relief. Named static class,
     * never anonymous: this is saved state (the {@code ReliefOp}/{@code ConvoyOp} precedent).
     *
     * <p><b>Industries are ids, not references.</b> An {@code Industry} handle held across weeks
     * of game time is a leak waiting to happen and would pin a market that may decivilise; the
     * drip re-resolves each id through {@code market.getIndustry(id)} every time.
     */
    public static class ReliefRecord {
        public String marketId;
        public List industryIds = new ArrayList(); // String
        public String victimFactionId;
        /** The extension, in days, this convoy may return. Never negative. */
        public float reliefDays;
        /**
         * Days after {@link #recordedTimestamp} at which the convoy may sail -- the bureaucratic
         * lag, rolled once at record time.
         *
         * <p>A day count measured against the clock rather than an absolute "sail at" timestamp:
         * {@code CampaignClockAPI} exposes {@code getElapsedDaysSince(long)} but no
         * "timestamp N days from now", so computing an absolute one would mean doing seconds
         * arithmetic by hand against {@code getSecondsPerDay()}. One subtraction on a slow path is
         * cheaper and impossible to get wrong.
         */
        public float lagDays;
        /** Clock timestamp the record was created; both the lag and the expiry measure from here. */
        public long recordedTimestamp;
        /** True once a convoy has been launched for this wound. One convoy per strike, ever. */
        public boolean dispatched = false;
    }

    /**
     * One convoy in flight. Named static class -- saved state.
     *
     * <p>{@code delivered} is the ledger the outcome line reads: it is the only thing that makes
     * "a convoy killed mid-unload denies exactly the remainder" observable rather than merely
     * true.
     */
    public static class ReliefOp {
        public CampaignFleetAPI fleet;
        public String marketId;
        public String targetEntityId;
        public List industryIds = new ArrayList(); // String
        public String victimFactionId;
        /** Budget already scaled by the side's recoveryDaysMult -- what this convoy can deliver. */
        public float budgetDays;
        public float deliveredDays = 0f;
        /** Days actually spent unloading, kept separately because {@code phaseDays} resets at each
         *  phase change (Run-2 finding 2026-09-20: the outcome line printed the DEPARTURE phase's
         *  length as "over 2 days"). */
        public float orbitDays = 0f;
        public float perDay = 0f;
        public int phase = OP_TRAVEL;
        public float phaseDays = 0f;
        public float unloadDays = 10f;
        public boolean conditionApplied = false;
        public boolean anythingToDeliver = true;
        /**
         * 7f-2: member ids of the relief freighters, and how many sailed. The drip is scaled by
         * survivors / sailed. Null on ops from before 7f-2: those deliver in full, as they did.
         */
        public List freighterIds = null; // String
        public int freightersSailed = 0;
        /** 7f: the convoy's LAST battle had the Black Fleet against it and not the player. */
        public boolean lastBattleBlackFleet = false;
    }

    // ------------------------------------------------------------------
    // Saved state
    // ------------------------------------------------------------------

    /** Wounds waiting for a convoy, bounded at {@code ledgerMaxRecords}. */
    protected List ledger = new ArrayList();    // ReliefRecord
    /** Convoys in flight, bounded at {@code maxActive}. */
    protected List activeOps = new ArrayList(); // ReliefOp

    // --- Transient runtime ---
    protected transient IntervalUtil tick;
    protected transient Random random;
    protected transient boolean conditionTunablesApplied;

    public ExileHegLeagueRecoveryManager() {
        initTransients();
    }

    /**
     * XStream builds saved objects without running constructors OR field initialisers, so every
     * transient comes back at the JVM default and both lists can come back null from a save made
     * before one of them existed. Re-assert everything here -- the
     * {@code ExileRefugeeConvoyManager.initTransients()} pattern.
     */
    protected Object readResolve() {
        initTransients();
        if (ledger == null) ledger = new ArrayList();
        if (activeOps == null) activeOps = new ArrayList();
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

    /**
     * The static resolver -- how {@link ExileHegLeagueStrikeStage} reaches this manager without
     * holding a reference to it (the {@code ExileHegLeagueDirector.get()} idiom).
     *
     * <p>Walks the sector's script list, so it must only ever be called on a slow path. It is:
     * once per landed strike.
     */
    public static ExileHegLeagueRecoveryManager get() {
        if (Global.getSector() == null) return null;
        List scripts = Global.getSector().getScripts();
        for (int i = 0; i < scripts.size(); i++) {
            Object script = scripts.get(i);
            if (script instanceof ExileHegLeagueRecoveryManager) {
                return (ExileHegLeagueRecoveryManager) script;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The strike-side entry point
    // ------------------------------------------------------------------

    /**
     * Record a landed strike's wound (D80.5 item 1). Called from
     * {@link ExileHegLeagueStrikeStage#performRaid} the instant the disruption is written.
     *
     * <p>Guarded on every axis: a save with no manager, a disabled feed, a zero budget and an
     * empty industry list are all silent-or-logged no-ops rather than errors. <b>A zero budget is
     * the shipped state at {@code extendedDisruptMult} 1.0 and must never look like a failure.</b>
     *
     * @param market   the struck market
     * @param picks    industry ids the strike disrupted (Strings)
     * @param reliefDays the EXTENSION, in days, this strike added over design-normal
     * @param victimFactionId the side that owns the struck market -- the side that sends relief
     */
    public static void record(MarketAPI market, List picks, float reliefDays,
                              String victimFactionId) {
        ExileHegLeagueRecoveryManager manager = get();
        if (manager == null) return;
        manager.recordImpl(market, picks, reliefDays, victimFactionId);
    }

    protected void recordImpl(MarketAPI market, List picks, float reliefDays,
                              String victimFactionId) {
        if (market == null || picks == null || picks.isEmpty()) return;
        ExileHegLeagueConfig config = getConfig();
        if (config == null || !recoveryEnabled(config)) return;
        if (ledger == null) ledger = new ArrayList();

        if (reliefDays <= 0f) {
            // THE DEGENERATE CASE, and it is a legitimate configuration: extendedDisruptMult 1.0
            // means a strike writes exactly the design-normal wound, so there is no extension to
            // heal. Say so once per strike and record nothing -- an empty budget in the ledger
            // would just be a convoy flying nowhere.
            Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                    "Exiles Heg-League RELIEF RECORDED: none for " + market.getName()
                            + " -- relief budget is 0d (surgicalStrike.extendedDisruptMult is"
                            + " 1.0, so the strike wrote no extension over design-normal)."
                            + " No convoy will be sent; this is not an error.");
            return;
        }

        ReliefRecord record = new ReliefRecord();
        record.marketId = market.getId();
        record.victimFactionId = victimFactionId;
        record.reliefDays = reliefDays;
        for (int i = 0; i < picks.size(); i++) {
            Object id = picks.get(i);
            if (id instanceof String) record.industryIds.add((String) id);
        }
        if (record.industryIds.isEmpty()) return;

        if (random == null) random = new Random();
        float lag = roll(config.recoveryLagDaysMin, config.recoveryLagDaysMax);
        record.recordedTimestamp = Global.getSector().getClock().getTimestamp();
        record.lagDays = lag;

        // BOUND THE LEDGER (CLAUDE.md sec 7). Dropping the OLDEST is the right end: a wound nobody
        // could service for a campaign year is less interesting than the one that just landed, and
        // the drop is permanent-log rather than debug so save-bloat pressure is visible.
        while (ledger.size() >= Math.max(1, config.recoveryLedgerMaxRecords)) {
            ReliefRecord dropped = (ReliefRecord) ledger.remove(0);
            Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                    "Exiles Heg-League relief ledger full (" + config.recoveryLedgerMaxRecords
                            + "); dropped the oldest outstanding record for "
                            + dropped.marketId + " (" + (int) dropped.reliefDays
                            + "d never delivered)");
            releaseDirective(dropped.marketId, "its relief record was dropped (ledger full)");
        }
        ledger.add(record);

        // *** MEASUREMENT LINE 3 (sec 14.11.3) -- the extended-baseline arithmetic, in full. ***
        Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                "Exiles Heg-League RELIEF RECORDED: " + market.getName()
                        + " (" + victimFactionId + ") industries=" + record.industryIds
                        + " reliefBudget=" + (int) reliefDays + "d"
                        + " lag=" + (int) lag + "d"
                        + " | ledger=" + ledger.size() + "/" + config.recoveryLedgerMaxRecords);

        // Pass 7 step 7f: Midan may name this convoy to its proxy. The memory expiry is only a
        // backstop -- the lag, the longest the convoy may travel, the unload, and a margin.
        ExileHegLeagueMidanModule.offerProxyDirective(config, market, victimFactionId,
                lag + config.recoveryTravelTimeoutDays + config.recoveryUnloadDays + 10f);
    }

    /**
     * 7f: clears Midan's directive for this market once nothing can still be ambushed there -- no
     * undispatched record and no convoy still travelling or unloading. Safe to call for any market.
     */
    protected void releaseDirective(String marketId, String reason) {
        if (marketId == null || !ExileMidanDirective.isConvoyAmbushFor(marketId)) return;
        for (int i = 0; i < ledger.size(); i++) {
            ReliefRecord r = (ReliefRecord) ledger.get(i);
            if (!r.dispatched && marketId.equals(r.marketId)) return;
        }
        for (int i = 0; i < activeOps.size(); i++) {
            ReliefOp op = (ReliefOp) activeOps.get(i);
            if (op.phase != OP_DEPARTING && marketId.equals(op.marketId)
                    && op.fleet != null && op.fleet.isAlive()) return;
        }
        if (ExileMidanDirective.clearIfFor(marketId)) {
            Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                    "Exiles Midan DIRECTIVE CLEARED: " + marketId + " -- " + reason);
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public void advance(float amount) {
        if (Global.getSector() == null || Global.getSector().isPaused()) return;
        if (Global.getSector().isInNewGameAdvance()) return;

        if (tick == null) tick = new IntervalUtil(0.1f, 0.2f);
        if (random == null) random = new Random();
        if (ledger == null) ledger = new ArrayList();
        if (activeOps == null) activeOps = new ArrayList();

        // THE INTERVAL GATE COMES FIRST (CLAUDE.md sec 7): resolving the director walks the
        // sector's script list, and nothing on the per-frame path allocates.
        float days = Global.getSector().getClock().convertToDays(amount);
        tick.advance(days);
        if (!tick.intervalElapsed()) return;
        float elapsed = tick.getIntervalDuration();

        ExileHegLeagueConfig config = getConfig();
        if (config == null) return;

        if (!conditionTunablesApplied) {
            conditionTunablesApplied = true;
            // The informational condition's orphan backstop tracks the unload window it marks.
            ExileHegLeagueReliefEffort.DURATION_DAYS = Math.max(5f,
                    config.recoveryUnloadDays * 2f + 10f);
        }

        // Convoys in flight keep going regardless of the master switch -- one already loaded and
        // under way is not recalled because the feature was toggled off mid-campaign.
        advanceOps(config, elapsed);

        if (!recoveryEnabled(config)) return;
        expireRecords(config);
        tryDispatch(config);
    }

    protected ExileHegLeagueConfig getConfig() {
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director == null) return null;
        return director.getConfig();
    }

    /** Config default, overridable at runtime by quests via the memory flag. */
    protected boolean recoveryEnabled(ExileHegLeagueConfig config) {
        return ExileHegLeagueDirector.switchEnabled(RECOVERY_ENABLED_KEY, config.recoveryEnabled);
    }

    /** Drop records nothing could ever service, loudly (CLAUDE.md sec 7: bound what accumulates). */
    protected void expireRecords(ExileHegLeagueConfig config) {
        for (int i = ledger.size() - 1; i >= 0; i--) {
            ReliefRecord record = (ReliefRecord) ledger.get(i);
            if (record.dispatched) continue;
            float age = Global.getSector().getClock().getElapsedDaysSince(
                    record.recordedTimestamp);
            if (age < config.recoveryRecordExpiryDays) continue;
            ledger.remove(i);
            Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                    "Exiles Heg-League relief record for " + record.marketId + " expired after "
                            + (int) age + "d without a convoy ("
                            + (int) record.reliefDays + "d never delivered)");
            releaseDirective(record.marketId, "its relief record expired");
        }
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    protected void tryDispatch(ExileHegLeagueConfig config) {
        if (activeOps.size() >= Math.max(1, config.recoveryMaxActive)) return;

        for (int i = 0; i < ledger.size(); i++) {
            ReliefRecord record = (ReliefRecord) ledger.get(i);
            if (record.dispatched) continue;
            if (Global.getSector().getClock().getElapsedDaysSince(record.recordedTimestamp)
                    < record.lagDays) continue;

            MarketAPI market = Global.getSector().getEconomy().getMarket(record.marketId);
            if (market == null || !market.isInEconomy() || market.getPrimaryEntity() == null) {
                ledger.remove(i);
                Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                        "Exiles Heg-League relief record for " + record.marketId
                                + " dropped: the market is gone from the economy");
                releaseDirective(record.marketId, "the market is gone");
                return;
            }
            // Ownership is queried LIVE (R6c): a market that changed hands since the strike is
            // not the victim's problem any more, and the old owner is certainly not sending it
            // aid. Under Nexerelin this is the common case.
            if (record.victimFactionId != null
                    && !record.victimFactionId.equals(market.getFactionId())) {
                ledger.remove(i);
                Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                        "Exiles Heg-League relief record for " + market.getName()
                                + " dropped: it now belongs to " + market.getFactionId()
                                + ", not " + record.victimFactionId);
                releaseDirective(record.marketId, "the market changed hands");
                return;
            }

            if (launchConvoy(config, record, market)) {
                record.dispatched = true;
                ledger.remove(i);
            }
            // One dispatch attempt per tick either way: launching allocates a market list and a
            // fleet, and a failed attempt will be retried on the next tick anyway.
            return;
        }
    }

    protected boolean launchConvoy(ExileHegLeagueConfig config, ReliefRecord record,
                                   MarketAPI market) {
        MarketAPI source = ExileHegLeagueGeography.pickSourceNear(
                config, record.victimFactionId, market, random);
        if (source == null) {
            Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                    "Exiles Heg-League relief convoy for " + market.getName()
                            + " could not sail: " + record.victimFactionId
                            + " has no functional port to load at");
            return false;
        }

        ExileHegLeagueConfig.Side side = config.sideFor(record.victimFactionId);
        // D80.5 item 9: the Hegemony endurance asymmetry, as ONE multiplier. Clamped <= 1.0 at
        // config load, so this can only ever shorten what gets delivered.
        float sideMult = side != null ? side.recoveryDaysMult : 1f;
        float budget = record.reliefDays * sideMult;

        List freighterIds = new ArrayList(); // String, filled by createConvoy
        CampaignFleetAPI fleet = createConvoy(config, record.victimFactionId, source, freighterIds);
        if (fleet == null) return false;
        // 7f: where it is going (the Black Fleet's reader matches it to a directive), and this
        // manager hears its battles (the proxy-kill test). The listener dies with the fleet.
        fleet.getMemoryWithoutUpdate().set(ExileMidanDirective.RELIEF_DEST_KEY, record.marketId);
        fleet.addEventListener(this);

        SectorEntityToken sourceEntity = source.getPrimaryEntity();
        sourceEntity.getContainingLocation().addEntity(fleet);
        fleet.setLocation(
                sourceEntity.getLocation().x + (random.nextFloat() - 0.5f) * 300f,
                sourceEntity.getLocation().y + (random.nextFloat() - 0.5f) * 300f);
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, market.getPrimaryEntity(),
                config.recoveryTravelTimeoutDays,
                "carrying relief supplies to " + market.getName());

        ReliefOp op = new ReliefOp();
        op.fleet = fleet;
        op.marketId = record.marketId;
        op.targetEntityId = market.getPrimaryEntity().getId();
        op.victimFactionId = record.victimFactionId;
        for (int i = 0; i < record.industryIds.size(); i++) {
            op.industryIds.add((String) record.industryIds.get(i));
        }
        op.budgetDays = budget;
        op.unloadDays = Math.max(1f, config.recoveryUnloadDays);
        op.perDay = budget / op.unloadDays;
        op.freighterIds = freighterIds;
        op.freightersSailed = freighterIds.size();
        activeOps.add(op);

        // *** MEASUREMENT LINE 4 (sec 14.11.3). ***
        Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                "Exiles Heg-League RELIEF CONVOY LAUNCHED: " + source.getName() + " -> "
                        + market.getName()
                        + " | budget=" + (int) budget + "d of " + (int) record.reliefDays + "d"
                        + " perDay=" + format(op.perDay) + " sideMult=" + sideMult
                        + " | " + op.freightersSailed + " relief freighter(s), cargo "
                        + cargoSummary(fleet, config) + ", " + fleet.getFleetData().getNumMembers()
                        + " ship(s) " + fleet.getFleetPoints() + " FP"
                        + " | active=" + activeOps.size() + "/" + config.recoveryMaxActive);
        debug(config, "Heg-League relief convoy departing " + source.getName() + " for "
                + market.getName() + " (" + (int) budget + "d of repairs).");
        return true;
    }

    /**
     * The refugee-convoy recipe (D80.5 item 4): the one that reads as a civilian convoy and can
     * flee. Transponder on, trade-fleet flagged, allowed to disengage, no officers, and
     * {@code ignoreMarketFleetSizeMult} so a battered victim still manages to send something.
     *
     * <p>{@code maxShipSize} 2 for the same reason every other fleet in this director is capped:
     * a relief convoy is not a place to farm cruiser wrecks.
     *
     * <p><b>Rebuilt in step 7f-2 (ruled by Sean, 2026-11-03).</b> The factory now builds only the
     * sending faction's ESCORT ({@code combatFp}; freighter, tanker and transport FP are 0 in the
     * shipped config). The relief rides in {@code freighterCount} explicitly added
     * {@code freighterVariant} hulls (3 Colossi). Two reasons for building them by hand: vanilla's
     * {@code addFreighterFleetPoints} uses {@code SizeFilterMode.NONE}, so {@code maxShipSize}
     * never stopped Atlases (capital hulls); and the relief is now carried by THESE ships -- each
     * delivers an equal share, so the drip scales with survivors (see {@link #drip}).
     *
     * <p>They are added before the fleet is inflated, so vanilla's inflater still refits them and
     * rolls d-mods normally (lore: no fleet of pristine Colossi). Each carries
     * {@code cargoPerFreighter} (500) of the config manifest, 30/40/15/15
     * supplies/food/heavy machinery/metals -- under the Compromised Storage ceiling (900 x 0.75 =
     * 675), so a d-mod can never push the load over capacity. The cargo is real: vanilla's looting
     * pays a player who destroys the convoy, in proportion to the capacity lost. It drains as the
     * relief is delivered (see {@link #drainCargo}).
     *
     * @param freighterIds filled with the relief freighters' member ids
     */
    protected CampaignFleetAPI createConvoy(ExileHegLeagueConfig config, String factionId,
                                            MarketAPI source, List freighterIds) {
        FleetParamsV3 params = new FleetParamsV3(
                null,
                source.getLocationInHyperspace(),
                factionId,
                null,
                FleetTypes.TRADE_SMALL,
                (float) config.recoveryCombatFp,
                (float) config.recoveryFreighterFp,
                (float) config.recoveryTankerFp,
                (float) config.recoveryTransportFp,
                0f,
                0f,
                0f);
        params.random = random;
        params.ignoreMarketFleetSizeMult = Boolean.TRUE;
        params.withOfficers = false;
        params.maxShipSize = 2;

        // The faction escort (and any tanker/transport FP a config still asks for).
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null) {
            fleet = Global.getFactory().createEmptyFleet(factionId, "Relief Convoy", true);
        }

        // 7f-2: the relief freighters, added by hand (see the javadoc).
        String variant = config.recoveryFreighterVariant;
        if (config.recoveryFreighterCount > 0 && (variant == null
                || !Global.getSettings().doesVariantExist(variant))) {
            Global.getLogger(ExileHegLeagueRecoveryManager.class).warn(
                    "Exiles Heg-League relief convoy: freighter variant '" + variant
                            + "' does not exist; sailing without relief freighters");
        } else {
            for (int i = 0; i < config.recoveryFreighterCount; i++) {
                FleetMemberAPI member = Global.getFactory().createFleetMember(
                        FleetMemberType.SHIP, variant);
                member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
                fleet.getFleetData().addFleetMember(member);
                freighterIds.add(member.getId());
            }
        }
        if (fleet.isEmpty()) {
            Global.getLogger(ExileHegLeagueRecoveryManager.class).warn(
                    "Exiles Heg-League relief convoy fleet creation failed for " + factionId);
            return null;
        }
        fleet.getFleetData().sort();
        loadCargo(fleet, config, freighterIds.size());

        // Starsector prepends the faction display name (the refugee-convoy lesson).
        fleet.setName("Relief Convoy");
        fleet.setTransponderOn(true);
        fleet.getMemoryWithoutUpdate().set(RELIEF_FLEET_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
        // A relief convoy runs rather than brawls; one pursuit still catches it, which is exactly
        // the denial path sec 4.6 wants to remain possible.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        return fleet;
    }

    // ------------------------------------------------------------------
    // The relief freighters' cargo (step 7f-2)
    // ------------------------------------------------------------------

    /** The manifest, {@code cargoPerFreighter} per relief freighter, split by the config shares. */
    protected void loadCargo(CampaignFleetAPI fleet, ExileHegLeagueConfig config, int freighters) {
        float total = config.recoveryCargoPerFreighter * freighters;
        if (total <= 0f) return;
        float shareSum = 0f;
        for (int i = 0; i < config.recoveryCargoShares.size(); i++) {
            shareSum += ((Float) config.recoveryCargoShares.get(i)).floatValue();
        }
        if (shareSum <= 0f) return;
        for (int i = 0; i < config.recoveryCargoCommodities.size(); i++) {
            String id = (String) config.recoveryCargoCommodities.get(i);
            if (!commodityExists(id)) {
                Global.getLogger(ExileHegLeagueRecoveryManager.class).warn(
                        "Exiles Heg-League relief cargo: unknown commodity '" + id + "' skipped");
                continue;
            }
            float share = ((Float) config.recoveryCargoShares.get(i)).floatValue() / shareSum;
            fleet.getCargo().addCommodity(id, (float) Math.floor(total * share));
        }
    }

    /**
     * Unloading is delivery: each unload tick removes the same FRACTION of what is still aboard as
     * the tick is of the unload days still to run, so the holds reach empty exactly when the unload
     * ends (Sean, 2026-11-03: "drain it day by day") -- and a convoy killed on day 9 is nearly
     * empty, whatever it lost on the way. Only the manifest's commodities are touched.
     */
    protected void drainCargo(ExileHegLeagueConfig config, ReliefOp op, float elapsedDays,
                              float daysLeftBefore) {
        if (op.fleet == null || elapsedDays <= 0f) return;
        float fraction = daysLeftBefore <= elapsedDays ? 1f : elapsedDays / daysLeftBefore;
        for (int i = 0; i < config.recoveryCargoCommodities.size(); i++) {
            String id = (String) config.recoveryCargoCommodities.get(i);
            float have = op.fleet.getCargo().getCommodityQuantity(id);
            if (have <= 0f) continue;
            float take = fraction >= 1f ? have : have * fraction;
            if (take > 0f) op.fleet.getCargo().removeCommodity(id, take);
        }
    }

    /** Empties the manifest's commodities from the holds (departure). */
    protected void emptyCargo(ExileHegLeagueConfig config, ReliefOp op) {
        if (op.fleet == null) return;
        for (int i = 0; i < config.recoveryCargoCommodities.size(); i++) {
            String id = (String) config.recoveryCargoCommodities.get(i);
            float have = op.fleet.getCargo().getCommodityQuantity(id);
            if (have > 0f) op.fleet.getCargo().removeCommodity(id, have);
        }
    }

    protected static boolean commodityExists(String id) {
        if (id == null) return false;
        try {
            return Global.getSettings().getCommoditySpec(id) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** "supplies 450, food 600, ..." for the launch line. */
    protected static String cargoSummary(CampaignFleetAPI fleet, ExileHegLeagueConfig config) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < config.recoveryCargoCommodities.size(); i++) {
            String id = (String) config.recoveryCargoCommodities.get(i);
            if (sb.length() > 0) sb.append(", ");
            sb.append(id).append(" ").append((int) fleet.getCargo().getCommodityQuantity(id));
        }
        return sb.length() > 0 ? sb.toString() : "none";
    }

    /**
     * Surviving relief freighters / freighters that sailed. 1 for an op from before 7f-2 (no ids)
     * or a convoy that sailed without freighters, so those deliver as they always did.
     */
    protected static float freighterFraction(ReliefOp op) {
        if (op.freighterIds == null || op.freightersSailed <= 0 || op.fleet == null) return 1f;
        int alive = 0;
        List members = op.fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) members.get(i);
            if (op.freighterIds.contains(m.getId())) alive++;
        }
        return Math.min(1f, (float) alive / (float) op.freightersSailed);
    }

    protected static int freightersAlive(ReliefOp op) {
        if (op.freighterIds == null || op.fleet == null) return 0;
        return Math.round(freighterFraction(op) * op.freightersSailed);
    }

    // ------------------------------------------------------------------
    // Operation lifecycle
    // ------------------------------------------------------------------

    protected void advanceOps(ExileHegLeagueConfig config, float elapsed) {
        for (int i = activeOps.size() - 1; i >= 0; i--) {
            ReliefOp op = (ReliefOp) activeOps.get(i);
            if (!advanceOp(config, op, elapsed)) {
                activeOps.remove(i);
                releaseDirective(op.marketId, "its relief convoy is finished");
            }
        }
    }

    /** @return false when the operation is finished and should be dropped. */
    protected boolean advanceOp(ExileHegLeagueConfig config, ReliefOp op, float elapsed) {
        SectorEntityToken target = op.targetEntityId != null
                ? Global.getSector().getEntityById(op.targetEntityId) : null;
        CampaignFleetAPI fleet = op.fleet;

        // Target gone (decivilised, removed): send the convoy home rather than orphaning it.
        if (target == null || target.getMarket() == null) {
            if (fleet != null && fleet.isAlive()) {
                fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
            }
            reportOutcome(op, "target gone");
            return false;
        }
        // A DEPARTING convoy is EXPECTED to vanish: GO_TO_LOCATION_AND_DESPAWN removes the fleet
        // at the jump point. Checked before the loss branch below, which would otherwise report
        // every successful return as a loss (the 2026-08-01 Ashen finding).
        // isEmpty() included since the Run-2 read (2026-09-20): a fully delivered Yesod convoy
        // vanished on departure alive-but-empty for a tick and was labelled "convoy lost". Once
        // the unload is over nothing can be denied, so any disappearance here is completion.
        if (op.phase == OP_DEPARTING
                && (fleet == null || !fleet.isAlive() || fleet.isEmpty())) {
            finishUnload(config, op, target, "completed");
            return false;
        }
        // Convoy destroyed. NOTHING is written: the remainder of the budget is simply denied, and
        // no replacement is ever sent for this wound (D80.5 item 7).
        if (fleet == null || !fleet.isAlive() || fleet.isEmpty()) {
            if (op.conditionApplied) removeReliefCondition(config, target.getMarket());
            logProxyKill(op, target);
            reportOutcome(op, "convoy lost");
            debug(config, "A Heg-League relief convoy bound for " + target.getName()
                    + " was lost.");
            return false;
        }

        op.phaseDays += elapsed;

        if (op.phase == OP_TRAVEL) {
            if (fleet.getContainingLocation() == target.getContainingLocation()
                    && Misc.getDistance(fleet.getLocation(), target.getLocation()) < 400f) {
                beginOrbit(config, op, target);
            } else if (op.phaseDays >= config.recoveryTravelTimeoutDays) {
                Global.getLogger(ExileHegLeagueRecoveryManager.class).warn(
                        "Exiles Heg-League relief convoy timed out en route to "
                                + target.getName());
                fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
                reportOutcome(op, "travel timed out");
                return false;
            }
            return true;
        }

        if (op.phase == OP_ORBIT) {
            op.orbitDays += elapsed;
            drip(config, op, target.getMarket(), elapsed);
            drainCargo(config, op, elapsed, op.unloadDays - (op.phaseDays - elapsed));
            if (op.phaseDays >= op.unloadDays) {
                beginDeparture(config, op, target);
            }
            return true;
        }

        // OP_DEPARTING
        if (fleet.getContainingLocation() != target.getContainingLocation()
                || op.phaseDays >= config.recoveryDepartTimeoutDays) {
            if (fleet.isAlive()) {
                fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
            }
            finishUnload(config, op, target, "completed");
            return false;
        }
        return true;
    }

    protected void beginOrbit(ExileHegLeagueConfig config, ReliefOp op,
                              SectorEntityToken target) {
        op.phase = OP_ORBIT;
        op.phaseDays = 0f;
        op.fleet.clearAssignments();
        op.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target,
                op.unloadDays + 5f, "unloading relief supplies at " + target.getName());

        if (config.recoveryReliefEffortCondition && !op.conditionApplied) {
            op.conditionApplied = true;
            ExileHegLeagueReliefEffort.applyTo(target.getMarket());
        }

        // PROXIMITY-GATED (D80.7): the refugee-convoy precedent, live-tested and accepted
        // 2026-08-19. No intel, and no message at all when the player is nowhere near.
        if (config.recoveryArrivalMessage && op.fleet.isInCurrentLocation()
                && op.fleet.isVisibleToPlayerFleet()) {
            Global.getSector().getCampaignUI().addMessage(
                    "A relief convoy has arrived at " + target.getName()
                            + " and has begun unloading.");
        }
        Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                "Exiles Heg-League relief convoy began unloading at " + target.getName()
                        + " (" + (int) op.budgetDays + "d over " + (int) op.unloadDays + "d)");
    }

    /**
     * The drip (D80.5 item 6), applied once per MANAGER TICK converted to days -- so it is
     * frame-rate independent and correct at any {@code timeScale}, rather than "per arrival" or
     * "per whole day".
     *
     * <p>Verified against {@code BaseIndustry.setDisrupted(days, false)}: the non-max form SETS
     * the timer absolutely and can therefore LOWER it, and {@code dur <= 0} unsets the key
     * outright, bringing the industry back online. {@code notifyDisrupted()} only fires on the
     * not-disrupted -&gt; disrupted transition, so lowering fires nothing, which is what we want.
     *
     * <p>{@code minRemainingDays} is the floor under every industry: it is what stops a second
     * strike's relief over-healing a twice-struck market, and what stops a convoy handing the
     * victim a working shipyard on the day it arrives.
     *
     * <p>An industry that is no longer disrupted is skipped and costs nothing -- a Nexerelin raid
     * or an ownership flip may have healed it, and the convoy flying on to deliver nothing is
     * correct behaviour, not an error (D80.5 item 8).
     */
    protected void drip(ExileHegLeagueConfig config, ReliefOp op, MarketAPI market,
                        float elapsedDays) {
        if (market == null || elapsedDays <= 0f) return;
        float remaining = op.budgetDays - op.deliveredDays;
        if (remaining <= 0f) return;

        // 7f-2 (Sean, 2026-11-03): each relief freighter carries an equal share, so the day's drip
        // scales with the freighters still alive -- one rule for losses on the way in and losses
        // mid-unload. What a dead freighter carried is simply never delivered (denial, sec 4.6).
        float subtract = op.perDay * elapsedDays * freighterFraction(op);
        if (subtract > remaining) subtract = remaining;
        if (subtract <= 0f) return;

        boolean anyApplied = false;
        for (int i = 0; i < op.industryIds.size(); i++) {
            String industryId = (String) op.industryIds.get(i);
            Industry industry = market.getIndustry(industryId);
            if (industry == null || !industry.isDisrupted()) continue;
            float before = industry.getDisruptedDays();
            float after = before - subtract;
            if (after < config.recoveryMinRemainingDays) {
                after = config.recoveryMinRemainingDays;
            }
            if (after >= before) continue; // already at or below the floor
            industry.setDisrupted(after, false);
            anyApplied = true;
            if (config.recoveryDebugMessages) {
                Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                        "Exiles Heg-League relief drip: " + market.getName() + "/" + industryId
                                + " " + format(before) + "d -> " + format(after) + "d");
            }
        }

        // The budget is only spent on days that actually landed somewhere. An op whose industries
        // have all recovered on their own therefore reports "industry already recovered" with its
        // budget intact, rather than quietly claiming to have delivered it.
        if (anyApplied) {
            op.deliveredDays += subtract;
        } else {
            op.anythingToDeliver = false;
        }
    }

    protected void beginDeparture(ExileHegLeagueConfig config, ReliefOp op,
                                  SectorEntityToken target) {
        op.phase = OP_DEPARTING;
        op.phaseDays = 0f;
        // The drain has emptied the holds; this catches rounding. A departing convoy is empty.
        emptyCargo(config, op);

        if (op.conditionApplied) {
            removeReliefCondition(config, target.getMarket());
            op.conditionApplied = false;
        }

        CampaignFleetAPI fleet = op.fleet;
        fleet.clearAssignments();
        SectorEntityToken exit = RouteLocationCalculator.findJumpPointToUse(
                fleet.getFaction(), target);
        if (exit == null) {
            List points = target.getContainingLocation().getEntities(JumpPointAPI.class);
            if (!points.isEmpty()) exit = (SectorEntityToken) points.get(0);
        }
        if (exit == null) exit = target;
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, exit,
                config.recoveryDepartTimeoutDays, "returning home");

        // 7f: the unload is over, so nothing is left to deny -- an ambush now would be pointless.
        releaseDirective(op.marketId, "its relief convoy finished unloading");
    }

    // ------------------------------------------------------------------
    // Midan's proxy: the kill test (Pass 7 step 7f)
    // ------------------------------------------------------------------

    /**
     * Every battle one of our convoys fights. Records whether the Black Fleet was among its
     * attackers and the player was NOT (ruled 2026-10-19: kills by the player or the rival side do
     * not count). Overwritten each battle, so it describes the LAST fight -- the one a destroyed
     * convoy died in.
     */
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner,
                                     BattleAPI battle) {
        if (fleet == null || battle == null || activeOps == null) return;
        ReliefOp op = null;
        for (int i = 0; i < activeOps.size(); i++) {
            ReliefOp candidate = (ReliefOp) activeOps.get(i);
            if (candidate.fleet == fleet) {
                op = candidate;
                break;
            }
        }
        if (op == null) return;
        boolean blackFleet = false;
        StringBuffer against = new StringBuffer();
        List enemies = battle.getOtherSideSnapshotFor(fleet);
        if (enemies != null) {
            for (int i = 0; i < enemies.size(); i++) {
                CampaignFleetAPI enemy = (CampaignFleetAPI) enemies.get(i);
                if (enemy == null) continue;
                if (against.length() > 0) against.append(", ");
                against.append(enemy.isPlayerFleet() ? "the player" : enemy.getNameWithFaction());
                if (enemy.getFaction() != null && BLACK_FLEET.equals(enemy.getFaction().getId())) {
                    blackFleet = true;
                }
            }
        }
        boolean playerAgainst = battle.isPlayerInvolved() && !battle.isOnPlayerSide(fleet);
        op.lastBattleBlackFleet = blackFleet && !playerAgainst;

        // MEASUREMENT (7f, Sean 2026-11-03): every fight a relief convoy is in -- what it lost, what
        // is left, and who it fought. The 2026-11-03 run's ambush left one ship alive and the log
        // could not show it.
        float fpLost = 0f;
        List lost = Misc.getSnapshotMembersLost(fleet);
        for (int i = 0; i < lost.size(); i++) {
            fpLost += ((FleetMemberAPI) lost.get(i)).getFleetPointCost();
        }
        SectorEntityToken target = op.targetEntityId != null
                ? Global.getSector().getEntityById(op.targetEntityId) : null;
        Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                "Exiles Heg-League RELIEF CONVOY BATTLE: "
                        + (target != null ? target.getName() : op.marketId) + " convoy ("
                        + op.victimFactionId + ") vs " + (against.length() > 0 ? against.toString() : "?")
                        + " | lost " + lost.size() + " ship(s), " + (int) fpLost + " FP; left "
                        + fleet.getFleetData().getNumMembers() + " ship(s), " + fleet.getFleetPoints()
                        + " FP | " + (op.phase == OP_TRAVEL ? "travelling" : op.phase == OP_ORBIT
                                ? "unloading" : "departing")
                        + " | player " + (!battle.isPlayerInvolved() ? "not involved"
                                : playerAgainst ? "AGAINST the convoy" : "WITH the convoy")
                        + " | counts toward a proxy kill: " + op.lastBattleBlackFleet);
    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason,
                                               Object param) {
        // The op loop notices the loss on its next tick; nothing to do here.
    }

    /** The loss branch's 7f line: a convoy the proxy was sent after, killed by the Black Fleet. */
    protected void logProxyKill(ReliefOp op, SectorEntityToken target) {
        if (!op.lastBattleBlackFleet) return;
        boolean directed = ExileMidanDirective.isConvoyAmbushFor(op.marketId);
        Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                (directed ? "Exiles Midan PROXY KILL: " : "Exiles Midan convoy killed by the Black"
                        + " Fleet WITHOUT a directive (not counted): ")
                        + target.getName() + " relief convoy (" + op.victimFactionId + ")"
                        + " | " + format(op.budgetDays - op.deliveredDays) + "d of "
                        + format(op.budgetDays) + "d denied | attribution not built (deferred)");
    }

    /** Departure or despawn after a full unload: lift the marker, then report. */
    protected void finishUnload(ExileHegLeagueConfig config, ReliefOp op,
                                SectorEntityToken target, String reason) {
        if (op.conditionApplied && target != null) {
            removeReliefCondition(config, target.getMarket());
            op.conditionApplied = false;
        }
        reportOutcome(op, op.anythingToDeliver ? reason : "industry already recovered");
    }

    protected void removeReliefCondition(ExileHegLeagueConfig config, MarketAPI market) {
        ExileHegLeagueReliefEffort.removeFrom(market);
    }

    /** *** MEASUREMENT LINE 5 (sec 14.11.3) -- what extendedDisruptMult is tuned FROM. *** */
    protected void reportOutcome(ReliefOp op, String reason) {
        MarketAPI market = op.marketId != null
                ? Global.getSector().getEconomy().getMarket(op.marketId) : null;
        Global.getLogger(ExileHegLeagueRecoveryManager.class).info(
                "Exiles Heg-League RELIEF OUTCOME: "
                        + (market != null ? market.getName() : String.valueOf(op.marketId))
                        + " delivered " + format(op.deliveredDays) + "d of "
                        + format(op.budgetDays) + "d over " + (int) op.orbitDays + " unload days ("
                        + reason + ")"
                        + (op.freighterIds != null ? " | relief freighters alive " + freightersAlive(op)
                                + " of " + op.freightersSailed : ""));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    protected float roll(float min, float max) {
        if (max <= min) return min;
        return min + random.nextFloat() * (max - min);
    }

    protected static String format(float value) {
        return "" + Math.round(value * 100f) / 100f;
    }

    protected void debug(ExileHegLeagueConfig config, String text) {
        if (config == null || !config.recoveryDebugMessages) return;
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director != null) director.debug(text);
    }
}
