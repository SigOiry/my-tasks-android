# Chat access and task visibility fixes — v1.6.1

## Final delivery status

**Final Android and backend independent gates passed; the shared chat update is deployed.** The owner authorized continuation and publication. Indirect writing, neighboring transformation gerunds, opt-out qualifiers/typographic apostrophes and social/capability regressions were repaired with actual normal-answer fail-on-network tests. Final review passed 43 focused tests, 416 independent protected cases, positive public search/read controls and three conversation integration tests. See `backend-independent-review.json`.

The signed Android 1.6.1/code11 APK retains the original signer and exact bytes already verified at `/download/my-tasks.apk`. Standard GitHub release assets are `my-tasks.apk`, `update.json` and `SHA256SUMS`; source push alone is not publication. Deployment/publication readbacks are stored separately from the initial Android-only download receipt, which remains historical. No rejected backend candidate was deployed.

## What was actually broken

The deployed search engine worked, but the chat router skipped ordinary factual questions unless a narrow freshness/search keyword appeared. The original deployed durable request for `Who won the 2026 Nobel Prize in Physics?` completed with no search/read phase and only a tool-free provider call; its answer admitted it had no verified information. See `deployed-chat-before.json` and the compact live search receipt `deployed-search-before.txt`. Existing maintained/deployed source hashes matched, so this was a routing defect, not a missing v1.6 deployment.

The actual folded native baseline (`evidence/before-tasks.png`) visibly drew the category rail through completion circles. Task tints were weak and unread emphasis was dominated by a narrow fixed-blue outline.

## Backend correction

- Ordinary English/French public questions can research without special freshness wording. Eligible supplied public URLs receive one pinned public-page read and tool-free synthesis, never a URL-bearing search or link crawl.
- Capability/help replies describe server-provided public research rather than guessing from the synthesis model's unavailable tools. They do not claim that live retrieval happened.
- Greetings, ordinary writing/supplied-text transformations, credentials, private/history-dependent requests, explicit no-browse and rolling summaries do not perform public retrieval. Recognized transformations without a direct URL fail closed regardless of indirect phrasing/modifiers/verification instructions; this deliberately conservative rule is not a universal multilingual PII classifier.
- Exact persisted current request stays separate from merged model history. Public research never receives saved task context/history. Pinned public DNS/IP/TLS/redirect checks, bounded tools/time/bytes, full provenance within the existing context cap and durable request replay remain enforced.
- Independent review caught supplied-text and password-alias/no-browse regressions before deployment; RED/GREEN regressions and the fail-closed transformation boundary correct them. No rejected candidate was deployed.

## Android correction

- Removed both checkbox-crossing rail and floating dot. Completion/action targets remain separate 48dp controls.
- Stable project hue remains unchanged. Low/medium/high surface intensity is now **16/26/36% light**, **16/24/32% dark**, with tested 4.5:1 text and 3:1 priority-cue contrast on actual composites. Neutral stays untinted.
- Unread emphasis is a **15dp broad soft external halo in the actual task category hue**, with 16dp reserved paint gutters. The narrower rim is secondary. Reduced motion keeps the broad static treatment.
- Whole-stroke bounds are guarded using half the actual rounded stroke width. Extracted production-method tests cover 12 densities (including 2.25), nine sizes and three breath levels; the previous expression reproduced a -0.5px clipping failure.
- Bounded sustained breathing/lifecycle scheduling, task ordering lanes, saved preferences, exact-token acknowledgement and canonical mutations are unchanged.

## Verification

- Final signed Android build passed **69 Python tests**, 182 presentation checks, 1,357 actual task lane/group checks and the JVM/auth/controller/acknowledgement gates. Original signer verified, v2/v3 signatures and alignment passed: `android-build.txt`.
- Final backend: **117 tests** with ResourceWarning treated as an error: `backend-tests.txt`. Web: **101 passed**, built fingerprint **web-96cb7870c5f0**, all 17 web source/built assets unchanged: `web-tests.txt`.
- Actual configured-provider durable-store probes: natural factual question, supplied public URL and smalltalk. Final maintained receipts are `maintained-final-*.json`; exact deployed-source receipts are `deployed-final-*.json`. Earlier `maintained-*.json` probes remain historical, not final-source claims. These use temporary stores, never production conversations/tasks.
- Exact accepted-request replay causes no new work; reopening returns the exact persisted assistant message/citations. Real success, honest failure, tool-free summaries, unsupported-tool rejection, history isolation and context/provenance limits also retain regression coverage.
- Android independent review: `android-independent-review.json`. Backend final review and deployment/publication readbacks are retained alongside this report when completed.

## Actual native evidence and its boundaries

The local connected device is a **Pixel Fold emulator on Android 17**, not the owner's physical Fold8. Production account data was not used for mutations or messages. The original production and prior QA installations were retained; a separate `com.simon.mytasks.qafix` package uses only the existing fictional fixture and loopback-scoped TLS certificate.

The original NAS scratch QA signing root had been pruned while the remote fixture remained. New scratch-only instrumentation/package names avoided replacing either installed signing identity. The final QA build was transferred, installed and its installed SHA-256 verified as `0bf9b622e186d85a20462a67c3696f6bcc54541496c2c16666bd53da715b6ad3`.

- Inspected actual folded light/dark, unfolded and 200%-font renders. Final code11 folded/window/theme captures follow the fractional-density repair. Earlier unfolded/200% candidate captures precede only that narrow drawing-edge clamp; they are not represented as physical-device proof.
- Both blue Research and teal Operations unread tasks show their own broad halo, and completion circles have no overpainted rail. Titles/context remain readable.
- `evidence/task-color-halo-final.mp4` is the reviewed **first 30 seconds** of a real native display recording; `final-halo-contact.png` samples 8/8.4/8.8/20/20.4/20.8 seconds. This proves continuing visible native emphasis, not measured UI FPS.
- The original longer recording returned to the previously foreground production activity after instrumentation finished. Its mixed tail and tail-derived images were quarantined outside the repository. No mixed recording is published; the retained prefix's last decoded second was independently inspected as synthetic QA.
- Reduced-motion captures show a static broad halo. Earlier timed pixel comparison found no changes in the task region after excluding the right scrollbar's fade; see `evidence/motion-samples.json`.
- No physical Samsung/One UI, installer consent flow, exhaustive TalkBack audit or combined live-provider-on-native footage is claimed. Backend/provider evidence is real; native fixture provider responses are explicitly synthetic. These are distinct checks.

## Installable artifact

Android **1.6.1 / code11**, package `com.simon.mytasks`, original certificate SHA-256 `a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da`.

Exact signed APK: **796399 bytes**, SHA-256 `953da5d22d257e0ea9ec6f4843d3dddfd5e9b2c655badd7cf1cf0f76cef42eab`; derived metadata: `release-metadata.json`. Install over the existing app without uninstalling or clearing data. Stable updater publication and self-hosted download must match these exact bytes; source push alone is not a phone update.
