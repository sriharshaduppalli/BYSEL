# BYSEL Play Store Deployment Checklist

## ✅ Already Completed
- [x] App built successfully (AAB created)
- [x] Code signing configured
- [x] GitHub Actions workflows set up
- [x] Release notes prepared

---

## ⚠️ Still Required

### **1. Google Play Developer Account** (Cost: $25)
- [ ] Go to: https://play.google.com/console
- [ ] Pay the $25 registration fee
- [ ] Log in with Google Account

### **2. Create App Entry**
- [ ] Click "Create app"
- [ ] App name: `BYSEL`
- [ ] Default language: English
- [ ] App category: Finance
- [ ] Content rating: Required
- [ ] Package name: `com.bysel.trader` ✅ (Already in code)

### **3. Set Up Content Rating**
- [ ] Go to Setup → Content rating
- [ ] Complete questionnaire
- [ ] Get rating certificate

### **4. Add App Details**
- [ ] Screenshots (at least 2, up to 8)
  - Phone: min 480x800px, max 1440x2560px
  - Tablet: min 1024x768px, max 2560x1440px
- [ ] Feature graphic: 1024x500px
- [ ] Icon: 512x512px
- [ ] Short description: 50 chars max
- [ ] Full description: 4000 chars max
- [ ] Privacy policy: URL required
- [ ] Test accounts: Optional but recommended

### **5. Create Service Account for CI/CD**
- [ ] Go to Google Play Console → Settings → API access
- [ ] Click "Create Service Account"
- [ ] Follow Google Cloud setup
- [ ] Download JSON key file
- [ ] Go to GitHub repo → Settings → Secrets → New secret
- [ ] Name: `PLAYSTORE_SERVICE_ACCOUNT`
- [ ] Value: Paste entire JSON key contents

### **6. Set Release Track**
- [ ] Internal Testing (first release - recommended)
- [ ] Alpha Testing (2nd round of testing)
- [ ] Beta Testing (open beta)
- [ ] Production (public release)

### **7. Technical Release Contact**
- [ ] Payment account info
- [ ] Merchant ID setup
- [ ] Tax & compliance forms

---

## 📋 What Happens After Setup

### **First Release: Manual**
```bash
# Build and upload manually if needed:
cd android
./gradlew bundleRelease

# Then upload via Play Console
```

### **Subsequent Releases: Automatic**

Once PLAYSTORE_SERVICE_ACCOUNT is configured, just:
```bash
git tag -a v1.0.9 -m "Release v1.0.9"
git push origin v1.0.9
```

**Workflow will:**
- ✅ Build signed AAB
- ✅ Bump version
- ✅ Upload to Play Store (Internal Testing track)
- ✅ You can promote to other tracks in Play Console

---

## 🚀 Release Timeline

1. **Day 1**: Create Play Developer account + app entry
2. **Day 2-3**: Prepare app details, screenshots, content rating
3. **Day 4**: Set up Service Account
4. **Day 5**: Upload first release
5. **Day 6-7**: Review (Google typically reviews in 1-3 hours for internal, longer for production)

---

## 📱 Screenshots You Need

### **Phone (Portrait) - 2160x3840 px recommended**
1. Watchlist screen showing stock prices
2. Portfolio screen with holdings
3. Alerts screen with price alerts
4. Order placement flow

### **Tablet (Landscape) - 2560x1440 px recommended**
1. Dashboard view
2. Trading interface

### **Thumbnail** (512x512 px)
- App icon with "BYSEL" text
- Clean, simple design
- Represents trading/finance theme

---

## 🔐 Security Notes

### **Keystore (Already Configured)**
- ✅ Password: Your KEYSTORE_PASSWORD secret
- ✅ Key alias: Your KEY_ALIAS secret
- ✅ Key password: Your KEY_PASSWORD secret
- ✅ All stored in GitHub secrets

### **Service Account (Needs Setup)**
- ⚠️ Keep JSON key secret
- ⚠️ Only add to GitHub, not commit to repo
- ⚠️ Can revoke anytime from Google Cloud Console

---

## 📞 Support Resources

- **Google Play Console Help**: https://support.google.com/googleplay/android-developer
- **Service Account Setup**: https://developers.google.com/identity/protocols/oauth2/service-account
- **Play Store Policy**: https://play.google.com/about/developer-content-policy/
- **Gradle Build Docs**: https://developer.android.com/studio/build

---

## 💡 Tips

1. **Test on Internal Track First**
   - Lower risk
   - Faster review
   - Can test with limited users before going public

2. **Use Beta Track for Real Testing**
   - Let real users test before public release
   - Get feedback through reviews
   - Update based on feedback

3. **Version Bumping**
   - Each release must have higher versionCode
   - GitHub Actions auto-bumps for you ✅

4. **App Store Optimization (ASO)**
   - Good description = more downloads
   - Quality screenshots = higher conversion
   - User reviews matter for ranking

---

## Next Steps

1. **This week**: Register Google Play Developer account
2. **Next week**: Complete app setup in Play Console
3. **After setup**: Add PLAYSTORE_SERVICE_ACCOUNT secret
4. **Then**: Run first release via GitHub Actions

**Currently**: AAB is ready anytime! ✅
