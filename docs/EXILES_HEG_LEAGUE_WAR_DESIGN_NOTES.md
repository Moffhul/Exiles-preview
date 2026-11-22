# Heg–League War — Design Notes (sounding-board feedback)

> **STATUS: DESIGN NOTES — NOT CANON, NOT A PLAN. (2026-09-24)**
>
> This is candid design feedback Sean asked for, as a sounding board and second opinion. The
> feedback and every suggestion below are **assistant-originated** (Claude, Opus 5.5 session,
> 2026-09-24). Sean is using it as input for his own ideas, not as a spec.
>
> - Do NOT implement from this file, and do not treat its suggestions as ratified.
> - Canon stays in `docs/EXILES_HEG_LEAGUE_WAR.md`. Code-level fleet-behaviour proposals are in
>   `docs/EXILES_HEG_LEAGUE_WAR_RECOMMENDATIONS.md` (also unratified).
> - The only statements of intent here are Sean's own, and they are quoted as such in §6.

---

## 1. Context: Sean's framing of the problem

The Hegemony and the Persean League are the Core's two strongest militaries. Both know an all-out
war would tear the Core apart, or leave the winner too weak to survive the other factions. The design puts
them in a **limited war that neither side will commit to**, with internal factions on both sides:
hawks push for escalation, doves for peace. Among the other directors, Omicron is trying to wipe out
the Luddics, the Ashen are waging holy war, and the Black Fleet is sowing chaos; this war was meant to be distinct.

Observed result: "not spicy." Fleets show up, a few engagements happen, then the op breaks up, or
a Nexerelin task force wipes out the aggressor. Sean suspects the core issue is the design, not
only fleet behaviour (though the behaviour also needs tuning).

## 2. Verdict

The premise is good and is not the problem. The director carries out the *mechanics* of a limited war
but not its *drama*. On screen, a restrained war and a weak war look the same unless the player can see
what is being held back, and right now they can't.

## 3. What is going wrong (diagnosis)

1. **Its verbs are the other directors' verbs, scaled down.** Interdiction, blockade, squeeze and
   strike are raids and sieges with smaller numbers, destroyer caps and early abort lines. What makes
   a limited war interesting is the gap between what each side *could* do and what it *chooses* to do:
   the standoff, the near-miss, the commander who exceeds orders, the incident that could go either way.
   The director has no verb for that gap except "do less violence," so the player sees less
   violence. Omicron, the Ashen and the Black Fleet each have a signature action; this war's signature
   is holding back, and there is no action for it.
2. **The interesting state is invisible.** The tension meter, war weariness, the six-state cycle, and
   the hawks and doves are the whole design, and they all live in code. The player sees fleets arrive,
   skirmish and leave. The hawks and doves have no names, no faces and no visible wins or losses. A cold war is
   gripping when you can watch the clock; here the clock is a private float.
3. **Most of the war happens off screen, through placeholder text.** In the 2026-09 run, 13 of 14
   strikes resolved off screen, each on exactly day 45. For most of a campaign the war *is* the intel layer,
   and that text is still placeholder (R12). The main way players experience this director is its
   least finished part. This is normal sequencing, not a mistake, but it explains part of "not spicy."
4. **Nothing lasts, by design.** Conditions lift, disruptions heal, relief convoys repair, and the cycle
   resets to a temporary ceasefire. That is the controlled-collapse rule working as intended, but it
   leaves the war with no memory, and without memory there is no story. Gilead stays gutted and the Ashen had one
   great assault; this war returns to zero every cycle.
5. **The guardrails show up on screen.** The destroyer caps (a ratified protection against ship
   farming), the 50% abort, and ops built to fail gracefully are each reasonable. Together they make the
   Core's two strongest militaries arrive in destroyers and break off at half strength. "It breaks up" is
   what the code is built to do. A Nexerelin task force wiping out an attacker is a limited op meeting an
   unlimited response. That is on-theme, but the director does not treat it as an event.

## 4. Examples from other games and fiction

- **Victoria 3, diplomatic plays.** The drama is in the phase *before* war: both sides add war goals,
  allies choose sides, and either side can back down. Most plays never become wars, and they are still
  the most tense part of the game. This is the premise as a mechanic: the standoff is the event, and war
  is the failure state.
- **Twilight Struggle, the DEFCON track.** Tension is public, both players push it, and reaching the end
  loses the game for whoever pushed it there. Make the meter visible, and make pushing it a risk for the
  side that does.
- **The Cuban Missile Crisis "quarantine".** The defining moment of a limited war was a blockade line
  and ships turning around, not a battle. The spectacle came from almost firing.
- **The Expanse, early seasons.** The Earth–Mars cold war turns hot because a *third party* destroys
  ships and lets each side blame the other. That is almost exactly Midan's role here.
- **Shadow of Mordor's Nemesis system; Bannerlord's lords.** Named commanders who come back, remember, hold grudges
  and vote on war and peace. That is how "factions within factions" becomes something you can see.

## 5. Directions to consider (assistant suggestions — prompts, not plans)

1. **Give the Midan false flag (Pass 7) top priority for spice.** A war neither side wants becomes
   dramatic when someone wants it badly. The limited war becomes Midan's best lever, and the director's
   real question becomes "will it tip over?" Incidents, the telegraph and retaliation already exist;
   Midan is the missing antagonist. This is judged the single biggest improvement available, and it is
   already on the roadmap (currently waiting on Sean's target list).
2. **Build standoffs.** Two task forces in a disputed system for weeks, shadowing each other with
   `FOLLOW`/`HOLD` and not firing. Then a small roll: someone blinks, a hawk captain breaks orders, or it ends
   in a ceasefire. `docs/STARSECTOR_VANILLA_FLEET_BEHAVIOR.md` shows the engine already has orders for
   restraint (`FOLLOW`, `HOLD`, `STANDING_DOWN`). This is the missing verb, and it is cheap.
3. **Turn R1 around: fight over the border, not the colonies.** Real limited wars fight over marginal
   ground. Make comm relays, nav buoys and sensor arrays in a few disputed systems the war's lasting,
   visible, reversible stakes: the map changes, stability shifts a little, nothing gets decivilized. That
   gives the war memory inside the controlled-collapse guardrails. See `EXILES_HEG_LEAGUE_WAR_RECOMMENDATIONS.md`
   R1 for the unplanned capture mechanism this would redirect.
4. **Put faces on the hawks and doves.** One named hawk admiral and one dove on each side, even as
   intel-only characters. "Admiral X's faction gains the upper hand" gives tension shifts an author. It also
   sets up Phase 2, where the player's lever is backing one of them.
5. **Make the sides behave differently, not just tune differently.** Today it is the same classes with
   different numbers. For example, the Hegemony inspects, and the League hires privateers so it can deny
   involvement, which fits a confederation. That would give each side its own character. (Illustration
   only; any faction-behaviour choice is Sean's call.)

## 6. Caveats and the reframe

- **Quieter is fine if it is legible.** A slow burn is good contrast among open fires. Something quiet
  that the player can follow can still be gripping; something quiet that is invisible is just boring.
  Suggested order: fix visibility before intensity.
- **What the director is for.** Once quests arrive, a limited war's best content is the player as the
  deniable asset, the messenger, or whoever sparks the incident. Consider building the director less to
  resolve outcomes off screen and more to create situations the player can walk into. That also makes
  Phase 2 easier.

**Sean's response (2026-09-24, recorded as his stated intent, not a ruling on the suggestions above):**
- The reframe was new to him. He had been focused on the behaviours, which he still thinks need
  tuning, and had not been thinking about how the war will look once quests are in.
- Planned Heg–League quests include the player being asked to **transport secret negotiators between
  the factions** (Sean: "about 80%" on adding this; consistent with the reserved Phase-2 secret-envoy
  idea and ruling R15, "temporary ceasefire, never a summit").
- Planned Heg–League quests also include **uncovering Midan**, who started this war and are working to
  fuel it.

---

## 7. Complexity review — "Keep It Simple?" (2026-09-26)

> Same status as the rest of this file: **assistant feedback, not canon, not a plan.** Sean asked
> whether the war director is drifting from "keep it simple" and asked for any unnecessary
> complexity to be flagged. Nothing here is ruled.

**Short answer:** the 2026-09-26 units fix (§14.14 Option A) is a simplification in concept: one
unit, one set of odds, no per-source scatter. But it added temporary scaffolding that should not
become permanent. The larger complexity is in the design, not the code.

### 7.1 Temporary scaffolding added 2026-09-26: schedule its removal
1. **The `feasibilityUnits` `"strength"` / `"fp"` switch and the LEGACY `feasibilityMult` keys.**
   Every Heg–League gate currently has two code paths. Once Option A is confirmed in play, delete the
   `"fp"` path and the legacy keys. Removing player-facing settings needs Sean's approval (CLAUDE.md
   §7). Schedule it, so the rollback does not quietly become permanent.
2. **The Step 1a `Exiles units [` lines in all four directors.** They are measurement tools. Remove
   them once the decision on the other three directors is made. In Heg–League they now repeat the
   feasibility lines.

### 7.2 Existing code worth trimming
3. **Log volume.** One relief convoy printed about 80 drip lines in the 09-23 Nex run
   (`recoveryDebugMessages: true`). Many "measurement lines" across the directors have now answered
   their question. A pass to switch off or delete finished instrumentation cuts noise at no cost.

### 7.3 Design: where the real complexity is
4. **Three "fleets park and trade suffers" operations:** interdiction (whole system), system blockade
   (the jump gates) and squeeze (one planet). Each has its own gate, conditions, cooldowns and config
   block. To the player they look alike. Sean mistook a squeeze for a Nex attack, and a Nex blockade
   for ours (Run 4/5 reads). **The strongest simplification candidate.** Two presence ops, e.g. a
   system blockade plus a planetary one, may read more clearly than three.
5. **Machinery the player cannot see.** Two meters (tension, weariness), six states, a three-way
   exit from RETALIATION and incident tables. Well built, but most of its output never reaches the
   screen, and complexity that isn't visible has a cost with no benefit to the player. Suggestion:
   **add no new states or meters until the existing ones are visible** (links §3.2 and §5.4 of this file).
6. **Relief convoys.** At `extendedDisruptMult` 2.0 a strike doubles the damage, and a convoy then
   repairs the extra half. Without player interaction that loop is effectively one number with extra
   steps. It earns its complexity when Phase 2 makes convoys something the player can act on
   (escort, intercept, rob). Keep it, knowing that is its justification.

### 7.4 A test for future additions
Every mechanism should earn its place in one of two ways: **the player can see it and act on it,
or it protects stability.**
- The deciv guards, abort lines and cooldowns pass on stability.
- Items 4–6 currently pass neither.
- Items 1–3 are temporary by design.

**Bottom line:** the director is not over-built for its goal. The limited-war cycle is sound. The
risk is that its breadth grows faster than its visibility. Consolidating the presence ops and making
the state visible would add more "spice" than any new mechanism.
