package data.scripts.industries;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Pair;
import data.scripts.marketconditions.ExileRefugeeInflux;
import data.scripts.marketconditions.ExileRefugeeStrain;

/**
 * Security Centers -- a decentralized, coercive order apparatus. Player-buildable where a colony has a
 * refugee influx, refugee strain, or decivilized subpopulation.
 *  - Grants +3 generic stability. Marines or hand-weapons deficits reduce this bonus point-for-point.
 *  - Cuts Refugee Influx growth by 10 and cancels decivilized-subpopulation growth while functional;
 *    commodity deficits do not weaken these growth effects.
 *  - Demands MARINES (size) and HAND_WEAPONS (max(0, size-1)).
 *  - Does not generate patrols or add ground-defense strength.
 */
public class ExileSecurityCenter extends BaseIndustry implements MarketImmigrationModifier {

    public static final String INDUSTRY_ID = "exile_security_center";

    public static final int STABILITY_BONUS = 3;
    public static final int INFLUX_GROWTH_REDUCTION = 10;

    @Override
    public void apply() {
        super.apply(true);

        int size = market.getSize();
        demand(Commodities.MARINES, size);
        demand(Commodities.HAND_WEAPONS, Math.max(0, size - 1));

        modifyStabilityWithBaseMod();

        if (!isFunctional()) {
            supply.clear();
            unapply();
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        unmodifyStabilityWithBaseMod();
    }

    @Override
    protected boolean hasPostDemandSection(boolean hasDemand, IndustryTooltipMode mode) {
        return mode != IndustryTooltipMode.NORMAL || isFunctional();
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {
            addStabilityPostDemandSection(tooltip, hasDemand, mode);
        }
    }

    @Override
    protected int getBaseStabilityMod() {
        return STABILITY_BONUS;
    }

    @Override
    protected Pair<String, Integer> getStabilityAffectingDeficit() {
        return getMaxDeficit(Commodities.MARINES, Commodities.HAND_WEAPONS);
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (!isFunctional()) return;

        if (market.hasCondition(ExileRefugeeInflux.CONDITION_ID)) {
            incoming.getWeight().modifyFlat(getModId(2), -INFLUX_GROWTH_REDUCTION, "Security Centers");
        }
        if (market.hasCondition(Conditions.DECIVILIZED_SUBPOP)) {
            // Cancel decivilized_subpop's size-based growth contribution (= market.getSize()) -> ~0.
            incoming.getWeight().modifyFlat(getModId(3), -market.getSize(), "Security Centers");
        }
    }

    @Override
    public boolean isAvailableToBuild() {
        if (!super.isAvailableToBuild()) return false;
        // Either/or with the Refugee Processing Center -- harsh vs. humane, not both.
        if (market.hasIndustry(ExileRefugeeProcessingCenter.INDUSTRY_ID)) return false;
        return market.hasCondition(ExileRefugeeInflux.CONDITION_ID)
                || market.hasCondition(ExileRefugeeStrain.CONDITION_ID)
                || market.hasCondition(Conditions.DECIVILIZED_SUBPOP);
    }

    @Override
    public String getUnavailableReason() {
        if (!super.isAvailableToBuild()) return super.getUnavailableReason();
        if (market.hasIndustry(ExileRefugeeProcessingCenter.INDUSTRY_ID)) {
            return "Conflicts with the Refugee Processing Center -- the harsh and humane approaches are mutually exclusive";
        }
        return "Requires a refugee influx, refugee strain, or a decivilized subpopulation";
    }
}
