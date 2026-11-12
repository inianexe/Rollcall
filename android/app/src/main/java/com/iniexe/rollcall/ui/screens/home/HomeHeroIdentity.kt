package com.iniexe.rollcall.ui.screens.home

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.iniexe.rollcall.ui.theme.LocalRollcallColors
import com.iniexe.rollcall.ui.theme.RollcallMono
import com.iniexe.rollcall.ui.theme.RollcallTheme

@Composable
fun HomeHeroIdentity(state: HomeHeroIdentityState, modifier: Modifier = Modifier) {
    val colors = LocalRollcallColors.current
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ACADEMIC TRANSMISSION / 01",
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                    fontSize = 9.sp, letterSpacing = .6.sp, color = Color(colors.brandMuted))
            )
            Surface(color = Color(colors.brand), shape = CircleShape, border = BorderStroke(1.dp, Color(colors.onBrand))) {
                Text(state.date, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = TextStyle(
                    fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color(colors.onBrand)
                ))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(state.greeting, style = TextStyle(fontFamily = RollcallMono, fontSize = 11.sp, color = Color(colors.brandMuted)))
                Text(state.name, modifier = Modifier.padding(top = 4.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                        fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(colors.onBrand), letterSpacing = (-.8).sp))
                Text(state.note, modifier = Modifier.padding(top = 3.dp), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                        fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = .2.sp, color = Color(colors.brandMuted)))
            }
            Spacer(Modifier.width(8.dp))
            WeekTicket(state.week, state.month)
        }
    }
}

@Composable
private fun WeekTicket(week: String, month: String) {
    val colors = LocalRollcallColors.current
    Box(Modifier.size(width = 94.dp, height = 108.dp), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.size(width = 76.dp, height = 96.dp).graphicsLayer(rotationZ = 9f),
            color = Color(colors.ticketBacking), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(colors.border))) { }
        Surface(modifier = Modifier.size(width = 76.dp, height = 96.dp).graphicsLayer(rotationZ = -4f),
            color = Color(colors.ticketPaper), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(colors.border))) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("THE / WEEK", style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                    fontSize = 7.sp, color = Color(colors.border)))
                Text(week, modifier = Modifier.padding(top = 3.dp), style = TextStyle(fontFamily = RollcallMono,
                    fontWeight = FontWeight.Bold, fontSize = 32.sp, color = Color(colors.border), lineHeight = 34.sp))
                Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp), color = Color(colors.border)) { }
                Text(month, maxLines = 1, overflow = TextOverflow.Clip, style = TextStyle(fontFamily = RollcallMono,
                    fontWeight = FontWeight.Bold, fontSize = 7.sp, color = Color(colors.border)))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFCCFF00)
@Composable
private fun HomeHeroIdentityPreview() {
    RollcallTheme(com.iniexe.rollcall.AppPalette.get(false, 0).toComposeColors()) {
        HomeHeroIdentity(HomeHeroIdentityState("Good afternoon,", "ROLLCALL STUDENT.", "THU / 12 NOV",
            "46", "NOVEMBER", "ONE CLASS AT A TIME. YOUR WEEK, DECODED."), Modifier.padding(16.dp))
    }
}

@Preview(name = "Home identity · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeHeroIdentityDarkPreview() {
    RollcallTheme(com.iniexe.rollcall.AppPalette.get(true, 0).toComposeColors()) {
        HomeHeroIdentity(HomeHeroIdentityState("Good afternoon,", "ROLLCALL STUDENT.", "THU / 12 NOV",
            "46", "NOVEMBER", "ONE CLASS AT A TIME. YOUR WEEK, DECODED."), Modifier.padding(16.dp))
    }
}

@Preview(name = "Long student name", showBackground = true)
@Composable
private fun HomeHeroIdentityLongNamePreview() {
    RollcallTheme(com.iniexe.rollcall.AppPalette.get(false, 0).toComposeColors()) {
        HomeHeroIdentity(HomeHeroIdentityState("Good afternoon,",
            "A VERY LONG STUDENT NAME THAT NEEDS TO FIT", "THU / 12 NOV", "46", "NOVEMBER",
            "ONE CLASS AT A TIME. YOUR WEEK, DECODED."), Modifier.padding(16.dp))
    }
}
