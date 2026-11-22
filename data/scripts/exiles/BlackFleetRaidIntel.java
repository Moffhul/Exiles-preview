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
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.exiles.OmicronRaidIntel.BountyResult;

/**
 * Black Fleet raid intel, presented as a "System Bounty" (v0.8 polish, author 2026-07-11):
 * the board post reads like the vanilla event — the locals put a price on raiders' heads —
 * not a Black Fleet op forecast. Both tiers share the name; no "Faction: Black Fleet"
 * bullet; the op stays HIDDEN until the corsairs arrive in-system (or the player fights
 * them first), and the close-out says "has ended" for both outcomes. Fleet composition is
 * capped per tier (author ruling 2026-07-09): Raid fleets top out at DESTROYERS
 * (maxShipSize 2), Assault fleets at CRUISERS (maxShipSize 3; bump the config to 4 when
 * capitals should join later).
 *
 * {@link #createFleet} preserves vanilla's combat budget, adds tier-bounded dedicated
 * freighters after combat generation, and guarantees a tanker. Commerce omits transports.
 *
 * <p>BOUNTY/REP PORT (playtest-4, 2026-07-11): the Omicron per-kill bounty pattern
 * ({@link OmicronRaidIntel}), because vanilla pays NOTHING for raid defense — no bounty
 * (raids never post a SystemBountyIntel) and no rep unless the player literally reinforces a
 * defender fleet mid-battle. Registered as a {@link FleetEventListener} on each spawned
 * fleet: when the player destroys corsairs, the RAIDED faction ({@code targetFactionId},
 * fixed at launch — the assault victim's faction, or the biggest hostile market's for
 * commerce raids) pays credits per hull (SystemBountyIntel formula) and grants rep via the
 * vanilla {@code RepActions.SYSTEM_BOUNTY_REWARD} action. Payout notices are deferred a beat
 * so they land after the post-battle message flood. The -BlackFleet side of the ledger
 * happens automatically through the stock engagement reputation machinery. Intels restored
 * from pre-port saves have bountyPerFrigate 0 → every bounty path no-ops.
 */
public class BlackFleetRaidIntel extends RaidIntel implements FleetEventListener {

    /** Tag on spawned fleets (identification + the bounty listener's filter). */
    public static final String RAIDER_TAG = "exilebf_raider";

    /** See OmicronRaidIntel.BOUNTY_NOTICE_DELAY_DAYS — same post-battle-flood dodge. */
    protected static final float BOUNTY_NOTICE_DELAY_DAYS = 0.2f;

    /** Vanilla credits icon for payout notices (raw-path sprite, not settings-registered —
     *  load before first use; same pattern and reasoning as OmicronRaidIntel). */
    protected static final String PAYOUT_ICON = "graphics/icons/intel/gain_credits.png";
    protected static boolean payoutIconLoaded = false;

    protected boolean assault;
    protected boolean prosperityResolved;
    protected boolean prosperityDefeat;
    protected List prosperityLoot;
    protected ExileBlackFleetReturnStage prosperityReturn;

    public void markProsperityDefeat() { prosperityDefeat = true; }
    public void recordProsperityLoot(List loot) {
        if (prosperityLoot == null) prosperityLoot = new ArrayList();
        for (int i = 0; i < loot.size(); i++) {
            prosperityLoot.remove(loot.get(i));
            prosperityLoot.add(loot.get(i));
        }
        while (prosperityLoot.size() > ExileBlackFleetProsperity.get().config().lootCap) prosperityLoot.remove(0);
    }
    protected void failedAtStage(RaidStage stage) {
        super.failedAtStage(stage);
        if (prosperityDefeat) resolveProsperity(false);
    }
    public void resolveProsperity(boolean success) {
        if (!assault || prosperityResolved) return;
        prosperityResolved = true;
        if (success && prosperityLoot == null) return;
        ExileBlackFleetProsperity.get().outcome(true, success, prosperityLoot);
    }
    protected int maxShipSize;
    protected String targetFactionId;
    protected float bountyPerFrigate;
    protected BountyResult latestBounty;
    protected List pendingBounties; // BountyResult queued for the deferred payout notice
    protected float bountyNoticeDays = 0f;

    /** Ops launch hidden (v0.8 polish, author 2026-07-11: "don't post until the raid
     *  begins"); surfaced when the corsairs arrive in-system, or on first player battle
     *  contact. False on intels from pre-hidden-launch saves — adopted in advanceImpl. */
    protected boolean revealed = false;
    /** Set once the op is past its action stage and its fleets were told not to get sidetracked
     *  going home ({@link ExileReturnLeg}; bug sweep BF-1, 2026-10-06). Fewer losses on the way
     *  home also means more assault loot is delivered (ExileBlackFleetReturnStage). */
    protected boolean returnLegFlagged = false;

    public BlackFleetRaidIntel(StarSystemAPI target, FactionAPI faction, boolean assault,
                               int maxShipSize, String targetFactionId, float bountyPerFrigate) {
        super(target, faction, null);
        this.assault = assault;
        this.maxShipSize = maxShipSize;
        this.targetFactionId = targetFactionId;
        this.bountyPerFrigate = bountyPerFrigate;
        // Hidden launch: the op runs (stages, routes, fleets) but stays off the intel board
        // until the raid actually begins — including a pre-arrival collapse, which the
        // player then never hears about (the Omicron quiet-fail convention).
        setHidden(true);
    }

    /** Which tier this op is: assaults sack a colony, commerce raids strangle shipping. */
    public boolean isAssault() {
        return assault;
    }

    /** Self-report the outcome when the op closes. ASSAULT outcomes drive the extortion racket's
     *  cadence (spec #4: success = rackets embolden, failure = collectors lie low). Sector-memory
     *  Integer counters — loose coupling, XStream-safe.
     *
     *  Moved here from BlackFleetRaidManager.pruneEnded (review item 6, 2026-07-29). The manager
     *  used to keep activeRaids/activeAssaults lists purely so a periodic poll could notice ended
     *  ops — and that poll sat behind the raidsEnabled() gate, so a quest disabling raids would
     *  have stalled this signal and retained the ended intel. The op knows its own outcome; nothing
     *  needs to watch it. Fires once, immediately on close, rather than on the next cadence tick. */
    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        if (!assault) return;
        if (isFailed() && prosperityDefeat) resolveProsperity(false);
        if (!isSucceeded() && !isFailed()) return;
        // BF-6 (Sean, 2026-10-06): one canonical outcome. A sack that landed but whose RETURN
        // failed is a defeat for prosperity (above), so it is a SLOWDOWN here too -- the racket
        // speeds up only when the assault succeeded AND came home.
        String key = (isSucceeded() && !isFailed())
                ? ExileBlackFleetExtortionManager.SPEEDUP_KEY
                : ExileBlackFleetExtortionManager.SLOWDOWN_KEY;
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem =
                Global.getSector().getMemoryWithoutUpdate();
        Object curr = mem.get(key);
        int count = curr instanceof Integer ? ((Integer) curr).intValue() : 0;
        mem.set(key, Integer.valueOf(count + 1));
    }

    /** SYSTEM BOUNTY look (author 2026-07-11): the post reads like the vanilla event — the
     *  locals put a price on the raiders — not like a Black Fleet op forecast. Both tiers;
     *  the description and stage info still tell an attentive reader which tier it is. */
    @Override
    public String getName() {
        String base = "System Bounty";
        if (getSystem() != null) {
            base = base + " - " + getSystem().getBaseName();
        }
        if (isSendingUpdate() && getListInfoParam() instanceof BountyResult) {
            return base + " - Bounty Paid";
        }
        if (isEnding() || isEnded()) {
            return base + " - Over";
        }
        return base;
    }

    /** Put the op on the intel board (arrival passes the stock ENTERED_SYSTEM_UPDATE;
     *  battle-contact reveals pass null). */
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
                if (action != null && getCurrentStage() >= getStageIndex(action)) {
                    reveal(ENTERED_SYSTEM_UPDATE);
                }
            }
        }

        if (pendingBounties != null && !pendingBounties.isEmpty()) {
            bountyNoticeDays -= Misc.getDays(amount);
            if (bountyNoticeDays <= 0f) {
                flushPendingBounties();
            }
        }

        // RETURN LEG (ExileReturnLeg): the tick the op leaves its action stage.
        if (!returnLegFlagged && ExileReturnLeg.isPastActionStage(this)) {
            returnLegFlagged = true;
            ExileReturnLeg.flagLiveFleets(this, "Black Fleet",
                    getFaction() != null ? getFaction().getId() : null, targetFactionId);
        }
    }

    /** Hook each spawned fleet up to the bounty listener; a fleet spawning on its way home gets
     *  the return-leg flag too. */
    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        CampaignFleetAPI fleet = super.spawnFleet(route);
        if (fleet != null) {
            fleet.addEventListener(this);
            if (returnLegFlagged || ExileReturnLeg.isStraggler(route)) ExileReturnLeg.flag(fleet);
        }
        return fleet;
    }

    /** Credits icon on bounty-payout notices only; the faction crest everywhere else. */
    @Override
    public String getIcon() {
        if (isSendingUpdate() && getListInfoParam() instanceof BountyResult) {
            if (!payoutIconLoaded) {
                try {
                    Global.getSettings().loadTexture(PAYOUT_ICON);
                    payoutIconLoaded = true;
                } catch (Exception e) {
                    Global.getLogger(BlackFleetRaidIntel.class).warn(
                            "Could not load payout icon " + PAYOUT_ICON + " — using crest", e);
                    return super.getIcon();
                }
            }
            return PAYOUT_ICON;
        }
        return super.getIcon();
    }

    /** Surface under BOUNTIES too — the raided faction literally pays one (Omicron
     *  convention; the map icon sits on the TARGET system, and with the source undisclosed
     *  no arrow points home to the anchors). */
    @Override
    public java.util.Set<String> getIntelTags(SectorMapAPI map) {
        java.util.Set<String> tags = super.getIntelTags(map);
        if (bountyPerFrigate > 0f) {
            tags.add(Tags.INTEL_BOUNTY);
        }
        return tags;
    }

    /** Vanilla combat budget plus separate civilian freight and a guaranteed fuel ship. */
    @Override
    public CampaignFleetAPI createFleet(String factionId, RouteData route, MarketAPI market,
                                        Vector2f locInHyper, Random random) {
        if (random == null) random = new Random();

        OptionalFleetData extra = route.getExtra();

        float combat = extra.fp;
        float tanker = extra.fp * (0.1f + random.nextFloat() * 0.05f);
        float transport = extra.fp * (0.1f + random.nextFloat() * 0.05f);
        combat -= tanker;
        combat -= transport;
        // Preserve both original support deductions even when commerce replaces transport.
        ExileBlackFleetLogistics logistics = ExileBlackFleetLogistics.load();
        float freightRequest = transport;
        if (!assault) transport = 0f;

        FleetParamsV3 params = new FleetParamsV3(
                market,
                locInHyper,
                factionId,
                route == null ? null : route.getQualityOverride(),
                extra.fleetType,
                combat,
                0f, // append dedicated freighters after vanilla combat generation/pruning
                tanker,
                transport,
                0f,
                0f,
                0f
                );
        if (route != null) {
            params.timestamp = route.getTimestamp();
        }
        params.random = random;

        // The one addition: per-tier hull-size cap (2 = destroyers, 3 = cruisers, 4 = capitals).
        if (maxShipSize > 0) {
            params.maxShipSize = maxShipSize;
        }

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) return null;

        if (assault) freightRequest = extra.fp * (logistics.assaultMinShare
                + random.nextFloat() * (logistics.assaultMaxShare - logistics.assaultMinShare));
        float freighter = logistics.freight(extra.fp, freightRequest);
        fleet.getMemoryWithoutUpdate().set(ExileBlackFleetLogistics.COMBAT_FP,
                Float.valueOf(ExileBlackFleetLogistics.combatFP(fleet)));
        ExileBlackFleetLogistics.addSupport(fleet, params, random, freighter, 0f);
        ExileBlackFleetLogistics.log(fleet, assault ? "assault" : "commerce", extra.fp,
                combat, freighter, tanker, transport);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);
        fleet.addTag(RAIDER_TAG);

        if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        }

        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
        fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);

        return fleet;
    }

    @Override
    public float getRaidFP() {
        com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage stage = getAssembleStage();
        float strength = ExileBlackFleetLogistics.strength(stage.getRoutes(), new ArrayList());
        if (stage.getSpawnFP() > 0f) strength = Math.max(stage.getOrigSpawnFP(), strength);
        return strength;
    }

    // --- FleetEventListener: the raided faction pays for dead corsairs (per-fleet, so a
    // battle involving several of this op's fleets pays each fleet's losses exactly once) ---

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE) markProsperityDefeat();
        if (prosperityReturn != null) prosperityReturn.despawned(fleet, reason);
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (fleet != null && !Misc.getSnapshotMembersLost(fleet).isEmpty()) markProsperityDefeat();
        // Pay through the ENDING window too (only stop once fully ended): when an op collapses
        // early, its surviving fleets are still in-system for days — mop-up kills earn the
        // bounty like any other (Omicron playtest 2026-07-03 lesson).
        if (isEnded()) return;
        if (fleet == null || !fleet.hasTag(RAIDER_TAG)) return;
        if (battle == null || !battle.isPlayerInvolved()) return;

        // Player contact with a still-hidden op reveals it — fighting the corsairs IS
        // finding out about the operation (also lets the payout notice below actually show).
        if (!revealed && !isEnding()) {
            reveal(null);
        }

        if (bountyPerFrigate <= 0f) return;

        FactionAPI paying = targetFactionId != null
                ? Global.getSector().getFaction(targetFactionId) : null;
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

        // DEFERRED payout notice (Omicron pattern): credits are in hand immediately; the
        // popup waits a beat so it lands after level-up/promotion messages.
        if (pendingBounties == null) pendingBounties = new ArrayList();
        pendingBounties.add(new BountyResult(payment, battle.getPlayerInvolvementFraction(), rep));
        if (pendingBounties.size() == 1) {
            bountyNoticeDays = BOUNTY_NOTICE_DELAY_DAYS;
        }

        Global.getLogger(BlackFleetRaidIntel.class).info(
                "Black Fleet corsair bounty: " + payment + "c from " + targetFactionId
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
        Global.getLogger(BlackFleetRaidIntel.class).info(
                "Black Fleet BOUNTY NOTICE posted: " + combined.payment + "c"
                        + (combined.rep != null ? " | rep " + Math.round(combined.rep.delta * 100f) : "")
                        + " | intel " + (isHidden() ? "HIDDEN (no popup)" : "visible")
                        + (getPlayerVisibleTimestamp() == null ? ", never posted (no popup)" : "")
                        + (isEnding() || isEnded() ? ", ending" : ""));
    }

    /** Bounty updates render their own bullets (credits + rep, the vanilla way). Everything
     *  else is a faithful copy of the vanilla RaidIntel bullets (Janino raw-List style) with
     *  the SYSTEM-BOUNTY rewrites (author 2026-07-11): the "Faction: Black Fleet" line is
     *  DROPPED (the target-faction line stays), and the final update reads "has ended" for
     *  BOTH outcomes instead of announcing success or failure. Plus, while the op runs, the
     *  payout-terms rows that vanilla system-bounty list rows show. */
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

        Color h = Misc.getHighlightColor();
        float pad = 3f;
        float opad = 10f;
        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) initPad = opad;
        Color tc = getBulletColorForMode(mode);
        bullet(info);
        boolean isUpdate = getListInfoParam() != null;
        float eta = getETA();
        FactionAPI fac = getFaction();
        // (vanilla's "Faction: <raiding faction>" para deliberately omitted here)
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
            info.addPara("Target: " + other.getDisplayName(), initPad, tc,
                    other.getBaseUIColor(), other.getDisplayName());
            initPad = 0f;
        }
        if (isUpdate) {
            if (getListInfoParam() == ENTERED_SYSTEM_UPDATE) {
                info.addPara("Hostile forces have arrived in-system", tc, initPad);
            } else {
                // Outcome-neutral close-out (author 2026-07-11): no success/failure verdict.
                info.addPara("The raid on the " + getSystem().getNameWithLowercaseType()
                        + " has ended", tc, initPad);
            }
        } else {
            info.addPara(getSystem().getNameWithLowercaseType(), tc, initPad);
        }
        initPad = 0f;
        if (eta > 1 && !isEnding() && getListInfoParam() != ENTERED_SYSTEM_UPDATE) {
            String days = getDaysString(eta);
            info.addPara("Estimated %s " + days + " until arrival",
                    initPad, tc, h, "" + (int) Math.round(eta));
        }
        unindent(info);

        // Intel-LIST rows: the payout terms + time window like vanilla system-bounty rows.
        if (bountyPerFrigate > 0f && !isEnding()) {
            Color tc2 = getBulletColorForMode(mode);
            bullet(info);
            info.addPara("%s base reward per frigate", 0f, tc2, h, Misc.getDGSCredits(bountyPerFrigate));
            ActionStage action = getActionStage();
            if (action != null && getCurrentStage() == getStageIndex(action) && action.getMaxDays() > 1f) {
                int left = (int) Math.ceil(Math.max(0f, action.getMaxDays() - action.getElapsed()));
                info.addPara("up to %s days remaining", 0f, tc2, h, "" + left);
            }
            unindent(info);
        }
    }

    /** Stock raid description + the bounty terms — the data vanilla system-bounty intel
     *  shows that players rely on. */
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        super.createSmallDescription(info, width, height);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        FactionAPI paying = targetFactionId != null
                ? Global.getSector().getFaction(targetFactionId) : null;
        if (bountyPerFrigate > 0f && paying != null && !isEnding()) {
            info.addSectionHeading("Bounty", paying.getBaseUIColor(), paying.getDarkUIColor(),
                    Alignment.MID, opad);
            info.addPara(Misc.ucFirst(paying.getDisplayNameWithArticle())
                    + " is paying a bounty for Black Fleet corsairs destroyed while this"
                    + " operation is underway, scaled by hull size and your share of the"
                    + " fighting.", opad);
            info.addPara("%s base reward per frigate", opad, h,
                    Misc.getDGSCredits(bountyPerFrigate));
        }
    }
}
