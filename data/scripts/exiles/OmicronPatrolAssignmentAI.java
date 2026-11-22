package data.scripts.exiles;

import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/**
 * Patrol AI for Omicron home-defense roamers. Structure mirrors the vanilla
 * {@code RemnantAssignmentAI} (patrol out → occasionally stand down at the source station →
 * despawn back into the manager's pool), with ONE deliberate difference: the patrol-target
 * picker. Vanilla weights salvageable entities — battle wrecks count, TRIPLED when
 * neutrino-high — plus jump points; playtest 2026-07-06 had every patrol clustering around
 * the jump-point battle wreckage while the buoys went unwatched. This picker patrols what a
 * machine garrison would actually watch: jump points (weight 2) and objective entities —
 * the Sensor/Comm arrays (weight 1 each). Wreckage is ignored entirely.
 */
public class OmicronPatrolAssignmentAI implements EveryFrameScript {

    protected StarSystemAPI homeSystem;
    protected CampaignFleetAPI fleet;
    protected SectorEntityToken source;

    public OmicronPatrolAssignmentAI(CampaignFleetAPI fleet, StarSystemAPI homeSystem,
                                     SectorEntityToken source) {
        this.fleet = fleet;
        this.homeSystem = homeSystem;
        this.source = source;
        giveInitialAssignments();
    }

    protected void giveInitialAssignments() {
        boolean playerInSameLocation =
                fleet.getContainingLocation() == Global.getSector().getCurrentLocation();
        // Launch from the source station if the player is in-system (or sometimes anyway).
        if ((playerInSameLocation || (float) Math.random() < 0.1f) && source != null) {
            fleet.setLocation(source.getLocation().x, source.getLocation().y);
            fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, source,
                    3f + (float) Math.random() * 2f);
        } else {
            SectorEntityToken target = pickPatrolTarget();
            if (target != null) {
                Vector2f loc = Misc.getPointAtRadius(target.getLocation(), target.getRadius() + 100f);
                fleet.setLocation(loc.x, loc.y);
            } else if (source != null) {
                fleet.setLocation(source.getLocation().x, source.getLocation().y);
            }
            pickNext();
        }
    }

    /** Jump points (weight 2) + objective entities like the Sensor/Comm arrays (weight 1). */
    protected SectorEntityToken pickPatrolTarget() {
        WeightedRandomPicker picker = new WeightedRandomPicker();
        List jumpPoints = homeSystem.getJumpPoints();
        for (int i = 0; i < jumpPoints.size(); i++) {
            picker.add(jumpPoints.get(i), 2f);
        }
        List objectives = homeSystem.getEntitiesWithTag(Tags.OBJECTIVE);
        for (int i = 0; i < objectives.size(); i++) {
            picker.add(objectives.get(i), 1f);
        }
        return (SectorEntityToken) picker.pick();
    }

    protected void pickNext() {
        boolean standDown = source != null && (float) Math.random() < 0.2f;
        if (!standDown) {
            SectorEntityToken target = pickPatrolTarget();
            if (target != null) {
                float speed = Misc.getSpeedForBurnLevel(8);
                float dist = Misc.getDistance(fleet.getLocation(), target.getLocation());
                float seconds = dist / speed;
                float days = seconds / Global.getSector().getClock().getSecondsPerDay();
                days += 5f + 5f * (float) Math.random();
                fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, target, days, "patrolling");
                return;
            }
            fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, null,
                    5f + 5f * (float) Math.random(), "patrolling");
        }

        if (source != null) {
            float dist = Misc.getDistance(fleet.getLocation(), source.getLocation());
            if (dist > 1000f) {
                fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, source, 3f,
                        "returning from patrol");
            } else {
                fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source,
                        3f + (float) Math.random() * 2f, "standing down");
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source, 5f);
            }
        }
    }

    public void advance(float amount) {
        if (fleet.getCurrentAssignment() == null) {
            pickNext();
        }
    }

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }
}
