# Private saved knowledge — Discussions and task-linked chats

The maintained server bridge queries the existing private derived SQLite index.
Canonical task JSON remains authoritative; the index is not a second task store.
No canonical tasks, mail, databases, credentials or private conversation data are
published. The preexisting task-linked integration is retained and extended.

## Routing and API

- Free Discussions can ask `Search saved mail for reimbursement authorization`,
  `Find saved task Budget`, or an equivalent French request without selecting a
  task. Deterministic saved/mail/task/index intent routes before public research.
- The durable message API also accepts optional `search_scope: "private"` to
  explicitly choose private evidence retrieval for any wording. The default is
  `"auto"`; older clients do not need a new field. There is deliberately no public
  override for a private thread. Scope is persisted and is part of request-ID
  identity: changing scope on replay is409, not new work.
- Task-linked threads are always private/tool-free, including public-looking
  questions. In free Discussions, a private request, prior privacy-guarded input,
  or saved-task/mail receipt permanently disables public research for that thread,
  including failed/no-match requests, summaries, reopen and service restart.
  Start a **new free Discussion** for unrelated public research. This conservative
  policy prevents copying retrieved private facts into follow-up public queries.
- Privacy classification is independent of whether the current turn needs tools.
  Supplied transformation operands (rewrite, translate, summarize, edit, etc.)
  persist private-thread status **by input class**, without relying on recognized
  sensitive words. Quotes/delimiters, indirect wording, gerunds, French wording,
  and missing/ambiguous delimiters all fail closed. Even `Rewrite: The moon is
  bright.` is private: innocuous-looking supplied prose is not authorization to
  publish its later follow-ups. A public URL alongside supplied prose is not an
  escape; a genuinely direct eligible single-URL transformation retains the
  existing pinned read-only public research path.
- Explicit no-browse requests persist local-only thread intent, not merely a
  one-turn opt-out. This includes `Do not browse. What are email protocols?`:
  the public subject does not override the user's conservative privacy choice.
  These flags survive restart and are re-derived from historical requests too.
  Local-only forbids all public search/read I/O, not configured model inference.
- Ordinary greetings/social/capability requests and unambiguously fresh generation
  such as `Write a poem about the moon` do not alone privatize the thread; their
  subsequent unrelated public questions remain research-eligible.
- Ordinary public questions in a fresh free Discussion retain existing bounded
  public research. The restricted public researcher receives only the authorized
  current public request and public evidence, never saved context or history.
  Final synthesis is tool-free. No model-generated routing/search query can
  authorize private retrieval or bypass this boundary.
- Only the configured context-capable model provider sees private answer/summary/
  title context, with `tools=[]`. Private does **not** mean offline inference.
  There is no mailbox access, sending, task mutation or general agent tool access.
  Legacy `/api/chat` stays canonical-context-only and rejects deleted tasks404.

## Read-only retrieval and budgets

- `task_knowledge_context.py` implements the repository-owned query directly;
  production requests no longer dynamically import the outside-Git index module.
  The indexer's schema/application identity and canonical fingerprint encoding are
  checked. `/opt/data/personal-assistant/task_knowledge.py` is unchanged and remains
  the separate refresh CLI. Default index:
  `<canonical task directory>/knowledge/knowledge.sqlite`.
- SQLite is opened with `mode=ro`. Queries never create/refresh the index or fetch
  mail. Missing, corrupt, symlinked or incompatible indexes yield no extra evidence,
  not a public-search fallback. Keep directory0700/file0600 on the private volume.
- Global lexical FTS5/BM25 search uses at most24 quoted terms, scoped inside the SQL
  query to **current nondeleted canonical task IDs**, before the64-candidate limit.
  Scan rows additionally require exact current saved-reference membership; message
  authorization never expands to thread siblings. Canonical excerpts additionally
  require their saved canonical hash to match the current task including dashboard
  notes. Removed tasks, revoked references and stale canonical excerpts cannot
  crowd out authorized matches. No subject/person/account heuristics exist.
- At most4 excerpts and5000 complete JSON characters reach the model in total,
  not per task. Matched tasks also contribute at most6000 total JSON characters of
  current task fields (titles may be excerpted); these supersede historical mail.
  Task-linked retrieval keeps its small recent fallback; global search has **no
  unrelated-recent fallback**. Lexical misses, unavailable index data and incomplete
  saved coverage are not proof a fact does not exist.
- Existing full-history storage, summary/recent-message budgets and44000-codepoint
  final model-input cap remain. Character bounds are not provider-tokenizer claims.
  Provider output limits are1800 tokens for replies and1400 for summaries.

## Revocation, receipts and replay

- Each request saves a private authorization scope containing only exact source
  membership/fingerprints for supplied excerpts and current task context, not an
  additional copy of retrieved mail. Re-read canonical authority before history
  compaction, before each summary call, before summary persistence, before normal
  answering, and before answer persistence. Queued titles and title output use the
  same fence. Deletion/revocation during a call stops later calls and drops output.
  Legacy v1 receipts without scopes still validate task existence and opaque email
  source IDs against exact current references.
- Canonical writes are outside the conversation SQLite transaction: these are
  repeated current-state checks, not a cross-database atomic locking guarantee.
  Old originals/receipts remain readable and exact request-ID replay remains
  nonexecuting even after revocation. A thread whose history contains revoked or
  stale indexed canonical evidence fails closed for **new model work**; start a
  new Discussion after canonical changes rather than silently resummarizing it.
- Original assistant prose and `citation_metadata.version=1` receipts are persisted
  together in the existing SQLite transaction. Sources remain `task`, `saved_email`
  and `web`; private receipts carry task/resource IDs and supplied excerpt path,
  timestamps, hashes and chunk offset, never fabricated public URLs. Request
  replay/reopen returns the original saved metadata without retrieval/provider IO.
- Public synthesis is instructed to link researched claims as explicit Markdown
  `[relevant claim/label](exact supplied URL)`, never provider citation tokens.
  Backend prose is immutable: no guessed anchor offsets or rewritten content.
  Android can link corresponding safe explicit URLs/Markdown labels against web
  receipts; unanchored receipts belong in its context-menu fallback. Related saved
  tasks/mail use authenticated resource IDs, not answer footers.

## Verification and parent deployment

```sh
cd /opt/data/workspace/my-tasks-android/server
/opt/hermes/.venv/bin/python -W error::ResourceWarning -m unittest discover -s tests -v
cd /opt/data/personal-assistant
/opt/hermes/.venv/bin/python -W error::ResourceWarning -m unittest test_task_knowledge -v
```

The tests exercise real synthetic SQLite, canonical files, authenticated API,
durable asynchronous workers, authorization races, receipts and nonexecuting
replay. Only provider/network boundaries are synthetic. No production mutation,
restart, deployment or outbound LLM verification is performed by these tests.

The parent deployment owns consistent private backups, pending-work checks and
selective deployment of `conversations.py`, `mobile_api.py`,
`task_knowledge_context.py`, and `public_web.py` with their existing dependencies,
then restarting **only the existing My Tasks service through Dockge**. Preserve
private databases, credentials and existing web assets. The private conversation
DB gains additive `search_scope`, `private_search`, and `private_scope` request
columns on startup; no task/index migration is performed. Verify deployment
packaging includes the bridge (the historical optional Dockerfile copy list is
incomplete), authenticated behavior and nonexecuting replay. Index refresh remains
a separate reviewed CLI/sync-workflow operation; requests do not trigger refresh.
