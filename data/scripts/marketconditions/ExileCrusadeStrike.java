package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/**
 * Crusade Strike -- the aftermath of a successful Ashen Crusade sabotage operation
 * (docs/EXILES_ASHEN_CRUSADE_WAR.md §5.3). Warehouses burn, shipments never arrive, and the
 * colony spends a month short of things it cannot make locally.
 *
 * <p><b>Ruling E.</b> The original brief said "-2 to all commodities, the same as if pirates
 * destroyed a trade fleet bound for the colony". Vanilla's trade-fleet loss actually applies its
 * -2 to SPECIFIC commodities (the {@code sh_loss...} temp mods), not the whole list, and a blanket
 * hit on a size-6 Tri-Tachyon world would ripple through the Core economy -- Tri-Tachyon exports
 * ships, rare metals and domestic goods sector-wide. So this follows the {@link ExileCorsairActivity}
 * pattern instead: the spacer essentials always, plus the market's most import-dependent
 * commodities. {@code strikeCommodityCount} in config reaches the original "-2 to all" behaviour
 * by simply being set high.
 *
 * <p><b>No accessibility penalty, and that is why the Pass 5a base class does not own one.</b>
 * Sabotage burns a warehouse; it does not picket the approaches. Corsair Activity and Crusade
 * Harassment add accessibility themselves. Everything genuinely shared by all three -- the
 * commodity picker, the availability mods, the shortage grid, the timer -- lives in
 * {@link ExileCommodityShortageCondition} / {@link ExileTimedCondition}.
 *
 * <p>Deliberately NO stability or unrest effect. Ordinary sabotage hurts supply lines; hurting
 * the population is the SPECIAL sabotage's job (§5.4), and that one routes its unrest through
 * {@code ExileUnrestGuard}. Keeping the two tiers mechanically distinct is the point.
 *
 * <p>Timed: self-removes after {@link #DURATION_DAYS}. A fresh strike on the same market REFRESHES
 * the timer rather than stacking.
 */
public class ExileCrusadeStrike extends ExileCommodityShortageCondition {

    public static final String CONDITION_ID = "exileac_crusade_strike";
    public static final String CONDITION_NAME = "Crusade Strike";

    /** TUNE via config (sabotage.strikeDays); this is the fallback default. */
    public static float DURATION_DAYS = 30f;
    /** TUNE via config (sabotage.strikeCommodityCount): import-dependent commodities hit,
     *  on top of the always-hit essentials below. Set high for the original "-2 to all". */
    public static int HIT_COUNT = 3;
    /** TUNE via config (sabotage.strikeMagnitude): availability units removed from each. */
    public static int HIT_UNITS = 2;

    /** Always hit: the essentials a besieged colony cannot improvise. Same reasoning as
     *  Corsair Activity -- a flat availability mod works on producers too, so no special-casing. */
    public static final String[] ALWAYS_HIT = new String[] {
            Commodities.SUPPLIES, Commodities.FUEL };

    /** Apply to a market, or refresh the timer if already present. */
    public static void applyTo(MarketAPI market) {
        applyOrRefresh(market, CONDITION_ID);
    }

    public static void removeFrom(MarketAPI market) {
        removeIfPresent(market, CONDITION_ID);
    }

    public static boolean isStruck(MarketAPI market) {
        return market != null && market.hasCondition(CONDITION_ID);
    }

    public String getConditionName() {
        return CONDITION_NAME;
    }

    public float getDurationDays() {
        return DURATION_DAYS;
    }

    public int getHitCount() {
        return HIT_COUNT;
    }

    public int getHitUnits() {
        return HIT_UNITS;
    }

    protected String[] getAlwaysHit() {
        return ALWAYS_HIT;
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        float opad = 10f;

        // Deliberately does NOT name the Ashen Crusade. Sabotage is silent (§3.4) -- the colony
        // knows a warehouse went up, not who lit it. The player earns attribution elsewhere.
        tooltip.addPara(
                "Something went up in the storage district, and the manifests went with it. "
              + "Replacement shipments are weeks out, and the dockmasters have stopped "
              + "answering questions about what caused it.",
                opad);

        addShortageGrid(tooltip, opad,
                "Local availability of several commodities is reduced:");
    }
}
