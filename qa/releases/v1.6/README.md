# My Tasks v1.6 release verification

## Artifact

- Android package: `com.simon.mytasks`.
- Human version: **1.6**; Android versionCode: **10** (newer than both published v1.5/code8 and the locally verified 1.5.1/code9 checkpoint).
- APK SHA-256: `4cdfe3fcf0131bb65dde5aa93b8e59641aa6bba1ff9521a3d28543870560f81b`.
- APK size: **796399 bytes**.
- Original signer certificate SHA-256: `a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da`.
- Verified APK v2/v3 signatures, alignment, package/version metadata and the expected signer. Metadata was derived from this exact signed artifact, not an earlier build.
- Standard updater assets: `my-tasks.apk`, `update.json`, `SHA256SUMS`. APK/build outputs are release assets, not tracked source. Signing material and runtime/private data are excluded.

The Android presentation implementation matches the immediately preceding owner continuation; its release binary changes version/code. The shared backend additionally contains the reviewed privacy/context-budget fixes described below. All corresponding source is packaged under `v1.6`. No web presentation redesign is included.

## Executed local gates

- Full signed `android/build.sh`: existing JVM/auth/controller/API gates, **68 Python tests**, actual Java/resource/DEX build, signing/alignment — PASS.
- Final source policy rerun: **69 Python tests** — PASS, including an added unset-TMPDIR regression executing the real lane JVM harness. Initial GitHub CI found a NAS-specific scratch fallback in that test; standard tempfile discovery fixes portability without changing APK/runtime code or release asset bytes.
- Backend: **97 tests** — PASS, including failed-private-turn/current-public-request structural isolation and near-limit final-context budget regressions.
- Browser: **101 JS tests**, syntax check and reproducible existing web bundle — PASS. Web source/built presentation assets remain unchanged.
- Staged credential/forbidden-path scan: no actual credential material, signing keys, private databases, canonical tasks or APK files. Notification tokens in test harnesses are explicitly synthetic, not credentials.
- Exact v1.6 APK transferred with strict SSH host verification, hash-compared and installed over `com.simon.mytasks` on Pixel_Fold without uninstall/reset. The installed `pm path` APK hash matched the release artifact exactly; a read-only launch confirmed the existing full account session/task workspace remained available. The private account screenshot is not included in public evidence.

Independent source review and the GitHub main/tag/CI/asset checks are release publication gates, not inferred from these local passes. The publication workflow verifies them before marking the draft stable/latest.

The first independent review reproduced two backend defects and one test-seam defect. The backend now passes the exact persisted current request separately from merged model history; final synthesis includes research instructions/evidence inside the 44,000-character limit, preserving durable history/provenance or failing closed. Four new regressions pass. The SDK Tasks seam supplies the actual dismissal-style identifier and its compilation/anchor gate passes. Source worker egress was temporarily fail-closed during correction, then the corrected backend was deployed/restarted on the existing service without production chat writes. `deployed-backend-manifest.json` records the corrected live source hashes. A live post-fix query performed real search and honestly disclosed unavailable source pages (`deployed-public-query.txt`); an independent actual public-page read succeeded against Python's robots.txt (`deployed-public-read.txt`). This is not represented as new successful verification of that shop's hours.

## Features/evidence carried forward

See `CHANGELOG.md` for v1.6 features. Actual native synthetic screenshots, motion recordings, exact-token race, preference restart, large text, older-reader anchors, fold/IME behavior and deployed-provider evidence are in `qa/android-web-access-20261110/REPORT.md`. Those captures explicitly record the preceding 1.5.1/code9 implementation checkpoint. The version-only v1.6 rebuild is not represented as new footage or proof that previously incomplete runtime checks were completed.

The shared backend's deployed public-search/page integration was exercised with a real current-information query and real retrieved sources. Greetings and summaries stay tool-free; failures, citation persistence and accepted-request non-reexecution have real-provider scratch evidence and regression coverage.

## Known limits retained in this release

- A combined native recording of a live configured-provider search/read round-trip remains unverified. The isolated Linux fixture intentionally lacks provider packages/configuration; no credentials were copied or production test chat created. Real backend research and native synthetic transport/phase wiring were verified separately.
- Native source actions reached Chrome, but its first-run account/telemetry onboarding prevented external-page completion. Browser privacy/account defaults were not changed merely for QA.
- Physical Samsung/One UI performance, exhaustive TalkBack/accessibility and the end-user installer/update-permission flow remain separate checks. Android MCP/ADB installation is not evidence of the Android user-confirmation installer UI.
- Public-web routing/privacy heuristics are conservative; some sites, compressed/large/binary/JS-only pages are unsupported. No unrestricted agent tools or email/task/calendar mutation capability is granted.

Install the new APK over the existing application; do not uninstall. The in-app updater requires a complete published stable GitHub release with the verified asset triplet and still requires Android installation consent.
