# Contributing

Keep changes focused and compatible with the native Java Android app. Preserve Torch Genesis colors, typography, navigation, and accessibility unless the task calls for a design change.

Before a change, inspect the current branch and worktree, read the affected Java classes and their callers, and check related tests. For Android code, verify with:

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Add or update a focused JVM test for changed calculation or parsing behavior. Live MyCamu/KCT access, Firebase, notifications, alarms, widgets, and APK installation need device or service verification and must not be described as tested when only a local build ran.

For Web or Android↔Web changes, update `docs/architecture/API_CONTRACT.md` and run:

```sh
cd web
npm test
node --check app.js
node --check cloud-auth.js
node --check _worker.js
node --check functions/_api.js
```

Do not apply Cloudflare remote migrations or deploy from a feature branch. Review migration effects and preserve existing D1 data before an owner schedules a production rollout.

Keep credentials and signing material outside Git. Do not change package identity, signing configuration, version codes, or release publishing behavior without an explicit task.
