package com.iniexe.rollcall.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.iniexe.rollcall.AppPalette
import com.iniexe.rollcall.ui.theme.LocalRollcallColors
import com.iniexe.rollcall.ui.theme.RollcallTheme
import com.iniexe.rollcall.ui.theme.RollcallMono

@Composable
fun LatestClassAttendanceCard(
    state: LatestClassAttendanceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRollcallColors.current
    val statusColor = when (state.status) {
        "present" -> colors.attendanceSafe
        "absent" -> colors.attendanceCritical
        "not_taken" -> colors.attendanceWarning
        else -> colors.contentSecondary
    }
    Surface(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics {
            contentDescription = state.accessibleDescription
            role = Role.Button
        },
        color = Color(colors.surfaceSubtle),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text("LATEST CLASS", style = TextStyle(fontFamily = RollcallMono,
                fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(colors.contentSecondary)))
            Row(Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(state.courseName, modifier = Modifier.weight(1f).padding(end = 8.dp), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = RollcallMono,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(colors.contentPrimary)))
                Surface(color = Color(statusColor).copy(alpha = 0x24 / 255f), shape = RoundedCornerShape(9.dp)) {
                    Text(state.statusLabel, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), maxLines = 1,
                        style = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold,
                            fontSize = 10.sp, color = Color(statusColor)))
                }
            }
        }
    }
}

@Preview(name = "Latest class · present", showBackground = true)
@Composable
private fun LatestClassPresentPreview() {
    RollcallTheme(AppPalette.get(false, 0).toComposeColors()) {
        LatestClassAttendanceCard(LatestClassAttendanceState("Programming in C", "Present", "present",
            "Latest class attendance. Programming in C: Present. Open attendance history"), {}, Modifier.padding(16.dp))
    }
}

@Preview(name = "Latest class · absent dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LatestClassAbsentDarkPreview() {
    RollcallTheme(AppPalette.get(true, 0).toComposeColors()) {
        LatestClassAttendanceCard(LatestClassAttendanceState("Programming in C", "Absent", "absent",
            "Latest class attendance. Programming in C: Absent. Open attendance history"), {}, Modifier.padding(16.dp))
    }
}
