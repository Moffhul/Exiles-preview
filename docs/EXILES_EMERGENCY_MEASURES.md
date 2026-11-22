# Exiles — Emergency Measures

Status: **VERSION ONE IMPLEMENTED 2026-08-22; CONFIRMED WORKING IN PLAY BY SEAN, 2026-09-13.**
The test matrix below is retained as the regression reference; it is no longer an open item.

## 1. Intent

**Emergency Measures** is an Exiles-specific, visible, temporary recovery response for a populated
market threatened by unintended stacking from Exiles-authored pressure. Local authorities contract
security forces, requisition weapons and supplies, and impose extraordinary appropriations to buy a
limited recovery window.

It is not a general anti-decivilization safeguard. A vanilla-only crisis cannot arm it, player-caused
destruction cancels a dormant lead-in, and an active response has a fixed reserve and maximum duration.
The system never sets `$core_noDeciv`, never blocks player actions, and can be overwhelmed.

## 2. Version-one rules

### Eligibility and attribution

Only an explicit Exiles pressure report can arm a market. The narrow calls currently sit immediately
after these effects actually land:

- Omicron colony disruption/bombardment;
- Black Fleet assault raids and Corsair Activity;
- Ashen ordinary/special sabotage, rehearsals, interdiction harassment, and the Culann strike;
- Refugee Encampments established by a Refugee convoy.

Permanent authored conditions such as Refugee Strain or Religious Unrest do not independently arm a
market. Vanilla raids, shortages, crises, and other mods never call the marker seam.

`ExileUnrestGuard.reportExilesPressure()` sets a timed per-market
`$exiles_emergency_measures_pressure` marker and adds the Emergency Measures condition in a dormant,
invisible state. The marker lasts at least 90 days and may last as long as the originating Exiles
disruption or Recent Unrest queue. Repeated Exiles pressure may refresh the dormant eligibility
window, but reports are ignored while Emergency Measures is active.

The event-only `ExileEmergencyMeasuresPlayerActionListener` uses vanilla's
`ColonyPlayerHostileActListener`. A player raid, disruption raid, tactical bombardment, or saturation
bombardment cancels a dormant lead-in and blocks Exiles eligibility on that market for 270 days.
Once Emergency Measures is active, the listener deliberately does nothing: player action remains
unrestricted and cannot refresh, increase, or extend the response.

There is no sector-wide manager or market scan. Only marked markets own an inexpensive local timer.

### Trigger and activation

- The eligible market must remain at displayed Stability **0 for 60 continuous campaign days**.
- Any displayed Stability above zero resets the lead-in counter.
- Cooldown or player-attribution state prevents the counter from advancing.
- On activation, vanilla `RecentUnrest.counter(1, "Emergency Measures")` removes exactly one point
  where Recent Unrest exists. The condition is immediately reapplied before the reserve snapshot so
  the snapshot includes that relief.
- The plugin reads raw modified Stability, excluding no hidden floor. It calculates the flat bonus
  needed to reach Stability 1 and stores `ceil(1 - underlying Stability)`, clamped to **0 through
  +5**. The zero case is possible when countering Recent Unrest already restores underlying
  Stability to 1. The stored reserve is never recomputed or increased.

### Commodity dependency

While active, the condition creates demand for:

- **+1 Marines**;
- **+1 Hand Weapons**; and
- **+1 Supplies**.

The implementation follows vanilla's worst-deficit, point-for-point Stability convention. If any
one of those one-unit demands is short, the currently applied reserve is one point below the stored
reserve. Multiple simultaneous shortages do not stack beyond that worst one-point deficit. Supply
recovery may restore the modifier up to the original stored reserve, but never above it.

This is deliberately a capped modifier, not a Stability floor. New damage can push the market back
to zero even while the condition remains active.

### Duration, recovery, failure, and cooldown

- Maximum active duration: **180 days**.
- Early recovery: underlying Stability, calculated without the condition's current modifier, must
  remain at least **3 for 30 continuous days**.
- Falling below underlying Stability 3 resets the recovery counter.
- Reaching 180 days without recovery removes the condition cleanly. Version one has no escalation.
- Every active lifecycle removal sets a **180-day** cooldown in the market's memory.
- Dormant monitor cleanup does not create cooldown because Emergency Measures never activated.

### Exiles guard relationship and player agency

Active Emergency Measures always counts as **Battered** in `ExileUnrestGuard.isBattered()`, even if
the legacy unrest-guard setting is disabled. Existing Exiles directors therefore back off through
their existing target-selection and unrest checks. No unrelated director behavior was broadened.

Player and vanilla actions do not consult `ExileUnrestGuard`. They remain free to add penalties,
destroy infrastructure, bombard the population, or overpower the stored reserve. Exiles pressure
reports are ignored while active, so scripted attacks cannot refresh the timer or reserve either.

## 3. Economy and player-facing behavior

### NPC markets

The player pays **50% more** when purchasing any commodity at an affected NPC market. Player selling
prices and market Accessibility are unchanged. The modifier is removed completely when the condition
ends and is reapplied correctly if ownership changes while active.

### Player-owned colonies

NPC purchase-price modifiers are not applied. Instead, the current monthly income report receives a
separate **Emergency Measures** expense beneath the affected colony:

`10,000 credits × market size per month`

The condition implements `EconomyTickListener` only while active and player-owned. It accrues
one economy-iteration fraction of the monthly amount per tick, so partial active months and mid-month
ownership changes are prorated. A size-5 colony therefore costs 50,000 credits for a full active month.

## 4. Implementation map

- Tuning constants: `data/scripts/exiles/ExileEmergencyMeasuresConfig.java`
- Condition/lifecycle/economy: `data/scripts/marketconditions/ExileEmergencyMeasures.java`
- Player attribution: `data/scripts/exiles/ExileEmergencyMeasuresPlayerActionListener.java`
- Guard/pressure seam: `data/scripts/exiles/ExileUnrestGuard.java`
- Condition ID: `exile_emergency_measures`
- Market keys: `$exiles_emergency_measures_...`
- Content registration: `data/campaign/market_conditions.csv`
- Listener restoration: `data/scripts/exiles/ExilesModPlugin.java`

The named version-one tuning includes the 60-day trigger, +5 reserve cap, 180-day duration,
Stability-3/30-day recovery rule, 180-day cooldown, demands, price multiplier, appropriation formula,
pressure windows, and player-attribution window. These author rules are intentionally owned by the
Emergency Measures system rather than `director_config.json` or a faction raid director.

## 5. Deferred and excluded work

- No Marine-delivery bar mission.
- No Martial Law or escalation state.
- No Security Centers interaction.
- No Accessibility penalty.
- No intel notification beyond the visible market condition and financial-report entry.
- No global manager, polling scan, vanilla `DecivTracker` modification, or `$core_noDeciv` flag.

## 6. Focused live-test matrix

- Exiles pressure at positive Stability: invisible dormant monitor; no active effects.
- Eligible Stability 0 for 59 days: no activation. Day 60: one activation.
- Stability rises above zero during lead-in: zero-day counter resets.
- Vanilla-only crisis: no condition. Player action during lead-in: monitor disappears and remains
  blocked for 270 days.
- Activation counters exactly one Recent Unrest point once, including after save/reload.
- Snapshot tests at raw underlying Stability 1, 0, −1, and −5 or lower: stored reserves 0, +1, +2,
  and capped +5 respectively.
- Further damage does not increase reserve or duration and may return displayed Stability to zero.
- Full supply grants the stored reserve; a shortage in any demanded commodity reduces it by one.
- Active condition remains Battered for every existing Exiles director; player attacks still land.
- NPC market: all player purchase prices +50%; sell prices and Accessibility unchanged.
- Player colony: separate prorated monthly expense equals 10,000 × size for a full month.
- Underlying Stability 3 for 29 days: remains. Continuous day 30: removes and starts cooldown.
- Underlying Stability falls below 3 during recovery: recovery counter resets.
- Day 180 without recovery: removes with no escalation and starts cooldown.
- No reactivation during 180-day cooldown; a later fresh eligible crisis may arm normally.
- Ownership transfer, save/reload, market removal, and decivilization leave no duplicate listener,
  price modifier, demand, Stability modifier, finance charge, or permanent guard marker.

Static verification on 2026-08-22 compiled the condition, attribution listener, guard, every marked
director/stage, Refugee convoy manager, and `ExilesModPlugin` through Starsector's Janino compiler.
Live campaign testing remains required.
