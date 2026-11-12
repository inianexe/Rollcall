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
    val ink: Int,
    val muted: Int,
    val surface: Int,
    val softSurface: Int,
    val border: Int,
    val accent: Int,
    val accentInk: Int,
    val typeInk: Int,
    val sessionSurface: Int,
    val sessionInk: Int,
)
