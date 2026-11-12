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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iniexe.rollcall.ui.theme.RollcallMono

@Composable
fun UpcomingExamCard(state: UpcomingExamCardState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = state.accessibleDescription
        },
        color = Color(state.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(state.border)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("UPCOMING EXAM", style = TextStyle(fontFamily = RollcallMono,
                fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(state.accent)))
            Text(state.title, modifier = Modifier.padding(top = 5.dp), maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(state.ink)))
            if (state.isUpcoming) {
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.type.isNotEmpty()) Badge(state.type, state.softSurface, state.typeInk)
                    if (state.session.isNotEmpty()) Badge(state.session, state.sessionSurface, state.sessionInk)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(state.countdown, state.accent, state.accentInk)
                    Surface(modifier = Modifier.weight(1f), color = Color(state.softSurface),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(state.border))) {
                        Text(state.date, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(state.ink)))
                    }
                }
            }
            if (state.details.isNotEmpty()) Text(state.details, modifier = Modifier.padding(top = 3.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                    fontSize = 11.sp, color = Color(state.muted)))
        }
    }
}

@Composable
private fun Badge(text: String, background: Int, foreground: Int) {
    Surface(color = Color(background), shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(foreground)))
    }
}
