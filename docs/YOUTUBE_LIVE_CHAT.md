# YouTube Live Chat Handling

ChatMod Mobile should treat YouTube Live chat as an event stream, not just a list of plain text comments.

## Message Types

The backend Google YouTube adapter preserves the live-chat message type from YouTube:

- `textMessageEvent` - normal live chat text.
- `superChatEvent` - paid Super Chat with optional viewer comment, amount, and currency.
- `superStickerEvent` - paid Super Sticker with amount and currency.
- `newSponsorEvent` - new member event.
- `memberMilestoneChatEvent` - member milestone with optional viewer comment.
- `messageDeletedEvent` - YouTube reports a deleted message.
- `userBannedEvent` - YouTube reports a user ban.
- `systemEvent` - any unrecognized YouTube system event.

## Runtime Behavior

Android runtime handling is intentionally conservative:

- Non-demo foreground runtime polls and acts through authenticated backend YouTube live-chat endpoints, while the phone still owns the active bot loop, rule execution, timers, and local logs.
- Normal text, Super Chat text, and member milestone text can be logged and evaluated by the rule engine.
- Super Chat and Super Sticker metadata is recorded as runtime event metadata so purchase context is not lost.
- Member events are recorded as runtime events and treated as member context, not as ordinary spam.
- Deleted-message and user-ban events are recorded as runtime events and do not trigger bot commands, repeat-spam checks, deletes, hides, timers, or auto-replies.
- Bot command responses are only triggered by normal text messages, so system and purchase events do not cause unexpected automated replies.
- Raid-mode repeat/flood spam can trigger a temporary 5-minute timeout through the backend YouTube adapter, with the action still written to local/cloud audit logs.

## Live Chat vs Top Chat

ChatMod Mobile uses the YouTube Live Chat API feed for the selected live chat ID. It should not depend on YouTube's filtered **Top chat** viewer mode, because Top chat can hide messages from the creator's view. Moderation decisions, logs, and command handling should be based on the API live-chat stream so the bot sees the messages YouTube exposes to moderation tooling.

## Production Verification Needed

The backend adapter has mocked coverage for message type mapping. Final verification still needs a real test stream that exercises normal messages, deleted messages, member events, Super Chat/Super Sticker, and stream-ended behavior.
