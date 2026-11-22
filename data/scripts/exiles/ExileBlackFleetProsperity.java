package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.marketconditions.ExileBusinessIsGood;

/** One bounded shared account; no fleet ownership and only two markets inspected once a day. */
public class ExileBlackFleetProsperity implements EveryFrameScript {
    public static final String KEY = "$exiles_bf_prosperity";
    protected ExileBlackFleetProsperityState state;
    protected List goods = new ArrayList();
    protected long lastTime;
    protected transient ExileBlackFleetEconomyConfig config;
    protected IntervalUtil tick = new IntervalUtil(1f, 1f);
    public static ExileBlackFleetProsperity get() {
        return (ExileBlackFleetProsperity) Global.getSector().getMemoryWithoutUpdate().get(KEY);
    }
    public static void ensure() {
        ExileBlackFleetProsperity p = get();
        if (p == null) {
            p = new ExileBlackFleetProsperity();
            p.config = ExileBlackFleetEconomyConfig.load();
            p.state = new ExileBlackFleetProsperityState(p.config.initial, p.config.threshold);
            p.lastTime = Global.getSector().getClock().getTimestamp();
            p.goods.add("supplies"); p.goods.add("fuel"); p.goods.add("food"); p.goods.add("domestic_goods");
            Global.getSector().getMemoryWithoutUpdate().set(KEY, p);
        }
        p.config = ExileBlackFleetEconomyConfig.load();
        while (p.goods.size() > p.config.lootCap) p.goods.remove(0);
        if (!Global.getSector().hasScript(ExileBlackFleetProsperity.class)) Global.getSector().addScript(p);
        p.syncTime(); p.syncMarkets();
        String[] anchors = new String[]{"exilebf_sallee_station", "exilebf_lundy_station"};
        for (int i = 0; i < anchors.length; i++) {
            SectorEntityToken station = Global.getSector().getEntityById(anchors[i]);
            if (station != null && station.getMarket() != null) station.getMarket().reapplyIndustries();
        }
    }
    public ExileBlackFleetEconomyConfig config() {
        if (config == null) config = ExileBlackFleetEconomyConfig.load();
        return config;
    }
    protected void syncTime() {
        float days = Global.getSector().getClock().getElapsedDaysSince(lastTime);
        lastTime = Global.getSector().getClock().getTimestamp();
        ExileBlackFleetEconomyConfig c = config();
        state.advance(days, c.drain, c.cap, c.threshold, c.window);
    }
    public void outcome(boolean assault, boolean success, List loot) {
        syncTime();
        ExileBlackFleetEconomyConfig c = config();
        if (success) {
            state.credit(assault ? c.assaultGain : c.extortionGain, c.cap, c.threshold,
                    !assault, c.window, c.allowance);
            if (loot != null) for (int i = 0; i < loot.size(); i++) {
                Object id = loot.get(i);
                goods.remove(id); goods.add(id);
            }
            while (goods.size() > c.lootCap) goods.remove(0);
        } else state.loss(assault ? c.assaultLoss : c.extortionLoss, c.threshold);
        syncMarkets();
        Global.getLogger(ExileBlackFleetProsperity.class).info("Black Fleet prosperity: "
                + (assault ? "assault" : "extortion") + " " + (success ? "proceeds" : "loss")
                + "; reserve=" + state.reserve + "; active=" + state.active);
    }
    public static boolean eligible(MarketAPI market) {
        return market != null && market.isInEconomy() && "exileblackfleet".equals(market.getFactionId())
                && market.hasIndustry("exile_mysterious_stockpiles")
                && market.getIndustry("exile_mysterious_stockpiles").isFunctional();
    }
    protected void syncMarkets() {
        String[] ids = new String[]{"exilebf_sallee_station", "exilebf_lundy_station"};
        for (int i = 0; i < ids.length; i++) {
            SectorEntityToken entity = Global.getSector().getEntityById(ids[i]);
            MarketAPI market = entity == null ? null : entity.getMarket();
            if (market == null) continue;
            boolean should = state.active && eligible(market);
            Object old = market.getMemoryWithoutUpdate().get(ExileBusinessIsGood.LOOT_KEY);
            boolean changed = !goods.equals(old);
            if (should) {
                market.getMemoryWithoutUpdate().set(ExileBusinessIsGood.LOOT_KEY, new ArrayList(goods));
                if (!market.hasCondition(ExileBusinessIsGood.CONDITION_ID)) {
                    market.addCondition(ExileBusinessIsGood.CONDITION_ID); changed = true;
                }
            } else {
                market.getMemoryWithoutUpdate().unset(ExileBusinessIsGood.LOOT_KEY);
                changed = market.hasCondition(ExileBusinessIsGood.CONDITION_ID);
                if (changed) market.removeCondition(ExileBusinessIsGood.CONDITION_ID);
            }
            if (changed) market.reapplyIndustries();
        }
    }
    public float reserve() { return state.reserve; }
    public float allowance() { return state.allowance(config().window, config().allowance); }
    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }
    public void advance(float amount) {
        tick.advance(Global.getSector().getClock().convertToDays(amount));
        if (tick.intervalElapsed()) { syncTime(); syncMarkets(); }
    }
}
