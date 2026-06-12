# Android Build Verification

The Android project needs Android Studio or command-line Android tooling with JDK 17+.

## Required Tools

- JDK 17 or newer on `PATH`.
- Android SDK with API 35.
- Checked-in Gradle wrapper pinned to Gradle 8.10.2.

Verify the wrapper source before using it:

```powershell
npm run android:wrapper:check
```

## Verify Locally

```powershell
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

On Windows:

```powershell
cd android
.\gradlew.bat testInternalDebugUnitTest
.\gradlew.bat assembleClosedBetaDebug -PchatmodUseDemoApi=true
```

## Verify In Free CI

The GitHub Actions workflow includes an Android job that uses a free hosted Linux runner, JDK 17, Android SDK API 35, `gradle/actions/setup-gradle@v6` for caching, and the checked-in Gradle wrapper pinned to Gradle `8.10.2`:

```bash
cd android
./gradlew testInternalDebugUnitTest assembleClosedBetaDebug -PchatmodUseDemoApi=true --no-daemon
```

The CI job builds the closed-beta debug flavor in demo API mode so it does not require Render, Neon, Google OAuth, YouTube, Discord, or Play Console credentials.

## Backend Sync Configuration

Debug builds default to the Android emulator backend URL:

```text
http://10.0.2.2:4100
```

Override it when testing against a physical phone or hosted free-tier backend:

```powershell
cd android
gradle assembleDebug -PchatmodApiBaseUrl=http://192.168.1.20:4100
```

Use demo API mode when the backend is intentionally offline:

```powershell
cd android
gradle assembleDebug -PchatmodUseDemoApi=true
```

## What Must Pass

- Room KSP schema generation.
- Local unit tests for rule engine, command runtime, timer scheduler, and rate limiter.
- Compose source compilation for dashboard command/timer editors.
- Compose source compilation for the Account tab privacy controls, local data wipe, and destructive-action confirmations.
- Compose source compilation for the Support tab diagnostics workflow.
- Compose source compilation for the Settings tab DataStore controls.
- Compose source compilation for the Logs tab Room-backed chat message, moderation, and runtime event feed.
- Compose source compilation for dashboard accessibility helpers, live status semantics, and dense-row 48dp touch targets.
- HTTP backend client compilation for device sessions, entitlement, commands, timers, backups, and rule evaluation.
- HTTP backend client compilation for account export, YouTube disconnect, account deletion, and backup deletion.
- HTTP backend client compilation for settings backup and restore.
- HTTP backend client compilation for support diagnostic list/create.
- HTTP backend client compilation for API error listing in Support.
- HTTP backend client compilation for idempotent `GET`, `PUT`, and `DELETE` retry/backoff behavior.
- Foreground service manifest validation.
- Quick Settings tile manifest validation and source compilation.
- Closed beta debug APK source compilation in demo API mode on GitHub Actions.

## Device QA After Build

After producing an APK, use the free local ADB runner before checking off emulator/device gates:

```powershell
npm run android:device:qa -- --print-plan
npm run android:device:qa -- --install --launch
```

The full manual flow and evidence template are in `docs/ANDROID_DEVICE_QA.md`.

## Current Known Gap

Local verification completed on 2026-06-11 with a portable user-cache toolchain:

- Microsoft OpenJDK 17.0.19 at `%USERPROFILE%\.cache\chatmod-android-tools\jdk`.
- Android SDK command-line tools with API 35, build-tools 35.0.0, build-tools 34.0.0, and platform-tools at `%USERPROFILE%\.cache\chatmod-android-tools\android-sdk`.
- `.\gradlew.bat testInternalDebugUnitTest assembleClosedBetaDebug -PchatmodUseDemoApi=true --no-daemon` passed from the `android` directory.
- Closed-beta debug APK: `android/app/build/outputs/apk/closedBeta/debug/app-closedBeta-debug.apk`.

Future machines still need JDK 17+ and Android SDK API 35 before running the checked-in Gradle wrapper.
