package com.iniexe.rollcall.ui.bridge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.iniexe.rollcall.ui.screens.week.WeekScreen
import com.iniexe.rollcall.ui.screens.week.WeekScreenUiState
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.theme.RollcallThemeColors

class WeekScreenView(
    context: Context,
    colors: RollcallThemeColors,
    state: WeekScreenUiState,
    private val onModeChanged: (Boolean) -> Unit,
    private val onDayToggled: (String) -> Unit,
    private val onLoadOlder: () -> Unit,
    private val onEmptyAction: () -> Unit,
    private val onLeaveOd: () -> Unit,
) : AbstractComposeView(context) {
    private var themeColors by mutableStateOf(colors)
    private var screenState by mutableStateOf(state)
    private var scrollRequest by mutableIntStateOf(0)

    init { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool) }

    fun update(colors: RollcallThemeColors, state: WeekScreenUiState) {
        themeColors = colors
        screenState = state
    }

    fun scrollToTop() { scrollRequest++ }

    @Composable
    override fun Content() {
        RollcallTheme(themeColors) {
            WeekScreen(screenState, onModeChanged, onDayToggled, onLoadOlder, onEmptyAction, onLeaveOd, scrollRequest)
        }
    }
}
