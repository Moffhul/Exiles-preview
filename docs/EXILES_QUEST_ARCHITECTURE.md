# EXILES QUEST ARCHITECTURE — machinery map + authoring workflow

**Status: DRAFT — agent deliverable 2026-07-06, for Sean's review; scaffold NOT built yet.**

Purpose: make "quest plan → in-game quest" a bounded, mechanical task instead of a painful
file-type ping-pong. Part I maps the vanilla machinery (with citations). Part II is the
streamlining: anatomy, scaffold proposal, spec template, authoring checklist, pilot pick.

Path conventions: `api:` = relative to `D:\Fractal Softworks\Starsector\starsector-core\starfarer.api\com\fs\starfarer\api\`;
`core:` = relative to `D:\Fractal Softworks\Starsector\starsector-core\`;
`mod:` = repo-relative. Line numbers marked `≈` were reported by exploration agents and are
approximate; unmarked line numbers were verified directly.

---

## 0. Executive summary — the five findings that shape everything

1. **Vanilla already ships the "reusable dialog + intel + persistent-flag scaffold" CLAUDE.md §6
   asks for.** `BaseHubMission extends BaseIntelPlugin` (api:impl/campaign/missions/hub/BaseHubMission.java ≈94):
   a hub mission IS an intel entry, has a declarative stage graph driven by memory flags, handles
   credit/rep/XP payouts, time limits, and abandon logic. We should build a *thin* Exiles base class
   on top of it — not a bespoke framework.
2. **The whole pipeline is loose-script-safe.** The one hard constraint (custom `rules.csv`
   *command classes* crash at rule-load time) is never hit: mission dialog uses only vanilla
   commands (`FireBest`, `Call`, `BarCMD`, `AcceptMission`, memory assignments), and the
   mission's Java gets invoked *from* rules via the vanilla `Call $ref <method>` command routing
   into the mission's own `callAction()`/`callEvent()` hooks. Custom Java never has to appear in a
   rules script column.
3. **Bar hooks are one CSV row.** `core:data/campaign/bar_events.csv` maps an id + frequency
   numbers to a plugin class resolved via `Global.getSettings().getInstanceOfScript(pluginClass)`
   (api:loading/BarEventSpec.java:62–77) — the *same* script-classloader seam the mod already uses
   for its loose-script industry plugins in `mod:data/campaign/industries.csv` (plugin column).
   Resolution happens at campaign load, **not** at application-start rules parse, so the
   rules-command crash mode does not apply (verify item V1).
4. **Extending `HubMissionWithBarEvent` from a Janino loose script looks safe** — no abstract
   generics, stage ids are plain `Object`, and the mod's `OmicronRaidIntel extends RaidIntel`
   (mod:data\scripts\exiles\OmicronRaidIntel.java:51) proves deep vanilla-class extension compiles.
   One adaptation: vanilla missions declare stage `enum`s; the scaffold uses **`static final String`
   stage ids** instead (enum *declarations* under Janino 2.7.8 are unverified — V2; the game ships
   `core:janino.jar`, manifest `Implementation-Version: 2.7.8`).
5. **The tedium is real but small and fixed:** a typical quest touches exactly **one Java file +
   ~4–8 rules.csv rows + 1 bar_events.csv row** (+ optional strings/portrait entries). The
   back-and-forth is tamed by making the rules rows formulaic (§II-A template rows) and by the
   spec template (§II-C) that enumerates every row before any file is opened.

**Pilot recommendation (§II-E): the Tri-Tachyon "engage Omicron ships for data" quest.** Smallest
surface, and every hook it needs on the Director side already exists and is documented in
`mod:docs/war-capacity-design.md` §6.

---

# PART I — THE VANILLA MACHINERY

## 1. The hub-mission framework

Class chain (each file in api:impl/campaign/missions/hub/):

```
BaseIntelPlugin                                  (intel entry: shows in Intel screen, map icons)
  └─ BaseHubMission implements HubMission        (stage graph, flags, rewards, lifecycle)
       └─ HubMissionWithTriggers                 (declarative "when stage X starts, do Y" DSL)
            └─ HubMissionWithSearch              (market/person/system/planet pickers + requirements)
                 └─ HubMissionWithBarEvent       (doubles as a portside-bar event)
```

### 1.1 Lifecycle (BaseHubMission.java)

- **Creation:** `createAndAbortIfFailed(MarketAPI createdAt, boolean barEvent)` (≈705) calls the
  one **abstract method a subclass must implement**:
  `protected boolean create(MarketAPI createdAt, boolean barEvent)` (≈714). Return false = the
  mission can't be offered here (bad market, no valid target, etc.); everything it did is rolled
  back (the framework tracks an `Abortable` change list, ≈604).
- **Offer → accept:** `accept(InteractionDialogAPI, Map<String,MemoryAPI>)` (≈812) finalizes,
  adds the mission to the intel manager (≈838), calls the `acceptImpl(...)` hook (≈846).
- **Stage graph:** `currentStage` is an `Object` key into `LinkedHashMap<Object, StageData> stages`
  (≈595–596). You declare transitions, then the framework advances the mission for you:
  - `setStartingStage(Object)` (≈1554)
  - `setSuccessStage(Object)` / `addSuccessStages(Object...)`, `setFailureStage(Object)` /
    `addFailureStages(Object...)`, `setNoPenaltyFailureStage(...)` (≈1428–1454)
  - `setAbandonStage(Object)` / `setNoAbandon()` (≈2387–2390)
  - **Flag-driven transitions** (the core mechanism):
    `setStageOnMemoryFlag(Object to, HasMemory withMemory, String flag)` (≈2567),
    `setStageOnGlobalFlag(Object to, String flag)` (≈2535),
    `connectWithMemoryFlag(Object from, Object to, MemoryAPI memory, String flag)` (≈2570)
  - Other built-in transition sources: entity-not-alive, market-decivilized, hostilities
    started/ended, days-elapsed, in-range-of-comm-relay, entered-location, in-range-of-entity,
    within-hyperspace-range, and a fully custom `ConditionChecker` (≈2535–2630).
  - `setTimeLimit(Object failureStage, float days, ...)` for deadlines.
- **End:** `endSuccess(dialog, memoryMap)` / `endFailure(...)` / `endAbandon()` (≈1263–1378), each
  with an overrideable `...Impl` hook (≈1382–1386). Rewards declared once are paid automatically:
  `setCreditReward(int min, int max)` (≈1854) or the `CreditReward` tier enum,
  `setXPReward(int)` (≈1908), and the reputation set:
  `setRepChanges(rewardPerson, penaltyPerson, rewardFaction, penaltyFaction)` (≈1973),
  tier helpers `setRepPersonChangesLow()`…`setRepFactionChangesVeryHigh()` (≈1914–1971),
  `setNoRepChanges()`, plus penalty clamps `setPenaltyLimitFaction(RepLevel)` (≈2000–2004).
- **Intel display hooks** a subclass typically overrides:
  `addDescriptionForNonEndStage(TooltipMakerAPI, w, h)` (≈743),
  `addDescriptionForCurrentStage(...)` (≈746), `getNextStepText()` (≈768),
  `getBaseName()` (abstract in practice — every vanilla mission provides it),
  `getMissionTypeNoun()` (≈2466, default "mission").
- **Rules bridge:** `callEvent(...)`/`callAction(String action, String ruleId, dialog, params, memoryMap)`
  (≈885–909) — this is what the vanilla `Call $ref <method> [args]` rule command invokes. Built-in
  actions already handled for you: `accept`, `endSuccess`, `endFailure`, `updateData`,
  `addContacts`, `repSuccess`, `repFailure` (BaseHubMission ≈909; see the generic rules rows in §3.3).
  Override `callAction` for quest-specific verbs callable from rules.csv **with zero custom rule
  commands**.

### 1.2 Save-game behavior (XStream) — gotchas

Serialized: the stage graph, triggers, flag list, rewards (BaseHubMission ≈595–650). Transient
(gone after save/load): `startingStage`, `cargoOnAccept`, `genRandom`, the `giver*` config strings
(≈607–620, ≈2935). Rule of thumb for the scaffold: **anything a mission needs after accept() must
live in a non-transient field or in memory flags** — same discipline the mod already applies in
`OmicronRaidIntel` (named static inner classes, raw `List` fields with casts,
mod:data\scripts\exiles\OmicronRaidIntel.java:75–77).

### 1.3 The trigger DSL (HubMissionWithTriggers.java)

Declarative "when the mission enters stage X, do all of this" blocks — evaluated by
`runTriggers()` when the current stage matches (BaseHubMission ≈164–173):

```java
beginStageTrigger(STAGE_GO);                       // or beginWithinHyperspaceRangeTrigger(stage, entity, rangeLY)
triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.DEFAULT, "exileomicron", FleetTypes.PATROL_MEDIUM, system);
triggerSetFleetFlag(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);   // and many trigger* fleet tuners
triggerFleetInterceptPlayer(true);                  // ≈1442 (OrderFleetInterceptPlayerAction)
triggerRunScriptAfterDelay(3f, new Script() { public void run() { ... } });  // ≈1905 — anonymous inner class, Janino-OK
triggerSetGlobalMemoryValueAfterDelay(5f, "$exiles_q_flag", true);           // ≈1917
endTrigger();
```

Other useful primitives (all in HubMissionWithTriggers.java): `triggerSpawnEntity(...)` (≈309),
`triggerSpawnDebrisField(...)` (≈291), `triggerSpawnDerelict(...)` (≈324),
`triggerOrderFleetPatrolSystem(...)` (≈1320), fleet size/quality/officer auto-adjust (≈1957–1988),
`triggerSetMemoryValue(memory, key, value, removeOnMissionOver)` (≈539).

### 1.4 Pickers and giver plumbing (HubMissionWithSearch / BaseHubMission)

- `findOrCreateGiver(MarketAPI market, boolean addToCommDirectory, boolean cleanupIfNew)` (≈2977)
  with pre-config `setGiverFaction/Rank/Post/Portrait/Importance/Tags/Voice` (≈2944–2972).
- `findOrCreatePerson(factionId, market, cleanup, defaultRank, posts...)` (≈2842),
  `findOrCreateTrader(...)`, `findOrCreateCriminal(...)` (≈2810–2834).
- Market/system/planet search with composable requirements (`MarketFactionReq`,
  `MarketMemoryFlagReq(key, value)`, `ReqMode.ALL/ANY/NOT_ANY`… HubMissionWithSearch ≈222–624).
- **Ref-locking pattern** every vanilla mission uses: `setPersonMissionRef(person, "$prefix_ref")`,
  `setMarketMissionRef(...)`, `setEntityMissionRef(...)` — stores the mission object in that
  thing's memory so (a) duplicate offers are prevented, (b) rules rows can `Call $prefix_ref <action>`.
- `makeImportant(entityOrPerson, "$prefix_flagKey", STAGE_X)` — the pink target highlight +
  intel map arrow for the duration of a stage.

## 2. The bar-event pipeline

### 2.1 Moving parts (api:impl/campaign/intel/bar/events/)

- `BarEventManager` — campaign script + singleton via sector memory key
  `$core_genericBarEventManager` (BarEventManager.java ≈47–64). Holds a creator list, per-creator
  timeouts, and the active-event pool; ticks every ~0.5 days (≈55) and fills markets' bars up to
  the settings caps (`minBarEvents`/`maxBarEvents`).
- `GenericBarEventCreator` / `BaseBarEventCreator` — factory with frequency weight (default 10),
  active duration (~30–40d), timeout (~30–40d), accepted-timeout (~60–90d)
  (BaseBarEventCreator.java, defaults ≈per agent report).
- `PortsideBarEvent` — the thing shown at a bar: `shouldShowAtMarket(market)`,
  `addPromptAndOption(dialog, memoryMap)`, `optionSelected(...)` (api:impl/campaign/intel/bar/PortsideBarEvent.java).
- The bar itself is opened by vanilla rules rows in `core:data/campaign/rules.csv` firing
  `BarCMD showOptions` (api:impl/campaign/rulecmd/missions/BarCMD.java ≈150–298), which walks
  `PortsideBarData.getInstance().getEvents()` and calls each event's `addPromptAndOption`.

### 2.2 How a HUB MISSION becomes a bar event — the spec route (primary)

Vanilla does **not** hand-register mission creators. It loads
**`core:data/campaign/bar_events.csv`** (verified):

```
bar event id,tags,freq,prob,min dur,max dur,min timeout,max timeout,min accepted timeout,max accepted timeout,plugin
cheapCom,,10,1,30,40,15,20,60,90,com.fs.starfarer.api.impl.campaign.missions.CheapCommodityMission
```

Each row becomes a `BarEventSpec` (api:loading/BarEventSpec.java:33–58 — the constructor only
**stores the class name as a string**). `BarEventManager.updateBarEventCreatorsFromSpecs()`
(BarEventManager.java:100–133) later wraps each spec in a `SpecBarEventCreator`; mission specs are
detected by `isMission()`, which instantiates via
`Global.getSettings().getInstanceOfScript(pluginClass)` (BarEventSpec.java:62) — the script
classloader, i.e. **Janino loose-script classes are eligible** (same mechanism as the plugin
column of `mod:data/campaign/industries.csv`, which already resolves `data.scripts.industries.*`
loose classes in this mod). Spec creators are flagged `wasAutoAdded` and re-synced from CSV on
every game load — stale rows in old saves are replaced rather than crashing (≈106–133; V3).

**Timing analysis vs the rules-command crash (the constraint that killed custom rule commands):**
rules.csv resolves command classes at *application-start rules parse*, before loose scripts exist.
`bar_events.csv` resolves its class at *campaign load* (BarEventManager construction inside
`CoreLifecyclePluginImpl.addScriptsIfNeeded()` → `addBarEvents()`, api:impl/campaign/CoreLifecyclePluginImpl.java ≈804–849),
long after Janino compilation. Different seam, no known crash mode. **Still: prove it once with a
stub mission before building on it (verify item V1).**

At the bar, `HubMissionBarEventWrapper.addPromptAndOption` (api:impl/campaign/missions/hub/HubMissionBarEventWrapper.java ≈83–116):
1. calls `mission.createAndAbortIfFailed(market, true)` — your `create()` runs *speculatively*;
2. swaps the memory map to the giver person's memory;
3. fires **rules**: `FireBest.fire(..., mission.getTriggerPrefix() + "_blurbBar true")` then
   `"_optionBar true"` (≈107–108). The trigger prefix comes from the mission id set from the CSV
   row id (`BarEventSpec.createMission()` calls `mission.setMissionId(id)`, BarEventSpec.java:71).

**⇒ The bar prompt text and the clickable option live in rules.csv rows with triggers
`<missionId>_blurbBar` / `<missionId>_optionBar` — pure text/options rows, no command classes.**
Verified vanilla examples: `core:data/campaign/rules.csv` lines 24999–25000 (`cheapComBlurbBar`,
`cheapComOfferOptionBar`).

### 2.3 The code route (for NON-mission bar events)

`BarEventManager.getInstance().addEventCreator(new MyCreator())` from `ModPlugin.onGameLoad`,
guarded by `hasEventCreator(MyCreator.class)` — exactly the vanilla pattern
(CoreLifecyclePluginImpl.java ≈812–849) and exactly the mod's existing guarded-registration style
(mod:data\scripts\exiles\ExilesModPlugin.java:87–103). Fully runtime, loose-script-safe. Use this
only for bespoke non-mission bar flavor; for hub missions the CSV row is both required (the
wrapper restores its spec by id on save-load, HubMissionBarEventWrapper ≈33) and sufficient.

### 2.4 The contact route (person_missions.csv)

`core:data/campaign/person_missions.csv` (verified) keys missions to a **specific person id** with
rep gates (`min rep`), importance, and inter-mission prerequisites (`reqMissionAll/Any/None`) —
this is how Academy/story NPCs hand out quests. Class resolution is the same
`getInstanceOfScript` seam (api:loading/PersonMissionSpec.java:262). Missions are served through
the person's `BaseMissionHub` (api:impl/campaign/missions/hub/BaseMissionHub.java; generic dialog
rows `contactMissionAcceptGeneric` etc., §3.3). This is the natural Phase-2 vehicle for fixed
story NPCs (e.g. the Tapper questline) once the pilot proves the seam.

## 3. The rules.csv dialog layer

### 3.1 Row anatomy (columns: id, trigger, conditions, script, text, options, notes)

- **conditions:** newline-separated, AND-ed. `$key`, `!$key`, `$key == v`, `<`, `>=`,
  `$tag:sometag`, plus `score:N` to control FireBest priority. Memory scopes are reached by
  prefix: `$global.key`, `$market.key`, `$faction.key`, `$player.key`; bare `$key` = the LOCAL
  map (the person/entity being talked to).
- **script:** newline-separated commands. Three kinds matter:
  1. **Memory assignment — no command class at all:** `$flag = true`, `$flag = true 30`
     (expires in 30 days), `$global.counter = 2`. This is how the mod's own
     `exArr_beatDefenders` row sets `$defenderFleetDefeated = true 30`
     (mod:data/campaign/rules.csv:101).
  2. **Vanilla command classes** (loose-script-safe because they're vanilla): see §3.2.
  3. **`Call $ref method [args]`** — invokes Java on a mission/intel object stored in memory.
- **text:** dialog paragraph with `$token` substitution (tokens come from memory — your Java
  stuffs them via `updateInteractionDataImpl()`). `OR`-separated variants randomize.
- **options:** `optionId:Label` lines, optional leading sort number (`100:x_leave:Leave`).

Mod rows **merge** with vanilla by row id at load — the mod's existing `ngcExile*`/`exArr_*` rows
(mod:data/campaign/rules.csv) coexist with vanilla's, proving the merge.

### 3.2 The vanilla command vocabulary that covers quests (api:impl/campaign/rulecmd/)

Verified-in-use highlights (each is a class in that package or its subpackages):

| Command | Quest use |
|---|---|
| `FireBest T` / `FireAll T` | branch to the best/every rule with trigger T — your subroutine call |
| `Call $ref action [args]` | run mission Java (`callAction`) — the escape hatch that replaces custom commands |
| `AcceptMission` | registers + accepts `$missionRef`-style missions (used by story rows, rules.csv:34946) |
| `BarCMD accept $missionId` / `BarCMD returnFromEvent true|false` | bar-flow accept/return plumbing |
| `AddText` / `SetTextHighlights ...` / `Highlight` | paragraph + highlight control |
| `ShowPersonVisual` / `ShowDefaultVisual` / `ShowImageVisual` | visual panel |
| `SetShortcut optId ESCAPE` | ESC-to-leave (mod already uses it, rules.csv:92) |
| `AddOption` / `RemoveOption` / `SetEnabled` / `SetOptionText` / `SetTooltip` | option surgery |
| `AddCommodity` / `RemoveCommodity` / `AddCredits` / `SubCredits` | cargo/credit side effects |
| `AdjustRep` / `AdjustRepPerson` | rep deltas outside the mission's own payout |
| `DoCanAffordCheck price optId bool` / `ShowRemainingCapacity comm` | econ dialog niceties |
| `EndConversation` / `DismissDialog` / `CutCommLink` | exits |
| `SalvageGenFromSeed` / `SalvageDefenderInteraction` | dialog-layer battles (mod's `exArr_*` rows prove the pattern) |
| `DumpMemory` | dev: prints every memory map in-dialog (dev mode option) |

**What genuinely needs Java:** math beyond assignment, world queries, spawning with custom
doctrine, reacting to battle outcomes. All of it goes in the mission class (triggers,
`callAction`, listeners) — reachable from rules via `Call`. **Nothing needs a custom rule
command.** If a dialog outgrows rules entirely, the loose-script-safe fallback is a custom
`InteractionDialogPlugin` set on an entity (runtime-loaded; bypasses rules completely).

### 3.3 The generic plumbing you get for free (verified, core:data/campaign/rules.csv:24835–24860)

Vanilla ships market-agnostic accept/decline rows every mod mission reuses:

- `contactMissionAcceptGenericBar` (`$option == contact_acceptBar`):
  `FireBest ContactPrintAcceptText` → `BarCMD accept $missionId` → `FireBest ContactPostAccept` →
  `BarCMD returnFromEvent true`
- `contactMissionDeclineGenericBarNC` (`$option == contact_declineBarNoContinue`): `BarCMD returnFromEvent false`
- comm-link/mission-hub equivalents: `contactMissionAcceptGeneric` (`Call $mHub accept $missionId`), etc.

So your offer row just sets `$missionId = <yourId>` and emits options
`contact_acceptBar:Accept` / `contact_declineBarNoContinue:Decline` — accept plumbing done.
(Verified pattern: `cheapComOfferTextLocalBar`, rules.csv:25001–25008.)

### 3.4 Comm-directory NPC conversations

From code (all loose-script-safe; api:campaign/CommDirectoryAPI.java, FactionAPI.createRandomPerson
≈170–198, or `Global.getFactory().createPerson()` for hand-built people — full vanilla example in
CoreLifecyclePluginImpl ≈475):

```java
market.addPerson(person);
market.getCommDirectory().addPerson(person);
Global.getSector().getImportantPeople().addPerson(person);   // if referenced by id later
```

Conversations then route through rules: greeting rows use trigger `PickGreeting` with conditions
on the person's memory (`$myFlag score:1000`) — the LOCAL map inside a comm link *is* the person's
memory. Vanilla example: `cheapComPickupGreeting` (rules.csv:25044) gates on
`$cheapCom_hasCommodity`, calls `Call $cheapCom_ref updateData`, and offers the pickup option.
Contacts (the intel-screen kind) are opt-in:
`ContactIntel.addPotentialContact(person, market, textPanel)`
(api:impl/campaign/intel/contacts/ContactIntel.java ≈897).

## 4. Three dissected vanilla missions (what each actually touches)

### 4.1 CheapCommodityMission — minimal bar procurement
(api:impl/campaign/missions/CheapCommodityMission.java)

- One Java file. Stage "graph" is nearly degenerate: LOCAL variant completes inside the dialog
  (rules do `RemoveCommodity`/`AddCommodity`, set `$cheapCom_completed`, `Call $cheapCom_ref2 repSuccess`);
  REMOTE variant has a single stage `TALK_TO_PERSON` completed by
  `setStageOnMemoryFlag(COMPLETED, remoteContact, "$cheapCom_completed")` — the flag is set by a
  rules row when the player picks up the goods (rules.csv:25050–25054).
- `updateInteractionDataImpl()` publishes ~14 `$cheapCom_*` display tokens into memory for the
  text column to substitute.
- Data footprint beyond Java: ~12 rules rows (24997–25054), one bar_events.csv row. **No strings
  file, no people file, no variants.**

### 4.2 DeadDropMission — one travel stage + spawned target + complication fleet
(api:impl/campaign/missions/DeadDropMission.java)

- Spawns an invisible mission node via `spawnMissionNode(new LocData(EntityLocationType.HIDDEN_NOT_NEAR_STAR, null, system))`,
  ref-locks it (`setEntityMissionRef`), `makeImportant(target, "$ddro_target", Stage.DROP_OFF)`.
- Completion: `setStageOnMemoryFlag(COMPLETED, target, "$ddro_completed")` — set by the rules row
  that runs when the player interacts with the node. 120-day limit → FAILED.
- 50% `triggerComplicationBegin(...)` — a canned hostile-fleet-wants-the-item subsystem.
- Flavor item strings are hard-coded in Java (≈50–73) — precedent that inline flavor is fine.

### 4.3 JailbreakMission — the full multi-stage shape worth copying
(api:impl/campaign/missions/JailbreakMission.java)

- Stages JAILBREAK → RETURN → COMPLETED (+ FAILED, FAILED_DECIV), wired:
  `connectWithMemoryFlag(JAILBREAK, RETURN, market, "$jabr_needToReturn")`,
  `setStageOnMemoryFlag(COMPLETED, person, "$jabr_completed")`,
  `connectWithMarketDecivilized(JAILBREAK, FAILED_DECIV, market)`, staged time limits.
- Difficulty scales off giver importance → market size + `MarketCMD.RaidDangerLevel` + marines
  needed; the raid itself is the *stock market-raid custom objective* — the mission only registers
  the objective and reads the outcome flag. Lesson: **lean on stock interactions to resolve
  actions; the mission just wires flags.** (Same philosophy as the mod's `exArr_*` buoys.)

**Universal memory pattern** (all vanilla missions): `$<prefix>_ref` = mission object on
person/market/entity; `$<prefix>_<token>` = display vars; `$<prefix>_<flag>` = stage triggers set
by rules or Java. Adopt wholesale.

## 5. Loose-script safety map (the constraint table)

| Layer | Mechanism | Verdict |
|---|---|---|
| Mission class | Janino class extending `HubMissionWithBarEvent` | **SAFE** (precedent: OmicronRaidIntel extends RaidIntel; see §6 adaptations) |
| Bar hook | `mod:data/campaign/bar_events.csv` row, plugin = `data.scripts.exiles.quests.X` | **SAFE by analysis** — campaign-load `getInstanceOfScript`, same seam as industries.csv plugin column (**V1: prove with stub once**) |
| Bar/dialog text | mod rules.csv rows, triggers `<id>_blurbBar` / `<id>_optionBar` / offer rows | **SAFE** — text/options + vanilla commands only |
| Accept plumbing | vanilla generic rows (`contact_acceptBar` etc.) | **SAFE** — nothing to write |
| Custom logic from dialog | `Call $<prefix>_ref <action>` → `callAction()` override | **SAFE** — vanilla `Call` command, custom Java runtime-invoked |
| Custom rules **command class** | e.g. `ExilesDoThing` in script column | **CRASH** — hard constraint stands (2026-07-06); never needed given the above |
| Bespoke dialog | `entity.setInteractionDialogPlugin(new ...)` | **SAFE** — runtime-loaded (CLAUDE.md §7) |
| Intel | extend `BaseIntelPlugin` (or get it free via BaseHubMission) | **SAFE** — proven |
| NPC placement / contacts | `market.addPerson` + `getCommDirectory().addPerson` + `ContactIntel.addPotentialContact` | **SAFE** — plain campaign API |
| Contact missions | `person_missions.csv` plugin column | **SAFE by analysis** — same seam as bar_events.csv (**V1b**) |
| Director hooks | `$exiles_omicron_*` flags, `OmicronWarCapacity.modifyMult`, `OmicronDirector.setRaidsEnabled/addTargetFaction` | **SAFE & BUILT** — mod:docs/war-capacity-design.md §6 |

## 6. Janino hazard verdict for extending HubMissionWithBarEvent

Game Janino = **2.7.8** (core:janino.jar manifest). Scan of the base classes found **no abstract
generic methods and no signatures forcing generic code in subclasses** — stage ids are `Object`,
maps are internal. Specific adaptations for the scaffold:

1. **No enum declarations** — vanilla missions declare `public static enum Stage {...}`; enum
   *declaration* support in Janino 2.7.8 is unverified (V2). Use
   `public static final String STAGE_GO = "go";` — the stage machinery keys a
   `LinkedHashMap<Object,...>` so Strings work and serialize trivially.
2. **Varargs calls are fine** — the mod's own Janino-compiled code *declares* varargs
   (`getCount(PatrolType ... types)`, mod:data\scripts\industries\ExileAshenMilitaryBase.java:433),
   so calling `addSuccessStages(a, b)` is safe.
3. **Anonymous inner classes OK, lambdas not** — `triggerRunScriptAfterDelay(3f, new Script(){...})`
   is the sanctioned form (CLAUDE.md §7).
4. **Raw types + cast every `List.get()`** (established mod discipline; memory:
   janino-offline-compile-check).
5. **Public no-arg constructor required** — `getInstanceOfScript` instantiates reflectively.
6. **Save-safety:** named top-level/static classes only; keep needed state in non-transient fields;
   remember the transient list in §1.2. Run the mod's offline Janino compile-check (ScriptDemo
   harness, per memory) before any game launch.

## 7. Supporting data files a quest can touch

| Concern | File | Notes |
|---|---|---|
| Bar cadence | `mod:data/campaign/bar_events.csv` (new file, merges with vanilla) | 1 row/quest |
| Dialog text | `mod:data/campaign/rules.csv` | text lives in the text column — *already externalized*; the ratified strings.json migration (memory: externalized-strings-decision) applies to **Java-side** text (intel descriptions, notifications) via `Global.getSettings().getString(category, id)` (api:SettingsAPI.java:107–112) reading `mod:data/strings/strings.json` (new file, category e.g. `"exiles_quests"`) |
| Long descriptions | `mod:data/strings/descriptions.csv` (exists) | only for codex-style entity descriptions |
| Quest NPC portraits | faction `.faction` portraits or `person.setPortraitSprite(...)` with a settings.json-registered sprite | custom sprites are the heap budget — reuse existing (CLAUDE.md §7) |
| Quest battle variants | `data/variants/*.variant` + `triggerCreateFleet` role/quality knobs | only if stock rosters don't cut it |
| Contact-NPC quests | `mod:data/campaign/person_missions.csv` (new file) | Phase-2 story NPCs |
| Memory flags | none (runtime) | registry discipline: §II-B naming + log in `mod:docs/EXILES_ID_REGISTRY.md` |

---

# PART II — THE STREAMLINING (deliverables)

## II-A. Anatomy of an Exiles quest — one page, dependency order

For a quest `xq_<shortId>` (e.g. `xq_ttdata`):

```
0. SPEC   docs/quests/xq_<shortId>.md               ← §II-C template. Written FIRST. Everything below is derived.

1. JAVA   data/scripts/exiles/quests/XqShortId.java  ← ONE class: extends ExilesQuestBase
          create(): pick giver/market/targets, declare stages+flags+rewards, triggers
          updateInteractionDataImpl(): publish $xq_<shortId>_* display tokens
          callAction(): quest verbs invoked from rules rows via Call
          intel text overrides (getBaseName / addDescriptionForNonEndStage / getNextStepText)
          [loose-script-safe: yes — §5 row 1]

2. HOOK   data/campaign/bar_events.csv               ← 1 row: xq_<shortId>,...,data.scripts.exiles.quests.XqShortId
          [safe by analysis — §5 row 2; V1 stub-test first]
          (contact-NPC quests: person_missions.csv row instead)

3. DIALOG data/campaign/rules.csv                    ← formulaic block, ~4–8 rows, VANILLA commands only:
          xqSI_blurbBar   trigger xq_<shortId>_blurbBar     (bar flavor text)
          xqSI_optionBar  trigger xq_<shortId>_optionBar    (the approach option)
          xqSI_offer      trigger DialogOptionSelected, $option == xq_<shortId>_startBar
                          script: $missionId = xq_<shortId>; FireBest XqSIOfferText
                          options: contact_acceptBar:Accept / contact_declineBarNoContinue:Decline
          xqSI_offerText  trigger XqSIOfferText              (the pitch, token-substituted)
          + per-stage rows: PickGreeting / OpenInteractionDialog rows that set $xq_<shortId>_<flag>
            or Call $xq_<shortId>_ref <action>   [accept/decline plumbing: vanilla generic rows — free]

4. TEXT   data/strings/strings.json (category exiles_quests)  ← Java-side text only (intel/notifications);
          dialog text stays in rules.csv rows (already-external)

5. PEOPLE (only if fixed NPC) code-side person + comm directory (§3.4); portrait reuse

6. WIRING Director/flag interactions: read/write $exiles_* keys, OmicronWarCapacity.modifyMult(...)
          — zero new plumbing (war-capacity-design.md §6)

7. TEST   console lines from the spec's test block (§II-D)   [Console Commands 4.0.9 installed+enabled]
```

Total for a typical quest: **1 new Java file, 1 CSV row, one contiguous rules.csv block, a few
strings.json keys.** Nothing else.

## II-B. Reusable scaffold proposal (to build BEFORE the pilot; NOT built yet)

Three small pieces, all Janino-conservative, all in `data/scripts/exiles/quests/`:

### 1. `ExilesQuestBase extends HubMissionWithBarEvent`

Thin. Its job is defaults + the mod's conventions, not framework-building:

```java
public abstract class ExilesQuestBase extends HubMissionWithBarEvent {

    // String stage ids (Janino: no enum declarations — §6)
    public static final String STAGE_COMPLETED = "completed";
    public static final String STAGE_FAILED = "failed";

    /** Quest id == bar_events.csv row id == rules trigger prefix. */
    public abstract String getQuestId();

    /** One-stop flag namespace: $exiles_q_<questId>_<flag> in GLOBAL memory. */
    public String flagKey(String flag) {
        return "$exiles_q_" + getQuestId() + "_" + flag;
    }
    public void setGlobalFlag(String flag, boolean permanent) {
        // non-permanent flags are unset on mission end via the framework's flag list
        if (permanent) Global.getSector().getMemoryWithoutUpdate().set(flagKey(flag), Boolean.TRUE);
        else setFlag(flagKey(flag), true);   // BaseHubMission flag tracking
    }
    public boolean hasGlobalFlag(String flag) {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(flagKey(flag));
    }

    /** strings.json accessor, category "exiles_quests", key "<questId>_<id>". */
    public String str(String id) {
        return Global.getSettings().getString("exiles_quests", getQuestId() + "_" + id);
    }

    // Convention defaults every Exiles quest wants:
    // - setRepFactionChangesLow()/person Medium unless spec overrides
    // - sendUpdateForNextStep on stage change
    // - getMissionTypeNoun() -> "job" / faction-flavored noun
}
```

Deliberately **inheritance, not composition** — the wrapper/rules plumbing calls
`HubMissionWithBarEvent` methods on the instance the spec creates, so composition would mean
re-implementing the wrapper. §6 says inheritance is safe.

### 2. `ExilesQuestFlags` (static helper, no state)

Wraps the `$exiles_*` conventions the Director already reads, so quest code never typos a key:
`setOmicronRaidsEnabled(boolean)` → delegates to `OmicronDirector.setRaidsEnabled` (exists),
`setAssetDestroyedOverride(nodeId, key, boolean)` → `$exiles_omicron_asset_<node>_<key>_destroyed`,
`grantEarlyWarning(days)` → `$exiles_omicron_early_warning_days`,
`wcModify(nodeId, statId, modifierId, mult, days)` → `OmicronWarCapacity.modifyMult` (exists).
Every new key gets logged in `docs/EXILES_ID_REGISTRY.md` (working convention, CLAUDE.md §7).

### 3. `data/strings/strings.json` seed (new file)

One category `"exiles_quests"`; keys `<questId>_<textId>`. This *is* the ratified Phase-2-gate
strings migration starting point — quests are written externalized from day one, and the batch
migration of older inline text can proceed independently (memory: externalized-strings-decision).

**Plus one non-code asset:** a commented **template rules.csv block** kept in
`docs/quests/_rules_block_template.csv` to copy-paste per quest (the §II-A DIALOG rows with
`xq_<shortId>` placeholders). This kills the worst of the file-type ping-pong: the rules block is
written by substitution, not composition.

## II-C. QUEST SPEC TEMPLATE (write this FIRST; maps 1:1 to extension points)

Save as `docs/quests/xq_<shortId>.md`. Everything in [brackets] is a slot; each slot names the
artifact it becomes. "Opus, implement quest spec xq_foo" = fill the anatomy from this, in order.

```markdown
# QUEST SPEC — xq_<shortId>: <Working Title>
status: draft | ratified | built | tested

## Identity
- questId: xq_<shortId>              → bar_events.csv id, rules trigger prefix, flag namespace
- class: data.scripts.exiles.quests.Xq<ShortId>   → bar_events.csv plugin column
- factionContext: <factionId>        - whose story beat is this; tone check vs CLAUDE.md §3
- pillarCheck: <one line: which design pillar this serves; morally-grey angle>

## Giver & hook
- hook: bar | contact | dialog | intel-button
- giverSpec: faction / post / rank / importance / portrait(reuse!)   → create(): findOrCreateGiver + setGiver*
- barMarketReq: <market requirements — faction, size, not-hidden…>   → shouldShowAtMarket / create() requirements
- barBlurb: <1-2 sentences, third person, atmospheric>               → rules row <id>_blurbBar (text col)
- barOption: <the clickable approach line>                           → rules row <id>_optionBar (options col)
- pitch: <the offer paragraph(s); tokens $xq_<shortId>_*>            → rules offer row (text col)
- tokens: [$xq_<shortId>_reward, $..._systemName, ...]               → updateInteractionDataImpl()

## Stages   (STRING ids; first = starting stage)
| stage | objective (intel next-step text) | entry triggers (fleets/spawns/scripts) | exit condition → next stage |
|---|---|---|---|
| go        | "Travel to X"  | triggerCreateFleet(...)? makeImportant(...)? | memory flag $xq_<id>_arrived on <who/what> → act |
| act       | "Do Y"         | ...                                          | flag $xq_<id>_done → completed / battle-loss → failed |
- timeLimit: <days> → failed | none
- abandonable: yes(no-penalty-days N) | no

## Branches & morally-grey choice (if any)
- <option id>: <label> → effects (flags set, rep deltas, Director calls)  → callAction verb + rules row

## Payouts
- credits: <min–max or tier>        → setCreditReward
- rep: person <tier>, faction <tier>, penalties <tier>   → setRep* helpers
- special: <items/BP/officer/…>     → endSuccessImpl / cargoOnSuccess

## Flags & Director wiring
- flags SET (lifecycle-scoped vs permanent): $exiles_q_<id>_...      → setGlobalFlag
- flags READ (prerequisites): ...                                     → create() requirements / rules conditions
- Director calls: <OmicronWarCapacity.modifyMult(...) / setRaidsEnabled / $exiles_omicron_early_warning_days=N / none>

## Repeatability & exclusivity
- repeatable: no | timeout <days>    → bar_events.csv timeout columns
- exclusive-with: <questIds / global ref $xq_<id>_ref>

## Test block (paste-ready console lines — becomes §II-D step 8)
- spawn conditions: <e.g. runcode DebugFlags.BAR_DEBUG = true; + which market to dock at>
- fast-forward lines: <flag sets that skip to each stage>
- success criteria: <observable intel/flag/econ state per stage>
```

## II-D. Authoring checklist — the back-and-forth, tamed (ordered touchpoints)

Prereqs (once): scaffold built (§II-B); stub quest proved V1. Test tooling available **and
verified installed**: Console Commands 4.0.9 (`D:\Fractal Softworks\Starsector\mods\Console Commands`,
enabled in `mods/enabled_mods.json` as `lw_console`) — gives `runcode`, `addcredits`,
`adjustrelation`, `goto/jump`, `list`, etc.

| # | Step | File | Verify with |
|---|---|---|---|
| 1 | Write/ratify spec | `docs/quests/xq_<id>.md` | Sean sign-off (tone/pillar check) |
| 2 | Mission class | `data/scripts/exiles/quests/Xq<Id>.java` | offline Janino compile-check (ScriptDemo harness) — catches what javac won't |
| 3 | Bar hook row | `data/campaign/bar_events.csv` | game start reaches menu = class resolves; then in-game: `runcode com.fs.starfarer.api.impl.campaign.DebugFlags.BAR_DEBUG = true;` → dock at a qualifying market → bar shows blurb |
| 4 | Rules block (from template block, substitute `xq_<id>`) | `data/campaign/rules.csv` | `runcode com.fs.starfarer.api.impl.campaign.DebugFlags.PRINT_RULES_DEBUG_INFO = true;` then open the bar — log shows rule matching (`core:data/config/settings.json:14–17`); malformed CSV fails at game start |
| 5 | Strings | `data/strings/strings.json` | missing key throws on first intel render — open intel entry once |
| 6 | People/portraits (if fixed NPC) | code-side in class; portrait path | comm directory shows entry; `list persons`-style console checks |
| 7 | Director/flag wiring | (runtime keys) | `runcode org.lazywizard.console.Console.showMessage("" + Global.getSector().getMemoryWithoutUpdate().get("$exiles_q_xq_<id>_<flag>"));` |
| 8 | Stage walk | — | run the spec's test block: force-set each transition flag via `runcode Global.getSector().getMemoryWithoutUpdate().set("$...", true);` and confirm intel next-step text advances; deadline: fast-forward with dev-mode time controls |
| 9 | Payout check | — | complete final stage; confirm credits/rep popups; `AdjustRelation` beforehand to test rep-gated offers |
| 10 | Save/load cycle | — | save mid-stage, quit to menu, reload: intel intact, triggers not duplicated (§1.2 transients) |
| 11 | Registry hygiene | `docs/EXILES_ID_REGISTRY.md` | new ids/flags logged; grep sweep for the questId across data/ (CLAUDE.md §7 ID discipline) |

Iteration-speed notes:
- Rules text tweaks: **dev-mode is reported to reload rules.csv when a dialog reopens** (widely
  used modder workflow) — verify once (V4); if true, bar-text iteration needs **no restart**.
- Java tweaks: restart required (Janino compiles at launch) — keep logic-heavy work behind
  `callAction` verbs so most iteration lands in rules/text.
- `DebugFlags.ALLOW_ALL_CONTACT_MISSIONS` (dev-mode default true, api:impl/campaign/DebugFlags.java:42,110)
  un-gates contact missions for testing.

## II-E. Pilot quest recommendation

**Build the Tri-Tachyon ↔ Omicron data-run quest first** (CLAUDE.md §6 Phase 2: "A Tri-Tachyon
group needs help engaging Omicron ships to gather data + Omicron Cores").

Why it beats the refugee-trust quest as the scaffold-prover:

1. **Smallest true surface.** Bar hook at a Tri-Tachyon market + two stages
   (go to an Omicron-active system → destroy/scan N Omicron ships → return) + payout. Exercises
   every scaffold seam exactly once: bar_events.csv row (V1 proof), blurb/option/offer rules
   block, String stage ids, a memory-flag transition, one trigger block, credit+rep payout,
   intel display.
2. **The other side is already built.** Kill-detection is a solved pattern in this codebase —
   `OmicronRaidIntel` already registers as a `FleetEventListener` and counts player kills of
   Omicron hulls (mod:data\scripts\exiles\OmicronRaidIntel.java:45–51). The Director reward hooks
   are pre-registered: `$exiles_omicron_early_warning_days` is *explicitly documented as a quest
   reward* ("quest reward, e.g. 10–15", mod:docs/war-capacity-design.md §6), and reserved modifier
   ids like `exiles_wc_quest_tt_data_run` already exist in the ledger (war-capacity-design.md ≈160).
   Zero new plumbing.
3. **Lore-central, not lore-blocking.** It seeds the Phase-2 arc ("*why* does Omicron attack the
   Church?") and the Omicron Cores item (memory: omicron-cores-item — the item itself stays out of
   the pilot; the quest can pay credits + early-warning-days now, cores later).
   **Reveal guardrail (ratified 2026-07-22):** no one in the Core, including Tri-Tachyon, knows that
   Omicron has converted its defensive mandate into preemptive threat reduction, targeted the Luddic
   powers as the first high-confidence threat, or begun preparing a contingency against the Hegemony.
   The pilot should establish anomalous evidence and competing human theories, not disclose the
   answer. Later quests earn the conclusion in layers; public text and ordinary NPC knowledge remain
   uncertain until the appropriate reveal.
4. **The refugee-trust quest is a worse first pick** because it depends on Phase-0 content that
   isn't finished: the Refugees are a WIP faction, and "accepting refugees" needs the
   refugee-influx/processing-center subsystem semantics settled (CLAUDE.md §4, §6). Build it
   second — it will inherit a proven scaffold.

Pilot scope guard: no new sprites, no new variants (stock Omicron roster), no fixed NPC (generated
giver via `findOrCreateGiver` with TT posts), no custom InteractionDialogPlugin.

---

## Verify-before-relying list (explicit unknowns — do NOT build past these silently)

- **V1 — bar_events.csv plugin column resolving a Janino loose-script class.** Strongly indicated
  safe (§2.2 timing analysis; industries.csv precedent) but not yet proven in-game. **Test with a
  stub `HubMissionWithBarEvent` subclass before writing the real pilot.** Failure symptom would be
  a campaign-load (not app-start) error. Fallback if it fails: precompile ONLY the quest classes
  into a tiny jar (the release-jar path per docs/EXILES_BUILD_NOTES.md), or use code-registered
  `BaseBarEventCreator` + custom `BaseBarEvent` (skips HubMission bar wrapper; more code).
- **V1b — person_missions.csv same seam** (needed later for fixed story NPCs, not the pilot).
- **V2 — enum declarations under Janino 2.7.8.** Unverified; scaffold sidesteps with String stage
  ids. If someone wants enums, test in the ScriptDemo harness first.
- **V3 — stale bar_events state in saves.** `updateBarEventCreatorsFromSpecs` re-syncs auto-added
  creators each load (BarEventManager.java ≈106–133) so removed rows should degrade gracefully —
  confirm once with a save made before a row existed. (Moot-ish: mod requires new games by design.)
- **V4 — dev-mode rules.csv hot-reload on dialog open.** Reported modder workflow; the reload
  lives in obfuscated core so it could not be source-verified. Confirm in-game; it's the single
  biggest iteration-speed lever if real.
- **V5 — line numbers marked ≈** came from exploration agents reading the same sources; spot-check
  before quoting them anywhere load-bearing.
- **V6 — `HubMissionWithBarEvent` construction context.** The stub test (V1) should also confirm
  the no-arg-constructor + `setMissionId` flow works when the class lives in `data.scripts.*`
  (classloader parentage of Janino classes vs `instanceof HubMissionWithBarEvent` check in
  BarEventSpec.isMission(), api:loading/BarEventSpec.java:62 — expected fine, same-classloader
  parent API classes, but it is the exact line that decides mission-vs-plain-event).

*Cross-references: CLAUDE.md §6 (Phase-2 quest list), §7 (Janino + rules-command constraints);
mod:docs/war-capacity-design.md §6 (Director quest API); mod:docs/EXILES_BUILD_NOTES.md (jar path);
memory: externalized-strings-decision, janino-offline-compile-check, omicron-cores-item.*
