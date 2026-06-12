# Privacy Policy Draft

This is a draft for product planning, not final legal advice.

## What ChatMod Mobile Collects

- Account and channel metadata needed to connect YouTube.
- Device registration metadata such as app version and install ID.
- Moderation settings, command profiles, timer profiles, and rule presets.
- Chat message logs, moderation logs, and bot runtime events when the creator chooses to keep or back them up.
- Subscription entitlement data.
- Launch-site beta-interest email, optional creator name, optional YouTube channel URL, and optional note when a creator requests beta access.

## What ChatMod Mobile Does Not Collect

- Google passwords.
- Private messages outside the connected YouTube Live chat workflow.
- Unrelated device contacts, photos, files, or location.

## How Data Is Used

Data is used to run the creator's YouTube Live moderation bot, sync settings, validate entitlement, provide support, and improve reliability.

## Creator Controls

Creators should be able to:

- Disconnect YouTube.
- Stop the foreground bot service.
- Delete local logs.
- Delete cloud backups.
- Export settings and logs.
- Request account deletion.

Current backend status:

- `GET /accounts/export` exports account, device, linked account metadata including YouTube channel id/title when connected, channel profiles, commands, timers, backups, support events, device-scoped API errors, audit logs, and entitlement data.
- Account exports report whether OAuth tokens exist, but do not include raw encrypted access or refresh token values.
- `POST /accounts/youtube/disconnect` attempts Google token revocation when OAuth credentials are configured, then deletes stored YouTube linked-account/token rows for the current account.
- `DELETE /accounts/current` deletes the current account and cascades owned cloud data, including devices, linked accounts, profiles, commands, timers, backups, subscription rows, support diagnostics, device-scoped API errors, and account audit logs.
- Android crash markers are stored locally only until the next app launch can upload them as support events; local data wipe clears pending crash markers before upload.
- Usage analytics are opt-in, require `consent: true` on the backend event payload, and are stored as backend support-event metadata; they are included in account export/deletion through the existing support diagnostics path.
- Authenticated beta feedback notes are creator-written, stored as backend support-event metadata, and included in account export/deletion through the same support data path.
- Public launch-site beta interest is stored separately under the synthetic support device id `launch-site-beta-interest` until it is matched to a real app account or deleted during beta operations.
- The backend retention command can prune old support events, API errors, ended-stream detail logs, and old backup versions according to the documented beta retention policy.
- Android log export shares the current filtered local log view through the device share sheet, and backend stream-session export returns account-scoped synced logs as JSON or CSV.
- `DELETE /backups/{id}` deletes an account-scoped cloud backup.
- `POST /backups/settings` and `POST /backups/{id}/restore` let the creator back up and restore command/timer settings with backend validation.
- Prisma-backed cloud backup configs are encrypted at rest with the backend `SECRET_ENCRYPTION_KEYS` key ring. Account export and restore decrypt them server-side for the authenticated creator.
- The Android dashboard now includes Account tab source wiring for account export, YouTube disconnect, backup list/delete, settings backup/restore, account deletion confirmation flows, and local data wipe for Room/DataStore data on the phone.
- Google token revocation is implemented in source, but still needs end-to-end verification with production OAuth credentials.

## Security Commitments

- Use minimum YouTube scopes.
- Encrypt sensitive tokens and Prisma-backed cloud backup configs.
- Protect backend routes with signed session tokens.
- Scope cloud backup reads and deletes to the authenticated account.
- Keep audit logs for moderation actions.
- Avoid using chat data for model training unless the creator explicitly opts in later.
