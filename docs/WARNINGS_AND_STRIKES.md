# Warnings And Strikes

ChatMod Mobile supports a first warning/strike path for live moderation queues.

## What Exists

- Android queue rows expose a Warn action for flagged chat messages.
- The action calls the authenticated backend `/user-profiles/warnings` route.
- The backend creates or updates a device-scoped user profile for the chatter.
- User profiles retain the latest YouTube profile image URL when the queue source supplies one.
- Each warning records a `Strike` row and returns the updated strike count.
- The backend user-profile list includes recent strikes so warning history can be reviewed without per-user paid analytics tooling.
- The Android Users tab can refresh warned users, strike counts, latest strike reasons, and chatter channel IDs from the backend API.
- The Android Users tab can open a warned-user profile drawer with profile image, channel ID, first/last seen, message count, recent strikes, and editable moderator notes.
- Moderator notes save through the authenticated backend and remain scoped to the current account/device.
- The warned-user profile drawer can run manual whitelist, 5 minute timeout, and permanent hide actions against the selected live chat.
- Successful timeout and hide actions are persisted as recent user moderation history and shown in the profile drawer.
- The warned-user profile drawer can save a one-hour temporary whitelist entry with a backend-generated expiry and an expiring trusted-channel rule preset entry.
- If a selected YouTube live chat is available, Android also sends a compact warning message through the backend YouTube adapter.
- Warning text is kept under YouTube-safe live-chat limits and stripped of control characters before sending.

## Current Boundary

- This is a manual moderator action from the queue, not an automatic rule action yet.
- Unban flows, local-log author thumbnails, and automatic rule-triggered strike escalation still need UI/data follow-through.
- Automatic rule-triggered timeout/hide actions remain separate checklist items.

## Free-Tier Fit

The feature uses the existing Fastify API, Prisma data model, Android HTTP client, and YouTube Data API integration. It does not require paid moderation SaaS, paid analytics, or a paid crash/feedback provider.
