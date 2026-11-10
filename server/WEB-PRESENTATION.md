# Shared task presentation and coordinated web motion

This update extends (does not replace) the uncommitted Liquid Glass redesign. Authentication, task mutations, exact-token acknowledgement and conversation transport contracts are unchanged. No production data or deployment was touched.

## Presentation contract

- Project identity: exact nonempty `project_id` if provided (forward compatibility), otherwise exact `project`; UTF-8 FNV-1a unsigned 32-bit, modulo six. No trimming, case folding or locale hash. Missing projects use neutral Slate. Paired light/dark colors: Ocean `527FC2/8FB2E8`, Teal `3C8B83/82C8BA`, Violet `8972BB/BBA7E5`, Rose `B26D87/DFA2B9`, Amber `A57A35/D4B26F`, Slate `6C7E91/A6B7C9`.
- Color by Project / Importance / Neutral and sorting/direction use the existing `mytasks.web.*` local preference mechanism. No preference sync API exists or was introduced. Existing saved `priority`, `date`, and `title` keys remain understood; legacy priority/date retain their previous unread-first semantics. New Importance and Due date choices do not force unread tasks above their primary key.
- Smart defaults only when no valid saved sort exists. It compares the full eligible dataset, not project buckets or visible rows. Project sorting retains grouping; other new choices use a globally ranked list with quiet project context. Waiting follows actionable work, with explicit status labels; terminal records remain confined to the selected scope and follow waiting.
- Smart weights: overdue 50 / today 40 / tomorrow 25 / next seven days 10; high 30 / medium 15 / low 0; in progress 6; canonical unread 6; full user-local calendar days since creation divided by seven, capped at four. Due dates are strictly validated calendar dates. Creation/update instants require an explicit timezone and valid date/time. Current canonical workspace timezone is Europe/Rome, including DST; policy functions accept another timezone explicitly for interoperability tests.
- Ties are deadline ascending, importance descending, creation ascending, canonical task ID. Reverse affects the main sort key, not status buckets or tie-breaks. Why this order is in the filter panel, never repeated in ordinary rows.
- Routine refresh/reordering defers during pointer interaction, scrolling, open menus/dialogs and dirty edits. Render restores an existing visible task anchor and keyboard focus. Sorting never changes canonical task data.

## Motion and exact update identity

- Each newly observed exact task/update-token pair receives four 1600ms edge breaths, at most two rows simultaneously. Elapsed **visible active time** lives in `UpdateBreaths`, not row bindings. Rebinding uses the accumulated negative delay, not a fresh sequence. Offscreen/background effects pause; reduced motion is static. Persistent blue New update markers are independent of project/importance accents and stay until canonical exact-token review succeeds.
- Native task/chat dialogs use coordinated 280ms entry/reversed exit and scrim transitions on browsers supporting discrete display/overlay transitions. Older browsers retain the 280ms entry fallback and immediate native close/focus semantics.
- Task filters are a bounded top-right anchored panel, with 260ms entry and supported details-content exit, Escape/outside dismissal and trigger focus restoration. Destination content/navigation runs at 210ms. Reduced motion disables CSS and programmatic motion.
- Outgoing admission bubbles show Sending until durable admission, use only the client request ID (not an invented message ID), and are replaced by the saved server message. Uncertain delivery remains explicitly uncertain. Accepted outgoing/incoming insertions are local to new bubbles, not the transcript. Existing old-history node/scroll-anchor behavior is retained.
- The three-dot wave exists only for an actual running request in an online, visible, open conversation. Queued is labelled Queued; terminal/hidden states stop the effect. Reduced motion retains static status text/dots.

## Verification

- `node --test tests/web_*.test.mjs`: 101 passing, including new presentation/scheduler and admission/running-state checks, plus source/packaged nullable-key ordering in both directions.
- `python -m unittest discover -s tests -p 'test_*.py' -q` from server/: 72 passing. Running this command from the repository root without PYTHONPATH is not supported by the existing test imports.
- Shared independent `qa/task-presentation/verify.py --client web`: PASS for 8 contexts, 17 palette identities, 36 scores and 11 orders; the runner checks source and packaged modules. This caught and fixed invalid Today dates and timezone/DST/invalid timestamp age handling before packaging.
- `node --check web/app.js`, `web/policy.mjs`, and existing conversation module; reproducible bundle via `python build_web.py`.
- Real cookie-authenticated synthetic loopback browser QA only: existing task suite 16 checks; new `tests/browser_presentation.js` 10 checks (anchored layout, Escape/focus, full dataset Smart order, local preference persistence, slim accents, priority labels, overflow). 390x844 screenshots and older-history checks verified 48 saved messages, retained original message nodes and reachable composer. Browser reload read back selected sort/direction/color.
- Visible-browser CSS timing inspection measured two active 1.6s x4 animations and one paused batch member. Initial effects completed and the next began while all three canonical unread markers persisted; a mode-triggered row rebind continued using accumulated delay.

Private synthetic evidence is under `/opt/data/cache/scratch/web-coordinated-qa/` (screenshots and JSON). No credentials are recorded in repository files or screenshots. The scratch server uses port 9138 and no provider call was made by browser QA.

## Remaining verification limits

Current final bundle is `web-96cb7870c5f0`; an independent review caught nullable date/timestamp sentinels moving to the front under reverse sorting. A RED/GREEN regression now tests source and packaged Due/Created/Updated in both directions, keeping missing values last without rewriting legacy saved sort semantics.

The **live UI still hardcodes Europe/Rome**. Explicit-zone policy goldens do not prove browser-local clock wiring. A new actual-browser Los Angeles DST/Sunday regression demonstrates the discrepancy; a temporary correction passed seven checks but reapplication was blocked after tool approval was withdrawn. No renewed authorization was received, and the change was not bypassed. The user-visible local-calendar acceptance criterion therefore remains unmet. Web direction labels and shared moving scope capsule also remain unfinished. See `qa/coordinated-evidence-20261110/REPORT.md` for consolidated evidence and blockers.

Desktop Chromium screenshots and CSS timing are not a physical-device keyboard or frame-rate recording. Real provider running/terminal behavior is covered through actual production renderer/store tests, not a live external provider invocation in browser QA. Older browsers without discrete dialog/details transitions cannot animate the full exit while retaining immediate native semantics. Physical IME rollback, native Android behavior and APK delivery belong to the parallel native implementation, not this web-only change.
