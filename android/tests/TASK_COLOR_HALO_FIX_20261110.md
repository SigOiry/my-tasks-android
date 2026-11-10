# Android task surface / completion / unread halo fix

Latest owner request overrides the previous tiny accent and fixed-blue unread treatment. Android-only; no version bump, signing, install, commit or publish by this worker.

## Source changes

- `TaskRowView.java`: remove both the category rail crossing the checkbox and its floating dot. Keep both 48dp child targets. Reserve horizontal 16dp gutters for every row; unread rows additionally reserve vertical 16dp paint gutters and 18dp content padding. Ordinary rows recycle back to compact 10dp vertical padding; large-text title expansion remains intact.
- `PresentationPolicy.java`: low/medium/high surface blends become 16/26/36% light and 16/24/32% dark. Canonical project hues, identity hashing, importance semantics and explicit Neutral choice remain unchanged. Stronger row-local neutral context and unread-label colors maintain 4.5:1 contrast across all six palette slots; darkened same-hue light-mode priority cues retain 3:1.
- Unread drawing now uses the actual task category for 15 external falloff rings, within the View's reserved 16dp gutter. Gaussian-shaped opacity falls from a strong static inner halo to a soft outer radius; breathing increases only opacity. The 1dp, alpha-170 same-hue rim is secondary, not the old dominant blue outline. No text blur, software layer, negative bounds, or canonical acknowledgement changes. Existing animation cap, resting scheduler, lifecycle cleanup and exact-token ledger are unchanged.

## Executed checks

RED observed before implementation:

- Pinned stronger surface blends failed the actual production JVM `rowTint` with `actual opaque row tint must use pinned priority intensity`.
- Existing contrast checks caught the newly stronger surface with `neutral reading text on every actual composite Websitehighfalse`; stronger label/cue policy then passed. Increasing dark surface strength separately caught unread-label contrast 4.420948463353594; the final row-local unread label corrected it without reducing the surface strength.
- Expanded actual drawing-method test failed with `static unread has fifteen external falloff rings plus crisp edge` on the old renderer.

GREEN final command:

```sh
JAVA_HOME=/opt/data/cache/android-build/jdk-17.0.2 bash android/test.sh
```

Exit 0. Output includes:

```text
PASS 182 presentation checks
UpdateEmphasisTest: 32 checks passed
UpdateReviewPolicyTest: 16 assertions passed
Actual TasksUi.refresh lane/group checks passed: 1357
Ran 69 tests in 19.170s
OK
PASS route
PASS own
PASS ack
PASS reload
PASS quick
```

Full local output: `/opt/data/cache/scratch/task-visual-tests.log`.

`test_owner_visuals.py` compiles actual `PresentationPolicy` and extracts actual `TaskRowView.groupCorners`, drawing, start/stop/pause/resume methods at execution time. `UnreadHaloMethodsHarness.java` verifies background/paint gutter agreement, child padding, no rail/dot over completion, the category-colored broad static falloff, secondary rim, no View-boundary clipping at widths 280/320/600dp and heights 84/180/320dp, invariant breath geometry, continued scheduling through 25.6 seconds, exact-token arrival behavior, interaction/background/reduced-motion cleanup, and recycling back to compact padding. These are host seams, not Android rendered evidence.

The full suite initially rejected removal of the old dot via the obsolete `test_category_is_small_accent_not_text_or_unread` source contract. It was updated to the latest user requirement; all remaining behavior checks pass.

Full production resource compile/link, SDK35 `javac --release 8` and D8 min-api26 also passed, without running the signing build script. Final scratch output: `/opt/data/cache/scratch/task-visual-compile-t3_z513z`. Build log: `/opt/data/cache/scratch/task-visual-build.log`. Javac reports existing deprecated-API usage; there were no compilation errors.

## Parent-owned native visual gate

The actual before image `qa/liquid-glass/evidence/fix-before-tasks.png` was inspected: the rail visibly bisects the completion circle, and unread emphasis appears mainly as a thin blue outline. No after screenshots or runtime claims are supplied by this worker.

1. Rebuild the isolated QA package from current sources with the existing synthetic runtime/certificate/signing identity, using `qa/liquid-glass/build-qa.sh <existing-runtime-root> <new-output-directory>`. Do not restart/replace the parent's active fixture or touch the production account. Transfer/hash-verify/install only `com.simon.mytasks.qa` through the established parent workflow.
2. Capture matching folded light Tasks before/after plus dark and unfolded/split list. Check low/medium/high Research and Operations tasks, with ordinary and unread rows. Completion circles must have no rail/dot passing through them; both targets remain 48dp. The broad halo must follow each task hue, including non-blue tasks, rather than a universal blue stroke.
3. Inspect baseline, peak, and trough frames. Capture after 8 and 20 seconds to prove sustained breathing. With animations disabled/reduced motion, separately capture static visibility. Compare the actual 8–15dp external band, not only the rim. Geometry/text must remain stationary and halo must not clip at list/header/footer boundaries.
4. At 200% font and compact cover width, inspect full title wrapping, unread-label visibility, 48dp targets and final-row reachability; no child should occupy the paint gutter. Repeat sort/color changes and scrolling to check recycled rows.
5. Verify summary open/dismiss remains unread and explicit exact-token acknowledgement clears only the displayed version, including a newer canonical update arriving during review. No acknowledgement logic was modified, and its host regressions pass; native semantics remain the parent's acceptance gate.
