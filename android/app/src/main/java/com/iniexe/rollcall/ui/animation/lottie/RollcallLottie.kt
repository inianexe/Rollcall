package com.iniexe.rollcall.ui.animation.lottie

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun RollcallLottie(
    @RawRes animationRes: Int,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    iterations: Int = 1,
    speed: Float = 1f,
    contentDescription: String? = null,
    onLoadError: ((Throwable) -> Unit)? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) lifecycleStarted = true
            if (event == Lifecycle.Event.ON_STOP) lifecycleStarted = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val compositionResult = rememberLottieComposition(LottieCompositionSpec.RawRes(animationRes))
    val composition: LottieComposition? = compositionResult.value
    val loadError = compositionResult.error
    LaunchedEffect(loadError) { if (loadError != null) onLoadError?.invoke(loadError) }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying && lifecycleStarted,
        iterations = if (iterations == LottieConstants.IterateForever) iterations else iterations.coerceAtLeast(1),
        speed = speed,
    )
    val semantics = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
    }
    Box(modifier.then(semantics)) {
        if (composition != null) LottieAnimation(composition, { progress }, modifier = Modifier.fillMaxSize())
    }
}
