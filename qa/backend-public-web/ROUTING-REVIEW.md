# Backend public-web routing review — 2026-11-10

This review changes only `server/public_web.py`, `server/tests/test_public_web.py` and new artifacts in this directory. No deployment, service restart, production conversation/model request, web/Android edit, provider setting change, or commit was performed.

## Fixed with observed RED → GREEN tests

1. **Implicit mutable-information requests skipped retrieval.** The exact owner question returned `research_decision == 'off'`. Added opening/business/store/operating hours, bounded when/what-time open/close questions, open/closed/availability questions, plural prices/costs, stock, common price questions without the word price, and common stable/supported/version questions. The server's mandatory initial search now runs for the exact public business question. Existing privacy/writing/opt-out precedence remains intact.
2. **Opt-out could leak the public question to external search.** Curly-apostrophe `Don’t browse`, `Do not access the internet`, `Do not use online sources`, and `No online research` were not recognized. These variants now return `off` and make zero search calls. This changes no tool allowlist or network authority.
3. **Research-provider timeout/unavailability discarded retrieved evidence.** A `TimeoutError` or `RuntimeError` from the tool-capable research call previously escaped to the durable caller and failed the request. The research phase now stops, records a sanitized deterministic disclosure, preserves already-retrieved sources, and attempts the ordinary tool-free final synthesis within the same deadline. It does not rerun retrieval. Unsupported tools/arguments/excess budgets still fail closed; a failed final provider call still produces the existing saved, non-auto-retried failure.

Evidence: `routing-red.txt`, `routing-green.txt`, `boundaries-red.txt`, `prices-versions-red.txt`, and final `review-test-results.txt`. Pytest is not installed in this environment; the repository's unittest runner was used. Final command from `server/`: `python -m unittest discover -s tests -v` — **93 passed in 64.067 seconds**. `git diff --check -- server/public_web.py server/tests/test_public_web.py` passed (these new files remain untracked, as received).

## Actual configured-provider opening-hours result

Reproducible opt-in harness: `opening-hours-smoke.py`.
Final actual evidence: `opening-hours-real-smoke.json` (includes exact query, returned snippets, attempted/read source URLs, full bounded page text, provider route/tool schemas/calls, phases, reply, replay and reopen checks).

- Request: `What are the opening hours of tabaccheria N 196, Via di Prosecco 15, Trieste?`
- Final run started **2026-11-10 14:04:09 UTC**; actual configured **openai-codex**; **44.488 seconds**; succeeded.
- One real search, two page attempts. First source unavailable; second page read succeeded. The actual final answer deterministically disclosed the failed source and explicitly identified third-party—not shop-confirmed—hours.
- Successfully read source: `https://mapdoor.com/it/fri/trieste/ups/via-di-prosecco`.
- Failed page attempt: `https://www.oraridiapertura24.it/filiale/Trieste-Tabacchi%2520196-1691967O.html`.
- Mapdoor's page identifies **Tabacchi Giornali Cancelleria 196, Via Di Prosecco 15**, and lists **Monday–Saturday 07:00–13:00, 16:30–19:30; Sunday closed**. The final response includes that actual URL, snippet/page provenance labels, and uncertainty about holiday/temporary changes.
- An additional GLS search snippet (`https://www.cybertip.eu/en/gls/trieste-via-di-prosecco-15-tabacchi-196-p581556`) collapses weekday hours to 07:00–19:30. That page was **not read**; it does not independently verify continuous opening or remove the lunch break. This is a real source-quality limitation, not a test failure or evidence of shop confirmation.
- Scratch SQLite only, created under `/opt/data/cache/scratch` and removed afterward; no service started. Exact accepted-request replay made **zero additional model/search/page calls**. A reopened scratch store returned the exact same assistant message/citations. No existing chat was used.

## Security/durability review and remaining defects/limits

The inspected boundary retains pinned public DNS/IP connections, original-host TLS checks, redirect revalidation, byte/time/round limits, search-result URL allowlisting, original-public-query-only researcher input, restricted query words, unsupported-tool rejection, and tool-free synthesis/summaries. Existing tests covering these protections and durable citations/replay all pass. This is not a claim of exhaustive security assurance.

Remaining limitations:
- Classifier and privacy detection are heuristic, not comprehensive semantic or multilingual classification/PII detection; paraphrases may still miss or overroute. Private saved context is structurally excluded from the tool-capable researcher regardless of those heuristics.
- Natural-language synthesis still depends on model compliance; provenance checks establish actual retrieval, not factual truth or proof against all prompt injection. Web content never gains system/tool authority.
- Source availability varies. In this actual run one page failed and a snippet disagreed about the lunch break. No direct shop confirmation, holiday schedule, or live open-now guarantee was obtained.
- A failure of the final synthesis provider still results in the existing durable failed request rather than an evidence-only answer. Automatic replay remains forbidden.
- Deployment/reachability and Android/native acceptance remain with the parent; not exercised here.

`review-web-byte-manifest.json` independently verifies all **17 tracked `server/web` / `server/web_dist` files remain byte-identical to HEAD**.
