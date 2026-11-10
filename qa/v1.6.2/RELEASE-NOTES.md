# My Tasks 1.6.2 — contextual email replies

- Reply directly from an email-driven update or task details without acknowledging the update. Exact reviewed origins are snapshot-bound; historical/multiple references require explicit selection with useful metadata and excerpts.
- Persistent, revisioned reply drafts shared by Android and web. Correct Gmail/Nantes account, Reply-To/Reply-all, original subject, RFC thread headers and Gmail thread identity remain server-controlled.
- Optional **Draft with AI**, **Improve writing** and **Ask AI**. Generated text is an editable proposal, never an automatic replacement or send. Explicit Apply rejects stale proposals; the draft-specific AI conversation survives closing.
- Explicit review and confirmation before sending. Changed source/thread/task context requires a visible refresh before a new confirmation. Durable send identities prevent duplicate submission; ambiguous delivery is reconciled without automatic resend.
- Compact source citations, authenticated email context, visible provenance warnings and graceful unresolved references. Original assistant content is preserved separately from source receipts.
- Asynchronous meaningful conversation titles with bounded topic-change cadence and permanent manual-title ownership.
- Native narrow editor/assistant switching and unfolded split workspace, plus responsive desktop browser editing. Saved preferences, canonical task data, authentication and original Android signing identity are preserved.

Android **1.6.2 / code12**, package `com.simon.mytasks`. Install over the existing app; do not uninstall or clear data. Standard assets: `my-tasks.apk`, `update.json`, `SHA256SUMS`.

Verification: 164 backend tests, 135 web tests, existing Android policy/JVM/Python tests and new native email/citation/layout harnesses; independent backend/web/native gates; actual configured-model drafting/discussion/improvement/title probes using synthetic mail; native Pixel Fold folded/unfolded, light/dark, IME, state-retention and 200%-font checks. Exact scope/evidence: `qa/v1.6.2/`.

Physical Fold8/One UI and real external email delivery are not claimed tested. Development sends use isolated fixture transports, including a loopback SMTP contract test. Historical updates without exact provenance require a source choice rather than a guessed recipient/thread. Ordinary chat still has no mail-sending tool; only the separate human-confirmed composer can send.
