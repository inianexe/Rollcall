# Compose migration tracker

## Checkpoint

- Baseline branch: `codex/rollcall-torch-ui`
- Baseline commit: `2c21ca0` (Rollcall 1.0.24, code 25)
- Foundation commit: `7763bda`; Home exam card commit: `5683bfb` (pushed to the migration branch)
- Phase 3 starts from `c728211`; theme unification, one additional Home component, and Android component tests are staged in the current working tree.
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

## Still legacy / not migrated

- `MainActivity` remains the application controller and Java view builder.
- Home screen content outside its hero identity and upcoming-exam card remains Java Views.
- Courses, Week, What If, Marks, Settings, onboarding, consent, MyCamu/KCT WebViews, account dialogs and updater remain Java Views.
- No screen ViewModels, Compose navigation, repository abstraction migration, multi-module split or DI framework.
- Notifications, workers, widgets and update flows remain unchanged.

## Verification status

- Baseline `testDebugUnitTest`: passed before this phase.
- Phase 3 `./gradlew testDebugUnitTest`: PASS, 14 JVM tests (3 palette mapping/contrast, 6 attendance, 2 notifier, 3 widget metrics).
- Phase 3 `./gradlew connectedDebugAndroidTest`: PASS, 9 instrumentation tests on physical Android 16 device `A001 - 16`.
- Phase 3 `./gradlew assembleDebug assembleRelease assembleDebugAndroidTest`: PASS with existing local signing setup; no release was published or installed.
- Release APK after Phase 3: 12,395,490 bytes; checkpoint APK before this phase: 11,399,990 bytes. Difference: 995,500 bytes. This is the combined size cost of the existing Compose/Lottie support since the checkpoint, not a measurement of this phase's incremental change. The debug-only test animation was confirmed absent from the release APK.
- Theme tokens and a visible Compose card were tested in light/dark. Java preference persistence remains unchanged but was not exercised through a full Activity recreation in this component suite.
- MyCamu/KCT authentication, notification/alarm, Firebase, update/install, large-font, startup/frame/memory profiling and production-screen visual QA remain NOT TESTED in this phase.
- The debug Lottie fixture verifies adapter playback mechanics only; no production Lottie artwork was visually reviewed or enabled.

## Functional regression snapshot

| Area | Change in this phase | Verification | Status |
|---|---|---|---|
| Attendance formulas | No production formula change; tests characterize existing behavior | JVM tests for boundary values, targets, missing/invalid data and rounding | PASS for covered cases; feasible-recovery constraints beyond existing logic are unchanged |
| Home identity/date/week | Compose presentation behind Java bridge; Java still supplies the same values | Component instrumentation tests on phone | PASS for isolated hero fixture; full dashboard data flow not device-tested |
| Upcoming exam | Java computes current exam content; Compose maps semantic tokens | Present/empty/long-title/accessibility/theme tests on phone | PASS for isolated component |
| Latest class attendance | Java supplies mapped status, wording and history action; Compose renders semantic status | Present/absent/accessibility/click/recreation tests on phone | PASS for isolated component |
| Lottie adapter | Java app data is unaffected; adapter owns rendering only | Debug fixture load, visibility pause, playback, finish, remount and missing-resource test on phone | PASS; production asset and resource cost not tested |
| MyCamu/KCT, sync, preferences, Firebase, reminders, widgets, updates | No production code change | Not exercised by this phase's tests | NOT TESTED |
| Lottie | Added reusable local-resource adapter only; no asset or call site added | Debug and release compile | API/build verified; playback not tested |

This is a foundation slice, not a completed Compose migration. The app remains predominantly Java Views and must not be described as fully migrated.

## Next safe slices

1. Add targeted test fixtures for the existing parse/model boundaries before moving them.
2. Move one data-display screen at a time to Compose, keeping `MainActivity.render`/existing domain outputs authoritative until that screen has parity tests.
3. Continue with one read-only Home display component at a time; verify system back, Activity state restoration, themes and accessibility before migrating navigation.
4. Migrate WebView/auth/settings only after their current behavior is covered by integration tests; do not fold auth or persistence refactors into a visual migration.
