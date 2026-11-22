package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Vanilla travel, with Midan's return point (Pass 7 step 7d-2): a strike force broken on the way
 * goes back to the anchor system's hyperspace jump point, never into a Black Fleet port.
 *
 * <p><b>The in-system approach (sneak attack, 2026-10-30).</b> The leg runs from the staging point
 * to the target station, both inside the target system, so it must require the fleets to be NEAR
 * the target ({@code requireNearTarget} true: within 2000 units). With false -- vanilla's choice
 * for hyperspace legs to a jump point -- any live fleet merely in the same system counts as
 * arrived, which is what the 2026-10-30 test hit: the fleets never left the staging point, the
 * distress call came at "arrival", and the strike resolved off-screen at the window end.
 */
public class ExileHegLeagueMidanTravelStage extends TravelStage {

    protected SectorEntityToken returnPoint;

    public ExileHegLeagueMidanTravelStage(RaidIntel raid, SectorEntityToken from,
                                          SectorEntityToken to, SectorEntityToken returnPoint) {
        super(raid, from, to, true);
        this.returnPoint = returnPoint;
    }

    /**
     * Vanilla sets the window to the abstract travel time (1500 units/day). A live fleet can be
     * slower (nebula, slow-moving terrain), and a window that closes with every fleet still short
     * of the 2000-unit ring FAILS the leg, so live fleets get double the time plus two days.
     */
    @Override
    protected void updateRoutes() {
        super.updateRoutes();
        maxDays = maxDays * 2f + 2f;
    }

    /** Assignment custom marking the strike's own form-up holds. */
    protected static final String FORM_UP = "exiles_midan_form_up";
    /** How often (days) the live group is checked. */
    protected static final float COHESION_DAYS = 0.25f;
    /** A held fleet is released once the group's centre is this fraction of the leash away. */
    protected static final float RELEASE_FRACTION = 0.4f;
    protected float lastCohesionElapsed = -999f;
    protected float lastCohesionLogElapsed = -999f;

    @Override
    protected void updateStatus() {
        if (status == RaidStageStatus.ONGOING && elapsed - lastCohesionElapsed >= COHESION_DAYS) {
            lastCohesionElapsed = elapsed;
            keepTogether();
        }
        super.updateStatus();
    }

    /**
     * THE STRIKE FORCE ARRIVES TOGETHER (7e-2 test, 2026-11-01). Each route travels at its own
     * fleet's speed, so the faster fleets reached Eventide first and waited in orbit for the
     * slower ones. Vanilla's fleet groups avoid this in {@code FGTravelAction}: an FP-weighted
     * centre of mass (slow-moving fleets left out) and a leash of 750 units (+100 per fleet over
     * five, at most +500). This is the same rule cut down to the one case it is needed for -- every
     * fleet live and in the target system: a fleet more than the leash AHEAD of the group's centre
     * (nearer the target than the centre is) HOLDS where it stands until the centre comes within
     * {@link #RELEASE_FRACTION} of the leash, so the group moves at its slowest member's pace.
     * Fleets behind the centre keep their own travel orders; they are already going flat out.
     * Fleets in a battle are left alone. Off-screen (any route abstract) this does nothing.
     */
    protected void keepTogether() {
        if (to == null) return;
        List routes = getRoutes();
        List fleets = new ArrayList(); // CampaignFleetAPI
        for (int i = 0; i < routes.size(); i++) {
            RouteManager.RouteData route = (RouteManager.RouteData) routes.get(i);
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null) {
                if (!route.isExpired()) return; // an abstract route: no live group to hold together
                continue;
            }
            if (!fleet.isAlive()) continue;
            if (fleet.getContainingLocation() != to.getContainingLocation()) return;
            fleets.add(fleet);
        }

        Vector2f com = new Vector2f();
        float weight = 0f;
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            if (Misc.isSlowMoving(fleet)) continue;
            float w = fleet.getFleetPoints();
            com.x += fleet.getLocation().x * w;
            com.y += fleet.getLocation().y * w;
            weight += w;
        }
        boolean grouped = fleets.size() >= 2 && weight >= 1f;
        if (grouped) com.scale(1f / weight);
        float comToTarget = grouped ? Misc.getDistance(com, to.getLocation()) : 0f;
        float leash = 750f + Math.min(Math.max(fleets.size() - 5, 0) * 100f, 500f);

        int held = 0;
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            FleetAssignmentDataAPI curr = fleet.getCurrentAssignment();
            boolean holding = curr != null && FORM_UP.equals(curr.getCustom());
            if (!grouped || fleet.getBattle() != null) {
                if (holding) curr.expire();
                continue;
            }
            float toCom = Misc.getDistance(fleet.getLocation(), com);
            boolean ahead = Misc.getDistance(fleet.getLocation(), to.getLocation()) < comToTarget;
            if (holding) {
                if (!ahead || toCom < leash * RELEASE_FRACTION) {
                    curr.expire();
                } else {
                    held++;
                }
            } else if (ahead && toCom > leash) {
                SectorEntityToken here = fleet.getContainingLocation()
                        .createToken(new Vector2f(fleet.getLocation()));
                fleet.addAssignmentAtStart(FleetAssignment.HOLD, here, 1f, "forming up", null);
                FleetAssignmentDataAPI added = fleet.getCurrentAssignment();
                if (added != null) added.setCustom(FORM_UP);
                held++;
            }
        }
        if (held > 0 && elapsed - lastCohesionLogElapsed >= 1f) {
            lastCohesionLogElapsed = elapsed;
            Global.getLogger(ExileHegLeagueMidanTravelStage.class).info(
                    "Exiles Midan FORM UP: " + held + " of " + fleets.size()
                            + " live fleet(s) holding for the group | centre " + (int) comToTarget
                            + " from " + to.getName() + " | leash " + (int) leash
                            + " | day " + (int) elapsed + " of the approach");
        }
    }

    @Override
    public void giveReturnOrdersToStragglers(List stragglers) {
        ExileHegLeagueMidanStrikeIntel.sendHome(this, stragglers, returnPoint);
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        if (status == RaidStageStatus.FAILURE) {
            info.addPara("The strike force broke off its approach. The attack is over.", 10f);
        } else if (curr == index) {
            String target = intel instanceof ExileHegLeagueMidanStrikeIntel
                    ? ((ExileHegLeagueMidanStrikeIntel) intel).targetName() : "its target";
            info.addPara("The strike force is closing on " + target + ".", 10f);
        }
    }
}
