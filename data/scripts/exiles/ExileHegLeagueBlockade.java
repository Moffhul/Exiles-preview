package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.ActionType;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript.MilitaryResponseParams;
import com.fs.starfarer.api.impl.campaign.NPCHassler;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.group.BlockadeFGI;
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction.FGBlockadeParams;
import com.fs.starfarer.api.util.Misc;

import data.scripts.exiles.OmicronRaidIntel.BountyResult;
import data.scripts.marketconditions.ExileHegLeagueBlockadeCollateral;

/**
 * Hegemony-Persean League SYSTEM BLOCKADE -- PASS 4 of docs/EXILES_HEG_LEAGUE_WAR.md
 * (plan sec 14.9; mechanism sec 4.2, geography sec 2/R6, guardrails sec 5, bounty sec 4.7).
 *
 * <p><b>This is what makes ESCALATION real.</b> Interdiction (Pass 2) hunts shipping and strikes
 * (Pass 3) put one industry offline; a blockade shuts a whole system's jump points for weeks. It
 * is the Hegemony's signature operation and only ever runs during ESCALATION.
 *
 * <p><b>Deliberately thin, like its Ashen sibling.</b> Vanilla's {@link BlockadeFGI} owns the
 * entire lifecycle -- gather, travel, hold, return -- plus fleet creation, the intel listing, and
 * the {@code Blockaded} condition with its apply/unapply. What is ours is five things:
 * <ol>
 * <li>{@link SpreadBlockadeAction} -- hold EVERY gate, not mostly one (ported from
 *     {@code AshenBlockade}; vanilla's 1/N weighting is the wrong shape here too).</li>
 * <li>The COLLATERAL loop (decision D2, RULED 2026-09-20). Vanilla's own apply/unapply are left
 *     completely untouched and keep stamping the victim faction's markets at full tiered force; a
 *     second loop applies the lighter {@code exile_hl_blockade_collateral} to every OTHER market
 *     in the system that passes the guard. Both sets are recomputed live on every tick, so an
 *     ownership flip mid-op moves a market between them with no saved stamp list to go stale.</li>
 * <li>The bounty and war-weariness listener (decision D4). This is a STANDALONE operation, not a
 *     stage of a raid, and neither {@code FleetGroupIntel} nor {@code BaseIntelPlugin} implements
 *     {@code FleetEventListener} -- so it is its own listener, sharing the body with the raid
 *     intel through {@link ExileHegLeagueBattleRewards}.</li>
 * <li>The outcome report to the director plus the end-start system cooldown (the Pass-2
 *     precedent, same {@code $exiles_hl_op_cooldown} key).</li>
 * <li>{@link #isBrokenUp()} -- a route-damage break-off check. Vanilla DOES have a strength abort of
 *     its own (FleetGroupIntel aborts below 33% of spawned FP; see {@link #isBrokenByLosses()}),
 *     and that is what ends a blockade fought on screen. Corrected 2026-10-06 (bug sweep HL-7).</li>
 * </ol>
 *
 * <p><b>Dropped from the Ashen version, deliberately:</b> the companion-raid abort (that blockade
 * escorts a raid; this one is standalone) and the source-concealment overrides (sec 3.4: Hegemony
 * and League home systems are public knowledge, so the starmap may point at them).
 *
 * <p><b>The accessibility penalty is vanilla's and is NOT flat.</b>
 * {@code BlockadeFGI.getAccessibilityPenalty()} scales the configured value by
 * {@code getRelativeFGStrength}: below 0.75x the system's defenders it returns 0, below 1.25x it
 * returns half, above that the full value. The collateral condition follows the same tier when
 * {@code collateralScalesWithTier} is on, so a blockade too weak to hurt the League does not hurt
 * the bystanders either.
 *
 * <p><b>NPC-vs-NPC is a supported vanilla mode</b> (established during the Ashen build, sec 12 O3):
 * {@code playerTargeted = false} gates intel updates and keeps the player's fleet out of the
 * defender-strength sum, and {@code makeFleetsHostile = false} is load-bearing -- with it true the
 * Ashen blockade flew past the station it was blockading and hunted the player instead.
 */
public class ExileHegLeagueBlockade extends BlockadeFGI
        implements ExileBlockadeIntel, FleetEventListener {

    /** The side running this operation. */
    protected String actorFactionId;
    /** The side being blockaded -- and, per sec 4.7, the side that pays the bounty. */
    protected String victimFactionId;

    protected float bountyPerFrigate;
    protected float wearinessPerFpDestroyed;
    /** Days this op's target system stays off the menu AFTER the op ends. 0 disables. */
    protected float systemCooldownDays;

    /** D2: apply the lighter condition to non-victim markets. False = pure vanilla behaviour. */
    protected boolean collateralEnabled = true;
    /** Unscaled collateral penalty; the tier multiplier is applied on the tick. */
    protected float collateralPenalty = 0.3f;
    protected boolean collateralScalesWithTier = true;

    /** Route-damage fraction at which the blockade breaks off (0.67 since 2026-09-28, matching
     *  vanilla's live group abort). CORRECTION: vanilla DOES abort a live group whose surviving FP
     *  falls below 33% of what it spawned (FleetGroupIntel.advance); only {@code shouldAbort()}
     *  returns false flat. This value reads route damage, which vanilla writes only off screen for
     *  raids -- see {@link #isBrokenByLosses()} for the live break. */
    protected float abortDamageFraction = 0.67f;

    /** Fleet size at or above which a fleet is the named anchor rather than a picket. Derived
     *  from the configured sizes at construction, so retuning them cannot silently leave the
     *  blockade with no anchor or with three of them. */
    protected int anchorSize = Integer.MAX_VALUE;
    /** Guards against two fleets both claiming the anchor name when sizes tie. */
    protected boolean anchorNamed = false;

    /** D6: launched hidden, surfaced the moment the gates are held (or on battle contact). */
    protected boolean revealed = false;
    /** True once the op actually reached its payload window -- the "did it hold?" question. */
    protected boolean reachedPayload = false;
    /** One payload-start measurement line per operation. */
    protected boolean payloadLogged = false;
    /** Clock stamp of the HOLDING line, so the release line can print the hold in days. Long so
     *  a pre-2026-09-21 save deserialises it as null rather than 0. */
    protected Long payloadStartTs = null;
    /** One gates-released measurement line per operation. */
    protected boolean releaseLogged = false;
    /** Last strength tier written to the log; -99 = nothing logged yet. Run 5 read (2026-09-23):
     *  Kapteyn's spillover came off seven weeks before the gates opened and the tick recorded no
     *  reason, so the tier is now logged on every change and every removal names its cause. */
    protected int lastLoggedTier = -99;
    /** Guards the one-shot outcome report and cooldown stamp. */
    protected boolean outcomeReported = false;

    /** Seed of the last battle already credited to the weariness ledgers, or null. Stored as a
     *  Long rather than the BattleAPI so nothing battle-scoped enters saved intel state. */
    protected Long wearinessBattleSeed = null;

    /** VICTORY-BONUS LEDGER (sec 14.15): FP this blockade has lost in ALL battles, and the part
     *  credited to the player. Saved primitives; a pre-2026-09-28 save restores 0 (pays nothing). */
    protected float victoryTotalLostFp = 0f;
    protected float victoryPlayerFp = 0f;

    /** Keep blockade fleets off the stations they are blockading. */
    public static boolean suppressStationAttacks = true;
    /** Do-not-attack entries expire, so they are refreshed on the periodic tick. */
    public static float DO_NOT_ATTACK_DAYS = 5f;

    public ExileHegLeagueBlockade(GenericRaidParams params, FGBlockadeParams blockadeParams,
                                  String actorFactionId, String victimFactionId) {
        super(params, blockadeParams);
        this.actorFactionId = actorFactionId;
        this.victimFactionId = victimFactionId;

        if (params.fleetSizes != null) {
            for (int i = 0; i < params.fleetSizes.size(); i++) {
                int size = ((Integer) params.fleetSizes.get(i)).intValue();
                if (anchorSize == Integer.MAX_VALUE || size > anchorSize) {
                    anchorSize = size;
                }
            }
        }
    }

    public String getActorFactionId() {
        return actorFactionId;
    }

    public String getVictimFactionId() {
        return victimFactionId;
    }

    /** {@link ExileBlockadeIntel}: {@code GenericRaidFGI.getTargetSystem()} is protected, so the
     *  shared layer cannot reach it. */
    public StarSystemAPI getBlockadedSystem() {
        return getTargetSystem();
    }

    public void setAbortDamageFraction(float abortDamageFraction) {
        this.abortDamageFraction = abortDamageFraction;
    }

    public void setBountyPerFrigate(float bountyPerFrigate) {
        this.bountyPerFrigate = bountyPerFrigate;
    }

    public void setWearinessPerFpDestroyed(float wearinessPerFpDestroyed) {
        this.wearinessPerFpDestroyed = wearinessPerFpDestroyed;
    }

    public void setSystemCooldownDays(float systemCooldownDays) {
        this.systemCooldownDays = systemCooldownDays;
    }

    public void setCollateral(boolean enabled, float penalty, boolean scalesWithTier) {
        this.collateralEnabled = enabled;
        this.collateralPenalty = penalty;
        this.collateralScalesWithTier = scalesWithTier;
    }

    // ------------------------------------------------------------------
    // Hold the gates
    // ------------------------------------------------------------------

    /**
     * Hold the system's jump points -- vanilla's actual blockade behaviour, with the Ashen
     * spread fix.
     */
    protected GenericPayloadAction createPayloadAction() {
        return new SpreadBlockadeAction(blockadeParams, params.payloadDays);
    }

    /**
     * Hold EVERY gate, not mostly one.
     *
     * <p>Vanilla's {@code orderFleetMovements()} builds a {@code MilitaryResponseScript} per jump
     * point but weights them {@code 1f} for the primary and {@code 1f / blockadePoints.size()} for
     * the rest, so the fleets concentrate on one gate and the others are effectively open. That is
     * right for a player-directed blockade wanting mass at the likely approach; it is wrong for an
     * operation whose entire point is that nothing gets in or out. Sean observed exactly the
     * vanilla behaviour in the 2026-08-09 Ashen live test, and this is that fix, ported.
     *
     * <p>Named static class, never anonymous -- this ends up in saved state (CLAUDE.md sec 7).
     */
    public static class SpreadBlockadeAction extends FGBlockadeAction {

        public SpreadBlockadeAction(FGBlockadeParams params, float duration) {
            super(params, duration);
        }

        public Object readResolve() {
            return this;
        }

        @Override
        protected void orderFleetMovements() {
            if (blockadePoints.isEmpty() || primary == null) return;

            List fleets = intel.getFleets();
            for (int i = 0; i < fleets.size(); i++) {
                CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
                fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_BUSY);
            }

            for (int i = 0; i < blockadePoints.size(); i++) {
                SectorEntityToken point = (SectorEntityToken) blockadePoints.get(i);
                MilitaryResponseParams aggro = new MilitaryResponseParams(
                        ActionType.HOSTILE,
                        "blockade_" + point.getId(),
                        intel.getFaction(),
                        point,
                        1f, // vanilla: 1f only for primary, 1/N for the rest
                        getDurDays());
                aggro.travelText = null;
                aggro.actionText = params.patrolText;

                MilitaryResponseScript script = new MilitaryResponseScript(aggro);
                params.where.addScript(script);
                scripts.add(script);
            }
        }
    }

    /**
     * Name the anchor, give the pickets the job of turning traffic away, and put every fleet on
     * the blockaded side's bounty.
     *
     * <p>{@link NPCHassler} is vanilla's own implementation of "turn shipping away", used by
     * {@code KnightsOfLuddTakeoverExpedition} on exactly this kind of picket. It goes on pickets
     * only: the anchor reads as the formation's centre of mass, the pickets provide the visible
     * harassment.
     *
     * <p>The bounty hookup is BOTH halves -- the tag the handler filters on and the listener
     * registration -- because these fleets are spawned by vanilla's {@code BlockadeFGI} pipeline,
     * not by a raid intel's {@code spawnFleet()}. Missing either one means killing a blockade
     * picket pays nothing, which is the bug the Ashen build found the hard way.
     */
    protected void configureFleet(int size, CampaignFleetAPI fleet) {
        super.configureFleet(size, fleet);
        if (fleet == null) return;

        // setNoFactionInName(true) is what actually prevents a faction prefix being prepended.
        if (size >= anchorSize && !anchorNamed) {
            anchorNamed = true;
            fleet.setName("Blockade Fleet");
            fleet.setNoFactionInName(true);
        } else {
            fleet.setName("Blockade Picket");
            fleet.setNoFactionInName(true);
            fleet.addScript(new NPCHassler(fleet, getTargetSystem()));
        }

        fleet.addTag(ExileHegLeagueRaidIntel.RAIDER_TAG);
        fleet.addEventListener(this);
    }

    // ------------------------------------------------------------------
    // The periodic tick -- vanilla's own 0.1-0.3 day blockade interval
    // ------------------------------------------------------------------

    /**
     * Everything that has to be recomputed while the operation is live.
     *
     * <p><b>No new per-frame work.</b> Vanilla already runs this interval to apply and unapply its
     * own condition; the collateral loop rides it over the two to four markets of one system, and
     * at most two blockades exist at once, only during ESCALATION.
     */
    protected void periodicUpdate() {
        super.periodicUpdate();
        suppressStationTargeting();
        updateRevealAndPayloadLog();
        updateCollateral();
    }

    /** Withdrawal: strip the pickets' hassler and keep the order alive (2026-10-07 test run, an
     *  Ashen picket stranded the same way -- see {@link ExileReturnOrderWatchdog}). */
    @Override
    protected void giveReturnAssignments(CampaignFleetAPI fleet) {
        super.giveReturnAssignments(fleet);
        ExileReturnOrderWatchdog.attach(fleet, returnLocation, "Heg-League blockade");
    }

    /**
     * D6: hidden while preparing and travelling, visible from the moment the gates are held.
     *
     * <p>Ashen hid its blockade entirely because the rehearsal intel spoke for both objects. Here
     * the blockade IS the listing, so vanilla's {@code BlockadeFGI} intel -- faction crest,
     * assessment section, penalty figure -- is exactly what the player should get once the
     * operation is visible in the sky.
     *
     * <p>The payload-start MEASUREMENT LINE ships beside it: it prints which penalty tier the
     * blockade actually earned against the live defence, which is the number
     * {@code blockade.fleetSizes} and {@code feasibilityMult} are tuned from. Read it against the
     * launch line's estimate (sec 5, "instrument before tuning").
     */
    protected void updateRevealAndPayloadLog() {
        if (!isCurrent(PAYLOAD_ACTION)) return;
        reachedPayload = true;

        if (!revealed) {
            revealed = true;
            setHidden(false);
            if (getPlayerVisibleTimestamp() == null) {
                setPlayerVisibleTimestamp(
                        Long.valueOf(Global.getSector().getClock().getTimestamp()));
            }
        }

        if (payloadLogged) return;
        payloadLogged = true;
        payloadStartTs = Long.valueOf(Global.getSector().getClock().getTimestamp());
        StarSystemAPI system = getTargetSystem();
        int tier = getStrengthTier();
        Global.getLogger(ExileHegLeagueBlockade.class).info(
                "Exiles Heg-League blockade HOLDING "
                        + (system != null ? system.getNameWithLowercaseType() : "?")
                        + ": relative strength " + tier
                        + " (penalty tier " + describeTier(tier) + ")"
                        + " | actor=" + actorFactionId + " victim=" + victimFactionId);
    }

    /** -1 / 0 / 1 as vanilla defines it, or -1 when the route is not up yet (nothing is being
     *  held in that case, so the conservative reading is "no effect"). */
    protected int getStrengthTier() {
        if (getRoute() == null || getRoute().getExtra() == null) return -1;
        return getRelativeFGStrength(getTargetSystem());
    }

    protected static String describeTier(int tier) {
        if (tier < 0) return "zero";
        if (tier == 0) return "half";
        return "full";
    }

    /**
     * THE COLLATERAL SPLIT (decision D2, RULED by Sean 2026-09-20).
     *
     * <p>Vanilla stamps {@code Blockaded} on the victim faction's markets and nothing else, which
     * would leave a neutral colony under the same shut gates untouched. This adds the lighter
     * {@code exile_hl_blockade_collateral} to every other market in the system that passes
     * {@link ExileHegLeagueGeography#isBlockadeCollateralMarket}, at a tier-scaled penalty.
     *
     * <p><b>Both sets are recomputed from scratch on every tick and nothing is remembered.</b> A
     * market that changes hands mid-blockade simply moves between the vanilla condition and ours
     * on the next tick; a market that stops qualifying has ours removed. There is no saved list to
     * fall out of sync with the world, and the condition's own timer is the backstop if this
     * object dies without ever running {@link #notifyEnding()}.
     */
    protected void updateCollateral() {
        if (!collateralEnabled) return;
        StarSystemAPI system = getTargetSystem();
        if (system == null) return;

        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        ExileHegLeagueConfig config = director != null ? director.getConfig() : null;
        if (config == null) return; // the timer backstop covers this; never guess at the guard

        float penalty = 0f;
        boolean holding = isCurrent(PAYLOAD_ACTION);
        if (holding) {
            float mult = 1f;
            int tier = getStrengthTier();
            if (collateralScalesWithTier) {
                if (tier < 0) {
                    mult = 0f;
                } else if (tier == 0) {
                    mult = 0.5f;
                }
            }
            penalty = collateralPenalty * mult;
            // TIER MEASUREMENT LINE: once per change, never per tick. This is vanilla's own
            // relative-strength read, so it also explains the vanilla Blockaded penalty the
            // victim sees.
            if (tier != lastLoggedTier) {
                lastLoggedTier = tier;
                Global.getLogger(ExileHegLeagueBlockade.class).info(
                        "Exiles Heg-League blockade tier " + tier + " (" + describeTier(tier)
                                + ") in " + system.getBaseName()
                                + " | collateral penalty now " + penalty
                                + " | fleets alive=" + getFleets().size()
                                + " | actor=" + actorFactionId + " victim=" + victimFactionId);
            }
        }

        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m == null) continue;
            // The victim's own markets are vanilla's business, never ours -- and this is also the
            // ownership-flip correction: a market that just became the victim's loses ours here
            // and gains Blockaded from vanilla's loop on the same tick.
            boolean isVictimMarket = victimFactionId != null
                    && victimFactionId.equals(m.getFactionId());
            String dropReason = null;
            if (penalty <= 0f) {
                dropReason = holding ? "tier too weak" : "not holding";
            } else if (isVictimMarket) {
                dropReason = "victim market";
            } else if (!ExileHegLeagueGeography.isBlockadeCollateralMarket(config, m)) {
                dropReason = "guard (hidden/player/refugee/excluded/Battered)";
            }
            if (dropReason != null) {
                // Name the cause only when there is actually a stamp to remove -- once per drop.
                if (ExileHegLeagueBlockadeCollateral.isPresent(m)) {
                    Global.getLogger(ExileHegLeagueBlockade.class).info(
                            "Exiles Heg-League blockade collateral dropping " + m.getName()
                                    + ": " + dropReason + " | actor=" + actorFactionId);
                }
                ExileHegLeagueBlockadeCollateral.removeFrom(m);
                continue;
            }
            ExileHegLeagueBlockadeCollateral.applyOrRefresh(m, penalty);
        }
    }

    /**
     * Keep the blockade fleets off the stations in the system.
     *
     * <p>Belt-and-braces: with {@link SpreadBlockadeAction} the fleets hold jump points and have
     * no reason to close on a station at all. It is kept because it is written, tested, and costs
     * one pass over a handful of fleets on a tick that already runs -- and because vanilla's
     * target-selection tally skips station-mode fleets, so no FP balance deters a fleet that does
     * decide to engage one. Unlike the Ashen version this covers EVERY station in the system, not
     * just the victim's: a blockade is not supposed to start a fight with Kapteyn Starworks
     * either. Set {@code suppressStationAttacks} false to compare against stock behaviour.
     */
    protected void suppressStationTargeting() {
        if (!suppressStationAttacks) return;
        StarSystemAPI system = getTargetSystem();
        if (system == null) return;

        List stations = new ArrayList(); // CampaignFleetAPI
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            CampaignFleetAPI station = Misc.getStationFleet(market);
            if (station != null) stations.add(station);
        }
        if (stations.isEmpty()) return;

        List fleets = getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            if (fleet == null || !fleet.isAlive()) continue;
            if (!(fleet.getAI() instanceof ModularFleetAIAPI)) continue;
            ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
            if (ai.getStrategicModule() == null) continue;
            for (int k = 0; k < stations.size(); k++) {
                ai.getStrategicModule().getDoNotAttack().add(
                        (CampaignFleetAPI) stations.get(k), DO_NOT_ATTACK_DAYS);
            }
        }
    }

    // ------------------------------------------------------------------
    // Abort and ending
    // ------------------------------------------------------------------

    protected boolean shouldAbort() {
        if (super.shouldAbort()) return true;
        if (isBrokenUp()) return true;
        return false;
    }

    /**
     * True once the blockading force has taken more than {@link #abortDamageFraction} damage.
     *
     * <p>{@code FleetGroupIntel.shouldAbort()} returns false flat, but (CORRECTED 2026-09-28)
     * vanilla separately aborts a LIVE group below 33% surviving FP in {@code advance()} -- that
     * is the break that actually happens on screen ({@link #isBrokenByLosses()}). This check reads
     * {@code RouteData.getExtra().damage}, which vanilla writes only in FGRaidAction's off-screen
     * autoresolve, so for a blockade it is a belt-and-braces path that has never fired in a log.
     */
    protected boolean isBrokenUp() {
        RouteData route = getRoute();
        if (route == null || route.getExtra() == null) return false;
        Float damage = route.getExtra().damage;
        if (damage == null) return false;
        return damage.floatValue() >= abortDamageFraction;
    }

    /**
     * Clean up, report the outcome, and start the system's anti-hotspot cooldown.
     *
     * <p><b>{@code notifyEnding()} rather than a stage's own finish hook</b>, for the reason
     * recorded in {@code AshenRaidIntel.reportRehearsalOutcome()} and again in Pass 2: a finish
     * hook only runs on the success path, so an operation that aborts on losses never reaches it.
     * Vanilla's {@code BlockadeFGI} already overrides this method to unapply its own condition, so
     * it is guaranteed to run on every exit.
     *
     * <p>The collateral removal here is the ordinary path; the condition's own expiry timer is the
     * backstop for a blockade that is removed or lost without ever ending cleanly.
     */
    /**
     * THE GATES-RELEASED MEASUREMENT LINE (Run 3 read, 2026-09-21).
     *
     * <p>The outcome is reported from {@link #notifyEnding()}, which vanilla runs at the end of
     * the RETURN leg on success, but IMMEDIATELY on an abort ({@code finish(true)} ->
     * {@code endAfterDelay()} -> {@code notifyEnding()}; corrected 2026-10-06, bug sweep HL-8 --
     * {@link #isBrokenByLosses()} relies on that, reading survivors still in the fleet list).
     * On success the completion stamp therefore landed 55, 67 and 102 days after the HOLDING line
     * for 36d, 56d and 56d payloads, and the log could not separate hold length from the sail
     * home. This fires the tick the payload action leaves the queue, whatever ended it: window
     * run out, aborted, broken up, or the last fleet lost. Read the hold against the configured
     * window, and the gap to the outcome line is the return leg.
     *
     * <p>Vanilla calls this from both the spawned-fleets branch and the route-only branch of
     * {@code advance()}, so it is reached in either mode. Super sends the ordinary intel update.
     */
    @Override
    protected void notifyActionFinished(FGAction action) {
        super.notifyActionFinished(action);
        if (releaseLogged || action == null || !PAYLOAD_ACTION.equals(action.getId())) return;
        releaseLogged = true;

        float heldDays = -1f;
        if (payloadStartTs != null) {
            heldDays = Global.getSector().getClock().getElapsedDaysSince(payloadStartTs.longValue());
        }
        float window = -1f;
        if (action instanceof FGBlockadeAction) {
            window = ((FGBlockadeAction) action).getOrigDurDays();
        }
        RouteData route = getRoute();
        Float damage = route != null && route.getExtra() != null ? route.getExtra().damage : null;
        StarSystemAPI system = getTargetSystem();

        Global.getLogger(ExileHegLeagueBlockade.class).info(
                "Exiles Heg-League blockade GATES RELEASED "
                        + (system != null ? system.getNameWithLowercaseType() : "?")
                        + ": held " + (heldDays < 0f ? "?" : String.valueOf((int) heldDays))
                        + "d of " + (window < 0f ? "?" : String.valueOf((int) window)) + "d"
                        + " | reachedPayload=" + reachedPayload
                        + " aborted=" + isAborted()
                        + " damage=" + (damage == null ? "none" : String.valueOf(damage))
                        + " (abort at " + abortDamageFraction + ")"
                        + " brokenByLosses=" + isBrokenByLosses()
                        + " (vanilla break: survivors < "
                        + (int) (getGroupAbortsMissionFPFraction() * 100f) + "% of spawned FP)"
                        + " | fleets alive=" + getFleets().size()
                        + " | actor=" + actorFactionId + " victim=" + victimFactionId);
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();
        removeAllCollateral();
        reportOutcome();
    }

    protected void removeAllCollateral() {
        StarSystemAPI system = getTargetSystem();
        if (system == null) return;
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            ExileHegLeagueBlockadeCollateral.removeFrom((MarketAPI) markets.get(i));
        }
    }

    /**
     * sec 3.2.1 "Up": a completed op is small upward pressure on the meter, and sec 3.2.6's
     * outcome feed reads BOTH results -- the victim when the blockade held, the actor when it did
     * not. A blockade that was driven off is not a completed op and adds no tension.
     *
     * <p>"Completed" is the payload window having actually run: the operation reached the gates
     * and was neither aborted nor broken up. A blockade that never got past travel, or that was
     * shattered while holding, failed.
     */
    protected void reportOutcome() {
        if (outcomeReported) return;
        outcomeReported = true;

        boolean completed = reachedPayload && !isAborted() && !isBrokenUp();

        // "BREAK THE BLOCKADE" (sec 14.15, ratified 2026-09-28): a blockade BROKEN through damage
        // (vanilla's live group abort, read via isBrokenByLosses; isBrokenUp is only the route-damage
        // check, corrected 2026-10-06 HL-7) pays a contribution-based victory
        // bonus from the blockaded side. Any other ending pays nothing. Once, via outcomeReported.
        if (isBrokenUp() || isBrokenByLosses()) {
            payVictoryBonus();
        }

        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director != null) {
            director.reportOperationWeariness(actorFactionId, victimFactionId, completed,
                    ExileHegLeagueDirector.OP_TYPE_BLOCKADE);
        }

        StarSystemAPI system = getTargetSystem();
        if (!completed) {
            Global.getLogger(ExileHegLeagueBlockade.class).info(
                    "Exiles Heg-League blockade ended WITHOUT holding its window in "
                            + (system != null ? system.getBaseName() : "?")
                            + " -- no tension contribution");
        } else if (director != null) {
            director.reportOperationCompleted(actorFactionId);
        }

        if (systemCooldownDays <= 0f || system == null) return;
        // END-start, not launch-start: a blockade easily outlives 60 days between travel, the
        // payload window and the return leg, so a launch-start flag would expire while the gates
        // were still shut. Shares the key with interdiction and strikes on purpose.
        system.getMemoryWithoutUpdate().set(
                ExileHegLeagueInterdictionManager.SYSTEM_COOLDOWN_KEY, true, systemCooldownDays);
    }

    /**
     * Was this blockade ABORTED because its live fleets were shot down below vanilla's group
     * threshold? (sec 14.15 build finding, 2026-09-28.) {@link #isBrokenUp()} reads route damage,
     * but vanilla writes route damage only in FGRaidAction's off-screen autoresolve -- never for a
     * blockade whose fleets are live -- so every GATES RELEASED line so far read "damage=none".
     * The real live break is vanilla's own abort in {@code FleetGroupIntel.advance}: surviving
     * fleet points below {@code totalFPSpawned * getGroupAbortsMissionFPFraction()}. This mirrors
     * that test (surviving = the fleets vanilla still counts; the ones it sent home are already
     * out of the list). Other aborts -- e.g. the source losing its military before launch, with
     * nothing ever spawned -- are not breaks.
     */
    protected boolean isBrokenByLosses() {
        if (!isAborted()) return false;
        if (totalFPSpawned <= 0f) return false;
        float live = 0f;
        List fleets = getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            if (fleet == null || !fleet.isAlive()) continue;
            live += fleet.getFleetPoints();
        }
        return live < totalFPSpawned * getGroupAbortsMissionFPFraction();
    }

    /** Size = the formation's difficulty points (sum of fleetSizes) over the configured reference
     *  (14 = the shipped [6, 4, 4]); the shared helper does the share rules and the payment. */
    protected void payVictoryBonus() {
        try {
            ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
            ExileHegLeagueConfig config = director != null ? director.getConfig() : null;
            if (config == null) return;
            float points = 0f;
            if (params != null && params.fleetSizes != null) {
                for (int i = 0; i < params.fleetSizes.size(); i++) {
                    points += ((Integer) params.fleetSizes.get(i)).intValue();
                }
            }
            float ref = config.victoryBonusRefPointsBlockade;
            float sizeRatio = (ref > 0f && points > 0f) ? points / ref : 1f;
            StarSystemAPI system = getTargetSystem();
            ExileHegLeagueBattleRewards.payVictoryBonus(config, "blockade",
                    system != null ? system.getNameWithLowercaseType() : "the system",
                    actorFactionId, victimFactionId,
                    config.victoryBonusBlockadeBase, sizeRatio,
                    victoryPlayerFp, victoryTotalLostFp, ExileHegLeagueBlockade.class);
        } catch (Exception e) {
            // A reward must never break the blockade's ending.
            Global.getLogger(ExileHegLeagueBlockade.class).warn(
                    "Exiles Heg-League blockade victory bonus failed: " + e);
        }
    }

    // ------------------------------------------------------------------
    // FleetEventListener: the blockaded side pays for dead blockaders
    // ------------------------------------------------------------------

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason,
                                               Object param) {
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner,
                                     BattleAPI battle) {
        // Pay through the ENDING window too: survivors of a collapsing op are in-system for days,
        // and mop-up kills earn the bounty like any other.
        if (isEnded()) return;
        if (fleet == null || !fleet.hasTag(ExileHegLeagueRaidIntel.RAIDER_TAG)) return;

        // VICTORY-BONUS LEDGER (sec 14.15): every battle, player or not, per fleet.
        if (battle != null) {
            float lost = ExileHegLeagueBattleRewards.fleetFpLost(fleet);
            if (lost > 0f) {
                victoryTotalLostFp += lost;
                victoryPlayerFp += lost
                        * ExileHegLeagueBattleRewards.playerCreditFraction(fleet, battle);
            }
        }

        if (battle == null || !battle.isPlayerInvolved()) return;

        // Fighting the blockade IS finding out about it.
        if (!revealed && !isEnding()) {
            revealed = true;
            setHidden(false);
            if (getPlayerVisibleTimestamp() == null) {
                setPlayerVisibleTimestamp(
                        Long.valueOf(Global.getSector().getClock().getTimestamp()));
            }
        }

        wearinessBattleSeed = ExileHegLeagueBattleRewards.applyPlayerInflictedWeariness(
                battle, actorFactionId, victimFactionId, wearinessPerFpDestroyed,
                wearinessBattleSeed, ExileHegLeagueBlockade.class);

        BountyResult result = ExileHegLeagueBattleRewards.payRaidedSideBounty(
                fleet, battle, actorFactionId, victimFactionId, bountyPerFrigate,
                ExileHegLeagueBlockade.class);
        if (result == null) return;

        // The raid intel queues payouts into its own bullet renderer; this pipeline's intel
        // listing is vanilla's, so the notice is a plain campaign message beside the reputation
        // change the rewards helper already posts.
        Global.getSector().getCampaignUI().addMessage(
                Misc.getDGSCredits(result.payment) + " received for destroying "
                        + "blockading warships", Misc.getPositiveHighlightColor());
    }
}
