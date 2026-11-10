# Android owner-brief implementation — 2026-11-10

**Intermediate worker notes, not the final artifact manifest.** Subsequent lane, dismissal and contrast-test integration changed the build. Final signed APK, installed/download hashes, actual runtime captures and remaining acceptance gaps are authoritative in `qa/android-web-access-20261110/REPORT.md`; the SHA/test counts below describe this worker's earlier build only.

## Deliverable

- Signed release APK: `android/build/my-tasks.apk`.
- Package `com.simon.mytasks`, version **1.5.1 / code 9** (previous official code 8).
- APK SHA-256: `b2070eab816b1af52c0c9265672b85cd51b255aa59f42f6238fdfd73a1554c8d`.
- Verified v2/v3 signing and alignment using the existing external deployment-tree signer. Certificate SHA-256: `a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da`. No signing material copied or regenerated.
- Full build/test output: `/opt/data/cache/scratch/android-owner-build.log`.

## Causes corrected

1. Category hue previously affected only a tiny dot, while ordinary row fill remained white and unread fill replaced category with generic blue. Importance mode replaced project hue with global red/amber/teal. Rows now use opaque project-hue composites at high/medium/low **14/8/4% light; 20/12/6% dark**, with neutral text, a small indicator/slim accent, shape-distinct priority flags, accessible priority labels and explicit labels in Importance mode. Project/Importance keep the same stable canonical hue. Neutral remains untinted. Existing missing-preference default was already Project and is retained without rewriting explicit choices.
2. The old alleged glow was an interior low-alpha thick outline and the finite ledger permanently refused that same token after four cycles. The renderer now paints seven overlapping falloff rings **outside the crisp inset surface edge**, followed by the crisp 1.5dp edge. The first 1.6-second cycle is the stronger exact-token arrival; subsequent cycles have lower-amplitude resting breathing. Four cycles are only a **6.4-second fair scheduling quantum**, followed by continued resting effects; they are not a stopping rule. Arrival identity and resumable rest share a two-effect bounded pool, with FIFO waiting, exact-token comparison and safe release/cancellation. Interaction, editing, dialogs, offscreen/background and reduced motion suppress animation. Static halo/edge/New update remain until actual canonical read state changes. No acknowledgement paths were changed.
3. Incoming messages previously never animated. New incoming tail insertions now use the real history delta and 200ms motion; initial history, pagination and older readers remain stationary. A prepared request previously displayed as uncertain even during real admission. A local bubble now says Sending only while the exact send operation is actually active; recovery/ambiguity remains explicit, and canonical admission does not replay that bubble insertion.
4. Added moving measured capsules for global tabs and Discussions scopes; Tasks scopes retain their measured capsule and gain content entry. Forward/back uses signed 48dp / 280ms entry; global/scope content uses 210ms. Native dialogs/anchored menus now coordinate decor translation and actual native-window dim amount on one 280ms clock, retaining preflight bounds/anchors and existing dismissal/focus semantics. Completion remains canonical-confirmed, not fake optimistic success.
5. API 30+ composer follows the actual OS IME animation fraction and before/after measured position, with ancestor overflow allowance to avoid clipping; reduced motion and detach reset transforms. Existing viewport/draft/fold retention remains intact.
6. Normal assistant replies gain bounded deduplicated explicit public HTTPS source actions. Only validated HTTPS browser ACTION_VIEW actions are created; userinfo, local/numeric hosts, private suffixes and non-HTTPS schemes are excluded. Markdown/update summaries remain inert. Capabilities text no longer falsely claims that no model tools are enabled.
7. Inspected the concurrent backend's actual additive `web_phase` contract (`searching`, `reading`, `answering`). Android reads it only from the accepted request metadata while status is running; Searching public web / Reading public sources are never timer-invented. Connectivity changes participate in footer binding. Lost connection displays Last known and removes the wave; running dots also honor window focus and reduced motion.

## Preservation

- Smart/traditional comparisons, local-calendar policy, sorting directions, saved preference keys and exact-token review/ack logic retained.
- Existing visible-header/task-ID viewport restoration and safe-idle reorder deferral retained. Emphasis now pauses on list touch/scroll and resumes only at rest.
- Authentication, durable request identity/retry/replay handling, canonical tasks, revisions and session storage unchanged.
- No backend/web edits by this child. Existing `IMPLEMENTATION-PROMPT.txt` and unrelated `qa/liquid-glass/IMPLEMENTATION.md` changes preserved. No commit or emulator install.
- Read all native reference documents and viewed all nine actual reference PNGs, plus parent's before-APK synthetic Tasks screenshot.

## Executed verification

- `android/build.sh` with SDK 35 / JDK 17 and supported external signer paths: **PASS**. Includes all existing JVM suites, controller/auth gates, **66 Python tests**, actual extracted route/own-change/ack/reload/quick-action regressions, full Java-to-DEX/resource compilation, v2/v3 signature verification and alignment.
- `android/tests/run_chat_presentation.py`: **PASS** (real ChatRowPolicy and ChatPresentationPolicy JVM checks plus 8 source-wiring tests).
- `android/tests/run_tasks_ui.py`: **PASS** (32 ledger checks, 37 task-presentation checks, 10 wiring/contrast contracts, actual SDK compilation and 9 anchor checks).
- New `test_owner_visuals.py`: observed RED for missing tint/rest/incoming/citation/IME/scope/phase features; GREEN after implementation. Also executes the actual extracted TaskRowView draw/scheduling methods with deterministic host Canvas/animator seams: seven external rings, bounded unclipped geometry, persistent static halo, stable shape/content bounds, stronger first arrival, continued rest through **25.6 seconds**, interaction/reduced-motion pause and exact-token arrival lifecycle. **These are not native pixel/frame tests.**
- Final-composite matrix covers all six hue slots, Project/Importance, both themes, all three priorities. Neutral primary/secondary and New update text >=4.5:1; category indicators >=3:1. It exposed a real 4.349:1 light high-Amber unread-label failure; Android unread light blue was darkened to `#005BC8`, leaving the base primary/project palette unchanged, and the full matrix then passed.
- `git diff --check`: PASS.

## Parent native QA handoff / not yet established by this child

Runtime install/captures/recordings belong to parent; compilation and host seams do NOT establish visual acceptance. Verify current APK hash before capture. In particular, record beyond 8 seconds (prefer 20+) with the list idle, then touch/scroll/background/reduced-motion and resume. Check first arrival is not replayed by poll/rebind/navigation and exact newer-token arrival still occurs. Check light/dark tints, density/200% text, moving capsules/dialog scrim, real admission/incoming/running/search phases, keyboard clipping, older-history anchor and folding on the actual APK. No new native runtime defect is asserted as resolved without those captures. Pre-API30 keyboard falls back to existing platform resize; physical Samsung frame quality is unverified.

### Halo pixel sampling

- View class remains **`com.simon.mytasks.TaskRowView`**; View tag is exact canonical task ID, content description includes New update.
- Read View's actual bounds and density. For unread rows, the filled surface/crisp edge is inset **8dp on every side**; the 7dp soft falloff occupies the reserved band approximately **1–7dp from the View bounds**, entirely outside that edge and inside the View's clipping bounds. At horizontal center, sample the top external band around local `y=4–6dp`, not title/row fill. Compare multiple frames at the same bounds after 8 seconds. Only two rows animate concurrently; other unread rows keep static emphasis and take fair scheduling turns.
- No software View layer, child RenderEffect, negative draw bounds or text opacity animation is used for the halo.

## Owned files

Source: AndroidManifest.xml; ChatRowPolicy.java, ConversationsUi.java, MainActivity.java, NativeSheets.java, PendingDots.java, PresentationPolicy.java, TaskRowView.java, TasksUi.java, UpdateEmphasis.java, WorkspaceUi.java.

Tests/notes: UpdateEmphasisTest.java, run_chat_presentation.py, run_tasks_ui.py, test_native_motion_methods.py, test_tasks_ui_contract.py; new UnreadHaloMethodsHarness.java, test_owner_visuals.py, this note.
