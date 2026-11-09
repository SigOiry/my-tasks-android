# GitHub release updater (1.1.0 / code 2)

## Release metadata generation

After building with the **existing** release identity:

```sh
bash build.sh
python3 release_metadata.py build/my-tasks.apk \
  --expected-certificate YOUR_EXISTING_64_HEX_CERTIFICATE_SHA256
# Optional: --build-tools "$ANDROID_HOME/build-tools/35.0.0" --out-dir release-assets
```

The script runs `apksigner verify --verbose --print-certs` and `aapt2 dump badging` against the actual signed APK. It derives all fields from that APK and writes `update.json` and `SHA256SUMS`; it never reads a signing key/password. `--out-dir` writes metadata only: copy the same verified APK there as `my-tasks.apk` yourself. Publish the APK, metadata and checksums together, then publish the stable release. Do not publish a draft/prerelease, metadata for another APK, signing material, or a GitHub token. No app credentials or release automation secrets are needed.

Exact JSON contract (real values must come from the script, not this illustration):

```json
{
  "schema_version": 1,
  "version_code": 2,
  "version_name": "1.1.0",
  "package_name": "com.simon.mytasks",
  "apk_asset": "my-tasks.apk",
  "sha256": "<64 hexadecimal characters: signed APK SHA-256>",
  "size": 123456,
  "certificate_sha256": "<64 hexadecimal characters: current APK signer SHA-256, no colons>"
}
```

`version_code`, `schema_version` and `size` are JSON integers, never strings/floats. The APK asset must be exactly `my-tasks.apk`; metadata exactly `update.json`. `size` must also agree with GitHub's APK asset size. Both assets must be uploaded assets from the same release/tag returned by the fixed latest-release endpoint. Extra metadata keys are ignored for forward compatibility. Schema 1 supports one **current** signer only, not multi-signer packages or signing-key rotation.

## User flow

1. **Install the latest updater-enabled release (1.2.0 or later) manually once over 1.0.0**, without uninstalling. Version 1.0.0 has no updater. The updater was introduced in the 1.1.0 source; installation must retain the same package and signing identity.
2. On the first foreground/resume each local calendar day, 1.1.0 queries the public endpoint `https://api.github.com/repos/SigOiry/my-tasks-android/releases/latest`. It works before configuring a task server. No background poll/job is added for app updates.
3. Stable means `draft=false`, `prerelease=false`, and a valid `published_at` timestamp. Only an integer metadata version code **greater** than the actually installed version is offered; version names never determine order. Equal or older releases report "up to date" when checked manually.
4. A newer release shows its version and bounded **plain-text** notes, with **Update / Later**. The warning asks the user to save unsaved task fields and chat drafts before installing. No HTML, WebView, auto-clicked links or release-supplied intents are used.
5. Only **Update** starts an APK download. A cancellable progress dialog covers download/verification. The file is held in private app cache, not public Downloads.
6. The updater verifies exact byte count, SHA-256, APK package, actual version code/name, and APK current signer against the **currently installed** package certificate. API 28+ uses `GET_SIGNING_CERTIFICATES` / `getApkContentsSigners`; API 26–27 uses `GET_SIGNATURES`. Missing/multiple signers fail closed. The metadata certificate must also match the installed certificate. A same-version/downgrade or different package/signer is never forwarded for installation.
7. Android 8+ requires permission for My Tasks to request installs. After accepting/download verification, the app explains **Allow from this source**, opens the per-app Android settings only on user opt-in, and requires returning to the app. Denial cancels/deletes the pending file. Permission is not a silent-install permission; users can revoke it later.
8. Returning from permission settings resumes only an in-memory, previously accepted file. The entire file/package/version/signer check runs again on the dedicated worker. After long background pauses it is repeated before dispatch. Consent expires after 20 minutes. Process death discards consent; no installer opens automatically on restart.
9. A temporary random `content://com.simon.mytasks.updates/apk/<capability>` grants **read only** to an explicit system package-installer activity. The provider is non-exported, has no general filesystem mapping, allows exactly the current accepted URI only while installing, rejects write modes/inserts/updates/deletes, and never returns filesystem paths. No arbitrary APK paths or externally supplied install intents are accepted. The Android system confirmation UI remains mandatory. No root, silent installation, TLS bypass or disabling Play Protect is used.
10. Installer result/cancellation/error revokes the grant and deletes the file. Cancellation during network streaming deletes the partial file when its bounded request unwinds. After process death or a successful self-update, stale private cache files are deleted on next updater initialization. An already-open read descriptor cannot be recalled, but contains only the verified public APK.

## Daily, lifecycle and failure policy

- The local-day attempt marker is persisted in separate `app_updater` preferences, independent of connection/account storage. A manual check also counts as today's attempt. Failed daily checks are **not retried automatically that day**, including after rotation, process restart or reconnect; manual checks are always available in **Settings → Check for updates**. The next different local date permits a new automatic attempt; an unaccepted offer retained from an earlier day is discarded and refreshed on foreground. An already accepted download/permission/install operation remains single-flight. Changing time zone/clock can change that date; there is no promise of one check per rolling 24 hours.
- The process-owned state machine shares a single in-flight check with a concurrent manual request; the latter promotes the result to user-visible status rather than starting another request. Task/chat network work cannot block the updater: it has a separate single-thread executor.
- Workers use application context and immutable operation captures, not Activity/server/account credentials. A generation token ignores canceled/obsolete callbacks. A weak foreground Activity attachment dismisses dialogs on pause and renders retained state on return/recreation. No Activity is retained by updater network workers. No check is repeated for rotation.
- Automatic failures are quiet. Manual offline/TLS/timeout/rate-limit/missing-release/bad-metadata failures are shown; no-update status is also shown. Download/verification/permission errors after acceptance are visible. Nothing retries or installs silently.
- The transport never accepts backend headers/tokens. It uses HTTPS and the platform trust store, validates every redirect, permits at most five redirects, has 15-second connect and 20-second read timeouts and a 180-second per-body streaming deadline. Release JSON is capped at 512 KiB, metadata at 16 KiB, displayed notes at 8,000 characters and APK at **100 MiB**. A smaller declared APK size is also a hard streaming cap.
- Only the fixed repository's API endpoint and GitHub release-download paths are permitted initially. Redirects are confined to validated GitHub download paths and `release-assets.githubusercontent.com` / `objects.githubusercontent.com` production-release-asset paths. User-info, fragments, non-HTTPS, unexpected ports, traversal and encoded path separators/dots are rejected. GitHub changing its delivery hosts/paths will fail safely and require a new app build.

## Tests and remaining runtime boundary

`bash test.sh` runs real pure-JVM version/day/URL/size/hash/package/signer policy, strict release/metadata parsing, bounded stream, single-flight/consent state-machine, and redirect/error tests, plus Python release-metadata generator tests. The JVM parser uses a SHA-256-pinned `org.json` test dependency downloaded once into ignored `build/test-lib`; the Android APK uses platform `org.json`, not the test jar. The core policy was developed through observed RED → GREEN cycles. Network mocks test the real transport; they do not prove Android framework behavior.

Optional real public-network smoke test after `bash test.sh`:

```sh
"$JAVA_HOME/bin/javac" --release 8 -cp build/tests:build/test-lib/json.jar \
  -d build/tests tests/UpdateLiveCheck.java
"$JAVA_HOME/bin/java" -cp build/tests:build/test-lib/json.jar \
  com.simon.mytasks.UpdateLiveCheck 2 YOUR_INSTALLED_CERTIFICATE_SHA256
```

No usable device/emulator/KVM was available for this implementation. APK compile/dex/signature/manifest verification and real GitHub metadata checks are **not** proof that permission UI, provider grants or installation work on a device. Test the matrix in `TESTING.md` on API 26 and a current Android device before describing the install flow as runtime-verified. Existing task/chat code paths were retained and their JVM regression suite is run, but live task/chat/device regressions were not exercised here.

## Official API references

- `https://docs.github.com/en/rest/releases/releases#get-the-latest-release`
- `https://developer.android.com/reference/android/content/pm/PackageManager#getPackageArchiveInfo(java.lang.String,%20int)`
- `https://developer.android.com/reference/android/content/pm/SigningInfo#getApkContentsSigners()`
- `https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()`
- `https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES`
- `https://developer.android.com/reference/android/content/Intent#ACTION_INSTALL_PACKAGE` (deprecated since API 29, retained platform intent; requires `REQUEST_INSTALL_PACKAGES` and a readable content URI).
- `https://developer.android.com/privacy-and-security/security-tips` (non-exported providers and temporary read grants).
