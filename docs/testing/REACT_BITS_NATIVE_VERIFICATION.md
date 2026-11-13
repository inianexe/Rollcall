# React Bits native verification — 13 November 2026

Branch: `refactor/rollcall-java-torch`. The nine effects are Java Views in the actual RollCall dashboard. No Compose, WebView dashboard, onboarding, attendance math, GPA math, or navigation rewrite was introduced.

## Reference and method

The reference is the [official React Bits source](https://github.com/DavidHDev/react-bits/tree/main/public/r), specifically each component's `*-JS-CSS.json` registry entry (BellToggle, StatusMark, JellyRadio, CodeSlots, LetterGlitch, Shuffle, OptionWheel, FolderFloat, AnimatedContent). I inspected the component JS/CSS and motion defaults. Live browser recordings of all nine official demos were not available in this run; timing and geometry comparisons below are source-based. Native screen captures from the physical phone are in [react-bits-captures](react-bits-captures/). The debug host uses sanitized fixture data; the installed release app still uses the original live data paths.

Status means overall evidence, not whether the class exists. `PARTIALLY VERIFIED` identifies a specific unverified aspect; no component is marked fully verified from a still frame.

| Component | Source behavior and native result | Integration and checks | Status / limit |
| --- | --- | --- | --- |
| Bell Toggle | 17° decaying five-pass swing over 820 ms and 300 ms pill reveal, matching the source's default ring amplitude/passes/duration and spring UI duration. Canvas bell is simpler than the optional source waves/badge. | Week and Settings use `classRemindersEnabled`, existing permission and hold-to-disable paths. Disabled animations preserve the control. | PARTIALLY VERIFIED: native interaction and state wiring checked; permission prompt and Settings appearance were not exercised with a signed-in phone session. |
| Status Mark | Pending ring, running arc, success check, and failure cross, with 300 ms morph and no numeric progress claim. Success contrast was corrected on the lime Home card. | Home sync state comes from the existing `syncSucceeded`/status snapshot. State and accessibility description are asserted. | PARTIALLY VERIFIED: success captured; running and failure animation sequences were inspected in code, not recorded from a live failed sync. |
| Jelly Radio | Selected chip swells, adjacent chip shifts, and a 440 ms overshoot settles. Chips now expand vertically at 2× font scale. | Courses filters real rows; Week selects attendance/schedule; What If selects miss/attend. Filter result and selection state asserted. | PARTIALLY VERIFIED: functional states captured; spring shape is an Android approximation of the source's per-chip spring physics. |
| Code Slots | Six real server-issued digits rise in 20 ms stagger and settle over 300 ms. Invalid codes are rejected; the dialog uses `FLAG_SECURE` and a sensitive clipboard flag. | Existing web sign-in code dialog only; this is a read-only code display, so keyboard/paste input does not apply. Validation and accessible code text asserted. | PARTIALLY VERIFIED: no live sign-in challenge was generated in QA. |
| Letter Glitch | Canvas character field mutates a bounded subset at at most ~8 fps; redraw stops when hidden or animations are off. Per-character allocation was removed. | Home Status / Live card, beneath readable status text. Dark and light captures inspected. | PARTIALLY VERIFIED: source includes richer pointer effects and wave physics that are omitted; no full RenderThread trace was collected. |
| Shuffle | Headline scrambles individual characters for 520 ms and restores the exact final text/spans. | Home headline; final text asserted. | PARTIALLY VERIFIED: the source also supports 3D rolling and hover modes; only the requested headline transition is ported. |
| Option Wheel | Curved Canvas positions, 200 ms smoothing, drag/tap, snapping, plus Prev/Next and accessibility click. | Marks filters visible semester results while CGPA remains calculated from all academic records. Wheel and selection exercised on emulator. | PARTIALLY VERIFIED: large real-world semester labels and TalkBack gestures were not manually tested. |
| Folder Float | Canvas flap and floating paper options open from the Week history control. | Options open existing This semester / All Leave & OD views; the latter navigation is asserted. | PARTIALLY VERIFIED: source has optional continuous physics drift; native component uses bounded open/close motion. |
| Animated Content | Visible-in-viewport, one-time 560 ms alpha/translate reveal; child transforms reset on detach. | Home, Courses, Week, Marks. A stable reveal key prevents replay on dashboard rebuild. | PARTIALLY VERIFIED: long production lists were not profiled independently. |

## App and accessibility checks

- Emulator: JVM suite and five connected instrumentation tests passed. Covered dark/light Home, Courses, Week, What If, Marks; real filter and folder actions; exact code validation; empty data; rapid tab changes; activity recreation. The same dashboard tests passed at Android font scale `2.0`, then scale was restored to `1.0`. Animator duration scale `0` kept controls operable during the profiling pass, then was restored to `1`.
- Physical phone `002086557001418`: release APK installed as `com.iniexe.rollcall`; four React Bits instrumentation tests passed in a separate `com.iniexe.rollcall.qa` debug build, using the same Java views and sanitized fixture. Five phone captures are committed here. The installed release app was launched and its real What If screen inspected. No physical-device frame timing claim is made.
- After the phone review, the Home ticket's binder loops were moved down and unclipped, and the Home/Week rhythm graphs were given separate graph and label bands. Fresh release screenshots on the phone showed the complete loops and no day-letter/graph overlap. Those release screenshots contain personal academic data and are kept outside Git.
- Functional Views have click/focus semantics and selected/status descriptions. Code digits are exposed as one accessible description and not as six focus targets. Folder options and wheel selection have non-drag controls. Manual TalkBack reading order and contrast measurement were not completed.
- Existing academic calculations, navigation actions, and auth were left in their original owners. The Code Slots display accepts only a six-digit code returned by the existing authentication flow.

## Emulator performance sample

`dumpsys gfxinfo com.iniexe.rollcall` was reset during the same 12-cycle Home/Courses/Week navigation workload on the isolated API 16 emulator. One approximately 7-second sample per condition:

| Animator scale | Frames | 50th / 90th / 95th / 99th | Deadline misses | Total PSS |
| --- | ---: | --- | ---: | ---: |
| 1 | 277 | 24 / 31 / 34 / 150 ms | 36 (13.0%) | 93,565 KB |
| 0 | 23 | 16 / 16 / 30 / 31 ms | 13 (56.5%) | 93,163 KB |

The sample is too short and the frame counts differ too much to infer a reliable overhead percentage. The off state confirms most ongoing frames are animation driven. The 24 ms animated median and 13% deadline misses do **not** establish smooth 60 fps performance on this emulator. `LetterGlitchView` schedules at most one redraw every 120 ms while visible, and `AnimatedContentView` removes its scroll listener and cancels child animation on detach. Folder opening changes layout once. No main-thread/RenderThread trace or allocation timeline was captured, so bottleneck attribution remains open.

## Remaining verification

Capture official interactive demos and native beginning/middle/end video for each effect; test failed/offline/expired sync and a real web sign-in challenge; manually check Settings and TalkBack on a signed-in device; run longer scroll and frame traces on production-length Courses and Week lists. These limits do not affect the source-level integration or the passing fixture tests, but they prevent a full visual/performance fidelity verdict.

## Phone signing

The previous phone install used a different debug certificate. At the user's direction, it was removed and replaced with the configured release-signed APK. The stable keystore is stored outside Git at `/home/inian/.config/rollcall/rollcall-release.jks`; its SHA-256 certificate digest is `19117b6b5320c6adb907834880f031d00040e0a3114a9a8d3633539349bf1bc6`. Future updates must use this keystore. The prior app's local data was cleared by uninstall; Android may restore data from its own backup.
