# Independent task-presentation golden checks

All files and transient build output stay in this directory. No API calls, credentials, device, or task database are used. Client policies are compiled/imported **read-only**. Nothing is committed.

## Run

From the repository root:

```sh
# Validate pinned expectations using an independent stdlib Python specification.
python3 qa/task-presentation/verify.py --client oracle

# Verify the real browser module; optionally repeat against the packaged module.
python3 qa/task-presentation/verify.py --client web
python3 qa/task-presentation/verify.py --client web --web-module server/web_dist/policy.mjs

# Verify both clients. Requires Node, Python 3.9+ with IANA tzdata, a JDK,
# and a REAL executable JVM org.json library (not SDK android.jar stubs).
JAVA_HOME=/path/to/jdk JSON_JAR=/path/to/json.jar \
  python3 qa/task-presentation/verify.py --client both

# Verifier self-tests and real adapter shape/palette smoke tests.
JAVA_HOME=/path/to/jdk JSON_JAR=/path/to/json.jar \
  python3 -m unittest discover -s qa/task-presentation -p 'test_*.py' -v
```

The machine used for initial verification already had these dependencies; no installation/download was performed:

- `JAVA_HOME=/opt/data/cache/android-build/jdk-17.0.2`
- `JSON_JAR=/opt/data/cache/scratch/native-clean-ci-23uobitn/build/test-lib/json.jar`

Do not rely on that scratch jar being permanent. CI should provide its own real `org.json` jar through `--json-jar` or `JSON_JAR`. JVM classes are compiled in ignored `.build/` subdirectories and removed after execution. Missing dependency/module/method, process error, absent result, unexpected result, or mismatched golden value fails verification; clients are never silently skipped by the CLI. Exit status: 0 pass, 1 client mismatch/error, 2 fixture/oracle disagreement. The Android *unittest smoke* is explicitly skipped if JSON_JAR is absent; that is not a cross-client acceptance pass.

## Independence and coverage

`golden.json` pins expectations, not outputs captured from either implementation. `verify.py` contains a separate Python specification with strict date/timestamp validation, local calendar arithmetic, integer unsigned UTF-8 FNV-1a, and a complete ordering tuple. The oracle first checks every pin. Both actual clients are then checked independently against the same expected result, **not against one another**. Thus agreement between two buggy clients does not pass.

- 8 fixed instant/timezone contexts, including local midnight, Tokyo, spring DST jump, and both occurrences of a fall-back local hour.
- 17 project cases covering all six slots and neutral; case/whitespace preservation, canonical ID precedence, rename stability, null/empty fallback, composed/decomposed Unicode, CJK, astral characters, unsigned-overflow vectors, and exact light/dark hex colors.
- 36 component/composite score cases: all deadline buckets, strict invalid/missing/null dates, importance, progress, exact boolean canonical unread, full calendar weeks/cap/future/invalid/naive creation, wrong-key rejection, and timezone/DST boundaries.
- 11 full-dataset ordering cases: cross-project actionable ranking, waiting lower, completed/cancelled and trash scope, earliest-due tie, higher-importance equal-score tie, instant (not lexical offset) creation ordering, missing creation last, canonical-ID ties including UTF-16 ordering, and invalid dates excluded from Today.

Both adapters also rerun every order with reversed arrival order and reject canonical input mutation. They do not implement substitute client policy. Scores are raw components; eligibility/status buckets belong to selection/comparison, not score arithmetic. The `importance_tie` fixture exercises ordinary high-vs-low ranking; `equal_score_higher_importance` is the actual equal-primary importance tie.

### Production interfaces exercised

Browser `server/web/policy.mjs`:

- `projectPalette(task)` -> `{index:null|0..5, light, dark}`
- `smartScore(task, today, timezone)` -> number (the third argument allows a timezone-aware implementation; JS implementations accepting two arguments still run and their behavior is tested)
- `selectTasks(tasks, {view, sort:'smart', direction:'asc', today, timezone})` -> ordered tasks

Android `android/src/com/simon/mytasks/PresentationPolicy.java`:

- `projectSlot(JSONObject)` and `taskColor(JSONObject, "Project", dark, neutral)`
- `smartScore(JSONObject, LocalDate, ZoneId)`
- `compare(JSONObject, JSONObject, "Smart", false, LocalDate, ZoneId)`
- `matches(JSONObject, view, "", "", LocalDate)`

`GoldenAdapter.java` compiles the **actual** PresentationPolicy and Core source with real org.json. Reflection gives an explicit missing-interface error for independently implemented Smart entry points; it never falls back to a fake implementation. `web_adapter.mjs` imports real side-effect-free exports, without loading DOM/app/storage code. Context date conversion in these adapters checks the harness's fixed clock wiring; it does **not** prove that live UI code passes the correct clock/timezone or ranks before UI pagination/grouping. Device/UI acceptance remains separate.

## Canonical schema findings / integration cautions

Inspected source only (never imported canonical storage modules and never opened task data):

- `/opt/data/personal-assistant/taskdb.py`, especially `validate_task`, `valid_stamp`, and `validate_db`.
- `/opt/data/plugins/personal-tasks/dashboard/plugin_api.py`, especially task projection/notification and editable-field validation.
- Repository `server/CONTRACT.md`, `server/mobile_api.py`, both client policy files, Android tests, and browser app wiring.

Canonical importance is **`priority`**, not `importance`. Deadline is **`due_date`** (`YYYY-MM-DD` or null). Creation is **`created_at`**; the canonical store requires a timezone-bearing stamp with the Europe/Rome offset appropriate to its instant. `notification.unread` is added by the API projection; top-level `unread` is not a score source. Soft deletion is API-side `deleted`. Terminal canonical statuses are `completed` and `cancelled`; actionable statuses are `inbox`, `todo`, `in_progress`; `waiting` is separate.

**There is no canonical `project_id` in the inspected schema.** `validate_task` rejects unknown fields except `action_key`; `project` is string/null. Therefore ID-precedence vectors are forward-compatible presentation-policy probes, not valid storage/create payloads, and must not be inserted into the canonical store. Today, exact `project` is the usable identity. Likewise incomplete/invalid fixtures are intentional defensive-policy probes, not complete writable Task objects. Do not infer permission to mutate storage from these tests.

The store currently fixes DB timezone to **Europe/Rome**; API list metadata includes `today`/`week_end`, not a per-user timezone preference. The policy fixtures deliberately cover user-local zones independently. UI/API timezone ownership must be resolved by the integrating agents; this suite does not invent a backend preference endpoint.

Age interpretation is explicitly pinned: convert created_at to the user zone and compare local dates, then floor days/7. It is not elapsed milliseconds since UTC midnight. This distinction drives the local-week and DST vectors.

## Initial actual verification snapshot

Verifier self-tests: **7 passed**, including both real client adapter smoke tests. Oracle: **8 contexts, 17 palette vectors, 36 scores, 11 orders passed**. Every project slot and both-theme color checked passed in both clients. Full cross-client acceptance was **not green**:

- Browser source and packaged policy each failed 9 expectations: Today included invalid `2026-02-30`; calendar-week age undercounted for the exact-week/local-creation and four DST probes; invalid calendar creation and timezone-naive creation gained age. The exact IDs appear in CLI diagnostics.
- Android failed 2 expectations: Trash ranked completed above actionable, and string `notification.unread: "true"` earned the canonical boolean-only +6.

These are observations of the independently edited source at execution time, not claims about eventual final integration. No production files were changed to fix them. Run the commands again after policy owners make fixes. Passing unittest smoke tests alone does not mean full golden acceptance passed.
