package data.scripts.exiles;

import java.util.List;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/** One Nexus-launched reinforcement: short preparation, travel, defend, return. */
public class OmicronTriTachyonResponseScript implements EveryFrameScript {

    protected static final int PREPARING = 0;
    protected static final int TRAVELLING = 1;
    protected static final int DEFENDING = 2;
    protected static final int RETURNING = 3;

    protected CampaignFleetAPI fleet;
    protected SectorEntityToken source;
    protected String nodeId;
    protected String targetMarketId;
    protected String threatFactionId;
    protected float prepareDays;
    protected int state = PREPARING;
    /**
     * Bug sweep OMI-2 (2026-10-06): the earliest moment a DEFENDING response may stand down
     * (dispatch time + the threat's ETA + ttDefenseStandDownDays). Before this, a response that
     * arrived EARLY -- the dispatch window allows it -- found no alert up yet (the alert is set
     * only once a threat fleet is within range of the station) and turned for home on the next
     * frame, before the threat arrived. Stored as dispatch time + days rather than a computed
     * timestamp, so no clock-unit arithmetic is involved. 0 days on scripts from older saves =
     * the old behaviour.
     */
    protected long dispatchTs = 0L;
    protected float minHoldDays = 0f;
    /** Throttles the DEFENDING threat scan and the RETURNING watchdog (OMI-12). Transient. */
    protected transient IntervalUtil checkInterval;

    public OmicronTriTachyonResponseScript(CampaignFleetAPI fleet, SectorEntityToken source,
                                           String nodeId, String targetMarketId,
                                           String threatFactionId, float prepareDays) {
        this(fleet, source, nodeId, targetMarketId, threatFactionId, prepareDays, 0f);
    }

    /** @param minHoldDays days from dispatch before a DEFENDING response may stand down. */
    public OmicronTriTachyonResponseScript(CampaignFleetAPI fleet, SectorEntityToken source,
                                           String nodeId, String targetMarketId,
                                           String threatFactionId, float prepareDays,
                                           float minHoldDays) {
        this.fleet = fleet;
        this.source = source;
        this.nodeId = nodeId;
        this.targetMarketId = targetMarketId;
        this.threatFactionId = threatFactionId;
        this.prepareDays = Math.max(0.25f, prepareDays);
        this.minHoldDays = Math.max(0f, minHoldDays);
        if (Global.getSector() != null) {
            dispatchTs = Global.getSector().getClock().getTimestamp();
        }
        beginPreparation();
    }

    /** OMI-2: has the minimum hold (threat ETA + stand-down, from dispatch) run out? */
    protected boolean holdExpired() {
        if (minHoldDays <= 0f || Global.getSector() == null) return true;
        return Global.getSector().getClock().getElapsedDaysSince(dispatchTs) >= minHoldDays;
    }

    protected void beginPreparation() {
        if (fleet == null || source == null) return;
        fleet.clearAssignments();
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        OmicronTriTachyonDefenseManager.applyDefenseOnlyRoe(
                fleet, threatFactionId, true);
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, prepareDays,
                "preparing a strategic response");
    }

    protected void beginTravel() {
        MarketAPI target = getTarget();
        if (target == null || target.getPrimaryEntity() == null) {
            beginReturn();
            return;
        }
        state = TRAVELLING;
        fleet.clearAssignments();
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target.getPrimaryEntity(), 1000f,
                "responding to a threat near " + target.getName());
    }

    protected void beginDefense() {
        MarketAPI target = getTarget();
        if (target == null || target.getPrimaryEntity() == null) {
            beginReturn();
            return;
        }
        state = DEFENDING;
        fleet.clearAssignments();
        // It may now join battles around the protected station, but ordinary fleets still
        // ignore it; the explicit threat is the only faction this fleet treats as hostile.
        fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        OmicronTriTachyonDefenseManager.applyDefenseOnlyRoe(
                fleet, threatFactionId, true);
        OmicronTriTachyonDefenseManager.assignThreatOrHold(
                fleet, target.getPrimaryEntity(), threatFactionId,
                "holding at " + target.getName());
    }

    protected void beginReturn() {
        if (state == RETURNING || fleet == null) return;
        state = RETURNING;
        fleet.clearAssignments();
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        OmicronTriTachyonDefenseManager.applyDefenseOnlyRoe(fleet, null, false);

        if (source != null && source.isAlive()) {
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source, 1000f,
                    "returning to an Omicron Nexus");
        } else {
            MarketAPI target = getTarget();
            SectorEntityToken fallback = target == null ? null : target.getPrimaryEntity();
            if (fallback != null) {
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, fallback, 10f,
                        "standing down");
            } else {
                fleet.setExpired(true);
            }
        }
    }

    public void advance(float amount) {
        if (Global.getSector() == null || Global.getSector().isPaused()) return;
        if (fleet == null || !fleet.isAlive() || fleet.isEmpty()) return;

        MarketAPI target = getTarget();
        if (state == PREPARING) {
            if (fleet.getCurrentAssignment() == null) beginTravel();
            return;
        }
        if (state == TRAVELLING) {
            if (target == null) {
                beginReturn();
                return;
            }
            if (fleet.getContainingLocation() == target.getContainingLocation()
                    && Misc.getDistance(fleet, target.getPrimaryEntity()) < 1500f) {
                beginDefense();
            }
            return;
        }
        // OMI-12: DEFENDING's fleet scan and the RETURNING watchdog run on a 0.1-0.2 day clock,
        // not every frame. PREPARING/TRAVELLING above stay per-frame (cheap checks).
        if (checkInterval == null) checkInterval = new IntervalUtil(0.1f, 0.2f);
        checkInterval.advance(Global.getSector().getClock().convertToDays(amount));
        if (!checkInterval.intervalElapsed()) return;

        if (state == DEFENDING) {
            boolean alert = target != null && target.getMemoryWithoutUpdate()
                    .getBoolean(OmicronTriTachyonDefenseManager.ALERT_KEY);
            // OMI-2: an early arrival holds until the threat's ETA + stand-down has passed,
            // even with no alert up yet; a lost target still sends it home at once.
            if (target == null || (!alert && holdExpired())) {
                beginReturn();
            } else {
                OmicronTriTachyonDefenseManager.assignThreatOrHold(
                        fleet, target.getPrimaryEntity(), threatFactionId,
                        "holding at " + target.getName());
            }
            return;
        }
        if (state == RETURNING && fleet.getCurrentAssignment() == null) {
            // OMI-12 watchdog: the despawn order lapsed (its 10-day fallback timed out, or the
            // Nexus died mid-trip). A setNoAutoDespawn fleet would otherwise idle forever and hold
            // its node's source slot. Re-issue, falling back to vanilla's standard return.
            if (source != null && source.isAlive()) {
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source, 1000f,
                        "returning to an Omicron Nexus");
            } else {
                Misc.giveStandardReturnToSourceAssignments(fleet, true);
            }
            Global.getLogger(OmicronTriTachyonResponseScript.class).info(
                    "Omicron response " + nodeId + ": return order lapsed; re-issued"
                            + (source != null && source.isAlive() ? " (Nexus)" : " (standard return)"));
        }
    }

    protected MarketAPI getTarget() {
        if (targetMarketId == null || Global.getSector() == null) return null;
        return Global.getSector().getEconomy().getMarket(targetMarketId);
    }

    public boolean isDone() {
        return fleet == null || !fleet.isAlive() || fleet.isExpired();
    }

    public boolean runWhilePaused() {
        return false;
    }

    public String getNodeId() {
        return nodeId;
    }

    /** One response occupies the node's Nexus source slot until its fleet despawns. */
    public static int countActiveForNode(String nodeId) {
        if (Global.getSector() == null || nodeId == null) return 0;
        int count = 0;
        List scripts = Global.getSector().getScripts();
        for (int i = 0; i < scripts.size(); i++) {
            Object script = scripts.get(i);
            if (!(script instanceof OmicronTriTachyonResponseScript)) continue;
            OmicronTriTachyonResponseScript response =
                    (OmicronTriTachyonResponseScript) script;
            if (!response.isDone() && nodeId.equals(response.getNodeId())) count++;
        }
        return count;
    }
}
