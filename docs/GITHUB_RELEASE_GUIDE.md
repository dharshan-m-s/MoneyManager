# GitHub publishing and CI/CD guide

This project builds the Android **release** variant and publishes a signed APK through GitHub Actions. The in-app **Update Center** (Settings → App Updates) consumes exactly what this pipeline publishes.

## 1. Install the local toolchain

Install Android Studio with SDK Platform 36, Build-Tools 36.0.0, and JDK 17. The included `gradlew`/`gradlew.bat` bootstraps Gradle 9.5.0 on the first build, so a separate Gradle installation is not required. The project targets Android 16 (API 36), which is required for new Google Play submissions and updates from August 31, 2026. AGP 9.3.0 requires Gradle 9.5.0 and JDK 17. The GitHub workflows therefore install Gradle 9.5.0 and Temurin 17.

## 2. Create the release signing key (one time)

Windows PowerShell:

```powershell
./scripts/create-keystore.ps1 -Output .\money-manager-release.jks -Alias money-manager
```

Linux/macOS:

```bash
./scripts/create-keystore.sh money-manager-release.jks money-manager
```

Back up the keystore and both passwords securely (offline, multiple copies). **The same signing identity must be used for every release, forever.** An update signed with a different key will be rejected both by this app's verifier and by Android itself.

## 3. Create the GitHub repository

Create an empty repository and push this project:

```bash
git init
git add .
git commit -m "Initial publishable release"
git branch -M main
git remote add origin https://github.com/YOUR-USER/YOUR-REPO.git
git push -u origin main
```

Before pushing, verify that the financial export CSV is not present. The private `moneyview-export.csv` used during development is intentionally excluded from the publishable repository.

## 4. Add GitHub Actions secrets

Repository → **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 contents of `money-manager-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `money-manager` |
| `ANDROID_KEY_PASSWORD` | Key password |

PowerShell to create the first secret value:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('.\money-manager-release.jks'))
```

Linux/macOS:

```bash
base64 -w 0 money-manager-release.jks
```

Never put the keystore, passwords, or the Base64 value in Git. The workflows read these secrets only to sign the APK; nothing secret is embedded in the app.

> **No `GITHUB_REPO` configuration needed.** The release workflow injects `${{ github.repository }}` into the build automatically, so the shipped APK always knows which repository's releases to query. Local builds default to an empty value, which cleanly disables the Update Center instead of guessing a repository.

## 5. Let CI validate changes

Push normally:

```bash
git add .
git commit -m "Improve bills UI"
git push
```

The **Android CI** workflow runs unit tests, lint, and creates a debug APK artifact. Debug APKs are never published as updates.

## 6. Publish a release (the only thing you ever have to do)

```bash
# 1. Commit your work
git add .
git commit -m "Improve transaction navigation"
git push

# 2. Tag the release with a semantic version and push the tag
git tag v1.1.0
git push origin v1.1.0
```

That is the entire release process. On every `vX.Y.Z` tag push, the **Release Android APK** workflow:

1. derives `VERSION_NAME` from the tag and computes the deterministic `versionCode = major * 1,000,000 + minor * 1,000 + patch` (minor/patch must be ≤ 999, which the workflow enforces);
2. restores the signing key from GitHub Secrets;
3. runs the unit tests, then builds and signs the production APK;
4. verifies the APK signature with `apksigner verify` and confirms with `aapt2` that the package name is `com.moneymanager.app` (never the `.debug` id) and that the APK's `versionCode`/`versionName` match the tag;
5. computes the SHA-256 checksum;
6. generates `update.json` (machine-readable manifest: `versionName`, `versionCode`, `tag`, `apk`, `sha256`, `mandatory`) and re-validates it against the actual artifacts before publishing;
7. creates the GitHub Release `v1.1.0` titled "Money Manager 1.1.0" with generated release notes, attaching exactly:
   - `MoneyManager-1.1.0.apk` — the signed production APK
   - `MoneyManager-1.1.0.apk.sha256` — the checksum file
   - `update.json` — the update manifest

The moment the release is published, every installed copy of the app can see and install it through the Update Center. You can also run the workflow manually from the Actions tab (**Run workflow**, enter the version) — use this only to re-publish a version that was deleted.

## 7. What the app does with it (Update Center)

**Settings → App Updates** checks GitHub Releases automatically on screen open and on "Check again".

| Step | What happens | Security |
|---|---|---|
| **Check** | `GET https://api.github.com/repos/{owner}/{repo}/releases?per_page=10` — HTTPS only, unauthenticated, no PAT. | No tokens in APK; public API only. |
| **Filter** | Skips drafts, prereleases, source archives, debug APKs and unrelated assets. Picks the deterministic `MoneyManager-X.Y.Z.apk` asset. | Nothing but the production asset is ever offered. |
| **Version gate** | Integer `versionCode` comparison (never string): update is offered only if release `versionCode` > installed `versionCode`. The `update.json` values are cross-checked against the tag/asset names and rejected on mismatch. | Older or equal versions are never offered. |
| **Download** | Streams the APK into `cacheDir/updates/` with progress, cancellation, duplicate-download protection, storage pre-check and cleanup of incomplete files. | Cache-only, never touches financial data. |
| **Verify** | In order: 1) file is a valid APK; 2) package id equals the installed app; 3) APK versionCode is strictly newer; 4) signing certificate SHA-256 equals the installed app's signing identity (fail-closed); 5) SHA-256 matches the published digest (asset digest from the API, else `update.json`). Any failure deletes the file and shows the reason. | Never installs on a failed check. |
| **Install** | `ACTION_INSTALL_PACKAGE` via a scoped `FileProvider` (`content://` URI, temporary read permission). If the "install unknown apps" permission is missing, the app routes to the system settings and resumes automatically on return. | Android's installer remains in control; user confirmation is never bypassed. |
| **After install** | Android restarts the app; on start (and every Update Center resume) the app detects the new versionCode, clears the completed update state and purges leftover download files. If the user cancelled the installer instead, the verified APK is kept and "Ready to install" is restored for a one-tap retry. | No stale "downloading" state survives an update. |

### Fallback behavior

- No newer stable release → **"You're up to date"** (only shown after a successful check).
- No internet / GitHub down / rate-limited / malformed response → an explicit error with **Try again**, never a false "up to date".
- Repository unconfigured (local unsigned builds) → the Update Center explains that it is not configured.

## 8. Optional: mandatory updates

`update.json` carries a `mandatory` flag (always `false` in the generated pipeline). To mark a release mandatory, edit the flag in the release's `update.json` asset after publishing — the Update Center will show a "recommended update" banner for it. The app never force-installs: Android's user consent requirements always apply.

## 9. Pre-release checklist

- Test the signed `release` APK on a clean device.
- Install `v1.0.0`, create accounts, transactions, bills, and budgets.
- Install `v1.1.0` over `v1.0.0` (through the Update Center, not adb) and verify the existing Room database remains intact.
- Verify account selection, bills, cash, imports, backup/export, and navigation after updating.
- Keep multiple secure backups of the signing keystore.
- Never publish personal transaction exports, local configuration files, or signing credentials.
