# Phase 6B.1R — release-like Courses performance baseline

Date: 2026-11-13. Branch: `codex/rollcall-torch-ui`. Baseline checkpoint: `61dfd78`.

## Result

The benchmark build is non-debuggable, profileable by shell, and based on the app's `release` build type. The AndroidX Macrobenchmark suite completed **6/6 tests** on the isolated Android 16 emulator. Each of the five measured scenarios ran five iterations. A repeated-memory instrumentation test sampled six points across five navigation/scroll cycles.

These are release-like **emulator diagnostics**, not production-device performance claims. Android's Macrobenchmark guidance explicitly warns that emulator results are not representative of end-user devices ([Macrobenchmark overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)). No physical phone was targeted.

## Environment and build

| Item | Configuration |
|---|---|
| Emulator | `rollcall_phase4b` / `emulator-5554`, `sdk_gphone64_x86_64` |
| Android | 16, API 36, Google APIs Play Store image, x86_64 |
| Display | 1080×2400, 420 dpi, 60 Hz |
| Host/virtual CPU | 4 virtual cores, up to 2 GHz reported by Macrobenchmark; host contention is uncontrolled |
| Memory | 2.59 GB reported by Macrobenchmark |
| App target | `com.iniexe.rollcall`, benchmark build type |
| Benchmark harness | Separate `:macrobenchmark` instrumentation process |
| Compilation | `CompilationMode.None()` / `run-from-apk`; no profile compilation requested |
| Benchmark app build | `initWith release`, locally debug-signed, `debuggable false`, `<profileable android:shell="true"/>`; release signing untouched |
| Shrinking | Same release configuration; current app release does not enable R8 minification |
| Iterations | 5 per scenario |

The benchmark-only Activity and sanitized fixture live under `app/src/benchmark`. They display 48 course rows in 24 lecture/lab pairs, with varied status and long names, or a plain 48-row `ScrollView` control. Fixture values are isolated to this build type; the real attendance code and user data are not used or changed. The Activity is absent from the release manifest.

## Reproduction

Confirm the emulator instead of trusting a remembered serial:

```bash
adb devices -l
cd android
ANDROID_SERIAL=emulator-5554 ANDROID_HOME=/home/inian/Android/Sdk \
  ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

The benchmark module explicitly passes `androidx.benchmark.suppressErrors=EMULATOR` because this is a deliberate emulator-only diagnostic. The target app itself remains non-debuggable. The final run completed in 4m23s; its test report records 6 tests, 0 failures, 0 errors, 0 skipped.

To archive raw results persistently, run from the repository root:

```bash
./scripts/archive_rollcall_benchmark.sh
```

The script copies traces, Macrobenchmark JSON, and test results to `/home/inian/Projects/Rollcall-performance-qa/` and writes SHA-256 checksums. Save the memory-test log beside those files with `adb -s emulator-5554 logcat -d -s RollcallMemoryCycles:I '*:S' > <archive-dir>/memory-cycles.log`. The fresh run is archived at:
`/home/inian/Projects/Rollcall-performance-qa/phase6b1r-20261113-083653/`.
It contains 25 Perfetto traces and takes approximately 48 MB. Large traces stay outside Git. Analyze all captured app-layer frames with the repository script and the persistent QA virtualenv:

```bash
/home/inian/Projects/Rollcall-performance-qa/venv/bin/python \
  scripts/analyze_rollcall_perfetto.py \
  /home/inian/Projects/Rollcall-performance-qa/phase6b1r-20261113-083653 \
  --output /home/inian/Projects/Rollcall-performance-qa/phase6b1r-20261113-083653/perfetto-summary.json
```

The script uses Perfetto Trace Processor and reports per-trace frame percentiles, frame-timeline classifications, app-thread scheduled CPU time, and selected app slices. Install the `perfetto` Python package in a QA virtualenv when needed.

## Macrobenchmark measurements

`FrameTimingMetric` values below are aggregate percentiles across measured frame samples, not percentiles of iteration durations. Negative frame overrun means the frame completed before its deadline. Startup is time to initial display. The scroll and plain-list workload, fixture, device, build, and swipe sequence are identical; the plain list is a simple View `ScrollView`, not an equivalent app screen.

| Scenario | Iterations / median frames | CPU frame duration P50 / P90 / P95 / P99 (ms) | Frame overrun P50 / P90 / P95 / P99 (ms) | Startup median (ms) |
|---|---:|---:|---:|---:|
| Courses scrolling | 5 / 250 | 12.88 / 46.21 / 47.83 / 52.20 | -2.41 / 46.13 / 49.00 / 73.60 | — |
| Plain-list scrolling control | 5 / 261 | 8.22 / 44.22 / 45.89 / 49.22 | -7.26 / 43.65 / 46.38 / 103.72 | — |
| Search and filters | 5 / 16 | 16.35 / 32.76 / 35.35 / 38.47 | 2.66 / 23.14 / 24.99 / 32.22 | — |
| Expansion and tab navigation | 5 / 78 | 21.70 / 34.19 / 54.11 / 138.25 | 5.52 / 24.80 / 42.63 / 125.92 | — |
| Cold startup | 5 | — | — | 1,277.08 (runs 1,370.99; 1,285.00; 1,255.29; 1,277.08; 1,275.36) |

The two scrolling cases have similar high tails (P90 CPU duration 46.21 ms vs 44.22 ms; P90 overrun 46.13 ms vs 43.65 ms). Courses has a 4.66 ms higher median CPU frame duration. The plain-list P99 overrun is worse than Courses, which is consistent with substantial emulator scheduling/rendering noise; it does not prove the Compose list is free of cost.

## Perfetto frame timeline and CPU observations

Perfetto Trace Processor analyzed all 25 traces (five iterations in each of the five scenarios). For the five scrolling traces, the median of each run's frame-duration percentiles was:

| Scenario | App-layer frames | Duration P50 / P90 / P95 / P99 (ms) | App Deadline Missed | SurfaceFlinger CPU Deadline Missed | Buffer Stuffing | Prediction Error |
|---|---:|---:|---:|---:|---:|---:|
| Courses | 1,249 | 13.77 / 44.51 / 47.05 / 51.58 | 190 (15.2%) | 59 (4.7%) | 569 (45.6%) | 1,037 (83.0%) |
| Plain list | 1,312 | 8.68 / 42.17 / 44.03 / 47.55 | 156 (11.9%) | 73 (5.6%) | 574 (43.8%) | 1,115 (85.0%) |

At the emulator's 60 Hz interval (16.67 ms), Trace Processor found 579/1,249 Courses frames (46.4%) and 580/1,312 plain-list frames (44.2%) longer than one refresh interval. Above 33.33 ms were 560/1,249 (44.8%) and 540/1,312 (41.2%), respectively. Neither scrolling trace had a frame over 700 ms. These measured duration thresholds indicate substantial slow presentation in both workloads; their close rates and the unexpectedly large delays in the plain list point to emulator/host effects, but do not make the Courses absolute rate acceptable or rule out app cost.

Frame-timeline classifications may contain multiple labels for one frame; percentages overlap and must not be summed. In particular, **Prediction Error is not equivalent to a missed frame deadline** and is not counted here as jank. The actual App Deadline Missed share is 3.3 percentage points higher for Courses, while SurfaceFlinger deadline misses are 0.9 points lower. The plain-list control itself still shows sizeable app-deadline and Buffer Stuffing classifications, which indicates that the emulator/display pipeline contributes materially to the signal.

Median scheduled CPU time per trace across scrolling iterations was 0.672 s on the app main thread (`iniexe.rollcall`) and 3.162 s on `RenderThread`; the corresponding plain-list medians were 0.187 s and 2.640 s. This is consistent with additional work in the richer Courses page, but it does not identify one expensive composable or prove a production-device bottleneck. The Courses traces contain `AndroidOwner:measureAndLayout` slices (median 163 slices / 155 ms per trace) and `Compose:recompose` slices (median 36 / 58 ms per trace); these are instrumented durations and may be nested, so they are not additive CPU time. The plain-list control has no matching Compose slices.

Five GC slices totaling 188 ms were recorded across the five Courses scrolling traces (roughly one per trace); none appeared in the plain-list traces. This is a small, trace-instrumented sample and does not establish that GC caused the frame delays. CPU-frequency counters and allocation stacks were not used for attribution.

`searchAndFilter` has only 15–16 measured frames per iteration, and expansion/navigation includes transition frames; their tail percentiles are therefore sensitive to a small number of emulator frames and should not be compared directly with scrolling. Startup includes framework and first-render work in this benchmark variant.

## Memory cycle

The `repeatedCourseCyclesPss` instrumentation test passed and logged:

| Sample | PSS (KB) | Activity |
|---:|---:|---|
| 0 | 59,336 | benchmark process before entering Courses |
| 1 | 82,759 | after first Courses rendering |
| 2 | 72,099 | after first scroll/tab cycle |
| 3 | 70,197 | repeated cycle |
| 4 | 71,689 | repeated cycle |
| 5 | 69,553 | repeated cycle |

PSS falls after the first render and remains in a 69.6–72.1 MB band for the remaining samples. This does not show sustained growth across these five cycles; a single short test cannot prove absence of a leak.

## Interpretation and action

- **Emulator/environment effects:** the plain-list control has large tail overrun and deadline-classification rates, and the trace reports many prediction errors. The emulator shares host CPU/GPU resources; host load is not controlled. The results cannot characterize a physical device.
- **Potential app cost:** Courses has a higher median frame CPU duration, more main-thread scheduled CPU time and a 3.3 percentage-point higher app-deadline-miss share than the plain list. This is measurable additional work, but the control is intentionally simpler and does not isolate a specific root cause.
- **Confirmed code defect:** none isolated by these measurements. JIT compilation and nested Compose measure/recompose slices appear in traces, but their presence alone is not evidence of a defect.
- **Optimization:** none applied. A code change based on these emulator-only comparisons would be speculative. Before the next major screen migration, repeat this workload on a physical test device if safely available, or use this benchmark as a comparable emulator baseline and investigate any measured increase.

The run uses `CompilationMode.None()`, so the app executes from APK/runtime compilation rather than a release-trained profile. Benchmark variant source/configuration matches release except for local debug signing, `debuggable false`, `profileable shell=true`, and the benchmark-only activity. No R8 shrinking is enabled in the current release either. These details limit interpretation as a Play-install benchmark.

## Verification

Final verification on 2026-11-13:

| Check | Result |
|---|---|
| `./gradlew testDebugUnitTest` | PASS, 16 JVM tests / 5 suites |
| `./gradlew assembleDebug` | PASS |
| `./gradlew assembleRelease` | PASS |
| `./gradlew assembleDebugAndroidTest` | PASS |
| `./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark` | PASS |
| `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest` | PASS, 19/19 tests on isolated Android 16 emulator |
| `ANDROID_SERIAL=emulator-5554 ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest` | PASS, 6/6 tests; five measured scenarios at five iterations each plus the PSS-cycle test |
| `git diff --check` | PASS |
| Release APK fixture inspection | PASS; no `CoursesBenchmarkActivity`, benchmark profileable entry, fixture string, or `rollcall_compose_test` resource in release APK |

The first full rerun before persisting the emulator instrumentation argument failed all five Macrobenchmark methods because the harness rejected the emulator. The benchmark module now explicitly sets the documented `androidx.benchmark.suppressErrors=EMULATOR` argument, limited to the benchmark test APK. The final six-test run above then passed. Debug/benchmark builds emitted existing Java source/target 8 and Gradle deprecation warnings; release packaging also noted two native libraries that could not be stripped and were packaged unchanged.

No live account, real student record, production phone, authentication, Firebase, or external updater was used. The physical phone was visible to ADB but was never selected by Gradle or touched.

The earlier Phase 6B.1 debug-emulator measurements remain historical only. Their `/tmp` traces were lost; they were not reused as release-like evidence. See `PHASE6B_PERFORMANCE.md` for the original preliminary report and this notice's historical limits.
