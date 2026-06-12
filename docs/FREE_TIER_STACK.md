# Free-Tier Stack Plan

Date checked: 2026-06-11

ChatMod Mobile should stay open-source/local-first by default and only use hosted services that have a useful free tier for prototypes, closed beta, and early creator validation.

## Current Best Free-Tier Recommendation

| Need | Recommended platform | Why this is the default |
| --- | --- | --- |
| Backend beta API | Render Free web service | Fits the existing Dockerized Fastify API, supports custom domains/TLS and health checks, and is enough for closed beta traffic. Official Render docs list Free web services, but they spin down after 15 idle minutes, restart on the next request, use an ephemeral filesystem, and share 750 free instance hours per workspace/month. |
| Durable Postgres | Neon Free | Postgres-native, persistent, serverless scale-to-zero, 100 CU-hours monthly per project, 0.5 GB storage per project, no credit card required, and no 30-day database expiry. |
| Static landing/docs site | Cloudflare Pages Free | Strong static hosting free tier with 500 builds/month, unlimited static requests, unlimited bandwidth, and the checked-in `launch-site/` folder can deploy with no build command. |
| CI | GitHub Actions | Included for public repos and 2,000 monthly minutes on GitHub Free private repos; keep Linux jobs short and avoid large artifacts. |
| Optional cache | Upstash Redis Free first; Render Key Value Free only for disposable state | Use only for non-source-of-truth rate-limit/cache state. Upstash Free currently lists 256 MB data size, 10 GB monthly bandwidth, and 500K monthly commands. Render Free Key Value is in-memory only and can lose state on restart. Current app can run without hosted Redis. |
| Crash/error visibility | First-party support events by default; optional Sentry Developer | Current backend support/API-error storage is enough for beta. Add Sentry only if single-developer free monitoring is worth the SDK surface. |

This stack avoids paid-only vendors for MVP while preserving a clean upgrade path: Render paid instance or another always-on container host later for backend uptime, Neon Launch when database usage exceeds Free, Cloudflare R2 only when backups/exports outgrow Postgres, and Google Play only at store launch.

## Decision Rules

- Keep the backend stateless on Render Free; never depend on local disk for uploads, SQLite, generated exports, or bot state.
- Use Neon Free, not Render Free Postgres, for beta source-of-truth data because Render's Free Postgres expires after 30 days.
- Keep the launch site static on Cloudflare Pages Free until the app genuinely needs an authenticated web dashboard.
- Do not add Redis to hosted beta unless request volume proves in-memory rate limiting is insufficient; if added, cache only disposable data.
- Do not add object storage until encrypted Postgres backups/exports become too large for Neon Free after retention pruning.
- Treat Google Play Console as the one unavoidable launch-channel cost, not as part of the zero-cost beta path.

## Default Build Choices

| Layer | Choice | Cost posture | Notes |
| --- | --- | --- | --- |
| Android app | Kotlin, Jetpack Compose, Room, DataStore | Free/open-source | No paid mobile UI framework or hosted mobile backend required. |
| Backend API | Node.js, Fastify, Prisma | Free/open-source | Runs locally or in any Node container host. |
| Primary database | PostgreSQL | Free/open-source | Local Docker for development; managed free-tier Postgres for hosted beta. |
| Cache/rate limits | Redis-compatible cache | Free/open-source locally; free-tier hosted option | Use local Redis in Docker, or a free-tier serverless Redis provider for hosted beta. |
| Secrets | Host encrypted environment variables | Included on most free-tier hosts | Use env secrets for `JWT_SECRET`, `SECRET_ENCRYPTION_KEYS`, OAuth, and Play credentials; no paid secrets manager is required for beta. |
| CI | GitHub Actions | Free for public repositories; limited free minutes for private repositories | Keep CI lean and Linux-first to stay inside included minutes. |
| Error tracking | Built-in support events and API error logs; optional Sentry Developer later | Free in current repo | Android crash markers upload through support events, so beta does not require a paid crash SDK. |
| Basic analytics | First-party opt-in events via backend support-event storage | Free in current repo | No paid analytics SDK; events stay small, authenticated, and export/delete compatible. |
| Cross-stream moderation analytics | Backend summaries from existing stream-session audit tables | Free in current repo | No paid analytics warehouse; Pro-style summaries reuse synced Postgres audit rows. |
| OBS/browser overlay | Backend-rendered HTML plus JSON polling from stream-session audit tables | Free in current repo | No paid overlay widget, hosted realtime database, or object storage is required. |
| Team moderator access | Backend invite/membership rows plus Android Settings controls | Free in current repo | No paid workspace product, Firebase, realtime database, or external identity team tool is required for beta. |
| Web dashboard | Static support/admin dashboard in `launch-site/admin.html` plus existing `/admin/support/*` API | Free in current repo | No paid helpdesk, dashboard builder, Firebase console, or separate frontend hosting is required for beta. |
| Beta feedback | First-party feedback events via backend support-event storage | Free in current repo | No paid feedback widget or product analytics suite is needed for closed beta. |
| Launch-site beta interest | Static form posting to backend support-event storage | Free in current repo | No Typeform, Airtable, or paid form backend is needed for early beta collection. |
| Data retention | Built-in dry-run-first Prisma pruning command | Free in current repo | Keeps support events, API errors, stream detail logs, and backup versions small before adding paid storage. |
| Object storage | Avoid until needed; Cloudflare R2 candidate | Free allowance available | Current backups fit as encrypted Postgres JSON envelopes. Use object storage only for larger exports/backups. |
| Hosting | Render Free web service for beta | Free-tier candidate | Must tolerate cold starts and monthly usage limits. Do not rely on ephemeral disk. |
| Billing | Google Play Billing subscriptions only when publishing through Play | SDK/API are available, but Play Console has a required one-time registration fee | For zero-cost beta, use sideload/internal APK builds and local starter entitlements. Lifetime purchase is not offered for MVP. |

## Recommended Free-Tier Hosted Beta

- Backend web service: Render Free web service using the root `render.yaml` blueprint and `backend/Dockerfile`.
- Postgres: Neon Free Postgres, because it is persistent, Postgres-native, and does not expire after 30 days.
- Static landing page or docs: Cloudflare Pages Free serving the checked-in `launch-site/` folder.
- Redis/rate limit cache: disable hosted Redis until traffic requires it; if needed, use Upstash Redis Free for disposable cache/rate-limit state.
- Object storage if backup/export blobs outgrow Postgres: Cloudflare R2 free allowance.
- CI: GitHub Actions with short Linux jobs and artifact retention kept low.
- Crash/error visibility: first-party support/API-error events first; optional Sentry Developer with sampling and no paid profiling add-ons.

## Provider Limits To Design Around

- Render Free web services spin down after idle time, have usage limits, and cannot persist local filesystem data. Treat the backend as stateless and store data in Postgres.
- Render Free Postgres expires after 30 days, so do not use it as the primary beta database.
- Neon Free has per-project compute, storage, and egress limits. Keep logs/backups small and add retention before beta.
- Upstash Free has memory, bandwidth, and command-count limits. Use it for short-lived rate-limit/cache state, not source-of-truth data.
- Render Free Key Value is in-memory only and can lose data on restart, so do not use it for anything that must survive failures.
- Vercel Hobby is not the default backend target because it is best suited to personal/non-commercial use and serverless functions, while ChatMod's backend is a normal Fastify service with Prisma and OAuth callback flows.
- GitHub Actions private repos have included monthly minutes/storage, but public repos and self-hosted runners avoid minute charges.
- Cloudflare Pages is ideal for static launch/docs pages, but the current backend should not be forced into an edge/serverless rewrite just to chase a free tier.
- Cloudflare R2 has a free storage/operation allowance, but object storage is not needed until exports/backups become large.
- Google Play Console requires a one-time registration fee. That is not a free tier, so the zero-cost path is sideloading or local APK distribution until store launch.

## Current Stack Audit

- Root `render.yaml` defines a Render Free Docker web service with `/health/ready`; secrets remain `sync: false` and must be set in Render's encrypted environment UI.
- CI builds the backend Docker image without pushing it, keeping the free GitHub Actions path honest.
- No paid cloud SDKs are required to run the repo locally.
- No Stripe dependency is installed. Billing code is Google Play validation oriented.
- Database, cache, CI, and backend hosting can all be run locally for free during development.
- Production-style secret storage uses platform environment secrets and a separate backend token-encryption key ring.
- Prisma-backed cloud backup configs use the same backend encryption key ring, so Neon stores encrypted envelopes instead of plaintext settings backups.
- Crash/error tracking uses the existing backend support-event and API-error stores by default.
- Basic analytics uses opt-in first-party events instead of a paid third-party SDK.
- OBS/browser overlays use the existing backend and stream-session audit rows instead of a paid overlay provider or realtime database.
- Team moderator access uses the existing backend and Postgres rows instead of a paid workspace or realtime database.
- Admin web dashboard uses the existing launch-site static host and `/admin/support/*` API instead of a paid support dashboard.
- Beta feedback uses first-party support-event storage instead of a paid feedback SDK.
- Launch-site beta interest uses the backend support-event path instead of a paid form provider.
- Retention pruning is source-controlled and dry-run-first, so Neon Free storage pressure can be handled without a paid scheduler, data warehouse, or object storage dependency.
- The production checklist should keep paid-only services out of MVP unless there is no practical free/open-source substitute.

## Sources

- Neon pricing and Free plan allowances: https://neon.com/pricing
- Neon plan docs: https://neon.com/docs/introduction/plans
- Upstash Redis pricing: https://upstash.com/pricing/redis
- Render Free web service spin-down docs: https://render.com/docs/free
- Render pricing: https://render.com/pricing
- Cloudflare Pages Free plan: https://pages.cloudflare.com/
- Cloudflare Pages limits: https://developers.cloudflare.com/pages/platform/limits/
- Vercel Hobby plan limits: https://vercel.com/docs/plans/hobby
- GitHub Actions billing/free usage: https://docs.github.com/en/billing/concepts/product-billing/github-actions
- Cloudflare developer platform pricing: https://www.cloudflare.com/en-in/plans/developer-platform-pricing/
- Sentry pricing: https://sentry.io/pricing/
- Google Play Console registration fee: https://support.google.com/googleplay/android-developer/answer/6112435
