# Restricted public web backend — owner brief 2026-11-10

This directory retains intermediate research/routing evidence. The v1.6 pre-release review additionally fixed failed-turn history isolation and the total synthesis-context budget; current verification is in `qa/releases/v1.6/README.md` (97 backend tests). Historical RED logs retain their failure results, with trailing whitespace normalized for Git publication.

## Delivered scope

Only Python backend/tests and this verification directory were changed by the backend worker. No Android files, web assets, QA transport helpers, deployed-tree files, credentials or provider settings were changed; no service was restarted and no commit was made.

Production files:
- `server/conversations.py`: normal-answer integration, additive request progress, fenced progress writes, nullable SQLite migration.
- `server/hermes_chat.py`: existing configured auxiliary-provider integration now runs inside a killable, fixed Python worker. This is not a terminal/agent tool: the model receives only the caller's allowed schemas, and no arbitrary command is executed.
- `server/public_web.py` (new): isolated public researcher, existing Hermes search-provider adapter, pinned public-only page transport, budgets, privacy/routing guards, source/failure presentation.

Verification files:
- `server/tests/test_public_web.py` (new)
- `server/tests/test_conversations.py` (extended)
- `server/public_web_smoke.py` (new, opt-in real-provider smoke)
- `qa/backend-public-web/real-smoke.json`
- `qa/backend-public-web/test-results.txt`
- this README (and web hash manifest when present).

## Runtime contract for integration owner / Android worker

The existing request objects gain only **`web_phase`**, one of:
- `"searching"`: actual configured search worker running;
- `"reading"`: actual public page worker running, including DNS/TLS/read;
- `"answering"`: provider reasoning/final answer, not a network-search receipt;
- `null`: no public-web phase / queued / any terminal state.

Existing `status` values, admission body, HTTP status codes, request identity, routing, revision semantics, messages, cursors and recovery remain unchanged. Old clients can ignore this additive field. No web JS or appearance changes are needed. Map searching to Searching, reading to Reading sources, and answering to ordinary Responding; do not claim either search state for a greeting. Phase writes do not bump conversation revision. Phase is cleared on finish/restart and updates are fenced to the owning active runtime.

**Staging packaging action for parent:** include new `server/public_web.py` in the Linux QA/runtime module allowlist, plus updated `conversations.py` and `hermes_chat.py`. The optional smoke script need not be deployed. No QA helpers were modified here. The existing fixture that patches `hermes_chat.call_llm` and insists on `tools=[]` remains compatible for nonresearch messages, greetings and summaries. A synthetic provider that rejects all nonempty tools must not be represented as a real search-provider acceptance test.

## Allowed execution and privacy

- Reuses the actual configured Hermes auxiliary provider and existing `tools.web_tools.web_search_tool`; never instantiates an unrestricted Hermes agent or session database.
- Server forces initial search for recognized explicit search/current-information/evidence requests. Greetings, ordinary writing/explanations, explicit no-browse and supplied-context requests bypass research.
- Research receives **only the latest short public request**. Saved task context, history/rolling memory, private email/calendar context and final-answer context never enter its tool-capable model calls.
- Conservative privacy guards reject personal/context requests, email addresses, phone-like numbers, credential markers/long tokens, direct user URLs, and oversized requests. Rejection produces an explicit privacy notice; it does not silently pass private text to search. Refined follow-up queries may contain only original-request words plus a small fixed public vocabulary. Content cannot introduce arbitrary new query words.
- Researcher exposes only `public_search` and `public_page`. Unsupported names, malformed/extra arguments or excess tool calls fail closed. Pages must be in returned-search URL candidates; models cannot request arbitrary URLs. Final synthesis and rolling summaries always use `tools=[]` and reject unsolicited tool calls.
- Results/pages are labelled untrusted, never authority to change tool policy. Model-generated citation URLs not in the retrieved source set are replaced with `[unverified URL omitted]`. A deterministic footer differentiates search snippets from actual page reads; deterministic failure text survives even if model prose omits it.

## Network boundary and limits

- Public HTTP/HTTPS only, default ports only, no userinfo, control characters, credential-like query keys, proxy environment, custom model headers, cookies, auth, referrer, JS execution or downloads.
- Validate every DNS answer, reject the whole answer set if any address is nonpublic. Explicitly reject loopback/private/link-local/metadata/CGNAT/reserved/multicast addresses and IPv6 translation/tunnel prefixes.
- TCP connects to the exact validated IP. Original hostname is retained for Host and normal certificate/SNI verification. There is no second hostname lookup during connect, preventing DNS rebinding between validation and connection.
- Follow at most three redirects, validating/pinning every destination anew. No automatic redirect mechanism can bypass policy.
- Page worker wall-clock limit 15 seconds; socket timeout 6 seconds; 256,000 response bytes; 8,000 extracted text characters; HTML/plain text/XHTML only; compressed/oversized/binary content rejected. Scripts/styles/templates are excluded. Redirect chains share the worker deadline.
- Search worker wall-clock limit 45 seconds; at most four validated results per search, titles capped at 200 and snippets at 1,500 characters.
- Initial search plus at most three extra tool calls, of which at most one is another search and at most two are page reads. At most three tool-capable provider rounds and one tool-free final answer. Research/synthesis budget 145 seconds with at most a one-second final-call floor if exhausted. Provider calls have a real subprocess wall-clock limit, including configured-provider queuing/retries; normal nonresearch answers are capped at 45 seconds, summaries at 90 seconds.
- Exceptions/configuration are never printed from workers or returned to clients. Worker stdout carries only bounded response envelopes. Killing a local provider worker cannot cancel an already-dispatched upstream request; durable state still forbids automatic replay.

## Verification

Run from `server/` (the existing suite's subprocesses require this working directory):

```sh
python -m unittest discover -s tests -v
python public_web_smoke.py --output ../qa/backend-public-web/real-smoke.json
```

Final full suite: **89 tests passed**. Saved actual output is in `test-results.txt`. `git diff --check -- server` passed. All **17 tracked web/web_dist files were compared byte-for-byte against HEAD and were unchanged**, including CSS, JS, templates, fonts, icons and manifest.

Coverage includes public/private/metadata/mixed DNS, numeric/mapped/tunnel/multicast addresses, redirect-to-loopback rejection, exact IP connect plus original-host TLS verification, byte caps, worker deadlines, real configured-provider timeout boundary, content stripping, privacy/no-browse/writing/greeting routing, tool-free summaries, unlisted-page rejection, injected query/prompt instructions, unsupported tools, bounded calls, honest failure and unknown citation removal. Conversation integration proves source text persistence, replay without another search/model invocation, reopen and fresh-process citation readback. Existing admission/restart/fencing/history/authentication/canonical-task/web regression tests all pass.

The real smoke uses disposable scratch SQLite only, with the actual configured `openai-codex` provider, real configured public search, and real pinned HTTPS page retrieval. `real-smoke.json` records exact queries, source URLs, page sizes, durations, tool schemas, phase observations, replies, replay checks and reopen checks. It separately labels a deliberately **injected search failure** while retaining a real final model call; that is not claimed as an actual external outage. Greeting and summary cases verify zero search/page calls and `tools=[]` with real provider output. The smoke starts no service and touches no production task/conversation store.

## Remaining limitations / handoff

- Reachability, deployment and actual Android end-to-end UI acceptance are the parent's responsibility and were not attempted here.
- Search content/provider results vary and a public source can fail or exceed the byte/time limit. The implementation discloses failures and distinguishes snippet-only evidence; exploratory real runs exercised this behavior. It cannot promise every site or arbitrary current question succeeds.
- Pages requiring JS, login, nondefault ports, compressed transfer, PDF or larger responses are intentionally unavailable. Arbitrary URL browsing and unrestricted link crawling are intentionally unsupported.
- Routing/privacy detection is conservative and heuristic, not a universal multilingual PII detector. Some valid personal/contextual requests deliberately get no research; ask again with a short non-personal public topic. Saved private context is structurally absent from the research path regardless of that heuristic.
- Source allowlisting verifies retrieval provenance, not factual truth. Untrusted web content can be inaccurate; final natural-language synthesis still depends on model behavior. No independent third-party security review or live production load test was performed.
