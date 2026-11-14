# Native chat/source acceptance — 2026-11-14

## Post-fix bounded verification — current source

**PASS: two compact 100% native smoke scenarios**, reusing the existing prepared fixture, runner and scenario inputs. Built final Android source into `/opt/data/cache/scratch/chat-search-20261114/build-postfix/my-tasks-qa.apk` and upgraded only `com.simon.mytasks.qachat20261114`. Local, transferred and installed APK SHA-256 all match:

```
e51be106984789580219544740e0f55b2f2f43dd109d5f2449f77d8a8d61b837
```

- Task/email/menu/clipboard scenario passed: two receipt-bound spans, blocked-scheme zero spans, prose-only bubble, actual long-press menu, exact original clipboard text, saved synthetic email details, and canonical task navigation.
- Inline scenario passed: actual pointer tap, exact nonblocking HTTPS ACTION_VIEW/BROWSABLE monitor once, external Chrome first-run screen, and return to the original conversation. No remote page-load claim.
- Full-display saved-email and external-browser screenshots were visually inspected. Saved email sender, subject, timestamp and complete synthetic thread are visible; Done is reachable. PixelCopy app-surface PNG alone does not capture its dialog; use the `-display.png` evidence.
- Only `CitationUi.java` differs from the old APK's recorded production Java source. Only changed `server/conversations.py` and `server/public_web.py` were refreshed into the owned synthetic fixture; all deployed backend module hashes were checked. Protected fixture files retained exact hashes. Fixture restarted from PID 1117416 to **1182147**, still bound only to `127.0.0.1:9147`.
- Font **1.0** and fold **OPENED/state 2** restored and read back. Production package path/hash unchanged; no production session action, real provider/mail, production edits, or commit. Synthetic email jobs/proposals/confirmations/drafts/requests remain zero.
- Receipts: `evidence/post-fix-verification.json`, `evidence/postfix-installed-build-receipt.json`, `evidence/postfix-backend-source-manifest.json`, and `evidence/postfix-smoke-results.json`. Native outputs and screenshots use `postfix-` prefixes; old evidence is retained separately.

This is a bounded post-fix smoke, **not a rerun of the eight-scenario matrix or a native lifecycle-race test**. Host lifecycle coverage is outside this smoke. QA signing/package identity deliberately differs from the separately built public release APK.

## Prior result — pre-fix historical matrix

**Everything below describes the earlier pre-fix run unless explicitly labeled otherwise. Its “final/current” source statements were accurate at that earlier checkpoint, not after the lifecycle/privacy changes.** The old source/install and backend receipts are historical, superseded for current source by the post-fix receipts above.

Pre-fix Android **1.6.4 / versionCode 14**, isolated package `com.simon.mytasks.qachat20261114`, was compiled from the current production Java source, QA-signed, transferred, installed, and exercised on the existing Pixel_Fold `emulator-5554`. No production source, production package, release signer, or production session was changed. No commit was made.

Local APK = Linux-transferred APK = installed base APK SHA-256:

```
f2c6c9b36bb308f875b21941b4f48f8011da05081fbed7674631b00f7da40476
```

APK: `/opt/data/cache/scratch/chat-search-20261114/build-final-01/my-tasks-qa.apk`.

Per-source and install receipt: `evidence/installed-build-receipt.json`. All recorded Java hashes were checked again against final current source after native testing; no drift. Backend source/deployment hashes, including `task_knowledge_context.py`, are in `evidence/final-backend-source-manifest.json`; final source and remote hashes were reverified.

## Native acceptance actually exercised

`evidence/matrix-results.json` records **eight passing scenarios**: task/email/menu/clipboard flow and inline browser flow for each of compact 100%, compact 200%, expanded 200%, expanded 100%. Font scale was set before instrumentation started. Measured native viewports: compact 411×797dp; expanded 841×701dp. Device captures: compact 1080×2092px, expanded 2208×1840px. Observed layout at 200% was a reading-oriented single pane.

- Receipt-bound assistant Markdown phrases had exactly two real `ClickableSpan`s. A physical-style pointer down/up at the Example Domain phrase launched the external Chrome activity. A **nonblocking instrumentation monitor matched exactly ACTION_VIEW + BROWSABLE + HTTPS + example.org + path `/` once**. Actual full-display screenshots show Chrome, not the Activity's stale PixelCopy surface.
- Browser onboarding remained untouched. Chrome displayed its first-run screen. This proves safe URI dispatch and external launch, **not successful loading of the remote page**.
- `blocked scheme` had zero clickable spans in every matrix task scenario. A separate actual pointer tap left the native synthetic conversation visible (`unsafe-label-remains-native-display.png`).
- Actual pointer long-press on **bubble padding** opened the Hermès message menu. Long-press on selectable prose instead selected a word; the bubble-padding target is required to test the separate message menu. Menu contains Copy original, View original text, Review as new task, Related sources. No programmatic `performLongClick` substituted for the accepted bubble test.
- Actual pointer tap on Copy original followed by Android `ClipboardManager` exact-text equality against persisted original prose passed at every layout. The expected text includes original Markdown, both HTTPS URLs and unsafe-scheme Markdown, not only rendered text.
- Actual pointer taps selected Related sources, saved Email context, and Task. Task navigation asserted canonical ID `67c2aa7b5291450db00f5ce24aad8eb5` (Reply to the workshop update), rather than matching only its title. Automatic unread update summary was dismissed without acknowledgement.
- Saved synthetic email details were resolved through the current fixture HTTP backend and displayed sender, subject, timestamp, excerpt and fictional thread. No composer/send action was used. Final backend email workspace tables jobs/proposals/confirmations/drafts/requests all remained zero.
- The assistant bubble was asserted to contain exactly its prose view: no appended Sources/footer/chip views. This fixture has no warning rows; warnings-specific rendering was not tested here.
- Supplemental compact/expanded **200%** scenarios opened the same menus/details again, scrolled the email dialog to its final incoming text, dismissed it, navigated to the canonical task, then returned via task Back and Discussions to the exact original conversation ID `c7c145d9-314f-4cce-b939-9e88e74f8cf4`. Browser return also retained this ID in all four layouts.

Screenshots were loaded and visually inspected, including compact/expanded chat, 200% wrapping/scrolling, 200% compact message menu, saved email top/bottom and dismissal, task navigation, external Chrome launch and return. At compact 200%, header title/subtitle ellipsize; body content scrolls and the tested inline link, menus and Done remain reachable. This is not a claim that the entire long message fits simultaneously.

## Fixture refresh and synthetic provenance

Only the owned Linux fixture's final backend Python modules were refreshed. The only generated-code changes were the existing exact filesystem relocations. Existing synthetic certificate/key/password/tasks and store identities were retained; no production data, credentials or stores were copied. PID **1115078** was identity-checked, stopped once, and replaced with **1117416**. Final listener is **127.0.0.1:9147 only**. Certificate-verified health, anonymous task access 401, normal native login/read/logout, 13 synthetic tasks and four conversations were rechecked.

The preexisting synthetic assistant message had task/email receipts but **no web receipts**. Its link-looking prose correctly rendered non-actionable in that state. To test receipt-bound UI, two explicitly synthetic public receipts were added to that same synthetic message's metadata using the current `citations.public_metadata` serializer. Original prose and task/email receipts were preserved. This was a controlled QA-only SQLite transaction with no queued/running requests, not a production search/retrieval. `evidence/synthetic-web-receipt-seed.json` records the exact expected prose and synthetic metadata. Do **not** describe these injected receipts as proof of a real provider search or page read.

## Other verification and harness issues

- Current actual extracted production citation methods passed both host regressions: `evidence/host-inline-method-regressions.txt`. Host seams are additional checks, not native proof.
- First evidence pull failed because the owned remote evidence directory did not yet exist. It was created; login artifacts were collected normally.
- Harness fixes addressed dialog-local versus screen coordinates and a restored compact conversation whose title is not a clickable list row. Final acceptance used real screen coordinates and detected an already-open thread.
- One accessibility-root external-package check returned null despite an independently captured Chrome display. It was removed from the final matrix rather than treating absent UiAutomation ownership as app failure. Exact nonblocking intent monitor plus inspected full-display evidence remained the browser gates.
- A first return-navigation supplement looked for Discussions while compact task detail hid main navigation. Explicit task Back, then Discussions, passed; final evidence records that actual route.

## Boundaries / not tested

No real model, live public-search provider, mailbox retrieval, send, production deployment, release APK, physical foldable, TalkBack traversal, animation/FPS, raw-URL native tap, second IANA-link launch, warning rows, stale-account callbacks or arbitrary malicious-URL matrix were verified in this native run. Current extracted-method regressions cover additional source/fence logic, but are not substitutes for native checks. No browser account/telemetry onboarding defaults were changed.

## Ownership and cleanup handoff

- Historical fixture PID 1117416 was replaced during post-fix verification. **Current retained fixture PID 1182147**; PID file `/home/simon/hermes-apks/qa-chat-search-20261114/runtime.pid`; log `runtime.log` under that root. Verify `/proc/1182147/cmdline` contains this exact fixture entrypoint/root and the loopback listener before terminating only that PID. Do not print credential-bearing process arguments.
- Retained emulator PID **1114168**. Emulator shutdown remains parent-owned.
- Owned reverse remains `adb -s emulator-5554 reverse tcp:9147 tcp:9147`; remove only this mapping when cleanup is requested.
- Font **1.0** and fold **OPENED/state 2** were restored and read back. See `evidence/final-verification.json`.
- QA package and separate QA instrumentation remain installed. Existing production/other QA packages were not uninstalled, cleared, updated or logged out.
- Scratch runners and scenario inputs remain under `/opt/data/cache/scratch/chat-search-20261114/`; signing/certificate/private synthetic password remain private there. Do not stage that private runtime.

## Release verification

The parent rebuilt the original-signer release APK as 1.6.4/code14 (SHA-256 `9a5598c02cf8d2327dc2185d4efe5ea5b9d2a0cfead6d65973314e307053ce0e`). Fresh final gates passed: 193 backend tests, 135 browser tests, 77 Android Python tests plus JVM/controller/email suites, SDK compilation/dexing/signature/alignment. All 21 browser source and packaged presentation files were unchanged. Independent reviews cleared the privacy and source-navigation repairs; conservative private routing includes supplied transformations and explicit no-browse intent, with English/French immediate/follow-up regression tests.

Reviewed backend modules were deployed after a consistent private stopped-state backup; only My Tasks was stopped/started. Source hashes, unchanged canonical task bytes and preservation of all private table counts were verified, along with public HTTPS health/root 200 and anonymous tasks 401. A real configured-model request inside the deployed container used only synthetic scratch task/mail data in a **free Discussion**: the saved code was retrieved, email/source receipts persisted, reopen preserved provenance and replay made no second model call. No public search/page or mail sending was permitted by that probe. See `evidence/live-search-proof.json` and `evidence/release-verification.json`. Source publication and binary publication require their separate GitHub readbacks.

## Parent cleanup

After post-fix native smoke, the parent identity-checked and stopped owned synthetic backend PID 1182147, removed only its ADB reverse on 9147, and shut down the emulator it had started. Readbacks confirmed no listener on 9147, no emulator device, and both owned PIDs exited. QA app data was not cleared and no production package was altered.

## Reusable QA recipe

Use adapted scratch `toolkit/qa/liquid-glass/run_native.py`, never shared production tooling. Inputs belong in `scenarios/`, separate from output names. Native span tests use real layout-derived offsets; dialog touch bounds must be translated from root-local to absolute screen coordinates. Use bubble padding for its custom long-press menu while retaining text selection semantics. Prove clipboard equality against persisted original prose. Use a nonblocking exact-URI intent monitor plus full-display capture for browser launch, leaving first-run settings untouched. Set font before instrumentation, restore in `finally`, and verify exact installed/source hashes after the entire matrix. Preserve remote cert/store files when refreshing only final backend modules.
