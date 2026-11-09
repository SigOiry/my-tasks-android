# Android Liquid Glass v3 — native preview

## Scope and identity

`1.5.0-glass-preview`, versionCode **7**, package `com.simon.mytasks`. Java/native Views, minimum API 26, SDK/target 35, direct `aapt2`/`javac`/D8 build. No WebView, flattened reference artwork, Compose scaffold, backend/schema migration or production deployment. Existing release certificate, Android Keystore identity/AAD, HTTPS authentication, canonical task store and durable conversation contracts are retained.

The implementation brief is `IMPLEMENTATION-PROMPT.txt` and installed `native-android-liquid-glass` 3.0.0. All nine boards were opened with vision before implementation. They are references, not runtime evidence. Native iteration started from installed 1.4.0-preview/code 6 (SHA-256 `3214947b8cb3c267c33ee85b11432868cd8ea8747e6f3a0e4309f73ff009209a`).

## Presentation architecture

- `WorkspaceUi`: pearl `#F5F5F7` / graphite `#0D0F14`, stable white/graphite reading surfaces, licensed Inter, 600-weight headings, restrained blue, separators and semantic spacing. Native actions retain at least 48dp targets.
- `GlassLayout`, `GlassDrawable`, `GlassPixels`: actual bounded decor-backdrop sampling and two-pass downsampled blur, translucent tint and directional rim. All glass subtrees are omitted during capture so foreground labels remain sharp. Reused pixel buffers are bounded to 65,536 samples per surface. Scroll trailing refresh and visibility/focus checks limit work; reduced transparency/high contrast use opaque surfaces. This is not Apple's private optical/refraction renderer, nor a claim about physical-device frame rate. SurfaceView/video, hardware-only content and other windows cannot be sampled by this software renderer.
- Glass is a control/navigation material, not a filter over paragraphs or the whole window. API31+ native window blur is optional on supported sheet/menu windows. The ordinary app material does not rely on that capability.
- Android owns status/navigation bars, Back, IME, selection tools, permissions and installation. No fake iOS OS chrome is drawn.

## Screen and state map

| Route | Implementation |
|---|---|
| Tasks | Recycled grouped `ListView`, 64dp ordinary row minimum, circular 48dp completion controls, independent priority/unread/selection states. Default title and scope chrome is approximately 102dp; project/sort/less-used scopes are in the utility menu. Selected project is the title; non-default sort has quiet context. Week/Trash remain visibly selected. |
| Adaptive navigation | Two global destinations only. Compact home uses one floating capsule and an aligned Add/New action. Compact details/edit/full chat hide global navigation. Unfolded uses a 72dp rail, 280–340dp master and readable detail pane. Width and font scale determine whether both panes are usable. |
| Accessibility | Text grows rather than shrinking targets; large task titles and unread context wrap. At 200% a fold-sized window switches to a usable single pane. Priority has filled/outline/no-flag distinctions as well as contrast-safe colors. Native labels, checked/selected states and assistant review accessibility actions remain available. |
| Details/edit | Grouped reading surfaces, compact value rows, all editable fields, native date picker/clear and one relevant bottom action area. Save/Cancel remain outside the form scroll above IME. Focused editor fields request sufficient visible space after resize. Revisions and dirty draft confirmation stay authoritative. |
| Update review | Immutable displayed snapshot/token, restricted inert Markdown, supplied Context/What's new/Next step only. **Mark reviewed** alone acknowledges. Open task, Ask Hermes and dismissal keep unread. Newer-token conflict retains review and offers refresh. |
| Context actions | Bounded content-sized native menus, row anchoring when identity is unambiguous, safe compact fallback, status/priority/project/edit/recoverable Trash actions and focus restoration. No UI-only mutation queue. |
| Discussions/chat | Compact list, loaded-metadata search, scopes, saved task/free threads, rename/archive/reopen/history. One thread header and one multiline sampled-glass composer. Stable message views are retained; only new local insertions near the end animate. ID/offset anchors, older history, drafts and originating request/thread identity remain independent of navigation. |
| Manual planning review | Assistant text offers **Review as new task** through context/selection/accessibility actions. It opens an explicitly unsaved DRAFT editor with blank required title and exact editable description, after any dirty confirmation. Only a valid explicit Create uses the existing canonical task API; project remains a task attribute. No invented proposal schema or model tools. |
| Utilities | Grouped Settings, system/light/dark, reduced motion/transparency, Android notification controls, account/privacy/update utilities and native sign-in. No passwords are persisted. |

## Verification and data boundary

Native QA uses the local Linux **Pixel_Fold**, Android 17/API37, measured **411×797dp folded** and **841×701dp unfolded** at normal text. An isolated `com.simon.mytasks.qa` build uses the same presentation sources but separate identity, private loopback TLS trust and synthetic account. The normal package's real account was inspected read-only and upgraded in place, not logged out or cleared.

The fixture uses the real canonical integration, authentication, revisions, unread identities and durable workers. Only the model boundary is synthetic; its text is labeled **SYNTHETIC QA**. Real task/mail/calendar data is never a fixture. Linux SSH forwarding is explicitly prohibited; the backend runs in an isolated host-local venv on `127.0.0.1:9147`, reached through the specifically owned ADB reverse. No forwarding restriction was bypassed or LAN service installed.

Actual native checks include Tasks chrome/targets, project/date/Week/Trash scopes, light/dark/reduced-transparency/offline rendering, saved task/free chat, IME-open compact/unfolded layouts, multiline drafts and selected thread/task across folding, older-page loading, a pending reply completing in its original thread, durable provider failure, native all-field edits and stale revision retention. A genuine newer canonical update during an open review was rejected for the old token; the refreshed exact token cleared only that update. API readbacks verify mutations, acknowledgements, history and explicit draft-to-task creation.

Commands (run against final source):

```sh
JAVA_HOME=/path/to/jdk17 bash android/test.sh
python android/tests/run_tasks_ui.py
JAVA_HOME=/path/to/jdk17 python android/tests/run_details_v3.py
JAVA_HOME=/path/to/jdk17 python android/tests/run_conversation_review_regressions.py
python -W error::ResourceWarning -m unittest discover -s qa/liquid-glass -p 'test_*.py' -v
# Existing authorized signer supplied privately:
bash android/build.sh
```

`qa/liquid-glass/README.md` documents the reproducible isolated service/build/native test workflow. Local `qa/liquid-glass/evidence/` is gitignored because it also contains the private real-account baseline. Deliver only explicitly selected synthetic screenshots/recordings and safe test reports, never that baseline. Full-display `*-display.png` files come from Android `screencap`; the additional `.png` files are native Window PixelCopy, and JSON records actual View/state measurements. Neither is generated preview art.

Final native iteration also reproduced and fixed expanded Search losing an unsaved editor draft, verified 200% long task titles without ellipsis, and moved native sheet/menu bounds before Window attachment to remove the observed narrow-first-frame flash. The final QA build repeated compact/unfolded density, Search draft retention, large text, compact editor, scopes/dark/reduced effects/offline and sign-out/sign-in checks. `native-compact-final.mp4` and `native-fold-final.mp4` are actual host ADB recordings of that candidate; the square fold encoder canvas is letterboxed, not a square app layout. These recordings do not measure UI frame performance. Final production installation is verified by hashing `pm path`'s actual APK, and the original authenticated workspace remains available. No production task/message mutation was performed. The owned fixture worker and only its `tcp:9147` reverse are stopped after readback; synthetic data remains available for a deliberate restart.

## Remaining limits

Physical Samsung/One UI, spoken TalkBack walkthrough, recognizer audio processing, background notification scheduling, updater installer consent, battery cost and physical-device frame timing are separate checks. Emulator recordings and SDK/JVM tests do not establish those. Large text/native semantic targets are checked separately from a spoken screen-reader session. Cold-start offline access still requires the existing session verification contract; an already verified open workspace retains read-only cached tasks. There is no hidden offline mutation replay, unrestricted model execution, automatic project database or reconstructed old stateless history.

This is a local preview delivery, not a stable-release promotion or authorization to publish source, APKs or private captures.
