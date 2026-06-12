# Android UX And Accessibility Source Gates

This document is the source-level UX and accessibility gate for ChatMod Mobile. It does not replace emulator, device, TalkBack, or Play pre-launch report testing, but it keeps the checked-in Android UI from drifting away from the product bar.

Run before release work:

```powershell
npm run android:ux:check
```

## Source Requirements

- Screen reader labels: meaningful action icons use `contentDescription`; grouped status surfaces use Compose semantics.
- Status surfaces: live status, sync status, bot health, queue count, and YouTube API warnings expose `stateDescription`.
- Minimum touch targets: dense queue, command, timer, backup, and live-control icon actions use a 48dp `minimumTouchTarget()` helper.
- Live workspace tabs: Queue, Feed, and Controls share a compact native tab surface so creators can move between triage, activity, and live actions without hunting through secondary navigation.
- Non-color-only status: warning/ready/offline states pair color with text and icons, such as Warning, CheckCircle, "Network offline", "Reconnecting", and "Needs attention".
- Text scaling: the dashboard avoids fixed `fontSize` overrides and leans on Material typography so Android font scaling can work.
- Production state coverage: primary dashboard surfaces keep real loading, empty, error, offline, reconnecting, and success states instead of static placeholder panels.
- High contrast: `ChatModTheme` includes high-contrast light and dark color schemes and the Settings tab exposes the high contrast toggle.
- Reduced motion: the dashboard state and Settings tab expose reduced motion as a persisted user preference.
- Destructive actions: account deletion, local data wipe, message delete, timeout, and hide/ban actions go through confirmation flows.
- Haptics: high-intent live and destructive controls provide tactile feedback.

## Manual QA Still Required

- Run TalkBack through onboarding, stream selection, queue actions, commands, timers, settings, account/privacy, support, and logs.
- Test Android font scaling at 130%, 160%, and 200% on a small phone.
- Check dark mode and high contrast mode on device.
- Confirm all high-pressure controls remain one-handed and tappable during a simulated live stream.
- Run a Play pre-launch report once a signed internal or closed-beta artifact exists.

## Free-Tier Posture

This gate uses only local source checks and does not require paid accessibility tooling. Device QA can be done with Android Studio, local emulators, physical test phones, and Google Play's standard pre-launch report after the app is uploaded.
