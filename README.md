# Money Manager – Credit Card & Transaction Management Rework

This local project is a standalone reworked implementation based on the `dharshan-m-s/MoneyManager` project structure and the supplied reference screenshots.

## What was changed

### Credit-card management
- Dedicated credit-card account editor with biller name, last-four digits, nickname, credit limit, last reported outstanding, report date, billing-cycle start day, due day, bill amount, auto-pay, automatic bill generation, personal/business and inactive state.
- Credit-card detail page follows the supplied reference layout more closely: blue card header, large outstanding figure, last-reported line, credit-limit/available/bill metrics, and View / Monthly / Billing Cycle tabs.
- Billing-cycle dates are calculated from the stored cycle start day.
- Card purchases increase outstanding.
- Card-payment transactions reduce outstanding.
- Card-payment transactions are excluded from ordinary spend totals.
- Current outstanding is anchored to the latest reported outstanding checkpoint and then recalculated from transactions after that checkpoint.
- Credit-card bill settings are synchronised to the local bill table.

### Transaction management
- Every transaction row is clickable.
- Clicking a transaction opens a dedicated transaction detail page.
- The detail page exposes an Edit action and Delete action.
- Transactions can be edited for amount, merchant, category, date, type, account, payment type, notes, statistics inclusion and reimbursement flag.
- The same transaction detail flow is used from the dashboard, account detail and all-transactions views.
- Search/filter chips and sorting were added to the all-transactions screen.

### Import
- A Moneyview CSV import entry point is available from the dashboard.
- Imported transactions are mapped into the same ledger used by normal app transactions.

## Important build note

The execution environment used for this deliverable does not have outbound DNS/network access, so Gradle dependency/bootstrap download could not be completed here. The project contains its Gradle bootstrap script and project files, but the final Android build must be performed in an environment with Android SDK/Gradle dependency access (for example Android Studio on your machine).

No changes were pushed from this local deliverable.
