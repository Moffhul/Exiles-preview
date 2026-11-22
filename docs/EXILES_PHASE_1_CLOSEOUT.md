# Exiles Phase 1 closeout review

Updated: 2026-09-16

This records Sean's remaining-work list and the documentation review. Sean's items are confirmed
scope; suggested additions below are review findings, not newly approved feature commitments.
No gameplay code was changed during this review.

## Sean's remaining work

1. Complete Hegemony/League war activity, including Midan's secret strikes.
2. Refactor Black Fleet's Business is Business and Business is Good conditions, and review the
   market effects of Black Fleet activities.
3. Review Omicron and Black Fleet star-system structures.
4. Refactor/polish Midan's system and industries. Their shared private economy
   (`exiles_midan_network`) was implemented 2026-09-16 and confirmed in play 2026-09-19 (Accessibility
   improved considerably); Sean plans to tune the specifics, and Nex transfer/reload behaviour is still unverified.
5. Transfer Skathi in Valhalla from Tri-Tachyon to Hegemony at campaign start. Hegemony war hawks
   masterminded and executed its capture because it had long been suspected of black-project
   research. This existential crisis directly caused Omicron's activation. Sean confirms the
   capture occurred shortly before activation in Cycle 198. Ownership transfer is not
   implemented by this documentation update. Review faction/entity ownership, personnel, defenses,
   descriptions, and Nex startup interaction when implementing; do not invent a broader market
   redesign from the ownership ruling.

Sean confirms Refugee salvage fleets complete. Older pending-test headings must not reopen that
feature automatically. Omicron's September 6 completion and tactical-core tuning hold also stand.

## Additional closeout candidates from repository records

- Black Fleet freight composition implemented 2026-09-15; offline checks pass, live sampling pending.
  Combat budgets preserved; commerce replaces transports, assault freight is additive, and raids,
  assaults and collectors have a fuel ship. See `EXILES_BLACK_FLEET_SYSTEMS.md` freight section.
- Security Centers/Processing Center policy review: Processing stability bonuses, mutual exclusion,
  construction gate and NPC stability totals. **Security Centers and Emergency Measures are both
  CONFIRMED WORKING IN PLAY (Sean, 2026-09-13)** — their old pending-test markers are closed; the
  Processing Center stability-bonus review is the only item left here.
- Combined director review (Ashen Pass 7.5; Hegemony/League Pass 9), followed by configuration
  externalization. The planned independent review uses a different model; this document review
  does not fulfill it. Include player-targeting invariants and interacting market pressures.
- Broaden geography review to Providence placement, per-system visibility/constellation choices,
  and Eochu Bres damaged-mirror tuning/prose. Include Midan's faction identity and elite roster
  within its polish pass.
- Starting fines implemented 2026-09-12; short-run live test passed 2026-09-13. **Still open:** the
  Intel view-entire-debt / early-payoff mechanism is bugged, and the long-run test (full schedule,
  arrears, completion) has not been run (`EXILES_STARTING_DEBT.md`).
  Nex FLATTEN support is settled as
  unsupported; player-facing setup disclosures remain to deliver. General food shortage is now
  a playtest check.

## Deferred idea — scope the hard-start consequences to the Exiles start (Sean, 2026-09-16)

**Idea for later review, not an implementation decision. No gameplay change was made here.** Sean
wants players to be able to enjoy the Exiles setting and factions even if they choose a less punishing
vanilla or Nexerelin start. Although starting as "the Exile" fits the setting, it need not be the
only way to play the mod.

- Investigate replacing the global `data/config/settings.json` `"enableStipend":false` override
  with start-specific stipend suppression in or alongside `ExileAddStartingScript` and the Nex
  Exiles start. The custom command already calls Galatia completion without a stipend, but the
  config override also suppresses stipend payments for other starts. Verify that removing it would
  restore vanilla/Nex behavior elsewhere without granting the Exiles start a stipend.
- Consider making the Thar and Daria player-colony creation run only for the chosen Exiles start,
  including its Nex version. Today `setupXechasStartingColoniesOnce()` runs from the unconditional
  `onNewGameAfterEconomyLoad()` patch path. Review the start-marker timing before moving or gating
  that work: the current marker is installed by a before-time-pass character-creation script, so it
  may not yet exist during the economy-load hook. Decide what Thar and Daria should be for players
  who choose another start, and verify new-game/save behavior before changing ownership.

## Follow-up rulings from Sean — 2026-09-12

- **Starting fines: implemented 2026-09-12; pending live test.** The Hegemony (possibly other
  vanilla factions too) convicted the player in absentia for assisting in the destruction of
  Nachiketa's fuel-production facility. Most pre-game assets were seized; starting resources are
  what escaped seizure. Heavy fines remained dormant during the player's decade-long absence.
  The Sector's surviving automated Domain credit infrastructure makes recurring account deductions
  plausible. Implement a simple negative-stipend-style monthly credit deduction for a fixed number
  of months, representing the collective fines. Accepted defaults total 1.8 million over 48 installments,
  one full grace month plus the partial starting month; unpaid expenses carry through vanilla accounting.
  Configurable collection, saved terms, and early settlement are built. No interest, enforcement fleets or additional escalation has
  been requested. Existing hostile starting relations represent shoot-on-sight orders.
- **General food shortage: no separate feature required.** Existing pressure and planned Midan
  strikes are intended to cover it. Sean will review the result in game to confirm sufficiency.
- **Nex FLATTEN: unsupported (Sean, 2026-09-12).** Require normal/default starting relationships
  for the authored Exiles campaign. Do not silently accept FLATTEN and then restore a different
  relationship setup. This ruling does not implement a setup restriction or warning.
- **Player setup disclosure required (Sean, 2026-09-12).** Document every Exiles requirement,
  restriction or override that prevents a player's setup choice from taking effect. Explain the
  affected option, supported choice, reason and actual behavior before campaign commitment, with
  matching installation/setup documentation. Include normal starting relationships, the existing
  fixed-sector requirement and the configurable Hegemony–League diplomacy/conquest protections.
  Canonical policy: `EXILES_NEXERELIN_COMPAT.md`.

## Personal Word-document review

Source folder: `Exiles Design Documentation/`. All 13 substantive Word files were read as text;
Word lock files are excluded. This was a content review, not a document-layout audit.

- Vision/design principles largely match current canon. The timeline's Midan/Black Fleet conflation
  and account of Omicron developing independent goals are superseded by the faction split and
  ratified defensive-mandate interpretation. The personal NPC material includes brainstorming,
  not uniformly approved character canon.
- `Hegemony_League_War_Whitelists.docx` provides candidate lists missing from the repository war
  specification. Sean confirms these are still drafts (2026-09-12); keep approval pending.
  Do not silently install them as blockade or general attack restrictions.
  - League markets: Laicalle Habitat, Cibola, Madeira, Yesod, Athulf, Fikenhild, Suddene.
  - League systems: Isirah, Tyle, Westerness (source spelling; resolve IDs before implementation).
  - Hegemony markets: Citadel Arcadia, Ancyra. The source's explanatory heading says Hegemony
    action, apparently a typo for League action; correct/confirm when finalizing the draft.
  - Hegemony system: Arcadia.
- `02 - Player Start.docx` reinforces starting debt but still leaves custom script versus Spacer
  debt unresolved. It does not establish a new implementation decision.
- `Changes and Strategic Targets.docx`: Eventide solar-array damage is already represented by
  the Midan false-flag strike candidate in the current war specification, not an additional
  unconditional starting patch. Hesperus's proposed KoL training facility is phrased as a question,
  not a commitment. The marine-delivery stabilization mission remains a Phase-2 idea; Emergency
  Measures does not imply that mission exists.
- Quest/NPC documents belong to Phase 2. No need to build their missions or characters to close
  Phase 1. The old Ashen raid draft is superseded by the implemented canonical war specification.
- `Exiles Design Documents.docx` is a proposed document outline, not evidence that every named
  document exists or that every listed topic is unfinished work.

## Open rulings — refugee-enclave decivilization (Maxios trace, 2026-09-19)

**Sequencing (Sean, 2026-09-19): tackle AFTER the Heg-League war director.** Recorded here so it
is not lost; none of it blocks current work.

**What happened.** In the 7-year Heg-League test campaign the Maxios refugee enclave (Magec)
decivilized. The save proves it was vanilla's own `DecivTracker.decivilize()` (population condition
gone, `ruins_extensive` → `ruins_scattered`, `$wasCivilized` set): warning ~Nov c210, decivilization
~Jun c211, which by vanilla's 16-month sampling rule means stability had been at **zero since about
mid-c209**. In the earlier 4.4-year campaign the same market sat at stability 7.

**Why.** No Exiles condition touches stability on that market (Pariah, Destitute, Price Spikes,
Corsair Activity and Crusade Harassment are accessibility/price effects). The sinks were vanilla's
responses to what those effects did to Magec, the sector's busiest war zone that game (an Ashen
blockade + rehearsal + two interdictions, a Black Fleet commerce raid + 470-FP assault, three Omicron
raids + a 1,088-FP incursion, extortion collectors every 10–45 days): *Food shortage* −3 (non-habitable
size 3, every unit of food imported), *Insufficient accessibility* up to −3 (Pariah −50% permanently,
plus −50% Corsair Activity, −50% Crusade Harassment and the ×0.6 blockade when they overlapped),
*Recent incoming shipment lost* −2 per trade fleet killed in-system, *Pirate activity* −1, and one
vanilla pirate raid (~Oct c210; nothing in Exiles raids through `MarketCMD`). Base 5 + 1 could not
carry that.

**Rulings needed — three gaps between the design's intent (priority 4: "protect against accidental
decivilization") and the safety nets' reach:**

1. **The `decivilized` starting condition on Maxios.** `data/campaign/econ/magec.json` is the only
   refugee market carrying vanilla's `decivilized` condition (Corvus and Yma do not). On a live market
   it adds hazard only, but `PatrolFleetManagerV2` returns early for any market that has it — **Maxios
   never fielded a patrol** — and vanilla's other systems (`SDFBase`, pilgrim fleets, hostile-activity
   factors, Nex's `nex_decivEvent_*` dialogs) read it as already dead. Options: (a) replace with
   `decivilized_subpop`, the live-market equivalent — squatters on a ruined world — at the cost of its
   built-in −2 stability; (b) drop it and let `ruins_extensive` carry the flavour; (c) keep it
   knowingly, accepting no patrols. Assistant recommendation: (b), or (a) only if the −2 is wanted as
   part of the enclave's fragility.
2. **Emergency Measures cannot see chronic starvation.** Its only arming seam is
   `ExileUnrestGuard.reportExilesPressure`, which fires when an Exiles op applies *unrest* (assault
   sacks, bombardments, strikes). Accessibility starvation applies none, so a colony Exiles pressure
   strangled for two years never armed the net built for exactly that — while Tibicena, in the same
   system, armed it twice. Options: (a) let the accessibility-hitting conditions (Corsair Activity,
   Crusade Harassment, Wartime Interdiction, the Ashen blockade) report pressure when applied; (b) a
   direct stability probe on markets carrying any Exiles condition; (c) accept the gap for NPC
   markets. Assistant recommendation: (a) — it is the same one-line call the unrest guard already makes,
   at the seams that already exist.
3. **No Exiles market carries `$core_noDeciv`.** The nine in the save are vanilla's core worlds.
   Vanilla honours the flag in `decivilize()` except on `fullDestroy`, so it is a one-line shield with
   no side effects. Options: (a) flag the four refugee enclaves (they are the most exposed markets in
   the sector and are load-bearing for the Refugee faction, the salvage economy and the Phase-2
   trust quest); (b) flag every Exiles-authored market; (c) none — let them fall and treat it as the
   sector breaking. Assistant recommendation: (a) for the enclaves at minimum; (c) is defensible only
   if a fallen enclave has a designed consequence, which today it does not (the faction just loses a
   market and the convoy/salvage sources shrink silently).

Evidence: `save_Simon_6657351328975479124` (the decivilized market, two `DecivIntel` records),
`save_Charles_6276014474183454815` (the same market at stability 7 in the prior campaign), and the
run-2 log's Magec event trail. Session analysis 2026-09-19.

**Second occurrence (Sean, 2026-10-04): Maxios decivilized again, Feb 18 c209**, in the Vanessa test
campaign (no Nex; started Mar c206, so about three years in). The save's `DecivIntel` timestamps put
the event at ~Feb 17–18 c209. Caveat: most of that campaign ran under `forceState` 4 and 3 test pins
(constant escalation and retaliation, tension pinned at 77–100), so it was a harsher war than an
organic game; the cause has not been traced. No action requested; Sean notes it reinforces item 4
below (the governors' stability skill).

## Deferred — a pacing/tuning review pass (Sean, 2026-09-20)

**Idea, not a decision.** Sean expects a dedicated "Tuning Phase", or at least a general review,
after Phase 1 or Phase 2, gathering everything that is really about pacing:

- **The game-time baseline.** One game year is roughly one hour of real play at normal speed, so
  the ~4-game-year assumption every director was tuned to may bring the sector's crises to a head
  around a real playthrough's early mid-game (Ashen doc §14.11; Culann landed ~2.2 years in with no
  player). All director cadences are provisional until the baseline is ruled on. Lever 4 on the
  Heg-League ceasefire chance stays unapplied by the same logic.
- **Colony income vs the starting debt.** Observed tonight: income from Thar and Daria looked large
  enough to trivialise the 1.8M debt. Not a ruling — Sean wants to see the two running side by side
  in an Exile-start campaign (the debt is absent from Nex-start runs) before deciding.
- **Heg-League on-screen behaviour.** Sean has watched a few League–Hegemony raids: raiders seize
  the local beacons and will attack enemy stations. Watch item: interdiction raiders list every
  station in `doNotAttack` and should steer clear; surgical strikes deliberately do not. If the
  station attacks were interdiction ops, the suppression is not holding under Nexerelin — check
  before Pass 4. Recovery convoys and the ESCALATION blockade are unbuilt, so raids "all over the
  map" during a crisis is the current expected shape.

**4. Idea to explore — named NPC governors with a stability skill (Sean, 2026-09-20).** Sean plans
for each refugee enclave to be governed by a specific named NPC, and wants those four governors to
carry the +2 Stability that a player-governed colony enjoys, either by granting them the same
ability or by a bespoke skill only they have. **To-do, not a ruling.** Mechanism pointers from the
vanilla source, so the exploration starts in the right place:
- Player-governed effects are ordinary skills with the `GOVERNED_OUTPOST` effect scope
  (`hypercognition.skill`, `fleet_logistics.skill`), applied only to colonies the player runs
  personally. The exact source of the +2 Sean observed still needs verifying (skill vs base rule).
- NPC administrators carry admin skills (`industrial_planning` etc.) whose `MarketSkillEffect`
  applies to the market they are assigned to. **That is the natural seam:** an `exile_`-namespaced
  admin skill with a flat stability effect, given only to the four named governor persons and never
  offered in the hiring pool. Vanilla `AICoreAdmin`-style flat bonuses are the precedent.
- Fits the Maxios rulings above as a quiet, lore-carried counterweight to the accessibility sinks,
  and gives the future refugee-trust quest line four faces to talk to. Sequence with the enclave
  decivilization rulings; requires the governors to exist as persons first.
