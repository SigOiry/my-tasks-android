# Android 1.6.3 reply workspace QA

## Scope and result

Android-only fixes for spontaneous keyboard dismissal, unclear reply/assistant actions, cramped foldable/landscape/large-text layouts, duplicate suggestion rendering and controller races. All 90 tracked `server/` files are byte-identical to source baseline `914c68f6ac91305d0c9ccfda43cf4da7b73989ba`; no shared-backend or browser presentation change is included. See `server-unchanged.json`.

Final independent source review passed with no security concerns, logic errors or suggestions (`independent-review.json`). The parent separately exercised and verified the final native GREEN checks described below. Earlier RED receipts intentionally document reproduced defects, not the shipping outcome.

## Artifact and upgrade

- Official package: `com.simon.mytasks`, version **1.6.3 / code 13**.
- Official APK SHA-256: `1dc646db6da41fa81b77f4f44f4384958052903730edb93ac9a3b4cb1c4d3014`; **870127 bytes**.
- Original certificate SHA-256: `a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da`.
- Original-key build, signature/alignment, actual package/version and generated release metadata verified. `build-official-final.log`, `official-artifact.json`.
- Mint's production package was **1.6/code10**, not the prior public stable 1.6.2. Its installed certificate was verified before the data-preserving upgrade. MCP installed the official candidate without uninstalling or clearing data. The installed base APK hash equals the exact official artifact; native MainActivity opened authenticated Tasks with the existing Project preference. No production task, chat, draft or mail action was submitted. `pre-upgrade.json`, `official-upgrade.json`.

## Regression gates

- `JAVA_HOME=/opt/data/cache/android-build/jdk-17.0.2 bash android/test.sh`: JVM/policy/controller/extracted-method checks and **69 Python tests** passed. The final official build reran this gate. It includes `EmailReplyHingeUiHarness`: **36 actual-method geometry cases**, four densities, three font scales and vertical/horizontal/no occlusion, repeated Hide/Email/Show, immediate and settled bounds, focus, text, selection, persistence, input identity and attachment. The geometry doubles are not native IME evidence.
- From `server/`, `python -m unittest discover -s tests -q`: **164 tests passed**. An initial invocation from the wrong directory failed import discovery; it was corrected, not counted as a pass. `backend-final.log`.
- From `server/`, `node --test tests/web_*.test.mjs`: **135 passed, 0 failed/skipped**. `web-final.log`.
- Added-line credential/injection/deserialization scan and `git diff --check`: passed.

## Actual Pixel Fold emulator checks

Native runtime: Android 17 Pixel Fold on the Mint testing host. Separate QA package `com.simon.mytasks.qav162`; real authenticated API/controller/store code with explicitly synthetic mail and AI boundaries on Linux loopback HTTPS. Production credentials/state were not copied into QA.

The final post-hinge QA APK is hash-verified locally, after SSH transfer and from the installed base APK: **ecf0849f4f6227eab81eef5fb363f017f33943ef6089c4a2241c7d46038f8772**. Source hashes are in `source-hashes.json`. The distinct QA certificate, trust overlay and package are not the official distributable.

| Gate | Observed result / evidence |
|---|---|
| Autosave typing | Ten repeated edits across autosave; same EditText, focus and visible IME; selected range retained. `final-writing-shipping-result.txt`, `final-typing-ime-display.png`. |
| Assistant updates | Polling completion and mode changes retain the live instruction input, selected range and IME. New unsent instructions remain intact. `final-ai-poll-ime-display.png`, `final-modes-ime-display.png`. |
| Fold / rotation | Actual closed/open emulator states and portrait/landscape round trips retain draft/instruction text, selection and keyboard. Short landscape uses anchored inline input/action. `adaptive-shipping-result.txt`, `landscape-ai-ime-display.png`, `split-ai-ime-display.png`. |
| Nonzero hinge | A QA-only injected 16dp vertical occlusion exercises actual attached native views. Baseline atomic Hide failed before layout callbacks; fixed Hide and Show synchronously update hinge mode, then measured pane bounds avoid the occlusion. Both inputs retain identity. `hinge-native-atomic-red-result.txt`, `hinge-native-green-result.txt`, `injected-hinge-*-display.png`. This is injected geometry, not a physical hinge sensor claim. |
| Unfolded writing space | Both editor and assistant inputs retain at least 48dp visible above IME. A redundant left-pane assistant footer was removed. `split-editor-shipping-result.txt`, final native adaptive evidence. |
| 200% text | Focused assistant input retains at least 48dp visible with IME; secondary controls scroll, nonessential chrome yields space. `font200-shipping-result.txt`, `font200-final-display.png`. System font changes are applied before instrumentation because they can recreate the Activity. |
| Errors / recovery | Offline autosave leaves the same editor, keyboard and exact writing intact; explicit Save after connectivity restoration succeeds. `offline-shipping-result.txt`, `offline-writing-retained-display.png`, `offline-recovered-display.png`. |
| Suggestions / review | One suggestion/action group per exact message/proposal ID; explicit Apply updates the draft. Readable suggestion and current/suggested comparison replace technical diff markers. Review waits for dirty text to save; Cancel/Back retains writing. Actual-method dedup/capture tests and `proposal-readability-green-result.txt`. |
| Sending | Explicit review and confirmation passed through the native UI into the isolated synthetic transport. Exactly one additional synthetic receipt was created (existing count1 → count2); no real mail was sent. The read-only success receipt replaces raw provider JSON. `synthetic-send-result.txt`, `receipt-green-result.txt`, `sent-receipt-readable-display.png`. |
| Themes | Native compact and unfolded light/dark screenshots visually inspected. `folded-dark-editor-display.png`, `folded-dark-assistant-display.png`. |

Suggestion comparison, synthetic sending and readable-receipt runtime checks ran on the pre-hinge candidate; their controller/presentation paths were unchanged by the final geometry-only fix. Final-source retests covered typing/polling, errors/recovery, folding/rotation, both-pane input height, large text and injected hinge transitions. The bounded host screenrecord in `writing-keyboard.mp4` is supplementary pre-hinge-fix typing evidence. Decoded beginning/middle/end were inspected; only synthetic QA or the emulator launcher appears. Its canvas letterboxing and 26.8-second encoded duration are encoder properties, not a measured UI frame-rate claim. The final shipping-source typing scenario was rerun separately after the hinge fix.

## Mechanisms and regression boundaries

- Server revision/job/notice updates no longer replace the editor. Stable panes reflow in place; size-only Activity changes do not recreate the root.
- Explicit pane changes recompute hinge-safe bounds without recapturing the old focused pane and overriding the user's selection.
- Background history following uses `scrollTo`, not focus-transferring `fullScroll`. Error visibility is recomputed when notice changes, including while large-text keyboard chrome is compacted.
- Dirty Review/Assist chains behind a single autosave; a debounce deferred by another request drains after success. Failures preserve writing and do not loop retries.
- Preview responses are fenced by route, draft, revision, body and edit generation, including edit-away/edit-back. Pending or ambiguous AI admissions retain their exact request identity rather than creating duplicates.
- Outgoing widget capture cannot overwrite a newly restored draft's parked instructions. Back-to-writing dismissal is persisted.

## Limits and cleanup

- Physical Samsung Galaxy Z Fold8 / One UI, TalkBack end-to-end, genuine hardware hinge reporting and real external email delivery remain separate checks. Synthetic AI does not prove real model output quality; no mail/model backend changes are shipped here.
- A synthetic composing-span injection was inconclusive: Gboard explicitly finished composition via RemoteInputConnection. That probe is not counted as a passing acceptance gate; the app does not force composing spans against the IME. Actual input identity, focus, selection and visible-keyboard stress gates passed.
- During early deployment Mint's root filesystem filled and the emulator stalled. Only byte-identical redundant owned QA recording copies were removed; the emulator was restarted without a data wipe after space became available. No unrelated user files were deleted.
- Private account, signing material and runtime stores are excluded from source/evidence. Public screenshots use synthetic content; production task screen was inspected but not published.
- Final cleanup and public-release receipts are recorded with deployment verification. Original device settings and the official production foreground are restored; the owned loopback fixture/reverse are stopped after QA.
