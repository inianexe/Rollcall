package com.iniexe.rollcall.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iniexe.rollcall.ui.theme.LocalRollcallColors
import com.iniexe.rollcall.ui.theme.RollcallMono

/** Exact Torch Genesis native interpretation of the uploaded .hero + .ticket-art. */
@Composable
fun HomeHeroIdentity(
    state: HomeHeroIdentityState,
    modifier: Modifier = Modifier,
    onTicketClick: () -> Unit = {},
) {
    val colors = LocalRollcallColors.current
    Column(modifier.fillMaxWidth().padding(start = 21.dp, end = 18.dp, top = 19.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACADEMIC TRANSMISSION / 01", Modifier.weight(1f),
                style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                    fontSize = 9.sp, letterSpacing = .4.sp, color = colors.heroInk))
            Surface(color = colors.hero, shape = CircleShape,
                border = BorderStroke(1.dp, colors.heroInk)) {
                Text(state.date, Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                        fontSize = 9.sp, color = colors.heroInk))
            }
        }
        Spacer(Modifier.height(23.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(state.greeting, style = TextStyle(fontFamily = RollcallMono,
                    fontSize = 11.sp, color = colors.heroMuted))
                val headline = buildAnnotatedString {
                    append(state.name.trim().trimEnd('.'))
                    withStyle(SpanStyle(color = Color(0xFFFF4400))) { append(".") }
                    append("\nYOU GOT THIS.")
                }
                Text(headline, Modifier.padding(top = 8.dp), maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.ExtraBold,
                        fontSize = 27.sp, lineHeight = 28.sp,
                        letterSpacing = (-1.3).sp, color = colors.heroInk))
                Text(state.note, Modifier.padding(top = 7.dp), maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Medium,
                        fontSize = 9.sp, lineHeight = 13.sp, color = colors.heroMuted))
            }
            Spacer(Modifier.width(1.dp))
            CalendarTicket(state.week, state.month, state.ticketLabel, onTicketClick)
        }
    }
}

@Composable
private fun CalendarTicket(day: String, month: String, label: String, onClick: () -> Unit) {
    val ink = Color(0xFF121212)
    Box(Modifier.size(118.dp, 144.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Surface(Modifier.size(87.dp, 113.dp).offset(x = 9.dp, y = 8.dp).rotate(11f),
            color = Color(0xFFC8C8FF), shape = RoundedCornerShape(7.dp),
            border = BorderStroke(2.dp, ink)) { }
        Surface(Modifier.size(91.dp, 119.dp).offset(x = (-5).dp, y = (-3).dp).rotate((-4).toFloat()),
            color = Color(0xFFF1EEDA), shape = RoundedCornerShape(7.dp),
            border = BorderStroke(2.dp, ink)) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp)) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = RollcallMono, fontSize = 8.sp,
                        fontWeight = FontWeight.Bold, color = ink))
                Text(day, Modifier.padding(top = 10.dp),
                    style = TextStyle(fontFamily = RollcallMono, fontSize = 37.sp,
                        lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold, color = ink))
                Box(Modifier.fillMaxWidth().padding(top = 7.dp).height(1.dp).border(1.dp, ink))
                Text(month, Modifier.padding(top = 7.dp), maxLines = 1,
                    style = TextStyle(fontFamily = RollcallMono, fontSize = 8.sp,
                        fontWeight = FontWeight.Bold, color = ink))
            }
        }
        Box(Modifier.size(width = 17.dp, height = 25.dp)
            .offset(x = (-25).dp, y = (-62).dp)
            .border(4.dp, Color(0xFF707775), RoundedCornerShape(100)))
        Box(Modifier.size(width = 17.dp, height = 25.dp)
            .offset(x = 21.dp, y = (-62).dp)
            .border(4.dp, Color(0xFF707775), RoundedCornerShape(100)))
    }
}
