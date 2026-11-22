package data.scripts.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import data.scripts.world.systems.ExileAshenCrusade;
import data.scripts.world.systems.ExileBlackFleetLundy;
import data.scripts.world.systems.ExileBlackFleetSallee;
import data.scripts.world.systems.ExileMidan;
import data.scripts.world.systems.ExileMidanOrigin;
import data.scripts.world.systems.ExileOmicron0;
import data.scripts.world.systems.ExileOmicronCipher;
import data.scripts.world.systems.ExileOmicronLattice;
import data.scripts.world.systems.ExileOmicronVigil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Deferred custom-system placement (built 2026-07-20).
 *
 * Runs from ExilesModPlugin.onNewGameAfterProcGen(), AFTER vanilla SectorProcGen has placed its
 * constellations. Because these systems do not exist yet during that pass, they never trigger
 * SectorProcGen's per-system keep-out (blotOut, ~8000u) — which is what carved the isolated
 * "spotlight" gap around every custom system when they were generated in onNewGame().
 *
 * For each cluster in data/config/exiles/system_placement.json:
 *   1. jitter the cluster anchor within jitterRadius of its nominalCenter (per-game variety),
 *   2. run a proximity detector that avoids overlapping any existing system and prefers an anchor
 *      with a procgen neighbor within range (pulls the cluster into the field, killing the gap),
 *   3. build each member at anchor + its fixed offset (preserving the cluster's internal shape),
 *   4. optionally join the nearest constellation / add THEME_HIDDEN, per member flags.
 *
 * Runs before economy load, so market-bearing custom systems still bind their markets normally.
 * If the config can't be read, every system falls back to its original hardcoded coordinate with
 * no jitter, so worldgen never breaks.
 *
 * Conservative Java on purpose (Janino compiles this at game start): no lambdas/streams/diamond,
 * explicit casts on every List.get(). Do not "modernize."
 */
public class ExileSystemPlacement {

    public static final String PATH = "data/config/exiles/system_placement.json";

    // Detector defaults; overridden by the JSON "detector" block if present.
    private float minSeparation = 3000f;
    private float preferNeighborWithin = 10000f;
    private int maxAttempts = 48;

    private final Random random = new Random();

    public void generateDeferredSystems(SectorAPI sector) {
        JSONObject root = null;
        try {
            root = Global.getSettings().loadJSON(PATH);
        } catch (Exception e) {
            Global.getLogger(ExileSystemPlacement.class).error(
                    "Exiles: could not load " + PATH + " -- placing deferred systems at fallback coords, no jitter.", e);
        }

        if (root != null) {
            JSONObject det = root.optJSONObject("detector");
            if (det != null) {
                minSeparation = (float) det.optDouble("minSeparationFromAnySystem", minSeparation);
                preferNeighborWithin = (float) det.optDouble("preferNeighborWithin", preferNeighborWithin);
                maxAttempts = det.optInt("maxPlacementAttempts", maxAttempts);
            }
        }

        JSONObject clusters = (root == null) ? null : root.optJSONObject("clusters");
        if (clusters == null) {
            placeAllFallback(sector);
            return;
        }

        Iterator it = clusters.keys();
        while (it.hasNext()) {
            String clusterName = (String) it.next();
            JSONObject cluster = clusters.optJSONObject(clusterName);
            if (cluster == null) continue;
            try {
                placeCluster(sector, clusterName, cluster);
            } catch (Exception e) {
                Global.getLogger(ExileSystemPlacement.class).error(
                        "Exiles: failed to place cluster '" + clusterName + "'.", e);
            }
        }
    }

    private void placeCluster(SectorAPI sector, String clusterName, JSONObject cluster) {
        float[] center = readXY(cluster.optJSONArray("nominalCenter"), 0f, 0f);
        float jitter = (float) cluster.optDouble("jitterRadius", 0f);

        JSONObject members = cluster.optJSONObject("members");
        if (members == null) return;

        // Collect member keys + offsets (order irrelevant).
        List keys = new ArrayList();
        List offsets = new ArrayList();
        Iterator mit = members.keys();
        while (mit.hasNext()) {
            String mkey = (String) mit.next();
            JSONObject m = members.optJSONObject(mkey);
            if (m == null) continue;
            keys.add(mkey);
            offsets.add(readXY(m.optJSONArray("offset"), 0f, 0f));
        }
        if (keys.isEmpty()) return;

        float[] anchor = pickAnchor(sector, center[0], center[1], jitter, offsets);

        for (int i = 0; i < keys.size(); i++) {
            String mkey = (String) keys.get(i);
            float[] off = (float[]) offsets.get(i);
            float mx = anchor[0] + off[0];
            float my = anchor[1] + off[1];

            StarSystemAPI sys = dispatchGenerate(mkey, sector, mx, my);
            if (sys == null) continue;

            JSONObject m = members.optJSONObject(mkey);
            boolean join = (m != null) && m.optBoolean("joinConstellation", false);
            boolean hidden = (m != null) && m.optBoolean("themeHidden", false);
            if (join) joinNearestConstellation(sys);
            if (hidden) {
                sys.addTag(Tags.THEME_HIDDEN);
                sys.addTag(Tags.THEME_SPECIAL);
            }
        }
    }

    /**
     * Detector: try the nominal center first, then jittered candidates. Score each by evaluating
     * the WHOLE cluster footprint (anchor + every member offset). Strongly avoid overlap; prefer a
     * procgen neighbor within range. Returns as soon as an ideal anchor is found, else the best.
     */
    private float[] pickAnchor(SectorAPI sector, float cx, float cy, float jitter, List offsets) {
        List systems = sector.getStarSystems();

        float[] best = new float[]{cx, cy};
        int bestScore = Integer.MIN_VALUE;
        int attempts = Math.max(1, maxAttempts);

        for (int a = 0; a < attempts; a++) {
            float ax;
            float ay;
            if (a == 0 || jitter <= 0f) {
                ax = cx;
                ay = cy;
            } else {
                ax = cx + (random.nextFloat() * 2f - 1f) * jitter;
                ay = cy + (random.nextFloat() * 2f - 1f) * jitter;
            }

            boolean overlap = false;
            boolean hasNeighbor = false;
            float minSep2 = minSeparation * minSeparation;
            float neigh2 = preferNeighborWithin * preferNeighborWithin;

            for (int i = 0; i < offsets.size(); i++) {
                float[] off = (float[]) offsets.get(i);
                float mx = ax + off[0];
                float my = ay + off[1];
                for (int j = 0; j < systems.size(); j++) {
                    StarSystemAPI s = (StarSystemAPI) systems.get(j);
                    float dx = mx - s.getLocation().x;
                    float dy = my - s.getLocation().y;
                    float d2 = dx * dx + dy * dy;
                    if (d2 < minSep2) overlap = true;
                    if (d2 <= neigh2) hasNeighbor = true;
                }
            }

            if (!overlap && hasNeighbor) return new float[]{ax, ay};

            int score = (overlap ? -1000 : 0) + (hasNeighbor ? 10 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = new float[]{ax, ay};
            }
            if (jitter <= 0f) break; // re-rolling with no jitter just repeats the nominal center
        }
        return best;
    }

    private StarSystemAPI dispatchGenerate(String key, SectorAPI sector, float x, float y) {
        if (key.equals("omicron")) return new ExileOmicron0().generate(sector, x, y);
        if (key.equals("cipher")) return new ExileOmicronCipher().generate(sector, x, y);
        if (key.equals("vigil")) return new ExileOmicronVigil().generate(sector, x, y);
        if (key.equals("lattice")) return new ExileOmicronLattice().generate(sector, x, y);
        if (key.equals("sallee")) return new ExileBlackFleetSallee().generate(sector, x, y);
        if (key.equals("lundy")) return new ExileBlackFleetLundy().generate(sector, x, y);
        if (key.equals("meridian")) return new ExileMidanOrigin().generate(sector, x, y);
        if (key.equals("midan")) return new ExileMidan().generate(sector, x, y);
        if (key.equals("providence")) return new ExileAshenCrusade().generate(sector, x, y);
        Global.getLogger(ExileSystemPlacement.class).warn(
                "Exiles: unknown placement member key '" + key + "' -- skipped.");
        return null;
    }

    private void joinNearestConstellation(StarSystemAPI sys) {
        List systems = Global.getSector().getStarSystems();
        Constellation nearest = null;
        float bestD2 = Float.MAX_VALUE;
        for (int j = 0; j < systems.size(); j++) {
            StarSystemAPI s = (StarSystemAPI) systems.get(j);
            if (s == sys) continue;
            Constellation c = s.getConstellation();
            if (c == null) continue;
            float dx = sys.getLocation().x - s.getLocation().x;
            float dy = sys.getLocation().y - s.getLocation().y;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                nearest = c;
            }
        }
        if (nearest == null) return;

        Constellation old = sys.getConstellation();
        if (old != null && old != nearest) old.getSystems().remove(sys);
        if (!nearest.getSystems().contains(sys)) nearest.getSystems().add(sys);
        sys.setConstellation(nearest);
    }

    /** Config unreadable: build every deferred system at its original hardcoded coordinate. */
    private void placeAllFallback(SectorAPI sector) {
        dispatchGenerate("omicron", sector, -44500f, -2500f);
        dispatchGenerate("cipher", sector, -42500f, -12700f);
        dispatchGenerate("vigil", sector, -40600f, 8300f);
        dispatchGenerate("lattice", sector, -36500f, -7000f);
        dispatchGenerate("sallee", sector, 24500f, -10500f);
        dispatchGenerate("lundy", sector, 28500f, -2500f);
        dispatchGenerate("meridian", sector, 36000f, 1500f);
        dispatchGenerate("midan", sector, 44000f, 8500f);
        dispatchGenerate("providence", sector, -8000f, 26000f);
    }

    private float[] readXY(JSONArray arr, float dx, float dy) {
        if (arr == null) return new float[]{dx, dy};
        try {
            return new float[]{(float) arr.getDouble(0), (float) arr.getDouble(1)};
        } catch (Exception e) {
            return new float[]{dx, dy};
        }
    }
}
