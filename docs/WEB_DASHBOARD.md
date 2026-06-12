# Web Dashboard

ChatMod Mobile now includes a static support/admin web dashboard at `launch-site/admin.html`. It is designed for closed beta operations, not as a paid SaaS dependency.

## What It Does

The dashboard can:

- Check backend readiness with `GET /health/ready`.
- Look up a device support snapshot with `GET /admin/support/users?deviceId=...`.
- Review launch-site beta interest with `GET /admin/support/devices/launch-site-beta-interest`.
- Inspect recent support events and backend API errors for a device.
- Save audited manual entitlement adjustments through `POST /admin/support/entitlements/manual-adjust`.
- Save support ticket status, priority, tags, and notes through `POST /admin/support/tickets/metadata`.

## Free-Tier Posture

The dashboard is dependency-free static HTML, CSS, and JavaScript. It can ship with the existing `launch-site/` folder on Cloudflare Pages Free, GitHub Pages, or another static host with no build step.

It does not require:

- A paid helpdesk dashboard.
- A paid analytics workspace.
- Firebase.
- A separate admin frontend framework.
- Server-side rendering.

## Security Model

The backend only registers `/admin/*` routes when `ADMIN_API_KEY` is configured. The dashboard sends that key in the `x-admin-api-key` header.

The static dashboard:

- Does not include an admin key in source.
- Uses a password input for the key.
- Does not store the key in `localStorage` or `sessionStorage`.
- Stores only the backend origin in `sessionStorage` for convenience.
- Sends requests with `credentials: "omit"` so browser cookies are not used.

Production deployments must set `CORS_ORIGIN` to the static site origin before the dashboard can call the backend from a browser.

## Source Gate

Run:

```powershell
npm run launch-site:check
```

The source gate verifies that `admin.html` and `admin-dashboard.js` exist, that the page has accessible controls, that the script calls the intended admin routes, and that no durable browser storage is used for admin secrets.

`npm run production:check` also checks the dashboard docs and source posture.

## Keyboard And Accessibility

The dashboard is built from native HTML controls so beta support operators can use it without pointer-only interactions:

- A skip link moves keyboard users directly to the dashboard.
- Every input/select is paired with a visible label.
- Primary actions are real `button` elements.
- Lookup and save flows can be submitted from the keyboard.
- Focus states are visible through the shared `:focus-visible` style.
- Status messages use `role="status"` and `aria-live="polite"`.

## Manual Verification Still Required

Before public launch, verify the deployed static dashboard against the deployed backend:

- `GET /health/ready` succeeds from the browser.
- Device lookup works for a real beta device ID.
- `launch-site-beta-interest` lookup shows beta-interest submissions.
- Manual entitlement adjustment creates a support audit event.
- Ticket metadata writes are visible in the same device snapshot.
- The admin key is rotated if it was shared outside the support operator group.
