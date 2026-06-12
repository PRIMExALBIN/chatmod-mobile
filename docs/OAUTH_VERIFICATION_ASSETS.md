# OAuth Verification Assets

This packet contains the copy and evidence ChatMod Mobile should prepare for Google OAuth review. It is not a substitute for the actual Google review, real credentials, or a demo video.

## App Identity

- App name: ChatMod Mobile
- Package name: `com.chatmod.mobile`
- Product summary: Custom YouTube Live moderation bot hosted from the creator's Android phone.
- Launch surface: Android app plus Fastify backend for OAuth token custody, account metadata, backups, billing, diagnostics, and audit sync.

## Requested Scopes

| Scope | Why ChatMod Needs It | User-Facing Feature |
| --- | --- | --- |
| `https://www.googleapis.com/auth/youtube.readonly` | Find the authenticated channel, active or scheduled live broadcasts, and live chat IDs. | Stream selector and connection readiness. |
| `https://www.googleapis.com/auth/youtube.force-ssl` | Send bot messages and perform YouTube-allowed moderation actions such as message delete, timeout, and hide. | Commands, timers, test message, moderator check, and live moderation. |

ChatMod should not request broader scopes for MVP.

## Consent Screen Copy

Plain-language explanation:

> ChatMod Mobile uses YouTube access so your Android phone can run your own live chat bot during a stream. It finds your live chat, sends messages you configure, and applies moderation actions you choose. Google OAuth tokens are encrypted on the backend and are not stored directly on your phone.

Disconnect copy:

> You can disconnect YouTube from ChatMod Mobile at any time. Disconnecting removes stored YouTube account rows and attempts token revocation through Google.

## Demo Script

Use a private or unlisted test stream.

1. Open ChatMod Mobile.
2. Review YouTube permission copy in Account.
3. Connect a dedicated YouTube bot channel.
4. Use the connected channel in the stream selector.
5. Detect the active live stream.
6. Send the test message.
7. Use **Check tools** to delete the test message.
8. Start the foreground bot service.
9. Trigger a safe command and a timer.
10. Trigger a blocked-term test message from a separate viewer account.
11. Show the moderation log and account export/delete controls.
12. Disconnect YouTube.

## Reviewer Notes

- ChatMod Mobile is not affiliated with Google or YouTube.
- The phone hosts the active bot runtime; the backend does not run a hidden hosted bot for the creator.
- The connected YouTube channel name is the visible bot name in live chat.
- The app guides creators to grant moderator access explicitly; it does not silently add itself as a moderator.
- The app stores Google OAuth tokens encrypted on the backend, not in Android local storage.
- Creators can export account data, disconnect YouTube, delete cloud account data, and wipe local app data.

## Required Evidence Before Submission

- [ ] Production OAuth client ID and redirect URI.
- [ ] Deployed privacy policy URL.
- [ ] Deployed terms URL.
- [ ] Support email.
- [ ] Screen recording following the demo script if Google requests it.
- [ ] Screenshots of Account, stream selector, foreground-service running state, Logs, and privacy controls from a real Android build.
- [ ] Confirmation that scopes in Google Cloud match `docs/OAUTH_PERMISSIONS.md`.

## Policy Links To Provide

- Privacy policy: deploy `launch-site/privacy.html`.
- Terms: deploy `launch-site/terms.html`.
- Support: use final monitored support email from the Play listing.
