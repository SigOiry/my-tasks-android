# v1.6.2 final actual native QA

Outcome: prior runtime failures resolved in this rerun; citation warning fix verified by extracted actual-method tests, not injected on device. Independent scroll has limited runtime coverage described below. Historical initial report and receipts were not overwritten.

## Artifact
- Package `com.simon.mytasks.qav162`, versionName 1.6.2, versionCode 12.
- Fresh APK: `/opt/data/cache/scratch/v162-final-qa-2259/my-tasks-qa.apk`
- Local = strict-SSH transfer = installed base SHA-256: `eaa49cb32864e7d0fc986f91d40d33e03da8f3b46ae04b08b901447b57004643`
- Source SHA manifest: `fa6feb60081e17bd2a1632e77e7f593b608721adbbeeb1d9ef50005fd5ea9ead`; exact per-file SHA-256 values and byte count in `native-final-evidence/installed-final.json`. Dirty source tree provenance, not a clean commit claim.

## Results
- **PASS** fresh-build-transfer-installed-hash.  Evidence: `native-final-evidence/installed-final.json`, `native-final-evidence/build.log`
- **PASS** 841dp-simultaneous-editor-assistant.  Evidence: `native-final-evidence/final-unfolded-light-display.png`, `native-final-evidence/final-unfolded-dark-display.png`
- **PASS_LIMITED** independent-scroll. Actual editor ScrollView scrollY 0→464 while assistant ScrollView remains 0 and its bounds unchanged. Assistant history empty: overflow scrolling on assistant not exercised. Evidence: `native-final-evidence/final-unfolded-dark.json`, `native-final-evidence/final-independent-scroll.json`
- **PASS** fold-body-unsent-instruction.  Evidence: `native-final-evidence/final-folded-light.json`, `native-final-evidence/final-unfolded-light.json`, `native-final-evidence/final-folded-dark.json`, `native-final-evidence/final-retained-body-dark.json`
- **PASS** source-distinct-excerpts.  Evidence: `native-final-evidence/final-source-rows-display.png`
- **PASS** no-stale-Sent-after-task-switch. Sent receipt task opened, closed, other task selected; chooser displays Saved, no Sent or saved-delivery action. First rapid sequence timed out during task-list transition; separate fresh native snapshot and retry passed. Evidence: `native-final-evidence/final-receipt-task.json`, `native-final-evidence/final-source-rows.json`, `native-final-evidence/final-source-result.txt`
- **PASS_EXTRACTED_NOT_RUNTIME** citation-warnings. CitationWarningsTest and actual CitationSourceUiHarness passed; CitationUi renders CitationPolicy.warnings. No runtime warning injection performed. Evidence: `native-final-evidence/extracted-tests.log`
- **PASS_LIMITED** font200-smoke. 200% actual native snapshot and visible 48dp reply input assertion passed. Review button wraps to separate row, recipient wraps, bottom action rail horizontally scrolls. No comprehensive accessibility audit. Evidence: `native-final-evidence/final-font200.json`, `native-final-evidence/final-font200-dark-display.png`
- **PASS** receipt-preservation. Readback still one synthetic receipt; no further send invoked. Evidence: `native-final-evidence/final-server-receipts.json`
- **PASS** production-foreground-restored.  Evidence: `native-final-evidence/production-foreground.txt`

## Actual visual inspection
Folded/unfolded light and dark display captures inspected: simultaneous editor and assistant at 841dp now present, folded dedicated assistant readable, unsent instruction retained, distinct full excerpt endings “2” and “3” in source rows, no stale Sent on other task. At 200% font, primary action moves below title and recipient wraps; reply input remains visible, bottom action rail horizontally scrolls. Screenshots prove layout only, not motion.

## Bounds and cleanup
No new send, real email, production install, source edits, commit, or publish. Exactly one synthetic receipt remains in fixture readback. Restored font scale and system light mode, folded state, production foreground read back. Parent-owned fixture kept running. Rotation/cursor, physical OEM, injected warning rendering and assistant-history overflow scrolling remain untested. Native harness first rapid task-switch timed out during UI transition; independently inspected state and retry succeeded, logs retained.
