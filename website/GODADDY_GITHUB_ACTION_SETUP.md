# GoDaddy + GitHub Actions Direct Deploy

This repository supports direct website deployment to GoDaddy hosting through GitHub Actions.

Workflow file:

- `.github/workflows/website-godaddy-deploy.yml`

## 1. Add GitHub Secrets

In GitHub repository settings, add these Actions secrets:

- `GODADDY_FTP_SERVER`
  - Example: `ftp.byseltrader.com` or your hosting FTP host from cPanel.
- `GODADDY_FTP_USERNAME`
  - Your GoDaddy/cPanel FTP username.
- `GODADDY_FTP_PASSWORD`
  - Your FTP password.
- `GODADDY_FTP_TARGET_DIR`
  - Absolute or relative FTP path for the live site root.
  - Typical cPanel value: `/public_html/`

## 2. Run First Deploy Manually

1. GitHub -> `Actions` -> `website-godaddy-deploy`.
2. Click `Run workflow`.
3. For first cleanup deploy, set `clean_slate=true` if you are sure `GODADDY_FTP_TARGET_DIR` is correct.
4. Run.

## 3. Automatic Deploys

After setup, pushes to `main` that touch `website/**` will auto-deploy.

## 4. Verification

After workflow success, validate:

- `https://www.byseltrader.com/`
- `https://www.byseltrader.com/features/`
- `https://www.byseltrader.com/pricing/`

Expected homepage marker:

- `Train your trading process before risking real capital.`

## 5. SSL Setup In cPanel (Required)

If browser or tools show certificate trust errors, issue a trusted cert in cPanel:

1. Open `cPanel Admin` for `byseltrader.com`.
2. Go to `ACME Automation` (or `SSL/TLS Status` and run `AutoSSL`).
3. Issue/renew certs for both:
  - `byseltrader.com`
  - `www.byseltrader.com`
4. Confirm cert is installed for Apache virtual host.

Notes:

- A self-signed cert (issuer equals your own domain) will still show HTTPS warnings.
- DNS for root and `www` must point to your hosting IP before issuance.
- The deploy workflow writes `.htaccess` with an ACME challenge bypass so renewals keep working.

## Safety Notes

- `clean_slate=true` deletes files in the target directory before upload.
- Use it only when target path points to the correct site root.
- Keep `clean_slate=false` for normal incremental updates.
