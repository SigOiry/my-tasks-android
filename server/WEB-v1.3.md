# Web v1.3 — integrated and scratch-browser verified

## Final hardened artifact

Current bundle: **web-590a4005d502**, full SHA256 `590a4005d50209b701fce8de9378b3cdfac266f0dc76fd3775f0abcfaa406d03`. All eight source/dist assets are byte-identical. Final JavaScript suite: **69/69**; unchanged independent production-function probes: **38/38**. Independent final review passed.

Post-acceptance hardening closes private-dialog/error revival after session expiry (including acknowledgement JSON races), preserves replacement-session locks, and makes disconnected conversation-history gaps reachable through server cursors. Web chat now uses the same inert Markdown renderer as notifications, retaining exact originals for Copy; scoped paragraph/list styling keeps message text in the main ink color. Parent browser checks confirmed64px task rows, loaded Inter, rendered bold text, reachable compact composer, no active message links/images/scripts, no horizontal overflow or runtime errors.

The55 browser-flow assertions documented below were originally executed on the earlier acceptance bundle; they are historical evidence, not a claim that every browser suite was rerun after each hardening edit. Native runtime remains a separate device gate. Production publication/deployment is verified by the parent, not by these scratch results.

## Initial browser acceptance artifact

- Bundle: **web-d16f475a0d08**.
- Full ordered-name/content SHA256: `d16f475a0d0881177d1931773554e3cf00bc5b30c748c796151f857a045ffefd`.
- Eight assets: index.html, app.css, app.js, policy.mjs, conversations.mjs, icon.svg, inter-variable.woff2, INTER-LICENSE.txt. No fixture credentials, browser tests, production task data or remote dependencies are bundled.
- Missing HTML integration is resolved. Every static app selector exists exactly once; Tasks/Discussions, task-thread picker, chat/rename dialogs, appearance preferences, username/password and forced-change panes are actual markup, not placeholders.

## Implemented

Username/password login; immediate transient activation-fragment consumption; automatic legacy-cookie/session discovery; restricted bootstrap pane with no private API calls; current/new/confirmation validation using Array.from Unicode codepoints (15–128, UTF8 max1024, no trim); normal password change; clear expiry/activation-required/rate-limit/unreadable-response messages; uncertain password-change recovery without automatic replay. No service-token paste UI or client secret persistence/logging.

Canonical notification.sections Context/New/optional Suggested next steps; restricted safe Markdown (bold/emphasis/paragraph/list/code); inert link/image labels and literal untrusted HTML; no anchors/images/source actions in summaries and no legacy technical diffs when prose is present. Exact acknowledgement token and409 fresh-review gate preserved. Ordinary editor source links remain separate.

Persistent task/free discussions, multiple threads per task, all/free/task/archived listing, rename/archive/reopen, older-page deduplication, server latest-request recovery, exact-payload uncertain-delivery retry, per-thread memory drafts and async follow-up preservation. No tools, second agent or CLI/dashboard-history import.

Inter/OFL attribution and license; solid compact title-first rows; cool blue light/dark palette; capsule/glass control/navigation layer only; opaque/high-contrast/reduced-transparency fallback;160–260ms motion honoring reduced-motion. Actual supplied Things screenshot was inspected for density/content/control separation. Background refresh now preserves keyboard task-row focus. Logout/expiry scrubs task/notification headline+summary/chat/rename DOM, forms and bindings, closes every dialog and invalidates late chat and async-startup callbacks.

## Verification actually executed

- `node --test tests/web_*.test.mjs`: **29/29 passed** (auth, Unicode/fragment/restricted gate, late startup/logout, durable store, real app deferred admission, safe Markdown/XSS/link/unmatched syntax, markup uniqueness, contrast and policies).
- Syntax: app.js, policy.mjs, conversations.mjs and browser_ui/conversations/notifications/states/logout scripts passed node --check.
- `python build_web.py`: exact bundle above; full hash recomputed from built assets.
- `python -m unittest discover -s tests -p test_web.py -q`: **10 tests passed**. pytest is not installed; used the repository's unittest runner, not fabricated pytest results.
- Actual scratch HTTP: public initial-password login428; activation200 restricted; restricted /api/tasks428; explicit password change200; rotated full /api/tasks200; normal username/password login200. Generated credentials stayed private; no actual password was typed through browser tools.
- Actual browser on `http://127.0.0.1:9138`, final bundle **web-d16f475a0d08**: notifications **13**, tasks **13**, conversations **17**, appearance/account/error states **12** assertions passed, plus logout revocation/private DOM/forms/storage/bindings checks. Final error/unhandled-rejection array: **[]**. Final dialogs: **none open**, public login restored.
- Real notifications browser checks include rich prose, raw HTML inertness, no anchors/images/source URLs, stale exact-token409 leaving summary/read state intact, then fresh-summary acknowledgement persisted.
- Real discussion checks include real persisted admission/readback, editable follow-up, free+two independent task threads, correct thread draft, rename/archive/reopen and full48-message older-history deduplication. Request status at admission check was genuinely running, not a fabricated answer. Failed/interrupted state checks use explicitly labeled synthetic persisted fixtures through real API/UI, not claimed provider failures.
- Actual restricted startup browser showed password pane, hidden shell, zero task rows. Actual expiry cleared private task/message DOM and returned to login. Normal change/cancel was browser exercised; credential submission was exercised over real HTTP + production-handler unit tests, not password typing.
- Actual Inter and license endpoints200 after scratch restart with current backend allowlist; document.fonts loaded Inter. Earlier font404 was stale-running backend, now resolved.
- Visually inspected1280x900 desktop and390x844 mobile light/dark;390x460 short composer remained reachable, no horizontal overflow. Offline emulation disabled Send while loaded history remained readable. Reduced motion/transparency controls were checked through computed style.

Evidence: `/opt/data/cache/scratch/mytasks-web-v13-final/browser-acceptance-final.json` plus desktop/mobile screenshots in that private scratch directory. Final browser scripts were run serially against actual HTTP; final JSON has done=true and no error field.

## Startup / QA selectors

Public: #login, #login-form, #username, #password, #login-error.
Restricted/change: #password-pane, #password-form, #current-password, #new-password, #confirm-password, #password-error, #password-cancel.
Workspace: #app, #nav-tasks, #nav-discussions, #tasks-workspace, #discussions-workspace, #new-free, [data-conversation-id], #discussion-scope, #discussion-task, #discussion-archived, #discussion-more.
Task threads: #hermes-tab, #task-thread, #task-thread-new, #task-thread-open, #task-thread-more.
Chat: #chat-dialog[data-conversation-id], #chat-message, #chat-send, #chat-status, #chat-older, [data-message-id], #chat-check, #chat-replay, #chat-retry, #chat-rename, #conversation-title, #chat-archive.
Notification: #updates-dialog, #updates-summary, #updates-ack, #updates-refresh.

## Scope and remaining operator work

No production deployment/GitHub publication/native changes by web worker. Only web sources, build script, JS/browser tests and WEB documentation changed; temporary fixtures stayed under scratch. Account/backend/native delivery belongs to their owners.

Parent reports production still runs an older cached plugin without forwarded sections; parent-controlled backend/plugin restart/deployment is required for live canonical prose. This worker did not attempt the declined Dockge login or deploy. The browser client remains compatible with absent sections via legacy fallback, but that is not a claim production prose is deployed.

Not verified: physical mobile keyboard/installer/microphone, iOS-native materials, production delivery. CSS retains legacy foundation with authoritative v1.3 overrides. Unsent drafts intentionally do not survive full reload/logout. Search covers loaded discussion metadata because the contract has no server search route. Retry after reload uses question mode where persisted history exposes no original mode. No fabricated recovery of previously discarded stateless chats.
