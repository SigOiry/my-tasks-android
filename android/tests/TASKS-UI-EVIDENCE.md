# Tasks native module — test evidence

Run from repository root:

```sh
python3 android/tests/run_tasks_ui.py
```

The runner uses a scratch directory outside `android/build`, compiles the real
owned Java files against SDK 35 with `--release 8`, and uses a signature-only
MainActivity/R seam. That seam verifies integration types, NOT Activity behavior,
Android rendering, TalkBack, touch dispatch, frame timing, or a working device.
The Anchor nested class and UpdateEmphasis policy execute as real host JVM code.
`test_tasks_ui_contract.py` is explicitly a source/palette contract check.

## Observed RED

1. After writing `UpdateEmphasisTest`, javac initially reported missing policy.
   A method-signature skeleton was then compiled and executed using:

   ```sh
   /opt/data/cache/android-build/jdk-17.0.2/bin/javac --release 8 \
     -d /opt/data/cache/scratch/tasks-ui-jvm \
     android/src/com/simon/mytasks/UpdateEmphasis.java android/tests/UpdateEmphasisTest.java
   /opt/data/cache/android-build/jdk-17.0.2/bin/java \
     -cp /opt/data/cache/scratch/tasks-ui-jvm com.simon.mytasks.UpdateEmphasisTest
   ```

   Actual assertion failure: `first visible unread update breathes` (exit 1).

2. `python3 android/tests/test_tasks_ui_contract.py` initially failed on missing
   TaskRowView, missing TasksUi, and missing semantic SPACE_UNIT token.

3. Anchor test was added before its implementation. With the signature skeleton,
   `python3 android/tests/run_tasks_ui.py` failed on:
   `AssertionError: unchanged list restores row` (exit 1).

4. Canonical self-change suppression test was added before `observe` behavior.
   The same runner failed on:
   `AssertionError: own canonical snapshot token stays static without acknowledging`.

5. Process-state/navigation contract was added first; source tests failed because
   `void save(` was absent. Implemented Bundle save/restore and 104dp clearance.

6. Recycled-row emphasis ownership contract failed first because `ownsEmphasis`
   was absent. Added ownership guard so static rows cannot release another slot.

## Observed GREEN

Final `python3 android/tests/run_tasks_ui.py` returned exit 0:

```text
UpdateEmphasisTest: 26 checks passed
Ran 5 tests in 0.003s
OK
Tasks UI: SDK 35 / Java 8 isolated module compilation passed
TasksAnchorTest: 9 checks passed
```

Scoped `git diff --check` returned exit 0.

## Integration and limitations

- Retain one `TasksUi` instance; call `capture()` before replacing the old root.
- Use `build(LinearLayout)`, `refresh()`, `pause()`, `clear()`.
- `save(Bundle)` captures anchor and filter signature; `restore(Bundle)` restores
  that signature and task ID/pixel offset. Activity restores its canonical filter
  fields as usual before build. The emphasis ledger is session-only; process
  recreation starts a new emphasis session, whereas render/config rebuilds on the
  retained instance do not.
- Parent can explicitly call `resetAnchor()` before project/sort render.
- Call `acceptOwnChange(String taskId)` only AFTER applying the server-confirmed
  mutation snapshot. This observes the exact returned token; it never clears unread.
- Native ListView has `clipToPadding(false)` and 104dp bottom padding. Add has
  104dp bottom margin; an additional 88dp footer keeps the final row above Add.
- No GlassSurface class. `WorkspaceUi.glass()` is the existing honest translucent
  tint/highlight approximation, with opaque high-contrast/transparency fallbacks.
- No device/emulator available: native screenshots, 200% text rendering, actual
  touch/TalkBack traversal, IME/rotation behavior, and frame pacing remain unverified.
- No backend/auth/manifest/MainActivity/test.sh edits by this module owner.
