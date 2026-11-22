# Heg–League Pass 8 — Player Interception: findings and plan

> **STATUS 2026-11-22: THIRD RUN, ON RC8 (§16). SIX STOPS, FIVE OUTCOMES, NO RULE ERRORS.**
> Comply, Pay, Pay in supplies, Lie (success) and Refuse-when-stronger have all run in play.
> Untested: Negotiate, a caught lie, the fight after refusing when weaker, the three reserved
> options, the official voice and any cordon stop. The objective keep-out engaged three times
> and the first watched interdiction completed its window, but the run does not yet prove the
> keep-out is why. Two logging faults fixed. Raider prose is still placeholder.
>
> **Earlier, 2026-11-18 (late): OBJECTIVE KEEP-OUT BUILT, NOT RUN (§15.1).** Raiders were dying
> under stations because vanilla's war simulation orders them onto enemy relays and arrays
> (§15). By Sean's ruling they still take objectives, except one currently within
> `interdiction.objectiveStationKeepOut` (3000, a guess) of an enemy station. Interdiction only.
>
> **Earlier the same day: ONE STOP FIRED AND WORKED END TO END (§14).** A League raider in Valhalla
> was sent after the player by vanilla, finished its fight, hailed, and the Lie option succeeded.
> Only that one option has been exercised. Raiders are confirmed to fight and die beside the
> focus market (Tyle), and all four watched interdictions to date failed on losses; not tuned.
> One Tyle case is not fully explained (§14). Next: the rest of the option table, a cordon stop,
> and Sean's rulings on the open questions.
>
> **Earlier, 2026-11-17: PASS 8a-1 RUN ONCE; NO STOP FIRED, CAUSE NOT YET KNOWN (§13).** One watched
> League interdiction at Arcadia was marked, flew past the player onto the Citadel and was
> destroyed in four days. A `STOP DIAG` test measurement is built to find out why (off by
> default; not yet run). The same run showed interdictions only succeed unwatched (§13). Next:
> one watched interdiction or squeeze with `testing.stopDiagnosticsDays` 0.5.
>
> **Earlier, 2026-11-16 (evening): PASS 8a-1 BUILT, NOT YET RUN IN GAME.** Raider and cordon stops
> are in; build record §12, test checklist §11.7. Janino clean. 8a-2 (blockade stop) and 8b are
> not built.
>
> **Earlier the same day: PLANNING. The option table is RULED (§1.1a)**; the vanilla
> seam was re-checked on 0.98.5a-RC7 (§4.1). The defaults and the blockade build order are ACCEPTED (§7).
> Every agenda item of §1.2 is settled by §1.1a and §7. Next: the 8a build plan (§11), for Sean's
> approval before any code.
>
> **Earlier status, 2026-11-09 (session end): PLANNING. NOTHING BUILT.** Read-only review of the Exiles
> code, Sean's design document, and the vanilla sources. Sean answered Q1–Q4 the same evening
> (§1.1). **Next session: work through the agenda in §1.2 (the option table needs more discussion),
> then write the 8a build plan.** The six defaults in §7 have not been ruled individually; D4 is
> partly superseded by §1.1. Parent spec: `EXILES_HEG_LEAGUE_WAR.md` §4.5 (R4, S9).
>
> Tags used below: **[VERIFIED]** = read in source this session, with the file named.
> **[TO VERIFY]** = believed, not yet checked or needs a live test. **[ASSISTANT]** = originated by
> the assistant, not accepted canon until Sean rules.

---

## 1. Rulings so far (Sean, 2026-11-09)

1. **Pass 8 is split into two seams.**
   - **8a — the fleet stops the player.** Vanilla's hassle seam plus `rules.csv`, vanilla rule
     commands only.
   - **8b — docking interception at a squeezed market.** Custom `InteractionDialogPlugin` through
     `pickInteractionDialogPlugin`. Decided separately; may be deferred or cut.
2. **Dialogue prose is assistant-written PLACEHOLDER, clearly marked**, for Sean to revise later.
   Every rule row carries a `PLACEHOLDER` note in the `notes` column.

Carried over, unchanged: R4 (v1, final ops pass, own kill switch, shippable without it), S9, and the
Ashen Ruling F mitigations (live check every time, hard expiry, once-per-dock rep guard, kill switch).

### 1.1 Sean's answers to Q1–Q4 (2026-11-09, evening)

Sean called the option table "an excellent start" that "will need more discussion". What is firm
and what is still open is marked on each line.

- **Q1 — RULED: system-blockade pickets stop the player.** Sean also wants the blockade's
  jump-point behaviour reviewed and refactored: he recalls the fleets arriving at one jump point and
  only sometimes moving to a second. This is the open item **R2** in
  `EXILES_HEG_LEAGUE_WAR_RECOMMENDATIONS.md` ("Blockade gates left empty", decision PENDING), which
  already holds the diagnosis: every gate's response script asks for all of the faction's
  in-system fleet points, so fleets shuttle between gates. Its option (a) pins each picket to a gate.
  Timing is the assistant's recommendation, §1.2 item 1.
- **Q2 — the option table, line by line:**
  - **The intercepting fleet's goal comes first** and decides the dialogue. Sean's lean: turning
    ships out of the system entirely would be hard to program; in a limited war the simpler goal is
    to **slow, disrupt and harass trade**. Sean's framing idea: treat the lower tension levels as an
    **active Cold War**. PROPOSED by Sean, not yet ruled as canon wording.
  - **Pay (bribe): BUILD IT, may be removed later.** Sean finds a paid way around a fight more
    interesting than none. His lore doubt: would every Hegemony and League crew, at war with each
    other, let any ship through for cash? Any fleet that takes a bribe must always take one; a fleet
    that sometimes does reads wrong. Options to be discussed next session. Sean also wants
    **non-cash bribes** considered.
  - **"Official business" is RESERVED** for a real status granted by a quest. Sean's further idea:
    a generic bar mission that sells time-limited **authorization codes** unlocking a free-pass
    option. The reputation-based waiver in the draft table therefore needs a different name.
  - **Reputation-based differences across Pay / Negotiate / free pass: LIKED**; the specifics may
    change.
  - **Lie: RULED IN, as a probability check.** Sean would like reputation to move the odds, with the
    caveat that it may only sound good until built.
  - **Refuse: AGREED, with a guard.** Attacking a faction the player is not already hostile to
    drops the relation to Hostile at once. Sean wants that side-stepped, or at least a warning and a
    chance to back out.
  - **Cut the link: RULED.** It returns the player to the vanilla encounter options (open comms,
    move to engage, attempt to disengage). It must not start a fight by itself.
- **Q3 — RULED: reputation costs are at least the proposed figures** — 2 or more for a caught lie,
  1 or more for facing an officer down.
- **Q4 — RULED: build 8a first**, measure landings that slip past the cordon, then decide 8b.

### 1.1a Rulings of 2026-11-16 (Sean: "Yes, all three")

Recommended by the assistant, accepted by Sean as the working design for 8a. Evidence in §4.1.

- **The fleet's goal is to harass and slow trade, for all three op types.** **Comply** is the
  default option and costs what vanilla's League enforcer costs: combat readiness, no money.
  **Paying buys speed** (no readiness loss), not passage. Blockades and cordons differ from raiders
  in voice and in how heavy the inspection is, not in mechanics. This settles agenda item 2.
- **Why a crew takes money: split by op type, no per-fleet corruption roll.** Interdiction raiders
  run a **shakedown** (the inspection is the pretext; this is the design document's "corrupt
  officials would extort the player"). Cordons and system blockades charge an **official fee** (a
  Hegemony "inspection fee", a League "transit licence"). Every fleet of a kind therefore always
  takes the money, which was Sean's condition. This settles agenda item 3.
- **Walking away costs what vanilla makes it cost, nothing more in v1.** A stronger stopping fleet
  keeps prevent-disengage, so the player must attempt to disengage; a weaker one backs down and the
  player leaves. No extra pursuit or reputation mechanism. This settles agenda item 7.
  **[TO VERIFY, live]** whether escaping from a non-hostile fleet costs reputation.

**The option table, ruled the same day (Sean: A, B, C agreed; D amended).** This replaces the
§5.1 table.

| Option | Shown when | Result |
|---|---|---|
| **Comply** | Always | Readiness loss as in vanilla; free to go. |
| **Pay** | Always; greyed out if unaffordable | Toll paid, no readiness loss. Raiders: shakedown. Cordons and blockades: official fee. |
| **Pay in supplies** | Raiders only | Supplies instead of credits (the Knights' tithe is the vanilla model). |
| **Standing waiver** | **The actor's commission only** (Sean) | Free to go. |
| **Negotiate** | Reputation WELCOMING or better | Toll halved. |
| **Lie** | Once per stop | Roll: 50% at neutral standing, shifted by reputation, clamped 20%–80%, all config ("a good starting point" — Sean). Success: free to go. Caught: reputation −2, Lie and Negotiate close, toll doubles, Comply stays. No story-point lie. |
| **Refuse**, fleet stronger | `$relativeStrength >= 0` | Fails, as in vanilla. A fight starts only if the player chooses it after cutting the link. |
| **Refuse**, player stronger | `$relativeStrength < 0` | The officer backs down; reputation −1 or more. |
| **Cut the link** | Always | Vanilla encounter options. |
| **Official business** | A quest sets the flag | Free to go. **A quest lever (Sean).** Nothing sets it in v1. |
| **Authorization codes** | The codes flag is live for the actor's faction | Free to go. See below. |

- **A — the fight after a refusal.** Raiders get `$lowRepImpact` once the player has refused (an
  unsanctioned shakedown: 3 to 5 reputation per fight, no forced hostility). Cordons and blockades
  keep full consequences; vanilla's "are you sure?" prompt is the warning. Accepted risk: raiders
  become a cheap source of the victim's bounty until the losses add up to Hostile.
- **D — the standing waiver is commission-only** (Sean): "It gives the player a lot of leeway, but
  they'll have earned it." A commission with one side means hostility with the other, so the waiver
  only ever works on one side's fleets. FRIENDLY standing alone no longer waves the player through.
  This supersedes default D4's first clause.
- **Authorization codes (Sean, 2026-11-16).** A random bar quest sells codes good for 30 days with
  one faction; while they are live the player can talk their way out of any stop by that faction's
  fleets. Sean left the mechanism to whatever is easiest. **[ASSISTANT, recommended]** a second
  expiring flag beside the Official Business one, read by its own option row, so that codes
  running out cannot erase a quest-granted status and the two can carry different text. **8a builds
  the option row and the flag read only.** The bar quest, its price, and everything else the codes
  may unlock are a separate design, not part of Pass 8 (Sean: "the Auth Code idea goes beyond just
  these encounters").

**Starting relations (Sean's reminder, 2026-11-16).** An Exile-start player begins HOSTILE with
the League and VENGEFUL with the Hegemony. `HasslePlayerScript` skips hostile fleets, so **such a
player gets no stop dialogue at all** until a relation is repaired to INHOSPITABLE or better; until
then the war fleets simply attack. The dialogue is therefore something the player grows into:
shot at, then stopped and squeezed, then able to negotiate, then waved through on a commission.
Test consequence: an Exile-start run needs a console reputation change, or a standard start, to
see a stop.

### 1.2 Agenda for next session **[ASSISTANT — recommendations, nothing ruled]**

1. **Blockade gate behaviour (R2) before the blockade stop.** A picket can only stop the player at
   a gate it is actually holding, and both the player-hassle script and `NPCHassler` mark a fleet
   busy, which R2 lists as one cause of empty gates. Recommended order: first a measurement line
   (which gate each blockade fleet is nearest to, once per day of payload), then rule R2, then the
   blockade stop. Interdiction and squeeze stops do not depend on it and can be built first.
2. **The fleet's goal, and what "comply" costs.** If the goal is harassment, the default outcome can
   be the vanilla one: submit to the inspection and lose time and combat readiness, no money.
   Paying then buys speed, not passage, which may also ease the lore doubt in item 3.
3. **Why would a crew take money?** Three candidate answers:
   - (a) It is not a bribe but an official levy (a Hegemony "inspection fee", a League "transit
     licence"), so every crew charging it is policy.
   - (b) Corruption is a per-fleet trait rolled once when the fleet spawns and kept in its memory,
     so a given fleet is always or never for sale. This is cheap to build and meets Sean's "always
     willing" condition.
   - (c) Both: the levy is always there, and a corrupt officer offers a cheaper private deal.
4. **Non-cash bribes.** `AddRemoveCommodity` handles any commodity, and vanilla's Knights tithe
   takes supplies. Candidates: supplies, fuel, or contraband the officer keeps.
5. **Naming the reputation waiver** now that "official business" is reserved, and the seam for the
   reserved option: a faction-scoped, expiring memory flag that a quest or the bar mission sets and
   the rule reads. Nothing sets it in v1.
6. **The Refuse guard.** Vanilla ships `MakeOtherFleetLowRepImpact` and
   `MakeOtherFleetNoRepImpact` as rule commands **[VERIFIED — files exist; behaviour not yet read]**.
   Proposed: a confirm step that states the consequence, then a reduced-impact fight.
7. **What walking away costs.** With "cut the link" returning to the normal options against a
   non-hostile fleet, the player can simply leave. Unless something follows (renewed pursuit, a
   reputation cost for ignoring the order), every other option is optional. This needs a ruling.
8. **Lie odds from reputation.** `RollProbability` takes a memory variable **[VERIFIED —
   `params.get(0).getFloat(memoryMap)`]**, so Java can write a reputation-adjusted chance onto the
   fleet. Its seed comes from the fleet and the rule id, so the result is fixed per fleet per rule:
   reloading does not reroll it.

---

## 2. What Sean's design document asks for

Source: `docs/Exiles Design Documentation/Hegemony and Persean League war.docx` (file dated
2026-08-26), the "starting guidance" list. Two sentences cover the player:

- Under **interdiction raids**: "Also stops and inspects other fleets, including Player fleets,
  corrupt officials would extort the player."
- Under **planetary blockades**: "These would also intercept the Player if they tried to land. The
  player would have to fight, negotiate, lie or possibly bribe their way past the blockade. (May or
  may not be something we can program)."

What follows from the text:

- The inspector is the **interdiction raider**. The document does not ask for home-side customs
  patrols, and none exist in the code.
- The landing interception belongs to the **planetary blockade**, which the code calls the squeeze.
- The document says **nothing about system blockades stopping the player**. The war doc §4.5
  extended blockade-running to "§4.2/§4.4"; that extension is **[ASSISTANT]**, from the 2026-08-26
  spec session, and was never separately ruled. See question Q1 in §8.

---

## 3. What the code does today **[VERIFIED]**

| Op | State | Fleets | Where they sit | Hostile to a neutral player? |
|---|---|---|---|---|
| Interdiction (`ExileHegLeagueRaidIntel` + `ExileHegLeagueInterdictionStage`) | TENSION (cadence 90 d) and ESCALATION (×0.35) | 120–200 FP, destroyer cap | System centre for 30 d (`getLoiterToken()`, `loiterOnFocusMarket` false) | No |
| System blockade (`ExileHegLeagueBlockade`) | ESCALATION only, four whitelisted systems, at most 2 at once | Sizes [6, 4, 4], uncapped hulls; one "Blockade Fleet" anchor, the rest "Blockade Picket" with `NPCHassler` | Every jump point for 45 d | No (`playerTargeted=false`, `makeFleetsHostile=false`, both load-bearing) |
| Squeeze (`ExileHegLeagueSqueezeIntel` + `ExileHegLeagueSqueezeStage`) | RETALIATION | 250–400 FP, cruiser cap | Orbit of the one anchor market for 40 d; fights that market's station | No |

Other facts that bear on Pass 8:

- **No interception code exists.** No `InteractionDialogPlugin`, no `ColonyInteractionListener`, no
  `playerInterception` config block, no Exiles rule rows for fleets. The config header reserves the
  block name only.
- **Who does what is already asymmetric** (`director_config.json`, `opWeights`): League
  interdiction 3 / squeeze 3 / blockade 1; Hegemony interdiction 1 / squeeze 1 / blockade 3. In
  practice the player meets **League raiders and cordons** and **Hegemony blockades**.
- **Fighting an op fleet already feeds the war.** `reportBattleOccurred` on every op fleet pays the
  victim's bounty (3,000c per frigate, scaled), credits war weariness to the side whose ships died,
  and fills the victory-bonus ledger. Vanilla handles the reputation loss. A "refuse and fight"
  branch needs no new war plumbing.
- **Each op already has a short tick that walks its live fleets**: the interdiction and squeeze
  stages in `updateStatus()` → `suppressStationTargeting()`; the blockade in `periodicUpdate()`
  (vanilla's own 0.1–0.3 day interval). A flag refresh can ride these with no new script.
- **Withdrawal hooks exist**: `ExileReturnLeg` for the raid family, `giveReturnAssignments()` for
  the blockade.
- **Raid-family fleets carry `$cfai_makeHoldVsStronger`**, so against a stronger player they hold
  rather than flee. Vanilla then sets `$relativeStrength = -1` and `$weakerThanPlayerButHolding`
  (`CoreCampaignPluginImpl`), which is what a "you are in no position to demand anything" branch
  reads.
- **Relief convoys** carry `$exiles_hl_relief_convoy`, reserved for Pass 8 in a Pass 6 comment. The
  Black Fleet ambush reads it. Nothing player-facing does.
- **No Exiles code reads the player's commission.**
- The `violent_inspection` incident is text only. It skews the Hegemony as offender because
  "stop-and-search is the Hegemony's institutional habit" — an **[ASSISTANT]** placeholder
  characterisation from Pass 3, not canon.

---

## 4. The vanilla seam for 8a **[VERIFIED]**

Sources: `starfarer.api.zip` and `starsector-core/data/campaign/rules.csv`.

- **The trigger is a global vanilla script.** `HasslePlayerScript`, registered by
  `CoreLifecyclePluginImpl` (line 790). Every 0.1–0.3 days it looks within 2,000 units of the
  player for a fleet that is not hostile, not a station, can see the player, is not fleeing or
  already in an interaction, and carries `$willHasslePlayer` plus a non-empty `$hassleType`. It
  takes the closest, rolls 25%, and on success sets `$pursuePlayer` for 10–15 days.
- **Built-in rate limit.** A sector-wide `$hassleTimeout_<type>` is set for 20–30 days when a
  pursuit starts, whether or not it catches the player. One fleet pursues at a time, shared with
  vanilla's and Nexerelin's own hasslers.
- **The dialogue is `rules.csv`.** A `BeginFleetEncounter` rule matched on `$hassleType` sets
  `$hassleComplete`, calls `MakeOtherFleetPreventDisengage` and `HailPlayer`; an `OpenCommLink` rule
  shows the options. Worked examples: the League enforcer (rules 35076–35135: comply with CR
  damage, refuse, "with that fleet?" when the player is stronger) and the Hegemony investigator
  (36605 on).
- **The live check is free.** `PerseanLeagueBlockade` (line 260) writes both flags with a **2-day
  expiry** and re-writes them on its periodic tick. A flag cannot outlive the fleet or the
  operation by more than two days. Ruling F mitigation 1 is met by construction.
- **Every command the dialogue needs is vanilla**, so no JAR:
  - `AddRemoveCommodity credits -$entity.<var>` takes a memory variable (rule 2277, the Pather
    tithe). **Toll amounts can therefore live in `director_config.json`**; Java writes them into
    fleet memory beside the flags.
  - `DoCanAffordCheck`, `RollProbability`, `RepIsAtWorst`, `Commission hasFactionCommission`,
    `AdjustRep`, `MakeOtherFleetHostile`, `MakeOtherFleetPreventDisengage`, `ApplyCRDamage`.
- **Toll precedent:** `LPTitheCalc` charges player fleet points × 200.
- **Nexerelin uses the same seam** for its own "nex_police" hassle (Nex `rules.csv` 5162), so the
  mechanism is known to work under Nex. Our types are `exile_hl_*`; nothing collides.
- **It never touches market interaction.** 8a cannot lock a player out of a market.

### 4.1 Re-checked on 0.98.5a-RC7 (2026-11-16) **[VERIFIED]**

Everything above was read from 0.98a sources. Checked again in the 0.98.5a install:

- `HasslePlayerScript.java` is **byte-identical** to 0.98a. `rules.csv` still has the same three
  hassle types (`leagueEnforcer`, `hegemonyInvestigator`, `sacredProtectors`).
- **What "cut the link" costs, from `FleetInteractionDialogPluginImpl.fleetWantsToFight`:** a
  non-hostile fleet carrying `$cfai_makePreventDisengage` counts as wanting to fight whenever its
  AI would pick ENGAGE. The player then gets "Attempt to disengage" instead of "Leave". So walking
  away from a stronger stopping fleet means an escape battle; no rule has to start it. When the
  player is stronger, vanilla's rules clear the flag (`plEnforcerCommsWeaker`) and "Leave" returns.
- **Vanilla's enforcer does not turn hostile on a refusal.** "That's nonsense..." simply fails
  (`$failedToRefuse`, option removed). The remaining choices are comply or "Cut the comm link and
  power up weapons".
- **Vanilla already warns before the player attacks a non-hostile fleet** (same file, the ENGAGE
  option): "...not currently hostile, and you have been positively identified. Are you sure...?"
  with "Never mind". The warning is skipped when the fleet carries `$lowRepImpact` or
  `$noRepImpact`.
- **Reputation after a battle** (`FleetEncounterContext.adjustPlayerReputation`,
  `CoreReputationPlugin`): nothing moves unless the player was aggressive
  (`playerDidSeriousDamage || !playerOnlyRetreated`), so **an escape that only retreats costs no
  reputation** (source read; still worth one live check). A normal fight against a faction that
  knows the player is `COMBAT_NORMAL`: minus 3 and **forced to Hostile at best**; chasing down and
  winning is `COMBAT_AGGRESSIVE`, minus 5, also forced Hostile.
- **`$lowRepImpact` on the fleet** switches both to the transponder-off variants: the same minus 3
  or minus 5 with **no forced hostility**, no alarm spread to nearby fleets (`TOffAlarm` is not
  added), and vanilla's fleet description tells the player so ("...will not cause immediate
  hostilities... though it will slightly strain the relationship"). **`$noRepImpact`** removes the
  change entirely. Rule commands: `MakeOtherFleetLowRepImpact <reason> <true|false> [expiry]`.
- **Complying in vanilla costs combat readiness, not money:** `ApplyCRDamage $entity.fleetPoints
  0.2 1`, then `$player.inspectedByLeagueEnforcer = true 60` (which only unlocks a line of
  dialogue; the officer inspects anyway).
- **Vanilla's lie is a story-point option that always works** (`SetStoryOption LCP_notEnoughLie`,
  the Knights' supply tithe). The Knights' demand is also the non-cash precedent: supplies.
- **Commission check syntax** (was open in §9): `$player.fcm_faction == <faction id>`.

---

## 5. Proposed shape **[ASSISTANT — recommended, not ruled]**

### 5.1 Pass 8a — the stop

- **Flags.** Each op's existing tick writes `$willHasslePlayer`, `$hassleType` and the toll figures
  onto its live fleets with a 2-day expiry, only while the op is in its action window and the kill
  switch is on. Nothing is written on the return leg.
- **Three hassle types**, one per op kind: `exile_hl_inspect` (interdiction),
  `exile_hl_blockade_stop` (system blockade, if Q1 rules it in), `exile_hl_cordon_stop` (squeeze).
  One rule set serves both factions through `$faction` tokens; faction-specific voice is a second
  rule row conditioned on `$faction.id`.
- **Kill switch.** `playerInterception.enabled` in config plus `$exiles_hl_interception_enabled` in
  sector memory (the sibling convention). Off means the refresh stops and the flags lapse within
  two days.
- **Measurement.** Rules write the option taken into fleet memory
  (`$exiles_hl_stopOutcome`); the same tick logs it once: `Exiles Heg-League STOP: <op> <system>
  <actor> outcome=<paid|refused|bluffed|caught|waved|backed_down> toll=<n>`. Rows go into
  `tools/exiles-log-report.ps1`.
- **Footprint.** No new script, listener, intel or saved class. Roughly 100 lines of Java across
  three existing ticks and the config reader, plus 40–60 rule rows. Works on an existing save; a new
  game is still the test convention.

Proposed option table for the stop (numbers are guesses, every one a config key):

| Option | Shown when | Result |
|---|---|---|
| **Pay** ("bribe") | Always; greyed out if unaffordable | Lose the toll; free to go. |
| **Official business** | Reputation with the actor FRIENDLY or better (the J3 door, > +49), or the player holds the actor's commission | Free to go, no cost. |
| **Negotiate** | Reputation WELCOMING or better | Toll halved. |
| **Lie** | Once per stop | `RollProbability`: success = free to go; failure = toll doubled, pay or fight. |
| **Refuse**, fleet is stronger | `$relativeStrength >= 0` | The fleet turns hostile and prevents disengage. Vanilla reputation loss; the victim's bounty and the weariness ledger apply as they do today. |
| **Refuse**, player is stronger | `$relativeStrength < 0` | The officer backs down. Small reputation loss with the actor. |
| **Cut the link** | Always | Same as refuse. |

A player already hostile to the actor gets no dialogue: `HasslePlayerScript` skips hostile fleets
and vanilla combat takes over.

### 5.2 Pass 8b — the landing interception (deferred, decide on evidence)

The squeeze cordon orbits the anchor market, so a player flying in to land is inside the 2,000-unit
hassle range. **8a therefore already intercepts most landings at a squeezed market.** What 8a
cannot do is guarantee it: a fast approach can reach the dock before the roll and the pursuit land.

8b would add the guarantee and the passive trade penalty:

- A transient `CampaignPlugin` (the `ExileOmicronCampaignPlugin` registration pattern) whose
  `pickInteractionDialogPlugin` returns the custom dialogue only when every live check passes:
  kill switch on; the entity is a market's primary entity; a live squeeze intel names that market
  as its anchor and its stage is running with the condition applied; a live cordon fleet is in
  orbit and not hostile to the player; no skip flag. Any failed check, and any exception, falls
  through to vanilla docking.
- The trade penalty through `ColonyInteractionListener.reportPlayerMarketTransaction`, with the
  once-per-dock guard.
- This is the piece with the lock-out risk and the larger test burden.

**Recommendation:** build and test 8a on all three ops first. Add one log line when the player
docks at a squeezed market without having been stopped. Decide 8b from that count.

---

## 6. The twelve planning questions, answered where the review can

| # | Question | Answer | Basis |
|---|---|---|---|
| 1 | Which encounters are in v1? | Interdiction raiders and the squeeze cordon. System-blockade pickets are open. | Sean's design doc; see Q1 |
| 2 | Who inspects in TENSION? | The interdiction raider, in the raided side's space. No customs patrols. | Sean's design doc; no such fleets in code |
| 3 | What does paying buy? | Passage for this stop. Vanilla's 20–30 day cooldown per type then keeps that kind of fleet off the player sector-wide, so no pass flag is needed. | `HasslePlayerScript` |
| 4 | Toll or cargo? | Open; default is toll only. | §7 D2 |
| 5 | Asymmetry? | Already present in who the player meets (League raiders and cordons, Hegemony blockades). Default: the same mechanics for both, different voice. | `opWeights`; §7 D3 |
| 6 | +49 door and commission? | Door applies to both sides (J3 is ratified). Commission with the actor is waved through; commission with the victim means hostility, so no dialogue. | J3; vanilla commands exist; §7 D4 |
| 7 | Once-per-dock trade penalty? | Belongs to 8b only. Open with 8b. | §5.2 |
| 8 | Does the choice feed the war? | No new input. A fight already pays bounty, moves weariness and counts toward the victory bonus. | `ExileHegLeagueRaidIntel.reportBattleOccurred` |
| 9 | Relief-convoy dialogue? | Default: out of scope. The flag stays reserved. | §7 D6 |
| 10 | Who writes the prose? | Assistant placeholders, marked. | Ruled, §1 |
| 11 | Messaging pass §3.4.1 tonight? | Needs a joint review first. | Sean, 2026-11-09 |
| 12 | Correct the stale CLAUDE.md line? | Done 2026-11-09. | — |

---

## 7. Defaults the build will use unless Sean objects **[ASSISTANT]**

> **ACCEPTED 2026-11-16 (Sean): D1, D2, D3, D5, D6 as written; D4 as amended.** Supplies in kind
> are priced to match the D5 toll. Also accepted: the build order of §1.2 item 1 (interdiction and
> squeeze stops first; for blockades a gate measurement line, then the R2 ruling, then the stop).

- **D1 — No pass flag.** Rely on vanilla's 20–30 day cooldown per hassle type.
- **D2 — Toll only, no cargo inspection.** Vanilla has a `CargoScan` command, but seizing cargo is a
  second mechanism with its own balance and test surface.
- **D3 — Same mechanics for both sides; the difference is voice.** Hegemony officers levy an
  "inspection fee" by the book; League officers sell a "transit licence". One per-side toll
  multiplier in config, both shipped at 1.0.
- **D4 — Standing:** ~~FRIENDLY or~~ the actor's commission waves the player through
  (**commission only, ruled 2026-11-16, §1.1a**); WELCOMING halves the toll; hostile gets combat
  with no dialogue.
- **D5 — Toll size:** player fleet points × 200 (the vanilla Pather tithe), with a floor and a cap
  in config. A 100-FP fleet pays 20,000c.
- **D6 — No player dialogue with relief convoys.** Attacking one remains an ordinary fight.

---

## 8. Questions that remain for Sean

> **ANSWERED 2026-11-09 — see §1.1.** The original wording is kept below for the record.

- **Q1 — Do system-blockade pickets stop the player?** The design document gives interception to
  interdiction raiders and planetary blockades only. Vanilla's own League blockade does stop the
  player, and the cost here is one more flag write and one more rule set. Recommendation: yes, with
  a "this system is closed to traffic" voice and the same option table. A cost to weigh: blockade
  fleets are uncapped, so the player will usually be the weaker party and will pay or turn back.
- **Q2 — Is the option table in §5.1 right?** The design document names four ways past a
  blockade: fight, negotiate, lie, bribe. The table maps bribe to Pay, negotiate to a
  reputation-gated discount, and adds the J3 door. Is that the intended meaning of "negotiate"?
- **Q3 — Does anything change reputation besides fighting?** Proposed: paying changes nothing; a
  caught lie costs 2 with the actor; facing an officer down costs 1. All three could be zero.
- **Q4 — 8b: build 8a first and decide from the measurement, or commit to 8b now?**
  Recommendation: 8a first.

---

## 9. Not yet verified

- **[TO VERIFY, live]** That a raid-pipeline fleet (route-managed, `RaidIntel`) pursues the player
  on `$pursuePlayer` and returns to its loiter afterwards. Vanilla proves the seam on
  `BlockadeFGI` fleets and mission fleets, not on raid routes.
- **[TO VERIFY, live]** How often interdiction raiders actually reach the player. They loiter at
  the system centre and the hassle range is 2,000 units, so a player who stays on the rim may never
  be stopped. The `STOP` log line measures it; `loiterOnFocusMarket` is the existing lever.
- **[TO VERIFY, live]** The squeeze cordon while it is fighting the anchor's station: a fleet in a
  battle is skipped by the hassle script, so stops may only begin once the station is down.
- ~~**[TO VERIFY, source]** The exact rule syntax for a commission check against a named faction
  and for `RepIsAtWorst` with `$faction.id`.~~ **Read 2026-11-16:** `$player.fcm_faction ==
  luddic_church` and `RepIsAtBest $faction.id INHOSPITABLE` are vanilla's own forms (§4.1, §11.4).
- **[TO VERIFY, live]** One encounter under Nexerelin, to confirm Nex's fleet dialogue fires
  `BeginFleetEncounter` for our type as it does for its own.
- **A test pin is needed** to launch an interdiction on demand; the existing `testing` block has
  none (`forceState` covers blockades and squeezes).

---

## 10. Corrections made or needed in other documents

- `CLAUDE.md` §6 said Pass 8 was "built and tested in isolation". Corrected 2026-11-09.
- `EXILES_HEG_LEAGUE_WAR.md` §4.5 describes one seam (the docking plugin) for every encounter and
  its checklist says "approach a raider → the dialogue" without a trigger. Superseded by this
  document's 8a/8b split; a pointer was added under the §4.5 checklist heading. The §4.5 text
  itself is left as written until the rulings in §8 land.
- `EXILES_HEG_LEAGUE_WAR.md` §3.2.2 says TENSION has "picket inspections". There are no pickets in
  TENSION; the inspectors are the interdiction raiders. Wording fix pending with the Pass 8 build
  record.

---

## 11. Pass 8a build plan **[ASSISTANT — for Sean's approval; nothing built]**

Written 2026-11-16 from the rulings in §1.1a and §7 and the source checks in §4 and §4.1. Numbers
are proposed defaults, every one a config key; balance is Sean's call.

### 11.1 Two steps

- **8a-1: interdiction raiders and the squeeze cordon.** Everything below.
- **8a-2: system-blockade pickets.** After 8a-1 is tested: a gate measurement line, Sean's ruling
  on R2, then one more caller of the same helper and one more opening line. Not planned in detail
  here.

### 11.2 How a stop happens (all vanilla, §4)

1. While an op is in its action window, its stage tick marks each live fleet: "will stop the
   player", a stop type, and the figures the dialogue needs. Every mark expires after 2 days unless
   the tick renews it.
2. Vanilla's `HasslePlayerScript` does the rest. Within 2,000 units, if the fleet is not hostile,
   can see the player and is not already busy, it rolls 25% every 0.1 to 0.3 days. On a success
   the fleet pursues the player for 10 to 15 days, and that stop type goes on a sector-wide
   cooldown of 20 to 30 days.
3. When the fleet catches the player, our `rules.csv` rows open the dialogue.

Each stop type has its own cooldown, so a raider stop does not use up the cordon's.

### 11.3 Java (about 150 lines, no new script, listener, intel or saved field)

| File | Change |
|---|---|
| `ExileHegLeaguePlayerStop.java` (new, static helper, no state) | `refresh(fleets, stopType, actorFactionId, config)` writes the marks and figures; `collectOutcome(fleet)` reads the outcome the rules wrote, logs one `STOP` line and clears it. A helper because two callers (three with the blockade) would otherwise hold identical code. |
| `ExileHegLeagueInterdictionStage.java` | In `updateStatus()`, while `ONGOING`: call the helper for the op's live fleets. New hook `getStopType()` returns `exile_hl_inspect`. The tick already runs on vanilla's status interval, not per frame. |
| `ExileHegLeagueSqueezeStage.java` | Override `getStopType()` to return `exile_hl_cordon_stop`. |
| `ExileHegLeagueConfig.java` | Read the new `playerInterception` block (§11.5). |
| `ExileHegLeagueInterdictionManager.java` | Test pin `testing.interdictionNow`: the next launch attempt ignores the cadence wait (feasibility and caps still apply). |
| `ExileHegLeagueDirector.java` | Test pins `testing.grantAuthCodes` and `testing.grantOfficialBusiness` (a faction id; sets that flag for 30 days on game load). Needed because Console Commands may not be available on 0.98.5a. |
| `tools/exiles-log-report.ps1` | A `STOP` row in the pattern table and a "Player stops" watch-list section. |

Written onto each fleet, 2-day expiry: `$willHasslePlayer`, `$hassleType`, `$exiles_hl_stop`
(kind: `raider` or `official`), `$exiles_hl_toll`, `$exiles_hl_tollHalf`, `$exiles_hl_tollDouble`,
`$exiles_hl_supplies`, `$exiles_hl_lieChance`, `$exiles_hl_crMult`. The toll and lie chance are
worked out from the player's fleet points and standing at each tick, so they are current when the
dialogue opens.

Nothing is written when: the kill switch is off; the op has left its action window (the return leg
is never marked); the fleet is hostile to the player (vanilla would skip it anyway).

**Kill switch:** `playerInterception.enabled` in config and `$exiles_hl_interception_enabled` in
sector memory. Off means the marks lapse within two days.

**Log line:** `Exiles Heg-League STOP: <inspect|cordon> <system> actor=<faction> outcome=<complied|
paid|paid_supplies|waived|negotiated|lied_ok|lied_caught|refused_failed|backed_down|codes|official|
cut> toll=<n>`.

### 11.4 `rules.csv` (about 40 rows, vanilla commands only, ids prefixed `exileHLStop`)

Every row carries `PLACEHOLDER` in its notes column; all prose is the assistant's, for Sean to
rewrite. Two voices in v1: the raider shakedown and the official stop. Faction-specific wording
is a second row conditioned on `$faction.id`.

| Rows | Trigger | What they do |
|---|---|---|
| Opening | `BeginFleetEncounter`, on `$exiles_hl_stop` | Vanilla's enforcer pattern: mark the stop complete, hold the player (prevent disengage), hail. |
| Greeting, fleet stronger / player stronger | `OpenCommLink` | Opening line per voice. When the player is stronger the hold is released, as vanilla does. |
| Comply | option | `ApplyCRDamage` at the type's weight, release, free to go. |
| Pay | option | `DoCanAffordCheck` greys it out; `AddRemoveCommodity credits -$exiles_hl_toll`; release. |
| Pay in supplies | option, raider only, `$player.supplies >= $exiles_hl_supplies` | Supplies instead of credits. |
| Standing waiver | option, `$player.fcm_faction == $faction.id` | Release, no cost. |
| Negotiate | option, `RepIsAtWorst $faction.id WELCOMING`, once | The Pay option switches to the halved toll. |
| Lie | option, once | `RollProbability $exiles_hl_lieChance`. Success: release. Caught: `AdjustRep $faction.id INHOSPITABLE -2`, Lie and Negotiate removed, Pay switches to the doubled toll. |
| Refuse, fleet stronger | option, once | Fails with a line, as vanilla. Raiders only: `MakeOtherFleetLowRepImpact` for 2 days. |
| Refuse, player stronger | option | Officer backs down, `AdjustRep $faction.id INHOSPITABLE -1`, release. |
| Official business | option, `$faction.exiles_hl_officialBusiness` | Release. Nothing sets the flag in v1. |
| Authorization codes | option, `$faction.exiles_authCodes` | Release. Nothing sets the flag in v1 except the test pin. |
| Cut the link | option | Vanilla's `cutCommLinkNoText`. |

Verified for these rows (§4.1 and 2026-11-16 source reads): `RollProbability`, `AdjustRep` and
`ApplyCRDamage` all accept memory variables as arguments; `$faction.` is the stopping fleet's
faction memory in a fleet dialogue (`RuleBasedInteractionDialogPluginImpl` line 115), so one row
serves both sides; a lie's result is seeded by the fleet, the rule and the month, so a reload does
not reroll it. **[TO VERIFY at build]** that a condition may compare two variables of different
memories (`$player.fcm_faction == $faction.id`); vanilla only shows same-kind comparisons such as
`$player.supplies >= $SP_supplies`. Fallback: one row per faction with the id written out.

### 11.5 Config: `exilehegleague.playerInterception` (proposed defaults)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | true | Kill switch. PLAYER OPTION. |
| `tollPerFleetPoint` | 200 | Vanilla's Pather tithe rate. |
| `tollFloor` / `tollCap` | 2000 / 50000 | A 20-point fleet pays 4,000; the cap is reached at 250 points. |
| `tollMult.hegemony` / `tollMult.persean` | 1.0 / 1.0 | Per-side lever (D3). |
| `negotiateMult` / `caughtLieMult` | 0.5 / 2.0 | Applied to the toll. |
| `creditsPerSupply` | 100 | Supplies in kind = toll ÷ this (40 supplies for a 4,000 toll). |
| `lieBaseChance` / `lieRepShift` | 0.5 / 0.5 | Chance = base + standing × shift, standing from −1 to 1. Inhospitable about 35%, neutral 50%, Welcoming about 65%. |
| `lieMinChance` / `lieMaxChance` | 0.2 / 0.8 | Clamp. |
| `repCaughtLie` / `repFacedDown` | 2 / 1 | Reputation points lost with the actor. |
| `repFloor` | INHOSPITABLE | Neither loss can push the player into hostility. |
| `inspectionWeight.inspect` / `.cordon` | 0.2 / 0.3 | Readiness damage; 0.2 is vanilla's enforcer. |
| `raiderLowRepImpactDays` | 2 | How long a refused raider stays low-impact. |

### 11.6 Small decisions inside the build **[ASSISTANT — Sean to confirm or change]**

1. **The numbers in §11.5.**
2. **`repFloor` INHOSPITABLE**, so a caught lie or a faced-down officer can never tip the player
   into hostility with that side. **[TO VERIFY at build]** exactly where vanilla's limit stops
   the loss for a player already at Inhospitable.
3. **The codes flag is named `$exiles_authCodes`, not `$exiles_hl_...`,** because Sean's idea goes
   beyond these encounters and a later system should not have to rename it.
4. **A commissioned player is still hailed and uses the waiver option** (one click), per the
   ruling's wording. The alternative is not stopping a commissioned player at all, which is one
   line in the helper.

### 11.7 Test checklist (one sitting)

Start: a **standard (non-Exile) start**, which has neutral standing with both sides, so stops can
happen from day one. No Nexerelin (not yet on 0.98.5a).

| # | Setup | Do | Expect |
|---|---|---|---|
| 1 | `testing.interdictionNow` true | Fly to the raided system, stay within 2,000 units of the raiders, transponder on | Pursuit, hail, raider greeting. `STOP` line on exit. |
| 2 | Same | One option per stop across several stops: Comply, Pay, Pay in supplies, Lie, Refuse | Outcomes as in §11.4; toll and supplies match the config; a caught lie costs 2 reputation. |
| 3 | A fleet much stronger than one raider fleet | Open comms | The "player stronger" greeting; refuse makes the officer back down, reputation −1. |
| 4 | After a failed Refuse on a raider | Cut the link, look at the fleet's description, attack | Vanilla's "will not cause immediate hostilities" text; 3 to 5 reputation lost, no hostility. |
| 5 | `forceState` 3 (squeeze) | Approach the cordon at the anchor market | The official greeting; no supplies option; a fight there turns the side hostile after vanilla's warning. |
| 6 | `testing.grantAuthCodes` "persean", then `testing.grantOfficialBusiness` | Get stopped by a League fleet | The matching option appears and releases the player. |
| 7 | `playerInterception.enabled` false, reload | Sit near raiders for three days | No pursuit. |
| 8 | An Exile-start save (hostile to both) | Approach raiders | No dialogue; they attack as today. |
| 9 | Any stop | Cut the link from a stronger fleet and attempt to disengage | Escape battle; **check reputation is unchanged** (§4.1 says it should be). |

The 20 to 30 day cooldown per stop type will slow steps 1 to 4. **[ASSISTANT]** a fourth test pin,
`testing.clearStopCooldown`, would clear it on each tick; say if it is wanted.

Also to read from the log afterwards: how often raiders at the system centre reach the player at
all (§9), and whether the cordon stops anyone while it is still fighting the station.

### 11.8 What 8a-1 does not include

The blockade stop (8a-2), docking interception (8b), the Authorization Codes bar quest, cargo
seizure, any relief-convoy dialogue, and final prose.

---

## 12. Build record — Pass 8a-1 (2026-11-16) **BUILT, NOT YET RUN IN GAME**

Sean approved §11 and its four small decisions as written ("Approve, build it"). Built the same
day in the 0.98.5a copy.

**Checks that passed:** Janino EXIT=0 on all seven touched or dependent classes
(`ExileHegLeaguePlayerStop`, `ExileHegLeagueConfig`, `ExileHegLeagueInterdictionStage`,
`ExileHegLeagueSqueezeStage`, `ExileHegLeagueInterdictionManager`, `ExileHegLeagueDirector`,
`ExileHegLeagueStrikeManager`) and on `ExilesModPlugin`. `rules.csv` re-read as CSV: 63 rows, 30
of them new, no duplicate ids, every offered option has a handler, LF endings and no BOM as
before. All eleven rule commands the rows call exist in vanilla's `rulecmd` package.
`director_config.json` braces balance; every test pin is at its default. The log report tool
runs and shows the new section.

**Not checked:** anything in a running game. The rules file is only parsed at game start, so the
first launch is also the first real test of the rows.

**What was built**

| File | Change |
|---|---|
| `ExileHegLeaguePlayerStop.java` (new) | The stateless helper of §11.3: marks fleets, computes toll and lie chance, logs outcomes, applies the two grant test pins. |
| `ExileHegLeagueInterdictionStage.java` | `refreshPlayerStops()` from `updateStatus()` while ONGOING; marks renewed every 0.5 day; hooks `getStopType()` / `getStopKind()`. Two transient fields, nothing saved. |
| `ExileHegLeagueSqueezeStage.java` | Overrides both hooks: cordon stop type, official voice. |
| `ExileHegLeagueConfig.java` | The `playerInterception` block and three test pins. |
| `ExileHegLeagueDirector.java` | Pushes the config to the helper and applies the grant pins, once per load. |
| `ExileHegLeagueInterdictionManager.java` | `testing.interdictionNow`: one launch attempt per game load skips the cadence wait. |
| `data/config/exiles/director_config.json` | The `playerInterception` block (§11.5 values) and the three pins. |
| `data/campaign/rules.csv` | 30 rows, ids `exileHLStop*`, plus a comment row. |
| `tools/exiles-log-report.ps1` | "Player stops (Pass 8a)" section. |

**Differences from the plan**

- **30 rows, not about 40.** Raiders have one voice for both sides; only the official greeting is
  written per side.
- **The commission check is done in Java.** The helper writes `$exiles_hl_commissioned` onto the
  fleet when the player's commission matches the acting side, and the waiver row reads that. This
  removes the open question in §11.4 about comparing two memories in one condition.
- **Prices live in the conversation's own memory.** In a comm link the rule's local memory is the
  officer's, not the fleet's (vanilla's own rows show this), so the opening row copies the toll
  into `$exiles_hl_price` and Negotiate or a caught lie overwrite the copy. One Pay row serves
  all three prices.
- **One extra log line, `STOP MARKS`,** once per op per game load, the first time its fleets are
  marked while the player is in the system. It separates "never marked" from "marked but never
  caught the player", which §9 needs measured.
- **Marks are only written while the player is in the op's system.** An op the player never
  visits costs one route walk per tick and nothing else.
- **No cooldown test pin.** It was offered and not asked for.

**Known limits, by construction**

- A fleet can still stop the player for up to two days after its op leaves the action window,
  until the marks lapse.
- `testing.interdictionNow` resets the cadence clock when it fires.
- **[TO VERIFY, live]** still open from §9 and §11.6: raid-route fleets pursuing and returning to
  their loiter; how often raiders at the system centre reach the player; the cordon while it is
  fighting the station; where the reputation floor stops a loss at Inhospitable; that an escape
  costs no reputation.

**Test:** §11.7, nine steps, standard (non-Exile) start. After the run, read
`tools/exiles-log-report.ps1` → "Player stops (Pass 8a)" and "Test pins".

---

## 13. First run (2026-11-16, save "Horatio") — **NO STOP FIRED; CAUSE NOT YET KNOWN**

Standard start, no Nexerelin, no test pin except `debugMessages`. Mar 2 to Oct 30, c206. Zero
Exiles errors. The rules file loaded without complaint, which is the only thing this run proves
about the 30 rows: none of them was reached.

**What the log shows**

| When | Player in | Op | Result |
|---|---|---|---|
| Jun 26–30 | Arcadia | League interdiction of Arcadia from Mairaath, 174 FP, six fleets | Marked Jun 26 (`STOP MARKS … fleets=6 toll=2000`). No `STOP` line. All six fleets destroyed by Jun 30 (FP abort, remaining FP 0). Arrival strength 201 against a launch strength of 364. |
| Sep 18 – Oct 14 | Tyle | Hegemony strike on Cibola | Landed. Strikes carry no stop, by design. |
| Sep 18 – Oct 9 | Tyle | Second League interdiction of Arcadia, assembling at Cibola | Wiped out in the assemble stage while the Hegemony strike force sat on Cibola. Never marked. |

No system blockade and no squeeze launched; the war never reached Escalation.

**What Sean saw at Arcadia (2026-11-17)**

1. All six fleets made straight for Citadel Arcadia.
2. He put his fleet between them and the station; they flew past him.
3. They looked already locked onto the station.
4. The station and two fairly strong patrols destroyed them.

**Read against the code [VERIFIED in source]**

- An interdiction is not ordered to the station. The fleets enter at the FRINGE jump point
  (`RouteLocationCalculator.findJumpPointToUse` returns the fringe point for a faction that does
  not control the system), wait there on `ORBIT_AGGRESSIVE`, then take `PATROL_SYSTEM` on the
  system centre for the presence window. `loiterOnFocusMarket` was `false` on disk.
- With the centre as target, `PATROL_SYSTEM` picks its goals from own markets, jump points, comm
  relays and non-hostile planets. A hostile market is not on that list.
- The do-not-attack list only stops a raider choosing the station as its own target. It does not
  stop it chasing a patrol or a convoy that is beside the station, nor joining a battle there
  (`STARSECTOR_VANILLA_FLEET_BEHAVIOR.md`, traps).
- So what Sean saw does NOT match the orders, and the cause is not established. **[TO VERIFY]**
  the leading suspect: the raiders' own target choice pulled them onto fleets near the Citadel.
- `HasslePlayerScript` skips a marked fleet that is fleeing, in a special action, or whose
  interaction target is another fleet. A raider locked onto something else is skipped. That
  would explain no stop without any defect in 8a-1; a fleet that vanilla did send and that
  ignored the order would be a defect. The log could not tell these apart.

**Finding: an interdiction only succeeds when the player is not there [VERIFIED, small sample]**

- `RouteManager` spawns a route's fleet only within 1.6 light-years of the player
  (`SPAWN_DIST_LY`). Outside that the op is a clock: no fights, no losses.
- Archived reports, Heg-League interdictions: 11 of 11 with the player elsewhere completed
  their presence. The two with the player in the target system both failed on losses (Naraka
  in an earlier run; Arcadia here).
- The launch gate adds the group into one strength (364 here) against the defenders (163) and
  counts the station as zero, because raiders "never fight a station". In a real fight the
  group is six fleets of about 30 FP each.
- **Not tuned.** Sean's call, and only after the cause above is known.

**Suggestions awaiting a ruling [ASSISTANT]**

- Squeeze stops begin when the squeeze's market condition lands (day 5), not while the cordon is
  still fighting the station. From Sean's question "perhaps they don't stop anyone until they set
  up their cordon?" (2026-11-17).
- A source market that is the target of a live enemy op should perhaps not stage an op of its
  own (the Cibola case). Design question, not looked at further.

**Measurement added 2026-11-17: `STOP DIAG`**

`testing.stopDiagnosticsDays` (0 = off, production; 0.5 for a test). While the player shares the
system with an interdiction or squeeze, one log line per op per interval lists each of the op's
fleets there: current order and its target, what the AI is chasing, interaction target, distance
to the focus market and to the player, whether it can see the player, and the flags vanilla's
hassle filter reads (`marked`, `pursuingPlayer`, `busy`, `specialAction`, `inBattle`, `fleeing`,
`hostileToPlayer`), plus `stationOffLimits` when the focus market's station is on its
do-not-attack list. The line also carries vanilla's sector-wide cooldown for both stop types; a
cooldown only starts on a successful hassle roll, so "none" all through a watched op means
vanilla never sent a fleet after the player.

It is called from `ExileHegLeagueRaidIntel.advanceImpl`, not the stage, so it also covers the
approach and the wait at the jump point (`phase=approach`). Log only; one transient clock field,
nothing saved. Files: `ExileHegLeaguePlayerStop.java`, `ExileHegLeagueRaidIntel.java`,
`ExileHegLeagueConfig.java`, `director_config.json`, `tools/exiles-log-report.ps1` (section
"Player stop diagnostics (Pass 8a test)"). Janino EXIT=0 on ten touched or dependent classes.
Not yet run in game.

---

## 14. Second run (2026-11-18, same save, to Dec 31, c207) — **ONE STOP FIRED; THE SEAM WORKS**

Same "Horatio" campaign continued, standard start, no Nexerelin, pins `debugMessages` and
`stopDiagnosticsDays` 0.5. Four reloads of earlier saves. Zero Exiles errors. 191 `STOP DIAG`
lines, 529 fleet samples.

**The stop (Valhalla, Dec 9–10, c207; League interdiction from Kazeron)**

| Diag sample | Raider | Distance to player | Flags | Vanilla cooldown |
|---|---|---|---|---|
| 1–3 | chasing and fighting a Hegemony Fast Picket | 1619 → 306 | `marked`, then `inBattle` | none |
| 4 | still in battle | 302 | `marked,inBattle` | none |
| 5 | in battle | 298 | `marked,pursuingPlayer,busy,specialAction,inBattle` | 22.4 d |
| 6 | `STANDING_DOWN` | 294 | `marked,pursuingPlayer,busy,specialAction` | 21.9 d |
| 7 | chasing = PLAYER, contact = PLAYER | 260 | same | 21.4 d |
| — | `STOP: inspect Valhalla actor=persean outcome=lied_ok toll=0` | | | |
| 8 | chasing a Hegemony Patrol again | 755 | `marked` | 20.9 d |

So, **[VERIFIED live]**: vanilla rolled the stop on a marked raid-route fleet; the fleet finished
its fight, stood down, then came after the player and hailed about a day and a half after the
roll; the dialogue ran; the Lie success row fired and logged; the fleet went back to hunting.
This closes two §12 items: a raid-route fleet does pursue, and it does resume afterwards.
Sean: "the dialogue options seemed to work fine". **Only Lie (success) has been exercised.**
Untested: Comply, Pay, Pay in supplies, Negotiate, Lie caught, Refuse (both voices), the three
reserved options, the stronger-player greeting, any cordon stop.

**Why the other encounters produced no stop**

- **Valhalla after each reload (three timelines):** the nearest marked raider was never closer
  than 5,700 units and never saw the player. Not eligible; nothing to explain.
- **Samarra (Sean's "different system"):** the League force there was the surgical strike on
  Eventide. Strike fleets carry no stop, by design (§1.1a covers raiders, cordons and blockades).
  **Open question for Sean:** from the cockpit a strike force and an interdiction look alike.
- **Tyle (Hegemony interdiction, Jul–Aug c207), NOT fully explained.** For five consecutive
  samples (two to three days) four marked raiders were 28–1,848 units from the player and could
  see him, with no contact, no cooldown and no excluding flag I recorded, and vanilla never
  rolled. All four were `inBattle` the whole time and were destroyed at the end of it. At 25% per
  check that is roughly a 1-in-20 run of bad luck. **[TO VERIFY]** the alternative: vanilla's
  filter calls the fleet AI's `isFleeing()`, and the diagnostic was reading the tactical
  module's instead; a fleet losing a battle may count as fleeing. The diagnostic now makes the
  same call vanilla does (fixed 2026-11-18), so the next watched battle settles it.
- **Galatia:** the player arrived in the op's last half-day; no raider within range.

**Where the raiders fight [VERIFIED, two systems]**

- **Tyle:** 83 of 143 action-phase samples were in battle; 63 of those 83 were within 1,000
  units of the focus market, Madeira, and none beyond 3,000. Targets being chased: League
  convoys 19, Detachments 12, Patrols 5, Fast Pickets 3. The station was on every raider's
  do-not-attack list (138 of 143 samples) and they died beside it anyway.
- **Valhalla:** 58 of 196 in battle, all more than 3,000 units from the focus market.
- This confirms Sean's reading of Arcadia: raiders chase convoys and patrols, the chase ends at
  the market, and the fight happens under the station's guns. The do-not-attack list does not
  prevent it.

**Watched versus unwatched, updated.** Interdictions with the player in the system during the
action window: Naraka (earlier run), Arcadia, Tyle, Valhalla. All four failed on losses. The one
that completed this run (Galatia) had the player there only for its final day. **Not tuned.**

**Also seen:** 84 of 143 Tyle samples showed `GO_TO_LOCATION` onto a League objective. That is
NOT the patrol order; §15 traces it to vanilla's war simulation. (An earlier wording of this
paragraph called it "the patrol flying to an objective"; that was wrong.)

---

## 15. Why raiders end up under the station (2026-11-18) — **CAUSE FOUND; FIX NOT RULED**

Sean asked how vanilla keeps its pirate raids from dog-piling a station, and guessed our raiders
were spotting convoys at the market from far off and all running at the one target.

**What vanilla does [VERIFIED in source, `starfarer.api.zip`]**

- **A vanilla pirate raid is meant to attack the market.** `PirateRaidActionStage.updateRoutes`
  only picks a target market if raid strength exceeds the defenders' fleet strength PLUS that
  market's station (`WarSimScript.getStationStrength`). If no market passes, it picks nothing
  and the raid ends at once (the "cannot beat" exit in the CLAUDE.md raid-stage gotcha). If one
  passes, the stage adds a `MilitaryResponseScript` on it, so the raiders converge on that one
  market as a group and fight the station on purpose.
- **Raiders under that order are flagged busy** (`$core_fleetBusy`), and
  `MilitaryResponseScript` skips busy fleets, so nothing else can redirect them.
- **Vanilla pirates that prey on trade are a different thing.** They use `RAID_SYSTEM`, which
  parks a fleet on a jump point with no hostile market within 4,000 units, or on a lane between
  markets (`STARSECTOR_VANILLA_FLEET_BEHAVIOR.md` §2.2).

**What happens to ours [VERIFIED in source and in the Tyle diagnostics]**

- Our interdiction stage replaces `updateRoutes` wholesale: no target market, no military
  response, and so **no busy flag during the action window**. The raiders sit on a plain
  `PATROL_SYSTEM` at the system centre.
- Vanilla's `WarSimScript.processStarSystem` runs every 2–5 days per system. For every
  objective (comm relay, nav buoy, sensor array) owned by a hostile faction, the STRONGEST
  faction in the system by fleet strength "wants to own" it, and gets a `MilitaryResponseScript`
  that sends 40% of its fleet points in that system there for 20–40 days. Stations are not
  counted in that strength.
- **Our launch gate only launches a group stronger than the defenders' fleets.** So on arrival
  our raiders are, by construction, the strongest faction in the system, and vanilla orders them
  onto the enemy's objectives.
- **Tyle, sample by sample:** day 0–1 all five raiders `PATROL_SYSTEM>Tyle`, 11,000 units from
  Madeira. Day 1.5: all five on `GO_TO_LOCATION` to `Canaria Relay`, `Makeshift Sensor Array`
  or `Makeshift Nav Buoy`, **chasing nothing**, still 9,500–11,700 units out. They closed
  steadily. The first chase target (a League Detachment) appears at 1,800–4,500 units, the
  first battle at 1,300–2,900, then convoys. From there every sample is within about 600 units
  of Madeira until the group is destroyed. `Canaria Relay` orbits the same gas giant as
  Madeira, 1,400 units from it.
- The same fleets also run vanilla's `checkCapture` (from `RaidAssignmentAI.advance`): any
  hostile objective within 2,000 units gets a capture attempt (`DELIVER_CREW` then `HOLD`, seen
  at Valhalla's `Ragnar Relay`). `BaseAssignmentAI.canTakeAction` skips that for a busy fleet.

**So Sean's guess is half right.** They do all run at one place and feed themselves to the
station. The draw is an order from vanilla's war simulation, not long-range detection of
convoys: the order came when they were chasing nothing, three times farther out than they
first reacted to any fleet. Convoys and patrols are what finish the job once they are close.
**[TO VERIFY]** Arcadia has no diagnostics; its `Syrinx Relay` is 6,000 units from the Citadel,
so that case may have a different mix.

**Options [ASSISTANT; none built, Sean to rule]**

| | Change | What it does | Cost / risk |
|---|---|---|---|
| A | Flag raiders busy during the action window, renewed on the stage tick that already renews the do-not-attack list | Vanilla's own lever: the war simulation can no longer take them, and they stop trying to capture objectives | A few lines. Other readers of the flag must be checked first, including a string scan of the obfuscated engine. The player stop is not affected in source (`HasslePlayerScript` sets the flag itself and does not filter on it) |
| B | `WarSimScript.setNoFightingForObjective(objective, actor, days)` for each objective in the target system | Stops the order at its source | Faction-wide in that system, so it also binds a strike or blockade of the same side there. Does not stop `checkCapture` |
| C | Give raiders `RAID_SYSTEM` instead of the centre patrol | Vanilla's lane-lurking: jump points away from hostile markets, and lanes. This is the "disrupt traffic" behaviour by design | Needs a custom assignment AI for the action segment. Larger; still needs A |

Recommendation: A alone first, then one watched interdiction with `STOP DIAG` to see where they
go. C only if they still drift onto the market.

### 15.1 Sean's rulings (2026-11-18) and what was built

1. **The behaviour needs fixing.** It is situational, but "bizarre (and a little silly-looking)
   to watch in-game".
2. **Raiders keep taking relays and arrays.** It is routine in vanilla raids, every other
   director leaves it in, and it is a sensible tactic that puts the defender at a disadvantage.
   Sean's correction of my wording: objectives are MOSTLY placed well away from markets (the
   need is even mentioned in lore); some systems break that rule, and orbits bring bodies close
   and far over time. My "usually next to the station" rested on one case and is withdrawn.
3. **Option A as written is therefore out**: the busy flag stops objective-taking everywhere.
4. **Win and loss stay as they are.** A win is the presence lasting its planned window; a loss
   is dropping below `abortFraction` of launch fleet points.
5. **Unwatched ops are left to vanilla.** Whether they take losses is irrelevant.
6. **Heg-League only.** The Ashen and Black Fleet siblings were not looked at.
7. **Option C parked**: one change at a time.

**Built (Sean: "Yes, build it") — a narrow form of option B, interdiction only.**
`ExileHegLeagueInterdictionStage.keepOffObjectivesUnderStations()`, called from the stage tick
while ONGOING, every half day:

- For each objective in the target system not owned by the raiders' side, find the nearest
  market hostile to them that has a station INDUSTRY (not the station fleet, which reads absent
  after a load).
- If that market is within `interdiction.objectiveStationKeepOut` (3000, a first guess): withdraw
  any vanilla fight order for (objective, raider faction), SET vanilla's own per-objective
  no-fighting timeout to 3 days, and take this op's fleets off any response order aimed at it.
- Otherwise do nothing: the objective is fair game, exactly as in vanilla. A keep-out that is
  no longer renewed lapses within 3 days.
- The squeeze overrides the hook to off.

**Log lines** (report section "Objective keep-outs (interdiction)"):
`<op> OBJECTIVES at <system> (keep-out N): <name> [owner] <dist> from <market> -> KEPT OUT | open; …`
once per op per game load, and `OBJECTIVE KEEP-OUT START` / `END` on each transition.

**Known limits**

- The timeout is per faction, so a strike or blockade force of the same side in that system is
  bound by it too while the interdiction runs.
- Vanilla's opportunistic capture (`checkCapture`, within 2,000 units) is untouched. A raider
  that wanders that close to a kept-out objective on its own will still try to take it.
- The centre patrol can still pick a comm relay as a patrol goal on its own.
- The timeout id replicates vanilla's protected `getControlTimeoutId` (`factionId_objectiveId`).
  A vanilla change to that format would silently disable the keep-out; the START line would
  still print. Re-check on a Starsector update.
- Unwatched, vanilla's abstract simulation may still flip a kept-out objective to the raiders'
  side. Left to vanilla (ruling 5).

**Checks:** Janino EXIT=0 on nine touched or dependent classes. `director_config.json` braces
balance. **Not run in game.** Next: one watched interdiction; read the OBJECTIVES census, the
keep-out lines, and `STOP DIAG` for where the raiders actually go.

---

## 16. Third run (2026-11-19 to 11-22, RC8, new campaign, to Oct 16, c207) — **SIX STOPS, FIVE OUTCOMES, NO ERRORS**

New standard-start campaign on 0.98.5a-RC8, no Nexerelin, three launches, two reloads, pins
`debugMessages` and `stopDiagnosticsDays` 0.5. Zero Exiles errors. The only dialogue-command
errors in the log are the two vanilla `SellToFleet` ones from the first launch
(`EXILES_0985A_COMPAT_REVIEW.md` §8.4); none after that fix, though the log cannot show whether
a salvage expedition was hailed again. 73 `STOP DIAG` lines, 341 fleet samples.

**Stops (all interdiction raiders, all League)**

| When | System | Outcome logged | Toll | What it exercises |
|---|---|---|---|---|
| Sep 26–28, c206 | Corvus | `paid` | 9,000 | Pay in credits |
| Apr 3–4, c207 | Valhalla | `complied` | — | Comply; vanilla readiness damage |
| Oct 15–16, c207 | Naraka | `paid_supplies` | (logged `toll=0`) | Pay in supplies, raiders only |
| same save, replay | Naraka | `backed_down` | — | Player stronger: refuse, fleet backs down, reputation −1 |
| same save, replay | Naraka | `lied_ok` | — | Lie succeeds |
| same save, replay | Naraka | `paid` | 17,000 | Pay in credits |

Sean saved before the Naraka hail and played it four times. No rule row threw, in any of the six.
With `lied_ok` from §14, the exercised set is now: Comply, Pay, Pay in supplies, Lie (success),
Refuse when stronger, and both greetings (stronger and weaker player).

**Still untested:** Negotiate (needs Welcoming), Lie caught, Refuse when weaker (the fight),
the three reserved options (commission waiver, official business, authorization codes), the
official voice, and any cordon stop.

**Note for testing a caught lie.** `RollProbability` is seeded by fleet, rule id and month, so
replaying one save gives the same lie result every time. A caught lie needs a different fleet or
a different month.

**Two logging faults found and fixed (2026-11-22)**

- `paid_supplies` logged `toll=0`: the supplies row never wrote the paid amount. It now records
  the units and the line reads `supplies=<n>` for that outcome.
- `OBJECTIVE KEEP-OUT END … no enemy station left` was printed when the objective had in fact
  been captured by the raiders' side. It now says so.

**The cordon.** A League squeeze held Citadel Arcadia for its full window. Sean arrived in its
last two days: 12 of 18 samples were marked (`STOP MARKS: cordon … toll=8000`), but no cordon
fleet was ever both within 2,000 units and able to see him, so no stop. Two observations from
those three lines: 13 of 18 samples were in battle within 3,000 units of the Citadel (as
designed, the squeeze fights the station); and **all six cordon fleets were on
`GO_TO_LOCATION > Syrinx Relay`**, vanilla's war-simulation order. That is the squeeze being
pulled off its anchor, the case §15 flagged as unverified. Three lines at the very end of the op
are not enough to judge it.

**Objective keep-out: engaged three times, not yet proven**

| Op | Census (distance to nearest enemy station market) | Kept out | Player there? | Result |
|---|---|---|---|---|
| Naraka, Jun–Aug c206 | Nav Buoy 1,859 from Yama; Naraka Relay 7,967 | Nav Buoy | no | completed |
| Corvus, Sep–Oct c206 | Jangala Relay 4,500; Sensor Array 6,142; Nav Buoy already League-held | none | yes | **failed on losses, day 15** |
| Galatia, Dec c206 | Ancyra Relay 2,799; Nav Buoy 8,557 | Ancyra Relay | no | completed |
| Valhalla, Mar–May c207 | Ragnar Relay 2,424; Nav Buoy 5,485; Sensor Array 17,589 | Ragnar Relay | yes, about 19 days | **completed** |
| Naraka, Sep c207– | both objectives League-held; the Nav Buoy now 7,002 from Yama | none | yes | still running |

- **Valhalla is the first watched interdiction to complete its window** (five fleets, 238 to
  150 FP over the watched stretch). But it does not prove the keep-out: the raiders stayed on
  `PATROL_SYSTEM > Valhalla` for 126 of 132 samples and were never ordered to ANY objective,
  kept out or not. On arrival they were weaker than the defenders (509 against 598), so
  vanilla's "strongest faction" test may simply never have selected them.
- What Valhalla does show: **raiders left on the plain centre patrol survive.** They skirmished
  (17 of 132 samples in battle, none within 1,000 units of the focus market) and held.
- **Corvus is not the station pattern.** All 35 in-battle samples were more than 3,000 units
  from Jangala. The group was ordered to the League-held Nav Buoy and to the Jangala Relay
  throughout, fought Hegemony Detachments, Patrols and pickets in the open, and wore down from
  354 to 180 FP. That is defenders winning, which Sean has ruled acceptable.
- **Sean's two corrections are borne out by the census.** Objectives are mostly far from
  stations: of the eleven objective-to-station distances now measured (with Tyle's Canaria Relay
  at 1,400 and Arcadia's Syrinx Relay at about 6,000), four are inside 3,000. And orbits matter:
  Naraka's Nav Buoy was 1,859 from Yama in June and 7,002 in September.
- **3,000 looks reasonable so far**; nothing in this run argues for moving it. A decisive test
  still needs a watched interdiction that arrives as the strongest faction in a system with an
  objective inside the limit (Tyle is the known case).

**The "fleeing" flag.** Now read with vanilla's own call, it appears on raiders after losing
fights (18 samples at Corvus, 7 at Valhalla). That supports the second explanation of the §14
Tyle case (vanilla skips a fleeing fleet), without proving it.

**Watched interdictions to date:** Naraka, Arcadia, Tyle, Valhalla (earlier runs) and Corvus
failed on losses; Valhalla (this run) completed. One of six.

### 16.1 Sean's review of the run (2026-11-22)

**Rulings**

- **Tolls feel reasonable.** No change to `tollPerFleetPoint`, floor or cap.
- **Prose:** "very functional"; Sean expects a few tweaks only. Still his to edit.
- **`repFacedDown` 1 → 3.** Threatening a fleet into backing down should cost at least 3. This
  supersedes the 2026-11-09 answer (Q3: "at least 1"). A caught lie stays at 2 plus a doubled
  toll, so it costs less reputation than facing a fleet down. **RULED by Sean the same day:**
  that is correct; a lie should damage a reputation less than threatening the other party.
- **`creditsPerSupply` 100 → 150.** At 100 the two payments were worth the same, so credits were
  simply better (no restocking). At 150 a 17,000 toll is 114 supplies instead of 170: cheaper
  than cash at the base price, in return for the logistics.

Both are config values; the code defaults were moved with them. Janino clean, not yet run.

**Sean's three questions, answered from config and source**

1. **Only League fleets stopped him.** By design, not luck. Stops exist on interdictions and
   squeezes only, and `opWeights` gives the League 3 to the Hegemony's 1 on both. The Hegemony's
   weight sits on surgical strikes and system blockades (3 each): strikes carry no stop by design,
   and the blockade stop is 8a-2, not built. It strikes at low tension because RETALIATION is one
   punitive op chosen by the incident's victim from its own weights, and the League was the
   offender in three of this run's four incidents. **Until 8a-2 exists a Hegemony stop will be
   rare: one interdiction in four, one squeeze in four.**
2. **Supplies were always offered.** By design: the option appears whenever the stopping fleet
   is a raider and the player holds enough supplies. There is no roll. (The earlier idea of a
   per-fleet corruption roll was dropped on 2026-11-16.)
3. **The lie worked both times.** Luck, and not much of it: the chances were 0.57 and 0.55, so
   two successes in a row is about a one-in-three event. The failure branch has still never run
   in play. `RollProbability` takes a 0-to-1 chance and is seeded per fleet, rule and month, so
   one save replayed gives one answer.

**Cannot-afford handling [VERIFIED in the rows]**

- Credits: the Pay option calls vanilla's `DoCanAffordCheck`, which greys the option out when
  the player's credits are below the current price.
- Supplies: the option is only added when `$player.supplies` is at least the current price, so
  without enough it does not appear at all. (The two behave differently: one greyed, one hidden.)
- Both prices follow a Negotiate (halved) and a caught lie (doubled).
- The player is never stuck: Comply, Lie, Refuse and cutting the link need nothing.
- **Not seen in play:** either the greyed Pay option or the hidden supplies option.
