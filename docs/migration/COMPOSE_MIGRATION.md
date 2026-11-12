# Compose migration tracker

## Checkpoint

- Baseline branch: `codex/rollcall-torch-ui`
- Baseline commit: `2c21ca0` (Rollcall 1.0.24, code 25)
- Prior rollback: `design-review/rollback/20261112-before-compose-migration` (kept intact)
- This phase checkpoint: `design-review/rollback/20261112-pre-lottie-compose-foundation` (APK and source archive)

## Completed

- Compose build support and `ComponentActivity` interop.
- Home hero identity/calendar ticket in Compose, embedded in the existing Java dashboard.
- Functional inventory and architecture/design/animation notes.
- Central Compose color, typography, spacing, shape and motion tokens based on current Rollcall Signal palette.
- Immutable `HomeHeroIdentityState` and a thin `AbstractComposeView` bridge.
- Official Lottie Compose dependency and reusable local-resource adapter. No Lottie asset is shipped because the project has none to validate.
- Attendance calculation characterization tests added without changing the calculation implementation.

## Still legacy / not migrated

- `MainActivity` remains the application controller and Java view builder.
- Home screen content outside its hero identity remains Java Views.
- Courses, Week, What If, Marks, Settings, onboarding, consent, MyCamu/KCT WebViews, account dialogs and updater remain Java Views.
- No screen ViewModels, Compose navigation, repository abstraction migration, multi-module split or DI framework.
- Notifications, workers, widgets and update flows remain unchanged.

## Verification status

- Baseline `testDebugUnitTest`: passed before this phase.
- This phase `./gradlew testDebugUnitTest`: PASS, 8 JVM unit tests (the original 5 plus 3 characterization tests for existing attendance calculations).
- This phase `./gradlew assembleDebug`: PASS.
- This phase `./gradlew assembleRelease`: PASS with the existing local signing setup; no release was published.
- `git diff --check`: PASS.
- Device-specific MyCamu/KCT, notification, alarm, Firebase, update/install, large-font and performance tests remain NOT TESTED unless explicitly recorded in a later checkpoint.
- There is no Lottie asset, so playback appearance, theme recoloring, and Lottie frame/memory performance are NOT TESTED.

## Functional regression snapshot

| Area | Change in this phase | Verification | Status |
|---|---|---|---|
| Attendance formulas | No production formula change; tests characterize existing behavior | JVM tests for 75% safe absence/recovery, exact target/empty course, and 100% recovery boundary | PASS for covered cases; all other boundaries not tested here |
| Home identity/date/week | Moved the same Compose presentation into a screen composable behind a Java View bridge; Java still supplies the same values | Debug and release compile | BUILD-VERIFIED; no device visual test |
| MyCamu/KCT, sync, preferences, Firebase, reminders, widgets, updates | No production code change | Not exercised by this phase's tests | NOT TESTED |
| Lottie | Added reusable local-resource adapter only; no asset or call site added | Debug and release compile | API/build verified; playback not tested |

This is a foundation slice, not a completed Compose migration. The app remains predominantly Java Views and must not be described as fully migrated.

## Next safe slices

1. Add targeted test fixtures for the existing parse/model boundaries before moving them.
2. Move one data-display screen at a time to Compose, keeping `MainActivity.render`/existing domain outputs authoritative until that screen has parity tests.
3. Add Compose UI/instrumentation tests and verify system back, state restoration, themes and accessibility before migrating navigation.
4. Migrate WebView/auth/settings only after their current behavior is covered by integration tests; do not fold auth or persistence refactors into a visual migration.
