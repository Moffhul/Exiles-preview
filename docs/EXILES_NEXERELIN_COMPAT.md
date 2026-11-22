# EXILES ↔ NEXERELIN COMPATIBILITY AUDIT

**Status: COMPATIBILITY IMPLEMENTED EXCEPT JAR-GATED CUSTOM START — release validation deferred.**
**Date: 2026-07-06.** Audited against **Nexerelin 0.12.1d** (`D:\Fractal Softworks\Starsector\mods\Nexerelin`)
and Exiles working tree @ `main` (post-bdb6f0b). Author declaration: Nexerelin compatibility is now a
**priority** (previously undecided — CLAUDE.md §4 open item is superseded by this doc).

## Setup compatibility and player disclosure — ruled 2026-09-12

Sean approved requiring normal/default starting relationships for the authored Exiles campaign.
Nex FLATTEN is unsupported: its initialization reset bypasses `noRandomizeRelations` and can
erase story-required relationships. Do not silently honor the selection in the UI and then replace
its result with the authored matrix. Existing Hegemony–League runtime protections remain separate
from this setup decision. Older research below is historical where it conflicts with this ruling.

**Player-facing requirement:** document every Exiles restriction or override that prevents a
player-selected setup option from taking effect. Explain the affected option, the supported setting,
why Exiles requires it, and whether an incompatible selection is blocked, overridden, or unsupported.
Provide this information before campaign commitment and in installation/setup documentation.
Do not describe a planned warning or enforcement mechanism as already implemented.

The disclosure checklist includes:
- Normal/default starting relationships; FLATTEN is unsupported.
- The existing fixed/Corvus-sector requirement; randomized sectors are unsupported.
- Vanilla Survival Mode ("Extended Mode", 0.98.5a): not currently supported (Sean, 2026-11-12).
  The vanilla-dialog Exile start is hidden when that mode is selected (`ngcExileStart` carries
  `!$extendedMode`). In its place the dialog shows a greyed-out option, "Exiles of the Shattered
  Sector (compatibility pending)", whose tooltip explains why (`ngcExileStartExt`, built
  2026-11-14, not yet seen in game). The Nex custom-start path has not been checked against the
  mode and is on hold by Sean's ruling (2026-11-14) until Nexerelin itself supports 0.98.5a.
- Configurable Hegemony–League hostility and NPC-conquest protections: explain their effects,
  switches and the consequences of disabling them, retaining the previously approved reversibility.
- Any additional setup overrides found during the final compatibility pass.

Status: policy and documentation requirement recorded; player-facing setup text and UI behavior
remain to deliver and verify with the eventual Nex custom-start integration. No runtime change
was made for this ruling.

**Author workflow ruling 2026-07-12:** `ExilesNexStart` is implemented, but Nex validates custom-start
classes with a jar classloader that cannot see the Janino loose-script class. The start therefore
remains intentionally deferred until Exiles moves to a compiled jar; this is a known packaging gate,
not an unresolved design or a reason to interrupt the current edit → new game → test loop. Development
and any public WIP build remain loose-source and Janino-compatible so iteration stays fast and interested
players/developers can inspect the AI-authored source. The final release will use a fully compiled jar,
at which point the Nex start must receive its complete test pass.

**Post-split correction 2026-07-12:** `exileblackfleet` is registered in `mod_factions.csv`; Midan
and Black Fleet have reciprocal FRIENDLY floors/exceptions in their Nex configs. The intended private
economy remains unimplemented and requires a dedicated Nex/econ-group test before release.

**Method note:** Nexerelin's jar (`jars/ExerelinCore.jar`) bundles its full `.java` sources (629 files),
so every claim below is verified against actual Nex code, not just its data files. Key sources cited as
`nexsrc:` paths (relative to the jar root). Extracted copy used for this audit lived in the session
scratchpad; re-extract any time with 7zip/`unzip` per `jars/src.txt`.

---

## 0. Executive summary

Nexerelin fully replaces the new-game flow and adds faction-level machinery (diplomacy, invasions,
vengeance, alliances, respawns) that keys **entirely off its own per-faction config files**
(`data/config/exerelinFactionConfig/<factionId>.json`) — it never reads vanilla `.faction` custom flags
like `pirateBehavior`/`makesPirateBases` for its own decisions. Exiles already has the skeleton of a Nex
integration (`data/config/exerelinFactionConfig/` with `mod_factions.csv` + `exilerefugee.json` +
`player.json` — the "garbled path" in CLAUDE.md §4 is just a typo in the doc; the real files are correctly
named and **do** load). But:

1. **The Exiles custom start is unreachable under Nexerelin** (BLOCKER).
2. **The three unregistered factions (midan/omicron/ashen) are invisible to Nex** — which protects some
   invariants for free but leaves their home markets open to one-way conquest and their scripted relations
   open to two Nex features (relation randomization, commission rep-sync) (MAJOR).
3. **`exilerefugee.json` is dangerously minimal**: `playableFaction:true` alone turns the refugees into a
   full Nex military actor — invasions, raids, special forces, a strategic-AI brain, vengeance fleets (MAJOR).

Everything is fixable with **one Java class + one JSON file + three small JSON files + a few lines in
existing files**. Recommended fix list in §3.

---

## 1. Severity-ranked findings

| # | Sev | Finding | Where |
|---|-----|---------|-------|
| 1 | **BLOCKER** | Exiles' custom start (`ngcExileStart`) never appears in a Nexerelin game: Nex overrides `BeginNewGameCreation` at `score:999` and its NGC flow never fires vanilla's `AddNewGameChoices` trigger, which is what our rules.csv row hooks. | Nex `data/campaign/rules.csv:5`; Exiles `data/campaign/rules.csv:4` |
| 2 | **MAJOR** | `exilemidan`, `exileashencrusade`, `exileomicron` are not registered in `mod_factions.csv` → they get Nex's **neutral default config** (`playableFaction:false`). Net effect: never "live", excluded from Nex diplomacy/invasion-launching/respawn (mostly good), **but** their markets remain valid invasion/raid **targets** with no counterplay, and two Nex features can still break scripted relations (see #3, #4). | Exiles `data/config/exerelinFactionConfig/mod_factions.csv`; `nexsrc: exerelin/utilities/NexConfig.java:389-411`, `exerelin/campaign/SectorManager.java:1532-1559` (reinitLiveFactions), `:832-835` (playable guard in factionRespawned) |
| 3 | **MAJOR** | **"Random starting relations" NGC option breaks the invariants.** If the player picks randomized start relations, factions without `noRandomizeRelations:true` get their relations rolled −0.85..+0.6 — Midan could start friendly with the Hegemony; Omicron's player-INHOSPITABLE stance can be rolled away. None of our four factions set the flag (refugee config omits it too). | `nexsrc: exerelin/campaign/DiplomacyManager.java:1299-1445` (initFactionRelationships, random branch) |
| 4 | **MAJOR** | **Commission/faction-join rep-sync breaks "Omicron neutral-to-player".** Nex syncs the player's per-faction rep to the joined/commissioned faction's rep unless the *other* faction has `noSyncRelations:true`. Join/commission Hegemony (HOSTILE to Omicron) → player becomes hostile to Omicron; ditto Midan's INHOSPITABLE-to-player. | `nexsrc: exerelin/utilities/NexUtilsReputation.java:192-221` |
| 5 | **MAJOR** | **`exilerefugee.json` makes refugees a military power.** `playableFaction:true` (needed for the census — correct) also grants, by default: invasion-point accumulation from their 4 markets → NPC invasions/raids **launched by refugees**; `specialForcesMaxFleets` default **2** (refugee special task groups roaming the sector); a StrategicAI brain (`useStrategicAI` default true for non-pirates); vengeance fleets vs the player (GENERIC def, sourced from their markets); respawn invasion fleets if the player enables faction respawn. All violate "NOT a military power; runs from fights". | Exiles `data/config/exerelinFactionConfig/exilerefugee.json`; `nexsrc: exerelin/campaign/fleets/InvasionFleetManager.java:1084-1091,1150`, `exerelin/utilities/NexFactionConfig.java:230,287`, `exerelin/campaign/RevengeanceManager.java:218-233,832` |
| 6 | **MAJOR** | **Refugee colonies (and Midan/Ashen home markets) are conquerable by NPC invasions with no comeback.** Any hostile live faction may invade/raid them (`allowInvadeStartingMarkets:true` by default). Refugees are HOSTILE to Lions Guard/pirates/Remnants (Diktat lowered to INHOSPITABLE 2026-10-07); Midan is hostile to everyone; Ashen to League/Diktat/TT/pirates. Unregistered factions can't counter-invade or respawn → one-way grind-down; refugee wipe = `factionEliminated` (intel event, faction hidden). Decide: protect (memory key `$nex_uninvadable`), fight back (full configs), or accept as emergent narrative. | `nexsrc: exerelin/utilities/NexUtilsMarket.java:232-253` (canBeInvaded; honors `$nex_uninvadable`), `exerelin/campaign/fleets/InvasionFleetManager.java:600-650`, `exerelin/campaign/SectorManager.java:543-575` |
| 7 | **MINOR** | **Omicron fleets drop human prisoners.** Per-battle prisoner drops are gated on the *losing* faction's config `dropPrisoners` (default true; Omicron has no config). Machines dropping ransomable human prisoners is fiction-breaking. (Remnants set `dropPrisoners:false` for exactly this reason.) | `nexsrc: exerelin/campaign/SectorManager.java:232`; Nex `data/config/exerelinFactionConfig/remnant.json` |
| 8 | **MINOR** | **Asher gets double-patched.** In corvus mode with `useEnhancedCoreWorlds:true` (Nex default), Nex adds `orbitalstation_mid` + `heavybatteries` to Asher and removes ground defenses; Exiles' Asher patch adds `patrolhq` + swaps `orbitalstation`→`battlestation`. Depending on plugin call order Asher can end up with **two station industries**. Both patches buff Asher (compatible intent), but the result is untidy. | `nexsrc: exerelin/world/VanillaSystemsGenerator.java:190-193`; Exiles `ExilesModPlugin.applyAsherChangesOnce()` (data/scripts/exiles/ExilesModPlugin.java:328-354) |
| 9 | **MINOR** | **Exiles' `player.json` leaks the exile ships into all Nex solo starts.** `startShipsSolo` merges with Nex's own `player.json` (same merged-JSON mechanism as `.faction` files), adding `wolf_exile_Starting`+`hound_exile_Starting` as a candidate set for *any* Nex "own fleet / solo" start. Harmless-to-nice while Exiles is loaded, but it is NOT a substitute for finding #1 — it carries none of the start's story, colonies, officers, debt, or start location. Verify in game whether arrays concatenate (expected) or replace (would also hide Nex's tutorial ship sets). | Exiles `data/config/exerelinFactionConfig/player.json`; Nex same path; `nexsrc: exerelin/utilities/NexFactionConfig.java:215-216` (getMergedJSONForMod) |
| 10 | **MINOR** | **Nex random-sector mode is unsupported** (already declared: `corvusCompatible:true` + comments). In random mode our 4 systems still generate (ExilesModPlugin.onNewGame runs unconditionally) and Midan/Ashen econ-JSON markets load, but all vanilla-market patches no-op, refugee markets never exist, and the timeline anchors (Nachiketa, Gilead) are absent. Needs an explicit "not supported" statement (and `randomSector:-1` on the ported custom start, finding #1). | Exiles `data/campaign/econ/*`; `ExilesModPlugin.applyEconomyPatches()` null-guards |
| 11 | **MINOR** | **Nex dynamic diplomacy + StrategicAI will drive vanilla↔vanilla wars/peace on their own clock**, independent of the Exiles timeline. Helpful: Nex itself re-applies Hegemony↔League HOSTILE at start ("emulate starting hostilities"). Risky: its peace treaties/ceasefires can erase that tension before Phase-1 content lands, and the planned "Sector Destabilization Director" will overlap with Nex's StrategicAI (double-driving raids/wars). Architectural decision for Phase 1, not a bug. | `nexsrc: exerelin/world/VanillaSystemsGenerator.java:159-166`, `exerelin/campaign/DiplomacyManager.java` (war weariness → ceasefire), `exerelin/campaign/ai/*` |
| 12 | **OK** | **Refugee census integration works exactly as the ExilesModPlugin comment claims.** Markets are econ-JSON-declared → owned by `exilerefugee` before `SectorManager.reinitLiveFactions()` runs in Nex's `onNewGameAfterEconomyLoad`; the faction is registered via merged `mod_factions.csv`; `playableFaction:true` passes the census filter → live faction, diplomacy profile, visible in Factions intel. | `nexsrc: exerelin/plugins/ExerelinModPlugin.java:699-724`, `exerelin/campaign/SectorManager.java:1532-1559` |
| 13 | **OK** | **Marketless Omicron is correctly invisible to Nexerelin.** Live-faction census requires ≥1 invadable market → Omicron never live: no diplomacy events, no war weariness/ceasefires, no alliances, never an invasion target (target checks operate on markets), vengeance fleets can't spawn (need a source market — `pickMarketForFactionVengeance` returns null), counter-invasions can't requisition from it, agents have nothing to target. Station-mode fleets are excluded from `canBeInvaded` explicitly (`primaryEntity instanceof CampaignFleetAPI`). Only leaks: findings #3, #4, #7. | `nexsrc: exerelin/campaign/SectorManager.java:543-575`, `exerelin/campaign/RevengeanceManager.java:218-233,313`, `exerelin/utilities/NexUtilsMarket.java:235` |
| 14 | **OK** | **Exiles' scripted relations survive Nex's start-relations pass (default settings).** Order: Exiles sets exile-faction relations in `onNewGame`; Nex's `initFactionRelationships` runs in `onNewGameAfterEconomyLoad` but in corvus/default mode only re-runs *vanilla* `SectorGen.initFactionRelationships` + config-flagged factions — exile pairs untouched; Exiles' player tweaks (Path −0.65, pirates +1.0) run in `onNewGameAfterTimePass`, after everything Nex does at gen time. (The invariant-breakers are the *non-default* paths: findings #3, #4.) | `nexsrc: exerelin/plugins/ExerelinModPlugin.java:100` (hook order comment), `:720-724`; `exerelin/campaign/DiplomacyManager.java:1349-1360` |
| 15 | **OK** | **`pirateBehavior` / `makesPirateBases` do NOT get Exiles factions lumped into Nex's "pirate" handling.** Nex pirate status = config flag `pirateFaction` only (`NexUtilsFaction.isPirateFaction` reads NexFactionConfig, never the vanilla custom block). Vanilla `makesPirateBases` keeps working under Nex: `Nex_PirateBaseManager` extends the vanilla manager and inherits `pickPirateFaction()`, which scans the vanilla custom flag — Midan still gets pirate bases. | `nexsrc: exerelin/utilities/NexUtilsFaction.java:187-206`, `exerelin/campaign/intel/bases/Nex_PirateBaseManager.java:47`; vanilla `starfarer.api/.../PirateBaseManager.java:94-102` |
| 16 | **OK** | **No ID collision with Nexerelin's bundled `omicron_faction.json`.** That config belongs to a *different* mod's faction (`omicron_faction`), distinct from our `exileomicron`. Footnote only: don't grab it as a template by accident — though its content (playable:false, noRandomize, noSync, start-neutral-to-player) is coincidentally almost exactly what exileomicron needs. | Nex `data/config/exerelinFactionConfig/omicron_faction.json` |

### Answers to the audit questions, mapped to findings
1. **Faction config need:** Required for any faction that should participate in (or be *shielded* from) Nex systems. Without one → neutral default (`playableFaction:false`): skipped by census, diplomacy, invasion-launching, respawn; still targetable and still exposed to randomize/sync (findings #2–#4). The "garbled path" was a CLAUDE.md typo — the real `data/config/exerelinFactionConfig/` files exist and load.
2. **Diplomacy overrides:** Default corvus start does NOT clobber scripted relations (#14). The two real threats are the random-relations NGC option (#3) and rep-sync on join/commission (#4). Pinning tools used by other mods: `noRandomizeRelations`, `noSyncRelations`, `disableDiplomacy`, `hostileToAll` (1/2/3), `minRelationships`/`maxRelationships` caps (enforced while `useRelationshipBounds:true`, the default), `isPirateNeutral`. See `remnant.json`, `derelict.json`, `templars.json` for canonical patterns.
3. **Marketless Omicron:** correctly invisible (#13); residual leaks are relations (#3, #4) and prisoners (#7).
4. **pirateBehavior/makesPirateBases:** no lumping (#15). Do NOT set Nex's `pirateFaction:true` on any Exiles faction — that's what would cause the lumping (auto-hostility handling, "morality: evil", pirate-invasion gating, excluded from alliances).
5. **Refugee colonies:** conquerable and eliminable by default (#6); refugees also become an *attacker* with the current config (#5).
6. **Custom start:** dead under Nex (#1); random sector unsupported (#10); vanilla-market patches themselves are safe in corvus mode (only Asher overlaps Nex's own enhancements, #8).
7. **Nex config lists:** only `mod_factions.csv` (register all four) + per-faction JSONs. Nothing needs adding to `exerelin_config.json` itself (its `builtInFactions` list is explicitly do-not-touch; invasion/diplomacy gating is all per-faction config + memory keys).

---

## 2. How the relevant Nex machinery actually works (evidence digest)

- **Config load:** `NexConfig.loadSettings()` reads `builtInFactions` from `exerelin_config.json`, then
  `mod_factions.csv` (merged across mods — our one-line CSV works), and instantiates a `NexFactionConfig`
  per registered id from `data/config/exerelinFactionConfig/<id>.json` (also merged). Unregistered id →
  `getFactionConfig()` returns the **neutral** default. (`nexsrc: exerelin/utilities/NexConfig.java:320-411`)
- **Live-faction census:** `reinitLiveFactions()` iterates **registered** configs only; live = has ≥1
  market + `playableFaction:true`. The periodic `recheckLiveFactions()` scans all markets but
  `factionRespawned()` re-checks `playableFaction` — so unregistered factions can never drift into the
  live set. (`SectorManager.java:543-575, 832-835, 1532-1559`)
- **Diplomacy:** random events + war weariness/ceasefires iterate live faction ids and skip
  `disableDiplomacy` factions (also hardcoded skips: templars, independent, luddic_path).
  (`DiplomacyManager.java:98-111, 745-840`)
- **Invasions:** launchers = live + `playableFaction` + not-pirate (unless pirate invasions enabled);
  point rate × `invasionPointMult`; `canInvade:false` degrades launches to raids (does NOT pacify — use
  `invasionPointMult:0` to pacify). Targets = any market hostile to the attacker passing `canBeInvaded`
  (blocks: `abandoned_station` condition, station-mode fleets, hidden markets, `$nex_uninvadable` memory
  key, `nex_uninvadable` tag (deprecated)). (`InvasionFleetManager.java:1010-1160, 600-650`;
  `NexUtilsMarket.java:232-253`)
- **Vengeance vs player:** points accrue when the player kills a hostile faction's fleets; fleet spawn
  requires a faction market; enum `VengeanceDef` falls back to GENERIC for mod factions.
  (`RevengeanceManager.java:256-330`; `VengeanceFleetIntel.java:835+`)
- **Respawns:** opt-in at NGC (`respawnFactions` default **false**); picker skips `playableFaction:false`
  and anything in the public static `SectorManager.DO_NOT_RESPAWN_FACTIONS` set (addable from mod code).
  (`SectorManager.java:107, 715-755`; `ExerelinSetupData.java:46`)
- **Alliances:** live, non-pirate, relations better than WELCOMING, and a shared positive alignment
  required — exile factions' universal hostility + zero alignments make alliances a non-issue.
  (`AllianceManager.java:431-480, 132-144`)
- **Custom starts:** defined in merged `data/config/exerelin/customStarts.json`; `className` must extend
  `exerelin.campaign.customstart.CustomStart`; instantiated via **the script classloader**
  (`NexUtils.instantiateClassByName`) — a Janino-compiled `data/scripts` class works for *execution*.
  **CORRECTION (verified in game 2026-07-06):** the earlier claim that `validateCustomStart` is dead code
  was WRONG — `Nex_NGCCustomStart.addCustomStartOptions()` calls it when building the start menu, and it
  uses plain `Class.forName()`, which resolves through Nex's jar classloader and **cannot see Janino
  loose scripts**. Result: the Exile start is *listed* (instantiation via script classloader succeeds →
  name/tooltip render) but *greyed out* with the "failed to load" tooltip. **The start stays disabled
  until `ExilesNexStart` ships precompiled in a jar** (full release jar, or a one-class mini-jar — the
  class only references vanilla API + Nex classes, no other Exiles loose scripts, so it compiles alone
  against `starfarer.api.jar` + `ExerelinCore.jar`). Reference implementation:
  `SpacerStart.java` (adds ships/cargo/credits via `CharacterCreationData`, runs deferred `Script`s,
  sets difficulty, `$nex_startLocation` memory key exists for spawn placement).
  (`customstart/CustomStartDefs.java`, `customstart/SpacerStart.java`)

---

## 3. Recommended fix list (concrete, ordered)

> All items are **additive files or small idempotent script tweaks**; nothing here touches the Ashen
> polish template, the econ JSONs, or the director stack. Nothing has been changed yet.

### 3.1 BLOCKER — port the Exiles start to Nex's custom-start system
Create `data/config/exerelin/customStarts.json` in Exiles (merges with Nex's):

```json
{
    "starts":[
        {
            "id":"exileStart",
            "name":"Exile",
            "difficulty":"Very hard",
            "desc":"A mercenary framed for heinous crimes, commanding a pair of heavily damaged ships. A difficult start with a long grind, but with a small starting colony outside the core worlds.",
            "className":"data.scripts.exiles.ExilesNexStart",
            "randomSector":-1,   # non-random sector only (Exiles is corvus-only)
        },
    ],
}
```

New `data/scripts/exiles/ExilesNexStart.java` extending `exerelin.campaign.customstart.CustomStart`
(Janino-conservative style; model on `SpacerStart.java`):
- `execute()`: pull `$characterData` (`CharacterCreationData`) from `memoryMap`, add
  `wolf_exile_Starting` + `hound_exile_Starting`, starting cargo/credits matching `ngcExilePicked`,
  set difficulty, `ExerelinSetupData.getInstance().freeStart = true`,
  `PlayerFactionStore.setPlayerFactionIdNGC(Factions.PLAYER)`, and set the start location to the Xechas
  start point (mirror whatever `NGCSetStartingLocation ExileXechas` resolves to; `$nex_startLocation`
  in sector memory is the Nex-native hook).
- Prologue story pages: simplest v1 = render the rules.csv prologue text into the dialog text panel from
  the class; keep the vanilla rules-driven version for non-Nex games. (The rules rows themselves stay —
  they still serve the vanilla path.)
- **Janino caveat:** this class references Nexerelin classes, so it must NOT break game start when Nex is
  absent. Starsector's Janino loader compiles classes lazily on first load, and this class is only ever
  loaded by Nex itself (via `instantiateClassByName`), so it is safe in practice — but verify once with
  Nex disabled (per the Janino offline compile-check workflow, exclude this file when Nex is off).
- Starting officers/colonies/debt already ride on `onNewGameAfterTimePass` / persistent-key setup in
  `ExilesModPlugin` and work under any start path — no port needed. (Synergy note: Nex's
  `Nex_SpacerObligation` is a ready-made debt mechanic if the "Spacer debt" option wins the open
  starting-debt decision.)

### 3.2 MAJOR — register all four factions and add the three missing configs
`data/config/exerelinFactionConfig/mod_factions.csv` — append:

```csv
exilemidan
exileomicron
exileashencrusade
```

**`exileomicron.json`** (script-only faction; goal = total shielding):
```json
{
    "playableFaction":false,       # stays out of census/diplomacy/invasions/respawn
    "startingFaction":false,
    "corvusCompatible":true,
    "disableDiplomacy":true,       # belt-and-braces vs future Nex features
    "noRandomizeRelations":true,   # protects INHOSPITABLE-to-player + scripted matrix (finding #3)
    "noSyncRelations":true,        # protects vs commission rep-sync (finding #4)
    "dropPrisoners":false,         # machines carry no prisoners (finding #7)
    "allowAgentActions":false,
    "allowPrisonerActions":false,
    "specialForcesMaxFleets":0,
    "vengeanceFleetSizeMult":0,
}
```

**`exilemidan.json`** (must stay hostile to established powers):
```json
{
    "playableFaction":false,       # see decision box below before flipping to true
    "startingFaction":false,
    "corvusCompatible":true,
    "disableDiplomacy":true,       # no ceasefires/peace treaties, ever
    "noRandomizeRelations":true,
    "noSyncRelations":true,
    "maxRelationships":{ "default":-0.5 },   # hard cap: nothing can push Midan above HOSTILE
                                             # (enforced while useRelationshipBounds:true, Nex default)
    "specialForcesMaxFleets":0,
}
```
Do **not** set `pirateFaction:true` (finding #15 — it drags in morality/hostility/invasion-gating
semantics that misrepresent principled nihilists) and do **not** rely on `hostileToAll` (it only applies
during the randomize branch and would fight the hand-authored matrix; the `maxRelationships` cap +
`disableDiplomacy` is the precise tool).

**`exileashencrusade.json`**:
```json
{
    "playableFaction":false,       # same decision box
    "startingFaction":false,
    "corvusCompatible":true,
    "noRandomizeRelations":true,
    "noSyncRelations":true,
    "minRelationships":{ "luddic_church":-0.49 },  # optional: Church↔Ashen can never go to open war
                                                   # ("opposes methods, not faith") — same trick Nex
                                                   # uses for luddic_path↔luddic_church
    "specialForcesMaxFleets":0,
}
```

> **Decision box — `playableFaction` for Midan/Ashen.** `false` (recommended for now) keeps Nex totally
> hands-off: no Nex-driven invasions *by* them, no diplomacy, no respawn — their aggression stays 100%
> in Exiles' director scripts (fits the "one configurable Destabilization Director" architecture; avoids
> double-driving with Nex StrategicAI, finding #11). Cost: they cannot counter-invade if NPC factions
> take their worlds → pair with 3.4. Flipping to `true` later (with `startingFaction:false`,
> `alignments`, `morality`, `canInvade` etc.) is a clean upgrade path if you'd rather outsource their
> war-making to Nex — revisit at Phase 1.

### 3.3 MAJOR — harden `exilerefugee.json`
Append to the existing file (keep `playableFaction:true` — it's what makes the census/intel work):
```json
    "canInvade":false,             # degrade would-be invasions to raids...
    "invasionPointMult":0,         # ...and never accumulate points at all → no offensive fleets, period
    "specialForcesMaxFleets":0,    # no refugee special task groups (default is 2!)
    "useStrategicAI":false,        # no Nex strategic-AI brain scheming for them
    "vengeanceFleetSizeMult":0,    # no hunter-killer fleets when the player farms refugee ships
    "noRandomizeRelations":true,   # protect the scripted stance under random-relations starts
    "noSyncRelations":true,        # player↔refugee rep is quest currency (Phase 2) — never sync it
    "morality":"good",
    "colonyExpeditionChance":0,    # (default 0; explicit for clarity — no refugee colony expeditions)
```
Leave diplomacy ENABLED for refugees (they "negotiate when possible" — Nex ceasefire/aid events with
their enemies are on-fiction), but note their DiplomacyBrain can also *declare war*; if that ever fires
in testing, add `"diplomacyNegativeChance":{ "default":0.5 }` or accept `disableDiplomacy:true` as the
blunt fix (they keep the profile/intel either way).

### 3.4 MAJOR — decide + implement conquest protection (refugee colonies; Midan/Ashen homes)
Options, per market, cheapest first:
- **Protect:** set memory `$nex_uninvadable = true` on the market (works with zero Nex dependency —
  guard with `isModEnabled("Nexerelin")` or just set it unconditionally; vanilla ignores unknown keys).
  Natural home: the existing idempotent `applyLoadSafeFixes()` / `convertRefugeeColony()` path in
  `ExilesModPlugin`. Recommended baseline: **all four refugee markets** (they're narrative
  load-bearing pressure sources, and refugee "reconquest" is impossible by design) and — while
  `playableFaction:false` stands — the **Midan and Ashen econ-JSON markets** (their systems are quest
  and director anchors; one-way NPC conquest with no respawn silently deletes future content hooks).
- **Accept:** let the sector eat them (pure emergent chaos). If so, at minimum keep
  `corvus_abandoned_station` protected (player-start-adjacent, prologue-relevant) and note that
  refugee elimination kills the Phase-2 trust questline.
- Optional extra: if faction respawn gets enabled by players, add `exilerefugee` to
  `SectorManager.DO_NOT_RESPAWN_FACTIONS` from Exiles code (public static Set) so a wiped refugee
  faction never returns as an *invasion fleet*.

### 3.5 MINOR — small cleanups
1. **Asher patch:** make `applyAsherChangesOnce()` order-proof: also remove `orbitalstation_mid` (and
   skip adding `battlestation` if a bigger station is already present); optionally re-run the check from
   `onGameLoad` once so it wins regardless of plugin order. (finding #8)
2. **`player.json`:** add a comment documenting that `startShipsSolo` merges into Nex's generic solo
   start pool; once 3.1 lands, consider deleting the file (the custom start supersedes it) after
   verifying the array-merge behavior in game. (finding #9)
3. **CLAUDE.md:** fix the garbled `exerelineactioneonfig` path in §4 and retire the "is Nexerelin compat
   a goal?" open item (it is, per author 2026-07-06); point at this doc.
4. **Random sector:** state "unsupported" in the mod description/forum post; the `randomSector:-1` flag
   in 3.1 hides the start there. No code needed. (finding #10)
5. **Docs cross-link:** `EXILES_FACTION_RELATIONSHIP_MATRIX.md` should note which relations are pinned
   by Nex config caps once 3.2/3.3 land.

### 3.6 Phase-1 architectural note (no action now)
When the Sector Destabilization Director is designed, decide the division of labor vs Nexerelin:
either (a) director drives everything, Nex faction configs stay `playableFaction:false`/pacified — Nex
is scenery for exile factions; or (b) exile factions graduate to full Nex actors and the director only
*modulates* (relation nudges, event injection). Mixing both per-faction is fine (e.g. refugees stay
pacified forever; Midan graduates). Also note Nex diplomacy will happily end the Hegemony↔League war the
timeline needs — a `minRelationships`-style cap shipped from Exiles (merged into `hegemony.json` /
`persean.json`) is the lightweight pin if Phase 1 depends on that war (verify merge behavior of map
keys before relying on it).

---

## 4. Test checklist (for when fixes land)

1. New game via Nex NGC → "Exile" custom start appears, grants both ships, spawns at Xechas, officers +
   Thar/Daria colonies + vanilla patches all present; debt/stipend behavior as designed.
   **Current status 2026-07-06: appears but DISABLED ("failed to load") — expected while loose-script;
   blocked on a jar containing `ExilesNexStart` (see §2 custom-starts correction). Re-test after jar.**
2. Factions intel tab: refugees visible with diplomacy profile; Omicron hidden; Midan/Ashen visible
   (vanilla visibility) without Nex profiles.
3. Console (`runcode`) sanity: `NexConfig.getFactionConfig("exilemidan").disableDiplomacy` == true, etc.
4. Random-relations start: Midan still VENGEFUL/HOSTILE across the board.
5. Take a Hegemony commission → rep with Omicron/Midan/refugees unchanged.
6. Long-run soak (~2 cycles, dev speedup): no refugee invasion/raid/special-forces intel; no
   ceasefire/peace intel involving Midan; refugee colonies still refugee-owned (if protection chosen);
   kill Omicron patrols → no vengeance intel, no prisoners in loot.
7. With Nexerelin **disabled**: game still boots (Janino doesn't choke on `ExilesNexStart`), vanilla
   `ngcExileStart` path still works.

---

*Nex source references are to the `.java` files inside `Nexerelin/jars/ExerelinCore.jar` (line numbers
from 0.12.1d). Earlier audit language saying no changes had landed is historical; use the status block
at the top and `CLAUDE.md` for current state. Remaining decision boxes should be re-audited against the
live config during the outstanding-items review.*
