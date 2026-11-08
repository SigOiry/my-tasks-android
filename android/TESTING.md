# Verification

## Executed here

- Pure JVM test-first URL-origin cycle: initial implementation threw `IllegalArgumentException: Not implemented`; after implementation, `PASS 8 checks (url)`.
- Ordering cycle: initial constant comparator failed `AssertionError: project then priority then due; missing last`; implemented comparator passed.
- Cursor/event cycle: initial no-op cursor failed `AssertionError: advance on handled event`; implemented monotonic handling and bootstrap/permission decisions passed.
- Task path encoding cycle: initial identity function failed `AssertionError: task ID never changes URL path or query`; encoded segment implementation passed.
- Final suite: `bash test.sh` → **PASS 17 checks (all)**. Tests run against real pure Java code, with no mocked framework or dependencies.
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
