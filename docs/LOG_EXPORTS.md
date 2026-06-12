# Log Exports

ChatMod Mobile supports log export without paid object storage.

## Android Export

- The Logs tab exports the current filtered local log view as CSV text through Android's native share sheet.
- Export rows include log kind, timestamp, title, detail, action type, rule reason, review status, and subject key.
- The export is built from the Room-backed local log store, so it works for current-stream review even before cloud sync finishes.

## Backend Export

- `GET /stream-sessions/{id}/export?format=json` returns the synced stream-session audit bundle with an `exportedAt` timestamp.
- `GET /stream-sessions/{id}/export?format=csv` returns a CSV attachment containing chat messages, moderation actions, review metadata, and runtime events.
- The endpoint uses the same authenticated stream-session ownership checks as the normal log API.

## Free-Tier Posture

For beta, exports are generated on demand from local Room data or Postgres rows. Cloud object storage is intentionally avoided until export blobs become too large for direct responses.

## Privacy Notes

Exports can include live-chat text, moderation reasons, review notes, channel identifiers, and runtime event metadata. Creators should treat exported CSV/JSON files as private stream records and share them only with people who need moderation or support context.
