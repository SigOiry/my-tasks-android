# Android native redesign — local preview

## Status and scope

Opt-in `1.4.0-preview` / versionCode `6`. Native Java Views, minimum API26, target/API35; the existing direct SDK build is retained. No WebView, Compose/Gradle migration, backend/schema change or production deployment is included. Preview distribution was separately requested after the local redesign delivery and does not promote the stable release. The package, signing certificate, Keystore alias/AAD, authentication, task revisions and durable conversation protocol remain unchanged.

The local APK is `android/build/my-tasks.apk`. Install over the existing signed app; do not uninstall or reset app storage. This document does **not** certify native launch or upgrade behavior.

## Assessment and rendering decision

The baseline already had explicit read-only details/editing, update prose and saved conversations. The actual UI lacked separate row completion/context actions, used a conventional update dialog and rebuilt message content on unrelated request-state changes. The implementation therefore keeps Views and improves the native composition and state boundaries rather than rebuilding the backend or introducing another UI framework.

Material: pearl/cool-neutral canvas and solid white content; graphite canvas/content in dark mode; restrained blue, licensed Inter, quiet separators, concentric rounded controls and shallow elevation. `WorkspaceUi` defines semantic color, type, spacing, radius, elevation and motion tokens. Settings retains system/light/dark, reduced motion and reduced transparency.

**Glass is a translucent-fill/edge-highlight approximation, not backdrop sampling, blur or optical refraction.** Readable content and messages remain opaque. No RenderEffect is installed on text/control children. A custom capture pipeline was not introduced without a native runtime to validate capture cost, recursion, scrolling or IME behavior. Reduced transparency/high-text-contrast use opaque fills. Kyant0/AndroidLiquidGlass was not adopted because this is not a Compose implementation.

References studied for hierarchy/material inspiration, not copied assets or implementation prompts:
- https://liquidglassdesign.com
- https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass
- Apple's documentation data: https://developer.apple.com/tutorials/data/documentation/technologyoverviews/adopting-liquid-glass.json
- https://developer.android.com/reference/android/graphics/RenderEffect

The product skill and its `materials-motion.md`/`hermes-flows.md` references and `interaction-design` were inspected. `ui-ux-pro-max` is not installed. Compose-only design/performance skills were not applied to Views.

## Component and state map

| Component | Implementation and authority |
|---|---|
| Tasks | `TasksUi` recycles native ListView rows. Existing filters/search/sorts remain presentation-only. Visible task-ID/top-offset anchors survive root rebuilds; filter changes explicitly reset the anchor. Filter/project/sort preferences use the existing epoch-scoped account preferences. |
| Task row | `TaskRowView`: 72dp minimum, two-line title and subordinate context; content can increase row height. Separate native 48dp completion and overflow targets. Long press/overflow route to the same contextual sheet. Completion waits for server-confirmed state and never acknowledges an update. |
| Update accent | `UpdateEmphasis` tracks exact task/token pairs for this Activity session, limits two simultaneous accent-only breaths, two 2300ms cycles, then a static marker. Navigation/rebinding does not replay observed identities. Detach/background/reduced motion stop animated emphasis without changing unread state. |
| Update review | `UpdateSheet` is bottom-anchored on compact windows and centered on wider ones, with scrollable opaque prose and acknowledge/details/discuss actions. `UpdateReviewPolicy.Snapshot` owns immutable displayed strings/token. Missing context/advice is omitted; raw legacy JSON/diffs and source actions are not shown. |
| Acknowledgement | Only the displayed token is posted after explicit action. Failure retains review context. A newer observed unread task cannot be overwritten by a late old acknowledgement reply, and its OS notification is not cancelled. Rotation/theme replacement retains the original displayed snapshot. |
| Foreground updates | One quiet GET at a time, checked every30s while active. `UiReadGate` fences old reads when paused, a write starts or the session changes. Unchanged snapshots do not rebuild the root. Open editors keep their fields and original revision; changed remote revisions get a preserved-draft notice. This is polling, not instant push. |
| Details/editor | Title, readable metadata, prominent saved-task conversation entry, explicit Edit, description/notes, supplied safe source actions and available timestamps. The API does not expose historical update prose; the UI says so rather than inventing an activity feed. Save/Cancel and all existing editable fields/revision checks remain. |
| Context actions | Native sheets provide supported status/priority/project/edit/recoverable-trash/restore actions. `TaskActionPolicy` sends only the requested field and exact revision. A dirty editor for the same task must be saved/cancelled before a quick action. Ambiguous writes are not automatically retried. |
| Messaging | `ConversationsUi` renders opaque left/right grouped bubbles, actual timestamps/date separators, selectable inert Markdown and Copy original. Message history changes, not request-only polls, trigger history reconciliation. Per-thread ID/offset anchors, jump-to-latest and own-request following preserve reading position. |
| Discussions | Saved titles, excerpts, timestamps, filters, rename/archive/reopen and New conversation. Search explicitly covers loaded metadata, not full message history. Free chat discusses/drafts; the existing no-tools integration cannot apply task/project proposals, send mail or execute calendar actions. |
| Navigation/insets | Two labelled floating destinations, utility Settings, current-width list/detail layout and keyboard-sensitive chrome. Measured navigation height adjusts Tasks/FAB/editor clearance when text grows; the composer reserves navigation space until IME takes precedence. |

## Verification performed

The parent performed complete resource linking and full-source Java8/API35 compilation, then the repository's signed APK build using the existing authorized signing identity. The normal host test runner includes new review/quick-action/read-gate/emphasis/chat policy tests, plus executable extracted-method regressions covering reload, clean-editor quick actions, delayed acknowledgement versus replacement OS notifications, self-edit/remote-token interleaving and cross-task conversation navigation. Separate Tasks checks exercise the ID/offset anchor and isolated module compilation. Source-wiring checks and extracted-method host seams are explicitly **not** Android interaction tests.

OS task notifications now use exact epoch/task/token identities. Posting supersedes older versioned notifications for the same account/task; acknowledgement cancels only its displayed identity. Pre-preview shared-tag notifications have no trustworthy epoch/token and may require manual dismissal after upgrade. They are not cancelled speculatively by acknowledgement.

Available checks passed: **486 JVM assertions**, **28 Python source-contract checks**, **5 extracted-method host regression groups**, and **9 additional task-anchor checks**, plus complete resource/Java/D8 compilation and original-certificate APK signature verification. Final independent source re-review found no blocking security or logic issues; its recorded production hashes match the delivered source snapshot. This is approval of the local preview source, not native runtime validation or authorization to publish.

Concrete final counts, APK/source hashes and review results are recorded in `android/build/redesign-verification.json`. The final build log is retained locally, not as native UI evidence.

## Native runtime gates still open

ADB lists no connected device; the active emulator configuration lists no AVD, and its acceleration check reports missing `/dev/kvm`. No before/after native screenshots, motion recording or native visual iteration has been produced. Reference-gallery images are not app screenshots.

The following remain **unverified on Android**, even where source paths/pure policy tests exist:
- Light/dark rendering at360dp/412dp and expanded windows; rotation/fold and long task/project names.
- Text through200%, actual48dp touch targets, TalkBack traversal/focus restoration, system/gesture bars.
- Keyboard-open composer and short-window/inset behavior; offline/loading/empty/error visuals.
- Actual update-sheet interaction during arrival/acknowledgement, native edit conflicts and thread switching mid-response.
- Physical install-over-existing, Android Keystore, recognizer, notifications and installer behavior.
- Release-like frame timing, scrolling/typing responsiveness and battery cost. Task rows are virtualized, but loaded chat histories still reconstruct their message views when real message content changes; large histories require device profiling. No frame-rate or jank improvement is claimed from a host build.

Use synthetic tasks/threads for those checks. Never use production transcripts or canonical tasks as fixtures. Quiet-read generation invalidation prevents stale cache/UI application but does not cancel already queued/running network work; repeated pause/resume can leave obsolete work to finish on the serial executor. Request-queue cancellation/occupancy optimization remains a nonblocking follow-up. Do not publish/deploy this preview until separately authorized.
