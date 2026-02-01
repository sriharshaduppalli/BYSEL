# Keystore Setup Instructions

## ⚠️ IMPORTANT: Setting Up Your Signing Certificate

Since keytool isn't available in the current environment, follow these steps to create your keystore:

### Option 1: Using Android Studio (Recommended)
1. Open Android Studio
2. Go to: **Build → Generate Signed Bundle/APK**
3. Select **Create new...** for keystore
4. Fill in the details:
   - Store file: `bysel.jks`
   - Password: `BYSEL@2026`
   - Alias: `bysel_key`
   - Validity (years): `10000`
   - Certificate details:
     - First and Last Name: `BYSEL Trader`
     - Organization Unit: `Trading`
     - Organization: `BYSEL`
     - City: `India`
     - State/Province: `India`
     - Country: `IN`
5. Click **OK** to generate

### Option 2: Using Command Line (Windows)

If you have JDK installed, use keytool:

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

Or on Mac/Linux:

```bash
keytool -genkey -v -keystore bysel.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias bysel_key \
  -keypass "BYSEL@2026" \
  -storepass "BYSEL@2026" \
  -dname "CN=BYSEL Trader,OU=Trading,O=BYSEL,L=India,S=India,C=IN"
```

---

## 📋 Keystore Details

| Field | Value |
|-------|-------|
| **Filename** | `bysel.jks` |
| **Store Password** | `BYSEL@2026` |
| **Key Alias** | `bysel_key` |
| **Key Password** | `BYSEL@2026` |
| **Validity** | 10000 years (until 12026) |

---

## 🔑 Preparing for GitHub Secrets

Once your keystore is created, encode it to Base64:

### Windows PowerShell:
```powershell
$bytes = [System.IO.File]::ReadAllBytes("bysel.jks")
$base64 = [System.Convert]::ToBase64String($bytes)
$base64 | Set-Clipboard
# Then paste in GitHub Secrets
```

### Mac/Linux:
```bash
base64 -i bysel.jks | tr -d '\n' | pbcopy
# Then paste in GitHub Secrets
```

### Or save to file:
```bash
# Windows PowerShell
[System.Convert]::ToBase64String([System.IO.File]::ReadAllBytes("bysel.jks")) | Out-File keystore_base64.txt

# Mac/Linux
base64 -i bysel.jks > keystore_base64.txt
```

---

## ✅ Verify Keystore Creation

List keystore contents:
```bash
keytool -list -v -keystore bysel.jks -storepass "BYSEL@2026"
```

---

## 📝 Location

Place the keystore file at: `c:\Users\sriha\Desktop\Applications\BYSEL\BYSEL\bysel.jks`

---

## 🔒 Security Notes

⚠️ **DO NOT commit `bysel.jks` to Git**
- Add to `.gitignore` (already done)
- Store securely
- Use GitHub Secrets for CI/CD
- Never share publicly

---

## Next Step

After creating the keystore, proceed to **Set GitHub Secrets** with:
- `KEYSTORE_BASE64` - Base64 encoded keystore file
- `KEYSTORE_PASSWORD` - `BYSEL@2026`
- `KEY_ALIAS` - `bysel_key`
- `KEY_PASSWORD` - `BYSEL@2026`
