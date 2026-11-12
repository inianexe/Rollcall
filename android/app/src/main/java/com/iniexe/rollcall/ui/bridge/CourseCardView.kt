package com.iniexe.rollcall.ui.bridge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.iniexe.rollcall.ui.screens.courses.CourseCard
import com.iniexe.rollcall.ui.screens.courses.CourseCardUiState
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.theme.RollcallThemeColors

class CourseCardView(
    context: Context,
    initialColors: RollcallThemeColors,
    initialState: CourseCardUiState,
    private val onCourseClick: (String) -> Unit,
    private val onExploreWhatIf: (String) -> Unit,
) : AbstractComposeView(context) {
    private var colors by mutableStateOf(initialColors)
    private var state by mutableStateOf(initialState)

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    @Composable
    override fun Content() {
        RollcallTheme(colors) {
            CourseCard(state, onCourseClick, onExploreWhatIf)
        }
    }
}
