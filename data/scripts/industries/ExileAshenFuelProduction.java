package data.scripts.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;


public class ExileAshenFuelProduction extends BaseIndustry {

	public static final String INDUSTRY_ID = "exile_ashen_fuel_production";

	public void apply() {
		super.apply(true);
		supplyBonus.modifyFlat(getModId(2), market.getAdmin().getStats().getDynamic().getValue(Stats.FUEL_SUPPLY_BONUS_MOD, 0), "Administrator");
		
		int size = market.getSize();

		demand(Commodities.VOLATILES, size);
		demand(Commodities.HEAVY_MACHINERY, size - 2);

		supply(Commodities.FUEL, size - 2);

		if (market.hasCondition("exile_crusade_mobilization")) {
			supply(3, Commodities.FUEL, 1, "Crusade Mobilization");
			demand(3, Commodities.HEAVY_MACHINERY, 1, "Crusade Mobilization");
			demand(3, Commodities.CREW, size - 2, "Crusade Mobilization");
		}

		Pair<String, Integer> deficit = getMaxDeficit(Commodities.VOLATILES);

		applyDeficitToProduction(1, deficit, Commodities.FUEL);
		
		if (!isFunctional()) {
			supply.clear();
		}
	}

	
	@Override
	public void unapply() {
		super.unapply();
	}
	

	@Override
	public String getCurrentImage() {
		if (getSpecialItem() != null) {
			return Global.getSettings().getSpriteName("industry", "advanced_fuel_prod");
		}
		return super.getCurrentImage();
	}


	public boolean isDemandLegal(CommodityOnMarketAPI com) {
		return true;
	}

	public boolean isSupplyLegal(CommodityOnMarketAPI com) {
		return true;
	}

//	@Override
//	public List<InstallableIndustryItemPlugin> getInstallableItems() {
//		ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<InstallableIndustryItemPlugin>();
//		list.add(new GenericInstallableItemPlugin(this));
//		return list;
//	}

	@Override
	protected boolean canImproveToIncreaseProduction() {
		return true;
	}

	@Override
	public boolean isAvailableToBuild() {
		return false;
	}

	@Override
	public String getUnavailableReason() {
		return "Ashen Crusade Industry";
	}
}
