# Add ChatMod As A YouTube Moderator

ChatMod Mobile can only perform moderation actions that YouTube allows for the connected bot channel. Add the bot channel as a moderator before relying on delete, timeout, or hide actions.

## Current YouTube Studio Path

YouTube's current Help guidance says moderators can be assigned from YouTube Studio:

1. Open YouTube Studio.
2. Go to Settings.
3. Open Community moderation.
4. In the User management tab, add the bot channel URL under either Standard moderator or Managing moderator.
5. Save.

For ChatMod Mobile, start with Standard moderator unless the creator specifically needs managing-moderator permissions. Managing moderators have more permissions, so use the least access that supports the beta test.

## Add From A Live Chat Message

YouTube also supports adding a moderator from a live chat message:

1. Start or open the stream's live chat.
2. Have the bot channel send a safe test message.
3. Open the menu next to that message.
4. Choose Add as standard moderator or Add as managing moderator.

This is often the fastest path during a private test stream because it proves the bot channel can chat and gives the creator the exact channel identity to promote.

## Permission Check In ChatMod

After adding the moderator:

1. Refresh stream detection in ChatMod Mobile.
2. Send the test message from the dashboard.
3. Use **Check tools** to delete that test message through ChatMod's backend YouTube delete route.
4. Try one non-destructive flow next, such as warning or flagging.
5. In a private test stream, verify timeout/hide behavior against a test viewer account.
6. Verify unban only from a ChatMod-created hide or timeout entry that has a saved YouTube `liveChatBanId`.
6. Check Logs for the moderation action and any YouTube API error code.

## What The API Supports

Google's YouTube Live Streaming API models live chat moderators as `liveChatModerator` resources. The API can list, add, and remove moderators for a live chat, but those requests require authorization from the live chat channel owner. ChatMod Mobile should not silently add itself as a moderator during MVP; the creator should explicitly grant the bot channel access.

## Official References

- YouTube Help: https://support.google.com/youtube/answer/9826490
- YouTube Live Streaming API `liveChatModerators`: https://developers.google.com/youtube/v3/live/docs/liveChatModerators
