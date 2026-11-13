# Rollcall development guidance

Rollcall is a native Android application written in Java. Keep application UI in Android Views and custom Java `View` classes, preserve the Torch Genesis design system, and keep the existing single `:app` module unless a concrete requirement justifies changing it. Do not add Compose, Kotlin UI, React, or a WebView dashboard.

## Android workflow

1. Install the Android SDK for the project's `compileSdk` and Java 17 for Gradle/Android builds.
2. From `android/`, run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
3. For a local SDK outside the environment defaults, set `ANDROID_HOME` and `ANDROID_SDK_ROOT` for the command. Do not commit `local.properties`.
4. Install only when explicitly requested and after selecting the intended ADB serial: `adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`.

Declare third-party libraries in `android/app/build.gradle`. Run `./gradlew :app:dependencies` to inspect the resolved app dependency graph. This project does not currently configure `rosdep` or `colcon`; those are not Android build tools.

## Existing application boundaries

- `MainActivity` coordinates startup, MyCamu and KCT WebViews, synchronization, persistence-backed preferences, navigation, and settings.
- `DashboardView` renders the native Torch Genesis dashboard and its page interactions.
- `Attendance` owns attendance calculations; preserve its behavior when changing presentation code.
- `LiveSnapshotStore` owns the local attendance/timetable/history snapshot shared by the app, workers, and widgets.
- WorkManager workers, receivers, notification helpers, and app widgets provide background and Android system integrations.

Preserve application identity, release signing behavior, data formats, provider WebView sessions, and update verification unless the task explicitly changes them. Do not install on a USB-connected device or publish a release as part of ordinary verification.

## Change workflow

Read affected callers and tests before editing. Keep refactors small, run the JVM tests and debug build after meaningful changes, and state device-only or live-service checks that remain unverified. Never commit credentials, keystores, generated APKs, or machine-local SDK configuration.
