package data.scripts.exiles;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.NPCHassler;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * Keeps a withdrawing BLOCKADE fleet on its way home (found in Sean's 2026-10-07 test run).
 *
 * <p><b>The defect.</b> Both Exiles blockades (Ashen, Heg–League) put vanilla's {@link NPCHassler}
 * on their pickets. The hassler never ends ({@code isDone()} is false) and keeps working while the
 * fleet is still inside the blockaded system. When a picket on its way out meets a trader, the
 * hassler starts a {@code HassleNPCScript}, which calls {@code fleet.clearAssignments()} -- wiping
 * the return order {@code FleetGroupIntel.giveReturnAssignments} had just given -- and when the
 * inspection ends it removes only its own HOLD. The fleet is left with an EMPTY assignment queue,
 * no longer in the group's fleet list, and idles in the system for good. Save evidence: a
 * Silent Watch "Blockade Picket" in Mayasura with no assignments, {@code $core_fleetReturningToDespawn}
 * set, {@code $fleetDoNotGetSidetracked} gone (the hassle cleanup unsets it), and the hassler still
 * attached. Vanilla's own {@code PerseanLeagueBlockade} has the same latent gap.
 *
 * <p><b>The fix, in two parts.</b> At the return order the blockade removes the hassler (as vanilla's
 * {@code KnightsOfLuddTakeoverExpedition} does at its end), so no NEW inspection can start. An
 * inspection already under way still clears the order, so this watchdog rides on the fleet and
 * re-issues the same vanilla return order whenever the queue is found empty outside a battle or a
 * running special action. It ends with the fleet (despawn on arrival). Bounded: after
 * {@link #MAX_REISSUES} re-issues the fleet is faded out rather than left to idle.
 *
 * <p>Named class, never anonymous: it is saved on the fleet (CLAUDE.md sec 7).
 */
public class ExileReturnOrderWatchdog implements EveryFrameScript {

    public static final int MAX_REISSUES = 5;

    protected CampaignFleetAPI fleet;
    /** The group's return location, or null for "return to source" (vanilla's two branches). */
    protected SectorEntityToken returnLocation;
    protected String label;
    protected int reissues = 0;
    protected boolean done = false;
    protected IntervalUtil interval = new IntervalUtil(0.4f, 0.6f);

    /** Call from a blockade's {@code giveReturnAssignments} override, after {@code super}. */
    public static void attach(CampaignFleetAPI fleet, SectorEntityToken returnLocation, String label) {
        if (fleet == null || !fleet.isAlive()) return;
        fleet.removeScriptsOfClass(NPCHassler.class);
        fleet.removeScriptsOfClass(ExileReturnOrderWatchdog.class); // one per fleet
        fleet.addScript(new ExileReturnOrderWatchdog(fleet, returnLocation, label));
    }

    public ExileReturnOrderWatchdog(CampaignFleetAPI fleet, SectorEntityToken returnLocation,
                                    String label) {
        this.fleet = fleet;
        this.returnLocation = returnLocation;
        this.label = label;
    }

    public void advance(float amount) {
        if (done) return;
        if (fleet == null || !fleet.isAlive()) {
            done = true;
            return;
        }
        interval.advance(Misc.getDays(amount));
        if (!interval.intervalElapsed()) return;

        if (fleet.getBattle() != null) return;
        // A running inspection holds the fleet and would wipe a re-issued order on its next tick;
        // wait for it to finish (it lasts 2-3 days).
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_SPECIAL_ACTION)) return;
        if (fleet.getCurrentAssignment() != null) return;

        if (reissues >= MAX_REISSUES) {
            Global.getLogger(ExileReturnOrderWatchdog.class).info(
                    "Exiles RETURN ORDER WATCHDOG: " + label + " fleet " + fleet.getName()
                            + " still idle after " + reissues + " re-issues in "
                            + fleet.getContainingLocation().getName() + " -- fading it out");
            done = true;
            Misc.fadeAndExpire(fleet);
            return;
        }
        reissues++;
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        if (returnLocation != null) {
            Misc.giveStandardReturnAssignments(fleet, returnLocation, null, true);
        } else {
            Misc.giveStandardReturnToSourceAssignments(fleet, true);
        }
        Global.getLogger(ExileReturnOrderWatchdog.class).info(
                "Exiles RETURN ORDER WATCHDOG: " + label + " fleet " + fleet.getName()
                        + " found idle in " + fleet.getContainingLocation().getName()
                        + " -- return order re-issued (" + reissues + "/" + MAX_REISSUES + ")");
    }

    public boolean isDone() {
        return done;
    }

    public boolean runWhilePaused() {
        return false;
    }
}
