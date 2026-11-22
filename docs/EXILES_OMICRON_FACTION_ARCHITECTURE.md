# Exiles — Omicron Faction Architecture: Economy vs Script (Investigation + Recommendation)

Created: 2026-06-24
Status: **DECISION RATIFIED 2026-06-24 — Omicron is a SCRIPT-ONLY (marketless) faction.** Not yet built (no code
written). This is the *broader* question that sits above `EXILES_OMICRON_LOGISTICS.md`: not "how is the attack lever
degraded?" but **"does Omicron exist as an economy faction at all, or as a pure-script machine?"** — answered:
**pure-script.** See §2a for the ratified decision and the constraints it locks in, and §3a for the capability
mapping against the author's stated requirements.

Source: a 7-agent investigation (3 gap-research / decision-critical verification + 3 adversarial design advocates +
1 synthesis), grounded in real vanilla source under `starsector-core/starfarer.api` and the mod's own docs. **This
file is the durable record** — it also recovers the vanilla **pirate / Luddic-Path temporary-base lifecycle** code
map that was in the discarded raw dossier behind `EXILES_OMICRON_LOGISTICS.md` ("the raw dossier was a temp artifact").

> Line numbers below are as-of the investigation read of this install's `starsector-core`. Treat them as strong
> pointers, not eternal constants (they can drift across game versions). Class + method names are the stable handle.

---

## 1. The question

Omicron (`exileomicron`) is currently a **near-verbatim copy of vanilla `remnants.faction`** (renamed). It has a
system (`ExileOmicron0.java`) with Nexus / Damaged Nexus / Sensor Array placed as **bare `addCustomEntity` tokens**,
faction relations are wired, but it owns **no market** and has **no fleet source** — so today **Omicron spawns no
fleets at all** (the `THEME_REMNANT` tags on a hand-built system are almost certainly inert; theme generators only
run at sector-gen over procgen constellations — flagged to verify, §8).

Three ways it could exist:

- **A — Pure-script (marketless "Remnant model").** No economy markets anywhere; the Nexus / Damaged Nexus / Sensor
  Array become destroyable **station-mode fleet sources**, driven by a `SourceBasedFleetManager`-style Director.
  Same archetype as vanilla **Remnants and Omega**.
- **B — Economy faction.** Omicron owns real markets/colonies on its system planets, participates in the economy like
  a normal NPC faction (AI-core industries), likely econ-group-isolated to survive universal hostility, and Nex-wired.
- **C — Pirate-base hybrid.** No permanent home economy, but temporary, hidden, econ-group-isolated forward
  "logistics nodes" modeled on vanilla **pirate / Luddic-Path bases** that spawn raids and can be ground down.

---

## 2. Recommendation (synthesis verdict — high confidence)

**Layered. Build Omicron marketless (A) as its identity and permanent presence; borrow the pirate-base machinery (C)
as a *pattern library* for raid-dispatch + legibility verbs only; reject the full economy faction (B) outright.**

- **A is the spine:** promote the Nexus / Damaged Nexus / Sensor Array to destroyable station-mode fleet sources
  (mirror `RemnantThemeGenerator.addBattlestations`); drive them with a `SourceBasedFleetManager` subclass with the
  wrong-direction escalation terms dropped.
- **C is a borrowable verb layer, not the identity:** the vanilla pirate base *is itself a real (hidden) market* with
  a commander, trade, and population — so adopting it wholesale re-imports the exact lore violations as B. Its value is
  the **raid lifecycle + leak-hygiene + intel/UI shell**, taken *minus the market, commander, and trade*. A stripped
  C-node is also exactly what the **Midan "strike from forward bases"** framing needs — build it once, share it.
- **B is rejected** on four independent, code-verified axes (§5).

This **generalizes** the prior `EXILES_OMICRON_LOGISTICS.md` decision rather than reopening it: that doc chose
bespoke destroyable entities + a `SourceBasedFleetManager` Director for the *attack lever*, explicitly not economy
markets. This investigation finds those **same entities are sufficient to BE the faction's entire physical presence**,
so "attack source" and "faction footprint" are one and the same. Choosing B would have *conflicted* with the prior
decision (re-introducing the commander / trade / Nex baggage it was made to avoid).

---

## 2a. Ratified decision (2026-06-24)

**Omicron is a SCRIPT-ONLY (marketless) faction.** Model A is adopted as the spine; model B (economy faction) is
rejected outright; model C (pirate-base hybrid) is retained only as a borrowable *pattern library* — a hidden,
econ-group-isolated, Nex-unregistered single node may be used **where a market object is genuinely required** (the
stock ground-Raid/Disrupt dialog, or the optional "parody bar" flavor), never as the faction's home or identity.

Constraints this locks in:

- **Nexerelin is IN SCOPE, and the marketless route is the no-compromise compat path.** A faction that owns no market
  is never counted by Nex's census, so Omicron is not a Nex diplomacy participant — it cannot be made peace with,
  invaded, or conquered. That is exactly how Nex already treats Remnants/Omega. The *economy* route is the one that
  fights Nex (a market-owner becomes conquerable); marketless sidesteps it entirely.
- **Factions-tab visibility: handle exactly like the Remnants.** `exileomicron.faction` keeps `showInIntelTab:false`
  (its current value — **no edit needed**). Omicron is an *environmental machine threat*, not a faction-list/diplomacy
  entity, and listing it on the Factions tab at game start does not make narrative sense. **This supersedes, for
  Omicron, the earlier "Refugee + Omicron absent from the Factions tab = bug" flag** — that was a real bug for the
  *refugee* colonies (which needed visibility, since fixed); it is the *intended* state for a script-only machine.
  A future session must NOT "fix" it by flipping the flag.
- **Clean "Base Destruction = Base Destruction."** Omicron's physical presence is destroyable *entities* (station-mode
  Nexus + logistics nodes) that die cleanly in one decisive battle (`Misc.fadeAndExpire`). A colony cannot be destroyed
  that way (only saturation-bombed into a decivilized husk) — the decisive engine reason Omicron is entities, not colonies.
- **Logistics you can degrade, no economy required.** Script-controlled destroyable logistics entities (mining /
  construction / relay nodes, the Damaged Nexus, etc.) flip persistent triggers when destroyed; a Director keeps
  "score" off their surviving integrity and scales attack **size / target / timing** accordingly. Quests flip the same
  flags. Raidable "logistics points" that go *temporarily* offline are wanted (a good quest surface) — implement via a
  custom interaction + self-healing flag, or a stripped hidden node where the stock Raid dialog is specifically desired.
- **No random Omicron missions.** Marketless guarantees Omicron never auto-posts survey/analyze missions — there is no
  faction flag to disable those for a market-owner (§10f / `EXILES_ECONOMY_MAP.md` §10f), so this is a concrete reason
  the marketless route is cleaner, not just tidier.
- **Parody-colony bars are a "like," not a priority** — deferred; if built, only on a hidden isolated node with custom
  bar events gated to Omicron, and only after verifying that node does not reintroduce random missions (§8).

---

## 3. The weighing (the positives/negatives ledger)

| | **A — Pure-script (marketless)** | **B — Economy faction** | **C — Pirate-base hybrid** |
|---|---|---|---|
| **Fiction fit (CLAUDE.md §3)** | ✅ Best — same archetype as both vanilla AI factions; zero-population, no one to bribe | ❌ Worst — market forces population, a comm-directory **commander to deal with**, open/black-market **trade**, bar rumors | ⚠️ OK only once the market is stripped — at which point it collapses into A |
| **Player-degrade loop** | ✅ Free in `SourceBasedFleetManager` (kill source → stop; attrite → temporary suppression; far → slow) | — (would sit on top regardless) | ✅ Vanilla ships spawn → destroy → cooldown + discovery/bounty UI |
| **Nexerelin** | ✅ Marketless → invisible to the census → nothing to make peace with / invade / **conquer** | ❌ Registered market-owner → diplomacy profile → **conquerable polity**; breaks "permanently hostile machine" | ✅ Hidden + Nex-unregistered → stays out of the census |
| **Escalation (§3 "escalating machine logic")** | ✅ Director integrity/cadence/FP scalar | ❌ NPC market size is **static** — can't escalate through economy | ⚠️ Binary alive/dead + *wrong-direction* tier escalation to rip out |
| **Footprint** | Destroyable Nexus + roving fleets (a *place* to blow up, but no economy presence) | Persistent economy/intel-screen presence + MILITARYBASE patrols "for free" | Transient hidden nodes (disposable by design) |
| **Run cost** | trivial | **also trivial** (econ layer is event-driven, ~KB) — B's problem is *design*, not performance | trivial |
| **Effort** | Lowest — entities already exist in `ExileOmicron0.java` | Highest — self-sufficiency pass + isolation + untested Nex verification + suppressing trappings | Moderate as a borrowed pattern; high & wasteful if adopted whole |

**Fair to B:** the econ layer is genuinely cheap to run (CLAUDE.md §7). B does **not** fail on performance — it fails
on fiction + conquerability + can't-grow, and on the fact that its few unique perks (patrols, footprint, AI-core
grants) are obtainable from a stripped hidden node **without** becoming a conquerable polity.

---

## 3a. Capability mapping against the author's requirements (2026-06-24)

Sean's stated must-haves for Omicron, mapped to what each route can actually deliver. **The headline: requirement (c)
settles it on engine mechanics alone** — only the script/entity route can give clean base destruction, because a
*station/entity* (with a defense fleet) blows up cleanly while a *colony* can only be ground down by saturation-bombing
into a decivilized husk. Nothing on the list requires a full economy faction; the two items that want a "market object"
(d's stock Raid dialog, e's bars) are met by a single hidden, isolated, Nex-unregistered node.

| Requirement | Script route (marketless spine + optional hidden nodes) | Economy route — the limit |
|---|---|---|
| **a. Active attacks on select core worlds** | `RaidIntel` from the Nexus against a hand-picked target list — full control over which worlds and when | Same raid machinery, but via faction-war/Nex behavior you must fight to constrain; precise thematic targeting is harder |
| **b. Degrade Omicron logistically; "keep score" → adjust attack size/target/timing** | **Native.** Director reads an integrity/score scalar + persistent flags; destroying a node decrements the score → scales raid FP/cadence/targets. Quests flip the same flags | Would model logistics as industries + temporary DISRUPT — then write the same scoring script anyway, bolted onto an unneeded market |
| **c. Base Destruction = Base Destruction (no saturation-bomb mess)** | ✅ Station-mode entity is combat-destroyable → `fadeAndExpire` → gone in one battle | ❌ **Hard engine limit.** A colony isn't destroyable; "destroying" it = repeated saturation bombardment to decivilize. The exact mess to avoid |
| **d. Raid "logistics points"; take offline temporarily; quests** | ✅ and more flexible — a custom entity defines per-node what a raid does (self-healing temporary disable / permanent kill / quest trigger). Stock Raid/Disrupt dialog → host on a hidden isolated node | Temporary disrupt is native but locks you to industry semantics and still can't cleanly destroy |
| **e. Parody bars (low priority); NO random missions** | Bars need a market → hidden isolated node with custom gated bar events. Pure-marketless posts **zero** random missions ✅ | ❌ Actively **creates** the random-mission problem (market-owners are auto-named as survey/analyze givers; no flag to disable — §10f) |

**Why even an isolated own-economy is "still messy" (the author's point f):** isolation changes trade, not how colonies
die (no clean destruction); it still risks random missions unless every node is hidden *and* hidden markets prove
excluded from the giver pool (unverified — §8); `econGroup` is runtime-only and must be re-applied across Nex events
(the chore that reads as "messy"); and a market-owner is still in the Nex census unless also hidden, but a hidden
multi-planet "home" is a contradiction. The fully-marketless route has none of these.

---

## 4. The three agent recommendations (adversarial advocates, preserved)

Each advocate built the strongest honest case for one model and was required to concede its weaknesses. The
economy advocate (arguing *for* B) is the most telling — its concessions are why B loses.

- **Advocate A (pure-script) — thesis:** the only model satisfying the non-negotiable §3 identity without lore-breaking
  or fragile machinery; matches unanimous vanilla precedent. **Honest concessions:** legibility/UI must be hand-rolled
  (the named weakness in `EXILES_OMICRON_LOGISTICS.md`); the HostileActivity base-kill credit is player-colony-only;
  "station-mode fleet for a non-Remnants faction with null market" not fully traced; reusing `PirateBaseIntel`
  directly isn't viable (only `RaidIntel`/`AssembleStage` are faction-agnostic).
- **Advocate B (economy) — thesis:** the only model giving Omicron a persistent, sector-integrated footprint and a real
  conquerable objective; econ is cheap; vanilla's own raiding bases *are* markets. **Honest concessions (decisive):**
  "Fiction conflict is real and severe"; "Both vanilla precedents for AI factions are MARKETLESS — I am arguing against
  the only two shipped examples of Omicron's archetype"; static market size means it can't grow; Nex-conquerability is
  a genuine doc-grounded conflict; multi-market self-sufficiency is genuinely hard; "most of it can be obtained from a
  `setHidden`+`setEconGroup` base à la C **without** declaring Omicron a full economy participant."
- **Advocate C (pirate-base hybrid) — thesis:** the right pattern for forward *attack infrastructure*, not the whole
  identity; in practice it converges with A. **Honest concession (decisive):** "Once I strip the commander, submarkets,
  trade, and bar events, what remains is so close to the marketless station-mode source that **C collapses into A**" —
  and `PirateBaseIntel` can't be subclassed (its bar events/bounties/"deal" are hardcoded pirate flavor), so the "free
  legibility" is largely forfeited for a machine faction; the UI shell must be rewritten.

---

## 5. Decisive factors (verified against vanilla source)

1. **Vanilla precedent is unanimous and verified.** Both machine factions are marketless. `omega.faction` has no econ
   block, empty `illegalCommodities`/`ranks`/`posts`, `showInIntelTab:false`, automated-fleet flags
   (`postsNoBounties`, `fightToTheLast`, `noAICoresFromAutomatedDefenses`, `simulatorData.omegaAICores`) — and grep of
   `data/campaign/econ/` + `economy.json` shows **Omega owns no declared market and isn't in the economy registry at
   all**. The Remnant nexus deliberately avoids a market: `RemnantThemeGenerator.addBattlestations()` has
   `fleet.setMarket(market)` **commented out (~L671)** with the in-code rationale *"can actually trade etc then, no good."*
2. **A market inescapably imports a commander, trade, and population** — each a §3 violation for a zero-population
   "misclassification not malice" machine. You can't suppress all of them and still have "a real economy faction."
3. **Nexerelin conquerability (confirmed; Nex not installed here, so from `EXILES_ECONOMY_MAP.md` §9b).** A faction that
   owns ≥1 market AND is registered (`mod_factions.csv`, `playableFaction:true`) is added to `liveFactionIds` and gets
   `createDiplomacyProfile(...)` → visible, peace-able, **invadable, conquerable**. Escape hatches are half-states:
   omit it from `mod_factions.csv` → market-owner silently absent from diplomacy; or it's still conquerable *by* others.
   A **marketless** faction is invisible to the census (`reinitLiveFactions` gates on "owns ≥1 market right now") → no
   diplomacy profile → nothing to conquer. The script model sidesteps the whole problem.
4. **The weaken/slow/stop verbs are free in `SourceBasedFleetManager` and use the SAME entities** as the already-decided
   attack lever → faction-wide answer and logistics answer converge on marketless.
5. **NPC market size is static** (`EXILES_ECONOMY_MAP.md` §10b: `CoreImmigrationPluginImpl.increaseMarketSize()` bails on
   `!isPlayerOwned()`). An "escalating machine" can't escalate through an economy; escalation is scripted via the
   Director scalar regardless — exactly what `EXILES_OMICRON_LOGISTICS.md` already chose.
6. **Vanilla's own raiding bases are hidden, isolated, disposable nodes** (next section) — proving "strike from forward
   bases" does **not** require economy participation. This caps C to a borrowable pattern rather than an identity.

### 5a. Decision-critical claims, as verified
| Claim | Verdict | Core evidence |
|---|---|---|
| (a) A market-owning faction becomes Nex peace-able / invadable / **conquerable** | **Confirmed** | `EXILES_ECONOMY_MAP.md` §9b (`SectorManager.reinitLiveFactions` → `createDiplomacyProfile`); §9f (Omicron not yet in `mod_factions.csv`) |
| (b) Vanilla's other machine faction **Omega is marketless** (uniform AI-faction precedent) | **Confirmed** | `omega.faction` has no econ block; absent from `economy.json`; `RemnantThemeGenerator:~671` `setMarket` commented |
| (c) A universally-hostile Omicron economy is **accessibility-strangled** unless econ-group-isolated; isolation's Nex interaction is untested | **Confirmed** | §10g (hostility penalty up to −1.0, size-weighted, binary at ≤−50); §10h (isolation drops hostile-weight ≈0 but Nex interaction "UNTESTED", runtime-only) |
| (d) **NPC market size is static** → an Omicron economy can't grow | **Confirmed** | §10b (`increaseMarketSize` bails on `!isPlayerOwned`) |
| (e) A **marketless** faction is invisible to the Nex census (sidesteps diplomacy-with-a-machine) | **Confirmed** | §9b ("no market → forced hidden + no diplomacy"; `factionRespawned` never fires for a faction that owns nothing) |

---

## 6. RECOVERED CODE MAP — vanilla pirate / Luddic-Path temporary-base lifecycle

This is the dossier piece that was previously lost. It is the **bridge** between models A and B, the closest vanilla
analog to a player-degradable Omicron, and the template for the Midan "strike from forward bases" framing. Read this
when building the Phase-1 Director / any forward-node mechanic.

**Classes (vanilla `…impl.campaign.intel.bases` / `…intel.raid` / `…procgen.themes`):**
`PirateBaseManager`, `PirateBaseIntel`, `LuddicPathBaseManager`, `LuddicPathBaseIntel`, `LuddicPathCells`,
`BaseEventManager`, `raid.AssembleStage`, `raid.PirateRaidAssembleStage`, `intel.events.HAPirateBaseDestroyedFactor`.

- **The base IS a real (hidden) market, not a bare entity.** Ctor builds a full `MarketAPI`: `createMarket(genUID,3)`,
  `setSize(3)`, **`setHidden(true)`**, adds **POPULATION + SPACEPORT + MILITARYBASE** industries + a tiered station
  industry, **SUBMARKET_OPEN + SUBMARKET_BLACK**, a `POPULATION_3` condition, a tariff, a **comm-directory base
  commander**, `setPrimaryEntity`/`setMarket`, **`setEconGroup(market.getId())`** isolation + `NO_DECIV_KEY`, then
  `economy.addMarket`. Patrols come from MILITARYBASE via `economyUpdated()` setting `PATROL_NUM_*` + fleet quality/size
  mults by tier. *(`PirateBaseIntel` ~L166-286; `setEconGroup` ~L250. `LuddicPathBaseIntel` ~L119-208; `setEconGroup`
  ~L193.)* **This is the OPPOSITE of the Remnant nexus** (which deliberately avoids `setMarket`) — and the reason
  adopting C wholesale re-imports B's §3 violations.
- **Siting & creation: runtime, not procgen.** Singleton manager (`PirateBaseManager` / `LuddicPathBaseManager extends
  BaseEventManager`, an `EveryFrameScript`) registered **once** via `sector.addScript(...)` guarded by `!hasScript(...)`
  (`CoreLifecyclePluginImpl` ~L725-733). On `IntervalUtil` ~10 days and below `currMax` (settings `min/maxPirateBases`)
  → `createEvent()` rolls `CHECK_PROB≈0.5`, picks an eligible system/tier/faction. **Eligibility** (`pickSystemForPirateBase`):
  not player-visited 45 days, not `RECENTLY_USED_FOR_BASE`, has a procgen theme tag, contains an **OBJECTIVE or
  STABLE_LOCATION** entity, **has NO existing markets**, no pulsar. **Faction opt-in:** only factions with custom
  **`makesPirateBases:true`** (`pirates.faction:250`).
- **"Temporary" = persists until DESTROYED, then cooldown.** No lifespan timer ends the base (the expiry block is
  commented out); it self-escalates tier over months. On death, `incrDestroyed()` sets `numSpawnChecksToSkip` and
  `markRecentlyUsedForBase()` stamps the system for ~120-180 days. **`pickTier` pushes higher tiers with `numDestroyed`
  (`days += numDestroyed*200`) — the wrong-direction escalation to DROP for Omicron** (mirror of the Remnant
  `totalLost*4` term the logistics doc already drops).
- **Destruction & teardown: one `FleetEventListener`.** Intel registers as listener on the station's defense fleet
  (`Misc.getStationFleet`, re-bound each frame). On that fleet's battle-destruction,
  `reportFleetDespawnedToListener` → `Misc.fadeAndExpire(entity)` + `endAfterDelay()`, pays bounty if player involved,
  then `incrDestroyed()`; `notifyEnding()` cleans up `removeListener` / `removeMarket` / `removeUpdateListener` /
  `removeRadioChatter`. **Binary** (alive = full raids / dead = gone) — **no graduated integrity** (Omicron wants graduated).
- **Raids from the base (the reusable verb).** Monthly `IntervalUtil(20,40)`; `pickTarget()` weights every system with
  markets by `Σ size × distance-falloff` (`mult = 1 − max(0,distLY−10)/10`, clamped 0.1-1; player colonies only within
  15 LY unless forced). Gated by `raidTimeoutMonths` + ~0.2 prob → `startRaid(target, getBaseRaidFP())`. **`getBaseRaidFP`
  = 100/150/250/300/450 by tier × 0.75-1.25.** `startRaid` builds a 4-stage **`RaidIntel`** (`PirateRaidAssembleStage` →
  Travel → Action → Return); `AssembleStage.addRoutesAsNeeded()` drains the FP pool on `IntervalUtil(0.1,0.2)`, spawning
  one `RouteManager` fleet at a time (`FP_SMALL=20 / MEDIUM=45 / LARGE=85`, capped to the faction's
  `getApproximateMaxFPPerFleet`) with per-fleet `abortFP` thresholds. **Defeating an in-flight raid does NOT kill the
  base** but raises `raidTimeoutMonths` (a temporary reprieve — mirrors the Remnant decaying-suppression verb).
- **Kill-the-source cutoff (the key player-degrade hook).** `AssembleStage` checks each tick
  `if (!market.isInEconomy() || !market.getPrimaryEntity().isAlive()) sources.remove(market)` → **destroy the Nexus and
  in-flight raids stop reinforcing**; empty sources → stage FAILURE. This is the exact "attack the logistics → attacks
  weaken/stop" loop, available regardless of whether the source backs a market.
- **Legibility is built-in (what model A must hand-roll).** Spawns hidden+discoverable (`setDiscoverable`,
  `sensorProfile`, +5000 range); titles read "X Base — Exact Location Unknown" until close (`makeKnown` on proximity);
  `PortsideBarData` seeds bar rumors; tags `INTEL_EXPLORATION` / `INTEL_COLONIES` (when a player colony is affected) /
  `INTEL_BOUNTY` (tier-scaled destruction bounty); draws a target arrow; `createSmallDescription` narrates tier strength
  + current target. **`PirateBaseIntel` is NOT subclassable for a machine** — bar events, underworld "deal with the
  commander," and bounties are hardcoded pirate flavor; only `RaidIntel` + `AssembleStage` are faction-agnostic. The
  intel/UI shell must be **rewritten**, not inherited.
- **Luddic-Path differs.** Structurally near-identical hidden+isolated market, but its `startRaid` block is **commented
  out** — the Path base dispatches **no fleets**; instead it materially supports sabotage **CELLS** (`LuddicPathCells`,
  −1 stability) seeded by `LuddicPathBaseManager` on nearby **null-econ-group, size≥4** markets (`updateCellStatus`
  skips `getEconGroup()!=null`; interest scored by AI-core admin +10, per-industry `getPatherInterest`, ×0.1 for Church).
  2 tiers vs pirate's 5; commander `CONTACT_MILITARY` vs `CONTACT_UNDERWORLD`. → the "support remote effects, no direct
  fleets" variant; the pirate base is the "staging ground for raids" variant.
- **Econ-group isolation, proven.** Both base types call `setEconGroup(self)` + `NO_DECIV_KEY`. Per `EXILES_ECONOMY_MAP.md`
  §10g/§10h this makes supply/demand AND the accessibility hostility penalty compute **per-group**, so a universally-
  hostile market isn't strangled — **but only because it's a disposable, size-3, hidden, deciv-immune node not trying to
  sustain a 4-planet economy.** That does NOT license a full multi-planet Omicron *home* economy (which still needs the
  max-not-sum self-sufficiency pass, §7c, and has untested Nex interaction).
- **HostileActivity base-kill credit is player-colony-only.** `HAPirateBaseDestroyedFactor` knocks down the 0-600
  HostileActivity meter only when a base supporting activity **at the player's colonies** is destroyed. The core Omicron
  fantasy is **NPC-vs-NPC** (raiding Church/Path), so this is a **deferred bolt-on**, not the spine.

### 6a. The marketless source pattern (the spine, for reference)
- **`SourceBasedFleetManager.advance()`** gives weaken/slow/stop for free: `source.isAlive()` hard-gates all spawning
  (`source==null` → `currMax=0`); proximity-ramped `currMax`; decaying suppression `destroyed -= days/respawnDelay`,
  `currMax -= ceil(destroyed)`; `setDone(true)` once source dead + fleets empty. Bounds fleets `minFleets..maxFleets`,
  despawns excess via `despawn(PLAYER_FAR_AWAY)`, drops orphaned `!isAlive()` fleets each tick (satisfies CLAUDE.md §7
  leak discipline out of the box).
- **`RemnantThemeGenerator.addBattlestations()`** builds the station-mode source: `createEmptyFleet(REMNANTS,
  BATTLESTATION)` + a station ship member + `setStationMode(true)`, an AI-core commander (`ALPHA_CORE`, or the weaker
  **`$damagedStation`** 0.5-strength variant ← the literal seed for "Damaged Nexus"), combat-destroyable, **no
  `setMarket`** (commented ~L671).
- **`RemnantStationFleetManager.spawnFleet()`** drives raids/patrols off that marketless station via `FleetParamsV3`
  whose first arg (`source.getMarket()`) is simply **null**; its `totalLost*4` escalation term (~L88-91) is the
  wrong-direction term to **drop**. Listener hygiene: it registers its `EncounterPointProvider` as a **separate object
  (`epGen`)**, not `this`, because `SourceBasedFleetManager` already adds `this` to every spawned fleet — double-
  registration would double-fire `reportFleetDespawnedToListener`.

---

## 7. Concrete build plan (ratified)

> **Agreed sequencing (2026-06-24):** build the **logistics + Director framework first** — it's the substrate the
> in-game assets read from, so the framework comes before (and is then tuned *alongside*) asset placement. Expect a
> few iterative rounds of framework ↔ placement tuning, not a single pass. This is the heavier build and partly
> crosses the Phase-0/Phase-1 line by design.


- **Phase 0:** promote Nexus / Damaged Nexus / Sensor Array (`ExileOmicron0.java:103-128`) from bare `addCustomEntity`
  tokens to **destroyable station-mode fleet sources** (mirror `addBattlestations`; Damaged Nexus uses the
  `$damagedStation` 0.5 flag). This alone makes Omicron *do something* (it currently spawns nothing). Pair with the
  faction-file **identity polish** (the §5-checklist work scoped separately: machine-themed ranks/fleet names, finished
  UI colors, roster theming, name-capitalization, the `showInIntelTab` question).
- **Phase 1 (Sector Destabilization Director):** subclass `SourceBasedFleetManager` (drop `totalLost`); borrow
  `PirateBaseIntel.startRaid` + `pickTarget` + `RaidIntel`/`AssembleStage` for **NPC-colony (Church/Path) raids**;
  **hand-roll a bespoke intel/UI shell** (PirateBaseIntel's is pirate-hardcoded). Graduated integrity scalar per the
  logistics doc; HostileActivity meter deferred (player-colony-only).
- **Shared with Midan:** the stripped forward-node (C minus market/commander/trade) is the same shell Midan's
  "strike from forward bases" needs — build it once.

---

## 8. Unknowns to clear before building (from the agents' flagged gaps)

- **RESOLVED 2026-07-12:** a non-Remnants faction fleet created with a null source market retained its
  League-derived fleet character in the Midan throwaway test. The test script was removed after observation.
- The exact `FleetEventListener` destruction-detection bridge for an **Omicron** station-mode source (the pirate base keys
  off `Misc.getStationFleet(market)`; the marketless equivalent wiring wasn't opened).
- **`RouteManager`** per-raid fleet cap/despawn over a long campaign (the leak budget for the Phase-1 Director).
- Whether `THEME_REMNANT` / `THEME_REMNANT_MAIN` on the Omicron system **auto-spawns anything** or is inert (likely
  cosmetic/AI-hint; not confirmed against tag-consumption code).
- Whether tagging the system + a `makesPirateBases`-style flag could make the **Omicron system itself eligible for stray
  vanilla pirate bases** (eligibility = no-existing-markets + OBJECTIVE/STABLE_LOCATION + theme tag — the Omicron system
  matches several of these).
- `PlayerRelatedPirateBaseManager` (registered alongside `PirateBaseManager`) was not read — likely governs player-
  targeted bases; relevant only to the optional HostileActivity bolt-on.

---

## 9. Design questions — resolved vs still open

### Resolved 2026-06-27 (asset model + prototype)
- **What concrete assets does Omicron own?** → A **richer five-asset logistics chain** (Nexus engine + Foundry +
  Mining + Sensor Array + Damaged Nexus), each a destroyable combat target governing one threat axis
  (strength / cadence / precision). See **`EXILES_OMICRON_ASSET_MODEL.md`** for the full taxonomy + management model.
- **Logistics-node placement (was open #1 below)?** → **Home-system only for now.** All lever nodes live in the
  Omicron system; the forward/Midan-shared node is deferred to Phase 1.
- **Phase-0 station-mode promotion?** → **Built.** `ExileOmicron0.java` now creates the Nexus + Damaged Nexus as real
  station-mode battlestation fleets (mirrors `addBattlestations`, no `setMarket`); compiles clean under `javac`.
  Deferred next: the `SourceBasedFleetManager` Director + graduated integrity + lever-node defenses.

### Resolved 2026-06-24 (by author)
- **Economy footprint?** → **No.** Script-only; Omicron has no economy presence. Its physical footprint is the
  destroyable Nexus + logistics entities + roving fleets (a *thing to assault*, not a market on the economy screen).
- **Is Nexerelin a target?** → **Yes, in scope** — and the marketless route is the compat path (Omicron stays out of
  Nex diplomacy entirely, like Remnants/Omega). No design compromise required.
- **Factions-tab visibility?** → **Handle like the Remnants** (`showInIntelTab:false`, no Nex diplomacy profile).
  Showing Omicron on the faction list at game start isn't narratively sensible. Intentional, not a bug (see §2a).
- **Does Omicron get destroyable logistics nodes?** → **Yes.** Script-controlled logistics entities that flip triggers
  when destroyed, feeding the Director's "score." Raidable/temporarily-disable-able "logistics points" are wanted too.

### Still open (design / sequencing — not blocking the §2a decision)
1. **~~Logistics-node placement & Midan sharing.~~** RESOLVED 2026-06-27 → **home-system only** for now (see above /
   `EXILES_OMICRON_ASSET_MODEL.md`). The stripped forward-node shell shared with Midan is deferred to Phase 1.
2. **How much hand-rolled legibility/UI now vs deferred?** Pure-script must hand-roll the "a node is here raiding X,
   destroy it to weaken Omicron" intel surface (model A's named weakness). OK to ship minimal UI in Phase 0 and add the
   intel shell in Phase 1?
3. **Phase-0 vs Phase-1 split for the Director.** Recommendation stands: Phase 0 makes the Nexus a destroyable
   station-mode source (so Omicron *does something*); defer NPC-colony raid dispatch + the scoring Director to Phase 1.
4. **Keep or drop the `THEME_REMNANT` / `THEME_REMNANT_MAIN` tags** on the Omicron system? Likely cosmetic; small risk
   of vanilla-manager interaction (§8 verify item).
5. **Tri-Tachyon defensive behavior — next design priority (2026-07-18).** Omicron currently dispatches raids
   against configured Luddic Church/Path markets but does not perform its intended defensive role for
   Tri-Tachyon. Before implementation, decide the trigger, protected scope, qualifying attackers, player-facing
   legibility, quest-state interaction, and how intervention works without making marketless Omicron a normal
   diplomacy participant. No specific defense mechanic is ratified yet.

---

## 10. Relation to other docs

- **`EXILES_OMICRON_LOGISTICS.md`** — the *narrower* attack-degradation-lever decision (script vs economy for the
  logistics). This file **generalizes** it to the whole faction and recovers the pirate-base code map that doc's raw
  dossier lost.
- **`EXILES_ECONOMY_MAP.md`** — §7c (max-not-sum self-sufficiency), §9 (Nex census / faction visibility), §10b (static
  NPC size), §10g (accessibility hostility penalty), §10h (econ-group isolation), §10i (AI cores via econ JSON).
- **`CLAUDE.md`** — §3 (Omicron guardrails: misclassification not malice, zero-population, must-stay-hostile), §6 (build
  order: factions → activity → quests), §7 (performance / leak discipline).

## 11. Provenance

7-agent background workflow, 2026-06-24 (`omicron-economy-vs-script`): 3 gap-research/verification agents (vanilla
source + mod docs), 3 adversarial design advocates (one each for A/B/C, incl. one arguing *for* economy), 1 synthesis.
~589k subagent tokens, 96 tool uses. The recommendation is the synthesis verdict (high confidence); it was
**ratified by the author 2026-06-24** (script-only / marketless; see §2a).
