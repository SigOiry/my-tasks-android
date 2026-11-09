# Companion server and browser workspace

This directory contains the actual Android/browser API adapter and self-hosted browser UI. It is an integration with an existing Hermes **My Tasks** installation, not a second task database or a standalone general-purpose task server.

## Required existing integration

- Hermes Python runtime and configured model provider.
- Canonical `taskdb.py` and `tasks.json` under the configured personal-assistant directory.
- Existing compatible personal-tasks dashboard `plugin_api.py`, providing Store transactions, enriched tasks, revisions, source links and exact update acknowledgement.
- A private high-entropy legacy bootstrap token file and writable private event/session/account/conversation metadata directory.
- An initialized private `MOBILE_ACCOUNT_DB` (provision with `account_admin.py`; see `AUTH-v1.3.md`).

No canonical tasks, mailbox/calendar data, runtime databases, deployment credentials, token files or signing keys are included here. The existing task DB and plugin are deliberately not initialized or replaced by deploying this adapter.

## Build and test

With the compatible integration available in its documented paths:

```sh
cd server
python build_web.py
node --test tests/web_*.test.mjs
python -W error::ResourceWarning -m unittest discover -s tests -v
```

`web/` is the maintained source and `web_dist/` is the reproducibly built shipping bundle. UI behavior uses the real authenticated API. Fixtures exist only in tests and explicit loopback QA setup; no production demo data is embedded.

For isolated browser testing:

```sh
python qa_server.py --seed --account --port 9138
```

This creates a new private synthetic store and token inside scratch. It does not read the production task database or credential. Never run browser mutation tests against a real task store.

## Deployment

`compose.example.yaml` is an example, **not a copy of private deployment configuration**. Resolve the actual existing Docker volume and canonical helper path before use. Set `HERMES_DATA_VOLUME`, `TASKDB_HOST_PATH`, and a deliberate bind address; the example defaults to loopback. The code's default deployment layout uses the existing Hermes persistent home. Do not point it at a fresh competing task store.

Terminate trusted HTTPS at the intended origin. `MOBILE_WEB_ORIGIN` must match exactly; the application's default is `https://task.oirysimon.com`. Preserve Authorization and cookie headers, and allow upstream/read timeouts longer than205 seconds for model replies. Do not publish the raw HTTP port to the internet.

The browser uses a private opaque HttpOnly/Secure/SameSite session after password login; Android uses a private opaque native Bearer session. Initial access requires private owner activation or an existing legacy credential **and** mandatory password replacement before any API data. See `AUTH-v1.3.md` for private provisioning, epoch revocation, bootstrap-only activation rotation, limits, and explicit uninitialized legacy fixture mode. Keep Origin/CSRF enforcement, revision conflicts and exact notification tokens intact. Source overlays are read-only; the service currently shares the same-user Hermes data volume to support existing provider credential refresh, and is not isolated from every credential accessible to that account.

See `WEB.md` for session expiry/revocation, browser privacy, CSP, notification limits and detailed QA. `CONTRACT.md` documents the API.

## User-visible limitations

- Browser notifications work while the page is open; closed-browser push is not implemented.
- Browser offline access is read-only page memory; no private task data is persisted for offline reload.
- Speech recognition availability and processing depend on the browser/device; a transcript is always reviewed before sending.
- Hermès answers general questions in free conversations and uses the current saved task context for task-attached conversations. It has no agent tools, cannot send mail or mutate tasks. The old `/api/chat` remains task-focused and stateless for older clients.

## Durable conversations (v1.3)

Conversation metadata and full messages live in a **dedicated private SQLite file**, default `conversations.sqlite` beside `MOBILE_METADATA_DB`. For deployment, explicitly set `MOBILE_CONVERSATIONS_DB` to a file on the existing persistent private metadata volume (for example `/var/lib/my-tasks-mobile/conversations.sqlite` when that directory is the mounted metadata volume). Keep it out of public/static/APK directories and back it up as sensitive content. The adapter creates it with mode0600 and rejects symlinks, `state.db`, task/token/session/event path collisions and unrelated database schemas. Do not point it at Hermes's original session store, credentials or canonical task database. No session import or original Hermes state mutation is performed. Previously discarded stateless chats cannot be recovered.

Run **one Uvicorn application process per conversation database**, as in the current Docker CMD (`--workers 1`). Internally two bounded model workers accept up to six queued replies (eight outstanding globally), with one pending reply per conversation. Queue saturation is429 without a new message; stale edits/admissions are409. The async202 API persists the request and user message before model work. Requests continue while a screen/tab is closed; poll the stored request or read `latest_request` after reconnecting. Exact request-ID retries return existing state without another model call, even after failure or restart. A deliberate retry is a new request ID. Startup marks all orphan queued/running requests interrupted, increments affected conversation revisions and fences old completions/summaries with their owner/status. Do not run overlapping service instances against this file: a new instance replaces the previous worker epoch.

Full history is never deleted or truncated. Model context is server-authoritative and bounded to44000 Unicode codepoints (not a claim about a provider-specific token limit): recent messages, at most6000 characters of genuine model-generated persisted rolling summary, bounded saved-task context and fixed instructions. Older messages remain available through history pagination. Tiny failed-message overhead is included in the bound; large assistant replies are summarized in bounded segments, not silently dropped. Summary failure leaves full history and a truthful failed request; no fabricated fallback memory. Compaction uses the configured provider with `tools=[]` too. The existing auxiliary-client provider retries/fallback policy is unchanged; no strict end-to-end provider deadline is claimed.

For isolated UI QA with two task threads and a paginated free thread:

```sh
python qa_server.py --seed --seed-conversations --account --port 9138
```

Fixtures are explicitly labeled synthetic; no provider is called to seed them. All task, session, event, token and conversation paths are pinned inside the supplied scratch child, independent of production environment overrides. `--seed-conversations` requires `--seed` and cannot be combined with a copied task database.

For an exercised async API smoke (synthetic provider by default):

```sh
python conversation_smoke.py
# Explicit real configured model call, synthetic question/store only; may incur cost:
python conversation_smoke.py --real-provider
```

The report contains only status, counts and scratch request identities, never credentials or model text. A failed real-provider call is reported as failed, not replaced with synthetic success. Packaging the optional immutable image must include `conversations.py` alongside the existing server Python modules. Parent deployment remains responsible for the existing private configuration, volume ownership/backups and reviewed image/bind-mounted code update.
