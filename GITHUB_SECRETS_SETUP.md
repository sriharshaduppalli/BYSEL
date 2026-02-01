# GitHub Secrets Configuration Guide

## 🔑 Required Secrets for BYSEL CI/CD Pipeline

Add these secrets to your GitHub repository to enable automated builds and Play Store releases.

---

## ✅ Step-by-Step: Setting Up GitHub Secrets

### 1. Navigate to Repository Settings
- Go to: `https://github.com/sriharshaduppalli/BYSEL`
- Click **Settings** (gear icon)
- Left sidebar → **Secrets and variables** → **Actions**

### 2. Create Each Secret

#### Secret #1: KEYSTORE_BASE64
- **Name:** `KEYSTORE_BASE64`
- **Value:** Base64-encoded content of `bysel.jks`
- **How to generate:** See [KEYSTORE_SETUP.md](./KEYSTORE_SETUP.md)
- **Used by:** Play Store signing in CI/CD

#### Secret #2: KEYSTORE_PASSWORD
- **Name:** `KEYSTORE_PASSWORD`
- **Value:** `BYSEL@2026`

#### Secret #3: KEY_ALIAS
- **Name:** `KEY_ALIAS`
- **Value:** `bysel_key`

#### Secret #4: KEY_PASSWORD
- **Name:** `KEY_PASSWORD`
- **Value:** `BYSEL@2026`

#### Secret #5: PLAYSTORE_SERVICE_ACCOUNT (Optional, for automated publishing)
- **Name:** `PLAYSTORE_SERVICE_ACCOUNT`
- **Value:** Google Play service account JSON (base64 encoded)
- **Obtain from:** Google Play Console → API & Services → Service Accounts

---

## 📝 What Each Secret Does

| Secret | Purpose | Used In |
|--------|---------|---------|
| `KEYSTORE_BASE64` | Signs APK/AAB with private key | `bysel-playstore.yml` |
| `KEYSTORE_PASSWORD` | Unlocks keystore file | `bysel-playstore.yml` |
| `KEY_ALIAS` | Identifies the signing key | `bysel-playstore.yml` |
| `KEY_PASSWORD` | Unlocks the key | `bysel-playstore.yml` |
| `PLAYSTORE_SERVICE_ACCOUNT` | Authenticates with Google Play | `bysel-playstore.yml` |

---

## 🔗 GitHub Secrets URL

Direct link: `https://github.com/sriharshaduppalli/BYSEL/settings/secrets/actions`

---

## ✨ After Adding Secrets

Once secrets are configured:

1. ✅ Any push to `main` branch triggers `bysel-ci.yml` (tests)
2. ✅ Tagging with `v*.*.*` triggers `bysel-playstore.yml` (build & sign)
3. ✅ Version tags trigger `bysel-release.yml` (version bumping)

---

## 🧪 Verify Secrets Are Working

After setting secrets, create a test tag:

```bash
git tag -a v0.1.0-test -m "Test release"
git push origin v0.1.0-test
```

Monitor at: `https://github.com/sriharshaduppalli/BYSEL/actions`

---

## ⚠️ Security Best Practices

✅ **DO:**
- Use GitHub's secrets UI (values never logged)
- Rotate secrets periodically
- Use separate credentials for test/prod
- Monitor Action runs for suspicious activity

❌ **DON'T:**
- Commit secrets to repository
- Share secrets in issues/PRs
- Use production credentials in test builds
- Log secrets in workflow output

---

## 🐛 Troubleshooting

### Secrets not found in workflow
- Verify secret names match exactly (case-sensitive)
- Check branch is `main` (secrets only on default branch)
- Ensure you have permission to create secrets

### Base64 encoding issues
- Use: `base64 -i bysel.jks` (Mac/Linux)
- Use: PowerShell script (Windows) - see KEYSTORE_SETUP.md
- Ensure no line breaks in Base64 string

### Signing still fails in Actions
- Verify keystore password matches
- Check key alias exists: `keytool -list -keystore bysel.jks`
- Ensure keystore file permissions (should be readable)

---

## 📞 Need Help?

See workflow files:
- Build & Test: `.github/workflows/bysel-ci.yml`
- Signing & Release: `.github/workflows/bysel-playstore.yml`
- Version Management: `.github/workflows/bysel-release.yml`
