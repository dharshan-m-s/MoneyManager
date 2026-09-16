# Money Manager

A privacy-first Android money manager built with Kotlin, Jetpack Compose, Room, and Hilt.

## Product highlights

- Account-aware transaction entry: **Home → Choose Account → Transaction** with no second account selector.
- Direct account flow: **Accounts → Account → Add Transaction**.
- Bills can link to an already-recorded transaction instead of creating a duplicate expense.
- Bill payment matching uses merchant/name similarity, amount similarity, and payment-date recency.
- Manual **Choose from Recent Transactions** fallback and **Paid by Cash**.
- Ledger-based cash balance calculation and minor-unit/paise money storage.
- Import, filtering, search, budgets, categories, reimbursements, backup/export, and transaction history.
- Responsive transaction rows that remain stable with long merchant names.
- Hardened navigation/back behavior and a consistent green/neutral visual system.

## Modules

- `app/` Android application
- `core/` shared core utilities

## Build variants

- `dev` — local development flavor
- `staging` — staging flavor
- standard release build

Published releases use `release`.

## GitHub CI/CD

The repository is prepared for GitHub Actions:

- `.github/workflows/ci.yml` runs tests and builds a debug APK on pushes/PRs.
- `.github/workflows/release.yml` builds, signs, and publishes a production APK when a `vX.Y.Z` tag is pushed.
- The signing key is supplied through GitHub Actions Secrets and is never stored in Git. Release `versionCode` is deterministic from the semantic version, preventing accidental reuse of an older code.

Read `docs/GITHUB_RELEASE_GUIDE.md` before creating the first public release.

## Private verification data

The original `moneyview-export.csv` was a private development verification dataset and is intentionally **not included** in the publishable repository. The full-statement harness skips unless `MONEYVIEW_REFERENCE_CSV` points to a local copy.

## Categories, backup, and safe updates

The transaction category grid is intentionally fixed and non-scrollable. It shows the curated categories on the transaction screen and opens the full category picker through **More categories**. The full picker supports creating custom categories with an optional user-selected picture. Category artwork is stored locally with the category record.

When **Auto Backup** is enabled and a backup folder has been selected through Android's document picker, every data write schedules a durable WorkManager backup. The same database snapshot, CSV export, and manifest files are refreshed in the selected folder; automatic backup is not dependent on the app staying open.

The database uses explicit Room migrations. Category artwork uses migration `7 -> 8`, so existing installations are upgraded without destructive data loss.

## In-app updates

Money Manager checks published stable GitHub Releases through a dedicated updater layer. The release workflow publishes a deterministic APK asset (`MoneyManager-v<version>-release.apk`) and a SHA-256 file. The app validates the package name, version code, signing certificate, and GitHub asset digest before handing the APK to Android's package installer. See `docs/UPDATER_ARCHITECTURE.md` for the update contract.
