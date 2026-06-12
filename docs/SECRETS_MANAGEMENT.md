# Secrets Management

ChatMod Mobile should run on free-tier-friendly hosts without adding a paid secrets product. Use the deployment platform's built-in encrypted environment variables for beta and production, and keep `.env` files local-only.

## Required Production Secrets

| Secret | Purpose | Rotation posture |
| --- | --- | --- |
| `DATABASE_URL` | Managed PostgreSQL connection string | Rotate if leaked or when moving database providers. |
| `JWT_SECRET` | Signs short-lived ChatMod backend device sessions and OAuth state | Rotate with a release window because active sessions are invalidated. |
| `SECRET_ENCRYPTION_KEYS` | Encrypts stored Google OAuth tokens, Prisma-backed cloud backup configs, and other server-side secrets | Rotate by adding a new primary key while keeping old keys configured. |
| `ADMIN_API_KEY` | Optional key for backend `/admin/*` support routes | Keep unset unless admin support lookup is needed; rotate immediately if shared too widely. |
| `GOOGLE_OAUTH_CLIENT_ID` | YouTube OAuth client id | Rotate only through Google Cloud OAuth config changes. |
| `GOOGLE_OAUTH_CLIENT_SECRET` | YouTube OAuth client secret | Rotate if leaked or when Google Cloud credentials are recreated. |
| `GOOGLE_OAUTH_REDIRECT_URI` | OAuth callback URL | Treat as config, not a secret, but keep it deployment-controlled. |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64` | Google Play validation service account JSON | Rotate through Google Cloud IAM service account keys. |

## Encryption Key Format

`SECRET_ENCRYPTION_KEYS` is a comma-separated key ring:

```text
primary-key-id:base64url-32-byte-key,old-key-id:base64url-32-byte-key
```

The first key is the primary key used for new encrypted values. Older keys remain available for decryption until all rows have been re-encrypted or retired.

Generate a new key with Node.js:

```powershell
node -e "console.log('k202606:' + require('node:crypto').randomBytes(32).toString('base64url'))"
```

## Rotation Flow

1. Generate a new 32-byte key with a new key id.
2. Prepend it to `SECRET_ENCRYPTION_KEYS`.
3. Deploy the backend.
4. New OAuth/token writes and Prisma-backed cloud backup configs are encrypted with the new key id.
5. Keep old keys configured until a migration or reconnect flow has rewritten old encrypted rows.
6. Remove old keys only after confirming no encrypted values reference their key ids.

Do not rotate `JWT_SECRET` as the token-encryption rotation mechanism. `JWT_SECRET` signs sessions; `SECRET_ENCRYPTION_KEYS` encrypts stored provider tokens.

## Storage Rules

- Never commit real `.env` files, OAuth secrets, Play service account JSON, or generated encryption keys.
- Use platform secret/env storage for hosted beta and production.
- Keep local development secrets in a developer-local `.env` only.
- Store Play service account JSON as base64 in the environment variable, not as a checked-in file.
- Do not log raw access tokens, refresh tokens, JWTs, database URLs, or service account JSON.
- Run `npm run backend:env:check -- --json` before production starts to catch placeholders, local URLs, malformed key rings, and malformed Google config.

## Free-Tier Fit

No paid secrets manager is required for beta. Render, Vercel, Railway, Fly.io, Neon, GitHub Actions, and similar platforms include encrypted environment variable storage suitable for this stage. Upgrade to a dedicated secrets manager only when compliance, team access controls, or audit requirements demand it.
