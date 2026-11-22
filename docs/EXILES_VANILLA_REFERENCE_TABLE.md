# Exiles Vanilla Reference Table

Created: 2026-06-10

Purpose: compare Exiles IDs and content patterns against vanilla Starsector. This is for tuning and refactoring decisions, especially where Exiles uses direct IDs while vanilla often uses tags, roles, constants, and loader tables.

Vanilla source checked: `D:/Fractal Softworks/Starsector/starsector-core/data`

## Collision Summary

| Category | Exiles count | Vanilla ID collisions | Notes |
|---|---:|---:|---|
| Industries | 14 | 0 | All Exiles custom industries are namespaced. |
| Market conditions | 6 | 0 | All Exiles custom conditions are namespaced. |
| Special items | 2 | 2 | `corrupted_nanoforge` and `pristine_nanoforge` are vanilla IDs locally overridden/extended. |
| Hulls | 4 | 0 | No direct hull ID collisions. |
| Hull skins | 2 | 0 | No direct skin ID collisions. |
| Variants | 5 | 0 | No direct variant ID collisions. |

## Industries

| Exiles ID | Exiles name | Closest vanilla analog | Notes |
|---|---|---|---|
| `exile_farming_1` | Primitive Farming | `farming` | Custom lower-tier farming; shares `farming` tags. |
| `exile_farming_2` | Farming | `farming` | Custom farming using Exiles plugin. |
| `exile_gilead_farming` | Farming | `farming` | Gilead-specific farming replacement. |
| `exile_kol_garrison` | Knights of Ludd Planetary Garrison | `grounddefenses`, `patrolhq`, `militarybase` | Custom military/defense hybrid. |
| `exile_algae_processing` | Domain Algae Processor | No exact vanilla analog | Industrial custom production concept. |
| `exile_ashen_fuel_production` | Ashen Fuel Production | `fuelprod` | Custom fuel production. |
| `exile_ashen_mining` | Ashen Mining | `mining` | Custom mining. |
| `exile_ashen_refining` | Ashen Refining | `refining` | Custom refining. |
| `exile_ashen_light_industry` | Ashen Light Industry | `lightindustry` | Custom light industry. |
| `exile_ashen_heavy_industry` | Ashen Heavy Industry | `heavyindustry` | Custom heavy industry. |
| `exile_ashen_orbitalworks` | Ashen Orbital Works | `orbitalworks` | Custom orbital works using the heavy industry plugin class. |
| `exile_ashen_patrol_hq` | Ashen Patrol HQ | `patrolhq` | Custom military-base style patrol source. |
| `exile_ashen_military_base` | Ashen Military Base | `militarybase` | Custom military-base style patrol source. |
| `exile_ashen_high_command` | Ashen High Command | `highcommand` | Custom high-command style patrol source. |

Vanilla analog tags worth noting:

- Vanilla `farming`: `farming, industry, rural`.
- Vanilla `mining`, `refining`, `fuelprod`, `heavyindustry`, and `orbitalworks`: `industry, industrial` families.
- Vanilla `patrolhq`, `militarybase`, and `highcommand` share military structure behavior through `MilitaryBase`.
- Exiles mirrors many vanilla tags while keeping separate IDs and custom plugin classes.

## Market Conditions

| Exiles ID | Exiles name | Closest vanilla analog | Notes |
|---|---|---|---|
| `exile_refugee_influx` | Refugee Influx | `decivilized_subpop` only thematically adjacent | No direct vanilla condition match. |
| `exile_fecund_soil` | Fecund Soil | `farmland_rich`, `habitable` | Positive agriculture concept. |
| `exile_aggressive_megafauna` | Aggressive Megafauna | `inimical_biosphere` | Hostile biosphere concept. |
| `exile_corrupted_soil` | Corrupted Farmland | `pollution`, negative farmland states | Custom Gilead degradation. |
| `exile_luddic_unrest` | Religious Unrest | No exact vanilla analog | Political/religious instability concept. |
| `exile_crusade_mobilization` | Crusade Mobilization | No exact vanilla analog | Economy-wide war mobilization concept. |

Procgen note:

- `data/campaign/procgen/condition_gen_data.csv` includes `exile_aggressive_megafauna` and `exile_crusade_mobilization`.
- Other Exiles conditions are manually placed or script-driven unless added elsewhere.

## Special Items

| ID | Vanilla status | Exiles status | Notes |
|---|---|---|---|
| `corrupted_nanoforge` | Vanilla ID | Local Exiles row | Exiles extends allowed industries to include custom Ashen heavy industry/orbital works. |
| `pristine_nanoforge` | Vanilla ID | Local Exiles row | Exiles extends allowed industries to include custom Ashen heavy industry/orbital works. |

Design implication: these are deliberate vanilla overrides/extensions, not custom namespaced Exiles items. Keep this in mind when changing item behavior, balance, or compatibility.

## Ships, Hulls, Skins, Variants

### Hulls

| Exiles ID | Name | Closest vanilla analog | Notes |
|---|---|---|---|
| `HaT_clodhopper` | Clodhopper | `mudskipper`, civilian freighters | Custom low-tech civilian freighter. |
| `HaT_condor2` | Condor-II | `condor`, `tarsus` | Custom carrier-converted freighter concept. |
| `Exilehound` | Hound | `hound` | Custom/base hull for Exiles skin chain; capitalization matters. |
| `shepherd_exile` | Shepherd | `shepherd` | Custom direct hull using Shepherd concept. |

### Skins

| Exiles skin ID | Base hull | Vanilla collision | Notes |
|---|---|---:|---|
| `hound_exile` | `Exilehound` | No | Exiles skin over Exiles-defined base hull. |
| `wolf_exile` | `wolf` | No | Exiles skin over vanilla Wolf hull. |

### Variants

| Exiles variant ID | Hull ID | Vanilla collision |
|---|---|---:|
| `HaT_clodhopper_Standard` | `HaT_clodhopper` | No |
| `HaT_condor2_Standard` | `HaT_condor2` | No |
| `hound_exile_Starting` | `hound_exile` | No |
| `shepherd_exile_Starting` | `shepherd_exile` | No |
| `wolf_exile_Starting` | `wolf_exile` | No |

## Factions

| Exiles ID | Display name | Vanilla collision | Notes |
|---|---|---:|---|
| `exileashencrusade` | Ashen Crusade | No | Custom faction. |
| `exilemidan` | Black Fleet | No | Custom faction. |
| `exileomicron` | Omicron | No | Custom faction, Remnant-themed behavior. |
| `exilerefugee` | refugees | No | Custom faction. |

Vanilla faction constants used in relations include:

- `hegemony`
- `independent`
- `knights_of_ludd`
- `lions_guard`
- `luddic_church`
- `luddic_path`
- `mercenary`
- `persean`
- `pirates`
- `player`
- `remnant`
- `scavengers`
- `sindrian_diktat`
- `threat`
- `dweller`
- `tritachyon`

## Economy And Market Patches

| Exiles use | Vanilla target | Vanilla system/economy file | Notes |
|---|---|---|---|
| Gilead patch | `gilead` | `canaan.json` | Size, conditions, industries, station changes. |
| Asher patch | `asher` | `canaan.json` | Adds patrol HQ, swaps station. |
| Kapteyn patch | `station_kapteyn` | `isirah.json` | Adds corrupted nanoforge, swaps station. |
| Maxios patch | `new_maxios` | `magec.json` | Adds corrupted nanoforge, swaps station. |
| Nachiketa patch | `nachiketa` | `naraka.json` | Size/freeport/conditions/station/fuel changes. |
| Kazeron patch | `kazeron` | `thule.json` | Adds corrupted nanoforge. |
| Refugee settlement patch | `corvus_abandoned_station` | Vanilla entity reference | Creates/converts Asharu Refugee Platform, size 3, removes storage. |
| Refugee settlement patch | `mairaath_abandoned_station1` | Vanilla entity reference | Creates/converts Mairaath Refugee Enclave, size 3, removes storage. |
| Refugee settlement patch | `yma_abandoned_station` | Vanilla entity reference | Creates/converts Yma Refugee Enclave, size 3, removes storage. |
| Refugee settlement patch | `maxios` | Vanilla entity reference | Creates Maxios refugee settlement, size 3. |

## Direct ID Versus Vanilla-Style Indirection

Exiles mostly uses direct IDs:

- `exile_ashen_mining`
- `exile_crusade_mobilization`
- `exileashen_command_bastion`
- `hound_exile_Starting`

Vanilla often relies on indirection:

- Faction ship role keys such as `carrierSmall` and `freighterSmall`.
- Tags such as `base_bp`, `ind`, `military`, `industry`, and `codex_unlockable`.
- Java constants such as `Factions.*`, `Industries.*`, and `Conditions.*`.
- Economy loader entity lists.
- CSV row IDs that are reused by plugins and scripts.

Practical tuning recommendation:

- Keep direct IDs when content is specific to one faction, one system, or one start.
- Use vanilla-style roles/tags when content needs to be shared, imported, randomized, or balanced across multiple factions.
- Treat special item overrides with extra care because those IDs are vanilla-owned.
