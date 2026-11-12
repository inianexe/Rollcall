# Android architecture

## Current shape

Rollcall is a single-module native Android application (`android/app`). `MainActivity` extends `ComponentActivity` to host Compose but still coordinates the application. Most existing screens and flows are assembled as Android Views in Java; `DashboardView` is the main custom dashboard view. The Home hero identity/ticket, upcoming-exam card and latest-class attendance summary are embedded Compose components. There are not yet screen ViewModels or a Compose navigation graph.

```text
Android lifecycle / MainActivity (Java)
  ├─ MyCamu WebView + consent + JS bridge ──> existing parsers ──> LiveSnapshotStore
  ├─ KCT WebView + records bridge ─────────> existing record preferences
  ├─ Firebase / WorkManager / reminder and update components
  └─ DashboardView (Java custom View)
       ├─ Home / Courses / Week / What If / Marks
       ├─ RollcallHeroIdentityView (interop bridge) -> HomeHeroIdentity (Compose)
       ├─ UpcomingExamCardView (interop bridge) -> UpcomingExamCard (Compose)
       ├─ LatestClassAttendanceCardView (interop bridge) -> LatestClassAttendanceCard (Compose)
       └─ CourseCardView (interop bridge) -> CourseCard (Compose; Java still groups and filters)
```

`Attendance` owns course percentage, safe absence, recovery and guidance calculations. `LiveSnapshotStore` persists the MyCamu snapshot and notifies its listener. `MainActivity.render` maps snapshot and academic preferences into `DashboardView.State`. The UI displays that state. `AppPalette` remains the Java source for the current light/dark choice and maps semantic values into `RollcallThemeColors`; do not duplicate palette values in Compose. The latest-class summary state and click action are still supplied by Java. Keep these authority relationships during migration.

For the grouped Courses card, `MainActivity.render` supplies the existing Can miss / Must attend values, status and progress color. `CourseCardStateMapper` maps them into immutable `CourseCardUiState`. `DashboardView` still owns grouping, sorting, search/filter, expanded-course state, tab navigation and What If routing. Compose does not calculate attendance.

## Intended direction

```text
Compose screen -> immutable screen state / ViewModel (incremental) -> existing domain model
              -> existing repositories, WebView bridges, preferences and workers
```

This diagram describes the target direction, not the current implementation. Migrate one screen at a time. Do not introduce a ViewModel/repository rewrite unless a screen migration requires one and its existing behavior has tests. Existing Java integrations may remain Java; Kotlin and Java interoperate in this module.

## Important boundaries

- Compose is a renderer. It must not fetch portal data, read cookies, schedule reminders, or compute attendance.
- `Attendance` remains the source of truth for existing attendance math.
- `LiveSnapshotStore`, preference names, JSON shapes, Firebase and WorkManager behavior are compatibility boundaries.
- MyCamu and KCT pages are provider-owned content inside WebViews. Do not inject styling or rewrite their authentication as part of a Compose migration.
- `UpdateActivity` remains a separate Activity until its download, digest, signer and install flow has dedicated tests.
- No DI framework or multi-module split has been added; current evidence does not justify that extra migration risk yet.

## Build and verification

From `android/`:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
```

Release builds require the existing local signing setup; never add signing properties or passwords to Git. JVM tests characterize attendance calculations, course-state mapping and Java-to-Compose palette mapping. `androidTest` contains fixed-fixture component tests and a `DashboardView` host test; it does not launch `MainActivity`, portal authentication or live sync. The Phase 4 connected rerun was blocked by an installed-package signature mismatch, so the new card/dashboard UI tests are not marked passed.
