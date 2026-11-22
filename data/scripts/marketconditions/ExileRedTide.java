package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Red Tide -- a permanent cut to a market's food output from toxic algae blooms (Pass 7 step 7a,
 * war doc sec 6.1 rung 3; ruled by Sean 2026-10-14, food cap amended the same day).
 *
 * <p>Placed on Volturn at game start, quietly and permanently. While Eventide's solar array works,
 * nobody notices: Eventide's exports cover the gap. After Midan's rung-3 strike on Eventide, this
 * is what turns that loss into a lasting Sector food shortage. The description text lives ONLY in
 * {@code market_conditions.csv}; this class adds just the mechanical effect line.
 *
 * <p><b>Food only, lobster untouched</b> (Sean, 2026-10-24). Volturn's aquaculture also supplies
 * Volturnian lobster through the Lobster Pens condition, and Volturn is the Sector's only lobster
 * source. A per-commodity supply modifier -- vanilla's own pattern in
 * {@code ResourceDepositsCondition}, and what {@code BaseIndustry.supply} documents negative values
 * for -- leaves lobster alone, where an industry-wide bonus would have wiped it out.
 *
 * <p>Stateless on purpose: nothing is remembered between applies, so the transient-plugin trap
 * (market-condition plugin state stripped on save) cannot bite.
 */
public class ExileRedTide extends BaseMarketConditionPlugin {

	/** Food units lost. Tuned by editing this number (Sean's preference: numbers in files, not toggles). */
	public static int FOOD_PENALTY = 2;

	public void apply(String id) {
		Industry industry = getFoodIndustry();
		if (industry == null) return;
		if (industry.isFunctional()) {
			industry.supply(id + "_0", Commodities.FOOD, -FOOD_PENALTY,
					Misc.ucFirst(condition.getName().toLowerCase()));
		} else {
			industry.getSupply(Commodities.FOOD).getQuantity().unmodifyFlat(id + "_0");
		}
	}

	public void unapply(String id) {
		// Stateless: clear the modifier from whichever food industry could carry it.
		Industry aquaculture = market.getIndustry(Industries.AQUACULTURE);
		if (aquaculture != null) {
			aquaculture.getSupply(Commodities.FOOD).getQuantity().unmodifyFlat(id + "_0");
		}
		Industry farming = market.getIndustry(Industries.FARMING);
		if (farming != null) {
			farming.getSupply(Commodities.FOOD).getQuantity().unmodifyFlat(id + "_0");
		}
	}

	/** Aquaculture first (Volturn), Farming as a fallback for any other market it may sit on. */
	protected Industry getFoodIndustry() {
		Industry industry = market.getIndustry(Industries.AQUACULTURE);
		if (industry == null) {
			industry = market.getIndustry(Industries.FARMING);
		}
		return industry;
	}

	protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
		super.createTooltipAfterDescription(tooltip, expanded);
		tooltip.addPara("Reduces food production by %s (Aquaculture or Farming).",
				10f, Misc.getNegativeHighlightColor(), "" + FOOD_PENALTY);
	}
}
