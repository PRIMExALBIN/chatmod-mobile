# Support And Troubleshooting

ChatMod Mobile should be supportable during beta without paid crash tooling. The first support path uses backend support diagnostic events and clear self-service checks.

## In-App Support Diagnostics

The Android Support tab can:

- Send a creator-triggered diagnostic snapshot to `POST /logs/support-events`.
- Upload one sanitized Android crash marker on the next launch after an uncaught crash.
- List recent diagnostics from `GET /logs/support-events`.
- List recent backend API errors from `GET /logs/api-errors`.
- Submit beta feedback to `POST /feedback/beta`.
- List recent beta feedback from `GET /feedback/beta`.
- Include app state such as selected tab, bot running status, sync status, command count, timer count, queue count, billing plan, and whether a live chat ID is present.
- Avoid sending chat message bodies in diagnostics.
- Use public `YOUTUBE_*` API error codes and `requestId` values to separate quota/rate-limit, ended-chat, unavailable-chat, and permission issues.

## Creator Troubleshooting Checklist

### No Stream Found

- Confirm the YouTube stream is live or scheduled on the connected account.
- Confirm the app is connected to the intended bot/creator account.
- Refresh stream detection after going live.

### Bot Cannot Chat

- Confirm the bot YouTube channel is allowed to chat.
- Confirm the bot is not blocked or restricted by the channel.
- Use the dashboard Test connection control to send one safe test message before relying on timers or commands.

### Moderation Actions Fail

- Confirm the bot account has moderator permission where YouTube requires it.
- Reconnect YouTube if OAuth has expired.
- Send a diagnostic from the Support tab after a failed action.

### Commands Or Timers Do Not Sync

- Confirm the backend URL is reachable from the emulator or phone.
- Use demo API mode only when intentionally testing offline.
- Check the Support tab for recent diagnostics.
- Use Account > Cloud backups > Backup before destructive command/timer changes, then restore the latest settings backup if needed.

### Local App State Looks Wrong

- Export cloud account data first if needed.
- Use Account > Wipe local data to clear commands, timers, chat message logs, moderation logs, runtime events, queued sync jobs, and local settings from this phone.
- Reconnect and resync after wiping local data.

## Beta Support Workflow

1. Ask the creator to reproduce the issue.
2. Ask them to send a diagnostic from the Support tab.
3. Ask for any visible backend `requestId` if the issue came from an API error screen or log.
4. Inspect support events by device ID with the static web dashboard at `launch-site/admin.html` or the admin support API when `ADMIN_API_KEY` is configured, or directly in the backend database for local-only development.
5. Correlate the `requestId` with backend logs or the Support tab API error list; API auth, validation, and server errors return the same ID in the `x-request-id` header and JSON body.
6. If OAuth or YouTube API behavior is involved, check for a `YOUTUBE_*` code first, then verify against a private test stream.
7. Ask the creator to submit a beta feedback note from the Support tab if the issue changes product behavior, copy, pricing, onboarding, or missing features.
8. Record the issue and resolution in the beta feedback log.
9. Review launch-site beta-interest leads with the admin support API using device id `launch-site-beta-interest`.

## Admin Support API

The backend can expose an optional admin support API when `ADMIN_API_KEY` is set as a deployment secret. If the key is absent, `/admin/*` routes are not registered.

- `GET /admin/support/users?deviceId=...` returns a device-scoped support snapshot with entitlement, subscription, linked channel metadata flags, recent support events, and recent API errors.
- `GET /admin/support/devices/{deviceId}` returns the same support snapshot for device/session lookup.
- `GET /admin/support/devices/launch-site-beta-interest` returns recent public launch-site beta-interest submissions.
- `GET /admin/support/subscriptions/{deviceId}` returns the current entitlement/subscription view for billing support.
- `POST /admin/support/entitlements/manual-adjust` records an audited manual entitlement adjustment for beta support grants or corrections.
- `POST /admin/support/tickets/metadata` stores support ticket status, priority, tags, and notes in the first-party support-event path.

Use the `x-admin-api-key` header. Do not expose this API to the Android app.

The static dashboard is documented in `docs/WEB_DASHBOARD.md`. It is hosted with the launch site, uses the same admin routes, and avoids paid helpdesk or admin-dashboard software during beta.

Crash reporting behavior is documented in `docs/CRASH_REPORTING.md`.
The product feedback loop is documented in `docs/BETA_FEEDBACK.md`.

## FAQ

### Does ChatMod Mobile read my private messages?

No. It is designed around the connected YouTube Live chat workflow.

### Does a diagnostic include chat messages?

No. The current diagnostic snapshot intentionally sends counts and state, not chat message bodies.

### Does a crash report include chat messages?

No. Crash markers include exception class, redacted message preview, top stack frame, stack hash, app version, and device model metadata. They do not include chat message bodies or tokens.

### Can I run the bot without cloud hosting?

Yes. The phone hosts the active bot runtime. The backend supports account metadata, entitlement, backups, and support workflows.

### Can I use it without paying for cloud services during beta?

Yes. The stack is designed to run locally or on free-tier-friendly services. Google Play publishing is the known exception because Play Console has a one-time registration fee.
