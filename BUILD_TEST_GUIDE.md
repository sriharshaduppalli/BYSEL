# BYSEL App - Build & Test Guide

## Complete Steps to Build and Test the Enhanced App

### Phase 1: Open Project in Android Studio

1. **Launch Android Studio**
   - If not already open, start Android Studio from your applications

2. **Open the BYSEL Project**
   - File → Open
   - Navigate to: `C:\Users\sriha\Desktop\Applications\BYSEL\BYSEL`
   - Select the folder and click OK
   - Wait for the project to load (may take a few minutes on first load)

3. **Let Android Studio Sync Gradle**
   - After opening, you'll see: **"Gradle files need to be synced"**
   - Click **"Sync Now"** (top banner, or File → Sync Project with Gradle Files)
   - Wait for indexing and dependency download to complete
   - This will automatically download the Gradle wrapper JAR file needed for building

---

### Phase 2: Configure Project Settings

1. **Verify SDK Configuration**
   - Go to File → Project Structure
   - SDK Location tab: Verify your Android SDK path
   - JDK location: Should point to Android Studio's JRE
   - Click OK

2. **Check Build Configuration**
   - Open: `android/app/build.gradle.kts`
   - Verify:
     - `compileSdk = 35`
     - `targetSdk = 35`
     - `versionCode = 16`
     - `versionName = "1.0.1"`

---

### Phase 3: Build the Debug APK

#### Option A: Build in Android Studio (Recommended)

1. **Build Menu**
   - Click **Build** → **Build APK(s)**
   - Or use keyboard shortcut: **Ctrl + Shift + B**

2. **Monitor the Build**
   - Watch the **Build** output window at the bottom
   - Build process will:
     - Compile Kotlin code
     - Download dependencies
     - Process resources
     - Generate APK
   - Should take 3-5 minutes on first build

3. **Build Success**
   - You'll see: **"APK located at: .../app-debug.apk"**
   - APK location: `android/app/build/outputs/apk/debug/app-debug.apk`

#### Option B: Build from Terminal (In Android Studio)

1. **Open Terminal** in Android Studio: View → Terminal

2. **Run Gradle Build**
   ```powershell
   cd android
   ./gradlew clean assembleDebug
   ```

3. **Wait for completion**

---

### Phase 4: Set Up Android Emulator

1. **Open Device Manager**
   - Tools → Device Manager
   - Or in top right corner: "Device Manager" button

2. **Select or Create Emulator**
   - If you have one created: Click the **Play button** to launch it
   - If you need to create one:
     - Click **Create Virtual Device**
     - Choose "Pixel 5" (recommended)
     - Select API 35 (Android Vanilla)
     - Click Create
     - Wait for download to complete

3. **Start the Emulator**
   - After creation, click the **Play button**
   - Wait for it to fully boot (you'll see home screen)

---

### Phase 5: Run the App on Emulator

#### Option A: Android Studio Run Button (Easiest)

1. **Click Run Button**
   - Top right: Green **Play** button
   - Or keyboard: **Shift + F10**

2. **Select Device**
   - Choose your running emulator from the list
   - Click OK

3. **App Installation**
   - Android Studio will:
     - Build the debug APK (if not already built)
     - Install APK to emulator
     - Launch the app

#### Option B: Manual Installation

1. **Build the APK** (Phase 3)

2. **Open Terminal** in Android Studio

3. **Install APK**
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   cd android
   adb install -r build/outputs/apk/debug/app-debug.apk
   ```

4. **Launch App**
   ```powershell
   adb shell am start -n com.bysel.trader/.MainActivity
   ```

---

### Phase 6: Test All 6 Screens

#### Dashboard (Home)
- ✅ Verify portfolio summary displays
- ✅ Check total value, invested, P&L calculations
- ✅ See top gainers/losers cards
- ✅ Check trending indicators (up/down arrows)

#### Trading
- ✅ See all stock quotes
- ✅ Click "Buy" or "Sell" buttons
- ✅ Trade dialog appears with quantity input
- ✅ Click Trade to confirm (may show error if backend not running, but dialog works)
- ✅ View price changes with color coding

#### Portfolio  
- ✅ See your holdings
- ✅ View quantity, average cost, current value
- ✅ Click Buy/Sell buttons
- ✅ Check P&L indicators (green for gains, red for losses)

#### Alerts
- ✅ View active price alerts
- ✅ Create new alert
- ✅ Delete alerts
- ✅ See alert threshold prices

#### Search
- ✅ Type in search bar
- ✅ See filtered results
- ✅ Click View to see stock details

#### Settings
- ✅ Toggle Dark Mode
- ✅ Click "App Theme" - opens theme selection dialog
- ✅ Select different themes:
     - Default (Blue)
     - Ocean (Cyan)
     - Forest (Green)
     - Sunset (Orange)
     - Cyberpunk (Magenta)
- ✅ Toggle Notifications
- ✅ Click on Profile, Security, About, etc.
- ✅ Click Logout button

---

### Phase 7: Test Navigation Between Screens

1. **Bottom Navigation Bar**
   - Tap each icon at the bottom
   - Each screen should load without errors
   - Navigation should be smooth

2. **Cross-Screen Navigation**
   - From Dashboard → Click a stock → opens Trading
   - From Trading → Click Stock Details → should navigate properly
   - From any screen → Click Settings → should load Settings

3. **Back Navigation**
   - Use device back button (if applicable)
   - Screens should navigate backward correctly

---

### Phase 8: Verify New Features

#### Theme System
- [ ] Theme selection dialog appears in Settings
- [ ] 5 different themes are visible
- [ ] Selected theme has a checkmark
- [ ] Colors change when theme is selected

#### Trading Operations
- [ ] Trade dialog shows current price
- [ ] Buy/Sell buttons toggle in dialog
- [ ] Quantity input accepts number values
- [ ] Total calculation updates as you type quantity

#### Portfolio Summary
- [ ] Shows total value prominently
- [ ] Displays invested amount
- [ ] Shows P&L with percentage
- [ ] Color-coded (green for gains, red for losses)

#### Stock Details (if implementing)
- [ ] Shows full stock information
- [ ] Displays 52-week high/low
- [ ] Shows P/E ratio and valuation metrics
- [ ] Buy/Sell buttons present

---

### Phase 9: Troubleshooting Common Issues

#### Build Failures
- **"Could not find or load main class"**
  - Solution: Wait for Gradle sync to complete
  - Click File → Invalidate Caches → Invalidate and Restart

- **"Gradle sync failed"**
  - File → Settings → Tools → SDK Manager
  - Verify Android SDK is installed
  - Re-sync Gradle files

#### APK Won't Install
- **"adb: command not found"**
  - ADB is in Android SDK
  - Use Android Studio's built-in terminal instead
  - Or verify SDK Platform Tools are installed

#### Emulator Won't Start
- Close all other emulators
- Restart Android Studio
- Tools → Device Manager → Wipe data → start fresh

#### App Crashes on Startup
- Check Logcat (View → Tool Windows → Logcat)
- Search for "AndroidRuntime" errors
- Check that network_security_config.xml is properly linked

---

### Phase 10: Performance Tips

1. **First Build is Slower**
   - Gradle downloads dependencies
   - Subsequent builds are faster

2. **Clean Build** (if issues persist)
   - Build → Clean Project
   - Then Build → Build APK(s)

3. **Gradle Offline Mode** (if internet is slow)
   - File → Settings → Build, Execution, Deployment → Gradle
   - Check "Offline work" checkbox (only if Gradle cache is populated)

---

## Expected Results After Completion

✅ **App launches successfully**
✅ **All 6 screens are accessible**
✅ **Bottom navigation has 6 icons** (Dashboard, Trading, Portfolio, Alerts, Search, Settings)
✅ **Theme customization works** (5 selectable themes)
✅ **Stock data displays** with colors and icons
✅ **Buy/Sell dialogs appear** for trading operations
✅ **Portfolio summary shows** with accurate calculations
✅ **UI is responsive** with smooth transitions
✅ **All navigation between screens works**

---

## Next Steps After Testing

1. **Backend Integration** (Optional)
   - Start your FastAPI backend server
   - Connect the app to real trading operations
   - Test buy/sell operations with backend

2. **Release Build** (For Play Store)
   - Build → Build Bundle(s) / APK(s) → Build Bundle(s)
   - This creates signed AAB for Play Store upload

3. **User Testing**
   - Test app with real scenarios
   - Gather feedback
   - Make UI adjustments as needed

---

## Build Artifacts Location

- **Debug APK**: `android/app/build/outputs/apk/debug/app-debug.apk`
- **Release Bundle**: `android/app/build/outputs/bundle/release/app-release.aab`
- **Gradle Cache**: `~/.gradle/` (contains downloaded dependencies)

---

## Java Home Reminder

If you need to build from command line:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd android
.\gradlew.bat clean assembleDebug
```

---

**Last Updated**: February 15, 2026
**App Version**: 1.0.1
**Gradle Version**: 8.5
**Android API Level**: 35
