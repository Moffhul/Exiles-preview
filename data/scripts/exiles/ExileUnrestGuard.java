package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import data.scripts.marketconditions.ExileEmergencyMeasures;

/**
 * Unrest pressure valve (playtest-4 fix, 2026-07-11): vanilla Recent Unrest stacks WITHOUT
 * any cap and decays only 1 point per 90 days (RecentUnrest.DECREASE_DAYS), so repeated
 * Black Fleet assaults + Omicron bombardments on the same market can pile up a year-deep
 * stability hole — and a market held at 0 stability long enough (~16 months) DECIVILIZES.
 * Observed: Gilead (narrative-gutted base stability, and luddic_church sits in BOTH threat
 * factions' target lists) at stability 0 with ~a year of penalty queued.
 *
 * A market is "battered" when it is at or past the caps below; battered markets are exempt
 * from further scripted unrest:
 *  - BlackFleetRaidManager.pickVictim skips them (the assault goes after someone else —
 *    which also spreads attacks around the map);
 *  - OmicronRaidActionStage still bombards (disruption is temporary and deciv-safe) but
 *    skips the unrest add.
 *
 * PLAYER-FACING TOGGLE: settings.json "exilesUnrestDecivGuard" (this mod ships true).
 * Set false to let the sector burn — vanilla colonies can then be driven to
 * decivilization by sustained raiding. Deliberately a settings key, not config: it is a
 * player difficulty choice, not a designer tuning knob.
 */
public class ExileUnrestGuard {

    public static final String SETTINGS_KEY = "exilesUnrestDecivGuard";

    /** TUNE: existing Recent Unrest penalty at/above which a market is battered.
     *  4 points = a full year of decay (90d each) already queued. */
    public static final int UNREST_PENALTY_CAP = 4;
    /** TUNE: current (modified) stability at/below which a market is battered. */
    public static final int STABILITY_FLOOR = 1;

    /** Missing key (shouldn't happen — the mod ships it) fails SAFE: guard on. */
    public static boolean isEnabled() {
        try {
            return Global.getSettings().getBoolean(SETTINGS_KEY);
        } catch (Exception e) {
            return true;
        }
    }

    /** True = this market has taken enough: skip further scripted unrest / victim picks.
     *  Always false when the guard is disabled or the market is not a live econ market. */
    public static boolean isBattered(MarketAPI market) {
        if (market == null || !market.isInEconomy()) return false;
        // Emergency Measures is a separate visible recovery rule, not controlled by the legacy
        // unrest-guard toggle. Its active state always tells Exiles directors to back off.
        if (ExileEmergencyMeasures.isActive(market)) return true;
        if (!isEnabled()) return false;
        if (RecentUnrest.getPenalty(market) >= UNREST_PENALTY_CAP) return true;
        if ((int) market.getStability().getModifiedValue() <= STABILITY_FLOOR) return true;
        return false;
    }

    /** Narrow attribution seam used only after an Exiles-authored pressure effect actually lands. */
    public static void reportExilesPressure(MarketAPI market, float pressureDays) {
        ExileEmergencyMeasures.reportExilesPressure(market, pressureDays);
    }
}
