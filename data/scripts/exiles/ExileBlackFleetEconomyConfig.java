package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

public class ExileBlackFleetEconomyConfig {
    public float cap = 120f, initial = 90f, drain = 1f, threshold = 30f;
    public float assaultGain = 90f, extortionGain = 30f, assaultLoss = 30f, extortionLoss = 15f;
    public float window = 120f, allowance = 60f;
    public int supplyBoost = 1, lootCap = 6;
    public float returnTimeout = 365f, returnFraction = 0.5f;
    public float scarDays = 60f, shortageDays = 30f, portDays = 30f;
    public int shortageUnits = 3, producedCount = 3;
    public float accessPenalty = 0.2f, defenseMult = 0.75f;
    public static ExileBlackFleetEconomyConfig load() {
        try {
            JSONObject root = Global.getSettings().loadJSON("data/config/exiles/director_config.json")
                    .getJSONObject("exileblackfleet");
            ExileBlackFleetEconomyConfig c = new ExileBlackFleetEconomyConfig();
            JSONObject p = root.optJSONObject("prosperity");
            if (p != null) {
                c.cap = number(p, "maxReserveDays", c.cap, 1, 3650);
                c.initial = number(p, "startingReserveDays", c.initial, 0, c.cap);
                c.drain = number(p, "dailyConsumption", c.drain, 0.01f, 100);
                c.threshold = number(p, "recoveryThresholdDays", c.threshold, 0.01f, c.cap);
                c.assaultGain = number(p, "assaultRewardDays", c.assaultGain, 0, c.cap);
                c.extortionGain = number(p, "extortionRewardDays", c.extortionGain, 0, c.cap);
                c.assaultLoss = number(p, "assaultDefeatPenaltyDays", c.assaultLoss, 0, c.cap);
                c.extortionLoss = number(p, "extortionLossPenaltyDays", c.extortionLoss, 0, c.cap);
                c.window = number(p, "extortionWindowDays", c.window, 1, 3650);
                c.allowance = number(p, "extortionAllowanceDays", c.allowance, 0, 3650);
                c.supplyBoost = integer(p, "supplyBoost", c.supplyBoost, 0, 10);
                c.lootCap = integer(p, "maxLootCommodities", c.lootCap, 1, 12);
                c.returnTimeout = number(p, "returnTimeoutDays", c.returnTimeout, 30, 730);
                c.returnFraction = number(p, "minimumReturnFraction", c.returnFraction, 0.01f, 1);
            }
            JSONObject s = root.optJSONObject("sacked");
            if (s != null) {
                c.scarDays = number(s, "durationDays", c.scarDays, 1, 365);
                c.shortageDays = number(s, "shortageDays", c.shortageDays, 1, c.scarDays);
                c.portDays = number(s, "portDisruptionDays", c.portDays, 1, 180);
                c.shortageUnits = integer(s, "shortageUnits", c.shortageUnits, 0, 10);
                c.producedCount = integer(s, "producedCommodityCount", c.producedCount, 0, 10);
                c.accessPenalty = number(s, "accessibilityPenalty", c.accessPenalty, 0, 1);
                c.defenseMult = number(s, "groundDefenseMultiplier", c.defenseMult, 0.1f, 1);
            }
            return c;
        } catch (Exception e) { throw new IllegalArgumentException("Invalid Exiles Black Fleet economy config", e); }
    }
    private static float number(JSONObject obj, String key, float fallback, float min, float max) throws Exception {
        Object raw = obj.has(key) ? obj.get(key) : Float.valueOf(fallback);
        if (!(raw instanceof Number)) throw new IllegalArgumentException(key + " must be numeric");
        float n = ((Number) raw).floatValue();
        if (Float.isNaN(n) || n < min || n > max) throw new IllegalArgumentException(key + " outside " + min + ".." + max);
        return n;
    }
    private static int integer(JSONObject obj, String key, int fallback, int min, int max) throws Exception {
        float n = number(obj, key, fallback, min, max);
        if (n != (int)n) throw new IllegalArgumentException(key + " must be whole");
        return (int)n;
    }
}
