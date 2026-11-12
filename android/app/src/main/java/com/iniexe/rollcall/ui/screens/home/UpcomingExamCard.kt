package com.iniexe.rollcall.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iniexe.rollcall.ui.theme.RollcallMono
import com.iniexe.rollcall.ui.theme.LocalRollcallColors
import com.iniexe.rollcall.AppPalette
import com.iniexe.rollcall.ui.theme.RollcallTheme

@Composable
fun UpcomingExamCard(state: UpcomingExamCardState, modifier: Modifier = Modifier) {
    val colors = LocalRollcallColors.current
    Surface(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = state.accessibleDescription
        },
        color = Color(colors.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(colors.border)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("UPCOMING EXAM", style = TextStyle(fontFamily = RollcallMono,
                fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(colors.highlight)))
            Text(state.title, modifier = Modifier.padding(top = 5.dp), maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(colors.contentPrimary)))
            if (state.isUpcoming) {
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.type.isNotEmpty()) Badge(state.type, colors.surfaceSubtle, colors.contentSecondary)
                    if (state.session.isNotEmpty()) {
                        val isMorning = state.session == "AN"
                        val sessionColor = if (isMorning) colors.highlight else colors.attendanceWarning
                        Badge(state.session,
                            if (isMorning) sessionColor else withStatusTint(sessionColor),
                            if (isMorning) colors.surface else sessionColor)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(state.countdown, colors.brand, colors.onBrand)
                    Surface(modifier = Modifier.weight(1f), color = Color(colors.surfaceSubtle),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(colors.border))) {
                        Text(state.date, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(colors.contentPrimary)))
                    }
                }
            }
            if (state.details.isNotEmpty()) Text(state.details, modifier = Modifier.padding(top = 3.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                    fontSize = 11.sp, color = Color(colors.contentSecondary)))
        }
    }
}

private fun withStatusTint(color: Int): Int = (color and 0x00FFFFFF) or (0x24 shl 24)

@Composable
private fun Badge(text: String, background: Int, foreground: Int) {
    Surface(color = Color(background), shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(foreground)))
    }
}

private val previewExam = UpcomingExamCardState(
    isUpcoming = true,
    title = "Programming in C",
    type = "THEORY",
    session = "AN",
    countdown = "In 6 days",
    date = "Wed, 18 Nov · 9:00 AM",
    details = "CS101 · Embedded assessment",
    accessibleDescription = "Upcoming exam: Programming in C, theory, session AN, in 6 days",
)

@Preview(name = "Upcoming exam · light", showBackground = true)
@Composable
private fun UpcomingExamLightPreview() {
    RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
        UpcomingExamCard(previewExam, Modifier.padding(16.dp))
    }
}

@Preview(name = "Upcoming exam · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UpcomingExamDarkPreview() {
    RollcallTheme(AppPalette.get(true, 0).toComposeColors()) {
        UpcomingExamCard(previewExam, Modifier.padding(16.dp))
    }
}

@Preview(name = "No upcoming exam", showBackground = true)
@Composable
private fun NoUpcomingExamPreview() {
    RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
        UpcomingExamCard(UpcomingExamCardState(false, "No upcoming exam found", "", "", "", "",
            "Refresh KCT from Marks & academics to check the latest schedule.",
            "No upcoming exam found. Refresh KCT from Marks and academics to check the latest schedule."),
            Modifier.padding(16.dp))
    }
}

@Preview(name = "Long exam title", showBackground = true)
@Composable
private fun LongExamTitlePreview() {
    RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
        UpcomingExamCard(previewExam.copy(title = "Advanced Programming and Embedded Systems Practical Examination"),
            Modifier.padding(16.dp))
    }
}
