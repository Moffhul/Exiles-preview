# Vanilla Starsector Reference Note

Created: 2026-06-10

This repository does not contain a dedicated vanilla Starsector index. The base game raw data is available read-only at:

`D:/Fractal Softworks/Starsector/starsector-core`

Important locations:

- `data/campaign/starmap.json`: vanilla hyperspace/system placement data.
- `data/campaign/econ`: vanilla markets/economy JSON.
- `data/world/factions`: vanilla faction definitions and faction list.
- `data/hulls`: vanilla hull CSV and ship files.
- `data/variants`: vanilla ship variants.
- `data/weapons`: vanilla weapon/projectile data.
- `data/scripts`: vanilla campaign/combat scripts included as source.
- `starfarer.api`: unpacked API source tree.
- `starfarer.api.zip`: API source archive.
- `starfarer.api.jar`: compiled API jar.

Vanilla data inventory from a quick file-count pass:

- 447 variant files
- 203 ship files
- 163 weapon files
- 116 Java source files
- 110 projectile files
- 80 JSON files
- 70 skill files
- 66 skin files
- 63 system files
- 40 CSV files
- 21 faction files

Recommended future index, if needed:

- Factions: id, display name, doctrine, known relationships.
- Markets: id/entity, faction, size, conditions, industries, special items.
- Ships: hull id, role, size, systems, tags, variants.
- Weapons: weapon id, type, size, OP, damage profile.
- Conditions/industries/items: id, plugin/script, tags, icons.
- API cross-reference: common classes used by Exiles and their vanilla implementations.

