# Bot Account Setup

ChatMod Mobile's bot name is the connected YouTube channel name. If the creator wants a custom bot identity instead of a third-party bot name, they should connect a dedicated YouTube channel that represents their stream.

## Recommended Setup

Use a dedicated bot channel when possible:

- Main creator channel: owns the stream and manages the audience.
- Bot channel: connects to ChatMod Mobile and sends commands, timers, test messages, and moderation actions.

This keeps the bot's visible name clear in chat and makes it easier to disconnect or rotate access later.

## When To Use The Main Channel

Use the main creator channel only when:

- The creator is testing alone.
- They do not care whether bot replies appear as the main channel.
- The stream setup does not allow a separate channel to chat or be added as a moderator.

For beta creators, the dedicated bot channel is the cleaner default.

## Setup Checklist

1. Create or choose the bot YouTube channel.
2. Give it a clear public name, such as the channel brand plus "Mod" or "Chat".
3. Confirm the channel can join the creator's live chat.
4. Connect that channel in ChatMod Mobile's Account tab.
5. In the stream selector, use the connected channel action instead of manually typing a different Channel ID.
6. Add the bot channel as a moderator in YouTube Studio or from a live chat message.
7. Run ChatMod Mobile's test message, then use **Check tools** to delete that test message through the moderator action route before enabling timers or destructive moderation actions.
8. Export account data after setup if support needs to verify linked channel metadata.

## What ChatMod Stores

The backend stores linked YouTube channel metadata and encrypted OAuth tokens. The phone keeps a ChatMod session token and local runtime settings; raw Google OAuth tokens do not live on the phone.

## Troubleshooting

- If the bot name is wrong, disconnect YouTube and reconnect the intended bot channel.
- If the test message sends but **Check tools** fails, check moderator permission before enabling delete, timeout, or hide actions.
- If the bot cannot find the stream, confirm the connected channel has access to the stream and refresh stream detection.
- If the bot cannot chat, confirm the bot channel is not blocked, age-restricted, or restricted by live-chat participant settings.
