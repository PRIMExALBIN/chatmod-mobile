# Device Background Restriction Notes

Android behavior varies by manufacturer. ChatMod Mobile should treat this as a product support issue, not as a mystery bug.

## Baseline Behavior

On standard Android behavior, the live bot should run as a foreground service with a visible notification. Non-live queue draining should use WorkManager with network and battery constraints.

## Known Risk Areas

- Battery Saver may delay polling, network callbacks, or queued sync.
- Manufacturer battery managers can stop foreground services more aggressively than stock Android.
- Background data restrictions can prevent cloud sync and diagnostics.
- Removing the app from recent apps can stop work on some devices.
- A reboot will require the creator to re-open the app before a new live bot session unless a future release adds a boot recovery flow.
- If Android kills runtime work but leaves ChatMod's saved active runtime state, opening the app asks the foreground service to recover that saved stream session.
- The Quick Settings tile can stop an active saved runtime, or start the last selected stream through the same foreground service path as the dashboard.

## Support Triage

Ask the creator:

- Device model and Android version.
- Whether Battery Saver was enabled.
- Whether the ChatMod foreground notification was visible.
- Whether ChatMod Mobile has unrestricted battery usage.
- Whether background data is restricted for ChatMod Mobile.
- Whether the phone was locked, charging, or low battery when the bot stopped.

## Product Rules

- Never promise 24/7 background execution from a phone.
- Prefer visible foreground work for live moderation.
- Prefer WorkManager for deferred non-live sync.
- Use direct battery optimization exemption prompts only with clear context and only if the release has reviewed Google Play policy.
- Keep the foreground notification health text current for waiting-for-network, reconnecting, YouTube backoff, emergency mode, and link lockdown states.
- Surface a clear recovery state when saved runtime context exists, so the creator knows whether the bot service was recovered or needs a manual restart.
- Keep the Quick Settings tile conservative: it should only toggle the normal foreground service and should not create a hidden background runtime.
