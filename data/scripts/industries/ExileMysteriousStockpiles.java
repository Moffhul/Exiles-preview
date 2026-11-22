package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

/**
 * Mysterious Stockpiles -- the Black Fleet's quality engine (author design, ratified 2026-07-07).
 * Lives on the two Black Fleet anchor stations, NPC-only.
 *
 * Replicates Orbital Works + Pristine Nanoforge in ONE custom industry with NO input demands:
 *  - Supply: a full self-sufficiency basket, each commodity individually tunable via the
 *    EXTRA_* constants (defaults = exactly meet local demand; see the tuning block).
 *  - Market PRODUCTION_QUALITY_MOD +0.7 flat = Orbital Works' own +0.2
 *    (HeavyIndustry.ORBITAL_WORKS_QUALITY_BONUS) + pristine forge's +0.5
 *    (ItemEffectsRepo.PRISTINE_NANOFORGE_QUALITY_BONUS). TUNABLE -- it's ours, not vanilla's.
 *  - NO demands ON PURPOSE: warehouses that are never empty on a station that imports nothing.
 *    A curious player reading the anchor's economy page is SUPPOSED to notice the books don't
 *    balance -- that is the Midan-logistics hint (and the leash made visible).
 *
 * The faction-wide effect rides the vanilla ShipQuality machinery: the anchor becomes the
 * Black Fleet's best in-faction ship-producing market, so this quality feeds every Black Fleet
 * fleet in the sector. Emergent defeatability: kill both anchors and the bonus simply ceases to
 * exist -- fleet quality collapses to the doctrine baseline with no script needed. A raid that
 * disrupts this industry suspends the bonus for the disruption duration (defeat-by-degrees).
 *
 * No stability-based quality malus (unlike HeavyIndustry): Midan's supply chain does not care
 * about local unrest. No nanoforge item slot is ever used -- the bonuses are baked in.
 */
public class ExileMysteriousStockpiles extends BaseIndustry {

    public static final String INDUSTRY_ID = "exile_mysterious_stockpiles";

    /** Vanilla-equivalent: ORBITAL_WORKS_QUALITY_BONUS (0.2) + PRISTINE_NANOFORGE_QUALITY_BONUS (0.5). */
    public static final float QUALITY_BONUS = 0.7f;

    // --- GRANULAR OUTPUT TUNING (playtest 2026-07-08: the old uniform size+2 output left most
    // --- goods in VAST surplus over their real demands — "I accidentally created Wal-Mart").
    // Each commodity's output = its LOCAL MAX DEMAND (size-derived, from the vanilla demand
    // formulas of the co-located industries; see apply()) + its EXTRA_* constant below.
    //   EXTRA = 0  -> supply exactly meets demand (self-sufficient, no bargain-bin surplus)
    //   EXTRA > 0  -> deliberate surplus (exports/cheap goods at the black market)
    //   EXTRA < 0  -> deliberate chronic shortage (the ExileSalvageYards flavor)
    // Edit + restart to apply (Janino recompiles at game start; a fresh game is NOT needed —
    // industry apply() reruns on load). If an industry is added/removed on the anchors, re-run
    // the demand audit and update the formulas in apply().
    public static final int EXTRA_SHIPS = 0;
    public static final int EXTRA_HEAVY_MACHINERY = 0;
    public static final int EXTRA_SUPPLIES = 0;
    public static final int EXTRA_HAND_WEAPONS = 0;
    public static final int EXTRA_FOOD = 0;
    public static final int EXTRA_ORGANICS = 0;
    public static final int EXTRA_DOMESTIC_GOODS = 0;
    public static final int EXTRA_LUXURY_GOODS = 0;
    public static final int EXTRA_DRUGS = 0;
    public static final int EXTRA_ORGANS = 0;
    public static final int EXTRA_FUEL = 0;
    public static final int EXTRA_CREW = 0;
    public static final int EXTRA_MARINES = 0;

    @Override
    public void apply() {
        super.apply(true);

        int size = market.getSize();
        // Clear previous prosperity modifiers, including goods rotated out of the basket.
        for (com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            supply(1, com.getId(), 0, "Business is good");
        }

        // No demand() calls, ever -- the missing inputs ARE the clue. Midan provisions its
        // privateers with everything (yes, including the drugs and the organs -- nobody asks
        // where those come from either), and the manifests never reconcile. There is NO engine
        // limit on commodities per industry: supply() is an open map.
        //
        // Per-commodity output = local max demand + EXTRA_* (see the tuning block up top).
        // Demand formulas audited 2026-07-07/08 against the vanilla classes of the co-located
        // industries -- market demand per commodity is the MAX across industries, not the sum:
        //   Population:     food(size), organics(size-1), domestic(size-1), luxury(size-3),
        //                   drugs(size-2), organs(size-3), supplies(min(size,3))
        //   Spaceport:      fuel/supplies/ships(size-2)
        //   Battlestation:  crew(size), supplies(size)
        //   MilitaryBase:   supplies/fuel/ships(size+1)   <- extraDemand=2, the local maximum
        //   HeavyBatteries: supplies(size), marines(size), hand_weapons(size-2)

        // War materiel
        supplyMatched(Commodities.SHIPS, size + 1, EXTRA_SHIPS);              // MilitaryBase
        supplyMatched(Commodities.SUPPLIES, size + 1, EXTRA_SUPPLIES);        // MilitaryBase
        supplyMatched(Commodities.FUEL, size + 1, EXTRA_FUEL);                // MilitaryBase
        supplyMatched(Commodities.HAND_WEAPONS, size - 2, EXTRA_HAND_WEAPONS);// HeavyBatteries
        supplyMatched(Commodities.MARINES, size, EXTRA_MARINES);              // HeavyBatteries
        supplyMatched(Commodities.HEAVY_MACHINERY, 1, EXTRA_HEAVY_MACHINERY); // no local demand: token flavor only

        // Both anchors share the prosperity basket; the patron's baseline remains separate.
        if (market.hasCondition(data.scripts.marketconditions.ExileBusinessIsGood.CONDITION_ID)) {
            java.util.List loot = data.scripts.marketconditions.ExileBusinessIsGood.getLoot(market);
            for (int i = 0; i < loot.size(); i++) {
                String commodityId = (String) loot.get(i);
                supply(1, commodityId,
                        data.scripts.exiles.ExileBlackFleetProsperity.get() == null ? 0
                                : data.scripts.exiles.ExileBlackFleetProsperity.get().config().supplyBoost,
                        "Business is good");
            }
        }

        // Life support
        supplyMatched(Commodities.FOOD, size, EXTRA_FOOD);                    // Population
        supplyMatched(Commodities.ORGANICS, size - 1, EXTRA_ORGANICS);        // Population
        supplyMatched(Commodities.DOMESTIC_GOODS, size - 1, EXTRA_DOMESTIC_GOODS); // Population
        supplyMatched(Commodities.LUXURY_GOODS, size - 3, EXTRA_LUXURY_GOODS);// Population
        supplyMatched(Commodities.DRUGS, size - 2, EXTRA_DRUGS);              // Population
        supplyMatched(Commodities.ORGANS, size - 3, EXTRA_ORGANS);            // Population
        supplyMatched(Commodities.CREW, size, EXTRA_CREW);                    // Battlestation

        if (isFunctional()) {
            market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD)
                    .modifyFlat(getModId(0), QUALITY_BONUS, "Mysterious stockpiles");
        } else {
            supply.clear();
            market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD)
                    .unmodifyFlat(getModId(0));
        }
    }

    /** Output = local demand + extra; skipped entirely when that comes to zero or less
     *  (a negative EXTRA on a low-demand commodity = a deliberate shortage). */
    protected void supplyMatched(String commodityId, int localDemand, int extra) {
        int amount = localDemand + extra;
        if (amount > 0) {
            supply(commodityId, amount);
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodifyFlat(getModId(0));
    }

    @Override
    public boolean isAvailableToBuild() {
        return false; // NPC-only: this is Midan's logistics, not a colony blueprint
    }

    protected void addPostDescriptionSection(com.fs.starfarer.api.ui.TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addPostDescriptionSection(tooltip, mode);
        data.scripts.exiles.ExileBlackFleetProsperity p = data.scripts.exiles.ExileBlackFleetProsperity.get();
        if (p == null) return;
        tooltip.addPara("Black Fleet business reserve: %s / %s. After exhaustion, Business is Good resumes at %s reserve-days. Successful returning sacks and collectors replenish it; losses reduce it.",
                10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), "" + (int)p.reserve(),
                "" + (int)p.config().cap, "" + (int)p.config().threshold);
    }

    @Override
    public String getUnavailableReason() {
        return "Nobody knows where the stockpiles come from, and nobody sells the knowing.";
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }
}
