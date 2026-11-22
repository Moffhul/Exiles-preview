# Exiles — Omicron Logistics: Player-Degradable Attacks (Design Decision)

Created: 2026-06-22
Status: **DECISION MADE — design only, NOT yet built.** No scripting done; this captures the verdict and the
implementation shape for when Omicron's attack system is built (Phase 1 Sector Destabilization Director).

Source: a 7-agent source dive across the vanilla Remnant, pirate-base, HostileActivity, and raid/disruption
subsystems + the mod's current Omicron/Midan code, then two opposing design advocates (economy vs. script). The
raw dossier was a temp artifact; this file is the durable record.

> **See also `EXILES_OMICRON_FACTION_ARCHITECTURE.md` (2026-06-24)** — the *broader* economy-vs-script-vs-hybrid
> decision for the faction as a whole (this doc decided the narrower *attack-lever* question). That investigation
> generalizes this decision to the whole faction (recommend: marketless, like vanilla Remnants/Omega) and **recovers
> the pirate / Luddic-Path temporary-base lifecycle code map** that this doc's raw dossier discarded.

---

## The goal

The player should be able to **weaken or slow down Omicron's attacks by attacking their "logistics centers."**
The open question was whether to model Omicron logistics through the game **economy** (real markets/industries the
player raids/disrupts) or through **script/event** machinery (bespoke destroyable entities + a director), or a hybrid.

## DECISION

**Build Omicron logistics as bespoke destroyable ENTITIES (the vanilla Remnant-station model), driven by the
Phase 1 Sector Destabilization Director. Do NOT give Omicron economy markets.** The desired loop — *attack the
logistics → attacks weaken* — is almost exactly the vanilla Remnant Nexus loop already.

Confidence: high (the script approach; the economy advocate itself landed only at medium and conceded the
fiction/baggage problems).

---

## The three models considered

| Model | "Attack to weaken" mechanism | Fiction fit (machine faction) | Verdict |
|---|---|---|---|
| **Economy** — hidden industry-markets | Player runs vanilla DISRUPT ground-raid / bombard → `setDisrupted()` → `isFunctional()=false` → Director reads surviving node capacity | ❌ Markets imply a populated, trading, **conquerable**, Nex-diplomatic polity | Rejected |
| **Script/entity** — Remnant Nexus | `SourceBasedFleetManager`: `source.isAlive()` + an integrity scalar gate all spawning; kill it → `currMax=0`+`setDone()`; attrite fleets → decaying suppression counter | ✅ This *is* the lore (Omicron is Remnant-derived, zero-population) | **CHOSEN** |
| **HostileActivity meter** | Factors push a 0–600 meter; `HAPirateBaseDestroyedFactor` knocks it down on a kill | ⚠️ Hard-bound to the **player's** colonies only | Optional bolt-on, later |

**Why HostileActivity does not fit the core case:** the documented Omicron behavior is "attacks Luddic Church +
Path worlds" — NPC-vs-NPC. The HostileActivity event models threats **to the player** exclusively, so it does not
apply to the core Omicron fantasy. It only becomes relevant if/when Omicron attacks the *player's* colonies, as
optional later polish.

## Why script/entity wins (decisive reasons)

1. **Fiction guardrail (CLAUDE.md §3) is near-non-negotiable.** Omicron = "misclassification, not malice,"
   zero-population, Remnant-themed. The Remnant station-source system is the same machinery that animates the
   faction Omicron is themed on. Markets drag in stability, population, decivilization, trade-fleet visitation,
   "deal with the commander," and (under Nexerelin) diplomacy/invasion/conquest — all lore-breaking for a machine.
2. **We already paid the economy tax once.** The refugee-economy work found the resolver fights condition levers
   and NPC market size is static (see `EXILES_ECONOMY_MAP.md` §7). A bespoke integrity scalar the Director reads is
   a deterministic lever with none of that fight.
3. **The loop maps ~1:1 to existing vanilla code.** `SourceBasedFleetManager.advance()` already does
   proximity-scaled fleet caps, a decaying suppression counter (`destroyed`/`respawnDelay` — temporary reprieve
   from attriting raids), and a hard `source.isAlive()` gate that forces spawning to stop. That IS the
   weaken/slow/stop verb set, for free.
4. **It's mostly Phase 1 Director work anyway.** The Director is already planned, and `ExileOmicron0.java` already
   creates the Nexus / Damaged Nexus / Sensor Array entities — they just need to become station-mode (destroyable)
   sources. The economy path would add markets + industries + specs *on top of* the Director.

**The one thing the economy model does better** (and how we recover it): it gives free, save-persistent,
self-expiring degradation state (the `$core_disrupted_<ClassSimpleName>` flag) and reuses the player's existing
DISRUPT verb. In the script model we hand-roll an integrity scalar + persistence + intel/UI. That state is plain
float/boolean logic (low risk), and the legibility gap is closed by the Sensor Array's warning role + intel rows
(below). If a *non-combat* "raid the depot" option is ever wanted, a single narrowly-scoped hidden industry-only
node could be added later without giving Omicron a full economy — deferred.

---

## Recommended implementation shape (for when we build)

- **Three existing entities → three logistics roles** (no new nouns; depth from verbs):
  - **Nexus** = fleet-production source. Caps concurrent raids + per-raid strength. Upgrade from a bare
    `addCustomEntity` token to a station-mode `CampaignFleetAPI` (mirror `RemnantThemeGenerator.addBattlestations`)
    so it is combat-destroyable.
  - **Sensor Array** = targeting/warning. While alive, raids hit hard with no warning; destroy it → the player
    gets a warning window and Omicron's target selection degrades. (This also narratively justifies the warnings.)
  - **Damaged Nexus** = a pre-damaged demonstrator of the integrity mechanic; natural Phase 2 quest site
    ("recover Omicron cores / Tri-Tachyon data").
- **Graduated integrity, not vanilla's binary.** Vanilla ships only alive/dead + one `$damagedStation` 0.5 step.
  Give the Nexus a 0–1 integrity the player grinds down; the Director scales cadence / fleet FP / warning off it, so
  raids **visibly** shrink as the player hammers the source.
- **Drop the Remnant escalation term.** Vanilla's `totalLost*4` makes replacement fleets *stronger* the more you
  kill — that fights a logistics-attrition fantasy. **Keep** the decaying suppression counter; **cut** the escalation.
- **Invest in legibility.** The model's real weakness is invisible cause→effect. Tie warning messages + an intel
  row to integrity changes so the player *learns* that hitting the Nexus is what throttles the swarm. Without this
  the loop feels like nothing is happening.
- **Defer the HostileActivity meter** to if/when Omicron attacks the *player's* colonies. Do not make the core loop
  depend on it (player-colony-only; its `setup()` silently strips custom factors unless re-added via the
  `finishedAddingCrisisFactors` hook every time).

---

## Key vanilla references (read these when building)

- `com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager` — `advance()`: `source.isAlive()` check,
  `currMax` proximity ramp, `currMax -= ceil(destroyed)` decaying suppression, `currMax=0` + `setDone(true)` on
  source death. **The core pattern to subclass.**
- `…procgen.themes.RemnantStationFleetManager` — `spawnFleet()` builds fleets from `FleetParamsV3` point budgets;
  `reportFleetDespawnedToListener` increments `totalLost` (the escalation term we are dropping).
- `…procgen.themes.RemnantThemeGenerator` — `generateForSector()` wiring + `addBattlestations()` creating the
  station-mode `REMNANTS` `CampaignFleetAPI`. Note: code deliberately does NOT `setMarket()` the nexus
  ("can actually trade etc then, no good") — keep that discipline.
- `…intel.events.RemnantNexusActivityCause` / `RemnantHostileActivityFactor` — the meter feed gated on a live
  station; the `$damagedStation` half-contribution pattern (basis for finer integrity tiers). *(Only relevant for
  the optional player-targeted HostileActivity bolt-on.)*
- `…intel.events.HostileActivityEventIntel.reportBattleOccurred` + `HAPirateBaseDestroyedFactor` — how vanilla
  credits a player station-kill to lower a meter. *(Optional bolt-on only.)*
- Economy path, if ever needed for a single non-combat node: `DisruptIndustryRaidObjectivePluginImpl.performRaid`
  → `BaseIndustry.setDisrupted/getDisruptedDays/isDisrupted/isFunctional` (self-expiring market-memory flag,
  output→0). Beware: real markets drag in `FactionHostilityManager` wars + colony/Nex baggage.

## Key in-repo references

- `data/scripts/world/systems/ExileOmicron0.java` — builds the Omicron system + Nexus / Damaged Nexus / Sensor
  Array (currently bare entities; the station-entity id is flagged with a TODO to confirm).
- Historical Midan Corvus task-force test (removed 2026-07-12) — confirmed that a non-Remnants faction can
  produce an intended faction-family fleet with `FleetFactoryV3`/`FleetParamsV3` and a null source market.
  Current activity implementations, rather than the deleted prototype, are the source of truth.
- `data/scripts/industries/ExileAshenMilitaryBase.java` — in-repo example of a fleet-source driving `RouteManager`
  patrols with cap/despawn discipline (reference for fleet-bounding hygiene, even though Omicron won't be an industry).
- `data/world/factions/exileomicron.faction` — Remnant theme/doctrine. (Also: `showInIntelTab:false` + absent from
  Nex `mod_factions.csv` = the same faction-visibility bug class already fixed for Refugees; track separately.)

## Implementation gotchas to respect

- **CLAUDE.md §7 leak discipline.** The removed Midan seed once double-added its script (new game + load) and
  forced fleets to never despawn — anti-patterns. The Director/manager must register once, **bound + despawn** raid fleets, and
  drop refs to dead sources/fleets, or it leaks across saves.
- **Janino.** Game-start compiles `data/scripts/*.java` via Janino — conservative old-style Java (no lambdas /
  streams / `var` / diamond). Director must avoid allocation in `advance()` and gate on `IntervalUtil`.
- **Poll, don't wait.** Any timer/integrity state self-expires with no end callback (the disruption gotcha
  generalizes) — the Director polls on its interval.

## Roadmap placement

This adds a **combat/strategic player lever in Phase 1** that *complements* (does not replace) the Phase 2 Omicron
quest arc ("why does Omicron attack the Church?"). Grind the Nexus → buy breathing room; the quest answers *why*.
The Damaged Nexus is the natural physical anchor for that Phase 2 arc.
