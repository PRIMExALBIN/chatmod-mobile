# Data Retention

ChatMod Mobile should stay free-tier friendly during beta by keeping high-volume logs small before adding paid object storage or analytics infrastructure.

## Default Policy

The backend retention command is dry-run by default and deletes only when `--apply` is passed.

Default windows:

- Support events: keep 90 days.
- API error records: keep 30 days.
- Stream detail logs: keep 60 days after a stream session has ended.
- Cloud settings backups: keep the latest 10 versions per channel profile.

Stream detail pruning deletes chat message logs, moderation action logs, and runtime event logs for ended sessions past the retention window. It keeps the compact `StreamSession` row so account history and support context can still show that a session existed.

## Commands

Build first when using the production runner:

```powershell
npm run backend:build
```

Dry run:

```powershell
npm run backend:retention:prune -- --json
```

Apply:

```powershell
npm run backend:retention:prune -- --apply
```

Local source runner for development:

```powershell
npm run backend:retention:prune:dev -- --dry-run
```

## Environment Knobs

- `SUPPORT_EVENT_RETENTION_DAYS`
- `API_ERROR_RETENTION_DAYS`
- `STREAM_LOG_RETENTION_DAYS`
- `BACKUP_VERSIONS_PER_PROFILE`

The CLI also accepts:

- `--support-event-days=90`
- `--api-error-days=30`
- `--stream-log-days=60`
- `--backup-versions-per-profile=10`
- `--json`
- `--apply`
- `--dry-run`

Retention day windows cannot be shorter than 7 days, and backup retention cannot be lower than 1 version per profile.

## Free-Tier Operations

Run a dry run before public beta and after any traffic spike. For hosted beta, run the apply command from a trusted machine or a short manual GitHub Actions job with the production `DATABASE_URL` secret. This avoids a paid scheduler while keeping Neon Free storage under control.

Record each applied run in the production checklist evidence log with:

- Date.
- Environment.
- Command used.
- Counts deleted.
- Any incident or support ticket that required extending retention before pruning.
