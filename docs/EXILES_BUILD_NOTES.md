# Exiles Build / Compile Notes

Created: 2026-06-18
Status: **HYBRID DEV BUILD** — loose Janino scripts plus one precompiled rule-command helper JAR.

## Two compile models

- **Dev (current):** Starsector compiles the loose `.java` under `data/scripts/` at game start via **Janino**. Edit Java → restart game → done. The one exception is the precompiled rule-command helper described below. This is why ordinary loose code remains Janino-safe (conservative) Java.
- **Release (planned):** precompile the scripts into a jar shipped with the mod. Faster load, and frees *future* code from the Janino syntax subset.

### Current precompiled rule-command helper

The classes under `src/data/scripts/rulecmd/` must be available when `rules.csv` is loaded, before
loose scripts compile. They therefore ship in `jars/ExilesRuleCommands.jar`, declared in
`mod_info.json`; `data/config/settings.json` registers `data.scripts.rulecmd`.

- `ExileAICores` subclasses vanilla `AICores` and changes only the three Omicron reputation values
  used by preview and award.
- `ExileOmicronStationDefense` gives the qualitative pre-commit warning and wakes the correct
  dormant Omicron complement immediately before vanilla `MarketCMD engage` gathers station-battle
  participants. It is self-contained because a precompiled rule command cannot safely depend on
  the later-loaded loose-script classes.

- `ExileStartingFines` transfers vanilla Exile-start eligibility into the real campaign using a
  before-time-pass script. Package its anonymous inner class as well as the command itself.
- `ExileAddStartingScript` owns the vanilla Exile start cargo/credits, calls vanilla's Galatia
  cleanup without the standard package or relation bonuses, and holds the Exile-specific player
  relation values applied by `ExilesModPlugin.onNewGameAfterTimePass`. Rebuild after edits with
  `tools/build-rule-commands.ps1` and restart the game.

Do not duplicate these classes under `data/scripts`.

## Key fact: no cleanup pass needed to compile

Janino-safe code is a *subset* of Java, so it is **`javac`-compatible as-is**. Precompiling needs **no** modernization — the code behaves the same compiled as under Janino, and a single `javac` pass surfaces any incompatibility at once (no need to pre-flag anything). Only a rare genuinely-verbose Janino workaround (tagged `// JANINO-VERBOSE`, none currently) would *benefit* from modernization, and that's optional polish, not a requirement.

## Toolchain

### Refugee salvage boundary checks (2026-09-07)

Run `tools/refugee-salvage-check.ps1` for focused checks of the Janino-loaded salvage helper using
campaign API proxies and vanilla temporary modifiers. It compiles the harness into a uniquely named
system-temp directory; no game files or production output are generated. Checks cover target/item
exclusions, market refresh ordering, surviving-cargo delivery, starting stores, commodity caps and
independent expiry. It is not a movement, campaign simulation, or save/load test.
Run `tools/janino-check.ps1 data.scripts.exiles.ExilesModPlugin` for the startup script graph.

- **Compiler:** `C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\jbr\bin\javac.exe` (javac 25). **Target Java 17** (Starsector's runtime): `--release 17`.
- **Classpath:** the Starsector API + support libs under `D:\Fractal Softworks\Starsector\starsector-core\` (`starfarer.api.jar` plus the `json` / `lwjgl` / log4j etc. libs). The IntelliJ project already encodes this — see the `Exiles.iml` module dependencies. (Our scripts also use `com.fs.starfarer.api.impl.*`, which ships inside `starfarer.api.jar`.)

## Build steps (high level)

1. Compile `data/scripts/**/*.java` against the Starsector classpath; output `.class` files preserving the `data.scripts.*` package layout.
2. Package the `.class` files into a jar (e.g. `jars/Exiles.jar`).
3. Reference it in `mod_info.json` → add `"jars":["jars/Exiles.jar"]` (keep `"modPlugin":"data.scripts.exiles.ExilesModPlugin"`).
4. **Easiest path:** build via IntelliJ's *Build Artifacts* — the project dependencies are already set up. A command-line `javac` works too if you mirror the `.iml` classpath.

## RELEASE GOTCHA — don't ship the same class twice

Starsector compiles loose `.java` in `data/scripts/` via Janino **and** loads jars. If a class exists in **both** the jar and as loose `.java`, you get duplicate/conflicting class definitions at load. **At release, remove (or relocate out of the scanned path) the loose `.java` once it's in the jar** — e.g. move sources to a `src/` folder Starsector doesn't load.

## Workflow change to expect

Once you ship a jar, edit-Java → restart-game no longer recompiles — you rebuild the jar each change. Common practice: keep the Janino loose-source workflow during dev, build the jar only for release.
