package com.steady.tv.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.steady.tv.R

/**
 * Amazon / Fire TV palette: squid-ink surfaces, Amazon orange for the primary action, Prime blue
 * for focus and live data, white type. Feels native next to the Fire TV home screen.
 */
object C {
    val bg = Color(0xFF0B1118)
    val ink = Color(0xFF131A22)
    val squid = Color(0xFF232F3E)
    val squidHi = Color(0xFF37475A)
    val line = Color(0xFF2E3B4C)
    val white = Color(0xFFFFFFFF)
    val text = Color(0xFFF2F4F7)
    val muted = Color(0xFFA7B4C2)
    val orange = Color(0xFFFF9900)
    val orangeDeep = Color(0xFFE47911)
    val onOrange = Color(0xFF0F1111)
    val blue = Color(0xFF00A8E1)
    val blueDeep = Color(0xFF146EB4)
    val green = Color(0xFF3FBF8F)
    val red = Color(0xFFCC0C39)
    val redDeep = Color(0xFF8C0A28)

    // compatibility names used by drawing code
    val night get() = bg
    val deep get() = ink
    val surface get() = squid
    val surfaceHi get() = squidHi
    val cream get() = text
    val sun get() = orange
    val sunInk get() = onOrange
    val sage get() = green
    val coral get() = red
    val coralInk get() = white

    val heroGradient = Brush.horizontalGradient(listOf(Color(0xFF0B1118), Color(0xF20B1118), Color(0x000B1118)))
    val scrimBottom = Brush.verticalGradient(listOf(Color(0x000B1118), Color(0xFF0B1118)))
}

/** Inter: an open, highly legible sans with the clean feel of the Fire TV interface. */
val Ui = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)

/** 10-foot type scale: nothing a viewer must read is under 18sp. */
object T {
    val giant = TextStyle(fontFamily = Ui, fontWeight = FontWeight.ExtraBold, fontSize = 112.sp, lineHeight = 112.sp, color = C.white, letterSpacing = (-3).sp)
    val hero = TextStyle(fontFamily = Ui, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 58.sp, color = C.white, letterSpacing = (-1.2).sp)
    val display = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, color = C.white, letterSpacing = (-0.8).sp)
    val title = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, color = C.white, letterSpacing = (-0.4).sp)
    val heading = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp, color = C.white)
    val body = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Normal, fontSize = 21.sp, lineHeight = 29.sp, color = C.text)
    val small = TextStyle(fontFamily = Ui, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, color = C.muted)
    val label = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = C.white)
    val nav = TextStyle(fontFamily = Ui, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, color = C.muted)
}

/** Motion tokens: one easing family, springs for anything that answers a key press. */
object M {
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val decel = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    fun <T> focusSpring() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    fun <T> popSpring() = spring<T>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
}
