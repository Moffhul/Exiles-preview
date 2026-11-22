package data.scripts.exiles;

import java.util.List;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/** Physical collection only. Persisted cargo manifest distinguishes salvage from starting stores. */
public class ExileRefugeeSalvageAssignmentAI implements EveryFrameScript {
    protected CampaignFleetAPI fleet;
    protected MarketAPI home;
    protected CargoAPI collected;
    protected SectorEntityToken target;
    protected float age;
    protected float targetAge;
    protected float startingStrength;
    protected boolean returning;
    protected boolean delivered;
    protected IntervalUtil check;
    protected transient ExileRefugeeSalvageConfig config;

    public ExileRefugeeSalvageAssignmentAI(CampaignFleetAPI fleet, MarketAPI home, ExileRefugeeSalvageConfig config) {
        this.fleet = fleet; this.home = home; this.config = config;
        collected = Global.getFactory().createCargo(false);
        startingStrength = fleet.getEffectiveStrength();
        check = new IntervalUtil(config.dangerCheckMinDays, config.dangerCheckMaxDays);
    }
    protected ExileRefugeeSalvageConfig cfg() {
        if (config == null) config = ExileRefugeeSalvageConfig.load();
        return config;
    }
    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);
        age += days; targetAge += days;
        check.advance(days);
        if (!check.intervalElapsed() || !fleet.isAlive() || fleet.getBattle() != null) return;
        ExileRefugeeSalvageConfig tuning = cfg();
        if (returning) {
            if (reachedHome() || !visibleToPlayer()) retire();
            return;
        }
        if (age >= tuning.maxCollectionDays || full() || fleet.getEffectiveStrength() < startingStrength * 0.5f
                || danger()) { returnHome(); return; }
        if (target != null && (!ExileRefugeeSalvageRecovery.eligible(target)
                || !ExileRefugeeSalvageThreats.isTargetSafe(target, tuning) || targetAge > 30)) {
            target = null; fleet.clearAssignments();
        }
        if (target != null) return;
        target = ExileRefugeeSalvageRecovery.findTarget(fleet.getContainingLocation(), fleet, tuning);
        if (target == null) { returnHome(); return; }
        targetAge = 0;
        SectorEntityToken selected = target;
        if (tuning.diagnostics) ExileSalvageDiagnostics.event("TARGET_SELECTED", selected, fleet);
        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, selected, 30, "moving to salvage " + selected.getName());
        fleet.addAssignment(FleetAssignment.HOLD, selected, tuning.salvageDays, "conducting salvage operations",
                new CollectOnHoldEnd(this, selected));
    }

    /**
     * The HOLD assignment's completion callback. A NAMED static class (bug sweep REF-5,
     * 2026-10-06): it is saved inside the fleet's assignment queue, and an anonymous class's
     * synthetic name ($1) shifts with source order or between Janino and javac, which would
     * break loading a save taken mid-salvage (CLAUDE.md sec 7). Saves taken mid-salvage on
     * builds before this change will not load -- a new game is required.
     */
    public static class CollectOnHoldEnd implements Script {
        protected ExileRefugeeSalvageAssignmentAI ai;
        protected SectorEntityToken selected;
        public CollectOnHoldEnd(ExileRefugeeSalvageAssignmentAI ai, SectorEntityToken selected) {
            this.ai = ai; this.selected = selected;
        }
        public void run() {
            if (ai != null) ai.collectAt(selected);
        }
    }

    protected void collectAt(SectorEntityToken selected) {
        // A competing fleet, the player, or an expiring field may invalidate queued work.
        if (!returning && fleet.isAlive() && fleet.getBattle() == null && !danger()
                && selected == target && ExileRefugeeSalvageThreats.isTargetSafe(selected, cfg())
                && selected.getContainingLocation() == fleet.getContainingLocation()
                && Misc.getDistance(fleet, selected) <= fleet.getRadius() + selected.getRadius() + 200) {
            try {
                if (cfg().diagnostics) ExileSalvageDiagnostics.event("BEFORE_COLLECT", selected, fleet);
                ExileRefugeeSalvageRecovery.collect(selected, fleet, collected);
                if (cfg().diagnostics) ExileSalvageDiagnostics.event("AFTER_COLLECT", selected, fleet);
            }
            catch (Exception ex) {
                // Leave unconsumed malformed/modded targets intact, and avoid retrying each tick.
                selected.getMemoryWithoutUpdate().set(ExileRefugeeSalvageRecovery.FAILED, true, 5);
                Global.getLogger(ExileRefugeeSalvageAssignmentAI.class).warn("Refugee salvage failed for " + selected.getId(), ex);
            }
        }
        target = null;
    }
    protected boolean full() {
        CargoAPI cargo = fleet.getCargo();
        return cargo.getMaxCapacity() <= 0 || cargo.getSpaceUsed() >= cargo.getMaxCapacity() * cfg().returnCargoFraction;
    }
    protected boolean danger() {
        return ExileRefugeeSalvageThreats.findVisibleThreat(fleet, cfg()) != null;
    }
    protected boolean visibleToPlayer() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return true;
        if (player.getContainingLocation() != fleet.getContainingLocation()) {
            return Misc.getDistance(player.getLocationInHyperspace(), fleet.getLocationInHyperspace()) < 1500;
        }
        return fleet.getVisibilityLevelTo(player) != SectorEntityToken.VisibilityLevel.NONE
                || Misc.getDistance(player, fleet) < player.getMaxSensorRangeToDetect(fleet) + 500;
    }
    protected boolean reachedHome() {
        if (!ExileRefugeeSalvageExpeditionManager.eligibleRecipient(home)) return false;
        SectorEntityToken destination = home.getPrimaryEntity();
        return destination != null && destination.isAlive()
                && destination.getContainingLocation() == fleet.getContainingLocation()
                && Misc.getDistance(fleet, destination) <= fleet.getRadius() + destination.getRadius() + 200;
    }
    protected void returnHome() {
        returning = true; target = null;
        fleet.clearAssignments();
        List<MarketAPI> homes = ExileRefugeeSalvageExpeditionManager.getRecipients();
        if (!ExileRefugeeSalvageExpeditionManager.eligibleRecipient(home)) home = homes.isEmpty() ? null : (MarketAPI) homes.get(0);
        SectorEntityToken destination = home == null ? null : home.getPrimaryEntity();
        if (destination == null && fleet.getStarSystem() != null && !fleet.getStarSystem().getJumpPoints().isEmpty()) {
            destination = (SectorEntityToken) fleet.getStarSystem().getJumpPoints().get(0);
        }
        if (destination == null) destination = fleet.getContainingLocation().createToken(0, 0);
        fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, destination, 1000,
                home == null ? "withdrawing from salvage operations" : "returning to " + home.getName());
        // Deliberately no vanilla despawn assignment: retirement owns the once-only cargo handoff.
    }
    protected void retire() {
        if (delivered) return;
        List<MarketAPI> homes = ExileRefugeeSalvageExpeditionManager.getRecipients();
        if (!ExileRefugeeSalvageExpeditionManager.eligibleRecipient(home)) home = homes.isEmpty() ? null : (MarketAPI) homes.get(0);
        delivered = true;
        try {
            if (home != null) ExileRefugeeSalvageRecovery.deliver(home, fleet, collected, cfg());
        } catch (Exception ex) {
            // Never retry a partially completed handoff or strand a finished fleet after a market error.
            Global.getLogger(ExileRefugeeSalvageAssignmentAI.class).warn("Refugee salvage delivery failed", ex);
        } finally {
            fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, home == null ? null : home.getPrimaryEntity());
        }
    }
    public boolean isDone() { return !fleet.isAlive(); }
    public boolean runWhilePaused() { return false; }
}
