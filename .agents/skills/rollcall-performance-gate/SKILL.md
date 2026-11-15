---
name: rollcall-performance-gate
description: Measure RollCall startup, scrolling, rendering, and resource use on physical Android hardware before accepting performance changes.
---

# RollCall Performance Gate

Use when performance is the goal or a UI change may add recurring rendering work.

1. Read the relevant Phase 6 performance report and the installed `android-profiler` instructions. Check whether the target screen already has a baseline.
2. Use the same physical device, app build type, data volume, screen, and interaction for baseline and comparison. The RollCall workflow uses the user's phone, not an emulator; never install over the existing application without explicit authorization.
3. Record a repeatable baseline before optimizing. For scrolling or animation, capture frame timing and a Perfetto trace where supported; for startup or memory, select the matching profiler workflow.
4. Change only measured bottlenecks. Avoid extra invalidations, per-frame allocations, unbounded animation, unnecessary shadows/blur, or work that continues offscreen. Respect reduced motion and detach/lifecycle cancellation.
5. Repeat the same measurement after the change and report device/build details, sample size, medians/tail latency, missed-frame counts, and trace limitations. Do not infer improvement from one short run or from source inspection.
6. If physical-device profiling is unavailable, report the gate as unverified rather than substituting emulator numbers.
