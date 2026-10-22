package com.steady.tv.net

import android.os.Handler
import android.os.Looper
import com.steady.tv.pose.ExerciseId
import com.steady.tv.pose.Pt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class HistoryPoint(val at: String, val sitToStand: Int?, val balanceSec: Int, val chairStand: Int?)

data class Profile(
    val id: String,
    val name: String,
    val age: Int,
    val female: Boolean,
    val goal: String,
    val sessions: Int,
    val streakDays: Int,
    val history: List<HistoryPoint>
)

data class Household(val name: String, val familyName: String, val familyRelation: String, val likelyId: String, val likelyReason: String, val profiles: List<Profile>, val llm: Boolean)

data class PlanItem(val id: ExerciseId, val target: Int)

data class Plan(val items: List<PlanItem>, val why: String, val intro: String, val minutes: Int)

data class CoachNote(val spoken: String, val summary: String, val next: String, val family: String, val source: String)

/** Everything the TV hears from the phone, via the companion relay. */
sealed interface PhoneEvent {
    data class Code(val code: String, val phoneUrl: String, val localUrl: String, val phoneConnected: Boolean) : PhoneEvent
    data class PhoneLink(val connected: Boolean) : PhoneEvent
    data class Pose(val pts: List<Pt>?, val aspect: Float) : PhoneEvent
    data class Command(val cmd: String, val said: String?) : PhoneEvent
    data class Link(val up: Boolean) : PhoneEvent
}

/**
 * Talks to the Steady companion server: a WebSocket for pairing and live pose points, and a small
 * REST API for household history, plans and the coach.
 */
class CompanionClient(var baseUrl: String, private val onEvent: (PhoneEvent) -> Unit) {
    private val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var code: String? = null
    private var closed = false

    fun connect() {
        closed = false
        val wsUrl = baseUrl.replaceFirst("http", "ws") + "/ws?role=tv" + (code?.let { "&code=$it" } ?: "")
        ws = http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                post(PhoneEvent.Link(true))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = JSONObject(text)
                when (m.optString("t")) {
                    "paired-code" -> {
                        code = m.getString("code")
                        post(PhoneEvent.Code(m.getString("code"), m.getString("phoneUrl"), m.getString("localUrl"), m.optBoolean("phoneConnected")))
                    }
                    "phone" -> post(PhoneEvent.PhoneLink(m.optBoolean("connected")))
                    "pose" -> post(PhoneEvent.Pose(parsePose(m.optJSONArray("lm")), m.optDouble("ar", 0.75).toFloat()))
                    "cmd" -> post(PhoneEvent.Command(m.getString("cmd"), m.optString("said").ifBlank { null }))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = retry()
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = retry()
        })
    }

    private fun retry() {
        post(PhoneEvent.Link(false))
        if (!closed) main.postDelayed({ if (!closed) connect() }, 3_000)
    }

    private fun post(e: PhoneEvent) {
        main.post { onEvent(e) }
    }

    private fun parsePose(arr: JSONArray?): List<Pt>? {
        if (arr == null || arr.length() < 33) return null
        return List(arr.length()) { i ->
            val p = arr.getJSONArray(i)
            Pt(p.getDouble(0).toFloat(), p.getDouble(1).toFloat(), p.optDouble(2, 1.0).toFloat())
        }
    }

    /** Tell the phone what is happening, so its screen matches the TV. */
    fun sendState(state: JSONObject) {
        ws?.send(state.put("t", "state").toString())
    }

    fun close() {
        closed = true
        ws?.close(1000, "bye")
    }

    // ------------------------------------------------------------------ REST

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(baseUrl + path).build()).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            JSONObject(r.body!!.string())
        }
    }

    private suspend fun postJson(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + path).post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            JSONObject(r.body!!.string())
        }
    }

    suspend fun household(): Household {
        val j = get("/api/household")
        val profiles = j.getJSONArray("profiles").let { a ->
            List(a.length()) { i ->
                val p = a.getJSONObject(i)
                val h = p.getJSONArray("history")
                Profile(
                    id = p.getString("id"), name = p.getString("name"), age = p.getInt("age"), female = p.getString("sex") == "female",
                    goal = p.getString("goal"), sessions = p.getInt("sessions"), streakDays = p.getInt("streakDays"),
                    history = List(h.length()) { k ->
                        val s = h.getJSONObject(k)
                        HistoryPoint(s.getString("at"), s.optInt("sitToStand").takeIf { !s.isNull("sitToStand") }, s.optInt("balanceSec"), s.optInt("chairStand").takeIf { !s.isNull("chairStand") })
                    }
                )
            }
        }
        val likely = j.getJSONObject("likely")
        val fam = j.getJSONObject("family")
        return Household(j.getString("name"), fam.getString("name"), fam.getString("relation"), likely.getString("id"), likely.optString("reason"), profiles, j.optBoolean("llm"))
    }

    suspend fun plan(profileId: String): Plan {
        val j = get("/api/plan/$profileId")
        val items = j.getJSONArray("items")
        return Plan(
            items = List(items.length()) { i -> items.getJSONObject(i).let { PlanItem(ExerciseId.valueOf(it.getString("id")), it.getInt("target")) } },
            why = j.optString("why"), intro = j.optString("intro"), minutes = j.optInt("minutes", 15)
        )
    }

    suspend fun saveSession(profileId: String, session: JSONObject): CoachNote {
        val c = postJson("/api/sessions", JSONObject().put("profileId", profileId).put("session", session)).getJSONObject("coach")
        return CoachNote(c.getString("spoken"), c.getString("summary"), c.optString("next"), c.optString("family"), c.optString("source"))
    }

    suspend fun alert(profileId: String, kind: String): String =
        postJson("/api/alerts", JSONObject().put("profileId", profileId).put("kind", kind)).optString("notified", "your family")
}
