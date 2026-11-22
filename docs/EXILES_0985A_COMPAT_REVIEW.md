# Exiles — Starsector 0.98.5a compatibility review

> **Status (2026-11-18): RC8 RELEASED, NOT YET INSTALLED. RC5-to-RC7 NOW DIFFED (§8.2).** An RC7
> baseline was taken before the update (`<install>\logs-archive\baseline_0.98.5a-RC7`), so RC8 can
> be diffed against it once installed. The RC8 notes name nothing Exiles depends on; two items
> touch things Exiles also touches (Volturn, story-critical markets) and are listed in §8.2. The
> campaign has since run to Dec 31, c207 across two launches and four reloads with zero Exiles
> errors, which also covers "loading a mid-campaign save" below.
>
> **Earlier status (2026-11-16): BOOTED AND PLAYED ON 0.98.5a-RC7; FIRST LONG RUN CLEAN.** Record in §8.1.
> Sean played one new Exile-start campaign for 1 h 54 min real time (Mar 2 to Sep 27, c206, about
> seven game months, no Nexerelin). The log has **zero Exiles errors and no exceptions** in the
> campaign; every director ran real operations; the starting debt billed five installments with no
> arrears. **Still untested on 0.98.5a:** loading a mid-campaign save (the only load was the day-0
> autosave), Heg-League blockade and squeeze, Black Fleet assault, Omicron incursion, the Midan
> strike, and anything past month seven. §4.2 and §4.4 still need the multi-year run.
> **Not reviewed:** this review compared 0.98a-RC8 with 0.98.5a-**RC5**. The install is now RC7
> and the RC5-to-RC7 changes have not been diffed; the clean run is the only evidence for them.
>
> **Earlier status (2026-11-12, evening): required edits applied.** The review itself was
> a read-only pass (commit 9048c65). Afterwards, on Sean's go-ahead, these were applied in the
> `Starsector_098.5a` copy: §2.1 (game version), §2.2 (AI-core rule), §2.4 (tool paths and
> `CLAUDE.md`), the §3.1 ruling (Exile start hidden in Survival Mode), and the Persean League id
> fix from §8. Janino check on the 0.98.5a install: `BlackFleetRaidManager` and `ExilesModPlugin`
> both EXIT=0. First boot followed on 2026-11-15 (§8.1).
>
> **§2.3 (relations for `diktat_achilles`):** held on 2026-11-12, then **ruled and applied
> 2026-11-14** (Midan and Black Fleet hostile, the other three neutral). **No change, by ruling:** §3.5 (Omicron bounty flag), pending his own test of
> fighting Omicron while holding a commission.
>
> **Rulings (Sean, 2026-11-12):** recorded under each item in §3. Survival Mode is **not
> supported** for now; the start package gets **no** Domain-grade Parts; automated-ship recovery
> is accepted as is.
>
> **Added 2026-11-14:** §3.3.1, how vanilla stocks Domain-grade Parts at markets and which
> settings control it, checked against Sean's first 0.98.5a save; and §3.6.1, the markets the
> Sindrian questline uses and what its ending can do to them (spoilers).
>
> **Versions compared:** stock 0.98a-RC8 (`D:\Fractal Softworks\Starsector_Vanilla`) against
> 0.98.5a-RC5, confirmed from the engine's own version class.
>
> **Paths (Sean, 2026-11-12):** the 0.98.5a install was renamed after this review ran and is now
> **`D:\Fractal Softworks\Starsector_098.5a`**, the default going forward (it was
> `Starsector_098a` when the checks were made). The mod was copied to its `mods\Exiles` folder.
> `D:\Fractal Softworks\Starsector` is still live but deprecated.
>
> **Bottom line:** Exiles compiles and loads its scripts on 0.98.5a with **zero code errors**.
> There are **three small required edits** (§2), **six decisions that are Sean's to make** (§3),
> and a list of vanilla behaviour changes that need play-testing rather than code (§4).
> At the time of the review the game had never been launched; it has been since (§8.1).

---

## 1. What was checked, and how

| Check | Result |
|---|---|
| All 156 Exiles Java files compiled with `javac` against the old jars and the new jars | The two compiler logs are **byte-identical**. Nothing Exiles calls was removed, renamed or re-typed. |
| All 152 loose-script classes loaded through the new install's own Janino compiler and Java runtime (what the game does at start) | **152 of 152 compile.** A deliberately missing class was reported as a failure, so the check does report failures. |
| The four precompiled rule-command sources compiled against old and new | Output classes are **byte-identical**, so the shipped `jars/ExilesRuleCommands.jar` is still valid. No rebuild is required. |
| Accidental overrides: does any Exiles method now collide with a method vanilla newly added to a parent class? | 1,367 Exiles methods mapped under both versions; the maps are **identical**. (Anonymous inner classes were not covered.) |
| Vanilla API source: 1,947 files old, 2,020 new; 322 changed, 37 added, 0 removed | 47 of the changed files are ones Exiles imports. All 47 diffs were read; findings are in §2 to §6. |
| Vanilla `data/`: 155 files changed, 16 added, 2 removed | The 2 removed files (`kite_Stock.variant`, `ziggurat_HF.variant`) are not referenced by Exiles. No id was removed from any vanilla CSV. |
| Vanilla `graphics/` and `sounds/` | 0 files removed. The one Exiles file at a vanilla path (`graphics/illustrations/gilead.jpg`) still overrides an unchanged vanilla file. |
| Every settings key, sound id and music id Exiles reads | All still exist. |
| Obfuscated engine jar (scanned in memory, 2,845 classes) | The `$cfai_*` fleet-AI flag set is unchanged apart from one addition (`$cfai_makeFleePlayer`). |
| Runtime | Same Java (Azul 17.0.10), same Janino and commons-compiler jars (identical checksums), same bundled libraries. |

The raid pipeline every director sits on (`RaidIntel`, the raid stages, `BlockadeFGI`,
`FGBlockadeAction`, `FleetFactoryV3`, `BaseMarketConditionPlugin`) is **unchanged** in the new
version. That is the main reason the code result is clean.

---

## 2. Required changes

Small and mechanical. None needs a design ruling except 2.3, which has an obvious default.

### 2.1 `mod_info.json` game version
`"gameVersion": "0.98a-RC8"` should become `"0.98.5a-RC5"`.
The decompiled launcher treats both as major version `0.98`, so the old value is not a hard block,
but the launcher will flag the mismatch. (Inferred from the decompiled version check; not boot-tested.)

> **APPLIED 2026-11-12.**

### 2.2 AI-core turn-in rule is missing a condition vanilla just added
File: `data/campaign/rules.csv`, row `aiCores_turnInOption`.

Exiles overrides this vanilla rule so Omicron cores can be turned in. Vanilla's copy gained a new
condition, `!$tag:dont_take_ai_cores`, which stops one new story character from offering to take AI
cores. Our override does not have it, so with Exiles enabled that character would offer the option.

Fix: add `!$tag:dont_take_ai_cores` to the conditions of our row. CSV only; no jar rebuild.
The other four vanilla rules we override (`aiCores_disableTurnInOption`,
`aiCores_turnInOptionSelected`, `marketHostileSel`, `marketHostileEngageSel`) are unchanged in vanilla.

> **APPLIED 2026-11-12.** The row now reads `$isPerson` / `ExileAICores personCanAcceptCores` /
> `!$tag:dont_take_ai_cores`. Parsed back from the file to confirm; not yet seen in game.

### 2.3 No relations set for the new faction `diktat_achilles`
File: `data/scripts/world/ExilesSectorGen.java` (relations block, lines 57 to 153).

Vanilla added a faction, "Sindrian Reformers" (`diktat_achilles`), used by the new Diktat storyline.
`ExilesSectorGen` sets relations faction by faction, with explicit lines for the Diktat and the
Lion's Guard. The new faction gets no line, so all five Exiles factions default to **neutral** with
it. For Midan that contradicts the fixed constraint that Midan stays hostile to established powers.

Recommended default: mirror each Exiles faction's existing Diktat value. This only matters in
campaigns where the player pushes the Diktat storyline far enough for the faction to appear.

> **HELD (Sean, 2026-11-12).** He knows nothing about this faction yet and will play the questline
> before ruling. Until then the five Exiles factions are neutral toward it. Not applied.
>
> **RULED AND APPLIED (Sean, 2026-11-14),** after the code reading in §3.6.1 showed the faction is
> a rival claimant to the Diktat that exists only during the standoff. **Midan and Black Fleet:
> HOSTILE. Ashen Crusade, Omicron and Refugees: NEUTRAL, written explicitly.** This replaces the
> "mirror the Diktat value" default above. Reason for the two hostile lines: both factions are
> already hostile to the Diktat, so being hostile to both sides keeps Exiles from tilting the
> standoff. The player's own standing with the Reformers is left to vanilla. Janino check:
> `ExilesSectorGen` EXIT=0. Takes effect in new campaigns only (relations are set at new game).

### 2.4 Paths and version labels (housekeeping, do with the migration)
- `tools/janino-check.ps1` and the `tools/*Check.ps1` scripts hard-code
  `D:\Fractal Softworks\Starsector\...`. Development has moved to
  `D:\Fractal Softworks\Starsector_098.5a`, so **these paths now need updating**; until then the
  scripts check against the deprecated 0.98a install. (`build-rule-commands.ps1` uses a relative
  path and is fine.)
- `CLAUDE.md` header ("Starsector 0.98a-RC8") and the §7 vanilla reference path.
- Two stale lines found in passing: `CLAUDE.md` says the rule-command jar holds two classes; it
  holds four (`ExileAICores`, `ExileOmicronStationDefense`, `ExileAddStartingScript`,
  `ExileStartingFines`).

> **APPLIED 2026-11-12.** `janino-check.ps1` and `exiles-log-report.ps1` now derive every path from
> their own location, so each copy of the mod checks against the install it sits in. The other tool
> scripts already did. `CLAUDE.md` header, reference-install path and jar class list corrected.
> Consequence: log reports from the new install are written to
> `Starsector_098.5a\logs-archive` (created on first use); earlier reports stay in the old install.

---

## 3. Decisions for Sean

Each is a design question the update raises. Recommendations are **assistant suggestions**, not canon.

### 3.1 Survival Mode and the Exile start — the important one
Vanilla added "Survival Mode" (called *extended mode* in the code). It is chosen at new-game
creation and changes the economy across the board: crew salaries tripled, ship prices five to eight
times higher, far fewer ships and weapons for sale or recoverable, tariffs at 40%, colony growth
halved, and **forced retirement after 25 to 30 cycles** (which ends the campaign).

What happens today if a player picks Survival Mode and then the Exile start:
- The Exile start option **is offered**. Vanilla hid its own starts behind `!$extendedMode`; our
  rule `ngcExileStart` has no such condition.
- Our forked starting script (`src/data/scripts/rulecmd/ExileAddStartingScript.java`) does not do
  what vanilla's now does for this mode. In particular it never sets the sector flag
  `$extendedMode`, which vanilla rules read in two places (storage at abandoned stations in the
  Core, and one Diktat-story reward).
- The engine-side switch appears to be set independently of the starting script (not verified:
  that code is obfuscated). If so, the player gets Survival Mode pricing and retirement with a
  start that was never tuned for it, plus a missing flag. A half-configured hybrid.

Options:
1. **Block it for now (recommended).** Add `!$extendedMode` to `ngcExileStart`, and say so in the
   setup docs. This follows the setup-transparency ruling of 2026-09-12: disclose it, do not
   silently substitute.
2. **Support it.** Set the flag in our starting script and re-tune the Exile start package, the
   starting debt and the pacing of every director for Survival economics. This is real design work:
   retirement at 25 to 30 cycles alone collides with a long authored campaign.
3. Leave it as is. Not recommended.

Survival Mode's premise overlaps pillar 2 ("The Player Starts Behind") closely, so option 2 may be
worth a proper look later. It is not a quick port.

Unknown until Nexerelin updates: how Nex's own new-game screen handles the mode, and therefore how
`ExilesNexStart` behaves.

> **RULED (Sean, 2026-11-12): option 1.** Extended Mode is not currently supported. Hide the Exile
> start in that mode and disclose it. Sean notes that much of what he was building into Exiles is
> similar in intent to Extended Mode; that overlap is context for a later look, not a commitment.
>
> **APPLIED 2026-11-12.** `ngcExileStart` in `data/campaign/rules.csv` now carries the condition
> `!$extendedMode`, the same one vanilla put on its own starts. Recorded in `CLAUDE.md` §1 and in
> the disclosure checklist of `docs/EXILES_NEXERELIN_COMPAT.md`.
> **Still owed under the setup-transparency ruling:** nothing in-game tells the player why the
> option is missing. *Suggestion (assistant):* show the option greyed out in Survival Mode with a
> tooltip saying it is unsupported, using vanilla's `SetEnabled` and `SetTooltip` rule commands.
> Not built; untested in the new-game dialog.
>
> **BUILT 2026-11-14 (Sean's wording), NOT YET SEEN IN GAME.** New rule `ngcExileStartExt` in
> `data/campaign/rules.csv`, shown only when `$extendedMode` is set. It adds a disabled option,
> "Exiles of the Shattered Sector (compatibility pending)", with the tooltip "Exiles of the
> Shattered Sector was in development prior to the announcement of Survival Mode. Compatibility
> pending." followed, on its own line, by "The Exile start is available in a standard campaign."
> (assistant suggestion, approved by Sean the same day). Sean's sentence is 118 characters and vanilla's longest start option is 91, so the
> full sentence went in the tooltip and the short form on the button. Same pattern as vanilla's
> own greyed-out `ngcSkipTutorialNoSkip`. The Nexerelin custom-start path stays on hold (Sean:
> Nexerelin is still on 0.98a and that path was never fully implemented).

### 3.2 Crew pay changed, so the starting debt needs a balance check
- Old: every crew member cost 10 credits a month.
- New: each ship's working crew is paid a salary set by the ship (its monthly supply cost times
  50 for frigates and destroyers, 75 for cruisers, 150 for capitals), and spare crew cost 5 each.
- Officers cost 10% of salary while inactive or while the fleet is in a populated system.
- Commission pay is now 15,000 a month plus 3,500 per active officer (it was 20,000 plus 5,000 per
  player level), and the commission bounty setting rose from 300 to 500. (These are the values in
  the shipped `settings.json`; the release notes quote 10,000 and 2,500.)

For the Exile start (two frigates, 100 crew) the monthly payroll comes out at about 775 credits
against 1,000 before, roughly a fifth lower (computed from the formula; not measured in play).
Large fleets cost much more than before: a Paragon's crew is 9,000 a month.

The starting debt (1.8 million over 48 installments) is unchanged in code and still bills through
the same monthly-report mechanism. What changed is the income and expense picture around it.
**Decision:** re-measure before retuning (instrument first), then decide whether the defaults move.

Related, smaller: the mod's own hulls (Clodhopper, Condor-II, Calceus, the exile Hound and
Shepherd) have no value in the three new `ship_data.csv` columns (`parts/dmod`, `salary`,
`salMult`), so they get vanilla's defaults. Vanilla gave some civilian hulls a reduced salary
multiplier. *Suggestion:* decide whether the civilian Exiles hulls should get the same.

> **NOTED (Sean, 2026-11-12): no action now.** The debt is due a refactor anyway; fold the new
> payroll picture into that work.

### 3.3 Domain-grade Parts and the "pair of flying derelicts"
Vanilla replaced the dockyard "Restore" option with repairing d-mods one at a time using a new
commodity, **Domain-grade Parts** (id `parts`, base price 10,000 credits each; a frigate needs at
least 1 per d-mod). Parts drop from most fleets that drop supplies and are sold at every market that
stocks supplies (§3.3.1; this line first said "larger colonies", which came from the release notes
and was wrong). The Hull Restoration skill no longer repairs d-mods automatically.

The Exile start hands the player two heavily damaged ships. Fixing them now runs through parts.
- **Decision:** does the start package include any parts, or is having none the point?
- *Suggestion:* parts are a natural fit for Independent Salvage (`exile_salvage_yards`), Refugee
  Salvage Expeditions and Mysterious Stockpiles. Whether any of them should produce, sell or
  consume parts is a design call. Nothing breaks if they ignore parts.

> **RULED (Sean, 2026-11-12): the start package includes no parts.** Whether the salvage systems
> deal in parts stays open. ("Independent Salvage" is the in-game name of the refugee industry
> `exile_salvage_yards`, live on Corvus and Mairaath.)
>
> **Sean, later the same day:** he likes the Independent Salvage idea either way and will play the
> new version first to get a feel for the lore. The handling depends on what parts turn out to be:
> well-known goods that are on the market but expensive, or largely unknown items gained mainly
> from salvage. **Open; waits on his playthrough.**

#### 3.3.1 How vanilla puts parts on the market (checked 2026-11-14)
Sean's report after a partial playthrough: parts are sold in small quantities at every vanilla
market, 10,000 credits to buy, 250 to sell. The code agrees on all three points.

**Parts are not part of the economy.** The commodity is tagged `nonecon` in `commodities.csv`. No
industry produces or demands it, no trade fleet carries it, and it cannot be in shortage. There is
no "parts" line in a market's commodity list.

**Stock rides on Supplies.** `BaseSubmarketPlugin.addAndRemoveStockpiledResources()` has a block
commented "deal with Domain-grade Parts" that runs whenever it processes the Supplies commodity.
For each submarket:

    parts cap = (that submarket's Supplies stock cap) x fraction
    fraction  = partsForSaleBaseSuppliesFraction
              + (production quality + 0.3) x partsForSaleBaseProductionFraction
                x min(1.5, Supplies production / 10)

- With the default settings (0.1 and 0.2), a market that produces no Supplies stocks parts at
  **10% of its Supplies cap**. Markets that produce Supplies, and those with better ship
  production quality (the same stat Orbital Works and nanoforges raise), stock more.
- Stock refills toward the cap over about 30 days and drains back down if it is above the cap.
- It is generated **when the player opens the market**, catching up for the time since the last
  visit. A market the player has never opened holds no parts.
- Submarkets that get parts: the Open Market (Supplies are legal everywhere in vanilla), the
  Military Market (Supplies carry the `military` tag), the Black Market, and a player colony's
  Local Resources. Storage does not. What the player pays to take parts out of Local Resources
  was not traced.

*Check against Sean's save (`save_Dixon_1542714902972023770`, normal mode):* 43 submarket cargos
hold parts, and every one also holds Supplies. 35 of them sit at 10% of the Supplies quantity
(8 to 58 parts). The other 8 run from 17% to 34% (114 to 897 parts) and are the big
Supplies producers. No cargo holds parts without Supplies.

**Switches and dials** (all in vanilla `data/config/settings.json`, all sector-wide):

| Setting | Default | Effect |
|---|---|---|
| `useDModRepairWithPartsInExtendedModeOnly` | `false` | The master switch. `true` turns the whole parts system off outside Survival Mode: markets stop stocking parts, parts are stripped from salvage loot, and Hull Restoration's automatic d-mod removal runs again. Whether the dockyard "Restore" option also returns was not checked. Read once at game start. |
| `partsForSaleBaseSuppliesFraction` | 0.1 | The flat share of Supplies stock. This is the "every market has a few" number. |
| `partsForSaleBaseProductionFraction` | 0.2 | The extra share for Supplies producers. Setting both fractions to 0 removes parts from sale while leaving repairs and salvage drops working. |
| `ext_partsForSaleFraction` | 2 | Survival Mode only: multiplies the fraction (there is less Supplies stock in that mode). |
| `ext_partsPriceMult` | 2 | Survival Mode only: multiplies the base price, so 20,000 to buy. |

**There is no per-market, per-faction or per-submarket switch.** The stocking code checks no
market tag, memory flag or faction setting. The only per-market inputs are the market's Supplies
stock, its Supplies production and its production quality. One caveat on the master switch: if it
is turned on in a running campaign, parts already sitting in a submarket are not cleared.

**Price.** `commodities.csv` gives parts a base price of 10,000, price variability 0, and a new
column `sell price mult` of 0.025. The trade screen multiplies the sell price of any commodity by
that column (decompiled `campaign/ui/trade/E`), which gives 250. So the buy/sell gap is one number
in a data file, not a market effect. Tariffs were not traced.

**Other sources, for the lore question.** Salvage loot gains parts in proportion to the Supplies in
it (`partsPerUnitOfSupplies` 0.05, less from trade convoys and from wrecks heavy with d-mods), and
derelict ships, stations and caches have their own drop groups (`parts_10`, `parts_33`,
`parts_50`). So parts are both on every shop shelf and a routine salvage find.

**For Exiles:**
- Exiles has no custom submarket code, so every Exiles market with an open, military or black
  market stocks parts by the same rule. Nothing needs changing.
- Independent Salvage (`exile_salvage_yards`) produces metals, heavy machinery and domestic goods,
  not Supplies, so it does not raise the parts on sale at Corvus or Mairaath today.
- *Suggestions (assistant, not ruled):* if a salvage industry should mean more parts on sale, the
  levers vanilla already offers are the market's Supplies production and its production quality.
  A direct per-market amount would need an Exiles submarket class that overrides
  `getPartsGeneratedAsFractionOfSupplies()`. Simply adding parts to a market's cargo will not
  last: anything above the cap drains away at each visit.

> **RULED (Sean, 2026-11-14): no action.** Parts appear automatically in every market's store, so
> Independent Salvage does not need to deal in them. This closes the open question in §3.3.

### 3.4 Automated ships can now be recovered without the skill
Vanilla setting `allowAutomatedShipRecoveryWithoutSkill` is `true` by default: any player can
recover Remnant and Derelict hulls after a battle. The Automated Ships skill is still needed to
bring them to a useful combat readiness.

Omicron fleets are built from Remnant hulls, so Omicron wrecks become recoverable by everyone.
The Omicron tactical-core ledger (`docs/EXILES_OMICRON_CORES.md`) was designed when the skill gated
the hulls themselves. **Decision:** accept this, or keep Omicron hulls unrecoverable. The global
switch can be turned off from our `settings.json`, but that would change vanilla behaviour for
Remnants too, which the compatibility priority argues against.

> **RULED (Sean, 2026-11-12): accept as is.** The hulls were always recoverable and usable with
> the skill and are still only useful with it; the practical change is that some players will hunt
> Radiant hulls from the start.

### 3.5 Should killing Omicron pay commission and system bounties?
Vanilla added a faction flag, `noBountyForDefeating`, and set it on Remnants and Derelicts: kills
no longer pay commission or system bounties. Omicron is Remnant-themed and does not have the flag,
so Omicron kills still pay. **Decision:** add the flag to `exileomicron.faction` or leave it.

*Suggestion, cosmetic:* vanilla also added dedicated Remnant encounter music
(`music_remnant_encounter_hostile`). Omicron currently uses the Tri-Tachyon tracks.

> **RESEARCHED (2026-11-12, at Sean's request): this was not a hostility bug.** Read from
> `FactionCommissionIntel.reportBattleOccurred` and `SystemBountyIntel.reportBattleOccurred`, old
> and new:
> - A commission has always paid for destroying any fleet whose faction is **hostile to the
>   commissioning faction**, anywhere in the Sector. The hostility test was there in 0.98a and is
>   unchanged. Remnants are hostile to every faction, so every commission paid for Remnant kills
>   out in the fringe. That was working as written; the new flag removes it as a balance choice.
> - System bounties pay inside the posted system for fleets hostile to that market's faction. The
>   new flag is checked first there too.
>
> **What that means for Omicron.** Omicron is hostile or vengeful to the Hegemony, Persean League,
> Luddic Church, Knights of Ludd, Luddic Path, independents, pirates and mercenaries, and friendly
> to Tri-Tachyon. So a Hegemony, League or Church commission pays for Omicron kills and a
> Tri-Tachyon commission does not. Unlike Remnants, Omicron comes into the Core and attacks those
> factions' worlds, so a faction paying for those kills fits the fiction.
> **Recommendation (assistant): leave the flag off.** One thing to weigh in the debt refactor:
> the commission bounty rate rose by two thirds in this update, so Omicron kills pay more than before.
> **RULED (Sean, 2026-11-12): go with the recommendation and hold off; no change for now.** He
> has not seen this in play because he rarely takes a commission, and wants to test fighting
> Omicron while holding one before deciding anything further.
>
> **Sean, 2026-11-14:** still to be tested, but no change is planned. "The default of hostile
> factions paying a bounty makes sense."

### 3.6 The Diktat storyline now exists in vanilla
Vanilla finished the Sindrian Diktat storyline (the notes call it more than half the release).
What it means for Exiles:
- Eleven markets are now protected as story-critical from game start: Sindria, Volturn, Umbra,
  Nortia, Cruor, Kazeron, Eochu Bres, Yesod, Orthrus, Nomios and Qaras. That works **with** the
  controlled-collapse priority (they cannot decivilize).
- Through the player's choices the Askonia worlds can change owner (League, Church, Hegemony,
  Tri-Tachyon, pirates, independents), a Diktat splinter faction can appear (§2.3), and, judging
  by a new "exploded" description entry and a planet-explosion script, Sindria itself can be
  destroyed. (Read from the data files; the storyline was not played.)
- Exiles touches the same places: the quiet Volturn food condition, Black Fleet's occasional Diktat
  targets, and Eochu Bres (the wrecked-mirror content in the Ashen war).

No conflict was found in code. This is a **lore** question: the storyline assumes vanilla's
history, and Exiles runs on an altered timeline where the Diktat stayed neutral in the
Hegemony–League crisis. **Decision (not urgent, Phase 2):** leave the vanilla storyline available
as is, or reconcile it with the Exiles timeline.

> **NOTED (Sean, 2026-11-12): no action; keep watching.** This is the reason Exiles avoided
> changing Askonia. Our only change there is the Volturn red tide, which is environmental.
> Confirmed in code: no Exiles script changes an Askonia market's owner, industries or people.

#### 3.6.1 Which markets the Sindrian questline uses (read from code, 2026-11-14)
Sean asked whether the markets involved can be read from `rules.csv` instead of playing the whole
chain. They can. **This section contains story spoilers.**

*Method:* the dialogue is in two files, `data/campaign/rules.csv` (981 rules new in 0.98.5a) and
the new `data/campaign/rules/askoniaCrisis2.csv` (6,113 rules). Every vanilla market id named in a
rule's conditions or script was counted, and the Java behind the missions was read
(`missions/askonia/*`, `intel/events/sd/*`, `rulecmd/SDR_CMD`). Nothing was played.

**The chain.** The Usurpers (`sdtu`) already existed in 0.98a. New: Seven Sons (`sd7s`), a third
mission (`sdse`, icon `heir_to_andrada`), a side thread with the smuggler K-O Jove (rule prefix
`sdsw`), and a timed "Askonia standoff" event that settles who rules the Diktat (`sdr`/`sdd`).

**Markets where dialogue happens or the player is sent:**

| Market | System, owner at start | Role | Exiles touches it? |
|---|---|---|---|
| Sindria | Askonia, Diktat | Usurpers; home of Macario, Hyder, Caden, Ram; centre of the standoff | No |
| Volturn | Askonia, Diktat | Usurpers ("meet Ram"); seized by the Reformers during the standoff | **Yes: red tide condition** |
| Umbra | Askonia, pirates | Usurpers; home of Imoinu Kato; standoff outcome | No |
| Cruor | Askonia, Diktat | Standoff outcome | No |
| Nortia | Askonia, independent | **Seven Sons starts here** (only while Nortia is independent; three contacts elsewhere offer another way in); bar meeting | No |
| Yesod | Zagan, League | Seven Sons search; Jove meeting; **the Reformer fleet launches from here** | **Yes: Heg-League action list and planetary blockade whitelist** |
| Orthrus | Samarra, independent | Seven Sons search (bar) | No |
| Nomios | Arcadia, independent | Seven Sons search (bar, cryosanctum) | No |
| Qaras | Yma, pirates | Seven Sons search (bar, covert operation); K-O Jove | No |
| Kanta's Den | Magec, pirates | Seven Sons (Doctor Cydonia) | No |
| Kazeron | Thule, League | Siyavong (moved here by the quest), Fortuna Kato, Horus Yaribay, Reynard Hannan | Yes: Orbital Works patch, Heg-League config |
| Eochu Bres | Hybrasil, Tri-Tachyon | Arroyo, Zunya Glamor-Rotanev | Yes: Ashen war, Omicron defence |
| Tibicena (Achaman Enterprise Station) | Magec, Tri-Tachyon | Faro Inesis | Yes: Ashen war, Omicron defence |
| Ragnar Complex | Valhalla, Hegemony | Orcus Rao | No |
| Chicomoztoc | Aztlan, Hegemony | Daud's office (one hand-in) | No |
| Salamanca | Yma, League | Jove side thread (bar) | Yes: refugee strain, Heg-League config |
| Eventide | Samarra, Hegemony | Jove side thread | Yes: strike target (the Eventide wound), Heg-League config |
| Ilm | Zagan, independent | Jove side thread | No |

Not markets: the Galatia Academy station (Seven Sons), and two asteroid bases the mission creates,
"Irkalla" in Duzahk and "58966 Elysium" in Hybrasil on Donn's orbit. Several hand-ins accept "any
sufficiently senior" Hegemony, League or Tri-Tachyon officer, so they have no fixed market.

**Hard requirements in the code:**
- `SDResolution.INVOLVED_MARKETS` lists nine markets: Yesod, Orthrus, Nomios, Qaras, Sindria,
  Volturn, Umbra, Nortia, Cruor. **The standoff refuses to begin if any one is missing or
  decivilized**, and all nine are made story-critical while it runs.
- Ownership is checked in a few rules: The Usurpers starts at any Diktat-owned market, the Ram
  meeting needs Volturn Diktat-owned, Seven Sons needs Nortia independent.
- Exiles does not change the owner of, or decivilize, any of the nine. Its only ownership changes
  are the Corvus station, player colonies and Omicron's own sites.

**What the standoff does (heavy spoilers):**
- At the start: the Diktat is taken out of vanilla's random "hostilities", its relations are set
  to fixed values (Tri-Tachyon +0.25, League -0.25), Sindria's own fleet spawning is switched off,
  and scripted fleets take over Askonia.
- `diktat_achilles` ("Sindrian Reformers") becomes visible. When its fleet reaches Askonia it takes
  **Volturn** and turns fully hostile to the Diktat. Vanilla sets its relations to pirates and the
  Path at -0.5, Tri-Tachyon -0.25, League +0.25.
- At the end the Reformers are hidden again and their fleets fold back into the Diktat or the
  League. **So `diktat_achilles` is a temporary faction: it holds one market, Volturn, and only
  for the length of the standoff.** If its side wins, the ordinary Diktat faction takes its logo
  and description.
- Possible owners afterwards, depending on the player's choices: Sindria (Diktat, League,
  independent, or destroyed if the player earlier gave the Diktat the planet-killer); Volturn
  (Diktat, League, Church, independent); Cruor (Diktat, pirates, League, or decivilized by the
  script); Nortia (independent, League, Diktat, Tri-Tachyon, pirates, Hegemony); Umbra (pirates,
  League, Diktat, Tri-Tachyon as a free port, Hegemony, independent). The Diktat can end up with
  no markets at all.

**For Exiles:**
- No code conflict found. The Exiles-touched markets on the list are used as places to talk to
  someone, apart from Volturn and Yesod.
- **Volturn:** the red tide is a market condition, so it should stay through an owner change.
  Not seen in play.
- **Yesod:** it is on Exiles' Heg-League action list and blockade whitelist, and it is where the
  Reformer fleet is built. A Hegemony squeeze on Yesod during the standoff is an untested overlap.
  Exiles does not read vanilla's story-critical flag; it relies on its own unrest guard.
- **Black Fleet** can raid Nortia, Orthrus, Nomios (independent), Yesod (League, since the id
  fix) and the Diktat worlds. Exiles' unrest guard is there to stop raids decivilizing a market,
  so they should not block the standoff.
  While the Reformers hold Volturn it is off Black Fleet's target list.
- **§2.3 (relations for `diktat_achilles`)** can be ruled without playing to the end: the faction
  only matters for how Exiles fleets treat Reformer fleets and Volturn during the standoff.
- *Testing shortcut (suggestion):* the rules include dev-mode options (rule ids starting `DEV`,
  such as `dev_startSDSE`) that jump into the later missions. Not tried.

---

## 4. Vanilla behaviour changes to play-test

No code change is known to be needed for these. They change how existing Exiles systems will feel.

| # | What changed in vanilla | Where it touches Exiles | Expected effect |
|---|---|---|---|
| 4.1 | The two "send this fleet home" helpers now also mark the fleet busy, clear its military-response flags and strip its patrol or trade script. | `AshenStandDownScript.java:143`, `ExileReturnOrderWatchdog.java:89` and `:91`, `OmicronTriTachyonResponseScript.java:189` | Probably helpful: fleets ordered home are less likely to be pulled into a fight. All three sites send fleets home to despawn, so nothing should be re-tasked afterwards. Confirm in a run that stand-downs still complete. |
| 4.2 | Bug fix: destroyed trade fleets now apply a real accessibility penalty to both ends of the route. Before, most did not. | Black Fleet commerce raids, Heg-League interdiction and blockades | Trade-fleet kills hurt Core markets more than they did. This pushes toward shortages and needs watching against the controlled-collapse priority over a multi-year run. |
| 4.3 | Vanilla patrols: no new patrol for 2 days after one returns; a new patrol ignores other fleets and has zero sensor range for its first day. | Every raid, strike and assault against a vanilla market | Slightly softer and slower local defence. |
| 4.4 | Punitive expeditions against player colonies gained a flat 150 fleet points. | The player's two starting colonies | Early expeditions are much more dangerous. Check against "two fragile colonies". |
| 4.5 | Generic colony-crisis fleets in player systems capped at 7 (was 11). | Player colonies | Less vanilla pressure; Exiles pressure is a larger share of the total. |
| 4.6 | Bounties: 9 to 12 personal bounties at once (was 3 to 5), 4 system bounties (was 3), larger payouts farther from the Core. | Player income against the debt | More money available early. Part of the §3.2 measurement. |
| 4.7 | Officers: base limit 9 (was 8); activating an officer needs a colony with a spaceport; many more officers for hire. | The two starting officers | No conflict expected. Confirm the starting officers arrive active. |
| 4.8 | Ship balance: Champion +20 ordnance points and better flux, Conquest faster, Hammerhead more flux; Hurricane MIRV 5 points cheaper, IR Pulse Laser 1 point cheaper. | Midan variants (`data/variants/exiles_midan/`), `wolf_exile_Starting` | All our variants stay legal. Nothing we use became more expensive. The update left unspent points on Champion (20), Conquest Ajax (5), Gryphon (5), Medusa (2), Sunder (2) and one more on the starting Wolf. The full count for all 18 variants, including points that were already unspent, is in §4.8.1. Sean will spend them by hand in dev mode. |
| 4.9 | Vanilla variants we lock by name still exist with updated loadouts (`champion_Strike`, `champion_DEMEscort`, the Vanguard variants, `kite_Support`). | Faction `variantOverrides` | None expected. |
| 4.10 | The parent class of our scrap-preparation step changed its d-mod and readiness arithmetic. | `ExileRefugeeSalvageRecovery.ExileScrapPreparation` | Inherited automatically. Retest one refugee salvage recovery. |
| 4.11 | Player faction portraits: `standard_*` lists are now used for the player's officers; new `player_*` lists are used at character creation. | Not Exiles. It affects portrait mods that add to the player faction, including *Faces in the Void*. | Their portraits would show up on hired officers but not at character creation until those mods add `player_*` lists. |

#### 4.8.1 Unspent ordnance points on every Exiles variant (counted 2026-11-14)
All 18 variant files under `data/variants/` were costed against both game versions (hull budget
minus weapons, wings, hullmods, vents and capacitors; built-in and s-mods free; large ballistic
weapons 10 points cheaper on hulls with the built-in Heavy Ballistics Integration, which are the
Conquest, Onslaught and Invictus). The same calculator gives exactly zero for 377 of vanilla's own
424 costed variants and never goes over budget, which is the check that it adds up correctly.

| Variant | Hull | Budget | Unspent on 0.98a | Unspent on 0.98.5a |
|---|---|---|---|---|
| `exiles_midan_champion_assault` | Champion | 185 | 0 | **20** |
| `exiles_midan_gryphon_standard` | Gryphon | 140 | 0 | **5** |
| `exiles_midan_medusa_attack` | Medusa (Exiles skin) | 100 | 0 | **2** |
| `exiles_midan_sunder_assault` | Sunder | 105 | 0 | **2** |
| `wolf_exile_Starting` | Wolf (Exiles skin) | 60 | 4 | **5** |
| `hound_exile_Starting` | Hound (Exiles skin) | 38 | 2 | **2** |
| `exile_tarsus_2_Standard` | Tarsus II | 40 | 3 | **3** |
| `HaT_condor2_Standard` | Condor-II | 85 | 37 | **37** |
| `exiles_midan_conquest_ajax` | Conquest | 315 | 0 | **5** |

The other nine are exactly on budget: the Midan Conquest Standard, Buffalo, Eagle, Falcon,
Hammerhead, Omen and Phaeton, the Clodhopper, and the starting Shepherd.

- **Nine variants have unspent points.** Five come from the update (Champion, Conquest Ajax,
  Gryphon, Medusa, Sunder) plus one point on the Wolf. The Hound, Tarsus II, Condor-II and four of
  the Wolf's five were already unspent before it.
- **Correction, same day.** The first version of this table showed the two Conquests as over
  budget and guessed that the game trims vents and capacitors on load. That was wrong. Sean
  pointed out the Conquest's built-in hullmod: Heavy Ballistics Integration (`hbi`) takes 10
  points off each large ballistic weapon (`HeavyBallisticsIntegration.COST_REDUCTION`). With it
  counted, no vanilla or Exiles variant is over budget, and the figures in the first draft of
  §4.8 (Conquest Ajax 5) were right. Only the Wolf differs from that draft: 5 unspent in total,
  of which the update added 1.
- Whether the Condor-II's 37 and the starting ships' few points are deliberate (weak refugee
  hulls, a hard start) is Sean's call.

---

## 5. Exiles copies of vanilla classes that no longer match vanilla

These were written by copying a vanilla class. Vanilla's original moved on; the copies did not.
All still work. Bringing them level is optional.

| Exiles class | Copied from | What the copy lacks |
|---|---|---|
| `ExileAshenMilitaryBase`, `ExileKoLGarrison` | `MilitaryBase` | The 2-day pause after a patrol returns, and the 1-day blind start for new patrols (ours use the old 0.3-day value at lines 521 and 357). Higher officer-availability values (ours 0.1/0.2/0.3, vanilla now 0.2/0.3/0.4). The player-only Personnel Officer does not apply to NPC industries. Result: Ashen patrols respawn a little faster and react a little sooner than vanilla patrols. That may suit the faction; Sean's call. **2026-11-14 (Sean): the 2-day pause after a patrol returns is now in both classes** (four lines each, copied from vanilla and reading vanilla's own constant; Janino EXIT=0; not yet seen in game). The 1-day blind start and the officer values were not asked for and are unchanged. |
| `ExileAddStartingScript` | `NGCAddStandardStartingScript` | All Survival Mode handling (§3.1). Vanilla also now trims crew and fuel that exceed capacity. |

---

## 6. New vanilla features worth knowing about

All **assistant suggestions**. None is needed for compatibility.

- **Multiple rules files.** `settings.json` gained a `"rules"` list naming several rules CSVs.
  If mods can add to it (not verified), Phase-2 quests could each get their own file instead of
  growing one `rules.csv`. Worth testing before the quest scaffold is built.
- **`FactionHostilityManager.setExcluded(factionId, true)`.** Vanilla can now be told to leave a
  faction out of its random hostilities. `ExileHegLeagueNexSupport` currently ends vanilla's
  Hegemony–League hostility intel after the fact; excluding the pair up front may be cleaner.
- **`$suspendMilitaryResponse`** (a star-system memory flag). Switches off patrol reinforcement
  for a whole system. Possibly useful for the squeeze that fights the station.
- **`BaseIndustry.setNameOverride` / `setImageOverride`.** A vanilla industry can be renamed and
  re-iconed without a custom class. Some Ashen industry classes might reduce to this.
- **`IndustryApplyListener`, `IndustryMaxDeficitModifierListener`.** Hooks around industry
  application and shortage caps. Relevant to the "fuel shortage must not throttle output" rule in
  `ExileSalvageYards`, which is solved in-class today.
- **`RouteManager`**: route segments now expose elapsed and maximum days, and the route list is readable.
- **Tags** `star_hidden_in_planet_list` and `noColonization`: relevant to the hidden-system work.
- **`RemoveOption`** rule command now works; `$Name` is a new text token.
- Vanilla's rank table now has an id literally named `exile` ("Exile"). Exiles does not define one,
  so there is no collision; it is available as flavour for the player.

---

## 7. Verified unaffected

- **Loose-script syntax.** Janino is the same build; every conservative-Java workaround stays as is.
- **CSV files with old headers.** `data/campaign/commodities.csv` lacks vanilla's new
  `sell price mult` column and `data/hulls/ship_data.csv` lacks `parts/dmod`, `salary`, `salMult`.
  The decompiled loaders test for each column before reading it, so the old headers load cleanly.
- **The vanilla market patches** (Nachiketa, Gilead, Asher, Corvus and the rest). Their economy
  files and system scripts are unchanged. Vanilla's only economy edit adds "hot" to Cibola and
  "cold" to Madeira. `Askonia.java` changed only by naming two objects and an illustration.
- **Custom industries and conditions.** No vanilla industry id, condition id or column changed.
- **Starting debt and Emergency Measures billing.** Both add their own lines to the monthly report
  through a mechanism vanilla did not alter.
- **Omicron core drops.** The `drop_prob_officer_*` path is unchanged outside Survival Mode.
- **Hull files.** `wolf`, `shepherd`, `conquest`, `falcon`, `gryphon`, `hammerhead` changed only
  by one-pixel geometry adjustments. No weapon slot our variants use changed type.
- **Engine traps recorded in memory** (condition plugins stripped on save, station strength zero
  after load). Our fixes are defensive and unaffected. Vanilla quietly fixed one instance of the
  first trap in its own code (`CommRelayCondition` now declares itself non-transient).

---

## 8. Not covered

- **The game was not launched** during the review. Compiling proves the scripts load, not that a
  campaign generates and runs. It has been launched and played since: see §8.1.
- **RC5 to RC7.** The review compared against 0.98.5a-RC5. The install moved to RC7 by
  2026-11-16 and those changes were not diffed. **Diffed 2026-11-18: §8.2.**
- **Nexerelin and the library mods** (LazyLib, MagicLib, LunaLib, Console Commands) still declare
  0.98a and have not been updated by their authors. `ExilesNexStart` compiles against the
  *current* Nexerelin; whether it works against the next one cannot be known yet. Sean's usual
  test setup (Nexerelin, non-Exile start) is not available on 0.98.5a until they update.
- **Obfuscated engine behaviour** was spot-checked (fleet-AI flags, CSV loaders, launcher version
  check), not audited.
- **Combat-side changes** were not reviewed in depth. Exiles ships no hullmod, ship-system or
  weapon scripts.
- **Old saves.** Not assessed. A new game is required regardless.

Found in passing, unrelated to the update: six lines compile under Janino but not under `javac`
(`AshenInterdictionManager.java:1195`, `ExileHegLeagueBlockadeManager.java:481`,
`ExileBlackFleetReturnStage.java:83` to `:90`). They behave the same on both versions and matter
only for the planned jar build. `CLAUDE.md` §7 currently says the code is `javac`-compatible as is.

Also found in passing (2026-11-12), unrelated to the update and **not fixed**: the Black Fleet
target-faction list names the Persean League as `"persean_league"`, in both
`data/config/exiles/director_config.json:410` and the built-in default at
`BlackFleetRaidManager.java:648`. Vanilla's faction id is `"persean"` (`Factions.PERSEAN`; the
*file* is called `persean_league.faction`, the id inside it is `persean`). The list is compared
against each market's faction id, so as written **no League market can match it**. If Black Fleet
has never been seen picking a League world through this list, this is why. Needs Sean's
confirmation that the League is meant to be a target before it is changed.

> **FIXED 2026-11-12 (Sean: "I can't remember ever seeing the Black Fleet hit the League. I just
> assumed they were. Go ahead and make the change.").** Both places now say `"persean"`. The list
> is rebuilt from config on every load, so no save migration is involved.
> **Behaviour change to watch:** League worlds are now valid Black Fleet targets for the first
> time. The cap on simultaneous operations is unchanged, so the total number of raids does not
> rise; the same raids are spread over more candidate worlds. The League is already under pressure
> from the Hegemony–League war, so its markets are worth watching in the next long run.
> The same wrong id in `docs/EXILES_VANILLA_HOSTILITIES.md` was corrected.
> **Confirmed in play 2026-11-16:** Black Fleet extorted Suddene and ran a commerce raid on Tyle,
> its first League targets.

### 8.1 Runs on 0.98.5a

Both read with `tools/exiles-log-report.ps1`, then a hand scan of every ERROR, WARN and exception
line in the file.

**First boot, 2026-11-15 (RC5).** New Exile-start campaign "Alice", no Nexerelin, about 4.5 real
minutes and five game days in Xechas, one save. No exceptions; all directors logged their start-up
lines; the first refugee convoy launched.

**First long run, 2026-11-16 (RC7).** New Exile-start campaign "Anders", no Nexerelin, one session
of 1 h 54 min, Mar 2 to Sep 27, c206.

- **Errors:** zero from Exiles, no exceptions in the campaign. The only ERROR/WARN lines in the
  file are vanilla's own: `flare`, `module_hightech_decor` and `lightmortar_fighter` at every
  launch (they appear with Exiles disabled too), and one audio warning (AL error 40964) at the
  main menu before any campaign was loaded.
- **Heg-League:** two crisis cycles, both contained (tension 25 to 47). Strike landed on Suddene,
  interdiction completed at Galatia, relief convoy delivered in full. A strike on Yesod and an
  interdiction in Valhalla were in flight at the end.
- **Ashen:** reached Phase 3. Interdiction at Mayasura succeeded; five sabotage hits; four relief
  deliveries. Blockade and rehearsal at Tibicena in flight.
- **Black Fleet:** four commerce raids (one success, two failures, one in flight), five extortion
  dispatches (a sixth was in the stretch rolled back by the reload), one 15,000-credit bounty paid at Tyle.
- **Omicron:** two raids completed (Chalcedon, Epiphany), a third in flight.
- **Refugees:** three convoys, two salvage expeditions delivered with no error. That
  covers the recovery path of §4.10; the log does not show the d-mod and readiness figures.
- **Midan:** monthly measurements only; rung 1 is not due until day 365.
- **Starting debt:** five installments billed, arrears 0, 1,750,000 unbilled (read from the save;
  billing does not log).
- **Against 0.98a:** strength-per-fleet-point factors are identical to the old logs (Lundy Hold
  0.86, Sallee Anchorage 2.42, Omicron 1.0). Completed relief convoys report "freighters alive 0
  of 3" on both versions, so that line is not a regression.
- **Save size:** 11.7 to 11.9 MB across four saves.
- **Watch item:** Suddene took Black Fleet extortion in April and the Hegemony strike in June. One
  case; it is the League double-pressure flagged above.
- **Not exercised:** loading a mid-campaign save (the one load was the day-0 autosave, ten minutes
  in); Heg-League blockade and squeeze; Black Fleet assault; Omicron incursion; the Midan strike;
  the stand-down and return-order paths of §4.1 logged nothing either way. Not visible in a log:
  prologue, start package, starting officers (§4.7), Galatia completion.

### 8.2 RC5 to RC7 diff, and the RC8 hotfix (2026-11-18)

**Baselines.** `<install>\logs-archive\baseline_0.98.5a-RC5` and `…-RC7` each hold a SHA-1
manifest of `starsector-core\data` plus the top-level jars (1,434 files), a copy of vanilla
`rules.csv` and a copy of `starfarer.api.zip`. The RC7 one was taken on 2026-11-18, before
installing RC8, so that RC8 can be diffed against it. **Take a new baseline before every
vanilla update; the old files are gone once the installer runs.**

**RC5 to RC7 [VERIFIED by diff]**

- 11 of 1,434 files changed: `rules.csv`, `rules\askoniaCrisis2.csv`, `config\settings.json`,
  `hull_mods.csv`, `ship_systems.csv`, one starting variant, and the five code jars / zip.
- 14 API source files changed. **None is a class Exiles calls or mirrors**, with one near miss:
  `NGCAddStandardStartingScript` gained a supplies adjustment inside its extended-mode Hyperion
  start branch. `ExileAddStartingScript` mirrors that class, but not that branch; no edit needed.
- Unchanged: `HasslePlayerScript`, `WarSimScript`, `MilitaryResponseScript`, everything under
  `intel/raid`, `RouteManager`, `RouteFleetAssignmentAI`, `BaseAssignmentAI`,
  `RouteLocationCalculator`, and the rule commands the `exileHLStop*` rows call.
- `rules.csv`: seven changed lines, none in the hassle, enforcer, tithe or new-game rows the
  Exiles rows are modelled on.
- **Not diffable this way:** the three obfuscated jars changed, and no RC5 copy of
  `settings.json`, `hull_mods.csv` or `ship_systems.csv` was kept. Two long campaigns on RC7 are
  the evidence for those.
- Noted in passing: vanilla has a `missions/SecurityCodes` mission. Read it before designing the
  Authorization Codes bar quest (`EXILES_PASS8_PLAN.md` §1.1a).

**RC8 release notes, read against Exiles (not yet installed; nothing verified)**

- Almost all of it is story-mission dialogue fixes, survival-mode tuning and codex or music
  additions. No fleet AI, raid, route, economy or hassle change is listed.
- **Volturn** ("remaining in the wrong hands under certain conditions"; a shorter description
  after story events). Exiles puts its quiet red-tide food condition on Volturn at game start.
  Check after the update that the condition still applies in a new game.
- **Story-critical markets** (a denouement mission now marks its markets story critical). Only
  matters if a player runs that mission while an Exiles director is working the same markets.
- **Variant fixes** (goal variants, Lion's Guard ordnance points): vanilla variants only.
- **To do after installing RC8:** take nothing on trust from the notes. Diff against the RC7
  baseline, with these checked by name: `WarSimScript.getControlTimeoutId` (the interdiction
  objective keep-out replicates its id format), `HasslePlayerScript`, `MilitaryResponseScript`,
  `intel/raid/*`, `NGCAddStandardStartingScript`, and the vanilla rows the stop dialogue copies.
  **Done 2026-11-19: §8.3.**

### 8.3 RC7 to RC8 code check (2026-11-19, before the first RC8 launch)

Sean installed RC8 over the same folder. The mod folder, its git state (70149f2 plus the two
uncommitted test pins) and the saves were untouched. A `baseline_0.98.5a-RC8` was taken.

**What changed in vanilla [VERIFIED by diff against the RC7 baseline]**

- **39 of 1,434 files**, none added or removed: 14 variants, 15 skins (14 `_d` hulls and
  `falcon_p`), `rules.csv`, `rules\askoniaCrisis2.csv`, `config\settings.json`,
  `strings\descriptions.csv`, `scripts\world\systems\Galatia.java`, and the five code jars / zip.
- **19 API source files**, none added or removed. None is `HasslePlayerScript`, `WarSimScript`,
  `MilitaryResponseScript`, `TimeoutTracker`, anything under `intel/raid` or `intel/group`,
  `RouteManager`, `RouteFleetAssignmentAI`, `BaseAssignmentAI`, `RouteLocationCalculator`,
  `MemFlags`, `Tags`, `NGCAddStandardStartingScript`, or a rule command the stop dialogue calls.
  **So the keep-out's `factionId_objectiveId` timeout id is unchanged.**
- `MarketAPI.java` changed by an import reorder only. `CoreLifecyclePluginImpl` still registers
  `HasslePlayerScript`.
- **Vanilla `rules.csv`:** 32 rows changed, 16 added, 4 removed (12,068 to 12,083 rows). None is
  a hassle, enforcer, Knights or tithe row, a new-game row, or any of the FIVE vanilla ids Exiles
  deliberately overrides (`marketHostileSel`, `marketHostileEngageSel`, `aiCores_turnInOption`,
  `aiCores_disableTurnInOption`, `aiCores_turnInOptionSelected`), all of which are unchanged.
  The three vanilla row families the stop dialogue is modelled on are all still present.

**One change that touches Exiles targets, with no effect on Exiles code**

`CoreLifecyclePluginImpl.markStoryCriticalMarketsEtc()` now also marks **Qaras, Salamanca,
Eventide, Yesod and Ilm** story critical (mission id `sdsw`). In vanilla that flag is read by
`DecivTracker` (the market cannot decivilize) and by the player's bombardment dialogue. Exiles
never reads it, and its directors already work markets that were story critical before RC8
(Gilead, Chalcedon, Port Tse, Culann, Kazeron, Chicomoztoc). Eventide and Yesod are Heg-League
strike targets and Eventide is Midan's rung 3; Salamanca carries refugee strain. Net effect:
vanilla now refuses to decivilize five more markets, which is in line with the controlled-collapse
priority. **[TO VERIFY, live]** only that nothing in the Midan Eventide landing is blocked; it
applies its effects from Exiles code and does not go through vanilla's bombardment path.

**Other checks**

- The mod's `settings.json` overrides four vanilla keys; all four still exist. Vanilla ships
  `"enableStipend":true` again (the reinstall reset any hand edit); the mod's own override still
  sets it false.
- Exiles data references one changed skin, `falcon_p`, by hull id in a faction file; the id is
  unchanged. No changed variant is referenced.
- `Galatia.java` changed; the one Galatia id Exiles uses (`ancyra_market`) and the three entity
  ids checked (`ancyra`, `derinkuyu_station`, `galatia_academy`) are all still there.
- The mod's `descriptions.csv` overrides no vanilla row, so the Volturn description change
  cannot collide.
- The obfuscated engine jar still carries every flag string Exiles relies on (`$pursuePlayer`,
  `$keepPursuingPlayer`, `$core_fleetBusy`, `$doNotGetSidetracked`,
  `$cfai_makePreventDisengage`, `$cfai_makeAllowDisengage`, `$isTradeFleet`). Presence of the
  string, not proof of unchanged behaviour.

**Not diffable:** the obfuscated jars' logic; and `settings.json`, `descriptions.csv`,
`Galatia.java`, the skins and the variants line by line, because the RC7 baseline kept only
hashes for them. **From RC8 on the baseline should keep full copies of the small text trees**
(`data\config`, `data\campaign`, `data\strings`, `data\world`, `data\scripts`).

**Compile:** all 153 loose scripts were loaded through Janino against the RC8 jars. **151
compile (EXIT=0).** The two that do not are `ExileHegLeagueNexPin` and `ExilesNexStart`, each
with "a class `exerelin.…` could not be found": they are the Nexerelin-only classes, and
Nexerelin is not installed in this folder, so the check has no Nexerelin jar to compile against.
That is the same on RC7 and is not an RC8 effect; neither class is reachable at startup without
Nexerelin (`ExilesModPlugin` itself compiles). They remain unverified against RC8 until
Nexerelin is updated and installed here.

**Not checked:** anything in a running game. The first RC8 launch and the first load of the
"Horatio" campaign are still to come.

### 8.4 Found in play on RC8 (2026-11-22): red NullPointerException talking to a Refugee Salvage Expedition — **FIXED, NOT YET RETESTED**

**Report (Sean).** Opening comms with a Refugee Salvage Expedition printed, in red in the
dialogue window: `Cannot invoke "…CampaignFleetAPI.getCargo()" because "this.fleet" is null`.
The conversation otherwise worked. New campaign on RC8, standard start, no Nexerelin; the run
log otherwise has zero Exiles errors.

**Cause [VERIFIED from the log's stack trace and vanilla source].** A vanilla defect that only a
modded scavenger can reach:

- 0.98.5a added the rule command `rulecmd.salvage.SellToFleet` (absent in 0.98a) and the
  comm-link row `csf_sellStuffOptOther`, whose conditions are `$entity.isScavenger` and
  `SellToFleet willBuyStuff`. It runs every time the options are populated for any fleet
  carrying `$isScavenger`.
- `SellToFleet.execute()` reads `$fleetBuysStuffRate`; if it is missing or zero it sets its
  `fleet` field to null, and the very next line calls `fleet.getCargo()`.
- Vanilla sets that rate on its own scavengers (`RuinsFleetRouteManager`) and support fleets
  (`SupportFleetSpawner`). `ExileRefugeeSalvageExpeditionManager` sets `$isScavenger` but did
  not set the rate.
- The rules engine catches the exception, prints it, and treats the condition as false, so
  nothing else breaks.

**This review missed it.** It compared classes Exiles calls; it did not look for NEW vanilla
dialogue rows keyed on fleet flags Exiles sets. That sweep has now been run (0.98a against
0.98.5a-RC8): 20 new or changed rows test such a flag, and `csf_sellStuffOptOther` is the only
one that both tests a flag Exiles sets and runs a command. The rest key on `$entity.supportFleet`,
`$fleetType == RESUPPLY / TASK_FORCE` or `$isMercenary`, none of which Exiles sets.

**Fix.** The manager now writes `$fleetBuysStuffRate` 0.25 (vanilla's scavenger value) on each
expedition. `$fleetBuysStuff` is deliberately not set, so `willBuyStuff` answers false: no error
and no new "sell cargo" option. Janino EXIT=0. Expeditions already in flight in an existing save
keep the error until they return, a matter of days.

**Design questions raised, not decided [Sean]**

- Sean doubts the logic of buying hyperspace charts from poor salvagers who never leave the
  Core. That option is vanilla's `scavBuyHTDataOpt`, also keyed on `$isScavenger`. It can be
  hidden on refugee fleets with no rule override by setting `$boughtHTData` on them, which is
  the flag the row itself tests. **RULED AND BUILT 2026-11-22 (Sean: "Yes, hide it"):** each
  expedition now carries `$boughtHTData`, with no expiry. Vanilla reads that flag only in that
  one row. Janino EXIT=0; not yet retested. Expeditions already in flight keep the option until
  they return.
- Whether refugee salvagers should buy the player's cargo (set `$fleetBuysStuff`). Vanilla's
  line for it, "Heading back to the core soon?", does not fit fleets that never leave it.

---

## 9. Suggested order of work

1. ~~Sean rules on §3.1 and §2.3.~~ Done: §3.1 ruled and applied 2026-11-12; §2.3 ruled and
   applied 2026-11-14.
2. ~~Apply the required edits.~~ Done 2026-11-12 (§2.1, §2.2, §2.4, §3.1, the League id fix).
3. ~~First boot on 0.98.5a without Nexerelin: new game with the Exile start in normal mode.~~ Done
   2026-11-15 and 2026-11-16 (§8.1): directors start up and the debt bills. The prologue, start
   package, starting officers and Galatia completion do not show in a log; Sean reported no
   problems after the 2026-11-16 session.
4. **Next:** load the mid-campaign "Anders" save (first real load with operations in flight), then
   one multi-year run (about one real hour per game year) watching §4.2 and §4.4 in particular.
   Seven game months are done.
5. §3.2 to §3.5 once there are measurements.
6. Return to Nexerelin testing when its 0.98.5a build is out.

## 10. Evidence

Scratch outputs from this review (compiler logs, file lists, diffs) are in the session scratchpad
and are not part of the repo. Every figure above can be regenerated from the two installs named in
the status block.
