package data.scripts.exiles;

import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;

/**
 * Author-owned version-one tuning for Emergency Measures.
 *
 * These values belong to the recovery system, not to any raid director. They are code constants
 * because they are ratified campaign rules rather than player-facing difficulty settings.
 */
public final class ExileEmergencyMeasuresConfig {

    private ExileEmergencyMeasuresConfig() {
    }

    public static final float ZERO_STABILITY_TRIGGER_DAYS = 60f;
    public static final int STABILITY_RESERVE_CAP = 5;
    public static final float MAX_ACTIVE_DAYS = 180f;
    public static final float RECOVERY_HOLD_DAYS = 30f;
    public static final float RECOVERY_UNDERLYING_STABILITY = 3f;
    public static final float COOLDOWN_DAYS = 180f;

    public static final int MARINES_DEMAND = 1;
    public static final int HAND_WEAPONS_DEMAND = 1;
    public static final int SUPPLIES_DEMAND = 1;

    public static final float NPC_PLAYER_BUY_PRICE_MULT = 1.50f;
    public static final int PLAYER_MONTHLY_COST_PER_MARKET_SIZE = 10000;

    /** Reapply ownership/commodity-sensitive effects at most once per campaign day. */
    public static final float EFFECT_REFRESH_DAYS = 1f;

    /** A one-point Recent Unrest effect lasts 90 days in vanilla, comfortably past the 60-day trigger. */
    public static final float MIN_EXILES_PRESSURE_DAYS = 90f;

    /** A player-caused crisis cannot borrow a recent Exiles marker during this attribution window. */
    public static final float PLAYER_CAUSED_BLOCK_DAYS = 270f;

    /** Vanilla pirate-style raids cap their Stability penalty at three points. */
    public static final float BLACK_FLEET_ASSAULT_PRESSURE_DAYS = 270f;

    public static float pressureDays(float effectDays, int unrestPoints) {
        float unrestDays = Math.max(0, unrestPoints) * RecentUnrest.DECREASE_DAYS;
        return Math.max(MIN_EXILES_PRESSURE_DAYS, Math.max(effectDays, unrestDays));
    }
}
