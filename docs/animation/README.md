# Animation and Lottie

## Motion choice

- Use Compose animation APIs for layout, selection, loading-state and small tactile transitions.
- Use Lottie for authored vector timelines that are difficult to express clearly with Compose primitives.
- Use static artwork when motion adds no useful information.

Motion values live in `ui/animation/motion/RollcallMotion.kt`: zero-duration feedback, short interaction, standard and emphasized transitions, an expand/collapse size tween, and responsive/expressive spring specs. Use Compose-native animation APIs for view motion. Avoid indefinite animation in screens that are not visible.

## Lottie Compose

The app includes the official Airbnb `lottie-compose` library and the `RollcallLottie` adapter. It loads local `res/raw` resources only, follows the host lifecycle, defaults to a finite one-shot, and lets the caller stop playback or mark a tab hidden with `isVisible`. It uses Lottie's default system animator-scale behavior and guards one-shot completion callbacks against duplicate terminal-state notifications. Composition loading and error callbacks are caller-owned; the caller chooses an appropriate visible fallback. Lottie itself restarts from the beginning when `isPlaying` changes from false to true (`restartOnPlay` defaults to true).

The debug-only `rollcall_compose_test.json` is an original moving-dot fixture used by instrumentation tests; it is not included in release resources. There are still no production Lottie assets.

There are currently no production Lottie JSON assets. No unlicensed or placeholder animation is bundled. Add an asset only after checking its source/license, file size, renderer support, and light/dark appearance; place the file directly in `app/src/main/res/raw/` using a lowercase resource name. Do not add nested folders under `res/raw`.

Example:

```kotlin
RollcallLottie(
    animationRes = R.raw.sync_loading,
    isPlaying = uiState.isSyncing,
    iterations = LottieConstants.IterateForever,
    contentDescription = null,
)
```

The sync state remains owned by the sync code. A success/error animation must follow a confirmed result and must never determine whether the operation succeeded.

## Lifecycle and accessibility

Playback pauses when the lifecycle owner is below `STARTED`, the caller sets `isPlaying` false, or the caller sets `isVisible` false. Lottie respects Android's system animator scale by default. Decorative animations should use no content description; an animation that conveys necessary meaning needs equivalent text/state outside the animation.

## Verification

The wrapper was exercised on the isolated Android 16/API 36 x86_64 emulator using an original debug fixture: load, hidden pause, visible playback, one-shot completion, component remount/replay and missing-resource error callback passed. The physical phone was not targeted. Production app screens do not yet call this adapter. Java's Home sync glyph now pauses its infinite `ValueAnimator` when Home becomes hidden, resumes it when Home is shown, and cancels it on detach; an emulator test verifies this visibility behavior. Device profiling and Lottie-specific frame/memory profiling remain required when the first production asset is introduced.
