# Phase 5 — Courses screen migration verification

Date: 2026-11-12
Branch: `codex/rollcall-torch-ui`
Starting commit: `880e792`

## Scope

The Courses page presentation now uses `CoursesScreenView` → `CoursesScreen` (`LazyColumn`) → the existing `CourseCard`. The Java dashboard still supplies authoritative attendance values and owns grouping, sorting, filters, expansion, page navigation and What If routing. No attendance formulas, storage, authentication, integrations or other dashboard screens were changed.

## Behavior matrix

| Input / action | Expected visible result | Existing owner / callback | Verification |
|---|---|---|---|
| Course snapshot with theory and matching lab code | One group card with theory and lab metrics | Java `groupedCourses()` | Dashboard host test: paired metrics shown together |
| Lab code without a matching theory code | Standalone lab group | Java `groupedCourses()` | Dashboard host test: lab-only card shown |
| Group priorities and lab percentages | Labs and groups remain in the existing ascending percentage / minimum-group-priority order | Java `groupedCourses()` / `CourseGroup.priority()` | Dashboard host test confirms the visible lower-priority lab-only card precedes a paired group |
| Search by subject text | Matching groups remain; matching is case-insensitive substring over each group's subject names and codes | Compose input → `Actions.onQuery` → Java query state → state mapping | Dashboard host test searches subject text under Below Target |
| Search by course code | Matching groups remain | Same Java callback and matching rule | Dashboard host test searches `EE201L` |
| All Courses filter | Groups with a matching subject remain | Compose filter callback → `Actions.onFilter(false)`; Java applies group rule | Dashboard host test |
| Below Target plus search | A group remains only when some member matches the query and some member is below target, even if those are different members | Existing Java `CourseGroupRow.matches()` rule retained in state mapping | Dashboard host test covers a paired theory/lab group and excludes an on-target-only group |
| No matching result | Existing no-results message; no sync action | Java state mapping | Dashboard host test |
| No course records | Existing first-sync or empty-response message; tap routes to sync or reconnect according to the existing session state | `Actions.onSync` / `Actions.onPortal` | Dashboard host test covers both routes |
| Tap course heading or metric | Existing course code is sent to Java expansion owner | `Actions.onCourse` → `MainActivity.expandedCourses` → render | Existing dashboard host test verifies callback and expanded guidance refresh |
| Tap What If action | Planner opens with the chosen course code | `openPlannerFor(code)` | Existing dashboard host test |
| Snapshot refresh | New Java state replaces Compose state without rebuilding the page; search text is only reset when the authoritative query changes | `DashboardView.update()` | Existing dashboard host test updates course data and sees refreshed title |
| Select another dashboard tab / open OD details | Compose Courses page hides and returns through the existing Java page shell | `showPage`, `openOdDetails`, `closeOdDetails` | Five-tab host test passes |

## Verification results

| Check | Result |
|---|---|
| `./gradlew testDebugUnitTest` | PASS — 16 JVM tests |
| `./gradlew assembleDebug` | PASS |
| `./gradlew assembleRelease` | PASS |
| `./gradlew assembleDebugAndroidTest` | PASS |
| `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest` | PASS — 14/14 instrumentation tests |
| `git diff --check` | PASS |
| Emulator | `rollcall_phase4b`, Android 16 / API 36, x86_64, ADB serial `emulator-5554` |
| Physical phone | NOT TARGETED; the phone's existing Rollcall installation and data were left untouched |
| Release APK debug-fixture check | PASS — `rollcall_compose_test` is absent from the release APK listing |

Instrumentation comprises 11 `ComposeComponentsTest` tests and 3 `DashboardViewIntegrationTest` tests. The latter host the real Java `DashboardView` in a test `ComponentActivity` and exercise Java-to-Compose state and callback paths. Tests use deterministic fixtures; they do not sign in to MyCamu or KCT.

## Artifact and performance

- Phase 4 release APK: 12,411,861 bytes.
- Phase 5 release APK: 12,428,250 bytes.
- Difference: +16,389 bytes.
- Debug app cold launch on the isolated AVD: 1,558 ms via `am start -W`; one smoke measurement only, not a release benchmark or Courses screen timing.
- Startup, memory, search latency, frame timing, scroll smoothness and recomposition counts: NOT MEASURED. APK size is not a runtime performance measurement.

## Limits / remaining verification

- Production `MainActivity` Activity recreation, persisted theme restoration and process death were not exercised. The dashboard test uses `ComponentActivity` with the real `DashboardView` and a fixed `AppPalette`.
- Live MyCamu/KCT data refresh, authentication, Firebase, notifications, workers, widgets and updater flows were NOT TESTED; their production code was not changed.
- Large font-scale and narrow-screen screenshot review, TalkBack on physical hardware and visual screenshot comparison were NOT TESTED.
- Attendance percentages, Can Miss, Must Attend, target status and guidance continue to be generated by the unchanged Java/MainActivity/`Attendance` path. Test fixtures validate rendering and callbacks, not live academic-data parity.
- The Courses screen is migrated; the other four dashboard tabs and the wider application remain predominantly Java Views. RollCall is still a partial Compose migration.
