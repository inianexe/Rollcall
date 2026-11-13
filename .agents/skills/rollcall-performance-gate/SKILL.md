---
name: rollcall-performance-gate
description: Plan and evaluate reproducible Android performance investigations for Rollcall startup, Compose rendering, scrolling, jank, CPU, or memory. Use when measuring or optimizing runtime performance.
---

# Rollcall performance gate

Read prior performance reports and reuse their workload where it remains representative. Make benchmark source/configuration reproducible in the repository; keep large Perfetto traces and raw outputs in the persistent QA directory `/home/inian/Projects/Rollcall-performance-qa/`, never only in `/tmp`. Keep that QA directory outside Git unless the user requests otherwise.

Use a non-debuggable, profileable, release-like target and a separate Macrobenchmark test process when available. Record device/emulator identity, OS, resolution, refresh rate, build variant, compilation mode, fixture, and iteration count. Run operations separately when attributing a bottleneck. Capture traces and inspect frame-timeline classifications, app and SurfaceFlinger deadlines, main/RenderThread scheduling, layout/recomposition, GC, and memory. Compare equivalent workloads and definitions; emulator results are not physical-device evidence.

Treat the previous Phase 6B.1R values as preliminary only: its worktree and traces were lost before commit. A fresh benchmark is required; do not claim those values were reproduced. Make a minimal optimization only when trace evidence supports a specific cause, then rerun the same scenario and preserve before/after artifacts. Never alter attendance calculations or remove functionality to improve a score. If an isolated emulator is unavailable, implement and verify reusable benchmark infrastructure, document the execution blocker, and do not invent measurements.
