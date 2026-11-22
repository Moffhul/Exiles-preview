package data.scripts.exiles;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/** Ownership and prior-group behavior without a running campaign. */
public class ExileMidanEconomyNetworkCheck {
    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static class Market implements InvocationHandler {
        String faction;
        String group;
        boolean inEconomy = true;
        int groupWrites;
        final Map<String, Object> memoryValues = new HashMap<String, Object>();
        final MemoryAPI memory = (MemoryAPI) Proxy.newProxyInstance(
                MemoryAPI.class.getClassLoader(), new Class[] {MemoryAPI.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("contains".equals(name)) return memoryValues.containsKey(args[0]);
                        if ("get".equals(name) || "getString".equals(name)) {
                            return memoryValues.get(args[0]);
                        }
                        if ("set".equals(name)) {
                            memoryValues.put((String) args[0], args[1]);
                            return null;
                        }
                        if ("unset".equals(name)) {
                            memoryValues.remove(args[0]);
                            return null;
                        }
                        throw new IllegalStateException("Unexpected memory method: " + name);
                    }
                });
        final MarketAPI api = (MarketAPI) Proxy.newProxyInstance(
                MarketAPI.class.getClassLoader(), new Class[] {MarketAPI.class}, this);

        Market(String faction, String group) {
            this.faction = faction;
            this.group = group;
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("isInEconomy".equals(name)) return inEconomy;
            if ("getFactionId".equals(name)) return faction;
            if ("getEconGroup".equals(name)) return group;
            if ("setEconGroup".equals(name)) {
                group = (String) args[0];
                groupWrites++;
                return null;
            }
            if ("getMemoryWithoutUpdate".equals(name)) return memory;
            if ("getId".equals(name)) return "check_market";
            throw new IllegalStateException("Unexpected market method: " + name);
        }
    }

    public static void main(String[] args) {
        Market midan = new Market("exilemidan", null);
        ExileMidanEconomyNetwork.syncMarket(midan.api);
        check(ExileMidanEconomyNetwork.GROUP.equals(midan.group), "Midan joins");
        ExileMidanEconomyNetwork.syncMarket(midan.api);
        check(midan.groupWrites == 1, "Repeated sync does not rewrite market");
        midan.faction = "exileblackfleet";
        ExileMidanEconomyNetwork.syncMarket(midan.api);
        check(midan.groupWrites == 1, "Transfers within coalition stay grouped");
        midan.faction = "hegemony";
        ExileMidanEconomyNetwork.syncMarket(midan.api);
        check(midan.group == null, "Leaving coalition returns to regular economy");
        check(midan.memoryValues.isEmpty(), "Prior-group record cleared");

        Market base = new Market("exileblackfleet", "pirate_base_group");
        ExileMidanEconomyNetwork.syncMarket(base.api);
        check(ExileMidanEconomyNetwork.GROUP.equals(base.group), "Black Fleet joins");
        base.faction = "player";
        ExileMidanEconomyNetwork.syncMarket(base.api);
        check("pirate_base_group".equals(base.group), "Original special group restored");

        Market core = new Market("hegemony", null);
        ExileMidanEconomyNetwork.syncMarket(core.api);
        check(core.groupWrites == 0, "Unrelated Core market unchanged");

        Market removed = new Market("exilemidan", null);
        ExileMidanEconomyNetwork.syncMarket(removed.api);
        removed.inEconomy = false;
        ExileMidanEconomyNetwork.syncMarket(removed.api);
        check(removed.group == null, "Market outside the economy leaves network");
        System.out.println("PASS: coalition ownership, reversibility, and unrelated markets");
    }
}
