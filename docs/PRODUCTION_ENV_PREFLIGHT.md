# Production Environment Preflight

Run this before a Render/Neon beta deploy or any production restart that changes secrets.

The preflight command validates the environment without opening the backend port or contacting Google, Render, Neon, or Play Console.

## Command

Build first when using the production runner:

```powershell
npm run backend:build
```

Then run:

```powershell
npm run backend:env:check
```

For machine-readable output:

```powershell
npm run backend:env:check -- --json
```

## Strict Production Checks

By default the command expects a real production-ready environment:

- `NODE_ENV=production`
- Non-local `DATABASE_URL`
- HTTPS `CORS_ORIGIN`
- Strong non-placeholder `JWT_SECRET`
- Valid `SECRET_ENCRYPTION_KEYS` key ring
- Valid retention windows
- Google OAuth client id, secret, and HTTPS `/youtube/oauth/callback` redirect URI
- Google Play package name and base64 service-account JSON shape
- Android minimum/latest version metadata

It also warns when optional support admin routes are disabled by leaving `ADMIN_API_KEY` unset.

## Prototype Allow Flags

Use these only for local or zero-cost prototype checks, not for production release evidence:

```powershell
npm run backend:env:check -- --allow-missing-google-oauth --allow-missing-google-play
```

Local URL checks can also be relaxed for a local production-mode rehearsal:

```powershell
npm run backend:env:check -- --allow-local-origins --allow-missing-google-oauth --allow-missing-google-play
```

## Free-Tier Fit

This avoids a paid secrets manager during beta. Render, Neon, and GitHub Actions already provide encrypted environment variable storage; the preflight command makes those built-in free-tier secret stores safer to use.
