package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Price Spikes -- the visible symptom of a Black Fleet extortion racket (spec ratified
 * 2026-07-09, built 2026-07-11). Applied by the extortion manager when a disguised collector
 * fleet reaches a defenseless market; removed if the collector FLAGSHIP is destroyed
 * (bounty-mission logic, author ruling 2026-07-11 -- kill the captain, break the racket),
 * else self-expires after DURATION_DAYS.
 *
 * DELIBERATELY UNATTRIBUTED (legibility = silence, spec #8): the condition never mentions
 * the Black Fleet. The market pays protection; the books get cooked; prices climb; nobody
 * says why. The truth-reveal breadcrumbs are Phase-2 quest content.
 *
 * Mechanics -- the mirror image of ExileDestitutePopulation's player-facing price lever:
 * getPlayerSupplyPriceMod is the price the PLAYER PAYS when buying here (player price mods
 * never touch the NPC econ sim). Two variants, chosen by market faction at apply time:
 *  - Non-refugee: +50% on everything the player buys. Player SELL prices untouched (spec).
 *  - Refugees (exilerefugee): +100% on CREW only -- the racket squeezes people, and what a
 *    refugee enclave has to sell is hands willing to crew a ship. Everything else is
 *    already scarcity-priced misery; there is nothing left to spike.
 *
 * Ships/weapons/blueprints (submarket stock) are NOT touched in v1 -- spec-sanctioned
 * fallback; commodity spikes are the confident path.
 *
 * Also the COORDINATION FLAG (spec #5): markets carrying this condition are exempt from
 * Assault victim-picking and from Corsair Activity -- the Black Fleet does not burn what
 * pays it.
 */
public class ExilePriceSpikes extends ExileTimedCondition {

    public static final String CONDITION_ID = "exilebf_price_spikes";
    public static final String CONDITION_NAME = "Price Spikes";

    /** TUNE: player-buy price multiplier on everything (non-refugee markets). */
    public static float BUY_PRICE_MULT = 1.5f;
    /** TUNE: player-buy price multiplier on crew (refugee markets). */
    public static float REFUGEE_CREW_MULT = 2f;
    /** TUNE: days the racket runs if the collectors go unmolested (playtest tuning
     *  2026-07-11: 60 read as too short at campaign pacing; author ruling 120).
     *  CLAUDE.md names this as a knob that belongs in config -- Pass 8 wires it. */
    public static float DURATION_DAYS = 120f;

    public static final String REFUGEE_FACTION = "exilerefugee";

    /** Apply to a market, or refresh the timer if already present. */
    public static void applyTo(MarketAPI market) {
        applyOrRefresh(market, CONDITION_ID);
    }

    /** Remove (collectors destroyed -- the racket collapses with them). */
    public static void removeFrom(MarketAPI market) {
        removeIfPresent(market, CONDITION_ID);
    }

    public static boolean isSpiked(MarketAPI market) {
        return market != null && market.hasCondition(CONDITION_ID);
    }

    public float getDurationDays() {
        return DURATION_DAYS;
    }

    protected boolean isRefugeeMarket() {
        return market != null && REFUGEE_FACTION.equals(market.getFactionId());
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        if (isRefugeeMarket()) {
            CommodityOnMarketAPI crew = market.getCommodityData(Commodities.CREW);
            if (crew != null) {
                crew.getPlayerSupplyPriceMod().modifyMult(id, REFUGEE_CREW_MULT, CONDITION_NAME);
            }
        } else {
            for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
                com.getPlayerSupplyPriceMod().modifyMult(id, BUY_PRICE_MULT, CONDITION_NAME);
            }
        }
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        // Unmodify both paths unconditionally -- cheap, and safe if the market somehow
        // changed hands mid-condition.
        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            com.getPlayerSupplyPriceMod().unmodify(id);
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        float opad = 10f;
        if (isRefugeeMarket()) {
            tooltip.addPara("%s prices for crew hired here", opad, Misc.getHighlightColor(),
                    "+" + (int) Math.round((REFUGEE_CREW_MULT - 1f) * 100f) + "%");
        } else {
            tooltip.addPara("%s prices for goods bought here", opad, Misc.getHighlightColor(),
                    "+" + (int) Math.round((BUY_PRICE_MULT - 1f) * 100f) + "%");
        }
    }
}
