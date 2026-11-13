package com.iniexe.rollcall.ui.screens.week

data class WeekScreenUiState(
    val showingHistory: Boolean,
    val timetableStatus: WeekDataStatus,
    val historyStatus: WeekDataStatus,
    val updatedText: String,
    val pulse: WeekPulseUiState,
    val timetableLoaded: Boolean,
    val scheduleDays: List<ScheduleDayUiState>,
    val scheduleEmptyTitle: String,
    val scheduleEmptyDetail: String,
    val scheduleActionLabel: String,
    val scheduleActionDescription: String,
    val history: List<AttendanceHistoryUiState>,
    val historyEmptyMessage: String?,
    val historyActionLabel: String,
    val historyActionDescription: String,
    val historyRemaining: Int,
)

enum class WeekDataStatus { NOT_LOADED, LOADED_EMPTY, POPULATED, CACHED, SYNC_FAILED, SESSION_EXPIRED }

data class WeekPulseUiState(
    val heading: String,
    val cyberpunk: Boolean,
    val counts: List<Int>,
    val dayNames: List<String>,
    val today: Int,
    val nextClassDay: Int,
    val dateLabels: List<String>,
    val meta: String,
)

data class ScheduleDayUiState(
    val date: String,
    val summary: String,
    val classCount: Int,
    val expanded: Boolean,
    val classes: List<ScheduleClassUiState>,
)

data class ScheduleClassUiState(
    val id: String,
    val time: String,
    val name: String,
    val code: String,
    val details: String,
    val allDay: Boolean,
    val cancelled: Boolean,
)

data class AttendanceHistoryUiState(
    val id: String,
    val day: String,
    val time: String,
    val name: String,
    val code: String,
    val details: String,
    val status: String,
    val statusColor: Int,
)
