# ChatMod Mobile Battery Optimization Guide

ChatMod Mobile hosts the bot from the creator's phone. During a live stream, the bot runtime uses a foreground service with an ongoing notification so Android knows the work is user-visible and active.

## What The App Does

- Runs the live bot loop only while the creator starts the bot for a stream.
- Shows a persistent foreground-service notification while monitoring chat.
- Provides notification actions for stop, emergency mode, and link lockdown so live controls remain reachable outside the app.
- Holds a partial wake lock only while the bot loop runs, with a timeout.
- Uses WorkManager for non-live queued sync instead of keeping background work alive forever.
- Shows a Settings warning when Android reports that battery optimization still applies to ChatMod Mobile.
- Logs a `battery_optimization_active` runtime event when Android battery optimization may interfere with live moderation.

## Creator Setup Checklist

Before a long stream:

- Start ChatMod Mobile from the dashboard and confirm the foreground notification is visible.
- Keep the phone charged, or start above 50 percent battery.
- Turn off Battery Saver during the stream when possible.
- Keep ChatMod Mobile allowed to use background data.
- If the phone aggressively stops apps, allow ChatMod Mobile unrestricted battery usage in Android settings.

## Android Settings Copy

Use this in the app or support replies:

> ChatMod Mobile runs your moderation bot from this phone. For long streams, Android battery settings can pause the bot even while the stream is live. Allow unrestricted battery use for ChatMod Mobile only when you plan to host moderation from this device.

## Why We Do Not Hide This

Battery optimization is a real reliability risk for phone-hosted moderation. The app should explain the tradeoff plainly instead of pretending the bot can always run indefinitely in the background.

## References

- Android Developers: Optimize for Doze and App Standby
- Android Developers: Foreground services overview
- Android Developers: Background optimization
