# Rollcall development guidance

Rollcall is a native Android application written in Java. Keep application UI in Android Views and custom Java `View` classes, preserve the Torch Genesis design system, and keep the existing single `:app` module unless a concrete requirement justifies changing it. Do not add Compose, Kotlin UI, React, or a WebView dashboard.

## Android workflow

1. Install the Android SDK for the project's `compileSdk` and Java 17 for Gradle/Android builds.
2. From `android/`, run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
3. For a local SDK outside the environment defaults, set `ANDROID_HOME` and `ANDROID_SDK_ROOT` for the command. Do not commit `local.properties`.
4. Install only when explicitly requested and after selecting the intended ADB serial: `adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk`.

Declare third-party libraries in `android/app/build.gradle`. Run `./gradlew :app:dependencies` to inspect the resolved app dependency graph.

## Existing application boundaries

- `MainActivity` coordinates startup, MyCamu and KCT WebViews, synchronization, persistence-backed preferences, navigation, and settings.
- `DashboardView` renders the native Torch Genesis dashboard and its page interactions.
- `Attendance` owns attendance calculations; preserve its behavior when changing presentation code.
- `LiveSnapshotStore` owns the local attendance/timetable/history snapshot shared by the app, workers, and widgets.
- WorkManager workers, receivers, notification helpers, and app widgets provide background and Android system integrations.
- `web/app.js` owns the browser dashboard and local browser data; `web/cloud-auth.js` owns Firebase sign-in and the read-only cloud session UI.
- `web/functions/_api.js` owns Pages API routes; ordered SQL migrations live in `web/migrations/`; API tests live in `web/functions/_api.test.mjs`.
- `docs/architecture/API_CONTRACT.md` is the shared Android–Web wire contract. Update it when request, response, authentication, or migration behavior changes.

Preserve application identity, release signing behavior, data formats, provider WebView sessions, and update verification unless the task explicitly changes them. Do not install on a USB-connected device or publish a release as part of ordinary verification.

## Change workflow

Read affected callers and tests before editing. Keep refactors small, run the JVM tests and debug build after meaningful changes, and state device-only or live-service checks that remain unverified. Never commit credentials, keystores, generated APKs, or machine-local SDK configuration.

## RollCall migration workflow

1. Read this file and only the architecture notes relevant to the requested screen or integration.
2. Characterize current tested behavior before changing it.
3. Load only skills relevant to the task and reuse existing components, fixtures, and test infrastructure.
4. Keep the native Java UI architecture unless a scoped project decision explicitly selects an existing Compose migration branch.
5. Work in small slices; run targeted tests during implementation and the required regression suite before completion.
6. Preserve attendance calculations, provider sessions, account boundaries, persistence, notifications, and update verification.
7. Do not install over the user's production app or alter production data/settings as routine verification.
8. Use the user's physical phone for device checks when authorized; do not use an emulator for RollCall device verification.
9. Avoid unnecessary dependency or framework changes.
10. Report verified results, remaining device/live-service checks, and known risks. Keep commits reviewable and do not merge or deploy without authorization.
