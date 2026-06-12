# Production Release Checklist

This checklist is the launch control document for ChatMod Mobile. It separates source-complete work from external verification so the project does not mark production ready until the evidence exists.

## Release Principle

ChatMod Mobile should ship closed beta before public launch. The beta path should stay free-tier friendly: phone-hosted runtime, Render Free backend, Neon Free Postgres, static launch site, first-party diagnostics, and no paid object storage until exports or backups outgrow encrypted Postgres rows.

## Source Gates

- [x] Backend tests pass with `npm run test`.
- [x] Backend TypeScript build passes with `npm run build`.
- [x] Prisma schema validates with `npm run backend:db:validate`.
- [x] OpenAPI YAML parses.
- [x] Free-tier production source posture passes with `npm run production:check`.
- [x] Android free-tier CI job is configured for JDK 17, Android SDK API 35, Gradle 8.10.2, unit tests, and closed-beta debug assembly.
- [x] Android Gradle wrapper is checked in, pinned to Gradle 8.10.2, and verified with `npm run android:wrapper:check`.
- [x] Android Room/DataStore source validation is wired with `npm run android:data:check`.
- [x] Android Koin dependency injection source wiring is verified with `npm run android:di:check`.
- [x] Android UX/accessibility source validation is wired with `npm run android:ux:check`.
- [x] Android device QA runner is wired with `npm run android:device:qa -- --print-plan`.
- [x] Android device QA evidence capture is wired for ADB screenshot/logcat output with `--capture-evidence --clear-logcat`.
- [x] External release evidence manifest and validator are wired with `npm run release:evidence:check` for Google/YouTube, Android, Render/Neon, Play Console, and creator beta proof.
- [x] Release signing source wiring uses external env vars or Gradle properties, with keystore files excluded from the repo.
- [x] Launch-site beta interest source posts to the backend support-event path instead of a static placeholder.
- [x] Free-tier data retention pruning source is wired and dry-run-first.
- [x] Backend deployment smoke test source is wired for free-tier hosted verification.
- [x] Production environment preflight source is wired for Render/Neon/OAuth/Play secret validation.
- [x] Launch-site static validation source is wired for Cloudflare Pages-ready policy/form checks.
- [x] Play Store listing and Data Safety source validation is wired with `npm run store:check`.
- [x] Backend login/session integration test covers device-session token issuance, authenticated route access, missing/invalid token request IDs, and OAuth-not-configured copy.
- [x] Android Google Play Billing source flow is wired for product query, purchase, restore, backend validation, and acknowledgement.
- [x] Lifetime purchase is explicitly not offered for MVP/public v1; Play Billing source and docs remain subscription-only until a deliberate pricing revision.
- [x] OBS/browser overlay source is wired with tokenized public HTML/state, Android Settings controls, Pro/Creator entitlement gating, OpenAPI coverage, and backend tests.
- [x] Team moderator access source is wired with profile-scoped invites, hashed invite codes, Android Settings controls, `teamSeats` enforcement, OpenAPI coverage, and backend tests.
- [x] Static admin web dashboard source is wired with support lookup, beta-interest review, entitlement adjustment, ticket metadata, dashboard docs, and launch-site validation.
- [x] Tutorial video source/render artifact is prepared with HyperFrames source, storyboard, script, free local FFmpeg tooling, and `npm run promo-video:check`.
- [x] Android release-build warning cleanup is source-guarded for module-level BuildConfig and null-safe abuse-tracker queue trimming.
- [x] Android dashboard directional icon warning cleanup is source-guarded with AutoMirrored Rule and Send icons.
- [x] Dependency audit has no moderate-or-higher findings with `npm audit --audit-level=moderate`.
- [x] Android build passes on a machine with JDK 17+, Android SDK, and the checked-in Gradle wrapper.
- [ ] Android Room/DataStore migrations are verified on emulator or device.
- [x] Signed closed-beta release artifact produced with external ignored local keystore secrets; real Play upload key remains external to the repo.

## Backend Deployment Gates

- [ ] Neon Free project created for beta Postgres.
- [ ] Render Free web service created from `render.yaml`.
- [ ] Production environment variables set in host secret storage.
- [ ] `npm run backend:env:check -- --json` passes with hosted production secrets.
- [ ] `npm run backend:db:deploy` run against hosted Postgres.
- [ ] Retention dry run reviewed with `npm run backend:retention:prune -- --json`.
- [ ] `/health` returns `200`.
- [ ] `/health/ready` returns `200`.
- [ ] `/app/compatibility` returns the expected supported version floor.
- [ ] `npm run backend:smoke -- --base-url=<deployed backend> --require-database` passes.
- [ ] `/youtube/connect-url` returns configured OAuth state in staging.
- [ ] API errors include `x-request-id`.

## Google And YouTube Gates

- [ ] Google OAuth consent screen configured.
- [ ] Development OAuth client configured with backend callback URL.
- [ ] Production OAuth client configured with production callback URL.
- [ ] OAuth scopes match `docs/OAUTH_PERMISSIONS.md`.
- [ ] Real Google OAuth flow verified with the connected bot channel.
- [ ] Private test stream verifies live-chat discovery, send, delete, timeout, and hide behavior.
- [ ] Bot-not-moderator test verifies permission error copy and recovery guidance.
- [ ] Token revocation verified after disconnect.

## Android Beta Gates

- [x] Installable internal build produced.
- [x] Installable closed-beta build produced.
- Use `docs/ANDROID_DEVICE_QA.md` and `npm run android:device:qa -- --install --launch` to collect evidence for the device gates below.
- [ ] Koin graph verified through Activity, foreground service, Quick Settings tile, and WorkManager paths on device.
- [ ] Account tab privacy controls verified on device.
- [ ] Settings backup and restore verified on device.
- [ ] Local data wipe verified on device.
- [ ] Support diagnostics and beta feedback verified on device.
- [ ] TalkBack, text scaling, high contrast, and one-handed live-control QA completed from `docs/ANDROID_UX_ACCESSIBILITY.md`.
- [ ] Foreground service survives backgrounding and screen lock during a test stream.
- [ ] Battery optimization warning verified on a non-exempt device.
- [ ] Poor-network and airplane-mode recovery verified.

## Store And Policy Gates

- [ ] Play Console subscription products configured for `chatmod_pro_monthly` and `chatmod_creator_monthly`.
- [ ] Google Play purchase validation verified with real Play Console credentials.
- [ ] Purchase and restore flow tested from a Play-installed closed-testing build.
- [ ] Support email chosen and monitored.
- [x] `npm run launch-site:check` passes before static site deploy.
- [ ] Privacy policy deployed from `launch-site/privacy.html`.
- [ ] Terms page deployed from `launch-site/terms.html`.
- [ ] Launch-site beta-interest form verified against the deployed backend with production `CORS_ORIGIN`.
- [ ] Static admin web dashboard verified against deployed backend with production `CORS_ORIGIN` and rotated `ADMIN_API_KEY`.
- [ ] OBS browser source verified against the deployed backend URL and an actual OBS scene.
- [ ] Team invite/redeem/revoke flow verified on two Android devices or emulators.
- [ ] `npm run store:check` passes against the final listing, policy pages, and Android manifest.
- [ ] Phone screenshots captured from real Android build.
- [ ] Google Play listing reviewed from `docs/PLAY_STORE_LISTING.md`.
- [x] Play Data Safety source worksheet prepared in `docs/PLAY_DATA_SAFETY.md`.
- [ ] Play Data safety answers completed from final behavior.
- [x] OAuth verification packet prepared from `docs/OAUTH_VERIFICATION_ASSETS.md`.
- [ ] OAuth demo video recorded if Google requests it.
- [ ] Closed testing track created.
- [ ] Open beta track created only after closed beta feedback is stable.

## No-Paid-Service Guardrail

Before adding any paid service, confirm that one of these is true:

- A free/open-source option cannot satisfy the beta requirement.
- A platform fee is unavoidable for the release channel, such as Google Play Console registration.
- The usage has outgrown free-tier limits and the upgrade has a measurable reason.

Current MVP default: keep backups and account exports in encrypted Postgres records, keep analytics and crash reporting first-party, and host the launch site as static files.
Use `docs/DATA_RETENTION.md` before upgrading storage: prune safely first, then add paid storage only when retention would harm the product.

## Evidence Log

When a gate is completed, record:

- Date.
- Environment or device.
- Command, URL, or Play Console screen used.
- Result summary.
- Any request ID, build number, or screenshot reference needed for support.

Completed evidence:

- 2026-06-12. Local Android emulator `emulator-5554`, Android 15, model `sdk_gphone64_x86_64`, 1080x2400 at 420 dpi. `npm run android:device:qa -- --device=emulator-5554 --install --launch --capture-evidence --clear-logcat`. Result: closed-beta debug APK installed, `com.chatmod.mobile.beta` package verified, launcher opened `MainActivity`, package dump exposed notification permission state, app process remained resumed with no app crash signature in captured logcat, and dashboard screenshot confirmed the large-phone viewport. Evidence: `android/app/build/qa-evidence/2026-06-12T15-12-48-556Z-emulator-5554/launch-screen-after-wait.png` and `logcat.txt`. Deeper manual Android QA gates remain open.
- 2026-06-12. Local Android emulator `emulator-5554`, Android 15, model `sdk_gphone64_x86_64`, small-phone override 720x1280 at 320 dpi. Result: `com.chatmod.mobile.beta` relaunched into `MainActivity`, remained resumed, and the dashboard rendered without blocking overlap in the small-phone viewport. Evidence: `android/app/build/qa-evidence/2026-06-12T15-18-56-728Z-emulator-5554/small-phone-light.png`. Tablet/foldable, dark mode, orientation, and deeper manual Android QA gates remain open.
- 2026-06-11. Local workspace. Android dashboard release icon cleanup. Result: deprecated directional `Icons.Default.Rule` and `Icons.Default.Send` usages were migrated to `Icons.AutoMirrored.Filled.Rule` and `Icons.AutoMirrored.Filled.Send`, with `npm run production:check` guarding against regression.
- 2026-06-11. Local workspace. Android release-build warning cleanup. Result: removed deprecated `android.defaults.buildfeatures.buildconfig` from `android/gradle.properties`, kept module-level `buildConfig = true`, and changed abuse-tracker queue trimming to null-safe `peekFirst()` access. `npm run production:check` guards both source conditions.
- 2026-06-11. Local workspace with portable Microsoft OpenJDK 17.0.19 and Android SDK build-tools 35.0.0. `.\gradlew.bat verifyReleaseSigningConfigured assembleClosedBetaRelease -PchatmodUseDemoApi=true --no-daemon` from `android`, using an ignored local keystore under `release-evidence/android-signing/`. Result: signed closed-beta release APK produced at `android/app/build/outputs/apk/closedBeta/release/app-closedBeta-release.apk`. `apksigner verify --verbose --print-certs` verifies APK Signature Scheme v2, one signer, and 4096-bit RSA cert. This proves the signing pipeline; the real Play upload key and Play-installed purchase/restore gates remain external.
- 2026-06-11. Local workspace. `npm run release:evidence:check` and `npm run release:evidence:check -- --print-required`. Result: external proof packet template and validator are ready for backend deployment, Google/YouTube OAuth/live stream, Android device QA, Play Console/store policy, and creator beta acceptance gates. The `release-evidence/` folder is ignored so screenshots, logs, and redacted command output can be collected locally without committing secrets.
- 2026-06-11. Local workspace. `npm run android:device:qa -- --print-plan` plus source inspection. Result: Android device QA runner now supports `--capture-evidence --clear-logcat`, timestamped screenshot capture through ADB `exec-out screencap -p`, and recent logcat capture under `android/app/build/qa-evidence/`. Real device/emulator execution remains an external open gate.
- 2026-06-11. Local workspace. `npm --prefix chatmod-mobile-promo run check`, local HyperFrames render, thumbnail inspection, and `npm run promo-video:check`. Result: tutorial video source, storyboard, script, free local FFmpeg/FFprobe tooling, 20.0 second MP4 at `chatmod-mobile-promo/renders/chatmod-mobile-tutorial.mp4`, and thumbnail at `chatmod-mobile-promo/renders/chatmod-mobile-tutorial-midpoint.jpg` are prepared. OAuth review demo video remains a separate open external gate.
- 2026-06-11. Local workspace. Backend overlay route test in `npm --workspace backend run test` plus Android Settings source wiring. Result: verifies Starter gating, Pro overlay setup, public OBS HTML, sanitized public state from stream-session audit logs, hidden chat text by default, token rotation invalidating old URLs, and phone-side overlay controls. OBS app/deployed-domain verification remains an external open gate.
- 2026-06-11. Local workspace. Backend team access route test in `npm --workspace backend run test` plus Android Settings source wiring. Result: verifies Starter team-seat blocking, Pro invite creation, one-seat limit enforcement, invite redemption by another device, membership listing, and revocation. Two-device Android QA remains an external open gate.
- 2026-06-11. Local workspace. `npm run launch-site:check`. Result: 95 passed, 0 failures. Verifies static launch-site source before Cloudflare Pages deploy; deployment and public URLs remain separate open gates.
- 2026-06-11. Local workspace with portable Microsoft OpenJDK 17.0.19 and Android SDK command-line tools. `.\gradlew.bat testInternalDebugUnitTest assembleClosedBetaDebug -PchatmodUseDemoApi=true --no-daemon` from `android`. Result: build successful, 73 unit tests passed, closed-beta debug APK produced at `android/app/build/outputs/apk/closedBeta/debug/app-closedBeta-debug.apk`.
- 2026-06-11. Local workspace with portable Microsoft OpenJDK 17.0.19 and Android SDK command-line tools. `.\gradlew.bat :app:assembleInternalDebug -PchatmodUseDemoApi=true --no-daemon` from `android`. Result: build successful, internal debug APK produced at `android/app/build/outputs/apk/internal/debug/app-internal-debug.apk`.
