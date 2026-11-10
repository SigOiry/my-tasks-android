# Liquid Glass web presentation

Local implementation record, November 9, 2026. Branch: `redesign/web-liquid-glass-v3`. This is a presentation update, not a new backend/API release or production deployment.

## Design and behavior

- Pearl / graphite / blue, self-hosted Inter, functional SVG icons, restrained supported backdrop filtering on navigation and controls. Reading, editing and messages stay opaque; no all-window blur or simulated optical refraction.
- Compact task workspace: Active / Today / All, Search and More; Week / Trash, filters and utility actions stay available. Connection status occupies space only offline. Unread, completion, priority and selection have distinct representations.
- Desktop task inspector at 1100px and above; native modal task sheet below. Form nodes and unsaved values survive resizing. An underlying inspector never becomes the top modal over a conversation or Settings.
- Read-only Open task / Ask Hermès separate from explicit exact-token Mark reviewed. Same-session/task/token ownership, newer cached update protection and dismissal guards reject stale acknowledgement continuations.
- Conversations retain canonical message nodes and reading offset. Older history compensates only prepends. Incoming replies do not jump an older reader. Latest messages is explicit; a viewport/composer resize follows only when already at the end. Per-thread unsent drafts remain memory-only.
- Discussion search is loaded metadata search, not a claim of global transcript search. Secondary task/archive/refresh filters are disclosed rather than permanently consuming mobile space.
- Dark, reduced-motion, reduced-transparency and forced-color styles; native form/summary/button/dialog semantics and keyboard focus. No new frontend dependency, service worker or private persistent store.

Backend routes, cookie/CSRF authentication, optimistic revisions, canonical task data, saved histories and model authority remain unchanged. Browser QA uses an owned loopback service and synthetic scratch stores; only its model boundary is a plainly labeled fixture. No production task/message mutation was used.

## Executed verification

- `node --test server/tests/web_*.test.mjs`: 92 passed, 0 failed.
- Server discovery with ResourceWarning promoted to error: 72 passed.
- Build and JavaScript syntax checks passed. Candidate bundle: `web-936be401eebe`.
- Real-browser task acceptance: 16 checks passed (canonical writes/readback, exact review, revision conflict retention, deletion/cancel/restore, search/focus and browser-storage privacy).
- Real-browser conversation acceptance: 17 checks passed (real admission and saved-message readback, explicit fixture-model completion, follow-up drafts, inert untrusted text, metadata changes/recovery, multiple task threads and older-history deduplication).
- Additional actual-browser checks: read-only update navigation retains unread and does not auto-create a conversation; a pending fixture reply retains an older reader's node and scrollTop=100; responsive bound-chat preserves notes and draft, stays above its inspector, then Escape restores the compact task sheet; Save remains visible at y=431 in a 390x450 viewport.
- Short chat viewport: composer bottom=450 in a 450px viewport, retained multiline draft, latest reply visible and scroll distance from end=0. Older reading offset remains unchanged on resize.
- Rendered at 320x844, 390x844, 390x450, 720x500 at 2x device scale (200%-equivalent reflow, not browser-menu zoom), and 1440x1000. No horizontal document overflow in those checks.
- Browser UI logout returned 401 for the previously authenticated tasks endpoint and left zero open dialogs/task rows, empty notes/chat content and no synthetic private text in the DOM.
- Desktop inspector measured 560px at 1440px, modeless. Functional task-scope material computed `blur(20px) saturate(1.35)`; reduced-transparency computed none. Inter loaded through document.fonts.load.

Browser acceptance suites precede the viewport/dismissal guards; those guards have failing-before/passing-after real-source VM tests and actual browser checks on bundle web-0b598321bd89. Final editor-generation isolation is tested by executable production-source VM regressions; the screenshots retain the same presentation, with unchanged CSS/markup. Do not conflate synthetic fixture-model replies with a live production model, viewport emulation with a physical mobile IME, or screenshots with frame-time measurement.

## Limits and publication

Physical Safari/Android browser keyboard, spoken screen-reader traversal and measured frame-time/battery profiling are not verified. No formal accessibility-conformance claim. The acknowledgement/modal/viewport delta review passed. Initial full review found delayed-save draft loss in the modeless inspector; an editor-generation ownership fix now passes nine added production-source regressions (92 total). Independent re-review passed with no security or logic findings; the reviewer independently ran all 49 session-mutation tests, including the nine delayed-save cases. No web commit, push, release or production deployment was performed.

Local screenshots, result JSON, build output and source export belong under `/opt/data/deliverables/my-tasks-web-liquid-glass-v3/`; private fixture credentials/cookies/database and real-account baseline captures must not be exported or committed.
