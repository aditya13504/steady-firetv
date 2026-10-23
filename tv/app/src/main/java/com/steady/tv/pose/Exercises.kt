package com.steady.tv.pose

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ExerciseId {
    MARCH, SIT_TO_STAND, HEEL_RAISE, SIDE_LEG_RAISE, ONE_LEG_STAND, CHAIR_STAND_TEST,
    // leg strength
    MINI_SQUAT, HANDS_ASSISTED_STAND, ARMS_CROSSED_STAND, SEATED_KNEE_EXTENSION, STANDING_KNEE_CURL, SEATED_MARCH, SEATED_KNEES_OUT, SIDE_STEP,
    // arms and shoulders
    FRONT_ARM_RAISE, SIDE_ARM_RAISE, ARM_CURL, OVERHEAD_REACH, SHOULDER_SHRUG,
    // balance
    FEET_TOGETHER, TANDEM_STANCE, WEIGHT_SHIFT, KNEE_LIFT_HOLD,
    // mobility and moving
    HEAD_TURN, SIDE_BEND, TRUNK_TWIST, HIP_HINGE, SEATED_JACKS
}

/** How the library groups exercises, in the order the rows appear. */
enum class Category(val title: String) {
    STRENGTH("Leg strength"), BALANCE("Balance"), ARMS("Arms and shoulders"), MOBILITY("Mobility and stretching"), MOVE("Get moving"), CHECK("Health check")
}

enum class Kind { REPS, HOLD, TIMED_TEST }

/** What the person sees and hears about an exercise. Plain words, one instruction at a time. */
data class ExerciseInfo(
    val id: ExerciseId,
    val title: String,
    val how: String,
    val kind: Kind,
    val defaultTarget: Int,
    val unit: String,
    val usesChair: Boolean,
    val benefit: String,
    val category: Category = Category.STRENGTH,
    /** Done sitting on the chair (the coach is drawn seated in front of it). */
    val sit: Boolean = false,
    /** For holds: seconds each hold should last. */
    val holdSeconds: Int = 10
)

object Catalog {
    val all = listOf(
        ExerciseInfo(ExerciseId.MARCH, "Standing march", "Hold the back of a chair. Lift one knee, then the other.", Kind.REPS, 20, "steps", true, "Warms up hips and gets you walking taller.", Category.MOVE),
        ExerciseInfo(ExerciseId.SIT_TO_STAND, "Sit to stand", "Sit near the front of the chair. Lean forward and stand all the way up, then sit back down slowly.", Kind.REPS, 10, "stands", true, "Builds the leg strength you use every time you get up.", Category.STRENGTH, sit = true),
        ExerciseInfo(ExerciseId.HEEL_RAISE, "Heel raises", "Hold the chair. Rise up onto your toes, then lower slowly.", Kind.REPS, 10, "raises", true, "Strong calves help you catch a stumble.", Category.STRENGTH),
        ExerciseInfo(ExerciseId.SIDE_LEG_RAISE, "Side leg raise", "Hold the chair. Lift one leg out to the side, keep your body tall, and lower it.", Kind.REPS, 10, "lifts", true, "Hip strength keeps you steady side to side.", Category.STRENGTH),
        ExerciseInfo(ExerciseId.ONE_LEG_STAND, "One-leg balance", "Hold the chair lightly. Lift one foot and hold for 10 seconds. Then switch.", Kind.HOLD, 2, "holds", true, "Balance is the skill that prevents falls.", Category.BALANCE),
        ExerciseInfo(ExerciseId.CHAIR_STAND_TEST, "30-second chair stand", "Cross your arms over your chest. Stand up and sit down as many times as you can in 30 seconds.", Kind.TIMED_TEST, 30, "seconds", true, "The CDC's check of leg strength and fall risk.", Category.CHECK, sit = true)
    ) + MoreExercises.all

    fun info(id: ExerciseId) = all.first { it.id == id }
}

/** A coaching cue with an id, so repeats can be throttled and counted for the summary. */
data class Cue(val id: String, val text: String)

data class TrackerState(
    val reps: Int = 0,
    val target: Int = 0,
    /** 0..1 position within the current movement or hold, for the progress ring. */
    val progress: Float = 0f,
    val phase: String = "Get ready",
    val holdMs: Long = 0,
    val bestHoldMs: Long = 0,
    val steadiness: Int? = null,
    val remainingMs: Long? = null,
    val done: Boolean = false
)

abstract class Tracker(val info: ExerciseInfo, var target: Int) {
    var state = TrackerState(target = target)
        protected set

    /** Cues raised during the last update, for the screen and the voice. */
    val newCues = mutableListOf<Cue>()
    val cueCounts = mutableMapOf<String, Int>()
    private var lastCueAt = -10_000L
    var repEvent = false
        protected set

    fun feed(f: PoseFrame) {
        newCues.clear()
        repEvent = false
        update(f)
    }

    protected abstract fun update(f: PoseFrame)

    protected fun cue(f: PoseFrame, id: String, text: String) {
        cueCounts[id] = (cueCounts[id] ?: 0) + 1
        if (f.tMs - lastCueAt < 4_000) return
        lastCueAt = f.tMs
        newCues.add(Cue(id, text))
    }

    protected fun rep() {
        repEvent = true
    }

    fun easier() {
        target = max(2, (target * 0.7f).roundToInt())
        state = state.copy(target = target, done = state.reps >= target)
    }

    fun harder() {
        target = (target * 1.3f).roundToInt() + 1
        state = state.copy(target = target, done = false)
    }

    /** Average steadiness or form quality 0..100 for the summary. */
    open fun quality(): Int? = null

    companion object {
        fun create(id: ExerciseId, target: Int = Catalog.info(id).defaultTarget): Tracker = when (id) {
            ExerciseId.SIT_TO_STAND -> SitToStand(Catalog.info(id), target, timeLimitMs = null)
            ExerciseId.CHAIR_STAND_TEST -> SitToStand(Catalog.info(id), 99, timeLimitMs = 30_000)
            ExerciseId.HEEL_RAISE -> HeelRaise(target)
            ExerciseId.MARCH -> March(target)
            ExerciseId.SIDE_LEG_RAISE -> SideLegRaise(target)
            ExerciseId.ONE_LEG_STAND -> OneLegStand(target)
            ExerciseId.HANDS_ASSISTED_STAND, ExerciseId.ARMS_CROSSED_STAND -> SitToStand(Catalog.info(id), target, timeLimitMs = null)
            else -> MoreExercises.tracker(id, target)
        }
    }
}

/**
 * Sit to stand, and the CDC 30-second chair stand test.
 * Signal: knee-below-hip distance divided by torso length. Seated and facing the camera the thighs
 * point at the lens so hips and knees nearly meet (~0.2); standing, the thigh is upright (~0.8).
 */
class SitToStand(info: ExerciseInfo, target: Int, private val timeLimitMs: Long?) : Tracker(info, target) {
    private var seated = false
    private var standing = false
    private var peak = 0f
    private var seatedAt = 0L
    private var testStart: Long? = null
    private var reps = 0
    private var cleanReps = 0

    override fun update(f: PoseFrame) {
        if (state.done) return
        if (!f.visible(Lm.CORE + Lm.LEGS)) {
            state = state.copy(phase = "Step back so I can see your legs")
            return
        }
        val kneeY = (f[Lm.L_KNEE].y + f[Lm.R_KNEE].y) / 2f
        val r = (kneeY - f.hipMid.y) / f.torso
        val progress = ((r - 0.3f) / 0.45f).coerceIn(0f, 1f)
        val lean = abs(f.shoulderMid.x - f.hipMid.x) / f.torso

        if (r < SEATED) {
            if (standing && peak < TALL) cue(f, "stand_tall", "Stand all the way up, nice and tall.")
            if (!seated) seatedAt = f.tMs
            seated = true
            standing = false
            peak = 0f
            if (timeLimitMs != null && testStart == null) testStart = f.tMs
        } else if (r > STAND && seated) {
            seated = false
            standing = true
            peak = r
            reps++
            if (f.tMs - seatedAt < 900) cue(f, "slow", "Take your time. Slow and steady is perfect.") else cleanReps++
            rep()
        }
        if (standing) peak = max(peak, r)
        if (lean > 0.22f) cue(f, "center", "Keep your weight in the middle.")

        val remaining = timeLimitMs?.let { limit -> testStart?.let { (limit - (f.tMs - it)).coerceAtLeast(0) } ?: limit }
        val done = if (timeLimitMs != null) remaining == 0L else reps >= target
        val phase = when {
            done -> "Done"
            timeLimitMs != null && testStart == null -> "Sit down to start the clock"
            seated -> "Lean forward and stand"
            standing -> "Now sit down slowly"
            else -> "Keep going"
        }
        state = state.copy(reps = reps, target = target, progress = progress, phase = phase, remainingMs = remaining, done = done)
    }

    override fun quality(): Int? = if (reps == 0) null else (100 * cleanReps / reps)

    companion object {
        const val SEATED = 0.42f
        const val STAND = 0.6f
        const val TALL = 0.7f
    }
}

/** Heel raises: the shoulders rise when you go up on your toes. Baseline learned while standing still. */
class HeelRaise(target: Int) : Tracker(Catalog.info(ExerciseId.HEEL_RAISE), target) {
    private var baseline: Float? = null
    private val calib = mutableListOf<Float>()
    private var up = false
    private var peak = 0f
    private var reps = 0
    private var straightSince: Long? = null

    override fun update(f: PoseFrame) {
        if (state.done) return
        if (!f.visible(Lm.CORE)) return
        // Standing up out of a chair or a squat also lifts the shoulders. Only count once the legs have been
        // straight for a moment; until then the baseline follows the shoulders as they finish rising.
        if (f.visible(Lm.LEGS) && (f.kneeMidY - f.hipMid.y) / f.torso < STRAIGHT_LEGS) {
            straightSince = null
            up = false
            peak = 0f
            if (baseline == null) state = state.copy(phase = "Stand tall for a moment")
            return
        }
        val settled = f.tMs - (straightSince ?: f.tMs.also { straightSince = it }) >= SETTLE_MS
        if (!settled) {
            if (baseline != null) baseline = f.shoulderMid.y else calib.clear()
            return
        }
        val y = f.shoulderMid.y
        val base = baseline ?: run {
            calib.add(y)
            if (calib.size >= 12) baseline = calib.sorted()[calib.size / 2]
            state = state.copy(phase = "Stand tall for a moment")
            return
        }
        val rise = (base - y) / f.torso
        val progress = (rise / 0.12f).coerceIn(0f, 1f)
        if (!up && rise > UP) {
            up = true
            reps++
            rep()
        } else if (up && rise < DOWN) {
            up = false
        }
        if (!up) {
            if (peak in 0.035f..UP) cue(f, "higher", "Rise a little higher onto your toes.")
            baseline = base + 0.05f * (y - base) // follow slow drift while flat-footed
            if (rise < DOWN) peak = 0f
        }
        peak = max(peak, rise)
        state = state.copy(reps = reps, target = target, progress = progress, phase = if (up) "Lower slowly" else "Up onto your toes", done = reps >= target)
    }

    companion object {
        const val UP = 0.07f
        const val DOWN = 0.03f
        /** Knee-below-hip over torso: about 0.8 standing tall, 0.2 seated (same signal as sit to stand). */
        const val STRAIGHT_LEGS = 0.6f
        const val SETTLE_MS = 1_500L
    }
}

/** Standing march: a knee counts as lifted once it rises 40% of the thigh's standing length. */
class March(target: Int) : Tracker(Catalog.info(ExerciseId.MARCH), target) {
    private var thigh: Float? = null
    private val calib = mutableListOf<Float>()
    private val lifted = booleanArrayOf(false, false)
    private val best = floatArrayOf(1f, 1f)
    private var reps = 0

    override fun update(f: PoseFrame) {
        if (state.done) return
        if (!f.visible(Lm.CORE + Lm.LEGS)) return
        val l = f[Lm.L_KNEE].y - f[Lm.L_HIP].y
        val r = f[Lm.R_KNEE].y - f[Lm.R_HIP].y
        val t = thigh ?: run {
            calib.add((l + r) / 2f)
            if (calib.size >= 10) thigh = calib.sorted()[calib.size / 2]
            state = state.copy(phase = "Stand tall, hold the chair")
            return
        }
        var progress = 0f
        for ((side, d) in listOf(0 to l, 1 to r)) {
            val ratio = d / t
            // One knee at a time: when both "lift" together it's a squat or a sit, not a step.
            val otherDown = (if (side == 0) r else l) / t > DOWN
            progress = max(progress, ((1f - ratio) / 0.4f).coerceIn(0f, 1f))
            if (!lifted[side] && ratio < LIFT && otherDown) {
                lifted[side] = true
                reps++
                rep()
            } else if (lifted[side] && ratio > DOWN) {
                lifted[side] = false
                best[side] = 1f
            } else if (!lifted[side]) {
                best[side] = min(best[side], ratio)
                if (ratio > DOWN && best[side] in LIFT..0.8f) {
                    cue(f, "knee_higher", "Lift your knees a little higher.")
                    best[side] = 1f
                }
                if (ratio > DOWN) best[side] = 1f
            }
        }
        state = state.copy(reps = reps, target = target, progress = progress, phase = "Left, right, left, right", done = reps >= target)
    }

    companion object {
        const val LIFT = 0.6f
        const val DOWN = 0.85f
    }
}

/** Side leg raise: leg angle away from vertical, measured from the hip. */
class SideLegRaise(target: Int) : Tracker(Catalog.info(ExerciseId.SIDE_LEG_RAISE), target) {
    private val out = booleanArrayOf(false, false)
    private var reps = 0
    private var lastSide = -1

    override fun update(f: PoseFrame) {
        if (state.done) return
        if (!f.visible(Lm.CORE + Lm.LEGS)) return
        var progress = 0f
        for ((side, pair) in listOf(0 to (Lm.L_HIP to Lm.L_ANKLE), 1 to (Lm.R_HIP to Lm.R_ANKLE))) {
            val deg = legAbductionDeg(f[pair.first], f[pair.second])
            progress = max(progress, (deg / 25f).coerceIn(0f, 1f))
            if (!out[side] && deg > UP_DEG) {
                out[side] = true
                reps++
                rep()
                if (side == lastSide && reps > 2) cue(f, "switch", "Nice. Try the other leg.")
                lastSide = side
            } else if (out[side] && deg < DOWN_DEG) {
                out[side] = false
            }
            if (out[side] && f.torsoTiltDeg > 10f) cue(f, "tall", "Keep your body tall and hold the chair.")
        }
        state = state.copy(reps = reps, target = target, progress = progress, phase = "Out to the side, and back", done = reps >= target)
    }

    companion object {
        const val UP_DEG = 18f
        const val DOWN_DEG = 9f
    }
}

/** One-leg balance: time on one foot, and how still the hips stay while you do it. */
class OneLegStand(target: Int, private val holdTargetMs: Long = 10_000) : Tracker(Catalog.info(ExerciseId.ONE_LEG_STAND), target) {
    private var holdStart: Long? = null
    private var lastLifted = 0L
    private var best = 0L
    private var holds = 0
    private var shortHolds = 0
    private val sway = RollingStd(3_000)
    private val steadyScores = mutableListOf<Int>()
    private var credited = false

    override fun update(f: PoseFrame) {
        if (state.done) return
        if (!f.visible(Lm.CORE + Lm.LEGS)) return
        val diff = abs(f[Lm.L_ANKLE].y - f[Lm.R_ANKLE].y) / f.torso
        val lifted = diff > LIFTED
        if (lifted) lastLifted = f.tMs
        val start = holdStart
        if (lifted && start == null) {
            holdStart = f.tMs
            credited = false
            sway.clear()
        } else if (start != null && !lifted && diff < DOWN && f.tMs - lastLifted > 400) {
            val held = lastLifted - start
            if (held < 3_000) {
                shortHolds++
                if (shortHolds >= 2) cue(f, "chair", "Hold the chair a little more. That's perfectly fine.")
            }
            holdStart = null
        }
        val holdMs = holdStart?.let { f.tMs - it } ?: 0L
        var steadiness: Int? = state.steadiness
        if (holdStart != null) {
            sway.add(f.tMs, f.hipMid.x / f.shoulderWidth)
            if (holdMs > 1_500) {
                steadiness = (100 - sway.std() * 400).roundToInt().coerceIn(0, 100)
                steadyScores.add(steadiness)
            }
            if (holdMs >= holdTargetMs && !credited) {
                credited = true
                holds++
                rep()
                cue(f, "switch_leg", if (holds < target) "Well done. Rest, then switch legs." else "Well done.")
            }
        }
        best = max(best, holdMs)
        state = state.copy(
            reps = holds,
            target = target,
            progress = (holdMs / holdTargetMs.toFloat()).coerceIn(0f, 1f),
            phase = if (holdStart != null) "Hold… ${(holdMs / 1000)} s" else "Lift one foot",
            holdMs = holdMs,
            bestHoldMs = best,
            steadiness = steadiness,
            done = holds >= target
        )
    }

    override fun quality(): Int? = if (steadyScores.isEmpty()) null else steadyScores.average().roundToInt()

    companion object {
        const val LIFTED = 0.15f
        const val DOWN = 0.08f
    }
}

/**
 * Possible fall: the torso stays far from upright (or the head ends up below the hips) for more
 * than 1.5 seconds. Upright again for a second clears it.
 */
class FallCheck(private val holdMs: Long = 1_500) {
    private var since: Long? = null
    private var uprightSince: Long? = null
    var alarm = false
        private set
    /** False after "I'm OK" until the person has been upright for a moment, so getting up slowly doesn't re-alarm. */
    private var armed = true

    fun feed(f: PoseFrame): Boolean {
        if (!f.visible(Lm.CORE, 0.4f)) return alarm
        val horizontal = f.torsoTiltDeg > 60f || f[Lm.NOSE].y > f.hipMid.y + 0.02f
        if (!armed) {
            if (horizontal || f.torsoTiltDeg >= 30f) uprightSince = null
            else if (f.tMs - (uprightSince ?: f.tMs.also { uprightSince = it }) >= REARM_MS) {
                armed = true
                uprightSince = null
            }
            return false
        }
        if (horizontal) {
            uprightSince = null
            val s = since ?: f.tMs.also { since = it }
            if (f.tMs - s >= holdMs) alarm = true
        } else {
            since = null
            if (f.torsoTiltDeg < 30f) {
                val u = uprightSince ?: f.tMs.also { uprightSince = it }
                if (f.tMs - u > 1_000 && !alarm) since = null
            }
        }
        return alarm
    }

    fun clear(waitForUpright: Boolean = false) {
        alarm = false
        since = null
        uprightSince = null
        armed = !waitForUpright
    }

    private companion object {
        const val REARM_MS = 2_000L
    }
}

/** CDC STEADI 30-second chair stand: a score below these means higher fall risk. */
object ChairStandNorms {
    private val below = listOf(
        60..64 to (14 to 12), 65..69 to (12 to 11), 70..74 to (12 to 10), 75..79 to (11 to 10),
        80..84 to (10 to 9), 85..89 to (8 to 8), 90..94 to (7 to 4)
    )

    /** Fewest stands that count as average for this age and sex, or null outside 60..94. */
    fun threshold(age: Int, female: Boolean): Int? = below.firstOrNull { age in it.first }?.second?.let { if (female) it.second else it.first }

    fun belowAverage(stands: Int, age: Int, female: Boolean): Boolean? = threshold(age, female)?.let { stands < it }
}
