# BYSEL: Render → Google Cloud Run cutover

Play **4.0.10** still calls `https://bysel-backend.onrender.com`. Cloud Run does not replace that until a **4.0.11** AAB (or a custom domain) points at the new host.

Keep **bysel-db** on Render for the first cutover so testers keep the same passwords.

## What moves vs what stays

| Piece | On Render now | First cutover | Later |
|---|---|---|---|
| **bysel-backend** (API + WS) | Free Docker Oregon, 512MB, OOM | **Cloud Run** 2Gi, min 1, max 1, **us-west1** (near Oregon DB) | Scale max later |
| **bysel-db** (Postgres 18) | Oregon, Available | **Stay.** Use **External Database URL** | Cloud SQL + dump/restore |
| Users / wallets / refresh tokens | In bysel-db | Stay in that DB | Same |
| **AUTH_SECRET** | Render env | **Copy exact value** or every session dies | Same secret on Cloud SQL later |
| Groq / Gemini / Resend / Firebase / SMS | Render env | Copy into Cloud Run / Secret Manager | Same |
| Firebase Auth + FCM | Google project (already) | No move | — |
| Play app URL | Hardcoded onrender.com | New AAB **4.0.11** | Optional `api.` custom domain |
| In-memory quote/news/rate-limit caches | Process RAM | One Cloud Run instance only | Redis if you scale out |
| Render Free sleep / deploy freeze | Yes | Gone on Cloud Run | — |

## Secrets to copy (Render → bysel-backend → Environment)

**Must copy (login / mail / AI break without them):**

- `DATABASE_URL` — **External** URL from **bysel-db** (not Internal). Must allow connections from Cloud Run (add `0.0.0.0/0` on Render Postgres **or** the Cloud Run egress IPs).
- `AUTH_SECRET`
- `GROQ_API_KEY` (if set)
- `GEMINI_API_KEY` (if set)
- `RESEND_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SERVICE_ACCOUNT_JSON` (if set)
- `FAST2SMS_API_KEY` (if OTP SMS is used)

**Copy if present:** `SMTP_*`, `TWILIO_*`, `AUTH_ADMIN_TOKEN`, `ISM_*` keys.

**Set on Cloud Run (not secrets):**

```
DEBUG=false
PYTHON_VERSION=3.11.0
QUOTE_BATCH_SIZE=40
QUOTE_CACHE_TTL_OPEN=5
QUOTE_CACHE_MAX_ENTRIES=2500
HEATMAP_CACHE_TTL_OPEN=2
HEATMAP_QUOTE_MAX_AGE_OPEN=5
HEATMAP_QUOTE_REFRESH_BUDGET=60
MARKET_NEWS_BUDGET_SECONDS=4
NEWS_CACHE_MAX_SYMBOLS=80
RESEND_FROM_EMAIL=BYSEL <onboarding@resend.dev>
BYSEL_SUPPORT_EMAIL=bysel.trader@gmail.com
AUTH_PASSWORD_RESET_DEBUG_RESPONSE=false
ALLOW_USER_ID_HEADER_FALLBACK=false
```

## Cloud Run shape (first night)

- Region: **us-west1** (Oregon-ish; closer to `bysel-db`)
- Memory: **2 GiB** (512MB is why Render OOMs)
- CPU: 1
- Min instances: **1** (no cold start)
- Max instances: **1** (in-memory caches + WS)
- Timeout: **300s**
- CPU always allocated (needed for WS)
- Container: existing repo **root `Dockerfile`** (already uses `$PORT`)

Cloud Build in **europe-west1** can still build the image; deploy the service to **us-west1**.

## Console order

1. **bysel-db → Connect → External Database URL** — copy. In Postgres **Access**, allow external (or `0.0.0.0/0` for the first night only).
2. **bysel-backend → Environment** — screenshot/copy every key. Do not paste secrets into git.
3. GCP: enable **Cloud Run**, **Artifact Registry**, **Cloud Build** (Build history already exists).
4. Create service `bysel-backend` in **us-west1**, 2Gi, min=1, max=1, paste env vars.
5. Hit `https://<service>-<hash>.<region>.run.app/health` — expect **200**.
6. Hit `/auth/login` with a known tester account.
7. Build open AAB **4.0.11** with:

```
MARKET_REST_URL=https://<service>-<hash>.us-west1.run.app/
MARKET_WS_URL=wss://<service>-<hash>.us-west1.run.app/ws/quotes
```

8. Upload 4.0.11 to **Open testing** only. Leave Production Inactive.
9. Leave Render web service up until 4.0.11 is live, then you can suspend it. **Do not delete bysel-db.**

## Do not move yet

- Play listing / package name
- Firebase `google-services.json`
- Render disk / one-off jobs (unused)
- Local `bysel.db` / AABs / embedding cache
