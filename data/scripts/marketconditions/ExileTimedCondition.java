package data.scripts.marketconditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;

/**
 * Shared lifetime for every Exiles market condition that lands for a while and then lifts
 * (PASS 5a of docs/EXILES_ASHEN_CRUSADE_WAR.md §14.5).
 *
 * <p><b>Why this exists.</b> Seven conditions across three subsystems had each grown their own
 * private copy of the same four things: an {@code elapsed} day counter, an {@code advance()}
 * that converts frame time to days and self-removes at a duration, a {@code resetTimer()}, and
 * a static "add the condition, or refresh its clock if it is already here" helper. Seven copies
 * is seven places to fix a timer bug. This class owns all four; subclasses supply only their
 * duration.
 *
 * <p><b>The id is self-derived.</b> {@code advance()} removes the condition using
 * {@code condition.getId()} rather than a subclass-supplied constant, so a subclass cannot
 * accidentally report an id that disagrees with the row that actually created it. The static
 * helpers still take an explicit id because they run with no instance in hand.
 *
 * <p><b>Tunables stay on the subclasses as public statics.</b> This is deliberate and
 * load-bearing: {@code AshenInterdictionManager}, {@code AshenSabotage} and
 * {@code AshenReliefManager} push config values in by assigning those fields directly. Routing
 * them through {@link #getDurationDays()} lets this class read them polymorphically without any
 * director code having to change. Pass 8 wires the remaining Black Fleet and refugee tunables
 * to config the same way.
 *
 * <p><b>Not a consumer:</b> {@code ExileBusinessIsGood} rolls a random per-application duration
 * and does memory cleanup plus {@code reapplyIndustries()} on expiry. It could inherit through
 * {@link #onExpire()}, but its semantics differ enough that it was deliberately left alone
 * (Sean, 2026-08-03).
 */
public abstract class ExileTimedCondition extends BaseMarketConditionPlugin {

    /** Days since the condition landed, or since the last refresh. */
    protected float elapsed = 0f;

    /**
     * How long this condition lasts, in days. Subclasses return their own public static
     * tunable so config assignment keeps working; see the class comment.
     */
    public abstract float getDurationDays();

    /**
     * PERSIST THE PLUGIN (critical bug, found 2026-09-22 from Sean's "odd items"; the decompiled
     * engine is quoted in docs/EXILES_HEG_LEAGUE_WAR.md sec 14.12.3). Vanilla's
     * {@code BaseMarketConditionPlugin.isTransient()} returns TRUE, and
     * {@code MarketCondition.writeReplace()} then strips the plugin on save while
     * {@code readResolve()} builds an empty one on load. Every subclass therefore restarted its
     * timer on each reload and, for the shortage family, forgot which commodities it had hit -- so
     * the reload's {@code unapply()} removed nothing and the pre-load modifiers stayed forever.
     * Returning false makes the engine serialise this object with its fields, exactly as
     * {@code ExileEmergencyMeasures} already does. The cost is a float and a handful of strings per
     * live condition.
     */
    @Override
    public boolean isTransient() {
        return false;
    }

    /** Restart the clock -- a fresh application refreshes rather than stacking. */
    public void resetTimer() {
        elapsed = 0f;
    }

    public float getElapsedDays() {
        return elapsed;
    }

    /**
     * Hook for subclasses needing cleanup at expiry, called immediately before the condition is
     * removed. Default does nothing.
     */
    protected void onExpire() {
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null || condition == null) return;
        elapsed += Global.getSector().getClock().convertToDays(amount);
        if (elapsed >= getDurationDays()) {
            onExpire();
            market.removeCondition(condition.getId());
        }
    }

    // ------------------------------------------------------------------
    // Shared static helpers -- subclasses expose these as their own applyTo/removeFrom so
    // every existing caller keeps the exact API it already uses.
    // ------------------------------------------------------------------

    /** Add the condition, or refresh the clock if the market already carries it. */
    protected static void applyOrRefresh(MarketAPI market, String conditionId) {
        if (market == null || !market.isInEconomy()) return;
        MarketConditionAPI existing = market.getCondition(conditionId);
        if (existing != null && existing.getPlugin() instanceof ExileTimedCondition) {
            ((ExileTimedCondition) existing.getPlugin()).resetTimer();
        } else {
            market.addCondition(conditionId);
        }
    }

    /** Remove the condition if present; no-op otherwise. */
    protected static void removeIfPresent(MarketAPI market, String conditionId) {
        if (market == null) return;
        if (market.hasCondition(conditionId)) {
            market.removeCondition(conditionId);
        }
    }
}
