# Cross-Director Bug Sweep — Findings (2026-10-05)

> **STATUS 2026-10-06: ALL RULED FIXES BUILT (Group 1 + §7 in §8, Group 2 in §10), Janino-clean,
> UNTESTED IN GAME. Next: ONE test run on a NEW GAME with the §10.3 checklist.** Original status: sweep
> complete 2026-10-05. Plan:
> `docs/EXILES_SWEEP_AND_TOOLING_PLAN.md` Part 1. Find-only: **nothing in this sweep changed code.**
>
> **Headline.** 72 verified findings across five directors (Heg–League 16, Ashen 18, Black Fleet 12,
> Omicron 16, Refugees 10), plus the class-J inventory. **One high:** HL-1 — turning the Heg–League
> war off (`enabled: false` or the quest flag) does not stop its managers launching ops. **Two
> cross-director families account for most of the medium findings:** the return-leg sidetracking gap
> (ASH-1/2, BF-1, OMI-1 — the fix already built for Heg–League) and station-suppression scope (ASH-3,
> ASH-4, HL-15 — the Tigra City pattern). The rest of the mediums: the units gates still open
> (ASH-8, BF-2, plus a new one in refugee salvage, REF-3), HL-2 (interdiction cadence carries across
> states), OMI-2 (early Tri-Tachyon defence responses go home before the threat arrives), REF-2
> (salvage expeditions can be pulled into battles). Most of the remainder are comments, config-doc
> drift and missing log lines, which fall under the proposed standing permissions (§7).
>
> **Next session:** Sean rules on §4 (and §7), then the fixes are built, Janino-checked and tested in
> ONE run with one checklist (plan §1.4 step 5).

## 1. Method

- **Five read-only audits in parallel**, one per director (Heg–League, Ashen, Black Fleet, Omicron,
  Refugee fleet systems), each given the same defect-class table (§2). Model: the session model
  (Opus 5.5). The plan's choice of parallel agents is the reason subagents were used at all; see the
  memory note on Fable for R&D, which predates this session.
- **One verification pass by the lead**: every reported site is re-read at its `file:line` before it
  enters §3. An agent's claim is not a finding until checked. Rejected claims are listed in §5 so the
  same false lead is not re-chased.
- Engine questions go to the vanilla API source first, then the obfuscated jar.

## 2. Defect classes

A–K are the plan's (§1.3 of the plan). L–Q were added this session from defects found in the
2026-10-03/05 runs (**assistant-originated additions**, listed here for Sean's ratification):

| # | Class | Origin |
|---|---|---|
| A | Units mismatch (raw FP vs `WarSimScript` strength) | plan |
| B | Window-end player-range branch | plan |
| C | Route-damage reliance for live FGI fleets | plan |
| D | Hidden-intel assumptions | plan |
| E | Abort semantics (off-screen "driven off") | plan |
| F | Outcome reporting outside `notifyEnded` | plan |
| G | Player-scoped flags used for NPC behaviour | plan |
| H | Fleet-order misuse | plan |
| I | Stale "vanilla does X" claims | plan |
| J | Bounty intel presentation (inventory only; deferred by ruling) | plan |
| K | Config comment/value drift | plan |
| L | Station-suppression scope (gate counts one station, suppression all-or-nothing or absent) | 2026-10-04 Tigra City kill |
| M | Return-leg sidetracking (RaidIntel fleets without `$doNotGetSidetracked` going home) | 2026-10-05 Run-5 notes |
| N | Test-pin / cycle livelocks | 2026-10-03 forceState-3 run |
| O | Silent rejections (no-target paths with no log) | 2026-10-03 diagnosis |
| P | Market-condition plugin state lost on save (`isTransient()` not false) | 2026-09-22 engine trap |
| Q | Orphan scripts / intel / listeners / retained fleet references | CLAUDE.md §7 |

## 3. Verified findings

Severity: **high** = wrong outcomes in normal play; **med** = wrong in some cases or degrades a
designed behaviour; **low** = latent, cosmetic, or record-keeping. The "Needs" column says whether
the fix needs a ruling, or would fall under the proposed standing permissions in §7 (written "§6"
in the tables, after the plan's §1.6 numbering). **None of those permissions is granted yet.**

### 3.1 Heg–League war (verified 2026-10-05)

| ID | Class | Site | Finding | Sev | Proposed fix | Needs |
|---|---|---|---|---|---|---|
| HL-1 | N (master switch) | `ExileHegLeagueDirector.java:240`; `ExileHegLeagueInterdictionManager.java:245-257`; same shape in Strike/Blockade managers | `enabled: false` (a config switch) or the quest flag `$exiles_hl_war_enabled = false` freezes only the DIRECTOR. The managers check only their own `raidsEnabled`/`strikesEnabled`/`blockadesEnabled` switches and `director.getState()` (grep: no manager reads `WAR_ENABLED_KEY`, `config.enabled`, the start delay or the idle state). So with the war "off", interdictions keep launching forever in TENSION, and blockades/counter-strikes run forever if it froze in ESCALATION. Same during `startDelayDays`. **A disabled war is not disabled.** | **high** | Expose `director.isActive()` (enabled + start delay passed + not idle) and gate every manager's launch path on it. | ruling (behaviour), but it restores the documented meaning of an existing switch |
| HL-2 | N (cadence) | `ExileHegLeagueInterdictionManager.java:248-255,274-282` | `rollCadence(config, state)` uses the state at ROLL time and is never re-rolled when the state changes. A TENSION-rate roll (≈67–112 d) carries into a 45-day ESCALATION, so roughly half of escalations launch no interdiction and `escalationCadenceMult 0.35` ("multiple interdiction ops") rarely takes effect; the short ESCALATION roll then carries back into TENSION. The strike and blockade managers reset outside ESCALATION; this one does not. | med | Remember which state the roll was for; on a change, re-roll (or rescale the remaining days by the cadence ratio). | ruling (changes op tempo) |
| HL-3 | N (test pin) | `ExileHegLeagueDirector.java:261-267,667-687` | Under `forceState 4` every pinned re-entry runs `consecutiveHotCycles++` and `escalationsReached++`; the reset is reachable only through RETALIATION. From the third re-entry on, every ESCALATION is a General Engagement (75-day window, cap 2/side). **Confirmed in the 2026-10-04 Run 3** (ESCALATION #20, GE intel, tension pinned at 99–100): part-B blockade results measured the GE cap, not the normal one. | low (test fidelity) | Under the pin, skip those increments, or close the cycle on the 4→5 exit as the forceState-3 fix does. | §6? (test-only aid; same kind as the ruled 1fdaa25 fix) |
| HL-4 | N (test pin) | `ExileHegLeagueDirector.java:261-267,386-392` | `forceState 1`: INCIDENT→RETALIATION→forced back to TENSION within one director tick, so the strike manager (0.5–1 d tick) usually misses RETALIATION; if it catches one, the once-per-cycle guard blocks the rest (the cycle never closes). Each pinned dwell still fires an incident (+8 tension). `forceState 5` posts its ceasefire intel once. | low | Close the cycle on pinned exits, or document pins 1 and 5 as cycle-frozen. | ruling (test aid) |
| HL-5 | N/K | `ExileHegLeagueDirector.java:269-272`; config `:1323` | A `tensionOverride` below the floor (15) or above the cap never equals the clamped value, so the TEST debug message fires every 0.1–0.2 d. The config comment says "0..100". | low | Compare with the clamped override; comment "floor..cap". | §6 (comment); trivial code |
| HL-7 | I | `ExileHegLeagueBlockade.java:55,594-596` | Two comments still say `isBrokenUp` is "a strength-based abort, which vanilla does not provide at all", contradicted by vanilla `FleetGroupIntel`'s group abort and by the class's own lines 93-97. The 2026-09-28 correction missed these two. | low | Fix both comments. | §6 |
| HL-8 | I | `ExileHegLeagueBlockade.java:519-521` | "notifyEnding(), which vanilla runs at the end of the RETURN leg" is true only on success; on an abort `finish(true)` → `endAfterDelay()` → `notifyEnding()` runs immediately. `isBrokenByLosses` relies on that timing. | low | Fix comment. | §6 |
| HL-14 | E | `ExileHegLeagueInterdictionStage.java:196`; `ExileHegLeagueBlockade.java:592` | Off-screen, interdictions, squeezes and blockades can never be driven off, so every off-screen op reports "completed" (victim weariness + 2 tension, never `perOpFailed`). Strikes compensate through `canBeat` at window end; presence ops do not. Run 5 agrees: all 7 failures were on screen. | low | Doc note, or an arrival-time strength resolution for presence ops. | ruling (design) |
| HL-13 | O | Strike/Blockade/Interdiction managers (e.g. `StrikeManager.java:317,389-401,442`) | ESCALATION counter-strikes, blockade attempts and interdiction attempts can fail with no log line when no source exists or pre-gate filters remove every candidate. Only RETALIATION has a catch-all line. | low | One "no target (n filtered: reasons)" line per failed attempt. | §6 (logging) |
| HL-6 | G | `ExileHegLeagueRecoveryManager.java:521-523` (+ the same line in Ashen relief, refugee convoys/salvage, BF extortion, Omicron station builder) | `MAKE_ALLOW_DISENGAGE` is player-encounter-only (its only API-visible reader is the player dialog); the "runs rather than brawls" comment implies an NPC effect. `$isTradeFleet` is what does that. | low | "player-only" comments everywhere at once. | §6 |
| HL-9 | K | `director_config.json:1560-1561` | "at the shipped 0.1 this is ~9 days, not 90": the shipped timeScale is 1. | low | Remove the stale example. | §6 |
| HL-10 | K | `director_config.json:1744-1748` | Says the strike autoresolves at window end only out of the player's range; since 2026-09-28 it does so in both branches. | low | Fix comment. | §6 |
| HL-11 | K | `ExileHegLeagueConfig.java:393-400` (also `:491-494`, `:638-640`) | Code default `strikeExtendedDisruptMult = 1f` vs the ratified config 2.0, with a javadoc saying "must stay at 1.0 until Pass 6 exists" (Pass 6 is built). It only bites if the key is lost, but it breaks the "code defaults mirror the JSON" contract. Two more javadocs are stale the same way. | low | Default → 2f; fix the javadocs. | §6 (aligning a default to the ratified value) |
| HL-12 | K | `director_config.json:1373,1390,1464` | "forced summit", "Summit CUTS", "Summit outcome weights": ruling R15 says "temporary ceasefire", never "summit". Player-adjustable file. | low | Reword comments (keys unchanged). | §6 (comment); wording is Sean's terminology ruling, already made |
| HL-15 | L | `ExileHegLeagueStrikeStage.java:45-47` | No station suppression; the gate counts only the target's station. | med | Anchor-only suppression (squeeze pattern). | **known**, ruling |
| HL-16 | J | `ExileHegLeagueRaidIntel.java:399-440`; `ExileHegLeagueBlockade.java:726-728`; `ExileHegLeagueBattleRewards.java:288-291` | Bounty tags, "System Bounty – X" / "– Over" titles, actor crest; blockade has no Bounties tag. | — | Inventory only (end-of-Phase-1 pass). | deferred |

### 3.2 Ashen Crusade war (verified 2026-10-05)

| ID | Class | Site | Finding | Sev | Proposed fix | Needs |
|---|---|---|---|---|---|---|
| ASH-1 | M | `AshenInterdictionManager.java:691,851,929,990,1018`; `AshenRaidIntel.java:350-358` | All five Ashen raid-pipeline ops (interdiction, rehearsal, Culann sweep, assault, screen) end in vanilla `ReturnStage`, and no Ashen code sets `$doNotGetSidetracked` (0 hits). Returning fleets keep engaging and joining battles: the gap just fixed for Heg–League. | med | Port `ExileHegLeagueRaidIntel.flagReturningFleets()` + the `spawnFleet` STRAGGLER check into `AshenRaidIntel`. | §6 (same fix as ruled for Heg–League) |
| ASH-2 | L/M | `AshenInterdictionStage.java:219-221`; `DO_NOT_ATTACK_DAYS = 5` (`:87`) | Station suppression is refreshed only while the presence is ONGOING, so 5 days into the return leg, raiders still in-system can attack Tri-Tachyon, pirate or Omicron-sleeper stations. That undoes the 2026-08-10 suppression fix exactly where ASH-1 leaves them unflagged. | med | ASH-1 covers most of it. Optionally keep refreshing suppression until the op ends. | with ASH-1 |
| ASH-3 | L | `AshenRehearsalStage.java:192-200` (no `getDoNotAttack` in Rehearsal or Culann stages) | The rehearsal gate counts only the TARGET's station, and the stage has no suppression at all. Magec holds Kanta's Den (pirate battlestation) and Mayasura a pirate station; Ashen is hostile to pirates. This is the Tigra City pattern (Heg–League squeeze, 2026-10-04) with 90-FP cruiser groups. | med | Exempt only the target market's station; put every other station on do-not-attack (`ExileHegLeagueSqueezeStage.isStationProtected` pattern). | §6 (same fix as ruled for the squeeze) |
| ASH-4 | L | `AshenBlockade.java:323` | Blockade suppression lists only the TARGET faction's stations (`getMarketsInLocation(system, targetFaction)`), while `AshenInterdictionStage` was widened to every station after the Ashen playtest. Pirate stations in Magec/Mayasura are unsuppressed for the pickets. Lower risk: they hold jump points. | low | Use the interdiction stage's every-station loop. | §6 (already-ruled widening) |
| ASH-5 | L | `AshenCulannStrikeStage.java:165-168` | The armadas have no suppression, so they may engage other stations in Hybrasil (Eochu Bres' star fortress), which no gate counts and which is not the objective. May be acceptable fiction; never ruled. | low | Sean to rule. If unwanted, add the anchor-only suppression. | ruling |
| ASH-6 | E/I | `AshenCulannStrikeStage.java:165-168` + `AshenRehearsalStage.java:417-429`; javadoc `AshenCulannStrikeStage:38-40` | `canBeat()` returns true, so the window-end autoresolve always LANDS; off-screen the Culann strike can never be repelled. The javadoc's "an under-specced strike is *repelled*" is true only near the player. This fits the design target ("wins comfortably if the player does not intervene"), but the comment overstates it. | low | Correct the javadoc; confirm the intent. | §6 (comment); ruling if NPC repel wanted |
| ASH-7 | E | `AshenInterdictionStage.java:217,227-229`; javadoc `:50-51` | Off-screen, an interdiction always holds its full presence (abstract routes take no losses), so "Tri-Tachyon's own patrols contest it" cannot happen off-screen. Nothing compensates. | low | Document it, or add a window-end strength check (Heg–League has the same shape). | ruling |
| ASH-8 | A | `AshenInterdictionManager.java:563-566` | Gate compares raw FP with strength (`gate = fp * feasibilityMult` vs `getEnemyStrength`). The k-logging is already in. | med | `Misc.getAdjustedStrength(fp, source)` as Heg–League. | ruling (changes launch odds) — **known** |
| ASH-9 | C | `AshenBlockade.java:271,290-296` | The route-damage break-off check is still present after the 0.67 change. Vanilla never writes route damage for a blockade, so it never fires live, and vanilla's group abort already applies the same threshold. Dead and redundant; its comment is honest. | low | Delete, or mark "dead by design". | ruling — **known** |
| ASH-10 | N/I | `AshenWarDirector.java:240-245` | The comment says "forcePhase jumps the clock once, then behaves normally", but the code PINS the phase (the config comment says so). With `forcePhase 4` the war stalls silently after a successful strike; with `forcePhase 2` at most one rehearsal ever launches (`cyclesCompleted` never moves): the Heg–League livelock family. Test-only (config is -1). | low | Fix the comment; log once when a pin blocks progress. | §6 (comment + log line) |
| ASH-17 | O/Q | `AshenInterdictionManager.java:400-408,726-748` | `tryLaunchRehearsal` runs EVERY FRAME in Phase 3 until it launches. Its "int compare, no allocation" comment holds only after the launch. Before that, if a raid is live in the target system (documented as common) or no source exists, it re-runs `pickRehearsalTarget` + `hasLiveOpIn`/`pickSource` every frame for weeks, silently. Same exposure in the Culann wave launchers. | low | Gate retries on an `IntervalUtil`; log the blocking reason once. | ruling (code), cheap |
| ASH-18 | O | `AshenInterdictionManager.java:479-480,956-962` | `pickTarget` rejections other than feasibility (cooldown, live op, player colony, ownership) are not logged. Wave 2 logs "gate blockade launched" and marks the wave even if `launchBlockade` returned silently (`blockadeEnabled` false). | low | One-line reasons; log only on a real launch. | §6 (logging) |
| ASH-11 | K | `director_config.json:645,651` | Comment says interdiction raids run "Phases 2 and 3"; code is Phase 2 only (`isRaidingPhase`, `AshenInterdictionManager.java:438-440`). | low | Fix comment. | §6 |
| ASH-12 | K | `director_config.json:619-622` | Comment calls Culann sabotage success "the ONLY trigger" for the Phase-4 strike; the §3.2a reframe makes it +0.08 preparation, with the telegraph in the director. | low | Fix comment. | §6 |
| ASH-13 | K | `director_config.json:424` | `"phase4MusterDays": 90` has no reader anywhere (grep: config only); its comment calls it the player's warning window. | low | Remove the key, or wire it up. | ruling (player-facing key: the §7 hard rule) |
| ASH-14 | K | `director_config.json:865-867`, ~`973-975` | "Phases 1, 2 and 4 launch nothing" is false: Phase-4 wave 2 launches a blockade (`AshenInterdictionManager.java:956`). Fleet names in the comments ("Hussar Blockade Fleet/Picket") predate the 2026-08-09 rename (`AshenBlockade.java:209`). | low | Fix comments. | §6 |
| ASH-15 | K | `director_config.json:386,494,652`; `AshenInterdictionManager.java:264,279-281,345-346` | (1) `timeScale` "multiplies EVERY duration" but does not scale `rehearsal.maxDays`, `culannStrike.maxDays` or disrupt days. (2) "the ONLY organic source of war score" is stale. (3) The "RESTORE TO 1" warning is stale (the value is 1). (4) Code defaults (`rehearsalFpMin` 160/220, `maxShipSize` 3, Culann days 0/4/9) differ from the ratified config values (120/160, 2, 0/0/0). Only matters if a key is deleted. | low | Align the code defaults; fix the comments. | §6 (comments); ruling for the defaults |
| ASH-16 | G | `AshenReliefManager.java:386-387` | `MAKE_ALLOW_DISENGAGE` is player-scoped; the comment implies it helps against NPC pursuers. (The convoy also sets `$isTradeFleet`, which is what actually keeps it out of fights.) | low | Mark the comment "player-only". | §6 |

Inventory (class J, deferred by ruling): `AshenRaidIntel.java:380-387` adds `INTEL_BOUNTY`; crest icon
except on payout updates (`:362-377`); `AshenBlockade` carries no bounty tag.

### 3.3 Black Fleet (verified 2026-10-05)

| ID | Class | Site | Finding | Sev | Proposed fix | Needs |
|---|---|---|---|---|---|---|
| BF-1 | M | `ExileBlackFleetReturnStage.java:19-33`; `BlackFleetRaidManager.java:510`; `BlackFleetRaidIntel.java:214-220` | Neither tier sets `$doNotGetSidetracked` going home (0 hits in BF code). Worse than for Heg–League: assault losses on the way home cut `deliveredWeights` and can turn a successful sack into a −30 prosperity FAILURE. | med | Port `flagReturningFleets()` + the `spawnFleet` STRAGGLER check into `BlackFleetRaidIntel` (covers both tiers). | §6 (same fix) |
| BF-2 | A | `BlackFleetRaidManager.java:416-429,276-284` | Gate compares raw FP with strength (units logging is already in). | med | `Misc.getAdjustedStrength(fp, source)`. | ruling — **known** |
| BF-3 | E | `BlackFleetCommerceRaidStage.java:79-90` | Off-screen, a commerce raid can never be driven out (abstract routes at full FP), and unlike the assault it has no window-end strength resolve. A raid into a heavily defended system always runs 45 days and lands Corsair Activity. The logs agree: commerce withdrawals were 33 SUCCESS, 2 FAILURE across two logs. | low-med | Add a presence-end (or periodic off-screen) strength check that can end in FAILURE, mirroring the assault's window-end resolve. | ruling (outcome odds) |
| BF-6 | F | `BlackFleetRaidIntel.java:145-152` | `isSucceeded()` is true whenever the action stage succeeded, so a sack whose RETURN failed records a −30 prosperity defeat AND an extortion SPEEDUP. The two outcome signals disagree. | low | Pick the canonical signal (e.g. SPEEDUP only if `isSucceeded() && !isFailed()`). | ruling (design) |
| BF-4 | K | `marketconditions/ExileCorsairActivity.java:48`; `BlackFleetAssaultStage.java:189,321,348` | Config/docs say Corsair Activity "lifts when they withdraw", but it is applied once with a fixed 45-day self-timer (no refresh). Commerce (applied day 5, withdraws day 45) is fine; assault windows (20–45 days + 20 per secondary sack) can outlast it, so presence-scoping silently fails. | low | Refresh while ONGOING, or derive the duration from the window. | ruling (code) |
| BF-12 | coordination | `BlackFleetRaidManager.java:336-442` | `pickTarget` never consults `ExileLiveOps.getSystemsWithActiveRaids` (0 references), unlike every other director, so the BF can launch into a system already under a Heg–League, Ashen or blockade op: the cross-director dogpile CLAUDE.md §6 says shared state prevents. | low | Consult `ExileLiveOps` (excluding BF), unless the omission is deliberate. | ruling (intent) |
| BF-10 | O/Q | `BlackFleetRaidManager.java:189-196,454-455,469-485` | (a) A null source is silent; null target/victim leave only the units line. (b) The gather/jump-point fizzle burns the 60-day system cooldown and leaves a stageless `RaidIntel` registered (vanilla constructor `addScript`), despite the `:451` comment saying fizzles "no longer burn the cooldown". Self-ends after the ending delay. | low | Log rejections; move the cooldown and intel creation after the jump-point checks. | §6 (logging); ruling (order) |
| BF-7 | H | `ExileBlackFleetExtortionManager.java:237-238,296,318` | Latent empty-queue trap: if the GO_TO ends outside the 350-unit check (large planet, fast-advance arrival, 120-day expiry), the collector idles forever, never pruned, holding a `maxActive` slot. Rare in logs (56/61 arrived, 4 lost). | low | In `advanceOps`, if `getCurrentAssignment() == null` and unresolved, re-issue or `sendHome`. | ruling (code) |
| BF-11 | Q | `ExileBlackFleetReturnStage.java:14,20` | Saved `returning` list keeps `RouteData` (and through `activeFleet`, dead fleets) serialized for up to `returnTimeout` (365 d). Bounded by one assault. | low | Drop route refs once resolved. | ruling (code) |
| BF-5 | K | `director_config.json:289-346`; `ExileBlackFleetExtortionManager.java:147` | Drift: raid/assault cadences are at production values but still labelled "TEST" under a "RESTORE BEFORE PRODUCTION" banner; extortion `debugMessages` is already **false** (CLAUDE.md and BF doc §6 say true); only extortion `startDelayDays` 20 is still a test value; `maxActive` 3 vs the code default 2 and the doc's "max 2". | low | Update BF doc §6 + CLAUDE.md; remove stale TEST labels; Sean rules `maxActive`. | §6 (records); ruling (`maxActive`) |
| BF-8 | G | `ExileBlackFleetExtortionManager.java:381` (+ javadoc `:64-66`, config `:329-330`) | `MAKE_ALLOW_DISENGAGE` is player-only; "attacked collectors always flee" holds only against the player. | low | "player-only" comment (with HL-6). | §6 |
| BF-9 | I | `BlackFleetAssaultStage.java:35-36`; `BlackFleetRaidManager.java:38-39` | Javadocs say vanilla's ground raid does "commodity theft + recent_unrest"; vanilla `doGenericRaid` applies only the stability penalty and RECENTLY_RAIDED. The inline comment was corrected earlier; these two (and BF doc §2) were not. | low | Reword. | §6 |

Inventory (class J): `BlackFleetRaidIntel.java:245-251` (`INTEL_BOUNTY`), crest icon except payouts (`:224-238`).

### 3.4 Omicron (verified 2026-10-05)

| ID | Class | Site | Finding | Sev | Proposed fix | Needs |
|---|---|---|---|---|---|---|
| OMI-1 | M | `OmicronDirector.java:506`; `OmicronRaidIntel.java:204-219` | Returning Omicron raiders (hostile to most of the Core) get no `$doNotGetSidetracked`, so they keep engaging fleets and stations on the whole trip home. | med | Port the Heg–League return-leg pattern into `OmicronRaidIntel`. | §6 (same fix). **Constraint:** must not touch the cleared-targets signal in `updateRoutes` (ruled) — the fix is on the return leg only, so it does not. |
| OMI-2 | H | `OmicronTriTachyonResponseScript.java:126-135` (+ `OmicronTriTachyonDefenseManager.java:234-242`) | A Tri-Tachyon-defence response that arrives EARLY (the dispatch window allows it) enters DEFENDING and turns for home on the next frame, because `ALERT_KEY` is only set once a threat fleet is within 1,500 u. The §6 fallback works only when the response arrives late. | med | While DEFENDING, hold until the originating threat has ended (or arrival + remaining ETA + `standDownDays`), not just while the alert is up. | ruling (behaviour) |
| OMI-3 | N | `OmicronDirector.java:270-276,460-466` | If every mature Nexus is dead while the Lattice is alive and incomplete, no node can source an incursion; every attempt returns DEFERRED at no cost, `incursionPending` never clears, and the Lattice's own raids are blocked until it completes (up to ~540 days). A permanent "no source" state is treated as temporary. Rare late-game state. | low | Treat "no incursion-capable node at all" as NO_TARGET (clear and re-arm), or let raids proceed while the incursion is unsatisfiable. | ruling (code) |
| OMI-12 | H/Q | `OmicronTriTachyonResponseScript.java:86-139` | RETURNING has no branch in `advance()`: if the despawn assignment lapses (10-day fallback, or the Nexus dies mid-trip), a `setNoAutoDespawn(true)` fleet idles forever and holds the node's source slot. DEFENDING also runs `assignThreatOrHold` (a full location fleet scan) every frame. | low | Re-issue/expire on an empty queue; throttle DEFENDING with an `IntervalUtil`. | ruling (code). Trigger SUSPECTED |
| OMI-11 | Q | `OmicronDirector.java:842-850` | At Lattice completion the UC manager is killed outright; any guards/patrols it spawned keep a 1,000,000-day PATROL_SYSTEM on a removed entity, and `nodeSourceMarkets` keeps the old Nexus as primary entity until the next launch. | low | Despawn/adopt the old fleets; refresh the proxy's primary entity in `completeUcNexus`. | ruling. SUSPECTED (engine handling of removed targets) |
| OMI-13 | L | `OmicronDirector.java:1136-1138`; `OmicronRaidActionStage` | Launch gate counts only the primary target's station; nothing stops fleets fighting non-objective stations in-system. Same shape as vanilla pirate raids. | low | Accept as vanilla, or rule on anchor-only suppression. | ruling. SUSPECTED |
| OMI-14 | B | inherited `PirateRaidActionStage.updateStatus:127-131` | In player range at window end the outcome is purely physical; out of range vanilla autoresolves on strength. Vanilla behaviour, the fleets really exist. Flagged for consistency only. | low | None unless Sean wants parity with the fixed stages. | ruling (consistency) |
| OMI-5 | A | `OmicronDirector.java:1133-1139` | Raw FP vs strength, but the code confirms the proxy's k is exactly 1.0 (bare market quality 0 → ×0.5; size mult pinned 1; doctrine officer quality ×2). **Practical error zero**; live fleets are stronger, so the gate is conservative. | low | Optional: wrap in `Misc.getAdjustedStrength` for robustness (e.g. if Nex edits the doctrine). | **known**, ruling |
| OMI-4 | I | `OmicronRaidActionStage.java:36-40`; `OmicronDirector.java:44-46`; config `:51` | "Survival-gated: wiped raiders disrupt nothing" is stale: off-screen, vanilla `autoresolve()` calls `performRaid(null, target)` on a strength comparison. The strength check compensates, so behaviour is fine. | low | Reword. | §6 |
| OMI-6 | G | `OmicronDefenseFleetManager.java:194-195`; `OmicronTriTachyonDefenseManager.java:581,591,607,675-679` | `MAKE_NON_AGGRESSIVE` (dead), `MAKE_HOLD_VS_STRONGER` and unsuffixed `MAKE_NON_HOSTILE` (player-only) used as a dormant/active switch with no "player-only/inert" comment. The real NPC effect comes from ORBIT_PASSIVE, `$doNotGetSidetracked`, per-faction flags and ignored-by. | low | Remove or comment. | §6 (comment); ruling (removal) |
| OMI-7 | K | config `:209,212`; `OmicronDirectorConfig.java:307,588` | `growth.raidFpMult` is parsed but has no consumer. | low | Wire it up or delete key + comment. | ruling (player-facing key) |
| OMI-8 | K | `OmicronDirectorConfig.java:313,703` | `debugMessages` has no Omicron reader; code default true vs JSON false. Dead key. | low | Remove, or default false and use it. | ruling (key) |
| OMI-9 | K | config `:221,239-241`; `OmicronDirector.java:946` | Asset-death multiplier comments describe the pre-2026-08-30 model: no asset row carries `mods`, so `applyAssetDeath` is a no-op, band messages can never fire from facility kills, and `getEscalationMult` has no callers. | low | Update comments, prune dead method; or restore band messages from readiness (ruling). | §6 (comments); ruling |
| OMI-10 | K | config `:6` vs `OmicronDirectorConfig.java:482-488` | Header advice "lower raid.intervalDays / incursion.intervalDays" does nothing: the legacy key is overwritten by `intervalMinDays/MaxDays`, and readiness also gates cadence. | low | Point the header at the real keys. | §6 |
| OMI-15 | O | `OmicronDirector.java:285-288,455-466` | Global cap, no free source, temporary block and launch error all retry silently; OMI-3 would be undiagnosable from the log. | low | One INFO line per failed attempt. | §6 (logging) |
| OMI-17 | K (doc) | `EXILES_OMICRON_TRITACHYON_DEFENSE.md` §1/§6 vs config `:195-196` | Doc says "200 hostile-operation FP"; config and code say 250 since the 2026-08-02 raise. | low | Update the doc. | §6 |

Inventory (class J): `OmicronRaidIntel.java:244-249` (`INTEL_BOUNTY`).

### 3.5 Refugee fleet systems (verified 2026-10-05)

| ID | Class | Site | Finding | Sev | Proposed fix | Needs |
|---|---|---|---|---|---|---|
| REF-3 | A | `exiles/ExileRefugeeSalvageThreats.java:55,61` | The LAUNCH threat screen compares a hostile fleet's `getEffectiveStrength()` (strength units: vanilla `WarSimScript` sums exactly this as strength) with `cfg.combatFp * dangerStrengthMult` (raw FP, 10 × 0.75). The in-flight screen (`:85`) correctly compares strength with strength. With k = 1–5.4, the launch floor sits k× too low, so small hostile fleets defer launches. A plausible contributor to the Isirah deferrals. | med | Compare strength with strength (e.g. the expedition's nominal strength, measured once, or `Misc.getAdjustedStrength`). Log both numbers first. | ruling (changes launch odds) |
| REF-2 | H | `exiles/ExileRefugeeSalvageExpeditionManager.java:209-213`; `ExileRefugeeSalvageAssignmentAI.java:63-64` | Salvage expeditions get neither `$isTradeFleet` nor `$doNotGetSidetracked`. DELIVER_RESOURCES/HOLD never pick targets, but per the fleet-behaviour reference only `$isTradeFleet` stops battle JOINING. Wrecks sit where battles happen, so these civilians (with escorts) may be pulled into fights beside them. Convoys set `$isTradeFleet` and are fine. **SUSPECTED**: the flag gap is verified; the joining behaviour is from the reference doc, not observed. | med | If the escorts are not meant to fight: set `MEMORY_KEY_TRADE_FLEET` at spawn or on `returnHome()`, after checking trade-fleet side effects (pirate "hostile to all trade fleets" flags). | ruling (escort intent) |
| REF-4 | Q | `exiles/ExileRefugeeConvoyManager.java:479-484` | `beginAftermath` keeps the saved `ConvoyOp.fleet` reference to a destroyed or despawned fleet through the 10-day aftermath, so the dead fleet's object graph is serialized into saves. | low | `op.fleet = null` in `beginAftermath` (and `completeActive`); readers already null-check. | ruling (code change), trivial |
| REF-5 | Q | `exiles/ExileRefugeeSalvageAssignmentAI.java:64` | An anonymous `Script` is stored in the fleet's saved assignment queue. CLAUDE.md §7: named static inner classes for anything saved. A synthetic name (`$1`) changes with source order, or between Janino and a later jar build, and a save taken during a HOLD would then fail to load. | low | Named static inner class carrying the AI/fleet/target refs. | ruling (code change) |
| REF-6 | P | `marketconditions/ExileRefugeeInflux.java:34-38` | No `isTransient()` override, so `modId`, `scaleBase`, `stabilityPenalty` and `sinceRefresh` are stripped on save. The comment claims a field "serializes for existing-save compatibility", which is false. After a load the 30-day refresh does nothing until the next `apply()`. No orphaned modifiers. **The condition is applied nowhere today (dormant)**, so there is no live impact. | low | `isTransient()` → false (as `ExileTimedCondition`), or remove the stale comment. Fix before the refugee-trust quests apply it. | ruling (code), or comment fix under §6 |
| REF-1 | G | `ExileRefugeeSalvageExpeditionManager.java:213`; `ExileRefugeeSalvageThreats.java:59,95` | Sets and reads `MEMORY_KEY_MAKE_NON_AGGRESSIVE`, which is dead in the engine (memory `obfuscated-code-verification`). The set does nothing, and the two threat screens skip any hostile fleet carrying it although such a fleet still attacks. | low | Remove the set and both skip clauses, or comment "no engine effect". | ruling (the skip changes threat screening) |
| REF-7 | G | `ExileRefugeeConvoyManager.java:405`; `ExileRefugeeSalvageExpeditionManager.java:212` | `MEMORY_KEY_MAKE_ALLOW_DISENGAGE` is read only in the player-encounter dialog. Harmless, but it lacks the "player-only" comment the convention asks for. | low | Add the comment. | §6 (comment) |
| REF-8 | K | `data/config/exiles/director_config.json:1156` | `"maxActiveConvoys": 3`, not marked TEST, while `EXILES_REFUGEE_CONVOYS.md:53` says "its default remains 1" and the code default is 1. Set to 3 in commit `e94fbe5` (2026-09-08), bundled with an Omicron change. The multi-convoy refactor is still pending retest. | low | Sean: mark TEST, or update the doc's default. | ruling (player-facing value) |
| REF-10 | I | `exiles/ExilesModPlugin.java:106-111` | Stale comments: convoys "one at a time … peaceful local-patrol removal"; salvage "one global RouteManager expedition at a time, on a fixed 30-day clock". Both retired (direct spawn, daily scan, caps). | low | Rewrite both comments. | §6 (comment) |
| REF-9 | Q | `exiles/RefugeeShortageScript.java` | Registered nowhere (0 references outside its own file); its javadoc's "re-added each load" claim is false. Orphaned prototype, plus unprefixed naming debt. | low | Inventory only: delete, or wire it up with the refugee-trust quests. | ruling |

Also noted by the audit (not defects): `salvageExpeditions.diagnostics: true` is labelled temporary
and belongs on the pre-production list. `ExileRefugeeConvoyManager.tryLaunch` retries every 7 days
with no log when no source/target is found (class O, low).

## 4. Rulings needed (batch)

Grouped so each group is one decision. Claude's recommendation is given for each; all are
**assistant recommendations, not rulings**.

| Group | Findings | Question | Recommendation |
|---|---|---|---|
| **R-A Return leg** | ASH-1, ASH-2, BF-1, OMI-1 | Port the Heg–League return-leg fix (`$doNotGetSidetracked` past the action stage + on straggler spawns) to Ashen, Black Fleet and Omicron? | **Yes**, all three. Identical in kind to the fix you approved 2026-10-05. Omicron's ruled cleared-targets signal is untouched (return leg only). |
| **R-B Station scope** | ASH-3, ASH-4, HL-15; ASH-5, OMI-13 | Apply anchor-only station suppression (the squeeze fix) to the Ashen rehearsal, widen Ashen blockade suppression to every station (as Ashen interdiction already does), and give the Heg–League strike anchor-only suppression? Separately: may the Culann armadas and Omicron raiders fight non-objective stations? | **Yes** to ASH-3, ASH-4, HL-15. ASH-5 and OMI-13 are fiction questions; recommend leaving them as they are (vanilla-like) unless a run shows a problem. |
| **R-C War switch** | HL-1 | Gate every Heg–League manager on the director being active (enabled, start delay passed, not idle)? | **Yes.** It restores what the switch is documented to do; today a disabled war keeps launching interdictions. |
| **R-D Interdiction tempo** | HL-2 | Re-roll the interdiction cadence when the state changes, so ESCALATION gets its "multiple interdiction ops"? | **Yes**, re-roll on change (simplest). It will raise ESCALATION-time interdictions to roughly what the design says, so watch it in the test run. |
| **R-E Units gates** | ASH-8, BF-2, REF-3, OMI-5 | Convert the remaining FP-vs-strength gates the way Heg–League's were? | **Yes** for ASH-8, BF-2, REF-3, each with a units log line first (instrument before tuning). OMI-5: optional, practical error is zero. These change launch odds, so they need their own test read. |
| **R-F Off-screen outcomes** | HL-14, ASH-6, ASH-7, BF-3 | Presence ops (interdictions, squeezes, blockades, BF commerce raids) can never be driven off off-screen, so they always "complete" there. Add a window-end strength resolution, as strikes and assaults have? | **Not yet**: a design change with pacing effects. Recommend correcting the comments now (ASH-6, ASH-7) and ruling on the mechanism after the fix run. |
| **R-G Test pins** | HL-3, HL-4, HL-5, ASH-10 | Make `forceState 4`/`1` and Ashen `forcePhase` close or freeze cycles cleanly (as the forceState-3 fix did), and fix the tension-override comparison? | **Yes.** Test-only paths; HL-3 already distorted the part-B blockade test (General Engagement every escalation). |
| **R-H Black Fleet design** | BF-6, BF-12, BF-4 | (1) Which outcome is canonical when a sack lands but the return fails? (2) Should the BF respect `ExileLiveOps` (other directors' live ops)? (3) Should Corsair Activity last as long as the presence? | (1) SPEEDUP only if `isSucceeded() && !isFailed()`. (2) Yes, unless the dogpile is intended — a pirate proxy piling onto a war zone is arguably in character. (3) Yes, refresh while ONGOING. |
| **R-I Omicron behaviour** | OMI-2, OMI-12, OMI-3, OMI-11 | Hold early Tri-Tachyon defence responses until the threat ends; recover idle returning responders; un-stick the incursion request; clean up after Lattice completion? | **Yes** to OMI-2 and OMI-12 (live-play effects). OMI-3 and OMI-11 are rare late-game states: yes, low priority. |
| **R-J Refugees** | REF-2, REF-4, REF-5, REF-6, REF-8, REF-1 | (1) Should salvage escorts fight, or should expeditions behave as trade fleets? (2) Clean up saved fleet references and the anonymous assignment script? (3) `maxActiveConvoys` 3 or 1? (4) Fix the dormant influx condition's transient state before the trust quests use it? (5) Remove the dead non-aggressive flag and its threat-screen skips? | (1) Trade-fleet flag on the return leg at least (civilians, "run from fights"). (2) Yes. (3) Your call; it was set in `e94fbe5` without a TEST label. (4) Yes, before Phase 2. (5) Yes. |
| **R-K Dead keys** | ASH-13, OMI-7, OMI-8 | `phase4MusterDays`, `growth.raidFpMult` and Omicron `debugMessages` have no reader. Remove or wire up? (Removing a player-facing key needs your approval under CLAUDE.md §7.) | Remove `debugMessages` (Omicron); wire up or remove the other two — your call on whether the muster warning window is wanted. |
| **R-L Records & logging** | every finding marked "§6" (= the §7 permissions) | Grant the §7 standing permissions, so the comment fixes, doc/config-comment drift and missing log lines are done in one pass without separate rulings? | **Yes.** About 30 items, none of which changes behaviour. |

## 5. Verification notes

- **No agent claim was rejected.** Every finding above was checked at its `file:line` against the
  quoted code. The lead re-read in full every high and medium finding and all of: HL-1/2/3/7/11,
  ASH-1–6/10/11/13/17, BF-1/3/4/6/10/12, OMI-1/2/3/7/8, REF-1–10. The remaining low items (mostly
  comment and config drift) were accepted on the agent's quoted evidence after a spot check of their
  neighbours; re-read them when fixing.
- Two findings were cross-confirmed by this week's logs: HL-3 (Run 3's General Engagement under
  `forceState 4`) and HL-14 / BF-3 (all Run-5 failures on screen; BF commerce 33 SUCCESS / 2 FAILURE).
- The REF-3 units claim was settled from the API: vanilla `WarSimScript` sums
  `fleet.getEffectiveStrength()` as strength, so it is in strength units.

## 6. Engine questions still open

Not settled from the API source; check against the obfuscated jar before the fixes that depend on them.
1. Does `$doNotGetSidetracked` (or `$isTradeFleet`) stop a fleet JOINING a battle already in progress?
   Sizes R-A and REF-2. (The fleet-behaviour reference says only `$isTradeFleet` affects joining.)
2. `GO_TO_LOCATION` arrival radius during fast-advance (<500 from the edge?) vs the 350/400-unit
   centre checks in BF extortion, refugee convoys and Heg–League/Ashen relief. (BF-7, OMI-12.)
3. Does `IntelManagerAPI.getIntel(Class)` allocate per call? (Cost of ASH-17's per-frame retries.)
4. Does `FLEET_BUSY` (vanilla's straggler "raid_wait") gate engagement or battle joining? (BF-1.)
5. `MemoryAPI.set(key, value, -1f)`: no expiry, or immediate? (Omicron's per-faction ROE flags.)
6. Do raid fleets engage non-objective station-mode fleets? (OMI-13, BF commerce gate.)
7. When a fleet's assignment target is removed from its location, does the assignment end? (OMI-11/12.)

## 7. Standing permissions proposed (plan §1.6) — awaiting Sean

Proposed so fixes can proceed without a separate ruling each time (not yet granted):
- logging-only changes and permanent measurement lines;
- documentation and record updates (build records, evidence logs, memory);
- applying an **already-ruled** fix to a sibling director where the change is identical in kind
  (e.g. the window-end fix, the return-leg `$doNotGetSidetracked` fix, the vanilla-threshold alignment);
- correcting a factually wrong comment.

Always requiring a ruling: any change to a ratified value, player-facing setting, balance odds,
lore/naming, or presentation (the Phase-1 intel pass).

## 8. Fix record — Group 1 + §7 permissions (built 2026-10-06; Sean approved "Groups 1 and 7")

Method (Sean, 2026-10-06): plan each fix, check the Exiles code for side effects, proceed only if
none; anything unexpected stops and goes to the Group-2 ruling. One commit per group; Janino-checked
before every commit. **All of it is untested in game.**

| Group | Commit | Findings fixed | Impact check (what was looked at → result) |
|---|---|---|---|
| R-A Return leg | `0e3b82a` | ASH-1/2, BF-1, OMI-1 (+ Heg–League refactor) | New stateless helper `ExileReturnLeg` (4 identical copies → the extraction rule). Checked: Ashen stand-down script (agrees with intent), BF custom return stage (fewer losses → more loot delivered, intended), Omicron cleared-targets signal (untouched: return leg only), subclasses (none override the hooks), saves (new field restores false → flags on load). Clean. |
| R-B Station scope | `4b30924` | ASH-3, ASH-4, HL-15 | **Found during the check:** Culann inherits the rehearsal stage, and Hybrasil holds independent + pirate markets (not only Tri-Tachyon, as first assumed). So Culann was kept exactly as before pending ASH-5 (hook overridden off; wave-2 blockade keeps the target-faction filter via a launch-time flag that defaults to the old scope on old saves). Omicron sleepers are not market stations → unaffected. |
| R-C War switch | `cf283d5` | HL-1 | Gate placed after each manager's own switch, before its clock accrues. In-flight ops finish; relief convoys deliberately ungated (healing). After a load the flag is false for ≤0.2 day (harmless). Clean. |
| R-G Test pins | `07bdae9` | HL-3, HL-4, HL-5, ASH-10 (+ the Ashen warScoreOverride twin of HL-5) | Every change is behind `forceState`/`forcePhase` ≠ -1, unreachable in production. Note for the next pinned test: under `forceState 4` each window is now a fresh escalation with its own entry salvo (more blockades than before), and General Engagement only by tension — pair with `tensionOverride` < 85. |
| R-I Omicron responses | `65e04bb` | OMI-2, OMI-12 | Existing tunables only (threat ETA + `ttDefenseStandDownDays`). A units mistake was caught before commit (`getSecondsPerDay` is real seconds per game day); the hold is stored as dispatch time + days instead. Old-save scripts restore 0 hold days (old behaviour). Responders now occupy their node slot longer — intended. |
| R-J Refugee cleanup | `0287689` | REF-4, REF-6, REF-1 (half) | REF-4: all aftermath callers verified fleet-gone; linger never reads the reference. **REF-1 stopped half-way:** Omicron sets the same flag on dormant sleeper fleets in Tri-Tachyon systems, and the salvage threat screens rely on skipping them — the skips were KEPT and documented; only the expedition's own inert set was removed. **REF-5 NOT applied:** renaming the saved anonymous HOLD script would break loading saves taken mid-salvage (likely including the current Rexx save) → Group 2. |
| R-L code | `4d3ef2b` | Log lines HL-13, ASH-18, BF-10a, OMI-15; comments HL-7, HL-8, HL-11 (+ default 1→2, matches ratified config), ASH-6, ASH-7, BF-9, OMI-4, OMI-6, REF-10 | Log lines fire once per failed launch attempt on day-scale cadences (no spam); ASH-17's per-frame retry path deliberately NOT logged. BF-9 checked against vanilla `doGenericRaid` (Recent Unrest yes, theft no). |
| R-L records | `e794e22` | HL-9, HL-10 (+Ashen twin), HL-12, ASH-11, ASH-12, ASH-14, ASH-15, OMI-4, OMI-9, OMI-10, BF-5, OMI-17 | Config diff verified comment-only (no value changed). |
| Engine fact | `0735c85` | — | See below. |

**Verification overturned one sweep claim and refined another.** HL-6 / ASH-16 / REF-7 / BF-8 said
`$cfai_makeAllowDisengage` is player-only. A FernFlower decompile of `TacticalModule` shows it is read
in the GENERAL path: a stronger fleet carrying it declines ENGAGE, and it lets any fleeing opponent go.
So the existing "runs rather than brawls" comments were right and those findings are **rejected**;
`docs/STARSECTOR_VANILLA_FLEET_BEHAVIOR.md` §4 and its table are corrected. (`makeAggressive`,
`holdVsStronger`, `makePreventDisengage` are player-gated, as the doc said.) OMI-6 was narrowed to the
verified part (`makeNonAggressive` is inert).

**Engine questions settled (§6 #1 and the REF-2 premise), from decompiled `StrategicModule.isAllowedToEngage`:**
`$doNotGetSidetracked` (and `$isTradeFleet`) make a GO_TO / GO_TO_AND_DESPAWN fleet refuse to engage
any target, so a returning fleet will not voluntarily go after a fleet that is in a battle. Battle
JOINING itself (`TacticalModule.wantsToJoin`) is refused only for `$isTradeFleet`, so a fight that
lands on a returning fleet can still pull it in. HOLD never engages; DELIVER_* engages only a target
that is physically touching it — REF-2 (salvage expeditions pulled into battles) is therefore LOW,
limited to battles that happen on top of them.

**Not done from the §7 list:** OMI-9's dead method (`getEscalationMult`) — removing code is not a
comment fix. ASH-17 (per-frame rehearsal retries) — a code change, left in Group 2.

**Test focus for the one fix run:** grep `RETURN LEG:` (all four directors), `TEST: forceState`,
`attempt: no eligible target` / `nothing launched` / `request not launched`, `Omicron response …
return order lapsed`. Watch on screen: returning raiders heading straight home; rehearsal and Heg–League
strike fleets leaving other markets' stations alone; with the war switched off, no new Heg–League ops.

## 9. Group 2 — proposals (2026-10-06); RULED and BUILT the same day, see §10

Group 1 + §7 are built (§8). These items were held back because their main effect is a behaviour,
balance or presence change, or because the impact check found a risk. **All recommendations are
Claude's (assistant-originated), not rulings.** Sean may rule by number, "accept all", or with exceptions.
After the ruling: build each approved item with the same impact-check method (§8), one commit per
group, Janino before every commit, then ONE test run with one checklist covering Groups 1 + 2 —
**a new game is required anyway** (item 11).

| # | Item | What it changes | Claude's recommendation |
|---|---|---|---|
| 1 | **HL-2** interdiction cadence carries across states | ESCALATION would get its designed "multiple interdiction ops" (~1–2 per 45-day window, still capped 2/side) | **Yes** — re-roll the cadence on a state change. |
| 2 | **R-E** FP-vs-strength gates: ASH-8 (Ashen interdiction), BF-2 (Black Fleet), REF-3 (refugee salvage launch screen), OMI-5 (Omicron) | Correct units make Ashen/BF ops bolder and salvage defer less | **Yes for ASH-8 + BF-2, behaviour-preserving:** convert units, then re-derive the multipliers from the already-logged k values so launch odds stay as playtested; boldening is a later decision. **Yes for REF-3** as-is (the doc'd intent; probable cause of the Isirah deferrals). **Skip OMI-5** (k = 1.0, error zero). |
| 3 | **R-F** off-screen ops can never be driven off (HL-14, ASH-7, BF-3) | Adds a window-end strength resolution so off-screen presence ops can fail | **Not now** — a pacing change; belongs to the tuning pass. Comments already corrected. |
| 4 | **ASH-5** may the Culann armadas fight Hybrasil's non-objective stations (Eochu Bres' star fortress, independent/pirate stations)? | Same anchor-only protection the rehearsal now has (the hook exists: `AshenCulannStrikeStage.suppressesNonTargetStations()` returns false; wave-2 blockade uses `setSuppressEveryStation`) | **Yes, protect them** — no gate counts them and the fiction is "they came for Culann". |
| 5 | **BF-6** a sack lands but its return FAILS (−30 prosperity defeat AND extortion speed-up) | Which signal is canonical | Speed-up only if `isSucceeded() && !isFailed()`. |
| 6 | **BF-12** Black Fleet ignores `ExileLiveOps` | Stops BF piling into systems already under Heg–League/Ashen ops | **Yes, respect it** — in character, but dogpiles are the Maxios-decivilization path. |
| 7 | **BF-4** Corsair Activity's fixed 45-day timer can lapse before a long assault ends | Refresh while ONGOING | **Yes.** |
| 8 | **BF-5** extortion `maxActive` 3 in config vs code default and doc "max 2" | Player-facing value | **2** (documented design) unless 3 was deliberate. |
| 9 | **REF-8** `maxActiveConvoys` 3 in config vs doc "default remains 1" (set in `e94fbe5`) | Player-facing value | Sean's call; if 3 was for the multi-convoy test, label it TEST. |
| 10 | **REF-2** salvage expeditions pulled into battles | Now **LOW** (§8 engine facts: HOLD never engages, DELIVER only on contact) | Trade-fleet flag on the return leg only, or leave it. Lean: leave. |
| 11 | **REF-5** anonymous `Script` in the salvage HOLD assignment (saved with the fleet) | Fix = named static inner class; **breaks loading saves taken mid-salvage** | **Yes, bundled with the next new game**; do not load older saves after it. |
| 12 | **Dead keys** — `phase4MusterDays` (Ashen), `growth.raidFpMult` (Omicron), `debugMessages` (Omicron) | Removing a player-facing key needs Sean's approval (CLAUDE.md §7) | Remove Omicron `debugMessages`. **Wire up** the muster window (a player warning window fits the transparency rule). `raidFpMult`: remove, or wire if growth should scale raids. |
| 13 | **Low-priority code:** ASH-17 (IntervalUtil-gate the per-frame rehearsal/Culann retries), BF-7 (extortion empty-queue watchdog), BF-11 (drop resolved route refs), OMI-3 (un-stick an unsatisfiable incursion request), OMI-11 (clean up after Lattice completion), OMI-9 (remove dead `getEscalationMult`) | Stability/performance only | **Yes, all.** |
| 14 | **OMI-13 / OMI-14** Omicron raiders vs non-objective stations; window-end parity | Vanilla-like behaviour | **No** — leave as vanilla unless a run shows a problem. |

(The item-12 recommendation above to wire up the muster window was **withdrawn** in discussion: the
telegraph already provides the warning window — see §10.1.)

## 10. Group 2 — ruling and fix record (2026-10-06)

### 10.1 Sean's ruling (2026-10-06)

- **1–7:** accept Claude's recommendations.
- **8, 9:** the 2026-09-08 caps (extortion 3, convoys 3, expeditions 3) were **deliberate**; players
  must be able to change them in the director config. Hard ceiling **12** for convoys (they stay
  small passenger transports) and for extortion collectors (count, not escort size).
- **10:** leave REF-2. **11:** build REF-5; *old test saves breaking is an accepted cost of
  development as long as new saves work.* **13:** all. **14:** no.
- **12, after discussion:** remove all three keys. `phase4MusterDays` was never read; the real
  warning window is the telegraph (`phase4TelegraphScore` 0.85, ~90 days before launch), then the
  launch-visible strike intel, then vanilla's assemble + travel (days to weeks — the "90-day muster"
  comments were wrong). `growth.raidFpMult` belongs to the pre-readiness design (op size is rolled
  network-wide). Omicron `debugMessages` had nothing to gate; `starsector.log` already carries every
  Omicron decision for post-release post-mortems.

### 10.2 Fix record (one commit per director; Janino before every commit; ALL UNTESTED IN GAME)

| Commit | Director | Items | Notes from the impact check |
|---|---|---|---|
| `0ae3420` | Heg–League | HL-2 | Built as "re-roll for the new state, **carry progress proportionally**" (60/90 d → two-thirds of the new roll) rather than a bare re-roll, which would have fired an instant launch on entering ESCALATION whenever the TENSION clock was past the short roll. New saved int; older saves read -1 and simply adopt the current state. Log: `HL interdiction cadence re-rolled`. |
| `3336572` | Ashen | ASH-8, ASH-5, ASH-17, ASH-13 | ASH-8: gate = strength × mult; `feasibilityMult` 2.0 → **0.95** (logged Ashen k 1.57–2.78, mean ~2.1). Hybrasil still rejected at any measured k (max gate ~500 vs defense 1243+). ASH-5: the strike inherits the anchor-only suppression; the wave-2 blockade now suppresses every station (pickets never fight stations). ASH-17: quarter-day setpiece tick; `Ashen rehearsal waiting: <reason>` logs once per reason. ASH-13: key removed. **Flag:** `phase4Days` 180 was sized for the non-existent 90-day muster — now has slack; left alone (pacing question). **Ruled 2026-10-06: leave for the tuning pass / possible quest integration.** |
| `6ecbcce` | Black Fleet | BF-2, BF-12, BF-6, BF-4, BF-7, BF-11, BF-5 | BF-2: commerce 2.0 → **0.95** (mean k ~2.07), assault 1.25 → **0.70** (mean k ~1.78). Averages hold; **per source it shifts** (Sallee k 2.42 reaches further, Lundy k ~1.3 less far) — that is the correct-units behaviour. BF-12: `ExileLiveOps` excluding the BF itself (its cooldown covers own ops). BF-6: failed return = SLOWDOWN. BF-4: refresh every 15 d while ONGOING, skipping markets that started paying the racket. BF-7: outbound idle in the target system = arrived, elsewhere = sent home; returning idle = re-ordered home; both logged `Extortion watchdog:`. BF-11: resolved entries nulled, list dropped at stage end. BF-5: code default 3, ceiling 12; BF doc + CLAUDE.md updated. |
| `f41a864` | Omicron | OMI-3, OMI-11, OMI-9, OMI-7, OMI-8 | OMI-3: new `LAUNCH_NO_SOURCE` — request cleared at no planning cost, retried after `readinessRetryDays`; raids proceed meanwhile. OMI-11: old UC-Nexus manager's fleets **fade out** (`Misc.fadeAndExpire`) rather than being adopted (adoption would mean re-pointing three kinds of orders and the patrol AI's source); the new manager spawns its own. **Flag (new, needs a ruling):** with `getEscalationMult` gone, `escalation.cap` and the per-node frozen escalation are now write-only — `computeRampMult()` still writes `setFrozenEscalation` on UC-Nexus death, and nothing reads it. A player-facing key, so not removed. **Ruled 2026-10-06: leave for the tuning pass / possible quest integration.** |
| `24e1876` | Refugees | REF-3, REF-5, REF-8 | REF-3: built as a **measured** floor — the last dispatched expedition's `getEffectiveStrength()` × `strengthMult` (same quantity the in-flight screen uses), raw-FP fallback before the session's first dispatch. A computed conversion was tried first and dropped: the refugee market's ship quality could push the floor *below* the old one. REF-5: `CollectOnHoldEnd` named static class. REF-8: code default 3, ceiling 12; docs updated (expeditions doc said default 1 too). |

### 10.3 The ONE test run — checklist (Groups 1 + 2 together)

**New game required** (REF-5; Exiles requires one anyway). Test pins all -1 / "". With Nexerelin or
without — say which; a non-Exile start skips debt/relations/officers. Grep `starsector.log` for:

- **Return legs (Group 1):** `RETURN LEG:` from all four directors; on screen, returning raiders go
  straight home.
- **Units gates:** `Exiles units [Ashen interdiction]` / `[Black Fleet …]` now show `requiredOdds`
  = 1/mult (1.05:1 Ashen and BF commerce, 1.43:1 BF assault); `Ashen interdiction feasibility:`
  lines show `strength N (M FP)`; Magec/Mayasura still eligible, Hybrasil still rejected.
- **BF:** `Black Fleet … attempt: no eligible target` should not spike (BF-12 removes busy systems);
  `Extortion watchdog:` lines are rare; a long assault keeps Corsair Activity on its markets to the end.
- **HL-2:** `HL interdiction cadence re-rolled for state` on TENSION↔ESCALATION changes; more
  interdiction ops inside ESCALATION windows.
- **Ashen:** `Ashen rehearsal waiting:` at most once per reason; if Phase 4 is reached, Eochu Bres'
  star fortress and Hybrasil's non-Culann stations are not attacked.
- **Omicron:** `no incursion-capable node alive` only after every mature Nexus is dead; `Lattice
  completion: retired N` if the Lattice completes.
- **Refugees:** dispatch lines carry `expeditionStrength=`; deferral lines carry `(floor N)`; fewer
  deferrals by small local hostiles. Save and reload with an expedition in HOLD — it must load.
- **Caps:** no `exceeds the ceiling` warnings at the shipped values (3/3/3).
- **War switch (Group 1):** with the Heg–League war switched off, no new Heg–League ops.

### 10.4 Test run 1 — Brent save, vanilla (no Nex), non-Exiles start (confirmed by Sean 2026-10-08; starting debt skipped as designed), Mar c206 → Mar c208 (read 2026-10-07)

Two sessions (new game, then a reload); Sean sat in Mayasura from Jun 11 c207 to the end.
**Verdict: the Group 1 + 2 fixes behave as built, with one new defect found and fixed.**

| Check | Result |
|---|---|
| Return legs | `RETURN LEG:` ×46 across all four directors (Ashen 7, BF 17, HL 12, Omicron 10). Sean: raiders and attackers left at the end of their action. **PASS.** |
| Units gates | Lines show `requiredOdds` 1.05:1 / 1.43:1; Hybrasil rejected (defense 1294–1456 vs gate 432–465), Magec/Mayasura eligible. **PASS.** |
| HL-2 | 2 re-rolls (`state 4: 26d, progress carried 63%`; `state 1: 92d, 93%`). **PASS.** |
| BF-12 / caps / watchdogs | No `no eligible target`, no `exceeds the ceiling`, no `Extortion watchdog` lines (nothing went idle). **No fault seen.** |
| Ashen | Two Port Tse rehearsals and one Tibicena; `Ashen rehearsal waiting:` never needed. Phase 4 not reached. |
| Omicron OMI-3/11 | OMI-3 not triggered (no mature Nexus lost). **OMI-11 DID run** -- corrected 2026-10-08 by the log-report tool (the hand read grepped the wrong phrase): `Lattice construction COMPLETE` Aug 24 c207, then three Lattice-sourced raids launched normally. No `retired` line = the old manager had no live fleets (player far away; vanilla despawns them), so the retire branch had nothing to do. No errors. |
| REF-3 | `expeditionStrength` measured **8–14** (33 dispatches; mostly 8 or 12): the expeditions are about as strong as their 10 FP, so the floor (6–9) is about where the raw-FP floor was. **The sweep's premise (that the raw floor sat k× too low) did not hold for the expedition side**; the units are now correct, but expect no drop in deferrals from this fix. |
| REF-5 | The save reloaded and ran; whether an expedition was holding at the reload is not in the log. |
| Errors | None from Exiles (only MagicLib/hull-spec load noise and an unrelated template mod's `mod_info.json`). |

**New defect (fixed `555ae92`): a stranded Blockade Picket.** Sean saw one Ashen "Blockade Picket"
stay in Mayasura after the cycle-2 Port Tse blockade ended. The save shows why: the fleet (source
Silent Watch) has an **empty assignment queue**, `$core_fleetReturningToDespawn` set,
`$fleetDoNotGetSidetracked` gone, and vanilla's `NPCHassler` still attached. The hassler never ends;
on the way out the picket inspected a trader, and `HassleNPCScript` calls `clearAssignments()`
(wiping the return order) and later removes only its own HOLD. Fix: both blockades (Ashen, Heg–League)
now override `giveReturnAssignments` to strip the hassler and attach `ExileReturnOrderWatchdog`, which
re-issues vanilla's return order if the queue goes empty (max 5, then fades the fleet). Log:
`Exiles RETURN ORDER WATCHDOG:`. The picket already stranded in the Brent save is not healed (old
saves expendable). Vanilla `PerseanLeagueBlockade` carries the same latent gap.

**New observations for Sean (not changed):** salvage launches were deferred by hostile **Diktat
Mercantile Convoys** (strength 52–112, classed "mobile") and by **strength-7 smugglers** near the
wreck in Mayasura — whether trade fleets and tiny smugglers should count as threats is a ruling.
**Ruled 2026-10-07:** Refugee–Diktat lowered to INHOSPITABLE (Lion's Guard stays HOSTILE; new game).
The smugglers are vanilla Independents that fly the pirate flag inside a system whose market they
trade with (`EconomyFleetAssignmentAI.doSmugglingFactionChangeCheck`); accepted as a rare case — Sean
will not soften Pirate–Refugee hostility.

**Resume point for a new session:** §10.4 is current. Next: Sean's call on the observations above,
then sweep plan Part 2 (log-report tool) and Part 3 (lessons). Local commits since `eabf2dd` are
unpushed (weekly push cadence — offer at the end of the week; push only on Sean's go-ahead).
