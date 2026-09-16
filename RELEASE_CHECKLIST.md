# Money Manager release checklist

## Before v1.0.0

1. Create and back up the production signing keystore.
2. Confirm `applicationId` is the permanent ID you intend to ship.
3. Confirm `versionName` is `1.0.0` and the first release `versionCode` is derived by CI as `1000000` (or choose and document another monotonic baseline).
4. Test `debug` on a clean device.
5. Build `release` through GitHub Actions with the real signing secrets.
6. Install the release APK, create real test data, then install the next release over it and verify the database survives the update.
7. Do not commit private CSV exports or the signing key.

## Every release

1. Increase the semantic version and ensure `versionCode` is higher than every distributed build.
2. Run the GitHub CI checks.
3. Push a `vX.Y.Z` tag only after the release candidate is accepted.
4. Download the APK from the GitHub Release and verify it is signed with the same release identity as the previous release.
5. Test the update over the last public build before sharing the new release.
