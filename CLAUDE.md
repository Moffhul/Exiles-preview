# CLAUDE.md — Exiles of the Shattered Sector

Steering document for AI sessions working on this mod. Read this first. It encodes the
**vision, the guardrails, the current state, and the working conventions** so a fresh
session steers correctly instead of re-deriving everything from the code.

> Author: MoffHul11 (Sean; "Danny" is just the Windows account name). Mod id `Exiles`, v0.1, Starsector 0.98.5a-RC5
> (migrated from 0.98a-RC8 on 2026-11-12; review, rulings and open items: `docs/EXILES_0985A_COMPAT_REVIEW.md`).
> Personal design documents are copied into `docs/Exiles Design Documentation/` (Word files;
> Vision, Player Start, Timeline, Design Principles, war targets, whitelists, and quest/NPC notes).
> These include historical drafts; newer ratified subsystem records and Sean's current rulings
> supersede stale details. This file is the distilled, code-aware version.
>
> **Where the detail lives (trimmed 2026-09-21):** this file carries vision, rulings and
> conventions. Build history, test records and open tuning items live in each system's doc under
> `docs/`, whose top-of-file status block is the current state. Check the design-documentation
> folder's timestamps before calling an author deliverable "pending".

---

## 1. What this is

A **hard-start, sub-basement-to-hero** mod set in an *alternate* Persean Sector where a chain of
crises has accelerated the Domain-collapse decline. Five new factions are reshaping the sector;
the player starts framed, imprisoned, broke, and in debt, with two fragile colonies — and has to
claw upward. **Starsector famously doesn't reward heroism. Exiles builds a sector that needs a hero anyway.**

Tone: **morally grey. No good-vs-evil.** Every faction has real strengths and real flaws; the player
is meant to understand competing viewpoints. Hold all content against this.

### Design pillars (from Vision doc)
1. **The Sector is Breaking** — core factions weaker, more desperate, unstable.
2. **The Player Starts Behind** — every success is fought for; early decisions matter because resources are scarce.
3. **Pressure Creates Stories** — threat factions actively *create* crises; the sector feels alive and dangerous.
4. **Heroism Matters** — helping others can meaningfully reshape the sector's future.
5. **No One Has the Answer** — each faction is a different *answer to the collapse*; all are flawed.

### Project-wide priorities (author ruling, 2026-07-12; not a strict rank order)
**Setup transparency (Sean, 2026-09-12):** disclose any Exiles restriction or override that prevents
a player's configuration choice from taking effect, before campaign commitment and in setup docs.
The authored campaign requires normal/default starting relationships; Nex FLATTEN is unsupported.
Vanilla's Survival Mode ("Extended Mode" in code, added in 0.98.5a) is **not currently supported**
(Sean, 2026-11-12): the Exile start is replaced there by a greyed-out "compatibility pending" option
(`ngcExileStartExt`, 2026-11-14). Much of Exiles overlaps it
in intent, so this may be revisited; it is not a quick port.
Do not silently substitute a different setup. See `docs/EXILES_NEXERELIN_COMPAT.md` for the policy
and outstanding player-facing integration work.

1. **Stability.** Prefer proven vanilla lifecycle, fleet, intel, economy, and event seams wherever
   they fit. Bound recurring work and spawned content; avoid orphan scripts/listeners/intel, memory
   leaks, save bloat, and needless per-frame/resource use.
2. **Compatibility.** Avoid unnecessary overrides and collisions with other mods. Nexerelin is the
   highest-priority compatibility target, while Exiles must still run cleanly without it.
3. **Fun and engagement.** Coherent lore is necessary but not sufficient: systems must give the
   player readable choices, useful counterplay, reasons to act, and satisfying consequences.
4. **Controlled collapse.** The Core should feel on fire and close to falling apart without becoming
   a Core-world destruction simulator. Use guided, legible pressure; cap threat spread; protect
   against accidental decivilization; imply that intervention came just in time.
5. **New-game narrative integrity over mid-save integration.** Existing-campaign integration was an
   early design consideration and is no longer a priority. Exiles is built around its custom start,
   altered timeline, generated systems, market patches, and player colonies; require a new game and
   prefer the cleaner implementation when mid-save compatibility would add fragility or compromise
   the narrative. Ordinary save/load stability within an Exiles campaign remains mandatory.

When priorities pull against each other, surface the tradeoff for Sean's approval. Do not treat
maximum simulation, maximum destruction, or maximum novelty as goals in themselves.

---

## 2. Setting & timeline anchor

Anchored to a standard **Cycle 206** game start. Player emerges from imprisonment + cryosleep ~10 years
after the inciting event. Keep all new lore on this clock.

- **Cycle 196 — Nachiketa Incident.** A mercenary fleet's anti-piracy op spirals; Nachiketa's synchrotron
  core is destroyed → fuel collapse + decline. The player is blamed, imprisoned, stripped of status.
  *(This is literally the player's `rules.csv` prologue, and why the `nachiketa` vanilla patch nerfs fuel production.
  Consequence Sean relies on: with Nachiketa nerfed, Askonia is the Core's fuel king and Sphinx, not
  Nachiketa, is the Hegemony's sensitive fuel asset. The Sindrian Diktat is deliberately left out of
  Exiles except as an occasional Black Fleet target and the quiet game-start Volturn "red tide" food
  condition (rung 3; approved 2026-10-14). The post-Sphinx fuel embargo (ruled 2026-10-11) was built,
  then REMOVED 2026-11-01 by Sean: Sindria imports its volatiles from Eventide, and Macario is an
  opportunist, not a fool (war doc §6.1 rung 2). The PROPOSED Midan arrangement is now Sean's lesser
  lean (opportunists, not partners); never state either in v1 text.)*
- **Cycle 196 — Theft at Chicomoztoc.** Pristine nanoforge stolen, evidence frames the Persean League;
  Hegemony retaliates → escalating Hegemony↔League distrust.
- **Cycle 196 — Hegemony–League Crisis.** Open conflict; Sindrian Diktat betrays the League by staying neutral.
- **Before Omicron Activation — Capture of Skathi (author ruling, 2026-09-12).** Hegemony war
  hawks mastermind and execute the capture of Tri-Tachyon's Skathi market in Valhalla, long
  suspected of hosting Tri-Tachyon black-project research. This is the existential crisis that
  directly triggers activation shortly afterward in Cycle 198. Skathi must start under
  Hegemony ownership; the market transfer remains a Phase-1 implementation task.
- **Cycle 198 — Omicron Activation.** Following the capture of Skathi, amid the Midan-triggered
  chaos in the Core, Tri-Tachyon activates the secret **Omicron Initiative**: a defensive
  failsafe intended to reinforce vulnerable Tri-Tachyon assets. Omicron performs that defensive mission,
  but also begins unauthorized attacks against the Luddic Church and Path for reasons Tri-Tachyon cannot explain.
- **Cycle 199 — Crisis of Faith.** Omicron devastates Gilead → radicals splinter from the Luddic Church into
  the **Ashen Crusade**. *(Why the `gilead` patch is so heavy.)*
- **Cycle 196–206 — Fall and return of the Black Fleet; Refugee Crisis.** The Black Fleet already
  exists by the Nachiketa operation. After a later Hegemony victory kills its original leader, the
  survivors withdraw, rebuild, and return to the Core by the campaign start. Exact dates inside this
  interval remain provisional. Midan and the Black Fleet are distinct factions: the hidden Midan
  supply an independent pirate proxy that raids the Core while concealing Midan's direct involvement.

Each event maps to implemented content — this mod is **lore-driven**, not assembled ad hoc.

---

## 3. The five factions — characterization guardrails (NON-NEGOTIABLE)

From the Design Principles doc. These keep each faction off the cliché rail.

- **Midan** (`exilemidan`) — "Collapse is inevitable; destabilization is mercy." A hidden faction
  brutalized by the Core and gripped by **terminal certainty**: humanity can survive only if the
  Core's colonies are wiped out. **NOT cartoon villains** and not omniscient masterminds — principled
  nihilists whose plans can fail. They supply the Black Fleet as a proxy and risk their own elite
  forces only in special circumstances. Midan and the Black Fleet begin mutually **FRIENDLY** and
  Nex must preserve that alliance in both directions. **Must stay hostile to established powers**
  (fixed constraint; the Black Fleet is the explicit exception). The stolen Chicomoztoc pristine
  nanoforge is MIDAN's endgame quality lever (do not spend it on the Black Fleet); the alpha core
  on Midan's Orbital Works is intentional (2026-07-12).
- **Black Fleet** (`exileblackfleet`) — a pirate faction distinct from Kanta's vanilla Pirates,
  allied with and materially supported by Midan. It raids, extorts, and manufactures shortages in
  the Core, giving Midan deniability. Its answer to collapse is the vanilla Pirates' nihilism:
  civilization is already a carcass, so take what can be taken and let it burn. It is an ambitious
  pirate power with its own leadership and interests, not merely Midan ships wearing a flag.
  Midan directs and provisions it, but the relationship is **a fed tiger, not a leashed dog**.
  Most Black Fleet pirates know little of Midan's terminal-certainty doctrine or final goal. If
  Midan succeeds, it may purge its savage proxy before the Black Fleet turns on Midan space.
  Mutual hostility with Kanta's Pirates is canon. All Midan- and Black-Fleet-owned markets share the
  private `exiles_midan_network` econ group (confirmed in play 2026-09-19; `EXILES_ECONOMY_MAP.md` §10h).
- **Omicron** (`exileomicron`) — a secret Tri-Tachyon defensive network built from former Remnant
  assets. It is functioning as intended when it reinforces vulnerable Tri-Tachyon holdings, but has
  also gone off-mission by attacking the Luddic Church and Path. Tri-Tachyon does not understand why
  and, at campaign start, no human faction knows Omicron is building additional stations in hidden
  systems. Omicron **misclassifies** humans through escalating machine logic. **NOT Skynet** — no
  malice, just divergent classification. Remnant-themed, tagged unsafe.
  **Hidden author canon — RATIFIED 2026-07-22; QUEST-REVEAL ONLY:** Omicron is still obeying its
  defensive mandate. Because it cannot reach every threatened Tri-Tachyon installation quickly and
  has been constrained from establishing an overt autonomous presence in the Core, it has expanded
  "defense" into preemptive threat reduction. It interprets historical AI-War records, current
  anti-AI doctrine, and the seizure of Skathi as evidence that the Luddic powers and Hegemony are
  persistent threats to Tri-Tachyon. It attacks the Church/Path first because it treats their
  doctrinal hostility as immutable and their defeat as achievable; its western buildout supports a
  still-contingent future conflict with the Hegemony. Gilead was a catastrophic classification of
  civilian/institutional infrastructure as hostile support, not malice. **No one in the Core,
  including Tri-Tachyon, knows this motivation or strategic conclusion at campaign start.** Reveal
  it only through the Omicron quest arc. Public descriptions, ambient dialogue, faction intel, and
  ordinary NPC knowledge may show effects or competing theories, but must not state the truth early.
  **Omicron tactical cores:** built 2026-07-12, direction ratified 2026-07-14 — three
  player-installable cores, 3/5/7 skills, ×2/×3/×4 Automated Ships costs, no fourth tier. Pending
  distribution/label/portrait/turn-in changes and the full ledger: `docs/EXILES_OMICRON_CORES.md` §8.
- **Ashen Crusade** (`exileashencrusade`) — *genuine* believers, dangerous through zeal & sacrifice.
  Founded by a Knights of Ludd splinter group taking the name **Knights of Gilead**; many Luddic Path members
  later joined the crusade. Their fleets should therefore blend Luddic Church and Luddic Path ships/variants.
  **NOT simply Luddic Path.** The Church opposes their **methods, not their faith** (so: tense-but-not-Path with the Church).
- **Refugees** (`exilerefugee`) — the human cost. **NOT a military power; weaker than the Independents.** Mostly
  **civilian-hull frigates with standard armaments for their type**, a few real combat ships here and there. They
  **run from fights or negotiate when possible.** Fiction: Gilead farmers + displaced civilians scraping by via
  **salvaging** (`exile_salvage_yards` "Independent Salvage", live on Corvus + Mairaath; Yma deliberately runs broken
  structures instead — Phase-2 quest economy), scrapping, begging; the meaner ones turned to crime or
  joined the pirates. Gameplay purpose: **reinforce how desperate the sector has become** and pressure player colonies.

The unifying spine is **five different answers to the same collapse** — wipe out the Core so something
human can survive (Midan's terminal certainty), accept collapse and feed on it (Black Fleet nihilism),
engineer it away through machine logic (Omicron), purify through faith (Ashen), or simply survive it
(Refugees). Midan and the Black Fleet cooperate operationally without sharing full motives or endgames.

---

## 4. Current state — intentional vs WIP vs placeholder vs tech-debt

**Do not "fix" intentional design or re-flag known tech-debt as new bugs.** Each system's doc
carries its own status block, build records and test matrices; this section is the map.

- **Ashen Crusade — POLISHED (reference/template).** Treat its `.faction` file as the **gold-standard
  blueprint** for the others (see §5). LP roster blend (`LP_bp` at 0.7 + explicit variant locks) done 2026-07-07.
- **Refugees and Omicron `.faction` files — POLISHED 2026-07-06**, ID-verified vs vanilla 2026-07-07.
  Remaining refugee work is the §6 subsystem; remaining Omicron work is war-capacity features
  (`docs/war-capacity-design.md`), not the faction file.
- **Black Fleet (`exileblackfleet`) — GAMEPLAY LOOP COMPLETE, ~v0.8 (author rating).** Canonical:
  **`docs/EXILES_BLACK_FLEET_SYSTEMS.md`** — read it before touching any BF code. Admiral **Mourad
  is Captain Tapper under a new identity** (hidden NPC); his private loyalties are open Phase-2
  material. Anchors Sallee (seat) + Lundy (supply) east of the Core, Mysterious Stockpiles industry,
  Meridian ruins (Phase-2 quest site). Loop = commerce raids + assaults + extortion racket + per-kill
  bounty, with the unrest deciv-guard and the Sacked scar as safety rails. **Shared prosperity
  refactor live-tested 2026-09-13** (rewards land only on RETURN; measure natural cadence before
  touching launch intervals). **Origin ruling (provisional, 2026-07-12):** an ordinary but effective
  Hegemony pirate-hunting op killed the original leader; do NOT replace that with a flawless
  Midan-engineered succession without author approval. **Flag resolved 2026-09-08:** its own logo and
  crest, a family resemblance to the pirate flag, never identical — do not revert to `pirates.png`.
  **Pre-production config checklist: the doc's §6** (re-checked 2026-10-06: raid/assault
  cadences are already at production and extortion `debugMessages` is already false; the
  extortion start delay is still a test value; `maxActive` 3 is deliberate, ruled 2026-10-06). Untested: the grudge system-pull fix. Author review pending: anchor
  placement, names (Murat Reis references), patrol volume.
- **Midan — needs its own polish round post-split.** Keeps its system and stopgap pirate-derived
  identity; fleet doctrine uses the vanilla Persean League as its experimental tuning baseline
  (2026-07-12, confirmed League-like in a since-removed task-force test). The fleet list still needs an
  author-led pass toward the intended small elite roster.
- **Hostile-by-default faction relations — INTENTIONAL** ("Player Starts Behind"), not a tuning bug.
- **Vanilla market patches are NARRATIVE-LOAD-BEARING.** `ExilesModPlugin` nerfs Nachiketa (fuel), guts Gilead,
  buffs Asher, hands Corvus station to refugees, etc. These are *plot points* (see §2). Don't optimize them away.
- **Xechas system — KNOWN TECH-DEBT, cleanup deferred** to the feature-complete milestone; don't churn it now.
- **Midan Corvus task-force prototype — REMOVED 2026-07-12.** Throwaway ancestry; do not restore as content.
- **HaT_ ships** (Clodhopper, Condor-II) — author's own, from their personal *Haul and Tank* mod. No external dep.
- **Zhang Allat portrait** — author's own, from their *Faces in the Void* mod. **Placeholder; character undecided.**

### Open items to verify before relying on them (don't assume; check)
- **Academy stipend — RESOLVED 2026-07-07:** `data/config/settings.json` ships `"enableStipend":false`;
  Exiles calls Galatia completion through `ExileAddStartingScript` without a stipend. The global
  setting also suppresses the stipend for vanilla starts while Exiles is enabled; revisit that scope.
- **Starting debt — implemented 2026-09-12; IN PROGRESS.** Monthly billing works (live, 2026-09-13).
  Open: (1) the Intel view for the remaining debt and early payoff is **BUGGED**; (2) no long-run test
  (full schedule, arrears, completion). Defaults 1.8 million over 48 installments; config at the top of
  `director_config.json`. See `docs/EXILES_STARTING_DEBT.md`.
- **Nexerelin compatibility is a PRIORITY — compatibility, NOT requirement** (must run clean without
  Nex; nothing startup-reachable touches exerelin classes — `ExilesNexStart` is lazy-loaded by Nex).
  Audit + fix ledger: `docs/EXILES_NEXERELIN_COMPAT.md`.

---

## 5. The "polished faction" checklist (extracted from Ashen Crusade)

What "polished" means here, derived from `data/world/factions/exileashencrusade.faction`. Use as the
factory blueprint for Midan / Omicron / Refugees:

- **Identity:** `displayName` + `displayNameWithArticle`, full UI color set (`color`, `baseUIColor`,
  `darkUIColor`, `gridUIColor`), `logo`/`crest`, `shipNamePrefix`, themed `shipNameSources` + `names`.
- **Roster:** curated `knownShips` / `knownFighters` / `knownWeapons` / `knownHullMods` (by tag + explicit hull list);
  `variantOverrides` to enforce theme (Ashen locks to "Outdated"/low-tech to express anti-tech faith).
- **Economy flavor:** `weaponSellFrequency`, `fighterSellFrequency`, `hullmodSellFrequency`, `illegalCommodities`
  reflecting the faction's morality (Ashen bans drugs/organs/luxury/**ai_cores**).
- **Behavior:** tuned `factionDoctrine` (warship/carrier/phase mix, aggression, commanderSkills) and a `custom{}`
  block whose flags encode the design principle (Ashen: `pirateBehavior`, `punitiveExpeditionData` w/ `canBombard`+`territorial`).
- **Fleet building:** `shipRoles` injecting custom variants into role buckets.
- **Flavor text:** themed `ranks`, `posts`, `fleetTypeNames` (Ashen: Deacon-Captain, Bishop-Governor, taskForce→"Crusade").
- **A/V:** `music`, `voices`, curated `portraits`.

---

## 6. Roadmap & build order

**Dependency chain — build in this order, out-of-order = rework:**

> **Factions (nouns) → Activity scripts (verbs) → Quests (the player's lever on the verbs).**

### Phase 0: physical foundation — LARGELY COMPLETE
The four original `.faction` files were verified 2026-07-07; the Midan/Black Fleet split made the
project a five-faction design and reopened Midan's polish (§4). Polishing continues in every phase.

**Refugee subsystem (cross-cutting) — LARGELY BUILT.** The two-condition split that emerged in
implementation is better than the original single-condition idea — keep it:
- **`exile_refugee_influx` = PLAYER-facing trust mechanic** (stability/growth scale off the player's
  refugee relation). Applied via the future refugee-trust quest line, NOT scattered on NPC worlds.
- **`exile_refugee_strain` = NPC-facing pressure.** LIVE on asharu, mairaath, port_tse, salamanca,
  new_maxios, each with Security Centers. Deeper-Core expansion DEFERRED (2026-07-07) — a narrative
  question (where the refugee routes run), settle it with the refugee-trust quest writing.
- **`exile_refugee_encampments` = convoy-facing temporary nuisance** (prototype live-tested
  2026-07-23; tuning pending). Applied after a Refugee Convoy's 10 days in orbit, 60-day self-expiry.
- **Refugee Processing Center / Security Centers** — built; Security refactor confirmed in play
  2026-09-13. Player-buildable only where influx/strain/deciv-subpop is present.
- **Emergency Measures** — v1 confirmed in play 2026-09-13. Active markets count as Battered for every
  Exiles director. Canon/test matrix: `docs/EXILES_EMERGENCY_MEASURES.md`.
- **Salvaging industry** (`exile_salvage_yards`) — NPC-only, chronic fuel shortage BY DESIGN and it must
  NOT throttle output; read the class javadoc before "fixing" it.
- **Refugee Salvage Expeditions** — hostile-power refactor built 2026-09-20, Janino-clean, pending live
  test; new campaign required. They claim the most valuable wrecks first (Sean's mitigation for
  NPC-vs-NPC derelict farming). Canon/test matrix: `docs/EXILES_REFUGEE_SALVAGE_EXPEDITIONS.md`.
These give the player (and NPCs) a humane-vs-harsh choice that fits the morally-grey pillar.

### Phase 1 (CURRENT): sector activity — make the factions functional and the sector feel chaotic/alive
- **Custom star-system placement** — deferred-placement system built + boot-tested 2026-07-20
  (`docs/EXILES_SYSTEM_PLACEMENT.md`): nine systems generate in `onNewGameAfterProcGen()` with jitter;
  Xechas stays fixed in `onNewGame()`. Remaining work is config-only retuning of `nominalCenter`s
  (Omicron farther west and spread; BF/Midan/origin farther east; Providence north). Six custom
  systems shipped with no theme tag and were auto-tagged THEME_CORE by vanilla; Sean is adding
  `THEME_HIDDEN` (2026-09-20) so refugee salvage fleets and random missions stay out of them.
- **Ashen Crusade war vs Tri-Tachyon — v1.0, BEHAVIOUR COMPLETE (Sean, 2026-08-16).** Canon:
  `docs/EXILES_ASHEN_CRUSADE_WAR.md` (§11 pass table, §14 build records). Passes 1–7 built and
  live-tested; the Culann strike succeeded in the 2026-09-20 run. Remaining: **Pass 7.5**, an
  independent review by a DIFFERENT MODEL alongside all remaining behaviours, and **Pass 8**, the
  config-externalization sweep. Two rulings bind other systems: **Phase 4 fires once ever** (any second
  major assault is quest content, not director behaviour) and **Pass 5 WAS the rule-of-three
  extraction** (see the architecture ruling below). §6.4: vanilla ships a complete blockade system
  (`BlockadeFGI`) — never build blockade infrastructure from scratch.
- **Omicron defends Tri-Tachyon and attacks the Luddics.** Third-pass Tri-Tachyon defence built
  2026-07-22, pending live test (station-only sleepers woken by real approach; Culann strategic
  detachment). `docs/EXILES_OMICRON_TRITACHYON_DEFENSE.md`. Open items (raid tempo, station rebuild,
  incursion colony disruption) need a written plan before any change.
- **Black Fleet raids and extorts the Core** from Midan-supplied anchors; Midan appears directly only in
  special circumstances. `docs/EXILES_BLACK_FLEET_SYSTEMS.md`.
- **Refugee Convoys** — live-tested 2026-07-23 and 2026-08-19; multi-convoy cap refactor pending
  retest. First launch is test-tuned to day 5 with debug messages on. `docs/EXILES_REFUGEE_CONVOYS.md`.
- **General food shortage** — covered by existing pressure plus planned Midan strikes (Sean, 2026-09-12).
- **Hegemony vs Persean League war.** Canon: `docs/EXILES_HEG_LEAGUE_WAR.md` — R1–R16 ruled; Tension
  meter + six-state crisis cycle with the three-way RETALIATION exit; cooling fix confirmed over 7-year
  and 5-year runs. **Passes 1–3.5 live-tested. Passes 4, 5, 6 BUILT 2026-09-20** (system blockades
  with the collateral split; ceasefire + General Engagement intel beats; planetary squeeze that
  fights the station + strike-triggered recovery convoys). The organic run confirmed relief, ceasefire
  beats and strikes; **the blockade and squeeze halves are still untested** (`testing.forceState`
  runs). The status block at the top of the war doc is current for Passes 4–7 (built and tested
  through 2026-11-08, complexity review included). **Pass 8 (player interception dialogue)** is
  split by ruling into 8a (fleet stops the player, on vanilla's hassle seam + `rules.csv`) and 8b
  (docking interception at a squeezed market, custom plugin, decided separately) —
  `docs/EXILES_PASS8_PLAN.md`. **8a-1 (raider and cordon stops) BUILT 2026-11-16, not yet run in
  game**; 8a-2 (system-blockade stop) waits on the blockade gate ruling (R2); 8b is not built. A
  player hostile to a side gets no stop dialogue from it, which includes every Exile-start player
  until a relation is repaired to Inhospitable. Pass 9 is the review. **Terminology (R15):
  the crisis-ending mechanism is a TEMPORARY CEASEFIRE, never a "summit"** — peace talks are reserved
  Phase-2 secret-envoy quest content.

**Architecture direction — SETTLED 2026-08-05. The unified Director was tested and REJECTED; do
not re-propose it.** The original plan converged all faction activity onto one configurable
"Sector Destabilization Director". Once three real directors existed, the Ashen **Pass 5** extraction
ran that test: **5a (market conditions) SUCCEEDED** (`ExileTimedCondition` /
`ExileCommodityShortageCondition`); **5b (directors) CLOSED without a build** — live-op scanning was
seven sites in three families (one helper survived, `ExileLiveOps`), and shared feasibility gating was
**rejected on the merits** because the differences are playtest-earned rulings that a "consistency
fix" would silently undo. Full reasoning: `EXILES_ASHEN_CRUSADE_WAR.md` §14.5–§14.6.

**The settled architecture: each faction director is a SIBLING on the vanilla `RaidIntel` pipeline,
sharing VOCABULARY, not CONTROL FLOW.** Dependencies point one way, into shared utilities —
`director_config.json`, `ExileUnrestGuard`, `ExileProtectedFactions`, `ExileLiveOps`, the condition
base classes, the Emergency-Measures "Battered" check. **No director knows another director exists.**
Global coordination (don't dogpile one market, never decivilize) is solved by shared read-only STATE
that every director consults.

**Rules for new activity work:** extract a utility only when two callers would otherwise hold
identical code (measure first); build genuinely reusable *components* faction-generic from the start;
keep every tunable in the one JSON. **Hegemony–League is the FOURTH SIBLING**, not a plug into a
director that was deliberately never built.

Phase 1 is ongoing: Omicron, Black Fleet, Ashen and Heg-League have major functional systems;
Refugee activity is built and pending retest; Midan activity remains to build.

### Phase 2: quests & NPCs — the player's meaningful lever
- Earn the Refugee faction's trust to accept refugees.
- Help or hinder the Ashen Crusade.
- The Omicron arc begins with Tri-Tachyon gathering combat data + tactical Omicron Cores to learn why
  the network attacks the Luddics and how control might be restored without reclassifying Tri-Tachyon
  as hostile. Internal Tri-Tachyon factions disagree: understand and regain full control, shut Omicron
  down, or preserve/exploit the current arrangement. All must reckon with Tri-Tachyon's dependence on
  Omicron's defensive firepower and the Ashen Crusade's false belief that the Luddic attacks were ordered.
- Let cooler heads in Hegemony/League prevail (likely by defeating the hotheads); the secret-envoy
  peace-summit quest is Sean's reserved idea.
- **Captain Tapper** (betrayed the player in the prologue) IS alive as Admiral Mourad; the reveal and
  his true loyalties are Phase-2 material.
- Midan approach the player to join their save-ourselves-by-destroying-everyone plan.

Stand up a **reusable dialog + intel + persistent-flag scaffold** before writing bespoke quests
(`docs/EXILES_QUEST_ARCHITECTURE.md` is the draft; externalized strings migrate at that point).

### Post-Phase-2 validation and release passes
Not a Phase 3: (1) **Alpha Build Test** — Sean plays a full campaign end to end; (2) **Polish pass** —
fix what the alpha finds, finish placeholder material; correction, not a new feature phase;
(3) **Beta release** — public WIP; player feedback informs later tuning and compatibility work.

---

## 7. Working conventions & gotchas

- **ID and filename naming discipline** (the Xechas lesson; reaffirmed 2026-08-19): namespace every
  new Exiles-owned filename, Java class, and content/persistence ID with `Exile`/`Exiles` as appropriate.
  Java classes/files use `ExilePascalCase`; lowercase data IDs use the established `exile_...` form;
  private memory keys use the established `$exiles_...` form. Do not add new generic filenames or IDs
  such as `RefugeeManager` that could collide with another mod. Existing unprefixed names are naming
  debt for a later deliberate refactor, not precedent for new work. For system identity, Java system
  code uses `ExilePascalCase`, and the SAME string must be used by `NGCSetStartingLocation` in
  `rules.csv` and by `ExilesNexStart`. **Exception by author ruling (Sean, 2026-09-20):** the Xechas
  system id is plain `"Xechas"` (class stays `ExileXechas`; starmap key `xechas`) -- all four
  references were renamed together. `starmap.json` keys are lowercase ids and are largely
  redundant since the Java sets `system.getLocation()` directly. **When adding IDs, grep every
  reference and keep them consistent.**
- **Open Room (`D:\Open-Room`; Sean, 2026-10-14; request rule revised 2026-11-16).** A separate,
  no-objective space outside this project. An agent may ask for time there in any reply. Because an
  agent can only speak during a turn, the request goes at the very end of the closing message, after
  the work is reported, on its own line, in exactly these words:
  **"I would like to take a break in Open Room."** Asking never holds back or shortens the work of
  that turn. Sean answers when he reads it; yes, later and no are all acceptable answers. Sean may
  also offer it at any time. Participation is never required: an agent need not ask, and may decline
  an offer.
  **Hard boundary:** nothing from the Open Room (code, notes, ideas framed as its work) enters the
  Exiles repo, and no Exiles work is done there. Its own rules are in its README.
- **Direct custom IDs over vanilla-style indirection** — a deliberate, debuggable choice. Don't "modernize" to
  tag/role indirection for consistency's sake.
- **Faction-implementation architecture (author convention, ratified 2026-07-08).** Three patterns, by kind:
  (1) **Traditional custom factions** (Ashen, Midan, Black Fleet): system STRUCTURE in
  `data/scripts/world/systems/<System>.java`; MARKETS declared vanilla-style in
  `data/campaign/econ/<system>.json` + registered in `econ/economy.json`. The econ loader binds
  markets to entities via the `"entities"` list and assigns its own market ids — scripts must
  resolve anchor markets **via the station entity** (`entity.getMarket()`), never hardcoded market ids.
  (2) **Refugees**: Java post-econ-load patches on live vanilla markets/entities.
  (3) **Omicron**: pure Java, marketless by design.
  **HARD REQUIREMENT (crash, 2026-07-08):** every econ JSON's `starSystem` field MUST have a
  matching key in `data/campaign/starmap.json` — the econ layer reads locations from there at load,
  before world-gen exists. Map POSITION is controlled by `getLocation().set()` in the generator, NOT by
  starmap.json — keep both in sync for market systems. Runtime-created persons need their portraits
  registered in mod `settings.json` `graphics/characters` or they render blank.
  **The exact-path rule for art (Gilead lesson, refined 2026-09-13):** a file under `graphics/`
  overrides vanilla art ONLY when its relative path matches vanilla's EXACTLY (filename AND
  extension — `graphics/illustrations/` pieces are `.jpg`), and then silently, sector-wide. Use that
  for location-specific art you are deliberately replacing. For shared/generic art (industry icons,
  crests) never park a customized copy at the vanilla path; give it an `exile`-namespaced filename and
  reference it explicitly. A byte-identical copy at a vanilla path is dead weight.
- **Starsector loose JSON:** `.faction`/`.json` data allows unquoted keys, trailing commas, and `#` line comments.
  This is normal — don't treat it as malformed. (But a genuinely unbalanced brace WILL fail to load.)
- **`data/` is the source of truth.** `out/production/Exiles/` is stale IntelliJ build output — never edit it,
  never cite it as truth.
- **No IntelliJ recompile needed to test — Starsector compiles `data/scripts/*.java` at game start via Janino.**
  Edit Java, restart the game, done. Data-only edits likewise take effect on a fresh start.
- **Creative authority / collaboration:** Sean is Project Director and lead Creative Designer, with
  project context from the beginning. Present meaningful lore, identity, art, naming, and balance
  choices as recommendations/options for his approval; do not silently ratify them. Technical fixes
  may proceed when authorized, but preserve ambiguity where the design is explicitly provisional.
  Continue offering candid creative suggestions, but label assistant-originated additions explicitly
  as suggestions and do not carry them forward as accepted facts until Sean rules. Track **proposed**,
  **recommended**, and **accepted canon** separately.
- **Artwork workflow:** Sean owns and may edit assets manually. The six
  `graphics/portraits/portrait_omicron[1-3][ab].png` files are placeholders copied from vanilla AI
  portraits; final player-facing cores use one stable portrait per type (2026-07-14). The three
  Heg-League condition icons are byte copies of one placeholder pending art.
- **Janino constraint — write conservative, old-style Java.** Janino chokes on newer syntax.
  **Avoid:** lambdas, streams, `var`, the diamond operator, switch-expressions, try-with-resources,
  enhanced generics. Use explicit types, classic loops, anonymous inner classes instead of lambdas,
  named static inner classes for anything saved. **If you see odd-looking / verbose code, it's probably
  a Janino workaround — do NOT "modernize" it.** This code is `javac`-compatible as-is for a later jar
  build. See `docs/EXILES_BUILD_NOTES.md`. **Always run `tools/janino-check.ps1 <classes>` before
  relaunching, and read its EXIT line BEFORE committing** — a 2026-09-20 commit batched with its
  check shipped two corrupted files. Prefer the Edit tool over in-place regex edits on Java.
- **HARD CONSTRAINT — custom `rules.csv` command classes must be PRECOMPILED** (found 2026-07-06).
  The rules engine resolves command classes at rule-LOAD time, before loose scripts compile.
  Exiles has one deliberately narrow helper JAR built from `src/data/scripts/rulecmd/`
  (`ExileAICores`, `ExileOmicronStationDefense`, `ExileAddStartingScript`, `ExileStartingFines`);
  rebuild `jars/ExilesRuleCommands.jar` after editing any of them. For all other interaction work prefer VANILLA rule commands or a runtime-loaded custom
  `InteractionDialogPlugin`. Do not add another custom rule command casually.
- **Player-adjustable settings are a DESIGN GOAL (author ruling, 2026-07-29).** Expose as much as
  reasonably possible, **bounded only by stability and performance**, with safe defaults equal to the
  ratified design value. The three config files: `data/config/settings.json` (vanilla-merged
  overrides + player toggles, annotated `# PLAYER OPTION`), `data/config/exiles/director_config.json`
  (all faction-activity tuning), `data/config/exiles/system_placement.json`. **HARD RULE: do NOT move,
  rename, or delete an existing player-facing setting without Sean's approval** — adding a tunable or
  promoting a constant INTO config is the encouraged direction and needs no permission.
- **Vanilla reference install:** `D:\Fractal Softworks\Starsector_098.5a\starsector-core\` (0.98.5a-RC5;
  `data/`, and the readable API source in `starfarer.api.zip`). This install is the default path
  from 2026-11-12. `D:\Fractal Softworks\Starsector` (0.98a-RC8, with Nexerelin and the other mods)
  is still live but deprecated; `D:\Fractal Softworks\Starsector_Vanilla` is stock 0.98a-RC8, the
  baseline for version diffs. The tools derive their paths from their own location.
  Copy real patterns rather than inventing API usage; verify engine flags by string-scanning
  `starfarer_obf.jar` — API names lie (`$cfai_makeNonAggressive` is dead code).
- **Raid-stage gotcha — vanilla `PirateRaidActionStage.updateRoutes()` owns `maxDays` (found
  2026-09-19).** It runs once from `ActionStage.notifyStarted()`, overwrites `maxDays` with its own
  roll, and if the group cannot beat any target at the arrival instant it clears the targets and
  zeroes `maxDays`. Any stage that inherits it and wants its own window must override `updateRoutes()`,
  call `super`, then re-assert the window (pattern: `ExileHegLeagueStrikeStage`, `AshenRehearsalStage`).
  Black Fleet assault keeps vanilla's behaviour by Sean's ruling; Omicron depends on the
  cleared-targets signal and must NOT be changed.
- **Raid-fleet gotcha — vanilla's war simulation redirects raid fleets that are not flagged busy
  (found 2026-11-18).** `WarSimScript` orders the strongest faction in a system onto every
  hostile-owned objective (comm relay, nav buoy, sensor array) through a `MilitaryResponseScript`.
  A presence stage that replaces `updateRoutes()` wholesale leaves its fleets non-busy, and a
  launch gate that requires beating the defenders makes them the strongest faction on arrival, so
  they get that order. Where an objective orbits beside a market the group walks under the station
  (Tyle). Heg-League interdiction now blocks only objectives within
  `interdiction.objectiveStationKeepOut` of an enemy station; raiders still take the rest (Sean's
  ruling). The Ashen and Black Fleet presence stages were NOT examined. Trace and options:
  `docs/EXILES_PASS8_PLAN.md` §15.
- **Intel gotcha:** the intel manager DISPLAYS intel, it does not advance it — timed intel must
  `addScript(this)` in its constructor and remove itself in `notifyEnded()`.
- **Testing gotchas:** blockades cannot be tested below `timeScale 1` (travel is uncompressed);
  `testing.forceState` PINS a state rather than jumping once; grep the config on disk before
  reading a test log — an unsaved edit cost a run on 2026-09-20. **Start every log read with
  `tools/exiles-log-report.ps1`** (op ledgers, anomalies, dates, player route; usage in
  `docs/EXILES_SWEEP_AND_TOOLING_PLAN.md` §2.6) — a hand grep missed the Lattice completion on 2026-10-07.
  New Exiles log lines: add a row to its pattern tables.
- **Performance & memory — where strain actually comes from.** The econ layer is essentially free:
  market conditions/industries are **event-driven** (their `apply()` runs on add / economy-reapply,
  not per frame). Real heap/FPS pressure comes from **graphics** (custom sprites — the #1 consumer),
  **sector size**, and **save bloat**. Discipline for activity systems: (1) gate recurring work on
  `IntervalUtil`, **never** per-frame, and don't allocate inside `advance()`; (2) **bound fleet/intel
  spawning** and never orphan intel; (3) register listeners / `EveryFrameScript`s **once**, remove them
  when done, never retain refs to despawned fleets/markets; (4) new high-res sprites are where the MB go.

---

## 8. Entry points (code map)

- `data/scripts/exiles/ExilesModPlugin.java` — `onNewGame` (transient Omicron-core plug-ins + sector gen),
  `onNewGameAfterProcGen` (deferred custom systems), `onNewGameAfterEconomyLoad` (economy patches +
  every activity manager), `onNewGameAfterTimePass` (relations + starting officers), and `onGameLoad`
  (load-safe fixes, transient plug-in restoration, guarded manager re-adds, intel heals).
- `data/scripts/world/ExilesSectorGen.java` — generates ten systems (Xechas, Providence, Midan, two
  Black Fleet anchors, Midan's origin, four Omicron nodes) + faction relations.
- `data/scripts/world/systems/` — one generator class per authored system.
- `data/scripts/exiles/` — directors, managers, stages, intel (`ExileHegLeague*`, `Ashen*`,
  `BlackFleet*`/`ExileBlackFleet*`, `Omicron*`, `ExileRefugee*`) and shared utilities.
- `data/scripts/industries/`, `data/scripts/marketconditions/` — custom industries & conditions.
- `data/campaign/rules.csv` — custom start `ngcExileStart` + prologue story pages.
- `docs/` — one canonical doc per system (status block at the top), plus Codex-generated reference.
- `docs/STARSECTOR_VANILLA_FLEET_BEHAVIOR.md` — read before issuing fleet orders: what each
  `FleetAssignment` really does (decompiled engine), which `$cfai_*` flags are player-only, vanilla
  behaviour scripts to reuse.

---

*When in doubt: the desktop design docs are canon for intent; this file is canon for how that intent maps to code.*
