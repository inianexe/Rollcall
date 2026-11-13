# Phase 6B.2 — Week Compose migration verification

Date: 2026-11-13. Branch: `codex/rollcall-torch-ui`. Starting checkpoint: `227b30c`. Status: **PARTIALLY VERIFIED**.

## Implementation and responsibility boundary

`DashboardView` keeps the five-tab shell and authoritative Week behavior. The Week page is now a full-height `WeekScreenView` Compose sibling, with one `LazyColumn`; it is not nested inside the Java `ScrollView`. Compose files are under `android/app/src/main/java/com/iniexe/rollcall/ui/screens/week/`:

- `WeekScreen.kt`: schedule/history page composition, daily cards, history rows, pulse chart, empty/error presentations, and callbacks.
- `WeekScreenUiState.kt`: immutable display state and row models.
- `WeekPreviews.kt`: representative preview states.
- `ui/bridge/WeekScreenView.kt`: Java-friendly state/theme bridge.

Java continues to parse and group timetable/history values, order dates and classes, classify and label attendance, calculate Week Pulse counts, choose the initial expanded date, own expanded dates and current mode, limit history to 30 records per reveal, refresh data, handle dashboard navigation, and open/close Java Leave & OD details. Compose renders supplied values and emits user events. No attendance/timetable rules or persistence formats were moved.

## Behavior and test coverage

The deterministic fixtures verify schedule ordering/grouping; initial and multiple expanded days; day expansion/collapse; all-day and cancelled entries; present, absent, unmarked and unknown attendance; OD/leave information; history batch size and remaining count; refresh; empty, loading, sync failure and expired-session states; Leave & OD navigation/return; and five-tab navigation. Large-text coverage includes long subject/class names at 1.0×, 1.3×, 1.5× and 2.0×. Long schedule names are rendered without truncation. Light/dark screenshots were reviewed.

| Check | Result |
|---|---|
| JVM unit tests | **PASS**, 16 tests (final rerun recorded below) |
| App instrumentation suite | **PASS**, 21/21 on isolated Android 16/API 36 AVD `rollcall_phase4b` (`emulator-5554`) |
| Week Macrobenchmark | **PASS**, 7 scenarios, 5 iterations each (35 measured iterations) |
| Follow-up Macrobenchmark reruns | **PARTIALLY VERIFIED**; benchmark target selection and AVD graphics/device disconnect interrupted complete reruns, details below |
| Separate PSS cycle diagnostic | Week cycle **PASS**, six samples; Courses cycle **FAILED due to emulator disconnect** before completion |
| Actual `MainActivity` lifecycle tests | **PASS** in the app instrumentation suite; sanitized local fixture only |
| Live MyCamu/KCT, Firebase, notifications, updater | **NOT TESTED** |
| Manual TalkBack | **NOT TESTED** |
| `git diff --check` | **PASS** (final rerun recorded below) |

The Week PSS samples were 63,828; 76,170; 74,523; 74,689; 74,696; and 74,773 KB. The first interaction adds warm-up/runtime allocations; samples 3–5 differ by less than 0.3 MB, so this short series does not show continued growth. It is not a leak proof. The second PSS test did not complete because the emulator disappeared; no conclusion is drawn from its partial samples.

## Physical-device visual verification

User-authorized QA package `com.iniexe.rollcall.qa` was installed separately on the Nothing Phone A001 (Galaga), Android 16/API 36, 1080×2392, 420 dpi. It uses a QA-only launcher/fixture, no Internet permission, no production Firebase setup, and synthetic data. `com.iniexe.rollcall` versionCode 24 was present at the pre-install check; no production uninstall, data-clear or install command was issued. At the final package check, however, `com.iniexe.rollcall` was absent from all listed Android users while QA remained installed. The cause is unknown and this agent did not issue a command that removes it; its current installation/data status cannot be verified. No repair or reinstall was attempted. Reviewed screenshots cover Week Schedule (light/dark), Attendance history/statuses, Home, Courses and the existing Java Leave & OD screen. Long names wrap and the Compose Week page fits below system bars. Files are outside Git:

`/home/inian/Projects/Rollcall-performance-qa/phase6b2-week-final-20261113/physical-screenshots/`

This QA host is not `MainActivity`; real Activity lifecycle evidence comes from the emulator tests. Physical-device interaction/visual review is verified for the synthetic screen, not live student data.

## Performance

Macrobenchmark used `rollcall_phase4b` (Android 16/API 36, x86_64, 1080×2400, 420 dpi, 60 Hz), the release-derived non-debuggable/profileable benchmark target, `CompilationMode.None()`/run-from-APK, and deterministic fixture data (42 schedule classes over seven days and 60 history records). The benchmark target is locally debug-signed, does not enable R8 shrinking (neither does current release), and includes only benchmark fixture code. It is release-like, not a Play-installed production build. Five iterations were collected per scenario. `FrameTimingMetric` aggregates measured frame samples; a 60 Hz frame interval is 16.67 ms.

| Week scenario | Frames | CPU frame duration P50 / P90 / P95 / P99 (ms) | Frame overrun P50 / P90 / P95 / P99 (ms) | Frames >16.67 ms |
|---|---:|---:|---:|---:|
| Schedule scrolling | 1,043 | 41.02 / 45.84 / 47.49 / 57.45 | 36.63 / 46.86 / 49.61 / 71.07 | 81.8% |
| Schedule/Attendance switch | — | 21.36 / 34.57 / 40.61 / 45.69 | — | 80.2% |
| Day expansion | — | 12.00 / 40.63 / 43.50 / 44.82 | — | 22.9% |
| History scrolling | — | 9.59 / 44.61 / 46.48 / 50.29 | — | 43.1% |
| History pagination | — | 38.67 / 45.33 / 47.70 / 54.66 | — | 62.8% |
| Tab navigation | — | 12.21 / 51.67 / 90.64 / 317.50 | — | 34.6% |
| Cold startup | 5 launches | Time to initial display median **1,121 ms** (range 1,068–1,144) | — | — |

The Courses scroll benchmark on the same emulator/build/fixture methodology measured 13.33 ms P50, 47.34 ms P90 and 47.0% of frames over 16.67 ms. Its plain-View list control measured 8.59 ms P50, 45.82 ms P90 and 44.5% over 16.67 ms. Week schedule scroll is therefore notably slower in its median (27.69 ms higher than Courses; 32.43 ms higher than the plain list), while its P90 is similar. This is an emulator result and the simplified list is not feature-equivalent.

Perfetto analyzed the five Week schedule traces (1,043 frames): median-of-run frame duration percentiles were 38.18 / 43.59 / 46.37 ms (P50/P90/P95); 81.8% exceeded 16.67 ms and 76.5% exceeded 33.33 ms. Frame classifications overlap: Prediction Error + Buffer Stuffing 385, Prediction Error + App Deadline + Buffer Stuffing 207, Prediction Error 187, Buffer Stuffing 163, SurfaceFlinger CPU Deadline + Buffer 72, App Deadline + Buffer 15, and Prediction Error + App Deadline 10. Prediction Error is not by itself a missed deadline. The five traces contain 299 `AndroidOwner:measureAndLayout` slices and 110 Compose recomposition slices; main-thread scheduled CPU time median was about 0.448 s and RenderThread about 3.165 s per trace. These totals include benchmark setup/idle and nested work; they do not establish a causal per-frame bottleneck. A comparable plain-list control also has high tail delays and substantial SurfaceFlinger/prediction classifications. **No specific app-level cause was isolated, and no speculative optimization was made.** Treat schedule scrolling as an unresolved performance risk before starting another major screen migration.

### Physical frame-data limitation

Perfetto tracing was attempted on the physical QA app. The 5.9 MB trace contains only three `actual_frame_timeline_slice` entries for `TX - StatusBar#91`, plus three entries without a layer name; it contains no frames attributable to the Rollcall QA surface. Therefore app frame time, jank and physical performance are **NOT MEASURED**. A sparse `dumpsys gfxinfo` sample was also invalid for inference and is excluded. The trace is preserved at `/home/inian/Projects/Rollcall-performance-qa/phase6b2-week-final-20261113/physical-week-scroll.perfetto-trace`.

All seven Macrobenchmark tests passed. Raw traces are preserved outside Git under `/home/inian/Projects/Rollcall-performance-qa/phase6b2-week-final-20261113/` (approximately 191 MB including both Week and physical captures). The release APK is 12,461,020 bytes versus the previous Phase 6A APK at 12,428,245 bytes: **+32,775 bytes**. This is artifact-size comparison only, not runtime performance.

## Release and package safety

`assembleRelease` passed. APK inspection reports production package `com.iniexe.rollcall`, versionCode 25 / 1.0.24. The QA APK has application ID `com.iniexe.rollcall.qa`; QA uses a separate signing identity and no production Firebase configuration. The release APK contains neither the benchmark/QA launcher `CoursesBenchmarkActivity` nor a profileable manifest declaration. The production application ID and signing configuration were not changed; no production release was installed or published.

## Verification record

Final commands and outcomes:

| Command/check | Result |
|---|---|
| `./gradlew testDebugUnitTest` | **PASS**, 16 tests |
| `./gradlew assembleDebug` | **PASS** |
| `./gradlew assembleRelease` | **PASS** |
| `./gradlew assembleDebugAndroidTest` | **PASS** |
| `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest` | **PASS**, 21/21 |
| `ANDROID_SERIAL=emulator-5554 ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest` | **PASS**, 7/7 Week benchmark tests; five iterations each |
| `:app:assembleQa` and `:app:assembleBenchmark` | **PASS** |
| Release APK fixture/manifest inspection | **PASS**, QA/benchmark Activity absent |
| `git diff --check` | **PASS** (final rerun recorded before commit) |

The archived Week run above completed 7/7 before the final verification pass. Two later reruns exposed test-environment issues: running all Macrobenchmark classes after app instrumentation left a debuggable app package installed, causing `MacrobenchmarkRule` to reject the target; and the AVD then hung/shut down while another Android 16 AVD was active. A later Week-class rerun started against the non-debuggable package but ended when `emulator-5554` disappeared during tab navigation. The final rerun is therefore **PARTIALLY VERIFIED**; the original successful run and its traces remain the basis for the measurements above. The full combined Macrobenchmark invocation also found two Courses benchmark control-selector failures before the emulator disconnected. No Week production UI regression was identified from those harness failures.

External services and production account flows were deliberately not exercised. The migration is not the complete Rollcall Compose migration; What If, Marks, Settings and other legacy screens remain Java Views.
