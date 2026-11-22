package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidDangerLevel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;


public class ExileFarming1 extends BaseIndustry {

    @Override
	public void apply() {
		super.apply(true);
		
		int size = market.getSize();
		
		boolean ExileFarming2 = "exile_farming_2".equals(getId());
    int extraSize = 0;
    if (ExileFarming2) extraSize = 2;

    int output = size - 2 + extraSize;

    if (market.hasCondition("exile_fecund_soil")) {
        output += 2;
    }

    supply(Commodities.FOOD, output);

		if (ExileFarming2) {
			int machineryDemand = Math.max(1, size - 2);
			demand(Commodities.HEAVY_MACHINERY, machineryDemand);
		}

		if (!isFunctional()) {
			supply.clear();
			demand.clear();
		}
	}

	@Override
	public boolean isAvailableToBuild() {
		if (!super.isAvailableToBuild()) {
			return false;
		}

		return market != null && market.hasCondition("exile_fecund_soil");
	}

	@Override
	public boolean canImprove() {
		return true;
	}

	@Override
	public boolean canShutDown() {
		return false;
	}

	@Override
	public String getCanNotShutDownReason() {
		return "This primitive industry cannot be dismantled, but can be upgraded.";
	}

	@Override
	public boolean showWhenUnavailable() {
		return false;
	}
	
	@Override
	public void createTooltip(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
		super.createTooltip(mode, tooltip, expanded);
	}

	@Override
	public RaidDangerLevel adjustCommodityDangerLevel(String commodityId, RaidDangerLevel level) {
		return level.prev();
	}

	@Override
	public RaidDangerLevel adjustItemDangerLevel(String itemId, String data, RaidDangerLevel level) {
		return level.prev();
	}
}
