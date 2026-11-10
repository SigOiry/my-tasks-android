# Shared-chat public internet follow-up — 2026-11-10

## Outcome and scope

Fixed maintained backend only. No deployment, restart, commit, version bump, production API/chat/task write, credential copy, provider-configuration change, Android edit or web presentation edit was performed by this worker. Other workers' Android changes remain untouched. All 17 tracked `server/web` and `server/web_dist` files were compared byte-for-byte to HEAD and are unchanged.

## Root cause evidence

At investigation start the deployed `public_web.py`, `conversations.py`, `hermes_chat.py` and `mobile_api.py` were byte-identical to maintained main e6ba7d9. Deployed `public_web.py` SHA-256 remains `eea4810349f0f6a19c1a312786f07eb64646a316a16dbcefb3711fd304f3ee65`. This was not an omitted deployment of the v1.6 implementation.

The original `research_decision` (lines 187–190 in that revision) required a small freshness/search keyword list before doing anything else, classified all URLs as private if they reached the second gate, and bypassed explanations even about explicitly mutable information. Accordingly ordinary factual questions and bare URLs went directly to a model call with `tools=[]`, without any network attempt:

- `Who won the 2026 Nobel Prize in Physics?`: deployed **off**, maintained **public**.
- `What is the capital of France?`: **off → public**.
- `Quelle est la capitale de la France ?`: **off → public**.
- `Peux-tu me dire qui a inventé le télescope ?`: **off → public**.
- `https://www.python.org`: **off → public**.
- `Summarize https://www.python.org/`: **off → public**.

Exact read-only deployed/maintained routing comparison and hashes: `general-access-routing.json`. The parent separately owns the Dockge/deployed provider evidence; it reported that actual deployed `search_public('latest stable Python release')` already returned four sources. This worker independently exercised the configured real providers against the maintained implementation (below).

The broad private regex also treated benign `my question:` framing and public subjects such as `public IP address` as private when retrieval intent was explicit. Only narrow non-personal framing/technical-subject exceptions were added; actual possessives, contact information, credentials and opaque long tokens remain guarded. Long words can still be rejected by the long-token rule: this is deliberately not removed without a reliable way to distinguish secrets.

The durable path is `/api/conversations/{id}/messages` → `ConversationStore._work` → `model_text` → `normal_answer`. It already passes **exact persisted `request['content']`**, separately from merged model history. That boundary was retained. Legacy `/api/chat` → `hermes_chat.answer` deliberately remains stateless/task-focused with `tools=[]`; current native contract tests confirm it is not the durable-chat transport. Provider calls still use the existing killable auxiliary subprocess and configured `agent.auxiliary_client.call_llm`; search still uses `tools.web_tools.web_search_tool`. Neither adapter nor provider configuration needed changing.

## Fix

- Route ordinary English/French public-question forms, including conversational forms, independently of the freshness-keyword list. Keep greetings, ordinary writing/translation/supplied-text summaries, explicit no-browse and summaries out of research. Extend French opt-out/writing recognition alongside expanded question routing.
- Separate forced search-worker/query validation from text routing with `force_search=True`, instead of manufacturing `Search ` prefixes that changed wrapper detection and effective length. It does **not** override privacy, no-browse or capability-only handling.
- Add one eligible direct HTTP(S) URL → one existing pinned public-page read → tool-free synthesis. Never send the URL to a search provider, run a tool-capable researcher for it, or crawl links inside it. Reject multiple URLs, userinfo, nondefault ports, queries/fragments, percent-encoded input, private/account paths, credential/opaque-token hosts/paths and non-HTTP schemes. Real DNS/all-address validation, public-IP pinning, TLS original-host verification and redirect checks remain in the existing reader, unchanged.
- Answer recognized capability-only questions truthfully and deterministically in English/French, without a model/network call or a claim that live retrieval occurred. Explain server-provided research in the ordinary model system prompt so tool-free synthesis is not confused with global lack of internet access.
- Expand guards for newly routable first-person and saved-history questions. A full-suite run exposed that `What was the first marker?` otherwise attempted an unnecessary live search; the added contextual guard and final no-unmocked-search suite prevent that regression.

No authority was added to unrestricted agents, files, task/calendar/email writes or private history. Existing search follow-up word allowlisting, unsupported-tool rejection, three tool rounds/three extra calls, page/search/time/byte/context caps, source receipts, tool-free final synthesis/rolling summaries and durable replay remain intact.

## RED → GREEN and verification

Regression tests were written and observed failing before each behavior change: general questions (11 failing subcases), privacy/framing and French opt-outs, direct URLs (14 failing subcases), deterministic capability replies (5 failures), capability-aware synthesis instructions, direct-read tool-free-only handling, URL worker disclosure, contextual privacy and worker-wrapper validation. The same tests pass after the fixes.

Final commands from `server/`:

```sh
python -m unittest discover -s tests -v
```

**107 passed in 66.226 seconds**; actual output: `general-access-test-results.txt`.

A second complete run globally rejected any otherwise-unmocked `public_web.search_public` invocation (explicit per-test mocks still apply): **107 passed in 62.384 seconds**. Actual output: `general-access-no-network-tests.txt`. This verifies the broader routing does not silently turn pre-existing private/contextual unit fixtures into live searches. `git diff --check -- server qa/backend-public-web` passed.

### Actual configured-provider smoke (not fabricated results)

```sh
python qa/backend-public-web/general-access-smoke.py
```

Final receipt: `general-access-real-smoke.json`, including exact maintained module hashes, actual provider routes, queries, source URLs, page sizes, replies, timing, persisted replay and reopen checks. Runtime uses existing configured **openai-codex** and the real search/page workers; patches only instrument the original functions, never return synthetic provider/network responses. A disposable scratch SQLite store is created and removed; no service is started.

Final run:

- English capital question: one actual search returning four URLs; **23.270 s**, succeeded. Snippet-only; no page read claimed.
- French capital question: one actual search returning four URLs; **21.501 s**, succeeded. Snippet-only; no page read claimed.
- Supplied Python homepage: **zero searches**, one real pinned HTTPS read, **7069 extracted characters**, one tool-free provider call; **13.433 s**, succeeded.
- All three: exact replay caused **zero additional provider/search/page calls**; reopening returned the exact persisted assistant message/citations.

Earlier exploratory runs also read an EU country page, but the final saved English/French receipts are explicitly snippet-only; they must not be presented as page verification. Source quality and availability vary. One exploratory direct-URL implementation allowed an unnecessary researcher follow-up, which produced an honest blocked-source notice; final direct URL synthesis now has no tool-capable phase and the final receipt has no such failed follow-up.

## Files and handoff

Production: `server/public_web.py`, `server/conversations.py` (system instructions only). Tests: `server/tests/test_public_web.py`, `server/tests/test_conversations.py` (natural factual question now exercises persisted-current-request isolation after a failed private turn). Documentation: `server/CONTRACT.md`, this report and the README follow-up pointer. New executable evidence/harness files are the five `general-access-*` files in this directory.

**Not deployed.** The deployed source still has the original hash above and therefore still has the old routing. Parent owns reviewed selective deployment and exact live readback, the deployed-provider/source harness, and Android/browser end-to-end acceptance. No release was requested or created. Heuristic routing/privacy is not a universal multilingual semantic classifier or PII detector; safe direct URLs remain intentionally narrower than arbitrary URLs, and sites requiring authentication, JS, PDFs, oversized responses or unsupported encodings remain unavailable.
