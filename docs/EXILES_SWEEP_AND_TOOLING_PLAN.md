# Cross-Director Bug Sweep, Log-Report Tool, and Lessons — Plan (2026-09-28)

> **STATUS (2026-10-08): PART 2 BUILT — `tools/exiles-log-report.ps1`** (build record: §2.6). Part 1's
> fixes are all built and run-tested (sweep doc §10.4). Part 3 (lessons) is next.
>
> **STATUS (2026-10-05): PART 1 DONE (find phase) → `docs/EXILES_BUG_SWEEP_2026.md`** — 72 verified
> findings, batch rulings in its §4, standing permissions awaiting Sean in its §7. Parts 2 (log-report
> tool) and 3 (lessons) NOT STARTED. Originally written at Sean's request at the end of the 2026-09-26/28
> session; assistant-originated plan, approved in principle by Sean ("we can run both of these next
> session").
>
> **Read order for the next session:** this file → the memory hand-off
> (`heg-league-war-nanoforge-canon`) → `docs/EXILES_HEG_LEAGUE_WAR.md` §14.14–§14.15 for detail.

---

## Part 1 — One-time cross-director bug sweep

### 1.1 Why

Every real defect found in the 2026-09-26/28 session existed in **more than one director**. Each was
found by accident, one director at a time. The units error was in four gates across four directors,
the window-end failure in three stages, and the "vanilla has no abort" claim in two blockades. A
single systematic pass, checking every director against every known defect class, should find the
remaining copies at once. "Complete" systems are complete only until these classes are checked
(Sean, 2026-09-28).

### 1.2 Scope: the directors and their op classes

| Director | Intel / stage / manager classes (in `data/scripts/exiles/`) |
|---|---|
| Heg–League | `ExileHegLeague*` (RaidIntel ops, `ExileHegLeagueBlockade` FGI, recovery convoys) |
| Ashen | `Ashen*` (`AshenRaidIntel`, `AshenInterdictionStage`, `AshenRehearsalStage`, `AshenCulannStrikeStage`, `AshenBlockade`, `AshenReliefManager`) |
| Black Fleet | `BlackFleet*`, `ExileBlackFleet*` (raids, assaults, extortion) |
| Omicron | `Omicron*` (raid/incursion intel and stages, defence and patrol AIs) |
| Refugees | `ExileRefugee*` (convoys, salvage expeditions): fleet-order classes only (D, G, H below) |

### 1.3 The defect classes (checklist, one row per class × director)

Each class has a known-good reference fix to compare against.

| # | Defect class | How to check | Reference (fixed) |
|---|---|---|---|
| A | **Units mismatch**: a gate compares raw FP with `WarSimScript` strength | grep `getEnemyStrength` / `getFactionStrength` / `getStationStrength` near any `fp *` or `FP *` | Heg–League gates (§14.14.9/.12). Known open: Ashen interdiction, BF raid/assault, Omicron (measured k in §14.14.8) |
| B | **Window-end player-range branch**: `inSpawnRange` → `finish(...)` with no strength check | grep `isPlayerInSpawnRange` in every stage | Fixed in all 3 known sites 2026-09-28. Re-grep for new ones |
| C | **Route-damage reliance**: logic reading `getExtra().damage` for live FGI fleets (never written live) | grep `getExtra().damage`, `isBrokenUp` | `ExileHegLeagueBlockade.isBrokenByLosses` |
| D | **Hidden-intel assumptions**: code (ours or Nex's) treating `setHidden(true)` as "nobody knows" | grep `setHidden(true)` and anything depending on concealment | Register R7 (Nex task groups read hidden intel) |
| E | **Abort semantics**: code assuming ops can be "driven off" off screen | grep `abortIfNeededBasedOnFP`, `setAbortFP`, abort-dependent outcomes | §14.14.11 finding (abstract routes never lose FP) |
| F | **Outcome reporting outside `notifyEnded`**: reports in `finish()` that aborts skip | grep `finish(`, `reportOutcome`, war-score / tension calls | Ashen 2026-08-09 lesson; Heg–League reports on `notifyEnded` |
| G | **Player-scoped flags used for NPC behaviour**: `$cfai_*` encounter flags, `makeFleetsHostile` | grep `MEMORY_KEY_MAKE_`, `cfai`, `makeFleetsHostile` | Memory `obfuscated-code-verification`. Must be commented "player-only" or removed |
| H | **Fleet-order misuse**: `ATTACK_LOCATION`/`RAID_SYSTEM` expected to hurt markets; empty queues expected to go home; `doNotAttack` expected to block battle joining | grep `FleetAssignment.` and read each against `STARSECTOR_VANILLA_FLEET_BEHAVIOR.md` | The reference doc §6 "Traps" |
| I | **Stale "vanilla does X" comments/claims** | grep comments containing "vanilla" + "no"/"never"/"does not"/"flat" | The two corrected blockade comments (2026-09-28) |
| J | **Bounty intel presentation**: aggressor tab/crest instead of payer | grep `INTEL_BOUNTY` and `getIntelTags` | **Deferred by ruling** to the end-of-Phase-1 pass. Inventory only, do not fix |
| K | **Config comment/value drift**: a comment's direction or meaning contradicting the code | Read every `feasibility*`, `abort*`, `*Mult` comment in `director_config.json` against its reader | The "Lower = bolder" error (fixed 2026-09-28) |

### 1.4 Method

1. **Find, don't fix.** The sweep produces a findings table. Nothing is changed in the same pass.
   Fixes follow Sean's rulings, except where a standing permission applies (1.6).
2. **Parallelise by director**: one research agent per director (Heg–League, Ashen, Black Fleet,
   Omicron, Refugees), each given this table. Then **one verification pass** that re-reads every
   reported site before it goes into the findings table. An agent's claim is not a finding until checked.
3. **Engine questions** go to the vanilla API source first, then to the decompiled
   `starfarer_obf.jar` (method in memory `obfuscated-code-verification`; use IntelliJ's bundled JBR
   `java.exe`, since none is on PATH).
4. **Output:** `docs/EXILES_BUG_SWEEP_2026.md`, one row per finding: class, director, `file:line`,
   evidence, severity, proposed fix, and whether it needs a ruling or falls under a standing permission.
5. **Sean rules in one batch.** Then the fixes are built, Janino-checked, and tested in **one** run
   with a single checklist.

### 1.5 Estimated size

Five parallel agents plus one verification pass: one session. The fixes afterwards depend on the
count. Class A alone is four gates, already measured.

### 1.6 Standing permissions to request from Sean (proposed; not yet granted)

To cut round-trips (feedback 2026-09-28), propose that these proceed without a separate ruling:
- logging-only changes and permanent measurement lines;
- documentation and record updates (build records, evidence logs, memory);
- applying an **already-ruled** fix to a sibling director where the change is identical in kind
  (e.g. the window-end fix, the vanilla-threshold alignment);
- correcting a factually wrong comment.

Always requiring a ruling: any change to a ratified value, player-facing setting, balance odds,
lore/naming, or presentation (the Phase-1 intel pass).

---

## Part 2 — Log-report tool (to BUILD; nothing to download)

### 2.1 Why build it

No existing tool understands Exiles' log lines. Each run read in this session was reconstructed by
hand from the same ad-hoc `grep`/`awk` commands: splitting runs, date-stamping events from
`SALVAGE_DIAG`, locating the player, pairing launches with arrivals. A script makes that one command
and gives Sean a readable page.

### 2.2 Form

- `tools/exiles-log-report.ps1`: PowerShell 5.1, matching `tools/janino-check.ps1`, because Sean is on
  Windows and PowerShell ships with it. **Read-only**: it never modifies the log.
- Usage: `& tools/exiles-log-report.ps1 [-Log <path>] [-Run last|all|<n>] [-Out <file.md>]`.
  Defaults: the live `starsector-core/starsector.log`, the last run, and a Markdown report written to
  `D:\Fractal Softworks\Starsector\logs-archive\report_<date>.md` (outside the repo; logs and reports
  do not belong in git).

### 2.3 What it does

1. **Split runs** at `Starting Starsector … launcher`. Also handle a run that spans the rotation into
   `starsector.log.1` (join the tail of `.1` to the head of the live file).
2. **Run header:** Nex present? (`nex=` in `SALVAGE_DIAG`, or the Nexerelin mod line). In-game date
   range (first/last `SALVAGE_DIAG date=`). Config pins in force (the config dump lines, if present).
   Error count excluding known vanilla noise (`flare`, `module_hightech_decor`,
   the IntelliJ-template `mod_info.json`).
3. **Timeline:** every director event line, stamped with the in-game date and the player's system,
   both carried forward from the latest `SALVAGE_DIAG … SAMPLE system=` line.
4. **Op ledger** (a table per director): launch → arrival → outcome, paired by target system and
   order. Columns: launch strength and gate, arrival strength vs defence, outcome, days, and the player's
   location at the outcome (for proximity-dependent results such as `WINDOW END`).
5. **Watch-list sections** (driven by a small pattern table at the top of the script, so new lines
   need no code): `VICTORY BONUS`, `op ABORTED`, `WINDOW END`, `GATES RELEASED` (+ `brokenByLosses`),
   `Exiles units [`, relief outcomes, weariness/tension deltas, ceasefires, escalations.
6. **Summary counts:** ops launched, succeeded and failed per director and op type; bonuses paid; anomalies
   (e.g. arrival ≥ 3× defence but FAILED, the signature that found the window bug).

### 2.4 Design constraints

- A pattern table, not hard-coded parsing: each row maps a regex to a section and a column set.
  Adding a new log line = adding a row.
- Handles the Starsector log's relative-millisecond prefix and never assumes a date that is not in a
  `SALVAGE_DIAG` line.
- Fast on 50 MB files (stream line by line; no loading the file into memory).
- If `SALVAGE_DIAG` sampling is disabled in a run, dates and locations print as `?`. The report says
  so rather than guessing.

### 2.5 Build and test

1. Build against the three logs already on disk (`starsector.log`, `.1`): the 09-26 no-Nex run, the
   09-28 run, and the 09-23 Nex run.
2. Acceptance: the report reproduces the hand-built tables in war doc §14.14.8 and §14.14.10
   (for example Yama → Laicaille `1723 vs 281 … FAILED`, player near Isirah/Tyle).
3. Then use it for the next test-run read.

### 2.6 Build record (2026-10-08)

**Usage** (PowerShell; read-only; ~1.5 s on 170k lines, ~5 s on 310k):

```powershell
& "D:/Fractal Softworks/Starsector/mods/Exiles/tools/exiles-log-report.ps1"                  # last campaign -> logs-archive\report_<date>.md
& "D:/Fractal Softworks/Starsector/mods/Exiles/tools/exiles-log-report.ps1" -Run session     # last launch only
& "D:/Fractal Softworks/Starsector/mods/Exiles/tools/exiles-log-report.ps1" -Log <archived.log> -Out <file.md> -NoTimeline
```

**What it produces:** run header (files, line range, launches, Nex, date range, reloads of an older
save, error count with vanilla noise excluded, the player's route) → summary table per director/op
(launched / OK / FAIL / open / orphan) → anomalies (strong arrival but FAILED, annotated when the
player broke it or was in the system; ops with no outcome, aged; outcomes without a launch) → one op
ledger per director (launch, arrival strength vs defence, outcome date and result, days, player at
outcome, every paired event) → watch-list sections → Exiles errors → timeline.

**Deviations from §2.3, all deliberate:**
- **Default range is the last CAMPAIGN, not the last launch.** A test run spans reloads; the new-game
  marker `DEBUG: onNewGameAfterEconomyLoad reached` (ExilesModPlugin) bounds it. `-Run session|all|<n>`
  keeps the per-launch view.
- **Dates are ranges when samples pause.** `SALVAGE_DIAG` samples stop while the player is in
  hyperspace (a month-long gap in the Brent run). A line logged in a gap shows `Apr 17-29, c207`
  (between the bracketing samples), resolved when the report is written; staleness is judged from
  real ms per game day *measured* from consecutive samples, so x1 and x10 play both work.
- **Pairing** is by director + op + target system (Omicron: node), oldest open first; the return-leg
  line closes an op that failed before its action stage (no other line exists for that case).
- **No config-pin dump:** no such line exists in the log; the `Test pins` watch section catches the
  `TEST:` lines instead.

**Acceptance (§2.5):** reproduced §14.14.10's Yama → Laicaille `1723 vs 281 … STRIKE FAILED`, player in
Tyle; Raesvelg → Kumari Kandam "ended WITHOUT", player in Valhalla; §14.14.8's Ancyra → Laicaille
arrival 534. On the Brent run it reproduced the §10.4 hand read and **corrected it**: the Lattice did
complete (Aug 24 c207), which the hand grep missed.

**Bugs found while building (fixed):** case-insensitive `-match` read `failed=false` as a failure;
`fleetSizes=[8, 5, 5]` broke the blockade-launch regex; the loader's "Loading class: data.scripts…"
lines leaked into the timeline.

**Known limits:** BF/Ashen ops that died before their action stage *before 2026-10-06* left no line at
all (they show as "stuck"); Omicron bombardments and the Ashen blockade FGI have no system/ending line
to pair, so they appear only in the watch list.

---

## Part 3 — Lessons from the 2026-09-26/28 session

> **DONE 2026-10-08.** Lessons 1–18 re-checked: **#9 and #12 corrected** (marked inline). Lessons
> 19–31 added from the 2026-10-05 → 10-08 sweep, fix and test sessions (below). The closing table lists where
> each lesson now lives, because a lesson only in this file will not be read by a future session.

### Engine and API (verified in code)

1. **Launch gates must compare strength with strength.** `WarSimScript` strength ≈ k × FP; vanilla's
   conversion is `Misc.getAdjustedStrength(fp, sourceMarket)`. k measured 1.0–5.44 by source and
   drifting over time (Omicron's proxy market: exactly 1.0). Never assume a constant.
2. **The losses abort counts abstract routes at full launch FP** (`BaseRaidStage.enoughMadeIt`), so ops
   can only be "driven off" while their fleets are spawned near the player.
3. **Vanilla writes FGI route damage only in `FGRaidAction`'s off-screen autoresolve.** A live
   blockade breaks through vanilla's own group abort (survivors < 33% of spawned FP), not through
   route damage.
4. **Window-end checks that fail in spawn range are proximity bugs**: the outcome depended on where the
   player was at one tick. Resolve on strength in both branches.
5. **Nex Special Forces read hidden intel** and defend any system where their faction has a market. They
   commit at launch, not arrival.
6. **Aggressive fleets capture objectives** (relays, buoys, sensor arrays) via vanilla `checkCapture` and
   `WarSimScript`. Ruled desirable: vanilla raiding behaviour.
7. **Vanilla system bounties list under the paying faction** and use the generic bounty icon. Ours
   inherit `RaidIntel`'s aggressor tagging (deferred fix, end of Phase 1).
8. **Fleet orders are not what their names say**: `ATTACK_LOCATION` only loiters, `RAID_SYSTEM` lurks on
   lanes, an empty queue idles. See `STARSECTOR_VANILLA_FLEET_BEHAVIOR.md`.
9. **`$cfai_*` encounter flags are player-only; `doNotAttack` blocks target selection, not battle joining.**
   *CORRECTED 2026-10-06:* **except `$cfai_makeAllowDisengage`**, which `TacticalModule` reads for
   every fleet (decompiled): a stronger fleet carrying it declines ENGAGE and lets fleeing opponents go.

### Process

10. **Measure before asserting.** Three data points suggested "×3.4"; 21 showed 1.0–5.4. The
    instrument-first step (Step 1a) turned a confident but wrong conclusion into a correct fix.
11. **The player's on-screen notes find what logs cannot** (objective captures, intel tabs). Keep
    asking for dated notes with system names.
12. **`SALVAGE_DIAG` gives the player's location every half day.** Use it to explain any
    proximity-dependent outcome. *CORRECTED 2026-10-08:* **the samples pause while the player is in
    hyperspace** (a month-long gap in the Brent run), so a date or location carried forward across a
    gap is wrong. The log-report tool prints such dates as the bracketing range.
13. **Bugs come in families.** When one director has a defect, grep the others the same day (hence Part 1).
14. **Keep untested work bounded** to about one test run's worth. Bundle builds into one checklisted run.
15. **Keep proposals and rulings apart:** the register (R-numbers + dated evidence log) vs the canonical
    doc; assistant-originated items are labelled until Sean rules.
16. **Never commit the test pins.** Stage `director_config.json` from a pin-reverted copy
    (`git hash-object -w` + `git update-index --cacheinfo`) when committing config changes.
17. **Heredocs with apostrophes can break the Bash tool.** Write long text to a scratch file with the
    Write tool, then append with `cat`.
18. **Comments drift.** Two "vanilla has no abort" comments and one inverted "Lower = bolder" were wrong.
    Re-check direction words against the code whenever a value's meaning changes.

### Lessons added 2026-10-08 (sweep, fixes, test run 1, log-report tool)

**Engine and API (verified in code or in a save)**

19. **Vanilla `NPCHassler` strands fleets.** It never ends, and the `HassleNPCScript` it starts clears
    the hassler's whole assignment queue, then removes only its own HOLD. A picket that inspects a
    trader on its way out is left with an EMPTY queue and idles for good. Any fleet carrying the
    hassler must lose it at withdrawal (`ExileReturnOrderWatchdog`). Vanilla's `PerseanLeagueBlockade`
    has the same gap, so "vanilla does it" is not proof of safety.
20. **Which flags stop which fights** (`StrategicModule.isAllowedToEngage`, decompiled):
    `$doNotGetSidetracked` and `$isTradeFleet` stop a GO_TO fleet from *choosing* a fight; only
    `$isTradeFleet` stops it *joining* one; HOLD never engages; DELIVER_* engages only on contact.
21. **Smugglers are Independents that wear the local hostile market's flag** while inside its system
    (`doSmugglingFactionChangeCheck`). A "pirate" smuggler near a pirate market is a vanilla disguise.
22. **`SourceBasedFleetManager` despawns its fleets when the player is far away.** Code that acts on
    "the manager's fleets" (OMI-11's retire) may find none. That is correct, not a failure.
23. **A multiplier re-derived from the mean k preserves AVERAGE odds only.** Per source the odds shift
    (Black Fleet: Sallee reaches further, Lundy less). Say so whenever "behaviour-preserving" is claimed.

**Process**

24. **A fix's predicted effect is a hypothesis.** REF-3 was sold as the probable cause of the Isirah
    deferrals; the first run measured expeditions at strength 8–14, about their 10 FP, so the floor
    barely moved. Write expected effects into the test checklist as "expect X; check by Y".
25. **Trace what already exists before recommending a new mechanism.** The Group-2 recommendation to
    wire up the 90-day muster was withdrawn once the existing telegraph (~90 days' warning) was traced.
26. **Hand greps miss what the tool catches.** The test read reported "Lattice not complete" because the
    grep used the wrong phrase; the report tool found `Lattice construction COMPLETE`. Grep for the
    logger's exact text from the source, or run the tool first (now in CLAUDE.md §7).
27. **Read the save when the log cannot answer.** The stranded picket's empty assignment queue,
    unset flags and attached hassler in `campaign.xml` proved the mechanism in one pass.
28. **Think through state transitions before applying a ruled fix verbatim.** A bare HL-2 re-roll would
    have fired an instant launch on entering ESCALATION; carrying progress proportionally avoided it.
    The impact-check method (plan, check side effects, stop on surprises) is what caught it.
29. **Take counts from the tool, not from memory.** "16 unpushed commits" was 10;
    `git log origin/main..HEAD | wc -l` takes two seconds.
30. **Record the test setup Sean confirms, not the one recommended.** §10.4 said "Exiles start" because
    that was the recommendation; the log's "starting fines: skipped" line showed otherwise.
31. **When a fix rides a standing permission, say so and offer the revert** (the stranded-picket fix).

### Where each lesson lives (so it is actually read)

| Lessons | Durable home |
|---|---|
| 1–4, 23 | memory `heg-league-war-nanoforge-canon` (engine lessons); war doc §14.14 |
| 5 | register R7 (`EXILES_HEG_LEAGUE_WAR_RECOMMENDATIONS.md`) |
| 6, 7 | war doc rulings; end-of-Phase-1 legibility checklist (register evidence log) |
| 8, 9, 19–22 | `docs/STARSECTOR_VANILLA_FLEET_BEHAVIOR.md` (§2.4, §3, §4, traps; amended 2026-10-08) |
| 10, 24 | memory `instrument-before-tuning` |
| 11, 12, 26, 27 | memory `log-and-save-forensics`; CLAUDE.md §7 (run the tool first) |
| 13, 28 | sweep doc §1 and §8 (method) |
| 14, 15, 31 | memory `git-commit-push-cadence`, `assistant-ideas-welcome-but-flag-them` |
| 16 | memory `heg-league-war-nanoforge-canon` (test pins) |
| 17 | this file only (tool workaround) |
| 18, 25 | memory `exiles-testing-lessons` |
| 29 | memory `git-commit-push-cadence` |
| 30 | memory `sean-tests-with-nexerelin` |

---

## Part 4 — Open bug log

### BUG-2026-10-30-A: bounty paid silently once a raid op is ENDING [LOGGED; fix after the Heg fix is verified]
**Found** in the Midan sneak-attack retest (build plan §7, "Sneak-attack retest record"): two
Hegemony bounties were paid (credits plus SYSTEM_BOUNTY_REWARD reputation), and Sean saw neither
notice. **Fixed for the Heg-League pipeline** in `ExileHegLeagueRaidIntel` (commit c63b7b5,
untested): a bounty queued while the intel is ending posts at once, and every notice is logged as
`BOUNTY NOTICE posted`.

**Mechanism:** each raid intel pays at once in `reportBattleOccurred`, queues a `BountyResult`, and
posts one combined notice 0.2 days later from `advanceImpl()`. Vanilla `BaseIntelPlugin.advance()`
stops calling `advanceImpl()` once an intel is ENDING. A raid starts ending as soon as its action
stage is over, because the return stage finishes at once. So:
1. A fight with the raiders after the op starts ending (mop-up, or the return trip) pays credits and
   reputation, but the notice is never posted. The rep message is also suppressed by design
   (`withMessage` false; it appears only inside the notice), so the reputation change is invisible
   too.
2. A fight within 0.2 days before the op starts ending is lost the same way.

Fights during the active stages notify normally, which is why Omicron, Black Fleet and Ashen have
looked fine in testing.

**Read-only review, 2026-10-30 (all bounty payers in `data/scripts/exiles`):**

| Intel | Same bug? | Why |
|---|---|---|
| `AshenRaidIntel` (`reportBattleOccurred` ~l.475, flush ~l.346 / l.528) | **YES** | pays through ENDING on purpose ("Pay through the ENDING window too"), only `isEnded()` returns |
| `BlackFleetRaidIntel` (~l.354, flush ~l.211 / l.412) | **YES** | same: only `isEnded()` returns |
| `OmicronRaidIntel` (~l.319, flush ~l.169 / l.379) | **YES** | same: only `isEnded()` returns |
| `ExileHegLeagueRaidIntel` and subclasses (strike, squeeze, interdiction, Midan) | fixed c63b7b5 | untested |
| `ExileHegLeagueBlockade` (FleetGroupIntel) | no | posts its payout with `addMessage` at once, no queue |

**Planned fix (once Sean confirms the Heg version works):** the same three lines in each of the
three intels. After queuing: `if (isEnding() || isEnded()) flushPendingBounties();`. Add the same
`BOUNTY NOTICE posted` log line to each flush. Duplicated, not extracted: the three intels already
hold their own copies of the queue (the rule-of-three exception would apply, but they are
independent directors and the change is a guard, not shared logic). A shared helper is Sean's call.

**Live sighting, 2026-10-31 (Midan sneak-attack test 3 run, between Mar 19 and May 1 c206, in
hyperspace):** Sean broke a Black Fleet commerce raid (Sallee Anchorage → Westernesse, 4 fleets,
142 FP) in its travel stage. Log: `Black Fleet corsair bounty: 48000c` and `9000c from persean
(fpDestroyed 71 + 13, involvement 1.0)`, then 0.06 s later `RETURN LEG ... (failed at stage 1)`.
The losses abort put the intel into ENDING inside the 0.2-day notice delay, and Sean saw no notice.
The Black Fleet intel doesn't log its notices, so this is strongly indicated, not proven. The
planned fix adds the `BOUNTY NOTICE posted` line to settle it. This also shows a second trigger: a
fight that itself breaks the raid (losses abort) drops its own notice, not only fights during the
return leg.

**Vanilla comparison (code, 2026-10-31): it is OURS, not vanilla's.** Vanilla `SystemBountyIntel`
(the model for all four bounty ports) does two things differently:
(1) `if (isEnded() || isEnding()) return;`, so it never pays once ending;
(2) it calls `sendUpdateIfPlayerHasIntel(latestResult, false)` inside `reportBattleOccurred`, so it
pays and announces together. Vanilla raids pay no bounty at all. We changed both, for reasons that
were sound on their own:
- **Paying through ENDING:** Omicron playtest 2026-07-03, where a post-failure mop-up battle paid
  nothing. Copied to Black Fleet, Ashen and Heg-League.
- **Deferring the notice 0.2 days through `advanceImpl`:** so the popup lands after the post-battle
  message flood.

Combined, they leave a gap: vanilla `BaseIntelPlugin.advance()` stops calling `advanceImpl()` once
ending. Each raid intel overrides only `advanceImpl` and `notifyEnded`, and none flushes anywhere
else. So any bounty queued within 0.2 days before ending, or during the 3-day ending window
(`getBaseDaysAfterEnd` = 3, never overridden), is paid silently. Fixed for Heg-League in c63b7b5 and
confirmed in test 3 (pre-landing path).

**FIXED 2026-10-31 (Sean approved after the Heg fix was confirmed; Janino-clean 3/3; UNTESTED):**
`AshenRaidIntel`, `BlackFleetRaidIntel` and `OmicronRaidIntel` each announce a bounty queued while
ending at once, and log `<Director> BOUNTY NOTICE posted: Nc | rep N | intel visible/HIDDEN`. Same
three-line guard as `ExileHegLeagueRaidIntel`, duplicated per director (independent directors, no
shared control flow). No subclasses. No per-frame cost: the check runs only when a bounty is paid.
**Report tool:** the "Bounties paid" section is now "Bounties paid and notices". Every payout line
should be followed by a NOTICE line, so a missing notice is visible at a glance. Re-reading the
2026-10-31 run under it shows the bug plainly: the Midan payouts → NOTICE, the Black Fleet payouts →
none.
**Verify in play:** break a Black Fleet, Ashen or Omicron raid in a fight, or fight its fleets
after it fails. A notice should pop up, with a matching NOTICE line in the report.
