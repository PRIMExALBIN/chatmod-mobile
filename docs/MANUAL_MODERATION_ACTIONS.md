# Manual Moderation Actions

ChatMod Mobile supports manual moderation actions from the Queue tab and the Users tab profile drawer.

## Delete Queue Message

- A flagged queue row can delete the selected YouTube Live chat message when the row has a `youtubeMessageId`.
- Android asks for confirmation before sending the delete request.
- Android calls the authenticated backend `/youtube/live-chat/messages/delete` route with the selected message ID and a manual reason.
- The backend resolves the authenticated YouTube client, calls the YouTube delete-message adapter, and returns a timestamped action result.
- In local development without OAuth env vars, the same route uses the mock client so the UI can be tested without paid infrastructure.

## Hide User

- A warned user can be opened from the Users tab.
- The profile drawer can hide the selected chatter from the currently selected YouTube Live chat.
- Android asks for confirmation before sending the permanent hide request.
- Android calls the authenticated backend `/user-profiles/{id}/hide` route with the selected `liveChatId`.
- The backend verifies the user profile belongs to the authenticated account/device before calling the YouTube adapter.
- After a successful YouTube adapter call, the backend records a `hideUser` history item on the user profile.
- When Google OAuth is configured, the action uses the stored creator-owned YouTube tokens. In local development without OAuth env vars, it uses the mock client.

## Timeout User

- The same profile drawer can time out a selected chatter for 5 minutes from the currently selected YouTube Live chat.
- Android asks for confirmation before sending the timeout request.
- Android calls the authenticated backend `/user-profiles/{id}/timeout` route with the selected `liveChatId`, a 300 second duration, and a manual reason.
- The backend verifies the user profile belongs to the authenticated account/device before calling the YouTube adapter.
- The Google YouTube adapter sends a temporary live-chat ban with `banDurationSeconds`; local development keeps the same route available through the mock client.
- After a successful YouTube adapter call, the backend records a `timeoutUser` history item with duration and expiry.

## Unban User

- When YouTube returns a `liveChatBanId` for a ChatMod-created hide or timeout, the profile drawer shows an `Unban last action` control.
- Android asks for confirmation before sending the unban request.
- Android calls the authenticated backend `/user-profiles/{id}/unban` route with the saved `liveChatBanId`.
- The backend calls the YouTube live-chat ban delete adapter and records an `unbanUser` history item on the user profile.
- ChatMod cannot unban arbitrary viewers unless it has a valid YouTube `liveChatBanId` from a prior ChatMod hide or timeout.

## Whitelist User

- A warned user can be quick-whitelisted from the same profile drawer.
- Android calls the authenticated backend `/user-profiles/{id}/whitelist` route.
- The backend persists a permanent `WhitelistEntry` for the authenticated account profile.
- Android also adds the chatter channel ID to the active/default rule preset's `trustedChannelIds` list so rule engines can bypass that user.
- The drawer also exposes a one-hour temporary trust action that saves `durationSeconds` to the same route, receives a server-generated `temporaryUntil`, and adds a matching expiring trusted-channel entry to the active/default rule preset without changing permanent trusted IDs.

## Current Boundary

- Queue delete requires a selected queue item with a YouTube message ID.
- Hide is a permanent YouTube hide/ban action where YouTube supports it.
- Timeout is a temporary YouTube live-chat ban action where YouTube supports it.
- Unban is available only for ChatMod-created hide/timeout actions where YouTube returned a saved ban id.
- Bulk user actions are still separate checklist items.
- A selected active live chat is required before the Android hide or timeout action can run.

## Free-Tier Fit

The feature uses the existing Fastify backend, encrypted YouTube token custody, Android HTTP client, and YouTube Data API integration. It does not require paid moderation infrastructure.
