## Android email reply workspace

- Fix keyboard dismissal during autosave and assistant updates; preserve writing, focus and selection through folding and rotation.
- Make Email / AI assistant navigation and Draft / Improve / Ask actions clear.
- Show readable suggestions with explicit Apply to draft and Compare drafts controls.
- Improve unfolded, landscape and 200% text layouts so the keyboard does not hide the input.
- Save changes before Review/Assist, reject stale review responses and prevent duplicate assistant requests.
- Show clean, read-only sent receipts; sending still requires exact review and explicit confirmation.

Install over the existing app—do not uninstall. Package and original signing identity are unchanged. The shared backend and browser interface are unchanged.

Verified on the Pixel Fold emulator, with native keyboard/fold/rotation checks, injected nonzero-hinge bounds, offline recovery, synthetic send confirmation, regression suites and source review. Physical Samsung/One UI behavior and real external email delivery are not claimed; no real email was sent during QA.
