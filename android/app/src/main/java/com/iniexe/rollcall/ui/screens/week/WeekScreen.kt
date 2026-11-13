package com.iniexe.rollcall.ui.screens.week

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iniexe.rollcall.ui.animation.motion.RollcallMotion
import com.iniexe.rollcall.ui.theme.LocalRollcallColors
import com.iniexe.rollcall.ui.theme.RollcallMono

@Composable
fun WeekScreen(
    state: WeekScreenUiState,
    onModeChanged: (Boolean) -> Unit,
    onDayToggled: (String) -> Unit,
    onLoadOlder: () -> Unit,
    onEmptyAction: () -> Unit,
    onLeaveOd: () -> Unit,
    scrollRequest: Int = 0,
) {
    val colors = LocalRollcallColors.current
    val listState = rememberLazyListState()
    LaunchedEffect(scrollRequest) { if (scrollRequest > 0) listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "week-header") {
            Column {
                Text("03 / TIME & ATTENDANCE", style = labelStyle(colors.contentSecondary))
                Text("Your week", style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                    fontSize = 29.sp, color = Color(colors.contentPrimary)))
                Text("Every class. Every record. In one place.", style = bodyStyle(colors.contentSecondary))
            }
        }
        item(key = "week-mode") { WeekModeSelector(state.showingHistory, onModeChanged) }
        item(key = "week-updated") {
            Text(state.updatedText, style = bodyStyle(colors.contentSecondary))
        }
        if (state.showingHistory) {
            item(key = "history-header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Attendance history", Modifier.weight(1f), style = sectionStyle(colors.contentPrimary))
                    Text("Leave & OD  →", Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onLeaveOd)
                        .semantics { contentDescription = "Open Leave and On Duty details" }
                        .padding(horizontal = 8.dp, vertical = 14.dp), style = labelStyle(colors.attendanceSafe))
                }
            }
            if (state.history.isEmpty()) {
                item(key = "history-empty") { EmptyWeekState(state.historyEmptyMessage.orEmpty(), onEmptyAction,
                    state.historyActionLabel, state.historyActionDescription) }
            } else {
                items(state.history, key = { it.id }) { record -> AttendanceHistoryRow(record) }
                if (state.historyRemaining > 0) {
                    item(key = "history-more") {
                        Text("Show older · ${state.historyRemaining} more",
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onLoadOlder)
                                .semantics { contentDescription = "Show ${state.historyRemaining} older attendance records" }
                                .padding(vertical = 14.dp),
                            style = labelStyle(colors.attendanceSafe))
                    }
                }
            }
        } else {
            item(key = "week-pulse") { WeekPulse(state.pulse) }
            item(key = "schedule-heading") { Text("This week", style = sectionStyle(colors.contentPrimary)) }
            if (!state.timetableLoaded || state.scheduleDays.isEmpty()) {
                item(key = "schedule-empty") {
                    EmptyWeekState("${state.scheduleEmptyTitle}\n${state.scheduleEmptyDetail}", onEmptyAction,
                        state.scheduleActionLabel, state.scheduleActionDescription)
                }
            } else {
                items(state.scheduleDays, key = { it.date }) { day ->
                    ScheduleDayCard(day, onDayToggled)
                }
            }
        }
    }
}

@Composable
private fun WeekModeSelector(showingHistory: Boolean, onModeChanged: (Boolean) -> Unit) {
    val colors = LocalRollcallColors.current
    Surface(color = Color(colors.surfaceSubtle), shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, Color(colors.border))) {
        Row(Modifier.padding(3.dp)) {
            ModeOption("Schedule", !showingHistory, Modifier.weight(1f)) { onModeChanged(false) }
            ModeOption("Attendance", showingHistory, Modifier.weight(1f)) { onModeChanged(true) }
        }
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalRollcallColors.current
    val background = animateColorAsState(Color(if (selected) colors.surface else colors.surfaceSubtle),
        tween(RollcallMotion.standardMillis, easing = RollcallMotion.standard), label = "week-mode").value
    Surface(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics { this.selected = selected; contentDescription = "Show $label${if (selected) ", selected" else ""}" },
        color = background, shape = RoundedCornerShape(10.dp)) {
        Text(label, Modifier.fillMaxWidth().padding(vertical = 14.dp),
            style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = Color(if (selected) colors.contentPrimary else colors.contentSecondary)))
    }
}

@Composable
private fun WeekPulse(state: WeekPulseUiState) {
    val colors = LocalRollcallColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(state.heading, style = labelStyle(colors.attendanceSafe))
        Text(state.meta, Modifier.padding(top = 3.dp), style = bodyStyle(colors.contentSecondary))
        Canvas(Modifier.fillMaxWidth().height(52.dp)
                        .semantics { contentDescription = state.counts.mapIndexed { index, count -> "${state.dayNames[index]} $count classes" }.joinToString(", ", "Weekly timetable: ") }) {
            val step = size.width / 7f
            val graphTop = 6.dp.toPx()
            val baseY = size.height - 5.dp.toPx()
            val max = (state.counts.maxOrNull() ?: 0).coerceAtLeast(1)
            val path = Path()
            state.counts.forEachIndexed { index, count ->
                val height = if (count == 0) 4.dp.toPx() else 8.dp.toPx() +
                    (baseY - graphTop - 8.dp.toPx()) * count / max
                val x = step * (index + .5f)
                val y = baseY - height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (!state.cyberpunk) drawPath(path, Color(colors.border), style = Stroke(width = 1.5.dp.toPx()))
            state.counts.forEachIndexed { index, count ->
                val height = if (count == 0) 4.dp.toPx() else 8.dp.toPx() +
                    (baseY - graphTop - 8.dp.toPx()) * count / max
                val x = step * (index + .5f)
                val y = baseY - height
                val markerColor = Color(when {
                    index == state.nextClassDay -> colors.attendanceWarning
                    count > 0 -> colors.highlight
                    else -> colors.border
                })
                if (state.cyberpunk) {
                    val half = minOf(12.dp.toPx(), step * .27f)
                    drawRect(Color(colors.surfaceSubtle), androidx.compose.ui.geometry.Offset(x - half, y),
                        androidx.compose.ui.geometry.Size(half * 2, baseY - y))
                    drawRect(Color(if (index == state.today) colors.attendanceWarning else colors.attendanceSafe),
                        androidx.compose.ui.geometry.Offset(x - half, y), androidx.compose.ui.geometry.Size(half * 2, 2.dp.toPx()))
                    val floor = (baseY - y) / count.coerceAtLeast(1)
                    repeat(count) { level ->
                        val fy = baseY - floor * (level + .5f)
                        drawRect(markerColor, androidx.compose.ui.geometry.Offset(x - half + 3.dp.toPx(), fy - 1.dp.toPx()),
                            androidx.compose.ui.geometry.Size((half - 5.dp.toPx()).coerceAtLeast(0f), 2.dp.toPx()))
                        drawRect(markerColor, androidx.compose.ui.geometry.Offset(x + 2.dp.toPx(), fy - 1.dp.toPx()),
                            androidx.compose.ui.geometry.Size((half - 5.dp.toPx()).coerceAtLeast(0f), 2.dp.toPx()))
                    }
                } else drawCircle(markerColor, radius = if (index == state.nextClassDay) 4.dp.toPx() else 3.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            state.dayNames.forEachIndexed { index, day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (state.counts[index] == 0) "·" else state.counts[index].toString(), style = TextStyle(
                        fontFamily = RollcallMono, fontSize = 10.sp, color = Color(colors.contentSecondary)))
                    Text(day.take(1) + "  " + state.dateLabels[index], style = TextStyle(fontFamily = RollcallMono,
                        fontSize = 9.sp, color = Color(if (index == state.today) colors.attendanceWarning else colors.contentSecondary)))
                }
            }
        }
    }
}

@Composable
private fun ScheduleDayCard(day: ScheduleDayUiState, onToggle: (String) -> Unit) {
    val colors = LocalRollcallColors.current
    Surface(color = Color(colors.surface), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(colors.border))) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button) { onToggle(day.date) }
                .semantics { stateDescription = if (day.expanded) "Expanded" else "Collapsed"; contentDescription = "${day.date}, ${day.classCount} ${if (day.classCount == 1) "class" else "classes"}, ${if (day.expanded) "collapse" else "expand"}" }
                .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.date, style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = Color(colors.contentPrimary)))
                    if (!day.expanded) Text(day.summary, style = bodyStyle(colors.contentSecondary))
                }
                Text("${day.classCount} ${if (day.classCount == 1) "class" else "classes"}", style = bodyStyle(colors.contentSecondary))
                Text(if (day.expanded) "−" else "+", Modifier.size(40.dp), style = TextStyle(fontFamily = RollcallMono,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(colors.attendanceSafe)))
            }
            AnimatedVisibility(day.expanded, enter = expandVertically(tween(RollcallMotion.expandCollapseMillis)),
                exit = shrinkVertically(tween(RollcallMotion.expandCollapseMillis))) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp)) {
                    day.classes.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.fillMaxWidth().height(1.dp).padding(start = 72.dp))
                        ScheduleClassRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleClassRow(item: ScheduleClassUiState) {
    val colors = LocalRollcallColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(item.time, Modifier.width(72.dp).heightIn(min = 48.dp), style = TextStyle(fontFamily = RollcallMono,
            fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(colors.attendanceSafe)))
        Column(Modifier.weight(1f)) {
            val title = item.name + if (item.code.isEmpty()) "" else "  ·  ${item.code}"
            Text(title, style = TextStyle(fontFamily = RollcallMono,
                fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(colors.contentPrimary)))
            if (item.details.isNotEmpty()) Text(item.details, style = bodyStyle(colors.contentSecondary))
        }
        if (item.cancelled) Text("Cancelled", Modifier.padding(start = 6.dp).semantics { contentDescription = "Class cancelled" },
            style = labelStyle(colors.contentSecondary))
    }
}

@Composable
private fun AttendanceHistoryRow(record: AttendanceHistoryUiState) {
    val colors = LocalRollcallColors.current
    Column {
        if (record.day.isNotEmpty()) Text(record.day.uppercase(), Modifier.padding(start = 2.dp, top = 9.dp, bottom = 3.dp), style = labelStyle(colors.contentSecondary))
        Surface(Modifier.fillMaxWidth(), color = Color(colors.surface), shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, Color(colors.border))) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.name,
                        style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(colors.contentPrimary)))
                    Text(record.details, style = bodyStyle(colors.contentSecondary))
                }
                Text(record.status, Modifier.padding(start = 8.dp).semantics { contentDescription = "${record.name}, ${record.status}" },
                    style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                        color = Color(if (record.statusColor == 0) colors.contentSecondary else record.statusColor)))
            }
        }
    }
}

@Composable
private fun EmptyWeekState(message: String, onClick: () -> Unit, actionLabel: String? = null, description: String? = null) {
    val colors = LocalRollcallColors.current
    Surface(Modifier.fillMaxWidth().then(if (actionLabel != null) Modifier.clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description ?: actionLabel } else Modifier),
        color = Color(colors.ticketBacking), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(message, style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                fontSize = 15.sp, color = Color(colors.onBrand)))
            if (actionLabel != null) Text(actionLabel, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp, bottom = 4.dp),
                style = labelStyle(colors.onBrand))
        }
    }
}

private fun labelStyle(color: Int) = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(color))
private fun sectionStyle(color: Int) = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(color))
private fun bodyStyle(color: Int) = TextStyle(fontFamily = RollcallMono, fontSize = 11.sp, color = Color(color))
