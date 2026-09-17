# Final QA Report — Money Manager

## Scope

This pass rebuilt the GitHub update system around a dedicated updater package and then audited the surrounding navigation, release, manifest, database-migration, and publishing surfaces.

The updater is independently written using the proven separation pattern seen in SuvMusic's dedicated updater area and GitHub Release distribution model. No source files were copied from SuvMusic or LibreTorrent.

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
