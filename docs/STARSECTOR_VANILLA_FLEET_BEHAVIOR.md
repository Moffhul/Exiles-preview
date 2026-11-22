# Vanilla Fleet Behavior Reference — assignments, flags, scripts

> **Status (2026-09-24; amended 2026-10-08 -- §3 NPC-hassle trap, smuggler flag switch):** reference, Starsector 0.98a-RC8. Written for AI sessions that need to make
> campaign fleets "do things" with vanilla machinery. **Nothing here is Exiles canon.** §7 lists
> assistant-originated ideas that are unratified (PROPOSED only).
>
> **Provenance, so you know how much to trust each part:**
> - **§1–§2 (engine):** read directly from FernFlower decompiles of `starfarer_obf.jar` classes
>   `com/fs/starfarer/campaign/ai/{AssignmentModule,StrategicModule,TacticalModule}`. Mangled
>   identifiers, but control flow and string literals are exact. HIGH confidence.
> - **§3–§5 (vanilla scripts, flags, abilities):** catalogued by two research subagents reading
>   `starfarer.api/.../impl/campaign/**` source; spot-checked, not every line re-verified.
>   MEDIUM-HIGH confidence. Re-read the cited file before depending on a detail.
> - To re-derive engine behaviour, use the method in the `obfuscated-code-verification` memory:
>   extract the class and run IntelliJ's bundled FernFlower with its own JBR
>   (`C:/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/jbr/bin/java.exe`; there is no `java` on PATH).
>
> Paths: `API/` = `D:/Fractal Softworks/Starsector/starsector-core/starfarer.api/com/fs/starfarer/api/`,
> `IMPL/` = `API/impl/campaign/`.

---

## 0. Mental model (read first)

Three layers, bottom to top:

1. **Assignments** (`FleetAssignment` enum): the order queue. The head entry is the active order.
   Movement, targeting and "may I engage X?" all switch on it.
2. **Memory flags** on `fleet.getMemoryWithoutUpdate()`: modify *how* the order is executed (sidetracking,
   pursuit limits, hostility, battle-joining, jumping).
3. **Scripts** (`EveryFrameScript`s, route AIs, fleet-group intel, response scripts): everything vanilla
   calls "patrol", "trade", "raid", "blockade" is a script re-issuing layers 1 and 2.

The engine AI is `ModularFleetAI`: Assignment module (queue) → Strategic module (translate order into a
travel destination / priority target, decide `isAllowedToEngage`) → Tactical module (pick targets,
flee, pursue, join battles) → Navigation module (avoidance). Ability AIs run beside it.

**Key API:** `fleet.addAssignment(type, target, maxDays[, actionText][, onCompletion])`,
`fleet.addAssignmentAtStart(...)`, `fleet.clearAssignments()`, `fleet.getCurrentAssignment()`,
`fleet.getAI().getAssignmentsCopy()`, `fleet.getAI().removeAssignment(data)`,
`fleet.getAI().doNotAttack(entity, days)`. The 7-arg overload
`addAssignment(type, target, days, text, addTimeToNext, onStart, onCompletion)` exposes `onStart`.

---

## 1. Queue mechanics (engine, verified)

- **Expiry:** an assignment ends when `elapsedDays >= maxDurationInDays`, OR (movement family only) on
  arrival, OR (orbits only) when the target entity dies. On removal, `onCompletion` runs; orbit
  assignments also clear the fleet's orbit.
- **Arrival radius:** distance to target edge minus target radius (minus 20 more for `station`-tagged
  targets) `< 50` units, or `< 500` during fast-advance. A `GO_TO_LOCATION` whose target is a system's
  center counts as arrived as soon as the fleet is in that system.
- **Empty queue:** the engine substitutes an implicit `PATROL_SYSTEM` with a **null** target → the fleet
  gets no travel destination and just idles/reacts. It does NOT go home. Refill the queue or use
  `Misc.giveStandardReturnToSourceAssignments`.
- **`addAssignmentAtStart`** also calls `setInteractionTarget(null)` and `setOrbit(null)`; the fleet
  breaks its current orbit and interaction.
- **`onStart`** runs on the first tick the entry is at the head with `elapsedDays <= 0`.
- **`addTimeToNext`**: leftover time from an expired entry is added to the next entry's duration.
- **Frozen queues:** `AssignmentModule.freezeAssignments()` (engine-side) makes every add/remove/clear a
  silent no-op. Vanilla scripts check `areAssignmentsFrozen()` before touching a fleet; do the same.
- **Inter-system travel is automatic** for any targeted order: the strategic module builds a jump plan to
  the "safest" jump point. The score counts hostile markets within 4000 units and hostile fleets within
  750 units that have at least 67% of our fleet points (FP); stars, `$unstable` points and wormholes
  are skipped. If a jump point in the right direction is within 2000 units (and we are not in
  hyperspace), it is taken immediately. Near a star corona the AI sets `$ai_useEBForTravel` to
  emergency-burn through. `$cfai_noJump` blocks all jumping.
- **Action text:** the 4th arg overrides the status line. A `FleetActionTextProvider` on the AI can map
  stage codes to text (vanilla patrols store "a"–"e" there and translate them).

---

## 2. The 19 assignments (engine, verified)

"Engage rule" is the assignment-specific branch of `StrategicModule.isAllowedToEngage(target)`. It runs
only after the global gates in §2.4 pass. **Whatever the assignment, the fleet may always engage its own
assignment target.**

### 2.1 Movement family — ends on arrival

| Assignment | Movement | Engage rule |
|---|---|---|
| `GO_TO_LOCATION` | travel to target | allowed, **unless** `$isTradeFleet`, or `$doNotGetSidetracked`, or (`$isScavenger` or `$fleetType` scavenger*) in hyperspace and not pirates |
| `RESUPPLY` | travel to target | same as `GO_TO_LOCATION`. **On arrival:** repairs all hulls fully and raises CR to max CR. Also tops up supplies and fuel, but **only if `!fleet.isAIMode()`**. NPC fleets normally ARE in AI mode, so for them it means repair plus CR only. In the non-AI-mode branch, a fleet without cargo room for a third of a month's supplies is despawned instead. |
| `GO_TO_LOCATION_AND_DESPAWN` | travel to target | same as `GO_TO_LOCATION`. **On arrival:** `despawn(REACHED_DESTINATION, target)`. |
| `DELIVER_RESOURCES`, `_SUPPLIES`, `_FUEL`, `_PERSONNEL`, `_CREW`, `_MARINES` | travel to target | **engages only a target it is physically touching** (centre distance < sum of radii). All six are mechanically identical; only the default status text differs. This is the least combative way to travel. |

### 2.2 Area family — runs full duration

| Assignment | Movement | Engage rule |
|---|---|---|
| `PATROL_SYSTEM` | **Target is the star or system center:** every ~30 days it picks a goal by weight: own markets (weight = size); own + non-hostile market pairs, flown as a straight or weaving route (weight = average size); jump points (2); comm relays (weighted by burn); non-hostile planets (0.25). It circles a single goal or runs the route. **Other in-system target:** circles it at target radius + 200–700. **Hyperspace target:** circles at +500–1250. | allowed (`$doNotGetSidetracked` only stops it chasing a *fleeing* fleet). **Defended zone:** unless the target is the system center, any hostile within `600 + speed*5 + targetRadius` of the target is always pursued, with no give-up. |
| `DEFEND_LOCATION` | circles target at radius + 200–700 | same as PATROL, including the defended zone |
| `ATTACK_LOCATION` | **identical circling to DEFEND** | allowed, **without** the defended zone. **It does not attack the target entity.** Its only differences from DEFEND are the text and the missing defended zone. |
| `RAID_SYSTEM` | **Lane lurking.** In-system, it picks one goal (held for 30 days): a jump point with no hostile market within 4000 units (weight 5); an own market (size×2); the midpoint of two markets at least 5000 apart where one is hostile to us and they aren't hostile to each other (average size); a hostile-market→jump-point lane (size×0.25). It circles the goal. In hyperspace it circles jump points within 1500 of the target. | allowed (same as PATROL). **It does not raid markets.** Market raids come from `BaseAssignmentAI.checkRaid` or `FGRaidAction` (§3). |
| `ORBIT_PASSIVE` | settles into a real `CircularFleetOrbit` at fleet radius + target radius + 100–200 | refuses (and always if `$doNotGetSidetracked`), **except** an `$isPatrol` fleet vs a player with transponder off / `$sawPlayerTransponderOff` / `$pursuePlayer` |
| `ORBIT_AGGRESSIVE` | same orbit | allowed (as PATROL) |

Circle radius for PATROL/DEFEND/ATTACK/RAID can be overridden with `$cfai_patrolDist` (float). Circle
direction and radius are cached in `$ai_*CircleDir` / `$ai_*Dist` keys for a few days.

### 2.3 Target family — runs full duration

| Assignment | Movement | Engage rule / special |
|---|---|---|
| `INTERCEPT` | tactical priority target = assignment target (normal mode) | **only** the target; the target is always pursued (give-up logic skipped). Doesn't steer around nearby fleets. The engine inserts `INTERCEPT` (10 days) itself when a fleet reacts to a hostile action it saw and would ENGAGE or HOLD against that party. It sets `$core_fleetBusy` with a reason, and `isAlreadyIntercepting()` prevents stacking. Interacting with the fleet removes a front INTERCEPT. |
| `FOLLOW` | priority target in **follow mode**: tails it, and **never sets an interaction target**, so it won't initiate contact | refuses (same exception as ORBIT_PASSIVE). Doesn't steer around nearby fleets. Good for shadowing and escorting. |
| `HOLD` | with a target: travels to and sits on it. With **no** target: holds its current position. | **always refuses.** Doesn't steer around nearby fleets. With a null target the interaction target is also cleared. |
| `STANDING_DOWN` | travels to its target if it has one | **always refuses.** `isStandingDown()` = true. **Battle-join rule (`TacticalModule.wantsToJoin`):** let S = "this battle side already has a standing-down fleet". A fleet joins only if S equals its own standing-down state, or the side's primary is a station. So stood-down fleets only join sides with other stood-down fleets, and normal fleets won't join a side containing one. |

### 2.4 Global engage gates (checked before the table above)

- `doNotAttack` list (from `getAI().doNotAttack(entity, days)`) → refuse. **This only affects target
  SELECTION.** The fleet can still be pulled into a battle already happening next to it (Ashen blockade lesson, 2026-08-06).
- `$cfai_ignoredByOtherFleets` on the target, `$cfai_ignoredByFaction` = our faction id on the target,
  or `$cfai_ignoreOtherFleets` on us (ignored for the player if we have `$cfai_doNotIgnorePlayer`) → refuse.
  **None of these apply to our own assignment target.**
- **Pursuit give-up** (skipped if within contact range, EB active, in a defended zone, `$cfai_longPursuit`,
  target already in a battle, or target = INTERCEPT target):
  - Give up when all of these hold:
    - time to catch > 3 days
    - target speed ≥ ours
    - distance > contact + 50
    - days pursued > `1.5` (`3.0` if `$isPatrol`) + `0.1 × burn` − `1.5 × (count of recently abandoned targets)`
  - The target then goes on a "recently stopped pursuing" list for 2–4 days.
- **Terrain:** terrain flagged `DANGEROUS_UNLESS_GO_SLOW` makes the fleet slow down ("Moving slowly").
  `$doNotGetSidetracked` lets it push through the milder `NOT_SUPER_DANGEROUS…` kind. Emergency burn
  overrides both.
- **Trade fleets:** `$isTradeFleet` → `wantsToJoin` returns false for every battle, and the engine never
  inserts a reactive INTERCEPT for them.

---

## 3. Composite behaviours vanilla builds (scripts)

| Pattern | Class (under `IMPL/`) | What it does | Reusable trick |
|---|---|---|---|
| Market patrol | `fleets/PatrolAssignmentAIV4` | OP home (prep 3–6d) → GO_TO → PATROL_SYSTEM point → GO_TO home → OP → despawn | Point weight ×0.1 per patrol already there (spreads patrols). Sets `$cfai_longPursuit` via onStart/onCompletion while patrolling the system center. Stage codes → `FleetActionTextProvider`. |
| Route fleets | `fleets/RouteManager`, `procgen/themes/RouteFleetAssignmentAI` | Abstract route segments. A real fleet spawns within ~1.6 LY of the player and despawns at 3–4 LY. Segments map to OP (start), GO_TO (travel), OA (local entity), PATROL (local system), and GO_TO+OP+despawn (end). | Subclass and override `get*ActionText` / `add*Assignment`. `RouteManager.isPlayerInSpawnRange(e)` works as a "does the player care" gate. |
| Trade / smuggle | `fleets/EconomyFleetAssignmentAI` | Route fleet with real cargo and mothballed hull deliveries | Smugglers are spawned **Independent** and `setFaction(id, true)` to the source/destination market's faction while inside its system when that faction is hostile to both their own and the Independents (`doSmugglingFactionChangeCheck`) -- e.g. a smuggler at a pirate market flies as **pirates** there, Independent in hyperspace. Pirates get `$forceTOff`. |
| Data-driven civil traffic | `fleets/misc/MiscFleetAssignmentAI` + `MiscFleetCreatorPlugin` | Route fleets whose creation and text come from a plugin | Vanilla's extension point for new traffic types. |
| Military response | `MilitaryResponseScript` | Sends the nearest eligible fleets (`$isPatrol`/`$isWarFleet`/`$isPirate`, not busy) to a point until a set share of faction FP is committed. The share is budgeted across responses via faction memory `$mrs_<reason>`. Orders: GO_TO (if >2000 away) + PATROL_SYSTEM at front, tagged `"response"`. | **Most "fleets converge and fight at X" in vanilla is this.** On unrespond it also deletes the next OP so the fleet doesn't go back to "preparing". |
| Opportunistic capture / build / raid | `procgen/themes/BaseAssignmentAI` (`checkCapture`, `checkBuild`, `checkRaid`); `WarfleetAssignmentAI` = add-on that runs only these | Capture an objective or raid a market within 2000; build on a stable location | "Hold token" pattern: token orbiting the target, HOLD 0.5d, onCompletion does the action. The raid needs FP unchanged since the order. Gate: `canTakeAction()`. |
| Fleet group ops | `intel/group/FleetGroupIntel`, `GenericRaidFGI`, `BlockadeFGI` | Action chain Wait → Travel → payload (`FGRaidAction` / `FGBlockadeAction` / `FGBlockadePlanetAction`) → Travel home. Group moves as one (centre-of-mass leash); stragglers dropped after 30d; aborts at 33% FP. | **Never build blockades from scratch** (CLAUDE.md §6.4). A blockade = `MilitaryResponseScript` per jump point plus a `BLOCKADED` condition while strength holds. |
| Raid stages (older) | `intel/raid/RaidIntel` + `OrganizeStage`/`AssembleStage`/`TravelStage`/`PirateRaidActionStage`/`ReturnStage`, `RaidAssignmentAI` | Staged raid on routes; action stage = response scripts per market | **`PirateRaidActionStage.updateRoutes()` owns `maxDays`.** See the CLAUDE.md raid-stage gotcha. |
| Player hassle | `HasslePlayerScript`, `SmugglingScanScript` | Pursuit with **no assignment**: `$pursuePlayer` + `$keepPursuingPlayer` (short TTL, refreshed while the player is visible), leash by distance | Flag-driven pursuit. |
| NPC hassle | `HassleNPCScript` (started by `NPCHassler`) | Two fleets meet at a midpoint (`setMoveDestinationOverride`) and HOLD 2–3d; the target takes CR damage | Short-TTL `$core_fleetBusy` + `$doNotGetSidetracked` + `$core_fleetSpecialAction`, refreshed each tick, so a crash leaves nothing behind. **TRAP (found 2026-10-07):** whenever its own HOLD is not the current assignment the script `clearAssignments()` (the target's queue is cleared every tick), and on finish it removes only its own HOLD, so the hassler is left with an EMPTY queue (it idles forever) and its unset `$doNotGetSidetracked`. `NPCHassler.isDone()` is always false, so a picket keeps starting inspections while it is still in the restricted system -- including on its way home. Strip it at withdrawal (`removeScriptsOfClass(NPCHassler.class)`, as `KnightsOfLuddTakeoverExpedition` does) and re-issue the return order if the queue empties (`ExileReturnOrderWatchdog`). Vanilla `PerseanLeagueBlockade` carries the same gap. |
| Scavenger → pirate | `procgen/themes/ScavengerPiracyScript` | Transponder off, one visible weak target within 800 → `setFaction(PIRATES)` + `setNoFactionInName`, attack, revert when alone | Situational faction switching. |
| Lurk in hyperspace | `fleets/DisposableAggroAssignmentAI` | `RAID_SYSTEM` on `system.getHyperspaceAnchor()`, or PATROL on points weighted away from enemy patrols | `Misc.pickLocationNotNearPlayer` for out-of-sight spawns. |
| Ambush | `events/nearby/DistressCallPirateAmbushTrapAssignmentAI` | OA on a token 3000–3500 beyond a jump point | Hide just off a point. |
| Leashed guard | `world/ZigLeashAssignmentAI` | OA on post. If it strays >1500, OP at front + `doNotAttack(currentTarget, 1d)` | Soft leash that breaks a chase. |
| Pinned / escort | `LeashScript` | `setMoveDestination` every frame to anchor+offset | Formations without assignments. |
| Pod looting | `CargoPodsResponse` | Nearest pirate within 500 HOLDs on a moving token, then takes the cargo | A token that tracks a drifting target. |
| Named NPC fleet | `fleets/PersonalFleetScript` | Respawn after death. Retreat home below 33% FP after a battle. | |
| Keep N fleets near a source | `fleets/SourceBasedFleetManager`, `SeededFleetManager` | Population scaled by player distance; kills lower the cap | Remnant pattern. |
| Mission fleets | `missions/hub/HubMissionWithTriggers` | `triggerOrderFleetPatrol*`, `triggerOrderFleetInterceptPlayer`, `triggerFleetInterceptPlayerWithinRange/Nearby/OnSight`, `triggerFleetStopPursuingPlayerUnlessInStage`, `triggerOrderFleetEBurn`, `triggerFleetMakeFaster`, flag setters | Underneath: `TriggerFleetAssignmentAI`, `MissionFleetAutoDespawn` (despawns after 30d unseen and >3 LY away), `TransmitterTrapSpecial.makeFleetInterceptPlayer`. |
| Dormant | Remnant seeded fleets | `fleet.setAI(null)` + transponder off | True sleep with no AI cost. |

**Standard exit:** `Misc.giveStandardReturnToSourceAssignments(fleet[, clear])` sets
`$core_fleetReturningToDespawn` and chains GO_TO source → OP 1–2d → GTL&D. Other scripts check
`Misc.isFleetReturningToDespawn` and leave such fleets alone. `Misc.giveStandardReturnAssignments(fleet,
where, text, clear)` does the same to a custom target.

**Temporary-order pattern:** `addAssignmentAtStart(...)`, then
`getCurrentAssignment().setCustom(TAG)`, then `Misc.setFlagWithReason(mem, MemFlags.FLEET_BUSY, reason, true, ttl)`.
To clean up, walk `getAssignmentsCopy()` and remove entries by TAG. The base queue then resumes.

---

## 4. Memory flags that change behaviour

> ⚠ **Most `$cfai_*` encounter flags are PLAYER-SCOPED.** In `TacticalModule.pickEncounterOption`, the flags
> `$cfai_makeAggressive`, `$cfai_holdVsStronger` and `$cfai_makePreventDisengage` are read only when the
> other side is the player. `$cfai_makeAlwaysPursue` works the same way in `pickPursuitOption`.
> `$cfai_makeHostile` without a faction suffix is hostility toward the player. For NPC-vs-NPC control
> you have: the assignment, faction relations, `$doNotGetSidetracked`, `$cfai_longPursuit`, the ignore
> flags, `doNotAttack`, `$isTradeFleet`, and `$cfai_makeHostile_<factionId>` / `$cfai_makeNonHostile_<factionId>`.
> **Exception, verified 2026-10-06 (FernFlower decompile of `TacticalModule`):** `$cfai_makeAllowDisengage`
> is read OUTSIDE the player branch — in `pickEncounterOption` a fleet that out-strengths its opponent
> by 1.25× still declines ENGAGE if it carries the flag, and `pickPursuitOption` returns LET_THEM_GO
> for it against anyone. So it is a real NPC-vs-NPC lever: "this fleet does not force fights or chase".
> Verify any other flag before relying on it.

| Flag (`MemFlags` constant where one exists) | Effect | Scope |
|---|---|---|
| `$doNotGetSidetracked` (`MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED`) | travel orders won't engage; patrol/raid won't chase fleeing fleets; ORBIT_PASSIVE/FOLLOW never engage; pushes through mild slow-terrain | general |
| `$isTradeFleet` (`MEMORY_KEY_TRADE_FLEET`) | travel orders won't engage; **never joins battles**; no reactive intercept | general |
| `$isPatrol` (`MEMORY_KEY_PATROL_FLEET`) | pursuit patience 3d instead of 1.5d; eligible for military response; pursues a dark player from passive orbit | general |
| `$isWarFleet`, `$isPirate`, `$isRaider`, `$isSmuggler`, `$isScavenger`, `$fleetType` | response eligibility, `checkRaid`, ability AI choices, scavenger hyperspace rule | general |
| `$cfai_longPursuit` (`MEMORY_KEY_ALLOW_LONG_PURSUIT`) | disables pursuit give-up | general |
| `$cfai_noJump` (`MEMORY_KEY_NO_JUMP`) | no jump plans | general |
| `$cfai_patrolDist` (`FLEET_PATROL_DISTANCE`) | circling radius override | general |
| `$cfai_ignoreOtherFleets` / `$cfai_ignoredByOtherFleets` / `$cfai_ignoredByFaction` / `$cfai_doNotIgnorePlayer` | engagement opt-outs (§2.4) | general |
| `$doNotTryToAvoidNearbyFleets` (`DO_NOT_TRY_TO_AVOID_NEARBY_FLEETS`) | no avoidance steering (HOLD/STANDING_DOWN/INTERCEPT/FOLLOW already skip it) | general |
| `$core_fleetBusy` (`FLEET_BUSY`, use `Misc.setFlagWithReason`) | exempt from `MilitaryResponseScript`, WarSim, reactive intercepts | general |
| `$core_fleetSpecialAction`, `$core_fleetNoMilitaryResponse`, `$core_fightToTheLast` | see javadoc in `API/impl/campaign/ids/MemFlags.java` | general |
| `$cfai_makeHostile_<factionId>` / `$cfai_makeNonHostile_<factionId>` | per-faction hostility override | faction-targeted |
| `$cfai_makeHostile`, `…WhileTOff`, `…ToPlayerTradeFleets`, `$cfai_makeNonHostile` | hostility to the player (and to trade fleets) | **player** (`…ToAllTradeFleets` is general) |
| `$cfai_makeAggressive`, `$cfai_holdVsStronger`, `$cfai_makePreventDisengage`, `$cfai_makeAlwaysPursue` | encounter choices | **player** |
| `$cfai_makeAllowDisengage` | never ENGAGE merely because stronger (`pickEncounterOption`); LET_THEM_GO in `pickPursuitOption` | **general (NOT player-gated)** — decompiled 2026-10-06 |
| `$pursuePlayer`, `$keepPursuingPlayer`, `$willHasslePlayer` + `$hassleType` | flag-driven pursuit and hassle | **player** |
| `$lowRepImpact`, `$noRepImpact`, `$alwaysSpreadTOffHostility`, `$everyoneJoinsBattleAgainst` | reputation and battle-join side effects | player-facing |
| `$forceTOff` | transponder AI keeps it off | general |
| `$ai_useEBForTravel` | EB AI uses emergency burn for travel | general |
| `$ai_placeToLook` (`FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET`) | where an intercepting fleet searches for a lost target | general |

Read-only AI state you can inspect (set by the engine): `$ai_pursuitTarget`, `$ai_fleeingFrom`,
`$ai_nearestEnemy`, `$ai_travelDest`, `$ai_moveDest` (`API/campaign/ai/FleetAIFlags.java`).

---

## 5. Abilities and direct control

- **AI-driven abilities** (`data/campaign/abilities.csv` `ai` column, classes in
  `IMPL/abilities/ai/`): `transponder`, `go_dark`, `sensor_burst`, `emergency_burn`, `sustained_burn`,
  `interdiction_pulse`. Anything else (e.g. `distress_call`, `scavenge`) is never used by the AI.
- **Force one:** `AbilityPlugin a = fleet.getAbility(Abilities.EMERGENCY_BURN); if (a != null && a.isUsable()) a.activate();`
  `deactivate()` turns toggles off. The AI may flip a toggle back unless you set the flag its AI reads
  (e.g. `$forceTOff`) or `fleet.removeAbility(id)`. Remnants remove EB, sensor burst and go dark.
- **Direct steering (bypasses assignments):** `fleet.setMoveDestination(x, y)` (the AI also calls this,
  so call it every frame) and `setMoveDestinationOverride(x, y)` (overrides AI and player input).
- **Identity:** `fleet.setFaction(id, true)`, `setNoFactionInName(true)`, `setTransponderOn(bool)`.
- **Sleep:** `fleet.setAI(null)`.

---

## 6. Decision guide — "I want a fleet to…"

| Goal | Use |
|---|---|
| go somewhere and not get distracted | `GO_TO_LOCATION` + `$doNotGetSidetracked` (or `$isTradeFleet` for civilians) |
| travel meekly, fight only if cornered | any `DELIVER_*` |
| come home and refit | `RESUPPLY` (NPCs get repair + CR only) |
| guard a point and really hold it | `DEFEND_LOCATION` (defended-zone rule), or `ORBIT_AGGRESSIVE` + soft leash |
| loiter menacingly near a target without committing | `ATTACK_LOCATION` (despite the name) |
| actually attack a market | `BaseAssignmentAI.checkRaid` / `WarfleetAssignmentAI`, `FGRaidAction`, or `MilitaryResponseScript` + your own payload |
| prey on traffic lanes | `RAID_SYSTEM` (in-system or on the hyperspace anchor) |
| blockade | `BlockadeFGI` / `FGBlockadeAction` (never hand-rolled) |
| hunt one specific fleet | `INTERCEPT` (plus `$cfai_longPursuit` if it must never quit) |
| shadow or escort without contact | `FOLLOW` |
| wait somewhere and never fight | `HOLD` (or `STANDING_DOWN`, which has the odd battle-join rule) |
| sit dormant | `ORBIT_PASSIVE` long duration, or `setAI(null)` |
| converge several faction fleets on a point | `MilitaryResponseScript` |
| move as a coordinated group | `FleetGroupIntel` actions |
| leave cleanly | `Misc.giveStandardReturnToSourceAssignments` |

**Traps:** `ATTACK_LOCATION` and `RAID_SYSTEM` never hurt markets. An empty queue idles and does not go home -- and vanilla `HassleNPCScript` CREATES one (see §3, NPC hassle).
`doNotAttack` does not prevent battle participation. `$cfai_*` encounter flags are player-only, EXCEPT `$cfai_makeAllowDisengage` (general; see §4).
PATROL and DEFEND have a no-give-up zone of about 600 units around their target. `addAssignmentAtStart`
breaks orbits. Frozen queues silently ignore adds.

---

## 7. Exiles usage snapshot and PROPOSED ideas

**Usage as of 2026-09-24** (grep of `data/scripts` for `FleetAssignment.`): `ORBIT_PASSIVE` ×11,
`GO_TO_LOCATION_AND_DESPAWN` ×7, `GO_TO_LOCATION` ×5, `PATROL_SYSTEM` ×4, `INTERCEPT` ×4,
`DELIVER_RESOURCES` ×2 (refugee salvage), `ORBIT_AGGRESSIVE` ×1, `HOLD` ×1. Unused: `FOLLOW`,
`RAID_SYSTEM`, `ATTACK_LOCATION`, `DEFEND_LOCATION`, `RESUPPLY`, `STANDING_DOWN`, the other `DELIVER_*`.
Refugee convoys, Ashen relief and Heg-League recovery fleets already set `$isTradeFleet`.

**PROPOSED — assistant-originated, NOT ratified by Sean. Do not implement without a ruling.**

1. Omicron observation drones: `FOLLOW` a Luddic/Hegemony fleet (no contact), then GTL&D. This shows
   "classification" rather than aggression.
2. Black Fleet lane predators: `RAID_SYSTEM` in target systems or on their hyperspace anchors.
3. Black Fleet extortion "inspections" of trade fleets, reusing the `HassleNPCScript` pattern.
4. Midan false flag (Heg-League Pass 7): Midan fleets under Black Fleet colours via `setFaction`,
   modelled on `ScavengerPiracyScript` in reverse. The reveal mechanism would be new work.
5. Ashen "martyr pickets": `HOLD` / `STANDING_DOWN` on Tri-Tachyon jump points. They fight only if attacked.
6. Heg-League brinkmanship: at high Tension, patrols `FOLLOW` their opposite numbers without engaging.
7. Damaged director fleets `RESUPPLY` at home and return, instead of despawning (visible attrition and recovery).
8. Refugee salvagers comb wreck fields with entity-targeted `PATROL_SYSTEM` ("investigating").
9. Refugee convoy status text via `DELIVER_PERSONNEL`. Low value: the convoys' `$isTradeFleet` already gives
   the non-combat behaviour.
