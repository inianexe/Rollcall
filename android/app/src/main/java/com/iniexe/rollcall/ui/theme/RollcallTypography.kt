package com.iniexe.rollcall.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.iniexe.rollcall.R

val RollcallMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val RollcallTypography = Typography(
    headlineLarge = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    titleLarge = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontFamily = RollcallMono, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = RollcallMono, fontSize = 12.sp),
    labelMedium = TextStyle(fontFamily = RollcallMono, fontWeight = FontWeight.Bold, fontSize = 11.sp),
)
