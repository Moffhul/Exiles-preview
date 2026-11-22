# Pass 7 Build Plan — Midan's false-flag ladder + the Black Fleet proxy

> **Status: APPROVED by Sean 2026-10-24 (with the §1.5a amendments). NOTHING BUILT YET.** Drafted 2026-10-20. §1 (the strike-fleet
> roster) is for discussion first; §2 onward lays out the whole build so the roster can be judged in
> context. Every design point here was ruled in `EXILES_HEG_LEAGUE_WAR.md` §6.1–§6.3 (2026-10-08 →
> 2026-10-19) unless marked **NEW (needs ruling)**. Lore context: `EXILES_LORE_HISTORY.md`.
> Format follows the Pass 4 / Pass 6 plans (war doc §14.9, §14.11).

## 0. Rulings index (where each decision lives)

| Topic | Ruled in |
|---|---|
| Ladder, cap 3, permanence escalates | war doc §6.1 TARGET RULINGS |
| Rung 1 Citadel Arcadia: base + station 1y, spaceport 6mo, relief ≤ 6mo | §6.1 rung 1 |
| Rung 2 Sphinx: battlestation at game start; fuelprod 27mo, battlestation + base 18mo, spaceport 6mo; relief ≤ 9mo; Diktat embargo 24mo, quest early-lift flag | §6.1 rung 2 |
| Rung 3 Eventide: array, mirrors + shades, size 7 → 6, farmland scorched, art swap, on-screen sequence (b), bombardment last; Volturn red tide −2 at game start | §6.1 rung 3 |
| Strike fleet: 2× measured defence, League flag, `$noRepImpact`, elite, launch from Lundy → Sallee → Midan home; hyperspace assembly + warning window SUPERSEDED 2026-10-30 by the sneak attack (§7) | §6.1 STRIKE FLEET, FAILURE AND TENSION |
| Failure: no wound, +12, rung spent; tension raise-to-at-least 50 / 65 / 88; no weariness | same block |
| Trigger: opportunistic score, readiness pacing (yr 1, rebuild 2y / 3y), impatience, test pin | §6.1 TRIGGER GATE |
| Landing in each war state | §6.1 LANDING IN THE WAR CYCLE |
| War off / Nex | §6.1 WAR OFF AND NEXERELIN |
| Vow, Cunningham person, Kazeron hook | §6.1 CUNNINGHAM'S VOW AND THE KAZERON HOOK |
| BF proxy: typed directive key, 0.5 roll at the writer, convoy ambush op, numbers | §6.2 PASS 7 PROXY DESIGN |
| Rung-1 "witness" hooks, return to Lundy's jump point, custom variants, ships recoverable | 2026-10-20 session (recorded here, §1 and §2) |

---

## 1. The strike-fleet roster — FOR DISCUSSION FIRST

### 1.1 What the rulings already fix
- **League-appearing, on no roster.** Fleets are built from an **explicit list of Exiles variants**,
  not from any faction's doctrine, so nothing in the League's markets ever sells them, and "League
  hulls fitted the way no League yard fits them" is a Phase-2 clue.
- **Every ship gets Insulated Engine Assembly** (`insulatedengine`), **S-modded** on combat hulls,
  a normal hullmod on logistics hulls with spare OP. Purpose: a small sensor profile, so the fleet
  is seen late — the "powerful unknown fleet". **S-mod pattern (Sean's Hound example,
  `data/variants/hound_exile_Starting.variant`): the hullmod is listed in BOTH `permaMods` AND
  `sMods`.**
- **Elite:** no D-mods (an explicit list bypasses faction quality rolls), good officers.
- **Recoverable** (Sean, 2026-10-20): no unrecoverable flags. An S-modded Insulated Engine is a
  small prize and costs the player one of their own S-mod slots.
- **Sized by measurement:** at launch, the fleet must reach **≥ 2.0× the target system's measured
  defence** (patrols + the target's station, the squeeze gate's measure) **under an FP ceiling**.
  If the ceiling is hit first, the rung **does not launch** (it waits and re-checks).

### 1.2 The candidate hulls (vanilla `persean_league.faction` knownShips, `ship_data.csv`)
Combat and support hulls only; civilian hulls listed separately. FP = fleet points.

| Size | Hull | Role | FP | Vanilla base variants to copy (non-faction) |
|---|---|---|---|---|
| Frigate | Vigilance | Missile frigate | 5 | `vigilance_Standard`, `vigilance_Strike` |
| Frigate | Brawler | Gunship | 7 | `brawler_Assault`, `brawler_Elite` |
| Frigate | Monitor | Shield tank | 5 | `monitor_Escort` |
| Destroyer | Hammerhead | Destroyer | 10 | `hammerhead_Balanced`, `hammerhead_Elite` |
| Destroyer | Sunder | Energy destroyer | 10 | `sunder_Assault`, `sunder_CS` |
| Destroyer | Drover | Light carrier | 11 | `drover_Strike`, `drover_Support` |
| Cruiser | Falcon | Light cruiser | 12 | `falcon_Attack`, `falcon_CS`, `falcon_Escort` |
| Cruiser | Eagle | Cruiser | 14 | `eagle_Assault`, `eagle_Balanced` |
| Cruiser | Gryphon | Missile cruiser | 14 | `gryphon_Standard`, `gryphon_FS` |
| Cruiser | Heron | Fast carrier | 14 | `heron_Attack`, `heron_Strike` |
| Cruiser | Champion | Heavy cruiser | 15 | `champion_Assault`, `champion_Elite` |
| Capital | Conquest | Battlecruiser | 24 | `conquest_Elite`, `conquest_Standard` |
| Capital | Pegasus | Battleship | 30 | `pegasus_Balanced`, `pegasus_Strike` |
| Logistics | Prometheus | Heavy tanker | 12 | `prometheus_Super` |
| Logistics | Phaeton | Tanker | 6 | `phaeton_Standard` |
| Logistics | Colossus | Heavy freighter | 7 | `colossus_Standard` |

Excluded as bases: `_LG_` (Lion's Guard), `_xiv_` (Hegemony XIV), `_d_` (damaged), `_p_` (pirate),
`_DEM` (Tri-Tachyon-flavoured missiles) — each would muddy the false flag.

### 1.3 Proposed composition (Claude's original SUGGESTIONS — SUPERSEDED by §1.5a: one recurring fleet)
- **The flagship escalates with the ladder** — the fleet visibly grows each time, which also fits
  the lore (Midan rebuilding a bigger force between blows):
  - Rung 1 (Citadel Arcadia): a **Champion** heavy cruiser leads.
  - Rung 2 (Sphinx, now with a battlestation): a **Conquest** leads; **Gryphons** for the station.
  - Rung 3 (Eventide): a **Pegasus** leads, with a Conquest.
- **Body:** cruisers (Eagle, Falcon, Champion, Gryphon) as the core; destroyers (Hammerhead,
  Sunder) and frigates (Brawler, Vigilance) as the screen. Roughly League-doctrine proportions.
- **Carriers (Heron, Drover):** a small share, or none? The League fields them, but a carrier-heavy
  strike force reads less like a surgical raid.
- **Logistics:** one tanker (Prometheus or Phaeton) for the long haul from the east? Realistic, it is
  exactly the sensor-heavy hull Insulated Engines matter most on, and it is a non-combat prize. But
  it adds a ship that cannot fight.
- **Loadouts: copy proven vanilla loadouts verbatim** from the bases above, add the S-mod, give each a
  new id. Balance stays vanilla-tested and the work is mostly data. Hand-tuned loadouts can come later.
- **Variant naming:** `exiles_midan_<hull>_<role>` (e.g. `exiles_midan_eagle_assault`), files in
  `data/variants/exiles_midan/`. Display name ("Customized", like the Hound) is Sean's call.

### 1.4 How the fleet is assembled (the composer)
1. Measure the target system's defence; compute the required strength (2× defence).
2. Add the rung's flagship (and, for rung 3, the second capital).
3. Fill from a weighted pool — cruisers first, then destroyers, then frigates — until the fleet's
   strength reaches the requirement or the next ship would break the rung's **FP ceiling**.
4. Requirement met → launch. Ceiling hit first → no launch; log both numbers; re-check later.
5. Officers on the flagship and the larger hulls; a commander for the flagship.

**Build-time checks (NEW, flagged):** vanilla fleets can run a "fleet inflater" that re-rolls
loadouts and adds D-mods — the composer must make sure **no inflater runs over these fleets**.
Every hull, weapon and hullmod id in the new variants is validated against vanilla data (one typo
fails the load).

### 1.5a Rulings so far [Sean, 2026-10-20]
- **Carriers: few to none** — Midan lacks the League's manpower.
- **Logistics: yes** — one tanker plus a couple of freighters (e.g. two Buffaloes) to suggest
  resupply; they get Insulated Engine Assembly as a **normal** hullmod only (costs 3 / 6 / 9 / 15 OP
  by hull size; check each stock variant has the spare OP).
- **Loadouts: copy vanilla verbatim** for v1, adding only the S-mod.
- **Display name "Customized"** on every variant carrying an S-mod — a subtle clue: the player's own
  starting ships (the Exiles Hound) carry the same label, which hints at a link to the Nachiketa
  incident.
- **ONE RECURRING FLEET [RULED — Sean's proposal]**, replacing §1.3's escalating flagships. The
  wounds already escalate; the fleet builds *recognition* — the player meets "that fleet again", and
  it becomes a character for a later showdown quest. Lore: Midan's single elite strike force,
  repaired and replenished between rungs (the readiness rebuild); Midan lacks manpower.
  - **Flagship: a Conquest named "Ajax"** (in game, **PLS Ajax** — the League's vanilla ship-name
    prefix is `PLS`). If the player destroys it, the next appearance carries the same name, as navies
    rename a new ship after a lost one (the Royal Navy reused *Ajax* many times). Why the Conquest
    (vanilla data, 2026-10-20): burn 8 matches the escorts (a Pegasus would drag the fleet to burn 7),
    24 FP fits the ceiling (Pegasus 30), deep flux and sustained broadside fire suit a surgical
    strike; the Gryphons supply the missile burst against stations. Note: both hulls are generic
    midline ships on the League's known list; neither is League-specific.
  - **FP ceiling ≈ 300, about 24 ships** (vanilla caps NPC fleets at 30 ships, `maxShipsInAIFleet`;
    League hulls are cheap in FP, so 450 would be ~38 ships — a horde, not an elite force).
    Indicative composition (~280 FP): Conquest "Ajax"; 2 Champions, 3 Eagles, 3 Falcons, 2 Gryphons;
    4 Hammerheads, 3 Sunders; 3 Brawlers; **1 Phaeton tanker** (burn 8 — a Prometheus is burn 6 and
    would slow the fleet) and **2 Buffaloes**. Few to no carriers. Officers and S-mods add strength
    beyond FP.
  - **Stall risk, accepted with a check:** one fixed fleet faces three different targets; if it cannot
    reach 2× at the strongest (Sphinx with its new battlestation), rung 2 never launches. **Step 7c
    (log-only) measures all three targets before any launch exists**; fixes if needed: a lower margin
    for that rung, or one more heavy hull.
- **2026-10-24 amendments [RULED — Sean]:**
  - **3 Omens replace the 3 Brawlers** (Omen: 6 FP, speed 155, EMP emitter vs missiles and fighters;
    Brawler 7 FP). Not a League hull — a deliberate "something is off" hint. **Lore (Sean): Midan
    builds Omens** from one of many blueprints acquired during Wiggin's career. Implemented as a
    **ship skin** (a new Exiles hull id on the Omen base hull) pointing at
    `graphics/ships/exileomen1.png`, a renamed copy of vanilla's Omen sprite — a functional
    placeholder, so the planned midline-green recolour is a file swap (never a copy at vanilla's
    path, which would recolour every Omen in the Sector).
  - **Ajax gets three S-mods:** Insulated Engine Assembly + Extended Shields
    (`extendedshieldemitter`) + Hardened Shields (`hardenedshieldemitter`) — fixes the Conquest's
    weak stock shield (90° omni, efficiency 1.4). Build check: a three-S-mod variant loads cleanly.
  - **Gryphons:** S-modded ECCM Package (`eccm`) as a second S-mod.
  - **Insulated Engine Assembly is skipped on hulls with a base sensor profile of 30 or less**
    (verify per hull at build; expected: the frigates, i.e. the Omens). Phaeton and Buffaloes get it as
    a normal hullmod.
  - **Officers (vanilla cap 10 per NPC fleet, `maxOfficersInAIFleet`):** the Ajax's captain is the
    fleet commander, plus **nine officers** on the two Champions, three Eagles, two Gryphons and two
    destroyers — levels 5–6, one or two elite skills each, skills picked by vanilla's officer generator
    to suit each hull (verify its options at build). Officers add no FP, so the 2× launch check is
    unchanged; their power is margin the check doesn't count.
  - **The Ajax's captain [RULED — Sean, 2026-10-24]:** an unnamed fleet commander (not Wiggin), a
    strictly military officer **within vanilla limits**. **Level 7**, officer skills: Helmsmanship,
    Impact Mitigation, Damage Control, **Field Modulation (elite)**, **Target Analysis (elite)**,
    **Ballistic Mastery (elite)**, Missile Specialization. **Admiral skills (fleet-wide): Tactical
    Drills, Coordinated Maneuvers** — two, vanilla's NPC maximum (`FleetFactoryV3.addCommanderSkills`:
    one at level 4+, two at level 6+; admiral skills sit on top of the officer level and don't use
    officer slots). **Flux Regulation dropped** to stay within that limit. **Sensors dropped** — it is
    neither an officer nor an admiral skill (`skill_data.csv`), so it would do nothing on an NPC.
    Sean's rule for later: John Wiggin, if he ever takes the field, may break these limits — he is
    both a player-like character and a final boss.
- **Eventide on screen [RULED as the starting point; tune later]:** only destructions play out in
  view (explosion + removal, the vanilla gate-explosion pattern); the knocked-off-station survivors are
  moved while the player is out of the system — changing an orbit in view makes the object snap.
  Sean is not fully sure yet; revisit in tuning.
- **After the 7c test [RULED — Sean, 2026-10-24]:** the strike is strengthened with **unnamed escort
  fleets** accompanying Ajax's fleet. Escorts get the **same S-modded Insulated Engine Assembly** rule
  as the main fleet. **In-game name: "Strike Force"** (the whole Midan strike, Ajax's fleet and escorts).
  **Escort sizing [RULED]:** add escort fleets until the Strike Force meets the 2× margin against live
  defence at launch, **cap two escort fleets**; above the cap the opportunity gate waits for a lower
  defence window. **Ajax protection [RULED: (a) + (b)]:** (a) if Ajax is lost, Midan builds another
  within the rebuild delay before the next rung; (b) a vanilla `CoreAutoresolveListener` makes Ajax's
  own hull hard to disable in autoresolve (the listener runs after side totals are summed, so it does
  not change who wins; it never applies to a battle the player is in). Verify Nex keeps vanilla's
  autoresolver before relying on (b).
- **Vanilla Heg–League "hostilities" item [RULED — approved 2026-10-24]:** when the war starts (no Nex),
  quietly `endImmediately()` vanilla's `FactionHostilityIntel` for the pair; the relationship stays
  hostile (only its own random end-roll restores it) and the war's own intel carries the story.

### 1.5 Questions for Sean (original list)
1. The escalating flagships (Champion → Conquest → Pegasus + Conquest)?
2. Carriers: some, or none?
3. A logistics tanker in the fleet: yes or no?
4. Copy vanilla loadouts verbatim for v1?
5. **FP ceilings per rung** (guesses, to be replaced by measurement): **300 / 450 / 600** FP. For
   scale, Black Fleet assaults run 350–500 FP. Sphinx's new battlestation is the unknown; the
   composer's log line will show what each target really needs.
6. Variant display name?

---

## 2. Shape — what gets built

Grouped by component; "reuse" names the existing code each one leans on.

**A. Game-start patches (independent; can ship before anything else)**
- **Sphinx battlestation:** add a low-tech `battlestation` in a run-once patch (pattern:
  `ExilesModPlugin.applyMaxiosChangesOnce`; Sphinx has no station to remove).
- **Volturn red tide:** a permanent −2 food condition, visible, unannounced. New
  `market_conditions.csv` row, a small condition class, a placeholder icon; name and text are Sean's.
  Applied in the same run-once patch family. The −2 is a plain number in the condition.

**B. The Midan module (inside the war director)**
- Saved state: rung index, readiness date, last-launch / last-result, backfire counter, lockout
  date, the active directive, and the per-firing breadcrumb records.
- Daily check while ready: compute the opportunity score (means / distraction / cover) against a bar
  that lowers the longer Midan has been ready; when it clears, run the feasibility check (composer,
  §1.4) and launch.
- Readiness: year 1; rebuild 2 years after a success, 3 after a failure (config).
- Off paths: `midan.enabled`; the war switch (HL-1 precedent); the stop-for-good cases (League,
  Hegemony or Midan gone); skip a rung whose target isn't Hegemony-owned at launch.
- **Reuse:** the war director's state, tension and op counts; `ExileLiveOps`; the squeeze gate's
  defence measure.

**C. The strike operation**
- A `RaidIntel` subclass (League-flagged; **its text never names the source**), routed from the
  launch anchor (Lundy → Sallee → Midan home) via a **hyperspace assembly point** outside the anchor's
  system.
- The strike stage: a variant of `ExileHegLeagueStrikeStage` (single fixed target; goes through the
  target's station only; sticky success; window end resolves on strength; the `updateRoutes()`
  `maxDays` override). The payload is the rung's wound.
- Fleet flags at spawn: `$noRepImpact`; doNotAttack toward the player (Sean, 2026-10-20: the player
  cannot be dragged into an NPC battle, so this covers the real risk).
- **Return leg:** to the anchor system's **jump point in hyperspace**, despawning as it jumps in
  (Sean, 2026-10-20). Fallback noted for the retune: if the stealthed fleet is too hard to follow,
  despawn when it leaves the target system instead. Reuse `ExileReturnLeg`.
- **"Witness" hooks for a Phase-2 quest (Sean, 2026-10-20; hooks only, no quest):** a memory flag
  that says "a quest wants the player to see rung N"; while it is set, the strike fleet **holds at
  its assembly point until the player is at or near the target, or until a deadline** (the
  failsafe), then strikes. Without the flag, normal behaviour. A test pin exercises it.

**D. Rung payloads**
- **Rung 1:** disrupt Military Base + Orbital Station 365 days, Spaceport 180; one relief record with
  a 180-day budget (`ExileHegLeagueRecoveryManager.record`).
- **Rung 2:** Fuel Production 810 days, Battlestation + Military Base 540, Spaceport 180; one relief
  record, 270-day budget. Then the **embargo controller**: ~7 days later, the Story announcement and
  the embargo condition together, on every Hegemony, Independent and Refugee market in a system with
  a Hegemony market at strike time; 24 months; early-lift flag `$exiles_diktat_embargo_lifted`;
  "lifted" announcement at the end; skipped entirely if the Diktat doesn't hold Sindria. The
  condition is a fuel-only `ExileCommodityShortageCondition` subclass owned by the controller, with
  the fleet-size multiplier built at 1.0 (off).
- **Rung 3:** remove `solar_array`; the mirror-and-shade sequence (on-screen: one at a time over a
  few days with `ExplosionEntityPlugin`, fleet damage NONE; off-screen: all at once) — **find the
  entities by type and orbit, never by id** (vanilla's shades reuse the mirrors' ids); scorch the
  farmland (`farmland_adequate` → `farmland_poor`); bombardment **last** via
  `CoreImmigrationPluginImpl.reduceMarketSize` (7 → 6); description and illustration swap via the
  runtime setters. Failure refinement: what is destroyed stays destroyed; the array dies with the
  first mirror; the size loss happens only if the sequence completes.
- All durations and magnitudes are plain numbers in config or in the payload.

**E. Landing in the war cycle** — the ruled table (raise always applies; becomes the INCIDENT only
in TENSION / DE_ESCALATION; folds in during a crisis; talks forced to COLLAPSE; rung 3 forces
General Engagement mid-ESCALATION). One new entry point on the director.

**F. Shared story-announcement intel** — one generic Story-tagged intel class (self-advancing per the
CLAUDE.md intel gotcha) used by: the embargo announcement, the embargo-lifted notice, Cunningham's
vow (~5 days after a successful rung 3).

**G. Cunningham** — a hidden important person, id `exile_cunningham` (exile_ data-id convention), rank Admiral, post Fleet Commander, first name blank; portrait
`graphics/portraits/exiles_cunningham_placeholder.png` (already in the repo) **registered in
`settings.json` `graphics/characters`**.

**H. Conquest shields** — add `arcadia_station`, `sphinx`, `eventide` to
`nexConfig.conquestShieldMarkets`.

**I. The Black Fleet proxy**
- War-director side: on each new relief record, roll 0.5; on success (and no directive active)
  write the directive key `CONVOY_AMBUSH` naming the destination. Mark relief convoy fleets with a
  read-only flag. Detect a convoy destroyed in a battle involving the Black Fleet while a directive
  names its destination; roll attribution (0.4 misattributed → +6 tension; 0.6 correct → counter +1);
  at 3: the backfire beat (Story intel), −10 tension, reset, 1-year proxy-only lockout.
- BF-director side: a new **convoy ambush** operation — on a `CONVOY_AMBUSH` directive, when a marked
  convoy enters the target system, spawn one ~40–60 FP destroyer-capped fleet near the system edge or
  jump point, order it to intercept that convoy, then send it home or despawn it. Kept inside the BF
  director so its kill switch, caps, FP ramp, defeatability and spoils accounting all apply.
  Precedent: vanilla `DisposableFleetManager`.
- `RAID_FOCUS` directive: the rung-1 fallback (×10 commerce-raid weight, ×2 feasibility relax) —
  built only if the rung-1 test run shows no natural Black Fleet follow-up (ruled 2026-10-10).

**J. Test pins (`testing` block)** — Midan ready now; force a specific rung; force the witness hold;
force a directive. Same discipline as `forceState` (pins are never committed).

---

## 3. Config block (proposed shape; every number a guess shipping with its log line)

```
"midan": {
    "enabled": true,                    # PLAYER OPTION: keep the war, turn off the strikes
    "firstReadyDays": 365,
    "rebuildDays": 730,                 # after a success
    "rebuildFailedDays": 1095,          # after a failure
    "score": { "meansWeight": ..., "distractionWeight": ..., "coverWeight": ...,
               "barStart": ..., "barFloor": ..., "barDecayDays": ... },
    "strikeMarginMult": 2.0,
    "fpCeiling": 300,                   # one recurring fleet (§1.5a)
    "tensionRaiseTo": [50, 65, 88],
    "failedTensionGain": 12,
    "launchAnchors": ["lundy", "sallee", "midan_home"],   # resolved via station entities
    "witnessHoldMaxDays": ...,
    "rung1": { ... durations ... }, "rung2": { ... }, "rung3": { ... },
    "embargo": { "delayDays": 7, "durationDays": 730, "fleetSizeMult": 1.0 },
    "vowDelayDays": 5,
    "proxy": { "directiveChance": 0.5, "misattributionChance": 0.4, "misattributionTension": 6,
               "backfireThreshold": 3, "backfireTensionCut": 10, "lockoutDays": 365,
               "ambushFpMin": 40, "ambushFpMax": 60 }
}
```
Keys are proposals; final names follow the existing `exilehegleague` block's conventions.

---

## 4. Build order (each step Janino-checked, committed, and testable on its own)

| Step | Contents | Testable by |
|---|---|---|
| 7a | Game-start patches: Sphinx battlestation, Volturn red tide | A new game: inspect Sphinx and Volturn |
| 7b | Shared story intel class; Cunningham person + portrait registration | A test pin posts a dummy announcement |
| 7c | Midan module skeleton: state, readiness, score — **log-only, no launches** (instrument before tuning) | Score log lines over a run |
| 7d | Strike op + composer + variants + rung 1 payload + landing table + tension | Pin "ready now", rung 1 |
| 7e | Rung 2 payload + embargo controller; rung 3 payload + mirror sequence + art swap + vow | Pins for rungs 2 and 3, on- and off-screen |
| 7f | Proxy: directive writer + convoy marking + kill detection (war side); convoy ambush op (BF side) | Pin a directive; watch an ambush |
| 7g | Witness-hold hooks + switches + Nex edge cases + conquest shields | Pins; a Nex run |

---

## 5. Test matrix (first pass)
- Each rung, successful: wound applied with the right durations; relief record and budget; tension
  raised to the target; the right landing behaviour for the state it lands in.
- Each rung, foiled by the player: no wound (rung 3: partial wreckage, no size loss); +12; rung spent;
  the 3-year rebuild.
- On-screen vs off-screen for each rung (rung 3's sequence especially).
- Rung landing in each war state (pin the state): TENSION, DE_ESCALATION, RETALIATION, ESCALATION,
  GE, talks.
- Embargo: announcement + condition on the right markets; 24-month removal; the early-lift flag; the
  Diktat-lost-Sindria skip.
- Proxy: directive roll rate; ambush spawn on arrival; kill attribution; backfire at 3; lockout.
- War off mid-flight: wound applies, tension skipped. `midan.enabled` false: nothing launches.
- Nex: target not Hegemony-owned → skip; League / Hegemony / Midan gone → stop; Black Fleet gone →
  rungs from Midan home, no ambushes.
- Save/load in the middle of each long-running piece (strike in flight, mirror sequence, embargo,
  directive).
- Both starts (Exiles and non-Exiles).

## 6. Risks (flagged for the build)
- **Rung 3's mirror sequence** is the most novel code (timed entity removal with explosions while the
  player watches); build it last within 7e and test save/load mid-sequence.
- **Fleet inflater** over the custom variants (§1.4).
- **Hyperspace assembly point:** confirm a route's first segment can sit in hyperspace (unverified
  since 2026-10-11).
- **The witness hold** keeps a fleet waiting in hyperspace; bound it by the deadline and make sure the
  hold survives save/load.
- **Scope:** this is the largest pass in the war director. The step table exists so it can stop
  cleanly after any step.

---

## 7. Build record

### 7a — Game-start patches [BUILT 2026-10-24; Janino-clean 2/2; UNTESTED IN GAME]
- **Sphinx battlestation:** `ExilesModPlugin.applySphinxChangesOnce()` (run-once key
  `$exiles_sphinx_patch_done`) adds the low-tech `battlestation` to Sphinx unless it already has any
  station tier (guards every orbital-station / battlestation / star-fortress id, all three techs).
- **Red Tide:** new condition `exile_red_tide` (`data/scripts/marketconditions/ExileRedTide.java`;
  `market_conditions.csv` row, order 70.6, next to the other food conditions). **Food only:** a
  per-commodity supply modifier on Aquaculture (Farming as a fallback) — vanilla's
  `ResourceDepositsCondition` pattern, which `BaseIndustry.supply` documents for negative values — so
  Volturn's lobster is untouched (Sean, 2026-10-24). Penalty `FOOD_PENALTY = 2`, a plain number in the
  class. Stateless (no transient-plugin risk). **The description lives only in
  `market_conditions.csv`** (a marked placeholder by Claude); the Java adds just the effect line.
  Applied by `ExilesModPlugin.applyVolturnChangesOnce()` (run-once key `$exiles_volturn_patch_done`).
- **Placeholder icon:** `graphics/icons/markets/exile_red_tide.png`, a renamed copy of vanilla's
  `pollution.png` (Sean, 2026-10-24: copies make later art swaps easier).
- **Test (new game required):**
  1. Sphinx shows a Battlestation and a station fleet in orbit.
  2. Volturn lists "Red Tide" with the description and the "Reduces food production by 2" line.
  3. Volturn's Aquaculture food supply is 2 lower than before (expected 6: 7 size + 1 administrator
     − 2), and its lobster output is unchanged.
  4. The Sector food screen still shows no shortage (Eventide's exports mask it until rung 3).
  5. Save, reload: Red Tide's −2 is still there, not doubled.

### 7b — Shared story announcement + Admiral Cunningham [BUILT 2026-10-24; Janino-clean 3/3; UNTESTED IN GAME]
- **`ExileStoryAnnouncementIntel`** (new): one generic, self-advancing, Story-tagged intel. The caller
  supplies the title, body (paragraphs split on a blank line), one-line summary, a faction (crest and
  colours) and/or a person (portrait), and the number of days. Static `post(...)` creates it, adds it
  with the standard notification and logs `Exiles story announcement posted: "<title>"`. Saves only
  Strings and floats. No story text of its own. Callers come in 7e (embargo, embargo lifted, vow).
- **Admiral Cunningham:** `ExilesModPlugin.applyCunninghamSetupOnce()` (run-once key
  `$exiles_cunningham_setup_done`) creates `exile_cunningham` — Hegemony, male, first name blank, last
  name Cunningham, rank Admiral (`Ranks.SPACE_ADMIRAL`), post Fleet Commander — in ImportantPeople
  only (hidden, the Mourad pattern). Portrait registered in `data/config/settings.json`
  `graphics/characters` as `exile_cunningham` → `graphics/portraits/exiles_cunningham_placeholder.png`.
  **ID corrected** from the earlier `exiles_cunningham` to the `exile_` data-id convention.
- **Test pin:** `exilehegleague.testing.postStoryAnnouncement` (default false; new
  `ExileHegLeagueConfig.testPostStoryAnnouncement`). When true, every game load posts one sample
  announcement with Cunningham's portrait. Never commit it true.
- **Test (new game, then set the pin and reload):**
  1. The notification appears; the Intel window lists it under **Story**.
  2. The entry shows Cunningham's placeholder portrait (not blank — the registration works) and both
     test paragraphs.
  3. After 30 days it shows "(Over)" and then leaves the board.
  4. Save and reload mid-way: the countdown continues (it is not reset or duplicated beyond the one
     new post per load the pin makes).

### 7c — Midan module skeleton, LOG-ONLY [BUILT 2026-10-24; Janino-clean 3/3; UNTESTED IN GAME]
- **`ExileHegLeagueMidanModule`** (new), held by the director (`midan` field, saved, created lazily)
  and advanced only while the war is active — so the war's off switch pauses Midan (HL-1 precedent).
  **It launches nothing.**
- **Readiness:** schedules rung 1 at `firstReadyDays` after the director starts; `READY` line when it
  arrives (or at once under the `midanReadyNow` pin). Rung progression arrives with launching in 7d.
- **Measurement** (every `measureIntervalDays`, default 30, all three targets): live defence = patrol
  strength hostile to the League in the target's system + the target's own station (the squeeze
  gate's measure); required = `strikeMarginMult` (2.0) × defence; fleet strength =
  `Misc.getAdjustedStrength(fleetFpEstimate, launch anchor)` — the conversion every Heg–League gate
  uses since Option A. 7c uses `fleetFpEstimate` 277 (the planned roster); 7d's fleet builder replaces
  it. Officers and S-mods add power this does not count, so the true margin is higher than logged.
- **Opportunity score** (daily while ready, current rung): means (defence below a slow running
  baseline, `baselineWeight` 0.05/day) + distraction (live Hegemony raid-pipeline ops and blockades /
  `distractionOpsFull` 2) + cover (1 in INCIDENT/RETALIATION/ESCALATION, 0.5 during talks, 0
  otherwise), weighted 0.4 / 0.3 / 0.3; the bar falls 0.6 → 0.15 over 365 days of readiness.
- **Config:** new `exilehegleague.midan` block (`enabled` is a PLAYER OPTION) + test pin
  `testing.midanReadyNow`; durations scale with `timeScale`.
- **Log lines** (all "Exiles Midan"), added to `tools/exiles-log-report.ps1` (sections "Midan ladder
  (Pass 7)" and "Midan measurements"; MEASURE/SCORE kept out of the timeline):
  `module started`, `READY`, `MEASURE` (one per target per interval), `SCORE` (current rung, same
  cadence), `WOULD LAUNCH` / `BLOCKED` (score cleared the bar; feasible or not; at most every 30 days).
- ~~Known measuring caveat: defence counts the player's fleet.~~ **WRONG — corrected 2026-10-24.**
  Vanilla `WarSimScript.getFactionStrength` skips `fleet.isPlayerFleet()` and only counts factions
  that own a market in the system, so the player's fleet never enters "defence".
- **What 7c must answer before 7d (the stall check):** in the MEASURE lines, does **Sphinx** (rung 2,
  now with its battlestation) ever reach `FEASIBLE` (ratio ≥ 2.0)? How often do Citadel Arcadia and
  Eventide? If Sphinx never does, rule a fix before 7d: a lower margin for that rung, or one more
  heavy hull.
- **Test:** a new game (7a/7b/7c together), then about one game year at normal settings (MEASURE lines
  begin at day 30). Optionally set `midanReadyNow: true` to see SCORE and WOULD LAUNCH lines early. Run
  `tools/exiles-log-report.ps1` and read the two Midan sections.

#### 7c test record — first run [2026-10-24; new game, no Nexerelin, Mar 2 c206 → Apr 7 c207]

Report: `logs-archive/report_2026-10-24_1738.md`. Exiles errors 0. Module started day 0, READY at
day 366 as scheduled. 14 MEASURE rounds; one SCORE line (score 0.0 vs bar 0.57, TENSION, no Heg ops).
No WOULD LAUNCH / BLOCKED lines. 7a confirmed in the numbers: Sphinx shows a 240-strength station
(the new battlestation). Red Tide is not visible in the log (check in game).

**Stall check — answered: Sphinx is NEVER feasible.** Fleet strength 373 (277 FP via
`getAdjustedStrength` at Lundy, k≈1.35).

| Target | Defence range (14 samples) | Ratio range | Feasible at 2.0× |
|---|---|---|---|
| Citadel Arcadia | 173–337 (station 120) | 1.11–2.15 | 1 of 14 |
| Sphinx | 452–931 (station 240 + patrols 212–691) | 0.40–0.83 | 0 of 14 |
| Eventide | 263–691 (no station; Sphinx's counted once, at orbital conjunction) | 0.54–1.42 | 0 of 14 |

Sphinx is structural, not a tuning miss: the battlestation alone is ~2/3 of the fleet's strength and
Samarra's patrols never fell below 212, so Sphinx failed **even at 1.0×** in every sample. No margin
number fixes it; the one-fleet / ~300 FP / 2× / battlestation stack has to give somewhere (see the
options put to Sean, below). At 1.5× the same fleet would clear Arcadia in 7 of 14 samples.

Perspective: an ordinary League strike group logged FP 301 → strength 1216 (k≈4.0, Mazalot's
fleet-size multiplier and doctrine). A route FP is pre-multiplier; Ajax's 277 FP is the real fleet.
Ajax is roughly a third of a standard League strike group by vanilla's measure.

**Measurement understated (unverified estimate):** `getAdjustedStrength` ignores officers and S-mods;
vanilla judges a live fleet by `getEffectiveStrength` (quality 0.8+0.4q with +0.3q per S-mod,
captain ×(1+(L−1)/officerMaxLevel)). A rough hand estimate puts the composed fleet near 450–500, not
373. That would help Arcadia, but not Sphinx. Note: Sean's local `officerMaxLevel` is 8 (install
modified 2026-09-19); stock may differ, which changes the captain term.

**Bug (mine): baselines frozen before readiness.** Baselines only update in the daily opportunity
check, which runs only once ready and only for the current rung, so all three sat at their day-0
values for a year (337 / 883 / 643). Also 0.05/day is a ~14-day half-life, not "slow". Proposed fix:
update every target's baseline at each monthly MEASURE (per-sample weight in config), daily check
reads it only.

**Not 7c, noted from the same run:**
- **Aug 14, c206 — B3 self-heal fired with Nex absent.** Cause: vanilla `CoreLifecyclePluginImpl`
  seeds Heg–League hostility as a `FactionHostilityIntel`, which after 180 days rolls a 25% end
  chance every 30–90 days; it ended (`Ending hostilities: Hegemony <-> Persean League`), restored the
  starting relationship and posted a "hostilities ended" update to the player. B3 re-hostiled the pair
  ~0.2 s later (as designed). The player-facing problem: vanilla's last word is a false peace notice.
  It will happen once in every non-Nex game, any time after day 180.
- Three ops flagged "no outcome, 154 days" were a report artifact: SALVAGE_DIAG date samples
  stopped between Nov 4 and Feb 27; log timestamps place the Galatia commerce raid and the
  Kumari Kandam Omicron raid near day 341–345 (~55–60 days old at the end; normal).
- Naraka League strike FAILED with the player fighting in-system — expected.

### 7d split [RULED — Sean, 2026-10-25]
- **7d-1 (log-only):** composer + custom variants (incl. the Omen skin) + escort sizing (0/1/2/over
  cap); each month the Strike Force is assembled in memory (never spawned, then discarded) and
  vanilla's own `getEffectiveStrength` replaces the 277-FP estimate in MEASURE lines; plus the
  baseline fix and the vanilla hostility-intel retirement. Test: variants load, real strength, how
  often each rung is reachable within two escorts.
- **7d-2 (launching):** strike op, rung-1 payload, landing table, tension, Ajax protection (a)+(b).
- Red Tide confirmed in play 2026-10-25 (Volturn food reads 6; Sean had read the Domestic Goods line).

### 7d-1 — part 1: baseline fix + vanilla hostility intel [BUILT 2026-10-25; Janino-clean 4/4; UNTESTED IN GAME]
- **Baseline fix:** every target's baseline now moves at each monthly MEASURE
  (`score.baselineWeightPerMeasure`, 0.25 ≈ a 2.4-month memory); the daily opportunity check only
  reads it. Config key renamed from `baselineWeight` (0.05/day; not a player option, added in 7c).
- **Vanilla hostility intel:** `ExileHegLeagueNexSupport.retireVanillaHostilityIntel()` ends
  vanilla's Heg–League `FactionHostilityIntel` with `endImmediately()` on the director's first active
  tick, which bypasses vanilla's relationship restore; log line
  `vanilla Hegemony/League hostility intel retired`. Gated on `nex.selfHealHostility` (the B3 switch):
  with B3 off the director rides any peace, so vanilla's flare-up runs its course. **Note:** this
  runs with or without Nexerelin (the ruling text said "no Nex"); with Nex present it is the same
  harmless removal if the intel exists. Flagged for Sean.
- Report tool: new section "Heg-League premise (B3, vanilla intel)" (checked against the 10-24 log).
- **Test expectation:** the retire line appears once, early in the war; NO "hostilities ended"
  notice and NO B3 self-heal line for the rest of a non-Nex run; MEASURE baselines drift month to month.
- Remaining 7d-1: variants (10 hulls + Omen skin + `graphics/ships/exileomen1.png`), the composer,
  escort sizing, real-strength MEASURE.
- **Prep facts for the variant build (gathered 2026-10-25, vanilla data):**
  - Base variant files live in hull subfolders of `starsector-core/data/variants/` (e.g.
    `conquest/conquest_Elite.variant`); `omen_PD` and `phaeton_Standard` sit at the top level.
    Available bases: conquest Elite/Standard; champion Assault/Elite/Escort/Strike/Support; eagle
    Assault/Balanced/Support; falcon Attack/CS/Escort; gryphon FS/Standard; hammerhead
    Balanced/Elite/Overdriven/Support; sunder Assault/CS/Overdriven/Support; buffalo Standard; omen PD;
    phaeton Standard.
  - Hull FP / OP / burn: Conquest 24/315/8, Champion 15/165/8, Eagle 14/170/9, Gryphon 14/140/8,
    Falcon 12/130/9, Hammerhead 10/95/9, Sunder 10/105/9, Omen 6/40/10, Phaeton 6/45/8,
    Buffalo 5/28/8. Fleet burn is 8 (Conquest, Champion, Gryphon, logistics).
  - Insulated Engine Assembly (`insulatedengine`) normal-mod cost 3/6/9/15 by hull size; S-mods cost
    no OP. Check the Phaeton and Buffalo base variants for spare OP.
  - **Sensor profile is not a `ship_data.csv` column**; find where vanilla sets it before applying
    the "skip on profile ≤ 30" rule.
  - **S-mod cap:** Sean's local `settings.json` has `"maxPermanentHullmods":5`. I believe stock is
    lower (2); confirm with Sean. Ajax's three S-mods need the "three-S-mod variant loads cleanly"
    check either way; NPC variant files may not be held to the cap.
- **2026-10-25 follow-ups [Sean]:** keep the hostility-intel retirement for Nex games too. **Nex
  check (ExerelinCore sources):** `NexCoreLifecyclePlugin` comments out vanilla's
  `FactionHostilityManager` setup, and Nex's apply-to-existing-game path ends vanilla's hostility
  intels and removes the manager. Under Nex `FactionHostilityManager.getInstance()` is null, so
  `retireVanillaHostilityIntel()` returns at its null check: a no-op, no conflict.
- **Stock settings restored (Sean, 2026-10-25).** Stock values: `officerMaxLevel` **5**,
  `maxPermanentHullmods` **2**. Consequences for the roster, **for Sean's ruling**:
  - The nine fleet officers were planned at "levels 5–6"; stock NPC generation caps at 5, so level 6
    is above the normal cap. Recommend: level 5.
  - Ajax's level-7 captain is above the normal cap (5) but matches vanilla's own special
    sleeper-pod officers (level 7), so "within vanilla limits" holds only in that sense.
  - Ajax's three S-mods are above the normal cap (2) but equal what a player with the Best of the
    Best skill can reach (3). The build still checks that the variant loads.
  - **RULED (Sean, 2026-10-25):** the nine fleet officers are **level 5**; Ajax's captain stays
    **level 7** with **three S-mods** on Ajax (both above the normal stock caps, matching vanilla's
    special sleeper-pod officers and the Best of the Best ceiling).

### 7d-1 — part 2: Strike Force variants + Omen skin [BUILT 2026-10-25; data only; UNTESTED (load check pending)]
- **Ten variants** in `data/variants/exiles_midan/`, generated from vanilla bases (Claude's picks
  among the listed bases; loadouts verbatim; only the fields below differ; diffs checked):

  | Variant id | Base | S-mods | Other change |
  |---|---|---|---|
  | `exiles_midan_conquest_ajax` | conquest_Elite | insulatedengine, extendedshieldemitter, hardenedshieldemitter | |
  | `exiles_midan_champion_assault` | champion_Assault | insulatedengine | |
  | `exiles_midan_eagle_assault` | eagle_Assault | insulatedengine | |
  | `exiles_midan_falcon_attack` | falcon_Attack | insulatedengine | |
  | `exiles_midan_gryphon_standard` | gryphon_Standard | insulatedengine, eccm | |
  | `exiles_midan_hammerhead_balanced` | hammerhead_Balanced | insulatedengine | |
  | `exiles_midan_sunder_assault` | sunder_Assault | insulatedengine | |
  | `exiles_midan_omen_pd` | omen_PD | none (frigate, profile 30) | hullId `exile_omen` (skin) |
  | `exiles_midan_phaeton_standard` | phaeton_Standard | none | normal insulatedengine; flux capacitors 10 → 4 |
  | `exiles_midan_buffalo_standard` | buffalo_Standard | none | normal insulatedengine; flux capacitors 8 → 2 |

  S-modded variants show "Customized" (ruled); the others keep vanilla's name. Every variant has
  `goalVariant: false` (vanilla bases are `true`, which would list Midan's loadouts in the player's
  autofit menu and leak the clue). Hullmod ids verified in `hull_mods.csv`.
- **Sensor-profile rule resolved:** base profile comes from hull size (`settings.json`
  `baseSensorFrigate` 30 / Destroyer 60 / Cruiser 90 / Capital 150), and a fleet's profile counts its
  top `maxSensorShips` (5) ships. The Phaeton and Buffalo are DESTROYERS with built-in Civilian-grade
  Hull (raises profile), so they get Insulated Engine; only the Omens (frigates) skip it.
- **FLAGGED for Sean — logistics OP:** Phaeton (45/45) and Buffalo (28/28) stock variants use every
  OP point, so the normal 6-OP Insulated Engine did not fit. Smallest change: 6 flux capacitors
  removed from each (weapons and hullmods untouched). Alternative: S-mod it (0 OP) like the warships.
- **Omen skin** `data/hulls/skins/exile_omen.skin` → sprite `graphics/ships/exileomen1.png` (a
  byte-identical PLACEHOLDER copy of vanilla's Omen at an Exiles path). Tags `no_dealer`, `no_bp_drop`;
  skin tags REPLACE the base hull's (inferred from vanilla `brawler_LG.skin`, not engine-verified), so
  the Omen's `rare_bp`/`merc`/`ind` are dropped and no faction knows the hull.
- **Roster total: 24 ships, 264 FP** (Ajax 24, 2 Champions 30, 3 Eagles 42, 3 Falcons 36, 2 Gryphons
  28, 4 Hammerheads 40, 3 Sunders 30, 3 Omens 18, Phaeton 6, 2 Buffaloes 10) — below the 277 estimate.
- **Load check (cheap, before the composer):** launch Starsector to the main menu with Exiles on.
  Variants and skins load at startup; a bad id fails there. Then check `starsector.log` for errors
  naming `exiles_midan` or `exile_omen`.

### 7d-1 — part 3: logistics skins + the composer [BUILT 2026-10-25; Janino-clean 4/4; UNTESTED IN GAME]
- **Logistics skins** `exile_phaeton.skin`, `exile_buffalo.skin` (commit 640a1bc): Militarized
  Subsystems BUILT IN at hull level, vanilla names and sprites, tags `no_dealer`/`no_bp_drop`. Why a
  skin and not the variant: the decompiled variant loader (`HullVariantSpec`) reads only `hullMods`,
  `permaMods`, `sMods`, `sModdedBuiltIns`, `suppressedMods`, so a variant cannot make a mod built in
  (an unknown key is silently ignored), and a non-S-mod `permaMods` entry still charges OP (8 on a
  destroyer; none left). Vanilla puts built-ins on the hull the same way (Hegemony Buffalo skin;
  Salvage Rig `constructionrig.ship` carries `repair_gantry`). Logistics profile 60 × 0.4 = 24;
  **fleet profile ≈ 438** (top five: three Omens 30 + two logistics 24, plus vanilla's flat 300).
  Militarized also gives the two hulls +1 burn (fleet burn stays 8) and doubles their minimum crew.
- **Fleet sensor profile, verified in the engine** (decompiled `CampaignFleet`): the sum of the five
  highest ship profiles (each rounded) + `sensorRangeBase` (300), then fleet-level modifiers (Go Dark,
  burn abilities).
- **Omens keep their stock loadout:** omen_PD uses all 40 OP, so Insulated (3 OP) would cost three
  capacitors. Sean: don't cripple them.
- **Composer** `ExileHegLeagueMidanComposer` (commit 159747e): builds the main fleet (PLS Ajax +
  roster, flagged Persean League, named "Strike Force") and one escort group from explicit variant
  lists; Ajax's captain is a League `createRandomPerson` set to the ruled level/skills, rank Space
  Admiral, personality Steady (Claude's pick; not ruled), fleet commander; the other officers come
  from vanilla's own generator exactly as `FleetFactoryV3` does (skills by hull, default elites,
  timid → cautious). Full CR, fresh sync. Missing variants WARN and are skipped; a missing flagship
  returns null and the 7c estimate stands in. Nothing is added to a location.
- **Module:** at every MEASURE the Strike Force is composed and measured with vanilla's
  `getEffectiveStrength()` (verified: syncs on demand and sums `Misc.getMemberStrength`, so an
  in-memory fleet measures correctly), then dropped. Escorts needed per target = smallest n ≤
  `escortCap` with main + n × escort ≥ required, else NOT FEASIBLE (the rung waits). `fpCeiling`
  is checked per fleet.
- **Config:** `midan.strikeForce{}` holds every roster number (fleet name, flagship, roster, officer
  ships/levels, captain level/skills, admiral skills, escort cap/roster/officers).
- **Log lines:** new `Exiles Midan COMPOSED: main N ships F FP strength S (flagship PLS Ajax,
  captain level 7, 10 officers incl. captain) | escort N ships F FP strength S (5 officers) | escort
  cap 2`; MEASURE/SCORE now show `Strike Force main + escort/escort | needs N escort(s) (cap 2)`.
  Report tool: COMPOSED joins the "Midan measurements" section and stays out of the timeline.
- **Expected numbers (hand estimate; engine per-ship base may differ):** main ≈ 528, escort ≈ 376,
  main + 2 ≈ 1279. Against the 10-24 run's defence samples: Arcadia feasible alone 9/14 and with
  one escort 5/14; **Sphinx needs two escorts and clears in ~6/14 (over cap 8/14)**; Eventide alone
  1, +1 4, +2 7, over cap 2.

#### 7d-1 test checklist (ONE new game; stock settings.json)
1. **Load check:** the game reaches the main menu; `starsector.log` has no error naming
   `exiles_midan`, `exile_omen`, `exile_phaeton` or `exile_buffalo`.
2. **COMPOSED line at day 0:** expect main **24 ships, 264 FP** (flagship included),
   flagship `PLS Ajax`, captain level 7, 10 officers incl. captain; escort **20 ships, 226 FP**,
   5 officers. No "Exiles Midan composer:" WARN lines in the errors section.
3. **Strength sanity:** main and escort strengths near the estimates above (within ~±30%). If they
   are far off, stop and report before 7d-2.
4. **Vanilla hostility intel:** the line `vanilla Hegemony/League hostility intel retired` appears
   once, early; no "hostilities ended" notice and no B3 self-heal line all run.
5. **Baselines** drift from month to month in MEASURE lines.
6. **Escort needs:** MEASURE lines show `needs 0/1/2/MORE THAN 2` per target; Sphinx should show
   2 in quiet months and MORE THAN 2 when Samarra is crowded.
7. Optional: `midanReadyNow: true` (test pin; never commit) to see SCORE / WOULD LAUNCH lines early.
8. Optional in-game look: none — nothing spawns in 7d-1.

#### 7d-1 test record [2026-10-26; new game 10-25 22:33, no Nexerelin, Mar 2 c206 → Jun 10 c207]
The new game was started at 10-25 22:33. That launch composed and measured once, then exited. The
year was played from the day-0 save, reloaded once at about day 30.
1. **Load check: PASS.** No error naming the new variants, skins or sprite; 0 Exiles errors.
2. **COMPOSED: PASS.** Main 24 ships, 264 FP, PLS Ajax, captain level 7, 10 officers. Escort 20
   ships, **224 FP** (the checklist's 226 was an arithmetic slip), 5 officers.
3. **Strength: PASS.** Main **513** (estimate 528), escort **375** (estimate 376). Identical for 16
   months. A single first reading of 489/360 is explained by the hullmod file: Sean's custom
   `hull_mods.csv` was still installed at the new game, and vanilla's went in at 22:58. Different
   mod OP costs scale strength.
4. **Vanilla hostility intel: PASS.** Retired once per game start; no "hostilities ended" notice.
5. **Opportunity check: works.** WOULD LAUNCH on Jun 8 c207: rung 1 Arcadia, score 0.54 vs bar
   0.48, needs 0 escorts.
6. **BUG FOUND: station strength reads 0 after a save load** (engine cache; full mechanism in
   `ExileStationStrength`'s javadoc). Sphinx's battlestation read 240 fresh, then 0 for all 16
   months after the load. Arcadia's read 0 after each load and returned while the player sat in
   Arcadia. The 10-24 run shows the same drop. It affects every director that adds station
   strength (18 call sites).
7. **Corrected Sphinx feasibility** (logged enemy + 240 station, ×2.0): the Strike Force with two
   escorts (1263) clears Sphinx in **1 of 16 months**; the logged 10/16 was the bug. Eventide (no
   station) is genuinely feasible 10/16 with 1–2 escorts. Arcadia is feasible alone throughout.
   Sean ruled 2026-10-26 that this order is fine (war doc §6.1); how Sphinx becomes reachable is
   still open.

### 7d-1 fix — load-safe station strength [BUILT 2026-10-26; Janino-clean 11/11; UNTESTED IN GAME; approved by Sean]
- **New `ExileStationStrength.get(faction, system, from)`:** a drop-in for
  `WarSimScript.getStationStrength` with the same filters. It sums
  `Misc.getMemberStrength(member, true, true, true)` per member (the engine's own full-sync
  formula), read-only, with null guards. Shared measurement only; every caller keeps its own rules.
- **All 18 call sites switched** (Ashen Culann/rehearsal, Black Fleet assault/raid, Heg-League
  strike/squeeze managers and stages, Midan, Omicron). This is a behaviour change for every
  director: after a reload, stationed targets are no longer seen as undefended.
- **TEMPORARY parity diagnostic** on each Midan MEASURE line: `PARITY station vanilla V / exiles E ;
  hostile live fleets cached C / recomputed R (n fleets)`. Vanilla is read first. Expected: equal on
  a fresh game; after a load the station's vanilla value is 0 and ours holds. The enemy-side pair
  tests whether moving patrol fleets carry the same stale cache (believed not; unproven). Remove
  after the test.
- Midan log labels updated from "step 7c" to "step 7d-1".

#### Next test (ONE new game, stock settings)
Play a few months, **save, quit to desktop, relaunch, load**, then play a few more months. Do not
visit Samarra. In the report's "Midan measurements": before the reload, PARITY station values match
(Sphinx 240/240). After it, Sphinx shows vanilla 0 / exiles 240, and MEASURE's `station` uses 240.
Note whether the hostile-fleet pair ever differs.
#### Save/reload test record [2026-10-29; new game, no Nexerelin, player stayed in Corvus]
- **Station fix: PASS.** Before the reload (days 0–121) PARITY read 120/120 at Arcadia and 240/240
  at Sphinx. After the reload (days 151–272) it read **vanilla 0 / exiles 120** and **vanilla 0 /
  exiles 240**, and MEASURE's `station` used the correct value. Eventide 0/0 throughout (no
  station). 0 Exiles errors.
- **Patrol side: NO DATA.** "Hostile live fleets" was 0 every month. With the player in Corvus,
  the Arcadia and Samarra patrols exist only as abstract routes. `WarSimScript.getFactionStrength`
  counts routes from route data, not the cached fleet total, so the cache issue can only touch
  live fleets near the player.
- With the station correction in place, Sphinx was feasible (main-fleet ratio ~0.93–0.97, so with
  escorts) on days 181–272 in this run. The earlier 1-of-16 figure came from one run's samples.- **PARITY diagnostic REMOVED 2026-10-29** (Sean agreed): the station fix is proven, and the patrol
  side was judged not worth a targeted test. Pass 7d-1 and the station fix are complete; next is 7d-2.

### 7d-1 rulings [RULED — Sean, 2026-10-29; all three per Claude's recommendation]
1. **Escort roster:** keep it as built. The main fleet's warships without the Ajax or logistics:
   20 ships, 224 FP, strength 375, five level-5 officers. Sphinx stays hard (about 5 months in 26
   across two runs, with the station counted). Revisit after 7d-2 shows the real wait at rung 2.
2. **Ajax captain personality: Steady.** It suits the Conquest's broadsides. Personality affects
   real combat only (the player fighting the Strike Force); the autoresolver uses strength only.
3. **Logistics capacitors: keep the trim** (Phaeton 10 → 4, each Buffalo 8 → 2) to fit normal
   Insulated Engines. That holds the fleet sensor profile near 438 instead of ~510.
### 7d-2 plan [2026-10-30; rulings below by Sean; build follows]
**Rulings (Sean, 2026-10-30):**
- **Ajax protection (b): MEASURE FIRST.** Decompile finding: vanilla's `CoreAutoresolveListener` runs
  BEFORE the autoresolver shuffles each side's ships and spends one damage pool in that order, so a
  listener cannot put the Ajax "behind" its escorts without dropping it from the battle results
  (risk: a battle that never ends) or a trick that depends on how vanilla writes its loops. Nexerelin
  extends vanilla's autoresolver and keeps the hook (`NexBattleAutoresolverPlugin extends
  BattleAutoresolverPluginImpl`; its own `NexAutoresolveListener` uses it), so any later (b) works
  under Nex. 7d-2 builds (a) only, since every launch composes a fresh Ajax, and logs every Ajax
  loss. (b) is decided from the measured loss rate.
- **Bounty yes, no weariness.** The Hegemony pays its normal per-kill bounty for Strike Force ships
  (3,000 per frigate-equivalent, scaled by hull size). Player kills add NO League war weariness:
  Midan's losses are not the League's, and rungs never move weariness.

**Shape (all inside the war director's family; no new director):**
1. **`ExileHegLeagueMidanStrikeIntel`** (extends `ExileHegLeagueRaidIntel`, League-flagged): visible
   at LAUNCH (the ruled warning window), source never named (assembly reported as "an unknown
   location", no map arrow). Composes its fleets in `createFleet` via the composer (main / escort
   by route type), spawns them straight into hyperspace, and flags them: `$noRepImpact`,
   `$cfai_makeNonHostile` (player-scoped: never attacks the player), war fleet, raider tag (the
   bounty listener). Weariness hook overridden to nothing. Tracks the Ajax: a loss is logged and
   sticky (a respawned main fleet comes back without it). Its outcome goes to the director, not to
   the ordinary op tension feed.
2. **Stages:** assemble in **hyperspace** near the anchor system's jump point (Lundy, then Sallee,
   then Midan's home), one route per fleet (main + 0–2 escorts); travel to the target's jump point;
   **`ExileHegLeagueMidanStrikeStage`** (extends `ExileHegLeagueStrikeStage`: sticky success, the
   window fix, station suppression) with the rung's payload instead of the strike package; return
   to the anchor system's **hyperspace jump point** and despawn there (never into a Black Fleet
   system, where the League-flagged fleet would fight). Every stage's return orders use that point.
   Hyperspace assembly verified from vanilla source: a route fleet is placed in the location of its
   current segment (`RouteFleetAssignmentAI.giveInitialAssignments`).
3. **Rung 1 payload:** Military Base and Orbital Station disrupted 365 days, Spaceport 180 (max
   semantics); one relief record for the base and station with a 180-day budget, so neither heals
   below six months; Recent Unrest as an ordinary strike (2, config; SUGGESTION: it should look like
   ordinary war).
4. **Launch (module):** when the opportunity score clears the bar, the rung is feasible (escorts
   0–2) and every hard rail holds: one Midan strike in flight; the target is Hegemony-owned (else
   **skip the rung**, ladder advances, ruled); not Battered or protected (else wait); an anchor
   resolves. **Build gate `midan.maxLaunchRung` = 1:** rungs 2–3 stay log-only ("WOULD LAUNCH")
   until 7e builds their payloads.
5. **Resolution:** the intel reports once (landed or foiled; player present / fought; Ajax lost).
   The module advances the rung, schedules the rebuild (2 years after success, 3 after failure) and
   writes the breadcrumb memory keys `$global.exiles_midan_rung<N>_result` ("landed" / "foiled" /
   "skipped"), `..._playerPresent`, `..._playerFought` and `..._ajaxLost` (Phase-2 hooks).
6. **Landing table (director, `landMidanRung`):** war off → the wound stands and tension and
   incident are skipped. Otherwise: landed → raise tension to at least 50 / 65 / 88; foiled → +12.
   Then by state: TENSION → the rung becomes the INCIDENT (League offender, Hegemony victim; a
   Midan entry in the incident table, weight 0, so it never rolls randomly) and the cycle runs on
   to RETALIATION; DE_ESCALATION → the cycle closes and the rung opens the next one the same way;
   INCIDENT / RETALIATION / ESCALATION / General Engagement → fold in (rung 3 upgrades a plain
   ESCALATION to GE); POLITICAL_INTERVENTION → the talks collapse at once (the existing COLLAPSE
   outcome, then the raise).

**Known and accepted:** a raid fleet that despawns and respawns (the player flying away and back)
comes back undamaged. That is vanilla RaidIntel behaviour, shared by every Exiles raid op.

**Test (one new game, stock settings; pin `testing.midanReadyNow`):** the Strike Force launches at
the first opportunity; the intel shows at launch without naming a source; the fleets assemble in
hyperspace off Lundy; the strike lands on Citadel Arcadia (base, station, spaceport disrupted;
relief convoy); the landing line shows the right state transition; the fleets despawn at Lundy's
hyperspace jump point. Second pass: fight it yourself (losses abort, bounty, no weariness, Ajax
line).
### 7d-2 build record [BUILT 2026-10-30; Janino-clean 11/11; UNTESTED IN GAME]
- **New classes:**
  - `ExileHegLeagueMidanStrikeIntel`: the op, visible at launch; composed fleets spawned straight
    into hyperspace; Ajax tracking; no weariness; outcome sent to the director.
  - Four stages: `ExileHegLeagueMidanAssembleStage`, `ExileHegLeagueMidanTravelStage`,
    `ExileHegLeagueMidanStrikeStage` (rung payload, relief, Hegemony re-check at impact, the
    player-present breadcrumb) and `ExileHegLeagueMidanReturnStage`. All of them send stragglers to
    the anchor system's hyperspace jump point.
  - `ExileHegLeagueMidanIncident`: the never-rolled table entry the rung becomes.
- **Changed:**
  - The module launches, skips non-Hegemony targets, resolves rungs and writes the breadcrumb.
  - The director gains `onMidanStrikeResolved` / `landMidanRung` (the landing table).
  - Config: the `midan.strike{}` block, including `maxLaunchRung: 1` and the rung-1 payload, plus
    the `testing.midanLaunchNow` pin.
  - The composer can build the main fleet without the Ajax.
  - `ExileHegLeagueStrikeStage` gains a `logLabel()` hook. Its output is unchanged; Midan's
    ARRIVED / WINDOW END lines read "Midan strike".
  - The log report has a Midan op ledger, and its ladder section covers the new lines.
- **New log lines** (all "Exiles Midan"): `LAUNCHED`, `strike ARRIVED`, `STRIKE LANDED`,
  `AJAX LOST`, `STRIKE OUTCOME`, `RUNG RESOLVED`, `RUNG SKIPPED`, `LANDING` / `LANDING SKIPPED`,
  `launch failed`.
- **Known gaps:**
  - The inherited "Heg-League op ABORTED" and "Heg-League RETURN LEG" lines also fire for Midan
    ops (they name the class), so the report may attach them to a Heg-League row.
  - Abstract (off-screen) fleets take no damage (vanilla), so the Ajax can only be lost in a live
    fight near the player.

#### 7d-2 test checklist (ONE new game, stock settings; pins `testing.midanReadyNow` AND `testing.midanLaunchNow` true; never commit them)
1. **Launch within a day or two:** `Exiles Midan LAUNCHED: rung 1 Citadel Arcadia ... from Lundy
   Hold (assembling in hyperspace off ...)`. The intel appears at once as "League Special Strike -
   Arcadia" with no map arrow and the text "Its point of origin is unknown".
2. **Assembly:** dev-mode look at hyperspace near Lundy. The fleets are Persean League "Strike
   Force" fleets (the main fleet has PLS Ajax) and are NOT inside Lundy's system.
3. **Arrival and landing:** `Exiles Midan strike ARRIVED at Arcadia`, then `STRIKE LANDED: rung 1
   Citadel Arcadia -- militarybase 365d, orbitalstation 365d, spaceport 180d`, then `RELIEF
   RECORDED: Citadel Arcadia ... industries=[militarybase, orbitalstation] reliefBudget=180d`.
   Citadel Arcadia's market screen shows the three disrupted, and its station is gone from the map.
4. **Landing line:** `Exiles Midan LANDING: rung 1 LANDED | state X -> Y | tension a -> b (at least
   50) | ...`. In TENSION it becomes the incident (a "Strike on a Hegemony World" telegraph) and
   the state goes to RETALIATION; the Hegemony then answers with an ordinary retaliation op.
5. **Resolution:** `RUNG RESOLVED: rung 1 LANDED ... next: rung 2 ready at day N (rebuild 730d)`.
   With the pins on, rung 2 then logs `WOULD LAUNCH (rung 2 not built yet ...)` at most monthly.
6. **Return:** the fleets fly to Lundy's hyperspace jump point and despawn there. They do NOT enter
   Lundy's system.
7. **Second pass (optional, your fleet):** intercept the Strike Force. Expect no League reputation
   loss, the Hegemony bounty, NO League weariness line, and an `AJAX LOST` line if the Ajax dies.
   A losses abort gives `RUNG RESOLVED ... FOILED` and `LANDING ... FOILED` (+12 tension).
8. **Report tool:** the op ledger shows a Midan strike row with launch, arrival and outcome.
#### 7d-2 test record [2026-10-30; new game, no Nexerelin, pins midanReadyNow + midanLaunchNow; Mar 2 → Apr 27 c206; player at Arcadia from Mar 5]
1. **Launch: PASS.** Day 0: `LAUNCHED: rung 1 Citadel Arcadia ... from Lundy Hold (assembling in
   hyperspace off Lundy, 5d) | Strike Force main 513 + 1 escort x 375 = 889 | FP 488 | defense 323
   (x2.0 = 647)`. One escort, correctly sized.
2. **Assembly in hyperspace:** not observed directly (no log line for it); no errors.
3. **Arrival and landing: PASS.** `strike ARRIVED at Arcadia: group strength 889 vs defense 202`;
   landed Apr 13: militarybase 365d, orbitalstation 365d, spaceport 180d, unrest +2.
   `RELIEF RECORDED: Citadel Arcadia industries=[militarybase, orbitalstation] reliefBudget=180d`.
4. **Landing: PASS.** `LANDING: rung 1 LANDED | state 1 TENSION -> 3 RETALIATION | tension 23.56 ->
   50.0 | became the INCIDENT`. `INCIDENT: midan_strike | offender=persean victim=hegemony`. The
   Hegemony answered at once: RETALIATION roll STRIKE, Raesvelg -> Fikenhild @ Westernesse (428 FP).
5. **Resolution: PASS.** `RUNG RESOLVED: rung 1 LANDED | player present true, fought false | Ajax
   survived (0 of 1) | rung 2 ready at day 778 (rebuild 730d)`. With the pins, rung 2 then logged
   `WOULD LAUNCH (rung 2 not built yet)` as designed.
6. **Return leg: NOT TESTED** (the run stopped before the fleets reached Lundy). The return-leg
   line was mislabelled "Heg-League" and filed as an orphan Heg-League op. FIXED the same day:
   `ExileHegLeagueRaidIntel.logLabel()` (Midan: "Midan"), plus report rows for Midan's RETURN LEG
   and ABORTED lines. Janino-clean 4/4.
7. Not tried: intercepting the Strike Force yourself (bounty, no rep loss, no weariness, AJAX LOST).
- Side note: Samarra's defence opened very high in this run (Sphinx 1128, Eventide 888), so both
  read NOT FEASIBLE even with two escorts. That's one more sample for the 7e reachability question.
### 7d-2 revision: sneak attack [RULED + BUILT 2026-10-30; Janino-clean 8/8; UNTESTED IN GAME]
**Rulings (Sean, 2026-10-30, after the 7d-2 test):**
1. **Sneak attack.** All three Midan strikes come without advance warning. Checked in Nexerelin's
   source first: Nex doesn't spawn a new defence fleet; its **special task groups** scan every live
   `RaidIntel` (hidden or not, ETA ≤ 60 days) against a system where their faction has a market, and
   go to defend it. So hiding the intel wasn't enough: the strike intel is now **not registered
   at launch at all** (vanilla `RaidIntel` advances as its own sector script; vanilla's own
   `addIntel` line in the constructor is commented out). The Strike Force forms up **inside the
   target system** at a staging point `strike.stagingDistance` (7000) from the target, on the
   bearing farthest from the jump points, the star and the player. The 44-day crossing is gone.
   Sean's "disguised neutral traveller" idea was not needed: with the intel unlisted, nothing can
   read the op in transit. Return leg unchanged (ruled fine).
2. **The only warning: an emergency call for assistance** from the target, posted with the
   colony-crisis alarm (`colony_threat`) when the leading fleet is `strike.distressLeadDays` (2)
   from the station, carrying the Hegemony bounty. It goes out early if the player fights the
   Strike Force first, and at arrival at the latest. The incident telegraphs (accusation, denial,
   later confirmation) are unchanged.
3. **Approach:** `$doNotGetSidetracked` from spawn until the action stage begins (straight for the
   target, no chasing patrols). **After landing:** `$cfai_ignoreOtherFleets` + `$doNotGetSidetracked`
   on every fleet (live now, or spawned later): they start no fights during the orbit beat or the
   trip home, but still defend themselves.
4. **Reputation (confirmed):** no League loss (`$noRepImpact`), Midan untouched (League colours),
   Hegemony +2/+3/+5 per battle by vanilla's SYSTEM_BOUNTY_REWARD (already built). **NEW:** a
   one-time **+3 Hegemony** (`strike.ajaxKillRep`) when the player helps sink the Ajax.
5. **The Ajax's captain is one person across all strikes.** Created on first launch and registered
   in ImportantPeople as `exile_midan_ajax_captain` (the Mourad pattern). Like the player's
   officers, he escapes when the Ajax is lost: out for the rest of that strike (the respawned main
   fleet has no Ajax and its best officer commands), back aboard the next rung's new Ajax. The
   monthly MEASURE builds a throwaway copy with the same level and skills, so measuring never
   re-points the real captain at a dropped fleet.
6. **Quest hook (Claude's design, cheap to change):** global memory `$exiles_midan_rungN_hold` stops
   the director launching rung N on its own; `$exiles_midan_rungN_trigger` launches it at the next
   daily check (it overrides the rebuild clock and the score, NOT the hard rails), with
   `strike.questPrepDays` (0.5) of prep, then is cleared. Both can be set from rules.csv with vanilla
   commands. This replaces the 2026-10-20 "witness" hook idea (hold at the assembly point until the
   player is near): the quest itself sets the trigger when the player arrives.

**Strike window [RULED — Sean, 2026-10-30]:** `strike.maxDays` 45 → **5**. Off-screen, a strike
does not land on arrival: it waits in-system until the window ends, then resolves on strength. Once the
call is out the op is listed, and a 45-day wait would let Nexerelin task groups arrive.

**Known side effects (Claude's judgement, flagged):** before the distress call, the op is invisible
to other Exiles directors' live-op scans too. The whole op is now about a week, and the target is
re-checked for protected/Battered status at impact. A strike broken before its call (e.g. by
Hegemony patrols at the staging point) ends silently; only the log records it.
`strike.assemblyDistance` is now unused but **kept** (player-facing settings rule): Sean to rule on
removing it.

**Built:** `ExileHegLeagueMidanStrikeIntel` (unregistered until `sendDistress`; `reveal` override;
`getCommMessageSound` → colony threat; ETA from live distance or the travel segment; in-system
spawn; approach and stand-down flags; Ajax rep), `ExileHegLeagueMidanModule` (`pickStagingPoint`,
`activeStrike`, the hold/trigger flags, prep override), `ExileHegLeagueMidanComposer` (persistent
captain), `ExileHegLeagueMidanStrikeStage` (`onActionStarted` / `onLanded` hooks), travel/assemble
stage text, config + JSON (`stagingDistance`, `distressLeadDays`, `questPrepDays`, `ajaxKillRep`),
report-tool rows (`DISTRESS CALL`, `STAND DOWN`, `AJAX CAPTAIN`; LAUNCHED reads both forms).

#### Sneak-attack test checklist (new game; pins midanReadyNow + midanLaunchNow)
1. **Launch:** `Exiles Midan LAUNCHED: rung 1 Citadel Arcadia ... | nominal source Lundy ..., home
   Lundy | staging in-system 7000 from target, clearance N, prep Xd, approach ~4.7d`. NO intel,
   NO message. `AJAX CAPTAIN created: <name>` once.
2. **Silence:** nothing on the intel screen while the fleets prep and approach. Under Nex, Hegemony
   task groups do NOT head for Arcadia before the call.
3. **The call:** `DISTRESS CALL: Citadel Arcadia (approach, ETA ~2d)`. On screen: "Citadel Arcadia
   - Emergency Call", the colony-crisis alarm, the bounty in the bullets.
4. **Staging (optional, sit in Arcadia):** the Strike Force appears far from the jump points and
   does not chase patrols on its way in.
5. **Landing:** as in 7d-2, plus `STAND DOWN: N live fleet(s)`. Afterwards the fleets do NOT go
   after surviving Hegemony patrols.
6. **Return leg** (still owed from 7d-2): fleets fly to Lundy's hyperspace jump point and despawn.
7. **Interception (your fleet):** no League or Midan rep loss, Hegemony rep up per battle, the
   bounty notice, and if the Ajax dies: `AJAX LOST ... | Hegemony rep +3.0 | captain escapes`.

#### Sneak-attack test record [2026-10-30; new game, no Nexerelin, pins on; two launches (a reload); player in Arcadia from ~Mar 4]
**What Sean saw:** the station showed "under attack", then not, then "attack over" with the station
knocked out. No Persean-flagged fleet was ever visible.
**What the log shows (both launches):** `LAUNCHED ... staging in-system 7000 from target, clearance
~12500, prep 4.1-4.8d, approach ~4.7d`. Then `ARRIVED` about 3 days after launch (instead of the
~9 that prep plus approach should take), `DISTRESS CALL (arrival)` (not ~2 days out), and
`WINDOW END ... player IN spawn range ... 5d of 5d -> striking` (autoresolve, not a live fight).
After that: STAND DOWN, RELIEF RECORDED, RETURN LEG.
**Cause (vanilla `BaseRaidStage.getStragglers`, read 2026-10-30):** with the player nearby, the
fleets spawned live at the staging point, out of sensor range at the system's edge. Then:
1. Vanilla counts a live fleet within 2000 of the gathering point as assembled, so prep was
   skipped.
2. The travel stage had `requireNearTarget` false. Vanilla then uses a range of 10,000,000, so any
   live fleet in the same SYSTEM counts as arrived. That setting was carried over from the 7d-2
   hyperspace leg. The approach was skipped too, and the fleets never left the edge of the system.
3. The action stage began with them still there. The station fell only to the 5-day window-end
   autoresolve, with nothing visible.
**Fix (Janino-clean 3/3; UNTESTED):**
- `ExileHegLeagueMidanTravelStage`: `requireNearTarget` true (within 2000 units of the station),
  and a window of 2× the abstract travel time + 2 days, so a slow live fleet isn't failed.
- `ExileHegLeagueMidanAssembleStage`: vanilla's `minDays` = prep, so the prep holds live or
  abstract.
**Retest expectation:** the fleets sit at the edge for the prep and then fly visibly to the Citadel
(about 4–5 days). `DISTRESS CALL (approach, ETA ~2d)` fires during the approach. The fight with the
station happens in front of you, and `STRIKE LANDED` comes from that fight, normally before any
WINDOW END line.

#### Sneak-attack retest record [2026-10-30; two runs, no Nexerelin, pins on]
- **Fixed and working:** `DISTRESS CALL (approach, ETA 1.9d)` and `(ETA 1.8d)`, now DURING the
  approach. The fleets became visible and reached the Citadel (`ARRIVED` about 2 days after the call).
- **Run 1 (main fleet only):** the fleet chased patrols and never attacked the station. The Citadel
  fell to `WINDOW END ... 5d of 5d -> striking` (autoresolve). Then STAND DOWN and RETURN LEG.
- **Run 2 (main + 1 escort):** the escort attacked the Citadel; Sean helped the station and
  destroyed the escort. Sean then fought the main fleet and lost (the Ajax retreated badly damaged,
  98 FP destroyed). The Citadel still fell at WINDOW END (510 vs 222).
- **Bounty:** PAID both times (`raider bounty: 78037c ... fpDestroyed=224` and `54920c ...
  fpDestroyed=98`, with SYSTEM_BOUNTY_REWARD reputation), but Sean saw no notice either time.
- **Causes:**
  1. Vanilla puts live raiders on a system patrol, and its military-response script sends only a
     SHARE of them at the target (respondingFP / potentialFP below the response fraction). Our
     `onActionStarted` also lifted `$doNotGetSidetracked` at arrival, so the free fleet chased patrols.
  2. The bounty notice is flushed from `advanceImpl`, which vanilla stops calling once an intel is
     ENDING. A fight after the landing (the op ends right after its action stage) was paid silently.
     This also applies to every Heg-League op. The pre-landing notice (run 2, fight 1) should have
     posted; nothing logged it either way.
- **Fixes (Janino-clean 5/5; UNTESTED):**
  - `ExileHegLeagueMidanStrikeStage.orderAssault()`, every 0.25 day from arrival until the blow
    lands. Each live Strike Force fleet in the system not in a battle gets an order at the front of
    its queue: INTERCEPT the station fleet while it stands, then GO TO the station entity once it is
    down (which puts them inside vanilla's 2000-unit raid check). Logged as `ASSAULT ORDER` (at most
    daily).
  - `$doNotGetSidetracked` is kept through the action stage (`onActionStarted` removed). Fleets
    still defend themselves.
  - `ExileHegLeagueRaidIntel`: a bounty queued while the intel is ending is announced at once, and
    every posted notice is logged (`BOUNTY NOTICE posted: Nc | rep N | intel visible/HIDDEN`).
- **Open for Sean:** a strike with the player present still lands at WINDOW END on strength (the
  2026-09-28 ruling, made for Heg-League strikes). With the assault orders this should be rare,
  because the fleets now fight the station itself. Sean decides whether a Midan strike watched by the
  player should land ONLY through a visible victory.
- **Window-end ruling [RULED — Sean, 2026-10-30]:** a Midan strike with the player in spawn range
  lands ONLY through a visible victory. If the 5-day window ends first, the Strike Force BREAKS OFF
  and the strike is FOILED (`WINDOW END ... -> BREAKING OFF: no visible victory with the player
  present`). Off-screen it still resolves on strength. Built in
  `ExileHegLeagueMidanStrikeStage.autoresolve()` / `logWindowResolution()` (Janino-clean; UNTESTED).
- **Same-bug review of the other directors:** done (read-only). Ashen, Black Fleet and Omicron share
  the silent-bounty bug. Logged as BUG-2026-10-30-A in `EXILES_SWEEP_AND_TOOLING_PLAN.md` Part 4, to
  fix after the Heg fix is verified.
- **Window [RULED — Sean, 2026-10-30, superseding the earlier 5]:** `strike.maxDays` **10**, a
  single window. With the visible-victory rule, 5 days could let the clock, not the player, foil a
  watched strike. Held in reserve if a Nexerelin test shows task groups reaching Arcadia off-screen:
  separate windows (live 10 / off-screen 5). First measurement: the STRIKE OUTCOME line's "after N
  days of M".

#### Sneak-attack test 3 record [2026-10-31; new game, no Nexerelin, pins on; Mar 2 → Jun 26 c206]: **PASS**
- **Launch → call → arrival:** staging 7000 from the target, prep 4.6d. `DISTRESS CALL (approach,
  ETA 1.8d)`, then `ARRIVED ... 889 vs defense 227 ... window 10d`.
- **Assault:** `ASSAULT ORDER: 2 live fleet(s) -> INTERCEPT the station` at once (and again on day 1
  for one fleet). The station fight was visible; Sean took part.
- **Bounty:** two kills (15,468c + 44,195c), then `BOUNTY NOTICE posted: 59663c | rep 5 | intel
  visible`. Sean saw it. Fix confirmed for the pre-landing case.
- **Landing:** `STRIKE LANDED ... player in range`. Outcome line: "after 5d of 10d | player present
  true, fought true". That is the **first window measurement: a live, player-contested assault
  landed on day 5 of 10.**
- **After landing:** STAND DOWN (2 fleets), relief recorded and delivered (180d over 10 unload days),
  RETURN LEG.
- **Incident chain:** TENSION → RETALIATION, tension 24.4 → 50. The Hegemony's reprisal (surgical
  strike Yama → Madeira) launched at once and landed Jun 2 at its 45-day window end, off-screen.
- **Afterwards (RNG):** Hegemony patrols in hyperspace caught the homeward Strike Force: `AJAX LOST:
  battle in Hyperspace | player not involved` (no rep bonus, correctly). The captain survives for
  rung 2.
- **Report tool:** the op ledger had no Midan section (hard-coded director list). FIXED 2026-10-31,
  now in the ledger.
- **Seen; FIXED 2026-10-31 (Janino 3/3, untested):** the Ajax was lost AFTER the rung resolved, so `ajaxLosses` and the
  `_ajaxLost` breadcrumb say "survived". The protection-(b) measurement misses losses on the way home.
  Also, `relief drip` wrote 132 lines over a 10-day unload (noise).

### Post-Pass-7 complexity review: agenda items (Sean, 2026-10-31: hold until Pass 7 is complete; RULED 2026-11-07: it runs BEFORE Pass 9, so the independent reviewer sees the simplified director)
1. **"Contained" after a Midan strike reads anticlimactic.** It is mostly arithmetic, not luck: rung 1
   raises tension to 50, below hawk (60). So the RETALIATION exit can only be talks (30%) or CONTAINED
   (70%); escalation is impossible on rung 1 by design (escalation builds over rungs 2–3: 65, 88).
   Also, the exit came ~20 days in, while the Hegemony's reprisal was still sailing. It landed about
   two months AFTER "successfully contained". The only player-facing ending the strike produced was
   that message.
2. The wider review Sean asked for: the complexity of this and the other directors.
- **Late Ajax loss FIXED 2026-10-31:** `checkAjax` notices that the outcome is already reported and
  calls `ExileHegLeagueMidanModule.recordLateAjaxLoss(rung)`. That bumps `ajaxLosses`, rewrites
  `$exiles_midan_rungN_ajaxLost` to true, and logs `AJAX LOST AFTER RESOLUTION`. There was no
  gameplay impact before: the rebuild clock ignores the Ajax and every rung composes a new one.

### 7e-1 — Sphinx (rung 2) + the Diktat embargo [BUILT 2026-10-31; Janino-clean 8/8; UNTESTED IN GAME]
**Rulings this step (Sean, 2026-10-31):**
- **Reachability, option A:** escort caps per rung **2 / 4 / 4** (`strikeForce.escortCapPerRung`),
  margin unchanged at 2×. "Midan would make sure their blow had a good chance to land."
- **New ships, fixed escort mix:** every escort gains **1 Conquest** (`exiles_midan_conquest_standard`,
  base `conquest_Standard`, S-modded Insulated Engine, "Customized", officered) and **2 Medusas**
  (`exiles_midan_medusa_attack`, base `medusa_Attack` = railguns, heavy blasters, IR pulse, PD:
  a strike ship; S-modded Insulated Engine + Hard Shields; the freed 10 OP went to flux capacitors,
  10 → 20, still 100/100 OP). The Hammerheads stay (4). Escort: 23 ships, 272 FP (ceiling 300). The
  Ajax's main fleet is unchanged.
- **Medusa skin** `data/hulls/skins/exile_medusa.skin` → `graphics/ships/exilemedusa1.png`, a
  PLACEHOLDER byte copy of vanilla's Medusa at an Exiles path (the Omen pattern). Tags `no_dealer`,
  `no_bp_drop`, so no faction knows the hull.
- Per-rung escort roster (the code option): NOT built (Sean: A only for now).

**Built:**
- **Config:** `escortCapPerRung`, `midanEscortCapFor(i)`; rung 2 payload (fuelprod 810, battlestation
  540, militarybase 540, spaceport 180; relief over all four, 270 days; `diktatEmbargo: true`);
  `maxLaunchRung` 1 → 2; a new `midan.embargo{}` block (delay 7, duration 730, `fuelHitUnits` 3
  ASSISTANT DEFAULT, `fleetSizeMult` 1.0 = off, `noticeDays` 30); test pin `testing.midanStartRung`.
- **`ExileDiktatFuelEmbargo`** (market condition, `ExileCommodityShortageCondition` subclass):
  fuel-only, no import picks; optional fleet-size multiplier (off at 1.0); its timer is an orphan
  guard (duration + 60). CSV row with placeholder name, text and icon (vanilla `trade_disruption.png`).
- **`ExileDiktatEmbargoController`** (EveryFrameScript, daily, removes itself). Lifecycle:
  SCHEDULED at the landing (the market list fixed then; SKIPPED if the Diktat doesn't hold Sindria,
  or if an embargo already happened) → STARTED after 7 days (announcement + condition together)
  → LIFTED after 730 days or on `$exiles_diktat_embargo_lifted` ("lifted" notice; the flag set
  before the start CANCELS silently). State is mirrored in `$exiles_diktat_embargo_state` for
  quests. Log lines: `Exiles Midan EMBARGO SCHEDULED/SKIPPED/STARTED/CANCELLED/LIFTED`.
- **Strike stage:** snapshots `diktatEmbargo` at launch and schedules the embargo only from a
  LANDED strike.
- **Text:** the emergency call names its target instead of "the station" (Sphinx is a planet).
- **Report tool:** EMBARGO lines in the Midan section.

**Not yet verified:** the two new variants load (startup check: the game must reach the main
menu; then search `starsector.log` for `exiles_midan_medusa`, `exile_medusa`,
`exiles_midan_conquest_standard`).

#### 7e-1 test checklist (new game; pins midanReadyNow + midanLaunchNow + midanStartRung 2)
1. **Load:** the game reaches the main menu; no errors naming the new variants or the skin.
2. **Start:** `TEST PIN midanStartRung -> the ladder starts at rung 2`, then `COMPOSED: ... escort 23
   ships 272 FP strength N (6 officers) | escort caps 2/4/4`. The new escort strength is the first
   number to read.
3. **Launch:** `LAUNCHED: rung 2 Sphinx in Samarra ... N escort(s)` (needs up to 4). If it logs
   `BLOCKED (... NOT FEASIBLE)`, Sphinx's defence was above what 4 escorts reach that day; it
   re-checks daily.
4. **The strike:** the emergency call from Sphinx, the assault on the battlestation, and `STRIKE
   LANDED: rung 2 Sphinx -- fuelprod 810d, battlestation 540d, militarybase 540d, spaceport 180d`.
   Relief recorded with 270 days. Tension raised to at least 65.
5. **Embargo:** `EMBARGO SCHEDULED ... N market(s)` at the landing (the list is printed). About 7
   days later: `EMBARGO STARTED`, the Story announcement "Sindrian Diktat Declares Fuel Embargo",
   and the "Diktat Fuel Embargo" condition on, e.g., Orthrus, Agreus, Nomios, Asharu, showing fuel
   short and prices up.
6. **Optional:** set `$global.exiles_diktat_embargo_lifted` to true with the console. Within a day:
   `EMBARGO LIFTED: early`, the "lifted" notice, and the condition gone everywhere.

#### 7e-1 test record [2026-11-01; new game, no Nexerelin, pins ReadyNow + LaunchNow + StartRung 2; a reload]
- **Load: PASS.** The new variants and the Medusa skin loaded; no errors. **Reachability:** escort
  measured **467** (23 ships, 272 FP, 6 officers). Launched day 0: `main 513 + 3 escorts x 467 =
  1915 | FP 1080 | defense 910 (x2.0 = 1821)`. Three of the four allowed escorts.
- **Run 1 (NPC only): the strike FAILED, not landed.** No `STRIKE LANDED`; Sphinx was never
  disrupted. `op ABORTED in stage 0 (AssembleStage) after 66d ... remaining FP 0` → `STRIKE FAILED
  after 0d | distress call never sent` → `RUNG RESOLVED: FOILED` (+12 tension, 1095-day rebuild).
  The salvage log shows seven battle debris fields in Samarra between Mar 9 and May 8: the four fleets
  parked at the staging point fought Hegemony patrols for two months. Sean saw one of those wins.
- **Run 2 (reload of Apr 29; Sean engaged):** the op was still stuck in stage 0. Fighting it sent
  `DISTRESS CALL (player contact)`; bounty 49,122 + 48,195 c; `AJAX LOST ... player involved |
  Hegemony rep +3.0`; `BOUNTY NOTICE posted: 97317c | rep 10 | intel visible`. **Bounty notice and
  Ajax bonus: PASS.**
- **Causes:**
  1. Vanilla `AssembleStage` needs EVERY live fleet within 2000 units of the gathering point at once.
     Live fleets kept fighting and never all stood together.
  2. Vanilla `RaidIntel.extraDays` (60) let the stage overrun 60 days before failing.
  3. Nothing stopped the parked fleets from starting fights.
  4. A failed op's fleets went home without the stand-down flags (only a landing applied them),
     which likely explains the "confused" exit Sean saw. Vanilla jump-point pathing in a
     many-jump-point system may also contribute; not separable from the log.
  The missing or late emergency call was a symptom: the call fires on the approach, which run 1
  never reached.
- **Fixes (Janino-clean 5/5; UNTESTED):**
  - `MidanAssembleStage.updateStatus`: after the prep, live fleets anywhere IN THE TARGET SYSTEM
    count as assembled (vanilla `requireNearTarget` false). The approach stage still requires them
    near the target.
  - **Quiet staging:** `$cfai_ignoreOtherFleets` from spawn until the attack begins (they start no
    fights, but defend themselves); lifted in the action stage's `updateRoutes`.
  - **Return leg:** stand-down flags on every fleet when the op leaves its action stage or fails,
    landed or not (`STAND DOWN (return leg)`). Landing logs `STAND DOWN (landed)`.
  - **`strike.extraDays` 5** (ASSISTANT DEFAULT, not ruled; vanilla 60): a stuck stage fails in
    days, not months.
- **Main fleet grown [Sean, 2026-11-01]:** + 1 `exiles_midan_conquest_standard` (officered, as in the
  escorts; ten officers now) + a third Gryphon. 26 ships, **302 FP**, so the Ajax's fleet is at least
  as big as an escort (272). **`fpCeiling` 300 → 320** (per fleet; at 300 the main fleet would block
  every rung). Expected strength about 590 (unmeasured; the next COMPOSED line gives it). Fewer
  escorts only at the margin: at Sphinx's measured 1,821 requirement, 3 are still needed; 2 suffice
  at about 1,500 or less.

#### 7e-1 retest record [2026-11-01; new game, no Nexerelin, pins ReadyNow + LaunchNow + StartRung 2]: **PASS**
- `COMPOSED: main 26 ships 302 FP strength 590 (11 officers incl. captain) | escort 23 ships 272 FP
  strength 467`. The main fleet's strength matched the 590 estimate.
- `LAUNCHED: rung 2 Sphinx ... main 590 + 4 escorts x 467 = 2459 | defense 1209 (x2.0 = 2419)`. All
  four escorts were needed: Sphinx's defence was 1,209 at launch (910 in the previous run), so five
  fleets attacked. At arrival the defence was only 689.
- Prep about 6 days, quiet. `DISTRESS CALL (approach, ETA 1.8d)` during the approach (FIXED).
  `ARRIVED ... 2449 vs 689`; `ASSAULT ORDER: 5 live fleets`.
  `STRIKE LANDED: rung 2 Sphinx -- spaceport 180d, militarybase 540d, fuelprod 810d, battlestation
  540d`. Relief 270 days. `STAND DOWN (landed)`, then `(return leg)`; a clean exit.
- `RUNG RESOLVED: LANDED ... after 6d of 10d`. Tension 24.4 → 65.0, the INCIDENT, RETALIATION; the
  Hegemony answered with a strike on Suddene.
- `EMBARGO SCHEDULED: 17 markets in 7 systems` (Ancyra, Chicomoztoc, Coatl, Tigra City, Eventide,
  Orthrus, Sphinx, Asharu, Jangala, Abandoned Terraforming Platform, Raesvelg, Ragnar Complex, Nomios,
  Citadel Arcadia, Agreus, Yama, Nachiketa). `EMBARGO STARTED` 7 days later.
- The "Hegemony Detachment" Sean saw is vanilla's name for a large patrol (`patrolLarge` →
  "Detachment", `default_fleet_type_names.json`). It is Sphinx's own military-base patrol, not ours.

### OPEN — the embargo's form [Sean, 2026-11-01: "works as asked; I'm questioning the decision"; LEFT AS-IS pending his ruling]
Sean's observations:
- Nachiketa (Hegemony, a fuel producer) is effectively blocked from exporting by the embargo.
- Limiting the embargo to the Hegemony and its local worlds now feels like a mistake.
Why it happens: the condition cuts the fuel AVAILABLE on every listed market, producers included,
while in fiction an embargo stops Sindrian fuel coming in. Claude's suggestions are in Sean's session
reply of 2026-11-01 (source-side cut at Sindria / exempt producers / wider scope). Nothing changes
until Sean rules.
- **RESOLVED 2026-11-01 [Sean]: the embargo is REMOVED** (war doc sec 6.1 rung 2 has the reasoning and
  the consequence for rung 2's reach). Read-only findings that informed it (decompiled
  `CommodityMarketData`, `CommodityOnMarket`; `CommodityIconCounts`; Sean's in-game fuel and volatiles
  screens):
  - **Global imports ignore faction relations;** hostility only lowers accessibility. (This corrects
    Claude's earlier claim.)
  - **Every importer is capped by the single best exporter's capacity.** A market exports its whole
    production (not just the surplus), up to accessibility × 10 units per commodity; in-faction gets
    +5 units. Its own accessibility also caps its imports.
  - **Demand is the largest single industry's, not the sum.** Fuel production loses output one for
    one with any volatiles shortfall.
  - **In-game numbers:** fuel exporters Sindria 9, Madeira 5, Elpis 4, Sphinx 4, Yesod 3, Nachiketa 3
    (vanilla formula + 1, likely admin or AI core bonuses). Sindria needs 9 fuel and 7 volatiles; it
    makes 0 volatiles and buys them from Eventide (7, best in the sector; Ilm 6). Game-start fuel
    shortages are accessibility-driven: Jangala −3, Qaras −2, Coatl −1, Port Tse −1.
  - **Hidden link to keep in mind:** rung 3 shrinks Eventide 7 → 6, likely cutting its volatiles to 6
    and so Sindria's fuel by 1. Confirm in a rung-3 test.
  - Removed: the controller, the condition and its CSV row, `midan.embargo{}`, the `diktatEmbargo`
    flag, the strike-stage call and the report regex. Janino-clean 6/6; JSON balanced.
  - **Save note:** a save made while an embargo was scheduled or active will not load (the classes
    are gone). Old test saves only; new games are unaffected.

### 7e-2 — Eventide (rung 3) + Cunningham's vow [BUILT 2026-11-01; test fixes 34c316e; RETEST PASS 2026-11-01]
**Rulings this step (Sean, 2026-11-01):**
- **The sequence starts after a visible win.** The Strike Force must beat Eventide's defenders (the
  raid AI's own raid call); then the mirrors go one by one.
- **Split:** 4 destroyed, 1 adrift, 3 untouched ("something of Eventide visibly survives"; the
  array bonus is still removed). Claude's choice of which ones (config, cheap to change): destroyed
  are the centre mirrors Beta, Gamma and Delta plus the centre shade Psi; mirror Epsilon goes adrift;
  mirror Alpha and shades Omega and Chi are untouched. Order: Gamma first (the array dies with it),
  Psi, Beta, Delta, Epsilon adrift, then the bombardment.
- **Pace:** 0.6 day per beat (6 beats, about 3.6 days on-screen).
- *Assistant suggestion, not ruled:* the survivors make a Phase-2 "restore Eventide's sky" quest
  plausible.

**Built:**
- **`ExileEventideWound`** (shared helpers, so the on- and off-screen wounds cannot drift):
  - Mirrors and shades are found by custom entity type and NAME (vanilla's shades reuse the mirrors'
    ids).
  - `killArray` (removes `solar_array`).
  - `applyBeat`: "destroy" means vanilla's explosion entity (fleet damage NONE), then removal, then
    a thin no-salvage debris field orbiting where it hung. "Adrift" means a plain wider orbit plus the
    `exiles_eventide_wrecked_mirror` description. This is the Hybrasil pattern.
  - `bombard`: farmland adequate → poor; size via vanilla `CoreImmigrationPluginImpl.reduceMarketSize`
    (no player reputation effects); the planet description becomes `exiles_planet_eventide_struck`;
    the illustration is swapped only if the config names one.
  - `applyAll` (off-screen: everything at once, no explosions).
- **`ExileEventideSequence`** (saved EveryFrameScript):
  - **Timing:** beats every `beatDays`, the first at once; the array dies with the first beat; the
    bombardment is the last beat and calls `stage.onEventideSequenceComplete`.
  - **If the Strike Force is broken:** the stage fails and the script STOPS. What fell stays fallen;
    no bombardment and no size loss, so the rung is FOILED (+12), as ruled.
  - **Saved state:** the beats are snapshotted at the start.
- **`ExileHegLeagueMidanStrikeStage`:**
  - **Rung 3 branch:** after the usual rails, `performEventide`.
  - **Off-screen** (no live fleet, or the player out of range): `applyAll`, and the rung lands now.
  - **On-screen:** it starts the sequence, stretches the window over it, holds the fleets over
    Eventide and stops the assault orders.
  - `landEventide`: strikeDone, unrest, deciv-guard pressure (as for 365 days), the orbit hold and
    the stand-down.
  - Stage text for the sequence and for success.
- **Cunningham's vow:** `ExileHegLeagueMidanModule.postCunninghamVow` runs on a LANDED Eventide
  rung only, through the new generic `ExileStoryAnnouncementIntel.postAfter(delayDays, ...)` (a saved
  `DelayedPost` script). Hegemony crest colours, the `exile_cunningham` portrait, PLACEHOLDER text,
  5 days after landing, 30 days on the board.
- **Config:**
  - Rung 3 payload `eventideWound: true`; `maxLaunchRung` 2 → 3.
  - New `midan.eventide{}`: beats, `beatDays`, farmland from/to, `sizeLoss`, description ids,
    `illustration` (empty = keep vanilla's), vow delay and notice days.
  - Two `descriptions.csv` rows (PLACEHOLDER prose).
- **Report tool:** `EVENTIDE` and `VOW` lines in the Midan section.

**Known risk (by reading, unmeasured):** Eventide has no station, so the assault orders send the
fleets to the planet and vanilla's raid check waits until no strong hostile fleet is within 2000. If
Eventide's patrols keep coming with the player present, the 10-day window can end first, and the
strike then BREAKS OFF (the visible-victory rule). Watch for it.

#### 7e-2 test checklist (new game; pins midanReadyNow + midanLaunchNow + midanStartRung 3)
1. `TEST PIN midanStartRung -> rung 3`; `LAUNCHED: rung 3 Eventide in Samarra`. Eventide has no
   station, so expect fewer escorts than Sphinx.
2. The emergency call from Eventide, the approach, `ASSAULT ORDER ... GO TO Eventide (station down)`.
3. **On-screen** (be in Samarra): after the win, `EVENTIDE SEQUENCE STARTED`, then `EVENTIDE BEAT
   1/5: Eventide Stellar Mirror Gamma destroyed` ... `5/5: ... Epsilon knocked adrift`, each with an
   explosion and a small debris field; then `EVENTIDE BOMBARDMENT: array removed, ..., farmland_adequate
   -> farmland_poor, size 7 -> 6`; then `STRIKE LANDED: rung 3 Eventide`.
4. **Check Eventide:** size 6, no Orbital Solar Array, poor light, poor farmland, the new description;
   mirror Alpha and shades Omega and Chi untouched; Epsilon tumbling on a wider orbit with its own
   description.
5. **Landing:** tension ≥ 88. In ESCALATION it becomes General Engagement; otherwise it becomes the
   incident. 5 days later, `Exiles story announcement posted: "Admiral Cunningham's Vow"` with his
   portrait.
6. **Food and fuel** (the market screens): food from the farmland and size loss. Also Sindria's fuel:
   Eventide's volatiles should drop to about 6, so Sindria goes 1 volatile short and makes 1 less fuel.
7. **Optional, break it mid-sequence:** `EVENTIDE SEQUENCE STOPPED ... no bombardment`, the rung
   FOILED (+12), no vow, and Eventide still size 7.
8. **Optional, off-screen** (leave Samarra before arrival): everything lands at once at the window
   end.
- **REVISION 2026-11-01 [Sean]: a full vanilla saturation bombardment** (war doc sec 6.1 rung 3,
  "REVISED 2026-11-01"; Janino-clean 6/6; UNTESTED):
  - **The last beat** is now `ExileEventideWound.saturationBombard`, which calls
    `new MarketCMD(planet).doBombardment(<strike flag>, SATURATION)`, vanilla's own NPC path (as
    `PEActionStage`). Logged as `SATURATION BOMBARDMENT (vanilla): size 7 -> 6, N industries
    disrupted, pollution, stability -10`.
  - **Removed:** the farmland scorch, `eventideSizeLoss` and the strike's own +2 unrest on Eventide.
    These were only added in 7e-2, so no shipped setting is affected.
  - **Kept, optional:** `planetDescription` now defaults to "" (OFF). The CSV row stays.
  - **Emergency Measures:** the landing reports Exiles pressure sized to the bombardment
    (`pressureDays(365 × 1.25, 10)`). The `STRIKE LANDED` line prints "Emergency Measures armed
    (pressure Nd)".
  - **Checklist update** (7e-2 test, items 3–6):
    - Expect the bombardment animation over Eventide at the last beat.
    - Afterwards Eventide shows Pollution, size 6, Stability −10 from "Saturation bombardment", and
      most industries disrupted for about a year.
    - If Stability sits at 0 for 60 days, Emergency Measures activates.
    - The market screens should show the food squeeze and Sindria's volatiles drop for the year.

#### 7e-2 test record (2026-11-01; new game, pins on, run twice via a reload)
- **Worked:** launch, distress call (ETA 1.9d), arrival, the win, five beats, the vanilla saturation
  bombardment (`size 7 -> 6, 6 industries disrupted, pollution, stability -10`), `STRIKE LANDED`,
  tension 24 -> 88 and the incident, Cunningham's vow posted 5 days later, and **Emergency Measures
  activated at Eventide** (`underlying=-1.0, reserve=2`). Ajax survived.
- **Escort count (Sean's question): not a bug.** The launch sizes the force on in-system strength:
  vanilla `WarSimScript.getEnemyStrength` (every non-station, non-trade fleet of a faction hostile to
  the League in Samarra) plus the target's own station within 3x battle-join range. Eventide's line
  read `defense 888 = enemy 888 + station 0`: Sphinx's battlestation was NOT counted. 888 x 2 = 1776
  required, minus the main fleet's 590, is 3 escorts. 888 is a day-0 figure (the test pin launches
  at game start, when Samarra is fullest); the module's 30-day re-measures afterwards read 465-512,
  i.e. **one** escort for Eventide and two for Sphinx. The measure does not depend on the player.
- **BUG A, staggered arrival.** Each route travels at its own fleet's speed; the fast fleets reached
  Eventide and waited for the slow ones (`ASSAULT ORDER: 2 live fleet(s)` on day 0, `1` on day 1).
- **BUG B, the sky fell with no fleet in orbit.** The sequence start reset the routes to an orbit
  segment; the route AI turns that into ORBIT_AGGRESSIVE (engages like a patrol), so in Samarra's
  traffic the fleets left the planet, while the sequence ran on a timer that ignored them.
- **FIX (Janino-clean 5/5; UNTESTED), on vanilla's templates:**
  - **A:** `ExileHegLeagueMidanTravelStage.keepTogether()`, cut down from `FGTravelAction`: with
    every fleet live in the target system, a fleet more than the leash (750, +100 per fleet over
    five) ahead of the FP-weighted centre HOLDs in place ("forming up") until the centre is within
    40% of the leash. Logged `FORM UP`.
  - **B:** `ExileHegLeagueMidanStrikeStage.holdOverEventide()`, the Diktat punitive expedition's
    bombardment posture (`BaseAssignmentAI.giveRaidOrder` under `FGRaidAction`): a token on the
    planet's edge, DELIVER_CREW to it ("moving to bombard Eventide"), then HOLD ("bombarding
    Eventide"). HOLD starts no fights; an attacked fleet still fights back. Re-issued every quarter
    day, so a fleet pulled into a battle returns. Logged `EVENTIDE HOLD`. The raid AI is kept out
    once the sequence starts (`canRaid` false).
  - **B:** `ExileEventideSequence`'s clock runs only while a Strike Force fleet is over Eventide
    and out of battle (`EVENTIDE SEQUENCE PAUSED` / `RESUMED`); checked every 0.1 day, not every
    frame. If the player leaves spawn range mid-sequence, the rest lands at once (as off-screen),
    logged `EVENTIDE BOMBARDMENT (completed off-screen: the player left)`.
  - The window is stretched by twice the sequence length (the second is slack for pauses). If the
    player keeps the fleets off the planet past it, the strike breaks off: FOILED, what fell stays
    fallen, no bombardment (the 2026-10-14 failure refinement).
- **Retest checklist:** `FORM UP` lines on the approach and the fleets arriving together; after the
  win, `EVENTIDE HOLD`, all fleets sitting on Eventide's edge reading "bombarding Eventide" through
  every beat and the bombardment; then `STRIKE LANDED` and the stand-down orbit.

#### 7e-2 retest record [2026-11-01; Sean's report, pins on]: **PASS ("looked much better")**
- The form-up and Eventide-hold fixes behaved. One vanilla reaction remains, accepted as cosmetic
  (Sean, 2026-11-01): Hegemony patrols, far weaker than the Strike Force, will not engage it, but
  shadow it and fire interdiction pulses. The Midan fleets steer out of pulse range, which scatters
  them and drags them off Eventide's edge; the hold re-orders them back every quarter day.
- **Why there is no simple vanilla switch (decompiled 2026-11-01):** each pulse attaches an
  `InterdictionPulseAbility.IPReactionScript` to every fleet in range. It writes two
  `avoidLocation` zones straight into the target's navigation module, checking no memory flag and
  no assignment. Its only exits are: the pulser cannot see the target, the target is already
  pursuing the pulser and can reach it in time, or a one-in-three sensor burst. The one flag that
  sounds right, `$doNotTryToAvoidNearbyFleets`, is read only in `TacticalModule`'s non-hostile
  bump-avoidance branch, which HOLD already suppresses. It does nothing here.
- **Not built (assistant note, for the polish pass if it ever matters):** the navigation module
  exposes `clearAvoidList()`. Calling it every frame on held fleets would cancel the zones, but it
  also wipes terrain and hostile-fleet avoidance, and it needs a per-frame script. That is not worth
  it for a cosmetic issue.

### 7f plan — the Black Fleet proxy [rulings: Sean, 2026-11-01, all six per Claude's recommendation]
Ruled design: war doc §6.2 PASS 7 PROXY DESIGN (2026-10-19). Build details ruled today:
1. **Visibility:** no intel for an ambush. A campaign message fires only if the player is in the
   system when the raiders appear. Misattribution is log-only. The backfire beat (if built, see
   below) is the one Story intel, with placeholder text for Sean.
2. **Bounty:** yes. Ambushers are hooked to the existing Black Fleet per-kill bounty, like raid fleets.
3. **Prosperity:** none in v1. Ambushers never return home, and shared prosperity pays only on return.
4. **Exit:** after the intercept, the ambushers make for the system's jump point and despawn there,
   with a time limit if they never catch the convoy. They do not fly home to Lundy.
5. **Trigger and spawn:** the trigger is the first daily check that finds the marked convoy in the
   directive's system, travelling or unloading. The vanilla "enters the system" rule never fires
   when the source port is in the same system. The ambushers spawn at the jump point farthest from
   the convoy, so the player can see them coming.
6. **`RAID_FOCUS`: SKIPPED for now.** Sean does not recall whether the rung-1 runs showed a natural
   Black Fleet follow-up, so the ruled default (do not build) holds.

**OPEN (Sean, 2026-11-01): build the attribution layer now, or defer it?** That layer is the
misattribution +6 and the backfire counter (threshold, −10, Story intel, 1-year lockout). It is
tied to the post-Pass-7 complexity review. The options are in the reply of 2026-11-01.

**RULED (Sean, 2026-11-01): build WITHOUT the attribution layer.** The misattribution +6 and the
backfire counter (threshold, −10, Story intel, lockout) are deferred to the post-Pass-7 complexity
review. Reasons, from the discussion:
- Adding them later is cheap: both hang off the one "counted proxy kill" event that 7f builds anyway.
- Removing them later costs more: saved state, a config block, intel text and doc rulings.
- The backfire needs three kills to test once.
- They add a new lever on the tension meter, whose cooling took two long runs to confirm.

The kill is logged (`Exiles Midan PROXY KILL`) so the review has the real rate.

### 7f build record [BUILT 2026-11-01; Janino-clean 9/9; UNTESTED IN GAME]
- **`ExileMidanDirective` (new, shared vocabulary).**
  - One sector key, `$exiles_midan_directive`, holding `TYPE|marketId|serial`. The serial is the
    write timestamp, so the reader acts on each directive once.
  - `CONVOY_AMBUSH` is the only type.
  - Also defines the convoy's destination key, `$exiles_hl_relief_dest`.
  - The memory expiry is a backstop only (lag + travel timeout + unload + 10 days).
- **War side: `ExileHegLeagueMidanModule.offerProxyDirective`** (static, stateless).
  - Called from `ExileHegLeagueRecoveryManager.recordImpl` for every new relief record.
  - Gates: `midan.enabled`, `midan.proxy.enabled`, the Midan faction exists, and no directive is
    already active.
  - Rolls `directiveChance` 0.5; the test pin `testing.midanForceDirective` makes it always succeed.
  - Logs `DIRECTIVE WRITTEN` / `DIRECTIVE NOT WRITTEN`.
- **War side: `ExileHegLeagueRecoveryManager`.**
  - Tags each convoy with its destination and listens to its battles (`FleetEventListener` on the
    convoy fleet).
  - `lastBattleBlackFleet` is true when the Black Fleet was among the attackers and the player was not.
  - The loss branch logs `Exiles Midan PROXY KILL` when a directive names the destination. A
    Black Fleet kill without a directive is logged as not counted.
  - `releaseDirective` clears the directive (`DIRECTIVE CLEARED`) once nothing is left to ambush:
    unload finished, convoy finished, record expired, market gone or changed hands, or the record
    was dropped because the ledger was full.
- **Black Fleet side: `BlackFleetRaidManager`, third tier.**
  - Runs the live ambush every 0.25 day.
  - Otherwise checks daily for an unacted directive whose convoy is in its destination's system.
  - One at a time, one per directive.
  - Gated by `ambush.enabled` and the raid kill switch `$exilebf_raids_enabled`.
  - FP 40–60 × the raid ramp. No living anchor means no ambush (logged).
- **Black Fleet side: `ExileBlackFleetAmbush` (new; saved op and listener).**
  - Spawns "Raiders" (destroyer cap) at the jump point farthest from the convoy and gives them an
    INTERCEPT on the convoy for `huntDays` (20).
  - Then a GO_TO_LOCATION_AND_DESPAWN to the nearest jump point. After `leaveDays` (15) a straggler
    is removed, but only out of the player's sight.
  - A campaign message fires only if the player is in the system at spawn (PLACEHOLDER text).
  - When the player kills raiders, the convoy's side pays the bounty through the shared
    `payRaidedSideBounty`; the notice is deferred 0.2 day.
  - No prosperity.
  - Logs `AMBUSH LAUNCHED` / `NOT LAUNCHED` / `LEAVING` / `ENDED`.
- **Config.**
  - `exileblackfleet.ambush{}`: enabled, fpMin 40, fpMax 60, maxShipSize 2, huntDays 20, leaveDays 15.
  - `exilehegleague.midan.proxy{}`: enabled, directiveChance 0.5.
  - `testing.midanForceDirective`: false.
  - No existing key was moved or renamed.
- **Log report:** a new section, "Midan proxy (7f)".
- **Noted, not changed:**
  - The Black Fleet ambush calls one war-side static helper (`ExileHegLeagueBattleRewards`) for the
    bounty. It is stateless and shared, the same arithmetic as every other per-kill bounty.
  - The payout log line reads "Heg-League raider bounty", but the logger class names the ambush.
  - `$isRaider` is deliberately NOT set, because vanilla's colony-action check reads it.
- **Save note:** new fields are null or false on old saves; no migration.

#### 7f test checklist (new game, stock settings plus pins)
- **Pins:** `midanReadyNow` true, `midanLaunchNow` true, **`midanStartRung` 1 or 2** (rung 3 sends no
  relief), **`midanForceDirective` true**.
- **Expected sequence:**
  1. The rung lands.
  2. `RELIEF RECORDED`, then `DIRECTIVE WRITTEN ... TEST PIN`.
  3. 20–45 days later, `RELIEF CONVOY LAUNCHED`.
  4. The day the convoy is in the target system: `Black Fleet AMBUSH LAUNCHED` (FP, jump point,
     distance, player-in-system).
- **Watch, with the player in the target system:**
  - The message as the raiders appear.
  - The raiders appear at the far jump point and run the convoy down.
  - Then `PROXY KILL` (if they win), `DIRECTIVE CLEARED`, `AMBUSH LEAVING`, and `AMBUSH ENDED`
    when they jump out.
- **Variants worth one look each:**
  - Kill the raiders yourself: a bounty from the Hegemony, a notice a beat later, and no PROXY KILL.
  - Let the convoy finish unloading: `DIRECTIVE CLEARED ... finished unloading`.
- **Off-screen:** relief convoys are real fleets, so the ambush also runs with the player elsewhere.
  It should resolve in the log alone.

#### 7f test record [2026-11-01; new game, pins on, StartRung 1 + ForceDirective; Mar 2 → Jul 23 c206]: **FAIL, two causes found and fixed**
- **What worked:** the directive was written on both records the pin forced (Arcadia; later Cibola, a
  League convoy). A second directive was correctly refused while one was active. Both convoys were
  found in-system on the day they arrived, and both ambushes launched: Arcadia with the player
  present (the message fired), Cibola off-screen. The directive cleared when the Arcadia convoy
  finished unloading.
- **What failed (Sean saw no interceptions):** both ambushes went home 3–4 days in, logged "the
  intercept order ended", with no ships lost. No convoy was ever reached.
  - **Cause 1 (decompiled `AssignmentModule` / `CampaignFleetAI` / `TacticalModule`):** vanilla
    INTERCEPT has no give-up of its own; it ends on its duration, or is REMOVED when another fleet
    interacts with this one (`notifyInteractedWith`) or by an engine reaction. A patrol encounter on
    the crossing is the likely trigger in both runs. My code treated "order gone" as "hunt over".
    **Fix:** the hunt order is tagged and re-issued, out of battle, for the whole `huntDays` window.
    Each re-issue is logged (`AMBUSH RE-ORDERED`, at most once a day, naming what displaced it), and
    `AMBUSH ENDED` reports the count.
  - **Cause 2 (my ruling-5 recommendation):** the farthest jump point was 15,458 and 17,468 units
    out, about 10 days' travel, and the convoy unloads for 10. **Fix:** spawn at the jump point
    whose distance is closest to `ambush.spawnPreferredDistance`, 5000 (about 3 days). From the
    vanilla systems: Arcadia gives Syrinx Passage (~6000 from the Citadel); Tyle gives Wuya Jump,
    which drifts 1.3–8.3k from Cibola. A plain minimum distance would have fallen back to the 17k
    fringe whenever Wuya was close. A huge value restores "always the farthest". **This amends
    ruling 5's "farthest" for Sean's review.**
- Janino-clean 2/2. **UNTESTED.**
- **Retest (same pins):** `AMBUSH LAUNCHED` should name a nearer jump point (Syrinx Passage for
  Arcadia). Then the raiders reach the convoy while it unloads, giving `PROXY KILL` or a fight, and
  `AMBUSH ENDED ... hunt re-orders: N`. Any `RE-ORDERED` lines say what kept knocking the order off.

#### 7f retest record [2026-11-01; same pins; Mar 2 → May 26 c206]: **PARTIAL**
- **Fixed and confirmed:**
  - The ambush spawned at Syrinx Passage, 5977 from the unloading convoy.
  - The hunt survived for 11 days (no early "order ended").
  - The player's kill paid 21,000c from the Hegemony (35 FP destroyed, involvement 1.0).
- **Still wrong:** the raiders did not reach the convoy during its 10-day unload. The directive
  cleared at unload end with no PROXY KILL. On hunt day 11 they were 130 from the convoy as it
  departed by Syrinx Passage, where they had started. The convoy then despawned there ("completed").
- **The re-order was the PLAYER's doing:** "displaced by STANDING_DOWN" is vanilla
  `FleetEncounterContext.applyAfterBattleEffectsIfThereWasABattle`. A player encounter that ends
  with no battle near a station puts the other side on a 0.5–1 day stand-down.
- **Not yet known:** why the raiders stayed ~6000 out for 8+ days. The leading hypothesis is
  vanilla's tactical module making them FLEE a stronger hostile near the convoy (the player's fleet
  parked at the Citadel?). That flee runs regardless of an INTERCEPT order (`TacticalModule`
  encounter loop: DISENGAGE → `isFleeing`, `$ai_fleeingFrom`).
  - **Instrumented, not fixed:** an `AMBUSH HUNT` line once a day while hunting gives distance,
    fleeing yes/no and from whom, what the tactical module is chasing, the player's distance, and
    speed.
  - Fix after the next run says why (instrument before tuning).
- **Rung pacing seen in this run:** expected with `midanReadyNow`, which skips firstReadyDays AND
  every rebuild. The log shows the normal schedule the pin overrode: "next: rung 2 ready at day 755".
- **Sean's observation (2026-11-01) settles it:** he was parked at the Citadel beside the convoy and
  never saw the raiders until they engaged it on its way out. Afterwards they attacked him, so his
  fleet was not scaring them off.
- **Cause (decompiled `TacticalModule`):** when an intercepting fleet cannot SEE its target, it
  searches around `$ai_lastSeenTargetLoc`, else `$ai_placeToLook`, else a random point within 3000
  of ITSELF, and it keeps renewing that point. Spawned 5977 out, beyond sensor range, the raiders
  circled their own jump point until the departing convoy flew into them.
- **Fix (UNTESTED):** every hunt tick sets `FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET` and
  `LAST_SEEN_TARGET_LOC` to the convoy's current position. That is vanilla's own pattern
  (`TransmitterTrapSpecial`, `DelayedFleetEncounter`); the lore is Midan's tip-off.
- The `AMBUSH HUNT` line now also says whether the raiders can see the convoy. Janino-clean 2/2.
- **Retest:** the HUNT lines should show the distance falling day by day ("cannot see it", then
  "see it", "chasing: the convoy"). Expect contact well inside the 10-day unload (about 3 days'
  travel), then `PROXY KILL`, or a fight if the player steps in.
- **Accepted as is:** a convoy caught while DEPARTING counts as "completed". Nothing is left to deny
  then, and the directive has already cleared.

#### 7f retest 3 record [2026-11-02; same pins; Mar 2 → Apr 25 c206]: **PROXY KILL — the loop works**
- The ambush spawned at Syrinx Passage, 5999 from the convoy. The HUNT lines: day 0 5598 ("cannot
  see it", "chasing: the convoy"), day 1 3470, day 2 1441 ("see it"), day 3 95.
- Day 4: `PROXY KILL`, Citadel Arcadia relief convoy, **108 of 180 days denied**. The directive then
  cleared, and the raiders left.
- "Displaced by STANDING_DOWN" on day 4 was the player's encounter again (318 from them).
- **Sean's observation:** the convoy does not arrive by Syrinx Passage. It comes in by Syrinx's own
  jump point, a few hundred units from the Citadel, so there is no in-system leg to intercept. The
  raiders could only catch it mid-unload.

**RULED (Sean, 2026-11-02): the raiders follow the convoy in, triggered when it ENTERS the system.**
- `ambush.spawnPreferredDistance` 5000 → **0**, meaning the jump point nearest the convoy.
- The Black Fleet's check runs every quarter day instead of daily, so the raiders appear right
  behind the convoy and catch it on the approach.
- Chosen over Claude's first suggestion (trigger when unloading begins) because it is simpler: the
  trigger already fired on "in system". It also gives up to 100% denial, and the fight happens away
  from the colony's guns.
- **PARKED for a possible later pivot (Sean: "a good idea"): trigger on UNLOADING.**
  - For it: a clean "it arrived, then they jumped it" beat at the colony, where a defending player is.
  - Against it: the colony's station and patrols defend, so the kill is less reliable. About 90%
    denial. Needs a small trigger change.
- Janino-clean 2/2. **UNTESTED.**
- **Retest (same pins):** `AMBUSH LAUNCHED` should name Syrinx's jump point, a few hundred from the
  convoy, within a quarter day of its arrival. Expect contact in under a day, with `PROXY KILL`
  denying most of the 180 days.

#### 7f retest 4 record [2026-11-03; same pins; Mar 2 → Apr 28 c206]: **PASS (the ambush works; the player saved the convoy)**
- **Launch:** the ambush spawned at Syrinx Passage, 16726 from the convoy. The convoy took ~12 days
  to cross Arcadia, so it most likely came in by the far jump point this time; in earlier runs it
  used Syrinx's gravity well.
- **The hunt:** with the raiders told where the convoy was, they closed in daily (15666 → 11324 →
  7469 → 3397 → 84 on day 4) and caught it BEFORE it reached the Citadel.
- **The fight (Sean's account):** the raiders destroyed all but one convoy ship and went after the
  survivor. Sean intervened twice, killing the raiders in two fights. Bounties paid from the
  Hegemony: 18,000c (27 FP) and 3,000c (5 FP).
- **The four `STANDING_DOWN` re-orders were Sean's encounters.** He confirmed it; mercenary and
  privateer fleets were also present.
- **Outcome:** the lone survivor unloaded the full 180 of 180 days. No PROXY KILL (correctly: the
  player saved it).
- **Observation for Sean's relief questions:** the relief amount is fixed when the convoy sails, so
  ship losses do not reduce it. Only killing the whole convoy denies anything.
- **Open puzzle:** under the "nearest jump point" rule, why was Syrinx Passage at 16726 the nearest
  to a convoy that had just come in? Either the far jump point is not in the system's `JumpPointAPI`
  list, or the convoy was first seen well after entering.
- **Measurement added (Sean, 2026-11-03), Janino-clean 3/3:**
  - `RELIEF CONVOY BATTLE` for every fight a relief convoy is in: who it fought, ships and FP lost
    and left, its phase, which side the player was on, and whether the fight counts toward a proxy
    kill.
  - `AMBUSH SPAWN CHOICE` at launch: every jump point's distance from the convoy, the convoy's
    distance from its destination, and whether it was mid-jump.
  - Both are in the log report's "Midan proxy (7f)" section.

### 7f-2 plan — relief convoy rebuild [RULED by Sean 2026-11-03; BUILT 2026-11-03, Janino-clean 7/7; TESTED 2026-11-04: PASS, record below]
Prompted by retest 4: a convoy cut down to one ship still delivered all 180 relief days. Answers
checked in vanilla code on 2026-11-03:
- **Relief is fixed at sailing.** The drip is budget ÷ unloadDays per day, and the fleet is never
  consulted.
- **The Atlases have a known cause.** `FleetFactoryV3.addFreighterFleetPoints` uses
  `SizeFilterMode.NONE`, so `maxShipSize` never caps freighters, and the freighterLarge role is the
  Atlas, a capital hull.
- **Convoys carry no cargo.** Nothing in our code or vanilla's factory loads any. A player kill
  yields only standard salvage.
- **How vanilla trade-fleet loot works.** `EconomyFleetAssignmentAI.updateCargo` loads the route's
  manifest, scaled to fit capacity, at each leg. `FleetEncounterContext.handleCargoLooting` gives the
  winner the share of carried cargo in proportion to the capacity destroyed or captured.
- **Faction differences today.** It is the same FP recipe for both sides, drawn from each side's
  roles. The League swaps ~50% of freighters for combat freighters, the Hegemony ~10%.

**Rulings (Sean, 2026-11-03), with Claude's suggestions marked:**
1. **Composition:** 3 Colossi (`colossus_Standard`) plus the sending faction's combat escort
   (`combatFp`), built explicitly. No Atlases, no factory freighter roll. Tanker and transport FP drop
   to 0 (keys kept). Combat freighters only if the escort roll happens to give them.
2. **Relief per freighter:** each day's drip × (surviving Colossi ÷ 3), counted every tick. This
   covers losses on the way in and mid-unload with one rule [Claude: thirds instead of a flat 10
   days each, because budgets vary from 45 to 270 days].
3. **D-mods allowed normally** (lore: no fleet of pristine Colossi). Each Colossus is instead loaded
   BELOW the Compromised Storage ceiling: 900 base cargo × 0.75 = 675, so **500 per Colossus,
   1500 per convoy** (Sean, 2026-11-03: "a nice round 500"; config `cargoPerFreighter`).
4. **Manifest per Colossus, 30/40/15/15 Supplies/Food/Heavy Machinery/Metals:** 150 / 200 / 75 / 75.
   A full convoy is 450 / 600 / 225 / 225. Vanilla's existing looting pays the player's share.
5. **Unloading empties the holds, day by day** (Sean, 2026-11-03: "drain it day by day"). Each
   unload tick removes the same fraction of what is aboard as the tick is of the unload days left,
   so the holds reach empty when the unload ends. A convoy killed on day 9 is nearly empty. The
   holds are also cleared at departure, to catch rounding.
6. **Scope:** every Heg–League relief convoy, both sides. The Ashen relief convoys are untouched.
   Black Fleet hunter composition is next, after this.
- **Build notes:** Colossi are tracked by member id in `ReliefOp` (saved list). Cargo is loaded at
  launch; d-mods arrive later, at inflation, and cannot push the load over capacity. Config keys
  added, none moved.

#### 7f-2 build record [BUILT 2026-11-03; Janino-clean 7/7; TESTED 2026-11-04: PASS]
- **`ExileHegLeagueRecoveryManager.createConvoy`:**
  - The factory builds only the escort.
  - `freighterCount` × `freighterVariant` are added by hand, at max CR, before inflation, so
    vanilla's inflater still refits them and rolls d-mods.
  - `loadCargo` puts `cargoPerFreighter` × freighters of the manifest into the fleet's cargo
    (unknown commodity ids are skipped with a warning).
  - A missing variant logs a warning and the convoy sails without relief freighters, delivering in
    full as before.
- **`drip`:** the day's subtraction is × `freighterFraction(op)` (surviving ids ÷ sailed). Ops from
  before 7f-2 have no ids and deliver in full.
- **`drainCargo`** runs each unload tick; **`emptyCargo`** runs at departure.
- **Log:**
  - `RELIEF CONVOY LAUNCHED` gains the relief freighter count, the cargo, and ships/FP.
  - `RELIEF OUTCOME` gains "relief freighters alive N of 3".
- **Config (`recoveryConvoys`):**
  - Changed: `freighterFp` 24→0, `tankerFp` 6→0, `transportFp` 8→0.
  - New: `freighterVariant`, `freighterCount` 3, `cargoPerFreighter` 500, `cargoShares` {supplies
    0.30, food 0.40, heavy_machinery 0.15, metals 0.15}.
  - No key moved or removed.
- **Save note:** saved `ReliefOp` gains two fields (null/0 on old saves = old behaviour).
- **Test checklist (same pins as 7f):**
  - `RELIEF CONVOY LAUNCHED` shows 3 relief freighters and supplies 450 / food 600 /
    heavy_machinery 225 / metals 225. In game: three Colossi plus a small escort, no Atlas.
  - Let the ambush kill one or two Colossi and save the rest: `RELIEF OUTCOME` should show
    delivered below the budget and "alive 1 (or 2) of 3".
  - Destroy a convoy yourself (or a Colossus): the loot screen should offer a share of the manifest.
  - Look at a convoy's cargo late in its unload: it should be nearly empty.

#### "Strike Force Ajax" [Sean, 2026-11-03; BUILT, Janino-clean 4/4; SEEN IN GAME 2026-11-04: PASS]
- The fleet carrying the Ajax is named by `strikeForce.flagshipFleetName` ("Strike Force Ajax") with
  `setNoFactionInName(true)`, so it shows exactly that, with no "Persean League" prefix. Its tooltip
  still shows the League flag.
- Escort groups, and a main fleet rebuilt after the Ajax is lost, keep `fleetName` ("Strike Force",
  shown with the prefix). An empty `flagshipFleetName` restores the old behaviour.
- Code: `ExileHegLeagueMidanComposer.composeMainFleet` (flagship path only) plus one config field.

#### 7f-2 test record [2026-11-04; Saitama save, new game, no Nexerelin, pins ReadyNow + LaunchNow + StartRung 1 + ForceDirective; Mar 2 → Apr 23 c206, one reload to Apr 21]: **PASS**
Log read with `tools/exiles-log-report.ps1` (zero Exiles errors), checked against Sean's account.
- **Convoy:** `RELIEF CONVOY LAUNCHED` Eventide → Citadel Arcadia, 3 relief freighters, cargo
  supplies 450 / food 600 / heavy machinery 225 / metals 225, 5 ships 30 FP. **Sean: three Colossi,
  no Atlas.**
- **Spawn:** `AMBUSH LAUNCHED` at Syrinx Passage, 5739 from the convoy; `AMBUSH SPAWN CHOICE`: convoy
  778 from its destination, MID-JUMP; jump points Syrinx Passage 5739, Fringe Jump-point 24324.
  **This answers the retest-4 puzzle:** both jump points are in the list, so nothing is missing. The
  convoy arrives by Syrinx's gravity well, which is a planet, not a `JumpPointAPI`, so it is first
  seen on top of the colony and "nearest jump point" is still ~5700 out. On that route the raiders
  cannot follow it in; they reach it mid-unload (the parked trigger-on-unloading shape, by accident).
  Retest 4's 16726 was the other route: the convoy came in by the Fringe point and was first seen
  well after entering. The hunt closed 5119 / 3024 / 1160 / 81 over three days.
- **Branch A (Sean watched, did not intervene):** three `RELIEF CONVOY BATTLE` lines mid-unload (lost
  3 ships 19 FP, then 1, then 1; "player not involved; counts toward a proxy kill: true"), then
  `PROXY KILL` 105.77 of 180 days denied, `RELIEF OUTCOME` delivered 74.23d over 5 unload days,
  relief freighters alive 0 of 3, `DIRECTIVE CLEARED`, `AMBUSH LEAVING`. **The loss-scaled drip
  works:** five full days would have delivered 90. Sean: the relief "appeared to not be delivered"
  — the industries stayed disrupted, as a 74-day partial delivery against a 365-day base + 365-day
  station disruption should look.
- **Branch B (reload to Apr 21):** Sean fought WITH the convoy (`RELIEF CONVOY BATTLE ... player WITH
  the convoy`), the raiders were destroyed hunting (`AMBUSH ENDED`), bounty 23,485c from the Hegemony
  (39 FP destroyed, involvement 0.78). He then attacked the convoy himself (`player AGAINST the
  convoy; counts toward a proxy kill: false`) and **the loot screen offered supplies, heavy machinery,
  metals and food, food the largest single stack** — vanilla's percentage share, as designed.
- **Strike Force Ajax:** the name showed as discussed.
- **Rungs 2 and 3 also fired under the pins** (rung 3 Eventide needed 0 escorts: Samarra's measured
  defence was 285 against a 725 baseline right after the Sphinx strike). Noted for the complexity
  review, not a defect.
- **DEFECT (Sean): the ambush bounty message is non-standard, with no bounty icon.** Cause: the
  ambush has no intel (ruled 2026-11-01), so `flushNotice` posted a bare `CampaignUIAPI.addMessage`
  text line. Every other Exiles bounty is an intel update whose `getIcon()` returns
  `graphics/icons/intel/gain_credits.png` with "%s received" / damage-share / relationship bullets.
  **FIXED 2026-11-04 (Janino-clean 1/1, UNTESTED):** the notice is now a vanilla `MessageIntel`
  (no intel entry, same as before) carrying that icon, the "received" line, the damage share when a
  single battle paid less than 100%, and the relationship line in vanilla's wording. The queued
  state gains `pendingRepDelta` / `pendingFraction` / `pendingBattles` (defaults on old saves). The
  icon loads on first use; if it fails, the payer's crest is used and a warning logged.
  **Retest:** kill the ambushers; the lower-left message should carry the credits icon and read like
  a raid-intel payout update.

### Hunter composition [DISCUSSED 2026-11-04; RULED by Sean: no composition change now]
Discussion after the 7f-2 test, from the parked frigate-cap idea.
- **Facts on the table:** the destroyer-capped factory roll won every ambush so far without losses
  (retest 3: 108 of 180 denied; retest 4: all but one ship before arrival; 2026-11-04: all five
  convoy ships over five unload days, raiders untouched). Hunters move at burn 9, Colossus convoys at
  burn 7. A frigate cap would roll burn 9–10 (Kite and Mudskipper are 9; a fleet moves at its slowest
  ship), so it buys little speed; its case is feel and early-game fairness, and it would kill slower
  against three armoured Colossi (every extra day is relief delivered under the drip rule).
- **Claude's read of "something looks off":** vanilla's destroyer bucket weights `buffalo2_FS` at
  10 against ~1.4 per Enforcer variant, and the doctrine's carrier slot can add a Condor, so a
  hunting pack is often converted freighters and a light carrier. Unproven: the log did not record
  hulls. The Omen carries built-in High Resolution Sensors, ECM and ECCM (Sean was right); its sensor
  value is modest now that the tip-off fix tells the hunters where the convoy is, its EMP emitter
  against escort missiles/fighters is the stronger case, and Omens in BOTH Strike Force Ajax and the
  Black Fleet would bind the two in the player's eye — decide that on purpose (Phase-2 clue or tell).
- **Multiple hunter fleets:** recommended against (works now; the drip rule makes partial kills
  count; a second fleet only matters when the player beat the first, which is the point).
- **Sean's plan of action (2026-11-04):** (1) add the composition line and a Dram, nothing else;
  (2) no other hunter changes until the Black Fleet roster is reviewed and updated, which waits
  until the Heg–League director is complete and in beta; (3) an Omen may be forced into the hunters
  then, when the roster says which Omen and the fleet's predatory look is settled. The frigate idea
  stays parked with better data waiting for it.
- **BUILT 2026-11-04 (Janino-clean 2/2, UNTESTED):** `ambush.tankerFp` 3 (one Dram from vanilla's
  tankerSmall role; 0 = none) passed as the factory's tanker points; `Black Fleet AMBUSH COMPOSITION`
  log line at launch (ship count, FP, slowest burn, every hull with its FP). The log report's
  "Midan proxy (7f)" section already matches any `Black Fleet AMBUSH` line.

### 7g plan — switches, Nexerelin edge cases, conquest shields [DRAFTED + BUILT 2026-11-07; FULLY TESTED IN GAME over three runs 2026-11-07/08 — records at the end of this doc; 7g DONE]
The last behaviour step of Pass 7. Design authority: war doc §6.1 "WAR OFF AND NEXERELIN" (ruled
2026-10-19) and R11 (conquest shields). Scope rulings below are Sean's, 2026-11-07, each per Claude's
recommendation after the 2026-11-07 discussion.

**Scope — IN:** the three conquest shields; the three "no one left" stop conditions as a hold evaluated
at decision points; console checks of the quest flags and the war-off path; one Nexerelin run with a
fixed measurement list. **OUT:** anything new. Two items from the original plan are **STRUCK**: the
witness hold (§2 "Witness hooks", §2 J's "force the witness hold" pin, §3's `witnessHoldMaxDays`, §6's
witness risk) was superseded 2026-10-30 by the built quest hold/trigger flags; the Nex table's Diktat
embargo row died with the embargo (removed 2026-11-01).

**1. Conquest shields (config only).** Add `arcadia_station`, `sphinx`, `eventide` to
`exilehegleague.nex.conquestShieldMarkets` (today: `chicomoztoc`, `kazeron`). The applier
(`ExileHegLeagueNexSupport`) resolves ids to live markets, sets vanilla's inert `$nex_npc_no_invade`
key plus our marker, sweeps stale ones, and logs `Nex conquest shields: N applied, M removed` once per
load. **Not shielded, ruled:** the Black Fleet anchors and Midan's home — a pirate base that can fall
is better fiction, the Black Fleet director already survives a lost anchor, and every shield is one
more place Nex behaves differently under Exiles. Inert without Nex. First on the build order, because
the Nex run needs it.

**2. The hold — "no one left to frame / to hit / to plan" (code, small).** Three conditions:
- the Persean League owns no market;
- the Hegemony owns no market;
- Midan owns no market in its HOME system (home only, ruled: the origin ruins and anything Midan might
  later hold under Nex are not what makes the strikes possible).
"Owns no market" = `Misc.getFactionMarkets(faction)` empty, with a null-faction guard (vanilla never
deletes a faction object; Nex marks elimination by leaving it marketless).
- **Where it runs — the decision points, no timer (ruled 2026-11-07 over a 9/11-day interval):**
  (a) `ExileHegLeagueMidanModule.checkOpportunity`, after the ready check and the one-in-flight rail,
  before the target lookup: any condition true → no launch; (b) `offerProxyDirective`, before the
  roll: any condition true → no directive. Cost: three market-list scans only when the module was about
  to spend far more; coverage exact (no rung can launch into a sector with no League).
- **Visibility:** the monthly MEASURE (`measureAllTargets`) evaluates the same conditions purely to log
  the transition: `Exiles Midan HOLD: <reason>` when it begins, `Exiles Midan RESUMES` when every
  condition is clear again. A decision point that refuses also logs HOLD once (first refusal only).
- **State:** one saved boolean `holding` and a String `holdReason` for the transition line. Nothing
  else: the ruling's "stops for good" reads as "while it is true" — a Nex faction respawn lifts the hold
  by itself, no save edit.
- **Interaction with the existing rails:** the per-target skip (`skipRung`, built) still handles a
  single target that left Hegemony hands; the hold is the whole-ladder case. The launch-anchor fallback
  (Lundy → Sallee → Midan home) and the ambush's "no source" refusal already cover a dead Black Fleet.
- **Log report:** the two new lines need a row in `tools/exiles-log-report.ps1`'s Midan patterns.

**3. War-off path — verify, no code.** Built already: the director only ticks the module while the war
is active; a strike landing with the war inactive logs `LANDING SKIPPED` and leaves the wound standing;
`midan.enabled` false gates the ladder and the proxy writer. To check by console/pin:
- Strike in flight → `$exiles_hl_war_enabled` false → expect `STRIKE LANDED` (wound) then
  `LANDING SKIPPED ... tension and incident skipped`; no new READY/LAUNCHED afterwards.
- `midan.enabled` false in config → a run with no Midan READY/LAUNCHED/DIRECTIVE lines at all.
- A directive written before the switch expires on its backstop (`DIRECTIVE CLEARED`), and an ambush
  already spawned finishes under the Black Fleet director (`AMBUSH ENDED`).

**4. Quest flags — console check, no code.** The hold/trigger flags replaced the witness hold and have
never been exercised (every strike test used the pins). On a save where Midan is ready (pin
`midanReadyNow`, `midanLaunchNow` false): set `$global.exiles_midan_rung1_hold` true → expect READY but
no LAUNCHED; then set `$global.exiles_midan_rung1_trigger` true → expect LAUNCHED within a day with
`strike.questPrepDays` (0.5) of prep, and the trigger cleared. This is exactly what rules.csv will do.

**5. The Nexerelin run (Sean's usual Nex + vanilla start; pins ReadyNow + LaunchNow + StartRung 1 +
ForceDirective so a strike and an ambush both happen under Nex inside one session).** Measurements,
in priority order:
1. Task-group interference with the sneak attack in the window between `DISTRESS CALL` and
   `STRIKE LANDED` / `BREAKS OFF`: the R7 detector's `NEX TASK GROUP ENTERED` lines against the strike
   system during that window, and whether the strike still lands. The intel is hidden until the call
   precisely for this; never tested under Nex.
2. A Hegemony task group defending Arcadia against the ambush: `RELIEF CONVOY BATTLE` and the AMBUSH
   lines (who the raiders fought, whether they still reached the convoy).
3. No NPC invasion or raid of a shielded target: any Nex invasion intel naming Citadel Arcadia, Sphinx
   or Eventide is a FAIL; `Nex conquest shields: 3 applied` on the first load is the precondition.
4. The hold and the skip paths will not fire organically in one run — covered by the console checks.
5. The R7 detector's summary counts (register R7 still awaits a ruling).
Both starts are covered: the no-Nex runs used the Exiles start, this run uses the vanilla start.

**Build order inside 7g:** shields → hold (+ log-report rows) → console checks → the Nex run. No new
config keys beyond the three shield ids; no new test pins.

**Nex edge-case table, expected status after 7g:**
| Situation | Behaviour | After 7g |
|---|---|---|
| Rung target not Hegemony-owned | Skip the rung, ladder advances | Built (skipRung) |
| League has no markets | Hold while true | 7g |
| Hegemony has no markets | Hold while true | 7g |
| Midan has no home-system markets | Hold while true | 7g |
| Black Fleet eliminated | Rungs from Midan's home; no ambushes | Built (anchor fallback; ambush "no source") |
| Diktat / Sindria | — | Struck with the embargo |
| Nex relief fleets | Never ambushed | By construction |
| Nex's own Heg–League war | Unaffected; B2 pin keeps the pair hostile | Built (Pass 1) |

**Rulings recorded the same day, outside 7g:** the post-Pass-7 complexity review runs BEFORE Pass 9;
the Ashen director's missing live-hostility checks before launching go on the Ashen Pass 8 sweep list;
the Black Fleet hunters stay as built until the roster review (which waits for Heg–League beta).

#### 7g build record [BUILT 2026-11-07; Janino-clean 1/1; shields, hold, quest flags and war-off all CONFIRMED in play 2026-11-07/08]
- **Shields (config):** `nex.conquestShieldMarkets` is now `chicomoztoc, kazeron, arcadia_station,
  sphinx, eventide`, with the ruling in a comment. No code; the applier already resolves ids, sets
  `$nex_npc_no_invade` plus our marker, sweeps stale ones and logs the count per load.
- **The hold (`ExileHegLeagueMidanModule`):**
  - `holdReason(config)` (public static): League marketless → "no one left to frame"; Hegemony
    marketless → "no targets"; Midan marketless in its HOME system → "plans can fail". Home = the
    star system of the LAST launch anchor in `launchAnchors` (`exilemidan_haven`), so the rule follows
    the config; with no resolvable anchor it falls back to any Midan market. Market lists come from
    `Misc.getFactionMarkets`, which walks the economy's full market copy — the same walk
    `ExileMidanEconomyNetwork` uses to find and keep Midan's markets in their private econ group, so the
    group does not hide them (checked 2026-11-07 against the vanilla source).
  - `refreshHold(config, nowDay)` (instance): evaluates, logs `Exiles Midan HOLD: <reason> | rung N |
    day D` on the way in and `Exiles Midan RESUMES: <old reason> -- no longer true` on the way out,
    keeps `holding` + `holdReason` (two saved fields; old saves load them as false/null).
  - **Call sites, per the ruling:** `checkOpportunity` after the ready check and the one-in-flight
    rail (the launch decision; daily only while ready and idle); `offerProxyDirective` before the roll
    (static, so it logs its refusal as `DIRECTIVE NOT WRITTEN: ... -- Midan holds: <reason>` with no
    transition line); the monthly MEASURE for visibility. No new interval.
- **Log report:** `HOLD:` and `RESUMES:` added to the "Midan ladder (Pass 7)" section's pattern.
- **Not built, by design:** nothing for the war-off path or the quest flags (both already exist; 7g
  verifies them), no new config keys beyond the shield ids, no new pins.

#### 7g test checklist
- **Shields (needs Nexerelin):** first load logs `Nex conquest shields: 3 applied, 0 removed` (the two
  capitals already carry theirs). Any Nex invasion/raid intel naming Citadel Arcadia, Sphinx or
  Eventide afterwards is a FAIL.
- **Hold, by console (no Nex needed):** with Midan READY and no strike live, give every League market
  to another faction (or use a save where the League is gone) → next day `Exiles Midan HOLD: the
  Persean League holds no market ...`, no LAUNCHED; restore one League market → `Exiles Midan RESUMES`
  within a day, then the launch. The same for a Hegemony-less sector. For Midan: transfer Haven and the
  mining station → HOLD "Midan holds no market in its home system (Midan)"; also expect
  `DIRECTIVE NOT WRITTEN ... Midan holds:` on the next relief record while holding.
- **War-off and quest flags:** the console steps in the 7g plan above (sections 3 and 4).
- **The Nex run:** the five measurements in the 7g plan, section 5, with the four Midan pins.

#### 7g test session checklist (written 2026-11-07 for Sean's run; Nexerelin + vanilla start as usual; Console Commands mod)
**Pins on disk (never committed):** `midanReadyNow` true, **`midanLaunchNow` false** (the quest
trigger launches rung 1, which tests the Phase-2 seam under real conditions), `midanStartRung` 1,
`midanForceDirective` true, `forceState` -1. The config is re-read on every game load, so a pin can
be flipped between saves without a new game.

**0. First load.** Expect `Nex conquest shields: 5 applied, 0 removed.` on a NEW game (3 on an old
save that already had the capitals). Then `Exiles Midan: module started ... rung 1 (arcadia_station)
ready at day 0` and `Exiles Midan READY: rung 1 ... TEST PIN midanReadyNow`, and NO `LAUNCHED` (the
score sits near 0 against a 0.6 bar with LaunchNow off). Fly to Arcadia.

**1. Quest trigger (positive).** Console:
`runcode Global.getSector().getMemoryWithoutUpdate().set("$exiles_midan_rung1_trigger", true);`
Within a day: `Exiles Midan LAUNCHED: rung 1 Citadel Arcadia ... | QUEST TRIGGER` with prep 0.5d
(not the usual ~3d). No second launch afterwards = the trigger was cleared.

**2. The strike under Nex (headline measurement).** `DISTRESS CALL` → `STRIKE LANDED` (or `BREAKS OFF`
if the player-watched fight is lost). Note every `NEX TASK GROUP ENTERED` line for Arcadia between
those two, and whether a Nex task group actually joined the defence. Be in-system to watch; the
landing is by visible victory when the player is present.

**3. Relief and ambush.** `RELIEF RECORDED` → `DIRECTIVE WRITTEN ... TEST PIN` → 20–45 days →
`RELIEF CONVOY LAUNCHED` (3 relief freighters) → `AMBUSH LAUNCHED` + **`AMBUSH COMPOSITION`** (new:
every hull with its FP; expect a Dram in the list) → `RELIEF CONVOY BATTLE` lines. If a Hegemony task
group meets the raiders, note it. **Kill the raiders yourself once:** the bounty message should now
carry the credits icon and read like a raid intel's payout (the 2026-11-04 fix).

**4. The 7g hold, by console** (any time Midan is READY with no strike live, e.g. while waiting for the
relief convoy). `list markets` to find the two Midan home market ids (the econ loader names them),
then `setmarketowner <haven id> pirates` and `setmarketowner <mining station id> pirates`. Next
day: `Exiles Midan HOLD: Midan holds no market in its home system (Midan) (plans can fail) | rung N`.
`Midan economy: left ...` lines from the network script are expected. While holding, the next
relief record logs `DIRECTIVE NOT WRITTEN ... Midan holds:`. Give one market back
(`setmarketowner <id> exilemidan`) → `Exiles Midan RESUMES: ... -- no longer true` within a day.
The League-marketless and Hegemony-marketless branches are the same code path with a different
faction; test them only if a save with an eliminated faction is to hand.

**5. Quest hold (negative) + the LaunchNow phase.** After rung 1 resolves (`STRIKE OUTCOME`), rung 2
is READY at once. Console: `...set("$exiles_midan_rung2_hold", true);`, save, flip `midanLaunchNow`
to true on disk, reload. Expect NO rung 2 `LAUNCHED` for several days (the pin is overruled by the
hold; the monthly SCORE line will say `HELD for a quest`). Then
`...set("$exiles_midan_rung2_trigger", true);` → `LAUNCHED ... | QUEST TRIGGER` (a trigger overrides a
hold by design: the quest owns the rung). Leave LaunchNow true for the rest of the run.

**6. War off with a strike in flight.** During the rung 2 strike (after its `DISTRESS CALL`):
`runcode Global.getSector().getMemoryWithoutUpdate().set("$exiles_hl_war_enabled", false);`
Expect the strike to finish on its own (`STRIKE LANDED`, the wound applied) followed by
`Exiles Midan LANDING SKIPPED ... while the war is not active -- the wound stands; tension and
incident skipped`, no new Midan READY/LAUNCHED, and the Heg–League director's idle line. Set the key
back to `true` → the director and Midan resume.

**7. Shields over the whole run.** Any Nexerelin invasion or raid intel naming Citadel Arcadia,
Sphinx or Eventide is a FAIL. Player invasions are allowed by design.

**Afterwards:** run `tools/exiles-log-report.ps1` first; HOLD/RESUMES, AMBUSH COMPOSITION and the
Nex task-group lines all have rows. Save-name and date range in the test record.

#### 7g test record [2026-11-07; Sharpe save, NEW game, Nexerelin + vanilla start, Console Commands; pins ReadyNow + StartRung 1 + ForceDirective, LaunchNow OFF; Mar 2 c206 → Jan 9 c208, one reload to Nov 29 c207]: **PASS on parts 0, 2, 3 and 7; parts 1, 4, 5, 6 NOT RUN**
Log read with `tools/exiles-log-report.ps1` (zero Exiles errors, 1244 Exiles lines), checked against
Sean's account.
- **0. Shields:** `Exiles Heg-League: Nex conquest shields: 5 applied, 0 removed.` on the new game.
  Module started, `READY: rung 1 ... TEST PIN midanReadyNow` at day 0, no launch (SCORE 0.0 vs 0.6).
- **1. Quest trigger — NOT exercised.** Nothing wrong: **the ladder launched ORGANICALLY first**, the
  first organic launch in any run (every earlier run used LaunchNow). Day 611 (Nov 3 c207), state
  ESCALATION, cycle 3: `LAUNCHED: rung 1 ... score 0.6 vs bar 0.3 (means 0.0, distraction 1.0 [4 Heg
  ops], cover 1.0 [ESCALATION]) | defense 703 (baseline 661) | 2 escorts`. Two monthly `BLOCKED
  (score cleared the bar, fleet NOT FEASIBLE)` lines preceded it (Sep 21: defence 1236 vs baseline
  213; Oct 21: 1258) — the Hegemony massing at Arcadia during ESCALATION held the strike back until
  the defence thinned. **The feasibility guard and the cover/distraction score both behaved.**
- **2. The strike under Nex (headline).** Arrival strength 1525 vs defence 696. One live Hegemony
  task group, `"Special Task Group - 7th Yama" (231 FP)`, `ENTERED Arcadia` in the window (the watch
  attributes it to the concurrent League interdiction of Arcadia) and `LEFT after 2d`, before the
  landing. Sean: a heavily guarded Hegemony mercantile fleet defended the station; no Nex defence
  fleet engaged. `STRIKE LANDED` Nov 20 (spaceport 180d, military base and station 365d, unrest +2,
  player in range); `STAND DOWN` worked — Sean, Hegemony-aligned, watched the force withdraw without
  being attacked. `LANDING: rung 1 LANDED | DE-ESCALATION -> RETALIATION | became the INCIDENT`
  (cycle 4). **Second Nex measurement, elsewhere in the run:** a League surgical strike on Ancyra
  (Galatia) ended `cannot beat` (602 vs 704) while an abstract Hegemony task group (151 FP) sat in
  Galatia — **Nex task groups can deny a Heg-League strike.** For the Nex table.
- **The cascade (pin artefact, flagged for the complexity review):** rung 2 `LAUNCHED` the same tick
  rung 1 resolved (Nov 27; score 0.68 vs 0.6) and rung 3 the day after rung 2 resolved (Dec 21; 0.61
  vs 0.6, and 0.6 vs 0.6 on the reload branch). `midanReadyNow` removes the 730-day rebuild gate, so
  three strikes in seven weeks is the pin. But note **why** they cleared: in RETALIATION with ≥3 Heg
  ops, distraction 1.0 + cover 1.0 = 0.6 meets the rung-2/3 bar with means ≈ 0. In production the
  rebuild gate is the real pacing control; whether the bar should also demand means is a review item.
  Rung 2 Sphinx landed Dec 17 on both branches (`player out of range`, 3 abstract fleets), tension
  raised to the 65 floor; rung 3 Eventide was in flight at log end.
- **3. Relief and ambush.** `RELIEF RECORDED: Citadel Arcadia 180d` → `DIRECTIVE WRITTEN ... TEST PIN`
  (expiry 230d). The later Sphinx and Olinadu records logged `DIRECTIVE NOT WRITTEN ... one is
  already active (arcadia_station)` — one directive at a time, by design. `RELIEF CONVOY LAUNCHED`
  Eventide → Citadel Arcadia, 3 relief freighters, cargo 450/600/225/225, 5 ships 31 FP. **Branch A:**
  the convoy was hit leaving Samarra by the **League blockade of Samarra** (`RELIEF CONVOY BATTLE ...
  vs blockade fleet | lost 3 ship(s), 17 FP; left 2 ship(s), 14 FP | travelling` — both escorts and
  one Colossus; Sean read them as mercenaries). Genuine cross-system interplay between two directors
  sharing nothing but the world. Raiders: `AMBUSH LAUNCHED` at Syrinx Passage, 5867 from the convoy
  (MID-JUMP, the gravity-well route again), **`AMBUSH COMPOSITION`: 11 ships 57 FP — Shrike (P) ×2,
  Buffalo Mk.II, Vanguard (P), Cerberus (P), Wolf (P), Shepherd, Hound (P) ×3, Dram.** Blind hunt for
  five days, contact on day 5, two battles mid-unload (lost 1 then 1), `RELIEF OUTCOME: delivered
  83.1d of 180.0d over 7 unload days (convoy lost)`. Sean fought the raiders in between (41 FP
  destroyed, **bounty 30,000c** from the Hegemony); the survivors finished the convoy. **Branch B
  (reload to Nov 29):** raiders 9 ships 45 FP (Mule (P), Shrike (P), Wolf (P), Cerberus, Mudskipper
  Mk.II, Shepherd, Hound (P), Kite (P), **Dram**); Sean wiped them before contact → `AMBUSH ENDED ...
  destroyed hunting`, **bounty 33,000c** (45 FP, involvement 1.0). The Dram shipped both times.
  **Bounty icon: Sean to confirm** (the report says "full bounty both times", not whether the
  credits icon showed).
- **4. Hold — NOT RUN** (no HOLD/RESUMES lines; none expected without the console transfer).
- **5. Quest hold + LaunchNow phase — NOT RUN.** **6. War off mid-strike — NOT RUN.**
- **7. Shields over the run:** no `removed` after the first load; Sean reported no Nex invasion or
  raid against the three targets (to confirm explicitly).
- **Also seen:** relief convoys Raesvelg (Aug c206) and Fikenhild (Jul c207) both lost whole to
  ambushes (0 of 3 alive; Fikenhild re-ordered once by STANDING_DOWN and still caught it unloading);
  Heg-League raider bounties 15,000c twice in Aug c207 with the intel notice. Six cycles of the
  Tension meter in 22 months, two of them Midan incidents.

**Still to test for 7g:** the hold (console market transfer), the quest trigger and quest hold, and
the war-off path. All are console-driven and need no new game: load the Sharpe save with Midan READY
and no strike live.

#### 7g console-check sequence (written 2026-11-07 for the Sharpe save; rung 2 Sphinx in flight at the save)
**Pins on disk:** `midanReadyNow` true, **`midanLaunchNow` true** (flipped after the Nex run so that a
resumed hold shows as an immediate launch instead of depending on the Tension state), `midanStartRung` 1,
`midanForceDirective` true. Sean confirmed the credits icon on the ambush bounty message (2026-11-07):
the 2026-11-04 notice fix is **CONFIRMED in play**.
- **A. Right after loading** (before rung 2 resolves, ~Dec 20 c207):
  `runcode Global.getSector().getMemoryWithoutUpdate().set("$exiles_midan_rung3_hold", true);`
  Without it LaunchNow fires rung 3 the instant rung 2 reports.
- **B. War off (part 6):** after `DISTRESS CALL: Sphinx`, `...set("$exiles_hl_war_enabled", false);`.
  Expect `STRIKE LANDED: rung 2 Sphinx` then `Exiles Midan LANDING SKIPPED ... the wound stands`, and
  silence from Midan (the director returns at `warEnabled()`; the module does not tick). Then
  `...set("$exiles_hl_war_enabled", true);` → `Exiles Midan READY: rung 3 (eventide)` within a day,
  and **no LAUNCHED** despite LaunchNow — the quest hold (part 5, negative). The monthly SCORE line
  carries `HELD for a quest`; the daily check is silent by design.
- **C. The 7g hold (part 4):** Midan holds FOUR home markets, not two: `exilemidan_fawkes`,
  `exilemidan_haven`, `exilemidan_mining_station`, `exilemidan_gas_giant` (the econ loader uses the
  entity id as the market id; `list markets` confirms). `setmarketowner <id> pirates` for all four →
  within a day `Exiles Midan HOLD: Midan holds no market in its home system (Midan) (plans can fail) |
  rung 3`. Give one back (`setmarketowner exilemidan_haven exilemidan`) → `Exiles Midan RESUMES: ...
  -- no longer true` within a day, still no launch (quest hold). The `Midan economy:` network lines
  are expected noise. Do C only with the war on and no strike live: while a strike is live the
  daily decision point returns before the hold, and only the monthly measure would log it.
- **D. Quest trigger (part 1):** `...set("$exiles_midan_rung3_trigger", true);` → within a day
  `LAUNCHED: rung 3 Eventide ... | HELD for a quest | QUEST TRIGGER`, prep 0.5d (a trigger overrides
  a hold by design). This is the Eventide sequence (7e-2); rung 3 is the last rung. Skip D to leave
  the save short of Eventide.
**Production pacing (Sean, 2026-11-07: "a much bigger delay between the three attacks"):** the cascade
was `midanReadyNow`. Production readiness is `firstReadyDays` 365, `rebuildDays` 730,
`rebuildFailedDays` 1095, i.e. rung 1 no earlier than year 1, rung 2 ~year 3, rung 3 ~year 5, and
each still has to clear the score (≈1/3/5 real hours of play). The config comment already records a
planned stretch pass to 730/913; whether the bar should also demand means is on the complexity
review's agenda.

#### 7g console-check record [2026-11-07; Sharpe save reloaded at Nov 29 c207, played to Mar 26 c208; zero Exiles errors]: **part 6 (war off) PASS; parts 1, 4, 5 VOID — the war was never switched back on**
- **A** set on load (`$exiles_midan_rung3_hold` true). **B** war off Dec 6 c207, after the Sphinx
  distress call: `STRIKE LANDED: rung 2 Sphinx` → `STRIKE OUTCOME` → `RUNG RESOLVED ... next: rung 3
  ready at day 1388` → **`Exiles Midan LANDING SKIPPED: rung 2 LANDED while the war is not active --
  the wound stands; tension and incident skipped`**. No new Heg-League op launched afterwards, no
  Midan MEASURE/SCORE/READY: the module did not tick. **Part 6 PASS.**
- **The miss:** the switch-on was typed as `$exiles_midan_hl_war_enabled` (Jan 2 c208) and later
  `$exiles_midan_war_enabled`; the real key is `$exiles_hl_war_enabled`. The war stayed off for the
  remaining ~3.5 game months, so the four market transfers (Fawkes/Haven/mining station to `player`
  then pirates, gas giant to pirates — all four `Midan economy: left` lines fired), the rung 3 trigger,
  the hold off/on and Haven's return (`Midan economy: joined exilemidan_haven`) all ran against a
  director that returns at `warEnabled()`. Nothing could log HOLD, RESUMES or LAUNCHED. Not a defect.
- **Observed while off (for Sean's ruling, not a defect):** ops launched BEFORE the switch finished
  and still reported — five `tension` lines (53.05 → 63.05, "operation completed by ...") and the
  weariness lines. That is the ratified "ops in flight finish" behaviour; the switch does not freeze
  the meter for those completions.
- **Instrumentation added (2026-11-07, Janino-clean 1/1, local):** the director now logs
  `Exiles Heg-League WAR SWITCH: ON/OFF ($exiles_hl_war_enabled) -- ...` once per flip (saved
  `warSwitchSeen`; an older save logs ON once on its first tick). Report row "Heg-League war switch".
  A typo now shows as the ABSENCE of the ON line within a tick of the command.
- **Redo** from the same Sharpe save (no save was written during the run): A, B, then
  `runcode Global.getSector().getMemoryWithoutUpdate().set("$exiles_hl_war_enabled", true);` and
  wait for `WAR SWITCH: ON` + `READY: rung 3`; then C (four markets), then D.

#### 7g console-check rerun [2026-11-08; Sharpe save reloaded at Nov 29 c207, played to Jan 24 c208; zero Exiles errors; no save written]: **parts 5 and 6 PASS, hold-release → launch PASS, WAR SWITCH line PASS both ways, rung 3 Eventide landed; parts 1 and 4 still untested (C ran inside the war-off window)**
- **Load:** `WAR SWITCH: ON` once on the first tick, as predicted for a save from before the field.
- **A** Nov 29 (`rung3_hold` true). **B** Dec 5, after the Sphinx distress call: `WAR SWITCH: OFF ...
  ops in flight finish and still report`. Sphinx `STRIKE LANDED` Dec 17 → `STRIKE OUTCOME` → `RUNG
  RESOLVED` → **`LANDING SKIPPED ... the wound stands`** (part 6 PASS, second time). One `tension`
  line while off (53.05 → 55.05, an op that completed) and **one `RELIEF CONVOY LAUNCHED` Jangala →
  Citadel Arcadia while off** — the recovery manager is not behind the war gate; relief for an
  existing wound continues. **RULED (Sean, 2026-11-08): correct as is — relief launches whether or not the war is on.**
- **C ran too early:** the four transfers (Dec 12) and Haven's return (Dec 17) happened INSIDE the
  war-off window with the Sphinx strike still live, so the director never evaluated them; the
  network lines fired (`left` ×4, `joined exilemidan_haven`) but no HOLD/RESUMES could. Fawkes, the
  mining station and the gas giant are pirate-owned for the rest of the run; Midan kept Haven, so the
  hold was correctly NOT raised when the module resumed. **Part 4 still untested.**
- **War on** Dec 29: `WAR SWITCH: ON` within a tick → `READY: rung 3 (eventide) | day 666` within a
  day → **no LAUNCHED for four days despite `midanLaunchNow` true** (the quest hold; part 5 negative
  PASS). Sean then cleared the hold (Jan 2) instead of setting the trigger → `LAUNCHED: rung 3
  Eventide ... score 0.62 vs bar 0.6 ... | TEST PIN midanLaunchNow` the next day (hold release →
  launch PASS). **Part 1 (quest trigger) still untested.**
- **Rung 3 landed Jan 24 c208, player out of range:** `array removed, Mirror Gamma / Shade Psi /
  Mirror Beta / Mirror Delta destroyed, Mirror Epsilon knocked adrift, SATURATION BOMBARDMENT
  (vanilla): size 7 -> 6, 7 industries disrupted, pollution, stability -10 | Emergency Measures armed
  (pressure 900d)` — matches the 7e-2 ruling (4 destroyed / Epsilon adrift / Alpha, Omega, Chi
  untouched). No on-screen sequence because the player was absent (by design). `RUNG RESOLVED: ...
  next: ladder complete`; `LANDING: rung 3 LANDED | TENSION -> RETALIATION | tension 56.61 -> 88.0`.
- **What remains and how:** both need a ready rung with no strike live, which this save no longer
  has (ladder complete; after completion only the MONTHLY measure evaluates the hold, visibility
  only). Reload Nov 29 once more: A, let Sphinx resolve WITH the war on (~Dec 20), `READY: rung 3`
  held → **C** (four markets to pirates → `HOLD` within a day; Haven back → `RESUMES` within a day)
  → **D** `rung3_trigger` → `LAUNCHED ... | HELD for a quest | QUEST TRIGGER`, prep 0.5d. About 35
  game days. Alternative ruled acceptable to propose: defer part 1 to Phase 2, where the first quest
  exercises it anyway, and accept the hold's shared `refreshHold` path as verified by the monthly
  call site (same function, different caller).

#### 7g console-check third run [2026-11-08; Sharpe save reloaded at Nov 29 c207, played to ~Feb 5 c208; zero Exiles errors]: **parts 1 and 4 PASS — 7g is now fully tested in game**
- **A** Nov 29 (`rung3_hold` true); `WAR SWITCH: ON` once on load (old save, expected). Sphinx landed
  Dec 17 with the war ON this time: `LANDING: rung 2 LANDED | TENSION -> RETALIATION | tension 52.95
  -> 65.0 | became the INCIDENT (cycle 5)`; `READY: rung 3 (eventide) | day 659` Dec 21; no launch
  (quest hold, with LaunchNow on).
- **Part 4, the hold:** four markets to pirates Dec 21–25 → **`Exiles Midan HOLD: Midan holds no market
  in its home system (Midan Star System) (plans can fail) | rung 3 | day 662`** the same day, logged
  even before the network's four `left` lines. Haven back Jan 2 → **`Exiles Midan RESUMES: ... -- no
  longer true | rung 3 | day 671`** within a day. No launch either side (quest hold still on).
  Cosmetic: the reason printed the system's full name; changed to the base name ("Midan") 2026-11-08.
- **Part 1, the trigger:** `rung3_trigger` Jan 14 → **`LAUNCHED: rung 3 Eventide ... prep 0.5d ...
  (quest prep) | Strike Force main 590 + 4 escort(s) ... | HELD for a quest | QUEST TRIGGER | TEST PIN
  midanLaunchNow`** the next day: the trigger overrode the hold, the prep was the quest value, and
  the trigger cleared (no second launch). Defence 1104 against an 824 baseline in ESCALATION needed
  all four escorts (cap 4).
- **Rung 3 landed Jan 31** per the 7e-2 ruling (same wound as the second run); player absent, so no
  on-screen sequence. New this time: the state was ESCALATION, so **`LANDING: rung 3 LANDED |
  ESCALATION -> ESCALATION | tension 73.0 -> 88.0 | folded in; ESCALATION upgraded to GENERAL
  ENGAGEMENT`** — the fold-in branch, seen in play for the first time. `Admiral Cunningham's Vow`
  scheduled in 5d (the second run's Vow posted on schedule: "30 days, faction hegemony, person
  exile_cunningham"). `RUNG RESOLVED ... next: ladder complete`.
- **7g summary across the three runs:** shields (5 applied, none removed; Sean saw no Nex invasion or
  raid on the three targets), hold and resume, quest hold overriding LaunchNow, quest trigger
  overriding the hold with quest prep, war-off landing skipped, war-switch log line both ways, relief
  during a pause (ruled correct). **7g DONE.** Next: the post-Pass-7 complexity review (before Pass 9),
  with the new agenda items: the rung-2/3 bar meeting on cover + distraction alone, and the first
  organic launch's pacing against the 730/913 stretch (Sean's lean, 2026-11-07).

#### Note for Sean's planned new-game rename/hide script (asked 2026-11-07; answered, nothing built)
Sean plans a new-game script that hides the Ashen, Black Fleet and Midan systems and randomises
their visible names. Effect on the 7g hold: none — the Midan home-system hold resolves the system
through the entity id `exilemidan_haven` → `getStarSystem()`, so a display-name change cannot break
it. Two cautions for when the script is drafted: (1) it must run AFTER economy load (markets bind
to entities then); (2) `tools/exiles-log-report.ps1` keys Midan ops by system NAME, so a renamed
system shows under its new name in reports. Offer stands to review the hook placement.
