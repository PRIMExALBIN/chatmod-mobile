# Backend Deployment

ChatMod Mobile's backend can run anywhere that supports a Node.js container and managed PostgreSQL. The early deployment target should stay free-tier friendly: stateless backend, managed Postgres, optional Redis, no paid object storage unless backups/exports outgrow Postgres. See `docs/FREE_TIER_STACK.md` for the current free-tier plan and provider limits.

The public launch site and support/admin web dashboard are static under `launch-site/` and can deploy separately on Cloudflare Pages Free, GitHub Pages, or another static host with no build command. See `docs/WEB_DASHBOARD.md` for the admin dashboard source gate and security model.

Before deploying the static site, run:

```powershell
npm run launch-site:check
```

Before preparing the Play Store listing or Data safety form, run:

```powershell
npm run store:check
```

## Free-Tier Beta Path

Use this path before paying for always-on hosting:

1. Create a Neon Free Postgres project and copy the pooled connection string into `DATABASE_URL`.
2. Create a Render Blueprint from the repo root `render.yaml`.
3. Keep the backend service on Render's Free instance type for beta testing.
4. Set the `sync: false` secrets in Render's encrypted environment UI.
5. Run `npm run backend:db:deploy` from CI or a trusted machine before the first production start and after every Prisma migration.

Do not create a Render Free Postgres database for beta source-of-truth data; Render Free Postgres expires after 30 days. The `render.yaml` blueprint intentionally uses an external `DATABASE_URL`.

Before creating or updating the hosted service, run the source-level free-tier readiness check:

```powershell
npm run production:check
```

This does not contact Render, Neon, Google, or Discord. It verifies that the checked-in blueprint, environment example, Dockerfile, OpenAPI contract, Prisma migrations, release docs, and CI workflow still match the intended free-tier deployment posture.

Before device beta, print the Android device QA plan and then run it against a real phone or emulator:

```powershell
npm run android:device:qa -- --print-plan
npm run android:device:qa -- --install --launch
```

Before public beta, run a retention dry run so high-volume support, API error, stream detail, and backup rows cannot quietly outgrow the free Postgres tier:

```powershell
npm run backend:build
npm run backend:retention:prune -- --json
```

Apply only after reviewing the dry-run counts:

```powershell
npm run backend:retention:prune -- --apply
```

The retention policy and environment knobs are documented in `docs/DATA_RETENTION.md`.

Before starting the hosted backend with production secrets, run the environment preflight:

```powershell
npm run backend:build
npm run backend:env:check -- --json
```

The preflight policy is documented in `docs/PRODUCTION_ENV_PREFLIGHT.md`.

After a backend deploy, run the external smoke test:

```powershell
npm run backend:smoke -- --base-url=https://your-render-service.onrender.com --require-database
```

The smoke test is documented in `docs/DEPLOYMENT_SMOKE_TESTS.md`.

## Required Environment

```powershell
NODE_ENV=production
PORT=4100
HOST=0.0.0.0
DATABASE_URL=<managed postgres url>
CORS_ORIGIN=<app or admin origin>
JWT_SECRET=<long random secret>
SECRET_ENCRYPTION_KEYS=<key-id:base64url-32-byte-key>
ADMIN_API_KEY=<optional 32+ char support admin key>
JWT_ISSUER=chatmod-mobile
JWT_AUDIENCE=chatmod-mobile
GOOGLE_OAUTH_CLIENT_ID=<google oauth client id>
GOOGLE_OAUTH_CLIENT_SECRET=<google oauth client secret>
GOOGLE_OAUTH_REDIRECT_URI=<https backend url>/youtube/oauth/callback
GOOGLE_PLAY_PACKAGE_NAME=com.chatmod.mobile
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64=<base64 json>
```

## Build Image

```powershell
docker build -f backend/Dockerfile -t chatmod-backend .
```

CI also builds this image without pushing it so the free-tier deployment path is checked on every pull request.

Local Docker verification requires Docker Desktop or another Docker-compatible engine on PATH.

## Run Migrations

Run migrations as a release step before starting new application instances:

```powershell
npm run backend:db:deploy
```

Prisma migration drift checks against a migration directory require a shadow database URL. Use a separate disposable Postgres database for that check in CI.

## Health Check

```http
GET /health
```

Expected response:

```json
{
  "status": "ok",
  "service": "chatmod-mobile-api"
}
```

Readiness check for deployment probes:

```http
GET /health/ready
```

In production this verifies database connectivity and returns `503` when the database is unavailable.

## Release Gates

- `npm run production:check`
- `npm run test`
- `npm run build`
- `npm run backend:db:validate`
- `npm run backend:env:check -- --json`
- `npm run android:wrapper:check`
- `npm run android:data:check`
- `npm run android:di:check`
- `npm run android:ux:check`
- `npm run android:device:qa -- --print-plan`
- `npm run launch-site:check`
- `npm run store:check`
- `npm audit --audit-level=moderate`
- container image build
- migration deploy against staging
- `npm run backend:smoke -- --base-url=<deployed backend> --require-database`
- confirm 4xx/5xx API responses include `x-request-id` for support correlation
- if `ADMIN_API_KEY` is enabled, verify `launch-site/admin.html` can call `/health/ready` and `/admin/support/users` from the deployed static origin

## Free-Tier Guardrails

- Do not store production data on ephemeral container disks.
- Do not use Render Free Postgres as the durable beta database because it expires after 30 days.
- Keep logs, exports, and backups small enough for a free Postgres beta until object storage is genuinely needed.
- Run retention dry runs before beta launches and after traffic spikes.
- Keep CI jobs short and artifact retention low.
- Treat Google Play publishing as a launch cost, not a free-tier dependency.

## Production Guardrails

- `NODE_ENV=production` requires `DATABASE_URL`, `CORS_ORIGIN`, a non-local `JWT_SECRET` of at least 32 characters, and `SECRET_ENCRYPTION_KEYS`.
- Production CORS is restricted to `CORS_ORIGIN`; development and test modes keep permissive local CORS.
- The API sets baseline security headers without adding a paid service dependency.
- OAuth/provider tokens are encrypted with `SECRET_ENCRYPTION_KEYS`, not `JWT_SECRET`; see `docs/SECRETS_MANAGEMENT.md` for rotation.
