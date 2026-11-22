package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class ExileAlgaeProcessing extends BaseIndustry {

    public static final String INDUSTRY_ID = "exile_algae_processing";

    @Override
    public void apply() {

        if (market == null) return;

        int size = market.getSize();

        // Enormous food output: market size + 3.
        supply(0, Commodities.FOOD, size + 3, BASE_VALUE_TEXT);
        supply(0, Commodities.VOLATILES, size, BASE_VALUE_TEXT);
        supply(0, Commodities.ORGANICS, size, BASE_VALUE_TEXT);

        // Baseline machinery requirement for keeping the plant operational.
        demand(0, Commodities.HEAVY_MACHINERY, size + 2, BASE_VALUE_TEXT);

        if (market.hasCondition("exile_crusade_mobilization")) {
            // Mobilization bonus: more output through unsafe overclocking and brutal work cycles.
            supply(2, Commodities.FOOD, 1, "Crusade Mobilization");
            supply(2, Commodities.VOLATILES, 1, "Crusade Mobilization");
            supply(2, Commodities.ORGANICS, 1, "Crusade Mobilization");

            // Machinery demand is reduced because human labor and jury-rigging are being substituted for proper parts and automation.
            demand(2, Commodities.HEAVY_MACHINERY, -4, "Crusade Mobilization");

            // The human cost.
            demand(2, Commodities.CREW, size + 2, "Crusade Mobilization");
            demand(2, Commodities.DRUGS, size + 3, "Crusade Mobilization");
        }

        if (!isFunctional()) {
            supply.clear();
            demand.clear();
        }
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    public boolean isAvailableToBuild() {
        return false;
    }

    @Override
    public String getUnavailableReason() {
        return "Requires an existing Domain-era algae processing complex.";
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }
}
