package data.scripts.exiles;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * The Omicron Initiative's Tri-Tachyon protection mandate.
 *
 * <p>Each explicitly configured market has one small Omicron-faction fleet physically present
 * at its orbital station. In peacetime it is hidden, non-aggressive, ignored by ordinary NPC
 * fleets, and cares nothing about transponders: mechanically dormant, matching the established
 * hull-latching fiction. Strategic operations do not reveal these point-defense fleets merely
 * by targeting their system: an operation fleet must close on the assigned station first.
 *
 * <p>Culann separately houses one large, persistent strategic reserve. It wakes for
 * Black-Fleet-Assault-scale threats anywhere in Hybrasil. It is not an Omicron Director node,
 * never launches attacks, and falls back to the same Nexus-alive replacement rule as the
 * station complements. If it is unavailable, a large forewarned threat may still draw one
 * long-range response from a mature Nexus when travel time permits.
 *
 * <p>Faction relationships are never changed. The defense-only ROE is fleet-scoped: all
 * factions except the identified attacker are forced non-hostile for that fleet. This lets an
 * Omicron defender support Tri-Tachyon without deciding who may trade or land at its markets.
 */
public class OmicronTriTachyonDefenseManager implements EveryFrameScript, FleetEventListener {

    public static final String LOCAL_DEFENDER_TAG = "exiles_omicron_tt_local_defender";
    public static final String STRATEGIC_RESERVE_TAG = "exiles_omicron_tt_strategic_reserve";
    public static final String RESPONSE_TAG = "exiles_omicron_tt_response";
    public static final String MARKET_ID_KEY = "$exiles_omicron_tt_market";
    public static final String NODE_ID_KEY = "$exiles_omicron_tt_node";
    public static final String ACTIVE_KEY = "$exiles_omicron_tt_active";
    public static final String THREAT_FACTION_KEY = "$exiles_omicron_tt_threat_faction";
    public static final String ALERT_KEY = "$exiles_omicron_tt_defense_alert";
    public static final String ALERT_FACTION_KEY = "$exiles_omicron_tt_alert_faction";
    public static final String REPLACEMENT_COOLDOWN_KEY = "$exiles_omicron_tt_replacement_cooldown";
    public static final String RESERVE_ALERT_KEY = "$exiles_omicron_tt_reserve_alert";
    public static final String RESERVE_FACTION_KEY = "$exiles_omicron_tt_reserve_faction";
    public static final String RESERVE_ROUTE_KEY = "$exiles_omicron_tt_reserve_route";
    public static final String RESERVE_READY_KEY = "$exiles_omicron_tt_reserve_ready";
    public static final String RESERVE_THRESHOLD_KEY = "$exiles_omicron_tt_reserve_threshold";
    /** FP of the threat the detachment is currently assigned to; see {@link #tryArmStrategicReserve}. */
    public static final String RESERVE_THREAT_FP_KEY = "$exiles_omicron_tt_reserve_threat_fp";
    public static final String RESERVE_COOLDOWN_KEY = "$exiles_omicron_tt_reserve_cooldown";

    protected transient OmicronDirectorConfig config;
    /** SAVED. Baseline for the {@code spawnAfterDays} umbrella fallback; set on first use so it
     *  also behaves sanely when this feature is introduced into an existing campaign. */
    protected long umbrellaStartTs = 0L;
    protected IntervalUtil scan = new IntervalUtil(0.2f, 0.3f);
    /** Threat key -> timestamp at which a strategic reserve/Nexus response was committed. */
    protected HashMap handledThreats = new HashMap();
    protected Random random = new Random();

    public OmicronTriTachyonDefenseManager() {
        reloadConfig();
    }

    public void reloadConfig() {
        config = OmicronDirectorConfig.load();
        float days = config.ttDefenseScanIntervalDays;
        if (days < 0.05f) days = 0.05f;
        scan.setInterval(days, days);
    }

    /** Save-compatible registration and config refresh, called on every game load. */
    public static OmicronTriTachyonDefenseManager ensure() {
        if (Global.getSector() == null) return null;
        List scripts = Global.getSector().getScripts();
        for (int i = 0; i < scripts.size(); i++) {
            Object script = scripts.get(i);
            if (script instanceof OmicronTriTachyonDefenseManager) {
                OmicronTriTachyonDefenseManager manager =
                        (OmicronTriTachyonDefenseManager) script;
                manager.reloadConfig();
                manager.adoptExistingDefenders();
                return manager;
            }
        }
        OmicronTriTachyonDefenseManager manager = new OmicronTriTachyonDefenseManager();
        Global.getSector().addScript(manager);
        manager.adoptExistingDefenders();
        return manager;
    }

    /** Generic hook for future Ashen/quest operations that know their target before a
     *  RaidIntel exists. Repeated calls with the same key refresh the local alert but launch
     *  at most one Nexus response. */
    public static void requestDefense(String threatKey, StarSystemAPI targetSystem,
                                      String threatFactionId, float threatFp, float etaDays) {
        OmicronTriTachyonDefenseManager manager = ensure();
        if (manager != null) {
            manager.handleThreat(threatKey, targetSystem, threatFactionId, threatFp, etaDays);
        }
    }

    public void advance(float amount) {
        if (Global.getSector() == null || Global.getSector().isPaused()) return;
        if (config == null) reloadConfig();
        if (!config.ttDefenseEnabled) return;

        scan.advance(Misc.getDays(amount));
        if (!scan.intervalElapsed()) return;

        ensureLocalDefenders();
        ensureStrategicReserve();
        scanRaidIntel();
        updateLocalPostures();
        updateStrategicReservePosture();
        pruneHandledThreats();
    }

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }

    /** Vanilla and modded RaidIntel share enough public API for a conservative detector:
     *  if the raider is hostile to Tri-Tachyon and its target system contains a protected
     *  market, that market is at risk. This is exact for vanilla multi-target pirate raids
     *  and Black Fleet commerce/multi-sack operations. */
    protected void scanRaidIntel() {
        FactionAPI triTachyon = Global.getSector().getFaction(Factions.TRITACHYON);
        if (triTachyon == null) return;

        List raids = Global.getSector().getIntelManager().getIntel(RaidIntel.class);
        for (int i = 0; i < raids.size(); i++) {
            RaidIntel raid = (RaidIntel) raids.get(i);
            if (raid == null || raid.isEnded() || raid.isEnding() || raid.isFailed()) continue;
            if (raid.getSystem() == null || raid.getFaction() == null) continue;
            if (!raid.getFaction().isHostileTo(triTachyon)) continue;

            float fp = 0f;
            AssembleStage assemble = raid.getAssembleStage();
            if (assemble != null) fp = assemble.getOrigSpawnFP();
            handleThreat(raid.getRouteSourceId(), raid.getSystem(), raid.getFaction().getId(),
                    fp, raid.getETA());
            activateImminentStationDefenders(raid);
        }
    }

    protected void handleThreat(String threatKey, StarSystemAPI targetSystem,
                                String threatFactionId, float threatFp, float etaDays) {
        if (targetSystem == null || threatFactionId == null) return;

        List specs = protectedSpecsIn(targetSystem);
        if (specs.isEmpty()) return;

        OmicronDirectorConfig.ProtectedMarket responseTarget = null;
        MarketAPI responseMarket = null;
        float responseThreshold = Float.MAX_VALUE;

        for (int i = 0; i < specs.size(); i++) {
            OmicronDirectorConfig.ProtectedMarket spec =
                    (OmicronDirectorConfig.ProtectedMarket) specs.get(i);
            MarketAPI market = getProtectedMarket(spec);
            if (market == null) continue;

            if (spec.responseMinThreatFp < responseThreshold
                    || (spec.primary && responseTarget != null && !responseTarget.primary)) {
                responseTarget = spec;
                responseMarket = market;
                responseThreshold = spec.responseMinThreatFp;
            }
        }

        if (responseMarket == null || threatFp < responseThreshold) return;
        if (threatKey == null) threatKey = "external_" + targetSystem.getId() + "_" + threatFactionId;
        if (tryArmStrategicReserve(threatKey, targetSystem, threatFactionId,
                threatFp, etaDays)) {
            handledThreats.put(threatKey,
                    Long.valueOf(Global.getSector().getClock().getTimestamp()));
            return;
        }
        if (handledThreats.containsKey(threatKey)) return;

        if (dispatchResponse(threatKey, responseMarket, threatFactionId, threatFp, etaDays)) {
            handledThreats.put(threatKey,
                    Long.valueOf(Global.getSector().getClock().getTimestamp()));
        }
    }

    /**
     * A station sleeper wakes only when a fleet belonging to the actual strategic operation
     * closes on its assigned station. Looking up the operation's RouteData avoids reacting to
     * unrelated same-faction pirates the player may be farming elsewhere in-system.
     */
    protected void activateImminentStationDefenders(RaidIntel raid) {
        if (raid == null || raid.getSystem() == null || raid.getFaction() == null) return;
        String routeSource = raid.getRouteSourceId();
        if (routeSource == null) return;

        List specs = protectedSpecsIn(raid.getSystem());
        for (int i = 0; i < specs.size(); i++) {
            OmicronDirectorConfig.ProtectedMarket spec =
                    (OmicronDirectorConfig.ProtectedMarket) specs.get(i);
            MarketAPI market = getProtectedMarket(spec);
            if (market == null) continue;
            SectorEntityToken anchor = findStationAnchor(market);
            if (anchor == null) continue;

            CampaignFleetAPI threat = findNearestRouteFleet(routeSource, anchor);
            if (threat == null || Misc.getDistance(threat, anchor)
                    > config.ttDefenseStationActivationRange) continue;

            market.getMemoryWithoutUpdate().set(
                    ALERT_KEY, true, config.ttDefenseStandDownDays);
            market.getMemoryWithoutUpdate().set(
                    ALERT_FACTION_KEY, raid.getFaction().getId(),
                    config.ttDefenseStandDownDays);
        }
    }

    protected List protectedSpecsIn(StarSystemAPI system) {
        java.util.ArrayList result = new java.util.ArrayList();
        for (int i = 0; i < config.ttProtectedMarkets.size(); i++) {
            OmicronDirectorConfig.ProtectedMarket spec =
                    (OmicronDirectorConfig.ProtectedMarket) config.ttProtectedMarkets.get(i);
            MarketAPI market = getProtectedMarket(spec);
            if (market != null && market.getContainingLocation() == system) result.add(spec);
        }
        return result;
    }

    protected MarketAPI getProtectedMarket(OmicronDirectorConfig.ProtectedMarket spec) {
        if (spec == null || spec.id == null) return null;
        MarketAPI market = Global.getSector().getEconomy().getMarket(spec.id);
        if (market == null || !Factions.TRITACHYON.equals(market.getFactionId())) return null;
        if (!market.isInEconomy() || market.isHidden()) return null;
        return market;
    }

    protected void ensureLocalDefenders() {
        for (int i = 0; i < config.ttProtectedMarkets.size(); i++) {
            OmicronDirectorConfig.ProtectedMarket spec =
                    (OmicronDirectorConfig.ProtectedMarket) config.ttProtectedMarkets.get(i);
            MarketAPI market = getProtectedMarket(spec);
            if (market == null) continue;

            CampaignFleetAPI existing = findLocalDefender(spec.id);
            if (existing != null) {
                adoptDefender(existing);
                continue;
            }
            if (market.getMemoryWithoutUpdate().getBoolean(REPLACEMENT_COOLDOWN_KEY)) continue;
            if (!hasLivingMatureNexus()) continue;
            if (!isUmbrellaOpen(spec)) continue;
            spawnLocalDefender(market, spec);
        }
    }

    /**
     * Has Omicron's protective umbrella reached this market yet? (Ratified 2026-08-02.)
     *
     * <p>The outposts at Port Tse and Tibicena start UNCOVERED and gain their complements on a
     * dedicated Omicron-corporate-mandate clock. It is not keyed to another faction's schedule,
     * combat maturity, or Lattice construction:
     *
     * <ul>
     * <li>The mandate is not about any one enemy. Gating it on, say, the Ashen war clock would
     *     leave Port Tse uncovered against a Black Fleet assault for reasons that have nothing
     *     to do with the Black Fleet — and would break entirely if that war were disabled.</li>
     * <li>Lattice damage therefore cannot accidentally leave Tri-Tachyon markets permanently
     *     softer prey for unrelated attackers.</li>
     * </ul>
     *
     * <p>{@code spawnAfterDays} remains a separate absolute fallback for configuration safety.
     *
     * <p>Markets that set neither field (Culann, Eochu Bres) are covered from campaign start,
     * exactly as before.
     */
    protected boolean isUmbrellaOpen(OmicronDirectorConfig.ProtectedMarket spec) {
        if (spec.spawnAtGrowth <= 0f) return true;
        if (umbrellaStartTs == 0L) {
            umbrellaStartTs = Global.getSector().getClock().getTimestamp();
            return false;
        }
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(umbrellaStartTs);
        if (config.ttUmbrellaRampDays > 0f
                && elapsed / config.ttUmbrellaRampDays >= spec.spawnAtGrowth) return true;
        if (spec.spawnAfterDays > 0f && elapsed >= spec.spawnAfterDays) return true;
        return false;
    }

    protected void ensureStrategicReserve() {
        MarketAPI market = getStrategicReserveMarket();
        if (market == null) return;
        CampaignFleetAPI existing = findStrategicReserve();
        if (existing != null) {
            adoptDefender(existing);
            existing.getMemoryWithoutUpdate().set(
                    RESERVE_THRESHOLD_KEY, config.ttDefenseReserveMinThreatFp);
            return;
        }
        if (market.getMemoryWithoutUpdate().getBoolean(RESERVE_COOLDOWN_KEY)) return;
        if (!hasLivingMatureNexus()) return;
        spawnStrategicReserve(market);
    }

    protected CampaignFleetAPI spawnLocalDefender(MarketAPI market,
            OmicronDirectorConfig.ProtectedMarket spec) {
        SectorEntityToken anchor = findStationAnchor(market);
        if (anchor == null || anchor.getContainingLocation() == null) return null;

        int fp = spec.localFpMin;
        if (spec.localFpMax > spec.localFpMin) {
            fp += random.nextInt(spec.localFpMax - spec.localFpMin + 1);
        }
        CampaignFleetAPI fleet = createOmicronFleet(market, fp, 3, random);
        if (fleet == null) return null;

        anchor.getContainingLocation().addEntity(fleet);
        fleet.setLocation(anchor.getLocation().x, anchor.getLocation().y);
        fleet.setName("Station Reserve");
        fleet.addTag(LOCAL_DEFENDER_TAG);
        fleet.getMemoryWithoutUpdate().set(MARKET_ID_KEY, market.getId());
        fleet.setNoAutoDespawn(true);
        fleet.setAIMode(true);
        fleet.addEventListener(this);
        configureDormant(fleet, anchor);

        Global.getLogger(OmicronTriTachyonDefenseManager.class).info(
                "Omicron dormant complement attached: " + market.getName() + " (" + fp + " FP)");
        return fleet;
    }

    protected CampaignFleetAPI spawnStrategicReserve(MarketAPI market) {
        SectorEntityToken anchor = findStationAnchor(market);
        if (anchor == null || anchor.getContainingLocation() == null) return null;

        int fp = config.ttDefenseReserveFpMin;
        if (config.ttDefenseReserveFpMax > config.ttDefenseReserveFpMin) {
            fp += random.nextInt(
                    config.ttDefenseReserveFpMax - config.ttDefenseReserveFpMin + 1);
        }
        CampaignFleetAPI fleet = createOmicronFleet(market, fp, 4, random);
        if (fleet == null) return null;

        anchor.getContainingLocation().addEntity(fleet);
        fleet.setLocation(anchor.getLocation().x, anchor.getLocation().y);
        fleet.setName("Strategic Detachment");
        fleet.addTag(STRATEGIC_RESERVE_TAG);
        fleet.getMemoryWithoutUpdate().set(MARKET_ID_KEY, market.getId());
        fleet.getMemoryWithoutUpdate().set(
                RESERVE_THRESHOLD_KEY, config.ttDefenseReserveMinThreatFp);
        fleet.setNoAutoDespawn(true);
        fleet.setAIMode(true);
        fleet.addEventListener(this);
        configureDormant(fleet, anchor);

        Global.getLogger(OmicronTriTachyonDefenseManager.class).info(
                "Omicron strategic detachment stationed at " + market.getName()
                        + " (" + fp + " FP)");
        return fleet;
    }

    protected static CampaignFleetAPI createOmicronFleet(MarketAPI sourceMarket, int fp,
                                                           int maxShipSize, Random random) {
        String type = fp <= 45 ? FleetTypes.PATROL_SMALL
                : (fp <= 90 ? FleetTypes.PATROL_MEDIUM : FleetTypes.PATROL_LARGE);
        FleetParamsV3 params = new FleetParamsV3(
                sourceMarket,
                sourceMarket.getLocationInHyperspace(),
                OmicronDirector.OMICRON_FACTION,
                1f,
                type,
                fp,
                0f, 0f, 0f, 0f, 0f,
                0f);
        params.random = random;
        params.ignoreMarketFleetSizeMult = Boolean.TRUE;
        params.maxShipSize = maxShipSize;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;
        RemnantSeededFleetManager.addRemnantInteractionConfig(fleet);
        fleet.getMemoryWithoutUpdate().set(MemFlags.SALVAGE_SEED, Long.valueOf(random.nextLong()));
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        return fleet;
    }

    protected void updateLocalPostures() {
        for (int i = 0; i < config.ttProtectedMarkets.size(); i++) {
            OmicronDirectorConfig.ProtectedMarket spec =
                    (OmicronDirectorConfig.ProtectedMarket) config.ttProtectedMarkets.get(i);
            MarketAPI market = getProtectedMarket(spec);
            if (market == null) continue;
            CampaignFleetAPI fleet = findLocalDefender(spec.id);
            if (fleet == null) continue;

            boolean alert = market.getMemoryWithoutUpdate().getBoolean(ALERT_KEY);
            String threatFactionId = market.getMemoryWithoutUpdate().getString(ALERT_FACTION_KEY);
            boolean active = fleet.getMemoryWithoutUpdate().getBoolean(ACTIVE_KEY);
            String oldThreat = fleet.getMemoryWithoutUpdate().getString(THREAT_FACTION_KEY);

            SectorEntityToken anchor = findStationAnchor(market);
            if (alert && (!active || !same(oldThreat, threatFactionId))) {
                configureActive(fleet, anchor, threatFactionId, market.getName());
            } else if (!alert && active) {
                configureDormant(fleet, anchor);
            }
            if (alert && fleet.getMemoryWithoutUpdate().getBoolean(ACTIVE_KEY)) {
                assignThreatOrHold(fleet, anchor, threatFactionId,
                        "holding at " + market.getName());
            }
        }
    }

    protected void updateStrategicReservePosture() {
        MarketAPI home = getStrategicReserveMarket();
        CampaignFleetAPI fleet = findStrategicReserve();
        if (home == null || fleet == null) return;
        SectorEntityToken anchor = findStationAnchor(home);
        if (anchor == null) return;

        boolean alert = home.getMemoryWithoutUpdate().getBoolean(RESERVE_ALERT_KEY);
        boolean ready = home.getMemoryWithoutUpdate().getBoolean(RESERVE_READY_KEY);
        boolean active = fleet.getMemoryWithoutUpdate().getBoolean(ACTIVE_KEY);
        String threatFactionId =
                home.getMemoryWithoutUpdate().getString(RESERVE_FACTION_KEY);
        String routeSource = home.getMemoryWithoutUpdate().getString(RESERVE_ROUTE_KEY);
        CampaignFleetAPI routeThreat = findNearestRouteFleet(routeSource, fleet);

        if (alert && !active && (ready || routeThreat != null)) {
            configureStrategicReserveActive(
                    fleet, anchor, threatFactionId, routeSource, home.getName());
            active = true;
        } else if (!alert && active) {
            configureDormant(fleet, anchor);
            active = false;
        }

        if (alert && active) {
            assignRouteThreatOrHold(fleet, anchor, routeSource, threatFactionId,
                    "holding strategic reserve at " + home.getName());
        }
    }

    protected boolean tryArmStrategicReserve(String threatKey, StarSystemAPI targetSystem,
                                              String threatFactionId, float threatFp,
                                              float etaDays) {
        if (threatFp < config.ttDefenseReserveMinThreatFp) return false;
        MarketAPI home = getStrategicReserveMarket();
        CampaignFleetAPI fleet = findStrategicReserve();
        if (home == null || fleet == null || home.getContainingLocation() != targetSystem) {
            return false;
        }

        float alertDays = Math.max(config.ttDefenseStandDownDays, etaDays + 2f);

        // ONE DETACHMENT, ONE QUARRY -- and the BIGGEST threat wins (2026-08-16).
        //
        // This method is called once per THREAT, and a single attacker can field several
        // simultaneous operations against one system: the Ashen Phase-4 strike registers its
        // assault and its screening armada as two, and a Black Fleet assault could do the same.
        // Every call used to overwrite RESERVE_ROUTE_KEY, so the detachment ended up bound to
        // whichever operation happened to arm LAST, arbitrarily, with the other invisible to it.
        //
        // That key is not decorative: findNearestRouteFleet() and assignRouteThreatOrHold() both
        // read it, so it decides which operation's route fleets the detachment actually hunts.
        // Sean's live observation confirmed the deployment itself was always correct -- three
        // Omicron fleets, two station complements and one detachment -- so this was never a
        // duplication bug, only an arbitrary choice of target.
        //
        // Keeping the largest is the deterministic reading of "the detachment answers the threat":
        // it commits against the main assault rather than a screen, every time.
        boolean alertLive = home.getMemoryWithoutUpdate().getBoolean(RESERVE_ALERT_KEY);
        String heldRoute = home.getMemoryWithoutUpdate().getString(RESERVE_ROUTE_KEY);
        String heldFaction = home.getMemoryWithoutUpdate().getString(RESERVE_FACTION_KEY);
        float heldFp = home.getMemoryWithoutUpdate().getFloat(RESERVE_THREAT_FP_KEY);

        boolean sameAlert = alertLive && heldRoute != null && same(heldFaction, threatFactionId);
        boolean takeOver = !sameAlert || same(heldRoute, threatKey) || threatFp > heldFp;

        home.getMemoryWithoutUpdate().set(RESERVE_ALERT_KEY, true, alertDays);
        home.getMemoryWithoutUpdate().set(
                RESERVE_FACTION_KEY, threatFactionId, alertDays);
        if (takeOver) {
            home.getMemoryWithoutUpdate().set(RESERVE_ROUTE_KEY, threatKey, alertDays);
            home.getMemoryWithoutUpdate().set(
                    RESERVE_THREAT_FP_KEY, new Float(threatFp), alertDays);
        } else {
            // Refresh the incumbent's expiry so a secondary op cannot let the alert lapse.
            home.getMemoryWithoutUpdate().set(RESERVE_ROUTE_KEY, heldRoute, alertDays);
            home.getMemoryWithoutUpdate().set(
                    RESERVE_THREAT_FP_KEY, new Float(heldFp), alertDays);
        }

        String activeRoute = takeOver ? threatKey : heldRoute;
        List routes = RouteManager.getInstance().getRoutesForSource(activeRoute);
        if (etaDays <= config.ttDefenseReserveActivationLeadDays || routes.isEmpty()) {
            home.getMemoryWithoutUpdate().set(RESERVE_READY_KEY, true, alertDays);
        }

        // Log the ALERT, not each threat that reports into it -- one arming, one line.
        if (takeOver && !handledThreats.containsKey(threatKey)) {
            Global.getLogger(OmicronTriTachyonDefenseManager.class).info(
                    "Omicron strategic detachment armed at " + home.getName()
                            + " against " + threatFactionId + ", threat=" + threatKey
                            + " (" + (int) threatFp + " FP)"
                            + (sameAlert ? "; superseded " + heldRoute
                                    + " (" + (int) heldFp + " FP)" : ""));
        }
        return true;
    }

    protected static boolean same(String one, String two) {
        return one == null ? two == null : one.equals(two);
    }

    protected void configureDormant(CampaignFleetAPI fleet, SectorEntityToken anchor) {
        if (fleet == null || anchor == null) return;
        fleet.clearAssignments();
        applyDefenseOnlyRoe(fleet, null, false);
        fleet.getMemoryWithoutUpdate().unset(ACTIVE_KEY);
        fleet.getMemoryWithoutUpdate().unset(THREAT_FACTION_KEY);
        fleet.setHidden(true);
        fleet.setTransponderOn(false);
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, anchor, 1000000f, "dormant");
    }

    protected void configureActive(CampaignFleetAPI fleet, SectorEntityToken anchor,
                                   String threatFactionId, String marketName) {
        if (fleet == null || anchor == null) return;
        fleet.clearAssignments();
        applyDefenseOnlyRoe(fleet, threatFactionId, true);
        fleet.getMemoryWithoutUpdate().set(ACTIVE_KEY, true);
        fleet.getMemoryWithoutUpdate().set(THREAT_FACTION_KEY, threatFactionId);
        fleet.setHidden(false);
        fleet.setTransponderOn(false);
        assignThreatOrHold(fleet, anchor, threatFactionId,
                "holding at " + marketName);
        Global.getLogger(OmicronTriTachyonDefenseManager.class).info(
                "Omicron complement activated at " + marketName + " against " + threatFactionId);
    }

    /**
     * Gives a defender an explicit target-faction intercept, or a passive hold if no such
     * fleet is currently in-system. Avoiding PATROL_SYSTEM/DEFEND_LOCATION is important:
     * those AIs also consider fleets that are hostile to Omicron, which can make a
     * defense-only fleet chase unrelated pirates.
     */
    public static void assignThreatOrHold(CampaignFleetAPI fleet, SectorEntityToken anchor,
                                          String threatFactionId, String holdText) {
        if (fleet == null || anchor == null) return;
        CampaignFleetAPI target = findNearestThreatFleet(fleet, threatFactionId);
        FleetAssignmentDataAPI current = fleet.getCurrentAssignment();

        if (target != null) {
            fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE);
            if (current == null || current.getAssignment() != FleetAssignment.INTERCEPT
                    || current.getTarget() != target) {
                fleet.clearAssignments();
                fleet.addAssignment(FleetAssignment.INTERCEPT, target, 1f,
                        "intercepting designated hostile forces");
            }
            return;
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE, true);
        if (current == null || current.getAssignment() != FleetAssignment.ORBIT_PASSIVE
                || current.getTarget() != anchor) {
            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, anchor, 2f, holdText);
        }
    }

    protected static void assignRouteThreatOrHold(CampaignFleetAPI fleet,
                                                   SectorEntityToken anchor,
                                                   String routeSource,
                                                   String threatFactionId,
                                                   String holdText) {
        CampaignFleetAPI target = findNearestRouteFleet(routeSource, fleet);
        if (target != null) {
            FleetAssignmentDataAPI current = fleet.getCurrentAssignment();
            fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE);
            if (current == null || current.getAssignment() != FleetAssignment.INTERCEPT
                    || current.getTarget() != target) {
                fleet.clearAssignments();
                fleet.addAssignment(FleetAssignment.INTERCEPT, target, 1f,
                        "intercepting designated hostile forces");
            }
            return;
        }
        assignThreatOrHold(fleet, anchor, threatFactionId, holdText);
    }

    protected static CampaignFleetAPI findNearestRouteFleet(String routeSource,
                                                             SectorEntityToken origin) {
        if (routeSource == null || origin == null || origin.getContainingLocation() == null) {
            return null;
        }
        CampaignFleetAPI best = null;
        float bestDistance = Float.MAX_VALUE;
        List routes = RouteManager.getInstance().getRoutesForSource(routeSource);
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null || !fleet.isAlive() || fleet.isEmpty()
                    || fleet.getContainingLocation() != origin.getContainingLocation()) continue;
            float distance = Misc.getDistance(origin, fleet);
            if (distance < bestDistance) {
                best = fleet;
                bestDistance = distance;
            }
        }
        return best;
    }

    protected static CampaignFleetAPI findNearestThreatFleet(CampaignFleetAPI defender,
                                                               String threatFactionId) {
        if (defender == null || threatFactionId == null
                || defender.getContainingLocation() == null) return null;
        CampaignFleetAPI best = null;
        float bestDistance = Float.MAX_VALUE;
        List fleets = defender.getContainingLocation().getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI other = (CampaignFleetAPI) fleets.get(i);
            if (other == null || other == defender || !other.isAlive() || other.isEmpty()
                    || other.isStationMode()) continue;
            if (!threatFactionId.equals(other.getFaction().getId())) continue;
            float distance = Misc.getDistance(defender, other);
            if (distance < bestDistance) {
                best = other;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Fleet-scoped diplomatic shell. The defender retains the real Omicron faction and
     *  reputation, but only the identified attacker is hostile for campaign AI purposes. */
    public static void applyDefenseOnlyRoe(CampaignFleetAPI fleet, String threatFactionId,
                                           boolean active) {
        if (fleet == null) return;
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF);
        mem.set(MemFlags.MEMORY_KEY_PATROL_ALLOW_TOFF, true);
        mem.set(MemFlags.NON_HOSTILE_OVERRIDES_MAKE_HOSTILE, true);
        mem.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        mem.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);

        // MAKE_NON_AGGRESSIVE: inert in the engine (jar string scan, 2026-10-06), kept as an Exiles
        // marker read by the refugee salvage threat screens. MAKE_NON_HOSTILE IS read by the
        // engine's campaign TacticalModule; whether only toward the player is not yet verified.
        if (active) {
            mem.unset(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE);
            mem.unset(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE);
        } else {
            mem.set(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE, true);
            mem.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        }

        List factions = Global.getSector().getAllFactions();
        for (int i = 0; i < factions.size(); i++) {
            FactionAPI faction = (FactionAPI) factions.get(i);
            String id = faction.getId();
            Misc.makeHostileToFaction(fleet, id, false, -1f);
            Misc.makeNonHostileToFaction(fleet, id, false, -1f);
            if (active && id.equals(threatFactionId)) {
                Misc.makeHostileToFaction(fleet, id, true, -1f);
            } else {
                Misc.makeNonHostileToFaction(fleet, id, true, -1f);
            }
        }
    }

    protected CampaignFleetAPI findLocalDefender(String marketId) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null || market.getContainingLocation() == null) return null;
        List fleets = market.getContainingLocation().getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            if (fleet == null || !fleet.isAlive() || !fleet.hasTag(LOCAL_DEFENDER_TAG)) continue;
            if (marketId.equals(fleet.getMemoryWithoutUpdate().getString(MARKET_ID_KEY))) {
                return fleet;
            }
        }
        return null;
    }

    protected MarketAPI getStrategicReserveMarket() {
        if (config == null || config.ttDefenseReserveMarketId == null) return null;
        MarketAPI market = Global.getSector().getEconomy().getMarket(
                config.ttDefenseReserveMarketId);
        if (market == null || !Factions.TRITACHYON.equals(market.getFactionId())) return null;
        if (!market.isInEconomy() || market.isHidden()) return null;
        return market;
    }

    protected CampaignFleetAPI findStrategicReserve() {
        MarketAPI home = getStrategicReserveMarket();
        if (home == null || home.getContainingLocation() == null) return null;
        List fleets = home.getContainingLocation().getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            if (fleet == null || !fleet.isAlive()
                    || !fleet.hasTag(STRATEGIC_RESERVE_TAG)) continue;
            if (home.getId().equals(
                    fleet.getMemoryWithoutUpdate().getString(MARKET_ID_KEY))) return fleet;
        }
        return null;
    }

    protected void adoptExistingDefenders() {
        if (config == null || Global.getSector() == null) return;
        for (int i = 0; i < config.ttProtectedMarkets.size(); i++) {
            OmicronDirectorConfig.ProtectedMarket spec =
                    (OmicronDirectorConfig.ProtectedMarket) config.ttProtectedMarkets.get(i);
            CampaignFleetAPI fleet = findLocalDefender(spec.id);
            if (fleet != null) adoptDefender(fleet);
        }
        CampaignFleetAPI reserve = findStrategicReserve();
        if (reserve != null) adoptDefender(reserve);
    }

    protected void adoptDefender(CampaignFleetAPI fleet) {
        if (fleet == null) return;
        if (!fleet.getEventListeners().contains(this)) fleet.addEventListener(this);
        fleet.setNoAutoDespawn(true);
        fleet.setAIMode(true);
    }

    protected void configureStrategicReserveActive(CampaignFleetAPI fleet,
                                                    SectorEntityToken anchor,
                                                    String threatFactionId,
                                                    String routeSource,
                                                    String marketName) {
        if (fleet == null || anchor == null) return;
        fleet.clearAssignments();
        applyDefenseOnlyRoe(fleet, threatFactionId, true);
        fleet.getMemoryWithoutUpdate().set(ACTIVE_KEY, true);
        fleet.getMemoryWithoutUpdate().set(THREAT_FACTION_KEY, threatFactionId);
        fleet.setHidden(false);
        fleet.setTransponderOn(false);
        assignRouteThreatOrHold(fleet, anchor, routeSource, threatFactionId,
                "holding strategic reserve at " + marketName);
        Global.getLogger(OmicronTriTachyonDefenseManager.class).info(
                "Omicron strategic detachment activated from " + marketName
                        + " against " + threatFactionId);
    }

    protected SectorEntityToken findStationAnchor(MarketAPI market) {
        if (market == null) return null;
        SectorEntityToken primary = market.getPrimaryEntity();
        if (primary != null && primary.hasTag(Tags.STATION)) return primary;
        Set connected = market.getConnectedEntities();
        if (connected != null) {
            java.util.Iterator iter = connected.iterator();
            while (iter.hasNext()) {
                SectorEntityToken entity = (SectorEntityToken) iter.next();
                if (entity != null && entity.hasTag(Tags.STATION)
                        && !entity.hasTag("NO_ORBITAL_STATION")) return entity;
            }
        }
        return primary;
    }

    protected boolean hasLivingMatureNexus() {
        for (int i = 0; i < config.nodes.size(); i++) {
            OmicronDirectorConfig.Node node = (OmicronDirectorConfig.Node) config.nodes.get(i);
            if (node.uc && !OmicronWarCapacity.get().isLatticeCompleted()) continue;
            if (findNexus(node) != null) return true;
        }
        return false;
    }

    protected CampaignFleetAPI findNexus(OmicronDirectorConfig.Node node) {
        StarSystemAPI system = Global.getSector().getStarSystem(node.system);
        if (system == null) return null;
        List fleets = system.getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            if (fleet != null && fleet.isAlive() && fleet.hasTag(OmicronDirector.NEXUS_TAG)) {
                return fleet;
            }
        }
        return null;
    }

    protected boolean dispatchResponse(String threatKey, MarketAPI target,
                                       String threatFactionId, float threatFp, float etaDays) {
        NodeChoice choice = pickResponseNode(target, etaDays);
        if (choice == null) return false;

        float fp = threatFp * config.ttDefenseResponseFpMult;
        if (fp < config.ttDefenseResponseFpMin) fp = config.ttDefenseResponseFpMin;
        if (fp > config.ttDefenseResponseFpMax) fp = config.ttDefenseResponseFpMax;
        fp *= OmicronWarCapacity.getValue(choice.node.id, OmicronWarCapacity.STAT_STRENGTH);
        if (fp < 30f) return false;

        CampaignFleetAPI fleet = createOmicronFleet(target, Math.round(fp), 4, random);
        if (fleet == null) return false;
        choice.nexus.getContainingLocation().addEntity(fleet);
        fleet.setLocation(choice.nexus.getLocation().x, choice.nexus.getLocation().y);
        fleet.setName("Strategic Response");
        fleet.addTag(RESPONSE_TAG);
        fleet.getMemoryWithoutUpdate().set(NODE_ID_KEY, choice.node.id);
        fleet.getMemoryWithoutUpdate().set(MARKET_ID_KEY, target.getId());
        fleet.setAIMode(true);
        fleet.setNoAutoDespawn(true);

        // OMI-2: an early arrival holds until the threat is due plus the usual stand-down.
        OmicronTriTachyonResponseScript response = new OmicronTriTachyonResponseScript(
                fleet, choice.nexus, choice.node.id, target.getId(), threatFactionId,
                config.ttDefenseResponsePrepareDays,
                Math.max(0f, etaDays) + config.ttDefenseStandDownDays);
        Global.getSector().addScript(response);

        Global.getLogger(OmicronTriTachyonDefenseManager.class).info(
                "Omicron response dispatched: " + choice.node.id + " -> " + target.getName()
                        + ", " + Math.round(fp) + " FP, travel ~" + Math.round(choice.travelDays)
                        + "d, threat=" + threatKey);
        return true;
    }

    protected NodeChoice pickResponseNode(MarketAPI target, float etaDays) {
        NodeChoice best = null;
        for (int i = 0; i < config.nodes.size(); i++) {
            OmicronDirectorConfig.Node node = (OmicronDirectorConfig.Node) config.nodes.get(i);
            if (node.uc && !OmicronWarCapacity.get().isLatticeCompleted()) continue;
            if (OmicronTriTachyonResponseScript.countActiveForNode(node.id) > 0) continue;
            if (OmicronDirector.countLiveOpsForNode(node.id, false)
                    + OmicronDirector.countLiveOpsForNode(node.id, true) > 0) continue;

            CampaignFleetAPI nexus = findNexus(node);
            if (nexus == null) continue;
            float travelDays = RouteLocationCalculator.getTravelDays(
                    nexus, target.getPrimaryEntity());
            if (travelDays + config.ttDefenseResponsePrepareDays
                    > etaDays + config.ttDefenseResponseLateToleranceDays) continue;
            if (best == null || travelDays < best.travelDays) {
                best = new NodeChoice(node, nexus, travelDays);
            }
        }
        return best;
    }

    protected static class NodeChoice {
        OmicronDirectorConfig.Node node;
        CampaignFleetAPI nexus;
        float travelDays;

        NodeChoice(OmicronDirectorConfig.Node node, CampaignFleetAPI nexus, float travelDays) {
            this.node = node;
            this.nexus = nexus;
            this.travelDays = travelDays;
        }
    }

    protected void pruneHandledThreats() {
        java.util.Iterator iter = handledThreats.keySet().iterator();
        while (iter.hasNext()) {
            Object key = iter.next();
            Object value = handledThreats.get(key);
            if (value instanceof Long && Global.getSector().getClock()
                    .getElapsedDaysSince(((Long) value).longValue()) > 180f) {
                iter.remove();
            }
        }
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner,
                                     BattleAPI battle) {
        if (fleet == null || battle == null || !battle.isPlayerInvolved()) return;
        if (!fleet.hasTag(LOCAL_DEFENDER_TAG)
                && !fleet.hasTag(STRATEGIC_RESERVE_TAG)) return;
        if (battle.onPlayerSide(fleet)) return;

        String marketId = fleet.getMemoryWithoutUpdate().getString(MARKET_ID_KEY);
        MarketAPI market = marketId == null ? null
                : Global.getSector().getEconomy().getMarket(marketId);
        if (market == null || !Factions.TRITACHYON.equals(market.getFactionId())) return;
        if (fleet.hasTag(STRATEGIC_RESERVE_TAG)) {
            market.getMemoryWithoutUpdate().set(RESERVE_ALERT_KEY, true, 15f);
            market.getMemoryWithoutUpdate().set(
                    RESERVE_FACTION_KEY, Factions.PLAYER, 15f);
            market.getMemoryWithoutUpdate().set(RESERVE_READY_KEY, true, 15f);
        } else {
            market.getMemoryWithoutUpdate().set(ALERT_KEY, true, 15f);
            market.getMemoryWithoutUpdate().set(ALERT_FACTION_KEY, Factions.PLAYER, 15f);
        }
    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet,
                                                FleetDespawnReason reason, Object param) {
        if (fleet == null || (!fleet.hasTag(LOCAL_DEFENDER_TAG)
                && !fleet.hasTag(STRATEGIC_RESERVE_TAG))) return;
        if (reason != FleetDespawnReason.DESTROYED_BY_BATTLE
                && reason != FleetDespawnReason.NO_MEMBERS) return;

        String marketId = fleet.getMemoryWithoutUpdate().getString(MARKET_ID_KEY);
        MarketAPI market = marketId == null ? null
                : Global.getSector().getEconomy().getMarket(marketId);
        if (fleet.hasTag(STRATEGIC_RESERVE_TAG)) {
            if (market != null) {
                market.getMemoryWithoutUpdate().set(
                        RESERVE_COOLDOWN_KEY, true, config.ttDefenseReserveRespawnDays);
                Global.getLogger(OmicronTriTachyonDefenseManager.class).info(
                        "Omicron strategic detachment lost at " + market.getName()
                                + "; replacement delay "
                                + config.ttDefenseReserveRespawnDays + "d");
            }
            return;
        }

        OmicronDirectorConfig.ProtectedMarket spec = findSpec(marketId);
        if (market != null && spec != null) {
            market.getMemoryWithoutUpdate().set(REPLACEMENT_COOLDOWN_KEY, true, spec.respawnDays);
            Global.getLogger(OmicronTriTachyonDefenseManager.class).info(
                    "Omicron local complement lost at " + market.getName()
                            + "; replacement delay " + spec.respawnDays + "d");
        }
    }

    protected OmicronDirectorConfig.ProtectedMarket findSpec(String marketId) {
        if (marketId == null) return null;
        for (int i = 0; i < config.ttProtectedMarkets.size(); i++) {
            OmicronDirectorConfig.ProtectedMarket spec =
                    (OmicronDirectorConfig.ProtectedMarket) config.ttProtectedMarkets.get(i);
            if (marketId.equals(spec.id)) return spec;
        }
        return null;
    }
}
