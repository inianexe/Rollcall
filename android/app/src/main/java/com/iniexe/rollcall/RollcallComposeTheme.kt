package com.iniexe.rollcall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal data class RollcallTokens(
    val ink: Color,
    val muted: Color,
    val hero: Color,
    val heroInk: Color,
    val heroMuted: Color,
    val paper: Color,
    val border: Color
)

internal val LocalRollcallTokens = staticCompositionLocalOf {
    RollcallTokens(
        ink = Color(0xFF111827),
        muted = Color(0xFF3E4651),
        hero = Color(0xFFCCFF00),
        heroInk = Color(0xFF111827),
        heroMuted = Color(0xFF3E4651),
        paper = Color(0xFFF1EEDA),
        border = Color(0xFF121212)
    )
}

@Composable
internal fun RollcallComposeTheme(tokens: RollcallTokens, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRollcallTokens provides tokens, content = content)
}
