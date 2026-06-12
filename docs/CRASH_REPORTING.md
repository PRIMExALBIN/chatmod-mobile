# Crash And Error Reporting

ChatMod Mobile uses a free-tier-friendly crash reporting path for beta instead of adding a paid SDK.

## Android Crash Flow

1. `CrashReporter` installs an app-level uncaught exception handler.
2. On crash, it writes one sanitized crash marker to private app SharedPreferences using a synchronous commit.
3. On the next app launch, it sends that marker to `POST /logs/support-events` as an authenticated support event.
4. If the backend device-session token is stale, it refreshes once and retries.
5. After a successful upload, the local pending crash marker is cleared.

The crash marker includes:

- Exception class.
- Short redacted message preview.
- Thread name.
- Top stack frame.
- Short stack hash.
- Up to eight stack frames.
- App version, Android SDK, manufacturer, and model.

It intentionally does not include chat message bodies, OAuth tokens, backend device-session tokens, database URLs, or full unbounded logs.

## Backend Error Flow

Backend API errors already return request IDs and are recorded in the API error store when a device context exists. Android support diagnostics can list recent API errors and crash support events together without a paid crash tool.

## Local Privacy

Account > Wipe local data clears:

- Commands and timers.
- Chat/moderation/runtime logs.
- Pending cloud-sync jobs.
- Local settings.
- Pending crash markers that have not been uploaded yet.

Cloud account deletion removes backend support events and device-scoped API errors through the account privacy API.

## Free-Tier Posture

This is enough for private beta and early creator testing. If volume grows, the same support-event data can be exported to a free Sentry Developer project or another provider later, but the MVP does not require that dependency.
