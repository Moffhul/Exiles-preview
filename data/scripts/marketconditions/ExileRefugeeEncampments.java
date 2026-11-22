package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Refugee Encampments -- the temporary aftermath of a refugee convoy lingering at a Core
 * market. This is deliberately much milder than the chronic Refugee Strain condition:
 *
 *   - -2 stability
 *   - +20% to what the player pays for commodities other than crew
 *   - -25% to both the player's crew hiring and crew selling prices
 *
 * Player price modifiers do not alter NPC supply/demand or the colony-economy simulation.
 * The convoy manager normally removes this ten days after the convoy leaves. MAX_DAYS is
 * only an orphan guard: a broken/missing manager can never leave the condition permanently.
 */
public class ExileRefugeeEncampments extends ExileTimedCondition {

    public static final String CONDITION_ID = "exile_refugee_encampments";
    public static final String CONDITION_NAME = "Refugee Encampments";

    public static float STABILITY_PENALTY = 2f;
    public static float BUY_PRICE_MULT = 1.20f;
    public static float CREW_PRICE_MULT = 0.75f;
    /** Orphan guard only -- the convoy manager normally removes this well before the cap. */
    public static float MAX_DAYS = 60f;

    public static void applyTo(MarketAPI market) {
        applyOrRefresh(market, CONDITION_ID);
    }

    public static void removeFrom(MarketAPI market) {
        removeIfPresent(market, CONDITION_ID);
    }

    public float getDurationDays() {
        return MAX_DAYS;
    }

    @Override
    public void apply(String id) {
        if (market == null) return;

        market.getStability().modifyFlat(id, -STABILITY_PENALTY, CONDITION_NAME);
        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            if (Commodities.CREW.equals(com.getId())) continue;
            com.getPlayerSupplyPriceMod().modifyMult(id, BUY_PRICE_MULT, CONDITION_NAME);
        }

        CommodityOnMarketAPI crew = market.getCommodityData(Commodities.CREW);
        if (crew != null) {
            crew.getPlayerSupplyPriceMod().modifyMult(id, CREW_PRICE_MULT, CONDITION_NAME);
            crew.getPlayerDemandPriceMod().modifyMult(id, CREW_PRICE_MULT, CONDITION_NAME);
        }
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        market.getStability().unmodify(id);
        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            com.getPlayerSupplyPriceMod().unmodify(id);
            com.getPlayerDemandPriceMod().unmodify(id);
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        float opad = 10f;
        tooltip.addPara("%s stability", opad, Misc.getHighlightColor(),
                "-" + (int) STABILITY_PENALTY);
        tooltip.addPara("%s prices for goods bought here, except crew", opad,
                Misc.getHighlightColor(),
                "+" + (int) ((BUY_PRICE_MULT - 1f) * 100f) + "%");
        tooltip.addPara("%s crew hiring and selling prices", opad,
                Misc.getHighlightColor(),
                "-" + (int) ((1f - CREW_PRICE_MULT) * 100f) + "%");
    }
}
