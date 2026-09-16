# GitHub publishing and CI/CD guide

This project is prepared to build the Android **release** variant and publish a signed APK through GitHub Actions.

## 1. Install the local toolchain

Install Android Studio with SDK Platform 36, Build-Tools 36.0.0, and JDK 17. The included `gradlew`/`gradlew.bat` bootstraps Gradle 9.5.0 on the first build, so a separate Gradle installation is not required. The project targets Android 16 (API 36), which is required for new Google Play submissions and updates from August 31, 2026. AGP 9.3.0 requires Gradle 9.5.0 and JDK 17. The GitHub workflows therefore install Gradle 9.5.0 and Temurin 17.

## 2. Create the release signing key

Windows PowerShell:

```powershell
./scripts/create-keystore.ps1 -Output .\money-manager-release.jks -Alias money-manager
```

Linux/macOS:

```bash
./scripts/create-keystore.sh money-manager-release.jks money-manager
```

Back up the keystore and both passwords securely. The same signing identity must be used for every update of the distributed application.

## 3. Create the GitHub repository

Create the GitHub repository `dharshan-m-s/MoneyManager` (or change `UPDATE_GITHUB_OWNER` / `UPDATE_GITHUB_REPOSITORY` in the build if you use a different repository) and push this project:

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

Create:

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

Never put the keystore, passwords, or the Base64 value in Git.

## 5. Let CI validate changes

Push normally:

```bash
git add .
git commit -m "Improve bills UI"
git push
```

The **Android CI** workflow runs unit tests and creates a debug APK artifact.

## 6. Publish the first release

For the first release, use:

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will:

1. validate the release version;
2. restore the signing key from GitHub Secrets;
3. run unit tests;
4. build `release`;
5. sign the APK;
6. publish a SHA-256 checksum alongside the APK;
7. attach `MoneyManager-1.0.0.apk` to GitHub Release `v1.0.0`.

## 7. Publish future updates

Make the code change, test it, then increment the release version:

```bash
git add .
git commit -m "Improve transaction navigation"
git push

git tag v1.1.0
git push origin v1.1.0
```

Every release tag must have a higher Android `versionCode` than the previous installed release. The release workflow derives a deterministic Android `versionCode` from the semantic version (`major * 1,000,000 + minor * 1,000 + patch`), so every increasing release version also has an increasing version code.

## 8. User update path

The GitHub Release page is the distribution source. Give users the APK from the latest release. Android can install a newer APK over the older one only when the application ID and signing identity are compatible and the version code is newer.

The app itself currently does not silently self-install updates. A future in-app update checker can link users to the latest GitHub Release without weakening Android's installation protections.

## 9. Pre-release checklist

- Test the signed `release` APK on a clean device.
- Install `v1.0.0`, create accounts, transactions, bills, and budgets.
- Install `v1.1.0` over `v1.0.0` and verify the existing Room database remains intact.
- Verify account selection, bills, cash, imports, backup/export, and navigation after updating.
- Keep multiple secure backups of the signing keystore.
- Never publish personal transaction exports, local configuration files, or signing credentials.
