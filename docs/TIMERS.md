# Timers Guide

Timers are scheduled chat messages the bot can send during an active livestream.

## Timer Fields

- Name.
- Message.
- Interval in minutes.
- Minimum chat activity threshold.
- Optional stream-relative quiet window.
- Enabled or disabled state.
- Last sent timestamp.

## Send Rules

A timer is due only when:

- It is enabled.
- Emergency mode is off.
- The current stream minute is outside the timer's quiet window.
- Enough chat messages have arrived since the last timer.
- The configured interval has passed since the timer was last sent.

When multiple timers are due at the same time, the runtime randomly selects one timer to send during that poll. This prevents stacked promo messages from hitting chat all at once while still allowing the remaining due timers to rotate in after new chat activity and their next send window.

Quiet windows are relative to the current stream start. For example, a timer with quiet start `0` and resume `10` stays enabled but will not send during the first 10 minutes of each stream.

## Production Notes

Timers must share the bot message rate limiter with commands and moderation auto-replies. When a timer sends successfully, the app should mark it sent, record the event locally, and sync useful diagnostics to the backend when support telemetry is enabled.

## Current App Status

The Android dashboard now has create, edit, delete, interval, minimum chat activity, quiet-window, enabled-state, pause/resume-all controls, validation UI, Room persistence wiring, local-first sync behavior, authenticated HTTP sync against the backend timer API, settings backup/restore source wiring, and shared bot-message rate limiting in the coordinator. The foreground runtime loads enabled Room timers, tracks chat activity across polls, randomly rotates one due timer per poll, skips timers inside their stream-relative quiet window, marks sent timers with persisted timestamps, and suppresses scheduled timer sends while emergency mode is active. Timer sync uses `PUT /timers/{id}` so the phone can keep a stable local ID across create, edit, delete, pause/resume, backup, and restore.

The next production step is verifying timer sync from an emulator/phone against a running backend, then verifying live sends against a YouTube test stream.
