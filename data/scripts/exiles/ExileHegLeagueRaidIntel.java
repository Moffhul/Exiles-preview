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
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.StarSystemAPI;
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
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.exiles.OmicronRaidIntel.BountyResult;

/**
 * Hegemony-Persean League operation intel -- PASS 2 of docs/EXILES_HEG_LEAGUE_WAR.md sec 4.7.
 * A port of {@link AshenRaidIntel} (itself a Black Fleet port, itself an Omicron port); the
 * pipeline is identical and only the payer and the flavour change.
 *
 * <p><b>Why it exists: vanilla pays nothing for raid defense.</b> Raids never post a
 * {@code SystemBountyIntel} and grant no reputation unless the player literally reinforces a
 * defender fleet mid-battle. This is the heroism vector (pillar 4).
 *
 * <p><b>Here the bounty cuts both ways</b> (sec 4.7). Whichever side is being raided posts the
 * bounty, so the player can profitably pick a side per-cycle -- or play both across cycles, which
 * is exactly the mercenary shape the war wants. Player kills also feed war weariness -- credited to
 * whichever side owned the ships that died, so the choice of side matters and not just the choice
 * of target (Sean, 2026-08-27; see {@link #applyPlayerInflictedWeariness}), making that mercenary
 * work a real, quiet input to the tension meter.
 *
 * <p><b>Source concealment is deliberately NOT ported</b> (sec 3.4). Ashen hides Providence because
 * its location is recon the player should earn; Hegemony and League home systems are public
 * knowledge, so no {@code isSourceKnown()} / {@code getMapLocation()} overrides appear here.
 * The op is still hidden until the raiders ARRIVE -- the player should discover an interdiction by
 * flying into it or by reading the market screen, not by watching an op forecast.
 *
 * <p>{@link #createFleet} is the vanilla RaidIntel body plus two lines: the per-op
 * {@code maxShipSize} cap, which {@link FleetParamsV3} consumes natively and which is the
 * ratified ship-farming guardrail (S6 / sec 5 -- destroyers, cap 2), and the raider tag the bounty
 * listener filters on. <b>No XIV sweep</b> (R3): the destroyer cap already removes five of the six
 * XIV hulls, and a rare Enforcer XIV is accepted.
 */
public class ExileHegLeagueRaidIntel extends RaidIntel implements FleetEventListener {

    /** Tag on spawned fleets (identification + the bounty listener's filter). */
    public static final String RAIDER_TAG = "exile_hl_raider";

    /** See OmicronRaidIntel.BOUNTY_NOTICE_DELAY_DAYS -- same post-battle-flood dodge. */
    protected static final float BOUNTY_NOTICE_DELAY_DAYS = 0.2f;

    /** Vanilla credits icon for payout notices (raw-path sprite, not settings-registered --
     *  load before first use; the sibling pattern). */
    protected static final String PAYOUT_ICON = "graphics/icons/intel/gain_credits.png";
    protected static boolean payoutIconLoaded = false;

    /** The side running this operation. */
    protected String actorFactionId;
    /** The side being raided -- and, per sec 4.7, the side that pays the bounty. */
    protected String victimFactionId;
    protected int maxShipSize;
    protected float bountyPerFrigate;
    /** War weariness added to a war faction per fleet point of ITS OWN ships the player destroyed,
     *  whichever side that faction was on in the battle. 0 disables.
     *  See {@link #applyPlayerInflictedWeariness}. */
    protected float wearinessPerFpDestroyed;
    /** Days this op's target system stays off the menu AFTER the op ends. 0 disables. */
    protected float systemCooldownDays;

    protected BountyResult latestBounty;
    protected List pendingBounties; // BountyResult queued for the deferred payout notice
    protected float bountyNoticeDays = 0f;

    /** Seed of the last battle already credited to the weariness ledgers, or null.
     *  See {@link #applyPlayerInflictedWeariness} -- the one-battle-one-credit guard. Stored as a
     *  Long rather than the BattleAPI itself so nothing battle-scoped enters saved intel state. */
    protected Long wearinessBattleSeed = null;

    /** VICTORY-BONUS LEDGER (sec 14.15). FP this op has lost in ALL battles, and the part of it
     *  credited to the player. Saved primitives; a pre-2026-09-28 save restores 0 for both, which
     *  pays nothing (totalLostFp 0 means no share). Kept for every op type because the listener is
     *  shared; only the squeeze reads it. */
    protected float victoryTotalLostFp = 0f;
    protected float victoryPlayerFp = 0f;
    /** Set by {@link #failedAtStage} when the failure was vanilla's LOSSES abort -- the only
     *  failure that counts as the op being "broken". */
    protected boolean lastFailureWasLossesAbort = false;

    /** Ops launch hidden; surfaced when the raiders arrive in-system, or on first player battle
     *  contact. */
    protected boolean revealed = false;
    /** Guards the one-shot completion report to the tension meter. */
    protected boolean outcomeReported = false;
    /**
     * RETURN-LEG FIX (Sean, 2026-10-05): set once the op is past its action stage (completed or
     * aborted) and its live fleets have been told not to get sidetracked on the way home. Vanilla's
     * raid {@code ReturnStage} only re-routes the fleets, so they kept engaging targets of
     * opportunity, joining nearby battles and -- with the presence-stage station suppression gone
     * -- attacking stations days after the op had ended (Sean's Run-5 Galatia notes). Vanilla's
     * newer {@code FleetGroupIntel.giveReturnAssignments} already sets the same flag, which is why
     * our blockades never needed this. A converted save restores false and simply flags on load.
     */
    protected boolean returnLegFlagged = false;

    /** The action stage, so {@link #notifyEnded()} can ask whether the presence actually ran its
     *  window. Held as a direct reference rather than scanned out of the stage list: RaidIntel
     *  exposes no public stage accessor, and the stage is already in saved state as part of that
     *  list, so this adds no serialization surface (the AshenRaidIntel precedent). */
    protected ExileHegLeagueInterdictionStage interdictionStage = null;

    public ExileHegLeagueRaidIntel(StarSystemAPI target, FactionAPI actor, String victimFactionId,
                                   int maxShipSize, float bountyPerFrigate,
                                   float wearinessPerFpDestroyed, float systemCooldownDays) {
        super(target, actor, null);
        this.actorFactionId = actor != null ? actor.getId() : null;
        this.victimFactionId = victimFactionId;
        this.maxShipSize = maxShipSize;
        this.bountyPerFrigate = bountyPerFrigate;
        this.wearinessPerFpDestroyed = wearinessPerFpDestroyed;
        this.systemCooldownDays = systemCooldownDays;
        setHidden(true);
    }

    public String getActorFactionId() {
        return actorFactionId;
    }

    public String getVictimFactionId() {
        return victimFactionId;
    }

    /**
     * Is this op an INTERDICTION (the Pass-2 presence raid) rather than some other op type on the
     * same pipeline?
     *
     * <p>Added by Pass 3, which introduces {@link ExileHegLeagueStrikeIntel} as a subclass. The
     * intel manager's {@code getIntel(ExileHegLeagueRaidIntel.class)} lookup returns subclasses
     * too, so without this flag a live surgical strike would silently count against
     * interdiction's per-side concurrent-op cap and quietly throttle Pass-2 behaviour that has
     * already been signed off in a live test.
     */
    public boolean isInterdictionOp() {
        return true;
    }

    /**
     * The "this op is over" bullet line. Pass 3 seam: the same intel body now serves interdictions
     * and punitive strikes, and "the interdiction of the X has ended" is wrong for the latter.
     */
    protected String getOpEndedText() {
        return "The interdiction of the " + getSystem().getNameWithLowercaseType() + " has ended";
    }

    public void setInterdictionStage(ExileHegLeagueInterdictionStage stage) {
        this.interdictionStage = stage;
    }

    /**
     * Start the target system's anti-hotspot cooldown when the op ENDS, not when it launched, and
     * report a completed operation to the tension meter.
     *
     * <p>The end-start cooldown is the deliberate choice (sec 9 asks for it to be made
     * deliberately): an op easily outlives 30-60 days -- travel, then the presence window, then
     * the return leg -- so a launch-start flag would expire while the raiders were still on
     * station and fail at the anti-repeat job it exists for. That exact bug was found and fixed in
     * the Ashen build; this copy starts correct.
     *
     * <p>{@code notifyEnded()} is used rather than the stage's own {@code finish()} for the same
     * reason recorded in {@code AshenRaidIntel.reportRehearsalOutcome()}: {@code finish()} only
     * runs inside the {@code status == ONGOING} branch, so an op that aborts on losses never
     * reaches it.
     */
    /**
     * Run 4 read (2026-09-22): the second Galatia squeeze launched inside the first's
     * {@code endAfterDelay()} window, because every live-op scan deliberately skips
     * {@code isEnding()} ops and the cooldown was stamped only in {@link #notifyEnded()}. Stamp it
     * here too -- the moment the op starts ending is the moment the system must be off-limits.
     * Same key, same duration, idempotent; the blockade sibling already does this.
     */
    @Override
    protected void notifyEnding() {
        super.notifyEnding();
        stampSystemCooldown();
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        reportOutcome();
        stampSystemCooldown();
    }

    /**
     * THE ABORT-STAGE LINE (recommendations register R5, ruled by Sean 2026-09-28). Vanilla calls
     * this hook at the exact moment any stage fails -- for interdictions, strikes and squeezes alike,
     * since all three are this class. The 2026-09-28 run lost the Raesvelg -> Kumari Kandam
     * interdiction nine days after launch with no ARRIVED line and nothing in the log saying where
     * or why; this is that line. Logging only.
     *
     * <p>The FP figure mirrors vanilla's own {@code BaseRaidStage.enoughMadeIt()}, the test that
     * decides the losses abort: a SPAWNED fleet counts {@code fleetPoints / Misc.getSpawnFPMult},
     * an ABSTRACT route counts its full launch FP with no damage applied. So an op whose fleets are
     * all abstract can never fail this test; "live" vs "abstract" route counts are printed so a
     * reader can see which case it was.
     */
    @Override
    protected void failedAtStage(RaidStage stage) {
        super.failedAtStage(stage);
        lastFailureWasLossesAbort = false;
        try {
            int index = stages.indexOf(stage);
            String stageName = stage != null ? stage.getClass().getSimpleName() : "?";
            float madeItFp = 0f;
            float abortFp = 0f;
            float stageElapsed = -1f;
            int live = 0;
            int abstractRoutes = 0;
            if (stage instanceof com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage) {
                com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage base =
                        (com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage) stage;
                abortFp = base.getAbortFP();
                stageElapsed = base.getElapsed();
                List routes = base.getRoutes();
                for (int i = 0; i < routes.size(); i++) {
                    RouteData route = (RouteData) routes.get(i);
                    CampaignFleetAPI fleet = route.getActiveFleet();
                    if (fleet != null) {
                        live++;
                        madeItFp += fleet.getFleetPoints() / Misc.getSpawnFPMult(fleet);
                    } else {
                        abstractRoutes++;
                        if (route.getExtra() != null && route.getExtra().fp != null) {
                            madeItFp += route.getExtra().fp.floatValue();
                        }
                    }
                }
            }
            // sec 14.15: only vanilla's LOSSES abort counts as the op being broken.
            lastFailureWasLossesAbort = abortFp > 0f && madeItFp < abortFp;
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            String playerAt = player != null && player.getContainingLocation() != null
                    ? player.getContainingLocation().getName() : "?";
            Global.getLogger(ExileHegLeagueRaidIntel.class).info(
                    "Exiles " + logLabel() + " op ABORTED in stage " + index + " (" + stageName + ")"
                            + " after " + (int) stageElapsed + "d in that stage"
                            + " | target " + (getSystem() != null ? getSystem().getBaseName() : "?")
                            + " | remaining FP " + (int) madeItFp + " vs abort line " + (int) abortFp
                            + (madeItFp >= abortFp ? " (NOT an FP abort)" : " (FP abort)")
                            + " | routes live=" + live + " abstract=" + abstractRoutes
                            + " | player in " + playerAt
                            + " | actor=" + actorFactionId + " victim=" + victimFactionId);
        } catch (Exception e) {
            // A diagnostic must never break the op's failure path.
            Global.getLogger(ExileHegLeagueRaidIntel.class).warn(
                    "Exiles " + logLabel() + " op ABORTED (abort-stage line failed: " + e + ")");
        }
    }

    /** The director name in the ABORTED and RETURN LEG lines. Midan's strike (Pass 7) overrides it
     *  so the log report keeps its ops apart; the output here is unchanged. */
    protected String logLabel() {
        return "Heg-League";
    }

    protected void stampSystemCooldown() {
        if (systemCooldownDays <= 0f || getSystem() == null) return;
        getSystem().getMemoryWithoutUpdate().set(
                ExileHegLeagueInterdictionManager.SYSTEM_COOLDOWN_KEY, true, systemCooldownDays);
    }

    /** sec 3.2.1 "Up": a completed op is small upward pressure on the meter. An op that was driven
     *  off is not a completed op and reports nothing. */
    protected void reportOutcome() {
        if (outcomeReported) return;
        outcomeReported = true;
        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        boolean completed = interdictionStage != null && interdictionStage.isPresenceCompleted();
        // sec 3.2.6: weariness reads BOTH outcomes -- the victim when the presence completed, the
        // actor when the op was driven off. Tension still reads only a completed op, unchanged.
        if (director != null) {
            director.reportOperationWeariness(actorFactionId, victimFactionId, completed, false);
        }
        if (!completed) {
            Global.getLogger(ExileHegLeagueRaidIntel.class).info(
                    "Exiles Heg-League interdiction ended WITHOUT completing its presence in "
                            + (getSystem() != null ? getSystem().getBaseName() : "?")
                            + " -- no tension contribution");
            return;
        }
        if (director == null) return;
        director.reportOperationCompleted(actorFactionId);
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
                revealed = true; // added visible by an older build; adopt silently
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

        // RETURN LEG: runs in the same tick the op leaves its action stage, so it lands before the
        // intel starts ending. Fleets still abstract at that moment are caught in spawnFleet().
        if (!returnLegFlagged && ExileReturnLeg.isPastActionStage(this)) {
            returnLegFlagged = true;
            ExileReturnLeg.flagLiveFleets(this, logLabel(), actorFactionId, victimFactionId);
        }

        // PASS 8a TEST MEASUREMENT (off unless testing.stopDiagnosticsDays > 0). Runs from the
        // intel, not the stage, so it also covers the approach, when no fleet is marked yet.
        if (ExileHegLeaguePlayerStop.diagnosticsDays > 0f && interdictionStage != null
                && !isEnding() && !isEnded()) {
            stopDiagDays += Misc.getDays(amount);
            if (stopDiagDays >= ExileHegLeaguePlayerStop.diagnosticsDays) {
                stopDiagDays = 0f;
                logStopDiagnostics();
            }
        }
    }

    /** Clock for the STOP DIAG line. Transient ON PURPOSE: a test measurement must add nothing to
     *  the save; it comes back 0 after a load. */
    protected transient float stopDiagDays = 0f;

    /** Interdictions and squeezes only ({@code interdictionStage} is null on a strike, which
     *  never stops the player). The work and the reasons are in
     *  {@link ExileHegLeaguePlayerStop#logDiagnostics}. */
    protected void logStopDiagnostics() {
        ActionStage action = getActionStage();
        int current = getCurrentStage();
        int actionIndex = action != null ? getStageIndex(action) : -1;
        String phase = "action";
        if (actionIndex >= 0 && current < actionIndex) phase = "approach";
        if (actionIndex >= 0 && current > actionIndex) phase = "return";
        ExileHegLeaguePlayerStop.logDiagnostics(this, getSystem(),
                interdictionStage.getOpNoun(), phase, interdictionStage.focusMarketId);
    }

    /** Hook each spawned fleet up to the bounty listener; a fleet spawning on its way home gets the
     *  return-leg flag too (vanilla marks re-routed raid routes with {@code STRAGGLER}). */
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
                    Global.getLogger(ExileHegLeagueRaidIntel.class).warn(
                            "Could not load payout icon " + PAYOUT_ICON + " -- using crest", e);
                    return super.getIcon();
                }
            }
            return PAYOUT_ICON;
        }
        return super.getIcon();
    }

    /** Surface under BOUNTIES too -- the raided side literally pays one. */
    @Override
    public java.util.Set<String> getIntelTags(com.fs.starfarer.api.ui.SectorMapAPI map) {
        java.util.Set<String> tags = super.getIntelTags(map);
        if (bountyPerFrigate > 0f) {
            tags.add(Tags.INTEL_BOUNTY);
        }
        return tags;
    }

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

    /** Vanilla body + the maxShipSize cap + the raider tag. */
    @Override
    public CampaignFleetAPI createFleet(String factionId, RouteData route, MarketAPI market,
                                        Vector2f locInHyper, Random random) {
        if (random == null) random = new Random();

        OptionalFleetData extra = route.getExtra();

        float combat = extra.fp;
        float tanker = extra.fp * (0.1f + random.nextFloat() * 0.05f);
        float transport = extra.fp * (0.1f + random.nextFloat() * 0.05f);
        float freighter = 0f;
        combat -= tanker;
        combat -= transport;

        FleetParamsV3 params = new FleetParamsV3(
                market,
                locInHyper,
                factionId,
                route == null ? null : route.getQualityOverride(),
                extra.fleetType,
                combat,
                freighter,
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

        // THE ship-farming guardrail (sec 5, ruling S6). Native engine lever -- FleetFactoryV3
        // consumes it, so no post-generation filtering is needed and none is done (R3: no XIV
        // sweep; the cap already removes five of the six XIV hulls on its own).
        if (maxShipSize > 0) {
            params.maxShipSize = maxShipSize;
        }

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);
        fleet.addTag(RAIDER_TAG);

        // PLAYER-FACING ONLY (established by decompile during the Ashen build: the flag is read
        // only when the counterparty is the player). It earns its place on the player's side of
        // the fight -- a player defending the raided side should find raiders that break off
        // rather than throw themselves at a stronger fleet. It is NOT a fix for NPC-vs-NPC
        // behaviour and must not be re-proposed as one.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);

        if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        }

        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
        fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);

        return fleet;
    }

    // --- FleetEventListener: the raided side pays for dead raiders ---

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason,
                                               Object param) {
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner,
                                     BattleAPI battle) {
        // Pay through the ENDING window too: when an op collapses early its survivors are still
        // in-system for days, and mop-up kills earn the bounty like any other.
        if (isEnded()) return;
        if (fleet == null || !fleet.hasTag(RAIDER_TAG)) return;

        // VICTORY-BONUS LEDGER (sec 14.15): EVERY battle, player or not, so the share is measured
        // against all of the op's losses -- defenders and Nex task groups included. Per fleet, so
        // an op with two fleets in one battle counts each fleet's own losses once.
        if (battle != null) {
            float lost = ExileHegLeagueBattleRewards.fleetFpLost(fleet);
            if (lost > 0f) {
                victoryTotalLostFp += lost;
                victoryPlayerFp += lost
                        * ExileHegLeagueBattleRewards.playerCreditFraction(fleet, battle);
            }
        }

        if (battle == null || !battle.isPlayerInvolved()) return;

        // Fighting the raiders IS finding out about the operation.
        if (!revealed && !isEnding()) {
            reveal(null);
        }

        // WAR WEARINESS -- side-agnostic, per Sean's ruling of 2026-08-27 (sec 14.3). Handled
        // entirely inside the helper because it reads the WHOLE battle, not just this fleet.
        applyPlayerInflictedWeariness(battle);

        // BOUNTY -- still strictly the raided side paying for dead RAIDERS, scoped to this op
        // fleet's own losses, and never paid when this op fleet fought ON the player's side.
        //
        // EXTRACTED 2026-09-20 (Pass 4, decision D4) to ExileHegLeagueBattleRewards, because the
        // standalone system blockade is a second caller of the identical body. Behaviour, log
        // wording and the exploit guard are unchanged; the deferred-notice QUEUE stays here,
        // since the two callers present a payout differently.
        BountyResult result = ExileHegLeagueBattleRewards.payRaidedSideBounty(
                fleet, battle, actorFactionId, victimFactionId, bountyPerFrigate,
                ExileHegLeagueRaidIntel.class);
        if (result == null) return;

        if (pendingBounties == null) pendingBounties = new ArrayList();
        pendingBounties.add(result);
        if (pendingBounties.size() == 1) {
            bountyNoticeDays = BOUNTY_NOTICE_DELAY_DAYS;
        }
        // FOUND 2026-10-30 (Midan sneak-attack test): the delayed notice is flushed from
        // advanceImpl(), which vanilla stops calling once the intel is ENDING. A fight with the
        // raiders on their way home (the op ends right after its action stage) was paid but never
        // announced. Once the op is ending there is nothing left to aggregate: announce now.
        if (isEnding() || isEnded()) {
            flushPendingBounties();
        }
    }

    /**
     * War weariness from player-inflicted losses -- <b>RULED by Sean, 2026-08-27</b> (sec 14.3;
     * supersedes the raider-side-only reading Pass 2 originally shipped).
     *
     * <p><b>The rule:</b> weariness is credited to whichever faction OWNED the ships the player
     * killed, battle by battle. Player kills raiders -> the raiding faction wearies. Player sides
     * with the raiders and kills defenders -> the DEFENDING faction wearies. Both can happen in one
     * battle (a three-way), and both are credited. This is consistent with sec 3.2.1, which defines
     * weariness as a faction's own cumulative losses, and it makes the mercenary's choice of side
     * matter rather than only their choice of target system.
     *
     * <p><b>Implementation:</b> the player's kills are, by definition, losses on the side opposing
     * the player, so the scan is {@code battle.getNonPlayerSideSnapshot()} -- the same list vanilla
     * {@code SystemBountyIntel} pays out from. Fleets belonging to neither war faction (pirates,
     * a passing Independent) are skipped; each war faction's fleet-point losses are totalled
     * separately and credited to its own ledger, scaled by
     * {@code playerInvolvementFraction * wearinessPerFpDestroyed}. When the player fights ON the
     * raiders' side, this op's own fleet sits on the PLAYER'S side and is correctly excluded --
     * those raider losses were inflicted by NPC defenders, not by the player.
     *
     * <p><b>SCOPE LIMIT (documented, not a defect):</b> this listener is attached per-fleet to this
     * op's raiders, so it only ever sees battles <i>this op fleet is in</i>. Hegemony/League battles
     * elsewhere in the sector -- ordinary patrol skirmishes, a player ambush of a Hegemony fleet
     * unrelated to any op -- are invisible here and contribute nothing. Sector-wide weariness
     * accounting would need a ListenerManager-level {@code FleetEventListener} owned by the
     * director; that is deliberately NOT built in this pass.
     *
     * <p><b>One battle, one credit.</b> The listener fires once per raider fleet, so an op with two
     * fleets in the same battle would otherwise scan the losing side twice and double-credit.
     * Guarded by {@link #wearinessBattleSeed} against {@code battle.getSeed()}, which is fixed for
     * the life of a battle.
     */
    protected void applyPlayerInflictedWeariness(BattleAPI battle) {
        // EXTRACTED 2026-09-20 (Pass 4, decision D4) to ExileHegLeagueBattleRewards. The body is
        // unchanged, including the order of its early returns; the one-battle-one-credit GUARD
        // stays a saved field of this intel and is handed to the helper rather than owned by it,
        // so two independent operations can never suppress each other's credit.
        wearinessBattleSeed = ExileHegLeagueBattleRewards.applyPlayerInflictedWeariness(
                battle, actorFactionId, victimFactionId, wearinessPerFpDestroyed,
                wearinessBattleSeed, ExileHegLeagueRaidIntel.class);
    }

    /** Send the queued payout notice(s) as one update; multiple battles inside the delay window
     *  aggregate into a single popup. */
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
        // Instrumented 2026-10-30: the notice itself was invisible to the log, so a missing popup
        // could not be told from a missing post.
        Global.getLogger(ExileHegLeagueRaidIntel.class).info(
                "Exiles " + logLabel() + " BOUNTY NOTICE posted: " + combined.payment + "c"
                        + (combined.rep != null ? " | rep " + Math.round(combined.rep.delta * 100f) : "")
                        + " | intel " + (isHidden() ? "HIDDEN (no popup)" : "visible")
                        + (getPlayerVisibleTimestamp() == null ? ", never posted (no popup)" : "")
                        + (isEnding() || isEnded() ? ", ending" : ""));
    }

    /** Bounty updates render their own bullets; everything else is the vanilla RaidIntel bullet
     *  set with the System-Bounty rewrites. */
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
            FactionAPI paying = Global.getSector().getFaction(victimFactionId);
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

        FactionAPI paying = victimFactionId != null
                ? Global.getSector().getFaction(victimFactionId) : null;
        if (paying != null) {
            info.addPara("Target: " + paying.getDisplayName(), initPad, tc,
                    paying.getBaseUIColor(), paying.getDisplayName());
            initPad = 0f;
        }
        if (isUpdate) {
            if (getListInfoParam() == ENTERED_SYSTEM_UPDATE) {
                info.addPara("Hostile forces have arrived in-system", tc, initPad);
            } else {
                info.addPara(getOpEndedText(), tc, initPad);
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

        if (bountyPerFrigate > 0f && !isEnding()) {
            Color tc2 = getBulletColorForMode(mode);
            bullet(info);
            info.addPara("%s base reward per frigate", 0f, tc2, h,
                    Misc.getDGSCredits(bountyPerFrigate));
            ActionStage action = getActionStage();
            if (action != null && getCurrentStage() == getStageIndex(action)
                    && action.getMaxDays() > 1f) {
                int left = (int) Math.ceil(Math.max(0f, action.getMaxDays() - action.getElapsed()));
                info.addPara("up to %s days remaining", 0f, tc2, h, "" + left);
            }
            unindent(info);
        }
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        super.createSmallDescription(info, width, height);

        FactionAPI paying = victimFactionId != null
                ? Global.getSector().getFaction(victimFactionId) : null;
        FactionAPI actor = actorFactionId != null
                ? Global.getSector().getFaction(actorFactionId) : null;
        if (bountyPerFrigate > 0f && paying != null && !isEnding()) {
            info.addSectionHeading("Bounty", paying.getBaseUIColor(), paying.getDarkUIColor(),
                    Alignment.MID, opad);
            info.addPara(Misc.ucFirst(paying.getDisplayNameWithArticle())
                    + " is paying a bounty for "
                    + (actor != null ? actor.getDisplayNameWithArticle() : "hostile")
                    + " warships destroyed while this operation is underway, scaled by hull size"
                    + " and your share of the fighting.", opad);
            info.addPara("%s base reward per frigate", opad, h,
                    Misc.getDGSCredits(bountyPerFrigate));
        }
    }
}
