# Animation and Lottie

## Motion choice

- Use Compose animation APIs for layout, selection, loading-state and small tactile transitions.
- Use Lottie for authored vector timelines that are difficult to express clearly with Compose primitives.
- Use static artwork when motion adds no useful information.

Motion values live in `ui/animation/motion/MotionTokens.kt`. Respect Android's system animator scale; avoid indefinite animation in screens that are not visible.

## Lottie Compose

The app includes the official Airbnb `lottie-compose` library and the `RollcallLottie` adapter. It loads local `res/raw` resources only, follows the host lifecycle, defaults to a finite one-shot, and lets the caller stop playback. It uses Lottie's default system animator-scale behavior. Composition loading errors are returned to the caller; the caller chooses an appropriate visible fallback.

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

Playback pauses when the lifecycle owner is below `STARTED` or the caller sets `isPlaying` false. Lottie respects Android's system animator scale by default. Decorative animations should use no content description; an animation that conveys necessary meaning needs equivalent text/state outside the animation.

## Verification

Compile the wrapper with `assembleDebug`, and inspect every actual asset in the intended screen and on both themes before enabling it in production. Do not claim an asset was visually verified when no asset exists. Device profiling and Lottie-specific frame/memory profiling remain required when the first production asset is introduced.
