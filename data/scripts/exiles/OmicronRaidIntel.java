package data.scripts.exiles;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * One Omicron operation (raid or incursion) as a vanilla staged {@link RaidIntel}:
 * assemble at the node's Nexus (marketless source, see {@link OmicronAssembleStage}) →
 * travel → act in the target system ({@link OmicronRaidActionStage}: survival-gated colony
 * disruption) → return. Route-managed, so fleets cost nothing when the player is far away
 * and the whole lifecycle (including the intel entry, ETA, and failure states) is vanilla.
 *
 * <p>Also the per-kill bounty/rep hook: registered as a {@link FleetEventListener} on each
 * spawned fleet; when the player destroys raiders, the RAIDED faction pays credits per hull
 * (SystemBountyIntel pattern) and grants rep via the vanilla
 * {@code RepActions.SYSTEM_BOUNTY_REWARD} action. The -Omicron side of the ledger happens
 * automatically through the stock engagement reputation machinery.
 */
public class OmicronRaidIntel extends RaidIntel implements FleetEventListener {

    /** In-game days between a player battle and the bounty-paid notice, so the payout popup
     *  lands AFTER the post-battle message flood (level-ups, promotions) instead of inside it. */
    protected static final float BOUNTY_NOTICE_DELAY_DAYS = 0.2f;

    /** Payout notices get the vanilla credits icon instead of the Omicron crest, so they read
     *  differently from launch/status popups at a glance (playtest 2026-07-05: in the heat of
     *  the moment the popups were confusable). Vanilla only references this sprite by raw path
     *  (reports.csv), so it is NOT settings.json-registered — load it before first use. */
    protected static final String PAYOUT_ICON = "graphics/icons/intel/gain_credits.png";
    protected static boolean payoutIconLoaded = false;

    protected String nodeId;
    protected boolean incursion;
    protected String targetFactionId;
    protected long launchTs;
    protected float bountyPerFrigate;
    protected BountyResult latestBounty;

    /** Ops launch hidden; surfaced when the raiders arrive (or noticeDays out, or on first
     *  player contact). False on intels from pre-hidden-launch saves — adopted in advanceImpl. */
    protected boolean revealed = false;
    /** Set once the op is past its action stage and its fleets were told not to get sidetracked
     *  going home ({@link ExileReturnLeg}; bug sweep OMI-1, 2026-10-06). Return leg only: the
     *  ruled cleared-targets signal in the action stage's updateRoutes() is untouched. */
    protected boolean returnLegFlagged = false;
    /** Days of advance warning before the op surfaces. 0 = only when the raiders arrive. */
    protected float noticeDays = 0f;
    protected List damagedColonies;  // String market names where industries were knocked out
    protected List pendingBounties;  // BountyResult queued for the deferred payout notice
    protected float bountyNoticeDays = 0f;

    /** One bounty payout, kept so the intel-update notification can render credits + rep delta
     *  the vanilla way (SystemBountyIntel pattern). Named static class — XStream-safe. */
    public static class BountyResult {
        public int payment;
        public float fraction;
        public ReputationAdjustmentResult rep;

        public BountyResult(int payment, float fraction, ReputationAdjustmentResult rep) {
            this.payment = payment;
            this.fraction = fraction;
            this.rep = rep;
        }
    }

    public OmicronRaidIntel(StarSystemAPI target, FactionAPI omicron, String nodeId,
                            boolean incursion, String targetFactionId, float bountyPerFrigate) {
        super(target, omicron, null);
        this.nodeId = nodeId;
        this.incursion = incursion;
        this.targetFactionId = targetFactionId;
        this.launchTs = Global.getSector().getClock().getTimestamp();
        this.bountyPerFrigate = bountyPerFrigate;
        // Hidden launch: the op runs (stages, routes, fleets) but stays off the intel board
        // and sends no updates until reveal — including a pre-arrival collapse, which the
        // player then never hears about (they never knew the op existed).
        setHidden(true);
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        float duration = launchTs == 0L ? -1f
                : Global.getSector().getClock().getElapsedDaysSince(launchTs);
        Global.getLogger(OmicronRaidIntel.class).info(
                "Omicron " + (incursion ? "INCURSION" : "raid") + " op ENDED: node="
                        + nodeId + ", durationDays=" + duration + ", success=" + isSucceeded()
                        + ", failed=" + isFailed());
    }

    public void setNoticeDays(float noticeDays) {
        this.noticeDays = noticeDays;
    }

    /** Put the op on the intel board. Arrival reveals pass the stock ENTERED_SYSTEM_UPDATE
     *  ("Arrived in-system"); early-warning reveals pass null for the standard posting with
     *  the ETA line. */
    protected void reveal(Object updateParam) {
        revealed = true;
        setHidden(false);
        if (getPlayerVisibleTimestamp() == null) {
            setPlayerVisibleTimestamp(Long.valueOf(Global.getSector().getClock().getTimestamp()));
        }
        sendUpdateIfPlayerHasIntel(updateParam, false);
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        if (!revealed) {
            if (!isHidden()) {
                // Intel from a pre-hidden-launch save: it was added visible; adopt it silently.
                revealed = true;
            } else if (!isEnding() && !isEnded()) {
                ActionStage action = getActionStage();
                boolean arrived = action != null && getCurrentStage() >= getStageIndex(action);
                if (arrived) {
                    // Quiet-fail backstop: announce the arrival only if the op committed to
                    // attacking something. An op that arrives outmatched picks zero colonies
                    // (updateRoutes runs synchronously at stage start) and aborts on the next
                    // status tick — that one dies still-hidden, so the player just sees
                    // "fewer raids" instead of announce-then-instant-fail noise.
                    boolean committed = true;
                    if (action instanceof OmicronRaidActionStage) {
                        committed = ((OmicronRaidActionStage) action).hasChosenTargets();
                    }
                    if (committed) {
                        reveal(ENTERED_SYSTEM_UPDATE);
                    }
                } else if (noticeDays > 0f && getETA() <= noticeDays) {
                    reveal(null);
                }
            }
        }

        if (pendingBounties != null && !pendingBounties.isEmpty()) {
            bountyNoticeDays -= Misc.getDays(amount);
            if (bountyNoticeDays <= 0f) {
                flushPendingBounties();
            }
        }

        // RETURN LEG (ExileReturnLeg): the tick the op leaves its action stage (including the
        // quiet-fail abort of an outmatched op, whose fleets then head home unflagged otherwise).
        if (!returnLegFlagged && ExileReturnLeg.isPastActionStage(this)) {
            returnLegFlagged = true;
            ExileReturnLeg.flagLiveFleets(this, "Omicron",
                    getFaction() != null ? getFaction().getId() : null, targetFactionId);
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public boolean isIncursion() {
        return incursion;
    }

    public String getName() {
        String base = incursion ? "Omicron Incursion" : "Omicron Raid";
        // Board rows show the target system like vanilla raids do (playtest 2026-07-05:
        // without it you had to open each op to see where it was headed).
        if (getSystem() != null) {
            base = base + " - " + getSystem().getBaseName();
        }
        // Payout notifications get their own title so they read differently from launch /
        // status updates at a glance (playtest feedback: the popups looked too similar).
        if (isSendingUpdate() && getListInfoParam() instanceof BountyResult) {
            return base + " - Bounty Paid";
        }
        if (isEnding()) {
            // Failed shows on the BOARD row too, not just the popup title (vanilla only
            // shows it while sending the update, leaving failed raids as an ambiguous "Over").
            if (isFailed()) return base + " - Failed";
            if (isSucceeded()) return base + " - Successful";
            return base + " - Over";
        }
        return base;
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        CampaignFleetAPI fleet = super.spawnFleet(route);
        if (fleet != null) {
            // The campaign tooltip automatically prepends the faction display name.
            // Including "Omicron" here renders as "Omicron Omicron Raiders".
            fleet.setName(incursion ? "Incursion Force" : "Raiders");
            fleet.addEventListener(this);
            if (returnLegFlagged || ExileReturnLeg.isStraggler(route)) ExileReturnLeg.flag(fleet);
            // Vanilla RaidIntel.spawnFleet has a setLocation(x, x) typo; place correctly at
            // the source (the Nexus). The route AI repositions by elapsed route time anyway.
            MarketAPI market = route.getMarket();
            if (market != null && market.getPrimaryEntity() != null) {
                fleet.setLocation(market.getPrimaryEntity().getLocation().x,
                        market.getPrimaryEntity().getLocation().y);
            }
        }
        return fleet;
    }

    /** Credits icon on bounty-payout notices only; the Omicron crest everywhere else. */
    @Override
    public String getIcon() {
        if (isSendingUpdate() && getListInfoParam() instanceof BountyResult) {
            if (!payoutIconLoaded) {
                try {
                    Global.getSettings().loadTexture(PAYOUT_ICON);
                    payoutIconLoaded = true;
                } catch (Exception e) {
                    Global.getLogger(OmicronRaidIntel.class).warn(
                            "Could not load payout icon " + PAYOUT_ICON + " — using crest", e);
                    return super.getIcon();
                }
            }
            return PAYOUT_ICON;
        }
        return super.getIcon();
    }

    /** Surface on the intel board under BOUNTIES too — the raided faction literally pays one
     *  (author preference: the raided system shows up like a system bounty; the map icon sits
     *  on the TARGET system and, with the source unknown, no arrow points home). */
    @Override
    public java.util.Set<String> getIntelTags(SectorMapAPI map) {
        java.util.Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_BOUNTY);
        return tags;
    }

    /**
     * Machine fleets: all-combat composition (no tankers/transports), quality pinned to 1.0
     * (the bare source market carries no real economy stats to derive it from). Keeps the
     * vanilla WAR_FLEET + RAIDER memory flags — RAIDER is what makes RaidAssignmentAI
     * actually execute ground raids via the action stage's performRaid.
     */
    @Override
    public CampaignFleetAPI createFleet(String factionId, RouteData route, MarketAPI market,
                                        Vector2f locInHyper, Random random) {
        if (random == null) random = new Random();

        OptionalFleetData extra = route.getExtra();

        FleetParamsV3 params = new FleetParamsV3(
                market,
                locInHyper,
                factionId,
                1f,                 // quality override
                extra.fleetType,
                extra.fp,           // all combat
                0f, 0f, 0f, 0f, 0f, // freighter / tanker / transport / liner / utility
                0f                  // quality mod
        );
        // Belt-and-suspenders vs the bare-source-market zero-fleet-size bug: the source market
        // also pins COMBAT_FLEET_SIZE_MULT to 1, but never let a degenerate market stat zero
        // the fleet again (FleetFactoryV3 multiplies fleet size by it unless told not to).
        params.ignoreMarketFleetSizeMult = Boolean.TRUE;
        params.timestamp = route.getTimestamp();
        params.random = random;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) {
            // A null here makes RouteManager EXPIRE the route -> silent op failure. Log loudly.
            Global.getLogger(OmicronRaidIntel.class).warn(
                    "Omicron fleet creation FAILED (type=" + extra.fleetType + ", fp=" + extra.fp
                            + ", faction=" + factionId + ") — route will expire.");
            return null;
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);
        fleet.addTag(OmicronDirector.RAIDER_TAG);

        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
        fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);

        return fleet;
    }

    // --- FleetEventListener: the defended faction pays for dead raiders (per-fleet, so a
    // battle involving several of this op's fleets pays each fleet's losses exactly once) ---

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        // Pay through the ENDING window too (only stop once fully ended): when an op collapses
        // early, its surviving fleets are still in-system for days — mop-up kills earn the
        // bounty like any other (playtest 2026-07-03: a post-failure battle paid nothing).
        if (isEnded()) return;
        if (fleet == null || !fleet.hasTag(OmicronDirector.RAIDER_TAG)) return;
        if (battle == null || !battle.isPlayerInvolved()) return;

        // Player contact with a still-undetected op reveals it — fighting the raiders IS
        // finding out about the operation (also lets the payout notice below actually show).
        if (!revealed && !isEnding()) {
            reveal(null);
        }

        if (bountyPerFrigate <= 0f) return;

        FactionAPI paying = Global.getSector().getFaction(targetFactionId);
        if (paying == null) return;

        float bounty = 0f;
        float fpDestroyed = 0f;
        List lost = Misc.getSnapshotMembersLost(fleet);
        for (int i = 0; i < lost.size(); i++) {
            FleetMemberAPI loss = (FleetMemberAPI) lost.get(i);
            bounty += Misc.getSizeNum(loss.getHullSpec().getHullSize()) * bountyPerFrigate;
            fpDestroyed += loss.getFleetPointCost();
        }

        int payment = (int) (bounty * battle.getPlayerInvolvementFraction());
        if (payment <= 0) return;

        Global.getSector().getPlayerFleet().getCargo().getCredits().add(payment);

        float repFP = (int) (fpDestroyed * battle.getPlayerInvolvementFraction());
        ReputationAdjustmentResult rep = Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.SYSTEM_BOUNTY_REWARD, new Float(repFP),
                        null, null, true, false),
                targetFactionId);

        // Vanilla presentation (SystemBountyIntel): an intel update whose bullets show the
        // credits received, damage-dealt share, and the standard reputation-delta line.
        // DEFERRED, not sent here: queued and flushed from advanceImpl a beat later so the
        // notice lands after the post-battle message flood (playtest 2026-07-04: a payout
        // notice drowned under several level-up + promotion messages). Credits above are
        // already in hand; only the popup waits.
        if (pendingBounties == null) pendingBounties = new ArrayList();
        pendingBounties.add(new BountyResult(payment, battle.getPlayerInvolvementFraction(), rep));
        if (pendingBounties.size() == 1) {
            bountyNoticeDays = BOUNTY_NOTICE_DELAY_DAYS;
        }

        Global.getLogger(OmicronRaidIntel.class).info(
                "Omicron raider bounty: " + payment + "c from " + targetFactionId
                        + " (fpDestroyed=" + fpDestroyed + ", involvement="
                        + battle.getPlayerInvolvementFraction() + ")");

        // BUG-2026-10-30-A: the delayed notice is flushed from advanceImpl(), which vanilla stops
        // calling once the intel is ENDING -- and this op pays through ENDING on purpose. Once it
        // is ending there is nothing left to aggregate: announce now.
        if (isEnding() || isEnded()) {
            flushPendingBounties();
        }
    }

    /** Send the queued payout notice(s) as one update. Multiple battles inside the delay
     *  window aggregate into a single popup (summed credits + rep; no single damage-dealt
     *  share applies across battles, so that line is suppressed). */
    protected void flushPendingBounties() {
        if (pendingBounties == null || pendingBounties.isEmpty()) return;
        BountyResult combined;
        if (pendingBounties.size() == 1) {
            combined = (BountyResult) pendingBounties.get(0);
        } else {
            int payment = 0;
            float repDelta = 0f;
            for (int i = 0; i < pendingBounties.size(); i++) {
                BountyResult r = (BountyResult) pendingBounties.get(i);
                payment += r.payment;
                if (r.rep != null) repDelta += r.rep.delta;
            }
            combined = new BountyResult(payment, 1f, new ReputationAdjustmentResult(repDelta));
        }
        pendingBounties.clear();
        latestBounty = combined;
        sendUpdateIfPlayerHasIntel(combined, false);
        // Instrumented 2026-10-31 (BUG-2026-10-30-A): a missing popup vs a missing post.
        Global.getLogger(OmicronRaidIntel.class).info(
                "Omicron BOUNTY NOTICE posted: " + combined.payment + "c"
                        + (combined.rep != null ? " | rep " + Math.round(combined.rep.delta * 100f) : "")
                        + " | intel " + (isHidden() ? "HIDDEN (no popup)" : "visible")
                        + (getPlayerVisibleTimestamp() == null ? ", never posted (no popup)" : "")
                        + (isEnding() || isEnded() ? ", ending" : ""));
    }

    /** Called by the action stage when a raid actually knocks industries out at a colony —
     *  feeds the plain "X suffered damage" line in the description. */
    public void addDamagedColony(String name) {
        if (name == null) return;
        if (damagedColonies == null) damagedColonies = new ArrayList();
        if (!damagedColonies.contains(name)) damagedColonies.add(name);
    }

    /** Bounty updates render their own bullets (credits + rep, the vanilla way); everything
     *  else falls through to the stock raid bullets. */
    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        if (getListInfoParam() instanceof BountyResult) {
            BountyResult r = (BountyResult) getListInfoParam();
            Color tc = getBulletColorForMode(mode);
            Color h = Misc.getHighlightColor();
            bullet(info);
            info.addPara("%s received", 3f, tc, h, Misc.getDGSCredits(r.payment));
            if (Math.round(r.fraction * 100f) < 100f) {
                info.addPara("%s share based on damage dealt", 0f, tc, h,
                        "" + (int) Math.round(r.fraction * 100f) + "%");
            }
            FactionAPI paying = Global.getSector().getFaction(targetFactionId);
            if (paying != null && r.rep != null) {
                CoreReputationPlugin.addAdjustmentMessage(r.rep.delta, paying, null,
                        null, null, info, tc, true, 0f);
            }
            unindent(info);
            return;
        }

        // Faithful copy of RaidIntel.addBulletPoints (Janino raw-List style) with ONE reword:
        // the final success bullet says colonies were "bombarded", not "raided" (author
        // 2026-07-05 — "raided" survives only in the op name). Failure/arrival lines are
        // vanilla verbatim.
        Color h2 = Misc.getHighlightColor();
        float pad = 3f;
        float opad2 = 10f;
        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) initPad = opad2;
        Color tc2 = getBulletColorForMode(mode);
        bullet(info);
        boolean isUpdate = getListInfoParam() != null;
        float eta = getETA();
        FactionAPI fac = getFaction();
        info.addPara("Faction: " + fac.getDisplayName(), initPad, tc2,
                fac.getBaseUIColor(), fac.getDisplayName());
        initPad = 0f;
        int max = 0;
        MarketAPI biggest = null;
        List inLoc = Misc.getMarketsInLocation(getSystem());
        for (int i = 0; i < inLoc.size(); i++) {
            MarketAPI other = (MarketAPI) inLoc.get(i);
            if (!other.getFaction().isHostileTo(fac)) continue;
            int size = other.getSize();
            if (size > max || (size == max && other.getFaction().isPlayerFaction())) {
                max = size;
                biggest = other;
            }
        }
        if (biggest != null) {
            FactionAPI other = biggest.getFaction();
            info.addPara("Target: " + other.getDisplayName(), initPad, tc2,
                    other.getBaseUIColor(), other.getDisplayName());
        }
        if (isUpdate) {
            if (getListInfoParam() == ENTERED_SYSTEM_UPDATE) {
                info.addPara("Arrived in-system", tc2, initPad);
            } else if (!isFailed()) {
                info.addPara("Colonies in the " + getSystem().getNameWithLowercaseType()
                        + " have been bombarded", tc2, initPad);
            } else {
                info.addPara("The raid on the " + getSystem().getNameWithLowercaseType()
                        + " has failed", tc2, initPad);
            }
        } else {
            info.addPara(getSystem().getNameWithLowercaseType(), tc2, initPad);
        }
        initPad = 0f;
        if (eta > 1 && !isFailed() && getListInfoParam() != ENTERED_SYSTEM_UPDATE) {
            String days = getDaysString(eta);
            info.addPara("Estimated %s " + days + " until arrival",
                    initPad, tc2, h2, "" + (int) Math.round(eta));
        }
        unindent(info);

        // Intel-LIST rows: show the payout terms + time window like vanilla system-bounty rows
        // do (playtest feedback: the description had them, the list didn't).
        if (bountyPerFrigate > 0f && !isEnding()) {
            Color tc = getBulletColorForMode(mode);
            Color h = Misc.getHighlightColor();
            bullet(info);
            info.addPara("%s base reward per frigate", 0f, tc, h, Misc.getDGSCredits(bountyPerFrigate));
            ActionStage action = getActionStage();
            if (action != null && getCurrentStage() == getStageIndex(action) && action.getMaxDays() > 1f) {
                int left = (int) Math.ceil(Math.max(0f, action.getMaxDays() - action.getElapsed()));
                info.addPara("up to %s days remaining", 0f, tc, h, "" + left);
            }
            unindent(info);
        }
    }

    /** Stock raid description + the bounty terms and (during the action stage) days remaining —
     *  the data the vanilla system-bounty intel shows that players rely on. */
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        super.createSmallDescription(info, width, height);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        // Plain colony-damage summary (names only, no industry list) — rendered directly
        // under the vanilla Status block (bottom of the middle section, per author), and
        // worded as bombardment: Omicron doesn't land and grab things. Shown from first
        // damage onward, INCLUDING failed ops, which the vanilla status section omits
        // (a raid that trashed a colony and then collapsed would otherwise read as harmless).
        if (damagedColonies != null && !damagedColonies.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < damagedColonies.size(); i++) {
                if (i > 0) names.append(", ");
                names.append((String) damagedColonies.get(i));
            }
            String verb = damagedColonies.size() == 1 ? "has" : "have";
            info.addPara("%s " + verb + " been bombarded during this operation.", opad,
                    Misc.getNegativeHighlightColor(), names.toString());
        }

        FactionAPI paying = Global.getSector().getFaction(targetFactionId);
        if (bountyPerFrigate > 0f && paying != null && !isEnding()) {
            info.addSectionHeading("Bounty", paying.getBaseUIColor(), paying.getDarkUIColor(),
                    Alignment.MID, opad);
            info.addPara(Misc.ucFirst(paying.getDisplayNameWithArticle())
                    + " is paying a bounty for Omicron raiders destroyed while this operation"
                    + " is underway, scaled by hull size and your share of the fighting.", opad);
            info.addPara("%s base reward per frigate", opad, h,
                    Misc.getDGSCredits(bountyPerFrigate));
        }

        ActionStage action = getActionStage();
        if (action != null && !isEnding()
                && getCurrentStage() == getStageIndex(action) && action.getMaxDays() > 1f) {
            int left = (int) Math.ceil(Math.max(0f, action.getMaxDays() - action.getElapsed()));
            info.addPara("Omicron forces are expected to remain in-system for up to %s more days.",
                    opad, h, "" + left);
        }
    }
}
