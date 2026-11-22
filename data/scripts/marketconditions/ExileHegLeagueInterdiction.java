package data.scripts.marketconditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Hegemony-Persean League INTERDICTION effect -- PASS 2 of docs/EXILES_HEG_LEAGUE_WAR.md
 * sec 4.1 / sec 8. Applied by {@link data.scripts.exiles.ExileHegLeagueInterdictionStage} to the
 * raided side's markets in a system the other side is currently interdicting; lifted when the
 * raiders withdraw.
 *
 * <p><b>Its own id, never a shared one</b> (ruling S5, inherited from Ashen ruling E.c). Corsair
 * Activity and Crusade Harassment already prove the failure mode: two presence ops from different
 * directors overlapping in one system, and whichever withdraws FIRST silently cancels the other's
 * condition. Separate ids keep the lifecycles independent and let each tooltip name its culprit.
 *
 * <p><b>The mechanics are shared, the identity is not.</b> Timer, commodity picker and shortage
 * grid come from {@link ExileCommodityShortageCondition} / {@link ExileTimedCondition} (Ashen
 * Pass 5a); this class supplies the id, the tunables, the accessibility penalty and its voice.
 *
 * <p><b>Either belligerent can cause it.</b> Unlike the Ashen and Black Fleet conditions, which
 * have exactly one possible culprit, this one is applied by the Hegemony against the League and
 * by the League against the Hegemony. The raiding faction is therefore recorded on the market's
 * memory under {@link #ACTOR_KEY} when the condition lands, and the tooltip names it from there;
 * a missing or stale key degrades to neutral wording rather than lying about who is out there.
 *
 * <p><b>Display name and description are PLACEHOLDERS</b> (R12: player-facing text is an
 * outstanding author deliverable, sec 11 item 2). They are deliberately faction-neutral so they
 * read correctly whichever side is doing the interdicting.
 *
 * <p>Tunables are static and overwritten from config at load by
 * {@code ExileHegLeagueInterdictionManager.applyConditionTunables()}, so the whole condition is
 * player-adjustable without touching Java.
 */
public class ExileHegLeagueInterdiction extends ExileCommodityShortageCondition {

    public static final String CONDITION_ID = "exile_hl_interdiction";
    /** PLACEHOLDER -- author deliverable (sec 11 item 2). */
    public static final String CONDITION_NAME = "Wartime Interdiction";

    /** Market memory: which faction's raiders are responsible. Expires on its own so a
     *  condition removed by any path cannot leave a stale attribution behind. */
    public static final String ACTOR_KEY = "$exiles_hl_interdiction_actor";

    /** Accessibility penalty while the raiders hold the approaches. Matches the ratified -50%
     *  the two sibling presence conditions use: accessibility IS vanilla's import/export
     *  throttle, and -20% proved too weak to visibly choke imports in the BF playtest. */
    public static float ACCESSIBILITY_PENALTY = 0.5f;
    /** Backstop lifetime if the stage never lifts it. The stage removes it on withdrawal, so
     *  this is the orphan guard (Ashen Ruling F discipline), not the usual path. */
    public static float DURATION_DAYS = 45f;
    /** Import-dependent commodities knocked short, on top of ALWAYS_HIT. */
    public static int IMPORT_HIT_COUNT = 3;
    /** Availability units removed from each (vanilla trade-fleet-loss hits are -2). */
    public static int IMPORT_HIT_UNITS = 2;
    /** Always hit: the spacer essentials a commerce war strangles first. */
    public static final String[] ALWAYS_HIT = new String[] {
            Commodities.SUPPLIES, Commodities.FUEL };

    /**
     * Apply to a market (or refresh its timer), recording which side's raiders are responsible.
     *
     * @param actorFactionId the RAIDING faction; may be null, in which case the tooltip stays
     *        neutral.
     */
    public static void applyTo(MarketAPI market, String actorFactionId) {
        if (market == null || !market.isInEconomy()) return;
        if (actorFactionId != null) {
            // Outlives the condition by a margin so a refresh near the end of the window cannot
            // drop the attribution, but still expires on its own if the market is somehow left
            // holding the condition through a path that never calls removeFrom().
            market.getMemoryWithoutUpdate().set(
                    ACTOR_KEY, actorFactionId, DURATION_DAYS + 30f);
        }
        applyOrRefresh(market, CONDITION_ID);
    }

    /** Remove from a market if present -- the raiders have withdrawn. */
    public static void removeFrom(MarketAPI market) {
        if (market == null) return;
        market.getMemoryWithoutUpdate().unset(ACTOR_KEY);
        removeIfPresent(market, CONDITION_ID);
    }

    public static boolean isInterdicted(MarketAPI market) {
        return market != null && market.hasCondition(CONDITION_ID);
    }

    /** The faction whose raiders are responsible, or null if unrecorded. */
    protected FactionAPI getActorFaction() {
        if (market == null) return null;
        Object id = market.getMemoryWithoutUpdate().get(ACTOR_KEY);
        if (!(id instanceof String)) return null;
        return Global.getSector().getFaction((String) id);
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

    /** Accessibility lives here rather than in the shared base -- see that class's comment on
     *  why the third consumer (Crusade Strike) must not get it. */
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
