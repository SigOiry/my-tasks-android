# My Tasks for Android

Native Android companion for a self-hosted My Tasks service. Designed for compact phone windows and larger/foldable windows, with task lists grouped by project/category, priority/deadline ordering, task editing, update summaries, and task-linked Hermes questions/email drafts.

## Install

Download `my-tasks.apk` from this repository's Releases page and open it on your Android phone. Android may ask you to allow installation from the browser or file manager. Keep platform protections enabled and inspect any warning.

- Package: `com.simon.mytasks`
- Android 8.0 (API26) or later.
- A compatible My Tasks backend with trusted HTTPS is required for task data and Hermes features.
- The app contains **no backend token**. Enter your own HTTPS origin and token in Settings.

## Features

- Compact single-pane and expanded two-pane native layouts.
- Project/category grouping, priority/deadline ordering, active/today/week/all/trash views.
- Revision-protected task edits, completion, recoverable deletion and exact-version unread acknowledgement.
- App-private read-only offline cache; encrypted credentials via Android Keystore; backups disabled.
- Self-hosted background task-update polling, not instant push. Android can delay checks beyond the 15-minute minimum.
- Task-linked model questions and email drafts. The app does not send email or expose unrestricted agent tools.
- Optional platform voice recognition with editable transcription and explicit sending.

## Build

Source is in `android/`. Install JDK17 and Android SDK platform35/build-tools35.0.0, then:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
bash android/test.sh
bash android/build.sh
```

A local build generates its own private signing identity if none is supplied. It **cannot update the official app** unless it is signed with the same official release key. For an authorized release, supply `SIGNING_KEYSTORE`, `SIGNING_PASSWORD_FILE` and `SIGNING_ALIAS` through your private build environment. Never commit signing material or server credentials.

## Verification boundary

The app has JVM-tested policy logic and a real signed APK build. Physical-device visual behavior, fold/unfold transitions, speech recognition and background delivery still require device QA. A compiled APK is not evidence of those runtime behaviors.

See `android/README.md` for implementation details. No private server configuration, tasks, mailbox data, runtime state, tokens or signing keys are published in this repository.
