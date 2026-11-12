package com.iniexe.rollcall

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RollcallMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

class RollcallHeroIdentityView(context: Context) : AbstractComposeView(context) {
    private var greeting by mutableStateOf("Good afternoon,")
    private var studentName by mutableStateOf("MyCamu student.")
    private var date by mutableStateOf("")
    private var week by mutableStateOf("")
    private var month by mutableStateOf("")
    private var note by mutableStateOf("ONE CLASS AT A TIME. YOUR WEEK, DECODED.")
    private var tokens by mutableStateOf(
        RollcallTokens(
            ink = Color(0xFF111827), muted = Color(0xFF3E4651), hero = Color(0xFFCCFF00),
            heroInk = Color(0xFF111827), heroMuted = Color(0xFF3E4651),
            paper = Color(0xFFF1EEDA), border = Color(0xFF121212)
        )
    )

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    fun setIdentity(greeting: String, name: String, date: String, week: String, month: String, note: String) {
        this.greeting = greeting
        studentName = name
        this.date = date
        this.week = week
        this.month = month
        this.note = note
    }

    fun setColors(hero: Int, heroInk: Int, heroMuted: Int, paper: Int, ink: Int, border: Int) {
        tokens = RollcallTokens(
            ink = Color(ink), muted = Color(heroMuted), hero = Color(hero), heroInk = Color(heroInk),
            heroMuted = Color(heroMuted), paper = Color(paper), border = Color(border)
        )
    }

    @Composable
    override fun Content() {
        RollcallComposeTheme(tokens) {
            HeroIdentity(greeting, studentName, date, week, month, note)
        }
    }
}

@Composable
private fun HeroIdentity(greeting: String, name: String, date: String, week: String, month: String, note: String) {
    val colors = LocalRollcallTokens.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ACADEMIC TRANSMISSION / 01",
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                    fontSize = 9.sp, letterSpacing = .6.sp, color = colors.heroMuted)
            )
            Surface(
                color = colors.hero,
                shape = CircleShape,
                border = BorderStroke(1.dp, colors.heroInk)
            ) {
                Text(date, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = TextStyle(
                    fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                    color = colors.heroInk
                ))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(greeting, style = TextStyle(fontFamily = RollcallMono, fontSize = 11.sp, color = colors.heroMuted))
                Text(
                    name,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                        fontSize = 24.sp, color = colors.heroInk, letterSpacing = (-.8).sp)
                )
                Text(
                    note,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                        fontSize = 9.sp, letterSpacing = .2.sp, color = colors.heroMuted)
                )
            }
            Spacer(Modifier.width(8.dp))
            WeekTicket(week, month, colors)
        }
    }
}

@Composable
private fun WeekTicket(week: String, month: String, colors: RollcallTokens) {
    Box(Modifier.size(width = 94.dp, height = 108.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(width = 76.dp, height = 96.dp).graphicsLayer(rotationZ = 9f),
            color = Color(0xFFC8C8FF), shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colors.border)
        ) { }
        Surface(
            modifier = Modifier.size(width = 76.dp, height = 96.dp).graphicsLayer(rotationZ = -4f),
            color = colors.paper, shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("THE / WEEK", style = TextStyle(fontFamily = RollcallMono,
                    fontWeight = FontWeight.Bold, fontSize = 7.sp, color = colors.border))
                Text(week, modifier = Modifier.padding(top = 3.dp), style = TextStyle(fontFamily = RollcallMono,
                    fontWeight = FontWeight.Bold, fontSize = 32.sp, color = colors.border, lineHeight = 34.sp))
                Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp), color = colors.border) { }
                Text(month, maxLines = 1, overflow = TextOverflow.Clip,
                    style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                        fontSize = 7.sp, color = colors.border))
            }
        }
    }
}
