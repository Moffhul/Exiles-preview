# Exiles Faction Relationship Matrix

Created: 2026-06-10

> **STALE PRE-SPLIT MATRIX (2026-07-12 warning):** the tables below predate creation of
> `exileblackfleet` and do not fully match the live relation script. Do not use them for edits until
> the planned generated refresh. Current load-bearing post-split invariant: Midan and Black Fleet are
> mutually **FRIENDLY** in `ExilesSectorGen`, with reciprocal Nex FRIENDLY floors/exceptions; both remain
> hostile to the established Core powers. `CLAUDE.md` is authoritative meanwhile.

Source: `data/scripts/world/ExilesSectorGen.java`

Purpose: tuning reference for faction diplomacy. Relations are directional because Exiles sometimes sets different values in each direction.

## Legend

- `COOPERATIVE`
- `FRIENDLY`
- `NEUTRAL`
- `SUSPICIOUS`
- `INHOSPITABLE`
- `HOSTILE`
- `VENGEFUL`

## Exiles Factions

| Faction ID | Display name |
|---|---|
| `exilemidan` | Black Fleet |
| `exileashencrusade` | Ashen Crusade |
| `exileomicron` | Omicron |
| `exilerefugee` | refugees |

## Internal Exiles Relations

Rows are source factions. Columns are target factions.

| Source -> Target | Black Fleet | Ashen Crusade | Omicron | Refugees |
|---|---:|---:|---:|---:|
| Black Fleet | - | HOSTILE | HOSTILE | INHOSPITABLE |
| Ashen Crusade | HOSTILE | - | VENGEFUL | INHOSPITABLE |
| Omicron | HOSTILE | HOSTILE | - | INHOSPITABLE |
| Refugees | INHOSPITABLE | NEUTRAL | HOSTILE | - |

## Exiles Toward Vanilla And Player

Rows are vanilla/player targets. Columns are Exiles source factions.

| Target | Black Fleet | Ashen Crusade | Omicron | Refugees |
|---|---:|---:|---:|---:|
| Player | INHOSPITABLE | SUSPICIOUS | FRIENDLY | INHOSPITABLE |
| Hegemony | VENGEFUL | NEUTRAL | HOSTILE | FRIENDLY |
| Independents | HOSTILE | INHOSPITABLE | INHOSPITABLE | FRIENDLY |
| Knights of Ludd | HOSTILE | NEUTRAL | VENGEFUL | NEUTRAL |
| Lion's Guard | HOSTILE | HOSTILE | INHOSPITABLE | NEUTRAL |
| Luddic Church | HOSTILE | FRIENDLY | VENGEFUL | FRIENDLY |
| Luddic Path | VENGEFUL | COOPERATIVE | VENGEFUL | NEUTRAL |
| Mercenaries | HOSTILE | NEUTRAL | HOSTILE | NEUTRAL |
| Persean League | VENGEFUL | HOSTILE | INHOSPITABLE | FRIENDLY |
| Pirates | HOSTILE | HOSTILE | HOSTILE | HOSTILE |
| Remnants | HOSTILE | HOSTILE | FRIENDLY | HOSTILE |
| Scavengers | HOSTILE | NEUTRAL | INHOSPITABLE | FRIENDLY |
| Sindrian Diktat | HOSTILE | HOSTILE | INHOSPITABLE | SUSPICIOUS |
| Threat | VENGEFUL | HOSTILE | HOSTILE | HOSTILE |
| Shrouded Dwellers | VENGEFUL | HOSTILE | HOSTILE | HOSTILE |
| Tri-Tachyon | VENGEFUL | VENGEFUL | FRIENDLY | FRIENDLY |

## Vanilla And Player Toward Exiles

Rows are vanilla/player source factions. Columns are Exiles target factions.

| Source | Black Fleet | Ashen Crusade | Omicron | Refugees |
|---|---:|---:|---:|---:|
| Hegemony | VENGEFUL | NEUTRAL | HOSTILE | INHOSPITABLE |
| Independents | HOSTILE | INHOSPITABLE | HOSTILE | INHOSPITABLE |
| Knights of Ludd | HOSTILE | HOSTILE | HOSTILE | NEUTRAL |
| Lion's Guard | HOSTILE | HOSTILE | HOSTILE | HOSTILE |
| Luddic Church | HOSTILE | INHOSPITABLE | HOSTILE | INHOSPITABLE |
| Luddic Path | VENGEFUL | COOPERATIVE | VENGEFUL | INHOSPITABLE |
| Mercenaries | HOSTILE | NEUTRAL | HOSTILE | NEUTRAL |
| Persean League | HOSTILE | HOSTILE | INHOSPITABLE | INHOSPITABLE |
| Pirates | INHOSPITABLE | INHOSPITABLE | INHOSPITABLE | HOSTILE |
| Remnants | HOSTILE | HOSTILE | HOSTILE | HOSTILE |
| Scavengers | HOSTILE | NEUTRAL | NEUTRAL | NEUTRAL |
| Sindrian Diktat | HOSTILE | HOSTILE | HOSTILE | HOSTILE |
| Threat | HOSTILE | HOSTILE | HOSTILE | HOSTILE |
| Shrouded Dwellers | HOSTILE | HOSTILE | HOSTILE | HOSTILE |
| Tri-Tachyon | HOSTILE | HOSTILE | FRIENDLY | INHOSPITABLE |

Note: player-to-Exiles relations are only explicitly set for Exiles-to-player in `ExilesSectorGen.java`. `ExilesModPlugin.applyStartingRelations()` separately sets player relations toward Luddic Path and Pirates for the custom start.

## Asymmetry Highlights

These differences may be intentional, but they are worth reviewing during tuning:

- Black Fleet hates the Persean League at `VENGEFUL`, while the League is `HOSTILE` toward Black Fleet.
- Black Fleet is `HOSTILE` toward Pirates, while Pirates are only `INHOSPITABLE` toward Black Fleet.
- Ashen Crusade is `FRIENDLY` toward the Luddic Church, while the Church is `INHOSPITABLE` toward the Ashen Crusade.
- Ashen Crusade is `NEUTRAL` toward Knights of Ludd, while Knights of Ludd are `HOSTILE` toward the Ashen Crusade.
- Omicron is `INHOSPITABLE` toward Independents, while Independents are `HOSTILE` toward Omicron.
- Omicron is `INHOSPITABLE` toward Scavengers, while Scavengers are `NEUTRAL` toward Omicron.
- Refugees are `FRIENDLY` toward several major factions, while those factions are often `INHOSPITABLE` toward refugees.
- Refugees are `NEUTRAL` toward the Ashen Crusade, while the Ashen Crusade is `INHOSPITABLE` toward refugees.
- Tri-Tachyon and Omicron are mutually `FRIENDLY`.

## Tuning Read

- Black Fleet is positioned as broadly hated and highly antagonistic.
- Ashen Crusade is aligned with Luddic Path, warmer toward the Luddic Church, and hostile to high-tech/League/Diktat blocs.
- Omicron is friendly with the player, Remnants, and Tri-Tachyon, but strongly opposed by most human factions.
- Refugees are socially sympathetic in their outgoing relations but distrusted or opposed by many established factions.
