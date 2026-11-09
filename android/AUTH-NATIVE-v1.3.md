# Native v1.3 account and prose-notification handoff

Unpublished `1.3.0` / versionCode `5`. Changes are confined to maintained `android/`; no production provisioning, deployment, publication or new signing key.

## Account flow

- Mandatory native username/password gate, default public username `oiry-s`. No password default or manual bearer-token field is shipped.
- `AuthClient` uses `/auth/native/login`, `/auth/native/password`, `/auth/native/logout` and `/auth/session`. The existing encrypted credential is automatically supplied as bootstrap owner proof for session/password checks. Login probes a saved proof first: a revoked/expired proof is not sent as an invalid Authorization header that would block ordinary activated-account login.
- A fresh initial-password installation without owner proof receives 428 and explains private web activation. A restricted owner receives current/new/confirm fields, 15–128 Unicode-codepoint validation, no trim, no composition rules and server validation. No task cache load, conversation controller bound to the account, data request or model admission occurs before full authentication.
- Every `/api/*` request rechecks `/auth/session` before transport. Restricted, expired and revoked credentials block data/model transport; 401/428 close private workspace UI. Known restricted/full session expiry also gates the foreground UI via timer/onResume. Gate transitions rotate the local work epoch, stop old controller/cache/poll callbacks, clear old view references and cancel task notifications. Every app-owned summary/chat/rename/task/date/source dialog is registered and dismissed, not left over the sign-in screen. Confirmation and session callbacks carry a privacy-generation guard. Explicit sign-out also persists a signing-out gate so Activity recreation cannot reopen the account while remote revocation is pending.
- Issued opaque 64-hex sessions alone pass `Store.session` validation and existing `configure` encryption. The AES-GCM Keystore alias `mytasks.token.v1`, server-origin AAD and legacy credential parsing remain unchanged. No key deletion/reset. Passwords/confirmation are never written to preferences, instance state, task/conversation cache or logs. Password validation counts Unicode codepoints and rejects unpaired high/low surrogates before transport. Secret inputs disable view-state/autofill saving and are emptied when submitted. Immutable request strings necessarily remain transiently in process memory while a request is in flight.
- Authentication POSTs have one transport attempt. A failed/ambiguous password response (network, 5xx or epoch conflict) prompts sign-in with the new password, not automatic password-change replay. Storage failure has a separate safe Keystore diagnostic. Process-wide in-flight gating prevents a recreated Activity from starting a second concurrent password POST. Successful credentials saved by a destroyed Activity notify its current replacement.
- Sign-out attempts native revocation, reads `/auth/session` back to verify it, then clears local credentials/caches/bindings without deleting the key. If offline, the UI explicitly states that only local clearance is confirmed and the remote session may last until expiry. Custom server is retained independently; updater manual checks remain account-free.

## Notifications and messages

`NotificationProse` prefers canonical `notification.sections` over the old technical summary, including when canonical sections are present but empty. Order: Context, New, Suggested next steps. Empty optional sections are omitted; no invented context/advice. Legacy summary fallback exists only when no sections object is supplied.

`SafeMarkdown` parses text into inert style ranges; `MarkdownText` produces only bold/italic/monospace spans. Paragraphs/lists/code are readable. Link labels are inert, inline image Markdown is discarded, raw HTML is not executed and no HTML/link/image engine is used. Chat messages use the same renderer; formatted previews are bounded at 32,000 UTF-16 units with a visible shortening marker and a full saved plain-text view, not silent history truncation. Android OS BigTextStyle uses the same Spanned prose, has no source actions and disables generated contextual actions on supported versions. Android owns notification-shade appearance.

Unread tasks and account-epoch-bound notification deep links open a rounded, glass-inspired summary modal before task interaction. Acknowledgement sends the exact displayed token. A 409/network error keeps that summary visible and changes the action to refresh/review before another acknowledgement; dismiss/reopen cannot bypass the refresh requirement. Task-editor source links remain separate. No acknowledgement is triggered by opening/dismissing/delivering a notification.

## Appearance and accessibility

Bundled licensed Inter variable TTF is used by both themes and custom text/buttons/inputs; its OFL license is accessible in Settings. Cool blue/light/dark high-contrast tokens, compact 68dp task rows, wide split layout and IME insets remain. Navigation uses a stroked, floating capsule; buttons/pills have restrained scale and ripple feedback; task/sheet/message transitions are short. Motion honors system animator/window/transition settings and an explicit Reduce motion preference. Reduce transparency, high-text-contrast and exposed platform transparency settings select opaque materials. There is no backdrop blur or iOS optical-refraction implementation.

## Verification commands and limitations

```sh
JAVA_HOME=/path/to/jdk17 bash test.sh
python3 tests/run_auth_api_integration.py
python3 tests/run_conversation_api_integration.py
# Existing original external signing key/password paths only:
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/sdk \
SIGNING_KEYSTORE=/private/original/mytasks.p12 \
SIGNING_PASSWORD_FILE=/private/original/password bash build.sh
python3 release_metadata.py build/my-tasks.apk \
  --expected-certificate a30330e687f078beaa6870254c2f5ce47d22c6e4e9e970ef4b2abc087d3137da
```

The initialized-account HTTP integration uses real JVM `AuthClient`/`AuthPolicy` against an isolated ASGI server with synthetic credentials/private scratch DB. It covers no-proof 428, legacy proof, restricted/full sessions, blocked API access, password rotation/epoch revocation, native re-login/logout readback and a durable conversation under full native auth. Existing conversation integration separately verifies 42 messages, paging/replay, task threads and no-tools synthetic provider calls. Neither HTTP integration uses production credentials/data/provider or Android's HTTPS stack.

No `/dev/kvm` or connected ADB device is available. Compilation, JVM policies/transport, source invariants, isolated HTTP tests and signature metadata are verified separately from runtime. **Unverified device gates:** native launch/first-install/upgrade, actual Keystore persistence, lifecycle/rotation auth interactions, keyboard/focus/insets, compact/expanded/foldable pixels, font rendering/large text/TalkBack, notifications/permission/channel/deep-link display, live trusted HTTPS transport and updater installer consent. Cold offline app launch requires online session verification before showing cached private history; durable server history is unaffected. Parent review and device QA are still required; do not imply these were exercised by host tests.
