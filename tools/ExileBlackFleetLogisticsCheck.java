package tools;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import data.scripts.exiles.ExileBlackFleetLogistics;

/** Campaign API proxies exercise actual fleet classification and withdrawal aggregation. */
public class ExileBlackFleetLogisticsCheck {
    static List members = new ArrayList();
    static CampaignFleetAPI active;
    static float initial = 80f;
    static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
    static Object proxy(Class type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class[] {type}, handler);
    }
    static FleetMemberAPI member(final float fp, final boolean civilian, final boolean wing) {
        return (FleetMemberAPI) proxy(FleetMemberAPI.class, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] args) {
                if (m.getName().equals("isCivilian")) return Boolean.valueOf(civilian);
                if (m.getName().equals("isFighterWing")) return Boolean.valueOf(wing);
                if (m.getName().equals("getFleetPointCost")) return Integer.valueOf((int) fp);
                throw new IllegalStateException(m.getName());
            }
        });
    }
    public static class Route extends RouteData {
        public Route(OptionalFleetData extra) { super("test", null, Long.valueOf(1), extra); }
        public CampaignFleetAPI getActiveFleet() { return active; }
    }
    public static void run(String directory) throws Exception {
        ExileBlackFleetLogistics c = new ExileBlackFleetLogistics();
        check(c.freight(39.99f, 6f) == 0f, "Small remainder route excluded");
        check(c.freight(40f, 2f) == 5f, "Qualifying route floor");
        check(c.freight(100f, 12f) == 12f, "Percentage within band");
        check(c.freight(500f, 80f) == 15f, "Route ceiling");
        final FleetDataAPI data = (FleetDataAPI) proxy(FleetDataAPI.class, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] args) {
                if (m.getName().equals("getMembersListCopy")) return new ArrayList(members);
                if (m.getName().equals("sort")) return null;
                throw new IllegalStateException(m.getName());
            }
        });
        final MemoryAPI memory = (MemoryAPI) proxy(MemoryAPI.class, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] args) {
                if (m.getName().equals("getFloat")) return Float.valueOf(initial);
                throw new IllegalStateException(m.getName());
            }
        });
        active = (CampaignFleetAPI) proxy(CampaignFleetAPI.class, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] args) {
                if (m.getName().equals("getFleetData")) return data;
                if (m.getName().equals("getMemoryWithoutUpdate")) return memory;
                throw new IllegalStateException(m.getName());
            }
        });
        final ShipHullSpecAPI tankerHull = (ShipHullSpecAPI) proxy(ShipHullSpecAPI.class, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] args) {
                if (m.getName().equals("getHints")) return java.util.EnumSet.of(ShipTypeHints.TANKER);
                throw new IllegalStateException(m.getName());
            }
        });
        members.add(proxy(FleetMemberAPI.class, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] args) {
                if (m.getName().equals("getHullSpec")) return tankerHull;
                if (m.getName().equals("equals")) return Boolean.valueOf(p == args[0]);
                throw new IllegalStateException(m.getName());
            }
        }));
        FleetParamsV3 params = new FleetParamsV3();
        Boolean[] fallbacks = new Boolean[] {null, Boolean.FALSE, Boolean.TRUE};
        for (int i = 0; i < fallbacks.length; i++) {
            params.blockFallback = fallbacks[i];
            ExileBlackFleetLogistics.addSupport(active, params, new java.util.Random(1), 0f, 0f);
            check(params.blockFallback == fallbacks[i], "Support setup preserves nullable fallback setting");
            check(members.size() == 1, "Existing tanker is not duplicated");
        }
        members.clear();
        OptionalFleetData extra = new OptionalFleetData();
        extra.fp = Float.valueOf(100f);
        Route route = new Route(extra);
        members.add(member(80f, false, false));
        members.add(member(40f, true, false));
        members.add(member(20f, false, true));
        check(ExileBlackFleetLogistics.routeStrength(route) == 100f, "Civilian and wing FP do not inflate baseline");
        members.clear();
        members.add(member(40f, false, false));
        members.add(member(500f, true, false));
        check(ExileBlackFleetLogistics.routeStrength(route) == 50f, "Half combat remains half despite civilian survivors");
        members.clear();
        members.add(member(500f, true, false));
        check(ExileBlackFleetLogistics.routeStrength(route) == 0f, "Support-only survivors cannot sustain raid");
        active = null;
        check(ExileBlackFleetLogistics.routeStrength(route) == 100f, "Abstract route retains its baseline");
        List routes = new ArrayList(); routes.add(route);
        check(ExileBlackFleetLogistics.strength(routes, routes) == 0f, "Stragglers excluded");
        check(ExileBlackFleetLogistics.strength(routes, new ArrayList()) == 100f, "Eligible routes counted");
        System.out.println("Black Fleet logistics checks passed");
    }
}

