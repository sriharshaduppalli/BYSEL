# BYSEL Deployment Checklist

## 📋 6-Step Deployment Workflow

Follow this checklist to deploy BYSEL to production.

---

## ✅ Step 1: Test Locally

**Status:** ✅ COMPLETED

### Backend Tests
```bash
cd backend
python -m pytest tests/test_api.py -v
```

**Results:**
- ✅ test_health_check PASSED
- ✅ test_get_quotes PASSED
- ✅ test_get_holdings_empty PASSED
- ✅ test_place_order PASSED

### Android Tests (in Android Studio)
1. Open `BYSEL/` folder in Android Studio
2. Build → Build Bundle(s) / APK(s) → Build APK(s)
3. Run → Select device/emulator → Run app
4. Test basic functionality:
   - [ ] Watchlist screen loads quotes
   - [ ] Portfolio screen displays holdings
   - [ ] Alerts screen shows active alerts
   - [ ] Can place orders successfully

**Backend Status:** ✅ All 4 tests passing
**Android Status:** ⏳ Ready for testing

---

## 🔑 Step 2: Create Signing Keystore

**Status:** ⏳ PENDING

### Option A: Use Android Studio (Recommended)
1. [ ] Open Android Studio
2. [ ] Build → Generate Signed Bundle/APK
3. [ ] Create new keystore:
   - [ ] Store file: `bysel.jks`
   - [ ] Password: `BYSEL@2026`
   - [ ] Alias: `bysel_key`
   - [ ] Validity: `10000` years
4. [ ] Save keystore to: `BYSEL/bysel.jks`
5. [ ] Verify with: `keytool -list -v -keystore bysel.jks -storepass "BYSEL@2026"`

### Option B: Command Line
```bash
keytool -genkey -v -keystore bysel.jks ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -alias bysel_key ^
  -keypass "BYSEL@2026" ^
  -storepass "BYSEL@2026" ^
  -dname "CN=BYSEL Trader,OU=Trading,O=BYSEL,L=India,S=India,C=IN"
```

### Verification
- [ ] Keystore file exists: `BYSEL/bysel.jks`
- [ ] File is ~3-4 KB
- [ ] `keytool -list` shows `bysel_key` entry

**See:** [KEYSTORE_SETUP.md](./KEYSTORE_SETUP.md)

---

## 🔐 Step 3: Set GitHub Secrets

**Status:** ⏳ PENDING

### Encode Keystore
```bash
# Windows PowerShell
[System.Convert]::ToBase64String([System.IO.File]::ReadAllBytes("bysel.jks")) | Set-Clipboard

# Mac/Linux
base64 -i bysel.jks | tr -d '\n' | pbcopy
```

### Add to GitHub
1. [ ] Go to: `https://github.com/sriharshaduppalli/BYSEL/settings/secrets/actions`
2. [ ] Add secret: **KEYSTORE_BASE64** = (paste Base64 content)
3. [ ] Add secret: **KEYSTORE_PASSWORD** = `BYSEL@2026`
4. [ ] Add secret: **KEY_ALIAS** = `bysel_key`
5. [ ] Add secret: **KEY_PASSWORD** = `BYSEL@2026`
6. [ ] Add secret: **PLAYSTORE_SERVICE_ACCOUNT** = (if available)

### Verify Secrets
- [ ] All 5 secrets appear in settings (values hidden)
- [ ] No syntax errors

**See:** [GITHUB_SECRETS_SETUP.md](./GITHUB_SECRETS_SETUP.md)

---

## 🏷️ Step 4: Tag Release

**Status:** ⏳ PENDING

### Ensure All Code is Pushed
```bash
cd BYSEL
git status
# Should show: "On branch main, working tree clean"
```

### Create Version Tag
```bash
git tag -a v1.0.0 -m "Release v1.0.0 - Initial BYSEL trading platform"
git push origin v1.0.0
```

### Verify Tag
```bash
git tag -l
git show v1.0.0
```

### Triggers Workflows
- ✅ Tags matching `v*.*.*` automatically trigger:
  - `bysel-playstore.yml` (Build & Sign)
  - `bysel-release.yml` (Version bumping)

**Verification:**
- [ ] Tag appears in: `https://github.com/sriharshaduppalli/BYSEL/tags`
- [ ] Workflows start automatically

---

## 📊 Step 5: Monitor Pipeline

**Status:** ⏳ PENDING

### Watch Build Progress
1. [ ] Go to: `https://github.com/sriharshaduppalli/BYSEL/actions`
2. [ ] Wait for `bysel-playstore` workflow to complete
3. [ ] Check each job:

#### bysel-playstore Job Stages
- [ ] **checkout** - Code downloaded ✓
- [ ] **setup-java** - JDK 11 configured ✓
- [ ] **build-and-sign** - APK/AAB signed ✓
- [ ] **upload-artifacts** - Outputs ready ✓

### Expected Artifacts
- [ ] `app-release.aab` - Android App Bundle (35-50 MB)
- [ ] `app-release.apk` - APK file (15-25 MB)

### Troubleshooting
- ❌ **Secret not found?** → Check GitHub Secrets are set correctly
- ❌ **Build failed?** → Check Android build.gradle.kts syntax
- ❌ **Sign failed?** → Verify keystore credentials match

**Dashboard:** `https://github.com/sriharshaduppalli/BYSEL/actions`

---

## 🚀 Step 6: Publish to Google Play Store

**Status:** ⏳ PENDING (Requires Manual Steps)

### Prerequisites
- [ ] Google Play Developer Account (active, $25 one-time fee)
- [ ] App created in Play Console
- [ ] Store listing completed
- [ ] Service account credentials (if auto-publishing)

### Manual Publishing (Recommended for First Release)
1. [ ] Download `app-release.aab` from workflow artifacts
2. [ ] Go to: `https://play.google.com/console/u/0/developers`
3. [ ] Select **BYSEL** app
4. [ ] Left menu → **Release** → **Production**
5. [ ] Click **Create new release**
6. [ ] Upload `app-release.aab`
7. [ ] Complete store listing:
   - [ ] App title
   - [ ] Description
   - [ ] Screenshots (min 4)
   - [ ] Feature graphic
   - [ ] Content rating questionnaire
8. [ ] Review and submit for review

### Automated Publishing (After First Release)
- Ensure `PLAYSTORE_SERVICE_ACCOUNT` secret is set
- Workflow will automatically upload to internal testing track
- Manual promotion to production still required

### Review Timeline
- ⏳ Initial review: 24-48 hours
- ⏳ Subsequent updates: 1-4 hours
- ✅ Status visible in Play Console → Release management

### Post-Launch Monitoring
- [ ] Check crash reports (Play Console → Analytics)
- [ ] Monitor user reviews and ratings
- [ ] Track performance metrics
- [ ] Plan next releases

---

## 🎯 Success Criteria

### Before Step 1
- [ ] Backend code is clean
- [ ] Android code compiles
- [ ] Git repo is ready

### Before Step 2
- [ ] All backend tests pass: 4/4 ✅
- [ ] Android app builds without errors
- [ ] No critical warnings in build output

### Before Step 3
- [ ] Keystore file created and verified
- [ ] Base64 encoding successful
- [ ] Keystore passwords known and recorded

### Before Step 4
- [ ] All GitHub secrets configured
- [ ] Test push successful
- [ ] Workflow run without errors

### Before Step 5
- [ ] Git tag created and pushed
- [ ] GitHub Actions workflow triggered
- [ ] Build progresses without blocking

### Before Step 6
- [ ] Build completes successfully
- [ ] Artifacts available (AAB/APK)
- [ ] App Bundle signed correctly
- [ ] Ready for Play Store review

### Final Success
- ✅ App visible on Google Play Store
- ✅ Installation link: `https://play.google.com/store/apps/details?id=com.bysel.trading`
- ✅ Users can download and rate

---

## 📞 Troubleshooting Quick Links

| Issue | Reference |
|-------|-----------|
| Keystore problems | [KEYSTORE_SETUP.md](./KEYSTORE_SETUP.md) |
| Secrets not working | [GITHUB_SECRETS_SETUP.md](./GITHUB_SECRETS_SETUP.md) |
| Build failures | [ARCHITECTURE.md](./ARCHITECTURE.md#build-system) |
| App crashes | [QUICK_REFERENCE.md](./QUICK_REFERENCE.md#debugging) |

---

## ✨ Timeline Estimate

| Step | Duration | Cumulative |
|------|----------|-----------|
| Step 1: Testing | 15 min | 15 min |
| Step 2: Keystore | 10 min | 25 min |
| Step 3: Secrets | 5 min | 30 min |
| Step 4: Tag | 2 min | 32 min |
| Step 5: Monitor | 10-30 min | 42-62 min |
| Step 6: Publish | 30 min | 72-92 min |

**Total:** ~1.5 hours to production

---

## 🎉 Celebration!

Upon successful publication to Play Store:

1. [ ] Share app link with team
2. [ ] Update documentation with live store link
3. [ ] Celebrate launch! 🎊
4. [ ] Monitor for user feedback
5. [ ] Plan next version/features

---

**Last Updated:** `2025-01-09`
**Version:** `1.0.0`
**Status:** Ready for deployment
