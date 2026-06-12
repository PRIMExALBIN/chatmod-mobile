# Whitelist

ChatMod Mobile supports permanent trusted-user whitelisting from the warned-user profile drawer.

## What Exists

- Android can quick-whitelist a warned user from the Users tab profile drawer.
- The action creates or updates a backend `WhitelistEntry` scoped to the authenticated account profile.
- The same action updates the selected/default rule preset with the chatter's `authorChannelId` in `trustedChannelIds`.
- The profile drawer can also save a one-hour temporary whitelist record with server-generated `temporaryUntil` and add an expiring trusted-channel entry to the active/default rule preset.
- Backend and Android rule engines allow trusted channel IDs before applying blocked terms, links, caps, emoji, mention, symbol, regex, repeated-message, or flood filters.
- The dashboard caches the active rule preset in DataStore, and the foreground service watches that cache while the bot runs.
- The rule preset UI shows a Trusted users row when trusted channel IDs are active.

## Current Boundary

- Backend and Android rule engines ignore expired temporary trusted-channel entries when those entries are present in the active profile.
- A rule preset must be loaded, saved, selected, or changed from the dashboard before the offline foreground service has a cached active preset to use.
- Verified-user detection and local Room whitelist storage remain separate checklist items.
- Whitelisting does not unhide/unban a user; it only bypasses moderation filters once they can chat. Use the profile drawer's unban action when ChatMod has a saved YouTube `liveChatBanId` from a prior ChatMod-created hide or timeout.

## Free-Tier Fit

The whitelist uses the existing Fastify API, Prisma `WhitelistEntry` model, Android HTTP/demo clients, and rule preset contract. It does not require paid moderation infrastructure.
