# Liquid Glass v3 implementation and evidence ledger

## Authorized brief
`IMPLEMENTATION-PROMPT.txt` and installed native-android-liquid-glass 3.0.0. All nine preview boards inspected using vision; all five skill references and the entire implementation brief read. Preserve package/signer, private session, canonical APIs/store and durable conversations.

## Baseline
Branch started at fcd4218; user-provided untracked IMPLEMENTATION-PROMPT.txt and START-HERE.txt retained. Installed com.simon.mytasks 1.4.0-preview code 6/API target 35 on Pixel_Fold Android 17/API 37. APK SHA-256 3214947b8cb3c267c33ee85b11432868cd8ea8747e6f3a0e4309f73ff009209a. Private read-only baseline screenshot stored in evidence/baseline-unfolded.png (contains real task titles; do not publish). Existing native/host regression suite passed before changes.

## Architecture decision
First tracer bullet retains direct SDK Java Views and replaces the presentation hierarchy, rather than investing in a Compose build migration before observing a real render. A custom sampled/blurred bounded backdrop container, circular controls, reusable tokens and deliberate rail/master/detail composition will be evaluated on actual Pixel_Fold renders. This decision is conditional on visual quality, not a claim that small diffs meet the brief. Native Android owns bars, IME and permission flows.

## Sequence and gates
1. Shared tokens/bounded backdrop renderer; compact Tasks and measured unfolded rail/master/detail.
2. Actual signed build, strict-host SCP, matching source/host hashes, MCP update install. Use distinct QA package and real isolated synthetic backend for all mutation/chat checks. Do not change production account.
3. Native Tasks captures in compact/unfolded sizes; compare concrete typography, row count, header budget, alignment, circular controls and sampled glass. Correct observed defects before extending components.
4. Details/edit/update review/actions, Discussions/task/free chat and utilities; maintain existing state, revision and exact-token semantics. Repeat native captures and fix visible mismatches.
5. Regression suite, isolated API checks, keyboard-open multiline thread, drafts across fold, light/dark/200% text/reduced transparency/motion/offline. Record scrolling, transitions and folding. Independent code review and fixes.
6. Final signed source/APK hash/signature, matching installed APK, evidence manifest and honest tested/untested matrix. No publication/stable-release promotion or backend deployment unless separately authorized.

## Commands/environment
- `JAVA_HOME=/opt/data/cache/android-build/jdk-17.0.2 ANDROID_HOME=/opt/data/cache/android-build/sdk bash android/build.sh`
- Existing private signer must be supplied via build env, never copied into source or output.
- SSH strict-known-hosts key setup is documented in the installed skill. Linux adb is `/home/simon/Android/Sdk/platform-tools/adb` (not on noninteractive PATH).
- Native policy/behavior checks: `JAVA_HOME=/opt/data/cache/android-build/jdk-17.0.2 bash android/test.sh`.

## Test-driven tracer bullets
- New compact/full-thread global-chrome and expanded master-width assertions fail before implementation, then integrate production policy into Activity hierarchy. Concurrent renderer RED tests are isolated and may temporarily make the whole tree red while the independent component is being implemented.

## Coordinated task presentation continuation

Current requirements are in section 10 of `IMPLEMENTATION-PROMPT.txt`, superseding the older palette/order/two-breath motion rules. Preserve the existing native and in-progress web redesign. Shared task colors and deterministic ranking must be implemented in each client without mutating canonical task data or changing authentication/admission contracts.

Live baseline verification: Android MCP reports local `Pixel_Fold`, Android 17, online. SSH/ADB reports folded CLOSED state 0, physical viewport 1080x2092, density 420. Production installed package `com.simon.mytasks` SHA-256 `420d04cbe8bec7f5be63f105b670eea6f07571ea1d66bc3fd57d549bdd8c76f2`; existing QA package SHA-256 `8f476d0565bd4d2711a3a65fc67a8428643e89c1611898c800fef73a11a4d193`. Production baseline was inspected read-only; real task captures remain private and are not deliverable evidence. Synthetic baseline is `evidence/colors-before-qa.png` with corresponding native JSON/result.

The retained loopback fixture was stopped; its old PID was absent. Restarted the same isolated certificate/account/database using the documented command, owned PID 1414902 on Linux, without replacing sessions or certificate. Certificate expiry Nov 23 2026. Cert-verified normal API probe passed: health ok, anonymous tasks 401, native synthetic login ok, 11 synthetic tasks, 3 conversations, logout. ADB reverse port 9147 restored. Actual QA-package snapshot instrumentation passed without login/reset, preserving both installed sessions. Canonical fixture seed/isolation regression passed.

Remaining acceptance is tracked below and must be updated with actual build/hash/native evidence; this setup verification is not completion.

## Coordinated continuation verified artifact

Final installed synthetic QA APK is `android/build/my-tasks-synthetic-qa.apk`, SHA-256 `fae6b6746503a046a8ce030bca3558049e79e1521d0900393cee0d9ebe368e99`; source, SCP destination and installed package hashes matched. Production installed APK stayed unchanged. Final native suite passed (58 Python tests plus JVM/controller/API gates), independent fixed-clock golden checks passed both policy clients, and web suites passed 101 JS / 72 Python tests. Native all-color/all-sort, preference restart, waiting/inactive/Trash, exact-token race and chat/IME/fold scenarios passed. Final native screenshots/three recordings and full limitations are in `qa/coordinated-evidence-20261110/REPORT.md` and its artifact manifest.

This is a verified partial-delivery checkpoint, not full acceptance: the original production signing identity is missing; a live web local-timezone correction is blocked after withdrawn tool approval and no renewed authorization. The current web UI still uses Europe/Rome despite zone-aware policy tests passing. Remaining web capsule/direction-label polish, live-model invocation and full interrupted-effect resumption are explicitly unverified/incomplete in the report. No production mutation, release, web deployment or commit was performed.

## Remaining acceptance
See the precise blockers and limits in the coordinated report. Screenshots prove rendering only; recordings were decoded and inspected for local effects/navigation/IME/folding, not physical-device frame performance. Fixture replies are synthetic, never represented as actual model output. Physical Samsung/One UI checks require a physical device.
