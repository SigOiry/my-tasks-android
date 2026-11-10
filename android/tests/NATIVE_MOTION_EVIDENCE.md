# Native motion checkpoint (host + SDK verification)

## Implemented

- Tasks scope selection uses one measured capsule behind transparent semantic buttons. Before hierarchy replacement the retained Tasks presenter captures the capsule's current visual X as a track-width fraction (including interrupted motion). The new layout resumes from that fraction to the selected button over 210ms. First layout/resizing and reduced-motion selection are static. No list-pane insertion bounce on scope changes.
- Navigation has a separate horizontal 280ms helper: +24dp for entry, -24dp for return, both ending at zero. Task navigation retains its busy/dirty/confirmation guards. Task chat opening, saved global conversation selection, explicit chat back controls and system back share directional page motion. Message insertion still uses the existing +6dp Y helper. Pause settles page transforms.
- Safe idle refresh snapshots up to 12 visible task tags before adapter notification. A one-shot pre-draw after anchor restoration moves retained rows from their previous visual tops to final layout tops over 200ms. Offsets over 160dp and newly visible identities are not animated. Filter changes do not FLIP. Touch/scroll/edit/busy/private-dialog/pause and reduced-motion gates suppress snapshots/application; touch/scroll/pause cancel running row motion. Binding resets recycled translations. Canonical ListView selection and anchor policy are unchanged.

## Verification

Strict vertical RED/GREEN runs preceded the production seams and task navigation wiring. `test_native_motion_methods.py` extracts current production methods using the existing comment/string-aware extractor and executes their geometry, state and animator calls with deterministic **host-only seams**. Assertions cover live interrupted capsule position, measured target size, reduced motion, signed navigation, discard-confirmation ordering, insertion isolation, idle retained-tag reorder, safety gates, recycled offsets and pause settling. These are not actual Android frame/render tests.

Executed:

```sh
JAVA_HOME=/opt/data/cache/android-build/jdk-17.0.2 \
  python3 -m unittest discover -s tests -p test_native_motion_methods.py -v
JAVA_HOME=/opt/data/cache/android-build/jdk-17.0.2 bash test.sh
```

Eight focused methods tests passed. Full native suite passed, including 58 Python tests and existing deterministic JVM/controller/API/actual-method regressions. Existing toolbar-anchor repeated-capture/restore test remains green.

SDK35 aapt2 resource compile/link, all production Java8-compatible javac and D8 min26 passed. Isolated unsigned compile checkpoint is `android/build/motion-checkpoint/`; no existing signed APK was replaced. Compiler emitted only the existing deprecated-API note.

## Limits for final QA

- No device/emulator/installation/visual-frame result is claimed. Verify real layout/pre-draw timing, rapid taps, dynamic text sizes, RTL expectations, expanded panes and interruption/resume on device.
- Page motion is incoming-page translation, not a retained outgoing-page shared transition. Global Discussions currently transitions the root container; task navigation transitions its active pane. Ordinary refresh callbacks and thread creation/picker refreshes are deliberately not globally animated.
- Follow-up: emphasis remains bounded at two active effects, but overflow no longer consumes its token before admission. Releasing a real active slot schedules a foreground-visible-row batch; already admitted tokens cannot replay. The ledger now has 32 passing checks. Full visible-time resumption after recycling/background cancellation is not claimed.
- Parent final native installation/captures/recordings and remaining blockers are documented in `qa/coordinated-evidence-20261110/REPORT.md`; that evidence supersedes this host-only checkpoint for observed device behavior.
- The pre-existing render-host regression harness has a `renderPage` UI seam delegating to its render hydration; actual navigation geometry is exercised separately by the motion harness.
- No dependency migration, signing, device access or commit.
