# Rollcall

Rollcall is a Java Android attendance companion with an optional browser dashboard. The phone keeps the Torch Genesis design language, connects to MyCamu and KCT through their web portals, and can sync a read-only snapshot to the Cloudflare-hosted Web app.

## Project direction

- Android app: Java, one `:app` module, custom native Views.
- Web app: static browser dashboard plus Cloudflare Pages Functions and D1-backed account/snapshot API.
- UI: Torch Genesis palette and typography, documented in [`android/TORCH_GENESIS_DESIGN.md`](android/TORCH_GENESIS_DESIGN.md).
- Data: local attendance snapshot; MyCamu/KCT account and provider data remain subject to their existing web sessions and endpoints.
- Phone/Web API: [`docs/architecture/API_CONTRACT.md`](docs/architecture/API_CONTRACT.md) is the shared wire-contract reference.
- Historical Compose migration work remains on its existing branch and is not part of this Java development baseline.

The selected Java Torch baseline is versionCode 25 / versionName 1.0.24. Check [`docs/architecture/FUNCTIONAL_INVENTORY.md`](docs/architecture/FUNCTIONAL_INVENTORY.md) for source-level feature coverage and verification limits before changing integrations.

## Build and verify

Use Java 17 and install Android SDK Platform 35 / Build Tools. From the repository root:

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. For a nonstandard SDK path, set `ANDROID_HOME` and `ANDROID_SDK_ROOT` in the shell. Do not commit `local.properties` or signing material.

Read [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`AGENTS.md`](AGENTS.md) before making changes. App architecture, test limits and known compatibility concerns are recorded in [`docs/architecture/FUNCTIONAL_INVENTORY.md`](docs/architecture/FUNCTIONAL_INVENTORY.md).

For the browser dashboard and Worker tests, see [`web/README.md`](web/README.md) and run `cd web && npm test`.

## Data and release safety

Rollcall uses the MyCamu WebView session to read attendance and stores the resulting snapshot locally. The provider endpoints are undocumented and may change. KCT, Firebase, notifications, background jobs, widgets, and APK update/install flows require device or service verification; a local build does not validate them.

Do not change the package identity or release signing configuration during cleanup. The checked-in V1 APK predates the current app version; verify artifact version and signing continuity before publishing an upgrade.
