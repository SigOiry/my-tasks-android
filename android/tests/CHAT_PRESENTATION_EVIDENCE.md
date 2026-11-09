# Chat presentation verification

## Scope

Only `ConversationsUi.java`, the pure `ChatPresentationPolicy.java`, and separate chat tests/this evidence changed in this workstream. `ConversationController`, `ConversationState`, pending payloads, request identities, routing, backend, manifest, app identity and `test.sh` were not changed.

Presentation state is stored under `presentation:<thread>` in the existing controller `state.json`: message `anchor_id`, `anchor_offset`, fallback `y`, `at_bottom`, `seen_latest`, and the originating own-send `follow_request`. The legacy `scroll:<thread>` fallback remains supported. Existing drafts and task-thread selections remain unchanged.

## Actual test-first runs

The following RED runs happened before the corresponding implementation; every policy check executes Java behavior, not source text or a mock:

1. Missing viewport policy raised `AssertionError: Missing viewport policy: polling must not pull readers to the bottom`. Implemented follow decisions; GREEN: **4 assertions**.
2. Grouping lookup raised `AssertionError: Missing grouping policy: consecutive roles need real timestamp boundaries`. Implemented same-role, five-minute, local-calendar-day grouping using actual ISO timestamps; GREEN: **11 assertions**.
3. Geometry lookup raised `AssertionError: Missing stable viewport geometry`. Implemented near-bottom geometry and message-anchor offset restoration; GREEN: **18 assertions**.
4. Reconciliation lookup raised `AssertionError: Missing message-only reconciliation and actual arrival counting`. Implemented canonical message-content signature, deduplicated observed arrival count and exact own-send request matching; GREEN: **26 assertions**.
5. Fractional boundary regression raised `AssertionError: Fractional seconds beyond the grouping window split` against the first grouping implementation. Changed duration comparison to retain subsecond precision; GREEN: **27 assertions**.
6. Saved viewport lookup raised `AssertionError: Missing saved-thread viewport restoration policy`. Implemented first-visit vs return/rotation decisions; GREEN: **31 assertions**.

The initial plain `javac` command could not execute because JDK binaries were not on PATH. All actual RED/GREEN Java runs then used `/opt/data/cache/android-build/jdk-17.0.2/bin/{javac,java}`.

The separate source-integration guards first failed **4/4** against the original UI, then passed after wiring policy/anchors/grouping/copy/search/capabilities. An added resize guard failed before the resize hook and passed afterward. These are explicitly source checks, **not** native UI execution.

## Reproduce

From repository root, after the existing runner has obtained its checksum-verified JSON jar:

```sh
JAVA_HOME=/path/to/jdk17 python3 android/tests/run_chat_presentation.py
JAVA_HOME=/path/to/jdk17 TEST_BUILD_DIR=build/chat-regression bash android/test.sh
```

The separate runner uses `build/chat-presentation` (override `CHAT_TEST_BUILD_DIR`) and does not sign or install anything. The parent may add `ChatPresentationPolicy` / `ChatPresentationPolicyTest` to its `test.sh` JVM compile/run lists instead. Python discovery already includes `test_chat_presentation_wiring.py`.

Final observed output:

- `ChatPresentationPolicyTest: 31 assertions passed`
- Separate source-integration suite: **5 tests, OK**
- Existing `test.sh`: all printed JVM suites passed, including `ConversationStateTest: 20`, `ConversationClientTest: 8`, `ConversationControllerTest: 28`; Python discovery: **28 tests, OK**
- `git diff --check`: exit **0**

## Compile and outstanding device checks

SDK 35 `javac --release 8` compilation succeeded using existing generated resources and an isolated `build/chat-compile` output, excluding only the sibling's not-yet-integrated `TasksUi.java` / `TaskRowView.java`. The first full-source compile encountered the sibling's calls to `MainActivity.showTaskActions` and `MainActivity.quickChange` before the parent had added them. A later full-source repeat, while MainActivity was actively being integrated, was also blocked by unfinished parent references (`UpdateSheet`, `quietRefresh`, `invalidateQuietRead`, `saveWorkspacePreferences`, `ic_chat`, dialog button calls, and those task action methods). No chat compiler errors were reported. The parent must run a complete compile once its concurrent MainActivity integration is coherent.

No native device/emulator or KVM was available. Android rendering, 360/412dp, 200% font scale, TalkBack, keyboard-open/landscape, text-selection menu behavior and physical scroll/rotation still need native verification. Host tests and SDK compilation are not rendering or performance proof. No APK was signed, deployed, published, committed or pushed by this workstream.
