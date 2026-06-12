# OBS Browser Overlay

ChatMod Mobile now has a Pro/Creator OBS browser-source overlay that is backed by the same stream-session audit logs the Android phone syncs to the backend. It is not a separate hosted dashboard, paid widget, or third-party overlay service.

## What It Shows

The public overlay URL polls `GET /overlays/public/{token}/state` and renders:

- Stream title and live/standby/ended status.
- Message count, unique chatter count, destructive moderation action count, and spam-attempt count.
- Recent moderation actions with labels and reasons.
- Recent runtime events such as command sends and reconnect/backoff state.

Recent chat text is off by default. Creators can enable `showRecentChat`, but the default public overlay state avoids exposing chat text to anyone who has the OBS URL.

## Backend Contract

Authenticated setup routes:

- `GET /overlays/profiles/{profileId}` returns the current safe configuration and entitlement status.
- `PUT /overlays/profiles/{profileId}` creates or updates the overlay configuration.
- `POST /overlays/profiles/{profileId}/rotate-token` invalidates the old public URL and returns a fresh one.

The Android Settings tab uses these routes to load the active profile overlay, enable or pause it, choose privacy/display toggles, save changes, and rotate the public OBS URL.

Public read-only routes:

- `GET /overlays/public/{token}` returns the transparent-friendly overlay HTML.
- `GET /overlays/public/{token}/state` returns sanitized JSON state for polling.

The public token is generated server-side, stored only as a SHA-256 hash, and previewed as a shortened token hint in authenticated configuration responses. The full public URL is returned only when the token is first created or rotated.

## Entitlement

The feature is encoded as `obsOverlay`:

- Starter: `false`.
- Pro: `true`.
- Creator: `true`.

This keeps the beta free-tier friendly while leaving the overlay as a paid-plan feature once Google Play Billing is verified. No paid streaming overlay provider, realtime database, object storage, or analytics warehouse is required.

## OBS Setup

1. Configure the overlay from the authenticated app/backend route.
2. Copy the returned `publicUrl`.
3. In OBS, add a Browser source.
4. Paste the public URL.
5. Use a transparent source background and size the source around `720 x 260` for the default control-room theme.

Rotate the token any time the URL is exposed publicly or shared with the wrong person.

## Current Verification

Source-complete evidence:

- Prisma model and migration: `OverlayConfig`.
- Backend routes: `backend/src/modules/overlays/`.
- OpenAPI paths and schemas: `shared/contracts/openapi.yaml`.
- Android Settings panel controls for refresh, save, rotate URL, display toggles, and privacy-default hidden chat text.
- Backend test coverage proves Starter gating, Pro setup, public HTML, public state from synced stream-session logs, privacy-default hidden chat text, and token rotation invalidating the old URL.

Still external/manual:

- Load the deployed URL inside OBS Browser Source.
- Verify sizing and transparency against an actual stream layout.
- Verify the public overlay URL after the Render/Neon backend is deployed.
