package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.util.Misc;

/** Retains route references after vanilla marks them as stragglers. */
public class ExileBlackFleetReturnStage extends ReturnStage {
    protected List returning;
    protected int[] results;
    protected float[] weights;
    protected float[] deliveredWeights;
    public ExileBlackFleetReturnStage(BlackFleetRaidIntel intel) { super(intel); }
    public void notifyStarted() {
        returning = new ArrayList(getRoutes());
        results = new int[returning.size()];
        weights = new float[returning.size()];
        deliveredWeights = new float[returning.size()];
        for (int i = 0; i < weights.length; i++) {
            RouteData route = (RouteData) returning.get(i);
            CampaignFleetAPI fleet = route.getActiveFleet();
            weights[i] = Math.max(1f, ExileBlackFleetLogistics.routeStrength(route));
            deliveredWeights[i] = weights[i];
        }
        ((BlackFleetRaidIntel) intel).prosperityReturn = this;
        super.notifyStarted();
        maxDays = ExileBlackFleetProsperity.get().config().returnTimeout;
    }
    protected boolean atHome(RouteData route, CampaignFleetAPI fleet) {
        SectorEntityToken home = route.getMarket().getPrimaryEntity();
        return home != null && fleet.getContainingLocation() == home.getContainingLocation()
                && Misc.getDistance(fleet, home) < 500f;
    }
    public void despawned(CampaignFleetAPI fleet, FleetDespawnReason reason) {
        if (returning == null) return;
        for (int i = 0; i < returning.size(); i++) {
            RouteData route = (RouteData) returning.get(i);
            if (route == null || route.getActiveFleet() != fleet || results[i] != 0) continue;
            if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE) results[i] = -1;
            else if (reason == FleetDespawnReason.PLAYER_FAR_AWAY) {
                // Preserve return-trip attrition when the engine switches back to abstract routes.
                deliveredWeights[i] = Math.min(deliveredWeights[i], ExileBlackFleetLogistics.routeStrength(route));
            }
            else if (reason == FleetDespawnReason.REACHED_DESTINATION && atHome(route, fleet)) {
                results[i] = 1;
                deliveredWeights[i] = Math.min(weights[i], ExileBlackFleetLogistics.routeStrength(route));
            }
            if (results[i] != 0) returning.set(i, null); // BF-11: resolved; drop the route ref
        }
    }
    public void updateStatus() {
        if (returning == null) return;
        float total = 0f;
        float delivered = 0f;
        boolean pending = false;
        for (int i = 0; i < returning.size(); i++) {
            RouteData route = (RouteData) returning.get(i);
            total += weights[i];
            if (results[i] == 0 && route != null) {
                CampaignFleetAPI fleet = route.getActiveFleet();
                if (!ExileBlackFleetProsperity.eligible(route.getMarket())) results[i] = -2;
                else if (fleet != null && fleet.isAlive() && !fleet.isEmpty() && atHome(route, fleet)) {
                    results[i] = 1;
                    deliveredWeights[i] = Math.min(weights[i], ExileBlackFleetLogistics.routeStrength(route));
                } else if (fleet == null && route.getCurrent() != null && route.isExpired()) {
                    results[i] = 1;
                }
            }
            // BF-11 (2026-10-06): a resolved entry needs only its weights; drop the route (and,
            // through it, a possibly dead fleet) so the save does not carry it for up to a year.
            if (results[i] != 0) returning.set(i, null);
            if (results[i] == 1) delivered += deliveredWeights[i];
            if (results[i] == 0) pending = true;
        }
        BlackFleetRaidIntel raid = (BlackFleetRaidIntel) intel;
        if (total > 0f && delivered >= total * ExileBlackFleetProsperity.get().config().returnFraction) {
            raid.resolveProsperity(true);
            status = RaidStageStatus.SUCCESS;
        } else if (!pending || elapsed >= maxDays) {
            if (raid.prosperityDefeat) {
                raid.resolveProsperity(false);
                status = RaidStageStatus.FAILURE;
            } else status = RaidStageStatus.SUCCESS;
        }
        if (status != RaidStageStatus.ONGOING) returning = null; // stage over: nothing left to track
    }
}
