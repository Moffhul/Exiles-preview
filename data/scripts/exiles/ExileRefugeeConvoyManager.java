package data.scripts.exiles;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.scripts.marketconditions.ExileRefugeeEncampments;

/**
 * Phase-1 refugee convoy manager. A bounded number of weak civilian convoys leave refugee
 * enclave (or Gilead), travels to a weighted Core market, lingers in orbit, and creates
 * temporary Refugee Encampments. Local spaceport control has one configurable chance to
 * order the convoy away early; otherwise it eventually leaves on its own.
 *
 * The manager owns a bounded operation list and evaluates market/raid eligibility only at launch.
 * It does no per-frame economy or intel scan. Direct fleet spawning keeps the fleets physically
 * findable; the configurable cap should remain small.
 */
public class ExileRefugeeConvoyManager implements EveryFrameScript {

    public static final String CONFIG_PATH = "data/config/exiles/director_config.json";
    public static final String CONFIG_KEY = "exilerefugee";
    public static final String REFUGEE_FACTION = "exilerefugee";
    public static final String CONVOY_FLAG = "$exiles_refugee_convoy";
    public static final String DESTINATION_COOLDOWN_KEY = "$exiles_refugee_destination_cooldown";
    public static final String SOURCE_COOLDOWN_KEY = "$exiles_refugee_source_cooldown";
    public static final String ORDERED_DEPARTURE_MESSAGE =
            "Based on complaints to local authorities, the local spaceport control "
                    + "orders the refugees to withdraw immediately.";
    public static final String ORDERED_DEPARTURE_SOUND = "ui_noise_static_message_quiet";

    protected static final int PHASE_TRAVEL = 0;
    protected static final int PHASE_ORBIT = 1;
    /** Legacy saved-state value; active development saves migrate it to ordered departure. */
    protected static final int PHASE_INTERCEPT = 2;
    protected static final int PHASE_DEPARTING = 3;
    protected static final int PHASE_LINGER = 4;
    protected static final int PHASE_ORDERED_WAIT = 5;
    /** Lets an already-serialized vanilla HassleNPCScript finish and release both fleets. */
    protected static final float LEGACY_INTERCEPT_CLEANUP_DAYS = 2.5f;

    /** Named static saved-state object -- XStream-safe. */
    public static class ConvoyOp {
        public CampaignFleetAPI fleet;
        public String sourceEntityId;
        public String targetMarketId;
        public int phase = PHASE_TRAVEL;
        public float phaseDays = 0f;
        public float responseAtDays = 22f;
        public boolean conditionApplied = false;
        public boolean receptionChecked = false;
        /** Legacy saved-state field; retained so development saves remain cheap to load. */
        public boolean patrolResponded = false;
        public boolean nameNormalized = false;
    }

    // --- Saved state ---
    /** Legacy one-operation save field; migrated into activeOps by readResolve/init. */
    protected ConvoyOp active;
    protected List activeOps = new ArrayList();
    protected float daysUntilNext = -1f;
    protected boolean launchedAny = false;

    // --- Transient config/runtime ---
    protected transient boolean configLoaded = false;
    protected transient IntervalUtil tick;
    protected transient Random random;
    protected transient boolean enabled = true;
    protected transient boolean debugMessages = false;
    protected transient int maxActiveConvoys = 3; // Sean's deliberate value (2026-09-08), matches config
    /** Hard ceiling on concurrent ordinary convoys, whatever the config says (Sean, 2026-10-06). */
    public static final int MAX_ACTIVE_CONVOYS_CEILING = 12;
    protected transient float firstLaunchDelayDays = 5f;
    protected transient float intervalMinDays = 60f;
    protected transient float intervalMaxDays = 100f;
    protected transient float retryDelayDays = 7f;
    protected transient float sourceCooldownDays = 180f;
    protected transient float destinationCooldownDays = 180f;
    protected transient float encampmentDelayDays = 10f;
    protected transient float receptionCheckMinDays = 20f;
    protected transient float receptionCheckMaxDays = 25f;
    protected transient float earlyDepartureChance = 0.7f;
    protected transient float orderedDepartureDelayDays = 0.25f;
    protected transient float maxOrbitDays = 30f;
    protected transient float aftermathDays = 10f;
    protected transient float minDestinationStability = 5f;
    protected transient int combatFp = 2;
    protected transient int freighterFp = 6;
    protected transient int tankerFp = 4;
    protected transient int transportFp = 16;
    protected transient int linerFp = 4;
    protected transient List sourceEntityIds;
    protected transient List excludedFactionIds;

    public ExileRefugeeConvoyManager() {
        initTransients();
    }

    protected Object readResolve() {
        initTransients();
        return this;
    }

    protected void initTransients() {
        configLoaded = false;
        tick = new IntervalUtil(0.1f, 0.2f);
        random = new Random();

        // XStream may restore transient primitives as zeroes before readResolve(). Make the
        // documented code defaults real on save load and when an older config lacks new keys.
        enabled = true;
        debugMessages = false;
        maxActiveConvoys = 3;
        firstLaunchDelayDays = 5f;
        intervalMinDays = 60f;
        intervalMaxDays = 100f;
        retryDelayDays = 7f;
        sourceCooldownDays = 180f;
        destinationCooldownDays = 180f;
        encampmentDelayDays = 10f;
        receptionCheckMinDays = 20f;
        receptionCheckMaxDays = 25f;
        earlyDepartureChance = 0.7f;
        orderedDepartureDelayDays = 0.25f;
        maxOrbitDays = 30f;
        aftermathDays = 10f;
        minDestinationStability = 5f;
        combatFp = 2;
        freighterFp = 6;
        tankerFp = 4;
        transportFp = 16;
        linerFp = 4;

        sourceEntityIds = null;
        excludedFactionIds = null;

        if (activeOps == null) activeOps = new ArrayList();
        if (active != null) {
            if (!activeOps.contains(active)) activeOps.add(active);
            active = null;
        }
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
        if (tick == null) tick = new IntervalUtil(0.1f, 0.2f);
        if (random == null) random = new Random();
        while (activeOps.remove(null)) {
            // Defensive cleanup for malformed development-save list entries.
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        tick.advance(days);
        if (!tick.intervalElapsed()) return;
        float elapsed = tick.getIntervalDuration();

        List snapshot = new ArrayList(activeOps);
        for (int i = 0; i < snapshot.size(); i++) {
            ConvoyOp op = (ConvoyOp) snapshot.get(i);
            if (activeOps.contains(op)) advanceActive(op, elapsed);
        }
        if (!enabled) return;
        if (maxActiveConvoys <= 0) return;
        // Pause the launch clock while the configured operation cap is full. With the
        // default cap of one this preserves the original launch cadence after an operation ends.
        if (activeOps.size() >= maxActiveConvoys) return;

        if (daysUntilNext < 0f) {
            daysUntilNext = launchedAny ? roll(intervalMinDays, intervalMaxDays)
                    : firstLaunchDelayDays;
        }
        daysUntilNext -= elapsed;
        if (daysUntilNext > 0f) return;

        if (tryLaunch()) {
            launchedAny = true;
            daysUntilNext = -1f;
        } else {
            daysUntilNext = retryDelayDays;
        }
    }

    protected void advanceActive(ConvoyOp op, float days) {
        // Starsector prepends the faction display name. Normalize convoys already
        // present in saves created before the base name was corrected.
        if (!op.nameNormalized && op.fleet != null) {
            op.fleet.setName("Convoy");
            op.nameNormalized = true;
        }

        MarketAPI target = getTarget(op);
        if (target == null || !target.isInEconomy() || target.getPrimaryEntity() == null) {
            abortActive(op);
            return;
        }

        if (op.phase == PHASE_LINGER) {
            op.phaseDays += days;
            if (op.phaseDays >= aftermathDays) {
                ExileRefugeeEncampments.removeFrom(target);
                debug("Refugee Encampments cleared from " + target.getName() + ".");
                completeActive(op);
            }
            return;
        }

        CampaignFleetAPI fleet = op.fleet;
        if (fleet == null || !fleet.isAlive() || fleet.isEmpty()) {
            if (op.conditionApplied) {
                beginAftermath(op, target);
            } else {
                completeActive(op);
            }
            return;
        }

        op.phaseDays += days;

        // Cheap compatibility for a development save made during the retired physical
        // patrol-intercept phase. Full integration into non-Exiles campaigns is not a goal.
        if (op.phase == PHASE_INTERCEPT) {
            if (op.phaseDays >= LEGACY_INTERCEPT_CLEANUP_DAYS) {
                op.receptionChecked = true;
                beginOrderedDeparture(op, target);
            }
            return;
        }

        if (op.phase == PHASE_ORDERED_WAIT) {
            if (op.phaseDays >= orderedDepartureDelayDays) {
                beginDeparture(op, target, true);
            }
            return;
        }

        if (op.phase == PHASE_TRAVEL) {
            SectorEntityToken entity = target.getPrimaryEntity();
            if (fleet.getContainingLocation() == entity.getContainingLocation()
                    && Misc.getDistance(fleet.getLocation(), entity.getLocation()) < 400f) {
                // Transit may take weeks: re-check the volatile vetoes at arrival so a Nex
                // ownership change or newly launched raid does not strand civilians in a
                // system that became unsafe after target selection.
                if (isTargetStillSafeAtArrival(target)) {
                    beginOrbit(op, target);
                } else {
                    debug("The refugee convoy found " + target.getName()
                            + " unsafe on arrival and is leaving.");
                    beginDeparture(op, target);
                }
            } else if (op.phaseDays >= 180f) {
                Global.getLogger(ExileRefugeeConvoyManager.class).warn(
                        "Refugee convoy timed out en route to " + target.getName());
                fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
                completeActive(op);
            }
            return;
        }

        if (op.phase == PHASE_ORBIT) {
            if (!op.conditionApplied && op.phaseDays >= encampmentDelayDays) {
                op.conditionApplied = true;
                ExileRefugeeEncampments.applyTo(target);
                ExileUnrestGuard.reportExilesPressure(target,
                        ExileEmergencyMeasuresConfig.pressureDays(
                                ExileRefugeeEncampments.MAX_DAYS, 0));
                debug("Refugee Encampments established at " + target.getName() + ".");
            }

            if (!op.receptionChecked && op.phaseDays >= op.responseAtDays) {
                // Set this BEFORE rolling so save/reload and repeated manager ticks cannot
                // grant additional enforcement attempts.
                op.receptionChecked = true;
                if (random.nextFloat() < earlyDepartureChance) {
                    debug("Local spaceport control ordered the refugee convoy away from "
                            + target.getName() + ".");
                    beginOrderedDeparture(op, target);
                    return;
                }
                debug("Local spaceport control did not remove the refugee convoy from "
                        + target.getName() + "; it may remain until day "
                        + (int) maxOrbitDays + ".");
            }
            if (op.phaseDays >= maxOrbitDays) {
                debug("The refugee convoy at " + target.getName()
                        + " is leaving on its own.");
                beginDeparture(op, target);
            }
            return;
        }

        if (op.phase == PHASE_DEPARTING) {
            if (fleet.getContainingLocation() != target.getContainingLocation()
                    || !fleet.isAlive() || op.phaseDays >= 20f) {
                if (op.phaseDays >= 20f && fleet.isAlive()) {
                    fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
                }
                if (op.conditionApplied) {
                    beginAftermath(op, target);
                } else {
                    completeActive(op);
                }
            }
        }
    }

    protected boolean tryLaunch() {
        MarketAPI source = pickSource();
        if (source == null) return false;
        MarketAPI target = pickTarget(source);
        if (target == null) return false;

        CampaignFleetAPI fleet = createConvoy(source);
        if (fleet == null) return false;

        SectorEntityToken sourceEntity = source.getPrimaryEntity();
        sourceEntity.getContainingLocation().addEntity(fleet);
        fleet.setLocation(
                sourceEntity.getLocation().x + (random.nextFloat() - 0.5f) * 300f,
                sourceEntity.getLocation().y + (random.nextFloat() - 0.5f) * 300f);
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target.getPrimaryEntity(), 180f,
                "seeking refuge at " + target.getName());

        ConvoyOp op = new ConvoyOp();
        op.fleet = fleet;
        op.sourceEntityId = sourceEntity.getId();
        op.targetMarketId = target.getId();
        op.nameNormalized = true;
        activeOps.add(op);

        source.getMemoryWithoutUpdate().set(SOURCE_COOLDOWN_KEY, true, sourceCooldownDays);

        String where = target.getName() + " in "
                + target.getContainingLocation().getNameWithLowercaseType();
        debug("Refugee convoy departing " + source.getName() + " for " + where
                + " (active " + activeOps.size() + "/" + maxActiveConvoys + ").");
        Global.getLogger(ExileRefugeeConvoyManager.class).info(
                "Refugee convoy launched: " + source.getName() + " -> " + where
                        + " [active=" + activeOps.size() + "/" + maxActiveConvoys + "]");
        return true;
    }

    protected CampaignFleetAPI createConvoy(MarketAPI source) {
        FleetParamsV3 params = new FleetParamsV3(
                null,
                source.getLocationInHyperspace(),
                REFUGEE_FACTION,
                Float.valueOf(0f),
                FleetTypes.TRADE_SMALL,
                (float) combatFp,
                (float) freighterFp,
                (float) tankerFp,
                (float) transportFp,
                (float) linerFp,
                0f,
                -0.25f);
        params.random = random;
        params.ignoreMarketFleetSizeMult = Boolean.TRUE;
        params.maxShipSize = 2;
        params.withOfficers = false;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            Global.getLogger(ExileRefugeeConvoyManager.class).warn(
                    "Refugee convoy fleet creation failed");
            return null;
        }
        fleet.setName("Convoy");
        fleet.setTransponderOn(true);
        fleet.getMemoryWithoutUpdate().set(CONVOY_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        return fleet;
    }

    protected void beginOrbit(ConvoyOp op, MarketAPI target) {
        op.phase = PHASE_ORBIT;
        op.phaseDays = 0f;
        op.responseAtDays = roll(receptionCheckMinDays, receptionCheckMaxDays);
        op.receptionChecked = false;
        op.fleet.clearAssignments();
        op.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target.getPrimaryEntity(),
                maxOrbitDays + 5f, "sheltering near " + target.getName());
        target.getMemoryWithoutUpdate().set(
                DESTINATION_COOLDOWN_KEY, true, destinationCooldownDays);
        debug("Refugee convoy arrived in orbit at " + target.getName() + ".");
    }

    protected void beginDeparture(ConvoyOp op, MarketAPI target) {
        beginDeparture(op, target, false);
    }

    protected void beginOrderedDeparture(ConvoyOp op, MarketAPI target) {
        CampaignFleetAPI fleet = op.fleet;
        op.phase = PHASE_ORDERED_WAIT;
        op.phaseDays = 0f;
        if (fleet == null || !fleet.isAlive()) {
            if (op.conditionApplied) beginAftermath(op, target);
            else completeActive(op);
            return;
        }

        if (fleet.isInCurrentLocation() && fleet.isVisibleToPlayerFleet()) {
            Global.getSector().getCampaignUI().addMessage(ORDERED_DEPARTURE_MESSAGE);
            Global.getSoundPlayer().playUISound(ORDERED_DEPARTURE_SOUND, 1f, 1f);
        }

        // Refresh passive orbit so even a deliberately long configured pause cannot outlast
        // the original arrival assignment. Departure itself remains entirely vanilla-driven.
        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target.getPrimaryEntity(),
                orderedDepartureDelayDays + 2f,
                "preparing to withdraw under orders from local spaceport control");
    }

    protected void beginDeparture(
            ConvoyOp op, MarketAPI target, boolean orderedBySpaceportControl) {
        CampaignFleetAPI fleet = op.fleet;
        op.phase = PHASE_DEPARTING;
        op.phaseDays = 0f;
        if (fleet == null || !fleet.isAlive()) {
            if (op.conditionApplied) beginAftermath(op, target);
            else completeActive(op);
            return;
        }

        fleet.clearAssignments();
        JumpPointAPI jump = RouteLocationCalculator.findJumpPointToUse(
                fleet.getFaction(), target.getPrimaryEntity());
        SectorEntityToken exit = jump;
        if (exit == null) {
            List points = target.getContainingLocation().getJumpPoints();
            if (!points.isEmpty()) exit = (SectorEntityToken) points.get(0);
        }
        if (exit == null) exit = target.getPrimaryEntity();

        String assignmentText = "leaving the "
                + target.getContainingLocation().getNameWithLowercaseType();
        if (orderedBySpaceportControl) {
            assignmentText = "withdrawing under orders from local spaceport control";
        }
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, exit, 20f,
                assignmentText);
    }

    protected void beginAftermath(ConvoyOp op, MarketAPI target) {
        op.phase = PHASE_LINGER;
        op.phaseDays = 0f;
        // Bug sweep REF-4 (2026-10-06): every caller reaches here only once the fleet is gone or
        // has left the system, and PHASE_LINGER never reads op.fleet -- so drop the reference
        // rather than serialize a dead fleet's object graph through the 10-day aftermath.
        op.fleet = null;
        debug("The refugee convoy has left " + target.getName()
                + "; its encampments will linger for " + (int) aftermathDays + " days.");
    }

    protected void abortActive(ConvoyOp op) {
        if (op != null && op.fleet != null && op.fleet.isAlive()) {
            op.fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
        }
        MarketAPI target = getTarget(op);
        if (target != null && op != null && op.conditionApplied) {
            ExileRefugeeEncampments.removeFrom(target);
        }
        completeActive(op);
    }

    protected void completeActive(ConvoyOp op) {
        activeOps.remove(op);
    }

    protected MarketAPI getTarget(ConvoyOp op) {
        if (op == null || op.targetMarketId == null || Global.getSector() == null) return null;
        return Global.getSector().getEconomy().getMarket(op.targetMarketId);
    }

    protected MarketAPI pickSource() {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        for (int i = 0; i < sourceEntityIds.size(); i++) {
            String entityId = (String) sourceEntityIds.get(i);
            SectorEntityToken entity = Global.getSector().getEntityById(entityId);
            if (entity == null || entity.getMarket() == null) continue;
            MarketAPI market = entity.getMarket();
            if (!market.isInEconomy() || market.isHidden()) continue;
            if (market.getMemoryWithoutUpdate().getBoolean(SOURCE_COOLDOWN_KEY)) continue;
            Industry spaceport = Misc.getSpaceport(market);
            if (spaceport == null || !spaceport.isFunctional()) continue;
            picker.add(market);
        }
        return (MarketAPI) picker.pick();
    }

    protected MarketAPI pickTarget(MarketAPI source) {
        FactionAPI refugees = Global.getSector().getFaction(REFUGEE_FACTION);
        List busySystems = getSystemsWithActiveRaids();
        WeightedRandomPicker picker = new WeightedRandomPicker(random);

        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market == null || market == source) continue;
            if (!market.isInEconomy() || market.isHidden() || market.isPlayerOwned()) continue;
            if (market.isPlanetConditionMarketOnly() || market.getPrimaryEntity() == null) continue;
            if (!(market.getContainingLocation() instanceof StarSystemAPI)) continue;

            StarSystemAPI system = (StarSystemAPI) market.getContainingLocation();
            if (!system.hasTag(Tags.THEME_CORE_POPULATED)) continue;
            if (busySystems.contains(system)) continue;

            String factionId = market.getFactionId();
            if (excludedFactionIds.contains(factionId)) continue;
            if (market.getFaction() == null || refugees == null) continue;
            if (market.getFaction().isHostileTo(refugees)
                    || refugees.isHostileTo(market.getFaction())) continue;

            if (market.getStabilityValue() < minDestinationStability) continue;
            if (market.hasCondition("exile_refugee_strain")
                    || market.hasCondition(ExileRefugeeEncampments.CONDITION_ID)) continue;
            if (isTargetReserved(market)) continue;
            if (market.getMemoryWithoutUpdate()
                    .getBoolean(DESTINATION_COOLDOWN_KEY)) continue;

            float sizeWeight = Math.max(1f, market.getSize() - 2f);
            float accessibility = market.getAccessibilityMod().computeEffective(0f);
            if (accessibility < 0.25f) accessibility = 0.25f;
            picker.add(market, sizeWeight * accessibility);
        }
        return (MarketAPI) picker.pick();
    }

    /** Prevent two concurrently active operations from converging on the same destination. */
    protected boolean isTargetReserved(MarketAPI market) {
        if (market == null || market.getId() == null) return false;
        for (int i = 0; i < activeOps.size(); i++) {
            ConvoyOp op = (ConvoyOp) activeOps.get(i);
            if (op != null && market.getId().equals(op.targetMarketId)) return true;
        }
        return false;
    }

    protected boolean isTargetStillSafeAtArrival(MarketAPI market) {
        if (market == null || !market.isInEconomy() || market.isHidden()
                || market.isPlayerOwned() || market.getPrimaryEntity() == null) return false;
        if (!(market.getContainingLocation() instanceof StarSystemAPI)) return false;
        if (excludedFactionIds.contains(market.getFactionId())) return false;
        if (market.getStabilityValue() < minDestinationStability) return false;
        if (market.hasCondition("exile_refugee_strain")
                || market.hasCondition(ExileRefugeeEncampments.CONDITION_ID)) return false;

        FactionAPI refugees = Global.getSector().getFaction(REFUGEE_FACTION);
        if (refugees == null || market.getFaction() == null) return false;
        if (market.getFaction().isHostileTo(refugees)
                || refugees.isHostileTo(market.getFaction())) return false;
        return !getSystemsWithActiveRaids().contains(market.getContainingLocation());
    }

    /** All live RaidIntel subclasses: vanilla raids plus Black Fleet, Omicron and Ashen
     * operations, all of which ride the same stable pipeline. Called only at convoy launch.
     *
     * <p>No faction is excluded -- refugees keep clear of everyone's war zones. Body extracted to
     * {@link ExileLiveOps} (Pass 5b survivor). */
    protected List getSystemsWithActiveRaids() {
        return ExileLiveOps.getSystemsWithActiveRaids(null);
    }

    protected float roll(float min, float max) {
        if (max <= min) return min;
        return min + random.nextFloat() * (max - min);
    }

    protected void debug(String text) {
        if (!debugMessages || Global.getSector() == null) return;
        Global.getSector().getCampaignUI().addMessage("DEBUG: " + text, Color.YELLOW);
    }

    protected void loadConfig() {
        configLoaded = true;
        sourceEntityIds = new ArrayList();
        excludedFactionIds = new ArrayList();

        // Safe defaults if config load fails.
        sourceEntityIds.add("corvus_abandoned_station");
        sourceEntityIds.add("mairaath_abandoned_station1");
        sourceEntityIds.add("yma_abandoned_station");
        sourceEntityIds.add("maxios");
        sourceEntityIds.add("gilead");
        excludedFactionIds.add(Factions.PLAYER);
        excludedFactionIds.add(Factions.PIRATES);
        excludedFactionIds.add(Factions.LUDDIC_PATH);
        excludedFactionIds.add(REFUGEE_FACTION);
        excludedFactionIds.add("exileashencrusade");
        excludedFactionIds.add("exileomicron");
        excludedFactionIds.add("exileblackfleet");
        excludedFactionIds.add("exilemidan");

        try {
            JSONObject root = Global.getSettings().loadJSON(CONFIG_PATH);
            JSONObject cfg = root.optJSONObject(CONFIG_KEY);
            if (cfg == null) {
                normalizeConfig();
                return;
            }

            enabled = cfg.optBoolean("enabled", enabled);
            debugMessages = cfg.optBoolean("debugMessages", debugMessages);
            maxActiveConvoys = cfg.optInt("maxActiveConvoys", maxActiveConvoys);
            firstLaunchDelayDays = (float) cfg.optDouble(
                    "firstLaunchDelayDays", firstLaunchDelayDays);
            intervalMinDays = (float) cfg.optDouble("intervalMinDays", intervalMinDays);
            intervalMaxDays = (float) cfg.optDouble("intervalMaxDays", intervalMaxDays);
            retryDelayDays = (float) cfg.optDouble("retryDelayDays", retryDelayDays);
            sourceCooldownDays = (float) cfg.optDouble(
                    "sourceCooldownDays", sourceCooldownDays);
            destinationCooldownDays = (float) cfg.optDouble(
                    "destinationCooldownDays", destinationCooldownDays);
            encampmentDelayDays = (float) cfg.optDouble(
                    "encampmentDelayDays", encampmentDelayDays);
            if (cfg.has("receptionCheckMinDays")) {
                receptionCheckMinDays = (float) cfg.optDouble(
                        "receptionCheckMinDays", receptionCheckMinDays);
            } else {
                // Backward-compatible alias for manually tuned development configs.
                receptionCheckMinDays = (float) cfg.optDouble(
                        "patrolResponseMinDays", receptionCheckMinDays);
            }
            if (cfg.has("receptionCheckMaxDays")) {
                receptionCheckMaxDays = (float) cfg.optDouble(
                        "receptionCheckMaxDays", receptionCheckMaxDays);
            } else {
                // Backward-compatible alias for manually tuned development configs.
                receptionCheckMaxDays = (float) cfg.optDouble(
                        "patrolResponseMaxDays", receptionCheckMaxDays);
            }
            earlyDepartureChance = (float) cfg.optDouble(
                    "earlyDepartureChance", earlyDepartureChance);
            orderedDepartureDelayDays = (float) cfg.optDouble(
                    "orderedDepartureDelayDays", orderedDepartureDelayDays);
            maxOrbitDays = (float) cfg.optDouble("maxOrbitDays", maxOrbitDays);
            aftermathDays = (float) cfg.optDouble("aftermathDays", aftermathDays);
            minDestinationStability = (float) cfg.optDouble(
                    "minDestinationStability", minDestinationStability);

            JSONObject fleet = cfg.optJSONObject("fleet");
            if (fleet != null) {
                combatFp = fleet.optInt("combatFp", combatFp);
                freighterFp = fleet.optInt("freighterFp", freighterFp);
                tankerFp = fleet.optInt("tankerFp", tankerFp);
                transportFp = fleet.optInt("transportFp", transportFp);
                linerFp = fleet.optInt("linerFp", linerFp);
            }

            JSONArray sources = cfg.optJSONArray("sourceEntityIds");
            if (sources != null) {
                sourceEntityIds.clear();
                for (int i = 0; i < sources.length(); i++) {
                    sourceEntityIds.add(sources.getString(i));
                }
            }
            JSONArray excluded = cfg.optJSONArray("excludedFactionIds");
            if (excluded != null) {
                excludedFactionIds.clear();
                for (int i = 0; i < excluded.length(); i++) {
                    excludedFactionIds.add(excluded.getString(i));
                }
            }
        } catch (Exception e) {
            Global.getLogger(ExileRefugeeConvoyManager.class).warn(
                    "Refugee convoy config load failed; using code defaults", e);
        }
        normalizeConfig();
    }

    /** Keep player-facing timing knobs inside a lifecycle that cannot skip or reverse phases. */
    protected void normalizeConfig() {
        if (maxActiveConvoys < 0) maxActiveConvoys = 0;
        // Player-facing cap (Sean, 2026-10-06): free to change, bounded as a safety rail.
        if (maxActiveConvoys > MAX_ACTIVE_CONVOYS_CEILING) {
            Global.getLogger(ExileRefugeeConvoyManager.class).warn(
                    "Refugee maxActiveConvoys " + maxActiveConvoys + " exceeds the ceiling; clamped to "
                            + MAX_ACTIVE_CONVOYS_CEILING);
            maxActiveConvoys = MAX_ACTIVE_CONVOYS_CEILING;
        }
        if (earlyDepartureChance < 0f) earlyDepartureChance = 0f;
        if (earlyDepartureChance > 1f) earlyDepartureChance = 1f;
        if (orderedDepartureDelayDays < 0f) orderedDepartureDelayDays = 0f;

        if (encampmentDelayDays < 0f) encampmentDelayDays = 0f;
        if (maxOrbitDays < encampmentDelayDays) maxOrbitDays = encampmentDelayDays;

        if (receptionCheckMinDays < encampmentDelayDays) {
            receptionCheckMinDays = encampmentDelayDays;
        }
        if (receptionCheckMinDays > maxOrbitDays) {
            receptionCheckMinDays = maxOrbitDays;
        }
        if (receptionCheckMaxDays < receptionCheckMinDays) {
            receptionCheckMaxDays = receptionCheckMinDays;
        }
        if (receptionCheckMaxDays > maxOrbitDays) {
            receptionCheckMaxDays = maxOrbitDays;
        }

        if (aftermathDays < 0f) aftermathDays = 0f;
    }
}
