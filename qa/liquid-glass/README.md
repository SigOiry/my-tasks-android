# Isolated native Liquid Glass QA

This directory owns **QA tooling only**. No release source, auth validator, production credentials, canonical production records, installed account or device package is changed by these tools. Do not use fixture responses as proof of a real model call or native visual quality.

## Architecture and existing contracts

- `runtime.py` uses the actual `server/mobile_api.py:create_app`, canonical dashboard `plugin_api.py` / `taskdb.Store`, `AccountStore`, event metadata and durable `ConversationStore` workers. Every writable path is explicitly rooted in a private scratch directory; production `MOBILE_*` storage defaults are not inherited. NAS execution imports the installed integration. Linux-local deployment bundles only its canonical Python code and relocates three filesystem literals in generated copies, failing closed on source drift; no API/auth/TLS implementation is replaced.
- Normal account bootstrap and `/auth/native/password` establish an isolated `qa-owner`; the legacy bootstrap token is then rejected. Native UI signs in through unchanged `/auth/native/login` and saves its issued opaque session using the unchanged Android `Store` (AndroidKeyStore + origin-bound encryption + account epoch).
- Only the model-call boundary `hermes_chat.call_llm` is replaced, process-locally, by an explicitly synthetic provider with `tools=[]`. No API routes/responses, revision calculation, unread tokens, admission, persistence, idempotency or restart handling are faked. Both seeded and newly generated assistant messages say **SYNTHETIC QA**. No provider credentials are read and no real model is called.
- Seed: 10 fictional tasks, exactly two projects (Research/Operations), 7 active nondeleted tasks, completed/cancelled/trash examples, relative dates, a wrapping title, and 2 genuine canonical unread updates. Two task conversations have 6 and 2 messages; one free conversation has 48 persisted messages, genuinely paginated by the real API.
- `overlay.py` generates a **new scratch-only** manifest and resources: application ID `com.simon.mytasks.qa`, explicit original Java component names, separate provider authority, visible Synthetic QA label and a trust anchor restricted to `127.0.0.1`. Cleartext remains disabled. It does **not** rewrite `Core.java`, `Api.java`, `Store.java`, TLS validation or login logic. `aapt2 --custom-package com.simon.mytasks` preserves the Java/R namespace.
- `build-qa.sh` builds current native Java sources into a separate APK using that overlay. It never invokes the production build/signing script or reads `.signing`. A new **QA-only** signing key is generated once per runtime root and reused for subsequent QA updates, preserving the QA account during iteration. Do not publish this APK as production.

Source references: `server/CONTRACT.md`, `server/AUTH-v1.3.md`, `server/qa_server.py`, `server/conversation_smoke.py`, `server/tests/test_fixture_isolation.py`, `android/src/com/simon/mytasks/{Core,Api,AuthClient,Store}.java`. Existing scratch directories may contain real session material: do not inspect them or copy their stores into this fixture. The existing QA helper's legacy-only mode and direct SQLite message seeds were deliberately not used for this native login/integration path.

## Start a fresh TLS runtime

Run from the repository root, using the Hermes Python environment with existing FastAPI, httpx, uvicorn and canonical integration dependencies; OpenSSL must be available:

```sh
python qa/liquid-glass/runtime.py \
  --root /opt/data/cache/scratch/liquid-glass-runtime-NEW \
  --port 9147
```

Or add `--prepare-only` to seed, activate the synthetic account and generate its self-signed certificate without serving. Resume the same root with the command above (never run two server processes against one conversation database). Existing nonempty unmarked directories and paths outside scratch are refused. Seeding never copies production tasks. If interrupted during first preparation, choose a **new** root rather than trying to salvage partial seeds.

The service binds only `127.0.0.1`, serves **HTTPS**, disables access logging and prints paths/username only. The public cert is `ROOT/qa-cert.pem` with IP SAN `127.0.0.1`; the private key is `ROOT/qa-key.pem`. Cert validity is 14 days. Generate a new runtime and QA APK when it expires. Do not install this certificate system-wide, use trust-all hostname verifiers, disable HTTPS or expose this fixture to the LAN/public internet.

Private synthetic login material lives only in `ROOT/qa-password` (0600). Do not print it, put it in this repository, embed it in the APK, send it in chat, or use an existing production session. Use normal QA-package sign-in, or a separately reviewed QA-only instrumentation mechanism. Username is `qa-owner`; server is `https://127.0.0.1:9147`.

Synthetic provider controls (explicit in the submitted message):

- `[fixture:slow]`: an additional six-second provider wait, through the real queued/running/succeeded lifecycle.
- `[fixture:fail]`: real durable failed request with normal sanitized server error handling. No fallback answer.

These do not change tasks, send email or grant tools. A failed/interrupted request remains in history. A manual retry must have a new request UUID; exact replay must not reexecute.

## Build a separate native QA APK

```sh
bash qa/liquid-glass/build-qa.sh \
  /opt/data/cache/scratch/liquid-glass-runtime-NEW \
  /opt/data/cache/scratch/liquid-glass-build-NEW
```

The output directory must not exist. Defaults are the verified local JDK `/opt/data/cache/android-build/jdk-17.0.2` and SDK `/opt/data/cache/android-build/sdk` (build-tools/platform 35); override `JAVA_HOME`/`ANDROID_HOME` deliberately if needed. The script compiles actual sources, dexes, aligns, signs with the runtime's QA identity, verifies signature/alignment and prints package badging. It does not install anything. Rebuild after parent source changes, keeping the same runtime root for the same QA signing identity. This is the existing direct Java build path, not a Compose/Gradle builder.

## Linux-host-local backend; parent-owned emulator deployment

**SSH forwarding is prohibited by the dedicated authorized key.** Remote forwards on both 9147 and 19147 were rejected with `Server has disabled port forwarding`. Do not retry alternate ports, make a socket relay, change sshd/authorized_keys, or expose the NAS fixture on LAN. SSH/SCP execution and code transfer remain authorized. Run the actual fixture backend on Linux loopback instead; host-side `adb reverse` can then reach it directly.

From the repository root on NAS, package the retained **entirely synthetic** fixture and deploy once to a new destination:

```sh
python qa/liquid-glass/linux_runtime.py \
  --root /opt/data/cache/scratch/liquid-glass-runtime-20261109 \
  --bundle /opt/data/cache/scratch/liquid-glass-linux-bundle-NEW \
  --deploy
```

The fixed Linux destination is `/home/simon/hermes-apks/qa-runtime`. Existing destinations are refused rather than overwritten. The helper discovers Python/pip, creates only a local `venv`, installs pinned direct dependencies, and runs `pip check`; it never installs a host service or global Python package. If `python3 -m venv` is unavailable, stop and request an authorized isolated bootstrap, not a system package installation. Discovered host Python is `/usr/bin/python3`, 3.12.3.

Packaging is allowlisted: ten actual server modules, unchanged `runtime.py`, the TLS verifier, `live_mutate.py` / `live_summary.py` scenario helpers, canonical `plugin_api.py` and `taskdb.py`, plus only the synthetic marker/tasks/cert/key/token/password/account and conversation databases. SQLite uses read-only backup snapshots rather than copying live WALs. Original NAS sessions, task backups, provider configuration, production account/database, QA signing keys/password and Android source are **not transferred**. Linux generates its own synthetic session database through normal login. Archive and transferred private files are 0600 under 0700 directories. Treat the archive as private synthetic credential material; do not publish or commit it. The code manifest records original and relocated hashes. Only three exact path literals are rewritten in generated copies (plugin location, taskdb location and scratch root); canonical originals are untouched.

Start only after checking `ss -ltnp sport = :9147` and the existing runtime PID. For a parent-managed foreground/background tool process, the exact SSH command is:

```sh
ssh -T -o BatchMode=yes -o StrictHostKeyChecking=yes \
  -o UserKnownHostsFile=/opt/data/ssh/android-known_hosts \
  -i /opt/data/ssh/android-testing simon@192.168.0.133 \
  'cd /home/simon/hermes-apks/qa-runtime && umask 077 && exec env -i HOME=/home/simon/hermes-apks/qa-runtime PATH=/usr/bin:/bin PYTHONNOUSERSITE=1 /home/simon/hermes-apks/qa-runtime/venv/bin/python qa/liquid-glass/runtime.py --root /home/simon/hermes-apks/qa-runtime/scratch/runtime --port 9147'
```

For the retained handoff, this same executable/arguments/environment was launched via remote `subprocess.Popen(..., start_new_session=True, stdin=DEVNULL, stdout=runtime.log, stderr=runtime.log)`. Its PID is in `runtime.pid` (0600); it survives SSH disconnect without a service installation. The parent owns cleanup: first check `/proc/$(cat runtime.pid)/cmdline` and `ss` to ensure the PID still belongs to this exact fixture, then send SIGTERM to **only that PID**. Do not start a second server against the same conversation DB, use blanket `pkill`, or replace the still-running synthetic account/certificate.

Independently verify real TLS health, anonymous denial, native password login, synthetic task/conversation reads and logout on Linux (password read privately, never printed):

```sh
ssh -T -o BatchMode=yes -o StrictHostKeyChecking=yes \
  -o UserKnownHostsFile=/opt/data/ssh/android-known_hosts \
  -i /opt/data/ssh/android-testing simon@192.168.0.133 \
  '/home/simon/hermes-apks/qa-runtime/venv/bin/python /home/simon/hermes-apks/qa-runtime/qa/liquid-glass/verify_linux.py --root /home/simon/hermes-apks/qa-runtime/scratch/runtime'
```

The verifier uses the copied **same certificate** as the installed QA APK, validates its IP SAN/expiry with normal TLS verification, disables proxy inheritance, and uses real API routes. It does not prove Android trust/login. Parent alone performs the device step on Linux, choosing the actual emulator serial if needed:

```sh
/home/simon/Android/Sdk/platform-tools/adb reverse tcp:9147 tcp:9147
```

The path is emulator loopback → ADB reverse → Linux `127.0.0.1:9147`; no SSH network forwarding. Transfer/compare/install only the separate QA APK if required; launch component `com.simon.mytasks.qa/com.simon.mytasks.MainActivity`. Preserve `com.simon.mytasks` and its account. Remove only `adb reverse --remove tcp:9147` after shared QA; never `reverse --remove-all`.

Updater UI is not a QA-release installation test: the production release feed/package identity does not match this separate QA APK. Do not apply production updates from inside the QA package.

## Native instrumentation trust and capture limits

`run_native.py` login reads only the synthetic fixture password. It omits the credential-bearing command from failure reporting, but passes that password transiently through instrumentation/SSH/ADB process arguments. This is **not** protection against local process inspection: trust the fixture NAS, Linux host, emulator/device, their administrators and any process inspectors. Use only the isolated synthetic account; never supply production credentials. Do not enable shell tracing, log full subprocess arguments or publish process listings from a login run.

Native instrumentation's `PixelCopy` PNG captures the Activity window, not the full OS display or a separate IME/system window. The host observer's `*-display.png` uses ADB `screencap` for the full display, including system/keyboard surfaces when visible. Use the full-display evidence for keyboard overlap claims; neither capture alone proves animation continuity.

The current request/ACK protocol identifies requests by name only. Consecutive identical host actions/capture names can time out because the observer deduplicates the name. Use distinct capture names and avoid consecutive duplicate host actions. A per-request nonce and credential-safe observer last-error reporting remain follow-up work; observer exceptions currently defer to the instrumentation timeout. Do not change just one side of the protocol while installed instrumentation is in use.

## Reproducible tests / limits

```sh
python -W error::ResourceWarning -m unittest discover -s qa/liquid-glass -p 'test_*.py' -v
(cd server && python -W error::ResourceWarning -m unittest discover -s tests -q)
```

The QA suite actually builds two APKs and verifies stable QA signing identity, package/component badging and generated trust scope, then exercises real canonical task mutation, stale revision conflict, newer unread token conflict, exact acknowledgement, persisted note edit, asynchronous provider admission, changed-payload conflict, exact idempotent replay with one provider call, pagination and restart persistence without replay. All test stores and build products use fresh scratch directories. Tests are not screenshots, motion evidence, physical-device evidence or proof of real provider quality.

The canonical server baseline passed **72 tests** before fixture implementation; the final QA suite passed **3 end-to-end tests**. A real TCP/TLS health/login/read/logout probe verified the retained service with certificate verification enabled: health 200, anonymous tasks 401, 10 tasks / 7 active / 2 projects / 2 unread, 3 threads, free history page 40 with older messages. No on-device TLS trust or login has yet been verified; that remains the parent's native deployment step.

## Ready handoff

- Running Linux service: `https://127.0.0.1:9147` (Linux loopback only, not NAS forwarding).
- Linux deployment: `/home/simon/hermes-apks/qa-runtime`; private fixture: `scratch/runtime` beneath it.
- Detached Linux PID: **392518**, recorded in `runtime.pid`; private output: `runtime.log`. Verify PID ownership before cleanup.
- Original NAS root: `/opt/data/cache/scratch/liquid-glass-runtime-20261109`; keep its certificate and QA signing identity for APK rebuilds. Do not restart/replace the Linux fixture while native QA uses it.
- Linux TLS probe returned health `ok`, anonymous tasks 401, normal native login `ok`, **10 tasks / 3 conversations**, then normal logout. Remote cert/password were byte-identical to the original synthetic fixture; 13 deployed runtime/integration code hashes matched the transfer manifest. No device navigation/install was performed by this Linux-workflow subagent.
- **Use this retained APK**: `/opt/data/cache/scratch/liquid-glass-qa-build-ready-20261109/my-tasks-qa.apk`.
- An earlier `liquid-glass-qa-build-20261109` was an intermediate signing experiment. Do not install it: the `ready` build uses the stable runtime QA identity.
- Rebuild from current parent sources into a new output directory with the same runtime root before final visual QA.
