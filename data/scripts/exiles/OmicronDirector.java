package data.scripts.exiles;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * Phase-1 Omicron "Sector Destabilization Director" — SLICE 5: vanilla RaidIntel operations.
 *
 * <p>The Director is now a pure LAUNCHER + capacity bookkeeper. Faction-wide raid and incursion
 * clocks spend a shared readiness pool, then select a free Nexus and build staged, route-managed
 * {@link OmicronRaidIntel} operations (assemble at the chosen Nexus →
 * travel → act → return); the vanilla stage machinery owns fleet lifecycle, intel entries,
 * ETA display, failure states, and out-of-range despawning — the old hand-rolled fleet
 * tracking, pending-incursion timers, and overdue-despawn failsafe are all retired.
 *
 * <p>What this buys over the direct-spawn prototype (design: docs/war-capacity-design.md;
 * decisions 2026-07-02): a real intel-tab entry for BOTH archetypes, the SURVIVAL GATE
 * (colony disruption only happens via {@code performRaid}: on screen from fleets that actually
 * arrive -- wipe the raiders and nothing lands; off screen via vanilla's strength autoresolve,
 * see OmicronRaidActionStage, OMI-4), per-kill bounties paid by the raided faction + rep via
 * the vanilla SYSTEM_BOUNTY_REWARD action (see {@link OmicronRaidIntel}), and free defender
 * scrambles (MilitaryResponseScript).
 *
 * <p>Marketless integration: each node gets a bare, never-in-economy source {@code MarketAPI}
 * whose primary entity is the node's Nexus (the ratified §2a escape hatch) — destroy the Nexus
 * and in-flight ops stop reinforcing, then fail ({@link OmicronAssembleStage}).
 */
public class OmicronDirector implements EveryFrameScript {

    /** Omicron faction id. Must match {@code ExileOmicron0.OMICRON_FACTION}. */
    public static final String OMICRON_FACTION = "exileomicron";

    /** Tag on each node's Nexus fleet (added in ExileOmicron0) — resolved by scanning per system. */
    public static final String NEXUS_TAG = "exile_omicron_nexus";

    /** Tag on dispatched raiders (identification + the bounty listener's filter). */
    public static final String RAIDER_TAG = "exile_omicron_raider";

    /** Shared, quest-writable list of target faction ids in sector persistent data. */
    public static final String TARGET_FACTIONS_KEY = "$exiles_omicron_target_factions";

    /** Baseline timestamp for combat maturity and the retained legacy escalation seam. */
    private static final String START_TS_KEY = "$exiles_omicron_start_ts";

    /** Override-flag prefix: "$exiles_omicron_asset_&lt;nodeId&gt;_&lt;assetKey&gt;_destroyed" (Boolean). */
    public static final String ASSET_FLAG_PREFIX = "$exiles_omicron_asset_";
    public static final String ASSET_FLAG_SUFFIX = "_destroyed";

    /** Quest-writable archetype switches (Boolean; absent = the config default applies). */
    public static final String RAIDS_ENABLED_KEY = "$exiles_omicron_raids_enabled";
    public static final String INCURSIONS_ENABLED_KEY = "$exiles_omicron_incursions_enabled";

    /** Quest-writable early-warning window (float days). Ops launch hidden and surface on the
     *  intel board only when the raiders arrive; a quest reward sets this (e.g. 10-15) to
     *  surface FUTURE ops that many days before arrival instead. Unset/0 = no advance notice. */
    public static final String EARLY_WARNING_DAYS_KEY = "$exiles_omicron_early_warning_days";

    private static final int LAUNCH_OK = 1;
    private static final int LAUNCH_DEFERRED = 0;
    private static final int LAUNCH_NO_TARGET = -1;
    private static final int LAUNCH_NO_ENEMY = -2;
    private static final int LAUNCH_ERROR = -3;
    /** OMI-3: no node can EVER source this archetype right now (not merely busy). */
    private static final int LAUNCH_NO_SOURCE = -4;

    // --- State. Faction clocks/readiness/pending requests + source markets are saved. ---
    /** @deprecated pre-per-node fields — retained only so old dev saves deserialize. */
    private IntervalUtil raidInterval;
    /** @deprecated pre-per-node fields — retained only so old dev saves deserialize. */
    private IntervalUtil incursionInterval;
    /** @deprecated pre-RaidIntel pending-incursion queue — retained only so old dev saves deserialize. */
    private List pendingIncursions;
    private HashMap nodeRaidTimers;      // nodeId (String) -> IntervalUtil
    private HashMap nodeIncursionTimers; // nodeId (String) -> IntervalUtil
    private HashMap nodeSourceMarkets;   // nodeId (String) -> bare MarketAPI (never in economy)
    private IntervalUtil factionRaidTimer;
    private IntervalUtil factionIncursionTimer;
    private float readiness;
    private boolean raidPending;
    private boolean incursionPending;
    private boolean schedulerRaidEnabled;
    private boolean schedulerRaidStateKnown;
    private boolean schedulerIncursionEnabled;
    private boolean schedulerIncursionStateKnown;
    private int schedulerVersion;
    private float schedulerDeferDays;
    private transient OmicronDirectorConfig config;
    private transient Random random;
    private transient IntervalUtil scanCheck;
    private transient IntervalUtil schedulerCheck;
    private transient float cachedAnnualReadiness;
    private transient float cachedEffectiveNexus;
    private transient float cachedEffectiveFuel;
    private transient float cachedEffectiveMining;
    private transient float loggedAnnualReadiness;
    private transient float loggedEffectiveNexus;
    private transient float loggedEffectiveFuel;
    private transient float loggedEffectiveMining;

    public OmicronDirector() {
        nodeRaidTimers = new HashMap();
        nodeIncursionTimers = new HashMap();
        nodeSourceMarkets = new HashMap();
        initTransients();
    }

    /** Re-init transient fields after deserialization (XStream skips them / they may be null). */
    protected Object readResolve() {
        if (nodeRaidTimers == null) nodeRaidTimers = new HashMap();
        if (nodeIncursionTimers == null) nodeIncursionTimers = new HashMap();
        if (nodeSourceMarkets == null) nodeSourceMarkets = new HashMap();
        initTransients();
        return this;
    }

    private void initTransients() {
        config = null; // lazy-loaded on first advance (settings are guaranteed up by then)
        random = new Random();
        scanCheck = new IntervalUtil(0.2f, 0.3f);
        schedulerCheck = new IntervalUtil(0.2f, 0.3f);
        cachedAnnualReadiness = 0f;
        cachedEffectiveNexus = 0f;
        cachedEffectiveFuel = 0f;
        cachedEffectiveMining = 0f;
        loggedAnnualReadiness = -1f;
        loggedEffectiveNexus = -1f;
        loggedEffectiveFuel = -1f;
        loggedEffectiveMining = -1f;
    }

    public boolean isDone() {
        return false; // lives for the whole campaign
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void advance(float amount) {
        if (Global.getSector() == null) return;
        if (Global.getSector().isPaused()) return;
        if (Global.getSector().isInNewGameAdvance()) return;

        if (random == null) random = new Random();
        if (scanCheck == null) scanCheck = new IntervalUtil(0.2f, 0.3f);
        if (schedulerCheck == null) schedulerCheck = new IntervalUtil(0.2f, 0.3f);
        if (nodeRaidTimers == null) nodeRaidTimers = new HashMap();
        if (nodeIncursionTimers == null) nodeIncursionTimers = new HashMap();
        if (nodeSourceMarkets == null) nodeSourceMarkets = new HashMap();
        if (config == null) config = OmicronDirectorConfig.load();
        ensureSchedulerState();

        float days = Global.getSector().getClock().convertToDays(amount);

        // Short cadence (~0.25 days): growth accrual + stage builds + asset/Nexus scans +
        // rebuild processing + modifier expiry + threshold bands, so facility kills get
        // their message within seconds of the battle.
        scanCheck.advance(days);
        if (scanCheck.intervalElapsed()) {
            OmicronWarCapacity wc = OmicronWarCapacity.get();
            accrueGrowth(wc);
            advanceGrowthStages(wc);
            for (int n = 0; n < config.nodes.size(); n++) {
                OmicronDirectorConfig.Node node = (OmicronDirectorConfig.Node) config.nodes.get(n);
                CampaignFleetAPI nodeNexus = findNexus(node);
                boolean nexusAlive = nodeNexus != null && nodeNexus.isAlive();
                scanAssets(node, wc, nexusAlive);
                checkNexusState(node, wc, nexusAlive);
                processRebuilds(node, wc, nexusAlive);
            }
            wc.pruneExpired();       // expiry pruning IS the regen-to-cap
            checkThresholds(wc);
            refreshNetworkSnapshot(wc);
        }

        readiness += days * cachedAnnualReadiness / 365f;
        if (readiness < 0f) readiness = 0f;
        if (readiness > config.readinessMax) readiness = config.readinessMax;
        if (schedulerDeferDays > 0f) {
            schedulerDeferDays -= days;
            if (schedulerDeferDays < 0f) schedulerDeferDays = 0f;
        }

        advanceRequestClocks(days);
        schedulerCheck.advance(days);
        if (schedulerCheck.intervalElapsed()) processScheduler();
    }

    private void ensureSchedulerState() {
        if (schedulerVersion < 1) {
            readiness = 0f;
            raidPending = false;
            incursionPending = false;
            factionRaidTimer = new IntervalUtil(
                    config.raidIntervalMinDays, config.raidIntervalMaxDays);
            factionIncursionTimer = new IntervalUtil(
                    config.incursionIntervalMinDays, config.incursionIntervalMaxDays);
            schedulerIncursionEnabled = isArchetypeEnabled(true);
            schedulerIncursionStateKnown = true;
            schedulerRaidEnabled = isArchetypeEnabled(false);
            schedulerRaidStateKnown = true;
            schedulerVersion = 1;
            Global.getLogger(OmicronDirector.class).info(
                    "Omicron readiness scheduler initialized: pool=0, fresh request clocks");
        }
        if (factionRaidTimer == null) {
            factionRaidTimer = new IntervalUtil(
                    config.raidIntervalMinDays, config.raidIntervalMaxDays);
        }
        if (factionIncursionTimer == null) {
            factionIncursionTimer = new IntervalUtil(
                    config.incursionIntervalMinDays, config.incursionIntervalMaxDays);
        }
    }

    private void advanceRequestClocks(float days) {
        boolean raidsEnabled = isArchetypeEnabled(false);
        if (!schedulerRaidStateKnown || raidsEnabled != schedulerRaidEnabled) {
            schedulerRaidStateKnown = true;
            schedulerRaidEnabled = raidsEnabled;
            raidPending = false;
            factionRaidTimer = new IntervalUtil(
                    config.raidIntervalMinDays, config.raidIntervalMaxDays);
        }
        boolean incursionsEnabled = isArchetypeEnabled(true);
        if (!schedulerIncursionStateKnown || incursionsEnabled != schedulerIncursionEnabled) {
            schedulerIncursionStateKnown = true;
            schedulerIncursionEnabled = incursionsEnabled;
            incursionPending = false;
            factionIncursionTimer = new IntervalUtil(
                    config.incursionIntervalMinDays, config.incursionIntervalMaxDays);
            Global.getLogger(OmicronDirector.class).info(
                    "Omicron incursion gate " + (incursionsEnabled ? "OPENED" : "CLOSED")
                            + ": reservation cleared, fresh clock armed");
        }

        if (!raidPending && raidsEnabled) {
            factionRaidTimer.advance(days);
            if (factionRaidTimer.intervalElapsed()) raidPending = true;
        }
        if (!incursionPending && incursionsEnabled) {
            factionIncursionTimer.advance(days);
            if (factionIncursionTimer.intervalElapsed()) incursionPending = true;
        }
    }

    private void processScheduler() {
        if (schedulerDeferDays > 0f) return;
        if (incursionPending) {
            if (readiness + 0.0001f < config.readinessIncursionCost) return;
            int result = launchOperation(true);
            resolveRequest(true, result);
            return; // at most one scheduler decision per tick
        }
        if (!raidPending || !isArchetypeEnabled(false)) return;
        if (readiness + 0.0001f < config.readinessRaidCost) return;
        int result = launchOperation(false);
        resolveRequest(false, result);
    }

    private void resolveRequest(boolean incursion, int result) {
        // OMI-15 (bug sweep, 2026-10-06): every non-launch outcome is now visible in the log (one
        // line per scheduler decision; deferred retries are spaced by readinessRetryDays / 2).
        if (result != LAUNCH_OK) {
            Global.getLogger(OmicronDirector.class).info(
                    "Omicron " + (incursion ? "incursion" : "raid") + " request not launched: "
                            + (result == LAUNCH_DEFERRED ? "DEFERRED (global cap, no free source,"
                                    + " or a temporary block)"
                               : result == LAUNCH_ERROR ? "LAUNCH_ERROR"
                               : result == LAUNCH_NO_ENEMY ? "no strategic enemy"
                               : result == LAUNCH_NO_TARGET ? "no feasible target"
                               : result == LAUNCH_NO_SOURCE ? "no incursion-capable node alive"
                                    + " (request cleared; raids continue)"
                               : "result " + result));
        }
        if (result == LAUNCH_DEFERRED || result == LAUNCH_ERROR) {
            schedulerDeferDays = Math.max(1f, config.readinessRetryDays * 0.5f);
            return;
        }
        if (result == LAUNCH_NO_SOURCE) {
            // OMI-3 (2026-10-06): every mature Nexus is dead and the Lattice cannot source
            // incursions until it completes. That is not a temporary block, so do not hold the
            // pending request -- it would also block every raid behind it. Clear it at no
            // planning cost and look again after the normal retry delay.
            schedulerDeferDays = 0f;
            clearAndRearmRetry(incursion, config.readinessRetryDays);
            return;
        }
        schedulerDeferDays = 0f;
        float cost = incursion ? config.readinessIncursionCost : config.readinessRaidCost;
        if (result == LAUNCH_OK) {
            readiness -= cost;
            if (readiness < 0f) readiness = 0f;
            clearAndRearmNormal(incursion);
            return;
        }
        float planningCost = cost * config.readinessFailureFraction;
        readiness -= planningCost;
        if (readiness < 0f) readiness = 0f;
        clearAndRearmRetry(incursion, result == LAUNCH_NO_ENEMY
                ? config.readinessNoEnemyRetryDays : config.readinessRetryDays);
        Global.getLogger(OmicronDirector.class).info(
                "Omicron " + (incursion ? "INCURSION" : "raid")
                        + " planning cost=" + planningCost + ", readiness=" + readiness);
    }

    private void clearAndRearmNormal(boolean incursion) {
        if (incursion) {
            incursionPending = false;
            factionIncursionTimer = new IntervalUtil(
                    config.incursionIntervalMinDays, config.incursionIntervalMaxDays);
        } else {
            raidPending = false;
            factionRaidTimer = new IntervalUtil(
                    config.raidIntervalMinDays, config.raidIntervalMaxDays);
        }
    }

    private void clearAndRearmRetry(boolean incursion, float days) {
        if (days <= 0f) days = 1f;
        if (incursion) {
            incursionPending = false;
            factionIncursionTimer = new IntervalUtil(days, days);
        } else {
            raidPending = false;
            factionRaidTimer = new IntervalUtil(days, days);
        }
    }

    // --- Operation launching (vanilla staged RaidIntel; see class javadoc) ---

    /** Persistent switch if a quest has set one, else the config's campaign-start default. */
    private boolean isArchetypeEnabled(boolean incursion) {
        Object v = Global.getSector().getPersistentData().get(
                incursion ? INCURSIONS_ENABLED_KEY : RAIDS_ENABLED_KEY);
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        return incursion ? config.incursionEnabled : config.raidEnabled;
    }

    /** Phase-2 quest hook: turn Omicron raids on/off at runtime (no Director handle needed). */
    public static void setRaidsEnabled(boolean enabled) {
        if (Global.getSector() == null) return;
        Global.getSector().getPersistentData().put(RAIDS_ENABLED_KEY, Boolean.valueOf(enabled));
    }

    /** Phase-2 quest hook: turn Omicron incursions on/off at runtime (no Director handle needed). */
    public static void setIncursionsEnabled(boolean enabled) {
        if (Global.getSector() == null) return;
        Global.getSector().getPersistentData().put(INCURSIONS_ENABLED_KEY, Boolean.valueOf(enabled));
    }

    /** Shared source/global operation-count query used by the Tri-Tachyon defense manager. */
    public static int countLiveOpsForNode(String nodeId, boolean incursion) {
        List intels = Global.getSector().getIntelManager().getIntel(OmicronRaidIntel.class);
        int n = 0;
        for (int i = 0; i < intels.size(); i++) {
            OmicronRaidIntel op = (OmicronRaidIntel) intels.get(i);
            if (op.isEnded() || op.isEnding()) continue;
            if (op.isIncursion() != incursion) continue;
            if (nodeId != null && !nodeId.equals(op.getNodeId())) continue;
            n++;
        }
        return n;
    }

    private boolean isSourceFree(String nodeId) {
        return countLiveOpsForNode(nodeId, false) + countLiveOpsForNode(nodeId, true) == 0
                && OmicronTriTachyonResponseScript.countActiveForNode(nodeId) == 0;
    }

    private boolean maySource(OmicronDirectorConfig.Node node, boolean incursion) {
        if (!node.uc) return true;
        OmicronWarCapacity wc = OmicronWarCapacity.get();
        if (wc.isLatticeCompleted()) return true;
        if (incursion) return false;
        return wc.getGrowthProgress() >= config.growthRaidsAt();
    }

    private void refreshNetworkSnapshot(OmicronWarCapacity wc) {
        boolean anyLivingNexus = false;
        float effectiveNexus = 0f;
        HashMap nexusAlive = new HashMap();
        for (int i = 0; i < config.nodes.size(); i++) {
            OmicronDirectorConfig.Node node = (OmicronDirectorConfig.Node) config.nodes.get(i);
            CampaignFleetAPI nexus = findNexus(node);
            boolean alive = nexus != null && nexus.isAlive();
            nexusAlive.put(node.id, Boolean.valueOf(alive));
            if (!alive) continue;
            anyLivingNexus = true;
            if (!node.uc || wc.isLatticeCompleted()) effectiveNexus += 1f;
            else if (wc.getGrowthProgress() >= config.growthRaidsAt()) effectiveNexus += 0.5f;
        }

        float fuel = 0f;
        float mining = 0f;
        for (int i = 0; i < config.assets.size(); i++) {
            OmicronDirectorConfig.Asset asset =
                    (OmicronDirectorConfig.Asset) config.assets.get(i);
            if (!"fuel".equals(asset.type) && !"mining".equals(asset.type)) continue;
            OmicronDirectorConfig.Node node = nodeById(asset.nodeId);
            if (node == null) continue;
            if (!wc.isBuilt(node.id, asset.key, !node.uc)) continue;
            if (wc.isKnownDestroyed(node.id, asset.key)) continue;
            boolean localAlive = ((Boolean) nexusAlive.get(node.id)).booleanValue();
            float contribution = localAlive ? 1f : (anyLivingNexus ? 0.5f : 0f);
            if ("fuel".equals(asset.type)) fuel += contribution;
            else mining += contribution;
        }

        cachedEffectiveNexus = effectiveNexus;
        cachedEffectiveFuel = fuel;
        cachedEffectiveMining = mining;
        if (!anyLivingNexus || effectiveNexus <= 0f) {
            cachedAnnualReadiness = 0f;
            logNetworkSnapshotIfChanged();
            return;
        }
        float nexusPart = effectiveNexus / Math.max(0.01f, config.nexusNormalization);
        float fuelPart = fuel / Math.max(0.01f, config.fuelNormalization);
        if (fuelPart < 0f) fuelPart = 0f;
        if (fuelPart > 1f) fuelPart = 1f;
        cachedAnnualReadiness = config.readinessPerYear * nexusPart
                * (config.fuelBaseEfficiency + (1f - config.fuelBaseEfficiency) * fuelPart);
        logNetworkSnapshotIfChanged();
    }

    private void logNetworkSnapshotIfChanged() {
        if (Math.abs(cachedAnnualReadiness - loggedAnnualReadiness) < 0.001f
                && Math.abs(cachedEffectiveNexus - loggedEffectiveNexus) < 0.001f
                && Math.abs(cachedEffectiveFuel - loggedEffectiveFuel) < 0.001f
                && Math.abs(cachedEffectiveMining - loggedEffectiveMining) < 0.001f) return;
        loggedAnnualReadiness = cachedAnnualReadiness;
        loggedEffectiveNexus = cachedEffectiveNexus;
        loggedEffectiveFuel = cachedEffectiveFuel;
        loggedEffectiveMining = cachedEffectiveMining;
        Global.getLogger(OmicronDirector.class).info(
                "Omicron network: readinessPerYear=" + cachedAnnualReadiness
                        + ", pool=" + readiness + "/" + config.readinessMax
                        + ", effectiveNexus=" + cachedEffectiveNexus
                        + ", effectiveFuel=" + cachedEffectiveFuel
                        + ", effectiveMining=" + cachedEffectiveMining);
    }

    private OmicronDirectorConfig.Node nodeById(String nodeId) {
        if (nodeId == null) return null;
        for (int i = 0; i < config.nodes.size(); i++) {
            OmicronDirectorConfig.Node node = (OmicronDirectorConfig.Node) config.nodes.get(i);
            if (nodeId.equals(node.id)) return node;
        }
        return null;
    }

    /** Resolve one faction-wide request into an atomic source-target pair and staged operation. */
    private int launchOperation(boolean incursion) {
        if (countLiveOpsForNode(null, false) + countLiveOpsForNode(null, true)
                >= config.globalMaxActive) return LAUNCH_DEFERRED;
        if (incursion && countLiveOpsForNode(null, true) >= config.globalMaxIncursions) {
            return LAUNCH_DEFERRED;
        }
        if (incursion && !hasIncursionCapableNode()) return LAUNCH_NO_SOURCE; // OMI-3

        float spawnFP = rollOperationFp(incursion);
        LaunchSearch search = findLaunchChoices(incursion, spawnFP);
        if (search.choices.isEmpty()) {
            if (!search.hadFreeSource) return LAUNCH_DEFERRED;
            if (!search.hadStrategicEnemy) return LAUNCH_NO_ENEMY;
            if (search.hadTemporaryBlock) return LAUNCH_DEFERRED;
            return LAUNCH_NO_TARGET;
        }
        LaunchChoice choice = (LaunchChoice) search.choices.get(
                random.nextInt(search.choices.size()));
        return buildOperation(choice.node, choice.nexus, choice.target, incursion, spawnFP);
    }

    /** OMI-3: is any node with a living Nexus allowed to source an incursion at all? Busy
     *  sources still count -- busy is temporary, this asks whether the capability exists. */
    private boolean hasIncursionCapableNode() {
        for (int i = 0; i < config.nodes.size(); i++) {
            OmicronDirectorConfig.Node node = (OmicronDirectorConfig.Node) config.nodes.get(i);
            if (!maySource(node, true)) continue;
            CampaignFleetAPI nexus = findNexus(node);
            if (nexus != null && nexus.isAlive()) return true;
        }
        return false;
    }

    private int buildOperation(OmicronDirectorConfig.Node node, CampaignFleetAPI nexus,
                               MarketAPI target, boolean incursion, float spawnFP) {
        if (nexus == null || !nexus.isAlive() || !isSourceFree(node.id)) return LAUNCH_DEFERRED;
        LocationAPI loc = target.getContainingLocation();
        if (!(loc instanceof StarSystemAPI)) return LAUNCH_ERROR;
        StarSystemAPI targetSystem = (StarSystemAPI) loc;

        SectorEntityToken gather = findGatheringPoint(node, nexus);
        FactionAPI omicron = Global.getSector().getFaction(OMICRON_FACTION);
        SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(omicron, targetSystem.getCenter());
        if (gather == null || raidJump == null || omicron == null) return LAUNCH_ERROR;
        float abortFP = spawnFP * config.abortFraction;

        OmicronRaidIntel op = new OmicronRaidIntel(targetSystem, omicron, node.id, incursion,
                target.getFactionId(), config.bountyPerFrigate);

        OmicronAssembleStage assemble = new OmicronAssembleStage(op, gather);
        assemble.addSource(sourceMarketFor(node, nexus));
        assemble.setSpawnFP(spawnFP);
        assemble.setAbortFP(abortFP);
        op.addStage(assemble);

        TravelStage travel = new TravelStage(op, gather, raidJump, false);
        travel.setAbortFP(abortFP);
        op.addStage(travel);

        int objectiveBudget = incursion ? rollObjectiveBudget() : 1;
        OmicronRaidActionStage action = new OmicronRaidActionStage(op, targetSystem,
                getTargetFactionIds(), config, target.getId(), objectiveBudget);
        action.setAbortFP(abortFP);
        op.addStage(action);

        op.addStage(new ReturnStage(op));

        // Hidden launch: always add silently — the intel reveals ITSELF (arrival, or the
        // quest-granted early-warning window, or first player contact; OmicronRaidIntel).
        op.setNoticeDays(Global.getSector().getMemoryWithoutUpdate().getFloat(EARLY_WARNING_DAYS_KEY));
        Global.getSector().getIntelManager().addIntel(op, true);

        Global.getLogger(OmicronDirector.class).info(
                "Omicron " + (incursion ? "INCURSION" : "raid") + " op (node " + node.id + "): "
                        + Math.round(spawnFP) + " FP -> " + targetSystem.getBaseName()
                        + " (target market " + target.getName() + ", " + target.getFactionId()
                        + "), readinessBeforeSpend=" + readiness + ", objectives=" + objectiveBudget
                        + ", maturity=" + getCombatMaturity()
                        + ", mining=" + cachedEffectiveMining
                        + ", liveGlobal=" + (countLiveOpsForNode(null, false)
                        + countLiveOpsForNode(null, true)));
        return LAUNCH_OK;
    }

    private float rollOperationFp(boolean incursion) {
        float maturity = getCombatMaturity();
        float mining = cachedEffectiveMining / Math.max(0.01f, config.miningNormalization);
        if (mining < 0f) mining = 0f;
        if (mining > 1f) mining = 1f;
        // Mining scales the whole age-adjusted budget, including the starting floor.
        float miningMult = config.miningBaseEfficiency
                + (1f - config.miningBaseEfficiency) * mining;
        float startMin = incursion ? config.incursionStartFpMin : config.raidStartFpMin;
        float startMax = incursion ? config.incursionStartFpMax : config.raidStartFpMax;
        float matureMin = incursion ? config.incursionSpawnFpMin : config.raidSpawnFpMin;
        float matureMax = incursion ? config.incursionSpawnFpMax : config.raidSpawnFpMax;
        float min = (startMin + (matureMin - startMin) * maturity) * miningMult;
        float max = (startMax + (matureMax - startMax) * maturity) * miningMult;
        float fp = min + random.nextFloat() * (max - min);
        float questMult = getStrategicStrengthMult(incursion);
        return fp * questMult;
    }

    private float getStrategicStrengthMult(boolean incursion) {
        float sum = 0f;
        int count = 0;
        for (int i = 0; i < config.nodes.size(); i++) {
            OmicronDirectorConfig.Node node = (OmicronDirectorConfig.Node) config.nodes.get(i);
            if (!maySource(node, incursion) || findNexus(node) == null) continue;
            sum += OmicronWarCapacity.getValue(node.id, OmicronWarCapacity.STAT_STRENGTH)
                    * OmicronWarCapacity.getValue(node.id, OmicronWarCapacity.STAT_ESCALATION);
            count++;
        }
        float mult = count > 0 ? sum / count : 1f;
        if (mult < OmicronWarCapacity.MIN_VALUE) mult = OmicronWarCapacity.MIN_VALUE;
        if (mult > config.eventOverdriveCap) mult = config.eventOverdriveCap;
        return mult;
    }

    private float getCombatMaturity() {
        Object v = Global.getSector().getPersistentData().get(START_TS_KEY);
        long startTs;
        if (v instanceof Long) {
            startTs = ((Long) v).longValue();
        } else {
            startTs = Global.getSector().getClock().getTimestamp();
            Global.getSector().getPersistentData().put(START_TS_KEY, Long.valueOf(startTs));
        }
        if (config.combatMaturityDays <= 0f) return 1f;
        float raw = Global.getSector().getClock().getElapsedDaysSince(startTs)
                / config.combatMaturityDays;
        if (raw < 0f) raw = 0f;
        if (raw > 1f) raw = 1f;
        return (float) Math.pow(raw, config.combatMaturityExponent);
    }

    private int rollObjectiveBudget() {
        float maturity = getCombatMaturity();
        float one = 0.60f + (0.35f - 0.60f) * maturity;
        float two = 0.40f + (0.50f - 0.40f) * maturity;
        float roll = random.nextFloat();
        if (roll < one) return 1;
        if (roll < one + two) return 2;
        return 3;
    }

    /** The staging point: the jump point nearest the node's Nexus, or the Nexus itself. */
    private SectorEntityToken findGatheringPoint(OmicronDirectorConfig.Node node, CampaignFleetAPI nexus) {
        StarSystemAPI system = Global.getSector().getStarSystem(node.system);
        if (system == null) return nexus;
        List points = system.getEntities(JumpPointAPI.class);
        SectorEntityToken best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            SectorEntityToken p = (SectorEntityToken) points.get(i);
            float d = Misc.getDistance(nexus.getLocation(), p.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best != null ? best : nexus;
    }

    /**
     * The node's bare source market: a MarketAPI OBJECT (never added to the economy) whose
     * primary entity is the Nexus — what the vanilla route machinery needs from a "source"
     * without any economy participation (ARCHITECTURE §2a escape hatch). Persisted so routes
     * saved mid-operation keep referring to the same object; primary entity refreshed each
     * launch so a revived/replaced Nexus is picked up.
     */
    private MarketAPI sourceMarketFor(OmicronDirectorConfig.Node node, CampaignFleetAPI nexus) {
        MarketAPI m = (MarketAPI) nodeSourceMarkets.get(node.id);
        if (m == null) {
            m = Global.getFactory().createMarket("exiles_omicron_src_" + node.id, nexus.getName(), 3);
            m.setFactionId(OMICRON_FACTION);
            m.setHidden(true);
            nodeSourceMarkets.put(node.id, m);
        }
        m.setPrimaryEntity(nexus);
        // A bare market has NO conditions, so COMBAT_FLEET_SIZE_MULT computes to its base of
        // ZERO -> FleetFactoryV3 builds empty fleets -> RouteManager expires the route (the
        // silent no-fleets raid-failure bug, found 2026-07-03). Mirror exactly what
        // FleetFactoryV3 does for its own fake no-market market: pin the stat to 1. Applied
        // on EVERY call (idempotent, same modifier id) so markets persisted by older saves
        // are healed too.
        m.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat("exiles_wc", 1f);
        m.getStability().modifyFlat("exiles_wc", 10000f);
        return m;
    }

    // --- War capacity: asset scan, escalation, thresholds ---

    /** Detect newly-destroyed / revived facilities and hold the matching axis modifiers in place. */
    private void scanAssets(OmicronDirectorConfig.Node node, OmicronWarCapacity wc, boolean nexusAlive) {
        for (int i = 0; i < config.assets.size(); i++) {
            OmicronDirectorConfig.Asset asset = (OmicronDirectorConfig.Asset) config.assets.get(i);
            if (asset.nodeId != null && !node.id.equals(asset.nodeId)) continue;
            if ("ucnexus".equals(asset.type) && wc.isLatticeCompleted()) continue;
            // Spread gating: ucnexus rows only exist on UC nodes, and a UC system's not-yet-
            // built stations must not read as "destroyed" (built default: true for active
            // nodes' gen-time stations, false on UC nodes until a growth stage spawns them).
            if (asset.ucOnly && !node.uc) continue;
            if (!wc.isBuilt(node.id, asset.key, !node.uc)) continue;
            boolean destroyed = isAssetDestroyed(node, asset);
            boolean known = wc.isKnownDestroyed(node.id, asset.key);
            if (destroyed == known) continue;
            wc.setKnownDestroyed(node.id, asset.key, destroyed);
            if (destroyed) {
                applyAssetDeath(node, asset, wc);
                announceAssetDeath(node, asset);
                // Ratified 120d rebuild (#8): arm the rebuild clock — only while the node's
                // Nexus lives (a decapitated node never rebuilds).
                if (asset.rebuildDays > 0f && nexusAlive) {
                    wc.setRebuildTs(node.id, asset.key,
                            Global.getSector().getClock().getTimestamp());
                }
            } else {
                revertAssetDeath(node, asset, wc);
                wc.clearRebuildTs(node.id, asset.key);
                Global.getLogger(OmicronDirector.class).info(
                        "Omicron asset REVIVED: " + asset.key + " @ node " + node.id);
            }
        }
    }

    /**
     * Destroyed = override flag if present (quest verb: sabotage/revive without a battle),
     * otherwise no living tagged fleet in the node's system (station fleets fade on defeat).
     */
    private boolean isAssetDestroyed(OmicronDirectorConfig.Node node, OmicronDirectorConfig.Asset asset) {
        Object flag = Global.getSector().getPersistentData().get(
                ASSET_FLAG_PREFIX + node.id + "_" + asset.key + ASSET_FLAG_SUFFIX);
        if (flag instanceof Boolean) return ((Boolean) flag).booleanValue();

        StarSystemAPI system = Global.getSector().getStarSystem(node.system);
        if (system == null) return false; // misconfigured node: never falsely nerf
        return !hasLivingTagged(system, asset.tag);
    }

    /** Any living fleet with the tag in the system? (Station fleets fadeAndExpire on defeat.) */
    private boolean hasLivingTagged(StarSystemAPI system, String tag) {
        List fleets = system.getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI f = (CampaignFleetAPI) fleets.get(i);
            if (f != null && f.isAlive() && f.hasTag(tag)) return true;
        }
        return false;
    }

    private void applyAssetDeath(OmicronDirectorConfig.Node node, OmicronDirectorConfig.Asset asset,
                                 OmicronWarCapacity wc) {
        Iterator it = asset.mods.keySet().iterator();
        while (it.hasNext()) {
            String statId = (String) it.next();
            Float mult = (Float) asset.mods.get(statId);
            if (mult == null) continue;
            OmicronWarCapacity.modifyMult(node.id, statId,
                    OmicronWarCapacity.ASSET_MOD_PREFIX + asset.key, mult.floatValue(), 0f);
        }
        if (asset.freezeEscalation) {
            wc.setFrozenEscalation(node.id, computeRampMult());
        }
    }

    private void revertAssetDeath(OmicronDirectorConfig.Node node, OmicronDirectorConfig.Asset asset,
                                  OmicronWarCapacity wc) {
        Iterator it = asset.mods.keySet().iterator();
        while (it.hasNext()) {
            String statId = (String) it.next();
            OmicronWarCapacity.unmodify(node.id, statId,
                    OmicronWarCapacity.ASSET_MOD_PREFIX + asset.key);
        }
        if (asset.freezeEscalation) {
            wc.clearFrozenEscalation(node.id);
        }
    }

    /** The big perceivable payoff moment: axis-specific text the instant a facility kill registers. */
    private void announceAssetDeath(OmicronDirectorConfig.Node node, OmicronDirectorConfig.Asset asset) {
        String sysName = node.system;
        StarSystemAPI system = Global.getSector().getStarSystem(node.system);
        if (system != null) sysName = system.getBaseName();
        String text;
        if ("mining".equals(asset.type)) {
            text = "With its ore refinery in the " + sysName
                    + " system destroyed, Omicron's next operations will draw on less industrial capacity.";
        } else if ("fuel".equals(asset.type)) {
            text = "With its fuel refinery in the " + sysName
                    + " system destroyed, Omicron's network will prepare attacks more slowly.";
        } else if ("ucnexus".equals(asset.type)) {
            text = "The Omicron construction site in the " + sysName
                    + " system has fallen silent. The machine's growth there has been halted.";
        } else {
            text = "An Omicron facility in the " + sysName + " system has been destroyed.";
        }
        Global.getSector().getCampaignUI().addMessage(text, Color.YELLOW);
        Global.getLogger(OmicronDirector.class).info(
                "Omicron asset DESTROYED: " + asset.key + " @ node " + node.id);
    }

    /**
     * Decapitation legibility: the Nexus is the hard gate rather than an asset row, so it needs
     * its own alive→dead transition watch — the hardest-earned kill must not be the silent one.
     * Uses the same known-state table as the assets under the reserved key "nexus".
     */
    private void checkNexusState(OmicronDirectorConfig.Node node, OmicronWarCapacity wc, boolean nexusAlive) {
        boolean dead = !nexusAlive;
        boolean known = wc.isKnownDestroyed(node.id, "nexus");
        if (dead == known) return;
        wc.setKnownDestroyed(node.id, "nexus", dead);
        if (!dead) {
            Global.getLogger(OmicronDirector.class).info(
                    "Omicron Nexus REVIVED @ node " + node.id);
            return;
        }

        String sysName = node.system;
        StarSystemAPI system = Global.getSector().getStarSystem(node.system);
        if (system != null) sysName = system.getBaseName();
        Global.getSector().getCampaignUI().addMessage(
                "The Omicron Nexus in the " + sysName
                        + " system has been destroyed. Machine activity from the system has ceased.",
                Color.YELLOW);
        Global.getLogger(OmicronDirector.class).info("Omicron Nexus DESTROYED @ node " + node.id);

        // Faction-defeat condition: every node decapitated.
        boolean allDead = true;
        for (int n = 0; n < config.nodes.size(); n++) {
            OmicronDirectorConfig.Node other = (OmicronDirectorConfig.Node) config.nodes.get(n);
            if (!wc.isKnownDestroyed(other.id, "nexus")) {
                allDead = false;
                break;
            }
        }
        if (allDead) {
            Global.getSector().getCampaignUI().addMessage(
                    "With its last Nexus destroyed, the Omicron machine has fallen silent across the sector.",
                    Color.YELLOW);
            Global.getLogger(OmicronDirector.class).info("Omicron DECAPITATED: all nodes dead.");
        }
    }

    // --- Lattice construction + station rebuild (war-capacity-design.md amendment) ---

    /**
     * Lattice construction progress: 0..1 over escalationRampDays, accruing only while its
     * damaged Nexus survives. This clock controls Lattice's staged build and nothing else:
     * ordinary operation strength, faction readiness, and the corporate umbrella have their
     * own independent clocks/formulas.
     */
    private void accrueGrowth(OmicronWarCapacity wc) {
        OmicronDirectorConfig.Node uc = config.ucNode();
        if (uc == null) return;
        long now = Global.getSector().getClock().getTimestamp();
        long last = wc.getGrowthLastTs();
        wc.setGrowthLastTs(now);
        if (last == 0L) return; // first tick: baseline only
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(last);
        if (elapsed <= 0f) return;
        if (config.escalationRampDays <= 0f) return;
        CampaignFleetAPI ucNexus = findNexus(uc);
        if (ucNexus == null || !ucNexus.isAlive()) return; // growth frozen
        wc.setGrowthProgress(wc.getGrowthProgress() + elapsed / config.escalationRampDays);
    }

    /**
     * Spawn the UC system's stations as growth crosses their stage thresholds. Deliberately
     * NO campaign message (op-source secrecy: the player may not have found the system);
     * the log carries the event. A failed spec is retried next tick and WARNs via the builder.
     */
    private void advanceGrowthStages(OmicronWarCapacity wc) {
        OmicronDirectorConfig.Node uc = config.ucNode();
        if (uc == null) return;
        float progress = wc.getGrowthProgress();
        for (int i = 0; i < config.growthStages.size(); i++) {
            OmicronDirectorConfig.GrowthStage stage =
                    (OmicronDirectorConfig.GrowthStage) config.growthStages.get(i);
            if (stage.build == null) continue;
            if (progress < stage.at) continue;
            if (wc.isBuilt(uc.id, stage.build, false)) continue;
            CampaignFleetAPI fleet = spawnStation(uc, stage.build, wc);
            if (fleet == null) continue;
            Global.getLogger(OmicronDirector.class).info(
                    "Omicron growth stage: built " + stage.build + " @ node " + uc.id
                            + " (progress " + progress + ")");
        }
        if (progress >= config.growthCompletionAt() && !wc.isLatticeCompleted()) {
            completeUcNexus(uc, wc);
        }
    }

    private void completeUcNexus(OmicronDirectorConfig.Node node, OmicronWarCapacity wc) {
        CampaignFleetAPI old = findNexus(node);
        if (old == null || !old.isAlive()) return;
        CampaignFleetAPI completed = OmicronStationBuilder.buildFromSpec(node.id, "completed_nexus");
        if (completed == null) return;
        completed.addTag(NEXUS_TAG);
        wc.setBuilt(node.id, "nexus", true);
        wc.setLatticeCompleted(true);

        int retired = 0;
        List scripts = Global.getSector().getScripts();
        for (int i = 0; i < scripts.size(); i++) {
            Object script = scripts.get(i);
            if (!(script instanceof OmicronDefenseFleetManager)) continue;
            OmicronDefenseFleetManager manager = (OmicronDefenseFleetManager) script;
            if (manager.getSource() != old) continue;
            // OMI-11: retire its guards/patrols too -- they would otherwise keep million-day
            // orders on the entity removed below. The new manager spawns its own.
            retired += manager.retireFleets();
            manager.setDone(true);
        }
        old.setExpired(true);
        if (old.getContainingLocation() != null) old.getContainingLocation().removeEntity(old);
        // OMI-11: point the node's launch proxy at the completed Nexus now, not at next launch.
        if (nodeSourceMarkets.get(node.id) != null) sourceMarketFor(node, completed);
        if (retired > 0) {
            Global.getLogger(OmicronDirector.class).info(
                    "Omicron Lattice completion: retired " + retired + " UC-Nexus defense fleet(s)");
        }

        OmicronDirectorConfig.DefenseTier tier = config.defenseTier("nexus");
        if (tier != null && (tier.maxFleets > 0 || tier.patrolFleets > 0)) {
            Global.getSector().addScript(new OmicronDefenseFleetManager(completed, "nexus", tier));
        }
        Global.getLogger(OmicronDirector.class).info(
                "Omicron Lattice construction COMPLETE: full Nexus online at day-progress "
                        + wc.getGrowthProgress());
    }

    /**
     * The ratified 120d station rebuild: a destroyed station respawns rebuildDays after its
     * death IF the node's Nexus still lives; Nexus death during the window CANCELS the
     * pending rebuild (the station stays dead — decapitation is the only permanent kill).
     * Deadlines are armed by scanAssets on death detection.
     */
    private void processRebuilds(OmicronDirectorConfig.Node node, OmicronWarCapacity wc,
                                 boolean nexusAlive) {
        for (int i = 0; i < config.assets.size(); i++) {
            OmicronDirectorConfig.Asset asset = (OmicronDirectorConfig.Asset) config.assets.get(i);
            if (asset.nodeId != null && !node.id.equals(asset.nodeId)) continue;
            if (asset.rebuildDays <= 0f) continue;
            Long ts = wc.getRebuildTs(node.id, asset.key);
            if (ts == null) continue;
            if (!nexusAlive) {
                wc.clearRebuildTs(node.id, asset.key);
                Global.getLogger(OmicronDirector.class).info(
                        "Omicron rebuild CANCELLED (Nexus dead): " + asset.key + " @ node " + node.id);
                continue;
            }
            // Sabotage-flag edge: if a living tagged fleet exists (destroyed-by-flag only),
            // there is nothing to physically rebuild — the override flag governs.
            StarSystemAPI system = Global.getSector().getStarSystem(node.system);
            if (system != null && hasLivingTagged(system, asset.tag)) {
                wc.clearRebuildTs(node.id, asset.key);
                continue;
            }
            float elapsed = Global.getSector().getClock().getElapsedDaysSince(ts.longValue());
            if (elapsed < asset.rebuildDays) continue;
            CampaignFleetAPI fleet = spawnStation(node, asset.key, wc);
            wc.clearRebuildTs(node.id, asset.key);
            if (fleet == null) continue;
            announceRebuild(node, asset);
        }
    }

    /** Spawn a station from its persisted spec: asset tag + immediate home-defense manager. */
    private CampaignFleetAPI spawnStation(OmicronDirectorConfig.Node node, String assetKey,
                                          OmicronWarCapacity wc) {
        CampaignFleetAPI fleet = OmicronStationBuilder.buildFromSpec(node.id, assetKey);
        if (fleet == null) return null;
        OmicronDirectorConfig.Asset asset = config.assetByKey(assetKey);
        if (asset != null) {
            fleet.addTag(asset.tag);
            if ("mining".equals(asset.type)) fleet.addTag(OmicronDirectorConfig.ASSET_TAG_MINING);
            if ("fuel".equals(asset.type)) fleet.addTag(OmicronDirectorConfig.ASSET_TAG_FUEL);
            if ("ucnexus".equals(asset.type)) fleet.addTag(OmicronDirectorConfig.ASSET_TAG_UCNEXUS);
        }
        wc.setBuilt(node.id, assetKey, true);
        // Defense managers normally attach at game load (ensureManagers); a mid-campaign
        // spawn gets its guard immediately.
        OmicronDirectorConfig.DefenseTier tier = config.defenseTier(assetKey);
        if (tier != null && (tier.maxFleets > 0 || tier.patrolFleets > 0)) {
            String tierKey = asset != null ? asset.type : assetKey;
            Global.getSector().addScript(new OmicronDefenseFleetManager(fleet, tierKey, tier));
        }
        return fleet;
    }

    /** Legibility ping (part of the rebuild ruling): relentlessness, not rubber-banding. */
    private void announceRebuild(OmicronDirectorConfig.Node node, OmicronDirectorConfig.Asset asset) {
        String sysName = node.system;
        StarSystemAPI system = Global.getSector().getStarSystem(node.system);
        if (system != null) sysName = system.getBaseName();
        String what;
        if ("mining".equals(asset.type)) {
            what = "ore refinery";
        } else if ("fuel".equals(asset.type)) {
            what = "fuel refinery";
        } else {
            what = "facility";
        }
        Global.getSector().getCampaignUI().addMessage(
                "Omicron has rebuilt its " + what + " in the " + sysName
                        + " system. Its strategic network is recovering.",
                Color.YELLOW);
        Global.getLogger(OmicronDirector.class).info(
                "Omicron asset REBUILT: " + asset.key + " @ node " + node.id);
    }

    /** Retained legacy campaign-age escalation seam; ordinary operation FP does not use it. */
    private float computeRampMult() {
        if (config.escalationCap <= 0f) return 1f;
        Object v = Global.getSector().getPersistentData().get(START_TS_KEY);
        long startTs;
        if (v instanceof Long) {
            startTs = ((Long) v).longValue();
        } else {
            startTs = Global.getSector().getClock().getTimestamp();
            Global.getSector().getPersistentData().put(START_TS_KEY, Long.valueOf(startTs));
        }
        float bonus = Global.getSector().getClock().getElapsedDaysSince(startTs) / config.escalationRampDays;
        if (bonus < 0f) bonus = 0f;
        if (bonus > config.escalationCap) bonus = config.escalationCap;
        return 1f + bonus;
    }

    /** Once-per-band-crossing legibility messages off the mean of the config-fed axes. */
    private void checkThresholds(OmicronWarCapacity wc) {
        float sum = 0f;
        int count = 0;
        for (int n = 0; n < config.nodes.size(); n++) {
            OmicronDirectorConfig.Node node = (OmicronDirectorConfig.Node) config.nodes.get(n);
            for (int a = 0; a < config.capacityAxes.size(); a++) {
                String statId = (String) config.capacityAxes.get(a);
                sum += OmicronWarCapacity.getValue(node.id, statId);
                count++;
            }
        }
        if (count == 0) return;
        float index = sum / count;

        int band = 0;
        for (int i = 0; i < config.thresholds.length; i++) {
            if (index < config.thresholds[i]) band = i + 1;
        }
        int last = wc.getLastBand();
        if (band == last) return;
        wc.setLastBand(band);

        String text;
        if (band > last) {
            if (band >= 3) {
                text = "Omicron raiding activity has all but collapsed.";
            } else if (band == 2) {
                text = "Omicron raiding activity is significantly weakened.";
            } else {
                text = "Omicron raiding activity appears diminished.";
            }
        } else {
            text = "Omicron activity is intensifying.";
        }
        Global.getSector().getCampaignUI().addMessage(text, Color.YELLOW);
        Global.getLogger(OmicronDirector.class).info(
                "Omicron war-capacity band " + last + " -> " + band + " (index " + index + ")");
    }

    // --- Targeting ---

    /** Resolve the living, tagged Nexus fleet in one node's system, or null (= decapitated node). */
    private CampaignFleetAPI findNexus(OmicronDirectorConfig.Node node) {
        StarSystemAPI system = Global.getSector().getStarSystem(node.system);
        if (system == null) return null;
        List fleets = system.getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI f = (CampaignFleetAPI) fleets.get(i);
            if (f != null && f.isAlive() && f.hasTag(NEXUS_TAG)) return f;
        }
        return null;
    }

    /** The data-driven target-faction list (defaults from config); quests/events may extend it. */
    private List getTargetFactionIds() {
        Object v = Global.getSector().getPersistentData().get(TARGET_FACTIONS_KEY);
        if (v instanceof List) return (List) v;
        List defaults = new ArrayList(config.defaultTargetFactions);
        Global.getSector().getPersistentData().put(TARGET_FACTIONS_KEY, defaults);
        return defaults;
    }

    /** Phase-2 hook: add a faction id to Omicron's attack list at runtime (no Director handle needed). */
    public static void addTargetFaction(String factionId) {
        if (factionId == null || Global.getSector() == null) return;
        Object v = Global.getSector().getPersistentData().get(TARGET_FACTIONS_KEY);
        List list;
        if (v instanceof List) {
            list = (List) v;
        } else {
            list = new ArrayList();
            list.add(Factions.LUDDIC_CHURCH);
            list.add(Factions.LUDDIC_PATH);
            Global.getSector().getPersistentData().put(TARGET_FACTIONS_KEY, list);
        }
        if (!list.contains(factionId)) list.add(factionId);
    }

    private LaunchSearch findLaunchChoices(boolean incursion, float spawnFP) {
        LaunchSearch result = new LaunchSearch();
        FactionAPI omicron = Global.getSector().getFaction(OMICRON_FACTION);
        List targetFactions = getTargetFactionIds();
        List markets = Global.getSector().getEconomy().getMarketsCopy();
        List busySystems = new ArrayList(); // StarSystemAPI
        boolean unitsLogged = false; // sec 14.14 Step 1a measurement: one line per search
        List liveOps = Global.getSector().getIntelManager().getIntel(OmicronRaidIntel.class);
        for (int i = 0; i < liveOps.size(); i++) {
            OmicronRaidIntel op = (OmicronRaidIntel) liveOps.get(i);
            if (op.isEnded() || op.isEnding()) continue;
            if (op.getSystem() != null) busySystems.add(op.getSystem());
        }
        for (int n = 0; n < config.nodes.size(); n++) {
            OmicronDirectorConfig.Node node = (OmicronDirectorConfig.Node) config.nodes.get(n);
            if (!maySource(node, incursion) || !isSourceFree(node.id)) continue;
            CampaignFleetAPI nexus = findNexus(node);
            if (nexus == null || !nexus.isAlive()) continue;
            result.hadFreeSource = true;

            // UNITS MEASUREMENT (Heg-League doc sec 14.14 Step 1a) -- LOGGING ONLY, the gate below
            // is unchanged. It compares raw spawnFP against WarSimScript EFFECTIVE STRENGTH;
            // vanilla converts with Misc.getAdjustedStrength(fp, source), which
            // OmicronAssembleStage already calls on this node's proxy market. The proxy is READ,
            // never created here, so a node that has not launched yet stays untouched (no new
            // saved market). One line per search -- the first free node with a proxy -- since
            // every proxy is pinned the same way and a search repeats at most once a day.
            if (!unitsLogged && spawnFP > 0f) {
                MarketAPI proxy = (MarketAPI) nodeSourceMarkets.get(node.id);
                if (proxy != null) {
                    unitsLogged = true;
                    float adjusted = Misc.getAdjustedStrength(spawnFP, proxy);
                    float k = adjusted / spawnFP;
                    float mult = config.feasibilityMult;
                    Global.getLogger(OmicronDirector.class).info(
                            "Exiles units [Omicron " + (incursion ? "incursion" : "raid")
                                    + "]: source=node " + node.id
                                    + " fp=" + Math.round(spawnFP)
                                    + " adjustedStrength=" + Math.round(adjusted)
                                    + " k=" + (Math.round(k * 100f) / 100f)
                                    + " feasibilityMult=" + mult
                                    + (mult > 0f ? " requiredOdds="
                                            + (Math.round(k / mult * 100f) / 100f) + ":1"
                                            : " (gate disabled)"));
                }
            }

            List candidates = new ArrayList();
            LocationAPI home = nexus.getContainingLocation();
            for (int i = 0; i < markets.size(); i++) {
                MarketAPI m = (MarketAPI) markets.get(i);
                if (m == null || m.isPlayerOwned() || m.getPrimaryEntity() == null) continue;
                if (m.getContainingLocation() == null || m.getContainingLocation() == home) continue;
                if (m.isInHyperspace() || m.isHidden() || m.isPlanetConditionMarketOnly()) continue;
                if (!m.isInEconomy() || !targetFactions.contains(m.getFactionId())) continue;
                if (omicron != null && m.getFaction() != null
                        && !omicron.isHostileTo(m.getFaction())) continue;
                result.hadStrategicEnemy = true;
                if (Misc.flagHasReason(m.getMemoryWithoutUpdate(),
                        MemFlags.RECENTLY_RAIDED, OMICRON_FACTION)
                        || busySystems.contains(m.getContainingLocation())) {
                    result.hadTemporaryBlock = true;
                    continue;
                }
                candidates.add(m);
            }
            applyReachFilter(node, nexus, candidates);
            HashMap systemStrength = new HashMap();
            for (int i = 0; i < candidates.size(); i++) {
                MarketAPI m = (MarketAPI) candidates.get(i);
                LocationAPI loc = m.getContainingLocation();
                if (!(loc instanceof StarSystemAPI)) continue;
                StarSystemAPI system = (StarSystemAPI) loc;
                float defense = 0f;
                if (config.feasibilityMult > 0f && omicron != null) {
                    Float cached = (Float) systemStrength.get(system);
                    if (cached == null) {
                        cached = Float.valueOf(WarSimScript.getEnemyStrength(omicron, system));
                        systemStrength.put(system, cached);
                    }
                    defense = cached.floatValue()
                            + ExileStationStrength.get(
                            m.getFaction(), system, m.getPrimaryEntity());
                    if (defense > spawnFP * config.feasibilityMult) continue;
                }
                result.choices.add(new LaunchChoice(node, nexus, m));
            }
        }
        return result;
    }

    private void applyReachFilter(OmicronDirectorConfig.Node node, CampaignFleetAPI nexus,
                                  List candidates) {
        float reach = OmicronWarCapacity.getValue(node.id, OmicronWarCapacity.STAT_REACH);
        int n = candidates.size();
        if (reach >= 0.999f || n <= 1) return;
        int keep = (int) Math.ceil(reach * n);
        if (keep < 1) keep = 1;
        if (keep >= n) return;
        Vector2f homeLoc = nexus.getLocationInHyperspace();
        float[] dists = new float[n];
        float[] sorted = new float[n];
        for (int i = 0; i < n; i++) {
            MarketAPI m = (MarketAPI) candidates.get(i);
            dists[i] = Misc.getDistance(
                    homeLoc, m.getPrimaryEntity().getLocationInHyperspace());
            sorted[i] = dists[i];
        }
        Arrays.sort(sorted);
        float cutoff = sorted[keep - 1];
        for (int i = candidates.size() - 1; i >= 0; i--) {
            if (dists[i] > cutoff) candidates.remove(i);
        }
    }

    private static class LaunchSearch {
        ArrayList choices = new ArrayList();
        boolean hadFreeSource;
        boolean hadStrategicEnemy;
        boolean hadTemporaryBlock;
    }

    private static class LaunchChoice {
        OmicronDirectorConfig.Node node;
        CampaignFleetAPI nexus;
        MarketAPI target;

        LaunchChoice(OmicronDirectorConfig.Node node, CampaignFleetAPI nexus, MarketAPI target) {
            this.node = node;
            this.nexus = nexus;
            this.target = target;
        }
    }

    /** @deprecated retained only so old dev saves that serialized one can deserialize. */
    private static class PendingIncursion {
        long launchTs;
        MarketAPI target;

        PendingIncursion(long launchTs, MarketAPI target) {
            this.launchTs = launchTs;
            this.target = target;
        }
    }
}
