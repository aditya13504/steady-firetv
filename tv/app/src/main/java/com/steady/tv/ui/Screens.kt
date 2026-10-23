package com.steady.tv.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.steady.tv.AppController
import com.steady.tv.SessionRun
import com.steady.tv.Stage
import com.steady.tv.pose.Kind
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------- pairing

@Composable
fun PairScreen(app: AppController) {
    val p = app.pairing
    val demo = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { demo.requestFocus() } }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        AmbientLight(Modifier.fillMaxSize(), strength = 0.16f)
        Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(310.dp).appear(0).shadow(30.dp, RoundedCornerShape(26.dp), spotColor = C.orange).clip(RoundedCornerShape(26.dp)).background(C.white).padding(24.dp), contentAlignment = Alignment.Center) {
                if (p.phoneUrl != null) {
                    QrCode(p.phoneUrl, Modifier.fillMaxSize())
                    if (!p.phoneConnected) ScanLine(Modifier.fillMaxSize())
                    if (p.phoneConnected) CheckDraw(Modifier.size(140.dp), key = "linked")
                } else Text(if (p.serverUp) "Getting a code…" else "Steady server unreachable", style = T.heading.copy(color = C.bg), textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your phone is the camera", style = T.display, modifier = Modifier.appear(1))
                Step("1", "Scan the code with your phone's camera.", 2)
                Step("2", "Stand the phone 2 to 3 meters away, so it sees you head to feet.", 3)
                Step("3", "Video stays on your phone. Only joint positions reach the TV.", 4)
                if (p.code != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.appear(5)) {
                        Text("Code", style = T.small)
                        Text(p.code, style = T.display.copy(color = C.orange, letterSpacing = 8.sp))
                    }
                    Text(
                        "No QR scanner? Open ${p.publicBase?.removePrefix("http://")?.removePrefix("https://")} and enter the code.",
                        style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.appear(6)
                    )
                }
                Row(Modifier.appear(7)) {
                    when {
                        p.phoneConnected -> Pill("Phone linked. Starting…", C.green, pulse = true)
                        p.serverUp -> Pill("Waiting for your phone", C.orange, pulse = true)
                        else -> Pill("Can't reach the Steady server. You can still use the demo person.", C.red)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.appear(8)) {
                    TvButton("Use the demo person", { app.startDemo() }, Modifier.focusRequester(demo), primary = true)
                    TvButton("Back", { app.backToHome() })
                }
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String, index: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top, modifier = Modifier.appear(index)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(C.squidHi), contentAlignment = Alignment.Center) { Text(n, style = T.label) }
        Text(text, style = T.body, modifier = Modifier.padding(top = 1.dp))
    }
}

// ---------------------------------------------------------------------------------------------- session

@Composable
fun SessionScreen(app: AppController) {
    val run = app.session ?: return
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(run) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val info = run.info

    Box(Modifier.fillMaxSize().background(C.bg)) {
        AmbientLight(Modifier.fillMaxSize(), strength = 0.10f)
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(info.title, style = T.display)
                    Text("Exercise ${run.index + 1} of ${run.items.size}, with ${run.person.name}", style = T.small)
                }
                PlanProgress(run)
                Spacer(Modifier.width(20.dp))
                Pill(
                    if (run.demo) "Demo person" else if (run.lostSight) "Can't see you" else "Phone camera live",
                    if (run.demo) C.muted else if (run.lostSight) C.red else C.green, pulse = !run.demo && !run.lostSight
                )
            }

            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    CoachFigure(info.id, Modifier.fillMaxSize().padding(10.dp), playing = !run.paused)
                    Text("Coach", style = T.small, modifier = Modifier.padding(18.dp))
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    MirrorSkeleton(run.pose, Modifier.fillMaxSize().padding(10.dp), dim = run.lostSight)
                    Text("You", style = T.small.copy(color = C.blue), modifier = Modifier.padding(18.dp))
                    if (run.pose == null || run.lostSight) {
                        Text(
                            "Step back until I can see you from head to feet",
                            style = T.heading.copy(color = C.muted), textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(40.dp)
                        )
                    }
                }
                Counter(run, Modifier.width(260.dp).fillMaxHeight())
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.how, style = T.small.copy(color = C.text), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(24.dp))
                KeyHints("OK" to if (run.paused) "Carry on" else "Pause", "▶" to "Next", "Back" to "Finish")
            }
        }

        AnimatedVisibility(
            visible = run.cue != null && run.stage == Stage.ACTIVE,
            enter = fadeIn() + slideInVertically(M.popSpring()) { it } + scaleIn(initialScale = 0.9f), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        ) {
            Row(
                Modifier.shadow(24.dp, RoundedCornerShape(999.dp), spotColor = C.orange).clip(RoundedCornerShape(999.dp)).background(C.orange).padding(horizontal = 34.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(run.cue ?: "", style = T.title.copy(color = C.onOrange))
            }
        }

        Confetti(run.doneTick, Modifier.fillMaxSize())
        if (run.stage == Stage.INTRO || run.stage == Stage.REST) StageCard(run, now)
        if (run.paused && app.alert == null && run.stage == Stage.ACTIVE) Scrim {
            Text("Paused", style = T.hero)
            Text("Press OK on the remote, or say “resume” to your phone.", style = T.body.copy(color = C.muted))
        }
        if (app.alert != null) AlertOverlay(app)
    }
}

@Composable
private fun PlanProgress(run: SessionRun) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        run.items.indices.forEach { i ->
            val w by animateFloatAsState(if (i == run.index) 34f else 14f, M.focusSpring(), label = "seg")
            val color = when {
                i < run.index -> C.green
                i == run.index -> C.orange
                else -> C.squidHi
            }
            Box(Modifier.width(w.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
private fun Counter(run: SessionRun, modifier: Modifier) {
    val st = run.trackerState
    val info = run.info
    val (big, ringTarget, under) = when (info.kind) {
        Kind.HOLD -> Triple("${st.holdMs / 1000}", st.progress, "seconds on one foot")
        Kind.TIMED_TEST -> Triple("${st.reps}", ((st.remainingMs ?: 30_000L) / 30_000f), "stands, ${((st.remainingMs ?: 30_000L) + 999) / 1000} s left")
        Kind.REPS -> Triple("${st.reps}", if (st.target > 0) st.reps / st.target.toFloat() else 0f, "of ${st.target} ${info.unit}")
    }
    val ring by animateFloatAsState(ringTarget, tween(420, easing = M.emphasized), label = "ring")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(run.repTick) {
        if (run.repTick == 0) return@LaunchedEffect
        pop.snapTo(1.28f)
        pop.animateTo(1f, M.popSpring())
    }
    Panel(modifier, color = C.squid) {
        Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                ProgressRing(ring, Modifier.fillMaxSize(), color = if (info.kind == Kind.TIMED_TEST) C.blue else C.orange, track = C.ink, width = 24f)
                Burst(run.repTick, Modifier.fillMaxSize())
                Text(big, style = T.giant.copy(fontSize = 96.sp, lineHeight = 96.sp), modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
            }
            Spacer(Modifier.height(12.dp))
            Text(under, style = T.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            if (info.kind == Kind.HOLD) Text("Holds done: ${st.reps} of ${st.target}", style = T.small, textAlign = TextAlign.Center)
            Text(st.phase, style = T.small.copy(color = C.orange), textAlign = TextAlign.Center)
            st.steadiness?.let {
                Spacer(Modifier.height(8.dp))
                Text("Steadiness $it", style = T.small.copy(color = if (it >= 70) C.green else C.text))
            }
        }
    }
}

@Composable
private fun StageCard(run: SessionRun, now: Long) {
    val info = run.info
    val nextInfo = if (run.stage == Stage.REST) run.nextInfo ?: info else info
    val secs = ((run.stageUntil - now).coerceAtLeast(0) + 999) / 1000
    val waitingForBody = run.stage == Stage.INTRO && !run.demo && secs == 0L
    val total = if (run.stage == Stage.REST) 7f else if (run.index == 0) 9f else 6f
    val key = run.index * 10 + run.stage.ordinal
    Scrim {
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 80.dp)) {
            Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
                CoachFigure(nextInfo.id, Modifier.fillMaxSize())
                if (run.stage == Stage.REST) CheckDraw(Modifier.size(72.dp).align(Alignment.TopEnd), key = key)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (run.stage == Stage.REST) "Nice work. Up next" else if (run.index == 0) "First up" else "Up next", style = T.small.copy(color = C.orange), modifier = Modifier.appear(0, key))
                Text(nextInfo.title, style = T.hero, modifier = Modifier.appear(1, key))
                Text(nextInfo.how, style = T.body, modifier = Modifier.appear(2, key))
                Text(nextInfo.benefit, style = T.small, modifier = Modifier.appear(3, key))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!waitingForBody) {
                        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                            ProgressRing(secs / total, Modifier.fillMaxSize(), color = C.orange, track = C.squid, width = 8f)
                            Text("$secs", style = T.label)
                        }
                    }
                    Text(
                        if (waitingForBody) "Step back until I can see you from head to feet." else "Starting soon. Press OK to start now.",
                        style = T.heading.copy(color = C.white)
                    )
                }
            }
        }
    }
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.bg.copy(alpha = 0.95f)), contentAlignment = Alignment.Center) {
        AmbientLight(Modifier.fillMaxSize(), strength = 0.12f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

// ---------------------------------------------------------------------------------------------- alert

@Composable
fun AlertOverlay(app: AppController) {
    val a = app.alert ?: return
    val name = app.session?.person?.name ?: ""
    val family = app.household?.familyName?.ifBlank { null } ?: "your family"
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(a) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    val waited = (now - a.startedAt).coerceAtLeast(0)
    val left = ((AppController.ALERT_WAIT_MS - waited).coerceAtLeast(0) + 999) / 1000
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(C.red, C.redDeep), radius = 1600f)), contentAlignment = Alignment.Center) {
        PulseRings(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(64.dp)) {
            if (a.kind == "fall") {
                Text("$name, are you OK?", style = T.giant.copy(fontSize = 84.sp, lineHeight = 92.sp), textAlign = TextAlign.Center)
                Text("Press OK on the remote, or say “I'm OK” to your phone.", style = T.title.copy(color = C.white), textAlign = TextAlign.Center)
                if (a.notified == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            ProgressRing(1f - waited / AppController.ALERT_WAIT_MS.toFloat(), Modifier.fillMaxSize(), color = C.white, track = C.redDeep, width = 8f)
                            Text("$left", style = T.title)
                        }
                        Text("If I don't hear from you, I'll alert $family.", style = T.heading.copy(color = C.white))
                    }
                } else {
                    Text("Urgent alert sent to ${a.notified}'s phone. Press OK when you're able.", style = T.heading.copy(color = C.white), textAlign = TextAlign.Center)
                }
            } else {
                Text("Let's stop here", style = T.giant.copy(fontSize = 84.sp, lineHeight = 92.sp))
                Text("Sit down and rest. If you feel dizzy, faint or have chest pain, call 911.", style = T.title.copy(color = C.white), textAlign = TextAlign.Center)
                Text("I've let ${a.notified ?: family} know. Press OK to finish the session.", style = T.heading.copy(color = C.white))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- summary

@Composable
fun SummaryScreen(app: AppController) {
    val s = app.summary ?: return
    val done = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { done.requestFocus() } }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        AmbientLight(Modifier.fillMaxSize(), strength = 0.18f)
        Confetti(if (s.stoppedEarly) 0 else 1, Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 30.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (s.stoppedEarly) "Good effort, ${s.person.name}" else "Well done, ${s.person.name}", style = T.display, maxLines = 1, modifier = Modifier.appear(0))
                Text(if (s.minutes == 1) "1 minute today" else "${s.minutes} minutes today", style = T.small, modifier = Modifier.appear(1))
                if (s.chairStand != null && s.chairStandThreshold != null) ChairStandCard(s.chairStand, s.chairStandThreshold, s.previousChairStand, s.person.age)
                Spacer(Modifier.height(4.dp))
                for ((i, r) in s.results.filter { it.info.kind != Kind.TIMED_TEST }.withIndex()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).appear(3 + i), verticalAlignment = Alignment.CenterVertically) {
                        val met = r.reps >= r.target
                        Box(Modifier.size(24.dp).clip(CircleShape).background(if (met) C.green else C.squidHi), contentAlignment = Alignment.Center) {
                            Text(if (met) "✓" else "–", style = T.label.copy(color = C.bg, fontSize = 15.sp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(r.info.title, style = T.body.copy(fontSize = 20.sp), modifier = Modifier.weight(1f), maxLines = 1)
                        val value = when (r.info.kind) {
                            Kind.HOLD -> "best ${r.bestHoldMs / 1000} s" + (r.quality?.let { ", steady $it" } ?: "")
                            else -> "${r.reps} of ${r.target}"
                        }
                        Text(value, style = T.body.copy(color = C.muted, fontSize = 20.sp), maxLines = 1)
                    }
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight().appear(2), color = C.squid) {
                Column(Modifier.padding(30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(C.orange))
                        Text("From your coach", style = T.small)
                    }
                    val c = s.coach
                    if (c == null) {
                        Text("Writing your summary…", style = T.title.copy(color = C.muted))
                    } else {
                        Text(c.summary, style = T.heading.copy(lineHeight = 32.sp), maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.appear(0, c))
                        if (c.next.isNotBlank()) Text("Next time: ${c.next}", style = T.body.copy(color = C.orange), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.appear(1, c))
                        s.sharedWith?.let { Text("Sent to $it's phone.", style = T.small, modifier = Modifier.appear(2, c)) }
                    }
                    Spacer(Modifier.weight(1f))
                    TvButton("Done", { app.backToHome() }, Modifier.focusRequester(done), primary = true)
                }
            }
        }
    }
}

@Composable
private fun ChairStandCard(stands: Int, threshold: Int, previous: Int?, age: Int) {
    val ok = stands >= threshold
    val shown = countUp(stands)
    val fill = remember { Animatable(0f) }
    LaunchedEffect(stands) { fill.animateTo(1f, tween(1_000, delayMillis = 200, easing = M.emphasized)) }
    Panel(Modifier.fillMaxWidth().padding(top = 6.dp).appear(2), color = C.squid) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("$shown", style = T.hero.copy(color = if (ok) C.green else C.orange))
                Column {
                    Text("stands in 30 seconds", style = T.heading, maxLines = 1)
                    previous?.let { Text(if (stands > it) "up from $it last time" else "last time: $it", style = T.small.copy(color = if (stands > it) C.green else C.muted)) }
                }
            }
            val max = (threshold * 2).coerceAtLeast(stands + 2)
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(C.ink, cornerRadius = r)
                drawRoundRect(if (ok) C.green else C.orange, size = Size(size.width * stands / max * fill.value, size.height), cornerRadius = r)
                val x = size.width * threshold / max
                drawLine(C.white, Offset(x, -6f), Offset(x, size.height + 6f), strokeWidth = 5f)
            }
            Text(
                if (ok) "In the usual range for age $age ($threshold or more), by the CDC STEADI guide."
                else "The usual range for age $age starts at $threshold (CDC STEADI). Sit to stands help; share this with your doctor.",
                style = T.small.copy(color = C.text)
            )
        }
    }
}
