package data.scripts.marketconditions;

import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class ExileCrusadeMobilization extends BaseHazardCondition {

    public static final float STABILITY_PENALTY = 2f;

    @Override
    public void apply(String id) {
        if (market == null) return;

        super.apply(id);
        market.getStability().modifyFlat(id, -STABILITY_PENALTY, condition.getName());
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        super.unapply(id);
        market.getStability().unmodifyFlat(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        tooltip.addPara("%s stability",
                10f, Misc.getHighlightColor(),
                "-" + (int) STABILITY_PENALTY);
    }
}
