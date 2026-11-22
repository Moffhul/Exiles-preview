package data.scripts.exiles;

import java.awt.Color;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * PROTOTYPE: refugee-driven shortage as an EVENT, not a steady condition.
 *
 * This is the corrected shape for scarcity in Starsector's economy: a *temporary shock that recovers*
 * (the same mechanism vanilla Food Shortage and commodity raids use), rather than a permanent stat
 * modifier the resolver fights. A refugee-burdened colony periodically suffers a food + supplies
 * shortage:
 *   - availableStat is cut via addTemporaryModFlat (a real, self-healing deficit -> red markers),
 *   - buy prices spike (player-facing scarcity at the trade screen),
 *   - the event is announced (comm message),
 *   - and the PLAYER can relieve it by selling food/supplies here, earning Refugee-faction reputation
 *     (the hook into the future "earn the Refugees' trust" questline).
 *
 * Scoped as a single-market prototype so the loop is observable. Tuning constants up top. Relief is
 * detected via getPlayerTradeNetQuantity() (no transaction listener needed). State is not persisted
 * (the script is re-added each load), which is fine for testing.
 */
public class RefugeeShortageScript implements EveryFrameScript {

    // ---- tuning (deliberately fast/visible for testing) ----
    public static final float INITIAL_DELAY_DAYS = 8f;
    public static final float SHORTAGE_DURATION_DAYS = 30f;
    public static final float COOLDOWN_DAYS = 45f;

    public static final float FOOD_SHOCK = 8f;       // market units removed from FOOD availability
    public static final float SUPPLIES_SHOCK = 6f;    // market units removed from SUPPLIES availability
    public static final float PRICE_MULT = 1.5f;      // buy-price multiplier during the shortage

    public static final int RELIEF_UNITS = 200;       // cargo units of food+supplies the player must sell to relieve
    public static final float REP_REWARD = 0.05f;     // reputation with the refugee faction on relief

    private static final int STATE_WAIT = 0;
    private static final int STATE_ACTIVE = 1;

    private final String marketId;

    private int state = STATE_WAIT;
    private float timer = INITIAL_DELAY_DAYS;
    private String modId = null;
    private int foodNetAtStart = 0;
    private int suppliesNetAtStart = 0;

    public RefugeeShortageScript(String marketId) {
        this.marketId = marketId;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().isPaused()) return;

        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        timer -= days;

        if (state == STATE_WAIT) {
            if (timer <= 0f) {
                startShortage(market);
            }
            return;
        }

        // STATE_ACTIVE
        if (playerReliefSoFar(market) >= RELIEF_UNITS) {
            endShortage(market, true);
        } else if (timer <= 0f) {
            endShortage(market, false);
        }
    }

    private void startShortage(MarketAPI market) {
        modId = "exile_refugee_shortage_" + Misc.genUID();

        CommodityOnMarketAPI food = market.getCommodityData(Commodities.FOOD);
        CommodityOnMarketAPI supplies = market.getCommodityData(Commodities.SUPPLIES);

        // Real, self-healing deficit (recovers when the temp mod expires or we clear it on relief).
        food.getAvailableStat().addTemporaryModFlat(SHORTAGE_DURATION_DAYS + 10f, modId, "Refugee shortage", -FOOD_SHOCK);
        supplies.getAvailableStat().addTemporaryModFlat(SHORTAGE_DURATION_DAYS + 10f, modId, "Refugee shortage", -SUPPLIES_SHOCK);

        // Player-facing price spike on buying these here.
        food.getPlayerSupplyPriceMod().modifyMult(modId, PRICE_MULT, "Refugee shortage");
        supplies.getPlayerSupplyPriceMod().modifyMult(modId, PRICE_MULT, "Refugee shortage");

        foodNetAtStart = food.getPlayerTradeNetQuantity();
        suppliesNetAtStart = supplies.getPlayerTradeNetQuantity();

        announce(market,
                "Refugee shortage: " + market.getName() + " is running short of food and supplies.",
                Misc.getNegativeHighlightColor());

        state = STATE_ACTIVE;
        timer = SHORTAGE_DURATION_DAYS;
    }

    private void endShortage(MarketAPI market, boolean relieved) {
        CommodityOnMarketAPI food = market.getCommodityData(Commodities.FOOD);
        CommodityOnMarketAPI supplies = market.getCommodityData(Commodities.SUPPLIES);

        if (modId != null) {
            food.getAvailableStat().unmodifyFlat(modId);
            supplies.getAvailableStat().unmodifyFlat(modId);
            food.getPlayerSupplyPriceMod().unmodify(modId);
            supplies.getPlayerSupplyPriceMod().unmodify(modId);
            modId = null;
        }

        if (relieved) {
            Global.getSector().getFaction("exilerefugee").adjustRelationship(Factions.PLAYER, REP_REWARD);
            announce(market,
                    "Refugee shortage eased: your deliveries to " + market.getName() + " were noticed by the refugees.",
                    Misc.getPositiveHighlightColor());
        } else {
            announce(market,
                    "Refugee shortage worsens: " + market.getName() + " went unrelieved.",
                    Misc.getNegativeHighlightColor());
        }

        state = STATE_WAIT;
        timer = COOLDOWN_DAYS;
    }

    private int playerReliefSoFar(MarketAPI market) {
        // getPlayerTradeNetQuantity > 0 means the player has been SELLING here recently.
        int foodNow = market.getCommodityData(Commodities.FOOD).getPlayerTradeNetQuantity();
        int suppliesNow = market.getCommodityData(Commodities.SUPPLIES).getPlayerTradeNetQuantity();
        int delta = (foodNow - foodNetAtStart) + (suppliesNow - suppliesNetAtStart);
        if (delta < 0) delta = 0;
        return delta;
    }

    private void announce(MarketAPI market, String text, Color color) {
        MessageIntel intel = new MessageIntel(text, color);
        intel.setIcon(market.getFaction().getCrest());
        Global.getSector().getCampaignUI().addMessage(intel);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
