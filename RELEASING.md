# Releasing My Tasks

## Trust model

The embedded updater reads this repository's **latest published stable GitHub Release**, not the latest source commit, branch or prerelease. A source push alone does not prompt phones to update.

The APK must keep package `com.simon.mytasks` and the original official signing certificate. The updater compares the integer Android `versionCode`, checks downloaded size and SHA-256, and validates the actual APK's package/version and signing identity against the installed app before handing it to Android. Never publish a release built with a newly generated local/CI key as an official update.

Signing keys and passwords remain outside GitHub. This repository's CI runs tests only; it does not hold the release key or publish APKs.

## Release checklist

1. Increase `versionCode` monotonically and set the human-readable `versionName` in `android/AndroidManifest.xml`.
2. Run the policy tests and build the APK with the **original signing identity** supplied via private environment variables. Verify the output with `apksigner verify --print-certs` and check package/version with `aapt2 dump badging`.
3. Prepare exactly these release assets:
   - `my-tasks.apk`
   - `update.json`
   - `SHA256SUMS`
4. `update.json` schema:
   ```json
   {
     "schema_version": 1,
     "version_code": 2,
     "version_name": "1.1.0",
     "package_name": "com.simon.mytasks",
     "apk_asset": "my-tasks.apk",
     "sha256": "<actual APK SHA-256, 64 lowercase hex characters>",
     "size": 0,
     "certificate_sha256": "<actual APK signing-certificate SHA-256>"
   }
   ```
   The version and zero size above are explanatory placeholders: derive the real values from the exact signed APK. Do not hash an earlier build and then rebuild before uploading.
5. Commit the corresponding source, pass review/CI and push. Create the release as a **draft**, targeting that exact source commit and a tag such as `v1.1.0`.
6. Upload all assets. Read back their names, sizes and digests, download and compare them with the local artifacts, and verify the tag/source version.
7. Publish the draft as stable (not prerelease) and mark it latest. Verify `/repos/SigOiry/my-tasks-android/releases/latest` without authentication. Never expose incomplete update assets to clients as a stable latest release.

## User installation

Version 1.0.0 has no updater; users must manually install the first updater-enabled version once. All official releases retain the original signing identity so installation preserves app data subject to Android's normal behavior.

A newer version prompts the user. Acceptance downloads/verifies the APK, then Android may require **Allow from this source** and a separate installation confirmation. There is no silent/root installation or platform-security bypass.

Update checks occur on the first foreground launch per local calendar day; Settings provides a manual check. These are foreground app-version checks, separate from task-notification background polling. Android device testing remains required for actual dialogs/permission/install behavior.
