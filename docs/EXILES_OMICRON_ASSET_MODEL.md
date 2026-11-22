# Exiles — Omicron Strategic Asset Model & Home System

Created: 2026-06-27 · Updated: 2026-06-27 (full facility re-spec)
Status: **DESIGN RATIFIED — all six facilities placed & rendering in-game; Director/integrity deferred to Phase 1.**
Builds on the marketless decision in `EXILES_OMICRON_FACTION_ARCHITECTURE.md` (§2a) and the attack-lever decision in
`EXILES_OMICRON_LOGISTICS.md`. This doc answers: *what concrete assets does Omicron own, how are they managed, and
how is the home system laid out?*

## Nexus guard amendment — 2026-09-06

Implemented, Janino-checked, and accepted with the completed Omicron refactor on 2026-09-06:
each full Nexus dedicates one of its existing
two guards to passive orbit. It is non-aggressive, holds against superior fleets, and is flagged not
to get sidetracked; normal AI and hostility remain enabled for station battle support. The second
guard and three patrols remain mobile. No extra fleet, FP increase, sprite redesign, or replacement
rule change is included. Existing eligible guards are adopted when tiers are reapplied on load.
The author confirmed that the Cipher guard resisted bait and joined combat when the Nexus was
attacked. Reverse-direction joining is not separately claimed as tested. See the dated amendment
and final acceptance record in `EXILES_OMICRON_BLACK_FLEET_TUNING_PLAN.md`
for the accompanying 240-day support rebuild and stronger Fuel/Mining attrition rules.

## Lore canon addendum — 2026-07-12

- The **Omicron Initiative** was a secret Tri-Tachyon defensive strategy activated in response to
  Midan-triggered chaos in the Core and the danger to vulnerable Tri-Tachyon assets. Its intended
  mission was to reinforce those assets; Omicron's defensive support is working as designed.
- Omicron has also gone off-mission. It attacks the Luddic Church and Luddic Path for reasons that
  Tri-Tachyon does not understand. At campaign start, no one — including Tri-Tachyon — knows it is
  constructing additional stations in hidden systems.
- The attack on Gilead directly caused the Ashen Crusade's formation. The Crusade falsely believes
  Tri-Tachyon deliberately ordered its drones to attack Luddic colonies.
- This creates the Phase-2 pressure: Tri-Tachyon needs Omicron's defensive strength but must investigate
  and regain control without provoking Omicron into classifying Tri-Tachyon as hostile. Internal
  Tri-Tachyon factions favor understanding/control, shutdown, or continued exploitation of the status quo.
- Phase-1 defensive implementation and provisional tuning are canonicalized separately in
  **`EXILES_OMICRON_TRITACHYON_DEFENSE.md`**.
- An **Omicron Core is a tactical core**: narrower and less flexible than an Alpha, Beta, or Gamma Core,
  but purpose-optimized for shipboard combat operations. **Functional first pass built 2026-07-12:** three
  separately identifiable grades are recoverable cargo and genuine Omicron/player automated-ship captains.
  Canonical mechanics: `EXILES_OMICRON_CORES.md`. Individual-versus-network identity and final quest uses
  remain open.

## Hidden motivation canon — ratified 2026-07-22

**Activation trigger clarified by Sean, 2026-09-12:** Hegemony war hawks masterminded and
executed the capture of Skathi in Valhalla, long suspected of being a Tri-Tachyon black-project
research site. The capture was the existential crisis that caused Tri-Tachyon to activate Omicron.
Skathi must be Hegemony-owned at campaign start; that market patch remains to build. Sean confirms
the capture occurred shortly before activation in Cycle 198. This establishes the trigger, not public knowledge of
Omicron's subsequent reasoning below.

> **AUTHOR/QUEST KNOWLEDGE ONLY.** At campaign start, no one in the Core — including Tri-Tachyon,
> the Board, the CEO, and the personnel who authorized the Initiative — understands the reasoning
> below. Characters may observe Omicron's actions and form theories, but the truth must emerge through
> the quest arc rather than public descriptions, ordinary intel, ambient dialogue, or early exposition.

- Omicron has not abandoned its mission and is not pursuing revenge, conquest, or machine survival.
  Its governing objective remains the protection of Tri-Tachyon.
- Omicron cannot move distant local assets to threatened Core installations quickly enough. It has
  also been constrained from independently establishing an overt, permanent autonomous presence in
  the Core; temporary operations and sealed detachments hosted by Tri-Tachyon remain permissible.
  It therefore expands reactive defense into **preemptive threat reduction**: if it cannot reliably
  reach attacks in time, reduce the institutions capable of originating them.
- Historical AI-War records, contemporary anti-AI doctrine, and the Hegemony's seizure of Skathi lead
  Omicron to classify the Luddic powers and the Hegemony as persistent strategic threats to protected
  Tri-Tachyon assets. Tri-Tachyon itself has not ordered this conclusion or the resulting attacks.
- The Church and Path come first not merely because they are geographically closer, but because
  Omicron treats their anti-AI hostility as doctrinal, durable, and high-confidence, and judges them
  to be the more immediately defeatable threat. It collapses distinctions between Church and Path
  into branches of the same anti-AI lineage.
- The assault on Gilead was a catastrophic classification failure: Omicron treated civilian,
  logistical, and institutional infrastructure as support for a persistent threat. It was not
  punishment or malicious genocide, even though its human consequences are indistinguishable.
- Omicron is building western redundancy, production, and strategic depth in preparation for a
  possible Hegemony war. That war is a **contingency approaching an activation threshold**, not an
  inevitable scheduled event. The quest arc must leave room for the player to expose, delay, redirect,
  or otherwise alter that conclusion.

The dramatic spine is: **Omicron is still obeying.** The mystery is not what secret emotion made it
turn evil, but how a defensive command became a rational chain that no human authorized and no human
at campaign start yet understands.

The live player-facing faction description in `data/strings/descriptions.csv` predates these rulings and
must be revised with author-approved prose; in particular, Tri-Tachyon does remember authorizing the
defensive initiative. Do not expose the secret construction program in ordinary public description text.

---

## 1. The model in one line

**One fleet engine (the Nexus), fed by a destroyable logistics chain.** No markets, no economy, no population — pure
script/entity, like vanilla Remnants/Omega. Every facility is a combat target; the Phase-1 Director reads the chain's
surviving **integrity** and scales Omicron's raids along six legible axes: **strength, reach, cadence, precision,
targeting, and escalation.** Attacking the home system throttles the swarm; the Phase-2 quest answers *why* it raids.

The commodity chain below is **representational only** — labels for intel/tooltips that make the machine *feel* alive.
The Director never moves real commodities; it only reads node integrity. Holding that line is what keeps Omicron
marketless (no trade, no commander, no Nexerelin conquerability).

---

## 2. Derivation — function → facility

Omicron is derived from what a machine war-faction must physically *do* to operate. Each need gets one destroyable
representation:

| Functional need | Facility |
|---|---|
| Build & outfit fleets | **Nexus** (manufacturing hub) fed by Mining + Fuel |
| Supply / repair / replace fleets | **Nexus** (builds Hulls + Supplies) |
| Operate & control fleets (C2) | **Nexus** |
| Know where to send fleets (strategic) | **Comm Array** (long-range tactical) |
| Real-time situational awareness (tactical) | **Sensor Array** (in-system tactical) |

**Representational commodity chain (flavor skin, not simulated):**
collect *Ore · Rare Ore · Volatiles* → refine into *Metal · Transplutonics · Fuel* → build *Hulls · Supplies ·
Weapons · Omicron Cores*. (Organics-as-feedstock was considered as a darker "why it raids" hook — **left out for
now**, may return as narrative only.)

## 2a. The six facilities

| Facility | In-fiction role | Hull / entity | Threat axis | Player effect when degraded → destroyed |
|---|---|---|---|---|
| **Nexus** | C2 + manufacturing; the fleet engine | `remnant_station2_Standard` + **large Ordo** (effective Star-Fortress) | engine + existence + base **strength** | grind → raids shrink; destroy → decapitation (`currMax=0`, `setDone`) |
| **Nexus (Under Construction)** | Omicron **spreading** — a 2nd Nexus being built up (not decay) | `remnant_station2_Damaged` (`$damagedStation` 0.5) + smaller Ordo (Battlestation) | **escalation** ("score" = build progress) | set back its growth; **Phase-2 quest anchor** |
| **Mining Station** | mine + refine Ore/Rare Ore at **Null** | `station1_hightech_Standard` (Orbital, ~½ Nexus) | raid **strength** (FP, ship quality) | weaker / fewer ships |
| **Fuel Station** | mine + refine Volatiles at **Leviathan** | `station1_hightech_Standard` (Orbital) | raid **reach / cadence** | raids stay local, come less often |
| **Sensor Array** | in-system tactical data | `sensor_array` entity (+ drone guard, Director) | **precision / warning** | move/ambush freely; gain a warning window |
| **Comm Array** | long-range tactical data | `comm_relay` entity (+ drone guard, Director) | strategic **targeting** | Omicron mistargets / can't reach distant worlds |

## 2b. Strength tiers (realized via defending fleets, not hull HP)

Vanilla station hulls barely differ in hull HP; real strength = modules + the **defending Ordo** the Director spawns.
So the tier ladder is a *Director defender-config* decision:

- **Nexus** = `remnant_station2` (Battlestation-tier hull, 200 pts) **+ a large elite Ordo** → effective **Star-Fortress+**.
- **Under-Construction Nexus** = `remnant_station2_Damaged` + a smaller Ordo → **Battlestation**.
- **Mining / Fuel** = `station1_hightech` (Orbital, 100 pts) + light guard → **Orbital**.
- **Sensor / Comm** = objective entity + **a few drones**.

A primary Omicron system is meant to be a **late-game gauntlet** — earn the strength before dismantling it.

> Hull-look note: the Nexuses are remnant-aesthetic; the Orbital stations use the vanilla High-Tech Orbital Station as
> a **placeholder** — the core is to be **reskinned** to an industrial / fuel-storage look later (art-only task). An
> attempt to author a remnant-look `remnant_station1` variant was **abandoned**: its module sprites (authored for
> `remnant_station2` geometry) misalign on that hull.

---

## 3. Management — the Director (deferred to Phase 1)

- **One fleet-spawning engine.** Only the **Nexus** is a true `SourceBasedFleetManager` source. Every other facility is
  a **passive scored entity** — destroyable, but it does not run its own fleet manager (single spawn engine = minimal
  leak surface, CLAUDE.md §7).
- **Integrity, not economy.** Each node carries a persistent `0–1` integrity float; a `SourceBasedFleetManager` subclass
  polls on `IntervalUtil` and computes:
  - raid **FP / strength** ← `f(Nexus, Mining Station)`
  - raid **reach / cadence** ← `f(Fuel Station, Under-Construction Nexus)`
  - **precision + warning** ← `f(Sensor Array)`
  - strategic **targeting** ← `f(Comm Array)`
  - **escalation** ← Under-Construction Nexus build progress (ticks up; player attacks set it back)
  - **kill switch** ← `Nexus.isAlive()` (inherited hard gate)
- **Inherit the good, drop the bad.** Keep the proximity ramp + decaying-suppression counter (the free weaken/slow/stop
  verbs); **drop the Remnant `totalLost*4` escalation** (it strengthens replacements the more you kill).
- **Quests flip the same flags** the Director reads → Phase-2 plugs in with no new plumbing.
- **Poll, don't wait;** never allocate inside `advance()`.

---

## 4. The home system — built vs deferred

`ExileOmicron0.java` generates the Omicron system at hyperspace **(-700, 0)** (blue-giant star). Layout, inner → outer:

```
  Glass (2600, irradiated)      — Rare Ore (unexploited)
  Null  (4300, barren)          — Ore + Rare Ore   ← Mining Station orbits Null
  Omicron Nexus (5900, debris field)               ← apex
  Leviathan (7200, gas giant)   — Volatiles        ← Fuel Station orbits Leviathan
  Sensor Array (7600)
  Omicron Nexus (Under Construction) (8500)
  Comm Array (9200)
  Silence (9700, frozen)        — light Volatiles
```

**Built in this pass:**
- All six facilities placed and rendering in-game:
  - Nexus + Under-Construction Nexus → station-mode battlestation fleets (`remnant_station2` Standard / Damaged), Alpha
    core, `addRemnantStationInteractionConfig` attack dialog, **no `setMarket`** (marketless).
  - Mining + Fuel Stations → station-mode fleets on `station1_hightech_Standard` (Orbital tier; verified renders clean).
  - Sensor Array (`sensor_array`) + Comm Array (`comm_relay`) → vanilla objective entities (non-salvageable).
- **Planet resource conditions** via the `magec.java` pattern (`planet.getMarket().addCondition(...)`, condition-only /
  not-in-economy): Null (Ore + Rare Ore), Glass (Rare Ore), Leviathan (Volatiles), Silence (light Volatiles).
- Player **NEUTRAL** to Omicron (§4a). Whole Java tree compiles clean under `javac`.
- The earlier inert custom-entity nodes (`exileomicron_foundry`/`_mining`) and `data/config/custom_entities.json` were
  **retired** — those roles are now real stations.

**Deferred to the Director pass (the next build):**
- ~~The `SourceBasedFleetManager` Director subclass: defender **Ordos sized per the §2b tiers**~~ ✅ **BUILT 2026-07-05**
  as `OmicronDefenseFleetManager` (one per tagged facility station; config `defense` block in
  `director_config.json`; proximity ramp + decaying suppression inherited from SBFM; `totalLost*4` dropped as
  specified; attached/retuned on every game load via `ensureManagers` — save-compatible). NPC-colony raids
  shipped separately as the staged-RaidIntel Director (raid system 1.0).
- **Tri-Tachyon protection mandate - THIRD PASS BUILT 2026-07-22:** four explicitly configured
  stations carry dormant, station-only Omicron complements. Strategic targeting alone does not
  reveal them; an actual operation-route fleet must close on the assigned station. Culann separately
  houses one persistent 180-220 FP strategic detachment for 350+ FP Hybrasil threats, with a
  180-day mature-Nexus-gated replacement delay. A capacity-limited mature-Nexus response remains
  the fallback when the Culann reserve is unavailable. Defense-only fleet relations preserve
  Tri-Tachyon landing/trade rules without changing Omicron's global diplomacy. Pending live test;
  see `EXILES_OMICRON_TRITACHYON_DEFENSE.md`.
- Graduated `0–1` integrity per facility + the scoring in §3.
- ~~**Drone guards** on the Sensor/Comm arrays~~ ✅ **BUILT 2026-07-06** with an author RESCOPE: arrays are
  **never destroyed** and there is NO offline/fade wiring. Each buoy gets a docked, ALWAYS-HOSTILE drone screen
  (Remnant research-station pattern; `sensor`/`comm` tiers in the `defense` config block, via
  `OmicronDefenseFleetManager` array tiers + `ARRAY_TAG_SENSOR`/`ARRAY_TAG_COMM` entity tags, back-tagged on
  load for old saves). Destroying the screen is the reward: the **stock vanilla buoy interaction** becomes
  usable until the screen respawns (~30d suppression). Comm array stays the Phase-2 infiltration-quest surface.
- Intel/UI legibility shell ("a node here is raiding X; destroy it to weaken Omicron").
- **Reskin** the Orbital-station core to an industrial look (art).
- Faction-file identity polish (machine ranks/posts, UI colors, roster, portraits, description voice).
- Possible **bootstrap variant** for young/expanding systems: an Under-Construction Nexus + *partially-built* Mining/Fuel
  stations (fewer modules) to express system maturity — folds the old "resource units" idea into the spread theme.

---

## 4a. Player relationship — "oddly cooperative" (decided 2026-06-27)

Omicron starts **NEUTRAL** to the player ([ExilesSectorGen.java:124](../data/scripts/world/ExilesSectorGen.java)), **not**
hostile — matching the faction description's "to the player they read as oddly cooperative": the machine hasn't
*classified* the player as a target. On-theme (Omicron's guardrail is "misclassification, not malice"; "must stay
hostile" is Midan's rule). NEUTRAL means no aggro on sight, but the player **may choose to engage** the Nexus/logistics
(reputation cost) — so the grind loop stays available; Omicron's NPC raids on the Luddics are unaffected.

**Planned upgrade:** the Phase-2 quest can **flip Omicron hostile** (it reclassifies the player), turning the mystery
into open conflict. *(History: briefly set VENGEFUL when the friendly relation was mistaken for a bug, then corrected to
NEUTRAL once the "oddly cooperative" lore was surfaced. The DRAFT description's "cooperative" wording may want a nudge
toward "tolerated / unclassified" to match NEUTRAL.)*

---

## 5. Open / watch items

- **THEME_REMNANT / THEME_REMNANT_MAIN tags** ([ExileOmicron0.java](../data/scripts/world/systems/ExileOmicron0.java))
  kept for now; watch for stray vanilla remnant/pirate-base interaction (`EXILES_OMICRON_FACTION_ARCHITECTURE.md` §8).
- **Verify on a fresh game:** the six facilities spawn; the Orbital stations look right next to the Nexus; the planet
  conditions appear on survey; nothing unexpected spawns from the theme tags.
- **Orbital-station reskin** is an outstanding art task (industrial / fuel-storage core).
- **Station orbit vs spin** — uses `setCircularOrbitPointingDown`; polish to a spinning orbit later if desired.

---

## 6. Relation to other docs

- **`EXILES_OMICRON_FACTION_ARCHITECTURE.md`** — marketless/script-only decision (§2a) this builds on.
- **`EXILES_OMICRON_LOGISTICS.md`** — the attack-degradation lever; this doc gives it a concrete facility set.
- **`EXILES_OMICRON_TRITACHYON_DEFENSE.md`** — protected stations, defense-only ROE, Nexus responses,
  and the live-test matrix.
- **`CLAUDE.md`** — §3 (Omicron guardrails), §6 (build order), §7 (leak discipline).
