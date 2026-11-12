package com.iniexe.rollcall.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class RollcallThemeColors(
    val ink: Color,
    val muted: Color,
    val hero: Color,
    val heroInk: Color,
    val heroMuted: Color,
    val paper: Color,
    val border: Color,
)

data class RollcallSignalColors(
    val safe: Color,
    val warning: Color,
    val critical: Color,
)

val LocalRollcallColors = staticCompositionLocalOf {
    RollcallThemeColors(
        ink = Color(0xFF111827), muted = Color(0xFF3E4651), hero = Color(0xFFCCFF00),
        heroInk = Color(0xFF111827), heroMuted = Color(0xFF3E4651),
        paper = Color(0xFFF1EEDA), border = Color(0xFF121212)
    )
}

val LocalRollcallSignals = staticCompositionLocalOf {
    RollcallSignalColors(safe = Color(0xFF4C8B19), warning = Color(0xFFCE7A20), critical = Color(0xFFCB421B))
}

@Composable
fun RollcallTheme(
    colors: RollcallThemeColors = LocalRollcallColors.current,
    signals: RollcallSignalColors = LocalRollcallSignals.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRollcallColors provides colors,
        LocalRollcallSignals provides signals,
        content = content,
    )
}
