package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;

/**
 * Jury-Rigged Processor -- the repaired form of the Broken Processor on the Yma refugee Siphon Station. Cracks
 * volatiles into fuel: a rare and precious thing in the post-Nachiketa fuel-starved sector, and the payoff of
 * the Siphon Station's rebuild arc. Pairs with Makeshift Gas Mining -- once both are online the colony siphons
 * its own gas and refines it into a net fuel surplus.
 *
 *  - Supply: FUEL 3 (flat).
 *  - Demand: VOLATILES 3 (the gas to crack); a volatiles deficit throttles fuel output. When Makeshift Gas
 *    Mining is also online it produces VOLATILES 3 on the same colony, covering this demand locally.
 *
 * NPC-only; added by a Phase 2 quest swapping out exile_broken_processor.
 */
public class ExileJuryRiggedProcessor extends BaseIndustry {

    public static final String INDUSTRY_ID = "exile_juryrigged_processor";

    public static final int FUEL_OUTPUT = 3;
    public static final int VOLATILES_DEMAND = 3;

    @Override
    public void apply() {
        super.apply(true);

        demand(Commodities.VOLATILES, VOLATILES_DEMAND);
        supply(Commodities.FUEL, FUEL_OUTPUT);

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
    public boolean isAvailableToBuild() {
        return false;
    }

    @Override
    public String getUnavailableReason() {
        return "Requires a repaired refugee processor.";
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
