# My Tasks

A private task workspace for native Android and the web, sharing one canonical task store. Compact task lists, durable conversations with Hermès, and a Liquid Glass-inspired interface with blue accents, licensed Inter typography and restrained motion.

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
- Hermès questions, analysis and email drafting, with **no agent tools, email sending or task execution**. This is not an unrestricted gateway into terminal/dashboard agent sessions.
- Optional reviewable speech transcription where the platform supports it; no automatic submission of dictation.
- Light/dark themes, reduced-motion/transparency controls and width-adaptive compact/expanded layouts. Glass-inspired controls are distinct from solid readable content. This is not native iOS optical-refraction rendering.

Previously discarded stateless chats cannot be reconstructed. Unsent browser drafts are page-memory only; durable history starts with accepted messages, not unsubmitted text. Discussion search filters the loaded metadata; use pagination to load more.

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
