# Commands Guide

Commands are creator-defined chat replies triggered by messages that start with `!`.

## MVP Commands

- `!discord`
- `!rules`
- `!socials`
- `!schedule`
- `!commands`
- `!uptime`

## Command Rules

- Command names and aliases must start with `!`.
- Commands can be disabled without deleting them.
- Enabled commands can be manually sent by the creator to the selected live chat.
- Access levels are `everyone`, `members`, `mods`, and `owner`.
- Cooldowns prevent repeated replies from spamming chat. The runtime supports both global command cooldown keys and per-user command cooldown keys.
- Aliases trigger the same command response.

## Response Variables

- `{username}` - the viewer display name.
- `{args}` - everything typed after the command.
- `{streamTitle}` - current stream title when available.
- `{uptime}` - elapsed stream time when the active runtime start time is available.
- `{time}` - current ISO timestamp.
- `{random}` - random number for lightweight variety.

## Production Notes

Command replies must go through the shared bot message rate limiter. Moderator and owner-only commands must never run for normal viewers. Command responses are validated on the backend to reject unsafe link protocols, embedded URL credentials, and private/local-network targets before they can sync. Manual command sends use a creator-owned trigger context, render response variables through the backend command evaluator, and then pass the rendered text through the same live-chat text safety limits before sending.

## Current App Status

The Android dashboard now has create, edit, delete, manual send, access-level, alias, cooldown, enabled-state, validation UI, Room persistence wiring, local-first sync behavior, authenticated HTTP sync against the backend command API, settings backup/restore source wiring, and shared bot-message rate limiting in the coordinator. Command sync uses `PUT /commands/{id}` so the phone can keep a stable local ID across create, edit, delete, backup, and restore. Manual send uses `POST /commands/{id}/send` with the selected live chat ID, stream title, and active runtime start timestamp when available.

The local starter command pack includes safe enabled defaults for `!rules`, `!commands`, and `!uptime`. Channel-specific link templates such as `!discord`, `!socials`, and `!schedule` are present but disabled until the creator edits them with real links or schedule details.

The backend now enforces safe URL validation for command responses. Public `http` and `https` links are allowed; dangerous protocols, local/private hosts, and credential-stuffed links are rejected.

Settings backups use the same command validation and safe URL checks as normal command saves.

The next production step is verifying command sync from an emulator/phone against a running backend and then against a real YouTube test stream.
