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

## GitHub branch transition

The verified Java + Web branch is `refactor/rollcall-java-torch`. PR #13 to `main` is conflicting because `main` contains a separate Compose migration history. Do not merge the PR, force-push, rewrite history, or switch the default branch automatically.

Before promotion, maintainers should:

1. Review this branch and the current `main` independently; decide whether the Java app replaces the Compose history or whether any unique `main` commits must be ported.
2. Prepare a merge/reconciliation commit on a temporary branch, preserving both histories. Review the full resulting diff and run Android/Web CI.
3. Review Cloudflare Pages production-branch/build settings, Worker secrets, D1 migration state, and GitHub Actions tag/publish behavior without deploying or migrating production data.
4. Check every open PR's base branch and unique content; retarget or close only after review with its author/maintainers.
5. Confirm release signing continuity, update-client asset expectations, and monotonic version codes using a non-production candidate. Do not use the historical root APK.
6. Tell collaborators the selected transition commit and how to rebase or fetch it; confirm local changes are backed up before they switch.
7. Only after approval, update `main` through a reviewed PR and then change the repository default branch. Verify links, Pages deployment, protection rules and release actions afterward.

As of this branch, open PR #13 is dirty/conflicting; drafts #3–#9 have distinct historical proposal content and remain untouched. Repository branch protection is not configured. The PR workflow now runs for documentation-only changes as well.
