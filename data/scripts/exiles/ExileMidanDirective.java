package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * MIDAN'S DIRECTIVE TO ITS PROXY -- the one-way seam between the Heg-League war director and the
 * Black Fleet director (war doc sec 6.2, PASS 7 PROXY DESIGN, ruled 2026-10-19; build plan step 7f).
 *
 * <p><b>Shared vocabulary, not control flow.</b> The war side (the Midan module and the relief
 * manager) WRITES and CLEARS the directive; the Black Fleet side only READS it, on its own cadence,
 * and never learns who wrote it. Neither side calls the other (CLAUDE.md sec 6).
 *
 * <p><b>The key:</b> one sector-memory String, {@code TYPE|marketId|serial}. The serial is the
 * clock timestamp at writing, so a reader can tell two directives for the same market apart and
 * act on each once. The type field is ruled (2026-10-19); {@code CONVOY_AMBUSH} is the only type
 * built ({@code RAID_FOCUS} skipped, Sean 2026-11-01). The memory expiry is a backstop only: the
 * relief manager clears the directive when the convoy it names unloads, dies or is dropped.
 *
 * <p><b>Convoy marking:</b> relief convoys carry {@link ExileHegLeagueRecoveryManager#RELIEF_FLEET_FLAG}
 * and, since 7f, {@link #RELIEF_DEST_KEY} (their destination market id), so the reader can find the
 * convoy a directive names.
 */
public class ExileMidanDirective {

    public static final String KEY = "$exiles_midan_directive";
    public static final String TYPE_CONVOY_AMBUSH = "CONVOY_AMBUSH";
    /** Fleet memory on a relief convoy: the market id it is carrying relief to. */
    public static final String RELIEF_DEST_KEY = "$exiles_hl_relief_dest";
    public static final String CONVOY_FLAG = ExileHegLeagueRecoveryManager.RELIEF_FLEET_FLAG;

    private ExileMidanDirective() {
    }

    public static void write(String type, String marketId, float expireDays) {
        String value = type + "|" + marketId + "|" + Global.getSector().getClock().getTimestamp();
        mem().set(KEY, value, Math.max(1f, expireDays));
    }

    /** The whole directive string (its id), or null when none is active. */
    public static String getActive() {
        Object v = mem().get(KEY);
        return v instanceof String ? (String) v : null;
    }

    public static boolean isActive() {
        return getActive() != null;
    }

    public static String getType(String directive) {
        return part(directive, 0);
    }

    public static String getMarketId(String directive) {
        return part(directive, 1);
    }

    /** True when the active directive is a convoy ambush naming this market. */
    public static boolean isConvoyAmbushFor(String marketId) {
        String d = getActive();
        return d != null && marketId != null && TYPE_CONVOY_AMBUSH.equals(getType(d))
                && marketId.equals(getMarketId(d));
    }

    /** Clears the active directive if it names this market. @return true if one was cleared. */
    public static boolean clearIfFor(String marketId) {
        String d = getActive();
        if (d == null || marketId == null || !marketId.equals(getMarketId(d))) return false;
        mem().unset(KEY);
        return true;
    }

    protected static String part(String directive, int index) {
        if (directive == null) return null;
        String[] parts = directive.split("\\|");
        return index < parts.length ? parts[index] : null;
    }

    protected static MemoryAPI mem() {
        return Global.getSector().getMemoryWithoutUpdate();
    }
}
