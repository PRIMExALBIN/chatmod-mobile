# First Pricing Model

Date: 2026-06-08

This is the first pricing decision for ChatMod Mobile. The model keeps beta validation free or free-tier-friendly, avoids paid infrastructure in the MVP path, and leaves paid plans behind Google Play Billing only when the app is ready for store launch.

## Decision

ChatMod Mobile starts with a free-first model:

- **Free beta:** sideloaded/internal Android builds, local/starter entitlements, and free-tier backend hosting.
- **Starter:** default free plan for one creator account and one primary channel profile.
- **Pro:** paid plan later for heavier moderation workflows, unlimited commands/timers, emergency-mode workflows, advanced filters, longer local history, OBS/browser overlays, and export/import convenience.
- **Creator:** paid plan later for multi-profile, team-moderator workflows, analytics, Discord alerts, cloud backup expansion, and AI-assisted review features.
- **Lifetime:** not offered for MVP, closed beta, or public v1. The first paid release stays subscription-only through Google Play so support, refunds, entitlement expiry, and restore behavior can be verified before any one-time license is considered.

## Free-Tier Infrastructure Posture

- Hosted beta backend: Render Free web service.
- Durable database: Neon Free Postgres.
- Static launch/docs site: Cloudflare Pages Free.
- CI: GitHub Actions.
- Optional cache: Upstash Redis Free or Render Key Value Free only when needed.
- Crash/support/feedback: first-party support-event storage by default.

Google Play publishing is the one known non-free launch path because Play Console requires a developer registration fee. Until store launch, the zero-cost path is local builds, sideloading, and private APK distribution.

## Starter Limits To Build Toward

These Starter limits are enforced by the backend where the resource is created or synced:

- 1 primary channel profile.
- 3 custom commands.
- 5 timed messages.
- Basic moderation filters: blocked terms, link policy, caps threshold, and repeated-character threshold.
- Current-stream local logs.
- Export/delete/account controls required by privacy commitments.

Advanced moderation filters are enforced as paid entitlement features on backend rule-preset save and moderation-evaluation paths. Advanced fields include regex patterns, domain allow/block lists, emoji/mention/symbol thresholds, member bypass, raid mode, and new-chatter burst controls.

Pro and Creator command/timer limits are implemented as uncapped entitlement features. The backend returns `null` for `commandProfiles` and `timedMessages` on active paid plans, which means no quota cap for those resources; Starter remains capped.

Longer local history is implemented as the numeric `localHistoryLimit` entitlement. Starter exposes 120 local Room rows, Pro exposes 1,000 rows, and Creator exposes 2,000 rows across dashboard logs and viewer history without adding a paid analytics warehouse.

Preset export/import is implemented as the Pro/Creator `presetBundles` entitlement using authenticated JSON bundles through the backend rule-preset API and Android share/paste controls. It stays free-tier friendly by storing presets in Postgres rows and avoiding paid object storage.

Discord alerts are implemented as a Pro/Creator feature using free Discord webhooks. Webhook URLs are encrypted on the backend, Android only receives safe configured/enabled status, and runtime alerts send moderation counts rather than chat text.

OBS/browser overlays are implemented as a Pro/Creator feature using the existing Fastify backend and stream-session audit tables. Public overlay URLs use hashed read-only tokens, expose sanitized counts/actions/runtime status, keep recent chat text off by default, and avoid paid overlay hosting or a realtime database.

Team moderator access is implemented as a Pro/Creator feature using profile-scoped invite codes, hashed invite storage, Android Settings controls, and backend seat enforcement from `teamSeats`. It avoids paid workspace tooling and keeps moderator access revocable per profile.

Creator multiple profiles are source-wired across backend profile limits, Android profile list/create/select, moderation presets, warning history, Discord alerts, analytics, team access, backups, command/timer editor storage, backend command/timer sync writes, and foreground runtime command/timer reads. Keep emulator/device QA and real account verification in the release checklist before public marketing.

## Paid Plan Guardrails

The Android source flow is implemented for Google Play product query, purchase launch, restore, backend validation, and acknowledgement. Paid features still must not be presented as production-ready until all of the following are true:

- Backend purchase validation is verified with real Play Console credentials.
- Purchase and restore behavior are tested from a Play-installed closed-testing build.
- Expired, canceled, grace-period, and refunded states fall back correctly.
- Pricing copy is shown before purchase and is consistent with the Play listing.

The checked-in Play Billing source queries only the subscription products `chatmod_pro_monthly` and `chatmod_creator_monthly`. Do not add a `chatmod_lifetime` one-time product, one-time Play product type, or lifetime marketing copy unless this pricing decision is revisited and the backend entitlement model is expanded intentionally.

## AI Feature Guardrail

AI moderation suggestions, FAQ replies, and after-stream chat summaries are Creator-only assistant features. The current implementation uses a free local heuristic provider, exposes confidence/reasoning for moderation suggestions, suggests FAQ replies only from creator-provided knowledge-base entries, summarizes only synced stream-session logs, supports false-positive correction in the review workflow, and enforces a Creator daily moderation-suggestion usage limit through `aiSuggestionDailyLimit` before any future paid model provider is considered. It must not silently automate destructive moderation decisions.

## Open Decisions Before Public Launch

- Final monthly price points.
- Whether Pro or Creator receives cloud backup first.
- Whether beta users get a migration discount.
- Whether the launch website advertises paid tiers before billing is live.
