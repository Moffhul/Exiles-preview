package data.scripts.exiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

/**
 * Tuning config for the Omicron Director — loaded from
 * {@code data/config/exiles/director_config.json} (loose JSON, per-faction top-level keys;
 * see docs/war-capacity-design.md §7). NEVER persisted: the Director holds it in a transient
 * field and reloads on game load, so tuning edits apply to existing saves on restart.
 *
 * <p>Code defaults below mirror the pre-externalization constants and apply when the file or
 * an individual field is missing — a broken/absent JSON degrades to the shipped behavior, it
 * never crashes the Director.
 */
public class OmicronDirectorConfig {

    public static final String PATH = "data/config/exiles/director_config.json";
    public static final String FACTION_KEY = "exileomicron";

    // Asset tags — applied to the facility station fleets in ExileOmicron0 at generation,
    // scanned by the Director. (The Nexus tag itself lives in OmicronDirector.NEXUS_TAG.)
    public static final String ASSET_TAG_UCNEXUS = "exile_omicron_asset_ucnexus";
    public static final String ASSET_TAG_MINING = "exile_omicron_asset_mining";
    public static final String ASSET_TAG_FUEL = "exile_omicron_asset_fuel";
    public static final String ASSET_TAG_PRIME_MINING_1 = "exile_omicron_asset_prime_mining_1";
    public static final String ASSET_TAG_PRIME_MINING_2 = "exile_omicron_asset_prime_mining_2";
    public static final String ASSET_TAG_PRIME_FUEL_1 = "exile_omicron_asset_prime_fuel_1";
    public static final String ASSET_TAG_CIPHER_FUEL_1 = "exile_omicron_asset_cipher_fuel_1";
    public static final String ASSET_TAG_CIPHER_FUEL_2 = "exile_omicron_asset_cipher_fuel_2";
    public static final String ASSET_TAG_VIGIL_MINING_1 = "exile_omicron_asset_vigil_mining_1";
    public static final String ASSET_TAG_VIGIL_FUEL_1 = "exile_omicron_asset_vigil_fuel_1";
    public static final String ASSET_TAG_LATTICE_MINING_1 = "exile_omicron_asset_lattice_mining_1";

    // Array tags — the Sensor/Comm buoy ENTITIES. Deliberately NOT "asset" tags: the arrays
    // are not war-capacity assets (ratified v2.2) — they are vanilla-function objectives
    // behind docked drone screens (OmicronDefenseFleetManager array tiers). Never destroyed;
    // killing the screen unlocks the stock buoy interaction until it respawns.
    public static final String ARRAY_TAG_SENSOR = "exile_omicron_array_sensor";
    public static final String ARRAY_TAG_COMM = "exile_omicron_array_comm";
    /** Shared tag on both buoys — the rules.csv defender-gate rows key on this. */
    public static final String ARRAY_TAG = "exile_omicron_array";

    /** One Omicron star system participating in the Director (war-capacity node). */
    public static class Node {
        public String id;
        public String system;
        /** Under-construction node: gains raid sourcing at its raid stage and becomes an ordinary
         *  full Nexus source (including incursions/responses) after completion. */
        public boolean uc;

        public Node(String id, String system) {
            this.id = id;
            this.system = system;
            this.uc = false;
        }
    }

    /** One destroyable facility type: tag to scan for + the axis modifiers its death applies. */
    public static class Asset {
        public String key;
        public String type;
        public String nodeId;
        public String tag;
        public boolean freezeEscalation;
        /** Only scanned on UC nodes (the ucnexus row — active nodes no longer host one). */
        public boolean ucOnly;
        /** Station rebuild-while-Nexus-alive (RATIFIED 2026-07-06, war-capacity-design.md #8):
         *  days until a destroyed station respawns, iff its node's Nexus is still alive; the
         *  pending rebuild is cancelled if the Nexus dies first. 0 = never rebuilds. */
        public float rebuildDays;
        public HashMap mods; // statId (String) -> Float multiplier

        public Asset(String key, String type, String nodeId, String tag, boolean freezeEscalation) {
            this.key = key;
            this.type = type;
            this.nodeId = nodeId;
            this.tag = tag;
            this.freezeEscalation = freezeEscalation;
            this.ucOnly = false;
            this.rebuildDays = 0f;
            this.mods = new HashMap();
        }
    }

    /** One maturation stage of the UC system (spread build): at a progress threshold, either
     *  spawn a facility (build = asset key with a persisted placement spec) or turn on raids. */
    public static class GrowthStage {
        public float at;      // growth-progress threshold (0..1)
        public String build;  // asset key to spawn from its spec, or null
        public boolean raids; // true => the UC node may launch (light) raids from here on
        public boolean complete; // true => replace the damaged UC-Nexus with a full Nexus

        public GrowthStage(float at, String build, boolean raids) {
            this.at = at;
            this.build = build;
            this.raids = raids;
            this.complete = false;
        }
    }

    /** One home-defense tier (asset model §2b ladder). NEVER persisted — managers copy the
     *  primitives out; keep it that way (the config object must not enter the save). */
    public static class DefenseTier {
        public float thresholdLY;
        public int maxFleets;      // close-defense guards, anchored to the station
        public int patrolFleets;   // roamers (jump points / system sweeps) on top of the guards
        public float respawnDelayDays;
        public int fpMin;
        public int fpMax;

        public DefenseTier(float thresholdLY, int maxFleets, int patrolFleets,
                           float respawnDelayDays, int fpMin, int fpMax) {
            this.thresholdLY = thresholdLY;
            this.maxFleets = maxFleets;
            this.patrolFleets = patrolFleets;
            this.respawnDelayDays = respawnDelayDays;
            this.fpMin = fpMin;
            this.fpMax = fpMax;
        }
    }

    /** One Tri-Tachyon station carrying a dormant Omicron complement. The market list is
     *  explicit by design: Omicron protects the assets covered by its original mandate, not
     *  every market Tri-Tachyon might acquire later through conquest or another mod. */
    public static class ProtectedMarket {
        public String id;
        public boolean primary;
        public int localFpMin;
        public int localFpMax;
        public float respawnDays;
        /** Minimum hostile-operation FP before a strategic response is considered. Station
         *  sleepers do not activate merely because an operation targets their system. */
        public float responseMinThreatFp;
        /**
         * Corporate-umbrella progress (0..1) required before this market's complement is placed.
         * 0 = present from campaign start (the original behaviour and the default).
         *
         * <p>The umbrella uses its own elapsed-time ramp. It is deliberately independent from
         * Lattice construction and combat maturity so damaging one subsystem does not produce
         * an unrelated permanent change in Tri-Tachyon protection.
         */
        public float spawnAtGrowth;
        /**
         * Fallback: days since this manager started after which the complement is placed even
         * if {@link #spawnAtGrowth} was never reached. 0 = no fallback. Retained as a separate
         * safety valve even though the normal umbrella clock no longer freezes.
         */
        public float spawnAfterDays;

        public ProtectedMarket(String id, boolean primary, int localFpMin, int localFpMax,
                               float respawnDays, float responseMinThreatFp,
                               float spawnAtGrowth, float spawnAfterDays) {
            this.id = id;
            this.primary = primary;
            this.localFpMin = localFpMin;
            this.localFpMax = localFpMax;
            this.respawnDays = respawnDays;
            this.responseMinThreatFp = responseMinThreatFp;
            this.spawnAtGrowth = spawnAtGrowth;
            this.spawnAfterDays = spawnAfterDays;
        }
    }

    public ArrayList nodes = new ArrayList();                 // Node
    public ArrayList assets = new ArrayList();                // Asset
    public ArrayList defaultTargetFactions = new ArrayList(); // String

    // --- Raids (frequent, smaller FP pool -> mostly small/medium fleets: the derelict-farming
    //     comp skew; heavies stay incursion-only). spawnFP is the op's total FP budget; the
    //     vanilla AssembleStage splits it into fleets. maxActive is retained as a legacy config
    //     seam; readiness.globalMaxActive is the live faction-wide cap. ---
    public float raidIntervalMinDays = 60f;
    public float raidIntervalMaxDays = 90f;
    public float raidStartFpMin = 70f;
    public float raidStartFpMax = 120f;
    public float raidSpawnFpMin = 200f;
    public float raidSpawnFpMax = 360f;
    public int raidMaxActive = 2; // legacy parser field; see globalMaxActive
    /** Campaign-start default; overridden at runtime by the persistent switch (quest hook). */
    public boolean raidEnabled = true;

    // --- Incursions (rare, big FP pool -> includes capital-tier fleets) ---
    public float incursionIntervalMinDays = 180f;
    public float incursionIntervalMaxDays = 270f;
    public float incursionStartFpMin = 180f;
    public float incursionStartFpMax = 300f;
    public float incursionSpawnFpMin = 700f;
    public float incursionSpawnFpMax = 1100f;
    public int incursionMaxActive = 1; // legacy parser field; see globalMaxIncursions
    /** Campaign-start default; overridden at runtime by the persistent switch (quest hook). */
    public boolean incursionEnabled = true;

    // --- Faction-wide readiness scheduler ---
    public float readinessMax = 2f;
    public float readinessPerYear = 9f;
    public float readinessRaidCost = 1f;
    public float readinessIncursionCost = 2f;
    public float readinessFailureFraction = 0.25f;
    public float readinessRetryDays = 30f;
    public float readinessNoEnemyRetryDays = 90f;
    public int globalMaxActive = 2;
    public int globalMaxIncursions = 1;
    public float nexusNormalization = 4f;
    public float fuelNormalization = 4f;
    public float miningNormalization = 4f;
    public float fuelBaseEfficiency = 0.20f;
    public float miningBaseEfficiency = 0.20f;

    // --- Combat maturation, deliberately independent from Lattice construction ---
    public float combatMaturityDays = 1080f;
    public float combatMaturityExponent = 1.5f;
    public float eventOverdriveCap = 1.10f;

    // --- Operation shape (shared) ---
    /** Stage abort threshold as a fraction of the op's spawn FP (vanilla pirate raids use 0.5). */
    public float abortFraction = 0.5f;
    /** Per-frigate-equivalent credits the RAIDED faction pays for player kills of raiders; 0 disables. */
    public float bountyPerFrigate = 3000f;
    /** Legacy collateral switch. 0 = strict target discipline; any positive value lets hostile,
     *  non-exempt bystander markets enter the fixed objective-budget draw. The numeric value is
     *  retained for config/save compatibility but is no longer a per-market probability. */
    public float collateralChance = 0.5f;
    /** Feasibility gate: a candidate colony is a valid op target only if its defense estimate
     *  (system defenders + its station) is at most spawnFP * this. Stops ops that would arrive
     *  outmatched and instantly abort (the vanilla action stage refuses every colony whose
     *  defense exceeds raid strength). Values above 1 let borderline attempts through;
     *  0 disables the gate. */
    public float feasibilityMult = 1.25f;
    /** Faction ids never hit as collateral, exempt by BALANCE. Two exemptions are NOT listed here
     *  because they are canon rather than tuning: player colonies (code-exempt; player-directed
     *  ops are reserved for a future quest-controlled crisis mechanism) and the Refugee faction
     *  (see ExileProtectedFactions, author ruling 2026-07-29). Starts empty. */
    public ArrayList collateralExemptFactions = new ArrayList(); // String

    // --- Colony disruption (deciv-safe caps; see OmicronDirector docs) ---
    public int disruptShortMinDays = 20;
    public int disruptShortMaxDays = 30;
    public int disruptLongMinDays = 30;
    public int disruptLongMaxDays = 60;
    public float disruptSpaceportChance = 0.6f;
    public float disruptFarmingChance = 0.6f;
    public float disruptOtherChance = 0.5f;

    // --- Home-defense gauntlet (asset model §2b: Nexus = Star-Fortress+ via a large elite
    //     guard, UC-Nexus = Battlestation-tier, Mining/Fuel = light Orbital-tier guard).
    //     maxFleets = anchored close-defense; patrolFleets = roamers (jump points / sweeps)
    //     on top. Both 0 disables a tier. Defenders only exist near the player. ---
    public DefenseTier defenseNexus = new DefenseTier(2f, 2, 3, 20f, 100, 160);
    public DefenseTier defenseUcNexus = new DefenseTier(1.5f, 2, 1, 25f, 70, 110);
    public DefenseTier defenseMining = new DefenseTier(1f, 1, 0, 30f, 50, 80);
    public DefenseTier defenseFuel = new DefenseTier(1f, 1, 0, 30f, 50, 80);
    // Array tiers: DIALOG-LAYER drone defenders (Domain survey-ship pattern — no campaign
    // fleets; DefenderDataOverride + rules gate). Only fpMin/fpMax are consumed (defender
    // strength); the ~30d screen-respawn window lives in the mod rules.csv rows.
    public DefenseTier defenseSensor = new DefenseTier(1f, 1, 0, 30f, 35, 50);
    public DefenseTier defenseComm = new DefenseTier(1f, 1, 0, 30f, 35, 50);

    // --- Tri-Tachyon protection mandate. Station complements remain hidden until their own
    //     station is attacked. Culann also houses one large, pre-positioned strategic reserve
    //     for Black-Fleet-Assault-scale threats in Hybrasil. Other sufficiently large,
    //     forewarned threats may still draw a mature-Nexus response when travel time permits. ---
    public boolean ttDefenseEnabled = true;
    public float ttDefenseScanIntervalDays = 0.25f;
    public float ttDefenseStandDownDays = 5f;
    public float ttDefenseStationActivationRange = 1500f;
    public String ttDefenseReserveMarketId = "culann";
    public int ttDefenseReserveFpMin = 180;
    public int ttDefenseReserveFpMax = 220;
    public float ttDefenseReserveMinThreatFp = 350f;
    public float ttDefenseReserveRespawnDays = 180f;
    public float ttDefenseReserveActivationLeadDays = 2f;
    public float ttDefenseResponsePrepareDays = 3f;
    public float ttDefenseResponseLateToleranceDays = 10f;
    public float ttDefenseResponseFpMult = 0.65f;
    public int ttDefenseResponseFpMin = 90;
    public int ttDefenseResponseFpMax = 220;
    public float ttUmbrellaRampDays = 365f;
    public ArrayList ttProtectedMarkets = new ArrayList(); // ProtectedMarket

    /** Tier lookup by the key OmicronDefenseFleetManager derives from station/array tags. */
    public DefenseTier defenseTier(String key) {
        Asset asset = assetByKey(key);
        if (asset != null && asset.type != null) key = asset.type;
        if ("nexus".equals(key)) return defenseNexus;
        if ("ucnexus".equals(key)) return defenseUcNexus;
        if ("mining".equals(key)) return defenseMining;
        if ("fuel".equals(key)) return defenseFuel;
        if ("sensor".equals(key)) return defenseSensor;
        if ("comm".equals(key)) return defenseComm;
        return null;
    }

    // --- Lattice construction clock. The cap remains as a legacy/quest escalation seam;
    //     ordinary operation FP is governed by combat maturity and Mining capacity instead. ---
    public float escalationRampDays = 540f;
    public float escalationCap = 1.0f;

    // --- Lattice staged construction on the construction clock ---
    // growth.raidFpMult removed by Sean's ruling 2026-10-06 (OMI-7): op size is rolled
    // network-wide since the readiness pool, so a Lattice-only multiplier has no place.
    public ArrayList growthStages = new ArrayList(); // GrowthStage, ascending by at

    /** Legibility bands, descending; index below a threshold = deeper band. */
    public float[] thresholds = new float[] {0.85f, 0.65f, 0.45f};
    // debugMessages removed by Sean's ruling 2026-10-06 (OMI-8): nothing read it, and every
    // Omicron campaign message is a gameplay message. Post-mortems use starsector.log.

    /**
     * The distinct axes actually fed by configured assets — the legibility index averages
     * over these (so threshold bands track whatever the config wires up, and reserved axes
     * like reach/targeting/precision never dilute the index). Computed once at load.
     */
    public ArrayList capacityAxes = new ArrayList(); // String statIds

    public static OmicronDirectorConfig load() {
        OmicronDirectorConfig c = new OmicronDirectorConfig();
        c.fillDefaults();
        try {
            JSONObject root = Global.getSettings().loadJSON(PATH);
            JSONObject f = root.optJSONObject(FACTION_KEY);
            if (f != null) {
                c.readFaction(f);
                Global.getLogger(OmicronDirectorConfig.class).info(
                        "Omicron director config loaded from " + PATH);
            }
        } catch (Exception e) {
            Global.getLogger(OmicronDirectorConfig.class).warn(
                    "Omicron director config not loaded (" + e.getMessage() + "); using code defaults");
        }
        c.computeCapacityAxes();
        return c;
    }

    private void computeCapacityAxes() {
        capacityAxes.clear();
        for (int i = 0; i < assets.size(); i++) {
            Asset a = (Asset) assets.get(i);
            Iterator keys = a.mods.keySet().iterator();
            while (keys.hasNext()) {
                String statId = (String) keys.next();
                if (!capacityAxes.contains(statId)) capacityAxes.add(statId);
            }
        }
        if (capacityAxes.isEmpty()) {
            capacityAxes.add(OmicronWarCapacity.STAT_STRENGTH);
            capacityAxes.add(OmicronWarCapacity.STAT_CADENCE);
        }
    }

    private void fillDefaults() {
        nodes.clear();
        Node prime = new Node("omicron_prime", "Omicron");
        nodes.add(prime);
        nodes.add(new Node("omicron_cipher", "Cipher"));
        nodes.add(new Node("omicron_vigil", "Vigil"));
        Node lattice = new Node("omicron_lattice", "Lattice");
        lattice.uc = true;
        nodes.add(lattice);

        defaultTargetFactions.clear();
        defaultTargetFactions.add("luddic_church");
        defaultTargetFactions.add("luddic_path");

        // Empty by default: the refugee exemption moved to ExileProtectedFactions (canon,
        // unconditional) so clearing this list can no longer expose them.
        collateralExemptFactions.clear();

        ttProtectedMarkets.clear();
        // Hybrasil is the corporate heartland. Its sleepers remain point-defense assets;
        // the separate Culann reserve answers only Black-Fleet-Assault-scale threats.
        // Heartland: covered from campaign start (spawnAtGrowth 0).
        ttProtectedMarkets.add(new ProtectedMarket("culann", true, 70, 100, 90f, 350f, 0f, 0f));
        ttProtectedMarkets.add(new ProtectedMarket("eochu_bres", true, 80, 110, 90f, 350f, 0f, 0f));
        // Outposts: the umbrella reaches them on its dedicated clock (see ProtectedMarket
        // javadoc). Code defaults mirror director_config.json so a missing config behaves the
        // same way rather than silently reverting to day-one coverage.
        ttProtectedMarkets.add(new ProtectedMarket("port_tse", false, 35, 55, 120f, 250f, 0.85f, 540f));
        ttProtectedMarkets.add(new ProtectedMarket("tibicena", false, 45, 65, 120f, 250f, 0.85f, 540f));

        assets.clear();
        Asset ucnexus = new Asset("lattice_ucnexus", "ucnexus", "omicron_lattice",
                ASSET_TAG_UCNEXUS, true);
        ucnexus.ucOnly = true; // spread build: only the UC system hosts a UC-Nexus
        assets.add(ucnexus);
        addSupportAsset("prime_mining_1", "mining", "omicron_prime", ASSET_TAG_PRIME_MINING_1);
        addSupportAsset("prime_mining_2", "mining", "omicron_prime", ASSET_TAG_PRIME_MINING_2);
        addSupportAsset("prime_fuel_1", "fuel", "omicron_prime", ASSET_TAG_PRIME_FUEL_1);
        addSupportAsset("cipher_fuel_1", "fuel", "omicron_cipher", ASSET_TAG_CIPHER_FUEL_1);
        addSupportAsset("cipher_fuel_2", "fuel", "omicron_cipher", ASSET_TAG_CIPHER_FUEL_2);
        addSupportAsset("vigil_mining_1", "mining", "omicron_vigil", ASSET_TAG_VIGIL_MINING_1);
        addSupportAsset("vigil_fuel_1", "fuel", "omicron_vigil", ASSET_TAG_VIGIL_FUEL_1);
        addSupportAsset("lattice_mining_1", "mining", "omicron_lattice", ASSET_TAG_LATTICE_MINING_1);

        growthStages.clear();
        growthStages.add(new GrowthStage(0.50f, "lattice_mining_1", false));
        growthStages.add(new GrowthStage(0.75f, null, true));
        GrowthStage completion = new GrowthStage(1.0f, null, false);
        completion.complete = true;
        growthStages.add(completion);
    }

    private void addSupportAsset(String key, String type, String nodeId, String tag) {
        Asset asset = new Asset(key, type, nodeId, tag, false);
        asset.rebuildDays = 240f;
        assets.add(asset);
    }

    /** The first staged-construction node, or null (legacy configs may omit one). */
    public Node ucNode() {
        for (int i = 0; i < nodes.size(); i++) {
            Node n = (Node) nodes.get(i);
            if (n.uc) return n;
        }
        return null;
    }

    /** Config asset row by key, or null. */
    public Asset assetByKey(String key) {
        for (int i = 0; i < assets.size(); i++) {
            Asset a = (Asset) assets.get(i);
            if (a.key.equals(key)) return a;
        }
        return null;
    }

    public ArrayList assetsForNode(String nodeId) {
        ArrayList result = new ArrayList();
        for (int i = 0; i < assets.size(); i++) {
            Asset a = (Asset) assets.get(i);
            if (a.nodeId == null || a.nodeId.equals(nodeId)) result.add(a);
        }
        return result;
    }

    /** The lowest growth-progress threshold that enables UC raids (>1 = never in this config). */
    public float growthRaidsAt() {
        float at = Float.MAX_VALUE;
        for (int i = 0; i < growthStages.size(); i++) {
            GrowthStage s = (GrowthStage) growthStages.get(i);
            if (s.raids && s.at < at) at = s.at;
        }
        return at;
    }

    public float growthCompletionAt() {
        for (int i = 0; i < growthStages.size(); i++) {
            GrowthStage s = (GrowthStage) growthStages.get(i);
            if (s.complete) return s.at;
        }
        return 1f;
    }

    private void readFaction(JSONObject f) throws Exception {
        JSONArray nodesArr = f.optJSONArray("nodes");
        if (nodesArr != null && nodesArr.length() > 0) {
            nodes.clear();
            for (int i = 0; i < nodesArr.length(); i++) {
                JSONObject n = nodesArr.getJSONObject(i);
                Node node = new Node(n.getString("id"), n.getString("system"));
                node.uc = n.optBoolean("uc", false);
                nodes.add(node);
            }
        }

        JSONArray targets = f.optJSONArray("defaultTargetFactions");
        if (targets != null && targets.length() > 0) {
            defaultTargetFactions.clear();
            for (int i = 0; i < targets.length(); i++) {
                defaultTargetFactions.add(targets.getString(i));
            }
        }

        JSONObject raid = f.optJSONObject("raid");
        if (raid != null) {
            float legacy = (float) raid.optDouble("intervalDays", 0f);
            if (legacy > 0f) {
                raidIntervalMinDays = legacy * 0.75f;
                raidIntervalMaxDays = legacy * 1.25f;
            }
            raidIntervalMinDays = (float) raid.optDouble("intervalMinDays", raidIntervalMinDays);
            raidIntervalMaxDays = (float) raid.optDouble("intervalMaxDays", raidIntervalMaxDays);
            raidStartFpMin = (float) raid.optDouble("startFpMin", raidStartFpMin);
            raidStartFpMax = (float) raid.optDouble("startFpMax", raidStartFpMax);
            raidSpawnFpMin = (float) raid.optDouble("spawnFpMin", raidSpawnFpMin);
            raidSpawnFpMax = (float) raid.optDouble("spawnFpMax", raidSpawnFpMax);
            raidMaxActive = raid.optInt("maxActive", raidMaxActive);
            raidEnabled = raid.optBoolean("enabled", raidEnabled);
        }

        JSONObject inc = f.optJSONObject("incursion");
        if (inc != null) {
            float legacy = (float) inc.optDouble("intervalDays", 0f);
            if (legacy > 0f) {
                incursionIntervalMinDays = legacy * 0.75f;
                incursionIntervalMaxDays = legacy * 1.25f;
            }
            incursionIntervalMinDays = (float) inc.optDouble(
                    "intervalMinDays", incursionIntervalMinDays);
            incursionIntervalMaxDays = (float) inc.optDouble(
                    "intervalMaxDays", incursionIntervalMaxDays);
            incursionStartFpMin = (float) inc.optDouble("startFpMin", incursionStartFpMin);
            incursionStartFpMax = (float) inc.optDouble("startFpMax", incursionStartFpMax);
            incursionSpawnFpMin = (float) inc.optDouble("spawnFpMin", incursionSpawnFpMin);
            incursionSpawnFpMax = (float) inc.optDouble("spawnFpMax", incursionSpawnFpMax);
            incursionMaxActive = inc.optInt("maxActive", incursionMaxActive);
            incursionEnabled = inc.optBoolean("enabled", incursionEnabled);
        }

        JSONObject readiness = f.optJSONObject("readiness");
        if (readiness != null) {
            readinessMax = (float) readiness.optDouble("max", readinessMax);
            readinessPerYear = (float) readiness.optDouble("perYear", readinessPerYear);
            readinessRaidCost = (float) readiness.optDouble("raidCost", readinessRaidCost);
            readinessIncursionCost = (float) readiness.optDouble(
                    "incursionCost", readinessIncursionCost);
            readinessFailureFraction = (float) readiness.optDouble(
                    "failureFraction", readinessFailureFraction);
            readinessRetryDays = (float) readiness.optDouble("retryDays", readinessRetryDays);
            readinessNoEnemyRetryDays = (float) readiness.optDouble(
                    "noEnemyRetryDays", readinessNoEnemyRetryDays);
            globalMaxActive = readiness.optInt("maxActive", globalMaxActive);
            globalMaxIncursions = readiness.optInt("maxIncursions", globalMaxIncursions);
            nexusNormalization = (float) readiness.optDouble(
                    "nexusNormalization", nexusNormalization);
            fuelNormalization = (float) readiness.optDouble(
                    "fuelNormalization", fuelNormalization);
            miningNormalization = (float) readiness.optDouble(
                    "miningNormalization", miningNormalization);
            fuelBaseEfficiency = (float) readiness.optDouble(
                    "fuelBaseEfficiency", fuelBaseEfficiency);
        }

        JSONObject maturity = f.optJSONObject("combatMaturity");
        if (maturity != null) {
            miningBaseEfficiency = Math.max(0f, Math.min(1f, (float) maturity.optDouble(
                    "miningBaseEfficiency", miningBaseEfficiency)));
            combatMaturityDays = (float) maturity.optDouble("days", combatMaturityDays);
            combatMaturityExponent = (float) maturity.optDouble(
                    "exponent", combatMaturityExponent);
            eventOverdriveCap = (float) maturity.optDouble(
                    "eventOverdriveCap", eventOverdriveCap);
        }

        JSONObject ops = f.optJSONObject("operations");
        if (ops != null) {
            abortFraction = (float) ops.optDouble("abortFraction", abortFraction);
            bountyPerFrigate = (float) ops.optDouble("bountyPerFrigate", bountyPerFrigate);
            collateralChance = (float) ops.optDouble("collateralChance", collateralChance);
            feasibilityMult = (float) ops.optDouble("feasibilityMult", feasibilityMult);
            // An explicit empty array clears the exemptions (unlike the other lists, "no one is
            // exempt" is a meaningful setting here). Refugees stay protected regardless — that
            // rule is canon and lives in ExileProtectedFactions, not in this list.
            JSONArray exempt = ops.optJSONArray("collateralExempt");
            if (exempt != null) {
                collateralExemptFactions.clear();
                for (int i = 0; i < exempt.length(); i++) {
                    collateralExemptFactions.add(exempt.getString(i));
                }
            }
        }

        JSONObject dis = f.optJSONObject("disruption");
        if (dis != null) {
            disruptShortMinDays = dis.optInt("shortMinDays", disruptShortMinDays);
            disruptShortMaxDays = dis.optInt("shortMaxDays", disruptShortMaxDays);
            disruptLongMinDays = dis.optInt("longMinDays", disruptLongMinDays);
            disruptLongMaxDays = dis.optInt("longMaxDays", disruptLongMaxDays);
            disruptSpaceportChance = (float) dis.optDouble("spaceportChance", disruptSpaceportChance);
            disruptFarmingChance = (float) dis.optDouble("farmingChance", disruptFarmingChance);
            disruptOtherChance = (float) dis.optDouble("otherChance", disruptOtherChance);
        }

        JSONObject esc = f.optJSONObject("escalation");
        if (esc != null) {
            escalationRampDays = (float) esc.optDouble("rampDays", escalationRampDays);
            escalationCap = (float) esc.optDouble("cap", escalationCap);
        }

        JSONObject growth = f.optJSONObject("growth");
        if (growth != null) {
            JSONArray stagesArr = growth.optJSONArray("stages");
            if (stagesArr != null && stagesArr.length() > 0) {
                growthStages.clear();
                for (int i = 0; i < stagesArr.length(); i++) {
                    JSONObject s = stagesArr.getJSONObject(i);
                    GrowthStage stage = new GrowthStage(
                            (float) s.getDouble("at"),
                            s.optString("build", null),
                            s.optBoolean("raids", false));
                    stage.complete = s.optBoolean("complete", false);
                    growthStages.add(stage);
                }
            }
        }

        JSONObject defense = f.optJSONObject("defense");
        if (defense != null) {
            readDefenseTier(defense, "nexus", defenseNexus);
            readDefenseTier(defense, "ucnexus", defenseUcNexus);
            readDefenseTier(defense, "mining", defenseMining);
            readDefenseTier(defense, "fuel", defenseFuel);
            readDefenseTier(defense, "sensor", defenseSensor);
            readDefenseTier(defense, "comm", defenseComm);
        }

        JSONObject ttDefense = f.optJSONObject("triTachyonDefense");
        if (ttDefense != null) {
            ttDefenseEnabled = ttDefense.optBoolean("enabled", ttDefenseEnabled);
            ttDefenseScanIntervalDays = (float) ttDefense.optDouble(
                    "scanIntervalDays", ttDefenseScanIntervalDays);
            ttDefenseStandDownDays = (float) ttDefense.optDouble(
                    "standDownDays", ttDefenseStandDownDays);
            ttDefenseStationActivationRange = (float) ttDefense.optDouble(
                    "stationActivationRange", ttDefenseStationActivationRange);

            JSONObject reserve = ttDefense.optJSONObject("strategicReserve");
            if (reserve != null) {
                ttDefenseReserveMarketId = reserve.optString(
                        "marketId", ttDefenseReserveMarketId);
                ttDefenseReserveFpMin = reserve.optInt(
                        "fpMin", ttDefenseReserveFpMin);
                ttDefenseReserveFpMax = reserve.optInt(
                        "fpMax", ttDefenseReserveFpMax);
                ttDefenseReserveMinThreatFp = (float) reserve.optDouble(
                        "minThreatFp", ttDefenseReserveMinThreatFp);
                ttDefenseReserveRespawnDays = (float) reserve.optDouble(
                        "respawnDays", ttDefenseReserveRespawnDays);
                ttDefenseReserveActivationLeadDays = (float) reserve.optDouble(
                        "activationLeadDays", ttDefenseReserveActivationLeadDays);
            }

            ttDefenseResponsePrepareDays = (float) ttDefense.optDouble(
                    "responsePrepareDays", ttDefenseResponsePrepareDays);
            ttDefenseResponseLateToleranceDays = (float) ttDefense.optDouble(
                    "responseLateToleranceDays", ttDefenseResponseLateToleranceDays);
            ttDefenseResponseFpMult = (float) ttDefense.optDouble(
                    "responseFpMult", ttDefenseResponseFpMult);
            ttDefenseResponseFpMin = ttDefense.optInt(
                    "responseFpMin", ttDefenseResponseFpMin);
            ttDefenseResponseFpMax = ttDefense.optInt(
                    "responseFpMax", ttDefenseResponseFpMax);
            ttUmbrellaRampDays = (float) ttDefense.optDouble(
                    "umbrellaRampDays", ttUmbrellaRampDays);

            JSONArray protectedArr = ttDefense.optJSONArray("protectedMarkets");
            if (protectedArr != null) {
                ttProtectedMarkets.clear();
                for (int i = 0; i < protectedArr.length(); i++) {
                    JSONObject p = protectedArr.getJSONObject(i);
                    ttProtectedMarkets.add(new ProtectedMarket(
                            p.getString("id"),
                            p.optBoolean("primary", false),
                            p.optInt("localFpMin", 40),
                            p.optInt("localFpMax", 60),
                            (float) p.optDouble("respawnDays", 120f),
                            (float) p.optDouble("responseMinThreatFp", 200f),
                            (float) p.optDouble("spawnAtGrowth", 0d),
                            (float) p.optDouble("spawnAfterDays", 0d)));
                }
            }
        }

        JSONArray th = f.optJSONArray("thresholds");
        if (th != null && th.length() > 0) {
            float[] parsed = new float[th.length()];
            for (int i = 0; i < th.length(); i++) {
                parsed[i] = (float) th.getDouble(i);
            }
            thresholds = parsed;
        }

        JSONArray assetsArr = f.optJSONArray("assets");
        if (assetsArr != null && assetsArr.length() > 0) {
            assets.clear();
            for (int i = 0; i < assetsArr.length(); i++) {
                JSONObject a = assetsArr.getJSONObject(i);
                String key = a.getString("key");
                Asset asset = new Asset(key, a.optString("type", key),
                        a.optString("node", null), a.getString("tag"),
                        a.optBoolean("freezeEscalation", false));
                asset.ucOnly = a.optBoolean("ucOnly", false);
                asset.rebuildDays = (float) a.optDouble("rebuildDays", 0.0);
                JSONObject mods = a.optJSONObject("mods");
                if (mods != null) {
                    Iterator keys = mods.keys();
                    while (keys.hasNext()) {
                        String statId = (String) keys.next();
                        asset.mods.put(statId, Float.valueOf((float) mods.getDouble(statId)));
                    }
                }
                assets.add(asset);
            }
        }
    }

    private static void readDefenseTier(JSONObject defense, String key, DefenseTier tier) throws Exception {
        JSONObject t = defense.optJSONObject(key);
        if (t == null) return;
        tier.thresholdLY = (float) t.optDouble("thresholdLY", tier.thresholdLY);
        tier.maxFleets = t.optInt("maxFleets", tier.maxFleets);
        tier.patrolFleets = t.optInt("patrolFleets", tier.patrolFleets);
        tier.respawnDelayDays = (float) t.optDouble("respawnDelayDays", tier.respawnDelayDays);
        tier.fpMin = t.optInt("fpMin", tier.fpMin);
        tier.fpMax = t.optInt("fpMax", tier.fpMax);
    }
}
