package com.steady.tv.net

import android.graphics.BitmapFactory
import android.os.Handler
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.os.Looper
import com.steady.tv.data.CoachNote
import com.steady.tv.data.Household
import com.steady.tv.data.HouseholdJson
import com.steady.tv.data.Person
import com.steady.tv.data.Plan
import com.steady.tv.data.SessionRecord
import com.steady.tv.pose.ChairStandNorms
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

/** Everything the TV hears from the phone, via the companion relay. */
sealed interface PhoneEvent {
    data class Code(val code: String, val phoneUrl: String, val localUrl: String, val phoneConnected: Boolean) : PhoneEvent
    data class PhoneLink(val connected: Boolean) : PhoneEvent
    data class Pose(val pts: List<Pt>?, val aspect: Float) : PhoneEvent
    data class Command(val cmd: String, val said: String?) : PhoneEvent
    data class Link(val up: Boolean) : PhoneEvent
    /** A small live camera picture, when the person lets the phone show it on the TV (null: turned off). */
    class Picture(val image: ImageBitmap?) : PhoneEvent
}

private val JSON_TYPE = "application/json".toMediaType()

/**
 * Talks to the Steady companion server: a WebSocket for pairing and live pose points, and two
 * stateless coaching calls. The server stores nothing about the household.
 */
class CompanionClient(var baseUrl: String, private val onEvent: (PhoneEvent) -> Unit) {
    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build()
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
                    // decoded here, off the main thread; a bad picture is simply skipped
                    "img" -> post(PhoneEvent.Picture(m.optString("d").takeIf { it.isNotEmpty() && it != "null" }?.let { d ->
                        runCatching { Base64.decode(d, Base64.DEFAULT).let { b -> BitmapFactory.decodeByteArray(b, 0, b.size) }?.asImageBitmap() }.getOrNull() ?: return
                    }))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = retry()
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = retry()
        })
    }

    /** Point at a different companion (Settings). Drops the pairing code; the new server issues one. */
    fun switchServer(url: String) {
        close()
        baseUrl = url.trimEnd('/')
        code = null
        connect()
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
        ws = null
    }

    // ------------------------------------------------------------------ stateless coaching

    private suspend fun postJson(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + path).post(body.toString().toRequestBody(JSON_TYPE)).build()
        http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            JSONObject(r.body!!.string())
        }
    }

    /** Two warm sentences that open today's session, or null when the coach model is unavailable. */
    suspend fun coachIntro(p: Person, plan: Plan): String? = runCatching {
        val body = JSONObject().put("name", p.name).put("goal", p.goal).put("reasons", JSONArray(plan.reasons))
            .put("plan", JSONArray(plan.items.map { JSONObject().put("id", it.id.name).put("target", it.target) }))
        postJson("/api/coach/intro", body).optString("intro").ifBlank { null }
    }.getOrNull()

    /** The coach's words about a finished session, or null when the model is unavailable. */
    suspend fun coachSummary(p: Person, today: SessionRecord, h: Household): CoachNote? = runCatching {
        val previous = p.history.dropLast(1)
        val body = JSONObject()
            .put("profile", JSONObject().put("name", p.name).put("age", p.age).put("sex", if (p.female) "female" else "male").put("goal", p.goal))
            .put("today", HouseholdJson.sessionJson(today))
            .put("previousSessions", JSONArray(previous.takeLast(3).map { HouseholdJson.sessionJson(it) }))
            .put("family", JSONObject().put("name", h.familyName.ifBlank { "the family" }).put("relation", h.familyRelation.ifBlank { "family member" }))
        if (today.chairStand != null) {
            previous.lastOrNull { it.chairStand != null }?.chairStand?.let { body.put("previousChairStand", it) }
            ChairStandNorms.threshold(p.age, p.female)?.let { body.put("chairStandNorm", it) }
        }
        val c = postJson("/api/coach/summary", body)
        if (c.optString("source") != "llm") null
        else CoachNote(c.getString("spoken"), c.getString("summary"), c.optString("next"), c.optString("family"), "llm")
    }.getOrNull()
}

/**
 * Real push notifications to the family through ntfy (https://ntfy.sh), an open-source push service.
 * The family subscribes to the household's secret topic in the ntfy app or browser; no account needed.
 */
class FamilyNotifier(private val server: String = "https://ntfy.sh") {
    private val http = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    suspend fun send(topic: String, title: String, message: String, urgent: Boolean, tags: List<String>, clickUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("topic", topic).put("title", title).put("message", message)
                .put("priority", if (urgent) 5 else 3).put("tags", JSONArray(tags))
            clickUrl?.let { body.put("click", it) }
            http.newCall(Request.Builder().url(server).post(body.toString().toRequestBody(JSON_TYPE)).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
