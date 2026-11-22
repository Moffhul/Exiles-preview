# Heg–League War: Fleet-Behaviour Review — RECOMMENDATIONS ONLY

> **STATUS: PROPOSED — NOTHING IN THIS FILE IS RATIFIED. (2026-09-24)**
>
> This is a recommendations register, kept deliberately SEPARATE from the canonical spec
> `docs/EXILES_HEG_LEAGUE_WAR.md` so proposals and decisions don't get mixed up. Sean is
> considering these and has not ruled on any of them.
>
> **Rules for any session reading this file:**
> - Do NOT implement anything here without an explicit ruling from Sean in chat.
> - Do NOT copy these items into `EXILES_HEG_LEAGUE_WAR.md` as canon, and do not cite them as
>   decisions. When Sean rules, record the ruling in the canonical doc (with date) and mark the
>   item here `RULED → see canonical §x` (or `DECLINED`). Leave the original text alone.
> - All items below are **assistant-originated** (Claude, 2026-09-24 review session). R1–R6 come from the
>   fleet-behaviour review; R7 (Nexerelin Special Forces) was added later the same session from a read of
>   Nex's source.
>
> **Provenance:** a code review of every Heg-League file that issues fleet orders, checked against
> the engine behaviour in `docs/STARSECTOR_VANILLA_FLEET_BEHAVIOR.md` (decompiled
> `AssignmentModule`/`StrategicModule`/`TacticalModule`) and the vanilla API sources. The findings
> are **code-reading predictions**. None has been confirmed in a log or on screen unless the item
> says so. Line numbers are as of commit `0a47dd6`.
>
> Paths: `EX/` = `data/scripts/exiles/`; `IMPL/` = vanilla
> `starsector-core/starfarer.api/com/fs/starfarer/api/impl/campaign/`.

---

## Part A — What each operation's fleets are ordered to do (reference)

This part is descriptive, not a proposal. It records how the current code maps to engine orders.

### A.0 Shared RaidIntel lifecycle (interdiction, squeeze, strike)

Built in `EX/ExileHegLeagueInterdictionManager.java:658-676` (the squeeze and strike managers build it
the same way). The route AI is vanilla `IMPL/intel/raid/RaidAssignmentAI` (a `RouteFleetAssignmentAI`).

| Phase | Engine order | Notes |
|---|---|---|
| Prep (assemble) | `ORBIT_PASSIVE` on the source market | won't engage |
| Gather (assemble) | `GO_TO_LOCATION` → `ORBIT_AGGRESSIVE` on the source system's nearest jump point | `$core_fleetBusy` reason `raid_wait` |
| Travel | `GO_TO_LOCATION` to a target-system jump point, then `ORBIT_AGGRESSIVE` on it | busy flag set; **no `$doNotGetSidetracked`**, so fleets may engage en route |
| Action | per op, see below | |
| Return | `GO_TO_LOCATION` home → `ORBIT_PASSIVE` → `GO_TO_LOCATION_AND_DESPAWN` | failures send stragglers home |

Fleet flags (`EX/ExileHegLeagueRaidIntel.java:359-372`): `$isWarFleet`, `$isRaider`, tag
`exile_hl_raider`, `MAKE_HOLD_VS_STRONGER` (player-only, correctly documented in the code), and `$isPirate`
only for pirate-behaviour factions.

### A.1 Interdiction — `EX/ExileHegLeagueInterdictionStage.java`
- **Order:** one route segment at the system center for the presence window plus 5 days (`:147-157`), which becomes
  **`PATROL_SYSTEM` on the system center**.
- **Engine reading:** each fleet picks one patrol goal and circles it for ~30 days (effectively the
  whole window). Goal weights: jump points 2 each, comm relays ~2, planets not hostile to the fleet
  0.25 each (including uncolonized ones), own markets and own-market pairs (normally none). Victim markets can't be picked (they're hostile).
- No defended zone (the target is the system center). As `$isWarFleet` without `$isPatrol`, the fleets
  give up a chase of an equal-speed or faster target after ~1.5 days + 0.1/burn level.
- `canRaid` is always false (`:122`), so no colony actions. Station `doNotAttack` is refreshed every tick (`:235-260`).
- `loiterOnFocusMarket=true` (off by default) → `ORBIT_AGGRESSIVE` inside the station's orbit band.

### A.2 Squeeze — `EX/ExileHegLeagueSqueezeStage.java`
- **Order:** segment at the anchor planet (`:118-123`) → **`ORBIT_AGGRESSIVE` on the anchor**.
- **Engine reading:** all fleets share a tight orbit (fleet radius + planet radius + 100–200 units), about
  where the station sits. They will fight the station (matches the 2026-09-20 ruling) and engage
  hostiles that approach. Station losses count toward the 50% losses abort.

### A.3 Strike — `EX/ExileHegLeagueStrikeStage.java`
- **Accepted on arrival** (vanilla `PirateRaidActionStage.updateRoutes` finds the target beatable):
  - a system-center segment → `PATROL_SYSTEM`, plus vanilla's two response scripts on the target world:
    - `raid_<id>` recruits every same-faction `$isWarFleet`/`$isPatrol` fleet in the system;
    - `defRaid_<id>` recruits every defender.
  - Response order: `GO_TO_LOCATION` (if >2000 away) → `PATROL_SYSTEM` on the planet (defended zone).
  - The strike itself goes through `BaseAssignmentAI.checkColonyAction`: HOLD on a marker, then `performRaid`. It is
    blocked while any live hostile station, or a hostile fleet stronger than 25% of the striker's FP,
    is within 2000 units.
  - After landing (`:326-338`): `ORBIT_AGGRESSIVE` over the struck world for `orbitDays` (3) + 2 days.
- **Rejected on arrival:** see R3.

### A.4 System blockade — `EX/ExileHegLeagueBlockade.java` (vanilla `BlockadeFGI`)
- **Prep and travel:** vanilla fleet-group actions. The group moves together with `$doNotGetSidetracked`
  refreshed.
- **Hold:** `SpreadBlockadeAction` (`:207-243`) creates one `MilitaryResponseScript` per jump point,
  each at weight 1.0 with its own reason key → `GO_TO_LOCATION` (if far) + `PATROL_SYSTEM` on the gate
  (3 days, re-issued; defended zone applies).
- **Pickets** (`:260-277`) run `NPCHassler`, which inspects non-hostile, non-same-faction traders (Independent
  shipping). Victim traders are hostile, so the pickets fight them instead.
- Station `doNotAttack` (`:441-467`); 50% damage abort; `makeFleetsHostile=false` (load-bearing,
  `EX/ExileHegLeagueBlockadeManager.java:500`).
- Config: `blockadeFleetSizes: [7, 7]` → **two fleets**.

### A.5 Relief convoy — `EX/ExileHegLeagueRecoveryManager.java`
- `GO_TO_LOCATION` (`:448`) with `$isTradeFleet` (`:520`): the convoy never engages and never joins battles.
- Within 400 units the manager switches to `ORBIT_PASSIVE` while unloading (`:614-620`), then
  `GO_TO_LOCATION_AND_DESPAWN` to a jump point (`:698-718`).
- `MAKE_ALLOW_DISENGAGE` (`:523`) is player-only, as its comment says.

---

## Part B — Recommendations

Each item carries a **Decision** line that stays `PENDING` until Sean rules in chat.

### R1 — Unplanned objective captures by op fleets  *(priority: high; confidence: code-read, unobserved)*

**Finding.**
- **Live raid fleets:** `RaidAssignmentAI.advance()` calls `checkCapture()` on every live
  interdiction, squeeze and strike fleet. When the player is in spawn range, a fleet
  captures a hostile comm relay, nav buoy or sensor array within 2000 units. The main exception is
  when a hostile fleet stronger than 25% of its FP is within 2000 units of the objective
  (`IMPL/procgen/themes/BaseAssignmentAI.java` `checkObjectiveAction`).
- **All four combat ops:** vanilla `WarSimScript` counts **route** fleets, so any op (blockade
  included) can make its faction the strongest in the system (`wantsToOwnObjective`).
  - On-screen, that faction then gets objective-capture response scripts (weight 0.4, 20–40 days). These can recruit
    op fleets that aren't marked busy, including blockade fleets.
  - Off-screen, the simulation can hand the objective over directly.
- **Nothing in Exiles controls or logs this.** There is no `WarSimScript.setNoFightingForObjective` call
  anywhere in `data/scripts`.

**Why it matters.** Captures outlive the op. The victim can lose its comm relay's stability bonus
(and nav/sensor bonuses) for good, and that loss sits outside the Emergency Measures / deciv guard rails. Pulling fleets
toward objectives is also a second cause of R2.

**Options.**
- **(a)** Accept it as war flavour and document it.
- **(b)** Suppress it: call `WarSimScript.setNoFightingForObjective(obj, actorFaction, timeout)` on each
  objective in the target system when the op starts, refreshed for the op's life. The live-fleet
  `checkCapture` path needs its own gate, which is harder because it lives in vanilla `RaidAssignmentAI`.
- **(c)** Allow it only for certain op types (e.g. strikes, not interdictions).

**Recommended first step (no behaviour change):** instrument before tuning. Log objective
ownership changes in any system with a live Heg-League op, then read one run before choosing.

**Decision:** PENDING

### R2 — Blockade gates left empty (explains the open Q55 question)  *(priority: high; confidence: code-read, consistent with Q55)*

**Finding.**
- Each gate's response script has its own reason key and weight 1.0, so every gate asks for
  **100%** of the faction's in-system fleet points. A fleet can serve only one response at a time.
- Response flags last 0.3–0.5 days. When one lapses, the fleet goes to whichever gate's script ticks first while
  that fleet is its closest free fleet (`IMPL/MilitaryResponseScript.java`
  `seeIfFleetShouldRespond` / `respond`).
- With **two** fleets (`[7, 7]`) against a system with three or more gates, the code predicts fleets moving back and forth between
  gates instead of holding them.
- Other things pull fleets off the gates:
  - hassling pickets are marked busy and can't be recruited;
  - the gate's defended zone causes long chases;
  - R1's objective responses compete for the same fleets.
- Q55 (see `EXILES_HEG_LEAGUE_WAR.md`) observed no fleets parked on the gates.

**Options.**
- **(a) Pin pickets to gates**: each picket gets a fixed gate, re-issued on the existing periodic
  tick (the vanilla `PerseanLeagueBlockade` supply-fleet pattern: clear, busy flag with a reason,
  short `$doNotGetSidetracked`, orbit the assigned point). Only the anchor fleet responds to threats.
  This answers Sean's open Q55 question with "pin".
- **(b)** Keep response scripts but make the number of fleets at least the number of jump points in each whitelisted system.
- **(c)** Accept that holding every gate is only the resting state, as Q55 currently frames it.

**Recommendation:** (a), with (b) as the minimum if (a) is declined. Before either, count the jump
points in each whitelisted blockade system.

**Decision:** PENDING

### R3 — On-screen strikes that vanilla rejects on arrival  *(priority: medium; confidence: code-read)*

**Finding.**
- When vanilla's arrival check finds no beatable target, no response script exists. The group falls back
  to `PATROL_SYSTEM` goals that never include the enemy target, so it rarely comes within the
  2000-unit raid range.
- At window end with the player in spawn range, `updateStatus` fails the op **without asking
  `canBeat` again** (`EX/ExileHegLeagueStrikeStage.java:492-498`). Only the off-screen autoresolve
  asks again.
- The class comment at `:150-153` ("`canBeat` is asked again at the end") is therefore true
  off-screen only. The 2026-09 run (13/14 landed) was entirely off-screen.

**Options.**
- **(a)** When vanilla rejects, stage the group on a marker 2500–3000 units from the target (outside the station) so it can go
  in if the defenders disperse.
- **(b)** Ask `canBeat` again at window end in both paths.
- **(c)** Correct the comment only.

**Recommendation:** (a) + (b). At minimum, (c).

**Decision:** PENDING

### R4 — Interdiction covers random points, not shipping lanes  *(priority: medium; a question of feel for Sean)*

**Finding.** `PATROL_SYSTEM` on the system center scatters fleets over jump points, relays and
empty planets (A.1). Nothing specifically covers the lanes the victim's shipping uses, which is what the
fiction ("stopping shipping") describes.

**Option.** In the existing `updateRoutes()` override, give each route its own approach point:
the system's jump points, plus a point on each victim market → jump point lane. A non-center point
becomes `ORBIT_AGGRESSIVE` there. No custom AI is needed. Keep the station suppression unchanged.

**Decision:** PENDING

### R5 — Raid ops can be sidetracked while travelling  *(priority: low; confidence: code-read)*

**Finding.** Blockades travel with `$doNotGetSidetracked`; the three RaidIntel ops don't.
`FLEET_BUSY` blocks response recruitment but not engagement, so fights on the way can wear a
group below its losses-abort line before it arrives.

**Recommended first step:** a single log line naming the stage in which an op aborted. Consider
setting `$doNotGetSidetracked` during travel only if the log shows aborts before arrival.

**Decision:** PENDING

### R6 — Relief convoys vanish in place  *(priority: low; cosmetic)*

**Finding.** On "target gone" (`EX/ExileHegLeagueRecoveryManager.java:548-552`) and "travel timed
out" (`:582-588`) the convoy is `despawn()`ed wherever it is, possibly in front of the player.

**Option.** Use `Misc.giveStandardReturnToSourceAssignments(fleet)` (or despawn only when
`!fleet.isVisibleToPlayerFleet()`), keeping the op record's existing outcome report.

**Decision:** PENDING

### R7 — Nexerelin Special Forces ("task forces") drawn onto every op  *(priority: high; confidence: read from Nex source, spawn counts from one log)*

**Source read.** Nexerelin `jars/ExerelinCore.jar` (it ships `.java` sources):
`exerelin/campaign/intel/specialforces/SpecialForcesManager.java`, `SpecialForcesRouteAI.java`,
`SpecialForcesIntel.java`. In Nex's UI these are "special task groups": named fleets such as "6th Raesvelg", about
150 FP (`POINTS_TO_SPAWN 250 × SIZE_MULT 0.6`), with a persistent commander. They rebuild after ~40% losses, and
the event ends at 90% losses.

**Finding: Exiles doesn't spawn them. It draws them in.**
- **Spawning is independent of Exiles.** Each faction gains points daily from its markets' commodity value
  (`FleetPoolManager.getMarketCommodityValueStatic × 0.1 × specialForcesPointMult`, capped at 300) and
  spawns a task group at 250 points, up to that faction's `specialForcesMaxFleets`. A destroyed task group puts that
  faction on a 60-day respawn delay. Exiles' only effect on spawning is indirect: its ops kill task groups, so they get replaced.
  Log check (2026-09-24): the one log from a Nex run shows 8 spawns (Hegemony 3; Tri-Tachyon, Diktat, League,
  Church, Independents 1 each). That fits normal caps plus replacements, not a spawn flood.
- **They see every op, hidden or not.** `SpecialForcesRouteAI.getActiveEvents()` scans the intel
  manager for every `RaidIntel` and `FleetGroupIntel` and **does not check `isHidden()`**. Every 3–5
  days (`recheckTaskInterval`), a task group defends against an event when all of these hold:
  - the event's faction is hostile to it;
  - its faction has a market in the target system (`isDefendableEnemyEvent`, the "pirate raid" branch);
  - the ETA is 60 days or less (`MAX_RAID_ETA_TO_CARE`).
  
  So task groups commit at the op's **launch**, weeks before the player can see it.
- **Heg–League targets almost always count as urgent.** Priority is the sum over allied target markets of size²
  (×4 with heavy industry). The urgent-defence threshold is `8 × doctrine aggression`, doubled if the task group is already busy. A single size-6
  market (36) clears it for any faction below aggression 5, so the task group drops its current task.
- **They also assist their own side.** `isAssistableFriendlyEvent` sends task groups to help same-side ops,
  so a Hegemony task group can escort a Hegemony strike. Both sides converging, plus a ~150 FP task group meeting
  45–90 FP op fleets, explains the observed "the Nex task force wiped out the aggressor".
- Not the cause: `ResponseFleetManager` (a reserve fleet for player-initiated market battles only) and
  `DefenseFleetIntel` (requested by Nex's own offensives).

**Options (vanilla levers first):**
- **(a) Withdraw when a task group arrives, and show it (workaround).**
  - Detection: a hostile fleet with the memory flag
    `$nex_specialforces_npc` enters the op's system. A plain string check, no `exerelin` class reference, so it stays clean
    without Nex (CLAUDE.md Nex rule).
  - Withdrawal, using vanilla behaviour: end the presence early,
    `Misc.giveStandardReturnToSourceAssignments`, `$doNotGetSidetracked` on the way out, and an optional
    `emergency_burn` activate.
  - Payoff: an off-screen wipe becomes a readable beat ("withdrew as the Hegemony's 3rd
    task group arrived") and the fleets survive. Optionally report it to the tension meter as an escalation event
    (see `EXILES_HEG_LEAGUE_WAR_DESIGN_NOTES.md` §3.5: a limited op meeting an unlimited response *is* the event).
- **(b) Fight where they won't come (avoidance through target choice).** Task groups only defend systems where their faction has
  a market. The design notes' "fight over the border" idea (objectives in disputed, marketless systems;
  design notes §5.3, R1) would be completely invisible to task groups.
- **(c) Register ops later (delay).**
  - Vanilla `RaidIntel` runs as its own script (`Global.getSector().addScript(this)` in its
    constructor) and does not need the intel manager. Heg–League ops are already hidden until arrival.
  - Adding them to the intel manager at reveal instead of launch means task groups learn at arrival and still need
    travel time.
  - **Costs:** Exiles' own overlap guards (`ExileLiveOps`, the managers' `getIntel(...)` scans) read the
    intel manager and would need their own registry of live ops. Whether `BlockadeFGI` runs unregistered is unverified. It also
    deliberately steps around Nex's intended behaviour, so it is a compatibility decision.
  - **Cleaner alternative:** suggest to the
    Nex author that task groups respect `isHidden()`, which fixes it for every mod.
- **(d) Config levers (last resort).** Nex per-faction `specialForcesMaxFleets` /
  `specialForcesPointMult`, or the global `specialForcesPointMult` in `exerelin_config.json`. Changing
  them for the Hegemony and League overrides Nex's balance for vanilla factions, so under the Compatibility and
  Setup-transparency priorities it should only be a documented, opt-in player option, never a default.
- **Not recommended:** in-system evasion (`doNotAttack` on the task group; it hunts the op anyway), and
  `$cfai_ignoredByOtherFleets` (makes op fleets untouchable by everyone, not just Nex).

**Recommendation:** (a) now, (b) when designing border stakes, (c) only as a conscious compatibility
decision (and prefer the upstream suggestion to the Nex author).

**Decision:** PENDING

- **2026-10-04, MEASUREMENT BUILT (Sean: "build the detector first"), log-only, no behaviour change.**
  `ExileHegLeagueNexTaskGroupWatch`, driven from the director's tick, held in a transient field (no
  script, nothing saved), inert without Nex. It finds task groups by their **route** source
  `nex_specialForces` (verified in Nex `SpecialForcesIntel.SOURCE_ID`), so it sees off-screen
  task groups as well as spawned ones. Log lines: `NEX TASK GROUP ENTERED <system>` / `LEFT … after Nd`
  (task group name or origin, FP, whether it is HOSTILE to the op or on the op's own side, and the op's
  stage), plus one `NEX TASK GROUP SUMMARY` per op with its outcome and every task group it met.
  Config: `logNexTaskGroups` (true), `nexTaskGroupScanDays` (1). Janino-clean 3/3; first live read
  is the 2026-10 Nex comparison run. The metric for the ruling: share of ops that meet a hostile
  task group, and the outcome split with and without one.
- **2026-10-04, CORRECTION (re-read of the installed Nex `SpecialForcesRouteAI`): hostile task groups
  do NOT defend against our system blockades.** `isDefendableEnemyEvent` sets the target system only
  for `RaidIntel`; for a `FleetGroupIntel` (our `ExileHegLeagueBlockade` is a `BlockadeFGI`) `sys` stays
  null, and the fallback test `hasMarketInSystem(null, …)` returns false. So the "drawn onto every op"
  finding above holds for interdictions, strikes and squeezes (all `RaidIntel`), not blockades. The
  ASSIST path is different: `isAssistableFriendlyEvent` has no system test and `areFactionsAllied`
  counts a faction as its own ally, so the op's OWN side's task groups do come to help, blockades
  included. The war doc's §14.15 line "Nex task groups are drawn to our blockades and squeezes" is
  wrong for blockades by the same reading. Code-read only; the detector will confirm or refute it.
- **2026-10-04, Sean on option (b): problematic.** There are no real border regions in Starsector:
  each faction's military presence lives in its own systems, hyperspace pursuit is brief (patrols
  return quickly), and the few uninhabited systems are patrolled by nobody. A fight "over the border"
  would therefore be over nothing either side defends, with no stakes unless they were invented.
  Claude's revised rating: (b) drops from "7 long-term" to low; keep it only if a future design
  creates something worth contesting outside the factions' own systems. Ratings of the other options
  (and Claude's proposals e–j) are recorded in the 2026-10-04 session; discuss after the Nex run.

### R8 — Launch gates compare fleet points with effective strength  *(priority: high; confidence: confirmed in code, factor measured)*

Full proposal, scope table (all four directors) and decisions: **`EXILES_HEG_LEAGUE_WAR.md` §14.14**
(written there at Sean's request, 2026-09-26, and flagged PROPOSAL). Summary: gates use
`fp × feasibilityMult` against `WarSimScript` strength. Vanilla converts with
`Misc.getAdjustedStrength(fp, source)` (≈ ×3.4 for Heg–League sources), so Heg–League strikes and squeezes
effectively demand ≈ 3.4 : 1 and interdictions ≈ 1.7 : 1. Couple the odds decision with R7.

**Decision:** PENDING (see §14.14.6)

---

## Part C — Checked and fine (no action proposed)

- `makeFleetsHostile=false` on blockades is correct and load-bearing.
- `MAKE_HOLD_VS_STRONGER` / `MAKE_ALLOW_DISENGAGE` are player-only, and the code comments say so.
- Station suppression via `doNotAttack` is refreshed per tick (entries expire), for interdiction and
  blockade. The squeeze deliberately opts out per the 2026-09-20 ruling.
- Relief convoys' `$isTradeFleet` already gives non-combat travel and never joins battles.
- The strike's `updateRoutes()` override that keeps its own window follows the CLAUDE.md raid-stage gotcha.

---

## Evidence log (appended; decisions unchanged)

- **2026-09-28, R1: OBSERVED ON SCREEN.** Objective captures by op factions in Galatia, Westernesse,
  Corvus (Black Fleet), Arcadia, Valhalla, Naraka and Tyle (war doc §14.14.10). The unplanned
  mechanism is real and sector-wide. Decision still PENDING: suppress, or adopt as the "border
  stakes" feature (design notes §5.3).
- **2026-09-28, R3: MECHANISM CONFIRMED, no Nex.** Yama → Laicaille strike, 1723 vs 281 on arrival,
  FAILED because the player was in spawn range when the window ran out (`finish(false)` with no
  strength check). The same signature as the Nex run's Olinadu failure, so that one was probably not Nex either. Fix proposed in
  war doc §14.14.10. Decision PENDING.
- **2026-09-28, R5: first concrete case.** Raesvelg → Kumari Kandam interdiction died 9 days after
  launch, before arrival, with fleets live in the source system. Cause not provable without the proposed
  abort-stage log line.
- **2026-09-28, new legibility items (no R number yet):** Heg–League op intel is titled "System
  Bounty – X", and an op reads "Over" while its fleets are still in the system on the return leg.
- **2026-09-28, R1: RULED → see canonical §14.14.11. DECLINED (keep vanilla behaviour).** Sean:
  objective captures by aggressive fleets are identical to vanilla raiding and desirable; do not suppress.
- **2026-09-28, R3: RULED → see canonical §14.14.11.** Window-end fix built (strike stage only).
- **2026-09-28, R5: RULED → see canonical §14.14.11.** Abort-stage line built. (`$doNotGetSidetracked`
  during travel still not built; decide from the line's readings.)
- **2026-09-28, END-OF-PHASE-1 ITEM (Sean's ruling: defer the change, make it once at the end of
  Phase 1, before any release). Bounty intel presentation, all directors:**
  1. List each system bounty under the **paying** faction's intel tab, as vanilla `SystemBountyIntel`
     does (`tags.add(<payer>.getId())`, UI colours from the payer). Today the Heg–League, Ashen,
     Black Fleet and Omicron raid intels inherit `RaidIntel.getIntelTags`, which tags the
     **aggressor**. Options were (a) payer only (vanilla) or (b) payer + aggressor. Choose at the fix.
  2. Swap the aggressor crest for vanilla's generic bounty icon (`"system_bounty"` →
     `graphics/icons/intel/bounties2.png`). Sean keeps the crests until then as a tracking aid.
  3. The Heg–League and Ashen blockades pay per-kill bounties but carry no `Tags.INTEL_BOUNTY`, so
     they never appear under Bounties. Add it (see also the proposed blockade victory bonus).
  4. Wording: "System Bounty – X" titles and "Over" shown during the return leg (§14.14.10). Revisit together.
     **Evidence 2026-10-04 (May c208 → Jan c209 run):** Sean reported "three Persean blockades
     suddenly announced they were over at the same time, but no blockade was ever announced". They
     were the League squeezes on Yama, Jangala and Raesvelg. Each posted on arrival as "System
     Bounty – <system> … Arrived in-system" (`ExileHegLeagueRaidIntel.getName`), and the word
     "blockade" first appeared in the ENDING message, "The blockade in the X has ended"
     (`ExileHegLeagueSqueezeIntel.getOpEndedText`). So one operation starts as a bounty and ends as
     a blockade, and the player never sees it named as a blockade while it is active. They ended
     together because their arrivals bunched (Jul 3, Jul 3, Jul 5) and every squeeze holds 32 days.
     Include the end-text strings (interdiction's "The interdiction of the X has ended" too) in the
     same wording pass, so the title, updates and ending all name the operation consistently.
  Presentation only, save-safe (tags are computed at draw time). One pass, one test.

### R9 — "Break the Blockade" victory bonus (Sean's idea; proposal written 2026-09-28)

Full proposal: **`EXILES_HEG_LEAGUE_WAR.md` §14.15**. A contribution-based second-tier reward, paid by
the victim, when the player helps drive off a system blockade or planetary squeeze. Sean ruled:
contribution-based, squeezes included, explore relief fleets and Nex, and intel presentation as proposed.
Claude recommended the payout sizes and the transponder rule; those are unratified.

**Decision:** PENDING (VB-D1 to VB-D4)

- **2026-09-28, R9: RULED → see canonical §14.15.11.** Transponder rule dropped (pay either way);
  VB-D1 to VB-D4 as recommended. Not built yet.
- **2026-09-28, R9: BUILT (mechanics) → canonical §14.15.12.** Untested in game; presentation waits for the end-of-Phase-1 pass.
