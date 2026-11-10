# v1.6.2 actual native QA

## Outcome
Actual isolated native workflow and one fixture-only send passed. **Native acceptance is not fully green:** unfolded split view and source chooser issues remain. No host-test result is counted as native QA.

## Installed QA artifacts
- `com.simon.mytasks.qav162`: SHA-256 `467446a5b02f91f663594086919aa2f0639e19e247897877a6faa752f2dbfc36` (local = strict-SSH transfer = installed base APK).
- `com.simon.mytasks.uiqav162`: SHA-256 `7c2dd854e16f2ff141c9ea09cfae6b8a257d3cc9bbc3bed26a9f28a244e25073` (local = strict-SSH transfer = installed base APK).

## Runtime
Pixel Fold emulator: CLOSED state 0, OPENED state 2; density 420dpi / 2.625. Folded 1080×2092 px, 411×797dp; unfolded 2208×1840 px, 841×701dp. Native instrumentation uses Window PixelCopy plus host screencap for display/IME; no UiAutomation takeover. System light/dark both captured.

## Cases
- **PASS** — QA-only fresh build, strict SSH transfer, installed hash readback. Evidence: installed.json
- **PASS** — Folded update Reply leaves exact task unread through send. Evidence: native-after-close.json; native-server-readback.json
- **PASS** — Multiline manual writing and autosave. Evidence: native-manual-folded-light.json
- **PASS** — Draft with AI proposal preview and explicit Apply. Evidence: native-ai-draft-preview.json; native-applied-body.json
- **PASS** — Improve writing preview and Dismiss. Evidence: native-improve-preview.json
- **PASS** — Ask AI discussion persists after close/reopen. Server confirms all six messages; last discussion response outside visible history viewport.. Evidence: native-server-readback.json; native-reopened.json
- **PASS** — Explicit review, confirm, send once and fixture receipt. Evidence: native-send-review.json; native-send-receipt.json; native-server-readback.json
- **PASS** — Fold/unfold retains active instruction and saved draft. Evidence: native-compose-unfolded-light.json; native-unfolded-dark.json; native-applied-body.json
- **PASS** — Outer dedicated assistant switch and keyboard usable. Evidence: native-assistant-folded-ime-display.png
- **FAIL** — Unfolded two-pane editor/assistant. At 841dp unfolded (2208x1840 px, density 2.625), assistant occupies whole width. 64/36 pane minima require >=833.34 usable dp; padding takes viewport below that.. Evidence: native-compose-unfolded-light-display.png; native-unfolded-dark-display.png
- **FAIL** — Historical source disambiguation. Choose which workshop message to answer > Reply to email: both candidates have identical visible labels; no excerpt/reference shown.. Evidence: native-ambiguous-chooser-display.png
- **FAIL** — Source chooser task-specific status. After sending first task, close and open another task Reply: chooser incorrectly displays prior Sent receipt state.. Evidence: native-ambiguous-chooser-display.png
- **FAIL_SOURCE_INSPECTION** — Citation metadata.warnings rendering. No metadata.warnings read/render path. Not runtime-warning-injection tested.. Evidence: android/src/com/simon/mytasks/CitationUi.java

## Server verification
`native-evidence/native-server-readback.json`: one `synthetic-receipt-1`, draft `sent`, three succeeded jobs, six AI messages. Exact reply task remains absent from notification acknowledged/seen records; native Tasks still shows New update after send. No external email transport or real AI provider used.

## Untested
- Rotation and cursor-selection preservation assertions
- Physical Samsung/One UI and hinge occlusion
- Recording/motion timing (no recording launched)
- Unavailable-source recovery, conflict/offline, backend late-context refresh
- Real AI provider or external mail: intentionally synthetic only
- TalkBack/large text and process-death recovery

## Evidence and limits
See `native-evidence/` PNGs/semantic snapshots, installed hashes, safe instrumentation results and raw synthetic server readback. Reviewed light/dark folded/unfolded display images and IME image visually: folded controls readable, IME leaves instructions and Ask visible; unfolded lacks intended two panes; ambiguous chooser visibly duplicates labels and leaks stale Sent status. Screenshot inspection does not prove motion. No recording launched, so no recording tail exists. Fixture process is parent-owned and left running. No app source changes, production package install, commit, deploy or publish.

Harness retries are recorded in JSON and are not counted as app defects. Password read programmatically from private scratch only for login; never copied to repository.
