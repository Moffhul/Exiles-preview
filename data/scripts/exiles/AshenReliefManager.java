package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.scripts.marketconditions.ExileReliefShipment;

/**
 * Ashen Crusade relief convoys -- PASS 3 of docs/EXILES_ASHEN_CRUSADE_WAR.md §4.1.
 *
 * <p>During Phase 1 (Buildup) the Crusade sends aid convoys to the refugee enclaves, where they
 * unload and then leave with volunteers. This is the faction's whole character in one system: it
 * is genuinely the only power in the sector feeding these people, AND it is harvesting them for a
 * war they did not choose. Both halves are sincere.
 *
 * <p><b>Why a separate script</b> rather than a field on {@link AshenWarDirector}: convoys carry
 * per-operation saved state (fleet, target, phase, timers), and keeping that out of the director
 * leaves the director's save footprint to primitives. Matches the existing pattern --
 * {@code BlackFleetRaidManager}, {@code ExileBlackFleetExtortionManager} and
 * {@code ExileRefugeeConvoyManager} are all separate scripts. The director is consulted through
 * {@link AshenWarDirector#get()} for the current phase and to bank war score.
 *
 * <p><b>War score.</b> This is the only organic source of war score in the design (§3.1, §5.5) --
 * it is what eventually tips special-sabotage selection toward Culann and raises its odds. Score
 * is banked when a convoy finishes unloading and departs, NOT on arrival: a convoy destroyed in
 * orbit has fed the enclave but delivered no recruits home. The enclave keeps the aid either way,
 * which is the right moral shape.
 *
 * <p><b>Safety.</b> One bounded list of at most {@code maxActive} civilian fleets, each with a
 * travel timeout and a departure timeout, so nothing can be orphaned by a target going away
 * mid-flight. Ashen is INHOSPITABLE (not hostile) to Independents, so transit through Corvus and
 * Mairaath is safe by design, not by luck.
 *
 * <p>Silent to the player (§3.4): the convoy is visible in space and the condition is the tell.
 */
public class AshenReliefManager implements EveryFrameScript {

    public static final String CONFIG_PATH = "data/config/exiles/director_config.json";
    public static final String CONFIG_KEY = "exileashencrusade";
    public static final String FACTION_ID = "exileashencrusade";
    public static final String RELIEF_FLAG = "$exiles_ashen_relief";

    protected static final int OP_TRAVEL = 0;
    protected static final int OP_ORBIT = 1;
    protected static final int OP_DEPARTING = 2;

    /** Named static saved-state object -- XStream-safe (the ConvoyOp precedent). */
    public static class ReliefOp {
        public CampaignFleetAPI fleet;
        public String targetEntityId;
        public int phase = OP_TRAVEL;
        public float phaseDays = 0f;
        public float orbitDays = 15f;
        public boolean conditionApplied = false;
    }

    // --- Saved state ---
    protected List activeOps = new ArrayList(); // ReliefOp
    protected float daysUntilNext = -1f;

    // --- Transient config/runtime ---
    protected transient boolean configLoaded;
    protected transient IntervalUtil tick;
    protected transient Random random;

    protected transient boolean enabled = true;
    protected transient float cadenceDays = 30f;
    /** Phases 2 and 3 (§4.1, ratified 2026-08-01). 0 disables off-phase relief entirely. */
    protected transient float offPhaseCadenceDays = 60f;
    protected transient int maxActive = 2;
    /**
     * A faction that will not engage relief convoys, or "" for none. Default {@code tritachyon}.
     *
     * <p>Not a plot shield and not a disguise -- the convoys fly the Crusade's own flag, openly,
     * which is the point. Tri-Tachyon has certainly worked out that these are recruiting runs,
     * but half the enclaves sit inside its patrol space (maxios beside Tibicena in Magec,
     * mairaath beside Port Tse in Mayasura), and gunning down food convoys in front of every
     * independent trader in the system is a public-relations catastrophe it cannot afford --
     * it is already being blamed for Omicron's attacks on Luddic worlds. Letting the Crusade
     * feed refugees costs Tri-Tachyon less than the footage would.
     *
     * <p>Deliberately ONE faction rather than the blanket {@code FLEET_IGNORED_BY_OTHER_FLEETS}:
     * Kanta's pirates share Magec with the maxios enclave and have no reputation to protect, so
     * they still hunt these convoys. Losses stay possible, and the war-score damper survives.
     * The PLAYER is unaffected by this flag either way.
     */
    protected transient String ignoredByFactionId = "tritachyon";
    protected transient float orbitMinDays = 10f;
    protected transient float orbitMaxDays = 20f;
    protected transient float travelTimeoutDays = 180f;
    protected transient float departTimeoutDays = 20f;
    protected transient float scorePerRelief = 0.08f;
    protected transient List enclaveEntityIds; // String
    protected transient List sourceEntityIds;  // String
    protected transient int combatFp = 20;
    protected transient int freighterFp = 20;
    protected transient int tankerFp = 6;
    protected transient int transportFp = 12;

    public AshenReliefManager() {
        initTransients();
    }

    protected Object readResolve() {
        initTransients();
        if (activeOps == null) activeOps = new ArrayList();
        return this;
    }

    protected void initTransients() {
        configLoaded = false;
        tick = new IntervalUtil(0.1f, 0.2f);
        random = new Random();
        enclaveEntityIds = null;
        sourceEntityIds = null;
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
        if (activeOps == null) activeOps = new ArrayList();

        float days = Global.getSector().getClock().convertToDays(amount);
        tick.advance(days);
        if (!tick.intervalElapsed()) return;
        float elapsed = tick.getIntervalDuration();

        // In-flight convoys keep going regardless of phase -- only LAUNCHING is phase-gated.
        // A convoy caught by a phase change is already loaded and on its way.
        advanceOps(elapsed);

        if (!enabled) return;
        AshenWarDirector director = AshenWarDirector.get();
        if (director == null) return;

        float cadence = getCadenceForPhase(director.getPhase());
        if (cadence <= 0f) return; // relief does not run in this phase
        if (activeOps.size() >= maxActive) return;

        if (daysUntilNext < 0f) {
            daysUntilNext = cadence;
            return;
        }
        // Entering a faster phase should not leave a long dead period inherited from a slower
        // one (notably Phase 4 -> Phase 1, where relief resumes at double rate).
        if (daysUntilNext > cadence) daysUntilNext = cadence;

        daysUntilNext -= elapsed;
        if (daysUntilNext > 0f) return;

        daysUntilNext = cadence;
        tryLaunch(director);
    }

    /**
     * Launch cadence by war-clock phase (ratified 2026-08-01). Phase 1 is the recruiting drive
     * and runs at double rate; Phases 2 and 3 keep convoys visible campaign-wide at half rate,
     * because the Crusade would not stop feeding refugees merely because it started raiding.
     * Phase 4 runs none -- everything is committed to the Culann strike.
     *
     * <p>Adding the off-phase runs roughly doubles convoys per cycle (~3 to ~5.5), so
     * {@code scorePerRelief} drops correspondingly to hold the ratified 2-3 in-game year band
     * for the Phase-4 strike (§5.5). Those two numbers move together -- change one and recheck
     * the other.
     */
    protected float getCadenceForPhase(int phase) {
        if (phase == AshenWarDirector.PHASE_BUILDUP) return cadenceDays;
        if (phase == AshenWarDirector.PHASE_INTERDICTION
                || phase == AshenWarDirector.PHASE_BLOCKADE) return offPhaseCadenceDays;
        return 0f; // PHASE_ASSAULT
    }

    // ------------------------------------------------------------------
    // Operation lifecycle
    // ------------------------------------------------------------------

    protected void advanceOps(float elapsed) {
        for (int i = activeOps.size() - 1; i >= 0; i--) {
            ReliefOp op = (ReliefOp) activeOps.get(i);
            if (!advanceOp(op, elapsed)) {
                activeOps.remove(i);
            }
        }
    }

    /** @return false when the operation is finished and should be dropped. */
    protected boolean advanceOp(ReliefOp op, float elapsed) {
        SectorEntityToken target = getEntity(op.targetEntityId);
        CampaignFleetAPI fleet = op.fleet;

        // Target gone (decivilised, removed): send the convoy home rather than orphaning it.
        if (target == null || target.getMarket() == null) {
            if (fleet != null && fleet.isAlive()) {
                fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
            }
            return false;
        }
        // A DEPARTING convoy is expected to vanish: GO_TO_LOCATION_AND_DESPAWN removes the fleet
        // at the jump point rather than transiting it, and SectorEntityToken.isAlive() is false
        // once the entity is no longer in its containing location. Checked before the loss branch
        // below, which otherwise reports every successful return as a loss (found 2026-08-01).
        // A convoy killed on the way home also lands here and is reported as returned; that is
        // indistinguishable without extra tracking and changes nothing -- the score banked at
        // beginDeparture and the shipment landed on arrival.
        if (op.phase == OP_DEPARTING && (fleet == null || !fleet.isAlive())) {
            debug("An Ashen relief convoy has left " + target.getName() + " for Providence.");
            return false;
        }
        // Convoy destroyed. The enclave keeps whatever was already unloaded; no war score,
        // because no recruits went home.
        if (fleet == null || !fleet.isAlive() || fleet.isEmpty()) {
            debug("An Ashen relief convoy bound for " + target.getName() + " was lost.");
            return false;
        }

        op.phaseDays += elapsed;

        if (op.phase == OP_TRAVEL) {
            if (fleet.getContainingLocation() == target.getContainingLocation()
                    && Misc.getDistance(fleet.getLocation(), target.getLocation()) < 400f) {
                beginOrbit(op, target);
            } else if (op.phaseDays >= travelTimeoutDays) {
                Global.getLogger(AshenReliefManager.class).warn(
                        "Ashen relief convoy timed out en route to " + target.getName());
                fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
                return false;
            }
            return true;
        }

        if (op.phase == OP_ORBIT) {
            if (op.phaseDays >= op.orbitDays) {
                beginDeparture(op, target);
            }
            return true;
        }

        // OP_DEPARTING
        if (fleet.getContainingLocation() != target.getContainingLocation()
                || op.phaseDays >= departTimeoutDays) {
            if (fleet.isAlive()) {
                fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
            }
            return false;
        }
        return true;
    }

    protected void beginOrbit(ReliefOp op, SectorEntityToken target) {
        op.phase = OP_ORBIT;
        op.phaseDays = 0f;
        op.fleet.clearAssignments();
        op.fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target,
                op.orbitDays + 5f, "unloading relief supplies at " + target.getName());

        // The aid lands on arrival, not on departure: the enclave is being fed while the
        // convoy sits there, and it keeps the shipment even if the convoy never leaves.
        if (!op.conditionApplied) {
            op.conditionApplied = true;
            ExileReliefShipment.applyTo(target.getMarket());
            debug("Ashen relief convoy is unloading at " + target.getName() + ".");
            Global.getLogger(AshenReliefManager.class).info(
                    "Ashen relief delivered to " + target.getName());
        }
    }

    protected void beginDeparture(ReliefOp op, SectorEntityToken target) {
        op.phase = OP_DEPARTING;
        op.phaseDays = 0f;

        // War score banks HERE: the recruits are aboard and the convoy is heading home.
        AshenWarDirector director = AshenWarDirector.get();
        if (director != null && scorePerRelief != 0f) {
            director.addWarScore(scorePerRelief);
        }
        debug("Ashen relief convoy is leaving " + target.getName() + " with volunteers.");

        CampaignFleetAPI fleet = op.fleet;
        fleet.clearAssignments();
        SectorEntityToken exit = RouteLocationCalculator.findJumpPointToUse(
                fleet.getFaction(), target);
        if (exit == null) {
            List points = target.getContainingLocation().getEntities(JumpPointAPI.class);
            if (!points.isEmpty()) exit = (SectorEntityToken) points.get(0);
        }
        if (exit == null) exit = target;
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, exit, departTimeoutDays,
                "returning to Providence");
    }

    // ------------------------------------------------------------------
    // Launch
    // ------------------------------------------------------------------

    protected void tryLaunch(AshenWarDirector director) {
        MarketAPI source = pickSource();
        if (source == null) return;
        SectorEntityToken target = pickTarget();
        if (target == null) return;

        CampaignFleetAPI fleet = createConvoy(source);
        if (fleet == null) return;

        SectorEntityToken sourceEntity = source.getPrimaryEntity();
        sourceEntity.getContainingLocation().addEntity(fleet);
        fleet.setLocation(
                sourceEntity.getLocation().x + (random.nextFloat() - 0.5f) * 300f,
                sourceEntity.getLocation().y + (random.nextFloat() - 0.5f) * 300f);
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target, travelTimeoutDays,
                "carrying relief supplies to " + target.getName());

        ReliefOp op = new ReliefOp();
        op.fleet = fleet;
        op.targetEntityId = target.getId();
        op.orbitDays = roll(orbitMinDays, orbitMaxDays);
        activeOps.add(op);

        debug("Ashen relief convoy departing " + source.getName() + " for " + target.getName()
                + " (" + (int) op.orbitDays + "d stay).");
        Global.getLogger(AshenReliefManager.class).info(
                "Ashen relief convoy launched: " + source.getName() + " -> " + target.getName());
    }

    protected CampaignFleetAPI createConvoy(MarketAPI source) {
        FleetParamsV3 params = new FleetParamsV3(
                null,
                source.getLocationInHyperspace(),
                FACTION_ID,
                Float.valueOf(0f),
                FleetTypes.TRADE,
                (float) combatFp,
                (float) freighterFp,
                (float) tankerFp,
                (float) transportFp,
                0f,
                0f,
                0f);
        params.random = random;
        params.ignoreMarketFleetSizeMult = Boolean.TRUE;
        params.withOfficers = false;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            Global.getLogger(AshenReliefManager.class).warn(
                    "Ashen relief convoy fleet creation failed");
            return null;
        }
        // Starsector prepends the faction display name (the refugee-convoy lesson).
        fleet.setName("Relief Convoy");
        fleet.setTransponderOn(true);
        fleet.getMemoryWithoutUpdate().set(RELIEF_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
        // An aid convoy runs rather than brawls; one pursuit still catches it.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        // See the ignoredByFactionId javadoc. Vanilla precedent: EconomyFleetRouteManager uses
        // this same flag to keep certain fleets off the pirates' target list.
        if (ignoredByFactionId != null && ignoredByFactionId.length() > 0) {
            fleet.getMemoryWithoutUpdate().set(
                    MemFlags.FLEET_IGNORED_BY_FACTION, ignoredByFactionId);
        }
        return fleet;
    }

    /** An Ashen market with a functional spaceport -- the convoys have to load somewhere. */
    protected MarketAPI pickSource() {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        for (int i = 0; i < sourceEntityIds.size(); i++) {
            SectorEntityToken entity = getEntity((String) sourceEntityIds.get(i));
            if (entity == null || entity.getMarket() == null) continue;
            MarketAPI market = entity.getMarket();
            if (!market.isInEconomy() || market.isHidden()) continue;
            if (!FACTION_ID.equals(market.getFactionId())) continue;
            Industry spaceport = Misc.getSpaceport(market);
            if (spaceport == null || !spaceport.isFunctional()) continue;
            picker.add(market);
        }
        return (MarketAPI) picker.pick();
    }

    /**
     * A refugee enclave that is not already carrying a Relief Shipment and is not already the
     * target of an in-flight convoy. No spaceport requirement -- aid is being delivered TO them,
     * and Yma deliberately runs broken structures.
     */
    protected SectorEntityToken pickTarget() {
        List busySystems = getSystemsWithActiveRaids();
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        for (int i = 0; i < enclaveEntityIds.size(); i++) {
            String id = (String) enclaveEntityIds.get(i);
            SectorEntityToken entity = getEntity(id);
            if (entity == null || entity.getMarket() == null) continue;
            MarketAPI market = entity.getMarket();
            if (!market.isInEconomy() || market.isHidden()) continue;
            if (ExileReliefShipment.isRelieved(market)) continue;
            if (isTargeted(id)) continue;
            // Raid veto (ratified 2026-08-01): two of the four enclaves share a system with a
            // Tri-Tachyon market -- maxios with Tibicena in Magec, mairaath with Port Tse in
            // Mayasura -- so relief runs can wander into somebody else's live operation.
            // The Crusade's own raids are EXCLUDED (see getSystemsWithActiveRaids). Checked at
            // LAUNCH only: a convoy already most of the way there does not turn back, which is
            // both cheaper and better characterisation than an aid convoy making a U-turn.
            if (busySystems.contains(entity.getContainingLocation())) continue;
            // Smaller, poorer enclaves need it more -- and the Crusade knows where the
            // desperate are thickest.
            picker.add(entity, Math.max(1f, 6f - market.getSize()));
        }
        return (SectorEntityToken) picker.pick();
    }

    /**
     * Systems hosting a live staged operation -- vanilla raids plus Black Fleet, Omicron and
     * (from Pass 4) the Crusade's own interdiction raids, since all of them ride the same
     * {@code RaidIntel} pipeline. Called only at convoy launch, never per frame.
     *
     * <p>The Crusade's OWN operations are excluded (Sean, 2026-08-01): the Crusade knows where its
     * own fleets are, and the enclave is not its target. Load-bearing, not cosmetic -- the Crusade
     * raids Magec and Mayasura during Phases 2-3, which is exactly where two of the four enclaves
     * are, so counting Ashen raids here would silently cancel off-phase relief for them (§4.1c).
     *
     * <p>Body extracted to {@link ExileLiveOps} (Pass 5b survivor); this stays as the named,
     * documented seam its callers already use.
     */
    protected List getSystemsWithActiveRaids() {
        return ExileLiveOps.getSystemsWithActiveRaids(FACTION_ID);
    }

    protected boolean isTargeted(String entityId) {
        for (int i = 0; i < activeOps.size(); i++) {
            ReliefOp op = (ReliefOp) activeOps.get(i);
            if (entityId.equals(op.targetEntityId)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    protected static SectorEntityToken getEntity(String id) {
        if (id == null || Global.getSector() == null) return null;
        return Global.getSector().getEntityById(id);
    }

    protected float roll(float min, float max) {
        if (max <= min) return min;
        return min + random.nextFloat() * (max - min);
    }

    protected void debug(String text) {
        AshenWarDirector director = AshenWarDirector.get();
        if (director != null) director.debug(text);
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
            // Shared with the director so ONE knob still compresses the whole war (§9.1).
            timeScale = (float) faction.optDouble("timeScale", 1d);
            if (timeScale <= 0f) timeScale = 1f;

            JSONObject cfg = faction.optJSONObject("relief");
            if (cfg != null) {
                enabled = cfg.optBoolean("enabled", enabled);
                cadenceDays = (float) cfg.optDouble("cadenceDays", cadenceDays);
                offPhaseCadenceDays = (float) cfg.optDouble(
                        "offPhaseCadenceDays", offPhaseCadenceDays);
                ignoredByFactionId = cfg.optString("ignoredByFactionId", ignoredByFactionId);
                maxActive = cfg.optInt("maxActive", maxActive);
                orbitMinDays = (float) cfg.optDouble("orbitMinDays", orbitMinDays);
                orbitMaxDays = (float) cfg.optDouble("orbitMaxDays", orbitMaxDays);
                travelTimeoutDays = (float) cfg.optDouble(
                        "travelTimeoutDays", travelTimeoutDays);
                departTimeoutDays = (float) cfg.optDouble(
                        "departTimeoutDays", departTimeoutDays);
                scorePerRelief = (float) cfg.optDouble("scorePerRelief", scorePerRelief);

                ExileReliefShipment.DURATION_DAYS = (float) cfg.optDouble(
                        "conditionDays", ExileReliefShipment.DURATION_DAYS);
                ExileReliefShipment.STABILITY_BONUS = (float) cfg.optDouble(
                        "stabilityBonus", ExileReliefShipment.STABILITY_BONUS);
                ExileReliefShipment.FOOD_BONUS = cfg.optInt(
                        "foodBonus", ExileReliefShipment.FOOD_BONUS);
                ExileReliefShipment.SUPPLIES_BONUS = cfg.optInt(
                        "suppliesBonus", ExileReliefShipment.SUPPLIES_BONUS);
                ExileReliefShipment.CREW_PENALTY = cfg.optInt(
                        "crewPenalty", ExileReliefShipment.CREW_PENALTY);
                ExileReliefShipment.CREW_PRICE_MULT = (float) cfg.optDouble(
                        "crewPriceMult", ExileReliefShipment.CREW_PRICE_MULT);

                JSONObject fleet = cfg.optJSONObject("fleet");
                if (fleet != null) {
                    combatFp = fleet.optInt("combatFp", combatFp);
                    freighterFp = fleet.optInt("freighterFp", freighterFp);
                    tankerFp = fleet.optInt("tankerFp", tankerFp);
                    transportFp = fleet.optInt("transportFp", transportFp);
                }

                JSONArray enclaves = cfg.optJSONArray("enclaveEntityIds");
                if (enclaves != null && enclaves.length() > 0) {
                    enclaveEntityIds = new ArrayList();
                    for (int i = 0; i < enclaves.length(); i++) {
                        enclaveEntityIds.add(enclaves.getString(i));
                    }
                }
                JSONArray sources = cfg.optJSONArray("sourceEntityIds");
                if (sources != null && sources.length() > 0) {
                    sourceEntityIds = new ArrayList();
                    for (int i = 0; i < sources.length(); i++) {
                        sourceEntityIds.add(sources.getString(i));
                    }
                }
            }
        } catch (Exception e) {
            Global.getLogger(AshenReliefManager.class).warn(
                    "Ashen relief config load failed; using code defaults", e);
        }

        if (timeScale != 1f) {
            cadenceDays *= timeScale;
            offPhaseCadenceDays *= timeScale;
            orbitMinDays *= timeScale;
            orbitMaxDays *= timeScale;
            ExileReliefShipment.DURATION_DAYS *= timeScale;
            // travelTimeoutDays and departTimeoutDays are NOT scaled: they bound real fleet
            // travel, which is physical distance at real burn and does not compress (§9.1).
        }
    }

    protected void applyDefaults() {
        enclaveEntityIds = new ArrayList();
        enclaveEntityIds.add("corvus_abandoned_station");
        enclaveEntityIds.add("mairaath_abandoned_station1");
        enclaveEntityIds.add("yma_abandoned_station");
        enclaveEntityIds.add("maxios");

        sourceEntityIds = new ArrayList();
        sourceEntityIds.add("exileashen_penitent");
        sourceEntityIds.add("exileashen_steadfast");
        sourceEntityIds.add("exileashen_command_bastion");
        sourceEntityIds.add("exileashen_silent_watch");
    }
}
