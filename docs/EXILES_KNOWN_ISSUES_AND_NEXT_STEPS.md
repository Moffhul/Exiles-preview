# Exiles Known Issues And Project Plan

Created: 2026-06-10
Updated: 2026-07-19

> **Chronology note (2026-07-12):** this is a valuable implementation history but no longer a clean
> current to-do list. Many entries below were completed in later June/July sessions, and the document
> predates the Midan/Black Fleet split plus most Omicron/Black Fleet Phase-1 work. Do not delete the
> historical findings. During the next planning pass, reconcile every open/pending marker against the
> live tree and newest subsystem docs, then create a dated current-priority section at the top.

Purpose: short handoff checklist and active project plan. This is distilled from the project index, ID registry, `CLAUDE.md`, and a light source review; it is not a full in-game test pass.

## Raid-director code review — 2026-07-28 (READ-ONLY; NO CODE CHANGED)

Source review of both raid directors (`OmicronDirector` + stages/intel; `BlackFleetRaidManager` +
stages/intel) against `war-capacity-design.md`, `EXILES_BLACK_FLEET_SYSTEMS.md`, and `CLAUDE.md`.
No files were modified. **Every item below is an OPEN FINDING awaiting a Sean ruling — none of it
is accepted canon, and the "suggestion" lines are assistant-originated, not decisions.** Items 1–3
are behavior/lore calls; 4–6 are minor.

### 1. RESOLVED 2026-07-29 — Omicron collateral bombardment is not in the contract doc

> **RULING (Sean, 2026-07-29): doc updated to match the code; the code was NOT changed.**
> `collateralChance` stays at **0.5**. Rationale, now accepted canon: neither the Luddic Church
> nor the Luddic Path shares a star system with the Hegemony, so the risk of contradicting the
> "still-contingent Hegemony conflict" canon is low. The Independents and one Persean League
> market do share those systems, and Omicron treating them as valid targets is lore-plausible and
> better gameplay. Recorded in `war-capacity-design.md` §13 (collateral box). The analysis below
> is retained as the record of what was reviewed; its "suggestion" to lower the value was
> **declined**.

*(Historical record — as-reviewed on 2026-07-28, before the 2026-07-29 doc correction.)*
`war-capacity-design.md` §13 then described `OmicronRaidActionStage` as *"target discipline (only
configured target factions are raided, even in shared systems)."* The shipped code is broader:
`collateralChance` defaults to **0.5** (`OmicronDirectorConfig.java:170`, `director_config.json`
→ `operations.collateralChance`), so once an op is in a target system, **each other hostile colony
there gets a coin-flip to be bombarded** (`OmicronRaidActionStage.isCollateralTarget`, ~line 118).
Refugees are config-exempt; player colonies are code-exempt. The feature IS recorded in
`EXILES_ID_REGISTRY.md` and `EXILES_PROJECT_INDEX.md` — only the contract doc is stale.

Two separable questions:
- **Doc:** the contract doc and the code disagree; a cold session coding against §13 would be wrong.
- **Lore:** Omicron is HOSTILE to the Hegemony, Independents, mercenaries and pirates, so this
  routinely bombards Hegemony/Independent colonies sharing a system with a Church/Path target.
  `CLAUDE.md` §3 hidden canon holds the Hegemony conflict as *"still-contingent"* preparation — a
  50% collateral roll can read as that war having already begun.

*Suggestion (not a decision):* either record the ruling in `war-capacity-design.md` §13 as-is, or
lower `collateralChance` to ~0.15–0.25 so it reads as occasional machine misclassification.

### 2. DIRECTION SET 2026-07-29 (implementation pending) — the two directors disagree about player SYSTEMS

> **Superseded as a filter question.** The inconsistency below is a symptom of a missing layer, not
> a director bug. Direction: both directors take a hard "never deliberately target the player"
> invariant, and deliberate anti-player attacks move to a separate escalation layer built on
> vanilla's Colony Crisis API. See `EXILES_ANTIPLAYER_ESCALATION.md` (§1 split, §2 per-faction
> ambient exposure, §5 verified API findings, §7 sequencing). Not yet implemented.

- Black Fleet excludes any **system containing a player market**
  (`BlackFleetRaidManager.java:364`); `EXILES_BLACK_FLEET_SYSTEMS.md` §1 states it as
  *"Player-owned markets/systems are code-exempt everywhere."*
- Omicron excludes player **markets** only (`OmicronDirector.java:773`). It will send an operation
  into a system where the player holds a colony, provided it is aiming at a target-faction colony
  there. The player's colony is never bombarded, but the battle happens in their system.

Both files' comments claim the same convention; only one implements the system-wide form. Possibly
an oversight rather than a decision. Neither behavior is ratified anywhere as the intended rule.

### 3. FIXED 2026-07-29 — Black Fleet assault multi-sack ignores `targetFactions`

> **RULING (Sean, 2026-07-29): this is a bug. The Refugee faction must NEVER be directly targeted
> by any NPC faction.** Not a blanket plot shield — each faction has its own reason: the refugees
> are too weak to rise to Omicron's notice, too poor to be worth the Black Fleet's effort, and the
> Ashen Crusade is actively recruiting from them.
>
> **BUILT:** new `data/scripts/exiles/ExileProtectedFactions.java` is the single authority; checked
> in `BlackFleetAssaultStage.getTargets()` + `canRaid()` (the actual hole) and in
> `OmicronRaidActionStage.isCollateralTarget()`. Deliberately in code, not config — a config list
> implies tunability, and clearing it must not be able to expose the refugees. The redundant
> `exilerefugee` entry was therefore removed from `collateralExempt` (JSON + Java default), which
> now holds only balance-based exemptions and starts empty. Because the check runs at targeting
> time rather than being baked in at launch, it also corrects ops already in flight on a save.
> Compile-checked under Janino; config brace-balance verified. **Not yet live-tested.**
>
> **Still open (narrower, unruled):** Corsair Activity from commerce raids blankets *all* hostile
> markets in a raided system, refugee enclaves included. That is an area effect rather than direct
> targeting, so the ruling above does not settle it.

The primary victim is checked against the configured target factions (`pickVictim`). **Secondary**
multi-sack targets are not: `BlackFleetAssaultStage.getTargets()` (~line 118) and `canRaid`
(~line 147) accept any market in the system that is hostile, unsacked, unbattered and not paying
protection. The Black Fleet is **HOSTILE to `exilerefugee`** (`ExilesSectorGen.java:102`), and
refugee enclaves sit at Corvus/Mairaath/Yma/Maxios — Corvus and Mairaath both hold Independent
markets, which ARE target factions. Plausible sequence: assault launches at Corvus → Independent
primary falls → refugee enclave is a legal secondary and is sacked.

Note the asymmetry: Omicron's equivalent collateral path has an explicit exempt list **with
refugees on it**, and the extortion racket never grudges refugees. The assault stage has no such
list. (Same mechanism, milder: Corsair Activity blankets *all* hostile markets in a raided system,
refugee enclaves included — doc-consistent with §2 of the BF doc, but worth a conscious call.)

*Suggestion (not a decision):* if refugee protection is a mod-wide principle, it wants an exempt
list on the assault secondary path mirroring `collateralExempt`.

### 4. MINOR — capacity legibility index counts dead nodes

`war-capacity-design.md` §10 specifies the index as the mean of the asset-fed axes *"across all
live nodes."* `OmicronDirector.checkThresholds` (~line 666) loops every configured node with no
liveness check, so a decapitated node keeps contributing to the average and killing a whole node
does not move the "activity diminished" bands. Cosmetic only — decapitation has its own dedicated
message — but it is not what the doc says.

### 5. KNOWN, NOT NEW — test-tune values still in `director_config.json`

Cross-reference only, so it sits with the rest: BF commerce raids `intervalDays` 10 (production
45), assaults 25 (production 120), start delays 5/15 (production 90/180), extortion
`debugMessages: true`; refugee convoys likewise on test cadence with debug messages on. Canonical
checklist remains `EXILES_BLACK_FLEET_SYSTEMS.md` §6.

### 6. FIXED 2026-07-29 — BF op-list pruning is gated behind the enable flag

> **CORRECTION to the original finding.** It claimed that on re-enable "stale entries count against
> `maxActive` until incidentally cleared." That was **wrong**: `pruneEnded` ran as the first
> statement of the same branch as the cap check, so the lists were cleaned before `maxActive` was
> evaluated — there was never any launch starvation. The genuine defects were (a) up to 3 ended
> `RaidIntel` objects retained and saved while raids were disabled, and (b) the extortion cadence
> signal — success/failure of an assault — being emitted by the prune poll, so it stalled behind
> the same gate and could post months late or not at all.
>
> **BUILT (structural fix, not a guard):** the manager no longer tracks ops at all.
> `activeRaids`, `activeAssaults` and `pruneEnded` are deleted; `countActive(boolean)` derives live
> ops per tier from the intel manager, matching the `OmicronDirector` pattern that never had this
> bug. The extortion outcome now self-posts from `BlackFleetRaidIntel.notifyEnded()` — the op
> already knew its own tier (`assault` field) and outcome, so nothing needed to watch it.
> Net −25 lines, plus an unused `Iterator` import removed.
>
> **Behavior change to watch:** the extortion racket now learns an assault's outcome *immediately*
> on close instead of on the next cadence tick (up to 25d test / 120d production later). Intended,
> but it is a live change to a ~v0.8-rated loop. **Not yet live-tested.**
>
> **Save note:** removes persisted fields from `BlackFleetRaidManager`. New games unaffected;
> pre-existing test saves may not load. Per CLAUDE.md §1 priority 5 this is the accepted trade.

## Design adjustments for consideration (not scheduled)

Author-raised directions that are agreed in principle but deliberately out of scope for the change
in hand. Do not implement without a scheduling decision.

- **Consolidated, well-documented settings file (raised 2026-07-29).** Imitate vanilla's
  `settings.json`: one consolidated, heavily commented file for the settings Exiles introduces,
  so a player can find and adjust everything in one place. Today they are split across
  `data/config/settings.json` (vanilla-merged overrides + player toggles),
  `data/config/exiles/director_config.json` and `data/config/exiles/system_placement.json`, with
  some values still hardcoded in Java. Interacts with the CLAUDE.md §7 hard rule: **existing
  player-facing settings must not be moved without Sean's approval**, so a consolidation pass is
  exactly the kind of change that needs his sign-off before it starts.
- **`director_config.json` is the intended home for all faction BEHAVIOR settings** (author,
  2026-07-29) — including the not-yet-built Ashen Crusade activity, and the refugee fleet knobs:
  how many convoys at once, when the condition is applied, and how long it lasts. The refugee
  block already holds most of these; verify coverage when that work resumes.
- **Promote remaining hardcoded tunables into config.** Known: `ExilePriceSpikes.DURATION_DAYS`
  (120f, marked `// TUNE:`). Author intends a longer duration and more concurrent collector fleets
  (`extortion.maxActive`, currently 2 with a "perf audit: 1-2" note — raising it is the one
  extortion knob where the performance caveat genuinely applies).

## Session Handoff — 2026-07-18

### Completed this session

- Simplified `ExileLuddicUnrest`: removed its immigration effect and displaced-population tooltip.
  The condition now supplies only its −3 stability effect. Its player-facing CSV name remains
  **Religious Unrest**; “Luddic Unrest” refers to the same condition in code/design discussion.
- Expanded the new-game-only vanilla-market patch pass: Gilead and Hesperus receive their revised
  battlestation/KoL-garrison layouts; Tartessus, Asher, and Jangala receive the approved unrest,
  refugee-strain, and security additions. These changes deliberately require a new campaign and
  were not moved into the load-safe patch path.
- KoL Garrison now provides **+3 stability** and **×1.3 ground defense**. It still does not grant the
  Military Base officer bonus. Further commodity-demand and Marine-production tuning remains an
  author-side/manual item unless reopened.
- Completed the Omicron Core distribution pass and stable-portrait pass. Player-facing names are
  **Executor**, **Control**, and **Command** Cores. Base cash values are **5,000 / 25,000 / 100,000**
  credits. **2026-07-19:** live testing confirms recovery and ordinary contact turn-ins; current
  drop rates are accepted through Beta. Runtime reputation is now **2 / 3 / 5** before faction
  multipliers. The initial transient correction left the preview at 1 and double-awarded reputation;
  it has been removed. A precompiled `ExileAICores` subclass now supplies the same 2/3/5 calculation
  to both the vanilla preview and confirmed award. **Live retest passed 2026-07-19:** each Omicron
  grade and a mixed six-core Omicron/Gamma/Beta/Alpha batch previewed and awarded correctly once,
  with no interference between custom and vanilla cores.

### Next session priority

1. **Live-test Omicron defensive behavior toward Tri-Tachyon.** Third pass built 2026-07-22:
   the four dormant station complements no longer wake merely because a `RaidIntel` targets their
   system. An NPC operation's actual route fleet must close within 1,500 units of the assigned
   station; the exact `mktEngage` hook still handles direct player attacks. Culann separately
   houses one persistent 180-220 FP strategic detachment for 350+ FP Hybrasil threats, with a
   180-day mature-Nexus-gated replacement delay. It is not a Director node and takes priority over
   the capacity-limited mature-Nexus fallback. The first two passes proved complement creation,
   direct station pull-in at Port Tse, and a Black Fleet Nexus response; route-specific station
   activation, the new Culann reserve, thresholded player commitment, replacement, and no-double-
   response behavior require live testing. Canon:
   `docs/EXILES_OMICRON_TRITACHYON_DEFENSE.md`.
2. **If time: Ashen Crusade raiding behavior.** Define targets, cadence, force character, and desired
   consequences before implementation; no new raid behavior is ratified by this handoff.
3. **Refugee behavior — PROTOTYPE LIVE-TESTED 2026-07-23; functional, tuning pending.** One weak civilian convoy
   at a time leaves the four enclaves or Gilead for a weighted, non-hostile NPC Core market. Active
   `RaidIntel` systems, hostile/excluded factions, player colonies, unstable markets, and already
   strained markets are vetoed. Ten days in orbit applies the milder **Refugee Encampments**
   condition (−2 stability, +20% non-crew player-buy prices, −25% crew buy/sell); around day 20–25
   a local patrol uses vanilla's peaceful NPC-hassle behavior to order it away, with self-departure
   by day 30 and a 10-day aftermath. First launch/debug messages are test-tuned. Canon and live-test
   matrix: `EXILES_REFUGEE_CONVOYS.md`.
4. **Finalize custom star-system locations during Phase 1.** Xechas is satisfactory and requires no
   relocation. Spread the Omicron nodes farther apart and shift them farther west. The Black Fleet
   systems, Midan, and Midan's abandoned-colony/origin system need substantial layout revisions and
   should move farther east. Providence/Ashen Crusade remains in a temporary test location and needs
   a complete relocation to the north.

### Black Fleet protection-payment fix — 2026-07-19

- Live test exposed Derinkuyu being sacked while Price Spikes was active. The log showed an
  autoresolved Black Fleet assault: Derinkuyu was correctly excluded as the primary victim and from
  Corsair Activity, but entered the multi-sack list after Ancyra fell because that secondary-target
  path omitted the protection check.
- Fixed with three constant-time checks inside existing assault work only: target-list construction,
  live-fleet `canRaid`, and the final `performRaid` invariant. No new polling, scripts, or scans were
  added. Primary markets that begin paying after launch are also spared. `BlackFleetAssaultStage`,
  `BlackFleetRaidManager`, and `ExilesModPlugin` pass the bundled Janino compile-check.

## ⚠️ OPEN BUGS — 2026-06-21 In-Game Test (post Salvage-Yards)

Sean visited every refugee colony + the nearby vanilla colonies carrying Refugee Strain. Salvage Yards itself
checked out functionally. Four bugs found (most pre-existing, surfaced now):

### BUG 4 — CRITICAL: Refugee-Strain NPC colonies are broken/locked-down — ✅ FIXED 2026-06-21 (Java migration, pending in-game test)
**Fix applied:** removed all 5 duplicate vanilla-market entries from the econ JSONs (asharu/corvus.json,
mairaath+port_tse/mayasura.json, salamanca/yma.json, new_maxios/magec.json) — so each econ file now declares ONLY
its refugee enclave (a bare-entity market, which appends cleanly). The strain layer is now applied post-econ-load
in `ExilesModPlugin.applyRefugeeStrainColoniesOnce()`: for each of the 5 live vanilla markets (looked up by
`findMarketByPrimaryEntityId`), guarded `addCondition("exile_refugee_strain")` + `addIndustry("exile_security_center")`
+ reapply. Run-once via `$exiles_refugee_strain_done`; wired into both `applyEconomyPatches()` (new game) and
`applyLoadSafeFixes()` (load). Composes cleanly with `applyMaxiosChangesOnce` (both patch the real new_maxios market
by entity id; vanilla New Maxios already has its `orbitalworks`+`corrupted_nanoforge` and `orbitalstation`). javac
`--release 17`: clean (whole referenced source tree compiled). **Original analysis below.**


**Symptoms (all on the 5 Refugee-Strain colonies):** economy/commodity window empty (nothing for sale),
industries present but **completely non-functional**, **no Administrative officer** in the comm window,
**stability 0**, and **planetary-condition tooltips don't render** on mouse-over. The refugee colonies
themselves are fine.

**ROOT CAUSE (confirmed, high confidence):** we declare these 5 markets in our mod econ JSON, but they are
**already live vanilla markets** in `starsector-core/data/campaign/econ/*.json`. Mod econ JSON **appends** to
vanilla's `markets[]` (it does not merge by entity), so each of these entities ends up with **two conflicting
market definitions** → the market breaks. Verified asymmetry — the broken set are exactly the entities vanilla
already defines; the working refugee colonies use entities vanilla leaves uninhabited:

| Our econ-JSON entry | Vanilla already defines it? | Outcome |
|---|---|---|
| `asharu` (corvus.json), `mairaath` + `port_tse` (mayasura.json), `salamanca` (yma.json), `new_maxios` (magec.json) | **YES — live market** | **BROKEN (duplicate market)** |
| `corvus_abandoned_station`, `mairaath_abandoned_station1`, `yma_abandoned_station`, `maxios` (refugee colonies) | No (uninhabited entity) | Works ✓ |

(Maxios works precisely *because* vanilla never gives it a market — same pattern, opposite result.) This is the
first real in-game test of the Strain colonies (they were part of the 6/18 batch that was "pending test").

**RECOMMENDED FIX:** stop re-declaring live vanilla markets in econ JSON. Remove those 5 entries from the mod
econ JSONs, and instead apply `exile_refugee_strain` (condition) + `exile_security_center` (industry) to the
**existing** vanilla markets via a post-econ-load routine in `ExilesModPlugin` (same approach as
`convertRefugeeColony`: look up the market by entity id, `addCondition` / `addIndustry` if absent). Load-order-safe
and avoids the duplicate. (The refugee-colony econ-JSON entries stay as-is — they're on uninhabited entities.)

### BUG 1 — Refugee Processing Center and Security Center are not mutually exclusive — ✅ FIXED 2026-06-21 (pending in-game test)
Both could be built on the same colony; they should be **either/or** (humane vs. harsh is the whole point).
**Fix applied:** each center's `isAvailableToBuild()` now also returns false if `market.hasIndustry(<the other
center's id>)`, with a matching "mutually exclusive" `getUnavailableReason`. (`ExileRefugeeProcessingCenter`,
`ExileSecurityCenter`.) javac-clean. Caveat: this gates the *build menu* — if both are already present on a
colony (e.g. added via script/JSON), it won't auto-remove one; only blocks building the second.

### BUG 2 — Centers reduce population growth — ✅ RESOLVED 2026-06-22 (NOT A BUG; working as designed)
The Processing/Security Centers DO throttle population growth correctly. Confirmed in-game on **Daria** (player
colony + `exile_refugee_influx` + a built center) via temporary debug logging in `modifyIncoming()`: the center's
`-INFLUX_GROWTH_REDUCTION` is applied to the colony's incoming-population weight as intended. Debug logging has
since been removed. **No code change was needed.**

**Why it looked broken — two separate red herrings:**
1. **Stale docked UI.** The colony population-growth panel does NOT refresh while docked (no game-time passes while
   docked). After console-adding the condition/center from *inside* the market, you must **leave and re-enter the
   colony** (or let days pass undocked) for the growth readout to update. The throttle was working; the panel was stale.
2. **Earlier NPC-colony confusion.** The throttle is only ever *observable* on a PLAYER-owned colony. NPC colony
   size is STATIC w.r.t. immigration — `CoreImmigrationPluginImpl.increaseMarketSize()` early-returns for
   `!isPlayerOwned()` — so the centers on the 5 NPC refugee-strain colonies (which also carry neither
   `exile_refugee_influx` nor `decivilized_subpop`) can never show a size change. That is by engine design.

**Engine mechanics confirmed against `.apisrc` (keep for future economy work):** `BaseIndustry.apply(true)`
auto-registers any industry implementing `MarketImmigrationModifier` via `addTransientImmigrationModifier`;
`CoreImmigrationPluginImpl.computeIncoming()` invokes them all through `getAllImmigrationModifiers()`. Industry-as-
immigration-modifier is the canonical vanilla pattern (Spaceport/Farming/Mining/TechMining/Cryorevival). Conditions
register through the identical path, so there is no mechanical reason to prefer one over the other.

### BUG 3 — Ashen Crusade industries are player-buildable (should not be) — ✅ FIXED 2026-06-21 (pending in-game test)
Two culprits: **`ExileAshenFuelProduction`** had no `isAvailableToBuild()` override (inherited BaseIndustry's
buildable default), and **`ExileAshenMilitaryBase`** (backs `exile_ashen_military_base` + `exile_ashen_patrol_hq`)
overrode it to return *buildable whenever a functional spaceport exists* — vanilla MilitaryBase logic, so it
showed on any player colony with a spaceport. All other Ashen/KoL classes already `return false`. **Fix applied:**
both now `return false` with `getUnavailableReason` "Ashen Crusade Industry", matching the siblings. javac-clean.
If MORE Ashen industries still appear buildable in-game, check the `plugin` column in `industries.csv` for rows
mapping to a **vanilla** plugin class (Sean checking manually).

## Review Findings Integrated On 2026-06-13

- `default_ship_roles.json` is now structurally balanced.
- `rules.csv` now uses `ExileXechas` consistently for `NGCSetStartingLocation`.
- `planet_cama` now has a `CUSTOM` description row in `data/strings/descriptions.csv`.
- `exileashencrusade.faction` had a real brace imbalance around the commented-out `pirateBaseStationTypes` block; this has been fixed.
- `rules.csv` had one story-end line containing both `NGCSetStartingLocation` and `NGCAddCargo`; these are now separate commands.
- `SetTooltipHighlightColors` now targets `ngcExileEasy` instead of the non-existent `ngcEasy`.
- `exilerefugee.faction` has received a useful first polish pass: civilian-heavy roster, low doctrine quality, non-bombarding behavior, and stronger fiction.

## Session 2026-06-15 — Economy Arc, New Bug, Design Direction

### Economy / refugee-pressure arc (major outcome)
A multi-session investigation into expressing refugee economic strain concluded. **Full canonical write-up in `docs/EXILES_ECONOMY_MAP.md` (esp. §7–§8) — read it before touching refugee economics.** Headline: **scarcity must be an EVENT (a temporary, self-healing shock), not a steady market condition** — every condition-based lever fought the economy resolver. The working keeper is `RefugeeShortageScript` (food/supplies temp-shock + price spike + comm message + player relief → refugee reputation), confirmed working in-game. Also produced: `exile_pariah_colony` (−25% accessibility, "shunned by the sector") on the 4 refugee colonies, and the weak `exile_refugee_population` demand structure (limited by max-not-sum).
- Next on this thread: generalize the shortage event — add a near-blank `exile_refugee_shortage` **condition marker** (for the missing conditions-panel icon + relief-progress tooltip), **track relief amount**, use **partial deficits** (full food→0 triggers vanilla's stability penalty and risks decivilizing NPC worlds), and build the **director pipeline** (fleet arrival → Refugee Influx condition → periodic shortage rolls). Plus prototype a **"Poor/Impoverished"** condition that suppresses `getPlayerDemandPriceMod` (desperate but can't pay; composes with pariah).

### Open Bugs
- **Refugee and Omicron factions do not appear in the in-game factions/intel list** — cannot view faction info or player relations with them. **Ashen Crusade and Midan DO appear.** Suspected cause: how Refugee/Omicron are added. The refugee markets are now declared via econ-JSON (`corvus`/`mayasura`/`yma`/`magec.json`) and are functional in-game, but something that registers the faction as player-visible/known is not activated. Likely tied to the mid-save integration approach (below). Investigate faction registration/visibility for these two vs. how Ashen/Midan are set up.
  - **UPDATE 2026-06-24:** **Refugee** visibility was resolved 2026-06-16 (Nex census; see Economy Map §9). **Omicron's
    absence is now INTENTIONAL** — Omicron is a ratified **script-only (marketless)** faction handled exactly like the
    Remnants (`showInIntelTab:false`, not a Nex diplomacy participant). **Do NOT flip the flag.** Full rationale in
    `docs/EXILES_OMICRON_FACTION_ARCHITECTURE.md` (§2a).

- **RESOLVED (2026-06-18) — refugee colonies decivilizing from food starvation.** **Fix applied (Strategy B):** dropped **Free Port** from the refugee markets (removes its −3 stability hit at the source), blanked the `exilerefugee.faction` `illegalCommodities` list so trade stays open without Free Port (the Independents/Pirates precedent — nothing illegal, not even AI cores), and tuned `exile_pariah_colony`'s accessibility penalty down. Base stability now **~3** (was 0): the import strain still *reads* as at-risk but is actually stable. May want late-stage retuning. Original analysis retained below. — `exile_pariah_colony`'s accessibility penalty throttles food imports; cranked high enough (e.g. to bite domestic goods / organics) the structural food **deficit** drives colony stability to **0**, decivilizing the colony before the player can act. Root cause is the **structural food-deficit stability penalty**, not the random food-shortage *event*. Candidate fixes (Sean, 2026-06-18 — to evaluate/combine next session):
  1. **Remove Free Port from the refugee markets.** Free Port itself imposes a **−3 stability** penalty; dropping it reclaims 3 stability. Thematically fine to lose. *(Cheap, reliable; note it also restores normal legal/illegal trade rules, which interacts with the charity-rep idea.)*
  2. **Recurring food boost.** A recurring condition/script that tops up food production/stockpile often enough to avoid deciv. **Narrative (keep regardless):** the Ashen Crusade sends regular food shipments, drawing refugees to Providence — where they end up feeding the Crusade's war effort. Mechanically a "maybe."
  3. **`exemptFromFoodShortages` faction flag** (`Factions.CUSTOM_EXEMPT_FOOD_SHORTAGES`). **Investigated:** only reference in the API is *commented-out* code in `CoreLifecyclePluginImpl` that would skip a faction's markets when rolling the random FOOD_SHORTAGE **event**. So it appears to gate the *random event*, NOT the structural food-deficit stability penalty that's actually crashing the colonies — **probably won't fix this**, and current behavior is uncertain (disabled). Verify in-game before relying on it.
  4. **Arbitrary stability bonus** on a refugee condition — `market.getStability().modifyFlat(+N, ...)`, flavored as outcast solidarity / "nowhere else to go." Trivial and directly offsets the penalty. *(Cheap, reliable fallback.)*
  5. **Lower the accessibility penalty** (`ExilePariahColony.ACCESSIBILITY_PENALTY`) and accept a milder effect. *(Simplest.)*

- **LOW-PRIORITY POLISH (deferred 2026-06-18) — refugee faction posts standard-payout exploration missions.** Random survey/analyze missions (`SurveyPlanetMissionIntel` / `AnalyzeEntityMissionIntel`, auto-generated sector-wide by `GenericMissionManager`, ~20–35 active) can occasionally name the refugee faction as giver, paying the normal **distance-based** reward (`20000 + (dist/10000)*10000`, floored 10k) — a minor immersion crack (destitute refugees offering 90k to explore a probe). **No vanilla fix exists:** the reward has no faction multiplier, and there is no faction flag to disable these (unlike `postsNoBounties` for bounties; `no_contacts` doesn't apply — these bypass bars/contacts entirely; the mission code has zero faction checks). **Vanilla already mostly hides it:** givers are size-weighted (refugee colonies are size 3 → low weight) and ~67% of would-be-refugee missions auto-reassign to Independents, so it's already rare. **If ever worth fixing:** subclass `SurveyPlanetIntelCreator` + `AnalyzeEntityIntelCreator`, override `createMissionIntel()` to return null (or scale reward) when the giver resolves to `exilerefugee`, and swap them into `GenericMissionManager` at game start — contained but touches a sector-wide system. **Decision: accept for now, do not change.**

### Design Direction Change — the mod should REQUIRE a new game
- Decision (2026-06-15): **Exiles should require a new game rather than integrate into an existing save.**
- **RECONFIRMED 2026-07-12 as project priority #5:** integrating Exiles into an existing campaign is
  no longer a priority at all. Choose clean new-game generation and narrative correctness over mid-save
  migration machinery. This does not relax save/load stability within a campaign started with Exiles.
- Rationale: it lets the refugee colonies (and Omicron) be built the **vanilla way** — declared in sector generation / econ JSON instead of script-patched onto existing abandoned stations at load — which should fix bugs like the faction-not-showing issue and remove a whole class of mid-save integration fragility.
- The mod's gameplay fundamentally hinges on the **custom start**: the player begins in **Xechas** with a couple of broken-down ships and two tiny colonies (Thar/Daria). Mid-save compatibility was never essential to that.
- Action deferred. When taken: re-home the refugee colony setup out of `ExilesModPlugin` load-time patching into proper world-gen; re-verify Refugee/Omicron faction visibility; drop the "safe to add mid-save" constraint.

## Session 2026-06-16 — Refugee Visibility Bug Diagnosed (regression) + Re-home Underway

- **Root cause of "Refugee faction not in the factions/intel list":** two separable things. (1) Vanilla: the
  `showInIntelTab` flag in `exilerefugee.faction` is `false` (inherited from a neutral template). (2) Nexerelin:
  faction visibility + diplomacy are granted by a **market census** (`SectorManager.reinitLiveFactions`, runs in
  `onNewGameAfterEconomyLoad`) that only counts factions **owning a market at that instant** — and Nex **overrides**
  the static flag. Full mechanics in `docs/EXILES_ECONOMY_MAP.md` §9.
- **It was a regression:** the recent all-script refugee build (replacing the JSON-append `corvus.json`) made faction
  ownership happen *during* the racy hook phase instead of at econ-load, so Nex's census missed refugees.
- **✅ RESOLVED for Asharu (2026-06-16).** Refugees now appear in the Nexerelin Factions tab and have a diplomacy
  profile. The working recipe for a Nex faction colony, in full: (1) `exerelinFactionConfig/mod_factions.csv` lists the
  faction id, (2) `exerelinFactionConfig/<id>.json` has `playableFaction:true`, (3) the market is declared in econ JSON
  (`"faction":"exilerefugee"`) so it's owned at econ-load. Exiles previously had NONE of these wired (the old
  `exerelineactioneonfig/player.json` was misspelled + misnamed = dead). Details in `docs/EXILES_ECONOMY_MAP.md` §9.
- **✅ Step (b) DONE & confirmed in-game (2026-06-16):** all four refugee colonies re-homed to econ-JSON appends
  (`corvus`/`mayasura`/`yma`/`magec.json`) + slim `convertRefugeeColony`; Maxios carries its full vanilla Magec
  conditions. All four markets functional under Nexerelin.
- **✅ Dead-code cleanup DONE (2026-06-16):** removed the orphaned 449-line heavy refugee-market + personnel cluster
  from `ExilesModPlugin` (no longer called after the JSON re-home). **✅ Compile confirmed (2026-06-17):** new game
  started and ran without issue.
- **Cleanup/polish remaining (non-blocking):** ~~flip `showInIntelTab:true` for the pure-vanilla case~~ ✅ DONE
  (2026-06-17), tune the Nex config's diplomacy/invasion semantics (Sean doing manually), and ~~delete the dead
  `exerelineactioneonfig/player.json`~~ ✅ DONE (2026-06-17, whole folder removed).
  Ashen/Midan/Omicron still need their own `mod_factions.csv` + config wiring for Nex diplomacy (separate scope).
- **Repo:** mod is now under Git at `github.com/Moffhul/Exiles` (private).
- **PLAYER-FACING COMPAT CONSTRAINT — Nexerelin "random core worlds" mode is NOT supported.** The refugee colonies
  (and Exiles' entire narrative) bind to vanilla Core entity IDs (`corvus_abandoned_station`, etc.) that do not exist
  in a randomized core. Exiles is **corvus-mode / vanilla-layout only** and this must be advertised to players.
- **Custom art TODO:** the refugee station still shows the vanilla abandoned-station market illustration; needs a
  custom graphic for the scavenger society that moved in.

## TODO (2026-06-17): Faction Descriptions + Illegal Goods

Two flat content passes across all four factions (`exileashencrusade`, `exilemidan`, `exileomicron`,
`exilerefugee`):

### Faction descriptions — `data/strings/descriptions.csv`
- These are NOT stored in the `.faction` files. One row per faction with `type=FACTION`, `id=<factionId>`,
  and the blurb in the `text1` column (quote the text if it contains commas). Columns: `id,type,text1,...,notes`.
- **✅ First-pass DRAFTS added for all four (2026-06-17)** — `exileashencrusade`, `exilemidan`, `exileomicron`,
  `exilerefugee`, each tagged `DRAFT ... review/edit voice later` in the notes column.
- **⬜ STILL TODO: Sean to review & edit the four drafts in his own voice** (lore/tone pass).
- Example shape (vanilla): `luddic_church,FACTION,"The Church of Galactic Redemption is ...",,,,,`
- Shows in the in-game faction/intel panel — pairs naturally with the now-visible Factions tab work.

### Design note — "Black Fleet" vs "Midan" (naming, kept on purpose)
- `exilemidan`'s `displayName` is still **"Black Fleet"** — this is the faction's *original* name, deliberately not yet
  renamed to "Midan" (Sean's later coinage; recalled as ~"zero" in an ancient language, Persian/Aramaic — matches the
  `personNamePrefix:"zero"` already in the faction file).
- **Open narrative idea (may not be technically feasible):** start them in-fiction as a mysterious group everyone simply
  calls the **"Black Fleet,"** and only *later* (quest/intel reveal) does the player learn who the **Midan** actually are.
  If feasible, the display name stays "Black Fleet" until a reveal flips it. Revisit during Phase 2 quest design.
- **Fleet redesign (Phase 0 / future):** the Black Fleet needs a **complete fleet redesign** with a strong emphasis on
  **stealth** (low-profile/ambush doctrine fits the "strike from forward bases with cold discipline" framing).

### Illegal goods — `illegalCommodities` array in each `data/world/factions/<id>.faction`
- Add commodity ids (e.g. `drugs`, `organs`, `hand_weapons`, `ai_cores`, `harvested_organs`, etc.) to the array.
- Current state: **Ashen Crusade** already populated (`drugs`, `organs`, …); **Midan, Omicron, Refugee** are empty `[]`.
- Pick per faction character — e.g. Refugees likely tolerant (few illegal); Omicron may ban organics/AI per machine
  logic; Midan principled-nihilist set. Use vanilla `hegemony.faction` / `luddic_church.faction` as commodity-id refs.

## Session 2026-06-21 — Salvage Yards Industry (BUILT; IN-GAME FUNCTIONAL ✓)

The refugees' economic-backbone industry (CLAUDE.md §3: "scraping by via salvaging, scrapping, begging"),
the last A-list Phase 0.5 item. **`javac --release 17` against the real Starsector API: clean (exit 0).**
**Sean in-game test 2026-06-21: functional ✓** (showed/produced on the refugee colonies; not in the bug list).
The Strain-colony lockdown (BUG 4 above) is a separate pre-existing econ-JSON issue, NOT a Salvage Yards problem.

## Session 2026-06-21 — Refugee colonies as PER-COLONY NARRATIVES; Wildcat Mining (Maxios) BUILT

> **✅ IN-GAME CONFIRMED 2026-06-22 (Sean):** Wildcat Mining (Maxios), the renamed Independent Salvage rework,
> and the Yma Broken Siphon + Broken Processor industries all check out and work correctly in-game. The Yma
> *upgrade* path (Broken Siphon → Makeshift Gas Mining, Broken Processor → Jury-Rigged Processor, and the
> resulting self-feeding fuel chain) was deliberately NOT tested — there is no upgrade mechanism yet by design;
> that swap is a Phase 2 quest outcome. So what's confirmed is that the two inert Broken industries display
> correctly and keep the Yma colony intentionally near-dead until the (future) quest brings the station online.

**Design principle (Sean):** the Refugee faction is a *different kind* of Starsector challenge — a political/
socioeconomic **problem resolved through quests & player choices, not combat**. **Each refugee colony gets its own
narrative/situation, and its bespoke industry is how that situation surfaces on the economy screen before any
dialog exists** (industry = quest seed). So refugee colonies get DIFFERENTIATED industries: Salvage Yards stays
the scrap backbone (Corvus/Mayasura/Yma), but story-specific colonies swap it for a custom one.

**Maxios — `ExileWildcatMining` / `exile_wildcat_mining`** (industries.csv row, order 228; placeholder icon =
copy of exileashenmining.png, custom art TODO). **Replaced `mining` + `exile_salvage_yards` on the Maxios refugee
market** (`magec.json`) — Maxios is now the rare-ore-vein colony. NPC-only (`isAvailableToBuild→false`).
- **Flat, non-scaling output:** ORE 1, RARE_ORE 2 (efficient for a size-3 squat but can't grow). **Demands**
  HEAVY_MACHINERY 2 + DRUGS 2 (improvised gear + keeping a brutalized workforce doped); heavy-machinery deficit
  throttles output. **Planetary deposit conditions deliberately UNTOUCHED** (Sean: they measure the planet's true
  resources; the vein's productivity lives in the industry where we control it).
- **Lore/quest seed:** refugees squat on a vein that New Maxios (the independent strain colony, whose people are
  FROM Maxios) considers theirs; decivilized survivors still on Maxios; Phase 2 questline decides whether the
  refugee colony may remain. **Kanta's Den (pirate queen) is in-system** → raider-pressure / who-protects-them
  hook + a natural drug source for the DRUGS demand. (Logged to Phase 2 quests.)

**Mairaath / Mayasura — Salvage Yards REWORKED + RENAMED → "Independent Salvage" (javac-clean, pending test).**
Mairaath lore: once a great garden-world power with three terraforming stations; the Luddic Path sabotaged them,
one crashed into the planet → post-apocalyptic desert (`ruins_vast`), still a functional colony. The refugees
swarm it "like locusts" to pick the bones of the dead cities for scrap — to the survivors' fury. This is salvage's
natural home (it lives on all three scrap colonies: Corvus/Mayasura/Yma; id `exile_salvage_yards` unchanged, only
the display name + recipe changed — full id rename offered to Sean, deferred).
- **New recipe (flat, ruins-dependency dropped):** supply METALS 2 + HEAVY_MACHINERY 1 + DOMESTIC_GOODS 1;
  **demand FUEL 3** — deliberately more than these destitute, fuel-collapse-era colonies can get, so they run a
  **chronic fuel shortage**. The shortage is INTENTIONAL: **no `applyDeficitToProduction` on fuel, production does
  NOT shut down on the unmet demand** (only on genuine `isFunctional()` disruption). Ties into the mod's Nachiketa
  fuel collapse. ("Independent" = unaffiliated scrappers, no infrastructure — "Yard" sounded too civilized.)

**Yma / Abandoned Siphon Station + Salamanca — TWO quest-upgradeable industries BUILT (javac-clean, pending test).**
Darkest of the three. Narrative (Sean, to fine-tune): refugees were lured to **Salamanca** (vanilla: ex-Mazalot
regime, deep in criminal networks, mistrusted even by its League allies) with promises of work → slave labor or
worse; the **Siphon Station** settlement escaped/avoided it. Questline: keep their freedom + an engineer among them
trying to bring the dead Domain station back online. **Payoff = FUEL, the scarcest thing in the post-Nachiketa sector.**
- **Mechanism — 4 separate industries + scripted swap (NOT the vanilla player-upgrade path).** The colony is NPC and
  the "upgrade" is a quest outcome, so the quest does `market.removeIndustry(brokenId)` + `market.addIndustry(workingId)`.
  Cleaner/more reliable than driving the Heavy-Industry→Orbital-Works upgrade API on an NPC market. All 4 are
  NPC-only (`isAvailableToBuild→false`).
  - `exile_broken_siphon` (Broken Siphon, inert) → upgrades to `exile_makeshift_gas_mining` (**VOLATILES 3**, demand HEAVY_MACHINERY 1).
  - `exile_broken_processor` (Broken Processor, inert) → upgrades to `exile_juryrigged_processor` (**FUEL 3**, demand VOLATILES 3).
  - Both inert ones share one class **`ExileBrokenStructure`** (produces/demands nothing). The chain self-feeds once
    both are online: gas mining's 3 volatiles cover the processor's 3-volatile demand → net **3 fuel** on a size-3 refugee colony.
- **Yma refugee market (`yma.json`):** swapped out `techmining` + `exile_salvage_yards`, **starts with only the two
  Broken industries** (+ population/spaceport/orbitalstation). So the colony is economically near-dead until the
  quest brings the siphon online — intentional ("broken station with potential"). Salvage now lives only on
  Corvus + Mayasura.

**Corvus / Asharu — narrative-only, the GATEWAY colony (no bespoke industry — by design).** Asharu: a sabotaged
terraforming world (like Maxios) with a small, fiercely independent colony (like Mairaath) — but Independents read
as "good guys," and they're in Hegemony-controlled Corvus, so they're relatively **chill about thousands of refugees
in orbit**: sympathetic but too poor to really help. Sean's call: **the narrative carries the difference, no unique
industry.** Mechanically it keeps Independent Salvage (like Mairaath); only change = **removed the station's
`ruins_scattered`** (corvus.json refugee market) so it doesn't read as a ruins-pick site. NOTE: techmining now has
no ruins to work → produces ~nothing; flagged to Sean whether to also drop it.
- **Why no industry is correct:** Asharu's role is **structural** — the safe-ish harbor the refugees use as a
  jumping-off point into the wider sector. It's the connective tissue that wires the refugee story into the
  Hegemony/League war and an Ashen Crusade introduction (Phase 2). The "itinerant workers / beggars" idea is
  redirected to a **Phase 1 sector activity** (refugee labor fleets fanning out from Asharu, causing friction) —
  a better home than a static industry, and it IS the "causing trouble" hook.

**THE FOUR REFUGEE COLONIES — four faces of one crisis (design quartet, complete 2026-06-21):**
- **Maxios** — *contested* (Wildcat Mining; rare-ore vein New Maxios claims).
- **Mairaath/Mayasura** — *resented* (Independent Salvage; scavengers the survivors want gone).
- **Yma/Siphon Station** — *enslaved/escaped* (Broken Siphon+Processor → quest-upgraded fuel; Salamanca slave-labor escapees).
- **Corvus/Asharu** — *dispersing* (narrative gateway; sympathetic host, refugees fan out into Hegemony space).

**DESIGN NOTE — refugee industries as a future PLAYER-BUILDABLE set:** Salvage Yards / Wildcat Mining (and the
other planned per-colony industries) are NPC-only for now. We may later expose them as a full **player-buildable
industry set** (e.g. unlocked via the refugee trust arc). **Deferred deliberately to avoid scope creep** — flip
each class's `isAvailableToBuild` + add a gate when we go there.

- **`ExileSalvageYards` / `exile_salvage_yards`** (`data/scripts/industries/`, row in `data/campaign/industries.csv`,
  tag `industry`, order 227). Producing industry, **not player-buildable** (`isAvailableToBuild()→false`, like the
  Ashen industries / algae processor) — it's the refugees' own identity, declared on their markets in econ JSON.
- **Production:** supply METALS (size) + SUPPLIES (size−2); demand HEAVY_MACHINERY (size−2) as upkeep, with a
  machinery deficit throttling both outputs via `applyDeficitToProduction`. **Ruins bonus:** +1 metals on
  scattered/widespread, +2 on extensive/vast — "more wreckage to pick over." Distinct from techmining (tech/items
  vs. breaking down hulls); the two are complementary, not redundant. SUPPLIES output is the deliberate niche that
  gives destitute refugee colonies something worth trading.
- **Wired into all four refugee colonies** (econ-JSON `industries` lists): Corvus (`corvus_abandoned_station`,
  ruins_scattered → +1), Mayasura (`mairaath_abandoned_station1`, ruins_scattered → +1), Yma (`yma_abandoned_station`,
  no ruins → +0), Magec (`maxios`, ruins_extensive → +2).
- **Placeholder icon:** `graphics/icons/industry/exilesalvageyards.png` is a **copy of `exileashenmining.png`** —
  needs custom scrap/salvage art later (shipwright-not-painter; same TODO bucket as the refugee station illustration).
- **Tuning knobs (all trivial to adjust):** output amounts, the HEAVY_MACHINERY upkeep, the ruins-bonus tiers, and
  `isAvailableToBuild` (flip to gate on a refugee condition if a player trust-arc should let the player build it).

## Session 2026-06-23 — Midan Home-System Economy (self-sufficiency pass)

Reworked the `exilemidan` home system so the "main bad guys" aren't an economic joke. Root cause was the vanilla
**accessibility hostility penalty** (everyone is hostile to Midan → accessibility throttled toward 0). Mechanics fully
researched and documented in `EXILES_ECONOMY_MAP.md` §7c (producer-side / self-sufficiency rule), §10g (accessibility:
hostility is binary-per-faction at ≤−50, size-weighted), §10h (econ-group isolation), §10i (AI cores / special items
via econ JSON).

- **`exilemidan.json` now has 4 markets:** Fawkes (size5, ore — fuel production), Haven (size6, freePort — habitable
  farming/light-industry/mining hub + military base), Mining Station (size5 — refining + orbital works + pristine
  nanoforge), Gas Giant (size3 — volatiles → the fuel chain's input).
- **Self-sufficiency (max-producer ≥ max-consumer per commodity):** MET for food, organics, domestic goods, luxury
  goods, volatiles, ships, heavy machinery, ore, rare ore, metals, rare metals. **SHORT:** supplies −3, fuel −2,
  drugs −2 (and an ignorable organs −2). Binding consumer for supplies/fuel is Haven (megaport + military base, both
  demand 6).
- **DECISION — fuel fixed via Synchrotron.** Swapped Fawkes' fuelprod from `["fuelprod","alpha_core"]` →
  `["fuelprod","synchrotron"]` (Synchrotron +3 fuel; `size−2+3 = 6` meets demand). Narratively a salvaged Domain
  synchrotron core, not just optimization. **DONE.**
- **RESOLVED 2026-07-12 — freed alpha core placed on Orbital Works (intentional manual edit):** even with
  the pristine nanoforge, Midan otherwise could not produce enough hulls to meet its own demand. The live
  `exilemidan.json` entry `["orbitalworks", "pristine_nanoforge", "alpha_core"]` is authoritative.
- **Intended struggle:** leave **drugs** (and likely **supplies**) short on purpose — both lack any production-item
  fix, drugs only nicks colony growth, and a controlling faction running its mining workforce on rationed narcotics
  fits the Midan. Fuel was the right one to *fix* (punishing + a clean item existed).
- **Bug noted:** Fawkes has `ore_abundant` but no Mining industry, so the condition produces nothing (cosmetic only —
  ore is already met system-wide). Drop the condition or add Mining if desired.
- Rejected options (logged for context): shrinking Haven (weak; costs patrols + the size-6 "player faction gone off
  the rails" motif) and downgrading the military base to a patrol HQ (guts the in-system patrols Sean values).

## Highest-Value Checks

1. Run an in-game new-start smoke test.
   - Verify both Normal and Easy custom-start paths.
   - Confirm the story path reaches the same final setup as the direct difficulty path.
   - Confirm the player starts in `ExileXechas` near the intended coordinates and that Thar/Daria colonies exist.

2. Verify the Nexerelin-style config path.
   - Current path: `data/config/exerelineactioneonfig/player.json`.
   - The folder spelling looks unusual and should be checked before assuming this config is loaded.
   - Decide whether Nexerelin compatibility is actually in scope before spending more time here.

3. Confirm player-start economy assumptions.
   - The external Player Start doc reportedly says the Academy stipend is removed via `settings.json`, but no `settings.json` exists in this mod tree.
   - Starting debt is still design-open: custom script vs Spacer debt vs no debt in the first playable pass.

4. Keep Zhang Allat provisional.
   - Current local portrait: `graphics/portraits/characters/zhang.png`.
   - The file was copied from Faces in the Void and renamed to avoid a cross-mod dependency.
   - If Zhang remains in the mod, consider replacing it with a new portrait later.

5. **RESOLVED 2026-07-12: remove the Midan task-force test.**
   - The test confirmed that the League-derived faction configuration produces a League-like Midan fleet.
   - The event script, its orphaned watchdog, both plugin registrations, and its debug UI were removed.
   - Midan's fleet list remains open for later tuning; the removed attack is not Phase-1 gameplay.

## Active Build Order

Build in this order to avoid rework:

1. Factions: finish the nouns.
2. Activity scripts: add the verbs.
3. Quests: give the player leverage over the verbs.
4. Alpha Build Test: complete author playthrough after Phases 0–2.
5. Polish, then publish the public WIP Beta.

## Phase 0: Finish The WIP Factions

- Use `data/world/factions/exileashencrusade.faction` as the polish template, but keep its recent brace fix in mind.
- Ashen Crusade cleanup: update the roster around their origin as a Knights of Ludd splinter group calling itself the Knights of Gilead, later joined by many Luddic Path members.
- Ashen ships should be a deliberate blend of Luddic Church and Luddic Path hulls/variants, not simply one faction's list.
- Ashen known desired additions: include the Luddic Path Lasher variant and the Luddic Path Prometheus warship variant. Do a fuller LP ship review before finalizing the roster.
- Midan needs the largest fleet rework. Keep them principled nihilists, not cartoon raiders.
- Omicron needs more identity polish around misclassification and machine logic, not malice.
- Refugees should stay weaker than Independents: civilian hulls, standard-for-type armaments, rare combat ships, avoidance/negotiation bias.
- Add or tune faction sale frequencies, roles, ranks, fleet names, portraits, voices, and doctrine so each faction expresses its design principle in-game.

## Phase 0.5: Refugee Subsystem

This should happen before broad sector activity because it gives the Refugee faction a gameplay spine.

- **UPDATE (2026-06-15):** the economic-pressure mechanism is now settled — see `docs/EXILES_ECONOMY_MAP.md` §8. Lead with the **shortage-event** (`RefugeeShortageScript`), not the influx condition's direct economic effects. The Processing/Security/Salvaging industries below still stand as the player-buildable mitigations.
- Apply `exile_refugee_influx` to multiple NPC core-world colonies to show sector-wide displacement, not just player pressure.
- Add a Refugee Processing Center: humane mitigation, reduces instability, slightly reduces growth, costs more.
- Add a Security Center or better-named equivalent: harsher mitigation, reduces instability and growth more strongly, costs less.
- Add a salvaging/scrap industry as the Refugee economic backbone. **✅ BUILT 2026-06-21 (`exile_salvage_yards`),
  pending in-game test — see the 2026-06-21 session note above.**
- Refugee settlements currently patched by `ExilesModPlugin`: Asharu Refugee Platform in Corvus, Mairaath Refugee Enclave in Mayasura, Yma Refugee Enclave in Yma, and Maxios in Magec. All are size 3.
- Tune `ExileRefugeeInflux` after testing; current values are deliberately easy to adjust.

## Requested review — Black Fleet extortion driver (author request, 2026-08-01)

**Sean has asked for a review, and possible rewrite or recreation, of the Black Fleet extortion
fleet driver (`data/scripts/exiles/ExileBlackFleetExtortionManager.java`), and possibly other
fleet drivers alongside it.** Not scheduled and not scoped yet; raise it with him before starting.

Context worth carrying into that review:

- The extortion manager predates the Ashen work and uses a **sim-faction disguise** (collectors
  spawn as `independent`, with hyperspace spawn/despawn to dodge the anchor's own patrols). That
  was the right call for a *covert* racket, but it is the heaviest of the mod's fleet approaches.
- The Ashen relief driver (`AshenReliefManager`, Pass 3) solved a similar
  "NPC faction keeps shooting my special-purpose fleet" problem with **one line** —
  `MemFlags.FLEET_IGNORED_BY_FACTION` set to a single faction id (vanilla precedent:
  `EconomyFleetRouteManager`). That flag may simplify parts of the extortion driver, or at least
  parts of any future driver that would otherwise reach for the disguise.
- `EXILES_BLACK_FLEET_SYSTEMS.md` §7 already lists extortion-related open items, including the
  untested grudge system-pull and the pre-production config restore.
- ~~**Phase-1 Director convergence (CLAUDE.md §6) is the natural home for this work**, and the Ashen
  **Pass 5 rule-of-three extraction** is the moment shared fleet-driver structure gets factored
  out.~~ **SUPERSEDED 2026-08-05.** Pass 5 ran: 5a extracted the condition base classes, 5b was
  CLOSED without a build, and shared fleet-driver structure was found not to exist — feasibility
  gating in particular is **rejected on the merits, do not re-propose**. The extortion review is
  therefore a standalone review of one driver, not a step toward convergence. See CLAUDE.md §6
  "Architecture direction" and `EXILES_ASHEN_CRUSADE_WAR.md` §14.5/§14.6.

## Phase 1: Sector Activity

- ~~Replace the throwaway Midan Corvus attack with a configurable Sector Destabilization Director.~~
- ~~One director should support per-faction configs: targets, source markets, cadence, escalation triggers, warnings, and failure states.~~
- **BOTH SUPERSEDED 2026-08-05 — the unified Director was tested and REJECTED.** The Corvus prototype was removed 2026-07-12. The convergence goal was written when zero real directors existed; once three did, the Ashen **Pass 5** extraction tested it and it mostly failed. **5a (market conditions) succeeded** — `ExileTimedCondition` / `ExileCommodityShortageCondition`, no director file touched. **5b (directors) was closed without a build** — live-op scanning was seven sites in three families (one helper survived, `ExileLiveOps`), feasibility gating was five sites differing in scope, formula and purpose and is **rejected on the merits, do not re-propose**, and cooldown helpers save nothing. Extraction there is *actively harmful*: the differences are playtest-earned rulings, and one shared name over two deliberate definitions invites a later "consistency fix" that silently undoes them. **Settled architecture:** each faction director is a **sibling on the vanilla `RaidIntel` pipeline**, sharing **vocabulary** (`director_config.json`, `ExileUnrestGuard`, `ExileProtectedFactions`, `ExileLiveOps`, condition base classes, the Emergency-Measures "Battered" check) and **not control flow** — no director knows another exists. Global coordination is achieved with shared read-only STATE, which is the only thing fusion would have bought. Hegemony–League is the **fourth sibling**. Full reasoning: CLAUDE.md §6 "Architecture direction", `EXILES_ASHEN_CRUSADE_WAR.md` §14.5/§14.6.
- Planned plugs: Ashen attacks Tri-Tachyon, Omicron defends Tri-Tachyon and attacks Luddic worlds, Midan raids from forward bases, Refugee convoys move through the Core, food shortages hit stability, Hegemony/League conflict escalates. **2026-07-22:** Omicron's Luddic raids and third-pass Tri-Tachyon defense (station-only sleepers + Culann strategic detachment) are built; the defense pass is pending the live-test matrix in `EXILES_OMICRON_TRITACHYON_DEFENSE.md`. **2026-07-23:** the one-convoy Refugee prototype is built and pending the live-test matrix in `EXILES_REFUGEE_CONVOYS.md`. **2026-08-16 — v1.0, BEHAVIOUR COMPLETE (Sean; reaffirmed 2026-08-24):** the Ashen Crusade war director is **BUILT through Pass 7** — five subsystems (relief fleets, sabotage, interdiction raids, blockade, one-time Culann strike), rulings A–K settled. Passes 1–4, 5a and 6a/6c live-tested as they landed; 5b closed without a build; 6b cancelled and relocated to the Hegemony–League war; Pass 7 built 2026-08-15 and live-tested with fixes through 2026-08-16. 6d is built and confirmed in-game 2026-08-24 (author tuning and its custom description still open). Remaining: **Pass 7.5** (independent review by a DIFFERENT MODEL, reviewing the finished stack alongside all remaining faction behaviours) and **Pass 8** (config sweep). Canon: `EXILES_ASHEN_CRUSADE_WAR.md`. **2026-08-05:** its Pass 5 RAN. 5a extracted the condition base classes; 5b was CLOSED — shared target-picking and feasibility gating do NOT exist as extractable structure (feasibility is rejected on the merits), so nothing feeds a general Director and none is being built. Its Pass 6 blockade plumbing IS faction-generic, for reuse in the Hegemony–League war.
- **Omicron logistics — player-degradable attacks: DESIGN DECIDED 2026-06-22 (not built).** Model Omicron logistics
  as bespoke destroyable ENTITIES (vanilla Remnant-station / `SourceBasedFleetManager` model), NOT economy markets:
  the Nexus / Sensor Array / Damaged Nexus (already in `ExileOmicron0.java`) become station-mode destroyable sources
  with a graduated integrity scalar the Director reads to scale raid cadence/strength/warning; player weakens by
  damaging/destroying them. Drop the Remnant `totalLost` escalation term; invest in legibility (warning + intel row);
  defer the player-only HostileActivity meter. Full rationale + implementation shape + class refs in
  **`docs/EXILES_OMICRON_LOGISTICS.md`**. This is an additive Phase 1 combat lever that complements (not replaces)
  the Phase 2 Omicron quest arc.
  - **RATIFIED 2026-06-24 — Omicron is a SCRIPT-ONLY (marketless) faction** (NOT an economy faction). Full
    economy-vs-script-vs-hybrid investigation + decision in **`docs/EXILES_OMICRON_FACTION_ARCHITECTURE.md`**, which
    also recovers the vanilla pirate/Luddic-Path temporary-base lifecycle code map. Net: marketless destroyable
    station-mode sources + logistics entities the player degrades; clean base destruction (entities, not deciv-bombed
    colonies); Nex-compatible by staying out of Nex diplomacy (like Remnants/Omega). Phase-0 first step: promote the
    Nexus / Damaged Nexus / Sensor Array from bare entities to destroyable station-mode fleet sources (they currently
    spawn nothing).
- **Refugee labor dispersal (the "itinerant workers / beggars" idea, redirected here from being an industry):**
  refugee labor fleets fan out from **Asharu** into Hegemony space looking for work and causing friction — the
  "causing trouble" hook that feeds the Asharu gateway questline. Better as an activity than a static industry.

## Phase 2: Quests And NPCs

- Build a reusable dialog + intel + persistent-flag scaffold before writing bespoke quests.
- Refugee trust arc: earn the right to shelter or redirect refugees.
- **Maxios arc (per-colony refugee narrative):** the refugee Wildcat Mining squat vs. New Maxios's claim (their
  people are from Maxios; decivilized survivors remain there; New Maxios won't help them). Player ultimately
  decides whether the Maxios refugee colony is allowed to remain. Raider pressure from **Kanta's Den** (in-system)
  is a live hook — the miners can't defend an undefended rare-ore haul. Industry already seeds this (`exile_wildcat_mining`).
- **Mairaath arc (per-colony refugee narrative):** the survivors of Mairaath (Persean League, the strain colony)
  want their "pest problem" gone — refugees swarming the `ruins_vast` of their dead cities (Independent Salvage).
  Quests focus on them seeking aid to end it: pulling in another League world, or more direct means. Player chooses
  whether/how the refugees are pushed out or allowed to stay. Industry seeds it (`exile_salvage_yards`).
- **Yma / Siphon Station arc (per-colony refugee narrative):** refugees lured to Salamanca (ex-Mazalot criminal
  regime) with promises of work → slave labor; the Siphon Station settlement escaped. Arc = keep their freedom +
  the engineer's project to bring the dead station online, **quest-gated upgrades** that swap Broken Siphon →
  Makeshift Gas Mining and Broken Processor → Jury-Rigged Processor (via `removeIndustry`/`addIndustry`), ending in
  a rare fuel-producing free refugee colony. Industries already seed it (`exile_broken_siphon`/`exile_broken_processor`).
- **Corvus / Asharu arc (per-colony refugee narrative — the GATEWAY):** sympathetic-but-poor Independent host;
  refugees use the station as a jumping-off point around Hegemony space looking for work (→ trouble). Hooks into a
  **Hegemony-side questline** drawing the player into the Hegemony/League war and/or an **Ashen Crusade introduction**.
  No bespoke industry — this colony's distinctiveness is structural/narrative (the connective tissue to the wider mod).
- Ashen arc: help, moderate, or hinder the Crusade.
- Omicron arc: Tri-Tachyon needs combat data and Omicron cores to explain the unauthorized Luddic attacks
  and explore regaining control without causing Omicron to reclassify Tri-Tachyon as hostile. The arc must
  preserve Tri-Tachyon's dependence on Omicron's intended defensive support, the Ashen Crusade's false belief
  that Tri-Tachyon ordered the attacks, and internal TT factions favoring control, shutdown, or exploitation.
  **Hidden canon ratified 2026-07-22:** Omicron remains obedient to its defensive mandate but has expanded
  it into preemptive threat reduction because distant response is too slow and overt autonomous basing in
  the Core is constrained. It treats the Luddic powers as the first immutable/high-confidence threat and
  is building toward a still-contingent Hegemony conflict. Gilead was a catastrophic support-network
  classification, not malice. **No one in the Core, including Tri-Tachyon, knows this at campaign start.**
  Preserve it as a layered quest revelation; characters may hold incomplete or false theories beforehand.
- **Omicron quest-giver concept (OPEN CHARACTER DESIGN, 2026-07-14):** a Tri-Tachyon scientist who
  helped develop Omicron, sincerely wants the Luddic attacks stopped, and remains intensely fascinated
  by how the network is adapting. Avoid an "adorkable" presentation: fascination may make the character
  difficult or unsettling, but not cute by default. Gender is **undecided** (current nonbinding lean:
  male); keep neutral language until Sean rules. Preserve player agency while giving the scientist prior
  consequential actions, responsibility, goals, and constraints rather than making the quest-giver inert.
- **Omicron Cores — FUNCTIONAL FIRST PASS BUILT 2026-07-12; DISTRIBUTION/TURN-IN PASS 2026-07-18:** three tactical grades are separate
  droppable/storable commodities and genuine player-installable AI officers; Omicron fleets receive them
  through a faction-scoped vanilla officer-generator seam; Nexus-class stations and sufficiently serious
  capital-led fleets carry a single Command Core, while support stations use ordinary Gamma Cores; vanilla salvage handles
  recovery. Full contract, balance, contact-turn-in caveat, and test list: `EXILES_OMICRON_CORES.md`.
  Names are Executor/Control/Command; one stable portrait is used per grade; reduced base cash values are
  5k/25k/100k. Grade-specific reputation values are implemented at 2/3/5 before faction multipliers
  through the precompiled `ExileAICores` vanilla-command subclass;
  recovery and ordinary turn-ins are playtest-confirmed and held through Beta pending broader feedback.
  Still open: individual-vs-network ontology, final original art, primary-Nexus unique-object possibility,
  and quest/research uses.
- Hegemony/League arc: stop hotheads and let cooler heads prevail.
- Captain Tapper arc: he is alive; keep his fate mysterious until quest design.
- Midan recruitment arc: let the player engage with their destroy-everything-to-save-ourselves ideology.

## Post-Phase-2 Passes

These are validation/release milestones, not additional numbered development phases:

1. **Alpha Build Test:** Sean completes a full campaign after Phases 0–2 are assembled. Evaluate
   progression, faction behaviors interacting over campaign timescales, quest sequencing, economy,
   performance/save growth, and the complete player experience.
2. **Polish pass:** fix and tune what the Alpha playthrough exposes; finish public-facing presentation,
   compatibility, documentation, and required placeholder replacements without reopening settled
   systems absent evidence.
3. **Beta release:** publish Exiles as a WIP for broader player testing. Revisit provisional tuning
   and compatibility decisions when public feedback supplies evidence.

## Design Notes

- Exiles currently uses mostly direct custom IDs. This is okay.
- Vanilla Starsector often uses indirect references through tags, role keys, constants, faction lists, and loader tables.
- Do not migrate to vanilla-style indirection just for consistency.
- Revisit that choice if fleet composition, shared faction content, or compatibility work becomes difficult.

## Good Next Auto-Generated Artifacts

- A build/run note for IntelliJ and Starsector loading. — **STUBBED 2026-06-18:** `docs/EXILES_BUILD_NOTES.md`.
- A short README with the gameplay pitch, dependencies, and current scope.
- A small automated ID/reference smoke check for faction braces, custom descriptions, start-location strings, and economy file inclusion.

## Docs To Read First

- `CLAUDE.md`
- `docs/EXILES_ECONOMY_MAP.md` (economy mechanics + the refugee-pressure arc; read before any economy work)
- `docs/EXILES_PROJECT_INDEX.md`
- `docs/EXILES_ID_REGISTRY.md`
- `docs/EXILES_FACTION_RELATIONSHIP_MATRIX.md`
- `docs/EXILES_VANILLA_REFERENCE_TABLE.md`
- `docs/STARSECTOR_VANILLA_REFERENCE.md`
