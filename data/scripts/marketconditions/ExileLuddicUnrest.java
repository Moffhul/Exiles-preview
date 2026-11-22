package data.scripts.marketconditions;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class ExileLuddicUnrest extends BaseMarketConditionPlugin {

    public static final String CONDITION_NAME = "Luddic Unrest";

    public static final float STABILITY_PENALTY = 3f;

    @Override
    public void apply(String id) {
        if (market == null) return;

        market.getStability().modifyFlat(id, -STABILITY_PENALTY, CONDITION_NAME);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        market.getStability().unmodify(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        float opad = 10f;

        tooltip.addPara(
                "%s stability",
                opad,
                Misc.getHighlightColor(),
                "-" + (int) STABILITY_PENALTY
        );
    }
}
