# Phase 6A verification

Date: 2026-11-12

Branch: `codex/rollcall-torch-ui`

Starting commit: `22d58c9`

## Result

**PARTIALLY VERIFIED.** The production `MainActivity` lifecycle route, Java-to-Compose fixture updates, Java-owned theme preference across Activity recreation, and Courses rendering at tested font scales pass on the isolated Android 16 emulator. The full Gradle test/build set passes. Startup timings were sampled repeatedly, but frame timing, Courses scroll/search latency, recomposition counts, and a reliable memory baseline were not measured. Live external integrations were not tested.

## Environment and safety

- Emulator: `rollcall_phase4b` AVD, Android 16 / API 36, x86_64, emulator serial `emulator-5554`, 1080×2400 physical resolution, 420 dpi (411dp logical width). Narrow layout checks used 840×1800 pixels (320dp); the AVD was reset to 1080×2400 afterward.
- The physical phone (`002086557001418`) was listed by ADB but was never targeted by test/install commands. Its existing Rollcall installation and data were left alone.
- Lifecycle tests use an offline preference route fixture. No portal credentials, WebView cookies, authenticated session, or real student data are injected.
- Course rows are sanitized fixtures sent through the existing `LiveSnapshotStore` listener path. They do not exercise portal parsers or network synchronization.

## MainActivity lifecycle and Java-to-Compose propagation

`MainActivityLifecycleTest` launches the production `MainActivity` and covers:

- Fresh startup to the existing onboarding screen with no cached attendance.
- Dashboard routing using a local test-only preference fixture, including the initial empty Courses state.
- Five tab selection and return to Courses through the real Java dashboard shell.
- Attendance snapshot publication, initial course display, changed title/attendance, a course addition, and removal of a course absent from the next snapshot.
- Changing the existing attendance-target preference from 75% to 85%, recreating the Activity, and seeing Java's `Attendance`-computed Must attend value change from 12 to 34 for the 60% fixture.
- Switching the existing theme control on Home, confirming the Java control and Compose Courses colors update, and preserving the preference across Activity recreation.
- The existing selected-tab behavior: Activity recreation starts on Home; this was characterized and not changed.
- ActivityScenario `CREATED`/`RESUMED` transitions, repeated recreation, and system Back leaving the resumed `MainActivity`.

The Compose screen rendered the values received from the existing Java state mapping. No attendance formula or synchronization logic was changed.

**Not covered:** actual parser output from MyCamu/KCT, return from an external Activity, process death followed by a separate application relaunch, orientation changes, or a full refresh while a Compose field is focused. Those behaviors remain unverified.

## Theme persistence

- **VERIFIED:** light and dark palette mapping and live Compose theme repaint in component tests.
- **VERIFIED:** production Activity theme preference changes through the existing Home mode action and survives Activity recreation; both Java and Compose appearance were checked.
- **PARTIALLY VERIFIED:** the production theme action is surfaced on Home, so an in-place user toggle while the Courses tab is selected is not available. Compose live-palette updates are separately covered in the component host test.
- **NOT TESTED:** force-stop/process restart persistence and theme propagation during a live portal data refresh.

`AppPalette` and the existing `MainActivity` preferences remain authoritative; no second theme store was added.

## Accessibility and visual review

- Automated checks assert accessible tab names, course metric descriptions, search name, filter text, and the long course metric value.
- Font scale was set to 1.0×, 1.3×, 1.5×, and 2.0× on a 320dp-wide emulator; a standard 411dp layout was also captured. Long course names, 99,999/100,000 counts, large text, and scrolling to the course row were exercised.
- Screenshots were captured and manually reviewed for light/dark Courses layouts and narrow-width/font-scale cases. The large-text test scrolls the long course into view before capturing, so its header is not an initial-scroll-position screenshot.
- **VERIFIED fix:** large metric values no longer clip; at font scales above 1.3, visual bottom-navigation labels are hidden to prevent broken words in fixed-width tabs. Each tab retains its full accessible content description and icon/tap target.
- At 320dp, long course titles and summaries can visually ellipsize at 1.5×/2.0×; full content remains in the Compose accessibility semantics. Filters and attendance details wrap and remain scrollable.
- **NOT TESTED:** a human TalkBack session, hardware keyboard focus traversal, Android Accessibility Scanner, contrast measurement tooling, and large-display screenshots. Touch/semantics assertions do not replace a screen-reader review.

Screenshot files were kept as temporary QA artifacts under `/tmp/rollcall-phase6a-rechecked/`; they are not committed.

## Animation review

- The Java Home sync glyph now pauses its infinite `ValueAnimator` when Home is hidden, resumes when visible, and cancels on detach. `DashboardViewIntegrationTest` verifies pause/resume behavior.
- The existing Lottie wrapper test verifies fixture load, visible playback, hidden pause, one-shot completion, remount/replay, and missing-resource handling. The fixture exists only in debug resources and is absent from the release APK.
- No production Lottie asset or animation was added. Animation never controls application data.
- **NOT TESTED:** manual reduced-motion behavior across every screen, allocation profiling, and Lottie runtime profiling on a production screen. No production screen currently uses the Lottie adapter.

## Performance samples

Measurements are from the Android 16 x86_64 AVD, debug build, first-launch onboarding route after test state cleanup. They are an emulator smoke baseline, not a release benchmark and not comparable to a prior same-device benchmark.

| Metric | Samples | Summary | Status / limitation |
|---|---|---|---|
| Cold `am start -W` `TotalTime` | 1312, 1387, 1479, 1388, 1362 ms (n=5) | Median 1387 ms; range 1312–1479 ms | Measured Activity-start proxy; not time-to-usable-dashboard or a release profile |
| Warm resume `WaitTime` | 200, 91, 591, 81, 640 ms (n=5) | Median 200 ms; range 81–640 ms | High variance; `am start -W` reports a different subset of fields on task-to-front launches |
| Process PSS | 175,610 KB (single snapshot) | One debug-process snapshot | Not a repeatable memory baseline; no comparable before sample |
| Frame timing / jank | — | — | **NOT MEASURED:** `gfxinfo` did not retain usable frame stats after the instrumentation ActivityScenario finished |
| Courses scroll, search, filter, expand; recompositions/allocations | — | — | **NOT MEASURED:** no benchmark scenario/profiler trace was available in this phase |
| Release APK size | 12,428,245 bytes | 5 bytes smaller than the recorded Phase 5 APK (12,428,250 bytes) | Artifact size only; does not establish runtime performance |

No performance optimization beyond pausing the hidden Home animation was attempted.

## Verification commands

Run from `android/`:

| Command | Result |
|---|---|
| `./gradlew testDebugUnitTest` | **PASS** — 16 tests, 0 failures/errors/skips |
| `./gradlew assembleDebug` | **PASS** |
| `./gradlew assembleRelease` | **PASS**, existing local signing configuration; not published or installed on the phone |
| `./gradlew assembleDebugAndroidTest` | **PASS** |
| `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest` | **PASS** — 18/18 tests, 0 failures/skips on `rollcall_phase4b` (Android 16/API 36) |
| `git diff --check` | **PASS** |

Gradle emitted existing Java 8 source/target obsolescence and Gradle 9 deprecation warnings; they did not fail the build.

Two test-authoring issues were corrected before the final successful run: a Compose assertion extension was unavailable in this project's test dependency version, and a final snapshot assertion expected a course that the test had intentionally removed. Neither represented a production-app failure.

Release APK inspection confirmed the debug-only `rollcall_compose_test` animation resource is absent.

## Regression status

- Attendance calculations, parsing, authentication, persistence schemas, Firebase, notifications, workers, widgets, updater, application ID, and signing configuration were not changed.
- **NOT TESTED:** live MyCamu/KCT authentication or sync, Firebase/FCM, reminders/notification permission, workers, widgets, updater/download/install, or production-account data flows.
- No known critical regression was found in the covered local Activity, theme, and Courses paths.

## Remaining risks and next step

Phase 6A is not fully complete against its profiling and external-integration aspirations. The startup proxy is useful as a repeatable local reference, but Courses runtime smoothness and real accessibility still need dedicated profiling/manual checks. Keep Week in Java until those checks are completed and a separate Week behavior inventory is approved.
