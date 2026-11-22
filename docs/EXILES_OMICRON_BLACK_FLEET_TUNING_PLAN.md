# Omicron and Black Fleet Raid Tuning — Strength Baseline and Plan

Created: 2026-08-29  
Approved: 2026-08-30  
Status: **OMICRON REFACTOR SUCCESSFULLY COMPLETE — AUTHOR ACCEPTED 2026-09-06 AFTER FINAL LIVE TEST**  
Black Fleet Phase B1 freight implemented 2026-09-15; offline checks pass, campaign sampling pending.
See `EXILES_BLACK_FLEET_SYSTEMS.md` freight section for the current implementation contract.

This note records the first apples-to-apples strength comparison for Omicron and the Black
Fleet, the observed campaign cadence, and the approved tuning plan. Sections 1–8 preserve the
evidence behind the decision; section 9 is the ratified implementation contract and section 11
records the implementation boundary. The first intact-network live run is recorded in §10.1; no
numeric tuning change was approved from that sample.

## 2026-09-06 approved support/guard amendment

This amendment supersedes conflicting support formulas and reconstruction times in the historical
contract below. Tests 2 and 3 showed mechanically correct recovery but insufficient strategic reward
for attacking support stations rather than the Nexus. The following four changes are implemented:

1. All eight configured Fuel/Mining assets, plus fallback support-asset creation, use **240-day**
   reconstruction instead of 120 days. Nexus reconstruction policy is unchanged.
2. Fuel efficiency is `0.20 + 0.80 × effectiveFuel / 4`. At four effective Nexuses, four/three/two/
   one/zero Fuel gives 9.0/7.2/5.4/3.6/1.8 readiness per year. One Fuel loss therefore removes 20%
   of intact production for its downtime; readiness already banked and deployed attacks remain.
3. Mining multiplies the **entire age-interpolated raid/incursion combat budget**, not just maturity
   growth: `0.20 + 0.80 × clamp(effectiveMining / 4, 0, 1)`. Four/three/two/one/zero effective Mining
   gives 100/80/60/40/20% strength at every age. Config key: `combatMaturity.miningBaseEfficiency`.
   Full-network start/mature bands, maturity curve, cores, objectives, and quest multiplier remain
   unchanged. Before Lattice's Mining Station is built, the starting three Mining stations yield
   80% strength. This is an intended consequence of whole-budget scaling.
4. One of each full Nexus's existing two guards is assigned passive orbit, non-aggression,
   hold-versus-stronger, and do-not-get-sidetracked flags. It retains fleet AI and hostility so normal
   battle joining remains available. No ignore-other-fleets/non-hostile flags are used. The other
   guard and three patrols remain mobile. No fleet-count, FP, replacement-delay, or sprite changes.
   Existing eligible guards can be adopted when manager tiers are reapplied; no duplicate is spawned.

### Evidence motivating this pass

- **Test 2, Cipher Fuel destroyed:** approximately 418 campaign days from the common baseline;
  readiness fell from 9.0 to 7.65/year at N4/F3/M4, then recovered after the former 120-day rebuild.
  Five new raids (four successful, one failed) and two incursions (one successful, one still active).
  The former single-loss reduction was only 15%, for at most about 0.444 readiness foregone.
- **Test 3, Vigil Mining destroyed:** approximately 145 campaign days; N4/F4/M3 left readiness at
  9.0/year. A 197-FP raid failed; a 463-FP incursion remained active. The old Mining rule reduced
  sampled bands only from about 123–218 to 114–202 raid FP, and 431–687 to 391–624 incursion FP.
  Reconstruction restored Mining after 120 days. This supported stronger, longer-lasting attrition.

### Final live test and completion — 2026-09-06

`OmicronDirector`, `OmicronDirectorConfig`, and `OmicronDefenseFleetManager` compile with the game's
Janino compiler. The author accepted the Omicron refactor as successfully complete after the combined
support-loss test and direct Nexus-guard verification. Future balance reviews are optional tuning,
not unfinished implementation or a blocker to this completion status.

- Test branch: `save_Issac_3686564620849347735` to `save_Issac_1674078845096505737`, approximately
  **436.8 campaign days**. Latest-session log evidence begins at line 275178 in the reviewed log.
- Destroying `cipher_fuel_1` reduced readiness from 9.0 to **7.2/year**. Destroying
  `vigil_mining_1` reduced effective Mining to three without further changing readiness.
- During reduced Mining, new launches were raids of **158, 151, and 134 FP**, plus a **464-FP
  incursion**. Their age-adjusted reduced bands were respectively 98–174, 112–200, 122–218,
  and 345–549 FP. The 134-FP raid fell below its intact-network minimum of about 152 FP,
  supporting the intended whole-budget reduction.
- Both stations rebuilt and their network contributions recovered. The configured downtime is
  **240 days**; log wall-clock timestamps do not independently measure exact campaign-day downtime.
  The final save has both destruction flags cleared and no pending support rebuild timestamps.
- Excluding the inherited attack, **five raids and two incursions** launched: four raids and both
  incursions succeeded; the final 306-FP raid remained unfinished. All four operations launched
  with reduced Mining eventually succeeded. This is retained as balance evidence, not a defect.
- Two raids launched with readiness barely above 1.0, demonstrating readiness-limited launches.
  Banked readiness cushioned the initial Fuel loss; a 20% production reduction is not a promise
  of exactly 20% fewer launches in a finite run.
- The author directly confirmed that a Cipher guard **refused to leave close Nexus orbit** and
  **joined combat when the Nexus was attacked**. This validates the requested behavior in that test;
  reverse-direction battle joining and replacement edge cases are not separately claimed as tested.
- No runtime errors appeared in the reviewed session segment. Unrelated MagicLib achievement/trail
  warnings were present. The bombardment unrest safeguard activated at Tartessus.

Existing offensive fleets are not resized. The maturity clock's first-successful-launch origin is
deliberately retained. Black Fleet is untouched; its freight work remains separate and unimplemented.

## 1. Executive findings

1. **Omicron's fleet quality is correctly Remnant-class, but its mature operation budgets are far
   above a single ordinary Remnant fleet.** An early raid is approximately one healthy-Nexus
   Remnant fleet. A mature raid is roughly one to two strong Ordos. An incursion is a multi-Ordo
   operation from the beginning and reaches 700–1,100 combat FP at maturity.
2. **Black Fleet assaults are stronger than most ordinary pirate-base raids, but not categorically
   stronger than the largest tier-5 pirate raids.** Their better officers and anchor-supplied ship
   quality give them an additional qualitative edge. The cruiser hard cap prevents the current
   assault tier from expressing that edge through capitals.
3. **Black Fleet commerce raids are above low-tier pirate pressure, not above all pirate raids.**
   Their mature 150–250 total-FP band sits between the average tier-2 and tier-3 pirate-base raid,
   and the destroyer cap deliberately keeps them a light operational tier.
4. **Headline operation FP is not combat FP.** Omicron assigns 100% of its budget to combat ships.
   Black Fleet generation currently spends 10–15% on tankers and another 10–15% on troop
   transports, leaving 70–80% (75% on average) for the combat allocation.
5. **The current Omicron scheduler is opportunity-limited rather than production-limited.** In the
   completed test log, only about 22.5% of raid/incursion timer opportunities produced an operation.
   The remaining nodes therefore have enough unused attempt capacity to fill most target openings
   after one or even two Nexuses are destroyed. This is why a Nexus kill may not visibly reduce
   attacks promptly even though its own timer has stopped.
6. **The Mining and Fuel station concern is valid.** If their local Nexus dies first, they can no
   longer affect an operation, their rebuilds are cancelled, and they have no faction-wide role.
   They become defended salvage/cleanup targets rather than strategic objectives.
7. **First-pass Black Fleet freight budgeting should be route-bounded, not a third 10–15% support
   slice.** Commerce can replace its unnecessary troop-transport allocation with freighters at no
   combat cost. Assault freighters should be additive to the existing combat/tanker/transport
   baseline, normally 5–15 FP per meaningful route, so the retained troop transports and the new
   cargo ships never reduce the combat allocation or inflate target-feasibility calculations.
8. **Omicron's approved regulator is a capped faction-wide readiness pool.** Living Nexuses produce
   readiness, Fuel controls production speed, Mining controls the available FP band, and two
   faction-wide request clocks choose raids versus incursions. The pool holds at most one incursion.
9. **Core generation remains at the vanilla Remnant density for the first pass.** Lower FP floors,
   slower maturation, and fewer incursions should reduce supply before core density or recovery
   chances are touched. Omicron Prime's original Nexus is the exception: it uses an Alpha Core,
   while the three later Nexuses retain Command Cores.

## 2. Comparison method and limits

The comparison uses four kinds of evidence:

- current production settings in `data/config/exiles/director_config.json`;
- the fleet-generation paths in `OmicronRaidIntel` and `BlackFleetRaidIntel`;
- the 2026-08-29 test log and its campaign saves;
- the vanilla 0.98a Remnant and pirate-base fleet-generation formulas.

Two units must stay separate:

- **Operation FP** is the total `RaidIntel` assembly budget. The assemble stage may split it into
  several route fleets.
- **Fleet/member FP** is the actual FP represented by one generated route fleet's members.

No suitable live ordinary Remnant comparison fleet was found in the available saves; the Remnant
entries in the current test saves were stations. The Remnant comparison therefore uses the vanilla
generator's exact FP bands rather than pretending a Nexus station is a patrol. The current save did
contain a live Omicron incursion, and archived saves contained Black Fleet raiders, so both custom
generators still have composition sanity checks.

## 3. Vanilla reference bands

### 3.1 Standard Remnant fleets

Vanilla converts its internal point roll to combat FP at 8 FP per point and assigns no logistics
budget. The important bands are:

| Source | Ordinary combat FP | Loss-response ceiling | Operational reading |
|---|---:|---:|---|
| Destroyed/secondary Remnant system | 8–16 | 16 | Fragment-scale residue |
| Suppressed system seeded fleet | 32–96 | 96 | Fragment / Sub-Ordo |
| Suppressed-system damaged-station fleet | 48–96 | up to 192 | Sub-Ordo, reinforced after losses |
| Healthy Nexus fleet | 64–192 | up to 384 | Sub-Ordo through Ordo; can reinforce after repeated losses |

The loss-response ceiling is not the normal roll. A station fleet manager adds 32 FP per prior
fleet loss, capped at an added amount equal to its configured maximum. Thus 384 FP is a deliberately
angered healthy Nexus maximum, not the ordinary Ordo baseline.

Vanilla names a generated Remnant fleet an Ordo when the pre-conversion roll exceeds 16, i.e. above
128 combat FP. “Ordo” therefore describes a broad band rather than one fixed strength.

### 3.2 Standard pirate-base raids

Vanilla pirate bases apply a 0.75–1.25 random factor twice: once in `getBaseRaidFP()` and again in
`getRaidFP()`. The mean remains the tier's base value, while the full possible range is
0.5625–1.5625 times that value.

| Pirate base tier | Mean operation FP | Full possible operation-FP range |
|---|---:|---:|
| Tier 1 | 100 | 56–156 |
| Tier 2 | 150 | 84–234 |
| Tier 3 | 250 | 141–391 |
| Tier 4 | 300 | 169–469 |
| Tier 5 | 450 | 253–703 |

Ordinary pirate raid fleets use the same approximate 10–15% tanker plus 10–15% transport allocation
copied by `BlackFleetRaidIntel`. Headline operation FP is therefore directly comparable between the
two pirate systems, even though only about 75% is combat allocation on average.

## 4. Omicron strength comparison

### 4.1 Configured operation strength

Omicron assigns its entire operation budget to combat hulls, pins generation quality to 1.0, uses
the Remnant-tagged roster, and copies the vanilla Remnant doctrine values. Its custom tactical-core
generator assigns approximately one core per 20 fleet FP, with level-3/5/7 Grade I/II/III cores and
a Grade III capital flagship for a sufficiently large fleet.

| Omicron tier/state | Operation combat FP | Mean | Comparison with a healthy Nexus fleet |
|---|---:|---:|---|
| Raid, campaign start | 100–180 | 140 | Direct overlap; approximately one strong Sub-Ordo/Ordo |
| Raid, mature escalation | 200–360 | 280 | Above an ordinary 64–192 fleet; approaches the 384 reinforced ceiling |
| Lattice raid, mature | 100–180 | 140 | The 0.5 Lattice factor cancels the 2.0 mature escalation |
| Incursion, campaign start | 350–550 | 450 | About 1.8–2.9 ordinary 192-FP maximum fleets |
| Incursion, mature escalation | 700–1,100 | 900 | About 3.6–5.7 ordinary 192-FP maximum fleets |

Those ratios compare total operations with individual Remnant fleets. Omicron's vanilla assemble
stage splits a large incursion into several route fleets, so a player normally meets a group of
Ordo-scale fleets rather than one impossible 900-FP blob.

A destroyed Mining Station multiplies its node's strength by 0.75. At full escalation this changes
the mature bands to 150–270 FP for raids and 525–825 FP for incursions. A destroyed Fuel Station does
not reduce strength; it multiplies cadence by 0.75, making the next re-armed interval about 1.33 times
longer.

### 4.2 Save sanity check

The 2026-08-29 save contained two live route fleets belonging to the same Omicron incursion:

| Route fleet | Planned route FP | Member FP | Hulls | Tactical cores |
|---|---:|---:|---:|---:|
| Incursion Force A | 342.696 | 328 | 29 | 17: 8 Grade I, 8 Grade II, 1 Grade III |
| Incursion Force B | 342.696 | 342 | 28 | 17: 9 Grade I, 7 Grade II, 1 Grade III |

The generated members closely realize the planned route budgets, and the 17-core count exactly
matches the intended one-core-per-20-FP density after integer truncation. The composition includes
Nova/Apex/Brilliant hulls in one fleet and Radiant/Nova/Apex/Brilliant hulls in the other. This
supports the conclusion that the strength dial is producing genuine elite machine combat power,
not being lost to civilian support ships or weak officer generation.

### 4.3 Omicron conclusion

Omicron is already at least as powerful as standard Remnants on a per-FP and officer-quality basis.
The intended statement “Omicron should be Remnant-powerful” is met. The tuning question is not how to
bring it up to the Remnant baseline; it is how many Remnant fleets each operation should represent:

- current raids: about one Ordo early, one-to-two at maturity;
- current incursions: several Ordos early, roughly four-to-six ordinary maximum Ordos at maturity.

The incursion tier is the main strength outlier. This does not prove it is too strong in combat—the
user reports that the actual attacks function well—but it establishes the scale that future battle
tests should defend or revise deliberately.

## 5. Black Fleet strength comparison

### 5.1 Configured operation and combat strength

The Black Fleet rises from 0.6 to 1.0 strength over 365 days. Its current fleet generator assigns:

- tanker: 10–15% of operation FP;
- troop transport: 10–15%;
- freighter: 0%;
- combat: the remaining 70–80%, averaging 75%.

| Black Fleet tier/state | Total operation FP | Expected combat FP | Hard hull cap |
|---|---:|---:|---|
| Commerce, campaign start | 90–150 (mean 120) | 63–120 (mean 90) | Destroyer |
| Commerce, mature | 150–250 (mean 200) | 105–200 (mean 150) | Destroyer |
| Assault, campaign start | 210–300 (mean 255) | 147–240 (mean 191) | Cruiser |
| Assault, mature | 350–500 (mean 425) | 245–400 (mean 319) | Cruiser |

The expected-combat range shows the possible 70–80% allocation endpoints. The expected mean uses
the average 75% combat share.

### 5.2 Qualitative advantage over ordinary pirates

The Black Fleet and vanilla Pirates currently draw from the same ship tags, explicit hull list,
hull-frequency adjustments, fighter pool, warship/carrier/phase weighting, aggression, and autofit
randomization. Black Fleet strength is not coming from a secret superior hull roster.

Its real advantages are:

- officer quality 2 rather than the Pirates' 1;
- ship quality 2 rather than 1;
- +0.7 production quality while a functional Mysterious Stockpiles industry supplies the anchors;
- `fightToTheLast`, reinforcing the disciplined rebuilt-armada behavior;
- generally higher operation budgets than low- and middle-tier pirate bases.

The per-tier conclusion is:

- **Commerce raids:** mature mean 200 FP, between the tier-2 pirate mean (150) and tier-3 mean
  (250). They are stronger than low-tier pirate pressure but not stronger than serious tier-3 to
  tier-5 pirate raids. The destroyer cap further preserves their light-raider identity.
- **Assaults:** mature mean 425 FP, above the tier-4 mean (300) and just below the tier-5 mean (450).
  With better officers and ship quality, they should outperform most ordinary pirate raids of the
  same headline FP. They are not guaranteed to exceed a high-rolled tier-5 raid, which can reach
  about 703 FP and has no equivalent per-operation cruiser hard cap.

Thus “Black Fleet should field more powerful fleets than Pirates” is **mostly met for assaults and
only conditionally met for commerce raids**. If the requirement means every assault must read as
superior even beside a mature tier-5 pirate base, the current assault band and cruiser cap do not
guarantee it.

### 5.3 Archived save sanity check

No Black Fleet director fleet could spawn in the 2026-08-29 run because the director had been
disabled. Archived saves do contain Black Fleet route fleets. A representative ramp-era assault in
the 2026-08-01 save had a 293-FP operation budget and an Armada route containing 25 hulls, including
cruisers, destroyers, tankers, and transport hulls; the surviving member list represented 149
nominal hull FP and carried seven officers. This is a composition check, not a current-balance sample:
the fleet was already in flight and its saved route strength reflected campaign attrition.

The important confirmation is that troop transports are already materially present in Black Fleet
generation. They should be retained for assault fleets.

## 6. Cross-faction strength perspective

This is not the primary balance target, but it helps explain how the two directors feel:

| State | Omicron light vs Black Fleet light | Omicron heavy vs Black Fleet heavy |
|---|---:|---:|
| Campaign start, mean combat allocation | 140 vs 90 (1.56×) | 450 vs 191 (2.35×) |
| Mature, mean combat allocation | 280 vs 150 (1.87×) | 900 vs 319 (2.82×) |

Omicron is substantially more dangerous per operation before accounting for automated-ship and
tactical-core behavior. That is coherent if Omicron is meant to be a Remnant-grade strategic threat
and the Black Fleet a superior pirate force, but it makes frequency tuning especially important.

## 7. Cadence evidence and the hidden-surplus problem

### 7.1 Current theoretical capacity

With all three mature Nexuses alive:

- raids: 3 × 365 / 30 = **36.5 attempts/year**;
- incursions: 3 × 365 / 40 = **27.4 attempts/year**;
- total before Lattice raids: **63.9 attempts/year**.

Once mature Lattice raids activate, they add about 12.2 attempts/year, bringing the steady-state raw
timer capacity to about **76 attempts/year**. Lattice never launches incursions.

### 7.2 Completed-log result

The roughly 1,516-day completed test logged:

| Type | Launched | No-target aborts | At-capacity suppressions | Launch share of timer events | Mean launched FP |
|---|---:|---:|---:|---:|---:|
| Raid | 41 | 136 | 5 | 22.5% | 264.0 |
| Incursion | 24 | 47 | 36 | 22.4% | 848.5 |
| **Total** | **65** | **183** | **41** | **22.5%** | — |

This is about 15.65 visible launches/year: 9.87 raids and 5.78 incursions. Launched operation output
was about 7,510 combat FP/year over the escalating test.

The scheduler is therefore generating far more opportunities than the target pool and live-operation
caps can accept. This is not a stored queue—the code re-arms elapsed timers and discards failed
attempts—but it behaves like **latent production surplus**. Whenever a target window opens, a timer
from some surviving node is likely ready to fill it.

### 7.3 Why Nexus destruction can be visually delayed

One healthy mature Nexus supplies about 12.2 raid attempts and 9.1 incursion attempts per year.
That single node's raw capacity is already above the completed test's sector-wide visible rates of
9.87 raids and 5.78 incursions per year. Consequently:

- destroying one of three Nexuses removes one third of raw timers but may remove almost none of the
  visible launches;
- two surviving Nexuses still have large excess capacity;
- even one surviving mature Nexus could, in favorable target windows, approximate the old visible
  throughput.

The user's observation is therefore correct: under current throttling, multiple bases may have to be
destroyed before attacks visibly diminish. The balance is being set accidentally by target scarcity
and per-node caps rather than intentionally by Omicron's surviving industrial base.

## 8. Nexus-first support-station problem

Current semantics are local and strictly hierarchical:

- the Nexus is the node's fleet engine;
- Mining modifies only that node's operation strength;
- Fuel modifies only that node's re-arm interval;
- a dead Nexus stops all launches from the node;
- Mining/Fuel rebuild only while that Nexus lives, and a Nexus death cancels a pending rebuild.

Therefore, if the Nexus is destroyed first, its surviving Mining and Fuel stations cannot affect any
future operation. They lose strategic meaning even though they remain defended physical targets.

There is a second legibility issue: the current war-capacity band is the mean of configured asset
axes. Nexus existence is not itself an axis. A decapitated node whose support stations remain alive
can therefore continue contributing healthy-looking axis values to the “overall activity” band even
though its production is zero.

This is a design flaw, not merely a missing tooltip.

## 9. Approved implementation plan

The existing concept should be adjusted, not discarded. Keep the proven `RaidIntel` operations,
physical source nodes, destroyable assets, escalation clock, target logic, and bounty presentation.
Replace the layer that turns those assets into operation opportunities.

### Phase A — approved strength and progression contract

Omicron remains Remnant-grade and retains the current incursion ceiling as a true late-campaign
ceiling. The approved change is a substantially lower floor and a much longer, back-loaded path to
that ceiling:

| Operation | Campaign-start FP | Fully mature, intact-network FP |
|---|---:|---:|
| Raid | 70–120 | 200–360 |
| Incursion | 180–300 | 700–1,100 |

- The combat-maturation clock is separate from Lattice construction and the Tri-Tachyon protection
  umbrella; changing combat pacing must not accidentally delay those story/infrastructure events.
- Combat maturation takes approximately 1,080 days and uses a back-loaded curve equivalent to
  `progress ^ 1.5` for the first implementation.
- At approximately one year, a fully supplied mean incursion should be near 370–400 FP, not the
  current 900 FP; at two years it should be near 600–650 FP; the 900-FP mean belongs at full
  three-year maturity with an intact Mining network.
- Normal infrastructure cannot push output above the listed ceiling. Reserve at most 10% above it
  for an explicitly temporary quest/event overdrive; do not recreate unrestricted stacked
  multipliers.
- Black Fleet strength is not reduced in this pass. Its approved freight work is Phase B1 below;
  further growth-cap tuning waits for the next all-systems live test.

### Phase B — approved separation of combat strength and logistics presentation

Do not let believable support hulls silently consume the strength target.

- Treat **combat FP** as the balance contract.
- Budget support hulls separately, or raise total operation FP to preserve the chosen combat FP.
- Ensure feasibility and abort checks continue to measure combat capability rather than counting
  freighters, tankers, and troop transports as equivalent warships.

Approved first-pass composition direction:

- **Black Fleet commerce:** tanker + freighter; no troop-transport requirement.
- **Black Fleet assault:** tanker + freighter + troop transports. **Keep the existing troop
  transports.**
- **Omicron:** decide whether an automated tanker is needed only after the lore and compatibility
  check. A friendly automated fuel-ship mod may already provide an appropriate hull; verify that
  dependency/roster before creating a duplicate. Omicron does not loot, so it has no comparable
  cargo-freighter requirement.

#### Phase B1 — Black Fleet freighter FP first pass (approved 2026-08-30)

Status: **implemented 2026-09-15; live sampling pending.** The original proposal below is historical.
Sean resolved its conflicting commerce arithmetic: preserve both original 10–15% combat deductions,
replace transports with capped freighters, and leave unused support FP unspent. Dedicated freight
is appended after vanilla combat generation; collectors also gain tankers. The fuel-ship guarantee
applies to raids, assaults and collectors only. Current details and tests are recorded in
`EXILES_BLACK_FLEET_SYSTEMS.md`.

The dedicated freighter budget should be calculated for each assembled route fleet, because
`BlackFleetRaidIntel.createFleet()` receives a route's `extra.fp`, not the whole operation at once.
The first-pass composition contract is:

| Tier | Tanker | Freighter | Troop transport | Combat calculation |
|---|---:|---:|---:|---|
| Commerce | 10–15% of route baseline | 10–15%, bounded as below | 0 | baseline minus tanker and freighter |
| Assault | 10–15% of route baseline | 5–10%, bounded as below and **added on top** | retain 10–15% | baseline minus tanker and transport; do not subtract freighter |

For either tier, the first implementation should use this route guardrail:

```text
if route baseline FP < 40:
    dedicated freighter FP = 0
else:
    dedicated freighter FP = clamp(route baseline FP × random share, 5, 15)
```

The percentage supplies scale inside the useful band; the 5 FP floor is approximately one Buffalo,
and the 15 FP ceiling is approximately three Buffalos or two Colossi. The sub-40-FP exclusion keeps
the small remainder route produced by vanilla raid assembly from turning into a freighter-only tail.
The operation's main fleet or fleets carry the logistics burden instead.

This band is deliberately smaller for assaults. An assault is already much larger than a commerce
raid and retains its existing tanker and troop-transport train. Applying a third unrestricted
10–15% slice would produce many cheap civilian hulls without adding useful plausibility. The current
0.98a hull data and default role pool establish the relevant scale:

| Likely role hull | Hull size | Fleet points | Cargo capacity | Tier-cap result |
|---|---|---:|---:|---|
| Buffalo | Destroyer | 5 | 4,000 | available to both tiers |
| Colossus | Cruiser | 7 | 9,000 | assault only |
| Atlas | Capital | 7 | 20,000 | excluded by both current size caps |

The mod's merged default roles also offer `HaT_clodhopper_Standard` as a small freighter, but normal
faction hull-knowledge filtering still applies. The live sampling pass must record what the Black
Fleet actually receives rather than assuming every merged role candidate is eligible. Mules and
other combat freighters may already appear through the combat allocation and doctrine; they do not
replace the dedicated civilian-freighter allowance.

##### Protected combat budget

The current operation range remains the **combat-equivalent baseline**, not an all-in hull count:

- commerce baseline: 150–250 FP;
- assault baseline: 350–500 FP;
- the existing 0.60 campaign-start ramp continues to scale those baselines.

Current Black Fleet generation spends two independent 10–15% slices on tankers and transports, so
combat receives 70–80% of the baseline, 75% on average. That yields the following mature expected
combat bands:

| Tier | Current expected combat FP | First-pass expected combat FP | Result |
|---|---:|---:|---|
| Commerce | 112.5–187.5 | 112.5–187.5 | unchanged: freighter replaces transport |
| Assault | 262.5–375 | 262.5–375 | unchanged: freighter is additive |

For commerce, the 10–15% freighter allocation is a direct role substitution, so the all-in
150–250 FP band and its combat distribution remain unchanged. For assaults, each qualifying route's
generated `FleetParamsV3` budget increases by its 5–15 freighter FP while the original combat,
tanker, and troop-transport calculations remain untouched. In the unbounded percentage model this
would add roughly 5–10% to the all-in hull budget (a 350–500 baseline becomes 367.5–550); the route
floor/cap makes the real addition depend on vanilla's route split and is why it must be logged rather
than represented by a misleading single operation multiplier.

Do **not** simply raise assault `spawnFpMin/Max` and then subtract all three support shares from the
new total. For example, a 390–560 all-in range with three random slices can preserve average combat
FP, but individual rolls range much more widely and some assaults become weaker than today. It also
makes the feasibility gate approximately 11% looser if the gate continues to read headline FP.

The implementation should therefore keep two concepts explicit:

1. **Baseline/combat-equivalent FP** drives growth, target feasibility, route strength, and abort
   behavior.
2. **Generated all-in FP** is baseline FP plus the additive assault-freighter allowance and is used
   only for fleet construction and diagnostics.

If engine behavior forces abort bookkeeping to count actual member FP, the stage must compare
remaining combat capability rather than treating surviving freighters as warships. That should be
verified before the first live balance test, not inferred from the nominal route FP.

##### First-pass verification gates

Before changing the 5–15 FP band, collect a paired sample at campaign-start and mature growth:

1. Log each route's baseline FP plus requested/generated combat, tanker, freighter, and transport FP
   and hull counts.
2. Confirm commerce fleets retain their current combat distribution and contain no dedicated troop
   transports.
3. Confirm assault fleets retain current combat FP and troop transports while meaningful routes
   normally receive one to three dedicated freighters.
4. Confirm sub-40-FP remainder routes do not become support-only stragglers.
5. Confirm target feasibility and abort timing are unchanged when comparing identical baseline FP.
6. Inspect campaign burn, fleet cohesion, retreat behavior, and return trips; a statistically correct
   allocation still fails if the civilian tail is routinely detached or destroyed before arrival.
7. Count cargo hulls over the whole operation, not just the flagship route. Only tune upward if the
   complete operation still fails the visual test of carrying fuel, troops where appropriate, and
   plausible loot capacity.

### Phase C — approved faction-wide readiness regulator

Readiness is one persistent faction-wide value, not four node banks. It abstracts accumulated fuel,
replacement hulls, maintenance, routing calculations, and coordinated launch preparation.

#### C1. Production and storage

- Pool range: **0–2 readiness**.
- Raid cost: **1 readiness**.
- Incursion cost: **2 readiness**.
- Full-network production target: **8.5–9 readiness per year**.
- Production above the 2-point cap is discarded. Omicron can bank one incursion, never several.
- No living Nexus means zero generation and zero launches, regardless of surviving support stations.

With the approved four-Nexus/four-Fuel baseline, use:

```text
annual readiness = 9
    × (effective Nexuses / 4)
    × (0.40 + 0.60 × effective Fuel / 4)
```

Fuel is an efficiency input, not an independent producer. A Fuel Station contributes 1.0 while its
local Nexus lives and 0.5 after that Nexus dies and the station reroutes to the surviving network.
With no Fuel, living Nexuses retain 40% production. With three Nexuses and 3.5 effective Fuel, output
is approximately 6.24 readiness/year, an immediate 31% reduction from full capacity. With only one
Nexus and the other Fuel Stations rerouted, output is roughly 1.7/year: an incursion remains possible,
but saving for it takes more than a year and crowds out raids.

For readiness production, each completed living Nexus contributes 1.0. Lattice contributes 0 while
its Nexus cannot launch, 0.5 after its raid-operational construction stage, and 1.0 after completion.

#### C2. Raid versus incursion requests

Replace all per-node archetype timers with two faction-wide request clocks:

- raid request: random 60–90 days;
- incursion request: random 180–270 days.

The clocks request work; readiness pays for it:

1. A raid request launches when at least 1 readiness, a feasible target, a free global slot, and a
   living Nexus with a free source slot exist. Launch spends 1 and rearms the raid clock.
2. When the incursion clock matures, it creates a pending incursion request. New readiness is
   reserved toward the 2-point cost rather than repeatedly spent on raids.
3. At 2 readiness, the incursion launches if a feasible target and source exist, spends 2, clears
   the request, and rearms its clock.
4. There is no two- or four-Nexus launch requirement. One surviving Nexus may eventually launch
   either archetype; only the final Nexus kill silences Omicron completely.
5. If target selection fails, consume 25% of the requested cost (0.25 raid / 0.5 incursion), clear
   the pending request, and use an approximately 30-day retry. This prevents a full pool from
   checking constantly and occupying every target window instantly.
6. Incursions have deterministic priority when both requests mature together. At most one request
   is resolved per scheduler evaluation, and each archetype can hold at most one pending request.
7. Capacity, a busy/cooling target system, or an unavailable source defers without a planning cost.
   A genuinely infeasible target set pays the 25% cost; no remaining strategic enemy uses a longer
   retry. Internal route-construction errors retain readiness and the pending request.

Approved live-operation limits:

- at most two Omicron operations faction-wide;
- at most one incursion;
- each living Nexus may source at most one active operation;
- the physical source is selected from living Nexuses with a free slot after archetype and target
  are known.

A Nexus-launched Tri-Tachyon response occupies that Nexus's source slot but consumes neither
readiness nor one of the two offensive global slots. Culann's independent reserve remains outside
both limits.

The incursion enable switch is quest-safe. While disabled, its clock is paused, no request or
reservation exists, and active incursions finish normally. Enabling starts a fresh 180–270-day
clock rather than spending a banked pool immediately. The shipped testing default remains enabled;
a future quest release may start disabled and call `setIncursionsEnabled(true)` on completion.

These values target approximately four-to-six raids and one-to-two incursions per intact-network
year, with only 6–12% production surplus above the intended mixed expenditure instead of the current
large latent timer surplus.

### Phase D — approved Mining, orphan, and counted-asset model

#### D1. Mining controls strength, not cadence

At operation launch, calculate:

```text
effective Mining = intact local Mining + 0.5 × orphaned Mining
strength progress = combat maturity × (0.35 + 0.65 × effective Mining / 4)
operation FP band = interpolate(start band, mature ceiling, strength progress)
```

A Mining Station contributes 1.0 while its local Nexus lives and 0.5 after rerouting. At full
maturity, all four effective Mining Stations unlock the Phase-A ceiling; half effective Mining
reaches 67.5% of the distance from the starting band to that ceiling; no Mining retains 35% of the
advancement. For incursions at full maturity this is approximately:

| Effective Mining state | Available incursion FP band |
|---|---:|
| 4/4 | 700–1,100 |
| 2/4 | 531–840 |
| 0/4 | 362–580 |

This keeps a stripped network dangerous without allowing it to field intact-network armadas.

#### D2. Asymmetric system layout

Preserve the existing totals for the first pass—four Mining and four Fuel Stations—but redistribute
them so the systems are not four identical triplets:

| System | Nexus | Mining | Fuel | Strategic identity |
|---|---:|---:|---:|---|
| Omicron Prime | 1 | 2 | 1 | primary industrial center |
| Cipher | 1 | 0 | 2 | fuel-processing/logistics hub |
| Vigil | 1 | 1 | 1 | balanced forward complex |
| Lattice | 1 | 1 | 0 | developing extraction node |
| **Total** | **4** | **4** | **4** | unchanged network totals |

Do not add further support stations until this normalized layout is live-tested. Extra planets,
belts, or fields may be added later for placement and visual storytelling, but must not silently
increase the regulator's normalized maximum.

The current Boolean-per-type asset model cannot implement duplicates correctly: placement specs,
death flags, rebuild timers, and modifiers collide on `nodeId + assetKey`, while the tag scan only
asks whether any station of a type survives. Replace it with stable counted instances such as
`prime_mining_1` and `prime_mining_2`, each contributing one unit and owning its own placement,
destruction, reroute, and rebuild state.

#### D3. Lattice construction and completion

Lattice uses an independent **540 active-day** construction clock, frozen while its UC-Nexus is
dead. It no longer controls combat maturation or Tri-Tachyon umbrella coverage:

| Progress | Approx. surviving day | Result |
|---|---:|---|
| 50% | 270 | `lattice_mining_1` is built |
| 75% | 405 | the UC-Nexus may source raids and contributes 0.5 effective Nexus |
| 100% | 540 | the damaged station is atomically replaced by a full Nexus |

The completed Nexus contributes 1.0 readiness, uses the full Nexus defense tier, and may source
raids, incursions, and strategic responses. Completion does not enable incursions globally; the
quest-facing incursion switch remains an independent policy gate. Destroying the UC-Nexus before
completion freezes the build permanently unless future quest code explicitly restores it.

Orphan rules:

- local Nexus alive: station contributes 100% and may use its ordinary rebuild behavior;
- local Nexus dead but another Nexus alive: surviving station reroutes at 50%; its destruction
  still weakens the network;
- no Nexus alive: support contribution is zero and all attacks stop;
- player-facing activity messages and diagnostics must use these actual effective counts, not the
  old mean of Boolean node axes.

### Phase E — approved operation shape and core policy

#### E1. Objective budget

Widespread damage remains an intended incursion outcome, but it must not be routine. Replace the
independent 50% collateral roll per eligible market with one pre-rolled operation objective budget:

| Incursion maturity | One market | Two markets | Three markets |
|---|---:|---:|---:|
| Campaign start | 60% | 40% | 0% |
| Fully mature | 35% | 50% | 15% |

Interpolate the distribution over combat maturity. Use only existing route fleets and action-stage
target selection: the budget must not spawn more fleets, scripts, managers, or recurring scans.
Three-market destruction remains possible as a memorable late event instead of the expected report
after every incursion. If live testing still finds it too predictable, a later refinement may require
substantial surviving operation strength before the third objective unlocks.

#### E2. Tactical cores

Leave core economy and ordinary fleet distribution unchanged in the first implementation:

- retain vanilla Remnant density: one installed core per 20 fleet FP;
- retain current Grade I/II selection weights;
- retain base recovery probabilities of 20% / 10% / 5%;
- retain Command Cores on the three later Nexuses;
- give the original Omicron Prime Nexus an ordinary Alpha Core, reflecting the preexisting wartime
  Tri-Tachyon station that received the Omicron Initiative directives.

Do not add the recently destroyed second-Nexus hulk in this pass. It is a strong but narratively loud
idea that implies machine-on-machine conflict and belongs to quest design if used.

The first live test must log installed, destroyed, and recovered cores by grade and operation. The
lower FP floor, slower ramp, and lower incursion frequency should reduce supply before density or
drop rates are touched. Reopen one-Command-per-route versus one-per-operation only if evidence still
shows excessive Command Core supply. If overall recovery remains excessive after that isolated test,
the next candidate drop rates are 15% / 7.5% / 3%; they are not part of this approved first pass.

## 10. Required live tests for the implemented first pass

- Full intact network: launched output settles near four-to-six raids and one-to-two incursions per
  year; readiness never exceeds 2 and no operation is launched without paying its cost.
- One of four mature Nexuses destroyed: logged readiness production falls immediately and visible
  attack frequency falls over the next one-to-two request horizons without requiring a second kill.
- One Nexus remaining: raids and incursions remain possible, but generation is near the expected
  1.7 readiness/year case rather than the old sector-wide visible rate.
- Lattice: Mining appears near surviving day 270, raids unlock near day 405, and the damaged Nexus
  becomes exactly one full Nexus near day 540 without duplicate cores, managers, or death messages.
- Incursion quest gate: disabling clears a pending reservation without cancelling a live incursion;
  re-enabling starts a fresh request clock and does not immediately spend a banked pool.
- Incursion reservation under one-Nexus production: measure the expected long quiet interval and
  decide whether the resulting raid starvation is acceptable before changing the approved policy.
- Global cap: log complete operation lifetimes and verify that two long-distance slots can actually
  deliver the target annual range rather than becoming the accidental primary regulator.
- Mining destroyed: subsequent operations interpolate to the intended lower combat-FP band without
  changing readiness generation.
- Fuel destroyed: readiness generation and subsequent launch cadence slow without directly changing
  the operation FP band.
- Nexus destroyed before Mining/Fuel: surviving support stations still have a documented,
  measurable 50% network contribution; individual counted assets remain valid targets.
- No viable target for an extended period: planning costs/retries occur and opening a target does
  not cause a stored burst.
- Early and mature incursion samples approach the approved 60/40/0 and 35/50/15 objective-budget
  distributions without creating extra route fleets or scripts.
- Omicron Prime's Nexus carries an Alpha Core; the other three Nexuses carry Command Cores.
- Core logs record installed, destroyed, and recovered Grade I/II/III items per operation and verify
  that ordinary density and recovery probabilities remain otherwise unchanged.
- Black Fleet commerce fleets visibly carry fuel and cargo capacity.
- Black Fleet assault fleets visibly carry fuel, cargo, and troop transports.
- Adding support hulls does not lower the agreed combat-FP band or inflate feasibility estimates.
- Omicron fleet logs distinguish operation budget, each route's combat FP, support FP, source Nexus,
  contributing support assets, and reason for any failed launch.
- Black Fleet fleet logs provide the same combat/support split and identify commerce versus assault.

### 10.1 Intact-network live test — 2026-09-01

Sean extended the fresh campaign to approximately day 595.5. The current evidence is
`save_Issac_3686564620849347735`; the relevant appended log session begins near line 67017 of the
log named in §12. This run is a **pass for intact-network scheduling, Lattice staging, concurrency,
and mixed outcomes**, not a pass for the destruction/gate/core-drop cases that were not exercised.

Operations launched through approximately day 529:

| Approx. launch day | Type | Source | FP | Target | Result |
|---:|---|---|---:|---|---|
| 83 | Raid | Vigil | 91 | Chalcedon | Failure |
| 171 | Raid | Vigil | 74 | Chalcedon | Success; 4 disruptions |
| 225 | Incursion | Vigil | 291 | Gilead / Asher | Failure before an attack landed |
| 279 | Raid | Prime | 100 | Epiphany | Success; 4 disruptions |
| 342 | Raid | Prime | 147 | Chalcedon | Success; 5 disruptions |
| 411 | Raid | Prime | 131 | Gilead | Success; 5 disruptions |
| 482 | Incursion | Prime | 447 | Epiphany | Success; 3 disruptions, one objective |
| 529 | Raid | Lattice | 108 | Tartessus | Failure in the action stage after successful assembly/travel |

Aggregate: raids 4 successes / 2 failures; incursions 1 / 1; all operations 5 / 3. The average
through the final save was approximately five operations per campaign year, the low end of the
approved target. The high-roll 447-FP Incursion remained inside its interpolated band and did not
produce the old routine multi-market cascade. Two operations overlapped (`liveGlobal=2`), proving
that the global cap can be reached without stalling the director.

Infrastructure and final scheduler state:

- initial 3 Nexus / 4 Fuel / 3 Mining network generated 6.75 readiness/year;
- Lattice Mining appeared near day 270 without changing readiness generation;
- the 75% Lattice stage counted as 0.5 Nexus and raised generation to 7.875/year;
- Lattice sourced a raid before completion, then atomically became a full Nexus near day 540;
- the full 4 Nexus / 4 Fuel / 4 Mining network generated 9.0/year;
- the completed Lattice Nexus later dispatched a 171-FP response to Port Tse;
- at the final save the pool was 1.6075, no request was pending/deferred, the raid clock had about
  6 days remaining, and the Incursion clock about 153 days. There is no scheduler jam.

Save audit found one low-risk serialization detail: the removed UC-Nexus object remained reachable
only through the historical bare source-market object retained for Lattice's earlier raid. It was
not present in the Lattice system entity list, did not create a second station/core/manager, and the
source market refreshes to the completed Nexus on the next launch. Treat immediate reference cleanup
as optional save-hygiene work, not a gameplay defect.

The combat-maturity origin currently initializes on the first successful operation launch rather
than campaign/director start. In this run that placed the maturity curve approximately 83 days behind
the nominal campaign clock. Sean accepts the resulting early-game breathing room for now and has
approved **no code change**. Keep it recorded as provisional technical debt: if the pacing is later
made intentional, replace the accidental origin with an explicit configurable delay; otherwise fix
the origin. It is not currently harming scheduler correctness.

No Exiles/Omicron runtime error appeared. Successful off-screen action lines used `byFleet=false`,
which is the expected action-stage autoresolve path, not evidence that the route fleets were missing.
The appropriate next step is additional natural play rather than immediate Omicron or Black Fleet
strength/cadence tuning.

## 11. Implementation record and remaining boundary

Implemented 2026-08-30:

- faction-wide readiness, request clocks, reservation priority, planning costs, and global caps;
- source-target pair selection and the Nexus source-slot/strategic-response rule;
- 1,080-day back-loaded combat maturation and Mining-controlled FP interpolation;
- counted asymmetric Mining/Fuel assets with stable ids and orphan contribution;
- 540-day staged Lattice construction and atomic full-Nexus completion;
- quest-safe raid/incursion gate transitions, with incursions enabled for current testing;
- fixed incursion objective budgets and operation-lifetime/network diagnostics;
- Omicron Prime Alpha Core assignment; other Nexuses remain Command Core stations;
- Tri-Tachyon umbrella progression separated from Lattice construction.

Not implemented in this pass:

- Black Fleet Phase B1 freighters or other Black Fleet tuning;
- an Omicron tanker or compatibility decision for a third-party automated fuel ship;
- tactical-core density/recovery changes, which remain deliberately unchanged;
- any quest content that flips the already-implemented incursion gate.

## 12. Evidence locations

- Omicron settings: `data/config/exiles/director_config.json` → `exileomicron`
- Black Fleet settings: same file → `exileblackfleet`
- Omicron scheduling/assets: `data/scripts/exiles/OmicronDirector.java`
- Omicron fleet generation: `data/scripts/exiles/OmicronRaidIntel.java`
- Omicron tactical cores: `data/scripts/exiles/ExileOmicronFleetOfficerGenerator.java`
- Black Fleet scheduling: `data/scripts/exiles/BlackFleetRaidManager.java`
- Black Fleet fleet generation: `data/scripts/exiles/BlackFleetRaidIntel.java`
- Black Fleet quality engine: `data/scripts/industries/ExileMysteriousStockpiles.java`
- Faction comparisons: `data/world/factions/exileomicron.faction`,
  `data/world/factions/exileblackfleet.faction`, and the vanilla `remnants.faction` /
  `pirates.faction`
- Completed test log: `D:/Fractal Softworks/Starsector/starsector-core/starsector.log`
- 2026-09-01 intact-network save:
  `D:/Fractal Softworks/Starsector/saves/save_Issac_3686564620849347735/campaign.xml`
- Current save composition sample:
  `D:/Fractal Softworks/Starsector/saves/save_Yokko_6299500331632062783/campaign.xml`
- Archived Black Fleet composition sample:
  `D:/Fractal Softworks/Starsector/saves/save_Ellen_4389938484993986829/campaign.xml`
- Vanilla source cross-checks: `RemnantSeededFleetManager`, `RemnantStationFleetManager`,
  `RemnantThemeGenerator`, and `PirateBaseIntel` in the local `starfarer.api.jar`; readable source
  mirror at
  [Starsector API: RemnantSeededFleetManager](https://jaghaimo.github.io/starsector-api/RemnantSeededFleetManager_8java_source.html),
  [RemnantStationFleetManager](https://jaghaimo.github.io/starsector-api/RemnantStationFleetManager_8java_source.html),
  [RemnantThemeGenerator](https://jaghaimo.github.io/starsector-api/RemnantThemeGenerator_8java_source.html),
  and [PirateBaseIntel](https://jaghaimo.github.io/starsector-api/PirateBaseIntel_8java_source.html)
