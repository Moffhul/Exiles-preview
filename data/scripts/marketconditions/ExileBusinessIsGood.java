package data.scripts.marketconditions;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.exiles.ExileBlackFleetProsperity;

/** Shared prosperity presentation; the two-station manager owns lifetime. */
public class ExileBusinessIsGood extends BaseMarketConditionPlugin {
    public static final String CONDITION_ID = "exilebf_business_is_good";
    public static final String CONDITION_NAME = "Business is Good";
    public static final String LOOT_KEY = "$exilebf_loot_commodities";
    public static List getLoot(MarketAPI market) {
        Object value = market.getMemoryWithoutUpdate().get(LOOT_KEY);
        return value instanceof List ? (List)value : new ArrayList();
    }
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        ExileBlackFleetProsperity p = ExileBlackFleetProsperity.get();
        if (p == null) return;
        tooltip.addPara("Sallee and Lundy share proceeds from sacks and protection collections. Lost fleets and interrupted deliveries threaten business at both stations.", 10f);
        tooltip.addPara("Shared reserve: %s / %s. Daily consumption: %s.", 10f, Misc.getHighlightColor(),
                "" + (int)p.reserve(), "" + (int)p.config().cap, "" + p.config().drain);
        tooltip.addPara("Business ends at zero and resumes at %s reserve-days. Extortion allowance remaining: %s.",
                10f, Misc.getHighlightColor(), "" + p.config().threshold, "" + (int)p.allowance());
        tooltip.addPara("Stockpiles supply bonus: %s per listed commodity.", 10f,
                Misc.getHighlightColor(), "+" + p.config().supplyBoost);
        List loot = getLoot(market);
        for (int i = 0; i < loot.size(); i++) {
            tooltip.addPara(Global.getSettings().getCommoditySpec((String)loot.get(i)).getName(), 0f);
        }
    }
}
