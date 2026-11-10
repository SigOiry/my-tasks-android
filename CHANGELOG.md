# Changelog

## 1.6.1

Android visibility fixes and independently reviewed shared-chat public research routing. The final backend privacy gate passed after adding indirect-writing/gerund, opt-out modifier/apostrophe and social/capability regressions. Release assets retain the original signer and standard updater metadata.

- Correct shared-chat research routing: ordinary public factual questions in English/French no longer depend on a small set of freshness keywords. Eligible direct public URLs use a single pinned page read and tool-free synthesis. Capability replies describe the server's actual bounded research feature, not the synthesis model's lack of tools.
- Keep private/supplied text, credentials, ordinary writing, greetings, explicit no-browse requests and rolling summaries out of public retrieval. Persisted-current-request isolation, SSRF checks, provenance/context budgets and replay semantics remain enforced.
- Remove the Android category rail/dot that overlapped completion checkboxes. Preserve separate 48dp completion and task-action targets.
- Make project task surfaces substantially more visible: low/medium/high tint is 16/26/36% in light mode and 16/24/32% in dark mode, with tested readable text/cues. Importance remains intensity within the stable project hue.
- Replace the small fixed-blue unread outline with a broad 15dp soft external halo in the task's own hue, including visible static reduced motion. Sustained breathing, bounded concurrency/lifecycle and exact-token acknowledgement are retained.
- Android version `1.6.1`, code `11`, original signer. The web presentation and account/canonical task data are unchanged by this fix.

Verification and remaining physical-device/installer limitations: `qa/chat-visual-fix-20261110/REPORT.md`.

## 1.6

- Android-only project-hue/importance-intensity row tints, persistent Project/Importance/Neutral choices and stable canonical project mapping. The existing web presentation is unchanged.
- Genuine external unread halos: stronger once-per-token arrival followed by sustained gentle breathing while visible/idle; bounded concurrency, lifecycle/interaction pauses, static reduced motion and unchanged exact-token acknowledgement.
- Directional native navigation, moving capsules, coordinated menu/sheet entry and dismissal, outgoing/incoming message insertion, honest queued/running progress, keyboard/composer motion and preserved fold/draft/older-reading state.
- Smart and traditional sorts retain separate actionable/waiting/inactive/deleted lanes, meaningful direction controls and saved preferences without canonical mutations.
- Shared durable-chat backend gains bounded read-only public search/page retrieval, implicit opening-hours routing, actual search/read progress and persisted source URLs. Public-IP/DNS/redirect restrictions, isolated public research context, tool-free summaries/no-browse/writing paths, honest failures and idempotent replay remain enforced. No terminal, arbitrary files, email sending or task/calendar mutation tools.
- Fixed pre-release review findings: failed private turns cannot enter a later public search query; the exact persisted current request is passed separately from merged model history. Research evidence/instructions share the existing 44,000-character synthesis budget without truncating persisted history. Corrected the isolated Android SDK test resource seam and reran its real compilation/anchor gate.
- Android version `1.6`, versionCode `10`, original release signer. Install over the existing app without uninstalling. Release assets include the signed APK, exact metadata and checksums.

Verification/evidence: `qa/releases/v1.6/README.md` and `qa/android-web-access-20261110/REPORT.md`. Real search was verified inside the deployed backend and in durable scratch-provider probes; native transport/motion recordings use the explicitly synthetic isolated provider. Combined live-provider Android search footage, Chrome first-run external-page completion, physical Samsung/One UI, exhaustive accessibility and installer behavior remain separate checks. This release does not claim those gaps closed.

## 1.5

- Native Liquid Glass-inspired redesign: compact Tasks, grouped details/editing, anchored actions, readable update review, durable task/free conversations, keyboard-safe composition and deliberate folded/unfolded layouts.
- Shared muted project colors, deterministic canonical-identity mapping, Importance/Neutral alternatives and persisted presentation preferences.
- Smart cross-project ordering using canonical deadlines, importance, status, unread updates and capped creation age; traditional Project/Importance/Due/Updated/Created ordering remains available. Waiting and inactive work are separated, exact-token review semantics are unchanged, and sorting never rewrites tasks.
- Four finite update-edge/halo breaths with persistent unread markers; running-only response indicators and honest queued/admission states. Native moving scope capsule, directional page motion, bounded idle reorder feedback and reduced-motion/transparency behavior.
- Corrected repeated native toolbar-anchor drift and web reverse sorting with missing date/timestamp values. Authentication, canonical storage, original Android signer and durable message identities are preserved.
- Published as tag `v1.5`, Android version name `1.5`, version code `8`. Install over existing versions without uninstalling. Updates are offered on the next eligible foreground check or Settings → Check for updates; installation still requires Android confirmation.

Known limitations: web currently uses the workspace Europe/Rome calendar rather than the browser's local zone; web shared moving-capsule/semantic direction-label polish remains unfinished. Native offscreen/background emphasis stops rather than guaranteeing visible-time resumption. Physical Samsung/One UI, exhaustive accessibility and real-provider animation recordings remain separate checks. Native QA screenshots/recordings use an isolated explicitly synthetic backend, not fabricated production behavior.

## 1.3.0

- Durable server-side conversations: multiple selectable threads per task, free discussions, cross-client history, rename/archive/reopen, older pagination and preserved follow-up drafts.
- Persisted asynchronous message requests, exact-payload idempotency, bounded work, restart interruption and fenced late completions. No automatic replay of uncertain/failed work. Original history survives model-context compaction through real rolling summaries.
- Username/password sign-in replaces manual token pairing. Private first-activation ownership proof, mandatory initial password replacement, restricted API gates, slow salted password hashes, epoch-based revocation and separate browser/native sessions. Existing Keystore identity and release signer are preserved.
- Full native sessions remember the device for30 days so unattended task checks do not stop twice daily; browser and restricted activation sessions retain12-hour expiry.
- Canonical prose notifications show Context, New and optional advice with inert safe Markdown. No original-mail navigation in summaries. Exact task/update acknowledgement, fresh-review conflicts and ordinary-editor source access remain separate.
- Compact blue light/dark interface inspired by iOS26 control/navigation materials, licensed self-hosted Inter typography, floating capsule navigation, short transitions and reduced-motion/transparency fallbacks. Solid content remains readable; Android system notifications remain OS-rendered.
- Both native and web chat render safe Markdown while preserving complete original saved text. Old discarded stateless conversations cannot be recovered.

Same-package/same-signature upgrade: install over the current app without uninstalling. Account provisioning and coordinated backend/web deployment are required before activating this client. Hermès remains a no-tools assistant for questions, analysis and drafting, not mail sending or unrestricted agent execution. Native runtime/Keystore/foldable/IME/installer checks still require a device; host tests do not imply phone validation.

## 1.2.1

- Fixed a reproduced credential-save failure when a downloaded token is pasted with a final newline, CRLF, spaces or tabs around it. Only surrounding clipboard whitespace is removed; token contents are preserved and internal whitespace/control/non-ASCII characters remain invalid.
- Uses the same validation at the settings and encrypted-store boundaries, with separate field errors for the server origin and token.
- Genuine local credential-storage failures show a safe exception-type diagnostic instead of blaming HTTPS or echoing sensitive exception messages. Android Keystore encryption, key alias and existing encrypted credentials are unchanged; there is no plaintext fallback or key reset.
- Added22 credential regression assertions. The actual Store input seam changed from rejecting a final-newline synthetic token to accepting it up to the Android-only encryption boundary. A host JVM does not verify device Keystore/storage behavior.

Build verification:255 JVM assertions and6 Python tests passed; versionCode4, original release signer retained. Install over the existing app without uninstalling. Physical phone confirmation remains required.

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
