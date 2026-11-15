# Compact task descriptions — 1.6.5 / code 15

## Delivered behavior
- Android and browser task details default to a safe Markdown overview selected from the complete description, including late sections/actions and qualifiers. **See more / See less** reveals/collapses the original. Editing always uses the complete canonical description.
- API adds `description_summary` and `description_summary_version: extractive-v1` after canonical enrichment, without persisting presentation fields or changing revisions/unread tokens. Excerpts are explicitly labeled; this is deterministic extraction, not semantic AI synthesis. At most six bullets/1,600 code points; finite selection can omit context, so full text remains authoritative.
- Browser read-only Trash descriptions remain accessible; editor stale-response/draft fences and exact unchanged CRLF round trips are retained. Native task-selection changes reset disclosure, while same-task refresh and handled rotation preserve it.

## Verified
- Final backend discovery with ResourceWarning promoted to errors: **209 tests passed**.
- Final browser suite: **152 tests passed**; reproducible bundle **web-b09290002c68**.
- Final signed Android build: full JVM/controller/email/policy suite including **77 Python tests**, SDK35 compile, D8, alignment and original-signer verification passed.
- Independent native and backend/browser reviews approved after fixing navigation through empty/conversation-only tasks, disabled FormData in Trash, and quadratic heading parsing. Adversarial 2,000/16,000/32,000-hash headings measured approximately 3.1/25.9/50.8ms in final independent review.
- Real browser scratch QA covered keyboard disclosure, ARIA, full last instruction, draft safety, inert malicious Markdown and 390px layout.
- Final Pixel Fold isolated QA package upgraded without clearing data; local/transfer/installed SHA256 matched. Four final scenarios passed: description, visible controls, responsive layouts and selection lifecycle. Real API summary text exactly matched native rendered content; StyleSpan.BOLD asserted; full editor preserved the original 1,996-character synthetic fixture/hash.
- Actual screenshots inspected for folded/unfolded, light/dark and 200% text. Restored light, font1.0 and opened state. Synthetic provider/data only; no real mail sent.
- Reviewed backend/web deployed by stopping/starting only My Tasks after checking pending private work and taking consistent private rollback snapshots. Public HTTPS health/root200, anonymous tasks401, exact shipped web asset and APK hashes verified. **16 synthetic API tests passed inside the deployed container**, importing the exact deployed modules. Canonical task bytes/private-table counts preserved across the deployment window.

## Artifacts
Official APK: **882,415 bytes**, SHA256 `4b89c48ba19f37935c470d8fe18c20c051455873b529afdacbc8e4b83375f541`.

Original certificate SHA256 `a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da`.

Native synthetic QA APK (different package/signing identity, not a release asset): SHA256 `73ea809e3ca88fa8c79a6230d4913b22dd2a27787eec274866e78b7ce65051e3`.

## Limits
No physical Samsung/One UI or Android installer acceptance is claimed. Native empty-description navigation used an unsaved empty draft plus another-task conversation; persisted-empty read-only navigation is covered by the actual-method host harness. Long overviews/toggles can require scrolling, especially at 200% text. Existing short-description/summary differences may still show disclosure. No claim of complete semantic summarization or obligation detection.

Publication/CI are verified separately after the source commit; this report alone is not proof of publication.
