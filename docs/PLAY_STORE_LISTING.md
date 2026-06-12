# Play Store Listing Draft

This is a launch artifact draft, not a submitted Play Console listing. It must be reviewed against the final Android build, support email, privacy policy URL, terms URL, screenshots, and Google Play Data safety form before production release.

The detailed Play Data Safety source worksheet lives in `docs/PLAY_DATA_SAFETY.md`. Run `npm run store:check` before Play Console review to validate listing lengths, policy references, manifest permission coverage, and Data Safety planning copy.

## App Name

ChatMod Mobile

## Short Description

Run your own YouTube Live moderation bot from your Android phone.

Character count: 65 / 80.

## Full Description

ChatMod Mobile helps YouTube creators run a custom live chat moderation bot from an Android phone.

Instead of relying on a third-party bot name, creators can connect a dedicated YouTube bot channel, verify the active live chat, and run moderation from a visible foreground service on their phone. The app is built for lightweight streaming setups where a desktop control room is not always open.

What ChatMod Mobile supports:

- Active livestream detection and live chat connection status.
- Local moderation rules for blocked terms, links, repeated messages, caps, emoji, mentions, symbol spam, and first-time chatter bursts.
- Emergency mode, link lockdown, and high-pressure live controls.
- Custom commands and timed messages.
- Warning, strike, timeout, hide, whitelist, and manual moderation workflows where YouTube allows them.
- Local Room-backed chat, moderation, and runtime logs.
- CSV log export and false-positive review.
- Rule presets that can be tuned after a stream.
- Account export, YouTube disconnect, local data wipe, settings backup, and support diagnostics.

ChatMod Mobile is local-first. The phone hosts the active bot runtime; the backend handles account metadata, encrypted YouTube token custody, entitlement, settings backup, support diagnostics, and synced audit records.

Beta status:

ChatMod Mobile is being prepared for closed beta. Some production gates, including real Google OAuth verification, Play Billing validation, Play Console release tracks, and real livestream QA, must be completed before public launch.

Paid-plan copy should stay subscription-only for MVP. Lifetime purchase is not offered in the closed beta or public v1 listing.

## Search Terms To Keep Natural

- YouTube Live moderation
- live chat bot
- stream moderation
- Android moderation bot
- YouTube commands and timers

Do not stuff keywords or claim platform affiliation. ChatMod Mobile is not endorsed by YouTube or Google.

## Privacy Label Draft

Final Play Data safety answers must be completed from the production build and legal review. `docs/PLAY_DATA_SAFETY.md` is the source worksheet; the table below is only a quick listing-facing summary of the current implementation.

| Data category | Collected | Shared | Purpose | Notes |
| --- | --- | --- | --- | --- |
| Personal info: email/account identifiers | Yes | No | Account management, authentication, support | Depends on final account/session provider fields. |
| App activity: app interactions | Optional | No | App functionality, analytics, support | Usage analytics are opt-in support-event metadata. |
| App info and performance: crash logs/diagnostics | Yes | No | App functionality, analytics, support | Crash markers and support diagnostics are first-party. |
| User-generated content: live chat text/logs | Yes, when logged/synced | No | App functionality, moderation audit, support if exported by creator | Local logs can be exported; backend sync is account-scoped. |
| Device or other IDs | Yes | No | App functionality, fraud prevention, support | Device/install/session identifiers support auth and diagnostics. |
| Financial info: purchase history | Yes, when billing is enabled | No | Entitlement and subscription validation | Play Billing validation still needs real Play Console verification. |

## Data Safety Commitments

- Google OAuth tokens are stored encrypted on the backend, not on the phone.
- Creators can disconnect YouTube.
- Creators can export account data.
- Creators can delete cloud account data.
- Creators can wipe local phone data.
- Chat data is not used for model training.
- Diagnostics should avoid chat message bodies unless the creator explicitly exports logs for support.

## Store Assets Still Needed

- Final support email.
- Deployed privacy policy URL.
- Deployed terms URL.
- Phone screenshots from a real Android build.
- Feature graphic if required.
- OAuth review packet from `docs/OAUTH_VERIFICATION_ASSETS.md`.
- Demo video for OAuth review if Google requests it.

## Source References

- Google Play listing field limits: https://support.google.com/googleplay/android-developer/answer/9859152
- Store listing best practices: https://support.google.com/googleplay/android-developer/answer/13393723
- Google Play Data safety guidance: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
