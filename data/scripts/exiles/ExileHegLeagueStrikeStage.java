package data.scripts.exiles;

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
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * SURGICAL STRIKE on a military industry -- PASS 3 of docs/EXILES_HEG_LEAGUE_WAR.md sec 4.3
 * (sec 12 row 3). The RETALIATION state's punitive op, and the ESCALATION state's counter-strike.
 *
 * <p><b>Surgical means surgical (RATIFIED 2026-08-27, sec 4.1/4.2 scope split).</b> This op touches
 * <i>one market</i>: it puts that market's STRIKE PACKAGE (author ruling 2026-08-28 -- command +
 * ground defense + one war-economy industry, tension-gated; see {@link #pickIndustries}) offline
 * for a while, adds Recent Unrest, and reports the pressure to the deciv guard. It does not apply
 * a market condition, does not touch a neighbour, and does not squeeze the system. System-wide
 * pressure is the BLOCKADE's player-facing signature (Pass 4) and must not be duplicated here.
 *
 * <p><b>Never POPULATION</b> (sec 4.3). Industry selection runs through
 * {@link #isMilitaryIndustry}, copied from {@code OmicronRaidActionStage} exactly as the sec 13
 * reuse map specifies -- tag-based with the vanilla ids as belt-and-braces, because an id-only
 * check misfiled the Knights of Ludd garrison in a 2026-07-05 playtest. Disruption is verified
 * deciv-safe: it is a timer on an industry, not a hit to size or stability.
 *
 * <p><b>Structure copied from {@code AshenRehearsalStage}</b> (single fixed target, hit once,
 * tactical {@code canBeat} at the moment of attack, and the sticky-success fix). That last one is
 * load-bearing and was found the hard way on 2026-08-10: {@code abortIfNeededBasedOnFP} runs first
 * and can flip an op to FAILURE for the losses it took <i>winning</i>, which would then skip the
 * SUCCESS branch entirely. Once the strike has landed, it has landed.
 *
 * <p><b>The TARGET's station is not suppressed</b>, unlike {@link ExileHegLeagueInterdictionStage}.
 * That is the whole difference between the two ops: raiders must be kept off stations, a punitive
 * strike has to go through one. Every OTHER market's station in the system is suppressed (bug sweep
 * HL-15, 2026-10-06), because the gate never counted them.
 *
 * <p><b>Both directions.</b> Either belligerent can mount this, so the acting and struck faction
 * ids are constructor parameters and every string is composed from them.
 */
public class ExileHegLeagueStrikeStage extends PirateRaidActionStage {

    /** The single market this strike is aimed at. */
    protected String targetMarketId;
    /** The side mounting the strike. */
    protected String actorFactionId;
    /** The side that owns the struck market -- for a RETALIATION strike, the cycle's offender. */
    protected String struckFactionId;

    /** Days the chosen industries stay offline. Already includes {@code extendedDisruptMult}. */
    protected int disruptDays;
    /**
     * The DESIGN-NORMAL roll this strike's {@link #disruptDays} was derived from, before
     * {@code extendedDisruptMult} (Pass 6). Saved.
     *
     * <p>Carried as a field rather than derived as {@code disruptDays / mult} at impact time so
     * the stage never has to reach back for a live config: the mult is a tunable that can change
     * between launch and impact, and dividing by whatever it reads then would silently
     * mis-compute the relief budget of an op already in flight.
     *
     * <p><b>A pre-Pass-6 save restores this as 0</b>, which {@link #getRolledBaseDisruptDays()}
     * treats as "equal to disruptDays" -- correct, because a strike launched before this pass was
     * necessarily launched at {@code extendedDisruptMult} 1.0 (the config forbade anything else),
     * so its extension, and therefore its relief budget, is genuinely zero.
     */
    protected float rolledBaseDisruptDays = 0f;
    /** LEGACY saved fields from the pre-2026-08-28 build -- kept ONLY so XStream can restore an
     *  in-flight op from an older save; nothing reads them any more. */
    protected int maxIndustries;
    protected List industryAllowList = new ArrayList(); // String

    /** THE STRIKE PACKAGE tier, snapshotted at LAUNCH from the tension meter (author ruling
     *  2026-08-28): 1 = command bucket only, 2 = + ground defense, 3 = + one war-economy
     *  industry. A pre-ruling save restores this as 0, which clamps to 1 -- the old behaviour. */
    protected int packageTier;
    /** Bucket (c) candidates in priority order (config; already floor-validated). */
    protected List economicIndustries = new ArrayList(); // String
    /** Post-strike orbit hold, days -- the visible beat. 0 = old instant turnaround. */
    protected float orbitDays;
    /** Recent Unrest points on a landed strike. 0 = none. */
    protected int unrestPoints;
    /** {@code elapsed} at the moment the strike landed; the orbit hold counts from here. */
    protected float strikeLandedElapsed = 0f;
    /**
     * The config window ({@code strike.maxDays}), kept apart from the inherited {@code maxDays}
     * because vanilla overwrites that one -- see {@link #updateRoutes()}. A pre-2026-09-19 save
     * restores this as 0, which leaves vanilla's value in force for that one in-flight op.
     */
    protected float configuredMaxDays = 0f;

    /** Set once the strike actually lands, so it is applied and reported exactly once. */
    protected boolean strikeDone = false;
    /** Set when the group arrived but could not legally strike (target went Battered, changed
     *  hands, or had nothing military left standing). Stops the raid AI re-entering
     *  {@link #performRaid} once a tick for the rest of the window. */
    protected boolean strikeAborted = false;
    /** What went down, for the permanent outcome log line. */
    protected String struckIndustries = null;

    public ExileHegLeagueStrikeStage(RaidIntel raid, StarSystemAPI target, String targetMarketId,
                                     String actorFactionId, String struckFactionId,
                                     int disruptDays, int packageTier, List economicIndustries,
                                     float orbitDays, int unrestPoints, float maxDays,
                                     float rolledBaseDisruptDays) {
        super(raid, target);
        this.targetMarketId = targetMarketId;
        this.actorFactionId = actorFactionId;
        this.struckFactionId = struckFactionId;
        this.disruptDays = disruptDays;
        this.packageTier = packageTier < 1 ? 1 : packageTier;
        if (economicIndustries != null) {
            for (int i = 0; i < economicIndustries.size(); i++) {
                this.economicIndustries.add((String) economicIndustries.get(i));
            }
        }
        this.orbitDays = Math.max(0f, orbitDays);
        this.unrestPoints = Math.max(0, unrestPoints);
        this.configuredMaxDays = Math.max(5f, maxDays);
        this.maxDays = this.configuredMaxDays;
        this.rolledBaseDisruptDays = Math.max(0f, rolledBaseDisruptDays);
    }

    /** The design-normal base, with the pre-Pass-6-save fallback described on the field. */
    public float getRolledBaseDisruptDays() {
        if (rolledBaseDisruptDays <= 0f) return (float) disruptDays;
        return rolledBaseDisruptDays;
    }

    /**
     * Arrival. Vanilla's {@code updateRoutes()} runs here (from {@code notifyStarted()}) and does
     * two things this stage has to answer for, both found in the 2026-09-19 run-3 read:
     * <ul>
     * <li>It OVERWRITES {@code maxDays} with its own 20-30 (+10-15 with a station) roll, so the
     *     config window never applied and every outcome line read "Nd of Nd" at vanilla's number.
     *     The configured window is restored below; the offscreen autoresolve now fires at
     *     {@code strike.maxDays}, and live fleets strike as soon as {@link #canRaid} lets them.</li>
     * <li>It compares the group's in-system strength against the defenders' at the instant of
     *     arrival and, if the group cannot win, clears the target list and zeroes {@code maxDays}
     *     -- which made {@code updateStatus} autoresolve and FAIL on the same tick ("FAILED after
     *     0d of 0d" at Citadel Arcadia, a month's sailing ending without a beat). With the window
     *     restored the group holds in-system for the window instead and {@link #canBeat} is
     *     asked again at the end, by which time the defenders may have dispersed.</li>
     * </ul>
     * The measurement line prints what vanilla saw so the next log can tell "defence grew in
     * transit" from "measured before the group was fully in-system" without guessing.
     */
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
        FactionAPI actor = intel.getFaction();
        float str = WarSimScript.getFactionStrength(actor, system);
        float enemy = WarSimScript.getEnemyStrength(actor, system);
        float station = target != null
                ? ExileStationStrength.get(target.getFaction(), system,
                        target.getPrimaryEntity())
                : 0f;
        Global.getLogger(ExileHegLeagueStrikeStage.class).info(
                "Exiles " + logLabel() + " ARRIVED at " + system.getBaseName()
                        + " -- " + (target != null ? target.getName() : "?")
                        + ": group strength " + (int) str
                        + " vs defense " + (int) (enemy + station)
                        + " (enemy=" + (int) enemy + " + station=" + (int) station + ")"
                        + " | vanilla arrival check "
                        + (vanillaAccepted ? "accepted" : "found no beatable target")
                        + ", vanilla window " + (int) vanillaWindow
                        + "d -> window " + (int) maxDays + "d"
                        + " (actor=" + actorFactionId + ", struck=" + struckFactionId + ")");
    }

    /**
     * Exactly one target, and only until it has been hit.
     *
     * <p>Called once from the super constructor before our fields exist -- fall back to vanilla for
     * that call, the same guard the Ashen and Black Fleet ancestors need.
     */
    @Override
    protected List getTargets() {
        List all = super.getTargets();
        if (targetMarketId == null) return all;
        if (strikeDone || strikeAborted) return new ArrayList();

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

    /** Raid-AI gate: the strike target, once, and only if the group thinks it can win. */
    @Override
    public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (targetMarketId == null) return super.canRaid(fleet, market);
        if (strikeDone || strikeAborted) return false;
        if (!targetMarketId.equals(market.getId())) return false;
        if (!canBeat(market)) return false;
        return super.canRaid(fleet, market);
    }

    /**
     * Vanilla's own strength comparison at the moment of attack -- in-system striker strength
     * against system enemy strength plus this market's station.
     *
     * <p>This is the TACTICAL check and is separate from the manager's launch-time feasibility
     * veto. Keeping both is the sibling pattern: the launch gate decides whether to sail, this one
     * decides whether to go in. A strike that arrives and finds it cannot win mills until
     * {@code maxDays} and fails -- which is a designed outcome (the war is meant to be able to go
     * badly), not a bug.
     */
    protected boolean canBeat(MarketAPI market) {
        FactionAPI actor = intel.getFaction();
        float str = WarSimScript.getFactionStrength(actor, system);
        float enemy = WarSimScript.getEnemyStrength(actor, system);
        float station = ExileStationStrength.get(market.getFaction(), system,
                market.getPrimaryEntity());
        return str > enemy + station;
    }

    /** The op name in the ARRIVED / WINDOW END lines. Midan's strike (Pass 7) overrides it so the
     *  log report keeps its ops apart; the output here is unchanged. */
    protected String logLabel() {
        return "Heg-League strike";
    }

    /** Did the strike actually land? The verdict the tension meter is fed from. */
    public boolean hasLanded() {
        return strikeDone;
    }

    /** One line for the permanent outcome log, whatever path the op took. */
    public String getOutcomeSummary() {
        String verdict = "FAILED";
        if (strikeDone) {
            verdict = "LANDED (" + struckIndustries + ")";
        } else if (strikeAborted) {
            verdict = "ABORTED ON ARRIVAL (target no longer legal or nothing military standing)";
        }
        return (targetMarketId == null ? "?" : targetMarketId)
                + ": STRIKE " + verdict
                + " after " + (int) elapsed + "d of " + (int) maxDays + "d";
    }

    // ------------------------------------------------------------------
    // The strike
    // ------------------------------------------------------------------

    /**
     * Land the strike: disrupt the package ({@link #pickIndustries}), add Recent Unrest, report
     * the pressure, and put the group into its visible orbit hold.
     *
     * <p><b>Recent Unrest ships by author ruling 2026-08-28</b>, superseding the original
     * zero-unrest read (old deviation 6). It stays inside the sector-wide deciv rails: the
     * Battered/Emergency-Measures re-check on arrival exempts protected markets entirely, and
     * disruption itself remains a timer, never a size hit.
     *
     * <p><b>No condition, no neighbours.</b> See the class javadoc: surgical is a scope ruling.
     */
    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (strikeDone || strikeAborted) return;
        if (market == null) return;
        if (targetMarketId == null || !targetMarketId.equals(market.getId())) return;

        // Re-checked at the moment of impact rather than trusted from launch: an op outlives its
        // own target selection by weeks, and a market can go Battered, change hands or fall under
        // the refugee shield in transit.
        if (ExileProtectedFactions.isProtected(market) || ExileUnrestGuard.isBattered(market)) {
            Global.getLogger(ExileHegLeagueStrikeStage.class).info(
                    "Exiles Heg-League strike ABORTED on arrival at " + market.getName()
                            + " -- market is protected or Battered; nothing applied");
            strikeAborted = true;
            finish(false);
            return;
        }

        List picks = pickIndustries(market);
        if (picks.isEmpty()) {
            Global.getLogger(ExileHegLeagueStrikeStage.class).info(
                    "Exiles Heg-League strike reached " + market.getName()
                            + " but found no eligible military industry; nothing applied");
            strikeAborted = true;
            finish(false);
            return;
        }

        strikeDone = true;
        strikeLandedElapsed = elapsed;

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < picks.size(); i++) {
            String industryId = (String) picks.get(i);
            disruptFor(market, industryId, disruptDays);
            if (sb.length() > 0) sb.append(", ");
            sb.append(industryId);
        }
        struckIndustries = sb.toString();

        // RECENT UNREST -- author ruling 2026-08-28, superseding the zero-unrest read (old
        // deviation 6). The Battered/Emergency-Measures re-check above already exempted the
        // protected cases, and the sector-wide exilesUnrestDecivGuard caps stack-up as usual.
        if (unrestPoints > 0) {
            RecentUnrest.get(market).add(unrestPoints,
                    Misc.ucFirst(shortName(actorFactionId)) + " military strike");
        }

        ExileUnrestGuard.reportExilesPressure(market,
                ExileEmergencyMeasuresConfig.pressureDays((float) disruptDays, 0));

        // THE VISIBLE BEAT (author ruling 2026-08-28): put every route on a fresh segment
        // orbiting the struck market, so the group is SEEN holding over the target for
        // orbitDays before updateStatus() lets the op succeed and the return leg begins.
        // Harmless in the autoresolve path (no live fleets; finish() follows immediately).
        if (orbitDays > 0f && market.getPrimaryEntity() != null) {
            resetRoutes();
            List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
            for (int i = 0; i < routes.size(); i++) {
                RouteManager.RouteData route = (RouteManager.RouteData) routes.get(i);
                route.addSegment(new RouteManager.RouteSegment(
                        orbitDays + 2f, market.getPrimaryEntity()));
            }
        }

        Global.getLogger(ExileHegLeagueStrikeStage.class).info(
                "Exiles Heg-League SURGICAL STRIKE LANDED: " + market.getName()
                        + " (" + market.getFactionId() + ") by " + actorFactionId
                        + " -- tier " + packageTier + " package: " + struckIndustries
                        + " disrupted " + disruptDays + "d"
                        + (unrestPoints > 0 ? ", unrest +" + unrestPoints : "")
                        + (orbitDays > 0f ? ", holding orbit " + (int) Math.ceil(orbitDays) + "d"
                                          : ""));

        // PASS 6 (decision D80.5 item 1) -- THE RELIEF SEAM, and the last thing this method does.
        // Here, not in reportOutcome(): that fires at op END, up to 45 days later, and a strike
        // group destroyed on the way home would silently lose its victim's relief. The relief
        // budget is the EXTENSION this strike added and nothing else -- the difference between the
        // wound it wrote and the design-normal roll it came from -- so a campaign at
        // extendedDisruptMult 1.0 records a budget of zero and the manager launches nothing.
        //
        // Guarded static: a Pass-3/4/5 save with no recovery manager is a silent no-op, and this
        // stage holds no manager reference.
        float reliefDays = Math.max(0f, (float) disruptDays - getRolledBaseDisruptDays());
        ExileHegLeagueRecoveryManager.record(market, picks, reliefDays, struckFactionId);
    }

    /**
     * THE STRIKE PACKAGE (author ruling 2026-08-28): up to one industry from each unlocked
     * bucket, in bucket order.
     *
     * <ul>
     * <li><b>(a) COMMAND</b> (always): High Command &gt; Military Base &gt; Patrol HQ -- knocking
     *     out the fleet-generating industry is the meaningful act of war. Falls back to any other
     *     tag-military industry not in bucket (b)'s chain, so a mod-added garrison (the Knights of
     *     Ludd lesson) still gets classified.</li>
     * <li><b>(b) GROUND DEFENSE</b> (tier &gt;= 2): Heavy Batteries &gt; Ground Defenses.</li>
     * <li><b>(c) WAR ECONOMY</b> (tier &gt;= 3): the FIRST present entry of
     *     {@code economicIndustries} in list order (default Fuel Production &gt; Heavy
     *     Industry/Orbital Works &gt; Refining &gt; Mining). The list is floor-validated at config
     *     load; POPULATION and the never-touch industries cannot appear here.</li>
     * </ul>
     *
     * <p>Disruption is a timer, never a size or population hit, so the package stays deciv-safe
     * whatever it contains. Already-disrupted industries are skipped -- hitting them again buys
     * nothing under max-semantics.
     *
     * @return an ArrayList of industry ids, at most one per unlocked bucket.
     */
    protected List pickIndustries(MarketAPI market) {
        int tier = packageTier < 1 ? 1 : packageTier; // pre-ruling saves restore 0 -> old scope
        List result = new ArrayList(); // String

        // (a) COMMAND -- the chain first, then the tag-military fallback.
        String command = firstPresent(market, new String[] {
                Industries.HIGHCOMMAND, Industries.MILITARYBASE, Industries.PATROLHQ });
        if (command == null) {
            List industries = market.getIndustries();
            for (int i = 0; i < industries.size(); i++) {
                Industry ind = (Industry) industries.get(i);
                if (ind == null || ind.getSpec() == null) continue;
                String id = ind.getId();
                if (Industries.POPULATION.equals(id)) continue;
                if (Industries.HEAVYBATTERIES.equals(id)
                        || Industries.GROUNDDEFENSES.equals(id)) continue; // bucket (b)'s chain
                if (ind.isDisrupted()) continue;
                if (!isMilitaryIndustry(ind)) continue;
                command = id;
                break;
            }
        }
        if (command != null) result.add(command);

        // (b) GROUND DEFENSE.
        if (tier >= 2) {
            String ground = firstPresent(market, new String[] {
                    Industries.HEAVYBATTERIES, Industries.GROUNDDEFENSES });
            if (ground != null) result.add(ground);
        }

        // (c) WAR ECONOMY -- config order; the floor was enforced at load, but re-checked here so
        // a hand-edited save cannot smuggle an id past it either.
        if (tier >= 3) {
            for (int i = 0; i < economicIndustries.size(); i++) {
                String id = (String) economicIndustries.get(i);
                if (id == null || Industries.POPULATION.equals(id)) continue;
                if (ExileHegLeagueConfig.isStrikeForbiddenIndustry(id)) continue;
                Industry ind = market.getIndustry(id);
                if (ind == null || ind.isDisrupted()) continue;
                result.add(id);
                break;
            }
        }

        return result;
    }

    /** First id in {@code chain} that the market has standing and undisrupted, or null. */
    private static String firstPresent(MarketAPI market, String[] chain) {
        for (int i = 0; i < chain.length; i++) {
            Industry ind = market.getIndustry(chain[i]);
            if (ind != null && !ind.isDisrupted()) return chain[i];
        }
        return null;
    }

    /**
     * Vanilla max-semantics, per industry. <b>Never additive</b> -- summing is the
     * drift-to-permanent-shutdown path the Ashen build documents.
     */
    protected void disruptFor(MarketAPI market, String industryId, int days) {
        if (industryId == null || days <= 0) return;
        Industry industry = market.getIndustry(industryId);
        if (industry == null) return;
        industry.setDisrupted((float) days, true);
    }

    /**
     * Copied VERBATIM from {@code OmicronRaidActionStage} per the sec 13 reuse map, private static
     * as specified. Category checks are TAG-based with the vanilla ids as belt-and-suspenders so
     * CUSTOM industries classify correctly -- the 2026-07-05 playtest found an id-only check
     * dropping the Knights of Ludd garrison (a military installation) into the "other" bucket.
     */
    private static boolean isMilitaryIndustry(Industry ind) {
        String id = ind.getId();
        if (Industries.GROUNDDEFENSES.equals(id) || Industries.HEAVYBATTERIES.equals(id)
                || Industries.PATROLHQ.equals(id) || Industries.MILITARYBASE.equals(id)
                || Industries.HIGHCOMMAND.equals(id)) return true;
        return ind.getSpec().hasTag("military") || ind.getSpec().hasTag("command")
                || ind.getSpec().hasTag("patrol") || ind.getSpec().hasTag("grounddefenses");
    }

    // ------------------------------------------------------------------
    // Lifecycle (the AshenRehearsalStage shape, including the sticky-success fix)
    // ------------------------------------------------------------------

    @Override
    protected void updateStatus() {
        // Bug sweep HL-15 (2026-10-06): keep the strike off every OTHER market's station, also
        // while it holds orbit after landing. Refreshed each tick (spawns, expiring entries).
        if (status == RaidStageStatus.ONGOING) suppressNonTargetStations();
        if (strikeDone) {
            // The visible beat: hold SUCCESS back until the orbit window has run, so the group is
            // seen over the target instead of turning for home the moment the charges land. The
            // autoresolve path bypasses this (finish() is called there directly; no one is
            // watching). Losses during the hold cannot un-land a landed strike (sticky success).
            if (status == RaidStageStatus.ONGOING
                    && elapsed >= strikeLandedElapsed + orbitDays) {
                finish(true);
            }
            return;
        }
        // finish(false) already ran at the moment of the abort; nothing further to decide.
        if (strikeAborted) return;

        abortIfNeededBasedOnFP(true);

        if (status == RaidStageStatus.ONGOING) {
            if (getTargets().isEmpty()) {
                finish(false);
            } else if (steps.isEmpty()) {
                // THE WINDOW-END FIX (war doc sec 14.14.10, ruled by Sean 2026-09-28). This used to
                // autoresolve only when the player was OUT of spawn range, and otherwise
                // finish(false) with no strength check at all -- so a strike that arrived at 1723 vs
                // 281 (Yama -> Laicaille, 2026-09-28 run) FAILED because the player flew within
                // 1.6 LY as the window closed, and the fleets spawned that instant got no time to
                // strike. The outcome depended on where the player was at one tick. Now both
                // branches resolve on the same vanilla strength comparison (canBeat, on live
                // WarSimScript strengths when fleets are spawned), and the resolution is logged.
                if (elapsed > maxDays) {
                    boolean inSpawnRange = RouteManager.isPlayerInSpawnRange(system.getCenter());
                    logWindowResolution(inSpawnRange);
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
        // NOTHING is reported here. finish() is only reachable from the status == ONGOING branch,
        // and abortIfNeededBasedOnFP() runs ahead of it and sets the status itself, so an op that
        // aborts on losses never arrives (the 2026-08-09 Ashen finding -- two of three ops skipped
        // it). The outcome goes through ExileHegLeagueStrikeIntel.reportOutcome(), on
        // notifyEnded(), which every path reaches.
    }

    /**
     * THE RESOLUTION LINE (war doc sec 14.14.10): what the window-end strength check saw, and
     * where the player was. Once per strike that reaches the end of its window without landing.
     */
    protected void logWindowResolution(boolean inSpawnRange) {
        List targets = getTargets();
        MarketAPI market = targets.isEmpty() ? null : (MarketAPI) targets.get(0);
        FactionAPI actor = intel.getFaction();
        float str = WarSimScript.getFactionStrength(actor, system);
        float enemy = WarSimScript.getEnemyStrength(actor, system);
        float station = market != null
                ? ExileStationStrength.get(market.getFaction(), system,
                        market.getPrimaryEntity())
                : 0f;
        Global.getLogger(ExileHegLeagueStrikeStage.class).info(
                "Exiles " + logLabel() + " WINDOW END at " + system.getBaseName() + " -- "
                        + (market != null ? market.getName() : "?")
                        + ": group strength " + (int) str
                        + " vs defense " + (int) (enemy + station)
                        + " (enemy=" + (int) enemy + " + station=" + (int) station + ")"
                        + " -> " + (str > enemy + station ? "can beat, striking" : "cannot beat")
                        + " | player " + (inSpawnRange ? "IN" : "out of") + " spawn range"
                        + " | " + (int) elapsed + "d of " + (int) maxDays + "d"
                        + " (actor=" + actorFactionId + ", struck=" + struckFactionId + ")");
    }

    /** Window-end resolution in BOTH player-range branches since the sec 14.14.10 fix: vanilla's
     *  strength comparison ({@link #canBeat}), then the strike if it wins. */
    @Override
    protected void autoresolve() {
        List targets = getTargets();
        if (targets.isEmpty()) {
            finish(false);
            return;
        }
        MarketAPI market = (MarketAPI) targets.get(0);
        if (canBeat(market)) {
            performRaid(null, market);
        }
        finish(strikeDone);
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    protected String shortName(String factionId) {
        FactionAPI faction = factionId != null
                ? Global.getSector().getFaction(factionId) : null;
        if (faction == null) return String.valueOf(factionId);
        return faction.getDisplayName();
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        float opad = 10f;
        String actor = shortName(actorFactionId);
        String struck = shortName(struckFactionId);
        MarketAPI target = targetMarketId == null
                ? null : Global.getSector().getEconomy().getMarket(targetMarketId);
        String targetName = target != null ? target.getName() : "the target";

        if (status == RaidStageStatus.FAILURE) {
            info.addPara(actor + " forces failed to press home their strike on " + targetName
                    + ". The operation is over.", opad);
        } else if (status == RaidStageStatus.SUCCESS) {
            info.addPara(actor + " forces struck " + targetName + ", crippling its military"
                    + " installations. " + struck + " has lost the use of them for months.", opad);
        } else if (curr == index) {
            info.addPara(actor + " forces are moving against " + struck + " military"
                    + " installations at " + targetName + ".", opad);
        }
    }

    @Override
    public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
        return "striking military installations at " + market.getName();
    }

    @Override
    public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
        return "moving to strike " + market.getName();
    }

    @Override
    public String getRaidInSystemText(CampaignFleetAPI fleet) {
        return "conducting a punitive strike";
    }

    @Override
    public String getRaidDefaultText(CampaignFleetAPI fleet) {
        return "conducting a punitive strike";
    }

    /**
     * STATION SCOPE (bug sweep HL-15, 2026-10-06; same fix as the squeeze's Tigra City one). The
     * strike gate and the arrival line count only the TARGET market's station, so every other
     * market station in the system goes on the do-not-attack list. The target's own station stays
     * fair game: a punitive strike has to go through it. Limit: do-not-attack blocks target
     * selection, not joining a battle already under way.
     */
    protected void suppressNonTargetStations() {
        if (system == null) return;
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
            CampaignFleetAPI fleet = ((RouteManager.RouteData) routes.get(i)).getActiveFleet();
            if (fleet == null || !fleet.isAlive()) continue;
            if (!(fleet.getAI() instanceof com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI)) continue;
            com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI ai =
                    (com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI) fleet.getAI();
            if (ai.getStrategicModule() == null) continue;
            for (int k = 0; k < stations.size(); k++) {
                ai.getStrategicModule().getDoNotAttack().add((CampaignFleetAPI) stations.get(k),
                        ExileHegLeagueInterdictionStage.DO_NOT_ATTACK_DAYS);
            }
        }
    }
}
