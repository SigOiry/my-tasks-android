# My Tasks owner continuation — 10 November 2026

**Historical 1.5.1/code9 implementation checkpoint.** The owner subsequently requested publication as v1.6; the current `android/build/my-tasks.apk` is rebuilt as 1.6/code10. The hashes, captures and installation statements below describe the actual earlier checkpoint, not the new release binary. Current release artifact verification is in `qa/releases/v1.6/README.md`. The implementation and disclosed acceptance gaps carry forward.

**Pre-release review correction:** the original 93-test checkpoint missed a failed-user-turn history-merge privacy case and research evidence exceeding the synthesis context budget. v1.6 adds structural persisted-current-request isolation and total-budget regression coverage (97 backend tests). Its SDK Tasks test seam also now includes the actual dismissal style. The original claims below must be read as checkpoint observations, not proof against those subsequently found defects.

## Delivered state

Android changes and restricted shared-backend public-web access are implemented. The signed manual-download build is **1.5.1 / code 9**. The shared backend is deployed and healthy at the existing production origin. The web UI presentation was not changed. **Full acceptance is not claimed for the remaining runtime checks listed below.**

- Source checkout: `/opt/data/workspace/my-tasks-android` (changes intentionally uncommitted; unrelated existing edits preserved).
- Signed release APK: `android/build/my-tasks.apk`.
- Release SHA-256: `64a9841451bbe4e77b2c6c87d88810f2b483bf1e5cbf390317adb9f1a5752411`.
- Original signer SHA-256: `a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da`; v2/v3 signature and alignment passed.
- Final isolated QA APK SHA-256: `5463924171b841466483802cbf16fb7e7f73a95177b4b25b74e18c78a5195a93`.
- Both APKs were transferred with strict SSH host verification, installed over their existing packages, and independently hashed at their actual installed `pm path` targets. All hashes matched. No uninstall, account reset or signing replacement.
- The existing self-hosted `/download/my-tasks.apk` route now serves the release bytes; independent public HTTPS download returned 200 and the same SHA-256. No GitHub release/tag or update-feed promotion was performed.
- Production Settings was inspected read-only: **My Tasks 1.5.1**, saved origin **https://task.oirysimon.com**, existing signed-in account retained.

## Implemented

### Android only

Stable muted project hue with priority-dependent row tints (light 14/8/4%, dark 20/12/6%), neutral text, slim accents, priority flags/semantics, Project/Importance/Neutral choices and saved preferences. Smart and every traditional ordering mode preserve actionable/waiting/inactive/deleted lanes; direction changes only order within lanes. Project grouping repeats within those lanes. No canonical priority/deadline/read-state rewriting.

Unread rows now reserve room outside the crisp surface edge for a genuine soft halo. A stronger token-bound arrival is followed by sustained gentle breathing, not a finite four-breath stop. Effects are bounded to two concurrent rows and pause during interaction/background/offscreen states. Reduced motion retains a static halo and New update marker. Exact-token review behavior is unchanged.

Directional page entry/return, moving scope/tab capsules, coordinated native dialog/scrim entry, native-window 240ms fade/translation dismissal, actual IME-fraction composer motion, outgoing admission and incoming-tail insertion, actual queued/running dots, retained older-reader anchors and fold/draft adaptation. Public HTTPS source actions are available on assistant replies; update-summary rendering remains inert.

### Shared backend

Normal durable-chat answers use a bounded public-search/page loop when external evidence is required, including implicit opening-hours questions. Research sees only the short public latest request, never private saved task context/history. Only public search and read-only page retrieval are exposed. DNS/IP pinning, redirect revalidation, private/loopback/metadata rejection, byte/time/call budgets and unsupported-tool rejection are enforced. Summaries, greetings, ordinary writing and no-browse requests remain tool-free. Failure notices and actual source URLs are saved in ordinary reply content. Existing request identity/recovery/replay semantics remain intact.

Optional `Request.web_phase` is additive: searching/reading/answering/null, based on actual worker activity and cleared on completion/restart. Old web clients can ignore it.

## Executed tests

| Gate | Actual result |
|---|---|
| Full signed Android `build.sh` | PASS: existing JVM/auth/controller/API gates, 68 Python tests, Java/resource/DEX compilation, signing/alignment |
| Independent golden Android policy | PASS: 8 contexts, 17 palette vectors, 36 score vectors, 11 orders |
| Actual extracted `TasksUi.refresh` lane/group harness | PASS: 1,357 checks; policy harness 182 checks |
| Backend unittest suite | PASS: 93 tests |
| Unchanged web-client JS suite | PASS: 101 tests |
| Isolated fixture/package/build suite | PASS: 5 tests, including two actual QA APK builds and stable QA signing |
| `git diff --check` | PASS |

The contrast test was corrected to distinguish animation styles from actual color themes while explicitly requiring both supported themes. QA SDK commands require the JDK on PATH as well as JAVA_HOME; the native-runner builder now sets that itself. These host checks are not substituted for runtime evidence.

## Actual deployed public-web proof

Only the backend modules `conversations.py`, `hermes_chat.py`, and new `public_web.py` were deployed after private consistent state/code backups and a check that no production request was pending. Only the My Tasks Dockge service was stopped/started. Public health returned 200, anonymous conversations remained 401, and the live SQLite additive phase migration was confirmed.

`deployed-backend-manifest.json` pins the exact running source bytes. A fixed public-only diagnostic ran **inside the deployed backend container**, using its real configured provider and the deployed research code, without creating a production chat or accessing task context:

> What are the opening hours of tabaccheria N 196, Via di Prosecco 15, Trieste?

It emitted actual searching → reading → answering phases, read Fermopoint's Tabacchi 196 listing and returned cited hours with holiday/temporary-hours uncertainty. Full actual output: `deployed-real-public-query.txt`.

Separate real-provider scratch-store probes in `../backend-public-web/` prove greeting/no-search, tool-free summary, honest injected failure, successful opening-hours retrieval, exact replay without another model/search/page call and citation persistence after reopening. The injected outage is explicitly an injected failure, not claimed as a real external outage.

## Actual native evidence

All deliverable native evidence uses the isolated synthetic QA package/account/data. Its API, canonical Store, revisions, admission, worker state and persistence are real. The transport/motion provider is explicitly **synthetic**, not a claimed live AI call. The final QA backend used the same three production backend module hashes, with only the documented fixture provider replacement.

Key screenshots under `evidence/`:

- `final-owner-project-light-display.png`, `final-owner-project-dark-display.png`.
- `final-owner-importance-light-display.png`, `final-owner-neutral-light-display.png`.
- `final-owner-sort-light-display.png`, `final-owner-sort-dark-display.png`.
- `final-owner-all-waiting-inactive-display.png`, `final-owner-trash-scope-display.png`.
- `final-owner-update-review-display.png`, `final-owner-anchored-actions-display.png`.
- `final-owner-chat-transport-running-display.png`, `final-owner-chat-received-display.png`.
- `final-owner-folded-chat-ime-display.png`, `final-owner-unfolded-chat-ime-display.png`.
- `final-owner-large-text-top-display.png`, `final-owner-large-text-scrolled-display.png`.
- `final-owner-reduced-motion-after-ten-display.png`.
- `final-owner-citations-reopened.png` (explicit replay of actual deployed public-source text, not a new search).

Actual final-APK recordings:

- `evidence/final-owner-sustained-glow.mp4` — 23.926556s; decoded external-halo samples still vary at 22–22.4s while title position/appearance remains stable. See `final-sustained-halo-contact.png` and `final-sustained-halo-samples.json`.
- `evidence/final-owner-glow-navigation-menu.mp4` — actual update review, forward/back navigation, scope changes, menu and sheet transitions. Consecutive dismissal frames are in `sheet-close-motion-strip.png` / `menu-close-motion-strip.png`; they show gradual exit and scrim restoration rather than a snap.
- `evidence/final-owner-colors-sort.mp4` — actual Android color/sort interactions and theme changes.
- `evidence/final-owner-chat-ime-fold.mp4` — actual admission, queued/running synthetic-provider work, incoming reply, keyboard and fold adaptation. `final-chat-filmstrip.png` is a decoded review aid.

The fixed encoder canvas letterboxes during folding. Encoder timestamps/FPS are **not** physical-device frame-performance measurements.

Native scenarios additionally passed all color/sort modes, direction preference persistence through a QA process restart, exact-token/newer-update conflict, 200% text without the tested long title being ellipsized, static reduced motion, and draft survival through folding. Older-reader verification retained the actual ScrollView offset **15161** and the same visible message bounds across a synthetic reply; Jump to latest remained available (`older-reader-anchor.json`).

## Preservation and web regression

All 17 source/built web assets remained byte-identical to the parent baseline and HEAD. Live public root CSS/JS/icon downloads matched unchanged deployed files (`live-web-regression.json`); browser sign-in rendered normally. No CSS/templates/web sorting/colors/animation changes were made. Existing web links retain their existing inert/copyable rendering; Android adds its own explicit browser actions.

Canonical task entries, IDs and all task fields remained unchanged. The only top-level canonical database difference against the matching initial backup was `sync` metadata. Production conversation/request/message counts remained unchanged. Authentication/private state/signing files were preserved. Test mutations and messages stayed in the synthetic store. Private real-account baseline images and an invalid foreground capture were excluded from the deliverable evidence.

The owned synthetic fixture was stopped and its single ADB reverse removed after QA; the production service remains running. Its retained private certificate/account/signing state was not deleted or exported.

## Explicitly incomplete / blocked checks

1. **No final native recording of a live configured-provider search/read round-trip.** Real research is proven in the deployed container and real durable scratch-store probes; native transport, citations, queued/running behavior and the optional phase wiring are tested separately. The isolated Linux fixture intentionally has neither the Hermes `agent` provider package nor its configured web-tool package/credentials (verified). SSH forwarding is prohibited by the configured key. No credentials were copied, no production test conversation was created, and no unauthorized relay/LAN exposure was introduced. A supported isolated endpoint with the configured provider is required to close this combined end-to-end capture. Searching/Reading UI footage is therefore **not** claimed complete.
2. **Source action launches Chrome, but final external-page rendering is blocked by Chrome's first-run account/telemetry onboarding.** The persisted native source buttons were verified after reopening and launching one reached Chrome FirstRunActivity. Browser onboarding/account/privacy defaults were deliberately not accepted or changed. The mixed-window PixelCopy harness correctly failed once the app no longer had a backing surface; this is not reported as a successful external-page screenshot.
3. Physical Samsung/One UI motion/performance, TalkBack interaction and installer/update-feed promotion were not exercised in this pass. No claim of exhaustive security review or universal multilingual routing/PII detection; routing remains deliberately conservative and some sites/JS/compressed/binary/large pages are unsupported.

These limits do not prevent using the delivered signed APK or the deployed restricted search capability, but they remain acceptance gaps rather than being hidden behind compilation or screenshots.
