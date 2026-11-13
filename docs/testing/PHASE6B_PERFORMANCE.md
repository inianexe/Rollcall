# Phase 6B.1 Courses performance baseline

> Historical, preliminary debug-emulator results only. The original temporary worktree and `/tmp/rollcall-phase6b-qa/` traces were lost before Phase 6B.1R; the old trace data cannot currently be reopened or independently reanalyzed. The values below are retained as records from the earlier report, not as recovered evidence. Fresh release-like emulator evidence is in [`PHASE6B_RELEASE_PERFORMANCE.md`](PHASE6B_RELEASE_PERFORMANCE.md).

Date: 2026-11-12. Branch: `codex/rollcall-torch-ui`. Starting commit: `0cc7bdd`.

## Scenario and environment

`DashboardViewIntegrationTest.profileCoursesInteractions` hosts the real Java dashboard and Compose Courses page in a test `ComponentActivity`. It supplies 24 paired lecture/lab groups (48 sanitized course rows), including long names and safe/below-target states. Each of three passes opens Courses, scrolls down and up, searches for course 17, clears search, switches Below Target and All Courses, expands a visible lab metric, then switches tabs. The test runs entirely in `androidTest`; release code and academic calculations are unchanged.

Measurements used the isolated `rollcall_phase4b` AVD (`emulator-5554`), Android 16/API 36, x86_64, 1080×2400 pixels, 420 dpi, 60 Hz, debug build. The physical phone was not targeted. This is a repeatable debug-emulator baseline, not a release-like device benchmark.

## How to reproduce

From `android/`, after confirming `adb devices -l` lists the intended emulator:

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell perfetto -D \
  -o /data/misc/perfetto-traces/rollcall-phase6b-courses.perfetto-trace \
  -t 45s -b 64mb -a com.iniexe.rollcall \
  sched/sched_switch sched/sched_wakeup power/cpu_frequency am wm gfx view
adb -s emulator-5554 shell am instrument -w \
  -e class com.iniexe.rollcall.DashboardViewIntegrationTest#profileCoursesInteractions \
  com.iniexe.rollcall.test/androidx.test.runner.AndroidJUnitRunner
```

Wait for Perfetto's configured 45-second duration to finish before pulling the trace. Pull `/data/misc/perfetto-traces/rollcall-phase6b-courses.perfetto-trace` and `/sdcard/Android/data/com.iniexe.rollcall/files/phase6b-profile/` from `emulator-5554`. The test writes `before/after-frames.txt` and `before/after-memory.txt` while the Activity is alive, because querying `gfxinfo` after instrumentation exits loses the process data. Run the scenario repeatedly on the same AVD. No production session is needed.

The actual nonempty trace is stored locally at `/tmp/rollcall-phase6b-qa/run2/rollcall-phase6b-courses-run2.perfetto-trace` (27,204,073 bytes). Text metrics for three runs are under `/tmp/rollcall-phase6b-qa/run{1,2,3}/phase6b-profile/`. These QA artifacts are intentionally outside Git. An initial trace file was zero bytes after an early `SIGTERM`; letting the 45-second trace finish produced the usable file.

## Frame and memory results

`dumpsys gfxinfo ... framestats` captured after the interactions reported:

| Run | Frames | Janky frames | Median | P90 | P95 | PSS before → after |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 817 | 600 (73.44%) | 53 ms | 69 ms | 85 ms | 137,384 → 156,479 KB |
| 2 (Perfetto) | 841 | 588 (69.92%) | 48 ms | 69 ms | 77 ms | 136,558 → 156,342 KB |
| 3 | 870 | 630 (72.41%) | 53 ms | 69 ms | 81 ms | 136,494 → 155,536 KB |

The activity had one recorded startup frame before the interactions in each run; the table reports the complete post-scenario counters. PSS rose by 19,095, 19,784, and 19,042 KB respectively while rendering and interacting with the 48-row fixture. Fresh-process ending PSS stayed near 156 MB across runs; this does not establish leak freedom.

Perfetto Trace Processor queried `actual_frame_timeline_slice` for the `com.iniexe.rollcall/androidx.activity.ComponentActivity` layer. It found 854 app-layer frames with positive duration: median 50.0 ms, P90 66.1 ms, P95 71.1 ms; one frame exceeded 700 ms. The trace and `gfxinfo` use different frame definitions and counts, so their percentiles should not be mixed.

The trace was analyzed with the host `perfetto` Python package and its Trace Processor. The core frame query was:

```sql
SELECT dur, jank_type FROM actual_frame_timeline_slice
WHERE layer_name LIKE '%com.iniexe.rollcall/androidx.activity.ComponentActivity%'
  AND dur > 0;
```

Durations were converted from nanoseconds to milliseconds and sorted before calculating percentiles. The process CPU figures came from `sched` joined to `thread` for the app process; slice counts came from `slice` joined to `thread_track`. The package and processor binary were installed under `/tmp/rollcall-phase6b-qa/venv`, outside the repository.

## Trace observations

The 45-second trace recorded 16.84 CPU-seconds on RenderThread and 7.91 CPU-seconds on the app main thread. App-layer frame reasons frequently included `SurfaceFlinger CPU Deadline Missed`, `App Deadline Missed`, and `Buffer Stuffing` together (475 frames in that combined category). The main-thread trace included 4,911 `AndroidOwner:measureAndLayout` slices totalling 2.68 seconds and 780 `Compose:recompose` slices totalling 1.05 seconds. These nested trace slices are not exclusive CPU time. The emulator's rendering path and the instrumentation harness contribute to this result; the trace does not isolate a specific production-code defect or prove a Compose regression.

No production optimization was made. The measured jank is substantial on this debug AVD, but changing UI code without a comparable release-like trace would be speculative. A release-like baseline on a consistent device and a narrow trace around scrolling should precede an optimization decision. The trace lacks a reliable GC event series, and this scenario did not separately time individual search, filter, expansion, or navigation actions. Memory allocation spikes and recomposition causes were not isolated.

## Verification

The profiling scenario passed in three direct emulator runs. `./gradlew testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest` passed; the JVM suite contains 16 tests. `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest` passed 19/19 instrumentation tests on `rollcall_phase4b` (Android 16/API 36). `git diff --check` passed. The release APK contains no `rollcall_compose_test` debug fixture. No MyCamu/KCT connection, real student data, Firebase, or physical phone installation was used.
