Signing setup

1. Create `keystore.properties` in the `android/` directory (do not commit).
   Use `keystore.properties.example` as a template.

   Example `keystore.properties`:

   storeFile=keystore.jks
   storePassword=supersecret
   keyAlias=upload
   keyPassword=supersecret

2. Place your keystore file (e.g., `keystore.jks`) in the project or provide an absolute path in `storeFile`.

3. Build release bundle:

```powershell
Push-Location .\BYSEL\android
.\gradlew.bat :app:bundleRelease --warning-mode all
Pop-Location
```

4. CI recommendation: store keystore contents and secrets in CI secret store; write a small step to create `keystore.properties` from secrets at build time or set env vars `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

5. Security: never commit `keystore.properties` or keystore file into source control. Add `android/keystore.properties` and your keystore filename to `.gitignore`.

CI (GitHub Actions) example
----------------------------
You can store your keystore as a base64-encoded secret (`KEYSTORE_BASE64`) and related secrets in GitHub (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). The workflow at `.github/workflows/android-release.yml` will decode the keystore, write `android/keystore.properties`, and run the Gradle release bundle. It uploads the signed AAB as a workflow artifact.

Required repository secrets:
- `KEYSTORE_BASE64` (base64 of the keystore file) OR supply your keystore in other secure storage and set `KEYSTORE_PATH` in CI.
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Note: keep these secrets in the GitHub repository or organization secret store. The workflow triggers on `push` to `main` and on manual `workflow_dispatch`.
