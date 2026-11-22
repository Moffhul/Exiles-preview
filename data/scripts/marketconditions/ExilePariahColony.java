package data.scripts.marketconditions;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Pariah colony: a settlement the rest of the sector refuses to trade with (e.g. the refugee
 * enclaves everyone shuns). Models the isolation as an ACCESSIBILITY penalty -- a single market-wide
 * throughput cut. Cut off from the trade network, the colony can't readily import what it needs nor
 * export its meager production, so it barely scrapes by. Same lever as vanilla ShippingDisruption,
 * but here the *cause* finally fits: a pariah genuinely has reduced access to other markets.
 *
 * NOTE: accessibility is a single ~0.0-1.5 throughput knob, NOT a per-commodity quantity. It hits
 * imports AND exports across ALL commodities (overproduced goods may glut and cheapen; scarce imports
 * spike). Tune PENALTY carefully -- these are fragile size-3 colonies.
 */
public class ExilePariahColony extends BaseMarketConditionPlugin {

    public static final String CONDITION_NAME = "Pariah colony";

    /** TUNE: start conservative on small refugee colonies; -0.4 nearly isolated a size-4 test world. */
    public static final float ACCESSIBILITY_PENALTY = 0.5f;

    @Override
    public void apply(String id) {
        if (market == null) return;

        market.getAccessibilityMod().modifyFlat(id, -ACCESSIBILITY_PENALTY, CONDITION_NAME);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        market.getAccessibilityMod().unmodifyFlat(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        float opad = 10f;

        tooltip.addPara(
                "%s accessibility",
                opad,
                Misc.getHighlightColor(),
                "-" + (int) (ACCESSIBILITY_PENALTY * 100f) + "%"
        );
    }
}
