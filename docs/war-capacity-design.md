# Exiles — Omicron War Capacity (Asset-Coupled Raid Scaling)

Created: 2026-07-01 · Revised: 2026-07-01 (**v2 — per-node rework after author review**)
Status: **RATIFIED 2026-07-01 (v2 decisions below).** Phase B deliverable of the faction-director
briefing. **This document is the contract the Phase-2 quest work codes against** — stat IDs,
modifier IDs, and persistent keys defined here must not be renamed once a campaign can save
with them.

## 2026-08-30 scheduler and Lattice amendment, updated 2026-09-06 (supersedes conflicting sections below)

The original per-node scheduler and faction-wide FP escalation model are retained below as design
history, but are no longer the live operation contract. The approved first-pass regulator is:

- one faction readiness pool, clamped to 0–2, with raids costing 1 and incursions costing 2;
- annual readiness `9 × (effective Nexus / 4) × (0.2 + 0.8 × effective Fuel / 4)`;
- faction request clocks of 60–90 days for raids and 180–270 days for incursions;
- at most two live offensive operations globally, at most one incursion, and one sourced operation
  per Nexus; strategic Tri-Tachyon responses occupy only their Nexus source slot;
- combat FP matures independently over 1,080 days on a `progress^1.5` curve, then Mining capacity
  scales the WHOLE interpolated combat budget via `0.2 + 0.8 × clamp(effective Mining / 4, 0, 1)`;
- raids interpolate from 70–120 to 200–360 FP; incursions from 180–300 to 700–1,100 FP;
- an incursion request reserves readiness and has deterministic priority; temporarily unavailable
  capacity defers without cost, true target-planning failure spends 25%, and no enemies uses a
  longer retry;
- the quest switch `$exiles_omicron_incursions_enabled` suppresses the incursion clock, request,
  and reservation while false; the current testing default is true;
- Lattice construction is a separate 540-surviving-day clock: Mining at 50%, raid sourcing with
  0.5 effective Nexus at 75%, and full-Nexus completion at 100%. It then becomes eligible for
  incursions and strategic responses;
- the Tri-Tachyon umbrella has its own 365-day clock and no longer depends on Lattice;
- operation objective count is fixed: raids use one market; incursions interpolate from a
  60%/40%/0% distribution for 1/2/3 objectives to 35%/50%/15% at maturity.

Approved support/guard tuning implemented 2026-09-06: Fuel and Mining rebuild after 240 days
(formerly 120). One of each full Nexus's existing two guards holds passive orbit; the other guard
and three patrols remain mobile. Counts, FP, and replacement suppression are unchanged. This pass
is Janino-checked and part of the **successfully completed, author-accepted Omicron refactor
(2026-09-06)**. The final combined support-loss test confirmed reduced production/strength and
station recovery; the author confirmed Cipher guard lure resistance and joining a Nexus attack.
Reverse-direction joining is not separately claimed as tested. Further balance changes are future
tuning, not outstanding refactor work. Full evidence is in the tuning plan's completion record.
Before Lattice's Mining Station exists, three effective Mining stations give 80% offensive strength.
The existing first-successful-launch maturity-clock behavior is deliberately unchanged.

The complete rationale, formulas, system asset distribution, failure semantics, and live-test
matrix are in `EXILES_OMICRON_BLACK_FLEET_TUNING_PLAN.md` §9–11. Existing stat IDs and persistent
keys remain available for quest/save compatibility unless the amendment explicitly assigns them a
new role.

**Maintenance note 2026-07-12 — fleet display names:** Starsector's campaign tooltip automatically
prepends the faction display name. Omicron raid, defense, and station fleet object names must therefore
remain unprefixed (`Raiders`, `Defense Pattern`, `Nexus`, etc.); setting an object name such as
`Omicron Raiders` renders as the duplicated `Omicron Omicron Raiders`. Fixed across all four nodes,
including persisted rebuild specs generated for new campaigns.

**Capacity amendment 2026-07-19 — Tri-Tachyon response fleets:** a live strategic-defense response
counts against both archetype checks for its launching node. With the current caps, one response blocks
that node's single incursion slot and consumes one of its two raid slots until the fleet returns or is
destroyed. Response selection also refuses a node already at either cap. This prevents defensive support
from becoming a free parallel fleet budget. Full behavior: `EXILES_OMICRON_TRITACHYON_DEFENSE.md`.

**Capacity amendment 2026-07-22 - Culann strategic detachment:** Hybrasil now checks its persistent,
pre-positioned 180-220 FP Culann detachment before requesting a Nexus response. This reserve is not a
Director node and consumes no raid/incursion capacity; its constraint is physical attrition plus a
180-day, mature-Nexus-gated replacement delay. It answers only 350+ FP threats. If unavailable, the
2026-07-19 Nexus-capacity rules still govern the fallback response.

Builds on `EXILES_OMICRON_ASSET_MODEL.md` (six-facility taxonomy, ratified 2026-06-27). Where the
external briefing proposed a **single** capacity scalar, this keeps the ratified **multi-axis**
model. Ratified session decisions (2026-07-01, incl. the v2 author-review round):

1. **Multi-axis** capacity, one `MutableStat` per axis — not a single scalar.
2. **Binary facility integrity** + temporary timed modifiers; the facility chain is the gradient.
3. **PER-NODE scoping.** Each Omicron star system is a **node** (its own Nexus gate, facilities,
   and axis stats). One Director loops all nodes. N=1 tonight; the final sector has **3 active
   systems + 1 under-construction system**, so a single facility kill weakens *one node*, not the
   faction. This is why per-facility numbers are soft (×0.75): the sector-wide dip at 3 nodes is
   ~8% per kill.
4. **Arrays are NOT capacity assets** (v2). Sensor + Comm Arrays become vanilla-function
   objectives guarded by docked drones (Remnant Research Station pattern): defeat the guard →
   interact like a vanilla sensor array / comm relay. The Comm Array is additionally the anchor
   for a planned infiltration quest (tap it for advance warning of raid destinations, locations
   of other Omicron systems, or similar time-limited intel). Rationale: soft targets must not be
   raid-suppression buttons ("fizzle" rejected as an easy shutdown; "odd targets" rejected as
   causally nonsensical when the player is the trigger).
5. **No absolute reach distances** (v2). Home systems will move far outside the Core
   (X ≈ −3,000 … −10,000), so LY constants would cripple raiding. Reach is **relative**: a node
   ranks its own eligible targets by distance and a degraded node is restricted to the nearest
   fraction of that list.
6. **Growth is staged and designed, not simulated.** The UC system matures through scripted
   build stages on the escalation clock, capping at the designed layout — the player *feels*
   growth; there is no open-ended von Neumann expansion. Player attacks setting back build
   progress is the Phase-2 quest verb.
7. **Build order** (briefing Phase C): hygiene fixes → config externalization → capacity stats →
   effect wiring → regen/expiry → threshold notifications → **RaidIntel/route migration last**
   (separate slice; brings survival gate + bounty/rep + real intel entries).
8. **Station REBUILD-while-Nexus-alive (RATIFIED 2026-07-06, amends #2's permanence — integrity
   stays binary).** A destroyed Mining/Fuel station **rebuilds 120 days** (config dial) after its
   death **if and only if its node's Nexus is still alive**; **if the in-system Nexus dies during
   the rebuild window, the pending rebuild is CANCELLED** — the station stays dead. Net rule:
   **Nexus decapitation is the only permanent kill.** Ship with an intel/notification ping
   ("reconstruction activity detected" flavor) so a returning station reads as machine
   relentlessness, not rubber-banding. Future polish (NOT scoped now): a visible under-construction
   entity phase the player can interrupt, dovetailing with the station visual split / spread build.

---

## 1. The model in one line

Each node carries a small set of **`MutableStat`s (base 1.0)**; destroyed facilities and
quest/event effects attach **multiplicative modifiers by ID**; the Director reads only the
clamped effective values when launching from that node. Asset death holds a modifier in place
while the facility is dead (the *cap*); temporary modifiers carry expiry timestamps the Director
prunes — that pruning IS "regeneration toward the asset-defined cap."

## 2. Nodes

A **node** = one Omicron star system, declared in config (`nodes` list, stable `id` + system
name). Per node: a Nexus (tag `exile_omicron_nexus`, resolved within that system), its logistics
facilities, its own raid/incursion timers, and its own axis stats.

- **Node decapitation:** no living Nexus in the node's system → that node launches nothing; its
  surviving facilities sit inert. Other nodes are unaffected.
- **Faction defeat:** ALL nodes decapitated → the Director goes idle (the existing kill-switch,
  generalized).
- **UC-node:** an under-construction system is a node whose Nexus is the `$damagedStation`
  UC-Nexus (carrying BOTH the nexus and ucnexus tags); it matures by growth stages and raids
  lightly only from its raids stage; it never launches incursions. It stays under-construction
  at full maturity — completion into a 4th full Nexus is a Phase-2 quest stake (ruling 2026-07-06).

**Node registry (SPREAD BUILD SHIPPED 2026-07-06)** — the far-west cluster, all beyond the
vanilla core's western edge (Tyle at −19100):

| Node id | System | Position | Role |
|---|---|---|---|
| `omicron_prime` | Omicron (blue giant) | (−36500, −2500) | active; relocated from (−700, 0); its old in-system UC-Nexus moved to Lattice |
| `omicron_cipher` | Cipher (orange) | (−40500, −6800) | active |
| `omicron_vigil` | Vigil (white) | (−39500, 3000) | active |
| `omicron_lattice` | Lattice (red dwarf) | (−33500, −7000) | **UC node** — deliberately the core-facing edge of the cluster (the machine grows toward the sector) |

*(Positions pushed a further −10000 X on Sean's 2026-07-06 map check — the first placement
still read as too close to the core.)*

Active nodes: Nexus + Mining + Fuel + Sensor/Comm arrays (fortress tag loadout per
EXILES_SYSTEM_TAG_INDEX §3 incl. THEME_SPECIAL + DO_NOT_RESPAWN_PLAYER_IN). Lattice: lone
UC-Nexus + placement specs for its future stations; no arrays (young system). `maxActive`
op caps are **per node** since the spread. System/planet names are Claude drafts — author
may rename (grep the system name in the gen class + this config's `nodes` row together).

## 3. The axes (stat registry)

All stats per node: base **1.0**, clamped to **[0.05, 2.0]** on read. Stat IDs are final.

| Stat ID | Axis | Read by the Director as | Fed by (asset) |
|---|---|---|---|
| `exiles_wc_strength` | raid strength | multiplier on that node's raid & incursion **FP** | Mining Station |
| `exiles_wc_cadence` | tempo | divisor on that node's **interval re-arm** (0.75 → intervals ~1.33× longer) | Fuel Station |
| `exiles_wc_reach` | reach | *(reserved — plumbing live)* **nearest-fraction** of the node's distance-ranked target list; no base-game feeder since v2.2 | — |
| `exiles_wc_escalation` | escalation | multiplier on FP on top of strength — the time ramp; **frozen at its kill-time value** if the node's UC-Nexus dies | UC-Nexus |
| `exiles_wc_targeting` | *(reserved)* | no base-game feeder — reserved for Phase-2 quest modifiers | — |
| `exiles_wc_precision` | *(reserved)* | no base-game feeder — reserved for Phase-2 quest modifiers | — |

The **Nexus is not an axis** — it is the node's hard kill switch (§2).

## 4. Asset registry (tags, per-node)

Assets are discovered **by tag within the node's system, from config** — never hardcoded. Tags
are applied in `ExileOmicron0.java` at generation (Nexus tag already exists).

| Asset key | Kind | Tag | Destroyed-modifiers (config values, first-pass) |
|---|---|---|---|
| `nexus` | station fleet | `exile_omicron_nexus` | none — node hard gate |
| `ucnexus` | station fleet | `exile_omicron_asset_ucnexus` | **`ucOnly:true`** (Lattice only) — growth accrual stops while dead (the escalation freeze) |
| `mining` | station fleet | `exile_omicron_asset_mining` | `exiles_wc_strength` × **0.75**; **`rebuildDays:120`** (#8) |
| `fuel` | station fleet | `exile_omicron_asset_fuel` | `exiles_wc_cadence` × **0.75** *(tempo only — v2.2)*; **`rebuildDays:120`** (#8) |

**Placement specs (spread build):** each system generator persists every station's placement
as `$exiles_omicron_station_spec_<nodeId>_<key>` (String: focusEntityId|variant|name|angle|
radius|days|damaged — `OmicronStationBuilder.writeSpec`), so the Director can respawn a
rebuilt station (#8) or stage-spawn Lattice's stations without hardcoded placement. The
war-capacity state also tracks per-station **built flags** — a UC system's not-yet-built
stations are excluded from the destroyed-scan (they'd otherwise read as dead on day 0).

> **v2.2 (author decision 2026-07-02): fuel affects TEMPO only.** The reach effect was removed —
> gameplay-wise the Luddic targets cluster too tightly for it to matter, and lore-wise a destroyed
> refinery means each sortie takes longer to fuel; the ships' actual range is unchanged. The
> `exiles_wc_reach` stat and its nearest-fraction target filter remain fully plumbed but
> **reserved** (like targeting/precision): a quest can still ground a node via
> `modifyMult(nodeId, STAT_REACH, ...)` with zero new code. The legibility index now averages
> only over the axes the config actually feeds (`capacityAxes`, derived at load), so reserved
> axes never dilute the threshold bands.

**Not assets (v2):** Sensor Array (`exileomicron_sensor_array`) and Comm Array
(`exileomicron_comm_array`) — vanilla-function objectives with drone guards; see decision 4
above. Their build ships with the (deferred) guard slice, not with war capacity.

Destroyed-state detection, evaluated on the Director interval (never per frame):
- **Station fleets:** destroyed = no living tagged fleet in the node's system (station fleets
  `fadeAndExpire` on defeat).
- **Override flags** `$exiles_omicron_asset_<nodeId>_<key>_destroyed` (Boolean) take precedence
  over the scan — the quest phase's "sabotage without a battle" verb, and the revive verb
  (set false / clear + a living fleet → modifier removed automatically).

> Save-compat: tags apply at generation; pre-existing dev saves read untagged facilities as
> destroyed. Acceptable — the mod requires a new game by design (2026-06-15).

## 5. Modifier-ID registry

**Never invent ad-hoc modifier IDs — register new ones here first.** All lowercase,
prefix-namespaced.

| Modifier ID pattern | Source | Lifetime |
|---|---|---|
| `exiles_wc_asset_<assetKey>` | Director asset scan (facility destroyed) | while dead; removed on revive |
| `exiles_wc_disrupt_<source>` | temporary events | expiry timestamp, pruned by Director |
| `exiles_wc_quest_<questId>` | Phase-2 quests | quest-managed (duration or explicit remove) |
| `exiles_wc_debug` | console/testing | manual |

Reserved for Phase 2 (examples, not live): `exiles_wc_quest_tt_data_run`,
`exiles_wc_quest_ucnexus_setback`, `exiles_wc_quest_comm_tap` (targeting/precision debuffs via
the reserved stats).

## 6. Persistent-data key registry

| Key | Type | Status |
|---|---|---|
| `$exiles_omicron_war_capacity` | `OmicronWarCapacity` state object (nodeId → statId → MutableStat, + timed-modifier expiry table, + per-node frozen-escalation values, + last threshold band) | **NEW** — single source of truth |
| `$exiles_omicron_asset_<nodeId>_<key>_destroyed` | Boolean override flag | **NEW** |
| `$exiles_omicron_target_factions` | List of faction ids | kept as-is |
| `$exiles_omicron_raids_enabled` / `$exiles_omicron_incursions_enabled` | Boolean archetype switches (absent = config `enabled` default). Quest hooks: `OmicronDirector.setRaidsEnabled` / `setIncursionsEnabled`. Timers keep ticking while off — flipping on resumes at natural cadence, no backlog. | **NEW** (2026-07-03) |
| `$exiles_omicron_start_ts` | Long (escalation ramp baseline) | kept as-is |
| `$exiles_omicron_early_warning_days` | Float days. Ops launch HIDDEN and self-reveal when the raiders arrive (stock "Arrived in-system" update), on first player battle contact, or — if this is set — this many days before arrival (quest reward, e.g. 10–15). Read at op creation, so it affects future ops only. Unset/0 = no advance notice. | **NEW** (2026-07-04) |
| `$exiles_omicron_nexus_warning_shown` | Boolean, one-time. Set when the player first closes within 2500su of a live Nexus and the "the attack fleets come from here" sensor warning fires (OmicronDefenseFleetManager). | **NEW** (2026-07-05) |
| `$exiles_omicron_nexus_integrity` | Float 0–1 | **DEPRECATED** — Director no longer reads it |

**Reveal depends on vanilla's arrival gate — DO NOT override `updateRoutes()` in
`OmicronRaidActionStage` (reviewed 2026-09-19).** Vanilla `PirateRaidActionStage.updateRoutes()`
runs once on arrival and, when the raid cannot beat any candidate's defenses, clears the target
list and zeroes `maxDays`, so the op aborts on the next tick. `OmicronRaidActionStage.hasChosenTargets()`
reads that cleared list so the intel can stay hidden instead of announcing an op that is about to
fail (the Eos Exodus playtest, 2026-07-04). The same vanilla method also sets the op's clock to its
own 20–30 (+10–15 with a station) roll; Omicron exposes no window key, so nothing is silently
ignored. The Ashen rehearsal and Heg-League strike stages override this behaviour (Ashen doc
§14.11); Omicron deliberately keeps it. If a configured Omicron window is ever wanted, the override
must preserve the `targets`-empty signal that `hasChosenTargets()` relies on.

The state object lives in **sector persistent data** so quests attach modifiers via static
helpers without a Director handle. Named top-level class, XStream-safe (no anonymous classes,
explicit types, `readResolve` re-inits transients, raw collections with casts per the Janino
discipline).

**Quest-facing API (static, on `OmicronWarCapacity`):**

```java
OmicronWarCapacity.modifyMult(nodeId, statId, modifierId, mult, durationDays); // durationDays <= 0 => until removed
OmicronWarCapacity.unmodify(nodeId, statId, modifierId);
OmicronWarCapacity.getValue(nodeId, statId);       // clamped effective value
OmicronWarCapacity.modifyAllNodes(statId, modifierId, mult, durationDays); // faction-wide convenience
```

## 7. Config file (externalized tuning)

`data/config/exiles/director_config.json` — loose JSON, one top-level key per faction; only
`exileomicron` live. Loaded once into a transient holder at Director construction/`readResolve`
via `Global.getSettings().loadJSON(...)`; **code defaults = current constants** as fallback if
the file is missing/partial. Everything in `OmicronDirector`'s static-final block moves here:

```
{
  "exileomicron": {
    "nodes": [
      {"id": "omicron_prime", "system": "Omicron"},
      {"id": "omicron_cipher", "system": "Cipher"},
      {"id": "omicron_vigil", "system": "Vigil"},
      {"id": "omicron_lattice", "system": "Lattice", "uc": true}
    ],
    "growth": {"raidFpMult": 0.5,
               "stages": [{"at": 0.25, "build": "mining"}, {"at": 0.55, "build": "fuel"},
                          {"at": 0.85, "raids": true}]},
    "defaultTargetFactions": ["luddic_church", "luddic_path"],
    "raid":      {"intervalDays": 8,  "minFleets": 3, "maxFleets": 5, "fpMin": 30,  "fpMax": 60,
                  "maxActive": 8, "durationDays": 30, "returnDays": 60},
    "incursion": {"intervalDays": 40, "minFleets": 3, "maxFleets": 5, "fpMin": 60,  "fpMax": 100,
                  "heaviesMin": 1, "heaviesMax": 2, "heavyFpMin": 120, "heavyFpMax": 220,
                  "maxActive": 6, "warningDelayDays": 4},
    "disruption": {"shortMinDays": 20, "shortMaxDays": 30, "longMinDays": 30, "longMaxDays": 60,
                   "spaceportChance": 0.6, "farmingChance": 0.6, "otherChance": 0.5},
    "escalation": {"rampDays": 365, "cap": 1.0},
    "assets": [
      {"key": "ucnexus", "tag": "exile_omicron_asset_ucnexus", "freezeEscalation": true},
      {"key": "mining",  "tag": "exile_omicron_asset_mining",  "mods": {"exiles_wc_strength": 0.75}},
      {"key": "fuel",    "tag": "exile_omicron_asset_fuel",    "mods": {"exiles_wc_cadence": 0.75}}
    ],
    "thresholds": [0.85, 0.65, 0.45],
    "debugMessages": true
  }
}
```

Caps (`maxActive`) are **PER NODE** since the spread build (2026-07-06) — the same number
applies to each node. Schema is 4-faction-ready; the planned extension for market-owning
factions (Ashen/Midan/Refugee rows) is an `assets[].industryIds` variant beside the tag scan.

## 8. Effect wiring (what the Director does with each value, per node)

- **Pace:** intervals advance in raw days; on elapse the interval is **re-armed with
  `baseInterval / cadence`** (jittered ±25% as today). No per-frame stat reads.
- **FP:** `fp = roll(fpMin..fpMax) × strength × escalation`.
- **Reach:** in `pickTarget`, rank candidates by distance from the node's system; keep the
  nearest `ceil(reach × count)` (reach ≥ 1.0 → all). No absolute distances anywhere.
- **Escalation (spread build):** growth progress 0..1 accrues over `escalation.rampDays` on
  the scan tick, ONLY while the Lattice UC-Nexus lives — killing it IS the freeze (no more
  per-node frozen-ramp bookkeeping; that table remains only for legacy configs without a UC
  node, which fall back to the campaign-age ramp). Escalation mult = 1 + progress × cap,
  faction-wide; the per-node `exiles_wc_escalation` quest stat multiplies on top. Growth
  stages fire from the same progress value (`growth.stages`): station builds are LOG-ONLY
  (op-source secrecy — no campaign message about a system the player may not have found);
  the UC node's raids activate at the `raids:true` stage with FP × `growth.raidFpMult`.
  *(Superseded: since the 2026-09-08 readiness pool raid size is rolled network-wide;
  `raidFpMult` was removed 2026-10-06, bug sweep OMI-7.)*
- **Rebuild (#8):** the destroyed-scan arms a rebuild deadline (Nexus alive at detection);
  the rebuild pass respawns the station from its spec `rebuildDays` later if the Nexus still
  lives (campaign message — relentlessness legibility), or cancels the pending rebuild the
  tick the Nexus dies. Respawned/stage-spawned stations get their defense manager immediately
  (not just at next game load).
- **Nexus gate:** per node (§2); all-nodes-dead → Director idle (fleet cleanup still runs).

## 9. Regeneration rules

- **Asset modifiers** persist exactly while the facility is dead — the scan re-checks every
  interval, so a revived facility (quest verb / cleared override flag) restores the axis
  automatically. This is the "cap defined by surviving assets."
- **Timed modifiers** carry an expiry timestamp; the Director prunes expired entries on its
  interval tick. Pruning IS the regen — no lerp bookkeeping in v1 (gradual decay noted as
  possible later polish, deliberately skipped).

## 10. Legibility: threshold notifications

Aggregate legibility index = mean of the **asset-fed** axes (`strength`, `cadence`, `reach`)
across all live nodes (escalation excluded — it grows past 1.0 and would mask damage). On each
interval tick, compare against config `thresholds`; fire once per band crossing (last band
persisted in the state object):

- Crossing down: "Omicron raiding activity appears diminished." (band-specific text)
- Crossing up (expiry/revival): "Omicron activity is intensifying."
- **Per-asset kill messages** fire immediately on a newly-detected kill, axis-specific ("With
  its fuel refinery destroyed, Omicron raids from this region will rarely range far afield.").
- **Decapitation messages** (v2.1, from the 2026-07-02 playtest): the Nexus is the hard gate
  rather than an asset row, so it gets its own alive→dead watch (reserved known-state key
  `nexus`) — per-node "Machine activity from the system has ceased." plus a faction-wide
  "fallen silent across the sector" when the LAST node falls. The hardest-earned kill must
  never be the silent one.
- **Scan cadence** (v2.1): asset + Nexus scans run on the ~0.25-day sub-interval (with the
  incursion-landing check), not the raid interval — kill feedback lands within seconds of the
  battle instead of minutes. The scan is a few tag lookups per node system; launch work stays
  on the raid/incursion timers.

Capacity/asset messages remain `CampaignUIAPI.addMessage`. Operation visibility is now real
intel (see §13 — the RaidIntel migration was **BUILT 2026-07-02**).

## 13. Operation architecture (RaidIntel migration — BUILT 2026-07-02)

Operations are vanilla staged, route-managed `RaidIntel` structures. Ratified decisions:
**both archetypes get intel entries**; **the raided faction pays per-kill bounties while an op
is active** (`operations.bountyPerFrigate`, SystemBountyIntel pattern, rep via the vanilla
`SYSTEM_BOUNTY_REWARD` action); **fighting raiders also costs Omicron rep** via the stock
engagement machinery (gradual reclassification — author's call, matches every vanilla faction
incl. Remnants); **derelict mitigation = comp skew only for now** (small raid FP pools keep
NPC battlefields mostly sub-capital; heavies are incursion-only).

- **`OmicronRaidIntel`** (extends `RaidIntel`, implements `FleetEventListener`): archetype +
  nodeId + target faction; machine fleets (all-combat, quality 1.0, `RAIDER` memory flag so
  the assignment AI executes ground raids); pays bounty/rep per player battle, per fleet.
- **`OmicronAssembleStage`**: the marketless integration. Each node owns a **bare source
  `MarketAPI`** — never added to the economy — whose primary entity is the Nexus (the
  ARCHITECTURE §2a escape hatch, persisted in the Director's `nodeSourceMarkets`). Sole
  override drops the vanilla `isInEconomy()` check; the **Nexus-alive check remains** =
  kill-the-source cutoff (destroy the Nexus → in-flight ops stop reinforcing, then fail).
- **`OmicronRaidActionStage`** (extends `PirateRaidActionStage`): **target selection plus
  in-system collateral** (configured target factions decide which SYSTEM is raided; other
  hostile colonies in that system can be caught as collateral — see the box below) and the raid
  effect is Omicron's **deciv-safe colony disruption** applied in `performRaid` — which the
  vanilla AI only calls for fleets that **survive to arrive**. This IS the survival gate; the old
  warning-timer disruption (and its pending-incursion queue) is retired. Defender scrambles
  (`MilitaryResponseScript`) and outmatched/autoresolve logic are inherited.

  > **COLLATERAL — RATIFIED 2026-07-29 (doc corrected to match shipped code; code unchanged).**
  > Target FACTIONS decide which system is raided. Once an op is in that system, each *other*
  > hostile colony there gets a one-time roll to be bombarded:
  > `operations.collateralChance` = **0.5** (`OmicronDirectorConfig.java:170`,
  > `director_config.json` → `operations.collateralChance`; see
  > `OmicronRaidActionStage.isCollateralTarget`). `operations.collateralExempt` (refugees) is
  > never collateral and player colonies are exempt in code.
  >
  > **Author rationale (accepted canon):** neither the Luddic Church nor the Luddic Path shares
  > a star system with the Hegemony, so the §3 hidden canon holding the Hegemony conflict
  > "still-contingent" is not actually undercut by a 0.5 roll — the exposure is low. What DOES
  > share those systems is the Independents plus a single Persean League market, and Omicron
  > treating those as valid targets is both lore-plausible and more interesting in play.
  > **Do not "fix" 0.5 downward as a doc/code divergence — it is the ruled value.**

- **Config schema change:** `raid`/`incursion` blocks now carry `spawnFpMin/Max` (the op's
  total FP budget; the assemble stage splits it into fleets) and `maxActive` counts
  **operations**, not fleets. New `operations` block: `abortFraction`, `bountyPerFrigate`.
  `warningDelayDays` and the per-fleet count/FP fields are gone.
- Fleets are route-managed: cost ~nothing while the player is far away, and despawn handling
  is vanilla's — the orphan-fleet failsafe machinery was removed along with the rest of the
  hand-rolled fleet tracking.

**Post-playtest fixes (2026-07-03):**
- **Zero-fleet bug:** a bare market's `COMBAT_FLEET_SIZE_MULT` computes to base 0 →
  `FleetFactoryV3` built empty fleets → `spawnFleet` returned null → **RouteManager expires
  the route** → ops silently failed with no fleets ever spawning. Fixed both ways: the source
  market pins the stat to 1 (mirroring FleetFactoryV3's own fake-market setup, re-applied
  every launch so old saves heal) AND `params.ignoreMarketFleetSizeMult` is set in
  `createFleet`. Null creation now logs a WARN.
- **Source secrecy:** `OmicronAssembleStage.isSourceKnown()` returns false — no intel-map
  arrow back to the (secret) home system, assemble text reads "an unknown location". Ops are
  additionally tagged `INTEL_BOUNTY` so they surface on the Bounties board (the raided faction
  literally pays one); the map icon marks only the TARGET system.
- **Archetype switches** for the quest campaign-gate (see key registry §6).

## 11. Phase C implementation order (this codebase)

0. **Hygiene fixes from the Phase A review:** persist `pendingIncursions`; redirect/despawn
   in-flight raiders when their node's Nexus dies (no orphans); move `findNexus`/scan work
   behind the interval gates (re-arm-scaled intervals, §8).
1. Config loader + move all constants to `director_config.json`.
2. `OmicronWarCapacity` state object + static quest API; asset tags in `ExileOmicron0.java`.
3. Per-node asset scan → apply/remove `exiles_wc_asset_*` modifiers.
4. Wire §8 effects; delete the deprecated integrity read.
5. Expiry pruning + threshold/asset-kill messages.
6. ~~RaidIntel/route-managed migration~~ ✅ **BUILT 2026-07-02** (§13); ~~array drone
   guards~~ ✅ **BUILT 2026-07-06** (array slice v2, tested); ~~UC-system staged maturation +
   per-node caps + 120d rebuild + far-west cluster~~ ✅ **SPREAD BUILD SHIPPED 2026-07-06**
   (Janino-verified; pending fresh-start playtest — see checklist additions in §12).

Verify each step with the offline Janino compile-check (ScriptDemo harness — see
`janino-offline-compile-check` memory), then a fresh-start smoke test.

## 12. Playtest checklist (no headless loop exists)

- Fresh start; console-kill the **Mining Station** → next raids' fleets visibly smaller
  (log shows the ×0.75); asset-kill message fires.
- Kill the **Fuel Station** → cadence visibly slows (v2.2: tempo only — targets unchanged);
  band message when the index crosses a threshold.
- Set `$exiles_omicron_asset_omicron_prime_mining_destroyed` = true via console → same effect as
  the kill, without a battle; clear it → modifier removed on the next tick.
- Kill the **Nexus** → all launches from the node stop (existing behavior, must not regress) and
  in-flight raiders go home / despawn rather than loitering.
- Save/load mid-warning-window → the queued incursion warning + colony disruption still resolve.
- Confirm the arrays do nothing capacity-related (they are inert objectives until the guard
  slice).

**Spread-build additions (fresh start required):**
- Map: all 4 cluster systems render far west (beyond Tyle); no vanilla core-box weirdness;
  gates may roll in them (accepted ruling — verify patrols don't trap on gate wreckage).
- Ops now log per-node (`node omicron_cipher` etc.); raids launch from all 3 active nodes;
  up to maxActive ops PER node.
- Lattice: lone UC-Nexus (Battlestation-tier guard, NOT the full Nexus gauntlet — tier order
  fix), no raids at start. Console `$exiles_omicron_war_capacity` growthProgress accrues;
  at 0.25 a Mining Station appears at Seed (log line, no campaign message), 0.55 Fuel at
  Colossus, 0.85 light raids (FP × 0.5) begin.
- Kill the Lattice UC-Nexus → growth/escalation frozen; asset + decap messages; no Lattice
  launches; the other 3 nodes unaffected.
- 120d rebuild: console-kill a Mining/Fuel station (dev-speed 120 days) → "Omicron has
  rebuilt its ... refinery" message, station back at its planet with a fresh guard, axis
  modifier removed (REVIVED log). Kill the station AND its Nexus → no rebuild, ever
  (CANCELLED log on the next scan tick).
- Arrays in Cipher/Vigil get drone screens (same exArr_ rules); Prime buoys unchanged.
