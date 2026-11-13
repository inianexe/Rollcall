package com.iniexe.rollcall.ui.screens.courses

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.height
import com.iniexe.rollcall.AppPalette
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.theme.LocalRollcallColors
import com.iniexe.rollcall.ui.theme.RollcallMono
import com.iniexe.rollcall.ui.animation.motion.RollcallMotion

@Composable
fun CoursesScreen(
    state: CoursesScreenUiState,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (CourseFilter) -> Unit,
    onCourseClick: (String) -> Unit,
    onExploreWhatIf: (String) -> Unit,
    onEmptyAction: () -> Unit,
    onSearchBoundsChanged: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRollcallColors.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "courses-header") {
            Column {
                Text("02 / YOUR PROGRESS", style = labelStyle(colors.contentSecondary))
                Text("Courses", style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                    fontSize = 29.sp, color = Color(colors.contentPrimary)))
                Text("Attendance, stripped down to what matters. Can miss. Must attend. No guessing.",
                    style = TextStyle(fontFamily = RollcallMono, fontSize = 12.sp, color = Color(colors.contentSecondary)))
            }
        }
        item(key = "courses-summary") {
            if (state.totalCourses > 0) Text(state.summary,
                style = TextStyle(fontFamily = RollcallMono, fontSize = 12.sp, color = Color(colors.contentSecondary)))
        }
        item(key = "courses-search") { SearchField(state.query, onQueryChanged, onSearchBoundsChanged) }
        item(key = "courses-filters") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("All courses · ${state.totalCourses}", state.filter == CourseFilter.ALL,
                    Modifier.weight(1f), colors.surfaceSubtle, colors.border, colors.contentPrimary) {
                    onFilterChanged(CourseFilter.ALL)
                }
                FilterChip("Below target · ${state.belowTargetCourses}", state.filter == CourseFilter.BELOW_TARGET,
                    Modifier.weight(1f), colors.surfaceSubtle, colors.border, colors.contentPrimary) {
                    onFilterChanged(CourseFilter.BELOW_TARGET)
                }
            }
        }
        if (state.groups.isEmpty()) {
            item(key = "courses-empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth().then(if (state.emptyActionLabel != null)
                        Modifier.semantics { contentDescription = state.emptyActionLabel }.clickableAction(onEmptyAction) else Modifier),
                    color = Color(colors.surfaceSubtle), shape = RoundedCornerShape(12.dp),
                ) {
                    Text(state.emptyMessage, Modifier.padding(14.dp), style = TextStyle(fontFamily = RollcallMono,
                        fontSize = 13.sp, color = Color(colors.contentSecondary)))
                }
            }
        } else {
            items(state.groups, key = { it.headerCode.ifEmpty { it.title } }) { group ->
                CourseCard(group, onCourseClick, onExploreWhatIf)
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onBoundsChanged: (Float, Float, Float, Float) -> Unit,
) {
    val colors = LocalRollcallColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(color = Color(colors.surfaceSubtle), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(colors.border))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp)
            .onGloballyPositioned { bounds ->
                val rect = bounds.boundsInRoot()
                onBoundsChanged(rect.left, rect.top, rect.right, rect.bottom)
            }, verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value, onValueChange, Modifier.weight(1f)
                .semantics { contentDescription = "Search by subject or code" }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                textStyle = TextStyle(fontFamily = RollcallMono, fontSize = 14.sp, color = Color(colors.contentPrimary)),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("Search by subject or code", style = TextStyle(fontFamily = RollcallMono, fontSize = 14.sp, color = Color(colors.contentSecondary)))
                    inner()
                })
            if (value.isNotEmpty()) Text("Clear", Modifier.heightIn(min = 48.dp).clickableAction { onValueChange("") }
                .semantics { contentDescription = "Clear search" }, style = TextStyle(fontFamily = RollcallMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(colors.contentPrimary)))
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, modifier: Modifier, background: Int, border: Int, foreground: Int, onClick: () -> Unit) {
    Surface(modifier = modifier.heightIn(min = 48.dp).clickableAction(onClick).semantics { selected = isSelected; contentDescription = label },
        color = animateColorAsState(Color(if (isSelected) LocalRollcallColors.current.surface else background),
            animationSpec = tween(RollcallMotion.standardMillis, easing = RollcallMotion.standard), label = "course-filter").value,
        shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(border))) {
        Text(label, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                fontSize = 10.sp, color = Color(foreground)))
    }
}

private fun Modifier.clickableAction(onClick: () -> Unit): Modifier =
    clickable(role = Role.Button, onClick = onClick)

private fun labelStyle(color: Int) = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
    fontSize = 11.sp, color = Color(color))

@Preview(name = "Courses screen · light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CoursesScreenPreview() {
    RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
        CoursesScreen(previewState, {}, {}, {}, {}, {}, { _, _, _, _ -> }, Modifier.height(844.dp))
    }
}

@Preview(name = "Courses screen · empty", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun CoursesScreenEmptyPreview() {
    RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
        CoursesScreen(previewState.copy(totalCourses = 0, belowTargetCourses = 0, groups = emptyList(),
            summary = "", emptyMessage = "Your courses appear here after your first MyCamu sync. Tap to connect.",
            emptyActionLabel = "Sync MyCamu to load course attendance"), {}, {}, {}, {}, {},
            { _, _, _, _ -> }, Modifier.height(720.dp))
    }
}

@Preview(name = "Courses screen · long content / dark", showBackground = true,
    widthDp = 360, heightDp = 720, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CoursesScreenLongPreview() {
    RollcallTheme(AppPalette.get(true, 0).toComposeColors()) {
        CoursesScreen(previewState.copy(groups = listOf(previewState.groups.first().copy(
            title = "Advanced Programming, Embedded Systems and Applied Computing"))), {}, {}, {}, {}, {},
            { _, _, _, _ -> }, Modifier.height(720.dp))
    }
}

private val previewState = CoursesScreenUiState(
    "2 courses  ·  1 below target", "", CourseFilter.ALL, 2, 1,
    listOf(CourseCardUiState("Programming Fundamentals", "CS101",
        "Programming Fundamentals, needs focus. Tap to expand course guidance.",
        "Programming Fundamentals. Theory 80%, 16 attended / 20 total. Lab 60%, 12 attended / 20 total.",
        listOf(
            CourseMetricUiState("THEORY", "CS101", "Programming Fundamentals", 80f, "80%", "80% · 16 / 20 attended",
                "Above target", "You can miss 1 more class", "1", "0", false, false, 0xFF176146.toInt()),
            CourseMetricUiState("LAB", "CS101L", "Programming Fundamentals Lab", 60f, "60%", "60% · 12 / 20 attended",
                "Below target", "Attend 12 classes to reach target", "0", "12", true, true, 0xFFAA2921.toInt()),
        ))), "", null,
)
