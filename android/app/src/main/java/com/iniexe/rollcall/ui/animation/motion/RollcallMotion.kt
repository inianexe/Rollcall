package com.iniexe.rollcall.ui.animation.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

object RollcallMotion {
    const val fastMillis = 150
    const val standardMillis = 240
    const val slowMillis = 320

    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
