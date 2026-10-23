package com.steady.tv.data

import com.steady.tv.pose.Catalog
import com.steady.tv.pose.Category
import com.steady.tv.pose.Kind
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
    private const val DAY = 86_400_000L

    /** Starting targets for someone new: gentler after 80, and for sore knees on leg moves. */
    fun startingTarget(id: ExerciseId, age: Int, conditions: Set<Condition> = emptySet()): Int {
        val info = Catalog.info(id)
        if (info.kind == Kind.HOLD) return info.defaultTarget
        var t = info.defaultTarget
        if (age >= 80 && id != ExerciseId.MARCH && id != ExerciseId.SEATED_MARCH && id != ExerciseId.SEATED_JACKS) t = max(6, t - 2)
        if (Condition.KNEE_PAIN in conditions && info.category == Category.STRENGTH) t = max(4, t - 2)
        return t
    }

    private fun recent(p: Person, id: ExerciseId, n: Int) = p.history.takeLast(n).mapNotNull { s -> s.results.firstOrNull { it.id == id && !it.skipped } }
    private fun mastered(p: Person, id: ExerciseId) = recent(p, id, 2).let { r -> r.size == 2 && r.all { it.reps >= it.target } }

    fun allowed(p: Person, id: ExerciseId) = Health.whyNot(p.conditions, id) == null

    fun plan(p: Person, now: Long = System.currentTimeMillis()): Plan {
        val reasons = mutableListOf<Pair<Int, String>>()
        val c = p.conditions
        val seatedFirst = Condition.WALKING_AID in c || p.age >= 88

        // 1. Warm up, standing or seated.
        val warm = if (seatedFirst || Condition.DIZZINESS in c) ExerciseId.SEATED_MARCH else ExerciseId.MARCH

        // 2. The main strength move: hands helping for sore knees or a walking aid, arms crossed once it's easy.
        val strength = when {
            Condition.KNEE_PAIN in c || seatedFirst -> ExerciseId.HANDS_ASSISTED_STAND
            recent(p, ExerciseId.SIT_TO_STAND, 2).let { r -> r.size == 2 && r.all { it.reps >= it.target && it.target >= 14 } } -> {
                reasons += 0 to "You've mastered sit to stand, so today try it with your arms crossed."
                ExerciseId.ARMS_CROSSED_STAND
            }
            else -> ExerciseId.SIT_TO_STAND
        }

        // 3. Balance follows the Otago ladder: feet together, heel to toe, then one leg.
        val ladder = listOf(ExerciseId.FEET_TOGETHER, ExerciseId.TANDEM_STANCE, ExerciseId.ONE_LEG_STAND).filter { allowed(p, it) }
        val oneLegHolds = recent(p, ExerciseId.ONE_LEG_STAND, 2).map { (it.bestHoldMs / 1000).toInt() }
        val balance = when {
            Condition.RECENT_FALL in c || Condition.WALKING_AID in c -> ladder.firstOrNull { !mastered(p, it) } ?: ladder.last()
            oneLegHolds.size == 2 && oneLegHolds.all { it < 3 } && ExerciseId.TANDEM_STANCE in ladder -> {
                reasons += 1 to "Your one-leg holds were under 3 seconds, so today we'll practise heel-to-toe standing first."
                ExerciseId.TANDEM_STANCE
            }
            else -> ExerciseId.ONE_LEG_STAND
        }

        val core = listOf(warm, strength, ExerciseId.HEEL_RAISE, ExerciseId.SIDE_LEG_RAISE, balance).filter { allowed(p, it) }

        // 4. Two extras that change day to day, nudged towards what this person's data says they need.
        val boost = mutableMapOf<ExerciseId, Pair<Int, String>>()
        fun need(id: ExerciseId, score: Int, why: String) { if (score > (boost[id]?.first ?: 0)) boost[id] = score to why }
        val lastTest = p.history.lastOrNull { it.chairStand != null }
        if (lastTest?.chairStandBelow == true) {
            need(ExerciseId.MINI_SQUAT, 3, "Your chair stand check was below the usual range, so we've added mini squats for extra leg strength.")
            need(ExerciseId.SEATED_KNEE_EXTENSION, 3, "Your chair stand check was below the usual range, so we've added knee straightening for extra leg strength.")
        }
        val leanCues = p.history.takeLast(3).sumOf { s -> s.results.sumOf { it.cues["center"] ?: 0 } }
        if (leanCues >= 3) need(ExerciseId.WEIGHT_SHIFT, 3, "You've been leaning to one side, so we've added side to side sways to find your middle.")
        val bestHold = recent(p, ExerciseId.ONE_LEG_STAND, 3).maxOfOrNull { it.bestHoldMs }
        if (bestHold != null && bestHold < 5_000) need(ExerciseId.KNEE_LIFT_HOLD, 2, "Balance holds are still short, so we've added knee lifts to build them up.")
        if (Condition.KNEE_PAIN in c) need(ExerciseId.SEATED_KNEE_EXTENSION, 4, "Strong thigh muscles take pressure off sore knees, so knee straightening is in today.")
        if (Condition.OSTEOPOROSIS in c) need(ExerciseId.SIDE_STEP, 2, "Standing, weight-bearing moves help your bones, so side steps are in today.")
        if (Condition.BACK_PAIN in c) need(ExerciseId.SHOULDER_SHRUG, 1, "Gentle shoulder shrugs ease tension without loading your back.")

        val day = p.history.size
        fun pick(pool: List<ExerciseId>): ExerciseId? {
            val ok = pool.filter { allowed(p, it) && it !in core && !(seatedFirst && !Catalog.info(it).sit && !Catalog.info(it).usesChair) }
            if (ok.isEmpty()) return null
            return ok.withIndex().maxBy { (i, id) -> (boost[id]?.first ?: 0) * 100 - Math.floorMod(i - day, ok.size) }.value
        }
        val body = pick(listOf(ExerciseId.MINI_SQUAT, ExerciseId.SEATED_KNEE_EXTENSION, ExerciseId.ARM_CURL, ExerciseId.STANDING_KNEE_CURL, ExerciseId.FRONT_ARM_RAISE, ExerciseId.SEATED_KNEES_OUT, ExerciseId.SIDE_ARM_RAISE, ExerciseId.OVERHEAD_REACH, ExerciseId.SIDE_STEP))
        val flow = pick(listOf(ExerciseId.WEIGHT_SHIFT, ExerciseId.HEAD_TURN, ExerciseId.SHOULDER_SHRUG, ExerciseId.KNEE_LIFT_HOLD, ExerciseId.SIDE_BEND, ExerciseId.TRUNK_TWIST, ExerciseId.SEATED_JACKS, ExerciseId.HIP_HINGE))
        for (x in listOfNotNull(body, flow)) boost[x]?.let { reasons += 1 to it.second }

        val order = core.take(2) + listOfNotNull(body) + core.drop(2) + listOfNotNull(flow)
        val items = order.map { id ->
            val info = Catalog.info(id)
            if (info.kind == Kind.HOLD) return@map PlanItem(id, info.defaultTarget)
            val r = recent(p, id, 2)
            val last = r.lastOrNull()
            var target = last?.target ?: startingTarget(id, p.age, c)
            val name = info.title.lowercase()
            if (r.size == 2 && r.all { it.reps >= it.target }) {
                target += if (id == ExerciseId.MARCH || id == ExerciseId.SEATED_MARCH) 4 else 2
                reasons += priority(id) to "You reached ${last!!.target} $name twice in a row, so today it's $target."
            } else if (last != null && last.reps < last.target * 0.7) {
                target = max(4, target - 2)
                reasons += priority(id) to "Let's take $name down to $target today and build back up."
            }
            PlanItem(id, target)
        }.toMutableList()

        val holds = recent(p, ExerciseId.ONE_LEG_STAND, 6).map { (it.bestHoldMs / 1000).toInt() }
        if (balance == ExerciseId.ONE_LEG_STAND && holds.size >= 2 && holds.last() > holds.first()) {
            reasons += 0 to "Your one-leg balance went from ${holds.first()} to ${holds.last()} seconds. Today we aim for 10 on each leg."
        }

        // 5. The CDC check every two weeks, unless it isn't safe to do unaided.
        val daysSince = lastTest?.let { (now - it.at) / DAY } ?: Long.MAX_VALUE
        if (daysSince >= 13) {
            if (Condition.WALKING_AID in c) {
                reasons += 2 to "We'll skip the timed chair stand check because you use a walking aid. Your physio can do it with you."
            } else {
                items += PlanItem(ExerciseId.CHAIR_STAND_TEST, 30)
                reasons += 0 to (lastTest?.let { "It's been $daysSince days since your chair stand check (${it.chairStand} stands), so we'll repeat it at the end." }
                    ?: "We'll finish with the 30-second chair stand check, so we can track your strength from today.")
            }
        }
        Health.notes(c).forEach { reasons += 1 to it }
        if (p.history.isEmpty()) reasons.add(0, 0 to "Welcome! We'll start gently and learn what suits you.")
        val ordered = reasons.sortedBy { it.first }.map { it.second }
        return Plan(p.id, items, ordered, 8 + items.size * 2)
    }

    private fun priority(id: ExerciseId) = if (id == ExerciseId.SIT_TO_STAND || id == ExerciseId.HANDS_ASSISTED_STAND || id == ExerciseId.ARMS_CROSSED_STAND) 0 else 1

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
