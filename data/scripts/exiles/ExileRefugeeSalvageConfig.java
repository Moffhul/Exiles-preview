package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

/** Author-owned tuning for bounded Refugee battlefield cleanup. */
public class ExileRefugeeSalvageConfig {

    public static final String CONFIG_PATH = "data/config/exiles/director_config.json";
    public static final String FACTION_KEY = "exilerefugee";
    public static final String CONFIG_KEY = "salvageExpeditions";

    public boolean enabled = true;
    public boolean diagnostics = false;
    /** Shared Refugee testing channel; read from the faction root beside convoy tuning. */
    public boolean debugMessages = false;
    public int maxActiveExpeditions = 3;
    public float cycleDays = 1f;
    public float playerSpawnRangeLY = 1.6f;
    public int maxPerSystem = 1;
    public float responseMinDays = 2f;
    public float responseMaxDays = 4f;
    public float systemLaunchSpacingDays = 5f;
    public float maxCollectionDays = 15f;
    public float returnCargoFraction = 0.9f;
    public float commodityDeliveryFraction = 0.5f;
    public float commodityBonusCap = 500f;
    public float commodityBonusDays = 30f;
    // Retained legacy keys: outbound route/preparation tuning no longer drives this system.
    public float preparationDays = 2f;
    public float salvageDays = 0.5f;
    public float homeOrbitDays = 2f;
    public float travelDaysPerLY = 1.5f;
    public float maxDistanceLY = 35f;

    public float liveExilesRaidWeight = 4f;
    public float distanceScaleLY = 12f;

    public float combatFp = 10f;
    public int maxShips = 5;
    public int maxShipSize = 2;

    public float dangerStrengthMult = 0.75f;
    public float dangerCheckMinDays = 0.25f;
    public float dangerCheckMaxDays = 0.5f;
    public float hostileMarketSafetyRadius = 2500f;
    /** 2026-09-21: hostile fleets sourced from an in-system market defer a launch only within
     *  launchThreatRadius of the wreck; false = the old system-wide screen. */
    public boolean localDefenseExemptAtLaunch = true;
    public float launchThreatRadius = 2500f;

    public static ExileRefugeeSalvageConfig load() {
        ExileRefugeeSalvageConfig result = new ExileRefugeeSalvageConfig();
        try {
            JSONObject root = Global.getSettings().loadJSON(CONFIG_PATH);
            JSONObject faction = root.optJSONObject(FACTION_KEY);
            if (faction == null) return result;
            result.debugMessages = faction.optBoolean(
                    "debugMessages", result.debugMessages);
            JSONObject cfg = faction.optJSONObject(CONFIG_KEY);
            if (cfg == null) return result;

            result.enabled = cfg.optBoolean("enabled", result.enabled);
            result.diagnostics = cfg.optBoolean("diagnostics", result.diagnostics);
            result.maxActiveExpeditions = cfg.optInt(
                    "maxActiveExpeditions", result.maxActiveExpeditions);
            result.cycleDays = (float) cfg.optDouble("cycleDays", result.cycleDays);
            result.playerSpawnRangeLY = (float) cfg.optDouble("playerSpawnRangeLY", result.playerSpawnRangeLY);
            result.maxPerSystem = cfg.optInt("maxPerSystem", result.maxPerSystem);
            result.responseMinDays = (float) cfg.optDouble("responseMinDays", result.responseMinDays);
            result.responseMaxDays = (float) cfg.optDouble("responseMaxDays", result.responseMaxDays);
            result.systemLaunchSpacingDays = (float) cfg.optDouble("systemLaunchSpacingDays", result.systemLaunchSpacingDays);
            result.maxCollectionDays = (float) cfg.optDouble("maxCollectionDays", result.maxCollectionDays);
            result.returnCargoFraction = (float) cfg.optDouble("returnCargoFraction", result.returnCargoFraction);
            result.commodityDeliveryFraction = (float) cfg.optDouble("commodityDeliveryFraction", result.commodityDeliveryFraction);
            result.commodityBonusCap = (float) cfg.optDouble("commodityBonusCap", result.commodityBonusCap);
            result.commodityBonusDays = (float) cfg.optDouble("commodityBonusDays", result.commodityBonusDays);
            result.preparationDays = (float) cfg.optDouble(
                    "preparationDays", result.preparationDays);
            result.salvageDays = (float) cfg.optDouble("salvageDays", result.salvageDays);
            result.homeOrbitDays = (float) cfg.optDouble(
                    "homeOrbitDays", result.homeOrbitDays);
            result.travelDaysPerLY = (float) cfg.optDouble(
                    "travelDaysPerLY", result.travelDaysPerLY);
            result.maxDistanceLY = (float) cfg.optDouble(
                    "maxDistanceLY", result.maxDistanceLY);

            result.liveExilesRaidWeight = (float) cfg.optDouble(
                    "liveExilesRaidWeight", result.liveExilesRaidWeight);
            result.distanceScaleLY = (float) cfg.optDouble(
                    "distanceScaleLY", result.distanceScaleLY);

            JSONObject fleet = cfg.optJSONObject("fleet");
            if (fleet != null) {
                result.combatFp = (float) fleet.optDouble("combatFp", result.combatFp);
                result.maxShips = fleet.optInt("maxShips", result.maxShips);
                result.maxShipSize = fleet.optInt("maxShipSize", result.maxShipSize);
            }

            JSONObject danger = cfg.optJSONObject("danger");
            if (danger != null) {
                result.dangerStrengthMult = (float) danger.optDouble(
                        "strengthMult", result.dangerStrengthMult);
                result.dangerCheckMinDays = (float) danger.optDouble(
                        "checkMinDays", result.dangerCheckMinDays);
                result.dangerCheckMaxDays = (float) danger.optDouble(
                        "checkMaxDays", result.dangerCheckMaxDays);
                result.hostileMarketSafetyRadius = (float) danger.optDouble(
                        "hostileMarketSafetyRadius", result.hostileMarketSafetyRadius);
                result.localDefenseExemptAtLaunch = danger.optBoolean(
                        "localDefenseExemptAtLaunch", result.localDefenseExemptAtLaunch);
                result.launchThreatRadius = (float) danger.optDouble(
                        "launchThreatRadius", result.launchThreatRadius);
            }
        } catch (Exception ex) {
            Global.getLogger(ExileRefugeeSalvageConfig.class).warn(
                    "Refugee salvage expedition config load failed; using code defaults", ex);
        }
        result.normalize();
        return result;
    }

    protected void normalize() {
        if (maxActiveExpeditions < 0) maxActiveExpeditions = 0;
        if (maxActiveExpeditions > 12) maxActiveExpeditions = 12;
        maxPerSystem = Math.max(1, Math.min(3, maxPerSystem));
        if (Float.isNaN(playerSpawnRangeLY) || Float.isInfinite(playerSpawnRangeLY)) playerSpawnRangeLY = 1.6f;
        playerSpawnRangeLY = Math.max(0, Math.min(10, playerSpawnRangeLY));
        responseMinDays = Math.max(0, responseMinDays);
        responseMaxDays = Math.max(responseMinDays, responseMaxDays);
        systemLaunchSpacingDays = Math.max(1, systemLaunchSpacingDays);
        maxCollectionDays = Math.max(1, Math.min(60, maxCollectionDays));
        returnCargoFraction = Math.max(0.1f, Math.min(1, returnCargoFraction));
        commodityDeliveryFraction = Math.max(0, Math.min(1, commodityDeliveryFraction));
        commodityBonusCap = Math.max(0, Math.min(10000, commodityBonusCap));
        commodityBonusDays = Math.max(1, Math.min(120, commodityBonusDays));
        if (cycleDays < 1f) cycleDays = 1f;
        if (preparationDays < 0f) preparationDays = 0f;
        if (salvageDays < 0.25f) salvageDays = 0.25f;
        if (homeOrbitDays < 0f) homeOrbitDays = 0f;
        if (travelDaysPerLY < 0.1f) travelDaysPerLY = 0.1f;
        if (maxDistanceLY < 1f) maxDistanceLY = 1f;
        if (distanceScaleLY < 1f) distanceScaleLY = 1f;

        if (liveExilesRaidWeight < 0f) liveExilesRaidWeight = 0f;

        if (combatFp < 1f) combatFp = 1f;
        if (combatFp > 30f) combatFp = 30f;
        // Calceus, Shepherd, Tarsus, plus one or two escorts.
        if (maxShips < 4) maxShips = 4;
        if (maxShips > 5) maxShips = 5;
        if (maxShipSize < 1) maxShipSize = 1;
        if (maxShipSize > 2) maxShipSize = 2;

        if (dangerStrengthMult < 0.1f) dangerStrengthMult = 0.1f;
        if (dangerCheckMinDays < 0.05f) dangerCheckMinDays = 0.05f;
        if (dangerCheckMaxDays < dangerCheckMinDays) {
            dangerCheckMaxDays = dangerCheckMinDays;
        }
        if (Float.isNaN(hostileMarketSafetyRadius) || Float.isInfinite(hostileMarketSafetyRadius)) {
            hostileMarketSafetyRadius = 2500f;
        }
        hostileMarketSafetyRadius = Math.max(500f, Math.min(10000f, hostileMarketSafetyRadius));
        if (Float.isNaN(launchThreatRadius) || Float.isInfinite(launchThreatRadius)) {
            launchThreatRadius = 2500f;
        }
        launchThreatRadius = Math.max(500f, Math.min(10000f, launchThreatRadius));
    }
}
