# My Tasks — shared product UI direction

## Surface
Primary **Operate** workspace; secondary **Command/Inspect** details and task-linked Hermes. No marketing hero, ornamental metrics, gradients, glass effects, or generic equal-card grids.

## Composition
- Desktop: restrained navigation rail (views and projects), task workspace with grouped rows and clear priority/deadline/unread signals, task inspector with Details/Hermes modes.
- Compact phone: focused single-pane navigation, accessible search and New action, content-first task rows, clear back navigation.
- Expanded/foldable: list and inspector side by side when window permits; preserve selection, drafts and conversation state through resize.
- Persistent top-level connection/sync state, not a wall of debug messages. Empty/loading/error/permission states receive the same care as populated states.

## Tokens
Light canvas #f7f8f5; surface #ffffff; ink #17251f; secondary #63746a; border #dde5df; primary forest #23674b; primary-soft #edf5ee. Dark canvas #111b16; surface #1a2820; ink #eef4ef; secondary #a7b9ad; border #31453a; primary #abd8ba. Danger/error may use restrained red, overdue amber/red only where semantically meaningful. Do not rely only on color for priority/status.

Use a deliberate humanist/precise sans type system: self-hosted DM Sans if available with its license, or purposeful native sans on Android. Body14–16; task title16; section20–24; app heading24–28; metadata12–13. Avoid tiny text and ornamental oversized headings.

Spacing4/8/12/16/24/32. Interactive corner radius8–12, not giant pill containers everywhere. Hairline containment and slight elevation only for dialogs/popovers; rows should scan as one coherent list. 44px/48dp minimum primary touch targets. Clearly distinguish primary, secondary and destructive actions. Use native platform semantics and input accessibility.

## Interaction
- Search/filter/sort/group/category controls with truthful counts from real tasks.
- Dates in readable local format, explicit overdue/due signals, unknown due dates clear. Stored priority/deadlines unchanged by sort.
- Do not render raw provider JSON/evidence dumps; source buttons only supplied trusted URLs.
- Unread task: readable summary first; exact displayed-token acknowledgement; no silent acknowledgement on open/notification delivery. On conflict keep summary and offer refresh.
- Editing preserves draft on stale409; never offer unsafe force overwrite. Cancel unsaved navigation asks confirmation.
- Draft-only Hermes: clear speaker hierarchy, readable responses, copy draft, explicit voice-recognition privacy and editable transcript; never automatically send mail.
- Settings: theme, notification behavior/permissions, version/update actions, connection account/privacy. Never show tokens in page source, logs, URL or persistent browser storage.
- Keyboard navigation and focus; native browser dialogs/toplayer, reduced motion, clear errors/retry. Browser notifications supported while app open; do not claim closed-browser delivery without actual push implementation.

## Deployment and acceptance
Canonical app/server address is https://task.oirysimon.com. Root webapp and existing /api routes share origin; trusted secure-session login reuses existing token without exposing it to scripts after login. Android should suggest this origin for new setup, but preserve any saved custom server.

Parent must inspect real desktop/compact screenshots, operate scratch create/edit/conflict/ack/delete/restore/chat flows, and verify live HTTPS auth/session/root after deployment. Native Android still requires physical-phone QA if emulator remains unavailable. Source/release publication excludes all tokens/signing keys/private task data.
