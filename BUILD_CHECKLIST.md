# ChatMod Mobile Build Checklist

Date: 2026-06-09

Product name: **ChatMod Mobile**

Product subtitle:

> Custom YouTube live chat bot from your phone.

This checklist is the build source of truth. The goal is a real full-stack product, not a one-screen mockup, fake dashboard, or AI-slop UI.

Design bar: keep the product inspired by the latest Gemini app UI and Material 3 Expressive direction, translated into a creator moderation control room with real backend behavior behind every claimed workflow.

## Implementation Status - 2026-06-07

Completed foundation work:

- [x] Product context captured in `PRODUCT.md`
- [x] Gemini-inspired product design direction captured in `DESIGN.md`
- [x] Backend Fastify API scaffolded
- [x] Signed backend device session tokens added
- [x] Protected backend routes added
- [x] Prisma data model drafted
- [x] PostgreSQL and Redis local services defined
- [x] Shared OpenAPI contract created
- [x] Android project scaffolded
- [x] Android Compose dashboard scaffolded
- [x] Android foreground bot service skeleton created
- [x] Android local moderation rule engine created
- [x] Android YouTube Live Chat client interface created
- [x] Dashboard Queue pull-to-refresh wired to stream detection
- [x] Backend rule engine tests passing
- [x] Backend commands CRUD added
- [x] Backend timers CRUD added
- [x] Backend channel profiles API added
- [x] Backend Prisma persistence path added for profiles, commands, timers, backups, and support events
- [x] Local database setup script and docs added
- [x] Backend support-event telemetry API added
- [x] Backend API error log store, Prisma device-scoped route, and privacy export/delete coverage added
- [x] Backend request rate limiting added
- [x] Backend baseline security headers added
- [x] Backend request ID error correlation added for auth, validation, and server errors
- [x] Backend production env guardrails added
- [x] Production secret encryption key ring added for backend token custody
- [x] Backend readiness health endpoint added
- [x] Backend Dockerfile added
- [x] Backend deployment guide added
- [x] Render free-tier backend blueprint added with external Neon Postgres posture
- [x] CI backend Docker image build gate added
- [x] Backend YouTube OAuth connect URL and callback exchange added
- [x] Backend signed YouTube OAuth state added
- [x] Backend encrypted OAuth token storage added
- [x] Backend OAuth refreshed access-token persistence added
- [x] Backend Google YouTube Data API adapter added
- [x] Backend Google YouTube API adapter tests with mocks added
- [x] Backend active/scheduled YouTube broadcast listing added for stream selection
- [x] Backend multiple-active-stream discovery metadata added
- [x] Backend YouTube account status and channel mismatch guard added
- [x] Android YouTube broadcast discovery API source wiring added
- [x] Opt-in severe-match auto-hide wired through backend rules, Android runtime, preset UI, and OpenAPI
- [x] Android connected YouTube channel selector action added to prevent manual Channel ID mismatch
- [x] Android moderator permission check sends then deletes a test message through the backend YouTube route
- [x] Production release checklist added in `docs/PRODUCTION_RELEASE_CHECKLIST.md`
- [x] Google OAuth verification asset packet added in `docs/OAUTH_VERIFICATION_ASSETS.md`
- [x] Live-chat endpoints use stored OAuth tokens when configured
- [x] Backend YouTube live-chat send text safety validation added
- [x] Backend YouTube API errors normalized into public `YOUTUBE_*` codes with retry headers
- [x] Backend Google Play billing validation service added
- [x] Backend entitlement store added
- [x] Backend entitlement grace, canceled, and expired access handling added
- [x] Backend Google Play subscription status mapping tests added
- [x] Backend entitlement limits enforced for Starter channel profiles, commands, timers, and settings restore
- [x] Backend paid entitlement limits allow uncapped Pro/Creator commands and timers
- [x] Android billing summary screen added
- [x] Android entitlement validation contract added
- [x] Android Google Play validation response parser aligned with backend contract
- [x] Android Google Play Billing purchase and restore source flow added
- [x] Backend account data export route added
- [x] Backend account deletion route added
- [x] Backend YouTube disconnect route added
- [x] Backend YouTube OAuth token revocation attempt added
- [x] Backend account-scoped backup list/delete routes added
- [x] Backend command/timer settings backup route added
- [x] Backend settings backup restore route added
- [x] Backend settings backup validation reuses command/timer safety schemas
- [x] Prisma-backed cloud backup configs encrypted with backend secret key ring
- [x] Free/open-source and free-tier stack plan documented
- [x] Backend command parser and evaluator added
- [x] Backend command global and per-user cooldown tests added
- [x] Backend timer due/mark-sent runtime added
- [x] Backend command/timer routes scoped to authenticated accounts
- [x] Backend stable-ID command/timer sync endpoints added
- [x] Backend command response safe URL validation added
- [x] Backend rule preset CRUD and default-preset handling added
- [x] Backend curated rule preset template API added
- [x] Backend stream-session audit log API added for messages, moderation actions, and runtime events
- [x] Backend backup JSON serialization isolation tests added
- [x] Android real HTTP API client added for backend sync
- [x] Android rule preset API source wiring added
- [x] Android stream-session audit log API source wiring added
- [x] Android runtime-to-cloud audit sync wiring added for messages, moderation actions, and runtime events
- [x] Android persistent pending sync queue added for stream-session and runtime/audit cloud writes
- [x] Android device session manager added for backend access tokens
- [x] Android stale backend device-session recovery added for dashboard, command/timer sync, and pending cloud sync
- [x] Backend app compatibility API and Android update-required start guard added
- [x] Android live stream selector UI added for active/scheduled YouTube broadcasts
- [x] Android stream selector now uses backend discovery for live-chat readiness states
- [x] Android stream selector auto-binds a single active live chat and shows no-active/multi-active states
- [x] Android test connection control added for selected YouTube Live chat
- [x] Android user history screen added from local chat/moderation logs
- [x] Android rule preset picker/save/switch UI added against backend preset API
- [x] Android curated rule preset template chips and custom preset save flow added
- [x] Android channel profile list/create/select UI added for backend profile API, with selected-profile scoping for presets, warnings, Discord alerts, analytics, backups, commands, timers, and foreground runtime reads
- [x] Android quick-block phrase action added from queue rows into the synced default rule preset
- [x] Backend warning/strike API added with device-scoped user profiles
- [x] Android queue Warn action added for warning chatters and recording strikes
- [x] Backend user-profile list returns recent strike history
- [x] Android Users tab warning/strike history refresh added
- [x] Backend manual YouTube message delete route added for queue actions
- [x] Android queue manual delete action added for messages with YouTube IDs
- [x] Android destructive moderation confirmations added for delete, timeout, and hide
- [x] Backend user-profile notes update route added
- [x] Android warned-user profile drawer and notes editor added
- [x] Backend manual YouTube hide-user route added for user profiles
- [x] Android warned-user profile drawer hide-user action added
- [x] Backend temporary timeout route added for user profiles
- [x] Android warned-user profile drawer timeout action added
- [x] Backend permanent whitelist API added for user profiles
- [x] Android profile drawer quick-whitelist action updates trusted rule preset IDs
- [x] Rule engines support trusted channel IDs for whitelisted users
- [x] Android pause/resume all timers control added with Room persistence and backend sync path
- [x] Android Account tab privacy controls added
- [x] Android account export, YouTube disconnect, backup delete, and account deletion API wiring added
- [x] Android settings backup and restore source wiring added
- [x] Android local data wipe source wiring added
- [x] Android Support tab diagnostics source wiring added
- [x] Android support-event API client wiring added
- [x] Android Support tab API error source wiring added
- [x] Android live status bot health strip added with queue depth and YouTube API warning signals
- [x] Android free-tier crash reporter added using backend support events
- [x] Opt-in basic analytics endpoint and Android Settings toggle added
- [x] Beta feedback endpoint and Android Support tab form added
- [x] Launch-site beta interest form wired to `POST /feedback/beta-interest` with backend validation, support-event storage, OpenAPI docs, and static-site network states
- [x] Android Settings tab source wiring added for DataStore controls
- [x] Android emergency mode, link lockdown, and reduced motion settings exposed in UI
- [x] Android Logs tab source wiring added for Room chat messages, moderation logs, and runtime events
- [x] Android local chat message log storage added with Room migration and local wipe coverage
- [x] Android recent-log DAO observers added
- [x] Android Logs tab local analytics cards added for active chatters, command usage, rule effectiveness, spam attempts, and uptime/reconnect history
- [x] Backend cross-stream stream-session analytics summary API and Android HTTP/demo source wiring added without paid analytics infrastructure
- [x] Android Logs tab Pro trends card added for backend cross-stream message, audience, command, rule, spam, and uptime summaries
- [x] Rule preset/version metadata added to moderation audit logs, backend analytics, and Android Pro trends
- [x] Backend Discord webhook settings added with encrypted URL storage, Pro/Creator entitlement gating, safe account export, test alert, and runtime alert routes
- [x] Shared OpenAPI contract added for Discord webhook config, test alerts, runtime alerts, and `discordAlerts` entitlement
- [x] Android Settings tab added Discord alert controls for webhook save/delete/test, enable state, moderation alerts, and runtime-status alerts
- [x] Android HTTP/demo API clients and repository pass-throughs added for Discord alert settings and delivery
- [x] Android foreground runtime sends privacy-safe Discord moderation alerts after bot actions without blocking YouTube polling
- [x] Free-tier production readiness verifier added with CI coverage for Render/Neon posture, Dockerfile, env example, OpenAPI, Prisma migrations, and release docs
- [x] Free-tier data retention pruning added with dry-run-first CLI, Prisma indexes, checked-in migration, docs, and readiness checks
- [x] Backend deployment smoke test added for free-tier Render/Neon verification of health, readiness, compatibility, auth request IDs, and beta-interest validation
- [x] Free GitHub Actions backend job now smoke-tests both compiled backend output and the Docker image before marking CI green
- [x] Production environment preflight added for Render/Neon/OAuth/Play secret validation before backend startup
- [x] Launch-site static validation added for Cloudflare Pages-ready links, policy pages, beta form wiring, accessibility status, and placeholder-free copy
- [x] Play Data Safety source worksheet and Store readiness validation added for listing limits, policy consistency, and Android manifest permission coverage
- [x] Android free-tier CI source added with JDK 17, Android SDK API 35, Gradle 8.10.2, unit tests, and closed-beta debug assembly in demo API mode
- [x] Android Gradle wrapper checked in, pinned to Gradle 8.10.2, and guarded by `npm run android:wrapper:check`
- [x] Android Room/DataStore source validation added for migration chain, DataStore keys, local wipe coverage, and pending sync recovery
- [x] Android plan-aware local history added with backend `localHistoryLimit` entitlement, Room-backed log/viewer history caps, and billing/dashboard UI
- [x] Rule preset export/import added with authenticated backend JSON bundle routes and Android share/paste controls
- [x] Koin dependency injection source wiring added for Android app graph, Activity consumers, and release guardrails
- [x] Android UX/accessibility source validation added for screen-reader semantics, 48dp touch targets, high contrast, text scaling posture, and non-color-only status cues
- [x] Android Kotlin 2 Compose compiler plugin wired for real Compose builds
- [x] Android release signing source wiring added with external env/Gradle property secrets and keystore files ignored
- [x] Android HTTP retry/backoff source wiring added
- [x] Android device QA evidence capture added for ADB install/launch screenshots, logcat, timestamped evidence folders, and release evidence docs without paid device testing services
- [x] External release evidence manifest and validator added for backend deployment, Google/YouTube, Android device, Play Console, and creator beta proof with secret-safe local artifact handling
- [x] Signed closed-beta release APK produced with ignored external local keystore secrets, release minification, and apksigner verification
- [x] Android release-build warning cleanup added by moving BuildConfig enablement to the module DSL and making abuse-tracker queue trimming null-safe
- [x] Android dashboard release icon warnings cleaned up by moving deprecated directional Rule/Send icons to AutoMirrored variants with production-readiness guardrails
- [x] Support troubleshooting and FAQ docs added
- [x] Android command runtime added
- [x] Android coordinator can send command replies and due timers in source
- [x] Android foreground runtime loads enabled Room commands/timers, tracks timer activity, persists sent timestamps, and pauses timers during emergency mode
- [x] Backend and Android manual custom-command send action added for selected live chat
- [x] Android in-app command editor UI added
- [x] Android in-app timer editor UI added
- [x] Android command/timer editor persistence wiring to Room added
- [x] Android command/timer sync abstraction added
- [x] Android moderation action rate limiter added
- [x] Android shared bot message rate limiter added for commands and timers
- [x] Android multi-key rate limiter unit coverage added in source
- [x] Backend and Android advanced rule filters added for regex, domain lists, emoji spam, mention spam, and member bypass
- [x] Backend entitlement gate added for advanced moderation filters, rule presets, and moderation evaluation
- [x] OpenAPI moderation profile contract updated for advanced rule filters
- [x] Android foreground-service restart recovery source wiring added with persisted runtime context
- [x] Android active runtime state persisted in DataStore for crash-safe service restart
- [x] Android stream-ended signal handling added in bot coordinator and foreground service
- [x] Android foreground notification live-control actions added for stop, emergency mode, and link lockdown
- [x] Android Quick Settings tile source wiring added for saved-stream start and active-runtime stop
- [x] Android dashboard accessibility source pass added for live status semantics, non-clickable status chips, and 48dp touch targets on dense stream controls
- [x] Android live workspace tabs added for Queue, Feed, and Controls so high-pressure live actions stay in one context
- [x] Android device QA runner added for free ADB install, launch, and evidence-based manual proof gates
- [x] Android runtime moderation profile reacts to emergency mode and link lockdown settings
- [x] Android foreground runtime uses backend YouTube live-chat polling, send, delete, and hide/timeout endpoints outside demo mode
- [x] Backend live-chat runtime polling, send, and direct hide/timeout endpoints added
- [x] Backend, OpenAPI, Android HTTP/demo clients, and profile drawer unban flow added for ChatMod-created hide/timeout actions with saved YouTube ban IDs
- [x] Deterministic moderation auto-reply action added without paid AI services
- [x] Creator-gated AI-ready moderation suggestions added with free local heuristic provider, manual approval, confidence, repeated-question detection, and explanations
- [x] Creator AI moderation suggestion daily usage limits added with Prisma-backed production counters, OpenAPI usage metadata, Android billing display, and 429 limit handling
- [x] Creator-gated after-stream AI chat summary added with free local heuristic provider, synced-log backend endpoint, Android Logs card, and OpenAPI/docs coverage
- [x] Creator-gated AI FAQ replies added with Prisma-backed creator knowledge-base entries, local heuristic matching, Android Commands panel controls, queue suggestions, OpenAPI/docs coverage, and manual approval
- [x] Pro/Creator OBS browser overlay added with hashed public tokens, token rotation, sanitized stream-session state, backend-rendered transparent HTML, Android Settings controls, OpenAPI/docs coverage, and backend tests
- [x] Pro/Creator team moderator access added with profile-scoped invites, hashed invite codes, redeem/revoke flows, Android Settings controls, OpenAPI/docs coverage, and backend tests
- [x] Static web dashboard added with support lookup, beta-interest review, manual entitlement adjustment, ticket metadata, runtime admin key entry, free-tier static hosting docs, and source validation
- [x] Static web/admin keyboard support added with skip link, native labeled controls, submit buttons, visible focus, live status copy, docs, and launch-site source validation
- [x] Backend login/session integration proof added for device-session token issuance, authenticated entitlement access, OAuth-not-configured copy, and missing/invalid token request IDs
- [x] Lifetime purchase decision closed for MVP: not offered, subscription-only Play Billing source and store/docs guardrails added
- [x] Tutorial video artifact added with HyperFrames source, storyboard/script, free local FFmpeg tooling, rendered MP4, thumbnail, docs, CI gate, and source validation
- [x] First-stream-minutes moderation targeting added for rule presets
- [x] Verified YouTube author bypass added across live-chat adapter and Android runtime
- [x] Raid-mode runtime abuse tracking can temporarily time out repeat/flood spammers with audit logs
- [x] Android Room local database scaffold added
- [x] Android DataStore settings scaffold added
- [x] Android timer scheduler added
- [x] Backend TypeScript build passing
- [x] NPM audit clean
- [x] Built API smoke-tested on local port `4110`
- [x] Command/timer runtime smoke-tested in built API test mode

Still not complete:

- [ ] Real Google OAuth flow verified with Google credentials
- [ ] Real YouTube API adapter verified against a test stream
- [x] Android local storage with Room/DataStore
- [x] Android Room/DataStore source validation with `npm run android:data:check`
- [x] Full in-app command editor UI
- [x] Full in-app timer editor UI
- [x] Command/timer editor persistence wiring to Room and sync abstraction
- [x] Command/timer editor sync against real backend session/API client
- [ ] Command/timer editor sync verified on Android emulator or device
- [ ] Android Account tab privacy controls verified on emulator or device
- [ ] Android settings backup and restore verified on emulator or device
- [ ] Android local data wipe verified on emulator or device
- [ ] Android Support tab diagnostics verified on emulator or device
- [ ] Android Settings tab DataStore controls verified on emulator or device
- [ ] Android Logs tab Room data verified on emulator or device
- [ ] Android HTTP retry/backoff verified on emulator or device
- [ ] Android Koin graph verified through Activity, foreground service, Quick Settings tile, and WorkManager paths on emulator or device
- [ ] Android TalkBack, text scaling, high contrast, and one-handed live-control QA verified on emulator or device
- [ ] Timer scheduler verified sending messages against YouTube test stream
- [x] Android Room/DataStore build verification on a machine with JDK 17+ and Android SDK
- [x] Android build verification on a machine with JDK 17+, Android SDK, and the checked-in Gradle wrapper
- [x] Android build verification instructions documented
- [x] Android device QA plan and ADB runner documented
- [x] Android installable internal debug APK produced with the checked-in Gradle wrapper
- [ ] Google Play billing validation verified with Play Console credentials
- [ ] Google OAuth token revocation verified with production credentials
- [ ] Production deployment
- [ ] Production deployment on free-tier-friendly hosting
- [ ] Production environment preflight verified against hosted Render secret set
- [ ] Backend deployment smoke test verified against hosted free-tier backend
- [x] Launch-site static validation verified before Cloudflare Pages deploy
- [ ] Play Data Safety submitted in Play Console from the final build behavior
- [ ] Retention dry run verified against hosted beta Postgres
- [x] Checked-in Prisma migrations for beta/production
- [ ] Creator beta testing

## 1. Product Goal

Build an Android-first app that lets a creator run a custom YouTube Live chat bot from their phone. The bot should use the creator's own dedicated YouTube bot channel, handle live moderation, send commands and timers, and give the creator a clean mobile dashboard for control during a livestream.

## 2. Design Standard

The UI/UX target is inspired by the latest Gemini app UI direction:

- Use Google's May 19, 2026 Gemini app update and its **Neural Expressive** direction as inspiration: intuitive, vibrant, dynamic, and workflow-first.
- Use Google's **Material 3 Expressive** principles: fluid motion, dynamic color, emphasized typography, glanceable status, and useful haptics.
- Keep it production-grade: clear hierarchy, real states, real controls, no fake analytics, no placeholder cards pretending to be a product.
- Make it feel like a creator control surface, not a generic SaaS dashboard.
- The first screen should be the actual bot control experience after onboarding, not a marketing landing page.
- Every feature must have loading, empty, success, warning, error, offline, and reconnecting states.
- The app must be usable one-handed during a live stream.
- The UI must be fast, calm, and readable under pressure.
- Use expressive motion only when it helps users understand connection state, moderation action, or navigation.
- Avoid generic purple-blue AI gradients as the main visual identity.
- Use native Android interaction patterns, haptics, bottom sheets, tabs, segmented controls, toggles, sliders, and icon buttons where appropriate.

Design reference notes:

- Gemini app inspiration: Neural Expressive, intuitive new UI, dynamic interface, proactive/agentic workflows.
- Material 3 Expressive inspiration: fluid motion, dynamic color, responsive components, glanceable information.
- ChatMod Mobile should borrow the polish and movement, not copy Google's brand.

## 3. Full-Stack Requirement

ChatMod Mobile must be a full-stack app with:

- Android mobile app
- Local foreground bot service
- Backend API
- Database
- OAuth/token management
- Subscription/licensing system
- Admin/support tooling
- Logs and telemetry
- Release pipeline
- Privacy and compliance flows
- Optional web dashboard later

Local-first bot execution is the differentiator, but backend services are still needed for accounts, licensing, secure configuration backup, support, verification, crash reporting, and Pro features.

## 4. Recommended Tech Stack

### Android App

- [x] Kotlin
- [x] Jetpack Compose
- [x] Material 3
- [x] WorkManager for non-live background tasks
- [x] Foreground Service for active bot hosting
- [x] Room/SQLite for local storage
- [x] DataStore for settings
- [x] Kotlin coroutines/Flow
- [x] Hilt or Koin for dependency injection
- [x] Google Identity Services / OAuth support
- [x] YouTube Data API client layer

### Backend

- [x] TypeScript
- [x] Node.js
- [x] Fastify, NestJS, or Hono
- [x] PostgreSQL
- [x] Redis for short-lived jobs/cache/rate limits
- [x] Prisma or Drizzle ORM
- [x] Google Play Billing validation
- [x] Object storage intentionally deferred until exports/backups outgrow encrypted Postgres rows
- [x] OpenAPI spec

### Infrastructure

- [x] Free-tier-friendly backend host candidates selected
- [x] Free-tier-friendly managed Postgres candidate selected
- [x] Free-tier-friendly Redis/cache candidate selected
- [x] Error tracking
- [x] Basic analytics
- [x] CI checks
- [x] Release channels: internal, closed beta, production
- [x] Data retention plan for free-tier Postgres

## 5. Milestones

### M0 - Product Foundation

- [x] Confirm final name: ChatMod Mobile
- [x] Confirm package/app ID
- [x] Confirm Android-first launch
- [x] Confirm supported YouTube actions
- [x] Confirm minimum Android version
- [x] Confirm first pricing model
- [x] Create privacy policy draft
- [x] Create OAuth permission explanation
- [x] Create initial user flow diagrams
- [x] Create architecture diagram
- [x] Create data model draft
- [x] Create free-tier stack plan

### M1 - Technical Prototype

- [x] Android app shell
- [ ] Google OAuth login proof
- [x] Fetch active YouTube live broadcast
- [x] Resolve active live chat ID
- [x] Read live chat messages
- [x] Send one bot message
- [x] Delete one test message
- [x] Android foreground service proof
- [x] Local rule engine proof
- [x] Basic local log storage

### M2 - MVP Beta

- [x] Full onboarding
- [x] Bot account setup checklist
- [x] Live chat connection dashboard
- [x] Custom commands
- [x] Timed messages
- [x] Banned word filter
- [x] Link filter
- [x] Repeat spam filter
- [x] Caps spam filter
- [x] Emoji spam filter
- [x] Delete message action
- [x] Timeout/hide user action where available
- [x] Ban/hide user action where available
- [x] Warning/strike system
- [x] Foreground service reliability
- [x] Reconnect handling
- [x] Moderation log
- [x] Export/import settings
- [x] Closed beta build

### M3 - Public Launch

- [ ] Google OAuth verification if required
- [ ] Google Play listing
- [x] Subscription or one-time purchase flow
- [ ] Production backend deployed to hosting
- [x] Crash reporting
- [x] Support channel
- [x] Help docs
- [x] Tutorial video
- [x] Beta feedback loop
- [x] Launch landing page

### M4 - Pro Features

- [x] Multi-profile support
- [x] Cloud backup
- [x] Discord webhooks
- [x] Advanced analytics
- [x] AI moderation suggestions
- [x] AI FAQ replies
- [x] OBS/browser overlay
- [x] Team moderator access
- [x] Web dashboard

## 6. Core User Flows

### New Creator Setup

- [x] Install app
- [x] See concise product intro
- [x] Sign in with Google
- [x] Choose streamer account or bot account flow
- [x] Explain that bot name equals YouTube channel name
- [x] Guide user to create/select dedicated YouTube bot channel
- [x] Guide user to add bot account as moderator in YouTube Studio
- [x] Run permission check
- [x] Run test chat message
- [x] Save first rule preset
- [x] Enter dashboard

### Going Live

- [x] Detect active livestream
- [x] Show stream title and status
- [x] Show live chat connection status
- [x] Start foreground service
- [x] Start chat ingestion
- [x] Apply rule engine
- [x] Show live activity feed
- [x] Show bot health
- [x] Allow one-tap pause/resume
- [x] Allow emergency mode

### During Stream

- [x] See incoming messages
- [x] See deleted/flagged messages
- [x] Tap user for quick actions
- [x] Delete message
- [x] Warn user
- [x] Timeout user
- [x] Hide/ban user
- [x] Whitelist user
- [x] Add phrase to blocked words
- [x] Trigger custom command manually
- [x] Pause timers
- [x] Switch rule preset
- [x] View bot health notification

### After Stream

- [x] Stop service
- [x] Save session log
- [x] Show session summary
- [x] Review false positives
- [x] Tune rules
- [x] Export log
- [x] Save preset for next stream

## 7. Android App Screens

### Public/Pre-Auth

- [x] Splash screen
- [x] Intro screen
- [x] Permission explanation screen
- [x] Google sign-in screen

### Onboarding

- [x] Bot identity explanation
- [x] YouTube account/channel selector
- [x] Moderator setup checklist
- [x] Test connection screen
- [x] Rule preset picker
- [x] First command/timer setup

### Main App

- [x] Home dashboard
- [x] Live stream selector
- [x] Bot status panel
- [x] Live chat feed
- [x] Moderation queue
- [x] Rules screen
- [x] Commands screen
- [x] Timers screen
- [x] User history screen
- [x] Logs screen
- [x] Settings screen
- [x] Billing/Pro screen
- [x] Account/privacy screen
- [x] Help/support screen

### High-Pressure Controls

- [x] Emergency mode bottom sheet
- [x] One-tap pause bot
- [x] One-tap stop service
- [x] One-tap link lockdown
- [x] One-tap subscriber/member-only recommendation message
- [x] Quick block phrase from selected message
- [x] Quick whitelist selected user

## 8. UI/UX Checklist

### Visual System

- [x] Material 3 base
- [x] Gemini-inspired expressive motion direction
- [x] Dynamic color support
- [x] Dark mode
- [x] High contrast mode
- [x] Status colors for connected, warning, offline, action taken
- [x] Clear typography scale
- [x] No cramped controls
- [x] No decorative cards inside cards
- [x] No fake glassmorphism or visual noise
- [x] App icon direction
- [x] Launch screen polish

### Interaction

- [x] One-handed navigation
- [x] Bottom navigation or navigation rail where appropriate
- [x] Swipe/tab between live feed, queue, and controls
- [x] Haptic feedback for start, stop, delete, timeout, ban
- [x] Confirmation for destructive actions
- [x] Undo where YouTube action allows it
- [x] Persistent foreground notification action buttons
- [x] Pull-to-refresh for stream detection
- [x] Clear offline/reconnecting state

### Real States

- [x] No stream found
- [x] Stream private/unlisted
- [x] Live chat disabled
- [x] Bot not moderator
- [x] OAuth expired
- [x] Quota/rate limit warning
- [x] Network offline
- [x] Battery optimization warning
- [x] Service killed/restart required
- [x] YouTube API error
- [x] Stream ended
- [x] Empty command list
- [x] Empty rule list
- [x] Empty moderation log

### Accessibility

- [x] Screen reader labels
- [x] Minimum tap targets
- [x] Color contrast checks
- [x] Text scaling support
- [x] Android UX/accessibility source validation
- [x] Motion reduction option
- [x] Clear non-color status indicators
- [x] Keyboard support for web/admin later

## 9. YouTube Integration

### OAuth

- [ ] Google OAuth app setup
- [ ] Development OAuth credentials
- [ ] Production OAuth credentials
- [x] Minimum scope selection
- [x] Token refresh
- [x] Token revocation
- [x] Reconnect account flow
- [x] Account/channel mismatch handling
- [x] Clear permission copy

### Live Broadcast Discovery

- [x] List active broadcasts
- [x] List scheduled broadcasts
- [x] Detect current live stream
- [x] Handle no active broadcast
- [x] Handle multiple active streams
- [x] Store last selected stream
- [x] Fetch live chat ID

### Live Chat Reading

- [x] Poll live chat messages at YouTube-recommended interval
- [x] Respect nextPageToken/pollingIntervalMillis
- [x] Backoff on rate limits
- [x] Deduplicate messages
- [x] Handle deleted messages/events
- [x] Handle member/super chat message types
- [x] Handle top chat vs live chat explanation

### Live Chat Writing

- [x] Send text messages
- [x] Rate-limit bot messages
- [x] Reject invalid message text
- [x] Cooldown repeated replies
- [x] Handle live chat ended
- [x] Handle bot not allowed to chat

### Moderation Actions

- [x] Delete chat message
- [x] Hide/ban user where supported
- [x] Timeout/temporary ban where supported
- [x] Unban flow if supported/needed
- [x] Permission error handling
- [x] Log every executed moderation action with reason

## 10. Local Bot Runtime

- [x] Android foreground service
- [x] Persistent notification
- [x] Stop control from running notification
- [x] Optional start control from quick tile/widget if beta creators need it
- [x] Service health heartbeat
- [x] Reconnect watchdog
- [x] Network change listener
- [x] Battery optimization detection
- [x] Wake lock strategy if needed
- [x] Low-data mode
- [x] Safe shutdown on service stop
- [x] Safe shutdown when stream ends
- [x] Crash recovery
- [x] Local queue for pending runtime/audit sync jobs
- [x] Rate limiter shared across commands, timers, and moderation

## 11. Rule Engine

### Rule Types

- [x] Banned words
- [x] Regex patterns
- [x] Link blocking
- [x] Domain allowlist
- [x] Domain blocklist
- [x] Caps spam
- [x] Emoji spam
- [x] Repeated message spam
- [x] Message flood
- [x] Mention spam
- [x] Unicode/symbol spam
- [x] Suspicious new user pattern
- [x] Raid mode stricter thresholds

### Rule Actions

- [x] Ignore
- [x] Flag for review
- [x] Delete message
- [x] Warn user
- [x] Strike user
- [x] Timeout/hide user
- [x] Ban/hide user
- [x] Send auto-reply

### Rule Targeting

- [x] Ignore channel owner
- [x] Ignore moderators
- [x] Ignore members
- [x] Ignore verified/whitelisted users
- [x] Apply only to new chatters
- [x] Apply only during emergency mode
- [x] Apply only during first X minutes of stream

### Presets

- [x] Family friendly
- [x] Gaming default
- [x] Education/Q&A
- [x] Music/live performance
- [x] High-security raid mode
- [x] Custom preset

## 12. Commands

- [x] Command creation
- [x] Command editing
- [x] Command deletion
- [x] Command aliases
- [x] Command cooldowns
- [x] Global cooldowns
- [x] Per-user cooldowns
- [x] Mod-only commands
- [x] Owner-only commands
- [x] Member-only commands
- [x] Enable/disable command
- [x] Variables: username, stream title, time, random choice
- [x] Safe URL validation
- [x] Command usage stats
- [x] Import/export commands

Example MVP commands:

- [x] `!discord`
- [x] `!rules`
- [x] `!socials`
- [x] `!schedule`
- [x] `!commands`
- [x] `!uptime` if available

## 13. Timers

- [x] Timer creation
- [x] Timer editing
- [x] Timer deletion
- [x] Interval setting
- [x] Minimum chat activity threshold
- [x] Randomized timer rotation
- [x] Pause/resume all timers
- [x] Pause timer in emergency mode
- [x] Timer quiet hours per stream
- [x] Timer send history

## 14. User Management

- [x] User profile drawer
- [x] Channel ID
- [x] Display name
- [x] Profile image
- [x] First seen
- [x] Last seen
- [x] Message count
- [x] Strike count
- [x] Warning history
- [x] Timeout/ban history
- [x] Notes
- [x] Whitelist
- [x] Temporary whitelist action and backend expiry record
- [x] Temporary whitelist rule-engine support
- [x] Foreground runtime loads cached active preset temporary whitelist entries
- [x] Manual action buttons

## 15. Logs And Analytics

### Local Logs

- [x] Message log
- [x] Moderation action log
- [x] Bot status log
- [x] API error log
- [x] Rule match log
- [x] Command usage log
- [x] Timer send log

### Session Summary

- [x] Stream duration
- [x] Messages processed
- [x] Commands used
- [x] Timers sent
- [x] Messages deleted
- [x] Users warned
- [x] Users timed out/hidden
- [x] Users banned/hidden
- [x] Top triggered rules
- [x] False positive review list

### Local Analytics Cards

- [x] Most active chatters
- [x] Most used commands
- [x] Rule effectiveness
- [x] Spam attempts over time
- [x] Uptime/reconnect history

### Pro Analytics

- [x] Long-term trend charts
- [x] Cross-stream audience trends
- [x] Cross-stream command trends
- [x] Rule effectiveness by preset/version
- [x] Spam attempts by stream/day
- [x] Uptime/reconnect history across streams

## 16. Backend Features

### Accounts

- [x] User account record
- [x] Google account link metadata
- [x] Channel profile metadata
- [x] Device registration
- [x] Session tracking
- [x] Account deletion flow

### Licensing/Billing

- [x] Free tier entitlement
- [x] Pro entitlement
- [x] Creator tier entitlement
- [ ] Google Play purchase validation verified with Play Console credentials
- [x] Subscription status sync
- [x] Grace period handling
- [x] Cancelled subscription handling
- [x] Lifetime purchase support if offered

### Cloud Backup

- [x] Opt-in only
- [x] Backup commands
- [x] Backup timers
- [x] Backup rule presets
- [x] Restore backup
- [x] Delete backup
- [x] Encrypt sensitive data where appropriate

### Support/Admin

- [x] Admin user lookup
- [x] Device/session lookup
- [x] Crash/error correlation
- [x] Subscription lookup
- [x] Manual entitlement adjustment
- [x] Support ticket metadata

## 17. Data Model Checklist

Local database entities:

- [x] Command
- [x] Timer
- [x] ChatMessageLog
- [x] ModerationLog
- [x] BotRuntimeEvent
- [x] PendingSyncJob
- [x] DataStore settings
- [x] Active runtime recovery state
- [x] Last selected stream state
- [x] Active rule preset cache

Backend-owned or synced through API instead of separate Room entities:

- [x] Account
- [x] YouTubeChannel
- [x] StreamSession
- [x] ModerationAction
- [x] Rule
- [x] RulePreset
- [x] UserProfile
- [x] Strike
- [x] WhitelistEntry
- [x] ApiError

Backend database entities:

- [x] User
- [x] Device
- [x] LinkedAccount
- [x] ChannelProfile
- [x] UserProfile
- [x] Strike
- [x] Subscription
- [x] Entitlement
- [x] Backup
- [x] RulePreset
- [x] WhitelistEntry
- [x] SupportEvent
- [x] ApiError
- [x] AuditLog

## 18. Security And Privacy

- [x] Use minimum YouTube scopes
- [x] Do not store Google OAuth tokens on device; backend owns encrypted token custody
- [x] Never store Google passwords
- [x] Explain all permissions in plain language
- [x] Account disconnect flow
- [x] Account deletion flow
- [x] Local data wipe
- [x] Cloud backup deletion
- [x] Privacy policy
- [x] Terms of service
- [x] Data export, including support diagnostics and device-scoped API errors
- [x] Rate limiting to prevent spammy bot behavior
- [x] Guardrails against abusive automation
- [x] Audit every moderation action
- [x] Protect backend API routes
- [x] Require production database, CORS origin, and strong JWT secret
- [x] Secrets management

## 19. Reliability

- [x] API backoff strategy
- [x] Backend readiness probe
- [x] Retry policy
- [x] Network loss recovery
- [x] OAuth expiry recovery
- [x] YouTube quota warning
- [x] App update compatibility check
- [x] Foreground service restart handling
- [x] Battery optimization instructions
- [x] Device-specific background restriction notes
- [x] Crash-safe local state
- [x] Stream-ended detection

## 20. Testing Checklist

### Unit Tests

- [x] Rule engine tests
- [x] Command parser tests
- [x] Timer scheduler tests
- [x] Rate limiter tests
- [x] OAuth state tests
- [x] YouTube API adapter tests with mocks
- [x] Data serialization tests

### Integration Tests

- [x] Login flow
- [x] Live chat polling mock
- [x] Message send mock
- [x] Moderation action mock
- [x] Local database migration source gate
- [x] Backend entitlement sync
- [x] Backend account export/disconnect/delete
- [x] Backend backup create/list/delete

### Android QA

- [x] Small phone viewport
- [x] Large phone viewport
- [ ] Tablet/foldable behavior
- [ ] Dark mode
- [ ] Poor network
- [ ] Airplane mode
- [ ] Background/foreground transitions
- [ ] Screen locked while service runs
- [ ] Battery saver mode
- [ ] Orientation changes if supported

### Real Stream QA

- [ ] Private test stream
- [ ] Public test stream
- [ ] Bot has moderator permissions
- [ ] Bot does not have moderator permissions
- [ ] Live chat disabled
- [ ] Stream ended
- [ ] Rate limit simulation
- [ ] Spam burst simulation
- [ ] False positive review

## 21. App Store And Launch

- [x] App icon
- [ ] Screenshots
- [x] Short description
- [x] Long description
- [x] Play Data Safety source worksheet
- [ ] Play Data Safety submitted in Play Console
- [ ] Support email
- [x] Website
- [ ] Privacy policy URL
- [ ] Terms URL
- [ ] Closed testing track
- [ ] Open beta track
- [x] Production release checklist
- [x] OAuth verification assets
- [ ] Demo video for OAuth review

## 22. Documentation

- [x] Getting started guide
- [x] Bot account setup guide
- [x] How to add bot as YouTube moderator
- [x] OAuth permission explanation
- [x] Battery optimization guide
- [x] Commands guide
- [x] Timers guide
- [x] Billing guide
- [x] Rule presets guide
- [x] Emergency mode guide
- [x] Troubleshooting guide
- [x] FAQ

## 23. Pricing Checklist

### Free

- [x] 1 channel profile
- [x] Limited commands
- [x] Limited timers
- [x] Basic filters
- [x] Current-stream logs

### Pro

- [x] Unlimited commands
- [x] Unlimited timers
- [x] Advanced filters
- [x] Strike history
- [x] Emergency mode
- [x] Export/import presets
- [x] Longer local history

### Creator

- [x] Multiple profiles
  - [x] Backend profile list/create entitlement enforcement
  - [x] Android profile selector and profile-scoped moderation/backend flows
  - [x] Command/timer local store hot-switching per selected profile
  - [x] Foreground bot runtime reads selected-profile commands/timers
- [x] Cloud backup
- [x] Discord alerts
- [x] Advanced analytics
- [x] AI suggestions
- [x] Team access later

## 24. AI Features - Later, Not MVP

AI should support moderation, not become the product's fake personality.

- [x] AI suggested action, not automatic by default
- [x] AI toxicity/spam classification
- [x] AI repeated-question detection
- [x] AI FAQ answers from creator-provided knowledge base
- [x] AI chat summary after stream
- [x] Manual approval mode
- [x] Confidence thresholds
- [x] Explain why AI flagged a message
- [x] Easy false positive correction
- [x] Usage limits tied to Pro/Creator plans

## 25. First Build Task Board

Start here:

- [x] Create Android project
- [x] Create backend project
- [x] Create shared API contract
- [x] Create local data model
- [x] Create first design system tokens
- [x] Build auth/onboarding shell
- [x] Build foreground service skeleton
- [x] Build YouTube API client skeleton
- [x] Build rule engine package
- [x] Build first dashboard screen
- [x] Build live status component
- [x] Build commands CRUD
- [x] Build timers CRUD
- [x] Build real Android backend API client
- [x] Build local logs
- [x] Add tests for rule engine
- [x] Add README with setup instructions

## 26. Definition Of Done For MVP

The MVP is done only when:

- [ ] A creator can sign in with Google.
- [ ] The app can connect to an active YouTube Live chat.
- [ ] The bot can run from an Android phone using a foreground service.
- [ ] The bot can send command and timer messages.
- [x] The bot can detect and act on basic spam/banned-word rules.
- [x] Moderation actions are logged.
- [x] The UI has real loading, empty, error, offline, reconnecting, and success states.
- [ ] The app survives normal backgrounding and screen lock during a live stream.
- [x] The app explains YouTube permissions clearly.
- [ ] The app has enough polish to show beta creators without apologizing for the UI.

## 27. Source References For Design Direction

- Gemini app 2026 update: https://blog.google/innovation-and-ai/products/gemini-app/next-evolution-gemini-app/
- Material 3 Expressive Android refresh: https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/
- Material 3 Expressive research/design notes: https://design.google/library/expressive-material-design-google-research
