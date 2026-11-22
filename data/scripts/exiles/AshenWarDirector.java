package data.scripts.exiles;

import java.awt.Color;
import java.util.List;
import java.util.Random;

import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * Ashen Crusade war director -- PASS 1: the war clock only.
 *
 * <p>Canonical design: {@code docs/EXILES_ASHEN_CRUSADE_WAR.md}. Read it before extending this
 * class; rulings A-M and Q1-Q4 are settled there and should not be re-litigated in code.
 *
 * <p>This pass ships the phase state machine, the war score, config loading (including the
 * §9.1 test-compression block), and the public hooks later passes plug into. It deliberately
 * launches NOTHING -- no fleets, no intel, no market effects. Those arrive as:
 * Pass 2 sabotage, Pass 3 relief fleets, Pass 4 interdiction raids, Pass 6 blockade,
 * Pass 7 the one-time Culann strike.
 *
 * <p><b>Phase graph (§3.2).</b> Phase 4 fires at most ONCE per campaign, and only when the
 * Pass-2 special sabotage succeeds against a Culann target:
 * <pre>
 *   1 BUILDUP -> 2 INTERDICTION -+-> 4 CULANN STRIKE -> 1   (once ever, on success)
 *                                +-> 3 BLOCKADE      -> 1   (otherwise)
 * </pre>
 * Only a SUCCESSFUL strike closes the branch (ruling Q1); a repelled assault costs war score
 * and the Crusade may earn another attempt. Nanoforge presence never gates entry (ruling Q2).
 *
 * <p><b>War score</b> is the pacing spine (§3.1, §5.5): it shortens Phase 1 and scales operation
 * FP, and it is what tips special-sabotage target selection toward Culann as the war matures.
 * Its designed source is successful relief fleets (Pass 3), so it stays at 0 until that pass
 * lands -- use {@code testing.warScoreOverride} to exercise mature-war behaviour before then.
 *
 * <p><b>Save safety.</b> Only primitives are saved; every config field is transient and reloaded
 * on each launch, so tuning edits land on restart (the convention shared with
 * {@link BlackFleetRaidManager} and {@link ExileRefugeeConvoyManager}).
 */
public class AshenWarDirector implements EveryFrameScript {

    public static final String CONFIG_PATH = "data/config/exiles/director_config.json";
    public static final String CONFIG_KEY = "exileashencrusade";
    public static final String FACTION_ID = "exileashencrusade";
    /** Quest override; beats the config default for the whole director (BF convention). */
    public static final String WAR_ENABLED_KEY = "$exiles_ashen_war_enabled";

    public static final int PHASE_BUILDUP = 1;
    public static final int PHASE_INTERDICTION = 2;
    public static final int PHASE_BLOCKADE = 3;
    public static final int PHASE_ASSAULT = 4;

    // --- Saved state (primitives only) ---
    protected int phase = PHASE_BUILDUP;
    /** Days left in the current phase; < 0 means "not started yet", set on first advance. */
    protected float phaseDaysRemaining = -1f;
    /**
     * <b>THE PREPARATION METER (§3.2a, ruled 2026-08-13).</b> 0..1, where 1.0 is "the Crusade is
     * ready to move on Culann" and {@link #advancePhase()} sends the next cycle to Phase 4.
     *
     * <p>It keeps its three older jobs -- shortening Phase 1, scaling op FP, gating the rehearsal
     * escalation -- and that coupling is <b>chosen, not accidental</b>: a player who breaks Ashen
     * operations both delays the strike and keeps the Crusade weaker, while a player who ignores
     * them faces harder ops on a shorter clock. {@code rehearsalFailureDecay} caps how far that
     * runs in the player's favour.
     */
    protected float warScore = 0f;
    /** Ruling Q1: set only by a SUCCESSFUL Culann strike. Closes the Phase-4 branch forever. */
    protected boolean phase4Succeeded = false;
    /** §7.0 telegraph: fired once when preparation crosses the warning threshold. */
    protected boolean phase4Telegraphed = false;
    /** Drives the longer post-assault Phase 1 (§4, 150d vs 90d). */
    protected boolean lastPhaseWasAssault = false;
    protected int cyclesCompleted = 0;
    protected long startTs = 0L;
    /** Pass 2: days until the next sabotage batch; < 0 means "roll one on the next tick". */
    protected float daysUntilSabotage = -1f;
    /** Pass 2: the special attempt fires once per Phase 2; reset on entering Phase 2. */
    protected boolean specialSabotageDone = false;
    /** Pass 6c: cycle of the last rehearsal launch, so Phase 3 mounts exactly one. -1 = none. */
    protected int lastRehearsalCycle = -1;
    /** Pass 6c: consecutive failed rehearsals, driving the geometric penalty decay. */
    protected int rehearsalFailures = 0;

    // --- Transient config/runtime (reloaded each launch) ---
    protected transient boolean configLoaded;
    protected transient IntervalUtil tick;
    protected transient Random random;
    /** Pass 2 (§5). Rebuilt each launch so its config edits land on restart. */
    protected transient AshenSabotage sabotage;

    protected transient boolean enabled = true;
    protected transient boolean debugMessages = false;
    protected transient float startDelayDays = 0f;

    /** §9.1: multiplies EVERY duration below at load. 1.0 = production. */
    protected transient float timeScale = 1f;
    // Testing block (§9.1). All inert at defaults; loaded here so later passes read one source.
    protected transient boolean sabotageAlwaysSucceeds = false;
    protected transient int forcePhase = -1;
    protected transient float warScoreOverride = -1f;

    protected transient float phase1Days = 90f;
    protected transient float phase1AfterAssaultDays = 150f;
    protected transient float phase2MinDays = 60f;
    protected transient float phase2MaxDays = 90f;
    protected transient float phase3MinDays = 60f;
    protected transient float phase3MaxDays = 90f;
    protected transient float phase4Days = 60f;
    /** Symmetric jitter on Phase 1, as a fraction (0.15 = +/-15%). Phases 2 and 3 get their
     *  variety from min/max ranges; without this Phase 1 was identical every cycle. */
    protected transient float phase1JitterFraction = 0.15f;
    /** Fraction of Phase 1 a fully mature war score removes (§3.1). */
    protected transient float phase1ScoreShorten = 0.33f;
    /** Op FP multiplier at war score 1.0 (§3.1); 1.0 + this at maturity. */
    protected transient float scoreFpMult = 0.5f;

    // --- §3.2a countdown: the reframed Phase-4 trigger. Ruled 2026-08-13. ---
    /**
     * Preparation level at which the next cycle goes to Phase 4 instead of Phase 3.
     *
     * <p><b>Read from config rather than hardcoded on purpose.</b> Sean's question, 2026-08-13:
     * does a threshold of 1.0 lock out a future Phase 5+? It does not -- but the answer that
     * matters is that a later arc should get <i>its own clock</i> rather than a longer one. A
     * progress meter is a compressed answer to one question ("how ready are they to take the forge
     * from Culann?"); when the question changes the compression is wrong, and raising the ceiling
     * would silently rescale every fraction-of-range tunable below ({@code rehearsalEscalateWarScore},
     * {@code assaultFailureFloor}, the FP curve). CLAUDE.md already rules escalation past Phase 4 to
     * be quest content, so a second arc would naturally be quest-started. This knob is the cheap
     * insurance that keeps the door open either way.
     */
    protected transient float phase4TriggerScore = 1f;
    /**
     * Preparation level at which the §7.0 telegraph fires -- the player's warning that the
     * Crusade's one great offensive is coming. At the measured ~0.37/cycle accrual, 0.85 buys
     * roughly 90 days of notice.
     *
     * <p><b>This telegraph had to be rebuilt.</b> It used to hang off the special sabotage that
     * queued Phase 4; the §3.2a reframe took that trigger away and would have left ruling O4's
     * ratified warning silent. It is now a property of preparation itself, which is strictly
     * better: it fires when the strike is genuinely near rather than when dice happened to land.
     */
    protected transient float phase4TelegraphScore = 0.85f;
    /**
     * Where preparation resets to after a repelled Culann strike (ruling 4, floor at <b>0.5</b>).
     *
     * <p>An <b>absolute floor, never a percentage of current</b> -- Sean withdrew the relative
     * "75% of current" in the same message that proposed it, because it compounds
     * (0.75 -> 0.56 -> 0.42) and the third retry drifts past the one-year target in a direction
     * nobody chose. At the measured ~0.37/cycle, 0.5 rebuilds in ~1.35 cycles ~ 10 months, inside
     * the "no more than a year" ceiling with margin for the fact that late accrual accelerates as
     * Phase 1 shortens.
     *
     * <p>Fiction: the buildup took years, but the infrastructure survives the lost fleet.
     */
    protected transient float assaultFailureFloor = 0.5f;
    /**
     * Preparation added by a special sabotage that lands on a <i>Culann</i> target (ruling 2).
     *
     * <p>Below {@code scorePerRehearsal} deliberately: a rehearsal is a whole operation, this is
     * one covert success. Culann-only, because the meter measures readiness against Culann
     * specifically -- Eochu Bres successes are ordinary war damage.
     *
     * <p><b>Known tradeoff, accepted:</b> relief convoys and rehearsals can both be physically
     * attacked; sabotage cannot. This is therefore the one uncontestable accrual source. Measured
     * dilution is small -- one attempt per cycle at 0.25->0.50 success, Culann-weighted 0.5 early
     * to 6.0 mature, works out to ~0.02/cycle, about 5% of total accrual.
     */
    protected transient float scorePerSpecialSabotage = 0.08f;

    // --- Pass 6c: the Phase-3 rehearsal (§6.4). See reportRehearsal() for the arithmetic. ---
    /** War score a landed rehearsal adds. Deliberately worth more than a relief convoy
     *  (scorePerRelief 0.045): the rehearsal is the Crusade's real preparation. */
    protected transient float scorePerRehearsal = 0.12f;
    /** War score the FIRST failed rehearsal removes. Later ones decay -- see reportRehearsal. */
    protected transient float scorePerRehearsalFailure = 0.25f;
    /** Per-consecutive-failure decay. Max total delay = failure penalty / (1 - this). */
    protected transient float rehearsalFailureDecay = 0.6f;
    /** War score at which the rehearsal escalates from Tibicena to Port Tse (§6.4). */
    protected transient float rehearsalEscalateWarScore = 0.4f;

    public AshenWarDirector() {
        initTransients();
    }

    protected Object readResolve() {
        initTransients();
        return this;
    }

    protected void initTransients() {
        configLoaded = false;
        tick = new IntervalUtil(0.1f, 0.2f);
        random = new Random();
    }

    /** The live director, or null. Later passes use this instead of holding a reference. */
    public static AshenWarDirector get() {
        if (Global.getSector() == null) return null;
        List scripts = Global.getSector().getScripts();
        for (int i = 0; i < scripts.size(); i++) {
            Object script = scripts.get(i);
            if (script instanceof AshenWarDirector) {
                return (AshenWarDirector) script;
            }
        }
        return null;
    }

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void advance(float amount) {
        if (Global.getSector() == null || Global.getSector().isPaused()) return;
        if (Global.getSector().isInNewGameAdvance()) return;
        if (!configLoaded) loadConfig();
        if (tick == null) tick = new IntervalUtil(0.1f, 0.2f);
        if (random == null) random = new Random();

        if (startTs == 0L) {
            startTs = Global.getSector().getClock().getTimestamp();
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        tick.advance(days);
        if (!tick.intervalElapsed()) return;
        float elapsed = tick.getIntervalDuration();

        if (!warEnabled()) return;
        if (Global.getSector().getClock().getElapsedDaysSince(startTs) < startDelayDays) return;

        // Testing aid (§9.1): forcePhase PINS the phase -- the code re-forces it whenever the phase
        // differs, so it does NOT jump once (comment corrected 2026-10-06, bug sweep ASH-10; the
        // config comment already said so). Consequence: content gated once per cycle or once ever
        // (one rehearsal per Phase 3 cycle, the one-time Culann strike) does not repeat while
        // pinned, because the cycle counter advances only on a real phase exit. Logged on every
        // re-force so a quiet pinned run is diagnosable.
        if (forcePhase >= PHASE_BUILDUP && forcePhase <= PHASE_ASSAULT && phase != forcePhase) {
            debug("TEST: forcing phase " + getPhaseName(forcePhase) + ".");
            Global.getLogger(AshenWarDirector.class).info(
                    "Ashen TEST: forcePhase pin re-forcing " + getPhaseName(forcePhase)
                            + " (was " + getPhaseName(phase) + ", cycle " + cyclesCompleted
                            + ") -- once-per-cycle / once-ever content will not repeat while pinned");
            beginPhase(forcePhase, true);
            return;
        }
        // Compare with the CLAMPED override (the Heg-League HL-5 fix): a value above 1 never equals
        // the clamped score and would re-fire the debug message every tick.
        if (warScoreOverride >= 0f && warScore != clamp01(warScoreOverride)) {
            warScore = clamp01(warScoreOverride);
            debug("TEST: war score forced to " + format(warScore) + ".");
        }

        if (phaseDaysRemaining < 0f) {
            beginPhase(phase, true);
            return;
        }

        advanceSabotage(elapsed);

        phaseDaysRemaining -= elapsed;
        if (phaseDaysRemaining <= 0f) {
            advancePhase();
        }
    }

    /**
     * Pass 2 (§5): the monthly sabotage batch, plus the single special attempt in the last month
     * of Phase 2. Runs before the phase decrement so the special attempt always gets its window
     * even when the phase ends on this same tick.
     */
    protected void advanceSabotage(float elapsed) {
        if (sabotage == null || !sabotage.isEnabled()) return;

        // Special tier: once per Phase 2, inside the closing window (§5.4). Checked first so a
        // short test-compressed Phase 2 cannot skip past it.
        if (phase == PHASE_INTERDICTION && !specialSabotageDone
                && phaseDaysRemaining <= sabotage.getSpecialWindowDays()) {
            specialSabotageDone = true;
            sabotage.attemptSpecial(this);
        }

        int attempts = sabotage.getAttemptsPerMonth(phase);
        if (attempts <= 0) return;

        if (daysUntilSabotage < 0f) {
            daysUntilSabotage = sabotage.getCadenceDays();
            return;
        }
        daysUntilSabotage -= elapsed;
        if (daysUntilSabotage > 0f) return;

        daysUntilSabotage = sabotage.getCadenceDays();
        for (int i = 0; i < attempts; i++) {
            sabotage.attemptOrdinary(this);
        }
    }

    /** Config default, overridable at runtime by quests via the memory flag (BF convention). */
    protected boolean warEnabled() {
        Object flag = Global.getSector().getMemoryWithoutUpdate().get(WAR_ENABLED_KEY);
        if (flag instanceof Boolean) {
            return ((Boolean) flag).booleanValue();
        }
        return enabled;
    }

    // ------------------------------------------------------------------
    // Phase machine (§3.2)
    // ------------------------------------------------------------------

    protected void advancePhase() {
        boolean fromAssault = phase == PHASE_ASSAULT;
        int next;

        if (phase == PHASE_BUILDUP) {
            next = PHASE_INTERDICTION;
        } else if (phase == PHASE_INTERDICTION) {
            // §3.2a COUNTDOWN (ruled 2026-08-13). Phase 4 replaces Phase 3 in the final cycle --
            // the topology is unchanged (T1), only the CONDITION. It used to be a dice roll:
            // P(sabotage lands) x P(the target was Culann), which is the weakness the reframe
            // exists to remove. It is now simply "preparation completed."
            //
            // Ruling Q1/Q2 still hold: only a SUCCESSFUL strike closes the branch forever, and
            // nanoforge presence is never consulted -- the Crusade sails on old intelligence and
            // does not learn the vault is empty until Culann is cracked.
            if (isPreparationComplete()) {
                next = PHASE_ASSAULT;
            } else {
                next = PHASE_BLOCKADE;
            }
        } else {
            // Blockade and Assault both return to Buildup; that completes a war cycle.
            next = PHASE_BUILDUP;
            cyclesCompleted++;
        }

        lastPhaseWasAssault = fromAssault;
        beginPhase(next, true);
    }

    protected void beginPhase(int newPhase, boolean announce) {
        phase = newPhase;
        phaseDaysRemaining = rollPhaseDays(newPhase);
        // The special sabotage gets one attempt per Phase 2 (§5.4).
        if (newPhase == PHASE_INTERDICTION) {
            specialSabotageDone = false;
        }

        String text = "Ashen war clock -> " + getPhaseName(newPhase)
                + " (" + (int) phaseDaysRemaining + "d, war score " + format(warScore)
                + ", cycle " + cyclesCompleted + ")";
        if (announce) debug(text);
        Global.getLogger(AshenWarDirector.class).info(text);
    }

    protected float rollPhaseDays(int forPhase) {
        if (forPhase == PHASE_BUILDUP) {
            float base = lastPhaseWasAssault ? phase1AfterAssaultDays : phase1Days;
            // §3.1: a maturing war effort spends less time rebuilding between offensives.
            base *= 1f - clamp01(warScore) * phase1ScoreShorten;
            // Playtest 2026-08-01: Phases 2 and 3 roll a range but Phase 1 was a fixed 90d
            // every cycle, which reads mechanical once noticed. Symmetric jitter, so the
            // §5.5 pacing arithmetic (which uses the mean cycle length) is unaffected.
            if (phase1JitterFraction > 0f) {
                base *= 1f + (random.nextFloat() * 2f - 1f) * phase1JitterFraction;
            }
            return Math.max(1f, base);
        }
        if (forPhase == PHASE_INTERDICTION) return roll(phase2MinDays, phase2MaxDays);
        if (forPhase == PHASE_BLOCKADE) return roll(phase3MinDays, phase3MaxDays);
        return phase4Days;
    }

    public static String getPhaseName(int forPhase) {
        if (forPhase == PHASE_BUILDUP) return "Phase 1 (Buildup)";
        if (forPhase == PHASE_INTERDICTION) return "Phase 2 (Interdiction)";
        if (forPhase == PHASE_BLOCKADE) return "Phase 3 (Blockade)";
        if (forPhase == PHASE_ASSAULT) return "Phase 4 (Culann Strike)";
        return "Phase ?";
    }

    // ------------------------------------------------------------------
    // Public hooks for later passes
    // ------------------------------------------------------------------

    public int getPhase() {
        return phase;
    }

    /**
     * How many days Phase 4 is configured to run. Pass 7's wave scheduler needs it to turn
     * {@code phaseDaysRemaining} into "days since the strike began" without storing a second
     * counter that could desync from the phase clock.
     */
    public float getPhase4Days() {
        return phase4Days;
    }

    public float getPhaseDaysRemaining() {
        return phaseDaysRemaining;
    }

    public int getCyclesCompleted() {
        return cyclesCompleted;
    }

    public float getWarScore() {
        return warScore;
    }

    /** Op FP multiplier from war effort maturity (§3.1). 1.0 at war score 0. */
    public float getFpMult() {
        return 1f + clamp01(warScore) * scoreFpMult;
    }

    /** Pass 3: a relief fleet completed. Pass 4/7: an operation succeeded or failed. */
    public void addWarScore(float amount) {
        float before = warScore;
        warScore = clamp01(warScore + amount);
        if (warScore != before) {
            Global.getLogger(AshenWarDirector.class).info(
                    "Ashen war score " + format(before) + " -> " + format(warScore));
        }
        // Checked on any rise, not only on the logged change, so the crossing cannot be missed.
        if (warScore > before) checkPhase4Telegraph();
    }

    // ------------------------------------------------------------------
    // Pass 6c -- the Phase-3 rehearsal's effect on the countdown (§6.4)
    // ------------------------------------------------------------------

    /**
     * A Phase-3 rehearsal finished. Success advances preparation; failure sets it back.
     *
     * <p><b>Why failure uses geometric decay.</b> Sean's directive (2026-08-09): <i>"a focused
     * player buys one extra cycle, a very focused player can buy more than one, but should not be
     * able to completely stop it."</i> That is a bounded-sum requirement, and decaying the penalty
     * by {@code rehearsalFailureDecay} per consecutive failure satisfies it provably:
     *
     * <pre>
     *   penalty for the nth consecutive failure = P0 * d^n
     *   maximum total delay ever purchasable    = P0 / (1 - d)     (geometric series)
     * </pre>
     *
     * At the defaults (P0 = 0.25, d = 0.6) that ceiling is 0.625 war score -- roughly 2.5 cycles
     * at the current relief cadence. More than one, finite, never zero-able. The ceiling is
     * therefore a number Sean can <i>set</i> rather than discover in testing: raise d to let a
     * determined player buy more, lower it to buy less, and the Crusade can never be stalled
     * outright either way.
     *
     * <p>The counter resets on any success, so suppression has to be sustained to keep paying off
     * -- and the Crusade "commits harder" in the fiction precisely because each further failure
     * costs it less ground.
     */
    public void reportRehearsal(boolean success) {
        if (success) {
            rehearsalFailures = 0;
            addWarScore(scorePerRehearsal);
            debug("Ashen rehearsal SUCCEEDED (+" + format(scorePerRehearsal) + " war score).");
            return;
        }

        float penalty = scorePerRehearsalFailure;
        for (int i = 0; i < rehearsalFailures; i++) {
            penalty *= rehearsalFailureDecay;
        }
        rehearsalFailures++;
        addWarScore(-penalty);
        debug("Ashen rehearsal FAILED (-" + format(penalty) + " war score, consecutive failure "
                + rehearsalFailures + ").");
    }

    /** Cycle number of the last rehearsal launch, so Phase 3 mounts exactly one. */
    public int getLastRehearsalCycle() {
        return lastRehearsalCycle;
    }

    public void setLastRehearsalCycle(int cycle) {
        this.lastRehearsalCycle = cycle;
    }

    /** True once the war has matured enough to rehearse against the harder second target. */
    public boolean isRehearsalEscalated() {
        return clamp01(warScore) >= rehearsalEscalateWarScore;
    }

    /** True once the one-time Culann strike has SUCCEEDED (ruling Q1). */
    public boolean hasPhase4Succeeded() {
        return phase4Succeeded;
    }

    /**
     * §3.2a: is the Crusade ready to move on Culann? Checked at the Phase-2 exit.
     *
     * <p>Closed permanently by a successful strike (ruling Q1), so a war that has already taken
     * the forge settles into the ordinary 1 -> 2 -> 3 loop no matter how high the meter climbs.
     */
    public boolean isPreparationComplete() {
        return !phase4Succeeded && warScore >= phase4TriggerScore;
    }

    /** 0..1 progress toward the Culann strike, for callers that want it as a fraction. */
    public float getPreparationFraction() {
        if (phase4TriggerScore <= 0f) return 1f;
        return clamp01(warScore / phase4TriggerScore);
    }

    /**
     * Pass 2 (ruling 2, 2026-08-13): a special sabotage landed on a <b>Culann</b> target.
     *
     * <p>Under the old design this queued the Phase-4 branch outright -- one coin flip decided the
     * campaign's climax. It is now an accumulating reconnaissance step: the same built,
     * live-tested subsystem, feeding the meter instead of branching the machine. A hard
     * prerequisite ("preparation complete AND intel obtained") was considered and rejected, since
     * it would reinstate exactly the dice gate the reframe removed.
     */
    public void reportSpecialSabotage() {
        if (phase4Succeeded) return;
        addWarScore(scorePerSpecialSabotage);
    }

    /** Pass 7: the Culann strike cracked the defenses. Closes the branch permanently. */
    public void notifyPhase4Succeeded() {
        phase4Succeeded = true;
        Global.getLogger(AshenWarDirector.class).info(
                "Ashen Culann strike SUCCEEDED; the automatic loop is now 1 -> 2 -> 3.");
    }

    /**
     * Pass 7: the Culann strike was repelled. Ruling Q1 -- this does NOT consume the one-time
     * allowance; it resets preparation, which pushes the next attempt out without needing a
     * separate cooldown timer (§12 O2).
     *
     * <p><b>Ruling 4 (2026-08-13): reset to an absolute floor, not a fraction of current.</b>
     * {@code min()} rather than assignment so the floor can never <i>raise</i> preparation -- if a
     * future path ever repels a strike launched below the floor, the Crusade does not get a
     * promotion out of it.
     *
     * <p>The telegraph is re-armed: preparation drops below the warning threshold, so when the
     * Crusade rebuilds the player is warned again rather than being told once per campaign.
     */
    public void notifyPhase4Failed() {
        float before = warScore;
        warScore = Math.min(warScore, assaultFailureFloor);
        if (warScore < phase4TelegraphScore) phase4Telegraphed = false;
        Global.getLogger(AshenWarDirector.class).info(
                "Ashen Culann strike REPELLED; preparation " + format(before) + " -> "
                        + format(warScore) + " (floor), the branch stays open.");
    }

    /**
     * §7.0 TELEGRAPH (ruling O4, rebuilt 2026-08-13). The player's warning that the Crusade's one
     * great offensive is coming.
     *
     * <p><b>Why this exists here at all.</b> The telegraph used to be a side effect of the special
     * sabotage that queued Phase 4 -- and the §3.2a reframe took that trigger away, which would
     * have silently retired a ratified requirement. O4's reasoning was that the frustration Sean
     * described comes from <i>no warning</i>, not from <i>no quest</i>. Hanging it on preparation
     * instead of on dice is strictly better: it fires when the strike is actually near.
     *
     * <p>Fires once per approach, from {@link #addWarScore} at the moment of crossing rather than
     * at a phase boundary, so the warning arrives with the event that earned it.
     *
     * <p><b>PLACEHOLDER PROSE -- Sean's to write.</b> The mechanism and timing are ruled; the
     * wording is not.
     */
    protected void checkPhase4Telegraph() {
        if (phase4Telegraphed || phase4Succeeded) return;
        if (warScore < phase4TelegraphScore) return;
        phase4Telegraphed = true;

        Global.getSector().getCampaignUI().addMessage(
                "Tri-Tachyon security bulletins report a sustained Ashen Crusade buildup on the "
                        + "approaches to Hybrasil. Culann's garrison has been placed on alert.",
                Color.ORANGE);
        Global.getLogger(AshenWarDirector.class).info(
                "Ashen §7.0 telegraph fired at war score " + format(warScore) + ".");
    }

    /** §9.1 testing aid, read by Pass-2 sabotage. */
    public boolean isSabotageAlwaysSucceeds() {
        return sabotageAlwaysSucceeds;
    }

    public boolean isDebugMessages() {
        return debugMessages;
    }

    /** Shared debug channel so every subsystem obeys one config flag. */
    public void debug(String text) {
        if (!debugMessages || Global.getSector() == null) return;
        Global.getSector().getCampaignUI().addMessage("DEBUG: " + text, Color.YELLOW);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    protected float roll(float min, float max) {
        if (max <= min) return min;
        return min + random.nextFloat() * (max - min);
    }

    protected static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }

    protected static String format(float value) {
        return "" + (Math.round(value * 100f) / 100f);
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    protected void loadConfig() {
        configLoaded = true;
        JSONObject cfg = null;
        try {
            JSONObject root = Global.getSettings().loadJSON(CONFIG_PATH);
            cfg = root.optJSONObject(CONFIG_KEY);
            if (cfg == null) {
                applyTimeScale();
                loadSubsystems(null);
                return;
            }

            enabled = cfg.optBoolean("enabled", enabled);
            debugMessages = cfg.optBoolean("debugMessages", debugMessages);
            timeScale = (float) cfg.optDouble("timeScale", timeScale);
            if (timeScale <= 0f) timeScale = 1f;

            JSONObject testing = cfg.optJSONObject("testing");
            if (testing != null) {
                sabotageAlwaysSucceeds = testing.optBoolean(
                        "sabotageAlwaysSucceeds", sabotageAlwaysSucceeds);
                forcePhase = testing.optInt("forcePhase", forcePhase);
                warScoreOverride = (float) testing.optDouble(
                        "warScoreOverride", warScoreOverride);
            }

            JSONObject clock = cfg.optJSONObject("warClock");
            if (clock != null) {
                phase1Days = (float) clock.optDouble("phase1Days", phase1Days);
                phase1AfterAssaultDays = (float) clock.optDouble(
                        "phase1AfterAssaultDays", phase1AfterAssaultDays);
                phase2MinDays = (float) clock.optDouble("phase2MinDays", phase2MinDays);
                phase2MaxDays = (float) clock.optDouble("phase2MaxDays", phase2MaxDays);
                phase3MinDays = (float) clock.optDouble("phase3MinDays", phase3MinDays);
                phase3MaxDays = (float) clock.optDouble("phase3MaxDays", phase3MaxDays);
                phase4Days = (float) clock.optDouble("phase4Days", phase4Days);
                phase1JitterFraction = (float) clock.optDouble(
                        "phase1JitterFraction", phase1JitterFraction);
                phase1ScoreShorten = (float) clock.optDouble(
                        "phase1ScoreShorten", phase1ScoreShorten);
                scoreFpMult = (float) clock.optDouble("scoreFpMult", scoreFpMult);
                // §3.2a countdown (2026-08-13). assaultFailureFloor REPLACES the old
                // scorePerAssaultFailure, which described a flat subtraction that no longer
                // happens; Sean approved the rename rather than let the name lie about the value.
                phase4TriggerScore = (float) clock.optDouble(
                        "phase4TriggerScore", phase4TriggerScore);
                phase4TelegraphScore = (float) clock.optDouble(
                        "phase4TelegraphScore", phase4TelegraphScore);
                assaultFailureFloor = (float) clock.optDouble(
                        "assaultFailureFloor", assaultFailureFloor);
                scorePerSpecialSabotage = (float) clock.optDouble(
                        "scorePerSpecialSabotage", scorePerSpecialSabotage);
                startDelayDays = (float) clock.optDouble("startDelayDays", startDelayDays);
            }

            // Pass 6c. The rehearsal's FLEET tuning lives in AshenInterdictionManager; what the
            // director owns is its effect on the countdown, read from the same block so a tuner
            // sees every rehearsal number in one place.
            JSONObject reh = cfg.optJSONObject("rehearsal");
            if (reh != null) {
                scorePerRehearsal = (float) reh.optDouble(
                        "scorePerRehearsal", scorePerRehearsal);
                scorePerRehearsalFailure = (float) reh.optDouble(
                        "scorePerRehearsalFailure", scorePerRehearsalFailure);
                rehearsalFailureDecay = (float) reh.optDouble(
                        "failureDecay", rehearsalFailureDecay);
                rehearsalEscalateWarScore = (float) reh.optDouble(
                        "escalateWarScore", rehearsalEscalateWarScore);
            }
        } catch (Exception e) {
            Global.getLogger(AshenWarDirector.class).warn(
                    "Ashen war director config load failed; using code defaults", e);
        }

        applyTimeScale();
        loadSubsystems(cfg);
    }

    /** Subsystems own their own config blocks but share the director's timeScale, so one knob
     *  still compresses the whole war (§9.1). Rebuilt on every launch. */
    protected void loadSubsystems(JSONObject factionCfg) {
        sabotage = new AshenSabotage();
        sabotage.loadConfig(factionCfg, timeScale);
    }

    /**
     * §9.1: one multiplier, applied to every duration at load. Set {@code timeScale} to 0.1 for
     * testing and back to 1.0 for production -- a single line, so a half-restore is impossible.
     * This deliberately avoids the Black Fleet's per-value restore checklist
     * ({@code EXILES_BLACK_FLEET_SYSTEMS.md} §6), where an individual missed value ships silently.
     *
     * <p>Durations only. Probabilities are not durations and fleet travel is real distance at
     * real burn, so the {@code testing} block covers the rest.
     */
    protected void applyTimeScale() {
        if (timeScale == 1f) return;
        phase1Days *= timeScale;
        phase1AfterAssaultDays *= timeScale;
        phase2MinDays *= timeScale;
        phase2MaxDays *= timeScale;
        phase3MinDays *= timeScale;
        phase3MaxDays *= timeScale;
        phase4Days *= timeScale;
        startDelayDays *= timeScale;
        Global.getLogger(AshenWarDirector.class).info(
                "Ashen war director: timeScale " + timeScale + " applied to all durations.");
    }
}
