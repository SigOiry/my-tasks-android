# My Tasks

A private task workspace for native Android and the web, sharing one canonical task store. Compact task lists, durable conversations with Hermès, and a Liquid Glass-inspired interface with blue accents, licensed Inter typography and restrained motion.

## Android reply workspace polish — 1.6.3

Version **1.6.3 / code 13** fixes reply-composer keyboard interruptions and clarifies the native writing flow. Email and AI assistant panes keep their live inputs through autosave, AI polling and size-only fold/rotation changes. Draft, Improve and Ask have explicit actions; suggestions are readable previews with Apply to draft and Compare drafts. Short windows and 200% text give input priority above the keyboard. Review waits for saved writing, stale previews cannot replace newer edits, and sent results are read-only receipts rather than raw provider data.

This is an Android-only follow-up: the shared backend and web presentation are unchanged. Pixel Fold emulator acceptance, injected nonzero-hinge geometry checks and verification limits are documented in `qa/email-polish-20261111/REPORT.md`.

## Contextual email replies and conversations — 1.6.2

Version **1.6.2 / code 12** adds a shared, persistent email reply workspace to Android and web, without redesigning unrelated task screens.

- Reply from an email-driven update or task details; opening the composer does not acknowledge the update. Exact reviewed origins are snapshot-bound. Historical or multiple sources require an explicit message choice, with account, sender, subject, date and excerpt.
- Server-authoritative drafts autosave with revisions and restore after closing. Reply and Reply all preserve the selected account, original subject and thread headers. A separate exact-message review and explicit confirmation are required to send; ambiguous delivery is reconciled without automatic resend.
- **Draft with AI**, **Improve writing** and **Ask AI** use only bounded relevant context and no tools. Suggestions are editable previews; explicit Apply is revision-checked so newer human writing wins. The draft-bound AI discussion persists independently of its panel.
- Narrow displays use a full-height editor/assistant switch; larger windows support an optional split workspace. Native layouts preserve editing state across configuration changes and honor available insets and reported occlusion.
- Chat sources retain immutable original text plus durable provenance, compact source chips, authenticated email context and visible unavailable/failure notices. Asynchronous subject titles have a bounded update cadence; manual renaming is permanent.

Architecture and acceptance boundaries: `docs/v1.6.2-reply-workspace-design.md`, `docs/v1.6.2-api-contract.md`, and `qa/v1.6.2/`. Native evidence uses the Pixel Fold emulator, not a physical Samsung Fold8. Test email transports are synthetic; no real email is sent during development.

## Android and shared chat 1.6.1

Version **1.6.1 / code 11** removes the task rail crossing completion checkboxes and makes project surfaces and task-hue external unread halos substantially more visible. Text/cue contrast and reduced motion are retained.

The independently reviewed shared backend routes ordinary English/French public factual questions to bounded research and reads eligible public URLs directly. Recognized writing and transformations (including indirect/gerund forms), explicit no-browse, greetings, credentials and summaries stay tool-free. Current-request privacy, pinned-network safeguards, saved sources and durable replay are preserved. Web presentation and the original account/signing identity are unchanged. Verification, release assets and explicit emulator-versus-physical-device limits: `qa/chat-visual-fix-20261110/REPORT.md`.

## Android 1.6

Version **1.6 / code 10** packages the owner continuation under tag `v1.6`, with the original signing identity and standard updater assets. It adds Android-only project-hue/importance tints, sustained unread halo breathing, native navigation/dialog/chat/IME motion, and waiting/inactive lanes in all ordering modes. The web UI presentation is unchanged. The shared durable-chat backend now offers bounded read-only public search/page retrieval with saved sources and actual search/read progress; it is not an unrestricted agent.

Release verification: `qa/releases/v1.6/README.md`. The preceding 1.5.1/code9 native screenshots/recordings, deployed-provider proof and explicitly incomplete combined runtime checks are in `qa/android-web-access-20261110/REPORT.md`. The Android presentation is unchanged from that checkpoint; v1.6 also fixes reviewed backend query-isolation/context-budget defects. Publication does not imply that the remaining native runtime checks were completed.

## Android and web 1.5

Version `1.5` (Android code 8, GitHub tag `v1.5`) extends the native Liquid Glass redesign with shared muted task colors, persisted Smart/traditional ordering and coordinated motion. Tasks, details/editing, update review, messaging and utilities use compact native Views and real bounded sampled/blurred glass controls, deliberate folded/unfolded layouts, IME-safe composition and explicit assistant-text-to-task review. Official build output: `android/build/my-tasks.apk`, signed with the original external release identity. See `CHANGELOG.md`, `android/REDESIGN.md` and `server/WEB-PRESENTATION.md` for verification and known limits, including the web's current Europe/Rome calendar. Native synthetic QA evidence is separate from the official release and is not a live-model invocation.

## Open or install

- **Browser workspace:** https://task.oirysimon.com
- **Android APK:** `my-tasks.apk` in the latest stable GitHub Release.
- **Android server:** `https://task.oirysimon.com` is suggested for new setup. Saved custom server addresses are preserved.

Install an update **over the existing app without uninstalling**. Official releases retain the original package and signing identity. Version1.0.0 needs one manual upgrade; updater-enabled versions check once per local day at first foreground launch and manually from Settings. Download and Android installation always require consent.

## Sign in

Use your username and password, not a pasted service token. First activation requires an already trusted configured device/browser or a privately supplied, one-time activation link. The initial session cannot access tasks or chats until the owner replaces the provisional password. A guessable initial password alone cannot take over the public account.

The application never persists your password. The browser uses an opaque HttpOnly/Secure/Strict cookie; Android encrypts its issued session with the existing Android Keystore identity. Browser/bootstrap sessions last12 hours; full native sessions remember the device for30 days, unless revoked earlier by logout or password replacement. The updater works independently of account sign-in.

Server operators must provision the private owner account **before** starting the new backend; see `server/AUTH-v1.3.md`. Never enable the uninitialized legacy fixture override in production, publish activation links, or reset authentication by deleting the account database.

## Features

- Compact Active/Today/Week/All/Trash views, project/category filters, search, priority/deadline sorting and unread indicators.
- Create/edit/complete, notes, reliable source links in ordinary task details, and confirmation-backed recoverable trash/restore.
- Optimistic revisions preserve unsaved edits when a task changes elsewhere.
- Prose-first update summaries: **Context**, **New**, and optional **Suggested next steps**. Safe Markdown supports emphasis, lists and code without raw HTML, active source links or images. Acknowledgement applies only to the exact displayed update; conflicts require reviewing the newer summary.
- Multiple saved conversations per task, plus a **Discussions** destination for task-bound and free chats. Select, rename, archive/reopen and load older messages. Sent history is stored privately on the server and shared by web and Android.
- Durable, idempotent message admission: a response can finish after leaving its screen; reopening recovers request status and saved replies. Failed/interrupted requests are visible and never automatically replayed. Long model contexts use persisted rolling summaries while original messages remain available.
- Hermès questions, analysis and email drafting, with occasional **server-controlled public search and read-only page retrieval**. No terminal/file/email-send/task/calendar mutation tools, and no unrestricted terminal/dashboard agent session. Explicit no-browse requests and summaries stay tool-free; saved private context is excluded from research.
- Optional reviewable speech transcription where the platform supports it; no automatic submission of dictation.
- Light/dark themes, reduced-motion/transparency controls and width-adaptive compact/expanded layouts. Glass-inspired controls are distinct from solid readable content. This is not native iOS optical-refraction rendering.

Previously discarded stateless chats cannot be reconstructed. Unsent ordinary chat inputs are page-memory only; durable history starts with accepted messages, not unsubmitted text. Email reply drafts are separately server-persisted, with recoverable local writing during save failures. Discussion search filters the loaded metadata; use pagination to load more.

### Platform differences

| Capability | Browser | Android |
| --- | --- | --- |
| Task alerts | Polling while the page is open; no closed-browser push | Best-effort periodic background checks, potentially delayed by Doze/vendor policies |
| Offline reads | Current page memory; offline reload cannot restore tasks | Private task cache after online account verification; cold launch requires verification |
| Session storage | HttpOnly cookie; no bearer/password in browser storage | Keystore-encrypted opaque session; backup disabled |
| Presentation | Responsive glass-inspired web controls | Native grouped Views and bounded backdrop glass; the OS owns bars, IME and notification shade |
| Updates | New bundle on deployment/reload | Verified APK download and user-confirmed Android installer |

Presentation, alert delivery and chat do not change stored task priorities/deadlines or mailbox read flags. There is no hidden offline mutation queue.

## Source and deployment

- `android/`: native Java application, resources, updater, metadata generator and tests.
- `server/`: browser source/bundle, task adapter, password/session auth, conversation worker/store and scratch QA tooling.
- `DESIGN.md`: shared interaction and visual direction.
- `RELEASING.md`: verified source/tag/binary publication checklist.

The backend integrates with an existing compatible Hermes My Tasks Store/dashboard plugin; it does not introduce another task database. Account, conversation, event and session metadata use dedicated private persistent files. See `server/README.md`, `server/AUTH-v1.3.md`, `server/CONTRACT.md` and `server/WEB-v1.3.md`. Generic compose examples contain no credentials or private runtime data.

## Build Android

Install JDK17 and Android SDK platform35/build-tools35.0.0:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
bash android/test.sh
# Supply the existing authorized signing identity privately:
export SIGNING_KEYSTORE=/private/path/release.p12
export SIGNING_PASSWORD_FILE=/private/path/password
export SIGNING_ALIAS=mytasks
bash android/build.sh
python3 android/release_metadata.py android/build/my-tasks.apk \
  --expected-certificate a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da
```

The build refuses to create or replace the release identity. Do not commit keys/passwords or upload them to Actions. CI checks native-independent policies, browser behavior and reproducible web assets; it has no official APK signing key. Full adapter integration additionally requires the compatible installed canonical plugin and is exercised with isolated synthetic stores.

## Verification boundary

Tests cover native-independent policies/HTTP clients, account security, durable history/recovery, revisions and exact acknowledgements, browser flows, APK integrity/package/signature and independent code review. QA writes use synthetic scratch data, not production obligations. See component testing documents for actual execution evidence and limits.

**Physical Android launch/upgrade, Keystore, fold/unfold/IME, voice, notifications and installer behavior remain separate device-QA requirements.** Compilation, browser screenshots and JVM tests do not prove native runtime appearance or behavior. No passwords, private activation links, signing material, canonical tasks, mailbox/calendar content or runtime databases belong in this repository.
