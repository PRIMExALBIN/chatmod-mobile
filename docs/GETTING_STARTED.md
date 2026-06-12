# Getting Started

This guide is for a local or closed-beta ChatMod Mobile setup. It keeps the beta path free-tier-friendly and calls out the parts that still need real Google, YouTube, Android, or Play Console verification.

## What You Need

- Node.js 22 or newer.
- Android Studio with JDK 17+ and the Android SDK.
- A YouTube channel for the bot identity, preferably separate from the creator's main channel.
- Optional local services: Docker for Postgres and Redis.
- Optional hosted beta services: Render Free backend, Neon Free Postgres, and Cloudflare Pages Free for the static launch site.

## Start The Backend Locally

```powershell
npm install
npm run backend:dev
```

The API defaults to `http://localhost:4100`. The Android emulator should use `http://10.0.2.2:4100`.

The local backend login/session flow is source-gated by the backend test suite: `POST /accounts/device-session` issues a short-lived ChatMod bearer token, authenticated routes reject missing or invalid tokens with request IDs, and `/youtube/connect-url` reports missing Google OAuth configuration instead of pretending sign-in is ready.

For production-style local persistence:

```powershell
docker compose up -d
$env:DATABASE_URL='postgresql://chatmod:chatmod@localhost:5432/chatmod'
npm --workspace backend run prisma:migrate
```

## Configure YouTube OAuth

For local demo work, the backend can run without Google OAuth and will expose missing configuration in the Android Account tab. For real sign-in and real YouTube Live chat actions, set the Google OAuth environment variables documented in `.env.example`, configure the redirect URI, and verify the callback against a test channel.

Useful docs:

- `docs/OAUTH_PERMISSIONS.md`
- `docs/BOT_ACCOUNT_SETUP.md`
- `docs/YOUTUBE_MODERATOR_SETUP.md`

## Run The Android App

Open `android/` in Android Studio. Use the `internalDebug` flavor for local smoke tests once Android tooling is installed:

```powershell
./gradlew :app:assembleInternalDebug
```

The current workspace machine still needs JDK/Gradle/Android SDK setup before local Android compilation can run.

## First Creator Setup

1. Open ChatMod Mobile.
2. Read the dashboard setup panel.
3. Review YouTube permission copy in Account.
4. Connect the dedicated bot channel when OAuth is configured.
5. Add the bot channel as a YouTube moderator.
6. Refresh stream detection.
7. Send a test message.
8. Save a first rule preset.
9. Add one command and one timer.
10. Start the foreground bot runtime when the stream is live.

## Verify Before A Beta Stream

- The foreground notification appears after starting the bot.
- Stream title and live chat status match the intended stream.
- Test message posts from the bot channel.
- Commands and timers are enabled only when intended.
- Emergency mode and link lockdown are reachable from the app and notification.
- Logs appear in the Logs tab and export as CSV.
- Support diagnostics can be sent after a failed action.

## Known External Gates

These stay open until tested with real external accounts and devices:

- Google OAuth sign-in with production credentials.
- Real YouTube API adapter against a private and public test stream.
- Bot moderator permission behavior.
- Android emulator/device verification.
- Google Play billing and Play Console release tracks.
