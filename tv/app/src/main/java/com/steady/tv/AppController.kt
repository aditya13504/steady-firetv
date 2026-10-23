package com.steady.tv

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.steady.tv.data.Coach
import com.steady.tv.data.CoachNote
import com.steady.tv.data.DemoHousehold
import com.steady.tv.data.FamilyNote
import com.steady.tv.data.Household
import com.steady.tv.data.HouseholdStore
import com.steady.tv.data.Person
import com.steady.tv.data.Plan
import com.steady.tv.data.PlanItem
import com.steady.tv.data.ResultRecord
import com.steady.tv.data.SessionRecord
import com.steady.tv.data.Topics
import com.steady.tv.net.CompanionClient
import com.steady.tv.net.FamilyNotifier
import com.steady.tv.net.PhoneEvent
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
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Calendar

enum class Screen { SPLASH, WELCOME, SETUP, MAIN, PAIR, SESSION, SUMMARY }
enum class Tab(val label: String) { HOME("Home"), EXERCISES("Exercises"), PROGRESS("Progress"), FAMILY("Family"), SETTINGS("Settings") }
enum class Stage { INTRO, ACTIVE, REST, DONE }

data class Pairing(val code: String? = null, val phoneUrl: String? = null, val localUrl: String? = null, val phoneConnected: Boolean = false, val serverUp: Boolean = false) {
    /** Public base address of the companion, as phones see it. */
    val publicBase: String? get() = phoneUrl?.substringBefore("/p/")
}

data class AlertUi(val kind: String, val startedAt: Long, val notified: String? = null)

data class ExerciseResult(val info: ExerciseInfo, val reps: Int, val target: Int, val quality: Int?, val bestHoldMs: Long, val cues: Map<String, Int>, val skipped: Boolean)

data class SummaryUi(
    val person: Person,
    val results: List<ExerciseResult>,
    val minutes: Int,
    val chairStand: Int?,
    val chairStandThreshold: Int?,
    val previousChairStand: Int?,
    val stoppedEarly: Boolean,
    val coach: CoachNote? = null,
    val sharedWith: String? = null
)

/** One run through a plan. Compose reads these fields directly. */
class SessionRun(val person: Person, val items: List<PlanItem>, val demo: Boolean, val intro: String) {
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
    /** Bumps on every counted rep; drives the "pop" animation. */
    var repTick by mutableIntStateOf(0)
    /** Bumps when an exercise is completed; drives the celebration. */
    var doneTick by mutableIntStateOf(0)
    val results = mutableListOf<ExerciseResult>()
    val startedAt = SystemClock.elapsedRealtime()
    val info: ExerciseInfo get() = Catalog.info(items[index].id)
    val nextInfo: ExerciseInfo? get() = items.getOrNull(index + 1)?.let { Catalog.info(it.id) }
}

class AppController(
    private val scope: CoroutineScope,
    private val speech: Speech,
    private val store: HouseholdStore,
    serverUrl: String,
    /** Pins the household clock (demos and recordings); null means the real time. */
    var clockHour: Int? = null
) {
    var screen by mutableStateOf(Screen.SPLASH)
    var tab by mutableStateOf(Tab.HOME)
    var household by mutableStateOf<Household?>(null)
    var selectedId by mutableStateOf<String?>(null)
    var plan by mutableStateOf<Plan?>(null)
    var intro by mutableStateOf<String?>(null)
    var pairing by mutableStateOf(Pairing())
    var session by mutableStateOf<SessionRun?>(null)
    var summary by mutableStateOf<SummaryUi?>(null)
    var alert by mutableStateOf<AlertUi?>(null)
    var serverUrl by mutableStateOf(serverUrl)
    var voiceOn by mutableStateOf(true)
    var toast by mutableStateOf<String?>(null)

    val selected: Person? get() = household?.person(selectedId ?: "")

    private val smoother = Smoother()
    private val fallCheck = FallCheck()
    private var ticker: Job? = null
    private var lastStateSent = 0L
    private val companion = CompanionClient(serverUrl, ::onPhoneEvent)
    private val notifier = FamilyNotifier()

    fun start() {
        household = store.load()
        companion.connect()
        household?.let { pickLikely(it) }
        scope.launch {
            delay(SPLASH_MS)
            screen = if (household == null) Screen.WELCOME else Screen.MAIN
        }
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

    // ------------------------------------------------------------------ household

    fun hourNow(): Double = clockHour?.toDouble() ?: Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) + it.get(Calendar.MINUTE) / 60.0 }

    fun greeting(): String = when (hourNow().toInt()) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    fun likelyReason(): String? {
        val h = household ?: return null
        val (p, reason) = Coach.likely(h, hourNow()) ?: return null
        return reason.takeIf { p.id == selectedId && it.isNotBlank() }
    }

    private fun pickLikely(h: Household) {
        val keep = selectedId?.takeIf { h.person(it) != null }
        selectedId = keep ?: Coach.likely(h, hourNow())?.first?.id
        refreshPlan()
    }

    private fun save(h: Household) {
        household = h
        runCatching { store.save(h) }
    }

    fun select(p: Person) {
        if (selectedId == p.id) return
        selectedId = p.id
        refreshPlan()
    }

    private fun refreshPlan() {
        val p = selected ?: run {
            plan = null
            return
        }
        val pl = Coach.plan(p)
        plan = pl
        intro = null
        scope.launch {
            val text = companion.coachIntro(p, pl)
            if (selectedId == p.id) intro = text
        }
    }

    fun exploreDemo() {
        save(DemoHousehold.create())
        pickLikely(household!!)
        tab = Tab.HOME
        screen = Screen.MAIN
    }

    fun startSetup() {
        screen = Screen.SETUP
    }

    /** First-run setup and "Add a person" in Settings. */
    fun addPerson(name: String, age: Int, female: Boolean, goal: String, familyName: String?, familyRelation: String?) {
        val person = Person(DemoHousehold.newPersonId(), name.trim(), age, female, goal.trim())
        val h = household?.takeIf { !it.demo }
        val next = if (h == null) {
            Household(
                name = "${person.name}'s home", familyName = familyName?.trim().orEmpty(), familyRelation = familyRelation.orEmpty(),
                familyTopic = Topics.newTopic(), people = listOf(person)
            )
        } else {
            h.copy(
                people = h.people + person,
                familyName = familyName?.trim()?.takeIf { it.isNotEmpty() } ?: h.familyName,
                familyRelation = familyRelation?.takeIf { it.isNotEmpty() } ?: h.familyRelation
            )
        }
        save(next)
        selectedId = person.id
        refreshPlan()
        tab = Tab.HOME
        screen = Screen.MAIN
        flash("${person.name} is set up. The first session includes a strength check, so Steady can track progress.")
    }

    fun removePerson(p: Person) {
        val h = household ?: return
        val left = h.people.filter { it.id != p.id }
        if (left.isEmpty()) {
            resetAll()
            return
        }
        save(h.copy(people = left))
        pickLikely(household!!)
    }

    fun resetAll() {
        store.clear()
        household = null
        selectedId = null
        plan = null
        screen = Screen.WELCOME
    }

    fun setServer(url: String) {
        serverUrl = url
        pairing = Pairing()
        companion.switchServer(url)
        onServerChanged?.invoke(url)
    }

    var onServerChanged: ((String) -> Unit)? = null

    fun toggleVoice() {
        voiceOn = !voiceOn
        speech.muted = !voiceOn
    }

    private fun flash(text: String) {
        toast = text
        scope.launch {
            delay(4_500)
            if (toast == text) toast = null
        }
    }

    // ------------------------------------------------------------------ family

    /** Web page where the family subscribes to push alerts for this household. */
    fun familyUrl(): String? {
        val h = household ?: return null
        val base = pairing.publicBase ?: return null
        return "$base/f/${h.familyTopic}"
    }

    private fun notifyFamily(kind: String, title: String, message: String, urgent: Boolean, tags: List<String>) {
        val h = household ?: return
        save(h.copy(sent = h.sent + FamilyNote(System.currentTimeMillis(), kind, message)))
        scope.launch {
            val ok = notifier.send(h.familyTopic, title, message, urgent, tags, familyUrl())
            if (!ok) flash("Couldn't reach the notification service. Check the internet connection.")
        }
    }

    fun sendTestAlert() {
        val h = household ?: return
        notifyFamily("test", "Steady is connected", "This is a test from ${h.name}. You'll get session summaries here, and an urgent alert if a fall is detected.", false, listOf("white_check_mark"))
        flash("Test sent to ${h.familyName.ifBlank { "the family" }}'s phone.")
    }

    // ------------------------------------------------------------------ starting a session

    private var pendingItems: List<PlanItem>? = null

    /** Start today's plan, or a single exercise. Goes through pairing unless a phone is linked. */
    fun begin(single: ExerciseId? = null) {
        val p = selected ?: return
        val pl = plan ?: return
        pendingItems = single?.let { listOf(PlanItem(it, pl.items.firstOrNull { i -> i.id == it }?.target ?: Coach.startingTarget(it, p.age))) } ?: pl.items
        if (pairing.phoneConnected) startSession(p, pendingItems!!, demo = false) else screen = Screen.PAIR
    }

    fun startDemo() {
        val p = selected ?: return
        startSession(p, pendingItems ?: plan?.items ?: return, demo = true)
    }

    /** Launch shortcut for demos: `--es demo maggie [--es exercise CHAIR_STAND_TEST]` (loads the demo household if needed). */
    fun autoDemo(personId: String, exercise: ExerciseId?) {
        scope.launch {
            delay(SPLASH_MS + 200)
            if (household?.person(personId) == null) exploreDemo()
            val p = household?.person(personId) ?: return@launch
            select(p)
            begin(exercise)
            startDemo()
        }
    }

    private fun startSession(p: Person, items: List<PlanItem>, demo: Boolean) {
        val run = SessionRun(p, items, demo, if (items.size > 1) intro ?: "" else "")
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
        val opener = if (first && run.intro.isNotBlank()) "${run.intro} " else ""
        speech.say("$opener${run.info.title}. ${run.info.how}")
    }

    private fun enterActive(run: SessionRun) {
        run.stage = Stage.ACTIVE
        run.exerciseStartedAt = now()
        run.demoPerson = if (run.demo) DemoPerson(run.info.id, seed = run.index + 3) else null
        smoother.reset()
        speech.say(if (run.info.kind == Kind.TIMED_TEST) "Arms across your chest. Sit down, and the clock starts." else "Let's go.", interrupt = false)
    }

    private fun record(run: SessionRun, skipped: Boolean) {
        val st = run.tracker.state
        run.results.add(ExerciseResult(run.info, st.reps, run.tracker.target, run.tracker.quality(), st.bestHoldMs, run.tracker.cueCounts.toMap(), skipped))
    }

    private fun finishExercise(run: SessionRun, skipped: Boolean = false) {
        record(run, skipped)
        if (!skipped) run.doneTick++
        if (run.index >= run.items.lastIndex) {
            finishSession(run, stoppedEarly = false)
            return
        }
        run.stage = Stage.REST
        run.stageUntil = now() + 7_000
        val st = run.tracker.state
        val praise = when {
            skipped -> "Okay, moving on."
            run.info.kind == Kind.TIMED_TEST -> "${st.reps} stands. Well done."
            else -> "Nice work. Have a sip of water."
        }
        speech.say("$praise Next up, ${run.nextInfo?.title}.")
    }

    private fun finishSession(run: SessionRun, stoppedEarly: Boolean) {
        run.stage = Stage.DONE
        val h = household ?: return
        val minutes = ((now() - run.startedAt) / 60_000).toInt().coerceAtLeast(1)
        val chair = run.results.firstOrNull { it.info.id == ExerciseId.CHAIR_STAND_TEST && !it.skipped }?.reps
        val p = run.person
        val record = SessionRecord(
            at = System.currentTimeMillis(), durationSec = minutes * 60,
            results = run.results.map { ResultRecord(it.info.id, it.reps, it.target, it.quality, it.bestHoldMs, it.cues, it.skipped) },
            chairStand = chair, chairStandBelow = chair?.let { ChairStandNorms.belowAverage(it, p.age, p.female) }, stoppedEarly = stoppedEarly
        )
        val previousChair = p.history.lastOrNull { it.chairStand != null }?.chairStand
        val updated = p.copy(history = p.history + record)
        save(h.copy(people = h.people.map { if (it.id == p.id) updated else it }))
        val s = SummaryUi(updated, run.results.toList(), minutes, chair, ChairStandNorms.threshold(p.age, p.female), previousChair, stoppedEarly)
        summary = s
        screen = Screen.SUMMARY
        speech.say(if (stoppedEarly) "Let's stop there for today." else "That's the whole session. Well done, ${p.name}.")
        scope.launch {
            val note = withTimeoutOrNull(15_000) { companion.coachSummary(updated, record, h) } ?: Coach.rulesNote(updated, record, h.familyName)
            val shared = h.hasFamily
            summary = s.copy(coach = note, sharedWith = if (shared) h.familyName else null)
            // keep the coach's words with the session
            household?.let { cur -> save(cur.copy(people = cur.people.map { if (it.id == p.id) it.copy(history = it.history.dropLast(1) + record.copy(coach = note.summary)) else it })) }
            speech.say(note.spoken, interrupt = false)
            if (shared) notifyFamily("summary", "${p.name} exercised today", note.family, false, listOf("muscle"))
        }
        refreshPlan()
    }

    fun endSessionEarly() {
        val run = session ?: return
        if (run.stage == Stage.DONE) return
        if (run.stage == Stage.ACTIVE && run.tracker.state.reps > 0) record(run, skipped = true)
        finishSession(run, stoppedEarly = true)
    }

    fun backToHome() {
        session = null
        summary = null
        alert = null
        screen = if (household == null) Screen.WELCOME else Screen.MAIN
        speech.stop()
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
                val who = household?.familyName?.ifBlank { null } ?: "your family"
                alert = a.copy(notified = who)
                notifyFamily("alert", "Possible fall: ${run.person.name}", "${run.person.name} went down during exercise and hasn't answered on the TV. Please call now.", true, listOf("rotating_light", "sos"))
                speech.say("I've let $who know. Press OK on the remote when you're able, or call for help.")
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
        if (tracker.repEvent) run.repTick++
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
        val family = household?.familyName?.ifBlank { null } ?: "your family"
        if (kind == "fall") {
            speech.say("${run.person.name}, are you OK? Say I'm OK on your phone, or press OK on the remote.")
        } else {
            speech.say("Let's stop here. Sit down and rest. I'm letting $family know.")
            alert = alert?.copy(notified = family)
            notifyFamily("alert", "${run.person.name} stopped exercising", "${run.person.name} stopped a session and said they don't feel well. Please check in.", true, listOf("warning"))
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
            endSessionEarly()
            return
        }
        if (a.notified != null && run != null) {
            notifyFamily("resolved", "${run.person.name} is OK", "${run.person.name} answered \"I'm OK\" on the TV.", false, listOf("white_check_mark"))
        }
        speech.say("Thank you. Take a moment. Press OK when you're ready to carry on.")
    }

    /** Demo person only: act out a fall so the safety flow can be seen without anyone falling. */
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
            is PhoneEvent.Link -> pairing = pairing.copy(serverUp = e.up, phoneConnected = if (e.up) pairing.phoneConnected else false)
            is PhoneEvent.Code -> {
                pairing = pairing.copy(code = e.code, phoneUrl = e.phoneUrl, localUrl = e.localUrl, phoneConnected = e.phoneConnected, serverUp = true)
                if (intro == null) refreshPlan()
            }
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
        const val SPLASH_MS = 2_200L
    }
}
