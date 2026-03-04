FCM + WorkManager setup for BYSEL Android app

Steps to enable push notifications and local fallback polling:

1) Add `google-services.json`
- Download from your Firebase project and place it at `android/app/google-services.json`.

2) Add Google Services plugin (root project)
- In your root `build.gradle` (or `build.gradle.kts`) add the classpath:

  buildscript {
    dependencies {
      classpath 'com.google.gms:google-services:4.4.0'
    }
  }

- Or for `build.gradle.kts`:
  - Add `classpath("com.google.gms:google-services:4.4.0")` to `buildscript` dependencies.

3) Apply plugin in the app module (if using Groovy):

  apply plugin: 'com.google.gms.google-services'

- If using `build.gradle.kts`, add `apply(plugin = "com.google.gms.google-services")` after the `plugins` block.

4) Dependencies
- `android/app/build.gradle.kts` already includes:
  - `com.google.firebase:firebase-messaging:23.2.1`
  - `androidx.work:work-runtime-ktx:2.8.1`

5) AndroidManifest
- `MyFirebaseMessagingService` is registered in `AndroidManifest.xml`.
- Ensure `POST_NOTIFICATIONS` permission is requested at runtime on Android 13+.

6) Server-side
- Your server should store FCM tokens and send data payloads like:
  {
    "to": "<fcm-token>",
    "data": {"symbol":"RELIANCE","price":"2510.5","alertId":"42","alertType":"ABOVE","threshold":"2500"}
  }

7) Testing
- Use the Firebase Console to send a test message to your app.
- WorkManager polling runs every 15 minutes (minimum) as a fallback; check logs for `BYSEL-Worker` tag.

8) Notes
- Add notification permission request UI for Android 13+ before sending notifications.
- Review `AlertsManager` and `AlertWorker` to adjust polling interval and retry policies.

