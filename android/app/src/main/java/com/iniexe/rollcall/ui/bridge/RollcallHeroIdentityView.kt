package com.iniexe.rollcall.ui.bridge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.graphics.Color
import com.iniexe.rollcall.ui.screens.home.HomeHeroIdentity
import com.iniexe.rollcall.ui.screens.home.HomeHeroIdentityState
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.theme.RollcallThemeColors

class RollcallHeroIdentityView(context: Context) : AbstractComposeView(context) {
    private var state by mutableStateOf(HomeHeroIdentityState("Good afternoon,", "MyCamu student.", "", "", "", "ONE CLASS AT A TIME. YOUR WEEK, DECODED."))
    private var ticketAction by mutableStateOf<Runnable>(Runnable {})
    private var colors by mutableStateOf(
        RollcallThemeColors(Color(0xFF111827), Color(0xFF3E4651), Color(0xFFCCFF00),
            Color(0xFF111827), Color(0xFF3E4651), Color(0xFFF1EEDA), Color(0xFF121212))
    )

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    fun setIdentity(state: HomeHeroIdentityState) { this.state = state }
    fun setTicketAction(action: Runnable) { ticketAction = action }

    fun setColors(hero: Int, heroInk: Int, heroMuted: Int, paper: Int, ink: Int, border: Int) {
        colors = RollcallThemeColors(Color(ink), Color(heroMuted), Color(hero), Color(heroInk),
            Color(heroMuted), Color(paper), Color(border))
    }

    @Composable
    override fun Content() {
        RollcallTheme(colors = colors) { HomeHeroIdentity(state, onTicketClick = { ticketAction.run() }) }
    }
}
