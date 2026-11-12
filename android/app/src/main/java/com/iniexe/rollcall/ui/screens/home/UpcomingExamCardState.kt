package com.iniexe.rollcall.ui.screens.home

data class UpcomingExamCardState(
    val isUpcoming: Boolean,
    val title: String,
    val type: String,
    val session: String,
    val countdown: String,
    val date: String,
    val details: String,
    val accessibleDescription: String,
)
