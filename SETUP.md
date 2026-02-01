# BYSEL Setup & Deployment Guide

## Table of Contents
1. [Local Development Setup](#local-development-setup)
2. [Backend Deployment](#backend-deployment)
3. [Android Build & Release](#android-build--release)
4. [CI/CD Pipeline Configuration](#cicd-pipeline-configuration)
5. [Troubleshooting](#troubleshooting)

---

## Local Development Setup

### Prerequisites
- **JDK 17+** (for Android)
- **Python 3.11+** (for Backend)
- **Android Studio 2023.1+**
- **Git**
- **Docker** (optional, for containerized backend)

### Step 1: Clone Repository
```bash
cd c:\Users\sriha\Desktop\Applications\BYSEL
git clone https://github.com/sriharshaduppalli/BYSEL.git
cd BYSEL
```

### Step 2: Backend Setup

#### Option A: Local Python Environment
```bash
cd backend

# Create virtual environment
python -m venv venv

# Activate venv
# Windows
venv\Scripts\activate
# Mac/Linux
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Run server
python -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

#### Option B: Docker (Recommended for deployment)
```bash
cd backend
docker build -t bysel-backend .
docker run -p 8000:8000 bysel-backend
```

#### Option C: Docker Compose
```bash
# From project root
docker-compose up --build
```

**Backend URL:** `http://localhost:8000`  
**Swagger Docs:** `http://localhost:8000/docs`

### Step 3: Android Setup

#### Open in Android Studio
1. Open Android Studio
2. File → Open → Select `android` folder
3. Wait for Gradle sync
4. Connect device or start emulator
5. Run → Run 'app'

#### Build from Command Line
```bash
cd android

# Build APK
./gradlew clean assembleDebug

# Install on device/emulator
./gradlew installDebug

# Run tests
./gradlew test
```

### Step 4: Backend Configuration for Mobile

**For Emulator:**
- API URL: `http://10.0.2.2:8000` (already configured)

**For Physical Device:**
1. Get your computer IP: `ipconfig` (Windows) or `ifconfig` (Mac/Linux)
2. Edit `android/app/src/main/java/com/bysel/trader/data/api/RetrofitClient.kt`
3. Change `BASE_URL = "http://YOUR_COMPUTER_IP:8000"`

---

## Backend Deployment

### Development
```bash
cd backend
python -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload
```

### Production

#### Using Gunicorn + Uvicorn
```bash
pip install gunicorn
gunicorn -w 4 -k uvicorn.workers.UvicornWorker app:app --bind 0.0.0.0:8000
```

#### Using Docker
```bash
docker build -t bysel-api:latest .
docker run -d -p 8000:8000 --env-file .env bysel-api:latest
```

#### Cloud Deployment (Heroku Example)
```bash
# Create Procfile
echo "web: gunicorn -w 4 -k uvicorn.workers.UvicornWorker app:app" > Procfile

# Deploy
heroku login
heroku create bysel-api
git push heroku main
```

---

## Android Build & Release

### Debug Build
```bash
cd android
./gradlew clean assembleDebug
# Output: android/app/build/outputs/apk/debug/app-debug.apk
```

### Release Build (Signed)

#### Step 1: Create Keystore
```bash
keytool -genkey -v -keystore bysel.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias bysel_key \
  -keypass "YOUR_KEY_PASSWORD" \
  -storepass "YOUR_KEYSTORE_PASSWORD"
```

#### Step 2: Sign Release APK
```bash
cd android

./gradlew clean bundleRelease \
  -Pandroid.injected.signing.store.file=../bysel.jks \
  -Pandroid.injected.signing.store.password="YOUR_KEYSTORE_PASSWORD" \
  -Pandroid.injected.signing.key.alias=bysel_key \
  -Pandroid.injected.signing.key.password="YOUR_KEY_PASSWORD"

# Output: android/app/build/outputs/bundle/release/app-release.aab
```

#### Step 3: Upload to Play Store
1. Go to [Google Play Console](https://play.google.com/console)
2. Create new app → "BYSEL Trader"
3. Fill in store listing details
4. Upload AAB file
5. Review & publish

---

## CI/CD Pipeline Configuration

### GitHub Secrets Setup

1. Go to Settings → Secrets and variables → Actions
2. Add the following secrets:

#### Android Release Signing
```
KEYSTORE_BASE64: <base64 encoded keystore>
KEYSTORE_PASSWORD: <your keystore password>
KEY_ALIAS: bysel_key
KEY_PASSWORD: <your key password>
```

#### Encode Keystore to Base64
```bash
# Windows
certutil -encodehex bysel.jks bysel_base64.txt 0
# (Remove spaces, copy content)

# Mac/Linux
base64 -i bysel.jks | tr -d '\n' | pbcopy
```

#### Play Store Publishing
```
PLAYSTORE_SERVICE_ACCOUNT: <JSON content from Play Store service account>
```

### Workflow Triggers

**Automated CI/CD:**
- Every push to `main` branch
- Every pull request

**Release to Play Store:**
```bash
# Create release tag
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0

# This automatically triggers:
# 1. Build debug and release APKs
# 2. Run backend tests
# 3. Upload AAB to Play Store (internal track)
# 4. Bump version numbers
```

### Manual Release
1. Go to Actions tab on GitHub
2. Select "BYSEL Release Build" or "BYSEL Play Store Release"
3. Click "Run workflow"
4. Select branch → Run

---

## Troubleshooting

### Backend Issues

| Problem | Solution |
|---------|----------|
| Port 8000 already in use | `lsof -i :8000` then `kill -9 <PID>` or change port |
| Import errors | `pip install -r requirements.txt` and verify Python 3.11+ |
| Database locked | Delete `bysel.db` and restart |
| CORS errors | Verify CORS is enabled in `backend/app/__init__.py` |

### Android Issues

| Problem | Solution |
|---------|----------|
| Gradle sync fails | File → Sync Now, or `./gradlew clean` |
| Build fails | Update JDK to 17, clear `.gradle` folder |
| API connection fails | Check `RetrofitClient.kt` URL, restart backend |
| Emulator slow | Use hardware acceleration, allocate more RAM |

### Play Store Issues

| Problem | Solution |
|---------|----------|
| Upload rejected | Check signing certificate matches app console |
| Version code too low | Bump version in `build.gradle.kts` |
| Missing metadata | Add screenshots, descriptions in Play Console |

---

## Environment Variables

### Backend (.env)
```env
DEBUG=True
DATABASE_URL=sqlite:///./bysel.db
API_HOST=0.0.0.0
API_PORT=8000
```

### Android (build.gradle.kts)
```kotlin
signingConfigs {
    release {
        storeFile = file("../bysel.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
```

---

## API Reference

### Stock Quotes
```http
GET /quotes?symbols=RELIANCE,TCS,INFY
Content-Type: application/json

Response:
[
  {
    "symbol": "RELIANCE",
    "last": 2850.50,
    "pctChange": 1.25
  }
]
```

### User Holdings
```http
GET /holdings
Content-Type: application/json

Response:
[
  {
    "symbol": "TCS",
    "qty": 10,
    "avgPrice": 3200.00,
    "last": 3350.00,
    "pnl": 1500.00
  }
]
```

### Place Order
```http
POST /order
Content-Type: application/json

{
  "symbol": "RELIANCE",
  "qty": 5,
  "side": "BUY"
}

Response:
{
  "status": "ok",
  "order": {
    "symbol": "RELIANCE",
    "qty": 5,
    "side": "BUY"
  },
  "message": "BUY order for 5 shares of RELIANCE completed"
}
```

---

## Additional Resources

- [FastAPI Documentation](https://fastapi.tiangolo.com)
- [Jetpack Compose Guide](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Google Play Console](https://play.google.com/console)
- [GitHub Actions](https://docs.github.com/en/actions)

