# Java Torch baseline: feature inventory

## Baseline and evidence

- Selected starting ref: `origin/design/torch-genesis-exact-prototype` at `f20a982ea8f49c769abdc4d450d46b5930da8f4b`.
- Chosen because it is the verified native Java Torch Genesis implementation and retains `android/TORCH_GENESIS_DESIGN.md`. The compared `design/torch-genesis-native-polish`, `design/torch-genesis-exact-app`, and `design/torch-genesis-exact-app-wide` refs modify the same core Java UI and remove that canonical design document; `redesign/torch-genesis-reference-exact` adds an HTML/WebView runtime and was excluded.
- App identity is `com.iniexe.rollcall`; baseline is versionCode 24 / versionName 1.0.23. Preserve that identity and treat the versionCode difference from the later hybrid app (25) as a release-compatibility decision, not a cleanup opportunity.
- Source presence was checked in the selected baseline and against the original Java backup (`00b3f8eb07809d4686152f54e158c87fa28999e7`) and the current local hybrid workspace. The hybrid workspace contains extensive uncommitted changes and was not copied or modified.
- Verification: JVM tests, debug APK, and debug instrumentation APK are built in CI. Java unit coverage currently includes 16 tests across six classes; the AndroidJUnit4 navigation fixture exercises navigation, schedule/history switching, timetable, and attendance history on a temporary emulator.

Status terms: **Present** means the selected branch contains an implementation; **Build/test verified** means included in the successful local Gradle command; **Unverified** means runtime/device/provider behavior was not exercised. This is not a claim of full production parity.

## Feature parity matrix

| Area | Original Java backup | Selected Java Torch baseline | Latest local hybrid work | Verification / follow-up |
|---|---|---|---|---|
| Attendance model, percentage and skip/recovery guidance | Present in `Attendance` | Present in `Attendance`; JVM tests included | Present, with edits to `Attendance` | Build/test verified; expand edge-case characterization before changing calculations |
| MyCamu authentication and attendance sync | Present in `MainActivity` WebView flow | Present in `MainActivity`, with local snapshot and foreground refresh | Present with modified sync and additional background integrations | Source only; live login, cookies, endpoint and parsing unverified |
| Course list, search/filter and attendance planner | Present in native dashboard | Present in `DashboardView` | Present in native dashboard | Source/build only; UI behavior not emulator-tested |
| Timetable, attendance history, leave/OD | Timetable model and portal parsing present | Native timetable/history/leave views, persisted snapshot, reminder scheduling and event notifications present | Present with expanded synchronization/integration classes | Source/build only; alarms and provider records unverified |
| KCT login, academic results, GPA and exams | No KCT implementation found in backup source list | KCT portal and academic/exam presentation present | Present | Source/build only; KCT login and live result formats unverified |
| Onboarding, target and theme preferences | Basic startup/dashboard behavior | Native onboarding, consent, target, palette and light/dark themes | Present with UX and state edits | Source/build only; lifecycle and persistence UI behavior not instrumented |
| Notifications and class reminders | No matching worker/receiver/widget sources in backup | Present: receivers, scheduler, notification helpers and WorkManager sync | Present; related behavior exists in the dirty hybrid workspace | Build verified; notification permission, alarms and background restrictions unverified |
| Home-screen widgets | No widget sources found in backup | Present: today, week, streak, reminder and attendance widgets | Present | Build verified; launcher-host behavior unverified |
| Firebase account, FCM and cloud snapshots | No matching integration sources found in backup | Present: account client, snapshot payload/worker and update messaging | Present | Build verified; Firebase credentials, account flow, FCM delivery and server contract unverified |
| GitHub update check, APK verification and installer | No updater sources found in backup | Present: release notifier, `UpdateActivity`, digest/signer checks and `FileProvider` | Present | Source/build only; download, signature and install path unverified |
| Navigation, lifecycle, offline and accessibility | Native Activity/View flow | Native Java Activity/View flow and cached local snapshot | Present with migration regression tests in historical Compose branch | Test-only Activity supports navigation, timetable, and history instrumentation; lifecycle/offline behavior still needs device coverage |
| Compose/Kotlin UI | None | None in `app/src/main/java` (all Kotlin UI avoided) | Experimental Kotlin files exist in the dirty hybrid workspace and historical migration branch | Intentionally excluded from this Java-native branch |

## Current Java responsibilities

- `MainActivity.java` coordinates onboarding, WebViews, authentication, snapshot refresh, parsing, preferences, dashboard state and navigation. It remains high-coupling and is the main refactoring candidate, but should only be split behind characterization tests.
- `DashboardView.java` owns the Torch Genesis native dashboard, its five-page navigation, course planner, timetable/history/OD and academic presentation. Keep presentation changes here and calculations in shared model/helper code where already established.
- `Attendance.java` is the attendance calculation model.
- `AcademicRecords.java` owns KCT grade-point and semester-label parsing used by the Academics presentation; it has JVM regression tests.
- `LiveSnapshotStore.java` validates and persists attendance, timetable, leave and class-history data shared by the Activity, workers and widgets.
- Workers, receivers, notifications and widgets live as focused Java classes in the app package. The project is one Android app module; there is no separate data, domain, or UI module.

## Known limits and next steps

1. Add fixture-driven tests for `Attendance`, MyCamu/KCT parsing and snapshot merges before extracting code from `MainActivity` or `DashboardView`.
2. Expand Android instrumentation coverage to lifecycle and permission-dependent behavior; current `src/androidTest` coverage is limited to navigation, timetable, and attendance history.
3. Verify live MyCamu/KCT, Firebase, FCM, WorkManager, alarms, widgets and updater flows only with dedicated test accounts/devices.
4. Resolve release lineage before publishing: the Java Torch baseline uses versionCode 24 while the later app reached versionCode 25. Never lower the installed app's versionCode for an upgrade.
5. Engineering notes live in the existing Obsidian vault at `/home/inian/Projects/Rollcall-2.0/genesis-project`; the Java restoration log and task tracker are updated there. The vault remains in its separate worktree and is not included in this branch.
