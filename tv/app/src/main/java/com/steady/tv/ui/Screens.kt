package com.steady.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import android.os.SystemClock

// ---------------------------------------------------------------------------------------------- pairing

@Composable
fun PairScreen(app: AppController) {
    val p = app.pairing
    val demo = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { demo.requestFocus() } }
    Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(300.dp).clip(RoundedCornerShape(28.dp)).background(C.cream).padding(24.dp), contentAlignment = Alignment.Center) {
            if (p.phoneUrl != null) QrCode(p.phoneUrl, Modifier.fillMaxSize())
            else Text(if (p.serverUp) "Getting a code…" else "Companion not running", style = T.heading.copy(color = C.night), textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your phone is the camera", style = T.display.copy(fontSize = 40.sp))
            Step("1", "Scan the code with your phone's camera.")
            Step("2", "Stand the phone 2 to 3 meters away, so it sees you head to feet.")
            Step("3", "Video stays on your phone. Only joint positions reach the TV.")
            if (p.code != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Code", style = T.small)
                    Text(p.code, style = T.display.copy(color = C.sun, fontSize = 40.sp, letterSpacing = 6.sp))
                }
                Text(
                    "No QR scanner? Open ${p.phoneUrl?.substringBefore("/p/")?.removePrefix("http://")?.removePrefix("https://")} and enter the code.",
                    style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            when {
                p.phoneConnected -> Pill("Phone linked. Starting…", C.sage)
                p.serverUp -> Pill("Waiting for your phone", C.sun)
                else -> Pill("Can't reach the Steady companion. You can still try the demo person.", C.coral)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton("Try with the demo person", { app.startDemo() }, Modifier.focusRequester(demo), primary = true)
                TvButton("Back", { app.backToHome() })
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(C.surfaceHi), contentAlignment = Alignment.Center) { Text(n, style = T.label) }
        Text(text, style = T.body.copy(fontSize = 21.sp), modifier = Modifier.padding(top = 2.dp))
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
    val st = run.trackerState

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // top bar
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(info.title, style = T.display)
                    Text("Exercise ${run.index + 1} of ${run.items.size}, with ${run.profile.name}", style = T.small)
                }
                PlanDots(run)
                Spacer(Modifier.width(20.dp))
                Pill(if (run.demo) "Demo person" else if (run.lostSight) "Can't see you" else "Phone camera", if (run.demo) C.muted else if (run.lostSight) C.coral else C.sage)
            }

            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    CoachFigure(info.id, Modifier.fillMaxSize().padding(10.dp), playing = !run.paused)
                    Text("Coach", style = T.small, modifier = Modifier.padding(18.dp))
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    MirrorSkeleton(run.pose, Modifier.fillMaxSize().padding(10.dp), dim = run.lostSight)
                    Text("You", style = T.small, modifier = Modifier.padding(18.dp))
                    if (run.pose == null || run.lostSight) {
                        Text(
                            "Step back until I can see you from head to feet",
                            style = T.heading.copy(color = C.muted), textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(40.dp)
                        )
                    }
                }
                Counter(run, Modifier.width(250.dp).fillMaxHeight())
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.how, style = T.small.copy(color = C.cream), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(24.dp))
                KeyHints("OK" to if (run.paused) "Carry on" else "Pause", "▶" to "Next", "Back" to "Finish")
            }
        }

        // the cue: one instruction at a time, big, where the eye already is
        AnimatedVisibility(
            visible = run.cue != null && run.stage == Stage.ACTIVE,
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)
        ) {
            Text(
                run.cue ?: "", style = T.title.copy(color = C.sunInk),
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(C.sun).padding(horizontal = 34.dp, vertical = 14.dp)
            )
        }

        if (run.stage == Stage.INTRO || run.stage == Stage.REST) StageCard(run, now)
        if (run.paused && app.alert == null && run.stage == Stage.ACTIVE) Scrim {
            Text("Paused", style = T.display)
            Text("Press OK on the remote, or say “resume” on your phone.", style = T.body.copy(color = C.muted))
        }
        if (app.alert != null) AlertOverlay(app)
    }
}

@Composable
private fun PlanDots(run: SessionRun) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        run.items.indices.forEach { i ->
            val color = when {
                i < run.index -> C.sage
                i == run.index -> C.sun
                else -> C.surfaceHi
            }
            Box(Modifier.size(if (i == run.index) 16.dp else 12.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun Counter(run: SessionRun, modifier: Modifier) {
    val st = run.trackerState
    val info = run.info
    Panel(modifier, color = C.surface) {
        Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val (big, ring, under) = when (info.kind) {
                Kind.HOLD -> Triple("${st.holdMs / 1000}", st.progress, "seconds on one foot")
                Kind.TIMED_TEST -> Triple("${st.reps}", ((st.remainingMs ?: 30_000L) / 30_000f), "stands, ${((st.remainingMs ?: 30_000L) + 999) / 1000} s left")
                Kind.REPS -> Triple("${st.reps}", if (st.target > 0) st.reps / st.target.toFloat() else 0f, "of ${st.target} ${info.unit}")
            }
            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                ProgressRing(ring, Modifier.fillMaxSize(), color = if (info.kind == Kind.TIMED_TEST) C.coral else C.sun, width = 26f)
                Text(big, style = T.giant.copy(fontSize = 96.sp, lineHeight = 96.sp))
            }
            Spacer(Modifier.height(12.dp))
            Text(under, style = T.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            if (info.kind == Kind.HOLD) Text("Holds done: ${st.reps} of ${st.target}", style = T.small, textAlign = TextAlign.Center)
            Text(st.phase, style = T.small.copy(color = C.sun), textAlign = TextAlign.Center)
            st.steadiness?.let {
                Spacer(Modifier.height(10.dp))
                Text("Steadiness $it", style = T.small.copy(color = if (it >= 70) C.sage else C.cream))
            }
            if (info.kind == Kind.REPS) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(150.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.deep)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(st.progress).clip(RoundedCornerShape(5.dp)).background(C.cream))
                }
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
    Scrim {
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 80.dp)) {
            CoachFigure(nextInfo.id, Modifier.size(300.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (run.stage == Stage.REST) "Rest. Next up" else if (run.index == 0) "First up" else "Next up", style = T.small)
                Text(nextInfo.title, style = T.display)
                Text(nextInfo.how, style = T.body)
                Text(nextInfo.benefit, style = T.small)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        waitingForBody -> "Step back until I can see you from head to feet."
                        run.stage == Stage.REST -> "Starting in $secs. Press OK to start now."
                        else -> "Starting in $secs. Press OK to start now."
                    },
                    style = T.heading.copy(color = C.sun)
                )
            }
        }
    }
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.night.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

// ---------------------------------------------------------------------------------------------- alert

@Composable
fun AlertOverlay(app: AppController) {
    val a = app.alert ?: return
    val name = app.session?.profile?.name ?: ""
    val family = app.household?.familyName ?: "your family"
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(a) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    val left = ((AppController.ALERT_WAIT_MS - (now - a.startedAt)).coerceAtLeast(0) + 999) / 1000
    Box(Modifier.fillMaxSize().background(C.coral), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(64.dp)) {
            if (a.kind == "fall") {
                Text("$name, are you OK?", style = T.giant.copy(color = C.coralInk, fontSize = 84.sp, lineHeight = 90.sp), textAlign = TextAlign.Center)
                Text("Press OK on the remote, or say “I'm OK” to your phone.", style = T.title.copy(color = C.coralInk), textAlign = TextAlign.Center)
                Text(
                    when (a.notified) {
                        null -> "If I don't hear from you, I'll let $family know in $left seconds."
                        "…" -> "Letting $family know…"
                        else -> "I've let ${a.notified} know. Press OK when you're able."
                    },
                    style = T.heading.copy(color = C.coralInk), textAlign = TextAlign.Center
                )
            } else {
                Text("Let's stop here", style = T.giant.copy(color = C.coralInk, fontSize = 84.sp, lineHeight = 90.sp))
                Text("Sit down and rest. If you feel dizzy, have chest pain, or feel faint, call 911.", style = T.title.copy(color = C.coralInk), textAlign = TextAlign.Center)
                Text(if (a.notified != null) "I've let ${a.notified} know." else "Letting $family know…", style = T.heading.copy(color = C.coralInk))
                Text("Press OK to finish the session.", style = T.body.copy(color = C.coralInk))
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
    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 30.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (s.stoppedEarly) "Good effort, ${s.profile.name}" else "Well done, ${s.profile.name}", style = T.display)
            Text(if (s.minutes == 1) "1 minute today" else "${s.minutes} minutes today", style = T.small)
            // The strength check is the headline when there is one, so it leads.
            if (s.chairStand != null && s.chairStandThreshold != null) ChairStandCard(s.chairStand, s.chairStandThreshold, s.profile.age)
            Spacer(Modifier.height(4.dp))
            for (r in s.results.filter { it.info.kind != Kind.TIMED_TEST }) {
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    val met = r.reps >= r.target
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (met) C.sage else C.surfaceHi), contentAlignment = Alignment.Center) {
                        Text(if (met) "✓" else "–", style = T.label.copy(color = C.night, fontSize = 15.sp))
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
        Panel(Modifier.weight(1f).fillMaxHeight(), color = C.surface) {
            Column(Modifier.padding(30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("From your coach", style = T.small)
                val c = s.coach
                if (c == null) {
                    Text("Writing your summary…", style = T.title.copy(color = C.muted))
                } else {
                    Text(c.summary, style = T.heading.copy(lineHeight = 32.sp), maxLines = 6, overflow = TextOverflow.Ellipsis)
                    if (c.next.isNotBlank()) Text("Next time: ${c.next}", style = T.body.copy(color = C.sun), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (!s.coachFailed) Text("Shared with ${app.household?.familyName ?: "your family"}.", style = T.small)
                }
                Spacer(Modifier.weight(1f))
                TvButton("Done", { app.backToHome() }, Modifier.focusRequester(done), primary = true)
            }
        }
    }
}

@Composable
private fun ChairStandCard(stands: Int, threshold: Int, age: Int) {
    val ok = stands >= threshold
    Panel(Modifier.fillMaxWidth().padding(top = 6.dp), color = C.deep) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("30-second chair stand: $stands", style = T.heading)
            val max = (threshold * 2).coerceAtLeast(stands + 2)
            // Bar with a marker where the usual range for this age begins.
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(C.surface, cornerRadius = r)
                drawRoundRect(if (ok) C.sage else C.sun, size = androidx.compose.ui.geometry.Size(size.width * stands / max, size.height), cornerRadius = r)
                val x = size.width * threshold / max
                drawLine(C.cream, androidx.compose.ui.geometry.Offset(x, -6f), androidx.compose.ui.geometry.Offset(x, size.height + 6f), strokeWidth = 5f)
            }
            Text(
                if (ok) "In the usual range for age $age ($threshold or more), by the CDC STEADI guide."
                else "Below the usual range for age $age ($threshold or more). Practising sit to stands helps; share this with your doctor.",
                style = T.small.copy(color = C.cream)
            )
        }
    }
}
