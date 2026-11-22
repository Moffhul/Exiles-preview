# Exiles starting fines

Status: implemented 2026-09-12; Janino and offline accounting checks pass. **Short-run live test
PASSED 2026-09-13 (Sean): obligation posts at start, monthly billing deducts correctly. IN PROGRESS —
two open items:** (1) **BUG:** the Intel mechanism for viewing the entire remaining debt and paying it
off early does not work correctly — under investigation; (2) the long-run test (Verification items
3–7: full schedule, arrears, insufficient funds, completion) has not yet been run.

## Player configuration and behavior

`data/config/exiles/director_config.json` has a `startingDebt` block near the top.

- `enabled`: enables new installments. Change the file and restart/reload the campaign to apply.
  Disabled collection does not cancel already billed debt. Resuming never issues catch-up bills.
  A new campaign created with collection disabled still saves its terms; enabling later resumes
  those terms. No fines are retroactively added to older campaigns or unrelated starts.
- `gracePeriodMonths`: full calendar months without collection, in addition to the partial starting
  month. First installment is charged at the end of the next month. January 20 start, grace 1:
  February free, first charge March month-end. Grace 0: first charge February month-end.
- `paymentSchedule`: ordered rows of `months` and `monthlyCredits`. Total principal is their sum.
  Defaults: 6 x 10,000; 6 x 20,000; 12 x 35,000; 24 x 50,000 = 1,800,000 over 48 installments.
- Schedule and grace are snapshotted at initialization. Editing them affects new campaigns only;
  existing campaigns retain their terms. Only `enabled` is refreshed on load. File edits are not
  watched continuously during play.
- Validation: integer grace 0-1200; 1-100 schedule rows; positive integer months/payments;
  at most 1200 repayment months and 100 million total credits. Each payment is capped at 10 million.
  Malformed supplied settings fail with an explicit configuration error rather than silently
  substituting a different financial obligation. Missing settings use defaults.

The Nachiketa fines Intel entry is permanently listed under Story, including after payoff.
The temporary New tag expires after five days and must not be its only category.
The entry shows the original and remaining amounts, schedule, next rate,
first/final scheduled billing dates and unpaid amounts. The player can settle all remaining fines
there, with confirmation and an affordability check. There is no early-payment discount or penalty.
Full payment stops collection permanently and posts one completion message. The paid intel remains
as a receipt. Conviction and faction relationships are unaffected.

The lore is Sean's ruling: conviction in absentia for assisting in the Nachiketa fuel-facility
destruction, seized pre-game assets, and dormant fines now collected through surviving automated
credit infrastructure. No additional interest, late fees, enforcement fleets or escalating sanctions
are introduced. Ordinary consequences of vanilla monthly debt still apply.

## Accounting and lifecycle

Live-test timing correction (2026-09-13): Starsector's `ReachEconomyStepper` sends the final
economy tick after advancing into the next calendar month, before settling the old report.
The collector therefore bills `month() - 1`. Previously it charged one report too early.
A March 2 start with grace 1 has no March or April fine; May's first installment settles
at the June rollover. Existing saved terms and amounts already paid are retained.

`ExileStartingDebt` uses `EconomyTickListener` and an expense `FDNode`, following SpacerObligation's
report pattern without inheriting its endless level scaling. Academy stipend remains disabled.
One saved obligation owns `ExileStartingDebtLedger`, which separates unbilled principal, the current
report's pending installment and carried fine arrears. A month stamp prevents duplicate charges.

Vanilla aggregates all unpaid expenses into one balance. Exiles consistently allocates payments to
other expenses first, then fines: after a settled report, fine arrears are the smaller of (previous
fine arrears + this installment) and total report debt. This is an attribution rule, not extra debt.
It never considers issuing the final installment equivalent to paying it.

Reconciliation runs on economy ticks, load and Intel actions, after vanilla's atomic report rollover
and credit settlement. It deliberately does not depend on month-end listener order. Early settlement
subtracts the exact remaining obligation from cash, removes only the fine portion from carried
vanilla debt and cancels any current pending fine node. Unrelated expenses/debt remain untouched.

The obligation is initialized only through the Exiles start marker after new-game time passage.
Both vanilla difficulty paths use the precompiled `ExileStartingFines` rule command to set the
marker in a before-time-pass script, after the real campaign sector exists. Direct `$global`
assignments in the creation menu do not survive sector creation (Easy live-test failure, 2026-09-13).
Normal also previously omitted the marker entirely. The deferred Nex custom-start source sets
the same marker. The Nex
custom start remains subject to its existing packaging/test gate. Load never creates a new fine.
The collector is registered transiently once per load from its saved object; completion removes it.
No per-frame script, sector scan, additional fleet or recurring config read is used.

Saved IDs: `$exiles_starting_fines_eligible`, `$exiles_starting_debt`.
Monthly report node: `exiles_starting_fine`. Intel button: `exiles_settle_fine`.

## Verification

- `tools/janino-check.ps1`: new classes and `ExilesModPlugin` compile with the game's compiler.
- `tools/ExileStartingDebtCheck.ps1`: 125 passing production ledger/config and report integration checks use
  real `MonthlyReport` nodes with campaign API proxies. Covers exact totals/rate boundaries,
  grace 0/1, duplicate ticks, paused collection, no catch-up, partial payment, final unpaid bill,
  early settlement of pending and carried amounts, preserving unrelated debt, insufficient funds,
  completion idempotency, load registration and configuration validation. Includes XStream
  round-trips of the ledger and the complete obligation/pending-report reference graph;
  this is not a substitute for a full engine save/load test.

Required live test on a new Exiles campaign:
1. Confirm one visible Intel entry, correct starting terms and first billing date on both difficulty
   paths; unrelated starts and pre-feature saves must receive no new obligation.
2. With a short test schedule, pass grace and each rate boundary; compare report expenses and cash.
3. Save/reload before and after month-end, during grace, paused, in arrears, and after completion.
4. Restart with enabled false/true and changed schedule/grace: pause/resume works, saved terms persist.
5. Exhaust funds with both fine and ordinary expenses; verify carried balances and later repayment.
6. Pay off early while in arrears; verify next rollover retains other debt and never repeats fines.
7. Reach the final installment without enough funds; confirm completion only after the arrears clear.
8. Check Intel layout, confirmation, notification and monthly-report tooltip in game.
