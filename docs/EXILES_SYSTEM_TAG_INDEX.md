# EXILES_SYSTEM_TAG_INDEX — vanilla star-system tags and what they actually do

> **DRAFT — 2026-07-06.** Research reference generated from the vanilla 0.98a-RC8 API source.
> Purpose: let the mod's custom systems (ExileXechas, ExileMidan, ExileAshenCrusade/Providence,
> ExileOmicron0, plus the three planned far-west Omicron systems) choose their tag loadouts
> **deliberately** instead of inheriting surprises — like the Inactive Gate and the Midan pirate
> base did. §3 loadouts are **PROPOSALS for Sean to ratify, not decisions.**
>
> Citation root: `API/` = `D:\Fractal Softworks\Starsector\starsector-core\starfarer.api\com\fs\starfarer\api\`.
> All tag constants live in `API/impl/campaign/ids/Tags.java` (line numbers per constant given below).
> Line numbers verified against this install on 2026-07-06.

---

## 0. Two lifecycle facts that shape everything

These two mechanisms explain most tag "surprises" and both are load-bearing for §2/§3:

1. **Procgen theme passes run AFTER mod `onNewGame` system creation** (empirically established
   by the Omicron gate incident) **but BEFORE tags are "final."** Most `MiscellaneousThemeGenerator`
   passes (red planet, PK system, coronal taps, solar shades, LOCR planets) iterate
   `context.constellations` — **procgen constellations only, mod systems can never be picked**
   (e.g. `API/impl/campaign/procgen/themes/MiscellaneousThemeGenerator.java:184`, `:604`, `:767`, `:1118`).
   The exception is **`addExtraGates`**, which iterates `Global.getSector().getStarSystems()` —
   **every real system, including mod ones** (`MiscellaneousThemeGenerator.java:530`). See §4.1.

2. **Any system whose tag set is still EMPTY after new-game gen is auto-tagged as core.**
   `CoreLifecyclePluginImpl.updateKnownPlanets()` (`API/impl/campaign/CoreLifecyclePluginImpl.java:1154-1167`,
   called from `onNewGameAfterEconomyLoad` at `:1007`): empty-tag system → `THEME_CORE` +
   (`THEME_CORE_POPULATED` if it has markets, else `THEME_CORE_UNPOPULATED`).
   **"No tags" is not a neutral choice** — it means "become a core-theme system," with everything
   that implies (§1, Core family). This is currently the de-facto state of ExileXechas, ExileMidan,
   and Providence (only ExileOmicron0 sets system tags — `data/scripts/world/systems/ExileOmicron0.java:71-73`).

Also universally relevant: **a system containing any visible market is skipped by the pirate-base,
Pather-base, and person-bounty pickers regardless of tags** (`PirateBaseManager.java:219`,
`LuddicPathBaseManager.java:307`, `PersonBountyIntel.java:201-205`).

---

## 1. System-tag index

Only system-level tags with real consumers are listed. Entity-level tags (`GATE`, `STABLE_LOCATION`,
`OBJECTIVE`, `BEACON_*`, `SALVAGEABLE`, …) are out of scope except where a system picker counts them.

### 1.1 Core family

| Tag (string) | Tags.java | Who reads it | Consequence if present |
|---|---|---|---|
| `THEME_CORE` (`theme_core`) | :142 | `HubMissionWithSearch.java:1064-1071` (`requireSystemInterestingAndNotCore` / `...NotUnsafeOrCore`); GA Academy missions (`GACelestialObject.java:62`, `GAProbePackage.java:67`, `GATransverseJump.java:59`, `GAFindingCoureuse.java:99`, `GAProjectZiggurat.java:105`); bounty creators (`cb/CBRemnant.java:42`, `cb/CBRemnantPlus.java:70`, `cb/CBDerelict.java:42`); `missions/luddic/BornanewFilesFalseIdols.java:134` | **Excluded** from nearly all randomly-generated mission target searches. |
| | | `MusicPlayerPluginImpl.java:190` | Core campaign music instead of exploration music. |
| | | `velfield/SlipstreamManager.java:880-887` | A 4000-unit slipstream **blocker** is placed on the system — slipstreams route around it. |
| | | `util/Misc.java:6463-6482` (`computeCoreWorldsExtent`, recomputed at `CoreScript.java:135`) | System's hyperspace position **widens the "core worlds" bounding box** (`$coreWorldsMin/Max/Center`). Consumers: `enc/BaseEPEncounterCreator.java:118-120` and `ghosts/GhostFrequencies.java:132-134` scale abyssal-encounter/ghost frequency by distance from this box. **Tagging a far-flung system THEME_CORE distorts the whole Sector's encounter geography.** |
| | | `campaign/listeners/CoreDiscoverEntityPlugin.java:168` | No "Salvors Tally" intel ("salvors likely to arrive") for discoveries in the system. |
| `THEME_CORE_POPULATED` (`theme_core_populated`) | :141 | `rulecmd/missions/GateCMD.java:73-80` | Gate dialog treats the system as populated (affects gate-network UI/behavior). |
| | | `missions/DelayedFleetEncounter.java:307` | Markets here count as "core markets" for encounter-proximity math. |
| | | Historian & scientist bar events skip these systems (`historian/BaseHistorianOfferCreator.java:46,72,93`; `ScientistAICoreBarEvent.java:57`) | Never offered as exploration-offer targets. |
| `THEME_CORE_UNPOPULATED` (`theme_core_unpopulated`) | :140 | `PirateBaseManager.java:208-211` (weight hard-coded to **0**); `LuddicPathBaseManager.java:297-299` (weight **1**); `PersonBountyIntel.java:197` (weight **1**) | Immune to vanilla pirate bases; low-priority candidate for **Pather bases** and **person-bounty hideouts** (only if marketless + has OBJECTIVE/STABLE_LOCATION entities, see 1.5). |

### 1.2 Content-theme family (misc / ruins / derelict / interesting)

These are what procgen uses to mean "this system contains generated salvage content."
Putting them on a hand-built system **invites** vanilla content pickers in.

| Tag (string) | Tags.java | Who reads it | Consequence if present |
|---|---|---|---|
| `THEME_INTERESTING` (`theme_interesting`) / `THEME_INTERESTING_MINOR` (`theme_interesting_minor`) | :151, :158 | `HubMissionWithSearch.java:1066,1070,1073` (`preferSystemInteresting`); `historian/BaseHistorianOfferCreator.java:74-75, 97-98` | **Preferred** (not required) target for random missions and historian offers. The Tags.java javadoc warning is explicit: *"Should ONLY be used where it's acceptable that this may happen."* |
| `THEME_MISC` (`theme_misc`) | :160 | Base/bounty pickers: `PirateBaseManager.java:200` (w=3), `LuddicPathBaseManager.java:291` (w=3), `PlayerRelatedPirateBaseManager.java:279`, `PersonBountyIntel.java:189` (w=3); distress-call themes `events/nearby/NearbyEventsEvent.java:194`; mission requires `RuinsDataSwapMission.java:57`, `RaidSecretOutpostMission.java:59`, GA missions (`GATransverseJump.java:58`, `GAFindingCoureuse.java:98`, etc.) | Marks "generic procgen salvage system." Eligible/weighted for pirate & Pather bases, bounty hideouts, distress-call events, and several mission families. |
| `THEME_MISC_SKIP` (`theme_misc_skip`) | :161 | Same consumers, lower weight (w=1 in base/bounty pickers; still counts as a valid distress/mission theme) | Vanilla meaning: "misc pass considered this system and added nothing." **Not** an opt-out tag — it still qualifies the system for bases/bounties/missions at reduced weight. |
| `THEME_RUINS` / `_MAIN` / `_SECONDARY` (`theme_ruins*`) | :163-165 | Set by `RuinsThemeGenerator.java:97-99,116-118`. Read: base/bounty pickers at **highest weight 5** (`PirateBaseManager.java:206`, `LuddicPathBaseManager.java:295`, `PersonBountyIntel.java:193`); distress themes (`NearbyEventsEvent.java:196`); `AnalyzeEntityIntelCreator.java:36`; historian (`BaseHistorianOfferCreator.java:50`); `enc/BaseEPEncounterCreator.java:156` (`_MAIN`) | "Domain ruins here." Top-weighted candidate for pirate/Pather bases and bounty hideouts; qualifies for analyze-entity missions and historian offers. |
| `THEME_DERELICT` + `_MOTHERSHIP` / `_CRYOSLEEPER` / `_SURVEY_SHIP` / `_PROBES` (`theme_derelict*`) | :173-177 | Set by `DerelictThemeGenerator`. Read: `AnalyzeEntityIntelCreator.java:32-45` (mothership/cryosleeper systems excluded; probes weighted up); distress themes (`NearbyEventsEvent.java:200`); mission requires (`RuinsDataSwapMission.java:57`, `RaidSecretOutpostMission.java:59`, `GADataFromRuins.java:64`); misc-theme passes treat DERELICT systems specially (`MiscellaneousThemeGenerator.java:127-135, 776-781`) | "Derelict-faction content here." Similar exposure to RUINS but through the analyze/derelict mission families. |

### 1.3 Remnant family

Set by `RemnantThemeGenerator.java:168-234`. **Crucial:** vanilla Remnant fleets are spawned by
`RemnantSeededFleetManager` / `RemnantStationFleetManager` **scripts attached at procgen**
(`RemnantThemeGenerator.java:183-207, 229-231`) — **the tags themselves spawn nothing.** On a mod
system they only change how pickers, salvage, and events treat it.

| Tag (string) | Tags.java | Who reads it | Consequence if present |
|---|---|---|---|
| `THEME_REMNANT` (`theme_remnant`) | :182 | `SalvageSpecialAssigner.java:111-114, 246-249` | Derelict-ship recovery / sleeper-pod officer factions in-system become TriTach 10 / Hegemony 7 / Indep 3 instead of nearby factions. |
| | | `historian/BaseHistorianOfferCreator.java:51`; `GADataFromRuins.java:113`; `GADerelictArtifact.java:99` | Counts as a valid "interesting salvage" theme for historian offers and GA mission flavor. |
| | | `MiscellaneousThemeGenerator.java:152-154, 609` (procgen-only passes) | (Procgen constellations only — no effect on mod systems.) |
| `THEME_REMNANT_MAIN` (`theme_remnant_main`) | :183 | `MiscellaneousThemeGenerator.java:1333-1349` (`getRemnantStations`) and `cb/CBRemnantStation.java:152-153` | Scanned for **faction-`remnant` station-mode fleets**. Omicron stations are faction `exileomicron`, so they don't match — no effect *today*, but this is the hook that lights up if any station in a `_MAIN` system is ever faction `remnant`. |
| | | `cb/CBRemnant.java:49-50` | Difficulty-9 Remnant bar bounties **prefer** `_MAIN` (non-`_DESTROYED`) systems → **a vanilla Remnant bounty fleet can be spawned into the system.** |
| | | `ScientistAICoreBarEvent.java:58` and `BaseHistorianOfferCreator.java:94` | Excluded as a *destination* for those bar-event rewards (too dangerous). |
| `THEME_REMNANT_SECONDARY` (`theme_remnant_secondary`) | :184 | `GADataFromRuins.java:65`, `GADerelictArtifact.java:70`, `BasicExampleGADataFromRuins.java:47` (all `NOT_ANY`) | Marks the *weak* remnant systems; GA "data from ruins" missions exclude them. |
| `THEME_REMNANT_DESTROYED` / `_SUPPRESSED` / `_RESURGENT` (`theme_remnant_destroyed/suppressed/resurgent`) | :186-188 | Distress themes allow `_SUPPRESSED/_DESTROYED/_NO_FLEETS` (`NearbyEventsEvent.java:197-199`); base/bounty pickers weight `_NO_FLEETS`/`_DESTROYED` at 3 (`PirateBaseManager.java:202-205`, `PersonBountyIntel.java:191-196`, `LuddicPathBaseManager.java:293`); `SalvageSpecialAssigner.java:262-265` (`_SUPPRESSED`/`_RESURGENT` → salvage **trap fleets are faction `remnant`** instead of pirates); mission requires (`RuinsDataSwapMission.java:57`); `enc/OutsideSystemRemnantEPEC.java:35,113` (`_SUPPRESSED` enables an out-of-system Remnant encounter); GA missions require `ANY` of `_RESURGENT/_SUPPRESSED` (`GADataFromRuins.java:63`) | The "state" sub-tags. Vanilla always pairs exactly one of them with `THEME_REMNANT`. **ExileOmicron0 currently has none of them** — it's a Remnant-main system with no declared state, a combination vanilla never produces; most state-keyed consumers simply don't fire. |
| `THEME_REMNANT_NO_FLEETS` (`theme_remnant_no_fleets`) | :185 | Base/bounty pickers (weight 3, see above); `MiscellaneousThemeGenerator.java:784` (procgen PK pass) | Means "remnant-flavored but empty." **Do not** put on a defended system — it *advertises* the system to pirate/Pather base and bounty pickers. |

### 1.4 Safety / exclusion markers

| Tag (string) | Tags.java | Who reads it | Consequence if present |
|---|---|---|---|
| `THEME_UNSAFE` (`theme_unsafe`) | :181 ("relatively powerful fleets that may interfere with missions") | `HubMissionWithSearch.java:1068-1071`; GA missions (`GACelestialObject.java:62`, `GADerelictArtifact.java:121`, `GAFindingCoureuse.java:99`, `GAProbePackage.java:67`, `GATransverseJump.java:59`, `GAProjectZiggurat.java:105`); `BornanewFilesFalseIdols.java:134`; `cb/CBMercUW.java:20`; `GAAtTheGates.java:728` (skips the gate-scan ambush encounter); procgen PK pass excludes (`MiscellaneousThemeGenerator.java:785-787`) | **Pure opt-out marker with zero spawning consequences.** The single best tag for "defended fortress system — keep vanilla missions out." Vanilla sets it on live Remnant-main systems (`RemnantThemeGenerator.java:171`) and the TT black site (`world/TTBlackSite.java:154`). |
| `THEME_SPECIAL` (`theme_special`) | :171 ("should not be picked again for something special") | `PirateBaseManager.java:190` and `LuddicPathBaseManager.java:280` (**hard skip** — the only tag-level base veto besides `THEME_HIDDEN`); `RuinsDataSwapMission.java:58`, `RaidSecretOutpostMission.java:60`; every `MiscellaneousThemeGenerator` special pass (`:186, :606, :769, :1121, :1200, :1247, :1297`); `world/NamelessRock.java:170` | "Already used for something unique — hands off." Cheap insurance for story-relevant mod systems. Vanilla pairs it with `THEME_HIDDEN` on all its hand-built story systems (`TTBlackSite.java:156`, `Limbo.java:38`, `NamelessRock.java:47`, `GateHaulerLocation.java:42`). |
| `THEME_HIDDEN` (`theme_hidden`) | :138 | Hard skip in: `PirateBaseManager.java:191`, `LuddicPathBaseManager.java:281`, `NearbyEventsEvent.java:221` (distress calls), `AnalyzeEntityIntelCreator.java:53`, `SurveyPlanetIntelCreator.java:72`, `ScientistAICoreBarEvent.java:60`, `SalvageSpecialAssigner.java:910`, `DerelictThemeGenerator.java:474`, `GateHaulerCMD.java:202`, `HubMissionWithSearch.java:1535` (`requireSystemNotHidden`) | The strongest "no vanilla content, ever" marker. Doesn't hide the system from the player — it hides it from *content generators*. Meant for secret story systems; too heavy for a normal faction home. |
| `SYSTEM_ALREADY_USED_FOR_STORY` (`system_already_used_for_story`) | :396 | `HubMissionWithSearch.java:1243-1245` (`requireSystemNotAlreadyUsedForStory`, set by missions via `:1252-1255`); GA story missions (`GAFindingCoureuse.java:99`, `GAProjectZiggurat.java:105`, `BornanewFilesFalseIdols.java:134`); set by the PK setup (`MiscellaneousThemeGenerator.java:847`) | Keeps vanilla *story* missions (Academy chain etc.) from staging their set-pieces in the system. Pre-tagging mod story systems with it is legitimate. |
| `NOT_RANDOM_MISSION_TARGET` (`not_random_mission_target`) | :403 | **Entity-level**, listed because it's the per-planet/per-market companion: `HubMissionWithSearch.java:1567,1574,1690,1700,1804,1807`; `SurveyPlanetIntelCreator.java:67`; `AnalyzeEntityIntelCreator.java:50`; `SalvorsTallyIntel.java:228`; `BaseHistorianOfferCreator.java:58` | Put on individual planets/entities/markets to exempt them from random survey/analyze/mission targeting without tagging the whole system. |

### 1.5 Base/bounty picker mechanics (summary)

`PirateBaseManager.pickSystemForPirateBase` (`API/impl/campaign/intel/bases/PirateBaseManager.java:184-243`),
`LuddicPathBaseManager.pickSystemForLPBase` (`LuddicPathBaseManager.java:275-330`) and
`PersonBountyIntel.pickHideoutLocation` (`PersonBountyIntel.java:180-228`) all iterate **all real systems** and require:

- not `THEME_SPECIAL`, not `THEME_HIDDEN` (base managers; bounty only skips pulsars),
- a **positive tag weight**: `THEME_MISC_SKIP`=1, `THEME_MISC`=3, `THEME_REMNANT_NO_FLEETS`=3,
  `THEME_REMNANT_DESTROYED`=3 (pirate/bounty), `THEME_RUINS`=5, `THEME_CORE_UNPOPULATED`=0 (pirate) / 1 (Pather, bounty),
- **zero (visible) markets** in-system (`PirateBaseManager.java:219`; bounty: `PersonBountyIntel.java:201-205`),
- base managers additionally require ≥1 `OBJECTIVE`/`STABLE_LOCATION` entity (`PirateBaseManager.java:214-216`)
  — which **all four Exiles systems have** (relays/buoys/arrays, e.g. `data/scripts/world/systems/ExileMidan.java:115-134`),
- player hasn't visited within 45 days; not recently used for a base.

Consequence: a marketless mod system tagged `THEME_MISC`/`THEME_RUINS`/`_NO_FLEETS`/`_DESTROYED`
(or auto-tagged `THEME_CORE_UNPOPULATED`, for Pather/bounty) **will eventually host vanilla bases and
bounty hideouts.** The Omicron loadout (`REMNANT`+`REMNANT_MAIN`+`UNSAFE`) carries **weight 0** — safe.

### 1.6 Hard system-behavior flags

| Tag (string) | Tags.java | Who reads it | Consequence if present |
|---|---|---|---|
| `SYSTEM_CUT_OFF_FROM_HYPER` (`system_cut_off_from_hyper`) | :133 | `CoreCampaignPluginImpl.java:207-210` (sets `$systemCutOffFromHyper` for rules); `NearbyEventsEvent.java:220`; economy-fleet spawners skip markets in such systems (`LuddicPathFleetManager.java:142`, `PirateFleetManager.java:145`, `MercFleetManagerV2.java:155,188`, `MiscPilgrimFleetCreator.java:148,183`, `MiscAcademyFleetCreator.java:54,128`); added/removed by `GateExplosionScript.java:54,82` and `TutorialMissionIntel.java:488` (Galatia tutorial) | "Sealed system" (tutorial-Galatia style). NPC fleet ecosystems refuse to route there. Do not use casually. |
| `SYSTEM_ABYSSAL` (`system_abyssal`) | :134 | `MusicPlayerPluginImpl.java:187` (abyssal music); `DistressCallAbility.java:253,307` (ability unusable); `DisposableFleetManager.java:219` (markets there don't seed disposable fleets); `world/NamelessRock.java:172`; set by `SectorProcGen.java:241` and abyss encounters | Marks deep-abyss systems. Only relevant if Exiles ever does Abyss content. |
| `SYSTEM_CAN_SPAWN_THREAT` (`system_can_spawn_threat`) | :136 | `impl/combat/threat/DisposableThreatFleetManager.java:141-151` | The nearest tagged system within range of the player **actively spawns [REDACTED] Threat fleets**. Never tag an Exiles system with this unless that is the explicit intent. |
| `PK_SYSTEM` (`pk_system`) | :132 | Set at `MiscellaneousThemeGenerator.java:848`; no other API-source reader (story/rules use the `$` memory keys set at `:850-851`) | Vanilla endgame story marker. Don't touch. |
| `HAS_CORONAL_TAP` (`has_coronal_tap`) | :179 | Set at `MiscellaneousThemeGenerator.java:747`; read by `cb/CBRemnant.java:52` and `cb/CBRemnantPlus.java:71` (bounties prefer tap systems) | Only add if the system genuinely contains a coronal tap entity; attracts high-end bounty placement. |
| `NO_TOPOGRAPHY_SCANS` (`no_topography_scans`) | :130 | `intel/events/ht/HTFactorTracker.java:132-133` | Sensor bursts in the system yield no hyperspace-topography points. Niche anti-exploit lever for reward-dense mod systems. |
| `DO_NOT_SHOW_STRANDED_DIALOG` (`do_not_show_stranded_dialog`) | :144 | `enc/StrandedGiveTJScript.java:31` | Suppresses the pity "stranded, here's a transverse-jump" rescue in the system. |
| `DO_NOT_RESPAWN_PLAYER_IN` (`do_not_respawn_player_in`) | :211 | **No consumer in the API source** — consumed by obfuscated core code (player respawn placement). | Belt-and-braces on hostile fortress systems so a defeated player never respawns inside one. Cheap to add, cannot be verified from API source. |

---

## 2. Current Exiles state (for reference)

| System | Tags set in code | Effective tags after new game |
|---|---|---|
| ExileXechas (start) | none | auto `THEME_CORE` + `THEME_CORE_POPULATED` (has markets) — §0.2 |
| ExileMidan | none | auto `THEME_CORE` + `_POPULATED`/`_UNPOPULATED` depending on markets |
| ExileAshenCrusade (Providence) | none | auto `THEME_CORE` + `_POPULATED` (Ashen markets) |
| ExileOmicron0 | `THEME_REMNANT`, `THEME_REMNANT_MAIN`, `THEME_UNSAFE` (`ExileOmicron0.java:71-73`) | as set (never empty → no auto-core) |

Hyperspace positions: Xechas (-7200, -29500) (`ExileXechas.java:35`), Omicron0 (-700, 0)
(`ExileOmicron0.java:67`), Providence (3000, 2500) (`ExileAshenCrusade.java:33`); **ExileMidan sets
no location in Java** — verify where it actually lands before trusting any distance-based reasoning.

Note the auto-core wrinkle: Xechas at (-7200, -29500) is far south of the vanilla core, and it (plus
Midan/Providence) being `THEME_CORE` **stretches the `computeCoreWorldsExtent` box** (§1.1) — probably
harmless at these coordinates, but the three planned **far-west** Omicron systems must NOT be left
untagged, or they'd drag the core box across the entire map.

---

## 3. Recommended tag loadouts — **PROPOSALS ONLY (Sean to ratify)**

| System | Proposed loadout | One-line rationale |
|---|---|---|
| **ExileXechas** (start, player-adjacent, has markets) | *(keep empty → auto `THEME_CORE`/`_POPULATED`)* + consider explicit `THEME_CORE`+`THEME_CORE_POPULATED` for determinism | Core treatment is exactly right for the start system: excluded from random-mission targeting, core music, no gate spread (empty during procgen), markets already block bases/bounties. Setting the tags explicitly merely removes the dependence on the auto-tagger. |
| **ExileMidan** (WIP faction home) | `THEME_CORE` + `THEME_CORE_POPULATED` (explicit), + `THEME_SPECIAL` if it will host quest content | Same protection as Xechas; `_POPULATED` (not `_UNPOPULATED`) matters — `_UNPOPULATED` gives Pather-base weight 1 and bounty weight 1 if the system is ever marketless (`LuddicPathBaseManager.java:297`, `PersonBountyIntel.java:197`). |
| **Providence / ExileAshenCrusade** | `THEME_CORE` + `THEME_CORE_POPULATED` (explicit), + `THEME_SPECIAL` | Faction home with markets; explicit core tags + the story-veto marker keep vanilla missions and any future marketless window from biting. |
| **ExileOmicron0** (fortress, gate KEPT) | Keep `THEME_REMNANT` + `THEME_REMNANT_MAIN` + `THEME_UNSAFE`; **add** `THEME_SPECIAL`, and consider `SYSTEM_ALREADY_USED_FOR_STORY` + `DO_NOT_RESPAWN_PLAYER_IN` | Current trio is already weight-0 for bases and opt-out for missions; `THEME_SPECIAL` is the explicit base/story veto. Known residual: difficulty-9 `CBRemnant` bar bounties *prefer* `_MAIN` systems (`CBRemnant.java:49`) → a vanilla-Remnant bounty fleet may appear in-system (arguably fine flavor next to Omicron). Decide whether to also add a state tag (`THEME_REMNANT_RESURGENT` reads as "live, dangerous" and only affects salvage trap-faction + GA data-from-ruins eligibility, `SalvageSpecialAssigner.java:262-265`, `GADataFromRuins.java:63`). |
| **3 planned far-west Omicron systems** | `THEME_REMNANT` + `THEME_REMNANT_MAIN` + `THEME_UNSAFE` + `THEME_SPECIAL` (+ optional state tag, + `DO_NOT_RESPAWN_PLAYER_IN`) | Same fortress profile as Omicron0. **Do not leave untagged** (auto-`THEME_CORE` would wreck the core-extent box from the far west, §1.1/§2). Gate exposure: each will be gate-eligible (tagged + presumably ≥3 planets); see §4.3 for the (non-)options. |
| **Refugee holdings (Corvus)** | n/a — vanilla system, already core-tagged | No action. |

Cross-cutting proposal: keep using **entity-level** `NOT_RANDOM_MISSION_TARGET` on any bespoke
planet/station that vanilla survey/analyze/salvage pickers must never touch (§1.4), rather than
escalating whole systems to `THEME_HIDDEN`.

---

## 4. Known gotchas (the three incidents, with receipts)

### 4.1 Gate spreading reaches mod systems (Omicron got an Inactive Gate — KEPT)

`MiscellaneousThemeGenerator.addExtraGates` (`API/impl/campaign/procgen/themes/MiscellaneousThemeGenerator.java:519-594`):

- Iterates **`Global.getSector().getStarSystems()`** — all real systems, mod systems included (`:530`),
  unlike the other Misc passes which only see procgen constellations (§0.1).
- Skips only: **empty-tag systems** and Galatia (`:536-539`), and systems with **< 3 planets** (`:562`).
- The `THEME_CORE` veto is **commented out** — `//if (system.hasTag(Tags.THEME_CORE)) continue; // this isn't set yet` (`:534`) —
  because core tags are only assigned later (§0.2). **There is no working tag-based gate veto.**
- Gate count: `MIN/MAX_GATES` from settings `minNonCoreGatesInSector`/`maxNonCoreGatesInSector` (`:80-82`, used `:544-545`).
- Placement: for each gate to add, picks **the system farthest (in LY) from every existing gate** (`:558-573`)
  and drops an **Inactive Gate plus salvage/derelict dressing** via `addInactiveGate(data, 1f, 0.5f, 0.5f, factions)` (`:577-580`).
- ExileOmicron0 (tagged, 3+ planets, far from gates) won one. **Author decision: KEEP it** — the tag
  loadouts in §3 deliberately do not try to strip it.

### 4.2 A mod faction got vanilla pirate bases (Midan)

`PirateBaseManager.pickPirateFaction` (`API/impl/campaign/intel/bases/PirateBaseManager.java:94-102`)
builds its faction pool from **every faction whose `.faction` `custom{}` block has
`makesPirateBases:true`** (`Factions.CUSTOM_MAKES_PIRATE_BASES`, `API/impl/campaign/ids/Factions.java:64`).
`data/world/factions/exilemidan.faction:250` sets `"makesPirateBases":true` → vanilla spawns
**Midan-flavored pirate bases** on its own schedule, in systems picked by the tag-weighted logic in §1.5.
This is a **faction flag, not a system tag** — no tag loadout prevents it. (Ashen has the flag present
but commented out, `exileashencrusade.faction:346`; Omicron only sets `pirateBehavior`, which does not
feed this picker.) Whether the flag stays on Midan is a design decision (it *does* fit "forward supply
bases"), but it should be a decision, not an inheritance.

### 4.3 There is no "no gates" veto tag

Consequence of 4.1: the only states that dodge the gate pass are **empty tag set** (`:536`) or
**< 3 planets** (`:562`) at procgen time — and the empty-tag route immediately converts into
auto-`THEME_CORE` afterwards (§0.2) with its own side effects (core-extent skew, slipstream blocking).
Practical mitigations for the planned far-west systems, none of them clean:

1. **Accept the gate** (Omicron0 precedent) — a gate in a hostile fortress is arguably content.
2. **Pre-place a gate yourself** at build time: a system that already contains a `Tags.GATE` entity has
   min-distance 0 in the farthest-from-any-gate scan (`:564-568`) and will never be selected — this is
   the only deterministic, side-effect-free veto.
3. **Remove the spawned gate in `onNewGameAfterEconomyLoad`** — works, but fights the generator and can
   just shift the extra gate into a neighboring system on the next pick iteration (the loop adds
   gates one at a time using the updated gate list, `:558-582`).
4. Tag-timing tricks (adding tags only after procgen) are **not recommended**: they race the
   auto-core tagger in the same lifecycle phase (§0.2) and depend on plugin call order.

### 4.4 Procgen constellations actively AVOID custom systems (the "moat", confirmed 2026-07-06)

Sean noticed a persistent gap between the Omicron cluster and the random starfield — it is a
real mechanic, not chance. `SectorProcGen.generate()` (`API/impl/campaign/procgen/SectorProcGen.java:87-102`)
places random constellations on a **2000-su cell grid** (`CELL_SIZE`, `:29`) and, before picking
anchors, calls `blotOut(cells, x, y, 8)` for **every already-existing star system** — mod systems
included, since Exiles generates in `onNewGame`, before procgen. That forbids a 9×9-cell block
(**±8,000 su ≈ 4 LY**) around each custom system; placed constellations blot a further 11×11 around
themselves (`CONSTELLATION_CELLS`, `:30-31`). Clustered systems (the far-west Omicron four) merge
their moats into one large procgen void.

Implications: (a) position tuning can never tuck a custom system "into" a starfield — the void
follows it; (b) for the hidden fortress region the void is arguably flavor (observant players can
find Omicron by the suspicious emptiness); (c) the only way to remove the moat is generating
systems in `onNewGameAfterProcGen` — NOT recommended (overlap risk with random constellations, and
those systems then also skip the gate pass and every other procgen theme interaction).

---

*Sources: vanilla 0.98a-RC8 API source under `API/` (see header); Exiles mod source under
`data/scripts/world/systems/` and `data/world/factions/`. Nothing in this document changes code;
§3 is a proposal sheet awaiting ratification.*
