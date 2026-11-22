# Hegemony–Persean League War Director — Design Spec

> **STATUS 2026-11-08: COMPLEXITY REVIEW DONE AND BUILT (docs/EXILES_HEG_LEAGUE_COMPLEXITY_REVIEW.md).**
> Stable, cheap at runtime; two Midan landing-path defects fixed; ~560 lines and four inert config
> keys removed; the Nex task-group watch retired; debugMessages ships false; pacing ruling A: a
> Midan incident never ends CONTAINED. Smoke-tested in play the same day (review doc §10). **Next:
> Pass 8 — the player-interception dialogue (§4.5), which is designed and ruled but NOT YET BUILT
> (a 2026-11-08 session note calling it "built and tested in isolation" was wrong; checklist and
> estimate under §4.5) — then Pass 9.** Also pending Sean's rulings: the **messaging pass** (§3.4.1:
> one war icon, the telegraph down to one feed message, bounties under the payer, beats to one list,
> Midan strikes to Major events, in-system gating of bare messages) — proposed 2026-11-08, nothing built.
>
> **STATUS 2026-09-21: PASS 4 BLOCKADES LIVE-TESTED under `forceState: 4` — three blockades launched,
> held and completed on both sides, zero exceptions, cooldown and cadence observed (§14.12). On-screen
> checks (gates, condition split, bounty, station safety) still need Sean's read. The forced
> SQUEEZE run was a NULL RESULT: the RETALIATION pin never sets the offender, so `forceIncidentOffender`
> is dead under it; a testing-aid seed is proposed in §14.12. Passes 4–6 otherwise stand as below.
>
> **STATUS 2026-09-13: CONTAINMENT AMENDMENT RATIFIED + BUILT (Pass 3.5, §3.2.5, §14.5).**
> Sean ruled on all four open points: the RETALIATION exit is now a three-way branch (contained /
> pre-emptive ceasefire roll / escalation), war weariness decays, the crisis-ending mechanism is a
> **temporary ceasefire** (never a peace summit — that is Phase-2 envoy-quest territory), the
> containment beat is the incident telegraph's closing update, and recovery convoys become
> **strike-triggered** (§4.6 amended; still Pass 6 to build). Janino-clean, config re-parsed,
> **UNTESTED IN GAME** — Q37–Q41 in §14.5. Passes 4–9 remain unbuilt.
>
> **STATUS 2026-09-01: PASS 3 EXTENDED-RUN CONFIRMED.** Natural-time play produced two
> successful League interdictions, a Hegemony violent-inspection incident, the corresponding League
> RETALIATION strike, a full tier-3 landing at Nachiketa, and the summit/cycle reset with no Exiles
> runtime errors. The concrete record is in §14.4 under "Extended-run confirmation". This validates
> the ordinary Pass-3 loop; the broader Q-matrix, organic ESCALATION arithmetic, and later passes
> remain open.
>
> **STATUS 2026-08-27 (superseded build status): PASS 3 BUILT — surgical strikes on military industries, the
> weighted INCIDENT table with its rumour-then-confirmation telegraph intel, and the RETALIATION
> branch actually launching the victim's punitive op. Janino-clean (12/12), config re-parsed,
> **UNTESTED IN GAME**. Build record, deviations, a 10-minute smoke test and the Q1–Q36 matrix:
> **§14.4**. Three things to know before testing: **the §14.3 "ESCALATION never fires" item is now
> measured and reported, not fixed** — at the ratified values the meter nets ≈ −12.75 per cycle and
> pins at the floor, so reaching `hawkThreshold` needs an author tuning decision (§14.4 "THE
> ARITHMETIC"); `surgicalStrike.extendedDisruptMult` ships at **1.0** and must stay there until
> Pass 6's recovery convoys exist; and every strike FP number is a guess shipping with a
> measurement log line. Passes 4–9 remain unbuilt; blockade plumbing was not touched and still
> waits on the author whitelist.
>
> **STATUS 2026-08-27 (late): PASS 2 BUILT — interdiction raids, the `exile_hl_interdiction`
> condition, the per-kill bounty, and the TENSION/ESCALATION wiring that makes the crisis cycle
> launch fleets. Janino-clean (7/7), config re-parsed, **UNTESTED IN GAME**. Build record,
> deviations and the P1–P19 first-boot matrix: **§14.3**. Three things to know before testing:
> the shipped `testing.tensionOverride: 1` pins the meter at the floor and blocks every
> ESCALATION test; `timeScale: 0.1` now compresses presence windows but not fleet travel; and
> **every FP number ships as a guess with a measurement log line** — tune from it. Passes 3–9
> remain unbuilt; blockade plumbing was not touched and still waits on the author whitelist.
>
> **STATUS 2026-08-27 (evening): PASS 1 LIVE-TESTED — D2/D3/D4 PASSED, B3 self-heal proven
> organically.** Boots clean with and without Nexerelin; premise stack verified both ways
> (pin off = control, pin on = spec). Test record: **§14.2**. Outstanding from the checklists:
> the timeScale cycle walk, forceState/forceIncidentOffender knobs, and the long-run meter
> bounds (§14.1 vanilla items 5, 6, 8). Passes 2–9 remain unbuilt.
>
> **STATUS 2026-08-27: PASS 1 BUILT, Janino-clean, UNTESTED IN GAME.** The skeleton (director,
> tension meter, crisis-cycle state machine, config block, registration, and the switch-gated
> Nexerelin B2+B3+conquest-shield stack) is in the tree and launches nothing. Build record,
> deviations and the pending live-test checklist: **§14.1**. Passes 2–9 remain unbuilt.
>
> **STATUS 2026-08-26 (end of rulings session): SPEC RATIFIED — NO CODE YET.** All twelve
> rulings R1–R12 were taken by Sean on 2026-08-26 (§10). The Bilateral Crisis Engine (§3.2) is
> **accepted as the working framework** — every ruling was taken on it; numeric values remain
> tuning, and Sean may still flag reservations on the framework itself. Outstanding: the author
> deliverables listed at the end of §11 (target lists + player-facing text) and one minor nod
> (Relief Effort condition). **Pass 1 is unblocked.** Reuse and Nexerelin research below is
> **verified by code** where tagged; everything else needs a live test.
> This is the **fourth sibling** director (settled 2026-08-24, CLAUDE.md §6): a peer of the Ashen,
> Black Fleet, and Omicron directors on the vanilla `RaidIntel` pipeline — shared vocabulary, not
> shared control flow. House style follows `EXILES_ASHEN_CRUSADE_WAR.md`: §1–§13 is the spec,
> edited in place as rulings land; §14 is the append-only build log.

---

## 1. Mandate and fiction

### 1.1 Ratified background [RATIFIED — Sean, 2026-08-23]

The two-theft nanoforge chain, both thefts REAL:

1. Midan orchestrates the Nachiketa raid and, in the confusion, steals the **Hegemony's**
   Pristine Nanoforge **using ships that appear to be Persean League**.
2. The Hegemony, believing the League now holds two forges, raids the League and steals theirs —
   a **real theft**; the Hegemony is not merely a deceived victim.
3. The League, the only party that *knows* it didn't commit theft 1, correctly infers a lie and
   misattributes it: it reads Chicomoztoc as a manufactured casus belli and expects a
   forced-Reunification war. Both sides reason soundly to opposite conclusions; Midan need not
   be omniscient.
4. End state: Midan holds the Hegemony's forge (its endgame quality lever, per CLAUDE.md);
   the Hegemony holds the League's (running Pristine); the League holds none (making do with a
   Corrupted Nanoforge acquired afterward).

**Unruled hook:** Midan orchestrated Nachiketa — the event the player was framed for — so the
player's frame-up and the war's founding lie are the same event. (Phase-2 material.)

### 1.2 The war's character [AUTHOR — design doc, 2026-08-24 revision]

- The two militarily strongest Core powers; strategic **peers**, with the Hegemony holding the
  production advantage and both sides aware of it.
- **Neither leadership wants open war.** Warmongers exist on both sides, but top leadership is
  pragmatic: a major war risks destabilizing the Core, conquest by the rival, or leaving both
  vulnerable to the other factions. Soldiers on both sides are frustrated by the limits.
- The "war" is therefore **skirmish-scale**: interdiction raids and blockades against trade and
  commerce; surgical raids at most against markets; **no planetary bombardment** (at least at
  first); economic and force disruption, never conquest or decivilization.
- **Capital systems (Aztlan and Kazeron) are left alone** — which also means neither side's main
  ship production is hit.
- Midan is quietly trying to push both factions into the full war neither wants.

### 1.3 Relations at campaign start [VERIFIED — code]

- Vanilla `CoreLifecyclePluginImpl` (~line 761) **pre-seeds Hegemony–Persean League hostility at
  game start** via the (cosmetic) `FactionHostilityManager`.
- Under Nexerelin, `VanillaSystemsGenerator.initFactionRelationships():165` hardcodes
  `hegemony.setRelationship(PERSEAN, HOSTILE)` in corvus mode, and Nex's shipped faction configs
  already skew this pair toward war (`hegemony.json`: `startRelationships persean:-0.6`,
  `diplomacyPositiveChance persean:0.8`, `diplomacyNegativeChance persean:1.2`,
  `dispositions persean:-10`). **Nex is on our side at t=0; what it lacks is a permanent lock**
  (§7).

### 1.4 Design goals [AUTHOR]

1. **Peers, asymmetric:** Hegemony and League behaviors differ to reflect the Hegemony's
   industrial advantage.
2. **Feel vs. Reality:** the war should *look* like the sector may descend into chaos; quiet
   controls and failsafes prevent it from actually happening.
3. **Ship-farming:** standard skirmishes capped at destroyer size; no easy cruiser/capital
   derelict farming; XIV Battlegroup hulls treated as high-value (see §5 — "rare" vs "absent" is
   an open ruling). Cruisers/capitals live in defensive key-system patrols.
4. **Escalation model:** the director regulates, escalates, AND de-escalates over time, with more
   inertia than the Ashen war clock — a bilateral crisis rhythm of
   Tension → Incident → Retaliation → Escalation → Political Intervention → De-escalation.
5. **Quest input:** the player must eventually be able to affect the war's direction and outcome
   (Phase 2); the director must expose the seams now.
6. **Midan's involvement:** false-flag capability (established: Midan can pose as the League) and
   judicious Black Fleet proxy use — gated by war state or quests, **not** timers.

---

## 2. Strategic geography — targets and exemptions [RULED — Sean, 2026-08-26]

Ownership snapshot (live local econ files, 2026-08-26 — NOTE: this install is modified, and
ownership can flip under Nex; always query live): **Hegemony** — Aztlan (Chicomoztoc 8 capital,
Coatl 5), Samarra (Eventide 7, Sphinx 5, Tigra 4), Valhalla (Ratatosk/Raesvelg/Ragnar 5s),
Naraka (Yama 6, Nachiketa 5), Corvus (Jangala 6), Arcadia (station 4). **League** — Thule
(Kazeron 7 capital), Zagan (Mazalot 7, Yesod 5), Westernesse (Fikenhild 6, Suddene 5, Athulf 4),
Tyle (Madeira 6, Cibola 5), Mayasura (Mairaath 5), Yma (Salamanca 5), Kumarikandam (Olinadu 4),
Isirah (Laicaille 4).

The three-part ruling:

- **R6a — Capital exemption is SYSTEM-level:** all of Aztlan and all of Thule are untargetable
  (Coatl and Thule's minor markets ride along). Exempt systems remain valid op *sources*.
- **R6b — Refugee-site limits + blockade WHITELIST:** the five refugee strain markets (Asharu,
  Mairaath, Port Tse, Salamanca, New Maxios) are never direct victims of any op. Additionally —
  Sean's amendment — **system and planetary blockades target ONLY an author-curated whitelist**
  in config (`blockadeWhitelistSystems`, `planetaryBlockadeWhitelistMarkets`).
  **[AUTHOR — PENDING: Sean is working out the lore-driven whitelist; blockade passes cannot
  finalize targets until it lands.]** The whitelist inherently resolves the Corvus
  refugee-station collateral question (Corvus simply isn't whitelisted unless Sean chooses it).
- **R6c — Geography as config, not code:** interdiction raids, surgical strikes, and pickets
  draw from the pool of all non-exempt enemy markets, **distance-falloff weighted** (the BF
  pattern) so ops concentrate where the powers' spaces meet, with config allow/deny lists for
  author tuning — robust against the pending custom-system placement retune.
- **Never hardcode ownership.** Under Nexerelin, core-world ownership can flip
  (`allowInvadeStartingMarkets` defaults true; `allowInvadeStoryCritical` covers almost nothing at
  start) [VERIFIED — Nex `NexConfig.java:59`]. Target selection must query
  `market.getFactionId()` live every cycle; a market that changes hands silently leaves the
  target pool. If either capital falls to a third party the director's premise survives; if
  Hegemony and League somehow cease to both exist as market-holders, the director idles (see
  §3.6 kill conditions).
- **Overlap with other directors:** consult `ExileLiveOps.getSystemsWithActiveRaids()` to avoid
  dogpiling a system that already hosts a live Ashen/BF/Omicron op; respect
  `ExileUnrestGuard.isBattered()` and `ExileProtectedFactions.isProtected()` in all victim
  picking (refugee enclaves and strain markets are never targets).
- **Collateral is intentional:** system blockades hit Independent and Pirate markets in the same
  system as collateral damage [AUTHOR]. The Battered check still applies per-market.

---

## 3. Director architecture

### 3.1 Fourth sibling [RATIFIED — 2026-08-24, CLAUDE.md §6]

`ExileHegLeagueDirector` is a sibling on the vanilla `RaidIntel` pipeline. It shares the utilities
(`director_config.json`, `ExileUnrestGuard`, `ExileProtectedFactions`, `ExileLiveOps`, the
condition base classes, the Emergency-Measures Battered check) and nothing else. No director knows
another exists. The unified Sector Destabilization Director was tested and **rejected — do not
re-propose** (Ashen doc §14.5/§14.6).

### 3.2 The Bilateral Crisis Engine [ACCEPTED AS WORKING FRAMEWORK — 2026-08-26: all twelve
R-rulings were taken on this model; numeric values are tuning, not canon]

Two layers: a slow meter that carries memory, and a crisis cycle that produces the visible drama.

#### 3.2.1 Layer 1 — the Tension meter

A single persistent score, **0–100**, representing how close both leaderships are to authorizing
wider war. This is where the "large, fractious, bureaucratic" character lives, and it is the
long-term memory across cycles — the state machine forgets, the meter does not.

- **Up:** completed ops (small), incidents (moderate; scaled by incident severity), collateral
  damage to civilians/third parties (larger), Midan interference (§6), talks collapsing at
  Political Intervention.
- **Down:** successful political intervention (large cut), quiet stretches (slow decay), the
  De-escalation state (faster decay).
- **Floor:** tension never decays below a floor (proposed ~15) — the nanoforge grievance is
  permanent. The war can cool; it cannot evaporate. Ending it is quest territory (§11 R2).
- **War weariness (per faction):** cumulative fleet/market losses. Weariness strengthens the
  doves at Political Intervention — a bloody cycle makes stand-down *more* likely at the table.
  This is the central quiet failsafe for the Feel-vs-Reality goal: the hotter the war runs, the
  harder the system pulls back, and the player only ever sees the outcomes.

Persistence: meter, weariness, state, and timestamps are primitives on the director (saved);
all tunables are `transient`, reloaded from config per the established pattern.

#### 3.2.2 Layer 2 — the crisis cycle

Six states matching Sean's rhythm. Each has a minimum dwell time (bureaucratic lag — nothing
happens fast) and characteristic activity:

1. **TENSION** — the ambient war, most of the time. Low-rate interdiction raids and picket
   inspections along the border (§4.1, §4.5); beefed-up defensive patrols in key systems (where
   the cruisers/capitals live — defending, not farmable). Long, variable dwell; exits when an
   Incident fires.
2. **INCIDENT** — a discrete spark from a weighted table, naming an **offender** and a
   **victim**: a raid that hit civilians, a convoy destroyed with survivors testifying, an
   inspection gone violent, a border clash — or a Midan false-flag (§6, gated). Posts as
   rumor-then-confirmation intel (the Ashen Pass-7 telegraph pattern). Instant state, not a
   dwell state; seeds the grievance and adds tension.
3. **RETALIATION** — the victim launches **one** proportionate punitive op against the offender:
   surgical strike on a military industry (§4.3) or a planetary blockade (§4.4), sized by
   tension. At state end, **the three-way branch [AMENDED — Sean, 2026-09-13; §3.2.5]:**
   - tension ≥ `hawkThreshold` → **ESCALATION**;
   - `warningThreshold` ≤ tension < hawk → roll `preemptiveCeasefireChance`: success → **POLITICAL
     INTERVENTION** (the doves get there first); failure → CONTAINED;
   - tension < `warningThreshold` → **CONTAINED**: the border playbook absorbs the incident and the
     cycle returns **directly to TENSION** — no talks, no cut, no DE_ESCALATION. The victim's
     formal protest closes the incident telegraph and that is the last word.
   Most cycles are contained, which is what makes the war read as "skirmishing but contained"
   without ever scripting restraint directly. *(Pre-amendment text: "otherwise skip straight to
   POLITICAL INTERVENTION". That made the ceasefire table fire every cycle and its expected cut
   outweighed a cycle's gains, so the meter pinned at the floor — §14.4 arithmetic, confirmed live.)*
4. **ESCALATION** — both sides surge for a hard-capped window: system blockade(s) (§4.2),
   multiple interdiction ops, counter-strikes. Every guardrail consults here: destroyer skirmish
   cap, exempt systems, unrest guard/Battered, `maxConcurrentOps` per side, live-op dogpile
   check. This is the "sector on fire" state. Hard time limit — bureaucracies can't sustain it.
5. **POLITICAL_INTERVENTION** — the doves move to broker a **temporary ceasefire** [TERMINOLOGY
   RULED — Sean, 2026-09-13: *never* a "summit"; that implies the two leaderships sat down to
   negotiate peace, which is Phase-2 quest territory — see §3.2.5]: a League Assembly session, a
   Hegemony hearing, a back-channel, posted as intel. Outcome roll weighted by tension, both
   sides' war weariness, how the cycle's ops actually went, Midan interference — and **Phase-2
   quest flags reserved as first-class inputs** (this state is *the* player-lever seam). Outcomes:
   **ceasefire holds** (full stand-down, big tension cut), **partial ceasefire** (small cut),
   **ceasefire collapses** (minimal cut; shorter next TENSION dwell). Under the amendment this
   state fires less often and at higher stakes: only from the warning band on a successful roll,
   or always after an ESCALATION.
6. **DE_ESCALATION** — ops stop, conditions lift on schedule, tension decays fast. Then back to
   TENSION. Contained cycles never enter this state. *(Recovery convoys were keyed here until
   2026-09-13; they are now strike-triggered — §4.6.)*

#### 3.2.3 Asymmetry (peers, different shapes)

Same state machine, different per-faction op weights, response lags, and durations — all config,
no forked code:

- **Hegemony** — slow to start, hard to stop. Contributions skew heavy and methodical: system
  blockades, production strikes, longer op durations, longer response lag. Industrial advantage
  expressed as **endurance** (more ops per Escalation window, faster market recovery), never as
  bigger skirmish hulls.
- **League** — quick, light, commerce-focused: interdiction raids, trade disruption, shorter
  lag, shorter ops. It fights the way a trade league would.

#### 3.2.4 The "real shooting war" reconciliation [RATIFIED — Sean, 2026-08-26: A + B]

CLAUDE.md Phase 1 promises Hegemony–League "escalate into a real shooting war"; the design doc
says it stays contained. **Ruling: both, in their own lanes.**

- **A — ambient (director):** a rare emergent **General Engagement** super-state. If ESCALATION
  is reached with tension above a high threshold (or N consecutive hot cycles), the window
  extends: cruiser-led fleets, simultaneous blockades, a named operation in the intel. Hard
  bounded (proposed 60–90 days), capital-exemption still holds, and it **always** ends in a
  forced Political Intervention with elevated stand-down odds — mutual exhaustion; the doves
  seize the crisis. The campaign can visibly hit "real shooting war" without breaking
  containment, and the pullback itself reinforces "intervention came just in time."
- **B — finale (quests):** the true containment-breaking war is quest-gated end-of-chain
  content only (mirroring the Ashen Phase-4 fires-once ruling). The director never produces it.

#### 3.2.5 The containment amendment [RATIFIED — Sean, 2026-09-13; BUILT same day, §14.5]

**Origin and provenance.** Proposed by a Claude Chat review of this document (2026-09-13) from the
§14.4 arithmetic; reviewed in-session, three gaps found and ruled on (weariness, the skipped
DE_ESCALATION consequences, the build shape); ratified by Sean. Provenance of each point is marked.

**Problem (measured, §14.4; confirmed live 08-28 and 09-01).** At ratified values the meter nets
≈ −12.75 per cycle and pins at the floor. `hawkThreshold` 60 is unreachable; ESCALATION,
counter-strikes and General Engagement never fire. Root cause is design, not tuning: every cycle
ended at the ceasefire table, whose expected cut (−13) alone exceeds the mean incident gain (+8.25).
Fictionally that says leadership brokers a ceasefire after every border clash.

**Decision — separate containment from intervention [Chat proposal; ratified].** Routine incidents
are absorbed by the border playbook at no political cost; the ceasefire table is reserved for when
the playbook is failing. The RETALIATION exit becomes the three-way branch in §3.2.2 item 3.

**Rulings taken 2026-09-13 (Sean):**

1. **Weariness decays [assistant recommendation; ratified].** `weariness.decayPerDay` 0 → 0.025.
   The "purely cumulative" reading was fine while talks ran every cycle. Once talks are rare,
   unbounded weariness makes every one of them a near-certain full stand-down (−30, to the floor)
   and pushes ESCALATION further away the longer a campaign runs — inverting the amendment and
   killing the deferred ratchet-floor idea before it is tried. Decay keeps the property actually
   wanted (a bloody cycle tilts *that* table dove-ward) without permanent grief. Weariness accrues
   only from player-inflicted losses today (0.05/FP), so 0.025/day lets a 200-FP kill fade over
   ~400 days. No existing code: the knob and its decay path were already built.
2. **Target rate: 2–4 ESCALATIONs per campaign.** `preemptiveCeasefireChance` ships at 0.30 and is
   tuned from the measurement line, not the estimate (§14.5 arithmetic).
3. **Terminology: TEMPORARY CEASEFIRE, never "summit".** A summit implies the two leaderships sat
   down to negotiate peace. What the doves broker each cycle is a ceasefire that holds, half-holds,
   or collapses. **Reserved Phase-2 seam [Sean's idea, not yet worked out]:** a quest in which the
   player transports passengers who turn out to be a secret ambassadorial envoy trying to end the
   war — *that* is the peace summit, and it is quest content. State constants keep their Pass-1
   spelling (`STATE_POLITICAL_INTERVENTION`, `OUTCOME_STAND_DOWN`); only author-facing words changed.
   §14 build history keeps its original vocabulary.
4. **Contained beat: the incident telegraph's closing update [assistant recommendation; ratified].**
   No new intel class. `ExileHegLeagueIncidentIntel` gains a third beat, `CONTAINED_UPDATE`:
   "<Victim> has lodged a formal protest… neither government has taken the matter further." Per-type
   placeholder prose on `ExileHegLeagueIncident.getContainedText` (§11 item 2). One story from spark
   to resolution, zero board clutter, zero new objects.
5. **Recovery convoys become strike-triggered [assistant proposal B; ratified — amends the §4.6
   locked mechanics].** A strike that lands schedules the victim's relief to its own market after a
   lag, bounded by `maxActive`, regardless of state. Contained cycles therefore get relief for their
   one strike, the extended-baseline premise holds everywhere, and the component stops depending on
   the state machine (faction-generic; Ashen strikes could reuse it). Still **Pass 6** to build.

**Consequences of skipping DE_ESCALATION on a contained cycle — all deliberate:** no ceasefire
talks + de-escalation pause, so interdiction resumes at the TENSION rate immediately (the playbook
absorbed it; nobody paused); no fast decay; the cycle close-out (`cyclesCompleted`, party clearing,
`consecutiveHotCycles` reset) runs through one shared `closeCycle()` so the two exits cannot drift.

**Do NOT change:** `floor` 15, `hawkThreshold` 60, the three cuts, `deescalationDecayMult`, any
downstream guardrail. Restraint is not weakened; it moves from the table to the playbook.

**Deferred, logged not built — ratcheting floor:** floor rises a few points per ESCALATION, capped
~35, so later flare-ups start hotter; the Phase-2 truth quest is the only reset. R2-compatible
(the floor only rises). Decide after observing the first organic escalations.

#### 3.2.6 First organic-escalation trace and the cooling ruling [RULED — Sean, 2026-09-19; BUILT same day, §14.6]

**The trace (Sean's long run, read 2026-09-18; full record §14.6).** ~1,600 game days, 9 cycles,
`timeScale 1`, no overrides, the player never fought either side, zero Exiles exceptions. **Every
Pass-3.5 matrix item passed** (Q37–Q41). The tuning did not: **6 escalations in 9 cycles, five of
them consecutive, two General Engagements**, and after cycle 3 the meter never returned below the
warning line. Projected: ~15 escalations and ~5 General Engagements per ten-year campaign against
a target of 2–4 and an R1 that says General Engagement is rare-to-never.

**Diagnosis.** Two causes, both measured in the ledger.
1. *Above the hawk line there was no restoring force.* `collapsePerTensionOverHawk` (0.05/pt) made
   collapse likelier the angrier the sector was; collapse nets **+2** (−2 cut, +4 gain) and halves
   the next quiet stretch; and weariness — the designed damper — contributed **exactly zero**,
   because its only feed was player kills. Post-escalation expected net ≈ −12, landing at ~50:
   still above the warning line, where 70% of cycles contain (+10) and the next incident re-crosses
   60. Positive feedback; P(re-escalate next cycle) ≈ 0.6–0.7, observed 5 of 5.
2. *The climb was faster than §14.5 modelled* — +10.5 per contained cycle, not +8.65 (24 completed
   ops in 9 cycles = +5.3, incidents averaged 9.1) — but first escalation at ~1.5 years is good
   pacing and was left alone. The §14.5 "8–10 cycles" estimate was also wrong for a second reason:
   at +10.5/cycle a single contained cycle crosses the 45–60 band, so the pre-emptive roll only ever
   got one chance.

**Rulings (Sean, 2026-09-19 — all four recommendations approved; provenance: assistant):**
1. `interventionOutcomeWeights.collapsePerTensionOverHawk` **0.05 → 0.** Kept as a knob; the
   "angrier sector is harder to talk down" fiction now rides on weariness instead.
2. `tension.deescalationDecayMult` **4 → 8.** DE-ESCALATION's fast decay was worth 3.6 points;
   now 7.2.
3. **The NPC outcome feed — BUILT.** `ExileHegLeagueDirector.reportOperationWeariness()` is
   called from both op intels' `reportOutcome()` — the seam that already reports completion
   tension, so nothing new is listened for. A landed strike adds `weariness.perStrikeSuffered`
   (12) to the **struck** side; a completed interdiction adds `perInterdictionSuffered` (4) to the
   **victim**; an op that did not land adds `perOpFailed` (8) to the **actor**. Outcome-based, not
   per-FP: deterministic, no battle listener, and it is the spec's own phrase ("how the cycle's
   ops actually went"). Every credit writes a permanent `weariness Heg X / League Y` log line.
   `weariness.decayPerDay` **0.025 → 0.10** to match the ~10× larger accrual scale. Calibration
   target: a full ceasefire after an ESCALATION ≈ 40%, not 25%.
4. **Lesser levers STAGED, not applied** (assistant's call, disclosed): `preemptiveCeasefireChance`
   0.30 → 0.45 and/or `gains.perCompletedOp` 2 → 1. Turning all four dials at once would overshoot
   and leave the next trace unattributable. One line each if 1–3 undershoot.

**Expected shape after 1–3 (verify, don't trust):** post-escalation talks at ~50 total weariness
give ≈ 40/40/20 full/partial/collapse, net ≈ −16, plus 7.2 DE-ESCALATION decay → from ~65 the meter
lands near 41, below the warning line, and needs ~2–3 contained cycles to climb back. Roughly one
ESCALATION per ~4 cycles ≈ 5–6 per ten years — better, still above target; lever 4 trims it.

**Untouched, deliberately:** `hawkThreshold`, `warningThreshold`, the three cuts, `floor`. The
data says the exits were wrong, not the line.

### 3.3 Lifecycle, registration, and kill switches

- Registered in `ExilesModPlugin.onNewGameAfterEconomyLoad` (new game) and guarded re-add in
  `onGameLoad` (`if (!hasScript(...)) addScript(...)`), per the established pattern.
- Work gated on `IntervalUtil` (days), never per-frame; no allocation in `advance()`.
- Master `enabled` kill switch plus **one `enabled` per subsystem block** (the missing
  `blackfleet.assault` switch is a recorded defect — every sub-block here gets one).
- **Idle conditions:** if either faction no longer holds any market, or the pair is no longer
  mutually hostile (see §7 self-heal — transient peace is corrected under Nex-suppress; under
  ride it would pause), the director idles without unregistering, and logs why.
- All spawned intel/conditions bounded and self-expiring; no orphan listeners. Conditions carry
  hard self-expiry independent of the manager (Ashen Ruling F discipline).

### 3.4 Player messaging

Telegraph pattern per Ashen Pass 7: state transitions the player should feel (Incident rumor →
confirmation → contained-or-not; ceasefire talks convened; ceasefire announced) post as intel;
internal meter movements do not. Ops post per the sibling convention (hidden-until-arrival where appropriate — though unlike
Ashen, **source concealment is NOT wanted**: Heg and League home systems are public knowledge;
skip the `getMapLocation()`/`getArrowData()` overrides). Optionally fire vanilla
`FactionHostilityManager.startHostilities()` for free news flavor — but **never read it as state**
(`pickFactions()` skips already-hostile pairs, so vanilla stops selecting this pair once pinned;
the director owns its own war state) [VERIFIED — code].

### 3.4.1 Messaging pass — PROPOSED by Sean 2026-11-08; facts verified in code; RULINGS PENDING (next session); nothing built

**Sean's goal:** cut the noise the player has to sift through. Every message keeps going to the
main feed as now; what changes is the icon, how many beats a thing gets, and which Intel-screen
lists it appears in. Sean also wants a look at bare messages across all the directors.

**How it works today (verified 2026-11-08, tree at d38407e):**
- **Icons.** Each intel picks its own in `getIcon()`: the incident telegraph returns the offender's
  crest; the ceasefire and General Engagement beats return crests; the raid-family intel returns the
  acting faction's crest except on a bounty-payout update, where it already swaps to vanilla's
  credits icon (`graphics/icons/intel/gain_credits.png`, loaded once).
- **The telegraph (`ExileHegLeagueIncidentIntel`).** Posted at INCIDENT with the title suffixed
  "(Unconfirmed)" and the rumour text. A saved countdown runs `incidents.confirmDays` (5); at zero
  the intel flips to confirmed and sends ONE update (the second feed message: suffix dropped,
  confirmation text). One optional third beat: a cycle that ends CONTAINED sends a "formal protest
  lodged" update. That is the whole mechanism; no other variation was ever added. Expiry
  `intelDays` 60. Since pacing ruling A (2026-11-08) a Midan incident never gets the contained beat.
  A Midan incident's rumour reads wrong in play: the player has usually just watched the station
  fall through the strike's own intel.
- **Intel-screen lists = tags (`getIntelTags`).** The raid, strike and squeeze intels (and the Midan
  strike intel, which extends them) inherit vanilla `RaidIntel`'s tags: **Military + the ACTING
  faction** (+ Colony threats if the player owns a colony in that system) **+ Bounties** once
  `bountyPerFrigate` > 0. The bounty payout is an UPDATE on that same intel, not a separate entry,
  so a bounty shows under Bounties and under the RAIDER's faction, not the PAYER's (the League raids,
  the Hegemony pays, the notice sits under League). The three beats (incident, ceasefire,
  engagement) add Military + BOTH faction ids, which is why they appear in both faction lists.
  Cunningham's Vow (`ExileStoryAnnouncementIntel`) is Story + the faction. The blockade
  (`FleetGroupIntel`) is Military + faction; **its bounty is a bare feed message with no intel
  update at all**, unlike the three raid families.
- **Bare feed messages (`CampaignUIAPI.addMessage`, no intel):** about nineteen sites across the
  other directors (Ashen 5, Black Fleet 6 incl. "Black Fleet forces have sacked X!", Omicron 6 incl.
  the bombardment line in `OmicronRaidActionStage`, Refugees 2) plus debug channels. All
  sector-wide. The relief convoy's arrival message is already gated on the player being in the
  system — the precedent.
- Vanilla tag constants: `Tags.INTEL_STORY` = "Story", `Tags.INTEL_MAJOR_EVENT` = "Major events",
  `Tags.INTEL_BOUNTY` = "Bounties", `Tags.INTEL_MILITARY` = "Military".

**Sean's five changes and the assistant's recommendation on each:**
1. **One war graphic** for Heg–League communications (two half-flags split by a jagged line; Sean
   will draw it). Build: one `exile_`-namespaced PNG under `graphics/icons/intel/`, loaded once with
   the payout-icon pattern, returned from the three beats' `getIcon()`. **Recommend:** beats only;
   operations keep their faction's crest (it is that faction's op); payouts keep the credits icon.
   Half an hour.
2. **The telegraph down to one message.** Options: (a) drop the rumour, post once as confirmed —
   simplest, loses the fog; (b) keep the rumour, make the confirmation SILENT (flip the state and
   text without `sendUpdateIfPlayerHasIntel`, so the feed sees one message and the intel entry still
   changes); (c) = (b) plus Midan incidents post confirmed immediately (skip the rumour).
   **Recommend (c).** Keep the contained update: it is the only player-facing ending some cycles get.
3. **Bounties under Bounties + the paying faction only.** Override the raid family's tags: keep
   Military + Bounties, replace the actor id with the victim/payer id (`tags.remove(actor)`,
   `tags.add(victim)`). **Also** give the blockade a proper payout update so it matches. Open: do the
   op intels stay under Military (recommend yes; they are military operations, and that list is
   where vanilla puts raids).
4. **The three beats to one list.** Three one-line tag changes. Story (Cunningham's Vow already
   lives there) or Major events. **Recommend Story**, keeping Major events for the Midan material so
   the two lists mean different things.
5. **Midan strikes to Major events.** One `getIntelTags` override on the Midan strike intel. **Open
   for Sean:** (i) does its bounty payout keep the Bounties tag (recommend yes); (ii) do the Midan
   incident telegraph and Cunningham's Vow follow it to Major events (recommend yes for the
   telegraph when it is Midan-caused, yes for the Vow: "related to the three strikes" is the rule).
6. **Sacked / bombarded and the other bare messages.** Gate each on the player being in the same
   system (one line each; the relief precedent), or route the sector-wide ones through a tagged
   intel with no popup. **Recommend:** a short cross-director message audit as one sweep item —
   list every site, decide per site: in-system only / tagged intel / keep.

**Feasibility, complexity, stability:** all presentation-layer; `getIcon`/`getIntelTags`
overrides, one silent state flip, one new intel update on the blockade; no save-shape change, no
state-machine or fleet-pipeline change. About a hundred lines plus one image. Fits as a short
presentation pass beside Pass 8, or inside it.

**Rulings needed:** 2 (a/b/c), 3 (Military stays?), 4 (Story vs Major events), 5 (i)/(ii), 6
(gate vs tagged intel; audit yes/no), and whether to bundle with Pass 8.

---

## 4. Operations toolkit

Each op maps to a proven mechanism. Reuse table in §13; adaptations noted here.

### 4.1 Interdiction raids + condition

The design doc's "interdiction-style raids that attack opposing fleets and intercept others,"
causing an Ashen-interdiction-like condition while present.

- **Mechanism:** third port of the presence-raid pattern
  (`BlackFleetCommerceRaidStage` → `AshenInterdictionStage`; ~80% reuse measured on the second
  port). Fleets loiter `presenceDays`, never touch planets; condition lands day N, lifts on
  withdrawal or <50% FP abort.
- **Condition:** NEW id (`exile_hl_interdiction`), subclass of
  `ExileCommodityShortageCondition` — accessibility penalty added in our own `apply()`/
  `unapply()` around the super call. Per settled ruling, condition ids are **never shared across
  directors** (lifecycle collision + tooltip attribution).
- Per-kill bounty/rep paid by the *raided* faction during the op (§4.7).
- **Scope [RATIFIED — Sean, 2026-08-27]: interdiction stays VICTIM-FACTION-SCOPED** (per-market
  on the raided faction only, the Ashen/vanilla precedent). Third-party, neutral, strain, and
  player markets in the system take no collateral. Do not widen it.

### 4.2 System blockades

**Scope [RATIFIED — Sean, 2026-08-27]: blockades are SYSTEM-WIDE.** The blockade condition
applies to every market in the blockaded system, not just the victim faction's — a blockade
chokes the system's jump points, and system-wide pressure is its distinguishing player-facing
signature versus surgical interdiction. Standard guards still apply when Pass 4 builds it
(protected factions, strain markets, player-colony handling need explicit treatment in the
Pass-4 design — flag any carve-out that would contradict "system-wide" for a ruling).

- **Mechanism:** vanilla stack — `BlockadeFGI` + `FGBlockadeAction` + vanilla `econ.Blockaded`
  condition (no custom condition needed) [VERIFIED — Ashen Pass 6a]. Thin subclass
  `ExileHegLeagueBlockade` copied from `AshenBlockade` (only ~5 overrides; generic except
  `FACTION_ID`, the companion-raid type, and the station-suppression faction — all
  parameterized). Keep `SpreadBlockadeAction` (holds **every** jump point) as-is.
- Launch plumbing copies `AshenInterdictionManager.launchBlockade()` nearly verbatim. Two
  load-bearing flags: `playerTargeted=false` (keeps it NPC-vs-NPC) and `makeFleetsHostile=false`
  (it maps to the player-scoped `$cfai_makeHostile`; true made the blockade hunt the player)
  [VERIFIED — in-code comments].
- **Blockade fleets are uncapped — ACCEPTED [RULED — Sean, 2026-08-26].** The `BlockadeFGI`
  pipeline has no `maxShipSize` lever; gate-holders come from vanilla's `fleetSizes` path and
  will include cruisers/capitals (XIV possible for Hegemony, consistent with R3). Rationale:
  blockades are the serious op — Escalation-only, whitelist-confined, stationary; heavy wrecks
  from breaking one are earned against a real formation, not farmed. Matches Ashen precedent.
- Independent/Pirate markets in-system take the Blockaded condition as collateral [AUTHOR].
- Blockade fleets need `RAIDER_TAG` + `addEventListener(companionIntel)` or they pay no bounty
  (the `AshenBlockade.registerForBounty` lesson).

### 4.3 Surgical strikes on military industries

- **Mechanism:** `AshenCulannStrikeStage.disruptFor(market, industryId, days)` is the closest
  fit — explicit named-industry list, per-industry durations. Copy `isMilitaryIndustry()` from
  `OmicronRaidActionStage` verbatim (private static; tag-based category tests with vanilla ids
  as belt-and-braces — an id-only check misfiled the KoL garrison in playtest). **Never**
  POPULATION; disruption is verified deciv-safe.
- Used as the RETALIATION-state punitive op and as counter-strikes during ESCALATION.
- Report `ExileUnrestGuard.reportExilesPressure()` after any pressure lands; compute pressure
  days via `ExileEmergencyMeasuresConfig.pressureDays(...)` (Culann strike is the worked
  example).

### 4.4 Planetary blockade (squeeze, not siege)

The design doc's "attack station and fleets in orbit for an extended period, don't strike ground
targets; squeeze local authorities toward capitulation; Accessibility down, player Buy prices up."

- **Two candidate shapes** (decide at build time, prototype first):
  (a) vanilla `FGBlockadePlanetAction` — but note it is a **siege** action
  (`MilitaryResponseParams(HOSTILE)` aimed at the market) and was the source of every behavior
  problem in Ashen §14.8; or (b) the presence-raid pattern (§4.1) anchored to one market's
  orbit with the condition below. **(b) is the lower-risk default** given §14.8's history.
  **CONFIRMED AND BUILT AS (b) — Pass 6, 2026-09-20 (D80.2).** The consequence, flagged for the
  author: the fleets never engage the station at all — `suppressStationAttacks` actively keeps
  them off every station in the system — so the opening sentence's "attack station" is true only
  in the condition's prose. Making it literal is `FGBlockadePlanetAction` and carries the Ashen
  §14.8 behaviour log with it: a separate ruling, not a config knob.
- **Condition:** NEW id (`exile_hl_planetary_blockade`), subclass of `ExileTimedCondition`:
  accessibility penalty + `getPlayerSupplyPriceMod().modifyMult(...)` (the `ExilePriceSpikes`
  mechanism, which is generic; the BF-specific flagship lifecycle and refugee carve-out stay
  behind). Hard self-expiry independent of the fleet, evaluated live (Ruling F mitigations).
  **CORRECTION (Pass 6, D80.10 item 1): `getPlayerSupplyPriceMod()` is on `CommodityOnMarketAPI`,
  not on `MarketAPI`** (verified, `campaign/econ/CommodityOnMarketAPI.java:151`). The
  implementation is therefore a loop over `market.getCommoditiesCopy()` with a matching
  `unmodify(id)` loop in `unapply()`, exactly as `ExilePriceSpikes` does it. The earlier wording
  here and in §8 named a market-level method that does not exist.
- **Scope: THE ANCHOR MARKET ONLY** (D80.10 item 3, taken at build time — this section never said).
  §4.1 is victim-faction-scoped and §4.2 is system-scoped; "planetary" beside "system" can only
  mean the single colony the cordon is parked over, and it is the reading that keeps the three ops
  distinguishable.
- Player interception on landing → §4.5.

### 4.5 Player interception / inspection / extortion dialogue [HIGH RISK — isolated late pass]

Formally **relocated here from cancelled Ashen Pass 6b**; the implementation path is verified:

- Runtime `CampaignPlugin.pickInteractionDialogPlugin(SectorEntityToken)` returning a
  loose-script `InteractionDialogPlugin` — **no precompiled rule command needed** [VERIFIED —
  Ashen §6.4a.1]. Hand-off back to normal docking via short-expiry market-memory flag; combat
  via `FleetInteractionDialogPluginImpl` + `FIDConfig`; trade penalty via
  `ColonyInteractionListener.reportPlayerMarketTransaction`.
- Covers both flavors: picket inspections during TENSION (corrupt officials extort the player)
  and blockade-running during §4.2/§4.4 (fight / negotiate / lie / bribe past).
- **Mandated mitigations transfer intact** (Ashen Ruling F): (1) "is this blockaded" evaluated
  live every time, never cached where it can outlive the fleet; (2) hard self-expiry on all
  effects; (3) once-per-dock rep guard (the transaction listener fires per transaction);
  (4) own isolated build pass + own config kill switch. Ruling J3's rep-gated visible-door
  option carries over as a pattern.
- This was the highest-risk pass in the Ashen plan (could lock a player out of a market).
  **RULED — Sean, 2026-08-26: in v1 scope, as the isolated FINAL ops pass** (§12 Pass 8) —
  built only after the whole loop is stable, behind its own kill switch, shippable without it.
  When Phase 2's reusable dialog scaffold is later designed, this dialogue is prior art to
  fold in, not a second framework to maintain.

#### Pass 8 checklist and time estimate [DRAFT 2026-11-08, for Sean's planning; nothing built]
> **2026-11-09: superseded in part by `docs/EXILES_PASS8_PLAN.md`.** Sean ruled a split: 8a (the
> fleet stops the player, on vanilla's `HasslePlayerScript` seam + `rules.csv`, no market hijack)
> and 8b (the docking plugin described above, for the squeeze only, decided separately). Read that
> document before using the checklist below; its session plan still assumes the single docking seam.
**Status:** designed (this section + Ashen §6.4a.1's option table, ratified for reuse by J3 and
F.d), ruled V1 as the final ops pass (R4), implementation path verified (S9). **No code exists.**
The campaign-plugin registration pattern is already in the tree (`ExileOmicronCampaignPlugin`).

**Session 1 — rulings (Sean + assistant, ~1 real hour, no game).** Decide: (1) the two option
tables — TENSION picket inspection (corrupt officials: pay / refuse → fight / lie / leave) and
blockade-running at a §4.2 gate or §4.4 cordon (fight / negotiate / lie / bribe / leave), plus
Ashen's rep-gated "official business" door (>+49 with the actor, J3) and the passive once-per-dock
trade penalty; (2) credit and rep numbers per side (asymmetry: does the Hegemony inspect harder,
the League bribe cheaper?); (3) which fleets intercept — interdiction raiders only, or also
blockade pickets and squeeze cordons; (4) what the player's standing with the actor changes;
(5) the kill switch (`playerInterception.enabled` + `$exiles_hl_interception_enabled`); (6) the
lock-out guard — market-memory flag expiry (hours, not days), live "is this still blockaded" check,
one rep hit per dock. Output: a §14 build plan with every number as a tunable.

**Session 2 — build (assistant, one session; Janino + a boot test).** `InteractionDialogPlugin`
picked by a campaign plugin; config block; handoff back to vanilla docking by a short-expiry
market-memory flag; combat via `FleetInteractionDialogPluginImpl`; trade penalty via
`ColonyInteractionListener`; one measurement log line per interception with the option taken.
No new game needed for the code; a new game is still the test convention.

**Session 3 — live test (Sean, ~1¼ real hours of play at ~1 game year per hour, plus reloads;
Nexerelin as usual; `timeScale` 1 because blockades cannot be tested below it).**
1. **Inspection in TENSION.** Wait for `INTERDICTION LAUNCHED` (cadence 90 d ≈ 15 min; a test pin
   that shortens it is worth adding in session 2), fly to the system, approach a raider → the
   dialogue. One reload per option: pay (credits gone, log line), refuse (combat), lie (roll), leave.
   Dock at the market afterwards: ordinary docking, no lingering flag. ~25 min including the wait.
2. **Blockade running.** `forceState: 4` → entry-salvo blockade (3 d prep + travel ≈ 3 min). At
   the blockaded market: each option once with reloads; the rep-gated door via console
   `setrelation`; the passive trade penalty hits once per dock (dock twice, trade both times);
   save/reload mid-blockade and dock again; wait out the 45-day payload (≈ 8 min) and dock once more
   → nothing lingers. ~30 min.
3. **Squeeze cordon** (`forceState: 3`, retaliation roll → squeeze): the same set if the dialogue
   covers cordons. ~15 min. Skip if (3) ruled interdiction-only.
4. **Kill switch.** `$exiles_hl_interception_enabled` false → approach a raider → vanilla dialogue
   only. 2 min.
5. **Nexerelin collision.** One encounter under Nex with a Nex-blockaded or Nex-invaded system in
   play, to confirm the two dialogues do not stack. 5 min.
Read with `tools/exiles-log-report.ps1` (add rows for the new lines in session 2).

**Total: three sittings — about one hour of rulings, one build session, and roughly 1¼–1½ hours
of play.** The highest-risk item is the market lock-out; test 2's "dock again after it ends" is
the line that matters.

### 4.6 Recovery convoys [RATIFIED — Sean, 2026-08-26; mechanics locked same day]

The supply-fleet idea, halved: **mid-op war-fleet resupply is CUT** (no engine seam — NPC war
fleets consume nothing the engine tracks; any dependency mechanic would be invented plumbing
with an unreadable payoff). **Recovery convoys are IN**, with these locked mechanics:

- **Extended-baseline downtime:** surgical strikes set a deliberately *extended* disruption
  (e.g. 90 days where the design-intended feel is ~45 — config). Recovery convoys subtract it
  back down. A full, uninterrupted convoy schedule lands the effective downtime at
  design-normal — **relief never makes a market recover faster than the intended norm**; dead
  convoys let the wound fester toward the full extended figure.
- **Delivery [AMENDED — Sean, 2026-09-13, §3.2.5 ruling 5: STRIKE-TRIGGERED, not state-keyed]:**
  a strike that lands schedules the struck side's relief to that market after a lag; the convoy
  is route-managed (freighter hulls, light escort, supplies/machinery cargo flavor; bounded
  `maxActive`, self-expiring — the proven refugee-convoy discipline). This replaces the original
  "during DE_ESCALATION each side launches convoys to its own war-damaged markets": contained
  cycles never reach DE_ESCALATION, and a wound with no relief scheduled would fester the full
  extended baseline — punishing restraint harder than escalation. Fiction: a faction repairing
  its own shipyard is logistics, not diplomacy. Faction-generic by construction. **~10-day unload orbit**
  (`unloadDays` — long enough for the player to plausibly see one at work [AUTHOR]); the
  subtraction **drips daily** across the orbit, so a mid-orbit kill denies exactly the
  undelivered remainder.
- **Recovery floor:** drips never reduce a timer below `minRemainingDays` — over-healing is
  structurally impossible.
- **Nothing is written on convoy death.** The extension is pre-baked at strike time; loss =
  denial of relief. Deciv-proof by construction; no kill-listeners, no stacking hazards.
- **`setDisrupted` coarseness (accepted):** one unattributed timer per industry — our relief
  will also "heal" disruption from unrelated sources (e.g. a Nex raid). Harmless in-fiction.
  Build checks: clamp at zero, effects fire while the fleet is abstracted, nothing depends on
  player observation.
- **Unrest-decay acceleration: DROPPED — the build-time check FAILED (Pass 6, 2026-09-20,
  D80.6).** Read against `impl/campaign/econ/RecentUnrest.java`: there is no clean per-market
  decay lever. `DECREASE_DAYS` is a `public static` (90f), so writing it would change the decay
  rate for every market and every source of unrest in the sector, permanently — exactly the kind
  of global side effect CLAUDE.md §7 and the Emergency-Measures work exist to avoid. The
  per-instance countdown `untilDecrease` is `protected` with no accessor, so a per-market
  acceleration needs reflection. Dropped without redesign, per this item's own instruction.
  *One clean alternative exists and was NOT built:* `counter(int, String)` is public, clamps at
  zero and removes the condition when it hits zero, so `RecentUnrest.counter(1, "Relief convoy")`
  on a completed unload would be a safe one-line "relief also calms the streets". It is a
  **different mechanic** from decay acceleration and it interacts with
  `ExileUnrestGuard.isBattered()` (which reads `getPenalty() >= 4`) in a way that could pull a
  market back *into* the target pool, so it needs Sean's ruling. Not shipped, not switched.
- **Asymmetry hook:** Hegemony endurance = more convoys and/or larger per-arrival subtraction.
  **RESOLVED as the second of the two (Pass 6, D80.5 item 9): one per-side `recoveryDaysMult`
  (Hegemony 1.0, League 0.75), NOT `convoysPerStrike`.** One knob, no change to fleet counts, no
  extra pressure on `maxActive`, and the multi-convoy alternative would double the active-fleet
  budget to say the same thing. Clamped to ≤ 1.0 in code, because above 1 a convoy would heal
  below the design-normal band and break this section's own floor.
- **[PROPOSED — pending Sean's nod]:** an *informational* "Relief Effort" condition (name TBD)
  during the unload orbit — icon + tooltip narrating the repair progress, **zero** stability or
  availability effects. Visibility layer distilled from Sean's emergency-assistance alternative;
  the mechanical version of that alternative (availability/stability buffs) was analyzed and
  rejected 2026-08-26 (masks symptoms without touching recovery, guts the interception lever,
  perturbs `isBattered()` cross-director reads, leaks into econ scarcity signals).

### 4.7 Per-kill bounty and rep

The BF-origin pattern, faction-generic: during any active op, the **raided** faction pays
`bountyPerFrigate × hull size × player involvement` + `SYSTEM_BOUNTY_REWARD` rep, deferred
popup, `INTEL_BOUNTY` tag, paying through the ending window. Here it cuts both ways — whichever
side is the victim of the current op posts the bounty, so the player can profitably pick a side
per-cycle (or play both across cycles). Player kills also feed the victim's war-weariness ledger
(small), making the player's mercenary work a real, quiet input to the meter.

---

## 5. Ship-farming guardrails

- **Destroyer cap on all skirmish fleets:** `FleetParamsV3.maxShipSize = 2` in the `createFleet`
  override — native engine lever, already config-driven and already ratified at destroyer for BF
  raids and Ashen interdictions for exactly this reason [VERIFIED — code + config comments].
  Cruiser/capital presence lives in *defensive* key-system patrols and (if ruled in) the General
  Engagement super-state — defenders aren't farmable the way attackers' wrecks are.
- **Feasibility gate:** own private copy of the 5-line idiom
  (`WarSimScript.getEnemyStrength(faction, system) <= fp * feasibilityMult`), with the
  instrumentation log line. **Do not extract a shared helper** — rejected on the merits
  (§14.6); the differences between directors' gates are playtest-earned.
- **XIV Battlegroup hulls** [VERIFIED — skins carry `XIV_bp` tag; reach fleets only via
  `hegemony.faction` knownShips/priorityShips; no engine-side exclusion exists]:
  - **RULED — Sean, 2026-08-26: NO sweep.** The destroyer cap alone removes 5 of 6 XIV hulls
    (only `enforcer_xiv` is a destroyer); its rare natural appearance in skirmish fleets is
    acceptable and satisfies the design doc's "avoid XIV if reasonably possible" on its own.
    No `XIV_bp` post-generation filtering anywhere in the director.
  - (Reference, should this ever be revisited: a one-line
    `member.getVariant().getHullSpec().hasTag("XIV_bp")` sweep in the `createFleet` override
    would remove them mod-safely.)
  - Avoid `modeOverride = PRIORITY_ONLY` (Hegemony's priorityShips are `legion_xiv` /
    `onslaught_xiv` — it makes things worse).
- **Raid ramp:** ops follow vanilla-pirate-raid-like FP advancement [AUTHOR]; FP guesses ship
  with a measurement log line (the `assaultFp: 800` lesson — instrument before tuning).

---

## 6. Midan's involvement [PROPOSED — assistant; awaiting §11 R8–R9]

Both mechanisms slot into the crisis engine as INCIDENT-table entries / TENSION-state actions;
neither is timed.

### 6.1 The false-flag "Persean special strike fleet" [RATIFIED — Sean, 2026-08-26]

- An INCIDENT-table entry, **state-gated**: eligible only when de-escalation is winning
  (tension below a floor for Y consecutive days), long cooldown, powerful unknown fleet
  attacking a strategically important Hegemony location, unattributable to Midan — read
  in-fiction as a League special strike fleet.
- **RULED: ambient from v1, hard-capped** — live as soon as its pass builds it, with a
  per-campaign firing cap (proposed 2–3, config) on top of the state gate and cooldown. A
  player who later learns the truth realizes they watched it happen.
- Gating on war state (not time) turns Midan into an antagonist that **reacts** — including to
  player peacemaking, which satisfies the Quest-Input goal ambiently before any quest exists.
  (This satisfies the design doc's "quests or special phases, not timed" leaning: the
  tension-floor condition IS a special phase — the moment peace threatens to break out.)
- **Leaves evidence** each firing (persistent flags/breadcrumbs): the player's eventual route
  into the conspiracy, and the in-fiction reason Midan cannot spam it — every use risks
  exposure.
- The one-sided asymmetry is a feature: the League's grievance is self-sustaining (it knows it
  was framed), so Midan only needs to keep the Hegemony's anger hot — fits "plans can fail."
- **PERMANENT-CONSEQUENCE PAYLOADS [RATIFIED — Sean, 2026-08-26; AMENDED 2026-10-10 — only the LAST rung is permanent, see TARGET RULINGS below]:** each capped firing deals a
  **real, permanent world-state change** from an author-curated target list — candidate #1:
  Eventide, damaging the Orbital Solar Array → food output drop → sector-wide econ ripple.
  Rationale: a lasting wound justifies the tension jump (transient raids are weak casus belli);
  once-per-game is automatic (finite list, each target strikeable once); and the strikes
  **rhyme with Nachiketa** — Midan's M.O. of permanent economic wounds becomes the conspiracy's
  recognizable fingerprint across the evidence trail. Machinery precedent: Ashen 6d (damaged
  Hybrasil mirror) — entity state change + condition swap + custom description, confirmed
  in-game. Bounds (controlled-collapse pillar): one-time, non-stacking, econ absorbs shortfall
  via imports, deciv guard applies. Scope fence: Black Fleet permanent strikes do NOT live in
  this director (BF systems doc / Phase-2 territory if wanted); anything grander than these
  capped strikes is Phase-2 quest material.
- Target selection: **author deliverable — one merged list** covering the R6b blockade
  whitelist, false-flag strike targets, and each strike's permanent effect + custom description
  (author voice). Attribution/fleet-construction mechanics (League-appearing roster, no Midan
  traceability) are build-pass detail.
- **TARGET RULINGS [RULED — Sean, 2026-10-08]:**
  - **AMENDED 2026-10-10 (Sean): permanence escalates too.** The 2026-08-26 "each firing is
    permanent" rule is replaced: **rungs 1–2 are "really bad, semi-permanent"; only Eventide is
    permanent.** The ladder escalates in severity AND duration, so the player learns "this is bad but
    heals" before Eventide breaks that rule. The once-per-game guarantee now comes from the fixed,
    capped ladder; the fingerprint becomes "wounds chosen to cripple recovery".
  - **CLARIFIED 2026-10-26 (Sean): the ladder escalates in EFFECT, not in fight difficulty.**
    Arcadia is a local strike with long recovery, Sphinx a strategic strike with long recovery, and
    Eventide a strategic strike with permanent effects. Sphinx is realistically Midan's hardest
    fight (the battlestation); Eventide being easier is fine and still reads as the most dramatic.
    The order stays. Still open: how Sphinx becomes reachable (escort strength, cap, or margin).
    Decide on corrected station numbers (Pass 7 plan §7, 7d-1 test record).
  - **Fixed escalation ladder, cap 3:**
    1. **Citadel Arcadia — its military base wrecked: disrupted ~1 year** (vanilla disruption, no
       cap exists; about 3× the ≤133 days an ordinary League strike inflicts). The opening rung looks
       like ordinary war and is recognised as Midan's only in hindsight. Station market
       (`arcadia_station`), so no bombardment. Knock-on for the duration: Agreus and Nomios lose
       their protector and Arcadia's defence drops for every director — Midan opening ground for
       its Black Fleet proxy (§6.2). Rejected forms: deleting the base (permanent; and a
       destroy-then-rebuild mechanism is needless saved state when disruption IS vanilla's
       "rebuilding"), killing the base commander as the goal (too lucky; may appear as a
       consequence in the intel text), saturation bombing (overshadows Eventide, deciv risk).
       **Rung-1 follow-up rulings [RULED — Sean, 2026-10-10]:**
       - **Base down 1 year, relief capped at half (6 months)** — confirmed.
       - **Black Fleet follow-up: EMERGENT, nothing built for v1.** Arcadia is already the Black
         Fleet's historical magnet (BF config: the 2026-07-09 anti-hotspot fix), and losing Citadel's
         patrols lowers its measured defence further. Measure it first: in a test run, log which
         Black Fleet ops reach Arcadia after the strike. **Fallback only if it does not appear:** a
         "Midan directive" market-memory key read in the same two places as the grudge pull
         (`BlackFleetRaidManager.pickTarget` system pull + `pickVictim` weighting; ×10 weight, ×2
         feasibility relax, self-expiring) — the §6.2 one-way memory-key seam, ~15–20 lines. **The war
         director never launches a Black Fleet op itself** (single-spawner rule, §6.2), so "sacked
         within a couple of months" is a likelihood, not a guarantee (assault cadence ~120d + 60d
         system cooldown → expect 2–6 months, well inside the one-year window). Note: Citadel's
         station still counts in the BF assault gate for the whole system, so commerce raids
         (which ignore stations) are the likelier natural visitor unless the station is also down.
         A BF sack of the independents is attributed to the BF and moves no Heg–League tension —
         opportunism, not Midan's plan.
       - **Hegemony special defence fleet: REJECTED.** A persistent fleet lifecycle (spawn, patrol,
         despawn on repair, early-death and Nex-ownership handling, save/load) is orphan-fleet risk
         for little play value, and it would suppress the Black Fleet follow-up above. The relief
         convoy is the Hegemony's visible response.
       - **"The Hegemony can't spare ships": v1 = one line in the strike/relief intel text.**
         Deferred mechanical option (unbuilt): while Citadel's base is disrupted, scale the
         Hegemony's offensive launch weight by one config value. Build only if a run shows rung 1
         feels weightless — it touches playtest-earned war pacing.
       - **Wound widened [RULED — Sean, 2026-10-10]: Military Base + Orbital Station 1 year each,
         Spaceport 6 months; Mining NOT struck.** One relief record covering all three with a
         6-month budget gives the ruled split for free: the drip subtracts the same days from
         every struck industry down to `minRemainingDays` (`ExileHegLeagueRecoveryManager.drip`),
         so base + station land at ≥6 months and the spaceport heals to the floor (no cap in
         effect). Mining was dropped: under max-not-sum its volatiles loss is probably invisible
         (§10j of the economy map) and it only added breadth.
         - Vanilla effects while disrupted: station = no station fleet + its ×1.5 ground-defence
           bonus gone (vanilla already disrupts a defeated station for 45 days — this is the long
           form); spaceport = **−50% accessibility** (the market keeps `hasSpaceport`, so no −100%
           no-spaceport penalty). Citadel Arcadia is near-defenceless for a year.
         - **Accepted tradeoff:** rung 1 no longer passes for ordinary war (an ordinary League strike
           is one military industry, ≤133 days); the "recognised as Midan's only in hindsight"
           framing above needs rewording in the text pass. **RESOLVED 2026-10-11:** Sphinx was
           widened (rung 2 below) and now outdoes rung 1 on every axis.
         - Build-time checks added: with the station down the whole system passes the BF assault
           gate (Citadel itself becomes sackable — the intended follow-up); Citadel becomes
           extortion-eligible; League ops will find Arcadia soft for a year (BF-12 blocks
           simultaneous dogpiles, not sequential ones) — watch a run for a slow Recent-Unrest slide
           toward decivilization (size-4 market; unrest guard + Nex conquest shield are the rails).
    2. **Sphinx (fuel, the Nachiketa rhyme) — semi-permanent. FORM RULED [Sean, 2026-10-11].**
       The rung escalates on every axis over rung 1: rung 1 wounds one system's defences, rung 2
       wounds the Hegemony's fuel across its whole sphere, rung 3 is permanent and sector-wide.
       Shape is Claude's "Option B" (assistant-originated, accepted), widened by Sean's ideas 1–4.
       - **Why a production cut alone was not enough (verified from source 2026-10-11):** Sphinx
         makes ~4 fuel (`FuelProduction` size − 2, +1 administrator); Sindria makes ~9 (5 + 3
         synchrotron + 1). By §10j max-not-sum, Hegemony markets would simply switch to the global
         route and buy Sindria's 9 — nobody shorts. The faction-wide shortage therefore needs an
         in-fiction cause: the **Diktat fuel embargo** below.
       - **Game-start battlestation [Sean's idea 1]:** add a low-tech `battlestation` to Sphinx in
         the run-once vanilla-patch script (`ExilesModPlugin`, pattern `applyMaxiosChangesOnce`;
         Sphinx has no station to remove). Lore: a decade of fortifying the Hegemony's sensitive
         fuel asset after Nachiketa. Side effects accepted: the League squeeze/strike gates count
         the anchor's station, so League ops on Sphinx become rarer (watch the target shift in
         Samarra); the Midan strike fleet becomes Midan's largest commitment (fits "elite forces
         only in special circumstances"); new game only.
       - **The wound (durations are config):** Fuel Production **27 months**; Battlestation and
         Military Base **18 months**; Spaceport **6 months**; Heavy Batteries untouched. All
         disruptions use max semantics.
       - **Relief capped at 9 months:** ONE `ExileHegLeagueRecoveryManager.record(...)` covering
         all four industries with a 270-day budget (Hegemony `recoveryDaysMult` is 1.0, so the full
         budget is deliverable). The drip subtracts the same days from every still-disrupted industry
         down to `minRemainingDays` (5), so a fully delivered convoy leaves: spaceport at the floor
         (fully healable, as ruled), battlestation + base at 9 months, fuel production at 18 months.
         Convoy losses (§6.2 proxy) deny the remainder; 9 months is a ceiling, not a guarantee.
         No new relief code. Sean's reason for the long fuel wound: time for a questline or two to
         start, and "this is a big deal" must be very visible.
       - **Decivilization watch item (as rung 1):** with station + base down, Sphinx (size 5) is a
         soft League squeeze target for 9–18 months. Rails: unrest guard + Nex conquest shield.
       - **REMOVED 2026-11-01 [Sean]: the Diktat fuel embargo below.** It was built (7e-1) and
         worked as specified, but Sean ruled it a bad idea after seeing it in play and checking the
         fuel and volatiles economy (build plan sec 7, "7e-1 retest record" and "OPEN — the
         embargo's form"):
         - **It choked fuel producers.** The per-market availability cut hit Nachiketa's own exports.
         - **It made no sense for the Diktat.** Sindria imports all 7 of its volatiles and Eventide
           (Hegemony, 7) is the sector's best volatiles exporter (Ilm, 6, is next). Lore read (Sean):
           Andrada is old and dying; Dolos Macario, who pulls the strings, is sociopathic and
           more than slightly fascist, but not foolish. He might one day try to SEIZE Eventide; he
           would not badger the Hegemony. The Diktat are opportunists, not Midan's partners. That
           supersedes the PROPOSED hidden canon below as Sean's current lean, not yet ruled.
         - **Removed:** `ExileDiktatEmbargoController`, the `ExileDiktatFuelEmbargo` condition and its
           CSV row, `midan.embargo{}` and the payload's `diktatEmbargo`. Rung 2 is now the Sphinx
           wound and relief only.
         - **Consequence flagged for the post-Pass-7 review:** without the embargo, rung 2's fuel
           effect is local. Sphinx's 4 fuel is lost and the Hegemony's own best source drops 4 → 3,
           but Hegemony markets still import Sindria's 9 (this is why the embargo existed, see "Why
           a production cut alone was not enough" above). Rung 2 still escalates over rung 1 in
           scale (station, base and fuel for 18–27 months, Midan's largest commitment) but no longer
           "across the Hegemony's whole sphere".
         The original ruling is kept below for the record.
       - ~~**Diktat fuel embargo [Sean's ideas 3–4; RULED]:**~~ (REMOVED 2026-11-01) a short delay after a SUCCESSFUL Sphinx
         strike (config, default ~7 days, assistant default), one Story-tagged intel announces that
         the Sindrian Diktat has declared a fuel embargo. It uses the standard vanilla notification,
         stays in the Intel window's Story section for 30 days, and is one-shot and self-advancing
         (CLAUDE.md intel gotcha). **No cause is stated in v1** — Sean will revise the text near the end
         of Phase 2. No Diktat relations change (scope ruling). The embargo condition goes on **at
         the same moment** as the announcement, so the explanation and the price rise arrive together
         (Claude's recommendation, accepted).
         - **Condition:** a fuel-only subclass of `ExileCommodityShortageCondition` (fuel is the only
           commodity it always hits; it picks no import-dependent extras). Working name "Diktat Fuel
           Embargo" (assistant suggestion; final name is Sean's). Effect is vanilla only: fuel
           shortage, higher prices (the most player-facing effect — Sean, 2026-10-11), and at most −2
           stability on markets with a Patrol HQ / Military Base / High Command (their stability bonus
           is cut by the largest supplies/fuel/ships shortage, never below zero). It does NOT touch
           patrol counts, ground defence (supplies only), fleet size (ships only) or production.
         - **Fleet-size option, built OFF [RULED]:** a config multiplier (default 1.0). At 1.0 the
           condition adds no modifier and no tooltip line; below 1.0 it applies a "fuel rationing"
           `COMBAT_FLEET_SIZE_MULT`. Raise it only if a run shows rung 2 feels weightless — it softens
           Hegemony space for every director.
         - **Scope:** every Hegemony, Independent and Refugee market in any system containing a
           Hegemony-owned market **at strike time**. A rule, not a list, so it survives Nex ownership
           changes and the pending Skathi transfer; the log prints the resulting list. Today this
           catches Agreus + Nomios (Arcadia), Asharu + the Asharu Refugee Platform (Corvus; refugees
           IN by Sean's ruling, watch the salvage economy), Orthrus (Samarra).
         - **Duration: fixed 24 months.** The embargo is political, so it is not tied to Sphinx's
           repairs. When it ends, a second short Story notice says the Diktat has lifted the embargo.
         - **Quest seam for an early lift [RULED]:** a Phase-2 quest (e.g. the player bribes or
           persuades the Diktat) sets a sector memory flag (working name
           `$exiles_diktat_embargo_lifted`) with a VANILLA rule command — no new precompiled rulecmd.
           A small embargo controller checks it daily and lifts early. The controller OWNS the
           condition (remembers the markets it hit and removes them all at month 24 or on the flag)
           instead of each market timing its own copy, so the end lives in one place. Bounded: one
           controller, removes itself when the embargo ends.
         - **Nex edge case [RULED]:** if the Diktat does not hold Sindria when the strike lands, the
           in-fiction cause does not exist — skip the embargo beat entirely (no announcement, no
           condition). Sphinx's own wound still applies.
         - **Hidden canon, PROPOSED by Sean 2026-10-11 (Phase 2):** the Diktat has a loose,
           probably indirect, arrangement with Midan; investigating the Diktat helps the player solve
           the mystery of the "Persean" special-ops fleets the League denies. Never stated in v1
           text. Candidate public causes for the later rewrite (assistant suggestions, unruled):
           (a) neutrality — refusing to fuel either side, which hurts only the Hegemony because the
           League has its own fuel; (b) a domestic reserve for the Lion's Guard; (c) Black Fleet
           raids making the tanker route uneconomical.
       - **Strike fleet, failure and tension for rung 2:** RULED 2026-10-11 for all three rungs; see
         the STRIKE FLEET, FAILURE AND TENSION block below.
    3. **Eventide (the solar array; food; the atrocity) — PERMANENT.** No relief can fix it.
       **Vanilla quest interaction [RULED — Sean, 2026-10-29; PHASE 2]:** vanilla's "Sword of
       Eventide" (Princess of Persea) quest stays available after the strike. House Rao's grand
       ball going ahead on a wounded world is deliberate grim irony. Phase-2 task: once Eventide
       has been struck, adjust the relevant descriptions and add a line or two of dialogue so
       characters acknowledge the damage. Likely route, to verify then: flag-gated `rules.csv`
       rows using vanilla commands only (no new rule-command class), plus description overrides.
       **FORM RULED [Sean, 2026-10-14].** The third and final Midan blow: the most permanent and
       the largest. Midan's goal is the end of the Core, so it cuts deep; Sean's timing target is
       early late-game (game-years in), ideally before the player has a colony making more than 6
       food — an input for the trigger-gate/spacing discussion, not a guarantee (the gate is
       war-state, not calendar). Where Midan goes after rung 3 is Phase-2 quest material.
       - **Why the array alone was not enough (§10j arithmetic, vanilla `SolarArray` source):**
         Eventide makes 10 food (7 size + 2 array + 1 administrator; exports capped at 9). The array
         is −2 farming; unmasking `poor_light` is +25% hazard only (vanilla poor light has no farming
         effect). Eventide at 8 still covers Chicomoztoc (demand 8) beside Volturn's 8 — nobody shorts.
       - **REVISED 2026-11-01 [Sean]: "this is supposed to be huge."** The wound is now:
         - **The array:** the Orbital Solar Array removed (1, unchanged).
         - **The sky:** the mirror and shade adjustments as built in 7e-2 (2). Split ruled the same
           day: 4 destroyed (mirrors Beta, Gamma, Delta, shade Psi), Epsilon adrift, and mirror Alpha
           plus shades Omega and Chi untouched, "something of Eventide visibly survives".
         - **The bombardment: a FULL VANILLA SATURATION BOMBARDMENT** in place of 3 and 4, through
           vanilla's own NPC entry point `MarketCMD.doBombardment(faction, SATURATION)`, as its
           punitive expeditions use it. That means:
           - Stability −10 (temporary recent unrest);
           - pollution (+25% hazard, Luddic Path immigration);
           - every bombardable industry disrupted for 365–456 days;
           - size −1, the 30-day "recently bombarded" flag, and the bombardment animation.
           - The atrocity reputation hits are player-dialog only, so they don't apply.
         - **Dropped as unnecessary:** the farmland scorch and Strike Force's own +2 unrest ("let the
           sat-bomb do the heavy lifting"). The planet-description swap is kept as an optional config
           key, OFF.
         - **Emergency Measures must see it:** the landing sends the Exiles pressure report sized to the
           bombardment (456 days of disruption, 10 unrest points). If Eventide then sits at Stability 0
           for 60 days, Emergency Measures activates. Vanilla decivilization needs 16 months of
           history ending in a 3-month zero streak, then 10% a month, so EM acts first.
         - **Consequences, by reading (confirm in the rung-3 test):**
           - **During the disruption year,** farming and mining are down, so Eventide's food AND its
             volatiles are gone. Sindria's fuel line loses its best volatiles source for a year (Ilm 6
             is next). Expect a sector-wide food squeeze and a Diktat fuel dip.
           - **Afterwards,** Eventide is size 6 without the array: food ≈ 7, not the 6 that the
             2026-10-14 food-cap amendment reached through the scorch. The cap is now 7 unless Sean
             re-adds the scorch.
       - **The wound (all permanent) [ORIGINAL 2026-10-14; items 3–4 superseded above]:**
         1. **The Orbital Solar Array dies:** remove `solar_array` (Hybrasil 6d precedent).
         2. **Samarra's sky is wrecked:** most of Eventide's **5 stellar mirrors (Alpha–Epsilon) and
            3 stellar shades (Omega, Psi, Chi)** destroyed, some knocked off station (switch from
            `setCircularOrbitPointingDown` to plain `setCircularOrbit`, as at Hybrasil), thin debris
            fields (scenery, not loot), new entity descriptions. Exact split is an art call at build.
            **VANILLA ID BUG:** the three shades reuse the mirrors' ids (`eventide_mirror1/3/5`,
            `Samarra.java` lines 74–76), so `getEntityById` is ambiguous — find them by custom entity
            type (`stellar_mirror` / `stellar_shade`) and orbit, never by id.
         3. **Bombardment: Eventide size 7 → 6** via vanilla `CoreImmigrationPluginImpl.reduceMarketSize`
            (what saturation bombardment calls), without the player-side reputation effects. A plain
            vanilla state, no ongoing script; size 6 cannot decivilize. If Nex lets it regrow to 7,
            that is fine (Sean). In-fiction, "the League" bombarded a Hegemony world — the weight
            behind General Engagement and behind the League's denial (intel text).
         4. **Planet description + illustration change** at strike time via runtime setters
            (`setCustomDescriptionId`, `setInteractionImage`) — persisted by the save automatically, no
            save editing. New `exiles_`-namespaced `descriptions.csv` row and illustration (registered in
            `settings.json`; never at vanilla's exact path, or it would replace Eventide's art from game
            start). Art is Sean's, optional.
       - **On-screen playout [RULED: option (b) for v1, (a) as a stretch]:** players will want to
         witness this and try to stop it. **(b)** When the strike arrives with the player in-system, a
         short script destroys mirrors and shades one at a time over a few days with vanilla's
         `ExplosionEntityPlugin` (the `GateExplosionScript` precedent; fleet damage NONE), leaving debris.
         **(a) stretch:** the fleet physically flies to each mirror first — more choreography, more
         risk; only if (b) feels flat. Off-screen, everything applies at once on arrival.
       - **Failure refinement [RULED — Claude's proposal, Sean 2026-10-14; refines the all-rung "On
         failure: no wound" rule for rung 3 only]:** once mirrors are visibly exploding, all-or-nothing
         no longer works. **What is destroyed stays destroyed; the array dies with the first mirror;
         the bombardment (size loss) comes LAST.** A player who breaks the fleet mid-sequence saves
         Eventide's people but not its sky. The +12 failed-rung tension rule still applies.
       - **Expected engine effect (to confirm by test, no famine condition in v1):** with the red tide
         below, Eventide makes 6 + 1 = **7**, the best source anywhere is 7, and **Chicomoztoc runs
         permanently 1 food short** (vanilla: stability −1 at the starving market, equal to its food
         shortage) plus higher food prices. Honest note: the food number is modest; rung 3's depth comes
         from the size loss, the visible wreckage and General Engagement. The commodity-generic shortage
         controller (built for the Sphinx embargo) stays in reserve as a famine shock if the test feels
         thin (Sean: keep in mind, rely on the engine first).
       - **Player counterplay accepted:** a player colony exporting 7+ food can erase the capital's
         shortage (max-not-sum). Treated as a feature — the player as Chicomoztoc's breadbasket.
       - **THE VOLTURN RED TIDE [RULED — Sean's idea; working name]:** a **permanent −1 food** condition
         on Volturn (Askonia, Diktat; aquaculture), applied **at game start** in the run-once vanilla
         patch script (Nachiketa pattern), **visible but unannounced**: a `market_conditions.csv` entry
         with a description and a placeholder icon, so observant players can spot it — quiet
         foreshadowing. Masked by Eventide for years (Volturn 7 vs Eventide's 9 exports); after rung 3 it
         is what turns Eventide's loss into a lasting sector shortage. Name and text are Sean's. This is
         the Diktat's approved "second touch" (minor; Sean, 2026-10-14).
       - **AMENDED SAME DAY — TARGET FOOD CAP 6 [RULED — Sean, 2026-10-14]:** start extreme and back
         off if needed ("backing out is easier than adding more"). Two changes to the above:
         **red tide −2** (Volturn 8 → 6), and the **bombardment also scorches Eventide's farmland**
         (vanilla condition swap `farmland_adequate` → `farmland_poor`, −1; Eventide 7 → 6). Mazalot and
         Eochu Bres already sit at 6, so the best food source anywhere becomes 6. Expected effect (hand
         arithmetic, confirm on the food screen): **all three great capitals go short** — Chicomoztoc
         (demand 8) −2, Kazeron (7) −1, Sindria (7) −1 — each costing that capital stability equal to
         its shortage, plus sector-wide food price rises. Size-6 importers (e.g. Madeira) stay covered.
         Player counterplay grows with it: any colony making 7+ food can feed three capitals.
         **Tuning is done by editing the numbers in the files, not by toggles** (Sean: more flexible,
         e.g. a cap of 5 for a test). So the build keeps each magnitude a plain number in one obvious
         place — the red tide's penalty in its condition, Eventide's farmland step in the rung-3
         payload — so a test of 5 or 7 is a one-line edit.
  - **STRIKE FLEET, FAILURE AND TENSION — ALL RUNGS [RULED — Sean, 2026-10-11; Claude's
    recommendations, accepted unless marked].** Sean's aim: a small-to-no chance of failure without
    the player's intervention.
    - **Pipeline:** reuse `ExileHegLeagueStrikeStage`'s structure (one fixed target; goes through the
      target's station and leaves the other stations alone; success sticks once landed; the window end
      resolves on strength whether or not the player is in range). Each rung is a variant whose payload
      is that rung's ruled wound, not the standard strike package.
    - **Sizing:** at launch, measure the target system's defence (patrols + the target's station, as
      the squeeze gate already does) and size the fleet to **≥ 2.0× that strength** (config), under
      an FP ceiling (config). The 2.0 is a guess and ships with a measurement log line. Benchmark:
      Black Fleet assaults run 350–500 FP; Sphinx with its new battlestation will likely need the top
      of that or more. **If the margin cannot be met under the ceiling, the rung does not launch** —
      it waits and re-checks. **Same if the target is Battered or protected** (the shared
      no-dogpile/no-deciv rails apply to Midan too).
    - **Faction and attribution:** the fleet flies as the **Persean League** with a League roster
      (Midan's doctrine already uses the League as its baseline). **`$noRepImpact`** (vanilla
      `MemFlags.MEMORY_KEY_NO_REP_IMPACT`, BF extortion precedent): the League disowns the fleet, so
      a player who defends the target loses no League standing — the denial expressed mechanically,
      and no punishment for heroism. Phase-2 clue: the League never protests its "losses".
      **Elite quality** (good officers, no D-mods — Midan's pristine nanoforge; in hindsight "too good
      to be League") — assistant suggestion, accepted.
    - **Launch point [RULED — Sean's idea]: the Black Fleet's anchors, Sallee or Lundy.** Midan
      provisions the Black Fleet, so staging through its ports is the fed-tiger relationship; a trace
      leads to the Black Fleet, not Midan (Phase-2 chain: League → Black Fleet → Midan); and the long
      haul from the east widens the player's warning window.
      - **Assembly point [RULED — Sean, 2026-10-14; SUPERSEDED 2026-10-30 by the sneak attack
        below — the fleets now form up inside the target system]:** the Black Fleet is
        HOSTILE to the League (`ExilesSectorGen`), so a League-flagged fleet spawning in orbit at
        Sallee would fight the station and patrols whenever the player is near. The route therefore
        STARTS at the anchor but the fleet ASSEMBLES in hyperspace just outside the anchor system and
        flies League colours throughout. This also keeps the single-spawner rule (§6.2): the war
        director never spawns a Black-Fleet-flagged fleet. Build check: confirm the route's first
        segment can sit in hyperspace (unverified).
      - **The intel must never name the source.** Vanilla raid text names the source market; ours
        overrides it ("a League special strike fleet has been sighted…").
      - **Anchor choice [RULED — Sean, 2026-10-14; Claude's recommendation]:** prefer **Lundy** (the
        supply anchor fits staging better than Mourad's seat), then Sallee; if neither is usable
        (decivilized, conquered under Nex, gone), fall back to **Midan's home market** rather than
        stalling the ladder. The alternative — the rung waits — would let a player who destroys the
        Black Fleet stop the ladder.
      - **Alternate idea, recorded, not for v1:** the transponder swap — the fleet leaves port in
        Black Fleet colours and switches to League colours in hyperspace (vanilla
        `fleet.setFaction`). A player watching Sallee catches the swap: the best clue of all. Costs:
        segment-aware faction logic, and while BF-flagged a kill counts as a Black Fleet kill
        (per-kill bounty) and blurs the single-spawner rule.
    - ~~**Warning window:** the intel appears at launch as a League "special strike" on the target, as
      vanilla raids do; travel time is the window. Without it, a player save would be luck.~~
      **SUPERSEDED — SNEAK ATTACK [RULED — Sean, 2026-10-30, after the 7d-2 test]:** the three Midan
      strikes give no advance warning. The visible 44-day crossing let Nexerelin's special task
      groups (which read every live raid intel, hidden or not) and the player prepare. Now the strike
      intel is not registered at launch; the Strike Force forms up INSIDE the target system, out of
      sight (far from the jump points, the star and the player), and the first warning is the
      target's **emergency call for assistance**, a day or two before the attack reaches it, posted
      with the colony-crisis alarm and carrying the Hegemony bounty. The Lundy/Sallee anchor stays
      the nominal source and the home the fleets return to (return leg unchanged, ruled fine); the
      hyperspace assembly point above no longer applies. A Phase-2 quest gets the player on site
      instead of a warning window (hold/trigger flags, build plan §7).
    - **How it can fail:** off-screen it resolves on strength (vanilla's losses-abort ignores
      abstract damage), so at 2× it effectively always succeeds. With the player present but idle,
      NPC battles run normally — a loss is unlikely, not impossible (Sean's "small chance"). With the
      player fighting, it can be broken through vanilla's losses-abort. (Rare: Black Fleet patrols in
      hyperspace near the anchor could clash with it on-screen.)
    - **On failure:**
      1. **No wound.**
      2. **The INCIDENT still fires with a smaller gain:** a flat **+12** (the existing
         `tension.gains.perMidanInterference`). A "League" fleet still attacked a Hegemony world —
         the player saved the target, but the war heats anyway.
      3. **The rung is spent; the ladder advances. No retry.** Consequence accepted: **Eventide is
         preventable** by a player in the right place when rung 3 arrives ("intervention came just
         in time").
      4. **Breadcrumb:** every firing writes a persistent record (rung, outcome, player present /
         fought); a foiled strike leaves the stronger trail (survivors, wrecks) for Phase 2.
    - **Tension per rung:** each rung opens an INCIDENT with the League as offender and the Hegemony
      as victim, so the Hegemony retaliates. A landed rung **raises tension to AT LEAST** a target
      value (config), which forces the cycle's exit regardless of the exact firing tension and does
      not depend on the still-unruled trigger floor:
      | Rung | Raise tension to at least | Cycle outcome |
      |---|---|---|
      | 1. Citadel Arcadia | 50 | Warning band: ceasefire roll, may still cool |
      | 2. Sphinx | 65 | Above hawk (60): ESCALATION guaranteed |
      | 3. Eventide | 88 | GENERAL ENGAGEMENT (≥ 85), then forced talks |
      **No weariness change** from any rung: weariness pushes toward peace, the opposite of Midan's
      aim, and the Hegemony is angry, not tired. This replaces the earlier unruled
      "small → medium → extreme" suggestion.
  - **Durations are settings** in `director_config.json`, defaults as above.
  - **Relief responds to rungs 1–2** through the existing `ExileHegLeagueRecoveryManager.record(...)`
    hook, so the Hegemony sends recovery convoys — the convoys §6.2's Black Fleet proxy hunts, and the
    player can escort. **Relief is capped at HALF the wound** (a one-year wound never drops below six
    months). Eventide gets no relief.
  - Build-time checks for rung 1 (Claude's list): Arcadia balance in a run; whether the Hegemony
    is an extortion target (Citadel would become eligible while the base is down); squeeze
    whitelist effect; that every disruption caller in Exiles uses max semantics (Omicron's did not;
    fixed 2026-10-10). ~~Suggestion (unruled): tension jump scaled by rung,
    small → medium → extreme.~~ Superseded 2026-10-11 by the raise-to-at-least targets above.
  - All targets are Hegemony: Midan lacks the Hegemony-style hulls for a safe false flag
    against the League (and its fleets already use League doctrine as their baseline).
  - **Conquest shields ON** for the ladder targets (`nexConfig.conquestShieldMarkets`).
  - **Moved to Phase-2 quest consideration:** Jangala, Skathi. Rejected outright: Yama (too
    minor), Coatl (capital system, military), Chicomoztoc (shielded capital), Tigra City, Nachiketa,
    Raesvelg/Ragnar (Valhalla off), Ancyra (no Midan motive).
  - **SCOPE RULING:** no mechanism for adjusting inter-faction relations (e.g. a League–Tri-Tachyon
    wedge) is to be built now. Sean: the mod is already far bigger than intended.
- **NEXT DISCUSSION AGENDA [set 2026-10-10; Sean: take the three events IN ORDER, then their role in
  the war; nothing is built until this is settled].** Claude's prepared questions, not rulings:
  - **Per event (Citadel Arcadia → Sphinx → Eventide):**
    - What exactly is wounded. Citadel: ANSWERED 2026-10-10 (base + station + spaceport, see rung 1). Sphinx: ANSWERED
      2026-10-11 (battlestation + base + spaceport + fuel production, plus the Diktat embargo; see rung 2).
      Eventide: ANSWERED 2026-10-14 (array + mirrors/shades + size 7 → 6 + Volturn red tide; see rung 3).
    - The strike fleet and failure: ANSWERED 2026-10-11 for all rungs (STRIKE FLEET, FAILURE AND
      TENSION block above).
    - What the player sees: intel text (author voice), market description/condition, and the
      evidence breadcrumb each strike leaves for the Phase-2 conspiracy trail.
    - Tension and weariness effect per rung: ANSWERED 2026-10-11 (raise-to-at-least 50 / 65 / 88;
      failed rung +12; no weariness).
  - **SUPERSEDED 2026-10-19 (see TRIGGER GATE — RULED below)** — PARKED 2026-10-15 — trigger-gate PROPOSAL (Claude; UNRULED; paused while Sean reworks the
    war's lore in `docs/EXILES_LORE_HISTORY.md`):** a rung launches when ALL hold — (1) campaign age ≥
    the rung's minimum (placeholders 1 / 3 / 5 years; rung 3 = Sean's "early late-game"); (2) ≥ 1 year
    since the previous rung resolved; (3) tension ≤ 25 continuously for 45 days, war in TENSION or
    DE_ESCALATION; (4) feasibility (2× margin, not Battered), one Midan strike in flight at a time.
    Basis: the 7-year run (§14.6) saw full ceasefires drop the meter near the floor ~every 1.7 years,
    so (3) opens on that rhythm and can't open at game start. Optional "Midan grows impatient"
    fallback: if (1)+(2) have held 2 years without (3), relax (3) to "no crisis underway" (all rungs,
    rung 3 only, or none — Sean to rule). Also open: §6.2's "during DE_ESCALATION" convoy-hunting
    clashes with R16's strike-triggered convoys.
  - **SUPERSEDED 2026-10-19 (ruled below; the Cunningham vow suggestion stays open)** — 2026-10-18 — DIRECTION CHANGE PROPOSED by Sean (unruled; discuss next session): Midan is
    "coldly and actively trying to wipe out the Core", so **less reactive, more opportunistic**.
    Claude's sketch: replace the peace gate (3) with a daily **opportunity score** built from signals
    already measured — *means* (target defence below its normal level), *distraction* (Hegemony
    offensive ops live against the League), *cover* (a crisis underway: a "League strike" mid-
    RETALIATION/ESCALATION is more believable than one out of a quiet peace). Keep (1) age minimums,
    (2) spacing, (4) feasibility + the Battered rule. Optional: "peace threatening" survives as an
    urgency term rather than the gate. The impatience fallback becomes unnecessary. This would replace
    the 2026-08-26 ratified "eligible only when de-escalation is winning". Related lore
    (`EXILES_LORE_HISTORY.md` §4): Cunningham is from Eventide's old families, so rung 3 needs a
    dramatic response from him — suggestion: an intel beat with his personal vow of vengeance, so
    General Engagement reads as his rage and the forced talks as his patrons pulling him back.
  - **TRIGGER GATE — RULED [Sean, 2026-10-19]: OPPORTUNISTIC MIDAN.** Supersedes the 2026-08-26
    "eligible only when de-escalation is winning" rule and both parked entries above. Lore basis:
    Midan wants to throw as much gasoline on the fire as fast as it can — but it is POOR (lore doc
    §2) and its elite strike force is the one thing it cannot easily replace, so it strikes as fast
    as its resources allow. Gameplay comes first; motivation shapes the behaviour.
    1. **No peace trigger.** Midan's urgency is always at maximum; "peace threatening" is dropped
       entirely (not even as an urgency term).
    2. **Readiness pacing replaces fixed age minimums.** Midan becomes ready for rung 1 at a first
       readiness date; after each rung resolves it must **rebuild its strike force** before the next
       rung can launch. **A foiled rung costs a longer rebuild** (the fleet was lost), so a player who
       defends Sphinx delays Eventide — counterplay from one number.
    3. **Opportunity score, with impatience built in.** Checked daily while ready, from signals the
       director already measures: *means* (the target's defence below its normal level), *distraction*
       (Hegemony offensive ops live against the League), *cover* (a crisis underway — a "League
       strike" mid-RETALIATION/ESCALATION is more believable). It launches when the score clears a bar
       that **lowers the longer Midan has been ready**: quick at a juicy moment in a hot war, eventually
       settling for less in a long quiet spell. Replaces the separate "impatience" fallback; the ladder
       can never stall forever. Weights and bar are build-plan details (config).
    4. **Hard rails unchanged:** feasibility (2× margin under the FP ceiling), the Battered rule (even
       Midan doesn't dogpile a failing market — controlled collapse over opportunism), one Midan strike
       in flight at a time.
    - **Pacing values (config) — RULED for now: keep the current ~1 / 3 / 5-year pace** so Midan stays
      in step with the other directors (Ashen peaks ~year 4, the war is calibrated to ~5–6 years):
      first readiness **year 1**, rebuild **2 years** after a success, **3 years** after a failure;
      plus however long Midan waits for an opening.
    - **Planned values for the STRETCH PASS** (Sean, 2026-10-19: average campaigns run ~15–20 game
      years, so Ashen and Heg–League will likely stretch to 12–15 years — an educated guess): first
      readiness **year 2**, rebuild **2.5 years**, putting rung 3 around **years 6–8**, with Cunningham's
      Kazeron quest carrying the later game. Accepted tension: that is likely after the player has a
      7+ food colony, so the food shortage becomes player-solvable — counterplay, not failure.
    - **Test pin:** a `testing` setting that makes Midan ready immediately (like `forceState`), so a
      test need not play five game years to reach rung 3.
    - **Deferred SUGGESTION:** tie rebuild speed to the Black Fleet's prosperity reserve (Midan
      depends on BF spoils, lore doc §2) — hurting the BF slows Midan. Couples two systems; revisit in
      the Haven retune, where the Midan–BF dependency gets built anyway.
  - **LANDING IN THE WAR CYCLE — RULED [Sean, 2026-10-19; Claude's proposal].** Opportunistic rungs
    will often land mid-crisis, so every state is defined. (Code facts: the states are TENSION,
    INCIDENT, RETALIATION, ESCALATION, POLITICAL_INTERVENTION (the ceasefire talks) and DE_ESCALATION;
    **General Engagement is a flag**, set only when ESCALATION *begins* at tension ≥ 85 or after the
    hot-cycle count — `ExileHegLeagueDirector.beginState`.)
    - **The tension effect always applies on landing:** raise to at least 50 / 65 / 88, or +12 for a
      failed rung. Then the state decides the rest:
      | State at landing | Effect |
      |---|---|
      | TENSION | The rung **becomes the INCIDENT** (League offender, Hegemony victim → Hegemony retaliates); the cycle runs normally. |
      | DE_ESCALATION | Cooling ends at once; the rung becomes the next INCIDENT. |
      | INCIDENT / RETALIATION | **Folds in:** no second incident; the higher tension steers the crisis's exit (65 guarantees escalation at the RETALIATION branch). |
      | ESCALATION | Folds in; **rung 3 upgrades it to General Engagement** if it isn't one already (GE is otherwise only checked at ESCALATION's start). |
      | Already in General Engagement | Folds in; the forced talks leave the meter higher afterwards. |
      | POLITICAL_INTERVENTION (talks) | **The talks collapse:** the outcome is forced to the existing COLLAPSE (+4), plus the rung's raise. The spoiler attack. |
    - **Why fold in, not queue:** queuing a second incident adds stacked crises, saved state and odd
      timing; folding in uses the existing machinery — the crisis just gets worse.
    - **Accepted quirk:** a rung landing during an incident with the HEGEMONY as offender leaves the
      League retaliating while "the League" just struck a Hegemony world. Chaotic but harmless — the
      raised tension still pushes both sides toward escalation; rewriting a crisis's sides mid-cycle
      would be fragile for little gain.
  - **WAR OFF AND NEXERELIN — RULED [Sean, 2026-10-19; Claude's proposal].**
    - **War switched off** (`enabled: false` or the quest flag `$exiles_hl_war_enabled = false`) follows
      the HL-1 precedent (`cf283d5`: no new ops, in-flight ops finish, relief ungated):
      - No new rungs and no new directives.
      - A Midan strike already in flight finishes and its **wound still applies** (the damage is
        real); its **tension and incident effects are skipped** (the meter is frozen).
      - An active directive expires; an ambush already spawned plays out under the BF director.
      - **Separate `midan.enabled` switch** (config): keep the war, turn off the false-flag strikes
        and directives (the player-settings design goal).
      - A Phase-2 quest that exposes the false flag and ends the war (R2) uses the same off path — an
        exposed frame stops working.
    - **Under Nex:**
      | Situation | Behaviour |
      |---|---|
      | The rung's target is not Hegemony-owned at launch (e.g. player-conquered — the Nex shield only stops NPC invasions) | **Skip the rung**; the ladder advances; log line. Waiting could stall the ladder forever. |
      | The League is eliminated | The Midan module stops for good (no one left to frame). |
      | The Hegemony is eliminated | Stops for good (no targets). |
      | Midan has no markets left | Stops for good ("plans can fail"). |
      | The Black Fleet is eliminated | Rungs continue (launch falls back to Midan's home, already ruled); ambushes stop automatically (the BF director spawns nothing). |
      | The Diktat doesn't hold Sindria | Embargo skipped (already ruled). |
      | Nex's own relief fleets | Never ambushed — only our marked convoys are eligible. |
      | Nex's own Hegemony–League war | Unaffected — our tension and incidents live only in our director; the B2 pin keeps the pair hostile. |
    - **Both starts:** Midan is created at sector generation, so all of this applies to Exiles and
      non-Exiles starts alike (Sean's usual Nex + vanilla-start test runs included).
  - **CUNNINGHAM'S VOW AND THE KAZERON HOOK — RULED [Sean, 2026-10-19; Claude's proposal].**
    Lore: `EXILES_LORE_HISTORY.md` §4 (Cunningham is of Eventide's old families; his answer to Eventide
    is a late-game Kazeron raid quest that succeeds by default if the player does nothing).
    1. **The vow:** a Story-tagged intel item a few days after rung 3 **succeeds** (config delay,
       default ~5 days — just after the General Engagement announcement), so GE reads as his rage and
       the forced talks as his patrons pulling him back. **Success only** (a foiled rung 3 gives +12,
       no GE); a quieter line for a foiled rung is a later text detail. Built on **one generic
       "story announcement" intel class shared with the Diktat embargo announcement** (build reusable
       pieces generically from the start). Text is Sean's; a marked placeholder until then.
    2. **Cunningham exists now [RULED: option (a)]:** create him as a hidden important person — stable
       id `exile_cunningham` (corrected 2026-10-24 to the `exile_` data-id convention, like `exile_mourad`), rank **Admiral** (Sean, 2026-10-24), name, portrait, no market post, not yet interactable — so the vow shows
       his face and every later quest (the Kazeron raid, the talks at Eventide, the Meridian cover-up)
       points at one consistent character. Portrait: placeholder until Sean supplies one (his own
       *Faces in the Void* mod is a natural source); **must be registered in `settings.json`
       `graphics/characters`** or it renders blank (CLAUDE.md §7). Exiles-namespaced filename.
       **Placeholder KEPT (Sean, 2026-10-19):** `graphics/portraits/exiles_cunningham_placeholder.png`
       (128×128, Claude-drawn): a backlit "classified dossier" silhouette of a Hegemony officer (peaked
       cap, gold collar piping, epaulettes, rank bars), fitting a man whose face the Sector doesn't know
       yet. Not yet registered or referenced — that happens in the Pass 7 build.
    3. **The Kazeron quest hook:**
       - **Trigger — already ruled, no new code:** every rung firing writes a persistent record (rung,
         outcome, player present / fought); the quest checks "rung 3 landed successfully".
       - **Consequence hook — DOCUMENT NOW, BUILD WITH THE QUEST:** the right consequence depends on how
         the quest's outcomes are designed; built now it would be untested code with no caller.
         Candidate shapes, each one memory key the director reads: (a) **block ceasefires until a
         date** — ESCALATION returns to TENSION with no talks and the early-ceasefire roll is disabled
         (the literal "peace pushed far down the road"); (b) **raise the tension floor for N years**
         (e.g. 15 → 45), so no crisis can end "contained". See also §6.3 (Nex's `declare_war` trigger).
  - **Role in the war:** ~~the trigger gate (§6.1: "tension below a floor for Y consecutive days" —
    floor and Y), spacing between rungs, interaction with ceasefires/General Engagement and the
    RETALIATION exit~~ (RULED above), ~~how §6.2's Black Fleet proxy hunts the relief convoys the rungs trigger~~ (RULED 2026-10-19, §6.2 PASS 7 PROXY DESIGN), ~~behaviour with the war switched off or under Nex~~ (RULED below),
    behaviour with the war switched off or under Nex.
  - **Build-time items already ruled:** add `arcadia_station`, `sphinx`, `eventide` to
    `nexConfig.conquestShieldMarkets`; durations as config; relief via
    `ExileHegLeagueRecoveryManager.record(...)` capped at half.

### 6.2 Black Fleet proxy ops [RATIFIED — Sean, 2026-08-26, after the direct-spawn-vs-handoff
question was resolved: single-spawner invariants (BF defeatability, caps, kill switch, FP ramp)
require the BF director to remain the only authority over BF fleets; the memory key is both
cheaper and lore-truthful]

Refined 2026-08-26 from the original broad-raid sketch, which had an attribution hole (a BF
raid on a Heg market is attributed to the BF — no mechanical reason it moves Heg–League
tension). The kept shape:

- **Proxy op = recovery-convoy hunting.** During DE_ESCALATION, when tension is falling, Midan
  directs the Black Fleet at the war's recovery convoys (§4.6). Deniability does real work:
  relief dying during a cool-down lets the victim's hawks blame the rival's hidden hand — each
  successful interception rolls a **misattribution chance** that bumps tension. One stroke slows
  physical recovery AND poisons political recovery; Midan stays two layers back.
- **Backfire counter:** each proxy op also rolls for *correct* attribution. Past a threshold of
  correctly-attributed interceptions: cooperation intel beat, real tension cut (attention pivots
  to the BF), long Midan-module lockout. V1 backfire is minimal — intel + meter effects only, no
  joint anti-BF fleet ops. Honesty note: "player-provokable" is thin in v1 (the player's lever
  is defending or ignoring convoys); engineering the backfire deliberately is Phase-2 material.
- **Decoupling seam:** the war director's Midan module never calls the BF director. It writes a
  directed-target **memory key**; the BF manager reads it on its own cadence and executes as its
  own op, not knowing who wrote it. One-way vocabulary, no control-flow coupling — the settled
  architecture holds.

**PASS 7 PROXY DESIGN — RULED [Sean, 2026-10-19].** Supersedes the "during DE_ESCALATION" timing
above, which clashed with R16's strike-triggered convoys.
- **The timing problem that drove it:** relief convoys sail 20–45 days after a strike, travel, then
  unload for 10 days; Black Fleet commerce raids launch every ~45 days and travel in from the far
  east. The existing `$exiles_hl_relief_target` key is written when a convoy SAILS, so a steered
  raid would usually arrive after the convoy had gone.
- **Which convoys — only SOME, decided at the key writer (Sean):** every relief convoy, either
  side's, in any war state, is *eligible*. When a strike creates a relief record, the war director
  rolls **0.5** (config) and writes a directive **only on success**. The Black Fleet never sees the
  others. **One directive active at a time.** Both sides' convoys count: if a League convoy dies, the
  hawks blame the Hegemony's hidden hand — Midan wants both sides angry.
- **The directive key [RULED]:** one Midan directive memory key **with a type field** —
  `CONVOY_AMBUSH` (this proxy) and `RAID_FOCUS` (the rung-1 follow-up fallback: commerce-raid target
  weighting, ×10 weight / ×2 feasibility relax). Self-expiring. Written by the war director, read
  only by the BF director.
- **The convoy ambush — a new Black Fleet operation [RULED; Sean's idea]:** the war director also
  marks relief convoy fleets with a read-only memory flag. The **BF director** checks daily; when a
  marked convoy enters the directive's target system, it spawns **one small fleet** near the
  system's edge or jump point, orders it to intercept that convoy, and afterwards sends it home or
  despawns it. Vanilla precedent: `DisposableFleetManager` (short-lived pirate/Path fleets spawned in
  systems near the player). Keeping the spawn inside the BF director preserves its kill switch, caps,
  FP ramp, defeatability and spoils accounting (the single-spawner rule).
  - Why it beats steering a commerce raid: timing is solved (it spawns on arrival); it is a readable,
    escortable beat ("pirates jumped the relief convoy"); relief convoys are real fleets, not
    abstract routes, so NPC battles resolve off-screen too.
  - **Size: ~40–60 FP, destroyer-capped** like BF raids (config). A relief convoy is ~48 FP with only
    10 FP of escort, so the ambush wins unless someone intervenes; struck markets usually have
    weakened defences.
  - Cost, honestly: a new BF op type (spawn, intercept order, cleanup, logging) — small and bounded:
    one fleet per directive, one directive at a time.
- **Counting a proxy kill:** the relief manager already tracks each convoy. A convoy destroyed in a
  battle involving the Black Fleet while a directive names its destination counts; kills by the
  player or the other side do not.
- **Numbers (starting values, config, each logged for measurement):**
  | Setting | Value |
  |---|---|
  | Directive chance per relief record | 0.5 |
  | Proxy kill blamed on the rival side | 0.4 → tension +6 (half a false-flag rung's +12) |
  | Correctly blamed on the Black Fleet | 0.6 → backfire counter +1 |
  | Backfire threshold | 3 correct attributions |
  | Backfire effect | intel beat ("both sides notice the Black Fleet hunting relief"), tension −10, counter resets |
  | Lockout after a backfire | 1 year, **proxy only** — the rungs keep their ruled pacing; Midan lies low with its proxy, not its war |
  | Ambush fleet size | ~40–60 FP, destroyer cap |

### 6.3 Phase-2 reserve

Nex's `DiplomacyManager.createDiplomacyEventV2(heg, league, "declare_war", null)` is a supported
programmatic war (re)trigger (it's what `InterventionIntel` uses, and it bypasses the 150-day
ceasefire block) [VERIFIED — Nex code]. Not used by the director; kept in the toolkit for a
Phase-2 quest beat.

**Quest idea — "the talks at the moment of Eventide" [Sean, 2026-10-19; half-baked, PARKED; rung 3
stays a director rung].** The player is hired to carry dignitaries to peace talks on one side and
stays on as security; all looks good until reports of the devastating attack on Eventide arrive.
The talks collapse, and the player is hired to get one set of dignitaries out post-haste.
- Claude's notes: the director already produces this beat — a rung landing during
  POLITICAL_INTERVENTION forces the talks to collapse (§6.1, LANDING IN THE WAR CYCLE) — so the
  quest would be the player's seat at a moment the war can create on its own. A possible tie: offer
  the quest when Midan is ready for rung 3, so the talks themselves become Midan's opening. Relates
  to Sean's reserved secret-envoy peace quest (CLAUDE.md §6, Phase 2) and to Cunningham's Kazeron raid
  (`EXILES_LORE_HISTORY.md` §4); both mind the R15 wording (a temporary ceasefire, never a "summit").

---

## 7. Nexerelin integration [VERIFIED — Nex 0.12.1d sources, 2026-08-26 analysis]

Sean has the Nex author's permission for AI reading of Nex source. **PERMISSION GRANTED —
Histidine (via Sean, 2026-08-27) — for the shipped suppression set:** the B2 runtime
relationship pin, the B3 self-heal, and the `$nex_npc_no_invade` conquest shields, as
described in §7.2–§7.3 (player-switchable, reversible, pair-specific). Pass 1's Nex work is
fully cleared. Full agent report archived in session; key findings and the decision below.

### 7.1 The actual threat model

- **Every Nex NPC↔NPC relation change funnels through one choke point:**
  `DiplomacyManager.adjustRelations(...)` (`DiplomacyManager.java:396`), bounded by
  `getMaxRelationship()` on entry/exit.
- **War weariness is a non-threat at defaults:** ~7,500 days (≈20 cycles) for Heg–League to
  reach ceasefire eligibility when they're each other's only countable enemy. (Caveat: Exiles'
  own directors may add countable enemies and accelerate this — watch in soak testing.)
- **The real risk is one random diplomacy event:** `"Star-crossed Lovers"`
  (`diplomacyConfig.json:121-130`) fires at HOSTILE (`maxRepLevelToOccur: inhospitable`),
  carries **no repLimit**, and one roll moves the pair from −0.5 to about −0.3 — no longer
  hostile, no war weariness needed, no telegraph. Low probability per ~20-day tick; high
  cumulative probability over a campaign. This is the mechanism that silently deletes the
  director's premise.

### 7.2 Decision: suppress, three layers deep [RATIFIED — Sean, 2026-08-26: all three layers,
side effects accepted (war-weariness exemption sector-wide; B3-only degradation if a player
disables `useRelationshipBounds`)]

> **AMENDMENT — RATIFIED same day (R11 rider): every Nex-behavior suppression must carry a
> player-facing true/false switch.** B1 is a static shipped file and cannot honor a runtime
> switch — and it is functionally redundant (B2's runtime pin alone clamps the pair below
> hostile and trips `canCeasefire` false; positive events fire but clamp to no effect).
> **B1 is therefore DROPPED.** The shipped stack is **B2 + B3 + the R11 conquest shields**, all
> runtime, each behind its own config switch in the `nex{}` block, and all **reversible on
> load** (switch off + our modifier/key present → remove it; the B2 accumulate-guard doubles as
> the removal hook). **Switches off = graceful ride:** B3 must not fight a Nex peace the player
> has allowed — the director idles while the pair is non-hostile and resumes if war resumes.
> Dropping B1 also deletes the untested merge-semantics risk (test D1 obsolete). The §7.2 table
> below is retained as research record; B1's row is superseded.

Ride was rejected as primary: a Nex-brokered peace erases the war's *history* (weariness zeroed,
ceasefire records set), leaving a resuming director with no continuity, and it couples the
escalation stack to a Nex-only API. The recommended stack — all three additive, none conflicting:

1. **B1 — config pin** (1 JSON file, 0 Java): ship a merged
   `data/config/exerelinFactionConfig/hegemony.json` with
   `"maxRelationships": {"persean": -0.51}` + `"diplomacyPositiveChance": {"persean": 0}`.
   −0.51 is Nex's own canonical pin value (`canCeasefire()` tests ≤ −0.5); positive-chance 0
   kills **every** positive event including starcrossed at the source, and independently trips
   `canCeasefire` false. ⚠ **Untested merge semantics:** whether Nex's config merge is key-wise
   or wholesale-replace for nested objects is unverified — if wholesale, we'd silently drop
   Nex's own `remnant` pin; test D1 below decides, and B1 must then re-declare Nex's entries.
2. **B2 — runtime pin** (~25 lines, Nex-only lazy-loaded class):
   `DiplomacyManager.modifyMaxRelationshipMod("exiles_hegleague", -1.6f, "hegemony", "persean",
   ...)`. Persists in save; immune to the merge question; **the only lever that works in
   FLATTEN start-relations mode**. ⚠ It **accumulates rather than sets**
   (`stat.modifyFlat(id, mod + current)`) — guard with a persistent once-flag. Lazy-load
   direction is safe (Exiles loose script importing Nex classes works; only the reverse breaks).
3. **B3 — self-heal backstop** (~15 lines, zero Nex imports, works in the non-Nex build too):
   the director polls `heg.isHostileTo(persean)` on its existing interval and re-asserts
   hostility on violation, with a log line. Cannot be defeated by any Nex setting; reacts rather
   than prevents, so it's the backstop, not the primary.

**Named side effect:** once `canCeasefire(heg, persean)` is false, this war stops counting
toward either faction's war weariness, leaving both *more* willing to start other wars. Pulls
in the "Sector is Breaking" direction, but it's a sector-wide behavior change — named here so
it's a decision, not a playtest surprise.

### 7.3 Double activity and conquest risk

- **Nex runs its own Heg–League shooting war** (`InvasionFleetManager` accrues invasion points
  for both and launches invasions/raids). Expect double activity; size it in the soak test
  (D5) before tuning the director's own cadence. Potentially a feature (free fleet-level war
  under Nex) — but never assume the director is the only source of Heg–League fleets.
- **Ownership protection — RULED [Sean, 2026-08-26]:** `$nex_npc_no_invade` (NPC invasion/raid/
  sat-bomb/rebellion suppressed; **player** invasion still allowed — unlike `$nex_uninvadable`)
  goes on a **minimal set: the two capitals (Chicomoztoc, Kazeron) + the false-flag
  permanent-strike targets** (Eventide + Sean's pending list). Nothing else — the director is
  flip-tolerant by design (R6c live queries); blockade-whitelist entries that fall just leave
  the pool; rebellion risk on ordinary targets is measured by soak test D6 first. Rationale:
  the B2/B3 pins keep the pair hostile but do nothing against decapitation-by-conquest; the
  shields keep the war's poles standing while preserving player agency to topple them.
  Config-switched and reversible per the 7.2 amendment; keys applied idempotently on load,
  same pattern as `applyRefugeeConquestShield()`.
  **[VERIFIED — Nex author (Histidine via Sean, 2026-08-27):** `$nex_npc_no_invade` blocks
  invasions by ALL NPC parties, including third parties at war with Heg/League — not just the
  Heg↔League pair. This matches our intent (no NPC hand may remove the war's poles or spoil the
  strike-target story beats; only the player can). Noted cost: in a long campaign the shielded
  handful of worlds never fall while the sector churns — accepted; the config switch covers
  players who object.]
- **Rebellion is the deciv vector we feed:** Nex accrues rebellion points daily on any market
  below stability 5, and Exiles war pressure drives exactly that. Against CLAUDE.md priority
  #4 this is the interaction most likely to produce accidental collapse — soak-test item D6.

### 7.4 Separate finding — FLATTEN gap in shipped compat [RULED 2026-09-12]

**Sean's ruling:** require normal/default starting relationships for the authored Exiles campaign;
FLATTEN is unsupported. Disclose setup restrictions and overrides to players before campaign
commitment and in setup documentation. Do not silently replace their selection. No runtime change
was made by this ruling. Canonical policy: `EXILES_NEXERELIN_COMPAT.md`, setup compatibility section.
The options below are retained as historical research, not an open decision.

`DiplomacyManager.resetFactionRelationships()` (`:1493`) does **not** honor
`noRandomizeRelations` — Nex's FLATTEN start-relations mode zeroes Exiles' entire scripted
relation matrix (Midan, Omicron, Ashen, refugees), which the existing compat audit (item 3)
treats as protected. **It is not protected against FLATTEN.** Options: declare FLATTEN/RANDOM
unsupported (cheapest, consistent with the random-sector precedent) or re-assert the Exiles
matrix post-start (also covers Heg–League as a bonus). Decision belongs with
`EXILES_NEXERELIN_COMPAT.md`, not this director — logged there when ruled.

### 7.5 Nex live-test list (feeds §14 test matrices)

- **D1 — OBSOLETE** (B1 dropped per the 7.2 amendment; no config merge shipped, no merge
  semantics to test).
- **D2:** `canCeasefire("hegemony","persean")` → false (driven by B2's runtime pin alone).
- **D3:** force `starcrossed` via Nex console `CreateDiplomacyEvent hegemony persean
  starcrossed` → relationship must not move.
- **D4 — B2 idempotency:** save/load ×3 → `$nex_max_relations` flat mod reads −1.6, not −4.8.
- **D5 — soak (~5 cycles, dev speed):** no Heg–League ceasefire/peace intel; count Nex-launched
  Heg↔League invasions/raids to size double activity.
- **D6 — rebellion pressure:** run war ops against a Heg/League core world; watch
  `$nex_rebellionPoints` there.
- **D7 — start-relations modes:** behavior under default / flatten / randomize per the matrix in
  the agent report; author ruling on supported modes (§7.4).

### 7.6 How a standard Nex war compares with the director [VERIFIED — Nex 0.12.1d sources, 2026-10-20; reference, no ruling]

Read from `ExerelinCore.jar` sources plus `data/config/exerelin/strategicAIConfig.json`,
`data/config/exerelinFactionConfig/*.json`, `exerelin_config.json` and Nex `settings.json`.
Sean asked how closely the director mirrors an ordinary Nex war.

**How Nex decides.** Two layers: the **Strategic AI** (`enableStrategicAI: true` by default)
keeps per-faction *concerns* (Retaliation, General Warfare, Vulnerable Enemy Target,
Revanchism, Commodity Competition, …) that unlock *actions* by matching tags; the older
**invasion-points spawner** (`InvasionFleetManager`) still runs as a backstop, alternating
invasion and raid, but needs 2× the points when a faction has a Strategic AI.

**What a Nex war contains (same menu for every faction):**

| Category | Operation | What it does |
|---|---|---|
| Offensive fleets | **Invasion** | Ground battle, market changes owner; wartime only; the loser may **counter-invade** (Revanchism) |
| | **Raid** (`NexRaidActionStage`) | Beat defenders → **disrupt the station** → disrupt one industry + up to −3 stability via ground raid |
| | **Blockade** (`NexBlockadeFGI extends BlockadeFGI`) | Min ~240 FP (×1.2 +120). **Rare:** only the Commodity Competition concern carries `canBlockade` (boosted by `trait_monopolist`), plus `nex_raidToBlockadeConversionFreq` 0.2 of older-path raids |
| | Base strike / Pirate raid | Vs pirate and Pather bases / peacetime-capable, favours `devious` |
| | Saturation bombardment | **Off by default** (`allowNPCSatBomb: false`) |
| Covert (NPC agents) | Sabotage industry, destabilize, destroy commodity stocks, instigate rebellion, raise/lower relations | |
| Defensive | Response fleets (reserve joining station fights), **Special Forces** task groups (patrol, defend vs raids, assist raids, counter ground battles), defence fleets in invasion battles, Build Defense | |
| Diplomatic / economic | Declare war, war weariness → peace, alliances, intervention, coalitions; build economy/heavy industry; rebellions (daily points below stability 5) | |

**Faction differences are weighting, not different tools.** Alignments scale concern/action
priority by category (military/economy/diplomacy); `trait_X` / `!trait_X` tags on concerns and
actions raise or lower priority for factions with that trait (`SAIUtils`). Exact multipliers
not traced.

| Faction | Alignments | Traits | Practical lean |
|---|---|---|---|
| Hegemony | militarist 1, hierarchical 1, diplomatic −0.5 | paranoid, **stalwart**, law_and_order, dislikes_ai, envious | Stalwart deprioritizes the war-weariness peace concern (keeps fighting); hunts pirate bases |
| Persean League | **diplomatic 1**, corporate 0.5 | helps_allies, **irredentist**, envious | Diplomacy-first, joins allies' wars, retakes lost markets |
| Tri-Tachyon / Diktat | — | **monopolist**, devious (+ paranoid for the Diktat) | Most likely to blockade and run peacetime pirate raids |

Neither Hegemony nor League is monopolist, so Nex itself rarely blockades this pair: **under Nex,
most Heg–League blockades are ours.**

**Mapping to the director:**

| Nex | Director |
|---|---|
| Raid | **Surgical strike** (§4.3) — same shape, aimed at military industries, longer disruption + relief convoys (§4.6) |
| Blockade (vanilla `BlockadeFGI`, rare) | **System blockade** (§4.2) — same vanilla seam, deliberate, collateral split |
| — | **Interdiction raids** (§4.1) and the **planetary squeeze** (§4.4): no Nex equivalent |
| Invasion / counter-invasion | **Deliberately omitted** — conquest shields (§7.3); the war never ends by conquest (controlled collapse) |
| Response fleets, Special Forces, Build Defense | **None** — the defender has only existing patrols/stations, plus relief after a strike |
| Covert agents, rebellion | None (Midan's false flag, §6.1, is the nearest analogue, and it is a story beat) |
| War weariness → peace | Weariness ledger → **temporary ceasefire**, never peace (R15) |
| No narrative arc | Tension meter + six-state crisis cycle, General Engagement beats, player interception, Midan |

**Assessment (Claude, 2026-10-20):**
1. The director mirrors the **harassment** half of a standard war and gives it a legible
   escalate/cool shape Nex lacks; it deliberately omits the **decisive** half (invasions) and
   has no **defensive** or **covert** layer.
2. **Under Nex the layers stack:** the B2 pin keeps the pair hostile permanently, so Nex's own war
   machine never stops — Nex supplies invasions, defences and agents; the director supplies
   blockades, squeezes, interdictions and the story. Size this in soak test D5 (§7.5).
3. **Without Nex the war is harassment only** — no market changes hands and nobody fields a
   defence. Consistent with the design, but the largest difference from a "standard" war.
4. **Observation, not a recommendation:** the director gives both sides the same op menu; Nex
   differentiates a stalwart, militarist Hegemony from a diplomatic, irredentist League. If side
   doctrines are ever wanted, Nex's trait-weighting pattern is a cheap model — it would touch
   playtest-earned pacing, so author ruling first.

---

## 8. New market conditions

| Condition | id | Effect | Lifecycle |
|---|---|---|---|
| Interdiction (name TBD — author) | `exile_hl_interdiction` | Accessibility penalty + availability hits on supplies/fuel/top imports (via `ExileCommodityShortageCondition`) | Applied day N of presence op; lifts on withdrawal/abort; hard self-expiry |
| Planetary Blockade (name TBD — author) | `exile_hl_planetary_blockade` | Accessibility −0.5 flat + ×1.4 player buy price on every commodity (via `ExileTimedCondition` + a per-commodity `CommodityOnMarketAPI.getPlayerSupplyPriceMod()` loop) | Applied day 5 of the squeeze presence to THE ANCHOR MARKET ONLY; lifts on withdrawal/abort; hard self-expiry at 80d — **BUILT 2026-09-20, §14.11.6** |
| Relief Effort (name TBD — author) | `exile_hl_relief_effort` | **None** — purely informational marker on a market being repaired by a recovery convoy | Applied at orbit start, removed at departure, **only when `recoveryConvoys.reliefEffortCondition` is true (ships FALSE)**; orphan self-expiry — **BUILT 2026-09-20, §14.11.6** |
| System blockade | — (vanilla `Blockaded`) | Vanilla accessibility penalty, driven by `BlockadeFGI.getAccessibilityPenalty()` | Owned by the vanilla FGI lifecycle — no custom condition |

New ids get rows in `data/campaign/market_conditions.csv` — Exiles occupies `order` 149–160 as of
Pass 6; continue from 161. Player-facing names/descriptions are author material.

**CORRECTION (Pass 6, D80.10 item 1):** `getPlayerSupplyPriceMod()` is a method on
`CommodityOnMarketAPI`, **not** on `MarketAPI`. The row above named it correctly only by accident
of brevity; the implementation is a loop over `market.getCommoditiesCopy()`, with a matching
`unmodify(id)` loop in `unapply()`.

---

## 9. Config schema and testing conventions

New top-level block in `data/config/exiles/director_config.json` (naming open — proposed
`"exilehegleague"`; it's a director key, not a faction id, since both factions are vanilla).
Pseudo-schema (values illustrative, not tuned — **every FP number ships with a measurement log
line before tuning**):

```
"exilehegleague": {
  "enabled": true,
  "debugMessages": true,          # TEST — false for production
  "timeScale": 1.0,               # compresses clocks, not physics/dice
  "testing": { },                 # forceState, forceIncident, etc. — Ashen §9.1 pattern
  "tension": {
    "start": 25, "floor": 15,
    "decayPerDay": 0.03, "deescalationDecayMult": 4.0,   # 0.05 -> 0.03, 2026-09-13
    "hawkThreshold": 60,          # RETALIATION -> ESCALATION branch
    "warningThreshold": 45,       # 2026-09-13: below it the cycle is CONTAINED (sec 3.2.5)
    "generalEngagementThreshold": 85,   # Option A only, if ruled in
  },
  "weariness": { "decayPerDay": 0.10, "standDownWeight": 0.02,     # decay 0 -> 0.025 (09-13) -> 0.10 (09-19)
                 "perStrikeSuffered": 12, "perInterdictionSuffered": 4, "perOpFailed": 8 },  # NPC feed, 2026-09-19
  "cycle": {
    "tensionDwellDaysMin": 60, "tensionDwellDaysMax": 180,
    "retaliationLagDaysHeg": 20, "retaliationLagDaysLeague": 10,
    "escalationWindowDays": 45, "generalEngagementDays": 75,
    "interventionOutcomeWeights": { },   # ceasefire holds / partial / collapses
    "deescalationDays": 30,
    "preemptiveCeasefireChance": 0.30,   # 2026-09-13: THE DIAL -- warning-band roll (sec 3.2.5)
  },
  "hegemony":  { "opWeights": { "blockade": ..., "surgical": ... }, "maxConcurrentOps": 2, ... },
  "persean":   { "opWeights": { "interdiction": ..., ... },        "maxConcurrentOps": 2, ... },
  "interdiction":       { "enabled": true, "spawnFpMin": ..., "spawnFpMax": ...,
                          "maxShipSize": 2, "presenceDays": ..., "feasibilityMult": ...,
                          "systemCooldownDays": ..., "bountyPerFrigate": ... },
  "blockade":           { "enabled": true, ... },   # no maxShipSize — pipeline has no lever
  "surgicalStrike":     { "enabled": true, "industries": [...], "disruptDaysMin": ...,
                          "extendedDisruptMult": 2.0, ... },   # war-scale wound; convoys drip it back to design-normal
  "planetaryBlockade":  { "enabled": true, ... },
  "recoveryConvoys":    { "enabled": true, "maxActive": ..., "unloadDays": 10,
                          "recoveryDaysPerConvoy": ..., "minRemainingDays": ..., ... },
  "playerInterception": { "enabled": false, ... },  # own kill switch — HIGH RISK pass
  "midan":              { "falseFlag": { "enabled": false, "tensionFloorDays": ..., "cooldownDays": ... },
                          "bfProxy":   { "enabled": false, "backfireThreshold": ... } },
  "exemptSystems": [ "aztlan", "thule" ],   # SYSTEM-level capital exemption (R6a)
  "blockadeWhitelistSystems": [ ],          # AUTHOR: lore-driven list pending (R6b)
  "planetaryBlockadeWhitelistMarkets": [ ], # AUTHOR: lore-driven list pending (R6b)
  "excludedMarkets": [ "asharu", "mairaath", "port_tse", "salamanca", "new_maxios" ],  # strain markets (R6b)
  "distanceFalloff": ...,                   # pool weighting (R6c)
  "nex": {                                  # ALL Nex-behavior suppression is player-switchable (R11 rider)
    "relationPin": true,        # PLAYER OPTION — B2 runtime max-relationship pin; off = removed on load
    "selfHealHostility": true,  # PLAYER OPTION — B3 re-assert; off = director rides Nex peace (idles)
    "conquestShields": true,    # PLAYER OPTION — $nex_npc_no_invade on capitals + strike targets; off = keys removed
  },
}
```

Conventions (all inherited, all binding): every sub-block has its own `enabled`;
`timeScale` + `testing{}` over per-value test tunes (ruled — no BF-style restore checklists);
transient config reloaded per launch so edits land without a new game; comments in the JSON are
load-bearing documentation — match the existing density; anti-hotspot cooldown key
`$exiles_hl_op_cooldown` (own idiom copy, own key; pick launch-start vs end-start deliberately);
never move/rename/delete an existing player-facing setting without Sean's approval.

⚠ **Blockades cannot be tested at `timeScale` 0.1** — travel is uncompressed while the payload
window compresses; it never gets past TRAVEL. Use `timeScale 1` for blockade tests (cost two
sessions on Ashen). `forcePhase`-style testing keys **pin**, not jump.

---

## 10. Rulings — RESOLVED (inherited or already settled)

| # | Topic | Ruling |
|---|---|---|
| S1 | Architecture | Fourth sibling on vanilla RaidIntel pipeline; unified director rejected — do not re-propose (2026-08-24 / Ashen §14.5–14.6) |
| S2 | Background lore | Two-theft nanoforge chain, both real (Sean, 2026-08-23) — §1.1 |
| S3 | Blockade infrastructure | Vanilla `BlockadeFGI` stack; never build from scratch (Ashen §6.4) |
| S4 | Shared feasibility/cooldown helpers | Rejected on the merits; copy the idiom per director (Ashen §14.6) |
| S5 | Condition ids | One id per director; never shared (Ashen ruling E.c) |
| S6 | Skirmish size cap | Destroyer (`maxShipSize: 2`) — already the ratified pattern for BF raids/Ashen interdictions, for this exact reason |
| S7 | Capital exemption | Aztlan + Kazeron untargetable; no planetary bombardment; no conquest/deciv (design doc) |
| S8 | Naming | All new files `Exile*`, all ids `exile_hl_*` / `$exiles_hl_*` (design doc + CLAUDE.md §7; `AshenBlockade` et al. are naming debt, not precedent) |
| S9 | Player-interception dialogue path | Runtime `InteractionDialogPlugin` via `pickInteractionDialogPlugin` — verified, no jar needed; Ruling-F mitigations mandatory (Ashen §6.4a.1) |
| S10 | Test compression | `timeScale` + `testing{}` block; no per-value restore checklists (Sean) |
| R1 | "Real shooting war" | **A + B** (Sean, 2026-08-26): rare bounded General Engagement super-state in the director; the true containment-breaking war is quest-gated finale content only — §3.2.4 |
| R2 | War's end | **NO** (Sean, 2026-08-26): the director's loop never permanently ends the war — permanent tension floor, always returns to TENSION. True resolution is quest content (the grievance is only dissolvable by exposing Midan's false flag). Director still idles on external premise loss (§3.3); quests may later write the meter / retire the director — that seam is reserved |
| R3 | XIV hulls | **NO sweep** (Sean, 2026-08-26): destroyer cap is the only filter — rare natural Enforcer XIV appearances are acceptable; no `XIV_bp` filtering — §5 |
| R4 | Player interception dialogue | **V1, final ops pass** (Sean, 2026-08-26): isolated Pass 8, own kill switch, shippable without it; Ruling-F mitigations mandatory — §4.5 |
| R5 | Supply fleets | **Recovery convoys only, mechanics locked** (Sean, 2026-08-26): extended-baseline downtime + dripping convoy subtraction over ~10-day orbit + recovery floor; nothing written on death; unrest-decay = build-time check; war-fleet resupply CUT — §4.6. Open sub-item: informational "Relief Effort" condition (proposed, pending nod) |
| R6 | War geography | **RULED in three parts** (Sean, 2026-08-26): (a) capital exemption is SYSTEM-level — Aztlan + Thule whole; (b) strain markets never direct victims, and blockades (both kinds) target ONLY an author whitelist — **lore-driven list PENDING from Sean**; (c) other ops use the non-exempt enemy-market pool, distance-falloff weighted, config allow/deny — §2 |
| R7 | Blockade fleet composition | **ACCEPT vanilla uncapped** (Sean, 2026-08-26): no size-cap attempt; cruisers/capitals (incl. natural XIV) legitimate in blockade fleets — §4.2 |
| R8 | Midan false-flag | **APPROVED, ambient v1 capped** (Sean, 2026-08-26): state-gated INCIDENT entry (fires when de-escalation winning), long cooldown + per-campaign cap, leaves evidence each firing — §6.1. Target candidate list = author material, pending |
| R9 | BF proxy ops | **APPROVED, refined shape** (Sean, 2026-08-26): proxy = recovery-convoy hunting during De-escalation; misattribution bumps tension, correct-attribution threshold triggers minimal backfire; cross-director contact ONLY via memory-key handoff (BF director stays sole BF spawner) — §6.2 |
| R8b | False-flag payloads | **FUSED** (Sean, 2026-08-26): each capped false-flag firing deals a curated permanent world-state wound (Eventide solar array = candidate #1); 6d machinery; Nachiketa-rhyme evidence; BF permanent strikes and grander events scoped OUT (BF doc / Phase 2) — §6.1 |
| R10 | Nex premise protection | **APPROVED: suppress stack** (Sean, 2026-08-26), shipped in Pass 1; war-weariness side effect and bounds-off degradation accepted. **Amended same day by R11 rider: B1 dropped; final stack = B2+B3, config-switched, reversible** — §7.2 |
| R11 | Nex conquest shields + switchability rider | **Capitals + strike targets only** (Sean, 2026-08-26), `$nex_npc_no_invade`, player invasion preserved; **rider: every Nex suppression behind a player-facing switch, reversible, switches-off = graceful ride** — §7.2 amendment, §7.3 |
| R12 | Naming | **APPROVED** (Sean, 2026-08-26): config key `"exilehegleague"`; classes `ExileHegLeague*`; ids `exile_hl_*`; memory keys `$exiles_hl_*`. Player-facing display names deferred to the author deliverable; placeholders clearly marked until then |
| R13 | Containment amendment | **RATIFIED** (Sean, 2026-09-13): RETALIATION exit becomes contained / warning-band ceasefire roll / escalation; `warningThreshold` 45, `preemptiveCeasefireChance` 0.30, `tension.decayPerDay` 0.03. Floor, hawk line, cuts and guardrails unchanged — §3.2.5 |
| R14 | Weariness decay | **YES** (Sean, 2026-09-13): `weariness.decayPerDay` 0 → 0.025; the "purely cumulative" reading is re-ruled — §3.2.5 item 1 |
| R15 | Crisis-end terminology | **TEMPORARY CEASEFIRE** (Sean, 2026-09-13), never "summit". Peace talks are the reserved Phase-2 secret-envoy quest seam — §3.2.5 item 3 |
| R16 | Recovery-convoy trigger | **STRIKE-TRIGGERED** (Sean, 2026-09-13; option B), amending the §4.6 locked mechanics. Still Pass 6 to build — §3.2.5 item 5, §4.6 |

## 11. Open rulings — FOR SEAN

| # | Question | Options / recommendation |
|---|---|---|
| ~~R1~~ | ~~"Real shooting war"~~ | **RULED 2026-08-26 — moved to §10** |
| ~~R2~~ | ~~Director ends war?~~ | **RULED 2026-08-26 (NO) — moved to §10** |
| ~~R3~~ | ~~XIV hulls~~ | **RULED 2026-08-26 (no sweep) — moved to §10** |
| ~~R4~~ | ~~Interception dialogue~~ | **RULED 2026-08-26 (v1, final ops pass) — moved to §10** |
| ~~R5~~ | ~~Supply fleets~~ | **RULED 2026-08-26 (recovery convoys, mechanics locked) — moved to §10** |
| ~~R6~~ | ~~War geography~~ | **RULED 2026-08-26 — moved to §10. Outstanding deliverable: Sean's lore-driven blockade whitelist** |
| ~~R7~~ | ~~Blockade fleets uncapped~~ | **RULED 2026-08-26 (accept) — moved to §10** |
| ~~R8~~ | ~~Midan false-flag~~ | **RULED 2026-08-26 (approved, ambient v1 capped) — moved to §10** |
| ~~R9~~ | ~~BF proxy + backfire~~ | **RULED 2026-08-26 (approved, refined) — moved to §10** |
| ~~R10~~ | ~~Nex premise protection~~ | **RULED 2026-08-26 (B1+B2+B3) — moved to §10** |
| ~~R11~~ | ~~Nex conquest shields~~ | **RULED 2026-08-26 (capitals + strike targets; switchability rider) — moved to §10** |
| ~~R12~~ | ~~Naming~~ | **RULED 2026-08-26 (approved) — moved to §10** |

**All R1–R12 RULED 2026-08-26. Remaining open items are AUTHOR DELIVERABLES + one nod:**

1. **The merged target list [Sean, lore-driven, in progress]:** blockade whitelist systems (R6b),
   planetary-blockade whitelist markets (R6b), false-flag permanent-strike targets with each
   strike's effect (R8b; Eventide solar array = candidate #1).
2. **Player-facing text [Sean]:** condition display names + descriptions, summit/operation intel
   titles, the false-flag fleet's in-fiction designation. Rides with item 1.
3. **"Relief Effort" informational condition (§4.6):** **PLANNED, SWITCH-GATED, DEFAULT OFF —
   still awaiting Sean's nod** (Pass 6, D80.7). `ExileHegLeagueReliefEffort`
   (`exile_hl_relief_effort`, CSV order 160) is built as a zero-effect `ExileTimedCondition`, and
   the recovery manager applies and removes it **only** when
   `recoveryConvoys.reliefEffortCondition` is true. That key ships `false`, so a CSV row that
   nothing applies is inert and the pass is not gated on the answer. Flipping it on is one config
   edit and no code change.

**Pass 1 is unblocked** — nothing above gates the skeleton, the crisis engine, or the Nex work.
Item 1 gates the blockade passes (4/6) and the Midan pass (7) target config only.

## 12. Build passes [PROPOSED]

| # | Pass | Contents | Risk |
|---|---|---|---|
| 1 | Skeleton + premise pins | Director class, tension meter + state machine ticking with debug messages only (no ops), config block, registration/reload guards, Nex B2+B3 + conquest shields (all switch-gated + reversible, per R10/R11) + Nex console tests D2–D4 | Low |
| 2 | Interdiction raids | Presence-raid port + `exile_hl_interdiction` condition + per-kill bounty; TENSION state becomes real | Low-medium (third port of a proven pattern) |
| 3 | Surgical strikes + Retaliation wiring | `disruptFor` strikes; INCIDENT table (non-Midan entries) + telegraph intel; RETALIATION branch logic — **BUILT 2026-08-27, §14.4** | Medium |
| 4 | System blockades | `ExileHegLeagueBlockade` + launch plumbing; ESCALATION state becomes real | Medium (blockade test discipline: timeScale 1) |
| 5 | Full cycle + asymmetry | POLITICAL_INTERVENTION outcome roll + intel beats; DE_ESCALATION; per-faction weights/lags; General Engagement if R1=A — **BUILT 2026-09-20, §14.10** (everything but the intel beats had already landed in Passes 1–4; §14.10 lists where each piece lives) | Medium |
| 6 | Planetary blockade + recovery convoys | §4.4 condition + squeeze op; §4.6 convoys — **strike-triggered per R16 (2026-09-13)**, not DE_ESCALATION-keyed — **BUILT 2026-09-20, §14.11** (Janino-clean, UNTESTED IN GAME; `surgicalStrike.extendedDisruptMult` moved 1.0 → 2.0 in the same commit, as §14.11 required) | Medium |
| 7 | Midan injection | False-flag ladder (3 rungs) + BF proxy + backfire — all design RULED (§6.1–§6.3, 2026-10-08 → 10-19); **build plan: `docs/EXILES_PASS7_BUILD_PLAN.md` (DRAFT 2026-10-20, under review)** | Medium-high (largest pass) |
| 8 | Player interception dialogue | §4.5, isolated, own kill switch (pending R4) | **High — deliberately last op pass** |
| 9 | Review + config sweep | Independent review by a DIFFERENT MODEL alongside remaining behaviors (per the standing Ashen 7.5 convention); config externalization audit. **Preceded by the post-Pass-7 complexity review (Sean, 2026-11-07; agenda in the Pass 7 plan), so the reviewer sees the simplified director** | — |

Each pass: Janino-check offline (`tools/janino-check.ps1`), live test matrix in §14, findings
logged verified-by-code vs needs-live-test.

## 13. Reuse map

| Need | Source | Adaptation |
|---|---|---|
| Blockade lifecycle + condition | vanilla `BlockadeFGI`/`FGBlockadeAction`/`econ.Blockaded`; `AshenBlockade` (+`SpreadBlockadeAction`) | Thin subclass; parameterize faction/targets; keep both load-bearing flags |
| Presence raid + condition timing | `BlackFleetCommerceRaidStage` → `AshenInterdictionStage` | Third port (~80% reuse measured) |
| Timed condition base | `ExileTimedCondition` | Use as-is; new ids |
| Shortage condition base | `ExileCommodityShortageCondition` | Use as-is; add accessibility in own apply/unapply |
| Player buy-price mechanism | `ExilePriceSpikes` (mechanism only) | New subclass; drop BF lifecycle/refugee carve-out |
| Industry disruption | `AshenCulannStrikeStage.disruptFor` + copy of `OmicronRaidActionStage.isMilitaryIndustry` | Copy (private static) |
| Feasibility gate | idiom from any sibling `pickTarget()` | Own copy + instrumentation line (S4) |
| Live-op dogpile check | `ExileLiveOps.getSystemsWithActiveRaids` | Use as-is; write own counters inline |
| Deciv/Battered guard | `ExileUnrestGuard` (+`ExileEmergencyMeasuresConfig.pressureDays`) | Use as-is; always via the guard |
| Protected factions | `ExileProtectedFactions` | Use as-is in getTargets + canRaid |
| Per-kill bounty | `BlackFleetRaidIntel.reportBattleOccurred` port | Payer = raided faction; blockade fleets need RAIDER_TAG + listener |
| Hidden intel / telegraph | sibling `setHidden(true)` pattern; Ashen Pass-7 telegraph | Skip source-concealment overrides (not wanted here) |
| Fleet size cap | `FleetParamsV3.maxShipSize` in `createFleet` override | Cap = 2; no XIV sweep (R3 ruled) |
| Fleet-shape (count vs size) | `maxFleetFp` ceiling pattern (Ashen `HiddenSourceAssembleStage`) | Ceiling, not divisor |
| Config load + transient pattern | Ashen/BF inline `loadConfig()` or `OmicronDirectorConfig` class form | Either; dedicated class suggested at this option count |
| Registration/reload | `ExilesModPlugin` onNewGameAfterEconomyLoad + onGameLoad guard | Standard |
| Nex conquest shield pattern | `applyRefugeeConquestShield()` memory-key idiom | `$nex_npc_no_invade` variant per R11 |
| Nex premise pins | `exilemidan.json` maxRelationships precedent; `modifyMaxRelationshipMod` | B1+B2+B3 per §7.2 |

---

## 14. Build log

*(Append-only.)*

### 14.1 Pass 1 — Skeleton + premise pins [BUILT 2026-08-27; UNTESTED IN GAME]

Janino-clean, registered, and **launching nothing**. States transition, the meter moves, debug
lines print. Everything else in §12 is still unbuilt.

#### What was built

| File | Status | Purpose |
|---|---|---|
| `data/scripts/exiles/ExileHegLeagueDirector.java` | NEW | The fourth sibling. `EveryFrameScript` on an `IntervalUtil(0.1, 0.2)` tick: Tension meter (§3.2.1), six-state crisis cycle (§3.2.2), General Engagement super-state (§3.2.4 A), idle conditions (§3.3), the public hooks later passes call, and the once-per-load Nex layer sync. |
| `data/scripts/exiles/ExileHegLeagueConfig.java` | NEW | Dedicated config class per the §13 reuse map (`OmicronDirectorConfig` shape). Transient, reloaded per launch, `timeScale` applied at load. |
| `data/scripts/exiles/ExileHegLeagueNexSupport.java` | NEW | **Zero Nexerelin types.** Nex detection, B3 self-heal, R11 conquest shields, the premise queries, and the by-name dispatch to B2. |
| `data/scripts/exiles/ExileHegLeagueNexHook.java` | NEW | Nex-free interface — the seam that keeps `exerelin.*` off the startup script graph. |
| `data/scripts/exiles/ExileHegLeagueNexPin.java` | NEW | **The only file that imports Nexerelin.** B2 runtime max-relationship pin, apply/remove/read. Loaded by NAME only. |
| `data/scripts/exiles/ExilesModPlugin.java` | MODIFIED | Registration in `onNewGameAfterEconomyLoad` + `hasScript` guard in `onGameLoad`, mirroring the Ashen/BF siblings. Two additions, nothing else touched. |
| `data/config/exiles/director_config.json` | MODIFIED | New top-level `"exilehegleague"` block appended after `"exilerefugee"`. No existing key was moved, renamed, or deleted. |

#### Janino check

`tools/janino-check.ps1` — **5 of 5 EXIT=0**: `ExileHegLeagueConfig`, `ExileHegLeagueNexHook`,
`ExileHegLeagueNexSupport`, `ExileHegLeagueDirector`, `ExilesModPlugin`.

`ExileHegLeagueNexPin` **fails the standard wrapper on purpose** —
`ClassNotFoundException: exerelin.campaign.DiplomacyManager`. That failure is the *proof of the
design*, not a defect: it demonstrates that any compile-time reference to this class from the
director would drag `exerelin.*` into the director's own compile and break game start in a
non-Nex install. It was checked separately with `Nexerelin/jars/ExerelinCore.jar` appended to
the wrapper's classpath — **EXIT=0**, alongside `ExilesNexStart`, which fails and passes the
same two ways for the same reason (the established precedent).

`director_config.json` was parsed end-to-end with the game's own `json.jar` (comments stripped
first, since stock `org.json` lacks Starsector's comment pass): 5 top-level blocks, 13 keys in
`exilehegleague`, and every value the code reads resolves as expected.

#### How the Nex-absence guard works

Three independent barriers, so no single mistake can reach a Nexerelin class without Nexerelin:

1. **`ExileHegLeagueNexPin` is never named as a type.** It appears in exactly one place, as the
   string constant `PIN_CLASS` in `ExileHegLeagueNexSupport`. Janino compiles loose scripts
   lazily and per class, and it resolves referenced types when compiling the *referring* class —
   so a string reference means the file is never compiled in a non-Nex install. The green
   `ExileHegLeagueNexSupport` check above is the direct evidence: it compiled without pulling
   the pin class in.
2. **`isModEnabled("nexerelin")` gates the load** before `getScriptClassLoader().loadClass(...)`
   is ever called (the same lazy path Nex uses to instantiate `ExilesNexStart`).
3. **Everything is wrapped in `try/catch (Throwable)`** and returns a status flag. A Nexerelin
   whose API has moved degrades to the B3 self-heal backstop with a log line, never a crash.

B3 and the conquest shields need no guard at all — B3 touches only vanilla relationships, and
the shields write market memory keys that vanilla ignores (the `applyRefugeeConquestShield()`
idiom, copied).

**Reversibility (R11 rider), all three layers, all on load:**
- B2: `nex.relationPin` off + persistent `$exiles_hl_nex_pin_applied` present → `unmodifyFlat`
  our modifier id, clear the flag. The same persistent flag is the accumulate guard (test D4).
- B3: `nex.selfHealHostility` off → the director stops re-asserting and **idles while the pair
  is at peace**, resuming if war resumes. The ratified graceful ride; it never fights a peace
  the player allowed.
- Shields: our own marker `$exiles_hl_nex_shield` rides alongside `$nex_npc_no_invade`, so the
  load-time sweep removes only shields Exiles installed. Switching off **or removing an id from
  `conquestShieldMarkets`** unshields on the next load.

#### Debug output and cadence

All on `debugMessages: true` (message window, yellow, `DEBUG:` prefix) and mirrored to the log:

| Line | When |
|---|---|
| `Heg-League tension initialised at N.` | First tick of a new campaign |
| `Heg-League -> <state> (Nd, tension T, cycle C) [offender X, victim Y]` | Every state entry |
| `Heg-League: GENERAL ENGAGEMENT declared (tension T, N consecutive hot cycles).` | On entering ESCALATION when either GE route trips |
| `Heg-League ceasefire outcome: <outcome> (weights standDown A / partial B / collapse C).` | End of POLITICAL_INTERVENTION *(was "summit outcome" until 2026-09-13, R15)* |
| `Heg-League meter: tension T \| state S (Nd left) \| weariness Heg A / League B \| cycle C` | Every `debugMeterIntervalDays` (default 30d) |
| `Heg-League director IDLE: <reason>.` | While idle, at most once per 30 days |
| `Heg-League: hostility had lapsed and was re-asserted (B3 self-heal).` | On a B3 correction |
| `Nex relation pin applied/removed …` / `Nex conquest shields: N applied, M removed.` | Once per load, only when something changed |
| `TEST: forcing state …` / `TEST: tension forced to …` | While a `testing{}` knob is set |
| `Exiles Heg-League tension A -> B (reason)` | **Log only**, every real meter movement |

#### Deviations and conservative readings — all flagged, none invented

1. **`DiplomacyManager.modifyMaxRelationshipMod` is an INSTANCE method**, reached via the static
   `DiplomacyManager.getManager()`. §7.2 wrote it as a static call. Corrected against the 0.12.1d
   source; behaviour is unchanged.
2. **`canCeasefire` lives on `NexFactionConfig`, not `DiplomacyManager`** — test D2's console line
   is `exerelin.utilities.NexFactionConfig.canCeasefire("hegemony","persean")`. Also confirmed
   from source: `getMaxRelationship` takes `min(mod1, mod2) + baseMax`, so **pinning one
   direction is sufficient**, which is what ships.
3. **INCIDENT is implemented as a zero-dwell state**, entered and left on the same tick, per
   §3.2.2 "instant state, not a dwell state". `testing.forceState` therefore rejects 2 (pinning
   an instant state would fire endless incidents) — documented in the config comment.
4. **Offender/victim are FACTIONS, not markets.** §3.2.2 names both; Pass 1 needs the offender
   only to decide who retaliates and with what lag. No market is selected and no geography is
   consulted, so nothing here pre-empts R6.
5. **`cycle.interventionDays` is a Pass-1 addition (default 20).** §3.2.2 requires every state to
   have a dwell and §9's schema does not give one for POLITICAL_INTERVENTION. Conservative
   default; flag it in the first tuning pass.
6. **Tension decay runs in TENSION and DE_ESCALATION only.** §3.2.1 names "quiet stretches" and
   "the De-escalation state"; an unfolding crisis is neither, so the middle states do not bleed
   the meter.
7. **Talks collapse applies BOTH the minimal cut and the collapse gain.** §3.2.1 lists collapsing
   talks as a tension *gain*; §3.2.2 calls collapse a *minimal cut*. Both are applied, net upward
   at the shipped defaults. If Sean wants one or the other, it is a two-line change.
8. **DE_ESCALATION always follows the summit, including after a collapse.** The cycle's ops wind
   down regardless; collapse's teeth are the minimal cut plus the shortened next TENSION dwell.
9. **Only the keys Pass 1 reads are shipped in the JSON.** The op blocks (`interdiction`,
   `blockade`, `surgicalStrike`, `planetaryBlockade`, `recoveryConvoys`, `playerInterception`,
   `midan`) and the geography lists (`exemptSystems`, `blockadeWhitelistSystems`,
   `planetaryBlockadeWhitelistMarkets`, `excludedMarkets`, `distanceFalloff`) arrive with the
   passes that read them — the Ashen block's discipline. Shipping keys that silently do nothing
   is worse than shipping none.
10. **`nex.conquestShieldMarkets` ships with the two capitals only.** R11 also covers the
    false-flag permanent-strike targets, but that list is an outstanding **author deliverable**
    (§11 item 1); Eventide is a *candidate*, not a ruling. Adding a guess would silently make a
    world unconquerable that Sean never chose. **Add the strike targets to this list when the
    merged list lands.**
11. **New tunable `premiseCheckIntervalDays` (default 5).** R6c requires live ownership queries,
    but that query allocates and CLAUDE.md §7 forbids allocating in `advance()`. The check runs
    on its own slow clock and the tick reads cached booleans. Deliberately not `timeScale`d — it
    is frame housekeeping, not a duration in the fiction.
12. **New tunable `debugMeterIntervalDays` (default 30).** Testing aid only; §3.4's rule that
    internal meter movements are never player-facing is unaffected — the whole channel is off in
    production with `debugMessages`.

#### One latent bug caught during the build

The conquest-shield sweep originally compared `market.getId()` against the raw config strings.
The econ loader assigns its own market ids (CLAUDE.md §7), so a market resolvable by
`getMarket("chicomoztoc")` need not *carry* that id — which would have shielded it on one line
and un-shielded it on the next, forever, once per load. Fixed by resolving the configured ids to
live markets first and sweeping against the ids those markets actually carry. Unresolvable ids
now log a warning instead of failing silently.

#### Nothing was built that needs a new author ruling

No item was left unbuilt for want of a ruling. The two outstanding author *deliverables*
(§11 items 1 and 2 — the merged target list and the player-facing text) gate Passes 4, 6 and 7,
not Pass 1, exactly as §11 predicted. The one place a deliverable touches Pass 1 is item 10
above, and it is shipped in the safe direction.

#### PENDING LIVE TESTS — none were run (no game was launched)

**Nexerelin console tests (§7.5), all PENDING:**
- **D2** — `runcode exerelin.utilities.NexFactionConfig.canCeasefire("hegemony","persean")`
  must print `false`, driven by B2's runtime pin alone.
- **D3** — force the event: Nex console `CreateDiplomacyEvent hegemony persean starcrossed`.
  The relationship must not move.
- **D4 — B2 idempotency** — save/load ×3, then read
  `exerelin.campaign.DiplomacyManager.getManager().getMaxRelationshipModValue("hegemony","persean")`.
  Must read **−1.6, not −4.8**. (The persistent flag `$exiles_hl_nex_pin_applied` is what makes
  this hold against Nex's accumulating setter.)
- D1 is OBSOLETE (B1 dropped). D5–D7 are soak/mode tests for later passes.

**First-boot checklist (vanilla, no Nexerelin):**
1. Game starts. No Janino error, and **no `exerelin` class is mentioned anywhere in the log** —
   that is the whole point of the hook indirection.
2. `Exiles Heg-League director config loaded from data/config/exiles/director_config.json`
   appears in the log.
3. `Heg-League tension initialised at 25.` then `Heg-League -> 1 TENSION (…)` in the message
   window within the first few in-game days.
4. Save, reload: exactly ONE director (the `hasScript` guard), tension and state preserved,
   config re-read.
5. Set `timeScale: 0.1` and watch a full cycle: 1 → 2 → 3 → 5 → 6 → 1 at low tension, and
   1 → 2 → 3 → 4 → 5 → 6 → 1 once tension is pushed over 60 with `testing.tensionOverride`.
6. `testing.forceState: 4` pins ESCALATION; `testing.forceIncidentOffender: "persean"` makes
   every retaliation the Hegemony's (20d lag instead of 10d).
7. `conquestShields` on → check Chicomoztoc and Kazeron carry `$nex_npc_no_invade`; flip it off,
   reload, confirm both keys are gone.
8. Confirm across a long run that the meter never falls below 15 and never exceeds 100.

**First-boot checklist (with Nexerelin):** all of the above, plus D2/D3/D4, plus confirming the
`Nex relation pin applied (-1.6; total now -1.6)` line appears exactly once on the first load and
never again.

### 14.2 Pass 1 — live-test record [2026-08-27; Sean at the controls, log-verified]

**Boots.** Vanilla and Nex boots both clean. Vanilla run: `ExileHegLeagueNexPin` never loaded
(no class-load line — the string-only indirection held), no exerelin classes touched, config
loaded, conquest shields applied (2/0 — inert vanilla memory keys, by design), entered
`TENSION (161d, tension 25.0, cycle 0)`. Nex run: NexPin loaded, `Nex relation pin applied
(-1.6; total now -1.6)`, shields 2/0, `TENSION (154d, …)` — differing dwell rolls confirm the
randomized dwell. Only pre-existing log noise (IntelliJ-template mod_info, vanilla `flare` /
`module_hightech_decor` hull-spec lines). Save/load and switch-off reversal: done by Sean
(author-reported; that session's log was not retained).

**Control run — pin OFF (unplanned, and the best evidence of the day).** With
`relationPin: false`, Nexerelin *organically* rolled Star-crossed Lovers between Hegemony and
the League mid-session; the alliance peace vote passed 2–0, the event transmitted, and
hostility genuinely lapsed — the §7.1 threat model demonstrated live, unprompted, inside a
single session. B3 caught it immediately:
`Exiles Heg-League B3 self-heal: Hegemony/Persean League were no longer hostile; hostility
re-asserted.` **B3 alone holds the premise.** (Knock-on: the Church↔League peace from the same
alliance vote stood — unpinned pair, expected Nex behavior, left alone.)

**D2 — PASS.** `canCeasefire: false` with the pin live.

**D3 — PASS ×3.** Every forced `CreateDiplomacyEvent hegemony persean starcrossed` produced
`Trying event: Star-crossed Lovers` then Nex's own refusal: *"Factions hegemony and persean are
above their maximum relations."* B2 blocks the positive event at the source — no vote, no
transmit, relationship untouched. (Contrast the control run, where the same event transmitted
and only B3 reverted it.)

**D4 — PASS.** Pin applied exactly once ever; across 3+ campaign re-entries in two game
sessions every readout returned `pin: -1.6`, never −3.2/−4.8. The
`$exiles_hl_nex_pin_applied` flag defeats Nex's accumulating setter, and reloads are silent
(no repeat apply line), exactly as specced.

**Tester gotchas (bind future D-test runs):**
1. Extra whitespace in `CreateDiplomacyEvent` args silently degrades to a *random* event
   ("Creating either diplomacy event … No event available") — the named event never fires.
   Single spaces only.
2. A bare `runcode <expression>` prints nothing. Wrap in
   `Console.showMessage("label: " + <expression>)` — the §7.5/§14.1 D2 and D4 lines should be
   read with that wrapper.

**Still open from §14.1 checklists:** vanilla items 5 (timeScale full-cycle walk), 6
(`forceState` / `forceIncidentOffender`), 8 (long-run meter bounds 15–100); item 7's memory-key
inspection was covered functionally by the reversal test but the keys were not read directly.
D5–D7 wait for later passes, per spec.

**D5 — early soak [2026-08-27, same day; Sean ran it ahead of schedule].** `timeScale: 0.1`,
pin live, **16 full crisis cycles** (spec asked ~5). Criterion (a) **PASS**: zero Heg–League
ceasefire/peace intel; Nex organically attempted a Heg↔League diplomacy event three times
(Antiwar Protests ×2 among them) and all three died at the source on *"above their maximum
relations"* — zero transmits for the pair, zero B3 self-heals needed. All transmitted
ceasefires/deals were unpinned pairs. Criterion (b) — **zero Nex Heg↔League invasions/raids
observed, but this is WEAK evidence**: no invasion-fleet lines for ANY faction; Nex invasions
gate on invasion-point accrual and campaign progression, and the soak covered only a couple
hundred early-game days. **Double-activity sizing stays OPEN — re-run D5 during a Pass 2+ soak
on a mature campaign.** Side findings: (1) the pin re-applied on this save (flag-less save —
counter at cycle 1, tension at floor); designed behavior, total stayed −1.6, D4 invariant holds.
(2) **Do not tune meter numbers from this soak's shape**: with no ops feeding tension gains
(Pass 1 launches nothing), the meter pins at the floor and every cycle short-circuits
1→2→3→5→6 — INCIDENT bumps ~+8, the summit cuts straight back to 15, ESCALATION never fires.
Expected at this feature level; the incident cadence at the floor (a full cycle every ~20–40
production days, forever) is a number to revisit once real ops exist.

### 14.3 Pass 2 — Interdiction raids [BUILT 2026-08-27; UNTESTED IN GAME]

Janino-clean, registered, and **the TENSION state now launches fleets**. Pass 1's cycle turned
over and printed; it now drives operations in the two states §3.2.2 says carry interdiction.
Everything else in §12 remains unbuilt, and **no blockade plumbing was touched** — the author's
lore-driven blockade whitelist (R6b) is still outstanding and still gates Passes 4/6.

#### What was built

| File | Status | Purpose |
|---|---|---|
| `data/scripts/exiles/ExileHegLeagueInterdictionManager.java` | NEW | The launcher. Reads the director's state, picks the acting side, source, budget and target (§2/R6), and builds the op. Holds the private feasibility gate + the S4 instrumentation, the anti-hotspot cooldown key, the per-side concurrency caps, and the nested `ExileHegLeagueAssembleStage` (per-fleet FP cap, no source concealment). |
| `data/scripts/exiles/ExileHegLeagueInterdictionStage.java` | NEW | The presence action stage — third port of `BlackFleetCommerceRaidStage` → `AshenInterdictionStage`. Loiter, never raid, apply/lift the condition, and keep raiders off every station in the system. |
| `data/scripts/exiles/ExileHegLeagueRaidIntel.java` | NEW | Hidden-until-arrival "System Bounty" intel + the §4.7 per-kill bounty/rep port, payer = the raided side. Owns `createFleet` with the `maxShipSize` guardrail and the end-of-op reports (cooldown, completed-op tension). **Amended 2026-08-27** with `applyPlayerInflictedWeariness()` per Sean's weariness ruling (see below). |
| `data/scripts/marketconditions/ExileHegLeagueInterdiction.java` | NEW | `exile_hl_interdiction` — accessibility penalty + availability hits, on the shipped `ExileCommodityShortageCondition` base, used **as-is**. |
| `data/scripts/exiles/ExileHegLeagueConfig.java` | MODIFIED | New `interdiction{}` block, the geography lists, per-side `opWeights.interdiction` / `maxConcurrentOps` / `presenceDaysMult`, `wearinessPerFpDestroyed`, and their `timeScale` handling. |
| `data/scripts/exiles/ExileHegLeagueDirector.java` | MODIFIED | Two things only: a public `getConfig()` accessor, and a `config == null` guard on the three `reportX()` hooks (Pass 1 shipped them unguarded; Pass 2 is the first caller, so it is the first pass that could have hit the NPE). No state-machine, debug-line or Nex behaviour was altered. |
| `data/scripts/exiles/ExilesModPlugin.java` | MODIFIED | Manager registration in `onNewGameAfterEconomyLoad` + `hasScript` guard in `onGameLoad`. Two additions, nothing else touched. |
| `data/campaign/market_conditions.csv` | MODIFIED | One appended row, `order 157`. (§8 said "continue from 156"; 156 is already Emergency Measures, so 157 it is.) |
| `data/config/exiles/director_config.json` | MODIFIED | New keys inside the existing `"exilehegleague"` block. **No existing key was moved, renamed, deleted, or had its value changed** — including the testing knobs (see the warning below). |

#### Janino check

`tools/janino-check.ps1` — **7 of 7 EXIT=0**: `ExileHegLeagueInterdiction`,
`ExileHegLeagueConfig`, `ExileHegLeagueInterdictionStage`, `ExileHegLeagueRaidIntel`,
`ExileHegLeagueInterdictionManager`, `ExileHegLeagueDirector`, `ExilesModPlugin`.
`ExileHegLeagueNexPin` was not re-checked — it is untouched by this pass and fails the plain
wrapper by design (§14.1).

**Re-checked 2026-08-27 after the weariness amendment — 5 of 5 EXIT=0:**
`ExileHegLeagueRaidIntel` (the only file changed), plus `ExileHegLeagueInterdictionManager`,
`ExileHegLeagueInterdictionStage`, `ExileHegLeagueDirector`, `ExileHegLeagueInterdiction` as
loaders of it. No config, CSV or other Java file was touched by the amendment.

`director_config.json` re-parsed end-to-end after the edit (comments and trailing commas stripped
first, as in Pass 1): **5 top-level blocks unchanged, 19 keys in `exilehegleague`** (13 + the 6
Pass-2 additions), 20 keys in `interdiction`, and every value the code reads resolves —
`maxShipSize 2`, `cadenceDays 90`, `bountyPerFrigate 3000`, `exemptSystems [aztlan, thule]`,
`excludedMarkets` = the five strain markets, `allowedMarkets []`, `distanceFalloff 0.10`,
`wearinessPerFpDestroyed 0.05`, per-side weights 1 (Heg) / 3 (League).

#### How the TENSION wiring changes Pass-1 behaviour

The director is unchanged as a state machine. The manager reads `director.getState()` and:

- **TENSION** — launch attempts on `interdiction.cadenceDays` (90d production, jittered ±25%),
  capped at `tensionMaxConcurrentOpsPerSide` (1) live ops **per side**. §3.2.2's "low-rate
  interdiction raids along the border".
- **ESCALATION** — the same clock times `escalationCadenceMult` (0.35 → ~31d), capped at each
  side's own `maxConcurrentOps` (2). §3.2.2's "multiple interdiction ops".
- **Every other state launches nothing**, and the launch clock does not accrue outside a raiding
  state, so ops cannot silently queue behind a summit and all fire at once when TENSION resumes.
- **Completed ops feed the meter**: an op whose presence ran its full window calls
  `director.reportOperationCompleted(actor)` → `tension.gains.perCompletedOp`. An op driven off
  reports nothing and says so in the log. This is the first real upward pressure besides
  incidents, and it is what should let the meter climb toward `hawkThreshold` instead of pinning
  at the floor the way the D5 soak observed.
- **Player kills feed weariness** (§4.7) → `weariness.standDownWeight` at the summit.

Pass-1 debug lines, state names, meter reports, idle logging and all three Nex layers are
untouched; the manager adds one `debug()` line per launch on the same `debugMessages` switch.

#### Target selection, exactly as it runs

Acting side is a weighted roll over `opWeights.interdiction` (Heg 1 / League 3), skipping a side
already at its cap; the victim is the other belligerent. Source is an acting-side market with a
**functional spaceport**, weighted by size — exempt systems are valid sources (R6a is a targeting
rule, not a basing rule). Budget rolls **before** target selection, because the gate needs to know
what the op can take on. Then, per candidate system:

1. live `market.getFactionId()` == victim, market visible/in-economy, hostile pair (R6c — never
   hardcoded, so a market that changes hands leaves the pool);
2. **R6a** — not in `exemptSystems` (matched against system **id and base name**,
   case-insensitively);
3. no player market in the system (Exiles code-exempt convention);
4. not on `$exiles_hl_op_cooldown` (set when an op **ends**, not when it launches);
5. no live op of this director in the system (`hasLiveOpIn`);
6. not in `ExileLiveOps.getSystemsWithActiveRaids(null)` — the cross-director dogpile check,
   read-only shared state, asked once per launch;
7. at least one **eligible victim market**: not `ExileProtectedFactions.isProtected`, not in
   `excludedMarkets` (R6b strain markets), inside `allowedMarkets` if that list is non-empty
   (R6c), and not `ExileUnrestGuard.isBattered` (which folds in the Emergency-Measures check);
8. **feasibility** — commerce tier, stations excluded, `enemyStrength(actor, system) <= fp ×
   feasibilityMult`;
9. surviving systems are weighted `1 / (1 + distanceLY × distanceFalloff)` from the source.

Check 7's per-market rules are **re-evaluated in the stage** when the condition lands, because an
op outlives its own target selection by weeks and a market can go Battered or change hands in
transit.

**The instrumentation.** Two log families, both `INFO`, log only (never the message window):

```
Exiles Heg-League interdiction feasibility: <System> REJECTED (defense 812 > gate 300 = 150 FP x 2.0), actor=persean, victim=hegemony
Exiles Heg-League interdiction feasibility: <System> eligible (defense 210 <= gate 300 = 150 FP x 2.0), actor=persean, victim=hegemony
Exiles Heg-League INTERDICTION LAUNCHED: <source> -> the <target> system | actor=persean victim=hegemony | FP=150 defense=210 gate=300 | maxShipSize=2 maxFleetFp=45 presenceDays=24 | state=1 TENSION tension=25.0 | focus=<market>
```

**Tune `spawnFpMin/Max` and `feasibilityMult` from those lines, not from intuition.** Every FP
number in this pass is a guess (the `assaultFp: 800` lesson). If the rejection lines dominate,
the Core's live patrol strength is above what a destroyer-capped op can face and the multiplier —
not the FP — is the dial; if nothing is ever rejected, the gate is inert and should be tightened
so it still means something.

#### The condition

`exile_hl_interdiction`, subclass of the shipped `ExileCommodityShortageCondition` (used as-is;
accessibility added in our own `apply()`/`unapply()` around the `super` call, exactly as §4.1
specifies and for the reason that base class documents). Effects: **−50% accessibility** plus
**−2 availability** on supplies, fuel and the three commodities the market most depends on
importing. Applied on **day 5** of the presence to every eligible victim-faction market in the
system; **lifted the moment the raiders withdraw**, success or abort; **45-day hard self-expiry**
as the orphan backstop (Ruling F discipline). `ExileUnrestGuard.reportExilesPressure` is called
with `ExileEmergencyMeasuresConfig.pressureDays(DURATION_DAYS, 0)` on every application.

**Either side can cause it**, which no sibling condition has to handle. The raiding faction id is
written to the market's memory under `$exiles_hl_interdiction_actor` when the condition lands and
the tooltip names that faction from it; a missing key degrades to neutral wording rather than
guessing. Display name ("Wartime Interdiction") and CSV description are **PLACEHOLDERS** pending
§11 item 2, and are written faction-neutral so they read correctly whichever way round the op is.

#### The bounty (§4.7)

Direct port of `BlackFleetRaidIntel.reportBattleOccurred` via `AshenRaidIntel`. Spawned fleets
carry `exile_hl_raider` and are registered as `FleetEventListener`s. On a player-involved battle
in which a raider fleet takes losses, the **raided** faction pays
`bountyPerFrigate × hull-size-num × playerInvolvementFraction` plus `SYSTEM_BOUNTY_REWARD`
reputation, aggregated through the 0.2-day deferred popup, tagged `INTEL_BOUNTY`, and paid
through the ending window. Because the payer is whichever side is the victim *this cycle*, the
player can profitably take either side, or both across cycles — which is the intended shape.

The bounty is **unchanged by the 2026-08-27 weariness ruling below**: it remains strictly the raided
side paying for dead *raiders*, scoped to the op fleet's own losses.

> **⚠ FLAGGED, NOT CHANGED — bounty pays even when the player fights ON the raiders' side.**
> Noticed while implementing the weariness ruling; left alone because the ruling explicitly kept
> bounty mechanics as-is. `Misc.getSnapshotMembersLost(fleet)` counts the op fleet's losses
> regardless of *who* inflicted them, so a player who joins the raiders and watches allied raiders
> die to NPC defenders is still paid credits **and** `SYSTEM_BOUNTY_REWARD` rep by the victim
> faction, scaled by their involvement. The Ashen/Black Fleet ancestors cannot hit this — their
> raiders are permanently hostile to the player, so the player is never on the raider side. This
> theatre is the first where both sides are joinable, which is exactly what makes the ruling
> interesting and also what exposes this. **Fix, if Sean wants one, is one line** before the bounty
> block: `if (battle.onPlayerSide(fleet)) return;`. Not built — needs his call, since "the player
> gets paid for a bloodbath they helped cause" is arguably in-genre for a mercenary war.

#### Weariness attribution [AMENDED — RULED by Sean, 2026-08-27]

**Supersedes deviation item 1 as originally written (kept below, struck, as the record of what
shipped first).**

**The ruling.** War weariness from player battles is credited to **whichever faction owned the ships
the player killed, on a battle-per-battle basis.** Player kills raiding ships → the *raiding* faction
gains weariness. Player sides with the raiders and kills defending ships → the *defending* faction
gains weariness. Both can occur in one battle and both are credited. This replaces the raider-side-
only reading Pass 2 shipped, and it makes the mercenary's choice of *side* matter, not merely their
choice of target system.

**Implementation.** `ExileHegLeagueRaidIntel.reportBattleOccurred` no longer computes weariness
inline; it delegates to a new `applyPlayerInflictedWeariness(BattleAPI)`. That method scans
`battle.getNonPlayerSideSnapshot()` — the same list vanilla `SystemBountyIntel` pays out from, and
by definition the side whose losses the player caused. Fleets belonging to neither war faction
(pirates, a passing Independent) are skipped; the fleet-point losses of the actor and of the victim
are totalled **separately** and each credited to its own ledger via `director.addWeariness`, scaled
by `playerInvolvementFraction × wearinessPerFpDestroyed`. When the player fights *on* the raiders'
side, this op's own fleet sits on the **player's** side and is therefore correctly excluded — those
raider losses were inflicted by NPC defenders, not by the player.

**One battle, one credit.** The listener is attached per-fleet, so an op with two fleets in the same
battle fires `reportBattleOccurred` twice and would double-credit the scan. Guarded by a new
`wearinessBattleSeed` field compared against `battle.getSeed()`, which is fixed for the life of a
battle. Stored as a `Long`, not as the `BattleAPI`, so nothing battle-scoped enters saved intel
state.

**⚠ SCOPE LIMIT — documented, not a defect.** This listener is registered on *this op's raider
fleets*, so it only ever observes **battles the op fleet is in**. Hegemony/League fighting elsewhere
in the sector — ordinary patrol skirmishes, a player ambush of a Hegemony fleet unrelated to any
op, losses to pirates or Ashen — is **invisible to this path and contributes nothing** to either
weariness ledger. That is the full extent of what the ruling was implemented to cover. Sector-wide
weariness accounting would require a `ListenerManager`-level `FleetEventListener` owned by the
director rather than a per-fleet one; that is deliberately **not** built in this pass and is not
implied by the ruling.

A permanent log line ships with it (`Exiles Heg-League player-inflicted weariness: <faction> +<amt>
(<fp> FP), ...; involvement=<f>`), so the first live test measures the real magnitude rather than
guessing whether `wearinessPerFpDestroyed 0.05` is the right order.

#### Deviations and conservative readings — all flagged, none invented

1. ~~**⚠ §4.7's "the victim's war-weariness ledger" is read as "the party whose ships were
   destroyed", i.e. the RAIDING side.** Taken literally the other way it contradicts §3.2.1,
   which defines weariness as a faction's own cumulative fleet/market losses — the ships the
   player destroys are the raider's, not the raided market owner's. **Needs a one-word ruling
   from Sean if the literal reading was meant**; it is a one-line change either way.~~
   **CLOSED — RULED by Sean, 2026-08-27.** Neither reading: weariness follows *ownership of the
   destroyed ships*, per battle, on either side. Built; see "Weariness attribution" above.
2. **Interdiction runs in TENSION and ESCALATION only.** §3.2.2 gives interdiction to those two
   states by name; RETALIATION is explicitly "ONE proportionate punitive op" (surgical strike or
   planetary blockade — Passes 3/6), INTERVENTION is the summit, DE_ESCALATION is where "ops
   stop", and INCIDENT has no dwell. The conservative reading.
3. **Raiders loiter at the SYSTEM CENTRE by default**, not on the target market's docks. §4.1
   says "never touch planets"; the Ashen "bold" loiter lever exists to control whether raiders
   wake Omicron's dormant station complements, which has no counterpart in this theatre.
   `interdiction.loiterOnFocusMarket` is exposed, off, for tuning.
4. **Source concealment deliberately NOT ported** (§3.4). The Ashen assemble stage's
   `isSourceKnown() == false` was dropped; its per-fleet FP cap and PATROL_LARGE suppression were
   kept, because those solve the station-bee-line and the "45 FP squadron calling itself an
   Armada" problems, not concealment.
5. **New keys §9 does not name**, all inside `interdiction{}`: `cadenceDays`,
   `escalationCadenceMult`, `tensionMaxConcurrentOpsPerSide`, `abortFraction`, `maxFleetFp`,
   `loiterOnFocusMarket`, `suppressStationAttacks`, `conditionDelayDays`, `conditionDays`,
   `accessibilityPenalty`, `importHitCount`, `importHitUnits`; per-side `presenceDaysMult`; and
   `allowedMarkets`. §9's own names are used exactly where it gives them (`enabled`,
   `spawnFpMin/Max`, `maxShipSize`, `presenceDays`, `feasibilityMult`, `systemCooldownDays`,
   `bountyPerFrigate`, `opWeights`, `maxConcurrentOps`, `exemptSystems`, `excludedMarkets`,
   `distanceFalloff`).
6. **Hegemony "endurance" (§3.2.3) is expressed as longer ops, not more of them, in this pass.**
   Both sides ship `maxConcurrentOps: 2` per §9's own illustrative schema; the Hegemony's
   industrial advantage lands properly in Pass 4's blockades. What ships now is: League
   interdicts 3× as often (`opWeights`), Hegemony ops sit 1.25× as long, League 0.8×.
7. **`allowedMarkets` narrows target selection only** — it deliberately does not suppress the
   condition on an ordinary victim market inside a system that was already chosen. R6b's
   `excludedMarkets` does both, because strain markets must never take the hit at all.
8. **The condition uses `order 157`**, not §8's "continue from 156": 156 is Emergency Measures.
9. **The feasibility log fires once per candidate system per attempt** (~10–15 lines at
   production, more with `timeScale` compression). Ungated on purpose for this pass — it is the
   measurement the tuning depends on. Pass 9's config sweep should decide whether to put it
   behind `debugMessages`.
10. **`ExileHegLeagueDirector.reportOperationCompleted` / `reportCollateralDamage` /
    `reportMidanInterference` dereferenced `config` without a null check.** Latent since Pass 1,
    unreachable until now. Guarded.

#### Nothing was built that needs a new author ruling

~~Item 1 above is the only question, and it is a wording ambiguity in a ratified section rather than
an unmade decision — the conservative reading is shipped and the alternative is one line.~~
**Item 1 was ruled on 2026-08-27 and is built; no open questions remain from this pass.** The two
outstanding author *deliverables* (§11 items 1 and 2) gate Passes 4/6/7 and the condition's
player-facing text, exactly as §11 predicted; neither blocked this pass.

#### ⚠ TEST-TUNED VALUES AND SHIPPED-CONFIG WARNINGS (pre-production checklist)

1. **`timeScale: 0.1`** — inherited from Pass 1. `interdiction.cadenceDays` 90 → ~9d,
   `presenceDays` 30 → 3d, `conditionDays` 45 → 4.5d, `systemCooldownDays` 60 → 6d.
   **RESTORE TO 1.0 BEFORE PRODUCTION.** Note that unlike Pass 1, this pass has *fleets*: travel
   is uncompressed while presence compresses, so at 0.1 raiders may reach the system with little
   presence window left. That is the same trap §9 records for blockades. **For a realistic
   pacing test, use `timeScale: 1`;** use 0.1 only to walk the state machine.
2. **`debugMessages: true`** — inherited. False for production.
3. **`testing.tensionOverride: 1`** — **left exactly as Sean set it, and it will block half of
   this pass's test matrix.** It pins the meter at the floor (15), so `hawkThreshold` (60) is
   never crossed, ESCALATION never fires, and the ESCALATION-cadence and per-side
   `maxConcurrentOps` paths are unreachable. **Set it to `-1` for normal play, or to `65`+ to
   test the escalation path.** Not changed here, per the no-silent-edits rule.
4. All FP numbers (`spawnFpMin/Max: 120/200`) and `feasibilityMult: 2.0` are **guesses awaiting
   the measurement line**.

#### PENDING LIVE TESTS — none were run (no game was launched)

**Pass-2 first-boot matrix (vanilla, no Nexerelin):**

| # | Test | Expect |
|---|---|---|
| P1 | Game starts clean | No Janino error; `Heg-League geography: exemptSystems aztlan=OK thule=OK \| excludedMarkets ...=OK ...` with **no UNRESOLVED entries** (an UNRESOLVED capital system means R6a is silently inert) |
| P2 | Let TENSION run | `Exiles Heg-League INTERDICTION LAUNCHED:` appears; an op sails from a Heg or League port |
| P3 | **The measurement** | Collect every `interdiction feasibility:` line over a run. Record eligible-vs-rejected ratio and the actual `defense` numbers, then tune FP/mult from them. Nothing else in this checklist matters as much |
| P4 | Actor mix | Over ~10 launches, roughly 3 League ops per 1 Hegemony op (`opWeights` 3:1), and Heg ops carry a visibly longer `presenceDays` |
| P5 | Geography | No op ever targets an Aztlan or Thule market, none of the five strain markets ever takes the condition, and no op targets a system holding a player market |
| P6 | Fleet shape | Arriving fleets are destroyers and below (**maxShipSize 2**), several small fleets rather than one large one, and named Patrol/Lance-tier — no "Armada" |
| P7 | Station behaviour | Raiders do **not** dogpile the target's orbital station (`suppressStationAttacks`). This is the single most likely regression: it was found the hard way twice in the Ashen build |
| P8 | Condition | On presence day 5 (×timeScale), victim markets show **Wartime Interdiction**: −50% accessibility, supplies/fuel/3 imports down 2, and the tooltip **names the correct raiding faction** |
| P9 | Lift | The condition disappears from every affected market when the op ends — both on SUCCESS and on a forced abort (kill fleets below 50% FP) |
| P10 | Bounty | Destroy raiders as a third party: credits paid by the **raided** side, rep with that side, one aggregated popup, `INTEL_BOUNTY` tag, intel reveals on first contact |
| P11 | Bounty symmetry | Repeat against the other side's op; the payer flips |
| P12 | Meter | `Exiles Heg-League tension A -> B (operation completed by ...)` in the log after a completed op; an op that is driven off logs "ended WITHOUT completing its presence" and moves nothing |
| P13 | Weariness | Kill raiders: the **raiding** side's weariness rises in the periodic meter line, one `player-inflicted weariness` log line per battle, and a bloody cycle visibly raises the stand-down weight at the summit |
| P13a | Weariness, other side (the 2026-08-27 ruling) | Take the raiders' side and kill **defenders**: weariness accrues to the **defending** faction instead, and the raiding faction's ledger does not move |
| P13b | Weariness, no double-credit | A battle in which **two** of the op's fleets participate produces exactly **one** `player-inflicted weariness` log line, not two |
| P14 | Cooldown | A system that hosted an op is not re-targeted for `systemCooldownDays` **after the op ends** |
| P15 | Dogpile | With an Ashen/BF/Omicron op live in a system, no Heg-League op is sent there |
| P16 | Battered | A market under Emergency Measures / at the unrest cap is never chosen and never takes the condition |
| P17 | Save/load mid-op | Exactly one manager (the `hasScript` guard); the in-flight op resumes; the condition neither duplicates nor vanishes; config re-read |
| P18 | Kill switch | `interdiction.enabled: false` → no new ops; `$exiles_hl_interdiction_enabled` set true at runtime overrides it |
| P19 | Idle | Force the premise loss (§3.3) mid-op: the director idles and no new ops launch |

**Piggyback items still open from §14.1/§14.2 — this pass is the natural time to close them:**

- **Vanilla item 5 (timeScale cycle walk).** Now worth far more than it was: with ops feeding
  `perCompletedOp`, the cycle should actually be able to reach ESCALATION. Requires
  `testing.tensionOverride: -1` first (see warning 3).
- **Vanilla item 6 (`forceState` / `forceIncidentOffender`).** `forceState: 4` is now the cheap
  way to test the ESCALATION cadence and the per-side `maxConcurrentOps` cap without waiting for
  the meter; `forceState: 3` / `5` / `6` should produce **no** interdiction launches at all.
- **Vanilla item 8 (long-run meter bounds 15–100).** Re-check with real gains flowing in.
- **Vanilla item 7 (conquest-shield memory keys read directly).** Unaffected by this pass but
  still unread.
- **D5 re-run (double-activity sizing).** §14.2 recorded criterion (b) as weak evidence on an
  early-game soak. **Re-run it on a mature campaign now**, and count Nex-launched Heg↔League
  invasions/raids *alongside* this director's ops — the point of the re-run is to size total
  Heg-League activity, which only exists to be sized now that this director contributes some.
- **D6 (rebellion pressure).** Now testable for real: run interdiction ops against a Heg/League
  core world and watch `$nex_rebellionPoints`. This is the CLAUDE.md priority-4 interaction most
  likely to produce accidental collapse.

#### Bounty exploit CLOSED [RULED by Sean, 2026-08-27, same day]

The joined-the-raiders bounty finding above is fixed: `ExileHegLeagueRaidIntel.
reportBattleOccurred` now returns before the bounty block when `battle.isOnPlayerSide(fleet)`
is true for the op fleet — no payout, no rep, when the op fleet fought on the player's side.
Weariness attribution (side-agnostic, runs first) is unaffected. Janino-clean (EXIT=0).
Test P13a additionally confirms NO bounty popup when siding with the raiders.

#### Live-test crash FIXED [2026-08-27 evening; first Pass-2 boot]

First live op (Kazeron -> Arcadia, League vs Hegemony, 176 FP vs defense 205) launched, loitered
and applied its condition to Citadel Arcadia correctly, then the game crashed rendering the
intel screen: NPE in vanilla `PirateRaidAssembleStage.isSourceKnown()` via
`RaidIntel.getArrowData`. Cause: `ExileHegLeagueAssembleStage` passes null as the pirate-base
intel, and the inherited `isSourceKnown()` dereferences it -- Ashen's source-concealment
override had been masking that null by accident, so dropping concealment (a correct §3.4 call)
reintroduced the vanilla null path. Fix: explicit `isSourceKnown() { return true; }` override,
documented as load-bearing. Janino-clean. Knock-on: the "missing" bounty section and the
seemingly absent condition in the same report were the crash killing the events panel before
the intel rendered, plus the condition's day-5 landing delay -- both confirmed working in the
log. Retest: reopen the intel screen during a live op; bounty section and map arrow must render.

#### Post-Pass-2 rulings + long-run observations [2026-08-27, after the Phase-3 retest]

- Retest PASSED: intel screen renders during a live op (isSourceKnown fix holds); Pass 2
  committed at 4e7d878.
- **RATIFIED (Sean): interdiction stays victim-faction-scoped; blockades will be SYSTEM-WIDE
  in Pass 4** (recorded inline in sec 4.1/4.2).
- Long-run log (4 cycles, 5 ops, zero errors): per-side asymmetry confirmed live (League 24
  presence days vs Hegemony 37; retaliation lags 10d/20d flip correctly with the offender);
  target rotation and cooldowns working; conditions land on victim markets only, as ratified.
- **Open tuning item: ESCALATION never fires organically.** Summit cuts return tension to the
  floor faster than op-completion gains accumulate toward hawkThreshold 60. Deliberately NOT
  tuned now: Pass 3's INCIDENT table is designed to be the main tension driver, so revisit the
  gains/cuts balance after Pass 3 is live rather than compensating here first.


### 14.4 Pass 3 — Surgical strikes + the INCIDENT table + RETALIATION wiring [BUILT 2026-08-27; UNTESTED IN GAME]

Janino-clean (12/12), config re-parsed, registered. **The RETALIATION state now launches the
victim's punitive op**, incidents come from a weighted table with rumour-then-confirmation intel,
and surgical strikes put military industries offline. Passes 4–9 remain unbuilt, **no blockade
plumbing was touched**, and the author's lore-driven blockade whitelist (R6b) is still outstanding
and still gates Passes 4/6.

#### What was built

| File | Status | Purpose |
|---|---|---|
| `data/scripts/exiles/ExileHegLeagueIncident.java` | NEW | One INCIDENT-table entry: id, weight, severity multiplier, soft offender bias, and the placeholder rumour/confirmation prose. Carries `defaultTable()` (the four non-Midan entries) and the **Pass-7 seam** `isEligible()` / `isMidanFalseFlag()`. |
| `data/scripts/exiles/ExileHegLeagueIncidentIntel.java` | NEW | The telegraph. `BaseIntelPlugin` subclass posting the rumour immediately and sending the confirmation as an update `confirmDays` later, then ending itself after `intelDays`. Holds only Strings/floats/booleans. |
| `data/scripts/exiles/ExileHegLeagueStrikeStage.java` | NEW | The §4.3 action stage. Single target hit once, tactical `canBeat()`, sticky-success fix, `disruptFor()` with vanilla max-semantics, and the **private-static `isMilitaryIndustry` copy** from `OmicronRaidActionStage`. |
| `data/scripts/exiles/ExileHegLeagueStrikeIntel.java` | NEW | `extends ExileHegLeagueRaidIntel`. Three overrides only: `isInterdictionOp()` false, the ended-bullet text, and `reportOutcome()` (completed == the strike LANDED). Everything else — `createFleet` + `maxShipSize`, bounty, exploit guard, weariness attribution, hidden-until-arrival, cooldown — is inherited untouched. |
| `data/scripts/exiles/ExileHegLeagueStrikeManager.java` | NEW | The launcher. RETALIATION (one op per crisis cycle, keyed on the director's cycle counter) and ESCALATION counter-strikes (cadence + per-side cap). Holds its own assault-tier feasibility gate and the S4 instrumentation line. |
| `data/scripts/exiles/ExileHegLeagueGeography.java` | NEW | The four R6 predicates, **moved not rewritten** from the interdiction manager, which now delegates. Three callers (interdiction, strikes, the incident site pick) is the measured bar for extraction; feasibility gates were deliberately NOT extracted (S4). |
| `data/scripts/exiles/ExileHegLeagueDirector.java` | MODIFIED | `fireIncident()` now picks from the table, applies the severity multiplier, picks a flavour site through the R6 guards, and posts the telegraph. New saved fields `incidentTypeId`, `incidentSiteMarketId`, `lastRetaliationCycle` (an `Integer` — see deviation 8) plus their accessors, and a permanent `INCIDENT:` log line. **The state machine, the branch, the meter arithmetic, every Pass-1 debug line and all three Nex layers are unchanged.** |
| `data/scripts/exiles/ExileHegLeagueConfig.java` | MODIFIED | New `incidents{}` and `surgicalStrike{}` blocks, `testing.forceIncidentType`, per-side `opWeights.surgical`, and their `timeScale` handling. |
| `data/scripts/exiles/ExileHegLeagueRaidIntel.java` | MODIFIED | Two seams only: `isInterdictionOp()` (default true) and `getOpEndedText()`. No behaviour change for interdiction. |
| `data/scripts/exiles/ExileHegLeagueInterdictionManager.java` | MODIFIED | Four predicate bodies moved to `ExileHegLeagueGeography` (call sites unchanged, delegates left in place), plus one line in `countActive()` so a live strike does not eat an interdiction slot. |
| `data/scripts/exiles/ExilesModPlugin.java` | MODIFIED | Strike-manager registration in `onNewGameAfterEconomyLoad` + `hasScript` guard in `onGameLoad`. Two additions, nothing else touched. |
| `data/config/exiles/director_config.json` | MODIFIED | New keys inside the existing `"exilehegleague"` block. **No existing key was moved, renamed, deleted, or had its value changed.** |

#### Janino check

`tools/janino-check.ps1` — **12 of 12 EXIT=0**: `ExileHegLeagueGeography`,
`ExileHegLeagueIncident`, `ExileHegLeagueIncidentIntel`, `ExileHegLeagueStrikeStage`,
`ExileHegLeagueStrikeIntel`, `ExileHegLeagueStrikeManager`, `ExileHegLeagueConfig`,
`ExileHegLeagueDirector`, `ExileHegLeagueRaidIntel`, `ExileHegLeagueInterdictionManager`,
`ExileHegLeagueInterdictionStage`, `ExilesModPlugin`.
`ExileHegLeagueNexPin` was not re-checked — untouched by this pass, and it fails the plain wrapper
by design (§14.1).

`director_config.json` re-parsed end-to-end with the game's own `json.jar` (comments and trailing
commas stripped first, as in Passes 1–2): **5 top-level blocks unchanged, 21 keys in
`exilehegleague`** (19 + `incidents` + `surgicalStrike`), 5 keys in `incidents` with 4 types,
20 keys in `surgicalStrike`, 20 in `interdiction` (unchanged), and every value the code reads
resolves. The parser also printed the table's weighted-average severity: **1.03125**, i.e. the
average incident is worth `perIncident × 1.03 = 8.25`.

#### Config keys added (all inside the existing `"exilehegleague"` block)

`testing.forceIncidentType` · `hegemony.opWeights.surgical` (3) · `persean.opWeights.surgical` (1)

`incidents{}`: `enabled`, `telegraphIntel`, `confirmDays` (5), `intelDays` (60),
`types{}` with one object per entry carrying `enabled` / `weight` / `severity` /
`hegemonyOffenderChance`.

`surgicalStrike{}`: `enabled`, `retaliationEnabled`, `escalationCounterStrikes`,
`escalationCadenceDays` (30), `escalationMaxConcurrentOpsPerSide` (1), `spawnFpMin` (250),
`spawnFpMax` (400), `tensionFpScale` (0.5), `maxShipSize` (2), `maxFleetFp` (90),
`feasibilityMult` (1.0), `systemCooldownDays` (60), `abortFraction` (0.5), `maxDays` (45),
`bountyPerFrigate` (3000), `disruptDaysMin` (45), `disruptDaysMax` (90),
`extendedDisruptMult` (**1.0 — leave it there until Pass 6**), `maxIndustries` (1),
`industries` (`[]` = any military industry).

§9's own names are used verbatim where it gives them (`enabled`, `spawnFpMin/Max`, `maxShipSize`,
`feasibilityMult`, `systemCooldownDays`, `bountyPerFrigate`, `opWeights`, `industries`,
`disruptDaysMin`, `extendedDisruptMult`). Everything else is new and named to match.

#### The INCIDENT table as shipped

| id | Weight | Severity | Tension gain | Offender bias | Telegraph |
|---|---|---|---|---|---|
| `border_clash` | 3 | 0.75 | **6.0** | symmetric (0.5) | "Border Clash" — patrols traded fire near a named market; rumour hedges, confirmation is a formal protest with both sides claiming they were fired on first |
| `convoy_destroyed` | 2 | 1.25 | **10.0** | symmetric | "Convoy Destroyed" — an overdue civilian convoy; confirmation releases survivor testimony naming the offender's warships |
| `violent_inspection` | 2 | 1.0 | **8.0** | Hegemony 0.75 | "Inspection Turned Violent" — a stop-and-search that ended in gunfire; confirmation demands the crew's release |
| `civilian_casualties` | 1 | 1.5 | **12.0** | symmetric | "Civilian Casualties" — refugee traffic carries claims of civilian dead after a raid; confirmation attributes the strike formally, offender calls the figures fabricated |

Mechanics common to all four:

- **Offender/victim are FACTIONS.** The bias is soft, never a lock — `hegemonyOffenderChance: -1`
  falls through to `cycle.incidentOffenderHegemonyChance` (0.5).
- **A site market is named for flavour only.** It is picked through `ExileProtectedFactions`,
  the R6b strain list, the R6c allow-list, `ExileUnrestGuard.isBattered` **and** the R6a
  system-level capital exemption, so the prose can never name Mairaath or Chicomoztoc — but
  nothing mechanical reads it. If nothing resolves, the prose degrades to "the border".
- **Telegraph lifecycle:** posted VISIBLE at the incident (a telegraph exists to be read),
  confirmation update at day 5, self-ends at day 60. Tagged `INTEL_MILITARY` plus both faction
  ids; offender crest as icon, victim colours for the UI.
- **No `reportCollateralDamage()` from an incident.** §3.2.1 lists incidents and collateral as
  separate gain sources; civilian harm is folded into `civilian_casualties`' severity instead, so
  the meter is never double-dipped. `reportCollateralDamage` stays reserved for op-caused
  collateral (Pass 4's system-wide blockades are its natural first caller).
- **`incidents.enabled: false` reproduces Pass 1 exactly** — unnamed incident, coin-flip offender,
  flat gain of 8.

**Pass 7 adds its entry without surgery:** subclass `ExileHegLeagueIncident`, override
`isEligible()` with the tension-floor/cooldown/cap gate and `isMidanFalseFlag()` with true, add the
instance to `defaultTable()`. The director already routes a false-flag entry's tension through
`tensionPerMidanInterference` instead of `perIncident` (that branch is written and unreachable
today), picks polymorphically, and reads its prose through the same four methods.

#### How RETALIATION picks and launches

1. **When.** `ExileHegLeagueStrikeManager` sees `state == RETALIATION` on its half-daily tick and
   launches **at state entry**, once per crisis cycle, guarded on
   `director.getLastRetaliationCycle() == director.getCyclesCompleted()`. The cycle is **claimed
   before the attempt**, so a retaliation that finds no legal target is a contained crisis rather
   than a twice-daily rescan.
2. **Who.** Actor = `director.getVictimFactionId()` (the side the incident wronged). Target =
   `director.getOffenderFactionId()`. Not a weighted roll — the wronged side answers.
3. **Budget before target** (the Black Fleet ordering lesson): FP rolls from
   `spawnFpMin..spawnFpMax`, then **"sized by tension"** per §3.2.2 — multiplied by
   `1 + tensionFpScale × (tension − floor) / (cap − floor)`, so a 250–400 FP band becomes 375–600
   at the meter's cap.
4. **Source:** an actor market with a functional spaceport, size-weighted. Exempt systems are
   valid sources (R6a is a targeting rule, not a basing rule).
5. **Target pool**, per candidate market: live `getFactionId()` == target and hostile (R6c);
   primary entity present; **not** in an `exemptSystems` system (R6a, id and base name,
   case-insensitive); no player market in the system; not on `$exiles_hl_op_cooldown`; not in
   `ExileLiveOps.getSystemsWithActiveRaids(null)`; no live op of this director in the system;
   passes `isEligibleVictimMarket` (protected factions, R6b strain markets, R6c allow-list,
   `ExileUnrestGuard.isBattered`); **carries an undisrupted military industry**; passes the
   feasibility gate; then weighted `1 / (1 + distanceLY × distanceFalloff)` from the source.
   A **market** is returned, not a system — a strike is aimed at one colony.
6. **Feasibility is its own gate, not interdiction's** (S4). `enemyStrength(actor, system) +
   stationStrength(target) <= fp × feasibilityMult`. Interdiction **excludes** station strength
   because raiders never fight stations; a strike **includes** it because it has to go through one.
   Do not merge them.
7. **The op:** `ExileHegLeagueAssembleStage` (reused from Pass 2 — its per-fleet FP cap, its
   PATROL_LARGE suppression, and its load-bearing `isSourceKnown()` override) → `TravelStage` →
   `ExileHegLeagueStrikeStage` → `ReturnStage`, posted hidden-until-arrival.
8. **If nothing qualifies**, one permanent log line says so and the cycle passes without a punitive
   op. There is deliberately **no cooldown relaxation fallback** — see deviation 6.

**ESCALATION counter-strikes** use the same `launchStrike()` with a weighted actor roll over
`opWeights.surgical` (Hegemony 3, League 1 — the mirror of interdiction's 1:3), on
`escalationCadenceDays` ±25%, capped at `escalationMaxConcurrentOpsPerSide` live strikes per side.
The clock does not accrue outside ESCALATION, so counter-strikes cannot queue behind a summit.

#### Strike mechanics

- **Industry selection**, on arrival, at the market: every industry that passes the copied
  `isMilitaryIndustry()` (tag-based, vanilla ids as belt-and-braces), is not `POPULATION`, is not
  already disrupted, and is inside `industries` if that list is non-empty. Ordered
  **High Command > Military Base > Patrol HQ > Heavy Batteries > Ground Defenses**, then anything
  else military; the top `maxIndustries` (default **1**) are taken. Knocking out the
  fleet-generating industry is the meaningful act of war, so it is priority-ordered, not random.
  The allow-list can only NARROW — the military-category test is still the floor, so no config
  entry can slip POPULATION or a farming industry past the deciv rules.
- **Duration:** `disruptDaysMin..disruptDaysMax` (45–90) × `extendedDisruptMult` (1.0), applied
  with vanilla max-semantics `setDisrupted(days, true)` — **never additive**, which is the
  drift-to-permanent path.
- **Pressure:** `ExileUnrestGuard.reportExilesPressure(market,
  ExileEmergencyMeasuresConfig.pressureDays(disruptDays, 0))`, once, on a landed strike.
- **Scope:** the struck market only. No condition, no neighbours, no system-wide effect — the
  2026-08-27 ratified split makes system-wide pressure the blockade's signature.
- **Re-checked at impact**, not trusted from launch: a target that went Battered, fell under the
  refugee shield, or lost its last military industry in transit aborts the strike on arrival with a
  log line and no effect.
- **Fleet shape:** `maxShipSize 2` (destroyers, S6/R3 — **no XIV sweep**), `maxFleetFp 90` (higher
  than interdiction's 45: a strike should arrive as a striking force, not raiding packs),
  `abortFraction 0.5`, `maxDays 45`. Stations are **not** suppressed — that is the whole difference
  from an interdiction raid.
- **Bounty and weariness** are inherited verbatim: the **struck** side pays per frigate-equivalent
  striker killed plus `SYSTEM_BOUNTY_REWARD` rep; no payout when the op fleet fought on the
  player's side; weariness is credited side-agnostically to whoever owned the ships the player
  destroyed, once per battle seed.
- **Meter:** a landed strike calls `reportOperationCompleted(actor)` → `+perCompletedOp`. A strike
  that was repelled, timed out, or aborted on arrival reports nothing and says so permanently in
  the log.

#### What changed in the director

Only `fireIncident()` and three new saved fields. To be explicit about what did **not** change:
the six states and their dwells, the `hawkThreshold` branch, General Engagement entry, the summit
outcome roll and all its weights, tension decay, the weariness ledger, the idle conditions, every
Pass-1 debug line, the `reportX()` hooks, and all three Nexerelin layers are byte-identical.
`fireIncident()` gained the table pick, the severity multiplier, the flavour-site pick, the intel
post and a permanent log line; the offender roll now consults the entry's bias before falling back
to `cycle.incidentOffenderHegemonyChance`, and `testing.forceIncidentOffender` still wins over both.

New permanent log line (INFO, log only):

```
Exiles Heg-League INCIDENT: convoy_destroyed | offender=hegemony victim=persean | severity=1.25 site=mazalot | tension now 25.0
```

#### ⚠ THE ARITHMETIC — flagged, NOT tuned (the §14.3 open item)

§14.3 recorded that ESCALATION never fires organically and left it for Pass 3 on the grounds that
"the INCIDENT table is designed to be the main tension driver". **It cannot be, at the ratified
values, and this pass did not invent a rebalance to make it one.** The measured budget, per crisis
cycle, at shipped defaults (`timeScale 1`, meter at the floor):

| Term | Value | Where it comes from |
|---|---|---|
| INCIDENT | **+8.25** | `perIncident` 8 × weighted-average severity 1.03125 (parser-verified) |
| Interdiction ops completed | **+2.50** | Pass-2 long run measured 5 ops / 4 cycles × `perCompletedOp` 2 |
| RETALIATION strike completed | **+1.50** | 1 op × 2, at an assumed ~75% land rate (**guess — the first live test replaces it**) |
| **Total gains** | **+12.25** | |
| TENSION decay | **−6.00** | avg dwell 120d × 0.05/day |
| Summit expected value | **−13.00** | 0.25(−30) + 0.50(−12) + 0.25(−2 + 4) at weights 1/2/1 below the hawk line |
| DE-ESCALATION decay | **−6.00** | 30d × 0.05 × 4.0 |
| **Total losses** | **−25.00** | |
| **NET** | **≈ −12.75 / cycle** | clamped at the floor of 15 |

The meter therefore pins at 15 forever; `hawkThreshold` 60 is unreachable, and so are ESCALATION,
the ESCALATION counter-strike path, and General Engagement. War weariness makes it *worse*, since
it only ever adds to the stand-down weight. **Closing a 25-point gap needs a swing of ~17 points
per cycle across at least two of `gains.perIncident`, `cuts.standDown`/`cuts.partial`,
`tension.decayPerDay` and `deescalationDecayMult` — that is a design decision about how often the
war should visibly boil over, and it is Sean's to make, not a Pass-3 implementation detail.**
Everything downstream of the meter is built and testable today via `testing.tensionOverride`.

One concrete option, for illustration only and **not shipped**: `decayPerDay 0.02`,
`cuts.standDown 12`, `cuts.partial 5` gives ≈ +12.25 − 2.4 − 5.75 − 2.4 = **+1.7/cycle**, about
26 cycles from floor to hawk — probably too slow. Halving the summit cuts *and* doubling
`perIncident` reaches the hawk line in roughly 4–6 cycles. The right answer is between those and
depends on the answer to "how many times per campaign should the sector see an ESCALATION?", which
is a question for the author.

#### Deviations and conservative readings — all flagged, none invented

1. **RETALIATION launches at state ENTRY, not at the end of the lag.** The lag *is* the state's
   dwell (Heg 20d / League 10d, confirmed live in §14.3), and assemble + travel add weeks on top,
   so the answer still materialises well after the incident. Launching on state exit would need an
   exit hook the director does not have and would race the state change.
2. **The branch is still tension-only.** §3.2.2 says the RETALIATION → ESCALATION branch tests the
   hawk threshold; it does not say the op's *outcome* feeds it, and the op cannot finish inside a
   10–20 day dwell anyway. Unchanged from Pass 1.
3. **ESCALATION counter-strikes ship in this pass.** §12 row 4 gives "ESCALATION becomes real" to
   Pass 4, but §4.3 names counter-strikes during ESCALATION as a use of *this* op type, and Pass 2
   already launches interdiction there. Shipping the path now means Pass 3's own matrix exercises
   it rather than Pass 4 inheriting untested code. Own switch (`escalationCounterStrikes`) if Sean
   would rather hold it.
4. **`extendedDisruptMult` ships at 1.0.** §4.6 wants strikes to set a deliberately extended wound
   that recovery convoys drip back down. Shipping >1 before Pass 6 exists would be a permanent
   balance change smuggled in as a seam: nothing in the game could heal it. The key is read, the
   arithmetic is in place, and Pass 6 raises it.
5. **A strike takes ONE industry.** §3.2.2 says "surgical strike on a military industry", singular;
   §4.3's "explicit named-industry list, per-industry durations" describes the Culann *setpiece*,
   which hit five. `maxIndustries` is the dial.
6. **No unrest, and no cooldown relaxation.** §4.3 asks for a disruption and for
   `reportExilesPressure`; it does not ask for `RecentUnrest`, and §1.2 rules this war "economic and
   force disruption, never conquest or decivilization" — Pass 2 sets the same precedent (pressure
   with zero unrest points). Likewise, when every candidate system is on cooldown or hosts another
   director's op, the retaliation simply does not happen and logs why; relaxing a guard to force a
   narrative beat would be a new rule, not a reading of an existing one. Both are one-line changes
   if Sean wants the harder read.
7. **Incident severity multiplies the ratified gain; it does not replace it.** The four shipped
   severities average 1.03, so the mean incident is worth the ratified 8. See the arithmetic block.
8. **`lastRetaliationCycle` is an `Integer`, not an `int`.** XStream builds saved objects without
   running constructors or field initialisers, so a new primitive field returns from a Pass-1/2 save
   as `0` — which would collide with cycle 0 and silently swallow the first retaliation of a
   converted campaign. `null` cannot collide.
9. **Four geography predicates were extracted; the feasibility gates were not.** Three callers now
   need `isExemptSystem` / `isEligibleVictimMarket` / `hasEligibleVictimMarket` / `pickSource`,
   which meets CLAUDE.md §6's measured bar. The bodies were **moved, not rewritten**, and the
   interdiction manager keeps one-line delegates so every Pass-2 call site and behaviour is
   identical. The gates stay private per op type per S4 — and this pass is a live example of why:
   the two now differ on station strength for design reasons.
10. **`countActive()` in the interdiction manager gained one line.** `getIntel(...)` returns
    subclasses, so without `isInterdictionOp()` a live strike would have eaten an interdiction slot
    and quietly throttled already-signed-off Pass-2 behaviour. `hasLiveOpIn()` deliberately still
    counts **both** op types — that check exists to stop two Exiles ops colliding in one system.
11. **The strike's feasibility measurement is cached per SYSTEM**, using the station strength of the
    first candidate market encountered there. A four-market system therefore logs one line rather
    than four, at the cost of a slightly imprecise launch estimate; the tactical `canBeat()` at the
    moment of attack uses the real target's station.
12. **The incident telegraph is NOT hidden.** The sibling `setHidden(true)` pattern is for
    operations, which the player should discover by flying into them — and the strike op keeps it,
    inherited. A telegraph is the opposite instrument. No source-concealment overrides anywhere
    (§3.4), and the reused assemble stage keeps its load-bearing `isSourceKnown()` override.
13. **A landed strike posts no campaign message.** §3.4 lists incident rumour/confirmation, summit
    convened and stand-down announced as the beats that surface; a landed strike is not among them,
    and it already surfaces through the op intel's stage text plus its end update. One line to add
    if it reads too quiet in play.
14. **All player-facing prose is PLACEHOLDER** pending §11 item 2 — the four incident titles, the
    eight rumour/confirmation strings and the strike stage text. Everything is composed from faction
    names and reads correctly in both directions, because either side can be the offender.

#### Nothing was built that needs a new author ruling

No item was left unbuilt for want of a ruling. The arithmetic block above is the one thing that
**needs Sean's decision** — and it is a tuning decision on ratified values, deliberately not taken
here. The outstanding author *deliverables* (§11 items 1 and 2) gate Passes 4/6/7 and the
player-facing text, exactly as §11 predicted; neither blocked this pass.

#### ⚠ SHIPPED-CONFIG WARNINGS (pre-production checklist, cumulative)

1. **`timeScale: 1`** — currently correct for production and correct for a realistic pacing test.
   Drop to 0.1 only to walk the state machine; travel is uncompressed while windows compress, so
   at 0.1 strike groups may reach the target with little of `maxDays` left.
2. **`debugMessages: true`** — false for production.
3. **`testing.tensionOverride: -1`** — currently normal. **Set it to 65+ to test anything above the
   hawk line**, which per the arithmetic block is the only way to reach ESCALATION today. Note that
   it PINS: with it at 65 every cycle escalates, and `generalEngagementHotCycles: 3` means General
   Engagement declares on the third consecutive one.
4. **`surgicalStrike.extendedDisruptMult: 1.0`** — must stay at 1.0 until Pass 6 ships recovery
   convoys.
5. **All strike FP numbers (`250/400`), `tensionFpScale 0.5` and `feasibilityMult 1.0` are
   guesses** awaiting the measurement line.

---

#### PASS-3 LIVE TEST — first session run 2026-08-28 (smoke test + ~14 cycles; findings below)

Sean tests this solo. Everything needed is below; nothing assumes this session is still around.

**Findings so far (2026-08-28; record open — more observations pending):**

1. **FIXED — incident intel never expired (and never confirmed).** The intel manager only
   *displays* intel; it does not advance it. Vanilla timed intel is ticked either by an owning
   manager script (`SystemBountyManager` advances `SystemBountyIntel` via
   `BaseEventManager.advance()`) or by adding itself as a sector script (`RaidIntel.init()` calls
   `Global.getSector().addScript(this)` — with `addIntel` commented out beside it).
   `ExileHegLeagueIncidentIntel` was only `addIntel`'d, so `advanceImpl()` never ran: no
   confirmation update at `confirmDays` (S4 could never pass) and no self-expiry at `intelDays` —
   incidents accumulated on the board forever. Fix: the intel now `addScript`s itself in its
   constructor and removes itself in `notifyEnded()` (the RaidIntel pattern), and
   `ExilesModPlugin.onGameLoad` re-attaches stranded instances from saves made with the bugged
   build (idempotent sweep). Janino-clean 2026-08-28; pending retest.
2. **CONFIRMED LIVE — the §14.3/§14.4 tension arithmetic.** 14 cycles, incident distribution
   6/3/3/2 vs expected 5.25/3.5/3.5/1.75, every severity delta exact, every RETALIATION had
   actor==victim and struck==offender, strikes landed ~9 with 2 failures — and tension peaked at
   37.97 with `hawkThreshold` 60 never approached. ESCALATION remains unreachable at ratified
   values; the tuning ruling is still open.
3. **Minor, unfixed:** the strike outcome lines' elapsed-day figures look unreliable
   ("FAILED after 33d of 32d"; one landed strike logged "after 7d of 40d" following a ≥30-day
   flight). Cosmetic in the log; worth a look only if those counters feed `maxDays` enforcement.
4. **Sean observation (Tyle interdiction): "3 fleets expected, 1 spotted, no picket at the second
   jump point."** Assessed, NO code change: the fleet count is vanilla `RaidIntel`'s launch-time
   ESTIMATE (`getOrigNumFleets()` prose), 45-FP destroyer packets crossing hostile space get eaten
   or straggle piecemeal (worse under `timeScale 0.1`, where travel is uncompressed — judge fleet
   delivery only at timeScale 1), and jump-point picketing is deliberately absent: interdiction
   loiters at the SYSTEM CENTRE (the anti-station-suicide posture), while sealing jump points is
   vanilla `BlockadeFGI`'s native behaviour and therefore Pass 4's signature, per the ratified
   interdiction/blockade split. The §11-item-2 prose pass should stop the intel calling it a
   "raid" so the player isn't invited to expect a blockade.
5. **Sean observation (Westernesse): the op intel reads as "Hegemony posts a bounty" — PENDING
   RULING.** The logic is verified correct (the RAIDED side pays: `victimFactionId` receives the
   credits/rep wiring and the Bounty description section, and the log shows `bounty ... from
   persean`), but the presentation is incoherent: the intel is TITLED "System Bounty" (a
   defender-identity name, per vanilla training) while its crest and UI colours are the ATTACKER's
   (RaidIntel defaults) and the bullet reads "Target: <victim>". At-a-glance that parses as the
   attacker posting a bounty on the defender — backwards. Options for Sean: (A, recommended)
   rename the op intel to an operation-identity title (e.g. "Interdiction of the X star system" /
   strike equivalent), keeping the attacker crest — the payer-coloured Bounty section and
   INTEL_BOUNTY tag already carry the bounty correctly; (B) keep "System Bounty" and re-crest/
   recolour to the victim, rewording the "Target:" bullet to name the attacker. NOTE: the same
    title/crest mismatch exists in the BF/Ashen/Omicron siblings this intel was ported from —
    scope of any change is Sean's call.

**Extended-run confirmation (2026-09-01, natural `timeScale: 1`):**

- League interdiction Fikenhild → Corvus launched at 191 FP, applied the condition to one Hegemony
  market, withdrew successfully, and credited the League's completed operation.
- League interdiction Mazalot → Galatia launched at 155 FP, applied/lifted the condition on one
  Hegemony market, and also ended successfully. Strong Samarra/Valhalla candidates were rejected by
  the logged feasibility gate while softer systems remained eligible, so the gate was not inert.
- A naturally selected violent-inspection incident selected the Hegemony as offender and League as
  victim, moving tension 15 → 23. RETALIATION correctly reversed the parties: a 314-FP League strike
  launched from Olinadu against Hegemony Nachiketa.
- The strike landed its tier-3 package: Military Base, Heavy Batteries, and Fuel Production each
  disrupted for 74 days, Recent Unrest +2, and the three-day orbit hold. It completed successfully
  and credited the League; actor/victim/struck orientation was correct.
- The following political-intervention summit produced a full stand-down to tension 15 and advanced
  the director into cycle 3. No duplicate operation or Exiles runtime error appeared.

This sample confirms the revised strike package and ordinary incident → retaliation → summit loop.
It does not resolve the already-documented inability to reach ESCALATION organically at current
arithmetic, nor replace the remaining Q-matrix/Nexerelin tests. No tuning change was approved from
this run.

**Sean's strike tuning directions (ruled 2026-08-28; BUILT 2026-08-29, Janino 3/3 + config
re-parse clean, UNTESTED — his timeScale-1 rerun tests them).** Sequencing ruled by Sean: build
first, then rerun. Build notes follow each item; the superseded Q-matrix rows are Q16 (one
industry), Q18's "no unrest" implication and Q36's "no RecentUnrest from strikes" clause — a
landed strike now legitimately adds unrest, and Q36's deciv-safety claim now rests on the
`ExileUnrestGuard` rails (Battered arrival re-check + the sector-wide guard) rather than on
adding zero.

**Build record (2026-08-29):** `surgicalStrike.maxShipSize` 2→3 (config only; interdiction keeps
2). New config keys `groundDefenseBucketTension` (0), `economicBucketTension` (0),
`economicIndustries` (ordered list), `orbitDays` (3, timeScale-scaled), `unrestPoints` (2);
`maxIndustries` and `industries` REMOVED from config (superseded by the buckets — their saved
fields remain on the stage class, unread, so an in-flight op from an older save still
deserializes; it restores as tier 0 → clamps to command-only, no dwell, no unrest = its old
behaviour). The package tier is SNAPSHOTTED AT LAUNCH from the tension meter (1 = command,
2 = +ground defense, 3 = +one war-economy industry; both thresholds 0 ⇒ always tier 3, the
ruled default) and carried as an int — the stage never reads the director at impact. Bucket (a)
falls back to any tag-military industry for mod-added garrisons; bucket (c) takes the FIRST
present entry of `economicIndustries` and is double-checked against a hard-coded never-touch
floor (`ExileHegLeagueConfig.isStrikeForbiddenIndustry`: population, spaceport, megaport,
farming, aquaculture, lightindustry, commerce, techmining) that config cannot override —
forbidden config entries are dropped at load with a warning. On landing the stage records the
elapsed time, adds `RecentUnrest` (+2, "<Actor> military strike") — the Battered/EM arrival
re-check already exempted protected markets — reroutes every route onto a `RouteSegment`
orbiting the struck market's primary entity, and `updateStatus` holds SUCCESS back until
`orbitDays` have run, so the group is visibly on station before the return leg (autoresolve
bypasses the dwell; sticky success is preserved — losses during the hold cannot un-land the
strike). Launch and landed log lines now print `packageTier`; the landed line lists every
disrupted industry plus the unrest and orbit hold. The launch-worthiness test
(`hasStrikeableIndustry`) stays military-only: bucket (a) is in every tier, so a market with
nothing military left is not worth the op even though tier 3 could touch its war economy.

The rulings as given:

1. **`surgicalStrike.maxShipSize` 2 → 3 (cruisers), FP unchanged.** Config-only. Supersedes the
   S6 destroyer cap FOR STRIKES ONLY (interdiction stays 2). Implication to note: the destroyer
   cap was what made R3's "no XIV sweep" free — at cruiser size, Hegemony strike fleets will roll
   XIV Eagles/Falcons/Dominators, and dead ones near the battle are recoverable
   ([[npc-combat-derelict-farming]]). Sean is separately building Nex-"Vulture-Scavengers"-like
   debris cleanup himself, which is the systemic mitigation; recommendation is accept without an
   XIV sweep and revisit only if farming shows up in playtest.
2. **A strike needs a visible beat at the target.** Bombardment animation ruled out by Sean.
   His suggestion (endorsed): the strike group holds ORBIT of the target market for a short
   action window before the disruption applies, with action text; optionally a sensor-ping flash
   at the moment it lands and/or a proximity-gated campaign message (deviation 13's one-liner).
   Design open — pick concrete numbers at build time.
3. **Heavier strike package — supersedes deviations 5 and 6.** "Limited warfare" as shipped reads
   too restrained in play (his words). New target set per landed strike, one from each bucket
   where present: (a) command — Patrol HQ / Military Base / High Command (highest present);
   (b) ground defence — Ground Defenses / Heavy Batteries; (c) ONE war-supporting economic
   industry in priority order Fuel Production > Heavy Industry/Orbital Works > Refining > Mining.
   Still NEVER: Spaceport/Megaport, Farming/Aquaculture, Light Industry, Commerce, Tech-Mining
   ("cannot directly support a war effort") — and never Population. Gameplay intent: disrupted
   economic industries ping commodity shortages the player can notice and trade against. Lore
   frame: a strike is the hawks having the upper hand. NOTE: bucket (c) requires widening the
   stage's military-category floor to a bucketed allow-list — the deciv rules still hold
   (disruption is a timer, not a size hit). CORRECTION recorded: Sean believed strikes already
   add Recent Unrest — they do NOT (deviation 6 shipped zero unrest deliberately). His ruling:
   they SHOULD. Add unrest on a landed strike, routed through `ExileUnrestGuard` (Battered
   exemption + deciv guard apply), magnitude TBD at build (suggest 1–2 pts). Assistant
   suggestion, unruled: scale the package with tension (military-only at the floor, full
   three-bucket package at high tension/ESCALATION) to keep "limited war" readable at low heat.

##### Quick smoke test (~10 minutes)

Goal: prove the pass loads, the table fires, the telegraph reads, and one strike sails. Do this
before the full matrix.

**Setup** — edit `data/config/exiles/director_config.json`, block `"exilehegleague"`:

```
"timeScale": 0.1,          # was 1 -- compresses the cycle so an incident arrives in minutes
"debugMessages": true,     # already true
"testing": {
    "forceState": -1,
    "tensionOverride": -1,
    "forceIncidentOffender": "",
    "forceIncidentType": "civilian_casualties",   # the loudest entry, easiest to read
},
```

Start a **new game** (or load a dev campaign — the `hasScript` guards pick the new manager up).
Then, in order:

| # | Do this | Expect |
|---|---|---|
| S1 | Watch the log at startup | No Janino error. `Exiles Heg-League director config loaded from data/config/exiles/director_config.json` and `Exiles Heg-League geography: exemptSystems aztlan=OK thule=OK \| excludedMarkets ...` with **no UNRESOLVED** entries |
| S2 | Let the cycle run to the first INCIDENT (message window: `Heg-League -> 2 INCIDENT`) | Message window shows `DEBUG: TEST: forcing incident type civilian_casualties.` then the state line. Log shows `Exiles Heg-League INCIDENT: civilian_casualties \| offender=... victim=... \| severity=1.5 site=... \| tension now ...` |
| S3 | Open the intel screen, Military tab | An intel titled **"Civilian Casualties (Unconfirmed)"** with the offender's crest, both faction names, the site name, and the word "Unconfirmed". Its description is the hedged rumour paragraph |
| S4 | Wait ~half a day of compressed time (`confirmDays` 5 × 0.1) | The same intel updates to **"Civilian Casualties - Confirmed"**; the description now carries the confirmation paragraph as well |
| S5 | Watch for `Heg-League -> 3 RETALIATION` | Within a tick or two: message window `DEBUG: Heg-League RETALIATION: <faction> -> <market> (N FP, Nd).` and log `Exiles Heg-League SURGICAL STRIKE LAUNCHED: ...` — **or** `RETALIATION found no eligible target`, which is a legitimate outcome; re-roll by letting the next cycle run |
| S6 | Follow the fleets to the target system and open the intel screen while the op is live | The op renders without crashing (the §14.3 `isSourceKnown` regression), shows a map arrow, and carries a **Bounty** section naming the struck faction as payer |
| S7 | Let the strike land (or watch the log if you are out of range) | `Exiles Heg-League SURGICAL STRIKE LANDED: <market> ... -- <industry> disrupted Nd`. Open the market: the named industry shows as disrupted with the right timer |

If S1–S7 pass, the pass is functional. **Restore `timeScale` to 1 and `forceIncidentType` to `""`
before the full matrix** — most of it is about natural behaviour.

##### Full matrix (vanilla, no Nexerelin unless stated)

Config for the main run: `timeScale: 1`, `debugMessages: true`, `testing` all inert
(`forceState: -1`, `tensionOverride: -1`, both force-strings `""`). Per-test overrides are named.

| # | Test | Setup | Expect / pass criteria |
|---|---|---|---|
| Q1 | Clean boot | default | No Janino error; config + geography lines as in S1; **exactly one** of each Heg-League script (director, interdiction manager, strike manager) |
| Q2 | Save/load mid-cycle | default | Config re-read; state, tension, cycle counter, incident id and site all preserved; the incident intel survives with its confirm timer intact; no duplicate managers |
| Q3 | Table distribution | run ~12 cycles at `timeScale: 0.1`, then restore | Collect every `Exiles Heg-League INCIDENT:` line. Over 12 draws expect roughly 4–5 `border_clash`, 3 `convoy_destroyed`, 3 `violent_inspection`, 1–2 `civilian_casualties` (weights 3/2/2/1). **Judge by arithmetic and σ, not by eyeballing a short run** |
| Q4 | Severity reaches the meter | any run | For each incident, `tension now` minus the previous reading equals `8 × severity` (6.0 / 10.0 / 8.0 / 12.0), subject to the floor clamp. If the meter is already at 15 and the summit is about to cut it back, confirm the *log* line `Exiles Heg-League tension A -> B (incident (<id> x<sev>))` shows the right delta |
| Q5 | Offender bias | `forceIncidentType: "violent_inspection"`, ~12 cycles at 0.1 | Roughly 9 of 12 have `offender=hegemony`. `border_clash` under the same treatment should be ~6/12 |
| Q6 | Offender force still wins | `forceIncidentOffender: "persean"` | Every incident has `offender=persean`, whatever the entry's own bias says |
| Q7 | Bad force id | `forceIncidentType: "nonsense"` | Log warns `testing.forceIncidentType "nonsense" matches no table entry; rolling normally`, and incidents keep firing |
| Q8 | Telegraph lifecycle | default | Rumour posts visible at the incident; confirmation update at day 5; intel ends itself ~day 60 and leaves the board. **No incident intel ever accumulates** — check the intel screen after 5+ cycles |
| Q9 | Telegraph off | `incidents.telegraphIntel: false` | No incident intel at all; the meter, the state machine and RETALIATION are unaffected |
| Q10 | Table off | `incidents.enabled: false` | Exactly Pass-1 behaviour: log line reads `INCIDENT: (table off)`, gain is a flat 8, offender is a coin flip, no intel. Retaliation still launches |
| Q11 | **RETALIATION fires** | default, watch 3+ cycles | Every cycle either launches one strike (`SURGICAL STRIKE LAUNCHED ... reason=RETALIATION`) or logs `RETALIATION found no eligible target`. **Never two in one cycle** |
| Q12 | Correct parties | default | The strike's `actor=` equals the incident's `victim=`, and `struck=` equals the incident's `offender=`. Getting these backwards is the most likely logic error in this pass |
| Q13 | **The measurement** | collect every `strike feasibility:` and `SURGICAL STRIKE LAUNCHED:` line over a long run | Record the eligible-vs-rejected ratio and the real `enemy` / `station` split. **Tune `spawnFpMin/Max` and `feasibilityMult` from these, not from intuition.** If rejections dominate, the multiplier is the dial, not the FP. If nothing is ever rejected, the gate is inert and should be tightened. Nothing else in this matrix matters as much |
| Q14 | Sized by tension | `tensionOverride: 15`, note launch FP; then `tensionOverride: 100`, note launch FP | At 100 the launched FP is ~1.5× the value at 15 (`tensionFpScale 0.5`) |
| Q15 | Geography | long run | No strike ever targets an Aztlan or Thule market, none of the five strain markets, none in a system holding a player market, and none in a system on cooldown or hosting another director's op |
| Q16 | Industry choice | inspect several landed strikes | Exactly ONE industry down per strike (`maxIndustries: 1`); it is the highest-priority military industry present (High Command > Military Base > Patrol HQ > Heavy Batteries > Ground Defenses); **never Population, never farming, never a spaceport** |
| Q17 | Duration + max-semantics | inspect a struck market | The industry shows 45–90 days disrupted. Strike the same industry again while it is still down (`systemCooldownDays: 0` to allow it): the timer takes the LONGER of the two, never the sum |
| Q18 | Surgical scope | inspect the whole target system after a landed strike | Only the struck market changed. **No market condition anywhere**, no effect on neighbours, no system-wide accessibility hit. (System-wide pressure is Pass 4's blockade.) |
| Q19 | Fleet shape | watch an arriving strike group | Destroyers and below (`maxShipSize 2`), several fleets of ~90 FP rather than one huge one, Patrol/Lance-tier names — no "Armada" |
| Q20 | Stations ARE engaged | watch a strike arrive at a station-bearing market | The strike group **does** engage the orbital station. This is the opposite of interdiction's P7 and is correct: suppression is deliberately absent here |
| Q21 | Repelled strike | pick a heavily defended target (raise `feasibilityMult` to 5 to force a bad matchup) | The group mills and times out at `maxDays`, the op ends FAILURE, log reads `STRIKE FAILED after Nd of 45d`, **nothing is disrupted**, and the meter does not move |
| Q22 | Abort on arrival | put the target under Emergency Measures / at the unrest cap after the op launches | Log reads `strike ABORTED on arrival ... market is protected or Battered; nothing applied`; the op ends; **nothing is applied** and it repeats no more than once |
| Q23 | Meter feedback | default | A landed strike produces `Exiles Heg-League tension A -> B (operation completed by ...)`. A repelled or aborted one produces the `strike outcome ... FAILED/ABORTED` line and **no** tension movement |
| Q24 | Bounty, correct payer | destroy strike fleets as a third party | Credits and `SYSTEM_BOUNTY_REWARD` rep paid by the **struck** side (= the cycle's offender), one aggregated popup, `INTEL_BOUNTY` tag |
| Q25 | Bounty exploit guard | join the strike group's side and let defenders kill some strikers | **No** payout and **no** rep popup (the 2026-08-27 ruling) |
| Q26 | Weariness attribution | (a) kill strikers as a defender; (b) join the strikers and kill defenders | (a) credits the striking faction; (b) credits the defending faction. Exactly ONE `player-inflicted weariness` log line per battle even with two op fleets present |
| Q27 | Interdiction is unthrottled | run with both managers live | A live strike does **not** stop interdiction launching (`countActive` filter). Over ~10 interdiction launches the 3:1 League:Hegemony ratio from Pass 2 still holds |
| Q28 | Cooldown is shared | after a strike ends | The target system carries `$exiles_hl_op_cooldown` for 60 days and neither a strike nor an interdiction targets it during that window |
| Q29 | ESCALATION counter-strikes | `tensionOverride: 65`, let a cycle reach ESCALATION | `SURGICAL STRIKE LAUNCHED ... reason=ESCALATION counter-strike` on ~30-day cadence, at most 1 live per side, actor mix skewed ~3:1 toward the **Hegemony** (the mirror of interdiction) |
| Q30 | Counter-strike switch | `escalationCounterStrikes: false`, `tensionOverride: 65` | ESCALATION launches interdiction (Pass 2) but no strikes |
| Q31 | Kill switches | `surgicalStrike.enabled: false`; then re-enable and set `$exiles_hl_strike_enabled` false at runtime | No strikes in either case; the runtime memory flag overrides the config |
| Q32 | Retaliation switch | `retaliationEnabled: false` | RETALIATION launches nothing; ESCALATION counter-strikes still work |
| Q33 | Save/load mid-strike | default | Exactly one strike manager; the in-flight op resumes; the once-per-cycle guard is not re-armed (no second strike in the same cycle after the load) |
| Q34 | Converted save | load a Pass-1/2 development campaign | The strike manager is added by the `onGameLoad` guard, and the **first** cycle after the load can still retaliate (the `Integer` sentinel — deviation 8) |
| Q35 | Idle | force the premise loss (§3.3) mid-op | The director idles; no new incidents, no new strikes |
| Q36 | Deciv safety | run 10+ cycles with strikes landing | No market decivilizes. Disruption is a timer, not a size hit; confirm no struck market drops size and that `RecentUnrest` is not being added by strikes |

##### Piggyback items still open from §14.1–§14.3

- **Vanilla item 5 (timeScale cycle walk)** and **item 8 (long-run meter bounds 15–100)** — now
  worth running with the incident table live, though see the arithmetic block for what to expect.
- **Vanilla item 6 (`forceState`)** — note the limitation found while building: with
  `forceState: 3` the cycle counter never increments, so exactly **one** retaliation ever fires,
  and if the pin was applied before any INCIDENT the offender/victim are null and the manager logs
  `RETALIATION skipped: no incident parties this cycle`. **Test the real path by letting the cycle
  reach RETALIATION naturally.**
- **Vanilla item 7 (conquest-shield memory keys read directly)** — still unread.
- **D5 re-run (double-activity sizing)** and **D6 (rebellion pressure)** — D6 is now the sharper
  test: a landed strike on a Core world is more rebellion pressure than an interdiction, and it is
  the CLAUDE.md priority-4 interaction most likely to produce accidental collapse. Watch
  `$nex_rebellionPoints` on a struck market.

### 14.5 Pass 3.5 — The containment amendment [RATIFIED + BUILT 2026-09-13; UNTESTED IN GAME]

Rulings and rationale live in §3.2.5 (R13–R16). This is the build record.

#### What was built

| File | Status | Purpose |
|---|---|---|
| `data/scripts/exiles/ExileHegLeagueConfig.java` | MODIFIED | New `warningThreshold` (tension{}) and `preemptiveCeasefireChance` (cycle{}) with parsing; code defaults for `tensionDecayPerDay` (0.03) and `wearinessDecayPerDay` (0.025) now match the shipped config. Neither new key is timeScaled — a threshold and a probability are not durations. |
| `data/scripts/exiles/ExileHegLeagueDirector.java` | MODIFIED | The three-way RETALIATION exit; `closeCycle()` extracted from the DE_ESCALATION exit and shared with the new `containCycle()`; `findLiveIncidentIntel()` (once per cycle, slow path); two new saved `int` counters `containedCycles` / `escalationsReached`; three permanent log lines (`RETALIATION exit`, `CONTAINED`, `ESCALATION #n reached` — the measurement line); ceasefire vocabulary in the tension-move reasons, the outcome debug line and `getOutcomeName()`. State/outcome constants unchanged. |
| `data/scripts/exiles/ExileHegLeagueIncidentIntel.java` | MODIFIED | Third beat `CONTAINED_UPDATE`; `markContained()` (idempotent, forces confirmation if somehow pending, degrades to generic prose on a pre-amendment intel); `contained` flag in name/bullets/description; `getOffenderFactionId()` / `getVictimFactionId()` accessors for the director's lookup. |
| `data/scripts/exiles/ExileHegLeagueIncident.java` | MODIFIED | `getContainedText()` per type + `getContainedShortDesc()` — PLACEHOLDER prose (§11 item 2). |
| `data/config/exiles/director_config.json` | MODIFIED | `tension.decayPerDay` 0.05 → 0.03; `tension.warningThreshold` 45 (NEW); `weariness.decayPerDay` 0 → 0.025; `cycle.preemptiveCeasefireChance` 0.30 (NEW); comments rewritten around the branch, weariness and the interdiction state list. No key moved, renamed or removed. |

**Not built here, by ruling:** recovery convoys (R16 amends their trigger; they remain Pass 6), the
ceasefire intel beats (Pass 5), the ratcheting floor (deferred).

#### Janino check

`tools/janino-check.ps1` over the four classes — see the session record; the config re-parsed
through the loose-JSON validator.

#### The arithmetic, honestly

Contained cycle at shipped values: gains ≈ +12.25 (incident 8.25 + interdiction 2.5 + strike 1.5,
all §14.4 figures), TENSION decay ≈ −3.6 (120d × 0.03) → **≈ +8.65 net**. Floor 15 → warning 45 in
**3–4 cycles**.

The warning band is *not* a straight climb. Each in-band cycle rolls 0.30 for talks; a successful
roll nets ≈ −8 (gains +12.25, decay −3.6, expected cut −13, de-escalation decay −3.6) and a partial
(−12) drops the meter back below the band while a full ceasefire (−30) resets it to the floor.
Crossing 45 → 60 needs roughly two consecutive contained cycles: P = 0.70² ≈ 0.49 per attempt, each
failure costing 1–4 cycles of recovery. **Honest estimate: warning → hawk in ~4–6 cycles; floor →
hawk in ~8–10; one ESCALATION per ~3.5–4 game years at ~150–160d per contained cycle.** Over a
10–15-year campaign that is the ruled 2–4. Chat's "5–7 cycles / 2–3 years" was optimistic by
roughly 2× because it modelled the band as monotone.

Weariness at 0.025/day: not in the estimate. It accrues only from player kills, so its steady-state
depends entirely on how much the player fights in this war; the periodic meter line prints both
readings and that is where to look.

**Tune the dial from the `ESCALATION #n reached | cycle m | day d` line, not from this section.**
Lower `preemptiveCeasefireChance` for more escalations (0.20 makes the two-cycle crossing ~64%).

#### Deviations and conservative readings — all flagged, none invented

1. **Counters are plain `int`s, not the deviation-8 `Integer` sentinel.** Deviation 8 needed
   `null` because `0` collided with cycle 0. A converted save reading `containedCycles` /
   `escalationsReached` as 0 is simply correct.
2. **The contained beat is delivered by lookup, not by a retained reference.** The director never
   held its telegraph; `findLiveIncidentIntel()` scans the intel manager once per contained cycle
   (the strike manager's `countActive` idiom) and matches on offender/victim. A `transient` field
   would be null after a mid-cycle load anyway.
3. **`markContained()` forces confirmation if it has not landed.** It always has — `confirmDays` 5
   is shorter than either `retaliationLagDays` — but the guard costs one line and removes a
   "contained but unconfirmed" state that would read as nonsense.
4. **CONTAINED resets `consecutiveHotCycles`**, exactly as the old short-circuit did: a contained
   cycle is not a hot cycle. The pre-emptive-talks branch resets it too.
5. **The branch is evaluated when the RETALIATION dwell ends (10–20d), not when the strike
   lands.** Unchanged from Pass 3 — the old summit branch fired at the same moment with the strike
   still in flight. A contained cycle's strike completes under TENSION and still credits its
   `perCompletedOp` gain.
6. **Ceasefire vocabulary changed only in author-facing strings.** Constants, config keys, memory
   keys and the §14 build history keep their spelling (R15). The one log-string reference in the
   §14.1 table is updated in place with its old form noted.

#### Q-matrix additions (append to §14.4's list)

| # | Test | Setup | Expected |
|---|---|---|---|
| Q37 | Containment exit | default; let a cycle reach RETALIATION naturally with tension < 45 | Log: `RETALIATION exit: CONTAINED \| tension …` then `CONTAINED: cycle n …` then `-> 1 TENSION`. **No** `ceasefire outcome` line, **no** DE-ESCALATION transition, **no** tension cut. The incident intel updates to "… - Contained", bullet reads Contained, description carries the protest paragraph, and it still ends on its own timer |
| Q38 | Warning-band roll | `tensionOverride: 50` (pins the meter in the band), 3–4 cycles | **Verify the branch against the logged roll, not the frequency:** each `RETALIATION exit` line prints `roll r vs 0.30`; `r < 0.30` must be followed by `-> 5 POLITICAL INTERVENTION`, otherwise by `CONTAINED`. That is deterministic and needs only a handful of cycles. A frequency check (≈30% talks) needs **30+ cycles at `timeScale 0.1`** — at 10 cycles σ ≈ 1.5 on an expected 3, which distinguishes nothing (the standing testing lesson) |
| Q39 | **Organic ESCALATION** | `tensionOverride: -1`, `timeScale: 0.1`, long run | `ESCALATION #1 reached \| cycle m` appears **with no overrides**; record `m` and the day. Strikes will mostly fail to land at 0.1 (their travel is uncompressed) — that costs only the +1.5/cycle term, so expect `m` a little above the §14.5 estimate. Confirm once at `timeScale 1` |
| Q40 | Meter bounds re-run (vanilla item 8) | as Q39 | Tension never below 15 or above 100 with the new branch live; weariness readings on the periodic line fall between cycles when the player has not fought |
| Q41 | Post-escalation talks still mandatory | force or reach ESCALATION | ESCALATION always exits to `5 POLITICAL INTERVENTION`, the `ceasefire outcome` line fires, and the cut values are the ratified 30/12/2 — the amendment touched none of them |
| Q42 | Converted save | load a Pass-3 campaign | Counters read 0; a mid-cycle incident intel from the old build takes the contained update with the generic protest line (`containedText == null` path); the first RETALIATION exit after load branches normally |

### 14.6 Pass 3.5 live-test record + the cooling fix [TRACE READ 2026-09-18; FIX RULED + BUILT 2026-09-19; UNTESTED]

Rulings and diagnosis in §3.2.6. This is the evidence and the build record.

#### The trace, as logged

~1,600 game days, 9 complete cycles, `timeScale 1`, `tensionOverride -1`, no player involvement in
the war (zero `player-inflicted weariness` lines), zero Exiles exceptions.

| Cycle | Exit tension | RETALIATION exit | Talks outcome | Notes |
|---|---|---|---|---|
| 0 | 30.8 | CONTAINED | — | below warning |
| 1 | 42.8 | CONTAINED | — | below warning |
| 2 | 48.0 | CONTAINED (roll 0.72 vs 0.30) | — | band roll failed, branch matched roll |
| 3 | 61.3 | **ESCALATION #1** (day 528) | partial (−12) | 45d window |
| 4 | 63.0 | ESCALATION #2 (day 725) | collapse (+2) | |
| 5 | 70.8 | ESCALATION #3 (day 928) | collapse (+2) | **75d — General Engagement** (3 consecutive hot cycles) |
| 6 | 83.2 | ESCALATION #4 (day 1133) | **full (−30)** | 75d — General Engagement; reset the hot-cycle counter |
| 7 | 64.7 | ESCALATION #5 (day 1433) | collapse (+2) | 45d |
| 8 | 67.9 | ESCALATION #6 (day 1578) | partial (−12) | 45d |

Meter bounds: min 20.8, max 85.2. Decay verified at 0.03/day (25 → 20.8 over the first 139-day
quiet stretch). Tension ledger by reason: incidents 9 / +82.0; ops completed 24 / +48.0 (Hegemony
11, League 13); ceasefire holds in full 1 / −30; holds in part 2 / −24; collapses 3 / −6 with
+12 talks-collapsed gains. Strikes: 9 RETALIATION + 7 ESCALATION counter-strikes launched, 12
landed, 4 failed. Interdictions: 26 launched.

**Matrix verdicts:** Q37 PASS · Q38 PASS (one roll, branch matched) · Q39 PASS (organic at cycle 3,
day 528) · Q40 PASS · Q41 PASS (cuts exactly 30/12/2). General Engagement fired twice via the
consecutive-hot-cycles route and the full stand-down correctly reset the counter — coded behaviour
to the letter. **The tuning failed the target** — see §3.2.6.

#### What was built (the cooling fix)

| File | Status | Purpose |
|---|---|---|
| `ExileHegLeagueConfig.java` | MODIFIED | `outcomeCollapseTensionWeight` 0.05 → 0; `deescalationDecayMult` 4 → 8; `wearinessDecayPerDay` 0.025 → 0.10; new `wearinessPerStrikeSuffered` (12), `wearinessPerInterdictionSuffered` (4), `wearinessPerOpFailed` (8) with parsing. Amounts are never timeScaled. |
| `ExileHegLeagueDirector.java` | MODIFIED | `addWeariness(faction, amount, reason)` logged overload — the periodic meter line is debug-only, so this is the only permanent weariness trace; `reportOperationWeariness(actor, victim, landed, strike)`. |
| `ExileHegLeagueStrikeIntel.java` | MODIFIED | `reportOutcome()` reports weariness for BOTH outcomes before the existing landed-only tension report. |
| `ExileHegLeagueRaidIntel.java` | MODIFIED | Same, for interdictions; the "ended WITHOUT completing" log line is unchanged. |
| `director_config.json` | MODIFIED | The three dials + three new keys, comments carrying the trace evidence. No key moved, renamed or removed. |

#### Deviations and conservative readings — all flagged, none invented

1. **Outcome-based, not per-FP.** The player path counts destroyed FP from a battle snapshot; the
   NPC feed credits a flat amount per op outcome. Chosen for determinism and because no battle
   listener exists for NPC-only battles (the raid intel's own scope note). Coarser, tunable.
2. **"Did not land" over-counts timed-out ops** as if fleets were lost. Accepted: a strike group
   that sat 32 days and never landed did take attrition, and the alternative needs stage-level
   failure reasons that do not exist.
3. **Lever 4 staged, not applied** (§3.2.6 ruling 4) — assistant's call on attribution, disclosed.
4. **Weariness decay is still linear.** A proportional decay would be nicer; changing the saved
   field's semantics was not worth it for a first calibration.

#### Q-matrix additions

| # | Test | Setup | Expected |
|---|---|---|---|
| Q43 | NPC weariness feed | default, watch one cycle with a landed strike | Log: `weariness Heg X / League Y (<side> +12.0: strike landed on <side>)` on the struck side; `+4.0: interdiction completed against` on victims; `+8.0: ... did not land` on the actor of a failed op. Readings fall between cycles at 0.10/day |
| Q44 | Cooling after ESCALATION | let an organic ESCALATION occur | Talks outcome logged; meter after DE-ESCALATION lands **below 45** in most cases (expected ~41 from 65); the next cycle contains rather than re-escalating |
| Q45 | Rate | long run, `timeScale 1` | `ESCALATION #n reached` lines: target one per ~4+ cycles and **no** General Engagement via the consecutive route in a ten-cycle span. If still hot, apply lever 4 (§3.2.6) |
| Q46 | Collapse weight at 0 | force tension 80 via `tensionOverride`, reach talks | Outcome weights logged as `standDown A / partial 2 / collapse 1` — the collapse figure no longer grows with tension |

#### Live-test record — run 2, the cooling fix [READ 2026-09-19; 3-year checkpoint PASSED]

New game, `timeScale 1`, no overrides, no player combat, **game speed x50** (Sean's testing
setting; see the x50 note below). 1,164 game days (3.19 years), 9 complete cycles, zero Exiles
exceptions.

| Cycle | Exit tension | RETALIATION exit | Talks outcome | After DE-ESC |
|---|---|---|---|---|
| 0 | 28.7 | CONTAINED | — | — |
| 1 | 39.8 | CONTAINED | — | — |
| 2 | 47.4 | CONTAINED (roll 0.55 vs 0.30) | — | — |
| 3 | 55.6 | CONTAINED (roll 0.62 vs 0.30) | — | — |
| 4 | 64.1 | **ESCALATION #1** (day 570) | partial (−12) → 54.1 | **46.9** |
| 5 | 55.0 | **CEASEFIRE TALKS (pre-emptive, roll 0.19)** | **full (−30) → 25.0** | **19.8** |
| 6 | 23.7 | CONTAINED | — | — |
| 7 | 37.5 | CONTAINED | — | — |
| 8 | 51.3 | CEASEFIRE TALKS (pre-emptive, roll 0.19) | partial (−12) → 39.3 | 34.1 |

**The loop is broken.** Run 1 re-escalated 5 times in a row from a 55–85 band. Run 2's one
escalation was followed by a partial ceasefire, 7.2 points of DE-ESCALATION cooling (verified exact:
54.1 → 46.9), one pre-emptive talks success, and a full stand-down that put the meter back at the
floor. Cycles 6–7 then contained from 24 and 37. Zero General Engagements. **Q43 PASS · Q44 PASS ·
Q46 PASS.** Q45 (rate) needs the 7-year run: one escalation in 3.2 years extrapolates to ~3 per
ten — inside the 2–4 target — but that is one data point and the two pre-emptive successes both
rolled 0.19, which is luck, not tuning.

**The weariness feed, as logged (20 credits):** +12 per landed strike to the struck side, +4 per
completed interdiction to the victim, +8 to the actor of the one strike that did not land; decay
visible between cycles (Hegemony 27.8 → 11.5 across the late quiet stretches). Peak total ~28 across
both sides = +0.56 stand-down weight; at the post-escalation talks it was ~28 → P(full) ≈ 34%,
against the §3.2.6 calibration target of ~40%. Close; not worth touching on one sample.

**Ledger:** incidents 9 / +72 (avg 8.0 — lower severity draws than run 1's 9.1); ops completed
19 / +38 (+4.2/cycle vs run 1's +5.3); contained cycles netted ≈ +8–9. Strikes 9 landed / 1 failed;
interdictions 15 launched, all completed. Op success rates are comparable to run 1.

**x50 game speed: no effect found.** Every decay arithmetic check reconciles exactly (DE-ESC drops
of 7.22 / 5.24 / 5.21, the latter two each with one +2 op credit inside the window), op completion
rates match run 1, no exceptions. Rolled dwells are values, not elapsed time, so speed cannot bias
them. Cleared for testing use.

**Watched, not a defect:** TENSION dwells averaged 85 days from U(60,180) — ten draws that low is
~1-in-1,000. `roll()` is a clean uniform on an unseeded `Random` (verified); run 1's non-collapse
draws averaged 121. Logged so a third low run gets looked at.

**Lever 4 stays staged.** Decision at the 7-year mark from `ESCALATION #n reached` counts.

#### Live-test record — run 2 extended to 7 years [READ 2026-09-19; COOLING CONFIRMED; DWELL ANOMALY OPEN]

Same save continued: **2,515 game days (6.89 years), 20 complete cycles**, `timeScale 1`, x50 game
speed, no player combat, zero Exiles exceptions, **zero General Engagements**.

| Cycle | Exit | Talks | Notes |
|---|---|---|---|
| 0–3 | CONTAINED ×4 | — | climb from 25; band rolls 0.55, 0.62 failed |
| 4 | **ESCALATION #1** (day 570) | partial −12 | → 46.9 after cooling |
| 5 | pre-emptive talks (0.19) | **full −30** | → floor |
| 6–7 | CONTAINED | — | |
| 8 | pre-emptive talks (0.19) | partial −12 | |
| 9 | CONTAINED | — | |
| 10 | pre-emptive talks (0.20) | **full −30** | → floor |
| 11–13 | CONTAINED ×3 | — | roll 0.50 failed at cycle 13 |
| 14 | pre-emptive talks (0.01) | collapse (+2) | the run's only collapse |
| 15 | **ESCALATION #2** (day 1904) | partial −12 | → 47.1 |
| 16 | CONTAINED (roll 0.96 failed) | — | 54.6 |
| 17 | **ESCALATION #3** (day 2198) | **full −30** | → 33 |
| 18 | CONTAINED | — | |
| 19 | pre-emptive talks (0.06) | **full −30** | → 17.7 |

Meter bounds 17.7–66.2. Ledger: incidents 20 / +170 (avg 8.5); ops completed 40 / +80 (+4.0 per
cycle); ceasefires 8 — full 4, partial 3, collapse 1 (post-escalation: partial, partial, full).
Strikes 18 landed / 4 failed; interdictions 35 launched, 1 driven off. Weariness: 45 credits, peak
total 31 across both sides; the feed and its decay behave exactly as built.

**Verdict on the cooling fix: confirmed.** The loop that held run 1 in a 55–85 band never
re-formed. After each escalation the meter came back below the warning line within one or two
cycles, twice all the way to the floor. Escalations #2 and #3 were two cycles apart because #2's
partial ceasefire left the meter at 47 — inside the band — and cycle 16's roll (0.96) failed; that
is the designed rhythm, not the old feedback loop.

**Rate: 3 escalations in 6.9 years ≈ 4.4 per ten years — top edge of the 2–4 target, with two
opposing corrections that roughly cancel:**
- *Luck pushed the rate DOWN:* pre-emptive talks succeeded on 5 of 9 band rolls (0.19, 0.19, 0.20,
  0.01, 0.06) against a 0.30 chance — expected ~2.7. At the true chance the run would likely have
  seen one or two more escalations.
- *The dwell anomaly pushed the rate UP* (next paragraph): quiet stretches ran ~30% short, so 20
  cycles fit in 6.9 years instead of the ~9 they would occupy at designed dwells.

**⚠ OPEN — TENSION dwell distribution is wrong in this run, cause unknown.** Twenty TENSION
dwells drawn from a 60–180 uniform averaged **84** with none above 143 (expected mean 120, half the
draws above 120): p ≈ 10⁻⁶. Run 1, on identical `rollStateDays()`/`roll()` code, averaged 121.
The raw uniform draws implied by run 2's dwells average 0.19, and its nine logged band rolls
average 0.36 — the same `Random` looks low in both. Checked and cleared: `roll()` is a clean
`min + nextFloat()×(max−min)`; the config bounds are 60/180 in the file, `timeScale` 1, no
duplicate keys, no change to the cycle block between runs; every fixed dwell in the log is
config-exact; the `stateDaysRemaining < 0` re-roll guard never fired (0 double TENSION entries); the
director's clock tracks real days (dwell sum to ESCALATION #1 = 564 vs logged day 570). **The only
known difference between the runs is x10 vs x50 game speed, and no mechanism for that is visible.**
A diagnostic now logs the raw draw and the bounds at every TENSION roll
(`Exiles Heg-League TENSION dwell roll: raw r x [min, max] -> base`). **Q47:** the next run — at
x10 — reads those lines: if the raws are uniform, x50 is implicated and testing runs at x10; if the
bounds print as something other than 60/180, the config path is wrong somewhere unseen.

**Lever 4 stays staged.** Do not tune the ceasefire chance until the dwell question is settled: at
designed dwells the same 20 cycles span ~9 years and the rate is ~3.3 per ten — in target without
touching anything. Deciding on a distorted clock would be tuning the wrong number.

### 14.7 Run 3 (x10) — Q47 closed + three strike-pipeline fixes [READ + BUILT 2026-09-19; Janino 3/3; UNTESTED IN GAME]

**Run 3: ~2.4 years at x10, 6 cycles closed, 7 TENSION dwell draws.** The diagnostic fired every
time with bounds `[60.0, 180.0]`, and every logged dwell equals `60 + raw × 120` (the one halved
draw, cycle 3, is 115.6 → 57 with talks collapsed). Raws 0.90 / 0.35 / 0.17 / 0.46 / 0.34 / 0.67 /
0.02 — mean 0.42, 0.8σ below 0.5. Base mean 110.

| run | speed | draws | mean base dwell (collapse halving undone) | z |
|---|---|---|---|---|
| 1 (09-18, hot) | — | 10 | 122.9 | +0.3 |
| 2 (7-yr cooling) | x50 | 20 | 87.3 | −4.2σ |
| 3 (this) | x10 | 7 | 109.9 | −0.8 |

Runs 1+3 together: n=17, mean 117.6. **Q47 answer: the RNG, the bounds and the arithmetic are
clean. Run 2 is a lone unrepeated outlier with no mechanism found.** The diagnostic stays in (one
line per cycle); testing stays at x10; **lever 4 stays staged.** If run 2's skew ever recurs, the
only suspect left is `java.util.Random` itself — swap for `Misc.random` and re-measure. Note for
readers of §14.6: its 84 was computed on the logged dwells *including* the halved one; un-halved it
is 87.3, same conclusion.

**Run 3 health:** exits 4 CONTAINED / 1 pre-emptive ceasefire (roll 0.28 vs 0.30, then collapsed) /
1 ESCALATION (#1, cycle 4, day 612; the band roll the cycle before was 0.81). 0 General
Engagements. The post-escalation ceasefire held in full (60.5 → 30.5), meter 24 by the next cycle.
Range 20.7–60.5. NPC weariness feed peaked ~13. Offender was Hegemony on all six incidents —
p ≈ 3% from the table's weights, and 43 Heg / 24 League across all three runs (1.3σ): variance.
Cycle 0's strike disrupted Nachiketa's `fuelprod` for 69d — that market is narrative-load-bearing
(§2 timeline), and whether it belongs in the strike pool is an author call, not a defect.

**Three findings in the strike pipeline, all built the same day:**

1. **Feasibility gate cached the FIRST market's station per system** (`pickStrikeTarget`). Cycle 3:
   `Valhalla eligible (344 = enemy 344 + station 0 ≤ 428)`, then `LAUNCHED → Ragnar Complex …
   defense=584 (344 + station 240) gate=428` — the launch line showed the target failing its own
   gate, and the strike failed 34 days later. Every Valhalla feasibility line in the log carried
   `station 0`. **Fix:** enemy strength is measured once per system (`enemyBySystem`); the station
   is measured per market and the gate is applied per market. Log shape changed: a system whose
   fleets alone exceed the gate logs one `REJECTED (enemy N alone > gate …)` line; otherwise each
   candidate market logs its own `<Market> in <System> eligible/REJECTED (defense = enemy + station …)`
   line. Q13 readers: collect on `strike feasibility:` as before.
2. **`strike.maxDays` was a dead knob.** Vanilla `PirateRaidActionStage.updateRoutes()` runs at
   stage start (`notifyStarted`) and overwrites `maxDays` with its own 20–30 (+10–15 with a
   station) roll; the single sub-step drains on the first tick, so an offscreen op autoresolved at
   vanilla's number — which is why every outcome line read `after 43d of 43d`, `37d of 37d`,
   `34d of 34d`. `timeScale` on it was dead too. **Fix:** the stage keeps `configuredMaxDays`
   (new saved field; a pre-fix in-flight op restores 0 and keeps vanilla's value) and an
   `updateRoutes()` override restores it after `super`. **Behaviour change to expect:** offscreen
   strikes now land or fail at exactly `maxDays` (45) after arrival instead of 34–43; live fleets
   are unaffected (`canRaid` → `canBeat` per tick as before).
3. **`arcadia_station: STRIKE FAILED after 0d of 0d`.** Same vanilla `updateRoutes()`: on arrival it
   compares the group's in-system strength with the defenders' and, if the group cannot win, clears
   the target list and zeroes `maxDays`; our `updateStatus` then autoresolved and failed on the
   same tick — a month's sailing ending without a beat. With fix 2 the group now holds in-system
   for the window and `canBeat` is asked again at the end. **Measurement line added** at arrival:
   `Exiles Heg-League strike ARRIVED at <System> -- <Market>: group strength S vs defense D
   (enemy=… + station=…) | vanilla arrival check accepted|found no beatable target, vanilla window
   Nd -> window 45d`. Read it before tuning anything: it separates "defence grew in transit"
   (vanilla's `MilitaryResponseScript` pulls patrols toward raiders) from "measured before the
   group was fully in-system".
   **Sean's theory (2026-09-19), to test against that line:** the test campaigns run WITH Nexerelin,
   whose response fleets spawn from a market's defence reserve when a significant hostile force
   approaches, then patrol, defend against raids and even move between systems. That would inflate
   the defenders' strength at the arrival instant relative to the launch-time feasibility read,
   which is exactly the profile of the `0d of 0d` failures. Ruling: do NOT counter it unless it
   causes significant problems; it is another mod's feature. The restored window (fix 2) is the
   right amount of mitigation, since the group now holds and re-asks `canBeat` as those fleets
   disperse. Confirm by comparing the `ARRIVED` line's enemy figure with the launch feasibility
   line for the same op, and by grepping the log for Nexerelin response-fleet spawns in that system.

**Test matrix additions:**

| # | Check | Expect |
|---|---|---|
| Q48 | Every `SURGICAL STRIKE LAUNCHED` line | `defense … gate` on the launch line never exceeds the gate (it did at Ragnar Complex) |
| Q49 | Every `strike outcome` line | `of 45d`, never a vanilla 20–45 number; no `0d of 0d` |
| Q50 | Every `strike ARRIVED` line | present once per strike, before its outcome; note how often `found no beatable target` appears and whether those ops later land at the window's end |
| Q51 | Dwell diagnostic, next 20 draws at x10 | raw mean within ~0.13 of 0.5 (2σ); if a run-2-style skew recurs, swap `Random` for `Misc.random` |

### 14.8 Run 4 — Sean's 5-year Nexerelin run [READ 2026-09-20; ALL THREE §14.7 FIXES CONFIRMED; COOLING HOLDS]

Fresh campaign, ~1,780 game days (c206 → c210), Nexerelin on, non-Exile start, player never
fought. Zero Exiles exceptions. Game speed changed mid-run (~14M ms), so wall-clock gaps in the
log are not comparable across that point; every duration below is read from the director's own
day/cycle stamps.

**Cooling (§3.2.6): CONFIRMED AGAIN.** 11 crisis cycles. **2 ESCALATIONS** (cycle 4, day 751,
tension 62.7; cycle 10, day 1708, tension 62.8), **0 General Engagements**, one pre-emptive
ceasefire (cycle 5, roll 0.16 vs 0.30). After each escalation the meter cooled below the warning
line (47.5 → 17.6 after #1; 34.8 → 31.6 after #2). Peak tension in the whole run: 64.8.

**NPC weariness feed: WORKING, first live read.** 27 credit lines; values ranged 0–15.8 and
decayed visibly between events (e.g. Heg 12.0 → 7.97 → 1.07 across ~60 days). Escalation #2 ran
into Heg 14.4 / League 12.0 and DE-ESCALATION printed `holds in full` (64.77 → 34.77). Escalation
#1 at lower weariness printed `holds in part` (66.71 → 54.71). Both branches exercised.

**Q47 / dwell: stays CLOSED.** 12 `TENSION dwell roll` lines, raw 0.08–0.92 (mean 0.59), all
inside [60,180]. Actual TENSION durations match the rolled dwell once the speed change is
accounted for (cycles 3, 4, 5, 8, 9 read 156/127/90/128/70 days against rolls of 165/126/89/126/69).
The pre-fix anomaly did not reproduce.

**Fix 1 (per-market station in feasibility): CONFIRMED.** 81 feasibility lines; 45 carry a
non-zero station term, 28 REJECTED (including the new one-line `enemy N alone > gate` form for
Samarra/Corvus/Valhalla). No `station 0` on a market that has one.

**Fix 2 (`strike.maxDays` restored): CONFIRMED.** 14 strikes, **13 LANDED, 1 FAILED, every one
`after 45d of 45d`** — offscreen ops resolve at the configured window, never vanilla's 22–41d
(the `ARRIVED` lines print vanilla's roll beside the restored 45). Note for tuning: with the
player never nearby, *every* strike lands on exactly day 45 after arrival; that is the offscreen
model, not a defect, but it means the `orbitDays` hold and package logic only differ on-screen.

**Fix 3 (arrival gate): CONFIRMED, and it produced a designed failure.** Yama, cycle 7: launched
at defence 271 (enemy 151 + station 120), arrived to `group strength 548 vs defense 1059 (enemy=939)
| vanilla arrival check found no beatable target, vanilla window 0d -> window 45d`. The group held
45 days, `canBeat` was re-asked at autoresolve, still lost, and the op ended `STRIKE FAILED after
45d of 45d` with `League +8.0: strike by League did not land` — the first time a strike has
failed for the right reason instead of on the arrival tick.

**Arrival-vs-launch defence (T2a, Sean's Nexerelin theory): MEASURED, CAUSE STILL OPEN.** 13
launch/arrival pairs. 9 were within ±20% (enemy 151→113, 223→260, 140→143, 117→97, 174→128,
163→119, 210→184, 155→145, 181→62). **4 grew 2.6–7×**: Yama 146→582 (landed at 708 vs 702),
Suddene 67→456, Mazalot 187→494, Yama 151→939 (the failure). All four are in Naraka, Westernesse
and Zagan. The log has **no Nexerelin response-fleet spawn lines under any name**, so the theory
is neither confirmed nor excluded from this run; Nex may simply not log them at INFO. The
alternative that IS in the log: those three systems were simultaneously hosting Black Fleet
commerce raids and assaults and Heg-League interdictions (Naraka took a Black Fleet assault, two
Heg-League interdictions and three strikes in cycles 7–9 alone), so vanilla patrol and military
response surges are a sufficient explanation on their own. Either way the restored window turns
the spike into a held-then-decided outcome, which is the intended shape. No action.

**Not tested this run:** the starting debt (absent — Nex start, no Exile eligibility marker) and
anything on-screen.

### 14.9 Pass 4 — System blockades: PLAN [RATIFIED 2026-09-20, D1–D8 ALL RULED] + BUILD RECORD (§14.9.6) [BUILT 2026-09-20; Janino-clean; LIVE-TESTED 2026-09-21 — log-observable checks PASS, on-screen checks pending, §14.12]

#### 14.9.1 The whitelist — DELIVERED 2026-08-27, REVISED by Sean 2026-09-20 (the revision is canon)

`docs/Exiles Design Documentation/Hegemony_League_War_Whitelists.docx` first landed 2026-08-27,
the day after R6 was ruled, but was never transcribed, so §2, §11 item 1, §12 and every build-log
entry since called it "pending". Found 2026-09-20; Sean then revised it the same day (the
Hegemony side had been badly incomplete) and copied the revision into the mod's `docs/` folder.
Transcription of the **revised** list, vanilla ids resolved from
`starsector-core/data/campaign/econ` (ownership is always queried live in code — this table is
the author's intent, not a hardcoded list):

| Author list | Author entries | Resolved id (system · owner · size) | Consumer |
|---|---|---|---|
| League target **markets** (for Hegemony planetary blockade) | Athulf, Cibola, Fikenhild, Laicalle Habitat, Madeira, Suddene, Yesod | `athulf` (Westernesse · 4), `cibola` (Tyle · 5), `fikenhild` (Westernesse · 6), `laicaille_habitat` (Isirah · 4), `madeira` (Tyle · 6), `suddene` (Westernesse · 5), `yesod` (Zagan · 5) | Pass 6 |
| League target **systems** (for Hegemony system blockade) | Isirah, Tyle, Westerness | `isirah`, `tyle`, `westernesse` | **Pass 4** |
| Hegemony target **markets** (for League planetary blockade) | Ancyra, Citadel Arcadia, Jangala, Nachiketa, Raesvelg, Sphinx, Yama | `ancyra_market` (Galatia · 5), `arcadia_station` (Arcadia · 4), `jangala` (Corvus · 6), `nachiketa` (Naraka · 4 under Exiles), `raesvelg` (Valhalla · 5), `sphinx` (Samarra · 5), `yama` (Naraka · 6) | Pass 6 |
| Hegemony target **systems** (for League system blockade) | Naraka, Samarra | `naraka`, `samarra` | **Pass 4** |

**Author rulings taken while revising (Sean, 2026-09-20):**

- **Aztlan and Thule stay off every list.** A blockade of a capital system is a blatant act of
  war: the pre-game one started this mess, a second by either side would mean all-out war.
  R6a's system-level exemption stands as the belt-and-braces in code.
- **Valhalla stays OFF the system list; Raesvelg stays ON the market list.** Analysed at
  Sean's request. Valhalla is all-Hegemony (Skathi included once transferred), carries the vanilla
  Ragnar Complex quest chain, and is the planned base of the admiral leading the Hegemony's war
  (Sean's "halfway plan"), so a system blockade there would be maximum disruption with no
  collateral tier to soften it, and the admiral's fleet would make it fail feasibility or, under
  D3's scale-up, send an inflated League fleet to die there. The Raesvelg planetary squeeze is a
  scalpel that only lands when the admiral is away — the better emergent story. A League strike at
  the admiral's home is fires-once quest material, not director behaviour.
- **Arcadia moved from the system list to the market list.** Only one Hegemony market there, so
  a planetary blockade of Citadel Arcadia is the right shape and a system blockade was overkill.
- **Samarra added** knowing Sphinx is now the sensitive Hegemony fuel asset (Nachiketa's nerf
  leaves Askonia the Core's fuel king — Sean's note, recorded in memory). **Naraka added** with
  the Nachiketa fuel wound acknowledged; the author's call.

Things the transcription surfaces (none blocks Pass 4):

1. **The system-blockade map is 3 : 2.** Hegemony: Isirah, Tyle, Westernesse. League: Naraka,
   Samarra. Near parity, so the per-side `opWeights.blockade` (3 : 1) is the real driver of who
   blockades more — which is the intended lever.
2. **Collateral in the five systems** (the D2 split): Isirah — Kapteyn Starworks (**Pirates** 4,
   battlestation + corrupted nanoforge under Exiles); Westernesse — Ailmar (Indep 5); Tyle, Naraka,
   Samarra — none (all-Hegemony or all-League). So the lighter collateral condition will actually
   appear only in Isirah and Westernesse; in the other three every market is the victim's and
   takes vanilla `Blockaded` at full tier. No refugee, strain, Church or player market sits in any
   of the five. Feasibility note: `WarSimScript.getEnemyStrength` counts everything hostile to the
   blockader, so Isirah's pirate patrols and the Kapteyn station inflate its defence for a
   Hegemony blockade. The measurement line will say by how much; do not pre-tune around it.
3. **Ancyra (Pass 6 only)** is created in `Galatia.java` "not connected to the rest of the economy
   to start with" and joins on Galatia-tutorial completion. `isEligibleVictimMarket` requires
   `isInEconomy()`, so under a start that never completes Galatia it silently leaves the pool. Pass
   6's first-boot geography line must report it; nothing for Pass 4.
4. **Planetary-only sites:** Yesod (Zagan), Ancyra (Galatia), Citadel Arcadia (Arcadia), Jangala
   (Corvus), Raesvelg (Valhalla) sit in systems not on the system lists. Jangala shares Corvus
   with the refugee station and Asharu (strain) — a planetary squeeze touches Jangala only, which is
   why Corvus is legal as a market but not as a system.

Both whitelists go into config now (`blockadeWhitelistSystems`,
`planetaryBlockadeWhitelistMarkets`, the §9 names) so the deliverable is captured once; Pass 6
reads the second.

#### 14.9.2 Shape — what gets built

Fourth-sibling discipline (S1/S4): a new manager that READS the director and owns no config; a thin
`BlockadeFGI` subclass; every tunable in the one JSON; no shared control flow with any other
director. Reuse is `AshenBlockade` + `AshenInterdictionManager.launchBlockade()` (§13), copied and
parameterised, not called.

| File | New/Mod | Contents |
|---|---|---|
| `data/scripts/exiles/ExileHegLeagueBlockade.java` | NEW | `extends BlockadeFGI implements FleetEventListener`. Copies from `AshenBlockade`: `SpreadBlockadeAction` (hold EVERY gate — vanilla's 1/N weighting is the wrong shape here too), anchor/picket naming + `NPCHassler` on pickets, `isBrokenUp()` damage abort, `suppressStationTargeting()`. **Drops** the companion-raid abort (standalone op, D4) and the source-concealment overrides (§3.4: sources are public). **Adds:** (a) the collateral loop on the existing periodic tick (D2 as RULED): vanilla's own `applyBlockadeCondition()` / `unapplyBlockadeCondition()` are **left untouched** and keep stamping the victim faction's markets at full tiered force; a second loop over `Misc.getMarketsInLocation(system)` applies-or-refreshes `exile_hl_blockade_collateral` on every other market passing the guard, passing the current tier-scaled penalty as a plain `Float` param, and removes it from any market that no longer qualifies (ownership flip) — both sets recomputed live each tick, no saved list; `notifyEnding()` removes the collateral condition explicitly, the condition's own timer is the backstop; (b) `reportBattleOccurred` → the bounty/weariness body (D4); (c) `notifyEnding()` → outcome report to the director + end-start system cooldown (the interdiction precedent, same `$exiles_hl_op_cooldown` key); (d) reveal on payload start when `hiddenUntilArrival` (one `setHidden(false)` on the first tick where `isCurrent(PAYLOAD_ACTION)`). Implements the shared `ExileBlockadeIntel` interface (below). Saved fields: actor/victim ids, `outcomeReported`, `revealed`, `abortDamageFraction`. |
| `data/scripts/marketconditions/ExileHegLeagueBlockadeCollateral.java` | NEW | `extends ExileTimedCondition`, id `exile_hl_blockade_collateral` (**RULED — Sean, 2026-09-20**). The lighter condition for non-victim markets in a blockaded system. Holds a `float penalty` (set via `setParam(Float)` on every refresh) and the base class timer; `apply()` = `getAccessibilityMod().modifyFlat(id, -penalty, name)`, `unapply()` = `unmodifyFlat`. **Holds no reference to the intel** (vanilla's `Blockaded` does; do not copy that). Self-expires `collateralExpiryDays` after the last refresh, so a dead or orphaned blockade can never leave a permanent penalty (Ruling F discipline, the interdiction-condition precedent). Tooltip prints the live scaled figure. CSV row in `market_conditions.csv` at order 158; icon = `graphics/icons/markets/exile_blockade_collateral.png` (Sean's placeholder copy of vanilla `blockade.png`, renamed to the `exile` prefix 2026-09-20; a non-vanilla path, so it overrides nothing; art to be customised later). Name/description = PLACEHOLDER prose until Sean writes it. |
| `data/scripts/exiles/ExileBlockadeIntel.java` | NEW | One-method interface in the shared layer: `StarSystemAPI getBlockadedSystem()`. Implemented by `ExileHegLeagueBlockade` and (three lines) by `AshenBlockade`, which already has the method. Exists so `ExileLiveOps` can ask a blockade where it is **without importing a director class** — dependencies point into the shared utilities, never out. |
| `data/scripts/exiles/ExileLiveOps.java` | MOD | **D8 as RULED:** `getSystemsWithActiveRaids()` additionally scans `getIntel(FleetGroupIntel.class)`, keeps only instances of `ExileBlockadeIntel`, applies the FGI live test (`isEnded() \|\| isEnding() \|\| isAborted() \|\| isFailed()`) and the same `excludeFactionId` rule, and adds the blockaded system. Launch-time only, as before. Effects: refugee convoys now avoid HL blockades (new) and Ashen blockades directly (previously covered only via the companion raid); Ashen relief is unchanged (its own faction is excluded, and the Ashen blockade carries that faction); HL managers see nothing new they could target (Mayasura's only League market is a strain market). BF/Omicron pickers do not call the helper. |
| `data/scripts/exiles/AshenBlockade.java` | MOD | `implements ExileBlockadeIntel` — the method already exists; no behaviour change. Touched only for the interface; flagged because Ashen behaviour is declared complete. |
| `data/scripts/exiles/ExileHegLeagueBlockadeManager.java` | NEW | `EveryFrameScript`, the `ExileHegLeagueStrikeManager` template verbatim (interval gate first, no per-frame allocation, `$exiles_hl_blockade_enabled` memory override). ESCALATION only. On ESCALATION entry (keyed on `director.getCyclesCompleted()` like the retaliation guard, so save/load and `forceState` cannot re-arm it) one launch attempt per eligible side; then attempts on `cadenceDays` while the state lasts, each capped by `maxActivePerSide` (`generalEngagementMaxActivePerSide` under GE — §3.2.4's "simultaneous blockades"). Side pick = weighted by `opWeights.blockade` among sides under cap. Target pick = own copy of the idiom over the whitelist (D1): live-owned eligible victim market present, not exempt (R6a, belt-and-braces — none is), no player market in system, no cooldown, no live HL op (`hasLiveOpIn` extended to blockades), not in `ExileLiveOps.getSystemsWithActiveRaids(null)`, feasibility gate (D3), distance falloff from source (own copy). Launch = `launchBlockade()` port with `playerTargeted=false`, `makeFleetsHostile=false` (both load-bearing, §4.2), `targetFaction=victim`, `specificMarket=focus` (size-weighted eligible victim market — it only decides `patrolText`; the action holds gates). **Measurement line** on launch (source → system, actor/victim, `fleetSizes`, the `getApproximateStrengthForTotalDifficultyPoints` estimate, live defence, gate, payload days, state/tension) and on payload start (`getRelativeFGStrength` result, i.e. which penalty tier actually applies). |
| `data/scripts/exiles/ExileHegLeagueConfig.java` | MOD | `blockade{}` block (§14.9.3); `Side.blockadeWeight` from `opWeights.blockade`; `blockadeWhitelistSystems` + `planetaryBlockadeWhitelistMarkets` lists via `readIdList`; `weariness.perBlockadeSuffered`; timeScale applied to `payloadDays`, `prepDays`, `cadenceDays`, `systemCooldownDays`. Class javadoc's "still absent" list updated. |
| `data/scripts/exiles/ExileHegLeagueGeography.java` | MOD | `isBlockadeWhitelistedSystem(config, system)` (id-or-base-name match, the `isExemptSystem` idiom) and `isBlockadeCollateralMarket(config, m)` (the D2 guard). Pass 6 becomes the second caller of both; until then they live here because the whitelist resolution report needs them too. |
| `ExileHegLeagueInterdictionManager` / `ExileHegLeagueStrikeManager` | MOD | `hasLiveOpIn()` also scans live `ExileHegLeagueBlockade` intel (`getIntel(ExileHegLeagueBlockade.class)`), so a strike or interdiction never launches into a system this director is blockading. `logGeographyResolution` gains the two whitelist lists (`=OK/=UNRESOLVED` per id). Interdiction's `countActive` is untouched — a blockade is not an interdiction slot. |
| `ExileHegLeagueDirector` | MOD | `reportOperationWeariness` gains a blockade case (prefer an `int opType` over a third boolean; the two existing call sites keep identical behaviour). Completion tension stays `perCompletedOp` (spec: "completed ops (small)"). |
| `ExilesModPlugin` | MOD | Register in `onNewGameAfterEconomyLoad`; guarded re-add in `onGameLoad` (a Pass-3 dev campaign picks it up). |
| `director_config.json` | MOD | `blockade{}` block, `opWeights.blockade` per side, `weariness.perBlockadeSuffered`, the two whitelist lists with the transcription table above as comments. No key moved/renamed/removed. |
| `docs/EXILES_HEG_LEAGUE_WAR.md` | MOD | §2 whitelist marked DELIVERED (pointer here); §11 item 1 split (blockade lists done; false-flag targets + display names still open); §12 row 4 BUILT; §14.9 becomes the build record; Q52+ matrix. |

**Not in this pass, by ruling or by spec:** planetary blockade + condition (Pass 6), player
interception at the gates (Pass 8 — the `NPCHassler` pickets are vanilla behaviour, not the
dialogue), ceasefire intel beats (Pass 5), any change to vanilla `Blockaded` (accessibility-only,
no unrest — S3/§8; the collateral condition is a SECOND condition beside it, not a replacement).
`ExileLiveOps` stays one method, but that method now sees blockades (D8 as ruled). **Not
retrofitted:** the collateral condition is Heg-League plumbing only; the Ashen blockade keeps
stamping Tri-Tachyon markets exclusively (New Maxios and Mairaath take nothing from it, as today).
Giving the Ashen blockade collateral would be a separate ruling on a behaviour declared complete.

**Verified 2026-09-20, before the collateral ruling:** nothing in the vanilla API or the engine
core reads the `Blockaded` condition except the condition itself and `BlockadeFGI` (the only other
reference is commented-out code in `KnightsOfLuddTakeoverExpedition`). There is no vanilla seam
that changes trade-fleet spawning during a blockade; trade is affected only through accessibility
plus the pickets physically hassling ships, and non-hostile traders are turned away, not attacked.

**Compute (Sean's standing concern, answered 2026-09-20):** no new per-frame check. The collateral
loop rides vanilla's existing 0.1–0.3-day blockade tick over the 2–4 markets of one system; the
condition's per-frame cost is one float add per holding market, identical to the shipped
interdiction/Sacked/Price-Spikes conditions; the helper scan is launch-time only. At most two
blockades exist at once, and only during ESCALATION.

#### 14.9.3 Config block (proposed; every number a GUESS shipping with the measurement line)

```
"blockade": {
  "enabled": true,
  "launchAtEscalationEntry": true,   # one attempt per side when ESCALATION begins
  "cadenceDays": 30,                 # further attempts inside the window, +/-25%, timeScaled
  "maxActivePerSide": 1,
  "generalEngagementMaxActivePerSide": 2,   # sec 3.2.4 "simultaneous blockades"
  "fleetSizes": [6, 4, 4],           # vanilla difficulty points: one anchor + pickets (Ashen ran [6,3,3])
  "prepDays": 3,
  "payloadDays": 45,                 # x the side's presenceDaysMult (Heg 1.25 -> 56d, League 0.8 -> 36d)
  "accessibilityPenalty": 0.6,       # vanilla default; tiered by relative strength (0 / half / full)
  "abortFraction": 0.5,
  "feasibilityMult": 1.25,           # estimate * mult >= defence (D3); 0 = vanilla behaviour
  "fleetSizesMaxMult": 1.5,          # D3 (Sean): scale fleetSizes up to this before skipping a launch
  "systemCooldownDays": 90,          # end-start, shared key with the other two ops
  # COLLATERAL (RULED 2026-09-20): the victim's markets take vanilla Blockaded at full tiered
  # force; every OTHER market in the system takes exile_hl_blockade_collateral instead.
  "collateralEnabled": true,         # false = vanilla behaviour (victim-faction markets only)
  "collateralAccessibilityPenalty": 0.3,   # vs vanilla's 0.6 on the victim
  "collateralScalesWithTier": true,  # follow the blockade's 0 / half / full tier, so a blockade
                                     # too weak to hurt the League does not hurt Nomios
  "collateralExpiryDays": 5,         # self-expiry after the last refresh -- the orphan backstop
  "hiddenUntilArrival": true,
  "suppressStationAttacks": true,
  "bountyPerFrigate": <interdiction's value>,
},
"weariness": { ..., "perBlockadeSuffered": 8 },
"hegemony": { "opWeights": { ..., "blockade": 3 } },
"persean":  { "opWeights": { ..., "blockade": 1 } },
"blockadeWhitelistSystems": ["isirah", "tyle", "westernesse",      # Hegemony action
                             "naraka", "samarra"],                # League action
"planetaryBlockadeWhitelistMarkets": ["athulf", "cibola", "fikenhild", "laicaille_habitat",
                                      "madeira", "suddene", "yesod",                 # Hegemony action
                                      "ancyra_market", "arcadia_station", "jangala",
                                      "nachiketa", "raesvelg", "sphinx", "yama"],    # League action
```

No `maxShipSize` — the pipeline has no lever and R7 accepts uncapped blockade fleets.
`fleetSizes` is the only size control: entries are vanilla difficulty points consumed by
`FleetCreatorMission.createFleet(style, size, faction, loc)`, so heavier gate-holders means bigger
entries. The Ashen live tests ran `[6,3,3]`; `[6,4,4]` is a guess toward "a real formation" and is
tuned from the payload-start line (which penalty tier the blockade actually earned).

#### 14.9.4 Decisions D1–D8 — ALL RULED (Sean, 2026-09-20; D2 and D8 first, D1 and D3–D7 later the same day)

Every decision below is closed. Provenance: the recommendations were the assistant's; D2's split and
D3's scale-up were Sean's ideas. Recorded so the build can be delegated without reopening any of them.


- **D1 — RULED: ONE MERGED WHITELIST.** Original question: one merged whitelist or per-side lists? The author doc is per side. Code queries
  ownership live, so a single merged list + "victim owns an eligible market there" yields the same
  four targets today, and under a Nex ownership flip it lets the war follow the market (if the League
  ever takes Citadel Arcadia, the Hegemony may blockade Arcadia). **Recommend merged** (R6c spirit:
  the whitelist names acceptable war zones, not owners). Per-side lists cost two keys and a
  side-aware lookup; trivial if preferred.
- **D2 — Collateral: RULED (Sean, 2026-09-20) — THE SPLIT.** Sean's proposal, adopted: the
  opponent's markets take vanilla `Blockaded` at full tiered force; every other market in the
  system takes a separate, lighter Exiles condition (`exile_hl_blockade_collateral`, table above).
  Chosen over "everyone takes full" because it needs NO override of vanilla's apply/unapply and no
  saved stamp list — one extra loop, one condition on the proven base — and over "vanilla only"
  because that contradicts the 2026-08-27 system-wide ruling. Guard for the collateral loop: skip
  hidden/not-in-economy, `ExileProtectedFactions` (refugees), `excludedMarkets` (strain),
  player-owned, and `ExileUnrestGuard.isBattered()`. None exists in the four whitelisted systems
  today (Sean's own gotcha — the enclaves are fragile — is covered if the whitelist ever changes).
  The player-system skip stays at TARGET selection per the sibling convention: a player colony in
  Tyle takes Tyle off the map rather than being blockaded (Pass 8 is where the player meets a
  blockade on purpose). **Open sub-item, assistant's call unless Sean objects:** the ruled default
  is the recommendation above; `collateralEnabled: false` restores vanilla behaviour.
- **D3 — RULED: GATE AT 1.25, AND SCALE UP BEFORE ABORTING.** Sean's amendment: since blockades are uncommon, the manager may raise the fleet sizes to clear the gate rather than skip the launch. Build: multiply the configured `fleetSizes` uniformly until `getApproximateStrengthForTotalDifficultyPoints` clears the gate, capped by new `fleetSizesMaxMult` (1.5); if the cap still fails, skip and log. The launch line prints the multiplier used. Accepted consequence: bigger entries roll more cruisers/capitals (R7 already allows it); Sean notes the farming concern is alleviated by the Refugee Salvage Expeditions, which claim the most valuable wrecks first, so the player has to race them for a capital hulk. Original analysis: Feasibility gate. Vanilla self-limits: below 0.75× the defenders the penalty is zero
  (the blockade sails, sits, and does nothing). A launch-time gate using
  `FleetGroupIntel.getApproximateStrengthForTotalDifficultyPoints(faction, Σ fleetSizes)` against
  `WarSimScript.getEnemyStrength` (station excluded — gate-holders never fight the station) keeps
  toothless blockades from being launched at all. **Recommend shipping the gate at 1.25 with the
  estimate AND the live post-spawn relative-strength tier both logged**, then tuning from the
  lines. `0` disables it (vanilla behaviour) — the knob exists for exactly that comparison.
- **D4 — RULED: STANDALONE, WITH THE SHARED BOUNTY HELPER.** Sean confirmed it is the same bounty every op already pays; only the listener plumbing and the extraction are new. Original question: standalone op with its own bounty listener, or escorted like Ashen?
  Ashen's blockade dies with its companion raid and pays bounty by borrowing the raid's listener.
  Here the spec (§4.2) describes a standalone Escalation op, and blockade fleets are the heavy
  formation, not the "easy prey". Standalone needs its own `FleetEventListener` (neither
  `FleetGroupIntel` nor `BaseIntelPlugin` implements one) with the bounty + side-agnostic weariness
  body from `ExileHegLeagueRaidIntel.reportBattleOccurred`. That is a second caller of ~60
  identical lines, which meets the extraction bar (CLAUDE.md §6): pull the body into a static
  `ExileHegLeagueBattleRewards` used by both, byte-identical behaviour for the raid intel.
  Escorted would instead force a paired interdiction launch (its own gate, its own cap) per blockade
  and stack `exile_hl_interdiction` on `Blockaded` in the same system — not recommended.
- **D5 — RULED AS RECOMMENDED.** New `perBlockadeSuffered` (8) on the victim when the payload
  window completes; `perOpFailed` (8, existing) on the actor when it is broken up. Sits between
  interdiction (4) and a landed strike (12). Tension: `perCompletedOp` like every other completed op.
- **D6 — RULED AS RECOMMENDED.** `hiddenUntilArrival: true` = hidden while preparing/travelling,
  visible from the moment the gates are held (sibling convention, no source concealment). Ashen hid
  its blockade entirely because the raid intel spoke for both; here the blockade IS the listing.
  Vanilla's `BlockadeFGI` intel with the faction crest and the raw penalty is what the player sees.
- **D7 — RULED AS RECOMMENDED.** Reusing the existing per-side multiplier
  (Heg 1.25 / League 0.8) for blockade duration instead of adding `blockadeDaysMult`. Same fiction
  ("longer op durations" is the Hegemony's whole §3.2.3 line). One knob fewer; say if a separate
  one is wanted.
- **D8 — `ExileLiveOps` visibility: RULED (Sean, 2026-09-20) — EXTEND IT.** The original
  recommendation (leave it) was reversed once the collateral design showed the real consumer:
  refugee convoys pick targets through this helper and would otherwise sail into a standalone HL
  blockade and sit ten days being hassled at the gates. Lore: refugees avoid blockaded systems.
  Mechanics in the `ExileLiveOps` row above; the Ashen relief manager keeps its faction exclusion
  and is unaffected; the Ashen blockade was already effectively covered through its companion raid.
  Engine assumption to verify at build: whether `IntelManagerAPI.getIntel(Class)` matches
  interfaces — the plan scans `FleetGroupIntel.class` and tests `instanceof ExileBlockadeIntel`,
  which is safe either way.

#### 14.9.5 Build order and verification

1. Config + `ExileHegLeagueConfig` parsing + geography helpers + resolution line (boots, logs the
   whitelist as `=OK`). 2. `ExileHegLeagueBlockadeCollateral` condition + CSV row + placeholder
   icon path. 3. `ExileBlockadeIntel` interface + `ExileLiveOps` extension + the three-line
   `AshenBlockade` implementation. 4. `ExileHegLeagueBlockade` (collateral loop, abort, naming,
   listener, outcome). 5. Rewards extraction (D4) — the interdiction path runs unchanged through
   `tools/janino-check.ps1`. 6. Manager + registration + `hasLiveOpIn` extension. 7. Doc updates.
   Delegation: Opus subagent drafts 1–4 from this section, Fable reviews (the workflow that carried
   Passes 2–3).

**Janino:** every touched class through `tools/janino-check.ps1`; cast every `List.get()`; no
lambdas/diamonds; `SpreadBlockadeAction` stays a named static class (it is saved state).

**⚠ Blockades cannot be tested below `timeScale` 1** (§9): travel is uncompressed.
`testing.forceState: 4` pins ESCALATION; a forced ESCALATION has no incident parties, which the
blockade manager must tolerate (the weighted side pick does not need them). Expect ~10–20 days of
prepare + travel before the gates are held.

| # | Check | Expect |
|---|---|---|
| Q52 | First boot, geography line | `blockadeWhitelistSystems isirah=OK tyle=OK westernesse=OK arcadia=OK`; the nine planetary ids resolve, or `ancyra_market=UNRESOLVED` under a non-Galatia start (expected, Pass 6 concern) |
| Q53 | `forceState: 4`, `timeScale 1` | `BLOCKADE LAUNCHED` line within a day of ESCALATION for the Hegemony (weight 3) and, if Arcadia passes feasibility, the League; never more than `maxActivePerSide` per side |
| Q54 | Target legality | Every launched target is one of the four whitelisted systems; never Aztlan/Thule; never a system with a live HL op or a `$exiles_hl_op_cooldown` |
| Q55 | Gates held | Fleets sit on EVERY jump point of the system (not 1/N weighted); names "Blockade Fleet" / "Blockade Picket"; pickets hassle traffic |
| Q56 | Condition scope (the split) | Vanilla `Blockaded` ONLY on the victim faction's markets; `exile_hl_blockade_collateral` on every other non-guarded market incl. Nomios/Agreus (Arcadia), Kapteyn/Ailmar as applicable; NOT on any refugee/strain/player/Battered market; the collateral tooltip shows the tier-scaled figure (0.3 × tier) and the vanilla one 0.6 × tier; both removed on ending, none left on any market after the op |
| Q56a | Collateral orphan backstop | Console-remove the blockade intel mid-payload (or `abort()` it): the collateral condition disappears within `collateralExpiryDays` on its own timer, and `Blockaded` is gone at once |
| Q56b | Refugee convoy avoidance | With a blockade holding a whitelisted system, the convoy manager's launch log never picks a market in that system; Ashen relief still launches into Magec/Mayasura during a rehearsal (its own blockade is excluded) |
| Q57 | Penalty tier | Payload-start line prints `relative strength -1/0/1`; compare with the launch estimate to tune `fleetSizes` / `feasibilityMult` |
| Q58 | Outcome | Completed window → `weariness … +8.0: blockade completed against <victim>` and `perCompletedOp` tension; broken up (`abortFraction`) → `+8.0: … did not hold` on the actor; system cooldown set at END |
| Q59 | Bounty | Player kills a picket → victim faction pays per hull size, rep via `SYSTEM_BOUNTY_REWARD`, no payout when the player fought on the blockader's side; interdiction bounty behaviour unchanged after the extraction |
| Q60 | Station safety | Blockade fleets never engage the victim's station or Kapteyn's battlestation on their own initiative (`doNotAttack` refreshed on the tick) |
| Q61 | Save/load mid-blockade | Reload with gates held: condition persists, `SpreadBlockadeAction` scripts resume, unapply still fires on ending; a Pass-3 save gains the manager on load |
| Q62 | General Engagement | Under GE both sides may hold 2; blockades launched late in the window outlive the state and end on their own `payloadDays` (expected — same as strikes) |
| Q63 | Nex run | Ownership flip of a whitelisted market moves it out of / into the pool with no code change; no Nex invasion of a blockaded market during the op (observe only — no shield is added, per R11) |

#### 14.9.6 Pass 4 build record [BUILT 2026-09-20; Janino-clean; UNTESTED IN GAME]

Built from §14.9 exactly as ratified; D1–D8 were not reopened. **Nothing here has run in game** —
the Q52–Q63 matrix in §14.9.5 is entirely outstanding.

**Files**

| File | New/Mod | What landed |
|---|---|---|
| `data/scripts/exiles/ExileBlockadeIntel.java` | NEW | One-method interface (`getBlockadedSystem()`) so the shared layer can ask a blockade where it is without importing a director class. |
| `data/scripts/exiles/ExileHegLeagueBlockade.java` | NEW | `BlockadeFGI` subclass: `SpreadBlockadeAction` (every gate), anchor/picket naming + `NPCHassler`, collateral loop on `periodicUpdate()`, station-targeting suppression, `isBrokenUp()` abort, reveal + payload-start measurement line, outcome report + end-start cooldown, `FleetEventListener` bounty/weariness. |
| `data/scripts/exiles/ExileHegLeagueBlockadeManager.java` | NEW | ESCALATION-only manager: cycle-keyed entry salvo, jittered cadence, per-side caps (GE-aware), whitelist target pick with the D3 scale-up gate, launch plumbing + measurement line. |
| `data/scripts/exiles/ExileHegLeagueBattleRewards.java` | NEW | D4 extraction: the bounty body and the side-agnostic weariness body, static, state-free. |
| `data/scripts/marketconditions/ExileHegLeagueBlockadeCollateral.java` | NEW | `exile_hl_blockade_collateral`; `ExileTimedCondition` subclass holding a `Float` penalty and no intel reference. |
| `data/scripts/exiles/AshenBlockade.java` | MOD | `implements ExileBlockadeIntel` + a javadoc line. No behaviour change. |
| `data/scripts/exiles/ExileLiveOps.java` | MOD | D8: also scans `FleetGroupIntel`, keeps `instanceof ExileBlockadeIntel`, FGI live test, same `excludeFactionId` rule. |
| `data/scripts/exiles/ExileHegLeagueRaidIntel.java` | MOD | Bounty and weariness bodies replaced by calls into the helper; `pendingBounties` / `wearinessBattleSeed` stay its own fields. |
| `data/scripts/exiles/ExileHegLeagueConfig.java` | MOD | `blockade{}` fields + parsing, `Side.blockadeWeight`, both whitelists, `weariness.perBlockadeSuffered`, timeScale on the four durations, javadoc scope list. |
| `data/scripts/exiles/ExileHegLeagueGeography.java` | MOD | `isBlockadeWhitelistedSystem()` + `isBlockadeCollateralMarket()`. |
| `data/scripts/exiles/ExileHegLeagueDirector.java` | MOD | `OP_TYPE_*` constants + the `int opType` overload of `reportOperationWeariness`; the boolean form now delegates, so both old call sites are unchanged. |
| `ExileHegLeagueInterdictionManager` / `ExileHegLeagueStrikeManager` | MOD | `hasLiveOpIn()` also scans live blockades; the interdiction manager's `logGeographyResolution` reports both whitelists. |
| `data/scripts/exiles/ExilesModPlugin.java` | MOD | Manager registered in `onNewGameAfterEconomyLoad`, guarded re-add in `onGameLoad`. |
| `data/campaign/market_conditions.csv` | MOD | One row, order 158, icon `exile_blockade_collateral.png`, PLACEHOLDER name/desc. |
| `data/config/exiles/director_config.json` | MOD | `blockade{}`, `opWeights.blockade`, `weariness.perBlockadeSuffered`, both whitelists. No key moved, renamed or removed. |

**Janino:** `tools/janino-check.ps1` over all fifteen touched/new classes — **exit 0**. The wrapper
was negative-tested in the same session (a deliberately broken file returned exit 1), so the pass
is a real compile, not an empty run. `director_config.json` was verified two ways: a
comment/string-aware brace and bracket count (net 0 / 0) and a full parse after stripping comments
and trailing commas.

**Config keys added:** `blockade.{enabled, launchAtEscalationEntry, cadenceDays, maxActivePerSide,
generalEngagementMaxActivePerSide, fleetSizes, prepDays, payloadDays, accessibilityPenalty,
abortFraction, feasibilityMult, fleetSizesMaxMult, systemCooldownDays, collateralEnabled,
collateralAccessibilityPenalty, collateralScalesWithTier, collateralExpiryDays, hiddenUntilArrival,
suppressStationAttacks, bountyPerFrigate}`; `hegemony.opWeights.blockade` (3) /
`persean.opWeights.blockade` (1); `weariness.perBlockadeSuffered` (8); `blockadeWhitelistSystems`;
`planetaryBlockadeWhitelistMarkets`.

**Deviations and conservative readings**

1. **Collateral at tier −1 REMOVES the condition rather than applying it at 0%.** §14.9's literal
   text computes `penalty = base × 0` and then applies. Applying a condition whose tooltip reads
   "-0% accessibility" is visible clutter, and vanilla's own `applyBlockadeCondition()` *unapplies*
   `Blockaded` at that tier — so this matches vanilla parity. Effect at every other tier is
   identical. Reverse by dropping the `penalty <= 0f` clause in `updateCollateral()`.
2. **Outcome report and the system cooldown both sit in `notifyEnding()`**, not split across
   `notifyEnding()` / `notifyEnded()`. Vanilla's `BlockadeFGI` already overrides `notifyEnding()`
   and it is reached on every exit path including abort, so one guarded block there is the
   reliable seam. The cooldown is still end-start, as ruled.
3. **`collateralExpiryDays` is NOT timeScaled** (§14.9 lists only the four other durations). It is
   measured against vanilla's own untimescaled 0.1–0.3 day blockade tick, not against the war's
   fiction. Noted in code.
4. **The bounty payout notice is a plain campaign message**, not the raid intel's deferred
   `BountyResult` bullet renderer. The blockade's intel listing is vanilla's `BlockadeFGI` one,
   whose update rendering is not the raid intel's, and §14.9 specified the bounty *semantics*
   rather than its presentation. Credits and the `SYSTEM_BOUNTY_REWARD` reputation are identical.
5. **`suppressStationTargeting()` covers EVERY station in the system**, where `AshenBlockade`
   scoped it to the target faction's. A blockade of Isirah should not start a fight with Kapteyn
   Starworks either. Flagged because it is a deliberate widening, not a port.
6. **Code defaults mirror the JSON whitelists** rather than shipping empty. An empty whitelist
   means "no blockade can ever launch", so a broken or missing config would have silently disabled
   the whole pass instead of degrading to the ratified map.
7. **`Side.blockadeWeight` gained a 7th constructor argument** rather than a setter; both existing
   construction sites were updated. `reportOperationWeariness`'s boolean form was kept as a
   delegate so the Pass-2/3 call sites are untouched.

**Known-unverified / things to watch on the first live test**

- Whether `IntelManagerAPI.getIntel(ExileHegLeagueBlockade.class)` indexes an FGI-derived intel the
  way it indexes `RaidIntel` subclasses. `countActive` and both `hasLiveOpIn` extensions depend on
  it. (`ExileLiveOps` deliberately does not: it scans `FleetGroupIntel.class` and tests the
  interface, which is safe either way.)
- The feasibility estimate is vanilla's flat 50-points-per-difficulty-point conversion against a
  live `getEnemyStrength`. Both measurement lines ship; **tune `fleetSizes` and `feasibilityMult`
  from them**, and expect Isirah to read high (its pirate patrols and the Kapteyn station count).
- `getRelativeFGStrength` needs a live route; it is null-guarded here and treated as tier −1 before
  the route exists, which is why the collateral condition cannot land before the gates are held.

#### 14.10 Pass 5 — Ceasefire + General Engagement intel beats [BUILT 2026-09-20; Janino-clean; UNTESTED IN GAME]

Pass 5 in §12 bundles four things. **Three of them were already built** by Passes 1–3.5 and were
verified present rather than written again (list below). What was actually missing was the
player-facing half of §3.4 — "ceasefire talks convened; ceasefire announced" — and §3.2.4 A's
"a named operation in the intel". That is what this pass builds, and nothing else. **Nothing here
has run in game**; the Q64–Q74 matrix below is entirely outstanding.

**What was ALREADY built (verified present, not re-implemented)**

| §12 row-5 item | Where it lives | Built in |
|---|---|---|
| POLITICAL_INTERVENTION outcome roll | `ExileHegLeagueDirector.resolveIntervention()` — weights from `interventionOutcomeWeights`, the weariness term, the GE stand-down multiplier, the three cuts | Pass 1; amended 2026-09-13 (§14.5) and 2026-09-19 (§14.6) |
| DE_ESCALATION | `STATE_DE_ESCALATION`, `rollStateDays()` → `cycle.deescalationDays`, fast decay in `decayMeters()` via `tension.deescalationDecayMult` | Pass 1; mult 4 → 8 in §14.6 |
| Per-faction weights / lags | `ExileHegLeagueConfig.Side` — `retaliationLagDays` (read in `rollStateDays`), `opWeights.interdiction` / `.surgical` / `.blockade`, `maxConcurrentOps`, `presenceDaysMult` | Passes 1–4 |
| General Engagement (R1 = A) | `beginState(STATE_ESCALATION)` — `generalEngagementThreshold` or `generalEngagementHotCycles`, window override to `cycle.generalEngagementDays`, `outcomeGeneralEngagementStandDownMult` in the roll, `blockade.generalEngagementMaxActivePerSide` | Pass 1; blockade half in Pass 4 |
| The third telegraph (incident: rumour → confirmation → contained) | `ExileHegLeagueIncidentIntel` + `ExileHegLeagueIncident` prose | Pass 3 / §14.5 |

**Files**

| File | New/Mod | Lines | What landed |
|---|---|---|---|
| `data/scripts/exiles/ExileHegLeagueCeasefireIntel.java` | NEW | 314 | `BaseIntelPlugin`; two beats (convened → announced), three `REASON_*` constants, idempotent `markOutcome(int)`, dual timer (normal from the outcome beat + hard backstop from posting), crest icon, neutral UI colours, `INTEL_MILITARY` + both faction tags. All prose PLACEHOLDER. |
| `data/scripts/exiles/ExileHegLeagueEngagementIntel.java` | NEW | 258 | Same lifecycle; title `General Engagement #N`, config-supplied PLACEHOLDER operation name, idempotent `markEngagementEnded()`, `intel/hostilities` (vanilla `war.png`) icon. |
| `data/scripts/exiles/ExileHegLeagueDirector.java` | MOD | 1163 | Saved `lastInterventionReason`, `lastCeasefireIntelCycle`, `lastEngagementIntelCycle`; `postCeasefireIntel()` / `markCeasefireOutcome()` / `postEngagementIntel()` / `endEngagementIntel()` / `findLiveCeasefireIntel()` / `findLiveEngagementIntel()` / `getInterventionReasonName()`; three seam calls; the reason set at both branch points. No existing log line or behaviour changed. |
| `data/scripts/exiles/ExileHegLeagueConfig.java` | MOD | 1030 | `intel{}` fields + `optXxx` parsing + timeScale on the two day counts + the javadoc scope list. |
| `data/scripts/exiles/ExilesModPlugin.java` | MOD | 1296 | Two idempotent `onGameLoad` heal sweeps, copied from the incident-telegraph block. Defensive only. |
| `data/config/exiles/director_config.json` | MOD | 2030 | The `intel{}` block inside `"exilehegleague"`. **No key moved, renamed or removed.** |

**The three seams, exactly**

1. **Enter POLITICAL_INTERVENTION** → `beginState()`'s new `else if` branch calls
   `postCeasefireIntel()`. This is the single transition point: both routes into state 5 (the
   pre-emptive RETALIATION exit and the ESCALATION exit) go through `beginState`.
2. **After `resolveIntervention()`** → `markCeasefireOutcome()`, in `advanceState()`, on the very
   next line, so `lastOutcome` is fresh and the beat cannot be separated from the roll.
3. **Enter ESCALATION with `generalEngagement == true`** → `postEngagementIntel()`, immediately
   after the flag is set and the window is overridden. **Leave ESCALATION** →
   `endEngagementIntel()` in `advanceState()`'s ESCALATION branch, which also sets
   `lastInterventionReason` before `beginState(STATE_POLITICAL_INTERVENTION)`.

`lastInterventionReason` is set at exactly two places: the pre-emptive branch of the RETALIATION
exit (`REASON_PREEMPTIVE`) and the ESCALATION exit (`REASON_GENERAL_ENGAGEMENT` when the cycle ran
as one, else `REASON_ESCALATION`). The CONTAINED exit never reaches state 5 and deliberately does
not set it.

**The `forceState` guard — the risk the build brief asked about, confirmed real.**
`testing.forceState` PINS the machine: `advance()` re-enters the pinned state whenever the machine
has moved off it. With `forceState: 5` the director runs state 5 → `resolveIntervention()` →
state 6, and the next tick pins it back to 5 — calling `beginState(STATE_POLITICAL_INTERVENTION)`
again, in the same cycle, indefinitely. `forceState: 4` behaves the same and additionally
re-increments `escalationsReached` each time. Both posts are therefore guarded on
`cyclesCompleted` with the `lastRetaliationCycle` idiom (`Integer`, not `int`, so a converted save
cannot read cycle 0 as "already posted"). **Consequence under a pinned state 4:** the intel is
posted once and keeps its original number while `escalationsReached` in the log keeps climbing.
That is a testing-aid artefact only.

**Config keys added** (all inside the existing `"exilehegleague"` block, new `"intel"` object):
`intel.{ceasefireEnabled (true), ceasefireIntelDays (60, timeScaled), generalEngagementEnabled
(true), generalEngagementIntelDays (30, timeScaled), generalEngagementOperationName ("Operation
PLACEHOLDER")}`. Either `*Enabled` set false posts no intel and changes nothing else — the outcome
roll, the cuts, the durations and the state graph are identical with the beats off.

**Janino:** `tools/janino-check.ps1` over all five touched/new classes (both new intels, the
director, the config, `ExilesModPlugin`) — **exit 0**. Negative-tested in the same session: a
deliberately broken statement appended to the ceasefire intel returned exit 1 with a parse error,
so the pass is a real compile and not an empty run; the file was then restored and re-checked.
`director_config.json` verified two ways — a comment/string-aware balance count (**braces 96/96,
net 0; brackets 27/27, net 0**) and a full parse after stripping comments and trailing commas,
with the new `intel` block read back key by key.

**Deviations and conservative readings — all flagged, none invented**

1. **Two timers per intel, not one.** The spec for this pass asked for expiry `ceasefireIntelDays`
   after the outcome beat *plus* a hard backstop of `stateDays + ceasefireIntelDays` from posting.
   Built as two independent counters; whichever hits zero first calls `endAfterDelay()`. The
   incident telegraph has only one because its second beat is on a fixed timer it owns; these beats
   are driven by the director, so the backstop is what makes them safe if the director stops.
2. **The ceasefire icon is the faction crest, not a peace icon.** The vanilla intel icon folder was
   inspected: `peace.png` and `faction_peace.png` exist on disk but core `settings.json` binds
   **no** key to either (a `peace` grep over the whole file returns nothing), so there is no
   registered sprite name to ask for and inventing one would be adding art plumbing. The crest is
   the shipped sibling pattern (`ExileHegLeagueIncidentIntel`, `AshenRaidIntel`, …). The General
   Engagement intel *does* use a vanilla sprite, because `"hostilities"` **is** registered to
   `graphics/icons/intel/war.png` — vanilla's own faction-war icon, a genuinely better fit.
3. **`getFactionForUIColors()` returns neutral (super) on both**, where the incident telegraph
   returns the victim faction. An incident has a victim; a ceasefire table and a general engagement
   have two equal parties, and colouring either as one side would read as that side's
   announcement. Flagged because it is a deliberate divergence from the copied precedent.
4. **The engagement intel is named from config, not from a side.** §3.2.4 A says "a named
   operation"; which side is "the surge leader" is not knowable, because a General Engagement is
   both sides committing at once. One PLACEHOLDER string in config, with the cycle's number in the
   title.
5. **The closing engagement beat fires at the ESCALATION exit, not at `closeCycle()`.** §3.2.4 A
   rules the GE always ends at the table, so the exit *is* the end of the fighting; `closeCycle()`
   is a whole ceasefire plus de-escalation later and would have the intel claiming the fleets broke
   off two states late.
6. **The `onGameLoad` sweeps are dead code by construction** and are shipped anyway, because the
   2026-08-28 incident-telegraph bug is exactly what they protect against. Both intels add
   themselves as scripts in their constructors.
7. **One stale javadoc line was corrected**, not merely added to: `resolveIntervention()`'s Pass-1
   comment said "the summit intel beat … [is] Pass 5", which violates the R15 / §3.2.5-3 vocabulary
   ruling. No behaviour change.
8. **No campaign message and no forced-silent update.** Both beats call
   `sendUpdateIfPlayerHasIntel(..., false)` and `addIntel(intel, false)` exactly like the incident
   telegraph, so they raise the ordinary intel notification and nothing louder. §3.4 asks for
   intel, not for a message box.

**Nothing was built that needs a new author ruling.** Every player-facing string is PLACEHOLDER
under §11 item 2 / R12 and is marked as such in code and in config.

**Test matrix — Q64–Q74 (continues §14.9.5's numbering; all OUTSTANDING)**

| Q | Area | What to verify |
|---|---|---|
| Q64 | Ceasefire beat 1, pre-emptive | A warning-band cycle that passes `preemptiveCeasefireChance` posts "Ceasefire Talks" with the pre-emptive prose; log reads `ceasefire intel: convened (reason pre-emptive, from the warning band) \| cycle n` |
| Q65 | Ceasefire beat 1, after ESCALATION | An ESCALATION cycle posts with the post-escalation prose and the matching log reason |
| Q66 | Ceasefire beat 1, after GE | A General Engagement cycle posts with the GE prose (reason `after a General Engagement`) |
| Q67 | Ceasefire beat 2 | The update lands the same tick the outcome is rolled; the title becomes Temporary / Partial / Collapsed per `lastOutcome`; both factions named; one `ceasefire intel: outcome …` log line |
| Q68 | Ceasefire expiry + switch | Intel ends `ceasefireIntelDays` after the outcome beat and removes itself from `getScripts()`; with `ceasefireEnabled: false` nothing is posted and the cycle, cuts and log are otherwise identical |
| Q69 | Ceasefire backstop | Set `enabled: false` (or `$exiles_hl_war_enabled` false) while a talks intel is live: it still expires by `interventionDays + ceasefireIntelDays` from posting rather than sitting on the board |
| Q70 | Engagement beat 1 | A declared GE posts `General Engagement #N` with the config operation name in the bullets and the description; N matches the `ESCALATION #N` log line |
| Q71 | Engagement beat 2 | At the ESCALATION exit the intel updates to "Broken Off" and the ceasefire intel posts in the same tick — two beats, correct order, no double-post |
| Q72 | Engagement expiry + switch | Ends `generalEngagementIntelDays` after the closing beat; `generalEngagementEnabled: false` posts nothing and leaves the GE window, stand-down multiplier and blockade caps unchanged |
| Q73 | Save/load mid-beat | Save with talks convened but not announced, reload: the intel still ticks (it is in `getScripts()`), still receives the outcome beat, still expires. Repeat with a live GE intel |
| Q74 | `forceState` pinning | `testing.forceState: 5` posts exactly ONE ceasefire intel per `cyclesCompleted`, not one per dwell; `forceState: 4` posts exactly one engagement intel per cycle (its number freezes while `escalationsReached` climbs — expected) |

### 14.11 Pass 6 — Planetary blockade + recovery convoys: PLAN [DRAFTED 2026-09-20 by an Opus planning agent, Fable-reviewed; D80.1–D80.10 are ASSISTANT RECOMMENDATIONS adopted as build-time assumptions under Sean's autonomous-evening mandate — each awaits his ruling and is reversible by config or a small edit + BUILD RECORD (§14.11.6) [BUILT 2026-09-20; Janino-clean; UNTESTED IN GAME — Q80–Q97 open]

Pass 6 builds the RETALIATION state's *other* punitive option (§3.2.2 item 3, §4.4) and the
strike-triggered relief loop that R16 moved out of DE_ESCALATION (§3.2.5 ruling 5, §4.6). The two
halves ship together because `surgicalStrike.extendedDisruptMult` is only allowed to leave 1.0 once
convoys exist — the config comment shipped in Pass 3 says so in as many words, and this section
honours it: **the mult moves 1.0 → 2.0 in the same commit that lands the convoys, or not at all.**

Everything below marked **[assistant]** is an assistant-originated addition or recommendation, per
the standing disclosure rule; everything else is transcription of an existing ruling.

---

#### 14.11.1 What the existing code already gives Pass 6

Read before designing anything; four of the five components exist and were verified in source
2026-09-20.

1. **The squeeze is the FOURTH port of the presence-raid pattern.** `ExileHegLeagueInterdictionStage`
   is already parameterised on exactly the axes the squeeze needs: `canRaid()` always false (no
   ground action), a `getLoiterToken()` that already supports **loitering on a focus market's
   primary entity** (`loiterOnFocusMarket`, config-switched, with a fall-back to the system centre
   when the market is gone), `suppressStationTargeting()` refreshed per tick over *every* station in
   the system, a day-N condition apply, a lift on withdrawal-or-abort, and
   `abortIfNeededBasedOnFP(true)`. The squeeze is that stage with (a) a market-scoped condition
   instead of a faction-scoped one and (b) `loiterOnFocusMarket` forced true. **Measured: this is a
   subclass or a constructor flag, not a new stage.** See D80.2 for which.
2. **`ExileHegLeagueRaidIntel` already carries** the RAIDER_TAG, the per-kill bounty paid by the
   raided side, the joined-the-raiders exploit guard, the per-battle weariness seed, the
   hidden-until-arrival reveal and the end-of-op `$exiles_hl_op_cooldown` write. Pass 3 proved the
   subclass route (`ExileHegLeagueStrikeIntel` is 98 lines, three overrides). Pass 6 does the same.
3. **The convoy discipline exists twice, and neither instance is route-managed.**
   `AshenReliefManager` (585 lines) and `ExileRefugeeConvoyManager` (727 lines) both use the same
   shape: an `EveryFrameScript` with an `IntervalUtil` gate, a **bounded `activeOps` list of named
   static saved-state objects** (`ReliefOp` / `ConvoyOp`, XStream-safe), a TRAVEL → ORBIT →
   DEPARTING phase machine with `FleetAssignment.GO_TO_LOCATION` → `ORBIT_PASSIVE` →
   `GO_TO_LOCATION_AND_DESPAWN`, per-phase timeouts so nothing can be orphaned, direct
   `FleetFactoryV3.createFleet` spawning, launch-time-only eligibility, and a dead-fleet branch that
   drops the op without writing anything. `AshenReliefManager` applies its market condition at the
   *start* of the orbit and banks its reward at *departure* — the exact two seams §4.6 needs.
   **`RouteManager.RouteFleetSpawner` exists** (`impl/campaign/fleets/RouteManager.java:235`) but no
   Exiles convoy uses it; see D80.5a.
4. **`ExilePriceSpikes` holds the player-buy mechanism** — and the spec's name for it is wrong.
   `MarketAPI` has **no** `getPlayerSupplyPriceMod()`; the method is on
   **`CommodityOnMarketAPI`** (`campaign/econ/CommodityOnMarketAPI.java:151`), so the mechanism is a
   loop over `market.getCommoditiesCopy()` calling
   `com.getPlayerSupplyPriceMod().modifyMult(id, mult, name)`, with a matching `unmodify(id)` loop
   in `unapply()`. §4.4/§8 both name the market-level method; corrected here (D80.10 item 1).
5. **The disruption maths is clean.** `BaseIndustry.setDisrupted(float days, boolean useMax)`
   (`impl/campaign/econ/impl/BaseIndustry.java:1789`) writes `$core_disrupted_<PluginClassSimpleName>`
   into **market** memory with `days` as the expiry; `useMax=false` therefore **sets absolutely and
   can lower a timer**, and `dur <= 0` unsets the key outright (the industry comes back online).
   `getDisruptedDays()` reads that expiry and clamps at 0. So the drip is literally
   `ind.setDisrupted(Math.max(floor, ind.getDisruptedDays() - perDay))` with no reflection and no
   vanilla override. Two coarseness notes to carry into the build: the key is keyed on the industry
   **plugin class**, so two industries sharing a plugin on one market share one timer; and
   `notifyDisrupted()` only fires on the not-disrupted → disrupted transition, so lowering fires
   nothing (which is what we want).

#### 14.11.2 Shape — what gets built

Fourth-sibling discipline unchanged (S1/S4, CLAUDE.md §6): two new managers that READ the director
and own no config, thin subclasses of proven classes, every tunable in the one JSON, **no director
knows another exists**, and no extraction is attempted where the callers are not already identical.

| File | New/Mod | Contents |
|---|---|---|
| `data/scripts/exiles/ExileHegLeagueSqueezeStage.java` | NEW | `extends ExileHegLeagueInterdictionStage`. Overrides exactly three things: `getLoiterToken()` returns the target market's primary entity unconditionally (falling back to the system centre on the same guards as the parent), `applyConditions()` / `removeConditions()` stamp `exile_hl_planetary_blockade` on **the one anchor market only** (not the faction's markets in the system), and `showStageInfo`/`getRaid*Text` carry the squeeze's nouns. `canRaid()` (false), `updateRoutes()`, `updateStatus()`, `suppressStationTargeting()`, `isPresenceCompleted()` and the FP abort are **inherited untouched**. Saved fields: `anchorMarketId`, `conditionApplied`, `conditionRemoved` (the last two already exist on the parent). |
| `data/scripts/exiles/ExileHegLeagueSqueezeIntel.java` | NEW | `extends ExileHegLeagueRaidIntel`, the `ExileHegLeagueStrikeIntel` template: `isInterdictionOp()` → false (a squeeze must not consume an interdiction slot — the Pass-3 precedent and the reason that method exists), `getOpEndedText()`, and `reportOutcome()` reporting `OP_TYPE_PLANETARY` weariness + `reportOperationCompleted` on a completed presence window. Everything else inherited. |
| `data/scripts/marketconditions/ExileHegLeaguePlanetaryBlockade.java` | NEW | `extends ExileTimedCondition`, id `exile_hl_planetary_blockade` (§8). `apply()` = `getAccessibilityMod().modifyFlat(id, -ACCESSIBILITY_PENALTY, name)` **plus** the per-commodity `getPlayerSupplyPriceMod().modifyMult(...)` loop; `unapply()` unwinds both. Static tunables pushed from config by the manager (`applyConditionTunables()`, the Pass-2 idiom). Records the acting faction in market memory under `$exiles_hl_squeeze_actor` with the `ExileHegLeagueInterdiction.ACTOR_KEY` pattern, so the tooltip can name either belligerent and degrades to neutral wording when the key is gone. `getDurationDays()` = hard self-expiry backstop (Ruling F). **Holds no reference to the intel.** CSV row at order **159**; icon = a new `graphics/icons/markets/exile_planetary_blockade.png` (Sean's placeholder; a non-vanilla path, overrides nothing — the §7 exact-path rule). Name/description PLACEHOLDER until Sean writes them (§11 item 2). |
| `data/scripts/exiles/ExileHegLeagueRecoveryManager.java` | NEW | `EveryFrameScript`, the `AshenReliefManager` shape verbatim. Owns the **relief ledger** (a bounded `List` of named static `ReliefRecord`) and the **convoy list** (`List` of named static `ReliefOp`). Static `record(...)` entry point called by the strike stage; `get()` static resolver so no caller holds a reference (the `ExileHegLeagueDirector.get()` idiom). Phase machine TRAVEL → ORBIT (the drip) → DEPARTING. Per-side asymmetry read from config. Measurement lines on record, launch, each arrival, and completion. |
| `data/scripts/exiles/ExileHegLeagueStrikeStage.java` | MOD | Two lines at the end of `performRaid()`, after `struckIndustries` is set: compute `reliefDays` and call `ExileHegLeagueRecoveryManager.record(market, picks, reliefDays, struckFactionId)`. Guarded static — a Pass-3/4 save with no manager is a silent no-op. **No other change**; the strike's own behaviour, ordering and logging are untouched. |
| `data/scripts/exiles/ExileHegLeagueStrikeManager.java` | MOD | `tryLaunchRetaliation()` gains the **op-type roll** (D80.1) and delegates to the squeeze manager when it picks `planetary`; `hasLiveOpIn()` also scans `ExileHegLeagueSqueezeIntel`. `rollDisruptDays()` unchanged (it already multiplies by `extendedDisruptMult`). |
| `data/scripts/exiles/ExileHegLeagueSqueezeManager.java` | NEW | The launcher. **Not an autonomous cadence manager** — it exposes `launchSqueeze(director, config, actorId, targetId, reason)` with the `launchStrike()` signature and is called by the strike manager's retaliation path (and, if D80.1c is ruled in, by the ESCALATION cadence). Target pick = `planetaryBlockadeWhitelistMarkets` ∩ live-owned-by-target ∩ `isEligibleVictimMarket` ∩ not exempt ∩ no player market in system ∩ no `$exiles_hl_op_cooldown` ∩ not in `ExileLiveOps.getSystemsWithActiveRaids(null)` ∩ no live HL op in system ∩ feasibility gate, then distance-falloff weighted from the source. Launch = the `launchOp()` plumbing (assemble → travel → squeeze → return), `addIntel(raid, true)`, measurement line. |
| `data/scripts/exiles/ExileHegLeagueInterdictionManager.java` | MOD | `hasLiveOpIn()` also scans squeezes; `logGeographyResolution()` already prints `planetaryBlockadeWhitelistMarkets` (Pass 4 added it) — only the `ancyra_market=UNRESOLVED` note in §14.9.1 item 3 needs honouring: keep the line, it is now load-bearing. |
| `data/scripts/exiles/ExileHegLeagueBlockadeManager.java` | MOD | `hasLiveOpIn()`-equivalent scan gains squeezes, so a system blockade and a planetary squeeze can never coexist in one system. |
| `data/scripts/exiles/ExileHegLeagueDirector.java` | MOD | `OP_TYPE_PLANETARY = 3` + a fourth case in `reportOperationWeariness(String,String,boolean,int)`'s two branches ("squeeze completed against" / "squeeze did not hold"). The boolean delegate and both legacy call sites are untouched, exactly as Pass 4 left them. |
| `data/scripts/exiles/ExileHegLeagueGeography.java` | MOD | `isPlanetaryBlockadeWhitelisted(config, market)` (id match against `planetaryBlockadeWhitelistMarkets`, the `isBlockadeWhitelistedSystem` idiom) and `pickSourceNear(actorFactionId, target, random)` — spaceport-market source weighted `1/(1+distLY*distanceFalloff)` instead of size-weighted, for the convoy's "nearest same-faction spaceport". Two callers each by end of pass. |
| `data/scripts/exiles/ExileHegLeagueConfig.java` | MOD | `planetaryBlockade{}` and `recoveryConvoys{}` blocks; `Side.planetaryWeight` from `opWeights.planetary` (8th constructor arg, both construction sites updated — the Pass-4 `blockadeWeight` precedent); `Side.recoveryDaysMult`; `timeScale` on `squeezePrepDays`, `squeezePresenceDays`, `squeezeSystemCooldownDays`, `recoveryLagDays`, `recoveryUnloadDays`, `recoveryTravelTimeoutDays`. Javadoc's "still absent" list emptied. |
| `data/scripts/exiles/ExilesModPlugin.java` | MOD | Register `ExileHegLeagueRecoveryManager` in `onNewGameAfterEconomyLoad`; guarded re-add in `onGameLoad` (a Pass-4 dev campaign picks it up). The squeeze manager is **not** registered — it is stateless plumbing called by the strike manager, not a script. |
| `data/campaign/market_conditions.csv` | MOD | One row, order **159**, PLACEHOLDER name/desc, `decivRemove` TRUE (matching rows 21–23). |
| `data/config/exiles/director_config.json` | MOD | `planetaryBlockade{}`, `recoveryConvoys{}`, `opWeights.planetary` per side, `hegemony.recoveryDaysMult` / `persean.recoveryDaysMult`, `weariness.perSqueezeSuffered`, and the `surgicalStrike.extendedDisruptMult` 1.0 → 2.0 flip **with its warning comment rewritten**. No key moved, renamed or removed. |
| `docs/EXILES_HEG_LEAGUE_WAR.md` | MOD | §4.4 shape (b) confirmed + the `getPlayerSupplyPriceMod` correction; §4.6 unrest-decay item closed; §8 row for `exile_hl_planetary_blockade` marked BUILT; §12 row 6 BUILT; §11 item 3 resolved either way; this section becomes the build record; Q80+ matrix. |

**Not in this pass, by ruling or by spec:** player interception of a squeeze or of a convoy
(Pass 8 — the memory-key hook name is reserved here and nothing else); the BF proxy that hunts
convoys (Pass 7, §6.2 — same, name only); ceasefire intel beats (Pass 5, being built concurrently);
any change to the system blockade, the interdiction condition, or vanilla `Blockaded`.

**Not extracted, deliberately** (CLAUDE.md §6, measured not assumed): the squeeze manager's target
loop shares the *comparison* `defense <= fp * mult` with three existing loops and nothing else — the
S4/§14.6 rejection stands and a fourth private copy ships. The convoy phase machine is a **second**
near-identical caller of the Ashen relief shape, which is on the extraction bar; the recommendation
is still **copy, not extract**, because the two differ in what they do at orbit (condition-apply vs
daily drip), in their launch trigger (cadence vs event ledger) and in their end-of-op bookkeeping —
a shared base would have to parameterise all three, which is the Pass-5b arithmetic again. **Revisit
at a third convoy, not before** [assistant].

#### 14.11.3 Config block (proposed; every number a GUESS shipping with its measurement line)

```
"planetaryBlockade": {
  "enabled": true,
  # Sized by tension like the strike it replaces (sec 3.2.2 state 3), same formula.
  "spawnFpMin": 180,                 # GUESS -- strike runs 250-400; a squeeze is lighter
  "spawnFpMax": 300,                 # GUESS
  "tensionFpScale": 0.5,             # mirror of strike.tensionFpScale
  "maxShipSize": 2,                  # S6 destroyer cap; this is a skirmish fleet, not a blockade
  "maxFleetFp": 60,                  # GUESS -- between interdiction's 45 and strike's 90
  "prepDays": 3,
  "presenceDays": 40,                # x the side's presenceDaysMult (Heg 1.25 -> 50d, League 0.8 -> 32d)
  "conditionApplyDay": 5,            # inherited constant; exposed so it can be compared
  "abortFraction": 0.5,
  "feasibilityMult": 1.25,           # STATION EXCLUDED (D80.2); 0 = no gate
  "systemCooldownDays": 60,          # shared $exiles_hl_op_cooldown key, interdiction's value
  "accessibilityPenalty": 0.5,       # GUESS -- matches the three sibling presence conditions
  "playerBuyPriceMult": 1.4,         # GUESS -- Price Spikes runs 1.5; a squeeze is shorter
  "conditionExpiryDays": 80,         # orphan backstop only: presenceDays x 1.25 + 30
  "suppressStationAttacks": true,
  "hiddenUntilArrival": true,
  "bountyPerFrigate": 3000,          # interdiction's value; the squeezed side pays
},
"recoveryConvoys": {
  "enabled": true,
  "maxActive": 2,                    # GUESS -- Ashen relief runs 2, refugee convoys 1
  "lagDaysMin": 20,                  # GUESS -- "after a lag" (sec 4.6); the victim has to react
  "lagDaysMax": 45,                  # GUESS
  "unloadDays": 10,                  # LOCKED by sec 4.6 ("~10-day unload orbit", author)
  "minRemainingDays": 5,             # the recovery floor -- a drip never goes below this
  "ledgerMaxRecords": 8,             # bound on outstanding relief; oldest dropped with a log line
  "recordExpiryDays": 400,           # a record nobody could service is dropped, logged
  "travelTimeoutDays": 180,          # the Ashen relief number
  "departTimeoutDays": 20,           # the Ashen relief number
  "combatFp": 10, "freighterFp": 24, "tankerFp": 6, "transportFp": 8,   # GUESSES
  "arrivalMessage": true,            # proximity-gated campaign message, no intel (D80.7)
  "debugMessages": true,             # TEST -- false for production
},
"hegemony": { "opWeights": { ..., "planetary": 1 }, "recoveryDaysMult": 1.0 },
"persean":  { "opWeights": { ..., "planetary": 3 }, "recoveryDaysMult": 0.75 },
"weariness": { ..., "perSqueezeSuffered": 6 },
"surgicalStrike": { ..., "extendedDisruptMult": 2.0 },   # <-- MOVES IN THIS COMMIT ONLY
```

**The measurement lines that must ship with those numbers** (§5, the `assaultFp: 800` lesson):

1. `SQUEEZE LAUNCHED: <source> -> <market> in <system> | actor= victim= | FP= defense=<enemy, station
   excluded> gate= | maxShipSize= maxFleetFp= presenceDays= | reason= state= tension=` — the
   interdiction launch line with the anchor market named.
2. `squeeze feasibility: <market> in <system> eligible/REJECTED (enemy N <= gate G = FP x mult)` —
   one line per candidate, per the Pass-3 fix's per-market shape.
3. `RELIEF RECORDED: <market> (<faction>) industries=<ids> base=<rolled>d extended=<rolled x mult>d
   reliefBudget=<extension>d lag=<Nd> | ledger=<k>/<max>` — the line that proves the extended
   baseline arithmetic.
4. `RELIEF CONVOY LAUNCHED: <source> -> <market> | budget=<Nd> perDay=<x.xx> sideMult=<m>
   | active=<k>/<max>` and, per day of unload, at debug level only,
   `relief drip: <market>/<industry> <before>d -> <after>d`.
5. `RELIEF OUTCOME: <market> delivered <N>d of <budget>d over <k> days (<reason: completed | convoy
   lost | industry already recovered | target gone>)` — **this is the line that tunes
   `extendedDisruptMult`**, because it says what fraction of the extension the war actually heals.

#### 14.11.4 Decisions D80.1–D80.10 — ALL OPEN, FOR SEAN

- **D80.1 — RETALIATION: strike vs squeeze, and the empty-pool fallback.**
  **RECOMMENDATION: `opWeights.planetary` = Hegemony 1 / League 3, and a symmetric fallback to the
  other op type, config-switched, default ON.** The weighting argues straight from §3.2.3: the
  Hegemony is "system blockades, production strikes, longer op durations" and the League is "quick,
  light, commerce-focused: interdiction raids, trade disruption". A planetary squeeze is *trade
  disruption aimed at one colony* — accessibility down, prices up, no ground action — which is the
  League's whole line, while the Hegemony's punitive instinct is to take a shipyard offline. 1:3
  mirrors interdiction (Heg 1 / League 3) exactly as surgical mirrors it the other way (Heg 3 /
  League 1), so each side's RETALIATION reads as a different *kind* of anger without a single line
  of forked code. Consequence worth stating: the Hegemony will still squeeze roughly one cycle in
  four, which is right — it is rare, not forbidden.
  **The fallback matters more than the weights.** `tryLaunchRetaliation()` claims the cycle before it
  attempts anything (Pass 3, deliberately), so an op-type roll that lands on a squeeze with no legal
  whitelisted target currently costs the cycle its entire punitive response. The League's squeeze
  pool is seven Hegemony markets, of which Jangala (Corvus, size 6, Hegemony's most defended
  non-capital), Yama (6) and Raesvelg (5, and §14.9.1 says the war admiral is based there) will
  routinely fail feasibility, and `ancyra_market` may not be in the economy at all. Recommend: roll
  the type, try it, and **on an empty pool try the other type once** before declaring the crisis
  contained — one extra economy scan per cycle at most, and it preserves the Pass-3 "one punitive op
  per cycle" invariant exactly. `retaliationFallbackToOtherType: false` restores the strict reading.
  *Alternative rejected:* weighting the roll by which pool is non-empty — that needs both scans every
  cycle and makes the asymmetry invisible.
- **D80.1c — ESCALATION squeezes?** Strikes get an ESCALATION cadence (`escalationCounterStrikes`);
  blockades get an ESCALATION-only cadence. **RECOMMENDATION: NO squeeze cadence in v1** — ESCALATION
  already runs interdictions, counter-strikes and up to two blockades per side, and a fourth
  concurrent op type is exactly the dogpile the guardrails exist to prevent. Ships behind
  `escalationSqueezes: false` so it is one config flip if the ESCALATION window reads thin in play.
- **D80.2 — Target, feasibility gate, and what the fleets actually do in orbit.**
  **RECOMMENDATION: target = whitelist ∩ live-owned-by-target ∩ `isEligibleVictimMarket` ∩ the
  standard system guards; gate EXCLUDES station strength; station attacks SUPPRESSED; the fiction is
  carried by prose, not by a fight.**
  §4.4's source sentence ("attack station and fleets in orbit") and §4.4's own ruled default (shape
  (b), the presence raid) genuinely contradict each other, and the contradiction has to be resolved
  in favour of shape (b) or Ashen §14.8 repeats itself. So: the gate uses
  `WarSimScript.getEnemyStrength(actor, system)` **only** — the interdiction gate, not the strike
  gate — because a squeezer never fights the station, and including station strength would make
  Fikenhild (6), Madeira (6), Jangala (6) and Yama (6) permanently unreachable and reduce the whole
  whitelist to its size-4s. `suppressStationAttacks` ships true, inherited unchanged from the parent
  stage, so the fleets hold station near the anchor world and engage **hostile mobile fleets that
  come to them** (the raid AI does this on its own; it is what the interdiction pickets already do)
  while the do-not-attack list keeps them off the orbital station. The player sees warships parked
  over a colony turning traffic away and fighting the patrols that try to lift the cordon. That is
  the squeeze. *Flagged for the author:* this makes the design-doc phrase "attack station" false in
  mechanics and true only in the condition's prose. If Sean wants the station actually engaged, that
  is `FGBlockadePlanetAction` and the Ashen §14.8 behaviour log comes with it — a separate ruling,
  not a config knob.
  **No `NPCHassler` on the squeeze fleets** [assistant]: Pass 4's blockade pickets hassle because
  vanilla's blockade fiction is "stop and turn back ships at the gates"; here the accessibility
  penalty already models the turned-away traffic, and adding hassling to a loitering fleet over a
  market is a new behaviour with no precedent in this director. Config: `hassleTraffic: false`, not
  even shipped unless Sean asks.
- **D80.3 — Ship-size cap and FP band.**
  **RECOMMENDATION: `maxShipSize: 2` (destroyer, S6), `maxFleetFp: 60`, FP 180–300 scaled by
  tension.** R7 exempted the *system* blockade from the cap on a specific argument — uncapped,
  stationary, Escalation-only, whitelist-confined, "earned against a real formation". Every clause of
  that argument except "whitelist-confined" fails for the squeeze: it is a RETALIATION op that fires
  most cycles, it sits in a populated inner system for 30–50 days where the player passes anyway,
  and §5's opening sentence is "destroyer cap on **all skirmish fleets**". Cruiser wrecks parked over
  Fikenhild for six weeks are the NPC-derelict-farming problem in its purest form (recorded in
  memory as an active concern). FP is lower than a strike's 250–400 because the squeeze does not have
  to punch through a station; `maxFleetFp` 60 sits between interdiction's 45 and the strike's 90 so
  the cordon arrives as two or three real formations rather than a swarm. **All three are guesses and
  all three are read off measurement lines 1 and 2.**
- **D80.4 — The condition: penalty, duration model, stacking.**
  **RECOMMENDATION: accessibility −0.5 flat + player-buy ×1.4 on every commodity; applied on day 5 of
  the orbit, lifted on withdrawal or abort, hard self-expiry at 80d; and NO stacking-resolution code,
  because overlap is prevented at launch.**
  −0.5 is the ratified figure the three sibling presence conditions already use (§4.1's comment: −0.2
  proved too weak to choke imports in the BF playtest). ×1.4 is a guess tuned down from Price Spikes'
  ×1.5 because a 120-day racket and a 40-day cordon should not cost the player the same. Day-5 apply
  and lift-on-withdrawal are inherited from the parent stage without a line of new code; the 80-day
  `getDurationDays()` is the Ruling-F orphan backstop and should never fire in normal operation.
  On stacking: the cheap answer is already shipped. `exile_hl_interdiction`, `Blockaded` from an HL
  blockade and this condition cannot coexist in one system, because all three managers consult
  `hasLiveOpIn(system)` and `ExileLiveOps.getSystemsWithActiveRaids(null)` before launching, and this
  pass adds the squeeze to every one of those scans. The BF "stronger wins" rule exists because BF
  Corsair Activity and BF assaults *can* legitimately overlap on one market; here they cannot, so
  importing the rule would be dead code guarding an unreachable state. **What ships instead is a
  permanent log line** if a squeeze ever finds its anchor already carrying `exile_hl_interdiction` or
  `Blockaded` at apply time — that is a guard failure and the tester needs to see it, not have it
  silently smoothed over.
  *Second-order case, flagged:* an **Ashen** blockade of a system containing a Hegemony/League
  whitelist market is covered by `ExileLiveOps` as of Pass 4's D8. A **vanilla** `PerseanLeagueBlockade`
  is not visible to `ExileLiveOps` at all; today it cannot touch a whitelist market (it targets
  Hegemony markets over AI inspections, and its condition is the same vanilla `Blockaded`). If it
  ever does, both conditions apply and the accessibility penalties add. **Recommend accepting that**
  — it is vanilla behaviour, it is rare, and it is self-limiting.
- **D80.5 — Recovery convoys, end to end.**
  **RECOMMENDATION, in the order the build needs it:**
  1. **Trigger seam:** `ExileHegLeagueStrikeStage.performRaid()`, immediately after `struckIndustries`
     is assembled — the instant the disruption is written. *Not* `reportOutcome()`: that fires at op
     end, up to 45 days later, and a strike whose group is destroyed on the way home would silently
     lose its relief. Two guarded lines; the stage holds no manager reference (static resolver).
  2. **The relief budget is the EXTENSION, computed at strike time, not a config day-count.**
     `reliefDays = rolledBaseDays × (extendedDisruptMult − 1)`. This is the single most important
     line in the pass: it makes §4.6's locked promise ("a full, uninterrupted convoy schedule lands
     the effective downtime at design-normal") **exactly true by construction**, and it survives any
     later change to `disruptDaysMin/Max` or `extendedDisruptMult` without a second knob drifting out
     of sync. At the shipped 45–90 × 2.0 the wound is 90–180 days and the budget is 45–90.
  3. **`ReliefRecord` (saved, named static):** `marketId`, `industryIds` (String list — re-resolved
     via `market.getIndustry(id)` at drip time, never a held `Industry` reference), `reliefDays`,
     `scheduledDay` (= `Global.getSector().getClock().getTimestamp()` + a rolled
     `lagDaysMin..lagDaysMax`), `victimFactionId`, `sourceMarketId` (null until launch). Ledger bounded
     at `ledgerMaxRecords`; oldest dropped with a permanent log line; records older than
     `recordExpiryDays` dropped and logged. Nothing else is saved.
  4. **Fleet:** `FleetFactoryV3` / `FleetParamsV3` with the victim's faction id,
     `FleetTypes.TRADE_SMALL`, `combatFp/freighterFp/tankerFp/transportFp` per config,
     `ignoreMarketFleetSizeMult = TRUE`, `maxShipSize = 2`, `withOfficers = false`, transponder on,
     `MEMORY_KEY_TRADE_FLEET` and `MEMORY_KEY_MAKE_ALLOW_DISENGAGE` set — the refugee-convoy recipe,
     which is the one that reads as a civilian convoy and can flee. Name "Relief Convoy". Cargo is
     flavour only; nothing mechanical reads it.
  5. **Source:** `ExileHegLeagueGeography.pickSourceNear(victimFactionId, targetMarket, random)` —
     same-faction market with a functional spaceport, weighted `1/(1+distLY × distanceFalloff)`. The
     existing `pickSource()` is size-weighted and would sail relief across the sector.
  6. **Orbit and drip:** `ORBIT_PASSIVE` on the struck market's primary entity for `unloadDays + 5`.
     `subtractPerDay = (reliefDays × side.recoveryDaysMult) / unloadDays`, applied **once per manager
     tick** (converted to days, so it is frame-rate independent and correct at any `timeScale`), to
     **each** recorded industry:
     `ind.setDisrupted(Math.max(minRemainingDays, ind.getDisruptedDays() − subtract))`, skipping any
     industry that is no longer disrupted. Verified against `BaseIndustry` (§14.11.1 item 5): the
     non-max form sets absolutely, and ≤0 unsets the key.
  7. **Mid-orbit death denies exactly the remainder** — which falls out for free, because nothing is
     pre-paid and nothing is written on death (§4.6). The op is dropped, the record is consumed, no
     second convoy is sent for the same wound. *That last clause is a ruling, not a mechanism:* one
     convoy per strike, destroyed means denied. Making relief retry would invert §4.6's whole point.
  8. **Target no longer disrupted** (a Nex raid healed it, the industry was destroyed, ownership
     flipped, the market decivilised): the convoy still flies, still unloads, drips nothing, logs
     `industry already recovered`, and departs. It is a civilian fleet delivering supplies to a
     friendly market — there is nothing wrong with it being pointless, and cancelling in flight would
     need a per-tick target scan the manager otherwise does not do.
  9. **Hegemony endurance asymmetry: `recoveryDaysMult` per side (Heg 1.0, League 0.75), NOT
     `convoysPerStrike`.** One knob, no change to fleet counts, no change to `maxActive` pressure, and
     it expresses §3.2.3's "industrial advantage = faster market recovery" precisely: the Hegemony
     heals its extension in full, the League heals three-quarters of it and its wounds linger. The
     multi-convoy alternative doubles the active-fleet budget to say the same thing and gives the
     player twice as many identical fleets to look at. **`recoveryDaysMult` must be ≤ 1.0** — the
     config comment says so and the code clamps it, because >1 would heal below design-normal and
     break §4.6's floor.
  10. **`surgicalStrike.extendedDisruptMult` 1.0 → 2.0 in the same commit**, with the warning comment
      rewritten from "LEAVE AT 1.0 UNTIL PASS 6 EXISTS" to a statement of the new invariant: the
      strike sets `base × mult`, relief can return at most `base × (mult − 1) × recoveryDaysMult`, so
      the best case is design-normal and the worst case is the full extension. **2.0 is a guess**; it
      is the first number to revisit off measurement line 5, and `1.0` remains a legal setting that
      simply makes relief a no-op (budget zero — the manager must handle that without launching).
- **D80.5a — Route-managed or directly spawned?** §4.6 says "route-managed"; **RECOMMENDATION:
  direct-spawn, as both existing Exiles convoy systems do.** `RouteManager.RouteFleetSpawner` exists
  and would let the fleet be abstracted away when the player is far off, but neither `AshenReliefManager`
  nor `ExileRefugeeConvoyManager` uses it, the drip runs on the manager's own tick either way
  (§4.6's "effects fire while the fleet is abstracted" is satisfied by *any* implementation, because
  the manager, not the fleet, does the work), and Pass 7's BF proxy needs a **real, findable fleet to
  intercept**. Cost is at most `maxActive` (2) persistent civilian fleets. Recording this as a
  deliberate deviation from §4.6's word, not an oversight.
- **D80.6 — Unrest-decay acceleration: DROP.** §4.6 made this a build-time check against the actual
  class. Read: `impl/campaign/econ/RecentUnrest.java`. **There is no clean per-market decay lever.**
  `DECREASE_DAYS` is a `public static` (90f) — writing it changes the decay rate for every market and
  every source of unrest in the sector, permanently, which is exactly the kind of global side effect
  CLAUDE.md §7 and the Emergency-Measures work exist to avoid. The per-instance countdown
  `untilDecrease` is `protected` with no accessor, so a per-market acceleration needs reflection.
  **Conclusion: DROP, per §4.6's own instruction ("dropped without redesign if not").**
  *One clean alternative exists and is NOT recommended for v1* [assistant]: `counter(int points,
  String reason)` is public, clamps the penalty at zero, removes the condition when it hits zero, and
  touches only the tooltip's reason list otherwise — so `RecentUnrest.counter(1, "Relief convoy")` on
  a completed unload would be a safe, discrete, one-line "relief also calms the streets". But that is
  a **different mechanic** from decay acceleration, it interacts with `ExileUnrestGuard.isBattered()`
  (which reads `RecentUnrest.getPenalty() >= 4`) in a way that could pull a market back *into* the
  target pool, and it needs Sean's ruling. Ships only if asked, behind `reliefCountersUnrest: false`.
- **D80.7 — What the player sees of a convoy.** **RECOMMENDATION: no intel; a proximity-gated
  campaign message on arrival, `arrivalMessage: true`.** The refugee-convoy precedent is exactly this
  and it was live-tested and accepted (2026-08-19): `fleet.isInCurrentLocation() &&
  fleet.isVisibleToPlayerFleet()` around a single `addMessage`. An intel entry per convoy would put
  2–8 low-stakes listings on the board per campaign year for an event whose whole payload is a number
  ticking down invisibly; §3.4 reserves intel for "state transitions the player should feel". The
  convoy itself is visible in space, the disruption timer in the industry tooltip is the tell, and
  Pass 8 is where the player gets a reason to care. *If Sean wants a listing*, the cheapest honest
  version is a line appended to the existing strike intel rather than a new class — offer it, do not
  build it.
  **The "Relief Effort" informational condition (§4.6, §11 item 3) — PLAN IT, DEFAULT OFF.** Build
  `ExileHegLeagueReliefEffort` (`exile_hl_relief_effort`, order 160) as a zero-effect
  `ExileTimedCondition` applied at orbit start and removed at departure, gated on
  `recoveryConvoys.reliefEffortCondition: false`. It is ~40 lines, it is the only thing that makes
  the repair *legible* in the market panel, and `ExileReliefShipment` is the working precedent. Off
  by default until Sean nods; a CSV row that nothing applies is inert.
- **D80.8 — Squeeze intel presentation.** **RECOMMENDATION: hidden until arrival, source NOT
  concealed, no `getMapLocation()`/`getArrowData()` overrides** — inherited verbatim from
  `ExileHegLeagueRaidIntel`, which is the sibling convention (§3.4) and already does all of this. The
  op posts as "System Bounty — <system>" like its siblings, and the bounty text is correct: the
  squeezed side really does pay for dead squeezers. One override only: `getOpEndedText()`.
- **D80.9 — Save-compat inside a dev campaign.** The recovery manager is a new script:
  `onGameLoad` adds it under the standard `if (!hasScript(...))` guard, so a Pass-4 save picks it up
  and simply has an empty ledger — wounds struck before the upgrade get no relief, which is correct
  and needs no migration. Every new saved field is a primitive or a named static class with a
  no-arg-safe default; `readResolve()` re-initialises transients and null-checks both lists, the
  `ExileRefugeeConvoyManager.initTransients()` pattern (XStream restores transient primitives as
  zeroes *before* `readResolve`, which is why the defaults are re-asserted there and not at field
  declaration). The squeeze stage and intel are new classes, so no in-flight op can deserialise into
  them. `OP_TYPE_PLANETARY = 3` is additive; the existing three constants and both legacy
  `reportOperationWeariness` call sites are untouched. **A campaign that has never had a strike land
  needs no new game; one mid-strike will simply not schedule relief for that one strike.**
- **D80.10 — Contradictions and under-specification found in §4.4 / §4.6.** All ten are listed here
  so the build is not the place they get discovered:
  1. **§4.4 and §8 both name `getPlayerSupplyPriceMod()` on the market.** It does not exist there; it
     is on `CommodityOnMarketAPI` (verified, `campaign/econ/CommodityOnMarketAPI.java:151`). The
     implementation is a per-commodity loop. Docs corrected in this pass.
  2. **§4.4's fiction vs §4.4's ruled shape.** "Attack station and fleets in orbit" describes shape
     (a); the ruled default is shape (b), which never fights the station. Resolved in D80.2; needs
     Sean's eye because it is his design-doc sentence.
  3. **§4.4 never says whether the squeeze is one market or the system.** §4.1's ratified
     victim-faction scope and §4.2's ratified system-wide scope leave a third scope unstated. Taken
     here as **the anchor market only** — that is what "planetary" means beside a "system" blockade,
     and it is the only reading that keeps the three ops distinguishable.
  4. **§4.4 gives no FP, no duration, no cap and no cooldown.** All four are invented here as guesses
     with measurement lines (D80.3, §14.11.3).
  5. **§3.2.2 says RETALIATION is "sized by tension"** and Pass 3 implemented that for strikes only.
     The squeeze inherits the same formula; flagged because it means a high-tension squeeze can be a
     450-FP destroyer cordon, which is a lot of hulls parked in one place.
  6. **§4.6 says "route-managed"** but no Exiles convoy is. D80.5a.
  7. **§4.6's `maxActive` is ambiguous** — active *convoys* or outstanding *relief records*? Read
     here as convoys, with a separate `ledgerMaxRecords` bound on records, because a bounded convoy
     count with an unbounded ledger is a save-bloat path.
  8. **§4.6 never says what happens to a second strike on a market that already has relief
     outstanding.** Taken here as: a second record, serviced in order; the drip clamps at
     `minRemainingDays` so double relief cannot over-heal. Flagged as the likeliest place a tester
     sees something surprising.
  9. **§4.6's "Hegemony endurance = more convoys and/or larger per-arrival subtraction"** offers two
     mechanisms and rules neither. D80.5 item 9 picks one.
  10. **§4.6's informational condition is still "pending Sean's nod"** (§11 item 3) and is the only
      item in this plan that is genuinely blocked on an author yes/no. D80.7 ships it switched off so
      the pass is not gated on it.
  **Plus one hook name, reserved and nothing else** (§6.2, Pass 7): the BF proxy will read
  `$exiles_hl_relief_target` — a sector-memory key naming a market whose relief convoy is currently in
  flight, written by the recovery manager on launch and unset on completion. **Pass 6 writes the key
  and nothing reads it.** The war director never calls the BF director; the decoupling seam stays
  one-way. Pass 8's interception dialogue will likewise key off the fleet flag
  `$exiles_hl_relief_convoy`, set at spawn — also written now, read by nothing.

#### 14.11.5 Build order and verification

1. Config (`planetaryBlockade{}`, `recoveryConvoys{}`, the two per-side keys) + `ExileHegLeagueConfig`
   parsing + `Side` 8th argument + `ExileHegLeagueGeography.isPlanetaryBlockadeWhitelisted` /
   `pickSourceNear` + the geography line already printed by Pass 4. Boots, logs, launches nothing.
2. `ExileHegLeaguePlanetaryBlockade` condition + CSV row 159 + placeholder icon; apply/remove it by
   hand from the console to confirm both mods unwind.
3. `ExileHegLeagueSqueezeStage` + `ExileHegLeagueSqueezeIntel` + `OP_TYPE_PLANETARY`.
4. `ExileHegLeagueSqueezeManager` + the retaliation op-type roll + the three `hasLiveOpIn` extensions.
   **Stop here and run the squeeze half** (Q80–Q86) before touching the strike stage.
5. `ExileHegLeagueRecoveryManager` + the two-line strike-stage hook, with `extendedDisruptMult` still
   at **1.0** — the budget is zero, the manager must launch nothing and log that it did so.
6. Flip `extendedDisruptMult` to 2.0. This is the whole balance change and it is one line, so it is
   its own step and its own commit.
7. `ExilesModPlugin` registration + `onGameLoad` guard. 8. Doc updates + Q-matrix.

**Janino:** every touched class through `tools/janino-check.ps1`; cast every `List.get()`; no
lambdas, no diamonds, no enhanced generics; `ReliefOp` and `ReliefRecord` are **named static**
classes (they are saved state — the `ConvoyOp`/`ReliefOp` precedent). `director_config.json`
verified with the comment-aware brace/bracket count and a full parse after comment stripping, as
Pass 4 did.

**Test discipline:** the squeeze is a presence op, so unlike blockades it *is* testable under
`timeScale` compression — but `timeScale 1` is still recommended for the first run so the day-5
condition apply and the 10-day unload are readable against real travel. `testing.forceState: 3`
pins RETALIATION; `testing.forceIncidentOffender` picks which side squeezes.

| # | Check | Expect |
|---|---|---|
| Q80 | First boot, geography line | all 14 `planetaryBlockadeWhitelistMarkets` print `=OK`, or `ancyra_market=UNRESOLVED` under a start that never completes Galatia (expected — §14.9.1 item 3) |
| Q81 | RETALIATION op-type split over ~10 cycles | Hegemony strikes ≫ squeezes, League squeezes ≫ strikes, roughly 3:1 each way; every cycle still produces **at most one** punitive op |
| Q82 | Empty-pool fallback | a `SQUEEZE found no eligible target` line is followed by a strike launch in the same cycle, never by a second squeeze attempt, never by two ops |
| Q83 | Target legality | every anchor is on the whitelist, owned by the offender, non-exempt, no player market in system, no live HL op, no `$exiles_hl_op_cooldown` |
| Q84 | Squeeze behaviour in orbit (AMENDED by the 2026-09-20 station ruling) | fleets loiter on the anchor market, never make a ground action, engage hostile fleets that come to them AND may engage the anchor's orbital station (`suppressStationAttacks` false); a beaten station reads as disrupted for vanilla's repair window; feasibility lines print `defense = enemy + station` |
| Q85 | The condition | `exile_hl_planetary_blockade` on the anchor market ONLY, from day 5, lifted the tick the cordon withdraws or aborts; tooltip names the acting side; −50% accessibility and +40% player buy prices both visible and both gone after |
| Q85a | Orphan backstop | console-remove the squeeze intel mid-presence: the condition self-expires within `conditionExpiryDays` and leaves no price or accessibility mod behind |
| Q86 | No stacking | no market ever carries `exile_hl_planetary_blockade` together with `exile_hl_interdiction` or `Blockaded` from an HL op; if the guard-failure log line ever prints, that is a bug to chase, not a tuning note |
| Q87 | Outcome + weariness | completed presence → `+6.0: squeeze completed against <victim>` and `perCompletedOp` tension; broken up → `+8.0: … did not hold` on the actor; cooldown set at END |
| Q88 | Bounty | player kills a squeezer → the squeezed side pays per hull size + `SYSTEM_BOUNTY_REWARD`; no payout when the player fought alongside the cordon; interdiction and strike bounties unchanged |
| Q89 | Relief recorded | every `SURGICAL STRIKE LANDED` is followed by exactly one `RELIEF RECORDED` line, with `extended = base × 2.0` and `reliefBudget = base × 1.0` |
| Q90 | Relief delivered | convoy launches after the lag from a **nearby** same-faction spaceport; over `unloadDays` the industry's disrupted timer falls by the budget × side mult and **never below `minRemainingDays`**; `RELIEF OUTCOME` says `delivered N of N` |
| Q91 | Denial | kill a convoy mid-unload → the timer stops falling at exactly the delivered fraction, nothing is written, no replacement convoy is ever sent for that wound |
| Q92 | Asymmetry | Hegemony wounds return to the 45–90 design-normal band; League wounds settle ~25% above it |
| Q93 | No over-heal, no permanence | across a long run, no industry disrupted by an Exiles strike ever reads below 45 days at relief completion, and none is still disrupted past its extended figure |
| Q94 | Degenerate config | `extendedDisruptMult: 1.0` → budget 0, no convoy launches, one log line per strike saying so; `recoveryConvoys.enabled: false` → ledger never fills |
| Q95 | Save/load | reload mid-unload: the drip resumes at the right rate and stops on schedule; reload mid-squeeze: the condition persists and still lifts; a Pass-4 save gains the recovery manager with an empty ledger |
| Q96 | Bounds | `activeOps` never exceeds `maxActive`, the ledger never exceeds `ledgerMaxRecords`, no fleet survives `travelTimeoutDays`, no condition survives its expiry, no orphaned convoy after a target decivilises or changes hands |
| Q97 | Nex run | ownership flip of a whitelist market moves it in/out of the squeeze pool with no code change; a Nex raid that heals a disrupted industry makes relief a logged no-op rather than an error |

---

#### 14.11.6 Pass 6 build record [BUILT 2026-09-20; Janino-clean; UNTESTED IN GAME]

Built to the §14.11 plan, with D80.1–D80.10 adopted as build-time assumptions per the section
header. **Nothing below has been run in game.** The Q80–Q97 matrix in §14.11.5 is the test plan and
every item in it is outstanding.

**Files**

| File | New/Mod | Lines | What |
|---|---|---|---|
| `data/scripts/exiles/ExileHegLeagueSqueezeStage.java` | NEW | 236 | `extends ExileHegLeagueInterdictionStage`. Overrides `getLoiterToken()` (anchor orbit, unconditional), `applyConditions()`/`removeConditions()` (the ONE anchor market), `getConditionApplyDay()`, `performRaid()` (no-op) and the five text methods. `canRaid`, `updateRoutes`, `updateStatus`, `suppressStationTargeting`, `isPresenceCompleted` and the FP abort inherited untouched. Carries the guard-failure WARN. |
| `data/scripts/exiles/ExileHegLeagueSqueezeIntel.java` | NEW | 94 | `extends ExileHegLeagueRaidIntel`, the `ExileHegLeagueStrikeIntel` template: `isInterdictionOp()` → false, `getOpEndedText()`, `reportOutcome()` with `OP_TYPE_PLANETARY`. |
| `data/scripts/exiles/ExileHegLeagueSqueezeManager.java` | NEW | 354 | Stateless launcher — **not** an `EveryFrameScript`. `launchSqueeze(director, config, actorId, targetId, reason, random)`, the whitelist-first anchor picker, the station-excluded feasibility gate with its per-candidate line, tension-scaled FP, the `SQUEEZE LAUNCHED` measurement line, `applyConditionTunables()` and a private `hasLiveOpIn()`. |
| `data/scripts/exiles/ExileHegLeagueRecoveryManager.java` | NEW | 769 | `EveryFrameScript`, the `AshenReliefManager` shape. Named static `ReliefRecord` + `ReliefOp`, bounded ledger and convoy list, TRAVEL→ORBIT(drip)→DEPARTING with timeouts, static `record(...)`/`get()`, `readResolve`/`initTransients`, direct `FleetFactoryV3` spawn, proximity-gated arrival message, the three measurement lines, and the two reserved hooks. |
| `data/scripts/marketconditions/ExileHegLeaguePlanetaryBlockade.java` | NEW | 179 | `exile_hl_planetary_blockade`. Accessibility flat + the per-commodity `getPlayerSupplyPriceMod().modifyMult` loop, `$exiles_hl_squeeze_actor` in market memory, hard self-expiry, no intel reference, PLACEHOLDER prose. |
| `data/scripts/marketconditions/ExileHegLeagueReliefEffort.java` | NEW | 64 | `exile_hl_relief_effort`, zero-effect. Applied/removed by the recovery manager **only** when `reliefEffortCondition` is true. |
| `data/scripts/exiles/ExileHegLeagueConfig.java` | MOD | 1273 (+253) | `planetaryBlockade{}` + `recoveryConvoys{}` parsing, `Side.planetaryWeight` (8th constructor arg, both construction sites updated), `Side.recoveryDaysMult` with its ≤ 1.0 clamp, `weariness.perSqueezeSuffered`, the new timeScale entries, javadoc "still absent" list trimmed to `playerInterception` + `midan`. |
| `data/scripts/exiles/ExileHegLeagueGeography.java` | MOD | 264 (+65) | `isPlanetaryBlockadeWhitelisted(config, market)` and `pickSourceNear(config, factionId, target, random)`. |
| `data/scripts/exiles/ExileHegLeagueStrikeManager.java` | MOD | 672 (+115) | The RETALIATION op-type roll + symmetric empty-pool fallback, `rollBaseDisruptDays()` split out, the base figure passed to the stage and printed on the launch line, the once-per-load squeeze condition-tunable push. |
| `data/scripts/exiles/ExileHegLeagueStrikeStage.java` | MOD | 584 (+37) | New saved `rolledBaseDisruptDays` + `getRolledBaseDisruptDays()`, the constructor argument, and the two-line relief hook at the end of `performRaid()`. |
| `data/scripts/exiles/ExileHegLeagueInterdictionStage.java` | MOD | 327 (+17) | `getConditionApplyDay()` accessor — behaviour-neutral in the parent; see deviation 1. |
| `data/scripts/exiles/ExileHegLeagueDirector.java` | MOD | 1173 (+10) | `OP_TYPE_PLANETARY = 3` + the two log-phrase cases. |
| `data/scripts/exiles/ExilesModPlugin.java` | MOD | 1313 (+17) | Recovery-manager registration + guarded `onGameLoad` re-add. The squeeze manager is deliberately NOT a script. |
| `data/scripts/exiles/ExileHegLeagueInterdictionManager.java` | MOD | 700 (+9) | Javadoc only — see deviation 2. |
| `data/scripts/exiles/ExileHegLeagueBlockadeManager.java` | MOD | 556 (+7) | Javadoc only — see deviation 2. |
| `data/config/exiles/director_config.json` | MOD | 2253 (+241) | The two new blocks, the three per-side keys, `perSqueezeSuffered`, and the `extendedDisruptMult` flip. **No key moved, renamed or removed.** |
| `data/campaign/market_conditions.csv` | MOD | 25 (+2) | Rows at `order` 159 and 160, `decivRemove` TRUE, PLACEHOLDER name/desc. |
| `graphics/icons/markets/exile_planetary_blockade.png` | NEW | — | **Placeholder: a byte copy of `exile_blockade_collateral.png`** (3,705 bytes). Non-vanilla path, so it overrides nothing (§7 exact-path rule). Author art job. |
| `graphics/icons/markets/exile_relief_effort.png` | NEW | — | The same placeholder, the same copy. |

**Janino:** `tools/janino-check.ps1` over all fifteen new and touched classes —
`ExileHegLeagueConfig`, `Geography`, `Director`, `InterdictionStage`, `InterdictionManager`,
`BlockadeManager`, `SqueezeStage`, `SqueezeIntel`, `SqueezeManager`, `RecoveryManager`,
`StrikeStage`, `StrikeManager`, `ExilesModPlugin`, `ExileHegLeaguePlanetaryBlockade`,
`ExileHegLeagueReliefEffort` — **exit 0 on every one.**

**`director_config.json` verified** with the comment/string-aware scan the Pass-4 build used: brace
balance **0**, bracket balance **0**, minimum brace depth **0**, not inside a string at EOF; then a
full parse after stripping `#` comments and trailing commas — **PARSE OK**, with
`planetaryBlockade` reading 20 keys, `recoveryConvoys` 17, `extendedDisruptMult` 2.0,
`hegemony.opWeights.planetary` 1 / `recoveryDaysMult` 1.0, `persean` 3 / 0.75, and
`weariness.perSqueezeSuffered` 6.

**Config keys added** (none moved, renamed or deleted — CLAUDE.md §7 hard rule):
`planetaryBlockade.{enabled, spawnFpMin, spawnFpMax, tensionFpScale, maxShipSize, maxFleetFp,
prepDays, presenceDays, conditionApplyDay, abortFraction, feasibilityMult, systemCooldownDays,
accessibilityPenalty, playerBuyPriceMult, conditionExpiryDays, suppressStationAttacks,
hiddenUntilArrival, bountyPerFrigate, retaliationFallbackToOtherType, escalationSqueezes}`;
`recoveryConvoys.{enabled, maxActive, lagDaysMin, lagDaysMax, unloadDays, minRemainingDays,
ledgerMaxRecords, recordExpiryDays, travelTimeoutDays, departTimeoutDays, combatFp, freighterFp,
tankerFp, transportFp, arrivalMessage, reliefEffortCondition, debugMessages}`;
`hegemony.opWeights.planetary` (1) and `persean.opWeights.planetary` (3);
`hegemony.recoveryDaysMult` (1.0) and `persean.recoveryDaysMult` (0.75);
`weariness.perSqueezeSuffered` (6).
**`hassleTraffic` and `reliefCountersUnrest` are deliberately NOT shipped** (D80.2, D80.6).

**THE `extendedDisruptMult` FLIP.** `surgicalStrike.extendedDisruptMult` moved **1.0 → 2.0** in the
same commit, which is the condition the Pass-3 comment set and §14.11's opening paragraph restated.
Its warning comment was rewritten from "LEAVE AT 1.0 UNTIL PASS 6 EXISTS" to a statement of the new
invariant: the strike writes `base × mult`, relief can return at most
`base × (mult − 1) × side.recoveryDaysMult`, so the best case is design-normal and the worst case is
the full extension, and nothing can heal below the design-normal band. **2.0 is a guess** and is the
first number to revisit off `RELIEF OUTCOME`. **This is the pass's whole balance change**, and it is
why Q89–Q94 matter more than the squeeze half's checks: a 45–90 day wound is now a 90–180 day wound
until a convoy arrives.

**Deviations and conservative readings**

1. **The parent interdiction stage was touched — one behaviour-neutral accessor.**
   `CONDITION_APPLY_DAY` is a `public static`, and a subclass static would merely *shadow* it while
   the inherited `updateStatus()` went on reading the parent's, silently. The plan anticipated this
   ("if they are private, restructure minimally… WITHOUT changing parent behaviour").
   `ExileHegLeagueInterdictionStage` gained `protected float getConditionApplyDay()` returning
   `CONDITION_APPLY_DAY`, and `updateStatus()` now calls it. For interdiction the value read is
   byte-identical; the squeeze overrides it. `applyConditions()` / `removeConditions()` /
   `getLoiterToken()` were already `protected` and needed nothing.
2. **The three `hasLiveOpIn()` extensions were NOT written — they were already correct.** Every one
   scans `getIntel(ExileHegLeagueRaidIntel.class)`, which returns subclasses (the Pass-3
   `countActive` comment establishes this in the same codebase), and `ExileHegLeagueSqueezeIntel` is
   such a subclass. Adding a squeeze-specific scan would double-count. What shipped instead is a
   javadoc note at each of the three sites saying so, so the next reader does not "fix" it. The same
   reasoning covers `ExileLiveOps.getSystemsWithActiveRaids`, which scans vanilla `RaidIntel.class`
   and therefore already keeps refugee and Ashen traffic out of a squeezed system. Conversely the
   two `countActive` methods needed nothing either: interdiction's filters on `isInterdictionOp()`
   (false for a squeeze) and the strike's scans `ExileHegLeagueStrikeIntel.class` (which a squeeze
   is not), so a squeeze consumes neither concurrency slot. **Verified by reading, not assumed.**
3. **The relief budget is computed as `disruptDays − rolledBaseDays`, not `base × (mult − 1)`.**
   Arithmetically identical modulo the integer truncation `disruptDays` already carries, and it
   means the stage never reaches back for a live config at impact time — the mult is a tunable that
   can change between launch and impact, and dividing by whatever it reads *then* would silently
   mis-compute the budget of an op already in flight.
4. **The base roll is carried as a new saved field, not derived.** `ExileHegLeagueStrikeStage`
   gained `rolledBaseDisruptDays`, set at construction. A pre-Pass-6 save restores it as 0, which
   `getRolledBaseDisruptDays()` treats as "equal to `disruptDays`" → a relief budget of zero. That
   is *correct*, not a fallback: a strike launched before this pass was necessarily launched at
   `extendedDisruptMult` 1.0, because the config forbade anything else. `rollDisruptDays()` was
   **replaced** by `rollBaseDisruptDays()` rather than delegated to — `launchStrike` now rolls the
   base and multiplies inline, the multiplied form had no remaining caller, and keeping it as
   documentation would have been dead code with a rationalisation attached. Same single roll, same
   distribution.
5. **The squeeze condition's tunables are pushed from the STRIKE manager's tick**, once per load,
   before its enabled gate. The squeeze manager is deliberately not a script, so it has no tick of
   its own; pushing at launch time instead would leave a condition restored from a save reading the
   *code* defaults until the next squeeze happened to launch — and the condition's `apply()` re-runs
   on every economy reapply, so that is a real wrong-value window, not a cosmetic one. The strike
   manager is this op's only launcher, which makes it the honest owner.
6. **`suppressStationAttacks` is a static SHARED with the parent stage.** Both ops ship `true`, so
   they agree today, and each manager's push now asserts its own value rather than letting whichever
   ticked last win. If the two knobs are ever meant to *differ*, the static has to become an
   instance field. Recorded rather than pre-solved.
7. **`ReliefRecord` stores a `lagDays` float plus the record timestamp, not an absolute "sail at"
   timestamp.** `CampaignClockAPI` exposes `getElapsedDaysSince(long)` but no "timestamp N days from
   now", so an absolute form would mean hand-rolled seconds arithmetic against
   `getSecondsPerDay()`. One subtraction on a slow path is cheaper and impossible to get wrong.
8. **`planetaryBlockade.prepDays` is shipped but not yet consumed.** The squeeze gathers at the
   nearest jump point like its three siblings and the assemble stage sets its own pace; the key is
   there so the block reads consistently with `blockade.prepDays` and so a later staged-assembly
   knob needs no new key. **`escalationSqueezes` is likewise read into config and implemented by
   nothing** (D80.1c). Both facts are stated in the JSON comments, so neither can look like a
   silently broken setting.
9. **The relief drip only spends budget on days that actually landed somewhere.** If every recorded
   industry has already recovered (a Nex raid, an ownership flip, a destroyed industry), the convoy
   still flies, still unloads and still departs, but reports `industry already recovered` with its
   budget intact rather than claiming to have delivered it. D80.5 item 8 specified the behaviour;
   this is the honest accounting of it in the outcome line.
10. **`$exiles_hl_relief_target` is a single sector-memory key, so it is only unset when no
    remaining convoy still claims that market.** With `maxActive` 2 two convoys can be in flight at
    once, and a naive unset-on-completion would blind the reserved Pass-7 hook to the survivor.
    Nothing reads the key in this pass either way.
11. **`recoveryConvoys.minRemainingDays` is NOT timeScaled**, unlike most of the block. It is a floor
    on a vanilla industry-disruption timer, which the engine runs on the real clock, not a duration
    in the war's fiction — the reasoning §14.9 recorded for `blockade.collateralExpiryDays`.
    `travelTimeoutDays` and `departTimeoutDays` are likewise unscaled, per the Ashen relief
    precedent.
12. **Both new market-condition icons are byte copies of `exile_blockade_collateral.png`.** They sit
    at non-vanilla paths and so override nothing (§7's exact-path rule), but they are placeholders,
    and three conditions now share one picture.
13. **All player-facing prose is PLACEHOLDER**, including the two CSV descriptions, the condition
    tooltips, the stage's `showStageInfo` text and the four fleet-action strings (R12, §11 item 2).
14. **Untested, and worth naming specifically:** the squeeze's loiter-on-market behaviour has never
    been run. §14.11.1 notes that `loiterOnFocusMarket` ships *false* for interdiction precisely
    because pressing onto a defended Core world's docks is what produced the Ashen station-suicide
    problem, and this op does it unconditionally. **Q84 is the check that matters most**, and the
    inherited do-not-attack refresh is the only thing standing between the cordon and the station it
    is parked beside.

#### 14.11.7 Late rulings on the Pass 6 build [RULED — Sean, 2026-09-20 evening; BUILT same session; Janino-clean; UNTESTED]

Sean ruled on the five build-time assumptions surfaced at hand-off:

1. **The squeeze FIGHTS the station — D80.2 REVERSED.** "Attack station and fleets in orbit" is
   literal. Sean's reasoning: the Ashen blockade suppressed station attacks because the Crusade had
   to hold its main strength for later, and its blockade escorted the Hussar station assault; the
   Hegemony/League squeeze has no such constraint. **Built:** `ExileHegLeagueInterdictionStage`
   gained an instance hook `isStationSuppressionEnabled()` over its static (interdiction unchanged);
   `ExileHegLeagueSqueezeStage` overrides it with its OWN static `SUPPRESS_STATION_ATTACKS`
   (shipped false), so the two managers no longer write one shared flag (the §14.11.6 deviation 6
   risk, now closed). The squeeze feasibility gate now INCLUDES the anchor's station strength per
   market (the strike gate's shape and value, `feasibilityMult` 1.25 → **1.0**), and the launch and
   feasibility lines print `defense = enemy + station`. Because a destroyer cordon cannot crack a
   size-6 station, `maxShipSize` **2 → 3** (the strike's cruiser cap, Sean's 2026-08-28 strike ruling
   extended), `maxFleetFp` **60 → 90**, `spawnFpMin/Max` **180–300 → 250–400** (the strike's band).
   D80.3's destroyer recommendation is superseded; the farming concern is mitigated, per Sean, by the
   Refugee Salvage Expeditions claiming the best wrecks first. Q84 amended. Expect the size-6 anchors
   to pass the gate only near the top of the tension-scaled band — read the feasibility lines first.
2. **RETALIATION strike-vs-squeeze roll (Heg 1:3 / League 3:1) with the single fallback: TEST AS
   BUILT** ("let's test with your decision and see how it pans out").
3. **`extendedDisruptMult` 1.0 → 2.0: AGREED.**
4. **Unrest-decay acceleration DROPPED: AGREED** ("we'll test and, if necessary, find a workaround").
5. **Relief Effort informational condition: ON** (`recoveryConvoys.reliefEffortCondition` true, code
   default true). §11 item 3 is CLOSED. It was never tied to unrest decay: it is the zero-effect
   marker on the struck market while a convoy unloads, so the drip is legible in the market panel.
   Prose remains PLACEHOLDER.

#### 14.11.8 Run 2 read [2026-09-20 late; ORGANIC run — `forceState` edit had not saved; two reporting fixes BUILT]

Sean's second campaign of the evening (~2 cycles, ~200 game days, Nex, x10, on-screen for the
Laicaille strike). `testing.forceState` on disk still read `-1`, so ESCALATION was never entered and
**no blockade launched — Q53–Q63 remain open.** What the run did exercise, all clean, zero Exiles
exceptions:

- **Q52/Q80 PASS:** every whitelist id resolved, `ancyra_market=OK` under this start.
- **Two RETALIATIONs, both Hegemony, both rolled STRIKE** (Yesod, then Laicaille Habitat). No
  squeeze yet — at 1:3 that is a 56% outcome for two rolls, not evidence. A `RETALIATION roll` log
  line was added so Q81 can be read directly.
- **Relief loop end to end (Q89, Q90 PASS; Q92 arithmetic PASS):** `RELIEF RECORDED` 73d for a 148d
  Yesod wound (base 74 × 2) and 88d for a 178d Laicaille wound; League convoys launched after the
  lag from the nearest League spaceport with `budget = 0.75 × relief` (55d, 66d); the drip ran 66
  ticks over ~10 game days per convoy and delivered exactly the budget; Sean watched the Laicaille
  convoy arrive and repair. Two REPORTING bugs fixed: (1) the outcome line printed "over 2 days"
  because `phaseDays` resets at departure — a separate `orbitDays` counter now feeds it; (2) the
  fully delivered Yesod convoy was labelled `convoy lost` because it vanished on departure
  alive-but-empty — the DEPARTING branch now treats `isEmpty()` as completion too (nothing can be
  denied after the unload). Neither affected mechanics.
- **Pass 5 beats (Q64–Q66 PASS):** cycle 1 hit the warning band, rolled 0.06 vs 0.30 → pre-emptive
  ceasefire talks; `ceasefire intel: convened (pre-emptive)` then `outcome CEASEFIRE HOLDS (full
  stand-down)`; DE-ESCALATION; meter 46.3 → 15.
- **Observation, Sean's (accepted as flavour, no change):** the Laicaille strike group was still
  fighting when the ceasefire was declared and kept pursuing defenders until its window ran out.
  In-flight ops are not aborted by a ceasefire anywhere in the spec; the fiction of orders not
  reaching the fleet in time is welcome. Recorded so nobody "fixes" it.
- **Interdiction unchanged after the D4 extraction** (four launches at the normal cadence).

**Next:** re-run Run 2 with `testing.forceState: 4` verified on disk (`grep forceState` before
launching), then Run 3 with `forceState: 3` + `forceIncidentOffender: "hegemony"`.

### 14.12 Run 3 read [2026-09-21; FORCED BLOCKADE run (`forceState: 4`) — Q52–Q54, Q57, Q58, Q61, Q62 PASS, zero exceptions; then a FORCED SQUEEZE run (`forceState: 3`) — NULL RESULT, the pin never sets the offender]

Two sessions on Sean's "Shana" save (Nex, x10), both `forceState` values confirmed on disk before
reading. Log split at the launcher lines; the runs are contiguous in game time (Mar 12 → Jul 6
c207, then Jul 6 → ~Dec c207 after the config edit at 22:37).

**Session 1 — the blockade half (Q52–Q63). Zero Exiles exceptions.** ESCALATION #1 fired on Mar
12 c207 and both sides launched inside the day:

| Blockade | Actor → victim | Launched | est / defence / gate | HOLDING (tier) | Completed (credit) |
|---|---|---|---|---|---|
| Raesvelg → **Tyle** | Hegemony → League | Mar 13, ESCALATION entry | 700 / 168 / 875 | Apr 14, **1 full** | Jun 20, League +8, tension +2 |
| Mairaath → **Samarra** | League → Hegemony | Mar 13, ESCALATION entry | 700 / 507 / 875 | Apr 12, **0 half** | Jun 6, Hegemony +8, tension +2 |
| Eventide → **Westernesse** | Hegemony → League | Jun 30, ESCALATION cadence | 700 / 84 / 875 | Aug 1 (session 2, after reload), **1 full** | Nov 11, League +8, tension +2 |

Payload windows printed 56d (Hegemony, 45 × 1.25) and 36d (League, 45 × 0.8) — D7 confirmed.

- **Q52 PASS.** Geography line: `blockadeWhitelistSystems isirah=OK tyle=OK westernesse=OK
  naraka=OK samarra=OK` — the whitelist on disk is now five systems, not the four Q52 was written
  against (Arcadia out, Naraka + Samarra in). Every id resolved.
- **Q53 PASS.** Both sides launched within the day of entry; `maxActivePerSide 1` held — the
  Hegemony's second blockade waited for Tyle's end and the 30d cadence, the League launched once.
- **Q54 PASS.** All three targets whitelisted. The Jun 30 feasibility scan listed Isirah and
  Westernesse only: **Tyle was excluded ten days after its blockade ended**, so the 90d end-start
  `$exiles_hl_op_cooldown` is observed working.
- **Q57 PASS (the measurement).** Tier lines printed for all three. Samarra came in at the HALF
  tier against a 507 defence; the two ~100-defence League systems at FULL. Nothing to retune yet —
  three data points, all inside the intended band.
- **Q58 PASS ×3, half-exercised.** Every completion produced `+8.0: blockade completed against
  <victim>` and `perCompletedOp` tension. No blockade was broken up, so the `abortFraction` /
  `did not hold` path is still unexercised.
- **Q61 PARTIAL.** Westernesse survived save → quit → reload mid-TRAVEL, then held and completed.
  A reload with the gates actually held was not exercised.
- **Q62 PASS.** Westernesse launched in ESCALATION, the state was left (pin moved to RETALIATION
  on Jul 6) and the blockade ran to its own end on Nov 11.
- **Q55, Q56, Q56a, Q56b, Q59, Q60, Q63 — NOT LOG-OBSERVABLE.** Gate coverage, fleet names,
  the `Blockaded`/collateral split and its tooltips, bounty payout and station safety need
  Sean's on-screen read. Nothing logs for the collateral condition at all; if Q56 is to be read
  from a log, the apply/remove loop needs a line.
- **Timing observation (instrument before tuning).** Prep + travel ran 30–32 days on all three,
  against the §14.9.5 expectation of 10–20. HOLDING → completion ran 55d for a 36d payload
  (Samarra), 67d for 56d (Tyle) and **102d for 56d (Westernesse)**. Completion is stamped in
  `notifyEnding()`, i.e. at the END of the RETURN leg, so the weariness/tension credit lands
  weeks after the gates reopen and the log cannot separate hold length from return. Recommend a
  `gates released` line at payload end before anyone touches `payloadDays`. Whether the credit
  should land at payload end instead of return end is a design question for Sean.
- **Pin artefacts, expected, recorded so nobody chases them:** ESCALATION #1, #2 and #3 all fell
  inside cycle 2 (the pin re-enters ESCALATION 200 ms after POLITICAL INTERVENTION), so the
  escalation counter hit 3 and posted a General Engagement intel with a 75d window purely from
  the pin; the save's "escalations so far 3" is polluted. The ceasefire intel convened at the
  first exit never got an outcome (state re-forced the same tick) — its backstop timer ends it,
  no orphan. Relief and strikes ran alongside: Laicaille Habitat took a tier-3 Hegemony strike
  (113d), and its League relief convoy delivered 42d of 42d in session 2 — second clean relief
  loop.
- **Observation to chase on the next organic run:** the League's Ancyra strike arrived at 707 vs
  72 and still logged `STRIKE FAILED after 36d of 45d` — not landed, not aborted, nine days short
  of its window. Unexplained; the group may have been destroyed or dispersed by Nex response
  fleets. Not blockade-related, noted only.

**Session 2 — the squeeze half (Q83–Q88): NULL RESULT.** `forceState: 3` +
`forceIncidentOffender: "hegemony"` confirmed on disk. Twelve cycles ran RETALIATION → CONTAINED and
every entry logged `RETALIATION skipped: no incident parties this cycle (victim=null,
offender=null)`. **Cause, in the code, not the game:** `forceIncidentOffender` is consumed only
inside `fireIncident()`, which runs in INCIDENT — and the pin jumps TENSION straight to RETALIATION,
so INCIDENT never runs and the parties stay null. The §14.11.8 recipe was wrong. **Proposed fix
(testing aid only, ~10 lines):** when the director enters RETALIATION under the pin with null
parties, seed offender/victim from `forceIncidentOffender` (roll if blank) and log that it did.
Then re-run Run 3. The twelve idle cycles cost nothing: tension 23 → 29 from the three completed
ops, no exceptions, and the interdiction cadence continued normally (Naraka, Corvus).

**Next:** (1) build the pin seed, Janino-check, re-run the squeeze; (2) Sean's on-screen read of
Q55/Q56/Q59/Q60 on any blockade; (3) decide the completion-credit timing and add the
`gates released` line; (4) push only after both halves are clean and Sean says so.

**Sean's on-screen read (2026-09-21, later the same evening).** Mostly at x5, slowing to watch
manoeuvres; he laid eyes on ISIRAH, not Tyle. The director never blockaded Isirah (eligible, Tyle
was rolled), so what he watched was the Hegemony SURGICAL STRIKE group: arrived May 8 (1131 vs
301), milled in-system 38 days behind the per-fleet `canBeat` gate, landed Jun 20 on Laicaille
Habitat (militarybase, heavybatteries, mining 113d, unrest +2), held orbit 3d. That is the "one
Hegemony fleet moving between jump points". No condition named blockade could have been on
Laicaille: the two Exiles conditions with that word never touched Isirah, vanilla `Blockaded` never
stamped it, and neither save holds a Nexerelin blockade. What Laicaille did carry in sequence:
disrupted industries + Recent Unrest, then Relief Effort (Pass 6), then Black Fleet Price Spikes
in session 2. Kapteyn Starworks (pirates, Exiles battlestation) rightly had nothing — and it is
exactly the bystander Q56 needs, so Isirah is THE system to watch for the collateral split; Tyle has
no bystander at all (Cibola + Madeira are both League). **Net: Q55, Q56, Q59, Q60 remain
unobserved — no blockade was watched.** Bystanders exist in Isirah (Kapteyn), Westernesse (Ailmar)
and Samarra (Orthrus); Tyle and Naraka have none.

**Ancyra resolved.** Sean watched the League strike group get cut to pieces by two Hegemony task
forces, at least one a Nex creation, which is the `STRIKE FAILED after 36d of 45d` line. Correct
outcome; belongs to the known "Nex response fleets" observation class, no change.

#### 14.12.1 Build: the pinned-RETALIATION seed + the GATES RELEASED line [BUILT 2026-09-21; Janino-clean 2/2; UNTESTED]

Both items from the Run 3 read, built by Sean's instruction the same evening.

1. **`ExileHegLeagueDirector.seedPinnedRetaliationParties()`** — `beginState(RETALIATION)` with
   null parties and `testing.forceState == 3` now seeds offender/victim from
   `testing.forceIncidentOffender` (or the ratified coin when blank) BEFORE the dwell roll, which
   reads the victim's lag. No incident type, no tension gain, no telegraph intel — the pin tests the
   punitive op, not the table. A flavour site is picked so the log reads like a real cycle. Fires
   once per pinned cycle because `closeCycle()` clears the parties. Log:
   `Exiles Heg-League TEST: RETALIATION pinned with no incident -- seeded offender=… victim=… from …`.
   Unreachable in production: the pin is the only way into RETALIATION without an incident.
2. **`ExileHegLeagueBlockade.notifyActionFinished()`** — the tick the payload action leaves the
   queue (window out, aborted, broken up, or last fleet lost) prints
   `Exiles Heg-League blockade GATES RELEASED <system>: held Nd of Md | reachedPayload aborted damage
   (abort at f) | fleets alive | actor victim`. Hold days come from a `payloadStartTs` stamped on the
   HOLDING line (a `Long`, so an older save reads null and prints `?`). The gap from this line to
   the outcome line is the return leg. The config comment on `forceIncidentOffender` was updated.

**Run 3 recipe, corrected:** `forceState: 3`, `forceIncidentOffender: "hegemony"` (both already on
disk), continue the Shana save. Expect the seed line at the first RETALIATION entry, then a
`RETALIATION roll` and either a squeeze (Q83–Q88) or a strike with the fallback line. Q87's
`did not hold` branch and Q58's blockade twin remain unexercised until something breaks an op up.

#### 14.12.2 Build: Blockade Spillover apply/remove log lines [BUILT 2026-09-22; Janino-clean 2/2; UNTESTED]

`ExileHegLeagueBlockadeCollateral` now logs `Blockade Spillover APPLIED to <market> (<faction>)
penalty=<p>` on the add path only (never the per-tick refresh) and `… REMOVED from <market>` when a
present condition is actually removed. One line per bystander per blockade each way, so Q56's
collateral half can be read from the log: which non-victim markets were stamped, at what tier-scaled
penalty, and that every stamp came off at the end. The vanilla `Blockaded` half still needs eyes.

#### 14.12.3 Run 4 read [2026-09-22; forced SQUEEZE run (`forceState: 3` + offender hegemony) — seed WORKS, squeeze half exercised; one launch-guard gap; one MOD-WIDE condition bug found from Sean's "odd items"]

Shana save continued, Feb 21 → Aug 10 c208, mostly x5. Zero Exiles exceptions. **The pin seed fired
on every one of sixteen cycles** (`TEST: RETALIATION pinned with no incident -- seeded offender=hegemony
victim=persean`) and the dwell rolled the League's 10d lag, so the recipe is fixed. Sixteen
RETALIATIONs → seven launches: six squeezes and one strike (Madeira → Eventide, arrived 851 vs 888,
outcome pending). Nine cycles found no legal target and passed (fallback line printed each time),
which is the whitelist plus live-op and cooldown guards doing their job under a pin that fires a
cycle every ~12 days.

| Squeeze (League → Hegemony) | Launch FP / defence | Condition | Outcome |
|---|---|---|---|
| Mazalot → Citadel Arcadia | 356 / 83 | applied day 5 | did NOT hold (FP abort) |
| Kazeron → Raesvelg, Valhalla | 326 / 326 | applied | HELD, +6 Heg, tension +2 |
| Suddene → Yama, Naraka | 317 / 64 | applied | HELD, +6 Heg, tension +2 |
| Laicaille → Ancyra, Galatia (1) | 291 / 265 (station 120) | applied | HELD, +6 Heg, tension +2 |
| Salamanca → Jangala, Corvus | 387 / 226 | applied | did NOT hold, days after arrival |
| Fikenhild → Ancyra, Galatia (2) | 333 / 168 | applied | did NOT hold |
| Laicaille → Yama, Naraka (2) | 325 / 19 | — | pending at session end |

- **Q83 PASS with one gap.** Every anchor whitelisted, offender-owned, no player market. BUT the
  second Galatia squeeze launched 583 ms before the first's `notifyEnded()`: the first was in its
  `endAfterDelay()` window, which `hasLiveOpIn()` and `ExileLiveOps` deliberately skip, and the
  system cooldown is stamped only in `notifyEnded()`. **Fix: stamp the cooldown in `notifyEnding()`
  too** (idempotent, the blockade already does this).
- **Q84 half-read.** Sean watched the Arcadia cordon and the Jangala and Ancyra attacks on screen —
  the "suspected Nex attack" on Jangala WAS this director's squeeze, as was the Oct Ancyra attack.
  Three of six squeezes were driven off by the FP abort; the stage prints no arrival measurement, so
  launch-time defence cannot be compared with what the cordon met. **Add the strike stage's ARRIVED
  line to the interdiction stage** (shared by squeezes) before anyone touches the gate.
- **Q85 PASS.** Planetary Blockade applied on day 5 to the anchor only, lifted the tick the cordon
  withdrew, on every op. Sean confirmed the tooltip on Citadel Arcadia and Ancyra.
- **Q86 PASS.** No GUARD FAILURE line all session.
- **Q87 PASS both branches.** `+6.0: squeeze completed` ×3 with `perCompletedOp`; `+8.0: squeeze by
  League did not hold` ×3 on the actor.
- **Q88 not exercised** (Sean did not fight).
- **Salvage (Run 1 of the retuned launch screen):** five dispatches (Corvus ×3, Galatia ×2), all
  delivered; one deferral — Arcadia, by pirate Raiders, `class=mobile … distToWreck=19681`, which is
  the raid rule working. No Isirah visit, so the local-defense rule itself is still unobserved.
- Blockade lines (GATES RELEASED, Spillover) not exercised — no blockade under a RETALIATION pin.

**THE MOD-WIDE BUG, from Sean's two "odd items"** (Citadel Arcadia showing a −2 availability hit
"due to Corsair Activity", Ancyra a −2 Heavy Machinery hit "due to Wartime Interdiction", neither
condition present). The save holds **41 orphaned stat modifiers** from three Exiles conditions
(Corsair Activity 25, Wartime Interdiction 11, Crusade Strike 5) whose per-instance ids match no
live condition; every remaining Exiles modifier is backed by a live condition. Decompiling
`MarketCondition` settled the mechanism: `writeReplace()` **strips the plugin on save when
`plugin.isTransient()`**, and `readResolve()` builds a fresh instance on load. Vanilla's
`BaseMarketConditionPlugin.isTransient()` returns **true**. Only `ExileEmergencyMeasures` overrides
it. So for every condition on `ExileTimedCondition` (twelve classes: Corsair Activity, Price Spikes,
Crusade Harassment/Strike, HL Interdiction/Planetary Blockade/Spillover/Relief Effort, Refugee
Encampments, Relief Shipment, Sacked, and the shortage base) a save/load **resets `elapsed` to zero
and forgets `hitCommodityIds`**: the reload's `unapply()` removes nothing, `apply()` re-picks the
commodity set, and every pre-load modifier whose commodity is not re-picked is orphaned for the life
of the save. The Naraka interdiction proves it: applied in session A (log line 120844), lifted in
session B after a relaunch (157471), five orphans on Yami. The accessibility mod from the same
instance was NOT orphaned, because it needs no remembered state — exactly the signature.

**Proposed fix (three parts, all technical):** (1) `ExileTimedCondition.isTransient()` → false, so
state persists like Emergency Measures'; (2) `ExileCommodityShortageCondition.unapply()` sweeps
every commodity's availability stat for its own id when the list is null — belt and braces;
(3) a load-time heal in `ExilesModPlugin.onGameLoad()` that removes any `exile*_<uid>` modifier on
availability, accessibility or player-price stats whose uid matches no live condition on that
market, logging the count. Existing saves lose nothing (their plugins were already empty) and gain
correct timers going forward.

#### 14.12.4 Build: cooldown at `notifyEnding()` + interdiction/squeeze ARRIVED line [BUILT 2026-09-22; Janino-clean 5/5; UNTESTED]

1. `ExileHegLeagueRaidIntel` now stamps `$exiles_hl_op_cooldown` from `notifyEnding()` as well as
   `notifyEnded()` (one idempotent helper), closing the `endAfterDelay()` window in which the second
   Galatia squeeze launched. Interdictions, strikes and squeezes all inherit it.
2. `ExileHegLeagueInterdictionStage.updateRoutes()` prints `Exiles Heg-League <interdiction|squeeze>
   ARRIVED at <system>: group strength S vs defense D (enemy=E + station=T) | presence window Nd` once,
   from the same one-shot hook the strike stage uses. The squeeze overrides the station term with its
   anchor's `WarSimScript.getStationStrength`, interdictions report zero. Read it against the launch
   line before touching `feasibilityMult` or the abort fraction. Sean's on-screen note: one cordon met
   a Nexerelin special task group ("6th Raesvelg"); Nex special forces respond to raids in their
   faction's space, so arrival defence can legitimately exceed launch defence by a task group.

#### 14.12.5 Build: the transient-plugin fix [BUILT 2026-09-22; Janino-clean 16/16; UNTESTED — needs a save/reload test]

Three parts, as planned and approved by Sean:

1. **`ExileTimedCondition.isTransient()` → false.** The engine now serialises every Exiles timed
   condition's plugin with its fields (timer, hit list, Sacked's looted goods), exactly as Emergency
   Measures already did. Per live condition the cost is a float and a few strings.
2. **`ExileCommodityShortageCondition.unapply()` is stateless.** It walks the market's commodity
   list and removes its own instance id from every availability stat — ~30 no-op hash removals where
   the id was never applied — instead of trusting the remembered list. The list stays for the
   tooltip and for holding the pick stable across reapplies.
3. **`ExileConditionModifierHeal.run()`**, called from `onGameLoad()` after the load-safe fixes.
   Per economy market it collects the live condition instance ids, then strips any modifier on
   commodity availability, market accessibility or the player supply price whose source is
   `<exile…conditionId>_<uid>` with a registered spec and no live instance. One line per removal and
   one summary line, silent when nothing is found; per-market try/catch; runs every load.
   Accessibility and player price are `StatBonus`, availability is `MutableStat` — different map and
   remover names, same semantics, two helpers.

**Test (new standing rule for every op test, Sean 2026-09-22):** save, quit and reload while a
condition is live, then confirm it lifts cleanly. For this fix specifically: load Shana → expect
`Exiles condition heal: removed 41 orphaned modifier(s)` (Corsair Activity 25, Wartime Interdiction
11, Crusade Strike 5) and the Ancyra/Citadel Arcadia tooltips clean; then let any interdiction or
squeeze land its condition, save, reload, let it lift, and confirm no modifier remains and the next
load's heal is silent.

#### 14.12.6 Run 5 read [2026-09-23; four sessions with reloads — transient-plugin fix CONFIRMED, blockade lines CONFIRMED, Q61 both halves PASS]

Shana save, Aug c208 → Apr c209, four launches (three save/quit/reload stops, the new standing
rule). Zero Exiles exceptions. The log rotated mid-evening; this read spans `.1` and the live file.

- **Heal (§14.12.5) PASS.** First load: `removed 41 orphaned modifier(s) on 9 market(s)` — Corsair
  Activity 25, Wartime Interdiction 11, Crusade Strike 5, exactly the save analysis. Three later loads:
  silent.
- **Persistence PASS.** League squeeze Madeira → Nachiketa: `ARRIVED 597 vs 176 (enemy 56 + station
  120)`, condition applied Sep 11 (session 1), Sean saved/quit Oct 1, cordon `withdrew (SUCCESS)` Oct 11
  in session 2, `condition lifted from 1 market(s)`, outcome HELD Oct 14, and no orphan surfaced on
  the two loads after. Sean's notes match to the day (bounty ends Oct 13, no condition Oct 16, "raid
  over" Oct 19 = return leg). His "Yama has no Planetary Blockade" was correct: the anchor was
  Nachiketa; Yama's "Raiding" is Nexerelin's `nex_raid_condition`.
- **Blockade half under `forceState: 4`:** Coatl → Isirah (Hegemony) launched Nov 10, HOLDING full
  Nov 29, **`GATES RELEASED held 56d of 56d, fleets alive 3, damage none`** Jan 24 — the payload
  window ran exactly to the day. Madeira → Samarra (League) `held 35d of 36d`. Chicomoztoc → Tyle
  launched Apr 6 with `mult x1.3, estimate 900 vs defence 1085` (gate 1125 passes; expect tier −1
  and no effect — by design). Two `blockade completed` weariness lines.
- **Q56 collateral PASS from the log.** `Blockade Spillover APPLIED to Kapteyn Starworks (pirates)
  penalty=0.3` on the HOLDING tick; `APPLIED to Orthrus (independent) 0.3`, later `REMOVED` then
  re-`APPLIED … 0.15` as the Samarra tier dropped to half — the live tier-following works.
  **Open:** Kapteyn's spillover was REMOVED Dec 8, seven weeks before the gates opened, and never
  re-applied. Either the tier fell to −1 (League defenders plus Nex response outgrew the three
  fleets — Sean saw them fighting League fleets at the sensor array) or a guard turned false. The
  tick logs neither. **Add a once-per-change tier line** before reading anything into it.
- **Q61 PASS, both halves.** Sean reloaded Jan 17 with the Isirah gates held; Laicaille stayed
  Blockaded, the blockade completed on schedule, the condition came off.
- **Q55 observed, soft.** No fleets parked on the jump points when Sean arrived; they were fighting
  League fleets near the sensor array, one heading for a gate. Equal-weight response scripts pull
  fleets toward hostiles by design, so "every gate held" is the resting state, not a guarantee. For
  Sean: acceptable as is, or should pickets be pinned to gates and only the anchor respond?
- **Sean's Apr 4 "Persean blockade of Galatia" was Nexerelin's own blockade** (a `NexBlockadeFGI`
  is in the save, ended); Galatia is not on our whitelist. Deinkuyu's Shipping Disruption is Nex's.
- **Cooldown at `notifyEnding()` (§14.12.4):** no back-to-back launch into one system all night.
- **Relief loop:** three convoys delivered in full (Eventide, Citadel Arcadia, Yama); Ragnar's was
  lost in transit (`0.0d of 57.3d, convoy lost`) — the designed failure branch, first time seen.
- **Pin artefacts:** ESCALATION #4–#7 in one cycle, a General Engagement at #6, tension 35 → 57,
  Hegemony weariness 68. Expected under a pin; do not read the meter from this save.
- **Salvage:** nine dispatches, all delivered; one deferral by a roaming pirate Corsair
  (`class=mobile`, 7,339 units out, Naraka has no pirate market — correct).

#### 14.12.7 Build: blockade tier-change + collateral-drop reason lines [BUILT 2026-09-23; Janino-clean; UNTESTED]

`ExileHegLeagueBlockade.updateCollateral()` now prints `blockade tier N (none|half|full) in <system>
| collateral penalty now p | fleets alive` once per tier change while holding, and `blockade
collateral dropping <market>: <reason>` (tier too weak / not holding / victim market / guard) whenever
it strips a market that currently carries Blockade Spillover. The Kapteyn question from §14.12.6 reads
straight off these two lines next time.

### 14.13 PROPOSAL — The Relief Battle: a climax beat for squeezes and blockades [ASSISTANT-ORIGINATED, 2026-09-24; awaiting Sean's ruling; nothing built]

**The problem it answers (Sean, 2026-09-23):** the Heg-League squeeze and system blockade are sieges
in a game whose drama engine is the battle. They arrive, sit on a plateau whose only effect is a
tooltip percentage, and leave. The fights Sean enjoyed in Naraka happened only because a Nexerelin
special task group chose to defend; without Nex the same op is an auto-win, and with Nex it is tuned
against a 150-FP fleet Exiles does not control. The op has no middle beat, no verb, and no place for
the player to be.

**The idea in one line:** once a cordon has held past a threshold, the victim faction launches a
named RELIEF FORCE from its nearest military market, sized from the cordon's measured strength, with a
countdown in the intel and a telegraph message. The op then resolves in a battle everyone can see
coming, in vanilla and in Nex alike.

#### 14.13.1 Trigger

- Applies to the planetary squeeze (`ExileHegLeagueSqueezeStage`) and the system blockade
  (`ExileHegLeagueBlockade`). Interdictions and surgical strikes are excluded: they are hit-and-run by
  design and already have a climax (the strike landing).
- Fires ONCE per op, on the tick the op's condition has been live for `relief.triggerDays`
  (proposed 8 for a squeeze, 12 for a blockade). An op driven off before that never draws one.
- Guards, all existing: not if the victim faction has no eligible military market within
  `relief.maxSourceLY`; not during a ceasefire (POLITICAL INTERVENTION / DE-ESCALATION); not if the
  victim's side already has `relief.maxActive` relief forces in flight (proposed 1). The Battered /
  Emergency-Measures guard does not apply: the relief force defends, it does not pressure.

#### 14.13.2 Sizing — from the arrival line, never from the launch estimate

- Strength target = cordon's live in-system strength (`WarSimScript.getFactionStrength`, the same
  read the ARRIVED line prints) x `relief.strengthMult` (proposed 1.1), clamped to
  `[relief.minFp, relief.maxFp]` (proposed 120–400).
- Ship-size cap `relief.maxShipSize` = 4 (capitals allowed). A relief force led by an Onslaught or a
  Conquest against a cruiser cordon is the intended picture: the Sector's largest militaries answering
  in kind.
- Built through `FleetFactoryV3` with the victim's doctrine, `FleetTypes.TASK_FORCE`, one named
  commander, transponder on, faction prefix suppressed, name from a small per-side pool ("Relief
  Force <anchor>", "<Nth> Relief Squadron") — placeholder prose, R12.

#### 14.13.3 Behaviour

1. **Muster** at the source market for `relief.musterDays` (proposed 4). Intel beat 1: "<Faction>
   musters a relief force at <source>." Campaign message to the player if within
   `relief.telegraphRangeLY`.
2. **Travel** as an ordinary fleet with a `GO_TO_LOCATION` on the anchor / primary gate.
3. **Engage**: on arrival, `MilitaryResponseScript`-style aggression against the cordon's fleets only
   (RAIDER_TAG), never trade or patrols. `MEMORY_KEY_MAKE_HOSTILE` stays false (the 2026-08-06 lesson).
4. **Resolve**: the op's existing outcome path decides. If the cordon's damage crosses its abort
   fraction the op fails ("driven off"); if the relief force falls below `relief.retreatFraction`
   (proposed 0.4) it withdraws to its source and the cordon completes. Either way, intel beat 2 names
   the result: "The relief of <anchor> succeeded / was broken."
5. **Retire**: survivors return to the source market and despawn through the normal route end.
   No lingering fleets, no orphaned scripts: the relief force is a stage of the OP's intel, not a
   new intel, so it ends when the op ends.

#### 14.13.4 Outcomes and the meter

- Relief succeeds: the op reports "did not hold" exactly as today (actor +8 weariness, no tension).
  Add `relief.victimWearinessOnSuccess` (proposed +2): a victory still costs ships.
- Relief broken: the op completes as today; add `relief.victimWearinessOnFailure` (proposed +4) on top
  of the completion figure, because a fleet was lost.
- Player fought for the cordon: the existing per-kill bounty on the victim's side stands.
  Player fought for the relief: NEW `relief.playerBounty` paid by the victim per cordon hull destroyed,
  through `ExileHegLeagueBattleRewards`, no payout when the player fought both sides. This is the
  player's lever (Phase-1 priority 3: readable choices, useful counterplay).

#### 14.13.5 Nexerelin interaction

- Nex special task groups already defend against raid-type ops (route AI, verified 2026-09-23) and
  never against blockades (their system lookup misses fleet-group events). The relief force is
  ADDITIVE: with Nex the two may stack on a squeeze; without Nex the relief force stands alone.
  Sizing from the cordon's live strength, not from a fixed number, is what keeps both cases fair.
- No Nex class is touched. If stacking proves too strong in play, `relief.strengthMult` is the dial.

#### 14.13.6 Cost

- One fleet per triggered op, at most `relief.maxActive` per side, alive for ~20–40 days.
- Trigger check rides the op's existing periodic tick (0.1–0.3 day); no new EveryFrameScript.
- State: a few fields on the op's intel (fired flag, source market id, fleet id). Nothing retained
  after the op ends.

#### 14.13.7 Config block (proposed, `director_config.json` → hegemonyLeague)

    "relief": {
        "enabled": true,
        "triggerDaysSqueeze": 8,      "triggerDaysBlockade": 12,
        "maxActivePerSide": 1,        "maxSourceLY": 12,
        "strengthMult": 1.1,          "minFp": 120,    "maxFp": 400,
        "maxShipSize": 4,             "musterDays": 4,
        "retreatFraction": 0.4,
        "victimWearinessOnSuccess": 2, "victimWearinessOnFailure": 4,
        "playerBountyPerFrigate": 3000, "telegraphRangeLY": 6,
    }

#### 14.13.8 Test matrix (first pass)

| # | Check | Expect |
|---|---|---|
| R1 | Trigger | `RELIEF MUSTERING` line on the trigger day; none for an op driven off earlier; none during a ceasefire |
| R2 | Sizing | `RELIEF LAUNCHED … target=<cordon str> x mult → fp` inside the clamp; capital present when fp allows |
| R3 | Beats | intel updates at muster and at resolution; campaign message within telegraph range |
| R4 | Engagement | relief fleet fights cordon fleets only; ignores trade and patrols; never hunts the player |
| R5 | Outcome | one of the two branches logged; weariness deltas as configured; op outcome path unchanged |
| R6 | Cleanup | survivors return and despawn; no script or fleet left after the op ends; save/reload mid-relief holds |
| R7 | Vanilla vs Nex | same op in both: relief alone in vanilla; relief + task group in Nex; neither an auto-win |
| R8 | Bounty | player kills on either side pay the right faction; no double payout |

#### 14.13.9 Decisions for Sean

- D1: squeezes only first, blockades second? (Recommended: squeezes first — one stage, one intel.)
- D2: trigger by days held, or by the cordon beating the station? (Recommended: days; the station
  fight then becomes the relief force's cue rather than a second trigger.)
- D3: does a relief victory also lift the condition immediately, or does it lift on the cordon's
  withdrawal tick as today? (Recommended: as today; the cordon leaving IS the lift.)
- D4: name pools and the telegraph prose: author material, placeholder until then.
- D5: sequencing: the vanilla-only control run first, so the sizing constants are not Nex's.

### 14.14 PROPOSAL — Launch gates compare fleet points against effective strength (a units error) [ASSISTANT-ORIGINATED, 2026-09-26; awaiting Sean's ruling; NOTHING BUILT]

> Proposal only. Found in the 2026-09-26 log read (no-Nex `forceState: 4` run, Mar 2 → Jun 5 c206)
> and confirmed in code. Sean agreed on 2026-09-26 that this is apples to oranges. No fix is ratified yet.

#### 14.14.1 The finding

Every Exiles **launch** feasibility gate has this form:

```
defense = WarSimScript.getEnemyStrength(actor, system) [+ getStationStrength(...)]
gate    = fp * feasibilityMult          // fp = the op's raw fleet-point budget
reject if defense > gate
```

`getEnemyStrength` sums `CampaignFleetAPI.getEffectiveStrength()` over live fleets (per-member
strength scaled by quality, s-mods and officer level). For abstract routes it sums
`OptionalFleetData.strength`, which vanilla sets to `Misc.getAdjustedStrength(fp, sourceMarket)`
(`AssembleStage.java:204`). That function is FP × `(0.5 + ship quality)` × the source market's
combat-fleet-size multiplier × `(1 + (doctrine officerQuality − 1)/4)`. **Vanilla compares strength
with strength everywhere. The Exiles gates compare FP with strength.**

**Measured factor (Heg–League, arrival lines, both factions):**

| Op | Launch FP | Arrival group strength | Ratio |
|---|---|---|---|
| Heg interdiction → Zagan | 165 | 557 | 3.4 |
| Heg strike → Olinadu | 276 | 1002 | 3.6 |
| League strike → Nachiketa | 404 | 1358 | 3.4 |

The factor is **not a constant**: it follows the source market's quality and fleet size and the
faction's doctrine. That is why the fix should call vanilla's function, not hard-code 3.4. It has
only been measured for Heg–League sources. Black Fleet, Ashen and Omicron sources will differ.

**What it means in practice.** A gate passes when real strength ÷ defence ≥ k ÷ mult (k ≈ 3.4 here):

| Heg–League op | `feasibilityMult` | Advantage actually required |
|---|---|---|
| Strike | 1.0 | ≈ 3.4 : 1 |
| Squeeze (planetary) | 1.0 | ≈ 3.4 : 1 |
| Interdiction | 2.0 | ≈ 1.7 : 1 |
| System blockade | 1.25 | 0.8 : 1 (**already consistent**: its estimate is strength via `FleetGroupIntel.getApproximateStrengthForTotalDifficultyPoints`) |

This explains the no-Nex picture (strikes arriving at 1358 vs 296 and 1002 vs 13; every op wins
comfortably) and part of the Nex picture. The gates only admit fights the op is certain to win
against *vanilla* defenders, and Nex task groups (register R7) are invisible to them.

**It is a units error, not a crash bug.** Nothing breaks. The gates are just stricter than their
numbers say, by a hidden, source-dependent factor. The log lines inherit the mislabel ("gate 331 =
165 FP x 2.0" compares against a strength of 171).

#### 14.14.2 Scope — every launch gate in the mod

| Director | Gate | Code | Source known at gate? |
|---|---|---|---|
| Heg–League | interdiction | `ExileHegLeagueInterdictionManager.java:482-484` | yes (`:331` before `:338`) |
| Heg–League | strike | `ExileHegLeagueStrikeManager.java:601` | yes (`:394` before `:399`) |
| Heg–League | squeeze | `ExileHegLeagueSqueezeManager.java:311` | yes (`:131` before `:136`) |
| Heg–League | system blockade | `ExileHegLeagueBlockadeManager` | **consistent already** |
| Ashen | interdiction | `AshenInterdictionManager.java:546-548` | yes (`:472` before `:478`) |
| Black Fleet | commerce raid / assault (system pick) | `BlackFleetRaidManager.java:398-405` | yes (`:189` before `:195`) |
| Black Fleet | assault victim pick (grudge-relaxed) | `BlackFleetRaidManager.java:276-282` | yes |
| Omicron | incursion/strike choice | `OmicronDirector.java:1101-1110` | yes, via the nexus **proxy market**; `OmicronAssembleStage.java:79` already calls `Misc.getAdjustedStrength(fp, market)` for its routes |

**Not affected:** every *in-stage* check compares strength with strength (`AshenRehearsalStage`
`:131/:194`, `AshenCulannStrikeStage:140`, `BlackFleetAssaultStage:170/:226`, the Heg–League strike
`canBeat`, vanilla `PirateRaidActionStage.updateRoutes`). Not checked: the Ashen blockade's gate.

**Ruling constraint.** The Black Fleet, Ashen and Omicron gates are playtest-earned rulings (the
architecture ruling in CLAUDE.md §6: gate differences are deliberate). Their current feel was tuned
on the mismatched numbers. Any change to them needs its own ruling, per director.

#### 14.14.3 Proposal — two steps, so behaviour and design change separately

**Step 1a — instrument only (zero behaviour change).** Each feasibility line also prints
`adjusted=` `Misc.getAdjustedStrength(fp, source)` and the ratio `k`. One run of each director then
gives the real per-source factor instead of the Heg–League sample. (Instrument before tuning.)

**Step 1b — behaviour-neutral conversion.** The gate becomes
`defense > Misc.getAdjustedStrength(fp, source) × mult'`, with `mult' = mult ÷ k_measured`, so
today's effective odds are kept, but in correct, per-source units. Log lines print strength against
strength. Heg–League first. The other three only on Sean's say.

**Step 2 — choose the odds on purpose (design decision).** Options for Heg–League, as starting points:

| Op | Today (effective) | Candidate |
|---|---|---|
| Strike | ≈ 3.4 : 1 | 1.5–2 : 1 (would admit Corvus/Valhalla-class targets rejected in the 09-26 run) |
| Squeeze | ≈ 3.4 : 1 | 1.5 : 1 (it has to beat the station) |
| Interdiction | ≈ 1.7 : 1 | 1 : 1 or looser (it avoids stations and heavy fights by design) |

Honest odds against *vanilla* defenders still ignore Nex task groups. Pair Step 2 with a decision on
register R7 (withdraw on task-group arrival, or a Nex-aware defence allowance). Otherwise looser
gates will simply feed more ops to task groups under Nex.

#### 14.14.4 Config handling

Changing what an existing `feasibilityMult` *means* changes a player-facing setting. Under the
CLAUDE.md §7 hard rule, that needs Sean's approval. Options: (a) keep the key and change its meaning,
with a migration note in its comment; (b) add a new key (e.g. `feasibilityStrengthMult`) and retire
the old one on Sean's approval; (c) add `fpToStrength: "vanilla"` alongside the old key as a
switch, so the old behaviour stays available. **Recommendation: (c) for Step 1b** (reversible and
comparable in one run), then (a) or (b) once the new odds are ruled.

#### 14.14.5 Test matrix (first pass)

| # | Check | Pass condition |
|---|---|---|
| U1 | 1a lines, all four directors | every feasibility line prints `fp`, `adjusted`, `k`; no behaviour change (launch counts match a control run in distribution) |
| U2 | 1b neutral | same accept/reject decisions as 1a on the same log inputs, within the per-source variation of `k` |
| U3 | Omicron proxy | `getAdjustedStrength` on the nexus proxy market returns a sane value (not the 0.25 quality floor) |
| U4 | Step 2, no Nex | strikes land at some rate below 100%; some fail for the right reason (arrival loses) |
| U5 | Step 2, Nex | combined with the R7 ruling: no increase in ops wiped by task groups without an intel beat |

#### 14.14.6 Decisions for Sean

- U-D1: approve Step 1a (instrumentation, all four directors)? (Recommended: yes. Zero risk.)
- U-D2: Step 1b for Heg–League only, or all four at once? (Recommended: Heg–League only, the others
  after their 1a numbers are read.)
- U-D3: Step 2 target odds per op (table above).
- U-D4: config handling (§14.14.4). (Recommended: (c) first.)
- U-D5: sequence relative to R7 (Nex task groups). (Recommended: rule R7 in the same sitting as U-D3.)

#### 14.14.7 Build: Step 1a units measurement [RULED U-D1 by Sean 2026-09-26: "Go ahead with Step 1a for all four directors"; BUILT same day; Janino-clean 6/6; UNTESTED IN GAME]

**Logging only. Every gate is byte-for-byte unchanged**: the diff is 115 added lines, zero removed.
Each site calls vanilla's own `Misc.getAdjustedStrength(fp, source)` (no Exiles formula, so no drift
from vanilla). It prints one line per launch evaluation, because `k` depends only on the source and
the budget, not on the candidate. Per ruling S4, the line is written inline at each site, not in a
shared helper.

| Site | Log prefix |
|---|---|
| `ExileHegLeagueInterdictionManager.pickTarget` | `Exiles units [Heg-League interdiction]` |
| `ExileHegLeagueStrikeManager.pickStrikeTarget` | `Exiles units [Heg-League strike]` |
| `ExileHegLeagueSqueezeManager.pickAnchor` | `Exiles units [Heg-League squeeze]` |
| `AshenInterdictionManager.pickTarget` | `Exiles units [Ashen interdiction]` |
| `BlackFleetRaidManager.pickTarget` (also covers `pickVictim`, same fp) | `Exiles units [Black Fleet commerce raid\|assault]` |
| `OmicronDirector.findLaunchChoices` | `Exiles units [Omicron raid\|incursion]` |

Line format: `source=… fp=… adjustedStrength=… k=… feasibilityMult=… requiredOdds=k/mult:1`.

**Guards:** null source or `fp <= 0` skips the line. **Omicron** reads the node's existing proxy market
and never creates one, so no new saved state appears. Consequence: a node's *first-ever* search
prints nothing, and its later searches do. At most one Omicron line per search, and searches repeat
no more than once a game day. Cost: a few multiplications and three stat reads per launch evaluation.

**Read next run (any setup):** grep `Exiles units [`. For each director, record the spread of `k`
per source. Those numbers become the rescale divisors for Step 1b (§14.14.3). Expect Heg–League near
the arrival-derived 3.4. Omicron's proxy (fleet-size pinned to 1) should be noticeably lower.

#### 14.14.8 Step 1a run read [2026-09-26; today's no-Nex save continued, Jun 5 → Dec 16 c206, `forceState: 4`; 21 units lines, all four directors; zero Exiles errors]

**Verdict: the measurement works. Vanilla's conversion is exactly the number the off-screen fight
uses. And `k` is NOT a near-constant 3.4: it ranges 1.0–4.03 by source and drifts over time.** The
premise of §14.14.1 needs correcting. Today's gates are not uniformly conservative. They are
*inconsistent*: the odds an op actually needs depend on which market happened to launch it.

**Validation: predicted strength against the same op's ARRIVED line.**

| Op (source → target) | `adjustedStrength` at launch | Arrival group strength |
|---|---|---|
| League interdiction Yesod → Arcadia | 330 | 330 |
| League interdiction Athulf → Naraka | 253 | 253 |
| Heg strike Tigra City → Yesod | 941 | 941 |
| Heg strike Citadel Arcadia → Olinadu | 428 | 442 |
| Heg strike Ancyra → Laicaille | 1009 | 534 (outlier: lost strength en route or spawned live) |

Off screen, `WarSimScript` counts the route's stored strength, which vanilla sets with this exact
call. So Step 1b makes the gate reason in the same number the arrival check and autoresolve use.

**Measured `k` by director and source:**

| Director / op | Source: k |
|---|---|
| Heg–League strike | Kazeron 4.03 · Ancyra 3.36 · Tigra City 2.72 (×2) · Cibola 2.09 · Citadel Arcadia 1.29 |
| Heg–League interdiction | Salamanca 2.48 · Yesod 2.28 · Mairaath 2.09 · Athulf 1.86 · Fikenhild 1.57 · Olinadu 1.30 |
| Black Fleet raid/assault | Sallee Anchorage 2.42 (×4, stable) · Lundy Hold 1.14 |
| Ashen interdiction | Silent Watch Outpost 1.76 (one sample) |
| Omicron raid/incursion | proxy 1.00 (×3, exact) |

- **Required odds today, Heg–League:** strikes 1.29–4.03 : 1; interdictions 0.65–1.24 : 1.
- **Drift:** Fikenhild read 3.36 at the 09-26 morning arrival and 1.57 now (market state changes quality and fleet size).
- **The one failed Heg–League op this run is the lowest-k source.** Olinadu → Galatia interdiction:
  gate "120 FP × 2 = 241 vs 151" passed, but its real strength was 156 against 151, and it did not land.
  The units error over-trusts weak sources and under-trusts strong ones.
- **Omicron:** its proxy market makes vanilla value Omicron at raw FP (k = 1.0), at the gate AND in
  `OmicronAssembleStage` route strength, so its gate is already self-consistent (Step 1b neutral).
  **Open question (not measured):** real Omicron hulls with AI-core officers are probably stronger than
  1.0 × FP, so off-screen autoresolve may under-count Omicron. No Omicron ARRIVED line exists to check.
  Cosmetic: the Omicron units line names the first free node with a proxy, which need not be the launching node.

**Other results this run (no Nex):**
- Heg–League strikes: 5 LANDED, 0 failed (2 pending). The no-Nex Olinadu strike LANDED after 32d,
  where the Nex run's (502 vs 2) FAILED. That is consistent with a Nex task group being the cause (register R7), not proven.
- Interdictions: 5 completed, 1 failed (above). Blockades: Westernesse's pending outcome reported
  (League +8), Tyle held 56d of 56d. Relief: 3 convoys delivered in full, 1 in flight.
- Weariness Heg 17 / League 45. Tension 29 → 49 (pinned; do not read the meter).
- Black Fleet 4 raids SUCCESS, assault SUCCESS. Ashen interdiction SUCCESS, rehearsal LANDED.
- Errors: only vanilla's `flare` / `module_hightech_decor` hull-loader noise.

**Consequence for §14.14.3.** A per-op divisor cannot keep today's behaviour per source, because
today's behaviour *is* the per-source scatter. Revised proposal: **fold Step 1b into Step 2.**
Convert with `getAdjustedStrength` and set one multiplier per op, either at the current *median*
(no average change: Heg–League strike median k 2.72 → required odds ≈ 2.7 : 1, `mult' ≈ 0.37`;
interdiction median k ≈ 1.97 → ≈ 1 : 1, `mult' ≈ 1.0`) or at deliberately chosen odds. Either way
the scatter disappears. The ruling needed is the odds (U-D3), still paired with R7.

#### 14.14.9 Build: Option A, Heg–League gates in strength units [RULED by Sean 2026-09-26: "Go ahead with Option A for the Heg-League gates"; BUILT same day; Janino-clean 5/5; UNTESTED IN GAME]

**What changed:**
- The interdiction, strike and squeeze gates now read
  `defense <= Misc.getAdjustedStrength(fp, source) × <op>.feasibilityStrengthMult`, so every source
  market faces the same real odds.
- The per-source scatter measured in §14.14.8 (strikes 1.29–4.03 : 1, interdictions 0.65–1.24 : 1) is gone.
- The **system blockade is untouched**; it was already in strength units.
- Ashen, Black Fleet and Omicron are untouched; they still carry only the Step 1a logging.

**Values: median-neutral, the ruled Option A.**

| Op | Old gate (FP units) | New `feasibilityStrengthMult` | Real odds now required |
|---|---|---|---|
| Interdiction | 2.0 | 1.0 | 1 : 1, every source (old median ≈ 0.99 : 1) |
| Strike | 1.0 | 0.37 | ≈ 2.7 : 1, every source (old median 2.72 : 1) |
| Squeeze | 1.0 | 0.37 | ≈ 2.7 : 1 (strike's value: same gate shape, same old value, same sources) |

**Config (player-facing rule respected: keys ADDED, nothing moved, renamed or deleted):**
- `exilehegleague.feasibilityUnits`: `"strength"` (default) or `"fp"`. `"fp"` is the one-key rollback
  to the legacy gate, which still reads the untouched `feasibilityMult` keys (now commented LEGACY).
- `interdiction` / `surgicalStrike` / `planetaryBlockade`: new `feasibilityStrengthMult` beside each
  `feasibilityMult`.
- The test pins in Sean's working copy (`forceState: 4`, `forceIncidentOffender: "persean"`) were
  deliberately NOT committed.

**Code:**
- `ExileHegLeagueConfig` gains the fields plus three small methods (`activeFeasibilityMult`,
  `feasibilityGate`, `describeGate`). They are shared by this director's own three gates only; S4 is
  about cross-director helpers.
- The strike and squeeze gates now compute the gate once per pass instead of per candidate.
- A null source falls back to the legacy FP gate rather than guessing.
- Log lines print `gate N = strength S (F FP) x M`.
- The units line now prints `mode=` and the odds the ACTIVE gate requires.
- A stale squeeze comment ("station EXCLUDED", predating the 2026-09-20 ruling) is annotated, not rewritten.

**What to expect:**
- Average launch behaviour should be unchanged.
- Strong sources (Kazeron, Ancyra) now accept harder targets than before.
- Weak sources (Olinadu, Citadel Arcadia) stop starting fights they cannot win: the §14.14.8 Galatia failure would now be rejected.

**Test (next run, any setup):**
- Every Heg–League `units` line reads `mode=strength`, with `requiredOdds=1.0:1` (interdiction) or `2.7:1` (strike/squeeze).
- Feasibility lines print the strength form.
- Launch counts are similar to the §14.14.8 run.
- No op launches from a weak source against a defence above its converted strength.

**Sean's observation (2026-09-26), recorded, NOT acted on:** a failed military strike should still
raise tension between the two powers. Today a failed op adds no tension and only raises the actor's
weariness, so a war with riskier odds would cool faster. This is a design question for any future
retune of the tension and weariness numbers (§3.2), not part of Option A.

#### 14.14.10 Option A run read [2026-09-28; same no-Nex save continued, Jan 2 → Oct 15 c207, `forceState: 4` whole run (part B, the squeeze pin, not run); zero Exiles errors; Sean's on-screen notes cross-checked]

**Verdict: Option A works as built. Every Heg–League units line reads `mode=strength` at 1.0 : 1
(interdiction) or 2.7 : 1 (strike). Strong sources reach targets the old gate refused, and weak
sources stay home.** Two defects surfaced, neither caused by Option A (below).

**Option A evidence:**
- **Strong source reaches further.** Raesvelg (k 3.63) → Cibola strike: defence 481, strength gate 515.
  The old FP gate (384) would have **rejected** it. It LANDED.
- **Weak sources restrained.** Passes from Olinadu (k 1.71), Madeira (strike, k 1.24) and Laicaille
  (strike, k 2.02) launched nothing.
- **Outcomes:**
  - Strikes: Yesod, Ancyra, Raesvelg, Cibola LANDED; Laicaille FAILED (defect 1); Olinadu launched Sep 30, pending.
  - Interdictions: Samarra, Arcadia ×2, Naraka ×2, Corvus SUCCESS; Kumari Kandam failed before arriving (defect 2); Zagan ongoing.
  - Blockades: Westernesse ×2 and Isirah/Tyle held their full windows.
  - Relief: 5 convoys, all delivered. Ancyra delivered 34.7 of 47.5 days: "industry already recovered", the designed branch.

**Defect 1: a strike fails if the player is nearby when its window ends (confirms register R3, now a
concrete mechanism).**
- Yama → Laicaille arrived Aug 1 at **1723 vs 281** and ended `STRIKE FAILED after 45d of 45d` (logged Sep 20).
- **No Nex.** So this is the same signature as the Nex run's Olinadu failure (502 vs 2), and it is not Nex-caused.
- The half-day `SALVAGE_DIAG` samples put Sean in Naraka to Sep 10, then Tyle from Sep 20. The Naraka → Tyle hyperspace
  leg passes about 0.5 LY from Isirah (starmap: Isirah −14900,−4400; Tyle −19100,−2700), inside the 1.6 LY
  spawn range, around when the window ran out.
- **Mechanism** (`ExileHegLeagueStrikeStage.updateStatus`):
  - Off screen, a strike can only land at the end of the window, via `autoresolve()` → `canBeat`.
  - If the player is in spawn range at that tick, the branch is `finish(false)`, with **no strength
    check**, and the just-spawned live fleets get no time to strike.
  - The outcome therefore depends on where the player happens to be at one instant.
- **Proposed fix (needs a ruling):** at window end, resolve with `canBeat` in both branches, or extend
  the window while the player is in range and live fleets have not yet had a chance to strike.

**Defect 2 (probable): an interdiction lost before reaching its target.**
- Raesvelg → Kumari Kandam launched May 3 and ended May 12 `WITHOUT completing` ("did not land", Hegemony +8 weariness).
- There is **no ARRIVED line**, so it died during assembly or travel.
- Sean was in Valhalla (the source system) the whole time, so the fleets were live. The League strike
  group that hit Raesvelg on Apr 23 had just left the system.
- Cause not provable from the log. This is the first concrete case for register R5: log which stage an op aborts in.

**Sean's on-screen notes, reconciled with the log:**

| Note | Log / explanation |
|---|---|
| Feb 14: Samarra raid "over"; Feb 28 no evidence | League interdiction held Jan 21 → withdrew (SUCCESS) before the visit. Correct. |
| Feb 28: League owns relay + nav buoy in Galatia; Hegemony owns Westernesse's; Black Fleet owns Corvus's | **Register R1 CONFIRMED on screen**, across three directors. |
| Mar 16: Arcadia, League took the relay; Citadel Arcadia has Wartime Interdiction, Agreus not | Condition applied to exactly 1 Hegemony market (Mar 8), as designed. Relay capture = R1. |
| Mar 29 / Apr 24: League "Mercantile Convoy" attacks Citadel Arcadia, destroys a Black Fleet Armada | "Mercantile Convoy" is vanilla's League **trade**-fleet name, not an Exiles fleet (Heg–League ops use patrol types; ours read "Relief Convoy"). Vanilla trade fleets never initiate or join battles, so it was a participant in a battle others fought. The log does not record NPC battles; unverified. |
| Apr 10: Citadel Arcadia station disrupted 40d, no other industry hit | Not Heg–League: no strike in Arcadia, and the interdiction avoids stations. The Black Fleet raid in Arcadia (launched Feb 21) is the likely cause. Not attributable from the log. |
| Apr 27: Valhalla strike, Raesvelg disrupted 127d | League strike LANDED Apr 23: `patrolhq, heavybatteries, orbitalworks` 135d. The log names **Patrol HQ**, not a military base. League owns the sensor array = R1. |
| Jul 24: "Corvus system bounty over?" | The League interdiction of Corvus ended. **Legibility defect:** these ops are titled "System Bounty – X" (`ExileHegLeagueRaidIntel.getName`), which reads as a bounty, not a war operation. |
| Aug 17–Sep 10: Naraka raid "over" but ships still present | Presence ended (SUCCESS). The fleets were on the return leg. **Legibility:** the intel says "Over" while the fleets linger about two weeks. |
| Sep 21 / Oct 1: Tyle blockade, Cibola + Madeira Blockaded; Hegemony owns relay, buoy, sensor | Held 56d of 56d, condition on and off correctly. Objectives = R1. |

**Not covered this run:** squeezes (part B, `forceState: 3`), and any Nex comparison.

#### 14.14.11 Rulings and builds, 2026-09-28

**RULING, Sean 2026-09-28 (register R1): objective captures are KEPT.** "Aggressive fleets taking
over Relays, Nav Buoys and Sensor Buoys (and the makeshift versions) is identical to all vanilla
raiding behavior and is desirable, do not suppress it." No `setNoFightingForObjective`, no capture
gate: vanilla `checkCapture` / `WarSimScript` behaviour stands for every director.

**BUILD: the strike window-end fix [RULED 2026-09-28; BUILT same day; Janino-clean; UNTESTED IN GAME].**
- `ExileHegLeagueStrikeStage.updateStatus`: at the end of the window, the strike now resolves through
  `autoresolve()` → `canBeat` **whether or not the player is in spawn range**. The old in-range
  branch was `finish(false)` with no strength check (§14.14.10 defect 1).
- In range, `canBeat` reads live `WarSimScript` strengths, so spawned fleets count as they really are.
- New permanent line, once per strike that reaches its window's end:
  `strike WINDOW END at <system> -- <market>: group strength S vs defense D (...) -> can beat,
  striking | cannot beat | player IN|out of spawn range | Nd of Md`.
- **Side effect to watch:** a strike that wins at window end with the player present lands
  immediately. Its fleets may not be over the target at that instant.
- **Not changed:** `AshenRehearsalStage:344` and `BlackFleetAssaultStage:196` carry the same
  in-range `finish(false)` pattern. Each needs its own ruling (Ashen is behaviour-complete; the Black
  Fleet assault window keeps vanilla by an earlier ruling).

**BUILD: the R5 abort-stage line [RULED 2026-09-28; BUILT same day; Janino-clean; UNTESTED IN GAME].**
- `ExileHegLeagueRaidIntel.failedAtStage` (vanilla's hook, called at the moment any stage fails)
  covers interdictions, strikes and squeezes. It prints:
  `op ABORTED in stage i (StageClass) after Nd in that stage | target | remaining FP x vs abort line y
  (FP abort | NOT an FP abort) | routes live=a abstract=b | player in <location>`.
- Logging only, wrapped so it can never break the failure path.
- **Engine fact recorded with it (code-read):** vanilla's losses test (`BaseRaidStage.enoughMadeIt`)
  counts a spawned fleet at `fleetPoints / Misc.getSpawnFPMult`, but an **abstract** route at its full
  launch FP with **no damage applied**. So the losses abort can only fire while fleets are spawned
  near the player: ops are "driven off" only on or near screen. This fits every observed abort so far.

#### 14.14.12 Rulings and builds, 2026-09-28 (second round)

**RULING, Sean 2026-09-28: fix the window-end defect in the other directors too.** "They are
'complete' only because we never caught the bugs we're catching now."

**BUILT (Janino-clean; UNTESTED IN GAME):**
- `AshenRehearsalStage.updateStatus` and `BlackFleetAssaultStage.updateStatus` now resolve at window end
  through `autoresolve()` in both player-range branches, with a `WINDOW END` log line each.
- **`AshenCulannStrikeStage` inherits the rehearsal fix.** Note: that is the fire-once Phase-4 strike.
- The Black Fleet assault's window *length* (vanilla's `maxDays`, an earlier ruling) is untouched.
  Its `autoresolve()` skips anything the live fleets already sacked.
- These were the only three `isPlayerInSpawnRange` window checks in the mod (grep 2026-09-28).

**RULING, Sean 2026-09-28: remove the Option A rollback keys** (no reason found to keep them).

**BUILT (Janino-clean; UNTESTED IN GAME):**
- **Removed** the `exilehegleague.feasibilityUnits` switch and the legacy
  `interdiction` / `surgicalStrike` / `planetaryBlockade` `feasibilityMult` keys, with their fields and parsing.
- **Removed** the Heg–League `Exiles units [` lines, which duplicated the feasibility lines' `strength S (F FP) x M` text.
  The Ashen, Black Fleet and Omicron units lines stay until their gate decision.
- The gates now read only `feasibilityStrengthMult`.
  `ExileHegLeagueConfig.feasibilityGate` / `describeGate` lost the mode argument. A null source is treated as k = 1
  (raw FP, the most conservative reading); no caller passes one.
- The system blockade's `feasibilityMult` is unaffected (always strength units).
- **Correction:** the 2026-09-26 strike comment said "Lower = bolder". It is the reverse: a HIGHER
  `feasibilityStrengthMult` admits stronger defences (0.67 = 1.5 : 1 is bolder than 0.37 = 2.7 : 1).
  The comment is fixed.
- Git history (`acf6c50`) preserves the removed rollback if it is ever wanted.

**RULING DEFERRED: intel naming ("System Bounty – X", "Over" during the return leg).** Sean has
noticed a related issue across all directors that is out of step with vanilla. To be discussed
before any change.

**Update 2026-09-28 (Sean):** the deferred intel items become one **end-of-Phase-1, pre-release**
pass: bounties under the paying faction's tab, the generic bounty icon, the Bounties tag on
blockades, and the wording. Checklist in `EXILES_HEG_LEAGUE_WAR_RECOMMENDATIONS.md` (evidence log,
2026-09-28 end-of-Phase-1 item).

### 14.15 PROPOSAL — "Break the Blockade": a contribution-based victory bonus for blockades and squeezes [ASSISTANT-ORIGINATED from Sean's idea, 2026-09-28; awaiting rulings; NOTHING BUILT]

> Proposal only. **Sean's idea** (2026-09-28): dress the blockade's bounty as a call to *break* the
> blockade, with a second-tier reward for helping drive it off, on top of the per-kill bounty.
> **Sean's answers so far:** contribution-based (yes); apply to squeezes if feasible (yes); explore relief fleets
> and their Nex interaction (yes, see §14.15.6); intel presentation as below (agreed). Payout size and
> blockader consequences were left to Claude's recommendation (§14.15.4 and §14.15.5); those are
> assistant-originated and unratified.

#### 14.15.1 The shape (two tiers)

| Tier | Trigger | Paid by | Exists today? |
|---|---|---|---|
| 1. Per-kill bounty | Each blockade/cordon ship the player's side destroys | The victim faction | **Yes** (`ExileHegLeagueBattleRewards`, 3000 per frigate-equivalent) |
| 2. **Victory bonus** | The op is **broken** (driven off) **and** the player contributed enough | The victim faction | New |

**Precedents:**
- Vanilla `SystemBountyIntel` (per-ship pay, tier 1).
- Vanilla person and military-contact bounties (a lump sum plus reputation for completing a goal, tier 2).
- Elite: Dangerous conflict zones (combat bonds per kill, plus a victory payout).

#### 14.15.2 Feasibility: both ops can detect "broken", and both breaks happen with the player present

- **System blockade** (`ExileHegLeagueBlockade`, vanilla FGI): "broken" = `isBrokenUp()`, route damage ≥
  `abortDamageFraction` (0.5), which already drives `shouldAbort()`. The `GATES RELEASED` line already
  reports `aborted=` and `damage=`.
- **Planetary squeeze** (`ExileHegLeagueSqueezeIntel`, RaidIntel): "broken" = the action stage fails on the
  **losses abort**. That is the new `failedAtStage` hook (§14.14.11), which already tells an FP abort
  from any other failure.
- **Engine fact (§14.14.11):** these aborts can only fire while the op's fleets are spawned near the
  player. So a break is inherently a player-present event, which is right for a player reward.
- **Interdictions are excluded.** They hunt shipping; they are not a siege to lift.

#### 14.15.3 Contribution (ruled: contribution-based; mechanism proposed)

- **Ledger:** add one per-op record: `playerFp` (the op's fleet points destroyed with the player involved,
  weighted by vanilla's `battle.getPlayerInvolvementFraction()`) and `totalLostFp` (all the op's losses,
  every battle).
  - Both op types already receive every battle their fleets fight: blockade pickets and cordon fleets carry
    `exile_hl_raider` and the op as `FleetEventListener`.
  - Today that listener returns early on battles without the player. For this it must still add those
    losses to `totalLostFp`.
  - The weariness/bounty helper already computes the player-side numbers, so the ledger is additive,
    not a new system.
- **Share** = `playerFp / totalLostFp` at the moment the op breaks.
- **Recommended rule:** below 20% nothing; 20–50% pro-rated; 50% or more full bonus. Configurable.
- **What this handles:**
  - Nex task groups or NPC defenders doing most of the work → little or no bonus. Fair.
  - The player "finishing stragglers" after others broke it → the break has already been decided, so
    only the share at the moment of the break counts.

#### 14.15.4 Payout (Claude's recommendation for Sean's question 2)

**Credits:**
- A lump sum on the **victim's** account, sized like vanilla's person bounties (`pirateBaseBounty1..5` =
  40k–80k), scaled by the op's size and the player's share.
- `bonus = victoryBonusBase × (op launch FP / victoryBonusRefFp) × payoutFraction(share)`.
- Suggested: `victoryBonusBase` 60 000 for a system blockade and 40 000 for a squeeze;
  `victoryBonusRefFp` = the median launch FP of each op type (read it from the launch lines before fixing it).
- Cross-check: a player who kills about half of a 350 FP blockade already earns tens of thousands of credits
  from tier 1. The bonus should feel like a *bonus* for finishing the job, not a second full payday.

**Reputation with the victim:** use vanilla's own actions rather than invented numbers.
- `COMBAT_HELP_MAJOR` (+0.03, up to FRIENDLY) for a pro-rated share.
- `COMBAT_HELP_CRITICAL` (+0.05, up to COOPERATIVE) for a full share.

#### 14.15.5 Consequences with the blockader (Claude's recommendation for Sean's question 3)

**Recommendation: no extra penalty.** Vanilla already prices the choice. With the transponder on,
`COMBAT_NORMAL` costs −0.03 per engagement *and forces the player to at best HOSTILE*
(`ensureAtBest = HOSTILE`). Adding a scripted penalty on top would punish the player twice.

**Recommended instead: the victory bonus requires the transponder ON in the deciding battle.**
- The victim can only pay and thank someone it can identify.
- Helping dark stays possible: the tier-1 per-kill bounty is unchanged, and the vanilla `_TOFF` combat actions
  do not force hostility. But the bonus and the reputation are forfeited.
- That gives a **credited-vs-deniable** choice that fits the morally grey tone: open help earns gratitude
  and makes an enemy; deniable help earns only the per-kill pay.
- **To verify at build:** whether the existing tier-1 payout already checks the transponder.

**Link to Sean's 2026-09-26 observation** (a failed strike should still raise tension): a **broken**
blockade or squeeze is a public humiliation for the actor. Suggest reporting it to the meter as
tension up, not only actor weariness up. Deferred with that broader retune (§14.14.9 note).

#### 14.15.6 Relief fleets and Nexerelin (Sean: explore)

**Merge with §14.13 (the Relief Battle).** The NPC relief force is the non-player way to break the
cordon. The player can be the relief, or fight beside it. The same ledger decides the bonus:
- relief force breaks it alone → no player bonus;
- relief force and player together → share by contribution;
- player alone → full bonus.

**Nex:**
- Nex task groups are drawn to our blockades and squeezes (register R7). When they break an op, the player's share is small, so the bonus is small. Correct.
- R7's "withdraw when a task group arrives" option must count as a **break only if it happens through
  losses**. A voluntary withdrawal must not pay the bonus.
- Nex's own blockades (`NexBlockadeFGI`) are not affected. Nothing touches `exerelin` classes, so this is
  clean without Nex.

#### 14.15.7 Intel presentation (Sean: agreed)

- While the op is live, the bounty entry should read as a call to action:
  "<Victim> offers a reward to break the <actor> blockade of <system>". It shows the window, the per-kill
  rate, the victory bonus and the break condition, and ideally "your contribution so far: N%".
- It pays out through the existing payout notice (credits icon).
- Built in the **same pass** as the end-of-Phase-1 presentation item (paying-faction tab, bounty icon,
  Bounties tag on blockades), so the intel is rebuilt once.

#### 14.15.8 Config (proposed; every number a guess until measured)

```
"victoryBonus": {
    "enabled": true,
    "blockadeBase": 60000,
    "squeezeBase": 40000,
    "refFpBlockade": 0,        # 0 = measure from launch lines first
    "refFpSqueeze": 0,
    "shareFloor": 0.2,         # below: no bonus
    "shareFull": 0.5,          # at/above: full bonus; pro-rated between
    "requireTransponder": true,
    "repPartial": "COMBAT_HELP_MAJOR",
    "repFull": "COMBAT_HELP_CRITICAL"
}
```

#### 14.15.9 Test matrix (first pass)

| # | Case | Expected |
|---|---|---|
| V1 | Player alone breaks a blockade, transponder on | full bonus, `COMBAT_HELP_CRITICAL` with victim, payout notice |
| V2 | Player joins defenders, deals about 30% of losses | pro-rated bonus, `COMBAT_HELP_MAJOR` |
| V3 | NPC defenders or Nex break it, player watches or kills stragglers after | no bonus (share below floor at the break) |
| V4 | Player breaks it with the transponder off | tier 1 only; no bonus, no reputation, no forced hostility |
| V5 | Squeeze broken by the player | as V1, squeeze base |
| V6 | Blockade completes its window | no bonus; tier 1 payments already made stand |
| V7 | Save/load mid-op | ledger persists (saved primitives on the intel) |

#### 14.15.10 Decisions for Sean

- VB-D1: payout formula and bases (§14.15.4).
- VB-D2: the transponder rule (§14.15.5), and whether a broken op also adds tension.
- VB-D3: contribution thresholds (20% / 50%).
- VB-D4: sequence with §14.13 (the Relief Battle). Recommended: this first (it needs no new fleets), relief second.

#### 14.15.11 Rulings, Sean 2026-09-28 [RATIFIED; NOTHING BUILT YET]

- **Transponder rule: DROPPED.** The victory bonus and the victim's reputation are paid **whether the
  transponder is on or off.** Sean's reasoning: attacking a non-hostile faction with the transponder
  on forces HOSTILE (a lasting penalty that has to be worked off), and a transponder condition is out of step
  with every other payment in the game. This is simpler to build: one check removed, none added.
  The "credited vs deniable" idea in §14.15.5 is withdrawn. Vanilla's own combat reputation
  rules (`COMBAT_NORMAL` vs `_TOFF`) still decide what the *blockader* thinks, unchanged.
- **VB-D1 (payout): as recommended in §14.15.4.** 60 000 blockade / 40 000 squeeze base, scaled by launch FP
  over the measured median (`refFp*`, read from the launch lines at build time), times the share
  fraction. Victim reputation: `COMBAT_HELP_MAJOR` for a partial share, `COMBAT_HELP_CRITICAL` for a full share.
- **VB-D2 (broken op adds tension): as recommended.** Deferred to the broader tension/weariness retune
  (with Sean's 2026-09-26 "a failed strike should still raise tension" observation). Not part of this build.
- **VB-D3 (contribution): as recommended.** Below 20% nothing; 20–50% pro-rated; 50% or more full.
- **VB-D4 (sequence): as recommended.** The victory bonus first (no new fleets); the relief fleets (§14.13) second.

**Amendments to the proposal text above:**
- §14.15.8: drop `"requireTransponder"`.
- §14.15.9 V4 becomes: "Player breaks it with the transponder off → same payout and victim reputation as
  V1/V2; the blockader's reaction is vanilla's `_TOFF` combat handling."
- §14.15.7 still ties the *presentation* to the end-of-Phase-1 intel pass. The payout mechanics can be
  built earlier, with a plain payout notice.

#### 14.15.12 Build: victory-bonus mechanics [RULED "Build the mechanics now", Sean 2026-09-28; BUILT same day; Janino-clean 8/8; UNTESTED IN GAME]

Mechanics only. The call-to-action intel presentation stays with the end-of-Phase-1 pass
(§14.15.7). Payouts use a plain campaign message plus vanilla's reputation message.

**Shared arithmetic: `ExileHegLeagueBattleRewards`** (already shared by the blockade and the raid intel):
- `fleetFpLost(fleet)`: the same `Misc.getSnapshotMembersLost` accounting as the per-kill bounty.
- `playerCreditFraction(fleet, battle)`: vanilla's `getPlayerInvolvementFraction()`, but zero if the player
  was not involved or fought on the op's side (the bounty's own exploit guard).
- `payVictoryBonus(...)`: the share rules (below 20% nothing; up to 50% pro-rated as `share / shareFull`, so the
  floor pays 40% rather than a sliver; 50% or more full). Size multiplier clamped to [0.5, `maxSizeMult`].
  Credits, then `COMBAT_HELP_MAJOR` or `COMBAT_HELP_CRITICAL` with the payer, a campaign message, and a permanent
  `VICTORY BONUS` log line, which is written **even when nothing is paid** ("no bonus (below 20% floor)").

**The contribution record** (saved primitives, `victoryTotalLostFp` / `victoryPlayerFp`):
- On `ExileHegLeagueRaidIntel` (the squeeze inherits it) and on `ExileHegLeagueBlockade`.
- Updated in `reportBattleOccurred` for **every** battle an op fleet fights, before the existing
  player-involved early return, per fleet (no double count).
- Pre-2026-09-28 saves restore 0, which pays nothing.

**Break detection:**
- **Squeeze:** `ExileHegLeagueSqueezeIntel.failedAtStage` pays when the parent's R5 abort line has flagged
  the failure as vanilla's **losses abort** (`lastFailureWasLossesAbort`). Once per op. Size = launch FP
  (`setLaunchFp`, set by the manager) / `refFpSqueeze` (330, the Run 4 median).
- **Blockade:** `reportOutcome` (once, from `notifyEnding`) pays when `isBrokenUp() || isBrokenByLosses()`.
  Size = the sum of `fleetSizes` / `refPointsBlockade` (14 = [6, 4, 4]). A documented deviation from the
  proposal's "launch FP": FGI formations are sized in difficulty points.
- Both paths are wrapped so a reward error can never break an op's ending.

**BUILD FINDING (why `isBrokenByLosses` exists):**
- `isBrokenUp()` reads route damage, but vanilla writes route damage only in `FGRaidAction`'s
  off-screen autoresolve, **never** for a live blockade. Every `GATES RELEASED` line so far read `damage=none`.
- The real live break is vanilla's own abort in `FleetGroupIntel.advance`: surviving fleet points below
  `totalFPSpawned × getGroupAbortsMissionFPFraction()` (0.33).
- `isBrokenByLosses()` mirrors that test for an **aborted** blockade. A pre-launch abort (source lost its
  military, nothing spawned) is not a break.
- **Side note for the design:** the existing 50% `abortDamageFraction` therefore effectively never fires
  live. Live blockades break at vanilla's 67% losses. Worth a look when blockades are tuned.

**Config:** a new `exilehegleague.victoryBonus` block (added keys only): `enabled`, `blockadeBase` 60000,
`squeezeBase` 40000, `refPointsBlockade` 14, `refFpSqueeze` 330, `maxSizeMult` 2.0, `shareFloor` 0.2,
`shareFull` 0.5. No transponder key (ruled out).

**Test (matrix §14.15.9, V4 as amended in §14.15.11).** Easiest route: `forceState: 4` for blockades or
`3` for squeezes. Fight one until it breaks, then grep `VICTORY BONUS`, `op ABORTED` (squeeze) and
`GATES RELEASED` (blockade; `aborted=true`).

**Ruling, Sean 2026-09-28: the blockade break is vanilla's 67% (survivors below 33% of spawned FP), not the proposed 50%.** Built: `blockade.abortFraction` 0.5 → 0.67 (config + code default) so our route-damage check agrees with vanilla's live group abort; the victory bonus already fires on exactly vanilla's test (`isBrokenByLosses`, same getter, same strict `<`, evaluated inside `abort()` → `finish(true)` → `endAfterDelay()` → `notifyEnding()` after `aborted` is set). `GATES RELEASED` now prints `brokenByLosses=`. The config and javadoc claim "vanilla has no strength-based abort" was wrong and is corrected. Not changed: `AshenBlockade` carries the same never-live 0.5 route-damage check (flagged for its own ruling). Janino-clean 3/3; untested in game.

**2026-09-28 (Sean): Ashen blockade aligned too.** `ashen blockade.abortFraction` 0.5 → 0.67 (config + `AshenInterdictionManager` + `AshenBlockade` defaults), superseding Ashen §6.4's "below 50% strength", with the same corrected "vanilla does abort live groups below 33%" comments. Janino-clean 2/2; untested in game.

### 14.16 Test records and fixes, 2026-10-03/04 (Vanessa save, no Nex, `forceState: 3`)

**Run 1 (Oct c207 → May c208, offender `persean`): tested nothing — PIN LIVELOCK, fixed `1fdaa25`.**
Tension sat at 77–81, above the hawk line, because the save's earlier `forceState: 4` life never resolved
a ceasefire. Pinned RETALIATION exited to ESCALATION, the pin dragged it straight back, `closeCycle()`
never ran, and `cyclesCompleted` stayed 0 (day 795, cycle 0). The strike manager's once-per-cycle guard
then blocked every roll after the first, which itself found no target (League space full of operations
left over from the forceState-4 run). Fix: under `forceState: 3`, every RETALIATION exit closes the
cycle (`RETALIATION exit: CYCLE CLOSED (testing.forceState 3 pin)`); `closeCycle()`, not
`containCycle()`, so the containment statistics stay clean. Unreachable in production.

**Run 2 (May c208 → Jan c209, offender `hegemony`): PART A PASSED.** 25 cycles, 10 operations
(7 squeezes, 3 strikes); the other 15 rolls found every whitelisted system busy or on cooldown — a pin
artifact (a 10-day cadence against normal 60–180-day TENSION dwells). Sean broke the Galatia squeeze:
`op ABORTED in stage 2 … remaining FP 0 vs abort line 232 (FP abort)`, `VICTORY BONUS` 56,253c (player
324 of 469 FP lost, share 69%, fraction 1.0), rep COMBAT_HELP_CRITICAL, outcome "did NOT hold" (League
weariness +8), 216,354c of raider bounties from the Hegemony. Tension did not move on the failed squeeze
(the open "a broken op should raise tension" item). Heg–League `WINDOW END` fired twice (Arcadia,
Yama), player out of spawn range, both resolved on strength and landed. Three relief convoys delivered
in full. Tension is now pinned at 99, so a `forceState: 4` run on this save will be a General Engagement.

**Fix (Sean approved 2026-10-04): the squeeze attacks only its anchor's station.** Sean watched League
fleets from the Sphinx squeeze destroy Tigra City's station; the Jan 18 save's 45-day disruption timer
dates the kill to ~Dec 8 c208. Cause: `planetaryBlockade.suppressStationAttacks: false` switched station
protection off for EVERY station in the system; Sphinx has no station, vanilla's tally ignores stations,
and the cordon charged the only one it could reach — another market's, which the launch gate and the
arrival line never counted. Built: `ExileHegLeagueInterdictionStage.isStationProtected(market)` (per-market
filter, true for interdiction); the squeeze always runs suppression and exempts only its anchor's station
(all stations when the flag is true). Config meaning documented, key unchanged. Janino-clean 3/3;
untested in game. **Same exposure, unverified:** the strike stage has no station suppression and its
gate counts only the target's station — for the bug sweep.

**Evidence filed:** Sean's "three blockades ended, none announced" is the known naming defect (squeezes
post as "System Bounty – X", end as "The blockade in the X has ended"); added to the end-of-Phase-1
checklist in `EXILES_HEG_LEAGUE_WAR_RECOMMENDATIONS.md`. "Recent Unrest" on Tigra City remains
unexplained: vanilla station loss only disrupts the station (no Recent Unrest in the API path, none in
the obfuscated jar), and no Exiles strike touched Tigra City.

**Run 3 (Jan c209 → May c209, `forceState: 4`, tension 99–100, General Engagement): PART B PASSED.**
Sean broke the Isirah blockade (Sphinx → Isirah, launched Mar 5): `GATES RELEASED … held 48d of 56d |
aborted=true … (abort at 0.67) brokenByLosses=true`, `VICTORY BONUS` 60,000c (player 279 of 296 FP lost,
share 94%, base 60000 × size 1.0 for the [6,4,4] blockade × fraction 1.0), rep COMBAT_HELP_CRITICAL,
Hegemony weariness +8 ("did not hold", no tension contribution), plus 176,727c of raider bounties from
the League. The NEGATIVE case also fired: the Tyle blockade was broken by League defenders 4 days into
its hold while Sean was in-system but not fighting — `VICTORY BONUS … player 0 of 223 FP lost (share 0%)
-> no bonus (below 20% floor)`. The Black Fleet assault's window-end branch fired too (Zagan, player out
of range, resolved on strength, three markets sacked), so the window-end fix is now confirmed in all
three directors (Ashen 2026-10-03, Heg–League and Black Fleet here). No Exiles warnings or errors.
Still untested: the squeeze station fix (no squeeze launched under `forceState: 4`).

### 14.17 Run 5 — Sean's 3-year Nexerelin comparison run (Rexx save, Mar c206 → Apr c209, no pins) [READ 2026-10-05]

**Organic war:** 23 ops launched (12 interdictions, 5 strikes, 2 squeezes, 4 blockades), 7 crisis
cycles, ESCALATION #1 at day 720 (~2.0 years; the Run-4 Nex run had it at day 751) and #2 at day 1135.
Two strikes landed via `WINDOW END` with the player away; relief convoys ran. No Exiles warnings or errors.

**R7 detector, first live read (20 ops summarised; 3 still live at the end).**

| Task groups seen during the op | Ops | Failed |
|---|---|---|
| A HOSTILE task group in the target system | 9 | 3 (33%) |
| None | 8 | 3 (38%) |
| Only own-side or neutral task groups | 3 | 1 |

Hostile task groups met almost half the ops but did not measurably raise the failure rate (small
sample). **Every one of the run's seven failures happened with the player in the system** — the
engine lesson again: abstract routes take no losses, so an op is only ever driven off where it is
spawned near the player. The R7 effect is therefore real only on screen, where it is also visible.
Own-side ASSIST confirmed (e.g. Hegemony "5th Sphinx" assisting the Tyle interdiction — Sean killed
it; the op aborted the same day). The correction holds: no hostile task group came to defend
against any of our four blockades.

**Nexerelin runs its own Heg–League war on top of ours.** Nex's strategic AI logged 9 Persean League
offensives: 5 raids (`NexRaidIntel`, 4 of them at Tri-Tachyon's Port Tse) and 4 blockades
(`NexBlockadeFGI`: Eventide twice, Yama, Tibicena). The "Persean blockade" Sean broke in Samarra was
Nex's (Suddene → Eventide, launched Oct 10 c207), which is why no Exiles victory bonus appeared; Sean
never fought an Exiles op fleet this run, so the absence of raider-bounty lines is consistent. Nex
relief fleets (`ReliefFleetIntelAlt`) are a separate Nex feature that lowers recent unrest. Note:
Exiles' own premise pin (B2, "Nex relation pin applied") is what keeps the two at war under Nex,
which is also what keeps Nex's war AI running between them.

**Nex task groups also hit other directors.** The Dec c207 Ashen rehearsal on Port Tse FAILED after
25 of 40 days (Feb 12 c208) after a Hegemony task group destroyed its fleets (Sean, Feb 11); a
Persean task group "defended against the Ashen Crusade" in Mayasura — the rehearsal is a `RaidIntel`.
A Galatia task group attacked the Black Fleet (Jun c207). The detector covers Heg–League ops only.

**Return-leg fix (Sean approved 2026-10-05; Janino-clean 3/3; untested in game).** Vanilla's raid
`ReturnStage` only re-routes fleets home; nothing stops them engaging on the way, so returning op fleets
kept fighting, joined nearby battles and (with the presence-stage station suppression ended) could hit
stations days after an op was over. `ExileHegLeagueRaidIntel` now sets `$doNotGetSidetracked`
(`MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED`) on every live fleet the tick the op leaves its
action stage (completed or failed), and on any fleet that spawns later on a re-routed (`STRAGGLER`)
route. Covers interdictions, strikes and squeezes; blockades already had it, because vanilla's
`FleetGroupIntel.giveReturnAssignments` sets the same flag. One log line per op: `RETURN LEG: … N live
fleet(s) set $doNotGetSidetracked`. Unverified: whether the flag also stops a fleet joining a battle
already in progress (the fleet-behaviour reference covers engagement choice, not battle joining).
Not covered: the outbound travel leg (register R5, still unbuilt), and the Ashen / Black Fleet /
Omicron raid intels, which have the same vanilla gap.
