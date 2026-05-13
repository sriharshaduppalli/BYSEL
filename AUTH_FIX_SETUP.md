# BYSEL Authentication - Complete Fix & Setup Guide

## 🚨 Critical Issues Fixed

### **Issue 1: No AUTH_SECRET** → FIXED ✅
- **Problem**: Default hardcoded secret makes all tokens predictable
- **Fix**: Now validates AUTH_SECRET on startup, fails fast with clear error
- **Action**: Add to `.env`:
  ```bash
  AUTH_SECRET=generate_secure_random_string_here_min_32_chars
  ```

### **Issue 2: Silent Failures During Registration** → FIXED ✅
- **Problem**: Wallet creation failures didn't block registration
- **Fix**: Now uses atomic transactions, fails completely if wallet creation fails
- **Action**: New `/auth/register` endpoint in `auth_fixed.py`

### **Issue 3: OTP Console Fallback in Production** → FIXED ✅
- **Problem**: OTP codes printed to console (security risk)
- **Fix**: Proper fallback chain: Fast2SMS → Twilio → Error (no console)
- **Action**: Configure SMS providers in `.env` (see below)

### **Issue 4: Poor Input Validation** → FIXED ✅
- **Problem**: Username/email/password not validated
- **Fix**: Comprehensive Pydantic validators
- **Action**: Use new validated endpoints

### **Issue 5: No Rate Limiting** → FIXED ✅
- **Problem**: Brute force attacks possible
- **Fix**: Built-in rate limiting ready (configure in `.env`)
- **Action**: Deploy with rate limiting enabled

---

## 📋 Setup Checklist

### **Step 1: Generate AUTH_SECRET**
```bash
# Generate secure random 32+ character string
python -c "import secrets; print(secrets.token_hex(32))"
# Output: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6

# Add to .env
AUTH_SECRET=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6
```

### **Step 2: Configure SMS Provider (Choose One)**

#### **Option A: Fast2SMS (RECOMMENDED for India)**
```bash
# Get API key from: https://www.fast2sms.com/
# Free 100 SMS/day
FAST2SMS_API_KEY=your_fast2sms_api_key_here
OTP_CONSOLE_FALLBACK=false
```

#### **Option B: Twilio (Backup)**
```bash
# Get credentials from: https://www.twilio.com/
TWILIO_ACCOUNT_SID=your_account_sid
TWILIO_AUTH_TOKEN=your_auth_token
TWILIO_PHONE_NUMBER=+1234567890
OTP_CONSOLE_FALLBACK=false
```

#### **Option C: Development (Console Fallback)**
```bash
# Only for development - NOT production!
OTP_CONSOLE_FALLBACK=true
```

### **Step 3: Enable New Auth Routes**

In `backend/app/main.py`, add the new router:

```python
from app.routes.auth_fixed import router as auth_fixed_router

# Include the fixed auth routes
app.include_router(auth_fixed_router)
```

### **Step 4: Initialize Auth System on Startup**

In `backend/app/main.py`, add to startup:

```python
from app.config.auth_config import validate_auth_on_startup

@app.on_event("startup")
async def startup_event():
    """Validate auth config on startup"""
    try:
        validate_auth_on_startup()
    except Exception as e:
        logger.error(f"Authentication initialization failed: {e}")
        raise

    # ... other startup tasks
```

### **Step 5: Update Requirements**

```bash
# Ensure these are in requirements.txt
bcrypt==4.1.1
pydantic==2.5.0
sqlalchemy==2.0.23

# For SMS (choose as needed)
twilio==8.10.0  # If using Twilio
requests==2.31.0  # For Fast2SMS HTTP requests
```

---

## 🔄 API Endpoints (Fixed)

### **1. Register User**
```bash
POST /auth/register

{
  "username": "john_doe",        # 3-30 chars, alphanumeric + underscore/hyphen
  "email": "john@example.com",   # Valid email
  "password": "SecurePass123",   # Min 6 chars
  "phone": "+919876543210"       # Optional, valid E.164 format
}

Response 200:
{
  "success": true,
  "message": "Registration successful",
  "user": {
    "id": 123,
    "username": "john_doe",
    "email": "john@example.com"
  },
  "token": "auth_token_here"
}

Response 400:
{
  "detail": "Username already registered"
}
```

### **2. Login**
```bash
POST /auth/login

{
  "username_or_email": "john_doe",
  "password": "SecurePass123"
}

Response 200:
{
  "success": true,
  "message": "Login successful",
  "user": { ... },
  "token": "auth_token_here"
}

Response 401:
{
  "detail": "Invalid credentials"
}
```

### **3. Send OTP**
```bash
POST /auth/otp/send

{
  "phone": "+919876543210"  # Valid E.164 format
}

Response 200:
{
  "success": true,
  "message": "OTP sent successfully",
  "otp_id": "unique_otp_request_id",
  "validity_seconds": 300
}

Response 500:
{
  "detail": "Failed to send OTP. Please try again."
}
```

### **4. Verify OTP & Login/Register**
```bash
POST /auth/otp/verify

{
  "otp_id": "unique_otp_request_id",
  "phone": "+919876543210",
  "otp_code": "123456"  # 6 digits
}

Response 200 (Existing User):
{
  "success": true,
  "message": "Login successful",
  "is_new_user": false,
  "user": { ... },
  "token": "auth_token_here"
}

Response 200 (New User):
{
  "success": true,
  "message": "Registration and login successful",
  "is_new_user": true,
  "user": {
    "id": 456,
    "username": "user_543210",  # Auto-generated
    "email": "user_543210@bysel-otp.local"
  },
  "token": "auth_token_here"
}

Response 401:
{
  "detail": "Wrong OTP (attempt 1/3)"
}
```

---

## 🧪 Testing

### **Test 1: Registration**
```bash
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "TestPass123",
    "phone": "+919876543210"
  }'
```

### **Test 2: Login**
```bash
curl -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username_or_email": "testuser",
    "password": "TestPass123"
  }'
```

### **Test 3: OTP Send**
```bash
curl -X POST http://localhost:8000/auth/otp/send \
  -H "Content-Type: application/json" \
  -d '{"phone": "+919876543210"}'
```

### **Test 4: OTP Verify**
```bash
# Copy otp_id from send response
# Get OTP from SMS or console (if dev mode)

curl -X POST http://localhost:8000/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{
    "otp_id": "abc123def456...",
    "phone": "+919876543210",
    "otp_code": "123456"
  }'
```

---

## ⚠️ Environment Variables Reference

```bash
# CRITICAL - Must be set
AUTH_SECRET=secure_random_string_32_chars_min

# SMS Configuration - Choose at least one provider
FAST2SMS_API_KEY=your_api_key        # Fast2SMS for India
TWILIO_ACCOUNT_SID=your_sid          # OR Twilio backup
TWILIO_AUTH_TOKEN=your_token
TWILIO_PHONE_NUMBER=+1234567890

# Development only
OTP_CONSOLE_FALLBACK=false           # Set true only in dev

# Optional - Rate limiting
LOGIN_RATE_LIMIT_ATTEMPTS=6          # Attempts per window
LOGIN_RATE_LIMIT_WINDOW_SECONDS=60   # Time window
LOGIN_LOCKOUT_FAILURES=5             # Lockouts after this
LOGIN_LOCKOUT_DURATION_SECONDS=300   # Lockout duration

# Optional - Token configuration
ACCESS_TOKEN_TTL_SECONDS=900         # 15 minutes
REFRESH_TOKEN_TTL_SECONDS=2592000    # 30 days
MAX_ACTIVE_SESSIONS_PER_USER=5       # Max concurrent logins
```

---

## 🔍 Troubleshooting

### **Error: "AUTH_SECRET must be set"**
- **Solution**: Add `AUTH_SECRET` to `.env` file (see Step 1)

### **Error: "Failed to send OTP"**
- **Check**: Is `FAST2SMS_API_KEY` or Twilio credentials configured?
- **Check**: Is the phone number in valid E.164 format (+country code)?
- **Check**: Development mode? Set `OTP_CONSOLE_FALLBACK=true`

### **Error: "Username already registered"**
- **Solution**: Use different username or login if you forgot password

### **Error: "Invalid email format"**
- **Solution**: Use valid email like `user@example.com`

### **Error: "OTP expired"**
- **Solution**: OTP valid for 5 minutes. Request a new one with `/auth/otp/send`

### **Error: "Too many OTP attempts"**
- **Solution**: Max 3 wrong attempts. Request new OTP.

---

## 📊 Security Features

✅ Secure password hashing (bcrypt)
✅ Input validation on all fields
✅ Rate limiting ready
✅ Account lockout after failed attempts
✅ OTP expiration (5 minutes)
✅ OTP attempt limits (3 attempts)
✅ SMS provider fallback
✅ Atomic transaction (user + wallet)
✅ No information disclosure
✅ No console leaks in production

---

## 🚀 Deployment Checklist

- [ ] `AUTH_SECRET` set to secure random value
- [ ] SMS provider configured (Fast2SMS or Twilio)
- [ ] `OTP_CONSOLE_FALLBACK=false` (production)
- [ ] New auth routes included in main.py
- [ ] Startup validation enabled
- [ ] Tested registration flow
- [ ] Tested login flow
- [ ] Tested OTP send/verify flow
- [ ] Database migrations applied
- [ ] Logs monitored for auth errors

---

## 📞 Support

If you encounter issues:
1. Check `.env` file has all required variables
2. Review logs for specific error messages
3. Test endpoints with curl (see Testing section)
4. Ensure SMS provider is working (test separately if needed)

