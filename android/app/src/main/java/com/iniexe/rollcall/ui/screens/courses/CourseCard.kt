package com.iniexe.rollcall.ui.screens.courses

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.iniexe.rollcall.AppPalette
import com.iniexe.rollcall.ui.theme.LocalRollcallColors
import com.iniexe.rollcall.ui.theme.RollcallMono
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.animation.motion.RollcallMotion

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CourseCard(
    state: CourseCardUiState,
    onCourseClick: (String) -> Unit,
    onExploreWhatIf: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRollcallColors.current
    Surface(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = state.accessibleDescription
        },
        color = Color(colors.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(colors.border)),
    ) {
        Column(Modifier.animateContentSize(RollcallMotion.expandCollapse()).padding(14.dp)) {
            Text(
                state.title,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onCourseClick(state.headerCode) }
                    .semantics {
                        contentDescription = state.headerAccessibleDescription
                        role = Role.Button
                    }
                    .padding(vertical = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                    fontSize = 17.sp, color = Color(colors.contentPrimary)),
            )
            val columns = if (LocalDensity.current.fontScale > 1f || state.metrics.size == 1 ||
                state.metrics.any { maxOf(it.canMiss.length, it.mustAttend.length) > 4 }) 1 else 2
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val itemWidth = if (columns == 1) maxWidth else (maxWidth - 8.dp) / 2
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = columns,
                ) {
                    state.metrics.forEach { metric ->
                        SubjectMetric(
                            metric = metric,
                            modifier = Modifier.widthIn(min = 0.dp, max = itemWidth).fillMaxWidth(),
                            onCourseClick = { onCourseClick(metric.code) },
                            onExploreWhatIf = { onExploreWhatIf(metric.code) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectMetric(
    metric: CourseMetricUiState,
    modifier: Modifier,
    onCourseClick: () -> Unit,
    onExploreWhatIf: () -> Unit,
) {
    val colors = LocalRollcallColors.current
    val metricDescription = "${metric.kind} attendance for ${metric.accessibleName}: ${metric.percentLabel}, " +
        "can miss ${metric.canMiss} classes, must attend ${metric.mustAttend} classes"
    Surface(
        modifier = modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onCourseClick)
            .semantics(mergeDescendants = true) {
                contentDescription = metricDescription
                role = Role.Button
            },
        color = Color(colors.surface),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, Color(colors.border)),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(
                metric.kind + (metric.code.ifEmpty { "" }.let { if (it.isEmpty()) "" else " · $it" }),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                    fontSize = 9.sp, color = Color(colors.contentSecondary)),
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Outcome("CAN MISS", metric.canMiss,
                    if (metric.belowTarget) colors.contentSecondary else colors.attendanceSafe,
                    Modifier.weight(1f))
                Outcome("MUST ATTEND", metric.mustAttend,
                    if (metric.belowTarget) metric.progressColor else colors.contentSecondary,
                    Modifier.weight(1f))
            }
            AttendanceProgress(metric.percent, metric.percentLabel, metric.progressColor,
                Modifier.fillMaxWidth().padding(top = 7.dp))
            Text(
                metric.detail,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = RollcallMono, fontSize = 10.sp,
                    color = Color(colors.contentSecondary)),
            )
            if (metric.expanded) {
                Text(
                    metric.status + ". " + metric.guidance,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    style = TextStyle(fontFamily = RollcallMono, fontSize = 12.sp,
                        color = Color(colors.contentSecondary)),
                )
                Text(
                    "Try in What if?  →",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onExploreWhatIf)
                        .padding(vertical = 12.dp),
                    style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = Color(colors.attendanceSafe)),
                )
            }
        }
    }
}

@Composable
private fun Outcome(label: String, value: String, color: Int, modifier: Modifier) {
    val colors = LocalRollcallColors.current
    Column(modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
        Text(label, style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
            fontSize = 9.sp, color = Color(colors.contentSecondary)))
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                fontSize = if (value.length > 4) 10.sp else 18.sp, color = Color(color)))
    }
}

@Composable
private fun AttendanceProgress(percent: Float, label: String, color: Int, modifier: Modifier) {
    val colors = LocalRollcallColors.current
    Surface(
        modifier = modifier.heightIn(min = 8.dp).semantics {
            contentDescription = "Attendance $label"
            progressBarRangeInfo = ProgressBarRangeInfo(percent.coerceIn(0f, 100f) / 100f, 0f..1f)
        },
        color = Color(colors.border), shape = RoundedCornerShape(4.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(percent.coerceIn(0f, 100f) / 100f),
            color = Color(color), shape = RoundedCornerShape(4.dp),
        ) { }
    }
}

@Preview(name = "Courses · light", showBackground = true)
@Composable
private fun CourseCardPreview() {
    RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
        CourseCard(coursePreview, {}, {}, Modifier.padding(16.dp))
    }
}

@Preview(name = "Course below target · dark", showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CourseCardDarkPreview() {
    RollcallTheme(AppPalette.get(true, 0).toComposeColors()) {
        CourseCard(coursePreview.copy(metrics = listOf(coursePreview.metrics[0].copy(
            percent = 60f, percentLabel = "60%", status = "Below target", guidance = "Attend 12 classes in a row to reach 75%",
            canMiss = "0", mustAttend = "12", belowTarget = true, expanded = true))), {}, {}, Modifier.padding(16.dp))
    }
}

@Preview(name = "Course long title / no class counts", showBackground = true)
@Composable
private fun CourseCardLongContentPreview() {
    RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
        CourseCard(coursePreview.copy(title = "Advanced Programming and Embedded Systems",
            metrics = listOf(coursePreview.metrics[0].copy(percentLabel = "—", detail = "Attendance counts unavailable",
                canMiss = "—", mustAttend = "—"))), {}, {}, Modifier.padding(16.dp))
    }
}

private val coursePreview = CourseCardUiState(
    title = "Data Structures and Algorithms",
    headerCode = "24CSI024L",
    headerAccessibleDescription = "Data Structures and Algorithms. Tap to expand course guidance.",
    accessibleDescription = "Data Structures and Algorithms. Lab attendance 81.8%, 18 / 22 attended.",
    metrics = listOf(CourseMetricUiState("THEORY", "24CSI024", "Data Structures and Algorithms · 24CSI024",
        81.8f, "82%", "81.8% · 18 / 22 attended", "Above target", "You can miss 2 more periods",
        "2", "0", false, false, 0xFF176146.toInt()),
        CourseMetricUiState("LAB", "24CSI024L", "Data Structures and Algorithms Lab · 24CSI024L",
            60f, "60%", "60% · 12 / 20 attended", "Below target", "Attend 12 periods in a row to reach 75%",
            "0", "12", true, false, 0xFFAA2921.toInt())),
)
