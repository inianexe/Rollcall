# Rollcall update notifications

Rollcall subscribes to the `app_updates` Firebase Cloud Messaging topic after notification permission is granted. When Android receives an update announcement, the app checks GitHub Releases and opens the existing themed update screen from the notification. The APK still goes through Android's installer and confirmation.

## One-time Firebase setup

1. Register the Android app using package ID `com.iniexe.rollcall`.
2. Save the downloaded `google-services.json` in `android/app/`.
3. In Google Cloud IAM, create/select a service account for Rollcall and grant it the **Firebase Cloud Messaging API Admin** role. Generate its JSON key from Firebase Console → Project settings → Service accounts. Keep the file private; it must never be added to the Android app or repository.
4. In the GitHub repository, add an Actions secret named `FIREBASE_SERVICE_ACCOUNT_JSON` and paste the complete service-account JSON as its value.

The `google-services.json` file contains public project configuration identifiers. The service-account JSON contains a private key and is the credential that must remain secret.

## Publishing

The repository's `publish-v1-apk.yml` workflow runs when a signed APK named `Rollcall V1.apk` is pushed to `main`. It requires the APK's package, version code/name, and release certificate to match the checked-in Android source and known signing identity. It creates a new `v<versionName>` release with versioned APK/checksum names and announces that version to FCM. Existing release tags and assets are never overwritten or removed. Devices ignore announcements whose `versionCode` is not newer.

The release notes contain hidden version metadata. The app uses it for its GitHub polling fallback, which scans the ten newest public releases and skips drafts and prereleases.

## Verify delivery

- Install the signed APK built with this Firebase configuration.
- Allow notifications during onboarding (or later in Android settings), then launch Rollcall once so it subscribes to `app_updates`.
- Publish the exact source-version release-signed APK to the repository root as `Rollcall V1.apk`. The workflow sends an FCM announcement to all `app_updates` subscribers; tapping it opens the update screen. A release tag is one-time; if it already exists, create a new app version instead of replacing the release.

FCM delivery depends on Google Play services and Android notification settings. The app's periodic GitHub release check remains the fallback if a push is missed.
