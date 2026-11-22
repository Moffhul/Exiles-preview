# Exiles — Lore History (pre-campaign) — WORKING DRAFT

> **Status: DISCUSSION IN PROGRESS (started 2026-10-15, Sean + Claude; Meridian/Cunningham rewrite
> 2026-10-18).** Driven by Sean finding he could not explain *why* the Hegemony–League war is the
> way it is ("motivation is an important thing for me"). This doc consolidates what Sean has stated
> in discussion, separated from assistant suggestions and open questions. **Sean's statements below
> are author canon unless marked provisional; items marked SUGGESTION are unruled.** When the
> discussion ends, this becomes the canonical lore record, and the stale points in Sean's Word
> timeline (`docs/Exiles Design Documentation/02 - Timeline.docx`, May 2026, unfinished after
> "Start − 7 years") are superseded.
>
> Hidden-canon rule (as for Omicron): most of this is unknown to the Sector at campaign start. Reveal
> it only through Phase-2 quests; rumours may carry fragments, half of them wrong.
>
> **2026-10-18 rewrite — what changed:** the Meridian attackers are now a **Hegemony task force
> only** (no mercenaries in the bombing); the cabal is **inside Tri-Tachyon only** (no Hegemony
> members); the Hegemony Commander is now **Commander Cunningham**, who did not order the
> bombardment; the spelling is **Wiggin** (not Wiggins). Next session: Meridian system changes (§6).

## 1. John Wiggin and Meridian (Cycles 182–186)

### 1.1 The rise of John Wiggin (≈ Cycle 182 onward)
- **John Wiggin** is deliberately "a player who came before you": his history mirrors a typical
  playthrough's early and mid game. (Name: "John Starsector", the community's name for the player
  character, + Ender Wiggin of *Ender's Game*.)
- As a young captain he **escapes the purges in Askonia** as Supreme Executor Philip Andrada betrays
  the Hegemony and founds the Sindrian Diktat (vanilla: the Askonia Crisis, c.180–181; the purges
  run on after it). Use Andrada's vanilla title, **Supreme Executor**.
- Over the next several years he succeeds in **trade, bounty hunting and exploration**; his fortune
  and fleet grow (the player's early game).
- **Company name (working): Starfarer Inc.** — Starsector's original title. The joke: spacers
  conflate the company name with his surname, so legend calls him "John Starfarer" and it slides
  toward "John Starsector". (Rejected alternatives: S.E.C.T., Star Son Enterprises.)
- He colonizes several worlds in **Meridian** to settle down and immediately has trouble with the
  Pirates, the Luddic Church and Tri-Tachyon (one colony crisis per faction — the player's mid
  game), and is eventually **forced to join the Persean League** for protection.
- **Note:** 182 → 186 is fast for all of this, but it is about how fast a real player moves, which
  suits the mirror.

### 1.2 The Fall of Meridian (Cycle 186)
- **The discovery.** Exploring far from the Core for artifacts to improve Meridian, Wiggin's fleet
  finds evidence suggesting **Tri-Tachyon** is poking around in the **Abyss** and has found
  "something". Exiles never states what.
- **He "hears the music".** Vanilla canon: the player hears it (the Gates scan, the Ziggurat), and
  Brother Livewell Cotton heard it once, in a prison cell, and took it as confirmation of his
  "righteous calling". Others have heard it and been driven mad. Vanilla never explains it; it seems
  linked to the Abyssal entities. Note the pattern: the music confirms whatever the hearer already
  believes — consistent with the ruling that the threat stays unconfirmed (§1.4).
- **He asks questions in the Core.** Powerful people in Tri-Tachyon become uncomfortable.
- **The cabal** is a group **within Tri-Tachyon** — one of many cabals there; some members may sit
  on the Board of Directors or answer to it. **Barely mentioned in Exiles; the player never roots it
  out or confronts it.** (Vanilla precedent for Tri-Tachyon factions fabricating evidence against
  each other: the Galatia Academy arc's Rayan Arroyo worries that a "junior-exec smelled blood and
  was coming after my operation by manufacturing dirt".)
- **The frame.** The cabal leaks fabricated but compelling evidence that Wiggin is engaged in
  **piracy**, supported by a **rogue AI** (RULED: "not merely AI, but rogue AI"). It pulls enough
  strings to make the **Persean League withdraw its protection** from Meridian, while the
  **Hegemony** sends a task force under **Fleet Commander Tarquin**, with **Commander Cunningham**
  as his second-in-command. ("Fleet Commander" is a vanilla Hegemony post; "Commander" the rank.)
  - **Why the Hegemony bombs (Sean's premise, 2026-10-18):** vanilla never shows the Hegemony
    bombarding anyone (see §5), but a rogue AI core cannot be confiscated because it cannot be
    found, so the protocol is sterilization. Cycle 186 is before Baikal Daud became High Hegemon
    (after the Second AI War, c.194), so a harsher pre-Daud rogue-AI protocol is plausible; today's
    confiscation-only inspections read as a later reform. **Canon by inference only — keep it as
    hidden backstory, never state a policy change in v1 text.**
  - **SUGGESTION (Claude; Sean liked the direction, not explicitly ruled):** the AI core was real —
    Wiggin ran an alpha-core governor like any player — and the cabal turned "AI governor" into
    "rogue AI". The rogue part is the lie, and the only part that justifies the bombs. (Echo:
    Midan's Orbital Works still runs an alpha core, an intentional 2026-07-12 ruling.)
  - **The League's motive (SUGGESTION):** it backed Tri-Tachyon in the First AI War, and in the
    run-up to the Second, no League politician wants to be seen sheltering an AI violator. It also
    gives Midan a second reason to frame the League at Chicomoztoc in 196: tactic **and** revenge.
- **The leak to Wiggin.** The cabal also leaks word of the attack to Wiggin, and hires a
  **Tactistar** mercenary fleet to clean up afterwards. (Tactistar is vanilla's Tri-Tachyon-linked
  military contractor, the fleet behind Tri-Tachyon's colony-crisis attacks on the player.)
  - **SUGGESTION — why leak it:** the cabal cannot let the Hegemony *arrest* Wiggin; a Wiggin in a
    Hegemony cell talks about Tri-Tachyon and the Abyss. Tipped off in a way that makes surrender
    look like death, he fights — and a colony firing on a Hegemony fleet "proves" the charges. Both
    sides act sensibly on what they know; only the cabal sees both halves.
- **The battle.** The task force arrives in 186. Without League support Meridian's defences are no
  match for it, but they refuse to surrender.
  - **Tarquin orders the saturation bombardment of First Haven; Cunningham's ships take part.**
    Civilian transmissions from the surface (women and children pleading) are dismissed as
    fabrications by the rogue AI — Hegemony doctrine taught the crews to distrust the very calls
    that were real. **Vanilla precedent (verified 2026-10-18):** the Galatia Academy outpost
    mission, `$gaOp_variation == ROGUE_AI` (rules.csv rows `gaOpPlanetRogueAI` →
    `gaOpPlanetRogueAIConfront`). A beta core that has seized a research outpost impersonates its
    leader on comms ("eager to become your friend"; "It all feels rather false"); a hidden holo
    from the real, terrified leader reveals the takeover; confronted, "the image of the broadly
    smiling scientist freezes, glitches, and distorts as the AI drops all pretense". The same core
    later speaks of Omega. Note the inversion: in vanilla the *reassuring* call was the fake and
    the *desperate* one was real — at Meridian the crews assumed the opposite.
  - **Tarquin is killed in the fighting.** Cunningham rallies what is left of the fleet.
  - **Wiggin is defeating the Hegemony when the Tactistar fleet arrives** and attacks everyone left
    (inference: the cabal wanted no witnesses on either side). Cunningham's remnant **escapes both Wiggin and
    the mercenaries**. Wiggin, fighting a well-equipped Tactistar fleet on the heels of the
    Hegemony task force, **defeats the mercenaries too**, makes basic repairs, gathers as many
    survivors and as much surviving equipment as he can, and **abandons the system before anyone
    else arrives.** The cost of winning twice is part of why Midan starts poor.
- **Wiggin's wife and children were on First Haven and were killed** (RULED 2026-10-18; "killed"
  stands unless a really good idea comes along — "never found" is held in reserve, but it mainly
  gives other people a lever over him and pulls toward a rescue/reveal twist that undercuts the
  grief).
- **The cabal fails twice.** Wiggin's escape is the big long-term failure. Cunningham's survival
  cost them a few sleepless nights, until the reports showed no mention of the Abyss.

### 1.3 Haven and the name
- Survivors retreat east to a new system; its terran world is named **Haven** after the one that
  died (the generator calls the dead world **First Haven**). They call themselves **Midan** because
  the Core obviously considered them "nothing".
- **The name:** Sean recalls "midan" as an old word for "zero"/"nothing". Closest match: Greek
  μηδέν (*mēdén*, "nothing"), still the modern Greek word for **zero**, pronounced "mee-DHEN".
  "Midan" reads as a spacer corruption of it. By accident it also echoes the Persian/Arabic
  *meydān*, "square, arena, battlefield".
- **Timing (RULED 2026-10-18):** Meridian falls in **Cycle 186**. The Second AI War runs
  **c.189–194** (wiki; the Treaty of Crom Cruach, 194, is in vanilla rules.csv), so Meridian falls
  in its tense run-up, three years before it. The ten years from 186 to 196 are what Midan needs to
  grow strong and organized enough to raid both Nachiketa and Chicomoztoc.

### 1.4 Wiggin now
- **Wiggin still leads Midan:** old, bitter, a little crazy.
- **Terminal certainty needs BOTH sources [Sean, 2026-10-15]:** the Core is meddling in the Abyss,
  and the Core destroyed his people. Certainty alone is emotionally empty (it can as easily produce
  apathy as violence). It may be an excuse, but it is an excuse with strong emotional force behind it.
- **RULED [Sean, 2026-10-18] — Wiggin cannot tell his grief from his reasoning.** The loss of his
  family welded the conviction shut. **Keep the order straight:** he was asking about the Abyss
  *before* First Haven burned, so the conviction came first and the grief hardened it. This keeps
  both sources real and stops Midan collapsing into a revenge plot.
  - Consequences worth using (SUGGESTIONS): when Wiggin argues that the Core must fall, no one,
    Wiggin included, can say whether he means the Abyss or his children, and people who love him
    cannot challenge the argument without seeming to challenge the grief. A man who has already
    lost everything has little reason to care what the Black Fleet does to Core civilians, so the
    tiger's drift is partly his failure too. If he first hears the music *after* the bombing, no
    one can say whether it is the Abyss or a broken man. And his recruitment line to the player —
    framed, imprisoned, stripped of everything — can be "they took everything from you too".
  - **SUGGESTION (unruled; possibly one coincidence too many):** the transmissions Cunningham's
    crews dismissed included Wiggin's family. Wiggin holds the recording; the man now running the
    League war heard those calls and fired anyway; neither knows the other's part at campaign start.
    Lighter version: Wiggin knows his family's calls went out and *assumes* they were heard and
    ignored, and he may be wrong about who heard them.
- **SUGGESTION (Claude) — the chain of reasoning Midan tells itself:** (1) the danger in the Abyss
  is real; (2) the Core's power centres will keep pursuing it — they killed to protect the secret;
  (3) reform is impossible — Meridian asked questions and was burned for it; (4) so the only safe
  Core is a broken one, and a scattered humanity of small colonies (what Meridian was) survives.
  "Destabilization is mercy" = a controlled collapse now instead of extinction later. Why not
  despair: they believe exactly ONE thing can still be done. (Note: Midan blames "the Core", not
  only Tri-Tachyon — the Hegemony fired the bombs and the League walked away.)
- **SUGGESTION — Midan is not of one mind:** believers (the Abyss argument, pure), the grieving
  (revenge wearing the argument), pragmatists (survival). Gives NPC variety; the recruitment pitch
  is strongest in its Abyss form and weakest where it is revenge.
- **RULED [Sean, 2026-10-15] — the threat stays unconfirmed:** the Abyss's dangers are real
  vanilla content — the **Dwellers** and the **Threat** — believed to be end-game material, with
  quests not yet fleshed out (the player can fight them and loot interesting things; for now they
  are simply "there"). That they will "come and kill the Core and everyone else" is NOT proven; in
  Exiles it is a product of John Wiggin's addled mind. Maybe he's right, maybe he's wrong. Exiles
  never settles it and never contradicts vanilla's own Abyss lore.
- **Wiggin is now an urban legend.** He was famous; people still talk about him; half the rumours
  are baloney. Planned: spacer-bar rumours referencing him.

## 2. The Black Fleet and the Mourad title

- Midan was **poor**. The Black Fleet began **out of necessity**: eschewing trade, it raided Core
  commerce for necessary supplies and explored the Sector to retrieve equipment and artifacts.
  (SUGGESTION — the irony: the piracy charge was false in 186; the survivors became what they were
  accused of.)
- **Midan partly depends on the spoils the Black Fleet brings home** — not a one-way supply chain
  from Haven (Sean prefers this to the earlier "proxy only" framing).
- **"Admiral Mourad" is a title, not a man** (the Dread Pirate Roberts model; also historically apt —
  the name Murat Reis passed from an Ottoman admiral to Jan Janszoon, the Dutch convert who operated
  from Salé and seized Lundy, the Black Fleet anchors' namesakes):
  1. **Mourad I — John Wiggin** himself led the first raids.
  2. **Mourad II** — a trusted subordinate he handed the reins to; killed by the Hegemony (consistent
     with the provisional CLAUDE.md origin ruling: an ordinary, effective Hegemony pirate-hunting op).
  3. **Mourad III — Captain Tapper**, who betrayed the player at Nachiketa, under a new identity.
     Whether he knowingly worked for Midan or the Black Fleet in 196 is **undecided**; he certainly did
     not know how deep the rabbit hole went.
- **SUGGESTION:** the tiger was born in Midan's house; its drift toward independence under later
  Mourads is the story, and the founder vs the third man to wear his title is a Phase-2 tension.

## 3. Cycle 196

- **The Nachiketa Incident was Midan's operation, and it FAILED.** Mercenaries (hired indirectly by
  Midan, the player among them) and Black Fleet pirates raided Nachiketa to **steal the Hegemony's
  synchrotron core**; it was destroyed in the raid, causing massive destruction. **Nachiketa was and
  remains Hegemony** (the Word timeline's "independent world" is stale).
- **The Theft at Chicomoztoc** followed (War doc §1.1): Midan stole the Hegemony's pristine nanoforge
  with ships that appeared to be League. (SUGGESTION: Midan learned the false flag the hard way —
  Meridian was destroyed by a frame.)
- **The Hegemony raided Kazeron** (where the League's Orbital Works and pristine forge were) as
  surgically as it could and took the League's forge. This sparked the **open war of 196–198**, in
  which the League failed to retake the forge.
- **The hawks are state politics, not a conspiracy [Sean, 2026-10-15]:** people in positions of power
  in the Hegemony who want the League wiped out. They had a hand in the Kazeron raid as they would in
  any major action against the League, through their positions. **Cunningham is a hawk. He is not a
  member of any cabal** (2026-10-18: the cabal is Tri-Tachyon's alone); he was *played* by one.

## 4. Admiral Cunningham

- **Rank today: ADMIRAL (Sean, 2026-10-24).** He was a Commander at Meridian in 186; twenty years
  on he is an Admiral (vanilla rank `spaceAdmiral`, post Fleet Commander). Sean's reason: vanilla's
  **Admiral Orcus Rao** (`People.java`: rank Admiral, post Military Administrator, admin of **Ragnar
  Complex**) sits in the same system as Skathi (Valhalla), so a mere Commander there would invite the
  question why Rao doesn't pull rank. First name: blank for now. In-game person id `exile_cunningham`.
  - **Lore hook (Claude, noted 2026-10-24; unruled):** vanilla's other Raos, **Neriene and Augusta**,
    live **on Eventide** (the *Sword of Eventide* mission), so Eventide's "old families" already
    include the Raos. Possible use: Cunningham and the Raos as fellow old families, allies or rivals.
    Compatibility note for rung 3: Eventide is home to vanilla mission characters; the bombardment
    leaves the market in place, but check those missions still work after rung 3.
- **Who he is (RULED 2026-10-18):** a member of **Eventide's old nobility** who advanced on the
  support of powerful family connections. He wants to become **High Hegemon** to get the Hegemony
  back on track and out of the hands of "the rabble".
  - **The irony (Sean):** if he succeeded he would essentially be a puppet of the old families he
    owes his career to — or he would pull an Andrada-style purge to avoid that.
- **At Meridian:** second-in-command to Fleet Commander Tarquin. His ships fired on First Haven; if
  pressed, he was only following orders, and the data said the civilian transmissions were the
  rogue AI's fabrications. (Vanilla's bombardment screen carries a quote that is nearly his
  defence: "If it's just followin' orders, it's not me that's going against the convention. Then
  it's on the captain. Right?") With Tarquin dead, the captain cannot answer for it. When Tarquin
  fell, Cunningham's leadership in rallying the remnant and escaping was **legitimately skilled and
  courageous**.
- **His report** — the version of Meridian the Hegemony knows — plays up the Hegemony's heroism and
  downplays its attack on civilians. **RULED: it calls the Tactistar fleet "pirate reinforcements."**
  The lie hides that a third party was in the system and "confirms" the planted piracy charge; the
  cover-up and the frame hold each other up without either side planning it. Cunningham did not
  order the atrocity; his sin is the whitewash. Sean's reasoning: ordering a saturation bombardment
  of an inhabited world is too evil for a character meant to be morally grey.
- **What he learned (RULED):** at first he did not know who hired the mercenaries. As his access and
  resources grew, his own investigations found that **Tri-Tachyon hired them** — but never exactly
  who or why, only that he and his commander were played. **He never learns of the Abyss.**
- **Skathi:** he seized it on some pretext (before Omicron's activation, Cycle 198), partly as
  payback and partly as a fishing trip for whoever used him; vanilla's descriptions rumour secret
  Tri-Tachyon research there. Hegemony higher-ups who want to know what Tri-Tachyon was up to (if
  anything; the sites are unproven) protect him.
- He now **commands the war against the League** — the Hegemony Admiral in charge.
- **Midan ultimately wants him destroyed** — but he is genuinely talented, so that is hard.
- **The Eventide strike (Sean, 2026-10-18):** his reaction to Midan's rung-3 strike on Eventide
  (war doc §6.1) should be "rather dramatic" — it is his home and his patrons' seat.
  - **SUGGESTIONS (unruled):** rung 3 bombards Eventide (size 7 → 6, farmland scorched, mirrors
    wrecked) — a Meridian done to *his* home by the people his ships burned. His rage, aimed at the
    League he believes did it, is the human reason the war peaks at rung 3's General Engagement;
    the forced talks are his patrons pulling him back. Midan's planners pick Eventide for the food;
    Wiggin may want it because it is Cunningham's (whether Wiggin knows his name is undecided).
- **IDEA (Sean, 2026-10-18; unruled) — Cunningham's answer to Eventide:** a daring, warmongering
  raid, potentially on **Kazeron itself**; if it succeeds, peace talks are pushed far down the road,
  perhaps beyond reach. Possibly a quest in which the player can stop him.
  - **Claude's notes:** it repeats the act that started the war (the 196 Kazeron raid), breaking the
    capital rule that grew out of it. It is Midan's plan succeeding beyond its hopes: the false flag
    drives the man Midan wants destroyed into the act most likely to burn the Core. Existing rulings
    put it in **quest** territory, not director behaviour: the war doc's R1-B makes a
    containment-breaking war quest-gated finale content; capitals are exempt and conquest-shielded;
    and the Ashen "Phase 4 fires once" ruling sends any second major assault to quests. Fit: rung 3
    lands in early late-game, so this would be a late-game quest. Director's part: rung 3's intel beat
    (his vow), plus a seam the quest can set — e.g. a memory flag that blocks ceasefires or raises the
    tension floor for N years if the raid succeeds.
  - **RULED (Sean, 2026-10-18): if the player does nothing, Cunningham succeeds** — or at least has
    a really good chance to. Ignoring the quest has a real cost.
- **SUGGESTIONS (unruled, carried from 2026-10-15):**
  - *Why the war is contained:* he needs a simmering war as political capital, not one that risks
    the Hegemony he means to inherit; leadership keeps it contained for pragmatic reasons; the hawks
    below him want more.
  - *The irony:* every Midan strike blamed on the League justifies his hard line and raises his
    standing — Midan and he feed each other's plans unknowingly; exposing the false flag breaks both
    (a Phase-2 spine).
  - *Keep him grey:* a talented, ambitious man whose acts cascaded far beyond intent (Meridian →
    Skathi → Omicron → Gilead → the Ashen Crusade), not a mastermind behind everything.
  - *The cabal's reason:* they genuinely feared what is in the Abyss and buried Meridian to keep it
    secret — Midan and the cabal as two answers to the same fear.
- **Name note:** "Tarquin" evokes Tarquinius Superbus, Rome's last king, overthrown for tyranny.
  Vanilla's random surname list contains "Cunningham" (and "Wiggins"), so generic NPCs may share
  the name; harmless.

## 5. Vanilla research (2026-10-18): the Hegemony and bombardment

Searched vanilla data/code (`starsector-core`) and the wiki. **No confirmed instance of the Hegemony
saturation-bombing anyone, in lore or behaviour.**
- **Code:** `hegemony.faction` sets `caresAboutAtrocities:true` and punitive `canBombard:false`
  (as do the League, Church and Independents; Path, Pirates, Diktat and Tri-Tachyon are `true`).
  `PunitiveExpeditionManager` overrides it only for the territorial case (`if (canBombard || true)`).
- **AI inspections** (`HegemonyInspectionIntel`) end in confiscation, a bribe, a destroyed task
  force, or — evidence but no cores — "great zeal" and disrupted industries. Never bombardment.
- **Rogue AI Core** (`rogue_ai_core`): "operating on or near" the market, plans "unknown"; −1
  stability, Luddic Path immigration; no faction reacts to it mechanically. Dialogue: rogue AIs are
  "extremely dangerous" and "extremely illegal throughout most of the Sector"; Hegemony
  investigators: "Uncontrolled artificial intelligence led to the death of billions."
- **Deniable atrocities near the Hegemony:** **Opis** (Askonia Crisis, c.181) — destroyed by a
  planetkiller during Andrada's intervention; blame traded, "outside agents" alleged; Admiral Rao
  won't "discuss certain mutinous former officers of the Hegemony and their crimes". **Hanan Pacha**
  (Second AI War) — razed by a planetkiller; the investigation "was dropped due to concessions made
  during the peace arrangement". The Hegemony was a bombardment *victim* at Yama (First AI War).
- **Daud** became High Hegemon after the Second AI War (wiki; no exact date).

## 6. Open items

- **NEXT SESSION — Meridian system changes (`ExileMidanOrigin.java`).** Parked brainstorm
  (SUGGESTIONS, nothing ruled):
  - Three battle sites, one per phase: First Haven orbit (bombardment line: Hegemony + Meridian
    wrecks, thickest debris), the Tactistar engagement (Tri-Tachyon high-tech hulls among Meridian
    hulls), Refuge (Starfarer Inc. hulls stripped during the retreat).
  - Wrecks that tell the story: Tactistar hulls where the report says "pirates"; Starfarer Inc.
    crests on Meridian wrecks (vanilla's "Chartery of Opis" ghost ship is the tone to copy);
    Tarquin's flagship as a named, gutted, unrecoverable hulk (Phase-2 log artifact); Wiggin's
    fleet as a mixed player-style collection, possibly with HaT hulls.
  - Engineering: one-time generation in `onNewGameAfterProcGen` (no scripts); vanilla debris
    fields decay by default, so make these permanent (verify the `DebrisFieldParams`); derelicts
    are free ships, so keep the count small, all twenty years weathered with heavy d-mods, no
    capitals unless Sean chooses; **Meridian's `themeHidden` is `false`** in
    `system_placement.json` (every system defaults false) — flip it in the same pass, since it
    keeps random missions, pirate/Path bases and vanilla derelicts out (and salvage traffic away
    from authored wrecks; verify the expeditions honour it).
  - Decisions for Sean: wreck count, whether any are recoverable, Tarquin's flagship class.
  - **Stale code comment:** the `ExileMidanOrigin` javadoc still says "The Core noticed it, tried
    to strongarm them, then invaded and saturation-bombed the colony flat" — update to this doc.
- **Haven retune:** too wealthy, too perfect; must stay wealthy enough to be a threat. Note: the
  2026-06-23 session (`EXILES_KNOWN_ISSUES_AND_NEXT_STEPS.md`) deliberately made Midan self-sufficient
  because universal hostility throttles its accessibility; the new direction answers that with
  dependence on Black Fleet spoils (mechanics later — the BF shared prosperity reserve currently
  benefits only the two BF anchors). Review the **salvaged Domain synchrotron** in the retune.
- **Still to discuss:** the Gaps — how the 196–198 open war ended and why capitals are now
  off-limits; where the League's corrupted forge came from; the hawks and doves; the Diktat's 196
  neutrality (possible first fingerprint of Sean's proposed Midan arrangement).
- **Parked from the war discussion:** the Pass 7 trigger-gate proposal (unruled).
