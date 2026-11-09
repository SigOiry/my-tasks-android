# Changelog

## 1.2.0

- Browser workspace at `https://task.oirysimon.com`, sharing the canonical task store and API with Android.
- Coordinated warm-neutral/forest design system, light/dark appearance, desktop navigation and compact/expanded native Android layouts.
- Project/category filters, search, priority/deadline order, notes, completion, confirmation-backed trash/restore and reliable source links.
- Readable unread summaries with exact-version acknowledgement; stale task edits and stale acknowledgements preserve unsaved/unseen information.
- Browser task-linked Hermes questions/email drafts, explicit reviewed voice input, privacy-preserving logout and foreground task alerts.
- Opaque HttpOnly/Secure/Strict browser sessions, absolute expiry/revocation, Origin/CSRF protections, duplicate-header rejection, safe malformed-header handling and strict self-hosted CSP.
- Embedded Android updater: daily first-foreground and manual Settings checks, latest stable GitHub release metadata, numeric version ordering, bounded HTTPS, APK integrity/package/version/current-signer verification and user-confirmed Android installer.
- Fixed compact short-window scrolling, exact project-name filtering/Trash options and preservation of follow-up text typed during an in-flight web chat.

Verification includes 233 Android JVM assertions, 6 Android Python tests, 27 server Python tests, 10 web JavaScript tests, browser scratch-flow checks, signed APK verification and independent source review. Native physical-device layout/IME/foldable behavior, speech/notifications and self-update installer remain device-QA requirements; no phone-runtime approval is implied.

### Upgrade

Install the latest APK once over version1.0.0 without uninstalling; that original version has no updater. App data is preserved through the normal same-package/same-signature Android upgrade path. Later updater-enabled versions can offer newer releases in-app. Keep platform protections enabled; installation always requires consent.

Browser alerts require the page to remain open. Android background alerts are best effort, not instant push. Browser offline reads are page-memory only. Hermes drafts do not send mail.

## 1.1.0 — source milestone

Added and independently reviewed the embedded updater. This intermediate source milestone is retained in Git history; version1.2.0 is the recommended updater-enabled APK.

## 1.0.0

Original signed native Android companion with task management, self-hosted polling alerts, offline read-only cache, task-context Hermes questions and email drafts.
