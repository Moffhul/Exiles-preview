# Ashen Crusade War Director — Design Spec

> **Status: SPEC RATIFIED, NO CODE WRITTEN. Pass 1 cleared to begin.** Author spec supplied
> 2026-07-31 by Sean; rulings **A–L and Q1–Q4 all answered** and folded in below. Three open items
> remain in §12 (O1–O3); none blocks Pass 1.
>
> **⚠ READ §3.2a FIRST (added 2026-08-08).** The war clock has been **reframed from a beat-timer
> into a countdown on the Crusade's preparations to steal Tri-Tachyon's Corrupted Nanoforge**. It
> supersedes parts of §5.4, all of §5.5, and §7.2 step 3, and it re-scopes Pass 7. Passes 1–4 are
> unaffected. Phase 3's disposition is deferred to the next session.
>
> **Two late findings worth knowing before you read further:**
> - §6.4 — **vanilla already ships a complete blockade system** (`BlockadeFGI` / `FGBlockadeAction`
>   / `econ.Blockaded`, with `PerseanLeagueBlockade` as a worked example). This re-plans Pass 6.
> - §7.5 — an earlier claim in this doc about the nanoforge install gate was **wrong** and is
>   corrected there. Exiles overrides `special_items.csv`; Sean confirmed the install in-game.
>
> **Labelling convention** (CLAUDE.md §7 creative-authority rule):
> - **[RATIFIED]** — Sean has ruled. Accepted canon for this system.
> - **[AUTHOR]** — from Sean's original spec, taken as written.
> - **[PROPOSED]** — assistant recommendation still awaiting a ruling. Not canon.
> - **[VERIFIED]** — checked against vanilla source or Exiles code.
>
> Code home (planned): `data/scripts/exiles/AshenWarDirector.java` + stages, and
> `data/scripts/marketconditions/` for the new conditions. Config:
> `data/config/exiles/director_config.json` → `"exileashencrusade"`.

---

## 1. Mandate and fiction

**[AUTHOR]** The Ashen Crusade is a splinter of the Knights of Ludd (the Luddic Church's military
arm). After Omicron devastated Gilead in Cycle 199, the Crusade broke from the Church and went to
war against Tri-Tachyon, believing Tri-Tachyon is deliberately trying to exterminate the Luddic
faith. Its stated goal is to eliminate Tri-Tachyon as a faction — in the mistaken belief that this
will end the attacks on Luddic worlds.

The Crusade's Knights-of-Ludd and Luddic-Path heritages produce a **combination of symmetrical and
asymmetrical warfare**: it attacks Tri-Tachyon with fleets *and* with sabotage. The sabotage arm is
the Pather influence showing itself.

Crusade relief shipments to refugee enclaves are **genuine charity and genuine recruitment at the
same time**. This is the morally-grey pillar in a single market condition.

### Relations at campaign start **[VERIFIED — `ExilesSectorGen.java`]**

| Toward | Relation | Consequence for this director |
|---|---|---|
| `tritachyon` | **VENGEFUL** | War is live from day one; no diplomacy gate needed. |
| `exileomicron` | **VENGEFUL** | Omicron intervention at Hybrasil is already hostile-valid. |
| `pirates` | HOSTILE | **Hybrasil contains a pirate market (Donn).** Target discipline required. |
| `independent` | INHOSPITABLE (not hostile) | Relief fleets can transit Corvus/Mairaath safely. |
| `exilerefugee` | NEUTRAL | Relief fleets welcome; refugees are never a target (`ExileProtectedFactions`). |
| `player` | SUSPICIOUS | The >+49 blockade option is reachable, but only via Phase-2 quest content. |
| `luddic_path` | COOPERATIVE | — |
| `luddic_church` | INHOSPITABLE | The Church opposes methods, not faith. |

---

## 2. Strategic geography — the four targets

**[VERIFIED]** — read from `starsector-core/data/campaign/econ/{hybrasil,magec,mayasura}.json`.
This table is load-bearing: several parts of the original spec assumed industries that do not exist.

| Market | System | Size | Industries |
|---|---|---|---|
| **Culann** | Hybrasil | 5 | population, **spaceport**, refining, **orbitalworks** *(corrupted_nanoforge)*, heavybatteries, militarybase, battlestation_high |
| **Eochu Bres** | Hybrasil | 6 | population, **farming**, **megaport**, grounddefenses, patrolhq, lightindustry, starfortress_high |
| **Tibicena** | Magec | 5 | population, megaport, mining, grounddefenses, patrolhq, orbitalstation, commerce |
| **Port Tse** | Mayasura | 4 | population, spaceport, mining, grounddefenses, militarybase, orbitalstation |

**Discrepancies against the original spec, and what they forced:**

1. **Culann has NO Mining industry** (commented out in vanilla). Eochu Bres has **no Mining,
   Refining, or Orbital Works at all**. → §5.2 generalization.
2. **Tibicena is a size-5 Megaport with Commerce**, not a "small mining and trade station" — the
   richer of the two outposts.
3. All four **special**-sabotage targets exist as written: Megaport and Farming on Eochu Bres,
   Orbital Works and Military Base on Culann. ✅
4. All five Phase-4 Culann knockout targets exist as written. ✅ (Note: **spaceport**, not megaport.)
5. Both Hybrasil markets carry a **high-tier defensive station**. Assault feasibility must account
   for these or the op suicides.

### Omicron overlap **[VERIFIED — `docs/EXILES_OMICRON_TRITACHYON_DEFENSE.md`]**

All four markets are already under the Omicron protection mandate:

| Market | Station sleeper | Strategic-response threshold |
|---|---|---:|
| Culann | 70–100 FP | **350 FP** (plus a 180–220 FP pre-positioned strategic detachment) |
| Eochu Bres | 80–110 FP | **350 FP** |
| Port Tse | 35–55 FP | 200 FP |
| Tibicena | 45–65 FP | 200 FP |

`OmicronTriTachyonDefenseManager` scans live `RaidIntel` ops and arms a strategic response when the
target system contains a protected market **and the raiding faction is hostile to Tri-Tachyon**.
Ashen is VENGEFUL to Tri-Tachyon, so **Ashen operations already trigger this with zero new code.**

- A Phase-2/3 interdiction raid on **Mayasura or Magec** above 200 FP wakes an Omicron response.
- A Phase-4 Major Assault on **Hybrasil** above 350 FP wakes Culann's strategic detachment.
- Sabotage creates no `RaidIntel` and therefore never triggers Omicron. Correct and intended.

The best emergent property of the design: **the Crusade's war against Tri-Tachyon walks it straight
into the machine that started the war**, and neither side understands why.

---

## 3. Director architecture

### 3.1 The war clock **[RATIFIED — hybrid]**

The timer is the spine; the war score is a modifier on it. Saved state is three fields plus one
`IntervalUtil`:

```
int   phase              // 1..4
float phaseDaysRemaining
float warScore           // accrues and is spent
```

- Phase durations come from config, rolled once on phase entry.
- `warScore` **shortens Phase 1** and **scales operation FP**. Successful relief fleets add to it;
  failed/aborted operations subtract.
- Cost: one `advance()` decrement on a coarse `IntervalUtil` (0.1–0.2 day tick, matching
  `ExileRefugeeConvoyManager`). No per-frame allocation, no per-frame economy scan.

A pure war score with phase thresholds was rejected: the phases are duration-driven, so a score
would need thresholds mapped back onto those durations — two systems doing one job, with worse
legibility when debugging.

### 3.2 Phase graph **[RATIFIED — ruling A]**

**Phase 4 succeeds at most ONCE. It is entered automatically, and only on a successful special
sabotage against a Culann target; after a successful STRIKE the automatic loop is permanently
1 → 2 → 3.**

> **Read this together with the Q1 retry semantics below before calling a repeat a bug.** "Once"
> governs *successful strikes*, not entries: the Crusade re-enters Phase 4 on every fresh Culann
> special-sabotage success until one strike actually lands. Until Pass 7 exists there is no strike
> at all, so nothing can succeed and repeated Phase-4 entries in a test run are **correct**, not a
> violation of ruling A. (Misdiagnosed as a bug on 2026-08-02; Sean caught it.)

```
        ┌───────────────────────────────────────────────┐
        │                                               │
        ▼                                               │
   [1 BUILDUP]  90d  (150d if the previous phase was 4)  │
        │                                               │
        ▼                                               │
   [2 INTERDICTION]  60–90d                              │
        │                                               │
        ├── special sabotage succeeds on a CULANN target │
        │   AND Phase 4 has not yet succeeded ──────────►│ [4 CULANN STRIKE]
        │                                                 (once ever)
        ▼
   [3 REHEARSAL]  60–90d ─────────────────────────────► back to [1]
```

> **Amended 2026-08-09.** Phase 3 is **the Rehearsal**, not a blockade (§6.4). The branch shape
> above is **confirmed and kept** (topology T1): Phase 4 replaces Phase 3 in the final cycle. Only
> the branch *condition* changes under §3.2a — preparation-complete instead of a special-sabotage
> roll. Planned pacing is **3 cycles (~1.8 yr)**, or 4 (~2.4 yr) if the player suppresses it.

Further major attacks — including anything on Eochu Bres — are **quest-gated content outside this
director** (§7.4). Fiction: the Crusade shot its bolt. It can still grind, blockade, and sabotage
forever, but it cannot escalate again on its own.

**Cut as a consequence [RATIFIED — Q2]:** the original "Culann has no nanoforge → skip Phase 3 →
Phase 4" branch. It existed only to route to the Eochu Bres attack, which has left the director.
Phase-4 entry no longer consults nanoforge presence at all.

**Retry semantics [RATIFIED — Q1]:** only a **successful** Culann strike closes the loop. A repelled
assault costs war score and the Crusade may try again — but it must earn another successful Culann
special sabotage first, so retries are naturally rare.

### 3.2a WAR-CLOCK REFRAME — **RATIFIED 2026-08-08**

> **Read this before §5.4, §5.5 and §7.2 — it supersedes parts of all three.** Sean's proposal,
> ratified in the same session. This is the largest change to the director since the spec was
> written, and it is a change of *frame*, not of built code: Passes 1–4 keep working as-is.

#### The reframe

**The war clock is no longer a beat-timer. It is a countdown on the Ashen Crusade's preparations
for a deliberate operation to steal Tri-Tachyon's Corrupted Nanoforge from Culann.**

Sean's diagnosis of the old design: *"hanging Phase 4 off a random Special Sabotage attempt (random
target and random success rate) is a real weakness."* Correct — under the old scheme the campaign's
climax was decided by `P(sabotage lands) × P(target was Culann) × P(assault cracks Culann)`, and the
whole of §5.5 exists only to force that product into a 2–3 year window.

**Phases 1 and 2 are working and stay as they are** (durations need a tuning review later, no
structural change). Phase 3 needs tuning and its disposition is **deferred to the next session** —
see the note at the end of this section. Phase 4 remains a good, logical, once-per-campaign climax;
only its *trigger* changes.

#### What this retires

| Retired | Why |
|---|---|
| §5.5's entire probability edifice | The strike now arrives because preparation completed, not because dice landed. A schedule replaces a gamble. |
| The random-target/random-roll Phase-4 trigger (§5.4 "Phase-4 branch") | Superseded by preparation completion. |
| **Ruling K's "UNDER REVIEW" flag** (§7.5) | **Resolves to STOLEN.** A war built around a heist cannot have the prize destroyed. |

#### Architecture — ✅ **RATIFIED 2026-08-13, BUILT the same day**

All three items below were ruled by Sean and are now canon. **Built and Janino-clean; untested in
game.** The measured accrual figures come from auditing every `addWarScore` caller.

- **Keep the phase machine; give it a destination.** §3.1 rejected pure-score-with-thresholds for
  legibility and that reasoning stands. Phases are the visible *stages of preparation*; Phase 4
  fires when preparation completes. Topology unchanged (T1) — **only the condition changed**, from
  `phase4Requested` to `isPreparationComplete()`.
- **`warScore` IS the preparation meter, threshold `1.0`.** ✅ Ruled.
- **Special sabotage repurposed, Culann-only, `+0.08`.** ✅ Ruled.
- **Failure floor `0.5`, absolute.** ✅ Ruled (see ruling 4 below).
- **`scorePerAssaultFailure` → `assaultFailureFloor`.** ✅ Rename approved (CLAUDE.md hard rule).

#### The arithmetic this was ruled on

| Source | Rate | Per cycle | Player-contestable? |
|---|---|---|---|
| Relief convoys | 0.045 × ~5.5 | **0.2475** | ✅ attackable |
| Rehearsal success | 0.12 × 1 | **0.12** | ✅ attackable |
| Special sabotage (Culann) | 0.08 × ~0.25 | **~0.02** | ❌ dice only |
| | | **~0.37/cycle** | |

A cycle is ~225–240d, so **1.0 arrives in ~2.7 cycles ≈ 1.7 in-game years** — independently
reproducing §6.4's 1.8-year projection from a different direction, and sitting **slightly fast**
against the ratified 2–3 year band. **Tune the accrual rates, never the threshold:** every other
tunable is a fraction of the 0..1 range, so moving the ceiling silently rescales all of them.

Recovery from the floor is `(1.0 − floor) / 0.37` cycles: **0.6 → ~8 months, 0.5 → ~10 months,
0.4 → ~12 months.** 0.5 sits inside the "no more than a year" ceiling with margin, which matters
because late accrual accelerates as Phase 1 shortens.

**The uncontestable-accrual tradeoff was weighed and accepted.** Relief convoys and rehearsals can
both be physically attacked; sabotage cannot. At ~5% of total accrual the dilution does not
meaningfully blunt the player's lever — but raising `scorePerSpecialSabotage` would, and that is the
reason it sits below the rehearsal's 0.12. A hard prerequisite (*preparation complete AND intel
obtained*) was considered and **rejected**: it reinstates the dice gate the reframe exists to remove.

#### 🔴 The §7.0 telegraph had to be rebuilt — a gap the reframe opened and nobody had recorded

**Found during the build, 2026-08-13.** The ratified §7.0 telegraph was gated on `queued` — it fired
only when special sabotage actually triggered Phase 4. **The reframe removes that trigger, so the
warning would have gone silent**, retiring a RATIFIED requirement (O4: *"the frustration comes from
no warning, not from no quest"*) as an invisible side effect.

Rebuilt as a property of preparation: `phase4TelegraphScore: 0.85`, fired from `addWarScore()` at the
moment of crossing, which at ~0.37/cycle buys **~90 days of notice**. Strictly better than the old
behaviour — it fires when the strike is genuinely near rather than when dice happened to land. It
**re-arms on a repelled strike** (the floor drops preparation below the threshold), so a rebuilt
Crusade warns the player again instead of warning once per campaign.

The old sabotage message **survives, demoted to ambient signal** and no longer gated: the player
hears that the Crusade is working Culann over, without being promised an imminent offensive.

> ⚠️ **The telegraph's PROSE is placeholder — Sean's to write.** Mechanism and timing are ruled;
> wording is not. Same standing as the Pass-6d mirror description.

#### Sean's question — does threshold 1.0 lock out a Phase 5+? **No. Answered 2026-08-13**

The clamp is ours, not the engine's, and every consumer already clamps at point of use — so the
ceiling could be raised tomorrow. Raising it is nonetheless the worst of three options:

1. **Raise the ceiling (0→2).** Cheapest in code, worst in practice: every tunable is a *fraction of
   the range*, so `rehearsalEscalateWarScore: 0.4` stops meaning "40% prepared" with nobody editing
   it. Same failure family as the hardcoded exemption windows rejected on 08-08.
2. **Multiple thresholds inside [0,1].** Range stays stable, but the failure floor, escalation gate
   and FP curve all share one axis; late-phase pacing becomes hostage to early-phase tuning.
3. **A new clock per arc — Sean's own instinct, and the right one.** *A progress meter is a
   compressed answer to one specific question* ("how ready are they to take the forge from
   Culann?"). After the heist the question changes, so the compression is wrong — relief convoys
   meaning "recruit the crews for the raid" does not map onto whatever follows. It also matches two
   settled things: the Sector Destabilization Director is meant to be per-faction *configurable*,
   and **CLAUDE.md already rules post-Phase-4 escalation to be quest content**, so an Exiles 2.0
   Phase 5 would be quest-*started* — which is exactly "a new clock beginning after a successful
   Phase 4."

**Insurance built:** the threshold is a config key rather than a hardcoded `1.0`, so a future arc can
change threshold *or* meter without touching the phase machine.

#### Ruling 1 — the fiction. **RATIFIED**

The Crusade's pursuit of a Corrupted Nanoforge is doctrinally survivable and **does not fracture the
faction**.

**[VERIFIED 2026-08-08]** The precedent is in the game data, not just forum lore. Vanilla
`starsector-core/data/campaign/econ/canaan.json:55` gives Asher — the Luddic Church's primary
manufacturing world — `["orbitalworks", "corrupted_nanoforge"]`. **Exiles does not override
`canaan.json`** (no Canaan file in the mod's econ dir; `applyAsherChangesOnce()` only adds
`exile_luddic_unrest`, a patrol HQ, and a battlestation upgrade), so it is still installed in an
Exiles game.

The Crusade therefore inherits an existing doctrinal exception from the Church it splintered from
rather than inventing one. This also plays well against the §3 CLAUDE.md guardrail that the Church
opposes the Crusade's *methods, not their faith*: the Church cannot condemn the nanoforge itself
without indicting Asher, so its objection must be the theft and the war.

**Motive is deliberately left unsettled** among three readings, all in play: leadership sees the
pragmatic benefit; the devout accept it as a sacrifice they are making; some expect it destroyed
once Tri-Tachyon is defeated, some consider themselves the proper *keepers* of such technology, some
do not care so long as Tri-Tachyon burns for Gilead. *(Assistant suggestion, not ruled: the
"keepers" reading is the most quest-fertile, being the only one that does not resolve when
Tri-Tachyon falls.)*

#### Ruling 2 — the nanoforge is already gone. **RATIFIED**

**The Phase-4 operation launches and executes regardless.** The Crusade has prepared for years on
old intelligence and does not know the vault is empty until Culann is cracked open. Intelligence
failure is better fiction than a director that omnisciently checks Tri-Tachyon's inventory before
committing, and it costs nothing to build.

**§5.4 Q2 was already correct and STANDS** — nanoforge presence does not gate Phase-4 entry, it only
decides whether there is anything to steal on success. *(An earlier claim this session that the
reframe reopened Q2 was wrong and was withdrawn.)*

Two alternatives were considered and **rejected**:

- **Flip Omicron hostile to Tri-Tachyon.** Mechanically trivial, but it fires the Omicron arc's
  designated failure state as a side effect: CLAUDE.md §6 defines that quest as restoring control
  *without reclassifying Tri-Tachyon as hostile*, and the ratified hidden canon (2026-07-22) has
  Omicron still obeying a defensive mandate. It is also the largest event in the mod triggered by
  its least-considered branch. **Preserved as Omicron-arc endgame content**, quest-gated, where
  ruling A.b already licenses catastrophic outcomes for player alignment. Not spent here.
- **Saturation-bomb Culann.** Reverses ruling B / §7.4 in Sean's own words, and re-adds
  `doBombardment`, `reduceMarketSize`, permanent Pollution and the market-size floor — what §7.4
  calls the single largest stability risk in the original spec — to serve the rarest branch.

**[NOTED — kept in mind, NOT committed]** If the *player* holds the stolen forge, the Crusade's war
aim shifting to the player is the strongest version of this consequence: steal Tri-Tachyon's forge
and inherit Tri-Tachyon's war. Belongs to `EXILES_ANTIPLAYER_ESCALATION.md`, not this director.

#### Ruling 3 — Phase-4 success outcome. **RATIFIED — supersedes §7.2 step 3**

On a successful strike, **whether or not the nanoforge is present**, work Culann over:

| Industry | Offline |
|---|---|
| **Heavy Batteries** | **30 days** ← *added 2026-08-08* |
| Spaceport | **60 days** |
| Refining | **180 days** |
| Orbital Works | **360 days** |
| Military Base | **360 days** |

Plus the bombardment animation on success, as Omicron does.

**Recent Unrest [RATIFIED 2026-08-09]: apply to BOTH Culann and Eochu Bres**, even though Eochu Bres
is not attacked — the Crusade cracking Tri-Tachyon's industrial heart shakes the whole of Hybrasil.
Route both through **`ExileUnrestGuard.isBattered()`**, which matters more here than anywhere else in
the spec: Hybrasil is already double-teamed by Omicron ops, and Eochu Bres takes this hit without
any compensating event of its own.

**Heavy Batteries — RULED 2026-08-08.** All five industries go down, but Heavy Batteries recovers
**fastest of the five**, deliberately: *"keep Culann wounded not dying."* The 30-day window is long
enough to matter and short enough that Culann does not spend a year as a free target for the Black
Fleet, pirates and the player.

The resulting shape is coherent with the fiction: Culann gets its **hard shell back within a month**
but loses its **reach for a year** (Military Base still 360d, so patrols and defense-fleet quality
stay degraded long after the batteries are live). The Crusade came for the forge and to cripple
production — not to take the planet.

**Two carryovers CONFIRMED 2026-08-08:**

1. ✅ **Bombardment stays visual only** — `MarketCMD.addBombardVisual()`, **no `doBombardment` call**,
   per ruling B. No market-size loss, no pollution.
2. ✅ **The nanoforge transfer still happens in addition** when the forge is present — the industry
   knockout and the theft are not alternatives. §7.2 step 4 stands unchanged.

**3. "Already offline" semantics — RATIFIED 2026-08-08. Replaces §7.2's "extend by 90 days".**

```
offline = max(remaining, thisIndustryDuration)   // per industry, NOT additive
```

> ✅ **[VERIFIED 2026-08-08] This is a vanilla parameter, not custom code.** `BaseIndustry` ships a
> two-arg overload (line 1789):
>
> ```java
> public void setDisrupted(float days, boolean useMax) {
>     ...
>     if (useMax) dur = Math.max(memory.getExpire(key), dur);
> ```
>
> So the ruling implements as **`setDisrupted(days, true)`** — one boolean argument. No arithmetic
> of ours, no new saved state.

**Scope [RULED 2026-08-08]:** this rule governs the **Phase-4 Culann strike only**. Ordinary and
special sabotage keep their own semantics and are **not** covered by it.

The original "extend by 90 days if already offline" was written when all five durations were 90. It
is not a hypothetical case: Culann's Orbital Works or Military Base can already be disrupted by a
special sabotage (§5.4, 90 days) when the strike lands, and other Exiles systems disrupt Culann on
their own schedules.

`max()` guarantees the strike always imposes at least its full duration while never exceeding it.
Additive stacking was rejected — a 40-day sabotage remnant would push Orbital Works to 400 days, and
repeated disruption could drift it further, in a direction nobody chose.

> ✅ **Phase 4's design is now COMPLETE**: trigger, success outcome, failure semantics, fiction, the
> nanoforge-missing contingency, and "already offline" handling are all ruled. Pass 7 has a full
> spec to build against. The only outstanding Phase-4 item is a **test** item, not a ruling: the
> deciv check on five simultaneous shutdowns, routed through `ExileUnrestGuard`.

**Consequences flagged as chosen, not discovered:**
- This is a large escalation from the flat 90 days. Culann is Tri-Tachyon's main industrial base, so
  360 days of Orbital Works offline is a sector-wide Tri-Tachyon ship quality and availability
  collapse for a year. Judged correct for a once-per-campaign climax and on-theme for controlled
  collapse: dramatic, bounded, recoverable.
- **Deciv margin narrows.** The verified rule is ~16 months sustained 0-stability; 360 days is ~12.
  Under it, but four simultaneous industry shutdowns is a bigger stability hit than what was
  measured. **Needs a live check, and routing through `ExileUnrestGuard`** exactly as the special
  sabotage does.

#### Ruling 4 — failure semantics. **RATIFIED (target), implementation PROPOSED**

**Target, in Sean's revised words:** buildup to Phase 4 takes **2–3 years, preferably ~2**; after a
repelled strike the Crusade must be able to reproduce it in **no more than a year**. *(His initial
"reset to 75%" was withdrawn in the same message — do not reinstate it.)*

Fiction: the buildup took a long time, but the infrastructure survives the lost fleet. They keep
coming.

✅ **RATIFIED 2026-08-13 — floor = 0.5, implemented as `assaultFailureFloor`.** Rebuilds in ~1.35
cycles ≈ 10 months at the measured 0.37/cycle. Implemented as `min(warScore, floor)` rather than a
plain assignment, so the floor can never *raise* preparation if a strike is ever repelled from below
it. The original proposal, which the ruling followed, is preserved below.

**[PROPOSED → RATIFIED] Implement as an absolute floor, not a percentage of current.** A relative 75% compounds
across repeated failures (0.75 → 0.56 → 0.42), so the third retry drifts well past the one-year
target in a direction nobody chose. A configured floor retries in a predictable window every time,
is directly tunable against "no more than a year," and matches the fiction better — infrastructure
does not degrade a little more each time. The target implies a floor around **50–60%** of the meter
depending on where full preparation lands.

**Caveat to verify live:** preparation probably does not accrue linearly, since ops scale with war
score and a mature Crusade rebuilds faster. Expect the floor to need tuning downward after the first
Pass 7 run.

#### Ruling 5 — Tri-Tachyon is too passive. **RAISED, direction recorded, NOT SCOPED**

Sean, correctly: Tri-Tachyon *"basically sits there and takes it."* Its only agency in the current
design is the §6.3 system bounty (a player-facing lever, not a faction action) and Omicron's
defensive detachment. Success or failure in Phase 4 should generate some Tri-Tachyon response.

**§7.3 ruling H.c already records Sean's proposed answer** — Tri-Tachyon hires the player to find
where the Crusade operates from, then passes the target data to Omicron, making the Providence
markets addable Omicron targets. His instinct has been consistent across sessions and the mechanism
is already scoped. It remains **quest content**, so it does not fix passivity on the director's own
timescale.

**[PROPOSED] Director-side half: let Omicron thicken defensively at Culann after a strike.** This is
free fiction — reinforcing threatened Tri-Tachyon assets *is* Omicron's ratified mandate, and
`OmicronTriTachyonDefenseManager` already exists. Tri-Tachyon does not need to know where Providence
is to harden what it just lost. The *offensive* answer stays quest-gated per H.c, keeping the big
escalation as the player's lever rather than an automatic consequence.

**Scope honesty:** a genuine Tri-Tachyon response *system* is its own faction director and belongs
with the general Sector Destabilization Director (CLAUDE.md §6), **not Pass 7**. Recorded here as a
named work item so it does not grow into the Ashen reframe.

#### Deferred to the next session — Phase 3 and the blockade

Sean opened the session with two related concerns, only the first of which was worked through:

1. **Phase-3 blockade behaviour is not what he planned** (§14.8 records the open question). Phase 3
   *"needs tuning, but it's coming along."*
2. **He believes the blockade concept fits the Hegemony/League war better** — lore-wise neither side
   will fully commit, and both have strong voices preferring disengagement. That is also a better
   match for what vanilla's `BlockadeFGI` actually does well: **system denial** (hold the gates),
   not the **station denial** §6.4 asked for.

**[PROPOSED, undiscussed]** The reframe may rescue Phase 3 rather than remove it. A heist needs
Culann unable to be reinforced when the fleet arrives, so Phase 3's natural job becomes **isolate
Culann** — which *is* system denial, aimed at what vanilla is good at. It also answers Sean's own
objection (*"why would a cause dedicated to annihilation stop there?"*): under a heist frame the
blockade is instrumental setup, not a terminal state. This does not preclude the Hegemony/League
getting their own blockade — `FGBlockadeParams` is per-instance, so both can exist with different
fleets and durations, which is what ruling F.d always intended.

**None of that is ruled. Phase 3 is the first item for the next session.**

### 3.3 Lifecycle, registration, and kill switches

- One `AshenWarDirector implements EveryFrameScript`, registered in `ExilesModPlugin`
  `onNewGameAfterEconomyLoad()` and re-added under the existing `hasScript` guard in
  `onGameLoad()` — identical to `BlackFleetRaidManager` / `ExileRefugeeConvoyManager`.
- Quest override: `$exiles_ashen_war_enabled` (Boolean sector memory), matching
  `$exilebf_raids_enabled`.
- Every sub-system (sabotage, relief, interdiction, blockade, assault) gets its **own** config
  `enabled` flag so any one can ship dark without touching the others.
- Saved op objects are **named static inner classes** (XStream safety — the
  `ExileRefugeeConvoyManager.ConvoyOp` precedent). Never anonymous classes in saved state.

### 3.4 Player messaging **[RATIFIED — ruling J4: mirror the Black Fleet]**

| Event | Player-facing? |
|---|---|
| Sabotage (success or failure) | **Silent.** The condition appearing on the market is the only tell. |
| Relief fleets | **Silent.** The fleet is visible; the condition is the tell. |
| Interdiction raid | Hidden intel, **revealed on in-system arrival** or player battle contact. |
| Tri-Tachyon system bounty | **Visible** — explicitly specified. |
| Blockade | Visible once the player interacts. |
| Culann Major Assault | Visible on arrival. |

All debug messaging goes through a `debugMessages` config flag, **true during development, false
before production** — with its own line in the §9 checklist.

---

## 4. Phase 1 — Buildup

**[AUTHOR]** Default 90 days; **150 days** if the previous phase was Phase 4. No raids are spawned.

### 4.1 Relief Fleets

**[AUTHOR]** One relief fleet every 30 days. Transports with an armed escort, sent to refugee
enclaves, where they orbit for **10–20 days** (randomized, tunable). Each successful relief fleet
adds to the war clock.

### 4.1a Cadence by phase **[RATIFIED 2026-08-01 — amends the Phase-1-only original]**

After seeing convoys in play, Sean extended relief beyond Phase 1. The Crusade would not stop
feeding refugees merely because it started raiding, and confining the faction's most characterful
content to a third of the cycle meant the player rarely saw it.

| Phase | Cadence | Rationale |
|---|---|---|
| 1 Buildup | **30d** | the recruiting drive; still visibly double-rate, so the clock keeps its signature |
| 2 Interdiction | **60d** | charity is not an offensive that pauses |
| 3 Blockade | **60d** | as above |
| 4 Culann Strike | **none** | everything is committed |

**`scorePerRelief` moves with this and must be rechecked whenever either changes.** Phase-1-only
was ~3 convoys/cycle at 0.08 → maturity in ~4 cycles ≈ 2.6 years. The off-phase runs give
~5.5/cycle, so the value drops to **0.045** to hold the ratified 2–3 year band (§5.5). Naively
adding off-phase relief at the full 30d cadence without touching the score would have pulled the
Culann strike in to roughly **one year**.

### 4.1b Tri-Tachyon does not shoot aid convoys **[RATIFIED 2026-08-01]**

**The problem, found in play:** two of the four enclaves sit inside Tri-Tachyon patrol space —
`maxios` beside **Tibicena** in Magec, `mairaath_abandoned_station1` beside **Port Tse** in
Mayasura. Ashen and Tri-Tachyon are VENGEFUL, so TT patrols and even trade fleets attacked relief
convoys on sight. Structural, not incidental: those two runs would be contested forever.

**The decisive argument is the morally-grey pillar, not convoy survival.** Tri-Tachyon gunning
down food convoys to refugee camps reads as cartoon villainy, and the Design Principles say no
faction is evil-by-default. There is no defensible TT reading of "we strafed the aid ships."

**Fiction:** TT has certainly worked out that these are recruiting runs. But shooting food convoys
over Mayasura in front of every independent trader in the system is a public-relations catastrophe
it cannot afford while it is *already* blamed for Omicron's attacks on Luddic worlds. Letting the
Crusade feed refugees costs Tri-Tachyon less than the footage would. Colder and more interesting
than either mercy or malice.

**Implementation [VERIFIED]:** one line — `MemFlags.FLEET_IGNORED_BY_FACTION` set to
`"tritachyon"` (config `relief.ignoredByFactionId`, `""` disables). Vanilla precedent:
`EconomyFleetRouteManager` uses the same flag to keep certain fleets off the pirates' list.

Three scoping decisions, all deliberate:
- **One faction, not the blanket `FLEET_IGNORED_BY_OTHER_FLEETS`.** Kanta's pirates share Magec
  with the maxios enclave, have no reputation to protect, and **still hunt these convoys.** Losses
  stay possible and the war-score damper survives.
- **The player is unaffected** and can always attack them, with the usual rep consequence. Not a
  plot shield.
- **No disguise.** The convoys fly the Crusade's own flag. Sean: the Crusade wants to send these
  openly, so the extortion-fleet sim-faction trick would have been wrong even if it were cheap.

### 4.1c Active-raid veto **[RATIFIED 2026-08-01]**

Relief convoys do not launch toward an enclave whose system hosts a live staged operation
(the `ExileRefugeeConvoyManager` pattern).

> **The Crusade's OWN raids are excluded from the veto.** This is load-bearing, not cosmetic. The
> Crusade raids Magec and Mayasura during Phases 2–3 — exactly where two of the four enclaves are —
> so counting Ashen raids would have silently cancelled the §4.1a off-phase relief for those two
> enclaves, undoing the change that was just made. *(Caught by Sean; the assistant had originally
> framed self-vetoing as a feature.)*

Checked at **launch only**. A convoy already most of the way there does not turn back — cheaper
than in-flight revalidation, and better characterisation than an aid convoy making a U-turn.

**Implementation:** clone the `ExileRefugeeConvoyManager` lifecycle shape — travel → orbit → apply
condition → depart → linger → clear. One fleet at a time, globally capped. Transponder on,
`MEMORY_KEY_MAKE_ALLOW_DISENGAGE` set, so the escort does not become a brawl generator. Ashen is
INHOSPITABLE (not hostile) to Independents, so transit through Corvus and Mairaath is safe
**[VERIFIED]**.

**Destinations:** the four refugee enclaves — `corvus_abandoned_station`,
`mairaath_abandoned_station1`, `yma_abandoned_station`, `maxios` — resolved via **station entity**,
never hardcoded market ids (econ-JSON loader convention, CLAUDE.md §7). Gilead is deliberately
excluded: it is Luddic Church territory, and a Crusade relief convoy there is a different (and
better) quest beat.

### 4.2 Condition: **Relief Shipment** (`exileac_relief_shipment`)

**[RATIFIED — ruling J2]** 60-day duration. `+1` Stability, `+1` Food, `+1` Supplies, **`-1` Crew**
(reduced from −2: −2 would zero out crew availability on a size-3 enclave). Player crew **purchase**
price **+50%**; selling untouched.

Pattern: `ExileRefugeeEncampments` — `market.getStability().modifyFlat(...)`,
`com.getAvailableStat().modifyFlat(...)`, `crew.getPlayerSupplyPriceMod().modifyMult(...)`, plus a
`MAX_DAYS` self-expiry backstop so a broken manager can never orphan the condition.

### 4.3 Sabotage — 1 attempt per month

See §5. Phase 1 runs **ordinary** sabotage only.

---

## 5. Sabotage subsystem

No fleets, no intel, no `RaidIntel` — a dice roll and two effects. Cheapest subsystem in the
director and the one that most directly expresses the Pather half of the Crusade's character.

### 5.1 Success odds **[RATIFIED — ruling C]**

> **⚠️ FORMULA DIRECTION CORRECTED AT IMPLEMENTATION (2026-08-01).** This read
> `baseChance × Π(modifier per FUNCTIONAL defensive industry)`. **That cannot produce the ratified
> numbers.** Sean's 25% for the outposts is the chance *with defenses intact*, so multiplying it
> down by every defense present would undershoot badly. Implemented the other way round:
> `baseChance` **is** the fully-defended chance, and every **non-functional** defensive industry
> multiplies it **up**. Same intent, correct arithmetic, and it *strengthens* the cross-system
> interaction this section wanted. **The ratified numbers are unchanged**; only the modifier's
> direction is. Flagged rather than changed silently.

```
chance = baseChance[market]                     // the FULLY DEFENDED chance, per market
       x defenseDownMult ^ (NON-functional defensive industries)
       capped at maxChance
```

Default `baseChance`: outposts **0.25**, heartland **0.02** — the author's stated odds by
construction, when every defense is up. `defenseDownMult` 2.0, `maxChance` 0.9. Defensive
industries are matched by spec **tag** (`grounddefenses`, `patrol`, `military`, `command`,
`station`) with vanilla ids as a fallback — tag-based because station tiers like
`battlestation_high` never match a bare id constant, and custom industries would misclassify
(the `OmicronRaidActionStage` lesson).

A raw Ground-Defense formula was rejected: **[VERIFIED]** Tibicena (size 5, grounddefenses +
patrolhq) and Eochu Bres (size 6, grounddefenses + patrolhq) sit close together on the
`PopulationAndInfrastructure.getBaseGroundDefenses(size)` × `Stats.GROUND_DEFENSES_MOD` curve,
nowhere near 25%-vs-≈0%.

Why the config-and-modifiers model is better:
- It hits the ratified odds precisely instead of approximately.
- It makes "if their station, base/HQ and ground batteries are up" **mechanically true**.
- **Cross-system interaction:** an Omicron or Black Fleet strike that disrupts Culann's Military
  Base opens a genuine Crusade sabotage window. Three directors, one sector.
- Fully player-adjustable, per the standing design goal.

**"Impossible" [RATIFIED — ruling J1]:** below a config floor (default 0.01) a target is skipped.
If *every* target is below the floor that month, the attempt is **skipped entirely** rather than
rerolled onto the best available target — the Crusade husbanding scarce operatives.

### 5.2 Ordinary sabotage effect **[RATIFIED — ruling D]**

Disable up to **N** (config, default 3) currently-functional industries drawn from a configured
**tag list**. Duration 60 days, **extending** rather than replacing an existing disruption (author
intent preserved). Never `population`. Never military — knocking out defenses is the *special*
sabotage's job, and letting ordinary sabotage do it would collapse the two tiers.

**Tag list [RATIFIED, amended]:** `mining`, `refining`, `heavyindustry`, `lightindustry`,
`farming`, `spaceport`. **`commerce` is DROPPED** — see the finding below.

> #### ⚠️ Finding: Commerce is a bad sabotage target **[VERIFIED 2026-07-31]**
>
> Sean asked whether Commerce can be knocked offline. **It can** — `commerce` is a normal
> `BaseIndustry` subclass (`TradeCenter`) with `disruptDanger = MEDIUM` in `industries.csv`, so
> vanilla already treats it as a legitimate disruption target. No crash, no engine weirdness.
>
> **But disrupting it helps the target.** `TradeCenter.apply()` ends with
> `if (!isFunctional()) unapply();`, and `unapply()` removes:
> - `+25%` colony income (`getIncomeMult()`) — invisible to the player on an NPC market; and
> - **the `-3` stability penalty** the industry imposes.
>
> So sabotaging Tibicena's Commerce would hand Tibicena **+3 stability**. That is the exact
> opposite of what sabotage should do.
>
> **Sean's related worry is resolved and is good news:** the "planet's marketplace" he was thinking
> of is the Open Market submarket, and `TradeCenter` only adds/removes it
> `if (market.isPlayerOwned())`. **NPC markets keep all their submarkets regardless of disruption**,
> so there is no risk of breaking a market's trade screen.
>
> Sean's instinct to flag it was right — for a different reason than he guessed.

**Coverage check after dropping `commerce`** — every target still has at least two valid industries:

| Market | Valid ordinary-sabotage targets |
|---|---|
| Culann | spaceport, refining, orbitalworks *(3)* |
| Eochu Bres | farming, megaport, lightindustry *(3)* |
| Tibicena | megaport, mining *(2)* |
| Port Tse | spaceport, mining *(2)* |

**[RATIFIED — ruling L]** Spaceport/megaport disruption craters accessibility, vanilla's single
biggest economic lever. Disabling Eochu Bres's Megaport for 60 days on top of a Crusade Strike is a
heavy compound hit. Use the `OmicronRaidActionStage` treatment: a **shorter duration and a lower
pick weight for the spaceport category** (`spaceportWeight`, `spaceportDisableDays`), config-driven,
so it is not the default pick every month.

### 5.3 Condition: **Crusade Strike** (`exileac_crusade_strike`)

**[RATIFIED — ruling E]** 30 days. Follows the `ExileCorsairActivity` pattern: supplies and fuel
always, plus the top-N import-dependent commodities, with **N and the magnitude in config** so
"−2 to all" remains reachable by setting N high.

Rejected: literal `-2` on every commodity. Vanilla's trade-fleet loss applies `-2` to *specific*
commodities (the `sh_loss…` temp mods), not the whole list, and blanket application on a size-6
Tri-Tachyon world would ripple sector-wide (Tri-Tachyon exports ships, rare metals and domestic
goods into the Core economy).

### 5.4 Special sabotage — last month of Phase 2

> ⚠️ **PARTLY SUPERSEDED by §3.2a (2026-08-08 war-clock reframe).** The **"Phase-4 branch"** at the
> end of this section — special-sabotage success routing to Phase 4 — is retired: the strike now
> fires on preparation completion, not on a die roll. **Q1 and Q2 both stand.** The sabotage
> subsystem itself stays; §3.2a proposes repurposing each Culann success as an accumulating
> preparation step. Read §3.2a first.
>
> **Sabotage is NOT governed by §3.2a's `max()` rule** — that is Phase-4-strike-only (Sean,
> 2026-08-08). See the disruption note below.

> #### 🟢 Disruption handling — **already correct, no change needed [VERIFIED 2026-08-08]**
>
> Sean asked whether skipping already-disrupted targets should be a live check or a set of
> hardcoded post-strike exemption windows (Culann Refining 120d, Military Base / Orbital Works
> 300d). **Neither is needed: the behaviour already ships.**
>
> `AshenSabotage.java:284` filters candidates on `!ind.isFunctional()`, and vanilla
> `BaseIndustry.isFunctional()` (line 482) opens with `if (isDisrupted()) return false;`. **A
> disrupted industry is therefore already excluded from the sabotage picker** — after the Culann
> strike, Refining, Orbital Works and Military Base drop out of the candidate list until they
> recover, and the Crusade's operatives go elsewhere on their own. The special-target path filters
> the same way (`"no functional target available this cycle"`, line 229).
>
> **Why the hardcoded exemption was rejected**, recorded so it is not re-proposed: it is a second
> source of truth that silently desyncs the moment a strike duration changes in config; it is blind
> to disruption from Omicron raids, Black Fleet ops and vanilla events; it needs new saved state for
> "when did the strike happen"; and the proposed 120/300 against actual durations of 180/360 would
> still leave 60-day windows landing on a saturated target. The live check costs a `MemoryAPI`
> lookup over ~4 candidates, 1–2 times a month — not on any hot path.
>
> **Principle: read the state, don't mirror it.**
>
> **One fix made (2026-08-08).** `extendDisruption()` was `setDisrupted(getDisruptedDays() + days)`
> — **additive**. Harmless today *only* because the picker never hands it a disrupted industry, so
> the compounding was unreachable by accident rather than by design. Changed to vanilla's
> `setDisrupted(days, true)`: identical behaviour now, but the safety is explicit instead of
> depending on a filter two methods away. Loosening that filter would otherwise have started
> stacking sabotage toward permanent shutdown.

**[AUTHOR]** One attempt against one of: Eochu Bres Megaport, Eochu Bres Farming, Culann Orbital
Works, Culann Military Base. **At least 25%** success even with all market defenses operative.
Success knocks the targeted industry out for **90 days** and applies vanilla **Recent Unrest**.

**Target selection [RATIFIED — O1]:** **weight the Culann targets higher, and let war score tip
selection.** Early in the war the Crusade probes Eochu Bres at base odds; as war score matures it
commits to Culann, the war-winning target. This is both the pacing dial and good fiction — the
Crusade goes for the kill only when it believes it can land one.

**Escalating odds [RATIFIED — O1]:** the special-sabotage chance **climbs with war score**, starting
at the author's 25% floor and rising toward roughly 50% as the Crusade matures. A war that keeps
failing sees the Crusade commit more operatives — which both fits the fiction and guarantees the
climax eventually arrives instead of stalling on cold dice.

**Pacing target [RATIFIED]:** the Culann strike should land in the **2–3 in-game year** band. Sean:
not too soon, but not a coin-flip the player may never see. See §5.5 for the arithmetic behind the
starting values.

**[VERIFIED]** The vanilla method is
`com.fs.starfarer.api.impl.campaign.econ.RecentUnrest.get(market).add(points, reason)` — the same
call `MarketCMD.doBombardment` uses; Exiles already uses it in `OmicronRaidActionStage`.

**[RATIFIED]** Route it through `ExileUnrestGuard.isBattered(market)` exactly as Omicron
bombardment does. Recent Unrest stacks uncapped and decays only 1 point per 90 days; Hybrasil is
already double-teamed by Omicron ops, and repeated unadjusted adds are the known decivilization
path (the Gilead lesson from playtest-4).

**Phase-4 branch [RATIFIED — Q1/Q2]:** success against the Culann **Orbital Works** or **Military
Base**, *and* Phase 4 has not already **succeeded** → skip Phase 3, go to Phase 4. Otherwise Phase 3.

- **Q1:** a *failed* Phase-4 assault does **not** consume the allowance. Only a **success** closes
  the loop. A repelled assault costs war score, and the Crusade may try again.
- **Q2:** nanoforge presence does **not** gate Phase-4 entry. It only decides whether there is
  anything to steal on success — which also cleanly covers the player, another faction, or
  Nexerelin having removed it first.

### 5.5 Phase-4 pacing arithmetic **[RETIRED 2026-08-08 — superseded by §3.2a]**

> ⛔ **This entire section is dead.** It existed only to force a three-term probability product into
> a 2–3 year window. Under the §3.2a reframe the strike arrives on **preparation completion**, so
> there is no product to tune — the pacing target becomes a schedule. **The 2–3 year (preferably
> ~2) target survives; the arithmetic below does not.** Retained for the reasoning trail only. Do
> not implement or tune against these numbers.

Why the §5.4 levers exist. A full cycle (Phase 1 + 2 + 3) averages **~240 days**, so ~1.5 war-cycles
per 365-day cycle. The chain to a Phase-4 *success* is:

```
P(success per war-cycle) = P(special sabotage lands)
                         × P(target was Culann)
                         × P(the assault then cracks Culann)
```

**Original values: far too slow.** `0.25 × 0.50 × ~0.55 ≈ 6.9%` per cycle → ~14 cycles ≈ **9 in-game
years**. The player would almost never see it.

**Target:** ~65% cumulative by year 2.5 (≈3.8 war-cycles) needs **~24% success per cycle**, i.e. a
**trigger** probability near **44%** once the assault's own ~55% is accounted for.

**Starting values to hit that (all config, tune live):**

| Lever | Early war | Mature war |
|---|---|---|
| Special-sabotage chance | 0.25 (author floor) | ~0.50 |
| P(target is Culann) | low — probes Eochu Bres | ~1.0 once war score crosses its threshold |
| Assault success | uncertain by design (~0.5–0.6) — author: *"by no means assured"* | same |

The back-loading is deliberate and matches Sean's "not too soon": early cycles are near-zero for a
Culann trigger, and the odds converge on the target band around years 2–3. A repelled assault costs
war score, which naturally spaces retries without needing a cooldown timer (§12 O2).

> **These numbers are arithmetic, not playtest.** Assault success in particular is a guess until
> Pass 7 runs; expect to retune after the first live test.

---

## 6. Phase 2 — Interdiction Raids · Phase 3 — The Rehearsal

> ⚠️ **Phase 3 was redesigned 2026-08-09 — see §6.4.** It is no longer a planetary blockade. It is a
> full-dress rehearsal of the Culann operation against Tibicena, then Port Tse. Interdiction raids
> **do not run in Phase 3** any more; sabotage does.

### 6.1 Interdiction raids (**Phase 2 only** — amended 2026-08-09)

**[AUTHOR]** Phase 2: 60–90 days, one raid at a time, each lasting 30 days, targeting hostile ships
near Tri-Tachyon worlds. They run from more powerful fleets and retreat if reduced by more than
50%. Phase 3: one interdiction raid per month, carrying a blockade fleet.

**Implementation:** `BlackFleetCommerceRaidStage` almost verbatim — presence-only op,
`abortFraction 0.5`, `presenceDays` 30, condition lands on day 5 and lifts on withdrawal. Estimated
~80% code reuse.

**[RATIFIED — ruling J5] Target discipline is mandatory and Tri-Tachyon-only.** Hybrasil contains a
pirate market (Donn, size 4) and an independent one (Cethlenn, size 4), and Ashen is HOSTILE to
Pirates. Author's reasoning: *the Crusade doesn't like pirates at all, but they aren't worth risking
their ships and crews over.* Use the `OmicronRaidActionStage` target-faction filter pattern, which
must override **both** `getTargets()` and `canRaid()` — vanilla's opportunistic-raid check accepts
any hostile market otherwise.

### 6.1a The loiter lever — how Omicron escalation is actually controlled **[RATIFIED 2026-08-01]**

**Mechanism finding, made during the Pass 4 build and load-bearing for the whole ladder.** Omicron
has **two** response mechanisms against a raid, and only one of them is FP-gated:

| | Trigger | What responds |
|---|---|---|
| **A. Station sleeper** — `OmicronTriTachyonDefenseManager.activateImminentStationDefenders()` | An op-route fleet closing within `ttDefenseStationActivationRange` (**1,500u**) of the station anchor. **No FP check whatsoever.** | The dormant complement: Port Tse 35–55 FP, Tibicena 45–65 FP |
| **B. Strategic response** — `handleThreat()` | Op spawn FP ≥ `responseMinThreatFp`: **200** at the outposts, **350** in Hybrasil | A dispatched Nexus response; at 350 in Hybrasil, also Culann's 180–220 FP detachment |

Sean's requirement — *no Omicron for a while; Port Tse and Tibicena once raiding heats up; Culann's
ships only for a Phase-4 strike or a major late-stage pirate assault* — therefore **cannot be
delivered by spawn FP alone**, because the station complements he named are gated on proximity.
What controls them is where the raiders park.

`BlackFleetCommerceRaidStage` loiters at `system.getCenter()`, thousands of units off the markets,
so a straight copy would have woken the sleepers *never* rather than *later*. The ratified fix
makes that position the lever:

- **war score < `boldWarScore`** → loiter at the system centre. No Omicron of any kind.
- **war score ≥ `boldWarScore`** → loiter on the focus market, inside the 1,500u radius, waking
  that station's complement. By then `scoreFpMult` has also lifted spawn FP toward 200, arming
  tier B behind it.
- **never Culann's detachment** — spawn FP 130–190 × `getFpMult()` tops out near 285, well under
  the 350 Hybrasil threshold. That confrontation stays reserved for Phase 4 and for Black Fleet
  assaults (350–500 FP), which already trip it with no new code.

`boldWarScore` **= 0.25** (Sean, 2026-08-01). The FP band is deliberately capped **under 200** so
the early-war tier is guaranteed silent — the check is `threatFp < responseThreshold`, so a flat
200 would fire.

**The token is latched once, at arrival** (`ActionStage.updateRoutes()` is called only from
`notifyStarted()` — verified in the API sources). Each new raid re-evaluates, so the
campaign-scale ladder holds; a single op that crosses the threshold mid-presence does not turn
bold. Re-calling `updateRoutes` mid-stage was rejected as leaving a proven vanilla seam for a
marginal gain.

### 6.2 Condition: **Crusade Harassment** (`exileac_crusade_harassment`)

**[AUTHOR]** Identical in effect to Corsair Activity; applies to **Tri-Tachyon markets only** within
the raided system.

> #### Ruling E.c — should Corsair Activity be genericized and shared?
>
> Sean asked whether this is an opportunity to make `ExileCorsairActivity` more generic in its
> player presentation so one condition serves both factions.
>
> **Recommendation: share the mechanics, not the identity.** Three options were considered:
>
> 1. **One shared condition id for both directors.** Rejected. Both are presence-scoped (applied
>    day 5, lifted on withdrawal), and the Black Fleet also targets `tritachyon` — so a BF commerce
>    raid and an Ashen interdiction raid can overlap in the same system. Whichever withdrew first
>    would remove the condition and silently cancel the other's effect. It also forces the tooltip
>    to become uninformative ("someone is hunting shipping here"), which **loses** the discoverability
>    we just ratified in J4: naming the culprit in the tooltip is how the player learns who is
>    strangling the system.
> 2. **One id + a stored `sourceFactionId` driving the display text.** Elegant, but reintroduces the
>    same lifecycle collision — one condition instance with two owners.
> 3. **✅ Shared abstract base class, separate ids.** All mechanics (accessibility mod, availability
>    picking, timer, tooltip scaffolding) move to a base class; `ExileCorsairActivity` and
>    `ExileCrusadeHarassment` become thin subclasses supplying id, name, flavour text, and constants.
>    Separate `market_conditions.csv` rows, independent lifecycles, faction-appropriate text.
>
> **Sequencing:** build `ExileCrusadeHarassment` as a straight copy in Pass 4, confirm it works
> in-game, then extract the base class in **Pass 5** (the extraction pass). Refactoring shared
> infrastructure *while* adding a feature is how working systems break. The Hegemony/League content
> Sean flagged in F.d will make this the third consumer, which is exactly when extraction earns its
> keep.

Note the open Black Fleet question this sidesteps: Corsair Activity's system-wide blanket reaches
refugee enclaves as an area effect. Crusade Harassment is Tri-Tachyon-only, so the issue cannot
arise here.

### 6.3 Tri-Tachyon system bounty

**[AUTHOR]** During an interdiction raid, Tri-Tachyon posts a system bounty paying credits and
Tri-Tachyon reputation per hostile ship destroyed.

**Implementation:** direct port of `BlackFleetRaidIntel`'s bounty (itself an Omicron port) —
`bountyPerFrigate` × hull size × player involvement, plus `SYSTEM_BOUNTY_REWARD` rep, deferred
payout popup, `INTEL_BOUNTY` tag. Payer is Tri-Tachyon. This is the heroism vector: vanilla pays
nothing for raid defense.

### 6.4 Phase 3 — **The Rehearsal** **[RATIFIED 2026-08-09 — replaces the blockade design]**

> **This section is the authority for Phase 3.** The previous planetary-blockade design is retained
> below as **§6.4a** for its verified engine research only; its *design* is superseded. Read §3.2a
> (the war-clock reframe) first — this section is its Phase-3 consequence.

#### The idea

**Phase 3 is a full-dress rehearsal of the Culann operation, run against a softer Tri-Tachyon
target.** The Crusade is not harassing Tri-Tachyon in Phase 3; it is *practising the heist*.

Sean's framing of the war as a whole: Phase 1 gathers strength, Phase 2's raids test Tri-Tachyon's
defenses and train crews for deep-space engagements, and Phase 3 rehearses the operation itself —
the crews who will steal the nanoforge learn the job on a live target first.

**Why this shape.** The real operation has three parts: hold the gates so reinforcements cannot
arrive, crack the station, and extract something heavy under fire. Phase 3 rehearses **all three**,
so Phase 4 is the same operation at full scale against the real target. The player who watches two
rehearsals knows exactly what Phase 4 is the moment it begins — which makes §7.0's telegraph
**emergent** rather than bolted on, and is a better climax than a surprise.

It also answers the objection that killed the old design (*"why would a cause dedicated to
annihilation stop at a blockade?"*): they are not stopping. They are setting up. And the player can
wreck the setup.

#### Fiction

The Crusade already tried the direct approach. The **Knights of Gilead** threw a large assault at
Hybrasil around **Cycle 204** — after Omicron's devastation of Gilead in 199, and a couple of years
before campaign start — and were **repulsed**, though not before doing lasting damage. That failure
is what drew Luddic Path elements into the movement, and it is why the Crusade now prepares
deliberately instead of charging in again. See §6.4b for the world-state changes that make this
visible on the map.

**[RATIFIED] The war clock does not start before the player's start.** The old assault earns no
preparation credit; it is backstory that explains the countdown, not part of it.

#### The three-part force

Mirrors §7.1's Culann order of battle at rehearsal scale. The player learns to read it here.

| Element | Role | Notes |
|---|---|---|
| **Hussar blockade fleets** | Hold the system's jump points | `BlockadeFGI` with the **default** `FGBlockadeAction` (system denial), **not** `FGBlockadePlanetAction`. See §6.4a. |
| **Assault group** | Crack the orbital station | Reuse the `BlackFleetAssaultStage` shape, scaled down. |
| **Small attack fleets** | Screen against Tri-Tachyon patrols | The §7.1 harassment-fleet role. |

Fleet strength scales with war score (**"current FP levels"**), so the rehearsal visibly escalates
between cycles. That escalation is itself a telegraph.

#### Targets — Tibicena, then Port Tse **[RATIFIED]**

Escalating, **not** randomised. Both markets are already in the interdiction pool, so Phase 3 needs
**no new target geography and no new feasibility work**.

| Order | Market | System | Size | Fleet industry | Response |
|---|---|---|---|---|---|
| 1st | **Tibicena** | Magec | 5 | `patrolhq` | 2 small fleets |
| 2nd | **Port Tse** | Mayasura | 4 | `militarybase` | 2 small + 1 medium |

**[VERIFIED]** Both carry `mining` with rich ore, both have an `orbitalstation` (weakest station
tier) and both have `grounddefenses` — so the **fleet-generating industry is the only meaningful
difference**, which is what makes Port Tse the harder target despite being smaller. *(Sean confirmed
the fleet counts against the Starsector wiki; the size-vs-industry ambiguity I raised is resolved by
that isolation.)*

**Gate the escalation on war score, not cycle count** — reusing §5.4's established pattern, so the
progression tracks Crusade maturity, self-adjusts if durations are retuned, and never de-escalates.

#### Effects of a successful rehearsal **[RATIFIED]**

1. **Mining disrupted 30 days.**
2. **The fleet-generating industry disrupted 30 days** — `patrolhq` at Tibicena, `militarybase` at
   Port Tse. Look up whichever the market has. This mirrors the Culann strike's Military Base
   knockout at rehearsal scale, and 30 days is short enough that the market is not left as prey for
   the Black Fleet or pirates ("wounded, not dying", per the §3.2a Heavy Batteries logic).
3. **Recent Unrest**, routed through **`ExileUnrestGuard.isBattered()`** like everything else.
4. **The ore theft is flavour text only** — no cargo mechanic. Building a hauling system returns
   nothing playable.
5. **Vanilla `Conditions.BLOCKADED`** applies while the Hussars hold the gates.

#### Conditions — what Phase 3 does *not* apply **[RATIFIED]**

**Crusade Harassment does not apply in Phase 3.** Vanilla `Blockaded` replaces it.

This is a safety improvement, not just a simplification: Crusade Harassment is the
trade-strangulation condition that stacks invisibly with the Black Fleet's Corsair Activity to
double the supplies/fuel hit to −4 (§14.5). Vanilla `Blockaded` is **accessibility-only**, so the
doubled commodity hit cannot occur. **Phase 3 is therefore *less* exposed to the shared-market
stacking problem than Phase 2 is.**

**[VERIFIED]** `BlockadeFGI.applyBlockadeCondition()` stamps every `targetFaction` market in the
system, and **Magec and Mayasura each contain exactly one Tri-Tachyon market** — Tibicena and Port
Tse. The system-wide apply therefore hits precisely the intended market. No over-spread.

#### Interdiction raids and sabotage in Phase 3 **[RATIFIED]**

- **Interdiction raids: REPLACED.** Phase 2 is the raiding phase. The rehearsal's own screen fleets
  cover the patrol problem. This keeps the concurrent-op budget sane.
- **Sabotage: KEPT** at §6.5's two attempts per month. It is the Crusade's constant background
  pressure and the main preparation source between rehearsals.

#### War score — the player's lever **[RATIFIED]**

A successful rehearsal **advances** preparation by **more than a relief fleet**; a failed one
**sets it back**. Since war score *is* the preparation meter under §3.2a, this makes the rehearsal
the player's principal lever on when the climax arrives — **pillar 4, with no quest content
required**.

**Suppression uses diminishing returns, not a hard floor [RATIFIED].** Repeated failures make the
Crusade commit harder next time, so player suppression buys **delay, not prevention**. Fits §5.4's
precedent of an escalating Crusade, and keeps §12 O4's "the climax must eventually fire" intact.

**Tuning target — this is a testable number, not a feel:** a focused player buys approximately
**one** extra cycle; a very focused player still buys only one.

#### Pacing **[RATIFIED — settles the §3.2a topology question]**

**Phase 4 replaces Phase 3 in the final cycle** (topology T1 — the existing `advancePhase()` branch
at `AshenWarDirector.java:240-247` already has this shape; only its condition changes from a
sabotage roll to preparation-complete).

| Path | Cycles | Elapsed |
|---|---|---|
| Undelayed — cycles 1–2 full, cycle 3 stops after Phase 2 | 3 | 240 + 240 + 165 ≈ **645d ≈ 1.8 yr** |
| Player delays one cycle | 4 | + 240 ≈ **885d ≈ 2.4 yr** |

Both inside the ratified 2–3 year band, with the preferred "~2-ish" between them.

**The player therefore sees exactly two rehearsals — Tibicena, then Port Tse — and the third time
the Crusade assembles that force, it goes to Culann.**

#### What happened to the docking dialogue **[relocated, not cut]**

§6.4a's four-option docking dialogue (Leave / Threaten / Claim Crusade business / passive trade
penalty) is **not built for Ashen**. With the Hussars at jump points rather than in orbit over a
market, a docking interception no longer fits the fiction.

It remains **ratified content for the Hegemony–Persean League war** (rulings J3 and F.d — Sean
explicitly wants the >+49 reputation door reused there). It moves with the blockade to that
director, which does not yet exist. **This is a shelf, not a transfer:** nothing receives that code
today, and it will not run in anyone's game until the Hegemony/League war is built.

This also retires **Pass 6b, the highest-risk pass in the plan** — the one that could lock a player
out of a market permanently. That risk leaves the Ashen director entirely.

### 6.4b Hybrasil world-state changes — the repulsed assault **[RATIFIED 2026-08-09]**

Makes the backstory visible from the first time the player flies through Hybrasil, and gives the
countdown a causal origin.

- **Remove the `solar_array` condition from Eochu Bres.**
- **Alter the orbital mirror entities on the Hybrasil map** to show battle damage.

**[VERIFIED] What removing `solar_array` actually does.** It is a market *condition*
(`com.fs.starfarer.api.impl.campaign.econ.SolarArray`), not an industry:

- **`FARMING_BONUS = 2`** — a flat +2 supply bonus to `FARMING` (or `AQUACULTURE`).
- Suppresses **`HOT`** and **`POOR_LIGHT`**.

Eochu Bres is size 6, `tundra`, `habitable`, `farmland_poor`, with a `farming` industry — so removal
is a genuine cut to Tri-Tachyon food production. **That feeds the planned "general food shortage
threatens stability" Phase-1 feature (CLAUDE.md §6) rather than fighting it.**

**One unknown to check in game:** whether `POOR_LIGHT` un-suppresses and appears as a visible
condition on the market.

**Build note:** this is a post-econ-load patch in the existing Gilead / Asher / Nachiketa family
(`ExilesModPlugin`), **entirely separable from the Phase-3 build** and safe to do at any time.

---

### 6.4a Blockade — Hussar Fleet — **SUPERSEDED DESIGN, RETAINED RESEARCH**

> ⛔ **The design in this section is superseded by §6.4** (Sean, 2026-08-09). It is retained because
> its engine research is **verified and still load-bearing**: the Hussar element of the rehearsal
> uses `BlockadeFGI`, and the vanilla-blockade findings below are what make that nearly free.
>
> **Two corrections carried forward into §6.4:**
> 1. **Use the default `FGBlockadeAction`, not `FGBlockadePlanetAction`.** The default builds its
>    points from `params.where.getEntities(JumpPointAPI.class)` — fleets hold the gates. The
>    Planet variant sets `primary = specificMarket.getPrimaryEntity()`, which is vanilla's *siege*
>    action and the source of every behaviour problem in §14.8.
> 2. **The accessibility penalty is already system-wide and independent of the action.**
>    `applyBlockadeCondition()` iterates `Misc.getMarketsInLocation(getTargetSystem(), targetFaction)`
>    and **never consults `specificMarket`**. So the "station denial vs system denial" distinction
>    this section agonised over **does not exist economically** — both produce the identical penalty,
>    and `specificMarket` only decides where the fleets park. Switching actions is one line and
>    makes the `doNotAttack` override unnecessary.

### 6.4a.1 Original design **[SUPERSEDED]**

**[AUTHOR]** One Hussar Blockade Fleet accompanies each Phase-3 interdiction raid. It travels to the
**least-defended Tri-Tachyon market** and orbits until reduced below 50% strength, threatened off by
the player, or the raid ends. While in orbit: an additional **−100% accessibility** on that market.

**Dialogue options [AUTHOR]:**

| Option | Result |
|---|---|
| **Leave** | Abort the docking attempt; return to the system map. |
| **Threaten** | Combat with the blockade fleet — *unless* the player is significantly stronger, in which case the player loses **3** Ashen relation and the blockade withdraws. |
| **Claim Crusade business** (requires Ashen rep **> +49**) | Proceed to the market; the blockade stays. |
| *(passive)* | Trading at a blockaded market costs **5** Ashen relation, **once per dock**. |

**[RATIFIED — ruling J3]** The >+49 option being unreachable until Phase-2 quest content exists is
**desirable, not a gap**: it is a visible door the player can unlock by improving standing. Sean
explicitly wants this reused for the Hegemony and Persean League.

**Implementation [VERIFIED as feasible]:**
- Interception via `CampaignPlugin.pickInteractionDialogPlugin(SectorEntityToken)` returning a
  custom `InteractionDialogPlugin`. This is the CLAUDE.md-endorsed path (a runtime-loaded
  interaction plugin, **not** a new rules.csv command — those must be precompiled into a JAR).
- Hand-off after "Leave"/"Claim business": set a short-expiry market-memory flag that
  `pickInteractionDialogPlugin` checks and skips, then
  `Global.getSector().getCampaignUI().showInteractionDialog(new RuleBasedInteractionDialogPluginImpl(), entity)`.
  Without the flag the pick would re-fire and loop.
- "Threaten" → combat via `FleetInteractionDialogPluginImpl` + `FIDConfig`.
- Trade penalty via `ColonyInteractionListener.reportPlayerMarketTransaction`.

**Risk and required mitigations [RATIFIED]:**

> This component **hijacks market interaction**. A bug means the player cannot dock at a
> Tri-Tachyon world — potentially permanently if a flag or condition orphans. It is the most
> dangerous thing in this spec and gets its own isolated build pass and its own config kill-switch.

1. The "is this market blockaded" check must be evaluated **live every time** (blockade fleet
   alive, in orbit, in-system, op still active) and never cached in a condition or memory flag that
   can outlive the fleet.
2. Hard self-expiry on the accessibility effect, independent of the manager.
3. Trade penalty needs a **once-per-dock** guard — the listener fires per transaction, so selling
   three cargo stacks would otherwise cost 15 rep.

**[RATIFIED — ruling F] "Other fleets will be turned away" is CUT.** Physically intercepting every
NPC fleet approaching a market means per-frame work fighting vanilla fleet AI, and produces visibly
janky results. The −100% accessibility **is** the abstraction — vanilla models blockades exactly
this way. The Ashen fleet in orbit plus ordinary vanilla hostility supplies the visible drama free.

**[RATIFIED — ruling F.d] Design for reuse from day one.** Sean wants this same tactic in the
Hegemony–Persean League war (that director is not built yet), as light on resources as reasonable
while staying flexible — e.g. the Hegemony or League can afford a stronger fleet held for longer
than the Crusade could. He has delegated implementation detail.

> #### 🟢 MAJOR FIND — vanilla already has a complete blockade system **[VERIFIED 2026-07-31]**
>
> Discovered while checking Sean's accessibility question. Vanilla ships:
>
> | Class | What it gives us |
> |---|---|
> | `intel.group.BlockadeFGI extends GenericRaidFGI` | Full lifecycle: gather → travel → blockade → end. Owns fleets, intel, and condition apply/unapply (`applyBlockadeCondition` / `unapplyBlockadeCondition` / `notifyEnding`). |
> | `intel.group.FGBlockadeAction` + `FGBlockadePlanetAction` | The blockade behaviour itself, including explicit player handling and `NPCHassler` for turning fleets away. |
> | `econ.Blockaded` (market condition) | The accessibility penalty, tooltip, and lifecycle — driven by `blockade.getAccessibilityPenalty()`. |
> | `intel.group.PerseanLeagueBlockade extends BlockadeFGI` | **A worked example subclass — the Persean League blockading a target.** Precisely Sean's F.d use case. |
>
> `FGBlockadeParams` exposes exactly the knobs the spec needs:
>
> ```java
> public StarSystemAPI where;
> public MarketAPI  specificMarket    = null;   // "the least-defended Tri-Tachyon market"
> public float      accessibilityPenalty = 0.6f; // set 1.0f for the spec's -100%
> public String     targetFaction     = Factions.PLAYER; // settable -> "tritachyon"
> public String     patrolText        = null;
> public boolean    doNotGetSidetracked = true;
> ```
>
> …with `duration` as a constructor argument. **Sean's "stronger fleet, held longer" flexibility for
> the Hegemony/League is therefore just parameters, not code.**
>
> **What this changes:** Pass 6 no longer builds blockade infrastructure. It builds *only* the
> player-interaction layer (the four-option dialogue) on top of vanilla's blockade. Fleet
> management, intel, the condition, and cleanup all become vanilla's problem — which is both the
> lightest-resource answer available and the most compatible one.
>
> **Consequences to fold in:**
> - The planned `exileac_crusade_blockade` condition is probably **unnecessary**; vanilla `Blockaded`
>   may serve directly. Confirm before writing it (§8).
> - "Other fleets turned away", which ruling F **cut** as too expensive to build, may come **free**
>   via `NPCHassler`. Re-evaluate — the ruling was about build cost, and that cost may be zero now.
> - `targetFaction` defaulting to `Factions.PLAYER` and `PerseanLeagueBlockade`'s ties to
>   `HostileActivityEventIntel` mean the vanilla usage is **player-colony-directed**. Reusing
>   `BlockadeFGI` for an **NPC-vs-NPC** blockade is the load-bearing assumption and is **not yet
>   verified**. See §12 O3.
>
> **Confidence:** the API surface is verified by reading; the *behaviour* (player interaction path,
> NPC-vs-NPC viability) is not. This is a strong lead that re-plans Pass 6, not a settled design.
> A focused feasibility check is the first task of Pass 6.

If `BlockadeFGI` proves unusable for NPC-vs-NPC, fall back to the bespoke plan above: the
interception plumbing is naturally faction-agnostic — one `CampaignPlugin`, one dialog plugin,
faction-driven text and rep values — built generic with only the Ashen consumer shipped.

### 6.5 Phase 3 sabotage

**[AUTHOR]** Two ordinary sabotage attempts per month (up from one).

**[RATIFIED 2026-08-09] Sabotage is KEPT in the redesigned Phase 3**, unchanged at two attempts per
month. It is the Crusade's constant background pressure and the main preparation source between
rehearsals. Interdiction raids were replaced (§6.4); sabotage was not.

---

## 7. Phase 4 — Culann Strike (once per campaign)

**[RATIFIED — rulings A and B]** Phase 4 is now a **single, one-time Culann operation**. The Eochu
Bres attack and all saturation bombardment have left this director entirely (§7.4).

### 7.0 The telegraph — making sure the player can be there **[RATIFIED 2026-08-01 — O4 hybrid]**

Sean's concern: a once-per-campaign climax that fires on organic odds risks going off while the
player is surveying the far reaches. *"That adds life to the sector, but probably in a frustrating
way."*

**The framing that resolves it is not "organic vs quest-gated" — it is "can the player see it
coming and choose to be there."** An event the player misses because they were never told is
frustrating. An event they miss because they weighed it against something else and chose wrong is
a *story*. The difference is a telegraph, and this director already has a natural one.

Proposed, all cheap:

1. **The successful Culann special sabotage is the one sabotage that is NOT silent.** Everything
   else in §3.4 stays quiet, but Tri-Tachyon would absolutely publicize a strike on Culann's
   Military Base or Orbital Works. That is the warning shot, and it is already the Phase-4 trigger.
2. **Phase 4 posts its intel at launch, not on arrival** — unlike ordinary raids (§3.4). A fleet
   that size mustering is not secret, and the vanilla assemble stage already provides the delay.
3. **A muster window between trigger and arrival**, config-tuned so a player anywhere in the sector
   can realistically make the trip. Long enough to cross the map is the design target; the exact
   number is a live-test question, not something to guess here.

Result: the war keeps its own momentum (pillar 3 — *the sector acts whether or not the player is
watching*), the player gets a real window and a map marker, and skipping it becomes a decision with
a cost rather than a thing that happened offscreen.

### 7.1 Fleet strength **[RATIFIED — ruling G]**

**[VERIFIED]** Vanilla system-defense fleets are generated from market size and industries at spawn
time. There is no stable FP number to read, so "75% of the Capital Assurance Reserve" cannot be
computed — it has to be a tuned constant.

Benchmarks in the mod: Omicron's Culann strategic reserve is **180–220 FP**; Black Fleet assaults
run **350–500 FP**; Omicron's strategic-response threshold at Culann is **350 FP**.

**Starting values (config, tuned live):**
- 2 × attack fleet at **250–350 FP** each
- 2–3 × harassment fleet at **80–120 FP** each

At this scale the op clears Omicron's 350 FP Hybrasil threshold — which is precisely what §7.3
wants, for free.

### 7.2 Culann outcome **[AUTHOR — step 3 SUPERSEDED by §3.2a ruling 3, 2026-08-08]**

> ⚠️ **Step 3's flat "five industries, 90 days" is replaced** by Heavy Batteries 30d / Spaceport 60d
> / Refining 180d / Orbital Works 360d / Military Base 360d, applied **whether or not the nanoforge
> is present**. Same five industries, differentiated durations — Heavy Batteries recovers fastest by
> design ("wounded not dying"). Restating the "extend by 90 if already offline" rule per-industry is
> still open. Steps 1, 2 and 4 stand — and **ruling K's UNDER REVIEW flag is resolved: STOLEN**.
> See §3.2a.

On defeating Culann's defenses:
1. A surviving assault fleet enters orbit around Culann.
2. The planetary bombardment animation plays.
   → **[VERIFIED]** `MarketCMD.addBombardVisual(entity)` — already used by `OmicronRaidActionStage`.
   Self-guarding (no-op unless the player is in-system) and self-terminating. **This is the visual
   only — no `doBombardment` call, no market-size loss, no pollution.**
3. Culann's **Spaceport, Refining, Orbital Works, Heavy Batteries and Military Base** go offline for
   90 days (extend by 90 if already offline). **[VERIFIED]** all five exist on Culann as written.
4. The Corrupted Nanoforge is removed from Culann's Orbital Works and added to the Elpis Orbital
   Works in Providence. **[RATIFIED — ruling K: STOLEN, flagged UNDER REVIEW]** — see §7.5.

### 7.3 Omicron interplay **[RATIFIED — ruling H]**

**Diverting or recalling in-flight Omicron raids is rejected.** Cancelling live vanilla `RaidIntel`
operations mid-stage is fragile and save-risky.

**Half of the intent is already built [VERIFIED]:** `OmicronTriTachyonDefenseManager` will detect
the Ashen `RaidIntel` on Hybrasil and commit Culann's 180–220 FP strategic detachment against it,
automatically, with **zero new code**.

> #### Sean's memory-flag concern — answered, and the flag is no longer needed
>
> Sean was uneasy about a sector-memory flag for suppressing Omicron Luddic launches, having heard
> that many memory flags slow the game down.
>
> **The cost concern is real but aimed at the wrong thing.** `Global.getSector().getMemoryWithoutUpdate()`
> is a `MemoryAPI` backed by a HashMap; reading or writing a handful of boolean keys is free and
> allocates nothing. What actually costs is (a) **rules.csv rule matching** — the engine evaluates
> rule conditions against the memory map, so a large *rules file* is the expense, not the memory
> size; (b) storing **objects** rather than primitives in memory, which bloats saves via
> serialization. Exiles already relies on many such flags (`$exilebf_raids_enabled`,
> `$exilebf_op_cooldown`, `$exilebf_grudge`, `$exiles_refugee_convoy`, and several cooldown keys).
> One more boolean would not be measurable.
>
> **But there is a strictly better option that needs no flag at all.** `OmicronDirector` can simply
> ask the intel manager whether a live Ashen `RaidIntel` is targeting Hybrasil — the exact pattern
> `ExileRefugeeConvoyManager.getSystemsWithActiveRaids()` already uses. That runs only at Omicron's
> raid-launch cadence (~every 8 days), holds no saved state, survives save/load, self-corrects if
> the assault ends early, and cannot orphan. **Recommendation: use the intel scan, drop the flag.**
>
> **Deferred:** with Phase 4 now once-only and quest-adjacent, this feature is low-priority Pass-7
> work rather than core director behaviour.

**[NOTED — Sean, ruling H.c, future direction]** When the general War Director exists, consider
making the **Providence markets addable Omicron targets**, gated behind a player quest: Tri-Tachyon
hires the player to find where the Ashen Crusade operates from, then passes that target data to
Omicron. Recorded as author direction; not scheduled, not built.

### 7.4 Content that has LEFT this director **[RATIFIED — rulings A and B]**

Moved out of the war director and into future quest content:

- **The Eochu Bres attack** and its **saturation bombardment**. Sean: *"Any such attack will only
  occur after the Player completes a quest at the end of a quest chain that initiates it. So it
  becomes no-longer related to the Ashen Crusade's war director."*
- **Any second major assault.** Follow-up major attacks are linked to player-completed quests.

This removes `MarketCMD.doBombardment`, `CoreImmigrationPluginImpl.reduceMarketSize`, permanent
Pollution, and the market-size floor from the director entirely — the single largest stability and
"controlled collapse" risk in the original spec. Pass 7 shrinks accordingly.

**[NOTED — Sean, ruling A.b, canon direction]** On CLAUDE.md priority #4: the controlled-collapse
guardrail stands for *ambient NPC activity*, but if the player **joins Midan or the Ashen Crusade**,
genuinely catastrophic events should be allowed to follow — one faction wants Tri-Tachyon destroyed
and the other wants the Core destroyed, and player alignment should mean something. Recorded as
author direction. Nothing is built for it now; it is the mirror image of
`EXILES_ANTIPLAYER_ESCALATION.md` and belongs with that layer. See §12 Q3.

### 7.5 Nanoforge — investigation findings **[VERIFIED 2026-07-31]**

**[RATIFIED — ruling K]** The nanoforge is **STOLEN** and installed at Elpis. ✅ **UNDER REVIEW flag
CLEARED 2026-08-08 by the §3.2a reframe** — the whole war is now a countdown to stealing this item,
so destruction is no longer a live alternative. Ruling K is settled.

Sean's concern — that the custom `exile_ashen_orbitalworks` industry
(`data.scripts.industries.ExileAshenHeavyIndustry`) may not accept a Corrupted Nanoforge — was
well-founded in the abstract but **does not apply**. Vanilla's install gate and vanilla's *effect*
path are two separate mechanisms, and only the gate cares about industry id.

> **⚠️ CORRECTION 2026-07-31.** An earlier version of this section stated that the install gate
> would *reject* the nanoforge. **That was wrong.** It was derived from vanilla's
> `special_items.csv` without checking whether Exiles overrides it. **Exiles ships its own
> `data/campaign/special_items.csv`**, and both nanoforge rows are already whitelisted:
>
> ```
> "heavyindustry, orbitalworks, exile_ashen_heavy_industry, exile_ashen_orbitalworks"
> ```
>
> So the gate **passes**. This is why Sean's in-game test worked (below). The conclusion was right;
> the reasoning was incomplete. Lesson, and a repeat of the `enableStipend` one: **check whether the
> mod overrides a vanilla data file before reasoning from the vanilla copy.**

**1. The install gate PASSES — both the gate path and the direct path work.**
`BaseIndustry.wantsToUseSpecialItem(data)` matches `spec.getParams()` from `special_items.csv`
against `getId()`. Because Exiles whitelists `exile_ashen_orbitalworks` (and
`exile_ashen_heavy_industry`) on both nanoforge rows, the gate returns **true**. It is consulted in
`CoreScript` (auto-install when the *player sells* a special item to an NPC market's submarket) and
`HA_CMD` (the player-colony item-management dialog) — both of which therefore work.

**✅ CONFIRMED IN-GAME (Sean, 2026-07-31):** sold a Corrupted Nanoforge to the Ashen Crusade; they
installed it in their Ashen Orbital Works and the production bonus applied. *Not* tested: stealing
it back from the Crusade in a raid — noted as potential quest material, not a priority.

**2. The effect path ignores the gate entirely.** `BaseIndustry.apply()` runs:

```java
if (special != null) {
    InstallableItemEffect effect = ItemEffectsRepo.ITEM_EFFECTS.get(special.getId());
    List<String> unmet = effect.getUnmetRequirements(this);
    if (unmet == null || unmet.isEmpty()) effect.apply(this);
}
```

No industry-id check. The only gate is `getUnmetRequirements`.

**3. The nanoforge has no requirements.** Corrupted Nanoforge is a
`BoostIndustryInstallableItemEffect`, which does not override `getSimpleReqs()`. The base returns an
empty array → `getRequirements()` empty → **`getUnmetRequirements()` always returns empty.**

**⇒ `industry.setSpecialItem(new SpecialItemData(Items.CORRUPTED_NANOFORGE, null))` on
`exile_ashen_orbitalworks` applies the full effect** (+1 supply bonus, +20% `PRODUCTION_QUALITY_MOD`)
and renders correctly in the industry tooltip via `getVisibleInstalledItems()`.

**4. `ExileAshenHeavyIndustry` already contains nanoforge plumbing** — it is a close copy of vanilla
`HeavyIndustry` and retains the corrupted→pristine upgrade override, `daysWithNanoforge` tracking
with `updatePollutionStatus()`, and `canImproveToIncreaseProduction()`.

**5. Removal from Culann is clean.** `setSpecialItem(null)` unapplies the previous effect before
clearing the field (`BaseIndustry` 1865–1873). No orphaned modifier.

**6. Quality stacking is vanilla-correct.** `exile_ashen_orbitalworks` grants +20% under
`getModId(1)`; the nanoforge grants +20% under mod key `"nanoforge"`. Different keys, so both apply,
for +40% — and **vanilla `HeavyIndustry` does exactly the same thing**, which is why Culann gives
Tri-Tachyon +40% today. The transfer *moves* an existing +40% ship-quality producer from Tri-Tachyon
to the Crusade rather than inventing a new one.

**7. No pollution side effect.** `updatePollutionStatus()` returns immediately unless the market has
`Conditions.HABITABLE`. Elpis (`exileashen_command_bastion`) is a station whose only starting
conditions are `population_4` and `exile_crusade_mobilization`.

**Minor blemish, not a blocker:** `daysWithNanoforge` is never reset when a special item is removed,
so remove-then-reinstall carries stale accumulation. Harmless here (it only feeds the habitable-world
pollution timer), but worth a one-line fix if that field is ever touched.

**Recoverability — resolved by the correction above.** An earlier note worried the nanoforge might be
permanently welded in because `HA_CMD` consults the gate. Since the gate passes, a player who
captures Elpis should be able to manage the item normally. Still untested, but no longer a concern.

**Entity mapping [VERIFIED]:** Elpis = `exileashen_command_bastion`, a station in the **Providence**
system (`ExileAshenCrusade.java` calls `createStarSystem("Providence")`), carrying
`exile_ashen_orbitalworks`.

---

## 8. New market conditions

To be registered in `data/campaign/market_conditions.csv`, following the existing rows.

| Condition | Proposed id | Effect | Lifecycle |
|---|---|---|---|
| **Relief Shipment** | `exileac_relief_shipment` | +1 stability, +1 food, +1 supplies, **−1 crew**; player crew **buy** price +50% | 60d; refresh on re-supply; `MAX_DAYS` backstop |
| **Crusade Strike** | `exileac_crusade_strike` | −N availability, Corsair-Activity picking pattern (§5.3) | 30d self-timer |
| **Crusade Harassment** | `exileac_crusade_harassment` | Corsair-Activity-equivalent; Tri-Tachyon markets only | Presence-scoped: day 5 → withdrawal; self-timer backstop |
| ~~**Crusade Blockade**~~ | ~~`exileac_crusade_blockade`~~ | **Probably not needed** — vanilla `econ.Blockaded` already does the accessibility penalty with `BlockadeFGI`-owned lifecycle (§6.4). Confirm in Pass 6 before writing a custom one. | vanilla-owned |

---

## 9. Config schema and pre-production checklist

All tuning lives under `"exileashencrusade"` in `data/config/exiles/director_config.json`
(CLAUDE.md §7: player-adjustable settings are a design goal; no `// TUNE:` constants in Java where
a config key will do).

### 9.1 Test compression **[RATIFIED 2026-07-31 — build this in Pass 1, not retrofitted]**

Sean: *"all discussed time periods are final-version targets; testing times will, as usual, need to
be compressed and require debugging messages so I don't go insane waiting for an event to happen."*

**Learn from the Black Fleet.** Its config carries a `!!! RESTORE BEFORE PRODUCTION !!!` banner and
a six-line manual checklist (`EXILES_BLACK_FLEET_SYSTEMS.md` §6) precisely because there is no
single knob — every cadence must be restored individually, and a half-restore ships silently.
**Do not repeat that pattern here.**

```
"timeScale": 1.0,     # PLAYER OPTION. Multiplies EVERY duration and cadence in this
                      # block at config load. 1.0 = production. Set 0.1 for testing:
                      # Phase 1 becomes 9 days, a 60-day condition becomes 6.
                      # One line to set, one line to restore, impossible to half-restore.
"testing": {          # All default OFF. Dice and travel are not durations, so timeScale
                      # alone cannot compress them — these cover the rest.
    "sabotageAlwaysSucceeds": false,   # exercise effect paths without waiting on odds
    "forcePhase": -1,                  # jump straight to a phase on load; -1 = normal
    "warScoreOverride": -1,            # force a mature-war state for late-game testing
},
```

**Honest limits:** `timeScale` compresses clocks, not physics. Fleet travel time is real distance at
real burn, and probabilities are not durations — hence the `testing` block for the dice. Debug
messaging stays per-subsystem (below) so one noisy system can be watched without the rest.

### 9.2 Schema

```
enabled, debugMessages
warClock  { phase1Days, phase1AfterAssaultDays, phase2MinDays, phase2MaxDays,
            phase3MinDays, phase3MaxDays, scorePerRelief, scoreFpMult, ... }
relief    { enabled, intervalDays, orbitMinDays, orbitMaxDays, fleet{...},
            enclaveEntityIds[], conditionDays, stabilityBonus, foodBonus,
            suppliesBonus, crewPenalty, crewPriceMult }
sabotage  { enabled, attemptsPerMonthPhase1/2/3, impossibleFloor,
            baseChance{culann, eochu_bres, tibicena, port_tse},
            defenseModifiers{...}, industriesDisabled, disableDays,
            industryTags[], spaceportWeight, spaceportDisableDays,
            strikeDays, strikeCommodityCount, strikeMagnitude,
            special{ targets[], baseChance, matureChance, disableDays,
                     unrestPoints, culannWeightEarly, culannWeightMature,
                     warScoreThreshold } }
            # baseChance -> matureChance and culannWeight* are the sec 5.5
            # pacing dials. Retune these, not the phase durations, if the
            # Culann strike lands outside the 2-3 year target band.
interdiction { enabled, presenceDays, spawnFpMin/Max, feasibilityMult,
               maxActive, bountyPerFrigate, conditionDelayDays }
blockade  { enabled, fleetFp, durationDays, accessibilityPenalty,
            threatenStrengthRatio, repLossThreaten, repLossTrade,
            repRequiredForBusiness }
            # fleetFp / durationDays / accessibilityPenalty map onto vanilla
            # FGBlockadeParams (sec 6.4) — this IS Sean's F.d flexibility knob:
            # a Hegemony or League blockade is the same code with bigger numbers.
assault   { enabled, attackFleetCount, attackFpMin/Max,
            harassFleetCount, harassFpMin/Max, culannDisruptDays,
            nanoforgeAction }
            # failureCooldownDays DROPPED — see sec 12 O2
targetMarkets: ["culann", "eochu_bres", "tibicena", "port_tse"]
```

**Pre-production checklist:**
- [ ] **`timeScale` → `1.0`** (the single restore; see §9.1)
- [ ] **`testing` block → all defaults off**
- [ ] **`interdiction.maxActive` → `1`** (test-tuned to 3 on 2026-08-01; §6.1's "one raid at a
      time" is the production value — travel does not compress, so 1 was too sparse to observe)
- [x] ~~`exileomicron.escalation.rampDays` → `365`~~ — **restored 2026-08-02** after the
      umbrella-gate test. Kept on this list as a reminder that the Omicron growth clock is *not*
      compressed by this block's `timeScale`, so testing anything on it means dropping `rampDays`
      and restoring it afterwards.
- [ ] `debugMessages` → `false` (all sub-systems)
- [ ] Culann strike lands in the 2–3 year band in a real campaign (§5.5)
- [ ] `startDelayDays` set so the war does not open on day 1 of a new campaign
- [ ] Blockade `enabled` confirmed
- [ ] Nanoforge action (steal vs destroy) confirmed — §7.5 is UNDER REVIEW

---

## 10. Rulings — RESOLVED

| # | Topic | Ruling |
|---|---|---|
| **A** | Phase-4 loop | **Phase 4 fires once**, on a successful Culann special sabotage. Automatic loop is 1→2→3 thereafter. Follow-up major attacks are quest-linked. |
| **B** | Eochu Bres saturation | **Removed from the director.** Occurs only at the end of a player quest chain. |
| **C** | Sabotage odds | **Config base chance × functional-defense modifiers.** As recommended. |
| **D** | Sabotage effect | **Tag list**, never population, never military. **`commerce` dropped** — disrupting it grants +3 stability (§5.2 finding). |
| **E** | Crusade Strike | **Corsair Activity pattern**, count and magnitude in config. |
| **E.c** | Condition sharing | **Shared abstract base class, separate ids** — extracted in Pass 5, not Pass 4 (§6.2). |
| **F** | Blockade | "Other fleets turned away" **cut**. Live checks, hard expiry, kill-switch, once-per-dock rep guard. |
| **F.d** | Blockade reuse | Build the interception plumbing **faction-generic from day one**; no second consumer yet. |
| **G** | Assault FP | **Config FP**, tuned live. 2 × 250–350 + 2–3 × 80–120. |
| **H** | Omicron redirect | **No diversion or recall.** Hybrasil response is already automatic. Use an **intel scan, not a memory flag** (§7.3); deferred to Pass 7. |
| **H.c** | Providence targets | Recorded as future direction for the general War Director. Not scheduled. |
| **J1** | "Impossible" | **Skip the month entirely.** |
| **J2** | Relief crew | **−1 crew** (not −2); keep the +50% purchase cost. |
| **J3** | >+49 option | **Keep.** A door the player unlocks by improving standing; reuse for Hegemony/League. |
| **J4** | Messaging | **Mirror the Black Fleet** (§3.4 table). |
| **J5** | Target discipline | **Tri-Tachyon only.** Pirates aren't worth Crusade ships and crews. |
| **K** | Nanoforge | **Stolen** — no technical blocker, and **confirmed working in-game** (§7.5). **Flagged UNDER REVIEW**; destruction may be better characterization. |
| **L** | Spaceport sabotage | **Shorter duration + lower pick weight** for the spaceport/megaport category. |
| **Q1** | Phase-4 retry | A **failed** assault does not consume the allowance. Only **success** closes the loop; failure costs war score. |
| **Q2** | Phase-4 entry | Nanoforge presence **does not gate entry**. Entry = successful Culann special sabotage + Phase 4 not yet succeeded. Nanoforge presence only decides whether there is anything to steal. |
| **Q3** | Catastrophic events | **Canon note only.** Belongs to a separate layer, gated behind quests that let the player side with a faction. Nothing built here. |
| **Q4** | Blockade reuse | Outcome wanted: the **same blockade tactic in the Hegemony–League war**, light on resources, flexible enough for a stronger fleet held longer. Implementation delegated. → vanilla `BlockadeFGI` (§6.4) is very likely the answer. |
| **O1** | Phase-4 pacing | **Weight Culann higher; war score tips selection and escalates the sabotage chance.** Target band **2–3 in-game years** (§5.5). |
| **M** | Test compression | **Single `timeScale` multiplier + a `testing` block**, built in Pass 1 — explicitly *not* the Black Fleet's manual per-value restore checklist (§9.1). |

---

## 11. Build passes

> **STATUS: v1.0 — BEHAVIOUR COMPLETE (Sean, 2026-08-16; reaffirmed 2026-08-24).** Passes 1–7 are
> built. 1–4, 5a and 6a/6c were live-tested as they landed; 5b was closed without a build; 6b was
> cancelled and relocated to the Hegemony–League war; Pass 7 was built 2026-08-15 and live-tested
> with fixes through 2026-08-16. **6d is confirmed in-game 2026-08-24** (author tuning open: orbital
> distances of the damaged mirror). **6a is fully cleared 2026-08-24** — debug flag removed,
> `forcePhase` and `phase3` restored. **Config is at production values** except `debugMessages`,
> deliberately left on while testing. Remaining: **Pass 7.5** (independent review by a different
> model, against the finished stack alongside all remaining faction behaviours) and **Pass 8**
> (config sweep).

Each pass ends with `tools/janino-check.ps1`, a boot/save-load test where applicable, and a local
commit. Nothing is pushed without Sean's go-ahead (free-account cadence).

| # | Pass | Risk |
|---|---|---|
| 0 | **This document.** ✅ Complete. | — |
| 0.5 | **Settle Q1–Q4.** ✅ Complete 2026-07-31. Two small items remain in §12 (O1, O2); neither blocks Pass 1. | — |
| 1 | ✅ **BUILT 2026-08-01, Janino-clean, AWAITING BOOT TEST.** `AshenWarDirector` — phase machine, war score, config load, `timeScale` + `testing` block, quest override, registration. Launches nothing. See §14. | low |
| 2 | ✅ **BUILT + LIVE-TESTED 2026-08-01.** Sabotage (ordinary + special), Crusade Strike condition, Phase-4 trigger, §7.0 telegraph. 29 attempts, rates match design, zero exceptions. §14.2. | low |
| 3 | ✅ **BUILT + LIVE-TESTED 2026-08-01**, amended same day (§4.1a–c: off-phase cadence, TT ignore flag, raid veto), **amendments re-tested and passed** the same day along with a departure false-positive fix. Relief convoys + Relief Shipment + the first organic war-score source. Only the §4.1c raid veto remains unexercised (needs a non-Ashen raid to coincide). See §14.3. | low |
| 4 | ✅ **BUILT + LIVE-TESTED, PASSING as of 2026-08-02** (five test rounds). Interdiction raids + Crusade Harassment + Tri-Tachyon bounty + the §6.1a war-score ladder. Fixed across rounds: fleet composition (`maxFleetFp`), station attacks (per-fleet `doNotAttack`), same-system condition collision (`hasLiveOpIn`), anti-hotspot cooldown (now starts at op end). One item remains open and is **Omicron-side, not Ashen-side**: the early station-complement wake (§6.1a). See §14.4. | low-med |
| 5a | ✅ **BUILT + LIVE-TESTED 2026-08-04, PASSING.** Condition base classes — `ExileTimedCondition` (7 consumers) and `ExileCommodityShortageCondition` (3). Touches **zero director files**. Full clock cycle, zero exceptions. See §14.5. | low |
| 5b | ⛔ **CLOSED 2026-08-05 WITHOUT A STANDALONE BUILD.** Investigated in full; the duplication the pass was scoped around does not exist. Feasibility **rejected on the merits**, cooldown helpers **rejected as zero-saving**, and what survived (one live-op helper, two callers) folds into Pass 6. See §14.6. | — |
| 6a | ✅ **BUILT + LIVE-TESTED 2026-08-05/06** (§14.8). `AshenBlockade extends BlockadeFGI` — named Hussar anchor + `NPCHassler` pickets, withdraws with the raid or past 50% damage, vanilla `Conditions.BLOCKADED`, own kill switch. Mechanism confirmed working; two bugs fixed (`doNotAttack`, `makeFleetsHostile`). **⚠ Its design was superseded 2026-08-09 but the CODE SURVIVES** — Pass 6c reuses it as the rehearsal's gate-holding element, switched from `FGBlockadePlanetAction` to the default `FGBlockadeAction`. The `doNotAttack` override becomes unnecessary. ✅ **CLEARED 2026-08-24 (Sean, by hand):** `AshenBlockade.DEBUG` removed (verified absent), `forcePhase` back to `-1` (continuous override, never matches a real phase), `phase3` back to 60/90 — the same band as Phase 2 by design. | med |
| 6b | ⛔ **CANCELLED for Ashen 2026-08-09.** The four-option docking dialogue is **relocated** to the Hegemony–Persean League war (rulings J3, F.d), which does not yet exist — a shelf, not a transfer. With the Hussars at jump points instead of in orbit, a docking interception no longer fits the fiction. **This removes the highest-risk pass in the plan** — the one that could permanently lock a player out of a market. | — |
| 6c | ✅ **BUILT + LIVE-TESTED + FIXED 2026-08-09.** Escalation and effects passed first time; four fixes followed, **two of them defects in the new code** — the war-score outcome never reported on failure (the player's lever was disconnected) and the rehearsal launched as Phase 3 was closing. See §14.9. Two items open: the intel leaks the source system, and the `getEnemyStrength` hypothesis is unverified. **Pacing remains unmeasured.** Original scope below. | med |
| ~~6c~~ | **Phase 3 — The Rehearsal.** §6.4, ratified 2026-08-09. Three-part op: Hussars hold the jump points (6a's `AshenBlockade`, switched to the default `FGBlockadeAction` — one line), assault group cracks the orbital station (`BlackFleetAssaultStage` shape, scaled), screen fleets vs patrols. Tibicena → Port Tse, war-score gated. On success: Mining + the market's fleet industry disrupted 30d, Recent Unrest via `ExileUnrestGuard`, vanilla `Blockaded` while held. Interdiction raids off in Phase 3; sabotage stays. War score up on success, down on failure, diminishing returns. | med |
| 6d | **Hybrasil world-state (§6.4b).** Remove `solar_array` from Eochu Bres, damage the orbital mirrors. Post-econ patch in the Gilead/Asher/Nachiketa family. **Fully separable — buildable any time.** ✅ **BUILT 2026-08-10; CONFIRMED IN-GAME 2026-08-24 — §14.10.** One mirror destroyed with a poor debris field on its station, one knocked off-axis, one intact, all still Tri-Tachyon. **Confirmed in-game by Sean 2026-08-24: renders correctly.** Two author items open, neither blocking: the custom description (author prose — the backstory is implied by the wreckage and stated nowhere), and trying different orbital distances for the damaged mirror. | low |
| 7 | ✅ **BUILT 2026-08-15, LIVE-TESTED + FIXED through 2026-08-16.** Culann Strike — assault + harassment fleets, bombardment *visual only*, Culann knockout, nanoforge transfer, once-only guard, Omicron intel scan. Built across seven steps (`dbb292a`→`8192af8`), catching three bugs mid-build including a scheduler that would have relaunched every wave. **Five fixes followed:** Phase-4 fleet counts made hard with FP as a pure strength dial (`68b8526`), counts carved rather than capped (`8d59bcf`), the harassment flag re-phrased so old saves keep their behaviour (`cd439d5`), the three waves launched together rather than in sequence (`32063e8`), and the Crusade made to disengage once Culann falls (`d2f191d`) — that last via the strategic module's do-not-attack tracker, because `$cfai_makeNonAggressive` is **DEAD CODE** (zero hits across all 2,818 classes in `starfarer_obf.jar`). Sean's choreography runs: sweep d0 / gates d4 / capitals d9, 11 fleets. **⚠ RE-SCOPED 2026-08-08 by §3.2a:** trigger is preparation completion, knockouts 60/180/360/360 with or without the forge, failure resets to a floor; §5.5's pacing arithmetic is retired — do not tune against it. **Open: `assaultFp: 800` is an extrapolation** — §7.1's fleet numbers were measured wrong (Hybrasil reads 1278–1483); a measurement line ships with it, tune from that log. | med |
| 7.5 | 🔍 **NEXT UP — INDEPENDENT DIRECTOR REVIEW, to be run by a DIFFERENT MODEL** (Fable 5 or GPT Sol; Sean's call, and the right one — see §14.6 "Why a different reviewer"). A **review, not a refactor**. Scheduled 2026-08-05 and placed after Pass 7 so it reviews a finished stack rather than code about to change; **that condition is now met.** Sean, 2026-08-24: Ashen is at v1.0 *"or as close to 1.0 as we're going to get before reviewing it next to all remaining behaviors"* — so the review is deliberately scoped against Ashen **alongside the other faction directors**, not in isolation. Scope in §14.6. | — |
| 8 | Config externalization sweep, docs, pre-production checklist, Nexerelin note, orphan-guard audit. **Widened 2026-08-03/04:** also wires the Black Fleet and refugee *condition* tunables to config (5a made them assignable), and adds an `enabled` toggle to the `blackfleet.assault` block — currently the only activity block without one. | low |

**Architecture position — and the project-wide ruling that came out of it.** This pass plan does
**not** build the generalized "Sector Destabilization Director" (CLAUDE.md §6). Ashen is the third
sibling on the same vanilla `RaidIntel` pipeline. **Pass 5 then settled the question for the whole
project (2026-08-05):** 5a extracted what had genuinely repeated three times (the condition base
classes, touching zero director files); 5b found the director-level duplication *did not exist* and
was closed without a build, with feasibility gating **rejected on the merits — do not re-propose**.
The unified Director is therefore **rejected outright**: faction directors share VOCABULARY, not
CONTROL FLOW, and global coordination runs through shared read-only state. Hegemony–League will be
the **fourth sibling**. See CLAUDE.md §6 "Architecture direction", and §14.5/§14.6 below.

---

## 12. Open items

> **Q1–Q4 and L are RESOLVED** (see §10). **Pass 1 is cleared to begin.** Three items remain; none
> blocks Pass 1, and O3 is a Pass-6 research task rather than a question for Sean.

**O1 — Phase-4 pacing.** ✅ **RESOLVED 2026-07-31.** Weight Culann higher, let war score tip
selection, escalate the special-sabotage chance with war score; target band **2–3 in-game years**.
Arithmetic and starting values in §5.5, config dials in §9.2.

**O4 — Should the Culann strike be quest-gated rather than organic?** ✅ **RESOLVED 2026-08-01 —
HYBRID RATIFIED.** Keep the trigger **organic with the §7.0 telegraph**; Phase-2 quest content may
later accelerate, delay, or prevent it, but never gates it. §7.0 is therefore **[RATIFIED]**, not
proposed. Reasoning retained below.

Sean's worry: the player may be in the far reaches when the climax fires. *"It's a really neat idea,
but we run the risk of the player exploring the far reaches when it goes down."*

**Recommendation: keep it organic, add the §7.0 telegraph, and use quests as an accelerant rather
than a gate.**

Reasoning:
- Quest-gating makes the Crusade wait for the player. That undercuts pillar 3 (*the sector acts
  whether or not the player is watching*) and makes the four-phase war clock decorative for any
  player who never touches Ashen content.
- It also compounds: the Eochu Bres saturation and any second assault are **already** quest-gated
  (ruling B). If the Culann strike joins them, **the war director never does anything irreversible
  on its own** — it becomes raids, blockades and sabotage in perpetuity, with no arc and no payoff,
  and Phase 4 is dead code in the automatic loop.
- The frustration Sean is describing comes from *no warning*, not from *no quest*. §7.0 fixes the
  warning for a fraction of the cost.

**The hybrid:** organic trigger and telegraph as the baseline; Phase-2 quest content (Ashen-aligned
or Tri-Tachyon-aligned) can later **accelerate, delay, or prevent** it. That is the player's lever
per pillar 4, it slots into quest work already planned, and — importantly for build order — Pass 7
builds the organic path either way. The quest layer hooks it later; it is not a second
implementation.

**Sean's call.** Nothing about Passes 1–6 changes whichever way this goes.

**O2 — Drop `failureCooldownDays`?** *(trivial; confirm in passing)*
It was proposed when Q1 was open. With Q1 answered, re-entry already requires **another successful
Culann special sabotage**, and there is at most one such roll per cycle — the gate *is* the
cooldown. **Recommendation: drop it**, keep only the war-score cost for a repelled assault. Removed
from the §9 schema on that basis; say so if you want it back.

**O3 — Verify `BlockadeFGI` works for NPC-vs-NPC.** *(Pass-6 research task, not a question for Sean)*
The §6.4 find re-plans Pass 6 around vanilla's blockade system, but vanilla only uses it
player-directed (`FGBlockadeParams.targetFaction` defaults to `Factions.PLAYER`;
`PerseanLeagueBlockade` is wired into `HostileActivityEventIntel`). Before committing:
1. Can `BlockadeFGI` target an arbitrary NPC market with `targetFaction = "tritachyon"`?
2. What does `FGBlockadeAction.directFleets` actually do to the player (line ~173, "not hassle the
   player")? That determines whether the four-option dialogue layers on cleanly.
3. Does `NPCHassler` give the "turn other fleets away" behaviour ruling F cut, for free?

If any answer is no, the bespoke fallback in §6.4 stands.

---

## 14. Pass 1 — build record and test matrix

**Built 2026-08-01.** `data/scripts/exiles/AshenWarDirector.java` (new),
`data/config/exiles/director_config.json` (new `"exileashencrusade"` block),
`data/scripts/exiles/ExilesModPlugin.java` (registration in `onNewGameAfterEconomyLoad` +
`hasScript` guard in `onGameLoad`).

**Verified offline:** both classes pass `tools/janino-check.ps1` (exit 0);
`director_config.json` brace/bracket balance confirmed (43/43, 10/10).
**Not verified:** anything requiring the game to run — see the matrix below.

### What Pass 1 does and does not do

It runs the four-phase clock and the war score, and **launches nothing** — no fleets, no intel,
no market conditions. That is deliberate: it proves the state machine and save/load in isolation,
before any subsystem can confuse a phase bug with a fleet bug.

Public hooks later passes plug into, so no pass has to reach into this class's internals:

| Hook | Used by |
|---|---|
| `AshenWarDirector.get()` | any pass — finds the live director, no stored reference |
| `requestPhase4()` | Pass 2 — special sabotage landed on a Culann target |
| `notifyPhase4Succeeded()` / `notifyPhase4Failed()` | Pass 7 |
| `addWarScore(float)` | Pass 3 (relief fleets), Passes 4/7 (op outcomes) |
| `getPhase()`, `getWarScore()`, `getFpMult()` | Passes 2–7 |
| `isSabotageAlwaysSucceeds()`, `debug(String)` | all passes — one config flag, one channel |

### Live test matrix — RESULTS 2026-08-01

Config ships **`timeScale: 0.1`** and **`debugMessages: true`**, so a full cycle runs in ~24 days
instead of ~240 and every transition prints to the message window and `starsector.log`.

| # | Test | How | Result |
|---|---|---|---|
| 1 | **Boot** — no crash, Phase 1 announced | play | ✅ **PASS** |
| 2 | **Cycle** — 1 → 2 → 3 → 1, ~9d / 6–9d / 6–9d | play | ✅ **PASS** — 4 full cycles, all durations in band |
| 3 | **Save/load mid-phase** — state preserved, director not duplicated | play | ✅ **PASS** — 2 reloads, correct resume, one message per transition |
| 4 | **`forcePhase`** — set 3, clock jumps once then continues | config edit + restart | ⏸ not run |
| 5 | **`warScoreOverride`** — set 1.0, Phase 1 shortens ~33% | config edit + restart | ⏸ not run |
| 6 | **Phase-4 branch never fires** (no Pass-2 trigger yet) | play | ✅ **PASS** — 4+ cycles, always 2 → 3 |
| 7 | **Quest override** stops the clock | console (below) | ⏸ **deferred — see note** |
| 8 | **`timeScale` restore** — 1.0 gives ~90d Phase 1 | config edit + restart | ⏸ not run |

**Log evidence (2026-08-01):** cycles 0→4 clean; two reloads resumed at the correct saved phase and
cycle with no re-announce and no duplicate director; **zero runtime exceptions from any
`data.scripts.*` class**.

Unrelated pre-existing log noise, confirmed NOT from Exiles: `Ship hull spec [flare] /
[module_hightech_decor] not found` (no Exiles file references either id) and a `JSONException` for
`mods/Starsector-IntelliJ-Template-main/mod_info.json` (a separate broken mod folder).

### Console syntax for test 7

> **Doc defect fixed 2026-08-01.** This matrix previously said "set the flag via console" without
> giving syntax. There is **no `Set` command** in Console Commands — the attempt logged
> `No such command "set" registered!`, and the `RunCode` follow-ups failed on a **missing trailing
> semicolon** (`RunCode` compiles its input as a statement, so the `;` is mandatory).

```
runcode Global.getSector().getMemoryWithoutUpdate().set("$exiles_ashen_war_enabled", false);
runcode Global.getSector().getMemoryWithoutUpdate().unset("$exiles_ashen_war_enabled");
```

**Test 7 is deliberately deferred.** `warEnabled()` is a line-for-line copy of
`BlackFleetRaidManager.raidsEnabled()`, which is already playtested, and no quest exists yet to flip
the flag. Run it when the first quest that uses it lands.

### Known-open after Pass 1

- The Phase-4 entry path is unreachable until Pass 2. Test 6 asserts that absence rather than the
  branch itself; the branch gets its real test in Pass 7.
- War score has no organic source until Pass 3 (relief fleets), so `warScoreOverride` is the only
  way to exercise mature-war behaviour. Expected, not a defect.
- **[PROPOSED — open, Sean's call] Phase 1 has no jitter.** It rolls exactly `phase1Days` every
  cycle while Phases 2 and 3 jitter, which is faithful to the spec but reads mechanical once
  noticed — every other Exiles director jitters its cadence (Black Fleet uses 0.75–1.25×). A
  `phase1JitterFraction` config key would cost one line. Not changed.

---

## 14.2 Pass 2 — build record and test matrix

**Built 2026-08-01.** New: `data/scripts/exiles/AshenSabotage.java`,
`data/scripts/marketconditions/ExileCrusadeStrike.java`, a `market_conditions.csv` row
(`exileac_crusade_strike`, order 153). Changed: `AshenWarDirector` (sabotage cadence + special
window + Phase-1 jitter), `director_config.json` (`sabotage` block + `phase1JitterFraction`).

**Verified offline:** all four classes pass `tools/janino-check.ps1`; `director_config.json`
balanced (50/50 braces, 12/12 brackets); the new CSV row parses to 9 fields with the right script
and icon. **Not verified:** anything needing the game to run.

### Design notes worth knowing

- **Odds direction was inverted** relative to §5.1 as originally written — see the correction
  banner there. Ratified numbers unchanged.
- **Phase-1 jitter added** (`phase1JitterFraction`, ±15%) per Sean's call. Symmetric, so §5.5's
  mean-cycle pacing arithmetic is unaffected.
- **`commerce` is absent** from `industryTags`, per the §5.2 finding.
- **Special sabotage skips already-disabled targets** — an operative's life is not spent on an
  installation that is already offline.
- **Only a Culann success that actually queues Phase 4 fires the telegraph.** If the strike has
  already succeeded once, `requestPhase4()` returns false and the message is suppressed — no
  misleading warning about an offensive that can never come.
- **`AshenSabotage` is transient**, rebuilt each launch, so its config edits land on restart. Only
  two floats and a boolean were added to the director's saved state.

### Live test matrix

Config ships `timeScale: 0.1`, so the 30-day sabotage cadence is **3 days** and the special window
is the last **3 days** of Phase 2. Expect roughly one ordinary attempt every 3 days.

1. **Boot** — no crash; `starsector.log` shows `Ashen ordinary sabotage vs …: chance=… -> …`
   within the first few days.
2. **Odds sanity** — logged chances should read ~0.25 for Tibicena/Port Tse and ~0.02 for
   Culann/Eochu Bres while their defenses are intact.
3. **Success effect** — on a success, the target market gains **Crusade Strike** (30d → 3d at test
   scale) and up to 3 industries go offline. Check the market's industry list for disruption
   timers and the condition tooltip for the commodity list.
4. **Phase 3 doubles up** — two ordinary attempts per batch during Phase 3, one in Phases 1–2.
5. **Special sabotage** — fires once in the closing window of each Phase 2. Log line begins
   `Ashen SPECIAL sabotage vs …`.
6. **Telegraph + Phase 4** — when a special succeeds against **Culann**, an orange message names
   the crippled installation, and the clock routes Phase 2 → **Phase 4** instead of Phase 3. This
   is the first time Phase 4 becomes reachable. Set `testing.sabotageAlwaysSucceeds: true` to force
   it rather than waiting on the dice.
7. **Cross-system window** — disrupt Culann's Military Base by any means and confirm the logged
   Culann sabotage chance roughly doubles per downed defense.
8. **Phase-1 jitter** — Phase 1 durations should now vary (~7.6–10.4d at test scale) instead of a
   flat 9d.

### Live test RESULTS 2026-08-01 — ✅ PASS, no bug

29 ordinary attempts observed. **Zero exceptions from mod code.** Condition loads and applies.
Phase-1 jitter confirmed working (durations 8/9/8/9/8, previously a flat 9).

| Target | Attempts | Successes | Logged chance |
|---|---|---:|---:|
| Port Tse | 16 | 3 | 0.25 |
| Tibicena | 10 | 0 | 0.25 |
| Culann | 2 | 0 | 0.02 |
| Eochu Bres | 1 | 0 | 0.02 |
| **Total** | **29** | **3 (10.3%)** | |

**Sean's observation — "it fails more often than I expected" — is variance, not a defect.**
Targets are picked weighted by their own chance, so expected overall success is
`2×(0.463 × 0.25) + 2×(0.037 × 0.02)` = **23.3%**, i.e. 6.8 successes in 29. Observed 3 is
**1.65σ low** (σ = 2.3) — roughly a 1-in-20 stretch, inside normal variance.

Everything verified against design intent:
- Logged chances exactly 0.25 / 0.02, with no inflation (no defense was ever down).
- Target weighting: expected 13.4 / 13.4 / 1.1 / 1.1, observed 16 / 10 / 2 / 1.
- Special sabotage: 3 attempts, 1 success, chance 0.25 at war score 0.0 (correct lerp from
  `baseChance`). Culann picked 1 of 3; expected P(Culann) at early weighting is exactly 1/3.
- Successes reported "2 industries offline" not 3 — Port Tse has only two eligible industries
  (spaceport, mining); population, ground defenses, military base and orbital station were all
  correctly excluded. The eligible-industry filter works.

**Perception note for future testing:** at `timeScale 0.1` a session shows ~29 attempts, which is
~2.4 **in-game years** of war at production speed. Compressed testing makes any probabilistic
system look like a stream of failures. Judge rates from the arithmetic, not the log density.

**Ruling — no tuning.** The ratified 25% is landing precisely as specified. If more activity is
wanted later, raise `attemptsPerMonthPhase1/2/3` rather than `baseChance`, which would break the
"practically impossible against the heartland" contract.

### Known-open after Pass 2

- **[WATCH — not a defect] Repeat-hit stacking on small markets.** Port Tse has only two eligible
  industries, so every success hits both and extends each by 60d. At production rates that is
  ~1.1 successes per cycle, leaving its spaceport disrupted ~25% of the time on a rolling basis;
  a lucky streak could keep it down continuously. There is no anti-repeat guard here (unlike the
  Black Fleet's `Sacked`). Deliberately **not** fixed: ordinary sabotage adds no unrest, so there
  is no decivilization path, and "the frontier outposts are being slowly strangled" may be the
  intended fiction. It does interact with ruling L's caution about spaceport accessibility, so
  review it during the Alpha Build Test. Cheap fix if needed: skip markets already carrying
  Crusade Strike — one line, and thematically sound (no operative is spent on a target the
  Crusade just crippled).

- Phase 4 has no action stage yet (Pass 7), so entering it will simply run out its
  `phase4Days` and return to Phase 1. That is expected: Pass 2 delivers the *trigger*, not the
  strike. `notifyPhase4Succeeded()` is never called, so the branch stays open and can re-fire.
- War score still has no organic source (Pass 3), so the special sabotage's escalating chance and
  the Culann weighting stay at their early-war values unless `warScoreOverride` is set.

---

## 14.3 Pass 3 — build record and test matrix

**Built 2026-08-01.** New: `data/scripts/exiles/AshenReliefManager.java`,
`data/scripts/marketconditions/ExileReliefShipment.java`, a `market_conditions.csv` row
(`exileac_relief_shipment`, order 154). Changed: `director_config.json` (`relief` block),
`ExilesModPlugin` (registration + load guard).

**Verified offline:** all four classes pass `tools/janino-check.ps1`; config balanced (52/52
braces, 14/14 brackets); the new CSV row parses. **Not verified:** anything needing the game to run.

### Design decisions made during the build

- **Separate `EveryFrameScript`, not a director field.** Convoys carry per-op saved state, and
  keeping it out of `AshenWarDirector` leaves the director's save footprint to primitives. Matches
  `BlackFleetRaidManager` / `ExileRefugeeConvoyManager`. The director is consulted via
  `AshenWarDirector.get()` for the phase and to bank score.
- **The aid lands on ARRIVAL; the war score banks on DEPARTURE.** A convoy destroyed in orbit has
  fed the enclave but delivered no recruits home, so it banks nothing and the enclave keeps the
  shipment. That is the right moral shape and it costs one field.
- **Launching is Phase-1 gated; in-flight convoys are not.** A convoy caught by a phase change is
  already loaded and on its way. Necessary at `timeScale 0.1`, where Phase 1 is 9 days but real
  travel from Providence is not compressible.
- **`maxActive` 2, not 1.** Round trips to the Core outlast the 30-day cadence; a hard cap of 1
  would silently drop the rate well below the ratified "one every 30 days".
- **Travel and departure timeouts are NOT scaled by `timeScale`** — they bound physical fleet
  travel, which does not compress with the war clock. Every other duration here does.
- **No spaceport requirement on destinations** (aid is delivered *to* them, and Yma deliberately
  runs broken structures); sources DO require a functional spaceport.
- **Enclaves already carrying Relief Shipment are skipped**, as are enclaves already targeted by
  an in-flight convoy — so the four enclaves rotate rather than one being fed repeatedly.
- Destinations weighted toward **smaller** enclaves: the Crusade knows where the desperate are
  thickest.

### Live test matrix

At `timeScale 0.1`: cadence 3d, orbit 1–2d, Relief Shipment 6d. Travel is real and uncompressed,
so a convoy from Providence to the Core may take a while — this is the first pass where physical
distance matters.

1. **Launch** — during Phase 1, log shows `Ashen relief convoy launched: <Ashen market> -> <enclave>`.
2. **Arrival** — `Ashen relief delivered to <enclave>`; the enclave gains **Relief Shipment**.
3. **Condition effects** — +1 stability, +1 food, +1 supplies, −1 crew, and crew costs the player
   50% more to buy (selling unchanged). Check the tooltip and the commodity screen.
4. **War score** — on departure, log shows `Ashen war score 0.0 -> 0.08`. **This is the first time
   war score moves on its own.**
5. **Rotation** — successive convoys should pick *different* enclaves while earlier ones still
   carry the condition.
6. **Phase gating** — no new launches outside Phase 1; a convoy already in flight keeps going.
7. **Maturity knock-on** — after several convoys, confirm the special-sabotage log line shows a
   rising `chance=` and `war score` above 0, and that Culann starts winning target selection more
   often (§5.5). This closes the loop from Pass 2.
8. **Save/load with a convoy in flight** — reload mid-journey; the convoy survives and continues,
   and no duplicate manager appears.
9. **Convoy destroyed** — kill one in orbit (or let pirates); the enclave keeps Relief Shipment,
   no war score is banked, and the op is dropped cleanly.

### Live test RESULTS 2026-08-01 — ✅ PASS, with one design problem found

Convoys spawn, travel, unload, apply the condition, and depart with no bugs on the play side. One
was observed destroyed after leaving orbit — the lifecycle handled it cleanly.

**Design problem found in play:** Tri-Tachyon patrols *and trade fleets* attacked relief convoys
aggressively in Magec and Mayasura, where enclaves share a system with TT markets. Not a code
defect; an unmodelled consequence of VENGEFUL relations. Resolved by §4.1b.

### Amendments 2026-08-01 (built, Janino-clean)

Three changes landed after the test, all ratified by Sean:
1. **§4.1a** — relief now also runs in Phases 2 and 3 at 60d (Phase 1 stays 30d, Phase 4 none),
   with `scorePerRelief` cut 0.08 → 0.045 to hold the pacing band.
2. **§4.1b** — `FLEET_IGNORED_BY_FACTION = "tritachyon"`, so TT no longer engages relief convoys.
   Pirates still do; the player still can.
3. **§4.1c** — active-raid veto at launch, **excluding the Crusade's own raids**.

**Amendment re-test RESULTS 2026-08-01 — ✅ PASS (log-verified, second session).**

| Item | Evidence |
|---|---|
| §4.1a off-phase relief | Convoys launched inside Phase 2 and Phase 3 windows (e.g. launch at 644533 between the Phase-2 entry at 589905 and the Phase-3 entry at 656958). |
| §4.1b TT ignore flag | **20 launches, 0 losses** across two sessions — against the first test, where TT patrols and trade fleets killed convoys in Magec and Mayasura. |
| Matrix 4 — war score | Four increments logged at +0.045 (0.14→0.18→0.23→0.27→0.32), matching the retuned value. |
| Matrix 7 — maturity knock-on | Special-sabotage chance visibly escalating with war score: 0.30 at score 0.18, 0.31 at 0.23. Culann selected on its own in cycle 3, so the weighting is live. |
| §4.1c raid veto | **NOT exercised** — no non-Ashen raid overlapped an enclave system during the test. Note that Pass 4 will not test it either: the veto deliberately ignores the Crusade's own raids. It needs a Black Fleet or vanilla raid to coincide, so treat it as opportunistic verification during a longer campaign. |

4. **Departure false-positive fix** (found in play 2026-08-01, after the amendments). Every
   *successful* convoy was reporting "was lost" in the debug channel. `GO_TO_LOCATION_AND_DESPAWN`
   removes the fleet **at the jump point rather than transiting it**, and
   `SectorEntityToken.isAlive()` is false once an entity leaves its containing location — so the
   loss branch in `advanceOp()` caught the normal despawn before the `OP_DEPARTING` branch could.
   Fixed by testing `OP_DEPARTING` first. Cosmetic only: war score banks in `beginDeparture()`
   before any of this, and the op was removed either way, so there was no scoring error, slot leak
   or orphan. Log arithmetic confirmed **zero real losses** (20 launched, 18 delivered, the 2
   unmatched being each session's last launch still in flight at quit; no `timed out en route`
   warnings, no exceptions).

   A convoy genuinely killed on the way home now also reports as returned. Accepted: the two are
   indistinguishable without extra tracking, and nothing mechanical depends on the difference.
   Loss detection during **travel and orbit** — the part the war-score damper rests on — is intact.

### Known-open after Pass 3

- **The Black Fleet still hunts relief convoys.** Ashen is HOSTILE to Pirates and the Black Fleet
  flies the pirate flag, so BF fleets — and Kanta's, which share Magec with the maxios enclave —
  remain a live threat. This is deliberate (§4.1b): losses stay possible.
- War score currently only ever goes **up** from relief and **down** from a failed Phase-4 assault
  (Pass 7). Passes 4 and 7 add the remaining consumers.
- `getSystemsWithActiveRaids()` now exists in **two** places (`ExileRefugeeConvoyManager` and
  `AshenReliefManager`) with different veto rules. **Pass 5 extracts it** — and must preserve the
  per-caller "ignore my own faction's raids" behaviour, which the refugee copy does not have.

---

## 14.4 Pass 4 — build record and test matrix

**Built 2026-08-01.** New: `data/scripts/exiles/AshenInterdictionManager.java`,
`data/scripts/exiles/AshenInterdictionStage.java`, `data/scripts/exiles/AshenRaidIntel.java`,
`data/scripts/marketconditions/ExileCrusadeHarassment.java`. Edited:
`data/campaign/market_conditions.csv` (row `exileac_crusade_harassment`, order 155),
`data/config/exiles/director_config.json` (new `interdiction` block + `targetMarkets`),
`data/scripts/exiles/ExilesModPlugin.java` (registration in `onNewGameAfterEconomyLoad` +
`hasScript` guard in `onGameLoad`).

**Verified offline:** all four new classes plus `ExilesModPlugin` pass `tools/janino-check.ps1`
(exit 0). `director_config.json` parsed and read back through the loose-JSON → strict-JSON path,
confirming every interdiction value the manager consumes, including the `timeScale` products.
**Not verified:** anything requiring the game to run.

### What Pass 4 does

Phase 2 and Phase 3 only, `maxActive` 1, cadence 30d, presence 30d. Presence-only ops on the
vanilla `RaidIntel` pipeline: `canRaid` is hard-false so no colony is ever approached; Crusade
Harassment lands on day 5 and lifts on withdrawal; `abortFraction` 0.5 delivers §6.1's *retreat
if reduced by more than 50%*. Tri-Tachyon pays 3,000/frigate-equivalent plus
`SYSTEM_BOUNTY_REWARD` rep for raiders the player kills. Ops post hidden and reveal on arrival
or on player battle contact.

Target discipline (ruling J5) is enforced in **three** places, not one: `pickTarget` only ever
walks the configured `targetMarkets`; `applyConditions` filters to `Factions.TRITACHYON`; and
`performRaid` (unreachable, kept as a net) does the same. Hybrasil is in the pool per Sean's
2026-08-01 ruling, with the commerce-tier feasibility gate expected to veto it early and open it
as FP climbs.

### Design decisions made during the build

1. **The loiter lever** — the mechanism finding and Sean's `boldWarScore` = 0.25 ruling. §6.1a.
2. **FP band 130–190**, capped below 200. §6.1a.
3. **Feasibility is the commerce tier's** (station strength excluded, mult 2.0): raiders never
   fight stations, and the assault-tier gate would have excluded all three systems permanently.
4. **`systemCooldownDays` 45**, set *after* the launch is committed rather than at target
   selection, so a fizzled launch does not burn one of only three candidates. With cadence 30 and
   `maxActive` 1 this rotates targets instead of grinding one.
5. **In-flight ops ignore phase changes** — only *launching* is phase-gated, matching
   `AshenReliefManager`. A raid ordered out at the end of Phase 3 may arrive during the next
   Phase 1; it is already underway, and turning it around reads worse than letting it land.
6. **Focus market = biggest Tri-Tachyon market in the system.** Doubles as the loiter target and
   the intel's "Target" line, so the two can never disagree.

### Live test matrix

| # | Test | Expected |
|---|---|---|
| 1 | Reach Phase 2 | Raid launches within ~1 cadence; log line gives FP, focus market and `bold=`. Nothing launches in Phase 1 or 4. |
| 2 | Follow one in | Intel hidden until arrival, then posts as "System Bounty — \<system\>". |
| 3 | Day 5 of presence | Crusade Harassment on **Tri-Tachyon markets only**. Donn and Cethlenn in Hybrasil must stay clean. |
| 4 | Withdrawal | Condition lifts on both success and early abort; log confirms the count lifted. |
| 5 | Kill raiders | Credits + Tri-Tachyon rep, payout popup a beat after the battle. |
| 6 | **Early war** (score < 0.25) | Raiders sit at the system centre. **No Omicron reaction at all** at Port Tse or Tibicena. |
| 7 | **Mature war** (score ≥ 0.25) | Raiders sit on the market; the station complement wakes and engages. `bold=true` in the launch log. |
| 8 | **Culann** | No Omicron strategic detachment at any interdiction FP. Check the Hybrasil case specifically if feasibility ever lets a raid in. |
| 9 | Save/load mid-op | Op resumes; no duplicate manager (the `hasScript` guard); condition survives. |
| 10 | Phase 3 | Raids continue at the same cadence (blockade fleet is Pass 6, not expected here). |

**Test-tuning note:** at `timeScale` 0.1 the cadence and presence compress to 3 days each, but
**hyperspace travel does not compress** — Providence to Magec/Mayasura is real distance at real
burn. With `maxActive` 1, one op can occupy the only slot for its whole round trip, so raids are
too sparse to observe under compression. **`maxActive` is therefore test-tuned to 3** (Sean,
2026-08-01) and is on the §9.2 pre-production checklist; production is 1 per §6.1's "one raid at
a time".

Consequence worth watching during the test: at 3 concurrent ops the 45-day `systemCooldownDays`
becomes the real limiter, since there are only three target systems. Expect all three occupied
and then a lull, rather than a steady trickle — that is the cooldown working, not a stall. If
Hybrasil is feasibility-vetoed at the time, only two systems are actually available.

### Live test RESULTS 2026-08-01 — ⚠️ PARTIAL PASS, three issues, no crashes

Sean's summary: *"pure chaos... a little hard to keep track of everything going on."* Note that
most of the density is the `maxActive: 3` **test tune**; production is 1.

**What passed.** 6 raids launched, travelled, arrived and withdrew. Zero exceptions. Crusade
Harassment applied 6/6 times, always to **exactly 1 market** (Tibicena or Port Tse — never Donn
or Cethlenn), and lifted on withdrawal. Target discipline (ruling J5) held. The `bold` flag
flipped correctly once war score passed 0.25.

**Issue 1 — the condition was invisible to the player.** Not a defect; a test-compression
artifact. `timeScale` 0.1 takes `presenceDays` 30 → 3 and `conditionDelayDays` 5 → 0.5, so
Crusade Harassment exists for roughly **2.5 game days**. Candidate fix: exempt `presenceDays`
and `conditionDelayDays` from `timeScale` the way relief's travel timeouts already are — the
presence window is the thing the player observes, so compressing it optimises away the only
visible output.

**Issue 2 — raiders attacked and defeated Tri-Tachyon's orbital stations** at Tibicena and Port
Tse. `canRaid` being false blocks **colony ground actions only**; an orbital station is a
*fleet*, and vanilla fleet AI treats it as an ordinary combat target. "They never touch planets"
was true; "they don't attack stations" was never actually implemented. (For the record these
were Tri-Tachyon's own stations — the log's only `complement lost` line belongs to an earlier
session, so no Omicron complement died in this test.)

**Issue 3 — Omicron woke at Port Tse while every raid was `bold=false`**, breaking tier 1 of the
§6.1a ladder:
```
874439  Omicron complement activated at Port Tse Franchise Station against exileashencrusade
```
The loiter position was not at fault: Port Tse sits at (2431, 5199), **~5,740u from Mayasura's
star**, well outside the 1,500u radius (measured from the save). The raiders *left* the loiter
token. A `RouteSegment` token is a "go here" hint, not a leash, and aggressive raider-flagged
fleets chase hostile traffic — which in Mayasura means traffic bound for Port Tse.

### Direction after the test (Sean, 2026-08-01) — NOT YET BUILT

- **`MEMORY_KEY_MAKE_NON_AGGRESSIVE` was proposed and REJECTED.** It would have fixed issues 2
  and 3 together, but Sean: *"non-aggressive raiders who arrive, orbit the local star for awhile
  and leave sounds more like an angry protest than a raid."* Correct — it solves the mechanism
  at the cost of the fiction. Do not re-propose it without addressing that.
- **Preferred direction instead: revisit the Omicron side.** Two options Sean raised, both
  unresolved: (a) keep the small Port Tse / Tibicena detachments *hidden* more robustly, or
  (b) **do not spawn them at all until some amount of game time has passed.** Option (b) is
  attractive because it makes the early-war quiet period a property of Omicron's own buildout
  rather than something Ashen has to tiptoe around — and it would apply to every future
  director, not just this one. Both touch `OmicronTriTachyonDefenseManager`, so they need
  their own scoping pass.
- ✅ **`maxShipSize` 3 → 2 (destroyers). RATIFIED AND APPLIED 2026-08-01.** Sean did not expect
  cruisers; 3 was an unforced choice on my part. Black Fleet commerce raids — the tier this was
  ported from — use 2; their assault tier uses 3. This also partly addresses issue 2, since
  destroyers are materially worse at beating an orbital station. **Untested in game.**

### Second live test 2026-08-02 — fleet composition, and two mechanism findings

Session run with Omicron raids/incursions and all Black Fleet activity **disabled** to isolate
Ashen. 6 raids launched, all reached SUCCESS, Harassment applied 6/6 correctly, **no runtime
exceptions**. Omicron woke at Port Tse again at `bold=false` (issue 3 reproduced).

**Observation (Sean):** every raid arrived as *one very large fleet plus one small one*, and the
large one made a bee-line for the Tri-Tachyon station and was beaten off. Preferred behaviour:
more, smaller fleets that circulate, hunt Tri-Tachyon *fleets*, and leave at the end of the timer.

**Cause — structural, not random.** `AssembleStage.getFP()` opens with

```
if (spawnFP >= getLargeSize(true) + getFPMedium())  → carve getLargeSize() off as ONE fleet
```

and `getLargeSize()` is the source faction's `approximateMaxFPPerFleet` — about **130** for Ashen's
roster. At a 130–190 FP budget the first carve therefore takes nearly the whole op into one fleet,
leaving one small remainder. Every time.

✅ **FIXED 2026-08-02 — `maxFleetFp` (default 50).** `HiddenSourceAssembleStage` now overrides the
two `protected` size getters (`getLargeSize`, `getFPLarge`), so the same budget splits into ~4
fleets of ≤50 FP. No reimplementation of the chunking loop; the rest of vanilla runs unchanged.
**Untested in game.**

#### Finding A — `MEMORY_KEY_MAKE_HOLD_VS_STRONGER` is a no-op for NPC combat, but IS shipped

It was proposed as the fix for the bee-line (aggressive hunters that avoid stronger opponents,
per vanilla's Remnant usage) and **disproved by decompiling** `campaign.ai.TacticalModule`:

```java
boolean var4 = Misc.isPlayerOrCombinedPlayerPrimary(var2);   // counterparty is the PLAYER?
if (!var3 && var4) {
   if (makeAggressive)   return ENGAGE;
   if (holdVsStronger)   var5 = true;      // only reachable when var4 is true
   ...
}
```

The flag is read **only when the other party is the player**, and only upgrades `DISENGAGE` →
`HOLD_VS_STRONGER` for all-civilian fleets. It has no effect on NPC target selection.

**What actually governs engaging a stronger opponent** is the strength ratio later in the same
method, gated on commander personality: `reckless` 0.25, `aggressive` 0.33, otherwise 0.5. That is
why the fleet-size cap is sufficient on its own — a ~130 FP fleet clears the bar against a station,
a ~50 FP fleet does not. One change, both symptoms.

✅ **The flag ships anyway, on Sean's call (2026-08-02), for the reason it actually works.** Being
player-scoped is a *feature*: the player may side with Tri-Tachyon, and raiders that break off
rather than suicide into a stronger player fleet is exactly right for a faction whose spec says
*run from more powerful fleets* (§6.1). Set in `AshenRaidIntel.createFleet`. It is **not** the fix
for the station bee-line — `maxFleetFp` is — and the comment there says so, so the two do not get
conflated later.

> **Method note.** Obfuscation mangles identifiers but leaves **string literals** intact, so the
> consuming class can be found by scanning `starfarer_obf.jar` for the flag string
> (`campaign/ai/TacticalModule` — one hit in 2,818 classes) and then decompiled with IntelliJ's
> bundled FernFlower:
> `java -cp "<IDEA>/plugins/java-decompiler/lib/java-decompiler.jar" org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler -dgs=true <in> <out>`
> Note that plain extraction of that jar **fails on Windows** — the obfuscator emits ~250-char
> class names that exceed the path limit — so scan entries in memory and extract only the one
> class you need. Use this whenever the question is *"what does this engine flag actually do"*;
> use an in-game test for *"does the resulting behaviour feel right"*.

#### Finding C — raiders dog-piling Tri-Tachyon's stations. FP tuning CANNOT fix this.

Third live test (2026-08-02, with `maxFleetFp` and the hull cap in): fleet count improved as
intended, but the raiders still converged on the station and knocked **Port Tse offline twice**.

Sean's hypothesis was an FP-balance problem — aggressive commanders (0.33) against a 100 FP
station. Decompiling shows that is not the calculation, in two ways:

1. The 0.33 lives in **two** places. In *target selection* it is **doubled to 0.66** and compared
   using **fleet points**; in *encounter resolution* it is raw and compared using
   `Misc.getMemberStrength`, a different scale entirely.
2. **A station is invisible to the target-selection tally.** `hasEnoughStuffAround`'s nearby-fleet
   loop is gated on `!var18.isStationMode()`, so a station contributes to **neither** the friendly
   nor the hostile total. The test reduces to

```
return var27 > var16 * threshold && var27 > var25;
//     var27 = own FP + nearby friendly FP
//     var16 = nearby hostile FP   ← station contributes ZERO
//     var25 = own FP
```

   With the station the only hostile present, `var16` is 0, so the first clause is trivially true
   and the second needs only one friendly fleet nearby. **Any raider with a companion sees the
   station as a free target regardless of how strong it is.** No FP number changes that.

   Corollary worth stating plainly: `maxFleetFp` made this facet *worse*. Four clustered fleets
   means every one of them has companions, so `var27 > var25` always holds. The cap was still
   right for composition; it was never going to help here.

3. **Position is not a fix either.** Omicron woke at Port Tse with every raid at `bold=false`,
   i.e. raiders crossed ~5,700u from the system centre unprompted — tactical pursuit of
   Tri-Tachyon traffic, which converges on Tri-Tachyon markets. The loiter token is a hint, not a
   leash (already established in the first test; confirmed again here).

✅ **FIXED 2026-08-02 — per-fleet do-not-attack.** `StrategicModule.isAllowedToEngage` opens with a
per-fleet `doNotAttack` set, exposed as public API via `StrategicModulePlugin.getDoNotAttack()`
(a `TimeoutTracker<SectorEntityToken>`). `AshenInterdictionStage.suppressStationTargeting()`
refreshes each raider's list with the system's Tri-Tachyon station fleets on the stage tick.
`isOkToPursue` then returns false **for that target only**, sending targeting down vanilla's avoid
path — which, because the entity is station-mode, actively steers away. Tri-Tachyon *fleets* stay
fully huntable, which is §6.1's actual intent. Config: `suppressStationAttacks` (default true).
**Untested in game.**

> **Consequence for the §6.1a ladder — option (c) will not work as a gate.** Sean chose "keep the
> centre loiter and wake Omicron explicitly" over an offset loiter point. That is fine as a
> *trigger* but useless as a *gate*: the evidence above shows raiders reach the 1,500u ring on
> their own at `bold=false`, so Omicron wakes early no matter what the stage does. Deterministic
> early-war quiet has to come from **Omicron's side** — Sean's own earlier proposal, *don't spawn
> the Port Tse / Tibicena complements until N game days have passed*, is the clean version and
> benefits every director rather than just this one. Not built; needs its own scoping pass.

#### Finding D — the anti-hotspot cooldown never worked, and it hid a condition collision

Fourth live test (2026-08-02, `presenceDays` raised to 300 → 30 compressed so a full raid could
be watched). Sean's verdict on behaviour: **no complaints.** Raiders flew past Tri-Tachyon's
stations without engaging, hunted Tri-Tachyon fleets and traders doing business with Tri-Tachyon,
and the do-not-attack fix held. No exceptions; Harassment balanced 6 applied / 6 lifted.

Two things the log showed that play did not:

**1. The Omicron strategic response was one raid, not two.**
```
628209  raid launched: Elpis -> Magec, FP=210 (warScore 0.225, fpMult 1.1125), focus=Tibicena
628634  Omicron response dispatched: omicron_cipher -> Tibicena, 137 FP, threat=raid_cd6e
```
`handleThreat` reads `assemble.getOrigSpawnFP()` **per raid** and never sums concurrent ops. Base
`spawnFpMax` 190 × the war-score multiplier crossed Tibicena's 200 threshold — the §6.1a ladder
firing exactly as designed, and it will do so in production once war score passes ~0.11.

**2. Two ops in one system broke the Harassment lifecycle.**
```
369355  applied to Magec   (op A)
414808  applied to Magec   (op B)
496504  op A withdrew -> LIFTED      <-- op B's raiders still on station
515780  op B withdrew -> lifted (no-op)
```
Whichever op withdraws first strips the condition from a market the other is still working. Same
collision ruling E.c identified for shared condition ids across directors; it applies equally to
two ops of the **same** director, which the original build did not consider.

**Root cause, and it is worse than the symptom.** `systemCooldownDays` was set **at launch**, but
an op outlives it easily (travel + 30d presence + return). So the flag expired mid-op and the
guard failed at *both* its jobs: it never blocked a second op in the same system — `maxActive: 1`
was quietly doing that instead — and it never delivered anti-repeat either, since a later op
could re-target the moment the previous one got home. **At the ratified `maxActive: 1` the
collision is unreachable, but `maxActive` is a documented player knob, and at 2+ it returns at
production durations.**

✅ **FIXED 2026-08-02, two changes:**
- `AshenInterdictionManager.hasLiveOpIn()` — target selection refuses a system that already hosts
  a live `AshenRaidIntel`. Direct, correct at any `maxActive`, self-corrects if an op ends early.
- The cooldown now starts in `AshenRaidIntel.notifyEnded()` instead of at launch, so **N days
  means N days of respite after the raiders leave** — what it always meant to say. Set to **30**
  (Sean). Pattern borrowed from `BlackFleetRaidIntel.notifyEnded()`.

**Untested in game.** Note the two knobs are unrelated despite similar names: `systemCooldownDays`
is the anti-hotspot timer; `conditionDays` is Crusade Harassment's own self-expiry backstop.

### Fifth live test 2026-08-02 — ✅ PASS

Everything from Findings C and D verified in play. Sean: *"Fleet behavior was excellent... no
complaints."* Raiders flew past Tri-Tachyon's stations without engaging and went after
Tri-Tachyon fleets and traders instead. **No warnings or errors from any Exiles code.**

- **Same-system collision fixed.** Perfect apply→lift alternation, never two applies in one
  system without an intervening lift: Mayasura 217926→244215, Magec 348226→360863, Mayasura
  425724→446493, Magec 607165→632517, Mayasura 704007→723939. 5 applied / 5 lifted, 6 launches
  with one still in flight at exit.
- **Station combat is incidental and correct.** Port Tse was drawn into fights happening in its
  neighbourhood. `doNotAttack` suppresses *initiation*; it does not (and should not) stop a
  station joining a nearby battle.
- **Omicron's response was again ONE raid crossing the threshold** — FP 206 vs Port Tse's 200,
  dispatched 1.8s after launch. Not two raids summing.

Two tuning items came out of it, both applied:

**1. `responseMinThreatFp` 200 → 250 at the outposts.** At 200 the ladder tripped every session
from war score ~0.11 (`spawnFpMax` 190 × mult 1.053), making a "strategic" response routine.
Sean asked for 300; **250 was recommended instead and taken**, because 300 is unreachable: the
hard ceiling on an Ashen op is `spawnFpMax` 190 × max `getFpMult()` 1.5 = **285 FP**, so a 300
threshold would disable the tier outright rather than making it rare. 250 needs war score ≥ 0.63.
Note this key applies to **every** hostile raider — Black Fleet commerce raids (150–250 FP) will
now essentially never trip it; their assaults (350–500) still will.

**2. `feasibilityMult` 2.0 → 3.5 — attempted, MEASURED, and REVERTED.** Hybrasil had never once
been selected across four sessions, so the multiplier was loosened on the theory that the gate was
marginally too tight, with logging added to `pickTarget` because
`WarSimScript.getEnemyStrength` sums **live** patrol strength and cannot be computed offline.

The very next run settled it:

```
Hybrasil REJECTED (defense 1482 > gate 656 = 187 FP x 3.5)
Magec    eligible (defense 38  <= gate 656)
Mayasura eligible (defense 101 <= gate 656)
```

Measured ranges: **Hybrasil 1243–1560, Magec 23–38, Mayasura 31–101** — fifteen to sixty times
the difference. Admitting Hybrasil needs a multiplier around **8.7–10**, which would push the
Magec/Mayasura gates past 1500 and switch feasibility off everywhere. **Reverted to 2.0.**

**The gate is working, and the target pool is two systems by geography, not by tuning.** Hybrasil
is the corporate heartland — two large markets, a military base, a patrol HQ, both high-tier
stations (§2). A ~170 FP force of Ashen destroyers loitering there would be annihilated. If
Hybrasil should ever be interdictable it needs a different mechanism — a per-system exemption, or
Phase-3-only access at much higher FP — not a multiplier. Note the gate never binds on Magec or
Mayasura anyway (defense 23–101 vs op FP 130–190); it exists precisely for the Hybrasil case.

### Sixth live test 2026-08-02 — ✅ umbrella gate verified

`exileomicron.escalation.rampDays` temporarily 40 (restored to 365 afterwards) so growth 0.85
landed near day 34 rather than day 310 — the Omicron growth clock is **not** compressed by this
block's `timeScale`.

```
 37654  Culann (77 FP)                        <- startup, heartland covered from day one
 37656  Eochu Bres (109 FP)                   <- startup
 37658  Culann strategic detachment (195 FP)  <- startup
 76495  growth stage: mining (progress 0.255)
115592  growth stage: fuel   (progress 0.556)
169120  Port Tse (36 FP)                      <- GATED, after 0.85
169120  Tibicena (54 FP)                      <- GATED
```

Clean separation, no errors, conditions balanced 2/2. The Omicron fleet Sean saw fighting in
Mayasura was **Port Tse's own complement** (activated against Ashen at 194071), not Culann's
detachment — and there were **zero `Omicron response dispatched` lines**, confirming the
`responseMinThreatFp` 250 change also holds (max op FP this session was 187).

> **What this test did NOT show.** At `rampDays` 40 the umbrella opened ~day 34, but Ashen's first
> raids need ~30–40 days in transit from Providence, so they arrived *as* the gate opened. The
> **mechanism** is verified; the intended early-war experience of raids meeting no defenders was
> not. At production `rampDays` 365 the gate opens ~day 310 and many raids land before it, so the
> shipped behaviour should be considerably more pronounced than what was observed.

#### Finding B — the nav-buoy mystery is SOLVED, and it is not Omicron-specific

```
RaidAssignmentAI.advance() → checkCapture() → checkObjectiveAction()   [BaseAssignmentAI:126]
```

`RaidAssignmentAI extends RouteFleetAssignmentAI extends BaseAssignmentAI`, and it calls
`checkCapture()` on a 0.3–0.7 day interval. **Every fleet on the vanilla `RaidIntel` pipeline
captures objectives** — nav buoys, sensor arrays, comm relays — belonging to a faction hostile to
it. (Non-hostile owners are skipped when they hold a colony in-system; hostile owners never are.)

So it was Omicron's **raid** fleets, not `OmicronPatrolAssignmentAI` — yesterday's hypothesis
looked in the wrong fleet population. It applies equally to **Black Fleet raiders and Ashen
interdiction raiders**, which will take Tri-Tachyon's buoys in Magec and Mayasura.

**RESOLVED 2026-08-02 — KEEP, and nothing needs suppressing.** Sean: the behaviour is *desirable*
for raiding fleets; his two reservations were Omicron **defense** fleets doing it, and anyone doing
it to a **Tri-Tachyon** buoy. Neither can occur:

- **No Omicron defense fleet has the code path.** `OmicronDefenseFleetManager` attaches only the
  custom `OmicronPatrolAssignmentAI` (a plain `EveryFrameScript`, no `BaseAssignmentAI` ancestry),
  and `OmicronTriTachyonDefenseManager` / `OmicronTriTachyonResponseScript` drive their fleets with
  direct `addAssignment` calls and no assignment-AI script at all. Only fleets on the `RaidIntel`
  pipeline call `checkCapture()`. What was observed was almost certainly an Omicron **raid** fleet —
  visually identical in-system unless you check the intel.
- **Tri-Tachyon buoys are already protected from Omicron by vanilla.** The gate skips an objective
  whose owner is *not hostile* to the fleet when that owner holds a colony in-system. Omicron is
  **FRIENDLY** to Tri-Tachyon (`ExilesSectorGen.java:138`) and Tri-Tachyon holds colonies in
  Hybrasil, Magec and Mayasura, so those buoys are skipped. Only a Tri-Tachyon buoy in a system
  where Tri-Tachyon has no colony would be eligible — an edge case.

Ashen raiders **are** hostile to Tri-Tachyon, so they *will* take Tri-Tachyon buoys in the raided
system. That is intended: "tiger by the tail."

*Caveat: capture is not logged, so the attribution above is inferred from code paths rather than
read from the log. If an Omicron defense fleet is ever seen doing it, check whether an Omicron raid
intel is live in that system before reopening this.*

### Superseded — Omicron captured nav buoys and sensor arrays (SOLVED, see Finding B)

Observed in play, cause NOT yet established. Vanilla's objective-taking behaviour lives in
`procgen.themes.BaseAssignmentAI` (~line 155): it walks `Tags.OBJECTIVE`, skips objectives its
own faction already owns, and skips non-hostile owners *that hold a colony in the system* —
otherwise it goes and takes them. That is the Remnant-family behaviour and would explain the
symptom exactly.

**But Exiles' Omicron fleets do not use it**: `OmicronDefenseFleetManager` attaches the custom
`OmicronPatrolAssignmentAI` (a plain `EveryFrameScript`), and nothing in `data/scripts/exiles/`
references `RemnantAssignmentAI` or `BaseAssignmentAI`. So the source is something else — a
different Omicron fleet population (raid, TT-defense response, or Nexus-spawned), or an
objective-ownership path unrelated to fleet AI. **First task next session:** identify which
Omicron fleet population did it, then decide whether it is even unwanted — an expanding machine
network claiming the sector's navigation infrastructure is arguably good fiction, but it should
be deliberate rather than inherited.

#### Finding E — fleet SIZE and fleet NAME are separate levers

`maxFleetFp` 50 → **45** (Sean, 2026-08-02), but the stated reason — stopping "Armada" fleets —
is **not** what that knob does, and acting on it alone would have made things slightly worse.

- **Size** comes from `getFP()`, which `maxFleetFp` caps.
- **Name** comes from the *type* chosen by `pickNextType()`, which Ashen's `fleetTypeNames` maps:
  `patrolSmall` → **Lance**, `patrolMedium` → **Fury**, `patrolLarge` → **Armada**.

Vanilla's `pickNextType()` returns `PATROL_LARGE` whenever
`spawnFP >= getLargeSize(true) + getFPMedium()`. Capping `getLargeSize` dropped that threshold
from ~175 to ~95, so **the size cap added earlier that day is what started producing Armadas**,
and lowering it further to 45 drops the threshold to ~90 — marginally more often, not less.

✅ **Fixed by overriding `pickNextType()`** in `HiddenSourceAssembleStage` to omit `PATROL_LARGE`
entirely, mirroring vanilla's alternating picker so consecutive fleets still vary. Raiders are now
Lances and Furies at ≤45 FP. **Untested in game.**

### Known-open after Pass 4

- **Untested in game as of session end 2026-08-02:** `maxFleetFp` 45 + the `pickNextType`
  Armada suppression (Finding E). Everything else in Pass 4 has been live-tested.
- **The interdiction target pool is TWO systems, by geography.** Hybrasil measures 1243–1560
  defense against Magec's 23–38 and Mayasura's 31–101; no `feasibilityMult` admits it without
  disabling feasibility everywhere. Settled, not open — but do not "fix" it with a multiplier.
- **Derelict farming.** Ashen-vs-Tri-Tachyon-vs-Omicron battles in Magec and Mayasura will leave
  recoverable hulls near the player, cutting against "Player Starts Behind". Watch during tuning.
- **`getSystemsWithActiveRaids()` now has a third consumer's worth of pressure**: Ashen raids are
  live `RaidIntel`, so `AshenReliefManager`'s veto will finally see non-Ashen raids too — and its
  own-faction exclusion (§4.1c) becomes load-bearing rather than theoretical. Still **Pass 5**.
  Note Ashen's own `hasLiveOpIn()` is a *fourth* near-duplicate of the same intel-scan idea — fold
  it into the extraction.
- **Interdiction adds no war score** either way. Score remains relief-only until Pass 7.
- **§4.1c relief raid-veto still never exercised** — it ignores Ashen's own raids by design, so it
  needs a Black Fleet or vanilla raid to coincide with a convoy. Opportunistic.

---

## Session close 2026-08-02 — state of play

**Pass 4 is functionally complete and live-tested across six rounds.** Behaviour Sean signed off
on: raiders arrive as several small fleets, ignore Tri-Tachyon's stations, hunt Tri-Tachyon fleets
and the traders doing business with them, apply Crusade Harassment, and withdraw. Tri-Tachyon pays
a per-kill bounty. Omicron's outposts start uncovered and gain complements as the network matures.

**Config: PRODUCTION VALUES RESTORED 2026-08-24 (Sean, by hand).** The §9.2 checklist is the
authority. All three deviations recorded here are now cleared: `timeScale` is **1**,
`interdiction.maxActive` is **1**, and `exileomicron.escalation.rampDays` is **365**. Only
`debugMessages` **true** remains, deliberately, while testing continues — it is on the
pre-production checklist, not a defect.
Sean also currently has Omicron raids/incursions and all Black Fleet activity **disabled** in
config to isolate Ashen testing — those are his test switches, not production defaults.

**Next up:** Pass 5, the rule-of-three extraction. Do not start it before the two untested items
above have had a run.

**Method notes worth carrying forward** (both earned the hard way this session):
1. **Decompile before trusting an engine flag.** `MEMORY_KEY_MAKE_HOLD_VS_STRONGER` was
   recommended from its name plus one vanilla usage site and turned out to be player-scoped —
   a no-op for the problem it was proposed for. Scanning `starfarer_obf.jar` for the string
   literal and running FernFlower on the one class took five minutes. See Finding A.
2. **Instrument before tuning.** One log line of `defense vs gate` replaced four sessions of
   guessing about Hybrasil and produced a definitive answer in a single run.

---

## 14.5 Pass 5a — build record and test matrix

**Built 2026-08-04** (the commit message says 08-03; the build and its test both ran on 08-04,
the planning session was 08-03). Two new classes in `data/scripts/marketconditions/`:
`ExileTimedCondition.java`, `ExileCommodityShortageCondition.java`. Seven existing conditions
converted: `ExileCorsairActivity`, `ExileCrusadeHarassment`, `ExileCrusadeStrike`,
`ExileReliefShipment`, `ExileSacked`, `ExilePriceSpikes`, `ExileRefugeeEncampments`.

**Verified offline:** all 9 changed classes and all 11 consumer classes pass
`tools/janino-check.ps1` (exit 0). Janino handles abstract classes and abstract methods without
complaint — that was the main unknown going in. **Not verified:** anything requiring the game to
run; see the matrix below.

### What Pass 5a does

Pure refactoring. **Zero intended behaviour change, and zero director files touched.**

- **`ExileTimedCondition`** owns `elapsed`, the frame-time-to-days conversion, self-removal at
  duration, `resetTimer()`, and the static add-or-refresh / remove-if-present helpers. Seven
  conditions had each grown a private copy of all five.
- **`ExileCommodityShortageCondition extends ExileTimedCondition`** owns `hitCommodityIds`, the
  import-dependent commodity picker, the availability modify/unapply pair, and the flipped-grid
  tooltip. Three conditions carried verbatim copies.

### Design decisions made during the build

**The commodity picker had THREE consumers, not two.** `ExileCrusadeStrike.pickCommodities` is a
verbatim copy of Corsair's and Harassment's. §6.2's ruling E.c anticipated the base class arriving
at two consumers with the Hegemony/League content as the third; the third already existed and
nobody had noticed. Rule of three met by measurement.

**Accessibility is NOT in the shared class.** Corsair Activity and Crusade Harassment crater
accessibility; Crusade Strike does not — sabotage burns a warehouse, it does not picket the
approaches. The original Pass 5 sketch called the shared layer "trade strangulation" and bundled
accessibility with the commodity hit, which would have fitted two consumers and silently changed
the third. The two that want it add two lines around a `super` call.

**Tunables stay as per-subclass public statics, read through overridable getters.**
`AshenInterdictionManager`, `AshenSabotage` and `AshenReliefManager` push config values in by
assigning those fields directly. Converting them to instance state would have broken every one of
those write-sites and dragged three director files into a refactoring pass. Reading them
polymorphically through `getDurationDays()` / `getHitCount()` / `getHitUnits()` preserves the
config plumbing exactly.

**The condition id is self-derived.** `advance()` removes via `condition.getId()` rather than a
subclass constant, so a subclass cannot report an id that disagrees with the CSV row that created
it. The static helpers still take an explicit id (no instance in hand).

**`final` dropped from every numeric tunable** on Corsair, Sacked, Price Spikes and Refugee
Encampments. This does **not** make them config-reachable on its own — that needs read-and-assign
lines in the Black Fleet and refugee managers, which is **Pass 8**, now widened from "Ashen config
sweep" to cover Black Fleet and refugee condition tunables as well (Sean, 2026-08-03: he wants every
war tunable reachable from one JSON; high-desire, low-urgency). 5a does the refactoring half so
Pass 8 is mechanical.

**Pass 8 also adds an `enabled` toggle to the `blackfleet.assault` block** (Sean, 2026-08-04). It
is the one activity block with no kill switch of its own — which is why three sacks fired in the
second live test even though only `raid` and `extortion` had been switched on. To be added when
the Black Fleet assault tunables are moved to config, not before.

**Pass 8 also adds live-hostility checks before Ashen ops launch** (Sean, 2026-11-07, from the
Nexerelin relations audit). Only `AshenInterdictionManager`'s target pick and `AshenStandDownScript`
consult `isHostileTo` today; the war clock, sabotage, rehearsal and the Culann strike do not, so a
peace made by a Phase-2 quest, a console command, or any future Nex change (the Crusade is
non-playable under Nex, so Nex itself cannot make that peace) would leave some ops firing at a
faction the Crusade is no longer at war with. The Omicron, Black Fleet and refugee directors all
check. Pattern: gate each launch on `ashen.isHostileTo(tritachyon)`, idle with a 30-day log line
otherwise, the way the Heg–League director rides a peace.

**`ExileBusinessIsGood` deliberately excluded** (Sean, 2026-08-03). It rolls a random per-application
duration and does memory cleanup plus `reapplyIndustries()` on expiry. It could inherit through the
`onExpire()` hook; its semantics differ enough that it was left alone.

**Conditions correctly NOT converted:** Corrupted Soil, Destitute Population, Fecund Soil, Luddic
Unrest, Pariah Colony, Refugee Influx, Refugee Strain. None are timed — they are permanent
conditions and stay on `BaseMarketConditionPlugin`.

### ⚠ Save compatibility

Moving `elapsed` into a superclass changes the serialized shape of seven condition plugins.
**Test on a fresh game.** A save from before 2026-08-03 may fail to load or come back with dead
timers. Acceptable per CLAUDE.md §4 (new-game narrative integrity over mid-save integration), but
do not spend a save you wanted to keep.

### Live test matrix

Behaviour should be **identical to Pass 4**. Anything that differs is a 5a regression.

| # | Test | Expected |
|---|---|---|
| 1 | Fresh game boots, no exceptions in `starsector.log` | Clean start |
| 2 | Sabotage fires → **Crusade Strike** lands | Availability drops on supplies/fuel + up to 3 import-dependent commodities; tooltip grid renders one commodity per row |
| 3 | Crusade Strike expires | Gone at `strikeDays × timeScale`; availability returns |
| 4 | Interdiction op → **Crusade Harassment** lands | −50% accessibility **and** the commodity grid on the focus market |
| 5 | Raiders withdraw | Harassment lifts immediately; accessibility and availability both restored |
| 6 | Second op on a market still harassed | Timer refreshes, effects do **not** stack |
| 7 | Relief convoy → **Relief Shipment** lands and expires | +1 stability, food/supplies up, crew down, crew price +50%; clears on schedule |
| 8 | Save / load mid-condition | Timers survive; no duplicate or orphaned conditions |

**Not exercised by an Ashen-only run:** `ExileSacked`, `ExilePriceSpikes`,
`ExileRefugeeEncampments`. Verified by compile, boot and inspection only. They are structurally
identical conversions, but a Black Fleet or refugee run is what would prove them.

### Live test RESULTS 2026-08-04 — ✅ PASS, no regressions

Sean ran a full war-clock cycle on a fresh game at 10× speed, `timeScale` 0.1. Behaviour matched
Pass 4; log verified independently afterwards.

**Log evidence (`starsector-core/starsector.log`):**
- **4 ERROR lines in 10 MB**, all four `ShipHullSpreadsheetLoader` on `flare` /
  `module_hightech_decor` — vanilla/other-mod noise, duplicated across two sessions. **Zero
  exceptions from `data.scripts`.**
- `ExileTimedCondition` and `ExileCommodityShortageCondition` both compiled and loaded under
  Janino in-game, along with all seven converted conditions.
- **7 ordinary sabotage successes**, each logging `Crusade Strike + N industries offline` —
  the commodity-shortage subclass applying repeatedly, clean (matrix #2).
- **1 special sabotage success** vs Culann/orbitalworks at `chance=0.27 (war score 0.09)`,
  followed by `Ashen Culann strike QUEUED for the Phase 2 exit` — §5.4 intact.
- `Ashen relief delivered to Maxios` — the plain timed subclass (matrix #7).
- **15 interdiction feasibility evaluations.** Hybrasil rejected at defense **1229–1277**, inside
  the previously measured 1243–1560 band; Magec (9) and Mayasura (45) eligible (matrix #4).
- Full clock traversal: Phase 1 → 2 → 4 → Phase 1 cycle 2, war score climbing 0.0 → 0.05 → 0.09
  → 0.14 → 0.18.

Matrix rows 3, 5, 6 and 8 (expiry, lift-on-withdrawal, refresh-not-stack, save/load) are
timing/visual and rest on Sean's in-game debug-message observation, which was clean.

#### Investigated and CLOSED — paired sabotage rolls at identical timestamps

Several ordinary sabotage rolls appeared twice in the same millisecond, sometimes against the
same market. Two hypotheses were considered and both are **wrong**: it is not an artifact of 10×
game speed, and it is not two independent checks or a double-registered manager.

**It is `"attemptsPerMonthPhase3": 2`** (director_config.json — *"the Crusade presses harder
during the blockade"*). `AshenWarDirector.advanceSabotage()` runs
`for (i = 0; i < attempts; i++) sabotage.attemptOrdinary(this)`, so Phase 3 makes two independent
target picks in one tick. Confirmed by correlation: every paired roll (125396, 128632, 131785)
precedes the `-> Phase 1 (Buildup), cycle 1` transition at 132826, and every roll after it is a
single. The 131785/131786 pair hit two *different* markets, which is the independent pick showing
its work. **Designed behaviour. No action.**

#### Not a defect — "Copies of campaign data in memory: 2"

Seen on screen after loading. This is vanilla's own dev-mode leak detector in
`com/fs/starfarer/campaign/CampaignState`, whose neighbouring string constants are
`enableMemoryLeakChecking`, `enableMemoryLeakCheckingDevModeOnly`, `MEMORY_CHECK_INTERVAL`,
`Memory leak detected!`, `The previous memory leak warning was a false alarm`, `calledGC`,
`getAllInstances`, and `WeakHashMap`.

Instances live in a **`WeakHashMap`**, so a count of 2 straight after a load is the expected
transient — the previous `CampaignState` simply has not been collected yet, which is precisely why
the class ships a "false alarm" retraction and an explicit GC call. **The string that would
indicate a real leak is `Memory leak detected!` with no following false-alarm line**, and it never
appeared. Both settings are `true` in *vanilla* `settings.json`; Exiles does not override either,
so this only prints in dev mode. Neither message reaches `starsector.log` — they go to the
campaign message feed only.

Pass 5a cannot be a cause: everything it added is a primitive static or a `String[]`. Nothing it
introduced can retain a reference to a `CampaignState`.

### Second live test 2026-08-04 — Black Fleet + Refugees enabled. ✅ PASS, and the overlap is REAL

Sean re-enabled Black Fleet and refugee activity for one run specifically to exercise the three
conditions an Ashen-only run cannot reach, then turned them back off before Pass 5b. Log rotated on
relaunch, so this log is that session only.

**Zero exceptions from `data.scripts`. Two ERROR lines**, both the same vanilla
`ShipHullSpreadsheetLoader` noise on `flare` / `module_hightech_decor`.

**All three remaining conditions now exercised — the 5a verification gap is CLOSED:**

| Condition | Evidence |
|---|---|
| **Sacked** | 3 applications: Derinkuyu Mining Station, Yama, Nachiketa |
| **Price Spikes** | 2 applications (Suddene, Orthrus), plus a clean removal through the "collector flagship lost → quiet collapse" path |
| **Refugee Encampments** | Sean observed directly: applied after 10 days in orbit, removed after the convoy departed. *(Not in the log — it reports through `debug()` to the campaign feed, not the logger.)* |

Corsair Activity also applied and lifted repeatedly across Galatia, Canaan, Magec and Naraka.

#### ⚠ BF × Ashen condition stacking — CONFIRMED, measured, unresolved

Previously theoretical (§14.4 known-open). It happened, in **Magec**:

```
165329  BF     Corsair Activity applied to 4 market(s) in Magec
165878  Ashen  Crusade Harassment applied to 1 market(s) in Magec  (focus=Tibicena)
168404  Ashen  raiders withdrew, Harassment lifted
206093  BF     corsairs withdrew, Corsair Activity lifted
```

**Same market, confirmed by code rather than inference:**
`BlackFleetCommerceRaidStage.applyConditions()` filters on hostility only — there is no
target-faction list — so every hostile market in the system receives Corsair Activity. Tri-Tachyon
is hostile to the Black Fleet, Magec's Tri-Tachyon market is Tibicena, and Tibicena is what the
Ashen raid logged as its focus.

**The measured effect on Tibicena for that window:**
- **−100% accessibility** (−50% Corsair + −50% Harassment)
- **Supplies and fuel at −4** — both conditions carry them on `ALWAYS_HIT`, so the −2 lands twice
- up to **six further commodities at −2**

**This is structural, not a freak alignment.** Both directors converge on Magec and Mayasura by
geography: Ashen because Hybrasil's 1229–1560 defense is unreachable, the Black Fleet because they
are hostile Core systems near its anchors. A near-miss second overlap occurred at 208417/215399.

**Assessment.** The accessibility stack is defensible and arguably good fiction — two factions
strangling one world, self-limiting because both lift on withdrawal. **The doubled commodity hit is
the riskier half**, and it is *invisible to the player*: each tooltip honestly reports its own −2,
so the market screen never shows −4. Neither condition touches stability directly (both are
deliberately stability-free), so there is no direct decivilization path; the indirect path is
vanilla's own unmet-demand effects, which is the CLAUDE.md priority-#4 guardrail.

**Ruling: measure before building** (Sean, 2026-08-04). We know it happens and roughly how often;
we do not know whether it actually harms Tibicena over a long run. If a cap is wanted later it
lives entirely inside `ExileCommodityShortageCondition` — one condition checking for the other's
presence, which is cross-*condition* awareness within the shared layer, **not** the cross-director
coupling ruled out as fragile. Watch Tibicena's stability and essential availability during the
next long run.

#### Open, low priority — refugee convoy patrol response has no visible intercept

The convoy-departure debug message ("ordered away by a security patrol") fired, but no intercept
was visible. **Viewer position is RULED OUT** — Sean travelled to the convoy's target market and
observed the whole lifecycle in-system (correction 2026-08-04; an earlier note in this doc guessed
he had been parked at Corvus, which was true of the *previous* run, not this one). So the message
fires while the player is present and watching, and nothing visibly happens.

Mechanism is `ExileRefugeeConvoyManager.findRespondingPatrol()` + `beginPatrolResponse()`, riding
vanilla's NPC-hassle behaviour. `findRespondingPatrol()` returns a fleet or null; the message
implies it returned one. Candidates to check when this is picked up: whether
`beginPatrolResponse()` actually issues an assignment the fleet acts on, whether the chosen patrol
is too distant to close before the day-30 self-departure fallback fires, and whether the convoy
departs on the fallback rather than on the patrol order (which would look identical in the log).

**Not a priority** (Sean, 2026-08-04) — he has a workaround in mind if it recurs. Recorded so it is
not re-diagnosed from scratch. The refugee convoy system is canon in
`docs/EXILES_REFUGEE_CONVOYS.md`, not this doc.

### Known-open after Pass 5a

- **5b not started.** Live-op intel scan (four near-duplicates), feasibility calculation,
  anti-hotspot cooldown. Target-picking stays un-unified by ruling.
- ~~**BF × Ashen condition stacking untested**~~ — ✅ **CONFIRMED 2026-08-04** in Magec on
  Tibicena: −100% accessibility plus supplies/fuel at −4. Structural, not a freak alignment.
  Ruled **measure before building**; see the second-live-test section above.
- ~~**`ExileSacked` / `ExilePriceSpikes` / `ExileRefugeeEncampments` unexercised**~~ — ✅ **CLOSED
  2026-08-04**, all three verified in the BF+Refugee run.
- **Refugee convoy patrol response has no visible intercept**, with the player present in-system
  (viewer position ruled out). Low priority per Sean; see the section above for what to check.
- **Pass 8 widened** to cover Black Fleet and refugee condition tunables, not just Ashen's.

---

## 14.6 Pass 5b — CLOSED without a standalone build (2026-08-05)

**5b was scoped before anyone had counted the duplication.** Investigation found that the shared
structure the pass assumed does not exist. Recording the reasoning here so the rejected items are
not re-proposed by a future session reading the old plan.

### Item 1 — live-op intel scan. PARTIALLY SURVIVES → folded into Pass 6

Not four near-duplicates of one thing. **Seven sites in three families:**

| Family | Question | Sites |
|---|---|---|
| A | "How many of *my* ops are live?" | `AshenInterdictionManager.countActive()`, `BlackFleetRaidManager.countActive(assault)`, `OmicronDirector.countLiveOpsForNode(node, incursion)` |
| B | "Is one of *my* ops live in *this system*?" | `AshenInterdictionManager.hasLiveOpIn()`, `OmicronDirector`'s inline `busySystems` build |
| C | "Which systems have *any* live raid?" | `AshenReliefManager`, `ExileRefugeeConvoyManager` |

Only **Family C** is near-verbatim. Family A's three sites each apply a *different* filter (none /
tier / node+incursion); with no lambdas under Janino, unifying that needs a filter interface plus
anonymous inner classes at three call sites — more code than it saves and harder to read. Family B
is two sites with different return types.

**Surviving work:** one helper, `getSystemsWithActiveRaids(String excludeFactionId)`, serving
Family C's two callers with the §4.1c own-faction exclusion preserved. ~20 minutes, save-safe
(methods only, no field changes). **Folded into Pass 6 as its first step** rather than given its
own pass and test window.

### ⚠ Correction — the "two definitions of live" was not a real finding

An earlier draft of this pass flagged that five sites check `isEnded() || isEnding()` while
`AshenReliefManager` and `ExileRefugeeConvoyManager` also check `isFailed()`, and presented it to
Sean as an accreted semantic inconsistency needing a ruling.

**It needs no ruling.** In vanilla `RaidIntel`, every path that sets `failStage` immediately calls
`endAfterDelay()` (lines 231–232, 243–244), so a failed raid is always already ending. The extra
`isFailed()` check is harmless redundancy. The inconsistency was inferred from code shape without
checking the engine — the exact mistake §14.4 Finding A already warns about. **Verify against the
engine before reporting a finding.**

### Item 2 — feasibility calculation. ❌ REJECTED ON THE MERITS. Do not re-propose.

**Five** call sites, differing in scope, formula and purpose:

| Site | Scope | Defense | Extra |
|---|---|---|---|
| `AshenInterdictionManager.pickTarget` | system | `enemyStrength` | instrumentation logging |
| `BlackFleetRaidManager.pickTarget` (commerce) | system | `enemyStrength` | grudge relaxation |
| `BlackFleetRaidManager.pickTarget` (assault) | system | `enemyStrength` + stations over **all hostile markets** | grudge relaxation |
| `BlackFleetRaidManager.pickVictim` | **market** | `enemyStrength` + that market's station | grudge relaxation |
| `OmicronDirector.pickTarget` | **market** | `enemyStrength` (cached per system) + that market's station | softest-target fallback |

The only line common to all five is `defense <= fp * mult` — a comparison, not logic. Nothing
reaches three identical consumers.

**And extraction would be actively harmful.** The differences are deliberate: commerce raids
exclude station strength *on purpose* (the ratified playtest-4 tier split — stations sit in orbit
and do not chase freighter-hunters; assaults fight ground battles and must count them). A shared
`defense()` helper would put two intentionally-different definitions behind one name and invite a
future contributor to "fix the inconsistency," silently undoing a decision that took a playtest to
reach. There is no later date at which this becomes correct.

### Item 3 — anti-hotspot cooldown helpers. ❌ REJECTED as zero-saving.

Twelve sites, four classes, six keys — every one either `memory.getBoolean(KEY)` or
`memory.set(KEY, true, days)`. A helper replaces one line with one line. This is idiom repetition,
which is what `MemoryAPI` already is; vanilla writes it the same way throughout.

The consistency argument does not rescue it either. There *is* a real semantic difference — the
Black Fleet starts its cooldown at **launch**, Ashen at **op end** (changed in Pass 4 when the
cooldown turned out never to have worked) — but that is a policy difference, not an idiom one, and
a shared helper would neither surface nor prevent it. If the idiom is ever centralized for
tidiness, Pass 8 is the home.

### What this means for the rule of three

Pass 5 was scoped on the assumption that three directors doing similar work must share extractable
structure. **The conditions half (5a) is where nearly all of it actually lived** — seven consumers
of one timer, three of one commodity picker, all verbatim. The directors deliberately differ,
because their factions deliberately differ. A smaller Pass 5 is the correct outcome, not a
shortfall: the rule of three is a test the code has to pass, not a quota to fill.

---

## 14.8 Pass 6a — build record and test matrix

**Built 2026-08-05.** `data/scripts/exiles/AshenBlockade.java` (new),
`data/scripts/exiles/AshenInterdictionManager.java` (blockade launch + config),
`data/config/exiles/director_config.json` (new `blockade` block).
Preceded by `data/scripts/exiles/ExileLiveOps.java`, the Pass 5b survivor, committed separately.

**Verified offline:** all touched classes pass `tools/janino-check.ps1` (exit 0);
`director_config.json` balance confirmed (54/54 braces, 16/16 brackets).
**Not verified:** anything requiring the game to run.

### O3 — RESOLVED GREEN before a line was written

**1. NPC-vs-NPC is a supported vanilla mode, not a hack.** `FleetGroupIntel` declares
`protected abstract boolean isPlayerTargeted()`; `GenericRaidFGI` implements it as
`return params.playerTargeted`, gating intel updates and whether `WarSimScript.getEnemyStrength`
counts the player's fleet. `BlockadeFGI.applyBlockadeCondition()` is generic on `targetFaction`.
The engine author's own comment on the `GenericRaidParams` constructor settles it:
*"now supported: making fleets hostile to arbitrary factions"*. **Caveat:** both shipped subclasses
(`PerseanLeagueBlockade`, `KnightsOfLuddTakeoverExpedition`) target `Factions.PLAYER`, so this is
supported-but-unexercised. Watch the first live test accordingly.

**2. `FGBlockadeParams.doNotGetSidetracked` is DEAD.** The entire block that reads it in
`FGBlockadeAction.directFleets` is commented out, with vanilla's note that enabling it would make
fleets *"1) not hassle the player, 2) not attack nearby enemies unless they bump into them."*
§6.4 lists it as one of "exactly the knobs the spec needs" — it does nothing.

**3. `NPCHassler` is NOT free from the blockade action.** §6.4 claims `FGBlockadeAction` includes it;
it never references it. It is opt-in, one line per fleet, as `KnightsOfLuddTakeoverExpedition` does
on its pickets.

### Corrections to §6.4

- **The worked example is `KnightsOfLuddTakeoverExpedition`, not `PerseanLeagueBlockade`.** Vanilla
  ships two payload actions: `FGBlockadeAction` scatters fleets across the system's **jump points**
  via `MilitaryResponseScript`; `FGBlockadePlanetAction` sets
  `primary = specificMarket.getPrimaryEntity()` and orbits **the market**. The spec's fiction — and
  Pass 6b's docking dialogue — require the second.
- **The accessibility penalty is not flat.** `getAccessibilityPenalty()` scales the configured value
  by `getRelativeFGStrength`: `< 0.75×` defenders → **0**, `< 1.25×` → **half**, else **full**.
- **Vanilla has no strength-based abort.** `FleetGroupIntel.shouldAbort()` returns false and nothing
  overrides it, so §6.4's "orbits until reduced below 50% strength" had to be built. It is ours,
  reading `RouteData.getExtra().damage`.
- **Vanilla display quirk:** `addAssessmentSection` prints the RAW penalty while the `Blockaded`
  tooltip prints the SCALED one, so the intel over-promises against a marginal blockade. Not our bug.

### Author rulings 2026-08-05

- **Accessibility penalty stays at vanilla's 0.6**, not the spec's 1.0 — *"the difference is small
  and we adjust fewer knobs that way"*. Combined ceiling with Crusade Harassment is **−110%**, and
  only where the Crusade decisively outguns the system.
- **`NPCHassler` reinstated**, reversing ruling F's cut. That ruling was made on build cost; the
  cost turned out to be one line.
- **Fleet shape: one named anchor plus pickets** (the KOLT shape), sizes `[6, 3, 3]`.
- **Blockade withdraws with the raid** — *"it would be vulnerable without the rest of the raid"*.
- **"Hussar" is a nice-to-have, not a requirement.** It cost nothing: vanilla has no generic
  blockade fleet name, and KOLT names its fleets with two lines in the creation hook. Shipped as
  "Hussar Blockade Fleet" and "Hussar Picket"; **no `.faction` or `fleetTypeNames` entry needed.**

### What Pass 6a does

Three overrides on `BlockadeFGI`; everything else is vanilla's.

- `createPayloadAction()` → `FGBlockadePlanetAction`, so fleets orbit the market.
- `configureFleet(size, fleet)` → names the largest fleet the anchor, names the rest pickets and
  attaches `NPCHassler` to them only. The anchor stays clean so it sits on the market — which is
  what Pass 6b needs something to intercept against.
- `shouldAbort()` → aborts when the force passes `abortFraction` damage, or when no Ashen
  interdiction op is live in the system. Deliberately a **pull**: it looks the raid up through the
  intel manager rather than holding a reference, so it cannot retain dead intel, cannot leak, and
  self-corrects across save/load.

Launch is hooked into `AshenInterdictionManager.launchOp()` **after** the raid intel is registered
(otherwise `shouldAbort()` finds no companion and kills the blockade on its first advance), gated on
`PHASE_BLOCKADE`. It does **no target selection of its own** — it inherits the raid's already-gated
system and focus market. §6.4's "least-defended Tri-Tachyon market" resolves to the same market
regardless: Magec and Mayasura hold exactly one Tri-Tachyon market each.

**Not built:** no `exileac_crusade_blockade` condition. Vanilla `Conditions.BLOCKADED` is used
directly, confirming §8's suspicion. No fleet management, travel, intel or cleanup.

### Live test matrix

| # | Test | Expected |
|---|---|---|
| 1 | Phase 3 reached | Blockade launches with the interdiction raid; nothing in Phases 1, 2, 4 |
| 2 | Fleets arrive | "Hussar Blockade Fleet" orbits the Tri-Tachyon market; two "Hussar Picket" present |
| 3 | `Blockaded` applies | On the Tri-Tachyon market, alongside Crusade Harassment |
| 4 | **Accessibility reading** | **Settles whether accessibility clamps at 0** — read the market tooltip with both conditions live |
| 5 | Strength scaling | Penalty reads −60% / −30% / 0 by relative strength |
| 6 | `NPCHassler` | NPC traffic turned away; no fleet-AI thrash |
| 7 | Raid ends | Blockade aborts with it; `Blockaded` lifts |
| 8 | Blockade mauled | Withdraws past 50% damage without waiting for the raid |
| 9 | Save/load mid-blockade | Fleets, intel and condition survive |
| 10 | `blockade.enabled: false` | No blockade; raids unaffected |
| 11 | **Docking still normal** | Tri-Tachyon markets dock exactly as before — the canary for 6b |

Test 4 is worth doing deliberately: it answers the accessibility-floor question by observation
instead of decompilation, and decides how much the Black Fleet triple-stack (§14.5) actually matters.

### Live test 1, 2026-08-05 — ⚠ launches confirmed, fleets never seen. UNRESOLVED.

**Blockades launch correctly and nothing throws.** Five launches logged across two runs with the
right parameters (Port Tse and Tibicena, sizes `[6,3,3]`, penalty 0.6); zero exceptions from
`data.scripts`. **No blockade fleet was ever observed in game.**

**The first run was a test artifact, not a defect.** Only one blockade launched, because blockades
trigger on raid *launch* during Phase 3, and only 1 of 6 raids launched inside a Phase-3 window.
Cause: `timeScale` 0.1 compresses the phase clock, cadence, presence and cooldowns but **not
hyperspace travel**, so the war clock outruns the raid pipeline. The `maxActive` comment in
`director_config.json` already documents the same artifact one level up. Measured concurrency
peaked at **2** and never reached `maxActive` 3 — the real ceiling is the **two-system target
pool**, so raising `maxActive` would have changed nothing. Retested with `phase3MinDays`/`MaxDays`
temporarily at 700/800, which produced four blockades — confirming the diagnosis.

**Still unexplained: the fleets.** Vanilla's `BlockadeFGI` logs nothing, so the launch line is the
last signal; the log cannot distinguish an early self-abort from a stalled prepare stage from
fleets that spawned but stayed abstract. The save was no help either — it holds the three Ashen
*managers* but neither `AshenBlockade` nor `AshenRaidIntel`, and since raids demonstrably work,
that absence proves nothing.

**Candidate causes, undistinguished:** (1) early `shouldAbort()` self-abort on the companion-raid
lookup; (2) never leaves prepare/travel; (3) fleets spawn at the Providence source but the route
stalls; (4) fleets stay abstract because `spawnFleets()` is `RouteManager`-driven.

### ⚠ TEMPORARY DIAGNOSTICS IN `AshenBlockade` — REMOVE WHEN RESOLVED

Added 2026-08-05 rather than guessing between the four candidates above. Gated behind
`AshenBlockade.DEBUG`; all log lines are prefixed `BLOCKADE-DIAG:`.

| Marker | Answers |
|---|---|
| `spawnFleets()` entry/exit | Is the spawn path reached, and does the route exist? |
| `configureFleet()` per fleet | Are fleets created, named and located correctly? |
| `shouldAbort()` reason, logged once | Which of the three abort conditions fired? |
| `applyBlockadeCondition()` first success | Did it reach the market and apply `Blockaded`? |
| `periodicUpdate()` heartbeat, on state change only | PREPARE → TRAVEL → PAYLOAD → RETURN trace |

Set `DEBUG = false` or delete the block and its call sites once the cause is found. **Pass 6a is
not signed off until these are out.**

### Live tests 2–4, 2026-08-06 — mechanism RESOLVED, behaviour improved, design question OPEN

**The blockade works end to end.** Diagnostic trace across three runs: PREPARE → TRAVEL →
`spawnFleets` (3 fleets, correctly named and sized) → TRAVEL → PAYLOAD → `Blockaded` applied at the
full 0.6 → `ABORTING -- no live AshenRaidIntel in <system>` when the companion raid ends. Every
piece of Pass 6a's own logic is confirmed working, including the withdraw-with-the-raid pull and
the do-not-attack suppression (`station targeting suppressed on 3 fleet(s) vs 1 station(s)`).

#### ⚠ TESTING CONSTRAINT — the blockade CANNOT be tested at `timeScale` 0.1

Travel is uncompressed while the payload window compresses tenfold, so the blockade never gets past
TRAVEL. This wasted two sessions. **`timeScale` 1 is required.** `forcePhase: 3` is the fast way in
— note its comment says it "jumps the clock once" but the code re-forces whenever `phase !=
forcePhase`, so it actually **pins** the clock. *(Comment/code mismatch — fix during cleanup.)*

#### Fix 1 — station attacks. `doNotAttack`, ported from §14.4 Finding C

Blockade fleets arrived and engaged Port Tse's station. Cause: `FGBlockadePlanetAction` is vanilla's
**siege** action — `orderFleetMovements()` aims a `MilitaryResponseParams(ActionType.HOSTILE, ...)`
at `specificMarket.getPrimaryEntity()`, and `ActionType` offers only `CANCEL` and `HOSTILE`. Its one
vanilla user is a *takeover*. `AshenInterdictionStage`'s suppression didn't cover them — it scopes to
the raid's route source, and the blockade is a separate FGI. Fixed per-fleet, refreshed on the tick.

#### Fix 2 — `makeFleetsHostile` is PLAYER-scoped. **Author find, 2026-08-06**

With it true the blockade **ignored the station and hunted the player across the system**. It calls
`triggerMakeHostile()` → `MemFlags.MEMORY_KEY_MAKE_HOSTILE` = `"$cfai_makeHostile"` — the
player-scoped campaign-fleet-AI family (siblings: `MAKE_HOSTILE_TO_PLAYER_TRADE_FLEETS`,
`AVOID_PLAYER_SLOWLY`, `ALLOW_PLAYER_BATTLE_JOIN_TOFF`), the same family §14.4 Finding A already
flagged. **Now false.** Nothing is lost — Ashen is hostile to Tri-Tachyon by faction relations, and
`KnightsOfLuddTakeoverExpedition` sets it false even when targeting the player's own colony.
Fleet attrition went from 3→2 to no losses at all once disabled.

#### Remaining behaviour — a jump-point blockade wearing a station's coordinates

Author observations after both fixes:

1. ✅ No longer attacks the player.
2. They **do not orbit** — they mill around nearby. `MilitaryResponseScript` is a response assignment
   in the vicinity of a point; vanilla's real orbit assignments (`FleetAssignment.ORBIT_PASSIVE` /
   `ORBIT_AGGRESSIVE`) are not used.
3. They **break off to pursue hostile fleets** — inherent to `ActionType.HOSTILE`.
4. They engage patrols leaving the planet, which **drags the station into the battle**, and being
   stronger they clobber it. **`doNotAttack` prevents target SELECTION, not battle PARTICIPATION** —
   the station still joins a fight occurring in its own envelope.

Vanilla's blockade is **system denial** (hold the gates; the accessibility penalty abstracts the
rest). §6.4 wants **station denial** (nobody docks, friend or foe). `FGBlockadePlanetAction` is the
closest vanilla offers and it is really a siege action.

#### 🔶 OPEN — author design question, 2026-08-06. NOT a bug; do not "fix" it unilaterally

> *"Why would a cause that is dedicated to the annihilation of an enemy and is capable of fielding
> forces that can blow past that enemy's defenses stop there?"*

Sean is stepping back to reconsider planetary blockades. Three framings were offered as
**suggestions only**, no ruling made:

1. **The spec may already answer it.** Phase 3 is pressure; **Phase 4 is the Culann strike** — the
   war does escalate to annihilation, once, against the heartland. Under that reading the blockade
   is what the Crusade can afford against an outpost while building toward the target that matters.
2. **Match fiction to strength.** `getAccessibilityPenalty()` already models "present but
   ineffective" (0 below 0.75× defenders, half below 1.25×). `[6,3,3]` sits well above that, which
   is *why* they can kill the station. Lower it and the blockade becomes imposable but not
   convertible into a kill.
3. **Lean in** — let a strong blockade escalate into a station assault. A real feature, own pass.

### Known-open after Pass 6a

- 🔶 **The planetary-blockade design question above.** Blocks sign-off; everything else is ready.
- **Blockade applies `Blockaded` with `fleets=0`.** Observed on an unobserved blockade: route fleets
  stay abstract until the player is near, while the condition is driven by the intel. Vanilla
  behaviour and probably correct — the blockade exists whether or not anyone watches — but it
  deserves a deliberate decision rather than a discovery later.
- ⚠ **Diagnostics still in** (`AshenBlockade.DEBUG`) — remove before sign-off.
- ⚠ **Config in heavy test state** — `timeScale` 1, `phase3` 700/800, shortened Phase 1/2, possibly
  `forcePhase`. Restore before production.
- **NPC-vs-NPC `BlockadeFGI` is unexercised in vanilla.** Supported by design; first real use.
- **Pass 6b not started** — the four-option dialogue, the live-every-time blockade check, the
  hand-off flag and the once-per-dock rep guard.
- The war-score-vs-blockade relationship is unconsidered: every Phase-3 raid gets one, regardless of
  war maturity. A `minWarScore` gate is a config-only addition if it turns out to be wanted.

---

## 14.9 Pass 6c — build record, live test 1, and fixes

**Built 2026-08-09** (`7d666c6`), **live-tested same day**, **fixed same day** (`2826531`).
Preceded by the cleanup/payload commit (`8679f2e`). Files: `AshenRehearsalStage.java` (new),
`AshenInterdictionManager.java`, `AshenWarDirector.java`, `AshenRaidIntel.java`,
`AshenBlockade.java`, `director_config.json`.

### Architecture

The op is structurally an **assault**, so it reuses the existing skeleton with one stage swapped:

```
HiddenSourceAssembleStage → TravelStage → AshenRehearsalStage → ReturnStage
                          + AshenBlockade companion (jump points)
```

`AshenInterdictionManager` was extended rather than duplicated — it already owned source picking,
cadence, config load and the blockade attachment, and Phase 3 no longer runs interdiction, so its
Phase-3 branch was free. One new stage class; no second `EveryFrameScript`, registration or config
path.

**Author rulings folded in at build time (2026-08-09):** no launch feasibility gate (fixed targets,
deliberate op — FP scaling plus the damage-abort carry the risk); `suppressStationAttacks` OFF for
the assault group and ON for the Hussars; `maxShipSize` 3 for the visual read even though Sean's
testing showed Hussars already beat these stations; skip `super.performRaid()`.

### Live test 1 — 3 war cycles. Escalation and effects PASS; two of my own bugs found

**What worked.** Escalation is exactly right: Tibicena at war score 0.18 (cycle 0) and 0.36
(cycle 1), Port Tse at 0.66 (cycle 2, `escalated=true`) against the 0.4 threshold. Effects landed
cleanly —

```
1380106  AshenRehearsalStage - Ashen rehearsal: struck Tibicena (tritachyon),
                               mining + patrolhq down 30d, unrest=guarded
```

`unrest=guarded` is `ExileUnrestGuard.isBattered()` correctly suppressing unrest on a market the
Black Fleet and Ashen both work over. **Sean saw "no effect" because he checked after the 30-day
window expired**, not because anything failed. *(Gap worth noting: nothing tells the player it
happened.)*

### 🔴 Finding 1 — the player's lever was silently disconnected. **MINE**

Three rehearsals ran; **one** reported an outcome. The war score across the entire run shows a
single `0.41 -> 0.53` (+0.12) and **no decrease anywhere**. Breaking a rehearsal cost the Crusade
nothing — the exact inverse of §6.4's design.

**Cause:** the stage reported from `finish()`, which only runs inside `updateStatus()`'s
`status == ONGOING` branch. An op that aborts on fleet losses, or ends before its action stage is
reached, skips it — which is precisely the case the failure penalty exists for.

**Fix:** report from `AshenRaidIntel.notifyEnded()`, the seam that always runs whatever path the op
took, and already trusted for the system cooldown. The stage keeps the verdict (`hasLanded()`).
Held by direct reference because `RaidIntel` exposes no stage accessor in the shipped API sources.

> **Lesson worth carrying:** the success path worked perfectly and the failure path was dead. A test
> that only ever succeeds proves half a mechanism. **Watch for the negative case explicitly.**

### 🔴 Finding 2 — the rehearsal sailed as Phase 3 was closing. **MINE, two causes**

Cycle 0 launched at `t=470291` against a Phase 3 that ended at `t=471327`.

1. It sat behind the **30-day interdiction cadence**, so it waited for a raid interval that had
   nothing to do with it. Now checked every tick, guarded by an int compare that short-circuits
   once launched — no allocation, no intel scan in the common case.
2. The overlap guard refused to launch while **any** Ashen op was live. Phase-2 raids outlive their
   phase by travel + 30 days presence + return, so the rehearsal was held hostage to a raid working
   a different system. Now `hasLiveOpIn(system)` — the same-system check Pass 4 added for this.

### Finding 3 — vanilla holds one gate, not both **[VERIFIED]**

`FGBlockadeAction.orderFleetMovements()` weights the response fractions:

```java
float fPrimary    = 1f;
float fNonPrimary = 1f / blockadePoints.size();
```

With Magec's and Mayasura's two jump points that is 1.0 vs 0.5, so fleets concentrate on one gate
and the other is effectively open — which is what Sean observed. Correct for vanilla's
player-directed use (mass on the likely approach), wrong for cutting reinforcement routes.
`AshenBlockade.SpreadBlockadeAction` gives every point the full fraction.

### Finding 4 — the two op types were indistinguishable **[RESOLVED]**

Sean reported "interdiction fleets appear to have cruisers now." **They do not share settings** —
`maxShipSize` is per-instance on `AshenRaidIntel` and each launch path passes its own; the log
confirms interdiction kept 2 throughout. What actually happened: rehearsal fleets use the same
intel class, the same `pickNextType()` Lance/Fury names and the same two systems, and Phase-2 raids
outlive their phase — so destroyer raiders and cruiser rehearsal fleets were on screen together.

**Naming [RATIFIED 2026-08-09]:** the station-attack group is **"Hussar"**; the gate-holders became
**"Blockade Fleet"** / **"Blockade Picket"**. Plain, not "Crusade Blockade" — the faction is already
the Ashen Crusade and that read as a stutter. Applied over the route list on the status tick,
because `AssembleStage`'s fleet-creation seam is not in the shipped API sources. **Watch this one
next run** — it is the fix least verifiable offline.

### ⚠️ CORRECTION — the API source extraction was broken, and three claims here were wrong

**A partial `unzip` produced 4 files where the archive holds 1947.** Several statements above and
in the Pass-6c commits were reasoned against that stub tree and are **wrong on their premise**:

| Claim made | Truth |
|---|---|
| `WarSimScript` not in the sources | **Present.** See below — the hypothesis it protected is disproven. |
| `RaidIntel` has no stage accessor | **`protected List<RaidStage> stages`** is inherited and directly accessible. The direct-reference approach still works and is arguably clearer, but the reason given for it was false. |
| `MilitaryResponseScript` / `ActionType` not inspectable | Present. The import was found by reading `FGBlockadeAction`'s own imports instead — right answer, wrong method. |

**Lesson: verify the tool worked before trusting what it says isn't there.** An empty search result
proves nothing if the corpus is empty. Re-extract and count before concluding a class is absent.
This is a variant of [[obfuscated-code-verification]]'s rule.

### ❌ Port Tse failure — the pirate hypothesis is DISPROVEN **[VERIFIED 2026-08-09]**

`WarSimScript.getEnemyStrength()` iterates **`Misc.getMarketsInLocation(system)`** and sums
`getFactionStrength()` for each hostile **market-owning** faction, deduplicated by faction id.
Passing pirate *fleets* never enter the sum — only a pirate-owned market in the system would, and
Mayasura has none.

**So the pirate activity was coincidence.** The real cause is unknown. The likeliest remaining
explanation is that `getFactionStrength(ashen, system)` counts only Crusade fleets *already
in-system*, so an assault group still arriving piecemeal can fail `canBeat()` against a
militarybase-backed Port Tse — but that is another hypothesis, and the last one was wrong.

**Do not guess a third time — instrument it.** A single log line in `canBeat()` printing both sides
of the comparison settles it, exactly as the Hybrasil 1243-vs-Magec-23 measurement settled the
feasibility question. See [[instrument-before-tuning]].

### ✅ FIXED — the source leak was the BLOCKADE intel, not the rehearsal **[VERIFIED 2026-08-09]**

Sean attributed it to the rehearsal fleets; the two launch together and read as one event, but
`AshenRaidIntel` is **clean** — `RaidIntel.getMapLocation()` returns the *target* system's
hyperspace anchor, its `getArrowData()` bails on `!isSourceKnown()`, and `AssembleStage` prints
*"The raid is currently assembling at an unknown location."*

`GenericRaidFGI` is the culprit, twice over:

```java
// getMapLocation()
if (PREPARE_ACTION.equals(getCurrentAction().getId()) || getDelayRemaining() > 0)
    return getSource();          // an Ashen market in PROVIDENCE
return getDestination();
```

```java
// FleetGroupIntel.getArrowData()
ArrowData arrow = new ArrowData(src, dest);   // draws PROVIDENCE -> target on the starmap
```

That arrow is precisely the "attack from a pirate station" presentation Sean described. Both are
overridden in `AshenBlockade`: the marker follows the destination at every stage, and no arrow is
drawn. Concealing where the Crusade operates from is recon the player should earn — the same reason
`isSourceKnown()` is false and the same reason ruling H.c makes finding Providence a quest.

### ✅ TRACE READ 2026-08-13 — feasibility is NOT the cause, and the diagnostic was in a dead path

**The instrumentation is removed.** What follows is what it settled, and the defect reading it exposed.

**The log holds seven runs, nine rehearsal launches, seven strikes.**

| Run | Launches | Result |
|---|---|---|
| 1 | Tibicena `t=470291`, Tibicena `t=1056612`, **Port Tse `t=1781561`** | 1 strike, **2 no-shows**, and **zero feasibility lines** |
| 2–4, 7 | 6 launches — 3 Tibicena, 3 Port Tse | **6 strikes, 6 of 6** |
| 5–6 | resumed save, one op in flight | session ended before it resolved |

**Run 1 is the 2026-08-09 live test and predates the instrumentation** — three rehearsals, one
reported, exactly as §14.9 records. **So the trace never observed the Port Tse failure at all.**

**What the 38 captured lines do settle: `canBeat()` is not the constraint.** Every evaluation
returned ENGAGE — not one hold, across both targets and every war-score tier — with crusade
strength 182–947 against defenses of 0–246 and `ashenFleetsInSystem` at 4–9 throughout. **The
piecemeal-arrival hypothesis is disproven** alongside the pirate one. Three subsequent Port Tse
rehearsals (war score 0.48, 0.705, 0.75) all landed.

**Run 1's first failure is explained: it is Finding 2.** Launch #1 sits at `t=470291` — the exact
timestamp in the finding that the rehearsal sailed as Phase 3 closed (`phase end 471327`). Already
fixed. **Port Tse's cause remains formally unknown but has not recurred in three attempts** since
the Finding 2/3 fixes.

#### 🔴 The end-of-op diagnostic was in `finish()` — the known-dead path. Same defect class as Finding 1.

Finding 1 moved the *war-score report* out of `finish()` because that method is only reachable from
`updateStatus()`'s `status == ONGOING` branch. **The diagnostic written to catch the failure case
was left behind in it.** Tracing the calls: `finish()` is reached only at the "targets empty" and
"elapsed > maxDays" sites, and `abortIfNeededBasedOnFP()` runs ahead of both and sets the status
itself — so an op aborting on losses, the likeliest real failure mode, emits nothing.

**Therefore "zero `NEVER EVALUATED` lines" was evidence of nothing.** Nine launches, seven strikes,
and not one failure line for the two that did not land.

Compounding it: `director.reportRehearsal()` announces through `debug()`, which writes to the
campaign message feed and **never to `starsector.log`**. A failed rehearsal left no after-the-fact
trace anywhere.

#### Resolution 2026-08-13 — scaffolding out, one permanent line in

- **Removed:** `DEBUG_FEASIBILITY`, `logFeasibility()`, `lastFeasibilityLog`, and the `finish()`
  block. `canBeat()` is back to four lines and a verdict.
- **Added:** `AshenRehearsalStage.getOutcomeSummary()` (the stage owns `elapsed`/`maxDays`, which
  are protected on `BaseRaidStage`), logged from **`AshenRaidIntel.reportRehearsalOutcome()`** —
  the `notifyEnded()` seam every path reaches — as
  `Ashen rehearsal ended -- <market>: LANDED|FAILED after Nd of Md`.
- Logged **before** the `director == null` check on purpose: an outcome that cannot be scored is
  the one most worth having.
- **Kept permanently, not as scaffolding.** Phase 4's countdown will be paced off these outcomes,
  and this is the only file-log record that a rehearsal failed.

Janino-clean. **Untested in game** — the next Phase-3 run should show one `Ashen rehearsal ended`
line per op, including any that fails.

#### Assault-group strength retuned 2026-08-13 — `spawnFpMin/Max` 160–220 → **120–160**

The same trace answered a second question. Sean's config note of 2026-08-09 already suspected it
(*"Hussar fleets already beat these orbital stations"*); the trace measured it. Nine launches at
209–293 FP, and the group never had a real fight: **crusade 182–947 vs defense 0–246, ENGAGE 38/38**,
with stations reading ~98–120 whether the market carries `patrolhq` or `militarybase`. Roughly −25%,
leaving a 2–3× margin at the moment of attack. `maxShipSize` stays **3** — the cruisers were always
for the visual read.

**Why the conservative cut and not the 100–140 alternative** (Sean's call; both were on the table):

1. **`canBeat()` overcounts. [VERIFIED]** `WarSimScript.getFactionStrength()` (line 476) sums
   `getEffectiveStrength()` over *every* non-station-mode fleet of the faction in the system — so the
   three Hussar **blockade** fleets sit in the numerator even though `suppressStationTargeting()`
   holds them out of the station fight. **Cutting this dial reduces real striking power faster than
   it reduces the number the gate reads.**
2. **Failure is the player's lever and it does not distinguish causes.** With no launch feasibility
   gate (ruling, 2026-08-09), a group that cannot clear `canBeat()` does not withdraw — it sits until
   `maxDays` and fails, costing the same **0.25** war score a player-broken rehearsal costs. Ambient
   under-tuning would write silently into the countdown's only player lever.
   **Correction 2026-09-19 (§14.11): until that date the "sits until `maxDays`" half was FALSE.**
   Vanilla's arrival check zeroed `maxDays` and failed the op on the same tick — the log holds three
   `port_tse: FAILED after 0d of 0d`. The sentence is true again now that the stage restores its
   window on arrival.

**Watch on the next run:** at `maxFleetFp` 90 the assault group now parcels into ~**2** fleets at low
war score (2–3 late) where it was 2–3 (3–4 late). If the striking force reads too thin, the fix is
`maxFleetFp`, which changes the parcelling without changing the total.

**Deferred, deliberately not bundled:** scoping `canBeat()`'s numerator to the raid's own fleets
would make FP tuning mean what it says, but it *raises* the effective bar at the same moment the FP
*drops* — two changes at once teaches nothing clean. Sequence it after this run.

---

#### Historical — the original instrumentation note, 2026-08-09

⚠️ **`AshenRehearsalStage.DEBUG_FEASIBILITY` is TEMPORARY. Remove it once the question is
answered** — the Pass-6a diagnostics lingered in a commit and that should not repeat.

Two log lines, both prefixed `REHEARSAL-FEASIBILITY`:

1. **Every time `canBeat()` changes its answer**, printing both sides and the split:
   `crusade=N vs defense=M (enemy=X + station=Y) -> ENGAGE|hold [ashenFleetsInSystem=K, elapsed=Dd]`.
   `ashenFleetsInSystem` is the point of the exercise: it tests the leading hypothesis directly,
   that `getFactionStrength` counts only fleets **already in-system** so a group still arriving
   piecemeal fails the comparison. Throttled on everything but elapsed, so a stable standoff logs
   once and any real movement prints immediately.
2. **On any failed op**, an end-of-run line with the last evaluation — or
   `NEVER EVALUATED -- fleets never reached the market`. `canBeat()` only runs from
   `canRaid()`/`autoresolve()`, so without this an op whose fleets never got close would leave the
   trace empty, and **"no line at all" and "line says hold" are very different failures.**

**What to look for:** if `ashenFleetsInSystem` is low while `crusade` sits just under `defense`,
it is the piecemeal-arrival hypothesis and the fix is patience (a longer window) rather than more
FP. If `crusade` is far under `defense` with the full group present, the FP band is simply too low
for a militarybase target. If the line never appears, the problem is upstream of the fight
entirely.

### Live test 2, 2026-08-10 — ✅ both rehearsals landed; five fixes

Source leak confirmed shut (no Providence marker). **Both rehearsals succeeded** — Tibicena
(`unrest=2`) and Port Tse (`unrest=guarded`) — and the feasibility trace worked:
`crusade=332 vs defense=155 (enemy=35 + station=120) -> ENGAGE [ashenFleetsInSystem=4]`. Nothing
resembling the earlier Port Tse failure recurred, so **that question stays open** rather than
answered; the instrumentation stays in to catch it if it does.

**Fix 1 — naming.** Fleets read as bare `"Hussar"`. That was literally what was specified, but it
was specified to stop the faction name doubling, and the result was a fleet with no faction in its
name at all. Now the full player-facing string `"Ashen Crusade Hussar Fleet"` alongside
`setNoFactionInName(true)` — spelled out rather than composed, so it can neither double nor drift
if `displayName` is edited.

**🔴 Fix 2 — a landed rehearsal could be marked FAILURE.** Sean saw Tibicena finish as a failure on
the intel screen despite the log confirming the strike. `abortIfNeededBasedOnFP()` runs first in
`updateStatus()` and flipped the stage to FAILURE because the group had dropped below its abort FP
**fighting the station it had just beaten** — and with status no longer `ONGOING`, the branch that
records SUCCESS never ran. *The op did the job and was marked a failure for the losses it took doing
it.* A landed rehearsal now short-circuits `updateStatus()` entirely; `rehearsalDone` is monotonic
and terminal. This was also corrupting the war score, which reads the same flag.

**Fix 3 — one operation, one listing.** The blockade and the op it escorts were two intel objects on
two pipelines, appearing as separate entries at different times. The blockade intel is now added
hidden and `AshenRaidIntel.createSmallDescription()` speaks for it via a live query. Fleets remain
visible in space.

**Fix 4 — blockade `prepDays` 7 → 0.** Both launch on the same tick, but the blockade sat in a
seven-day PREPARE while the assault group assembled immediately, so the gates were still unheld when
the station fell (Tibicena elapsed 18d, Port Tse 9d).

**Fix 5 — pirate stations were never protected.** Interdiction raiders engaged a pirate station as
well as Tri-Tachyon's, and the code says why: `suppressStationTargeting()` filtered on
`Factions.TRITACHYON`, so Kanta's station was simply never on the do-not-attack list. Now every
station in the system. Commerce-tier raiders have no business fighting any station — and this also
keeps the Crusade out of a war with Kanta nobody designed.

### War-score reporting timing — CONSIDERED AND REJECTED 2026-08-10

Reporting success at strike time (keeping failures at op end) was proposed and **rejected on the
evidence**. Recorded so it is not re-proposed.

**Verified:** `notifyEnded()` is reached from exactly two places in `BaseIntelPlugin`, and every
ending path funnels through `endAfterDelay(days)` — `endImmediately()` is `endAfterDelay(0)`. It
fires **exactly once on every path**. One seam, one guarantee, no cross-save state.

**The benefit was illusory.** War score is not player-facing (§3.4 makes the subsystem silent, and
`debug()` only writes to the feed under the dev-only `debugMessages` flag), so early credit has no
UX value. And its only internal consumers — the escalation threshold and FP scaling — are read at
the *next* rehearsal launch, a full cycle (~240d) away, which no return leg can affect.

**Residual risk, accepted:** `getBaseDaysAfterEnd()` is 3, so an intel removed from the manager
during that three-day ending window would skip the report. The system cooldown has ridden the same
window since Pass 4. **If a rehearsal ever ends with no war-score movement in the log, look here
first.**

### Live test 3, 2026-08-10 — presentation, and a hiding mechanism I had wrong

Eochu Bres (Pass 6d) confirmed good. Three intel findings, all from Sean's screenshots.

**⚠️ `addIntel(plugin, boolean)` is `forceNoMessage`, NOT `hidden`.** The blockade kept listing
separately — and reporting FAILED when it withdrew alongside a *successful* rehearsal — because the
"add it hidden" call only ever suppressed the notification. `AshenRaidIntel` is hidden because its
**constructor calls `setHidden(true)`**; the `addIntel(raid, true)` beside it was silencing a
message, and the comment claiming otherwise was right by accident. **`setHidden()` is the
mechanism.** Both comments corrected.

**The hidden blockade entry was also leaking Providence in prose** — its Status read *"Making
preparations in orbit around Steadfast, a size 4 colony in the Providence system…"*. The §14.9 fix
covered the map marker and the arrow, not the text. **Hiding the entry is therefore load-bearing for
source concealment, not merely tidiness.**

**Two intel OBJECTS are structural; two intel ENTRIES were not.** `econ.Blockaded.setParam()` casts
its parameter to `BlockadeFGI` and reads `getAccessibilityPenalty()` from it, so the vanilla
accessibility condition *cannot exist* without a live `BlockadeFGI`. Merging the two operations into
one intel would mean writing a bespoke condition and losing vanilla's fleet orchestration — to solve
a problem one line of hiding solves. **Do not re-propose a merge.**

#### Presentation [RATIFIED 2026-08-10]

`AshenRaidIntel` serves **both** op types, so every player-facing override is gated on
`isRehearsal()`. **Interdiction raid language is unchanged — Sean's explicit instruction.**
Rehearsals title as **"Ashen Crusade Blockade — `<system>`"** with a blockade-styled right panel and
the Bounty section retained.

**The body deliberately does NOT copy vanilla's blockade text.** That text promises the blockading
forces *"will not directly attack colonies in the system"* — true of a vanilla blockade, **false
here**, since the Hussars exist to crack the station. Copying it would have the intel lie about the
operation's whole purpose. The rewrite keeps the blockade's shape and pulls its numbers from the
same accessors vanilla uses (`getRaidStrDesc()`, `getOrigNumFleets()`, the live blockade's
penalty), then states plainly that a strike group will press an attack.

Dropping vanilla's raid body also removed a visible inaccuracy: it listed **Kanta's Den** as at risk
from the operation, when the rehearsal only ever targets its one configured market.

#### Bounty extended to the gate-holders [RATIFIED 2026-08-10]

Killing a Blockade Picket previously paid **nothing** — no credits, no `SYSTEM_BOUNTY_REWARD`
reputation — because `reportBattleOccurred()` gates on `RAIDER_TAG` **and** listener registration,
both of which `AshenRaidIntel` does in its own `spawnFleet()`, and blockade fleets spawn on
vanilla's other pipeline. They now get both. Safe to attach a fleet the raid does not own: that
handler keeps no per-fleet state and does no route lookup. Side effect, intended: fighting the
pickets reveals the operation, as fighting the Hussars does.

**Known edge case, accepted:** registration happens when the blockade spawns its fleets. If the raid
intel has already ended, they register nothing. With `prepDays: 0` they launch together, so this
should not bite — but it is the first place to look if bounties ever go missing.

### Known-open after Pass 6c

- ~~**Why the Port Tse rehearsal failed.**~~ **CLOSED 2026-08-13 as far as it can be.** Trace read:
  the failure predates the instrumentation, and **feasibility is ruled out** — 38 evaluations, all
  ENGAGE, and three later Port Tse rehearsals landed. One of run 1's two failures is Finding 2. The
  other has not recurred; if it ever does, the new permanent `Ashen rehearsal ended` line will catch
  it where the old diagnostic could not.
- **Fleet naming via routes** — works in principle, unverified in play.
- **Pacing is unmeasured.** Finding 1 means every earlier war-score reading is suspect: two
  unreported failures inflated the curve. The 3-cycle/~1.8-year projection in §6.4 has **not** been
  validated and cannot be until a run completes with failures actually scored.
- `interdiction.maxActive` is still **3** (production 1) — Sean is handling it manually.
- Nothing tells the player a rehearsal landed.

---

## 14.10 Pass 6d — Hybrasil world-state. ✅ **BUILT 2026-08-10, Janino-clean; CONFIRMED IN-GAME 2026-08-24**

> Built to the author rulings below. `ExilesModPlugin.applyHybrasilMirrorDamageOnce()`, registered
> in `applyEconomyPatches()` between the Asher and Jangala patches. **One item remains open: the
> custom description (decision 4) — the mirrors still carry vanilla's generic
> `stellar_mirror` text, so the backstory is currently implied by the wreckage and stated nowhere.**

Makes the repulsed Knights of Gilead assault of ~Cycle 204 visible from the first time the player
flies through Hybrasil, giving the countdown a causal origin. **Fully separable from the rest of the
director** — no war-clock, fleet or intel code is touched.

### What exists **[VERIFIED 2026-08-10]**

**The mirrors — there are three**, from vanilla `data/scripts/world/systems/Hybrasil.java:77-88`:

| Entity id | Orbit angle | Radius | Period | Faction | Type |
|---|---|---|---|---|---|
| `eochu_bres_mirror1` | 0° | 220 | 40 | `tritachyon` | `stellar_mirror` |
| `eochu_bres_mirror2` | 120° | 220 | 40 | `tritachyon` | `stellar_mirror` |
| `eochu_bres_mirror3` | 240° | 220 | 40 | `tritachyon` | `stellar_mirror` |

All three use `setCircularOrbitPointingDown(eochu_bres, angle, 220, 40)` and
`setCustomDescriptionId("stellar_mirror")`.

**The condition.** `solar_array` on `eochu_bres` is a market *condition*
(`com.fs.starfarer.api.impl.campaign.econ.SolarArray`), **not** an industry: `FARMING_BONUS = 2`
flat to `FARMING`/`AQUACULTURE`, and it suppresses `HOT` and `POOR_LIGHT`.

**Nothing in Exiles references the mirrors** — verified by grep across `data/`. `rules.csv`'s
`stellarMirrorOpenDialog` is gated on `$tag:stellar_mirror`, so it is tag-driven and removing
entities cannot orphan a rule. Descriptions live in `data/strings/descriptions.csv`
(`stellar_mirror,CUSTOM` and `stellar_mirror,GALLERY`).

**Both halves are needed and neither works alone.** Removing the condition without touching the
mirrors is invisible on the map — the player sees three intact mirrors and a farming penalty with no
explanation. Removing mirrors without the condition is the reverse: visible wreckage with no
mechanical consequence, and three mirrors' worth of scenery still feeding a bonus.

### Implementation

A **post-econ-load patch in `ExilesModPlugin`**, in the existing Gilead / Asher / Nachiketa family.
The pattern is `applyAsherChangesOnce()` verbatim:

1. Guard on a persistent-data key (`$exiles_hybrasil_patch_done_v1`) so it runs exactly once.
2. Resolve the market via `findMarketByPrimaryEntityId("eochu_bres")`; bail if null.
3. `market.removeCondition("solar_array")`, then `market.reapplyConditions()`.
4. Resolve the mirror entities by id from the Hybrasil system and remove or alter them per the
   decisions below.
5. Write the persistent key.

New-game only, per CLAUDE.md priority #5. No `EveryFrameScript`, no saved state beyond the flag,
nothing to orphan.

### Test items

- **Does `POOR_LIGHT` become visible on Eochu Bres?** `SolarArray.unapply()` calls
  `unsuppressCondition` for `HOT` and `POOR_LIGHT`. Neither appears in Eochu Bres's
  `startingConditions` (`tundra`, `population_6`, `habitable`, `farmland_poor`, `organics_trace`,
  `solar_array`), so unsuppressing *should* be a no-op — but "should" is why this is a test item and
  not a claim.
- **Farming output before and after.** Expect −2 supply. Eochu Bres is size 6 with `farmland_poor`,
  so this is a real cut to Tri-Tachyon food production — which **feeds** the planned "general food
  shortage" Phase-1 feature (CLAUDE.md §6) rather than fighting it. Worth measuring so that later
  feature is tuned against a known baseline.
- **Stability on Eochu Bres**, since it is also a §5.4 special-sabotage target and a Phase-4 unrest
  recipient (§3.2a). Three separate systems now push on one market; the deciv guard covers unrest but
  not lost food.
- Mirror interaction still works on any survivor (`stellarMirrorOpenDialog`).

### Author rulings — **RATIFIED 2026-08-10**

| Mirror | Fate |
|---|---|
| `eochu_bres_mirror1` (0°) | **Untouched.** The array still half-works. |
| `eochu_bres_mirror2` (120°) | **Knocked off station** — moved out to radius 340 at 155°, period 70. |
| `eochu_bres_mirror3` (240°) | **Destroyed**, replaced by a small debris field on its old station. |

- **All remain Tri-Tachyon.** They held the field; the surviving array is still theirs.
- **Debris field is deliberately poor** — radius 120, density 0.35, `baseSalvageXP = 0`,
  `defenderProb = 0`, no glow (the wreck is two years cold). Sean's "little in it": salvage is free
  loot, and pillar 2 is that the player starts behind. This is scenery, not a reward.

**The displacement is the detail worth understanding.** Vanilla puts all three mirrors on
`setCircularOrbitPointingDown()`, which keeps a mirror permanently facing the planet — that *is* the
working configuration. `mirror2` is switched to plain `setCircularOrbit()`, so it holds its own
facing instead of tracking: it visibly no longer aims at anything. Combined with the wider, slower
orbit, it reads as something knocked outward and never recovered, rather than something merely
moved.

### Decision 4 — the custom description. **PLUMBING BUILT 2026-08-11, PROSE PENDING**

**No new entity type is needed.** `setCustomDescriptionId` is per-**entity** on
`SectorEntityToken`, not per-type — vanilla's generator merely sets every mirror to the same id, and
consumers read `Global.getSettings().getDescription(entity.getCustomDescriptionId(), Type.CUSTOM)`.
**[VERIFIED both directions 2026-08-11.]** Sean's instinct that this was worth doing was right; his
expectation that it needed a replacement mirror object was not — it is one setter plus one CSV row.

Built: `data/strings/descriptions.csv` gains `exiles_eochu_bres_wrecked_mirror,CUSTOM`, and the 6d
patch sets it on **`eochu_bres_mirror2` only** — the displaced one. The intact mirror deliberately
keeps vanilla's `stellar_mirror` text, because nothing happened to it.

🔶 **The text is a PLACEHOLDER and is marked as such in the CSV's notes column. Player-facing prose
is Sean's to write** (CLAUDE.md §7). This is the only place in the pass where the reader is *told*
what happened rather than left to infer it, and the site explains the entire war clock.

### Sequencing note

6d touches no director code and can be built at any time, before or after Pass 7. It is the cheapest
remaining item in the Ashen stack and the only one that is pure world-building.

---

## 14.11 Rehearsal + Culann arrival window — BUILT 2026-09-19, Janino 3/3, UNTESTED IN GAME

**Origin.** A Heg-League run-3 read (that doc's §14.7) found that vanilla
`PirateRaidActionStage.updateRoutes()`, which runs once from `ActionStage.notifyStarted()`, does two
things to any stage that inherits it: it **overwrites `maxDays`** with its own 20–30 (+10–15 with a
station) roll, and if the group's in-system strength is below defenders + station at the instant of
arrival it **clears the target list and zeroes `maxDays`**, so `updateStatus()` autoresolves and
fails on the same tick. Sean asked for every Exiles raid stage to be checked for the same exposure.

**Survey result (all seven stages):**

| Stage | Exposed? | Why |
|---|---|---|
| `AshenInterdictionStage`, `ExileHegLeagueInterdictionStage`, `BlackFleetCommerceRaidStage` | No | Override `updateRoutes()` wholesale without calling `super`; constructor `maxDays` survives. |
| `ExileHegLeagueStrikeStage` | Was | Fixed in the Heg-League doc's §14.7 (same day). |
| **`AshenRehearsalStage`** | **Yes — fixed here** | Inherited vanilla's method. No Exiles window existed; the arrival gate contradicted the 2026-08-09 ruling. |
| **`AshenCulannStrikeStage`** | **Yes — fixed here** (inherits the rehearsal fix) | Its `canBeat()` is unconditionally true by design, but vanilla's own check ran underneath it. The climactic strike could be REPELLED on arrival without a shot. |
| `BlackFleetAssaultStage` | Yes — **left as-is (Sean, 2026-09-19)** | Assaults are running smoothly; see the BF systems doc for the documented behaviour. |
| `OmicronRaidActionStage` | Yes — **deliberately kept** | `hasChosenTargets()` reads vanilla's cleared target list to suppress announce-then-instant-fail noise (Eos Exodus, 2026-07-04). Overriding would break a designed behaviour. |

**Evidence in the current `starsector.log`:** every rehearsal outcome ended at "Nd of Nd" between
21 and 43 days — vanilla's roll, not any Exiles number — and four ended `FAILED after 0d of 0d`
or `0d of 1d` (three at Port Tse, one at Tibicena). Each of those charged the 0.25 failure score
for an op that never held over the target.

**Fix (same shape as the Heg-League strike stage):**
- `AshenRehearsalStage` gains a saved `configuredMaxDays` and an `updateRoutes()` override that
  calls `super`, restores the configured window, and logs one measurement line:
  `Ashen rehearsal ARRIVED at <System> -- <Market>: group strength S vs defense D (enemy=… +
  station=…) | vanilla arrival check accepted|found no beatable target, vanilla window Nd -> window
  Md`. The Culann strike prints the same line under the label `Ashen Culann strike`.
- New config: `rehearsal.maxDays` (default **40**, near the middle of the vanilla roll so default
  pacing does not shift) and `culannStrike.maxDays` (default **45**). Both parsed by
  `AshenInterdictionManager` and passed through the constructors.
- A pre-fix in-flight op restores `configuredMaxDays` as 0 and keeps vanilla's value; no
  migration needed, and a new campaign is not required.

**Behaviour change to expect:** an offscreen rehearsal now lands or fails at exactly 40 days after
arrival instead of 21–43; a rehearsal that arrives against a stronger defence now holds and re-asks
`canBeat()` per tick instead of failing at once. The Culann strike can no longer be repelled on the
arrival tick.

**Test items for the next run:**
- T1: every rehearsal outcome line reads `of 40d`; no `0d of 0d` unless the group was destroyed in
  transit.
- T2a (Sean's theory, 2026-09-19): the test campaigns run WITH Nexerelin, whose response fleets
  spawn from a market's defence reserve when a significant hostile force approaches. If the
  `ARRIVED` line's enemy figure is well above the interdiction-manager feasibility read for the
  same system, that is the likely cause of the `0d of 0d` failures. Ruling: do not counter another
  mod's feature unless it causes significant problems; the restored window is the mitigation.
- T2: the `ARRIVED` line appears once per rehearsal and once for the Culann strike; compare its
  "vanilla arrival check" verdict with the eventual outcome to see how often vanilla would have
  instant-failed a group that later landed.
- T3: rehearsal failure count per cycle drops relative to the pre-fix log; watch the war-score pace
  and be ready to retune `scorePerRehearsalFailure` if the countdown now runs faster than the
  §3.2a intent.

#### Live test — Sean's 5-year Nexerelin run, READ 2026-09-20. ✅ FIX CONFIRMED; pacing question raised

Fresh campaign, ~1,780 game days (c206 → c210), Nexerelin enabled, non-Exile start, player
never fought. Zero Exiles exceptions.

- **T1 PASS.** 7 rehearsals, **7 LANDED, every one `after 40d of 40d`** (all offscreen, so each
  resolved at the configured window's end). No `0d of 0d` anywhere. Pre-fix baseline was 4 of 20
  outcomes instant-failing.
- **T2 PASS, and the fix did real work.** All 8 `ARRIVED` lines printed once. Two arrivals met a
  defence vanilla would have failed on the spot: Port Tse at group 328 vs defence 491 (vanilla
  accepted only because it measured before the group was fully in) and Port Tse at 299 vs 1013
  (`found no beatable target, vanilla window 0d -> window 40d`). The first **held 40 days, re-asked
  `canBeat()` at autoresolve, and LANDED** — the defence had dispersed, exactly the behaviour the
  2026-08-09 ruling described. The second was still holding at log end.
- **T2a (Nexerelin response-fleet theory): NOT RESOLVED.** The log carries no Nex response-fleet
  spawn lines under any name (Nex may not log them at INFO). Rehearsal arrivals saw enemy strength
  10–893 at Port Tse against a launch-time read that is not logged for rehearsals; the Heg-League
  record (§14.8 there) has the paired launch/arrival numbers and the same open question.
- **T3 — the countdown is FAST without a player.** War score 0.18 → 0.44 → 0.74 → 1.0 across
  cycles 0–3; the **§7.0 telegraph fired at 0.87 (~day 700) and the Culann strike launched on
  ~day 796 (cycle 3, c208)**. It arrived at group 3393 vs defence 1555 (enemy 1315 + station 240),
  held the 45-day window and **SUCCEEDED: heavy batteries 30d, spaceport 60d, refining 180d,
  orbital works 360d, military base 360d, nanoforge STOLEN**, after which the loop ran 1 → 2 → 3
  as designed and Port Tse was rehearsed against once per cycle (5 more, all landed). With 100%
  rehearsal success and no player interference the climax lands about 2.2 years in. **Whether
  that is the intended pace is Sean's call**; the levers are `scorePerRehearsal` (0.12, seven
  landed), relief `scorePerRelief`, and the Phase 1 length. Nothing here is a defect.
  **Sean, 2026-09-20 — thinking about it, with a new datum that applies to EVERY director:** one
  game year is roughly one hour of real play at normal speed, and he spends many multi-hour days on
  a playthrough, speeding to x5 mostly for travel. The "about 4 game years" baseline that the
  Exiles war clocks were built around therefore brings everything to a head around where a real
  playthrough's mid-game begins. No ruling yet; treat every director's multi-year cadence as
  provisional until he decides whether the baseline itself moves.
- The Culann strike's own `CULANN STRIKE ARRIVAL` measurement line and the new `ARRIVED` line
  both printed; the pair agree (crusade 3393 vs defence 1555).

---

## 14.7 Post-Pass-7 director review — SCHEDULED 2026-08-05

**A review, not a refactor.** No defect currently justifies rewriting the seven sites above; the
risk is in what nobody has looked for, not in what has been read.

**Timing:** after Pass 7. Passes 6 and 7 both add director code, so an earlier review would examine
code about to change. Its value scales with how much of the stack is finished.

**Why a different reviewer.** Sean's call, and correct. The author of code is its worst reviewer —
it re-derives its own assumptions rather than testing them, as the `isFailed` correction above
demonstrates within a single session. More importantly, **this document is written in the previous
author's voice**: "settled, do not reopen", "ruled", "verified". Those markers exist for
continuity and are actively harmful to a review, because they map exactly the areas the author was
most confident about — which are the areas most worth checking.

**Briefing notes for whoever runs it:**
- Rulings marked settled **may be challenged with evidence**, but not on preference. If the
  evidence contradicts a ruling, say so; do not re-argue a design call on taste.
- **Janino, not javac.** No lambdas, streams, `var`, diamond operator or try-with-resources.
  Odd-looking verbose code is usually a deliberate workaround — see CLAUDE.md §7.
- **Verify against the engine, never from an API name.** Two findings in this document were wrong
  on exactly that basis (§14.4 Finding A; §14.6 above). Decompiling one class takes five minutes.
- The reviewer cannot run the game. Separate findings into **verified by code** and **needs a live
  test**, and do not present the second kind as the first.

**Known live questions to carry in** (not an exhaustive scope):
- BF × Ashen condition stacking on shared Tri-Tachyon markets — measured, unresolved (§14.5).
- The §4.1c relief raid-veto has **never been exercised** since Pass 3.
- `ExileCorsairActivity` applies on hostility only, with no target-faction filter, so it reaches
  refugee enclaves as an area effect.
- The refugee convoy patrol response fires with no visible intercept, player in-system.

---

## 13. Reuse map

| Need | Source |
|---|---|
| Op launch, feasibility gating, distance weighting, anti-hotspot | `BlackFleetRaidManager` |
| Presence-only raid + timed condition lifecycle | `BlackFleetCommerceRaidStage` |
| Assault stage, target discipline, autoresolve | `BlackFleetAssaultStage`, `OmicronRaidActionStage` |
| Hidden-until-arrival intel + per-kill bounty/rep | `BlackFleetRaidIntel` |
| Single-fleet travel/orbit/condition/depart lifecycle | `ExileRefugeeConvoyManager` |
| Condition patterns (stability, availability, player prices, self-expiry) | `ExileCorsairActivity`, `ExileRefugeeEncampments`, `ExileSacked` |
| Bombardment visual, `RecentUnrest`, industry disruption | `OmicronRaidActionStage` |
| Live-raid intel scan (no memory flag needed) | `ExileRefugeeConvoyManager.getSystemsWithActiveRaids()` |
| Decivilization safety | `ExileUnrestGuard` |
| Refugee targeting canon | `ExileProtectedFactions` |
| Tri-Tachyon defensive response | `OmicronTriTachyonDefenseManager` (**no changes needed**) |
| Config load pattern, transient reload-on-restart | all three existing managers |

---

**2026-09-28 cross-reference: window-end fix (built, untested in game).** By Sean's ruling, the
op's action stage now resolves at the end of its window on the strength comparison
(`autoresolve()`) whether or not the player is in spawn range. It used to fail outright
(`finish(...)` with no strength check) when the player happened to be within 1.6 LY at that tick. A
`WINDOW END` log line records each resolution. Affected here: `AshenRehearsalStage` and the
inherited `AshenCulannStrikeStage` (Ashen); `BlackFleetAssaultStage` (Black Fleet; its window length
is unchanged). Full record: `docs/EXILES_HEG_LEAGUE_WAR.md` §14.14.10–§14.14.12.

**2026-09-28 ruling (Sean): blockade break-off aligned to vanilla.** `blockade.abortFraction` 0.5 → 0.67, superseding §6.4's "orbits until reduced below 50% strength". Reason: vanilla aborts a LIVE fleet group whose surviving FP falls below 33% of what it spawned (FleetGroupIntel.advance) -- that is what really ends an on-screen blockade -- while this setting reads route damage, which vanilla never writes for live blockade fleets, so the 0.5 never fired. The earlier "vanilla has no strength abort" statement (config, AshenBlockade, AshenInterdictionManager) was wrong and is corrected. Record: `docs/EXILES_HEG_LEAGUE_WAR.md` §14.15.12.
