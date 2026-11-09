# My Tasks — native Android 1.5.0-glass-preview (7)

Native Java, package `com.simon.mytasks`, minimum SDK 26, compile/target SDK 35. This is an Android APK, not a web wrapper or native iOS component. No app-library/Gradle dependencies; JVM tests use a checksum-pinned JSON jar that is not packaged in the APK.

## Build

```sh
export JAVA_HOME=/path/to/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/path/to/android-sdk
# Existing, original private release identity, outside the checkout:
export SIGNING_KEYSTORE=/private/path/mytasks.p12
export SIGNING_PASSWORD_FILE=/private/path/password
bash build.sh
python3 release_metadata.py build/my-tasks.apk --expected-certificate a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da
```

Requires Python 3, platform android-35 and build-tools 35.0.0 (`aapt2`, `d8`, `zipalign`, `apksigner`). `ANDROID_BUILD_TOOLS` overrides the tools directory. The build runs deterministic tests, compiles/dexes/packages, signs with the supplied existing key, then verifies signatures, alignment and package metadata. It refuses to generate or replace signing identity. Never publish `.signing` or any password/key. `bash test.sh` needs JDK17 but not the Android SDK/signing material; `TEST_BUILD_DIR` isolates test class outputs (default `build/tests-native`). Updater transport/consent/provider security and manual checks without an account remain independent of task/chat networking. See `UPDATER.md`.

## Workspace

- **Tasks / Discussions** are the two primary destinations. Normally, current width >=720dp uses a rail and split list/detail when font-scaled pane widths remain useful; compact windows focus one pane. Global navigation is hidden on compact details/edit/full chat. Task rows have a 64dp minimum, dominant ordinary two-line title, semantic high-priority/unread markers, and due/project text only when useful. Full priority/status/project/due/unread context remains available to accessibility services. Large fonts can grow rows.
- View, project and sorting choices remain in compact menus; search is expandable. The compact task list retains one scroll containing toolbar, controls and rows. Chat has an independent message scroll above its composer, never a vertically scrolling parent around both. IME/short windows hide secondary chrome, with task/discussion/settings actions still available in menus. Native compact/unfolded, IME and state measurements are recorded in `REDESIGN.md`; physical Samsung behavior remains separate.
- Content stays opaque and readable. Cool light/dark tokens use bundled OFL-licensed Inter with system fallback, with contrast-tested light-theme primary blue `#0068E5`. Native glass containers sample and blur a bounded backdrop while keeping their foreground labels sharp; reading content stays solid. Opaque reduced-transparency/high-contrast fallbacks retain the same geometry. These are not Apple assets or private optical refraction. Short tab/task/sheet/message transitions and pressed scales respect system motion settings and explicit Reduce motion; Reduce transparency provides opaque controls. Font attribution/license is in Settings.
- Task create/edit/complete/recoverable Trash, revision-conflict draft preservation, exact displayed-update acknowledgement, safe supplied source links, encrypted credentials and best-effort notification behavior remain. Opening a task never acknowledges an update. Offline task caches are read-only; no hidden mutation queue exists.

## Durable conversations

- In task details choose **Discuss with Hermes**, then choose saved history or explicitly create a new conversation. Saved/new task actions move into overflow once a thread is open. Multiple saved threads can share one task. Saved server context is used, never unsaved task fields.
- **Discussions** lists all app threads, with All/Free/Task/Archived views, latest preview, task association, pending/failure indicators, search of loaded results and explicit **Load more**. Create a free conversation, reopen an existing thread, or use its menu to rename/archive/reopen. Archive is recoverable, not deletion. Renames/archives use optimistic server revisions; a proposed rename is retained locally on conflict.
- New clients exclusively use `/api/conversations` and its documented history/message/request routes. Server history is authoritative. Latest pages contain up to 40 messages; **Load older messages** prepends deduplicated pages without truncating original history. A context-compacted hint reflects the server flag; full stored messages remain viewable.
- A UUID, immutable message/mode/target and expected revision are durably saved **before POST**. Acceptance is asynchronous (202); only the visible pending thread has a bounded status timer. Accepted work continues when leaving a tab/task. Reopening/restarting reads server `latest_request` and, when necessary, resolves the exact unconfirmed saved identity. No automatic POST replay or fresh request is made.
- Uncertain delivery offers status checking and explicit exact-identity replay. Definitively rejected admissions are distinct from provider failure. Failed/interrupted requests offer **review** of a new request; sending still requires an explicit tap. Server restart interruption never automatically runs a model again. Newly typed follow-ups are never cleared by acceptance/completion.
- Drafts/modes/selection/history/pending bindings are thread-keyed and app-private. Account epochs guard queued work and callbacks. Disconnecting or changing connection clears local credentials/caches/search/drafts/pending bindings; server history remains private and durable. Same-account ordinary Activity recreation retains durable drafts. Backup is disabled. Local content caches use Android sandbox/device encryption, not separate application-level content encryption.
- One assistant: **Hermès**, the backend-configured assistant without tools. Free questions, analysis and email drafting are supported; chat cannot send mail, run tools or modify tasks. Existing terminal/dashboard Hermes sessions are not imported. Previously discarded stateless chats cannot be reconstructed.
- Responses are selectable safe Markdown with inert link labels, not raw HTML or active links/images. Platform voice recognition starts only after a privacy notice: the selected recognizer may send audio to its provider. My Tasks itself records no audio and requests no microphone permission. Transcript insertion is guarded by account and conversation identity and is always editable, never auto-sent.

## Connection and notifications

The mandatory account gate accepts a custom trusted HTTPS origin, username (public default `oiry-s`) and password, never a pasted token. Existing encrypted credentials automatically supply bootstrap ownership proof. Restricted first owner access forces current/new/confirm password replacement before private cache/API/model access; new installations without proof require private web activation. Only issued 64-hex sessions are stored using the unchanged AES-GCM Android Keystore alias/AAD; passwords are never persisted. Restricted/expired/revoked sessions gate the UI and every API request. Ambiguous password changes are not retried; sign-out attempts revocation with session readback and clears local bindings without a key reset. Redirects/cleartext/untrusted TLS remain rejected. Preserved legacy token-policy/security tests still cover migration. See `AUTH-NATIVE-v1.3.md` for exact routes, lifecycle caveats and verification.

Background task notifications are best-effort persisted JobScheduler checks, not instant push. Requested periods are at least 15 minutes; Doze, OEM restrictions, force-stop, quotas and offline state can delay them. First connection establishes a quiet event baseline. Permission/channel denial keeps relevant events pending. Foreground Refresh also checks updates. Canonical Context/New/optional Suggested next steps prose uses the same safe Markdown in OS BigTextStyle and an in-app summary modal, without source actions. Android controls OS notification appearance. Opening/dismissing an update does not acknowledge it; explicit acknowledgement binds its exact token and failures require fresh summary review.

## Sources and verification

`ConversationClient.java` implements the exact API; `ConversationState.java` owns pure paging/draft/admission policies; `ConversationController.java` owns private persistence, asynchronous account/target guards and bounded visible polling; `ConversationsUi.java` owns native discussion UI. Task policies remain in `Core.java` / `PresentationPolicy.java`, compact display policy in `CompactPolicy.java`, and shared material/tokens in `WorkspaceUi.java`.

Run the optional native-client HTTP contract check against an isolated **synthetic** loopback server:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 tests/run_conversation_api_integration.py
```

This requires the maintained companion `../server` Python dependencies; it creates only temporary scratch stores and never calls a real provider. It exercises the actual JVM conversation client against real ASGI endpoints, including revision conflicts, exact replay, two task threads, archive recovery and 42-message paging. It is not an Android runtime/network test.

See `TESTING.md`, `UI-ACCEPTANCE.md` and generated `build/update.json` / `SHA256SUMS`. SDK compilation/signature/package verification and deterministic/loopback tests do **not** prove Android launch, installation, IME/focus, actual pixels, Keystore, notifications, voice, HTTPS or updater installer behavior. The configured Linux Pixel_Fold provides actual Android 17 native evidence; see `REDESIGN.md` and the isolated QA workflow. Emulator results do not certify physical Samsung/One UI, speech, notification delivery or installer consent. No production task writes are used for verification.
