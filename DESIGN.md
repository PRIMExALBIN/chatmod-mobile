# Design

## Style

ChatMod Mobile should feel like a live control room in your pocket: crisp cobalt instruments, bright signal states, quiet surfaces, and expressive transitions that confirm what the bot is doing. The visual inspiration is the latest Gemini app direction and Google's Material 3 Expressive refresh, translated into a creator operations product instead of a general assistant.

The product register is task-first. The UI can be vivid, but moderation work stays readable and calm.

## Color

Color strategy: restrained product palette with expressive state color. Brand color carries identity; surfaces stay clean.

```css
:root {
  --cm-bg: oklch(1.000 0.000 0);
  --cm-surface: oklch(0.969 0.012 230);
  --cm-surface-strong: oklch(0.925 0.020 230);
  --cm-ink: oklch(0.175 0.025 235);
  --cm-muted: oklch(0.465 0.025 235);
  --cm-primary: oklch(0.570 0.145 230);
  --cm-primary-strong: oklch(0.460 0.160 232);
  --cm-accent: oklch(0.700 0.160 160);
  --cm-live: oklch(0.680 0.145 148);
  --cm-warn: oklch(0.760 0.145 82);
  --cm-danger: oklch(0.620 0.170 28);
  --cm-info: oklch(0.650 0.120 250);
  --cm-focus: oklch(0.710 0.160 210);
}
```

Dark mode should keep chroma deliberate, not drenched:

```css
@media (prefers-color-scheme: dark) {
  :root {
    --cm-bg: oklch(0.075 0.000 0);
    --cm-surface: oklch(0.140 0.015 235);
    --cm-surface-strong: oklch(0.205 0.020 235);
    --cm-ink: oklch(0.930 0.010 230);
    --cm-muted: oklch(0.680 0.020 230);
    --cm-primary: oklch(0.690 0.140 230);
    --cm-primary-strong: oklch(0.780 0.115 226);
    --cm-accent: oklch(0.760 0.145 160);
    --cm-live: oklch(0.740 0.130 148);
    --cm-warn: oklch(0.790 0.130 82);
    --cm-danger: oklch(0.700 0.145 28);
    --cm-info: oklch(0.750 0.110 250);
    --cm-focus: oklch(0.760 0.150 210);
  }
}
```

## Typography

Use a single strong sans family. Android uses the platform Material typography scale with tighter hierarchy, not display-font theatrics. Labels, counters, and queue rows should be compact. Hero-scale type belongs nowhere inside the app shell.

## Layout

The first screen is the usable app dashboard, not a landing page. It shows:

- Live status and current YouTube stream connection.
- Foreground bot control with a clear running/stopped state.
- Moderation queue and recent action log.
- Rule profile health.
- Quick access to commands, timers, filters, and settings.

Mobile layout uses a top status band, segmented navigation, and dense panels. Desktop/admin surfaces can use a left nav and data tables.

## Components

- Status bands for live, warning, failed, and syncing states.
- Icon buttons for quick moderation actions, with text only where the command is ambiguous.
- Segmented controls for mode switches.
- Toggles for binary rule settings.
- Sliders or numeric steppers for thresholds.
- Tabs for command, timer, filter, and log views.
- Skeleton rows for loading messages.
- Empty states that explain the next setup action.

Cards are allowed for repeated items and modals only. Do not nest cards. Page sections are full-width surfaces or unframed layouts.

## Motion

Motion should feel Gemini-inspired and Material 3 Expressive, but it must communicate state. Use short transitions for:

- Bot start and stop confirmation.
- New chat message arrival.
- Queue item resolved.
- Sync or entitlement state changes.

Respect reduced motion. Avoid decorative page-load choreography.
