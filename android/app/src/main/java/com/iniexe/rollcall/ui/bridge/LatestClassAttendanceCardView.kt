package com.iniexe.rollcall.ui.bridge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.iniexe.rollcall.ui.screens.home.LatestClassAttendanceCard
import com.iniexe.rollcall.ui.screens.home.LatestClassAttendanceState
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.theme.RollcallThemeColors

class LatestClassAttendanceCardView(
    context: Context,
    initialColors: RollcallThemeColors,
    private val onOpenHistory: Runnable,
) : AbstractComposeView(context) {
    private var colors by mutableStateOf(initialColors)
    private var state by mutableStateOf(LatestClassAttendanceState("", "", "", "Latest class attendance. Open attendance history"))

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    fun setAttendanceState(state: LatestClassAttendanceState) { this.state = state }
    fun setTheme(colors: RollcallThemeColors) { this.colors = colors }

    @Composable
    override fun Content() {
        RollcallTheme(colors) { LatestClassAttendanceCard(state, onClick = { onOpenHistory.run() }) }
    }
}
