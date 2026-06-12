# External Release Evidence

Most remaining ChatMod Mobile launch gates depend on systems we cannot fake locally: Google OAuth, a real YouTube Live stream, Play Console, hosted Render/Neon, and Android device or emulator behavior.

Use this evidence packet flow to collect proof without committing secrets or pretending a gate is complete.

## Source Gate

Print the required evidence plan:

```powershell
npm run release:evidence:check -- --print-required
```

Validate the template packet:

```powershell
npm run release:evidence:check
```

Validate a real sanitized evidence manifest:

```powershell
npm run release:evidence:check -- --manifest=release-evidence/evidence.json --require-complete
```

## Evidence Folder

Use a local ignored folder:

```text
release-evidence/
  evidence.json
  backend/
  google-youtube/
  android-device/
  play-console/
  creator-beta/
```

The template lives at `docs/release-evidence.template.json`. Copy it to `release-evidence/evidence.json` when collecting real proof.

## Secret Rules

Do not commit:

- OAuth authorization codes.
- OAuth access or refresh tokens.
- Play service account JSON.
- Raw `DATABASE_URL`, `JWT_SECRET`, or encryption keys.
- Private YouTube stream keys, unlisted stream URLs, or creator personal data.
- Full screenshots that show private emails, payment details, or tokens.

Allowed evidence should be sanitized: command output with secrets redacted, public URLs, request IDs, build names, pass/fail summaries, and cropped screenshots that prove state without leaking accounts.

## Completion Rule

Only mark an external checklist item complete when the evidence manifest has:

- `status: "complete"` for the matching gate.
- At least one artifact path for that gate.
- Required evidence notes that match the exact behavior being checked.
- No placeholder values such as `TODO`, `<deployed-backend>`, or `YYYY-MM-DD`.

The validator intentionally fails `--require-complete` until every external gate has real evidence.
