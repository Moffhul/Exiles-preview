package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Corsair Activity -- the Black Fleet's commerce-raid aftermath (design ratified 2026-07-09).
 * Applied by {@code BlackFleetCommerceRaidStage} (and stacked on by Assaults) to hostile
 * markets whose shipping the corsairs have been hunting. Models trade disruption WITHOUT
 * needing the player in-system or literal trade-fleet kills -- the economy is abstract, so an
 * accessibility penalty IS "your freighters keep not arriving" (vanilla precedent:
 * PirateActivityIntel's standing accessibility penalty).
 *
 * PLAYTEST-4 PUNCH-UP (2026-07-11, Sean: the condition felt "meh"): on top of accessibility,
 * the market's most import-dependent commodities take a direct AVAILABILITY hit while the
 * corsairs hunt — visible shortages and price spikes, not just a market-screen percentage.
 * This is vanilla's own coupling: ShippingDisruption's accessibility penalty gets its felt
 * punch from the -2 availability temp mods (sh_loss...) that trade-fleet kills land on
 * specific commodities. Ours are condition-scoped modifyFlat mods, so they lift cleanly the
 * moment the condition ends (corsairs withdraw / timer lapses). Side effect that fits the
 * fiction: a player willing to run the blockade finds seller's-market prices waiting.
 *
 * Timed: self-removes after DURATION_DAYS; a fresh commerce raid on the same market REFRESHES
 * the timer instead of stacking (applyTo). Deliberately NO stability/unrest effect -- planets
 * untouched is the tier distinction (author ruling 2026-07-09): commerce raids strangle trade,
 * Assaults hurt worlds.
 *
 * <p>PASS 5a (2026-08-03): the timer, the commodity picker and the shortage grid moved to
 * {@link ExileCommodityShortageCondition} / {@link ExileTimedCondition}; this class now supplies
 * identity, tunables, the accessibility penalty and its own voice. The tunables also lost their
 * {@code final} so config can reach them — the wiring itself is Pass 8, which is when the Black
 * Fleet director side can actually be exercised (Sean, 2026-08-03).
 */
public class ExileCorsairActivity extends ExileCommodityShortageCondition {

    public static final String CONDITION_ID = "exilebf_corsair_activity";
    public static final String CONDITION_NAME = "Corsair Activity";

    /** TUNE: accessibility penalty while corsairs hunt the market's shipping. Accessibility IS
     *  the vanilla import/export throttle (production untouched) — playtest 2026-07-09: -20%
     *  was too weak to visibly choke imports; raised to -50% (Sean's import-strangulation
     *  intent; vanilla's own "trade disruption" gets its punch the same way, by cratering
     *  accessibility via spaceport disruption). */
    public static float ACCESSIBILITY_PENALTY = 0.5f;
    /** TUNE: days the condition lasts (a fresh raid refreshes the clock). */
    public static float DURATION_DAYS = 45f;
    /** TUNE: how many of the market's most import-dependent commodities go short, on top of
     *  the always-hit list below. */
    public static int IMPORT_HIT_COUNT = 3;
    /** TUNE: availability units knocked off each (vanilla trade-fleet-loss hits are -2;
     *  availability is on the market-size scale, so -2 is a real shortage on most worlds). */
    public static int IMPORT_HIT_UNITS = 2;
    /** ALWAYS hit (author ruling 2026-07-11): supplies and fuel — spacer essentials are what
     *  corsairs prize and freighter captains fear to carry. A flat availability mod works on
     *  producers too (their output is bought up/lost under predation), so no special-casing. */
    public static final String[] ALWAYS_HIT = new String[] {
            Commodities.SUPPLIES, Commodities.FUEL };

    /** Apply to a market, or refresh the timer if already present. */
    public static void applyTo(MarketAPI market) {
        applyOrRefresh(market, CONDITION_ID);
    }

    /** Remove from a market if present (commerce raids remove it on withdrawal —
     *  2026-07-09 ruling: the condition lives only while the corsairs actually hunt). */
    public static void removeFrom(MarketAPI market) {
        removeIfPresent(market, CONDITION_ID);
    }

    public String getConditionName() {
        return CONDITION_NAME;
    }

    public float getDurationDays() {
        return DURATION_DAYS;
    }

    public int getHitCount() {
        return IMPORT_HIT_COUNT;
    }

    public int getHitUnits() {
        return IMPORT_HIT_UNITS;
    }

    protected String[] getAlwaysHit() {
        return ALWAYS_HIT;
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        market.getAccessibilityMod().modifyFlat(id, -ACCESSIBILITY_PENALTY, CONDITION_NAME);
        super.apply(id);
        ExileSacked.corsairChanged(market, false);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        market.getAccessibilityMod().unmodifyFlat(id);
        super.unapply(id);
        ExileSacked.corsairChanged(market, true);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        float opad = 10f;

        tooltip.addPara("%s accessibility", opad, Misc.getHighlightColor(),
                "-" + (int) Math.round(ACCESSIBILITY_PENALTY * 100f) + "%");

        addShortageGrid(tooltip, opad,
                "Shipments the market depends on are going missing outright, "
              + "reducing the local availability of some commodities:");
    }
}
