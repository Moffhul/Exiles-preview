package data.scripts.exiles;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

/**
 * Tuning config for the Hegemony-Persean League war director -- loaded from
 * {@code data/config/exiles/director_config.json}, top-level key {@code "exilehegleague"}
 * (naming ruled R12, docs/EXILES_HEG_LEAGUE_WAR.md sec 9).
 *
 * <p>Class form rather than the Ashen/BF inline {@code loadConfig()} because the reuse map
 * (sec 13) calls for it at this option count -- {@link OmicronDirectorConfig} is the shape
 * copied here.
 *
 * <p><b>NEVER persisted.</b> The director holds this in a {@code transient} field and reloads
 * it on every launch, so tuning edits land on restart without a new game. Code defaults below
 * mirror the ratified values in the spec, so a missing or broken JSON degrades to shipped
 * behaviour rather than crashing.
 *
 * <p><b>SCOPE DISCIPLINE.</b> Only the keys a BUILT pass actually reads are declared here and
 * shipped in the JSON -- the same discipline the Ashen block follows; shipping keys that silently
 * do nothing is worse than shipping none. Pass 1 added the master/tension/cycle/asymmetry/nex
 * keys. <b>Pass 2 adds the {@code interdiction} block, the geography lists it consults
 * ({@code exemptSystems}, {@code excludedMarkets}, {@code allowedMarkets},
 * {@code distanceFalloff}), the per-side {@code opWeights.interdiction} /
 * {@code maxConcurrentOps} / {@code presenceDaysMult}, and {@code wearinessPerFpDestroyed}.</b>
 * Pass 3 adds {@code incidents} and {@code surgicalStrike}. <b>Pass 4 adds the {@code blockade}
 * block, {@code opWeights.blockade}, {@code weariness.perBlockadeSuffered}, and BOTH author
 * whitelists -- {@code blockadeWhitelistSystems} (read by Pass 4) and
 * {@code planetaryBlockadeWhitelistMarkets} (captured now, read by Pass 6).</b>
 * <b>Pass 5 adds the {@code intel} block (the ceasefire and General Engagement beats).</b>
 * <b>Pass 6 adds the {@code planetaryBlockade} and {@code recoveryConvoys} blocks,
 * {@code opWeights.planetary}, {@code recoveryDaysMult} and
 * {@code weariness.perSqueezeSuffered}, and reads the {@code planetaryBlockadeWhitelistMarkets}
 * list Pass 4 transcribed.</b>
 * Pass 7 added the {@code midan} block (the false-flag ladder and its proxy). Still absent, by
 * design: {@code playerInterception} (Pass 8). Complexity review 2026-11-08 removed four keys the
 * code never read; see docs/EXILES_HEG_LEAGUE_COMPLEXITY_REVIEW.md sec 5.1.
 */
public class ExileHegLeagueConfig {

    public static final String PATH = "data/config/exiles/director_config.json";
    public static final String CONFIG_KEY = "exilehegleague";

    public static final String HEGEMONY_ID = "hegemony";
    public static final String PERSEAN_ID = "persean";

    /**
     * One Midan rung's wound (Pass 7 step 7d-2): which industries go down and for how long (max
     * semantics), and which of them the Hegemony's relief convoy may shorten, by how many days.
     * Config-only, never saved.
     */
    public static class MidanRungPayload {
        public ArrayList industryIds = new ArrayList();        // String
        public ArrayList industryDays = new ArrayList();       // Float, parallel to industryIds
        public ArrayList reliefIndustryIds = new ArrayList();  // String
        public float reliefDays = 0f;
        /** 7e-2: this rung is Eventide's wound (array, mirrors, farmland, bombardment), not an
         *  industry list. See {@link ExileEventideWound}. */
        public boolean eventideWound = false;

        public void add(String industryId, float days) {
            industryIds.add(industryId);
            industryDays.add(Float.valueOf(days));
        }

        /** Days for this industry, or 0 if the payload does not name it. */
        public float daysFor(String industryId) {
            for (int i = 0; i < industryIds.size(); i++) {
                if (industryIds.get(i).equals(industryId)) {
                    return ((Float) industryDays.get(i)).floatValue();
                }
            }
            return 0f;
        }
    }

    /** One belligerent's asymmetry knobs (sec 3.2.3). */
    public static class Side {
        public String factionId;
        /**
         * Bureaucratic lag between an incident and this side's punitive answer, in days --
         * the RETALIATION dwell when this side is the victim. Hegemony slow, League quick.
         */
        public float retaliationLagDays;
        /**
         * {@code opWeights.interdiction} (sec 9). Relative chance this side is the one that mounts
         * a given interdiction op. This is where sec 3.2.3's asymmetry lives for Pass 2: the
         * League is commerce-focused and leans on interdiction; the Hegemony prefers blockades and
         * production strikes and contributes fewer. 0 = this side never interdicts.
         */
        public float interdictionWeight;
        /** sec 9. Concurrent ops this side may run during ESCALATION. TENSION uses the shared
         *  low-rate ambient cap instead ({@code interdiction.tensionMaxConcurrentOpsPerSide}). */
        public int maxConcurrentOps;
        /** sec 3.2.3: "longer op durations" for the Hegemony, "shorter ops" for the League.
         *  Multiplies {@code interdiction.presenceDays} for ops this side mounts. */
        public float presenceDaysMult;
        /**
         * {@code opWeights.surgical} (sec 9). Relative chance this side mounts a given ESCALATION
         * counter-strike. The mirror image of {@link #interdictionWeight}: sec 3.2.3 gives
         * "production strikes" to the Hegemony and commerce raiding to the League, so the Hegemony
         * leads here. 0 = this side never counter-strikes.
         *
         * <p>It does NOT gate the RETALIATION strike -- that one is mounted by whichever side the
         * cycle's incident wronged, which is not a weighted choice.
         */
        public float surgicalWeight;
        /**
         * {@code opWeights.blockade} (sec 9, Pass 4). Relative chance this side is the one that
         * mounts a given ESCALATION system blockade. sec 3.2.3 gives blockades to the Hegemony,
         * so it leads here (3 : 1) -- which is the real driver of who blockades more, since the
         * whitelisted system map is near parity at 3 : 2. 0 = this side never blockades.
         */
        public float blockadeWeight;
        /**
         * {@code opWeights.planetary} (sec 9, Pass 6). Relative chance, against
         * {@link #surgicalWeight}, that this side's RETALIATION answer is a PLANETARY SQUEEZE
         * rather than a surgical strike (decision D80.1). The mirror of
         * {@link #interdictionWeight}: a squeeze is trade disruption aimed at one colony, which
         * is the League's line, so the League leads here 3 : 1 exactly as the Hegemony leads on
         * surgical. 0 = this side never squeezes.
         *
         * <p>It does NOT choose the actor -- the RETALIATION op is always mounted by whichever
         * side the cycle's incident wronged. It only chooses that side's WEAPON.
         */
        public float planetaryWeight;
        /**
         * {@code recoveryDaysMult} (sec 9, Pass 6, decision D80.5 item 9). Fraction of a landed
         * strike's relief budget that this side's recovery convoys actually deliver -- sec 3.2.3's
         * "industrial advantage = faster market recovery" as one multiplier.
         *
         * <p><b>Clamped to &lt;= 1.0 at load.</b> Above 1 a convoy would heal a wound BELOW the
         * design-normal {@code disruptDaysMin/Max} band, which breaks sec 4.6's floor.
         *
         * <p>Not a constructor argument: it is set per side in {@link #fillDefaults()}, because
         * both {@code Side} instances are field initialisers and a ninth positional argument
         * would make them unreadable.
         */
        public float recoveryDaysMult = 1f;

        public Side(String factionId, float retaliationLagDays, float interdictionWeight,
                    int maxConcurrentOps, float presenceDaysMult, float surgicalWeight,
                    float blockadeWeight, float planetaryWeight) {
            this.factionId = factionId;
            this.retaliationLagDays = retaliationLagDays;
            this.interdictionWeight = interdictionWeight;
            this.maxConcurrentOps = maxConcurrentOps;
            this.presenceDaysMult = presenceDaysMult;
            this.surgicalWeight = surgicalWeight;
            this.blockadeWeight = blockadeWeight;
            this.planetaryWeight = planetaryWeight;
        }
    }

    // --- Master ---
    public boolean enabled = true;
    /** PLAYER OPTION; false in production (Sean, 2026-11-08). The code default was true until the
     *  complexity review: a missing JSON would have turned the yellow DEBUG lines on for players. */
    public boolean debugMessages = false;
    /**
     * Days between periodic "here is the meter" debug lines. 0 = only state transitions.
     * A testing aid: sec 3.4 rules that internal meter movements are never player-facing, and
     * this whole channel is off in production with {@code debugMessages}.
     */
    public float debugMeterIntervalDays = 30f;
    /**
     * How often the "do both belligerents still hold markets?" premise check runs, in days.
     *
     * <p>A PERFORMANCE cadence, not a design knob. R6c forbids hardcoding ownership, so the
     * check must be live -- but it costs a {@code getMarketsCopy()} allocation and CLAUDE.md
     * sec 7 forbids allocating inside {@code advance()}. Raising it is free; lowering it below
     * a day buys nothing, since a faction losing its last market is not a same-day event.
     */
    public float premiseCheckIntervalDays = 5f;
    /** sec 9.1 convention: multiplies EVERY duration at load. 1.0 = production. */
    public float timeScale = 1f;
    public float startDelayDays = 0f;

    // --- testing{} (sec 9.1). All inert at these defaults. ---
    /** 1..6 PINS the machine to that state (it does not jump once); -1 = normal. */
    public int forceState = -1;
    /** 0..100 forces the meter; -1 = normal. */
    public float tensionOverride = -1f;
    /** "hegemony" / "persean" forces every incident's offender; "" = normal. */
    public String forceIncidentOffender = "";
    /** An incident-table id ("border_clash", ...) forces every incident to that entry; "" = roll.
     *  Pass 3 testing aid -- the cheap way to see one entry's telegraph text without waiting for
     *  the weighted picker to offer it. */
    public String forceIncidentType = "";
    /** Pass 7 step 7b testing aid: on every game load, post one sample story announcement with
     *  Admiral Cunningham's portrait, to check the shared announcement intel and the portrait
     *  registration without waiting for a rung. false = normal. Never commit true. */
    public boolean testPostStoryAnnouncement = false;
    /** Pass 7 step 7c testing aid: Midan is ready immediately (skips firstReadyDays and any
     *  rebuild). false = normal. Never commit true. */
    public boolean testMidanReadyNow = false;
    /** Pass 7 step 7d-2 testing aid: once ready, launch without waiting for the opportunity score.
     *  The hard rails (feasibility, one in flight, ownership, Battered) still apply. Never commit
     *  true. */
    public boolean testMidanLaunchNow = false;
    /** 7e-1 test pin: start the ladder at this 1-based rung (0 = off). Applied once, when the
     *  module starts in a new game. Never commit non-zero. */
    public int testMidanStartRung = 0;
    /** 7f test pin: every relief record writes Midan's directive (the 0.5 roll always succeeds,
     *  while none is active). Never commit true. */
    public boolean testMidanForceDirective = false;
    /** Pass 8a test pin: the next interdiction launch attempt after each game load ignores the
     *  cadence wait (feasibility and caps still apply). Never commit true. */
    public boolean testInterdictionNow = false;
    /** Pass 8a test pins: a faction id ("hegemony" / "persean") sets that faction's
     *  authorization-codes / official-business flag for 30 days on every game load, so the two
     *  reserved stop options can be seen. "" = normal. Never commit non-empty. */
    public String testGrantAuthCodes = "";
    public String testGrantOfficialBusiness = "";
    /** Pass 8a test measurement: days between STOP DIAG log lines for an interdiction or squeeze
     *  the player is in the same system as (what each fleet is ordered to do, what it is chasing,
     *  and the flags vanilla's hassle script filters on). 0 = off. Never commit above 0. */
    public float testStopDiagnosticsDays = 0f;

    // --- playerInterception{} (Pass 8a-1, docs/EXILES_PASS8_PLAN.md sec 11.5) ---
    // Pushed onto ExileHegLeaguePlayerStop's statics by the director; see that class.
    /** PLAYER OPTION / kill switch. Off = no fleet is marked and the marks lapse in two days. */
    public boolean stopEnabled = true;
    public float stopTollPerFleetPoint = 200f;
    public float stopTollFloor = 2000f;
    public float stopTollCap = 50000f;
    public float stopTollMultHegemony = 1f;
    public float stopTollMultPersean = 1f;
    public float stopNegotiateMult = 0.5f;
    public float stopCaughtLieMult = 2f;
    public float stopCreditsPerSupply = 150f;
    public float stopLieBaseChance = 0.5f;
    public float stopLieRepShift = 0.5f;
    public float stopLieMinChance = 0.2f;
    public float stopLieMaxChance = 0.8f;
    public float stopRepCaughtLie = 2f;
    public float stopRepFacedDown = 3f;
    /** A RepLevel name. Neither reputation loss can push the player below it. */
    public String stopRepFloor = "INHOSPITABLE";
    public float stopInspectionWeightInspect = 0.2f;
    public float stopInspectionWeightCordon = 0.3f;
    /** NOT scaled by timeScale: a real grace period for the player, not a war duration. */
    public float stopRaiderLowRepImpactDays = 2f;

    // --- midan{} (Pass 7, war doc sec 6.1; build plan docs/EXILES_PASS7_BUILD_PLAN.md) ---
    // midan.proxy{} (Pass 7 step 7f; war doc sec 6.2): the directive to the Black Fleet.
    public boolean midanProxyEnabled = true;
    public float midanProxyDirectiveChance = 0.5f;
    /** PLAYER OPTION: false keeps the war but stops Midan's strikes and directives. */
    public boolean midanEnabled = true;
    public float midanFirstReadyDays = 365f;
    public float midanRebuildDays = 730f;
    public float midanRebuildFailedDays = 1095f;
    public float midanStrikeMarginMult = 2.0f;
    public float midanFpCeiling = 320f; // 300 -> 320 (2026-11-01): the main fleet grew to 302 FP
    /** Step 7c ONLY: the planned roster's FP, until 7d's fleet builder measures the real fleet. */
    public float midanFleetFpEstimate = 277f;
    public ArrayList midanTargets = new ArrayList(); // String market ids, ladder order
    public ArrayList midanLaunchAnchors = new ArrayList(); // String station ENTITY ids, preference order
    public float midanMeasureIntervalDays = 30f;
    public float midanMeansWeight = 0.4f;
    public float midanDistractionWeight = 0.3f;
    public float midanCoverWeight = 0.3f;
    public float midanDistractionOpsFull = 2f;
    public float midanCoverTalks = 0.5f;
    public float midanBaselineWeightPerMeasure = 0.25f;
    public float midanBarStart = 0.6f;
    public float midanBarFloor = 0.15f;
    public float midanBarDecayDays = 365f;
    // midan.strikeForce{} (Pass 7 step 7d-1): the composer's roster. Lists hold one variant id per
    // ship (a count of 3 appears three times). Code defaults are the ruled roster (plan sec 1.5a);
    // the JSON replaces a list wholesale when it carries one.
    public String midanFleetName = "Strike Force";
    /** The fleet carrying the flagship, shown WITHOUT a faction prefix (Sean, 2026-11-03). */
    public String midanFlagshipFleetName = "Strike Force Ajax";
    public String midanFlagshipVariant = "exiles_midan_conquest_ajax";
    public String midanFlagshipName = "Ajax";
    public ArrayList midanRoster = new ArrayList();            // the main fleet, flagship excluded
    public ArrayList midanOfficerShips = new ArrayList();      // which roster ships get an officer
    public int midanOfficerLevel = 5;
    public int midanCaptainLevel = 7;
    public ArrayList midanCaptainSkillIds = new ArrayList();   // parallel lists: id, level (2 = elite)
    public ArrayList midanCaptainSkillLevels = new ArrayList();
    public ArrayList midanAdmiralSkills = new ArrayList();
    /** CURRENTLY UNREACHED: {@link #midanEscortCapPerRung} ships with an entry for every rung, so
     *  this fallback never applies. Kept "just in case" (Sean, 2026-11-08, complexity review). */
    public int midanEscortCap = 2;
    /** Per-rung escort cap, ladder order (Integer); a rung past the list uses {@link #midanEscortCap}.
     *  RULED 2026-10-31 (option A): 2 / 4 / 4 -- Midan sends a stronger task force to make sure the
     *  later blows land, with the 2x margin unchanged. */
    public ArrayList midanEscortCapPerRung = new ArrayList();
    public ArrayList midanEscortRoster = new ArrayList();
    public ArrayList midanEscortOfficerShips = new ArrayList();
    public int midanEscortOfficerLevel = 5;
    // midan.strike{} (Pass 7 step 7d-2): the launched operation. Build plan sec 7, "7d-2 plan".
    /** Build gate: rungs above this stay log-only (WOULD LAUNCH) until their payloads exist.
     *  7d-2 built rung 1; 7e-1 rung 2 (Sphinx); 7e-2 rung 3 (Eventide). */
    public int midanMaxLaunchRung = 3;
    /** Sneak attack (ruled 2026-10-30): the Strike Force forms up INSIDE the target system, this
     *  many units from the target, on the bearing farthest from the jump points, the star and the
     *  player. Abstract travel runs 1500 units a day, so 7000 is about 4.7 days of approach. */
    public float midanStagingDistance = 7000f;
    /** The target's emergency call goes out when the first fleet is this many days out. */
    public float midanDistressLeadDays = 2f;
    /** Prep days for a strike launched by a quest trigger ($exiles_midan_rungN_trigger). */
    public float midanQuestPrepDays = 0.5f;
    /** One-time Hegemony reputation for an Ajax the player helped destroy (ruled 2026-10-30: 3,
     *  vanilla's MEDIUM). On top of the per-battle bounty reputation. */
    public float midanAjaxKillRep = 3f;
    public float midanPrepDaysMin = 3f;
    public float midanPrepDaysMax = 6f;
    /** Losses abort line, as a fraction of the launched FP (the strike's 0.5 by default). */
    public float midanAbortFraction = 0.5f;
    /** Spare days vanilla lets a raid stage overrun before it fails (vanilla default 60). 7e-1 test
     *  (2026-11-01): a stuck assembly sat 66 days. ASSISTANT DEFAULT 5, not ruled. */
    public float midanExtraDays = 5f;
    /** The strike window once in-system (the ExileHegLeagueStrikeStage window-end rules). */
    public float midanStrikeMaxDays = 10f; // ruled 2026-10-30 (was 45, then 5): see director_config
    public float midanOrbitDays = 3f;
    public int midanUnrestPoints = 2;
    public float midanSystemCooldownDays = 60f;
    /** Ruled 2026-10-30: the Hegemony pays its normal bounty for Strike Force kills. */
    public float midanBountyPerFrigate = 3000f;
    /** Landed rung N raises tension to at least this (ruled 50 / 65 / 88). One per rung. */
    public ArrayList midanTensionRaiseTo = new ArrayList(); // Float
    /** Per-rung payloads, ladder order: MidanRungPayload. Rungs 1-2 since 7e-1. */
    public ArrayList midanRungPayloads = new ArrayList();
    // midan.eventide{} (Pass 7 step 7e-2; war doc sec 6.1 rung 3, ruled 2026-10-14; beats and pace by
    // Sean 2026-11-01). The permanent wound of rung 3, applied by ExileEventideWound.
    /** Ordered beats: entity NAME (unique; vanilla reuses the ids) and action "destroy" or "adrift". */
    public ArrayList eventideBeatNames = new ArrayList();   // String
    public ArrayList eventideBeatActions = new ArrayList(); // String, parallel
    /** On-screen: game days between beats (ruled ~0.6). The bombardment is the last beat. */
    public float eventideBeatDays = 0.6f;
    /** Eventide's new description (descriptions.csv CUSTOM id) and illustration key (settings.json
     *  graphics/illustrations); an empty illustration keeps vanilla's until Sean's art exists. */
    public String eventidePlanetDescId = ""; // OFF since 2026-11-01 (the sat-bomb does the work); row kept: exiles_planet_eventide_struck
    public String eventideIllustration = "";
    public String eventideAdriftDescId = "exiles_eventide_wrecked_mirror";
    /** Cunningham's vow (ruled 2026-10-19): days after a LANDED rung 3, and days on the board. */
    public float eventideVowDelayDays = 5f;
    public float eventideVowNoticeDays = 30f;

    // --- tension{} (sec 3.2.1) ---
    public float tensionStart = 25f;
    /** The nanoforge grievance is permanent: the meter never decays below this (R2). */
    public float tensionFloor = 15f;
    public float tensionCap = 100f;
    /** 0.05 -> 0.03 with the 2026-09-13 containment amendment (sec 3.2.5): once contained
     *  cycles stop paying the ceasefire cut, quiet decay was the second oversized sink. */
    public float tensionDecayPerDay = 0.03f;
    /** 4 -> 8, RULED 2026-09-19 after the first organic-escalation trace (sec 3.2.6): at 4 the
     *  30-day "fast decay" was worth 3.6 points, no cooling at all against a +10.5 cycle. */
    public float deescalationDecayMult = 8f;
    /** RETALIATION -> ESCALATION branch. At or above it the hardliners get their surge. */
    public float hawkThreshold = 60f;
    /**
     * Containment amendment (sec 3.2.5, RATIFIED 2026-09-13). Below this the RETALIATION exit is
     * CONTAINED: the border playbook absorbs the incident and the cycle returns straight to
     * TENSION with no ceasefire talks and no cut. Between this and {@link #hawkThreshold} the
     * doves roll {@link #preemptiveCeasefireChance} to convene talks pre-emptively.
     */
    public float warningThreshold = 45f;
    /** sec 3.2.4 A: General Engagement super-state entry by meter... */
    public float generalEngagementThreshold = 85f;
    /** ...or by N consecutive cycles that reached ESCALATION. 0 disables that route. */
    public int generalEngagementHotCycles = 3;

    // Gains (sec 3.2.1 "Up"). Pass 1 moves only the incident and talks-collapse ones;
    // the rest are the seams later passes call through reportX() on the director.
    public float tensionPerIncident = 8f;
    public float tensionPerCompletedOp = 2f;
    public float tensionPerMidanInterference = 12f;
    public float tensionPerTalksCollapse = 4f;

    // Cuts (sec 3.2.2 state 5 outcomes).
    public float interventionStandDownCut = 30f;
    public float interventionPartialCut = 12f;
    public float interventionCollapseCut = 2f;

    // --- War weariness (sec 3.2.1). Per faction; the quiet failsafe. ---
    /**
     * Weariness fades per day. 0 was the original "purely cumulative" reading; RE-RULED
     * 2026-09-13 (sec 3.2.5): once ceasefire talks are rare, unbounded weariness would make every
     * one of them a near-certain stand-down and push ESCALATION further away the longer a
     * campaign runs. Weariness accrues only from player-inflicted losses today
     * ({@link #wearinessPerFpDestroyed}), so 0.025/day lets a 200-FP kill (10 points) fade over
     * ~400 days, about two to three cycles.
     *
     * <p>0.025 -> 0.10, RULED 2026-09-19 (sec 3.2.6): the NPC outcome feed below accrues on a
     * ~10x larger scale than player kills, and the decay has to match it or weariness ratchets.
     * A bloody escalation (~50 points across both sides) now fades over ~500 days.
     */
    public float wearinessDecayPerDay = 0.10f;
    /** Stand-down weight added per point of TOTAL (both sides) weariness at intervention. */
    public float wearinessStandDownWeight = 0.02f;
    /**
     * The NPC outcome feed, BUILT 2026-09-19 (sec 3.2.6 ruling 3). The first organic trace ran
     * twelve landed strikes and 24 completed ops with weariness at exactly zero, because the only
     * feed was player kills -- the designed damper was absent from an NPC-only war. Outcome-based
     * rather than per-FP: no battle listener, deterministic, and it is the spec's own vocabulary
     * ("how the cycle's ops actually went"). Amounts, never timeScaled.
     */
    /** Added to the STRUCK side when a surgical strike lands (industries disrupted, unrest). */
    public float wearinessPerStrikeSuffered = 12f;
    /** Added to the VICTIM side when an interdiction completes its presence window. */
    public float wearinessPerInterdictionSuffered = 4f;
    /** Added to the ACTOR side when its op ends without landing -- driven off, aborted on
     *  losses, or timed out. The timed-out case over-counts slightly; accepted and documented. */
    public float wearinessPerOpFailed = 8f;
    /** Pass 4 (D5): added to the VICTIM side when a system blockade holds its payload window.
     *  Sits between a completed interdiction (4) and a landed strike (12). */
    public float wearinessPerBlockadeSuffered = 8f;
    /** Pass 6 (D80.1): added to the VICTIM side when a planetary squeeze holds its presence
     *  window. Sits between a completed interdiction (4) and a system blockade (8). */
    public float wearinessPerSqueezeSuffered = 6f;

    // --- cycle{} (sec 3.2.2 dwell times) ---
    public float tensionDwellDaysMin = 60f;
    public float tensionDwellDaysMax = 180f;
    /** Multiplier on the next TENSION dwell after talks collapse ("shorter next TENSION"). */
    public float tensionDwellCollapseMult = 0.5f;
    public float escalationWindowDays = 45f;
    public float generalEngagementDays = 75f;
    /** Ceasefire-talks dwell. Not numbered in the spec; sec 3.2.2 requires every state to have one. */
    public float interventionDays = 20f;
    public float deescalationDays = 30f;
    /**
     * Containment amendment (sec 3.2.5): chance, rolled once at the RETALIATION exit when tension
     * sits in the warning band, that the doves convene ceasefire talks before the hawks get their
     * surge. <b>The tuning dial for how often the war boils over</b> -- lower = more ESCALATIONs.
     * A probability, so it is never timeScaled. See the sec 14.5 arithmetic for what 0.30 buys.
     */
    public float preemptiveCeasefireChance = 0.30f;
    /** Chance the offender of a given incident is the Hegemony. 0.5 = symmetric. */
    public float incidentOffenderHegemonyChance = 0.5f;

    // --- interventionOutcomeWeights{} ---
    public float outcomeStandDownWeight = 1f;
    public float outcomePartialWeight = 2f;
    public float outcomeCollapseWeight = 1f;
    /**
     * Collapse weight added per tension point above {@link #hawkThreshold}. 0.05 -> 0, RULED
     * 2026-09-19 (sec 3.2.6): "an angrier sector is harder to talk down" was the positive-feedback
     * term that kept the first organic trace in a 55-85 band for 1,050 days -- collapse nets +2
     * and halves the next quiet stretch, so making it likelier above the hawk line locked the war
     * hot. Kept as a knob at 0; the fiction is now carried by weariness instead.
     */
    public float outcomeCollapseTensionWeight = 0f;
    /** Stand-down weight multiplier when the cycle ran as a General Engagement (sec 3.2.4 A). */
    public float outcomeGeneralEngagementStandDownMult = 3f;

    // --- Per-side asymmetry (sec 3.2.3) ---
    // Hegemony: slow to start, hard to stop; methodical, longer ops; fewer interdictions because
    // its contributions skew toward blockades and production strikes (Passes 3-4).
    public Side hegemony = new Side(HEGEMONY_ID, 20f, 1f, 2, 1.25f, 3f, 3f, 1f);
    // League: quick, light, commerce-focused. It fights the way a trade league would.
    public Side persean = new Side(PERSEAN_ID, 10f, 3f, 2, 0.8f, 1f, 1f, 3f);

    // ------------------------------------------------------------------
    // PASS 2 -- interdiction raids (sec 4.1) and the geography they consult (sec 2 / R6)
    // ------------------------------------------------------------------

    public boolean interdictionEnabled = true;
    /** Days between launch ATTEMPTS during TENSION -- the ambient, "low-rate" war. Jittered
     *  +/-25% at roll time, and scaled by {@link #timeScale}. */
    public float interdictionCadenceDays = 90f;
    /** Multiplier on that cadence during ESCALATION: sec 3.2.2 asks for "multiple interdiction
     *  ops" in the surge window, so the clock runs faster there. */
    public float interdictionEscalationCadenceMult = 0.35f;
    /** Concurrent ops PER SIDE during TENSION. ESCALATION uses each side's own
     *  {@code maxConcurrentOps} instead. */
    public int interdictionTensionMaxConcurrentPerSide = 1;
    /** GUESSES until the launch measurement line has been read (sec 5: instrument before tuning). */
    public float interdictionSpawnFpMin = 120f;
    public float interdictionSpawnFpMax = 200f;
    /** THE ship-farming guardrail: 2 = destroyers (S6 / R3). Do not raise without a ruling. */
    public int interdictionMaxShipSize = 2;
    /** Base presence window; multiplied by the acting side's {@code presenceDaysMult}. */
    public float interdictionPresenceDays = 30f;
    /** Anti-hotspot respite AFTER an op ends (see the manager's SYSTEM_COOLDOWN_KEY note). */
    public float interdictionSystemCooldownDays = 60f;
    public float interdictionAbortFraction = 0.5f;
    /** Per-fleet FP ceiling -- what stops one very large fleet bee-lining into an orbital
     *  station. 0 restores vanilla sizing. */
    public float interdictionMaxFleetFp = 45f;
    /** False = loiter at the system centre (default, and the low-risk reading of sec 4.1
     *  "never touch planets"); true = sit on the focus market's orbit. */
    public boolean interdictionLoiterOnFocusMarket = false;
    /** Keep raiders off every orbital station in the system. See the stage's javadoc for why
     *  no FP balance can substitute for this. */
    public boolean interdictionSuppressStationAttacks = true;
    /** Units. While an objective (comm relay, nav buoy, sensor array) is this close to a market
     *  hostile to the raiders that has an orbital station, vanilla's war simulation is stopped
     *  from sending the raiders' side to take it. 0 = off (vanilla). Ruled 2026-11-18; the value
     *  is a first guess. See the stage's javadoc. */
    public float interdictionObjectiveStationKeepOut = 3000f;
    /** sec 4.7: credits the RAIDED side pays per frigate-equivalent raider killed; 0 disables. */
    public float interdictionBountyPerFrigate = 3000f;

    // Condition tunables, pushed onto the condition's statics by the manager at load.
    public float interdictionConditionDelayDays = 5f;
    public float interdictionConditionDays = 45f;
    public float interdictionAccessibilityPenalty = 0.5f;
    public int interdictionImportHitCount = 3;
    public int interdictionImportHitUnits = 2;

    /**
     * sec 4.7: war weariness added to the RAIDING side per fleet point of its own losses that the
     * player caused. Not a duration and not a probability, so {@link #timeScale} leaves it alone.
     * Lives at block level rather than inside {@code interdiction} because every later op type
     * pays into the same ledger.
     */
    public float wearinessPerFpDestroyed = 0.05f;

    // ------------------------------------------------------------------
    // PASS 3 -- the INCIDENT TABLE (sec 3.2.2 state 2) and its telegraph intel (sec 3.4)
    // ------------------------------------------------------------------

    /** Master switch for the weighted table. OFF = the Pass-1 fallback: an unnamed incident that
     *  rolls an offender and adds the flat {@code perIncident} gain. */
    public boolean incidentsEnabled = true;
    /** Post {@link ExileHegLeagueIncidentIntel} (rumour -> confirmation). Off = the meter and the
     *  cycle still move; the player just never reads about it. */
    public boolean incidentTelegraphIntel = true;
    /** Days from the rumour beat to the confirmation update. */
    public float incidentConfirmDays = 5f;
    /** How long the incident intel lingers before it ends itself. */
    public float incidentIntelDays = 60f;
    /** The table itself: {@link ExileHegLeagueIncident}. Pass 7 adds the Midan false flag here. */
    public ArrayList incidents = ExileHegLeagueIncident.defaultTable();

    // ------------------------------------------------------------------
    // PASS 3 -- SURGICAL STRIKES on military industries (sec 4.3)
    // ------------------------------------------------------------------

    public boolean strikeEnabled = true;
    /** sec 3.2.2 state 3: the victim's ONE punitive op per crisis cycle. */
    public boolean strikeRetaliationEnabled = true;
    /** sec 4.3: strikes also serve as ESCALATION counter-strikes. Own switch -- see the sec 14.4
     *  deviation note on why the path ships in this pass rather than in Pass 4. */
    public boolean strikeEscalationCounterStrikes = true;
    /** Days between ESCALATION counter-strike attempts, jittered +/-25%. */
    public float strikeEscalationCadenceDays = 30f;
    /** Concurrent counter-strikes per side during ESCALATION. */
    public int strikeEscalationMaxConcurrentPerSide = 1;

    /** GUESSES until the launch measurement line has been read (sec 5: instrument before tuning).
     *  Calibration note: the Ashen rehearsal lands at 120-160 FP against Tri-Tachyon defenses of
     *  0-246 with stations reading ~98-120; Heg/League Core worlds measured 205-812 in the Pass-2
     *  interdiction trace, and this op has to go THROUGH the station rather than around it. */
    public float strikeSpawnFpMin = 250f;
    public float strikeSpawnFpMax = 400f;
    /** sec 3.2.2 state 3: the punitive op is "sized by tension". Fraction added to the rolled FP
     *  at the tension CAP; 0 at the floor, linear between. 0 disables the scaling. */
    public float strikeTensionFpScale = 0.5f;
    /** Ship-size cap. 3 = cruisers by author ruling 2026-08-28, superseding S6 for STRIKES ONLY
     *  (interdiction keeps 2). FP unchanged; XIV-cruiser recovery accepted pending Sean's own
     *  scavenger work. */
    public int strikeMaxShipSize = 3;
    /** Per-fleet FP ceiling. Higher than interdiction's 45 on the Ashen rehearsal's reasoning: a
     *  strike is supposed to arrive as a striking force, not a scattering of raiding packs. */
    public float strikeMaxFleetFp = 90f;
    /** Anti-hotspot respite AFTER the op ends; shares the interdiction cooldown key on purpose. */
    public float strikeSystemCooldownDays = 60f;
    public float strikeAbortFraction = 0.5f;
    /** How long the strike group will keep trying before it gives up and goes home. */
    public float strikeMaxDays = 45f;
    /** sec 4.7: credits the STRUCK side pays per frigate-equivalent striker killed; 0 disables. */
    public float strikeBountyPerFrigate = 3000f;

    /** Industry offline time on a landed strike, before {@link #strikeExtendedDisruptMult}. */
    public float strikeDisruptDaysMin = 45f;
    public float strikeDisruptDaysMax = 90f;
    /**
     * sec 4.6's extended-baseline hook: strikes are eventually meant to set a deliberately LONGER
     * disruption that Pass 6's recovery convoys drip back down to the design-normal figure.
     *
     * <p>Pass 6's recovery convoys are built, and the ratified config value is 2.0; the code
     * default now matches it (bug sweep HL-11, 2026-10-06), so a lost key no longer silently
     * halves strike disruption. Before Pass 6 this shipped at 1.0, because above 1 with no convoys
     * the wound would simply have been longer with nothing to heal it.
     */
    public float strikeExtendedDisruptMult = 2f;

    // THE STRIKE PACKAGE -- author ruling 2026-08-28, superseding the one-industry read and the
    // old maxIndustries/industries keys (their saved-state twins remain on the stage class for
    // XStream compatibility, unread). See director_config.json for the player-facing contract.
    /** Tension at launch at/above which bucket (b), ground defense, is included. 0 = always. */
    public float strikeGroundDefenseBucketTension = 0f;
    /** Tension at launch at/above which bucket (c), war economy, is included. 0 = always. */
    public float strikeEconomicBucketTension = 0f;
    /** Bucket (c) candidates in priority order. Entries on the never-touch floor are dropped at
     *  load with a warning -- config can reorder or narrow this list but never widen it past the
     *  floor. */
    public ArrayList strikeEconomicIndustries = defaultEconomicIndustries(); // String
    /** Post-strike orbit hold over the struck market, days (the visible beat). 0 = instant
     *  turnaround (the old behaviour). Scaled by timeScale. */
    public float strikeOrbitDays = 3f;
    /** Recent Unrest points on a landed strike (author ruling 2026-08-28). 0 disables. */
    public int strikeUnrestPoints = 2;

    private static ArrayList defaultEconomicIndustries() {
        ArrayList list = new ArrayList();
        list.add("fuelprod");
        list.add("heavyindustry");
        list.add("orbitalworks");
        list.add("refining");
        list.add("mining");
        return list;
    }

    /** Industries no strike may EVER touch, whatever the config says (sec 4.3 + the 2026-08-28
     *  ruling's own exclusion list: they cannot directly support a war effort). POPULATION is
     *  additionally excluded in the stage itself. */
    public static boolean isStrikeForbiddenIndustry(String id) {
        return "population".equals(id) || "spaceport".equals(id) || "megaport".equals(id)
                || "farming".equals(id) || "aquaculture".equals(id)
                || "lightindustry".equals(id) || "commerce".equals(id)
                || "techmining".equals(id);
    }

    // ------------------------------------------------------------------
    // PASS 4 -- SYSTEM BLOCKADES (sec 4.2, plan sec 14.9)
    // ------------------------------------------------------------------

    public boolean blockadeEnabled = true;
    /** One launch attempt per side the moment ESCALATION begins (sec 14.9.2). */
    public boolean blockadeLaunchAtEscalationEntry = true;
    /** Days between further attempts inside the ESCALATION window, jittered +/-25%. */
    public float blockadeCadenceDays = 30f;
    public int blockadeMaxActivePerSide = 1;
    /** sec 3.2.4's "simultaneous blockades" under a General Engagement. */
    public int blockadeGeneralEngagementMaxActivePerSide = 2;
    /** Vanilla difficulty points, one entry per fleet: one anchor plus pickets. */
    public ArrayList blockadeFleetSizes = defaultBlockadeFleetSizes(); // Integer
    public float blockadePrepDays = 3f;
    /** Base payload (gates-held) window; multiplied by the acting side's presenceDaysMult (D7). */
    public float blockadePayloadDays = 45f;
    /** Vanilla's own default; BlockadeFGI tiers it by relative strength (0 / half / full). */
    public float blockadeAccessibilityPenalty = 0.6f;
    /** Route-damage fraction at which the blockade breaks off. 0.67 since 2026-09-28 (Sean's
     *  ruling) to agree with vanilla's live group abort (survivors below 33% of spawned FP), which
     *  is what really ends an on-screen blockade -- vanilla never writes route damage for live
     *  blockade fleets. See ExileHegLeagueBlockade.isBrokenByLosses. */
    public float blockadeAbortFraction = 0.67f;
    /** D3: estimated strength * this must clear the system's live defence. 0 = vanilla (no gate). */
    public float blockadeFeasibilityMult = 1.25f;
    /** D3 (Sean): scale {@link #blockadeFleetSizes} up to this multiple, in 0.1 steps, to clear
     *  the gate before skipping a launch. 1.0 = never scale up. */
    public float blockadeFleetSizesMaxMult = 1.5f;
    /** Anti-hotspot respite counted from when the op ENDS; shares the interdiction cooldown key. */
    public float blockadeSystemCooldownDays = 90f;
    /** D2: the collateral split. false restores pure vanilla behaviour (victim markets only). */
    public boolean blockadeCollateralEnabled = true;
    /** Accessibility penalty on NON-victim markets in the blockaded system. */
    public float blockadeCollateralAccessibilityPenalty = 0.3f;
    /** Follow the blockade's own 0 / half / full tier, so a blockade too weak to hurt the victim
     *  does not hurt the bystanders either. */
    public boolean blockadeCollateralScalesWithTier = true;
    /** Self-expiry after the last refresh -- the orphan backstop (Ruling F discipline). */
    public float blockadeCollateralExpiryDays = 5f;
    /** D6: hidden while preparing and travelling, visible from the moment the gates are held. */
    public boolean blockadeHiddenUntilArrival = true;
    public boolean blockadeSuppressStationAttacks = true;
    /** sec 4.7: credits the BLOCKADED side pays per frigate-equivalent blockader killed. */
    public float blockadeBountyPerFrigate = 3000f;

    /**
     * R6b -- the author's system-blockade whitelist (sec 14.9.1), ONE merged list per D1.
     * Ownership is still queried live: an entry names an acceptable war zone, not an owner.
     */
    public ArrayList blockadeWhitelistSystems = new ArrayList(); // String
    /**
     * R6b -- the author's PLANETARY-blockade whitelist (sec 14.9.1). Captured in Pass 4 so the
     * deliverable is transcribed once; <b>read by Pass 6's squeeze</b> (built 2026-09-20) and
     * reported on the first-boot geography line.
     */
    public ArrayList planetaryBlockadeWhitelistMarkets = new ArrayList(); // String

    // ------------------------------------------------------------------
    // PASS 6a -- PLANETARY BLOCKADES, "the squeeze" (sec 4.4, plan sec 14.11)
    // ------------------------------------------------------------------

    public boolean planetaryEnabled = true;
    /** GUESSES until the launch measurement line has been read (sec 5: instrument before
     *  tuning). The STRIKE's band since the 2026-09-20 ruling: the squeeze fights the station,
     *  so it has to be able to win that fight. */
    public float planetarySpawnFpMin = 250f;
    public float planetarySpawnFpMax = 400f;
    /** sec 3.2.2 state 3: the punitive op is "sized by tension". Mirror of
     *  {@link #strikeTensionFpScale}, same formula. */
    public float planetaryTensionFpScale = 0.5f;
    /** 3 = cruisers, the strike's cap (Sean's 2026-08-28 strike ruling extended to the squeeze
     *  on 2026-09-20, when he ruled it fights the station -- a destroyer cordon cannot crack a
     *  size-6 station). D80.3's destroyer recommendation is superseded; the farming concern is
     *  mitigated by the Refugee Salvage Expeditions claiming the best wrecks first. */
    public int planetaryMaxShipSize = 3;
    /** Per-fleet FP ceiling, the strike's: a cordon that fights a station arrives as a force. */
    public float planetaryMaxFleetFp = 90f;
    /** Base presence window; multiplied by the acting side's {@code presenceDaysMult}. */
    public float planetaryPresenceDays = 40f;
    /** Days into the presence before the cordon starts turning traffic away. Its OWN knob, not
     *  interdiction's shared static -- see {@code ExileHegLeagueSqueezeStage}. */
    public float planetaryConditionApplyDay = 5f;
    public float planetaryAbortFraction = 0.5f;
    /** Anti-hotspot respite AFTER the op ends; shares the interdiction cooldown key. */
    public float planetarySystemCooldownDays = 60f;

    // ------------------------------------------------------------------
    // Feasibility gates in STRENGTH units (sec 14.14 Option A, ruled 2026-09-26; the legacy FP
    // gate and its rollback switch were removed 2026-09-28 by Sean's ruling -- git history keeps
    // them). The interdiction, strike and squeeze gates read
    // defense <= Misc.getAdjustedStrength(fp, source) * <op>FeasibilityStrengthMult, the same
    // vanilla conversion WarSimScript and the arrival check use. The system blockade's gate was
    // always in strength units and keeps blockadeFeasibilityMult.
    // ------------------------------------------------------------------

    /** Median-neutral against the 2026-09-26 measurement: 1.0 = the op needs even odds or better
     *  in real strength, for every source. 0 disables the gate. */
    public float interdictionFeasibilityStrengthMult = 1.0f;
    /** 0.37 = ~2.7:1 real odds (the measured median of the old gate). 0 disables the gate. */
    public float strikeFeasibilityStrengthMult = 0.37f;
    /** The strike's value: same gate shape, same source markets. 0 disables the gate. */
    public float planetaryFeasibilityStrengthMult = 0.37f;

    // ------------------------------------------------------------------
    // "Break the Blockade" victory bonus (sec 14.15, ratified 2026-09-28 in 14.15.11)
    // ------------------------------------------------------------------
    public boolean victoryBonusEnabled = true;
    /** Credits for breaking a system blockade at the reference size and a full share. */
    public float victoryBonusBlockadeBase = 60000f;
    /** Credits for breaking a planetary squeeze at the reference size and a full share. */
    public float victoryBonusSqueezeBase = 40000f;
    /** Blockade size reference: the formation's difficulty points (sum of fleetSizes). 14 = the
     *  shipped [6, 4, 4]. */
    public float victoryBonusRefPointsBlockade = 14f;
    /** Squeeze size reference: launch FP. 330 = the median of the Run 4 squeeze launches. */
    public float victoryBonusRefFpSqueeze = 330f;
    /** Upper clamp on the size multiplier (the lower clamp is 0.5, in code). */
    public float victoryBonusMaxSizeMult = 2f;
    /** Player share of the op's losses below which nothing is paid. */
    public float victoryBonusShareFloor = 0.2f;
    /** Player share at or above which the bonus is paid in full; pro-rated share/this below it. */
    public float victoryBonusShareFull = 0.5f;

    /**
     * The gate value in the units {@code WarSimScript} reports defence in, converted with vanilla's
     * own {@code Misc.getAdjustedStrength} -- the call vanilla's AssembleStage uses for a raid
     * route's strength -- so it tracks vanilla and each source's quality, fleet size and doctrine.
     * Every caller has a source in hand; a null source is treated as k = 1 (raw FP), the most
     * conservative reading, rather than guessed.
     */
    public float feasibilityGate(float fp, com.fs.starfarer.api.campaign.econ.MarketAPI source,
                                 float strengthMult) {
        float strength = source != null
                ? com.fs.starfarer.api.util.Misc.getAdjustedStrength(fp, source) : fp;
        return strength * strengthMult;
    }

    /** Log text for how a gate value was derived (never parsed). */
    public String describeGate(float fp, com.fs.starfarer.api.campaign.econ.MarketAPI source,
                               float strengthMult) {
        float strength = source != null
                ? com.fs.starfarer.api.util.Misc.getAdjustedStrength(fp, source) : fp;
        return "strength " + (int) strength + " (" + (int) fp + " FP"
                + (source == null ? ", no source: k=1" : "") + ") x " + strengthMult;
    }
    /** FALSE since the 2026-09-20 ruling: the squeeze fights the station. True restores the
     *  interdiction-style hands-off cordon for comparison. */
    public boolean planetarySuppressStationAttacks = false;
    public boolean planetaryHiddenUntilArrival = true;
    /** sec 4.7: credits the SQUEEZED side pays per frigate-equivalent squeezer killed. */
    public float planetaryBountyPerFrigate = 3000f;

    // Condition tunables, pushed onto the condition's statics at load.
    public float planetaryAccessibilityPenalty = 0.5f;
    /** Multiplier on what the PLAYER pays for every commodity bought at the squeezed market. */
    public float planetaryPlayerBuyPriceMult = 1.4f;
    /** Orphan backstop only (Ruling F discipline); the stage lifts the condition on withdrawal. */
    public float planetaryConditionExpiryDays = 80f;

    /** D80.1: on an empty target pool for the rolled RETALIATION op type, try the OTHER type
     *  once before declaring the crisis contained. false restores the strict one-roll reading. */
    public boolean planetaryRetaliationFallbackToOtherType = true;
    // ------------------------------------------------------------------
    // PASS 6b -- STRIKE-TRIGGERED RECOVERY CONVOYS (sec 4.6, ruling R16)
    // ------------------------------------------------------------------

    public boolean recoveryEnabled = true;
    /** Concurrent relief convoys, both sides together. */
    public int recoveryMaxActive = 2;
    /** Bureaucratic lag between the wound and the convoy sailing, rolled per record. */
    public float recoveryLagDaysMin = 20f;
    public float recoveryLagDaysMax = 45f;
    /** LOCKED by sec 4.6 ("~10-day unload orbit", author). Also the drip rate's denominator. */
    public float recoveryUnloadDays = 10f;
    /** The recovery floor: a drip never takes an industry below this many disrupted days. */
    public float recoveryMinRemainingDays = 5f;
    /** Bound on outstanding relief records; the oldest is dropped with a log line at the cap. */
    public int recoveryLedgerMaxRecords = 8;
    /** A record nobody could service is dropped and logged after this long. */
    public float recoveryRecordExpiryDays = 400f;
    /** NOT timeScaled: these bound real fleet travel, which does not compress (sec 9.1). */
    public float recoveryTravelTimeoutDays = 180f;
    public float recoveryDepartTimeoutDays = 20f;
    public int recoveryCombatFp = 10;
    public int recoveryFreighterFp = 24;
    public int recoveryTankerFp = 6;
    public int recoveryTransportFp = 8;
    // Relief convoy rebuild (step 7f-2, ruled by Sean 2026-11-03): the relief rides in a fixed
    // number of explicitly added freighters; each carries an equal share of the relief and of the
    // loot manifest. d-mods roll normally, so the load sits under the Compromised Storage ceiling.
    public String recoveryFreighterVariant = "colossus_Standard";
    public int recoveryFreighterCount = 3;
    public float recoveryCargoPerFreighter = 500f;
    public ArrayList recoveryCargoCommodities = new ArrayList(); // String commodity ids
    public ArrayList recoveryCargoShares = new ArrayList();      // Float, parallel
    /** Proximity-gated campaign message on arrival (D80.7). No intel, ever. */
    public boolean recoveryArrivalMessage = true;
    /** D80.7 / sec 11 item 3: the zero-effect "Relief Effort" condition. ON (Sean, 2026-09-20),
     *  matching the config; the "default off" note was stale (bug sweep HL-11). */
    public boolean recoveryReliefEffortCondition = true;
    /** TEST channel for per-record and per-drip lines. False in production (Sean, 2026-11-08). */
    public boolean recoveryDebugMessages = false;

    // ------------------------------------------------------------------
    // PASS 5 -- the intel BEATS sec 3.4 promises (ceasefire + General Engagement)
    // ------------------------------------------------------------------

    /** Post {@link ExileHegLeagueCeasefireIntel}. Off = the outcome roll, the cuts and the state
     *  machine are unchanged; the player simply never reads about the table. */
    public boolean ceasefireIntelEnabled = true;
    /** Days the ceasefire intel lingers after its OUTCOME beat before it ends itself. */
    public float ceasefireIntelDays = 60f;
    /** Post {@link ExileHegLeagueEngagementIntel} when a General Engagement is declared. */
    public boolean generalEngagementIntelEnabled = true;
    /** Days the General Engagement intel lingers after its closing beat. */
    public float generalEngagementIntelDays = 30f;
    /** sec 3.2.4 A's "a named operation in the intel". PLACEHOLDER (sec 11 item 2 / R12). */
    public String generalEngagementOperationName = "Operation PLACEHOLDER";

    private static ArrayList defaultBlockadeFleetSizes() {
        ArrayList list = new ArrayList();
        list.add(new Integer(6));
        list.add(new Integer(4));
        list.add(new Integer(4));
        return list;
    }

    /** R6a -- SYSTEM-level capital exemption. Matched against system id AND base name. */
    public ArrayList exemptSystems = new ArrayList(); // String
    /** R6b -- the refugee strain markets; never a direct victim of any op. */
    public ArrayList excludedMarkets = new ArrayList(); // String
    /** R6c author allow-list. EMPTY = every non-exempt enemy market is fair game. */
    public ArrayList allowedMarkets = new ArrayList(); // String
    /** R6c pool weighting: weight = 1 / (1 + distanceLY * this). 0 = distance-blind. */
    public float distanceFalloff = 0.10f;

    // --- nex{} -- ALL Nexerelin-behaviour suppression is player-switchable (R11 rider) ---
    /** B2: runtime max-relationship pin. Off = removed on the next load. */
    public boolean nexRelationPin = true;
    /** B3: re-assert hostility. Off = the director rides a Nex peace and idles. */
    public boolean nexSelfHealHostility = true;
    /** R11: {@code $nex_npc_no_invade} on the war's poles. Off = our keys removed on load. */
    public boolean nexConquestShields = true;
    /**
     * B2's flat modifier. Nex takes {@code min(mod, otherMod) + baseMax} and clamps at -1, so
     * any value at or below -1.51 holds {@code canCeasefire} false against Nex's own -0.51
     * canonical pin. -1.6 is the ratified value.
     */
    public float nexPinValue = -1.6f;
    /**
     * Markets that keep {@code $nex_npc_no_invade} (R11: the two capitals plus the false-flag
     * permanent-strike targets). Strike targets are an AUTHOR DELIVERABLE and are deliberately
     * absent until Sean's merged list lands -- shipping a guess would silently make a world
     * unconquerable that he never chose.
     */
    public ArrayList nexShieldMarkets = new ArrayList(); // String market ids

    public static ExileHegLeagueConfig load() {
        ExileHegLeagueConfig c = new ExileHegLeagueConfig();
        c.fillDefaults();
        try {
            JSONObject root = Global.getSettings().loadJSON(PATH);
            JSONObject cfg = root.optJSONObject(CONFIG_KEY);
            if (cfg != null) {
                c.read(cfg);
                Global.getLogger(ExileHegLeagueConfig.class).info(
                        "Exiles Heg-League director config loaded from " + PATH);
            } else {
                Global.getLogger(ExileHegLeagueConfig.class).info(
                        "Exiles Heg-League director: no \"" + CONFIG_KEY
                                + "\" block in " + PATH + "; using code defaults");
            }
        } catch (Exception e) {
            Global.getLogger(ExileHegLeagueConfig.class).warn(
                    "Exiles Heg-League director config not loaded (" + e.getMessage()
                            + "); using code defaults");
        }
        c.applyTimeScale();
        return c;
    }

    private void fillDefaults() {
        // Pass 6 (D80.5 item 9), set here rather than as a ninth Side constructor argument:
        // "industrial advantage = faster market recovery" (sec 3.2.3). The Hegemony heals its
        // whole extension; the League heals three-quarters of it and its wounds linger.
        hegemony.recoveryDaysMult = 1f;
        persean.recoveryDaysMult = 0.75f;

        nexShieldMarkets.clear();
        // R11: the two capitals.
        nexShieldMarkets.add("chicomoztoc");
        nexShieldMarkets.add("kazeron");

        // R6a: the capital SYSTEMS, entire. Coatl and Thule's minor markets ride along, and both
        // sides' main ship production is therefore never hit (sec 1.2). Exempt systems remain
        // valid op SOURCES -- this is a targeting rule, not a basing rule.
        exemptSystems.clear();
        exemptSystems.add("aztlan");
        exemptSystems.add("thule");

        // R6b: the five refugee strain markets, never a direct victim of any op.
        excludedMarkets.clear();
        excludedMarkets.add("asharu");
        excludedMarkets.add("mairaath");
        excludedMarkets.add("port_tse");
        excludedMarkets.add("salamanca");
        excludedMarkets.add("new_maxios");

        // R6c author allow-list: empty means "the whole non-exempt enemy pool".
        allowedMarkets.clear();

        // R6b -- the author's blockade whitelists, transcribed in sec 14.9.1 (REVISED by Sean
        // 2026-09-20; that revision is canon). Code defaults mirror the JSON so a broken config
        // degrades to the ratified map rather than to an empty pool that silently launches
        // nothing. Aztlan and Thule are absent deliberately: blockading a capital system would
        // mean all-out war, and R6a is the belt-and-braces in code.
        blockadeWhitelistSystems.clear();
        blockadeWhitelistSystems.add("isirah");      // Hegemony action against the League
        blockadeWhitelistSystems.add("tyle");
        blockadeWhitelistSystems.add("westernesse");
        blockadeWhitelistSystems.add("naraka");      // League action against the Hegemony
        blockadeWhitelistSystems.add("samarra");

        // PASS 6 reads this one; Pass 4 only reports whether the ids resolve.
        planetaryBlockadeWhitelistMarkets.clear();
        planetaryBlockadeWhitelistMarkets.add("athulf");
        planetaryBlockadeWhitelistMarkets.add("cibola");
        planetaryBlockadeWhitelistMarkets.add("fikenhild");
        planetaryBlockadeWhitelistMarkets.add("laicaille_habitat");
        planetaryBlockadeWhitelistMarkets.add("madeira");
        planetaryBlockadeWhitelistMarkets.add("suddene");
        planetaryBlockadeWhitelistMarkets.add("yesod");
        planetaryBlockadeWhitelistMarkets.add("ancyra_market");
        planetaryBlockadeWhitelistMarkets.add("arcadia_station");
        planetaryBlockadeWhitelistMarkets.add("jangala");
        planetaryBlockadeWhitelistMarkets.add("nachiketa");
        planetaryBlockadeWhitelistMarkets.add("raesvelg");
        planetaryBlockadeWhitelistMarkets.add("sphinx");
        planetaryBlockadeWhitelistMarkets.add("yama");

        // Step 7f-2: the relief manifest, 30/40/15/15 (Sean, 2026-11-03), as code defaults.
        recoveryCargoCommodities.clear();
        recoveryCargoShares.clear();
        recoveryCargoCommodities.add("supplies");
        recoveryCargoShares.add(new Float(0.30f));
        recoveryCargoCommodities.add("food");
        recoveryCargoShares.add(new Float(0.40f));
        recoveryCargoCommodities.add("heavy_machinery");
        recoveryCargoShares.add(new Float(0.15f));
        recoveryCargoCommodities.add("metals");
        recoveryCargoShares.add(new Float(0.15f));

        // Pass 7: the ruled ladder and launch-anchor order, as code defaults (the JSON overrides).
        midanTargets.add("arcadia_station");
        midanTargets.add("sphinx");
        midanTargets.add("eventide");
        midanLaunchAnchors.add("exilebf_lundy_station");
        midanLaunchAnchors.add("exilebf_sallee_station");
        midanLaunchAnchors.add("exilemidan_haven");

        // Pass 7 step 7d-1: the ruled Strike Force (plan sec 1.5a, 2026-10-20/24/25).
        addCopies(midanRoster, "exiles_midan_champion_assault", 2);
        addCopies(midanRoster, "exiles_midan_eagle_assault", 3);
        addCopies(midanRoster, "exiles_midan_falcon_attack", 3);
        // 2026-11-01 (Sean): + a support-fleet Conquest and a third Gryphon, so the Ajax's fleet is
        // at least as big as an escort (302 FP vs 272).
        addCopies(midanRoster, "exiles_midan_conquest_standard", 1);
        addCopies(midanRoster, "exiles_midan_gryphon_standard", 3);
        addCopies(midanRoster, "exiles_midan_hammerhead_balanced", 4);
        addCopies(midanRoster, "exiles_midan_sunder_assault", 3);
        addCopies(midanRoster, "exiles_midan_omen_pd", 3);
        addCopies(midanRoster, "exiles_midan_phaeton_standard", 1);
        addCopies(midanRoster, "exiles_midan_buffalo_standard", 2);
        addCopies(midanOfficerShips, "exiles_midan_champion_assault", 2);
        addCopies(midanOfficerShips, "exiles_midan_eagle_assault", 3);
        addCopies(midanOfficerShips, "exiles_midan_gryphon_standard", 2);
        addCopies(midanOfficerShips, "exiles_midan_hammerhead_balanced", 1);
        addCopies(midanOfficerShips, "exiles_midan_sunder_assault", 1);
        addCopies(midanOfficerShips, "exiles_midan_conquest_standard", 1);
        addCaptainSkill("helmsmanship", 1);
        addCaptainSkill("impact_mitigation", 1);
        addCaptainSkill("damage_control", 1);
        addCaptainSkill("field_modulation", 2);
        addCaptainSkill("target_analysis", 2);
        addCaptainSkill("ballistic_mastery", 2);
        addCaptainSkill("missile_specialization", 1);
        midanAdmiralSkills.add("tactical_drills");
        midanAdmiralSkills.add("coordinated_maneuvers");
        // Escort roster (ruled 2026-10-29): the main fleet's warships without the Ajax or
        // logistics (20 ships, 224 FP), five level-5 officers.
        addCopies(midanEscortRoster, "exiles_midan_champion_assault", 2);
        addCopies(midanEscortRoster, "exiles_midan_eagle_assault", 3);
        addCopies(midanEscortRoster, "exiles_midan_falcon_attack", 3);
        addCopies(midanEscortRoster, "exiles_midan_gryphon_standard", 2);
        addCopies(midanEscortRoster, "exiles_midan_hammerhead_balanced", 4);
        addCopies(midanEscortRoster, "exiles_midan_sunder_assault", 3);
        addCopies(midanEscortRoster, "exiles_midan_omen_pd", 3);
        // 7e-1 (Sean, 2026-10-31; fixed mix): a second Conquest and two Medusas in every escort,
        // the Medusas beside -- not instead of -- the Hammerheads.
        addCopies(midanEscortRoster, "exiles_midan_conquest_standard", 1);
        addCopies(midanEscortRoster, "exiles_midan_medusa_attack", 2);
        addCopies(midanEscortOfficerShips, "exiles_midan_champion_assault", 2);
        addCopies(midanEscortOfficerShips, "exiles_midan_eagle_assault", 1);
        addCopies(midanEscortOfficerShips, "exiles_midan_gryphon_standard", 2);
        addCopies(midanEscortOfficerShips, "exiles_midan_conquest_standard", 1);
        // Per-rung escort caps (option A, ruled 2026-10-31).
        midanEscortCapPerRung.add(Integer.valueOf(2));
        midanEscortCapPerRung.add(Integer.valueOf(4));
        midanEscortCapPerRung.add(Integer.valueOf(4));
        // Ruled tension targets (war doc sec 6.1, STRIKE FLEET, FAILURE AND TENSION).
        midanTensionRaiseTo.add(Float.valueOf(50f));
        midanTensionRaiseTo.add(Float.valueOf(65f));
        midanTensionRaiseTo.add(Float.valueOf(88f));
        // Rung 1, Citadel Arcadia (ruled 2026-10-10): base + station a year, spaceport six months;
        // relief may shorten the base and station by at most half (180 of 365 days).
        MidanRungPayload rung1 = new MidanRungPayload();
        rung1.add("militarybase", 365f);
        rung1.add("orbitalstation", 365f);
        rung1.add("spaceport", 180f);
        rung1.reliefIndustryIds.add("militarybase");
        rung1.reliefIndustryIds.add("orbitalstation");
        rung1.reliefDays = 180f;
        midanRungPayloads.add(rung1);
        // Rung 2, Sphinx (ruled 2026-10-11): fuel production 27 months, battlestation + base 18,
        // spaceport 6; ONE relief record over all four with a 270-day (9-month) budget. (The Diktat
        // fuel embargo that followed was REMOVED 2026-11-01, Sean's ruling.)
        MidanRungPayload rung2 = new MidanRungPayload();
        rung2.add("fuelprod", 810f);
        rung2.add("battlestation", 540f);
        rung2.add("militarybase", 540f);
        rung2.add("spaceport", 180f);
        rung2.reliefIndustryIds.add("fuelprod");
        rung2.reliefIndustryIds.add("battlestation");
        rung2.reliefIndustryIds.add("militarybase");
        rung2.reliefIndustryIds.add("spaceport");
        rung2.reliefDays = 270f;
        midanRungPayloads.add(rung2);
        // Rung 3, Eventide (ruled 2026-10-14; split by Sean 2026-11-01): permanent, no relief. The
        // wound is in midan.eventide{} (ExileEventideWound), not an industry list.
        MidanRungPayload rung3 = new MidanRungPayload();
        rung3.eventideWound = true;
        midanRungPayloads.add(rung3);
        // Eventide's wound: the ordered beats (entity name, action), per Sean 2026-11-01: the centre
        // three mirrors and the centre shade destroyed, mirror Epsilon knocked adrift, mirror Alpha and
        // shades Omega and Chi untouched. Gamma goes first and the array dies with it.
        eventideBeatNames.add("Eventide Stellar Mirror Gamma");   eventideBeatActions.add("destroy");
        eventideBeatNames.add("Eventide Stellar Shade Psi");      eventideBeatActions.add("destroy");
        eventideBeatNames.add("Eventide Stellar Mirror Beta");    eventideBeatActions.add("destroy");
        eventideBeatNames.add("Eventide Stellar Mirror Delta");   eventideBeatActions.add("destroy");
        eventideBeatNames.add("Eventide Stellar Mirror Epsilon"); eventideBeatActions.add("adrift");
    }

    /** The escort cap for 0-based rung {@code index}: the per-rung list, else the flat cap. */
    public int midanEscortCapFor(int index) {
        if (index >= 0 && index < midanEscortCapPerRung.size()) {
            return ((Integer) midanEscortCapPerRung.get(index)).intValue();
        }
        return midanEscortCap;
    }

    /** The per-rung caps as "2/4/4", for the log. */
    public String midanEscortCapsText() {
        if (midanEscortCapPerRung.isEmpty()) return String.valueOf(midanEscortCap);
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < midanEscortCapPerRung.size(); i++) {
            if (i > 0) sb.append("/");
            sb.append(midanEscortCapPerRung.get(i));
        }
        return sb.toString();
    }

    /** The payload for 0-based rung {@code index}, or null if none is configured (not built). */
    public MidanRungPayload midanPayloadFor(int index) {
        if (index < 0 || index >= midanRungPayloads.size()) return null;
        return (MidanRungPayload) midanRungPayloads.get(index);
    }

    /** The ruled "raise tension to at least" target for 0-based rung {@code index}, or -1. */
    public float midanRaiseFor(int index) {
        if (index < 0 || index >= midanTensionRaiseTo.size()) return -1f;
        return ((Float) midanTensionRaiseTo.get(index)).floatValue();
    }

    private static void addCopies(ArrayList list, String id, int count) {
        for (int i = 0; i < count; i++) list.add(id);
    }

    private void addCaptainSkill(String id, int level) {
        midanCaptainSkillIds.add(id);
        midanCaptainSkillLevels.add(Integer.valueOf(level));
    }

    /** {"variant": count, ...} -> one id per ship. Null or empty leaves the list untouched. */
    private static void readCounts(JSONObject obj, ArrayList into) throws Exception {
        if (obj == null || obj.length() == 0) return;
        into.clear();
        JSONArray names = obj.names();
        for (int i = 0; i < names.length(); i++) {
            String id = names.getString(i);
            addCopies(into, id, obj.optInt(id, 0));
        }
    }

    private void read(JSONObject cfg) throws Exception {
        enabled = cfg.optBoolean("enabled", enabled);
        debugMessages = cfg.optBoolean("debugMessages", debugMessages);
        debugMeterIntervalDays = (float) cfg.optDouble(
                "debugMeterIntervalDays", debugMeterIntervalDays);
        premiseCheckIntervalDays = (float) cfg.optDouble(
                "premiseCheckIntervalDays", premiseCheckIntervalDays);
        if (premiseCheckIntervalDays <= 0f) premiseCheckIntervalDays = 5f;
        timeScale = (float) cfg.optDouble("timeScale", timeScale);
        if (timeScale <= 0f) timeScale = 1f;
        startDelayDays = (float) cfg.optDouble("startDelayDays", startDelayDays);

        JSONObject testing = cfg.optJSONObject("testing");
        if (testing != null) {
            forceState = testing.optInt("forceState", forceState);
            tensionOverride = (float) testing.optDouble("tensionOverride", tensionOverride);
            forceIncidentOffender = testing.optString(
                    "forceIncidentOffender", forceIncidentOffender);
            forceIncidentType = testing.optString("forceIncidentType", forceIncidentType);
            testPostStoryAnnouncement = testing.optBoolean(
                    "postStoryAnnouncement", testPostStoryAnnouncement);
            testMidanReadyNow = testing.optBoolean("midanReadyNow", testMidanReadyNow);
            testMidanLaunchNow = testing.optBoolean("midanLaunchNow", testMidanLaunchNow);
            testMidanStartRung = testing.optInt("midanStartRung", testMidanStartRung);
            testMidanForceDirective = testing.optBoolean("midanForceDirective", testMidanForceDirective);
            testInterdictionNow = testing.optBoolean("interdictionNow", testInterdictionNow);
            testGrantAuthCodes = testing.optString("grantAuthCodes", testGrantAuthCodes);
            testGrantOfficialBusiness = testing.optString(
                    "grantOfficialBusiness", testGrantOfficialBusiness);
            testStopDiagnosticsDays = (float) testing.optDouble(
                    "stopDiagnosticsDays", testStopDiagnosticsDays);
            if (testStopDiagnosticsDays < 0f) testStopDiagnosticsDays = 0f;
        }

        JSONObject stop = cfg.optJSONObject("playerInterception");
        if (stop != null) {
            stopEnabled = stop.optBoolean("enabled", stopEnabled);
            stopTollPerFleetPoint = (float) stop.optDouble(
                    "tollPerFleetPoint", stopTollPerFleetPoint);
            stopTollFloor = (float) stop.optDouble("tollFloor", stopTollFloor);
            stopTollCap = (float) stop.optDouble("tollCap", stopTollCap);
            JSONObject tollMult = stop.optJSONObject("tollMult");
            if (tollMult != null) {
                stopTollMultHegemony = (float) tollMult.optDouble(
                        HEGEMONY_ID, stopTollMultHegemony);
                stopTollMultPersean = (float) tollMult.optDouble(PERSEAN_ID, stopTollMultPersean);
            }
            stopNegotiateMult = (float) stop.optDouble("negotiateMult", stopNegotiateMult);
            stopCaughtLieMult = (float) stop.optDouble("caughtLieMult", stopCaughtLieMult);
            stopCreditsPerSupply = (float) stop.optDouble(
                    "creditsPerSupply", stopCreditsPerSupply);
            stopLieBaseChance = (float) stop.optDouble("lieBaseChance", stopLieBaseChance);
            stopLieRepShift = (float) stop.optDouble("lieRepShift", stopLieRepShift);
            stopLieMinChance = (float) stop.optDouble("lieMinChance", stopLieMinChance);
            stopLieMaxChance = (float) stop.optDouble("lieMaxChance", stopLieMaxChance);
            stopRepCaughtLie = (float) stop.optDouble("repCaughtLie", stopRepCaughtLie);
            stopRepFacedDown = (float) stop.optDouble("repFacedDown", stopRepFacedDown);
            stopRepFloor = stop.optString("repFloor", stopRepFloor);
            JSONObject weight = stop.optJSONObject("inspectionWeight");
            if (weight != null) {
                stopInspectionWeightInspect = (float) weight.optDouble(
                        "inspect", stopInspectionWeightInspect);
                stopInspectionWeightCordon = (float) weight.optDouble(
                        "cordon", stopInspectionWeightCordon);
            }
            stopRaiderLowRepImpactDays = (float) stop.optDouble(
                    "raiderLowRepImpactDays", stopRaiderLowRepImpactDays);
        }

        JSONObject midan = cfg.optJSONObject("midan");
        if (midan != null) {
            midanEnabled = midan.optBoolean("enabled", midanEnabled);
            midanFirstReadyDays = (float) midan.optDouble("firstReadyDays", midanFirstReadyDays);
            midanRebuildDays = (float) midan.optDouble("rebuildDays", midanRebuildDays);
            midanRebuildFailedDays = (float) midan.optDouble(
                    "rebuildFailedDays", midanRebuildFailedDays);
            midanStrikeMarginMult = (float) midan.optDouble(
                    "strikeMarginMult", midanStrikeMarginMult);
            midanFpCeiling = (float) midan.optDouble("fpCeiling", midanFpCeiling);
            midanFleetFpEstimate = (float) midan.optDouble("fleetFpEstimate", midanFleetFpEstimate);
            JSONArray targets = midan.optJSONArray("targets");
            if (targets != null) {
                midanTargets.clear();
                for (int i = 0; i < targets.length(); i++) {
                    String id = targets.optString(i, null);
                    if (id != null && id.length() > 0) midanTargets.add(id);
                }
            }
            JSONArray anchors = midan.optJSONArray("launchAnchors");
            if (anchors != null) {
                midanLaunchAnchors.clear();
                for (int i = 0; i < anchors.length(); i++) {
                    String id = anchors.optString(i, null);
                    if (id != null && id.length() > 0) midanLaunchAnchors.add(id);
                }
            }
            midanMeasureIntervalDays = (float) midan.optDouble(
                    "measureIntervalDays", midanMeasureIntervalDays);
            JSONObject score = midan.optJSONObject("score");
            if (score != null) {
                midanMeansWeight = (float) score.optDouble("meansWeight", midanMeansWeight);
                midanDistractionWeight = (float) score.optDouble(
                        "distractionWeight", midanDistractionWeight);
                midanCoverWeight = (float) score.optDouble("coverWeight", midanCoverWeight);
                midanDistractionOpsFull = (float) score.optDouble(
                        "distractionOpsFull", midanDistractionOpsFull);
                midanCoverTalks = (float) score.optDouble("coverTalks", midanCoverTalks);
                midanBaselineWeightPerMeasure = (float) score.optDouble(
                        "baselineWeightPerMeasure", midanBaselineWeightPerMeasure);
                midanBarStart = (float) score.optDouble("barStart", midanBarStart);
                midanBarFloor = (float) score.optDouble("barFloor", midanBarFloor);
                midanBarDecayDays = (float) score.optDouble("barDecayDays", midanBarDecayDays);
            }
            JSONObject sf = midan.optJSONObject("strikeForce");
            if (sf != null) {
                midanFleetName = sf.optString("fleetName", midanFleetName);
                midanFlagshipFleetName = sf.optString("flagshipFleetName", midanFlagshipFleetName);
                midanFlagshipVariant = sf.optString("flagshipVariant", midanFlagshipVariant);
                midanFlagshipName = sf.optString("flagshipName", midanFlagshipName);
                readCounts(sf.optJSONObject("roster"), midanRoster);
                readCounts(sf.optJSONObject("officerShips"), midanOfficerShips);
                midanOfficerLevel = sf.optInt("officerLevel", midanOfficerLevel);
                midanCaptainLevel = sf.optInt("captainLevel", midanCaptainLevel);
                JSONObject skills = sf.optJSONObject("captainSkills");
                if (skills != null && skills.length() > 0) {
                    midanCaptainSkillIds.clear();
                    midanCaptainSkillLevels.clear();
                    JSONArray names = skills.names();
                    for (int i = 0; i < names.length(); i++) {
                        addCaptainSkill(names.getString(i), skills.optInt(names.getString(i), 1));
                    }
                }
                JSONArray admiral = sf.optJSONArray("admiralSkills");
                if (admiral != null) {
                    midanAdmiralSkills.clear();
                    for (int i = 0; i < admiral.length(); i++) {
                        String id = admiral.optString(i, null);
                        if (id != null && id.length() > 0) midanAdmiralSkills.add(id);
                    }
                }
                midanEscortCap = sf.optInt("escortCap", midanEscortCap);
                if (midanEscortCap < 0) midanEscortCap = 0;
                JSONArray caps = sf.optJSONArray("escortCapPerRung");
                if (caps != null) {
                    midanEscortCapPerRung.clear();
                    for (int i = 0; i < caps.length(); i++) {
                        midanEscortCapPerRung.add(Integer.valueOf(Math.max(0, caps.optInt(i, 0))));
                    }
                }
                readCounts(sf.optJSONObject("escortRoster"), midanEscortRoster);
                readCounts(sf.optJSONObject("escortOfficerShips"), midanEscortOfficerShips);
                midanEscortOfficerLevel = sf.optInt("escortOfficerLevel", midanEscortOfficerLevel);
            }
            JSONObject st = midan.optJSONObject("strike");
            if (st != null) {
                midanMaxLaunchRung = st.optInt("maxLaunchRung", midanMaxLaunchRung);
                midanStagingDistance = (float) st.optDouble(
                        "stagingDistance", midanStagingDistance);
                midanDistressLeadDays = (float) st.optDouble(
                        "distressLeadDays", midanDistressLeadDays);
                midanQuestPrepDays = (float) st.optDouble("questPrepDays", midanQuestPrepDays);
                midanAjaxKillRep = (float) st.optDouble("ajaxKillRep", midanAjaxKillRep);
                midanPrepDaysMin = (float) st.optDouble("prepDaysMin", midanPrepDaysMin);
                midanPrepDaysMax = (float) st.optDouble("prepDaysMax", midanPrepDaysMax);
                if (midanPrepDaysMax < midanPrepDaysMin) midanPrepDaysMax = midanPrepDaysMin;
                midanAbortFraction = (float) st.optDouble("abortFraction", midanAbortFraction);
                midanExtraDays = Math.max(0f, (float) st.optDouble("extraDays", midanExtraDays));
                midanStrikeMaxDays = (float) st.optDouble("maxDays", midanStrikeMaxDays);
                midanOrbitDays = (float) st.optDouble("orbitDays", midanOrbitDays);
                midanUnrestPoints = st.optInt("unrestPoints", midanUnrestPoints);
                midanSystemCooldownDays = (float) st.optDouble(
                        "systemCooldownDays", midanSystemCooldownDays);
                midanBountyPerFrigate = (float) st.optDouble(
                        "bountyPerFrigate", midanBountyPerFrigate);
                JSONArray raise = st.optJSONArray("tensionRaiseTo");
                if (raise != null && raise.length() > 0) {
                    midanTensionRaiseTo.clear();
                    for (int i = 0; i < raise.length(); i++) {
                        midanTensionRaiseTo.add(Float.valueOf((float) raise.optDouble(i, 0)));
                    }
                }
                JSONArray rungs = st.optJSONArray("rungs");
                if (rungs != null && rungs.length() > 0) {
                    midanRungPayloads.clear();
                    for (int i = 0; i < rungs.length(); i++) {
                        JSONObject r = rungs.optJSONObject(i);
                        MidanRungPayload payload = new MidanRungPayload();
                        if (r != null) {
                            JSONObject days = r.optJSONObject("disruptDays");
                            if (days != null && days.length() > 0) {
                                JSONArray names = days.names();
                                for (int k = 0; k < names.length(); k++) {
                                    String id = names.getString(k);
                                    payload.add(id, (float) days.optDouble(id, 0));
                                }
                            }
                            JSONArray relief = r.optJSONArray("reliefIndustries");
                            if (relief != null) {
                                for (int k = 0; k < relief.length(); k++) {
                                    String id = relief.optString(k, null);
                                    if (id != null && id.length() > 0) {
                                        payload.reliefIndustryIds.add(id);
                                    }
                                }
                            }
                            payload.reliefDays = (float) r.optDouble("reliefDays", 0);
                            payload.eventideWound = r.optBoolean("eventideWound", false);
                        }
                        midanRungPayloads.add(payload);
                    }
                }
            }
            JSONObject ev = midan.optJSONObject("eventide");
            if (ev != null) {
                JSONArray beats = ev.optJSONArray("beats");
                if (beats != null && beats.length() > 0) {
                    eventideBeatNames.clear();
                    eventideBeatActions.clear();
                    for (int i = 0; i < beats.length(); i++) {
                        JSONObject b = beats.optJSONObject(i);
                        if (b == null) continue;
                        String name = b.optString("entity", null);
                        if (name == null || name.length() == 0) continue;
                        eventideBeatNames.add(name);
                        eventideBeatActions.add(b.optString("action", "destroy"));
                    }
                }
                eventideBeatDays = Math.max(0.05f, (float) ev.optDouble("beatDays", eventideBeatDays));
                eventidePlanetDescId = ev.optString("planetDescription", eventidePlanetDescId);
                eventideIllustration = ev.optString("illustration", eventideIllustration);
                eventideAdriftDescId = ev.optString("adriftDescription", eventideAdriftDescId);
                eventideVowDelayDays = Math.max(0f, (float) ev.optDouble("vowDelayDays", eventideVowDelayDays));
                eventideVowNoticeDays = Math.max(1f, (float) ev.optDouble("vowNoticeDays", eventideVowNoticeDays));
            }
            JSONObject proxy = midan.optJSONObject("proxy");
            if (proxy != null) {
                midanProxyEnabled = proxy.optBoolean("enabled", midanProxyEnabled);
                midanProxyDirectiveChance = Math.max(0f, Math.min(1f,
                        (float) proxy.optDouble("directiveChance", midanProxyDirectiveChance)));
            }
        }

        JSONObject tension = cfg.optJSONObject("tension");
        if (tension != null) {
            tensionStart = (float) tension.optDouble("start", tensionStart);
            tensionFloor = (float) tension.optDouble("floor", tensionFloor);
            tensionCap = (float) tension.optDouble("cap", tensionCap);
            tensionDecayPerDay = (float) tension.optDouble("decayPerDay", tensionDecayPerDay);
            deescalationDecayMult = (float) tension.optDouble(
                    "deescalationDecayMult", deescalationDecayMult);
            hawkThreshold = (float) tension.optDouble("hawkThreshold", hawkThreshold);
            warningThreshold = (float) tension.optDouble("warningThreshold", warningThreshold);
            generalEngagementThreshold = (float) tension.optDouble(
                    "generalEngagementThreshold", generalEngagementThreshold);
            generalEngagementHotCycles = tension.optInt(
                    "generalEngagementHotCycles", generalEngagementHotCycles);

            JSONObject gains = tension.optJSONObject("gains");
            if (gains != null) {
                tensionPerIncident = (float) gains.optDouble("perIncident", tensionPerIncident);
                tensionPerCompletedOp = (float) gains.optDouble(
                        "perCompletedOp", tensionPerCompletedOp);
                tensionPerMidanInterference = (float) gains.optDouble(
                        "perMidanInterference", tensionPerMidanInterference);
                tensionPerTalksCollapse = (float) gains.optDouble(
                        "perTalksCollapse", tensionPerTalksCollapse);
            }

            JSONObject cuts = tension.optJSONObject("cuts");
            if (cuts != null) {
                interventionStandDownCut = (float) cuts.optDouble(
                        "standDown", interventionStandDownCut);
                interventionPartialCut = (float) cuts.optDouble(
                        "partial", interventionPartialCut);
                interventionCollapseCut = (float) cuts.optDouble(
                        "collapse", interventionCollapseCut);
            }
        }

        JSONObject weariness = cfg.optJSONObject("weariness");
        if (weariness != null) {
            wearinessDecayPerDay = (float) weariness.optDouble(
                    "decayPerDay", wearinessDecayPerDay);
            wearinessStandDownWeight = (float) weariness.optDouble(
                    "standDownWeight", wearinessStandDownWeight);
            wearinessPerStrikeSuffered = (float) weariness.optDouble(
                    "perStrikeSuffered", wearinessPerStrikeSuffered);
            wearinessPerInterdictionSuffered = (float) weariness.optDouble(
                    "perInterdictionSuffered", wearinessPerInterdictionSuffered);
            wearinessPerOpFailed = (float) weariness.optDouble(
                    "perOpFailed", wearinessPerOpFailed);
            wearinessPerBlockadeSuffered = (float) weariness.optDouble(
                    "perBlockadeSuffered", wearinessPerBlockadeSuffered);
            wearinessPerSqueezeSuffered = (float) weariness.optDouble(
                    "perSqueezeSuffered", wearinessPerSqueezeSuffered);
        }

        JSONObject cycle = cfg.optJSONObject("cycle");
        if (cycle != null) {
            tensionDwellDaysMin = (float) cycle.optDouble(
                    "tensionDwellDaysMin", tensionDwellDaysMin);
            tensionDwellDaysMax = (float) cycle.optDouble(
                    "tensionDwellDaysMax", tensionDwellDaysMax);
            tensionDwellCollapseMult = (float) cycle.optDouble(
                    "tensionDwellCollapseMult", tensionDwellCollapseMult);
            escalationWindowDays = (float) cycle.optDouble(
                    "escalationWindowDays", escalationWindowDays);
            generalEngagementDays = (float) cycle.optDouble(
                    "generalEngagementDays", generalEngagementDays);
            interventionDays = (float) cycle.optDouble("interventionDays", interventionDays);
            deescalationDays = (float) cycle.optDouble("deescalationDays", deescalationDays);
            preemptiveCeasefireChance = (float) cycle.optDouble(
                    "preemptiveCeasefireChance", preemptiveCeasefireChance);
            incidentOffenderHegemonyChance = (float) cycle.optDouble(
                    "incidentOffenderHegemonyChance", incidentOffenderHegemonyChance);

            JSONObject weights = cycle.optJSONObject("interventionOutcomeWeights");
            if (weights != null) {
                outcomeStandDownWeight = (float) weights.optDouble(
                        "standDown", outcomeStandDownWeight);
                outcomePartialWeight = (float) weights.optDouble(
                        "partial", outcomePartialWeight);
                outcomeCollapseWeight = (float) weights.optDouble(
                        "collapse", outcomeCollapseWeight);
                outcomeCollapseTensionWeight = (float) weights.optDouble(
                        "collapsePerTensionOverHawk", outcomeCollapseTensionWeight);
                outcomeGeneralEngagementStandDownMult = (float) weights.optDouble(
                        "generalEngagementStandDownMult",
                        outcomeGeneralEngagementStandDownMult);
            }
        }

        readSide(cfg.optJSONObject(HEGEMONY_ID), hegemony);
        readSide(cfg.optJSONObject(PERSEAN_ID), persean);

        JSONObject interdiction = cfg.optJSONObject("interdiction");
        if (interdiction != null) {
            interdictionFeasibilityStrengthMult = (float) interdiction.optDouble(
                    "feasibilityStrengthMult", interdictionFeasibilityStrengthMult);
            interdictionEnabled = interdiction.optBoolean("enabled", interdictionEnabled);
            interdictionCadenceDays = (float) interdiction.optDouble(
                    "cadenceDays", interdictionCadenceDays);
            interdictionEscalationCadenceMult = (float) interdiction.optDouble(
                    "escalationCadenceMult", interdictionEscalationCadenceMult);
            interdictionTensionMaxConcurrentPerSide = interdiction.optInt(
                    "tensionMaxConcurrentOpsPerSide", interdictionTensionMaxConcurrentPerSide);
            interdictionSpawnFpMin = (float) interdiction.optDouble(
                    "spawnFpMin", interdictionSpawnFpMin);
            interdictionSpawnFpMax = (float) interdiction.optDouble(
                    "spawnFpMax", interdictionSpawnFpMax);
            interdictionMaxShipSize = interdiction.optInt(
                    "maxShipSize", interdictionMaxShipSize);
            interdictionPresenceDays = (float) interdiction.optDouble(
                    "presenceDays", interdictionPresenceDays);
            interdictionSystemCooldownDays = (float) interdiction.optDouble(
                    "systemCooldownDays", interdictionSystemCooldownDays);
            interdictionAbortFraction = (float) interdiction.optDouble(
                    "abortFraction", interdictionAbortFraction);
            interdictionMaxFleetFp = (float) interdiction.optDouble(
                    "maxFleetFp", interdictionMaxFleetFp);
            interdictionLoiterOnFocusMarket = interdiction.optBoolean(
                    "loiterOnFocusMarket", interdictionLoiterOnFocusMarket);
            interdictionSuppressStationAttacks = interdiction.optBoolean(
                    "suppressStationAttacks", interdictionSuppressStationAttacks);
            interdictionObjectiveStationKeepOut = (float) interdiction.optDouble(
                    "objectiveStationKeepOut", interdictionObjectiveStationKeepOut);
            if (interdictionObjectiveStationKeepOut < 0f) interdictionObjectiveStationKeepOut = 0f;
            interdictionBountyPerFrigate = (float) interdiction.optDouble(
                    "bountyPerFrigate", interdictionBountyPerFrigate);
            interdictionConditionDelayDays = (float) interdiction.optDouble(
                    "conditionDelayDays", interdictionConditionDelayDays);
            interdictionConditionDays = (float) interdiction.optDouble(
                    "conditionDays", interdictionConditionDays);
            interdictionAccessibilityPenalty = (float) interdiction.optDouble(
                    "accessibilityPenalty", interdictionAccessibilityPenalty);
            interdictionImportHitCount = interdiction.optInt(
                    "importHitCount", interdictionImportHitCount);
            interdictionImportHitUnits = interdiction.optInt(
                    "importHitUnits", interdictionImportHitUnits);
        }

        JSONObject incidentsCfg = cfg.optJSONObject("incidents");
        if (incidentsCfg != null) {
            incidentsEnabled = incidentsCfg.optBoolean("enabled", incidentsEnabled);
            incidentTelegraphIntel = incidentsCfg.optBoolean(
                    "telegraphIntel", incidentTelegraphIntel);
            incidentConfirmDays = (float) incidentsCfg.optDouble(
                    "confirmDays", incidentConfirmDays);
            incidentIntelDays = (float) incidentsCfg.optDouble("intelDays", incidentIntelDays);

            // Per-entry overrides, BY ID. Deliberately a merge rather than a replacement: the
            // table's membership is code (and Pass 7 adds to it in code), while weight, severity
            // and the offender bias are tuning. An unknown id is logged rather than ignored --
            // a typo here would otherwise silently leave the shipped value in place.
            JSONObject types = incidentsCfg.optJSONObject("types");
            if (types != null) {
                java.util.Iterator keys = types.keys();
                while (keys.hasNext()) {
                    String id = (String) keys.next();
                    ExileHegLeagueIncident entry = ExileHegLeagueIncident.find(incidents, id);
                    if (entry == null) {
                        Global.getLogger(ExileHegLeagueConfig.class).warn(
                                "Exiles Heg-League: incidents.types has no such entry \"" + id
                                        + "\" -- ignored");
                        continue;
                    }
                    JSONObject entryCfg = types.optJSONObject(id);
                    if (entryCfg == null) continue;
                    entry.enabled = entryCfg.optBoolean("enabled", entry.enabled);
                    entry.weight = (float) entryCfg.optDouble("weight", entry.weight);
                    entry.severity = (float) entryCfg.optDouble("severity", entry.severity);
                    entry.hegemonyOffenderChance = (float) entryCfg.optDouble(
                            "hegemonyOffenderChance", entry.hegemonyOffenderChance);
                }
            }
        }

        JSONObject strike = cfg.optJSONObject("surgicalStrike");
        if (strike != null) {
            strikeEnabled = strike.optBoolean("enabled", strikeEnabled);
            strikeRetaliationEnabled = strike.optBoolean(
                    "retaliationEnabled", strikeRetaliationEnabled);
            strikeEscalationCounterStrikes = strike.optBoolean(
                    "escalationCounterStrikes", strikeEscalationCounterStrikes);
            strikeEscalationCadenceDays = (float) strike.optDouble(
                    "escalationCadenceDays", strikeEscalationCadenceDays);
            strikeEscalationMaxConcurrentPerSide = strike.optInt(
                    "escalationMaxConcurrentOpsPerSide", strikeEscalationMaxConcurrentPerSide);
            strikeSpawnFpMin = (float) strike.optDouble("spawnFpMin", strikeSpawnFpMin);
            strikeSpawnFpMax = (float) strike.optDouble("spawnFpMax", strikeSpawnFpMax);
            strikeTensionFpScale = (float) strike.optDouble(
                    "tensionFpScale", strikeTensionFpScale);
            strikeMaxShipSize = strike.optInt("maxShipSize", strikeMaxShipSize);
            strikeMaxFleetFp = (float) strike.optDouble("maxFleetFp", strikeMaxFleetFp);
            strikeFeasibilityStrengthMult = (float) strike.optDouble(
                    "feasibilityStrengthMult", strikeFeasibilityStrengthMult);
            strikeSystemCooldownDays = (float) strike.optDouble(
                    "systemCooldownDays", strikeSystemCooldownDays);
            strikeAbortFraction = (float) strike.optDouble(
                    "abortFraction", strikeAbortFraction);
            strikeMaxDays = (float) strike.optDouble("maxDays", strikeMaxDays);
            strikeBountyPerFrigate = (float) strike.optDouble(
                    "bountyPerFrigate", strikeBountyPerFrigate);
            strikeDisruptDaysMin = (float) strike.optDouble(
                    "disruptDaysMin", strikeDisruptDaysMin);
            strikeDisruptDaysMax = (float) strike.optDouble(
                    "disruptDaysMax", strikeDisruptDaysMax);
            strikeExtendedDisruptMult = (float) strike.optDouble(
                    "extendedDisruptMult", strikeExtendedDisruptMult);
            strikeGroundDefenseBucketTension = (float) strike.optDouble(
                    "groundDefenseBucketTension", strikeGroundDefenseBucketTension);
            strikeEconomicBucketTension = (float) strike.optDouble(
                    "economicBucketTension", strikeEconomicBucketTension);
            readIdList(strike.optJSONArray("economicIndustries"), strikeEconomicIndustries);
            strikeOrbitDays = (float) strike.optDouble("orbitDays", strikeOrbitDays);
            strikeUnrestPoints = strike.optInt("unrestPoints", strikeUnrestPoints);
        }

        // The never-touch floor beats config: drop forbidden economic entries loudly rather than
        // letting a config edit route a strike into a farming world's food supply.
        for (int i = strikeEconomicIndustries.size() - 1; i >= 0; i--) {
            String id = (String) strikeEconomicIndustries.get(i);
            if (isStrikeForbiddenIndustry(id)) {
                Global.getLogger(ExileHegLeagueConfig.class).warn(
                        "Exiles Heg-League config: surgicalStrike.economicIndustries entry \""
                                + id + "\" is on the never-touch floor; ignored");
                strikeEconomicIndustries.remove(i);
            }
        }

        JSONObject blockade = cfg.optJSONObject("blockade");
        if (blockade != null) {
            blockadeEnabled = blockade.optBoolean("enabled", blockadeEnabled);
            blockadeLaunchAtEscalationEntry = blockade.optBoolean(
                    "launchAtEscalationEntry", blockadeLaunchAtEscalationEntry);
            blockadeCadenceDays = (float) blockade.optDouble(
                    "cadenceDays", blockadeCadenceDays);
            blockadeMaxActivePerSide = blockade.optInt(
                    "maxActivePerSide", blockadeMaxActivePerSide);
            blockadeGeneralEngagementMaxActivePerSide = blockade.optInt(
                    "generalEngagementMaxActivePerSide",
                    blockadeGeneralEngagementMaxActivePerSide);
            readIntList(blockade.optJSONArray("fleetSizes"), blockadeFleetSizes);
            blockadePrepDays = (float) blockade.optDouble("prepDays", blockadePrepDays);
            blockadePayloadDays = (float) blockade.optDouble(
                    "payloadDays", blockadePayloadDays);
            blockadeAccessibilityPenalty = (float) blockade.optDouble(
                    "accessibilityPenalty", blockadeAccessibilityPenalty);
            blockadeAbortFraction = (float) blockade.optDouble(
                    "abortFraction", blockadeAbortFraction);
            blockadeFeasibilityMult = (float) blockade.optDouble(
                    "feasibilityMult", blockadeFeasibilityMult);
            blockadeFleetSizesMaxMult = (float) blockade.optDouble(
                    "fleetSizesMaxMult", blockadeFleetSizesMaxMult);
            blockadeSystemCooldownDays = (float) blockade.optDouble(
                    "systemCooldownDays", blockadeSystemCooldownDays);
            blockadeCollateralEnabled = blockade.optBoolean(
                    "collateralEnabled", blockadeCollateralEnabled);
            blockadeCollateralAccessibilityPenalty = (float) blockade.optDouble(
                    "collateralAccessibilityPenalty", blockadeCollateralAccessibilityPenalty);
            blockadeCollateralScalesWithTier = blockade.optBoolean(
                    "collateralScalesWithTier", blockadeCollateralScalesWithTier);
            blockadeCollateralExpiryDays = (float) blockade.optDouble(
                    "collateralExpiryDays", blockadeCollateralExpiryDays);
            blockadeHiddenUntilArrival = blockade.optBoolean(
                    "hiddenUntilArrival", blockadeHiddenUntilArrival);
            blockadeSuppressStationAttacks = blockade.optBoolean(
                    "suppressStationAttacks", blockadeSuppressStationAttacks);
            blockadeBountyPerFrigate = (float) blockade.optDouble(
                    "bountyPerFrigate", blockadeBountyPerFrigate);
        }

        // sec 14.15: the "Break the Blockade" victory bonus.
        JSONObject victory = cfg.optJSONObject("victoryBonus");
        if (victory != null) {
            victoryBonusEnabled = victory.optBoolean("enabled", victoryBonusEnabled);
            victoryBonusBlockadeBase = (float) victory.optDouble(
                    "blockadeBase", victoryBonusBlockadeBase);
            victoryBonusSqueezeBase = (float) victory.optDouble(
                    "squeezeBase", victoryBonusSqueezeBase);
            victoryBonusRefPointsBlockade = (float) victory.optDouble(
                    "refPointsBlockade", victoryBonusRefPointsBlockade);
            victoryBonusRefFpSqueeze = (float) victory.optDouble(
                    "refFpSqueeze", victoryBonusRefFpSqueeze);
            victoryBonusMaxSizeMult = (float) victory.optDouble(
                    "maxSizeMult", victoryBonusMaxSizeMult);
            victoryBonusShareFloor = (float) victory.optDouble(
                    "shareFloor", victoryBonusShareFloor);
            victoryBonusShareFull = (float) victory.optDouble(
                    "shareFull", victoryBonusShareFull);
        }

        JSONObject planetary = cfg.optJSONObject("planetaryBlockade");
        if (planetary != null) {
            planetaryEnabled = planetary.optBoolean("enabled", planetaryEnabled);
            planetarySpawnFpMin = (float) planetary.optDouble(
                    "spawnFpMin", planetarySpawnFpMin);
            planetarySpawnFpMax = (float) planetary.optDouble(
                    "spawnFpMax", planetarySpawnFpMax);
            planetaryTensionFpScale = (float) planetary.optDouble(
                    "tensionFpScale", planetaryTensionFpScale);
            planetaryMaxShipSize = planetary.optInt("maxShipSize", planetaryMaxShipSize);
            planetaryMaxFleetFp = (float) planetary.optDouble(
                    "maxFleetFp", planetaryMaxFleetFp);
            planetaryPresenceDays = (float) planetary.optDouble(
                    "presenceDays", planetaryPresenceDays);
            planetaryConditionApplyDay = (float) planetary.optDouble(
                    "conditionApplyDay", planetaryConditionApplyDay);
            planetaryAbortFraction = (float) planetary.optDouble(
                    "abortFraction", planetaryAbortFraction);
            planetaryFeasibilityStrengthMult = (float) planetary.optDouble(
                    "feasibilityStrengthMult", planetaryFeasibilityStrengthMult);
            planetarySystemCooldownDays = (float) planetary.optDouble(
                    "systemCooldownDays", planetarySystemCooldownDays);
            planetaryAccessibilityPenalty = (float) planetary.optDouble(
                    "accessibilityPenalty", planetaryAccessibilityPenalty);
            planetaryPlayerBuyPriceMult = (float) planetary.optDouble(
                    "playerBuyPriceMult", planetaryPlayerBuyPriceMult);
            planetaryConditionExpiryDays = (float) planetary.optDouble(
                    "conditionExpiryDays", planetaryConditionExpiryDays);
            planetarySuppressStationAttacks = planetary.optBoolean(
                    "suppressStationAttacks", planetarySuppressStationAttacks);
            planetaryHiddenUntilArrival = planetary.optBoolean(
                    "hiddenUntilArrival", planetaryHiddenUntilArrival);
            planetaryBountyPerFrigate = (float) planetary.optDouble(
                    "bountyPerFrigate", planetaryBountyPerFrigate);
            planetaryRetaliationFallbackToOtherType = planetary.optBoolean(
                    "retaliationFallbackToOtherType", planetaryRetaliationFallbackToOtherType);
        }

        JSONObject recovery = cfg.optJSONObject("recoveryConvoys");
        if (recovery != null) {
            recoveryEnabled = recovery.optBoolean("enabled", recoveryEnabled);
            recoveryMaxActive = recovery.optInt("maxActive", recoveryMaxActive);
            recoveryLagDaysMin = (float) recovery.optDouble("lagDaysMin", recoveryLagDaysMin);
            recoveryLagDaysMax = (float) recovery.optDouble("lagDaysMax", recoveryLagDaysMax);
            recoveryUnloadDays = (float) recovery.optDouble("unloadDays", recoveryUnloadDays);
            recoveryMinRemainingDays = (float) recovery.optDouble(
                    "minRemainingDays", recoveryMinRemainingDays);
            recoveryLedgerMaxRecords = recovery.optInt(
                    "ledgerMaxRecords", recoveryLedgerMaxRecords);
            recoveryRecordExpiryDays = (float) recovery.optDouble(
                    "recordExpiryDays", recoveryRecordExpiryDays);
            recoveryTravelTimeoutDays = (float) recovery.optDouble(
                    "travelTimeoutDays", recoveryTravelTimeoutDays);
            recoveryDepartTimeoutDays = (float) recovery.optDouble(
                    "departTimeoutDays", recoveryDepartTimeoutDays);
            recoveryCombatFp = recovery.optInt("combatFp", recoveryCombatFp);
            recoveryFreighterFp = recovery.optInt("freighterFp", recoveryFreighterFp);
            recoveryTankerFp = recovery.optInt("tankerFp", recoveryTankerFp);
            recoveryTransportFp = recovery.optInt("transportFp", recoveryTransportFp);
            recoveryFreighterVariant = recovery.optString("freighterVariant", recoveryFreighterVariant);
            recoveryFreighterCount = Math.max(0, recovery.optInt("freighterCount", recoveryFreighterCount));
            recoveryCargoPerFreighter = Math.max(0f, (float) recovery.optDouble(
                    "cargoPerFreighter", recoveryCargoPerFreighter));
            JSONObject manifest = recovery.optJSONObject("cargoShares");
            if (manifest != null && manifest.length() > 0) {
                recoveryCargoCommodities.clear();
                recoveryCargoShares.clear();
                JSONArray ids = manifest.names();
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.optString(i);
                    float share = (float) manifest.optDouble(id, 0);
                    if (id == null || share <= 0f) continue;
                    recoveryCargoCommodities.add(id);
                    recoveryCargoShares.add(new Float(share));
                }
            }
            recoveryArrivalMessage = recovery.optBoolean(
                    "arrivalMessage", recoveryArrivalMessage);
            recoveryReliefEffortCondition = recovery.optBoolean(
                    "reliefEffortCondition", recoveryReliefEffortCondition);
            recoveryDebugMessages = recovery.optBoolean(
                    "debugMessages", recoveryDebugMessages);
        }

        // sec 4.6's floor, enforced in code and not merely documented: a recoveryDaysMult above 1
        // would heal a struck industry BELOW the design-normal disruptDays band, which is the one
        // thing the extended-baseline invariant promises can never happen.
        clampRecoveryDaysMult(hegemony);
        clampRecoveryDaysMult(persean);

        JSONObject intel = cfg.optJSONObject("intel");
        if (intel != null) {
            ceasefireIntelEnabled = intel.optBoolean("ceasefireEnabled", ceasefireIntelEnabled);
            ceasefireIntelDays = (float) intel.optDouble(
                    "ceasefireIntelDays", ceasefireIntelDays);
            generalEngagementIntelEnabled = intel.optBoolean(
                    "generalEngagementEnabled", generalEngagementIntelEnabled);
            generalEngagementIntelDays = (float) intel.optDouble(
                    "generalEngagementIntelDays", generalEngagementIntelDays);
            generalEngagementOperationName = intel.optString(
                    "generalEngagementOperationName", generalEngagementOperationName);
        }

        wearinessPerFpDestroyed = (float) cfg.optDouble(
                "wearinessPerFpDestroyed", wearinessPerFpDestroyed);
        distanceFalloff = (float) cfg.optDouble("distanceFalloff", distanceFalloff);

        // An explicit empty array is a MEANINGFUL setting on every one of these three, so a
        // present key always replaces the defaults rather than merging with them.
        readIdList(cfg.optJSONArray("exemptSystems"), exemptSystems);
        readIdList(cfg.optJSONArray("excludedMarkets"), excludedMarkets);
        readIdList(cfg.optJSONArray("allowedMarkets"), allowedMarkets);
        // Pass 4's whitelists. Same rule: a present key replaces the defaults entirely, so an
        // explicit [] means "this side never blockades anything" rather than silently merging.
        readIdList(cfg.optJSONArray("blockadeWhitelistSystems"), blockadeWhitelistSystems);
        readIdList(cfg.optJSONArray("planetaryBlockadeWhitelistMarkets"),
                planetaryBlockadeWhitelistMarkets);

        JSONObject nex = cfg.optJSONObject("nex");
        if (nex != null) {
            nexRelationPin = nex.optBoolean("relationPin", nexRelationPin);
            nexSelfHealHostility = nex.optBoolean(
                    "selfHealHostility", nexSelfHealHostility);
            nexConquestShields = nex.optBoolean("conquestShields", nexConquestShields);
            nexPinValue = (float) nex.optDouble("pinValue", nexPinValue);
            JSONArray shields = nex.optJSONArray("conquestShieldMarkets");
            if (shields != null) {
                // An explicit empty array means "shield nothing" -- a meaningful setting, so it
                // clears rather than falling back to the defaults.
                nexShieldMarkets.clear();
                for (int i = 0; i < shields.length(); i++) {
                    nexShieldMarkets.add(shields.getString(i));
                }
            }
        }
    }

    private static void readSide(JSONObject side, Side into) throws Exception {
        if (side == null) return;
        into.retaliationLagDays = (float) side.optDouble(
                "retaliationLagDays", into.retaliationLagDays);
        into.maxConcurrentOps = side.optInt("maxConcurrentOps", into.maxConcurrentOps);
        into.presenceDaysMult = (float) side.optDouble(
                "presenceDaysMult", into.presenceDaysMult);
        into.recoveryDaysMult = (float) side.optDouble(
                "recoveryDaysMult", into.recoveryDaysMult);
        JSONObject weights = side.optJSONObject("opWeights");
        if (weights != null) {
            into.interdictionWeight = (float) weights.optDouble(
                    "interdiction", into.interdictionWeight);
            into.surgicalWeight = (float) weights.optDouble(
                    "surgical", into.surgicalWeight);
            into.blockadeWeight = (float) weights.optDouble(
                    "blockade", into.blockadeWeight);
            into.planetaryWeight = (float) weights.optDouble(
                    "planetary", into.planetaryWeight);
        }
    }

    /** sec 4.6's floor. Negative is meaningless (a convoy cannot deepen a wound) and above 1
     *  breaks the extended-baseline invariant; both are corrected loudly rather than silently. */
    private static void clampRecoveryDaysMult(Side side) {
        if (side == null) return;
        if (side.recoveryDaysMult < 0f) {
            Global.getLogger(ExileHegLeagueConfig.class).warn(
                    "Exiles Heg-League config: " + side.factionId + ".recoveryDaysMult "
                            + side.recoveryDaysMult + " is negative; clamped to 0");
            side.recoveryDaysMult = 0f;
        }
        if (side.recoveryDaysMult > 1f) {
            Global.getLogger(ExileHegLeagueConfig.class).warn(
                    "Exiles Heg-League config: " + side.factionId + ".recoveryDaysMult "
                            + side.recoveryDaysMult + " is above 1.0, which would heal a struck"
                            + " industry below the design-normal disruptDays band (spec sec 4.6);"
                            + " clamped to 1.0");
            side.recoveryDaysMult = 1f;
        }
    }

    /** Replace {@code into} with the array's contents, if the array is present at all. */
    private static void readIdList(JSONArray array, ArrayList into) throws Exception {
        if (array == null) return;
        into.clear();
        for (int i = 0; i < array.length(); i++) {
            into.add(array.getString(i));
        }
    }

    /** Integer twin of {@link #readIdList} -- blockade fleet sizes are difficulty points. */
    private static void readIntList(JSONArray array, ArrayList into) throws Exception {
        if (array == null) return;
        into.clear();
        for (int i = 0; i < array.length(); i++) {
            into.add(new Integer(array.getInt(i)));
        }
    }

    /**
     * sec 9.1: one multiplier over every duration, applied at load. Durations only --
     * probabilities are not durations, and fleet travel is real distance at real burn, which
     * is why {@code testing{}} exists alongside it.
     *
     * <p>Per-day RATES are divided rather than multiplied, so a compressed clock still moves
     * the meter by the same amount over the same (compressed) stretch of the cycle.
     */
    private void applyTimeScale() {
        if (timeScale == 1f) return;
        startDelayDays *= timeScale;
        debugMeterIntervalDays *= timeScale;
        tensionDwellDaysMin *= timeScale;
        tensionDwellDaysMax *= timeScale;
        escalationWindowDays *= timeScale;
        generalEngagementDays *= timeScale;
        interventionDays *= timeScale;
        deescalationDays *= timeScale;
        hegemony.retaliationLagDays *= timeScale;
        persean.retaliationLagDays *= timeScale;
        // Pass 2 durations. presenceDaysMult is a RATIO, not a duration, so it is left alone.
        // maxShipSize, FP, feasibility and the falloff are not durations either.
        interdictionCadenceDays *= timeScale;
        interdictionPresenceDays *= timeScale;
        interdictionSystemCooldownDays *= timeScale;
        interdictionConditionDelayDays *= timeScale;
        interdictionConditionDays *= timeScale;
        // Pass 3 durations. Severities, weights, FP, the feasibility multiplier and
        // extendedDisruptMult are not durations and are left alone.
        incidentConfirmDays *= timeScale;
        incidentIntelDays *= timeScale;
        strikeEscalationCadenceDays *= timeScale;
        strikeSystemCooldownDays *= timeScale;
        strikeMaxDays *= timeScale;
        strikeDisruptDaysMin *= timeScale;
        strikeDisruptDaysMax *= timeScale;
        strikeOrbitDays *= timeScale;
        // Pass 4 durations. fleetSizes, the two feasibility multipliers, the accessibility
        // penalties and the collateral flags are not durations. NOTE the sec 9 warning: blockades
        // cannot actually be tested below timeScale 1, because travel is uncompressed while the
        // payload window compresses. collateralExpiryDays is deliberately NOT scaled (sec 14.9's
        // list): it is an orphan backstop measured against vanilla's own untimescaled 0.1-0.3 day
        // blockade tick, not a duration in the war's fiction.
        blockadeCadenceDays *= timeScale;
        blockadePrepDays *= timeScale;
        blockadePayloadDays *= timeScale;
        blockadeSystemCooldownDays *= timeScale;
        // Pass 5 durations. The two *Enabled flags and the operation name are not durations.
        ceasefireIntelDays *= timeScale;
        generalEngagementIntelDays *= timeScale;
        // Pass 6a durations. FP, the two multipliers, maxShipSize, the accessibility penalty,
        // the player-buy multiplier and the two switches are not durations.
        planetaryPresenceDays *= timeScale;
        planetaryConditionApplyDay *= timeScale;
        planetarySystemCooldownDays *= timeScale;
        planetaryConditionExpiryDays *= timeScale;
        // Pass 6b durations. NOT scaled, deliberately: travelTimeoutDays and departTimeoutDays
        // bound real fleet travel, which is physical distance at real burn and does not compress
        // (sec 9.1, the Ashen relief precedent). recoveryDaysMult is a RATIO, and the fleet-point
        // figures are not durations.
        recoveryLagDaysMin *= timeScale;
        recoveryLagDaysMax *= timeScale;
        recoveryUnloadDays *= timeScale;
        recoveryRecordExpiryDays *= timeScale;
        // minRemainingDays is deliberately NOT scaled: it is a floor on a vanilla industry
        // disruption timer, which the engine runs on the real clock, not on the war's fiction.
        tensionDecayPerDay /= timeScale;
        wearinessDecayPerDay /= timeScale;
        // Pass 7 (Midan) durations. Weights, the margin, FP and the bar levels are not durations.
        midanFirstReadyDays *= timeScale;
        midanRebuildDays *= timeScale;
        midanRebuildFailedDays *= timeScale;
        midanMeasureIntervalDays *= timeScale;
        midanBarDecayDays *= timeScale;
        Global.getLogger(ExileHegLeagueConfig.class).info(
                "Exiles Heg-League director: timeScale " + timeScale
                        + " applied to all durations.");
    }

    /** The Side block for a faction id, or null if it is neither belligerent. */
    public Side sideFor(String factionId) {
        if (HEGEMONY_ID.equals(factionId)) return hegemony;
        if (PERSEAN_ID.equals(factionId)) return persean;
        return null;
    }
}
