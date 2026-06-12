# ChatMod Mobile Launch Site

This is a static launch page and support/admin dashboard for ChatMod Mobile. It is intentionally dependency-free so it can be deployed on a free static host such as Cloudflare Pages Free, GitHub Pages, or Vercel Hobby.

## Local Preview

Open `index.html` in a browser, or run a simple static server from this folder:

```powershell
python -m http.server 4173
```

Validate the checked-in static site before deploy:

```powershell
npm run launch-site:check
```

## Deployment

- Build command: none.
- Output directory: `launch-site`.
- Suggested free host: Cloudflare Pages Free.
- Included Cloudflare config: `_headers` (security headers) and `_redirects`.
- Configure the backend `CORS_ORIGIN` to the launch-site origin.
- Point the beta form at the backend with either a same-origin proxy for `/feedback/beta-interest` or a small inline deploy config:

```html
<script>
  window.CHATMOD_BETA_API_URL = "https://your-render-service.onrender.com/feedback/beta-interest";
</script>
```

### Cloudflare Pages Setup

1. Go to Cloudflare Dashboard → Pages → Create a project.
2. Connect your Git repository.
3. Set build command: (leave empty).
4. Set build output directory: `launch-site`.
5. Deploy.

The included `_headers` file automatically sets security headers (CSP, HSTS, etc.)
The included `_redirects` file can proxy `/api/*` to your Render backend if needed.

## Notes

- The beta form posts to `POST /feedback/beta-interest` and stores beta interest through the backend support-event path.
- `admin.html` is the static support/admin dashboard. It calls `/admin/support/*` only when `ADMIN_API_KEY` is configured on the backend and entered by the operator at runtime.
- The dashboard stores only the backend origin in `sessionStorage`; it does not store the admin key.
- Privacy and terms pages are beta summaries; publish reviewed legal copy at the same URLs before public store launch.
