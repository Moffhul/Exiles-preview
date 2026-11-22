package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * The rung's blow (Pass 7 step 7d-2; war doc sec 6.1, STRIKE FLEET, FAILURE AND TENSION:
 * "reuse ExileHegLeagueStrikeStage's structure ... Each rung is a variant whose payload is that
 * rung's ruled wound, not the standard strike package").
 *
 * <p><b>Inherited unchanged</b> from {@link ExileHegLeagueStrikeStage}: one fixed target, the
 * tactical {@code canBeat} at the moment of attack, sticky success, the window override of vanilla's
 * {@code maxDays}, the window-end resolution on strength whether or not the player is near, and the
 * suppression of every OTHER market's station.
 *
 * <p><b>What changes:</b> the payload (the rung's industries, each with its own duration, snapshot
 * at launch so a mid-flight config edit cannot change a blow already sailing), the relief record
 * (only the industries the ruling lets relief shorten), the Hegemony-ownership re-check at impact,
 * whether the player was ever in range (the breadcrumb), and the return orders.
 */
public class ExileHegLeagueMidanStrikeStage extends ExileHegLeagueStrikeStage {

    protected ArrayList payloadIds = new ArrayList();      // String
    protected ArrayList payloadDays = new ArrayList();     // Float, parallel
    protected ArrayList reliefIds = new ArrayList();       // String
    protected float reliefDays;
    protected int rungIndex;
    protected boolean playerPresent = false;
    /** 7e-2: this rung is Eventide's wound (ExileEventideWound), not an industry list. */
    protected boolean eventideWound = false;
    /** 7e-2: the on-screen sequence has begun (guards the raid AI's repeated performRaid calls). */
    protected boolean eventideSequenceStarted = false;

    public ExileHegLeagueMidanStrikeStage(RaidIntel raid, StarSystemAPI target,
                                          String targetMarketId, int rungIndex,
                                          ExileHegLeagueConfig.MidanRungPayload payload,
                                          float orbitDays, int unrestPoints, float maxDays) {
        // disruptDays/base carry the longest wound for the inherited log and window code only;
        // the payload below is what is actually applied.
        super(raid, target, targetMarketId, Factions.PERSEAN, Factions.HEGEMONY,
                (int) maxPayloadDays(payload), 1, null, orbitDays, unrestPoints, maxDays,
                maxPayloadDays(payload));
        this.rungIndex = rungIndex;
        if (payload != null) {
            for (int i = 0; i < payload.industryIds.size(); i++) {
                payloadIds.add(payload.industryIds.get(i));
                payloadDays.add(payload.industryDays.get(i));
            }
            for (int i = 0; i < payload.reliefIndustryIds.size(); i++) {
                reliefIds.add(payload.reliefIndustryIds.get(i));
            }
            reliefDays = payload.reliefDays;
            eventideWound = payload.eventideWound;
        }
    }

    /** The strike's flag (the Persean League) -- the bombarding faction at Eventide. */
    public String getActorFactionId() {
        return actorFactionId;
    }

    public boolean isStrikeAborted() {
        return strikeAborted;
    }

    /** Once Eventide's sequence runs, the raid AI is kept out of it: the hold below owns the
     *  fleets, and performRaid has nothing left to start. */
    @Override
    public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (eventideSequenceStarted) return false;
        return super.canRaid(fleet, market);
    }

    /** Is the player within spawn range of the target system (fleets live, the strike seen)? */
    public boolean isOnScreen() {
        return system != null && RouteManager.isPlayerInSpawnRange(system.getCenter());
    }

    /**
     * Is a live Strike Force fleet over the target right now -- out of battle and within its hold
     * distance of the planet (vanilla's bombarding fleet sits at the planet's edge)? The Eventide
     * sequence's clock runs only while one is.
     */
    public boolean hasFleetOverTarget(MarketAPI market) {
        if (market == null || market.getPrimaryEntity() == null) return false;
        SectorEntityToken entity = market.getPrimaryEntity();
        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            CampaignFleetAPI fleet = ((RouteManager.RouteData) routes.get(i)).getActiveFleet();
            if (fleet == null || !fleet.isAlive() || fleet.getBattle() != null) continue;
            if (fleet.getContainingLocation() != entity.getContainingLocation()) continue;
            if (Misc.getDistance(fleet, entity)
                    <= entity.getRadius() + fleet.getRadius() + OVER_TARGET_MARGIN) {
                return true;
            }
        }
        return false;
    }

    /** Slack (units) beyond the planet's and fleet's radii that still counts as over the target. */
    protected static final float OVER_TARGET_MARGIN = 400f;

    protected static float maxPayloadDays(ExileHegLeagueConfig.MidanRungPayload payload) {
        float max = 1f;
        if (payload == null) return max;
        for (int i = 0; i < payload.industryDays.size(); i++) {
            float d = ((Float) payload.industryDays.get(i)).floatValue();
            if (d > max) max = d;
        }
        return max;
    }

    public boolean wasPlayerPresent() {
        return playerPresent;
    }

    @Override
    protected String logLabel() {
        return "Midan strike";
    }

    protected float daysFor(String industryId) {
        for (int i = 0; i < payloadIds.size(); i++) {
            if (payloadIds.get(i).equals(industryId)) {
                return ((Float) payloadDays.get(i)).floatValue();
            }
        }
        return 0f;
    }

    /** The rung's industries that this market actually has. Disrupted ones count too: the blow
     *  extends them under max semantics. */
    @Override
    protected List pickIndustries(MarketAPI market) {
        List result = new ArrayList();
        for (int i = 0; i < payloadIds.size(); i++) {
            String id = (String) payloadIds.get(i);
            if (market.getIndustry(id) != null) result.add(id);
        }
        return result;
    }

    /**
     * WINDOW END WITH THE PLAYER PRESENT -- <b>RULED by Sean, 2026-10-30</b>: a Midan strike the
     * player can see lands ONLY through a visible victory (the live fleets beating the station and
     * raiding). If the window runs out first, the Strike Force breaks off and the strike is FOILED.
     * Off-screen (player out of spawn range, fleets abstract) it still resolves on strength, as the
     * 2026-09-28 window-end ruling has it for every Heg-League strike.
     */
    @Override
    protected void autoresolve() {
        if (system != null && RouteManager.isPlayerInSpawnRange(system.getCenter())) {
            strikeAborted = true;
            finish(false);
            return;
        }
        super.autoresolve();
    }

    /** The parent's line, except that a player-present window end says it is breaking off. */
    @Override
    protected void logWindowResolution(boolean inSpawnRange) {
        if (!inSpawnRange) {
            super.logWindowResolution(inSpawnRange);
            return;
        }
        MarketAPI market = targetMarketId != null
                ? Global.getSector().getEconomy().getMarket(targetMarketId) : null;
        float str = com.fs.starfarer.api.impl.campaign.command.WarSimScript.getFactionStrength(
                intel.getFaction(), system);
        float enemy = com.fs.starfarer.api.impl.campaign.command.WarSimScript.getEnemyStrength(
                intel.getFaction(), system);
        float station = market != null
                ? ExileStationStrength.get(market.getFaction(), system, market.getPrimaryEntity()) : 0f;
        log("Exiles Midan strike WINDOW END at " + system.getBaseName() + " -- "
                + (market != null ? market.getName() : "?")
                + ": group strength " + (int) str + " vs defense " + (int) (enemy + station)
                + " (enemy=" + (int) enemy + " + station=" + (int) station + ")"
                + " -> BREAKING OFF: no visible victory with the player present (ruled 2026-10-30)"
                + " | player IN spawn range | " + (int) elapsed + "d of " + (int) maxDays + "d");
    }

    /** Assignment custom marking the strike's own assault orders. */
    protected static final String ASSAULT_ORDER = "exiles_midan_assault";
    /** How often (days) live fleets are checked for their assault order. */
    protected static final float ASSAULT_ORDER_DAYS = 0.25f;
    protected float lastAssaultOrderElapsed = -999f;

    /** Arrival (the parent's window and measurement), then the first assault order. */
    @Override
    protected void updateRoutes() {
        super.updateRoutes();
        // The quiet-staging flag comes off: from here they fight at the target (7e-1 test fix).
        if (intel instanceof ExileHegLeagueMidanStrikeIntel) {
            ((ExileHegLeagueMidanStrikeIntel) intel).onActionStarted();
        }
        lastAssaultOrderElapsed = elapsed;
        orderAssault();
    }

    /** Breadcrumb: was the player ever within spawn range of the target system during the action?
     *  Then the assault orders, re-checked every {@link #ASSAULT_ORDER_DAYS}. */
    @Override
    protected void updateStatus() {
        if (!playerPresent && system != null
                && RouteManager.isPlayerInSpawnRange(system.getCenter())) {
            playerPresent = true;
        }
        if (elapsed - lastAssaultOrderElapsed >= ASSAULT_ORDER_DAYS) {
            lastAssaultOrderElapsed = elapsed;
            orderAssault();
        }
        super.updateStatus();
    }

    /**
     * THE LIVE ASSAULT (found in the 2026-10-30 retest). Vanilla leaves live raiders on a system
     * patrol and sends only a SHARE of them at the target through its military-response script
     * (respondingFP / potentialFP below the response fraction). So one run's lone Ajax fleet
     * chased patrols, the next run's escort hit the station while the Ajax did not, and both times
     * the Citadel fell only to the window-end autoresolve, with no visible attack.
     *
     * <p>Every live Strike Force fleet in the system gets an explicit order at the front of its
     * queue: INTERCEPT the station fleet while the station stands (engages only that target, and
     * never gives up the pursuit), then GO TO the station entity once it is down, which brings
     * them within the 2000 units vanilla's raid check needs. Fleets in a battle are left alone, and
     * nothing is issued after the blow has landed (or been called off). Off-screen this is a
     * no-op: abstract routes have no fleet.
     */
    protected void orderAssault() {
        if (strikeDone || strikeAborted || status != RaidIntel.RaidStageStatus.ONGOING) return;
        if (eventideSequenceStarted) { // the win is in; they hold over Eventide now
            holdOverEventide();
            return;
        }
        MarketAPI market = targetMarketId != null
                ? Global.getSector().getEconomy().getMarket(targetMarketId) : null;
        if (market == null || market.getPrimaryEntity() == null) return;
        SectorEntityToken entity = market.getPrimaryEntity();
        CampaignFleetAPI station = Misc.getStationFleet(market);
        boolean stationUp = station != null && station.isAlive()
                && station.getContainingLocation() == system;

        int issued = 0;
        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            CampaignFleetAPI fleet = ((RouteManager.RouteData) routes.get(i)).getActiveFleet();
            if (fleet == null || !fleet.isAlive() || fleet.getBattle() != null) continue;
            if (fleet.getContainingLocation() != system) continue;
            FleetAssignmentDataAPI curr = fleet.getCurrentAssignment();
            if (curr != null && ASSAULT_ORDER.equals(curr.getCustom())
                    && curr.getTarget() == (stationUp ? (SectorEntityToken) station : entity)) {
                continue;
            }
            if (!stationUp && Misc.getDistance(fleet, entity) < 1500f) continue;
            if (stationUp) {
                fleet.addAssignmentAtStart(FleetAssignment.INTERCEPT, station, 3f,
                        "attacking " + market.getName(), null);
            } else {
                fleet.addAssignmentAtStart(FleetAssignment.GO_TO_LOCATION, entity, 3f,
                        "attacking " + market.getName(), null);
            }
            FleetAssignmentDataAPI added = fleet.getCurrentAssignment();
            if (added != null) added.setCustom(ASSAULT_ORDER);
            issued++;
        }
        if (issued > 0 && elapsed - lastAssaultLogElapsed >= 1f) {
            lastAssaultLogElapsed = elapsed;
            log("Exiles Midan ASSAULT ORDER: " + issued + " live fleet(s) -> "
                    + (stationUp ? "INTERCEPT the station" : "GO TO " + market.getName()
                       + " (station down)") + " | day " + (int) elapsed + " of the window");
        }
    }

    protected float lastAssaultLogElapsed = -999f;

    /**
     * The landing. Same order and rails as the parent's (protected/Battered re-check first, sticky
     * success, unrest, deciv-guard pressure, the visible orbit hold), with the rung's own wound and
     * relief. Kept as an override rather than a change to the parent so the playtested Pass-3
     * strike is untouched.
     */
    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (strikeDone || strikeAborted) return;
        if (market == null) return;
        if (targetMarketId == null || !targetMarketId.equals(market.getId())) return;

        // Ownership can change in flight under Nexerelin; Midan only frames attacks on the Hegemony.
        if (!Factions.HEGEMONY.equals(market.getFactionId())
                || ExileProtectedFactions.isProtected(market) || ExileUnrestGuard.isBattered(market)) {
            log("Exiles Midan strike ABORTED on arrival at " + market.getName()
                    + " -- no longer Hegemony, or protected, or Battered; nothing applied");
            strikeAborted = true;
            finish(false);
            return;
        }

        if (eventideWound) {
            performEventide(fleet, market);
            return;
        }

        List picks = pickIndustries(market);
        if (picks.isEmpty()) {
            log("Exiles Midan strike reached " + market.getName()
                    + " but none of the rung's industries exist there; nothing applied");
            strikeAborted = true;
            finish(false);
            return;
        }

        strikeDone = true;
        strikeLandedElapsed = elapsed;

        StringBuffer sb = new StringBuffer();
        float longest = 0f;
        for (int i = 0; i < picks.size(); i++) {
            String id = (String) picks.get(i);
            float days = daysFor(id);
            Industry industry = market.getIndustry(id);
            if (industry == null || days <= 0f) continue;
            industry.setDisrupted(days, true); // max semantics, never additive
            if (days > longest) longest = days;
            if (sb.length() > 0) sb.append(", ");
            sb.append(id).append(" ").append((int) days).append("d");
        }
        struckIndustries = sb.toString();

        if (unrestPoints > 0) {
            RecentUnrest.get(market).add(unrestPoints, "Persean League military strike");
        }
        ExileUnrestGuard.reportExilesPressure(market,
                ExileEmergencyMeasuresConfig.pressureDays(longest, 0));

        // The visible beat over the target (the parent's pattern).
        if (orbitDays > 0f && market.getPrimaryEntity() != null) {
            resetRoutes();
            List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
            for (int i = 0; i < routes.size(); i++) {
                RouteManager.RouteData route = (RouteManager.RouteData) routes.get(i);
                route.addSegment(new RouteManager.RouteSegment(orbitDays + 2f,
                        market.getPrimaryEntity()));
            }
        }

        log("Exiles Midan STRIKE LANDED: rung " + (rungIndex + 1) + " " + market.getName()
                + " -- " + struckIndustries
                + (unrestPoints > 0 ? " | unrest +" + unrestPoints : "")
                + " | player " + (playerPresent ? "in range" : "out of range"));

        // Ruled 2026-10-30: once the blow lands, the Strike Force starts no more fights.
        if (intel instanceof ExileHegLeagueMidanStrikeIntel) {
            ((ExileHegLeagueMidanStrikeIntel) intel).onLanded();
        }

        // Relief: only the industries the ruling lets the Hegemony shorten, by at most reliefDays.
        List relief = new ArrayList();
        for (int i = 0; i < reliefIds.size(); i++) {
            if (picks.contains(reliefIds.get(i))) relief.add(reliefIds.get(i));
        }
        if (!relief.isEmpty() && reliefDays > 0f) {
            ExileHegLeagueRecoveryManager.record(market, relief, reliefDays, Factions.HEGEMONY);
        }
    }

    /**
     * RUNG 3, EVENTIDE (7e-2). Reached only after the same rails as every rung and a WIN: the live
     * raid AI calls this once the Strike Force has beaten Eventide's defenders (Sean, 2026-11-01:
     * "after a visible win"), and the off-screen path calls it from the window-end autoresolve.
     * <ul>
     * <li><b>Off-screen</b> (no live fleet, or the player out of range): the whole wound at once, and
     * the rung lands now.</li>
     * <li><b>On-screen:</b> {@link ExileEventideSequence} takes it one beat at a time; the rung lands
     * only when the bombardment (the last beat) falls ({@link #onEventideSequenceComplete}). The
     * window is stretched to cover the sequence twice over (the second length is slack for the
     * pauses below), and the fleets hold over Eventide meanwhile ({@link #holdOverEventide}).</li>
     * </ul>
     */
    protected void performEventide(CampaignFleetAPI fleet, MarketAPI market) {
        if (eventideSequenceStarted) return;
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        ExileHegLeagueConfig config = director != null ? director.getConfig() : null;
        if (config == null) config = ExileHegLeagueConfig.load();

        boolean onScreen = fleet != null && isOnScreen();
        if (!onScreen) {
            landEventide(market, ExileEventideWound.applyAll(market, config));
            return;
        }

        eventideSequenceStarted = true;
        float length = ExileEventideSequence.lengthDays(config);
        maxDays = Math.max(maxDays, elapsed + 2f * length + orbitDays + 2f);
        holdOver(market, 2f * length + orbitDays + 2f); // the route's fallback; live fleets: below
        holdOverEventide();
        Global.getSector().addScript(new ExileEventideSequence(this, market.getId(), config));
        log("Exiles Midan EVENTIDE SEQUENCE STARTED at " + market.getName() + ": "
                + config.eventideBeatNames.size() + " beat(s) + the bombardment, one every "
                + config.eventideBeatDays + "d (~" + (int) Math.ceil(length) + "d) | window now "
                + (int) maxDays + "d");
    }

    /** Assignment custom marking the strike's own hold over Eventide. */
    protected static final String EVENTIDE_HOLD = "exiles_midan_eventide_hold";

    /**
     * THE HOLD OVER EVENTIDE (7e-2 test fix, 2026-11-01). The first build reset the routes to an
     * orbit segment, which the route AI turns into ORBIT_AGGRESSIVE -- engage like a patrol -- so
     * in Samarra's traffic the fleets left the planet to chase contacts, and the mirrors fell and
     * the bombs dropped with no fleet in orbit.
     *
     * <p>Now it is vanilla's own bombardment posture, the one a Diktat punitive expedition uses
     * ({@code BaseAssignmentAI.giveRaidOrder} under {@code FGRaidAction}): a token on the planet's
     * edge, orbiting it, at the planet's radius plus half the fleet's; DELIVER_CREW to reach it
     * ("moving to bombard"), then HOLD on it ("bombarding"). HOLD never starts a fight, though a
     * fleet that is attacked still fights back. Every live Strike Force fleet in the system gets
     * it, re-checked with the assault orders every quarter day, so a fleet pulled into a battle
     * returns to the planet afterwards. The sequence's clock runs only while one of them is over
     * Eventide ({@link #hasFleetOverTarget}).
     */
    protected void holdOverEventide() {
        if (strikeDone || strikeAborted || status != RaidIntel.RaidStageStatus.ONGOING) return;
        MarketAPI market = targetMarketId != null
                ? Global.getSector().getEconomy().getMarket(targetMarketId) : null;
        if (market == null || market.getPrimaryEntity() == null) return;
        SectorEntityToken entity = market.getPrimaryEntity();
        float days = Math.max(1f, maxDays - elapsed);

        int issued = 0;
        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            CampaignFleetAPI fleet = ((RouteManager.RouteData) routes.get(i)).getActiveFleet();
            if (fleet == null || !fleet.isAlive() || fleet.getBattle() != null) continue;
            if (fleet.getContainingLocation() != entity.getContainingLocation()) continue;
            FleetAssignmentDataAPI curr = fleet.getCurrentAssignment();
            if (curr != null && EVENTIDE_HOLD.equals(curr.getCustom())) continue;

            float angle = Misc.getAngleInDegrees(entity.getLocation(), fleet.getLocation());
            float radius = fleet.getRadius() * 0.5f + entity.getRadius();
            org.lwjgl.util.vector.Vector2f loc = Misc.getUnitVectorAtDegreeAngle(angle);
            loc.scale(radius);
            org.lwjgl.util.vector.Vector2f.add(loc, entity.getLocation(), loc);
            SectorEntityToken holdLoc = entity.getContainingLocation().createToken(loc);
            holdLoc.setCircularOrbit(entity, angle, radius, 1000000f);
            entity.getContainingLocation().addEntity(holdLoc);
            Misc.fadeAndExpire(holdLoc, Global.getSector().getClock().convertToSeconds(days + 1f));

            fleet.addAssignmentAtStart(FleetAssignment.HOLD, holdLoc, days,
                    "bombarding " + market.getName(), null);
            FleetAssignmentDataAPI added = fleet.getCurrentAssignment();
            if (added != null) added.setCustom(EVENTIDE_HOLD);
            if (Misc.getDistance(fleet, entity) > fleet.getRadius() + entity.getRadius()) {
                fleet.addAssignmentAtStart(FleetAssignment.DELIVER_CREW, holdLoc, 3f,
                        "moving to bombard " + market.getName(), null);
                added = fleet.getCurrentAssignment();
                if (added != null) added.setCustom(EVENTIDE_HOLD);
            }
            issued++;
        }
        if (issued > 0) {
            log("Exiles Midan EVENTIDE HOLD: " + issued + " live fleet(s) ordered over "
                    + market.getName() + " | day " + (int) elapsed + " of the window ("
                    + (int) maxDays + "d)");
        }
    }

    /** Called by the sequence when the bombardment falls: only now has rung 3 landed. */
    public void onEventideSequenceComplete(MarketAPI market, String summary) {
        if (strikeDone || strikeAborted) return;
        landEventide(market, summary);
    }

    /** Rung 3 has landed (either path): the parent's landing beats, with the wound as the summary. */
    protected void landEventide(MarketAPI market, String summary) {
        strikeDone = true;
        strikeLandedElapsed = elapsed;
        struckIndustries = summary;
        // No unrest of our own: the saturation bombardment's vanilla -10 is the whole stability hit
        // (Sean, 2026-11-01: the sat-bomb does the heavy lifting).
        //
        // EMERGENCY MEASURES MUST SEE IT (Sean, 2026-11-01): this is the explicit Exiles pressure
        // report that arms Eventide's dormant Emergency Measures, sized to what actually landed --
        // the longest vanilla bombardment disruption (365 x 1.25 days) and its 10 unrest points. If
        // Eventide then sits at Stability 0 for 60 days, Emergency Measures activates long before
        // vanilla decivilization could (16 months of history, a 3-month zero streak, then 10% a
        // month -- settings.json decivSamplingMonths / decivMinStreak / decivProbPerMonthOverStreak).
        int bombardUnrest = com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD
                .getSaturationBombardmentStabilityPenalty();
        float longestDisruption = com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD
                .getBombardDisruptDuration() * 1.25f;
        ExileUnrestGuard.reportExilesPressure(market,
                ExileEmergencyMeasuresConfig.pressureDays(longestDisruption, bombardUnrest));
        holdOver(market, orbitDays + 2f);
        log("Exiles Midan STRIKE LANDED: rung " + (rungIndex + 1) + " " + market.getName()
                + " -- " + summary + " | Emergency Measures armed (pressure "
                + (int) ExileEmergencyMeasuresConfig.pressureDays(longestDisruption, bombardUnrest)
                + "d) | player " + (playerPresent ? "in range" : "out of range"));
        if (intel instanceof ExileHegLeagueMidanStrikeIntel) {
            ((ExileHegLeagueMidanStrikeIntel) intel).onLanded();
        }
    }

    /** The visible beat over the target (the parent's pattern), for {@code days}. */
    protected void holdOver(MarketAPI market, float days) {
        if (market.getPrimaryEntity() == null) return;
        resetRoutes();
        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            RouteManager.RouteData route = (RouteManager.RouteData) routes.get(i);
            route.addSegment(new RouteManager.RouteSegment(days, market.getPrimaryEntity()));
        }
    }

    @Override
    public void giveReturnOrdersToStragglers(List stragglers) {
        ExileHegLeagueMidanStrikeIntel.sendHome(this, stragglers, returnPoint());
    }

    protected com.fs.starfarer.api.campaign.SectorEntityToken returnPoint() {
        if (intel instanceof ExileHegLeagueMidanStrikeIntel) {
            return ((ExileHegLeagueMidanStrikeIntel) intel).getReturnPoint();
        }
        return null;
    }

    // --- Text: PLACEHOLDER ---

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        MarketAPI target = targetMarketId == null
                ? null : Global.getSector().getEconomy().getMarket(targetMarketId);
        String targetName = target != null ? target.getName() : "the target";
        if (status == RaidIntel.RaidStageStatus.FAILURE) {
            info.addPara("The strike force failed to press home its attack on " + targetName
                    + ". The operation is over.", 10f);
        } else if (status == RaidIntel.RaidStageStatus.SUCCESS && eventideWound) {
            info.addPara("The strike force tore down " + targetName + "'s orbital mirrors and"
                    + " bombarded the world below. Nothing will bring its sky back.", 10f);
        } else if (status == RaidIntel.RaidStageStatus.SUCCESS) {
            info.addPara("The strike force hit " + targetName + " hard, wrecking its military"
                    + " installations. The Hegemony will be without them for many months.", 10f);
        } else if (curr == index && eventideSequenceStarted) {
            info.addPara("The strike force is tearing down " + targetName + "'s orbital mirrors.", 10f);
        } else if (curr == index) {
            info.addPara("The strike force is attacking " + targetName + ".", 10f);
        }
    }

    @Override
    public String getRaidInSystemText(CampaignFleetAPI fleet) {
        return "conducting a special strike";
    }

    @Override
    public String getRaidDefaultText(CampaignFleetAPI fleet) {
        return "conducting a special strike";
    }

    protected static void log(String line) {
        Global.getLogger(ExileHegLeagueMidanStrikeStage.class).info(line);
    }
}
