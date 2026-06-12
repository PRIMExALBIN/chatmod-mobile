# Android Release Signing

ChatMod Mobile release builds must be signed with keystore secrets supplied from outside tracked source.

The local proof artifact uses an ignored test upload keystore in `release-evidence/android-signing/`. This proves the signing pipeline and release minification path. It is not the final Play upload key.

## Source Wiring

The Android Gradle build reads these env vars or matching Gradle properties:

- `CHATMOD_RELEASE_STORE_FILE`
- `CHATMOD_RELEASE_STORE_PASSWORD`
- `CHATMOD_RELEASE_KEY_ALIAS`
- `CHATMOD_RELEASE_KEY_PASSWORD`

The source gate is:

```powershell
npm run android:release:check
```

## Build A Signed Closed-Beta Release APK

Generate or provide an external keystore outside the repo. For local proof, use the ignored `release-evidence/android-signing/` folder.

Then run:

```powershell
cd android
.\gradlew.bat verifyReleaseSigningConfigured assembleClosedBetaRelease -PchatmodUseDemoApi=true --no-daemon
cd ..
```

Verify the artifact:

```powershell
npm run android:release:check -- --require-artifact
```

Expected artifact:

```text
android/app/build/outputs/apk/closedBeta/release/app-closedBeta-release.apk
```

## Current Local Proof

On 2026-06-11, the closed-beta release APK was built with:

- Portable JDK 17 from `%USERPROFILE%\.cache\chatmod-android-tools\jdk`
- Android SDK build-tools 35.0.0
- External ignored keystore at `release-evidence/android-signing/chatmod-upload-test.jks`
- `verifyReleaseSigningConfigured`
- `assembleClosedBetaRelease -PchatmodUseDemoApi=true --no-daemon`
- `apksigner verify --verbose --print-certs`

Result:

- APK: `android/app/build/outputs/apk/closedBeta/release/app-closedBeta-release.apk`
- Variant: `closedBetaRelease`
- Application ID: `com.chatmod.mobile.beta`
- Signature: APK Signature Scheme v2
- Signer: 4096-bit RSA local proof certificate
- Release-build warning cleanup: BuildConfig is enabled in the module DSL instead of the deprecated global Gradle property, and abuse-tracker queue trimming uses null-safe `peekFirst()` access.
- Dashboard icon warning cleanup: directional Rule and Send icons use AutoMirrored Material icons instead of deprecated default directional icons.

## Still External

Before Play upload, replace the local proof keystore with the real Play upload key managed outside the repo. Keep the Play Console subscription, closed-testing install, purchase/restore, and policy-submission gates open until verified in Play Console.
