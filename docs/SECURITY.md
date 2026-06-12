# ChatMod Mobile Security Notes

## Token Custody

ChatMod Mobile should not store Google OAuth access tokens or refresh tokens on the phone.

The intended production model is:

- The backend completes Google OAuth and stores Google tokens encrypted server-side.
- The Android app receives only short-lived ChatMod backend device-session tokens.
- Device-session access tokens are cached in memory by `ChatModSessionManager` and refreshed before expiry.
- The app stores a local install ID in private app storage so the backend can issue a device session without exposing Google credentials to the phone.
- Server-side provider tokens are encrypted with `SECRET_ENCRYPTION_KEYS`, a versioned key ring separate from `JWT_SECRET`.
- Prisma-backed cloud backup configs are stored as encrypted JSON envelopes with the same backend key ring, while local/test in-memory backups remain plain process memory only.

This keeps Google token revocation, refresh, audit, and incident response in one backend-controlled place. If a future release adds direct Google OAuth on Android, it must use Android Keystore-backed encrypted storage and update this document before the checklist item can remain complete.

Key generation, storage, and rotation rules are documented in `docs/SECRETS_MANAGEMENT.md`.

## Local Data

Room and DataStore are used for local-first product state: rules, commands, timers, logs, settings, active runtime recovery, pending cloud sync jobs, and last selected stream context. Local wipe clears Room/DataStore-backed app data and queued sync jobs.
