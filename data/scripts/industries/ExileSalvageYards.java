package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/**
 * Independent Salvage (id kept as exile_salvage_yards) -- the scrap backbone of the refugee colonies
 * (Corvus / Mayasura / Yma). Unaffiliated scavengers with no real infrastructure breaking down derelict hulls
 * and dead cities for metal, parts, and whatever can be refurbished. Most at home at Mairaath, where the
 * refugees pick over the bones of the terraforming-age cities the Luddic Path destroyed -- to the fury of the
 * survivors who still live there (a Phase 2 "pest problem" questline).
 *
 *  - Supply (FLAT, size-independent): METALS 2, HEAVY_MACHINERY 1, DOMESTIC_GOODS 1.
 *  - Demand: FUEL 3 -- by design MORE than these destitute colonies can usually get (the sector's Nachiketa
 *    fuel collapse hits them hardest), so they run a CHRONIC fuel shortage. **The shortage is intentional and
 *    must NOT shut the operation down or throttle output** -- no applyDeficitToProduction on fuel; production
 *    only stops on genuine disruption (isFunctional()).
 *
 * Ruins dependency dropped (they salvage derelict hulls in space too, not just planetary ruins). NPC-only for
 * now (see the "refugee industries as a player-buildable set" design note before opening it up).
 */
public class ExileSalvageYards extends BaseIndustry {

    public static final String INDUSTRY_ID = "exile_salvage_yards";

    public static final int METALS_OUTPUT = 2;
    public static final int HEAVY_MACHINERY_OUTPUT = 1;
    public static final int DOMESTIC_GOODS_OUTPUT = 1;
    public static final int FUEL_DEMAND = 3;

    @Override
    public void apply() {
        super.apply(true);

        // Chronic by design: more fuel than they can get. Does NOT throttle output (no deficit applied).
        demand(Commodities.FUEL, FUEL_DEMAND);

        // Flat scrap output: salvaged plating, reclaimed gear, makeshift goods.
        supply(Commodities.METALS, METALS_OUTPUT);
        supply(Commodities.HEAVY_MACHINERY, HEAVY_MACHINERY_OUTPUT);
        supply(Commodities.DOMESTIC_GOODS, DOMESTIC_GOODS_OUTPUT);

        // Only a genuine disruption stops production -- an unmet fuel demand does not.
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
        return "Requires a refugee salvage population.";
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
