# Exiles — Omicron Tactical Cores

Created: 2026-07-12
Updated: 2026-07-19
Status: **LIVE PLAYTEST CONFIRMED — BETA TUNING HOLD**

This is the canonical design and implementation reference for Omicron Cores and Omicron
combat officers. Read it before changing core IDs, officer skills, fleet distribution, or drops.

> **TUNING HOLD — author rulings updated 2026-07-19:** the three-core structure, base skill
> packages, and Automated Ships cost multipliers are retained. Fleet/station distribution,
> stable player portraits, functional names, and reduced cash turn-in values are implemented.
> Live testing confirms ordinary recovery and faction-contact turn-ins. Current drop rates and
> base turn-in reputation (**2/3/5**) are accepted for the Phase-1 build. Revisit them only if the
> post-Phase-2 Alpha Build Test or later public Beta feedback supplies evidence. See §8.

## 1. Canon and design boundary

An Omicron Core is a **tactical AI core**. It is less flexible than even a general-purpose
Gamma Core outside its specialty, but purpose-optimized for shipboard combat. Increasing grade
means greater tactical processing capacity and coordination scope, not increasing humanity,
creativity, or general intelligence.

The three grades are separate cargo commodities so the player can identify, store, install,
and pursue a particular quality deterministically:

| Grade | Player-facing name | Commodity ID | Intended scope | Vanilla balance baseline |
|---|---|---|---|---|
| I | Executor Core | `exiles_omicron_core_1` | single-ship tactical process | Gamma |
| II | Control Core | `exiles_omicron_core_2` | complex line combat / local tactical control | Beta |
| III | Command Core | `exiles_omicron_core_3` | capital and fleet-scale tactical process | Alpha |

The question of whether a core is an independent individual, a synchronized fragment of the
larger Omicron network, or a disposable execution process remains deliberately open for Phase 2.

## 2. Player-facing behavior

- All grades are ordinary `RESOURCE` cargo using the vanilla `ai_core` and `nonecon` tags.
- They stack, transfer to storage, survive ordinary player combat loss, unlock Codex entries,
  and use separate 80×80 placeholder icons.
- Each can captain a player automated ship through the normal AI-core officer interface.
- Automated Ships cost multipliers are Grade I **×2**, Grade II **×3**, Grade III **×4**,
  matching Gamma/Beta/Alpha.
- No Omicron Core is an administrator or colony-industry item in this pass. No
  `AICoreAdminPlugin` is provided, and vanilla industry effects recognize only the stock IDs.
- Future preferred non-combat use: Tri-Tachyon research/quest turn-ins and Omicron
  countermeasure or early-warning unlocks. Do not add general economic competence casually;
  tactical narrowness is load-bearing.

### Vanilla contact-turn-in consequence

The standard `AICores` rule command accepts every commodity whose demand class is `ai_cores`.
It does not hard-code Alpha/Beta/Gamma for selection or credit value. Omicron Cores therefore
appear in ordinary AI-core turn-in dialogs. Their base cash values are **5,000 credits** for an
Executor Core, **25,000 credits** for a Control Core, and **100,000 credits** for a Command Core,
before faction bounty multipliers.

This differs from the initial preference to reserve them for bespoke Tri-Tachyon handling. The
vanilla behavior is retained for stability/compatibility: excluding only Omicron Cores would
require either a broader replacement of the turn-in path or moving them out of the `ai_cores`
demand class, risking cargo grouping, legality, and officer-selection behavior. Ordinary cash
payouts are therefore reduced through commodity base values. A narrow precompiled subclass of
vanilla `AICores` changes only the reputation lookup used by the three existing command calls.

**Implemented reputation baseline — 2026-07-19:** Executor **2**, Control **3**, Command
**5** base faction-reputation points, before faction multipliers. The Command Core deliberately
does not exceed an Alpha Core's base reputation value: its value as another ship officer is subject
to diminishing returns, and it entirely lacks the Alpha Core's colony and administrative value.
Vanilla computes unknown custom IDs as 1 point each. `ExileAICores` overrides only
`computeCoreReputationValue()`, maps the Omicron IDs to 2/3/5, and delegates every other ID to
vanilla `AICores.getBaseRepValue()`. The mod's `rules.csv` overrides the three vanilla command-call
rows to use this subclass. The picker preview, confirmed faction award, faction multiplier,
mixed vanilla/Omicron batches, threshold behavior, and quarter-strength contact reputation all
therefore run through one calculation.

**Implementation correction — 2026-07-19:** the first post-confirmation campaign-plugin workaround
left the vanilla preview at 1 and could double-apply the corrected award because two transient
instances participated. That workaround was removed completely; reputation correction now exists
only in the precompiled `ExileAICores` command. **Live test passed:** each Omicron grade previewed
and awarded the correct value exactly once. A mixed batch containing one Executor, Control, Command,
Gamma, Beta, and Alpha Core also produced the correct independent rewards with no interference.

## 3. Officer packages

Player-installed cores use deterministic base packages. All skills are elite and the personality
is the stock AI-core `reckless` behavior. **Author ruling 2026-07-14:** retain the implemented
3/5/7 packages and ×2/×3/×4 costs as the design baseline; ordinary tuning remains possible, but no
major departure is planned.

| Grade | Level | Base elite skills |
|---|---:|---|
| I | 3 | Helmsmanship, Target Analysis, Combat Endurance |
| II | 5 | Grade I + Field Modulation, Gunnery Implants |
| III | 7 | Grade II + Systems Expertise, Energy Weapon Mastery |

NPC Omicron cores follow the vanilla Remnant integration seam unless a fleet explicitly sets
`doNotIntegrateAICores`: integration adds one level and adapts one additional combat skill to the
ship loadout. Fleet commanders may also receive the faction's normal commander skills. Thus the
recoverable item is the stable base core while an installed Omicron combat process can be adapted
to its assigned hull.

Current portrait placeholders are deterministic by grade:

- Grade I: `portrait_omicron1a.png`
- Grade II: `portrait_omicron2a.png`
- Grade III: `portrait_omicron3a.png`

They are copies of vanilla AI portraits and remain art placeholders. Removing and reinstalling a
player core recreates the same grade-specific portrait; no per-item identity tracking is used.

## 4. Omicron fleet distribution

`ExileOmicronFleetOfficerGenerator` is registered as a transient
`GenerateFleetOfficersPlugin` at `MOD_SUBSET` priority. It returns a handling priority only for
faction `exileomicron`, so other factions and other mods' fleets are untouched.

- At least one eligible automated combat ship receives a core.
- Additional core count scales through vanilla `baseFPPerAICore` plus the fleet parameters'
  officer-number multiplier/bonus.
- Frigates strongly favor Grade I; destroyers I/II; cruisers primarily II; capitals II.
- Fleets above 150 FP with an eligible capital reserve exactly one Grade III core for that
  capital, which is then made the fleet commander and flagship.
- Grade III never enters the ordinary per-hull picker, preventing multiple Grade III officers.
- The commander is selected from installed cores with strong preference for higher grade and
  larger hull, then assigned through the normal fleet commander/flagship fields.
- Nexus and UC-Nexus station fleets are explicitly commanded by Grade III cores. Mining and Fuel
  Stations use ordinary Gamma Cores. Undamaged stations receive the same integration and
  commander-skill adaptation previously applied to their vanilla cores.

## 5. Vanilla salvage path

No custom battle listener or loot script is used. For each enemy battle casualty (destroyed or
disabled; repaired/escaped non-casualties are excluded), `FleetEncounterContext` reads the
captain's exact `aiCoreId`, checks that the commodity lacks `no_drop`, loads
`drop_prob_officer_<coreId>`, applies the stock hull-size multiplier, and adds the commodity to
loot. Exiles supplies only the three base settings:

| Grade | Base | Frigate | Destroyer | Cruiser | Capital | Station |
|---|---:|---:|---:|---:|---:|---:|
| I | 20% | 20% | 40% | 60% | 80% | guaranteed |
| II | 10% | 10% | 20% | 30% | 40% | guaranteed |
| III | 5% | 5% | 10% | 15% | 20% | guaranteed |

The station multiplier is vanilla's ×1000, so any positive configured chance is effectively
guaranteed. Nexus and UC-Nexus stations carry Grade III; Mining and Fuel Stations carry ordinary
Gamma Cores.

There is no grade conversion and no generic Omicron-core drop group. A recovered Grade I core
proves that at least one eligible casualty in the full battle had a Grade I captain, even if the
player inspected only the primary fleet/visible senior officers. Multi-fleet battles and low-grade
cores on frigates/destroyers are the common reasons this may not be obvious before combat.

## 6. Implementation map

- `data/campaign/commodities.csv` — three cargo definitions.
- `data/strings/descriptions.csv` — public descriptions.
- `data/config/settings.json` — drop probabilities + portrait sprite registration.
- `ExileOmicronCoreIds` — stable IDs.
- `ExileOmicronAICoreOfficerPlugin` — commodity → deterministic officer package.
- `ExileOmicronCampaignPlugin` — campaign picker making the custom IDs genuine AI officers.
- `ExileOmicronFleetOfficerGenerator` — Omicron-only grade distribution and commander selection.
- `ExilesModPlugin.registerOmicronCorePlugins()` — transient, load-safe registrations.
- `OmicronStationBuilder` — Grade III Nexus commanders and Gamma Mining/Fuel commanders.
- `src/data/scripts/rulecmd/ExileAICores.java` — self-contained vanilla-command subclass supplying
  the 2/3/5 reputation lookup for both preview and confirmed award.
- `jars/ExilesRuleCommands.jar` — precompiled rule command loaded before `rules.csv` resolution.

There is no recurring core-management script and no serialized custom plug-in instance.

## 7. Verification

Offline completed 2026-07-12:

- Full `javac --release 17` source-tree compilation: clean (six pre-existing warnings only).
- Starsector bundled Janino check: all six changed/new entry classes compile clean.
- Commodity CSV parses as three complete rows; descriptions parse; all icons are 80×80 RGBA.

Fresh-game checks still required:

1. Cargo/Codex: grant each ID; verify separate icon, description, stacking, storage, and value.
2. Player use: with Automated Ships, install/remove/reinstall each grade on an automated hull;
   verify its stable grade-specific portrait, 3/5/7 skills, reckless personality, and
   ×2/×3/×4 automated-points cost.
3. Fleets: inspect raid, patrol, defense, Nexus, and UC-Nexus captains; confirm no human officers,
   correct grade-by-hull tendency, and a valid fleet commander.
4. Salvage: destroy one captained hull of each size/grade; confirm probabilistic drops. Destroy a
   station and confirm its Grade III core drops.
5. Save/load: save with each grade in cargo and installed on ships; reload and recheck officers.
6. Compatibility: repeat one fleet/player-install test with Nex enabled.
7. Turn-in: confirm the picker previews 2/3/5 before faction multipliers and confirmation awards
   that amount exactly once. Repeat with a mixed vanilla/Omicron batch and Tri-Tachyon's ×0.5
   reputation multiplier.

### Playtest 2026-07-12 — first live confirmation

- Omicron campaign fleet names render without the duplicated `Omicron Omicron` prefix.
- Omicron fleet casualties successfully drop Omicron Cores through vanilla salvage.
- Recovered Grade I cores enter cargo/storage normally.
- Grade I cores can be installed as officers on player automated ships.
- **Playtest 2026-07-19:** recovery rate accepted for Phase 1; further tuning requires evidence
  from the Alpha Build Test or broader Beta feedback.
- **Playtest 2026-07-19:** ordinary faction AI-core contact turn-ins confirmed functional.
- **Playtest 2026-07-19:** precompiled 2/3/5 correction confirmed for each Omicron grade and for a
  mixed six-core Omicron/Gamma/Beta/Alpha batch; preview and award matched, with no double award.

## 8. Author rulings and tuning status — updated 2026-07-19

Approved design direction and current implementation status:

- Retain **three recoverable Omicron tactical cores**. The proposed ordinary fourth tier is
  rejected; do not add it.
- Keep the stable commodity IDs and intuitive internal Grade I/II/III ordering. The finalized
  player-facing functional names are **Executor Core**, **Control Core**, and **Command Core**.
- Retain the implemented 3/5/7 elite-skill packages and ×2/×3/×4 Automated Ships multipliers as
  the baseline. The specialized packages, rather than extra skill levels, provide Omicron's combat
  advantage on appropriate hulls.
- Grade I appears primarily on frigates and destroyers and remains common enough for player use.
- Grade II appears primarily on cruisers, with limited destroyer and capital use.
- Grade III appears on Nexuses and on at most one capital flagship in a sufficiently serious fleet.
  It is a rare command process, not ordinary station loot. **Implemented in the distribution pass.**
- Mining and Fuel Stations use ordinary Gamma Cores. This is intentional evidence that Omicron
  assigns commodity general-purpose intelligence where a tactical process is unnecessary; expose
  that reasoning later through Omicron quest dialogue. **Implemented in the distribution pass.**
- Use one stable portrait per player-facing core type. Random portrait changes after
  remove/reinstall are undesirable. **Implemented using the `a` portrait for each grade.**
- Reserve the primary Nexus as possible future design space for a unique network-level object if
  Phase-2 quest design justifies one. This is not approval for a fourth ordinary core.

Deferred tuning/to-do:

- Treat recovery probabilities and the implemented **2/3/5** reputation baseline as complete for
  Phase 1. Reopen them only if the Alpha Build Test or broader Beta feedback identifies a concrete
  economy, farming, or progression problem.
- Preserve the open question of whether cores are individuals, network fragments, or disposable
  execution processes for Phase 2.
