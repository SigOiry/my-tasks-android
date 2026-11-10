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

## Shared task presentation contract

The current user brief in `IMPLEMENTATION-PROMPT.txt` section 10 supersedes older conflicting colors, ordering and motion rules. Preserve the compact iOS 27-inspired direction and established light/dark bases.

**Color by** offers Project, Importance and Neutral. Project tokens (light/dark) are Ocean `#527FC2/#8FB2E8`, Teal `#3C8B83/#82C8BA`, Violet `#8972BB/#BBA7E5`, Rose `#B26D87/#DFA2B9`, Amber `#A57A35/#D4B26F`, Slate `#6C7E91/#A6B7C9`. Map exact canonical project identity with unsigned 32-bit UTF-8 FNV-1a modulo six. The current store has project string/null, not a project table or ID; exact project string is therefore today's identity. A future canonical `project_id` may take precedence; do not submit that unsupported field to the current API. No case-folding/trimming or position-dependent assignment. Missing identity is neutral. Importance remains muted coral/amber/teal, labelled independently of unread, selection and error. Keep neutral task text on stable reading surfaces; dots/slim accents/light tints carry color.

**Sort** offers Smart, Project, Importance, Due date, Recently updated and Created date when canonical timestamps exist. Preserve existing saved choices, default Smart only when absent, and persist mode/direction/color through the existing client preference stores. There is no preference API in the current contract: cross-device preference sync is not implied or invented. Shared policy, rather than synchronized preferences, guarantees matching order for matching inputs/timezone.

Smart uses deadline overdue +50, today +40, tomorrow +25, next seven days +10; High +30/Medium +15; in progress +6; exact canonical boolean unread +6; one point per full local-calendar week since valid creation, capped at four. Missing/invalid data adds zero. Use the visible client local calendar/timezone consistently for date-only deadlines and age; API `today` metadata is currently Europe/Rome and must not silently override a different visible local calendar. Actionable inbox/todo/in_progress rank across projects, Waiting stays in a labelled lower lane, and scope-eligible completed/cancelled/deleted never compete with actionable work. Tie-break earliest valid deadline, higher importance, oldest valid creation instant, canonical ID. Rank the entire eligible dataset before rendering. Direction does not bring inactive lanes above actionable. Explain the rules outside ordinary rows and retain compact project context outside Project grouping. Sorting is presentation-only. Preserve task/thread selection and viewport anchor; defer automatic reorder during touch, scrolling, editing or open menus.

New canonical update tokens receive a 1-2dp edge, bounded 6-10dp halo and persistent New update marker: **four 1.6-second breaths**, then a clearly visible static edge/marker. No text/height/list animation; token observation is independent of binding/polling. Bound concurrent animations and stop offscreen/background. Reduced motion remains static. No animation/open/dismiss acknowledges a token.

Motion timing: navigation 240-320ms with reversed return; tabs/scopes 180-240ms; anchored sheets/menus 220-320ms; sending insertion 180-240ms; incoming insertion 160-240ms. Sending lasts until admission; Queued and running reflect real transport. Three-dot wave is only for actual running requests, stopping on terminal/hidden state; reduced motion uses static text. Preserve request identity/drafts, reading anchors and explicit handling of ambiguous failures. Keyboard/composer insets must not clip or duplicate navigation; completion/quick actions must roll back honestly on failure.

Independent fixed-clock/Unicode/invalid-data golden vectors live in `qa/task-presentation/`. Passing them does not prove UI wiring, animation quality, final glass composites or native accessibility; actual native/browser evidence remains required.

## Conversations

Every accepted task/free message and reply is durable server history. Task details select among multiple saved threads; Discussions provides all/free/task/archive views, rename/reopen and pagination. Unsent drafts are a separate client concern, never represented as saved turns.

Long model contexts may use genuine persisted summaries; full original messages remain available. Request identity survives ambiguous delivery; never automatically rerun provider work. Navigation, polling, account expiry and logout must not attach a reply to another thread, erase a new follow-up, or recreate scrubbed private UI. Copy preserves original saved text. Voice is explicit, disclosed, reviewable and never auto-submitted. Hermès has no tools or email-sending authority.

## Authentication and privacy

Username/password replaces manual token pairing. First activation requires private ownership proof and forces password replacement before private APIs. Prevent public guessable-default takeover. Never put passwords/session credentials in source, logs, browser storage, query strings or public download URLs; the owner-only one-time activation fragment is transient and stripped before other work. Never fall back to browser cookies after bad Authorization.

Browser cookie sessions expire after12 hours. Full native sessions remember the device for30 days; restricted bootstrap remains12 hours. Logout/password replacement revokes access. Preserve the original Keystore alias/AAD and APK signer. Settings/update checks remain available without task-server credentials.

## Acceptance

Exercise real scratch browser/API flows and inspect desktop/compact screenshots; verify loading/empty/offline/error/conflict/pending/interrupted states as well as happy paths. Run security/worker/pagination regression tests and independent review before publication. Check exact live origin, bundle/artifact hashes, auth gates and remote source/tag/release readbacks after deployment.

Browser screenshots and JVM/SDK builds are not Android runtime proof. Physical launch/upgrade, real Keystore, folding/IME/font rendering, speech, OS notifications and installer consent require a device. Never write test obligations to production or include private state/signing material in public source.
