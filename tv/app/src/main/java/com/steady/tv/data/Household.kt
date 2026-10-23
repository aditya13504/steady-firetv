package com.steady.tv.data

import com.steady.tv.pose.ExerciseId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/**
 * The household lives on the TV: health history never needs to leave the home. The companion
 * server only sees what a single coaching request needs, and keeps nothing.
 */
data class ResultRecord(
    val id: ExerciseId,
    val reps: Int,
    val target: Int,
    val quality: Int? = null,
    val bestHoldMs: Long = 0,
    val cues: Map<String, Int> = emptyMap(),
    val skipped: Boolean = false
)

data class SessionRecord(
    val at: Long,
    val durationSec: Int,
    val results: List<ResultRecord>,
    val chairStand: Int? = null,
    val chairStandBelow: Boolean? = null,
    val coach: String? = null,
    val stoppedEarly: Boolean = false
)

data class Person(
    val id: String,
    val name: String,
    val age: Int,
    val female: Boolean,
    val goal: String = "",
    val history: List<SessionRecord> = emptyList(),
    val conditions: Set<Condition> = emptySet()
)

/** Health conditions that change which exercises are safe and how hard they start. Chosen at setup. */
enum class Condition(val label: String) {
    KNEE_PAIN("Knee pain"),
    HIP_REPLACEMENT("Hip replacement"),
    BACK_PAIN("Back pain"),
    OSTEOPOROSIS("Osteoporosis"),
    SHOULDER_PAIN("Shoulder pain"),
    DIZZINESS("Dizziness"),
    RECENT_FALL("Fell this year"),
    WALKING_AID("Cane or walker"),
    HEART("Heart or blood pressure")
}

data class FamilyNote(val at: Long, val kind: String, val text: String)

data class Household(
    val name: String,
    val familyName: String,
    val familyRelation: String,
    /** Secret ntfy topic the family subscribes to for push alerts. */
    val familyTopic: String,
    val people: List<Person>,
    val demo: Boolean = false,
    val sent: List<FamilyNote> = emptyList()
) {
    fun person(id: String) = people.firstOrNull { it.id == id }
    val hasFamily get() = familyName.isNotBlank()
}

object Topics {
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
    /** ~120 bits of randomness: unguessable, so only people given the link can read alerts. */
    fun newTopic(): String {
        val r = SecureRandom()
        return "steady-" + (1..24).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }
}

object HouseholdJson {
    fun write(h: Household): String = JSONObject()
        .put("version", 1)
        .put("name", h.name)
        .put("familyName", h.familyName)
        .put("familyRelation", h.familyRelation)
        .put("familyTopic", h.familyTopic)
        .put("demo", h.demo)
        .put("people", JSONArray(h.people.map { personJson(it) }))
        .put("sent", JSONArray(h.sent.takeLast(30).map { JSONObject().put("at", it.at).put("kind", it.kind).put("text", it.text) }))
        .toString()

    fun personJson(p: Person): JSONObject = JSONObject()
        .put("id", p.id).put("name", p.name).put("age", p.age).put("female", p.female).put("goal", p.goal)
        .put("conditions", JSONArray(p.conditions.map { it.name }))
        .put("history", JSONArray(p.history.map { sessionJson(it) }))

    fun sessionJson(s: SessionRecord): JSONObject = JSONObject()
        .put("at", s.at).put("durationSec", s.durationSec).put("stoppedEarly", s.stoppedEarly)
        .put("results", JSONArray(s.results.map { resultJson(it) }))
        .apply {
            s.chairStand?.let { put("chairStand", it) }
            s.chairStandBelow?.let { put("chairStandBelow", it) }
            s.coach?.let { put("coach", it) }
        }

    fun resultJson(r: ResultRecord): JSONObject = JSONObject()
        .put("id", r.id.name).put("reps", r.reps).put("target", r.target).put("bestHoldMs", r.bestHoldMs).put("skipped", r.skipped)
        .put("cues", JSONObject(r.cues as Map<*, *>))
        .apply { r.quality?.let { put("quality", it) } }

    fun read(text: String): Household {
        val j = JSONObject(text)
        val people = j.getJSONArray("people").let { a -> List(a.length()) { readPerson(a.getJSONObject(it)) } }
        val sent = j.optJSONArray("sent")?.let { a -> List(a.length()) { a.getJSONObject(it).let { n -> FamilyNote(n.getLong("at"), n.getString("kind"), n.getString("text")) } } } ?: emptyList()
        return Household(j.getString("name"), j.optString("familyName"), j.optString("familyRelation"), j.getString("familyTopic"), people, j.optBoolean("demo"), sent)
    }

    private fun readPerson(p: JSONObject): Person {
        val h = p.getJSONArray("history")
        val c = p.optJSONArray("conditions")
        val conditions = if (c == null) emptySet() else List(c.length()) { c.optString(it) }.mapNotNull { n -> Condition.entries.firstOrNull { it.name == n } }.toSet()
        return Person(p.getString("id"), p.getString("name"), p.getInt("age"), p.getBoolean("female"), p.optString("goal"), List(h.length()) { readSession(h.getJSONObject(it)) }, conditions)
    }

    private fun readSession(s: JSONObject): SessionRecord {
        val r = s.getJSONArray("results")
        return SessionRecord(
            at = s.getLong("at"), durationSec = s.getInt("durationSec"),
            results = List(r.length()) { i ->
                val o = r.getJSONObject(i)
                val cues = o.optJSONObject("cues")?.let { c -> c.keys().asSequence().associateWith { k -> c.getInt(k) } } ?: emptyMap()
                ResultRecord(ExerciseId.valueOf(o.getString("id")), o.getInt("reps"), o.getInt("target"), if (o.has("quality")) o.getInt("quality") else null, o.optLong("bestHoldMs"), cues, o.optBoolean("skipped"))
            },
            chairStand = if (s.has("chairStand")) s.getInt("chairStand") else null,
            chairStandBelow = if (s.has("chairStandBelow")) s.getBoolean("chairStandBelow") else null,
            coach = s.optString("coach").ifBlank { null },
            stoppedEarly = s.optBoolean("stoppedEarly")
        )
    }
}

/** Atomic JSON file in the app's private storage. */
class HouseholdStore(private val file: File) {
    fun load(): Household? = runCatching { if (file.exists()) HouseholdJson.read(file.readText()) else null }.getOrNull()

    fun save(h: Household) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(HouseholdJson.write(h))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun clear() {
        file.delete()
    }
}

object DemoHousehold {
    private const val DAY = 86_400_000L

    private fun at(daysAgo: Int, hour: Int, minute: Int, now: Long): Long {
        val c = java.util.Calendar.getInstance().apply {
            timeInMillis = now - daysAgo * DAY
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
        }
        return c.timeInMillis
    }

    private fun session(at: Long, sts: Int, stsTarget: Int, holdS: Int, steady: Int, chair: Int? = null, female: Boolean = true) = SessionRecord(
        at = at, durationSec = 14 * 60,
        results = listOf(
            ResultRecord(ExerciseId.MARCH, 20, 20),
            ResultRecord(ExerciseId.SIT_TO_STAND, sts, stsTarget, 80),
            ResultRecord(ExerciseId.HEEL_RAISE, 10, 10),
            ResultRecord(ExerciseId.SIDE_LEG_RAISE, 10, 10),
            ResultRecord(ExerciseId.ONE_LEG_STAND, if (holdS >= 10) 2 else 0, 2, steady, holdS * 1000L)
        ),
        chairStand = chair, chairStandBelow = chair?.let { true }
    )

    /** Fictional household used by "Explore the demo". */
    fun create(now: Long = System.currentTimeMillis()) = Household(
        name = "The Lee household", familyName = "Daniel", familyRelation = "son", familyTopic = Topics.newTopic(), demo = true,
        people = listOf(
            Person(
                "maggie", "Maggie", 76, true, "Walk to the farmers market on Saturdays without worrying about falling.",
                listOf(
                    session(at(14, 9, 10, now), 7, 10, 4, 58, chair = 8),
                    session(at(11, 9, 5, now), 8, 10, 5, 61),
                    session(at(9, 8, 55, now), 9, 10, 6, 66),
                    session(at(6, 9, 20, now), 10, 10, 7, 70),
                    session(at(4, 9, 0, now), 10, 10, 8, 72),
                    session(at(2, 9, 15, now), 10, 10, 8, 75)
                ),
                conditions = setOf(Condition.OSTEOPOROSIS)
            ),
            Person(
                "walter", "Walter", 81, false, "Get up from the sofa without pushing off with my hands.",
                listOf(
                    session(at(8, 18, 30, now), 5, 8, 3, 50, chair = 8),
                    session(at(5, 18, 40, now), 6, 8, 3, 54),
                    session(at(3, 18, 20, now), 7, 8, 4, 57)
                ),
                conditions = setOf(Condition.SHOULDER_PAIN)
            )
        )
    )

    fun newPersonId() = UUID.randomUUID().toString().take(8)
}
