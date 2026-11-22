# Faction polish drafts (2026-07-06)

Proposed replacements for the live files in `data/world/factions/`. Nothing live was touched.
Template: `exileashencrusade.faction` per CLAUDE.md §5. All ship/variant/weapon/portrait/music
ids verified against the vanilla install; `# OPEN QUESTION` comments in each draft mark rulings needed.

## exileomicron.faction.draft
- **Naming-bug fix:** "the omicron initiative" (lowercase prose) replaced with article-less proper
  noun **"Omicron"** everywhere; `personNamePrefixAOrAn` "a"→"an"; `displayNameIsOrAre` "are"→"is".
- **Preserved load-bearing:** `id`, `showInIntelTab:false` (secret systems), `remnantStation1`
  shipRoles (home Nexus fleets), TTDS prefix (Tri-Tachyon parentage), remnant-tag roster.
- **Added:** full UI color set (dark/grid uncommented), machine ranks/posts (Tactical Coordinator,
  Command Node, Nexus Kernel — process/node language, bureaucratic not menacing), neutral-encounter
  Tri-Tach music (player is NEUTRAL per asset-model §4a), `taskForce:"Directive"` fleet name.
- **By-design omissions (commented):** no sell frequencies / illegal commodities (marketless),
  no voices (machines don't chatter).

### Omicron — open questions
1. Keep plain "Omicron", or formalize "the Omicron Initiative" (capitalized) as the long name?
2. Is `custom.pirateBehavior:true` still wanted now that guard hostility is scripted? It may
   fight the "engaging Omicron costs reputation" intent.
3. Shift faction color off vanilla-Remnant teal (e.g. ice-blue) so the two are distinguishable?
   Also: was `patrolMedium:"Sub-Ordo Raider"` deliberate? Draft reverts to "Sub-Ordo".

## exilerefugee.faction.draft
- **Kept:** the already-good identity, grey palette, description, doctrine (all 1s/2s — weaker than
  Independents), civilian roster with rare combat hulls, no-hostilities custom block, ranks/voices.
- **Added (§5 gaps):** `variantOverrides` locking warships to Standard/PD/Escort/Support fits;
  `shipRoles` injecting armed freighters into combat/escort buckets; scrapyard-tier
  weapon/fighter/hullmod sell frequencies (mining gear, PD, efficiency mods); explicit default
  music block; scavenger fleet names (Scrapper Band / Salvage Convoy / Reclamation Fleet);
  `willTradeWhenHostile:true` ("negotiate when possible"); quartermaster "Scrap Reckoner".
- **Fixes:** dead `hammerhead` hullFrequency entry removed (never in knownShips); "Defacto
  Governor" → "De Facto Governor".

### Refugees — open questions
1. `illegalCommodities` left empty (desperation outlaws nothing) — or ban `ai_cores` for the
   Gilead/Luddic roots?
2. Music: keep neutral defaults, or `music_luddite_market_*` for Gilead flavor (overlaps Ashen)?
3. Keep the salvaged-registry "ISS" ship prefix, or mint a refugee-specific one?
