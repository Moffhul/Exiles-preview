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
 * Ashen Crusade interdiction-raid intel -- PASS 4 of docs/EXILES_ASHEN_CRUSADE_WAR.md §6.3.
 * A direct port of {@link BlackFleetRaidIntel}, which is itself an Omicron port; the pipeline
 * is identical, only the payer and the flavour change.
 *
 * <p><b>Why it exists: vanilla pays nothing for raid defense.</b> Raids never post a
 * {@code SystemBountyIntel} and grant no reputation unless the player literally reinforces a
 * defender fleet mid-battle. This is the heroism vector (pillar 4) -- Tri-Tachyon puts a price
 * on Crusade raiders' heads, and the player who breaks up an interdiction gets paid and gains
 * standing for it. Registered as a {@link FleetEventListener} on each spawned fleet, so a
 * battle involving several of the op's fleets pays each fleet's losses exactly once. The
 * -Ashen side of the ledger happens automatically through stock engagement reputation.
 *
 * <p>Presented as a "System Bounty" like the Black Fleet's, and <b>hidden until the raiders
 * arrive</b> (or until the player fights them first): the player should discover an
 * interdiction by flying into it or by reading the market screen, not by watching an op
 * forecast. A pre-arrival collapse is therefore never announced -- the Omicron quiet-fail
 * convention.
 *
 * <p>{@link #createFleet} is the vanilla RaidIntel body plus two lines (the per-op
 * {@code maxShipSize} cap, which FleetFactoryV3 consumes natively, and the raider tag). Ashen
 * hulls come from the faction's own curated low-tech roster, so no doctrine work is needed
 * here -- the fleets look like the Crusade because the .faction file says so.
 */
public class AshenRaidIntel extends RaidIntel implements FleetEventListener {

    /** Tag on spawned fleets (identification + the bounty listener's filter). */
    public static final String RAIDER_TAG = "exileac_raider";

    /** See OmicronRaidIntel.BOUNTY_NOTICE_DELAY_DAYS -- same post-battle-flood dodge. */
    protected static final float BOUNTY_NOTICE_DELAY_DAYS = 0.2f;

    /** Vanilla credits icon for payout notices (raw-path sprite, not settings-registered --
     *  load before first use; same pattern as the Black Fleet and Omicron intels). */
    protected static final String PAYOUT_ICON = "graphics/icons/intel/gain_credits.png";
    protected static boolean payoutIconLoaded = false;

    protected int maxShipSize;
    protected String payingFactionId;
    protected float bountyPerFrigate;
    /** Days this op's target system stays off the menu AFTER the op ends -- see
     *  {@link #notifyEnded()}. 0 disables. */
    protected float systemCooldownDays;
    protected BountyResult latestBounty;
    protected List pendingBounties; // BountyResult queued for the deferred payout notice
    protected float bountyNoticeDays = 0f;

    /** Ops launch hidden; surfaced when the raiders arrive in-system, or on first player
     *  battle contact. */
    protected boolean revealed = false;
    /** Set once the op is past its action stage and its fleets were told not to get sidetracked
     *  going home ({@link ExileReturnLeg}; bug sweep ASH-1/ASH-2, 2026-10-06). A save from before
     *  this field restores false and simply flags on load. */
    protected boolean returnLegFlagged = false;

    /** Set on Phase-3 rehearsal ops only; null on ordinary interdiction raids. Drives the
     *  war-score outcome report in {@link #notifyEnded()}. */
    protected AshenRehearsalStage rehearsalStage = null;

    /**
     * Which Phase-4 wave this op is, or 0 for anything that is not part of the Culann strike.
     *
     * <p>1 = the opening interdiction sweep, 2 = the gate blockade, 3 = the armadas. Exists so the
     * launcher can ask the intel manager "has wave N already sailed?" instead of holding a
     * countdown in saved state -- the same derive-don't-store pattern the manager already uses for
     * concurrency ({@code countActive}). Saved state that duplicates something derivable is a
     * second source of truth waiting to desync.
     */
    protected int phase4Wave = 0;

    public int getPhase4Wave() {
        return phase4Wave;
    }

    public void setPhase4Wave(int phase4Wave) {
        this.phase4Wave = phase4Wave;
    }

    public AshenRaidIntel(StarSystemAPI target, FactionAPI faction, int maxShipSize,
                          String payingFactionId, float bountyPerFrigate,
                          float systemCooldownDays) {
        super(target, faction, null);
        this.maxShipSize = maxShipSize;
        this.payingFactionId = payingFactionId;
        this.bountyPerFrigate = bountyPerFrigate;
        this.systemCooldownDays = systemCooldownDays;
        setHidden(true);
    }

    /**
     * Start the target system's anti-hotspot cooldown when the op ENDS, not when it launched.
     *
     * <p>Fixed 2026-08-02. The flag used to be set in {@code launchOp}, but an op lives far
     * longer than the cooldown -- outbound travel, then 30 days of presence, then the return leg
     * -- so it expired while the raiders were still on station. That broke it twice over: it
     * could not stop a second op picking the same system (only {@code maxActive: 1} was doing
     * that, invisibly), and it failed at the anti-repeat job it exists for, since a later op
     * could re-target the system the moment the previous one got home.
     *
     * <p>Setting it here makes "N days" mean N days of respite <b>after the raiders leave</b>,
     * which is what it always meant to say. Same-system concurrency is handled separately and
     * more directly by {@code AshenInterdictionManager.hasLiveOpIn()}.
     *
     * <p>Pattern borrowed from {@code BlackFleetRaidIntel.notifyEnded()} -- the op knows its own
     * outcome; nothing needs to watch it.
     */
    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        reportRehearsalOutcome();
        if (systemCooldownDays <= 0f || getSystem() == null) return;
        getSystem().getMemoryWithoutUpdate().set(
                AshenInterdictionManager.SYSTEM_COOLDOWN_KEY, true, systemCooldownDays);
    }

    /**
     * Feed the war clock when a Phase-3 rehearsal op ends, however it ended.
     *
     * <p><b>Moved here from {@code AshenRehearsalStage.finish()} after the 2026-08-09 live test,
     * which found the player's main lever silently disconnected.</b> Three rehearsals ran and
     * only one reported: the log shows a single +0.12 and <i>no war-score decrease anywhere in
     * the run</i>, so breaking a rehearsal cost the Crusade nothing.
     *
     * <p>Cause: the stage reported from {@code finish()}, which only runs inside
     * {@code updateStatus()}'s {@code status == ONGOING} branch. An op that aborts on fleet
     * losses -- or that ends before its action stage is ever reached -- skips that branch
     * entirely. Which is precisely the case the failure penalty exists for.
     *
     * <p>{@code notifyEnded()} is the seam that always runs, whatever path the op took, and is
     * already trusted for the system cooldown above. The stage still owns the <i>verdict</i>
     * ({@code hasLanded()}); this just makes sure someone always asks for it.
     *
     * <p><b>The log line moved here for the same reason, 2026-08-13.</b> The temporary
     * {@code DEBUG_FEASIBILITY} end-of-op diagnostic was left behind in {@code finish()} when the
     * war-score report was moved out of it -- so the one line written to diagnose a failed
     * rehearsal sat in the one method a failed rehearsal does not reach. Reading the trace proved
     * it: nine launches, seven strikes, and not a single failure line for the two that did not
     * land. It is kept permanently rather than removed because {@code director.reportRehearsal()}
     * announces itself through {@code debug()}, which reaches the campaign feed only.
     */
    protected void reportRehearsalOutcome() {
        AshenRehearsalStage rehearsal = findRehearsalStage();
        if (rehearsal == null) return; // ordinary interdiction op
        if (rehearsal.isOutcomeReported()) return;
        rehearsal.setOutcomeReported(true);

        // Before the director check, deliberately: an outcome that cannot be scored is exactly
        // the one worth having in the log.
        Global.getLogger(AshenRaidIntel.class).info(
                "Ashen rehearsal ended -- " + rehearsal.getOutcomeSummary());

        AshenWarDirector director = AshenWarDirector.get();
        if (director == null) return;
        // Polymorphic: a rehearsal moves the preparation meter, the Culann strike opens or closes
        // the Phase-4 branch. See AshenRehearsalStage.reportOutcome().
        rehearsal.reportOutcome(director);
    }

    /**
     * The rehearsal stage if this is a Phase-3 rehearsal op, else null.
     *
     * <p>Held as a direct reference set by {@code AshenInterdictionManager.launchRehearsal()},
     * rather than scanned out of the stage list: {@code RaidIntel} exposes no public accessor for
     * its stages in the shipped API sources, and reaching for the protected field would be a
     * worse dependency than one explicit setter. The stage is already in saved state as part of
     * that list, so this reference adds no serialization surface.
     */
    protected AshenRehearsalStage findRehearsalStage() {
        return rehearsalStage;
    }

    public void setRehearsalStage(AshenRehearsalStage rehearsalStage) {
        this.rehearsalStage = rehearsalStage;
    }

    /**
     * The rehearsal's right-hand panel, modelled on vanilla's blockade entry (Sean, 2026-08-10).
     *
     * <p>Numbers come from the same accessors vanilla uses -- {@code getRaidStrDesc()} and
     * {@code getOrigNumFleets()} for the force estimate, and the live companion blockade for the
     * accessibility penalty -- so the figures stay honest if the config is retuned.
     */
    protected void createRehearsalDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        FactionAPI faction = getFaction();

        info.addImage(faction.getLogo(), width, 128, opad);

        String systemName = getSystem() != null ? getSystem().getName() : "the target system";
        info.addPara(Misc.ucFirst(faction.getDisplayNameWithArticle()) + " is blockading the "
                + systemName + ". The blockading forces are projected to be " + getRaidStrDesc()
                + " and likely comprised of " + (int) getOrigNumFleets() + " fleets.",
                opad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());

        info.addSectionHeading("Assessment", faction.getBaseUIColor(), faction.getDarkUIColor(),
                Alignment.MID, opad);

        AshenBlockade blockade = findCompanionBlockade();
        if (blockade != null) {
            int penalty = (int) Math.round(blockade.getAccessibilityPenalty() * 100f);
            if (penalty > 0) {
                info.addPara("Crusade pickets are holding the system's jump-points, cutting off"
                        + " reinforcement and imposing a %s accessibility penalty on "
                        + Misc.ucFirst(Global.getSector().getFaction(Factions.TRITACHYON)
                                .getDisplayName()) + " colonies in the system.",
                        opad, h, "-" + penalty + "%");
            }
        } else {
            info.addPara("Crusade pickets are holding the system's jump-points, cutting off"
                    + " reinforcement.", opad);
        }

        // The honest divergence from vanilla's blockade text -- see the javadoc above.
        info.addPara("Unlike an ordinary blockade, these forces are not content to sit on the"
                + " approaches. A strike group is expected to press an attack against a colony in"
                + " the system, and any defending fleet or station in its path will be engaged.",
                opad);

        info.addSectionHeading("Status", faction.getBaseUIColor(), faction.getDarkUIColor(),
                Alignment.MID, opad);

        if (isEnding() || isEnded()) {
            info.addPara("The operation is over and the Crusade has withdrawn from the "
                    + (getSystem() != null ? getSystem().getNameWithLowercaseType()
                            : "system") + ".", opad);
        } else if (revealed) {
            info.addPara("Crusade forces are currently operating in the "
                    + (getSystem() != null ? getSystem().getNameWithLowercaseType()
                            : "system") + ".", opad);
        } else {
            info.addPara("Crusade forces are en route.", opad);
        }
    }

    /** The live Hussar blockade escorting this op, or null. */
    protected AshenBlockade findCompanionBlockade() {
        if (getSystem() == null) return null;
        List blockades = Global.getSector().getIntelManager().getIntel(AshenBlockade.class);
        for (int i = 0; i < blockades.size(); i++) {
            AshenBlockade blockade = (AshenBlockade) blockades.get(i);
            if (blockade.isEnded() || blockade.isEnding()) continue;
            if (blockade.getBlockadedSystem() == getSystem()) return blockade;
        }
        return null;
    }

    /** True while a Hussar blockade is live in this op's system. Mirror of
     *  {@code AshenBlockade.hasCompanionRaid()}. */
    protected boolean hasCompanionBlockade() {
        return findCompanionBlockade() != null;
    }

    /**
     * True for Phase-3 rehearsal ops; false for ordinary interdiction raids.
     *
     * <p>This class serves both, so <b>every player-facing override below is gated on it</b>.
     * Sean's instruction 2026-08-10 was explicit: model the rehearsal's presentation on the
     * blockade entry, and <i>do not change the interdiction raid language</i>.
     */
    protected boolean isRehearsal() {
        return rehearsalStage != null;
    }

    @Override
    public String getName() {
        String base = isRehearsal() ? "Ashen Crusade Blockade" : "System Bounty";
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

        // RETURN LEG (ExileReturnLeg): the tick the op leaves its action stage.
        if (!returnLegFlagged && ExileReturnLeg.isPastActionStage(this)) {
            returnLegFlagged = true;
            ExileReturnLeg.flagLiveFleets(this, "Ashen",
                    getFaction() != null ? getFaction().getId() : null, payingFactionId);
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
                    Global.getLogger(AshenRaidIntel.class).warn(
                            "Could not load payout icon " + PAYOUT_ICON + " -- using crest", e);
                    return super.getIcon();
                }
            }
            return PAYOUT_ICON;
        }
        return super.getIcon();
    }

    /** Surface under BOUNTIES too -- Tri-Tachyon literally pays one. */
    @Override
    public java.util.Set<String> getIntelTags(SectorMapAPI map) {
        java.util.Set<String> tags = super.getIntelTags(map);
        if (bountyPerFrigate > 0f) {
            tags.add(Tags.INTEL_BOUNTY);
        }
        return tags;
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

        if (maxShipSize > 0) {
            params.maxShipSize = maxShipSize;
        }

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);
        fleet.addTag(RAIDER_TAG);

        // PLAYER-FACING ONLY, and deliberately so (Sean, 2026-08-02). Decompiling
        // campaign.ai.TacticalModule.pickEncounterOption shows this flag is read ONLY when the
        // counterparty is the player (Misc.isPlayerOrCombinedPlayerPrimary) -- it does nothing
        // for NPC-vs-NPC, which is why it is NOT the fix for raiders charging Tri-Tachyon's
        // stations (that is maxFleetFp; see AshenInterdictionManager.HiddenSourceAssembleStage).
        //
        // It earns its place on the player's side of the fight instead: a player siding with
        // Tri-Tachyon should find raiders that break off rather than throw themselves at a
        // stronger fleet. Vanilla precedent: RemnantSeededFleetManager sets the same flag.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);

        if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
        }

        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
        fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);

        return fleet;
    }

    // --- FleetEventListener: Tri-Tachyon pays for dead Crusade raiders ---

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason,
                                               Object param) {
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner,
                                     BattleAPI battle) {
        // Pay through the ENDING window too: when an op collapses early its survivors are
        // still in-system for days, and mop-up kills earn the bounty like any other.
        if (isEnded()) return;
        if (fleet == null || !fleet.hasTag(RAIDER_TAG)) return;
        if (battle == null || !battle.isPlayerInvolved()) return;

        // Fighting the raiders IS finding out about the operation.
        if (!revealed && !isEnding()) {
            reveal(null);
        }

        if (bountyPerFrigate <= 0f) return;

        FactionAPI paying = payingFactionId != null
                ? Global.getSector().getFaction(payingFactionId) : null;
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
                payingFactionId);

        if (pendingBounties == null) pendingBounties = new ArrayList();
        pendingBounties.add(new BountyResult(payment, battle.getPlayerInvolvementFraction(), rep));
        if (pendingBounties.size() == 1) {
            bountyNoticeDays = BOUNTY_NOTICE_DELAY_DAYS;
        }

        Global.getLogger(AshenRaidIntel.class).info(
                "Ashen raider bounty: " + payment + "c from " + payingFactionId
                        + " (fpDestroyed=" + fpDestroyed + ", involvement="
                        + battle.getPlayerInvolvementFraction() + ")");

        // BUG-2026-10-30-A: the delayed notice is flushed from advanceImpl(), which vanilla stops
        // calling once the intel is ENDING -- and this op pays through ENDING on purpose. Once it
        // is ending there is nothing left to aggregate: announce now.
        if (isEnding() || isEnded()) {
            flushPendingBounties();
        }
    }

    /** Send the queued payout notice(s) as one update; multiple battles inside the delay
     *  window aggregate into a single popup. */
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
        Global.getLogger(AshenRaidIntel.class).info(
                "Ashen BOUNTY NOTICE posted: " + combined.payment + "c"
                        + (combined.rep != null ? " | rep " + Math.round(combined.rep.delta * 100f) : "")
                        + " | intel " + (isHidden() ? "HIDDEN (no popup)" : "visible")
                        + (getPlayerVisibleTimestamp() == null ? ", never posted (no popup)" : "")
                        + (isEnding() || isEnded() ? ", ending" : ""));
    }

    /** Bounty updates render their own bullets; everything else is the vanilla RaidIntel
     *  bullet set with the System-Bounty rewrites (no "Faction: Ashen Crusade" line, and an
     *  outcome-neutral close-out). */
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
            FactionAPI paying = Global.getSector().getFaction(payingFactionId);
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

        // Target line: the paying faction, which for interdiction is always Tri-Tachyon.
        FactionAPI paying = payingFactionId != null
                ? Global.getSector().getFaction(payingFactionId) : null;
        if (paying != null) {
            info.addPara("Target: " + paying.getDisplayName(), initPad, tc,
                    paying.getBaseUIColor(), paying.getDisplayName());
            initPad = 0f;
        }
        if (isUpdate) {
            if (getListInfoParam() == ENTERED_SYSTEM_UPDATE) {
                info.addPara("Hostile forces have arrived in-system", tc, initPad);
            } else {
                info.addPara((isRehearsal() ? "The blockade of the " : "The raid on the ")
                        + getSystem().getNameWithLowercaseType() + " has ended", tc, initPad);
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

    /**
     * Interdiction raids get vanilla's stock raid description. Rehearsals get blockade-styled
     * text, because to the player they ARE a blockade -- the Hussar element that holds the gates
     * is the same operation, and its own intel entry is hidden.
     *
     * <p><b>Deliberately NOT a copy of vanilla's blockade text.</b> That text promises the
     * blockading forces "will not directly attack colonies in the system", which is <i>true for a
     * vanilla blockade and false here</i> -- the Hussars are sent specifically to crack the
     * station. Copying it verbatim would have the intel lie to the player about the one thing the
     * operation is for. The wording below keeps the blockade's shape and numbers and tells the
     * truth about the strike group.
     */
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (isRehearsal()) {
            createRehearsalDescription(info, width, height);
        } else {
            super.createSmallDescription(info, width, height);
        }

        FactionAPI paying = payingFactionId != null
                ? Global.getSector().getFaction(payingFactionId) : null;
        if (bountyPerFrigate > 0f && paying != null && !isEnding()) {
            info.addSectionHeading("Bounty", paying.getBaseUIColor(), paying.getDarkUIColor(),
                    Alignment.MID, opad);
            info.addPara(Misc.ucFirst(paying.getDisplayNameWithArticle())
                    + " is paying a bounty for Ashen Crusade raiders destroyed while this"
                    + " operation is underway, scaled by hull size and your share of the"
                    + " fighting.", opad);
            info.addPara("%s base reward per frigate", opad, h,
                    Misc.getDGSCredits(bountyPerFrigate));
        }
    }
}
