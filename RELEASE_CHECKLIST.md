# Release Checklist

Run every item **in order** for each release.

## 1. Pre-release verification (local)

- [ ] `./gradlew clean`
- [ ] `./gradlew assembleDebug` — clean debug build
- [ ] `./gradlew testDebugUnitTest` — 42 tests, 0 failures (1 optional skip)
- [ ] `./gradlew compileDebugAndroidTestKotlin` — androidTest compiles
- [ ] `./gradlew lintDebug` — 0 errors; 36 informational warnings expected/suppressed
- [ ] `./gradlew assembleRelease` — clean release build

## 2. On-device verification

- [ ] `./gradlew connectedDebugAndroidTest` — **must** run Room `MigrationTest`
       (composite 4→9 and 8→9) and any androidTest suites. Not run in CI w/o device.
- [ ] Manual dark-mode pass in **forced-dark** **and** system-follow modes:
      dashboard, transactions, bills, cash, accounts, categories, settings,
      import, search, dialogs, bottom sheets, date pickers, status/nav bars.
- [ ] Manual light-mode smoke test: confirm **no** light-mode visual regression.
- [ ] Real-data sanity: add/edit/delete transaction → balances update correctly
      in Dashboard, Cash, Spend Summary, Income Summary.
- [ ] Verify launcher icon renders correctly on default + themed-live-icon surfaces.
- [ ] Confirm launcher icon artwork is clean-room original (see audit FLAG).

## 3. Signing setup (one-time, or when keystore changes)

- [ ] Generate a 25-year keystore outside the repo:
      `keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 9125`
- [ ] Add `signingConfigs.release` in `app/build.gradle.kts` pointing at it.
- [ ] Add `keystore.properties` (gitignored) with `storePassword`/`keyPassword`/`keyAlias`.
- [ ] Verify `assembleRelease` produces a signed APK; check with
      `apksigner verify --print-certs build/outputs/apk/release/app-release.apk`.

## 4. Release artifact

- [ ] Versioning is tag-driven: no manual `versionCode`/`versionName` edits needed.
      Pushing tag `vX.Y.Z` derives both deterministically (see
      `docs/GITHUB_RELEASE_GUIDE.md` § 6). Verify the tag has NOT been used before.
- [ ] Confirm Room `DBVersion` unchanged (DB v9) or a new `schemas/` JSON + migration
      is added **and** covered by `MigrationTest.kt` in this same release.
- [ ] Update `CHANGELOG.md` (categories: Added / Changed / Fixed / Removed).
- [ ] Build signed release APK/Bundle; sanity-install on a device.
- [ ] If distributing through a store, upload AAB (`app-release.aab`) not APK.

## 5. Post-release

- [ ] Tag the commit (`vX.Y.Z`).
- [ ] Attach artifacts/release notes; mark `SECURITY.md` supported version bumped.
- [ ] Archive a copy of the keystore and passwords in a secure offline location.