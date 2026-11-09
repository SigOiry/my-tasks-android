# Production browser workspace

## Artifact and routes

`web/` is the maintained dependency-free HTML/CSS/ES-module source. `python build_web.py` produces the shipping `web_dist/` bundle plus a reproducible content-derived version manifest. No CDN, remote font, inline script/style, framework runtime, source map, production task fixture, token, or browser service worker is shipped. Typography deliberately uses the local humanist stack Avenir Next / Avenir / Segoe UI. Rebuild after any web source change. The optional Dockerfile now copies the session/release modules and built bundle; no deployment was performed by this implementation.

Public routes:
- `GET, HEAD /`: static login/workspace shell; no private data embedded.
- `GET, HEAD /assets/{name}`: only the explicit shipped JS/module/CSS/icon filenames; no directory mounting or arbitrary path lookup.
- `GET /app-info`: exactly `version`, `android_release_url`, `default_server`. No credentials or private configuration.
- Existing `/health` and fixed `/download/my-tasks.apk` remain public.

Session routes:
- `POST /auth/login`, JSON `{ "token": "..." }`, exact configured `Origin` required. On success returns `{authenticated:true,csrf:"..."}` and sets an opaque session cookie. The paste input is cleared immediately, including on errors. Invalid request validation never echoes credential/chat input.
- `GET /auth/session`: `{authenticated:false}` or `{authenticated:true,csrf:"...",expires_at:<Unix seconds>}`. The CSRF value is not the access token and grants nothing without the HttpOnly session cookie.
- `POST /auth/logout`: exact Origin and `X-CSRF-Token` required. Revokes the exact server session and expires the cookie. The UI then clears private memory, DOM/form values, conversations, scoped alert cursors, preferences, pending requests and open browser alerts. Browsers do not permit websites to revoke notification permission; the settings explain how to revoke it in site settings.

All `/api/*` routes remain authenticated. The original Android `Authorization: Bearer <token>` contract is unchanged and does not require browser CSRF headers. An explicitly supplied invalid bearer header fails even if a valid cookie exists. Repeated `Authorization`, `Origin`, or `X-CSRF-Token` fields (case-insensitive, even identical values) are rejected with controlled400 before selecting authentication or reaching handlers. Cookie-authenticated unsafe API requests require **both** an exact allowed Origin and an explicit `X-CSRF-Token`; same-site cookies alone are not sufficient. Non-ASCII CSRF values fail with403 rather than escaping the security-header boundary. No CORS is enabled. The existing streaming 65,536-byte body cap also covers login/auth requests. All responses, including authentication failures and API errors, carry `Cache-Control: no-store` and security headers.

`GET /api/release-status` is an additional authenticated fixed-repository public GitHub lookup. It returns `{tag,published_at,release_url}`; response size is capped at 256 KiB, network timeout is 10 seconds and successful public metadata is cached for five minutes. No application token, cookie, private task content or provider credential is forwarded. On upstream failure it returns a factual sanitized503 and keeps the direct latest-release link available. The button does not pretend the server APK has the same version as GitHub.

## Session policy and deployment configuration

Production `create_app()` always defaults to `cookie_secure=True`. There is deliberately **no environment switch** that can silently disable Secure cookies. `cookie_secure=False` is an explicit constructor argument restricted to loopback/TestClient origins for scratch QA. Cookies are `HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=43200`, with no Domain attribute. Only an opaque random 48-byte value is sent in the cookie, never the bearer token or a token-derived browser credential.

`MOBILE_WEB_ORIGIN` defaults to **https://task.oirysimon.com** and must be an exact origin with no trailing slash/path, query or wildcard. The supplied compose source explicitly sets it. Origin comparison does not trust Forwarded/X-Forwarded headers or an attacker-provided Host. Keep uvicorn `--no-proxy-headers`; reverse proxy upstream/read timeouts should exceed205 seconds (210 or more) for chat. HTTPS must terminate at the public origin. A different web hostname must be deliberately configured before login can work.

Server sessions live separately in `web-sessions.sqlite` beside the configured `MOBILE_METADATA_DB` (the `state/` writable mount in the existing stack). The file is created0600. It contains hashed opaque session IDs, CSRF values, absolute expiries and a server-side SHA-256 fingerprint of the high-entropy bearer credential, never the raw token or task text. Login credential hashes and CSRF values are compared with `hmac.compare_digest`. Sessions expire absolutely12 hours after login, not12 hours after last use. They survive process restarts only while that same metadata file and bearer credential are retained. Rotating the bearer token and restarting invalidates old web sessions as well as requiring Android re-pairing. Removing the session SQLite file with the service stopped revokes every browser session; do not remove the separate events database casually. At most100 active stored sessions are retained; expired/old-credential sessions are pruned during login. Browser logout revokes just its current session.

Schema-valid same-origin login attempts (successful and failed) are limited in SQLite to five per socket-client address per60 seconds and30 globally;429 includes Retry-After60. Because proxy forwarding headers are intentionally untrusted, requests behind one reverse proxy may share its source-address bucket. This conservative limit protects authentication but can also briefly throttle a legitimate repeated login. Add proxy-side rate/body limits too; do not weaken authentication to work around them. Missing/corrupt session metadata fails closed with sanitized503. Health remains a liveness check, not a promise of database/provider readiness.

The CSP allows only same-origin scripts/styles/fonts/connections, blocks objects/base-uri/framing, and does not permit unsafe-inline. Referrer-Policy is no-referrer; X-Content-Type-Options nosniff; X-Frame-Options DENY; camera/geolocation are disabled. Browser speech recognition is opt-in with its own warning because the browser may use a remote speech service. No fake or public pairing/token download route exists.

## Workspace behavior

The responsive Operate surface has sidebar views Active/Today/Week/All/Trash, project navigation and dropdown, full-text task/notes search, status filtering, priority/date/title ordering, real counts, project-grouped rows and unread badges. Today/Week boundaries use server-supplied Europe/Rome dates, not browser time. Unread tasks sort first without changing stored fields. There are no invented production metrics or demo tasks.

A centered native-dialog task inspector supports every editable field, notes, trustworthy backend-supplied Gmail/Calendar source links, completion, confirmation-backed soft deletion and recovery. Dialogs use native focus containment/top-layer behavior. Unsaved close/navigation asks before discarding. A stale409 keeps the original form and revision, disables save, offers copy and explicit review/reload; there is no force-overwrite path. Opening an unread task shows only its readable update summary. Explicit OK acknowledges **that displayed token** before opening the editor. Any failure keeps the summary visible;409 disables OK until a fresh-summary review, so newer unseen updates cannot be silently cleared.

Hermes questions/email drafts call the actual existing `/api/chat`, with zero tools and no email sending. Current messages are never silently truncated. Pure policy serializes at most12 previous messages, evicts whole old turns to fit60,000 UTF-8 bytes and enforces8000 Unicode codepoints per message. Identical413 payloads are blocked until changed. The browser waits up to205 seconds; timeout/interruption does not prove cancellation, warns that original provider work may continue, and never automatically resends. A per-conversation draft revision lets a successful submission clear only its unchanged draft; typing or voice input during pending work remains editable and is preserved on success/error/interruption, without automatically submitting that follow-up. Provider failures are factual503, not mock replies. History/drafts are page-memory only. Voice transcription remains editable and never auto-submits; unsupported/denied microphone falls back to typing.

Foreground browser notifications poll `/api/events` every20 seconds while open. Permission is requested only from the explicit settings action. First activation/new session/reset drains historical pages quietly, persists only a cursor scoped to a hash of the opaque session's CSRF value, and processes pages before advancing it. Current deleted/missing/read tasks and own-response tokens stay quiet. Explicitly absent canonical tracking permits the documented event fallback; missing/unknown tracking is not false. Delivery never acknowledges task updates or reads mail. Successful own writes consume their event cursor before reload so a normal reload does not alert for those edits. **Closing the browser stops checks: no push/service worker/background delivery is claimed.** Native Android background behavior remains separate.

Offline mode retains only the current page's in-memory task copy, disables writes/chat and reconnects explicitly or on an online event. No raw task data, conversations or bearer credentials are stored in localStorage/sessionStorage/URLs. Only appearance preferences, notification opt-in and opaque scoped transport cursors use localStorage. Reloading offline cannot reload private cached tasks. Theme supports light/dark/system; density supports comfortable/compact. Search `/`, new `N`, Escape and visible focus states are supported; interactive controls are44px minimum; reduced-motion preferences suppress animation.

## Scratch-only QA startup

From `/opt/data/workspace/my-tasks-mobile/server`:

```sh
python build_web.py
node --check web/app.js
node --test tests/*.test.mjs
python -W error::ResourceWarning -m unittest discover -s tests -v
# Fresh explicit synthetic fixtures; does not read the canonical database/token:
python qa_server.py --seed --port 9138
# OR copy an explicit real DB read-only once into scratch, never mutate its source:
python qa_server.py --copy-db /absolute/path/to/tasks.json --port 9138
```

The helper prints only the loopback URL, scratch directory and private **scratch** token-file path, never its contents. The token is newly generated, private0600, unrelated to production. All managed data/session/event files are inside a private child of `/opt/data/cache/scratch`; the listener is hard-coded127.0.0.1 and forwarding headers are disabled. Use **exactly** `http://127.0.0.1:9138`, not a hostname alias with a different Origin. `--root <existing scratch directory>` reuses those fixtures/session metadata; `--prepare-only` writes fixtures without starting uvicorn. `--seed` and `--copy-db` are mutually exclusive. No public test/debug routes are added.

Paste only the scratch token in this loopback login page using the private local fixture. Alternatively a local test runner can call the real `/auth/login` endpoint and transfer its opaque cookie to the browser without exposing the bearer token to source or logs. A TestClient can use `create_app(db_path=...,token_file=...,metadata_path=...,session_path=...,web_origin='http://127.0.0.1:9138',cookie_secure=False)` with only scratch paths.

`tests/browser_ui.js` is an awaited browser-runtime acceptance script, **not distributed in the web bundle**. It refuses all but the designated loopback9138 origin, requires an authenticated scratch session, and drives real UI/API create, notes/priority, summary-before-ack, stale409 preservation, delete confirmation/cancel, trash/restore, search, responsive overflow and no-private-storage checks. It temporarily accepts only the explicit scratch conflict reload confirmation. Fresh `--seed` provides an unread task for the summary checks. `tests/browser_logout.js` separately verifies real server revocation and clearing private DOM/forms/storage. Never run these against production.

## Verification actually executed

- Blocker-fix verification: rebuilt shipping bundle **`web-bf498e61ff7d`**; `node --check web/app.js` and `node --check web_dist/app.js` passed.
- All27 Python tests passed with `python -W error::ResourceWarning -m unittest discover -s tests -v`, including native-generated chat-payload parity. New regressions exercise duplicate security fields in both orders with/without valid cookie fallback and with bearer bypass attempts, plus identical duplicates; rejected creates/edits/login/logout leave every scratch fixture file byte-for-byte unchanged. Exact raw CSRF byte `0xff` produces403 with CSP/no-store/security headers and preserves the session. Single Android bearer and explicit-invalid-bearer/no-cookie-fallback regressions still pass.
- All10 JS tests passed with `node --test tests/*.test.mjs`:5 existing policy tests and5 new chat regressions executing the actual production app/request/sendChat/render/input-handler code in Node VM with DOM/transport doubles. Deferred success, provider error and interruption preserve new input; editing away/back to identical text retains ownership; history appends the original successful exchange exactly once and nothing autosends. Untouched drafts clear only on success.

### Earlier implementation browser evidence (not rerun for blocker fixes)

The following records predate this blocker-fix bundle. This pass performed automated tests only, not new browser/provider/deployment QA:
- Real scratch HTTP server and authenticated browser at1440×1000 and390×844:15 browser acceptance assertions passed; repeated logout suite passed; measured visible buttons met44px and page width did not overflow. Light/dark settings and native task/Hermes dialogs were visually inspected. Runtime instrumentation recorded no error/unhandled-rejection/CSP violation during the final logout path.
- Actual configured Hermes provider returned a genuine task-context answer through the browser `/api/chat` flow (no provider double). GitHub release-status action performed its real request and returned the published public release metadata. Unit provider/release error tests intentionally use doubles only to exercise failures.
- Real foreground Notification constructor instrumentation (permission granted only for the scratch origin) observed zero baseline alerts, zero read-false alerts, exactly one external-update alert, and no new alert for the following own UI edit. Delivery left an unread badge until explicit acknowledgement. A later source change between summary display and OK produced real409, kept the editor closed/OK disabled, and refreshed/acknowledged the newer summary successfully.
- Browser network-offline emulation retained read-only memory rows, disabled Save, never persisted task text and reconnected successfully.

No production task writes, secret-file reads/changes, deployment, original Hermes restart, or physical microphone/phone QA were performed. Parent/operator still owns independent screenshots/interaction QA and deployment verification.
