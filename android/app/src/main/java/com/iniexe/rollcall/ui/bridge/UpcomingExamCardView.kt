package com.iniexe.rollcall.ui.bridge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.iniexe.rollcall.ui.screens.home.UpcomingExamCard
import com.iniexe.rollcall.ui.screens.home.UpcomingExamCardState
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.theme.RollcallThemeColors

class UpcomingExamCardView(context: Context, initialColors: RollcallThemeColors) : AbstractComposeView(context) {
    private var colors by mutableStateOf(initialColors)
    private var state by mutableStateOf(UpcomingExamCardState(
        isUpcoming = false, title = "No upcoming exam found", type = "", session = "", countdown = "", date = "",
        details = "Refresh KCT from Marks & academics to check the latest schedule.",
        accessibleDescription = "No upcoming exam found. Refresh KCT from Marks and academics to check the latest schedule.",
    ))

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    fun setExamState(state: UpcomingExamCardState) { this.state = state }
    fun setTheme(colors: RollcallThemeColors) { this.colors = colors }

    @Composable
    override fun Content() { RollcallTheme(colors) { UpcomingExamCard(state) } }
}
