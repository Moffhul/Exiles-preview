package data.scripts.exiles;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;

/**
 * RETURN-LEG DISCIPLINE for every Exiles op built on vanilla's {@code RaidIntel} pipeline
 * (Heg–League, Ashen, Black Fleet, Omicron). Built 2026-10-05 for Heg–League, extracted here
 * 2026-10-06 when the bug sweep (docs/EXILES_BUG_SWEEP_2026.md, R-A) found the same gap in the
 * other three directors -- four identical copies is the "two callers with identical code" rule.
 *
 * <p><b>The gap.</b> Vanilla's raid {@code ReturnStage} only re-routes the fleets home
 * ({@code BaseRaidStage.giveReturnOrdersToStragglers}); nothing stops them engaging on the way,
 * so returning raiders kept fighting, joined nearby battles and hit stations after their op was
 * over. Vanilla's newer {@code FleetGroupIntel.giveReturnAssignments} already sets
 * {@code $doNotGetSidetracked}, which is why blockades never needed this.
 *
 * <p><b>Shared vocabulary, not control flow</b> (the settled architecture): each intel keeps its
 * own saved {@code returnLegFlagged} field and decides when to call these; this class holds no
 * state and knows no director.
 */
public class ExileReturnLeg {

    /** True once the op has completed its action stage, or failed at any stage. */
    public static boolean isPastActionStage(RaidIntel intel) {
        if (intel == null) return false;
        if (intel.getFailStage() >= 0) return true;
        ActionStage action = intel.getActionStage();
        return action != null && intel.getCurrentStage() > intel.getStageIndex(action);
    }

    /** Vanilla marks a re-routed (homeward) raid route with {@code STRAGGLER}. */
    public static boolean isStraggler(RouteData route) {
        return route != null && BaseRaidStage.STRAGGLER.equals(route.getCustom());
    }

    public static void flag(CampaignFleetAPI fleet) {
        if (fleet == null) return;
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
    }

    /**
     * Flag every LIVE fleet of the op and write one log line. Fleets still abstract at this moment
     * are the caller's job, in its {@code spawnFleet} override.
     *
     * @param logPrefix the director's log prefix, e.g. "Heg-League", so existing greps keep working.
     */
    public static int flagLiveFleets(RaidIntel intel, String logPrefix, String actorId,
                                     String victimId) {
        int flagged = 0;
        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            CampaignFleetAPI fleet = ((RouteData) routes.get(i)).getActiveFleet();
            if (fleet == null || !fleet.isAlive()) continue;
            flag(fleet);
            flagged++;
        }
        Global.getLogger(ExileReturnLeg.class).info(
                "Exiles " + logPrefix + " RETURN LEG: " + intel.getClass().getSimpleName() + " in "
                        + (intel.getSystem() != null ? intel.getSystem().getBaseName() : "?")
                        + (intel.getFailStage() >= 0
                                ? " (failed at stage " + intel.getFailStage() + ")" : " (completed)")
                        + " | " + flagged + " live fleet(s) set $doNotGetSidetracked, "
                        + (routes.size() - flagged) + " other route(s) flagged as they spawn"
                        + " | actor=" + actorId + " victim=" + victimId);
        return flagged;
    }
}
