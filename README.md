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

### Appearance: Default and Modern

The app ships two complete, switchable appearances. **Settings → Appearance → Look** selects
between **Default** (the original interface and the fresh-install default) and **Modern** (a
midnight-navy dashboard with layered surfaces, indigo accents and a
GPU-refracted glass balance card). The choice persists across restarts, is independent of light/dark
mode, and never affects the ledger. See `docs/MODERN_LOOK.md` for the design tokens, the shader
physics and the package layout. The complete Modern UI lives under `ui/modern/` so the Default UI
can be retired later without touching it.

## Build and test

The project builds and its tests run with the checked-in Gradle wrapper (JDK 21 for CI, JDK 25
locally for Robolectric). The verified commands are:

```bash
./gradlew :app:testDebugUnitTest        # unit + Robolectric suite
./gradlew :app:lintDebug                # Android lint
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:connectedDebugAndroidTest # Room migration tests (needs a device/emulator)
```

`./gradlew :app:assembleRelease` requires the release signing secrets and is run by the
`.github/workflows/release.yml` workflow. See `RELEASE_CHECKLIST.md` and
`docs/GITHUB_RELEASE_GUIDE.md`.

No changes were pushed from this local deliverable.
