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

## Project structure

- `app/` Android application
- `docs/` release and design documentation
- `scripts/` release-keystore helpers

## Build

The project currently has a single Android application module. Debug builds use the `.debug` application-id suffix; production releases use the normal application id.

## GitHub CI/CD

The repository is prepared for GitHub Actions:

- `.github/workflows/ci.yml` runs tests, lint and a debug APK build on pushes/PRs.
- `.github/workflows/release.yml` builds, signs, checksums and publishes a production APK when a `vX.Y.Z` tag is pushed.
- The signing key is supplied through GitHub Actions Secrets and is never stored in Git. Release `versionCode` is deterministic from the semantic version, preventing accidental reuse of an older code.
- Every release carries `MoneyManager-X.Y.Z.apk`, its SHA-256 checksum, and an `update.json` manifest — the exact contract the in-app Update Center (Settings → App Updates) consumes for automatic update detection, verification and installation.

Read `docs/GITHUB_RELEASE_GUIDE.md` before creating the first public release.

## Private verification data

The original `moneyview-export.csv` was a private development verification dataset and is intentionally **not included** in the publishable repository. The full-statement harness skips unless `MONEYVIEW_REFERENCE_CSV` points to a local copy.

## Categories, backup, and safe updates

The transaction category grid is intentionally fixed and non-scrollable. It shows the curated categories on the transaction screen and opens the full category picker through **More categories**. The full picker supports creating custom categories with an optional user-selected picture. Category artwork is stored locally with the category record.

When **Auto Backup** is enabled and a backup folder has been selected through Android's document picker, every data write schedules a durable WorkManager backup. The same database snapshot, CSV export, and manifest files are refreshed in the selected folder; automatic backup is not dependent on the app staying open.

The database uses explicit Room migrations. Category artwork uses migration `7 -> 8`, so existing installations are upgraded without destructive data loss.

## Transaction receipts

Transactions support optional bill/receipt attachments. Users can capture a photo with the camera or choose an image from storage. Attachments are copied into app-private storage and linked to the transaction, so the app does not rely on a temporary external URI permission.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for the
full license text.

Third-party components (AndroidX, Compose, Room, Hilt, OpenCSV, Timber, Gson,
etc.) remain under their own licenses and are **not** covered by this project's
MIT license. See `THIRD_PARTY_NOTICES.md` for the full third-party attribution.

## Provenance and data privacy

- This is an independent, clean-room re-implementation. It is not affiliated
  with, endorsed by, or derived from any commercial money-management app. The
  MIT license covers only this project's own original material.
- **Moneyview** appears only as the name of a CSV import format this app parses
  (the format of the exports the owner receives) and the accounting conventions
  observed in those exports. No Moneyview source code, assets, or APIs are used
  or included.
- All launcher icons, category glyphs, UI, and layout assets are original works.
- No personal, financial, or private data is stored in this repository. The
  original verification export (`moneyview-consolidated-statement.csv`) is the
  application owner's private data and is intentionally excluded (see
  "Private verification data" below); the full-statement harness skips unless
  `MONEYVIEW_REFERENCE_CSV` points to a local copy.
