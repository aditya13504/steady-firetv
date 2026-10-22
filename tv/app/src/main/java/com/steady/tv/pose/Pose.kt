package com.steady.tv.pose

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** One body point in image space: x and y in 0..1 (y grows downward), v = visibility 0..1. */
data class Pt(val x: Float, val y: Float, val v: Float = 1f)

/** MediaPipe Pose Landmarker indices (33-point model). */
object Lm {
    const val NOSE = 0
    const val L_SHOULDER = 11
    const val R_SHOULDER = 12
    const val L_ELBOW = 13
    const val R_ELBOW = 14
    const val L_WRIST = 15
    const val R_WRIST = 16
    const val L_HIP = 23
    const val R_HIP = 24
    const val L_KNEE = 25
    const val R_KNEE = 26
    const val L_ANKLE = 27
    const val R_ANKLE = 28
    const val COUNT = 33

    /** Bones drawn for the mirror skeleton. */
    val BONES = listOf(
        L_SHOULDER to R_SHOULDER, L_SHOULDER to L_ELBOW, L_ELBOW to L_WRIST, R_SHOULDER to R_ELBOW, R_ELBOW to R_WRIST,
        L_SHOULDER to L_HIP, R_SHOULDER to R_HIP, L_HIP to R_HIP,
        L_HIP to L_KNEE, L_KNEE to L_ANKLE, R_HIP to R_KNEE, R_KNEE to R_ANKLE
    )
    val CORE = listOf(L_SHOULDER, R_SHOULDER, L_HIP, R_HIP)
    val LEGS = listOf(L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE)
}

/** A full-body pose at a moment in time. */
class PoseFrame(val pts: List<Pt>, val tMs: Long) {
    init {
        require(pts.size >= Lm.COUNT) { "expected ${Lm.COUNT} landmarks, got ${pts.size}" }
    }

    operator fun get(i: Int): Pt = pts[i]

    fun visible(indices: List<Int>, min: Float = 0.5f) = indices.all { pts[it].v >= min }

    fun mid(a: Int, b: Int) = Pt((pts[a].x + pts[b].x) / 2f, (pts[a].y + pts[b].y) / 2f, minOf(pts[a].v, pts[b].v))
    val shoulderMid get() = mid(Lm.L_SHOULDER, Lm.R_SHOULDER)
    val hipMid get() = mid(Lm.L_HIP, Lm.R_HIP)

    /** Shoulder-to-hip distance: the body's own ruler, so thresholds work at any distance from the camera. */
    val torso: Float get() = dist(shoulderMid, hipMid).coerceAtLeast(1e-3f)
    val shoulderWidth: Float get() = dist(pts[Lm.L_SHOULDER], pts[Lm.R_SHOULDER]).coerceAtLeast(1e-3f)

    /** Torso tilt away from vertical, in degrees (0 = upright, 90 = lying down). */
    val torsoTiltDeg: Float
        get() {
            val dx = shoulderMid.x - hipMid.x
            val dy = hipMid.y - shoulderMid.y
            return Math.toDegrees(atan2(abs(dx).toDouble(), dy.toDouble())).toFloat()
        }

    companion object {
        fun of(pts: List<Pt>, tMs: Long) = PoseFrame(pts, tMs)
    }
}

fun dist(a: Pt, b: Pt): Float = hypot(a.x - b.x, a.y - b.y)

/** Angle of the leg (hip → ankle) away from straight down, in degrees. */
fun legAbductionDeg(hip: Pt, ankle: Pt): Float =
    Math.toDegrees(atan2(abs(ankle.x - hip.x).toDouble(), (ankle.y - hip.y).toDouble().coerceAtLeast(1e-3))).toFloat()

/** Exponential smoothing of landmark jitter, per point. */
class Smoother(private val alpha: Float = 0.55f) {
    private var last: List<Pt>? = null
    fun apply(frame: PoseFrame): PoseFrame {
        val prev = last
        val out = if (prev == null) frame.pts else frame.pts.mapIndexed { i, p ->
            val q = prev[i]
            Pt(q.x + alpha * (p.x - q.x), q.y + alpha * (p.y - q.y), p.v)
        }
        last = out
        return PoseFrame(out, frame.tMs)
    }

    fun reset() {
        last = null
    }
}

/** Rolling standard deviation over a time window, used for balance steadiness. */
class RollingStd(private val windowMs: Long) {
    private val samples = ArrayDeque<Pair<Long, Float>>()
    fun add(t: Long, v: Float) {
        samples.addLast(t to v)
        while (samples.isNotEmpty() && t - samples.first().first > windowMs) samples.removeFirst()
    }

    fun std(): Float {
        if (samples.size < 3) return 0f
        val mean = samples.map { it.second }.average()
        return sqrt(samples.sumOf { (it.second - mean) * (it.second - mean) } / samples.size).toFloat()
    }

    fun clear() = samples.clear()
}
