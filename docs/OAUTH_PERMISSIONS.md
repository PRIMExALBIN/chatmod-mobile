# YouTube OAuth Permission Explanation

ChatMod Mobile needs YouTube permissions only for the bot account or channel that the creator connects.

## Required Scopes

- `youtube.readonly` - lets the app detect the active live stream and find the live chat ID.
- `youtube.force-ssl` - lets the bot send chat messages and perform moderation actions such as deleting messages where YouTube allows it.

## Plain-Language User Copy

ChatMod Mobile asks for YouTube access so your phone can run your live chat bot during a stream. The app uses this access to find your live chat, read messages, send your configured bot replies, and apply moderation actions you set up.

ChatMod Mobile does not need your Google password. You can disconnect the account at any time from the app or your Google account security settings.

The Android Account tab uses the backend `/youtube/connect-url` route for reconnect/sign-in. If Google OAuth env vars are not configured, the app shows the missing backend configuration instead of pretending sign-in is available. If OAuth is configured, the app opens the returned Google sign-in URL in the browser and tells the creator to complete sign-in there.

## Production Requirements

- Use the minimum scopes that support the chosen YouTube actions.
- Do not store Google OAuth tokens on the phone.
- Store Google OAuth tokens encrypted on the backend.
- Encrypt refresh tokens if backend-assisted OAuth flow is enabled.
- Show a disconnect account action.
- Show a local data wipe action.
- Log moderation actions with reasons for creator review.

## Current Implementation Status

The backend now supports signed OAuth state, connect URL generation, callback code exchange, encrypted token storage, linked channel id/title metadata capture, refreshed access-token persistence from Google OAuth token events, account-status reporting, connected-channel mismatch blocking, and a typed Google YouTube Data API client. The adapter has mocked tests for authenticated channel metadata lookup, active/scheduled broadcast listing, active-chat discovery, live-chat polling, message sending, message deletion, permanent user hide, and temporary timeout calls. End-to-end verification still requires real Google OAuth credentials, a configured redirect URI, and a private test stream.

Live-chat send endpoints validate outgoing text before calling YouTube: messages are trimmed, cannot be blank, cannot exceed 200 characters, and cannot contain control characters.

The Android Account tab source now exposes YouTube connect/reconnect, clear permission copy, missing OAuth env reporting, YouTube disconnect, linked channel metadata from account export, and local data wipe actions. The stream selector also reads `/youtube/account`, shows the connected channel, offers a **Use connected channel** action, and blocks a typed Channel ID that does not match the connected bot account. Emulator/device verification still requires Android tooling on the machine.

OAuth review copy, demo-script notes, and required submission evidence are organized in `docs/OAUTH_VERIFICATION_ASSETS.md`.
