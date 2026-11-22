package data.scripts.marketconditions;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.exiles.ExileBlackFleetEconomyConfig;

/** Guaranteed looting followed by a longer recovery scar. Recent Unrest remains vanilla-owned. */
public class ExileSacked extends ExileTimedCondition {
    public static final String CONDITION_ID = "exilebf_sacked";
    public static final String CONDITION_NAME = "Sacked";
    protected List lootedGoods;
    protected boolean shortagesEnded;
    protected transient ExileBlackFleetEconomyConfig tuning;
    protected ExileBlackFleetEconomyConfig tuning() {
        if (tuning == null) tuning = ExileBlackFleetEconomyConfig.load();
        return tuning;
    }
    public static void applyTo(MarketAPI market) { applyOrRefresh(market, CONDITION_ID); }
    public static boolean isSacked(MarketAPI market) { return market != null && market.hasCondition(CONDITION_ID); }
    public float getDurationDays() { return tuning().scarDays; }
    public void resetTimer() {
        super.resetTimer(); shortagesEnded = false; updateShortages(false);
    }
    protected void chooseGoods() {
        lootedGoods = new ArrayList();
        String[] essentials = new String[]{"supplies", "fuel"};
        for (int i = 0; i < essentials.length; i++) {
            CommodityOnMarketAPI c = market.getCommodityData(essentials[i]);
            if (c != null && (c.getMaxDemand() > 0 || c.getMaxSupply() > 0)) lootedGoods.add(essentials[i]);
        }
        List commodities = market.getCommoditiesCopy();
        for (int n = 0; n < tuning().producedCount; n++) {
            CommodityOnMarketAPI best = null;
            for (int i = 0; i < commodities.size(); i++) {
                CommodityOnMarketAPI c = (CommodityOnMarketAPI)commodities.get(i);
                if (c.isNonEcon() || c.getMaxSupply() <= 0 || lootedGoods.contains(c.getId())
                        || "crew".equals(c.getId()) || "marines".equals(c.getId())) continue;
                if (best == null || c.getMaxSupply() > best.getMaxSupply()) best = c;
            }
            if (best == null) break;
            lootedGoods.add(best.getId());
        }
    }
    public static int additionalShortage(int sack, int corsair) { return Math.max(0, sack - corsair); }
    public void updateShortages(boolean corsairRemoving) {
        if (market == null) return;
        if (lootedGoods == null) chooseGoods();
        ExileCorsairActivity corsair = null;
        MarketConditionAPI c = market.getCondition(ExileCorsairActivity.CONDITION_ID);
        if (!corsairRemoving && c != null && c.getPlugin() instanceof ExileCorsairActivity) corsair = (ExileCorsairActivity)c.getPlugin();
        for (int i = 0; i < lootedGoods.size(); i++) {
            String cid = (String)lootedGoods.get(i);
            int units = elapsed < tuning().shortageDays ? tuning().shortageUnits : 0;
            int extra = additionalShortage(units, corsair == null ? 0 : corsair.getHitFor(cid));
            market.getCommodityData(cid).getAvailableStat().modifyFlat(CONDITION_ID + "_loot", -extra, CONDITION_NAME);
        }
    }
    public static void corsairChanged(MarketAPI market, boolean removing) {
        if (market == null) return;
        MarketConditionAPI c = market.getCondition(CONDITION_ID);
        if (c != null && c.getPlugin() instanceof ExileSacked) ((ExileSacked)c.getPlugin()).updateShortages(removing);
    }
    public void advance(float amount) {
        super.advance(amount);
        if (!shortagesEnded && elapsed >= tuning().shortageDays) {
            shortagesEnded = true;
            if (market != null && market.hasCondition(CONDITION_ID)) updateShortages(false);
        }
    }
    public void apply(String id) {
        if (market == null) return;
        market.getAccessibilityMod().modifyFlat(id, -tuning().accessPenalty, CONDITION_NAME);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(id, tuning().defenseMult, CONDITION_NAME);
        updateShortages(false);
    }
    public void unapply(String id) {
        if (market == null) return;
        market.getAccessibilityMod().unmodifyFlat(id);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(id);
        if (lootedGoods != null) for (int i = 0; i < lootedGoods.size(); i++) {
            market.getCommodityData((String)lootedGoods.get(i)).getAvailableStat().unmodifyFlat(CONDITION_ID + "_loot");
        }
    }
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        tooltip.addPara("Accessibility: -%s%%. Ground defenses: -%s%%.", 10f, Misc.getHighlightColor(),
                "" + Math.round(tuning().accessPenalty * 100), "" + Math.round((1-tuning().defenseMult)*100));
        tooltip.addPara("Recovery scar: %s days remaining. The sack disrupts the port for at least %s days from the attack; longer existing disruption is preserved.",
                10f, Misc.getHighlightColor(), "" + (int)Math.ceil(Math.max(0, getDurationDays()-elapsed)), "" + tuning().portDays);
        if (elapsed < tuning().shortageDays && lootedGoods != null) {
            tooltip.addPara("Looted goods: -%s availability for another %s days. Overlapping Corsair Activity uses the stronger shortage, not both added together.",
                    10f, Misc.getHighlightColor(), "" + tuning().shortageUnits, "" + (int)Math.ceil(tuning().shortageDays-elapsed));
            for (int i=0; i<lootedGoods.size(); i++) tooltip.addPara(Global.getSettings().getCommoditySpec((String)lootedGoods.get(i)).getName(), 0f);
        } else tooltip.addPara("Looted-commodity shortages have ended; trade and defenses are still recovering.", 10f);
    }
}
