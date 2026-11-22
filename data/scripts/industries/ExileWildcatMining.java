package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;

/**
 * Wildcat Mining -- Maxios's bespoke refugee industry. Displaced people squat on a rare-ore vein that, by
 * rights, belongs to New Maxios (whose people are FROM Maxios and furious at the trespass), and strip it with
 * improvised gear and stimulants. A small, EFFICIENT operation that does NOT scale with colony size: it pulls
 * far more than a size-3 squat should, but it can't grow into a proper mine.
 *
 *  - Supply (FLAT, size-independent): ORE 1, RARE_ORE 2.
 *  - Demand: HEAVY_MACHINERY (cobbled-together mining gear) + DRUGS (keeping a brutalized workforce on its
 *    feet -- the grim refugee-underbelly note, and a market for whatever flows out of Kanta's Den next door).
 *  - A HEAVY_MACHINERY deficit throttles output.
 *
 * Planetary deposit conditions are intentionally left alone (they measure the planet's true resources); the
 * vein's productivity lives entirely in this industry, where we control it. NPC-only for now (faction identity,
 * like the Ashen industries / Salvage Yards); see the "refugee industries as a player-buildable set" design
 * note before opening it up. The political tension (New Maxios, the decivilized survivors, whether this colony
 * is allowed to remain) is a Phase 2 questline -- this industry just sets the stage by existing and overproducing.
 */
public class ExileWildcatMining extends BaseIndustry {

    public static final String INDUSTRY_ID = "exile_wildcat_mining";

    public static final int ORE_OUTPUT = 1;
    public static final int RARE_ORE_OUTPUT = 2;
    public static final int HEAVY_MACHINERY_DEMAND = 2;
    public static final int DRUGS_DEMAND = 2;

    @Override
    public void apply() {
        super.apply(true);

        // Cobbled-together mining gear; stimulants to keep the workforce going.
        demand(Commodities.HEAVY_MACHINERY, HEAVY_MACHINERY_DEMAND);
        demand(Commodities.DRUGS, DRUGS_DEMAND);

        // Flat, non-scaling output -- efficient for its size, but it can't grow.
        supply(Commodities.ORE, ORE_OUTPUT);
        supply(Commodities.RARE_ORE, RARE_ORE_OUTPUT);

        Pair<String, Integer> deficit = getMaxDeficit(Commodities.HEAVY_MACHINERY);
        applyDeficitToProduction(1, deficit,
                Commodities.ORE,
                Commodities.RARE_ORE);

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
        return "Requires a refugee wildcat claim.";
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }
}
