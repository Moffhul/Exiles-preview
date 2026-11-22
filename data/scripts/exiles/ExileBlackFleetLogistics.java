package data.scripts.exiles;

import java.util.List;
import java.util.Random;
import org.json.JSONObject;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;

/** Dedicated civilian logistics, added after combat generation so they cannot prune warships. */
public class ExileBlackFleetLogistics {
    public static final String COMBAT_FP = "$exilebf_initial_combat_fp";
    public float minimumRoute = 40f, minimumFreight = 5f, maximumFreight = 15f;
    public float assaultMinShare = 0.05f, assaultMaxShare = 0.10f;
    public float collectorTanker = 5f;

    public static ExileBlackFleetLogistics load() {
        ExileBlackFleetLogistics c = new ExileBlackFleetLogistics();
        try {
            JSONObject p = Global.getSettings().loadJSON("data/config/exiles/director_config.json")
                    .getJSONObject("exileblackfleet").optJSONObject("logistics");
            if (p != null) {
                c.minimumRoute = number(p, "minimumFreighterRouteFp", 40f, 1f, 1000f);
                c.minimumFreight = number(p, "minimumFreighterFp", 5f, 1f, 100f);
                c.maximumFreight = number(p, "maximumFreighterFp", 15f, c.minimumFreight, 100f);
                c.assaultMinShare = number(p, "assaultFreighterShareMin", 0.05f, 0f, 1f);
                c.assaultMaxShare = number(p, "assaultFreighterShareMax", 0.10f, c.assaultMinShare, 1f);
                c.collectorTanker = number(p, "collectorTankerFp", 5f, 1f, 100f);
            }
            return c;
        } catch (Exception e) { throw new IllegalArgumentException("Invalid Black Fleet logistics config", e); }
    }

    private static float number(JSONObject p, String key, float fallback, float min, float max) throws Exception {
        Object raw = p.has(key) ? p.get(key) : Float.valueOf(fallback);
        if (!(raw instanceof Number)) throw new IllegalArgumentException(key + " must be numeric");
        float n = ((Number) raw).floatValue();
        if (Float.isNaN(n) || n < min || n > max) throw new IllegalArgumentException(key + " out of range");
        return n;
    }

    public float freight(float baseline, float requested) {
        return baseline < minimumRoute ? 0f : Math.max(minimumFreight, Math.min(maximumFreight, requested));
    }

    public static boolean hasTanker(CampaignFleetAPI fleet) {
        List members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) members.get(i);
            if (m.getHullSpec().getHints().contains(ShipTypeHints.TANKER)) return true;
        }
        return false;
    }

    public static void addSupport(CampaignFleetAPI fleet, FleetParamsV3 params, Random random,
                                  float freight, float tanker) {
        List before = fleet.getFleetData().getMembersListCopy();
        // Vanilla's ordinary freighter helper uses NONE and ignores the tier size cap.
        // These roles are destroyer/cruiser/capital, so use its matching size filter.
        // Vanilla defaults this optional override to null; preserve all three states.
        Boolean fallback = params.blockFallback;
        params.blockFallback = true; // small-role fallback may otherwise select a cruiser
        try {
            FleetFactoryV3.addPriorityOnlyThenAll(fleet, random, freight, params,
                    FleetFactoryV3.SizeFilterMode.SMALL_IS_DESTROYER,
                    ShipRoles.FREIGHTER_SMALL, ShipRoles.FREIGHTER_MEDIUM, ShipRoles.FREIGHTER_LARGE);
        } finally { params.blockFallback = fallback; }
        if (tanker > 0f) FleetFactoryV3.addTankerFleetPoints(fleet, random, tanker, params);
        if (!hasTanker(fleet)) {
            // A positive FP request is not a hull guarantee, especially on remainder routes.
            fleet.getFleetData().addFleetMember("dram_Light");
        }
        List after = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < after.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) after.get(i);
            if (!before.contains(m)) m.getRepairTracker().setCR(m.getRepairTracker().getMaxCR());
        }
        fleet.getFleetData().sort();
    }

    public static float combatFP(CampaignFleetAPI fleet) {
        float fp = 0f;
        List members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) members.get(i);
            // Same combat/civilian distinction as vanilla FleetFactoryV3.pruneFleet.
            if (!m.isCivilian() && !m.isFighterWing()) fp += m.getFleetPointCost();
        }
        return fp;
    }

    public static float routeStrength(RouteData route) {
        CampaignFleetAPI fleet = route.getActiveFleet();
        if (fleet == null) return route.getExtra().fp;
        float initial = fleet.getMemoryWithoutUpdate().getFloat(COMBAT_FP);
        if (initial <= 0f) return 0f;
        return route.getExtra().fp * Math.min(1f, combatFP(fleet) / initial);
    }

    public static float strength(List routes, List stragglers) {
        float fp = 0f;
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            if (!stragglers.contains(route)) fp += routeStrength(route);
        }
        return fp;
    }

    public static class Travel extends TravelStage {
        public Travel(RaidIntel raid, SectorEntityToken from, SectorEntityToken to) {
            super(raid, from, to, false);
        }
        protected boolean enoughMadeIt(List routes, List stragglers) {
            return strength(routes, stragglers) >= abortFP;
        }
    }

    public static void log(CampaignFleetAPI fleet, String tier, float baseline,
                           float combat, float freight, float tanker, float transport) {
        String hulls = "";
        List members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) members.get(i);
            hulls += (i == 0 ? "" : ", ") + m.getHullId() + ":" + m.getFleetPointCost();
        }
        Global.getLogger(ExileBlackFleetLogistics.class).info("Black Fleet logistics " + tier
                + " baseline=" + baseline + " requested combat/freighter/tanker/transport="
                + combat + "/" + freight + "/" + tanker + "/" + transport
                + " generated combat=" + combatFP(fleet) + " total=" + fleet.getFleetPoints()
                + " hullCount=" + members.size() + " hulls=[" + hulls + "]");
    }
}
