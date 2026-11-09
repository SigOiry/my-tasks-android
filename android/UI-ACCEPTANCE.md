# Native workspace redesign — 1.2.0 (3)

## Delivered composition

This is an Operate workspace, with a secondary Command/Inspect task surface. It uses the exact light/dark neutral and forest tokens in the shared DESIGN.md, purposeful native sans typography, 4/8/12/16/24 spacing, 8dp containment, and explicit action hierarchy. Native theme resources also color pickers, selection and dialog controls. No gradient, emoji, fabricated metrics, demo tasks, provider JSON or evidence dump is rendered.

- Compact app bar: 24sp title plus two 48dp labelled vector-icon actions; connection/sync state is separate. Active/Today/Week/All/Trash selection has text, state and forest containment; the selected tab is scrolled into view.
- Task workspace: 22sp view heading, truthful filtered task/unread count, primary New, accessible search, project filter and sort menus. Project/priority/deadline grouping has restrained 14sp headings; rows use 16sp titles and explicit priority, status, readable local due and unread labels. Large font scales stack badges rather than clipping them horizontally.
- Task inspector: Details and Hermes are distinct selected modes. A readable exact-token unread summary precedes task content and editing. Details start with task title/status/date, readable description and source/account context. Only actual supplied HTTPS links passing the URL policy are offered; internal source IDs never become invented provider links. Opening is explicit and confirmed by host.
- Editing: consistently labelled 48dp-minimum native inputs and choices, optional date with native picker and clear action, primary Save, secondary Complete/Cancel and separated destructive Trash. Drafts remain in memory across mode/theme/window changes. Stale saves preserve the draft; there is no force-overwrite action.
- Hermes: task identity stays visible, speaker-labelled readable 16sp turns, selectable assistant text, explicit Copy response/draft, question/email-draft mode and primary Send. The unchanged platform-recognizer privacy/review path never auto-sends text or mail.
- Settings: System/Light/Dark, installed version and independent manual updater first, accurate Android permission/app/channel notification state, best-effort scheduling wording, encrypted connection settings and voice privacy. New unconfigured setup suggests https://task.oirysimon.com; saved custom origins remain intact.
- At current window width >=720dp, list and inspector share the workspace; below 720dp, one pane is focused. No named Fold model's physical dimensions are assumed. Current system/cutout/IME insets and adjustResize are handled. Selection, filters, drafts, conversation and busy request state survive Activity configuration changes; saved instance state restores ordinary process recreation.

### Reviewed UI corrections

- Compact list: `res/layout/task_list.xml` supplies one full-height vertical ScrollView. Its single vertical content contains app bar + connection banner, heading/New, horizontal view chips, accessible search, project/sort controls, then task rows. The header views are moved into that content by `buildList`; there is no fixed-height chrome above this compact list viewport and no inner vertical task scroller. Existing 48dp control minima remain. Insets stay on the outer root, outside the scrolling content. Search and rows can therefore share the remaining viewport rather than controls taking its entire height. The horizontal chip scroller remains horizontal only.
- Expanded (`layout-w720dp`): preserve fixed app bar/banner and list controls, independent row scrolling, and the existing side-by-side inspector. Existing `listY` capture/save/restore and filter/query fields are retained; scroll restore captures the intended offset before posting. Actual resize/IME scrolling and restoration are still device acceptance checks, not claimed runtime results.
- Project options now use the same pure current-view inclusion policy as task rows (without current project or search narrowing). Exact `Work` and `work` identities remain distinct; options sort case-insensitively with raw-string tie breaking. Trash offers `deletedOnly` when only deleted tasks have that project; ordinary views do not offer deleted-only names. A stale selected project is not injected into another view's options; All projects remains the explicit empty-filter reset and an empty result also retains Clear filters.

## Executed evidence

Command (from android/):

```sh
JAVA_HOME=/opt/data/cache/android-build/jdk-17.0.2 \
ANDROID_HOME=/opt/data/cache/android-build/sdk bash build.sh
```

- 233 JVM assertions passed: original Core/updater 164 plus presentation 69. Six Python tests passed: four release-metadata and two parsed layout-topology tests. Project-policy RED/GREEN runs first failed for missing enumeration and then for Trash-only project exclusion; GREEN verifies distinct Work/work, selecting each exact filter, empty-filter reset, deletedOnly inclusion/exclusion, terminal/due-window membership and stable tie sorting. Layout RED failed for missing scroll-safe resources; GREEN parses the actual compact/expanded XML topology, checking a single compact scroll ancestor for header/controls/rows and the preserved expanded split. These are structural tests, not measured Android layout, touch, focus or accessibility tests. Earlier date/source/ack regressions remain passing.
- SDK35 aapt2, javac/D8, zipalign and signing completed. Native APK reports com.simon.mytasks, versionName 1.2.0, versionCode 3, minSdk26, targetSdk35. There is no added app-library dependency.
- APK v2/v3 signatures verify with the original pre-redesign signer certificate SHA256 `a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da`. No signing material was regenerated or exported. Updater implementation/provider and lifecycle/result integration are preserved.
- APK entries contain no signing/password/cache/test fixture payloads. No live task mutation or fixture task was created. Server, publication snapshots and GitHub were not modified.
- Text-token contrast calculations: light body/surface 15.90:1, muted/canvas 4.65:1, primary/soft 6.07:1, primary-button text 6.75:1; dark equivalents 13.76:1, 8.54:1, 7.49:1 and 10.04:1. These are token calculations, not a rendered accessibility audit.

Artifact: `build/my-tasks.apk` (144492 bytes)

SHA256 of the final published APK is recorded in the release's `update.json` and `SHA256SUMS`. Local rebuilds may differ from the implementation-stage evidence artifact.

Build output: `build/build-v1.2.0.log`

## Physical-device test matrix — NOT executed

`adb devices -l` returns no device, and /dev/kvm is absent. No native screenshot, launch, installation, foldable, notification, recognition, network or updater runtime result is claimed. A compiled native view hierarchy is not a visual review. These checks remain release acceptance work on real devices, using a separate scratch task server/database for writes:

| Area | Required checks |
| --- | --- |
| Windows/layout | Compact cover-sized window, ordinary phone, split-screen at 719/720dp and wider, landscape, actual supported foldable; no inferred Fold8 dimensions. Inspect real populated/empty/loading/error screenshots in light and dark. |
| Accessibility | TalkBack labels/selection, keyboard/D-pad focus, 48dp actions, 100/150/200% fonts, long task/project titles, RTL, contrast in real controls. Check badges, horizontally scrollable views, app bar and dialog action wrapping. |
| Keyboard/insets | Gesture and three-button navigation, cutout, keyboard open while editing/task chat, native date picker, scroll to primary actions, compact landscape. In a short compact list window with search IME open, scroll from app bar through search/filters to final task, dismiss IME and scroll back; no stranded search/row, nested vertical gesture trap or shrunken action targets. |
| State | Resize/rotate/switch themes during unsaved edits and in-flight chat/save/read acknowledgement; preserve fields, selection and chat. Cancel navigation leaves data; discard is explicit. Process recreation restores ordinary draft state; interrupted chat never silently resends. |
| Task views | Active, Today including overdue, local week through Sunday, All including terminal states, Trash excluding ordinary tasks; null dates/project, all projects, case-insensitive search and each sort. Use Work/work and deletedOnly fixtures on the scratch server; select each exact name, switch Trash/normal views, reset stale filters with All projects/Clear filters. Confirm no stored priority/date is altered by sorting. |
| Task writes | Scratch create/edit/save/complete/trash/cancel/restore; read back exact changed task. External concurrent edit ->409 preserves draft and requires reviewed reload, never force overwrite. Offline cache disables writes/chat. |
| Unread | Summary appears before ordinary content/editor. Opening/notification delivery does not ack. Ack sends exact displayed token; concurrent update ->409 keeps summary/draft, Refresh summary loads the newer version, and only a subsequent explicit ack clears that version. |
| Source | Human source/accounts without provider IDs/JSON. Supplied HTTPS link opens only after confirmation in a browser; no account token is attached. Missing source/browser and unsafe link schemes fail safely. |
| Hermes/voice | Task context uses saved fields; Details/Hermes mode switching preserves unsaved task/chat text. Real provider response/copy/email draft, 413 changed-input handling, timeout warning. Privacy notice before recognizer; editable returned transcript; no recognizer graceful error; no automatic mail/text send. |
| Settings | Fresh no-account canonical-origin suggestion; saved custom server unchanged; invalid origin/token rejected; account change clears cache/drafts. Theme persists. Permission/app/channel disabled states accurate after returning from Android settings. |
| Updates | Manual Check for updates with no task account, offline, blocked task server, and long Hermes request; updater remains independent. Repeat full updater matrix in TESTING.md for consent/permission/provider/system installer, matching signer upgrade and rejected downgrade. |

Known limits: source context intentionally omits arbitrary raw evidence; assistant responses are faithful selectable plain text, not a Markdown/HTML renderer. Long lists use the existing dependency-free ScrollView rather than a recycled list, so very large datasets require on-device performance measurement. Clipboard copies are explicitly user-initiated. Local release metadata was regenerated from this signed APK with the expected signer enforced; publication/deployment remain parent-owned and were not performed. Build emits existing deprecated-API notes; no runtime device is available.
