# Local Database Setup

ChatMod Mobile uses PostgreSQL through Prisma for production-style backend persistence.

## Start Services

```powershell
docker compose up -d
```

## Apply Migrations In Development

```powershell
$env:DATABASE_URL='postgresql://chatmod:chatmod@localhost:5432/chatmod'
npm --workspace backend run prisma:migrate
```

The repo includes checked-in Prisma migrations under `backend/prisma/migrations`.

Android Room migrations live in `ChatModDatabase`. Version 6 adds moderation action metadata so active rule preset id/name/revision can be kept in local logs and synced later through the pending cloud-sync queue.

## Apply Migrations In Deployment

```powershell
$env:DATABASE_URL='<production postgres url>'
npm run backend:db:deploy
```

Use `migrate deploy` for production and beta environments. Do not use `prisma db push` in production.

## Validate Schema

```powershell
$env:DATABASE_URL='postgresql://chatmod:chatmod@localhost:5432/chatmod'
npm run backend:db:validate
```

## Production Notes

- Use managed PostgreSQL.
- Run migrations from CI or a controlled release job.
- Do not use `prisma db push` in production.
- Keep `DATABASE_URL`, `JWT_SECRET`, `SECRET_ENCRYPTION_KEYS`, `CORS_ORIGIN`, OAuth credentials, and Play service account material in the deployment secret manager.
- Rotate OAuth/provider token encryption with the `SECRET_ENCRYPTION_KEYS` key-ring flow in `docs/SECRETS_MANAGEMENT.md`.
