# ChatMod Mobile Promo Design

## Site Summary

Name: ChatMod Mobile
Vibe: a crisp live operations control room in your pocket.
Audience: YouTube creators who need reliable moderation without keeping a desktop rig open.
Core promise: your channel's custom moderation bot runs from your phone, with local control and honest backend sync.

## Colors

Use the app palette from the repository design direction, converted to video-friendly hex values.

- Background: #F6F8FC, a cool white control-room canvas.
- Surface: #FFFFFF, used for the phone UI and clean app panels.
- Strong surface: #E7EEF8, used for chips, dividers, and muted infrastructure.
- Ink: #172033, used for primary copy.
- Muted: #617086, used for secondary labels.
- Primary cobalt: #1F63CC, used for brand, status headers, and active controls.
- Primary strong: #16479E, used for deeper shadows and high-emphasis text.
- Live green: #00A878, used for running/live/synced signals.
- Warn amber: #C78314, used sparingly for flagged queue items.
- Danger red: #C22E1E, used for destructive moderation moments.

## Typography

Use `system-ui` for the product voice and "IBM Plex Mono" for numeric telemetry. The pairing keeps the product practical and operational while matching the Android/Material UI direction.

- Headlines: system-ui, 800-900, tight but readable.
- Body and UI labels: system-ui, 500-700.
- Metrics, timestamps, and IDs: IBM Plex Mono, 500-700, tabular numbers.

## Composition Shape

Format: 1920x1080 landscape.
Duration: 20 seconds.
Structure: one standalone HyperFrames composition with five scenes inside a single timeline.

Scene plan:

1. Creator problem: live chat pressure without a desktop babysitter.
2. Phone command center: start the foreground bot and connect to the live chat.
3. Moderation engine: queue, rules, destructive actions, and bypass logic.
4. Creator automation: commands, timers, logs, backups, support diagnostics.
5. Closing promise: "Your channel. Your bot. Live chat handled."

## Motion

Motion should feel Material 3 Expressive translated into video: stateful, bright, and purposeful.

- Transitions: primary push/slide with fast cobalt panels; one overexposure accent into the final promise.
- Entrances: every scene animates in with different y, x, scale, and opacity moves.
- Ambient: slow signal arcs, moving chat rows, and pulsing live dots.
- No jump cuts; scene transitions carry the exit.

## Assets

- ChatMod mark: converted from `android/app/src/main/res/drawable/ic_chatmod_mark.xml`.
- Product UI: drawn as video-native HTML/CSS mock screens derived from `DashboardScreen.kt` and `DashboardUiState.kt`.
- No stock imagery; the phone UI is the product signal.

## What Not To Do

- Do not make this a marketing landing page.
- Do not use dark navy/purple gradients as the whole identity.
- Do not show fake desktop SaaS dashboards; this is Android-first and phone-hosted.
- Do not imply production YouTube OAuth is already complete beyond the repo's honest setup state.
- Do not use vague AI copy like "unlock your potential" or fake engagement metrics.
