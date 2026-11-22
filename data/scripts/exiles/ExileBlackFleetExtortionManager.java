package data.scripts.exiles;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.scripts.marketconditions.ExileBusinessIsGood;
import data.scripts.marketconditions.ExilePriceSpikes;

import java.awt.Color;

/**
 * Black Fleet EXTORTION racket (spec ratified 2026-07-09, built 2026-07-11). Disguised
 * collector fleets visit defenseless markets; while the racket runs, the market carries the
 * unattributed "Price Spikes" condition. The player's lever is violence: kill the collectors
 * and the racket collapses — but the truth costs reputation, and the Black Fleet remembers.
 *
 * THE DISGUISE (feasibility verified 2026-07-09): the fleet is created with sim-level
 * faction INDEPENDENT — faction assignment IS the protection, since NPC AI reads faction
 * relations. It reads "Trade Fleet" (small-trader composition, indie hulls, token escort,
 * transponder on). NO makeNonHostile flag (ruling #9): factions hostile to independents —
 * Kanta's pirates, the Path — may interdict collectors ("no extortion today", emergent).
 * MEMORY_KEY_NO_REP_IMPACT suppresses the default engagement rep; consequences are handled
 * by this manager's fleet-attached listener instead. GOTCHA (solved in review): collectors
 * spawn in HYPERSPACE near the anchor — the Black Fleet is hostile to independents, so a
 * disguised fleet spawned in-system would be attacked by its own side's patrols.
 *
 * TARGETS (spec #1): markets with NO functional military industry beyond ground defenses /
 * heavy batteries (patrol HQ / military base / high command / any station = blocker, checked
 * via isFunctional() — a DISRUPTED military opens an extortion window: Omicron bombardments
 * and Black Fleet sacks open doors for collectors, emergent synergy). Markets hostile to
 * independents are skipped (the disguise must hold at the destination). Player markets
 * code-exempt. Usual victims in practice: independents and refugees.
 *
 * OUTCOMES (playtest-tuned 2026-07-11: BOUNTY-MISSION LOGIC — the racket lives and dies
 * with the collector FLAGSHIP, not the whole fleet; and attacked collectors always attempt
 * to disengage, so one pursuit catches escort and freighters in a single battle):
 *  - Collectors arrive -> Price Spikes lands (120d, refresh). Completed collection earns
 *    shared prosperity only on return; all collectors share one rolling allowance.
 *  - Player kills the flagship, any time: wreckage-reveal message + Black Fleet rep minus.
 *    AFTER collection additionally: condition removed; the extorted market's faction rep
 *    minus (officially you shot up a trade fleet) + SILENT grudge flag (heavy
 *    Assault-picker weighting; refugees NEVER get the grudge). BEFORE arrival: no
 *    target-faction penalty, no grudge (the market never knew — author ruling #4).
 *    Leaderless survivors scatter for home.
 *  - NPCs kill the flagship: the racket quietly collapses (condition removed, no grudge,
 *    no message — the player wasn't there to learn anything).
 *
 * CADENCE (spec #4): starts at initialIntervalDays; ADAPTIVE — Assault success speeds it
 * up, failure slows it down (outcome flags pushed by BlackFleetRaidManager via sector
 * memory), hard-clamped to [minIntervalDays, maxIntervalDays].
 *
 * Perf (CLAUDE.md par.7): interval-gated advance, maxActive collector fleets, self-expiring
 * conditions, listener attached to the FLEET (dies with it), ops pruned. ~one-trade-fleet
 * cost. No intel entries — legibility is silence (spec #8); the log carries everything.
 */
public class ExileBlackFleetExtortionManager implements EveryFrameScript, FleetEventListener {

    public static final String CONFIG_PATH = "data/config/exiles/director_config.json";
    public static final String CONFIG_KEY = "exileblackfleet";
    /** Silent grudge flag on a market whose player-killed collectors had already collected
     *  (spec #6). Read by BlackFleetRaidManager at SYSTEM selection (both tiers, playtest-5
     *  fix) and victim selection (heavy weighting + relaxed feasibility), cleared by
     *  BlackFleetAssaultStage.performRaid when the fleet finally gets its revenge, or
     *  expires after GRUDGE_DAYS (fortress-protected grudges may go unavenged — even
     *  Mourad won't storm Askonia for spite forever). */
    public static final String GRUDGE_KEY = "$exilebf_grudge";
    /** TUNE: days a grudge is held if never avenged. */
    public static final float GRUDGE_DAYS = 365f;
    /** Sector-memory outcome counters pushed by BlackFleetRaidManager as assaults end. */
    public static final String SPEEDUP_KEY = "$exilebf_extort_speedup";
    public static final String SLOWDOWN_KEY = "$exilebf_extort_slowdown";
    /** Collector-fleet marker (memory flag, not a tag — invisible to fleet inspection). */
    public static final String COLLECTOR_FLAG = "$exilebf_collector";

    /** TUNE: rep cost of the truth coming out (spec #6): the Black Fleet knows who burned
     *  its collectors; the extorted market's faction only knows you destroyed a trade
     *  fleet in their space. */
    public static final float REP_HIT_BLACK_FLEET = 0.05f;
    public static final float REP_HIT_TARGET_FACTION = 0.03f;
    /** TUNE: days the collectors sit at the market "conducting trade". */
    public static final float COLLECT_DAYS = 3f;
    /** TUNE: assault-outcome cadence nudges (success = rackets embolden). */
    public static final float SPEEDUP_MULT = 0.75f;
    public static final float SLOWDOWN_MULT = 1.35f;

    /** One collector op. Named static class — XStream-safe. */
    public static class ExtortionOp {
        public CampaignFleetAPI fleet;
        public String targetMarketId;
        public String sourceStationId;
        /** The captain's ship (bounty-mission logic, author ruling 2026-07-11): the racket
         *  lives and dies with the FLAGSHIP, not the whole fleet. */
        public String flagshipId;
        public boolean conditionApplied = false;
        public float collectDaysLeft = 0f;
        public boolean returning = false;
        public boolean collected;
        public boolean proceedsResolved;
        public SectorEntityToken returnPoint;
    }

    // --- Saved state ---
    protected List ops = new ArrayList(); // ExtortionOp
    /** Adapted cadence; <=0 = fresh save, seeded from config initialIntervalDays on load. */
    protected float currentIntervalDays = -1f;
    protected float daysUntilNext = -1f;  // <0 = roll on first tick after start delay
    protected float elapsedTotal = 0f;

    // --- Transient config ---
    protected transient boolean configLoaded = false;
    protected transient IntervalUtil tick = null;
    protected transient boolean enabled = true;
    protected transient float startDelayDays = 60f;
    protected transient float initialIntervalDays = 45f;
    protected transient float minIntervalDays = 10f;
    protected transient float maxIntervalDays = 60f;
    protected transient int maxActive = 3; // Sean's deliberate value (2026-09-08), matches config
    /** Hard ceiling on concurrent collector fleets, whatever the config says (Sean, 2026-10-06). */
    public static final int MAX_ACTIVE_CEILING = 12;
    protected transient int escortFpMin = 10;
    protected transient int escortFpMax = 25;
    protected transient int freighterFp = 30;
    /** TESTING AID (author request 2026-07-11): the racket is deliberately silent in a real
     *  campaign, which also makes it untestable — this flag announces each dispatch so the
     *  incidents can be found. Config-gated; ship false. */
    protected transient boolean debugMessages = false;

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void advance(float amount) {
        if (!configLoaded) loadConfig();
        float days = Global.getSector().getClock().convertToDays(amount);
        elapsedTotal += days;

        tick.advance(days);
        if (!tick.intervalElapsed()) return;

        consumeCadenceNudges();
        advanceOps();

        if (!enabled || elapsedTotal < startDelayDays) return;

        if (daysUntilNext < 0f) {
            daysUntilNext = rollNextDelay();
        }
        daysUntilNext -= tick.getIntervalDuration();
        if (daysUntilNext <= 0f) {
            daysUntilNext = rollNextDelay();
            if (ops.size() < maxActive) {
                tryLaunch();
            }
        }
    }

    /** Assault outcomes nudge the racket's tempo (spec #4): success emboldens, failure
     *  makes the collectors lie low. Integer counters pushed by BlackFleetRaidManager. */
    protected void consumeCadenceNudges() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem =
                Global.getSector().getMemoryWithoutUpdate();
        Object su = mem.get(SPEEDUP_KEY);
        Object sd = mem.get(SLOWDOWN_KEY);
        int speedups = su instanceof Integer ? ((Integer) su).intValue() : 0;
        int slowdowns = sd instanceof Integer ? ((Integer) sd).intValue() : 0;
        if (speedups == 0 && slowdowns == 0) return;
        mem.unset(SPEEDUP_KEY);
        mem.unset(SLOWDOWN_KEY);
        for (int i = 0; i < speedups; i++) currentIntervalDays *= SPEEDUP_MULT;
        for (int i = 0; i < slowdowns; i++) currentIntervalDays *= SLOWDOWN_MULT;
        if (currentIntervalDays < minIntervalDays) currentIntervalDays = minIntervalDays;
        if (currentIntervalDays > maxIntervalDays) currentIntervalDays = maxIntervalDays;
        Global.getLogger(ExileBlackFleetExtortionManager.class).info(
                "Extortion cadence adjusted: interval now " + (int) currentIntervalDays
                        + "d (+" + speedups + " successes / +" + slowdowns + " failures)");
    }

    protected float rollNextDelay() {
        return currentIntervalDays * (0.75f + new Random().nextFloat() * 0.5f);
    }

    /** Per-op state machine: arrive -> collect -> go home. Destruction is handled by the
     *  fleet-attached listener, not here. */
    protected void advanceOps() {
        Iterator it = ops.iterator();
        while (it.hasNext()) {
            ExtortionOp op = (ExtortionOp) it.next();
            if (op.fleet == null || !op.fleet.isAlive()) {
                // Despawned without the listener firing a removal (e.g. reached home).
                it.remove();
                continue;
            }
            MarketAPI target = Global.getSector().getEconomy().getMarket(op.targetMarketId);
            if (op.returning) {
                creditReturn(op);
                // BF-7: the 120-day homeward order expired without despawning -- re-issue it
                // rather than let an idle collector hold a maxActive slot forever.
                if (op.fleet.getCurrentAssignment() == null) {
                    Global.getLogger(ExileBlackFleetExtortionManager.class).info(
                            "Extortion watchdog: idle returning collector re-ordered home");
                    issueReturnOrder(op);
                }
                continue;
            }
            if (target == null || !target.isInEconomy()) {
                sendHome(op); // market died mid-op; recall
                continue;
            }

            if (!op.conditionApplied && !op.returning) {
                SectorEntityToken entity = target.getPrimaryEntity();
                if (entity != null && op.fleet.getContainingLocation() == entity.getContainingLocation()
                        && Misc.getDistance(op.fleet.getLocation(), entity.getLocation()) < 350f) {
                    onCollectorsArrived(op, target);
                } else if (op.fleet.getCurrentAssignment() == null) {
                    // BF-7: the outbound GO_TO ended outside the 350-unit check (a large planet,
                    // a fast-advance arrival, or the 120-day expiry). In the target's system it
                    // HAS arrived; anywhere else the run is abandoned and the slot freed.
                    boolean inSystem = entity != null
                            && op.fleet.getContainingLocation() == entity.getContainingLocation();
                    Global.getLogger(ExileBlackFleetExtortionManager.class).info(
                            "Extortion watchdog: idle outbound collector for " + target.getName()
                                    + (inSystem ? " treated as arrived" : " sent home"));
                    if (inSystem) {
                        onCollectorsArrived(op, target);
                    } else {
                        sendHome(op);
                    }
                }
            } else if (op.conditionApplied && !op.returning) {
                op.collectDaysLeft -= tick.getIntervalDuration();
                if (op.collectDaysLeft <= 0f) {
                    op.collected = true;
                    sendHome(op);
                }
            }
        }
    }

    protected void onCollectorsArrived(ExtortionOp op, MarketAPI target) {
        op.conditionApplied = true;
        op.collectDaysLeft = COLLECT_DAYS;
        ExilePriceSpikes.applyTo(target);


        op.fleet.clearAssignments();
        op.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target.getPrimaryEntity(),
                COLLECT_DAYS + 2f, "conducting trade");

        Global.getLogger(ExileBlackFleetExtortionManager.class).info(
                "Extortion: collectors arrived at " + target.getName() + " ("
                        + target.getFactionId() + "), Price Spikes applied");
    }

    protected void creditReturn(ExtortionOp op) {
        if (op.proceedsResolved || !op.collected || !op.returning || op.returnPoint == null) return;
        SectorEntityToken station = Global.getSector().getEntityById(op.sourceStationId);
        if (station == null || !ExileBlackFleetProsperity.eligible(station.getMarket())) return;
        if (op.fleet.getContainingLocation() != op.returnPoint.getContainingLocation()
                || Misc.getDistance(op.fleet, op.returnPoint) > 500f) return;
        op.proceedsResolved = true;
        ExileBlackFleetProsperity.get().outcome(false, true, null);
    }

    protected void sendHome(ExtortionOp op) {
        if (op.returning) return;
        op.returning = true;
        issueReturnOrder(op);
    }

    /** The homeward order itself, separate from the state flip so the BF-7 watchdog can re-issue it. */
    protected void issueReturnOrder(ExtortionOp op) {
        if (op.fleet == null || !op.fleet.isAlive()) return;
        op.fleet.clearAssignments();
        SectorEntityToken home = null;
        SectorEntityToken station = op.sourceStationId != null
                ? Global.getSector().getEntityById(op.sourceStationId) : null;
        if (station != null && station.getContainingLocation() instanceof StarSystemAPI) {
            // Despawn point in HYPERSPACE near the anchor system — the disguised fleet
            // never enters the anchor system (its own side's patrols would attack it).
            StarSystemAPI sys = (StarSystemAPI) station.getContainingLocation();
            home = Global.getSector().getHyperspace().createToken(
                    sys.getLocation().x, sys.getLocation().y);
        }
        if (home == null) {
            home = op.fleet.getContainingLocation()
                    .createToken(op.fleet.getLocation().x, op.fleet.getLocation().y);
        }
        if (station != null) op.returnPoint = home;
        op.fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, home, 120f,
                "returning home");
    }

    /** Launch one collector run at a pickable market. */
    protected void tryLaunch() {
        MarketAPI source = pickSourceAnchor();
        if (source == null) return; // both anchors dead -> the racket dies with the fleet
        MarketAPI target = pickTarget(source);
        if (target == null) return;

        CampaignFleetAPI fleet = createCollectorFleet(source);
        if (fleet == null) return;

        // Spawn in HYPERSPACE near the anchor (the gotcha: BF patrols vs "independents").
        StarSystemAPI sourceSys = (StarSystemAPI) source.getContainingLocation();
        Random random = new Random();
        float ox = (random.nextFloat() - 0.5f) * 400f;
        float oy = (random.nextFloat() - 0.5f) * 400f;
        Global.getSector().getHyperspace().addEntity(fleet);
        fleet.setLocation(sourceSys.getLocation().x + ox, sourceSys.getLocation().y + oy);

        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target.getPrimaryEntity(), 120f,
                "delivering goods");
        fleet.addEventListener(this);

        ExtortionOp op = new ExtortionOp();
        op.fleet = fleet;
        op.targetMarketId = target.getId();
        op.sourceStationId = source.getPrimaryEntity() != null
                ? source.getPrimaryEntity().getId() : null;
        op.flagshipId = fleet.getFlagship() != null ? fleet.getFlagship().getId() : null;
        ops.add(op);

        Global.getLogger(ExileBlackFleetExtortionManager.class).info(
                "Extortion: collectors dispatched " + source.getName() + " -> "
                        + target.getName() + " (" + target.getFactionId() + "), interval "
                        + (int) currentIntervalDays + "d");

        if (debugMessages) {
            String where = target.getName();
            if (target.getContainingLocation() != null) {
                where = where + " in " + target.getContainingLocation().getNameWithLowercaseType();
            }
            Global.getSector().getCampaignUI().addMessage(
                    "DEBUG: Extortion collectors en route to " + where + ".",
                    Color.YELLOW);
        }
    }

    /** The disguise: sim-faction INDEPENDENT trade fleet, token escort, transponder on. */
    protected CampaignFleetAPI createCollectorFleet(MarketAPI source) {
        Random random = new Random();
        float escort = escortFpMin + random.nextFloat() * (float) (escortFpMax - escortFpMin);
        FleetParamsV3 params = new FleetParamsV3(
                null,                                  // no market: no quality inheritance leaks
                source.getLocationInHyperspace(),
                Factions.INDEPENDENT,
                null,
                FleetTypes.TRADE_SMALL,
                escort,                                // combat: token escort
                (float) freighterFp,                   // freighters: the "trade" part
                0f, 0f, 0f, 0f, 0f);
        params.random = random;
        params.ignoreMarketFleetSizeMult = Boolean.TRUE;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            Global.getLogger(ExileBlackFleetExtortionManager.class).warn(
                    "Extortion: collector fleet creation FAILED");
            return null;
        }
        float tanker = ExileBlackFleetLogistics.load().collectorTanker;
        ExileBlackFleetLogistics.addSupport(fleet, params, random, 0f, tanker);
        ExileBlackFleetLogistics.log(fleet, "collector", escort + freighterFp,
                escort, freighterFp, tanker, 0f);
        // The protection: default engagement rep suppressed (consequences are ours to
        // hand out via the listener); transponder on like any legitimate trader.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fleet.getMemoryWithoutUpdate().set(COLLECTOR_FLAG, true);
        fleet.setTransponderOn(true);
        // RUN, don't fight (playtest tuning 2026-07-11): this flag makes fleetWantsToFight
        // return false outright (FleetInteractionDialogPluginImpl:3512), and the weak-trader
        // AI's own pickEncounterOption already chooses DISENGAGE vs anything threatening —
        // so an attacked collector immediately attempts to disengage, and one PURSUIT drags
        // escort and freighters into a single battle instead of the fight-then-chase double.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        return fleet;
    }

    /** A living anchor, at random if both live (same helper contract as the raid manager). */
    protected MarketAPI pickSourceAnchor() {
        List alive = new ArrayList();
        MarketAPI sallee = getAnchorMarket(BlackFleetRaidManager.SALLEE_STATION);
        MarketAPI lundy = getAnchorMarket(BlackFleetRaidManager.LUNDY_STATION);
        if (sallee != null && sallee.isInEconomy()
                && BlackFleetRaidManager.BF_FACTION.equals(sallee.getFactionId())) alive.add(sallee);
        if (lundy != null && lundy.isInEconomy()
                && BlackFleetRaidManager.BF_FACTION.equals(lundy.getFactionId())) alive.add(lundy);
        if (alive.isEmpty()) return null;
        return (MarketAPI) alive.get(new Random().nextInt(alive.size()));
    }

    protected MarketAPI getAnchorMarket(String stationEntityId) {
        SectorEntityToken station = Global.getSector().getEntityById(stationEntityId);
        if (station == null) return null;
        return station.getMarket();
    }

    /** Extortable: no functional military beyond ground defenses / heavy batteries, not
     *  already paying, not hostile to the disguise, not player-owned. Closer = likelier. */
    protected MarketAPI pickTarget(MarketAPI source) {
        FactionAPI indie = Global.getSector().getFaction(Factions.INDEPENDENT);
        WeightedRandomPicker picker = new WeightedRandomPicker();

        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m.isHidden() || m.isPlayerOwned() || !m.isInEconomy()) continue;
            if (!(m.getContainingLocation() instanceof StarSystemAPI)) continue;
            String fid = m.getFactionId();
            if (BlackFleetRaidManager.BF_FACTION.equals(fid) || "exilemidan".equals(fid)) continue;
            if (ExilePriceSpikes.isSpiked(m)) continue;             // already paying
            if (alreadyTargeted(m.getId())) continue;               // a collector is en route
            if (indie != null && m.getFaction().isHostileTo(indie)) continue; // disguise fails
            if (hasFunctionalMilitary(m)) continue;                 // spec #1

            float distLY = Misc.getDistanceLY(
                    source.getPrimaryEntity().getLocationInHyperspace(),
                    m.getContainingLocation().getLocation());
            picker.add(m, 1f / (1f + distLY * 0.1f));
        }
        return (MarketAPI) picker.pick();
    }

    protected boolean alreadyTargeted(String marketId) {
        for (int i = 0; i < ops.size(); i++) {
            ExtortionOp op = (ExtortionOp) ops.get(i);
            if (marketId.equals(op.targetMarketId)) return true;
        }
        return false;
    }

    /** Spec #1: patrol HQ / military base / high command / any station blocks — but only
     *  while FUNCTIONAL. A disrupted military is an open extortion window. */
    protected boolean hasFunctionalMilitary(MarketAPI market) {
        List industries = market.getIndustries();
        for (int i = 0; i < industries.size(); i++) {
            Industry ind = (Industry) industries.get(i);
            if (ind == null || !ind.isFunctional()) continue;
            String id = ind.getId();
            if (Industries.PATROLHQ.equals(id) || Industries.MILITARYBASE.equals(id)
                    || Industries.HIGHCOMMAND.equals(id)) return true;
            if (ind.getSpec().hasTag(Industries.TAG_STATION)) return true;
        }
        return false;
    }

    // --- FleetEventListener: the racket's failure modes ---

    protected ExtortionOp findOp(CampaignFleetAPI fleet) {
        for (int i = 0; i < ops.size(); i++) {
            ExtortionOp curr = (ExtortionOp) ops.get(i);
            if (curr.fleet == fleet) return curr;
        }
        return null;
    }

    /** BOUNTY-MISSION LOGIC (author ruling 2026-07-11): the racket lives and dies with the
     *  collector FLAGSHIP — the captain's ship — not the whole fleet. Kill the flagship and
     *  the operation is broken even if fast freighters slip away; the survivors turn and
     *  run home. */
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        ExtortionOp op = findOp(fleet);
        if (op == null || op.flagshipId == null) return;

        boolean flagshipLost = false;
        List lost = Misc.getSnapshotMembersLost(fleet);
        for (int i = 0; i < lost.size(); i++) {
            com.fs.starfarer.api.fleet.FleetMemberAPI member =
                    (com.fs.starfarer.api.fleet.FleetMemberAPI) lost.get(i);
            if (op.flagshipId.equals(member.getId())) {
                flagshipLost = true;
                break;
            }
        }
        if (!flagshipLost) return;

        boolean playerKill = battle != null && battle.isPlayerInvolved();
        resolveBrokenRacket(op, playerKill);

        // Leaderless survivors abandon the run and scatter for home.
        if (op.fleet != null && op.fleet.isAlive() && !op.fleet.isEmpty()) {
            sendHome(op);
        }
        ops.remove(op);
    }

    /** Fallback: whole-fleet destruction where the battle listener didn't already resolve
     *  it (e.g. flagshipId missing on an op from an older save), plus op cleanup for
     *  ordinary despawns (reached home etc.). */
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        ExtortionOp op = findOp(fleet);
        if (op == null) return;
        ops.remove(op);
        if (reason == FleetDespawnReason.REACHED_DESTINATION) creditReturn(op);

        if (reason != FleetDespawnReason.DESTROYED_BY_BATTLE) return; // reached home etc.

        boolean playerKill = param instanceof BattleAPI
                && ((BattleAPI) param).isPlayerInvolved();
        resolveBrokenRacket(op, playerKill);
    }

    /** The consequences, per the 2026-07-11 rulings:
     *   - Player kills the flagship, ANY time: wreckage-reveal message + Black Fleet rep
     *     hit (they know who cost them).
     *   - ...AFTER collection additionally: Price Spikes removed + the extorted market's
     *     faction rep hit (officially you shot up a trade fleet in their space) + SILENT
     *     grudge (never refugees).
     *   - ...BEFORE arrival: NO target-faction penalty, no grudge (the market never knew).
     *   - NPCs kill the flagship (Kanta, the Path — ruling #9): quiet collapse, nothing
     *     player-facing. */
    protected void resolveBrokenRacket(ExtortionOp op, boolean playerKill) {
        if (!op.proceedsResolved) {
            op.proceedsResolved = true;
            ExileBlackFleetProsperity.get().outcome(false, false, null);
        }
        MarketAPI target = Global.getSector().getEconomy().getMarket(op.targetMarketId);

        if (op.conditionApplied) {
            ExilePriceSpikes.removeFrom(target); // the racket collapses with the captain
        }

        if (!playerKill) {
            Global.getLogger(ExileBlackFleetExtortionManager.class).info(
                    "Extortion: collector flagship lost to NPCs (target=" + op.targetMarketId
                            + ", collected=" + op.conditionApplied + "), quiet collapse");
            return;
        }

        // The wreckage tells (always on a player kill — author ruling 2026-07-11 #4).
        Global.getSector().getCampaignUI().addMessage(
                "Combat salvage crews report the destroyed \"trade fleet\" was riding empty "
                        + "-- empty holds, doctored manifests, and Black Fleet duty rosters.",
                Color.ORANGE);

        CustomRepImpact bfImpact = new CustomRepImpact();
        bfImpact.delta = -REP_HIT_BLACK_FLEET;
        Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, bfImpact, null, null, true),
                BlackFleetRaidManager.BF_FACTION);

        boolean grudgeSet = false;
        if (op.conditionApplied) {
            String targetFactionId = target != null ? target.getFactionId() : null;
            boolean refugee = ExilePriceSpikes.REFUGEE_FACTION.equals(targetFactionId);

            if (target != null && !refugee) {
                // Silent grudge (never for refugees): the fleet knows who cost them.
                // Timed (playtest-5): avenged by a sack or forgotten after GRUDGE_DAYS.
                target.getMemoryWithoutUpdate().set(GRUDGE_KEY, true, GRUDGE_DAYS);
                grudgeSet = true;
            }
            if (targetFactionId != null && !refugee) {
                // Officially, you destroyed a trade fleet in their space.
                CustomRepImpact tfImpact = new CustomRepImpact();
                tfImpact.delta = -REP_HIT_TARGET_FACTION;
                Global.getSector().adjustPlayerReputation(
                        new RepActionEnvelope(RepActions.CUSTOM, tfImpact, null, null, true),
                        targetFactionId);
            }
        }

        Global.getLogger(ExileBlackFleetExtortionManager.class).info(
                "Extortion: player killed collector flagship (target=" + op.targetMarketId
                        + ", collected=" + op.conditionApplied + "), racket broken, grudge="
                        + grudgeSet);
    }

    protected void loadConfig() {
        configLoaded = true;
        try {
            JSONObject root = Global.getSettings().loadJSON(CONFIG_PATH);
            JSONObject f = root.optJSONObject(CONFIG_KEY);
            JSONObject ext = f != null ? f.optJSONObject("extortion") : null;
            if (ext != null) {
                enabled = ext.optBoolean("enabled", enabled);
                startDelayDays = (float) ext.optDouble("startDelayDays", startDelayDays);
                initialIntervalDays = (float) ext.optDouble("initialIntervalDays", initialIntervalDays);
                minIntervalDays = (float) ext.optDouble("minIntervalDays", minIntervalDays);
                maxIntervalDays = (float) ext.optDouble("maxIntervalDays", maxIntervalDays);
                maxActive = ext.optInt("maxActive", maxActive);
                escortFpMin = ext.optInt("escortFpMin", escortFpMin);
                escortFpMax = ext.optInt("escortFpMax", escortFpMax);
                freighterFp = ext.optInt("freighterFp", freighterFp);
                debugMessages = ext.optBoolean("debugMessages", debugMessages);
            }
        } catch (Exception e) {
            Global.getLogger(ExileBlackFleetExtortionManager.class).warn(
                    "Extortion config load failed; using code defaults", e);
        }
        // Player-facing cap (Sean, 2026-10-06): free to change, but bounded -- a safety rail
        // against a mistyped value flooding the sector, not a balance setting.
        if (maxActive > MAX_ACTIVE_CEILING) {
            Global.getLogger(ExileBlackFleetExtortionManager.class).warn(
                    "Extortion maxActive " + maxActive + " exceeds the ceiling; clamped to "
                            + MAX_ACTIVE_CEILING);
            maxActive = MAX_ACTIVE_CEILING;
        }
        if (maxActive < 0) maxActive = 0;
        if (currentIntervalDays <= 0f) currentIntervalDays = initialIntervalDays;
        // Fresh saves start at the configured initial cadence; existing saves keep their
        // adapted value (it's saved state, only clamped to the configured bounds).
        if (currentIntervalDays < minIntervalDays) currentIntervalDays = minIntervalDays;
        if (currentIntervalDays > maxIntervalDays) currentIntervalDays = maxIntervalDays;
        tick = new IntervalUtil(0.4f, 0.6f);
    }
}
