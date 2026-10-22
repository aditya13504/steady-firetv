package com.steady.tv

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.steady.tv.net.CoachNote
import com.steady.tv.net.CompanionClient
import com.steady.tv.net.HistoryPoint
import com.steady.tv.net.Household
import com.steady.tv.net.PhoneEvent
import com.steady.tv.net.Plan
import com.steady.tv.net.PlanItem
import com.steady.tv.net.Profile
import com.steady.tv.pose.Catalog
import com.steady.tv.pose.ChairStandNorms
import com.steady.tv.pose.DemoPerson
import com.steady.tv.pose.ExerciseId
import com.steady.tv.pose.ExerciseInfo
import com.steady.tv.pose.FallCheck
import com.steady.tv.pose.Kind
import com.steady.tv.pose.Lm
import com.steady.tv.pose.PoseFrame
import com.steady.tv.pose.Smoother
import com.steady.tv.pose.Tracker
import com.steady.tv.pose.TrackerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class Screen { HOME, PAIR, SESSION, SUMMARY }
enum class Stage { INTRO, ACTIVE, REST, DONE }

data class Pairing(val code: String? = null, val phoneUrl: String? = null, val localUrl: String? = null, val phoneConnected: Boolean = false, val serverUp: Boolean = false)

data class AlertUi(val kind: String, val startedAt: Long, val notified: String? = null)

data class ExerciseResult(val info: ExerciseInfo, val reps: Int, val target: Int, val quality: Int?, val bestHoldMs: Long, val cues: Map<String, Int>, val skipped: Boolean)

data class SummaryUi(
    val profile: Profile,
    val results: List<ExerciseResult>,
    val minutes: Int,
    val chairStand: Int?,
    val chairStandThreshold: Int?,
    val stoppedEarly: Boolean,
    val coach: CoachNote? = null,
    val coachFailed: Boolean = false
)

/** One run through a plan. Compose reads these fields directly. */
class SessionRun(val profile: Profile, val items: List<PlanItem>, val demo: Boolean, val intro: String) {
    var index by mutableIntStateOf(0)
    var stage by mutableStateOf(Stage.INTRO)
    var stageUntil = 0L
    var tracker: Tracker = Tracker.create(items[0].id, items[0].target)
    var trackerState by mutableStateOf(TrackerState(target = items[0].target))
    var cue by mutableStateOf<String?>(null)
    var cueAt = 0L
    var paused by mutableStateOf(false)
    var pose by mutableStateOf<PoseFrame?>(null)
    var lastPoseAt = 0L
    var lostSight by mutableStateOf(false)
    var exerciseStartedAt = 0L
    var demoPerson: DemoPerson? = null
    val results = mutableListOf<ExerciseResult>()
    val startedAt = SystemClock.elapsedRealtime()
    val info: ExerciseInfo get() = Catalog.info(items[index].id)
    val nextInfo: ExerciseInfo? get() = items.getOrNull(index + 1)?.let { Catalog.info(it.id) }
}

class AppController(private val scope: CoroutineScope, private val speech: Speech, serverUrl: String) {
    var screen by mutableStateOf(Screen.HOME)
    var household by mutableStateOf<Household?>(null)
    var selected by mutableStateOf<Profile?>(null)
    var plan by mutableStateOf<Plan?>(null)
    var pairing by mutableStateOf(Pairing())
    var session by mutableStateOf<SessionRun?>(null)
    var summary by mutableStateOf<SummaryUi?>(null)
    var alert by mutableStateOf<AlertUi?>(null)
    var offline by mutableStateOf(false)

    private val smoother = Smoother()
    private val fallCheck = FallCheck()
    private var ticker: Job? = null
    private var lastStateSent = 0L
    private val companion = CompanionClient(serverUrl, ::onPhoneEvent)

    fun start() {
        companion.connect()
        refreshHousehold()
        ticker = scope.launch {
            while (isActive) {
                tick()
                delay(66)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        companion.close()
        speech.shutdown()
    }

    // ------------------------------------------------------------------ home data

    fun refreshHousehold() {
        scope.launch {
            val h = runCatching { companion.household() }.getOrNull()
            offline = h == null
            val house = h ?: OfflineHousehold.household
            household = house
            val keep = selected?.id
            selected = house.profiles.firstOrNull { it.id == keep } ?: house.profiles.firstOrNull { it.id == house.likelyId } ?: house.profiles.first()
            loadPlan()
        }
    }

    fun select(p: Profile) {
        if (selected?.id == p.id) return
        selected = p
        plan = null
        loadPlan()
    }

    private fun loadPlan() {
        val p = selected ?: return
        scope.launch {
            plan = runCatching { companion.plan(p.id) }.getOrNull() ?: OfflineHousehold.plan(p)
        }
    }

    // ------------------------------------------------------------------ starting

    /** Start today's plan, or a single exercise from the row. Goes through pairing unless a phone is linked. */
    fun begin(single: ExerciseId? = null) {
        val p = selected ?: return
        val items = single?.let { listOf(PlanItem(it, plan?.items?.firstOrNull { i -> i.id == it }?.target ?: Catalog.info(it).defaultTarget)) } ?: plan?.items ?: return
        pendingItems = items
        if (pairing.phoneConnected) startSession(p, items, demo = false) else screen = Screen.PAIR
    }

    private var pendingItems: List<PlanItem>? = null

    fun startDemo() {
        val p = selected ?: return
        startSession(p, pendingItems ?: plan?.items ?: return, demo = true)
    }

    private fun startSession(p: Profile, items: List<PlanItem>, demo: Boolean) {
        val intro = if (items.size > 1) plan?.intro ?: "" else ""
        val run = SessionRun(p, items, demo, intro)
        session = run
        summary = null
        smoother.reset()
        fallCheck.clear()
        screen = Screen.SESSION
        enterIntro(run, first = true)
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun enterIntro(run: SessionRun, first: Boolean) {
        run.stage = Stage.INTRO
        run.stageUntil = now() + if (first) 9_000 else 6_000
        run.tracker = Tracker.create(run.items[run.index].id, run.items[run.index].target)
        run.trackerState = run.tracker.state
        run.cue = null
        val info = run.info
        val opener = if (first && run.intro.isNotBlank()) "${run.intro} " else ""
        speech.say("$opener${info.title}. ${info.how}")
    }

    private fun enterActive(run: SessionRun) {
        run.stage = Stage.ACTIVE
        run.exerciseStartedAt = now()
        run.demoPerson = if (run.demo) DemoPerson(run.info.id, seed = run.index + 3) else null
        smoother.reset()
        speech.say(if (run.info.kind == Kind.TIMED_TEST) "Arms across your chest. Sit down, and the clock starts." else "Let's go.", interrupt = false)
    }

    private fun finishExercise(run: SessionRun, skipped: Boolean = false) {
        val st = run.tracker.state
        run.results.add(ExerciseResult(run.info, st.reps, run.tracker.target, run.tracker.quality(), st.bestHoldMs, run.tracker.cueCounts.toMap(), skipped))
        if (run.index >= run.items.lastIndex) {
            finishSession(run, stoppedEarly = false)
            return
        }
        run.stage = Stage.REST
        run.stageUntil = now() + 7_000
        val praise = when {
            skipped -> "Okay, moving on."
            run.info.kind == Kind.TIMED_TEST -> "${st.reps} stands. Well done."
            else -> "Nice work. Have a sip of water."
        }
        speech.say("$praise Next up, ${run.nextInfo?.title}.")
    }

    private fun finishSession(run: SessionRun, stoppedEarly: Boolean) {
        if (run.stage == Stage.ACTIVE && !stoppedEarly) Unit
        run.stage = Stage.DONE
        val minutes = ((now() - run.startedAt) / 60_000).toInt().coerceAtLeast(1)
        val chair = run.results.firstOrNull { it.info.id == ExerciseId.CHAIR_STAND_TEST && !it.skipped }?.reps
        val threshold = ChairStandNorms.threshold(run.profile.age, run.profile.female)
        val s = SummaryUi(run.profile, run.results.toList(), minutes, chair, threshold, stoppedEarly)
        summary = s
        screen = Screen.SUMMARY
        speech.say(if (stoppedEarly) "Let's stop there for today." else "That's the whole session. Well done, ${run.profile.name}.")
        scope.launch {
            val note = runCatching { companion.saveSession(run.profile.id, sessionJson(run, minutes, chair, stoppedEarly)) }.getOrNull()
            summary = s.copy(coach = note ?: OfflineHousehold.note(s), coachFailed = note == null)
            (note ?: summary?.coach)?.let { speech.say(it.spoken, interrupt = false) }
        }
    }

    private fun sessionJson(run: SessionRun, minutes: Int, chair: Int?, stoppedEarly: Boolean): JSONObject {
        val results = JSONArray()
        for (r in run.results) {
            results.put(
                JSONObject().put("id", r.info.id.name).put("reps", r.reps).put("target", r.target).put("quality", r.quality ?: JSONObject.NULL)
                    .put("bestHoldMs", r.bestHoldMs).put("cues", JSONObject(r.cues as Map<*, *>)).put("skipped", r.skipped)
            )
        }
        return JSONObject().put("at", Instant.now().toString()).put("durationSec", minutes * 60).put("results", results).put("stoppedEarly", stoppedEarly).apply {
            if (chair != null) put("chairStand", JSONObject().put("stands", chair).put("belowAverage", ChairStandNorms.belowAverage(chair, run.profile.age, run.profile.female) ?: JSONObject.NULL))
        }
    }

    fun endSessionEarly() {
        val run = session ?: return
        if (run.stage == Stage.DONE) return
        if (run.stage == Stage.ACTIVE && run.tracker.state.reps > 0) finishExerciseRecordOnly(run)
        finishSession(run, stoppedEarly = true)
    }

    private fun finishExerciseRecordOnly(run: SessionRun) {
        val st = run.tracker.state
        run.results.add(ExerciseResult(run.info, st.reps, run.tracker.target, run.tracker.quality(), st.bestHoldMs, run.tracker.cueCounts.toMap(), skipped = true))
    }

    fun backToHome() {
        session = null
        summary = null
        alert = null
        screen = Screen.HOME
        speech.stop()
        refreshHousehold()
    }

    // ------------------------------------------------------------------ the loop

    private fun tick() {
        val run = session ?: return
        val t = now()
        when (run.stage) {
            Stage.INTRO -> if (t >= run.stageUntil && !run.paused && alert == null && (run.demo || bodyInView(run))) enterActive(run)
            Stage.REST -> if (t >= run.stageUntil && !run.paused && alert == null) {
                run.index += 1
                enterIntro(run, first = false)
            }
            else -> Unit
        }
        if (run.demo && !run.paused && alert == null && run.stage != Stage.DONE) {
            val person = run.demoPerson ?: DemoPerson(ExerciseId.SIT_TO_STAND, sloppyEvery = 0).also { run.demoPerson = it }
            val f = person.frame(t - run.exerciseStartedAt)
            ingest(PoseFrame(f.pts, t, f.aspect))
        }
        if (!run.demo && run.stage == Stage.ACTIVE) run.lostSight = t - run.lastPoseAt > 2_500
        if (run.cue != null && t - run.cueAt > 4_000) run.cue = null

        alert?.let { a ->
            if (a.notified == null && a.kind == "fall" && t - a.startedAt > ALERT_WAIT_MS) {
                alert = a.copy(notified = "…")
                scope.launch {
                    val who = runCatching { companion.alert(run.profile.id, "possible_fall") }.getOrDefault(household?.familyName ?: "your family")
                    alert = alert?.copy(notified = who)
                    speech.say("I've let $who know. Press OK on the remote when you're able, or call for help.")
                }
            }
        }
        if (t - lastStateSent > 500) {
            lastStateSent = t
            companion.sendState(phoneState(run))
        }
    }

    private fun bodyInView(run: SessionRun): Boolean {
        val f = run.pose ?: return false
        return now() - run.lastPoseAt < 1_500 && f.visible(Lm.CORE + Lm.LEGS)
    }

    private fun phoneState(run: SessionRun): JSONObject {
        val s = run.trackerState
        return when {
            alert != null -> JSONObject().put("status", "alert")
            run.stage == Stage.DONE -> JSONObject().put("title", "Session complete").put("detail", "Well done!")
            else -> JSONObject().put("exercise", run.info.title).put("reps", s.reps).put("target", s.target).put("unit", run.info.unit)
                .put("status", if (run.paused) "paused" else "running")
        }
    }

    private fun ingest(raw: PoseFrame) {
        val run = session ?: return
        val f = smoother.apply(raw)
        run.pose = f
        run.lastPoseAt = now()
        if (run.stage != Stage.DONE && alert == null && fallCheck.feed(f)) {
            raiseAlert("fall")
            return
        }
        if (run.stage != Stage.ACTIVE || run.paused || alert != null) return
        val tracker = run.tracker
        tracker.feed(f)
        run.trackerState = tracker.state
        val cue = tracker.newCues.firstOrNull()
        if (cue != null) {
            run.cue = cue.text
            run.cueAt = now()
            speech.say(cue.text)
        } else if (tracker.repEvent && tracker.info.kind != Kind.HOLD) {
            speech.say(spokenCount(tracker.state.reps))
        }
        if (tracker.state.done) finishExercise(run)
    }

    // ------------------------------------------------------------------ alerts

    private fun raiseAlert(kind: String) {
        val run = session ?: return
        run.paused = true
        alert = AlertUi(kind, now())
        if (kind == "fall") {
            speech.say("${run.profile.name}, are you OK? Say I'm OK on your phone, or press OK on the remote.")
        } else {
            speech.say("Let's stop here. Sit down and rest. I'm letting ${household?.familyName ?: "your family"} know.")
            scope.launch {
                val who = runCatching { companion.alert(run.profile.id, "unwell") }.getOrDefault(household?.familyName ?: "your family")
                alert = alert?.copy(notified = who)
            }
        }
    }

    fun resolveAlert() {
        val a = alert ?: return
        val run = session
        alert = null
        fallCheck.clear()
        smoother.reset()
        run?.demoPerson?.recover()
        if (a.kind == "unwell") {
            run?.let { endSessionEarly() }
            return
        }
        if (a.notified != null && run != null) scope.launch { runCatching { companion.alert(run.profile.id, "resolved") } }
        speech.say("Thank you. Take a moment. Press OK when you're ready to carry on.")
    }

    /** Demo mode only: act out a fall so the safety flow can be seen without anyone falling. */
    fun simulateFall() {
        session?.takeIf { it.demo && it.stage == Stage.ACTIVE }?.demoPerson?.falling = true
    }

    // ------------------------------------------------------------------ input: remote, phone buttons, phone voice

    fun onSelect() {
        if (alert != null) {
            resolveAlert()
            return
        }
        val run = session ?: return
        when (run.stage) {
            Stage.INTRO, Stage.REST -> run.stageUntil = 0
            Stage.ACTIVE -> togglePause(run)
            Stage.DONE -> Unit
        }
    }

    fun onNext() {
        val run = session ?: return
        if (alert != null) return
        when (run.stage) {
            Stage.ACTIVE -> finishExercise(run, skipped = run.tracker.state.reps < run.tracker.target)
            Stage.INTRO, Stage.REST -> run.stageUntil = 0
            Stage.DONE -> Unit
        }
    }

    private fun togglePause(run: SessionRun) {
        run.paused = !run.paused
        speech.say(if (run.paused) "Paused. Take your time." else "Here we go.")
    }

    private fun onPhoneEvent(e: PhoneEvent) {
        when (e) {
            is PhoneEvent.Link -> {
                pairing = pairing.copy(serverUp = e.up, phoneConnected = if (e.up) pairing.phoneConnected else false)
                if (e.up && offline && screen == Screen.HOME) refreshHousehold()
            }
            is PhoneEvent.Code -> pairing = pairing.copy(code = e.code, phoneUrl = e.phoneUrl, localUrl = e.localUrl, phoneConnected = e.phoneConnected, serverUp = true)
            is PhoneEvent.PhoneLink -> {
                pairing = pairing.copy(phoneConnected = e.connected)
                if (e.connected && screen == Screen.PAIR) {
                    speech.say("Phone connected.")
                    scope.launch {
                        delay(1_500)
                        val p = selected
                        val items = pendingItems ?: plan?.items
                        if (screen == Screen.PAIR && p != null && items != null) startSession(p, items, demo = false)
                    }
                }
            }
            is PhoneEvent.Pose -> {
                val run = session ?: return
                if (run.demo) return
                val pts = e.pts ?: return
                ingest(PoseFrame.fromImage(pts, now(), e.aspect))
            }
            is PhoneEvent.Command -> onCommand(e.cmd)
        }
    }

    private fun onCommand(cmd: String) {
        val run = session
        when (cmd) {
            "ok" -> if (alert != null) resolveAlert()
            "unwell" -> if (run != null && run.stage != Stage.DONE) raiseAlert("unwell")
            "pause" -> if (run != null && !run.paused) togglePause(run)
            "resume" -> if (alert != null) resolveAlert() else if (run != null && run.paused) togglePause(run) else if (run != null) run.stageUntil = 0
            "next" -> onNext()
            "easier" -> run?.let {
                it.tracker.easier()
                it.trackerState = it.tracker.state
                speech.say("Okay. Let's make it ${it.tracker.target}.")
            }
            "harder" -> run?.let {
                it.tracker.harder()
                it.trackerState = it.tracker.state
                speech.say("You've got this. ${it.tracker.target} it is.")
            }
            "repeat" -> run?.let { speech.say(it.info.how) }
        }
    }

    companion object {
        const val ALERT_WAIT_MS = 15_000L
    }
}

/** Built-in household so the TV app is fully usable without the companion server (demo person only). */
object OfflineHousehold {
    private val maggie = Profile(
        "maggie", "Maggie", 76, true, "Walk to the farmers market on Saturdays without worrying about falling.", 6, 3,
        listOf(
            HistoryPoint("", 7, 4, 8), HistoryPoint("", 8, 5, null), HistoryPoint("", 9, 6, null),
            HistoryPoint("", 10, 7, null), HistoryPoint("", 10, 8, null), HistoryPoint("", 10, 8, null)
        )
    )
    val household = Household("The Lee household", "Daniel", "son", "maggie", "", listOf(maggie), llm = false)

    fun plan(p: Profile) = Plan(
        items = listOf(
            PlanItem(ExerciseId.MARCH, 20), PlanItem(ExerciseId.SIT_TO_STAND, 10), PlanItem(ExerciseId.HEEL_RAISE, 10),
            PlanItem(ExerciseId.SIDE_LEG_RAISE, 10), PlanItem(ExerciseId.ONE_LEG_STAND, 2)
        ),
        why = "A balanced session of strength and balance.", intro = "Good to see you, ${p.name}.", minutes = 15
    )

    fun note(s: SummaryUi) = CoachNote(
        spoken = "Well done, ${s.profile.name}.",
        summary = s.results.filter { !it.skipped }.joinToString(" ") { "${it.info.title}: ${it.reps}." },
        next = "We add a little more where you found it easy.",
        family = "", source = "offline"
    )
}
