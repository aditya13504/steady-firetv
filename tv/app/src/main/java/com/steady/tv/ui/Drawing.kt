package com.steady.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.steady.tv.pose.ExerciseId
import com.steady.tv.pose.Figure
import com.steady.tv.pose.Lm
import com.steady.tv.pose.Motions
import com.steady.tv.pose.PoseFrame
import com.steady.tv.pose.Pt
import kotlin.math.min

/** Maps a figure space of width `aspect` × height 1 into the canvas, centered, keeping proportions. */
private class Fit(size: Size, aspect: Float, mirror: Boolean, pad: Float = 0.04f) {
    private val scale = min(size.width / aspect, size.height) * (1 - 2 * pad)
    private val ox = (size.width - aspect * scale) / 2f
    private val oy = (size.height - scale) / 2f
    private val mirror = mirror
    private val aspect = aspect
    fun o(p: Pt) = Offset(ox + (if (mirror) aspect - p.x else p.x) * scale, oy + p.y * scale)
    val unit get() = scale
}

private fun DrawScope.bones(pts: (Int) -> Pt, fit: Fit, color: Color, joint: Color, width: Float) {
    for ((a, b) in Lm.BONES) drawLine(color, fit.o(pts(a)), fit.o(pts(b)), strokeWidth = width, cap = StrokeCap.Round)
    val sh = pts(Lm.L_SHOULDER)
    val sh2 = pts(Lm.R_SHOULDER)
    val nose = pts(Lm.NOSE)
    val neck = Pt((sh.x + sh2.x) / 2, (sh.y + sh2.y) / 2)
    drawLine(color, fit.o(neck), fit.o(Pt(nose.x, nose.y + 0.04f)), strokeWidth = width, cap = StrokeCap.Round)
    drawCircle(color, radius = fit.unit * 0.045f, center = fit.o(Pt(nose.x, nose.y - 0.01f)))
    for (i in listOf(Lm.L_KNEE, Lm.R_KNEE, Lm.L_HIP, Lm.R_HIP)) drawCircle(joint, radius = width * 0.62f, center = fit.o(pts(i)))
}

private fun figurePt(f: Figure): (Int) -> Pt = { i ->
    when (i) {
        Lm.NOSE -> f.nose; Lm.L_SHOULDER -> f.lSh; Lm.R_SHOULDER -> f.rSh; Lm.L_ELBOW -> f.lEl; Lm.R_ELBOW -> f.rEl
        Lm.L_WRIST -> f.lWr; Lm.R_WRIST -> f.rWr; Lm.L_HIP -> f.lHip; Lm.R_HIP -> f.rHip; Lm.L_KNEE -> f.lKn
        Lm.R_KNEE -> f.rKn; Lm.L_ANKLE -> f.lAn; else -> f.rAn
    }
}

/** The coach: an animated figure demonstrating the move, with a chair where the move uses one. */
@Composable
fun CoachFigure(id: ExerciseId, modifier: Modifier = Modifier, playing: Boolean = true) {
    val motion = remember(id) { Motions.of(id) }
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(id, playing) {
        val start = withFrameMillis { it } - t
        while (playing) withFrameMillis { t = it - start }
    }
    val info = remember(id) { com.steady.tv.pose.Catalog.info(id) }
    val chair = info.sit
    val holdChair = !chair && info.usesChair
    Canvas(modifier) {
        val fit = Fit(size, 1f, mirror = true)
        val fig = motion.at(t)
        drawLine(C.surfaceHi, fit.o(Pt(0.18f, 0.905f)), fit.o(Pt(0.82f, 0.905f)), strokeWidth = fit.unit * 0.008f, cap = StrokeCap.Round)
        if (chair) {
            // a chair seen from the front, behind the figure
            val w = fit.unit * 0.008f
            drawRoundRect(C.surfaceHi, fit.o(Pt(0.4f, 0.44f)), Size(fit.unit * 0.2f, fit.unit * 0.24f), CornerRadius(12f), style = Stroke(w * 1.6f))
            drawLine(C.surfaceHi, fit.o(Pt(0.38f, 0.68f)), fit.o(Pt(0.62f, 0.68f)), strokeWidth = w * 2.2f, cap = StrokeCap.Round)
            for (x in listOf(0.39f, 0.61f)) drawLine(C.surfaceHi, fit.o(Pt(x, 0.68f)), fit.o(Pt(x, 0.9f)), strokeWidth = w * 1.6f, cap = StrokeCap.Round)
        }
        if (holdChair) {
            // the back of a chair to hold, beside the figure (on the person's left = screen right when mirrored)
            val w = fit.unit * 0.008f
            val x0 = 0.24f
            drawLine(C.surfaceHi, fit.o(Pt(x0, 0.5f)), fit.o(Pt(x0 + 0.1f, 0.5f)), strokeWidth = w * 2.2f, cap = StrokeCap.Round)
            for (x in listOf(x0, x0 + 0.1f)) drawLine(C.surfaceHi, fit.o(Pt(x, 0.5f)), fit.o(Pt(x, 0.9f)), strokeWidth = w * 1.6f, cap = StrokeCap.Round)
        }
        bones(figurePt(fig), fit, C.cream, C.sun, fit.unit * 0.026f)
    }
}

/** The person, as the phone sees them, mirrored so moving your right arm moves the arm on the right. */
@Composable
fun MirrorSkeleton(frame: PoseFrame?, modifier: Modifier = Modifier, dim: Boolean = false) {
    Canvas(modifier) {
        val f = frame ?: return@Canvas
        val fit = Fit(size, f.aspect, mirror = true, pad = 0.02f)
        val color = if (dim) C.muted else C.blue
        bones({ f[it] }, fit, color, C.white, fit.unit * 0.022f)
    }
}

/** The phone's live picture, mirrored like the skeleton, with the tracked body lines and joints drawn over it. */
@Composable
fun CameraView(image: ImageBitmap, frame: PoseFrame?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val aspect = image.width.toFloat() / image.height
        val fit = Fit(size, aspect, mirror = true, pad = 0f)
        val w = (aspect * fit.unit).toInt()
        val h = fit.unit.toInt()
        withTransform({ scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) }) {
            drawImage(image, dstOffset = IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()), dstSize = IntSize(w, h))
        }
        val f = frame ?: return@Canvas
        val pts: (Int) -> Pt = { f[it] }
        for ((a, b) in Lm.BONES) {
            drawLine(Color.Black.copy(alpha = 0.35f), fit.o(pts(a)), fit.o(pts(b)), strokeWidth = fit.unit * 0.022f, cap = StrokeCap.Round)
            drawLine(C.orange, fit.o(pts(a)), fit.o(pts(b)), strokeWidth = fit.unit * 0.012f, cap = StrokeCap.Round)
        }
        for (i in Lm.CORE + Lm.LEGS + listOf(Lm.L_ELBOW, Lm.R_ELBOW, Lm.L_WRIST, Lm.R_WRIST, Lm.NOSE)) {
            drawCircle(C.white, radius = fit.unit * 0.014f, center = fit.o(pts(i)))
            drawCircle(C.blue, radius = fit.unit * 0.014f, center = fit.o(pts(i)), style = Stroke(fit.unit * 0.006f))
        }
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, color: Color = C.sun, track: Color = C.surface, width: Float = 22f) {
    Canvas(modifier) {
        val inset = width / 2
        val s = Size(size.width - width, size.height - width)
        drawArc(track, -90f, 360f, false, Offset(inset, inset), s, style = Stroke(width))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), s, style = Stroke(width, cap = StrokeCap.Round))
    }
}

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    }
    Canvas(modifier) {
        val n = matrix.width
        val cell = min(size.width, size.height) / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix[x, y]) drawRect(C.night, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** A tiny trend line for the home screen. */
@Composable
fun Sparkline(values: List<Int>, modifier: Modifier = Modifier, color: Color = C.sage) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (values.size < 2) return@Canvas
            val lo = values.min().toFloat()
            val hi = values.max().toFloat().coerceAtLeast(lo + 1)
            val step = size.width / (values.size - 1)
            val path = Path()
            values.forEachIndexed { i, v ->
                val p = Offset(i * step, size.height - (v - lo) / (hi - lo) * size.height * 0.8f - size.height * 0.1f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                if (i == values.lastIndex) drawCircle(color, 7f, p)
            }
            drawPath(path, color, style = Stroke(5f, cap = StrokeCap.Round))
        }
    }
}
