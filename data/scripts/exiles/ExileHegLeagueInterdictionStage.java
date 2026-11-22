package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.marketconditions.ExileHegLeagueInterdiction;

/**
 * Hegemony-Persean League INTERDICTION action stage -- PASS 2 of
 * docs/EXILES_HEG_LEAGUE_WAR.md sec 4.1. <b>The third port of the presence-raid pattern</b>
 * ({@code BlackFleetCommerceRaidStage} -&gt; {@link AshenInterdictionStage} -&gt; here), which the
 * sec 13 reuse map measured at ~80% reuse on the second port.
 *
 * <p>Presence-only: the fleets enter the target system and work its approaches for
 * {@code presenceDays}, never touching a planet ({@link #canRaid} is always false, so the raid AI
 * never issues a colony action). Hold the presence and every market the raided side owns in that
 * system takes {@link ExileHegLeagueInterdiction}; get driven below the abort threshold and they
 * withdraw and it lifts.
 *
 * <p><b>What was deliberately NOT ported from the Ashen version.</b> Its "bold war score" loiter
 * lever exists to control whether raiders wake Omicron's dormant station complements at Port Tse
 * and Tibicena -- a purely Ashen-theatre mechanism with no counterpart here. This stage loiters at
 * the system centre by default; {@code loiterOnFocusMarket} is exposed in config for tuning, off,
 * because pressing onto a defended Core world's docks is the behaviour that produced the Ashen
 * station-suicide problem.
 *
 * <p><b>What WAS ported verbatim, and must stay.</b> {@link #suppressStationTargeting()}. The
 * Ashen live test (2026-08-02, widened 2026-08-10) established that vanilla's target-selection
 * tally in {@code campaign.ai.TacticalModule.hasEnoughStuffAround} opens with
 * {@code !isStationMode()} -- <b>a station counts toward neither the friendly nor the hostile
 * total</b>, so no FP balance can deter raiders from charging one. Interdiction raiders are a
 * commerce tier; they have no business fighting any station, whoever owns it. In this war that
 * matters more than it did for Ashen, because the target pool is the whole non-exempt Core and
 * nearly every market in it carries an orbital station.
 *
 * <p><b>Both directions.</b> Unlike the sibling stages there is no fixed aggressor: the same class
 * serves Hegemony raiders in League space and League raiders in Hegemony space, so the acting and
 * raided faction ids are constructor parameters and every piece of text is composed from them.
 */
public class ExileHegLeagueInterdictionStage extends PirateRaidActionStage {

    /** Day of the presence at which the condition lands -- the raiders need a few days on the
     *  lanes before shippers notice. Mirrors the ratified sibling value. Config-driven. */
    public static float CONDITION_APPLY_DAY = 5f;

    /** Keep raiders off every orbital station in the system (see
     *  {@link #suppressStationTargeting}). Config-toggleable so the behaviour can be compared
     *  against vanilla's during tuning. */
    public static boolean suppressStationAttacks = true;

    /** Instance hook over the static, so a subclass can carry a DIFFERENT setting without the two
     *  managers overwriting one shared flag (Pass 6 ruling 2026-09-20: the planetary squeeze
     *  fights the station, interdiction never does). Interdiction reads the static unchanged. */
    protected boolean isStationSuppressionEnabled() {
        return suppressStationAttacks;
    }

    /**
     * The apply day this stage actually uses, as an overridable accessor.
     *
     * <p><b>Added by Pass 6 and BEHAVIOUR-NEUTRAL here</b>: it returns
     * {@link #CONDITION_APPLY_DAY}, exactly what {@link #updateStatus()} read directly before.
     * It exists because {@code ExileHegLeagueSqueezeStage} subclasses this stage and needs its
     * OWN apply day from {@code planetaryBlockade.conditionApplyDay}, and a static field cannot
     * be overridden -- a subclass static would simply shadow this one while the parent's
     * {@code updateStatus()} went on reading the parent's, silently. One accessor is cheaper and
     * far harder to get wrong than duplicating the whole status method in the subclass.
     */
    protected float getConditionApplyDay() {
        return CONDITION_APPLY_DAY;
    }
    /** Refresh horizon for the do-not-attack entries -- longer than the stage tick, short enough
     *  that nothing lingers once the op is over and its fleets despawn. */
    public static float DO_NOT_ATTACK_DAYS = 5f;

    protected float presenceDays;
    /** The side conducting the interdiction. */
    protected String actorFactionId;
    /** The side being interdicted -- the only faction whose markets take the condition. */
    protected String victimFactionId;
    /** The market named in the intel and, if {@code loiterOnFocusMarket}, the loiter token. */
    protected String focusMarketId;
    /** False (default) = loiter at the system centre; true = sit on the focus market's orbit. */
    protected boolean loiterOnFocusMarket = false;

    protected boolean conditionsApplied = false;
    protected boolean conditionsRemoved = false;
    protected List affectedMarketIds = new ArrayList(); // String

    public ExileHegLeagueInterdictionStage(RaidIntel raid, StarSystemAPI target,
                                           float presenceDays, String actorFactionId,
                                           String victimFactionId, String focusMarketId,
                                           boolean loiterOnFocusMarket) {
        super(raid, target);
        this.presenceDays = presenceDays;
        this.actorFactionId = actorFactionId;
        this.victimFactionId = victimFactionId;
        this.focusMarketId = focusMarketId;
        this.loiterOnFocusMarket = loiterOnFocusMarket;
        this.maxDays = presenceDays + 10f; // slack; success normally fires at presenceDays
    }

    /** NEVER: interdiction raiders do not touch planets. Also stops the raid AI issuing any
     *  colony-approach order, so fleets just follow their loiter route. */
    @Override
    public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
        return false;
    }

    /** System centre by default; the focus market only if config asks for it. Falls back to the
     *  centre whenever the market is gone or has moved, so a market that changes hands or leaves
     *  the economy mid-op cannot strand the routes. */
    protected SectorEntityToken getLoiterToken() {
        if (!loiterOnFocusMarket) return system.getCenter();
        MarketAPI focus = focusMarketId != null
                ? Global.getSector().getEconomy().getMarket(focusMarketId) : null;
        if (focus == null || focus.getPrimaryEntity() == null) return system.getCenter();
        if (focus.getPrimaryEntity().getContainingLocation() != system) return system.getCenter();
        return focus.getPrimaryEntity();
    }

    /**
     * No ground-raid steps and no per-colony staging: send every route to the loiter token and
     * let the vanilla fleet AI handle whatever finds them. Overrides vanilla's version wholesale
     * -- no steps are ever created.
     *
     * <p>Called ONCE, from {@code ActionStage.notifyStarted()} (verified in the API sources during
     * the Ashen port), which is why nothing here promises mid-presence re-evaluation.
     */
    @Override
    protected void updateRoutes() {
        resetRoutes();

        SectorEntityToken loiter = getLoiterToken();
        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            route.addSegment(new RouteManager.RouteSegment(presenceDays + 5f, loiter));
        }
        logArrival();
    }

    /**
     * THE ARRIVAL MEASUREMENT LINE (Run 4 read, 2026-09-22), the strike stage's twin. Three of six
     * squeezes were driven off by the FP abort and nothing recorded what the cordon actually met,
     * so launch-time feasibility could not be compared with arrival. Same vanilla sums the launch
     * gate uses, read once from this one-shot hook; the station term is the subclass's to supply.
     */
    protected void logArrival() {
        if (system == null || intel == null) return;
        FactionAPI actor = intel.getFaction();
        float str = WarSimScript.getFactionStrength(actor, system);
        float enemy = WarSimScript.getEnemyStrength(actor, system);
        float station = getArrivalStationStrength();
        Global.getLogger(getClass()).info(
                "Exiles Heg-League " + getOpNoun() + " ARRIVED at " + system.getBaseName()
                        + ": group strength " + (int) str
                        + " vs defense " + (int) (enemy + station)
                        + " (enemy=" + (int) enemy + " + station=" + (int) station + ")"
                        + " | presence window " + (int) presenceDays + "d"
                        + " (actor=" + actorFactionId + ", victim=" + victimFactionId + ")");
    }

    /** Station strength counted against the group on arrival; an interdiction hunts shipping and
     *  never fights a station, so none. The squeeze overrides with its anchor's. */
    protected float getArrivalStationStrength() {
        return 0f;
    }

    /** Log noun for the arrival line. */
    protected String getOpNoun() {
        return "interdiction";
    }

    /** The condition lands on day CONDITION_APPLY_DAY and lifts when the raiders withdraw --
     *  whether that is the planned end of the presence window (success) or an early withdrawal
     *  below the abort threshold. Trade recovers the moment they leave. */
    @Override
    protected void updateStatus() {
        abortIfNeededBasedOnFP(true); // early withdrawal below abortFraction -> FAILURE

        if (status == RaidStageStatus.ONGOING) {
            suppressStationTargeting();
            keepOffObjectivesUnderStations();
            refreshPlayerStops();
            if (elapsed >= getConditionApplyDay() && !conditionsApplied) {
                applyConditions();
            }
            if (elapsed >= presenceDays) {
                status = RaidStageStatus.SUCCESS;
            }
        }

        if (status != RaidStageStatus.ONGOING && conditionsApplied && !conditionsRemoved) {
            removeConditions();
        }
    }

    // ------------------------------------------------------------------
    // Objectives under an enemy station (Sean's ruling, 2026-11-18; EXILES_PASS8_PLAN.md sec 15)
    // ------------------------------------------------------------------

    /** {@code interdiction.objectiveStationKeepOut}: an objective this close to a hostile market
     *  that has a station is off limits to the raiders' side. 0 = off (vanilla). */
    public static float OBJECTIVE_KEEP_OUT = 3000f;
    /** Days between checks. Orbits move slowly; half a day is ample. */
    public static float OBJECTIVE_CHECK_DAYS = 0.5f;
    /** How long each check blocks vanilla's order for. Longer than the check interval, short
     *  enough that nothing lingers once the op is over or the objective drifts clear. */
    public static float OBJECTIVE_NO_FIGHT_DAYS = 3f;

    /** {@code elapsed} at which the objectives are next checked. Transient ON PURPOSE (not saved;
     *  0 after a load, so the first tick after a load checks at once). */
    protected transient float objectiveCheckAt = 0f;
    /** Ids of objectives currently kept out, for the START / END log lines only. Transient. */
    protected transient List keptOutObjectiveIds = null; // String
    /** One OBJECTIVES census line per op per game load. Transient. */
    protected transient boolean objectivesLogged = false;

    /** Interdiction only. The squeeze overrides this to false: it holds one market's orbit and
     *  fights that market's station by design, and was not part of this ruling. */
    protected boolean isObjectiveKeepOutEnabled() {
        return true;
    }

    /**
     * Stop vanilla's war simulation sending the raiders to an objective that sits under an enemy
     * station's guns.
     *
     * <p><b>The problem (measured at Tyle, 2026-11-18).</b> This stage replaces vanilla's raid
     * targeting, so the raiders hold no military-response order and are not flagged busy.
     * {@code WarSimScript.processStarSystem} therefore treats them as free: for every objective a
     * hostile faction owns, the strongest faction in the system gets a
     * {@code MilitaryResponseScript} sending 40% of its fleet points there. The launch gate only
     * launches a group stronger than the defenders' fleets, so that faction is always ours. At
     * Tyle the Canaria Relay is 1,400 units from Madeira: all five raiders were ordered to it from
     * 10,000 units out, chasing nothing, and died beside Madeira's station.
     *
     * <p><b>The ruling.</b> Raiders SHOULD still take relays and arrays -- vanilla raids do, the
     * sibling directors do, and it is a sensible tactic (Sean). So nothing is switched off
     * wholesale. Only an objective currently within {@link #OBJECTIVE_KEEP_OUT} of a hostile
     * market with a station is blocked, and the distance is re-read on every check because orbits
     * carry objectives toward and away from markets.
     *
     * <p><b>The lever.</b> Vanilla's own: its per-objective, per-faction "no fighting" timeout
     * (what {@code WarSimScript.setNoFightingForObjective} writes). The timeout is SET here rather
     * than added through that method, because {@code TimeoutTracker.add} accumulates and a
     * renewing tick would bank weeks. Any order already issued is withdrawn, and this op's own
     * fleets are released from it (a withdrawn script does not release its responders).
     *
     * <p><b>Scope.</b> The timeout is per faction, so it also binds a strike or blockade force of
     * the same side in this system for as long as this interdiction runs. Accepted.
     *
     * <p><b>NOT covered, on purpose:</b> vanilla's opportunistic capture
     * ({@code BaseAssignmentAI.checkCapture}), which sends a raider that is already within 2,000
     * units of a hostile objective to take it. One change at a time; measure first.
     */
    protected void keepOffObjectivesUnderStations() {
        if (!isObjectiveKeepOutEnabled() || OBJECTIVE_KEEP_OUT <= 0f) return;
        if (elapsed < objectiveCheckAt) return;
        objectiveCheckAt = elapsed + OBJECTIVE_CHECK_DAYS;
        if (system == null || intel == null) return;
        FactionAPI actor = intel.getFaction();
        if (actor == null) return;

        if (keptOutObjectiveIds == null) keptOutObjectiveIds = new ArrayList();
        StringBuilder census = objectivesLogged ? null : new StringBuilder();

        List objectives = system.getEntitiesWithTag(Tags.OBJECTIVE);
        for (int i = 0; i < objectives.size(); i++) {
            SectorEntityToken objective = (SectorEntityToken) objectives.get(i);
            FactionAPI owner = objective.getFaction();
            boolean ours = owner == actor;

            MarketAPI guard = ours ? null : nearestHostileStationMarket(objective, actor);
            float dist = guard != null ? Misc.getDistance(objective.getLocation(),
                    guard.getPrimaryEntity().getLocation()) : -1f;
            boolean keepOut = guard != null && dist <= OBJECTIVE_KEEP_OUT;

            String id = objective.getId();
            boolean was = keptOutObjectiveIds.contains(id);
            if (keepOut) {
                WarSimScript.removeFightOrdersFor(objective, actor);
                WarSimScript sim = WarSimScript.getInstance();
                if (sim != null && sim.getTimeouts() != null) {
                    // Vanilla's own id format (WarSimScript.getControlTimeoutId, protected).
                    sim.getTimeouts().set(actor.getId() + "_" + id, OBJECTIVE_NO_FIGHT_DAYS);
                }
                int released = releaseFleetsFrom(objective);
                if (!was) {
                    keptOutObjectiveIds.add(id);
                    Global.getLogger(ExileHegLeagueInterdictionStage.class).info(
                            "Exiles Heg-League " + getOpNoun() + " OBJECTIVE KEEP-OUT START: "
                                    + objective.getName() + " in " + system.getBaseName() + ", "
                                    + (int) dist + " from " + guard.getName() + " (limit "
                                    + (int) OBJECTIVE_KEEP_OUT + "), fleets released=" + released
                                    + " (actor=" + actorFactionId + ")");
                }
            } else if (was) {
                keptOutObjectiveIds.remove(id);
                Global.getLogger(ExileHegLeagueInterdictionStage.class).info(
                        "Exiles Heg-League " + getOpNoun() + " OBJECTIVE KEEP-OUT END: "
                                + objective.getName() + " in " + system.getBaseName()
                                // "ours" must be tested first: a captured objective also has
                                // no guard, and three of the first four END lines in play said
                                // "no enemy station left" when the objective had changed hands.
                                + (ours ? ", now held by the raiders' side"
                                        : guard != null ? ", now " + (int) dist + " from "
                                                + guard.getName() : ", no enemy station left")
                                + " (actor=" + actorFactionId + ")");
            }

            if (census != null) {
                if (census.length() > 0) census.append("; ");
                census.append(objective.getName()).append(" [")
                        .append(owner != null ? owner.getId() : "?").append("] ");
                if (ours) {
                    census.append("ours");
                } else if (guard == null) {
                    census.append("no enemy station -> open");
                } else {
                    census.append((int) dist).append(" from ").append(guard.getName())
                            .append(keepOut ? " -> KEPT OUT" : " -> open");
                }
            }
        }

        if (census != null) {
            objectivesLogged = true;
            Global.getLogger(ExileHegLeagueInterdictionStage.class).info(
                    "Exiles Heg-League " + getOpNoun() + " OBJECTIVES at " + system.getBaseName()
                            + " (keep-out " + (int) OBJECTIVE_KEEP_OUT + "): "
                            + (census.length() > 0 ? census.toString() : "none")
                            + " (actor=" + actorFactionId + ")");
        }
    }

    /** The nearest market hostile to the raiders that has an orbital station, or null. The
     *  station INDUSTRY is the test, not the station fleet: the fleet reads as absent after a
     *  load until the player visits (the 2026-10-26 engine trap). */
    protected MarketAPI nearestHostileStationMarket(SectorEntityToken objective,
                                                    FactionAPI actor) {
        MarketAPI best = null;
        float min = Float.MAX_VALUE;
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market.getPrimaryEntity() == null || market.getFaction() == null) continue;
            if (!market.getFaction().isHostileTo(actor)) continue;
            if (Misc.getStationIndustry(market) == null) continue;
            float dist = Misc.getDistance(objective.getLocation(),
                    market.getPrimaryEntity().getLocation());
            if (dist < min) {
                min = dist;
                best = market;
            }
        }
        return best;
    }

    /** Take this op's live fleets off a military-response order aimed at one objective. Mirrors
     *  vanilla's {@code MilitaryResponseScript.unrespond}, narrowed to that objective and to our
     *  own fleets. Returns how many fleets had such an order. */
    protected int releaseFleetsFrom(SectorEntityToken objective) {
        int released = 0;
        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null || !fleet.isAlive() || fleet.getAI() == null) continue;
            boolean had = false;
            List assignments = fleet.getAI().getAssignmentsCopy();
            for (int k = 0; k < assignments.size(); k++) {
                FleetAssignmentDataAPI order = (FleetAssignmentDataAPI) assignments.get(k);
                if (order.getTarget() != objective) continue;
                if (!MilitaryResponseScript.RESPONSE_ASSIGNMENT.equals(order.getCustom())) continue;
                fleet.getAI().removeAssignment(order);
                had = true;
            }
            if (had) {
                Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(),
                        MemFlags.FLEET_MILITARY_RESPONSE, objective.getId(), false, 0f);
                released++;
            }
        }
        return released;
    }

    /** How often the player-stop marks are renewed, in days. Well inside their 2-day expiry. */
    public static float STOP_REFRESH_DAYS = 0.5f;
    /** {@code elapsed} at which the marks are next renewed. Transient ON PURPOSE: it is not
     *  saved, comes back 0 after a load, and so the first tick after a load renews at once. */
    protected transient float stopRefreshAt = 0f;
    /** One STOP MARKS log line per op per game load. Transient for the same reason. */
    protected transient boolean stopMarksLogged = false;

    /**
     * PASS 8a-1: let vanilla's {@code HasslePlayerScript} send these fleets after the player. All
     * of the work, and the reasons, are in {@link ExileHegLeaguePlayerStop}. Runs only while the
     * stage is ONGOING, so the return leg is never marked.
     */
    protected void refreshPlayerStops() {
        boolean markNow = elapsed >= stopRefreshAt;
        if (markNow) stopRefreshAt = elapsed + STOP_REFRESH_DAYS;
        boolean marked = ExileHegLeaguePlayerStop.refresh(intel, system, getStopType(),
                getStopKind(), actorFactionId, markNow, !stopMarksLogged);
        if (marked) stopMarksLogged = true;
    }

    /** Vanilla hassle type; each has its own sector-wide cooldown. The squeeze overrides. */
    protected String getStopType() {
        return ExileHegLeaguePlayerStop.TYPE_INSPECT;
    }

    /** The dialogue's voice: interdiction raiders run a shakedown (ruled 2026-11-16). */
    protected String getStopKind() {
        return ExileHegLeaguePlayerStop.KIND_RAIDER;
    }

    /** True once the presence ran its full window -- the intel reads this on end to decide
     *  whether the op counts as a completed operation for the tension meter (sec 3.2.1 "Up"). */
    public boolean isPresenceCompleted() {
        return status == RaidStageStatus.SUCCESS;
    }

    /**
     * Keep raiders off EVERY orbital station in the system while leaving them free to hunt
     * hostile FLEETS.
     *
     * <p>Ported verbatim from {@link AshenInterdictionStage}, including the widening from
     * "the target faction's stations" to "every station" -- the Ashen playtest found raiders
     * engaging a pirate station that the narrower filter never listed. The lever is
     * {@code StrategicModule.isAllowedToEngage}, whose first line is a per-fleet
     * {@code doNotAttack} set exposed through
     * {@link com.fs.starfarer.api.campaign.ai.StrategicModulePlugin#getDoNotAttack()}: listing a
     * station makes {@code isOkToPursue} false for that target only, and because the entity is
     * station-mode the avoid path actively steers away from it.
     *
     * <p>Entries are refreshed on the stage tick rather than set once: fleets spawn throughout the
     * assemble stage, and {@link com.fs.starfarer.api.util.TimeoutTracker} entries expire.
     */
    protected void suppressStationTargeting() {
        if (!isStationSuppressionEnabled()) return;

        List stations = new ArrayList(); // CampaignFleetAPI
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (!isStationProtected(market)) continue;
            CampaignFleetAPI station = Misc.getStationFleet(market);
            if (station != null) stations.add(station);
        }
        if (stations.isEmpty()) return;

        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null || !fleet.isAlive()) continue;
            if (!(fleet.getAI() instanceof ModularFleetAIAPI)) continue;
            ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
            if (ai.getStrategicModule() == null) continue;
            for (int k = 0; k < stations.size(); k++) {
                ai.getStrategicModule().getDoNotAttack().add(
                        (CampaignFleetAPI) stations.get(k), DO_NOT_ATTACK_DAYS);
            }
        }
    }

    /** Per-market filter inside {@link #suppressStationTargeting()}: which markets' stations go on
     *  the do-not-attack list. Interdiction protects every one; the squeeze exempts its anchor. */
    protected boolean isStationProtected(MarketAPI market) {
        return true;
    }

    /**
     * The raided side's markets only. Player colonies are code-exempt by convention;
     * {@link ExileProtectedFactions} and the Battered guard are re-checked here rather than
     * trusted from launch time, because an op outlives its own target selection by weeks and a
     * market can go Battered or change hands while the raiders are in transit.
     */
    protected void applyConditions() {
        conditionsApplied = true;
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market.isHidden() || !market.isInEconomy()) continue;
            if (market.isPlayerOwned()) continue;
            if (ExileProtectedFactions.isProtected(market)) continue;
            if (victimFactionId == null || !victimFactionId.equals(market.getFactionId())) continue;
            if (ExileUnrestGuard.isBattered(market)) continue;
            if (ExileHegLeagueInterdictionManager.isExcludedMarket(market.getId())) continue;

            ExileHegLeagueInterdiction.applyTo(market, actorFactionId);
            ExileUnrestGuard.reportExilesPressure(market,
                    ExileEmergencyMeasuresConfig.pressureDays(
                            ExileHegLeagueInterdiction.DURATION_DAYS, 0));
            affectedMarketIds.add(market.getId());
        }
        Global.getLogger(ExileHegLeagueInterdictionStage.class).info(
                "Exiles Heg-League interdiction: condition applied to " + affectedMarketIds.size()
                        + " " + shortName(victimFactionId) + " market(s) in "
                        + system.getBaseName());
    }

    protected void removeConditions() {
        conditionsRemoved = true;
        for (int i = 0; i < affectedMarketIds.size(); i++) {
            MarketAPI market = Global.getSector().getEconomy()
                    .getMarket((String) affectedMarketIds.get(i));
            ExileHegLeagueInterdiction.removeFrom(market);
        }
        Global.getLogger(ExileHegLeagueInterdictionStage.class).info(
                "Exiles Heg-League interdiction: raiders withdrew from " + system.getBaseName()
                        + " (" + status + "), condition lifted from " + affectedMarketIds.size()
                        + " market(s)");
    }

    /** Never invoked (canRaid is false); kept as a harmless safety net, matching the sibling. */
    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (market == null) return;
        if (victimFactionId == null || !victimFactionId.equals(market.getFactionId())) return;
        if (ExileUnrestGuard.isBattered(market)) return;
        ExileHegLeagueInterdiction.applyTo(market, actorFactionId);
        ExileUnrestGuard.reportExilesPressure(market,
                ExileEmergencyMeasuresConfig.pressureDays(
                        ExileHegLeagueInterdiction.DURATION_DAYS, 0));
    }

    protected String shortName(String factionId) {
        FactionAPI faction = factionId != null
                ? Global.getSector().getFaction(factionId) : null;
        if (faction == null) return String.valueOf(factionId);
        return faction.getDisplayName();
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        float opad = 10f;
        String actor = shortName(actorFactionId);
        String victim = shortName(victimFactionId);

        if (status == RaidStageStatus.FAILURE) {
            info.addPara(actor + " forces have been driven out of the "
                    + intel.getSystem().getNameWithLowercaseType()
                    + ". The interdiction is over.", opad);
        } else if (status == RaidStageStatus.SUCCESS) {
            info.addPara(actor + " warships have held the approaches to the "
                    + intel.getSystem().getNameWithLowercaseType()
                    + " for weeks. " + victim + " shipping into the system has been strangled.",
                    opad);
        } else if (curr == index) {
            info.addPara(actor + " warships are stopping " + victim + " shipping in the "
                    + intel.getSystem().getNameWithLowercaseType() + ".", opad);
        }
    }

    // --- Fleet action text ---

    @Override
    public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
        return "interdicting shipping near " + market.getName();
    }

    @Override
    public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
        return "moving to interdict shipping near " + market.getName();
    }

    @Override
    public String getRaidInSystemText(CampaignFleetAPI fleet) {
        return "interdicting shipping";
    }

    @Override
    public String getRaidDefaultText(CampaignFleetAPI fleet) {
        return "interdicting shipping";
    }
}
