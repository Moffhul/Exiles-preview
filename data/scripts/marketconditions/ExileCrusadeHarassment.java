package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Crusade Harassment -- the Ashen Crusade's interdiction-raid effect (PASS 4 of
 * docs/EXILES_ASHEN_CRUSADE_WAR.md §6.2). Applied by {@link data.scripts.exiles.AshenInterdictionStage}
 * to Tri-Tachyon markets in a system the Crusade is currently interdicting.
 *
 * <p><b>Separate id from {@link ExileCorsairActivity}, deliberately</b> (ruling E.c). Sharing one
 * condition id between the two directors was REJECTED: both are presence-scoped, both can target
 * Tri-Tachyon, and a Black Fleet commerce raid overlapping an Ashen interdiction raid in the same
 * system would leave whichever withdrew first silently cancelling the other's effect. Separate ids
 * keep the lifecycles independent and let each tooltip name its culprit, which is how the player
 * learns who is strangling the system (§3.4 / ruling J4).
 *
 * <p><b>PASS 5a (2026-08-03) shared the mechanics, not the identity</b> -- exactly as ruling E.c
 * option 3 specified. The timer, commodity picker and shortage grid now live in
 * {@link ExileCommodityShortageCondition} / {@link ExileTimedCondition}; this class supplies
 * identity, tunables, the accessibility penalty and its own voice.
 *
 * <p>Unlike Corsair Activity this lands ONLY on Tri-Tachyon markets, never system-wide, so the
 * area-effect problem that reaches refugee enclaves under the Black Fleet cannot arise here.
 *
 * <p>Tunables are static and overwritten from config at load by
 * {@code AshenInterdictionManager.loadConfig()}, so the whole condition is player-adjustable
 * without touching Java.
 */
public class ExileCrusadeHarassment extends ExileCommodityShortageCondition {

    public static final String CONDITION_ID = "exileac_crusade_harassment";
    public static final String CONDITION_NAME = "Crusade Harassment";

    /** Accessibility penalty while Crusade raiders work the lanes. Matches Corsair Activity's
     *  ratified -50%: accessibility IS vanilla's import/export throttle, and -20% proved too
     *  weak to visibly choke imports in the Black Fleet playtest. */
    public static float ACCESSIBILITY_PENALTY = 0.5f;
    /** Days the condition lasts if it is never lifted. The stage normally removes it on
     *  withdrawal, so this is a backstop against an orphaned condition, not the usual path. */
    public static float DURATION_DAYS = 45f;
    /** How many of the market's most import-dependent commodities go short, on top of ALWAYS_HIT. */
    public static int IMPORT_HIT_COUNT = 3;
    /** Availability units knocked off each (vanilla trade-fleet-loss hits are -2). */
    public static int IMPORT_HIT_UNITS = 2;
    /** Always hit: spacer essentials are what raiders prize and freighter captains fear to carry. */
    public static final String[] ALWAYS_HIT = new String[] {
            Commodities.SUPPLIES, Commodities.FUEL };

    /** Apply to a market, or refresh the timer if already present. */
    public static void applyTo(MarketAPI market) {
        applyOrRefresh(market, CONDITION_ID);
    }

    /** Remove from a market if present -- the raiders have withdrawn. */
    public static void removeFrom(MarketAPI market) {
        removeIfPresent(market, CONDITION_ID);
    }

    public static boolean isHarassed(MarketAPI market) {
        return market != null && market.hasCondition(CONDITION_ID);
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
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        market.getAccessibilityMod().unmodifyFlat(id);
        super.unapply(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        float opad = 10f;
        tooltip.addPara("%s accessibility", opad, Misc.getHighlightColor(),
                "-" + (int) Math.round(ACCESSIBILITY_PENALTY * 100f) + "%");

        addShortageGrid(tooltip, opad,
                "Shipments the market depends on are not arriving, "
              + "reducing the local availability of some commodities:");
    }
}
