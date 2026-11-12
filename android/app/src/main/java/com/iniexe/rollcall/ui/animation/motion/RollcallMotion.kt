package com.iniexe.rollcall.ui.animation.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize

object RollcallMotion {
    const val instantMillis = 0
    const val shortMillis = 120
    const val standardMillis = 220
    const val emphasizedMillis = 320
    const val expandCollapseMillis = 180

    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasized: Easing = CubicBezierEasing(0.18f, 0f, 0f, 1f)

    fun interactionSpring(): SpringSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 520f)

    fun expressiveSpring(): SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 320f)

    fun expandCollapse(): FiniteAnimationSpec<IntSize> =
        tween(durationMillis = expandCollapseMillis, easing = standard)
}
