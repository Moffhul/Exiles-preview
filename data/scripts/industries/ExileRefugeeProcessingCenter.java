package data.scripts.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import data.scripts.marketconditions.ExileRefugeeInflux;
import data.scripts.marketconditions.ExileRefugeeStrain;

/**
 * Refugee Processing Center -- the HUMANE, more-expensive mitigation. Player-buildable where a colony has a
 * refugee influx, a refugee strain, or a decivilized subpopulation.
 *  - Refugee Influx: offsets all but 1 point of its relation-scaled stability penalty; cuts influx growth by 4.
 *  - Refugee Strain: +3 stability (cancels its -3 penalty). Does NOT ease its accessibility/buy-price effects.
 *    The strain is removed when the colony grows a size (event-driven in ExileRefugeeIntegrationListener) or
 *    reaches size 6 (checked here in advance()).
 *  - Decivilized Subpopulation: +1 stability; does NOT touch its growth. Removed when the colony grows in size
 *    (also via ExileRefugeeIntegrationListener).
 *  - Demands FOOD (size+3) and DOMESTIC_GOODS (size).
 */
public class ExileRefugeeProcessingCenter extends BaseIndustry implements MarketImmigrationModifier {

    public static final String INDUSTRY_ID = "exile_refugee_processing_center";

    public static final int DECIV_STABILITY_BONUS = 1;
    public static final int STRAIN_STABILITY_BONUS = 3;
    public static final int INFLUX_GROWTH_REDUCTION = 4;
    public static final float REFRESH_DAYS = 30f;

    private float sinceRefresh = 0f;

    @Override
    public void apply() {
        super.apply(true);
        sinceRefresh = 0f;

        if (!isFunctional()) {
            return;
        }

        int size = market.getSize();
        demand(Commodities.FOOD, size + 3);
        demand(Commodities.DOMESTIC_GOODS, size);

        refreshInfluxStabilityBonus();

        if (market.hasCondition(Conditions.DECIVILIZED_SUBPOP)) {
            market.getStability().modifyFlat(getModId(1), DECIV_STABILITY_BONUS, "Refugee processing");
        }

        if (market.hasCondition(ExileRefugeeStrain.CONDITION_ID)) {
            market.getStability().modifyFlat(getModId(3), STRAIN_STABILITY_BONUS, "Refugee processing");
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getStability().unmodify(getModId(0));
        market.getStability().unmodify(getModId(1));
        market.getStability().unmodify(getModId(3));
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null || !isFunctional()) return;

        sinceRefresh += Global.getSector().getClock().convertToDays(amount);
        if (sinceRefresh >= REFRESH_DAYS) {
            sinceRefresh = 0f;
            refreshInfluxStabilityBonus();
        }

        // Strain is resolved once the colony reaches max size. (Growth-based removal is event-driven in
        // ExileRefugeeIntegrationListener; this catches a colony that is already at size 6.)
        if (market.getSize() >= 6 && market.hasCondition(ExileRefugeeStrain.CONDITION_ID)) {
            market.removeCondition(ExileRefugeeStrain.CONDITION_ID);
            market.getStability().unmodify(getModId(3));
        }
    }

    /** Bonus that nets the relation-scaled influx penalty down to -1 (overwrites itself on refresh). */
    protected void refreshInfluxStabilityBonus() {
        if (market.hasCondition(ExileRefugeeInflux.CONDITION_ID)) {
            int bonus = Math.max(0, ExileRefugeeInflux.getRefugeeStabilityPenalty() - 1);
            if (bonus > 0) {
                market.getStability().modifyFlat(getModId(0), bonus, "Refugee processing");
            } else {
                market.getStability().unmodify(getModId(0));
            }
        } else {
            market.getStability().unmodify(getModId(0));
        }
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (!isFunctional()) return;

        // Cuts the refugee-influx growth. Does NOT touch decivilized_subpop's growth.
        if (market.hasCondition(ExileRefugeeInflux.CONDITION_ID)) {
            incoming.getWeight().modifyFlat(getModId(2), -INFLUX_GROWTH_REDUCTION, "Refugee processing");
        }
    }

    @Override
    public boolean isAvailableToBuild() {
        if (!super.isAvailableToBuild()) return false;
        // Either/or with Security Centers -- humane vs. harsh, not both.
        if (market.hasIndustry(ExileSecurityCenter.INDUSTRY_ID)) return false;
        return market.hasCondition(ExileRefugeeInflux.CONDITION_ID)
                || market.hasCondition(ExileRefugeeStrain.CONDITION_ID)
                || market.hasCondition(Conditions.DECIVILIZED_SUBPOP);
    }

    @Override
    public String getUnavailableReason() {
        if (!super.isAvailableToBuild()) return super.getUnavailableReason();
        if (market.hasIndustry(ExileSecurityCenter.INDUSTRY_ID)) {
            return "Conflicts with Security Centers -- the humane and harsh approaches are mutually exclusive";
        }
        return "Requires a refugee influx, refugee strain, or a decivilized subpopulation";
    }
}
