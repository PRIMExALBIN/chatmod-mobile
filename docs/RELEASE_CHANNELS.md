# Release Channels

ChatMod Mobile uses Android product flavors for install-safe release channels before Google Play tracks are connected.

## Channels

| Channel | Flavor | Application ID | Label | Purpose |
| --- | --- | --- | --- | --- |
| Internal | `internal` | `com.chatmod.mobile.internal` | ChatMod Internal | Developer and smoke-test builds. |
| Closed beta | `closedBeta` | `com.chatmod.mobile.beta` | ChatMod Beta | Creator beta builds before public launch. |
| Production | `production` | `com.chatmod.mobile` | ChatMod Mobile | Public Play Store release. |

## Build Commands

These require JDK 17+, Android SDK, and a Gradle wrapper or local Gradle install:

```powershell
./gradlew :app:assembleInternalDebug
./gradlew :app:assembleClosedBetaRelease
./gradlew :app:assembleProductionRelease
```

The free GitHub Actions CI path installs JDK 17, Android SDK API 35, and Gradle 8.10.2 on the runner, then builds the closed-beta debug flavor with the checked-in wrapper:

```bash
cd android
./gradlew testInternalDebugUnitTest assembleClosedBetaDebug -PchatmodUseDemoApi=true --no-daemon
```

Local internal smoke artifact verified on 2026-06-11:

```powershell
cd android
.\gradlew.bat :app:assembleInternalDebug -PchatmodUseDemoApi=true --no-daemon
```

## Release Signing

Release signing is configured from environment variables or local Gradle properties so keystores and passwords never need to enter the repo.

Environment variables:

```text
CHATMOD_RELEASE_STORE_FILE=/secure/path/chatmod-release.jks
CHATMOD_RELEASE_STORE_PASSWORD=<secret>
CHATMOD_RELEASE_KEY_ALIAS=<alias>
CHATMOD_RELEASE_KEY_PASSWORD=<secret>
```

Equivalent Gradle properties:

```properties
chatmodReleaseStoreFile=/secure/path/chatmod-release.jks
chatmodReleaseStorePassword=<secret>
chatmodReleaseKeyAlias=<alias>
chatmodReleaseKeyPassword=<secret>
```

Verify signing before making a release artifact:

```bash
gradle -p android :app:verifyReleaseSigningConfigured
```

Then build a signed release flavor:

```bash
gradle -p android :app:assembleClosedBetaRelease
gradle -p android :app:assembleProductionRelease
```

Keep the keystore outside the repository. `.gitignore` excludes common keystore extensions as a last line of defense, but the source of truth should be local secure storage or the release platform's encrypted secret storage.

## Free-Tier Posture

The channel split does not require paid services. Google Play publishing still requires the one-time developer registration fee, and the closed-testing/production tracks should not be marked complete until Play Console credentials and signing are configured.

## Rules

- Internal and beta builds must be installable beside production builds.
- Production keeps the final package name `com.chatmod.mobile`.
- Store listing, OAuth verification assets, screenshots, and signing setup remain separate launch tasks. Use `docs/PRODUCTION_RELEASE_CHECKLIST.md` as the release control document.
