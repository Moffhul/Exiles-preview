package data.scripts.exiles;

import java.util.List;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Midan's Strike Force forms up INSIDE THE TARGET SYSTEM, out of sight (Pass 7 step 7d-2; sneak
 * attack ruled 2026-10-30, superseding the 2026-10-14 hyperspace assembly off the anchor). The
 * gathering point is a fixed token the module picks on the bearing farthest from the jump points,
 * the star and the player. The anchor market is still the routes' nominal source (vanilla needs
 * one), but no fleet ever appears there: the Black Fleet is hostile to the League.
 *
 * <p>Unlike vanilla's assemble stage, which carves a raid's FP into random patrol-sized fleets,
 * this adds exactly one route per fleet -- the Ajax's main fleet and 0-2 escorts -- each typed so
 * {@link ExileHegLeagueMidanStrikeIntel#createFleet} composes the right roster. The source is never
 * reported, so the intel draws no map arrow either.
 *
 * <p>Verified from vanilla source (2026-10-30): a route fleet is placed in the location of its
 * current segment ({@code RouteFleetAssignmentAI.giveInitialAssignments}); the strike intel's
 * {@code spawnFleet} adds it to that location directly.
 */
public class ExileHegLeagueMidanAssembleStage extends AssembleStage {

    protected SectorEntityToken returnPoint;
    protected int escorts;
    protected float mainFp;
    protected float escortFp;
    protected float mainStrength;
    protected float escortStrength;
    protected float prepDays;
    protected boolean routesAdded = false;

    public ExileHegLeagueMidanAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint,
                                            MarketAPI anchor, SectorEntityToken returnPoint,
                                            int escorts, float mainFp, float escortFp,
                                            float mainStrength, float escortStrength,
                                            float prepDays) {
        super(raid, gatheringPoint);
        addSource(anchor);
        this.returnPoint = returnPoint;
        this.escorts = Math.max(0, escorts);
        this.mainFp = mainFp;
        this.escortFp = escortFp;
        this.mainStrength = mainStrength;
        this.escortStrength = escortStrength;
        this.prepDays = Math.max(0.5f, prepDays);
        // Only read by vanilla's description and FP bookkeeping; the routes below are the real
        // fleets. Zeroed once they exist (updateStatus waits on it).
        setSpawnFP(mainFp + this.escorts * escortFp);
    }

    @Override
    public boolean isSourceKnown() {
        return false;
    }

    @Override
    protected void addRoutesAsNeeded(float amount) {
        if (routesAdded) return;
        routesAdded = true;
        spawnFP = 0f;

        MarketAPI market = getSources().isEmpty() ? null : (MarketAPI) getSources().get(0);
        if (market == null) {
            status = RaidStageStatus.FAILURE;
            return;
        }
        addRoute(market, ExileHegLeagueMidanStrikeIntel.MAIN_TYPE, mainFp, mainStrength);
        for (int i = 0; i < escorts; i++) {
            addRoute(market, ExileHegLeagueMidanStrikeIntel.ESCORT_TYPE, escortFp, escortStrength);
        }
        maxDays = Math.max(maxDays, prepDays + 1f);
        // FOUND IN THE 2026-10-30 SNEAK-ATTACK TEST: vanilla counts a LIVE fleet within 2000 of the
        // gathering point as assembled at once, so with the player in the system the fleets
        // spawned on the staging point and skipped their prep. Vanilla's own minimum-days gate
        // (default 3) holds the stage for the full prep, live or abstract.
        minDays = prepDays;
    }

    /**
     * FOUND IN THE 7e-1 TEST (2026-11-01): vanilla requires EVERY live fleet within 2000 units of
     * the gathering point at the same moment. Four fleets parked inside a Hegemony system (Sphinx)
     * were drawn into fight after fight with its patrols and never stood together again, so the
     * stage sat for 66 days (vanilla's 60 "extra days") and the strike failed without reaching its
     * approach. The staging point is already inside the target system, so "assembled" means: the
     * prep is over and the fleets are IN THE SYSTEM (vanilla's {@code requireNearTarget} false). The
     * approach stage then requires them near the target, which brings any stray fleet back in.
     * Abstract routes still need to reach their WAIT segment, exactly as before.
     */
    @Override
    protected void updateStatus() {
        if (spawnFP > 0) return;
        if (minDays > 0) return;
        abortIfNeededBasedOnFP(true);
        if (status != RaidStageStatus.ONGOING) return;
        updateStatusBasedOnReaching(gatheringPoint, true, false);
    }

    protected void addRoute(MarketAPI market, String type, float fp, float strength) {
        // League faction on the route itself: WarSimScript counts abstract routes by the ROUTE's
        // faction, which would otherwise default to the anchor market's (the Black Fleet's).
        OptionalFleetData extra = new OptionalFleetData(market, Factions.PERSEAN);
        extra.fleetType = type;
        extra.fp = Float.valueOf(fp);
        extra.strength = Float.valueOf(strength);
        RouteData route = RouteManager.getInstance().addRoute(intel.getRouteSourceId(), market,
                Long.valueOf(Misc.genRandomSeed()), extra, intel, null);
        route.addSegment(new RouteSegment(prepDays, gatheringPoint, (Object) PREP_STAGE));
        route.addSegment(new RouteSegment(1000f, gatheringPoint, (Object) WAIT_STAGE));
    }

    @Override
    public void giveReturnOrdersToStragglers(List stragglers) {
        ExileHegLeagueMidanStrikeIntel.sendHome(this, stragglers, returnPoint);
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        if (status == RaidStageStatus.FAILURE) {
            info.addPara("The strike force failed to assemble. The operation is over.", 10f);
        } else if (curr == index) {
            info.addPara("The strike force is massing at the edge of the system.", 10f);
        }
    }
}
