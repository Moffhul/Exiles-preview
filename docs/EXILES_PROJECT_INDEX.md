# Exiles Project Index

Created: 2026-06-10
Updated: 2026-09-06

> **Chronology note (2026-07-12):** this index predates the Midan/Black Fleet split and the completed
> Omicron/Black Fleet Phase-1 stacks. Use it as a structural map, not a current status report.
> `CLAUDE.md`, `EXILES_BLACK_FLEET_SYSTEMS.md`, and `war-capacity-design.md` are authoritative for
> current faction count and implementation status. A full index refresh belongs in the upcoming
> outstanding-items audit.

Purpose: compact first-read map for future Codex sessions. This is not a full code audit. Read `CLAUDE.md` first for current vision, guardrails, and build order.

Current player-observed redesign planning is tracked in `docs/EXILES_REDESIGN_REGISTER.md`. It is a
working register, not canon; accepted directions and open decisions are labelled separately.
The linked visible anti-decivilization recovery system is
`docs/EXILES_EMERGENCY_MEASURES.md` (version one implemented 2026-08-22, pending live test).
The Security Centers refactor was implemented 2026-08-20 and is Janino-clean, pending live testing;
its remaining policy decisions are tracked in the redesign register.
Current Omicron/Black Fleet strength baselines, cadence findings, logistics direction, and the
approved readiness, strength-progression, support-station, objective-budget, core, and Black Fleet
freighter implementation plan are recorded in
`docs/EXILES_OMICRON_BLACK_FLEET_TUNING_PLAN.md`. The Omicron first pass was implemented and
Janino-checked 2026-08-30; its first intact-network live run passed 2026-09-01 with tuning held.
Tests 2/3 motivated the approved 2026-09-06 support/guard pass: 240-day support rebuilds,
stronger Fuel attrition, whole-budget Mining scaling, and one passive-orbit guard per full Nexus.
**Omicron refactor successfully complete, author accepted 2026-09-06** after the combined Fuel/Mining
loss-and-recovery test and direct confirmation of guard lure resistance and Nexus battle joining.
Further offensive balance review is future tuning, not an incomplete refactor. The tuning plan's dated amendment
supersedes its historical formulas.
Black Fleet freight remains unimplemented. The same run's Black Fleet and Hegemony–League findings
are recorded in `EXILES_BLACK_FLEET_SYSTEMS.md` §7 and `EXILES_HEG_LEAGUE_WAR.md` §14.4.

## Current Artifact Status

- No earlier human-oriented project index, structure map, or handoff note was found in this workspace.
- `.codex` and `.agents` are mentioned in the sandbox read profile, but no actual workspace files were found there during this pass.
- The only pre-existing "map" file in the mod is gameplay data: `data/campaign/starmap.json`.
- Raw vanilla Starsector data is available outside this mod at `D:/Fractal Softworks/Starsector/starsector-core/data`, but no standalone vanilla guide/index was found there.

## Mod Identity

- Mod folder: `D:/Fractal Softworks/Starsector/mods/Exiles`
- Mod id/name: `Exiles`
- Author: `MoffHul11`
- Version: `0.1`
- Game version: `0.98a-RC8`
- Entry plugin: `data.scripts.exiles.ExilesModPlugin`

## High-Level Shape

Exiles is a Starsector custom-start/world-content mod. It adds four generated star systems, several custom factions, custom market conditions and industries, a difficult custom start, starting officers, starting player colonies, and several patches to vanilla markets.

## Development Lifecycle

- **Phase 0 — structural foundation:** establish the factions, systems, markets/entities, custom
  Xechas start, and core framework.
- **Phase 1 — behaviors and custom gameplay (current):** make factions act through raids, defenses,
  extortion, movement, economic pressure, and custom content such as Omicron Cores.
- **Phase 2 — quests/missions:** build the narrative arcs and player choices that explain, alter,
  or resolve the Phase-1 behaviors. “Quest” and “mission” are equivalent project terms.
- **Alpha Build Test:** after Phase 2, the author completes a full campaign as the first end-to-end
  validation of the assembled mod.
- **Polish pass:** correct and tune findings from the Alpha playthrough and prepare public-facing
  presentation, compatibility, and documentation.
- **Beta release:** public WIP download for broader player testing and feedback. Beta is a release
  milestone, not a fourth development phase.

Main source/data folders:

- `data/scripts/exiles`: mod plugin, start setup, event/watchdog scripts.
- `data/scripts/world`: sector generator and generated systems.
- `data/scripts/industries`: custom industries.
- `data/scripts/marketconditions`: custom market conditions.
- `data/campaign`: rules, industries, conditions, special items, starmap.
- `data/campaign/econ`: economy market JSON.
- `data/world/factions`: faction definitions and faction list.
- `data/hulls`, `data/variants`, `graphics/ships`: ship hulls, skins, variants, sprites.
- `graphics/icons`: condition and industry icons.
- `out/production/Exiles`: generated/compiled output; do not treat as source of truth unless debugging build output.

## Entry Points

- `data/scripts/exiles/ExilesModPlugin.java`
  - `onNewGame()`: calls `new ExilesSectorGen().generate(...)`.
  - `onNewGameAfterEconomyLoad()`: applies vanilla/economy patches, adds the `OmicronDirector`,
    and registers the Tri-Tachyon protection manager
    plus the Black Fleet lifecycle/raid/extortion managers, `ExileRefugeeConvoyManager`, and
    `ExileRefugeeSalvageExpeditionManager`.
  - `onNewGameAfterTimePass()`: applies Exile-start relations and officers once, only when
    `ExileStartingFines` or the Nex custom start marked that start eligible.
  - `onGameLoad(...)`: reapplies load-safe fixes, restores the `OmicronDirector`, Black Fleet,
    and Refugee Convoy/Salvage Expedition managers if missing, registers the refugee integration
    listener, and runs
    `OmicronDefenseFleetManager.ensureManagers(...)` (home-defense guards, save-compatible), and
    adopts/adds `OmicronTriTachyonDefenseManager` (station-only sleepers, Culann strategic
    detachment, and mature-Nexus fallback responses).

- **Omicron Phase-1 stack (2026-08-30 scheduler revision; intact-network live test passed
  2026-09-01):** `OmicronDirector`
  (faction-wide readiness scheduler, Nexus-source selection, and capacity bookkeeper),
  `OmicronWarCapacity` (persistent per-node stats plus Lattice construction state),
  `OmicronDirectorConfig` + `data/config/exiles/director_config.json` (all tuning),
  `OmicronRaidIntel`/`OmicronAssembleStage`/`OmicronRaidActionStage` (staged route-managed
  raids/incursions: hidden launch, feasibility gate, fixed objective budget, deciv-safe disruption,
  bounty),
  `OmicronDefenseFleetManager` (per-facility defender tiers), plus
  `OmicronTriTachyonDefenseManager` / `OmicronTriTachyonResponseScript` (route-specific dormant
  station complements, Culann's persistent strategic detachment, and capacity-limited Nexus
  fallback responses), and the precompiled
  `ExileOmicronStationDefense` rule command (direct station-attack activation before vanilla's
  defender scan). Design contracts:
  `docs/war-capacity-design.md`, `docs/EXILES_OMICRON_TRITACHYON_DEFENSE.md`; facility canon:
  `docs/EXILES_OMICRON_ASSET_MODEL.md`.
  Three-grade tactical-core stack: `ExileOmicronAICoreOfficerPlugin`,
  `ExileOmicronCampaignPlugin`, and `ExileOmicronFleetOfficerGenerator`; the precompiled
  `ExileAICores` rule command supplies the 2/3/5 ordinary turn-in preview and award. Both rule
  commands ship in `jars/ExilesRuleCommands.jar`. Canonical behavior,
  drops, player use, and test checklist: `docs/EXILES_OMICRON_CORES.md`.

- **Refugee Phase-1 prototype (multi-convoy cap refactor 2026-08-23, pending retest):**
  `ExileRefugeeConvoyManager` owns a config-bounded list of weak, directly spawned civilian convoys
  and their independent travel/orbit/reception/departure/aftermath lifecycles.
  `ExileRefugeeEncampments` is the timed arrival condition. Tuning lives under `exilerefugee`
  in `data/config/exiles/director_config.json`; canon and live-test matrix:
  `docs/EXILES_REFUGEE_CONVOYS.md`.

- **Refugee Salvage Expeditions (cleanup refactor built 2026-09-07; Janino/offline checks pass, live test pending):**
  Bounded Refugee fleets appear outside player view 2–4 days after daily detection of eligible Core
  battle remains. Three global/one local default caps; one Calceus, Shepherd, Tarsus and 1–2 escorts.
  They consume temporary debris/wrecks, keep commodities/weapons only, and transfer goods exclusively
  to eligible Asharu/Mairaath markets on offscreen retirement. Commodity bonuses are bounded and expire;
  weapons use ordinary market refresh. No Nex requirement, recovered hulls or recurring AI-core sales.
  Danger checks use actual hostility toward Refugees. New campaign required from decorative version.
  Canonical behavior and live-test matrix: `docs/EXILES_REFUGEE_SALVAGE_EXPEDITIONS.md`.
- **Emergency Measures (2026-08-22, pending live test):** `ExileEmergencyMeasures` owns an invisible
  Exiles-pressure-gated 60-day lead-in and a visible capped 180-day recovery response. Narrow attack
  hooks arm it; `ExileEmergencyMeasuresPlayerActionListener` prevents player-caused crises from
  borrowing dormant eligibility; active markets count as Battered in `ExileUnrestGuard`. Canonical
  behavior and test matrix: `docs/EXILES_EMERGENCY_MEASURES.md`.

- **Ashen Crusade war director (2026-07-31): SPEC RATIFIED, NO CODE.** The Crusade's five-subsystem
  war on Tri-Tachyon (relief fleets, sabotage, interdiction raids, blockade, one-time Culann
  strike) is fully designed and awaiting implementation across eight build passes. Rulings A–K are
  settled; four open questions remain in §12 and none blocks Passes 1–5. Canonical design:
  `docs/EXILES_ASHEN_CRUSADE_WAR.md`. **Read it before writing any Ashen activity code.**

- `data/scripts/world/ExilesSectorGen.java`
  - Generates `ExileXechas`, `ExileAshenCrusade`, `ExileMidan`, and `ExileOmicron0`.
  - Applies faction relations among Exiles factions and vanilla factions.

## Generated Systems

Locations from `data/campaign/starmap.json`:

- `exilexechas`: `[-7200, -29500]`
- `exilemidan`: `[26000, 9000]`
- `exileashencrusade`: `[3500, 3500]`
- `exileomicron0`: `[-7000, 10000]`

Systems generated in Java:

- `ExileXechas.java` creates `ExileXechas`.
  - Star: `xechas`
  - Planets/entities: `thar`, `daria`, `cama`, `reyes`, `disappointment`, `xechas_relay`, `xechas_gate`, stable locations.
  - Player starting colonies are later created on `thar` and `daria` by `ExilesModPlugin`.

- `ExileMidan.java` creates `Midan`.
  - Star: `exilemidan_star`
  - Planets/entities: `exilemidan_ember`, `exilemidan_haven`, `exilemidan_winter`, `exilemidan_gas_giant`, `exilemidan_outer_world`, `exilemidan_mining_station`, relay/sensor/nav, jump points.

- `ExileAshenCrusade.java` creates `Providence`.
  - Star: `exileashen_star`
  - Planets/entities: `exileashen_penitent`, `exileashen_steadfast`, `exileashen_vigil`, `exileashen_command_bastion`, `exileashen_silent_watch`, relay/sensor/nav, jump points.

- `ExileOmicron0.java` creates `Omicron`.
  - Star: `exileomicron_star`
  - Planets/entities: `exileomicron_glass`, `exileomicron_null`, `exileomicron_leviathan`, `exileomicron_silence`, `exileomicron_nexus`, `exileomicron_damaged_nexus`, sensor, jump points.
  - Tagged as Remnant-themed/unsafe.

## Economy Data

`data/campaign/econ/economy.json` includes:

- `exilemidan.json`
- `exileashencrusade.json`

Refugee settlements use code patching in `ExilesModPlugin.applyRefugeeStationSquattersOnce()` so vanilla abandoned-station storage can be removed and existing saves can be migrated.
Each Refugee settlement has its own setup method in `ExilesModPlugin`, with independent size, conditions, and industries lists.
Refugee settlement personnel are initialized by `ExilesModPlugin.applyRefugeePersonnelOnce()`, which repairs missing or invalid comm-directory staff without changing valid existing people. Refugee stations should have a station commander, portmaster, and supply officer; Maxios should have an administrator/Elder, portmaster, and supply officer.

Midan markets:

- `exilemidan_haven`: `exilemidan`, size 5.
- `exilemidan_mining_station`: `exilemidan`, size 4.

Ashen markets:

- `exileashen_penitent`: `exileashencrusade`, size 3.
- `exileashen_steadfast`: `exileashencrusade`, size 4.
- `exileashen_command_bastion`: `exileashencrusade`, size 4.
- `exileashen_silent_watch`: `exileashencrusade`, size 4, free port.

## Vanilla Market Patches

`ExilesModPlugin` patches these vanilla markets/entities once per new game:

- `nachiketa`: size/freeport/conditions/station/fuel-production changes.
- `gilead`: size, conditions, custom farming, KoL garrison, and battlestation changes.
- `tartessus`: adds Religious Unrest and Refugee Strain.
- `asher`: adds Religious Unrest and patrol HQ; swaps orbital station to battlestation.
- `jangala`: adds Religious Unrest, Refugee Strain, and Security Centers.
- `hesperus`: replaces its Military Base with a KoL Garrison and swaps orbital station to battlestation.
- `kazeron`: gives orbital works a corrupted nanoforge.
- `station_kapteyn`: gives orbital works a corrupted nanoforge and swaps station.
- `new_maxios`: gives orbital works a corrupted nanoforge and swaps station.
- `corvus_abandoned_station`: creates/converts Asharu Refugee Platform, size 3, `exilerefugee`, removes free storage.
- `mairaath_abandoned_station1`: creates/converts Mairaath Refugee Enclave, size 3, `exilerefugee`, removes free storage.
- `yma_abandoned_station`: creates/converts Yma Refugee Enclave, size 3, `exilerefugee`, removes free storage.
- `maxios`: creates Maxios refugee settlement, size 3, `exilerefugee`.

## Custom Factions

Listed in `data/world/factions/factions.csv`:

- `exileashencrusade`: Ashen Crusade
- `exilemidan`: Midan
- `exileblackfleet`: Black Fleet (post-index split; Midan-allied pirate proxy)
- `exileomicron`: Omicron
- `exilerefugee`: refugees

Also present:

- `neutral.faction`

Note: faction files use Starsector's loose JSON style, including unquoted `id:` keys in some files.

## Custom Industries

Defined in `data/campaign/industries.csv`:

- `exile_farming_1`: Primitive Farming, `ExileFarming1`
- `exile_farming_2`: Farming, `ExileFarming1`
- `exile_gilead_farming`: Farming, `ExileGileadFarming`
- `exile_kol_garrison`: Knights of Ludd Planetary Garrison, `ExileKoLGarrison`
- `exile_algae_processing`: Domain Algae Processor, `ExileAlgaeProcessing`
- `exile_ashen_fuel_production`: Ashen Fuel Production, `ExileAshenFuelProduction`
- `exile_ashen_mining`: Ashen Mining, `ExileAshenMining`
- `exile_ashen_refining`: Ashen Refining, `ExileAshenRefining`
- `exile_ashen_light_industry`: Ashen Light Industry, `ExileAshenLightIndustry`
- `exile_ashen_heavy_industry`: Ashen Heavy Industry, `ExileAshenHeavyIndustry`
- `exile_ashen_orbitalworks`: Ashen Orbital Works, `ExileAshenHeavyIndustry`
- `exile_ashen_patrol_hq`: Ashen Patrol HQ, `ExileAshenMilitaryBase`
- `exile_ashen_military_base`: Ashen Military Base, `ExileAshenMilitaryBase`
- `exile_ashen_high_command`: Ashen High Command, `ExileAshenMilitaryBase`

## Custom Market Conditions

Defined in `data/campaign/market_conditions.csv`:

- `exile_refugee_influx`: Refugee Influx, `ExileRefugeeInflux`
- `exile_refugee_encampments`: Refugee Encampments, `ExileRefugeeEncampments`
- `exile_emergency_measures`: Emergency Measures, `ExileEmergencyMeasures`
- `exile_fecund_soil`: Fecund Soil, `ExileFecundSoil`
- `exile_aggressive_megafauna`: Aggressive Megafauna, `ExileAggressiveMegafauna`
- `exile_corrupted_soil`: Corrupted Farmland, `ExileCorruptedSoil`
- `exile_luddic_unrest`: Religious Unrest, `ExileLuddicUnrest`
- `exile_crusade_mobilization`: Crusade Mobilization, `ExileCrusadeMobilization`

## Ships And Variants

Source files include:

- Hulls: `HaT_clodhopper`, `HaT_condor2`, `Exilehound`, `shepherd_exile`.
- Skins: `wolf_exile`, `hound_exile`.
- Variants: `wolf_exile_Starting`, `shepherd_exile_Starting`, `hound_exile_Starting`, `HaT_condor2_Standard`, `HaT_clodhopper_Standard`.

`data/hulls/ship_data.csv` quick summary:

- `HaT_clodhopper`: Civilian Light Freighter, `maneuveringjets`, civilian/freighter.
- `HaT_condor2`: Light Carrier, `ammofeed`, carrier.
- `Exilehound`: Combat Freighter, `flarelauncher`.
- `shepherd_exile`: Drone Tender, `maneuveringjets`, carrier.

## New Game Start

Rules are in `data/campaign/rules.csv`.

Key custom start trigger:

- `ngcExileStart`

Starting setup includes:

- Difficulty choice rules.
- Story pages.
- Starting officers:
  - Horatio Khuttie, aggressive, Ordnance Expertise.
  - Danielle Faraday, steady, Gunnery Implants.
  - Zhang Allat, steady, Helmsmanship. Portrait path is local: `graphics/portraits/characters/zhang.png`.
- Starting player colonies:
  - `thar_market`, size 1, player-owned, Thar.
  - `daria_market`, size 1, player-owned, Daria.

## Vanilla Reference

Raw vanilla data is installed at:

- `D:/Fractal Softworks/Starsector/starsector-core/data`
- `D:/Fractal Softworks/Starsector/starsector-core/starfarer.api`
- `D:/Fractal Softworks/Starsector/starsector-core/starfarer.api.zip`

Useful vanilla data files/folders:

- `starsector-core/data/campaign/starmap.json`
- `starsector-core/data/campaign/econ`
- `starsector-core/data/world/factions`
- `starsector-core/data/hulls`
- `starsector-core/data/variants`
- `starsector-core/data/weapons`
- `starsector-core/data/scripts`
- `starsector-core/starfarer.api/com/fs/starfarer/api`

Raw vanilla data counts from the base install:

- 447 `.variant`
- 203 `.ship`
- 163 `.wpn`
- 116 `.java`
- 110 `.proj`
- 80 `.json`
- 70 `.skill`
- 66 `.skin`
- 63 `.system`
- 40 `.csv`
- 21 `.faction`

## Helpful Missing Basics

These would save future sessions time:

- A short `README.md` with the intended gameplay pitch, current scope, and known dependencies.
- A build/run note for how this IntelliJ project compiles and how Starsector loads it.
- A dependency note for external assets or assumptions.
- A "known issues / active design questions" note.
- Deeper generated cross-checks from the ID registry, if future work needs automated validation beyond the current vanilla reference table.
- A more detailed faction-relations design note, if diplomacy tuning becomes a major focus beyond the current matrix.

Current planning note:

- `docs/EXILES_KNOWN_ISSUES_AND_NEXT_STEPS.md` is now the active project-plan handoff. It includes the Refugee subsystem to-do list and tracks review findings integrated on 2026-06-13.

Existing follow-up docs:

- `docs/EXILES_FACTION_RELATIONSHIP_MATRIX.md`
- `docs/EXILES_ID_REGISTRY.md`
- `docs/EXILES_KNOWN_ISSUES_AND_NEXT_STEPS.md`
- `docs/STARSECTOR_VANILLA_REFERENCE.md`
- `docs/STARSECTOR_VANILLA_FLEET_BEHAVIOR.md` (fleet assignments, AI flags, vanilla behaviour scripts; engine-verified)
- `docs/EXILES_VANILLA_REFERENCE_TABLE.md`
