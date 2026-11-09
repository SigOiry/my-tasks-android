# My Tasks

A private task workspace for the web and native Android, with a shared warm-neutral/forest design system, project/category organization, safe task editing, update summaries and task-linked Hermes assistance.

## Open or install

- **Browser workspace:** https://task.oirysimon.com
- **Android APK:** download `my-tasks.apk` from the latest stable GitHub Release.
- **Android server address:** `https://task.oirysimon.com` (suggested for new setup; saved custom servers are preserved).

Use your private access token to sign in or pair the Android app. No token is embedded in this source, public HTML or APK. Version1.0.0 has no updater: install the latest updater-enabled APK once over the existing app **without uninstalling**. Subsequent Android releases can be offered in-app and always require user/Android installation confirmation.

## Features

- Active/Today/Week/All/Trash views, project/category filters, search, priority/deadline sorting and unread badges.
- Create/edit/complete, notes, trustworthy supplied source links, confirmation-backed recoverable deletion and restore.
- Optimistic revisions preserve unsaved edits when a task changes elsewhere. Updates show a readable summary and acknowledge only the exact version explicitly reviewed.
- Task-linked Hermes questions and email drafts; **no email sending or unrestricted agent execution**.
- Optional reviewed speech transcription, with platform/browser availability and privacy notice.
- Coordinated light/dark themes and compact/expanded layouts. Android adapts to window width, not guessed foldable-device dimensions.
- Android updater: first foreground launch per local calendar day plus Settings → Check for updates; latest stable release, integer versionCode, bounded HTTPS download, size/SHA-256 and actual package/version/current-signer checks.

### Platform differences

| Capability | Browser | Android |
| --- | --- | --- |
| Task alerts | Foreground polling while page is open; no closed-browser push | Best-effort background jobs, at least15 minutes apart and potentially delayed further |
| Offline reads | Current page's memory; offline reload cannot restore tasks | App-private persisted read-only task cache |
| Credentials | Token used for a bounded opaque HttpOnly/Secure/Strict session; no bearer token in browser storage | Android Keystore-encrypted token; backup disabled |
| Updates | New web bundle on reload/deployment | User-approved APK update check/download and Android installer |

Sorting and notification delivery never alter task priority, deadline, status or mailbox read flags. No hidden offline mutation queue is used.

## Source layout

- `android/`: dependency-free native Java app, resources, updater, release-metadata generator and tests.
- `server/`: browser source/built bundle, authenticated API adapter, session handling and isolated synthetic QA tooling.
- `DESIGN.md`: shared interaction and visual direction.
- `RELEASING.md`: source/tag/APK publication checklist.

The companion server integrates with an existing compatible Hermes My Tasks canonical Store/dashboard plugin. It does not initialize a second task database. See `server/README.md` and `server/WEB.md` for integration prerequisites, security and deployment configuration. `compose.example.yaml` is generic; private deployment environment/state are not published.

## Android build

Install JDK17 and Android SDK platform35/build-tools35.0.0:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
bash android/test.sh
# Supply an EXISTING authorized signing identity privately:
export SIGNING_KEYSTORE=/private/path/release.p12
export SIGNING_PASSWORD_FILE=/private/path/password
export SIGNING_ALIAS=mytasks
bash android/build.sh
python3 android/release_metadata.py android/build/my-tasks.apk \
  --expected-certificate a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da
```

The build refuses to create or replace a release identity. A differently signed build cannot update the official app. Never commit keys/passwords or upload them to GitHub. CI runs policy tests only and has no official release-signing key.

## Verification boundary

The project has real JVM/Python/JavaScript tests, signed APK packaging/signature checks, browser interaction tests and independent source review. Browser flows are exercised against an isolated synthetic database, not by creating production obligations. See each component's testing documentation.

**Android physical-device appearance, fold/unfold/IME behavior, microphone recognition, notifications and the package-installer self-update flow remain device-QA requirements.** Compilation and pure-policy tests do not prove native runtime behavior. Browser microphone availability also depends on the browser/device. No credentials, canonical task data, mailbox/calendar content, runtime databases or private screenshots are included in this repository.
