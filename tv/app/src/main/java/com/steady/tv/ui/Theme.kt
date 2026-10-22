package com.steady.tv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.steady.tv.R

/** Night-sky navy with a marigold "sun" for focus and action; sage means done, coral means attention. */
object C {
    val night = Color(0xFF14213D)
    val deep = Color(0xFF0E1830)
    val surface = Color(0xFF1D2F57)
    val surfaceHi = Color(0xFF2B3F6B)
    val cream = Color(0xFFF7F3EA)
    val muted = Color(0xFFA8B3C9)
    val sun = Color(0xFFF2C14E)
    val sunInk = Color(0xFF2B1E00)
    val sage = Color(0xFF7FC8A9)
    val coral = Color(0xFFF28B82)
    val coralInk = Color(0xFF2A0B08)
}

/** Atkinson Hyperlegible: designed by the Braille Institute for readers with low vision. */
val Atkinson = FontFamily(Font(R.font.atkinson_regular, FontWeight.Normal), Font(R.font.atkinson_bold, FontWeight.Bold))

/** 10-foot type scale: nothing a viewer needs to read is under 20sp. */
object T {
    val giant = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 120.sp, lineHeight = 120.sp, color = C.cream)
    val display = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, color = C.cream)
    val title = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, color = C.cream)
    val heading = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = C.cream)
    val body = TextStyle(fontFamily = Atkinson, fontSize = 22.sp, lineHeight = 30.sp, color = C.cream)
    val small = TextStyle(fontFamily = Atkinson, fontSize = 18.sp, lineHeight = 24.sp, color = C.muted)
    val label = TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, color = C.cream)
}
