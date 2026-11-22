package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidDangerLevel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;


public class ExileGileadFarming extends BaseIndustry {

    @Override
	public void apply() {
		super.apply(true);
		
		int size = market.getSize();
		
    int output = size - 2;

    if (market.hasCondition("exile_corrupted_soil")) {
        output -= 1;
    }

    supply(Commodities.FOOD, output);

    if (!isFunctional()) {
        supply.clear();
			}
	}

	@Override
	public boolean isAvailableToBuild() {
			return false;
		}

	@Override
	public boolean canImprove() {
		return false;
	}

	@Override
	public boolean canShutDown() {
		return false;
	}

	@Override
	public String getCanNotShutDownReason() {
		return "Wounded as it is, farming is at the center of Gilead's purpose in the eyes of the luddic faith.";
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
