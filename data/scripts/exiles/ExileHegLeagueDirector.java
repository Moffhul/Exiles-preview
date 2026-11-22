package data.scripts.exiles;

import java.awt.Color;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/**
 * Hegemony-Persean League war director -- the Bilateral Crisis Engine: the Tension meter, the
 * six-state crisis cycle, the incident table, the Midan ladder's hook into the cycle, and the
 * switch-gated Nexerelin layers. The operations themselves live in the four manager scripts
 * (interdiction, strike + squeeze, blockade, recovery), which READ this class and are never
 * called by it.
 *
 * <p>Canonical design: {@code docs/EXILES_HEG_LEAGUE_WAR.md}. Read it before extending this
 * class; rulings S1-S10 and R1-R12 are settled there and must not be re-litigated in code.
 *
 * <p><b>Fourth sibling, not a plug-in to anything (sec 3.1, CLAUDE.md sec 6).</b> This is a peer of
 * the Ashen, Black Fleet and Omicron directors on the vanilla {@code RaidIntel} pipeline. It
 * shares VOCABULARY with them -- {@code director_config.json}, {@code ExileUnrestGuard},
 * {@code ExileProtectedFactions}, {@code ExileLiveOps}, the condition base classes -- and no
 * control flow whatsoever. <b>It does not know another director exists, and no other director
 * may learn about it.</b> The unified Sector Destabilization Director was tested and REJECTED
 * (Ashen doc sec 14.5/14.6): do not re-propose it.
 *
 * <p><b>This class launches nothing itself.</b> Two layers tick here: the Tension meter (sec
 * 3.2.1) and the six-state crisis cycle (sec 3.2.2), plus the intel beats they post. Operations
 * were added by pass: Pass 2 interdiction raids, Pass 3 surgical strikes + the incident table,
 * Pass 4 system blockades, Pass 5 the intervention outcome and asymmetry, Pass 6 planetary
 * blockade + recovery convoys, Pass 7 the Midan injection. Pass 8 (the player-interception
 * dialogue, war doc sec 4.5) is designed and ruled but NOT YET BUILT. Complexity review:
 * docs/EXILES_HEG_LEAGUE_COMPLEXITY_REVIEW.md.
 *
 * <p><b>State graph (sec 3.2.2).</b> INCIDENT is instant -- it is entered, resolved and left on
 * one tick, which is why it carries a zero dwell:
 * <pre>
 *   1 TENSION -&gt; 2 INCIDENT -&gt; 3 RETALIATION -+-&gt; 4 ESCALATION -&gt; 5 INTERVENTION -&gt; 6 DE_ESCALATION -&gt; 1
 *                                             +-&gt; 5 INTERVENTION -----------------&gt; 6 DE_ESCALATION -&gt; 1
 *                                             +-&gt; CONTAINED ----------------------------------------&gt; 1
 * </pre>
 * The RETALIATION exit is a three-way branch (sec 3.2.5, the 2026-09-13 containment amendment):
 * at or above {@code hawkThreshold} the hawks surge; in the warning band the doves ROLL to convene
 * ceasefire talks pre-emptively; below the warning line the border playbook simply absorbs the
 * incident and the cycle returns to TENSION with no talks and no cut. Containment, not the
 * ceasefire, is what makes the war read as "skirmishing but contained" -- the talks are reserved
 * for when the playbook is failing, which is what lets the meter climb at all.
 *
 * <p><b>The meter is the memory; the machine forgets.</b> Tension carries across cycles, never
 * decays below its floor (R2 -- the nanoforge grievance is permanent), and war weariness makes a
 * bloody cycle MORE likely to end in a stand-down. That inversion is the central quiet failsafe
 * for the Feel-vs-Reality goal: the hotter the war runs, the harder the system pulls back.
 *
 * <p><b>Save safety.</b> Only primitives and interned Strings are saved; every config field is
 * transient and reloaded on each launch, so tuning edits land on restart -- the convention
 * shared with {@link AshenWarDirector} and {@link BlackFleetRaidManager}.
 */
public class ExileHegLeagueDirector implements EveryFrameScript {

    /** Quest/runtime override; beats the config default for the whole director (BF convention). */
    public static final String WAR_ENABLED_KEY = "$exiles_hl_war_enabled";

    public static final int STATE_TENSION = 1;
    public static final int STATE_INCIDENT = 2;
    public static final int STATE_RETALIATION = 3;
    public static final int STATE_ESCALATION = 4;

    /** Operation types for {@link #reportOperationWeariness(String, String, boolean, int)}. */
    public static final int OP_TYPE_INTERDICTION = 0;
    public static final int OP_TYPE_STRIKE = 1;
    public static final int OP_TYPE_BLOCKADE = 2;
    /** Pass 6: the planetary blockade, "the squeeze". */
    public static final int OP_TYPE_PLANETARY = 3;
    public static final int STATE_POLITICAL_INTERVENTION = 5;
    public static final int STATE_DE_ESCALATION = 6;

    public static final int OUTCOME_NONE = 0;
    public static final int OUTCOME_STAND_DOWN = 1;
    public static final int OUTCOME_PARTIAL = 2;
    public static final int OUTCOME_COLLAPSE = 3;

    // ------------------------------------------------------------------
    // Saved state (primitives + Strings only)
    // ------------------------------------------------------------------

    protected int state = STATE_TENSION;
    /** Days left in the current state; &lt; 0 means "not started yet", set on the first advance. */
    protected float stateDaysRemaining = -1f;
    /** 0..cap. &lt; 0 means "uninitialised" -- seeded from config on the first tick. */
    protected float tension = -1f;
    /** Cumulative per-side loss ledger (sec 3.2.1). Strengthens the doves at intervention. */
    protected float wearinessHegemony = 0f;
    protected float wearinessPersean = 0f;
    /** Set at INCIDENT; the victim is the other belligerent. Null outside a live cycle. */
    protected String offenderFactionId = null;
    protected String victimFactionId = null;
    /** Pass 3: which INCIDENT-table entry fired this cycle, or null (table off / outside a cycle). */
    protected String incidentTypeId = null;
    /** Pass 3: the market the telegraph named, FLAVOUR ONLY -- nothing mechanical reads it. */
    protected String incidentSiteMarketId = null;
    /**
     * Pass 3: cycle whose RETALIATION op has already been mounted; null = none.
     *
     * <p><b>An Integer, not an int, on purpose.</b> XStream builds saved objects without running
     * constructors or field initialisers, so a field this pass adds comes back from a Pass-1/2 save
     * at the JVM default -- and {@code 0} would collide with cycle 0 and silently swallow the first
     * retaliation of a converted campaign. {@code null} cannot collide with anything.
     */
    protected Integer lastRetaliationCycle = null;
    protected int cyclesCompleted = 0;
    /** Consecutive cycles that reached ESCALATION -- the second General Engagement route. */
    protected int consecutiveHotCycles = 0;
    /**
     * Containment amendment counters (sec 3.2.5). Plain ints: a converted save reads them as 0,
     * which is also the right answer, so no Integer sentinel is needed (contrast deviation 8).
     */
    protected int containedCycles = 0;
    /** Times ESCALATION was reached -- the measurement the ceasefire dial is tuned from. */
    protected int escalationsReached = 0;
    /** True while THIS cycle is running as a General Engagement (sec 3.2.4 A). */
    protected boolean generalEngagement = false;
    /** Talks collapsed last cycle: shortens the next TENSION dwell. */
    protected boolean lastTalksCollapsed = false;
    protected int lastOutcome = OUTCOME_NONE;
    /**
     * Pass 5: which branch sent the cycle to the ceasefire table -- one of
     * {@link ExileHegLeagueCeasefireIntel}'s REASON_* constants. Set at each branch point, read
     * once when the intel is posted. A plain int: a converted save reads 0, which the intel's
     * prose treats as the ordinary post-escalation line, so no Integer sentinel is needed.
     */
    protected int lastInterventionReason = 0;
    /**
     * Pass 5 posting guards, keyed on {@link #cyclesCompleted}.
     *
     * <p><b>Why they exist:</b> {@code testing.forceState} PINS the machine rather than jumping
     * once, so with {@code forceState} 4 or 5 the director leaves the pinned state on its dwell and
     * {@code advance()} re-enters it on the next tick -- calling {@link #beginState(int)} for the
     * same state, in the same cycle, indefinitely. Without a guard that would post one intel per
     * dwell. Integer rather than int for the {@link #lastRetaliationCycle} reason: 0 is a real
     * cycle number and a converted save must not read as "already posted".
     */
    protected Integer lastCeasefireIntelCycle = null;
    protected Integer lastEngagementIntelCycle = null;
    protected long startTs = 0L;
    /** Pass 7: Midan's false-flag ladder. Saved with the director; created lazily on first tick. */
    protected ExileHegLeagueMidanModule midan;
    /** Days since the last periodic meter report, so the debug channel stays readable. */
    protected float daysSinceMeterReport = 0f;
    /** Days since the last idle log, so a lost premise does not spam the log. */
    protected float daysSinceIdleLog = 9999f;
    /**
     * 7g (2026-11-07): the war switch as last seen, so a flip of {@link #WAR_ENABLED_KEY} is logged
     * ONCE in either direction. Sean's console run switched the war off and then "on" under a
     * misspelled key, and the log had nothing to show for three game months of silence. A save
     * from before this field loads it as false, so the first tick after such a load logs ON once.
     */
    protected boolean warSwitchSeen = true;

    // ------------------------------------------------------------------
    // Transient runtime (reloaded each launch)
    // ------------------------------------------------------------------

    protected transient ExileHegLeagueConfig config;
    protected transient IntervalUtil tick;
    protected transient Random random;
    /** Days since the vanilla hostility-intel retire last ran (complexity review 2026-11-08: it
     *  used to run every tick for a job that acts once; now daily). */
    protected transient float daysSinceHostilityRetire;
    /**
     * Bug sweep HL-1 (2026-10-06): true while the war is ACTIVE -- enabled (config or quest flag),
     * past its start delay, and not idle (both sides hold markets and are hostile). Recomputed on
     * every director tick; the op managers gate their LAUNCH paths on it, so switching the war
     * off now stops new ops instead of freezing only the state machine. Ops already in flight
     * finish normally.
     *
     * <p><b>SAVED, not transient (complexity review S1, 2026-11-08).</b> As a transient it read
     * false for the first 0.1-0.2 days after every load, and a Midan strike resolving in that
     * window was logged "war is not active" and lost its tension raise and incident for good.
     * A save from before this change loads it as false, which is the old behaviour for one tick.
     */
    protected boolean activeThisTick;
    /**
     * Cached answer to "do both belligerents still hold markets?".
     *
     * <p>The live query (R6c -- ownership is NEVER hardcoded, because it can flip under
     * Nexerelin) costs a {@code getMarketsCopy()} allocation, and CLAUDE.md sec 7 forbids
     * allocating inside {@code advance()}. So it runs on its own slow clock and the tick reads
     * the cached booleans. Defaults to "true" so a fresh load never idles before its first
     * check.
     */
    protected transient boolean hegemonyHoldsMarkets;
    protected transient boolean perseanHoldsMarkets;
    protected transient float daysSincePremiseCheck;

    public ExileHegLeagueDirector() {
        initTransients();
    }

    protected Object readResolve() {
        initTransients();
        return this;
    }

    protected void initTransients() {
        config = null;
        tick = new IntervalUtil(0.1f, 0.2f);
        random = new Random();
        daysSinceHostilityRetire = 9999f;
        hegemonyHoldsMarkets = true;
        perseanHoldsMarkets = true;
        daysSincePremiseCheck = 9999f;
    }

    /** HL-1: may the op managers LAUNCH right now? See {@link #activeThisTick}. */
    public boolean isActive() {
        return activeThisTick;
    }

    /** The live director, or null. Callers use this instead of holding a reference. It walks the
     *  sector's script list, so it belongs on slow paths only (tick gates, launches, outcomes). */
    public static ExileHegLeagueDirector get() {
        if (Global.getSector() == null) return null;
        List scripts = Global.getSector().getScripts();
        for (int i = 0; i < scripts.size(); i++) {
            Object script = scripts.get(i);
            if (script instanceof ExileHegLeagueDirector) {
                return (ExileHegLeagueDirector) script;
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

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public void advance(float amount) {
        if (Global.getSector() == null || Global.getSector().isPaused()) return;
        if (Global.getSector().isInNewGameAdvance()) return;
        if (config == null) loadConfig();
        if (tick == null) tick = new IntervalUtil(0.1f, 0.2f);
        if (random == null) random = new Random();

        if (startTs == 0L) {
            startTs = Global.getSector().getClock().getTimestamp();
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        tick.advance(days);
        if (!tick.intervalElapsed()) return;
        float elapsed = tick.getIntervalDuration();

        activeThisTick = false; // HL-1: proven true below only if every premise holds
        boolean warOn = warEnabled();
        if (warOn != warSwitchSeen) {
            warSwitchSeen = warOn;
            Global.getLogger(ExileHegLeagueDirector.class).info("Exiles Heg-League WAR SWITCH: "
                    + (warOn ? "ON" : "OFF") + " (" + WAR_ENABLED_KEY + ")"
                    + (warOn ? " -- the director and Midan resume"
                             : " -- new ops and Midan stop; ops in flight finish and still report"));
        }
        if (!warOn) return;
        if (Global.getSector().getClock().getElapsedDaysSince(startTs) < config.startDelayDays) {
            return;
        }

        // B3 (sec 7.2): re-assert hostility before anything reads the premise. Cheap -- two
        // faction lookups -- and it works identically with Nexerelin absent. With the switch
        // off this is a no-op and the idle check below takes over: the director RIDES a peace
        // the player has allowed rather than fighting it.
        if (ExileHegLeagueNexSupport.selfHealHostility(config)) {
            debug("Heg-League: hostility had lapsed and was re-asserted (B3 self-heal).");
        }
        // Pass 7 (2026-10-25): vanilla's timed game-start flare-up would later announce a false
        // peace. Idempotent; it acts once, on the first check after the start delay. Daily rather
        // than per tick since 2026-11-08: the scan builds a conflict-id String each call.
        daysSinceHostilityRetire += elapsed;
        if (daysSinceHostilityRetire >= 1f) {
            daysSinceHostilityRetire = 0f;
            ExileHegLeagueNexSupport.retireVanillaHostilityIntel(config);
        }

        if (isIdle(elapsed)) return;
        activeThisTick = true;

        if (tension < 0f) {
            tension = clampTension(config.tensionStart);
            debug("Heg-League tension initialised at " + format(tension) + ".");
        }

        // Testing aids (sec 9.1). forceState PINS the machine -- it does not jump once.
        if (config.forceState >= STATE_TENSION
                && config.forceState <= STATE_DE_ESCALATION
                && config.forceState != STATE_INCIDENT
                && state != config.forceState) {
            debug("TEST: forcing state " + getStateName(config.forceState) + ".");
            beginState(config.forceState);
            return;
        }
        // HL-5: compare with the CLAMPED override, or an out-of-range value (below the floor or
        // above the cap) never matches and re-fires the debug message every tick.
        if (config.tensionOverride >= 0f && tension != clampTension(config.tensionOverride)) {
            tension = clampTension(config.tensionOverride);
            debug("TEST: tension forced to " + format(tension) + ".");
        }

        if (stateDaysRemaining < 0f) {
            beginState(state);
            return;
        }

        decayMeters(elapsed);
        reportMeterPeriodically(elapsed);

        // Pass 7 (step 7c: LOG-ONLY). Only reached while the war is active, so the war's off
        // switch pauses Midan too -- the HL-1 precedent (war doc sec 6.1, WAR OFF AND NEXERELIN).
        if (midan == null) midan = new ExileHegLeagueMidanModule();
        midan.advance(this, config, elapsed,
                Global.getSector().getClock().getElapsedDaysSince(startTs));

        stateDaysRemaining -= elapsed;
        // INCIDENT carries a zero dwell, so one tick can legitimately cross several states.
        // The guard exists only so a malformed config (all dwells zero) cannot spin forever.
        int guard = 0;
        while (stateDaysRemaining <= 0f && guard < 8) {
            advanceState();
            guard++;
        }
        if (guard >= 8 && stateDaysRemaining <= 0f) {
            Global.getLogger(ExileHegLeagueDirector.class).warn(
                    "Exiles Heg-League: state machine advanced 8 times in one tick; check "
                            + "the cycle{} dwell values in director_config.json.");
        }
    }

    /** Config default, overridable at runtime by quests via the memory flag (BF convention). */
    protected boolean warEnabled() {
        return switchEnabled(WAR_ENABLED_KEY, config.enabled);
    }

    /**
     * The switch convention every manager shares: a Boolean in sector memory overrides the
     * config default, anything else leaves it. One copy since the 2026-11-08 complexity review
     * (tier 3); it was six.
     */
    public static boolean switchEnabled(String memoryKey, boolean configDefault) {
        Object flag = Global.getSector().getMemoryWithoutUpdate().get(memoryKey);
        if (flag instanceof Boolean) {
            return ((Boolean) flag).booleanValue();
        }
        return configDefault;
    }

    /** A launch cadence with the +/-25% jitter every launch clock uses; never under a day. One
     *  copy since the 2026-11-08 complexity review (tier 3); it was three. */
    public static float jitterCadence(float days, Random random) {
        if (days < 1f) days = 1f;
        return days * (0.75f + random.nextFloat() * 0.5f);
    }

    /**
     * sec 3.3 idle conditions. The director never unregisters -- it goes quiet and says why, so a
     * premise that comes back (a reconquered market, a war that resumes) simply resumes it.
     */
    protected boolean isIdle(float elapsed) {
        daysSinceIdleLog += elapsed;
        daysSincePremiseCheck += elapsed;
        if (daysSincePremiseCheck >= config.premiseCheckIntervalDays) {
            daysSincePremiseCheck = 0f;
            hegemonyHoldsMarkets = ExileHegLeagueNexSupport.holdsAnyMarket(
                    ExileHegLeagueConfig.HEGEMONY_ID);
            perseanHoldsMarkets = ExileHegLeagueNexSupport.holdsAnyMarket(
                    ExileHegLeagueConfig.PERSEAN_ID);
        }

        String reason = null;

        if (!hegemonyHoldsMarkets) {
            reason = "the Hegemony holds no markets";
        } else if (!perseanHoldsMarkets) {
            reason = "the Persean League holds no markets";
        } else if (!ExileHegLeagueNexSupport.isPairHostile()) {
            // Only reachable with selfHealHostility off -- the ratified "graceful ride".
            reason = "the pair is not mutually hostile (selfHealHostility is off)";
        }

        if (reason == null) {
            daysSinceIdleLog = 9999f;
            return false;
        }
        if (daysSinceIdleLog >= 30f) {
            daysSinceIdleLog = 0f;
            String text = "Heg-League director IDLE: " + reason + ".";
            debug(text);
            Global.getLogger(ExileHegLeagueDirector.class).info("Exiles " + text);
        }
        return true;
    }

    /**
     * sec 3.2.1 "Down": quiet stretches decay slowly, DE_ESCALATION decays fast, and nothing ever
     * takes the meter below its floor.
     *
     * <p><b>Conservative reading:</b> decay runs in TENSION and DE_ESCALATION only. The spec
     * names "quiet stretches" and "the De-escalation state"; a crisis actively unfolding is
     * neither, so INCIDENT/RETALIATION/ESCALATION/INTERVENTION do not bleed the meter.
     */
    protected void decayMeters(float elapsed) {
        if (state == STATE_TENSION) {
            tension = clampTension(tension - config.tensionDecayPerDay * elapsed);
        } else if (state == STATE_DE_ESCALATION) {
            tension = clampTension(tension
                    - config.tensionDecayPerDay * config.deescalationDecayMult * elapsed);
        }
        if (config.wearinessDecayPerDay > 0f) {
            float amount = config.wearinessDecayPerDay * elapsed;
            wearinessHegemony = Math.max(0f, wearinessHegemony - amount);
            wearinessPersean = Math.max(0f, wearinessPersean - amount);
        }
    }

    protected void reportMeterPeriodically(float elapsed) {
        if (config.debugMeterIntervalDays <= 0f) return;
        daysSinceMeterReport += elapsed;
        if (daysSinceMeterReport < config.debugMeterIntervalDays) return;
        daysSinceMeterReport = 0f;
        debug("Heg-League meter: tension " + format(tension)
                + " | state " + getStateName(state)
                + " (" + (int) stateDaysRemaining + "d left)"
                + " | weariness Heg " + format(wearinessHegemony)
                + " / League " + format(wearinessPersean)
                + " | cycle " + cyclesCompleted
                + (generalEngagement ? " | GENERAL ENGAGEMENT" : ""));
    }

    // ------------------------------------------------------------------
    // Crisis cycle (sec 3.2.2)
    // ------------------------------------------------------------------

    protected void advanceState() {
        if (state == STATE_TENSION) {
            beginState(STATE_INCIDENT);
            return;
        }
        if (state == STATE_INCIDENT) {
            // TESTING AID (bug sweep HL-4, 2026-10-06): a forceState 1 pin means "TENSION and its
            // incidents only". Without this the pin dragged RETALIATION back to TENSION within one
            // director tick, the strike manager usually missed it, and the once-per-cycle guard
            // then blocked the rest because the cycle never closed. Unreachable at forceState -1.
            if (config != null && config.forceState == STATE_TENSION) {
                logPinnedCycleClose("INCIDENT");
                closeCycle();
                return;
            }
            beginState(STATE_RETALIATION);
            return;
        }
        if (state == STATE_RETALIATION) {
            // THE BRANCH (sec 3.2.5). Three ways out:
            //   hawk line and above  -> ESCALATION: the hardliners get their surge.
            //   warning band         -> the doves ROLL to convene ceasefire talks early.
            //   below the warning    -> CONTAINED: the playbook absorbs it, straight back to
            //                           TENSION, no talks, no cut.
            // Pre-amendment every cycle ended at the table, and the table's expected cut alone
            // outweighed a cycle's gains, so the meter pinned at its floor forever (sec 14.4).
            //
            // TESTING AID (2026-10-03 log read): under a testing.forceState 3 pin the cycle must
            // close on EVERY exit. Otherwise, at tension >= hawk, ESCALATION is entered and the pin
            // drags the machine straight back into RETALIATION, closeCycle() never runs,
            // cyclesCompleted never moves, and the strike manager's once-per-cycle guard blocks
            // every roll after the first -- a whole run passed with zero new ops. closeCycle(), not
            // containCycle(), so the containment statistics are not inflated. Unreachable in
            // production: forceState is -1.
            if (config.forceState == STATE_RETALIATION) {
                logBranch("CYCLE CLOSED (testing.forceState 3 pin)", -1f);
                closeCycle();
                return;
            }
            if (tension >= config.hawkThreshold) {
                logBranch("ESCALATION", -1f);
                beginState(STATE_ESCALATION);
                return;
            }
            consecutiveHotCycles = 0;
            // PACING RULING A (Sean, 2026-11-08; complexity review sec 7): a Midan false-flag
            // incident never ends in a shrug. Below the hawk line the doves convene at once
            // instead of rolling for containment -- a station assault is not something the
            // Hegemony files a formal protest over. The ceasefire intel is the visible ending.
            if (ExileHegLeagueMidanIncident.ID.equals(incidentTypeId)) {
                logBranch("CEASEFIRE TALKS (Midan incident -- contained roll bypassed)", -1f);
                lastInterventionReason = ExileHegLeagueCeasefireIntel.REASON_PREEMPTIVE;
                beginState(STATE_POLITICAL_INTERVENTION);
                return;
            }
            if (tension >= config.warningThreshold) {
                float roll = random.nextFloat();
                if (roll < config.preemptiveCeasefireChance) {
                    logBranch("CEASEFIRE TALKS (pre-emptive)", roll);
                    // Pass 5: the ceasefire intel's beat-1 prose says WHY the doves convened, and
                    // this is the branch that knows.
                    lastInterventionReason = ExileHegLeagueCeasefireIntel.REASON_PREEMPTIVE;
                    beginState(STATE_POLITICAL_INTERVENTION);
                    return;
                }
                logBranch("CONTAINED (talks roll failed)", roll);
            } else {
                logBranch("CONTAINED", -1f);
            }
            containCycle();
            return;
        }
        if (state == STATE_ESCALATION) {
            // Pass 5: the surge window closes. A General Engagement gets its closing beat here --
            // sec 3.2.4 A rules that it ALWAYS ends at the table, which is the next state.
            lastInterventionReason = generalEngagement
                    ? ExileHegLeagueCeasefireIntel.REASON_GENERAL_ENGAGEMENT
                    : ExileHegLeagueCeasefireIntel.REASON_ESCALATION;
            endEngagementIntel();
            // TESTING AID (bug sweep HL-3, 2026-10-06): under a forceState 4 pin each ESCALATION
            // window now closes its cycle, so every pinned window is a fresh escalation (entry
            // salvo re-armed, General Engagement flag cleared) instead of one endless cycle whose
            // re-entries counted as consecutive hot cycles -- which made every window a General
            // Engagement from the third on (seen in the 2026-10-04 Run 3).
            if (config != null && config.forceState == STATE_ESCALATION) {
                logPinnedCycleClose("ESCALATION");
                closeCycle();
                return;
            }
            beginState(STATE_POLITICAL_INTERVENTION);
            return;
        }
        if (state == STATE_POLITICAL_INTERVENTION) {
            resolveIntervention();
            // Pass 5 beat 2, immediately after the roll that set lastOutcome.
            markCeasefireOutcome();
            // TESTING AID (bug sweep HL-4): a forceState 5 pin closes the cycle after each
            // resolution, so every pinned session posts its own ceasefire intel (the post is
            // guarded on the cycle counter). Unreachable at forceState -1.
            if (config != null && config.forceState == STATE_POLITICAL_INTERVENTION) {
                logPinnedCycleClose("POLITICAL INTERVENTION");
                closeCycle();
                return;
            }
            beginState(STATE_DE_ESCALATION);
            return;
        }
        // DE_ESCALATION closes the cycle.
        closeCycle();
    }

    /**
     * The cycle close-out, shared by the DE_ESCALATION exit and the CONTAINED exit so the two
     * paths cannot drift apart. Bumping {@code cyclesCompleted} is what re-arms the strike
     * manager's once-per-cycle RETALIATION guard.
     */
    protected void closeCycle() {
        resetCycleFields();
        beginState(STATE_TENSION);
    }

    /**
     * The close-out WITHOUT the TENSION dwell -- for a caller that begins a different state
     * straight away (complexity review S2, 2026-11-08: a Midan landing during DE-ESCALATION used
     * {@link #closeCycle()}, whose TENSION dwell roll spent {@code lastTalksCollapsed} on a dwell
     * that was discarded one line later, and the log showed a transition that never happened).
     */
    protected void resetCycleFields() {
        cyclesCompleted++;
        generalEngagement = false;
        offenderFactionId = null;
        victimFactionId = null;
        incidentTypeId = null;
        incidentSiteMarketId = null;
    }

    /**
     * sec 3.2.5 CONTAINED: the border playbook absorbed the incident. No ceasefire talks, no
     * DE_ESCALATION, no cut, no fast decay -- the victim lodges a formal protest and the cycle
     * closes. The only player-facing trace is the incident telegraph's closing update; the
     * telegraph already exists and already updates itself, so no new intel is created.
     *
     * <p>Consequences, all deliberate (sec 3.2.5): interdiction resumes at the TENSION rate at
     * once (there is no talks + de-escalation pause), and recovery convoys -- strike-triggered
     * under the same amendment -- still fly for this cycle's one strike.
     */
    protected void containCycle() {
        containedCycles++;
        ExileHegLeagueIncidentIntel telegraph = findLiveIncidentIntel();
        if (telegraph != null) {
            telegraph.markContained();
        }
        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League CONTAINED: cycle " + cyclesCompleted
                        + " | offender=" + offenderFactionId + " victim=" + victimFactionId
                        + " | tension " + format(tension)
                        + " | contained so far " + containedCycles
                        + ", escalations so far " + escalationsReached);
        closeCycle();
    }

    /** Test pins (forceState 1/3/4/5): one line each time a pinned state closes its cycle. */
    protected void logPinnedCycleClose(String stateName) {
        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League TEST: forceState " + config.forceState + " pin -- " + stateName
                        + " exit closes cycle " + cyclesCompleted + " | tension " + format(tension));
    }

    /** One permanent line per RETALIATION exit, so the dial can be tuned from the log. */
    protected void logBranch(String branch, float roll) {
        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League RETALIATION exit: " + branch
                        + " | tension " + format(tension)
                        + " (warning " + format(config.warningThreshold)
                        + ", hawk " + format(config.hawkThreshold) + ")"
                        + (roll >= 0f
                                ? " | roll " + format(roll) + " vs "
                                        + format(config.preemptiveCeasefireChance)
                                : ""));
        debug("Heg-League RETALIATION exit: " + branch + " (tension " + format(tension) + ")");
    }

    /**
     * This cycle's live telegraph, or null. Looked up rather than retained: the intel is saved
     * and self-owning, and a transient reference would be null after a load mid-cycle anyway.
     * Once per cycle, on the slow path -- never per tick.
     */
    protected ExileHegLeagueIncidentIntel findLiveIncidentIntel() {
        List all = Global.getSector().getIntelManager().getIntel(ExileHegLeagueIncidentIntel.class);
        for (int i = 0; i < all.size(); i++) {
            ExileHegLeagueIncidentIntel intel = (ExileHegLeagueIncidentIntel) all.get(i);
            if (intel.isEnded() || intel.isEnding() || intel.isContained()) continue;
            if (offenderFactionId != null
                    && !offenderFactionId.equals(intel.getOffenderFactionId())) continue;
            if (victimFactionId != null
                    && !victimFactionId.equals(intel.getVictimFactionId())) continue;
            return intel;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // PASS 5 -- the intel beats sec 3.4 promises
    // ------------------------------------------------------------------

    /**
     * sec 3.4: "ceasefire talks convened" posts as intel. Called from the SINGLE transition point
     * into {@code STATE_POLITICAL_INTERVENTION}, and guarded on {@link #cyclesCompleted} so a
     * pinned {@code testing.forceState} cannot post one per dwell.
     *
     * <p>The intel carries only the two belligerent ids, the reason, and its own timers -- never
     * a reference to this director, which is why an intel whose director is later disabled still
     * expires on its own backstop.
     */
    protected void postCeasefireIntel() {
        if (config == null || !config.ceasefireIntelEnabled) return;
        if (lastCeasefireIntelCycle != null
                && lastCeasefireIntelCycle.intValue() == cyclesCompleted) {
            return;
        }
        lastCeasefireIntelCycle = new Integer(cyclesCompleted);

        int reason = lastInterventionReason;
        ExileHegLeagueCeasefireIntel intel = new ExileHegLeagueCeasefireIntel(
                ExileHegLeagueConfig.HEGEMONY_ID, ExileHegLeagueConfig.PERSEAN_ID,
                reason, cyclesCompleted, config.interventionDays, config.ceasefireIntelDays);
        // Visible from the first beat: a telegraph exists to be read (the incident intel's rule).
        Global.getSector().getIntelManager().addIntel(intel, false);

        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League ceasefire intel: convened (reason "
                        + getInterventionReasonName(reason) + ") | cycle " + cyclesCompleted);
        debug("Heg-League: ceasefire talks convened (" + getInterventionReasonName(reason) + ").");
    }

    /**
     * sec 3.4: "ceasefire announced". Called immediately after {@link #resolveIntervention()} has
     * set {@link #lastOutcome}. A missing intel (beats disabled, or a cycle that began before this
     * pass existed) is not an error -- the state machine is unchanged either way.
     */
    protected void markCeasefireOutcome() {
        if (config == null || !config.ceasefireIntelEnabled) return;
        ExileHegLeagueCeasefireIntel intel = findLiveCeasefireIntel();
        if (intel == null) return;
        intel.markOutcome(lastOutcome);
        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League ceasefire intel: outcome " + getOutcomeName(lastOutcome)
                        + " | cycle " + cyclesCompleted);
    }

    /** sec 3.2.4 A: "a named operation in the intel". Guarded exactly like the ceasefire beat. */
    protected void postEngagementIntel() {
        if (config == null || !config.generalEngagementIntelEnabled) return;
        if (lastEngagementIntelCycle != null
                && lastEngagementIntelCycle.intValue() == cyclesCompleted) {
            return;
        }
        lastEngagementIntelCycle = new Integer(cyclesCompleted);

        ExileHegLeagueEngagementIntel intel = new ExileHegLeagueEngagementIntel(
                ExileHegLeagueConfig.HEGEMONY_ID, ExileHegLeagueConfig.PERSEAN_ID,
                config.generalEngagementOperationName, escalationsReached, cyclesCompleted,
                config.generalEngagementDays, config.generalEngagementIntelDays);
        Global.getSector().getIntelManager().addIntel(intel, false);

        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League engagement intel: posted (General Engagement #"
                        + escalationsReached + ", " + config.generalEngagementOperationName
                        + ") | cycle " + cyclesCompleted);
        debug("Heg-League: General Engagement intel posted ("
                + config.generalEngagementOperationName + ").");
    }

    /** The closing beat, at the ESCALATION exit. No-op when there is no live engagement intel. */
    protected void endEngagementIntel() {
        if (config == null || !config.generalEngagementIntelEnabled) return;
        ExileHegLeagueEngagementIntel intel = findLiveEngagementIntel();
        if (intel == null) return;
        intel.markEngagementEnded();
        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League engagement intel: ended | cycle " + cyclesCompleted);
    }

    /**
     * This cycle's live ceasefire intel, or null. Looked up rather than retained, exactly like
     * {@link #findLiveIncidentIntel()} -- once per transition, on the slow path, never per tick.
     */
    protected ExileHegLeagueCeasefireIntel findLiveCeasefireIntel() {
        List all = Global.getSector().getIntelManager()
                .getIntel(ExileHegLeagueCeasefireIntel.class);
        for (int i = 0; i < all.size(); i++) {
            ExileHegLeagueCeasefireIntel intel = (ExileHegLeagueCeasefireIntel) all.get(i);
            if (intel.isEnded() || intel.isEnding() || intel.isOutcomeAnnounced()) continue;
            return intel;
        }
        return null;
    }

    /** The General Engagement twin of {@link #findLiveCeasefireIntel()}. */
    protected ExileHegLeagueEngagementIntel findLiveEngagementIntel() {
        List all = Global.getSector().getIntelManager()
                .getIntel(ExileHegLeagueEngagementIntel.class);
        for (int i = 0; i < all.size(); i++) {
            ExileHegLeagueEngagementIntel intel = (ExileHegLeagueEngagementIntel) all.get(i);
            if (intel.isEnded() || intel.isEnding() || intel.isEngagementEnded()) continue;
            return intel;
        }
        return null;
    }

    /** Author-facing name for a {@link ExileHegLeagueCeasefireIntel} REASON_* constant. */
    public static String getInterventionReasonName(int reason) {
        if (reason == ExileHegLeagueCeasefireIntel.REASON_GENERAL_ENGAGEMENT) {
            return "after a General Engagement";
        }
        if (reason == ExileHegLeagueCeasefireIntel.REASON_PREEMPTIVE) {
            return "pre-emptive, from the warning band";
        }
        return "after an ESCALATION";
    }

    protected void beginState(int newState) {
        state = newState;
        // TESTING AID (Run 3 read, 2026-09-21): testing.forceState 3 pins RETALIATION straight
        // from TENSION, so INCIDENT never runs and the parties stay null -- every pinned cycle
        // then logged "RETALIATION skipped: no incident parties" and the squeeze could not be
        // tested at all. Seed the parties here, BEFORE the dwell roll reads the victim's lag.
        // Unreachable in production: the pin is the only way into RETALIATION without an incident.
        if (newState == STATE_RETALIATION && offenderFactionId == null
                && config != null && config.forceState == STATE_RETALIATION) {
            seedPinnedRetaliationParties();
        }
        stateDaysRemaining = rollStateDays(newState);

        if (newState == STATE_INCIDENT) {
            fireIncident();
        } else if (newState == STATE_ESCALATION) {
            // HL-3: pinned re-entries are not consecutive hot cycles (no RETALIATION ever ran).
            if (config == null || config.forceState != STATE_ESCALATION) {
                consecutiveHotCycles++;
            }
            escalationsReached++;
            // THE MEASUREMENT LINE for cycle.preemptiveCeasefireChance (sec 14.5): tune the dial
            // from how many of these a campaign actually produces, not from the estimate.
            Global.getLogger(ExileHegLeagueDirector.class).info(
                    "Exiles Heg-League ESCALATION #" + escalationsReached
                            + " reached | cycle " + cyclesCompleted
                            + " | day " + (int) Global.getSector().getClock()
                                    .getElapsedDaysSince(startTs)
                            + " | tension " + format(tension)
                            + " | contained cycles so far " + containedCycles);
            generalEngagement = tension >= config.generalEngagementThreshold
                    || (config.generalEngagementHotCycles > 0
                            && consecutiveHotCycles >= config.generalEngagementHotCycles);
            if (generalEngagement) {
                stateDaysRemaining = config.generalEngagementDays;
                debug("Heg-League: GENERAL ENGAGEMENT declared (tension " + format(tension)
                        + ", " + consecutiveHotCycles + " consecutive hot cycles).");
                postEngagementIntel();
            }
        } else if (newState == STATE_POLITICAL_INTERVENTION) {
            postCeasefireIntel();
        }

        String text = "Heg-League -> " + getStateName(newState)
                + " (" + (int) stateDaysRemaining + "d, tension " + format(tension)
                + ", cycle " + cyclesCompleted + ")"
                + (offenderFactionId != null
                        ? " [offender " + shortName(offenderFactionId)
                                + ", victim " + shortName(victimFactionId) + "]"
                        : "");
        debug(text);
        Global.getLogger(ExileHegLeagueDirector.class).info("Exiles " + text);
    }

    protected float rollStateDays(int forState) {
        if (forState == STATE_TENSION) {
            // DIAGNOSTIC (2026-09-19, sec 14.6): run 2's twenty dwell draws averaged 84 from a
            // 60-180 uniform (p ~ 1e-6) while run 1's averaged 121 on identical code; the only
            // known difference was x50 game speed. The raw draw and the bounds in force are
            // logged so the next run separates "RNG skew" from "bounds not what the file says".
            float raw = random.nextFloat();
            float base = config.tensionDwellDaysMin
                    + raw * (config.tensionDwellDaysMax - config.tensionDwellDaysMin);
            if (config.tensionDwellDaysMax <= config.tensionDwellDaysMin) {
                base = config.tensionDwellDaysMin;
            }
            Global.getLogger(ExileHegLeagueDirector.class).info(
                    "Exiles Heg-League TENSION dwell roll: raw " + format(raw)
                            + " x [" + format(config.tensionDwellDaysMin) + ", "
                            + format(config.tensionDwellDaysMax) + "] -> " + format(base)
                            + (lastTalksCollapsed ? " (halved: talks collapsed)" : ""));
            if (lastTalksCollapsed) {
                base *= config.tensionDwellCollapseMult;
                lastTalksCollapsed = false;
            }
            return Math.max(1f, base);
        }
        if (forState == STATE_INCIDENT) {
            return 0f; // instant state (sec 3.2.2)
        }
        if (forState == STATE_RETALIATION) {
            // Asymmetry (sec 3.2.3): the lag belongs to the side that must answer.
            ExileHegLeagueConfig.Side side = config.sideFor(victimFactionId);
            float lag = side != null ? side.retaliationLagDays : config.hegemony.retaliationLagDays;
            return Math.max(1f, lag);
        }
        if (forState == STATE_ESCALATION) {
            return Math.max(1f, config.escalationWindowDays);
        }
        if (forState == STATE_POLITICAL_INTERVENTION) {
            return Math.max(1f, config.interventionDays);
        }
        return Math.max(1f, config.deescalationDays);
    }

    /**
     * sec 3.2.2 state 2 -- <b>the INCIDENT TABLE (PASS 3)</b>.
     *
     * <p>Pass 1 shipped this as an offender coin-flip plus a flat tension gain. Pass 3 gives it the
     * weighted table sec 3.2.2 calls for: the entry decides the severity multiplier on the ratified
     * {@code perIncident} gain, may bias which side is at fault, and supplies the prose for the
     * rumour-then-confirmation telegraph.
     *
     * <p><b>Offender and victim are still FACTIONS, not markets.</b> The site the telegraph names
     * is FLAVOUR: it is picked through every R6 guard so the prose can never name a strain market
     * or a capital, but nothing mechanical reads it. The victim's actual answer is the RETALIATION
     * strike, which picks its own target through its own guards.
     *
     * <p><b>Backward compatible:</b> with {@code incidents.enabled} false the table is skipped
     * entirely and the behaviour is exactly Pass 1's.
     */
    protected void fireIncident() {
        ExileHegLeagueIncident type = pickIncidentType();

        float hegChance = config.incidentOffenderHegemonyChance;
        if (type != null && type.hegemonyOffenderChance >= 0f) {
            hegChance = type.hegemonyOffenderChance;
        }

        MarketAPI site = seedParties(hegChance);
        incidentTypeId = type != null ? type.id : null;

        // sec 3.2.1: incidents are "moderate; scaled by incident severity". The ratified gain is
        // perIncident; severity is the table's multiplier on it and the shipped values average
        // ~1.03, so no ratified number is being quietly rebalanced here.
        float severity = type != null ? type.severity : 1f;
        String reason = "incident" + (type != null ? " (" + type.id + " x" + severity + ")" : "");
        if (type != null && type.isMidanFalseFlag()) {
            // Pass-7 seam: a false flag is Midan interference, and sec 3.2.1 gives that its own,
            // larger gain. Unreachable until Pass 7 adds such an entry.
            addTension(config.tensionPerMidanInterference, reason);
        } else {
            addTension(config.tensionPerIncident * severity, reason);
        }

        postIncidentIntel(type, site);

        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League INCIDENT: " + (type != null ? type.id : "(table off)")
                        + " | offender=" + offenderFactionId + " victim=" + victimFactionId
                        + " | severity=" + severity + " site="
                        + (incidentSiteMarketId == null ? "none" : incidentSiteMarketId)
                        + " | tension now " + format(tension));
    }

    /**
     * The offender coin ({@code testing.forceIncidentOffender} when set, else the given chance),
     * both parties, and the FLAVOUR-ONLY site (once per cycle, never per tick). Shared by
     * {@link #fireIncident()} and {@link #seedPinnedRetaliationParties()} since the 2026-11-08
     * complexity review (tier 3); the two copies had already drifted in comments only.
     *
     * @return the site picked for the telegraph, or null
     */
    protected MarketAPI seedParties(float hegemonyChance) {
        boolean hegemonyOffends;
        if (ExileHegLeagueConfig.HEGEMONY_ID.equals(config.forceIncidentOffender)) {
            hegemonyOffends = true;
        } else if (ExileHegLeagueConfig.PERSEAN_ID.equals(config.forceIncidentOffender)) {
            hegemonyOffends = false;
        } else {
            hegemonyOffends = random.nextFloat() < hegemonyChance;
        }
        offenderFactionId = hegemonyOffends
                ? ExileHegLeagueConfig.HEGEMONY_ID : ExileHegLeagueConfig.PERSEAN_ID;
        victimFactionId = hegemonyOffends
                ? ExileHegLeagueConfig.PERSEAN_ID : ExileHegLeagueConfig.HEGEMONY_ID;
        MarketAPI site = ExileHegLeagueGeography.pickAnyEligibleMarket(
                config, victimFactionId, random);
        incidentSiteMarketId = site != null ? site.getId() : null;
        return site;
    }

    /**
     * TESTING AID: the parties a pinned RETALIATION would have inherited from an INCIDENT.
     *
     * <p>Same offender rule as {@link #fireIncident()} -- {@code testing.forceIncidentOffender}
     * when set, otherwise the ratified coin -- but no incident type, no tension gain, no telegraph
     * intel: the pin is testing the punitive op, not the incident table. The site is picked only
     * so the log line reads like a real cycle's. Called once per pinned cycle, because
     * {@link #closeCycle()} clears the parties and the pin re-enters this state.
     */
    protected void seedPinnedRetaliationParties() {
        seedParties(config.incidentOffenderHegemonyChance);
        incidentTypeId = null;

        String source = (config.forceIncidentOffender != null
                && config.forceIncidentOffender.length() > 0)
                ? "testing.forceIncidentOffender" : "the offender coin";
        debug("TEST: RETALIATION pinned with no incident; seeded offender "
                + shortName(offenderFactionId) + ", victim " + shortName(victimFactionId)
                + " from " + source + ".");
        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League TEST: RETALIATION pinned with no incident -- seeded"
                        + " offender=" + offenderFactionId + " victim=" + victimFactionId
                        + " from " + source
                        + " | site=" + (incidentSiteMarketId == null ? "none" : incidentSiteMarketId)
                        + " | cycle " + cyclesCompleted);
    }

    /**
     * Weighted pick over the eligible table entries.
     *
     * <p>Eligibility is asked of the ENTRY, not decided here ({@link ExileHegLeagueIncident#isEligible}).
     * That is the Pass-7 seam: the Midan false flag's state gate, cooldown and per-campaign cap
     * live on its own subclass, and this method never learns they exist.
     *
     * @return the chosen entry, or null when the table is off or empty.
     */
    protected ExileHegLeagueIncident pickIncidentType() {
        if (!config.incidentsEnabled || config.incidents == null) return null;

        if (config.forceIncidentType != null && config.forceIncidentType.length() > 0) {
            ExileHegLeagueIncident forced =
                    ExileHegLeagueIncident.find(config.incidents, config.forceIncidentType);
            if (forced != null) {
                debug("TEST: forcing incident type " + forced.id + ".");
                return forced;
            }
            Global.getLogger(ExileHegLeagueDirector.class).warn(
                    "Exiles Heg-League: testing.forceIncidentType \"" + config.forceIncidentType
                            + "\" matches no table entry; rolling normally");
        }

        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        for (int i = 0; i < config.incidents.size(); i++) {
            ExileHegLeagueIncident entry = (ExileHegLeagueIncident) config.incidents.get(i);
            if (entry == null) continue;
            if (!entry.isEligible(this, config)) continue;
            picker.add(entry, entry.weight);
        }
        return (ExileHegLeagueIncident) picker.pick();
    }

    /**
     * sec 3.2.2 / sec 3.4: the incident "posts as rumour-then-confirmation intel". Bounded and
     * self-expiring -- the intel owns both timers and removes itself (sec 3.3).
     */
    protected void postIncidentIntel(ExileHegLeagueIncident type, MarketAPI site) {
        if (!config.incidentTelegraphIntel) return;
        ExileHegLeagueIncidentIntel intel = new ExileHegLeagueIncidentIntel(
                type, offenderFactionId, victimFactionId, site,
                config.incidentConfirmDays, config.incidentIntelDays);
        // Visible from the first beat, unlike the ops: a telegraph exists to be read. The
        // boolean is forceNoMessage -- false lets the rumour raise the usual intel notification.
        Global.getSector().getIntelManager().addIntel(intel, false);
    }

    /**
     * sec 3.2.2 state 5. The doves move, and the roll is weighted by tension, both sides' war
     * weariness, and -- once Phase 2 exists -- quest flags, which are reserved as first-class
     * inputs here. <b>This state is THE player-lever seam.</b>
     *
     * <p>Pass 1 rolls the outcome and moves the meter. The "how the cycle's ops actually went"
     * input arrived with the 2026-09-19 NPC outcome feed (sec 3.2.6 ruling 3), and <b>Pass 5 adds
     * the two player-facing beats</b> -- {@link #postCeasefireIntel()} at the transition in and
     * {@link #markCeasefireOutcome()} immediately after this roll. The per-outcome condition
     * lifting is still unbuilt. (Terminology R15: a TEMPORARY CEASEFIRE, never a "summit".)
     */
    protected void resolveIntervention() {
        float standDown = config.outcomeStandDownWeight
                + (wearinessHegemony + wearinessPersean) * config.wearinessStandDownWeight;
        if (generalEngagement) {
            // sec 3.2.4 A: a General Engagement ALWAYS ends at the table with elevated stand-down
            // odds. Mutual exhaustion; the doves seize the crisis.
            standDown *= config.outcomeGeneralEngagementStandDownMult;
        }
        float partial = config.outcomePartialWeight;
        float collapse = config.outcomeCollapseWeight
                + Math.max(0f, tension - config.hawkThreshold)
                        * config.outcomeCollapseTensionWeight;

        if (standDown < 0f) standDown = 0f;
        if (partial < 0f) partial = 0f;
        if (collapse < 0f) collapse = 0f;
        float total = standDown + partial + collapse;
        if (total <= 0f) {
            standDown = 1f;
            total = 1f;
        }

        float pick = random.nextFloat() * total;
        if (pick < standDown) {
            lastOutcome = OUTCOME_STAND_DOWN;
            addTension(-config.interventionStandDownCut, "ceasefire: holds in full");
            consecutiveHotCycles = 0;
        } else if (pick < standDown + partial) {
            lastOutcome = OUTCOME_PARTIAL;
            addTension(-config.interventionPartialCut, "ceasefire: holds in part");
        } else {
            lastOutcome = OUTCOME_COLLAPSE;
            lastTalksCollapsed = true;
            // Both halves of sec 3.2.1 apply: collapse is a minimal CUT (the meeting still
            // happened) and collapsing talks are themselves listed as a tension GAIN. Net
            // movement is upward at the shipped defaults, which is the intended reading.
            addTension(-config.interventionCollapseCut, "ceasefire: collapses");
            addTension(config.tensionPerTalksCollapse, "talks collapsed");
        }

        debug("Heg-League ceasefire outcome: " + getOutcomeName(lastOutcome)
                + " (weights standDown " + format(standDown) + " / partial " + format(partial)
                + " / collapse " + format(collapse) + ").");
    }

    // ------------------------------------------------------------------
    // Pass 7 step 7d-2: a Midan strike resolved -- the landing table
    // ------------------------------------------------------------------

    /**
     * Called once by {@link ExileHegLeagueMidanStrikeIntel} when its strike ends, landed or foiled.
     * The module advances the ladder and writes the breadcrumb; this director lands the rung in the
     * war cycle.
     */
    public void onMidanStrikeResolved(int rungIndex, String targetMarketId, boolean landed,
                                      boolean playerPresent, boolean playerFought,
                                      boolean ajaxLost) {
        if (config == null) loadConfig();
        if (config == null) return;
        float nowDay = startTs != 0L
                ? Global.getSector().getClock().getElapsedDaysSince(startTs) : 0f;
        if (midan == null) midan = new ExileHegLeagueMidanModule();
        midan.onStrikeResolved(config, rungIndex, landed, playerPresent, playerFought, ajaxLost,
                nowDay);
        landMidanRung(rungIndex, targetMarketId, landed);
    }

    /**
     * LANDING IN THE WAR CYCLE (war doc sec 6.1, ruled 2026-10-19). The tension effect always
     * applies -- a landed rung raises the meter to AT LEAST its ruled target (50 / 65 / 88), a
     * foiled one adds {@code perMidanInterference} -- then the state decides the rest:
     * <ul>
     * <li>TENSION: the rung becomes the INCIDENT (League offender, Hegemony victim) and the cycle runs
     * on into RETALIATION, so the Hegemony answers.</li>
     * <li>DE_ESCALATION: cooling ends; the cycle closes and the rung opens the next one the same way.</li>
     * <li>INCIDENT / RETALIATION / ESCALATION / General Engagement: it folds in -- no second incident;
     * the higher meter steers the crisis's exit. Rung 3 upgrades a plain ESCALATION to a General
     * Engagement (GE is otherwise only checked when ESCALATION begins).</li>
     * <li>POLITICAL_INTERVENTION: the talks collapse at once (the existing COLLAPSE outcome), then the
     * raise. The spoiler attack.</li>
     * </ul>
     * <b>War off</b> (switched off or idle): the wound already stands; tension and incident are
     * skipped (the HL-1 precedent: in-flight ops finish, the meter is frozen).
     */
    protected void landMidanRung(int rungIndex, String targetMarketId, boolean landed) {
        String outcome = landed ? "LANDED" : "FOILED";
        if (!isActive() || tension < 0f) {
            Global.getLogger(ExileHegLeagueDirector.class).info(
                    "Exiles Midan LANDING SKIPPED: rung " + (rungIndex + 1) + " " + outcome
                            + " while the war is not active -- the wound stands; tension and"
                            + " incident skipped");
            return;
        }
        int stateBefore = state;
        float tensionBefore = tension;
        MarketAPI site = targetMarketId != null
                ? Global.getSector().getEconomy().getMarket(targetMarketId) : null;
        String effect;

        if (state == STATE_POLITICAL_INTERVENTION) {
            lastOutcome = OUTCOME_COLLAPSE;
            lastTalksCollapsed = true;
            addTension(-config.interventionCollapseCut, "ceasefire: collapses (Midan strike)");
            addTension(config.tensionPerTalksCollapse, "talks collapsed (Midan strike)");
            markCeasefireOutcome();
            applyMidanTension(rungIndex, landed);
            beginState(STATE_DE_ESCALATION);
            effect = "the talks COLLAPSED";
        } else if (state == STATE_TENSION || state == STATE_DE_ESCALATION) {
            if (state == STATE_DE_ESCALATION) resetCycleFields(); // cooling ends; a new cycle opens
            applyMidanTension(rungIndex, landed);
            fireMidanIncident(site);
            beginState(STATE_RETALIATION);
            effect = "became the INCIDENT (Hegemony retaliates)";
        } else {
            applyMidanTension(rungIndex, landed);
            effect = "folded into the crisis";
            if (landed && rungIndex == 2 && state == STATE_ESCALATION && !generalEngagement) {
                generalEngagement = true;
                stateDaysRemaining = config.generalEngagementDays;
                debug("Heg-League: GENERAL ENGAGEMENT declared (Midan rung 3).");
                postEngagementIntel();
                effect = "folded in; ESCALATION upgraded to GENERAL ENGAGEMENT";
            }
        }

        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Midan LANDING: rung " + (rungIndex + 1) + " " + outcome
                        + " | state " + getStateName(stateBefore) + " -> " + getStateName(state)
                        + " | tension " + format(tensionBefore) + " -> " + format(tension)
                        + " | " + effect + " | cycle " + cyclesCompleted);
    }

    /** Landed: raise to AT LEAST the rung's target. Foiled: +perMidanInterference. */
    protected void applyMidanTension(int rungIndex, boolean landed) {
        if (landed) {
            float target = config.midanRaiseFor(rungIndex);
            if (target > 0f && tension < target) {
                float before = tension;
                tension = clampTension(target);
                Global.getLogger(ExileHegLeagueDirector.class).info(
                        "Exiles Heg-League tension " + format(before) + " -> " + format(tension)
                                + " (Midan rung " + (rungIndex + 1)
                                + " landed: raised to at least " + format(target) + ")");
            }
        } else {
            addTension(config.tensionPerMidanInterference,
                    "Midan rung " + (rungIndex + 1) + " foiled");
        }
    }

    /** The rung as this cycle's incident: League offender, Hegemony victim, the struck world as
     *  the site. No tension of its own -- {@link #applyMidanTension} already moved the meter. */
    protected void fireMidanIncident(MarketAPI site) {
        ExileHegLeagueIncident type = new ExileHegLeagueMidanIncident();
        offenderFactionId = ExileHegLeagueConfig.PERSEAN_ID;
        victimFactionId = ExileHegLeagueConfig.HEGEMONY_ID;
        incidentTypeId = type.id;
        incidentSiteMarketId = site != null ? site.getId() : null;
        postIncidentIntel(type, site);
        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League INCIDENT: " + type.id + " | offender=" + offenderFactionId
                        + " victim=" + victimFactionId + " | severity=Midan site="
                        + (incidentSiteMarketId == null ? "none" : incidentSiteMarketId)
                        + " | tension now " + format(tension));
    }

    public ExileHegLeagueMidanModule getMidanModule() {
        return midan;
    }

    // ------------------------------------------------------------------
    // Public hooks for later passes
    // ------------------------------------------------------------------

    public int getState() {
        return state;
    }

    public float getStateDaysRemaining() {
        return stateDaysRemaining;
    }

    public float getTension() {
        return tension;
    }

    public int getCyclesCompleted() {
        return cyclesCompleted;
    }

    public boolean isGeneralEngagement() {
        return generalEngagement;
    }

    public int getLastOutcome() {
        return lastOutcome;
    }

    /** The side that caused the current cycle's incident, or null outside a live cycle. */
    public String getOffenderFactionId() {
        return offenderFactionId;
    }

    /** The side entitled to retaliate this cycle, or null outside a live cycle. */
    public String getVictimFactionId() {
        return victimFactionId;
    }

    /** Pass 3: the INCIDENT-table entry id that opened this cycle, or null. */
    public String getIncidentTypeId() {
        return incidentTypeId;
    }

    /** Pass 3: the market the telegraph named. FLAVOUR -- do not gate an op on it. */
    public String getIncidentSiteMarketId() {
        return incidentSiteMarketId;
    }

    /** Pass 3: cycle whose RETALIATION op has already been mounted; -1 = none.
     *  The strike manager owns this; the director only stores it, so the once-per-cycle guard
     *  survives a save/load the way the Ashen rehearsal's does. */
    public int getLastRetaliationCycle() {
        return lastRetaliationCycle == null ? -1 : lastRetaliationCycle.intValue();
    }

    public void setLastRetaliationCycle(int cycle) {
        this.lastRetaliationCycle = new Integer(cycle);
    }

    public float getWeariness(String factionId) {
        if (ExileHegLeagueConfig.HEGEMONY_ID.equals(factionId)) return wearinessHegemony;
        if (ExileHegLeagueConfig.PERSEAN_ID.equals(factionId)) return wearinessPersean;
        return 0f;
    }

    /**
     * sec 3.2.1: cumulative fleet/market losses for one side. Later passes call this from op
     * outcomes and from the per-kill bounty path -- the player's mercenary work is a real,
     * quiet input to the meter.
     */
    public void addWeariness(String factionId, float amount) {
        if (amount == 0f) return;
        if (ExileHegLeagueConfig.HEGEMONY_ID.equals(factionId)) {
            wearinessHegemony = Math.max(0f, wearinessHegemony + amount);
        } else if (ExileHegLeagueConfig.PERSEAN_ID.equals(factionId)) {
            wearinessPersean = Math.max(0f, wearinessPersean + amount);
        }
    }

    /** The logged variant: the periodic meter line is debug-only (UI), so without this the
     *  weariness trajectory is invisible in the log and cannot be tuned from. */
    public void addWeariness(String factionId, float amount, String reason) {
        if (amount == 0f) return;
        addWeariness(factionId, amount);
        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League weariness Heg " + format(wearinessHegemony)
                        + " / League " + format(wearinessPersean)
                        + " (" + shortName(factionId) + " " + (amount > 0 ? "+" : "")
                        + format(amount) + ": " + reason + ")");
    }

    /**
     * sec 3.2.6 ruling 3 (2026-09-19): the NPC OUTCOME FEED. "How the cycle's ops actually went"
     * finally reaches the doves without the player having to fire a shot. A landed op wounds the
     * side it was aimed at; an op that did not land cost the side that mounted it. Called from the
     * two op intels' reportOutcome() -- the same seam that already reports completion tension --
     * so nothing new is listened for.
     */
    public void reportOperationWeariness(String actorFactionId, String victimFactionId,
                                         boolean landed, boolean strike) {
        reportOperationWeariness(actorFactionId, victimFactionId, landed,
                strike ? OP_TYPE_STRIKE : OP_TYPE_INTERDICTION);
    }

    /**
     * The same feed, with an op TYPE rather than a boolean (Pass 4). A third op type made the
     * boolean a lie, and a second boolean beside it would have been worse; the two Pass-2/3 call
     * sites keep their exact behaviour through the overload above.
     *
     * <p>Pass 4 (decision D5): a blockade that holds its window wounds the blockaded side by
     * {@code weariness.perBlockadeSuffered} -- between a completed interdiction and a landed
     * strike. One that is driven off costs the actor the shared {@code perOpFailed}, like any
     * other op.
     */
    public void reportOperationWeariness(String actorFactionId, String victimFactionId,
                                         boolean landed, int opType) {
        if (config == null) return;
        if (landed) {
            float amount;
            String reason;
            if (opType == OP_TYPE_STRIKE) {
                amount = config.wearinessPerStrikeSuffered;
                reason = "strike landed on";
            } else if (opType == OP_TYPE_BLOCKADE) {
                amount = config.wearinessPerBlockadeSuffered;
                reason = "blockade completed against";
            } else if (opType == OP_TYPE_PLANETARY) {
                // Pass 6 (D80.1): a cordon that held its window over one colony. Sits between a
                // completed interdiction (4) and a system blockade (8).
                amount = config.wearinessPerSqueezeSuffered;
                reason = "squeeze completed against";
            } else {
                amount = config.wearinessPerInterdictionSuffered;
                reason = "interdiction completed against";
            }
            addWeariness(victimFactionId, amount, reason + " " + shortName(victimFactionId));
        } else {
            String noun;
            String verb;
            if (opType == OP_TYPE_STRIKE) {
                noun = "strike";
                verb = " did not land";
            } else if (opType == OP_TYPE_BLOCKADE) {
                noun = "blockade";
                verb = " did not hold";
            } else if (opType == OP_TYPE_PLANETARY) {
                noun = "squeeze";
                verb = " did not hold";
            } else {
                noun = "interdiction";
                verb = " did not land";
            }
            addWeariness(actorFactionId, config.wearinessPerOpFailed,
                    noun + " by " + shortName(actorFactionId) + verb);
        }
    }

    /**
     * The live tuning config, or null before the director's first tick.
     *
     * <p>Exposed for Pass 2 so the operation managers read ONE parsed config with ONE timeScale
     * application, rather than each re-parsing the JSON and re-scaling their own copy. Callers
     * must null-check: this is null until {@link #loadConfig()} has run.
     */
    public ExileHegLeagueConfig getConfig() {
        return config;
    }

    /** Pass 2+: an operation ran to completion. Small upward pressure. */
    public void reportOperationCompleted(String actorFactionId) {
        if (config == null) return;
        addTension(config.tensionPerCompletedOp, "operation completed by "
                + shortName(actorFactionId));
    }

    /** The meter mover of record. Clamps to [floor, cap] and logs every real movement. */
    public void addTension(float amount, String reason) {
        if (config == null || amount == 0f) return;
        float before = tension;
        tension = clampTension(tension + amount);
        if (tension == before) return;
        Global.getLogger(ExileHegLeagueDirector.class).info(
                "Exiles Heg-League tension " + format(before) + " -> " + format(tension)
                        + " (" + reason + ")");
    }

    /** Shared debug channel so every subsystem obeys one config flag. */
    public void debug(String text) {
        if (config == null || !config.debugMessages || Global.getSector() == null) return;
        Global.getSector().getCampaignUI().addMessage("DEBUG: " + text, Color.YELLOW);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public static String getStateName(int forState) {
        if (forState == STATE_TENSION) return "1 TENSION";
        if (forState == STATE_INCIDENT) return "2 INCIDENT";
        if (forState == STATE_RETALIATION) return "3 RETALIATION";
        if (forState == STATE_ESCALATION) return "4 ESCALATION";
        if (forState == STATE_POLITICAL_INTERVENTION) return "5 POLITICAL INTERVENTION";
        if (forState == STATE_DE_ESCALATION) return "6 DE-ESCALATION";
        return "? UNKNOWN";
    }

    /**
     * Terminology RULED 2026-09-13: the mechanism that ends a crisis is a TEMPORARY CEASEFIRE
     * brokered by the doves, never a peace summit -- top-level peace talks are Phase-2 quest
     * territory (the secret envoy). The constant names keep their Pass-1 spelling; only the
     * words the author reads change.
     */
    public static String getOutcomeName(int outcome) {
        if (outcome == OUTCOME_STAND_DOWN) return "CEASEFIRE HOLDS (full stand-down)";
        if (outcome == OUTCOME_PARTIAL) return "partial ceasefire";
        if (outcome == OUTCOME_COLLAPSE) return "CEASEFIRE COLLAPSED";
        return "none";
    }

    protected static String shortName(String factionId) {
        if (ExileHegLeagueConfig.HEGEMONY_ID.equals(factionId)) return "Hegemony";
        if (ExileHegLeagueConfig.PERSEAN_ID.equals(factionId)) return "League";
        return String.valueOf(factionId);
    }

    protected float clampTension(float value) {
        float floor = config != null ? config.tensionFloor : 0f;
        float cap = config != null ? config.tensionCap : 100f;
        if (value < floor) return floor;
        if (value > cap) return cap;
        return value;
    }

    protected static String format(float value) {
        return "" + (Math.round(value * 100f) / 100f);
    }

    // ------------------------------------------------------------------
    // Config + Nexerelin layers
    // ------------------------------------------------------------------

    /** Once per game load: {@code config} is null only after construction or {@code readResolve},
     *  so the Nex layers sync exactly once per load here (the old once-flag was redundant). */
    protected void loadConfig() {
        config = ExileHegLeagueConfig.load();
        syncNexLayers();
        // Pass 8a-1: the stop helper is static and the stages that call it are saved objects
        // with no config reference, so its tunables are pushed here, once per load.
        ExileHegLeaguePlayerStop.configure(config);
        ExileHegLeaguePlayerStop.applyTestGrants(config);
    }

    /**
     * Applies or removes the switch-gated Nexerelin layers, once per game load (sec 7.2, R10/R11).
     *
     * <p>All three are reversible: turning a switch off and reloading removes what Exiles
     * installed. Nothing here can reach a Nexerelin class unless Nexerelin is actually enabled
     * -- see {@link ExileHegLeagueNexSupport} for how that is guaranteed.
     */
    protected void syncNexLayers() {
        String pin = ExileHegLeagueNexSupport.syncRelationPin(config);
        if (pin != null) {
            debug(pin);
            Global.getLogger(ExileHegLeagueDirector.class).info("Exiles Heg-League: " + pin);
        }
        String shields = ExileHegLeagueNexSupport.syncConquestShields(config);
        if (shields != null) {
            debug(shields);
            Global.getLogger(ExileHegLeagueDirector.class).info("Exiles Heg-League: " + shields);
        }
    }
}
