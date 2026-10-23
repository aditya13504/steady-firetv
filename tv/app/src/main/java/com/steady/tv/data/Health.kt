package com.steady.tv.data

import com.steady.tv.pose.ExerciseId
import com.steady.tv.pose.ExerciseId.*

/**
 * Which moves to leave out for which conditions, following common physiotherapy precautions
 * (for example no twisting or forward bending with osteoporosis, no deep hip bending or crossing
 * after a hip replacement, arms below shoulder height with a painful shoulder). This is general
 * guidance, not medical advice; the app tells people to check with their clinician.
 */
object Health {
    private val avoid: Map<Condition, Set<ExerciseId>> = mapOf(
        Condition.KNEE_PAIN to setOf(MINI_SQUAT, ARMS_CROSSED_STAND, SIT_TO_STAND),
        Condition.HIP_REPLACEMENT to setOf(HIP_HINGE, TRUNK_TWIST, TANDEM_STANCE, MINI_SQUAT),
        Condition.BACK_PAIN to setOf(HIP_HINGE, TRUNK_TWIST, SIDE_BEND),
        Condition.OSTEOPOROSIS to setOf(HIP_HINGE, TRUNK_TWIST, SIDE_BEND),
        Condition.SHOULDER_PAIN to setOf(OVERHEAD_REACH, SEATED_JACKS, SIDE_ARM_RAISE),
        Condition.DIZZINESS to setOf(HEAD_TURN, HIP_HINGE, SEATED_JACKS, MARCH),
        Condition.RECENT_FALL to setOf(SIDE_STEP, KNEE_LIFT_HOLD),
        Condition.WALKING_AID to setOf(SIDE_STEP, KNEE_LIFT_HOLD, HIP_HINGE, OVERHEAD_REACH, SIDE_ARM_RAISE, SIDE_BEND, TANDEM_STANCE, MARCH, SIT_TO_STAND, ARMS_CROSSED_STAND),
        Condition.HEART to setOf(SEATED_JACKS)
    )

    /** The condition that rules this exercise out, if any. */
    fun whyNot(conditions: Set<Condition>, id: ExerciseId): Condition? = conditions.firstOrNull { id in (avoid[it] ?: emptySet()) }

    /** One plain sentence per condition, said once in the plan's reasons. */
    fun notes(conditions: Set<Condition>): List<String> = conditions.sortedBy { it.ordinal }.map {
        when (it) {
            Condition.KNEE_PAIN -> "Because of your knee pain, you'll use your hands to help you stand, and we'll skip squats."
            Condition.HIP_REPLACEMENT -> "To protect your hip, we'll skip twists, hinges, squats and heel-to-toe standing."
            Condition.BACK_PAIN -> "To look after your back, we'll skip twisting and bending moves."
            Condition.OSTEOPOROSIS -> "For your bones, we'll skip twisting and forward bending, and keep standing strength in."
            Condition.SHOULDER_PAIN -> "We'll keep arm moves below shoulder height to protect your shoulder."
            Condition.DIZZINESS -> "To keep dizziness away, we'll march sitting down and skip head turns and bending."
            Condition.RECENT_FALL -> "After your fall, we'll rebuild balance step by step, with the chair always close."
            Condition.WALKING_AID -> "Since you use a cane or walker, more moves are seated and the chair stays close."
            Condition.HEART -> "Breathe out as you push and never hold your breath. We'll skip the fast moves."
        }
    }
}
