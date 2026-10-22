# Final Repair Report — Money Manager

Section 19 deliverable. Every claim below was produced by running the command shown, on this
working tree, and no commit, tag, push, release or PR was performed.

---

## 1. Branch and starting commit

- Branch: **`main`**.
- Starting HEAD: **`08f28cd0e9a9fa36de861b74b2b08dd8dc35b94d`** (in sync with `origin/main`, clean tree).
- Difference from the inspected reference: **none** — work was done in place on this commit; no
  commits were created. `git rev-parse HEAD` still returns the same hash. The working tree holds
  **65 modified files** (+3,270 / −567) and **18 new untracked files** (tests, `11.json` schema,
  `FailedAttemptPolicy.kt`, `ChecksumParser.kt`, the Gradle wrapper jar, `docs/PERFORMANCE.md`).
- Order deviation from §17: executed as §3→§11 in the brief's order, then §12–§18. §12/§13 were
  done before §14 because the navigation single-layer fix touches the same callbacks as the
  transition/rapid-tap audit; §16 regression tests were added alongside each repair rather than as
  one trailing block. No material deviation beyond that.

## 2. Root causes fixed, by severity

**Critical (data correctness / recoverability)**
- **§3 partial bill payments.** Bills could only be settled in full; a partial payment either
  vanished or overwrote the outstanding. Added a real paid/remaining model
  (`BillInstanceEntity.paidAmountMinorUnits`) with `MIGRATION_10_11`, and a repository path that
  allocates each payment to the instance without touching already-paid history.
- **§4 linked transfers.** Editing one leg of a transfer could desynchronise the pair. Introduced a
  pair-mirroring contract in `TransactionRepository.updateEditableTransaction`; the edit screen now
  treats a transfer/payment amount as read-only where the pair owns it.
- **§5 backup/restore.** The old backup was a best-effort copy with no verification and no safe
  restore. Rebuilt as temp→verify→swap with rollback, a manifest (v3) carrying
  `databaseBytes`/`databaseSha256`, a staged restore with a pre-restore safety copy, and a
  fail-safe that leaves the previous data untouched on any error.
- **§6 migrations.** Missing/incorrect migration handling risked data loss on upgrade. Added the
  6→7 budget-dedup and 9→10 bill-instance-dedup migrations plus a full 1→11 chain, all tested.
- **§7 CI would not build.** Committed `org.gradle.java.home`, missing Gradle wrapper and
  non-executable `gradlew` broke verification. Generated a real wrapper, removed the JDK pin, and
  made CI/release use `chmod +x ./gradlew` + `--no-daemon` + JDK 21.

**High (wrong numbers / silent failure)**
- **§8 sum ranges.** Month/date queries used `BETWEEN` semantics inconsistently, so boundary rows
  could be double-counted or dropped, making Dashboard/Spend Summary totals disagree. Made every
  range query **start-inclusive / end-exclusive** and added reconciliation tests.
- **§9 silent no-op writes.** `AccountEdit`, `CreditCardForm`, `TransactionEdit` and
  `TransactionDetail` could return success while persisting nothing (or crash on a vanished row).
  Rewrote the save/load paths with `withTransaction`, real error surfacing, and
  `NoSuchElementException("This transaction no longer exists")` instead of a false success.
- **§10 app lock & updater.** `AppLock.isEnabled()` failed *open* when the hash was unreadable; the
  PIN-lock screen had no cooldown after failed attempts; the updater trusted a missing checksum.
  Made `isEnabled()` fail closed, added `FailedAttemptPolicy` (persisted counter + exponential
  cooldown) and `ChecksumParser` (api-digest → `.sha256` asset, explicit failure on a
  malformed-but-present checksum).
- **§11 dark mode.** Decorative green/grey gradients and stray literals made dark mode look broken,
  and several semantic colours failed WCAG contrast as text. Tokenised the brand/card headers,
  moved every colour literal into `Theme.kt`, and fixed the failing pairs.

**Medium (UX / performance / hygiene)**
- **§12 rapid taps.** `TransferViewModel.save()` had no in-flight guard — a double tap created two
  transfer pairs. Added `saving`/`saved` guards and disabled the button while saving.
- **§13 stale detail after delete.** Deleting an account from its detail/editor flow returned to a
  detail screen pointing at the deleted row. Added `returnToAccountsAfterDelete()` (pops to the
  list in one step).
- **§14 quadratic grouping.** Account detail re-scanned the page once per group (O(groups×page)).
  Rewrote as a single pass (O(full+page)); also prune abandoned `.part` downloads in the update
  cache.
- **§15 stale docs.** README claimed the environment could not build; release guide said JDK 17;
  design summary listed old radii. Corrected all, and marked the historical QA report as
  superseded.

## 3. Files and components changed

- **Database:** `MoneyManagerDatabase.kt` (version 11), `AppDatabaseMigrations.kt` (all migrations +
  `latestVersion() = 11`), `BillDao.kt`, `TransactionDao.kt`, `BillEntity.kt`; new schema
  `app/schemas/.../11.json`.
- **Repositories:** `BillRepository.kt`, `TransactionRepository.kt`, `BackupManager.kt`.
- **Domain:** `BillRecurrenceService.kt`, `FinancialSummary.kt`, `AccountingEngine`.
- **Security:** `AppLock.kt`, new `FailedAttemptPolicy.kt`, `PinHasher.kt`, `PinLock.kt`,
  `SystemLockPrompt.kt`, `MoneyManagerApp.kt`.
- **Updater:** `UpdateRepository.kt`, `ApkVerifier.kt`, `UpdateModels.kt`, `UpdateScreen.kt`, new
  `ChecksumParser.kt`.
- **UI/theme:** `Theme.kt`, `MmCore.kt`, `DashboardScreen.kt`, `AccountDetailScreen.kt` +
  `AccountDetailViewModel.kt`, `SpendSummaryScreen.kt`, cash/dashboard/budget/account/bill screens.
- **Navigation:** `MoneyManagerNavHost.kt` (delete-to-list, single-layer Back, `launchSingleTop`).
- **Build/CI:** `gradlew(.bat)`, `gradle/wrapper/*`, `gradle.properties`,
  `.github/workflows/{ci,release}.yml`.
- **Docs:** `README.md`, `MODIFICATION_SUMMARY.md`, `FINAL_QA_REPORT.md` (superseded note),
  `docs/{UI_DESIGN_SYSTEM,NAVIGATION_TEST_MATRIX,UPDATER_ARCHITECTURE,GITHUB_RELEASE_GUIDE}.md`,
  new `docs/PERFORMANCE.md`.
- **Tests:** 33 test files (30 unit/Robolectric + 3 instrumentation), listed in §10.

## 4. How partial bill payments and linked-transfer edits now behave

- A bill instance tracks `amountDueMinorUnits` and `paidAmountMinorUnits`. Recording a payment adds
  to `paidAmountMinorUnits` and links the source transaction; the bill is `paid` only when paid ≥
  due, and an excess is reported as "extra recorded" rather than silently clamped. Multiple
  payments accumulate; nothing is overwritten. Existing/legacy links are preserved (no cascade
  delete; `ForeignKey.RESTRICT`/`SET_NULL` as designed).
- A linked transfer is a mirrored pair. Editing amount/date on one leg mirrors to the other through
  `updateEditableTransaction`; the credit-card **bill-payment** amount is shown read-only and never
  sent from the edit screen, so the payment model keeps sole ownership of what was paid.

## 5. How backup, validation, restore and recovery work

- **Backup:** copy the DB to a temp file, verify (integrity, magic header, core tables), then atomically
  swap into place; write a CSV + manifest (v3: `databaseBytes`, `databaseSha256`); only then advance
  the last-success stamp. A failure leaves the previous backup and stamp untouched.
- **Validation:** a candidate restore must pass magic header + PRAGMA integrity + schema/core-table
  checks before anything is replaced.
- **Restore:** stage the incoming file, verify it, take a pre-restore safety copy, swap, then reopen
  through a fresh connection (`verifyLiveDatabaseOpen`). `RoomDatabase.close()` is treated as
  one-shot; the native connection is closed explicitly instead.
- **Recovery:** any failure during restore rolls back to the pre-restore copy and reports
  "put back unchanged"; a `.pre_restore` safety copy is retained as a manual escape hatch.

## 6. How legacy migration data and payment links are preserved

- Migrations are additive and never destructive; the only `DELETE`s remove **duplicate** rows while
  keeping the kept/canonical row (budgets: max id per year/month; bill instances: canonical id per
  bill+due date), and the dedup is tested.
- `MIGRATION_10_11` adds the payment columns with defaults that preserve every existing row
  (previously paid-in-full instances keep their meaning).
- Foreign keys keep payment→bill and transfer-leg→leg links intact across recalculation and restart
  (`AccountingPersistenceTest`, `LinkedTransferEditingTest`). No schema version is skipped silently:
  schema versions 5/6/7 have no JSON because the corresponding hand-written migrations do not need
  intermediate schemas; the 1→11 chain is verified.

## 7. Colour palette and the reasoning behind the roles

- **Dark surfaces:** background `#080E1A`, surface `#131C2E`, surfaceMuted a step lighter — a
  lifted-surface stack rather than pure black, so cards read without shadows (which vanish on dark)
  while keeping ≈1.1:1 surface/background separation.
- **Light surfaces:** background `#F6F8F7`, surface white, `surfaceMuted` a soft inset tone.
- **Text:** light `textPrimary` near-black on light surfaces; dark `textPrimary` near-white on dark
  surfaces; `textSecondary`/`textTertiary` step down. All ≥ 4.5:1, enforced by test.
- **Accent:** brand green `#067A4C` (light) — darkened from the previous `#0A8F5A` which failed
  contrast as text; white `onAccent` sits on it.
- **Brand/card headers:** green band `#0B6B4A→#0A6E4B` (light) and `#0C3B2C→#0E5A40` (dark); credit
  cards get their own blue band `#1B4D8F→#245FA8` (light) / `#16305A→#1C4179` (dark) so a card is
  distinguishable from a bank account. All carry white text at ≥ 4.5:1.
- **Semantic:** income `#047857` (light, darkened from `#059669` to pass contrast), expense red, and
  a warning amber; each is legible as *text*, not just a tint.

## 8. Screens, navigation, transitions and performance reviewed

- **Screens/themes:** every screen audited for colour literals (all now come from `MmColors`); shared
  components smoke-rendered in light and dark (`ThemeRenderingTest`); contrast pair audit
  (`ThemeContrastTest`).
- **Navigation:** 14-flow matrix in `docs/NAVIGATION_TEST_MATRIX.md`, enforced by
  `NavigationRouteTest` over a real `NavController` (single-layer Back, no duplicate route on rapid
  taps, delete lands on a list).
- **Transitions/interactions:** reduced-motion respected (Compose scales tween durations via the
  system animator scale); no animation added where it does not aid understanding; double-tap guards
  on every editor; date/form state held in ViewModels (survives recomposition/config change);
  app-bar Back, system Back and save/delete completion coordinated through `safeBack()`.
- **Performance:** account-detail grouping rewritten single-pass; update-cache policy made explicit;
  stable lazy keys everywhere; images downsampled; no main-thread DB/file/crypto work.

## 9. Before/after measurements (actually available)

Reproducible via `./gradlew :app:testDebugUnitTest --tests
"com.moneymanager.app.ui.accountdetail.LedgerPerformanceTest"` (JVM/Robolectric, not device FPS):

| scenario (6,000 rows, 33 groups, full history paged in) | before | after |
| --- | --- | --- |
| account-detail month grouping | ~19 ms | ~4 ms |
| whole-account read | — | ~40 ms |

The rewrite was verified to produce identical groups, sums and row order. No device frame timings
were taken, so none are claimed.

## 10. Commands executed and their real results

| Command | Result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest` | **BUILD SUCCESSFUL — 160 tests, 0 failures, 0 errors, 1 skipped** |
| `./gradlew test --no-daemon` | **BUILD SUCCESSFUL — 160 tests, 0 failures** |
| `./gradlew :app:lintDebug --no-daemon` | **BUILD SUCCESSFUL — 0 errors, 292 warnings** (pre-existing; 217 are the intended Material2+M3 mixed-library notice) |
| `./gradlew :app:assembleDebug --no-daemon` | **BUILD SUCCESSFUL — `app-debug.apk` produced** |
| `./gradlew :app:assembleRelease --no-daemon` | **BUILD SUCCESSFUL — `app-release-unsigned.apk` produced** (R8 + resource shrinking + `lintVitalRelease`) |
| `./gradlew :app:connectedDebugAndroidTest` | **not run — no device/emulator attached** (`adb devices` empty) |

Unit-test composition (30 classes, 160 tests): accounting engine 31, bill payments 17, backup/restore
7, recurrence/schedule 14, security 13, navigation 5, theme contrast 7, transfer/transfer-edit 6,
dashboard reconciliation 4, checkpoints 9, date parsing 6, updater 12, performance 2, misc.

Instrumentation tests present but **device-only** (not run here): `Migration9To10Test`,
`Migration10To11Test`, `MigrationFullChainTest`, `ThemeRenderingTest`.

## 11. CI / device / emulator / release checks left unverified, and why

- **`connectedDebugAndroidTest`** (Room migration tests on a real SQLite, Compose rendering on an
  AndroidComposeView): requires a device/emulator; none was attached. The migration SQL is also
  exercised indirectly by the JVM suite, but the on-device run is unverified here.
- **Release signing / `assembleRelease` signed output:** requires `ANDROID_KEYSTORE_PATH` and the
  signing secrets; not present locally. The build was verified **unsigned**; signing happens in
  `.github/workflows/release.yml`.
- **GitHub Actions CI run:** the workflow files are corrected and parse, but the runner has not been
  executed from here.
- **`ApkVerifier` package/version/signer checks:** need a real APK + installed app (instrumentation);
  not unit-testable without a device. Logic reviewed, not executed.
- **On-device navigation/back walks and visual checks:** skipped by explicit instruction; covered by
  `NavigationRouteTest` and the theme tests instead.

## 12. Remaining issues, by severity

**Low**
- `observeRecent(20000)` in the transactions list keeps up to 20k rows in memory for that view.
  Search and export remain complete, so no record is hidden; a paged list is a future improvement.
- Account-detail aggregates still derive from the full account history in Kotlin; dedicated SQL
  `SUM` queries per month/cycle would cut more but risk drifting from the exact
  `includeInStatistics` + signed-amount semantics (see `docs/PERFORMANCE.md`).
- Lint carries 292 warnings (no errors): mostly the intentional Material2 usage, plus `UseKtx`,
  `GradleDependency`/`NewerVersionAvailable` (deliberately not upgraded) and a few `UnusedResources`.

**None outstanding at critical/high.** No data-loss path, no destructive fallback, no faked test, no
suppressed recalculation identified in the final review.

## 13. No GitHub action performed

No `git commit`, `git tag`, `git push`, release, PR or GitHub settings change was made.
`HEAD` remains `08f28cd0e9a9fa36de861b74b2b08dd8dc35b94d` on `main`; all work is in the working tree.

---

# Addendum A — Reminder-driven bill payment window, credit-card lifecycle, premium UI pass

Appended after the §19 deliverable above. Everything below was produced by running the command
shown on this working tree. Still **no commit, tag, push, release or PR**.

## A1. Starting point of this addendum

- Branch `main`, HEAD **`f78ed417023cb4b04eb857b59a965e8402563491`** ("ci pipeline fix").
- Working tree at the time of writing: **61 modified files** (+1,747 / −609) and **7 new
  untracked files** (`12.json` schema, `BillPaymentWindow.kt`, `MmMotion.kt`,
  `CreditCardWorkflowTest.kt`, `BillPaymentWindowTest.kt`, `BillsViewModelTest.kt`,
  `reference.pdf`).
- The addendum reused the §19 foundations: the `MmColors`/`MmSpacing`/`MmType` token system, the
  `MmCard`/`MmChip`/`MmSectionHeader` component family, integer minor units, and the existing
  Room migration chain (no destructive fallback).

## A2. Reminder setting now drives "Mark as paid"

- `BillPaymentWindow.leadDays(bill)` is the single source of truth for when an occurrence becomes
  payable, and it reads `bill.reminderDaysBefore` (clamped to 1..365).
- The default is **10 days**. `BillEntity.reminderDaysBefore` and `BillFormViewModel` both default
  to 10; `MIGRATION_11_12` rewrites only legacy rows that still carry the old default of 3, so a
  user who had explicitly chosen a different value keeps it.
- `BillFormScreen` always shows "Days before the due date" and states in the helper copy that the
  same number drives both the reminder and the "Mark as paid" window. `reminderEnabled` now only
  controls notifications, exactly as its name says.
- `autoGenerateLeadDays` remains a separate knob for auto-generated occurrences.

## A3. Credit-card lifecycle, corrected end to end

- **Purchases vs. refunds.** A card purchase increases the outstanding balance; a card credit is
  recorded as `REFUND_REVERSAL`, which reduces the outstanding balance without being counted as
  app income. Income analytics exclude that semantic explicitly.
- **Cash advances.** `CARD_CASH_ADVANCE` was added to `TransactionSemantics` and to the
  `AccountingEngine`, so a card cash advance increases card debt instead of behaving like a plain
  expense.
- **Limits.** `AccountEntity.creditLimitOverrideMinorUnits` holds a user-entered limit.
  `AccountingService` prefers the override over the derived limit, and the accounts and
  account-detail screens both show the effective limit.
- **Bill settlement.** `BillRepository` creates out-of-band card settlement legs and reuses an
  already-recorded card payment instead of double-counting it. A leg that settles a bill can no
  longer be re-pointed to another bill; an unallocated card-payment leg still can.
- **Dead code.** The unused card queries in `AccountDao` were removed.

## A4. Premium, interactive UI pass across the app

New shared primitives live in `app/src/main/java/com/moneymanager/app/ui/components/MmMotion.kt`:

| primitive | behaviour |
| --- | --- |
| `pressScale` / `pressableClick` | spring-ish press feedback + scale on every tappable surface |
| `revealOnAppear` | staggered fade/slide entrance, used for list items and screen sections |
| `MmAnimatedAmount` | animated count-up for money values |
| `MmSkeleton` | loading placeholder |
| `MmPrimaryButton` / `MmSecondaryButton` | one button geometry for the whole app, with press feedback and an optional loading spinner |
| `MmGlassSurface`, `MmHeroAction`, `MmStatTile` | hero/stat surfaces used by the dashboard, accounts, cash and detail screens |

Applied across the app:

- **Shell:** the top bar is now a brand gradient with a soft corner light; the FABs carry an accent
  colour and a radial glow; the dashboard speed-dial rotates; the drawer header has a gradient and
  a selected-item marker.
- **Hero screens:** Dashboard, Accounts, Cash, Transaction detail and Account detail have gradient
  headers with glow, animated headline amounts and staggered content.
- **Card UX:** the account-detail card screen offers "Pay this card", "Record a refund" and
  "Add a purchase"; add-transaction switches to card-aware wording; the transfer screen explains a
  card payment vs. a cash advance; `Destination.Transfer.route(toAccountId?)` carries the card
  selection through navigation.
- **Lists:** every lazy list (accounts, banks, categories, bills, income, search, reimbursements,
  import preview, dashboard breakdowns, category transactions) now staggers its items in and
  animates its amounts.
- **Consistency:** every remaining raw `Button`/`clickable` in the UI layer was converted to the
  shared button and press-feedback primitives, and the raw `clickable` imports were removed where
  they became unused.

## A5. Commands executed for this addendum, and their real results

| Command | Result |
| --- | --- |
| `./gradlew :app:compileDebugKotlin --no-daemon` | **BUILD SUCCESSFUL** (after every batch of edits) |
| `./gradlew :app:testDebugUnitTest --no-daemon` | **BUILD SUCCESSFUL — 187 tests, 0 failures, 0 errors, 1 skipped** |
| `./gradlew :app:lintDebug --no-daemon` | **BUILD SUCCESSFUL — 0 errors**; informational findings only (Material2/M3 mixed libraries, `UseKtx`, `GradleDependency`, `UnusedResources`) |
| `./gradlew :app:assembleDebug :app:assembleRelease --no-daemon` | **BUILD SUCCESSFUL** — `app-debug.apk` and `app-release-unsigned.apk` produced (R8 + resource shrinking + `lintVitalRelease`) |
| `12.json` schema inspection | version **12**; `accounts.creditLimitOverrideMinorUnits` **present**; `bills.reminderDaysBefore` **present** |
| `./gradlew :app:connectedDebugAndroidTest` | **not run — no device/emulator attached** (`adb devices` empty) |

Test delta since §19: 160 → **187** unit tests, driven by the new `BillPaymentWindowTest`,
`BillsViewModelTest` and `CreditCardWorkflowTest` classes (plus additions to
`BillPaymentRepositoryTest` and `AddTransactionDateTest`).

## A6. Still unverified (unchanged from §11)

- `connectedDebugAndroidTest`: `MigrationFullChainTest`, `Migration9To10Test`,
  `Migration10To11Test`, `ThemeRenderingTest` need a device.
- Signed release output: needs `ANDROID_KEYSTORE_PATH` and the signing secrets; only the unsigned
  APK was built here.
- `ApkVerifier` package/version/signer checks and on-device navigation walks: device-only.
- GitHub Actions: the workflow has not been executed from here.

---

# Addendum B — Credit-card statement derivation, corrected due-date mapping, inactive accounts

Appended after Addendum A. Still **no commit, tag, push, release or PR**.

## B1. What "statement" now means

- `domain/billing/CreditCardStatement.kt` is the single rule set:
  - `DUE_LAG_DAYS = 20` — only used to *derive* a default due date when the card has none
    (`cycleEnd + 20 days`). It is **not** used for cycle lookup any more.
  - `currentCycle(startDay, now)` — the cycle containing `now`.
  - `statementFor(cycle, closedOutstanding, currentOutstanding, movementSinceEnd)` —
    closed cycle = outstanding at close; ongoing cycle = current outstanding.
  - `cycleSettledByDueDate(cycleStartDay, dueEpochMillis)` — the occurrence that falls on a
    due date settles the cycle that **closed most recently before that due date**; if the cycle
    running on the due date has not closed yet, the previous cycle is the one being settled.
  - `defaultDueDate(cycleStartDay, epoch)` — cycle end + 20 days.
- `data/repository/CreditCardStatementService.kt` reads the ledger and produces:
  - `statementForCycle` — exact statement for any cycle (ongoing = live outstanding, closed =
    outstanding at close, future = none).
  - `previousCycleStatement` — the prior cycle's statement, used as the estimate floor.
  - `estimateStatementForCycle` — walks back up to `MAX_ESTIMATE_CYCLES = 24` cycles to the
    most recent **started** cycle and returns its statement; falls back to the stored/user
    estimate. This is what gives a not-yet-closed cycle a sensible number instead of 0.
  - `movementSince`, `cardMovementMinorUnits`, `derivedOutstanding`, `allCards`.
- `data/repository/CreditCardBillingService.kt` keeps the card row and its linked `CREDIT_CARD`
  bill in step: auto-creates the MONTHLY linked bill if missing, writes `billAmountMinorUnits`
  (the live statement) on the account, and syncs the head occurrence amount.
- `domain/billing/BillRecurrenceService.kt` amounts card occurrences from the settled cycle's
  statement (previous cycle when the due date still sits inside the open cycle), from
  `estimateStatementForCycle` for future cycles, and only from the stored estimate as the last
  resort; it touches only the head occurrence while it is unpaid with `paidAmount == 0`.
- `data/repository/AccountingService.kt` re-runs the card refresh after every recalculation;
  `MoneyManagerApp.kt` and `MainActivity.kt` refresh all cards on cold start and on foreground.

## B2. The bug the device exposed, and its fix

- The original mapping (`due − 20 days − 1ms`) put a payment made on the **24th** into the
  cycle that was still running for a card whose cycle starts on the **3rd**, so the payment was
  credited against the wrong statement and the linked bill grew for the wrong month.
- `CreditCardStatement.cycleForDueDate` was replaced by `cycleSettledByDueDate` everywhere
  (`BillRecurrenceService`, `AccountDetailViewModel`, tests) and the class doc was rewritten to
  state the rule: *the cycle that closed most recently before the due date*.
- Result on the reference card (cycle start day 3, due 24th): the 24 Oct occurrence now settles
  the `03 SEP – 02 OCT` cycle, and the `03 OCT – 02 NOV` cycle is settled by the due date that
  lands after 2 Nov.

## B3. Where the statement is shown

- **Account detail (card screen)** — `AccountDetailViewModel` now exposes
  `currentStatementMinorUnits` and `statementDueDateEpochMillis` (it injects `BillDao`); the
  statement shown is `billAmountMinorUnits ?: outstanding`, and the due date is the occurrence
  that settles the *current* cycle, falling back to `account.dueDate`, then to the derived
  default. The screen renders `Statement 03 OCT - 02 NOV • Due 24 Nov 2026` under the hero
  outstanding.
- **Cycle summary** — per user request the "This statement cycle starts on day X of each month"
  and "Statement so far …" lines were removed from `CycleSummaryCard`, together with the now
  unused `billingDay`/`statement`/`dueDateEpochMillis` parameters.
- **Card form** — a read-only "This cycle's statement" row, and the estimate field only when the
  user's number differs from it; saving refreshes the card so the bill is written back.
- **Accounts list** — a card with a computed statement is labelled `Outstanding`; only a card
  that has never been refreshed still reads `Estimated outstanding`.

## B4. Inactive accounts moved off the live list

- The first attempt (an "Inactive accounts (N)" section inside the same list) was verified on
  device and then **replaced on user request**: mixing the sections on one screen made the list
  hard to scan.
- `AccountsScreen.kt` now keeps two lists, `activeAccounts` and `inactiveAccounts`, and a
  segmented **`MmChip` switcher** right under the tab row — `Active (n)` / `Inactive (n)` — which
  only appears when there is at least one inactive account.
- The inactive view is its own screen state: header reads `Inactive outstanding` (or `Inactive
  balance`) with `n account(s) • switched off`, and only the switched-off accounts are listed.
- With nothing inactive the switcher disappears and `showInactive` can never hold; with zero
  active accounts the empty state says "No active accounts … your 1 switched-off account is in
  the Inactive view" and offers **Show inactive** instead of a misleading "No credit cards yet".
- Inactive accounts are never deleted — they stay reachable, editable and re-enableable from
  this view (verified both directions on device).

## B5. New and updated tests

| Test class | What it pins down |
| --- | --- |
| `domain/billing/CreditCardStatementTest.kt` | due-date → cycle mapping (due before/after cycle end), cycle shift when the start day changes, default due date = cycle end + 20 (Oct 24 for start day 4), future cycle has no statement, closed cycle = outstanding at close, running cycle = current outstanding |
| `data/CreditCardStatementServiceTest.kt` (Robolectric, in-memory DB) | linked bill + occurrence created, current bill amount follows the cycle and the occurrence, refunds move the statement, closed cycle keeps its amount, future-cycle estimate from the prior cycle, estimate walking back several cycles, card-screen bill amount = statement, missing linked bill created on refresh, a non-card never gets a card bill |
| `ui/accountdetail/AccountDetailStatementTest.kt` | card screen shows statement + due date from the linked bill, a card without a linked bill still gets a cycle-derived due date, a non-card shows no statement |
| `ui/creditcardform/CreditCardFormCheckpointTest.kt` | updated for the new `CreditCardFormViewModel(db, io, accounts, transactions, statementService, creditCardBillingService)` constructor |

Test delta since Addendum A: 187 → **202** unit tests before this addendum's final edits.

## B6. Commands executed for this addendum, and their real results

| Command | Result |
| --- | --- |
| `./gradlew :app:compileDebugKotlin --no-daemon` | **BUILD SUCCESSFUL** after every batch of edits |
| `./gradlew :app:testDebugUnitTest --no-daemon` (credit-card classes) | **BUILD SUCCESSFUL in 46s** |
| `./gradlew :app:testDebugUnitTest --no-daemon` (full, earlier in this addendum) | **BUILD SUCCESSFUL — 33 classes, 202 tests, 0 failures, 1 skipped** |
| `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon` (final) | **BUILD SUCCESSFUL in 2m 1s — 34 test classes, 208 tests, 0 failures, 0 errors, 1 skipped; lint 0 errors, 298 warnings, 2 hints (pre-existing informational)** |
| `./gradlew :app:assembleDebug --no-daemon` | **BUILD SUCCESSFUL**, every time |
| `adb install -r -t app/build/outputs/apk/debug/app-debug.apk` | `Performing Streamed Install … Success` (multiple times) |

**Device verification (`SM_M107F`, package `com.moneymanager.app.debug`):**

- Card detail: `Outstanding ₹16,055`, `Statement 03 OCT - 02 NOV • Due 24 Nov 2026`, cycle
  `03 OCT - 02 NOV • Current`, Spending `₹23,055`, Payments & credits `₹7,000`; the removed
  "This statement …" lines are gone.
- Inactive flow: disable card → Accounts / CREDIT CARDS shows `Total outstanding ₹0`,
  `0 accounts`, chips `Active (0)` `Inactive (1)`, empty state "No active accounts … Show
  inactive" → inactive view shows `Inactive outstanding ₹16,055`, `1 account • switched off`
  and only the Regalia card → re-enable from the edit form → back to `Total outstanding
  ₹16,055`, `1 account`, switcher gone.
- Live DB pulled with `run-as` confirmed `active=0` and then `active=1` for card id 1, and
  `outstandingMinorUnits = billAmountMinorUnits = 1,605,500`.
- Final build on device: the card now reads `Outstanding` (not `Estimated outstanding`) because a
  computed statement exists; the inactive round trip was repeated end to end — `Active (0)` /
  `Inactive (1)` chips → **Show inactive** → `Inactive outstanding ₹16,055` with the card marked
  `Inactive` → re-enable from the editor → `Total outstanding ₹16,055`, `1 account`, switcher
  gone. Switching tab away and back lands on **Active** again.

## B7. A test bug this addendum caused, and its fix

- The first full-suite run of this addendum **hung** for 15 minutes in
  `AccountDetailStatementTest.aNonCardAccountHasNoStatement`. `jstack` showed the Robolectric main
  thread parked inside `runBlocking { viewModel.stateFor(id).first { ... } }` while the
  ViewModel's `stateIn(viewModelScope)` transform waited for that same looper.
- Cause: reading `billDao.findByLinkedAccountId(...)` **inside** the `combine` transform made the
  transform suspend mid-way; the resumption is posted to `Dispatchers.Main`, which a main-thread
  blocked `runBlocking` can never service. Before that change the transform was pure computation
  and ran inline, so the same test passed only by luck.
- Fix in the test (not in production, where the looper keeps running): `awaitState()` collects on
  `Dispatchers.Default` while the test thread pumps the Robolectric looper with
  `shadowOf(Looper.getMainLooper()).idle()` - the pattern `BillsViewModelTest`,
  `CreditCardFormCheckpointTest` and the other ViewModel tests already use.

## B8. Instrumentation tests now run, and what is still unverified

- `./gradlew :app:connectedDebugAndroidTest --no-daemon` on the real device (`SM-M107F`,
  Android 11): **BUILD SUCCESSFUL in 1m 34s - 7 tests, 0 failures, 0 errors, 0 skipped**:
  `MigrationFullChainTest` (v1/v4/v8 chains), `Migration9To10Test`, `Migration10To11Test`,
  `ThemeRenderingTest` (light + dark). This closes the long-standing "needs a device" gap from
  section 11 and A6.
- Still unverified: signed release APK (`ANDROID_KEYSTORE_PATH` and the signing secrets are not
  available here - only `app-release-unsigned.apk` is built), `ApkVerifier` package/version/signer
  checks against that signed output, and GitHub Actions (not executed from this working tree).
- Nothing else: the final `testDebugUnitTest + lintDebug + assembleDebug` run, the connected
  Android test run and the on-device walk above all completed on this working tree.
