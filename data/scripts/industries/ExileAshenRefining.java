package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;


public class ExileAshenRefining extends BaseIndustry {

	public static final String INDUSTRY_ID = "exile_ashen_refining";

	public void apply() {
		super.apply(true);
		
		int size = market.getSize();
		
		
		demand(Commodities.HEAVY_MACHINERY, size - 2); // have to keep it low since it can be circular
		demand(Commodities.ORE, size + 2);
		demand(Commodities.RARE_ORE, size);
		
		supply(Commodities.METALS, size);
		supply(Commodities.RARE_METALS, size - 2);

		if (market.hasCondition("exile_crusade_mobilization")) {
			supply(3, Commodities.METALS, 1, "Crusade Mobilization");
			supply(3, Commodities.RARE_METALS, 1, "Crusade Mobilization");
			demand(3, Commodities.HEAVY_MACHINERY, 1, "Crusade Mobilization");
			demand(3, Commodities.CREW, size - 2, "Crusade Mobilization");
		}
		
		Pair<String, Integer> deficit = getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.ORE);
		applyDeficitToProduction(1, deficit, Commodities.METALS);
		
		deficit = getMaxDeficit(Commodities.HEAVY_MACHINERY, Commodities.RARE_ORE);
		applyDeficitToProduction(1, deficit, Commodities.RARE_METALS);
		
		if (!isFunctional()) {
			supply.clear();
		}
	}

	
	@Override
	public void unapply() {
		super.unapply();
	}

	
	public float getPatherInterest() {
		return 2f + super.getPatherInterest();
	}

	@Override
	public boolean isAvailableToBuild() {
		return false;
	}

	@Override
	public String getUnavailableReason() {
		if (!super.isAvailableToBuild()) return super.getUnavailableReason();
		
		return "Ashen Crusade Industry";
	}

	@Override
	protected boolean canImproveToIncreaseProduction() {
		return true;
	}
}
