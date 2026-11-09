# Native Discussions rebuild — host evidence

Scope: ConversationsUi.java, ChatRowPolicy.java, dedicated send-arrow resource, and chat tests. No controller/state/API edits, production writes, APK installation, or device interaction.

## Verified
- `CHAT_TEST_BUILD_DIR=/opt/data/cache/scratch/chat-ui-tests python3 android/tests/run_chat_presentation.py`: ChatRowPolicyTest passed; existing 31 policy assertions and 8 wiring checks passed.
- New pure row-policy tests were introduced before implementation (initial compile failed because ChatRowPolicy did not exist). Checks cover edited text, grouping-neighbor invalidation, local-only/near-end/initial/pagination/reduced-motion animation gates, and bounded measured master width.
- `python3 android/tests/run_redesign_regressions.py`: route, own-change, acknowledgement, reload, and quick-action groups passed. Original extracted `taskThreadAction(String task,boolean create)` remains intact.
- Isolated Android 35 aapt2 resource compile/link + full-source javac succeeded under `/opt/data/cache/scratch/chat-ui-sdk`. Only deprecation notices. No shared APK/class/dex outputs were used.
- Full `android/test.sh` with scratch TEST_BUILD_DIR passed Java suites including ConversationState/Client/Controller; Python discovery is currently blocked by the concurrently edited `test_details_v3.py` Save/Cancel outside-scrolling-fields assertion. Chat checks pass independently.

## Integration / remaining checks
- MainActivity must omit its own header/modes for task Hermès mode and invoke buildTask directly; global full-thread navigation must also suppress global chrome. Parent owns that integration and IME/system insets.
- Native visual, IME, fold/resize, actual row-reuse/anchor and 180ms motion recordings still require parent device QA. Host tests are not screenshot evidence.
- Optional planning-to-task manual review is NOT wired here. Safe integration needs an Activity-owned review callback that captures draft state, handles dirty confirmation asynchronously, opens NEW, seeds an empty title/project plus editable original-message description, labels it DRAFT, and uses existing explicit Save. Calling `navigate("NEW")` then assigning `draft` is unsafe because navigation may await dirty confirmation. No proposal/provider schema was fabricated.
