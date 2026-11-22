# Hegemony–League War Director — Complexity Review

> **STATUS 2026-11-08 (later): ALL SIX RULINGS TAKEN (§8) AND THE APPROVED WORK BUILT — S1, S2,
> tier 1, the task-group watch deletion, debug defaults, tier 3 and pacing A. Janino-clean 11/11,
> one local commit, UNTESTED IN GAME; build record and smoke-test list in §10.** Two review
> findings were revised during the build and are marked in §5.1: the unused getters stay as
> Phase-2 quest seams (design goal 1.4.5 says the director must expose them), and
> `incidentTypeId` stays because pacing ruling A now reads it.
>
> **STATUS 2026-11-08: REVIEW WRITTEN, NOTHING CHANGED IN CODE.** Requested by Sean 2026-11-08
> ("each director has been larger and more complex than the last ... review it to make sure we're
> doing everything in the simplest and most-efficient way possible"). Ruled to run BEFORE Pass 9
> (2026-11-07) so the independent Pass-9 reviewer sees the simplified director. Scope defaults
> accepted by Sean 2026-11-08: code and dead config in scope; test pins stay until Pass 9;
> measurement log lines stay; everything the player sees is protected; Pass 8 dialogue reviewed,
> not proposed for removal; asymmetry protected but costed; the Midan ladder simplifiable, not
> removable, attribution layer stays deferred; the Nex task-group watch proposed for retirement
> after Pass 9; report only, fixes after rulings. §8 lists the rulings this review needs.
>
> **Method.** Fable read the director tick, registration, save surface, every script/listener
> registration, every sector-wide walk, the recovery manager, the Midan module, the Nex layers and
> the intel lifecycles; an Opus agent read `ExileHegLeagueDirector` and `ExileHegLeagueConfig` in
> full with no prior conclusions (its findings were verified line by line before inclusion); a
> second Opus agent ran a mechanical duplication census over the four operation families and the
> shared classes. Everything below cites file:line and was checked against the tree at 21e2d12.

---

## 1. Verdict

**Stable.** Five persistent scripts, all guarded on load; every timed intel adds and removes its
own script; every fleet listener dies with its fleet; every accumulating list is bounded; every
field added after Pass 1 has a save-safe default. Two narrow correctness defects were found, both
in the Midan landing path and neither yet seen in play (§3.5).

**Cheap at runtime.** The per-frame cost is five interval gates. All real work runs on day-scale
clocks and the heaviest recurring job (the Midan monthly measure, which builds two ~50-ship fleets
in memory and discards them) runs twelve times a game year. CPU is not where this director costs
anything; its cost is size, and size is where the player-visible bugs will hide.

**Where the size is.** Of 15,862 lines, roughly a third is configuration plumbing and
per-operation copies of the same knob, a tenth is measurement scaffolding, and about 650 lines are
second copies of code that exists elsewhere in the director. The war's config block is 1,310 lines
and 296 keys, half of `director_config.json`. The recommended trims (§5, tiers 1–3) remove about
500 lines and five inert keys **without changing anything the player sees**; the structural
options (§5 tier 4) could remove several hundred more at the cost of a save-shape change, and the
recommendation is to leave those for a deliberate post-beta refactor or not do them at all.

---

## 2. Size and shape

| Part | Files | Lines | Notes |
|---|---|---|---|
| `ExileHegLeagueConfig` | 1 | 1,881 | one JSON parse per load; 296 keys |
| `ExileHegLeagueDirector` | 1 | 1,454 | tension meter, six-state cycle, incident table, Nex sync |
| Operation families (interdiction, strike, squeeze, blockade) | 11 | 5,395 | managers 2,391; stages 1,319; raid intels 941; blockade 744 |
| Shared by the families (geography, rewards, incident table, three beat intels) | 6 | 1,675 | |
| Midan ladder (module, strike stage/intel, composer, travel/assemble/return stages, incident, directive) | 9 | 2,746 | |
| Eventide wound + sequence; story announcement | 3 | 597 | |
| Recovery convoys | 1 | 1,068 | |
| Nexerelin layers (support, task-group watch, pin, hook) | 4 | 600 | |
| Black Fleet ambush (BF side of the proxy) | 1 | 446 | |
| **Total** | **37** | **15,862** | Ashen 5,799 · Omicron 5,037 · Black Fleet 3,613 · Refugees 1,786 |

Config: `director_config.json` is 2,645 lines / 653 keys; the `exilehegleague` block is lines
1334–2644, 296 keys. Every key the Java reads is in the JSON and every JSON key is read; five are
read and then never used (§5.1).

---

## 3. Stability

### 3.1 Scripts and registration — PASS
- Five `EveryFrameScript`s registered at new game and guarded with `hasScript` on load
  (`ExilesModPlugin:136–162`, `248–275`): director, interdiction, strike, blockade, recovery. The
  squeeze "manager" is a static library called from the strike manager, not a script. The Midan
  module is a saved field of the director, not a script.
- Timed intel (`IncidentIntel:111/117`, `CeasefireIntel:92/98`, `EngagementIntel:78/84`,
  `StoryAnnouncementIntel:130/136`) adds itself in its constructor and removes itself in
  `notifyEnded()`; the on-load heal (`ExilesModPlugin:284–309`) re-attaches strays and is idempotent.
- `StoryAnnouncementIntel.DelayedPost` and `ExileEventideSequence` are one-shot scripts with
  `isDone()`; the blockade's `MilitaryResponseScript`s are vanilla's and self-expire on
  `responseDuration` (`MilitaryResponseScript:271`).
- `ExileReturnOrderWatchdog` is attached per returning blockade fleet; the Midan strike intel
  stands its fleets down on the return leg (`MidanStrikeIntel:159–163`).

### 3.2 Save surface — PASS
- Director state is primitives, Strings and Integer sentinels (`Director:85–143`); the two
  "converted save" traps (`lastRetaliationCycle`, the Pass-5 posting guards) use `Integer` so a
  JVM-default 0 cannot collide with cycle 0. `readResolve` rebuilds every transient.
- Recovery: `ledger` bounded at `ledgerMaxRecords` (8), `activeOps` at `maxActive` (2); records
  expire (`RecoveryManager:402–418`); industries are ids, not references.
- Nothing retains a fleet, market or intel reference across ticks except `ReliefOp.fleet`, whose
  dead-fleet branch drops the op; the director's `find*` helpers look intel up each time.
- Field initialisers do not run on old saves: every post-Pass-1 field was checked and is either an
  Integer sentinel or safe at 0/false. `daysSinceIdleLog` loads as 0 instead of 9999 on a pre-field
  save, which only delays the first idle log line by 30 days.

### 3.3 Listeners — PASS
Every `FleetEventListener` is attached to a fleet the class itself spawned (`RaidIntel:365`,
`Blockade:286`, `MidanStrikeIntel:331`, `RecoveryManager:500`) and dies with it. No sector-level
listeners are registered.

### 3.4 Nexerelin — PASS
No Nex type is reachable from the startup graph: `ExileHegLeagueNexPin` is loaded by name behind
the `ExileHegLeagueNexHook` interface, after `isModEnabled("nexerelin")`, and every call returns a
flag rather than throwing. The task-group watch uses two string constants and vanilla API only.
Pins and conquest shields apply once per load (`Director:1428–1433`). With Nex absent the same
code paths run and no-op (verified in the 2026-11-04 no-Nex run and the 2026-11-07/08 Nex runs).

### 3.5 Two defects (not yet seen in play) — FIX
- **S1. A Midan strike that resolves in the first 0.1–0.2 game days after a load loses its
  landing.** `activeThisTick` is transient and false until the director's first interval fires
  (`Director:183, 262–289`); `landMidanRung` returns on `!isActive()` (`Director:1080`) and logs
  "LANDING SKIPPED ... war is not active", which is false in this case. The ladder has already
  advanced (`Director:1056`), so the rung's tension raise and incident are gone for good. The op
  managers skipping one launch window is harmless. **Fix:** save the last known active flag (a
  plain boolean, loads as false only on pre-field saves, which is the current behaviour) or
  recompute the premise inside `landMidanRung`. Two lines.
- **S2. A Midan landing during DE-ESCALATION consumes the "talks collapsed" halving on a dwell that
  is immediately discarded.** `Director:1103` calls `closeCycle()`, which `beginState(TENSION)`s
  and rolls a TENSION dwell that spends `lastTalksCollapsed` (`Director:801–803`); the very next
  line is `beginState(RETALIATION)`. The log also gets a fake "-> 1 TENSION" transition. **Fix:**
  a `closeCycleWithoutDwell()` (reset the cycle fields, do not begin a state) for this one caller.
  Cosmetic in effect, but the fake transition would mislead a log read.

---

## 4. Efficiency

### 4.1 What runs per frame
Five scripts, each: three null/pause checks, `convertToDays`, `IntervalUtil.advance`, return. Plus
vanilla's own per-frame advance of whatever `RaidIntel`s are live, in which our stages gate their
work on day counters (`MidanStrikeStage:224`, `MidanTravelStage` cohesion every `COHESION_DAYS`,
`MidanStrikeIntel:166` distress check every `DISTRESS_CHECK_DAYS`). No fleet or market walk is on a
per-frame path anywhere in the 37 files (grep of `getMarketsCopy`, `getAllEntities`, `getFleets`,
`getStarSystems`, `getIntel(` — every hit is behind a tick gate or on a launch/outcome path).

### 4.2 What runs per tick (director 0.1–0.2 d; managers 0.5–1 d; recovery 0.1–0.2 d)
- Director: war switch read, hostility self-heal (two faction lookups), **`retireVanillaHostilityIntel`
  (two faction lookups + a `FactionHostilityManager.getHostilties` call that builds a conflict-id
  String) on every tick for the whole campaign** for a job that acts once (`Director:286`,
  `NexSupport:225–240`), the premise check (`isIdle`: two `getMarketsCopy()` walks every
  `premiseCheckIntervalDays`), meter decay, the Midan module's daily/monthly work, the state machine.
- Midan daily (`MidanModule:296–345`): one `WarSimScript.getEnemyStrength` over the target system,
  one station read, one walk of the live `RaidIntel`/`FleetGroupIntel` lists. Monthly: the Strike
  Force is composed from scratch (two fleets, ~49 ships, officers) and discarded
  (`MidanModule:177–199`); every logged measurement since 2026-10-25 has produced the same 590/467,
  so this could be composed once per load.
- Managers: director lookup via `ExileHegLeagueDirector.get()` (a sector script-list walk) then
  state/cadence arithmetic; a launch attempt walks `getMarketsCopy()` once.
- Blockade (vanilla's 0.1–0.3 d interval while live, at most two live): collateral recompute over
  the system's markets, one `get()` walk each time (`Blockade:386`).
- Nex task-group watch (daily, Nex only): `RouteManager.getRoutesInLocation` per live op, a few
  small allocations.

### 4.3 Nits (none worth a ruling on their own; folded into §5)
- `ExileHegLeagueDirector.get()` has 23 call sites in 12 classes; all are on slow paths, but
  `InterdictionManager.isExcludedMarket` (`:575–580`) calls it once per market inside
  `InterdictionStage:285` (a handful of markets, once per arrival). Pass the config in.
- `holdsAnyMarket` is called twice back to back (`Director:359–362`), each copying the market list.
- `getElapsedDaysSince(startTs)` is computed twice per tick (`Director:272, 324`).
- `ExilesModPlugin.postStoryAnnouncementTestIfPinned` (`:1240`) parses the whole war JSON a second
  time on every load, to read one test pin.

**Verdict:** nothing here is measurable in play. The 2026-11-07 run carried the war director, the
Midan ladder, two ambushes and the task-group watch alongside the other four directors for 22
game months with no reported stutter and zero Exiles errors.

---

## 5. Trim candidates

Marked **[player-facing]** where a player could notice; **[ruling]** where Sean's approval is
needed under the CLAUDE.md settings rule. Everything else is internal and reversible.

### 5.1 Tier 1 — dead and inert (≈150 lines, 5 keys; no behaviour change)
- Dead methods with zero callers: `Director.roll()` (`:1414`), `reportCollateralDamage()` (`:1341`),
  `reportMidanInterference()` (`:1347`; the false-flag path calls `addTension` directly at `:877`),
  and `isDebugMessages`. **REVISED AT BUILD:** the five state getters nothing calls yet
  (`getWeariness`, `getStateDaysRemaining`, `getLastOutcome`, `getIncidentTypeId`,
  `getIncidentSiteMarketId`) are KEPT — design goal 1.4.5 says the director must expose its seams
  now for Phase-2 quests, and these are those seams.
- `incidentTypeId` (`Director:97`): the review called it write-only. **REVISED AT BUILD:** pacing
  ruling A reads it (a Midan incident bypasses the contained roll), so it stays and now earns its
  place. `incidentSiteMarketId` stays as a log field.
- `RecoveryManager.RELIEF_TARGET_KEY` (`:112–121, 527`): a sector-memory key "written here, read by
  NOTHING" — the 7f proxy ended up using the fleet flag instead. Remove the write and the constant.
- `nexLayersSynced` can never be true when `loadConfig()` runs (`Director:166, 1429–1432`); the
  `tick == null` / `random == null` checks at `Director:243–244` duplicate `readResolve`.
- `retireVanillaHostilityIntel` per tick → a saved once-latch (`Director:286`).
- `postStoryAnnouncementTestIfPinned` → read the pin from the director's already-loaded config, or
  drop the pin now that 7b is tested.
- **[player-facing] [ruling] Five config keys that are parsed and never used:**
  `tension.gains.perCollateral` (JSON 1464; only reader is the dead method above),
  `planetaryBlockade.prepDays` (2081), `planetaryBlockade.escalationSqueezes` (2138),
  `midan.strike.assemblyDistance` (2514, unused since 2026-10-30), and `midan.strikeForce.escortCap`
  (2481, a fallback no rung reaches with the shipped per-rung caps). A player editing them sees no
  effect. Recommendation: delete the first four; keep `escortCap` only if the per-rung list may
  ever be shorter than the rung count.
- Stale javadocs: the director still says "PASS 1 ... launches NOTHING" (`Director:14, 27`); the
  config says the midan block is "still absent" (`Config:40`); two comments have slid off the
  fields they describe (`Director:167–175, 209`).

### 5.2 Tier 2 — measurement scaffolding, retire on a schedule (≈280 lines, 2 keys)
- **`ExileHegLeagueNexTaskGroupWatch`** (227 lines; `logNexTaskGroups`, `nexTaskGroupScanDays`):
  the R7 measurement has answered its question (task groups pass through; one denied a strike at
  Galatia 2026-11-07; none joined a defence). **Recommend `logNexTaskGroups` false now and deletion
  after Pass 9**, when the §7.6 Nex table is written up from the runs already logged.
- `InterdictionManager.logGeographyResolution` (≈50 lines, one-shot per load): a 2026-09 startup
  diagnostic for the whitelists; the lists have not changed since. Delete after Pass 8's config
  sweep confirms them.
- **[player-facing] [ruling] `debugMessages` ships `true`** in the war block (JSON 1341) and the
  Midan block (2231), and the code defaults for `debugMessages`, `recoveryDebugMessages` and
  `logNexTaskGroups` are `true` (`Config:160, 177, 799`), so a missing or broken JSON would turn the
  yellow DEBUG lines on for players. This is the Black Fleet pre-production checklist item again.
  Recommend: code defaults false now; JSON false before the alpha build.
- Test pins (`forceState`, `tensionOverride`, the four Midan pins, `postStoryAnnouncement`,
  `forceIncidentOffender`): **keep until Pass 9 is done** (Sean's default), then prune the ones
  Pass 9 does not use.

### 5.3 Tier 3 — the identical copies (≈135 lines; the rule-of-two is met; no behaviour change)
From the census: ≈650 of the families' 7,070 lines are second copies; ≈200 are truly identical.
Extract only the identical ones:
- `hasLiveOpIn(system)` — four byte-identical copies (`InterdictionMgr:441–457`, `StrikeMgr:364–381`,
  `SqueezeMgr:357–372`, `BlockadeMgr:267–282`). One static on `ExileLiveOps`, which already holds
  the dogpile check it is always paired with. −48 lines.
- `pickFocusMarket` — two identical copies (`InterdictionMgr:650–664`, `BlockadeMgr:424–440`) →
  `ExileHegLeagueGeography`. −15.
- The enabled-switch reader (memory flag over config boolean), ×4 (7 lines each), and `rollCadence`
  ×3 (6 lines, same ±25% jitter) → two small statics on the director. −30.
- `fireIncident` / `seedPinnedRetaliationParties` share the offender coin, party assignment and
  site pick (`Director:849–867` vs `902–919`) → one helper. −25.
- `logGeographyResolution`'s two resolver loops repeat each other (`InterdictionMgr:597–617` vs
  `624–643`) → −15, or delete with tier 2.

Leave alone, deliberately: the manager tick prologue and script boilerplate (saved-class pattern,
differs per manager), the raid build pipeline (the config prefixes and stage classes differ), the
stage `showStageInfo` prose, the station-suppression loops (one guard differs each), the
`reportOutcome` skeletons (different verdict sources). Those are the "one guard, one field"
copies the Pass-5b ruling said not to unify.

### 5.4 Tier 4 — structural options [ruling]; recommendation: not now
- **(a) Fold the three op managers into director-owned modules**, the Midan-module precedent. Saves
  three script registrations and three load guards, 20 of the 23 `get()` walks, and ~130 lines of
  tick/lifecycle boilerplate; makes "one director, four modules" literal. Costs: every existing save
  breaks (expendable by Sean's standing ruling, but a new game is already required), a day of work
  and a retest of Passes 2–4 and 6. Nothing the player sees changes. **Not recommended before the
  alpha**: the current shape is proven across eleven test runs and the saving is boilerplate.
- **(b) Merge the Ceasefire and Engagement beat intels' presentation** (≈77 near-identical lines,
  27 differing after normalisation) into a shared base. Changes a saved class hierarchy. The gain is
  small and the two beats are written to diverge further in Phase 2. Not recommended.
- **(c) Merge the strike and squeeze launch paths.** Not recommended: the differences (whitelist
  first, per-market vs per-system feasibility, hidden-until-arrival opt-out) are rulings.
- **(d) Collapse the per-family knob copies** (14 knobs × 4 families in the census table). They
  are the per-operation tuning Sean asked for and several carry different ratified values. Keep.

### 5.5 What the asymmetry costs (protected; costed as asked)
`ExileHegLeagueConfig.Side` carries seven per-faction fields (op weights, presence multiplier,
concurrency cap, recovery multiplier, ...) and the director reads them at every side pick. About
120 lines across the config and the four pickers; zero runtime cost. It is design goal 1.4.1 made
concrete and the Hegemony's `recoveryDaysMult` is one of the few places the production advantage
shows. Keep.

---

## 6. Protected — not proposed for change
Intel beats (incident telegraph with its contained update, ceasefire, General Engagement),
`Admiral Cunningham's Vow` and the story-announcement intel, the Midan distress call and strike
intel, the Eventide on-screen sequence and wound, the four market conditions and their tooltips,
relief convoys and Black Fleet ambushes in space, every bounty and victory-bonus payment and
notice, the Pass-8 interception dialogue (**CORRECTION 2026-11-08: NOT BUILT** — designed in war doc
§4.5 and ruled V1 as the final ops pass, R4; no code, build record or commit exists; the review
and this session's messages wrongly called it "built and tested in isolation"), the Nex relation pin
and conquest shields, the war/recovery/op enabled switches (quest seams), and every measurement
log line that the report tool has a row for.

---

## 7. Pacing appendix — for rulings, not simplicity
- **A. The anticlimactic CONTAINED exit** (agenda item 1, 2026-10-31). Arithmetic, not luck: rung 1
  raises tension to 50, between `warningThreshold` 45 and `hawkThreshold` 60, so the RETALIATION
  exit is a 30% talks / 70% contained roll (`Director:468–487`) and the only player-facing ending is
  the telegraph's closing update, which arrives while the Hegemony's reprisal is still sailing.
  Options: (i) let a Midan-caused incident bypass the contained roll (it is the League's "strike",
  so the Hegemony should at least retaliate visibly); (ii) raise rung 1's target above 60 so it
  escalates; (iii) delay the exit until the retaliation op has reported. (i) is the cheapest and
  keeps the ladder's 50/65/88 shape.
- **B. The Midan bar can be met with zero "means".** Score = 0.4·means + 0.3·distraction +
  0.3·cover; distraction is full at two Hegemony ops; cover is 1.0 in INCIDENT/RETALIATION/
  ESCALATION; the bar starts at 0.6 and decays to 0.15 over 365 ready days (`JSON 2629–2641`). So
  in RETALIATION with two Hegemony ops the score is 0.6 with the target at full strength, and after
  a year ready any crisis state launches regardless of the target. Seen 2026-11-07: rungs 2 and 3
  launched with means 0.21 / 0.03 / 0.0. In production the 730-day rebuild paces this, so it is a
  question of fiction, not frequency: should Midan strike a target at full defence? Options: (i) a
  minimum-means gate (e.g. means ≥ 0.1, "the defence must have thinned at all"); (ii) stop the bar
  decaying below the cover weight; (iii) leave it: the strike force is sized to the live defence
  anyway, so "means" is already enforced by feasibility. **Assistant lean: (iii) plus a comment**,
  because feasibility (`escortsNeeded`) already refuses an infeasible target and the 2026-09/10
  BLOCKED months showed it working.
- **C. Readiness stretch.** Sean's lean (2026-11-07): `rebuildDays` 730 → the config comment's
  planned 730/913 "feels right, still a guess". Current: 365 / 730 / 1095. No change until ruled.
- **D. Tension while the war is off** — RULED 2026-11-08: ops in flight finish and report; relief
  convoys launch regardless. Recorded in the Pass 7 plan.
- **E.** The S1/S2 fixes (§3.5) touch the same landing path and should land with whatever A decides.

---

## 8. Rulings — ALL RULED by Sean, 2026-11-08 (walk-through)
1. **Tier 1 config keys:** delete `tension.gains.perCollateral`, `planetaryBlockade.prepDays`,
   `planetaryBlockade.escalationSqueezes`, `midan.strike.assemblyDistance`. **Keep
   `midan.strikeForce.escortCap`** "just in case", with a comment beside it saying it is currently
   unreached.
2. **Tier 2:** the Nex task-group watch is **deleted now** ("a terrific idea that gave us very
   valuable data, but it has no use in Production"), not after Pass 9. **The geography log stays**:
   Sean is considering handing some Hegemony/League worlds to the other side at game start to show
   a decade of war; the log catches whitelist typos if ids change, and can print owners (a two-line
   change) when that pass starts. **`debugMessages` → false now** in code defaults and JSON; the
   toggles stay as player options ("useful for some players to self-troubleshoot or if they're
   just curious").
3. **Tier 3:** extract as a block, minus the geography-log fold.
4. **Tier 4:** not now, all three. The manager fold is recorded as a post-alpha option; Sean's read
   ("a lot of effort for little reward") and the assistant's agree, and the per-manager script
   shape is itself the idiomatic vanilla form.
5. **S1 and S2:** build now, first.
6. **Pacing:** A = option (i), a Midan-caused incident bypasses the CONTAINED roll (the Hegemony
   always retaliates visibly after a false-flag strike); B = leave, feasibility already enforces
   "means", add a comment; C = 730/913 stays a recorded lean, no change.

**Build order (approved):** S1 + S2 → tier 1 → task-group watch deletion + debug defaults → tier 3
→ pacing A; one Janino pass and one local commit per step; then one new-game smoke run of a full
cycle plus one Midan rung (pins as for 7g), then Pass 8's live run and Pass 9.

---

## 10. Build record [2026-11-08; Janino-clean 11/11; one local commit; UNTESTED IN GAME]
Everything Sean approved in §8, in one working-tree change (the steps share files, so one
itemised commit rather than one per step):
- **S1:** `activeThisTick` is now a SAVED boolean (`ExileHegLeagueDirector`); a load resumes with
  the pre-save answer, so a Midan strike resolving in the first tick after a load lands normally.
  Old saves load it false for one tick, the previous behaviour.
- **S2:** `closeCycle()` split into `resetCycleFields()` + `beginState(TENSION)`; the
  DE-ESCALATION landing calls `resetCycleFields()` and then begins RETALIATION directly. No
  discarded dwell, no spent `lastTalksCollapsed`, no fake "-> 1 TENSION" line.
- **Tier 1:** removed `roll()`, `reportCollateralDamage()`, `reportMidanInterference()`,
  `isDebugMessages()`, the redundant `nexLayersSynced` once-flag (`loadConfig` runs once per load
  by construction), the write-only `RELIEF_TARGET_KEY` and its clear-on-finish helper
  (`ExileHegLeagueRecoveryManager`), and the four inert config keys (`perCollateral`,
  planetary `prepDays`, `escalationSqueezes`, midan `assemblyDistance`) from JSON, fields and
  reads. `escortCap` kept with "currently unused" comments in both files.
  `retireVanillaHostilityIntel` now runs daily (transient day counter) instead of every tick.
  Director and config class javadocs brought current. The `tick`/`random` null checks were kept
  (belt and braces; two lines).
- **Task-group watch:** `ExileHegLeagueNexTaskGroupWatch.java` deleted with its two config keys,
  field and tick block. The log report's "NEX TASK GROUP" row stays so old logs still parse.
- **Debug defaults:** `debugMessages` false in the war and Midan JSON blocks and in the code
  defaults (`debugMessages`, `recoveryDebugMessages`); the toggles remain player options.
- **Tier 3:** `ExileHegLeagueGeography.hasLiveOpIn` and `pickFocusMarket` (the four managers
  delegate to them, the Pass-3 delegate pattern); `ExileHegLeagueDirector.switchEnabled` (six
  callers: war, raids, strikes, squeezes, blockades, recovery) and `jitterCadence` (three);
  `seedParties` shared by `fireIncident` and `seedPinnedRetaliationParties`.
- **Pacing A:** in the RETALIATION exit, `ExileHegLeagueMidanIncident.ID.equals(incidentTypeId)`
  below the hawk line goes straight to POLITICAL INTERVENTION with reason PREEMPTIVE; log line
  `RETALIATION exit: CEASEFIRE TALKS (Midan incident -- contained roll bypassed)`. Above the hawk
  line ESCALATION is unchanged.
- **Net:** 10 code/config files changed, about 515 lines removed and 200 added (javadoc
  included); the director tree is 37 → 36 files.

**Smoke test (new game, pins as for 7g: ReadyNow + StartRung 1 + ForceDirective, LaunchNow off or
on):** one organic cycle to the RETALIATION exit (any branch), one Midan rung 1 landing in
TENSION → expect `became the INCIDENT`, then at the RETALIATION exit the new `CEASEFIRE TALKS
(Midan incident -- contained roll bypassed)` line and the ceasefire intel; no yellow DEBUG lines;
no `NEX TASK GROUP` lines; a save/reload mid-strike still lands (S1 regression check only — the
one-tick window itself cannot be hit on purpose). A NEW game logs no `WAR SWITCH: ON` line (the
field initialises true); only a save from before the field does. Read with
`tools/exiles-log-report.ps1`.

#### Smoke-test record [2026-11-08; Natasha save, NEW game, Nexerelin, all four Midan pins on; Mar 2 → Aug 4 c206; zero Exiles errors]: **PASS on everything that ran; the S1 reload check did not run**
- **Clean load:** no yellow DEBUG lines (Sean to confirm on screen; none can appear in the log),
  no `NEX TASK GROUP` lines, no Exiles errors or warnings. No `WAR SWITCH` line, correctly, on a
  new game.
- **Pacing A, the headline:** rung 1 launched day 0 (LaunchNow), landed Mar 22 in TENSION →
  `tension 24.4 -> 50.0`, `INCIDENT: midan_strike`, `-> 3 RETALIATION`, `became the INCIDENT`. At
  the exit, Apr 11: **`RETALIATION exit: CEASEFIRE TALKS (Midan incident -- contained roll
  bypassed) | tension 50.0 (warning 45.0, hawk 60.0)`** → `ceasefire intel: convened (reason
  pre-emptive, from the warning band)` → `-> 5 POLITICAL INTERVENTION`. The old 70% "formal
  protest" ending is gone for Midan incidents.
- **Chain reaction under the pins (expected):** rung 2 landed during the talks → `the talks
  COLLAPSED` (−2, +4, floor 65) → `ceasefire intel: outcome CEASEFIRE COLLAPSED` → DE-ESCALATION.
  Rung 3 landed May 8 during DE-ESCALATION → **S2 path exercised:** `-> 3 RETALIATION (… cycle 1)`
  with no fake `-> 1 TENSION` line between, tension to the 88 floor; exit May 28 = ESCALATION (at
  or above hawk; the bypass does not apply). Two Hegemony ops completed afterwards (+2 each). The
  Mazalot directive was written for the League's relief convoy.
- **S1 reload check — NOT RUN.** Sean saved on Apr 19 with rung 3 in flight (correctly timed), but
  all three saves went to the same slot and the Aug 3 save overwrote it; the post-restart load was
  the Aug 3 save, ladder complete, nothing to land. The check is a regression check only (the
  one-tick window cannot be aimed at); S1 itself stands verified by code reading. To run it: new
  game, save right after rung 1's `DISTRESS CALL`, reload, expect `STRIKE LANDED` and `LANDING`
  with no `LANDING SKIPPED`.
- **S1 reload check — RUN 2026-11-08 (Annie save, new game): PASS as far as it went.** Rung 1
  launched day 0, `DISTRESS CALL` Mar 13, saved Mar 14 with the strike in flight (right), game
  restarted, save loaded, `STRIKE LANDED: rung 1 Citadel Arcadia ... player in range` Mar 21 seven
  days after the load, relief recorded, directive written, stand-down and return leg Mar 25; Sean
  saw the station fall. He saved to a fresh slot on Mar 25 and quit before the `STRIKE OUTCOME` /
  `LANDING` pair (due a few days later when the op ends), so the cycle entry after a reload is not
  in this log; it is the same code the first smoke test exercised and no `LANDING SKIPPED` can
  fire until then. Loading the Mar 25 save and playing three days would close it; not required.
  **CLOSED the same evening:** Sean reloaded the Mar 25 save and ran seven days: `STRIKE OUTCOME`
  → `RUNG RESOLVED` → `tension 24.26 -> 50.0` → `INCIDENT: midan_strike` → `-> 3 RETALIATION` →
  `LANDING: rung 1 LANDED | TENSION -> RETALIATION | became the INCIDENT`, then the Hegemony's
  `RETALIATION roll: STRIKE` and a surgical strike launched on Fikenhild. No `LANDING SKIPPED`.
  **S1 regression check PASS.** The smoke test is complete.

## 9. Not covered
The Black Fleet ambush internals beyond its lifecycle (BF director's review), the four market
conditions beyond confirming they are event-driven with timers, the stage internals beyond their
tick gating, and the Pass-8 dialogue (built in isolation; its live run is its own pass). The
duplication census numbers are the agent's measurement, spot-checked on two rows (`hasLiveOpIn`
byte-identical ×4; Ceasefire/Engagement 27 differing lines of ~77).
