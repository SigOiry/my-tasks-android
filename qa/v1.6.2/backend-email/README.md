# Backend email workspace verification (v1.6.2)

## Integration

- Constructor: `EmailWorkspace(path, task_loader, provider=None, call_llm=None)`.
- Parent owns path separation preflight, lifecycle (`start()` once, `close()` on shutdown), task loader, and canonical sync.
- `email_routes.install_routes(app, workspace)` supports the root FastAPI app (adds `/api` prefix, inherits host authentication/Origin/CSRF/no-store middleware) and an already-authenticated APIRouter (relative routes). Installer does not start workers or alter auth.
- Loader's validated `email_origins` trigger refs take precedence over accumulated `source_references`. Missing provenance never asserts causality, even for one legacy candidate. Thread references expand exact incoming messages, requiring selection; no newest-message inference.
- Source IDs implement the shared SHA-256 formula verbatim. Public responses omit raw references and internal provider identifiers.
- Default Gmail adapter lazily reuses the private Google skill's `google_api.build_service('gmail','v1')`; configured HTTP transport has a 30-second socket timeout and send uses `num_retries=0`.
- Default Nantes adapter loads the existing private Himalaya account. `HIMALAYA_CONFIG` is an optional server-side path override; otherwise the known private integration path and runtime-home fallback are used. Only the installed `cat PRIVATE_FILE` password-command form is supported, read directly without executing a shell. No credentials reach responses, model context, or repository files.
- Injected provider protocol: `resolve(reference) -> [exact message dict]`, `read(reference) -> message`, `send(attempt, raw_mime) -> transport receipt`, `reconcile(attempt) -> verified receipt | None`. Fixtures exercise actual SQLite and API routes. Adapters validate exact account, headers, RFC identity, Gmail thread, or Nantes UIDVALIDITY/UID/Message-ID.
- AI injection uses the configured `call_llm(**kwargs)` response shape, `tools=[]`, bounded UTF-8 context, and bounded daemon worker admission. Production configured model wrapper has its existing killable subprocess timeout; injected models are terminal-status fenced after the workspace timeout, and cannot auto-apply a late result.

## Executed RED → GREEN tracer bullets

Each listed failure was observed before its implementation/fix, with the earlier slices rerun afterwards. Supplemental adversarial tests also exercise previously implemented paths.

| Slice | Observed RED | GREEN behavior |
|---|---|---|
| Source → persisted draft → save/reopen | Missing workspace module | Actual store/API and CAS |
| Validated update provenance | Two historical rows instead of one trigger | Trigger-only precedence; explicit Reply-all refresh/discard |
| Explicit send review | Preview route 404 | Fresh fingerprint, one-use confirmation, async send and verified readback |
| Contextual assistant | Assist route 404 | Durable jobs/chat/proposals, explicit apply/dismiss |
| Gmail transport | Constructor lacked transport injection | Exact-account messages/thread candidates, raw MIME/threadId, receipt readback |
| Nantes transport | Exact Nantes ref rejected | Read-only EXAMINE/PEEK + UIDVALIDITY/UID/Message-ID; SMTP and sent-copy verification |
| Root integration | `/api` source route 404 | Root FastAPI prefix; empty manual-task sources without mailbox lookup |
| Crash recovery | AI job remained running | Interrupted AI, uncertain send, no automatic retries |
| Strict confirmation | Integer `1` admitted as true | Only a literal JSON boolean true authorizes send |
| Self-sent exact refs | External Reply-To remained replyable | No reply target inferred from a self-sent source |
| Private storage | Foreign database accepted | Foreign schemas and symlinks refused without rewriting foreign data |
| Shutdown | Save still accepted after close | New mutations fail 503 |
| Concurrent assistant order | Older completion replaced latest request | Latest request follows admission, not completion |
| Unicode context bound | 175551 UTF-8 bytes exceeded budget | Serialized model context bounded, task/body/source excerpts explicit |
| HTML-only email / long readback | Empty context | Text conversion strips active content; large sent body verified |
| Reply-all participant roles | Original To was moved to Cc | Original To retained in To, original Cc in Cc, own identities excluded |
| Empty editor readiness | Empty draft `can_send=true` | Empty/blocked/non-draft workspaces cannot send |
| Durable proposal lifecycle | GET job still reported pending after Apply | Job, draft chat and proposal agree on lifecycle |
| Acknowledged send / failed readback | Transport receipt absent in persisted attempt | Acknowledgement retained for read-only reconciliation after restart |
| RFC References fallback | Ancestor In-Reply-To omitted | Preserve original References or single-parent fallback before original Message-ID |
| Encoded original subject | RFC2047 text not decoded | Unicode original subject preserved with successful threaded readback |
| Unavailable exact trigger | Trigger relabelled historical | Provenance retained, unavailable reason sanitized, no rebind |

## Final executed results

- Email-specific suite: **23 tests passed**, `python -W error::ResourceWarning -m unittest discover -s tests -p 'test_email_*.py' -v`.
- Complete backend suite at the shared working tree: **161 tests passed**, `python -W error::ResourceWarning -m unittest discover -s tests -v`.
- Owned modules/tests pass `python -m py_compile`.
- No commits, deployments, publication, or live sends were performed by this implementation.

## Safety and coverage

- No live email was sent. The only socket-level SMTP delivery is to a temporary `127.0.0.1` synthetic SMTP fixture, closed after testing. Gmail and IMAP fixtures remain entirely local.
- Real host middleware tests cover every route: auth denial, cookie Origin/CSRF, private no-store responses; source lookup/draft creation leave scratch canonical task bytes unchanged.
- Additional assertions cover stale notification tokens, strict unknown-field rejection, task/source membership removal, ambiguous exact-message candidates, multi-account separation, late thread changes invalidating review, no auto-apply, stale proposal conflicts preserving human writing, duplicate AI replay, bounded timeout, six concurrent duplicate sends yielding one transport call, six CAS writers yielding one winner, persistent uncertain receipts, read-only reconciliation, and malformed Unicode rejection.
- Real-account read-only auth was established by the parent before this implementation; these tests do not substitute for a live sent-message proof. Live sending was deliberately not tested.
- See `email-tests.txt` and `backend-tests.txt` for final command output.
