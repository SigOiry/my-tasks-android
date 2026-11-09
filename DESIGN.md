# My Tasks — compact Liquid Glass-inspired workspace

## Intent

Make tasks immediately scannable and conversations returnable. The home is a working list, not a marketing page: no oversized greeting, redundant metrics or repeated metadata badges. Two primary destinations: **Tasks** and **Discussions**. Settings is a utility.

The visual language adapts iOS26 principles to native Android and the web. It does not claim UIKit, Apple's proprietary fonts, iOS optical refraction or control over Android's notification shade.

## Reference patterns

Apple's official Liquid Glass adoption guidance separates a functional navigation/control layer from readable content, limits custom effects, and preserves reduced-motion/transparency accessibility. The official Things3.22 OS26 article and supplied screenshots informed compact checkbox/title hierarchy, subordinate context, rounded controls, a floating primary action, subtle touch feedback and adaptive windows. References are inspiration, not copied artwork:

- https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass
- https://developer.apple.com/videos/play/wwdc2025/219/
- https://culturedcode.com/things/blog/2025/09/things-for-os-26/

## Composition and density

- Desktop: narrow navigation/sidebar, condensed task toolbar, category/project grouping and title-first rows. Keep description, notes, sources and full editing inside details.
- Compact: floating capsule navigation and a focused single pane. Search/filter utilities must not consume most of a short window. Keyboard/composer access takes precedence over decorative chrome.
- Expanded/foldable Android: split list/detail when the current window is wide enough. Do not hardcode a named phone's unverified dimensions. Preserve task/thread selection and drafts through resize.
- Web task rows target64px; native rows68dp at ordinary font scale, expanding when accessibility text needs it. Titles can wrap to two lines; useful due/project context stays subordinate. Keep44px/48dp actions.
- No raw provider JSON or evidence/reference dumps in the landing view. Dates and unread/priority indicators must have textual/accessibility meaning, not color alone.

## Materials, typography and motion

Use cool neutral light/dark content, blue functional accents and quiet separators. Normal text/action roles meet4.5:1 contrast in the tested palette; use darker blue for small light-theme labels rather than white-on-bright-blue decoration. Content stays solid; frosted/translucent navigation, capsules and sheet controls form the separate functional layer. Hairline highlights and restrained elevation suggest material depth. Opaque/high-contrast/reduced-transparency fallbacks remain readable.

Self-hosted **Inter** is the licensed cross-client font, with system fallback and OFL attribution in Settings. Do not redistribute Apple's SF Pro. Preserve user font scaling. Hierarchy comes from weight, size and alignment, not oversized headings or constant all-caps copy.

Short purpose-driven transitions and pressed/selected feedback should clarify navigation and status without replaying on every poll. Honor browser reduced motion and Android system animation settings plus explicit preferences. No endless decorative shimmer, heavy continuous effects, or unmeasured frame-rate/battery claims. Native controls use bounded decor-backdrop capture/downsampled blur with a fine rim and opaque accessibility fallback; browser materials retain their own implementation. This does not reproduce Apple’s private optical-refraction renderer. Actual Android screenshots and recordings, not gallery art, establish native appearance.

## Updates and task safety

Unread tasks open a readable summary before the editor: **Context**, **New**, and optional **Suggested next steps**, omitting unavailable background/advice. Use canonical snapshot-bound prose, not raw technical diffs. Safe Markdown may emphasize facts or structure paragraphs/lists/code; no active links, images or interpreted HTML in the summary. Original-mail navigation belongs only to ordinary task details.

Acknowledge only the exact displayed task ID/token. A newer update or failed acknowledgement stays visible and requires explicit fresh review. Delivery/open/dismiss is not acknowledgement. Editing uses revisions and preserves stale409 drafts; deletion is recoverable and confirmed. Sorting never changes stored dates/priorities or mailbox read flags.

## Conversations

Every accepted task/free message and reply is durable server history. Task details select among multiple saved threads; Discussions provides all/free/task/archive views, rename/reopen and pagination. Unsent drafts are a separate client concern, never represented as saved turns.

Long model contexts may use genuine persisted summaries; full original messages remain available. Request identity survives ambiguous delivery; never automatically rerun provider work. Navigation, polling, account expiry and logout must not attach a reply to another thread, erase a new follow-up, or recreate scrubbed private UI. Copy preserves original saved text. Voice is explicit, disclosed, reviewable and never auto-submitted. Hermès has no tools or email-sending authority.

## Authentication and privacy

Username/password replaces manual token pairing. First activation requires private ownership proof and forces password replacement before private APIs. Prevent public guessable-default takeover. Never put passwords/session credentials in source, logs, browser storage, query strings or public download URLs; the owner-only one-time activation fragment is transient and stripped before other work. Never fall back to browser cookies after bad Authorization.

Browser cookie sessions expire after12 hours. Full native sessions remember the device for30 days; restricted bootstrap remains12 hours. Logout/password replacement revokes access. Preserve the original Keystore alias/AAD and APK signer. Settings/update checks remain available without task-server credentials.

## Acceptance

Exercise real scratch browser/API flows and inspect desktop/compact screenshots; verify loading/empty/offline/error/conflict/pending/interrupted states as well as happy paths. Run security/worker/pagination regression tests and independent review before publication. Check exact live origin, bundle/artifact hashes, auth gates and remote source/tag/release readbacks after deployment.

Browser screenshots and JVM/SDK builds are not Android runtime proof. Physical launch/upgrade, real Keystore, folding/IME/font rendering, speech, OS notifications and installer consent require a device. Never write test obligations to production or include private state/signing material in public source.
