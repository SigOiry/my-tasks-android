# Verification

## Version 1.2.0 / code 3 UI handoff

The signed code-3 APK was rebuilt with JDK 17 / Android SDK 35. Full output is in `build/build-v1.2.0.log`; package/version/min/target, v2/v3 signature and alignment checks passed. The signer matches the original code-2 APK. No connected device (`adb devices -l` is empty) or `/dev/kvm` is available: no native screenshots or runtime claims are made. See `UI-ACCEPTANCE.md` for composition, exact artifacts and the remaining physical-device matrix.

Presentation tests were run RED for missing date/filter/sort/source behavior, unsafe percent-encoded source controls, and missing acknowledgement-conflict copy, then GREEN after implementation. Reviewed corrections also ran RED/GREEN for exact Work/work project enumeration and Trash-only project inclusion; tests exercise selecting each exact identity, reset, view membership and stable ordering. Python XML-topology tests ran RED for missing scroll-safe resources, then GREEN for one compact header/controls/rows scroll and the preserved expanded layout. They inspect the actual layout XML resources used by the Activity, not Java source substrings, but do not inflate or measure Android views or prove gestures/focus/IME behavior. JVM tests run real pure presentation policy with deterministic dates/locales and synthetic in-memory test objects; no task server or live task data is accessed.

## Executed here

- Pure JVM test-first URL-origin cycle: initial implementation threw `IllegalArgumentException: Not implemented`; after implementation, `PASS 8 checks (url)`.
- Ordering cycle: initial constant comparator failed `AssertionError: project then priority then due; missing last`; implemented comparator passed.
- Cursor/event cycle: initial no-op cursor failed `AssertionError: advance on handled event`; implemented monotonic handling and bootstrap/permission decisions passed.
- Task path encoding cycle: initial identity function failed `AssertionError: task ID never changes URL path or query`; encoded segment implementation passed.
- Current suite: `bash test.sh` → **233 JVM assertions: Core 50, UI presentation 69, updater policy 51, release parsing 18, transport 15, consent/single-flight state 30; Python metadata generator 4 tests plus layout topology 2 tests**. Tests run real policy/parser/stream code; transport tests inject deterministic HTTP connections. The JSON JVM-only dependency is checksum-pinned and not bundled in the APK.
- Full APK build: `bash build.sh` → Java compilation, D8, aapt2 resource/manifest packaging, zipalign, persistent local signing and verification succeeded.
- APK v2/v3 signatures verified by SDK `apksigner`; minSdk 26 and targetSdk 35 reported by SDK `aapt2 dump badging`.
- No live task mutation was used for Android tests.

## Device acceptance matrix — NOT executed here

There is no connected Android device or usable emulator/KVM. These are required follow-up checks, not claimed passing results. Use a separate test database/server, not live tasks, for destructive/edit tests.

1. Install APK on API 26 and API 35+, launch, and connect to trusted HTTPS companion server. Confirm invalid token/HTTP/path/redirect/invalid certificate fail safely.
2. Fresh connection with historical events: no notification flood. New canonical task changes after baseline: notification appears when scheduled or Refresh is tapped.
3. Deny Android 13+ permission, then allow it. Confirm unread pending events survive denial; channel block/unblock behaves equivalently. Lock-screen content is generic; unlocked detail is visible. Tap opens correct task. Switch account then tap an old intent: old task data must not appear.
4. List grouping: distinct projects; high/medium/low priority; ascending deadlines; no deadline last. Search, Today (includes overdue), Week, All and Trash filters. Empty state and large text sizes.
5. Width <720dp uses navigation; width >=720dp uses side-by-side panes. Rotate, resize split-screen, switch dark/light while editing and during a chat request; selected task, draft, search/filter and scroll must remain.
6. Create, edit all fields, complete, soft-delete after confirm/cancel, restore. Refresh must show persisted values. Date validation rejects impossible dates; blank due/project sends JSON null.
7. Concurrent external edit: save gets 409; draft remains, no forced overwrite; Reload latest requires discard confirmation and fetches current revision. Displayed notification token acknowledgment succeeds; stale token gets 409 and cannot erase new unread change.
8. Disconnect network: cached list is readable; mutation/chat controls disabled; Refresh failure is visible. Reconnect and refresh to edit. HTTP 401/422/503 and slow network are actionable; no credential-bearing URL/error is displayed.
9. Save connection with same server/new token or different server: cache, cursor, chat/drafts and outstanding notifications are cleared. Decrypt works after restart; tokens never appear in cached tasks. Uninstall/reinstall loses local credentials as expected.
10. Chat question and email_draft against a real configured provider; task context matches selection. Server 503 never yields invented text. Voice flow shows privacy notice, captures text for review, never auto-sends; no recognizer installed yields graceful message.
11. Process recreation after backgrounding restores ordinary UI state from saved instance state; in-flight chat interrupted by actual process death is identified for explicit retry (not automatically replayed). Test after notification launch too, ensuring saved edits are not replaced by the original intent.
12. JobScheduler persistence/reboot and Doze/app standby/battery saver scheduling. Do not interpret a 15-minute requested period as a delivery SLA. Force-stop suppresses scheduling until user launch; user-facing wording must remain best effort.

Use SDK `adb logcat` to look for crashes without logging server secrets. No screenshot or runtime UI result is asserted without a device.

## Updater test-first and artifact evidence

- Version/day/URL/hash/metadata/stream/state tests were added before their implementation and executed RED, then GREEN. Later hardening tests demonstrably failed for accepting invalid published timestamps, mismatched release tags and bidi/control characters in notes; each passed after the corresponding production change.
- Tests reject same-version/downgrade policy, different package/signer, metadata/APK size disagreement, wrong hash, schema changes, numeric strings/floats, duplicate/missing assets, untrusted hosts/ports/user-info/non-HTTPS/traversal, redirect loops, oversized streams, cancellations and HTTP failures. The state tests exercise a manual check joining an automatic check, explicit consent, permission state, cancellation generation and installer single-flight gates.
- `tests/test_release_metadata.py` failed first because the generator did not exist, then passed. Generation was also executed against the actual built signed APK with the expected certificate identity, not a fabricated APK/tool result.
- Opt-in real GitHub smoke check fetched latest stable release **1.0.0 / code 1** and its actual metadata, matched the release signer, and returned `newerThanInstalled=false` for installed code 2. This confirms real transport/redirect/parser compatibility at verification time, not Android runtime behavior or future GitHub availability.
- Full build invokes all deterministic JVM/Python suites. SDK tools verify APK package/version/min/target SDK, v2/v3 signing, alignment and compiled manifest. Only `REQUEST_INSTALL_PACKAGES` was added; the new provider is non-exported/read-only and uses a temporary exact URI grant. No signing key/password or test JSON jar is an APK entry.
- **Not runtime verified:** Activity lifecycle, Android PackageManager archive verification, unknown-source permission flow, ContentProvider grant handling, OEM installer selection/confirmation, cancellation and actual self-update on a device. JVM state tests cannot prove these framework integrations. Do not treat a successful build or network smoke check as device acceptance.

## Updater device acceptance matrix — NOT executed here

Use test-signed fixtures / a separate test build/repository only; do not publish fake production releases or mutate live tasks.

1. Manually upgrade an installed 1.0.0 to 1.1.0 with matching signing identity. Confirm settings/cache remain and 1.1.0 reports up-to-date against code 1/2 releases. Confirm a different signer cannot upgrade.
2. Fresh install with **no task account**: first foreground checks GitHub. Rotate/resume repeatedly and make simultaneous manual checks; only one request runs. Repeat after process restart on the same local date: no extra automatic attempt. Advance local date/time zone and foreground again: one new attempt. Leave app continuously foreground across midnight: no check until next foreground/resume.
3. Offline or GitHub 404/403/429/timeout/bad TLS/invalid JSON: automatic failure is quiet and not retried that local day; manual action always remains available and displays the error. Manual no-newer shows status. Long task/chat request must not block the independent updater executor.
4. Newer fixture: version and bounded plain-text notes appear with **Update / Later** and unsaved-edits warning. Later/back dismiss does not download or launch any permission/installer. Background/rotation preserves operation/offer without a stale Activity callback.
5. Accept: download shows cancellable progress. Test short/oversized/mutated APK, mismatched SHA/size/package/version/name, same-version/downgrade and different current signer: no installer opens and private temporary files are deleted. Cancel while downloading, rotate or background during slow network; obsolete callbacks must not present a new installer.
6. Unknown-source permission initially off on API 26 and a current Android device: explanation precedes user-opt-in Settings navigation. Deny/back → no install, cleanup. Grant → return, reverify accepted file, open system confirmation. Revoking permission between verification/dispatch must fail safely. Work-profile/MDM policy blocks must display errors, not bypass security.
7. Cancel system confirmation: revoke temporary grant/delete file. Confirm installation: app updates retaining account data. No root/silent install/Play Protect change is requested. Explicit system installer selection must resolve on each tested OEM; absence must fail with a visible message.
8. Process death while downloading, awaiting permission or showing installer: no stored consent is replayed. Next app initialization deletes stale private downloads. Returning from Settings without an accepted in-memory file must never trigger installation.
9. Attempt external provider reads without grant, guessed/old URI, changed path/query/authority, `_data` projection, and write modes (`w`, `rw`, `rwt`), insert/update/delete: no arbitrary filesystem path or write access. Only the exact currently granted URI provides the public verified APK while the install flow is active. Check URI grant expiry/revocation after cancellation/result.
10. Re-run existing task/chat acceptance matrix while update checks/downloads run, especially unsaved edits and chat drafts. Save before confirming installation; process replacement may discard unsaved memory.

