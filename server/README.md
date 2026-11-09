# Companion server and browser workspace

This directory contains the actual Android/browser API adapter and self-hosted browser UI. It is an integration with an existing Hermes **My Tasks** installation, not a second task database or a standalone general-purpose task server.

## Required existing integration

- Hermes Python runtime and configured model provider.
- Canonical `taskdb.py` and `tasks.json` under the configured personal-assistant directory.
- Existing compatible personal-tasks dashboard `plugin_api.py`, providing Store transactions, enriched tasks, revisions, source links and exact update acknowledgement.
- A private high-entropy mobile token file and writable event/session metadata directory.

No canonical tasks, mailbox/calendar data, runtime databases, deployment credentials, token files or signing keys are included here. The existing task DB and plugin are deliberately not initialized or replaced by deploying this adapter.

## Build and test

With the compatible integration available in its documented paths:

```sh
cd server
python build_web.py
node --test tests/web_policy.test.mjs
python -W error::ResourceWarning -m unittest discover -s tests -v
```

`web/` is the maintained source and `web_dist/` is the reproducibly built shipping bundle. UI behavior uses the real authenticated API. Fixtures exist only in tests and explicit loopback QA setup; no production demo data is embedded.

For isolated browser testing:

```sh
python qa_server.py --seed --port 9138
```

This creates a new private synthetic store and token inside scratch. It does not read the production task database or credential. Never run browser mutation tests against a real task store.

## Deployment

`compose.example.yaml` is an example, **not a copy of private deployment configuration**. Resolve the actual existing Docker volume and canonical helper path before use. Set `HERMES_DATA_VOLUME`, `TASKDB_HOST_PATH`, and a deliberate bind address; the example defaults to loopback. The code's default deployment layout uses the existing Hermes persistent home. Do not point it at a fresh competing task store.

Terminate trusted HTTPS at the intended origin. `MOBILE_WEB_ORIGIN` must match exactly; the application's default is `https://task.oirysimon.com`. Preserve Authorization and cookie headers, and allow upstream/read timeouts longer than205 seconds for model replies. Do not publish the raw HTTP port to the internet.

The browser uses a private opaque HttpOnly/Secure/SameSite session after token login. Android retains bearer authentication. Keep rate limiting, Origin/CSRF enforcement, revision conflicts and exact notification tokens intact. Source overlays are read-only; the service currently shares the same-user Hermes data volume to support existing provider credential refresh, and is not isolated from every credential accessible to that account.

See `WEB.md` for session expiry/revocation, browser privacy, CSP, notification limits and detailed QA. `CONTRACT.md` documents the API.

## User-visible limitations

- Browser notifications work while the page is open; closed-browser push is not implemented.
- Browser offline access is read-only page memory; no private task data is persisted for offline reload.
- Speech recognition availability and processing depend on the browser/device; a transcript is always reviewed before sending.
- Hermes only answers task-context questions and prepares drafts. No email is sent and no unrestricted agent tools are exposed.
