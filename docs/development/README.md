# Development and release workflow

## Where changes go

- Android app, Java Views, manifest, assets and Android tests: `android/app/`.
- Cloudflare-hosted browser interface and service worker: `web/`.
- Pages Functions API routes and API tests: `web/functions/`.
- D1 schema changes: ordered files in `web/migrations/`; describe wire-visible effects in `docs/architecture/API_CONTRACT.md`.
- Shared setup, feature status and privacy: root `AGENTS.md`, `CONTRIBUTING.md`, `PRIVACY.md`, and `docs/architecture/`.

## Local checks

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest

cd ../web
npm test
node --check app.js
node --check cloud-auth.js
node --check _worker.js
node --check functions/_api.js
```

These commands do not deploy Cloudflare code or apply D1 migrations. Test migrations only against disposable local databases; do not point local migration commands at production.

## Release signing and APK publishing

Android release signing is configured locally through `~/.config/rollcall/release-signing.properties` and its referenced password file. Never put these files or their contents in the repository. Releases must preserve package `com.iniexe.rollcall`, the established signer certificate, and a strictly increasing `versionCode`.

The existing GitHub workflow listens for changes to the root `Rollcall V1.apk`, validates package/version/signer against source and creates a versioned GitHub Release plus an FCM update notification. That checked-in artifact is version 1.0.7 / code 8 while source is 1.0.24 / code 25; it is historical and the validation is expected to reject it. A replacement CI signing path has not been proven with the release key. Keep the APK and workflow until a release procedure is tested end to end with an owner-controlled signing secret and a disposable candidate version. Do not run the publish workflow as part of ordinary CI.

## Repository branch baseline

The repository consolidation is complete: `main` is the Java + Web baseline. Authentication work is developed from that baseline on the isolated `feature/firebase-auth-v2` branch. Historical PR and Compose-transition notes from earlier repository states are superseded. Do not merge this feature branch, deploy Pages/Workers, or apply remote D1 migrations as part of local verification.
