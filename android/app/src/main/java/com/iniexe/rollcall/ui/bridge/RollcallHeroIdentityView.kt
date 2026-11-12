package com.iniexe.rollcall.ui.bridge

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.iniexe.rollcall.ui.screens.home.HomeHeroIdentity
import com.iniexe.rollcall.ui.screens.home.HomeHeroIdentityState
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.theme.RollcallThemeColors

class RollcallHeroIdentityView(context: Context, initialColors: RollcallThemeColors) : AbstractComposeView(context) {
    private var state by mutableStateOf(HomeHeroIdentityState("Good afternoon,", "MyCamu student.", "", "", "", "ONE CLASS AT A TIME. YOUR WEEK, DECODED."))
    private var colors by mutableStateOf(initialColors)

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    fun setIdentity(state: HomeHeroIdentityState) { this.state = state }

    fun setTheme(colors: RollcallThemeColors) { this.colors = colors }

    @Composable
    override fun Content() {
        RollcallTheme(colors = colors) { HomeHeroIdentity(state) }
    }
}
