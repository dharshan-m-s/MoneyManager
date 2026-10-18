# Financial model and dashboard formulas

## Sources of truth

1. **Transactions** (`transactions`) are the ledger of actual money movements. They are never
   rewritten by a balance recalculation.
2. **Accounts** (`accounts`) hold durable configuration plus reconciliation checkpoints.
3. **Bill templates and occurrences** (`bills`, `bill_instances`) hold the schedule and the
   payment state of each occurrence.
4. All derived values are computed by `AccountingEngine` / `FinancialSummary`; the dashboard
   tiles and every drill-down call the same functions with the same period.

## Durable checkpoints (accounts and cards)

`effective value = engine-derived value + adjustment`

The adjustment is fixed when the user deliberately saves a new balance/outstanding. It is
stored on the account (`manualBalanceAdjustmentMinorUnits`, `outstandingAdjustmentMinorUnits`).
A recalculation recomputes the derived value from the whole ledger and re-applies the offset,
so:

- the user's value survives restarts and full recalculation;
- later purchases/payments move it exactly once;
- backdated transactions and edits are not lost by a timestamp filter;
- imported statement snapshots remain history and are never overwritten.

Legacy rows are initialised once by deriving the offset from the value the user already saw.

## Dashboard definitions (current month, Asia/Kolkata, start-inclusive/end-exclusive)

| Metric | Formula |
|---|---|
| Available | Σ `currentBalanceMinorUnits` for included BANK, CASH, WALLET, DEBIT_CARD, PREPAID_CARD accounts. Investments are excluded (not spendable). |
| Spent | Σ (`debit − credit`) for semantics EXPENSE, CARD_PURCHASE and REFUND_REVERSAL, with `includeInStatistics`, on included accounts. Card bill payments, transfers and reimbursements are excluded; refunds reduce the total. |
| Income | Σ (`credit − debit`) for semantics INCOME with `includeInStatistics`, on included accounts. Transfers, card payments and refunds are excluded. |
| Net worth | Σ included assets (BANK, CASH, WALLET, INVESTMENT, DEBIT_CARD, PREPAID_CARD) − Σ credit-card outstanding − Σ loan balances. Credit limits are not assets. Internal transfers do not create net worth. |
| Upcoming bills | Σ `amountDueMinorUnits` of unpaid occurrences of active billers (overdue + generated future occurrences). |
| Budget progress | `monthSpend / budget`. The dashboard and Budget screen use the same spend definition. |
| Safe to spend | With a budget: `budget − spend` (remaining budget). Without one: `available − upcoming bills` (liquid cash after scheduled commitments). Spending is not subtracted from the balance a second time. |

“Included account” means `active = 1`, `deleted = 0`, `hide = 0`, `hideAccountTxns = 0`,
`parentAccountId IS NULL`.

## Drill-downs

- Available → each contributing account and its effective balance.
- Spent → exact included transactions and their signed subtotal.
- Income → exact included income transactions.
- Net worth → assets, liabilities, both subtotals, and the resulting net worth.
- Unpaid bills → each occurrence and its amount.

Every breakdown displays the formula and period and reconciles exactly to its tile.

## Recurring bills

`BillSchedule` is the only date engine. Months clamp the intended anchor day to the month's
last day but never rewrite the anchor, so a 31st bill does not drift after February. `BIMONTHLY`
is one occurrence every two calendar months; a twice-monthly rule would be a separate rule.

`BillRecurrenceService` is the only generator. It materialises the template's first occurrence
and, when `autoGenerateBills` is on, every occurrence up to `autoGenerateLeadDays` ahead,
catching up missed runs. The unique `(billId, dueDateEpochMillis)` index and `INSERT OR IGNORE`
make repeated runs idempotent. Paid occurrences and linked payments are never deleted; editing a
schedule only removes future, unpaid, off-schedule occurrences.

Triggers: app start, every foreground entry, an after-create/after-edit reconciliation, and a
periodic WorkManager job (best-effort while the app is closed; the foreground run catches up).

## Credit-card bill payments

- Recording a new payment creates two linked legs atomically: TRANSFER_OUT on the source account
  and CARD_PAYMENT (TRANSFER_IN + cc-bill-payment) on the card. The bill instance is linked to the
  card leg and marked paid with the payment date.
- Linking an existing bank transaction reclassifies it from ordinary expense to transfer (so it
  is not counted as spending) and creates only the missing card leg. No bank transaction is
  duplicated.
- Deleting either leg unlinks the bill, restores it to unpaid, and recalculates both accounts.
