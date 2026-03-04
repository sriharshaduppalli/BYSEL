# BYSEL – Stock Trading Trial App

BYSEL is a **unique, stylish, and user‑friendly stock trading trial application** built with Kotlin, Jetpack Compose, and FastAPI backend.  
It demonstrates modern trading features with **mock data and paper trading only** – no real brokerage integration.

---

## ✨ Features

- 📈 **Watchlist** – Track favorite stocks with mock quotes
- 💼 **Portfolio** – Manage holdings, view average price, and monitor paper trading PnL
- 🔔 **Alerts** – Set price thresholds and receive in‑app notifications
- 🎨 **Stylish UI** – Dark mode, animated transitions, elegant cards
- ⚡ **Fast access** – Optimized backend and responsive interface
- 🛡 **Secure trial** – No personal data collected, mock data only

---

## 📂 Project Structure

---

## 🔐 Backend Auth Security Controls

Recent backend improvements include refresh-token rotation, session revocation APIs, rate limiting, and protected debug observability.

Access tokens now include versioned claims (`ver`) and unique IDs (`jti`). Security events such as `logout-all` and refresh-token misuse detection can invalidate outstanding access tokens immediately using server-side `token_version` checks.

### Auth/session endpoints

- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout` (current device/session)
- `POST /auth/logout-all` (all devices)
- `GET /auth/sessions` (list active sessions)
- `DELETE /auth/sessions/{session_id}` (revoke one session)

### Rate-limit environment variables

- `LOGIN_RATE_LIMIT_ATTEMPTS` (default `6`)
- `LOGIN_RATE_LIMIT_WINDOW_SECONDS` (default `60`)
- `REFRESH_RATE_LIMIT_ATTEMPTS` (default `12`)
- `REFRESH_RATE_LIMIT_WINDOW_SECONDS` (default `60`)

### Soft login lockout variables

- `LOGIN_LOCKOUT_FAILURES` (default `5`)
- `LOGIN_LOCKOUT_WINDOW_SECONDS` (default `300`)
- `LOGIN_LOCKOUT_DURATION_SECONDS` (default `300`)

### Debug auth observability (guarded)

- `AUTH_DEBUG_ENDPOINTS_ENABLED=true` to enable debug endpoints
- `AUTH_DEBUG_TOKEN=<secret>` optional token required via `X-Debug-Token`

Debug endpoints:

- `GET /auth/debug/rate-limits` (returns bucket summaries + auth metrics)
- `POST /auth/debug/rate-limits/reset` (resets in-memory buckets and metrics)

Use debug endpoints only in controlled environments (local/dev/staging).

### Quick smoke test (curl)

Base URL example: `http://localhost:8000`

1) Register

```bash
curl -X POST http://localhost:8000/auth/register \
	-H "Content-Type: application/json" \
	-d '{"username":"demo_user","email":"demo@example.com","password":"demo1234"}'
```

2) Login

```bash
curl -X POST http://localhost:8000/auth/login \
	-H "Content-Type: application/json" \
	-d '{"username":"demo_user","password":"demo1234"}'
```

Copy `access_token` and `refresh_token` from the response.

3) Refresh token

```bash
curl -X POST http://localhost:8000/auth/refresh \
	-H "Content-Type: application/json" \
	-d '{"refreshToken":"<REFRESH_TOKEN>"}'
```

4) List active sessions

```bash
curl -X GET http://localhost:8000/auth/sessions \
	-H "Authorization: Bearer <ACCESS_TOKEN>"
```

5) Revoke one session

```bash
curl -X DELETE http://localhost:8000/auth/sessions/<SESSION_ID> \
	-H "Authorization: Bearer <ACCESS_TOKEN>"
```

6) Logout current device/session

```bash
curl -X POST http://localhost:8000/auth/logout \
	-H "Authorization: Bearer <ACCESS_TOKEN>" \
	-H "Content-Type: application/json" \
	-d '{"refreshToken":"<REFRESH_TOKEN>"}'
```

7) Logout all devices

```bash
curl -X POST http://localhost:8000/auth/logout-all \
	-H "Authorization: Bearer <ACCESS_TOKEN>"
```

8) Debug rate-limits (only if enabled)

```bash
curl -X GET http://localhost:8000/auth/debug/rate-limits \
	-H "X-Debug-Token: <AUTH_DEBUG_TOKEN>"
```

9) Reset debug buckets/metrics (only if enabled)

```bash
curl -X POST http://localhost:8000/auth/debug/rate-limits/reset \
	-H "X-Debug-Token: <AUTH_DEBUG_TOKEN>"
```

