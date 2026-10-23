package com.steady.tv.data

import com.steady.tv.pose.Catalog
import com.steady.tv.pose.ChairStandNorms
import com.steady.tv.pose.ExerciseId
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PlanItem(val id: ExerciseId, val target: Int)

data class Plan(val personId: String, val items: List<PlanItem>, val reasons: List<String>, val minutes: Int) {
    val why: String get() = reasons.joinToString(" ")
}

data class CoachNote(val spoken: String, val summary: String, val next: String, val family: String, val source: String)

/**
 * The coach's rules. Progression follows how the Otago programme progresses: add reps once the
 * target is met twice, ease off after a clear miss, and repeat the strength check every two weeks.
 * These rules decide what changes; a language model (when available) only explains them.
 */
object Coach {
    private val ORDER = listOf(ExerciseId.MARCH, ExerciseId.SIT_TO_STAND, ExerciseId.HEEL_RAISE, ExerciseId.SIDE_LEG_RAISE, ExerciseId.ONE_LEG_STAND)
    private const val DAY = 86_400_000L

    /** Starting targets for someone new: gentler after 80. */
    fun startingTarget(id: ExerciseId, age: Int): Int {
        val base = Catalog.info(id).defaultTarget
        return when {
            id == ExerciseId.ONE_LEG_STAND -> 2
            age >= 80 && id != ExerciseId.MARCH -> max(6, base - 2)
            else -> base
        }
    }

    private fun recent(p: Person, id: ExerciseId, n: Int) = p.history.takeLast(n).mapNotNull { s -> s.results.firstOrNull { it.id == id && !it.skipped } }

    fun plan(p: Person, now: Long = System.currentTimeMillis()): Plan {
        val reasons = mutableListOf<Pair<Int, String>>()
        val items = ORDER.map { id ->
            if (id == ExerciseId.ONE_LEG_STAND) return@map PlanItem(id, 2)
            val r = recent(p, id, 2)
            val last = r.lastOrNull()
            var target = last?.target ?: startingTarget(id, p.age)
            val name = Catalog.info(id).title.lowercase()
            if (r.size == 2 && r.all { it.reps >= it.target }) {
                target += if (id == ExerciseId.MARCH) 4 else 2
                reasons += priority(id) to "You reached ${last!!.target} $name twice in a row, so today it's $target."
            } else if (last != null && last.reps < last.target * 0.7) {
                target = max(4, target - 2)
                reasons += priority(id) to "Let's take $name down to $target today and build back up."
            }
            PlanItem(id, target)
        }.toMutableList()

        val holds = recent(p, ExerciseId.ONE_LEG_STAND, 6).map { (it.bestHoldMs / 1000).toInt() }
        if (holds.size >= 2 && holds.last() > holds.first()) {
            reasons += 0 to "Your one-leg balance went from ${holds.first()} to ${holds.last()} seconds. Today we aim for 10 on each leg."
        }

        val lastTest = p.history.lastOrNull { it.chairStand != null }
        val daysSince = lastTest?.let { (now - it.at) / DAY } ?: Long.MAX_VALUE
        if (daysSince >= 13) {
            items += PlanItem(ExerciseId.CHAIR_STAND_TEST, 30)
            reasons += 0 to (lastTest?.let { "It's been $daysSince days since your chair stand check (${it.chairStand} stands), so we'll repeat it at the end." }
                ?: "We'll finish with the 30-second chair stand check, so we can track your strength from today.")
        }
        if (p.history.isEmpty()) reasons.add(0, 0 to "Welcome! We'll start gently and learn what suits you.")
        val ordered = reasons.sortedBy { it.first }.map { it.second }
        return Plan(p.id, items, ordered, 10 + items.size * 2)
    }

    private fun priority(id: ExerciseId) = if (id == ExerciseId.SIT_TO_STAND) 0 else 1

    /** Mean time of day this person exercises, in hours, learned from their own history. */
    fun usualHour(p: Person): Double? {
        if (p.history.isEmpty()) return null
        return p.history.map { s -> Calendar.getInstance().apply { timeInMillis = s.at }.let { it.get(Calendar.HOUR_OF_DAY) + it.get(Calendar.MINUTE) / 60.0 } }.average()
    }

    /** Who is most likely in front of the TV at this hour. */
    fun likely(h: Household, hour: Double): Pair<Person, String>? {
        if (h.people.isEmpty()) return null
        var best = h.people.first()
        var gap = Double.MAX_VALUE
        for (p in h.people) {
            val u = usualHour(p) ?: continue
            val d = min(abs(u - hour), 24 - abs(u - hour))
            if (d < gap) {
                gap = d
                best = p
            }
        }
        val u = usualHour(best) ?: return best to ""
        val hh = u.toInt()
        val mm = ((u - hh) * 60).roundToInt().coerceIn(0, 59)
        val clock = "${if (hh % 12 == 0) 12 else hh % 12}:${mm.toString().padStart(2, '0')} ${if (hh < 12) "AM" else "PM"}"
        return best to "${best.name} usually exercises around $clock"
    }

    fun streakDays(p: Person, now: Long = System.currentTimeMillis()): Int {
        val days = p.history.map { it.at / DAY }.toSet()
        return (0 until 7).count { (now / DAY - it) in days }
    }

    /** Plain-words summary when no language model is reachable. */
    fun rulesNote(p: Person, s: SessionRecord, familyName: String): CoachNote {
        val sts = s.results.firstOrNull { it.id == ExerciseId.SIT_TO_STAND && !it.skipped }
        val bal = s.results.firstOrNull { it.id == ExerciseId.ONE_LEG_STAND && it.bestHoldMs > 0 }
        val prevSts = recent(p.copy(history = p.history.dropLast(1)), ExerciseId.SIT_TO_STAND, 1).firstOrNull()
        val prevChair = p.history.dropLast(1).lastOrNull { it.chairStand != null }?.chairStand
        val norm = ChairStandNorms.threshold(p.age, p.female)
        val parts = mutableListOf<String>()
        s.chairStand?.let { c ->
            parts += "Chair stand check: $c stands" + (prevChair?.let { ", up from $it" }?.takeIf { c > prevChair } ?: "") +
                (norm?.let { if (c >= it) ", in the usual range for your age." else ", the usual range starts at $it." } ?: ".")
        }
        sts?.let { parts += "${it.reps} sit to stand${if (it.reps == 1) "" else "s"}" + (prevSts?.takeIf { p2 -> it.reps > p2.reps }?.let { p2 -> ", up from ${p2.reps}." } ?: ".") }
        bal?.let { parts += "Best balance hold: ${it.bestHoldMs / 1000} seconds." }
        return CoachNote(
            spoken = "Well done, ${p.name}. That's another session in the bank.",
            summary = parts.joinToString(" ").ifBlank { "Session complete." },
            next = "We'll add a little more where you found it easy.",
            family = "${p.name} finished a ${max(1, s.durationSec / 60)}-minute strength and balance session on Steady" +
                (s.chairStand?.let { c -> ", with $c stands on the 30-second chair stand check." } ?: "."),
            source = "rules"
        )
    }
}
