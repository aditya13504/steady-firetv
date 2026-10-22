package com.steady.tv.pose

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A front-view stick figure described by 13 joints. The same keyframes animate the on-screen coach
 * and generate a synthetic "demo person" in MediaPipe's 33-landmark format, so the whole app can be
 * exercised without a camera (and the exercise engine can be tested on the JVM).
 *
 * Coordinates are image-space (0..1, y down) for a person facing the camera: the person's LEFT side
 * appears on the image's right, exactly as MediaPipe reports it.
 */
data class Figure(
    val nose: Pt,
    val lSh: Pt, val rSh: Pt,
    val lEl: Pt, val rEl: Pt,
    val lWr: Pt, val rWr: Pt,
    val lHip: Pt, val rHip: Pt,
    val lKn: Pt, val rKn: Pt,
    val lAn: Pt, val rAn: Pt
) {
    fun lerp(o: Figure, t: Float): Figure {
        fun l(a: Pt, b: Pt) = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        return Figure(l(nose, o.nose), l(lSh, o.lSh), l(rSh, o.rSh), l(lEl, o.lEl), l(rEl, o.rEl), l(lWr, o.lWr), l(rWr, o.rWr),
            l(lHip, o.lHip), l(rHip, o.rHip), l(lKn, o.lKn), l(rKn, o.rKn), l(lAn, o.lAn), l(rAn, o.rAn))
    }

    fun shift(dx: Float, dy: Float): Figure {
        fun s(p: Pt) = Pt(p.x + dx, p.y + dy, p.v)
        return Figure(s(nose), s(lSh), s(rSh), s(lEl), s(rEl), s(lWr), s(rWr), s(lHip), s(rHip), s(lKn), s(rKn), s(lAn), s(rAn))
    }

    /** Expand to the 33-point MediaPipe layout (face, hands and feet placed around their anchors). */
    fun toLandmarks(noise: Float = 0f, rnd: Random = Random.Default): List<Pt> {
        val out = MutableList(Lm.COUNT) { nose }
        fun j(p: Pt) = if (noise == 0f) p else Pt(p.x + (rnd.nextFloat() - 0.5f) * noise, p.y + (rnd.nextFloat() - 0.5f) * noise, 0.98f)
        for (i in 0..10) out[i] = j(Pt(nose.x + (i % 3 - 1) * 0.012f, nose.y - 0.01f + (i / 4) * 0.008f))
        out[Lm.NOSE] = j(nose)
        out[Lm.L_SHOULDER] = j(lSh); out[Lm.R_SHOULDER] = j(rSh)
        out[Lm.L_ELBOW] = j(lEl); out[Lm.R_ELBOW] = j(rEl)
        out[Lm.L_WRIST] = j(lWr); out[Lm.R_WRIST] = j(rWr)
        for (i in listOf(17, 19, 21)) out[i] = j(lWr)
        for (i in listOf(18, 20, 22)) out[i] = j(rWr)
        out[Lm.L_HIP] = j(lHip); out[Lm.R_HIP] = j(rHip)
        out[Lm.L_KNEE] = j(lKn); out[Lm.R_KNEE] = j(rKn)
        out[Lm.L_ANKLE] = j(lAn); out[Lm.R_ANKLE] = j(rAn)
        out[29] = j(Pt(lAn.x - 0.005f, lAn.y + 0.02f)); out[30] = j(Pt(rAn.x + 0.005f, rAn.y + 0.02f))
        out[31] = j(Pt(lAn.x + 0.01f, lAn.y + 0.03f)); out[32] = j(Pt(rAn.x - 0.01f, rAn.y + 0.03f))
        return out
    }

    companion object {
        val STAND = Figure(
            nose = Pt(0.5f, 0.14f),
            lSh = Pt(0.57f, 0.26f), rSh = Pt(0.43f, 0.26f),
            lEl = Pt(0.595f, 0.38f), rEl = Pt(0.405f, 0.38f),
            lWr = Pt(0.605f, 0.49f), rWr = Pt(0.395f, 0.49f),
            lHip = Pt(0.545f, 0.50f), rHip = Pt(0.455f, 0.50f),
            lKn = Pt(0.55f, 0.69f), rKn = Pt(0.45f, 0.69f),
            lAn = Pt(0.55f, 0.88f), rAn = Pt(0.45f, 0.88f)
        )

        /** Seated on a chair, facing the camera: thighs point at the lens, so hips and knees nearly meet. */
        val SEATED = Figure(
            nose = Pt(0.5f, 0.31f),
            lSh = Pt(0.57f, 0.43f), rSh = Pt(0.43f, 0.43f),
            lEl = Pt(0.6f, 0.55f), rEl = Pt(0.4f, 0.55f),
            lWr = Pt(0.585f, 0.65f), rWr = Pt(0.415f, 0.65f),
            lHip = Pt(0.545f, 0.665f), rHip = Pt(0.455f, 0.665f),
            lKn = Pt(0.565f, 0.705f), rKn = Pt(0.435f, 0.705f),
            lAn = Pt(0.56f, 0.88f), rAn = Pt(0.44f, 0.88f)
        )

        /** Half way up, leaning forward from the hips. */
        val RISING = Figure(
            nose = Pt(0.5f, 0.27f),
            lSh = Pt(0.57f, 0.37f), rSh = Pt(0.43f, 0.37f),
            lEl = Pt(0.6f, 0.49f), rEl = Pt(0.4f, 0.49f),
            lWr = Pt(0.595f, 0.6f), rWr = Pt(0.405f, 0.6f),
            lHip = Pt(0.545f, 0.585f), rHip = Pt(0.455f, 0.585f),
            lKn = Pt(0.56f, 0.70f), rKn = Pt(0.44f, 0.70f),
            lAn = Pt(0.555f, 0.88f), rAn = Pt(0.445f, 0.88f)
        )

        /** Arms crossed over the chest, as the CDC chair stand test asks. */
        fun armsCrossed(f: Figure) = f.copy(
            lEl = Pt(f.lSh.x + 0.03f, f.lSh.y + 0.09f), rEl = Pt(f.rSh.x - 0.03f, f.rSh.y + 0.09f),
            lWr = Pt(f.rSh.x + 0.02f, f.rSh.y + 0.05f), rWr = Pt(f.lSh.x - 0.02f, f.lSh.y + 0.05f)
        )

        val TOES = STAND.shift(0f, -0.04f).copy(lAn = Pt(0.55f, 0.85f), rAn = Pt(0.45f, 0.85f))
        val KNEE_UP_L = STAND.copy(lKn = Pt(0.56f, 0.56f), lAn = Pt(0.555f, 0.74f), rWr = Pt(0.39f, 0.45f))
        val KNEE_UP_R = STAND.copy(rKn = Pt(0.44f, 0.56f), rAn = Pt(0.445f, 0.74f), lWr = Pt(0.61f, 0.45f))
        val LEG_OUT_L = STAND.copy(lKn = Pt(0.61f, 0.68f), lAn = Pt(0.70f, 0.855f), lSh = Pt(0.565f, 0.262f), rSh = Pt(0.425f, 0.258f))
        val LEG_OUT_R = STAND.copy(rKn = Pt(0.39f, 0.68f), rAn = Pt(0.30f, 0.855f), lSh = Pt(0.575f, 0.258f), rSh = Pt(0.435f, 0.262f))
        val ONE_LEG_R = STAND.copy(rKn = Pt(0.445f, 0.70f), rAn = Pt(0.455f, 0.76f))
        val ONE_LEG_L = STAND.copy(lKn = Pt(0.555f, 0.70f), lAn = Pt(0.545f, 0.76f))

        /** Lying on the floor: what the fall check looks for. */
        val ON_FLOOR = Figure(
            nose = Pt(0.18f, 0.80f),
            lSh = Pt(0.30f, 0.76f), rSh = Pt(0.30f, 0.86f),
            lEl = Pt(0.40f, 0.74f), rEl = Pt(0.40f, 0.9f),
            lWr = Pt(0.48f, 0.75f), rWr = Pt(0.48f, 0.9f),
            lHip = Pt(0.55f, 0.79f), rHip = Pt(0.55f, 0.86f),
            lKn = Pt(0.70f, 0.78f), rKn = Pt(0.70f, 0.86f),
            lAn = Pt(0.85f, 0.79f), rAn = Pt(0.85f, 0.87f)
        )
    }
}

/** A looping keyframe animation: (pose, ms to move into it, ms to hold it). */
class Motion(private val keys: List<Triple<Figure, Int, Int>>) {
    val cycleMs: Int = keys.sumOf { it.second + it.third }

    fun at(tMs: Long): Figure {
        var t = (tMs % cycleMs).toInt()
        for (i in keys.indices) {
            val (pose, move, hold) = keys[i]
            val prev = keys[(i - 1 + keys.size) % keys.size].first
            if (t < move) return prev.lerp(pose, ease(t / move.toFloat()))
            t -= move
            if (t < hold) return pose
            t -= hold
        }
        return keys.last().first
    }

    private fun ease(x: Float) = (0.5f - 0.5f * cos(PI * x)).toFloat()
}

object Motions {
    fun of(id: ExerciseId): Motion = when (id) {
        ExerciseId.SIT_TO_STAND -> Motion(
            listOf(
                Triple(Figure.SEATED, 700, 700), Triple(Figure.RISING, 650, 0), Triple(Figure.STAND, 600, 700), Triple(Figure.RISING, 700, 0)
            )
        )
        ExerciseId.CHAIR_STAND_TEST -> Motion(
            listOf(
                Triple(Figure.armsCrossed(Figure.SEATED), 600, 300), Triple(Figure.armsCrossed(Figure.RISING), 500, 0),
                Triple(Figure.armsCrossed(Figure.STAND), 500, 300), Triple(Figure.armsCrossed(Figure.RISING), 500, 0)
            )
        )
        ExerciseId.HEEL_RAISE -> Motion(listOf(Triple(Figure.STAND, 700, 600), Triple(Figure.TOES, 800, 700)))
        ExerciseId.MARCH -> Motion(
            listOf(Triple(Figure.STAND, 450, 100), Triple(Figure.KNEE_UP_L, 500, 200), Triple(Figure.STAND, 450, 100), Triple(Figure.KNEE_UP_R, 500, 200))
        )
        ExerciseId.SIDE_LEG_RAISE -> Motion(
            listOf(Triple(Figure.STAND, 700, 400), Triple(Figure.LEG_OUT_L, 900, 600), Triple(Figure.STAND, 900, 400), Triple(Figure.LEG_OUT_R, 900, 600))
        )
        ExerciseId.ONE_LEG_STAND -> Motion(
            listOf(Triple(Figure.STAND, 900, 1200), Triple(Figure.ONE_LEG_R, 700, 11000), Triple(Figure.STAND, 700, 1500), Triple(Figure.ONE_LEG_L, 700, 11000))
        )
    }
}

/**
 * Synthetic person for demo mode and tests: follows the coach motion with natural jitter, a slight
 * sway, and the occasional imperfect rep so form cues show up too.
 */
class DemoPerson(private val id: ExerciseId, private val seed: Int = 7, private val sloppyEvery: Int = 3) {
    private val motion = Motions.of(id)
    private val rnd = Random(seed)
    var falling = false
    private var fallStart = 0L

    /** Back on their feet after a (simulated) fall. */
    fun recover() {
        falling = false
        fallStart = 0L
    }

    fun frame(tMs: Long): PoseFrame {
        val cycle = (tMs / motion.cycleMs).toInt()
        var fig = motion.at(tMs)
        // Every few reps, do a half-hearted one: the engine should notice and coach.
        if (sloppyEvery > 0 && cycle % sloppyEvery == sloppyEvery - 1) {
            fig = when (id) {
                ExerciseId.SIT_TO_STAND -> {
                    // only cut the top of the movement short; the seated part stays the same
                    val up = ((Figure.SEATED.lHip.y - fig.lHip.y) / (Figure.SEATED.lHip.y - Figure.STAND.lHip.y)).coerceIn(0f, 1f)
                    fig.lerp(Figure.RISING, 0.45f * up)
                }
                ExerciseId.MARCH -> fig.lerp(Figure.STAND, 0.5f)
                else -> fig
            }
        }
        val sway = (sin(tMs / 700.0) * 0.004).toFloat()
        fig = fig.shift(sway, 0f)
        if (falling) {
            if (fallStart == 0L) fallStart = tMs
            val k = ((tMs - fallStart) / 900f).coerceIn(0f, 1f)
            fig = fig.lerp(Figure.ON_FLOOR, k)
        }
        return PoseFrame(fig.toLandmarks(noise = 0.006f, rnd = rnd), tMs)
    }
}
