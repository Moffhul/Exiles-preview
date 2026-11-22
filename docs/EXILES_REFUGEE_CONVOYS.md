# Exiles — Refugee Convoys

Status: **PHASE-1 PROTOTYPE LIVE-TESTED 2026-07-23; RECEPTION/ANNOUNCEMENT REDESIGN
LIVE-TESTED AND ACCEPTED 2026-08-19; CONFIGURABLE MULTI-CONVOY CAP REFACTOR
JANINO-CLEAN 2026-08-23, PENDING LIVE RETEST.**

Created: 2026-07-19

## 1. Core concept (author proposal)

Refugee colonies and selected crisis worlds such as Gilead periodically produce a **Refugee
Convoy** bound for a random Core World. These are not conventional trade fleets or an organized
military operation. They represent desperate civilians leaving the enclaves and other damaged
worlds in search of work, shelter, salvage, or enough resources to survive another day.

When a convoy lingers at its destination, that market temporarily gains **Refugee Encampments**. In-world,
the new arrivals look for work, engage in petty theft, take anything that resembles abandoned
salvage, and place pressure on already-fragile local institutions. The behavior should make the
Refugee crisis physically visible across the campaign map while preserving the Refugees as weak,
civilian, morally complicated survivors rather than another raiding faction.

The implementation rulings were ratified 2026-07-23. The first convoy launches after 5 days for
testing and posts config-gated debug lifecycle messages. Production cadence remains 60–100 days.

## 2. Feasibility assessment

**High feasibility; moderate implementation scope; low performance risk while bounded.** A single
persistent manager chooses sources and destinations and owns a config-bounded operation list.

Implemented event sequence:

1. A global scheduler reaches its launch interval.
2. Select one eligible source and one eligible Core destination.
3. Create a weak civilian Refugee-faction convoy.
4. Travel from source to destination.
5. If the fleet is destroyed before arrival, apply no market condition.
6. If it arrives, orbit for 10 days before applying temporary Refugee Encampments.
7. Around day 20–25, local spaceport control makes one flat 70% reception check. Success orders
   withdrawal; failure is not rerolled and the convoy leaves on its own by day 30.
8. Refugee Encampments remains for 10 days after departure/destruction, then clears.

The trip is **one-way**. These people are emigrating, not conducting a round-trip trade run.
Public-facing prose should generally say convoys **depart from** a source rather than implying that
the source government formally sends or controls them.

Convoys remain directly spawned so they are always physical and findable. The manager now owns a
saved list of independent operations, each with its own fleet, destination, phase, condition state,
reception roll, and cleanup. In-flight destinations are reserved so concurrent convoys cannot
converge on one market. The former single `active` save field migrates into the list on load.

All adjustable Refugee fleet/activity settings belong in
`data/config/exiles/director_config.json`, following its existing conventions. `maxActiveConvoys`
is consumed by the multi-operation manager; it ships at 3 (Sean's deliberate value, 2026-09-08, ruled
2026-10-06; hard ceiling 12, clamped with a log warning) and 0 prevents new launches while
allowing existing operations to finish. Values should remain modest because every convoy
is a physical fleet. The existing FP-by-role block is the intended granular fleet-composition tuning
surface. **Author ruling 2026-08-17:** keep
`qualityMod` hardcoded because near-junk ship quality is fixed Refugee identity, not a useful player
option. Engine-safety invariants such as arrival radii, route identifiers, migration state, and
forced cleanup timeouts should remain code.

## 3. Condition implementation constraint

The existing `exile_refugee_strain` condition is chronic on several authored markets and currently
applies:

- −3 stability
- −25% accessibility
- +10% to prices the player pays when buying commodities there

Do **not** add a universal expiration timer to `ExileRefugeeStrain`: that could cause the authored,
permanent copies to remove themselves.

Resolved with a distinct player-facing condition:

- Name/id: **Refugee Encampments** / `exile_refugee_encampments`.
- −2 stability.
- +20% to player buy prices for every commodity except crew.
- −25% to both player crew hiring and crew selling prices.
- No accessibility, shortage, supply, or NPC-economy effect.
- Normal removal is manager-controlled; a 60-day self-expiry is an orphan-condition backstop.

Destination selection prevents chronic Refugee Strain and Refugee Encampments from stacking.
Other price modifiers deliberately compose multiplicatively through the normal player-price APIs.

## 4. Safety and balance

The existing full-strength condition is a serious crisis rather than a minor nuisance. Its effects
will combine with Black Fleet attacks, Omicron activity, shortages, and the future Hegemony–League
war. The primary design risk is cumulative market collapse, not code complexity.

Ratified prototype values:

- `maxActiveConvoys: 3` by default (ruled 2026-10-06; ceiling 12); independently tunable from salvage expeditions.
- First launch after 5 days for testing; subsequent launches every 60–100 days.
- Source and destination cooldowns: 180 days.
- Minimum destination stability: 5.
- Player colonies excluded.
- No food/supplies shortage event.
- No chronic Refugee Strain or Refugee Encampments destination.
- A failed launch selection retries after 7 days rather than consuming a full cadence.

The first launch and all lifecycle debug messages are testing aids and are explicitly flagged in
configuration for production review.

## 5. Sources and destinations

### Sources

Implemented source model:

- The four Refugee enclaves plus Gilead, by configurable primary-entity id.
- Resolve configured ids against the live economy at launch time; skip markets that are dead,
  hidden, inaccessible as fleet sources, or lack a functioning spaceport where appropriate.
- Give individual sources cooldowns so one enclave does not produce every visible convoy.

Keep the special-source list in configuration rather than burying it in fleet logic. This makes
future lore adjustments and compatibility changes inexpensive.

### Destinations

Use the live economy rather than a hardcoded vanilla market list so ownership changes and compatible
mod-added Core markets can be handled sensibly. Implemented eligibility:

- Living, visible, in-economy Core markets with a valid destination entity.
- NPC-owned.
- Not the source market.
- Not already strained and not on destination cooldown.
- Stability 5 or higher.
- System tagged as populated Core.
- Exclude Pirates, Luddic Path, Refugees, Player, Ashen Crusade, Omicron, Black Fleet, and Midan.
- Exclude either-direction `HOSTILE` relations, while allowing `INHOSPITABLE`.
- Exclude the entire system if any live `RaidIntel` targets it. This covers vanilla raids,
  Black Fleet commerce raids/assaults, Omicron raids/incursions, and future Ashen attacks if they
  use the same pipeline.

Selection is weighted by `max(1, size - 2) * max(0.25, accessibility)`. There is no faction
preference or distance limit. Volatile eligibility is checked again when the convoy arrives.

## 6. Fleet identity and behavior

Implemented first-pass fleet character:

- Refugee faction, clearly named **Refugee Convoy**.
- Civilian transports, freighters, and tankers; poor quality, D-mods, minimal defensive capability.
- Avoidance and escape behavior, never patrol or aggression behavior.
- Little valuable cargo, preventing a recurring stream of easy salvage rewards.
- Assignment text should communicate departure, travel, and attempted resettlement.
- Petty theft and opportunistic salvage should initially be represented through prose and the
  destination condition, not complicated scavenging detours or raid AI.

Relationship resolution: `INHOSPITABLE` does not satisfy the engine's combat-hostility test, so
ordinary destination patrols do not attack the convoy. Actually hostile owners are excluded at
selection and arrival. Pirates and genuine enemies encountered in transit may still intercept it.
The convoy is marked as a trade fleet, keeps its transponder on, and attempts to disengage if attacked.

## 7. Player-facing consequences and later extensions

Version one should require actual arrival: destroying or losing the convoy prevents the condition.
That creates meaningful emergent outcomes without needing a quest interface. Player attacks should
use ordinary Refugee reputation consequences and should not be profitable enough to become an easy
farming loop.

Potential later extensions, explicitly outside the first implementation pass:

- Escort, redirect, admit, or turn-away choices.
- Relief deliveries and Refugee reputation rewards.
- Occasional food/supplies shortages after a severe arrival.
- Different reception policies by destination faction.
- Intel or news reports for launches, losses, and arrivals.
- Phase-2 Refugee trust and settlement decisions.

Avoid routine global intel spam. Fleets are visible when encountered, and Refugee Encampments makes
successful arrivals legible at the destination. Debug messages temporarily announce each lifecycle
step for the live test and are config-gated for removal.

## 8. Performance and compatibility guardrails

- Use one bounded manager, not one script per source market.
- Cap concurrent operations with `exilerefugee.maxActiveConvoys`; keep the value modest because the
  fleets are directly spawned rather than route-abstracted.
- Select markets and scan live raid intel only when launching; revalidate once at arrival.
- Reserve each in-flight destination in operation state so concurrent convoys cannot duplicate it.
- Resolve arrival from saved operation state and ensure it fires once.
- The temporary condition may run its own simple timer, following the mod's existing timed-condition
  pattern; only affected markets pay that trivial cost.
- Validate source and destination again at arrival because Nexerelin may change ownership,
  decivilize a market, or otherwise alter the economy during travel.
- Do not register a custom creator into vanilla's global static misc-fleet creator list.

## 9. Live-test checklist

Initial live testing on 2026-07-23 confirmed that the prototype boots, launches, and functions in
campaign. The first fleet exposed one cosmetic naming bug: the stored name included the faction
name, producing "Refugee Refugee Convoy." The base name is now "Convoy," with a save-compatible
normalization for an already-active fleet. Continue using the matrix below for timing and edge-case
tuning.

1. First fleet launches near day 5 and the debug message names its source and destination.
2. Fleet is a weak, civilian Refugee Convoy with transponder on and reaches the named market.
3. It orbits without a condition for 10 days; Refugee Encampments then appears with −2 stability,
   +20% non-crew buy prices, and −25% crew buy/sell prices.
4. With `earlyDepartureChance:1.0`, local spaceport control orders withdrawal around day 20–25.
   The full vanilla campaign message appears only when the convoy is in the player's current
   location and visible to the player fleet, accompanied by a quiet vanilla communications cue.
   The convoy remains in passive orbit for `orderedDepartureDelayDays` before its assignment reads
   as ordered withdrawal and it begins moving toward a jump point.
5. Convoy leaves through a jump point; the condition clears 10 days later.
6. With `earlyDepartureChance:0.0`, the single reception check fails and is not repeated; the
   convoy self-departs by day 30.
7. Destroying the convoy before arrival produces no condition; destroying it after encampments
   appear starts the same 10-day aftermath.
8. Set `maxActiveConvoys` to 2 and temporarily shorten the launch interval: two operations may
   coexist, must choose distinct destinations, and must resolve their conditions/cleanup separately.
   Lowering the cap must not despawn operations already underway; `0` must prevent new launches.
9. No destination is selected in a system with a live vanilla/Black Fleet/Omicron `RaidIntel`.
10. Save/reload during travel, orbit before/after the reception check, the ordered-wait phase,
   departure, and aftermath does not duplicate the manager, fleet, condition, reception roll,
   sound, or messages.
11. Before production, set `debugMessages:false` and decide whether to retain the 5-day first launch.

## 10. Reception redesign (live-tested and accepted 2026-08-19)

Live testing found that the physical patrol response was rarely legible and could look wrong: the
manager's fixed intercept timer could order the convoy to depart before vanilla's fleet-to-fleet
`HassleNPCScript` visibly completed its rendezvous. The manager itself has no player-location gate,
so the redesign removes this unnecessary presentation dependency rather than trying to make the
physical interception more elaborate.

Compatibility boundary: Exiles requires a new campaign for its game-start world and economy changes
and does not prioritize integration into an existing non-Exiles save. Preserving the legacy convoy
intercept phase is still worthwhile because it is a small safeguard for active development saves;
it retains the old 2.5-day cleanup window so an already-serialized vanilla hassle script can release
both fleets before ordered departure. Ordinary save/load stability inside an Exiles campaign remains
required.

Implemented replacement:

- Preserve the physical convoy and every lifecycle rule outside reception.
- At one random configured day from 20–25, make exactly one flat 70% roll.
- On success, local spaceport control announces the order, the fleet pauses briefly in passive
  orbit, and then begins the existing departure lifecycle without a responding patrol.
- On failure, make no further reception rolls; the convoy remains until the existing day-30
  self-departure fallback.
- Preserve the 10-day Refugee Encampments aftermath.
- Use generic, non-faction-specific prose. Preferred explanation: “Based on complaints to local
  authorities, the local spaceport control orders the refugees to withdraw immediately.”
- Deliver that explanation through vanilla `CampaignUI.addMessage(...)` only when the convoy is in
  the player's current location and visible to the player fleet (`isInCurrentLocation()` plus
  `isVisibleToPlayerFleet()`). The reception outcome and assignment change still occur globally;
  there is no global message or intel item.
- Accompany a locally visible announcement with vanilla `ui_noise_static_message_quiet`, then retain
  passive orbit for the configurable `orderedDepartureDelayDays` (default `0.25`) before departure.
- Rename the shipped timing keys to `receptionCheckMinDays` / `receptionCheckMaxDays`, add
  `earlyDepartureChance:0.7`, and remove `interceptDays`. The loader will accept the former
  `patrolResponseMinDays` / `patrolResponseMaxDays` keys as backward-compatible aliases.
- Do not tie the first version to Security Center, stability, patrol availability, or faction.
- Keep production presentation to the fleet's visible assignment; retain config-gated lifecycle
  debug text for testing and avoid global intel spam.

Build verification: `director_config.json` parsed after Starsector-style comment stripping;
`ExileRefugeeConvoyManager` passed the repository Janino check (`EXIT=0`); stale physical-intercept
runtime references were removed, leaving only documented legacy saved-state/key aliases. The
2026-08-18 announcement-delay refinement also passed config parsing, Janino compilation, and
diff/whitespace checks. The author live-tested the completed behavior and accepted it on 2026-08-19.

## 11. Phase-2 settlement migration (ratified 2026-08-19; no new code yet)

This is a separate, constructive fleet activity unlocked by the future Refugee trust quest. It is
not another outcome of the disruptive convoy scheduler. Keeping two independent clocks prevents a
long-lived migration fleet from suppressing a disruptive convoy through shared lifecycle state and
lets gameplay pressure and ambient migration be tuned separately.

### Shared settlement gate

Positive Refugee relation alone changes nothing before the gate. The gate must include an explicit
player decision inviting Refugees to settle at the player's colonies; the quest's offer requirements
and minimum relation remain Phase-2 writing decisions. That one persistent decision will:

- enable the independent migration-fleet clock while current Refugee relation is positive;
- enable positive Refugee relation to slow the disruptive convoy clock; and
- apply **Refugee Influx** (`exile_refugee_influx`) to every genuine player-owned colony, including
  colonies founded or acquired later while the settlement program remains active.

Refugee Influx is long-term but not necessarily permanent. It remains if relations later deteriorate
because settlers already present do not disappear; its eventual removal belongs to the future
resolution of the Refugee crisis. `ExileRefugeeInflux` and `ExileRefugeeIntegrationListener` already
provide the dormant condition behavior. Do not invent a temporary activation script or quest flag
before the actual quest owns their lifecycle.

### Slower disruptive clock

After the invitation, scale only the disruptive clock's accumulation rate:

`progress += elapsedDays * (1 - max(0, refugeeRelation))`

The Starsector relation value is normalized from `-1` to `1` and should be clamped before use. At
neutral or negative relation the clock advances at full speed; at +0.50 it advances at half speed;
at +1.00 it pauses. Before the invitation it always advances at full speed regardless of relation.
Retain accumulated progress while paused so worsening relations resumes the existing clock rather
than resetting it. This arithmetic belongs in the manager's existing 0.1–0.2 campaign-day tick and
adds no meaningful runtime cost.

### Independent migration fleets

- Use a separately saved, bounded scheduler with its own randomized interval and one-active-fleet
  limit for the first implementation. Do not share its active cap or launch clock with disruptive
  convoys; one of each may coexist.
- Run the migration clock only while the settlement gate is active and current Refugee relation is
  positive. At neutral or worse, pause new launches and retain clock progress. A fleet already under
  way completes its route.
- Choose a random eligible live Refugee enclave as source. Choose among genuine player-owned colony
  markets at dispatch, weighted only by market size. This launch-time pass over the player's small
  colony list is negligible; do not continuously score habitability or accessibility.
- Follow vanilla trade, Galatia Academy, and Luddic pilgrim fleets for route behavior and quiet
  presentation. The fleet is identifiable in-world but creates no routine intel item.
- At the destination, orbit while transferring settlers or preparing to land. It may then despawn
  near the colony to represent landing, or return to its enclave with assignment text establishing
  that empty transports are returning after delivery. It applies no market condition on arrival;
  the settlement gate, not individual fleet success, owns Refugee Influx.
- Fleet destruction, route cleanup, or a failed launch must never change demographic state. These
  fleets depict migration; they are not the population ledger.

Any future manager, helper, route ID, memory key, configuration ID, or other Exiles-owned artifact
introduced for this feature must follow the project namespace convention: `Exile...` for Java
classes/files, `exile_...` for lowercase content IDs, and `$exiles_...` for private memory keys.

### Future enclave depletion boundary

Long-term settlement may eventually reduce enclave size or eliminate an enclave, based on elapsed
time and player-colony capacity/size. Track that through a separate demographic model rather than
counting physical migration fleets. Market sizes are coarse and enclave removal can invalidate
quests, contacts, or authored content, so define population thresholds and content migration before
changing or removing any live market.
