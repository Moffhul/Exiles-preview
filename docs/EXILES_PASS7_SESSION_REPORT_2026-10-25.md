# Pass 7 session report — 2026-10-25 (afternoon, unattended)

For Sean, on return. Everything below is committed **locally**; nothing is pushed.
Full build records and the test checklist: `docs/EXILES_PASS7_BUILD_PLAN.md` §7 (7d-1 parts 1–3).

## Where I stopped, and why

**Stop condition 1: test before proceeding.** Step 7d-1 is complete: the Strike Force's variants,
skins, composer and escort sizing, all log-only. The 7d split exists so these numbers are checked
before 7d-2 builds the launch. 7d-2 also depends on two rulings below.

## What was built (all untested in game)

| Commit | What |
|---|---|
| `8ea535d` | Midan baseline fix (monthly, all targets) + retire vanilla's Heg–League hostility intel |
| `785d976` | Ten Strike Force variants + the Midan Omen skin (placeholder sprite `exileomen1.png`) |
| `640a1bc` | Phaeton and Buffalo skins with Militarized Subsystems built in |
| `159747e` | The composer: real fleet strength, escort sizing (cap 2), `strikeForce` config block |
| (docs) | Build records, Nex check, stock-settings notes, this report |

**Your built-in question:** the variant file cannot do it. The decompiled variant loader reads
only `hullMods`, `permaMods`, `sMods`, `sModdedBuiltIns`, `suppressedMods`; any other key is
silently ignored. `permaMods` without `sMods` keeps the mod on the ship but still charges OP (8 on a
destroyer, and none is left). Your Salvage Rig example agrees: its gantry is built in at the
**hull** (`constructionrig.ship`), and its variant's `permaMods` is empty. So I followed the
Hegemony Buffalo exactly: two small skins (`exile_phaeton`, `exile_buffalo`), vanilla names and
sprites, Militarized Subsystems in `builtInMods`. Side effects: +1 burn on those two hulls (fleet
burn stays 8) and their minimum crew doubles.

**Fleet sensor profile ≈ 438** (verified formula, decompiled engine): the five highest ship
profiles summed (three Omens at 30, two logistics at 24) plus vanilla's flat 300. Without Insulated
Engines it would be about 810.

## The test (one new game, stock settings)

The checklist is in plan §7 ("7d-1 test checklist"). The short version:
1. The game reaches the main menu, and there are no errors naming `exiles_midan` / `exile_omen` /
   `exile_phaeton` / `exile_buffalo`.
2. The log-report's "Midan measurements" section: a `COMPOSED` line with main **24 ships, 264 FP**,
   `PLS Ajax`, captain level 7, 10 officers; escort **20 ships, 226 FP**, 5 officers.
3. Strengths near my estimate: **main ≈ 528, escort ≈ 376** (a hand estimate; the engine's per-ship
   base may differ, so ±30% is fine, much more is worth a stop).
4. `vanilla Hegemony/League hostility intel retired` appears once; no "hostilities ended" notice.
5. MEASURE lines show `needs 0/1/2/MORE THAN 2 escort(s)`. My estimate against the 10-24 run:
   **Sphinx needs two escorts and clears in about 6 of 14 months**; Arcadia usually needs none;
   Eventide mostly one or two.

About one game year is enough (MEASURE lines start at day 0, monthly).

## Needs your ruling (I held off)

1. **Escort composition.** Code default (my SUGGESTION, labelled as such in the config): the main
   fleet's warships without the Ajax or logistics. That is 20 ships, 226 FP, with five level-5
   officers. It is all config (`midan.strikeForce.escortRoster` / `escortOfficerShips`).
   Alternatives: smaller escorts (more of them needed, so the cap of two bites more often) or a
   heavier escort with a capital. The test numbers will make this easier to judge.
2. **The Ajax captain's personality.** I used Steady (vanilla's default). Aggressive would suit a
   surgical strike; Cautious would suit a commander Midan can't afford to lose. Your call.
3. **Logistics capacitors.** Still in place from the earlier pass: the Phaeton and Buffalos each
   lost 6 flux capacitors to fit the normal 6-OP Insulated Engine (their stock variants use every
   OP point). Militarized Subsystems, being built in, costs no OP, so nothing else changed.
4. **7d-2 scope check** (no action needed now): the launch, rung-1 damage, the landing table,
   tension, and Ajax's protection (a) rebuild + (b) the autoresolve listener. Under Nex, (b) needs a
   check that Nex keeps vanilla's autoresolver; I'll do that in 7d-2.

## Things I verified along the way
- The Omen has **no spare OP** (stock PD variant uses 40/40), so it stays as is, per your
  no-crippling rule.
- Under Nexerelin, vanilla's hostility manager doesn't exist (Nex comments it out), so the retire
  call is a no-op. No conflict.
- Stock settings now on disk: `officerMaxLevel` 5 (not 6; the stock file says 5),
  `maxPermanentHullmods` 2. Ruled: fleet officers level 5; Ajax's captain 7 with three S-mods.
- Skin `tags` replace the base hull's (inferred from vanilla's Lion's Guard skins, not
  engine-verified): our four skins carry only `no_dealer` / `no_bp_drop`, so no faction knows those
  hulls and no market sells them.

## Not done / still open from earlier
- Push: all commits since `2001ac8` are local. Push when you say so.
- The art: `exileomen1.png` is a byte copy of vanilla's Omen (the midline-green recolour is a file
  swap).
