package com.iniexe.rollcall.ui.screens.courses

enum class CourseFilter { ALL, BELOW_TARGET }

data class CoursesScreenUiState(
    val summary: String,
    val query: String,
    val filter: CourseFilter,
    val totalCourses: Int,
    val belowTargetCourses: Int,
    val groups: List<CourseCardUiState>,
    val emptyMessage: String,
    val emptyActionLabel: String?,
)
