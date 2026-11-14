# Chat database search and contextual sources

## Approved scope

Implement the owner's requested saved-mail/task database search from chat, hyperlinks on researched web text, and long-press access to relevant mail/tasks instead of source footers. Publish a new signed Android version and source on the existing GitHub repository. Preserve canonical tasks, private metadata, original message content, account/signing identity, unrelated staged knowledge work and existing browser presentation.

## Delivery slices

1. Extend `server/task_knowledge_context.py` and durable worker routing in `server/conversations.py`: free Discussions may search the existing private derived index, bounded and read-only, validated against current canonical tasks/source membership. Private retrieval never enters public web queries. Persist exact supplied-source receipts and preserve request replay. Task-linked chats remain private/local. Exercise real synthetic SQLite/API/worker tests before implementation and after changes.
2. Update Android `CitationPolicy`, `CitationUi`, `ConversationsUi` and tests: safe receipt-bound inline web links; no fabricated attribution; related email/task actions in message long-press, selection and accessible actions. Keep original copy, warnings, text selection and review-as-task. Open actual task navigation and existing authenticated email details. Test production parsing/rendering and extracted UI methods.
3. Bump Android to 1.6.4/code14, build/sign using the existing external identity, derive update metadata from the actual artifact and compare the known signer. Independent review and fresh server/web/Android gates before commit. Browser source/assets must match the private baseline fingerprints.
4. Deploy only the reviewed backend modules using the established service lifecycle, preserving consistent private rollback snapshots; validate installed hashes, public health/anonymous denial and synthetic runtime checks. Do not modify real tasks or send mail to test this feature.
5. Verify GitHub main and CI, draft/upload/read back the exact release artifact set, then publish stable/latest and anonymously verify downloads. A source push, binary publication, backend deployment and native runtime verification are separate outcomes and must be reported separately if blocked.

## Verification and boundaries

- Full server unittest discovery, Node browser regressions, Android `test.sh` and focused UI/extracted-method checks with a nonzero discovered count.
- Native QA uses synthetic records in a separate package and private loopback fixture. Preserve production app data/session; no real mail sends, acknowledgements or task mutations.
- Keep APKs, signing files, private databases, source excerpts and actual mail/task content out of tracked source and public evidence.
- Source publication is authorized by this request; existing private material is not.
