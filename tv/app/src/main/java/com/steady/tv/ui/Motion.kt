package com.steady.tv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Steady mark: a figure balancing on one leg above a level line. `progress` 0..1 draws it
 * stroke by stroke, which is the launch animation.
 */
@Composable
fun LogoMark(modifier: Modifier = Modifier, progress: Float = 1f) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        fun o(x: Float, y: Float) = Offset(size.width / 2 + (x - 50) * u, size.height / 2 + (y - 50) * u)
        val strokes = listOf(
            listOf(o(14f, 88f), o(86f, 88f)), // ground
            listOf(o(50f, 34f), o(50f, 60f)), // body
            listOf(o(50f, 60f), o(50f, 86f)), // standing leg
            listOf(o(50f, 60f), o(66f, 68f), o(62f, 77f)), // lifted leg
            listOf(o(50f, 40f), o(28f, 35f)), // arm
            listOf(o(50f, 40f), o(72f, 35f)) // arm
        )
        val per = 1f / (strokes.size + 1)
        strokes.forEachIndexed { i, pts ->
            val local = ((progress - i * per) / per).coerceIn(0f, 1f)
            if (local <= 0f) return@forEachIndexed
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (p in pts.drop(1)) lineTo(p.x, p.y)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val part = Path()
            measure.getSegment(0f, measure.length * local, part, true)
            drawPath(part, if (i == 0) C.orange else C.white, style = Stroke(width = if (i == 0) 7 * u else 9 * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val head = ((progress - strokes.size * per) / per).coerceIn(0f, 1f)
        if (head > 0f) drawCircle(C.orange, radius = 9 * u * head, center = o(50f, 20f))
    }
}

@Composable
fun PulseDot(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(11.dp).graphicsLayer { alpha = a; scaleX = 0.8f + a * 0.3f; scaleY = 0.8f + a * 0.3f }.clip(CircleShape).background(color))
}

/** Slowly drifting orange and blue light behind the hero, like ambient light on a TV wall. */
@Composable
fun AmbientLight(modifier: Modifier = Modifier, warm: Color = C.orange, cool: Color = C.blue, strength: Float = 0.22f) {
    val t = rememberInfiniteTransition(label = "ambient")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "p")
    Canvas(modifier) {
        val a = p * 2 * PI.toFloat()
        val w = size.width
        val h = size.height
        val c1 = Offset(w * (0.72f + 0.08f * cos(a)), h * (0.35f + 0.10f * sin(a)))
        val c2 = Offset(w * (0.88f + 0.06f * sin(a * 1.3f)), h * (0.75f + 0.08f * cos(a)))
        drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = strength), Color.Transparent), c1, w * 0.38f), radius = w * 0.38f, center = c1)
        drawCircle(Brush.radialGradient(listOf(cool.copy(alpha = strength * 0.9f), Color.Transparent), c2, w * 0.32f), radius = w * 0.32f, center = c2)
    }
}

/** A burst of sparks from the centre each time `tick` changes: every rep gets a little celebration. */
@Composable
fun Burst(tick: Int, modifier: Modifier = Modifier, color: Color = C.orange) {
    val anim = remember { Animatable(1f) }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        anim.snapTo(0f)
        anim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val k = anim.value
        if (k >= 1f) return@Canvas
        val r0 = size.minDimension * 0.42f
        val r1 = size.minDimension * (0.42f + 0.22f * k)
        for (i in 0 until 12) {
            val ang = (i / 12f) * 2 * PI.toFloat()
            val from = Offset(center.x + cos(ang) * r0 * (1 + 0.15f * k), center.y + sin(ang) * r0 * (1 + 0.15f * k))
            val to = Offset(center.x + cos(ang) * r1, center.y + sin(ang) * r1)
            drawLine(if (i % 2 == 0) color else C.white, from, to, strokeWidth = 6f * (1 - k), cap = StrokeCap.Round, alpha = 1 - k)
        }
    }
}

/** Confetti for finishing an exercise or a session. */
@Composable
fun Confetti(tick: Int, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(1f) }
    val pieces = remember(tick) {
        val r = Random(tick)
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.4f + 0.6f, r.nextInt(3)) }
    }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        anim.snapTo(0f)
        anim.animateTo(1f, tween(1_800, easing = LinearEasing))
    }
    Canvas(modifier) {
        val k = anim.value
        if (k >= 1f) return@Canvas
        val colors = listOf(C.orange, C.blue, C.white)
        pieces.forEachIndexed { i, (x, speed, c) ->
            val y = -0.05f + k * speed * 1.2f
            val sway = sin((k * 6 + i) * 1.3f) * 0.02f
            val p = Offset((x + sway) * size.width, y * size.height)
            val w = 10f + (i % 4) * 3f
            rotateRect(p, w, w * 0.5f, k * 720f + i * 23f, colors[c], 1f - k * 0.6f)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateRect(center: Offset, w: Float, h: Float, deg: Float, color: Color, alpha: Float) {
    val rad = deg * PI.toFloat() / 180f
    val dx = cos(rad) * w / 2
    val dy = sin(rad) * w / 2
    drawLine(color, Offset(center.x - dx, center.y - dy), Offset(center.x + dx, center.y + dy), strokeWidth = h, alpha = alpha)
}

/** Expanding rings: the alert's heartbeat. */
@Composable
fun PulseRings(modifier: Modifier = Modifier, color: Color = C.white) {
    val t = rememberInfiniteTransition(label = "rings")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2_200, easing = LinearEasing)), label = "p")
    Canvas(modifier) {
        for (i in 0 until 3) {
            val k = (p + i / 3f) % 1f
            drawCircle(color, radius = size.minDimension * (0.15f + 0.55f * k), center = center, alpha = (1 - k) * 0.35f, style = Stroke(width = 6f))
        }
    }
}

/** A check mark that draws itself. */
@Composable
fun CheckDraw(modifier: Modifier = Modifier, color: Color = C.green, key: Any = Unit) {
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(1f, tween(600, delayMillis = 150, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        drawCircle(color, radius = size.minDimension / 2 * (0.6f + 0.4f * minOf(1f, anim.value * 2)))
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.68f)
            lineTo(size.width * 0.74f, size.height * 0.36f)
        }
        val m = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        m.getSegment(0f, m.length * anim.value, part, true)
        drawPath(part, C.bg, style = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A glowing line sweeping over the QR code while we wait for the phone. */
@Composable
fun ScanLine(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "scan")
    val p by t.animateFloat(0.04f, 0.96f, infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Canvas(modifier) {
        val y = size.height * p
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, C.orange.copy(alpha = 0.28f), Color.Transparent), y - 40f, y + 40f), Offset(0f, y - 40f), androidx.compose.ui.geometry.Size(size.width, 80f))
        drawLine(C.orange, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f)
    }
}

/** Fade and rise into place, `index` steps after the screen appears: staggered reveals. */
fun Modifier.appear(index: Int, key: Any = Unit): Modifier = composed {
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(1f, tween(520, delayMillis = 70 * index, easing = M.emphasized)) }
    graphicsLayer {
        alpha = anim.value
        translationY = (1 - anim.value) * 36f
    }
}

/** Counts a number up from zero; used for headline results. */
@Composable
fun countUp(target: Int, key: Any = target, durationMs: Int = 900): Int {
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(target.toFloat(), tween(durationMs, easing = M.decel)) }
    return anim.value.toInt()
}
