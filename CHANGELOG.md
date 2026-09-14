# Changelog

All notable changes to this project. Format follows
[Keep a Changelog](https://keepachangelog.com/). This project is pre-1.0.

## Unreleased

### Added
- Instrumented Room migration tests (`MigrationTest`): composite 4 -> 9 and
  8 -> 9 paths using `MigrationTestHelper`; intermediate schemas 5/6/7 restored
  in `app/schemas` so the full path validates.
- Security hardening: app backup disabled via `<data-extraction-rules>` /
  `backup_rules`, monochrome launcher icon, lint clean (0 fatal / 0 errors).
- Production docs: `LICENSE` (MIT), `THIRD_PARTY_NOTICES.md`, `SECURITY.md`,
  `CONTRIBUTING.md`.

### Changed
- Project original code relicensed under the MIT License (previously
  unlicensed/proprietary). Third-party components keep their own licenses.

## Earlier development

The database schema reached version 9 through the migration chain in
`AppDatabaseMigrations.kt`:
- 1 -> 2 dedupe-fingerprint index for CSV import.
- 2 -> 3 `includeInStatistics` spend/income toggle.
- 3 -> 4 Moneyview-style account balance snapshot columns.
- 4 -> 5 manual balance override.
- 5 -> 6 category ordering + custom-category flag.
- 6 -> 7 unique (year, month) budget index.
- 7 -> 8 category artwork URI.
- 8 -> 9 transaction receipt attachments.

Feature history (pre-tag) covers account-aware transaction entry, cash
ledger/balance, bills with payment linking, CSV import from Moneyview exports,
budgets, categories, reimbursements, SAF + WorkManager backup/export, and
receipt capture. Release history before v1.0.0 was not previously tracked.