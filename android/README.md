# My Tasks — native Android

Dependency-free Java Android app, package `com.simon.mytasks`, min SDK 26 (Android 8), compile/target SDK 35 (Android 15). The APK is a native Android installable, not a web wrapper. No Gradle, Maven, framework or network dependency resolution is needed to build once the SDK/JDK are installed.

## Build and install

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
bash build.sh
# On a connected Android device with USB debugging enabled:
"$ANDROID_HOME/platform-tools/adb" install -r build/my-tasks.apk
```

Prerequisites: JDK 17, Python 3, Android platform `android-35`, build-tools `35.0.0` (`aapt2`, `d8`, `zipalign`, `apksigner`). Defaults match the installed toolchain above; `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_BUILD_TOOLS` override them. The build runs the pure Java test suite before compiling, dexing, aligning, signing and verifying the APK. Run only tests with `bash test.sh`.

**Signing:** `.signing/mytasks.p12` and `.signing/password` are generated once with private permissions. Keep both private and backed up separately; updates must use the same key. Never publish `.signing`, put it in source archives, or commit it. `SIGNING_KEYSTORE`, `SIGNING_PASSWORD_FILE` and optional `SIGNING_ALIAS` select an existing PKCS12 identity. A new checkout without the existing identity generates a different certificate and cannot upgrade an existing installation. This is a self-signed local release, not a Play/App Signing distribution identity.

## Connect

1. Open **Settings**, enter an HTTPS origin such as `https://tasks.example` and the companion server bearer token.
2. Use a publicly trusted or device-trusted TLS certificate. HTTP, URL credentials, paths, fragments, queries and automatic redirects are rejected. There is deliberately no cleartext developer bypass.
3. Save and reconnect. Changing connection settings clears cached tasks, notification cursors and current chat/drafts, even when changing token on the same server. This avoids cross-account data leakage.
4. Enable notifications explicitly in Settings. Android 13+ asks for notification runtime permission. Denial does not prevent using tasks; you can enable notifications later in system settings.

The token is AES-GCM encrypted using a non-exportable Android Keystore key with the server origin as authenticated associated data. The app does not log tokens, put them in intents, or include them in cached JSON. Backup is disabled. TLS trust is never bypassed. Cached task data is app-private but not separately encrypted; Android's device encryption and sandbox protect it at rest. Clear app data to remove all local state and credentials.

## Use

- Search and **Active / Today / Week / All / Trash** views. Today includes overdue tasks; Week includes overdue tasks through the server's `week_end`. Dates and week boundary come from the server rather than the phone clock.
- Tasks are grouped by project/category, then high / medium / low priority, then deadline (undated last). Unread updates get a badge and an explicit exact-token **Mark this update read** action after the summary is displayed.
- At widths **720dp and above**, list and detail panes appear side-by-side. Narrow layouts use task navigation. Rotation, resizing and light/dark changes rebuild the native layout while keeping selection, edits, search, filter, scroll, chat text/history and in-flight requests. Saved instance state supports ordinary process recreation; it does not store credentials.
- Edit title, description, private notes, status, priority, date and project; create, complete, soft-delete after confirmation, or restore from Trash. A revision is sent on every existing-task mutation. On HTTP 409, the unsaved draft stays visible; copy needed fields and choose **Reload latest** before editing again. There is no forced overwrite.
- Offline cache is **read-only**. A successful refresh is required before editing or chat. There is no hidden mutation queue. GETs have one bounded retry; writes and chat are never automatically replayed because their result can be ambiguous after a network failure. Refresh before deciding to retry a write.
- **Ask Hermes** provides task-linked text questions and email drafts through `/api/chat`, with up to 12 prior turns and an exact 60,000-byte UTF-8 serialized body budget. Oldest whole turns are evicted as needed, including JSON framing/escaping, task ID, mode and the full current message. Prior turns outside the server schema (such as an oversized provider reply) are omitted whole. Your current message is independently checked at 1–8,000 Unicode codepoints and is never silently shortened. HTTP 413 explains that input must change and identical resubmission is blocked within the activity. Chat read timeout is 200 seconds; a timeout or interrupted activity does not prove server/provider cancellation. Copy warns that the original provider request may still be running; chat is never automatically replayed, and manual re-send may duplicate provider work. Nothing in the app sends email. Provider errors are displayed, not replaced with fabricated replies.
- **Voice input** launches the installed platform speech recognizer only after a privacy notice. That recognizer may transmit audio to its own provider. My Tasks requests no microphone permission and records no audio itself. The returned transcript is editable and is **never sent automatically**. Missing recognizers are handled with a type-instead message.

## Notification contract and limitations

`JobScheduler` schedules network-constrained, persisted periodic work at the greater of 15 minutes and the system minimum. This is **best effort, not instant push**: Doze, battery saver, app standby, force-stop, OEM restrictions, network loss and job quotas can delay or suppress background checks. Foreground **Refresh** also checks the durable event feed. No foreground service, FCM or permanently running socket is used.

The first event poll establishes a quiet baseline (including paginated history), so existing events do not flood the phone. Later event cursors persist only after successful handling: a notification was posted, or its current task is deleted/missing/already read under initialized canonical tracking. The feed explicitly reports `notification_tracking`; only literal false enables created/updated/restored fallback alerts using the current title even when unread is false. True never alerts an already-read task, and missing/unknown is not false. No poll implicitly initializes canonical notification metadata or acknowledges an update. If notification permission or the channel is disabled, an unread event stays pending. Cursors are monotonic. Per-task notification IDs replace duplicates after crashes. A crash between Android posting and cursor persistence may re-post the same per-task notification; this is intentionally at-least-once rather than silently losing updates. Notification content is private on the lock screen with a generic public version; tapping opens the relevant task in the matching account. Reading a summary in the app does not silently acknowledge it.

## Code

- `MainActivity.java`: native responsive home/detail/editor/chat/settings/voice UI.
- `Store.java`: encrypted credentials, account epochs, atomic app-private cache, durable cursors.
- `Api.java`: serialized background execution, timeout-bound HTTPS JSON, no redirects, safe errors and limited GET retries.
- `PollService.java`: persisted periodic job, initial quiet baseline, notifications and cursor handling.
- `Core.java`: JVM-testable origin validation, task URL encoding, ordering, explicit notification-tracking decisions, safe chat error copy, and exact UTF-8 chat serialization/history budgeting.
- `tests/CoreTest.java`: test-first regression checks; no JUnit dependency.

The companion contract is `../server/CONTRACT.md`. A single background executor serializes application network requests and account changes. UI callbacks check account epochs, so old-account responses cannot repaint a new account or overwrite its cache.

## Verification boundary

The project was compiled and signed with the installed JDK/SDK, and the resulting APK passed `apksigner verify`, `zipalign -c` and `aapt2 dump badging`. JVM tests were executed. There is **no connected device, emulator or KVM here**: installation, launch, layout appearance, runtime Keystore, rotation, actual notification delivery, recognizer availability and Android HTTPS integration still require device QA. See `TESTING.md`. No live task mutations were performed for Android verification.

## Official Android references consulted

- SDK 26 / Android 8 migration: `https://developer.android.com/about/versions/oreo/android-8.0-migration`
- SDK 35 / Android 15 edge-to-edge requirements: `https://developer.android.com/develop/ui/views/layout/edge-to-edge` (the activity applies system bar, cutout and IME insets).
- Periodic scheduling semantics and system minimum: `https://developer.android.com/reference/android/app/job/JobInfo.Builder` and `https://developer.android.com/reference/android/app/job/JobInfo#getMinPeriodMillis()`.
- Android 13+ notification permission: `https://developer.android.com/develop/ui/views/notifications/notification-permission`.
- AES-GCM Keystore key generation: `https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec`.
- Platform recognizer result/privacy/unavailable-app contract: `https://developer.android.com/reference/android/speech/RecognizerIntent`.

### Direct SDK build pitfall

Compile app sources using JDK `javac --release 8 -classpath android.jar`, then D8 with `--lib android.jar --min-api 26`. Using only `android.jar` as javac's `-bootclasspath` fails for lambdas because Android's `LambdaMetafactory` stub omits the compiler's bootstrap method. For PKCS12 signing with a password file, pass only `--ks-pass file:...` to apksigner; supplying the same file separately as `--key-pass` makes apksigner try reading a second line.
