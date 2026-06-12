# Deployment Smoke Tests

Use the backend smoke test after a local production-style start, staging deploy, or Render Free beta deploy. It uses only public or intentionally unauthenticated routes, so it does not need admin keys, Google OAuth credentials, or paid infrastructure.

## What It Checks

- `GET /health` returns the ChatMod backend service status.
- `GET /health/ready` returns dependency readiness.
- `GET /app/compatibility` returns Android version policy.
- `GET /entitlements/current` rejects missing auth with `401` and matching `x-request-id`.
- `POST /feedback/beta-interest` rejects an invalid public beta payload with `400` and matching `x-request-id`.

## Local Smoke

Start the backend, then run:

```powershell
npm run backend:smoke -- --base-url=http://127.0.0.1:4100
```

Local development can allow a missing database if the backend was started without `DATABASE_URL`:

```powershell
npm run backend:smoke -- --base-url=http://127.0.0.1:4100 --allow-missing-database
```

## Hosted Beta Smoke

After deploying to Render with Neon configured, require database readiness:

```powershell
npm run backend:smoke -- --base-url=https://your-render-service.onrender.com --require-database
```

The same command can also be driven through environment variables:

```powershell
$env:CHATMOD_SMOKE_BASE_URL="https://your-render-service.onrender.com"
$env:CHATMOD_SMOKE_REQUIRE_DATABASE="true"
npm run backend:smoke
```

## Options

- `--base-url=https://...`
- `--require-database`
- `--allow-missing-database`
- `--timeout-ms=8000`

The smoke test is intentionally small. It proves deployment shape, readiness, request ID support, and public validation without mutating production data.

## CI Coverage

The free GitHub Actions backend job runs this smoke test twice:

- Against the compiled Node backend on a local port.
- Against the built `chatmod-backend:ci` Docker image on a local port.

Both CI smoke checks allow a missing database because they are source and container boot checks. Hosted beta smoke should use `--require-database` after Render and Neon are connected.
