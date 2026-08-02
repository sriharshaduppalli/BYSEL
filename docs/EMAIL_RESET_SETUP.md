# BYSEL password-reset email (Resend / SMTP on Render)

Password reset emails are sent by the backend when a user taps **Forgot password?** in the app.

## Preferred: Resend (no SMTP server needed)

1. Create a free account at [https://resend.com](https://resend.com).
2. Create an API key (**API Keys** → **Create API Key**).
3. Until you verify a custom domain, send from Resend’s test sender:
   - `BYSEL <onboarding@resend.dev>`
   - Resend only delivers to the **email address of your Resend account** in this mode.
4. After you verify `byseltrader.com` (or your domain) in Resend → **Domains**, set:
   - `RESEND_FROM_EMAIL=BYSEL <noreply@your-verified-domain.com>`
   - Then resets can go to any registered user email.

### Render Dashboard

Open **bysel-backend** → **Environment** → add:

| Key | Value |
|---|---|
| `RESEND_API_KEY` | `re_...` (secret) |
| `RESEND_FROM_EMAIL` | `BYSEL <onboarding@resend.dev>` (or your verified domain) |
| `BYSEL_SUPPORT_EMAIL` | `bysel.trader@gmail.com` |
| `AUTH_PASSWORD_RESET_DEBUG_RESPONSE` | `false` |

Save → Render redeploys. No SMTP vars required when Resend is set.

### Verify

```bash
curl -s -X POST https://bysel-backend.onrender.com/auth/password-reset/request \
  -H "Content-Type: application/json" \
  -d "{\"identifier\":\"your-registered-email@example.com\"}"
```

Expected when live:

```json
{"status":"ok","message":"If an account exists, you will receive a password reset code shortly.","delivery":"email", ...}
```

If still `"delivery":"support"`, `RESEND_API_KEY` is missing on the running service (or an old deploy without the Resend gate fix).

In the app: Login → **Forgot password?** → enter email → check inbox for the code → set new password.

## Optional: SMTP fallback

Use any provider (Gmail app password, SendGrid SMTP, Amazon SES, etc.):

| Key | Example |
|---|---|
| `SMTP_HOST` | `smtp.gmail.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | your SMTP user |
| `SMTP_PASSWORD` | SMTP password / app password |
| `SMTP_FROM_EMAIL` | `BYSEL <noreply@byseltrader.com>` |
| `SMTP_USE_TLS` | `true` |

Resend is tried first; SMTP is used only if Resend is unset or fails.

## Code notes

- Gate: `_password_reset_email_configured()` is true when **either** `RESEND_API_KEY` **or** SMTP host is set.
- Older builds only checked SMTP, so Resend-only configs returned `delivery: support`. That is fixed in current `backend/app/routes/auth.py`.
