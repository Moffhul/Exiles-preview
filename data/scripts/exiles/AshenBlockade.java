package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript.MilitaryResponseParams;
import com.fs.starfarer.api.impl.campaign.NPCHassler;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.ActionType;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.intel.group.BlockadeFGI;
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction;
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction.FGBlockadeParams;

/**
 * The Hussar element of the Ashen Crusade's Phase-3 Rehearsal
 * (docs/EXILES_ASHEN_CRUSADE_WAR.md §6.4; built as Pass 6a, repurposed by Pass 6c).
 *
 * <p>A Hussar Blockade Fleet and its pickets accompany the rehearsal op, <b>hold the target
 * system's jump points</b> so Tri-Tachyon cannot reinforce, and withdraw when the op does. In
 * Phase 4 the same element does the same job at Culann, which is the point: the player who
 * watches two rehearsals learns to read the Crusade's order of battle before the real strike.
 *
 * <p><b>Pass 6c changed one thing:</b> {@link #createPayloadAction()} now returns the default
 * {@code FGBlockadeAction} (hold the gates) instead of {@code FGBlockadePlanetAction} (park on
 * the market, which is vanilla's siege action). See that method for why, and note that the
 * economic effect is identical either way.
 *
 * <p><b>This class is deliberately thin.</b> Vanilla's {@link BlockadeFGI} already owns the entire
 * lifecycle -- gather, travel, blockade, return -- plus fleet creation, intel, and the
 * {@code Blockaded} market condition with its apply/unapply on both {@code notifyEnding()} and
 * every {@code advance()} where the payload action is not current. Building any of that ourselves
 * would be strictly worse and less compatible. What is genuinely ours is three overrides.
 *
 * <p><b>NPC-vs-NPC is a supported vanilla mode, verified before this was written</b> (§12 O3):
 * {@code FleetGroupIntel} declares {@code protected abstract boolean isPlayerTargeted()} and
 * {@code GenericRaidFGI} implements it as {@code return params.playerTargeted}, gating intel
 * updates and whether {@code WarSimScript.getEnemyStrength} counts the player's fleet. The engine
 * author's own comment on the {@code GenericRaidParams} constructor -- <i>"now supported: making
 * fleets hostile to arbitrary factions"</i> -- settles it. Both shipped subclasses
 * ({@code PerseanLeagueBlockade}, {@code KnightsOfLuddTakeoverExpedition}) happen to target the
 * player, so this is supported-but-unexercised territory; watch the first live test accordingly.
 *
 * <p><b>The accessibility penalty is vanilla's 0.6 default and is NOT flat.</b>
 * {@code BlockadeFGI.getAccessibilityPenalty()} scales it by {@code getRelativeFGStrength}: below
 * 0.75x the system's defenders it returns 0, below 1.25x it returns half, above that the full
 * value. So the blockade is only fully effective where the Crusade decisively outguns the
 * defenders -- which is the case the fiction wants to feel brutal. Stacked with Crusade Harassment
 * (-50%) the ceiling is -110% (Sean, 2026-08-05: leave vanilla's number, fewer deviations).
 *
 * <p>Note a vanilla display quirk worth not mistaking for our bug: {@code addAssessmentSection}
 * prints the RAW configured penalty in the intel while the {@code Blockaded} condition tooltip
 * prints the SCALED one, so the intel over-promises against a marginal blockade.
 */
public class AshenBlockade extends BlockadeFGI implements ExileBlockadeIntel {

    public static final String FACTION_ID = "exileashencrusade";

    /** Fleet size at or above which a fleet is the named anchor rather than a picket. Derived
     *  from the configured fleet sizes at construction, so retuning sizes cannot silently leave
     *  the blockade with no anchor or with three of them. */
    protected int anchorSize = Integer.MAX_VALUE;
    /** Guards against two fleets both claiming the anchor name when sizes tie. */
    protected boolean anchorNamed = false;
    /** Suppress every market station in the system, not only the target faction's (ASH-4).
     *  Always set true by the launcher since ASH-5; false (the pre-2026-10-06 scope) on old saves. */
    protected boolean suppressEveryStation = false;

    public void setSuppressEveryStation(boolean suppressEveryStation) {
        this.suppressEveryStation = suppressEveryStation;
    }
    /** Route-damage fraction at which the blockade breaks off. Set from config at launch; 0.67
     *  since 2026-09-28 (Sean's ruling). CORRECTION: vanilla DOES abort a live group below 33%
     *  surviving FP (FleetGroupIntel.advance) -- only {@code shouldAbort()} returns false flat --
     *  and that live abort is what really ends an on-screen blockade, since vanilla writes route
     *  damage only in FGRaidAction's off-screen autoresolve. 0.67 makes the two agree. */
    protected float abortDamageFraction = 0.67f;

    /** Keep blockade fleets off the station they are blockading. Set false to compare against
     *  stock behaviour. Mirrors {@code AshenInterdictionStage.suppressStationAttacks}. */
    public static boolean suppressStationAttacks = true;
    /** Refresh window for the do-not-attack entries; they expire, so they are re-added on the
     *  periodic tick rather than set once. Same value the interdiction stage uses. */
    public static float DO_NOT_ATTACK_DAYS = 5f;

    // Pass-6a diagnostics REMOVED 2026-08-09 once the mechanism was confirmed working across
    // live tests 2-4 (docs sec 14.8). They existed because vanilla's BlockadeFGI logs nothing,
    // so a silent launch was indistinguishable from an early self-abort. That question is
    // answered; the trace is in the doc.

    public AshenBlockade(GenericRaidParams params, FGBlockadeParams blockadeParams) {
        super(params, blockadeParams);

        if (params.fleetSizes != null) {
            for (int i = 0; i < params.fleetSizes.size(); i++) {
                int size = ((Integer) params.fleetSizes.get(i)).intValue();
                if (anchorSize == Integer.MAX_VALUE || size > anchorSize) {
                    anchorSize = size;
                }
            }
        }
    }

    /**
     * Hold the system's jump points -- vanilla's actual blockade behaviour.
     *
     * <p><b>Changed 2026-08-09 (Pass 6c).</b> This returned {@link FGBlockadePlanetAction}, which
     * sets {@code primary = specificMarket.getPrimaryEntity()} so the fleets sit on the market.
     * That is vanilla's <i>siege</i> action -- its only vanilla user is a takeover -- and it was
     * the source of every behaviour problem in docs sec 14.8: fleets milled near the planet
     * instead of orbiting, broke off to pursue, and dragged the station into fights they then won.
     *
     * <p>The default {@code FGBlockadeAction} builds its points from
     * {@code where.getEntities(JumpPointAPI.class)} and holds the gates, which is what the
     * rehearsal wants: the Hussars cut Tri-Tachyon's reinforcement routes while the assault group
     * works the station.
     *
     * <p><b>The economic effect is unchanged.</b> {@code BlockadeFGI.applyBlockadeCondition()}
     * iterates {@code Misc.getMarketsInLocation(getTargetSystem(), targetFaction)} and never
     * consults {@code specificMarket}, so the Blockaded penalty was always system-wide regardless
     * of which action ran. {@code specificMarket} only ever decided where the fleets parked --
     * which means the "station denial vs system denial" question sec 6.4 was built around did not
     * exist economically. Verified against BaseIndustry/BlockadeFGI source, 2026-08-09.
     */
    protected GenericPayloadAction createPayloadAction() {
        return new SpreadBlockadeAction(blockadeParams, params.payloadDays);
    }

    /**
     * Hold EVERY gate, not mostly one.
     *
     * <p>Vanilla's {@code orderFleetMovements()} builds a {@code MilitaryResponseScript} for each
     * jump point but weights them unevenly:
     *
     * <pre>
     *   float fPrimary    = 1f;
     *   float fNonPrimary = 1f / blockadePoints.size();
     * </pre>
     *
     * With Magec's and Mayasura's two jump points that is 1.0 against 0.5, so the fleets
     * concentrate on the primary gate and the other is effectively open. Sean observed exactly
     * that in the 2026-08-09 live test -- one blockade fleet visible where the fiction wants a
     * gate each.
     *
     * <p>Vanilla's weighting is right for <i>its</i> use: a player-directed blockade wants mass at
     * the likely approach. A rehearsal for cutting Culann's reinforcement routes wants coverage,
     * so every point gets the full response fraction and the fleets distribute.
     *
     * <p>Named static class, never anonymous -- this ends up in saved state (CLAUDE.md §7).
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
     * Name the anchor, and give the pickets the job of turning traffic away.
     *
     * <p>Ruling F cut "other fleets will be turned away" on build cost -- physically intercepting
     * NPC fleets was judged too expensive and too janky. That cost turned out to be one line:
     * {@link NPCHassler} is vanilla's own implementation, used by
     * {@code KnightsOfLuddTakeoverExpedition} on exactly this kind of picket. Re-authorised by Sean
     * 2026-08-05 on that basis.
     *
     * <p>The hassler goes on pickets only, never the anchor: the anchor's job is to sit on the
     * market so the blockade reads as a blockade (and so Pass 6b has something to intercept
     * against), while the pickets provide the visible harassment.
     */
    protected void configureFleet(int size, CampaignFleetAPI fleet) {
        super.configureFleet(size, fleet);
        if (fleet == null) return;

        // RENAMED 2026-08-09. "Hussar" moved to the station-attack group (Sean's call), so these
        // gate-holders needed their own identity. Plain "Blockade", NOT "Crusade Blockade" --
        // the faction is already the Ashen Crusade, so that read as a stutter. The anchor/picket
        // split is kept: it is what anchorSize exists to decide, and only pickets get NPCHassler.
        //
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

        registerForBounty(fleet);
    }

    /**
     * Put the gate-holders on Tri-Tachyon's bounty, same as the assault group.
     *
     * <p><b>Why this is needed at all (Sean, 2026-08-10).</b> The bounty is paid by
     * {@link AshenRaidIntel#reportBattleOccurred}, which fires only for fleets that (a) registered
     * it as a {@code FleetEventListener} and (b) carry {@link AshenRaidIntel#RAIDER_TAG}. The raid
     * intel does both in its own {@code spawnFleet()}, but these fleets are spawned by vanilla's
     * {@code BlockadeFGI} on a different pipeline -- so killing a Blockade Picket paid nothing.
     * One operation should pay one way throughout.
     *
     * <p>Safe to attach a fleet the raid intel does not own: that handler holds no per-fleet state
     * and does no route lookup -- it reads {@code Misc.getSnapshotMembersLost(fleet)} and pays on
     * hull sizes. The one side effect is deliberate and correct: fighting the pickets reveals the
     * operation, exactly as fighting the Hussars does.
     *
     * <p>Looked up live rather than stored, so this class still holds no reference to the raid
     * (see {@link #shouldAbort()} for the same reasoning).
     */
    protected void registerForBounty(CampaignFleetAPI fleet) {
        AshenRaidIntel companion = findCompanionRaid();
        if (companion == null) return;
        fleet.addTag(AshenRaidIntel.RAIDER_TAG);
        fleet.addEventListener(companion);
    }

    protected void periodicUpdate() {
        super.periodicUpdate();
        suppressStationTargeting();
    }

    /** Withdrawal: strip the pickets' hassler and keep the order alive (2026-10-07 test run, the
     *  Mayasura picket that never left -- see {@link ExileReturnOrderWatchdog}). */
    @Override
    protected void giveReturnAssignments(CampaignFleetAPI fleet) {
        super.giveReturnAssignments(fleet);
        ExileReturnOrderWatchdog.attach(fleet, returnLocation, "Ashen blockade");
    }

    /**
     * Withdraw when the interdiction raid does.
     *
     * <p>Author ruling 2026-08-05: the blockade fleets sit still on a market and would be easy prey
     * without the raid screening them, so they leave together.
     *
     * <p>Deliberately a PULL, not a push. Looking the companion raid up through the intel manager
     * each time means this class holds no reference to the raid, so it cannot retain a dead intel,
     * cannot leak, and self-corrects across save/load -- the same reasoning that made
     * {@code hasLiveOpIn} an intel query rather than a bookkeeping list.
     */
    protected boolean shouldAbort() {
        if (super.shouldAbort()) return true;
        if (isBrokenUp()) return true;
        if (!hasCompanionRaid()) return true;
        return false;
    }

    public void setAbortDamageFraction(float abortDamageFraction) {
        this.abortDamageFraction = abortDamageFraction;
    }

    /**
     * True once the blockading force has taken more than {@link #abortDamageFraction} route
     * damage (originally §6.4's "orbits until reduced below 50% strength"; 0.67 since 2026-09-28).
     *
     * <p>CORRECTED 2026-09-28: {@code FleetGroupIntel.shouldAbort()} returns false flat, but
     * vanilla separately aborts a LIVE group below 33% surviving FP in {@code advance()} -- the
     * break that actually happens on screen. This check reads
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
     * Keep the blockade fleets off the Tri-Tachyon station.
     *
     * <p><b>Belt-and-braces since Pass 6c (2026-08-09).</b> This was load-bearing under
     * {@code FGBlockadePlanetAction}, which parked the fleets on the market and engaged it: live
     * test 2026-08-06 had them arrive and immediately attack Port Tse's station. Now that
     * {@link #createPayloadAction()} returns the default {@code FGBlockadeAction}, the Hussars
     * hold jump points instead and have no reason to close on the station at all.
     *
     * <p><b>Kept deliberately.</b> It is written, tested, and costs one pass over a handful of
     * fleets on a 0.1-0.3 day tick. More importantly the roles have <i>diverged</i>: in the
     * rehearsal the assault group is <i>supposed</i> to crack the station while these fleets are
     * not, so the suppression now marks a real distinction rather than papering over a wrong
     * base class. Set {@code suppressStationAttacks} false to compare.
     *
     * <p>{@code AshenInterdictionStage}'s own suppression does not cover these fleets --- it
     * scopes to {@code intel.getRouteSourceId()}, the raid's routes, and the blockade is a
     * separate FGI with its own. Entries expire, so they are refreshed on the periodic tick.
     */
    protected void suppressStationTargeting() {
        if (!suppressStationAttacks) return;
        StarSystemAPI system = getTargetSystem();
        if (system == null) return;

        List stations = new ArrayList(); // CampaignFleetAPI
        // EVERY station in the system (bug sweep ASH-4/ASH-5, 2026-10-06), as AshenInterdictionStage
        // was widened on 2026-08-10: the target-faction filter left Kanta's Den in Magec, the
        // pirate/League stations in Mayasura and Hybrasil's independent and pirate stations open
        // to the pickets. Sean ruled (ASH-5) that the Phase-4 force spares them too. The launcher
        // now always sets suppressEveryStation; the false branch survives only for older saves.
        List markets = suppressEveryStation
                ? Misc.getMarketsInLocation(system)
                : Misc.getMarketsInLocation(system, blockadeParams.targetFaction);
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
    // Source concealment (2026-08-09) -- THIS class was the Providence leak
    // ------------------------------------------------------------------

    /**
     * Never point the starmap at Providence.
     *
     * <p><b>This class, not the rehearsal intel, was the leak Sean reported.</b> The two launch
     * together so they read as one event, but {@link AshenRaidIntel} is clean:
     * {@code RaidIntel.getMapLocation()} returns the <i>target</i> system's hyperspace anchor, its
     * {@code getArrowData()} bails on {@code !isSourceKnown()}, and {@code AssembleStage} prints
     * "assembling at an unknown location". All verified in the API sources.
     *
     * <p>{@code GenericRaidFGI.getMapLocation()} does this instead:
     *
     * <pre>
     *   if (PREPARE_ACTION.equals(getCurrentAction().getId()) || getDelayRemaining() &gt; 0)
     *       return getSource();
     *   return getDestination();
     * </pre>
     *
     * <p>{@code params.source} is an Ashen market in Providence, so for the whole prepare window
     * the blockade intel plants a marker on the Crusade's home system. Worse,
     * {@code FleetGroupIntel.getArrowData()} draws an arrow <i>from source to destination</i> --
     * a line on the starmap from Providence to the target, which is exactly the "attack from a
     * pirate station" presentation Sean described.
     *
     * <p>Both are overridden: the marker follows the destination at every stage, and no arrow is
     * drawn at all. Concealing where the Crusade operates from is recon the player is meant to
     * earn -- the same reason {@code HiddenSourceAssembleStage.isSourceKnown()} returns false, and
     * the same reason §7.3 ruling H.c makes finding Providence a Tri-Tachyon quest.
     */
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        SectorEntityToken destination = getDestination();
        if (destination != null) return destination;
        return super.getMapLocation(map);
    }

    /** No source-to-target arrow: it would draw a line straight to Providence. */
    @Override
    public List getArrowData(SectorMapAPI map) {
        return null;
    }

    /** Public view of the blockaded system. {@code GenericRaidFGI.getTargetSystem()} is protected,
     *  so {@link AshenRaidIntel#hasCompanionBlockade()} -- which is not in this hierarchy --
     *  cannot reach it.
     *
     *  <p>Pass 4 of the Heg-League war (2026-09-20) made this the single method of
     *  {@link ExileBlockadeIntel}, so {@link ExileLiveOps} can keep refugee traffic out of a
     *  blockaded system without importing any director's class. The method and its behaviour are
     *  unchanged -- declaring the interface is the whole edit. */
    public StarSystemAPI getBlockadedSystem() {
        return getTargetSystem();
    }

    /** True while an Ashen op is still live in the system being blockaded. */
    protected boolean hasCompanionRaid() {
        return findCompanionRaid() != null;
    }

    /** The live Ashen op this blockade is escorting, or null. Looked up through the intel manager
     *  every time so this class holds no reference that could outlive the op. */
    protected AshenRaidIntel findCompanionRaid() {
        StarSystemAPI system = getTargetSystem();
        if (system == null) return null;

        List ops = Global.getSector().getIntelManager().getIntel(AshenRaidIntel.class);
        for (int i = 0; i < ops.size(); i++) {
            AshenRaidIntel op = (AshenRaidIntel) ops.get(i);
            if (op.isEnded() || op.isEnding()) continue;
            if (op.getSystem() == system) return op;
        }
        return null;
    }
}
