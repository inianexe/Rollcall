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

class UpcomingExamCardView(context: Context) : AbstractComposeView(context) {
    private var state by mutableStateOf(UpcomingExamCardState(
        isUpcoming = false, title = "No upcoming exam found", type = "", session = "", countdown = "", date = "",
        details = "Refresh KCT from Marks & academics to check the latest schedule.",
        accessibleDescription = "No upcoming exam found. Refresh KCT from Marks and academics to check the latest schedule.",
        ink = 0xFF111827.toInt(), muted = 0xFF3E4651.toInt(), surface = 0xFFFFFFFF.toInt(),
        softSurface = 0xFFF1EEDA.toInt(), border = 0xFF121212.toInt(), accent = 0xFFCCFF00.toInt(),
        accentInk = 0xFF111827.toInt(), typeInk = 0xFF3E4651.toInt(),
        sessionSurface = 0xFFE7E8DD.toInt(), sessionInk = 0xFF111827.toInt(),
    ))

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    fun setExamState(state: UpcomingExamCardState) { this.state = state }

    @Composable
    override fun Content() { UpcomingExamCard(state) }
}
