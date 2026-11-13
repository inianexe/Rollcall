package com.iniexe.rollcall.ui.screens.week

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.iniexe.rollcall.AppPalette
import com.iniexe.rollcall.ui.theme.RollcallTheme

private val sampleWeek = WeekScreenUiState(
    showingHistory = false,
    timetableStatus = WeekDataStatus.POPULATED,
    historyStatus = WeekDataStatus.POPULATED,
    updatedText = "MyCamu timetable · Updated just now",
    pulse = WeekPulseUiState("WEEK RHYTHM", false, List(7) { if (it in 1..5) it % 3 + 1 else 0 },
        listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"), 3, 4,
        listOf("8", "9", "10", "11", "12", "13", "14"), "9 classes across 5 campus days"),
    timetableLoaded = true,
    scheduleDays = listOf(ScheduleDayUiState("Wednesday, 11 Nov", "9:00 AM – 2:00 PM", 2, true,
        listOf(ScheduleClassUiState("fixture-1", "9:00 AM\n10:00 AM", "Applied Computing", "CS101", "Room 2 · Dr Tutor", false, false),
            ScheduleClassUiState("fixture-2", "All day", "Campus event", "EVENT", "", true, true))),
        ScheduleDayUiState("Thursday, 12 Nov", "10:00 AM – 1:00 PM", 3, false, emptyList())),
    scheduleEmptyTitle = "NO CLASSES ON YOUR SCHEDULE.",
    scheduleEmptyDetail = "Your connected timetable will appear here when classes are scheduled.",
    scheduleActionLabel = "Sync timetable  →",
    scheduleActionDescription = "Sync timetable from MyCamu",
    history = listOf(AttendanceHistoryUiState("history-1", "Wednesday, 11 Nov 2026", "9:00 AM", "Applied Computing",
        "CS101", "CS101  ·  9:00 AM\nOn Duty · Approved", "Present", 0xff24774f.toInt())),
    historyEmptyMessage = null,
    historyActionLabel = "Refresh attendance history  →",
    historyActionDescription = "Sync attendance history from MyCamu",
    historyRemaining = 0,
)

@Preview(name = "Week · light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WeekLightPreview() = RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
    WeekScreen(sampleWeek, {}, {}, {}, {}, {})
}

@Preview(name = "Week · dark", showBackground = true, widthDp = 390, heightDp = 844,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeekDarkPreview() = RollcallTheme(AppPalette.get(true, 0).toComposeColors()) {
    WeekScreen(sampleWeek.copy(showingHistory = true), {}, {}, {}, {}, {})
}

@Preview(name = "Week · empty schedule", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun WeekEmptyPreview() = RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
    WeekScreen(sampleWeek.copy(timetableLoaded = true, scheduleDays = emptyList()), {}, {}, {}, {}, {})
}
