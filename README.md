# Rollcall

Rollcall is a native Android attendance companion built with Java and Android Views. The app keeps the Torch Genesis design language and connects to MyCamu and KCT through their web portals.

## Project direction

- Android app: Java, one `:app` module, custom native Views.
- UI: Torch Genesis palette and typography, documented in [`android/TORCH_GENESIS_DESIGN.md`](android/TORCH_GENESIS_DESIGN.md).
- Data: local attendance snapshot; MyCamu/KCT account and provider data remain subject to their existing web sessions and endpoints.
- Historical Compose migration work remains on its existing branch and is not part of this Java development baseline.

The selected Java Torch baseline is versionCode 24 / versionName 1.0.23. Check [`docs/architecture/FUNCTIONAL_INVENTORY.md`](docs/architecture/FUNCTIONAL_INVENTORY.md) for source-level feature coverage and verification limits before changing integrations.

## Build and verify

Use Java 17 and install Android SDK Platform 35 / Build Tools. From the repository root:

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. For a nonstandard SDK path, set `ANDROID_HOME` and `ANDROID_SDK_ROOT` in the shell. Do not commit `local.properties` or signing material.

Read [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`AGENTS.md`](AGENTS.md) before making changes. App architecture, test limits and known compatibility concerns are recorded in [`docs/architecture/FUNCTIONAL_INVENTORY.md`](docs/architecture/FUNCTIONAL_INVENTORY.md).

## Data and release safety

Rollcall uses the MyCamu WebView session to read attendance and stores the resulting snapshot locally. The provider endpoints are undocumented and may change. KCT, Firebase, notifications, background jobs, widgets, and APK update/install flows require device or service verification; a local build does not validate them.

Do not change the package identity or release signing configuration during cleanup. The Java Torch branch's versionCode is lower than the later hybrid application version, so verify release lineage before creating an upgrade APK.
