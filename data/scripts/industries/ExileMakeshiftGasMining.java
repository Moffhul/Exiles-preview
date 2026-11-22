package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;

/**
 * Makeshift Gas Mining -- the repaired form of the Broken Siphon on the Yma refugee Siphon Station. The old
 * Domain siphon coaxed back to life with improvised rigging. Flat output; feeds the Jury-Rigged Processor's
 * volatiles demand on the same colony when both are online.
 *
 *  - Supply: VOLATILES 3 (flat).
 *  - Demand: HEAVY_MACHINERY 1 (improvised rigging); a machinery deficit throttles output.
 *
 * NPC-only; added by a Phase 2 quest swapping out exile_broken_siphon. (Deposit conditions are left alone --
 * the output lives in the industry, same convention as Wildcat Mining.)
 */
public class ExileMakeshiftGasMining extends BaseIndustry {

    public static final String INDUSTRY_ID = "exile_makeshift_gas_mining";

    public static final int VOLATILES_OUTPUT = 3;
    public static final int HEAVY_MACHINERY_DEMAND = 1;

    @Override
    public void apply() {
        super.apply(true);

        demand(Commodities.HEAVY_MACHINERY, HEAVY_MACHINERY_DEMAND);
        supply(Commodities.VOLATILES, VOLATILES_OUTPUT);

        Pair<String, Integer> deficit = getMaxDeficit(Commodities.HEAVY_MACHINERY);
        applyDeficitToProduction(1, deficit, Commodities.VOLATILES);

        if (!isFunctional()) {
            supply.clear();
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
        return "Requires a repaired refugee siphon.";
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    protected boolean canImproveToIncreaseProduction() {
        return true;
    }
}
