package data.scripts.exiles;

import java.util.List;
import java.util.Random;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * MIDAN'S FALSE-FLAG LADDER -- Pass 7 of the Hegemony-League war (war doc sec 6.1; build plan
 * docs/EXILES_PASS7_BUILD_PLAN.md). Lives inside {@link ExileHegLeagueDirector} and is advanced only
 * while the war is active, so the war's off switch pauses Midan too (the HL-1 precedent).
 *
 * <p><b>STEP 7d-2: LAUNCHING.</b> This class tracks readiness, scores the opportunity, measures
 * the Strike Force against all three targets, and -- when the score clears the bar and every hard
 * rail holds -- launches {@link ExileHegLeagueMidanStrikeIntel}. Only rungs up to
 * {@code strike.maxLaunchRung} launch (rungs 1-2 since 7e-1; escort caps per rung, 2/4/4 ruled 2026-10-31); later rungs keep the 7c "WOULD LAUNCH" line
 * until 7e builds their payloads. When a strike ends the director calls
 * {@link #onStrikeResolved}: the rung is spent, Midan rebuilds, and the breadcrumb keys are written.
 *
 * <p><b>The ruled design it implements (2026-10-19):</b>
 * <ul>
 * <li><b>Readiness:</b> ready for the first rung after {@code firstReadyDays}; after each rung, a
 * rebuild ({@code rebuildDays}, or {@code rebuildFailedDays} after a failure). Rung progression
 * arrives with launching in 7d; in 7c the ladder stays on rung 1.</li>
 * <li><b>Opportunity score, 0..1:</b> <i>means</i> (the target's defence below its running
 * baseline), <i>distraction</i> (live Hegemony offensive ops), <i>cover</i> (a crisis underway).
 * It must clear a <b>bar that lowers the longer Midan has been ready</b> -- impatience built in, so
 * the ladder can never stall forever on a quiet war.</li>
 * <li><b>Feasibility (hard rail):</b> fleet strength &gt;= {@code strikeMarginMult} x the target
 * system's defence (live patrol strength hostile to the League + the target's own station, the
 * squeeze gate's measure), with the fleet's FP under {@code fpCeiling}.</li>
 * </ul>
 *
 * <p><b>Fleet strength since step 7d-1 (still LOG-ONLY):</b> at every MEASURE the Strike Force is
 * composed in memory by {@link ExileHegLeagueMidanComposer} -- the Ajax's fleet and one escort
 * group -- and measured with vanilla's {@code getEffectiveStrength()} (officers, S-mods, CR), then
 * dropped. Feasibility = the main fleet plus at most {@code escortCap} escorts reaches the margin
 * (sizing ruled 2026-10-25). In 7c this was {@code fleetFpEstimate} through
 * {@code Misc.getAdjustedStrength}; that estimate now only stands in if the composer fails.
 *
 * <p><b>Saved state:</b> primitives, one float array, and -- since the sneak-attack ruling
 * (2026-10-30) -- the one live strike intel ({@link #activeStrike}), which is no longer findable
 * in the intel manager before its distress call. No fleet or market references are kept.
 *
 * <p><b>Quest hook (2026-10-30):</b> global memory {@code $exiles_midan_rungN_hold} stops the
 * director launching rung N on its own; {@code $exiles_midan_rungN_trigger} launches it at the next
 * daily check with {@code strike.questPrepDays} of prep (hard rails still apply), then is cleared.
 * Both can be set from rules.csv with vanilla commands ({@code $global.exiles_midan_rung1_trigger}).
 *
 * <p><b>Log lines</b> (all start "Exiles Midan"): {@code started}, {@code READY}, {@code COMPOSED}
 * and {@code MEASURE} (every {@code measureIntervalDays}; MEASURE one per target), {@code SCORE}
 * (same cadence, current rung), {@code WOULD LAUNCH} / {@code BLOCKED} (when the score clears the
 * bar; at most every 30 days).
 */
public class ExileHegLeagueMidanModule {

    /** Minimum days between two WOULD LAUNCH / BLOCKED lines (the condition can hold for weeks). */
    protected static final float WOULD_LAUNCH_LOG_GAP_DAYS = 30f;

    // ------------------------------------------------------------------
    // Saved state
    // ------------------------------------------------------------------

    /** The next rung to fire, 0-based into {@code config.midanTargets}. Stays 0 in step 7c. */
    protected int rungIndex = 0;
    /** War-clock day at which Midan is ready for {@link #rungIndex}; -1 = not yet scheduled. */
    protected float readyAtDay = -1f;
    /** Per target: the running "normal defence" the means term compares against; -1 = unset. */
    protected float[] baseline = null;
    protected float daysSinceMeasure = 9999f;
    protected float daysSinceCheck = 9999f;
    protected float lastWouldLaunchDay = -9999f;
    protected boolean readyLogged = false;
    /**
     * Step 7d-1: the Strike Force as vanilla measures it, refreshed at every MEASURE by composing
     * the fleets in memory (never spawned). -1 = not measured yet; then the 7c estimate stands in.
     */
    protected float mainStrength = -1f;
    protected float escortStrength = -1f;
    protected int mainFp = 0;
    protected int escortFp = 0;
    /** Step 7d-2: launches so far, and how many lost their Ajax (the protection-(b) measurement). */
    protected int launches = 0;
    protected int ajaxLosses = 0;
    /**
     * The live strike, held here because since the sneak-attack ruling (2026-10-30) it is NOT in
     * the intel manager until its distress call. The intel is a sector script either way, so this
     * adds no save surface of its own. Null when no strike is live.
     */
    protected ExileHegLeagueMidanStrikeIntel activeStrike = null;
    /**
     * Step 7g (Sean, 2026-11-07): the "no one left" hold. True while the League or the Hegemony
     * holds no market, or Midan holds none in its home system. Evaluated at the decision points
     * (the launch check, the directive writer) and at the monthly MEASURE for the transition line;
     * never on a timer of its own. "Stops for good" (war doc sec 6.1) reads as "while it is true":
     * a Nexerelin faction respawn lifts the hold by itself.
     */
    protected boolean holding = false;
    protected String holdReason = null;

    /**
     * Called by the director once per tick while the war is active.
     *
     * @param elapsed days since the last tick.
     * @param nowDay  the war clock: days since the director started.
     */
    public void advance(ExileHegLeagueDirector director, ExileHegLeagueConfig config,
                        float elapsed, float nowDay) {
        if (config == null || !config.midanEnabled) return;
        List targets = config.midanTargets;
        if (targets == null || targets.isEmpty()) return;

        if (baseline == null || baseline.length != targets.size()) {
            baseline = new float[targets.size()];
            for (int i = 0; i < baseline.length; i++) baseline[i] = -1f;
        }
        if (readyAtDay < 0f) {
            if (config.testMidanStartRung > 1) {
                rungIndex = Math.min(config.testMidanStartRung, targets.size()) - 1;
                log("Exiles Midan: TEST PIN midanStartRung -> the ladder starts at rung " + (rungIndex + 1));
            }
            readyAtDay = nowDay + config.midanFirstReadyDays;
            log("Exiles Midan: module started (step 7e-1: rungs up to " + config.midanMaxLaunchRung
                    + " launch) | day " + (int) nowDay
                    + " | rung " + (rungIndex + 1) + " (" + targets.get(rungIndex) + ") ready at day "
                    + (int) readyAtDay);
        }

        daysSinceMeasure += elapsed;
        daysSinceCheck += elapsed;
        boolean measureNow = daysSinceMeasure >= config.midanMeasureIntervalDays;
        boolean checkNow = daysSinceCheck >= 1f;
        if (!measureNow && !checkNow) return;

        FactionAPI league = Global.getSector().getFaction(Factions.PERSEAN);
        if (league == null) return;

        // <= 0, not < 0: a save from before 7d-1 loads these fields as 0 (initialisers do not run).
        if (measureNow || mainStrength <= 0f) {
            composeAndMeasure(config, nowDay);
        }
        if (measureNow) {
            daysSinceMeasure = 0f;
            measureAllTargets(config, league, nowDay);
            refreshHold(config, nowDay); // visibility only: logs HOLD / RESUMES transitions
        }
        if (checkNow) {
            daysSinceCheck = 0f;
            checkOpportunity(director, config, league, nowDay, measureNow);
        }
    }

    // ------------------------------------------------------------------
    // The Strike Force -- composed in memory, measured, dropped (7d-1)
    // ------------------------------------------------------------------

    /**
     * Builds the main fleet and one escort exactly as 7d-2 will launch them, reads vanilla's
     * {@code getEffectiveStrength()} for each, and keeps only the numbers. If the main fleet cannot
     * be built (a missing variant), the 7c estimate stands in and the line says so.
     */
    protected void composeAndMeasure(ExileHegLeagueConfig config, float nowDay) {
        Random random = new Random();
        CampaignFleetAPI main = ExileHegLeagueMidanComposer.composeMainFleet(config, random);
        CampaignFleetAPI escort = ExileHegLeagueMidanComposer.composeEscortFleet(config, random);
        if (main != null) {
            mainStrength = ExileHegLeagueMidanComposer.measure(main);
            mainFp = ExileHegLeagueMidanComposer.fleetPoints(main);
        } else {
            MarketAPI anchor = resolveAnchor(config);
            mainFp = (int) config.midanFleetFpEstimate;
            mainStrength = anchor != null ? Misc.getAdjustedStrength(mainFp, anchor) : mainFp;
        }
        escortStrength = escort != null ? ExileHegLeagueMidanComposer.measure(escort) : 0f;
        escortFp = ExileHegLeagueMidanComposer.fleetPoints(escort);
        log("Exiles Midan COMPOSED: main " + (main != null
                ? ExileHegLeagueMidanComposer.shipCount(main) + " ships " + mainFp + " FP strength "
                  + (int) mainStrength + " (flagship " + main.getFlagship().getShipName()
                  + ", captain level " + main.getCommander().getStats().getLevel() + ", "
                  + countOfficers(main) + " officers incl. captain)"
                : "NOT BUILT (composer WARN above) -- 7c estimate " + mainFp + " FP strength "
                  + (int) mainStrength)
                + " | escort " + (escort != null
                ? ExileHegLeagueMidanComposer.shipCount(escort) + " ships " + escortFp
                  + " FP strength " + (int) escortStrength + " (" + countOfficers(escort) + " officers)"
                : "NOT BUILT")
                + " | escort caps " + config.midanEscortCapsText() + " | day " + (int) nowDay);
        // Nothing is kept: the fleets were never added to a location and go out of scope here.
    }

    protected static int countOfficers(CampaignFleetAPI fleet) {
        int n = 0;
        List members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) members.get(i);
            if (m.getCaptain() != null && !m.getCaptain().isDefault()) n++;
        }
        return n;
    }

    /** Feasible = the escorts needed fit under the cap, and no fleet breaks the FP ceiling. */
    protected int escortsNeeded(ExileHegLeagueConfig config, float required, int rung) {
        if (mainFp > config.midanFpCeiling || escortFp > config.midanFpCeiling) return -1;
        return ExileHegLeagueMidanComposer.escortsNeeded(required, mainStrength, escortStrength,
                escortStrength > 0f ? config.midanEscortCapFor(rung) : 0);
    }

    protected String strikeForceText(ExileHegLeagueConfig config, int needed, int rung) {
        return "Strike Force " + (int) mainStrength + " + " + (int) escortStrength + "/escort"
                + " | needs " + (needed < 0 ? "MORE THAN " + config.midanEscortCapFor(rung) : String.valueOf(needed))
                + " escort(s) (cap " + config.midanEscortCapFor(rung) + ")";
    }

    // ------------------------------------------------------------------
    // Measurement -- every measureIntervalDays, all targets
    // ------------------------------------------------------------------

    protected void measureAllTargets(ExileHegLeagueConfig config, FactionAPI league, float nowDay) {
        List targets = config.midanTargets;
        for (int i = 0; i < targets.size(); i++) {
            String id = (String) targets.get(i);
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null || m.getStarSystem() == null) {
                log("Exiles Midan MEASURE: rung " + (i + 1) + " target " + id
                        + " NOT FOUND (no market or no system) | day " + (int) nowDay);
                continue;
            }
            float enemy = WarSimScript.getEnemyStrength(league, m.getStarSystem());
            float station = ExileStationStrength.get(m.getFaction(), m.getStarSystem(),
                    m.getPrimaryEntity());
            float defense = enemy + station;
            // The running baseline ("normal defence") follows reality slowly, for every target
            // from day 0, so a dip reads as a dip whenever a rung comes up. Updated only here.
            if (baseline[i] < 0f) {
                baseline[i] = defense;
            } else {
                baseline[i] += config.midanBaselineWeightPerMeasure * (defense - baseline[i]);
            }
            float required = config.midanStrikeMarginMult * defense;
            int needed = escortsNeeded(config, required, i);
            log("Exiles Midan MEASURE: rung " + (i + 1) + " " + m.getName() + " ("
                    + m.getFaction().getId() + ") | defense " + (int) defense + " = enemy "
                    + (int) enemy + " + station " + (int) station
                    + " | required " + (int) required + " (x" + config.midanStrikeMarginMult + ")"
                    + " | " + strikeForceText(config, needed, i)
                    + " | main-fleet ratio " + format(defense > 0f ? mainStrength / defense : 0f)
                    + " | " + (needed >= 0 ? "FEASIBLE" : "NOT FEASIBLE")
                    + " | baseline " + (int) baseline[i] + " | day " + (int) nowDay);
        }
    }

    // ------------------------------------------------------------------
    // Opportunity -- daily, current rung only
    // ------------------------------------------------------------------

    protected void checkOpportunity(ExileHegLeagueDirector director, ExileHegLeagueConfig config,
                                    FactionAPI league, float nowDay, boolean writeScoreLine) {
        List targets = config.midanTargets;
        if (rungIndex >= targets.size()) return; // ladder complete (not reachable in 7c)

        // A quest trigger overrides the rebuild clock: the quest decides when this rung happens.
        boolean ready = config.testMidanReadyNow || nowDay >= readyAtDay
                || Global.getSector().getMemoryWithoutUpdate().getBoolean(
                        "$exiles_midan_rung" + (rungIndex + 1) + "_trigger");
        if (!ready) return;
        if (!readyLogged) {
            readyLogged = true;
            log("Exiles Midan READY: rung " + (rungIndex + 1) + " (" + targets.get(rungIndex)
                    + ") | day " + (int) nowDay
                    + (config.testMidanReadyNow ? " | TEST PIN midanReadyNow" : ""));
        }

        // ONE MIDAN STRIKE IN FLIGHT AT A TIME (ruled hard rail). An ENDING op still counts: the
        // ladder only moves when it has fully ended and reported (onStrikeResolved).
        if (isStrikeLive()) return;

        // 7g: the decision point. Three market-list scans, only while ready with no strike live.
        if (refreshHold(config, nowDay) != null) return;

        String id = (String) targets.get(rungIndex);
        MarketAPI m = Global.getSector().getEconomy().getMarket(id);
        if (m == null || m.getStarSystem() == null) return; // MEASURE reports a missing target

        // NEXERELIN RULING (2026-10-19): a target that is no longer Hegemony-owned is SKIPPED, and
        // the ladder advances -- waiting could stall it forever.
        if (!Factions.HEGEMONY.equals(m.getFactionId())) {
            skipRung(config, m, nowDay);
            return;
        }

        float enemy = WarSimScript.getEnemyStrength(league, m.getStarSystem());
        float station = ExileStationStrength.get(m.getFaction(), m.getStarSystem(),
                m.getPrimaryEntity());
        float defense = enemy + station;

        // Read-only here: the baseline moves only at the monthly MEASURE (measureAllTargets).
        if (baseline[rungIndex] < 0f) baseline[rungIndex] = defense;

        float means = 0f;
        if (baseline[rungIndex] > 0f) {
            means = clamp01((baseline[rungIndex] - defense) / baseline[rungIndex]);
        }
        int hegOps = countHegemonyOffensiveOps();
        float distraction = config.midanDistractionOpsFull > 0f
                ? clamp01(hegOps / config.midanDistractionOpsFull) : 0f;
        float cover = coverFor(director.getState(), config);
        float score = config.midanMeansWeight * means
                + config.midanDistractionWeight * distraction
                + config.midanCoverWeight * cover;

        float daysReady = Math.max(0f, nowDay - readyAtDay);
        float decay = config.midanBarDecayDays > 0f
                ? Math.min(1f, daysReady / config.midanBarDecayDays) : 1f;
        float bar = config.midanBarStart - (config.midanBarStart - config.midanBarFloor) * decay;

        float required = config.midanStrikeMarginMult * defense;
        int needed = escortsNeeded(config, required, rungIndex);
        boolean feasible = needed >= 0;

        String detail = "rung " + (rungIndex + 1) + " " + m.getName()
                + " | score " + format(score) + " vs bar " + format(bar)
                + " (means " + format(means) + " x" + config.midanMeansWeight
                + ", distraction " + format(distraction) + " [" + hegOps + " Heg ops] x"
                + config.midanDistractionWeight
                + ", cover " + format(cover) + " [" + ExileHegLeagueDirector.getStateName(director.getState())
                + "] x" + config.midanCoverWeight + ")"
                + " | defense " + (int) defense + " (baseline " + (int) baseline[rungIndex] + ")"
                + " | required " + (int) required + " | " + strikeForceText(config, needed, rungIndex)
                + " | ready " + (int) daysReady + "d | day " + (int) nowDay;

        if (writeScoreLine) {
            log("Exiles Midan SCORE: " + detail);
        }
        // QUEST HOOK (2026-10-30): _hold keeps the director's hands off this rung; _trigger fires it
        // at this check regardless of the score (the hard rails below still apply).
        String questKey = "$exiles_midan_rung" + (rungIndex + 1);
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        boolean questTrigger = mem.getBoolean(questKey + "_trigger");
        boolean held = mem.getBoolean(questKey + "_hold");
        if (held) detail = detail + " | HELD for a quest";

        boolean opportunity = questTrigger
                || (!held && (score >= bar || config.testMidanLaunchNow));
        if (!opportunity) return;
        if (questTrigger) detail = detail + " | QUEST TRIGGER";

        // From here every non-launch is a BLOCKED / WOULD LAUNCH line, rate-limited (the condition
        // can hold for weeks).
        boolean mayLog = nowDay - lastWouldLaunchDay >= WOULD_LAUNCH_LOG_GAP_DAYS;
        String pin = config.testMidanLaunchNow ? " | TEST PIN midanLaunchNow" : "";

        // BUILD GATE (7d-2): only rungs whose payload exists may launch.
        if (rungIndex + 1 > config.midanMaxLaunchRung || config.midanPayloadFor(rungIndex) == null) {
            if (mayLog) {
                lastWouldLaunchDay = nowDay;
                log("Exiles Midan WOULD LAUNCH (rung " + (rungIndex + 1) + " not built yet; maxLaunchRung "
                        + config.midanMaxLaunchRung + "): " + detail + pin);
            }
            return;
        }
        // Even Midan doesn't dogpile a failing market (ruled hard rail).
        if (ExileProtectedFactions.isProtected(m) || ExileUnrestGuard.isBattered(m)) {
            if (mayLog) {
                lastWouldLaunchDay = nowDay;
                log("Exiles Midan BLOCKED (target protected or Battered): " + detail + pin);
            }
            return;
        }
        if (!feasible) {
            if (mayLog) {
                lastWouldLaunchDay = nowDay;
                log("Exiles Midan BLOCKED (score cleared the bar, fleet NOT FEASIBLE): " + detail + pin);
            }
            return;
        }
        float prep = questTrigger ? config.midanQuestPrepDays : -1f;
        if (launch(config, league, m, needed, defense, detail + pin, nowDay, prep)) {
            if (questTrigger) mem.unset(questKey + "_trigger");
        } else if (mayLog) {
            lastWouldLaunchDay = nowDay;
            log("Exiles Midan BLOCKED (launch failed: see the line above): " + detail + pin);
        }
    }

    // ------------------------------------------------------------------
    // Launch (7d-2)
    // ------------------------------------------------------------------

    /**
     * The Strike Force strikes. SNEAK ATTACK (ruled 2026-10-30, superseding the 7d-2 warning
     * window): it forms up INSIDE the target system at a staging point out of sight
     * ({@link #pickStagingPoint}), waits out its prep, closes on the target, strikes, and goes home
     * to the anchor system's hyperspace jump point (Lundy, then Sallee, then Midan's home -- ruled
     * 2026-10-14) to despawn. The intel is NOT registered here; its own distress call does that.
     *
     * @param prepOverride days at the staging point; negative = roll prepDaysMin..prepDaysMax.
     */
    protected boolean launch(ExileHegLeagueConfig config, FactionAPI league, MarketAPI target,
                             int escorts, float defense, String detail, float nowDay,
                             float prepOverride) {
        MarketAPI anchor = resolveAnchor(config);
        StarSystemAPI anchorSystem = anchor != null ? anchor.getStarSystem() : null;
        StarSystemAPI targetSystem = target.getStarSystem();
        SectorEntityToken targetEntity = target.getPrimaryEntity();
        if (anchorSystem == null || targetSystem == null || targetEntity == null) {
            log("Exiles Midan launch failed: no usable launch anchor (Lundy, Sallee, Midan home)"
                    + " or no target entity");
            return false;
        }
        JumpPointAPI home = RouteLocationCalculator.findJumpPointToUse(league,
                anchorSystem.getHyperspaceAnchor());
        ExileHegLeagueConfig.MidanRungPayload payload = config.midanPayloadFor(rungIndex);
        if (home == null || payload == null) {
            log("Exiles Midan launch failed: home jump point " + (home != null)
                    + ", payload " + (payload != null));
            return false;
        }

        Random random = new Random();
        SectorEntityToken gather = pickStagingPoint(targetSystem, targetEntity,
                config.midanStagingDistance, random);
        float clearance = clearanceAt(targetSystem, gather.getLocation());

        ExileHegLeagueMidanStrikeIntel raid = new ExileHegLeagueMidanStrikeIntel(targetSystem, league,
                rungIndex, target.getId(), home, escorts, config.midanBountyPerFrigate,
                config.midanSystemCooldownDays);
        raid.setExtraDays(config.midanExtraDays); // bound overruns (vanilla 60; the 66-day stall)
        float totalFp = mainFp + escorts * escortFp;
        float abortFp = totalFp * config.midanAbortFraction;
        float prep = prepOverride >= 0f ? prepOverride
                : config.midanPrepDaysMin
                  + random.nextFloat() * (config.midanPrepDaysMax - config.midanPrepDaysMin);

        ExileHegLeagueMidanAssembleStage assemble = new ExileHegLeagueMidanAssembleStage(raid, gather,
                anchor, home, escorts, mainFp, escortFp, mainStrength, escortStrength, prep);
        assemble.setAbortFP(abortFp);
        raid.addStage(assemble);

        ExileHegLeagueMidanTravelStage travel = new ExileHegLeagueMidanTravelStage(raid, gather,
                targetEntity, home);
        travel.setAbortFP(abortFp);
        raid.addStage(travel);
        raid.setTravelStage(travel);

        ExileHegLeagueMidanStrikeStage action = new ExileHegLeagueMidanStrikeStage(raid, targetSystem,
                target.getId(), rungIndex, payload, config.midanOrbitDays, config.midanUnrestPoints,
                config.midanStrikeMaxDays);
        action.setAbortFP(abortFp);
        raid.addStage(action);
        raid.setMidanStage(action);

        raid.addStage(new ExileHegLeagueMidanReturnStage(raid, home));

        // NOT added to the intel manager (sneak attack): the raid runs as its own sector script, and
        // its distress call registers it. Held here for the one-strike-at-a-time rail.
        activeStrike = raid;

        launches++;
        float approachDays = RouteLocationCalculator.getTravelDays(gather, targetEntity);
        log("Exiles Midan LAUNCHED: rung " + (rungIndex + 1) + " " + target.getName() + " in "
                + targetSystem.getNameWithLowercaseType() + " | nominal source " + anchor.getName()
                + ", home " + anchorSystem.getBaseName() + " | staging in-system "
                + (int) Misc.getDistance(gather.getLocation(), targetEntity.getLocation())
                + " from target, clearance " + (int) clearance + ", prep " + format(prep)
                + "d, approach ~" + format(approachDays) + "d"
                + (prepOverride >= 0f ? " (quest prep)" : "")
                + " | Strike Force main " + (int) mainStrength + " + " + escorts + " escort(s) x "
                + (int) escortStrength + " = " + (int) (mainStrength + escorts * escortStrength)
                + " | FP " + (int) totalFp + " | defense " + (int) defense
                + " (x" + config.midanStrikeMarginMult + " = "
                + (int) (config.midanStrikeMarginMult * defense) + ") | abort below "
                + (int) abortFp + " FP | launch #" + launches + " | " + detail);
        return true;
    }

    /**
     * The staging point: {@code distance} from the target, on whichever of 36 bearings lies
     * farthest from everything that would see it first -- the system's jump points (where the
     * player arrives), its centre (the star) and the player's fleet if it is in the system.
     */
    protected SectorEntityToken pickStagingPoint(StarSystemAPI system, SectorEntityToken target,
                                                 float distance, Random random) {
        Vector2f center = target.getLocation();
        float start = random.nextFloat() * 10f;
        float bestScore = -1f;
        float bestX = center.x + distance;
        float bestY = center.y;
        Vector2f p = new Vector2f();
        for (int i = 0; i < 36; i++) {
            Vector2f dir = Misc.getUnitVectorAtDegreeAngle(start + i * 10f);
            p.set(center.x + dir.x * distance, center.y + dir.y * distance);
            float score = clearanceAt(system, p);
            if (score > bestScore) {
                bestScore = score;
                bestX = p.x;
                bestY = p.y;
            }
        }
        return system.createToken(bestX, bestY);
    }

    /** Distance from {@code p} to the nearest jump point, the system centre, or the player. */
    protected float clearanceAt(StarSystemAPI system, Vector2f p) {
        float min = Misc.getDistance(p, system.getCenter().getLocation());
        List jumps = system.getJumpPoints();
        for (int i = 0; i < jumps.size(); i++) {
            SectorEntityToken jp = (SectorEntityToken) jumps.get(i);
            float d = Misc.getDistance(p, jp.getLocation());
            if (d < min) min = d;
        }
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null && player.getContainingLocation() == system) {
            float d = Misc.getDistance(p, player.getLocation());
            if (d < min) min = d;
        }
        return min;
    }

    /** The live strike (held since the sneak-attack ruling), or any still-registered one from an
     *  older build. Daily at most; the list is short. */
    protected boolean isStrikeLive() {
        if (activeStrike != null) {
            if (!activeStrike.isEnded()) return true;
            activeStrike = null;
        }
        List ops = Global.getSector().getIntelManager().getIntel(ExileHegLeagueMidanStrikeIntel.class);
        for (int i = 0; i < ops.size(); i++) {
            ExileHegLeagueMidanStrikeIntel op = (ExileHegLeagueMidanStrikeIntel) ops.get(i);
            if (!op.isEnded()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Resolution and the breadcrumb (7d-2)
    // ------------------------------------------------------------------

    /**
     * Called by the director when a strike ends. The rung is SPENT either way (ruled: no retry), and
     * Midan must rebuild: {@code rebuildDays} after a success, {@code rebuildFailedDays} after a
     * failure (the fleet was lost) -- the player's counterplay. A lost Ajax needs nothing extra:
     * every launch composes a fresh one (protection (a), ruled 2026-10-30).
     */
    public void onStrikeResolved(ExileHegLeagueConfig config, int rung, boolean landed,
                                 boolean playerPresent, boolean playerFought, boolean ajaxLost,
                                 float nowDay) {
        if (ajaxLost) ajaxLosses++;
        writeBreadcrumb(rung, landed ? "landed" : "foiled", playerPresent, playerFought, ajaxLost);
        ExileHegLeagueConfig.MidanRungPayload payload = config.midanPayloadFor(rung);
        if (landed && payload != null && payload.eventideWound) postCunninghamVow(config);
        if (rung == rungIndex) rungIndex++;
        readyAtDay = nowDay + (landed ? config.midanRebuildDays : config.midanRebuildFailedDays);
        readyLogged = false;
        log("Exiles Midan RUNG RESOLVED: rung " + (rung + 1) + " " + (landed ? "LANDED" : "FOILED")
                + " | player present " + playerPresent + ", fought " + playerFought
                + " | Ajax " + (ajaxLost ? "LOST" : "survived") + " (losses so far " + ajaxLosses
                + " of " + launches + " launches)"
                + " | next: " + (rungIndex < config.midanTargets.size()
                        ? "rung " + (rungIndex + 1) + " ready at day " + (int) readyAtDay
                          + " (" + (landed ? "rebuild " + (int) config.midanRebuildDays
                                           : "failed rebuild " + (int) config.midanRebuildFailedDays)
                          + "d)"
                        : "ladder complete")
                + " | day " + (int) nowDay);
    }

    /** The target left Hegemony hands (Nexerelin): the rung is skipped and the ladder moves on at
     *  once, with no rebuild -- nothing sailed. */
    protected void skipRung(ExileHegLeagueConfig config, MarketAPI m, float nowDay) {
        writeBreadcrumb(rungIndex, "skipped", false, false, false);
        log("Exiles Midan RUNG SKIPPED: rung " + (rungIndex + 1) + " " + m.getName() + " is held by "
                + m.getFactionId() + ", not the Hegemony | day " + (int) nowDay);
        rungIndex++;
        readyLogged = false;
    }

    /**
     * MIDAN'S PROXY (Pass 7 step 7f; war doc sec 6.2 PASS 7 PROXY DESIGN, ruled 2026-10-19). Called
     * by {@link ExileHegLeagueRecoveryManager} for every new relief record -- either side's, any war
     * state, Midan's own rungs included. Rolls {@code proxy.directiveChance}; on success, and only
     * when no directive is active, names the record's market to the Black Fleet
     * ({@link ExileMidanDirective}). The Black Fleet director acts on it without knowing who wrote it.
     *
     * <p>Static and stateless: with attribution and the backfire lockout deferred (Sean,
     * 2026-11-01), there is nothing for the module to remember. Rides the war director's switches:
     * records only exist while the war runs, and {@code midan.enabled} gates this as it gates the
     * rungs.
     */
    public static void offerProxyDirective(ExileHegLeagueConfig config, MarketAPI market,
                                           String victimFactionId, float expireDays) {
        if (config == null || market == null) return;
        if (!config.midanEnabled || !config.midanProxyEnabled) return;
        if (Global.getSector().getFaction("exilemidan") == null) return;
        String where = market.getName() + " (" + victimFactionId + ")";
        // 7g: the other decision point. Static here (no module in hand), so no transition line;
        // the refusal is logged in the writer's own form.
        String hold = holdReason(config);
        if (hold != null) {
            log("Exiles Midan DIRECTIVE NOT WRITTEN: " + where + " -- Midan holds: " + hold);
            return;
        }
        String active = ExileMidanDirective.getActive();
        if (active != null) {
            log("Exiles Midan DIRECTIVE NOT WRITTEN: " + where + " -- one is already active ("
                    + ExileMidanDirective.getMarketId(active) + ")");
            return;
        }
        float roll = new Random().nextFloat();
        boolean forced = config.testMidanForceDirective;
        if (!forced && roll >= config.midanProxyDirectiveChance) {
            log("Exiles Midan DIRECTIVE NOT WRITTEN: " + where + " -- roll " + format(roll)
                    + " >= chance " + format(config.midanProxyDirectiveChance));
            return;
        }
        ExileMidanDirective.write(ExileMidanDirective.TYPE_CONVOY_AMBUSH, market.getId(), expireDays);
        log("Exiles Midan DIRECTIVE WRITTEN: CONVOY_AMBUSH " + where
                + (forced ? " -- TEST PIN midanForceDirective" : " -- roll " + format(roll)
                        + " < chance " + format(config.midanProxyDirectiveChance))
                + " | backstop expiry " + (int) expireDays + "d");
    }

    /**
     * CUNNINGHAM'S VOW (war doc sec 6.1, ruled 2026-10-19): a Story announcement with his portrait a
     * few days after rung 3 LANDS -- success only -- so General Engagement reads as his rage.
     * The text is a marked PLACEHOLDER: Sean writes it.
     */
    protected void postCunninghamVow(ExileHegLeagueConfig config) {
        ExileStoryAnnouncementIntel.postAfter(config.eventideVowDelayDays,
                "Admiral Cunningham's Vow",
                "PLACEHOLDER (author text pending). Admiral Cunningham, whose family has held land on"
                        + " Eventide since its first terraforming, has spoken publicly for the first time"
                        + " since the attack on his homeworld.\n\n\"Eventide's sky was not taken by"
                        + " accident. Whoever gave that order will answer for it. I will see to it"
                        + " personally.\"\n\nHegemony command has declined to comment.",
                "Admiral Cunningham swears vengeance for Eventide",
                Factions.HEGEMONY, "exile_cunningham", config.eventideVowNoticeDays);
        log("Exiles Midan VOW scheduled: Admiral Cunningham, in " + config.eventideVowDelayDays + "d");
    }

    /**
     * PHASE-2 HOOKS (ruled 2026-10-11): one persistent record per firing, as global memory keys a
     * quest can read from rules.csv ({@code $global.exiles_midan_rung1_result} and so on).
     */
    protected void writeBreadcrumb(int rung, String result, boolean playerPresent,
                                   boolean playerFought, boolean ajaxLost) {
        String key = "$exiles_midan_rung" + (rung + 1);
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem =
                Global.getSector().getMemoryWithoutUpdate();
        mem.set(key + "_result", result);
        mem.set(key + "_playerPresent", playerPresent);
        mem.set(key + "_playerFought", playerFought);
        mem.set(key + "_ajaxLost", ajaxLost);
    }

    /**
     * The Ajax died AFTER its rung was already resolved -- on the way home (2026-10-31 test 3:
     * Hegemony patrols caught it in hyperspace). Corrects the protection-(b) count and the
     * Phase-2 breadcrumb, which were written as "survived" at resolution. Nothing else depends on
     * it: the rebuild clock ignores the Ajax and every rung composes a new one.
     */
    public void recordLateAjaxLoss(int rung) {
        ajaxLosses++;
        Global.getSector().getMemoryWithoutUpdate()
                .set("$exiles_midan_rung" + (rung + 1) + "_ajaxLost", true);
        log("Exiles Midan AJAX LOST AFTER RESOLUTION: rung " + (rung + 1)
                + " breadcrumb corrected | losses so far " + ajaxLosses + " of " + launches
                + " launches");
    }

    /** Crisis states give a "League strike" cover; ceasefire talks give partial cover. */
    protected float coverFor(int state, ExileHegLeagueConfig config) {
        if (state == ExileHegLeagueDirector.STATE_INCIDENT
                || state == ExileHegLeagueDirector.STATE_RETALIATION
                || state == ExileHegLeagueDirector.STATE_ESCALATION) {
            return 1f;
        }
        if (state == ExileHegLeagueDirector.STATE_POLITICAL_INTERVENTION) {
            return clamp01(config.midanCoverTalks);
        }
        return 0f;
    }

    /**
     * Live Hegemony offensive operations against the League: the war's own raid-pipeline ops
     * (interdictions, strikes, squeezes -- all {@link ExileHegLeagueRaidIntel}) plus its system
     * blockades. Own loop on purpose (ExileLiveOps' rule: a helper needs two identical callers;
     * this filter is unique). Called at most once a day.
     */
    protected int countHegemonyOffensiveOps() {
        int count = 0;
        List raids = Global.getSector().getIntelManager().getIntel(RaidIntel.class);
        for (int i = 0; i < raids.size(); i++) {
            Object o = raids.get(i);
            if (!(o instanceof ExileHegLeagueRaidIntel)) continue;
            RaidIntel raid = (RaidIntel) o;
            if (raid.isEnded() || raid.isEnding() || raid.isFailed()) continue;
            if (raid.getFaction() == null || !Factions.HEGEMONY.equals(raid.getFaction().getId())) continue;
            count++;
        }
        List groups = Global.getSector().getIntelManager().getIntel(FleetGroupIntel.class);
        for (int i = 0; i < groups.size(); i++) {
            Object o = groups.get(i);
            if (!(o instanceof ExileHegLeagueBlockade)) continue;
            FleetGroupIntel fgi = (FleetGroupIntel) o;
            if (fgi.isEnded() || fgi.isEnding() || fgi.isAborted() || fgi.isFailed()) continue;
            if (fgi.getFaction() == null || !Factions.HEGEMONY.equals(fgi.getFaction().getId())) continue;
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Step 7g: the "no one left" hold (war doc sec 6.1 WAR OFF AND NEXERELIN; ruled 2026-11-07)
    // ------------------------------------------------------------------

    /**
     * Why Midan cannot act right now, or null. Three conditions, each one pass over the market
     * list: the Persean League holds no market (no one left to frame), the Hegemony holds no
     * market (no targets), Midan holds no market in its HOME system (plans can fail; the home
     * system is the one containing the last launch anchor, "Midan home" in the ruled order).
     * Vanilla never deletes a faction object -- Nexerelin marks elimination by leaving it
     * marketless -- so "no markets" is the test, with a null guard for safety.
     */
    public static String holdReason(ExileHegLeagueConfig config) {
        FactionAPI league = Global.getSector().getFaction(Factions.PERSEAN);
        if (league == null || Misc.getFactionMarkets(league).isEmpty()) {
            return "the Persean League holds no market (no one left to frame)";
        }
        FactionAPI hegemony = Global.getSector().getFaction(Factions.HEGEMONY);
        if (hegemony == null || Misc.getFactionMarkets(hegemony).isEmpty()) {
            return "the Hegemony holds no market (no targets)";
        }
        FactionAPI midan = Global.getSector().getFaction("exilemidan");
        if (midan == null) return "Midan does not exist";
        List midanMarkets = Misc.getFactionMarkets(midan);
        StarSystemAPI home = null;
        if (config != null && config.midanLaunchAnchors != null && !config.midanLaunchAnchors.isEmpty()) {
            String homeAnchor = (String) config.midanLaunchAnchors.get(config.midanLaunchAnchors.size() - 1);
            SectorEntityToken e = Global.getSector().getEntityById(homeAnchor);
            if (e != null) home = e.getStarSystem();
        }
        boolean any = false;
        for (int i = 0; i < midanMarkets.size(); i++) {
            MarketAPI m = (MarketAPI) midanMarkets.get(i);
            if (m == null) continue;
            if (home == null || m.getStarSystem() == home) {
                any = true;
                break;
            }
        }
        if (!any) {
            return "Midan holds no market in its home system" + (home != null ? " (" + home.getBaseName() + ")" : "")
                    + " (plans can fail)";
        }
        return null;
    }

    /** Evaluates the hold and logs the transition each way. @return the current reason, or null. */
    protected String refreshHold(ExileHegLeagueConfig config, float nowDay) {
        String reason = holdReason(config);
        if (reason != null && !holding) {
            log("Exiles Midan HOLD: " + reason + " | rung " + (rungIndex + 1) + " | day " + (int) nowDay);
        } else if (reason == null && holding) {
            log("Exiles Midan RESUMES: " + holdReason + " -- no longer true | rung " + (rungIndex + 1)
                    + " | day " + (int) nowDay);
        }
        holding = reason != null;
        holdReason = reason;
        return reason;
    }

    /** The first launch anchor (station entity id) that still has a live market, in ruled order. */
    protected MarketAPI resolveAnchor(ExileHegLeagueConfig config) {
        List anchors = config.midanLaunchAnchors;
        if (anchors == null) return null;
        for (int i = 0; i < anchors.size(); i++) {
            SectorEntityToken e = Global.getSector().getEntityById((String) anchors.get(i));
            if (e == null) continue;
            MarketAPI m = e.getMarket();
            if (m != null && m.isInEconomy()) return m;
        }
        return null;
    }

    public int getRungIndex() {
        return rungIndex;
    }

    protected static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    protected static String format(float v) {
        return String.valueOf(Math.round(v * 100f) / 100f);
    }

    protected static void log(String line) {
        Global.getLogger(ExileHegLeagueMidanModule.class).info(line);
    }
}
