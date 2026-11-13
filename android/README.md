# Rollcall Android app

Rollcall is a Java Android app that reads MyCamu attendance through its authenticated in-app WebView and presents it in a native Torch Genesis dashboard. The selected baseline also includes KCT portal views, local snapshot persistence, reminders, notifications, widgets, Firebase account/snapshot integration and a verified APK update flow. These integrations are source-present; external service and device behavior is not implied to be verified by a successful build.

## Project structure

- `app/src/main/java/com/iniexe/rollcall/MainActivity.java` coordinates onboarding, MyCamu/KCT WebViews, synchronization, local preferences, screen navigation and UI state.
- `app/src/main/java/com/iniexe/rollcall/DashboardView.java` renders the native dashboard and its Home, Courses, Week, Lab and Academics experiences.
- `app/src/main/java/com/iniexe/rollcall/Attendance.java` owns attendance calculations.
- `app/src/main/java/com/iniexe/rollcall/LiveSnapshotStore.java` validates and persists attendance, timetable, leave and class-history snapshots used by UI, workers and widgets.
- Other Java files implement WorkManager jobs, reminders, event notifications, home-screen widgets, Firebase/cloud integration and the updater.
- `app/src/main/res/` contains Android resources and widget layouts; `app/src/main/assets/fonts/` contains bundled typography and license notices.
- `app/src/test/` contains JVM tests. The selected baseline currently has no `src/androidTest/` instrumentation suite.
- `TORCH_GENESIS_DESIGN.md` is the canonical design reference. The HTML prototype is a reference only, never the Android runtime.

The project uses a single Android `:app` module. Its configured identity is `com.iniexe.rollcall`, compile/target SDK 35, min SDK 23, versionCode 24 and versionName 1.0.23.

## Build and test

Install Java 17, Android SDK Platform 35 and Android Build Tools. From this directory:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

For a custom SDK location, set `ANDROID_HOME` and `ANDROID_SDK_ROOT` for the command. Do not commit `local.properties`. The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

To install on a chosen device, first inspect `adb devices -l`, then specify its serial:

```sh
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Only use this on a test device. Do not install over a production app as part of routine verification.

## Release signing

Release signing is configured outside the repository at `~/.config/rollcall/release-signing.properties`; keep the keystore and password file private. Release tasks fail when signing is not configured. Do not alter signing or version identity as part of a cleanup. Because versionCode 24 is below the later hybrid app's versionCode 25, do not publish an upgrade build from this baseline until the release lineage is resolved.

## Verification boundaries

The provider endpoints are undocumented. Unit tests and `assembleDebug` do not verify live MyCamu/KCT accounts, Firebase, FCM, Android notification permission, alarms, background scheduling, launcher widget behavior, or APK download/install. See [`../docs/architecture/FUNCTIONAL_INVENTORY.md`](../docs/architecture/FUNCTIONAL_INVENTORY.md) for status and follow-up coverage.
