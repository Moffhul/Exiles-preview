package data.scripts.marketconditions;

import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;


public class ExileAggressiveMegafauna extends BaseHazardCondition {
	
	public static float STABILITY_PENALTY = 2;
	
	public void apply(String id) {
		super.apply(id);
		
		market.getStability().modifyFlat(id, -STABILITY_PENALTY, "Aggressive Megafauna");
		
	}

	public void unapply(String id) {
		super.unapply(id);
		market.getStability().unmodify(id);
	}

	protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
		super.createTooltipAfterDescription(tooltip, expanded);
		
	}
}


