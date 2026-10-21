# Money Manager Update Architecture

Money Manager now keeps update checking, release parsing, version comparison, APK selection, download, verification, installation, and background checks under the `com.moneymanager.app.updater` package instead of coupling GitHub requests to the Settings UI.

The architecture is intentionally similar in separation of concerns to the updater module used by SuvMusic: a dedicated update area, structured release metadata, state-driven UI, and CI-backed GitHub Releases. SuvMusic's repository documents a dedicated `updater` area and describes its stable installation path through GitHub Releases. The Money Manager implementation is independently written and does not copy SuvMusic or LibreTorrent source files.

## Production update flow

```text
Settings → App Updates
        ↓
UpdateViewModel
        ↓
UpdateRepository
        ↓
GitHubReleaseDataSource
        ↓
GitHub Releases API
        ↓
Stable release + deterministic APK asset
        ↓
Semantic version / release versionCode comparison
        ↓
Download APK + SHA-256 digest check when GitHub supplies a digest
        ↓
Package name + versionCode + signing-certificate verification
        ↓
FileProvider → Android package installer
        ↓
Return to app → verify installed version
```

## GitHub configuration

Default production repository:

- Owner: `dharshan-m-s`
- Repository: `MoneyManager`
- API: `https://api.github.com`

The owner and repository are BuildConfig fields and can be overridden for a build through:

- `UPDATE_GITHUB_OWNER`
- `UPDATE_GITHUB_REPOSITORY`

The updater expects a published stable GitHub Release with an APK named:

`MoneyManager-v<version>-release.apk`

The release workflow now produces that exact filename and uploads its `.sha256` companion file.

## Release/version contract

The release workflow maps semantic version `M.m.p` to the monotonically increasing Android `versionCode` formula:

`M * 1_000_000 + m * 1_000 + p`

The updater derives the same release code from the release tag so release ordering is deterministic without relying on lexicographic string comparison.

## Safety

The updater refuses to install an APK when:

- the file is missing or empty
- the package name does not match the installed application
- the version code is not exactly the expected release code
- the APK signing certificate does not match the installed application's signer history
- the GitHub asset digest is present and does not match the downloaded APK

All downloaded files remain in the app cache and are exposed to the Android package installer through `FileProvider`.

## Cache policy

Downloads live in `cacheDir/updates`. The policy is explicit and covered by
`app/src/test/java/com/moneymanager/app/updater/data/UpdateCachePolicyTest.kt`:

- Each download writes to `<apk>.part` and is renamed to the final `.apk` only after the integrity
  checks pass, so a crash or a cancel never leaves a half-file that looks installable.
- `deleteApksExcept(versionName)` keeps exactly the APK (and any in-flight `.part`) for the version
  about to be installed, and deletes every other stale `.apk` **and** abandoned `.part`. A partial
  download for a version we are not installing can never be resumed, so it is pruned; the partial
  for the target version is preserved so an in-flight download is not interrupted.
- `deleteOldVersions(keepFile)` keeps only the file being installed after a successful hand-off.
- `cleanCache()` (full wipe) exists for explicit user-driven cleanup.
- No cache-cleaning path ever deletes user backups, the database, receipts or any persistent
  document — those live outside `cacheDir` and are never enumerated by these methods.
