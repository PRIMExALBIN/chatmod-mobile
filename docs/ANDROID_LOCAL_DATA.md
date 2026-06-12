# Android Local Data

ChatMod Mobile is local-first while the bot is live. The phone owns the active moderation loop, command/timer reads, local logs, foreground-service recovery state, and pending cloud-sync queue. The backend still owns OAuth token custody, billing, account metadata, cloud backups, and cross-stream history.

## Source Gate

Run:

```powershell
npm run android:data:check
```

This source gate verifies:

- Room is configured with KSP schema export, explicit migrations, and no destructive migration fallback.
- Room covers commands, timers, chat message logs, moderation action logs, bot runtime events, and pending cloud sync jobs.
- DataStore covers selected profile, active runtime recovery, last selected stream, active rule preset, emergency mode, link lockdown, reduced motion, high contrast, low-data mode, and opt-in analytics.
- Local privacy wipe clears commands, timers, chat logs, moderation logs, runtime events, pending sync jobs, DataStore settings, and crash markers.
- WorkManager drains pending cloud-sync jobs on connected, battery-not-low devices.
- Android dashboard logs and viewer history use plan-aware local history limits: Starter exposes 120 rows, Pro exposes 1,000 rows, and Creator exposes 2,000 rows from the Room-backed source.
- Backend channel profiles can be listed, created, selected, and persisted through DataStore. The dashboard command/timer store observes the selected profile, command/timer writes sync with that active profile ID, and the foreground runtime reads enabled commands and timers from the selected profile while the bot is live.

The check is intentionally source-only. It does not replace compiling the Android project, installing the app, or migrating a real database on an emulator/device.

## Local Ownership

On-device Room stores only what the phone needs to keep the live bot reliable:

- `commands`
- `timers`
- `chat_message_logs`
- `moderation_logs`
- `bot_runtime_events`
- `pending_sync_jobs`

DataStore stores lightweight control and recovery state:

- Selected profile.
- Emergency mode, link lockdown, reduced motion, high contrast, low-data mode, and usage analytics choices.
- Active foreground-service runtime recovery.
- Last selected stream.
- Active rule preset cache.

Account, Google OAuth, billing, cloud backup, support diagnostics, team access membership records, and durable cross-stream analytics remain backend-owned. Android Settings owns the team invite, redeem, list, and revoke controls while the backend remains the source of truth.

The Room log source observes up to the Creator local-history cap so entitlement upgrades can reveal more on-device history immediately. The dashboard trims the visible log and viewer-history lists using the backend `localHistoryLimit` entitlement.

## Migration Discipline

When changing a Room entity:

1. Increment the database version in `ChatModDatabase`.
2. Add a `MigrationXToY` block.
3. Add the migration to `.addMigrations(...)`.
4. Keep `exportSchema = true` and `room.schemaLocation`.
5. Run `npm run android:data:check`.
6. Run the Android build and migration test on a machine with JDK 17+ and Android SDK.

Do not add `fallbackToDestructiveMigration` for release builds. Creator command/timer settings and local audit history must not disappear during upgrade.

## Manual QA Still Required

Before beta release, verify on an emulator or Android device:

- Upgrade from an older app build with existing commands, timers, logs, settings, and pending sync rows.
- Emergency mode, link lockdown, high contrast, low-data mode, and analytics settings survive app restart.
- Active runtime and last selected stream recover after process death.
- Local wipe clears Room, DataStore, pending sync jobs, and crash markers.
- Pending sync jobs drain after network returns.
- WorkManager does not run while battery-not-low constraints are unmet.
