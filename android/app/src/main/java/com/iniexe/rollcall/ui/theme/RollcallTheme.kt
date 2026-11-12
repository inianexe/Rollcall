package com.iniexe.rollcall.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class RollcallThemeColors(
    val background: Int,
    val contentPrimary: Int,
    val contentSecondary: Int,
    val surface: Int,
    val surfaceSubtle: Int,
    val border: Int,
    val brand: Int,
    val onBrand: Int,
    val brandMuted: Int,
    val highlight: Int,
    val attendanceSafe: Int,
    val attendanceWarning: Int,
    val attendanceCritical: Int,
    val progressTrack: Int,
    val primaryAction: Int,
    val onPrimaryAction: Int,
    val focusSurface: Int,
    val focusBorder: Int,
    val ticketPaper: Int,
    val ticketBacking: Int,
)

val LocalRollcallColors = staticCompositionLocalOf<RollcallThemeColors> {
    error("RollcallTheme must be provided")
}

@Composable
fun RollcallTheme(
    colors: RollcallThemeColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRollcallColors provides colors,
        content = content,
    )
}
