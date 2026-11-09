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

## Remaining acceptance
This ledger records work in progress, not completion. Screenshots prove rendering only; recordings must be reviewed for motion. Host tests are not runtime/Keystore/provider proof. Fixture replies are synthetic, never represented as actual model output. Physical Samsung/One UI checks require a physical device.
