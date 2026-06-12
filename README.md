# ChatMod Mobile

Custom YouTube Live moderation from your phone.

ChatMod Mobile is a full-stack Android-first product. The phone runs the live bot as a foreground service, while the backend handles account metadata, entitlement, profile backup, audit trails, support diagnostics, and future team features.

## What Is Here

- `android/` - Android app skeleton with Jetpack Compose, Material 3 direction, bot foreground service, YouTube client contracts, local moderation rule engine, Room local data, and DataStore settings.
- `backend/` - TypeScript Fastify API with health, account, profile, entitlement, backup, command, timer, log, YouTube setup, and moderation evaluation routes.
- `launch-site/` - static public launch page plus support/admin web dashboard for free hosting on Cloudflare Pages, GitHub Pages, or another static host.
- `marketing-assets/` - release-prep visual assets such as the 512 x 512 Play Store icon export.
- `chatmod-mobile-promo/` - HyperFrames source, script, storyboard, and rendered tutorial video artifact.
- `shared/contracts/openapi.yaml` - API contract for app/backend integration.
- `PRODUCT.md` and `DESIGN.md` - product and visual direction, including the latest Gemini app and Material 3 Expressive inspiration.
- `BUILD_CHECKLIST.md` - feature checklist and production roadmap.
- `docs/USER_FLOWS.md` - onboarding, going-live, moderation, after-stream, and backend ownership flow diagrams.
- `docs/FREE_TIER_STACK.md` - free/open-source and free-tier infrastructure plan, including the Render + Neon beta default.
- `docs/DATA_RETENTION.md` - dry-run-first retention policy for staying inside free Postgres/storage limits during beta.
- `docs/DEPLOYMENT_SMOKE_TESTS.md` - public-route smoke checks for local, staging, and free-tier Render/Neon deploys.
- `docs/PRODUCTION_ENV_PREFLIGHT.md` - production environment validation before starting a hosted backend.
- `docs/ANDROID_LOCAL_DATA.md` - Room/DataStore migration posture, local wipe coverage, and source gate.
- `docs/ANDROID_DEPENDENCY_INJECTION.md` - Koin dependency graph, source gate, and remaining runtime QA.
- `docs/ANDROID_UX_ACCESSIBILITY.md` - Android UX/accessibility source gate and remaining manual QA checklist.
- `docs/ANDROID_RELEASE_SIGNING.md` - external-keystore release signing, signed closed-beta artifact proof, and Play upload-key separation.
- `launch-site/README.md` - Cloudflare Pages-ready static launch-site deployment and validation notes.
- `docs/RELEASE_CHANNELS.md` - Android internal, closed beta, and production flavor plan.
- `docs/PRICING.md` - free-first pricing model and paid-plan guardrails.
- `docs/TERMS.md` and `docs/PRIVACY_DRAFT.md` - launch policy drafts that still need legal review before public release.
- `docs/SUPPORT.md` - beta support workflow, troubleshooting checks, and FAQ.
- `docs/GETTING_STARTED.md`, `docs/BOT_ACCOUNT_SETUP.md`, and `docs/YOUTUBE_MODERATOR_SETUP.md` - beta setup flow from local backend to bot channel moderator setup.
- `docs/PLAY_STORE_LISTING.md` and `docs/PLAY_DATA_SAFETY.md` - Play Store listing copy, Data Safety worksheet, and policy-review source.
- `docs/PRODUCTION_RELEASE_CHECKLIST.md` and `docs/OAUTH_VERIFICATION_ASSETS.md` - production launch gates and Google OAuth review packet.
- `docs/EXTERNAL_RELEASE_EVIDENCE.md` and `docs/release-evidence.template.json` - secret-safe evidence packet for external Google/YouTube, Android, Render/Neon, Play Console, and creator beta proof.
- `docs/TUTORIAL_VIDEO.md` - tutorial video artifact, render commands, and source validation notes.
- `docs/OBS_BROWSER_OVERLAY.md` - Pro/Creator OBS browser overlay setup, privacy behavior, and free-tier source evidence.
- `docs/TEAM_ACCESS.md` - Pro/Creator team moderator invite, redeem, revoke, and seat-limit behavior.
- `docs/WEB_DASHBOARD.md` - static admin/support web dashboard behavior, source gate, and security model.
- `docs/BRAND_ASSETS.md` - launcher icon, adaptive icon, and splash/launch screen asset notes.
- `docs/BETA_FEEDBACK.md` and `docs/ANALYTICS.md` - first-party beta feedback, opt-in usage analytics, and local Logs tab analytics posture.
- `docs/BATTERY_OPTIMIZATION_GUIDE.md`, `docs/DEVICE_BACKGROUND_RESTRICTIONS.md`, `docs/EMERGENCY_MODE.md`, `docs/RULE_PRESETS.md`, `docs/WARNINGS_AND_STRIKES.md`, `docs/MANUAL_MODERATION_ACTIONS.md`, `docs/WHITELIST.md`, and `docs/LOG_EXPORTS.md` - phone-hosted runtime, moderation tuning, and audit export guidance.
- `docs/YOUTUBE_LIVE_CHAT.md` - YouTube Live chat message types, deleted/system event behavior, Super Chat/member handling, and Live chat vs Top chat guidance.

## Local Backend

```powershell
npm install
npm run backend:dev
```

The API defaults to `http://localhost:4100`.

The backend uses in-memory stores under `NODE_ENV=test` and Prisma-backed stores when `DATABASE_URL` is configured outside test mode.

YouTube OAuth support includes signed OAuth state, callback code exchange, encrypted backend token storage, linked channel metadata capture, refreshed access-token persistence, and a Google YouTube Data API adapter. It still needs real Google OAuth credentials and a test livestream before it can be marked end-to-end complete.

Rule presets include opt-in severe-match auto-hide: high-confidence delete rules can also emit a `hideUser` action, which the Android runtime executes through the existing YouTube hide-user path and records in audit logs.

Discord alerts are a Pro/Creator integration: the backend validates Discord webhook URLs, stores them encrypted, returns only safe configured/enabled status to Android, and excludes raw webhook secrets from account export. The Android Settings tab can save, delete, and test the webhook, and the foreground runtime sends count-only moderation alerts without including chat text.

OBS/browser overlays are a Pro/Creator integration: the backend creates a read-only public overlay URL with a hashed token, renders a transparent-friendly HTML overlay for OBS browser sources, and polls sanitized stream-session audit state without adding a paid overlay service.

Team moderator access is a Pro/Creator integration: creators can create profile-scoped team invite codes from Android Settings, invited moderators can redeem on their own phone, and creators can revoke access without adding a paid workspace or realtime database.

The static web dashboard at `launch-site/admin.html` gives beta support operators device lookup, beta-interest review, entitlement correction, ticket metadata, and backend readiness checks through the existing `/admin/support/*` API without adding a paid helpdesk or dashboard platform.

## Backend Verification

```powershell
npm run backend:test
npm run backend:build
```

## Local Services

```powershell
docker compose up -d
```

This starts PostgreSQL and Redis for production-style local development.

Apply the Prisma migrations locally:

```powershell
$env:DATABASE_URL='postgresql://chatmod:chatmod@localhost:5432/chatmod'
npm --workspace backend run prisma:migrate
```

More detail is in `docs/LOCAL_DB.md`.

## Android

The Android app is under `android/`. It is designed for Android Studio with JDK 17+ and the Android SDK installed.

The current machine does not have Java on PATH, so Android compilation cannot run locally here yet. The Android project now includes a checked-in Gradle wrapper pinned to Gradle 8.10.2, and the free GitHub Actions CI workflow is configured to install JDK 17 and Android SDK API 35, then run Android unit tests and assemble the closed-beta debug build through `./gradlew`.

Android verification steps are documented in `docs/ANDROID_BUILD.md`.

Billing setup and entitlement rules are documented in `docs/BILLING.md`, including active, trialing, grace/past-due, canceled, and expired access behavior. The first free-first pricing decision is documented in `docs/PRICING.md`.

Backend deployment is documented in `docs/DEPLOYMENT.md`, including the root `render.yaml` free-tier backend blueprint, production env guardrails, security headers, request ID error correlation, YouTube public error codes, and `/health/ready`.
Security notes are documented in `docs/SECURITY.md`, including the token custody decision: Google OAuth tokens stay encrypted on the backend, while Android only uses short-lived backend device-session tokens.
Secret generation and free-tier-friendly rotation are documented in `docs/SECRETS_MANAGEMENT.md`.
Prisma-backed cloud backup configs are encrypted with the backend key ring before being stored in Postgres, while restore and account export decrypt them only for the authenticated creator.

Free-tier stack constraints are documented in `docs/FREE_TIER_STACK.md`. The repo should remain runnable locally without paid cloud services; the hosted beta default is Render Free for the Docker backend plus Neon Free Postgres, and Google Play publishing is the one known external path with a required one-time registration fee.

Android local-first work currently includes:

- Room entities and DAOs for commands, timers, chat message logs, moderation logs, bot runtime events, and pending sync jobs.
- DataStore settings for selected profile, emergency mode, link lockdown, reduced motion, and low-data mode.
- Command runtime, persisted-in-loop command cooldown state, timer scheduler, moderation action rate limiter, shared bot-message rate limiting, and source wiring for command/timer sends inside the bot coordinator.
- Manual command send path from Android Commands tab through authenticated backend route to the selected YouTube Live chat, with backend rendering and live-chat text validation.
- The foreground runtime loads enabled Room commands and timers on each poll, carries command cooldown state, tracks timer chat activity, respects stream-relative timer quiet windows, persists timer sent timestamps, and pauses scheduled timer sends while emergency mode is active.
- Non-demo foreground runtime now uses backend YouTube live-chat endpoints for message polling, command/timer sends, message deletes, and direct hide/timeout actions; demo builds still use the local mock client.
- The foreground runtime watches the cached active rule preset from DataStore, including expiring temporary trusted-channel entries, and overlays emergency/link-lockdown controls while running.
- Backend and Android rule engines support blocked terms, regex patterns, link policy, domain allow/block lists, caps, repeated characters, emoji spam, mention spam, and trusted owner/mod/member bypass.
- Backend entitlement gates keep Starter on basic moderation filters while Pro/Creator unlock advanced regex, domain, member-bypass, symbol/emoji/mention, and raid/new-chatter controls.
- The backend exposes curated moderation templates for Family friendly, Gaming default, Education/Q&A, Music/live performance, and High-security raid mode; Android can apply one as the active synced preset or save a custom preset copy.
- Android runtime moderation also tracks repeated-message spam, message floods, and suspicious bursts of first-time chatters, while the rule engine flags high-density symbol spam.
- The Android bot coordinator suppresses repeated YouTube message IDs with a bounded in-memory cache so repeated polls cannot retrigger deletes, replies, timers, or log writes.
- Foreground-service notification actions for stop, emergency mode, and link lockdown, plus runtime heartbeat events, network-loss pause behavior, YouTube quota/rate-limit backoff events, and reconnect backoff for phone-hosted moderation.
- Foreground notification health text reflects waiting-for-network, reconnecting, YouTube backoff, emergency, and link-lockdown states.
- Emergency mode enables the high-security raid posture, tightens runtime moderation thresholds including first-time chatter bursts, can temporarily time out repeat/flood spammers, and pauses timers; link lockdown switches the runtime profile to delete live-chat links while active.
- Live controls include a one-tap subscribers-only/members-only recommendation message that sends through the connected bot channel during spam spikes.
- Partial wake lock is held only while the bot loop runs and renewed with heartbeat events to support screen-lock operation.
- Runtime start logs when Android battery optimization may restrict the bot.
- Settings shows a battery optimization warning when Android may restrict long live bot sessions.
- Dashboard live status includes a compact bot-health strip with live-chat readiness, sync state, queue depth, and recent YouTube quota/rate-limit API warning signals.
- Dashboard live status uses non-clickable status chips, screen-reader state summaries, and 48dp minimum touch targets on dense moderation controls.
- Dashboard live controls add a stream-time bottom sheet for pause/start, emergency mode, and link lockdown without requiring paid infrastructure.
- Dashboard sync state distinguishes ready, syncing, reconnecting, network offline, and hard failure states for stream setup.
- Dashboard bottom navigation keeps Queue, Feed, Users, Rules, and Logs reachable one-handed during a stream, while secondary sections remain in the tab chip row.
- Dashboard live workspace tabs keep Queue, Feed, and Controls together so creators can move between triage, activity, and high-pressure actions without leaving the live context.
- High-intent mobile actions use haptic feedback for start/stop, destructive deletes, timeout, and hide/ban taps.
- Active bot runtime context is persisted in DataStore so sticky foreground-service restarts can resume the same stream/session.
- The dashboard watches that persisted runtime context and asks the foreground service to recover it if the app opens after Android killed or restarted runtime work.
- Last selected stream context is persisted in DataStore and passed into the foreground service when the bot starts.
- Quick Settings tile source wiring starts the last selected stream or stops the active bot runtime from the system shade.
- Terminal YouTube auth/permission failures log a dedicated runtime event, clear active runtime state, and stop the foreground service instead of retrying forever.
- Service-stop cleanup records a runtime stopped event and closes the backend stream session when connectivity/auth are available.
- Stream-ended signals stop the foreground service, clear persisted runtime state, log the end event, and queue backend stream-session closure.
- Foreground runtime session summaries are saved as local/cloud-syncable runtime events with duration, processed messages, duplicate skips, deletes, hidden users, command usage counts, and timer send counts.
- In-app command and timer editor UI with validation, Room persistence wiring, bulk pause/resume timer controls, local-first behavior, and authenticated HTTP sync source wiring. Emulator/device verification is still pending until Android tooling is installed.
- Backend command response safe URL validation and Android HTTP retry/backoff source wiring for idempotent backend calls.
- Android outbound bot-message guardrails trim valid text and block blank, overlong, or control-character command/timer messages before they can be sent to YouTube.
- Backend rule preset CRUD with single-default handling, curated template catalog, and Android HTTP/demo API source wiring for preset template/list/save/delete.
- Rules tab preset picker can load presets, apply curated templates, save a custom preset copy, switch the backend default preset, and reflect active raid/new-chatter settings in rule summaries.
- Queue rows can quick-block a phrase from a flagged message into the selected/default rule preset and remove the handled queue item.
- Queue rows can delete a selected YouTube Live chat message through the authenticated backend live-chat adapter when a message ID is available.
- Queue rows can warn a chatter through the backend, record a strike on the device-scoped user profile, retain their profile image URL when available, and send a compact YouTube live-chat warning when a live chat is selected.
- Queue chatter details are tappable so existing warned-user profiles can open directly for notes, timeout, hide/ban, and trust actions.
- Users tab can refresh backend warning history, including warned chatter channel IDs, profile images, strike counts, and recent strike reasons.
- Warned-user profile drawer shows profile image, channel ID, first/last seen, message count, recent strikes, and backend-synced moderator notes.
- Warned-user profile drawer can manually hide/ban a chatter from the selected YouTube Live chat through the backend YouTube adapter.
- Warned-user profile drawer can temporarily time out a chatter for 5 minutes from the selected YouTube Live chat through the backend YouTube adapter.
- Warned-user profile drawer shows recent timeout/hide/unban history persisted on the backend user profile and included in account export.
- Warned-user profile drawer can unban the last ChatMod-created hide or timeout when YouTube returned a saved `liveChatBanId`.
- Warned-user profile drawer can quick-whitelist a chatter, creating a backend whitelist entry and adding their channel ID to the active trusted-users rule preset.
- Warned-user profile drawer can save a one-hour temporary whitelist record and add an expiring trusted-channel entry to the active/default rule preset without turning it into permanent trust.
- Destructive live moderation actions now use confirmation dialogs for queue delete, profile timeout, and permanent hide.
- Backend stream-session audit log API with Android HTTP/demo source wiring and foreground-runtime sync for chat messages, moderation actions with reasons, and bot runtime events including heartbeat/reconnect events.
- Moderation audit logs include destructive actions, flag-for-review decisions, rule-match reasons, and rate-limited moderation attempts using backend-compatible action names.
- Android Live Feed and Logs tab classify rule matches separately from manual moderation actions, with a dedicated Rules filter.
- Android Logs shows a local top-triggered-rules summary, and runtime session summaries persist `topTriggeredRulesJson` for completed bot runs.
- Android Logs shows a false-positive review list for non-destructive rule matches that were flagged instead of auto-deleted, with local/backend review status for marking noisy matches as false positives and one-tap preset tuning for safe threshold/domain fixes.
- Android Logs includes a session moderation summary with distinct users timed out/hidden, and manual profile timeout/hide actions are mirrored into active stream-session logs.
- Android Logs includes local analytics cards for most active chatters, most used commands, rule effectiveness, spam attempts over time, and uptime/reconnect history without a paid analytics SDK.
- Backend cross-stream audit analytics summarize synced stream sessions for top chatters, command usage, rule effectiveness by rule and preset/version, spam attempts by day, and uptime/reconnect history, with an Android Logs tab Pro trends card and no paid analytics warehouse.
- Pro/Creator OBS browser overlays render a tokenized read-only public surface from synced stream-session audit state, with chat text hidden by default and token rotation for URL leaks.
- Pro/Creator team moderator access lets creators create, redeem, list, and revoke profile-scoped team seats through the backend and Android Settings without a paid team-management service.
- Android Logs can export the current filtered local log view through the native share sheet as CSV text, while the backend exposes stream-session audit export as JSON or CSV for synced cloud logs.
- Persistent Room-backed pending sync queue for stream session start/end, chat message logs, moderation action logs, and runtime events.
- AndroidX WorkManager periodically drains pending cloud-sync jobs on connected, battery-not-low devices so non-live sync can recover without paid infrastructure.
- Android Room/DataStore source validation is available through `npm run android:data:check` for migration chain, settings keys, local wipe, and pending-sync recovery guardrails.
- Backend active/scheduled YouTube broadcast discovery plus Android API source wiring for stream selection and live-chat readiness state.
- Backend entitlement limits now enforce Starter channel profile, custom command, timed-message, settings-restore, and local-history caps; Pro/Creator return uncapped command/timer limits, Android displays those as Unlimited, and Pro/Creator show larger Room-backed log/viewer-history windows without paid analytics infrastructure.
- Backend YouTube adapter preserves text, member, Super Chat/Super Sticker, deleted-message, user-ban, and system event types; Android runtime records non-text events without letting them trigger bot commands or destructive moderation.
- Verified YouTube authors, channel owner, moderators, members when configured, and whitelisted channel IDs bypass moderation filters and raid/flood abuse tracking.
- Rule profiles can enable a deterministic moderation auto-reply with creator-written text; the Android runtime validates and rate-limits it through the same YouTube send path as commands/timers, with no paid AI dependency.
- Rule profiles can also target only the first minutes of a stream; the Android runtime passes stream start context into the rule engine and the Rules screen exposes a first-10-minutes toggle.
- Pro/Creator rule presets can export/import portable ChatMod JSON bundles through authenticated backend routes and Android share/paste controls without paid object storage or storage permissions.
- Dashboard setup checklist now acts as onboarding: product intro, YouTube permission explanation, Google sign-in, stream discovery, test message, first rule preset, and first command/timer setup route directly into the real app controls.
- Dashboard live stream selector for channel ID lookup, active/scheduled broadcast review, pull-to-refresh stream detection, single-active-chat auto-binding, active stream selection, connection-state chips, and last-selected stream persistence.
- Stream discovery reads the connected YouTube channel from `/youtube/account`, blocks typed channel mismatches, clears stale live-chat state, and shows the connected channel in the mobile selector.
- Stream detection surfaces backend YouTube public error codes as clear mobile states for OAuth expiry, disabled live chat, ended streams, private/unlisted access, bot permission issues, rate limits, and YouTube API failures.
- Account tab can request the backend YouTube OAuth connect URL, explain the exact YouTube permissions, show missing OAuth env vars in free/local mode, and open Google sign-in when production OAuth is configured.
- Dashboard test connection control sends one safe test message to the selected YouTube Live chat through the backend.
- Settings tab source wiring for DataStore-backed emergency mode, link lockdown, reduced motion, high contrast mode, low-data mode, selected-profile visibility, and Discord webhook alerts.
- Opt-in basic usage analytics are wired through the backend using the existing free-tier support-event persistence path.
- Beta feedback submission/listing is wired through the Android Support tab and backend using the existing free-tier support-event persistence path.
- Launch-site beta interest posts to `POST /feedback/beta-interest`, giving the static Cloudflare Pages-ready site a real first-party signup path instead of a placeholder form.
- Free-tier retention pruning is available through `npm run backend:retention:prune`, with dry-run mode by default for support events, API errors, ended-stream detail logs, and old backup versions.
- Backend deployment smoke checks are available through `npm run backend:smoke -- --base-url=<backend-url> --require-database`.
- Production environment preflight is available through `npm run backend:env:check -- --json` before deploying Render/Neon secrets.
- Launch-site and static admin dashboard validation are available through `npm run launch-site:check` before publishing the Cloudflare Pages static site.
- Optional backend `/admin/*` support routes can be enabled with `ADMIN_API_KEY` for user/device lookup, subscription lookup, manual entitlement correction, and ticket metadata without adding a paid support platform.
- Low-data mode setting stored in DataStore and consumed by the foreground bot service to reduce background polling.
- Live Feed, User History, and Logs tab source wiring for Room-backed chat messages, moderation logs, and bot runtime events.
- Stale backend device-session tokens are refreshed once after `401` responses across dashboard calls, command/timer sync, and pending cloud-sync drains.
- Public backend app compatibility checks let Android show version health and block live bot start from unsupported builds.
- Account tab source wiring for account export, YouTube disconnect, cloud backup listing/deletion, settings backup/restore, account deletion against the backend privacy API, and local data wipe for phone-side Room/DataStore data including queued sync jobs. Backend export/deletion also covers linked YouTube channel metadata, support diagnostics, and device-scoped API errors.
- Support tab source wiring for sending/reviewing backend diagnostics, uploaded Android crash markers, recent API errors, and beta feedback without paid crash or feedback tooling.

## YouTube Setup

Production YouTube integration needs:

- Google Cloud project.
- YouTube Data API v3 enabled.
- OAuth consent screen.
- Android OAuth client for the app.
- Web OAuth client for backend token exchange, if backend-assisted setup is enabled.
- Scopes reviewed carefully for moderation actions.

The first implementation uses clear interfaces and mock clients so UI, rules, and backend contracts can be built without leaking secrets or pretending OAuth is already done.
