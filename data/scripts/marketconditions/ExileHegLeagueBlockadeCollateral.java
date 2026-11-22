package data.scripts.marketconditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Hegemony-Persean League BLOCKADE COLLATERAL -- PASS 4 of docs/EXILES_HEG_LEAGUE_WAR.md
 * (sec 14.9, decision D2, RULED by Sean 2026-09-20).
 *
 * <p><b>The split.</b> A system blockade holds every jump point, so nothing gets in or out --
 * including the traffic of colonies that belong to neither belligerent. Vanilla's
 * {@code Blockaded} condition only ever stamps the TARGET faction's markets, which would leave
 * Kapteyn Starworks or Ailmar untouched while the gates above them are shut. Rather than override
 * vanilla's apply/unapply, {@code ExileHegLeagueBlockade} leaves that path alone and applies THIS
 * lighter condition to every other market in the system. Two conditions, two lifecycles, no
 * saved stamp list.
 *
 * <p><b>Its own id, never a shared one</b> (ruling S5): separate ids keep the lifecycles
 * independent and let each tooltip name its own cause.
 *
 * <p><b>It holds NO reference to the intel.</b> Vanilla's {@code Blockaded} stores the
 * {@code BlockadeFGI} in {@code setParam} and reads the live penalty off it; that is precisely
 * what makes an orphaned copy of it impossible to reason about. This one stores a plain
 * {@code Float} -- the already tier-scaled penalty -- which the blockade refreshes on its own
 * periodic tick. If the blockade dies, is console-removed, or is somehow lost, the condition
 * simply stops being refreshed and expires on the {@link ExileTimedCondition} timer after
 * {@link #DURATION_DAYS}. A dead operation can never leave a permanent accessibility penalty
 * behind (Ashen Ruling F discipline).
 *
 * <p><b>Display name and description are PLACEHOLDERS</b> (R12: player-facing text is an
 * outstanding author deliverable). They are deliberately faction-neutral: either belligerent can
 * cause this, and the markets that take it belong to neither.
 *
 * <p>{@link #DURATION_DAYS} is a public static overwritten from config at load by
 * {@code ExileHegLeagueBlockadeManager.applyConditionTunables()} -- the established sibling
 * arrangement that makes the whole condition player-adjustable without touching Java.
 */
public class ExileHegLeagueBlockadeCollateral extends ExileTimedCondition {

    public static final String CONDITION_ID = "exile_hl_blockade_collateral";
    /** PLACEHOLDER -- author deliverable. */
    public static final String CONDITION_NAME = "Blockade Spillover";

    /**
     * Backstop lifetime measured from the LAST refresh. The blockade refreshes this on vanilla's
     * own 0.1-0.3 day tick while it holds the gates, so in normal operation the timer never gets
     * close; this is the orphan guard, not the usual path. Not timeScaled -- it is measured
     * against that untimescaled engine tick, not against the war's fiction.
     */
    public static float DURATION_DAYS = 5f;

    /**
     * The tier-scaled accessibility penalty, as a fraction (0.3 = -30%). Pushed in via
     * {@link #setParam(Object)} on every application and refresh. 0 until told otherwise, so a
     * condition that somehow lands without a param is inert rather than guessing.
     */
    protected float penalty = 0f;

    @Override
    public void setParam(Object param) {
        if (param instanceof Float) {
            penalty = ((Float) param).floatValue();
        } else {
            penalty = 0f;
        }
    }

    public float getPenalty() {
        return penalty;
    }

    public float getDurationDays() {
        return DURATION_DAYS;
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        market.getAccessibilityMod().modifyFlat(id, -penalty, CONDITION_NAME);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        market.getAccessibilityMod().unmodifyFlat(id);
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        tooltip.addPara("%s accessibility", 10f, Misc.getHighlightColor(),
                "-" + (int) Math.round(penalty * 100f) + "%");
    }

    // ------------------------------------------------------------------
    // Static application helpers -- the blockade calls these, nothing else does.
    // ------------------------------------------------------------------

    /**
     * Add the condition with this penalty, or -- if the market already carries it -- update the
     * penalty and restart the expiry clock.
     *
     * <p>A variant of {@link ExileTimedCondition#applyOrRefresh} rather than a call to it: this
     * condition carries a live VALUE as well as a timer, and the value has to follow the
     * blockade's strength tier, which can change mid-op as fleets are lost or defenders arrive.
     */
    public static void applyOrRefresh(MarketAPI market, float penalty) {
        if (market == null || !market.isInEconomy()) return;
        MarketConditionAPI existing = market.getCondition(CONDITION_ID);
        if (existing != null) {
            if (existing.getPlugin() instanceof ExileHegLeagueBlockadeCollateral) {
                ExileHegLeagueBlockadeCollateral plugin =
                        (ExileHegLeagueBlockadeCollateral) existing.getPlugin();
                plugin.setParam(new Float(penalty));
                plugin.resetTimer();
            }
            return;
        }
        market.addCondition(CONDITION_ID, new Float(penalty));
        // Q56 MEASUREMENT LINE (2026-09-22): the add path only, never the per-tick refresh, so a
        // blockade prints one line per bystander market it stamps and one when it lets go.
        Global.getLogger(ExileHegLeagueBlockadeCollateral.class).info(
                "Exiles Heg-League " + CONDITION_NAME + " APPLIED to " + market.getName()
                        + " (" + market.getFactionId() + ") penalty=" + penalty);
    }

    /** Remove from a market if present -- the gates are open again. */
    public static void removeFrom(MarketAPI market) {
        boolean present = isPresent(market);
        removeIfPresent(market, CONDITION_ID);
        if (present && !isPresent(market)) {
            Global.getLogger(ExileHegLeagueBlockadeCollateral.class).info(
                    "Exiles Heg-League " + CONDITION_NAME + " REMOVED from " + market.getName()
                            + " (" + market.getFactionId() + ")");
        }
    }

    public static boolean isPresent(MarketAPI market) {
        return market != null && market.hasCondition(CONDITION_ID);
    }
}
