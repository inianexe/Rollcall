package com.iniexe.rollcall.ui.bridge

import android.content.Context
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.iniexe.rollcall.ui.screens.courses.CourseFilter
import com.iniexe.rollcall.ui.screens.courses.CoursesScreen
import com.iniexe.rollcall.ui.screens.courses.CoursesScreenUiState
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.theme.RollcallThemeColors

class CoursesScreenView(
    context: Context,
    colors: RollcallThemeColors,
    state: CoursesScreenUiState,
    private val onQueryChanged: (String) -> Unit,
    private val onFilterChanged: (CourseFilter) -> Unit,
    private val onCourseClick: (String) -> Unit,
    private val onExploreWhatIf: (String) -> Unit,
    private val onEmptyAction: () -> Unit,
) : AbstractComposeView(context) {
    private var themeColors by mutableStateOf(colors)
    private var screenState by mutableStateOf(state)
    private val searchBounds = RectF()

    init { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool) }

    fun update(colors: RollcallThemeColors, state: CoursesScreenUiState) {
        themeColors = colors
        screenState = state
    }

    fun isSearchTouch(x: Float, y: Float): Boolean = searchBounds.contains(x, y)

    @Composable
    override fun Content() {
        RollcallTheme(themeColors) {
            CoursesScreen(screenState, onQueryChanged, onFilterChanged, onCourseClick, onExploreWhatIf,
                onEmptyAction, { left, top, right, bottom -> searchBounds.set(left, top, right, bottom) })
        }
    }
}
