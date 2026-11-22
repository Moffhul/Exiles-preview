package data.scripts.exiles;

import com.fs.starfarer.api.impl.campaign.fleets.DisposableFleetManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;

/** Temporary passive diagnostics. Never call Nex demand/update/advance: those mutate its cache.
 * Half-day samples of the player's system are observations, NOT records of actual Nex checks.
 * State lives in the manager's transient helper, not campaign memory or saved entities.
 */
public class ExileSalvageDiagnostics {
    protected float remaining;
    protected boolean announced;
    protected String previous;

    public void advance(float days) {
        remaining -= days;
        if (remaining > 0) return;
        remaining = 0.5f;
        try {
            if (!announced) {
                log("ENABLED half-day player-system observations; events sector-wide; no forced Nex checks");
                announced = true;
            }
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null || !(player.getContainingLocation() instanceof StarSystemAPI)) {
                previous = null;
                return;
            }
            LocationAPI location = player.getContainingLocation();
            String state = snapshot(location);
            // Cache expiry is deliberately included: repeated samples show the cache window.
            if (!state.equals(previous)) { log("SAMPLE " + state); previous = state; }
        } catch (Exception ex) {
            log("SAMPLE_UNAVAILABLE " + ex.getClass().getSimpleName());
            remaining = 10f;
        }
    }

    public static void event(String kind, SectorEntityToken target, CampaignFleetAPI fleet) {
        try {
            LocationAPI location = fleet.getContainingLocation();
            log(kind + " fleet=" + fleet.getId() + " target=" + target.getId()
                    + " refugeeEligible=" + ExileRefugeeSalvageRecovery.eligible(target)
                    + " " + snapshot(location));
        } catch (Exception ex) {
            // Diagnostic failure must never cancel a collection or fleet assignment.
            log(kind + " DIAGNOSTIC_UNAVAILABLE " + ex.getClass().getSimpleName());
        }
    }

    public static String snapshot(LocationAPI location) throws Exception {
        if (location == null) return "system=none";
        StringBuilder result = new StringBuilder("system=" + location.getName());
        result.append(" fields=[");
        for (CampaignTerrainAPI terrain : location.getTerrainCopy()) {
            if (!(terrain.getPlugin() instanceof DebrisFieldTerrainPlugin)) continue;
            DebrisFieldTerrainPlugin debris = (DebrisFieldTerrainPlugin) terrain.getPlugin();
            if (debris.getParams().source != DebrisFieldTerrainPlugin.DebrisFieldSource.BATTLE) continue;
            result.append(terrain.getId()).append(":scavenged=").append(debris.isScavenged())
                    .append(",fading=").append(debris.isFadingOut())
                    .append(",baseXP=").append(debris.getParams().baseSalvageXP)
                    .append(",density=").append(debris.getParams().density).append(";");
        }
        result.append("] refugeeTargets=[");
        for (SectorEntityToken entity : location.getEntitiesWithTag(Tags.SALVAGEABLE)) {
            if (ExileRefugeeSalvageRecovery.eligible(entity)) result.append(entity.getId()).append(";");
        }
        result.append("]");
        // Use vanilla base-class APIs only: loose scripts cannot use reflection.
        if (!Global.getSettings().getModManager().isModEnabled("nexerelin")) return result + " nex=absent";
        Object stored = Global.getSector().getPersistentData().get("nex_vultureFleetManager");
        DisposableFleetManager manager = stored instanceof DisposableFleetManager ? (DisposableFleetManager)stored : null;
        LocationAPI selected = manager == null ? null : manager.getCurrSpawnLoc();
        result.append(" nexSelected=").append(selected == null ? "none" : selected.getName())
                .append(" rate=").append(manager == null ? "none" : String.valueOf(manager.getSpawnRateMult()));
        String key = "$nex_vultureFleetCountCache";
        result.append(" cachedDemand=").append(location.getMemoryWithoutUpdate().contains(key)
                ? String.valueOf(location.getMemoryWithoutUpdate().getFloat(key)) : "absent")
                .append(" cacheDays=").append(location.getMemoryWithoutUpdate().getExpire(key));
        result.append(" raids=[");
        for (IntelInfoPlugin info : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
            RaidIntel raid = (RaidIntel)info;
            if (raid.getSystem() != location || raid.isEnding() || raid.isEnded()) continue;
            result.append(raid.getClass().getSimpleName()).append(":faction=").append(raid.getFaction().getId())
                    .append(",action=").append(raid.getStageIndex(raid.getActionStage()) == raid.getCurrentStage())
                    .append(",origFleets=").append(raid.getOrigNumFleets())
                    .append(",hostileToIndies=").append(raid.getFaction().isHostileTo(Factions.INDEPENDENT)).append(";");
        }
        return result.append("]").toString();
    }
    protected static void log(String message) {
        Global.getLogger(ExileSalvageDiagnostics.class).info("SALVAGE_DIAG timestamp="
                + Global.getSector().getClock().getTimestamp() + " date="
                + Global.getSector().getClock().getDateString() + " " + message);
    }
}
