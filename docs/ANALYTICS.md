# Basic Analytics

ChatMod Mobile uses first-party, opt-in usage analytics for beta. No paid analytics SDK is required.

## Product Rules

- Default is off.
- The creator enables analytics in Settings.
- The Android reporter only builds analytics requests after the Settings opt-in check passes.
- The backend requires `consent: true` on every analytics event payload.
- Analytics events must not include chat message bodies, OAuth tokens, backend tokens, email addresses, URLs, or arbitrary text.
- Events are authenticated with the same short-lived backend device-session token as the rest of the app.
- Failed analytics sends are dropped instead of blocking live moderation.

## Backend Contract

- `POST /analytics/events` records one usage event only when the authenticated payload includes `consent: true`.
- `GET /analytics/events` lists events for the current authenticated device.
- Events are stored through the existing support-event persistence path with `details.eventType = "usage_analytics"`, so the free-tier beta does not need another database table or paid event pipeline.
- `/logs/support-events` filters analytics events out of the creator-facing diagnostics list.

Allowed event names:

- `app_open`
- `bot_start`
- `bot_stop`
- `tab_selected`
- `command_saved`
- `timer_saved`
- `settings_changed`
- `diagnostic_sent`

## Android Behavior

The Android app exposes a Settings toggle named `Share usage analytics`. When enabled, `UsageAnalyticsReporter` can send small product events such as app open, bot start/stop, tab selection, saved command/timer, and sent diagnostic. When disabled, the reporter returns before building or sending the event request.

The reporter filters unsafe property names locally, and the backend validates the event shape again.

## Local Moderation Analytics

Rule-match moderation logs are kept in the local Room log store and grouped on-device for the Logs tab's `Top triggered rules` summary. Completed runtime session summaries also persist `topTriggeredRulesJson` in the session-summary runtime event metadata, keeping rule analytics available without adding a paid analytics SDK.

Non-destructive `flagForReview` matches are tagged locally as review candidates and shown in the Logs tab's false-positive review list. When a creator marks one as a false positive, the row stays in the audit log but drops out of unresolved review and active top-rule counts; the backend stream-session action log also stores the review status when the action has synced. If the creator taps Tune preset, the active/default rule preset is adjusted and saved for later streams.

Moderation action entries also carry action type and subject keys locally. The Logs tab uses those fields to count distinct users timed out/hidden for the current local log window, while completed runtime summaries persist `usersTimedOutOrHidden`.

## Local Logs Tab Cards

The Android Logs tab now turns recent Room-backed chat, moderation, and runtime entries into on-device analytics cards:

- Most active chatters from local chat, rule-match, and moderation subject keys.
- Most used commands from live command runtime events, with completed session summaries as a fallback.
- Rule effectiveness from rule-match totals, destructive actions, and false-positive review counts.
- Spam attempts over time from local rule-match buckets for the recent session window.
- Uptime and reconnect history from local runtime session-summary and reconnect/backoff events.

These cards are intentionally local and recent-window first. They do not require a paid analytics SDK, cloud warehouse, or background data export, and they avoid storing chat message bodies in product analytics events.

## Cross-Stream Audit Analytics

`GET /stream-sessions/analytics/summary` summarizes synced stream-session audit rows for the authenticated account. It can be filtered by `profileId`, `days`, and `limit`.

The summary is built from existing stream session, chat message, moderation action, and runtime event records. It returns total messages, moderation actions, runtime events, uptime, reconnect count, per-stream totals, daily totals, top chatters, command usage, rule effectiveness, rule effectiveness by preset/version, spam attempts by day, and uptime by stream.

The Android Logs tab has a `Pro trends` card that loads this summary automatically when the creator opens Logs and also exposes a manual refresh action. It renders compact bar trends for recent message volume, cross-stream audience activity, command usage, rule effectiveness, rule effectiveness by preset revision, and stream uptime/reconnect health.

Creator plans also expose a local heuristic after-stream summary through `GET /stream-sessions/{id}/ai-summary`. It reuses synced stream-session logs and returns repeated questions, top chatters, moderation notes, highlights, and follow-up suggestions without a paid AI provider or analytics warehouse.

Repeated questions can be turned into Creator FAQ replies through the FAQ knowledge-base endpoints. Queue-row suggestions then match viewer questions against saved FAQ entries instead of generating unsupported answers.

Android runtime moderation action logs include the active rule preset id, name, and revision in local Room metadata and the pending cloud-sync payload. The backend stores that optional metadata on `ModerationActionLog`, includes it in account/export paths, and groups it in `ruleEffectivenessByPreset`.

This keeps Pro-style cross-stream analytics free-tier friendly for beta: no paid analytics SDK, no separate warehouse, and no product analytics event pipeline containing chat bodies.

## Free-Tier Posture

For beta, usage analytics are stored in Postgres alongside support events and are covered by the same account export/delete privacy path. If traffic grows, this route can fan out to a free-tier analytics warehouse later without changing the Android UI contract.
