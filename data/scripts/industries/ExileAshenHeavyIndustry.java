package data.scripts.industries;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;


public class ExileAshenHeavyIndustry extends BaseIndustry {

	public static final String INDUSTRY_ID = "exile_ashen_heavy_industry";
	public static final String ORBITAL_WORKS_ID = "exile_ashen_orbitalworks";

	public static float ORBITAL_WORKS_QUALITY_BONUS = 0.2f;
	
	public static String POLLUTION_ID = Conditions.POLLUTION;
	public static float DAYS_BEFORE_POLLUTION = 0f;
	public static float DAYS_BEFORE_POLLUTION_PERMANENT = 90f;
	
	
	public void apply() {
		super.apply(true);
		
		int size = market.getSize();
		
		boolean works = ORBITAL_WORKS_ID.equals(getId());
		int shipBonus = 0;
		float qualityBonus = 0;
		if (works) {
			//shipBonus = 2;
			qualityBonus = ORBITAL_WORKS_QUALITY_BONUS;
		}
		
		demand(Commodities.METALS, size);
		demand(Commodities.RARE_METALS, size - 2);
		
		supply(Commodities.HEAVY_MACHINERY, size - 2);
		supply(Commodities.SUPPLIES, size - 2);
		supply(Commodities.HAND_WEAPONS, size - 2);
		supply(Commodities.SHIPS, size - 2);
		if (shipBonus > 0) {
			supply(1, Commodities.SHIPS, shipBonus, "Orbital works");
		}

		if (market.hasCondition("exile_crusade_mobilization")) {
			supply(3, Commodities.SUPPLIES, 1, "Crusade Mobilization");
			supply(3, Commodities.SHIPS, 2, "Crusade Mobilization");
			supply(3, Commodities.HEAVY_MACHINERY, -1, "Crusade Mobilization");
			demand(3, Commodities.CREW, size - 2, "Crusade Mobilization");
		}

		Pair<String, Integer> deficit = getMaxDeficit(Commodities.METALS, Commodities.RARE_METALS);
		int maxDeficit = size - 3; // to allow *some* production so economy doesn't get into an unrecoverable state

		int deficitAmount = ((Integer) deficit.two).intValue();
		if (deficitAmount > maxDeficit) {
			deficitAmount = maxDeficit;
		}

		Pair<String, Integer> cappedDeficit = new Pair<String, Integer>();
		cappedDeficit.one = deficit.one;
		cappedDeficit.two = Integer.valueOf(deficitAmount);

		applyDeficitToProduction(2, cappedDeficit,
				Commodities.HEAVY_MACHINERY,
				Commodities.SUPPLIES,
				Commodities.HAND_WEAPONS,
				Commodities.SHIPS);


//		if (market.getId().equals("chicomoztoc")) {
//			System.out.println("efwefwe");
//		}
		
		if (qualityBonus > 0) {
			market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId(1), qualityBonus, "Orbital works");
		}
		
		float stability = market.getPrevStability();
		if (stability < 5) {
			float stabilityMod = (stability - 5f) / 5f;
			stabilityMod *= 0.5f;
			//market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId(0), stabilityMod, "Low stability at production source");
			market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId(0), stabilityMod, getNameForModifier() + " - low stability");
		}
		
		if (!isFunctional()) {
			supply.clear();
			unapply();
		}
	}
	
	@Override
	public void unapply() {
		super.unapply();
		
		market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodifyFlat(getModId(0));
		market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodifyFlat(getModId(1));
	}

	
	@Override
	protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
		//if (mode == IndustryTooltipMode.NORMAL && isFunctional()) {
		if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {			
			boolean works = ORBITAL_WORKS_ID.equals(getId());
			if (works) {
				float total = ORBITAL_WORKS_QUALITY_BONUS;
				String totalStr = "+" + (int)Math.round(total * 100f) + "%";
				Color h = Misc.getHighlightColor();
				if (total < 0) {
					h = Misc.getNegativeHighlightColor();
					totalStr = "" + (int)Math.round(total * 100f) + "%";
				}
				float opad = 10f;
				if (total >= 0) {
					tooltip.addPara("Ship quality: %s", opad, h, totalStr);
					tooltip.addPara("*Quality bonus only applies for the largest ship producer in the faction.", 
							Misc.getGrayColor(), opad);
				}
			}
		}
	}
	
	public boolean isDemandLegal(CommodityOnMarketAPI com) {
		return true;
	}

	public boolean isSupplyLegal(CommodityOnMarketAPI com) {
		return true;
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
	
	@Override
	public boolean wantsToUseSpecialItem(SpecialItemData data) {
		if (special != null && Items.CORRUPTED_NANOFORGE.equals(special.getId()) &&
				data != null && Items.PRISTINE_NANOFORGE.equals(data.getId())) {
			return true;
		}
		return super.wantsToUseSpecialItem(data);
	}

	protected boolean permaPollution = false;
	protected boolean addedPollution = false;
	protected float daysWithNanoforge = 0f;
	
	@Override
	public void advance(float amount) {
		super.advance(amount);
		
		if (special != null) {
			float days = Global.getSector().getClock().convertToDays(amount);
			daysWithNanoforge += days;
	
			updatePollutionStatus();
		}
	}
	
	protected void updatePollutionStatus() {
		if (!market.hasCondition(Conditions.HABITABLE)) return;
		
		if (special != null) {
			if (!addedPollution && daysWithNanoforge >= DAYS_BEFORE_POLLUTION) {
				if (market.hasCondition(POLLUTION_ID)) {
					permaPollution = true;
				} else {
					market.addCondition(POLLUTION_ID);
					addedPollution = true;
				}
			}
			if (addedPollution && !permaPollution) {
				if (daysWithNanoforge > DAYS_BEFORE_POLLUTION_PERMANENT) {
					permaPollution = true;
				}
			}
		} else if (addedPollution && !permaPollution) {
			market.removeCondition(POLLUTION_ID);
			addedPollution = false;
		}
	}

	public boolean isPermaPollution() {
		return permaPollution;
	}

	public void setPermaPollution(boolean permaPollution) {
		this.permaPollution = permaPollution;
	}

	public boolean isAddedPollution() {
		return addedPollution;
	}

	public void setAddedPollution(boolean addedPollution) {
		this.addedPollution = addedPollution;
	}

	public float getDaysWithNanoforge() {
		return daysWithNanoforge;
	}

	public void setDaysWithNanoforge(float daysWithNanoforge) {
		this.daysWithNanoforge = daysWithNanoforge;
	}

	@Override
	public void setSpecialItem(SpecialItemData special) {
		super.setSpecialItem(special);
		
		updatePollutionStatus();
	}
	
	
	
//	@Override
//	public List<InstallableIndustryItemPlugin> getInstallableItems() {
//		ArrayList<InstallableIndustryItemPlugin> list = new ArrayList<InstallableIndustryItemPlugin>();
//		list.add(new GenericInstallableItemPlugin(this));
//		return list;
//	}
	
	
}









