package data.scripts.exiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;

/**
 * Per-node war-capacity state for Omicron. THE CONTRACT for stat ids, modifier ids and
 * persistent keys is docs/war-capacity-design.md — register new ids there before inventing
 * them. Stored in sector persistent data under {@link #KEY} so quests can attach modifiers
 * through the static API without a Director handle (same pattern as
 * {@code OmicronDirector.addTargetFaction}).
 *
 * <p>A "node" is one Omicron star system (config: {@code OmicronDirectorConfig.nodes}).
 * Each node carries one {@link MutableStat} per axis, base 1.0, read clamped to
 * [{@link #MIN_VALUE}, {@link #MAX_VALUE}]. Facility destruction holds a permanent
 * multiplicative modifier in place while the facility is dead; temporary effects register a
 * timed entry the Director prunes on its interval — pruning IS the regeneration toward the
 * asset-defined cap.
 */
public class OmicronWarCapacity {

    /** Sector persistent-data key holding the singleton state object. */
    public static final String KEY = "$exiles_omicron_war_capacity";

    // --- Stat ids (war-capacity-design.md §3). FINAL — never rename once saves exist. ---
    public static final String STAT_STRENGTH = "exiles_wc_strength";
    public static final String STAT_CADENCE = "exiles_wc_cadence";
    public static final String STAT_REACH = "exiles_wc_reach";
    public static final String STAT_ESCALATION = "exiles_wc_escalation";
    /** Reserved for Phase-2 quest modifiers; no base-game feeder. */
    public static final String STAT_TARGETING = "exiles_wc_targeting";
    /** Reserved for Phase-2 quest modifiers; no base-game feeder. */
    public static final String STAT_PRECISION = "exiles_wc_precision";

    /** Modifier-id prefix for facility-destruction modifiers ("exiles_wc_asset_" + assetKey). */
    public static final String ASSET_MOD_PREFIX = "exiles_wc_asset_";

    public static final float MIN_VALUE = 0.05f;
    public static final float MAX_VALUE = 2.0f;

    // --- Persisted state. XStream skips field initializers; readResolve() re-inits nulls. ---
    private HashMap nodeStats;         // nodeId -> HashMap(statId -> MutableStat)
    private ArrayList timedMods;       // TimedMod
    private HashMap frozenEscalation;  // nodeId -> Float (legacy ramp freeze; unused w/ UC node)
    private HashMap knownDestroyed;    // nodeId + "|" + assetKey -> Boolean
    private int lastBand;              // last legibility band (0 = above all thresholds)
    // --- Spread build (2026-07-06) ---
    private float growthProgress;      // UC-system maturation, 0..1; accrues while UC-Nexus lives
    private long growthLastTs;         // last accrual timestamp (0 = not started)
    private HashMap rebuildTs;         // nodeId + "|" + assetKey -> Long (death-detection ts)
    private HashMap builtFlags;        // nodeId + "|" + assetKey -> Boolean (station exists/existed)
    private boolean latticeCompleted;  // damaged UC-Nexus has been atomically promoted

    public OmicronWarCapacity() {
        nodeStats = new HashMap();
        timedMods = new ArrayList();
        frozenEscalation = new HashMap();
        knownDestroyed = new HashMap();
        lastBand = 0;
        growthProgress = 0f;
        growthLastTs = 0L;
        rebuildTs = new HashMap();
        builtFlags = new HashMap();
        latticeCompleted = false;
    }

    protected Object readResolve() {
        if (nodeStats == null) nodeStats = new HashMap();
        if (timedMods == null) timedMods = new ArrayList();
        if (frozenEscalation == null) frozenEscalation = new HashMap();
        if (knownDestroyed == null) knownDestroyed = new HashMap();
        if (rebuildTs == null) rebuildTs = new HashMap();
        if (builtFlags == null) builtFlags = new HashMap();
        return this;
    }

    /** The singleton, created in persistent data on first use. */
    public static OmicronWarCapacity get() {
        Object v = Global.getSector().getPersistentData().get(KEY);
        if (v instanceof OmicronWarCapacity) return (OmicronWarCapacity) v;
        OmicronWarCapacity wc = new OmicronWarCapacity();
        Global.getSector().getPersistentData().put(KEY, wc);
        return wc;
    }

    // --- Quest-facing API (war-capacity-design.md §6) ---

    /**
     * Attach (or replace) a multiplicative modifier on one node's axis.
     * @param durationDays &lt;= 0 =&gt; permanent until {@link #unmodify} is called.
     */
    public static void modifyMult(String nodeId, String statId, String modifierId,
                                  float mult, float durationDays) {
        OmicronWarCapacity wc = get();
        MutableStat stat = wc.stat(nodeId, statId);
        stat.modifyMult(modifierId, mult);
        wc.removeTimed(nodeId, statId, modifierId);
        if (durationDays > 0f) {
            wc.timedMods.add(new TimedMod(nodeId, statId, modifierId,
                    Global.getSector().getClock().getTimestamp(), durationDays));
        }
    }

    /** Remove a modifier (and any timed entry for it) from one node's axis. */
    public static void unmodify(String nodeId, String statId, String modifierId) {
        OmicronWarCapacity wc = get();
        wc.stat(nodeId, statId).unmodify(modifierId);
        wc.removeTimed(nodeId, statId, modifierId);
    }

    /** Clamped effective value of one node's axis (1.0 when untouched). */
    public static float getValue(String nodeId, String statId) {
        float v = get().stat(nodeId, statId).getModifiedValue();
        if (v < MIN_VALUE) return MIN_VALUE;
        if (v > MAX_VALUE) return MAX_VALUE;
        return v;
    }

    /** Faction-wide convenience: apply the modifier to every node this object knows about. */
    public static void modifyAllNodes(String statId, String modifierId,
                                      float mult, float durationDays) {
        OmicronWarCapacity wc = get();
        Iterator it = wc.nodeStats.keySet().iterator();
        while (it.hasNext()) {
            String nodeId = (String) it.next();
            modifyMult(nodeId, statId, modifierId, mult, durationDays);
        }
    }

    // --- Director-facing state ---

    /** Get-or-create the MutableStat for a node axis (base 1.0). Also registers the node. */
    public MutableStat stat(String nodeId, String statId) {
        HashMap stats = (HashMap) nodeStats.get(nodeId);
        if (stats == null) {
            stats = new HashMap();
            nodeStats.put(nodeId, stats);
        }
        MutableStat stat = (MutableStat) stats.get(statId);
        if (stat == null) {
            stat = new MutableStat(1f);
            stats.put(statId, stat);
        }
        return stat;
    }

    /** Drop expired timed modifiers; called by the Director on its interval. This is the regen. */
    public void pruneExpired() {
        if (timedMods.isEmpty()) return;
        Iterator it = timedMods.iterator();
        while (it.hasNext()) {
            TimedMod tm = (TimedMod) it.next();
            float elapsed = Global.getSector().getClock().getElapsedDaysSince(tm.startTs);
            if (elapsed >= tm.durationDays) {
                stat(tm.nodeId, tm.statId).unmodify(tm.modifierId);
                it.remove();
            }
        }
    }

    private void removeTimed(String nodeId, String statId, String modifierId) {
        Iterator it = timedMods.iterator();
        while (it.hasNext()) {
            TimedMod tm = (TimedMod) it.next();
            if (tm.nodeId.equals(nodeId) && tm.statId.equals(statId)
                    && tm.modifierId.equals(modifierId)) {
                it.remove();
            }
        }
    }

    public boolean isKnownDestroyed(String nodeId, String assetKey) {
        Object v = knownDestroyed.get(nodeId + "|" + assetKey);
        return (v instanceof Boolean) && ((Boolean) v).booleanValue();
    }

    public void setKnownDestroyed(String nodeId, String assetKey, boolean destroyed) {
        knownDestroyed.put(nodeId + "|" + assetKey, Boolean.valueOf(destroyed));
    }

    /** Ramp value captured when the node's UC-Nexus died, or null if not frozen. */
    public Float getFrozenEscalation(String nodeId) {
        Object v = frozenEscalation.get(nodeId);
        if (v instanceof Float) return (Float) v;
        return null;
    }

    public void setFrozenEscalation(String nodeId, float value) {
        frozenEscalation.put(nodeId, Float.valueOf(value));
    }

    public void clearFrozenEscalation(String nodeId) {
        frozenEscalation.remove(nodeId);
    }

    public int getLastBand() {
        return lastBand;
    }

    public void setLastBand(int band) {
        lastBand = band;
    }

    // --- Spread build: UC growth + station-rebuild bookkeeping (Director-driven) ---

    /** UC-system maturation progress, 0..1. */
    public float getGrowthProgress() {
        return growthProgress;
    }

    public void setGrowthProgress(float progress) {
        if (progress < 0f) progress = 0f;
        if (progress > 1f) progress = 1f;
        growthProgress = progress;
    }

    /** Last growth-accrual timestamp (0 = accrual not started yet). */
    public long getGrowthLastTs() {
        return growthLastTs;
    }

    public void setGrowthLastTs(long ts) {
        growthLastTs = ts;
    }

    /** Death-detection timestamp for a pending station rebuild, or null if none pending. */
    public Long getRebuildTs(String nodeId, String assetKey) {
        Object v = rebuildTs.get(nodeId + "|" + assetKey);
        if (v instanceof Long) return (Long) v;
        return null;
    }

    public void setRebuildTs(String nodeId, String assetKey, long ts) {
        rebuildTs.put(nodeId + "|" + assetKey, Long.valueOf(ts));
    }

    public void clearRebuildTs(String nodeId, String assetKey) {
        rebuildTs.remove(nodeId + "|" + assetKey);
    }

    /**
     * Whether a node's station has been built (generation or a growth stage). Assets are only
     * scanned/debuffed once built — a UC system's future stations must not read as "destroyed".
     * @param dflt default when unrecorded (true for active nodes' gen-time stations).
     */
    public boolean isBuilt(String nodeId, String assetKey, boolean dflt) {
        Object v = builtFlags.get(nodeId + "|" + assetKey);
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        return dflt;
    }

    public void setBuilt(String nodeId, String assetKey, boolean built) {
        builtFlags.put(nodeId + "|" + assetKey, Boolean.valueOf(built));
    }

    public boolean isLatticeCompleted() {
        return latticeCompleted;
    }

    public void setLatticeCompleted(boolean completed) {
        latticeCompleted = completed;
    }

    /** One timed modifier entry. Named static class — XStream-safe (no anonymous classes). */
    private static class TimedMod {
        String nodeId;
        String statId;
        String modifierId;
        long startTs;
        float durationDays;

        TimedMod(String nodeId, String statId, String modifierId, long startTs, float durationDays) {
            this.nodeId = nodeId;
            this.statId = statId;
            this.modifierId = modifierId;
            this.startTs = startTs;
            this.durationDays = durationDays;
        }
    }
}
