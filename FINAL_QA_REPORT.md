# Final QA Report — Money Manager

> **Historical document — superseded.** The PASS tables below describe an earlier commit
> (database schema version 9, 40 unit tests). They are kept for the record but the numbers and
> migration references are **out of date** and must not be read as current status. The repository
> now ships schema **version 11** with a full 1→11 migration chain, and the unit/Robolectric suite
> has grown well beyond 40 tests. See the current final report (Section 19 deliverable) and
> `docs/` for what actually works on this commit. Do not treat any PASS line below as applying to
> the current tree.

## Scope

This pass rebuilt the GitHub update system around a dedicated updater package and then audited the surrounding navigation, release, manifest, database-migration, and publishing surfaces.

The updater is independently written using the proven separation pattern seen in SuvMusic's dedicated updater area and GitHub Release distribution model. No source files were copied from SuvMusic or LibreTorrent.

## Production-readiness pass (latest)

An in-progress change set was found mid-way and broken: the project did not compile, and the Spend Areas
month selector could crash at runtime. The pass was completed and its defects fixed at the root:

**Found and fixed**

| Issue | Root cause | Fix |
|---|---|---|
| Project did not compile (7 Kotlin errors) | `SpendSummaryViewModel` mixed `Money` and `Long` types across its streams; a `flatMapLatest` without the `ExperimentalCoroutinesApi` opt-in; an impossible `MutableStateFlow(0L)` | Retyped all streams as `Long` minor units, added the opt-in, removed the impossible default |
| Crash on opening Spend Areas (all chips shared `fillMaxWidth`) and on selecting the oldest month (`months[selectedIdx + 1]` out of bounds) | Selector built as a `Row` of 12 same-width chips; previous-month label read without a bounds check | Replaced with a 6-month bar graph (one bar = one month's total), and the comparison now goes through `previousMonthLabel()`, which returns null past the oldest month |
| Inactive accounts crashed the Accounts screen when expanded | A `LazyColumn` rendered inside a `LazyColumn` item with unbounded height, and inactive rows had `onClick = {}` | Inactive accounts are now sibling `LazyColumn` items after a collapsible header; every inactive row opens its real detail screen and its real editor |
| Editing a transaction on an inactive account showed a blank Account field | Picker only resolved accounts from the active-accounts stream | The edit screen now shows the transaction's actual account, marked "(inactive account)", read back from the same account ID |
| Dashboard Import shortcut (one-time use) | Import was the only quick action that never recurs after migration | Replaced with Bills (real Bills screen). Import remains in Settings → Data → Import a statement and in the first-run empty state |
| Account totals double-counted hidden rows pre-audit | Totals summed only `active` rows within each tab; unchanged here, verified | Verified totals exclude inactive accounts; the inactive header states they are hidden from totals |

**Data-flow verification (no code change needed — already real)**

- Credit card save writes `outstanding`/`creditLimit` (user-entered source values) through
  `accountDao.update`; `availableLimit` is derived as limit − outstanding, never stored independently.
  The accounting engine preserves user-entered card values when the ledger cannot derive them.
- Account edit persists `active`/`hide`/`hideTransactions` via a real `accountDao.update` plus
  `accountingService.recalculateAccounts`, and back-up is scheduled after the write.
- Deletion of a card is a soft delete (`deleted` flag); transactions keep their `accountId`.
- All add/edit-transaction account pickers use `observeActive()`, so inactive accounts never appear
  as new-transaction targets; historical edits keep their original account.

**Static audit**

- No TODO/FIXME/placeholder/mock implementations; no no-op click handlers; no hardcoded ₹ amounts.
- Logging restricted to WARN/ERROR app-wide, with no transaction, balance or account data in log output.

**Verification this pass**

| Check | Result |
|---|---|
| `compileDebugKotlin` | PASS — 0 errors, 0 warnings |
| `testDebugUnitTest` | PASS — 40 tests, 0 failures, 0 errors, 1 skipped (pre-existing) |
| `assembleDebug` | PASS — debug APK produced |
| `assembleRelease` (R8, resource shrinking, `lintVitalRelease`) | PASS — unsigned release APK produced (signing happens in CI) |
| Second regression audit of this pass's own diff | PASS — removed an unused import, a redundant spacer and a duplicated caption line; final compile + tests re-run green |

## Spec re-audit + One UI 9 premium pass (final)

A section-by-section re-audit against the original 49-point prompt found one remaining functional gap,
then a visual refinement pass was applied:

**Functional fix**

- **Spec 19 — Spend Areas drill-down scope.** The category detail screen showed ALL-TIME transactions
  ("All time" pill) while the summary above it showed the selected month. Now both screens share the
  selected month window (`SpendAreasSelection` singleton written by `SpendSummaryViewModel.selectMonth`),
  backed by a new month-bounded `TransactionDao.observeByCategoryInRange` query. The drill-down header,
  pill and net-spend figure are scoped to the same month the user selected.

**One UI 9 refinements (token-level, so every screen inherits them consistently)**

- **Tabular numerals** on all financial styles (`amountHero`, `amountLarge`, `amountRow`, new
  `amountTile`): digits render with equal widths, so amounts align in columns and stop jittering
  when values change.
- **Larger, calmer screen titles** (20→22sp, tightened letter-spacing) — One UI's confident header.
- **Softer, larger card radius** (20→22dp) and sheet radius (28→30dp); cards now use a subtle 1dp
  ambient elevation with a hairline 0.5dp tonal edge instead of a hard 1px border.
- **Rounded-square icon containers** (squircle-feel, radius/2.45) with slightly deeper tint — the
  One UI 9 icon identity — applied once in `MmIconBadge` so every list, header and badge updates.
- **One UI 9 FAB shape** (22dp rounded square, 18dp for the extended speed-dial) across dashboard,
  cash, account detail, categories, accounts and bills.
- Dashboard summary tiles use the new tabular `amountTile` style.

**Verification of this final pass**

| Check | Result |
|---|---|
| `compileDebugKotlin` | PASS — 0 errors, 0 warnings |
| `testDebugUnitTest` | PASS — 40 tests, 0 failures, 1 skipped (pre-existing) |
| `assembleDebug` | PASS |
| `assembleRelease` (R8 + shrinking + lintVitalRelease) | PASS — unsigned (signed in CI) |
| `docs/UI_DESIGN_SYSTEM.md` updated to match shipped tokens | PASS |

**Spec sections deliberately verified and left unchanged** (already satisfied): cash income/spend via
the shared transaction domain model, single canonical transaction editor routed from every list,
bill payment matcher (merchant/amount/date/token similarity with manual selection and no duplicate
expenses), backup/restore + WorkManager auto-backup, PIN lock with PBKDF2 hashing and background
re-lock, month-bounded spend/income/category/category-percentage queries, import moved off the
dashboard while remaining in Settings, no destructive Room fallback migrations, GitHub Releases
updater with digest/signature validation, and the CI release workflow.

## Implemented

- Dedicated `updater` package with model/data/domain/presentation separation.
- Structured update states: idle, checking, up-to-date, available, downloading, downloaded, installing, offline, error.
- Public GitHub Releases API integration.
- Stable-release filtering; drafts and prereleases are ignored.
- Deterministic APK asset selection.
- Semantic version parsing and versionCode ordering.
- Network timeouts and structured failure categories.
- Retry/check-again behavior through cancellation of the previous request.
- APK download progress.
- GitHub asset SHA-256 digest validation when the API supplies a digest.
- Package name, versionCode, and signing-certificate validation.
- FileProvider-backed Android APK installation flow.
- Post-install version verification when the app resumes.
- Background update checks through WorkManager.
- Debug-only update diagnostics.
- Settings → App Updates navigation.
- CI/CD APK naming aligned with updater asset discovery.

## Verification performed here

| Check | Result |
|---|---|
| Pure updater version comparison tests | PASS |
| Stable/draft/prerelease release selection tests | PASS |
| Deterministic APK asset selection tests | PASS |
| Missing APK selection test | PASS |
| Navigation destination/handler audit | PASS — 33 / 33 |
| GitHub workflow YAML parse | PASS |
| Android manifest XML parse | PASS |
| FileProvider paths XML parse | PASS |
| Publishing artifact hygiene audit | PASS |
| Release APK naming consistency | PASS |
| GitHub owner/repository BuildConfig centralization | PASS |
| Update permissions/provider presence | PASS |
| Room migration chain through the receipt-attachment migration | PASS — 1→9 chain present, 8→9 SQL checked against the exported schema |
| ZIP integrity | PASS |
| `clean compileDebugKotlin` | PASS — 0 errors, 0 warnings |
| `testDebugUnitTest` | PASS — 40 tests, 0 failures |
| `assembleDebug` | PASS — debug APK produced |
| `assembleRelease` (R8, resource shrinking, `lintVitalRelease`) | PASS — unsigned release APK produced |
| Light-only colour literals outside `Theme.kt` | PASS — 0 remaining |
| `Color.White` on an accent surface | PASS — 0 remaining |

See `MODIFICATION_SUMMARY.md` for the full UI/UX pass and its verification table, and
`docs/UI_DESIGN_SYSTEM.md` for the design-system contract.

## Environment limitation

Compilation, unit tests, R8/`lintVital` release packaging and the scripted audits listed above all
run locally. What still requires a device or emulator is the visual and hardware behaviour:
dark-mode appearance, small and large screen widths, keyboard interaction, camera capture and
receipt persistence across a real app restart. Those are the remaining external verification
gates.

The repository is configured so GitHub Actions supplies Gradle 9.5.0 and performs the authoritative CI/release build.

## Release acceptance test still required on GitHub/device

1. Push the project to the intended GitHub repository.
2. Confirm CI is green.
3. Configure the four Android signing secrets.
4. Publish `v1.0.0` with the release workflow.
5. Install `v1.0.0` on a phone and create test financial data.
6. Publish `v1.0.1` through GitHub Actions.
7. From `v1.0.0`, open Settings → App Updates.
8. Confirm update detection and release notes.
9. Download and verify the APK.
10. Install without uninstalling.
11. Confirm the new version is detected after restart.
12. Confirm all transactions, accounts, categories, bills, budgets, and settings remain intact.

Do not label the first public release production-ready until that real upgrade test succeeds.
