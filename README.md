# Exiles of the Shattered Sector

A work-in-progress mod for [Starsector](https://fractalsoftworks.com/) 0.98.5a by MoffHul11.

Exiles is a hard-start campaign set in an alternate Persean Sector that is coming apart faster
than the vanilla one. The player begins framed, imprisoned, broke and in debt, with two fragile
colonies, while five new factions pull the Core in different directions.

This copy is a **preview snapshot**, shared to show what a project built with AI coding
assistants looks like from the inside. It is not a release. Expect placeholders, test values,
and systems that are built but not yet fully play-tested.

## How this was built

The design, lore, rulings and play-testing are the author's. Most of the code and Markdown
documentation was written by AI coding assistants (Claude Code, with Codex for some of the
earlier reference documents) working under the author's direction over about five months and
more than 400 commits. The snapshot holds roughly 160 Java files (about 45,000 lines) and 44
Markdown documents (about 25,000 lines).

The working method is visible in the files themselves:

- The author decides what the mod should do and rules on every design question.
- The assistant reads the game's own code, proposes a plan, writes the implementation, and
  records what was built, what was tested and what is still open.
- The author plays the result and reports what happened. The findings go back into the docs.

## Where to look first

If you want to see how the collaboration works, read in this order:

1. **`CLAUDE.md`** is the steering document every AI session reads before doing anything. It
   holds the vision, the non-negotiable faction guardrails, the build order, and the
   conventions and engine traps learned along the way.
2. **`docs/EXILES_HEG_LEAGUE_WAR.md`** is the largest single system record. Its top-of-file
   status block shows how state is tracked, and the rest shows rulings, build passes and test
   runs accumulating over time.
3. **`docs/EXILES_PASS8_PLAN.md`** is a recent plan: findings from the game's code, options, the
   author's answers, then build and test records.
4. **`docs/EXILES_BUILD_NOTES.md`** explains the unusual constraint on the Java: Starsector
   compiles loose scripts at launch with Janino, which rejects much modern syntax. Verbose,
   old-style code in this mod is deliberate.
5. **`data/config/exiles/director_config.json`** holds every tunable for the faction-activity
   systems, with comments recording why each value is what it is.

For the code itself:

- `data/scripts/exiles/ExilesModPlugin.java` is the entry point.
- `data/scripts/exiles/` holds the faction directors, managers, raid stages, intel and shared
  utilities.
- `data/scripts/world/` generates the custom star systems.
- `data/scripts/industries/` and `data/scripts/marketconditions/` hold custom industries and
  market conditions.
- `tools/` holds the offline checks, including `janino-check.ps1` (compile check without
  launching the game) and `exiles-log-report.ps1` (test-run log analysis).

## Spoiler warning

`CLAUDE.md` and the documents under `docs/` state the mod's hidden story canon plainly, since
the assistant needs it to write consistent content. If you would rather play first, stay out of
those files until you have.

## Running it

1. Install Starsector 0.98.5a.
2. Unzip so that this folder sits at `Starsector/mods/Exiles/`.
3. Enable **Exiles** in the launcher and start a **new game**. Exiles does not support being
   added to an existing save.
4. Pick the Exiles start at character creation.

Nexerelin is supported but not required. Nexerelin's "flatten starting relationships" option is
not supported.

## Art

This snapshot does not include copies of Starsector's own artwork. Icons and station sprites
that reused vanilla images are replaced with plain "placeholder" images, and ships that use an
unmodified vanilla sprite point at the game's own file. Final art is future work.

## Status and terms

Version 0.1, unfinished. Shared privately for review. Please do not redistribute it.
