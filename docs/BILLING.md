# Billing And Entitlements

ChatMod Mobile uses backend-verified entitlement snapshots. The Android app reads the current entitlement, and the backend is responsible for validating Google Play purchases before Pro or Creator limits are trusted.

For a zero-cost beta, ChatMod Mobile can run with local/starter entitlements and sideloaded APK builds. Google Play Billing becomes relevant only when the app is published through Google Play; Play Console currently requires a one-time developer registration fee, so it is not a pure free-tier dependency.

The first free-first pricing decision and paid-plan guardrails are documented in `docs/PRICING.md`.

Lifetime purchase is not offered for MVP, closed beta, or public v1. The Play Billing source queries subscriptions only: `chatmod_pro_monthly` and `chatmod_creator_monthly`. A one-time lifetime product should not be configured in Play Console or advertised until the pricing decision is reopened and the entitlement model is updated deliberately.

## Backend Endpoints

- `GET /entitlements/current` - current plan and feature limits.
- `GET /entitlements/google-play/config` - whether Google Play validation is configured.
- `POST /entitlements/google-play/validate` - validate a Google Play subscription purchase and update entitlement state.

## Required Environment

```powershell
GOOGLE_PLAY_PACKAGE_NAME=com.chatmod.mobile
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64=<base64 service account json>
```

The service account must have Android Publisher API access for the Play Console app.

## Plans

Starter:

- Custom bot identity.
- 1 channel profile.
- 3 command profiles/custom commands.
- 5 timed messages.
- Basic filters: blocked terms, link policy, caps threshold, and repeated-character threshold.
- Cloud backup enabled.

Pro:

- Unlimited command profiles/custom commands.
- Unlimited timed messages.
- Emergency mode.
- Advanced filters: regex, domain allow/block lists, emoji/mention/symbol thresholds, member bypass, and raid/new-chatter controls.
- OBS/browser overlay.
- 2 team seats, meaning creator plus one extra moderator.

Creator:

- Unlimited command profiles/custom commands.
- Unlimited timed messages.
- Multiple channel profiles.
- 5 team seats, meaning creator plus four extra moderators.
- OBS/browser overlay.
- AI suggestions with manual approval and a daily Creator limit.

Paid-plan command and timer limits are encoded as `null` in entitlement snapshots. Backend quota checks treat `null` as uncapped, and Android displays those limits as "Unlimited."

Plan-aware local history is encoded as `localHistoryLimit`. Starter exposes 120 local rows, Pro exposes 1,000 rows, and Creator exposes 2,000 rows in Android dashboard logs and viewer history while the Room source remains local-first.

Preset bundle import/export is encoded as `presetBundles`. Starter cannot export or import preset bundles; Pro and Creator can use authenticated backend JSON bundle routes and Android share/paste controls.

OBS browser-source access is encoded as `obsOverlay`. Starter can read safe configuration state, but only Pro and Creator can create/update overlay URLs. Public overlay tokens are stored as hashes, can be rotated, and expose sanitized stream-session state without requiring a paid overlay provider.

Team moderator access is encoded as `teamSeats`. Starter has one creator-only seat. Pro has two seats, and Creator has five seats. The backend treats `teamSeats - 1` as the number of extra inviteable moderators for the selected channel profile, counts invited and active seats, and excludes revoked members.

AI review-assistant access is encoded as `aiSuggestions` plus the numeric `aiSuggestionDailyLimit`. Starter and Pro currently receive `aiSuggestions: false` and a limit of `0`. Creator receives `aiSuggestions: true` and a 300-per-UTC-day moderation-suggestion limit; the backend returns usage counts and `resetAt` on successful suggestions, and returns `AI_SUGGESTION_LIMIT_REACHED` with `429` when the limit is exhausted. The same Creator AI flag gates deterministic after-stream chat summaries from synced session logs and FAQ reply suggestions from creator-provided knowledge-base rows.

## Subscription Status Rules

- `active` and `trialing` subscriptions receive their paid plan features while the paid period is valid.
- `past_due` subscriptions keep paid features during the current grace period, then fall back to Starter after expiry.
- `canceled` subscriptions keep paid features until `currentPeriodEndsAt`, then fall back to Starter after expiry.
- `expired` subscriptions do not grant paid features, even if the old product ID is still visible for support context.

## Current Status

Implemented:

- Backend entitlement snapshots.
- Backend plan-limit enforcement for creating channel profiles, custom commands, timed messages, and restoring settings backups.
- Backend advanced-filter entitlement enforcement for moderation evaluation and saved rule presets.
- Google Play configuration checks.
- Google Play subscription validation service.
- Grace, canceled, and expired entitlement handling with tests.
- Prisma-backed entitlement updates.
- Backend and Android `localHistoryLimit` entitlement for longer local history on Pro/Creator.
- Backend and Android `presetBundles` entitlement for Pro/Creator rule preset import/export.
- Backend `obsOverlay` entitlement for Pro/Creator OBS/browser overlays.
- Backend and Android `teamSeats` entitlement for Pro/Creator team invite, redeem, and revoke flows.
- Backend and Android `aiSuggestionDailyLimit` entitlement for Creator review-assistant usage caps.
- Android billing summary panel with Play product, restore, and demo validation controls.
- Android Google Play Billing client purchase flow using Billing Library 8.3.0 for product queries, purchase launch, purchase restore, backend validation, and client acknowledgement after backend validation succeeds.
- Subscription-only MVP decision: no Lifetime SKU is offered or advertised for closed beta/public v1.

Still required before launch:

- Verify backend validation with real Play Console credentials.
- Configure the Play Console subscription products and closed-testing track for `chatmod_pro_monthly` and `chatmod_creator_monthly`.
- Run real purchase, pending purchase, cancellation, expiry, and restore tests from a Play-installed build.
- Decide whether billing account disconnect/reconciliation needs extra product behavior beyond YouTube disconnect and account deletion.
