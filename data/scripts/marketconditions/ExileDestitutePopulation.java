package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Destitute Population: the colony's people are too poor to pay well for what they buy. Modelled as a
 * steady, PLAYER-FACING price effect (player price mods only touch player transactions -- no NPC-sim
 * side effects), which is why this is correctly a steady CONDITION (unlike scarcity, which must be an
 * event). Two opposing levers on the same condition:
 *   - blanket suppression of getPlayerDemandPriceMod (the price the colony pays YOU when you sell to
 *     them) -> you can help them, but there is no profit in it.
 *   - a boosted sell-reputation impact on the universally-legal growth essentials -> selling those here
 *     earns extra goodwill with the refugees: an organic, credit-COSTING charity vector (the suppressed
 *     price self-limits the rep gain). Mirrors vanilla Food Shortage's x4 sell-rep trick.
 *
 * Composes with exile_pariah_colony. Luxury Goods are intentionally excluded from the charity list --
 * they rub against the "struggle to survive" feel.
 */
public class ExileDestitutePopulation extends BaseMarketConditionPlugin {

    public static final String CONDITION_NAME = "Destitute Population";

    /** Fraction of the normal price the colony pays when you sell to it. TUNE. */
    public static final float SELL_PRICE_MULT = 0.5f;

    /** Multiplier on the reputation impact of selling the essentials below. TUNE (event-grade is 4f). */
    public static final float REP_MULT = 4f;

    /** Officer-availability multiplier here (0.1 = "hardly ever"). Invisible to the player. TUNE. */
    public static final float OFFICER_PROB_MULT = 0.1f;

    /** Mercenary-officer probability multiplier (0 = the destitute attract no mercenaries). */
    public static final float OFFICER_MERC_PROB_MULT = 0f;

    /** Universally-legal commodities a growing colony needs; selling any of these is genuinely charitable. */
    public static final String[] CHARITY_COMMODITIES = new String[] {
            Commodities.DOMESTIC_GOODS,
            Commodities.FOOD,
            Commodities.FUEL,
            Commodities.ORGANICS,
            Commodities.SUPPLIES
    };

    @Override
    public void apply(String id) {
        if (market == null) return;

        // Blanket: they are broke, so they pay poorly for everything you sell them.
        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            com.getPlayerDemandPriceMod().modifyMult(id, SELL_PRICE_MULT, CONDITION_NAME);
        }

        // But selling them the essentials they need to survive earns extra goodwill.
        for (String c : CHARITY_COMMODITIES) {
            String sellId = Stats.getPlayerSellRepImpactMultId(c);
            market.getStats().getDynamic().getStat(sellId).modifyMult(id, REP_MULT);
        }

        // Invisible (no tooltip line): the destitute can rarely keep captains, and attract no
        // mercenaries at all. Read by OfficerManagerEvent when the player docks here.
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyMult(id, OFFICER_PROB_MULT);
        market.getStats().getDynamic().getMod(Stats.OFFICER_IS_MERC_PROB_MOD).modifyMult(id, OFFICER_MERC_PROB_MULT);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            com.getPlayerDemandPriceMod().unmodify(id);
        }

        for (String c : CHARITY_COMMODITIES) {
            String sellId = Stats.getPlayerSellRepImpactMultId(c);
            market.getStats().getDynamic().getStat(sellId).unmodify(id);
        }

        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodify(id);
        market.getStats().getDynamic().getMod(Stats.OFFICER_IS_MERC_PROB_MOD).unmodify(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        float opad = 10f;

        tooltip.addPara(
                "%s on prices paid for goods sold here",
                opad,
                Misc.getHighlightColor(),
                "-" + (int) ((1f - SELL_PRICE_MULT) * 100f) + "%"
        );

        tooltip.addPara(
                "%s reputation gain when selling food, supplies, fuel, organics, or domestic goods",
                opad,
                Misc.getHighlightColor(),
                "x" + (int) REP_MULT
        );
    }
}
