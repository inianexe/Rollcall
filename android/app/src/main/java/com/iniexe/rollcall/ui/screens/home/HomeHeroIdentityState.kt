package com.iniexe.rollcall.ui.screens.home

/** Live academic identity and a *real* date, rather than a decorative week number. */
data class HomeHeroIdentityState(
    val greeting: String,
    val name: String,
    val date: String,
    val week: String,
    val month: String,
    val note: String,
    val ticketLabel: String = "TODAY",
)
