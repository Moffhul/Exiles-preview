package data.scripts.marketconditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * "Relief Effort" -- the purely INFORMATIONAL marker on a market whose recovery convoy is in
 * orbit unloading (PASS 6 of docs/EXILES_HEG_LEAGUE_WAR.md sec 4.6, decision D80.7).
 *
 * <p><b>Zero mechanical effect, by design.</b> It applies nothing, unapplies nothing and modifies
 * nothing. The repair it announces is done entirely by
 * {@code data.scripts.exiles.ExileHegLeagueRecoveryManager}, which drips the struck industries'
 * disruption timers down on its own tick; this condition exists only so the player can SEE, in the
 * market panel, that the ticking-down number in the industry tooltip has a cause. The working
 * precedent is {@link ExileReliefShipment}, which carries real bonuses; this one deliberately does
 * not, because the relief is already being delivered by the drip and paying it twice would break
 * the extended-baseline invariant.
 *
 * <p><b>DEFAULT OFF</b> (sec 11 item 3, still awaiting Sean's nod). The recovery manager applies
 * and removes it only when {@code recoveryConvoys.reliefEffortCondition} is true. A CSV row that
 * nothing applies is inert, so shipping the row switched off costs nothing.
 *
 * <p><b>Display name and description are PLACEHOLDERS</b> (R12). They are faction-neutral: either
 * belligerent can be the one repairing its own world.
 *
 * <p>The {@link ExileTimedCondition} timer is the orphan backstop only (Ashen Ruling F
 * discipline): the manager removes this at departure, and {@link #DURATION_DAYS} catches the case
 * where the convoy, the manager or the save state is lost mid-unload.
 */
public class ExileHegLeagueReliefEffort extends ExileTimedCondition {

    public static final String CONDITION_ID = "exile_hl_relief_effort";
    /** PLACEHOLDER -- author deliverable. */
    public static final String CONDITION_NAME = "Relief Effort";

    /**
     * Backstop lifetime. Generous relative to the ~10-day unload because it only ever fires when
     * something went wrong; pushed from config by the recovery manager so it tracks
     * {@code unloadDays}.
     */
    public static float DURATION_DAYS = 30f;

    public static void applyTo(MarketAPI market) {
        applyOrRefresh(market, CONDITION_ID);
    }

    public static void removeFrom(MarketAPI market) {
        removeIfPresent(market, CONDITION_ID);
    }

    public static boolean isPresent(MarketAPI market) {
        return market != null && market.hasCondition(CONDITION_ID);
    }

    public String getConditionName() {
        return CONDITION_NAME;
    }

    public float getDurationDays() {
        return DURATION_DAYS;
    }

    // apply()/unapply() are deliberately NOT overridden: BaseMarketConditionPlugin's do nothing,
    // which is exactly the contract here. Do not "complete" this class by giving it an effect.
}
