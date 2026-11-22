# Exiles — Refugee Salvage Expeditions

**Status (2026-11-22):** On 0.98.5a each expedition now carries two extra fleet flags: a guard
against a vanilla `SellToFleet` error, and one that hides vanilla's "buy hyperspace charts" option
(Sean's ruling). Record: `EXILES_0985A_COMPAT_REVIEW.md` §8.4. One open design item was added:
a viability review of expeditions selling salvage to the player (see "Open design items").

**Earlier status (2026-09-21):** The launch screen was retuned by Sean's ruling after a log read showed
Kapteyn Starworks' roaming scouts and convoys holding Isirah deferred for months: a hostile market's
own in-system traffic now defers a launch only within `danger.launchThreatRadius` of the wreck,
while raiders, war fleets and out-of-system hostiles still defer from anywhere. Deferral logging now
prints every change of blocking fleet. Janino-clean; **awaits live verification** — watch Isirah.
Earlier status (2026-09-20): author testing confirms faster fleets, successful collection/delivery and
coexistence with Nex Vultures. Large-wreck preference and nearby-player dispatch are implemented.
Hostile-market safety bubbles are Janino/offline-clean and await live verification. Requires a new campaign only when moving from the old decorative version,
not when updating an existing battlefield-cleanup campaign.

## Current contract — approved 2026-09-07

Refugees now consume unattended Core battlefield salvage and return commodities and weapons to
Asharu or Mairaath. This supersedes the decorative version and the August design pause below.
No Nexerelin dependency, custom dialogs, intel, donations, hull recovery, or custom reputation rules.
The separate story-driven Omicron-core acquisition constraint remains in force: no recurring core sales.

### Dispatch and fleet

- Once per campaign day, filter `theme_core` systems by player proximity before scanning their
  temporary battle fields or expiring wrecks. New launches require the player in that system or
  within `playerSpawnRangeLY` (default 1.6 LY) of its hyperspace position.
  Creation faction is irrelevant. There is no raid-intel gate or hard-coded hostile-faction list.
- An eligible nearby system receives a random 2–4-day response timer after first detection.
  Preserve the deadline while the player is away; revalidate salvage on return. Distant systems
  are not observed for salvage changes. If a nearby scan finds no remains, discard its timer.
  Remove timers for deleted/non-Core systems. No eligible recipient means no dispatch.
- Defaults: three active expeditions globally, one per system, at least five days between launches
  into one system. Returning fleets still count until retired. Zero global cap prevents new launches;
  lowering the cap does not kill existing fleets. Randomize selection among ready systems.
- Appear near the target outside player sensor range with a buffer; no outbound enclave journey.
  Defer spawning during the player's hyperspace transition. Fleets continue working offscreen.
- Once a response is ready, inspect only that system's live fleet list. A fleet actually hostile
  to Refugees and at least 75% of the expedition's nominal combat budget is a candidate threat.
  **Ruled 2026-09-21 (Sean, after the Isirah read):** candidates split into two classes. A
  **mobile threat** — a raider, a war fleet, or any hostile fleet not sourced from a market in that
  system — defers dispatch from anywhere in the system. **Local defense** — a hostile fleet sourced
  from a market in the same system, whatever its type (patrol, scout, convoy) — defers dispatch
  only within `danger.launchThreatRadius` (default 2,500) of the chosen wreck. Refugees brave a
  system they know hostile patrols roam; the in-flight visible-threat check makes them run when
  spotted. `danger.localDefenseExemptAtLaunch: false` restores the old system-wide screen.
  Preserve the matured response and retry on the normal daily scan; do not restart the 2–4-day timer.
  Explicitly non-aggressive fleets and station-mode fleets do not count. This prevents replacement
  expeditions launching into an active raid without making a pirate market poison its whole system.
  The deferral log line prints on every change of blocking fleet with its class, strength and
  distance to the wreck; before 2026-09-21 it printed once per episode and hid months of deferral.
- Exactly one `exile_tarsus_2_Standard` Calceus, one `shepherd_Frontier`, one `tarsus_Standard`, and
  one or two generated small escorts. Refugee faction/doctrine, transponder on, no subordinate officers.
  Calceus replaces `crig` in the Refugee known-hull roster and one Shepherd in the old expedition.
- New expedition commanders receive vanilla Bulk Transport at level 1 (approved 2026-09-08).
  This raises the civilian Calceus from base burn 7 to 9 and includes vanilla cargo, fuel,
  and personnel capacity bonuses. Other ships/modifiers can still limit fleet burn.
  Restart and load an existing save to apply this to future spawns; already spawned fleets
  are not retrofitted. Deployment timing, fleet counts, and hull stats are unchanged.
- Calceus retains its author-created sprite and built-in `repair_gantry`. Its hull/file/description
  ID is now `exile_tarsus_2`; the display name remains Calceus. No bespoke gantry multiplier.

### Collection and danger

- Prefer large wrecks with a travel penalty: capital/cruiser/destroyer/frigate/ordinary debris weights
  are 16/8/4/2/1, divided by `1 + distance / 2000` (local campaign units). A field containing ships
  uses its largest remaining hull's weight. Equal scores favor the nearer target. Missing/broken
  variants fall back to weight 1. Scoring only reads existing ship data; it does not roll loot or
  construct temporary fleet members. Mission/expiry exclusions still apply before scoring.
- Do not select salvage within `hostileMarketSafetyRadius` (default 2,500 local units) of a market
  whose faction is hostile to Refugees. Stations are ignored as system-wide threats because their
  immediate operating space is represented by this bubble instead.
- **In flight** (unchanged 2026-09-21), a visible hostile home patrol is ignored only while it
  remains within that radius of its valid same-faction source market and farther than that radius
  from the active operation. Patrol recognition uses both the vanilla patrol marker and patrol
  fleet types because vanilla pirate patrols deliberately omit the marker. Raiders never receive
  the exemption. A patrol that roams outside its market space or closes on the operation becomes
  an ordinary mobile threat and the expedition breaks off. The **launch** screen uses the looser
  local-defense rule above instead: it was the launch screen, not this one, that held Isirah
  deferred for months.
- At spawn, rank from the player's local position, or system center if the player is elsewhere.
  The physical fleet then ranks from its actual position when choosing work. Move there and hold
  for 0.5 day. Retain a valid target until completion; every 0.25–0.5 days abandon invalid targets,
  and recheck before collection. Nearby competitors do not reserve targets or force reassignment.
- Debris must be unscavenged, non-fading `BATTLE` terrain with initial lifetime at most 60 days.
  Wrecks must be `SALVAGEABLE`, `EXPIRES`, vanilla wreck entities with a derelict plugin.
  Skip mission-important objects, mission/important tags, fading/expired objects, and other salvage
  special types. Never clean permanent exploration wrecks or unrelated salvage entities.
- Generate debris drops from local drop-list copies using vanilla `SalvageEntity.generateSalvage`.
  Scrap ships using vanilla condition-based weapon retention and scuttling quantities; never add
  a recovered hull. Retain ordinary commodity stacks and weapons, excluding AI cores, LPCs,
  hullmod/special items and blueprints. A material ID allowlist also excludes custom core commodities.
- Clear recovery/drop/extra-salvage data and mark debris scavenged; expire consumed wrecks.
  This prevents subsequent Refugee collection and leaves vanilla/Nex with no remaining field loot.
  Do not reserve targets against Vultures. Competing completion and expiration are rechecked.
- Carry only what fits cargo, fuel and personnel capacity; surplus from the consumed target is discarded.
  Keep a separate collected-cargo manifest so starting stores are never delivered as salvage.
- Every 0.25–0.5 days, check locally visible fleets using the same mobile-threat/local-defense policy
  and at least 75% of current Refugee strength. Break off if threatened. Also return after losing
  half the initial effective strength, reaching 90% cargo capacity, 15 days, or exhausting local targets.

### Delivery and persistence

- The only recipient entities are `corvus_abandoned_station` and `mairaath_abandoned_station1`.
  Resolve their actual markets; require live Refugee ownership, a functional salvage yard and port.
- Display a return assignment. When out of player view/proximity and not in battle, deliver once and
  despawn using vanilla fleet APIs. Also finish at the eligible home enclave when within fleet radius
  plus enclave radius plus 200 units, even while visible; this prevents an exhausted return assignment
  leaving a watched fleet wandering. No full return trip is required. Revalidate the destination and
  fall back to the other eligible enclave; if neither qualifies, retire without market delivery.
  A destroyed fleet makes no delivery. Only surviving manifested cargo can be delivered.
- Half the collected commodity quantity contributes to a temporary `addTradeModPlus` bonus, capped
  at 500 outstanding units per commodity per enclave. Each contribution lasts 30 days independently;
  later deliveries do not renew old contributions. Unrelated trade modifiers do not count toward this cap.
- Recovered weapons enter the ordinary open market. Perform any due inventory refresh before injection;
  the next ordinary refresh removes them. No valuable-weapon exclusion or extra weapon-volume cap.
- Manager, pending timers, active fleets, assignment state and collected cargo are saved campaign state.
  `ExilesModPlugin` already registers/restores the manager idempotently. No extra registration is needed.
  Old decorative RouteManager save payloads are not migrated; start a new campaign for this refactor.
- No sector scan in every-frame work; dispatch scans daily, fleet-local work is interval-gated, and
  destroyed/retired fleet references are pruned at the next dispatch check. No persistent delivery ledger:
  vanilla temporary modifiers own commodity expiry. Shared Refugee debug toggle announces launches;
  logs also record collection and delivery.

### Configuration

`data/config/exiles/director_config.json` → `exilerefugee.salvageExpeditions` retains its location.
`cycleDays` now controls the scan interval and `salvageDays` the per-target operation time. New knobs:
`responseMinDays`, `responseMaxDays`, `maxPerSystem`, `systemLaunchSpacingDays`, `maxCollectionDays`,
`returnCargoFraction`, `commodityDeliveryFraction`, `commodityBonusCap`, `commodityBonusDays`.
`playerSpawnRangeLY` controls only new launches (0 means current system only, maximum 10 LY).
`danger.hostileMarketSafetyRadius` controls both unsafe target space and containment of local patrols
(default 2,500, clamped to 500–10,000 local units). Existing `danger.strengthMult` controls both the
nominal pre-launch threat floor and active-fleet withdrawal comparison. Added 2026-09-21:
`danger.localDefenseExemptAtLaunch` (default true) and `danger.launchThreatRadius` (default 2,500,
clamped 500–10,000) — the launch-screen local-defense rule described under Dispatch.
Already-active fleets continue working offscreen. Existing pending deadlines survive loading;
configuration is reloaded after restart/load and new target choices use the new preference.
Legacy preparation/travel/home-orbit/distance/raid-weight keys remain annotated but unused.
Safety ceilings: 12 global fleets, 3 per system, 5 ships per fleet, 30 escort FP, destroyer maximum, 60-day collecting
limit, 10,000-unit commodity cap, 120-day bonus duration. Scans cannot run faster than daily.

### Validation and live-test handoff

2026-09-10 live test: Refugees and Vultures both spawned and collected salvage. Author observed
ship preference with sensible debris fallback and considers the feature ready for Beta. Two small
follow-up fixes are implemented: standalone wrecks with an initialized but empty recovery list are
ineligible (never fall back to original hull data), and fleets retire on reaching their valid home even
while watched. Empty ship lists in debris still allow ordinary debris collection. The empty-list case
is a verified code defect, but the log cannot prove it explains every reported phantom-wreck visit.
Focused regression checks cover spent-wreck selection/collection rejection and once-only arrival
retirement. These fixes still need visual confirmation in-game.

Offline checks passed:
- Game Janino loads `ExilesModPlugin` and all salvage classes without Nex on the classpath.
- `tools/refugee-salvage-check.ps1`: production-helper checks with campaign proxies and vanilla
  temporary modifiers cover item/target exclusions, refresh-before-weapon-injection, surviving-cargo
  delivery, protecting starting stores, commodity caps, unrelated modifiers, and independent expiry.
- 2026-09-09 extension: large-wreck versus nearby-small-wreck decisions, long-distance penalty,
  ship-bearing versus emptied debris, malformed-variant fallback, read-only scoring, proximity
  boundaries and transition exclusion. The actual dispatcher is exercised with campaign proxies
  for distant-scan avoidance, preserved deadlines, active-fleet retention, global cap and timer cleanup.
- Installed Nex compatibility checks pass: independent target eligibility, consumed versus fresh
  debris, hostile exclusion, demand-cache behavior and passive diagnostic snapshots.
- Reviewed target selection, assignment invalidation, dispatch/cooldown state, configuration loading,
  cargo handoff and save-field continuity. No new saved fields or Nex hooks were introduced.
  Offline checks do not simulate physical travel, full fleet generation or save serialization.
- 2026-09-20 extension: hostile-market target exclusion, station exclusion, contained versus roaming
  patrol classification, pirate patrol fleet-type fallback, raider override and explicitly
  non-aggressive fleet exclusion. All affected production classes pass the game Janino loader.

Focused live checks for the 2026-09-09 changes:
- Place small and large temporary wrecks at different distances; observe large-wreck preference
  without chasing a capital across the entire system. Include ships embedded in a debris field.
- Race a Vulture/player to the chosen wreck; confirm the loser retargets after consumption.
- Leave the 1.6-LY radius during a response timer, return, and verify the timer was not restarted.
  With no nearby eligible remains, confirm no launch; already-active fleets should keep working.
- Save/reload with an active target and a pending response. Confirm caps and once-only delivery.

Broader regression matrix (not all cases have been individually confirmed):
1. New campaign, with and without Nex: create small and large NPC battlefields in multiple Core systems.
   Confirm 2–4-day response after detection, offscreen appearance, 3-global/1-local caps and 5-day spacing.
2. Inspect four/five-ship fleets: exactly one Calceus, Shepherd and Tarsus, small escort(s), commander
   flagship, low-quality fits and no subordinate officers. Confirm normal encounter/transponder behavior.
3. Observe collection of separate wrecks and debris-contained ships: half-day work, weapons/materials
   gained, no recovered hulls, no repeat salvage or AI-core/LPC/hullmod/blueprint stock.
4. Race player and Vultures against a Refugee target, including during its hold. Confirm no duplicate
   collection. Let targets expire or become quest-important en route; confirm safe retarget/return.
5. Verify friendly custom-faction fleets do not cause flight; introduce an actually hostile fleet to test
   withdrawal and interception. Destroy a loaded expedition and verify no delivery and slot release.
6. Observe visible return then offscreen retirement. Check only Asharu/Mairaath receive goods. Capture,
   disrupt, or remove the selected recipient, then both recipients, before return to test fallback/loss.
7. Repeat deliveries to saturate commodity cap; let 30 days elapse and verify independent expiry.
   Check valuable weapon frequency, ordinary refresh turnover, and fleet capacity behavior.
8. Save/reload during pending response, travel to target, collection, loaded return and after delivery.
   Verify no extra managers, repeat cargo handoffs, renewed timers or orphan fleet references.
9. Long campaign at default and increased caps: monitor fleet count, frame time and save growth.
   Test zero cap and lowering cap with active expeditions. Tune weapon volume and response timing.
10. In a mixed Core system, leave a pirate station and patrols at their market while creating debris
    elsewhere: dispatch should proceed. Put debris inside the 2,500-unit market bubble: it must be
    skipped. Move a patrol outside its bubble or introduce a raid fleet: a matured response must defer
    without resetting, then launch after the mobile threat clears if eligible salvage remains.

Implementation: `ExileRefugeeSalvageExpeditionManager`, `ExileRefugeeSalvageAssignmentAI`,
`ExileRefugeeSalvageRecovery`, `ExileRefugeeSalvageThreats`, `ExileRefugeeSalvageConfig`, plus the
Calceus hull/variant/data entries.

### Open design items

**1. VIABILITY REVIEW WANTED: expeditions SELL salvage to the player (Sean, 2026-11-22). Not
designed, not scheduled, nothing built.**

Origin: fixing the 0.98.5a `SellToFleet` error (`EXILES_0985A_COMPAT_REVIEW.md` §8.4) raised
whether refugee salvagers should BUY the player's cargo, which is what vanilla's new option does.
Sean: he has a hard time picturing them simply buying cargo from the player, but salvagers
SELLING cargo "is a neat idea"; it may turn out to be "more trouble than it's worth", but is worth
looking into. The task is a viability review only: how hard it is, and which questions need a
ruling. Do not build from this note.

Questions for that review **[ASSISTANT; a starting list, none answered]**:

- **Mechanism.** Vanilla's `SellToFleet` only moves cargo from the player TO a fleet. Is there a
  vanilla command or screen for buying FROM a fleet's cargo? If not, this needs a runtime-loaded
  `InteractionDialogPlugin`; a new custom rule command is ruled out (CLAUDE.md §7).
- **Contract.** The approved contract above says "no custom dialogs". Selling would amend it.
- **What is for sale.** What an expedition actually carries, and when: nothing on the way out,
  real salvage on the way home? Commodities only, or the recovered weapons too?
- **What it costs the refugees.** Does anything sold come out of the delivery to Asharu or
  Mairaath? If so, the player buying salvage starves the settlement, which may be the point
  (a grey choice) or may be a griefing lever.
- **Price and gating.** A flat discount, or tied to refugee standing and the future
  refugee-trust quest line? Do they sell to someone they distrust?
- **Abuse.** Can it be farmed, alone or with NPC-battle wreck farming (the reason expeditions
  claim the best wrecks first)? The no-recurring-AI-core-sales constraint must hold.
- **Tone.** Refugees are the human cost, weaker than the Independents. Does a shop on their
  salvage fleets help that read or undercut it?
- **Cost.** Config keys, placeholder prose for Sean, save footprint, a test matrix.

---

## Historical record — superseded decorative version and August design pause

The remainder records the prior version; it is not the current implementation contract or test matrix.
Prior status: version one live-tested 2026-08-23; configurable cap Janino-clean, pending retest.

## Purpose and boundary

Refugee Salvage Expeditions are atmospheric Core traffic. They make the Refugees' scavenging economy
visible without creating a real competing salvage economy. Version one never consumes or marks a
debris field, changes cargo, grants rewards, creates intel, or applies custom relationship effects.
There are no supply donations or custom fleet dialogs.

## Design pause — 2026-08-27 (resume over the weekend)

Omicron-core acquisition through this ecosystem, if used, must be a **rare or one-time,
story-driven quest acquisition**. It must not become recurring market stock or a general core
economy. The quest should explicitly control the core grade and exact quantity. A Refugee,
Independent, or Knights-of-Ludd recovery chain may provide the item's provenance without making
that actor a permanent core vendor.

Nexerelin's Vulture Scavengers are a useful reference and should not be treated as stealing the
player's earned battle rewards. The player receives first choice of recovery and salvage before
returning to the campaign map. Vultures primarily consume the secondary wrecks and battle debris
left by NPC-only combat—the ambient free salvage available to a player who watches from the
sidelines. Their role as an aggressive ambient-salvage sink is therefore broadly legitimate.

Questions deliberately left open for the weekend:

- Whether Refugee expeditions should remain atmospheric and non-destructive or become a real,
  bounded cleanup system.
- Whether Independent scavengers should clean up Black Fleet and Omicron battlefields that do not
  attract Nexerelin Vultures.
- Whether Church-controlled systems should instead dispatch specialized Knights fleets to gather
  Omicron drones and cores for ritual destruction.
- Who holds and transfers the quest's specific core, and how that non-combat handoff fits the
  Refugees' trauma, Church doctrine, and local territorial boundaries.
- Keep the cleanup actor and quest-core provider separate if combining them would force an
  implausible faction role.

## Launch cadence and cap

- `ExileRefugeeSalvageExpeditionManager` receives one launch opportunity every **30 campaign days**.
- `salvageExpeditions.maxActiveExpeditions` caps concurrent global routes independently of ordinary
  Refugee convoys. It ships at 3 (Sean's deliberate value, 2026-09-08, ruled 2026-10-06; hard
  ceiling 12); 0 prevents new launches. A full cap skips that opportunity.
- Source and target selection happens only at the opportunity. If no eligible pair exists, the cycle
  is skipped with no retry and no catch-up.
- A source must be a live, visible Refugee market with a functional `exile_salvage_yards` and a
  functional Spaceport or Megaport. This dynamically admits both current working Refugee enclaves
  without hardcoding their entity ids.

## Debris selection

The manager scans star-system terrain only at launch opportunities and considers live
`DebrisFieldTerrainPlugin` entities within the configured source distance. Fields in vanilla's
final three-day dissipation window are rejected, and remaining lifetime relative to the planned
preparation/outbound/salvage commitment contributes a multiplier to candidate weight.

Selection uses four strict priority tiers:

1. battle-source debris in Core systems;
2. battle-source debris outside the Core;
3. other debris in Core systems;
4. other debris outside the Core.

Within a battle tier, the live-raid multiplier favors systems with a live Exiles `RaidIntel`
operation. Only when no reachable battle field exists does selection fall back to other debris.
Remaining lifetime and distance weight candidates within each tier.
There is deliberately **no raid or combat-system veto**.

Starsector exposes `DebrisFieldSource.BATTLE` but no creator faction. A live Exiles raid in the same
system is therefore the narrowest practical proxy for Exiles-created debris. Version one does not
add a battle listener or persistent attribution marker solely to improve this decorative weighting.

## Fleet and visible loop

- The global route is owned by vanilla `RouteManager`.
- Fleet faction: Refugees (`exilerefugee`); type: small scavenger.
- Composition is guaranteed rather than left to tiny market-scaled budgets: one or two generated
  Refugee combat-role escorts, two `shepherd_Frontier`, and one `tarsus_Standard`, at most five
  ships, with no hull above destroyer size. Fleet generation ignores the source market's fleet-size
  multiplier. When the custom salvage Tarsus exists, it replaces one of the two Shepherd calls.
- Vanilla commander setup marks a combat escort as the flagship. Subordinate officers are capped at
  zero, so the fleet gains the normal flagship/portrait relationship without an officer roster.
- The transponder remains on. The fleet is vulnerable, destroyable, and uses ordinary faction
  relationships.
- Visible assignments are: prepare at the source, travel to the selected debris site, orbit it for a
  fixed **10 days** while “conducting salvage operations,” return to the source, orbit briefly, and
  despawn there.
- Neither reaching nor orbiting the debris changes it in any way.

The route targets a stable, invisible token at the field's last-known location rather than the
expiring terrain entity itself. If the selected field expires, the expedition continues into the
system. At the salvage leg it first looks for another live local field, favoring battle debris and
proximity; if none exists, it surveys the last-known site for the planned duration. It never reverses
in hyperspace solely because terrain expired and never extends or changes vanilla debris lifetime.

While outbound or salvaging, a local interval check examines only fleets visible to the expedition.
A hostile fleet with at least 75% of the expedition's effective strength is credible danger: the
expedition breaks off, displays a fleeing-home assignment, and follows its return route. This is not
a pre-launch safety filter and does not make the expedition immune to interception or destruction.

The faction-root `exilerefugee.debugMessages` toggle controls the same yellow campaign debug channel
for both ordinary Refugee convoys and salvage-expedition dispatch messages.

## Performance and persistence

- No sector-wide every-frame scan exists.
- The only sector scan occurs once per 30-day launch opportunity.
- Battle and other candidates are separated during that existing scan; no additional scan,
  polling script, or battle listener is added.
- Each active physical fleet runs its danger check every 0.25–0.5 campaign days and examines only
  locally visible fleets; total fleet count remains bounded by `maxActiveExpeditions`.
- The manager is a saved `EveryFrameScript`; `ExilesModPlugin` registers it on new games and restores
  it idempotently on load.
- RouteManager owns fleet spawning, abstract travel, destruction cleanup, return, and despawning.

## Implementation map

- Manager/spawner: `data/scripts/exiles/ExileRefugeeSalvageExpeditionManager.java`
- Assignment/flee behavior: `data/scripts/exiles/ExileRefugeeSalvageAssignmentAI.java`
- Tuning loader: `data/scripts/exiles/ExileRefugeeSalvageConfig.java`
- Tuning: `data/config/exiles/director_config.json` →
  `exilerefugee.salvageExpeditions`
- Registration/load restoration: `data/scripts/exiles/ExilesModPlugin.java`
- Source industry: `data/scripts/industries/ExileSalvageYards.java`

## Focused live-test checklist

1. With no eligible debris, advance through a 30-day opportunity: no route launches and no delayed
   catch-up launch occurs.
2. With eligible debris and both yards functional, observe that either current enclave can be picked.
   Disrupt one yard and then its Spaceport; each disruption must exclude that source.
3. At the default cap, confirm only one route exists globally. Set `maxActiveExpeditions` to 2 and
   verify a later opportunity can create a second route, while a full cap blocks additional routes.
   Lowering the cap must not cancel existing routes; 0 must prevent new launches.
4. Inspect the fleet: Refugee faction, transponder on, one or two combat-role escorts, exactly two
   Shepherds, exactly one standard Tarsus, four or five ships total, no cruiser/capital, and one
   gold-star flagship whose captain is the commander shown during communication.
5. Follow the full source → debris → fixed salvage orbit → source → despawn loop and verify the
   assignment text at every phase.
6. Save/reload during preparation, outbound travel, salvage orbit, return, and final source orbit.
7. Let the target debris expire before arrival: the fleet should continue into the system, use a
   replacement local field when one exists, otherwise survey the stable last-known site, then return.
   Repeat during the salvage orbit and confirm the assignment switches without an early return.
8. Put a credible hostile fleet near the outbound/salvaging expedition, including in an active raid
   system: it should break off toward home but remain interceptable and destroyable.
9. Destroy the expedition and verify the route clears and no replacement appears before the next
   ordinary 30-day opportunity.
10. Before and after the visit, verify the debris, player salvage rewards, market cargo/economy,
    intel list, and faction relationships are unchanged.
11. Toggle `exilerefugee.debugMessages`: launch messages should follow the same switch as ordinary
    Refugee convoy debug messages. The message should identify debris source and days remaining;
    the log should additionally identify the target ID, priority tier, and candidate-pair counts.
12. Create a reachable battle field before a launch opportunity: it should be selected ahead of all
    non-battle fields. With no reachable battle field, the expedition should use the normal Core-
    first fallback.
