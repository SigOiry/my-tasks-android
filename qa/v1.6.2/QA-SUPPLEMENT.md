# Bounded native QA supplement — 2026-11-10

Target: existing isolated `com.simon.mytasks.qav162` installation and existing synthetic backend; installed instrumentation reused without changes. No application Java/source changes, reinstall, deployment, send action/API, or update-ack action/API.

## Passed
- Actual portrait → landscape → portrait: native dimensions 411×797 → 797×411 → 411×797 dp. The unsent AI instruction remained byte-identical in each snapshot. The existing unsent draft body was byte-identical between the initial editor snapshot and the subsequently unfolded editor after the rotation round trip.
- Five actual native **Ask · discuss** requests populated overflow history using the synthetic provider; no claims about real-model quality.
- Unfolded independent scrolling: native editor ScrollView moved 0→464 while assistant remained 0; separate physical swipe inside the assistant moved its ScrollView 0→836 while editor remained 0. The instruction stayed outside the history viewport. JSON and before/after display screenshots record both directions.
- Inspected actual landscape and unfolded assistant-before/after display screenshots: controls readable, separate panes visible, no unexpected overlap. History and long body are intentionally viewport-clipped and scrollable.
- Restored original accelerometer rotation=1, user rotation=0, font scale=1.0 and closed posture (override=0, base=2). Readback confirms production `com.simon.mytasks/.MainActivity` foreground. Rotation settings were reapplied after folding because fold transition reset auto-rotation.

## Untested / limits
- Selection/cursor retention: installed instrumentation snapshot has no selectionStart/selectionEnd fields or selection operation; deliberately left untested rather than altering application or rebuilding instrumentation.
- Body is hidden in the compact assistant view; full body equality is checked before versus after the complete rotation/unfold sequence, not independently at the intermediate landscape frame.
- No send or update acknowledgement was invoked. Receipt count was not independently re-read in this supplement.

Evidence: `native-supplementary/results.json`, scenario inputs, native JSON snapshots, PixelCopy screenshots, physical display screenshots, instrumentation PASS outputs and original/restored device readbacks. Only supplementary QA files were added; private orchestration scripts live under scratch and contain no credentials.
