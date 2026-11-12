# Phase 4 verification record

Branch `codex/rollcall-torch-ui`, starting commit `95d621b`, app 1.0.24.

## Build and tests

| Check | Result | Notes |
|---|---|---|
| `testDebugUnitTest` | PASS | Includes course UI-state mapper tests and existing attendance characterization tests. |
| `assembleDebug` | PASS | No production install performed. |
| `assembleRelease` | PASS | Local signing setup present; no release published. |
| `assembleDebugAndroidTest` | PASS | Test APK compiled. |
| `connectedDebugAndroidTest` | PASS | `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`: all 12 tests passed on a newly created isolated Android 16 / API 36 x86_64 AVD. The physical phone was not targeted. |
| `git diff --check` | PASS | |

## Feature matrix

| Feature | Status | Evidence / limit |
|---|---|---|
| Attendance safe-absence/recovery calculations | BUILD-VERIFIED | Existing `Attendance` remains authoritative; mapper tests prove the card receives supplied results. Full Android UI parity after the card migration is NOT VERIFIED. |
| Course card state mapping | VERIFIED | JVM fixtures cover mapped attendance values, status, guidance, expanded state and unavailable counts. |
| Course card rendering and interactions | VERIFIED | Fixed-fixture Compose tests cover values, unknown counts, long content, expansion, planner action and theme update on the isolated AVD. |
| Five-tab DashboardView navigation | VERIFIED | Host test traverses all five native tabs, refreshes mapped course data, expands guidance and opens What If on the isolated AVD. |
| Home / latest class / upcoming exam | PARTIALLY VERIFIED | Previous phase isolated component tests passed. Phase 4 did not verify MainActivity data propagation. |
| Theme preference, Java screen repaint and Activity recreation | NOT VERIFIED | Previous isolated light/dark component repaint passed; full preference lifecycle not exercised. |
| Physical phone signature conflict | DIAGNOSED | Installed/debug APK IDs both `com.iniexe.rollcall`; installed version 1.0.23 (code 24) signature SHA-256 `a1d35509…c703ba2`, debug version 1.0.24 (code 25) SHA-256 `c51624e4…8747da`. Instrumentation targets `com.iniexe.rollcall`. Android rejected the mismatch; no uninstall/data clearing was attempted. |
| MyCamu/KCT authentication, consent and sessions | NOT TESTED | No live accounts or credentials used. |
| Live/cached/empty/error sync propagation | NOT TESTED | No portal or production snapshot fixtures used. |
| Timetable, exams, marks, GPA, leave/OD, What If | NOT TESTED | Existing Java implementations unchanged. |
| Firebase, FCM, notification permission, reminders, workers and widgets | NOT TESTED | No device/service integration run. |
| Update notifications/download/install | NOT TESTED | No update action performed. |
| Large font, narrow-display screenshot review | NOT TESTED | No screenshot baseline captured. |

## Performance and artifact size

| Metric | Phase 3 artifact | Phase 4 artifact | Difference | Configuration |
|---|---:|---:|---:|---|
| Release APK file size | 12,395,490 bytes | 12,411,861 bytes | +16,371 bytes | Same project release variant; artifact size only. |

Cold/warm startup, frame timing, scroll performance, main-thread stalls, memory use and allocation counts are NOT MEASURED. No runtime-performance claim is inferred from APK size.

## Scope

The Phase 4 change moves only the grouped Courses card into Compose. Java still owns attendance calculations, group ordering, filtering, screen navigation and expansion state. Authentication, persistence, sync, notifications, background work, widgets, update flows and all other screens were not changed.
