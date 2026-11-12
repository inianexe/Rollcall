# Compose migration tracker

## Checkpoint

- Baseline branch: `codex/rollcall-torch-ui`
- Baseline commit: `2c21ca0` (Rollcall 1.0.24, code 25)
- Foundation commit: `7763bda`; Home exam card commit: `5683bfb` (pushed to the migration branch)
- Phase 4 starts from `95d621b` on `codex/rollcall-torch-ui`.
- Prior rollback: `design-review/rollback/20261112-before-compose-migration` (kept intact)
- This phase checkpoint: `design-review/rollback/20261112-pre-lottie-compose-foundation` (APK and source archive)

## Completed

- Compose build support and `ComponentActivity` interop.
- Home hero identity/calendar ticket, Home upcoming-exam card, and latest-class attendance summary in Compose, hosted by narrow Java View bridges inside the existing Java dashboard.
- Upcoming exam date, countdown, visibility, session highlighting and accessibility text remain prepared by the existing Java dashboard logic; Compose only displays those values.
- Functional inventory and architecture/design/animation notes.
- Java `AppPalette` is now the authoritative palette; its semantic mapping supplies Compose color tokens for light and dark appearances. The Java theme preference and Activity recreation flow remain unchanged.
- Central Compose typography, spacing, shape and motion tokens based on the current Rollcall Signal palette.
- Immutable `HomeHeroIdentityState` and a thin `AbstractComposeView` bridge.
- Official Lottie Compose dependency and lifecycle/visibility-aware local-resource adapter. A per-play latch prevents duplicate one-shot completion callbacks. No Lottie asset ships in release; a small original dot animation exists only under debug resources for instrumentation tests.
- Attendance characterization tests expanded for zero, exact/above/below 75%, full attendance, alternate targets, rounding, parser/missing-count behavior, and invalid constructor counts. Production calculation code is unchanged.
- Compose previews cover Home identity, upcoming exam and latest class in light/dark and long-content states where relevant.
- Instrumentation tests cover Home components, empty exam, long content, accessible descriptions, live theme updates, state updates, card disposal/recreation, and Lottie load/pause/play/finish/remount/error behavior.
- One grouped Courses card is now rendered by Compose inside the existing Java `DashboardView`; Java retains filtering, grouping, navigation, expansion state and source palette selection.
- The current Java render mapping now supplies the same Can miss / Must attend values previously calculated while constructing the Java card. Compose only renders those strings, percent, status and progress color.
- Phase 5 migrates the complete Courses page presentation to Compose: header/summary, search, filters, empty states and grouped course cards in a single `LazyColumn` hosted directly in the Java page shell. Java continues supplying data, grouping/sort/filter results, calculations, expanded state and navigation callbacks.
- The five-tab shell now stores page content as `View[]`; the Home, Week, What If and Marks pages remain their original Java `ScrollView`s. Courses is a full-height Compose sibling, not a nested scroll container. OD detail return, tab visibility and page-entry transitions use that shared page array.
- The Courses search is controlled by the existing Java query state. The bridge reports search-field bounds so the dashboard's horizontal tab-swipe recognizer does not take over a gesture that starts inside the search control.
- Added DashboardView-host tests for paired lecture/lab and lab-only groups, priority order among visible groups, search by subject/code, combined search and filters, no-result/no-data states, sync/reconnect actions, and the already-tested refresh, expansion, What If and five-tab navigation behavior.

## Still legacy / not migrated

- `MainActivity` remains the application controller and Java view builder.
- Home screen content outside its hero identity and upcoming-exam card remains Java Views.
- Courses screen presentation is Compose, while Courses calculations, source data, grouping, sorting, filtering, expansion ownership and event destinations remain Java. Week, What If, Marks, Settings, onboarding, consent, MyCamu/KCT WebViews, account dialogs and updater remain Java Views.
- No screen ViewModels, Compose navigation, repository abstraction migration, multi-module split or DI framework.
- Notifications, workers, widgets and update flows remain unchanged.

## Verification status

- Phase 4 `./gradlew testDebugUnitTest`: PASS, including the two new course state-mapper tests.
- Phase 4 `./gradlew assembleDebug assembleRelease assembleDebugAndroidTest`: PASS with existing local signing setup; no release was published or installed.
- Phase 4 `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`: PASS, all 12 instrumentation tests on a newly created isolated Android 16 (API 36, x86_64) AVD. The three earlier test issues were assertion/setup mistakes and are corrected. The physical phone was not targeted.
- The physical phone's installed package is `com.iniexe.rollcall`, version 1.0.23 / versionCode 24, signed with SHA-256 `a1d35509…c703ba2`. The debug APK uses the same application ID, version 1.0.24 / versionCode 25, but a different debug certificate (`c51624e4…8747da`). Android correctly rejected an in-place update. Instrumentation manifest targets `com.iniexe.rollcall`. No attempt was made to bypass signature enforcement or uninstall the phone app.
- Release APK after Phase 3: 12,395,490 bytes; checkpoint APK before this phase: 11,399,990 bytes. Difference: 995,500 bytes. This is the combined size cost of the existing Compose/Lottie support since the checkpoint, not a measurement of this phase's incremental change. The debug-only test animation was confirmed absent from the release APK.
- Phase 4 release APK: 12,411,861 bytes, +16,371 bytes from the Phase 3 release APK. This is artifact size only; it does not establish runtime performance.
- Phase 5 `./gradlew testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest`: PASS. `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`: PASS, 14/14 tests on `rollcall_phase4b` (Android 16/API 36, x86_64). The emulator is isolated; the physical phone was not connected, targeted or modified.
- Phase 5 release APK: 12,428,250 bytes, +16,389 bytes from the Phase 4 release APK. This is artifact size only and does not establish runtime performance. Release APK listing contains no debug-only `rollcall_compose_test` resource.
- A debug build was installed and cold-launched on the isolated AVD (`am start -W`: 1,558 ms). This is one debug smoke measurement, not a comparable release benchmark or a Courses-rendering measurement.
- Phase 5 host tests exercise `DashboardView` in a test `ComponentActivity`, not the production `MainActivity`. Activity recreation, preference restoration, real data refresh from MyCamu/KCT, portal integrations, screen-reader hardware usage, font-scale screenshots, startup time, memory and frame timing remain NOT VERIFIED / NOT MEASURED.
- Theme repaint and light/dark rendering were verified in isolated component tests from Phase 3. Phase 4 card, refresh and dashboard-host tests pass on the isolated AVD. Theme preference persistence and full `MainActivity` recreation remain NOT VERIFIED; the host test exercises `DashboardView`, not portal startup or lifecycle restoration.
- Startup time, frame timing, scroll smoothness, main-thread stalls, runtime memory, large-font/narrow-screen screenshot coverage and production-screen visual QA remain NOT MEASURED / NOT TESTED.
- MyCamu/KCT authentication, fresh/cached synchronization, notification/alarm, Firebase, background workers, widgets and update/install integrations remain NOT TESTED by Phase 4.
- The debug Lottie fixture verifies adapter playback mechanics only; no production Lottie artwork was visually reviewed or enabled.

## Functional regression snapshot

| Area | Change in this phase | Verification | Status |
|---|---|---|---|
| Attendance formulas | No production formula change; tests characterize existing behavior | JVM tests for boundary values, targets, missing/invalid data and rounding | PASS for covered cases; feasible-recovery constraints beyond existing logic are unchanged |
| Home identity/date/week | Compose presentation behind Java bridge; Java still supplies the same values | Component instrumentation tests on phone | PASS for isolated hero fixture; full dashboard data flow not device-tested |
| Upcoming exam | Java computes current exam content; Compose maps semantic tokens | Present/empty/long-title/accessibility/theme tests on phone | PASS for isolated component |
| Latest class attendance | Java supplies mapped status, wording and history action; Compose renders semantic status | Present/absent/accessibility/click/recreation tests on phone | PASS for isolated component |
| Lottie adapter | Java app data is unaffected; adapter owns rendering only | Debug fixture load, visibility pause, playback, finish, remount and missing-resource test on phone | PASS; production asset and resource cost not tested |
| Courses screen | Complete page rendering (header, summary, search, filters, empty state and cards) is Compose; Java data, Attendance calculations, group rules, filters and navigation remain authoritative | JVM/build and 14 connected instrumentation tests PASS on isolated Android 16 AVD, including DashboardView bridge fixtures | VERIFIED for covered fixed fixtures and interactions; not live-data or production MainActivity parity |
| Five dashboard tabs | Navigation logic remains Java; Courses page is a Compose sibling in the Java shell | `DashboardView` host test traverses all tabs and returns to Home | PASS on isolated Android 16 AVD; Activity recreation not tested |
| MyCamu/KCT, sync, preferences, Firebase, reminders, widgets, updates | No production code change | Not exercised by this phase's tests | NOT TESTED |

This is a foundation slice, not a completed Compose migration. The app remains predominantly Java Views and must not be described as fully migrated.

## Phase 6A — lifecycle, theme, data, accessibility and performance checkpoint

Phase 6A was run from `22d58c9` on `codex/rollcall-torch-ui`.

- Added production `MainActivityLifecycleTest` using a local offline preference route and sanitized `LiveSnapshotStore` fixtures. It verifies onboarding startup, production dashboard startup, all five tabs, Java-to-Compose updates, Java-computed attendance target changes, theme preference through Activity recreation, existing selected-tab reset behavior, ActivityScenario lifecycle transitions, repeated recreation and Back exit. No portal credentials, cookies or live accounts are used.
- The connected suite now passes 18/18 instrumentation tests on the isolated `rollcall_phase4b` Android 16/API 36 emulator. JVM tests pass 16/16. Debug, release and instrumentation APK builds pass.
- Screenshot review covered dark/light Courses at standard width and long course content/font scale on a narrow 320dp layout. The tab labels hide above 1.3× font scale to avoid splitting words; their accessible names remain on the clickable tabs. Large course titles may still visually ellipsize at 1.5×–2.0× while full semantic text remains available.
- A real hidden-tab animation issue was fixed: Home's Java sync glyph pauses when hidden, resumes when visible, and cancels on detach. The isolated instrumentation test verifies this.
- Repeated debug startup proxies were collected on the AVD (cold median 1387ms, n=5; warm `WaitTime` median 200ms, n=5) and one debug PSS snapshot was recorded. These are not release benchmarks. Frame/jank and Courses scroll/search/filter/expand timing, recomposition/allocation profiling, repeated memory sampling, process-death persistence, manual TalkBack and live integrations remain unverified.
- Full details: `docs/testing/PHASE6A_VERIFICATION.md`.

**Status: PARTIALLY VERIFIED.** The tested lifecycle/theme/snapshot/Courses gates pass. Do not call the entire app migrated or begin Week on the strength of these tests alone. Remaining integration and runtime profiling risks are documented.

## Next safe slices

1. Add a reproducible Courses performance scenario or profiler trace, including scroll/search and frame timing, on the same isolated AVD.
2. Perform manual TalkBack and process-death/relaunch checks; verify live external integrations separately with an authorized account only when required.
3. Continue with Week only after a separate behavior inventory and migration gate.
