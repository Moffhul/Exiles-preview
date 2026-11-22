package data.scripts.exiles;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/** Keeps Midan and Black Fleet markets in one private trading group as ownership changes. */
public class ExileMidanEconomyNetwork implements EveryFrameScript {
    public static final String GROUP = "exiles_midan_network";
    private static final String PREVIOUS_GROUP = "$exiles_midan_previous_econ_group";
    private static final String NO_GROUP = "$exiles_midan_no_previous_group";

    protected IntervalUtil check = new IntervalUtil(1f, 1f);

    /** Called only for newly generated campaigns, after economy.json markets have loaded. */
    public static void startNewGame() {
        syncMarkets();
        if (!Global.getSector().hasScript(ExileMidanEconomyNetwork.class)) {
            Global.getSector().addScript(new ExileMidanEconomyNetwork());
        }
    }

    /** Reconcile immediately on load; old campaigns without this script remain untouched. */
    public static void syncMarkets() {
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) return;
        for (Object item : Global.getSector().getEconomy().getMarketsCopy()) {
            syncMarket((MarketAPI) item);
        }
    }

    static void syncMarket(MarketAPI market) {
        if (market == null) return;
        boolean member = market.isInEconomy() && ("exilemidan".equals(market.getFactionId())
                || "exileblackfleet".equals(market.getFactionId()));
        String current = market.getEconGroup();
        MemoryAPI memory = market.getMemoryWithoutUpdate();

        if (member) {
            if (GROUP.equals(current)) return;
            // Preserve a pre-existing special group (for example, a captured pirate base).
            if (!memory.contains(PREVIOUS_GROUP)) {
                memory.set(PREVIOUS_GROUP, current == null ? NO_GROUP : current);
            }
            market.setEconGroup(GROUP);
            Global.getLogger(ExileMidanEconomyNetwork.class).info(
                    "Midan economy: joined " + market.getId() + " (" + market.getFactionId() + ")");
            return;
        }

        if (GROUP.equals(current)) {
            String previous = memory.getString(PREVIOUS_GROUP);
            market.setEconGroup(previous == null || NO_GROUP.equals(previous) ? null : previous);
            Global.getLogger(ExileMidanEconomyNetwork.class).info(
                    "Midan economy: left " + market.getId() + " (" + market.getFactionId() + ")");
        }
        if (memory.contains(PREVIOUS_GROUP)) memory.unset(PREVIOUS_GROUP);
    }

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }

    public void advance(float amount) {
        check.advance(Global.getSector().getClock().convertToDays(amount));
        if (check.intervalElapsed()) syncMarkets();
    }
}
