# Exiles Economy Map

Created: 2026-06-14

Purpose: a code-aware map of **how Starsector's vanilla economy works** and, more importantly for Exiles,
**which economy levers are visible to the player** — so we can make the sector *feel* like it's under
pressure without rewriting the simulation. Distilled from a read of `starsector-core` (data + `starfarer.api`)
and the Exiles source. Companion to `CLAUDE.md` and `EXILES_KNOWN_ISSUES_AND_NEXT_STEPS.md`.

> **Design intent (2026-06-14):** We are **not** overhauling the economy simulation. The goal is to
> **present sector-wide distress to the player** *through the economy that already exists* — pressure on
> NPC colonies, refugees, shortages, instability — so the "Sector is Breaking" pillar reads in-game.
> §4 (the pressure toolkit) is the part that matters most for current work.

---

## 1. The vanilla model in one screen

Starsector's economy is a **declarative-data + imperative-code hybrid**, recomputed every tick:

| Layer | Lives in | Defines |
|---|---|---|
| **Commodities** | `data/campaign/commodities.csv` | id, base price, `econUnit`, export value, tags (~25 commodities: food, ore, supplies, metals, volatiles, drugs, …). |
| **Industries** | `industries.csv` + a **Java plugin** | CSV = cost/upkeep/build time/icon/tags. **The plugin's `apply()` is where supply & demand actually live.** |
| **Conditions** | `market_conditions.csv` + a **Java plugin** | CSV = name/icon/planetary flag. The `script` class applies effects (resource deposits, hazards, stability/immigration). |
| **Markets (colonies)** | `data/campaign/econ/<system>.json` | per-planet: size + starting conditions + industry list. |
| **Constants** | `data/config/settings.json` | pricing curve, stockpile caps, accessibility, growth. |

**Where the knobs live:** *data* says WHAT exists; *code* says HOW it behaves. Adding a commodity/industry/
condition/market is data; changing supply/demand *logic* is Java.

---

## 2. The resolution loop (how supply/demand actually resolves)

1. Each industry's `apply()` declares production and consumption, **scaled by market size**:
   ```java
   supply(Commodities.FOOD, size);              // Farming
   demand(Commodities.HEAVY_MACHINERY, size-3); // Farming's input
   ```
   Quantities are abstract **"market units"** (a log-ish scale tied to size), not literal tons.

2. A commodity's **`available`** on a market = local production + stockpile + in-faction production
   elsewhere + imports (trade mods). `CommodityOnMarketAPI.getAvailable()`.
   *(Simplified — imports come from the single best source and are capped by both sides'
   accessibility; exact formula in §10j.)*

3. **Deficit = `max(demand − available, 0)`** — `BaseIndustry.getMaxDeficit(...)`.

4. **Shortages cascade.** If an industry's *input* is in deficit, `applyDeficitToProduction(...)` cuts its
   *output* proportionally (no machinery → less ore → less refining → …).

5. **Prices** move in a tight band: deficit raises price up to **+25%** (`economyDeficitPriceMultMax`),
   excess lowers it to **−15%** (`economyExcessPriceMultMin`).

6. **Stockpiles** are the only "memory": ~25% of monthly production (`stockpileMultProduction`),
   capped at **5 months** (`stockpileMaxMonths`). Recomputed across **10 ticks/month** (`economyIterPerMonth`).

7. **Stateful hooks:** `EconomyTickListener.reportEconomyTick(int)` / `reportEconomyMonthEnd()`
   (registered via `Global.getSector().getListenerManager().addListener(...)`).

**Structural truth:** the vanilla economy is **stateless and flat** — it's recomputed from current conditions
each tick; stockpiles are tiny buffers, not persistent reserves; prices stay narrow. Local shortages exist
only while a deficit is *live*; they don't accumulate. **We work with this, not against it** — pressure is
expressed by *applying conditions / driving deficits / pushing intel*, not by simulating reserves.

---

## 3. Key API reference (package `com.fs.starfarer.api...`)

**Core econ interfaces** (`campaign.econ`):
- `EconomyAPI` — `advance()`, `forceStockpileUpdate(market)`, `addUpdateListener(...)`.
- `MarketAPI` — `getCommodityData(id)`, `getConditions()`, `addCondition(id[, source])`/`removeCondition(id)`,
  `getIndustries()`/`addIndustry(id)`, `getStability()` (MutableStat), `getSize()`, `addImmigrationModifier(...)`.
- `CommodityOnMarketAPI` — `getAvailable()`, `getStockpile()/setStockpile(f)`, `getDeficitQuantity()`,
  `getExcessQuantity()`, `getPlayerSupplyPriceMod()`/`getPlayerDemandPriceMod()`, `addTradeMod(...)`.
- `CommoditySpecAPI` — static props: `getBasePrice()`, `getEconUnit()`, `getDemandClass()`.

**Industry hooks** (extend `impl.campaign.econ.BaseIndustry`):
`apply()` / `unapply()`, `advance(days)`, `supply(id,q)` / `demand(id,q)`, `getMaxDeficit(...)`,
`applyDeficitToProduction(idx,deficit,...)`, `modifyIncoming(market,incoming)` for growth effects.

**Condition hooks** (extend `impl.campaign.econ.BaseMarketConditionPlugin`, or `...Plugin2` for stateful):
`apply(id)` / `unapply(id)`, `advance(days)`, `getName()`, `getIconName()`, `createTooltip(tooltip,expanded)`,
`getTokenReplacements()`, `getHighlights()` / `getHighlightColors()`.

---

## 4. The pressure toolkit — making distress VISIBLE (the important part)

Seven player-facing levers, ranked from local to sector-wide. Each is what the player actually *sees*.

| Lever | API | Player sees | Saved? |
|---|---|---|---|
| **Market condition** | `market.addCondition(id[, source])` | Named, icon'd entry in the colony's condition list + tooltip | Yes |
| **Stability modifier** | `market.getStability().modifyFlat(modId, amt, "reason")` | A line in the stability breakdown tooltip (red, with reason) | Yes (with modifier) |
| **Commodity deficit** | reduce supply / `setStockpile(...)` so `demand > available` | Red "needs N more" markers in the commodity tab | Yes |
| **Immigration modifier** | `market.addImmigrationModifier(MarketImmigrationModifier)` | Slower colony growth (Population tab) | Yes |
| **Floating message** | `getCampaignUI().getMessageDisplay().addMessage(text[,color,...])` | Brief overlay, lower-left | No |
| **Intel entry** | `getIntelManager().addIntel(intel)` / `queueIntel(intel, daysMax)` | Intel-tab entry, optional comm-relay arrival | Yes |
| **Sector crisis** | custom `BaseEventIntel` (à la `HostileActivityEventIntel`) | Big intel with progress bar, affected markets, stages | Yes |

### 4a. The gold-standard template: vanilla Food Shortage

This is the cleanest example of "drive a visible, dynamic distress signal" and is the model to copy.

- **Condition:** `event_food_shortage` → plugin `impl.campaign.econ.FoodShortage`.
- **Driver:** `impl.campaign.events.FoodShortageEvent` (a `BaseEventPlugin`) monitors food, then on start:
  ```java
  market.addCondition(Conditions.EVENT_FOOD_SHORTAGE, this);
  CommodityOnMarketAPI com = market.getCommodityData(Commodities.FOOD);
  com.setStockpile(com.getStockpile() * getStockpileMult());      // cut available food
  com.getPlayerSupplyPriceMod().modifyMult(ID, FOOD_PRICE_MULT);  // 1.5x price
  ```
- **What the player sees** (`FoodShortage.apply(id)`):
  ```java
  market.getStability().modifyFlat(id, -event.getStabilityImpact(), "Food shortage");
  ```
  Stability penalty scales with size (−1 at size 8–9 … −4 at size 0–1). Tooltip uses
  `getTokenReplacements()` to show `$stabilityPenalty` and `$neededFood`, highlighted via `getHighlights()`.
- **Announcement:** reported as an event stage / intel so the player gets a comm-relay notification.

**Exiles already does the lightweight version of this** — `exile_refugee_influx` is a condition with a
−2 stability hit and an immigration effect. The food-shortage pattern shows how to make it *dynamic and
announced* rather than statically applied at game start.

### 4b. Sector-wide pressure: Intel & Events

For "the whole sector is straining," vanilla uses the **Hostile Activity** framework
(`impl.campaign.intel.events`): `HostileActivityEventIntel` (a `BaseEventIntel`) with a progress bar and
per-faction `HostileActivityFactor`s that ratchet a crisis through `START → MINOR_EVENT → HA_EVENT` stages.
A modder can either plug a factor into this, or stand up a custom `BaseEventIntel` for an economic crisis
and `getIntelManager().addIntel(...)` it. This is the natural home for a future "Sector Destabilization
Director" (see `CLAUDE.md` §6).

### 4c. Practical recipes for Exiles

- **"This colony is straining under refugees":** keep `exile_refugee_influx` as a condition, but drive it
  *dynamically* (apply/scale via a tick listener) and add an intel ping when it lands on a new world, so the
  player notices the spread — not just a static −2 at game start.
- **"The sector is short on X":** apply a shortage-style condition to several NPC colonies (food, fuel,
  supplies) and let the real deficit show red in the commodity tab. Cheap, very legible, ties to lore
  (Nachiketa fuel collapse, Gilead food loss).
- **"Things are getting worse over time":** an `EconomyTickListener` (or `BaseEventIntel` with stages) that
  escalates how many colonies carry distress conditions, with comm-relay intel at each escalation.

---

## 5. How Exiles plugs into all this today

Exiles is already a heavy economy modder. Inventory:

**14 custom industries** (`data/scripts/industries/`, all extend `BaseIndustry`):
- **Ashen war-economy suite** — `exile_ashen_mining / _refining / _light_industry / _heavy_industry /
  _orbitalworks / _fuel_production` — all flex under the `exile_crusade_mobilization` condition (more output,
  more crew/drug demand). The most developed economy content.
- **Farming chain** — `exile_farming_1/2` (gated by `exile_fecund_soil`), `exile_gilead_farming`
  (penalized by `exile_corrupted_soil`).
- **Military** — `exile_ashen_patrol_hq / _military_base / _high_command`, `exile_kol_garrison`.
- `exile_algae_processing`.

**6 custom conditions** (`data/scripts/marketconditions/`):
- `exile_refugee_influx` (BaseMarketConditionPlugin) — stability −2, immigration boost (refugee pressure).
- `exile_fecund_soil` / `exile_corrupted_soil` (gating, no direct effect).
- `exile_luddic_unrest` — stability −3, immigration penalty.
- `exile_aggressive_megafauna` / `exile_crusade_mobilization` (BaseHazardCondition) — stability −2;
  the latter also unlocks/boosts the Ashen industries.

**Faction econ files:** `data/campaign/econ/exilemidan.json`, `exileashencrusade.json` (+ `economy.json` registry).

**ModPlugin market patches** (`ExilesModPlugin.onNewGameAfterEconomyLoad`): Nachiketa fuel nerf, Gilead
gutting (unrest + corrupted soil + KoL garrison), Asher fortification, corrupted-nanoforge worlds
(Kazeron/Kapteyn/Maxios), refugee station squatters (Asharu/Mairaath/Yma/Maxios, size 3), refugee personnel.

**Gap vs. the roadmap:** the planned Refugee subsystem industries — **Processing Center**, **Security
Center**, **Salvaging** — don't exist yet. `exile_refugee_influx` applies pressure but nothing lets a colony
*respond* to it. That's the natural next build, and §4 is the toolkit for making the pressure legible.

---

## 6. Gotchas (carried from CLAUDE.md)

- **Janino-safe Java only** — no lambdas/streams/`var`/diamond. Anonymous inner classes, explicit types.
- **`data/` is source of truth;** never edit `out/production/Exiles/`.
- **Starsector loose JSON** allows unquoted keys, trailing commas, `#` comments — but unbalanced braces fail.
- **One class can back several industry IDs** (e.g. `ExileAshenMilitaryBase` → patrol HQ / base / high command).
- **Copy real API usage** from `starsector-core\starfarer.api\com\fs\starfarer\api\impl\campaign\econ\`
  (esp. `FoodShortage`, `BaseIndustry`, `BaseMarketConditionPlugin`) rather than inventing signatures.

---

## 7. Empirical findings — refugee-pressure tests (2026-06-14)

Hard-won, in-game-verified facts about expressing economic pressure. **Do not re-litigate these.**

### 7a. How vanilla Food Shortage actually works
- `event_food_shortage` is a **near-blank condition** (`FoodShortage`): it does only a **stability** hit and a
  reputation stat (selling food here earns more rep). It touches **neither supply nor demand.**
- The real work is a **driving event script** (`FoodShortageEvent`) that does a **one-time** `setStockpile(× mult)`
  + `getPlayerSupplyPriceMod()` bump, babysits a relief-fleet narrative, lets the player end it by selling food,
  then **reverts** the pokes. Its transience is **by design** (it's an event with a lifecycle), not a limitation.
- **Causation is backwards from intuition:** the shortage is *triggered by* an existing food deficit; the
  condition is the visible **symptom**, not the cause.

### 7b. What a *condition* can and cannot do to the economy (tested)
- **Inject demand → IGNORED.** `getCommodityData(c).getDemand().getDemand().modifyFlat(...)` has **no effect**;
  demand is rebuilt from industries each tick and the stat-mod isn't part of that rebuild.
- **Reduce `getAvailableStat()` → BUGGED.** It's applied **after the import calculation**, so it subtracts from
  the final number without telling the importer "we need more" → a frozen, unrealistic short.
- **Price mods → CLEAN & PERSISTENT.** `getPlayerSupplyPriceMod()` / `getPlayerDemandPriceMod()` are `StatBonus`
  modifiers read at price-query time; they persist until `unmodify(id)`, exactly like the stability mod.
  **This is the only reliable economy lever a condition has.**
- **Demand/supply belong in an Industry/structure `apply()` via `supply()/demand()`** — the only engine-honored path.

### 7c. THE killer constraint — demand is max-not-sum
**A market takes the LARGEST single demand source per commodity and assumes lesser ones are covered — it does
NOT sum them.** So an added demand only matters if it **dominates** that commodity's demand on that colony.
Corollary: the "apply a little refugee demand to many colonies" plan **does not work**; each instance must
out-demand everything else on its market to register. (This is also why the original Crusade-Mobilization-as-a-
condition went weird — see §6 history.)

**The producer side is symmetric, and the scope is the whole econ group — not one colony (verified 2026-06-23).**
Production is **stateless** (exporting a commodity doesn't deplete it), so the engine also counts only the **single
largest producer** of each commodity — a *second* producer of the same good adds **nothing**. So the self-sufficiency
test for a faction / econ-group is, per commodity: **largest single producer ≥ largest single consumer, anywhere in
the group.** *(Refined 2026-10-10: "largest producer" means largest `min(production, shipping capacity)` —
accessibility caps exports and imports; see §10j.)* Meeting the biggest demand covers every smaller one automatically. Corollaries that matter for balancing:
1. You fix a deficit by **raising the one biggest producer or lowering the one biggest consumer** — never by adding
   more producers (a common, wrong instinct).
2. **Growing a colony that carries a `megaport` is self-defeating** for supplies/fuel/ships: megaport demand = `size`
   scales up with the colony, while the matching producers (orbital works supplies / fuelprod fuel = `size−2`) stay 2
   behind. You can't outgrow the gap.
3. Flat **production items** are the clean way to close that 2-unit gap — **Synchrotron (+3 fuel)**, **pristine
   nanoforge (+3 ships)** — but **there is NO supplies- or drugs-boosting item** in vanilla, so those two commodities
   are structurally the hardest to make self-sufficient (drugs especially: light industry `size−2` trails mining's
   drug demand `size` by 2 at equal size). This is why supplies/drugs make natural "intended-struggle" commodities.

### 7d. A/B test: paint (condition price-mod) vs simulate (structure demand)
Two prototypes, separate markets, both stable in-game:
- **`exileashen_steadfast`** — `exile_refugee_influx` with **+25% buy-price** on food/supplies (paint). Result:
  Supplies **129**, Food **10**. The price moved reliably — but supply/demand state was **untouched** (no deficit,
  no machinery, no ripple, no self-attribution to refugees). *Caveat: steadfast was already supplies-poor +
  food-overproducing, so part of the effect is the market, not the mechanism.*
- **`exileashen_command_bastion`** — `exile_refugee_population` **structure** with real `demand(FOOD, size+3)` /
  `demand(SUPPLIES, size+2)`. Result: Supplies **85** (orbitalworks surplus → −15% floor), Food **20** (base —
  imported with ample availability). The demand was **absorbed** by max-not-sum + the sector importing the gap away.
  *It was tested in the two worst spots for it to show: a supplies-producer and a freely-supplied food importer.*

### 7e. Verdict / design takeaway
- **You cannot manufacture scarcity from the demand side at colony scale.** max-not-sum + sector imports erase it
  unless refugee demand dominates the market AND the commodity is import-constrained sector-wide.
- **Real, felt scarcity is supply-side and sector-wide** — food gets expensive *everywhere* only when total
  *production* drops. Exiles **already builds this** (Gilead gutted, Nachiketa nerfed, devastated farming removed).
  *That* is the "Core under dangerous strain," and it's real/emergent/machinery-driven.
- **Refugees are best as the visible human marker + local amplifier, not the engine of scarcity:** price-mod
  **paint** for guaranteed "this colony is hurting," and a **dominant-demand structure** only on the few worlds
  meant to be refugee-overwhelmed.
- **Paint's weakness:** it changes the *price number*, not the colony's *state*, and doesn't tell the player
  *"refugees did this."* The real unlock for player attention + attribution is likely the **missions / bar-events /
  intel** layer on top of a genuinely supply-short sector — not either economy lever alone.

### 7f. Current in-mod test state — SUPERSEDED, see §8f.

---

## 8. The resolution — scarcity is an EVENT (2026-06-15)

Continuation of §7. We finished testing the remaining levers and found the answer. **This section is the
canonical conclusion; read it before touching refugee economics.**

### 8a. The accessibility lever (ShippingDisruption) — tested, then repurposed
`market.getAccessibilityMod().modifyFlat(id, -penalty, ...)` is the live lever behind vanilla `ShippingDisruption`
(the other two — `Smuggling`, `TradeDisruption` — are `Unused` vanilla stubs). It throttles the colony's whole
trade throughput, so it sidesteps max-not-sum (it starves the demand the colony *already has*). Tested at −40% on
`exileashen_steadfast` (dropped accessibility to ~13%): it works, but the effect is **two-sided and blunt**:
- **Imports** above the throttled cap (crew/drugs/marines) → scarce, prices **spike**. On-theme.
- **Exports** of overproduced goods (food/volatiles/domestic) → can't leave → local **glut**, prices **crash**. Off-theme.
- It hits **all** commodities; you can't target food/supplies; and it **missed** the intended target (Supplies sat under the cap).

**Verdict:** wrong tool for *refugee influx on Core worlds* (refugees don't sever a world's trade lanes, and the
export-glut reads backwards). **Right tool for a colony the sector *shuns*** — so it was repurposed as
**`exile_pariah_colony`** (`ExilePariahColony`, −25% accessibility, "barely scraping by because no one will trade
with them"), placed on the 4 refugee colonies. Bank the same mechanic for raids/blockades/war (the Director).

### 8b. THE breakthrough — scarcity must be an EVENT, not a steady condition
Every condition lever fought us (§7b/§7c/§8a) because we kept asking a *steady-state condition* to produce
scarcity. The grep that cracked it: vanilla's **commodity raid** creates a real shortage with
```java
com.getAvailableStat().addTemporaryModFlat(ShippingDisruption.ACCESS_LOSS_DURATION, Misc.genUID(),
                                           "Recent raid", -deficitActuallyCaused);
```
Two things: it's **temporary** (`addTemporaryModFlat`, self-healing), and it's **sized to the actual deficit**.
**This refines §7b:** `getAvailableStat()` reduction is NOT bugged — it's the genuine shortage lever. Your earlier
permanent `modifyFlat(-5)` "felt bugged" because you used a **shock mechanism as a permanent modifier**.

> **The pattern:** *every* scarcity mechanism in the engine is a **temporary shock that recovers** — Food Shortage
> (stockpile slash + relief), commodity raids (temp availableStat mod), Shipping Disruption (temp losses). There is
> **no clean permanent extra-scarcity from a steady condition**, because permanent scarcity simply *is* the colony's
> normal industry-driven supply/demand. Anything layered on top is, by design, an **event**.

### 8c. The working prototype — `RefugeeShortageScript` (CONFIRMED WORKING)
`data/scripts/exiles/RefugeeShortageScript.java`, an `EveryFrameScript` registered in `ExilesModPlugin.onGameLoad`,
currently targeting `jangala`. Lifecycle: wait → **shortage** (`addTemporaryModFlat` cuts Food −8 / Supplies −6,
buy-price ×1.5, comm message) → player can **relieve** by selling food/supplies (detected via
`getPlayerTradeNetQuantity()`) → **+rep with `exilerefugee`**, or it expires → cooldown → repeat.

**In-game result (works):** real deficits with red markers, cascading shortages into dependent industries
(drugs/fuel went short too), prices spiked, recovers. This is THE gameplay-layer solution: scarcity that is
**real + visible + interactable + faction-attributable** in one mechanic.

**Cautions surfaced by the working test:**
- **Food → 0 availability triggers vanilla's food-deficit stability penalty** (observed −6). Repeated full food
  collapse will **decivilize** NPC worlds. Design rule: shortages should usually be **partial deficits**, not
  collapse; reserve full food-zero for rare, severe, reliably-relieved beats.
- **No conditions-panel icon** because the script adds no `MarketCondition`. Fix in the real version: the event
  should `addCondition("exile_refugee_shortage")` (a near-blank marker, like vanilla `event_food_shortage`) for the
  icon + a relief-progress tooltip.
- **Relief message lacks an amount.** Track "units still needed" and surface it (vanilla's `$neededFood` pattern).
- Prototype is single-market, hardcoded target, state not persisted, relief detection approximate.

### 8d. Forward design directions (decided in principle, not yet built)
- **The pipeline (= Phase 1 Sector Destabilization Director):** random pirate-raid-style **refugee-fleet arrival**
  ("the fleet you didn't turn away") → applies **Refugee Influx** condition (standing marker + eligibility) → a
  **director script periodically rolls shortage events** on influx-flagged worlds (severity/cadence tunable).
- **"Poor / Impoverished" condition** for the refugee colonies: suppress **`getPlayerDemandPriceMod`** (the price
  the colony pays the player when selling *to* them) → they're desperate for goods but **can't pay** → "help them,
  but not for profit." The one price lever still unused; clean/persistent; **composes with `exile_pariah_colony`**
  (pariah = isolated/scarce, Poor = broke). Strong morally-grey fit.

### 8e. Consolidated lever reference (the whole investigation in one table)
| Lever | Shape | Verdict |
|---|---|---|
| `demand()` from a structure | steady | Real, but **max-not-sum**: only bites if it dominates the market. |
| demand-stat modify from a condition | steady | **Ignored** by the resolver. Dead. |
| `getAvailableStat().modifyFlat(-x)` permanent | steady | Reads broken (no import response). Wrong *shape*. |
| `getAvailableStat().addTemporaryModFlat(...)` | **event/shock** | ✅ **Real, recovering shortage. The answer.** |
| `getPlayerSupplyPriceMod` (buy price) | steady | Reliable but cosmetic; no state change/attribution. |
| `getPlayerDemandPriceMod` (sell-to price) | steady | Unused — the "Poor colony" lever (§8d). |
| `getAccessibilityMod` (throughput) | steady | Real but blunt/two-sided; wrong cause for influx, **right for pariah** (§8a). |
| stockpile slash (`setStockpile`) | event | Vanilla Food Shortage; transient by design. |

### 8f. Current in-mod state (supersedes §7f)
> **STALE (2026-06-18):** superseded by the latest session — relation-scaled `exile_refugee_influx`, retired
> `exile_refugee_population`, the two mitigation centers, and `exile_refugee_strain` on 5 NPC worlds. See the
> project-memory "LATEST SESSION" note; formal §8f rewrite held pending Sean's in-game test.
- `exile_refugee_influx` (condition): −2 stability, +growth, +25% food/supplies buy-price. **Applied nowhere** now.
- `exile_refugee_population` (structure, `ExileRefugeePopulation`): real food/supplies demand. On `exileashen_command_bastion`.
- `exile_pariah_colony` (condition, `ExilePariahColony`): −25% accessibility. On the 4 refugee colonies (via `ExilesModPlugin`).
- `RefugeeShortageScript`: the working shortage-event prototype, registered in `onGameLoad`, targeting `jangala`.
- All still **experimental** — the shortage event is the keeper; the rest awaits the §8d direction + tuning.

## 9. Faction visibility & the Nexerelin market census — why refugee colonies must be JSON-declared (2026-06-16)

This section came out of the "Refugee faction doesn't appear in the in-game factions/intel list" bug. The
investigation produced a general, reusable rule about how faction visibility and Nexerelin diplomacy actually work.

### 9a. The two regimes (they answer the same question differently)
| | Vanilla game (no Nex) | Nexerelin game |
|---|---|---|
| What gates the Factions intel tab | static `showInIntelTab` flag in the `.faction` file | a runtime **market census** — Nex overrides the static flag |
| Diplomacy participation | n/a | granted only to factions the census counts |

- **Vanilla:** the Factions tab is gated purely by `showInIntelTab` in `data/world/factions/<id>.faction`. Exiles
  refugee + omicron had it `false` (inherited from a neutral-faction template); Ashen `true`, Midan omitted→default
  `true`. That 4-for-4 correlation IS the vanilla bug. One-line fix: set it `true`.

### 9b. The Nexerelin census (verified from ExerelinCore source)
`SectorManager.reinitLiveFactions()` runs in **`onNewGameAfterEconomyLoad`**. For each faction it checks:
> "Does this faction **own ≥1 market right now**?" AND "is its Nex config `playableFaction != false`?"

If yes → add to `liveFactionIds`, `createDiplomacyProfile(...)`, and `setShowFactionInIntelTab(id, true)` — which
literally calls `faction.setShowInIntelTab(true)`, **overriding the static flag**. If no market → forced hidden + no
diplomacy. It does **not** re-run on `onGameLoad` (no self-heal). The only later add-path is `factionRespawned`,
triggered by market-transfer events — which an isolated, script-added colony never fires. **Missed = permanently
missed.**

**CRUCIAL — the `playableFaction` default is a trap (verified 2026-06-16).** For a faction with **no Nex config**,
`NexConfig.getFactionConfig()` returns `defaultConfig`, which is the **neutral faction's** config — and Nex's
`neutral.json` is `playableFaction:false`. So an unconfigured faction with a market hits `!playableFaction` → **`continue`**
→ skipped entirely: no `liveFactions`, **no diplomacy profile**, and `setShowFactionInIntelTab` is never called so the
static `showInIntelTab` flag governs (refugees were `false` → hidden). This is exactly why Asharu's market worked
(colony, comm officers, trade) but the faction was absent from the Factions tab **and** silently excluded from Nex
diplomacy. The field-level default (`playableFaction = true`) only applies when a config FILE exists but omits the key —
it does NOT apply to a faction with no file. **Fix: ship `data/config/exerelinFactionConfig/<id>.json` with
`playableFaction:true`.**

### 9c. Why load-time scripting loses the race
Exiles also adds markets in `onNewGameAfterEconomyLoad`, and declares **no** Nexerelin dependency → the order of the
two plugins' hooks is **nondeterministic**. If Nex's census runs first, the refugee markets don't exist yet → refugees
are excluded for the whole game. A `createMarket`+`addMarket` (or a post-econ-load faction *reassignment*) is
fundamentally racy here.

### 9d. The fix — declare ownership at econ-load via the JSON-append trick
Econ-JSON load happens **before any plugin hook**, so a market declared there with `"faction":"exilerefugee"` baked in
is owned in time for the census, **load-order-proof**. For colonies sitting on **vanilla entities in vanilla systems**:
- A mod file at the same path as a vanilla econ file (e.g. `data/campaign/econ/corvus.json`) **appends** its
  `markets[]` entry to vanilla's (JSON/CSV append; `.java` overwrites). Vanilla `economy.json` already lists
  `corvus.json`, so no change to the mod's `economy.json` is needed.
- A **slim post-econ-load script** then only finalizes the physical token (set faction/name) + cosmetic cleanup
  (drop the free starter storage/Hermes). It does **not** create the market.
- **Division of labor:** system `.java` CREATES the entity (token/position/graphics); econ `.json` makes it a
  faction-owned MARKET. Both are required; neither alone is a live colony.

### 9f. The registration gate — `mod_factions.csv` (verified 2026-06-16)
Adding `exerelinFactionConfig/<id>.json` is **not enough on its own**. Nex only builds `NexFactionConfig` objects for
faction IDs it knows about — `builtInFactions` (its own) + `supportedModFactions`, the latter read from a merged
spreadsheet **`data/config/exerelinFactionConfig/mod_factions.csv`** (column `faction`, one ID per line, case-sensitive,
`#` comments ok). The census iterates `getFactions()`, which only includes factions that have a **loaded config** —
so a faction absent from every `mod_factions.csv` is never even considered, regardless of its markets or a stray
`<id>.json`. **Every Nex-compatible mod here ships this file** (Interstellar Imperium's is literally `faction\ninterstellarimperium`).
Full required set for one mod faction under Nex: (1) entry in `mod_factions.csv`, (2) `<id>.json` with
`playableFaction:true`, (3) a market owned at econ-load. Note: Ashen/Midan/Omicron are also absent from this file →
they too currently get the neutral default (visible only via their static `showInIntelTab`, but no Nex diplomacy
profile). Wiring them is separate scope.

### 9e. Status (2026-06-16)
- Diagnosed as a **regression**: the working `corvus.json` + ~15-line script (preserved in `Exiles_backup_5_30_26`)
  was replaced with an all-script build (`createMarket` + ~450 lines of personnel/comm machinery), which severed the
  census.
- **Step (a) done & verified in-game:** `corvus.json` + `convertAsharuRefugeeStation()` → Asharu is a live,
  refugee-owned market with auto-generated comm officers, working under Nexerelin. Confirms the JSON-append pattern AND
  that the personnel/comm machinery is **not needed** (JSON markets auto-staff).
- **Step (a) follow-up #1 — added the Nex config**, but the Factions tab was *still* empty. Root cause went one level
  deeper (see §9f): a config file is inert unless the faction is **registered** with Nex via `mod_factions.csv`.
  Neither current Exiles nor the backup ever had that file, so **refugee Nex visibility/diplomacy was never working —
  it wasn't a regression, it was never wired.**
- **Step (a) follow-up #2 — added `data/config/exerelinFactionConfig/mod_factions.csv`** listing `exilerefugee`, which
  makes Nex load `exilerefugee.json` (`playableFaction:true`).
- **✅ CONFIRMED IN-GAME (2026-06-16):** with `showInIntelTab` still `false`, refugees now appear in the Nex Factions
  tab → the census counted them (overrode the false flag) → diplomacy profile is present. The full three-part recipe
  (mod_factions.csv + `<id>.json` playableFaction:true + market owned at econ-load) is validated. Asharu is the proven
  template; ready to scale to the other three.
- **Step (b) — JSON appends DONE (2026-06-16):** created `mayasura.json` (Mairaath), `yma.json` (Yma), `magec.json`
  (Maxios). All entity IDs verified against vanilla world-gen. **Maxios** is a vanilla *barren planet* — its append
  reproduces the exact conditions `Magec.java:75-83` applies (`decivilized`, `ruins_extensive`, `meteor_impacts`,
  `ore_moderate`, `rare_ore_sparse`, `hot`, `thin_atmosphere`) + `population_3`, so the refugee market doesn't strip the
  planet's vanilla identity. Mairaath/Yma carry placeholder station conditions (Sean to polish). The script's slim
  `convertAsharuRefugeeStation` was generalized to `convertRefugeeColony(entityId, name)` and now finalizes all four; the
  old heavy `setup*`/`configureRefugeeMarket`/personnel methods are no longer called (dead code, left for a cleanup pass).
- **✅ CONFIRMED IN-GAME (2026-06-16):** all four refugee markets (Asharu/Corvus, Mairaath/Mayasura, Yma, Maxios/Magec)
  live and functional under Nexerelin via the JSON-append + `convertRefugeeColony` pattern. The refugee market layer is
  fully re-homed.
- **Dead-code cleanup DONE (2026-06-16):** removed the orphaned heavy refugee-market + personnel cluster from
  `ExilesModPlugin` (449 lines: `setup*`/`configureRefugeeMarket`/all personnel helpers/condition+industry helpers +
  the `REFUGEE_PERSONNEL_DONE` key). Verified no residual references + balanced braces; needs a rebuild to confirm
  compile. The mod is now under Git (`github.com/Moffhul/Exiles`), so this landed as its own revertible commit.
- **Step (b) remaining:** flip
  `showInIntelTab:true` (pure-vanilla case), tune the Nex config's diplomacy/invasion semantics (refugees shouldn't
  launch invasions: `specialForcesMaxFleets:0` etc.), and delete the dead misspelled
  `data/config/exerelineactioneonfig/player.json`. Ashen/Midan/Omicron still need their own `mod_factions.csv` + config
  wiring for Nex diplomacy (separate scope).
- **Custom market graphic TODO:** the station still shows the vanilla `$abandonedStation` market illustration; replace
  with custom art for the refugee/scavenger society.
- **Nex random-core mode is incompatible** (vanilla entity IDs don't exist there) and Exiles' whole vanilla-Core
  narrative depends on the corridor sector → corvus-mode only; must be advertised to players. See KNOWN_ISSUES.

---

## 10. Vanilla mechanics reference (researched 2026-06-18)

Findings from investigating Free Port, NPC economics, officers, and bar events while building the refugee
subsystem — recorded so future work doesn't re-derive them.

### 10a. Free Port (`free_market` condition / `FreeMarket`)
On an **NPC** colony it starts at MAX immediately (verified): **−3 stability, +25% accessibility, +3→+10 growth**,
injects **+5 Pirates / +5 Poor / +5 Independent** into immigration, **+0.25 mercenary-officer probability**, **all
commodities legal + no transponder for open trade**, income from smuggled exports, and **requires a spaceport** (auto-
removes without one). Indirect (keyed off the flag): flags the colony for **`ANTI_FREE_PORT` punitive expeditions**
(weight ∝ size), **no customs scans**, and **weights some bar events** (e.g. `PlanetaryShieldBarEvent` +10). On
**player** colonies these RAMP over 365 days (−1→−3 / +5%→+25% / +3→+10); NPC colonies start maxed. The immigration
mix is **inert** (10c) — the "rougher crowd" feel is the merc officers + bar-event weighting, not the population.
*(Drove dropping Free Port from the refugee colonies — Strategy B; see KNOWN_ISSUES.)*

### 10b. NPC colony growth & income — both inert
NPC market **size is STATIC**: `CoreImmigrationPluginImpl.increaseMarketSize()` bails on `!market.isPlayerOwned()`, so
NPC colonies accumulate population *weight* but never change size. NPC **income** is computed but **unused** (the
monthly report skips non-player markets; NPC fleet production runs off a separate capacity). So growth bonuses and
income do nothing on NPC colonies.

### 10c. Population composition — vestigial
The per-faction population shares are **written** by many modifiers but **read by almost nothing**. `LuddicMajority`
triggers off industries/habitability, **not** population. Not load-bearing for crime, raids, stability, or flips.

### 10d. Officers for hire — condition-adjustable
Generated when the **player opens a market** (`OfficerManagerEvent.reportPlayerOpenedMarket`), driven by four dynamic
market stats: `Stats.OFFICER_PROB_MOD`, `OFFICER_ADDITIONAL_PROB_MULT_MOD`, `OFFICER_IS_MERC_PROB_MOD`, `ADMIN_PROB_MOD`.
A **market condition can adjust them** via `market.getStats().getDynamic().getMod(STAT_ID).modifyMult/modifyFlat`. Used
by `exile_destitute_population` to suppress officers (×0.1) and mercs (×0), invisibly.

### 10e. Bar events — add custom, don't modify vanilla
**Add** via `data/campaign/bar_events.csv` (row → a `PortsideBarEvent` plugin) or
`BarEventManager.getInstance().addEventCreator(...)`. **Gate** per-market via the event's `shouldShowAtMarket(MarketAPI)`
(check faction, `hasCondition`, stability, size) and globally via the creator's `getBarEventFrequencyWeight()`.
**Modifying vanilla bar events is fragile — don't.** Add custom ones gated on a refugee condition. *(This is the path
for the planned "seedy refugee atmosphere" bar events — Phase 1 / polish.)*

### 10f. Random exploration/survey missions — not faction-tunable
`SurveyPlanetMissionIntel` / `AnalyzeEntityMissionIntel` (auto-generated by `GenericMissionManager`): reward is
distance-only (no faction multiplier), and there is **no faction flag to disable them**. See the deferred polish item
in KNOWN_ISSUES.

### 10g. Colony Accessibility — base, distance, hostility (verified by decompile 2026-06-23)
Accessibility = additive stack of flat mods on `market.getAccessibilityMod()`, then `computeEffective`, clamped ≥0.
Core terms (from `settings.json` "reach economy" block, applied by the obfuscated `CommodityMarketData`): **base 0.5**;
a **distance** penalty linear to **−1.0 at 50 LY** from the econ center-of-mass (`accessibilityDistFromCOM`); a
**hostility** penalty up to **−1.0** (`accessibilityLossWhenAllHostile`). Readable API impl classes stack on top:
colony size (+0.1→+0.3), no-spaceport (−1), spaceport/megaport tier, **Free Port (+0.25)**, gravity, blockade,
hostile-activity events, alpha core, player skills.
- **Hostility is a binary yes/no per faction, NOT scaled by relation depth.** The test is `factionA.isHostileTo(factionB)`
  → true only at RepLevel **HOSTILE or worse (≈ −50)**; Suspicious (−24) / Inhospitable (−40) count as **zero**, same as
  Neutral or Cooperative. Crossing −50 flips it fully on; there is no gradient.
- **Magnitude is size-weighted** — not by relation score, not raw colony count. Each market weighs `max(1, size−2)`;
  penalty = `1.0 × (combined weight of every faction hostile to YOUR faction) ÷ (combined weight of ALL factions in the
  econ group)`. Your own faction is in the denominator but never counts against you.
- **Distance and hostility are independent additive terms** — being remote does NOT soften the hostility hit, or vice
  versa. *(This is why the universally-hostile Midan are accessibility-throttled; levers: Free Port, larger colony size,
  or econ-group isolation — 10h.)*

### 10h. Econ-group isolation (`MarketAPI.setEconGroup`)
Partitions markets into trading bubbles (default `null` = the regular shared economy). **Vanilla DOES use it** — pirate &
Luddic-Path bases each call `setEconGroup(getId())`. Use a **shared** group name across a faction's markets to make them
one internal economy that trades only with itself. Every consumer traced (2026-06-23):
- Commodity supply/demand/pricing is computed **per group**; the **accessibility hostility penalty (10g)** is computed
  **within-group** → isolating a universally-hostile faction into its own group drops hostile-weight ≈ 0 and the penalty
  **vanishes** (the main reason to do this).
- **Trade fleets** route only within group; **ship quality** pools per `factionId + econGroup`; **punitive expeditions**
  and **Luddic-Path cell seeding** iterate the **null group only**, so grouped markets are excluded from both (no vanilla
  punitive expeditions *from* an isolated faction; immune to Pather cells); **contacts** and **bar deliveries** are
  restricted to same-group markets.
- **Risks:** isolation removes outside imports → the group MUST be self-sufficient (the §7c test) or it **starves**;
  **Nexerelin** behavior vs econGroup (market transfers, diplomacy, invasions) is **untested** — verify in a throwaway
  save; econGroup is **runtime-only** (no CSV/JSON column) so it needs a script call and re-application across Nex events.

**Exiles status 2026-09-19: implemented and CONFIRMED IN PLAY (Sean).** Sallee, Lundy and the Midan
markets trade only within the network; the isolation improved their Accessibility considerably.
Sean plans to tune the specific supply/demand figures. Nex conquest/market-transfer behaviour and
reloads are still unverified live. **RULING (Sean, 2026-09-20): Black Fleet forward bases trade
inside the network.** Both vanilla's `PirateBaseManager` and Nexerelin's `Nex_PirateBaseManager`
(a subclass that inherits the picker unchanged) spawn bases for every faction whose file sets
`makesPirateBases`, at equal weight — so each new base is a coin flip between Kanta's pirates and
the Black Fleet while the anchors live. Vanilla puts each base in a bubble of ONE
(`setEconGroup(market.getId())`), never the ordinary economy; the network script moves a Black
Fleet base into the shared group on creation and a destroyed one simply disappears. Verified safe:
vanilla bases have NO timer (the duration countdown is commented out), carry the no-deciv key, and
end ONLY when the station fleet is destroyed, so the economy a base sits in cannot shorten or extend
its life. Lore fit: Midan provisions its proxy's forward posts. Build record: `ExileMidanEconomyNetwork` assigns
every Midan- or Black-Fleet-owned market to the shared `exiles_midan_network` group in
`onNewGameAfterEconomyLoad`, before the opening time pass. Its saved script reconciles ownership
daily and on load, restoring a market's prior group if it leaves the coalition. Existing campaigns
without this script are not retrofitted. This changes normal commodity trade, not fleet travel,
raids, extortion, or player docking.
The offline `tools/ExileMidanEconomyNetworkCheck.ps1` covers coalition transfers, restoration of
the previous group, and unrelated Core markets; the manager and startup plugin pass Janino.

### 10i. AI cores & special items via econ JSON (`["industry", item, …]`)
A market's `industries` entry can be an **array**: element 0 = industry id, the rest = params fed to
`BaseIndustry.initWithParams`. **AI cores** (`alpha_core` / `beta_core` / `gamma_core`) route to the industry's
**AI-core slot**; **special items** (e.g. `pristine_nanoforge`, `synchrotron`) route to the **special-item slot** — but
only if the item's spec lists that industry. The two slots are **independent**: one industry can carry a core AND a
special item (e.g. `["fuelprod","synchrotron","alpha_core"]`). AI-core economic effects: **all** cores give **−1 demand**
per commodity on that industry; **alpha** additionally gives **+1 supply** per produced commodity. Caveat: this works for
markets the **mod declares** itself; you can't merge a core onto a **live vanilla** market via the JSON-append trick (§9)
— do that post-econ-load in Java (`industry.setAICoreId(...)`; then `market.reapplyIndustries()`).

### 10j. How a market sources a commodity — the exact engine formula (decompiled 2026-10-10)
Source: obfuscated `com/fs/starfarer/campaign/econ/reach/CommodityMarketData` (FernFlower), cross-checked against the
readable `Misc.getShippingCapacity`, `CommodityIconCounts` and `PopulationAndInfrastructure.modifyStability`. Computed
**per econ group** (§10h), per commodity. This refines §2 step 2 and the §7c self-sufficiency test: **accessibility
caps both ends of every trade.**

**Shipping capacity** (units a market can move): `ship = floor(accessibility / 0.1)` (`accessibilityPerUnitShipping`);
**in-faction** trade adds `accessibilitySameFactionBonus` **+0.5**, i.e. **+5 units**. This is the number in brackets in
the commodity screen's Access column ("94% (9)").

**Export side** — each market's exportable quantity is `P = min(maxSupply, available)`, so temporary availability cuts
(raids, shocks) also cut exports:
- `maxExportGlobal` = largest `min(P, ship_global)` of any market in the group (the UI's "Max global export").
- `maxExport[faction]` = largest `min(P, ship_inFaction)` among that faction's markets (the UI's "Max <faction> export").
- Still max-not-sum (§7c): only the single best source on each route counts.

**Import side** — for each market: demand `D`, local production `L`, gap `G = max(0, D − L)`. Two routes are scored and
the better one is taken:
- **In-faction:** shortfall = `min(G, D − maxExport[faction])` ("In-faction shortage"), plus
  `G − ship_inFaction(importer)` beyond that ("Insufficient accessibility").
- **Global:** shortfall = `min(G, D − maxExportGlobal)` ("Global shortage"), plus `G − ship_global(importer)` beyond that
  ("Insufficient accessibility"). Faction hostility does NOT block this route; it only lowers accessibility (§10g).
- The importer's capacity must cover the **gap**, not the whole demand. Hidden markets (`isHidden()`) never import.
  Illegal commodities can't use the in-faction route (it's labelled "smuggling" on the global one).

**Consequences (verified against the 2026-10-10 food screen):**
- A high-output producer with poor access barely counts for anyone outside its faction: Tartessus (9 food, 45% access)
  exports **4** globally; Steadfast (8, 31%) exports **3**.
- A commodity's real chokepoints are the few markets whose `min(P, ship)` is highest. For food at game start, the global
  route rests on **Eventide (9)** and **Volturn (8)**; the next tier is 6 (Mazalot, Eochu Bres).
- Wounding one top producer rarely creates a shortage — the next-best source takes over within a tick. Eventide alone,
  even fully shut down, shorts nobody: the Hegemony's in-faction route falls to Jangala (7 via +5 in-faction shipping),
  and Chicomoztoc (demand 8) switches to the global route and draws Volturn's 8.
- **Accessibility is the other lever.** Blockades and shipping disruption lower an exporter's cap AND an importer's
  ability to fill its gap, so they can create shortages that a production cut cannot (e.g. Agreus at game start: a
  2-unit food deficit from "shipping disruption due to trade fleet loss" despite 118% base access).

**Hidden +1 on NPC production — administrators.** `CoreLifecyclePluginImpl.addSkillsAndAssignAdmin` gives every NPC
market of **size ≥ 6 or with ≥ 2 industries** an administrator with **Industrial Planning: +1 supply on every industry**,
custom Exiles industries included (it applies through `BaseIndustry.supply()`). Wiki "output = size" figures omit it.
Worked: Volturn = 7 (size) + 0 (water-covered surface) + 1 (admin) = **8 food**; Eventide = 7 + 2 (solar array) + 1 =
**10**; Eochu Bres = 6 − 1 (poor farmland) + 1 = **6**; Gilead = 3 (`exile_gilead_farming`) + 1 = **4**. Other +1 sources
on the same `supplyBonus` stat: alpha core in the industry (§10i), improvements, fusion lamp, Luddic Majority.
Volturnian Lobster Pens produce **lobster** (base 0 + 1), not food. Aquaculture demands heavy machinery = **size**
(Farming: size − 3), and a machinery deficit cuts food proportionally.
