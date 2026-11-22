# Exiles ID Registry

Created: 2026-06-10
Updated: 2026-06-13

Purpose: make Exiles-owned IDs, vanilla references, overrides, and indirect list/role references visible in one place. This is a registry and design aid, not a full bug audit.

## How To Read This

Starsector data does not use one single kind of ID.

- **Exiles-defined**: the ID is created by this mod, either in data files or Java sector generation.
- **Vanilla reference**: the ID is owned by base Starsector and Exiles points at it.
- **Vanilla override/extension**: the same ID exists in vanilla, but this mod supplies a local row/file to change or extend behavior.
- **Indirect/list reference**: the string is a role key, tag, category, option, constant, or loader key that resolves through another registry. It should not automatically be treated as a direct object ID.
- **Review finding**: a registry-level mismatch or suspicious reference worth checking before related work.

## Naming Strategy Note

Exiles currently favors direct custom IDs such as `exile_ashen_mining`, `exile_crusade_mobilization`, and `exileashen_command_bastion`. Vanilla Starsector often uses shorter IDs, shared categories, tags, faction role tables, Java constants, and loader files that behave like indirection layers.

Fact-based recommendation: do not migrate the whole mod to a more vanilla-style indirection pattern just for consistency. The current direct-ID approach is workable and easy to trace. Consider vanilla-style indirection when one of these becomes true:

- Multiple factions or markets need to share the same content pool.
- Fleet composition becomes hard to manage directly.
- A custom faction needs broad import/role behavior like vanilla factions.
- The same family of IDs appears in many files and is becoming tedious or error-prone.
- Compatibility with other mods needs more generic tags or role keys.

Until then, direct IDs plus a registry is safer than a premature conversion.

## Mod Identity

| ID | Status | Defined/used in | Notes |
|---|---|---|---|
| `Exiles` | Exiles-defined mod id | `mod_info.json` | Mod id/name. |
| `data.scripts.exiles.ExilesModPlugin` | Exiles-defined plugin class | `mod_info.json`, `data/scripts/exiles/ExilesModPlugin.java` | Main mod plugin entry point. |

## Star Systems And Sector Placement

| ID/name | Status | Defined/used in | Notes |
|---|---|---|---|
| `ExileXechas` | Exiles-defined system name | `ExileXechas.java` | Created with `sector.createStarSystem("ExileXechas")`. |
| `Midan` | Exiles-defined system name | `ExileMidan.java` | Created with `sector.createStarSystem("Midan")`. |
| `Providence` | Exiles-defined system name | `ExileAshenCrusade.java` | Created with `sector.createStarSystem("Providence")`. |
| `Omicron` | Exiles-defined system name | `ExileOmicron0.java` | Created with `sector.createStarSystem("Omicron")`. |
| `exilexechas` | Placement key | `data/campaign/starmap.json` | Lowercase placement key, differs from Java system name. |
| `exilemidan` | Placement/economy key | `data/campaign/starmap.json`, `data/campaign/econ/exilemidan.json` | Differs from Java system name `Midan`. |
| `exileashencrusade` | Placement/economy key | `data/campaign/starmap.json`, `data/campaign/econ/exileashencrusade.json` | Differs from Java system name `Providence`. |
| `exileomicron0` | Placement key | `data/campaign/starmap.json` | Differs from Java system name `Omicron`. |

Review status: system naming is still not fully normalized across Java names, starmap keys, and economy `starSystem` keys. The new-game start location now uses `ExileXechas` consistently, but this should still be tested before any system-location or economy-loader work.

## Generated Entities

### Xechas

| ID | Status | Kind | Notes |
|---|---|---|---|
| `xechas` | Exiles-defined | Star/entity | Orange star in `ExileXechas.java`. |
| `thar` | Exiles-defined | Planet | Later gets player market `thar_market`. |
| `daria` | Exiles-defined | Planet | Later gets player market `daria_market`. |
| `cama` | Exiles-defined | Planet | Has `planet_cama` custom description reference. |
| `reyes` | Exiles-defined | Gas giant | Has condition market initialized in Java. |
| `disappointment` | Exiles-defined | Moon | Has condition market initialized in Java. |
| `xechas_jump` | Exiles-defined | Jump point | Inner Xechas jump point. |
| `xechas_relay` | Exiles-defined | Comm relay | Uses vanilla custom entity type `comm_relay`. |
| `xechas_gate` | Exiles-defined | Gate entity | Uses vanilla custom entity type `inactive_gate`. |
| `thar_market` | Exiles-defined | Player market | Created in `ExilesModPlugin`. |
| `daria_market` | Exiles-defined | Player market | Created in `ExilesModPlugin`. |

Review status: `planet_cama` is referenced in Java and now has a matching `CUSTOM` row in `data/strings/descriptions.csv`.

### Midan

| ID | Status | Kind | Notes |
|---|---|---|---|
| `exilemidan_star` | Exiles-defined | Star/entity | Yellow star in `ExileMidan.java`. |
| `exilemidan_ember` | Exiles-defined | Planet | Inner barren world. |
| `exilemidan_haven` | Exiles-defined | Planet/market entity | Market in `exilemidan.json`, faction `exilemidan`, size 5. |
| `exilemidan_winter` | Exiles-defined | Planet | Frozen outer world. |
| `exilemidan_gas_giant` | Exiles-defined | Gas giant | Named Moloch. |
| `exilemidan_outer_world` | Exiles-defined | Planet | Named Grave. |
| `exilemidan_mining_station` | Exiles-defined | Station/market entity | Market in `exilemidan.json`, faction `exilemidan`, size 4. |
| `midan_comm_relay` | Exiles-defined | Comm relay | Uses vanilla custom entity type `comm_relay`. |
| `midan_sensor_array` | Exiles-defined | Sensor array | Uses vanilla custom entity type `sensor_array`. |
| `midan_nav_buoy` | Exiles-defined | Nav buoy | Uses vanilla custom entity type `nav_buoy`. |
| `midan_inner_jump` | Exiles-defined | Jump point | Haven jump point. |
| `midan_outer_jump` | Exiles-defined | Jump point | Moloch jump point. |

### Providence / Ashen Crusade

| ID | Status | Kind | Notes |
|---|---|---|---|
| `exileashen_star` | Exiles-defined | Star/entity | Red giant in `ExileAshenCrusade.java`. |
| `exileashen_penitent` | Exiles-defined | Planet/market entity | Market in `exileashencrusade.json`, size 3. |
| `exileashen_steadfast` | Exiles-defined | Planet/market entity | Market in `exileashencrusade.json`, size 4. |
| `exileashen_vigil` | Exiles-defined | Planet | Frozen outer world. |
| `exileashen_command_bastion` | Exiles-defined | Station/market entity | Market in `exileashencrusade.json`, size 4. |
| `exileashen_silent_watch` | Exiles-defined | Station/market entity | Market in `exileashencrusade.json`, size 4, free port. |
| `exileashen_comm_relay` | Exiles-defined | Comm relay | Uses vanilla custom entity type `comm_relay_makeshift`. |
| `exileashen_sensor_array` | Exiles-defined | Sensor array | Uses vanilla custom entity type `sensor_array`. |
| `exileashen_nav_buoy` | Exiles-defined | Nav buoy | Uses vanilla custom entity type `nav_buoy_makeshift`. |
| `exileashen_inner_jump` | Exiles-defined | Jump point | Penitent jump point. |
| `exileashen_outer_jump` | Exiles-defined | Jump point | Silent Watch jump point. |

### Omicron

| ID | Status | Kind | Notes |
|---|---|---|---|
| `exileomicron_star` | Exiles-defined | Star/entity | Blue giant in `ExileOmicron0.java`. |
| `exileomicron_glass` | Exiles-defined | Planet | Inner irradiated rock. |
| `exileomicron_null` | Exiles-defined | Planet | Barren observation world. |
| `exileomicron_leviathan` | Exiles-defined | Gas giant | Outer visual anchor. |
| `exileomicron_silence` | Exiles-defined | Planet | Frozen outer world. |
| `exileomicron_nexus` | Exiles-defined | Station/entity | Uses vanilla custom entity type `station_mining00` for now. |
| `exileomicron_damaged_nexus` | Exiles-defined | Station/entity | Uses vanilla custom entity type `station_mining00` for now. |
| `exileomicron_sensor_array` | Exiles-defined | Sensor array | Uses vanilla custom entity type `sensor_array`. |
| `exileomicron_inner_jump` | Exiles-defined | Jump point | Nexus jump point. |
| `exileomicron_outer_jump` | Exiles-defined | Jump point | Leviathan jump point. |

## Factions

| ID | Status | Display name | Defined/used in |
|---|---|---|---|
| `exileashencrusade` | Exiles-defined | Ashen Crusade | `data/world/factions/exileashencrusade.faction` |
| `exilemidan` | Exiles-defined | Midan | `data/world/factions/exilemidan.faction` |
| `exileblackfleet` | Exiles-defined | Black Fleet | `data/world/factions/exileblackfleet.faction`; added by the post-registry Midan/Black Fleet split |
| `exileomicron` | Exiles-defined | Omicron | `data/world/factions/exileomicron.faction` |
| `exilerefugee` | Exiles-defined | refugees | `data/world/factions/exilerefugee.faction` |

Faction list file:

- `data/world/factions/factions.csv`

Vanilla faction references through `Factions.*` constants:

- `player`, `hegemony`, `independent`, `knights_of_ludd`, `lions_guard`, `luddic_church`, `luddic_path`, `mercenary`, `persean`, `pirates`, `remnant`, `scavengers`, `sindrian_diktat`, `threat`, `dweller`, `tritachyon`.

These are vanilla references, not Exiles-defined IDs.

## Economy Markets

| Entity/market anchor | Status | Faction | Size | Source |
|---|---|---|---|---|
| `exilemidan_haven` | Exiles-defined | `exilemidan` | 5 | `data/campaign/econ/exilemidan.json` |
| `exilemidan_mining_station` | Exiles-defined | `exilemidan` | 4 | `data/campaign/econ/exilemidan.json` |
| `exileashen_penitent` | Exiles-defined | `exileashencrusade` | 3 | `data/campaign/econ/exileashencrusade.json` |
| `exileashen_steadfast` | Exiles-defined | `exileashencrusade` | 4 | `data/campaign/econ/exileashencrusade.json` |
| `exileashen_command_bastion` | Exiles-defined | `exileashencrusade` | 4 | `data/campaign/econ/exileashencrusade.json` |
| `exileashen_silent_watch` | Exiles-defined | `exileashencrusade` | 4 | `data/campaign/econ/exileashencrusade.json` |
| `corvus_abandoned_station` | Vanilla reference patched by Exiles | `exilerefugee` | 3 | Asharu Refugee Platform; `ExilesModPlugin` |
| `mairaath_abandoned_station1` | Vanilla reference patched by Exiles | `exilerefugee` | 3 | Mairaath Refugee Enclave; `ExilesModPlugin` |
| `yma_abandoned_station` | Vanilla reference patched by Exiles | `exilerefugee` | 3 | Yma Refugee Enclave; `ExilesModPlugin` |
| `maxios` | Vanilla reference patched by Exiles | `exilerefugee` | 3 | Maxios refugee settlement; `ExilesModPlugin` |
| `thar_market` | Exiles-defined player market | `player` | 1 | Created in `ExilesModPlugin` |
| `daria_market` | Exiles-defined player market | `player` | 1 | Created in `ExilesModPlugin` |

Review status: refugee settlements rely primarily on Java patching in `ExilesModPlugin`, not on mod economy JSON loading.

## Vanilla Market/Entity Patch Targets

These are vanilla references confirmed in base economy files:

| ID | Vanilla system/economy file | Exiles behavior |
|---|---|---|
| `gilead` | `canaan.json` | Size/conditions/industries/station changes. |
| `asher` | `canaan.json` | Adds patrol HQ, swaps station. |
| `station_kapteyn` | `isirah.json` | Adds corrupted nanoforge, swaps station. |
| `new_maxios` | `magec.json` | Adds corrupted nanoforge, swaps station. |
| `nachiketa` | `naraka.json` | Size/freeport/conditions/station/fuel changes. |
| `kazeron` | `thule.json` | Adds corrupted nanoforge. |
| `corvus_abandoned_station` | Vanilla entity patched by Exiles | Refugee size-3 station market, storage removed. |
| `mairaath_abandoned_station1` | Vanilla entity patched by Exiles | Refugee size-3 station market, storage removed. |
| `yma_abandoned_station` | Vanilla entity patched by Exiles | Refugee size-3 station market, storage removed. |
| `maxios` | Vanilla entity patched by Exiles | Refugee size-3 planet market. |

## Industries

### Exiles-Defined Industries

| ID | Name | Plugin |
|---|---|---|
| `exile_farming_1` | Primitive Farming | `data.scripts.industries.ExileFarming1` |
| `exile_farming_2` | Farming | `data.scripts.industries.ExileFarming1` |
| `exile_gilead_farming` | Farming | `data.scripts.industries.ExileGileadFarming` |
| `exile_kol_garrison` | Knights of Ludd Planetary Garrison | `data.scripts.industries.ExileKoLGarrison` |
| `exile_algae_processing` | Domain Algae Processor | `data.scripts.industries.ExileAlgaeProcessing` |
| `exile_ashen_fuel_production` | Ashen Fuel Production | `data.scripts.industries.ExileAshenFuelProduction` |
| `exile_ashen_mining` | Ashen Mining | `data.scripts.industries.ExileAshenMining` |
| `exile_ashen_refining` | Ashen Refining | `data.scripts.industries.ExileAshenRefining` |
| `exile_ashen_light_industry` | Ashen Light Industry | `data.scripts.industries.ExileAshenLightIndustry` |
| `exile_ashen_heavy_industry` | Ashen Heavy Industry | `data.scripts.industries.ExileAshenHeavyIndustry` |
| `exile_ashen_orbitalworks` | Ashen Orbital Works | `data.scripts.industries.ExileAshenHeavyIndustry` |
| `exile_ashen_patrol_hq` | Ashen Patrol HQ | `data.scripts.industries.ExileAshenMilitaryBase` |
| `exile_ashen_military_base` | Ashen Military Base | `data.scripts.industries.ExileAshenMilitaryBase` |
| `exile_ashen_high_command` | Ashen High Command | `data.scripts.industries.ExileAshenMilitaryBase` |

Upgrade/downgrade links:

- `exile_farming_1` upgrades to `exile_farming_2`.
- `exile_farming_2` downgrades to `exile_farming_1`.
- `exile_ashen_heavy_industry` upgrades to `exile_ashen_orbitalworks`.
- `exile_ashen_orbitalworks` downgrades to `exile_ashen_heavy_industry`.
- `exile_ashen_patrol_hq` upgrades to `exile_ashen_military_base`.
- `exile_ashen_military_base` downgrades to `exile_ashen_patrol_hq` and upgrades to `exile_ashen_high_command`.
- `exile_ashen_high_command` downgrades to `exile_ashen_military_base`.

### Vanilla Industry References

Confirmed in vanilla `data/campaign/industries.csv`:

- `population`
- `spaceport`
- `farming`
- `lightindustry`
- `heavybatteries`
- `orbitalstation`
- `militarybase`
- `mining`
- `refining`
- `orbitalworks`
- `grounddefenses`
- `battlestation`
- `patrolhq`
- `techmining`
- `fuelprod`
- `battlestation_mid`
- `starfortress`

These are vanilla references unless an Exiles row with the same ID exists.

## Market Conditions

### Exiles-Defined Conditions

| ID | Name | Plugin |
|---|---|---|
| `exile_refugee_influx` | Refugee Influx | `data.scripts.marketconditions.ExileRefugeeInflux` |
| `exile_refugee_encampments` | Refugee Encampments | `data.scripts.marketconditions.ExileRefugeeEncampments` |
| `exile_fecund_soil` | Fecund Soil | `data.scripts.marketconditions.ExileFecundSoil` |
| `exile_aggressive_megafauna` | Aggressive Megafauna | `data.scripts.marketconditions.ExileAggressiveMegafauna` |
| `exile_corrupted_soil` | Corrupted Farmland | `data.scripts.marketconditions.ExileCorruptedSoil` |
| `exile_luddic_unrest` | Religious Unrest | `data.scripts.marketconditions.ExileLuddicUnrest` |
| `exile_crusade_mobilization` | Crusade Mobilization | `data.scripts.marketconditions.ExileCrusadeMobilization` |

Procgen rows:

- `exile_aggressive_megafauna`
- `exile_crusade_mobilization`

### Vanilla Condition References

Confirmed in vanilla `data/campaign/market_conditions.csv`:

- `population_1`, `population_3`, `population_4`, `population_5`, `population_6`, `population_7`
- `habitable`
- `decivilized_subpop`
- `irradiated`
- `pollution`
- `mild_climate`
- `no_atmosphere`
- `very_hot`
- `toxic_atmosphere`
- `poor_light`
- `farmland_rich`
- `ore_sparse`, `ore_moderate`, `ore_abundant`, `ore_rich`
- `rare_ore_sparse`, `rare_ore_rich`
- `volatiles_diffuse`, `volatiles_abundant`
- `hot`
- `organics_trace`, `organics_abundant`
- `ruins_scattered`, `ruins_widespread`, `ruins_extensive`
- `inimical_biosphere`
- `high_gravity`

## Special Items

| ID | Status | Notes |
|---|---|---|
| `corrupted_nanoforge` | Vanilla override/extension | Exists in vanilla and has a local Exiles row. Exiles uses it with custom heavy industry/orbital works and applies it to vanilla markets. |
| `pristine_nanoforge` | Vanilla override/extension | Exists in vanilla and has a local Exiles row. Exiles economy data uses it with `orbitalworks` on `exilemidan_mining_station`. |

Review finding: these IDs are not Exiles-namespaced. That may be intentional to extend vanilla nanoforge behavior, but future edits should treat them as vanilla IDs being overridden/extended, not new Exiles IDs.

## Ships, Skins, Variants, Roles

### Hull IDs

| ID | Status | Source | Notes |
|---|---|---|---|
| `HaT_clodhopper` | Exiles-defined hull | `ship_data.csv`, `HaT_clodhopper.ship` | Description row exists. |
| `exile_tarsus_2` | Exiles-defined hull | `ship_data.csv`, `exile_tarsus_2.ship` | Calceus salvage Tarsus; renamed from `tarsus_2_exile` 2026-09-07; built-in `repair_gantry`. |
| `HaT_condor2` | Exiles-defined hull | `ship_data.csv`, `HaT_condor2.ship` | Description row exists. |
| `Exilehound` | Exiles-defined hull | `ship_data.csv`, `exilehound.ship` | Base hull for `hound_exile` skin. Capitalization is unusual. |
| `shepherd_exile` | Exiles-defined hull | `ship_data.csv`, `exileshepherd.ship` | Direct Exiles hull. |

### Skin Hull IDs

| ID | Status | Base hull | Source |
|---|---|---|---|
| `hound_exile` | Exiles-defined skin hull | `Exilehound` | `data/hulls/skins/hound_exile.skin` |
| `wolf_exile` | Exiles-defined skin hull | `wolf` | `data/hulls/skins/wolf_exile.skin`; `wolf` is vanilla. |

### Variant IDs

| ID | Status | Hull ID | Source |
|---|---|---|---|
| `HaT_clodhopper_Standard` | Exiles-defined variant | `HaT_clodhopper` | `data/variants/HaT_clodhopper_Standard.variant` |
| `exile_tarsus_2_Standard` | Exiles-defined variant | `exile_tarsus_2` | Guaranteed exactly once per Refugee salvage expedition. |
| `HaT_condor2_Standard` | Exiles-defined variant | `HaT_condor2` | `data/variants/HaT_condor2_Standard.variant` |
| `hound_exile_Starting` | Exiles-defined variant | `hound_exile` | `data/variants/hound_exile_Starting.variant` |
| `shepherd_exile_Starting` | Exiles-defined variant | `shepherd_exile` | `data/variants/shepherd_exile_Starting.variant` |
| `wolf_exile_Starting` | Exiles-defined variant | `wolf_exile` | `data/variants/wolf_exile_Starting.variant` |

Vanilla hull references:

- `wolf`: base hull for `wolf_exile`.
- `hound`: not directly used as a base hull; Exiles defines `Exilehound` instead.
- `shepherd`: vanilla hull exists, but Exiles also defines `shepherd_exile`.

Review finding: the Hound chain is intentionally or accidentally split across `Exilehound` and `hound_exile`. This can work because `hound_exile` is a skin hull, but future ship work should preserve capitalization exactly.

### Faction Role Keys

`data/world/factions/default_ship_roles.json` uses:

- `civilianRandom`
- `carrierSmall`
- `freighterSmall`

These are indirect/list references. The role keys are not ship IDs; they map role buckets to variant IDs.

Review status: `default_ship_roles.json` is structurally balanced as of 2026-06-13. The role keys remain indirect/list references, not ship IDs.

## New Game Start And Rules

### Rule IDs And Options

| ID/option | Status | Kind |
|---|---|---|
| `ngcExileStart` | Exiles-defined | Rule row id |
| `ngcExile` | Exiles-defined | New-game option id |
| `ngcExilePicked` | Exiles-defined | Rule row id |
| `ngcExileDifficulty` | Exiles-defined | Rule row id |
| `NGCExileDifficulty` | Exiles-defined | Rule trigger fired by `FireAll` |
| `ngcExileEasy` | Exiles-defined | New-game option id |
| `ngcExileNormal` | Exiles-defined | New-game option id |
| `ngcExileEasyPicked` | Exiles-defined | Rule row id |
| `ngcExileNormalPicked` | Exiles-defined | Rule row id |
| `ngcExileStory1` | Exiles-defined | Rule row/trigger/option id |
| `ngcExileStory2` | Exiles-defined | Rule row/trigger/option id |
| `ngcExileStory3` | Exiles-defined | Rule row/trigger/option id |
| `ngcExileStory4` | Exiles-defined | Rule row/trigger/option id |
| `ngcExileStory5` | Exiles-defined | Rule row/trigger/option id |
| `ngcExileStartEnd` | Exiles-defined | Rule row/option id |

### Rule-Referenced Variant IDs

- `wolf_exile_Starting`
- `hound_exile_Starting`

### New-Game Start Location Strings

- `ExileXechas`

Review status: `rules.csv` now uses `ExileXechas` consistently for `NGCSetStartingLocation`, while `starmap.json` still uses lowercase placement key `exilexechas`. This should still be verified in an in-game custom-start smoke test.

### Nexerelin-Like Config Path

- `data/config/exerelineactioneonfig/player.json`
- Contains `startShipsSolo` with `wolf_exile_Starting` and `hound_exile_Starting`.

Review finding: the folder name `exerelineactioneonfig` looks unusual. Verify whether this is intentional and whether any external mod actually reads this path.

## Descriptions

| ID | Status | Type | Source |
|---|---|---|---|
| `HaT_clodhopper` | Exiles-defined/reference to Exiles hull | `SHIP` | `data/strings/descriptions.csv` |
| `HaT_condor2` | Exiles-defined/reference to Exiles hull | `SHIP` | `data/strings/descriptions.csv` |
| `planet_thar` | Exiles-defined description id | `CUSTOM` | Used by `thar`. |
| `planet_daria` | Exiles-defined description id | `CUSTOM` | Used by `daria`. |
| `planet_cama` | Exiles-defined description id | `CUSTOM` | Used by `cama`. |

Review status: `planet_cama` is covered.

## Persistent Memory Keys

Defined in `ExilesModPlugin.java`:

- `$exiles_start_setup_done`
- `$exiles_nachiketa_patch_done`
- `$exiles_gilead_patch_done`
- `$exiles_asher_patch_done`
- `$exiles_kazeron_patch_done`
- `$exiles_kapteyn_patch_done`
- `$exiles_maxios_patch_done`
- `$exiles_xechas_colonies_done`
- `$exiles_refugee_stations_done_v2`
- `$exiles_refugee_personnel_done_v3`

These are Exiles-defined save/persistent-data keys.

### Addendum 2026-07-23 — Refugee convoy prototype

- Persistent script: `data.scripts.exiles.ExileRefugeeConvoyManager`.
- Fleet flag: `$exiles_refugee_convoy`.
- Timed market-memory cooldowns: `$exiles_refugee_source_cooldown`,
  `$exiles_refugee_destination_cooldown`.
- Condition: `exile_refugee_encampments`.
- Configuration: `data/config/exiles/director_config.json` → `exilerefugee`.

### Addendum 2026-07-05 — Omicron Director stack (raid system 1.0 + home defense)

The sections above predate the Phase-1 Omicron work. New identifiers since; the
**authoritative registry for war-capacity stats/modifiers/keys is
`docs/war-capacity-design.md` §6** — do not invent IDs not registered there.

**Classes (`data/scripts/exiles/`):** `OmicronDirector` (launcher + capacity bookkeeper,
persistent script), `OmicronDirectorConfig` (transient, loads
`data/config/exiles/director_config.json`), `OmicronWarCapacity` (persistent per-node
MutableStats), `OmicronRaidIntel` / `OmicronAssembleStage` / `OmicronRaidActionStage`
(staged route-managed ops), `OmicronDefenseFleetManager` (per-facility home-defense
guards, SourceBasedFleetManager subclass).

**Entity/fleet tags:** `exile_omicron_nexus` (Nexus station), `exile_omicron_asset_ucnexus`
/ `exile_omicron_asset_mining` / `exile_omicron_asset_fuel` (facility stations),
`exile_omicron_raider` (op fleets; defenders deliberately do NOT carry it).

**Persistent/memory keys (summary; details in war-capacity-design.md §6):**
`$exiles_omicron_war_capacity`, `$exiles_omicron_asset_<nodeId>_<key>_destroyed`,
`$exiles_omicron_target_factions`, `$exiles_omicron_raids_enabled`,
`$exiles_omicron_incursions_enabled`, `$exiles_omicron_start_ts`,
`$exiles_omicron_early_warning_days`; per-market `$core_disrupted_<ClassName>` entries are
vanilla disruption state written via `Industry.setDisrupted`.

**Config:** `data/config/exiles/director_config.json` — per-faction blocks
(`exileomicron`: nodes, raid/incursion cadence+FP, operations incl. `collateralChance` /
`collateralExempt` / `feasibilityMult`, disruption, `defense` tiers, escalation, assets,
thresholds).

### Addendum 2026-07-19; expanded 2026-07-22 - Tri-Tachyon protection mandate

**Classes:** `OmicronTriTachyonDefenseManager` (persistent protected-market manager),
`OmicronTriTachyonResponseScript` (one Nexus-launched response fleet lifecycle), and the
precompiled `ExileOmicronStationDefense` rule command (direct `mktEngage` activation).

**Fleet tags:** `exiles_omicron_tt_local_defender` (dormant station complement),
`exiles_omicron_tt_strategic_reserve` (Culann's persistent large detachment), and
`exiles_omicron_tt_response` (long-range Nexus reinforcement).

**Fleet/market memory:** `$exiles_omicron_tt_market`, `$exiles_omicron_tt_node`,
`$exiles_omicron_tt_active`, `$exiles_omicron_tt_threat_faction`,
`$exiles_omicron_tt_defense_alert`, `$exiles_omicron_tt_alert_faction`,
`$exiles_omicron_tt_replacement_cooldown`, `$exiles_omicron_tt_reserve_alert`,
`$exiles_omicron_tt_reserve_faction`, `$exiles_omicron_tt_reserve_route`,
`$exiles_omicron_tt_reserve_ready`, `$exiles_omicron_tt_reserve_threshold`, and
`$exiles_omicron_tt_reserve_cooldown`.

**Config:** `exileomicron.triTachyonDefense` in `director_config.json`. Canonical behavior and
test matrix: `EXILES_OMICRON_TRITACHYON_DEFENSE.md`.

### Addendum 2026-07-12 — Omicron tactical cores

| ID | Type | Meaning |
|---|---|---|
| `exiles_omicron_core_1` | Commodity / AI officer ID | Grade I tactical core |
| `exiles_omicron_core_2` | Commodity / AI officer ID | Grade II tactical core |
| `exiles_omicron_core_3` | Commodity / AI officer ID | Grade III tactical core |
| `exiles_omicron_core_campaign_plugin` | Campaign plugin ID | Transient custom-core officer picker |

Canonical behavior and implementation map: `EXILES_OMICRON_CORES.md`.

## Graphics And Asset Paths

Exiles-owned asset paths include:

- `graphics/icons/industry/exilealgaeprocessing.png`
- `graphics/icons/industry/exileashenfuelproduction.png`
- `graphics/icons/industry/exileashenheavyindustry.png`
- `graphics/icons/industry/exileashenhigh_command.png`
- `graphics/icons/industry/exileashenlightindustry.png`
- `graphics/icons/industry/exileashenmilitary_base.png`
- `graphics/icons/industry/exileashenmining.png`
- `graphics/icons/industry/exileashenorbitalworks.png`
- `graphics/icons/industry/exileashenpatrol_hq.png`
- `graphics/icons/industry/exileashenrefining.png`
- `graphics/icons/industry/exilefarming.png`
- `graphics/icons/industry/exilefarming2.png`
- `graphics/icons/industry/exilegileadfarming.png`
- `graphics/icons/industry/exilekolgarrison.png`
- `graphics/icons/markets/corrupt_soil.png`
- `graphics/icons/markets/crusade_mobilization.png`
- `graphics/icons/markets/fecund_soil.png`
- `graphics/icons/markets/hostile_fauna.png`
- `graphics/icons/markets/luddic_unrest.png`
- `graphics/icons/markets/refugees.png`
- `graphics/portraits/characters/faraday.png`
- `graphics/portraits/characters/khuttie.png`
- `graphics/portraits/characters/zhang.png`
- `graphics/ships/clodhopper/clodhopper.png`
- `graphics/ships/condor2/condor2_base.png`
- `graphics/ships/hound_exile/hound_exile.png`
- `graphics/ships/shepherd_exile/shepherd_exile.png`
- `graphics/ships/wolf_exile/wolf_exile_Starting.png`

Vanilla asset references include backgrounds, illustrations, industry sprites, planet sprites/glows, custom entity types, and ship-system IDs.

Note: officer Zhang Allat currently uses `graphics/portraits/characters/zhang.png`, copied into Exiles from Faces in the Void and renamed to avoid a cross-mod dependency or path overlap. If Zhang stays, this may later be replaced with a new portrait.

## Indirect/List/Tag References To Keep Separate

These are examples of strings that are not necessarily direct object ownership:

- Faction ship role keys: `civilianRandom`, `carrierSmall`, `freighterSmall`.
- Faction blueprint/tag pools: `base_bp`, `ind`, `lowtech_bp`, `midline_bp`, `hightech_bp`, `missile_bp`, `remnant`.
- Industry tags: `farming`, `industry`, `rural`, `military`, `structure`, `codex_unlockable`, `uses_blueprints`, `patrol`, `command`.
- Condition procgen groups/categories: `animals`, `special`, `cat_*`, planet category columns.
- Vanilla Java constants: `Factions.*`, `Industries.*`, `Conditions.*`, `Skills.*`, `Ranks.*`, `Submarkets.*`, `Tags.*`.
- Rule commands: `NGCAddShip`, `NGCAddCargo`, `NGCSetStartingLocation`, `NGCDone`, `FireAll`.

When refactoring, decide whether a string is a direct ID, a key into a table, or a tag consumed by game logic before renaming it.
