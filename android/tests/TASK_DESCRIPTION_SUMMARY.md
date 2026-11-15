# Compact Android task descriptions

## Contract and preservation

- Optional task-level `description_summary` is a derived Markdown **string**, not an editable or canonical field. Backend ownership includes binding it to the current description, invalidating stale summaries, and providing concise coverage of the entire description. Native accepts nonblank strings up to 1,600 Unicode code points; absent/null/non-string/blank/oversized values use the local fallback.
- The default reader prefers that complete-content summary. Without one, it explicitly labels deterministic start/middle/end selections **selected excerpts**, never a semantic summary. Selections preserve source order, include the tail, retain existing emphasis and indicate omitted context. They are bounded to approximately 400 characters and at most six explicit lines. Short original descriptions need no excerpting.
- **See more / See less** expands/collapses only presentation. The complete `description` remains authoritative for full reading and editing. **Original text** additionally exposes its exact original Markdown/HTML/whitespace as inert, selectable text; it is never passed through the parser or persisted as a replacement.
- Safe Markdown uses the existing inert renderer: existing bold/italic/code/list layout, plus task-only heading and short-label emphasis. No HTML interpretation, image fetches, URL spans, or automatic link actions. Notes, update summaries, conversation rendering and backend/web presentation are not changed by this Android implementation.
- The complete original is shown as plain inert text when it exceeds the bounded Markdown renderer or added layout markup would exceed that bound. This avoids silently losing final conditions.
- Same task/source/summary rebinding preserves reader mode during in-place refresh and handled configuration changes. Changed task/source/summary resets compact mode. Selection ownership is checked before any render early return, including empty-description, conversation-only, missing-cache and editor paths that never bind a reader. Returning to the list or leaving authenticated UI clears reader state. Description editor initialization and save serialization still use the canonical description, never summary/excerpt text.

## Checks

```sh
export JAVA_HOME=/opt/data/cache/android-build/jdk-17.0.2
export ANDROID_HOME=/opt/data/cache/android-build/sdk
export PATH="$JAVA_HOME/bin:$PATH"
python3 android/tests/run_task_description_tests.py
bash android/test.sh
python3 android/tests/run_details_v3.py
```

The focused runner compiles the real policy and extracts the current production `readingDescription` method, exercising the bound See more/See less/Original text callbacks against test-only UI shells. A separate lifecycle harness executes complete production `render`, `navigate`, `openTaskConversation`, `renderPage` and `onConfigurationChanged` methods, plus the actual detail hydration/conversation early-return and optional-description dispatch seams. It reproduces A → empty-description B → A and A → conversation-only B → A, verifies expanded/original modes reset on return, and checks same-task refresh/rebinding/navigation and handled rotation preserve both modes. Both selection cases failed on the reviewed production code before the lifecycle fix. Detail layout tails and Android/OS/storage are host shells, not native integration proof.

The runner also checks both normal/trash details and canonical editor/save wiring. Tests cover backend summary preference/invalidation, tail preservation, safe styles/links, null/oversized inputs, Unicode, fenced code, newline-heavy fallback, parser-budget expansion and exact original retrieval. Fail-first runs were observed before policy/binding implementation and for the dense-layout and newline regressions.

Host shells and SDK/D8 compilation are **not native runtime/render evidence**. Device large-text/fold/scroll checks and actual APK delivery are handled by the parent release/QA worker; this worker does not sign, publish, deploy, change version metadata or mutate production tasks.
