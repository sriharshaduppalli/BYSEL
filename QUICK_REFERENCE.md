# BYSEL Quick Reference Guide

## 🚀 Quick Commands

### Backend
```bash
# Install & Run
cd backend
pip install -r requirements.txt
python -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload

# Run Tests
pytest tests/ -v

# Docker
docker-compose up --build
```

### Android
```bash
# Build Debug
cd android
./gradlew clean assembleDebug

# Run on Device
./gradlew installDebug

# Build Release (signed)
./gradlew clean bundleRelease

# Run Tests
./gradlew test
```

---

## 📝 Key Files

| Purpose | File |
|---------|------|
| API Endpoints | `backend/app/routes/__init__.py` |
| API Models | `backend/app/models/schemas.py` |
| DB Models | `backend/app/database/db.py` |
| App Entry | `android/app/src/main/java/com/bysel/trader/MainActivity.kt` |
| API Client | `android/app/src/main/java/com/bysel/trader/data/api/RetrofitClient.kt` |
| ViewModel | `android/app/src/main/java/com/bysel/trader/viewmodel/TradingViewModel.kt` |
| UI Screens | `android/app/src/main/java/com/bysel/trader/ui/screens/` |
| Config | `backend/.env.example` |

---

## 🔌 API Endpoints Summary

```
GET  /quotes?symbols=RELIANCE,TCS        → List[Quote]
GET  /holdings                            → List[Holding]
POST /order                               → OrderResponse
GET  /health                              → HealthCheck
```

---

## 📊 Data Models

### Quote
```json
{
  "symbol": "RELIANCE",
  "last": 2850.50,
  "pctChange": 1.25
}
```

### Holding
```json
{
  "symbol": "TCS",
  "qty": 10,
  "avgPrice": 3200.00,
  "last": 3350.00,
  "pnl": 1500.00
}
```

### Order
```json
{
  "symbol": "RELIANCE",
  "qty": 5,
  "side": "BUY"
}
```

---

## 🎨 UI Screens

1. **Watchlist** - Tab 0
   - Displays quotes
   - Refresh button
   - Quote cards with symbol, price, % change

2. **Portfolio** - Tab 1
   - Shows holdings
   - Buy/Sell buttons per holding
   - P&L calculation

3. **Alerts** - Tab 2
   - Create new alert dialog
   - List active alerts
   - Delete button per alert

---

## 🔧 Environment Configuration

### Backend (.env)
```env
DEBUG=True
DATABASE_URL=sqlite:///./bysel.db
API_HOST=0.0.0.0
API_PORT=8000
```

### Android
Edit `RetrofitClient.kt` for API URL:
```kotlin
private const val BASE_URL = "http://10.0.2.2:8000" // Emulator
// For device: "http://YOUR_IP:8000"
```

---

## 🔑 GitHub Secrets (For CI/CD)

```
KEYSTORE_BASE64            → Base64 encoded .jks file
KEYSTORE_PASSWORD          → Keystore password
KEY_ALIAS                  → bysel_key
KEY_PASSWORD               → Key password
PLAYSTORE_SERVICE_ACCOUNT  → Google Play Console JSON
```

---

## 📦 Generate Keystore

```bash
keytool -genkey -v -keystore bysel.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias bysel_key
```

Convert to Base64 (for GitHub secrets):
```bash
# Mac/Linux
base64 -i bysel.jks | tr -d '\n' | pbcopy

# Windows
certutil -encodehex bysel.jks encoded.txt 0
```

---

## 🏗️ Project Architecture at a Glance

```
Android (MVVM)
    ↓
ViewModel (StateFlow)
    ↓
Repository (API + Cache)
    ↓
API Client ←→ Room Database
    ↓
Backend (FastAPI)
    ↓
SQLAlchemy ORM
    ↓
SQLite Database
```

---

## 🧪 Test Commands

```bash
# Backend API tests
cd backend && pytest tests/test_api.py -v

# Android unit tests
cd android && ./gradlew test

# Backend health check
curl http://localhost:8000/health

# Get quotes
curl http://localhost:8000/quotes?symbols=RELIANCE,TCS
```

---

## 📱 Release Process

1. **Tag Release**
   ```bash
   git tag -a v1.0.0 -m "Release v1.0.0"
   git push origin v1.0.0
   ```

2. **Monitor CI/CD**
   - GitHub Actions automatically builds APK/AAB
   - Runs tests
   - Uploads to Play Store

3. **Play Store**
   - Review in Play Console
   - Approve for release

---

## 🐛 Troubleshooting

| Problem | Quick Fix |
|---------|-----------|
| API not connecting | Check URL in RetrofitClient.kt |
| Gradle sync fails | `./gradlew clean` |
| Backend won't start | Delete `bysel.db`, check Python version |
| Port 8000 in use | `lsof -i :8000` then `kill -9 <PID>` |

---

## 📚 Documentation Map

- **README.md** → Overview & quick start
- **SETUP.md** → Detailed setup & deployment
- **ARCHITECTURE.md** → System design
- **IMPLEMENTATION_SUMMARY.md** → What's been built
- **This File** → Quick reference

---

## 🌐 URLs

| Service | URL | Notes |
|---------|-----|-------|
| Backend (Local) | http://localhost:8000 | Development |
| API Docs | http://localhost:8000/docs | Swagger UI |
| Emulator | http://10.0.2.2:8000 | From Android emulator |
| Play Console | https://play.google.com/console | Release |

---

## 💡 Tips & Tricks

1. **Auto-reload backend:** Use `--reload` flag
2. **View API docs:** Open `/docs` endpoint in browser
3. **Debug network calls:** Check logcat or Android Studio
4. **Fast Gradle sync:** Update JDK to 17
5. **Clear cache:** Delete `.gradle` and `build/` folders

---

## 🎯 Useful Code Snippets

### Start Observation in ViewModel
```kotlin
fun refreshQuotes() {
    viewModelScope.launch {
        repository.getQuotes(defaultSymbols).collect { result ->
            when(result) {
                is Result.Success → _quotes.value = result.data
                is Result.Error → _error.value = result.message
            }
        }
    }
}
```

### Add New API Endpoint
```python
# backend/app/routes/__init__.py
@router.get("/new-endpoint")
async def new_endpoint(db: Session = Depends(get_db)):
    # Your logic here
    return {"result": "data"}
```

### Create New Screen
```kotlin
@Composable
fun NewScreen(viewModel: TradingViewModel) {
    val data by viewModel.someData.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Your UI here
    }
}
```

---

## ✅ Pre-Release Checklist

- [ ] All tests passing
- [ ] Backend API responding correctly
- [ ] Android app builds successfully
- [ ] Signing keystore created
- [ ] GitHub secrets configured
- [ ] Release tag created
- [ ] CI/CD pipeline completed
- [ ] Play Store build reviewed
- [ ] Store listing details filled
- [ ] App ready for publication

---

## 📞 Quick Contact Info

- **Repo:** github.com/sriharshaduppalli/BYSEL
- **Issues:** Use GitHub Issues
- **Docs:** See README.md, SETUP.md, ARCHITECTURE.md

---

**Last Updated:** February 1, 2026  
**Version:** 1.0.0  
**Status:** Ready for Production Release ✨
