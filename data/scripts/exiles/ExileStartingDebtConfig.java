package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONObject;

/** Terms are snapshotted at new game; only enabled is refreshed on load. */
public class ExileStartingDebtConfig {
    public boolean enabled = true;
    public int gracePeriodMonths = 1;
    public int[] months = new int[] {6, 6, 12, 24};
    public int[] payments = new int[] {10000, 20000, 35000, 50000};
    public static final String PATH = "data/config/exiles/director_config.json";

    public static ExileStartingDebtConfig parse(JSONObject cfg) throws Exception {
        ExileStartingDebtConfig result = new ExileStartingDebtConfig();
        if (cfg == null) return result;
        if (cfg.has("enabled")) result.enabled = cfg.getBoolean("enabled");
        if (cfg.has("gracePeriodMonths")) {
            result.gracePeriodMonths = whole(cfg, "gracePeriodMonths", 0, 1200);
        }
        if (cfg.has("paymentSchedule")) {
            JSONArray rows = cfg.getJSONArray("paymentSchedule");
            if (rows.length() < 1 || rows.length() > 100) {
                throw new IllegalArgumentException("paymentSchedule must have 1-100 rows");
            }
            result.months = new int[rows.length()];
            result.payments = new int[rows.length()];
            long total = 0;
            long duration = 0;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                result.months[i] = whole(row, "months", 1, 1200);
                result.payments[i] = whole(row, "monthlyCredits", 1, 10000000);
                duration += result.months[i];
                total += (long) result.months[i] * result.payments[i];
            }
            if (duration > 1200 || total > 100000000) {
                throw new IllegalArgumentException("schedule exceeds 1200 months or 100 million credits");
            }
        }
        return result;
    }

    private static int whole(JSONObject row, String key, int min, int max) throws Exception {
        Object raw = row.get(key);
        if (!(raw instanceof Number)) throw new IllegalArgumentException(key + " must be an integer");
        double value = ((Number) raw).doubleValue();
        if (Double.isNaN(value) || value < min || value > max || value != Math.floor(value)) {
            throw new IllegalArgumentException(key + " must be an integer in " + min + ".." + max);
        }
        return (int) value;
    }

    public static ExileStartingDebtConfig load() {
        try {
            JSONObject root = Global.getSettings().loadJSON(PATH);
            return parse(root.has("startingDebt") ? root.getJSONObject("startingDebt") : null);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Exiles startingDebt configuration invalid in " + PATH
                    + ": " + ex.getMessage(), ex);
        }
    }

    public static boolean loadEnabled() {
        try {
            JSONObject root = Global.getSettings().loadJSON(PATH);
            JSONObject cfg = root.has("startingDebt") ? root.getJSONObject("startingDebt") : null;
            return cfg == null || !cfg.has("enabled") || cfg.getBoolean("enabled");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Exiles startingDebt.enabled must be true or false", ex);
        }
    }
}
