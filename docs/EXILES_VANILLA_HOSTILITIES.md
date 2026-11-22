# Vanilla "Hostilities" Intel — What It Actually Does (Answer: Almost Nothing)

*Investigated 2026-07-07 at Sean's request: "I keep seeing reports of hostilities between factions
and those hostilities later ending, but I've never noticed anything actually happening in-game."*

**Verdict: your observation is correct.** In vanilla 0.98a the entire mechanical payload of a
"Hostilities" event is a **temporary faction-to-faction relationship flip to HOSTILE, restored to
the exact prior value when it ends**. No fleets are spawned, no raids occur, no trade/economy/market
effect exists. For a player without a commission it is pure news flavor.

## The system

- **Generator:** `FactionHostilityManager` (a `BaseEventManager` CoreScript) + one
  `FactionHostilityIntel` per active conflict. Registered at sector creation in
  `CoreLifecyclePluginImpl` (~line 761), which also **pre-seeds three conflicts at game start**:
  Hegemony–Tri-Tachyon, Hegemony–Persean League, Tri-Tachyon–Luddic Church.
- **Who can be picked:** only factions with `"engagesInHostilities":true` in their `.faction`
  custom block AND ≥1 market. In vanilla that is exactly 5: hegemony, tritachyon, sindrian_diktat,
  persean (the Persean League; `persean_league` is only its file name), luddic_church.
- **Cadence:** spawn check every ~7.5–12.5 days, 50% skip chance, concurrent conflicts capped at
  ceil(0.1×eligible) … ceil(0.67×eligible) (settings.json `minFactionHostilitiesMult` /
  `maxFactionHostilitiesMult`). Pair selection is **biased toward the player's commission faction**
  (weighted × factionCount). Pairs already hostile, or at COOPERATIVE+, or in the 30-day
  recently-ended timeout are skipped.
- **Duration:** guaranteed 180 days minimum, then a 25% end-chance roll every 30–90 days.
- **The one real effect:** `FactionHostilityIntel` ctor does
  `one.setRelationship(two, HOSTILE)` after saving `initialRelationship`; `endHostilties()`
  restores it exactly. That's the whole payload.

## Why you never see consequences

1. Nothing in vanilla attaches fleet or economy behavior to the intel — no code path reads the
   hostility event to spawn war fleets or disrupt trade. (The old `FactionHostilityEvent` that
   *did* mention trade disruption is disabled in `events.json` — "replaced by FactionHostilityIntel".)
2. Eligible pairs were already near-hostile (SUSPICIOUS/INHOSPITABLE), so the flip only changes
   how their fleets treat each other *if they happen to meet* — and no new fleets are created to
   make that happen.
3. **The only player-facing consequence is via commissions:** `FactionCommissionIntel.makeRepChanges()`
   drags a commissioned player to hostile with the other faction for the duration (restored after,
   minus a 0.1 penalty). Uncommissioned players: zero effect.
4. It self-heals completely — no cumulative drift over a campaign.

## Interaction with Exiles

- **Already-hostile pairs are skipped** (`pickFactions()`: `if (faction.isHostileTo(one)) continue;`).
  Exiles' scripted hostile-by-default relations therefore *shrink* the candidate pool; in an Exiles
  game the system fires rarely or never beyond the three seeded conflicts. Caveat: `startHostilities()`
  (the seeding path) does NOT check pre-existing hostility — the three seeded pairs will still run
  their timer and "restore" to whatever the relation was at seed time (under Exiles, likely already
  hostile → visually a no-op).
- **exilerefugee correctly opts out** (`"engagesInHostilities":false` — "they do not start wars").
  The other Exile factions don't set the flag (default false), so none of the four are ever picked.
- **Modding hooks:** per-faction `engagesInHostilities` flag; global `minFactionHostilitiesMult` /
  `maxFactionHostilitiesMult` in settings.json; programmatic `FactionHostilityManager.getInstance()`
  `.startHostilities(a,b)` / `.areHostilitiesOngoing(a,b)` / `.getHostilitiesInvolving(f)`.
  Duration/probability constants are hardcoded (no settings keys, no listener interface) — changing
  cadence means replacing the classes.

## Relevance to Phase 1 (Sector Destabilization Director)

This is a useful negative result: **vanilla "hostilities" are cosmetic, so the Director doesn't
need to coexist with a real vanilla war system** — there isn't one. Two design notes:

1. The planned "Hegemony vs Persean League escalate into a real shooting war" content (§6 Phase 1)
   can *reuse* this system's surface (its intel/notifications feel native) while the Director
   supplies the missing substance (actual raid/attack fleets). `startHostilities()` is callable to
   force the flavor layer on cue.
2. If the Director sets more vanilla majors mutually hostile over a campaign, expect the vanilla
   hostilities intel to gradually stop appearing at all (pool exhaustion) — not a bug.

*Key files: `starfarer.api/com/fs/starfarer/api/impl/campaign/intel/FactionHostilityManager.java`,
`.../FactionHostilityIntel.java`, `.../FactionCommissionIntel.java` (makeRepChanges),
`.../CoreLifecyclePluginImpl.java` (registration/seeding), `data/config/settings.json` (mult knobs).*
