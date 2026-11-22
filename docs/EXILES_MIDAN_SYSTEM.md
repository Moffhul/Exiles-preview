# Midan system redesign

Status: implemented 2026-11-05 from Sean's approved design; new-game visual review pending.
This is the current refuge, **Midan**, not the ruined origin system Meridian.
Lore authority: `EXILES_LORE_HISTORY.md`; survivors fled First Haven's destruction in
Cycle 186, and Haven remembers their lost home. The refuge should express hardship
while retaining Midan's industrial threat.

## Layout and climate

All distances below are game units, measured from the star unless stated otherwise.
All orbits remain circular. Existing entity IDs are retained.

| Feature | Radius / orbit | Treatment |
| --- | --- | --- |
| White dwarf | radius 180; corona 300 | Subdued near-neutral white system light |
| Inner remnants | orbit 1,800; belt width 180 | 35 sparse rocks, no continuous ring overlay; mostly empty interior |
| Fawkes | orbit 2,400; radius 90 | Barren, warm rock tint; no cold or poor light |
| Haven | orbit 4,000; radius 160 | Tundra texture, muted seas/land and atmosphere; Cold; poor farmland retained |
| Midan Belt | orbit 5,600; width 800 | 220 asteroids and three visible bands |
| Mining station | orbit 5,600 | Embedded at belt center; no Cold, Very Cold, or Poor Light |
| Celsius | orbit 7,600; radius 210 | Restrained pale blue ice giant; Cold and Poor Light |
| Brahmagupta | orbit 10,400; radius 320 | Darker gas giant; Very Cold and Poor Light |
| Icy rings | 650 and 850 from Brahmagupta; each width 256 | Broad ring extending to 978 from the giant |
| Grave | orbit 1,500 from Brahmagupta; radius 110 | Rocky moon; Very Cold and Poor Light based on parent's stellar distance |

The inner clearing uses vanilla red-giant displayed scale as a visual reference,
not a physical engulfment simulation. Haven's survival and habitability are accepted
fictional allowances. Distances, tint values, and climate gradation implement the
approved direction and remain subject to in-game visual review.

The relay is at 4,800, sensor array at 8,600, and nav buoy co-orbits Brahmagupta
60 degrees ahead. Haven's jump stays at 4,300, linked to Haven and sharing its period.
The outer jump co-orbits Brahmagupta 60 degrees behind, clear of the moon and rings,
and remains linked to Brahmagupta. **Suggestion only:** rename "Moloch Jump-point"
to match Brahmagupta in a later author-approved pass; its current name is preserved.

## Market and lifecycle boundaries

Populated markets remain authored in `data/campaign/econ/exilemidan.json`. Only Haven's
Cold and Brahmagupta's replacement of Cold with Very Cold plus Poor Light change there.
All other conditions, resources, sizes, industries, defenses, items, cores, factions,
and free-port settings are preserved. Planet appearance changes use spec changes,
not procedural condition generation or colony market reinitialization.

Celsius and Grave use vanilla `Misc.initConditionMarket`: neutral, condition-only
markets, with no population, industries, or registration in the trading economy.
Only their specified climate conditions are added. Generation occurs after vanilla
procgen and before authored economy loading. No save migration is included.

Meridian, secrecy/constellation settings, faction behavior, and the economy outside
the authorized climate changes are outside this redesign.

## Validation and remaining review

- Starsector Janino compile of `data.scripts.world.systems.ExileMidan`: EXIT=0,
  using the existing checker with its source root redirected in memory to this worktree.
- Parsed market comparison against HEAD: all nonclimate fields identical;
  station explicitly checked for absence of all three climate conditions.
- `git diff --check`: passed. Vanilla white-dwarf ID, spec APIs, ring APIs and
  condition-market lifecycle checked against installed game source.
- Geometric review: Grave's inner surface is 1,390 from its parent, outside the
  ring's 978 outer radius; its stellar sweep begins at 8,790, beyond Celsius's
  outer surface at 7,810. The station lies inside belt bounds 5,200–6,000.
- **Not verified in-game:** start a new campaign and review white-dwarf lighting,
  Haven's ice/land/sea readability, sparse inner debris, station visibility in the
  industrial belt, outer ring/moon spacing, jump links, and displayed climate/markets.

Implementation was developed in the Codex worktree and then copied to the installed
mod at `D:/Fractal Softworks/Starsector/mods/Exiles`. Both existing destination files
matched the task's starting base before transfer; the new design record did not exist.
Only the three task files were transferred, with matching SHA-256 hashes verified.
No commit, push, or game launch was performed.
