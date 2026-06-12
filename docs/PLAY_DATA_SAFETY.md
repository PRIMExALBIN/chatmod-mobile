# Google Play Data Safety Draft

This is a release-planning worksheet for the Google Play Data safety form. It is not legal advice and is not a Play Console submission. Complete the final form from the signed production Android build, deployed privacy policy, final support contact, and real Google Play billing state.

## Official References

- Data safety form guidance: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Store listing setup and field limits: https://support.google.com/googleplay/android-developer/answer/9859152
- Store listing best practices: https://support.google.com/googleplay/android-developer/answer/13393723

## App Scope

- App name: ChatMod Mobile.
- Package namespace: `com.chatmod.mobile`.
- Core function: phone-hosted YouTube Live moderation bot with a backend for account metadata, encrypted OAuth token custody, entitlement, backup, support, diagnostics, and audit sync.
- Ads: no ads in current source.
- Third-party analytics SDKs: none in current source.
- Paid crash SDKs: none in current source.
- Data sharing: current source does not sell or share collected user data with third parties for advertising. YouTube and Google Play APIs are service providers required for requested app functionality.
- Not sold: current source does not sell creator, viewer, device, chat, diagnostics, or billing data; collected data is not sold.
- Model training: chat, moderation, diagnostics, and support data are not used for model training in the current source.

## Android Manifest Permission Inventory

Current manifest permissions:

| Permission | Reason | Data safety note |
| --- | --- | --- |
| `android.permission.INTERNET` | Backend and YouTube API communication. | Enables off-device transmission declared below. |
| `android.permission.ACCESS_NETWORK_STATE` | Detect offline/reconnecting states. | Does not collect location, contacts, photos, or files. |
| `android.permission.WAKE_LOCK` | Keep foreground bot runtime reliable while active. | No user data type by itself. |
| `android.permission.POST_NOTIFICATIONS` | Foreground service and live bot status notifications. | User-visible runtime status only. |
| `android.permission.FOREGROUND_SERVICE` | Run active bot runtime visibly. | Required for phone-hosted bot execution. |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | Declare foreground service type for data-sync runtime. | Supports live-chat polling/sync. |
| `android.permission.BIND_QUICK_SETTINGS_TILE` | Protect the optional Quick Settings tile service. | System binding permission for the tile, not a data collection permission. |

Permissions not currently requested: camera, microphone, contacts, location, calendar, phone, SMS, advertising ID, broad file access, photos/videos, and all-files access.

## Data Collection And Use Draft

| Play data category | Collected? | Shared? | Required? | Purposes | Current source evidence |
| --- | --- | --- | --- | --- | --- |
| Personal info: email or account identifiers | Yes | No | Required for account/backend support where configured | App functionality, account management, support | Backend user/account metadata, launch-site beta interest email, support admin lookup. |
| App activity: app interactions | Optional | No | Optional | App functionality, analytics, support | Opt-in usage analytics support events with consent. |
| App info and performance: crash logs and diagnostics | Yes | No | Required for support/reliability during beta | App functionality, analytics, support | First-party crash markers, support diagnostics, API error logs. |
| User-generated content: live chat messages and moderation logs | Yes, when logged or synced | No | Required for moderation/audit features when enabled | App functionality, moderation audit, support if creator exports logs | Room logs and backend stream-session logs. |
| Device or other IDs | Yes | No | Required | App functionality, fraud prevention, support | Device ID, install ID, backend session correlation, request IDs. |
| Financial info: purchase history | Yes, when Play billing is enabled | No | Required for paid entitlement validation after store launch | App functionality, account management | Google Play validation path and subscription entitlement rows. |
| Photos and videos | No | No | Not applicable | Not applicable | No manifest permissions or source paths for photo/video collection. |
| Audio files or microphone data | No | No | Not applicable | Not applicable | No microphone permission. |
| Contacts | No | No | Not applicable | Not applicable | No contacts permission. |
| Location | No | No | Not applicable | Not applicable | No location permission. |
| Health and fitness | No | No | Not applicable | Not applicable | No health data source. |

## Security Practices Draft

- Data is encrypted in transit with HTTPS for hosted backend and Google/YouTube/Play APIs.
- Google OAuth access and refresh tokens are encrypted on the backend with `SECRET_ENCRYPTION_KEYS`.
- Cloud backup configs stored in Postgres are encrypted before persistence.
- Raw Google passwords are never collected.
- Creators can disconnect YouTube.
- Creators can export account data.
- Creators can delete cloud account data.
- Creators can wipe local Room/DataStore data on the phone.
- Data retention pruning can remove old support events, API errors, ended-stream detail logs, and old backup versions.

## Privacy Policy Consistency Checklist

Before submitting the Play form:

- Deploy `launch-site/privacy.html` or the final reviewed privacy policy to a public URL.
- Ensure the privacy policy mentions beta-interest email collection if the launch-site form stays live.
- Ensure the privacy policy mentions support diagnostics, crash markers, opt-in analytics, device/install IDs, YouTube channel metadata, synced logs, account export, account deletion, YouTube disconnect, and local data wipe.
- Ensure Data safety answers match `docs/PRIVACY_DRAFT.md`, `docs/PLAY_STORE_LISTING.md`, and the production Android manifest.

## Known Non-Final Items

- Real Google OAuth verification still needs production credentials.
- Google Play purchase validation still needs Play Console credentials and live purchase tests.
- Screenshots and final policy URLs still need real deployed assets.
- Legal review is still required before public launch.
