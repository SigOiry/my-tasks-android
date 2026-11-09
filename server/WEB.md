# Browser workspace v1.3

Maintained source: `/opt/data/workspace/my-tasks-android/server/web/`. `python build_web.py` creates reproducible `web_dist/`. See `WEB-v1.3.md` for the current verification record. No deployment/publication is performed by these commands.

## Account and privacy

The new client uses username/password, never a service-token paste field. Initialized accounts follow `AUTH-NOTIFICATIONS-v1.3.md`. Startup automatically checks `/auth/session`, including an existing legacy owner cookie. Restricted sessions see only mandatory password replacement; the client transport rejects private API requests locally too.

`#activate=64hex` is removed synchronously before asynchronous startup, held transiently and exchanged once through `/auth/activate`. Invalid activation fragments are removed too. No secret is stored or logged and no activation is automatically retried. Public initial-password login without ownership proof receives an activation-required explanation, not a workspace session. No registration/reset/default-password backdoor or MFA claim.

`/auth/login` sends exact `{username,password}`. `/auth/password` sends `{current_password,new_password}` with cookie + CSRF. Confirmation stays client-side; new passwords require15–128 Unicode codepoints and at most1024 UTF-8 bytes, without trimming/truncation. Submitted password fields clear. Wrong current password preserves a still-valid owner session. Lost change responses advise explicit sign-in with the new password, never automatic retry. Ordinary password change remains cancellable; bootstrap cannot bypass replacement.

The server manages an HttpOnly/Secure/SameSite=Strict cookie; the app holds CSRF only in memory and never adds Authorization. Passwords, activation codes, cookies and CSRF are never written to browser storage. Unsafe cookie requests require exact Origin + explicit CSRF. Backend bearer selection, duplicate-header defenses, limits, validation sanitization and no-CORS policy remain authoritative.

Confirmed logout clears private memory/DOM/forms/drafts/pending bindings/preferences/cursors, not durable server history. Expiry clears private UI and returns to sign-in. No production credential or initial password is embedded. Appearance preferences, notification opt-in and an opaque scoped alert cursor are the only persistent browser values.

## Tasks and notifications

Compact title-first project groups; Active/Today/Week/All/Trash; expandable search/filter/sort; project navigation; notes and separate source context. Date boundaries come from the server. Background refresh preserves row keyboard focus. Native dialogs contain focus. Revision409 preserves edits and offers copy + fresh review, never force overwrite. Deletion is confirmed, soft and recoverable.

Unread updates gate the editor. Canonical `notification.sections` renders Context, New, then optional Suggested next steps. Empty optional sections are omitted and prose never mixes with legacy technical summaries; legacy summary is used only when sections are absent. Acknowledgement sends the exact displayed token.409 preserves the summary and requires newer review before OK.

Notification Markdown is a restricted AST rendered with createElement/text nodes: paragraphs, bold/emphasis, lists, inline/fenced code. No innerHTML, raw HTML interpretation, anchors, images, URL attributes or source actions. Markdown link/image labels become inert text; unmatched delimiters remain visible. Reliable backend Gmail/Calendar links remain only in the ordinary task editor.

## Persistent discussions

Tasks/Discussions are primary destinations. Global All/Free/Task/archived listing, explicit pagination and search of loaded titles/task names/previews. Saved per-task picker supports multiple discussions per task; free discussions need no task. One assistant, Hermès, with no tools, sending mail or task mutation. No terminal/dashboard-history import or fabricated recovery of discarded stateless chats.

ConversationStore calls only `/api/conversations`, never stateless `/api/chat` or client-supplied model history. Admission identity is retained before POST. Delivery uncertainty offers explicit Check / exact same-request Retry. Reload recovers accepted work from latest_request; failed/interrupted Retry creates a new ID. Optimistic metadata edits, visible pending-only bounded polling, older-page merge/deduplication and compaction hints preserve full server history.

Memory-only per-thread drafts survive navigation, not reload/logout. New follow-ups survive asynchronous admission/replies. Logout epochs reject late private responses. Chat is selectable plain text, never untrusted HTML. Dictation is opt-in/editable and never auto-sends; typing remains available.

## Appearance, offline and delivery

Self-hosted Inter WOFF2 plus SIL OFL1.1 license (linked in Settings), no proprietary Apple font/CDN/telemetry. Blue light/dark palette, compact solid content, capsule navigation and restrained glass only on controls/navigation. Opaque/high-contrast fallback, reduced transparency/motion and short160–260ms transitions. This is an iOS26-inspired adaptation, not native iOS optical refraction.

Foreground browser alerts only, not background push; baseline/reset stays quiet and delivery never acknowledges tasks. Offline last-loaded tasks/history stay readable in page memory while writes are disabled. Full offline reload cannot restore private cached data. No service worker/private offline database.

## Verification and scratch QA

Run in maintained server/: `python build_web.py`; `node --test tests/web_*.test.mjs`; `node --check web/app.js`; `node --check web/policy.mjs`; `node --check web/conversations.mjs`; `python -m unittest discover -s tests -p test_web.py -q`.

Browser scripts under tests/ refuse all but `http://127.0.0.1:9138` and are never bundled. Use only a generated synthetic scratch account/task/conversation DB under `/opt/data/cache/scratch`, loopback-only, cookie_secure=False, forwarding disabled. Authenticate via real HTTP then transfer the opaque fixture cookie privately, or approved vault. Never type an actual password into browser tools/source/logs. Old token-paste QA instructions are obsolete. `qa_server.prepare(...,seed=True,seed_conversations=True)` supplies synthetic data; an isolated initialized account_path may be passed to create_app. Account provisioning belongs to backend tooling/operator. Never modify canonical production task/helper/plugin/Hermes stores for client tests.
