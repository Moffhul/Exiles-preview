# Black Fleet Gameplay Systems — Reference

## Freight composition — implemented 2026-09-15; campaign testing pending

Sean confirmed the commerce ambiguity: preserve the original combat budget exactly; unused
support FP is unspent. Fuel-ship guarantees cover commerce raids, assaults and disguised collectors
only, not local patrols or unrelated vanilla/Nex fleets. This supersedes the unimplemented freight
status and conflicting commerce arithmetic in historical sections and tuning-plan Phase B1.

- Both raid tiers retain the original combat calculation: route baseline minus the two independent
  10–15% support rolls. Operation ranges, growth and launch feasibility are unchanged.
- Commerce replaces transports with dedicated freighters: use the old transport roll, bounded to
  5–15 FP on routes of at least 40 FP. Smaller routes get no dedicated freight. No dedicated transports.
- Assaults retain their tanker and transport requests; add bounded 5–10% freight after combat generation.
- Freighters are appended through vanilla role selection after factory pruning, avoiding combat-freighter
  substitution and warship pruning. Their allowance is per route and not market-multiplied. Size filters
  and blocked upward role fallback keep commerce freight at destroyers and assault freight at cruisers
  under the current tier caps. Existing combat-freighter doctrine is unchanged.
- Every generated raid/assault/collector has a tanker: existing raid requests remain, collectors add
  5 tanker FP, and missing tankers receive a vanilla `dram_Light`. This is a spawn guarantee; destroyed
  tankers are not replenished in flight. Combat and escort allocations do not fund the fallback.
- Assembly, travel, action, raid-strength and prosperity-return accounting use baseline FP scaled by
  surviving non-civilian combat FP / initial combat FP. Civilian survivors cannot sustain a defeated raid.
  Offscreen routes retain vanilla abstract baseline handling; this does not overhaul route simulation.

Implementation: `ExileBlackFleetLogistics`, raid intel/stages, extortion manager. Tuning lives in
`director_config.json` → `exileblackfleet.logistics`. Use a new campaign for the changed stage and
strength bookkeeping. Detailed `Black Fleet logistics` log entries record requested budgets and
generated hull IDs, FP, combat FP, total FP and count.

Validation: startup graph passes game-Janino compilation; `tools/ExileBlackFleetLogisticsCheck.ps1`
passes boundary, civilian/wing exclusion, combat-loss and straggler checks; existing economy harness
passes. These are offline checks, not generated-fleet or campaign samples. Remaining live tests:
sample both anchors at starting/mature growth; inspect dedicated hull counts and tanker presence;
test combat losses, civilian-only survivors, burn/cohesion, return journeys and save/load.

Live-test crash correction: collector generation exposed a null-unboxing NPE in `addSupport`:
vanilla `FleetParamsV3.blockFallback` defaults to nullable `Boolean`, not primitive `boolean`.
The helper now preserves its nullable value. The regression harness reproduced the crash before
the fix and passes afterward for null/false/true restoration and existing-tanker preservation.
Startup Janino compilation also passes. Restart the game and reload the last save; this correction
does not require a new campaign.

**Live sampling — Sean's 5-year Nexerelin run, READ 2026-09-20.** 216 `Black Fleet logistics` lines:
117 commerce, 14 assault, 85 collector. Zero Exiles exceptions.
- **Tanker guarantee: 216/216** fleets carry a tanker hull (dram, phaeton or prometheus); the
  `dram_Light` fallback was never needed.
- **Freight bounds hold.** Commerce freight was 0 on 72/117 routes (baseline under 40 FP, mean
  baseline 43) and 5–15 on the rest; assault freight 5–15 on 6/14 (0 on the small remainder
  routes); collectors kept their fixed 30.
- **Generated vs requested combat FP:** commerce ×1.11, collectors ×1.16, **assaults ×0.73 with
  12 of 14 under 80%**. The assault shortfall is most likely an accounting effect of the Black
  Fleet's combat-freighter doctrine — the factory fills part of the combat budget with hulls such
  as `venture_p`, `colossus3` and `HaT_condor2`, which `combatFP()` classifies as civilian, exactly
  as vanilla's `pruneFleet` would. Strength bookkeeping is unaffected (the ratio is taken against
  the fleet's own initial combat FP), but it does mean a 215-baseline assault fields ~133 FP of
  warships. **Worth a look during the doctrine pass; not a logistics defect.**
- **Operations:** 12 assaults, 9 SUCCESS / 3 FAILURE (Mayasura twice, Galatia once). All three
  failures resolved at the end of the vanilla window (~40–50 days after arrival), not on the
  arrival tick — the arrival-gate paragraph below describes the mechanism. Mayasura is Port Tse,
  i.e. Tri-Tachyon under Omicron's station complement, so those two are the defence working. The
  reserve hit 0 once (two assault losses in a row), Business is Good went dark, and extortion
  proceeds restored it to 97 within the next cycle; it later capped at 120 after an assault return.
- **Forward bases join the private economy.** Nexerelin's `Nex_PirateBaseManager` honours the
  faction file's `makesPirateBases` flag: it created Black-Fleet-owned pirate bases (Vecna Presidio,
  Morga Headquarters, and one destroyed later) which `ExileMidanEconomyNetwork` promptly joined to
  `exiles_midan_network`. **RULED (Sean, 2026-09-20): keep them in the private economy.** Verified
  before ruling: vanilla puts each base in a bubble of one, not the ordinary economy; a base has no
  timer and carries the no-deciv key, ending only when its station fleet is destroyed, so the network
  cannot change how long a forward base lives. Both vanilla's and Nexerelin's base managers honour
  `makesPirateBases` at equal weight with Kanta's pirates. Full record: `EXILES_ECONOMY_MAP.md` §10h.

## Shared prosperity and Sacked refactor — implemented 2026-09-12; live-tested by Sean 2026-09-13

Sean approved the shared reserve and recovery threshold. This section supersedes conflicting
historical descriptions below, including source-only fencing, random condition duration, instant
sack rewards, collector duration extensions, and a proposed fixed defeat shutdown.

Tuning: `data/config/exiles/director_config.json` → `exileblackfleet.prosperity` and `sacked`.
Restart/reload to read changed settings; `startingReserveDays` seeds a new account only.
Use a new campaign for this refactor. Ordinary save/load preserves the reserve, allowance
receipts, collected loot, return routes, and per-operation settlement guards.

| Prosperity setting | Default |
| --- | ---: |
| Starting / maximum reserve | 90 / 120 reserve-days |
| Consumption | 1 reserve-day per campaign day |
| Recovery threshold after exhaustion | 30 reserve-days |
| Successful returning assault / defeated assault | +90 / −30 |
| Returning collector / lost collector flagship | +30 / −15 |
| All collectors' combined rolling allowance | +60 per 120 campaign days |
| Supply bonus / maximum commodity basket | +1 / 6 commodities |
| Minimum assault return fraction / return timeout | 0.5 / 365 days |

Both Black Fleet-owned anchors receive Business is Good while the reserve is active and their
Stockpiles are functional. It switches off at zero and stays off until recovery reaches 30;
after recovery, dropping below 30 does not itself switch it off. Patron supplies and quality
remain separate. Stockpiles display reserve/recovery information even while the condition is absent.
The initial basket is supplies, fuel, food and domestic goods; successful sacks rotate in locally
produced goods, dropping oldest entries at the configured cap.

Assaults earn one reward per operation, regardless of sack count, only after enough returning
route strength reaches its source. Each returning route is weighted by its recorded FP at
withdrawal; visible returning fleets contribute their surviving FP, capped at that weight.
Return-trip attrition is retained when fleets go offscreen. Offscreen route completion also
counts. Destroyed fleets do not deliver. The return
stage waits up to the configured timeout; unresolved administrative returns grant nothing.
Confirmed combat defeats apply one penalty. Return-trip losses that prevent sufficient delivery
fail the operation. Recalled collectors without a completed collection earn nothing. Collectors
deliver near their source system in hyperspace, retaining their existing disguise behavior.
Rewards/penalties are guarded against repeated callbacks, including after save/load.

The extortion allowance counts actual reserve credited, globally across concurrent collectors.
Cap overflow and allowance overflow are discarded; there is no hidden backlog. Receipts expire
individually after the rolling window. NPC losses count as well as player losses. Commerce raids
do not directly replenish this account.

Nominal steady arithmetic is +90 from one sack plus +60 from extortion minus 120 consumption
= +30 per 120 days. Actual recovery is lumpy and depends on successful returns, travel time,
clipped rewards, and losses. The existing assault active-operation cap now includes the return
journey; measure natural cadence in playtests before tuning launch intervals.

Sacked changes:

- Guarantee 30 days of port disruption after a successful sack.
- Add 30 days of -3 availability on supplies/fuel where supplied or demanded, plus up to three
  locally produced goods ranked by supply. Crew, marines and non-economic goods are excluded.
- Retain the 60-day recovery scar, existing ground-defense penalty and anti-repeat protection.
- Retain vanilla Recent Unrest rather than adding another independent stability penalty.
- For commodities affected by both Corsair Activity and Sacked, apply the stronger shortage rather
  than adding them to -5. Keep the economic effects and recovery legible in the tooltip.
- Retain Corsair Activity strength and the existing chance of industrial disruption. Existing
  longer port/industry disruption is never shortened. Cached repeated sack calls are rejected.

Review correction: the installed vanilla `MarketCMD.doGenericRaid()` called by this assault adds
Recent Unrest and raid flags, not commodity theft. Existing comments claiming that call steals
commodities overstate the victim-side implementation. Anchor loot is generated separately.

Validation: game-Janino compilation; `tools/ExileBlackFleetEconomyCheck.ps1` checks configuration
parsing, exhaustion/recovery hysteresis, concurrent allowance, exact rolling-window expiry,
overflow, XStream reserve/receipt and collector-guard restoration, and shortage overlap arithmetic.
These checks do not substitute for campaign testing of engine callbacks and economic ticks.

Playtest: check both station bonuses; exhaust/recover the reserve; intercept outbound and returning
collectors/assaults; leave operations offscreen; save/load during collection and return; verify
multi-sack operations award once; inspect Sacked/Corsair overlap in both application orders and
at the 30/60-day recovery boundaries. Watch Emergency Measures and natural assault throughput.

> Status as of **2026-07-11** (end of the Fable-5 build sessions). Everything below is BUILT,
> Janino-clean, and playtested through playtest-5 unless marked otherwise. Author rating of the
> raid/assault loop: **~v0.8**. Extortion playtested same day: "just works."
> **2026-09-01 addendum:** an extended natural run reconfirmed scheduled commerce raids and
> assaults from both anchors, including a successful 468-FP Lundy assault. Current author ruling is
> to leave Black Fleet strength/cadence unchanged pending more observation; Phase B1 freighters are
> still approved and unimplemented. See §7.
>
> Code home: `data/scripts/exiles/` (managers, intel, stages) + `data/scripts/marketconditions/`
> (conditions). Config: `data/config/exiles/director_config.json` → `"exileblackfleet"`.
> Fiction: Admiral Mourad's rebuilt pirate armada, Midan-backed, based at the Sallee/Lundy
> anchors east of the Core. Defeatability is emergent: anchors dead → Stockpiles gone → rabble.

### Provisional origin (author direction, 2026-07-12)

Midan quietly supplied an ambitious pirate who wanted to operate independently of Kanta's faction.
The Black Fleet attacked Nachiketa; the resulting chaos let Midan commit its own forces to steal the
Hegemony's pristine nanoforge at Chicomoztoc. Captain Tapper escaped the Hegemony counterstrike at
Nachiketa and joined the Black Fleet. Later, the Hegemony cornered and battered the fleet through
straightforward pirate hunting and good tactics, killing its original leader. Tapper took command;
the survivors withdrew, rebuilt with Midan support, and eventually resumed operations in the Core.

This is the preferred direction, but exact dates/details remain provisional pending a full faction
write-up. Midan did **not** engineer the original leader's death: making every reversal part of a
perfect Midan plan would violate the setting's fallible-faction tone. **Admiral Mourad is Captain
Tapper under a new identity**; how and when the player learns this remains a Phase-2 decision.

### Midan relationship and distinct worldview (author ruling, 2026-07-12)

The Black Fleet is a directed and materially controlled proxy, but it is **a tiger Midan keeps fed,
not a dog on a leash**. Its worldview is the vanilla Pirates' nihilism: the sector is collapsing, so
take what can be taken and revel in the fire. Midan instead acts from terminal certainty that the
Core must be exterminated for humanity to survive. Most Black Fleet pirates are deliberately kept in
the dark about Midan's motives and ultimate goal. If Midan's plan succeeds, it may try to destroy the
Black Fleet before its savagery turns on Midan itself.

The two factions begin mutually **FRIENDLY**, with reciprocal Nex relationship bounds. A private
Midan/Black Fleet economy isolation was implemented 2026-09-16 and **confirmed in play by Sean
2026-09-19**: their markets share the `exiles_midan_network` econ group starting after economy
load, with ownership-following updates, and the isolation improved both anchors' Accessibility
considerably. Sean plans to tune the specific figures; Nex ownership-transfer and reload behaviour
are still unverified live. See `EXILES_ECONOMY_MAP.md` §10h.

Mourad/Tapper may not share the average pirate's appetite for destruction and may distrust Midan; an
alternative possibility is that Midan promised him eventual full membership and sanctuary. His true
understanding, bargain, and loyalty remain open quest material requiring author approval.

---

## 1. The loop at a glance

| System | Cadence (production) | What it does | Player lever |
|---|---|---|---|
| **Commerce raids** | ~45d, max 2 active | Hunt shipping in a system for ~45d; Corsair Activity blankets hostile markets | Kill raiders for bounty/rep |
| **Assaults** | ~120d, max 1 active | Sack a designated market (+ feasible follow-ups); unrest, looted docks, Sacked scar, loot fenced at anchor | Defend; bounty/rep |
| **Extortion** | adaptive 10–60d, max 3 (config; ceiling 12) | Disguised collectors visit defenseless markets → unattributed Price Spikes | Kill the collector **flagship** |
| **Bounty/rep** | per battle | The raided faction pays per corsair hull killed during an op + rep | The heroism vector |

All ops originate from the two anchor markets (resolved **via station entities**
`exilebf_sallee_station` / `exilebf_lundy_station`, never hardcoded market ids). Ops stop
emergently when both anchors die. Player-owned markets/systems are code-exempt everywhere.
Quest override: `$exilebf_raids_enabled` (Boolean sector memory) beats the config default for
both raid tiers.

## 2. Two-tier ops (BlackFleetRaidManager + stages)

**Targeting** (`pickTarget`): nearest-weighted (`1/(1 + LY × distanceFalloff)`, falloff 0.10),
per-tier feasibility — commerce raids ignore station strength (mult 2.0; they hunt freighters,
not forts), assaults use full enemy-strength + stations (mult 1.25). Anti-hotspot: a launched-at
system is untargetable for `systemCooldownDays` (60). Rise-from-ashes FP ramp ×0.6→1.0 over 365d.

**Commerce raid** (`BlackFleetCommerceRaidStage`): presence-only — fleets loiter `presenceDays`
(45), never touch planets. **Corsair Activity** lands on day 5 of the presence on every hostile
market and **lifts on withdrawal** (planned end or <50% FP abort).

**Assault** (`BlackFleetAssaultStage`): single designated victim (manager-picked, size²-weighted)
  until it falls, then **multi-sack**: unsacked / unbattered / un-Sacked / non-paying secondaries
  the fleet thinks it can beat (vanilla strength comparison; autoresolve uses vanilla attrition,
  weakest first, primary-first discipline — can't beat the primary = whole op fails). Price Spikes
  protection is enforced during target construction, live-fleet raid approval, and final execution;
  a market that pays after an assault launches is still spared. Each sack: vanilla
ground raid (theft + Recent Unrest) → **Sacked** condition → looted docks (spaceport 0.8/20–35d,
heavy industry 0.6/30–45d, never population/military) → loot fenced at the source anchor
(**Business is Good**, lists merge across sacks, cap 6). Corsair Activity is **presence-based**
here too: same day-5 blanket, lifts on withdrawal — NOT a sack effect.

**Arrival window and arrival gate — DOCUMENTED, DELIBERATELY UNCHANGED (Sean, 2026-09-19).** The
assault stage inherits vanilla `PirateRaidActionStage.updateRoutes()`, which runs once on arrival.
Two consequences: (1) the op's clock is **vanilla's 20–30 (+10–15 with a station) roll**, not an
Exiles setting — no config key governs it, so nothing is being silently ignored; the post-sack
`SECONDARY_SACK_EXTRA_DAYS` (20) extends whatever vanilla rolled. (2) Vanilla compares in-system
Black Fleet strength with the primary's defense (enemy + station) at the instant of arrival; if the
primary cannot be beaten, targets clear and the clock zeroes, so on the next tick `autoresolve()`
re-checks, calls `markProsperityDefeat()` (−30 reserve) and the op FAILS without a fight. **This is
consistent with single-victim discipline** — the assault is locked to the primary until it falls and
never skips ahead to a softer target — but it means an assault whose primary grew stronger in
transit ends on the arrival tick rather than holding. The launch-time feasibility gate (mult 1.25)
normally prevents that matchup. The Ashen rehearsal and Heg-League strike stages received an
override that restores a configured window (Ashen doc §14.11); Sean chose not to apply it here
while assaults are running well. If a `Black Fleet assault` ever reads as failing immediately on
arrival in the log, this is the first place to look.

**Intel presentation**: both tiers post as **"System Bounty - [system]"** — no
"Faction: Black Fleet" bullet, hidden until in-system arrival (or player battle contact),
outcome-neutral "The raid … has ended" close. Pre-arrival collapses die unannounced.

**Bounty/rep** (`BlackFleetRaidIntel`, Omicron pattern): the raided faction (assault victim's /
biggest hostile market's) pays `bountyPerFrigate` (3000) × hull size × player involvement +
`SYSTEM_BOUNTY_REWARD` rep, deferred payout popup, INTEL_BOUNTY tag, pays through the ending
window. Vanilla pays NOTHING for raid defense — this is deliberate mod surface.

## 3. Extortion racket (ExileBlackFleetExtortionManager)

Collector fleet = sim-faction **independent** (the disguise IS the protection), TRADE_SMALL,
token escort, transponder on, `$noRepImpact`, spawns/despawns in **hyperspace** near the anchor
(own patrols would kill it in-system). No `makeNonHostile`: Kanta/Path interdiction stays
possible. **Targets**: markets with no *functional* military beyond ground defenses/heavy
batteries (patrol HQ / military base / high command / any station blocks — but `isFunctional()`,
so disruption opens windows), not hostile to independents, not player/BF/Midan.

Arrival → **Price Spikes** (120d, unattributed: player-buy +50% everything; refugee enclaves
crew +100% only; player SELL never touched) + Business is Good +10d at the home anchor.
Extorted markets are exempt from assault victim-picking AND Corsair Activity (the condition is
the coordination flag). Cadence adapts to assault outcomes via sector-memory counters
(success ×0.75 / failure ×1.35, clamp [10, 60]).

**Flagship-kill logic** (bounty-mission style): the racket lives and dies with the collector
flagship (`op.flagshipId`); attacked collectors always flee (`MEMORY_KEY_MAKE_ALLOW_DISENGAGE` —
one pursuit catches everything); leaderless survivors scatter home. Consequences:

| Who kills flagship | When | Result |
|---|---|---|
| Player | any time | Wreckage-reveal message + Black Fleet rep −0.05 |
| Player | post-collection | …plus Price Spikes removed + target-faction rep −0.03 + **grudge** (never refugees) |
| Player | pre-arrival | …and nothing else (the market never knew) |
| NPCs | any | Quiet collapse, nothing player-facing |

**Grudge** (`$exilebf_grudge`, playtest-5 fix): pulls **system selection ×10 for BOTH tiers**
(assaults sack it; commerce raids strangle it — works vs fortress systems), relaxes feasibility
×2 at system and victim selection (revenge is worth losses), expires after **365d** unavenged,
cleared by the sack. The original victim-only weighting was verified inert in playtest-5.

## 4. Market conditions (all in `data/campaign/market_conditions.csv`)

| Condition | id | Effect | Lifecycle |
|---|---|---|---|
| **Corsair Activity** | `exilebf_corsair_activity` | −50% accessibility; −2 availability on supplies + fuel (always) + top-3 import-shortfall commodities; grid tooltip | Presence-scoped: day 5 → withdrawal (both tiers); 45d self-timer backstop |
| **Sacked** | `exilebf_sacked` | −20% accessibility, ×0.75 ground defenses, **no stability** | 60d, refresh on re-sack; anti-repeat marker (victim-pickers skip) |
| **Price Spikes** | `exilebf_price_spikes` | Player-buy +50% (refugees: crew +100% only); unattributed | 120d, or dies with the collector flagship |
| **Business is Good** | `exilebf_business_is_good` | Stockpiles output boost on fenced loot commodities | 30–60d, +10d per collection, loot lists merge |

## 5. Safety & balance rails

- **Unrest deciv-guard** (`ExileUnrestGuard`, settings.json `exilesUnrestDecivGuard`, ships
  true): Recent Unrest stacks uncapped and decays 1pt/90d — repeated attacks can decivilize
  (observed: Gilead, double-teamed since `luddic_church` is in BOTH Omicron and BF target
  lists). "Battered" = unrest ≥ 4 or stability ≤ 1 → skipped by assault victim-picking; Omicron
  still bombards but skips its unrest add. Player sets false to let the sector burn.
- **Sacked** doubles as a 60d anti-repeat marker (outlasts vanilla's 30d RECENTLY_RAIDED), and
  its ground-defense cut makes sacked worlds tempt *other* predators — free chaos ripple.
- Deciv-safety of disruption: temporary `setDisrupted` cannot decivilize (verified); the guard
  closes the stacked-unrest path.

## 6. PRE-PRODUCTION CONFIG CHECKLIST (director_config.json)

Status (re-checked 2026-10-06, bug sweep BF-5): the raid and assault values are ALREADY at
production, and extortion `debugMessages` is already **false**. Only the extortion start delay is
still a test value. Extortion `maxActive` 3 is DELIBERATE (Sean, ruled 2026-10-06): player-tunable,
code default now 3, hard ceiling 12. Original checklist kept for the record:

- raid: `intervalDays` 10 → **45**, `startDelayDays` 5 → **90**
- assault: `intervalDays` 25 → **120**, `startDelayDays` 15 → **180**
- extortion: `startDelayDays` 20 → **~60**, `debugMessages` true → **false** (the dispatch
  popup is a test aid; silence IS the extortion design)

## 7. Known-open / next steps

- **Review findings 2026-07-28 — RULED/FIXED 2026-07-29** (full write-up and the corrections in
  `EXILES_KNOWN_ISSUES_AND_NEXT_STEPS.md` → "Raid-director code review — 2026-07-28"). All three
  BF items are resolved in code; **none is live-tested yet**:
  - (a) **FIXED** — assault multi-sack secondaries accepted any hostile market, so a **refugee
    enclave** (BF is HOSTILE to `exilerefugee`) could be sacked after the primary fell. Author
    ruling: refugees are **never** a direct NPC target. `ExileProtectedFactions` is now the single
    authority, checked in `BlackFleetAssaultStage.getTargets()`/`canRaid()`.
    *Still unruled:* Corsair Activity's system-wide blanket still reaches refugee enclaves — an
    area effect rather than direct targeting.
  - (b) **DIRECTION SET** — the player-**system** exclusion in §1 is BF-specific (Omicron exempts
    player markets only). Both directors will take a "never deliberately target the player"
    invariant, with deliberate anti-player attacks moving to a separate layer on vanilla's Colony
    Crisis API. See `EXILES_ANTIPLAYER_ESCALATION.md`. Not yet implemented.
  - (c) **FIXED** — `pruneEnded` was gated behind `raidsEnabled()`. The manager no longer tracks
    ops at all: `activeRaids`/`activeAssaults`/`pruneEnded` are gone, `countActive(boolean)` reads
    live ops from the intel manager, and the extortion outcome self-posts from
    `BlackFleetRaidIntel.notifyEnded()`. **Watch in playtest:** that signal now fires immediately
    on op close rather than on the next cadence tick.


- Author review pending: anchor/Meridian placement (placeholder coords), system/planet names,
  same-flag-as-pirates call, patrol volume.
- Extortion v2 candidates: ships/weapons/blueprints price spikes (submarket path, deferred),
  bar-rumor breadcrumbs + truth-reveal quest (Phase 2, rides the quest scaffold), extorted-market
  cargo-run quest hook (spec #5).
- Grudge fix (system-pull, ×2 relax, 365d expiry) is built but **untested in-game** — verify a
  post-collection kill eventually draws an op to the grudged market's system.
- Phase-1 Director generalization will absorb BlackFleetRaidManager + extortion manager as
  config, not code (CLAUDE.md §6).

## 7. Extended live-test confirmation — 2026-09-01

The extended `save_Issac_3686564620849347735` run removes the earlier ambiguity about whether the
Black Fleet scheduler was active. The log identifies these operations through
`BlackFleetRaidManager`, `BlackFleetCommerceRaidStage`, and `BlackFleetAssaultStage`; they are Black
Fleet operations, not vanilla Pirate raids.

- Commerce raids launched from both anchors at 160–246 FP and successfully applied/lifted Corsair
  Activity in Westernesse, Eos Exodus, Arcadia, and Zagan. A 171-FP Magec raid launched near the end.
- Lundy launched a 468-FP assault on Galatia. It sacked Derinkuyu Mining Station and Ancyra,
  applied/lifted Corsair Activity correctly, fenced the loot at Lundy, and ended successfully.
- The sample supports the current **tuning hold**: do not reduce Black Fleet FP or cadence before a
  further run with the whole campaign ecology active. The approved additive freighter work remains
  the next Black Fleet implementation pass and must preserve the existing combat budget.

The same save also confirms the intended per-anchor semantics of **Business is Good**:

- the condition is not faction-wide and is not a starting condition;
- a successful assault applies it only to that assault's source anchor for 30–60 days;
- multi-sack loot lists merge at that source anchor, up to the six-commodity cap;
- an extortion collection extends an already-active condition at its own source by 10 days but does
  not create one;
- Sallee's earlier Mayasura assault fenced Port Tse/Mairaath loot at Sallee, but that temporary
  condition expired long before the final visit;
- Lundy's later Galatia assault fenced ore, domestic goods, drugs, and luxury goods at Lundy, so
  Lundy correctly had Business is Good while Sallee did not.

This behavior is mechanically correct. If players continue to read the condition as faction-wide,
clarify the market-condition prose so it says that **this anchorage** is fencing a recent haul; do
not synchronize the condition between anchors.

---

**2026-09-28 cross-reference: window-end fix (built, untested in game).** By Sean's ruling, the
op's action stage now resolves at the end of its window on the strength comparison
(`autoresolve()`) whether or not the player is in spawn range. It used to fail outright
(`finish(...)` with no strength check) when the player happened to be within 1.6 LY at that tick. A
`WINDOW END` log line records each resolution. Affected here: `AshenRehearsalStage` and the
inherited `AshenCulannStrikeStage` (Ashen); `BlackFleetAssaultStage` (Black Fleet; its window length
is unchanged). Full record: `docs/EXILES_HEG_LEAGUE_WAR.md` §14.14.10–§14.14.12.
