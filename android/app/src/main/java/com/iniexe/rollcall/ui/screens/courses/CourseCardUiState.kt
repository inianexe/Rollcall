package com.iniexe.rollcall.ui.screens.courses

data class CourseMetricUiState(
    val kind: String,
    val code: String,
    val accessibleName: String,
    val percent: Float,
    val percentLabel: String,
    val detail: String,
    val status: String,
    val guidance: String,
    val canMiss: String,
    val mustAttend: String,
    val belowTarget: Boolean,
    val expanded: Boolean,
    val progressColor: Int,
)

data class CourseCardUiState(
    val title: String,
    val headerCode: String,
    val headerAccessibleDescription: String,
    val accessibleDescription: String,
    val metrics: List<CourseMetricUiState>,
)
