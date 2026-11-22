# Exiles - Omicron Protection of Tri-Tachyon

**Status:** Phase-1 third pass built 2026-07-22 after final custom-system placement made
long-range Hybrasil reinforcement unreliable by design. Loose classes pass the Janino
compile-check and the early-loaded rule command compiles into its JAR. The station-only
sleepers and Culann strategic detachment await live testing; balance values remain provisional.

## 1. Mandate

Omicron remains its own faction. It does not become part of Tri-Tachyon's normal ship roster,
and there is no defensive proxy faction.

The Initiative protects four explicitly named markets. The list is intentionally not dynamic:
later conquests and mod-added Tri-Tachyon markets do not silently acquire Omicron protection.

| Market | Role | Station complement | Strategic-response threshold |
|---|---|---:|---:|
| Culann | Hybrasil industrial asset + reserve host | 70-100 FP | 350 hostile-operation FP |
| Eochu Bres | Tri-Tachyon capital | 80-110 FP | 350 hostile-operation FP |
| Port Tse | commercial outpost | 35-55 FP | 250 hostile-operation FP (raised from 200 on 2026-08-02) |
| Tibicena | commercial outpost | 45-65 FP | 250 hostile-operation FP (raised from 200 on 2026-08-02) |

Culann additionally houses one 180-220 FP strategic detachment. It is a pre-positioned fleet,
not a Nexus, market, or Omicron Director node. Values are externalized under
`triTachyonDefense` in `data/config/exiles/director_config.json`.

## 2. Dormant station complements

Each protected station carries one persistent Omicron-faction fleet. In peacetime it is hidden,
transponder-off, non-aggressive, ignored by ordinary NPC fleets, and assigned a passive orbit at
the station. This is the campaign abstraction for smaller Omicron hulls latched to the structure
and powered down.

Merely targeting the system with a strategic operation does **not** wake these fleets. For an NPC
operation, the manager resolves the operation's actual `RouteData`; a sleeper wakes only when one
of those fleets closes within 1,500 campaign units of its assigned station. This route-specific
check prevents unrelated same-faction pirates near the player from exposing a reserve. For a
direct player attack, the existing `mktEngage` hook wakes only the attacked station's complement.

Once activated, a complement remains visible for the configured five-day stand-down. Its
fleet-scoped orders initially target the designated attacker, but surviving drones may pursue
that attacker across the system before returning and relatching.

Losses are not replaced immediately:

- Culann and Eochu Bres station complements: 90-day replacement delay.
- Port Tse and Tibicena station complements: 120-day replacement delay.
- No replacement is produced if every mature Omicron Nexus has been destroyed.
- An already deployed complement does not disappear merely because the Nexuses die.

## 3. Threat detection and direct attacks

The manager scans live `RaidIntel` operations. An operation is strategically relevant when:

1. its target system contains at least one configured protected market; and
2. the raiding faction is hostile to Tri-Tachyon.

This scan arms strategic response logic but leaves station complements dormant. Each
point-defense fleet activates independently under the proximity/direct-attack rules in section 2.

A direct player attack uses a separate exact hook. The `mktEngage` rule wakes, reveals, and places
the market's attached complement beside the station immediately before vanilla `MarketCMD`
gathers nearby defenders. It does not run when the player merely approaches, lands, trades, or
views the market's defenses.

Before commitment, opening the normal military-options/defenses screen checks whether a living
station reserve is present. If so, it adds this qualitative warning without revealing fleet
strength or composition: "An unusual number of targeting sensors are suddenly directed at your
fleet. Evidently, the station's visible defenses are not all that Tri-Tachyon has prepared."

The public hook
`OmicronTriTachyonDefenseManager.requestDefense(threatKey, system, factionId, fp, etaDays)`
allows future Exiles operations or quests to request strategic-response behavior before or
without creating a `RaidIntel`. It does not pre-emptively reveal station sleepers.

## 4. Defense-only rules of engagement

The fleet retains the real `exileomicron` faction and Omicron reputation consequences. Its local
orders override ordinary diplomacy without changing faction relationships sector-wide:

- Never enforce transponder use.
- Never police landing or trade access.
- Never pursue the player merely because the player is hostile to Omicron.
- Treat every faction except the identified attacker as non-hostile for that deployment.
- Intercept only fleets that actually belong to the identified attacking faction.
- Defend Tri-Tachyon's station and nearby forces against the identified attacker.
- Retain ordinary self-defense and Omicron-core recovery behavior if attacked.

Thus market access remains Tri-Tachyon's decision. Omicron intervenes when a protected asset is
actually attacked, not when a visitor violates a relationship threshold Omicron happens to hold.

## 5. Culann strategic detachment

One persistent 180-220 FP strategic detachment is concealed at Culann:

- It responds only to operations of at least **350 FP**, exactly the configured minimum strength
  of a Black Fleet Assault.
- It protects all of Hybrasil, including Eochu Bres, but is politically and physically based at
  Culann rather than beside Tri-Tachyon's Board and CEO.
- It remains hidden while the hostile operation assembles and travels. It activates when an
  operation fleet enters Hybrasil or when ETA falls within two days.
- It targets the operation's actual route fleets first, rather than opportunistically hunting
  unrelated same-faction traffic.
- It is a fixed, persistent detachment with persistent losses, not a fleet generated to match
  each threat.
- Destruction starts a 180-day replacement delay. Replacement requires at least one living mature
  Omicron Nexus; an already deployed detachment survives the later loss of those Nexuses.
- It is not registered in `OmicronDirectorConfig.nodes`, has no raid/incursion timers, and consumes
  no Director operation slot when dormant or deployed.
- A direct player attack of at least 350 campaign FP against Culann or Eochu Bres also commits it
  through the precompiled station-defense hook.

One threat cannot receive both this reserve and a newly launched Nexus response. The local
detachment answers first; the long-range response is the fallback.

## 6. Mature-Nexus fallback

If the Culann detachment is destroyed, absent, or otherwise unavailable, a qualifying operation
may draw one additional fleet from the nearest available mature Nexus:

- Hybrasil considers the fallback at 350 hostile FP; outposts at 250 FP (200 before 2026-08-02).
- Response strength is `threat FP x 0.65`, clamped to 90-220 FP, then multiplied by that node's
  current strength capacity.
- The Nexus spends three days preparing the fleet.
- A response is skipped when its projected arrival would be more than ten days late.
- Lattice does not provide strategic responses until its full Nexus completes.
- A Nexus already sourcing either an offensive operation or another response is unavailable.
- While a response exists, it occupies that Nexus's source slot. It can delay a new raid or
  incursion from that Nexus, but does not consume either faction-wide offensive-operation slot.
- After the alert ends, survivors return to their launching Nexus. If that Nexus was destroyed,
  they stand down at the protected market instead.

## 7. Implementation map

- `OmicronTriTachyonDefenseManager` - protected-market registry, route-specific station activation,
  persistent Culann strategic detachment, fleet-scoped ROE, replacement delays, and Nexus fallback.
- `OmicronTriTachyonResponseScript` - preparation, travel, defense, return, and per-node Nexus
  response capacity accounting.
- `OmicronDirectorConfig` / `director_config.json` - protected markets, 1,500-unit station trigger,
  Culann reserve block, and all provisional response tuning.
- `OmicronDirector.countLiveOpsForNode(...)` - shared source-occupancy query; ordinary launches
  will not select a Nexus that has a live strategic response.
- `ExileOmicronStationDefense` / `rules.csv` - early-loaded direct-player-attack wake-up, executed
  immediately before vanilla `MarketCMD engage`; the command is packaged in
  `jars/ExilesRuleCommands.jar` because rules resolve before loose Janino scripts.
- `ExilesModPlugin` - save-compatible manager registration and fleet adoption on game load.

## 8. Required live tests

Compilation proves API compatibility, not campaign behavior. Before calling this pass complete:

1. Confirm all four station complements plus Culann's separate strategic detachment generate with
   the intended Omicron compositions and cores, then remain invisible in peacetime.
2. Approach all protected markets without a transponder; confirm Omicron never initiates an
   inspection or attack.
3. Test player/Tri-Tachyon/Omicron relationship mismatches in both directions.
4. Attack a protected station without a prior raid alert; confirm its hidden station complement is
   pulled into the station battle.
5. Launch a sub-350-FP operation toward Hybrasil; confirm neither station sleeper nor the Culann
   strategic detachment reveals itself while attackers remain away from a protected station.
6. Move one operation-route fleet within 1,500 units of Culann; confirm only Culann's station
   complement wakes. Repeat at Eochu Bres and confirm only Eochu Bres's complement wakes.
7. Launch a 350-500 FP Black Fleet Assault toward Hybrasil; confirm the Culann detachment stays
   hidden in transit, then activates on system entry and intercepts the actual assault route.
8. Confirm the same assault does not also launch a mature-Nexus response.
9. Attack Eochu Bres directly with player fleets below and above 350 campaign FP; confirm the local
   complement always joins, while the Culann detachment joins only at or above the threshold.
10. Destroy local and strategic fleets; confirm 90/120-day local and 180-day strategic replacement,
    all gated by a surviving mature Nexus.
11. Repeat at Port Tse or Tibicena below and above the 250-FP Nexus-response threshold (200 before 2026-08-02).
12. Destroy a response's launching Nexus while it is deployed; confirm it stands down cleanly at
    the protected market after the alert.
13. Confirm a live Nexus response prevents the same Nexus from sourcing an offensive operation;
    confirm neither the response nor Culann's pre-positioned detachment consumes the faction-wide
    offensive-operation cap.

## 9. Live-test history

### First and second passes (2026-07-19)

- Log confirmed dormant complements at all four configured markets.
- A Black Fleet raid successfully launched and operated a mature-Nexus response.
- First-pass direct attacks failed to include hidden complements. The `mktEngage` precompiled hook
  fixed this; the Port Tse retest passed.
- Generic patrol AI chased unrelated pirates. Explicit target-faction intercept/hold assignments
  replaced generic patrol orders.
- The defenses screen gained the qualitative targeting-sensor warning.

### Third-pass rationale (2026-07-22)

Final system placement leaves the mature Omicron network roughly 20-24 light-years from Hybrasil.
That distance remains meaningful: Omicron does not teleport newly manufactured fleets into the
Core. Tri-Tachyon instead concealed a finite detachment at Culann. Eochu Bres remains protected by
its own station sleeper without placing the strategic reserve beside the Board and CEO.

### Scheduler/Lattice confirmation (2026-09-01)

- Lattice reached 75% construction, sourced an ordinary raid, and still did not provide a strategic
  response while under construction.
- At approximately day 540 it atomically became a full Nexus. Later in the same run it dispatched a
  171-FP strategic response toward Port Tse (about 27 days projected travel) against Ashen threat
  `raid_86888`.
- The response was independent of offensive global capacity; the run separately reached
  `liveGlobal=2` with two offensive operations.
- Direct-attack activation, source destruction during deployment, and a live response blocking the
  same Nexus's next offensive launch remain open items in §8.

---

## Umbrella gate — outposts start uncovered **[RATIFIED 2026-08-02]**

> **2026-08-30 scheduler amendment:** the placement values remain `spawnAtGrowth: 0.85` and
> `spawnAfterDays: 540`, but `spawnAtGrowth` now reads a dedicated 365-day umbrella clock.
> It no longer reads Lattice construction progress, and destroying Lattice no longer delays or
> cancels the corporate protection mandate. The rationale below is retained as decision history;
> wherever it describes UC-Nexus-gated umbrella growth, this amendment supersedes it.

Port Tse and Tibicena begin the campaign with **no Omicron complement**. Culann and Eochu Bres
are unchanged and covered from day one.

**Why.** Sean's requirement from the Ashen Pass-4 testing was "no Omicron response from the
stations for a while; response once raiding heats up". Ashen-side attempts at this failed: the
loiter lever could not gate it, because raiders reach the 1,500u station-activation ring on their
own while pursuing Tri-Tachyon traffic (see `EXILES_ASHEN_CRUSADE_WAR.md` §6.1a and §14.4). The
only deterministic answer is that the complements are **not there yet**.

**Mechanism.** Two new per-market fields on `triTachyonDefense.protectedMarkets`:

| Field | Meaning | Default |
|---|---|---:|
| `spawnAtGrowth` | Omicron growth progress (0..1) required before the complement is placed | `0` = day one |
| `spawnAfterDays` | Fallback: place it after this many days regardless | `0` = no fallback |

Both outposts ship at **`spawnAtGrowth: 0.85`, `spawnAfterDays: 540`**.

**Why growth, not a timer or the Ashen war clock** (all three were considered):

- **Not the Ashen war clock.** The mandate protects Tri-Tachyon from *everything* — Black Fleet,
  pirates, Luddic raiders. Gating on the Crusade's clock would leave Port Tse uncovered against a
  Black Fleet assault for reasons unrelated to the Black Fleet, would invert the dependency
  (mature Omicron code reading newer Ashen code), and would break outright if the Ashen war were
  disabled via `$exiles_ashen_war_enabled`. Omicron does not know the Crusade has a clock.
- **Not a plain timer.** Growth gives the same "time passes" behaviour with better ownership and
  no new saved state — `escalationRampDays` is 365 and progress is already tracked and clamped.
- **Growth accrues only while the Lattice UC-Nexus lives.** So a player who finds and kills it
  early also keeps these outposts uncovered. A real strategic consequence, for free.

`spawnAfterDays` is the safety valve on that last point: it makes a frozen UC-Nexus a **delay**
rather than a permanent cancellation, so an unrelated player action cannot leave two Tri-Tachyon
markets soft prey for the rest of the campaign. Set it to 0 for the absolute version.

**Timing.** 0.85 growth ≈ day 310, past one full Ashen war cycle (~210–270d production) and short
of two. Growth is clamped at 1.0 (day 365), so a true two-cycle gate would need
`escalationRampDays` raised — which would slow Omicron's escalation generally, and is not
recommended.

**Implementation.** `OmicronTriTachyonDefenseManager.isUmbrellaOpen(spec)`, checked in
`ensureLocalDefenders()` alongside the existing replacement-cooldown and mature-Nexus gates.
Already-existing complements are adopted regardless, so this never despawns anything. The
fallback baseline (`umbrellaStartTs`) is saved and set on first use, so introducing the feature
into an existing campaign behaves sanely. Code defaults in `OmicronDirectorConfig` mirror the
JSON, so a missing config does not silently revert to day-one coverage.

**Untested in game** as of 2026-08-02.
