# Emergency Mode Guide

Emergency mode is the creator's fast safety state for chat spikes, raids, or moments where the bot should prioritize control over promotion.

## What It Does Now

- Stores the emergency-mode flag locally in DataStore.
- Shows the current state in the Android Settings dashboard.
- Shows a live-controls bottom sheet from the dashboard health band for stream-time pause/start, emergency mode, and link lockdown.
- Feeds the live setting into the foreground bot service.
- Exposes an Emergency on/off action from the foreground-service notification.
- Adds the emergency-mode state to runtime heartbeat metadata.
- Pauses scheduled timer messages while emergency mode is active.
- Enables the high-security raid posture for the active runtime profile.
- Tightens runtime moderation thresholds for caps, repeated characters, emoji, mentions, symbols, per-user floods, and first-time chatter bursts.
- Can be paired with the notification Link Lockdown action to delete live-chat links during a spike.

## Why Timers Pause

Timers are usually promotional or informational. During a spam burst, raid, or sensitive moderation moment, automatic scheduled messages can make the chat feel noisier and can hide important moderation context. Emergency mode keeps moderation and command handling active while suppressing scheduled timer sends.

## Operator Notes

- Use emergency mode when chat volume or risk spikes.
- Use it during raids when several brand-new chatters arrive together; the runtime flags that burst for review without automatically deleting ordinary hello messages.
- Turn it off after the incident so normal timer cadence can resume.
- Link lockdown can be used alongside emergency mode when spam links are the main problem.
- The foreground notification keeps Stop, Emergency, and Link Lockdown reachable while the app is not on screen.
- The in-app live controls can send a one-tap recommendation message suggesting subscribers-only or members-only mode during a spam spike; this posts a chat recommendation and does not pretend to change YouTube Studio settings automatically.
- The dashboard health band keeps pause/start one tap away, while the live-controls sheet groups higher-risk toggles together.
- Timer timestamps are only marked sent when a timer actually sends, so paused timers are not falsely recorded as delivered.

## Still Planned

- Dedicated UI controls for custom raid thresholds.
- Clear in-stream status treatment in the dashboard health panel.
