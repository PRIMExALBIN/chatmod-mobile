# Android Device QA

ChatMod Mobile still needs real phone or emulator proof before closed beta. This file turns the remaining Android checklist gates into a repeatable free local QA flow. It does not replace the evidence log in `docs/PRODUCTION_RELEASE_CHECKLIST.md`.

## Free Tooling

- Android Studio emulator or a physical Android phone with USB debugging.
- Android SDK platform-tools, especially `adb`.
- The checked-in Gradle wrapper.
- Optional Google Play pre-launch report after a closed-testing build is uploaded.

No paid mobile testing service is required for beta.

## Build The APK

From the repo root:

```powershell
cd android
.\gradlew.bat testInternalDebugUnitTest assembleClosedBetaDebug -PchatmodUseDemoApi=true --no-daemon
cd ..
```

The default device QA runner expects:

```text
android/app/build/outputs/apk/closedBeta/debug/app-closedBeta-debug.apk
```

For a physical phone hitting a local backend, rebuild with a LAN URL:

```powershell
cd android
.\gradlew.bat assembleClosedBetaDebug -PchatmodApiBaseUrl=http://192.168.1.20:4100
cd ..
```

For hosted beta testing, point the app at the free-tier backend URL:

```powershell
cd android
.\gradlew.bat assembleClosedBetaDebug -PchatmodApiBaseUrl=https://your-render-service.onrender.com
cd ..
```

## Run The Device QA Runner

Print the plan without requiring a device. This is the CI-safe mode:

```powershell
npm run android:device:qa -- --print-plan
```

Install and launch the closed-beta debug APK on the only connected device:

```powershell
npm run android:device:qa -- --install --launch
```

Install, launch, clear logcat before opening the app, and capture evidence files:

```powershell
npm run android:device:qa -- --install --launch --capture-evidence --clear-logcat
```

When multiple devices are connected:

```powershell
adb devices
npm run android:device:qa -- --device=<adb-serial> --install --launch --capture-evidence --clear-logcat
```

Override paths only when testing another artifact:

```powershell
npm run android:device:qa -- --apk=android/app/build/outputs/apk/internal/debug/app-internal-debug.apk --package=com.chatmod.mobile.internal --install --launch
```

## Automatic Checks

The runner verifies:

- `adb` is available from `ANDROID_HOME`, `ANDROID_SDK_ROOT`, common local SDK locations, or `PATH`.
- The target APK exists.
- A single online Android device is connected, or the selected `--device` is online.
- Device model and Android version can be read.
- The APK can be installed when `--install` is passed.
- The expected package is installed.
- The launcher activity opens when `--launch` is passed.
- The package dump exposes notification permission state when available.
- `--capture-evidence` writes a timestamped screenshot and recent logcat output under `android/app/build/qa-evidence/`.
- `--evidence-dir=<path>` can move evidence output when collecting release-review files.

## Manual QA Still Required

Run these flows before checking off Android device gates:

- Install and first launch.
- Room and DataStore migration smoke after upgrade/install.
- Command and timer editor sync against a real backend session.
- Account tab privacy controls: export, YouTube disconnect, backup delete, account delete copy.
- Settings backup export/restore and local data wipe.
- Support diagnostics, beta feedback, and recent API errors.
- Settings DataStore toggles: emergency mode, link lockdown, reduced motion, high contrast, analytics.
- Logs tab Room feed and local analytics cards.
- HTTP retry/backoff while backend is slow, offline, or recovers.
- Koin graph paths: Activity, foreground service, Quick Settings tile, WorkManager pending-sync drain.
- TalkBack through live controls, queue actions, account/privacy, support, settings, and logs.
- Text scaling at 130 percent, 160 percent, and 200 percent on a small phone.
- Dark mode, high contrast, and non-color-only status cues.
- One-handed live-control reach while queue is active.
- Foreground service while app is backgrounded and screen is locked during a test stream.
- Battery optimization warning on a non-exempt device.
- Poor network and airplane-mode recovery.
- Small phone, large phone, tablet/foldable, and orientation behavior.

## Evidence Template

Copy this shape into the evidence log when a gate is genuinely complete:

```text
- YYYY-MM-DD. Android device QA. Device: <model>, Android <version>, build <variant/versionCode>.
  Backend: <local LAN URL or hosted free-tier URL>. Stream: <private test stream URL or N/A>.
  Command: npm run android:device:qa -- --device=<serial> --install --launch --capture-evidence --clear-logcat.
  Result: <summary of passed flows>. Screenshots/logcat/video: <path or link>.
  Remaining issues: <none or issue IDs>.
```

Only mark checklist items complete after the evidence proves the exact behavior.
