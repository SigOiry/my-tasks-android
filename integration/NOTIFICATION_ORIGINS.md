# Exact email notification provenance — v1.6.2

## Scope and envelope v1

This extension is **not installed in the live workflow** by the implementation/test
run. It adds only reviewed-envelope validation and root dashboard metadata. It
never changes `taskdb.py`, canonical candidate/task fields, snapshot projection,
notification acknowledgement tokens, provider data, flags, or messages.

Future reviewed envelopes may contain:

```json
{
  "notification_origins_version": 1,
  "notification_origins": {
    "report:review": [
      {
        "item_id": "gmail:reviewer@example.com:thread:abcd",
        "reference": "gmail:reviewer@example.com:message:1a23456789abcdef",
        "role": "trigger"
      }
    ]
  }
}
```

The version must be integer `1` (not boolean) whenever the origins mapping is
present. An empty mapping is valid. Both fields can be omitted by legacy reviewers;
**omission never infers an origin**. No historical backfill is performed. The
review template contains version 1 and an empty mapping, plus reviewer instructions.

- Keys are exact candidate `action_key`s, not task IDs or source IDs.
- Each entry contains exactly `item_id`, `reference`, and `role`.
- `trigger` means the reviewer identified this exact message as causing the update;
  `context` means supporting evidence, not a default reply target. Multiple exact
  triggers are permitted; context-only provenance is also permitted.
- An origin requires an explicit disposition **in this review**, even for an
  unchanged previously reviewed item: its hash must match the manifest item, its
  decision must be `action` or `existing`, and it must link this candidate key.
- The candidate must carry the exact reference in `source_reference` or
  `source_references`. Existing task references alone are insufficient.
- Gmail proof comes exclusively from this item's collector-produced
  `content.messages[].source_reference`, with matching message `id`, account,
  and Gmail source. A thread ID, quoted prose, evidence JSON, another item's
  messages, or the newest message in a thread is not proof.
- Nantes proof comes exclusively from this item's `content.source_reference`,
  matching its structured mailbox, UIDVALIDITY, and UID and Nantes source.
  The reference must include a nonempty RFC Message-ID in angle brackets.
- Limits: 128 action mappings, 32 origins per action, 512 origins overall,
  256 characters per action key, 2048 per item ID/reference; UID/UIDVALIDITY are
  positive 32-bit values. Duplicate references (including conflicting roles) fail.
- References must match the helper's conservative allowlisted syntax. Controls,
  CR/LF, encoded Gmail injection, incomplete IDs, thread refs, traversal folders,
  URL/command suffixes, unknown fields, unsupported versions, and malformed shapes
  fail closed before `reviewed.json` or any database write. Unusual legitimate
  mailbox/Message-ID syntax outside this v1 allowlist must use the source chooser;
  do not normalize or invent a substitute reference to make validation pass.

The collector manifest remains the existing workflow's trusted collection
boundary. This extension does not query a provider or claim independent provider
verification. Source text is untrusted, not reviewer authorization.

## Atomic persistence and consumer contract

Inside the existing `LockedStore._write`, under the original Store lock and in the
same atomic canonical write / byte-exact backup as tasks and workflow receipt:

```text
dashboard.notification_origins[TASK_ID] = {
  snapshot: SAME_EXISTING_NOTIFICATION_SNAPSHOT,
  run_id: RUN_ID,
  manifest_hash: REVIEW_MANIFEST_HASH,
  origins: [{reference, role, item_id, item_hash}, ...]
}
```

No extra canonical field or token input is added. This record is replaced only
when the meaningful snapshot changes, the candidate survives reconciliation, and
the existing manual-override/status block check accepts the incoming update.
Unchanged replay cannot change provenance behind an existing token. Missing origins,
suppressed tombstones, and blocked candidates attach nothing. Older records may
remain but become invalid when their snapshot no longer matches the current task.

Parent loader must expose `task['email_origins']` as **trigger reference strings
only**, and only after exact record/current snapshot equality. Do not expose
context references as triggers. Missing/stale/context-only provenance requires the
historical source chooser; never pick the primary source, newest email, a thread,
old disposition, or latest record by inference. The record means reviewed update
provenance, not authorization to send a reply.

## Parent integration (review + deployment lease required)

1. Review the three Python files and this document. Test only private scratch copies.
2. Parent pauses concurrent synchronization entrypoints and acquires the existing
   workflow command lock. Read canonical workflow status while holding that lock;
   **do not deploy during an active prepared/review lease**. Do not abort or replace
   another agent's lease. Keep synchronization excluded through deployment.
3. Preserve an owner-only byte-exact workflow backup for rollback. The installer
   intentionally does not touch task state, acquire a second DB lock, create a
   second state directory, or override any lease. Its caller owns concurrency.
4. Dry-run with an explicit target (no target/default path is assumed):

   ```sh
   python integration/install_notification_origins.py /PATH/TO/sync_workflow.py
   ```

   Importable equivalent: `install(path)` returns a dry-run report. The exact
   accepted original workflow SHA-256 is:
   `eeed8e6d039b03fc36b7a12d7a154ff12b684e7c683c2b270cbb5ec0f8634853`.
   Five uniquely matched code anchors are additionally checked. The complete
   installed source is recognized by reversing those exact patches and checking
   the same original digest. Any drift, including CRLF normalization, fails closed.
5. **Only parent, after review and lease check**, runs the same command with
   `--apply` (or `install(path, apply=True)`). Helper is copied next to the workflow
   first; workflow is then atomically replaced. Repeating is a no-op. Symlink
   targets and an existing nonidentical helper are rejected, not overwritten.
   Staging files use runtime `$TMPDIR`; it must be on the target filesystem for
   atomic `os.replace`. Failure after helper copy leaves old workflow operational;
   rerun safely after resolving the cause. This is not a two-file transaction.
6. Read back the exact deployed workflow/helper, verify hashes, syntax, status and
   permissions before releasing synchronization exclusion. Run isolated regression
   tests, not a live prepare/commit. Existing old review envelopes remain accepted.
   A future workflow source update needs deliberate anchor/digest review, not a
   guard bypass.

Installer report `changed` describes needed changes; `applied` is true only when
this invocation actually performed them. No production install was done here.

## Verification and TDD evidence

From repository root:

```sh
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s integration -p test_notification_origins.py -v
```

Tests patch a private copy of the **actual current** workflow and copy the real
unchanged Store code, initialize fresh private databases, and use synthetic local
collector fixtures (no network). Temporary directories/staging use `$TMPDIR`.
The original workflow's 24 tests are additionally executed in a private subprocess
against the patched workflow, with only read-only collector source modules copied
for the CLI import-before-lock path. No credentials/configuration/source dumps or
live task/state files are copied.

The final run passed **22 integration tests plus 24 original workflow tests**
(46 test executions, including the integration wrapper around the original suite).
Both suite counts and full original-test output are in `08-green.log`.

RED/GREEN logs: `qa/v1.6.2/provenance/01-red.log` through `08-green.log`.
They cover exact provenance/backup/replay, malformed-no-write validation, explicit
older-message selection, Gmail/Nantes cross-source roles and existing-thread update,
manual and terminal blocking, omission/stale fallback, source/hash/link boundaries,
bounded counts, tombstones, canonical-replace rollback, receipt-crash recovery,
review template, installer dry-run/idempotence/symlinks/digest drift, and the original
workflow regressions. RED includes an encoded-injection bug fixed by tightening the
account allowlist and a line-ending guard bug fixed by byte-preserving reads.
