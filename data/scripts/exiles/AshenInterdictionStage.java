package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.marketconditions.ExileCrusadeHarassment;

/**
 * Ashen Crusade INTERDICTION action stage -- PASS 4 of docs/EXILES_ASHEN_CRUSADE_WAR.md §6.1.
 * Presence-only, modelled on {@link BlackFleetCommerceRaidStage}: fleets enter the target
 * system and work its approaches for {@code presenceDays}, never touching a planet
 * ({@link #canRaid} is always false, so the raid AI never issues a colony action). Hold the
 * presence and every Tri-Tachyon market in the system takes {@link ExileCrusadeHarassment};
 * get driven below the abort threshold and they withdraw and it lifts.
 *
 * <p><b>Target discipline (ruling J5) is mandatory and Tri-Tachyon-only.</b> Hybrasil holds a
 * pirate market (Donn) and an independent one (Cethlenn), and Ashen is HOSTILE to Pirates --
 * but per Sean, <i>the Crusade doesn't like pirates at all, but they aren't worth risking
 * their ships and crews over</i>. Only Tri-Tachyon markets are ever affected.
 *
 * <h2>The loiter lever -- war score decides how close they dare sit</h2>
 *
 * This is the one deliberate addition over a straight copy (ratified 2026-08-01), and it exists
 * because of a mechanism discovery: Omicron's dormant station complements at Port Tse and
 * Tibicena are woken by an op fleet closing within
 * {@code ttDefenseStationActivationRange} (1,500u) of the station -- <b>proximity, with no FP
 * check whatsoever</b>. {@link OmicronTriTachyonDefenseManager}'s FP thresholds (200 at the
 * outposts, 350 in Hybrasil) gate the separate, larger STRATEGIC response, not the sleepers.
 *
 * <p>So spawn FP alone could never produce the escalation Sean asked for. What controls it is
 * where the raiders park:
 *
 * <ul>
 * <li><b>Early war</b> (score below {@code boldWarScore}) -- loiter at the system centre,
 *     thousands of units off the markets. No Omicron of any kind wakes. Harassment lands,
 *     Tri-Tachyon's own patrols contest it, and that is the whole fight. (Only on screen: off
 *     screen the abstract routes take no losses, so the presence always runs its full window --
 *     bug sweep ASH-7, 2026-10-06; whether to add an off-screen resolve is an open ruling.)</li>
 * <li><b>Heating up</b> (score at or above it) -- loiter on the target market itself, inside
 *     the activation radius, waking that station's complement. By then {@code scoreFpMult} has
 *     also pushed spawn FP toward the 200 strategic threshold, so a second tier arms behind it.</li>
 * <li><b>Never Culann's detachment</b> -- interdiction FP tops out well under the 350 Hybrasil
 *     threshold, reserving that confrontation for the Phase-4 strike and for Black Fleet
 *     assaults (350-500 FP), which already trip it with no new code.</li>
 * </ul>
 *
 * <p>It reads in fiction as well as it plays: the Crusade begins by working the outer lanes and
 * grows bold enough to sit on Tri-Tachyon's docks, which is exactly where Phase 3's blockade
 * picks up.
 *
 * <p><b>Latched at arrival, not at launch, and not re-evaluated mid-presence.</b> Vanilla calls
 * {@link #updateRoutes} exactly once, from {@code ActionStage.notifyStarted()} -- verified in
 * the API sources, and the reason this class does not promise mid-op escalation. Arrival is the
 * better of the two moments anyway (it uses the war score at the time the raiders actually
 * show up, not the one at the time they were ordered out), and each new raid re-evaluates, so
 * the campaign-scale ladder Sean asked for still lands. Re-calling {@code updateRoutes} from
 * {@code updateStatus} to flip the token mid-presence was considered and rejected: resetting
 * routes mid-stage is not a path vanilla exercises, and the gain -- a single ~30-day op that
 * happens to cross the threshold while in progress -- does not justify leaving the proven
 * lifecycle seam (CLAUDE.md priority 1).
 */
public class AshenInterdictionStage extends PirateRaidActionStage {

    /** Day of the presence at which Crusade Harassment lands -- the raiders need a few days
     *  on the lanes before shippers notice. Mirrors the Black Fleet's ratified day 5. */
    public static float CONDITION_APPLY_DAY = 5f;

    /** Keep Tri-Tachyon stations on each raider's do-not-attack list (see
     *  {@link #suppressStationTargeting}). Config-toggleable so the behaviour can be compared
     *  against vanilla's during tuning. */
    public static boolean suppressStationAttacks = true;
    /** Refresh horizon for the do-not-attack entries -- comfortably longer than the stage tick,
     *  short enough that nothing lingers once the op is over and its fleets despawn. */
    public static float DO_NOT_ATTACK_DAYS = 5f;

    protected float presenceDays;
    /** War score at or above which raiders loiter ON the target market instead of the system
     *  centre. Ratified 0.25 (Sean, 2026-08-01); config-driven. */
    protected float boldWarScore = 0.25f;
    /** The market they press in on once bold, and the one whose faction pays the bounty. */
    protected String focusMarketId;
    protected boolean conditionsApplied = false;
    protected boolean conditionsRemoved = false;
    protected List affectedMarketIds = new ArrayList(); // String

    public AshenInterdictionStage(RaidIntel raid, StarSystemAPI target, float presenceDays,
                                  String focusMarketId, float boldWarScore) {
        super(raid, target);
        this.presenceDays = presenceDays;
        this.focusMarketId = focusMarketId;
        this.boldWarScore = boldWarScore;
        this.maxDays = presenceDays + 10f; // slack; success normally fires at presenceDays
    }

    /**
     * Optional explicit fleet name. Null (the default) leaves the engine's own composition alone,
     * which is what every ordinary interdiction raid wants.
     *
     * <p>Added for Pass 7's screening armada, which is a loitering op like any other but has to
     * read as the <b>"Knights of Gilead Holy Armada"</b>.
     */
    protected String fleetName = null;

    public void setFleetName(String fleetName) {
        this.fleetName = fleetName;
    }

    /**
     * Suppress {@link ExileCrusadeHarassment} on this op. Set only on Pass 7's screening armada
     * (Sean, 2026-08-15): it is a military body, and with the Phase-4 sweep applying the same
     * condition to the same two Hybrasil markets, sharing it would reproduce the Pass-4 collision
     * where the first op to withdraw lifts the condition out from under the other.
     * {@code hasLiveOpIn()} guards that for interdiction, but Phase 4 runs several ops in one
     * system by design.
     *
     * <p><b>Phrased as "suppress = false" rather than "apply = true" for SAVE SAFETY.</b> XStream
     * allocates instances without running field initializers, so a field absent from an older
     * save's XML comes back as the JVM default -- {@code false} for a boolean. Written the natural
     * way round, every in-flight interdiction raid in an existing save would have silently stopped
     * applying harassment for the rest of its life. Inverted, the JVM default IS the old
     * behaviour, and the question disappears rather than needing a migration.
     */
    protected boolean suppressHarassment = false;

    public void setApplyHarassment(boolean applyHarassment) {
        this.suppressHarassment = !applyHarassment;
    }

    /**
     * Apply {@link #fleetName}, if one was set.
     *
     * <p>Done over the route list on the status tick rather than at fleet creation, because
     * {@code AssembleStage}'s creation seam is not in the shipped API sources and routes spawn
     * their fleets lazily. Same approach as {@code AshenRehearsalStage.nameFleets()}.
     *
     * <p>{@code setNoFactionInName(true)} means the string is the <b>complete</b> name -- nothing
     * is prepended. That is deliberate here (Sean, 2026-08-13): the Knights are the Crusade's
     * founding splinter and are meant to stand apart from "Ashen Crusade ..." fleets.
     */
    protected void nameFleets() {
        if (fleetName == null) return;
        List routeList = getRoutes();
        if (routeList == null) return;
        for (int i = 0; i < routeList.size(); i++) {
            RouteData route = (RouteData) routeList.get(i);
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null) continue;
            if (fleetName.equals(fleet.getName())) continue;
            fleet.setName(fleetName);
            fleet.setNoFactionInName(true);
        }
    }

    /** NEVER: interdiction raiders do not touch planets. Also stops the raid AI from issuing
     *  any colony-approach order, so fleets just follow their loiter route. */
    @Override
    public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
        return false;
    }

    /** True once the war has matured enough for the Crusade to press onto the docks. */
    public boolean isBold() {
        AshenWarDirector director = AshenWarDirector.get();
        if (director == null) return false;
        return director.getWarScore() >= boldWarScore;
    }

    /** The loiter token: system centre early, the focus market once bold. Falls back to the
     *  centre whenever the market is gone, so a decivilised or transferred target cannot
     *  strand the routes. */
    protected SectorEntityToken getLoiterToken() {
        if (!isBold()) return system.getCenter();
        MarketAPI focus = focusMarketId != null
                ? Global.getSector().getEconomy().getMarket(focusMarketId) : null;
        if (focus == null || focus.getPrimaryEntity() == null) return system.getCenter();
        if (focus.getPrimaryEntity().getContainingLocation() != system) return system.getCenter();
        return focus.getPrimaryEntity();
    }

    /** No ground-raid steps and no per-colony staging: send every route to the loiter token
     *  and let the vanilla fleet AI handle whatever finds them. Overrides the vanilla version
     *  wholesale -- no steps are ever created, and vanilla's playerTargeted
     *  {@code sendEnteredSystemUpdate()} is dropped because {@link AshenRaidIntel} reveals
     *  itself on reaching this stage (the Black Fleet's playtested arrangement).
     *
     *  <p>Called ONCE, from {@code ActionStage.notifyStarted()} -- see the class javadoc. */
    @Override
    protected void updateRoutes() {
        resetRoutes();

        SectorEntityToken loiter = getLoiterToken();
        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            route.addSegment(new RouteManager.RouteSegment(presenceDays + 5f, loiter));
        }
    }

    /** Harassment lands on day CONDITION_APPLY_DAY and lifts when the raiders withdraw --
     *  whether that is the planned end of the presence window (success) or an early
     *  withdrawal below the abort threshold. Trade recovers the moment they leave. */
    @Override
    protected void updateStatus() {
        abortIfNeededBasedOnFP(true); // early withdrawal below abortFraction -> FAILURE

        if (status == RaidStageStatus.ONGOING) {
            nameFleets();
            suppressStationTargeting();
            // conditionsApplied stays false when harassment is suppressed, so removeConditions()
            // below is never reached either -- no half-state to clean up.
            if (!suppressHarassment && elapsed >= CONDITION_APPLY_DAY && !conditionsApplied) {
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

    /**
     * Keep raiders off Tri-Tachyon's orbital stations while leaving them free to hunt
     * Tri-Tachyon FLEETS -- Sean's stated ideal, and §6.1's "targeting hostile ships".
     *
     * <p><b>Why this and not an FP or personality tune.</b> Live test 2026-08-02: raiders
     * dog-piled Port Tse and knocked it offline twice. Decompiling
     * {@code campaign.ai.TacticalModule.hasEnoughStuffAround} shows the nearby-fleet tally that
     * gates target selection opens with {@code !var18.isStationMode()} -- <b>a station counts
     * toward neither the friendly nor the hostile total</b>. With a station as the only hostile
     * present the test collapses to {@code ownFP + friendlyFP > 0}, which is trivially true for
     * any fleet with one companion nearby. <i>No FP balance can deter this, because the
     * station's strength is never on the scale.</i> Nor is the loiter point enough: Omicron woke
     * at Port Tse with every raid at {@code bold=false}, i.e. raiders crossed ~5,700u from the
     * system centre on their own (tactical pursuit of Tri-Tachyon traffic, which converges on
     * Tri-Tachyon markets).
     *
     * <p>The precise lever is {@code StrategicModule.isAllowedToEngage}, whose first line is a
     * per-fleet {@code doNotAttack} set exposed as public API through
     * {@link com.fs.starfarer.api.campaign.ai.StrategicModulePlugin#getDoNotAttack()}. Listing
     * the station there makes {@code isOkToPursue} false for that target only, which sends the
     * targeting branch down vanilla's avoid path -- and because the entity is station-mode, that
     * path actively steers away from it. Everything else stays a valid target.
     *
     * <p>Entries are refreshed on the stage tick rather than set once: fleets spawn throughout
     * the assemble stage, and {@link com.fs.starfarer.api.util.TimeoutTracker} entries expire.
     * Cost is a handful of map writes over at most a few fleets and one or two markets.
     */
    protected void suppressStationTargeting() {
        if (!suppressStationAttacks) return;

        // EVERY station in the system, not just Tri-Tachyon's (fixed 2026-08-10).
        //
        // Sean's live test: interdiction raiders engaged and disabled a PIRATE station as well as
        // Tri-Tachyon's. The pirate case was never suppressed at all -- this loop filtered on
        // TRITACHYON, so Kanta's station in Magec was simply not on the do-not-attack list and
        // read as a free target to any raider with a companion nearby (sec 14.4 Finding C: vanilla's
        // target-selection tally skips station-mode fleets, so station strength deters nothing).
        //
        // Interdiction raiders are a COMMERCE tier -- they hold lanes and stop hulls; they have no
        // business fighting any station, whoever owns it. Widening this is also what keeps the
        // Crusade out of a war with Kanta that no one designed.
        List stations = new ArrayList(); // CampaignFleetAPI
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
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

    /** Tri-Tachyon markets only (ruling J5). Player colonies are code-exempt by convention. */
    protected void applyConditions() {
        conditionsApplied = true;
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market.isHidden()) continue;
            if (market.isPlayerOwned()) continue;
            if (!Factions.TRITACHYON.equals(market.getFactionId())) continue;
            ExileCrusadeHarassment.applyTo(market);
            ExileUnrestGuard.reportExilesPressure(market,
                    ExileEmergencyMeasuresConfig.pressureDays(
                            ExileCrusadeHarassment.DURATION_DAYS, 0));
            affectedMarketIds.add(market.getId());
        }
        Global.getLogger(AshenInterdictionStage.class).info(
                "Ashen interdiction raid: Crusade Harassment applied to "
                        + affectedMarketIds.size() + " market(s) in " + system.getBaseName()
                        + " (bold=" + isBold() + ")");
    }

    protected void removeConditions() {
        conditionsRemoved = true;
        for (int i = 0; i < affectedMarketIds.size(); i++) {
            MarketAPI market = Global.getSector().getEconomy()
                    .getMarket((String) affectedMarketIds.get(i));
            ExileCrusadeHarassment.removeFrom(market);
        }
        Global.getLogger(AshenInterdictionStage.class).info(
                "Ashen interdiction raid: raiders withdrew from " + system.getBaseName()
                        + " (" + status + "), Crusade Harassment lifted from "
                        + affectedMarketIds.size() + " market(s)");
    }

    /** Never invoked (canRaid is false); kept as a harmless safety net. */
    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (market != null && Factions.TRITACHYON.equals(market.getFactionId())) {
            ExileCrusadeHarassment.applyTo(market);
            ExileUnrestGuard.reportExilesPressure(market,
                    ExileEmergencyMeasuresConfig.pressureDays(
                            ExileCrusadeHarassment.DURATION_DAYS, 0));
        }
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        float opad = 10f;

        if (status == RaidStageStatus.FAILURE) {
            info.addPara("The Crusade raiders have been driven out of the "
                    + intel.getSystem().getNameWithLowercaseType() + ". The raid is over.", opad);
        } else if (status == RaidStageStatus.SUCCESS) {
            info.addPara("The Crusade has held the approaches to the "
                    + intel.getSystem().getNameWithLowercaseType()
                    + " for weeks. Tri-Tachyon shipping into the system has been strangled.", opad);
        } else if (curr == index) {
            info.addPara("Crusade raiders are stopping shipping in the "
                    + intel.getSystem().getNameWithLowercaseType() + ".", opad);
        }
    }

    // --- Crusade flavor for fleet action text ---

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
