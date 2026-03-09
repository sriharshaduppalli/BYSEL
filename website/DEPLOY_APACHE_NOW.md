# Deploy Website To Apache Now

This project is configured for static export (`output: "export"`) so it can be hosted on Apache.

## 1. Build Static Site

Run from `website/`:

```bash
npm ci
npm run lint
npm run build
```

After build, deployable files are in `website/out/`.

## 2. Upload Correct Artifacts

Upload the **contents** of `out/` to your Apache document root for `www.byseltrader.com`.

Important:

- Do not upload source folders like `src/`, `app/`, or `website/` itself.
- Deploy `out/index.html` as document root `index.html`.
- Deploy route directories from `out/` (for example `out/features/index.html`, `out/pricing/index.html`).

## 3. Clean Old Conflicting Paths

On the server, remove stale folders/files that cause 403 responses:

- `features/`
- `markets/`
- `pricing/`
- `support/`
- `about/`
- `careers/`
- `blog/`
- `legal/`

Then upload the fresh `out/` content.

## 4. Quick Verification

Check these routes return `200` and show new text:

- `/` -> `Train your trading process before risking real capital.`
- `/features/` -> `Features built for process quality, not hype.`
- `/markets/` -> `Real-time context for better simulated decisions.`
- `/pricing/` -> `Plans for every stage of your trading journey.`
- `/support/` -> `Need help? We are here to keep your learning flow smooth.`

## 5. Cache Refresh

If old content still appears:

- Purge CDN/proxy cache (if any).
- Hard refresh browser cache.
