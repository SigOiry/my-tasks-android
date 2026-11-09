# Private owner account (v1.3)

This document specifies browser/native password routes and the first-activation ownership boundary. No public registration, password reset, default password or production account is created by this source. The deployment owner supplies the initial password privately. Keep the original Android encrypted credential store/Keystore: the native bearer becomes an opaque account session, never a user-entered token.

## Persistent configuration and provisioning

Set `MOBILE_ACCOUNT_DB` to a dedicated private persistent SQLite file beside `MOBILE_METADATA_DB`, e.g. `/var/lib/my-tasks-mobile/account.sqlite`. Explicitly set `MOBILE_CONVERSATIONS_DB` on the same private persistent volume too. Neither file is canonical task data or Hermes `state.db`. Account startup rejects task/token/event/session/conversation path collisions, `state.db`, symlinks, unrelated SQLite schemas, and nonprivate account files. Mode0600 is required. Keep account metadata and private activation inputs out of Git, the APK, `web`, `web_dist`, download/public directories, backups exposed to the web, and logs.

Provision locally, as the service filesystem owner, **before** exposing/restarting the service:

```sh
python account_admin.py init \
  --db /var/lib/my-tasks-mobile/account.sqlite \
  --username oiry-s \
  --password-file /private/account-initial-password \
  --activation-file /private/account-activation-link \
  --origin https://task.oirysimon.com
```

`/private` represents an existing owner-only0700 directory, not a supplied fixture credential. The password input must be a private regular0600/0400 file. Its UTF-8 contents are exact: no trimming, truncation, or automatic newline removal; create it without a trailing newline unless that newline is intentionally part of the password. The owner removes this plaintext input after successful provisioning. The command prints only status and nonsecret file paths, not a password, nonce or activation link. Existing accounts cannot be reset/reinitialized with `init`.

The output activation link is a newly created0600 file; the command refuses to overwrite an existing file. It contains `https://task.oirysimon.com/#activate=…`, valid for seven days and one successful redemption. The server stores only the nonce SHA-256 digest, not the nonce. Transfer this file privately to the owner. The browser strips the fragment before POSTing the code; activation never belongs in a query string, access log or referrer. The server should continue to run with Uvicorn access logging disabled and without request-body logging.

If an activation was consumed but its restricted cookie was lost/expired, a local administrator may issue a **new activation only while the account still requires its first password change**:

```sh
python account_admin.py rotate-activation \
  --db /var/lib/my-tasks-mobile/account.sqlite \
  --activation-file /private/replacement-activation-link \
  --origin https://task.oirysimon.com
```

This invalidates the previous nonce and restricted account sessions, increments the credential epoch, and refuses an already activated account. It is not a password reset, recovery backdoor or public HTTP route. A disk-write failure after account initialization/rotation may leave an unused nonce without an output file; retry `rotate-activation` with a fresh private filename while still in bootstrap state.

The existing high-entropy legacy token file remains mounted privately for bootstrap proof, but after activation the static bearer and all legacy web cookies are completely rejected. Preserve the account database across deploys/rollbacks; **never delete/replace it to restore token access**. Code predating account enforcement is not a safe rollback after activation. Back up account metadata as sensitive authentication material.

Production refuses to start with an uninitialized account. `MOBILE_ALLOW_LEGACY_UNINITIALIZED=1` (or the factory argument `allow_legacy_uninitialized=True`) permits old token fixtures only when the account is genuinely uninitialized. It does not weaken an initialized/bootstrap/active account. Leave this unset in production. If an account is initialized while an explicitly legacy-compatible process is still running, that process fails closed503 on auth/API until restarted; it cannot keep serving token-authorized data.

## HTTP contract

All request objects reject extra/wrong-type fields. Validation errors never echo input. JSON is required on login, activation and password routes; the existing body cap remains65536 bytes. Duplicate Authorization, Origin or CSRF headers are400, even identical duplicates. Supplied bad/malformed Authorization is authoritative: it never falls back to cookies. No CORS support is enabled.

- `GET /auth/session`: browser cookie or native Bearer; `{authenticated:false}` for absent/invalid credentials, otherwise username, password-change flag, expiry and browser-only CSRF. Legacy owner proof during bootstrap identifies a restricted owner. No raw session is returned here.
- `POST /auth/login {username,password}`: browser-only exact configured Origin; sets HttpOnly/Secure/SameSite=Strict opaque cookie and returns `{authenticated:true,username,csrf,password_change_required,expires_at}`. Rotates the previous browser session.
- `POST /auth/activate {activation_code}`: exact Origin; redeems one64hex nonce only in bootstrap state, returning the same browser metadata and a restricted cookie. Does not unlock API data or change the password.
- `POST /auth/password {current_password,new_password}`: browser cookie plus exact Origin/CSRF. Verifies current password, transactionally increments epoch/replaces hash/revokes all old sessions, returns a fresh full browser cookie and metadata.
- `POST /auth/logout`: browser Origin/CSRF; revokes restricted/full current cookie and clears it.
- `POST /auth/native/login {username,password}`: returns `{authenticated:true,username,session_token,password_change_required,expires_at}`. No cookie side effect or CSRF field. Static legacy Authorization may prove bootstrap ownership; cookie proof is never accepted. If Origin is supplied it must match exactly.
- `POST /auth/native/password {current_password,new_password}`: native Bearer, no cookie fallback; same password/epoch rules and new full native token response. Existing legacy native owner proof can perform the initial replacement directly.
- `POST /auth/native/logout`: revokes native Bearer only; no cookie fallback or side effect.

Before initial replacement, username/initial-password alone returns428 `activation_required` with **no session**. Existing private bearer, existing valid legacy cookie, or redeemed restricted cookie is required for bootstrap. Restricted/bootstrap identity cannot read or mutate **any** `/api/*`:428 `password_change_required`, before parsing task data. First replacement additionally requires the current initial password. Once active, ordinary username/password login needs no legacy proof. Invalid legacy/token-login shapes remain rejected; static bearer no longer authorizes anything.

All full browser unsafe API requests still require exact Origin and CSRF. Native sessions are64hex opaque random tokens. Browser and native session kinds are separate: a native token in a cookie or a browser token in Authorization cannot authenticate. Browser and restricted bootstrap sessions expire after12 hours; fully authenticated native sessions remember the device for30 days so unattended task alerts do not stop twice daily. All remain valid across restart until their expiry/logout/password replacement. Every password replacement invalidates all old legacy, restricted, browser and native access through the persistent epoch/active state; stale concurrent login or password-change work cannot resurrect an old session. If a password-change response is lost, sign in using the new password rather than blindly replaying the change.

## Password hashing and limits

Passwords use random16-byte salts and `hashlib.scrypt(n=32768,r=8,p=3,maxmem=134217728,dklen=32)`, with constant-time digest comparison. Persisted cost/salt parameters are validated before hashing to prevent corrupt metadata triggering unbounded allocation. The initial-password reuse verifier also remains scrypt-hashed/salted; no fast unsalted password digest is retained. New passwords are15–128 Unicode codepoints, at most1024UTF-8 bytes, preserved exactly; no composition rule. Current, original initial, and a small explicit obvious-password blocklist are rejected. The blocklist is not a breached-password service or an MFA claim.

A process-wide bounded semaphore allows at most two simultaneous scrypt computations; saturation returns429 instead of accumulating hash jobs. Persistent account attempt limits run before expensive verification: five attempts per direct peer IP and30 globally per60seconds across login/activation/password changes, with Retry-After. No forwarded client IP is trusted. A reverse proxy consequently shares the direct-peer limit; use one process (`--workers 1`) and do not weaken the application limits casually. Rate limiting also applies to failed ownership/default-password attempts. Bounds are intentionally conservative for this single-owner service.

## Scratch-only QA

```sh
python qa_server.py --seed --seed-conversations --account --port 9138
```

This pins every task/token/event/session/conversation/account path inside a private scratch child, ignoring production path environment variables. It creates random test-only credentials for username `qa-owner`, with `qa-password` and `qa-activation` private0600 files. Startup output contains only their paths and the loopback origin. Read/use them privately; do not paste credentials into test reports. `--prepare-only` provisions without starting HTTP. Reusing a scratch root does not reset its password/account; use a new scratch root for a fresh bootstrap. Omitting `--account` explicitly permits the old uninitialized-token fixtures.

Run backend tests with:

```sh
python -W error::ResourceWarning -m unittest discover -s tests -p 'test_*.py' -v
```

Account HTTP tests start real loopback Uvicorn/TCP servers, using only scratch databases and QA-only insecure cookies. They cover ownership/default blocking, one-time/expired activation, API gating, Origin/CSRF/duplicate headers, strict/sanitized errors, native-cookie isolation, expiry/logout, durable restart/password state, epoch races/revocation, bounded hash/rate limits, corrupt scrypt parameters and private CLI provisioning/refusal. Existing token tests explicitly opt into uninitialized legacy fixtures. They do not weaken account-enabled production behavior.

The immutable Dockerfile explicitly includes `conversations.py`, `account_auth.py`, `account_routes.py`, and the local administration module. The generic compose example sets account/conversation paths but contains no password/activation credentials. Only the parent deployment owner provisions real credentials, publishes builds or deploys. The canonical notification adapter forwards the installed plugin's `notification.sections` unchanged; no canonical plugin/task database edits are needed. A mobile-service restart reloads updated installed plugin source.
