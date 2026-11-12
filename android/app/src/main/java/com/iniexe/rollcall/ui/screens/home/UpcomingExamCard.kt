package com.iniexe.rollcall.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iniexe.rollcall.ui.theme.RollcallMono

/** .exam-card from the supplied HTML, with live KCT state rather than sample results. */
@Composable
fun UpcomingExamCard(state: UpcomingExamCardState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().semantics { contentDescription = state.accessibleDescription }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(state.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(state.border))
        ) {
            Column(Modifier.padding(horizontal = 17.dp, vertical = 17.dp)) {
                Text("● UPCOMING EXAM", style = TextStyle(fontFamily = RollcallMono,
                    fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
                    letterSpacing = .5.sp, color = Color(state.accent)))
                Text(state.title.uppercase(), modifier = Modifier.padding(top = 13.dp),
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-.3).sp,
                        color = Color(state.ink)))
                if (state.isUpcoming) {
                    Row(Modifier.padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (state.type.isNotEmpty()) Badge(state.type, state.softSurface, state.ink)
                        if (state.session.isNotEmpty()) Badge(state.session,
                            state.sessionSurface, state.sessionInk)
                    }
                    Canvas(Modifier.fillMaxWidth().padding(top = 14.dp).height(1.dp)) {
                        drawLine(
                            color = Color(state.border), start = Offset(0f, 0f),
                            end = Offset(size.width, 0f), strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 13.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(state.countdown.uppercase(), style = TextStyle(
                                fontFamily = RollcallMono, fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp, color = Color(state.accentInk)))
                            Text(state.date, Modifier.padding(top = 5.dp), maxLines = 2,
                                overflow = TextOverflow.Ellipsis, style = TextStyle(
                                    fontFamily = RollcallMono, fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp, color = Color(state.muted)))
                        }
                        Text("↗", style = TextStyle(fontFamily = RollcallMono,
                            fontWeight = FontWeight.ExtraBold, fontSize = 24.sp,
                            color = Color(state.accentInk)))
                    }
                }
                if (state.details.isNotEmpty()) {
                    Text(state.details, modifier = Modifier.padding(top = 9.dp),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = RollcallMono, fontSize = 10.sp,
                            lineHeight = 15.sp, color = Color(state.muted)))
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-28).dp, y = 0.dp)
            .width(46.dp).height(7.dp)) {
            Surface(Modifier.fillMaxWidth(), color = Color(state.accent)) { }
        }
    }
}

@Composable
private fun Badge(title: String, background: Int, foreground: Int) {
    Surface(color = Color(background), shape = RoundedCornerShape(7.dp)) {
        Text(title, Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(
                fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                color = Color(foreground)))
    }
}
