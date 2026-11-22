package data.scripts.exiles;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * PHASE 3 -- THE REHEARSAL (docs/EXILES_ASHEN_CRUSADE_WAR.md §6.4, Pass 6c).
 *
 * <p>The Crusade is not harassing Tri-Tachyon here; it is <b>practising the heist</b>. The crews
 * who will later steal Culann's Corrupted Nanoforge learn the job on a live target first: the
 * Hussars hold the system's jump points ({@link AshenBlockade}) so nothing reinforces, this stage's
 * assault group cracks the orbital station, the ore goes home, and everyone withdraws together.
 * Phase 4 is the same operation at full scale against the real target -- which is why a player who
 * has watched two rehearsals can read the Culann strike the moment it begins.
 *
 * <p><b>Deliberately NOT a Black Fleet sack.</b> {@link BlackFleetAssaultStage} is the shape this
 * borrows, but the intent is opposite: the Black Fleet takes what it can and leaves a scar, while
 * the Crusade is running an exercise. So there is no {@code ExileSacked} marker, no loot fencing,
 * no multi-victim escalation, and no follow-on targets. One market, one rehearsal, withdraw.
 *
 * <p><b>Why {@code super.performRaid()} is not called.</b> Vanilla's ground raid applies Recent
 * Unrest <i>ungated</i>, and every other unrest source in Exiles routes through
 * {@link ExileUnrestGuard} -- the Gilead lesson from playtest-4, where uncapped stacking is the
 * known decivilization path. Since the ore theft is flavour text rather than a cargo mechanic
 * (author ruling: building a hauling system returns nothing playable), vanilla's raid body has
 * nothing this stage wants. It does its own two disruptions and its own guarded unrest instead.
 */
public class AshenRehearsalStage extends PirateRaidActionStage {

    /**
     * Display name for the station-attack group. See {@link #nameFleets()}.
     *
     * <p><b>The FULL player-facing string, faction included</b> (Sean, 2026-08-10). It is set
     * alongside {@code setNoFactionInName(true)}, so the engine prepends nothing and what is
     * written here is exactly what the player reads. The previous value was the bare "Hussar" --
     * literally what was asked for, but it was asked for to avoid the faction name being doubled,
     * and the result was a fleet with no faction in its name at all.
     *
     * <p>Spelling it out rather than letting the engine compose it is the deliberate choice: it
     * cannot double, and it cannot drift if the faction's {@code displayName} is ever edited.
     * (Verified: {@code exileashencrusade.faction} declares {@code "displayName":"Ashen Crusade"},
     * so the composed form would match today -- but only today.)
     */
    public static final String FLEET_NAME = "Ashen Crusade Hussar Fleet";

    /** The single market being rehearsed against -- Tibicena, then Port Tse. */
    protected String targetMarketId;

    /** Days the industries stay down. Config; §6.4 ratifies 30. */
    protected int disruptDays = 30;
    /** Recent Unrest points on success, routed through the deciv guard. */
    protected int unrestPoints = 2;

    /** Set once the rehearsal actually lands, so the outcome is reported exactly once. */
    protected boolean rehearsalDone = false;
    /** Guards double-reporting of the war-score outcome across save/load and repeat finishes. */
    protected boolean outcomeReported = false;
    /**
     * The config window ({@code rehearsal.maxDays}), kept apart from the inherited {@code maxDays}
     * because vanilla overwrites that one on arrival -- see {@link #updateRoutes()}. A
     * pre-2026-09-19 save restores this as 0, which leaves vanilla's value in force for that one
     * in-flight op.
     */
    protected float configuredMaxDays = 0f;

    public AshenRehearsalStage(RaidIntel raid, StarSystemAPI target, String targetMarketId,
                               int disruptDays, int unrestPoints, float maxDays) {
        super(raid, target);
        this.targetMarketId = targetMarketId;
        this.disruptDays = disruptDays;
        this.unrestPoints = unrestPoints;
        this.configuredMaxDays = Math.max(5f, maxDays);
        this.maxDays = this.configuredMaxDays;
    }

    /**
     * Arrival. Vanilla's {@code updateRoutes()} runs here (from {@code ActionStage.notifyStarted()})
     * and does two things this stage has to answer for, both found in the 2026-09-19 review of the
     * raid stages against {@code PirateRaidActionStage} (docs sec 14.11):
     * <ul>
     * <li>It OVERWRITES {@code maxDays} with its own 20-30 (+10-15 with a station) roll, so every
     *     rehearsal outcome line read "Nd of Nd" at vanilla's number (21-43d in the log) and no
     *     Exiles setting governed how long the group held over the target. The configured window
     *     is restored below.</li>
     * <li>It compares the group's in-system strength against the defenders' at the instant of
     *     arrival and, if the group cannot win, clears the target list and zeroes {@code maxDays}
     *     -- so {@link #updateStatus()} autoresolved and FAILED on the same tick. The log showed
     *     three {@code port_tse: FAILED after 0d of 0d}, each costing the 0.25 war-score failure
     *     the player is supposed to earn by breaking a rehearsal. That contradicted Sean's
     *     2026-08-09 ruling (no launch gate; the group "sits until maxDays and fails"). With the
     *     window restored the group now holds in-system and {@link #canBeat} is asked per tick, as
     *     the ruling intended.</li>
     * </ul>
     * The measurement line prints what vanilla saw so the next log can tell "defence grew in
     * transit" from "measured before the group was fully in-system" without guessing.
     */
    /** Prefix for the arrival measurement line; the Culann strike overrides it. */
    protected String arrivalLogLabel() {
        return "Ashen rehearsal";
    }

    @Override
    protected void updateRoutes() {
        super.updateRoutes();

        float vanillaWindow = maxDays;
        boolean vanillaAccepted = !steps.isEmpty();
        if (configuredMaxDays > 0f) {
            maxDays = configuredMaxDays;
        }

        MarketAPI target = targetMarketId != null
                ? Global.getSector().getEconomy().getMarket(targetMarketId) : null;
        FactionAPI ashen = intel.getFaction();
        float str = WarSimScript.getFactionStrength(ashen, system);
        float enemy = WarSimScript.getEnemyStrength(ashen, system);
        float station = target != null
                ? ExileStationStrength.get(target.getFaction(), system,
                        target.getPrimaryEntity())
                : 0f;
        Global.getLogger(AshenRehearsalStage.class).info(
                arrivalLogLabel() + " ARRIVED at " + system.getBaseName()
                        + " -- " + (target != null ? target.getName() : "?")
                        + ": group strength " + (int) str
                        + " vs defense " + (int) (enemy + station)
                        + " (enemy=" + (int) enemy + " + station=" + (int) station + ")"
                        + " | vanilla arrival check "
                        + (vanillaAccepted ? "accepted" : "found no beatable target")
                        + ", vanilla window " + (int) vanillaWindow
                        + "d -> window " + (int) maxDays + "d");
    }

    /**
     * Exactly one target, and only until it has been hit.
     *
     * <p>Called once from the super constructor before our fields exist -- fall back to vanilla
     * for that call, the same guard {@link BlackFleetAssaultStage#getTargets()} needs.
     */
    @Override
    protected List getTargets() {
        List all = super.getTargets();
        if (targetMarketId == null) return all;
        if (rehearsalDone) return new ArrayList();

        List result = new ArrayList();
        for (int i = 0; i < all.size(); i++) {
            MarketAPI m = (MarketAPI) all.get(i);
            if (targetMarketId.equals(m.getId())) {
                result.add(m);
                break;
            }
        }
        return result;
    }

    /** Raid-AI gate: the rehearsal target, once, and only if the group thinks it can win. */
    @Override
    public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (targetMarketId == null) return super.canRaid(fleet, market);
        if (rehearsalDone) return false;
        if (!targetMarketId.equals(market.getId())) return false;
        if (!canBeat(market)) return false;
        return super.canRaid(fleet, market);
    }

    /**
     * Vanilla's own strength comparison -- in-system Crusade strength against system enemy
     * strength plus this market's station.
     *
     * <p>Note this is a <i>tactical</i> check at the moment of attack, not the launch-time
     * feasibility veto the interdiction manager applies. Sean ruled 2026-08-09 that the rehearsal
     * has no launch feasibility gate: the targets are fixed and the operation is deliberate, so
     * the Crusade commits what the job needs and accepts the risk. What protects it from suiciding
     * is FP scaled to the target plus the damage-abort, not a refusal to launch.
     */
    protected boolean canBeat(MarketAPI market) {
        FactionAPI ashen = intel.getFaction();
        float str = WarSimScript.getFactionStrength(ashen, system);
        float enemy = WarSimScript.getEnemyStrength(ashen, system);
        float station = ExileStationStrength.get(market.getFaction(), system,
                market.getPrimaryEntity());
        float defense = enemy + station;
        return str > defense;
    }

    /**
     * How this rehearsal ended, in one line, for {@code AshenRaidIntel.reportRehearsalOutcome()}.
     *
     * <p><b>Permanent, not scaffolding.</b> The war-score verdict is reported through
     * {@code AshenWarDirector.debug()}, which writes to the campaign message feed and never to
     * {@code starsector.log} -- so before this line existed, a failed rehearsal left no
     * after-the-fact trace anywhere. Since a failure is exactly the event the war clock is scored
     * on, and Phase 4's countdown will be paced off these outcomes, it is worth one log line
     * forever.
     *
     * <p>Lives here rather than in the intel because {@code elapsed} and {@code maxDays} are
     * protected on {@code BaseRaidStage}: the stage owns the detail, the intel owns the seam.
     */
    public String getOutcomeSummary() {
        return (targetMarketId == null ? "?" : targetMarketId)
                + ": " + (rehearsalDone ? "LANDED" : "FAILED")
                + " after " + (int) elapsed + "d of " + (int) maxDays + "d";
    }

    /**
     * The rehearsal itself: two disruptions, guarded unrest, and a message. No cargo changes
     * hands in code -- the ore theft lives in the player-facing text.
     */
    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (rehearsalDone) return;
        if (targetMarketId == null || !targetMarketId.equals(market.getId())) return;

        rehearsalDone = true;

        // 1. The ore -- what they came to practise hauling out under fire.
        disrupt(market, Industries.MINING);

        // 2. The garrison. Mirrors the Culann strike's Military Base knockout at rehearsal
        //    scale; 30 days is short enough that the market is not left as prey for the Black
        //    Fleet or pirates ("wounded, not dying", §3.2a Heavy Batteries logic).
        disrupt(market, findFleetIndustryId(market));

        ExileUnrestGuard.reportExilesPressure(market,
                ExileEmergencyMeasuresConfig.pressureDays(disruptDays, unrestPoints));

        // 3. Unrest, through the guard like every other Exiles unrest source.
        if (!ExileUnrestGuard.isBattered(market)) {
            RecentUnrest.get(market).add(unrestPoints, "Ashen Crusade raid");
        }

        Global.getSector().getCampaignUI().addMessage(
                "Ashen Crusade forces have raided " + market.getName()
                        + ", stripping its ore stocks and scattering its garrison.",
                Color.ORANGE);
        Global.getLogger(AshenRehearsalStage.class).info(
                "Ashen rehearsal: struck " + market.getName() + " (" + market.getFactionId()
                        + "), mining + " + findFleetIndustryId(market) + " down " + disruptDays
                        + "d, unrest=" + (ExileUnrestGuard.isBattered(market) ? "guarded" : "" + unrestPoints));
    }

    /**
     * Take the longer of any existing disruption and this one, never the sum -- vanilla's own
     * max-semantics overload. Additive stacking is the drift-toward-permanent-shutdown path
     * (see {@code AshenSabotage.extendDisruption}).
     */
    protected void disrupt(MarketAPI market, String industryId) {
        if (industryId == null) return;
        Industry industry = market.getIndustry(industryId);
        if (industry == null) return;
        industry.setDisrupted((float) disruptDays, true);
    }

    /**
     * Whichever fleet-generating industry this market actually has -- Tibicena carries
     * {@code patrolhq}, Port Tse {@code militarybase}. Checked strongest-first so a market with
     * more than one loses the biggest.
     */
    protected String findFleetIndustryId(MarketAPI market) {
        if (market.hasIndustry(Industries.HIGHCOMMAND)) return Industries.HIGHCOMMAND;
        if (market.hasIndustry(Industries.MILITARYBASE)) return Industries.MILITARYBASE;
        if (market.hasIndustry(Industries.PATROLHQ)) return Industries.PATROLHQ;
        return null;
    }

    /**
     * Name the assault group "Hussar" (Sean, 2026-08-09).
     *
     * <p><b>Why this matters, from the live test:</b> the rehearsal fleets were indistinguishable
     * from ordinary interdiction raiders -- same {@link AshenRaidIntel}, same Lance/Fury type
     * names from {@code pickNextType()}, same two systems. And Phase-2 raids comfortably outlive
     * their phase, so destroyer raiders and cruiser rehearsal fleets were genuinely on screen
     * together. Sean read the cruisers as interdiction raiders having silently changed.
     *
     * <p>This is the force the player should learn to recognise, because it is what turns up at
     * Culann in Phase 4 -- so it gets the name, and the gate-holding blockade fleets were renamed
     * to "Crusade Blockade Fleet" / "Crusade Picket" to free it up.
     *
     * <p>Done over the route list rather than at fleet creation: {@code AssembleStage}'s
     * fleet-creation seam is not present in the shipped API sources, while {@code getRoutes()} is
     * already used by this class for straggler orders. Runs on the status tick until every fleet
     * has been seen, since routes spawn their fleets lazily -- and only while the op is ONGOING.
     */
    protected void nameFleets() {
        List routeList = getRoutes();
        if (routeList == null) return;
        for (int i = 0; i < routeList.size(); i++) {
            RouteData route = (RouteData) routeList.get(i);
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null) continue;
            if (FLEET_NAME.equals(fleet.getName())) continue;
            fleet.setName(FLEET_NAME);
            fleet.setNoFactionInName(true);
        }
    }

    /**
     * Vanilla shape, success keyed to whether the rehearsal actually landed.
     *
     * <p><b>A LANDED REHEARSAL CANNOT BE UNDONE BY ATTRITION (fixed 2026-08-10).</b> Sean saw the
     * Tibicena op finish as a <i>failure</i> on the intel screen despite the strike succeeding --
     * the log confirms `struck Tibicena ... mining + patrolhq down 30d`.
     *
     * <p>Cause: {@code abortIfNeededBasedOnFP(true)} ran first and flipped the stage to FAILURE
     * because the group had dropped below its abort FP <i>fighting the station it had just
     * beaten</i>. With the status no longer ONGOING, the branch below that calls
     * {@code finish(rehearsalDone)} never ran, so SUCCESS was never recorded. The op did the job
     * and was then marked a failure for the losses it took doing it.
     *
     * <p>Now the landed case short-circuits everything: once the rehearsal is done the op is a
     * success, and what happens to the fleets afterwards is the withdrawal, not the verdict. This
     * also protects the war score, which reads the same {@code rehearsalDone} flag.
     */
    @Override
    protected void updateStatus() {
        if (rehearsalDone) {
            if (status == RaidStageStatus.ONGOING) finish(true);
            return;
        }

        abortIfNeededBasedOnFP(true);
        if (status == RaidStageStatus.ONGOING) nameFleets();
        if (status == RaidStageStatus.ONGOING) suppressNonTargetStations();

        if (status == RaidStageStatus.ONGOING) {
            if (getTargets().isEmpty()) {
                finish(rehearsalDone);
            } else if (steps.isEmpty()) {
                // THE WINDOW-END FIX (Heg-League doc sec 14.14.11; ruled by Sean 2026-09-28 for
                // Ashen too). This used to autoresolve only when the player was OUT of spawn range
                // and otherwise finish(rehearsalDone) with no strength check -- so a group that
                // arrived overwhelmingly strong FAILED if the player happened to be within 1.6 LY as
                // the window closed (the fleets spawned that instant get no time to strike). Both
                // branches now resolve on canBeat (live WarSimScript strengths when spawned).
                // AshenCulannStrikeStage inherits this.
                if (elapsed > maxDays) {
                    boolean inSpawnRange = RouteManager.isPlayerInSpawnRange(system.getCenter());
                    Global.getLogger(AshenRehearsalStage.class).info(
                            "Ashen " + getClass().getSimpleName() + " WINDOW END at "
                                    + system.getBaseName() + ": strength "
                                    + (int) WarSimScript.getFactionStrength(intel.getFaction(), system)
                                    + " vs enemy "
                                    + (int) WarSimScript.getEnemyStrength(intel.getFaction(), system)
                                    + " (+ target station in canBeat) | player "
                                    + (inSpawnRange ? "IN" : "out of") + " spawn range | "
                                    + (int) elapsed + "d of " + (int) maxDays + "d");
                    autoresolve();
                }
            }
        }
    }

    protected void finish(boolean success) {
        status = success ? RaidStageStatus.SUCCESS : RaidStageStatus.FAILURE;
        removeMilScripts();
        if (!success) {
            giveReturnOrdersToStragglers(getRoutes());
        }
        // NOTE: NOTHING about the outcome is reported here -- not the war score, not the log line.
        // finish() is only reachable from updateStatus()'s status == ONGOING branch, and
        // abortIfNeededBasedOnFP() runs ahead of it and sets the status itself, so an op that
        // aborts on losses never arrives. Two of three rehearsals skipped it in the 2026-08-09
        // live test. Both reports now go through AshenRaidIntel.reportRehearsalOutcome(), on
        // notifyEnded(), which every path reaches. See getOutcomeSummary().
    }

    /**
     * Tell the war clock how this operation ended.
     *
     * <p>Overridable because {@link AshenCulannStrikeStage} rides the same intel seam but feeds a
     * different part of the director: a rehearsal moves the preparation meter, the Culann strike
     * opens or closes the Phase-4 branch. Polymorphism rather than an {@code instanceof} in
     * {@code AshenRaidIntel}, so a third op type never has to edit the caller.
     */
    public void reportOutcome(AshenWarDirector director) {
        director.reportRehearsal(hasLanded());
    }

    /**
     * Did the rehearsal actually land? The verdict the war clock is scored on.
     *
     * <p>Deliberately <i>not</i> the stage's {@code status}: an op can abort in travel and never
     * set a status at all, and "we never got there" is a failed rehearsal exactly like "we got
     * there and were driven off".
     */
    public boolean hasLanded() {
        return rehearsalDone;
    }

    public boolean isOutcomeReported() {
        return outcomeReported;
    }

    public void setOutcomeReported(boolean outcomeReported) {
        this.outcomeReported = outcomeReported;
    }

    /**
     * Player-out-of-range resolution. Vanilla's attrition model: if the group outguns the system
     * plus the station it lands the rehearsal, otherwise the exercise is driven off.
     */
    @Override
    protected void autoresolve() {
        List targets = getTargets();
        if (targets.isEmpty()) {
            finish(rehearsalDone);
            return;
        }
        MarketAPI market = (MarketAPI) targets.get(0);
        if (canBeat(market)) {
            performRaid(null, market);
        }
        finish(rehearsalDone);
    }

    /**
     * STATION SCOPE (bug sweep ASH-3, 2026-10-06; the Heg-League squeeze's Tigra City fix). The
     * rehearsal's gate ({@link #canBeat}) counts only the TARGET market's station, but nothing kept
     * the assault off every OTHER station in the system -- Kanta's Den in Magec, the pirate and
     * League stations in Mayasura -- and vanilla's target tally ignores station-mode fleets, so
     * station strength deters nothing. Every market station except the target's goes on the
     * do-not-attack list, refreshed each status tick (fleets spawn throughout the op and the
     * entries expire). Omicron's sleeper stations are not market stations and are untouched.
     *
     * <p>Limit (documented engine fact): do-not-attack blocks target SELECTION, not joining a
     * battle that is already under way.
     */
    protected void suppressNonTargetStations() {
        if (!suppressesNonTargetStations() || system == null) return;
        List stations = new ArrayList(); // CampaignFleetAPI
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (targetMarketId != null && targetMarketId.equals(market.getId())) continue;
            CampaignFleetAPI station = Misc.getStationFleet(market);
            if (station != null) stations.add(station);
        }
        if (stations.isEmpty()) return;
        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            CampaignFleetAPI fleet = ((RouteData) routes.get(i)).getActiveFleet();
            if (fleet == null || !fleet.isAlive()) continue;
            if (!(fleet.getAI() instanceof ModularFleetAIAPI)) continue;
            ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
            if (ai.getStrategicModule() == null) continue;
            for (int k = 0; k < stations.size(); k++) {
                ai.getStrategicModule().getDoNotAttack().add(
                        (CampaignFleetAPI) stations.get(k), AshenInterdictionStage.DO_NOT_ATTACK_DAYS);
            }
        }
    }

    /** True for the rehearsal and, since Sean's ASH-5 ruling (2026-10-06), the inheriting Culann
     *  strike. A subclass may override it to let its fleets fight every station. */
    protected boolean suppressesNonTargetStations() {
        return true;
    }
}
