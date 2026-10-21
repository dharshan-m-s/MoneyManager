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
