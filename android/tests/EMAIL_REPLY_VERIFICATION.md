# Native v1.6.2 reply workspace verification

## Maintained runner hooks (parent integrates test.sh)

```sh
JAVA_HOME=/path/to/jdk python3 android/tests/run_email_reply_tests.py
JAVA_HOME=/path/to/jdk python3 android/tests/run_email_reply_api_integration.py
```

The first runner compiles the actual Java state/client/controller/layout/citation/diff and reflective OEM observer, then executes actual extracted UI capture and review-recipient formatting methods. Android and OEM class shells live only under `tests/email-stubs`, never APK sources. Both Java Consumer and vendor Consumer signatures exercise the same production reflective observer. The second runner requires companion server dependencies, starts actual email routes/store on loopback with a private scratch database and the synthetic FixtureProvider, and executes the real Java client over HTTP. It asserts exactly one synthetic provider send and one tool-free synthetic AI call. No external mail transport, production credentials, install, deployment or commit is involved.

## Observed RED -> GREEN tracer bullets

- Missing EmailReplyState / ComposerLayoutPolicy symbols -> implemented separate local/server state and readable measured split -> state suite green.
- Missing EmailReplyClient -> exact percent-encoded source/detail/draft routes and explicit null token/false reply_all -> client suite green.
- Missing EmailReplyController -> async CAS save and send-without-preview rejection -> controller suite green.
- `switching sources cannot lose offline writing` failed -> per-draft private recovery snapshots -> state suite green.
- `terminal AI job must refresh persistent discussion` failed -> terminal status fetches authoritative draft discussion without replacing local writing -> race suite green.
- `new ambiguous AI identity must be checked even after old terminal job` failed -> clear previous terminal job when preparing the persisted new identity -> race suite green.
- `additive citations must invalidate visible rows` failed -> include canonical metadata in message/row signatures -> citation suite green.
- `old reply erased canonical citation metadata` failed -> preserve additive metadata when an older response lacks it -> citation suite green.
- `old list response cannot overwrite new generated metadata` failed -> merge list rows monotonically against known metadata -> actual ConversationControllerTest green.
- `definitive stale review rejection must allow new review, not trap unknown outcome` failed -> distinguish a server 409 rejection from transport ambiguity, reload authoritative draft and require a fresh human review -> race suite green.
- `truncated provider marker escaped into rendered copy` failed -> unresolved-marker projection also handles unterminated markers, preserving original content -> citation suite green.
- Missing diff policy -> word-token LCS preview with explicit removed/added text and bounded fallback -> diff suite green.
- Missing usablePane / splitAroundHinge -> measured occlusion exclusion and readable vertical-hinge split policy -> hinge suite green.

## Native integration and limitations

- Composer is rendered before the Tasks/Discussions shell. UpdateSheet passes the displayed task/token directly to Reply; no acknowledgement call is added. Details uses the token-optional source route.
- Email recovery uses existing AndroidKeyStore AES-GCM conventions, dedicated private encrypted preferences and server + account epoch + purpose AAD. Body/instruction/selection/focus/scroll are not placed into task fields or instance-state Bundle. Recovery includes per-draft local writing across source switches.
- A real nonzero reported vertical occlusion can separate readable editor/assistant panes; header/status stay within the editor pane and a gap reserves the exact hinge. Horizontal/unsupported multi-occlusion layouts choose an unobscured usable pane. Zero-area folds are not invented as hinges. Without OEM extensions, measured viewport/system insets remain authoritative.
- Parent should add optional `<uses-library android:name="androidx.window.extensions" android:required="false" />` under application for OEM extension class visibility. No Gradle/runtime library dependency is introduced. Listener removal and stale-callback fencing are host-tested; actual OEM API availability must be measured on device.
- Citation kind `saved_email` (backend canonical value) and `email` both fetch authenticated `/api/tasks/{task}/email-sources/{resource}` detail. No raw provider reference is made into a URL. Original Copy remains unchanged.
- Title fields consumed: title_revision/title_origin plus normal conversation revision; manual ownership remains dominant. Visible metadata refresh is bounded and foreground/listener-owned, not a second conversation presenter.

## Resume verification — November 10, 2026

- Toolchain verified at `/opt/data/cache/android-build/jdk-17.0.2` and `/opt/data/cache/android-build/sdk/platforms/android-35/android.jar`. Neither `javac` nor `JAVA_HOME` was initially on this worker's PATH/environment; both runners work with explicit `JAVA_HOME`. The API runner now reports the missing prerequisite clearly rather than raising `Path(None)`.
- The previously reported API hang is **not reproduced** against the currently integrated backend: initial run passed, then three independent private-fixture runs passed in 4.18, 3.93 and 4.04 seconds. No evidence establishes the historical hang's root cause; no speculative backend/transport workaround was applied. Added compiler/JVM deadlines to both runners and phase diagnostics to the API runner so a recurrence identifies its boundary.
- A separate native bug was reproduced RED: after an ambiguous create response, choosing another source reused the old request ID with a different payload (backend idempotency conflict). GREEN fix scopes persisted create identity to exact task/source/update token, retains same-payload replay identity, and ignores source choices while busy/invalid.
- `run_email_reply_tests.py`: **10 JVM executions passed** (8 main test classes, actual UI-method harness, and OEM vendor-Consumer variant). Includes the new ambiguous-create/source-switch regression.
- `run_email_reply_api_integration.py`: **12 Java contract assertions** and **3 post-run Python invariants** passed per run: one synthetic send, one tool-free AI call, unchanged canonical update token. An expected HTTP 409 exercises stale CAS rejection; it is not a test failure. Three repeat runs used an additional outer 40-second per-run deadline.
- `android/test.sh`: **21 JVM programs / 860 reported checks**, **69 Python unittest tests**, and **5 redesign regression scenarios** passed. Additional Python-invoked host harness checks also passed (including 1,357 actual TasksUi lane/group checks).
- Full SDK35 `javac --release 8` passed after the fix: **57 Java source files -> 119 class files**, including generated R.java. Only deprecation notes, no compilation errors.
- No backend, manifest, build/test.sh script, production state, real email, commit, install or deployment changes were made by the resumed worker. Parent must add the two runner hooks shown above to its test pipeline; physical native acceptance remains unverified.

All Java sources have been compiled against SDK35 using direct javac. Existing test.sh and the new runners were executed. These host results do not establish native rendering, AndroidKeyStore process-recreation behavior, IME/fold/rotation, 200% text or physical OEM runtime quality. The parent owns APK packaging, emulator install and measured native acceptance.
