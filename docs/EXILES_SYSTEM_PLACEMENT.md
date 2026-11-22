# EXILES — Custom Star-System Placement

Canonical reference for how the custom star systems are placed on the hyperspace map, and how to
tune them. Built 2026-07-20.

## The problem this solves

Vanilla `SectorProcGen` places ~100 procedural constellations, and for **every star system that
already exists** when it runs, it reserves an ~8,000-unit-radius keep-out where no constellation may
spawn (`blotOut(cells, x, y, 8)`, `CELL_SIZE = 2000`). When the Exiles systems were all generated in
`onNewGame()` — i.e. *before* proc-gen — each one carved its own empty bubble. Hidden systems sat
alone in a clearing and "stood out like spotlights."

The fix is **timing, not tags**: generate the custom systems in `onNewGameAfterProcGen()`, *after*
the constellations are down. They then never trigger the keep-out, and a proximity detector actively
drops them near procgen systems so they read as part of the field.

## Two-pass architecture

| Pass | Callback | What runs | Why |
|------|----------|-----------|-----|
| Home | `ExilesModPlugin.onNewGame()` → `ExilesSectorGen.generateHomeSystems()` | **Xechas only** + faction relations | Xechas is the `NGCSetStartingLocation` target; it stays on the proven early path. A gap around the player's home is harmless. |
| Deferred | `ExilesModPlugin.onNewGameAfterProcGen()` → `ExilesSectorGen.generateDeferredSystems()` → `ExileSystemPlacement` | The other **9 systems**, config-driven | After proc-gen (no keep-out gap), but still **before economy load**, so the market-bearing custom systems bind their markets normally. |

`onNewGameAfterProcGen` is the exact right seam: any later (e.g. `onNewGameAfterEconomyLoad`) and the
market systems would miss economy binding.

## How placement works (`ExileSystemPlacement`)

Per cluster, each new game:

1. **Jitter** the cluster anchor to a random point within `jitterRadius` of `nominalCenter`
   (per-game variety).
2. **Detector** re-rolls the anchor (up to `maxPlacementAttempts`) evaluating the *whole* cluster
   footprint (anchor + every member offset). It rejects candidates that drop any member within
   `minSeparationFromAnySystem` of an existing system (no overlapping icons) and prefers an anchor
   with a procgen neighbor within `preferNeighborWithin` (this is what removes the gap). The nominal
   center is tried first, so a good authored position is kept as-is.
3. **Build** each member at `anchor + its offset`, so the cluster keeps its internal shape wherever
   it lands. The generator sets that position first, so its own `clearArc` (nebula clearing) and
   `autogenerateHyperspaceJumpPoints` land at the final spot.
4. **Optional per-member**: join the nearest constellation (`joinConstellation`) and/or add
   `THEME_HIDDEN`+`THEME_SPECIAL` (`themeHidden`).

**Graceful degradation:** if a region genuinely has no procgen system within reach (deep fringe), the
"prefer neighbor" step is soft — the system still places (no gap either way, because nothing was
blotted). If the config file can't be read at all, every system falls back to its original hardcoded
coordinate with no jitter, so worldgen never breaks.

## The config: `data/config/exiles/system_placement.json`

Loose JSON (comments + trailing commas allowed). Edits take effect on the next **new game** only —
this runs at worldgen. Structure:

- `detector` — `minSeparationFromAnySystem`, `preferNeighborWithin`, `maxPlacementAttempts`.
- `clusters.<name>` — `nominalCenter` `[x,y]`, `jitterRadius`, and `members`.
- `clusters.<name>.members.<key>` — `offset` `[x,y]` (from the anchor), `joinConstellation` (bool),
  `themeHidden` (bool).

Member keys map to generators in `ExileSystemPlacement.dispatchGenerate()`:
`omicron`→ExileOmicron0, `cipher`→ExileOmicronCipher, `vigil`→ExileOmicronVigil,
`lattice`→ExileOmicronLattice, `sallee`→ExileBlackFleetSallee, `lundy`→ExileBlackFleetLundy,
`meridian`→ExileMidanOrigin, `midan`→ExileMidan, `providence`→ExileAshenCrusade.

Seeded clusters (nominalCenter = current layout centroid; offsets reproduce today's positions at zero
jitter): **omicron** (4 marketless nodes, jitter 7000), **blackFleet** (sallee+lundy market +
meridian marketless, jitter 3000), **midan** (market, jitter 3000), **ashen** (market, jitter 3000,
TEST location — retune north).

## Tuning notes / constraints

- **Units** ≈ 2000 = 1 light-year. Sector is 164000 × 104000, so the field fills to ~10k from each
  edge; all current coords are interior, so the detector finds real neighbors.
- **Market systems** (`midan`, `sallee`, `lundy`, `providence`): keep `jitterRadius` **small**, and if
  you move a `nominalCenter`, **mirror it in `data/campaign/starmap.json`** — that file feeds econ
  trade-distance at load. Runtime accessibility uses the live position (`getLocationInHyperspace`), so
  a small jitter drifts distances only ~1–2 LY. **Marketless systems** (omicron cluster, `meridian`)
  have no starmap tie — jitter freely.
- **`joinConstellation`** makes a system a *labeled* member of the nearest constellation (shares its
  name; good for "looks native"). Leave `false` for secret systems you don't want surfaced.
- **`themeHidden`** keeps pirate/Path bases, random missions, derelicts, and survey intel out of the
  system. Ships **`false`** everywhere — enable per-system once ratified. (Does **not** hide the
  system on the map; that's a separate no-star/gravity-well mechanism — see the TT Black Site pattern.)

## Code map

- `data/config/exiles/system_placement.json` — the tunables.
- `data/scripts/world/ExileSystemPlacement.java` — loader + detector + dispatch + join/tags.
- `data/scripts/world/ExilesSectorGen.java` — `generateHomeSystems()` / `generateDeferredSystems()`.
- `data/scripts/exiles/ExilesModPlugin.java` — `onNewGame()` (home) + `onNewGameAfterProcGen()` (deferred).
- `data/scripts/world/systems/*.java` — the 9 deferred generators now take `(SectorAPI, float, float)`
  and return the `StarSystemAPI`. Xechas is unchanged (`generate(SectorAPI)`).

## Status

**BUILT + boot-tested in-game 2026-07-20 (Sean: "it just works").** New-game run confirmed the map
fills in around the custom systems with no spotlight gaps and the Xechas start intact. Default
`themeHidden`/`joinConstellation` remain off, so this changed no content behavior beyond the
placement/timing itself. Remaining work is tuning only: retune `nominalCenter`s toward the real
compass intent (Omicron farther west + spread, Ashen north) and decide per-system
`joinConstellation`/`themeHidden`.
