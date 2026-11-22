package data.scripts.exiles;

import java.awt.Color;
import java.util.List;
import java.util.Random;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * MIDAN'S STRIKE -- Pass 7 step 7d-2 (build plan docs/EXILES_PASS7_BUILD_PLAN.md sec 7, "7d-2
 * plan"; war doc sec 6.1). One launched rung of the false-flag ladder: the Strike Force (the PLS
 * Ajax's fleet plus 0-2 escort groups) flies Persean League colours against a Hegemony world.
 *
 * <p><b>A subclass of {@link ExileHegLeagueRaidIntel}, not a fork.</b> Inherited unchanged: the
 * Hegemony's per-kill bounty and its SYSTEM_BOUNTY_REWARD reputation (ruled 2026-10-30), the
 * victory ledger, the abort-stage line, the end-of-op system cooldown and the return-leg flag.
 * What differs:
 * <ul>
 * <li><b>A SNEAK ATTACK (ruled 2026-10-30, superseding the 7d-2 warning window).</b> The intel is
 * NOT added to the intel manager at launch -- a raid advances as its own sector script, so it
 * needs no registration -- which keeps it out of the player's view AND out of Nexerelin's
 * special-task-group defence scan (which reads every live RaidIntel, hidden or not). The fleets
 * form up inside the target system, out of sight. The first the player hears is the target's
 * <b>emergency call for assistance</b>, posted with the colony-crisis alarm when the leading fleet
 * is {@code distressLeadDays} out ({@link #sendDistress}). Fighting the Strike Force first, or its
 * arrival, sends the call early.</li>
 * <li><b>Fleets are composed, not generated</b> ({@link ExileHegLeagueMidanComposer}), and spawn
 * where their route currently is -- never into the anchor's Black Fleet system.</li>
 * <li><b>Behaviour flags:</b> until the strike lands they do not get sidetracked (straight for the
 * target; the stage's assault orders put every live fleet on the station); once it lands they start
 * no fights at all ({@link #onLanded}), though they still defend themselves.</li>
 * <li><b>No war weariness</b> from player kills (ruled 2026-10-30: Midan's losses are not the
 * League's), and the outcome goes to the director's Midan landing, not the ordinary op feed.</li>
 * <li><b>The Ajax is tracked.</b> A loss is logged once and is sticky: a respawned main fleet comes
 * back without it. If the player helped sink it, the Hegemony adds a one-time reputation bonus.
 * Its captain is one persistent person who escapes and returns next rung.</li>
 * </ul>
 */
public class ExileHegLeagueMidanStrikeIntel extends ExileHegLeagueRaidIntel {

    /** Route types (OptionalFleetData.fleetType), saved with each route. */
    public static final String MAIN_TYPE = "exiles_midan_main";
    public static final String ESCORT_TYPE = "exiles_midan_escort";
    /** Fleet tags: every Strike Force fleet, and the Ajax's own fleet. */
    public static final String STRIKE_FORCE_TAG = "exiles_midan_strike_force";
    public static final String MAIN_TAG = "exiles_midan_strike_main";

    /** How often (days) the approach ETA is re-read for the distress call. */
    protected static final float DISTRESS_CHECK_DAYS = 0.25f;

    /** 0-based ladder rung this strike carries. */
    protected int rungIndex;
    protected String targetMarketId;
    /** Where every fleet goes home to: the anchor system's hyperspace jump point. */
    protected SectorEntityToken returnPoint;
    protected int escorts;
    protected ExileHegLeagueMidanStrikeStage midanStage;
    protected ExileHegLeagueMidanTravelStage travelStage;
    /** The current Ajax fleet member's id; refreshed each time the main fleet spawns. */
    protected String ajaxMemberId;
    protected boolean ajaxLost = false;
    protected boolean playerFought = false;
    /** The target's emergency call has gone out (the intel is now registered and visible). */
    protected boolean distressSent = false;
    protected float daysSinceDistressCheck = 0f;
    protected boolean returnStandDownApplied = false;

    public ExileHegLeagueMidanStrikeIntel(StarSystemAPI target, FactionAPI league, int rungIndex,
                                         String targetMarketId, SectorEntityToken returnPoint,
                                         int escorts, float bountyPerFrigate,
                                         float systemCooldownDays) {
        // maxShipSize 0 (no cap: the roster is explicit) and weariness 0 (ruled: none).
        // Stays hidden (the parent's default) until the distress call.
        super(target, league, Factions.HEGEMONY, 0, bountyPerFrigate, 0f, systemCooldownDays);
        this.rungIndex = rungIndex;
        this.targetMarketId = targetMarketId;
        this.returnPoint = returnPoint;
        this.escorts = escorts;
    }

    public void setMidanStage(ExileHegLeagueMidanStrikeStage stage) {
        this.midanStage = stage;
    }

    public void setTravelStage(ExileHegLeagueMidanTravelStage stage) {
        this.travelStage = stage;
    }

    public int getRungIndex() {
        return rungIndex;
    }

    public SectorEntityToken getReturnPoint() {
        return returnPoint;
    }

    public boolean isDistressSent() {
        return distressSent;
    }

    @Override
    public boolean isInterdictionOp() {
        return false;
    }

    @Override
    protected String logLabel() {
        return "Midan";
    }

    @Override
    protected String getOpEndedText() {
        return "The attack on " + targetName() + " has ended";
    }

    protected MarketAPI getTargetMarket() {
        return targetMarketId != null ? Global.getSector().getEconomy().getMarket(targetMarketId) : null;
    }

    protected String targetName() {
        MarketAPI target = getTargetMarket();
        return target != null ? target.getName() : "a Hegemony world";
    }

    // ------------------------------------------------------------------
    // The emergency call -- the only warning (ruled 2026-10-30)
    // ------------------------------------------------------------------

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        // 7e-1 test (2026-11-01): on the way home -- landed or failed -- start no fights. The parent
        // flags the return leg in the tick the op leaves its action stage (or fails); a failed op
        // never reached onLanded(), so its fleets used to brawl their way out of the system.
        if (returnLegFlagged && !returnStandDownApplied) {
            returnStandDownApplied = true;
            standDownAll("return leg");
        }
        if (distressSent || isEnding() || isEnded() || travelStage == null) return;
        if (getCurrentStage() != getStageIndex(travelStage)) return;

        daysSinceDistressCheck += Misc.getDays(amount);
        if (daysSinceDistressCheck < DISTRESS_CHECK_DAYS) return;
        daysSinceDistressCheck = 0f;

        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        ExileHegLeagueConfig config = director != null ? director.getConfig() : null;
        float lead = config != null ? config.midanDistressLeadDays : 2f;
        float eta = estimateTravelRemaining();
        if (eta <= lead) sendDistress("approach, ETA " + format1(eta) + "d");
    }

    /**
     * The parent reveals an op when it reaches its action stage or when the player fights it. Here
     * either one sends the emergency call if it has not gone out yet (a short approach, or the player
     * found the Strike Force first); once it has, nothing more is posted -- no "arrived" update.
     */
    @Override
    protected void reveal(Object updateParam) {
        if (!distressSent) {
            sendDistress(updateParam == null ? "player contact" : "arrival");
            return;
        }
        revealed = true;
    }

    /** Registers the intel (until now it ran unlisted) and posts it with the colony-crisis alarm. */
    protected void sendDistress(String why) {
        if (distressSent || isEnding() || isEnded()) return;
        distressSent = true;
        revealed = true;
        setHidden(false);
        if (getPlayerVisibleTimestamp() == null) {
            setPlayerVisibleTimestamp(Long.valueOf(Global.getSector().getClock().getTimestamp()));
        }
        Global.getSector().getIntelManager().addIntel(this, false);
        Global.getLogger(ExileHegLeagueMidanStrikeIntel.class).info(
                "Exiles Midan DISTRESS CALL: " + targetName() + " (" + why + ") | rung "
                        + (rungIndex + 1) + " | stage " + getCurrentStage());
    }

    /** The colony-crisis alarm for the call itself; ordinary update sounds after that. */
    @Override
    public String getCommMessageSound() {
        if (!isSendingUpdate()) return getSoundColonyThreat();
        return super.getCommMessageSound();
    }

    /**
     * Days until the leading fleet reaches the target. A live fleet is measured by distance and its
     * own travel speed; an abstract one by what is left of its travel segment (vanilla's 1500
     * units/day). 0 when there is nothing left to measure.
     */
    protected float estimateTravelRemaining() {
        MarketAPI target = getTargetMarket();
        SectorEntityToken t = target != null ? target.getPrimaryEntity() : null;
        List routes = RouteManager.getInstance().getRoutesForSource(getRouteSourceId());
        if (routes.isEmpty()) return 0f;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            float days = 0f;
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet != null && t != null && fleet.getContainingLocation() == t.getContainingLocation()) {
                float perDay = fleet.getTravelSpeed() * Global.getSector().getClock().getSecondsPerDay();
                days = perDay > 1f ? Misc.getDistance(fleet, t) / perDay : 0f;
            } else {
                RouteSegment seg = route.getCurrent();
                if (seg != null && seg.to != null) days = Math.max(0f, seg.daysMax - seg.elapsed);
            }
            if (days < best) best = days;
        }
        return best == Float.MAX_VALUE ? 0f : best;
    }

    /** Only meaningful once the call is out: the approach estimate, else nothing to show. */
    @Override
    public float getETA() {
        if (travelStage == null) return super.getETA();
        if (getCurrentStage() == getStageIndex(travelStage)) return estimateTravelRemaining();
        return 0f;
    }

    // ------------------------------------------------------------------
    // Fleets
    // ------------------------------------------------------------------

    /** Composed from the explicit roster; the route's type picks main or escort. */
    @Override
    public CampaignFleetAPI createFleet(String factionId, RouteData route, MarketAPI market,
                                        Vector2f locInHyper, Random random) {
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        ExileHegLeagueConfig config = director != null ? director.getConfig() : null;
        if (config == null) return null;
        if (random == null) random = new Random();

        boolean main = route != null && route.getExtra() != null
                && MAIN_TYPE.equals(route.getExtra().fleetType);
        CampaignFleetAPI fleet = main
                ? ExileHegLeagueMidanComposer.composeMainFleet(config, random, !ajaxLost, true)
                : ExileHegLeagueMidanComposer.composeEscortFleet(config, random);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);
        // The League disowns the fleet: a player who fights it loses no League standing.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        // Player-scoped (fleet behaviour doc sec 4): it never picks a fight with the player. The
        // player can still choose to engage it.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        if ((midanStage != null && midanStage.hasLanded()) || returnLegFlagged || getFailStage() >= 0) {
            standDown(fleet);
        } else {
            // Approach AND attack: straight for the target, no chasing patrols (ruled 2026-10-30;
            // kept through the action stage after the retest, where a fleet freed of it at arrival
            // chased patrols for the whole window). The stage's assault orders steer them in.
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
            // 7e-1 test (2026-11-01): while waiting and approaching, start no fights at all -- four
            // fleets parked in Sphinx's system fought its patrols for two months and never formed
            // up. They still defend themselves; the flag comes off when the attack begins.
            if (!isAtOrPastAction()) {
                fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
            }
        }
        fleet.addTag(RAIDER_TAG);
        fleet.addTag(STRIKE_FORCE_TAG);
        if (main) {
            fleet.addTag(MAIN_TAG);
            if (!ajaxLost && fleet.getFlagship() != null) {
                ajaxMemberId = fleet.getFlagship().getId();
            }
        }
        return fleet;
    }

    /**
     * Vanilla adds a new fleet to the SOURCE market's system first. Here that system belongs to the
     * Black Fleet, which is hostile to the League, so the fleet goes straight to where its route
     * currently is: the staging point inside the target system, or hyperspace on the way home. Its
     * assignment AI then places it exactly.
     */
    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        Random random = route.getRandom();
        CampaignFleetAPI fleet = createFleet(Factions.PERSEAN, route, route.getMarket(), null, random);
        if (fleet == null || fleet.isEmpty()) return null;

        RouteSegment seg = route.getCurrent();
        LocationAPI loc = null;
        if (seg != null && (seg.from != null || seg.to != null)) {
            loc = seg.getCurrentContainingLocation();
        }
        if (loc == null) loc = Global.getSector().getHyperspace();
        loc.addEntity(fleet);
        if (loc.isHyperspace()) {
            Vector2f at = route.getInterpolatedHyperLocation();
            if (at != null) fleet.setLocation(at.x, at.y);
        } else if (seg.from != null && seg.from.getContainingLocation() == loc) {
            fleet.setLocation(seg.from.getLocation().x, seg.from.getLocation().y);
        } else if (seg.to != null && seg.to.getContainingLocation() == loc) {
            fleet.setLocation(seg.to.getLocation().x, seg.to.getLocation().y);
        }
        fleet.setFacing((float) Math.random() * 360f);
        fleet.addScript(createAssignmentAI(fleet, route));

        fleet.addEventListener(this);
        if (returnLegFlagged || ExileReturnLeg.isStraggler(route)) ExileReturnLeg.flag(fleet);
        return fleet;
    }

    /**
     * The strike has landed (ruled 2026-10-30): from here the Strike Force starts no fights -- the
     * orbit beat and the trip home are not a hunt for the target's surviving patrols. It still
     * defends itself if attacked. Fleets that spawn later get the same flags in createFleet.
     */
    public void onLanded() {
        standDownAll("landed");
    }

    /** The attack begins: the quiet-staging flag comes off so the fleets fight at the target. */
    public void onActionStarted() {
        List routes = RouteManager.getInstance().getRoutesForSource(getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            CampaignFleetAPI fleet = ((RouteData) routes.get(i)).getActiveFleet();
            if (fleet == null || !fleet.isAlive()) continue;
            fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        }
    }

    protected void standDownAll(String why) {
        int n = 0;
        List routes = RouteManager.getInstance().getRoutesForSource(getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            CampaignFleetAPI fleet = ((RouteData) routes.get(i)).getActiveFleet();
            if (fleet == null || !fleet.isAlive()) continue;
            standDown(fleet);
            n++;
        }
        Global.getLogger(ExileHegLeagueMidanStrikeIntel.class).info(
                "Exiles Midan STAND DOWN (" + why + "): " + n + " live fleet(s) flagged to start no"
                        + " fights | " + (routes.size() - n) + " abstract (flagged on spawn)");
    }

    protected boolean isAtOrPastAction() {
        return midanStage != null && getCurrentStage() >= getStageIndex(midanStage);
    }

    protected static void standDown(CampaignFleetAPI fleet) {
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
    }

    /**
     * Every stage's return orders come here: home is the anchor system's HYPERSPACE jump point,
     * where the fleet despawns (ruled 2026-10-20). Vanilla's version sends routes to the source
     * market, which would fly a League flag into a Black Fleet port.
     */
    public static void sendHome(BaseRaidStage stage, List routes, SectorEntityToken point) {
        if (routes == null) return;
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            Vector2f loc = route.getInterpolatedHyperLocation();
            if (loc == null) continue;
            SectorEntityToken from = Global.getSector().getHyperspace().createToken(loc.x, loc.y);
            route.setCustom(BaseRaidStage.STRAGGLER);
            stage.resetRoute(route);
            SectorEntityToken to = point != null ? point : from;
            float travelDays = RouteLocationCalculator.getTravelDays(from, to);
            route.addSegment(new RouteSegment(travelDays, from, to));
            route.addSegment(new RouteSegment(1f + (float) Math.random(), to));
        }
    }

    // ------------------------------------------------------------------
    // Battles: bounty (inherited), player involvement, the Ajax
    // ------------------------------------------------------------------

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner,
                                     BattleAPI battle) {
        super.reportBattleOccurred(fleet, primaryWinner, battle);
        if (fleet == null || !fleet.hasTag(STRIKE_FORCE_TAG)) return;
        if (battle != null && battle.isPlayerInvolved()) playerFought = true;
        if (fleet.hasTag(MAIN_TAG)) checkAjax(fleet, battle,
                battle != null && battle.isPlayerInvolved(), "battle");
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason,
                                               Object param) {
        super.reportFleetDespawnedToListener(fleet, reason, param);
        if (fleet == null || !fleet.hasTag(MAIN_TAG)) return;
        if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE) {
            checkAjax(fleet, null, playerFought, "fleet destroyed");
        }
    }

    /** Ruled 2026-10-30: player kills of Midan's ships add no League weariness. Bounty unchanged. */
    @Override
    protected void applyPlayerInflictedWeariness(BattleAPI battle) {
    }

    /** One line, once: the Ajax is no longer in its fleet. */
    protected void checkAjax(CampaignFleetAPI fleet, BattleAPI battle, boolean playerInvolved,
                             String how) {
        if (ajaxLost || ajaxMemberId == null) return;
        List members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) members.get(i);
            if (ajaxMemberId.equals(m.getId())) return;
        }
        ajaxLost = true;
        String where = fleet.getContainingLocation() != null
                ? fleet.getContainingLocation().getName() : "?";
        float rep = 0f;
        // Only a battle the player was in, and only with the player on the other side.
        if (battle != null && playerInvolved && !battle.isOnPlayerSide(fleet)) {
            rep = payAjaxRep();
        }
        Global.getLogger(ExileHegLeagueMidanStrikeIntel.class).info(
                "Exiles Midan AJAX LOST: " + how + " in " + where
                        + " | player " + (playerInvolved ? "involved" : "not involved")
                        + (rep > 0f ? " | Hegemony rep +" + format1(rep * 100f) : "")
                        + " | rung " + (rungIndex + 1) + " -> " + targetMarketId
                        + " | stage " + getCurrentStage()
                        + " | main fleet left: " + members.size() + " ships"
                        + " | captain escapes (returns next rung)");
        // Lost after the rung already resolved (on the way home): the module's count and the
        // Phase-2 breadcrumb were written as "survived" and need correcting.
        if (outcomeReported) {
            ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
            ExileHegLeagueMidanModule module = director != null ? director.getMidanModule() : null;
            if (module != null) module.recordLateAjaxLoss(rungIndex);
        }
    }

    /** Ruled 2026-10-30: a one-time Hegemony reputation bonus for helping sink the Ajax. */
    protected float payAjaxRep() {
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        ExileHegLeagueConfig config = director != null ? director.getConfig() : null;
        float delta = config != null ? config.midanAjaxKillRep / 100f : 0.03f;
        if (delta <= 0f) return 0f;
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = delta;
        Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact, null, null, true),
                Factions.HEGEMONY);
        return delta;
    }

    // ------------------------------------------------------------------
    // Outcome -- to the director's Midan landing, not the ordinary op feed
    // ------------------------------------------------------------------

    /**
     * Reported once, from {@code notifyEnded()} (inherited), which every path reaches -- including
     * a losses abort that never touches the stage's {@code finish()}.
     */
    @Override
    protected void reportOutcome() {
        if (outcomeReported) return;
        outcomeReported = true;
        boolean landed = midanStage != null && midanStage.hasLanded();
        boolean playerPresent = midanStage != null && midanStage.wasPlayerPresent();
        Global.getLogger(ExileHegLeagueMidanStrikeIntel.class).info(
                "Exiles Midan STRIKE OUTCOME: rung " + (rungIndex + 1) + " " + targetMarketId
                        + " -- " + (midanStage != null ? midanStage.getOutcomeSummary() : "no stage")
                        + " | player present " + playerPresent + ", fought " + playerFought
                        + " | Ajax " + (ajaxLost ? "LOST" : "survived")
                        + " | escorts " + escorts
                        + " | distress call " + (distressSent ? "sent" : "never sent"));
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director == null) return;
        director.onMidanStrikeResolved(rungIndex, targetMarketId, landed, playerPresent,
                playerFought, ajaxLost);
    }

    protected static String format1(float v) {
        return String.valueOf(Math.round(v * 10f) / 10f);
    }

    // ------------------------------------------------------------------
    // Text -- ALL PLACEHOLDER (assistant-written; Sean's text pass replaces it)
    // ------------------------------------------------------------------

    @Override
    public String getName() {
        if (isSendingUpdate() && getListInfoParam() instanceof
                data.scripts.exiles.OmicronRaidIntel.BountyResult) {
            return targetName() + " - Bounty Paid";
        }
        if (isEnding() || isEnded()) return targetName() + " - Attack Over";
        return targetName() + " - Emergency Call";
    }

    /** The call's own bullets on first posting; every update keeps the parent's bullets. */
    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        if (getListInfoParam() != null) {
            super.addBulletPoints(info, mode);
            return;
        }
        Color tc = getBulletColorForMode(mode);
        Color h = Misc.getHighlightColor();
        float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;
        FactionAPI league = getFaction();
        bullet(info);
        info.addPara("Attackers flying %s colours", pad, tc, league.getBaseUIColor(),
                league.getDisplayName());
        if (bountyPerFrigate > 0f && !isEnding() && !isEnded()) {
            info.addPara("Hegemony bounty: %s per frigate", 0f, tc, h,
                    Misc.getDGSCredits(bountyPerFrigate));
        }
        float eta = getETA();
        if (eta > 1f && !isEnding() && !isEnded()) {
            info.addPara("Estimated %s days until they reach " + targetName(), 0f, tc, h,
                    "" + (int) Math.round(eta));
        }
        unindent(info);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        FactionAPI league = getFaction();
        FactionAPI heg = Global.getSector().getFaction(Factions.HEGEMONY);
        info.addImage(heg != null ? heg.getLogo() : getFactionForUIColors().getLogo(), width, 128, opad);

        info.addPara(targetName() + " is broadcasting an emergency call for assistance on all"
                + " channels. A strike force flying %s colours has appeared inside the "
                + getSystem().getNameWithLowercaseType() + " and is closing on " + targetName() + "."
                + " The League has not acknowledged any such operation.", opad,
                league.getBaseUIColor(), league.getDisplayName());

        if (!isEnding() && !isEnded()) {
            float eta = getETA();
            if (eta > 1f) {
                info.addPara("Estimated %s days until the attackers reach " + targetName() + ".", opad, h,
                        "" + (int) Math.round(eta));
            }
        }

        for (int i = 0; i < stages.size(); i++) {
            ((RaidStage) stages.get(i)).showStageInfo(info);
        }

        if (bountyPerFrigate > 0f && heg != null && !isEnding() && !isEnded()) {
            info.addSectionHeading("Bounty", heg.getBaseUIColor(), heg.getDarkUIColor(),
                    Alignment.MID, opad);
            info.addPara(Misc.ucFirst(heg.getDisplayNameWithArticle())
                    + " is paying a bounty for strike force warships destroyed while the attack is"
                    + " underway, scaled by hull size and your share of the fighting.", opad);
            info.addPara("%s base reward per frigate", opad, h,
                    Misc.getDGSCredits(bountyPerFrigate));
        }
    }
}
