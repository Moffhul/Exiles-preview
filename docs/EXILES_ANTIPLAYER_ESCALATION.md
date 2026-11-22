# EXILES — Anti-Player Escalation Layer

**Status: DESIGN DIRECTION (Sean, 2026-07-29) + VERIFIED API FINDINGS. No code written yet.**

Scope: how Exiles factions go from "ambient sector-wide threat the player watches" to "this faction
has personally decided to come after you." Companion to `war-capacity-design.md` (Omicron ops) and
`EXILES_BLACK_FLEET_SYSTEMS.md` (BF ops). Nothing here has been implemented.

Origin: the 2026-07-28 raid-director review (`EXILES_KNOWN_ISSUES_AND_NEXT_STEPS.md`) raised
player-targeting inconsistencies between the two directors. The follow-up design discussion
concluded the inconsistency was a symptom of a **missing layer**, not a bug in the directors.

---

## 1. The two-layer split (RECOMMENDED; supersedes the "policy filter" sketch)

The raid directors and anti-player content answer two different questions and should stay separate.

| Layer | Question it answers | Player exposure |
|---|---|---|
| **Raid directors** (`OmicronDirector`, `BlackFleetRaidManager`, future Ashen) | "Who does the burning sector attack?" — ambient, opportunistic, in-Core, NPC-vs-NPC | **Invariant: never deliberately targets the player.** Ambient catch only, per §2 |
| **Anti-player layer** (new; this doc) | "Which faction has personally decided to come after you, and how far along is it?" | The whole point — telegraphed, staged, counterable |

**Why not rewrite the directors:** they encode playtest-earned corrections (the Al Gebbar transit
race, the playtest-4 per-tier feasibility split, the playtest-5 grudge-pull fix). A rewrite discards
empirical knowledge whose only re-acquisition cost is author playtime. The directors are not wrong
at their job; player targeting was simply never a distinct concept.

**The consolidation that IS coming:** `OmicronDirector` and `BlackFleetRaidManager` duplicate
substantially, and CLAUDE.md §6 already names the intended convergence on one configurable Sector
Destabilization Director. Do that refactor **when Ashen's director is built** — three
implementations is when the shared abstraction stops being speculative (rule of three). Do NOT fuse
it with this change.

## 2. Per-faction disposition at campaign start (author description, 2026-07-29)

The player is weak early and "needs some protection." Ambient exposure by faction:

- **Omicron** — cannot determine whether the player is foe. **Zero ambient exposure.**
- **Ashen Crusade** — does not care unless the player overtly sides with Tri-Tachyon. **Zero ambient
  exposure.**
- **Black Fleet** — treats the player as **typical pirate prey**, but has no interest in expeditions
  outside the Core. A player colony *in the Core* may be caught as an ordinary follow-up target;
  the player's home colonies are never the object of a deliberate expedition.
  - *Structural note:* the player's starting colonies (Thar, Daria) are in **Xechas**, which
    contains **no NPC markets** — no `econ/xechas.json`, no market creation in the generator. Both
    directors require a target-faction market in-system before a system is a candidate, so Xechas
    can never be selected. The starting colonies are safe by construction, independent of any filter.
  - *Author note (2026-07-29):* vanilla NPC behavior already contests player colonies planted in the
    Core, so BF ambient exposure there is a minor addition to existing pressure — minimal with mods,
    near-nonexistent in vanilla.
- **Midan** — follows the Black Fleet's escalation (see §3), not its ambient behavior.

Consequence: the Black Fleet's campaign-start `HOSTILE` (−50) relation toward the player
(`ExilesSectorGen.java:93`) is **correct, not a bug** — "pirate prey" is the intended default. The
escalation is the *expedition*, not the ambient sacking.

## 3. Escalation triggers (author description, 2026-07-29 — narrative direction, not yet specified)

All are quest outcomes, so all are **Phase 2** work:

- **Omicron** — the player proves a threat, especially by targeting Nexuses → direct attacks on
  player colonies.
- **Ashen Crusade** — the player proves a threat or becomes a major Tri-Tachyon ally → same.
- **Black Fleet** — Admiral Mourad recognizes the player as the officer he had thrown in the brig,
  and makes an overture to join the Black Fleet/Midan coalition:
  - **Accept** → player faction joins their economy; missions to destabilize the Core; Core story
    protections lift; goal becomes wiping out the Core.
  - **Refuse** → Black Fleet, and later Midan, begin attacking player colonies.
  - Either branch enters **mod endgame mode**.

**Scope caution (author, 2026-07-29):** "wipe out the Core" may be too ambitious. It is achievable in
vanilla but expensive to set up. Not scoped; do not build toward it without a ruling.

## 4. RATIFIED — "Core story protections lifted" global mode

**Author ruling 2026-07-29:** leave architectural room for a global flag that lifts Exiles' Core-world
story protections (the deciv guard, `exilesUnrestDecivGuard`, and the anti-repeat/battered markers
that currently keep the Core "on fire but standing" per CLAUDE.md priority 4).

> ### ⚠ MANDATORY — PLAYER WARNING BEFORE THE THRESHOLD
> **The player MUST receive an explicit in-game warning message before crossing the threshold that
> lifts Core story protections.** This is a point of no return that permanently changes the campaign;
> the player must be told, in clear terms, before they commit — not after. Any implementation of the
> endgame branch is incomplete without this warning. Do not ship the flag without it.

Cost note: leaving room for this now is nearly free; retrofitting it later is not.

## 5. VERIFIED — vanilla Colony Crisis API is usable (read-only recon, 2026-07-29)

Reference: `starsector-core/starfarer.api/com/fs/starfarer/api/impl/campaign/intel/events/`.
**Conclusion: yes, we can tap it. A custom director is NOT required for the anti-player layer.**

**Extension point is public and supported:**
- `HostileActivityEventIntel.get()` — public static accessor; the intel is parked in sector memory
  under `$hae_ref` (`HostileActivityEventIntel.java:61,99`).
- `addActivity(BaseHostileActivityFactor, HostileActivityCause2)` is **public**
  (`HostileActivityEventIntel.java:557`), as is `removeActivityCause(Class, Class)`.
- `BaseHostileActivityFactor` is a **concrete** class implementing the whole `HostileActivityFactor`
  interface with defaults — subclass and override only what is needed.
- The intel's display name is literally **"Colony Crises"** (`getName()`), i.e. this is the system
  the player already understands.

**It supplies exactly the "player needs protection" properties:**
- Staged progress with a visible bar: `START` 0 / `MINOR_EVENT` 300 / `HA_EVENT` 600, with
  randomized stage rolls (`setup()`, lines 128-136).
- `HostileActivityCause2` rows are the player-visible **"why"**, each contributing progress — these
  map directly onto §3's triggers ("you destroyed a Nexus", "you allied with Tri-Tachyon", "you
  refused Mourad"). Legible cause → legible consequence.
- `resetEvent` / `removeActivityCause` give de-escalation and counterplay.
- Ambient fleet pressure is separate from the staged attack:
  `getSpawnFrequency` / `getMaxNumFleets` / `createFleet` feed
  `DisposableHostileActivityFleetManager`.

**Omicron has a near drop-in vanilla template.** `RemnantHostileActivityFactor` provides
`getRemnantNexus(system)`, `findAttackTarget()` and
`startAttack(nexus, target, system, stage, random)` using `GenericRaidParams` with `remnant = true`.
Omicron is Remnant-derived and actually has Nexuses.
*Caveat:* vanilla's `findAttackTarget` only picks player markets in systems that **already contain a
Remnant Nexus** (`RemnantHostileActivityFactor.java:242-254`). Omicron's Nexuses live in its own
hidden nodes, so this needs reworking — Omicron attacks **from** its nodes **to** the player, rather
than requiring a Nexus next door. Not a blocker; it is the one real piece of adaptation work.

**Lifecycle gotcha — attach factors idempotently, never once.**
`HostileActivityManager` creates the intel only when the player has colonies and calls
`endImmediately()` when they have none. Exiles starts the player with Thar/Daria, so it exists from
game start — but `setup()` does `factors.clear()` and re-adds **only vanilla factors**, and
`redoSetupIfNeeded()` can invoke it on legacy saves. A custom factor added once can therefore be
silently dropped. Use an idempotent "ensure present" check (`getActivityOfClass`) on a slow tick or
game-load hook.

**Nexerelin compatibility: good.** Nex does not disable vanilla colony crises — it **adds its own**
through the same seam (`exerelin_config.json:167`, `"enableNexColonyCrises"`, currently the free-port
crisis). The extension point is proven mod-usable and Nex-friendly.

## 6. Still to verify before implementation

- **Janino.** These factors would be loose scripts. Most overrides we need are generics-free in
  their signatures, but this has NOT been compile-checked. Run `tools/janino-check.ps1` on a stub
  factor before committing to the loose-script path; fall back to the `jars/` precedent if it chokes.
- **`isPlayerOwned()` under Nex** — confirm it covers Nex-held player markets before relying on it
  as the ownership test.
- Whether the deciv guard should extend to player markets once the anti-player layer can hit them.

## 7. Recommended sequencing

1. **Now (Phase 1):** the directors' hard "never deliberately target the player" invariant, plus the
   per-faction disposition state that Phase-2 quests will write to. Small, safe, resolves the
   2026-07-28 review's items 2 and 3.
2. **Phase 2, with the quest scaffold:** the per-faction crisis factors and causes on the API above.
   These are quest *consequences*; building them before triggers exist means building against
   imagined interfaces.
