package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Refugee Strain -- the main signifier that refugees are overwhelming a colony's resources. Applied to
 * NPC colonies NEAR refugee colonies (not the refugee colonies themselves). Simple, fixed mechanics:
 *   - -3 stability
 *   - -25% accessibility
 *   - +10% to the player's BUY prices here (does NOT change the player's sell prices)
 *
 * Removed when the colony has a Refugee Processing Center and either grows a size (event-driven in
 * ExileRefugeeIntegrationListener) or reaches size 6 (checked in the center). The mitigation centers ease
 * the stability hit (both currently net it to 0) but not the accessibility penalty or the buy-price bump.
 *
 * Later plan: also applied to PLAYER colonies that carry exile_refugee_influx and fail to build a control
 * structure within ~400 days.
 */
public class ExileRefugeeStrain extends BaseMarketConditionPlugin {

    public static final String CONDITION_NAME = "Refugee strain";
    public static final String CONDITION_ID = "exile_refugee_strain";

    public static final float STABILITY_PENALTY = 3f;
    public static final float ACCESSIBILITY_PENALTY = 0.25f;
    public static final float BUY_PRICE_MULT = 1.10f;   // +10% to what the player pays to buy here

    @Override
    public void apply(String id) {
        if (market == null) return;

        market.getStability().modifyFlat(id, -STABILITY_PENALTY, CONDITION_NAME);
        market.getAccessibilityMod().modifyFlat(id, -ACCESSIBILITY_PENALTY, CONDITION_NAME);

        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            com.getPlayerSupplyPriceMod().modifyMult(id, BUY_PRICE_MULT, CONDITION_NAME);
        }
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        market.getStability().unmodify(id);
        market.getAccessibilityMod().unmodifyFlat(id);

        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            com.getPlayerSupplyPriceMod().unmodify(id);
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        float opad = 10f;

        tooltip.addPara("%s stability", opad, Misc.getHighlightColor(),
                "-" + (int) STABILITY_PENALTY);
        tooltip.addPara("%s accessibility", opad, Misc.getHighlightColor(),
                "-" + (int) (ACCESSIBILITY_PENALTY * 100f) + "%");
        tooltip.addPara("%s to buy prices here", opad, Misc.getHighlightColor(),
                "+" + (int) ((BUY_PRICE_MULT - 1f) * 100f) + "%");
    }
}
