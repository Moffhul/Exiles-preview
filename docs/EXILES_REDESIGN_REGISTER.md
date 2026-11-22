# Exiles Redesign Register

Created: 2026-08-16

Status: **ACTIVE PLANNING DOCUMENT — PLAYER-OBSERVED REDESIGN, NOT A DEFECT LIST.**

## 1. Purpose and working method

This register tracks systems whose original implementation may be technically correct but whose
in-game behavior does not fully produce the experience the author intended. Treat these as
evidence-led redesigns, not as proof that the earlier design or code was "wrong."

For each item:

1. Record the current behavior and the author's playtest observation.
2. Agree on the intended player experience and the behaviors that must remain unchanged.
3. Trace dependencies, lifecycle, performance, compatibility, and save/load implications.
4. Choose the smallest robust design that reaches the intended experience, preferring vanilla APIs.
5. Write an implementation and test plan before changing code.
6. Perform source-level, Janino/compile, configuration, and focused lifecycle tests where possible.
7. Record the author's in-game result against the intended experience, then tune or redesign again.

Standing priorities:

1. Keep runtime and save costs bounded; use proven vanilla APIs and lifecycle seams where they fit.
2. Optimize for a fun, readable player experience rather than simulation for its own sake.
3. Keep meaningful design values easy to tune, with safe defaults and validation. Do not expose
   internal invariants merely to maximize the number of settings.

Campaign compatibility boundary (author reaffirmation 2026-08-17): Exiles is designed for a new
game and does not prioritize integration into a campaign that began without the mod or before its
game-start changes. Ordinary save/load stability within an Exiles campaign remains mandatory.
Low-cost compatibility for development saves is desirable, but never at the cost of brittle code or
a compromised design.

Collaboration rule: provide candid technical and design push-back when a request would introduce
fragility, excessive resource use, or complexity disproportionate to its player-facing benefit.
Separate **observed**, **accepted direction**, **recommendation**, and **open decision** so that an
assistant proposal never becomes an author ruling by implication.

## 2. Initial work sequence

The first three items share the Refugee faction but should not be forced into one implementation.
Design the convoy reception and Security Center together because the industry could plausibly
affect reception. Implement them as separate changes. Treat salvage expeditions as a separate,
bounded activity lifecycle and do not prematurely generalize all Refugee fleets behind one base
class.

Provisional sequence:

1. Settle convoy reception and quest-boundary decisions; simplify the existing convoy lifecycle.
2. Redesign Security Center and approve the NPC-market placement list.
3. Settle salvage-expedition scope; implement only after deciding whether it is decorative or
   allowed to consume player-accessible salvage.
4. Revisit random refugee-removal missions during Phase 2 quest design, using the convoy system's
   concepts or shared predicates without depending on its internal operation list.

## 3. Refugee Convoys — replace physical patrol interception

### Status

**RECEPTION REDESIGN LIVE-TESTED AND ACCEPTED 2026-08-19. CONFIGURABLE MULTI-CONVOY CAP REFACTOR
JANINO-CLEAN 2026-08-23; PENDING LIVE RETEST.**

### Observed in game (author, 2026-08-16)

- Refugee convoys are rarely visibly chased away from the destination they orbit.
- When a response does occur, the visual sequence is odd: the convoy may leave orbit just before
  the responding patrol reaches it.
- Author suspects the interception may only occur while the player is in the system.

### Previous implementation baseline (replaced 2026-08-17)

- `ExileRefugeeConvoyManager` is one global `EveryFrameScript`, gated by a 0.1–0.2 campaign-day
  `IntervalUtil`, and owns at most one physical convoy.
- A convoy applies Refugee Encampments after 10 orbit days. At a random day 20–25, the manager scans
  the destination system for an eligible same-owner patrol. If none exists, it retries until the
  day-30 self-departure fallback.
- When a patrol is found, the manager starts vanilla `HassleNPCScript` for a two-day visual
  interaction but independently enters its own intercept phase and orders departure after 2.5 days.
  The manager does not wait for the two fleets to meet or for the vanilla script to report success.
- `findRespondingPatrol()` and the manager's phase timer contain no player-location check. This
  makes a hard player-presence dependency unlikely in Exiles' own code. Whether location-attached
  scripts receive identical off-screen simulation is still worth verifying if the visual mechanism
  is retained, but the manager's independent fixed timer already explains the observed premature
  departure.
- The convoy condition aftermath, source/destination cooldowns, volatile arrival revalidation,
  active-raid avoidance, bounded cap, and timed self-departure are independent of patrol
  interception and should remain unless separately redesigned.
- **Configuration ruling 2026-08-17:** every adjustable Refugee fleet/activity value belongs in
  `data/config/exiles/director_config.json`, following the existing faction-activity conventions.
  Keep lifecycle invariants and migration plumbing internal rather than exposing unsafe knobs.

### Accepted direction

- Remove the physical patrol interception/rendezvous mechanic.
- Replace it with a simpler, simulation-wide reception outcome representing either spaceport
  control ordering the refugees to depart or local security removing the encampment and ordering
  the fleet away.
- The outcome should end the visit early without requiring a suitable patrol fleet to exist or
  reach the convoy.
- **Author ruling 2026-08-17:** make exactly one flat 70% reception roll at the existing random
  day 20–25 response point. Success orders departure; failure leaves the convoy in orbit
  until the existing day-30 fallback. Do not repeat the roll.
- **Author ruling 2026-08-17:** keep the reception generic rather than faction-specific. Preferred
  explanatory prose: “Based on complaints to local authorities, the local spaceport control orders
  the refugees to withdraw immediately.” A shorter fleet assignment may say “withdrawing under
  orders from local spaceport control.”
- **Author ruling 2026-08-17:** approve backward-compatible configuration renames and removal of the
  obsolete `interceptDays` setting from the shipped configuration.
- **Author ruling 2026-08-17:** send the explanatory campaign message only when vanilla proximity
  rules make the event locally observable: the convoy must be in the player's current location and
  visible to the player fleet. The reception outcome and fleet assignment still occur globally.
- **Author ruling 2026-08-18:** accompany a locally observable order with the vanilla quiet static
  message cue, retain passive orbit for a configurable short pause (default `0.25` campaign days),
  and only then issue the departure assignment so the cause-and-effect is visually legible.

### Ratified first version

Use one bounded reception check per visit at the existing configurable response day, then transition
directly from orbit to departure. Do not roll every frame or every day. A single roll is cheaper,
easier to reason about, deterministic in lifecycle shape, and easier to tune.

Implementation behavior:

- Replace shipped `patrolResponseMinDays` / `patrolResponseMaxDays` with
  `receptionCheckMinDays` / `receptionCheckMaxDays`; the loader continues to accept the old keys as
  fallbacks so existing manually tuned configurations do not break.
- Add `earlyDepartureChance`, default `0.7`, clamped to the safe range 0–1.
- Remove `interceptDays` from the shipped configuration and runtime behavior.
- Roll once when the response day is reached. Save a `receptionChecked` flag before evaluating the
  result so save/reload cannot create repeated attempts.
- On success, use reception prose and enter a saved ordered-wait phase; retain passive orbit for
  `orderedDepartureDelayDays` before calling the existing departure path. On failure, leave at
  `maxOrbitDays` as now.
- Remove the patrol scan, `HassleNPCScript`, and the active rendezvous. Migrate an active saved
  `PHASE_INTERCEPT` operation directly to departure rather than invalidating the convoy.
- Keep the physical convoy: its travel, possible destruction, arrival, and visible orbit are the
  player-facing point of the feature. Only the reception simulation is being simplified.
- Do not connect the initial chance to Security Center, market stability, patrol strength, or
  faction. Those are unnecessary hidden couplings for the first version.
- Use vanilla `CampaignUI.addMessage(...)` for the full explanatory message only when
  `fleet.isInCurrentLocation()` and `fleet.isVisibleToPlayerFleet()` are both true. This delegates
  proximity to vanilla location, sensors, and transponder behavior rather than inventing a fixed
  distance. The fleet's assignment communicates the result on the campaign map, while config-gated
  lifecycle debug text supports testing without global intel.

### Resolved design decisions

1. **Reception actor and tone:** generic local authorities/spaceport control.
2. **Chance model:** one flat 70% roll; no market or industry modifiers.
3. **Timing:** retain the random day 20–25 check.
4. **Failure meaning:** remain until day 30; no repeated enforcement attempt.
5. **Player notice:** a vanilla campaign message only when the convoy is in the player's current
   location and visible to the player fleet, plus the fleet assignment and config-gated debug text;
   no global message or intel.

### Build record (completed 2026-08-17)

1. Preserved the legacy numeric `PHASE_INTERCEPT` value and deprecated saved fields so an existing
   development operation can load; that phase honors its old 2.5-day cleanup window before converting
   to departure, allowing an already-serialized vanilla hassle script to release both fleets. This is
   a cheap nice-to-have, not a requirement to integrate Exiles into pre-existing campaigns.
2. Added saved `receptionChecked` state and transient reception timing/chance configuration with safe
   defaults and range/order normalization.
3. Replaced the patrol lookup branch in `PHASE_ORBIT` with the single reception roll. The check is
   recorded before rolling; the operation then either begins ordered departure or waits for
   `maxOrbitDays`.
4. `beginDeparture()` now accepts an assignment-text/cause variant so ordered withdrawal is distinct
   from voluntary timeout departure without duplicating departure logic. At ordered withdrawal,
   it sends the vanilla campaign message only if `isInCurrentLocation()` and
   `isVisibleToPlayerFleet()` both pass.
5. Removed `HassleNPCScript` imports, `beginPatrolResponse()`, `findRespondingPatrol()`, patrol-only
   memory checks, and the obsolete runtime intercept duration.
6. Updated `director_config.json`, this register, and `EXILES_REFUGEE_CONVOYS.md`. Retained legacy
   response-key aliases in the loader but document only the new names.
7. JSON/config parsing, Janino compilation (`EXIT=0`), diff/whitespace checks, and focused source
   review passed. The remaining in-game matrix must cover 0%/100% forced outcomes, normal 70%, both
   departure causes, save/reload before and after the check, and a legacy intercept-phase
   development save if one is available.

### Presentation refinement (built 2026-08-18)

1. Added saved `PHASE_ORDERED_WAIT` state so reception success announces the order before changing
   the fleet to its departure assignment and cannot repeat the message after save/reload.
2. During the configurable delay, the fleet remains on a refreshed vanilla `ORBIT_PASSIVE`
   assignment; the default `orderedDepartureDelayDays` is `0.25`.
3. A locally visible announcement plays vanilla `ui_noise_static_message_quiet` through
   `SoundPlayerAPI`; remote outcomes remain silent and unannounced.
4. After the delay, the existing vanilla jump-point selection and
   `GO_TO_LOCATION_AND_DESPAWN` departure path runs unchanged.
5. Offline verification passed: the commented configuration parsed with the new value at `0.25`,
   the manager passed Starsector's Janino compiler (`EXIT=0`), and diff/whitespace checks passed.
   The author live-tested the completed behavior and accepted it on 2026-08-19.

### Configurable multi-convoy refactor (2026-08-23)

- The single saved `active` operation migrated to an `activeOps` list without discarding an
  in-progress development-save convoy.
- `exilerefugee.maxActiveConvoys` independently caps ordinary convoy operations; default 1 preserves
  prior behavior, and 0 prevents launches without deleting operations already underway.
- Each operation owns its fleet, lifecycle, reception state, condition, and cleanup. In-flight
  destination reservation prevents concurrent convoys from selecting the same market.
- The launch clock pauses while the cap is full. Below the cap it measures the configured interval
  between launches, allowing overlap when the cap and cadence permit it.
- Direct spawning remains bounded and physically legible. Keep the cap modest; a future RouteManager
  migration is optional optimization, not a prerequisite for a tunable cap.

### Quest boundary — random jobs to remove refugees

The concept fits the morally grey refugee system, but the random mission should **not** depend on
`ExileRefugeeConvoyManager.active`. There is only one convoy globally, its relevant orbit window is
short, and it can disappear, be destroyed, or become unsafe before the player can reasonably accept
and complete a job. That would make mission availability rare and mission state brittle.

Recommended Phase-2 boundary:

- Reuse shared eligibility/reception concepts or a small helper, not the singleton operation state.
- Prefer a market/encampment-centered mission or a mission-owned fleet and lifecycle.
- Decide what "remove" means before implementation: negotiate departure, escort relocation,
  deliver relief, intimidate civilians, or destroy them have very different tone, reputation, and
  failure requirements.
- Do not let a generic bar mission silently force the Refugee faction into villain status; the
  employer's framing and the player's available methods should preserve moral ambiguity.

### Phase-2 settlement migration — accepted direction 2026-08-19

The future Refugee quest may let the player explicitly invite Refugees to settle at all genuine
player-owned colonies. The decision, not positive relation by itself, is the shared gate for three
effects: applying long-term Refugee Influx to current/future player colonies, enabling independent
migration fleets while Refugee relation remains positive, and allowing positive relation to slow
the disruptive convoy clock. Neutral or negative relation pauses new migration launches and restores
the disruptive clock to full speed; existing migration fleets finish and Refugee Influx remains.

Use two independent bounded schedulers rather than an either/or spawn roll. The disruptive clock
accumulates elapsed days at `1 - max(0, relation)` after the gate, reaching half speed at +0.50 and
pausing at +1.00. The migration scheduler gets its own randomized cadence and one-fleet cap; it
selects a live Refugee enclave and a size-weighted player colony only at dispatch. Fleets are quiet,
identifiable ambient traffic with no intel item or arrival-owned demographic effect. Full design and
the future enclave-depletion boundary are recorded in `docs/EXILES_REFUGEE_CONVOYS.md` §11.

No new runtime scaffold is warranted before the quest owns the persistent invitation flag and its
lifecycle. The dormant `ExileRefugeeInflux` condition and `ExileRefugeeIntegrationListener` already
cover the useful pre-quest infrastructure.

### Dependency and test notes

- Source: `data/scripts/exiles/ExileRefugeeConvoyManager.java`
- Condition: `data/scripts/marketconditions/ExileRefugeeEncampments.java`
- Registration/load restoration: `data/scripts/exiles/ExilesModPlugin.java`
- Tuning: `data/config/exiles/director_config.json` → `exilerefugee`
- Canon/test matrix to update: `docs/EXILES_REFUGEE_CONVOYS.md`
- Test travel, arrival, early-removal success/failure, max-orbit fallback, destruction before/after
  condition application, save/reload in every phase, hostile ownership change, and active-raid veto.

## 4. Security Centers — generic stability structure

### Status

**REFACTOR IMPLEMENTED 2026-08-20; JANINO-CLEAN, PENDING AUTHOR LIVE TEST.** Construction eligibility,
mutual exclusion, current NPC placements, and Refugee Processing Center's final Stability treatment
were deliberately left unchanged or compatible pending their separate reviews.

### Observed in game (author, 2026-08-16)

- Its value is too narrowly tied to refugee conditions.
- A generic Stability benefit would make it more useful to the player.
- Several frequently attacked NPC markets could benefit from the same industry.

### Pre-refactor implementation baseline

- `ExileSecurityCenter` previously offset Refugee Influx stability, canceled Refugee Strain's four-
  point stability penalty, gives +2 stability with Decivilized Subpopulation, and suppresses some
  population growth through `MarketImmigrationModifier`.
- It demanded Marines equal to market size and Hand Weapons equal to `max(1, size - 2)`.
- Player construction was allowed only with Refugee Influx, Refugee Strain, or Decivilized
  Subpopulation, and is mutually exclusive with Refugee Processing Center.
- `ExileRefugeeIntegrationListener` treats a functional Security Center as a control structure that
  prevents an unmanaged Refugee Influx from becoming Refugee Strain when a colony grows.
- The mod adds Security Center to five chronic-strain NPC markets through post-economy vanilla-market
  patches, and separately to Jangala. These placements and the structure's generic redesign must be
  reviewed together; they should not accidentally stack into excessive stability.

### Accepted direction

- Give the industry a useful generic **+3 Stability** role rather than condition-specific Stability
  offsets. The display name is plural **Security Centers**, representing a decentralized colony-wide
  apparatus whose personnel and weapons demands scale with colony size. Preserve the existing
  namespaced `exile_security_center` ID and `ExileSecurityCenter` class/file.
- Remove its special Refugee Influx, Refugee Strain, and Decivilized Subpopulation Stability bonuses.
- Retain population-growth suppression for Refugee Influx and Decivilized Subpopulation at full
  strength while the structure is functional. Commodity deficits do not scale these growth effects.
- Demand Marines equal to market size and Hand Weapons equal to `max(0, size - 1)`. The worst Marines
  or Hand Weapons deficit reduces the Stability bonus point-for-point through vanilla industry hooks.
- Do not add patrol generation or ground-defense strength.
- Retune Refugee Influx's Stability penalty alone to one point per 20 positive Refugee relation,
  floored and capped at **1–5** while the condition exists. Preserve its separate existing growth
  scale of twice the 10-percent relation scale (−20 to +20).
- Reduce Refugee Strain's Stability penalty from **−4 to −3**; preserve its Accessibility and buy-price
  effects.
- Keep current build times (Security 30 days; Processing 45 days). Set Processing/Security build cost
  to **60/50** and upkeep to **8/7**, preserving Processing as the more expensive program.
- Preserve Processing Center's current behavior of reducing Refugee Influx to net −1 as a compatibility
  measure; review all of its Stability bonuses separately.
- Do not add Security Centers to more NPC markets until each candidate's narrative fit and total
  Stability stack have been audited.

### Balance assessment and remaining caution (updated 2026-08-20)

The simplified identity is cleaner and makes the structure useful outside Refugee colonies. Higher
costs are also defensible: a pervasive coercive apparatus consumes personnel, weapons, detention
capacity, surveillance, administration, and political resources even if it remains cheaper than a
humane integration program. For comparison, vanilla Ground Defenses costs 30/upkeep 2 and grants +1
Stability plus ground defense; Patrol HQ costs 60/upkeep 8 and grants +1 Stability plus patrols;
Military Base costs 90/upkeep 10 and grants +2 Stability plus stronger patrol infrastructure.

The linked +3/Influx-cap/Strain changes now produce clear policy outcomes: Security Centers fully
offsets Refugee Strain, leaves maximum-trust Refugee Influx at net −2, and makes vanilla
Decivilized Subpopulation net +1 before other modifiers. This is credible as a coercive alternative
without returning to condition-specific offsets. The remaining caution is that Security Centers is
a slot-free structure. A universally available +3 option could become an automatic player purchase
and overpower vanilla Stability choices; retain the current condition gate until build availability
is deliberately reviewed, and audit the current NPC Stability stacks during live testing.

Commodity shortages reduce only the Stability bonus. This is intentionally simpler than scaling the
two population-growth suppressions, while ordinary disruption still disables all effects.

### Open design decisions

1. **Processing Center Stability:** review its Influx mitigation and its Refugee Strain and
   Decivilized Subpopulation bonuses against the revised condition values.
2. **Processing Center relationship:** remain mutually exclusive as two refugee-policy choices, or
   allow both now that Security Center is generic? Allowing both may erase the original moral choice.
3. **Build availability:** remain gated by the current conditions, become universally player-buildable,
   use a colony-size gate, or unlock through content?
4. **NPC list:** identify attacked markets, then audit their current conditions, industries,
   stability, and narrative fit before adding the structure.
5. **Tuning surface:** economy content placement is probably authored content rather than a player
   setting. Exact mechanical values may belong in a dedicated economy setting only if there is a
   meaningful player choice; `director_config.json` should remain focused on faction activity.

### Dependency and test notes

- Industry: `data/scripts/industries/ExileSecurityCenter.java`
- Humane counterpart: `data/scripts/industries/ExileRefugeeProcessingCenter.java`
- Growth listener: `data/scripts/exiles/ExileRefugeeIntegrationListener.java`
- Industry spec/text/cost/upkeep: `data/campaign/industries.csv`
- NPC placement: `data/scripts/exiles/ExilesModPlugin.java`
- Conditions: `ExileRefugeeInflux`, `ExileRefugeeStrain`, and vanilla
  `decivilized_subpop`
- Test functional/disrupted/supply-deficit states, construction availability, mutual exclusion,
  removal, colony growth, NPC-market totals, Nex ownership change, save/reload, and tooltips.

### Linked design cluster — Emergency Measures

**VERSION ONE IMPLEMENTED 2026-08-22; JANINO-CLEAN, PENDING AUTHOR LIVE TEST.** Emergency Measures is
the visible, Exiles-specific recovery layer for markets that remain at zero Stability for 60 days
after a concrete Exiles pressure event. It uses condition-local timers and narrow damage/player-action
hooks rather than a sector scan. Vanilla-only crises cannot arm it, and player raids or bombardments
cancel a dormant lead-in without affecting an already-active response.

Activation counters one Recent Unrest point, then snapshots the reserve required to reach Stability 1
with a hard +5 cap. The reserve never grows; its applied value falls by one under the worst shortage
among +1 Marines, Hand Weapons, and Supplies demand. Active duration is capped at 180 days; natural
Stability 3 for 30 days ends it early; every active removal starts a 180-day market-local cooldown.
Active markets count as Battered for Exiles directors. NPC player-buy prices rise 50%; player colonies
pay a prorated monthly-report expense of 10,000 credits per market size. No Accessibility penalty,
escalation, mission, Security Centers interaction, or global anti-decivilization immunity was added.

Canonical behavior, implementation map, exact economic semantics, and live-test matrix:
`docs/EXILES_EMERGENCY_MEASURES.md`.

## 5. Refugee Salvage Expeditions

**SUPERSEDING RULING AND BUILD — 2026-09-07:** Sean approved physical Core battlefield cleanup by
Refugee fleets, commodity/weapon delivery to Asharu/Mairaath only, no hull recovery, and offscreen
appearance/retirement. The refactor is Janino/offline-check clean and pending author live testing.
The decorative baseline and cleanup objections below are historical, not current restrictions.
Canonical contract, tuning and tests: `EXILES_REFUGEE_SALVAGE_EXPEDITIONS.md` (current contract).

### Status

**VERSION ONE LIVE-TESTED AND FUNCTIONAL 2026-08-23; CONFIGURABLE CAP REFACTOR JANINO-CLEAN,
PENDING LIVE RETEST.**

### Author concept (2026-08-16)

- Refugee salvage fleets launch from Refugee enclaves with functional Independent Salvage
  (`exile_salvage_yards`).
- They seek systems with substantial recent battle debris and ship hulks, especially systems that
  recently suffered raids.
- Determine whether they should actually clean debris/hulks or remain decorative.

### Current implementation baseline

- `ExileRefugeeSalvageExpeditionManager` owns a config-bounded set of global RouteManager expeditions
  and one fixed 30-day launch-opportunity clock. Empty or full-cap cycles skip without catch-up.
- `ExileSalvageYards` is an NPC-only economy industry. Its chronic Fuel shortage is intentional and
  must not throttle its flat output.
- Source markets are discovered dynamically by functional industry id plus a functional Spaceport or
  Megaport rather than duplicated as a hardcoded enclave list. This follows ownership, disruption,
  and future placement.
- Vanilla already provides the relevant architecture:
  `RouteManager` owns travel and despawning, while a small Exiles assignment AI provides the exact
  debris orbit, return text, and local danger breakoff.
- Candidate targets are debris-field terrain, inspected only at the 30-day launch opportunity.
  Reachable battle debris is a strict priority over other debris; Core systems remain preferred
  within both source classes, and a live Exiles raid adds weight within the battle tier. Final-
  dissipation fields are rejected and remaining lifetime contributes to candidate weight.

### Ratified version one

The first version is a **bounded, non-destructive vanilla-style expedition**:

- `salvageExpeditions.maxActiveExpeditions` independently caps active routes; default 1, with 0
  preventing new launches.
- Use `RouteManager` for source → target system → source travel and automatic despawning.
- Spawn only from live Refugee markets with a functional `exile_salvage_yards` and functional
  spaceport.
- Score candidate debris fields only at dispatch on the 30-day interval. Use reachable battle debris
  before non-battle debris, retain a Core-before-non-Core preference within both source classes,
  favor live-Exiles-raid systems within the battle tier, and apply lifetime/distance weighting. Do
  not apply a raid or combat-system safety veto.
- Use a small Refugee-faction scavenger fleet, transponder on, avoidance-oriented, with vanilla
  route travel plus an explicit fixed-duration salvage orbit at the chosen debris field.
- Do not remove or mark debris/hulks in version one. Battle debris already dissipates, while hulks
  may be player rewards, exploration content, or quest targets. Consuming them adds compatibility,
  fairness, and persistence risks for little performance benefit.
- Guarantee one or two Refugee combat-role escorts, two Shepherds and one standard Tarsus, cap the
  fleet at five ships and hulls at destroyer size, and reserve one Shepherd slot for replacement by
  the future modified Tarsus salvager. Use vanilla commander setup for a marked flagship while
  capping subordinate officers at zero.
- Route to a stable last-known-site token rather than expiring terrain. If the chosen field vanishes,
  continue into the system, retarget another local field when available, or survey the original site.
  Never mutate or extend the vanilla debris field to preserve the trip.
- Do not avoid raid systems at launch. If credible hostile danger becomes locally visible while the
  fleet is outbound or salvaging, break off toward home without making the fleet invulnerable.
- No launch/completion intel, donations, custom dialog, rewards, cargo accounting, or custom
  relationship effects in version one.
- The existing faction-root `exilerefugee.debugMessages` testing toggle also announces salvage
  expedition dispatches; no fleet-type-specific debug knob is added.

This is "decorative" only in its effect on salvage objects; it still makes the Refugee economy
visible, creates encounterable traffic, and can react to actual sector damage.

### Push-back on physical cleanup

Actual cleanup is technically possible but is not advisable as the default:

- Removing salvage competes with the player for rewards without a clear counterplay or notice.
- Generic `Tags.SALVAGEABLE` entities include more than disposable battle remains and may be used by
  vanilla missions, exploration offers, or other mods.
- Battle debris already expires and therefore does not need cleanup for performance.
- Safe cleanup requires source classification, mission/importance checks, ownership rules,
  player-presence policy, partial value consumption, and robust save/load bookkeeping. That is a
  substantial system for a subtle visual payoff.

If later playtesting shows that expeditions need tangible effects, consider a narrowly scoped second
pass limited to old `DebrisFieldSource.BATTLE` fields, with explicit exclusions and player-facing
signaling. Do not despawn generic hulks.

### Deferred beyond version one

1. Decide whether later quests add donations, escort, salvage-rights, rescue, or recovered-material
   interactions; none exists now.
2. Consider a future modified Tarsus salvager only as a separate ship-content decision.
3. Tune `maxActiveExpeditions` above its default only after long-campaign performance testing.
4. Physical salvage consumption remains rejected unless a later design supplies fair player-facing
   counterplay and robust exclusions.

### Dependency and test notes

- Source industry: `data/scripts/industries/ExileSalvageYards.java`
- Economy placements: `data/campaign/econ/*.json`
- Bounded manager and route assignment under `data/scripts/exiles/`
- Registration/load restoration: `data/scripts/exiles/ExilesModPlugin.java`
- Tuning: `exilerefugee.salvageExpeditions` block in
  `data/config/exiles/director_config.json`
- Vanilla references: `RuinsFleetRouteManager`, `ScavengerFleetAssignmentAI`, `RouteManager`,
  `DebrisFieldTerrainPlugin`
- Test no eligible source/target, disrupted yard/spaceport, route spawn/despawn, source ownership
  change, target safety change, fleet destruction, save/reload in every segment, one-route cap,
  no salvage mutation, and long-campaign route cleanup.
- Canonical implementation contract and focused live-test matrix:
  `docs/EXILES_REFUGEE_SALVAGE_EXPEDITIONS.md`.

## 6. Author playtest record

Add dated results here after each implementation. Record intended experience, observed experience,
unexpected cross-system effects, performance/save behavior, and the ruling for the next pass.
