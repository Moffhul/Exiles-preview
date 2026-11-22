import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import data.scripts.rulecmd.ExileStartingFines;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.thoughtworks.xstream.XStream;
import data.scripts.exiles.*;
import org.json.*;

/** Exercises real report nodes and production ledger with API proxies, not a live campaign. */
public class ExileStartingDebtCheck {
    interface Call { Object call(String method, Object[] args); }
    static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            Object v = call.call(m.getName(), a == null ? new Object[0] : a);
            if (v != null || !m.getReturnType().isPrimitive()) return v;
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == float.class) return 0f;
            if (m.getReturnType() == long.class) return 0L;
            if (m.getReturnType() == double.class) return 0d;
            return 0;
        }));
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checks++; }
    static int checks;
    static class Debt extends ExileStartingDebt {
        Debt(ExileStartingDebtLedger l) {
            ledger = l; collectionEnabled = true;
            reconciledReport = SharedData.getData().getPreviousReport();
        }
        void sync() { reconcile(); }
        void enabled(boolean value) { collectionEnabled = value; }
    }
    static String stripComments(String s) {
        return s.replaceAll("(?m)^\\s*#.*$", "").replaceAll("(?m)\\s+#.*$", "");
    }
    static Map<String,Object> persistent = new HashMap<>(), memory = new HashMap<>();
    static Set<Object> listeners = Collections.newSetFromMap(new IdentityHashMap<>());
    static float cash;
    static int month, notices;
    static JSONObject root;
    static void environment() throws Exception {
        MemoryAPI mem = proxy(MemoryAPI.class, (m,a) -> {
            if (m.equals("get")) return memory.get(a[0]);
            if (m.equals("contains")) return memory.containsKey(a[0]);
            if (m.equals("getBoolean")) return Boolean.TRUE.equals(memory.get(a[0]));
            if (m.equals("set")) memory.put((String)a[0], a[1]);
            return null;
        });
        Object credits = new com.fs.starfarer.api.util.MutableValue() {
            public float get() { return cash; }
            public void subtract(float value) { cash -= value; }
            public void set(float value) { cash = value; }
        };
        CargoAPI cargo = proxy(CargoAPI.class, (m,a) -> m.equals("getCredits") ? credits : null);
        CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, (m,a) -> m.equals("getCargo") ? cargo : null);
        CampaignClockAPI clock = proxy(CampaignClockAPI.class, (m,a) -> {
            if (m.equals("getCycle")) return month / 12;
            if (m.equals("getMonth")) return month % 12 + 1;
            return null;
        });
        ListenerManagerAPI lm = proxy(ListenerManagerAPI.class, (m,a) -> {
            if (m.equals("addListener")) listeners.add(a[0]);
            if (m.equals("removeListener")) listeners.remove(a[0]);
            return null;
        });
        CampaignUIAPI ui = proxy(CampaignUIAPI.class, (m,a) -> { if (m.equals("addMessage")) notices++; return null; });
        Global.setSector(proxy(SectorAPI.class, (m,a) -> {
            switch (m) {
                case "getPersistentData": return persistent;
                case "getMemoryWithoutUpdate": return mem;
                case "getPlayerFleet": return fleet;
                case "getClock": return clock;
                case "getListenerManager": return lm;
                case "getCampaignUI": return ui;
                case "getIntelManager": return proxy(IntelManagerAPI.class, (im,ia) -> null);
            }
            return null;
        }));
        Global.setSettings(proxy(SettingsAPI.class, (m,a) -> {
            if (m.equals("getFloat")) return 3f;
            if (m.equals("getSpriteName")) return "graphics/icons/intel/credits.png";
            if (m.equals("loadJSON")) return root;
            return null;
        }));
    }
    static void rollover(int otherExpenses) {
        SharedData shared = SharedData.getData();
        MonthlyReport report = shared.getCurrentReport();
        report.getNode("test_other").upkeep = otherExpenses;
        int previousDebt = shared.getPreviousReport().getDebt();
        report.getDebtNode().upkeep = previousDebt;
        shared.rollOverReport();
        report.setPreviousDebt(previousDebt);
        report.computeTotals();
        float balance = cash + report.getRoot().totalIncome - report.getRoot().totalUpkeep;
        report.setDebt((int)Math.max(0, -balance));
        cash = Math.max(0, balance);
        month++;
    }
    public static void main(String[] args) throws Exception {
        root = new JSONObject(stripComments(Files.readString(Path.of(args[0], "data/config/exiles/director_config.json"))));
        ExileStartingDebtConfig cfg = ExileStartingDebtConfig.parse(root.getJSONObject("startingDebt"));
        ExileStartingDebtLedger l = new ExileStartingDebtLedger(cfg.months,cfg.payments,0,cfg.gracePeriodMonths);
        check(l.total == 1800000, "shipped total");
        check(l.bill(0,true)==0 && l.bill(1,true)==0, "partial plus complete grace month free");
        int billed = 0;
        for (int m=2; m<50; m++) {
            int amount = l.bill(m,true); billed += amount;
            int expected = m<8 ? 10000 : m<14 ? 20000 : m<26 ? 35000 : 50000;
            check(amount==expected, "tier schedule " + m);
            check(l.bill(m,true)==0, "duplicate tick");
            l.reconcile(0);
        }
        check(billed==1800000 && l.completed && l.remaining()==0, "48 installments end exactly");
        check(l.bill(100,true)==0, "completed never restarts");
        l = new ExileStartingDebtLedger(new int[]{2},new int[]{100},0,0);
        check(l.bill(0,true)==0 && l.bill(1,false)==0 && l.bill(12,false)==0, "zero grace and disabled");
        check(l.bill(13,true)==100 && l.installments==1, "resume without catch-up");
        l.reconcile(70);
        check(l.remaining()==170 && l.arrears==70, "partial payment retains only unpaid amount");
        XStream xs = new XStream(new com.thoughtworks.xstream.io.xml.DomDriver());
        l = (ExileStartingDebtLedger)xs.fromXML(xs.toXML(l));
        check(l.remaining()==170 && l.lastBillingMonth==13, "saved ledger round-trip");
        l.bill(14,true); l.reconcile(500);
        check(l.remaining()==170 && !l.completed && l.unbilled==0, "final bill does not forgive arrears");
        l.reconcile(0); check(l.completed, "arrears eventually cleared");
        for (String bad : new String[]{"{gracePeriodMonths:-1}","{gracePeriodMonths:1.5}",
                "{paymentSchedule:[]}","{paymentSchedule:[{months:1,monthlyCredits:0}]}",
                "{paymentSchedule:[{months:1200,monthlyCredits:10000000}]}"}) {
            boolean rejected = false;
            try { ExileStartingDebtConfig.parse(new JSONObject(bad)); } catch (Exception e) { rejected=true; }
            check(rejected, "invalid config rejected " + bad);
        }
        environment();
        ExileStartingDebt.initialize();
        check(!memory.containsKey(ExileStartingDebt.KEY), "unrelated start excluded");
        // Menu memory is not the sector memory. Exercise the shipped rule command,
        // then replace the sector before running the deferred creation scripts.
        List<Script> beforeTimePass = new ArrayList<>();
        CharacterCreationData creation = proxy(CharacterCreationData.class, (m,a) -> {
            if (m.equals("addScriptBeforeTimePass")) beforeTimePass.add((Script)a[0]);
            return null;
        });
        Map<String,MemoryAPI> menu = new HashMap<>();
        menu.put(MemKeys.LOCAL, proxy(MemoryAPI.class, (m,a) ->
                m.equals("get") && "$characterData".equals(a[0]) ? creation : null));
        new ExileStartingFines().execute("ngcExileEasyPicked", null, Collections.emptyList(), menu);
        check(beforeTimePass.size()==1 && memory.isEmpty(), "menu defers campaign eligibility");
        environment();
        beforeTimePass.get(0).run();
        ExileStartingDebt.initialize();
        Object initialized = memory.get(ExileStartingDebt.KEY);
        check(initialized instanceof ExileStartingDebt && listeners.contains(initialized),
                "deferred vanilla start initializes debt and registers collection");
        ExileStartingDebt.initialize();
        check(memory.get(ExileStartingDebt.KEY)==initialized && listeners.size()==1,
                "initialization cannot duplicate obligation");
        listeners.clear(); memory.clear();
        ExileStartingDebtLedger spring = new ExileStartingDebtLedger(
                new int[]{6}, new int[]{10000}, 206 * 12 + 2, 1);
        Debt springDebt = new Debt(spring);
        month=206 * 12 + 3; // April 1: the engine closes March's report.
        springDebt.reportEconomyTick(2);
        check(spring.installments==0, "March starting month closes without a fine");
        month=206 * 12 + 4; // May 1: the engine closes the full April grace month.
        springDebt.reportEconomyTick(2);
        check(spring.installments==0, "April grace month closes without a fine");
        month=206 * 12 + 5; // June 1: the engine closes May, the first billing month.
        springDebt.reportEconomyTick(2);
        check(spring.installments==1 && spring.pending==10000
                && spring.lastBillingMonth==206 * 12 + 4, "May installment billed at June rollover");
        SharedData.getData().getCurrentReport().getNode(
                SharedData.getData().getCurrentReport().getNode(MonthlyReport.FLEET),
                ExileStartingDebt.NODE).upkeep=0;
        l = new ExileStartingDebtLedger(new int[]{2},new int[]{100},0,0);
        Debt debt = new Debt(l);
        month=2; cash=60; // Final tick uses the new calendar month while closing month 1.
        debt.reportEconomyTick(0); check(l.pending==0,"no early tick charge");
        debt.reportEconomyTick(2); debt.reportEconomyTick(2);
        check(l.pending==100 && l.installments==1,"one report installment");
        rollover(40); debt.sync(); debt.sync();
        check(cash==0 && l.arrears==80 && l.remaining()==180,"real report mixed expense allocation");
        debt.enabled(false); cash=30; debt.reportEconomyTick(2); rollover(0); debt.sync();
        check(l.arrears==50 && l.installments==1,"paused fines still repay carried debt");
        debt.enabled(true); debt.reportEconomyTick(2);
        cash=500;
        IntelUIAPI intelUI = proxy(IntelUIAPI.class,(m,a)->null);
        debt.buttonPressConfirmed(ExileStartingDebt.PAY,intelUI);
        check(cash==350 && l.completed,"early settlement pays pending plus carried debt exactly");
        check(SharedData.getData().getPreviousReport().getDebt()==0,"own carried debt removed");
        rollover(25);
        check(cash==325,"no double charge after payoff; other expense preserved");
        debt.buttonPressConfirmed(ExileStartingDebt.PAY,intelUI);
        check(cash==325 && notices==1,"repeat payoff inert, notification once");
        // A distinct mixed-debt case: payoff must retain unrelated arrears.
        persistent.clear(); month=1; cash=0;
        l=new ExileStartingDebtLedger(new int[]{1},new int[]{100},0,0); debt=new Debt(l);
        debt.reportEconomyTick(2); rollover(70); debt.sync();
        cash=100; debt.buttonPressConfirmed(ExileStartingDebt.PAY,intelUI);
        check(SharedData.getData().getPreviousReport().getDebt()==70,"unrelated carried debt preserved");
        // Restore is idempotent, and does not replace saved terms from edited config.
        persistent.clear(); month=1;
        l=new ExileStartingDebtLedger(new int[]{2},new int[]{123},0,0); debt=new Debt(l);
        memory.put(ExileStartingDebt.KEY,debt);
        ExileStartingDebt.restore(); ExileStartingDebt.restore();
        check(listeners.size()==1 && l.total==246,"one listener; terms survive restore");
        debt.buttonPressConfirmed(ExileStartingDebt.PAY,intelUI); // insufficient cash
        check(!l.completed,"insufficient funds cannot settle");
        cash=1000; debt.buttonPressConfirmed(ExileStartingDebt.PAY,intelUI);
        ExileStartingDebt.restore();
        check(listeners.isEmpty() && l.completed,"completed restore never registers collector");
        // Serialize the complete obligation/report graph while an installment is pending.
        // The report tooltip points back to the obligation: identity must survive this cycle.
        persistent.clear(); month=1; cash=500;
        l=new ExileStartingDebtLedger(new int[]{2},new int[]{100},0,0); debt=new Debt(l);
        debt.reportEconomyTick(2);
        Object[] graph = new Object[]{debt, SharedData.getData().getCurrentReport(),
                SharedData.getData().getPreviousReport()};
        Object[] restored = (Object[])xs.fromXML(xs.toXML(graph));
        debt=(Debt)restored[0];
        SharedData.getData().setCurrentReport((MonthlyReport)restored[1]);
        SharedData.getData().setPreviousReport((MonthlyReport)restored[2]);
        memory.put(ExileStartingDebt.KEY,debt);
        ExileStartingDebt.restore();
        rollover(0); debt.sync();
        debt.buttonPressConfirmed(ExileStartingDebt.PAY,intelUI);
        check(cash==300,"saved pending graph reconciles and settles without a duplicate bill");
        System.out.println("PASS: " + checks + " starting-debt checks");
    }
}
