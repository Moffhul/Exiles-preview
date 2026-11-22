package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Relief Shipment -- an Ashen Crusade aid convoy has unloaded at a refugee enclave
 * (docs/EXILES_ASHEN_CRUSADE_WAR.md §4.2).
 *
 * <p>This is the morally-grey pillar compressed into one market condition. The Crusade genuinely
 * feeds and stabilises the enclave: food, supplies, and enough order to matter. It also leaves
 * with the enclave's able-bodied, which is why crew availability drops and what the player pays
 * for crew here goes up by half. Both halves are true at once, and neither is a trick -- the
 * refugees who sign on are volunteers who would rather fight than starve.
 *
 * <p>Timed via {@link ExileTimedCondition} (Pass 5a): self-removes after {@link #DURATION_DAYS}.
 * A fresh shipment REFRESHES the timer rather than stacking, and {@link AshenReliefManager} will
 * not send a convoy to an enclave that still carries the condition.
 */
public class ExileReliefShipment extends ExileTimedCondition {

    public static final String CONDITION_ID = "exileac_relief_shipment";
    public static final String CONDITION_NAME = "Relief Shipment";

    // TUNE via config (relief.*); these are the fallback defaults.
    public static float DURATION_DAYS = 60f;
    public static float STABILITY_BONUS = 1f;
    public static int FOOD_BONUS = 1;
    public static int SUPPLIES_BONUS = 1;
    /** Ruling J2: -1, not -2. On a size-3 enclave, -2 zeroed out crew availability entirely. */
    public static int CREW_PENALTY = 1;
    /** What the PLAYER pays to hire crew here. Selling crew is deliberately untouched. */
    public static float CREW_PRICE_MULT = 1.5f;

    /** Apply to a market, or refresh the timer if already present. */
    public static void applyTo(MarketAPI market) {
        applyOrRefresh(market, CONDITION_ID);
    }

    public static void removeFrom(MarketAPI market) {
        removeIfPresent(market, CONDITION_ID);
    }

    /** The relief manager's "already fed" check -- it will not double up on an enclave. */
    public static boolean isRelieved(MarketAPI market) {
        return market != null && market.hasCondition(CONDITION_ID);
    }

    public float getDurationDays() {
        return DURATION_DAYS;
    }

    @Override
    public void apply(String id) {
        if (market == null) return;

        market.getStability().modifyFlat(id, STABILITY_BONUS, CONDITION_NAME);
        modifyAvailable(id, Commodities.FOOD, FOOD_BONUS);
        modifyAvailable(id, Commodities.SUPPLIES, SUPPLIES_BONUS);
        modifyAvailable(id, Commodities.CREW, -CREW_PENALTY);

        CommodityOnMarketAPI crew = market.getCommodityData(Commodities.CREW);
        if (crew != null) {
            crew.getPlayerSupplyPriceMod().modifyMult(id, CREW_PRICE_MULT, CONDITION_NAME);
        }
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        market.getStability().unmodifyFlat(id);
        unmodifyAvailable(id, Commodities.FOOD);
        unmodifyAvailable(id, Commodities.SUPPLIES);
        unmodifyAvailable(id, Commodities.CREW);

        CommodityOnMarketAPI crew = market.getCommodityData(Commodities.CREW);
        if (crew != null) {
            crew.getPlayerSupplyPriceMod().unmodify(id);
        }
    }

    protected void modifyAvailable(String id, String commodityId, int amount) {
        CommodityOnMarketAPI com = market.getCommodityData(commodityId);
        if (com == null) return;
        com.getAvailableStat().modifyFlat(id, amount, CONDITION_NAME);
    }

    protected void unmodifyAvailable(String id, String commodityId) {
        CommodityOnMarketAPI com = market.getCommodityData(commodityId);
        if (com == null) return;
        com.getAvailableStat().unmodifyFlat(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        float opad = 10f;

        tooltip.addPara("%s stability", opad, Misc.getHighlightColor(),
                "+" + (int) STABILITY_BONUS);
        tooltip.addPara("%s food, %s supplies available", opad, Misc.getHighlightColor(),
                "+" + FOOD_BONUS, "+" + SUPPLIES_BONUS);
        tooltip.addPara("%s crew available", opad, Misc.getNegativeHighlightColor(),
                "-" + CREW_PENALTY);
        tooltip.addPara("%s to the price of hiring crew here", opad,
                Misc.getNegativeHighlightColor(),
                "+" + (int) Math.round((CREW_PRICE_MULT - 1f) * 100f) + "%");
    }
}
