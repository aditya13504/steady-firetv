package com.steady.tv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.steady.tv.AppController
import com.steady.tv.BuildConfig
import com.steady.tv.Tab
import com.steady.tv.data.Coach
import com.steady.tv.data.Health
import com.steady.tv.data.Person
import com.steady.tv.pose.Catalog
import com.steady.tv.pose.Category
import com.steady.tv.pose.Kind
import com.steady.tv.pose.ChairStandNorms
import com.steady.tv.pose.ExerciseId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(app: AppController) {
    Box(Modifier.fillMaxSize().background(C.bg)) {
        AmbientLight(Modifier.fillMaxSize(), strength = 0.14f)
        Column(Modifier.fillMaxSize()) {
            TopNav(app.tab, { app.tab = it }, Modifier.padding(start = 48.dp, end = 48.dp, top = 22.dp)) {
                val p = app.pairing
                when {
                    !p.serverUp -> Pill("Offline", C.red)
                    p.phoneConnected -> Pill("Phone linked", C.green, pulse = true)
                    else -> Pill("No phone yet", C.muted)
                }
            }
            AnimatedContent(
                app.tab, label = "tab",
                transitionSpec = { (fadeIn(tween(260)) + slideInVertically(tween(320, easing = M.emphasized)) { it / 18 }) togetherWith fadeOut(tween(160)) }
            ) { tab ->
                when (tab) {
                    Tab.HOME -> HomeTab(app)
                    Tab.EXERCISES -> ExercisesTab(app)
                    Tab.PROGRESS -> ProgressTab(app)
                    Tab.FAMILY -> FamilyTab(app)
                    Tab.SETTINGS -> SettingsTab(app)
                }
            }
        }
        Toast(app.toast)
    }
}

// ------------------------------------------------------------------------------------------- home

@Composable
private fun HomeTab(app: AppController) {
    val h = app.household ?: return
    val p = app.selected
    val plan = app.plan
    val start = remember { FocusRequester() }
    val scroll = rememberScrollState()
    LaunchedEffect(p?.id) {
        runCatching { start.requestFocus() }
        // Focusing the button would scroll the hero up; the greeting should stay in view.
        kotlinx.coroutines.delay(60)
        scroll.scrollTo(0)
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 48.dp).padding(top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // hero
        Box(Modifier.fillMaxWidth().height(318.dp)) {
            Box(Modifier.align(Alignment.CenterEnd).width(430.dp).fillMaxHeight()) {
                CoachFigure(plan?.items?.getOrNull(1)?.id ?: ExerciseId.SIT_TO_STAND, Modifier.fillMaxSize())
            }
            Column(Modifier.fillMaxWidth(0.66f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (h.demo) Text("Demo household", style = T.small.copy(color = C.orange), modifier = Modifier.appear(0, p?.id ?: ""))
                Text("${app.greeting()}, ${p?.name ?: ""}", style = T.hero, maxLines = 1, modifier = Modifier.appear(1, p?.id ?: ""))
                app.likelyReason()?.let { Text("$it, so I picked them first.", style = T.small, modifier = Modifier.appear(2, p?.id ?: "")) }
                if (plan != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.appear(3, p?.id ?: "")) {
                        Text("Today: ${plan.minutes} minutes, ${plan.items.size} exercises", style = T.heading)
                        if (plan.items.any { it.id == ExerciseId.CHAIR_STAND_TEST }) Badge("Strength check today")
                    }
                    Text(app.intro ?: plan.why, style = T.body.copy(color = C.muted), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.appear(4, p?.id ?: ""))
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.appear(5, p?.id ?: "")) {
                    TvButton("Start session", { app.begin() }, Modifier.focusRequester(start), primary = true, icon = "▶")
                    if (!app.pairing.phoneConnected) TvButton("Demo person", { app.begin(); app.startDemo() })
                }
            }
        }
        if (h.people.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Who's exercising?", style = T.small)
                for (person in h.people) PersonChip(person, person.id == app.selectedId) { app.select(person) }
            }
        }
        Text("Today's plan", style = T.title.copy(fontSize = 24.sp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.height(176.dp)) {
            itemsIndexed(plan?.items.orEmpty()) { i, item ->
                ExerciseCard(item.id, "${item.target} ${if (item.id == ExerciseId.CHAIR_STAND_TEST) "seconds" else Catalog.info(item.id).unit}", i, Modifier.width(300.dp)) { app.begin(item.id) }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(text, style = T.small.copy(color = C.onOrange, fontSize = 15.sp), modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.orange).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
private fun PersonChip(p: Person, selected: Boolean, onClick: () -> Unit) {
    Focusable(onClick, shape = RoundedCornerShape(999.dp), idle = C.squid, focusedColor = C.white, selected = selected) { focused ->
        Row(Modifier.padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(if (selected) C.orange else C.squidHi), contentAlignment = Alignment.Center) {
                Text(p.name.take(1), style = T.label.copy(color = if (selected) C.onOrange else C.white))
            }
            Text("${p.name}, ${p.age}", style = T.label.copy(color = if (focused) C.onOrange else C.white))
        }
    }
}

@Composable
private fun ExerciseCard(id: ExerciseId, detail: String, index: Int, modifier: Modifier, blockedBy: String? = null, onClick: () -> Unit) {
    val info = Catalog.info(id)
    var focused by remember { mutableStateOf(false) }
    Focusable(onClick, modifier.fillMaxHeight().appear(index).onFocusChanged { focused = it.isFocused }, idle = C.squid, focusedColor = C.squidHi) { f ->
        val dim = if (blockedBy != null && !f) 0.4f else 1f
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 118.dp).alpha(dim).clip(RoundedCornerShape(12.dp)).background(C.ink), contentAlignment = Alignment.Center) {
                CoachFigure(id, Modifier.fillMaxSize(), playing = focused)
                if (info.sit) Text("Seated", style = T.small.copy(fontSize = 12.sp, color = C.blue), modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(info.title, style = T.label.copy(fontSize = 18.sp, lineHeight = 22.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(dim))
                if (blockedBy != null) Text("⊘  Not advised: ${blockedBy.lowercase()}", style = T.small.copy(color = Color(0xFFFF6B81), fontSize = 14.sp), maxLines = 2)
                else Text(detail, style = T.small.copy(color = C.orange, fontSize = 16.sp))
                if (f) Text(info.benefit, style = T.small.copy(fontSize = 14.sp, lineHeight = 18.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// -------------------------------------------------------------------------------------- exercises

/** Fire TV style rows: today's plan first, then one row per kind of exercise. Moves ruled out by health are dimmed with the reason. */
@Composable
private fun ExercisesTab(app: AppController) {
    val p = app.selected ?: return
    val planIds = app.plan?.items?.map { it.id }.orEmpty()
    fun detail(id: ExerciseId): String {
        val info = Catalog.info(id)
        val target = app.plan?.items?.firstOrNull { it.id == id }?.target ?: Coach.startingTarget(id, p.age, p.conditions)
        return when (info.kind) {
            Kind.TIMED_TEST -> "30 seconds"
            Kind.HOLD -> "$target × ${info.holdSeconds} s"
            Kind.REPS -> "$target ${info.unit}"
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp).padding(top = 14.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.appear(0)) {
            Text("Exercise library", style = T.display)
            Badge("${Catalog.all.size} moves")
        }
        Text(
            if (p.conditions.isEmpty()) "Pick any move to practise on its own. Targets are set for ${p.name}."
            else "Tuned for ${p.name}: planning around ${p.conditions.joinToString(", ") { it.label.lowercase() }}. Dimmed moves aren't advised.",
            style = T.small, modifier = Modifier.appear(1)
        )
        LibraryRow("In today's plan", planIds, app, p, 2, ::detail)
        for ((i, cat) in Category.entries.withIndex()) {
            LibraryRow(cat.title, Catalog.all.filter { it.category == cat }.map { it.id }, app, p, 3 + i, ::detail)
        }
    }
}

@Composable
private fun LibraryRow(title: String, ids: List<ExerciseId>, app: AppController, p: Person, index: Int, detail: (ExerciseId) -> String) {
    if (ids.isEmpty()) return
    Column(Modifier.appear(index), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = T.title.copy(fontSize = 23.sp))
            Text("${ids.size}", style = T.small.copy(color = C.orange))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(150.dp)) {
            itemsIndexed(ids) { i, id ->
                val blocked = Health.whyNot(p.conditions, id)
                ExerciseCard(id, detail(id), i, Modifier.width(290.dp), blockedBy = blocked?.label) {
                    if (blocked != null) app.flash("Steady leaves this out because of ${blocked.label.lowercase()}. Ask your physio if it's right for you.")
                    else app.begin(id)
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------- progress

@Composable
private fun ProgressTab(app: AppController) {
    val h = app.household ?: return
    val p = app.selected ?: return
    val df = remember { SimpleDateFormat("MMM d", Locale.US) }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${p.name}'s progress", style = T.display, modifier = Modifier.weight(1f))
            if (h.people.size > 1) for (person in h.people) PersonChip(person, person.id == p.id) { app.select(person) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Sessions", "${p.history.size}", 0)
            Stat("This week", Coach.streakDays(p).let { if (it == 1) "1 day" else "$it days" }, 1)
            Stat("Last session", p.history.lastOrNull()?.let { df.format(Date(it.at)) } ?: "None yet", 2)
        }
        if (p.history.isEmpty()) {
            Panel(Modifier.fillMaxWidth().height(260.dp)) {
                Text("Charts appear after the first session. Start one from Home.", style = T.heading.copy(color = C.muted), modifier = Modifier.align(Alignment.Center))
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(300.dp)) {
            val sts = p.history.mapNotNull { s -> s.results.firstOrNull { it.id == ExerciseId.SIT_TO_STAND && !it.skipped }?.reps }
            val bal = p.history.mapNotNull { s -> s.results.firstOrNull { it.id == ExerciseId.ONE_LEG_STAND }?.bestHoldMs?.takeIf { it > 0 }?.let { (it / 1000).toInt() } }
            val chair = p.history.mapNotNull { it.chairStand }
            ChartCard("Sit to stand", "reps", sts, null, C.orange, 3, Modifier.weight(1f), "Shows up after a sit to stand set.")
            ChartCard("One-leg balance", "seconds", bal, 10, C.blue, 4, Modifier.weight(1f), "Shows up after a one-leg balance hold.")
            ChartCard("Chair stand check", "stands", chair, ChairStandNorms.threshold(p.age, p.female), C.green, 5, Modifier.weight(1f), "Shows up after the 30-second chair stand check. It runs every two weeks.")
        }
    }
}

@Composable
private fun Stat(label: String, value: String, index: Int) {
    Panel(Modifier.width(240.dp).appear(index)) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
            Text(label, style = T.small)
            Text(value, style = T.title)
        }
    }
}

@Composable
private fun ChartCard(title: String, unit: String, values: List<Int>, line: Int?, color: Color, index: Int, modifier: Modifier, empty: String) {
    val draw = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { draw.animateTo(1f, tween(1_100, delayMillis = 120 * index, easing = M.emphasized)) }
    Panel(modifier.fillMaxHeight().appear(index)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = T.small)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(values.lastOrNull()?.toString() ?: "–", style = T.display)
                Text(unit, style = T.small, modifier = Modifier.padding(bottom = 6.dp))
                if (values.size >= 2) {
                    val d = values.last() - values.first()
                    Text(if (d >= 0) "+$d" else "$d", style = T.small.copy(color = if (d >= 0) C.green else C.orange), modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            if (values.isEmpty()) {
                // Nothing measured yet: say how to get a chart instead of showing an empty frame.
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                    Text(empty, style = T.body.copy(color = C.muted))
                }
                return@Column
            }
            Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                val lo = minOf(values.min(), line ?: values.min()).toFloat() - 1
                val hi = maxOf(values.max(), line ?: values.max()).toFloat() + 1
                fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
                val step = if (values.size > 1) size.width / (values.size - 1) else 0f
                val x0 = if (values.size == 1) size.width / 2 else 0f
                line?.let {
                    drawLine(C.muted.copy(alpha = 0.7f), Offset(0f, y(it.toFloat())), Offset(size.width, y(it.toFloat())), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                }
                val path = Path()
                values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v.toFloat())) else path.lineTo(i * step, y(v.toFloat())) }
                val m = PathMeasure().apply { setPath(path, false) }
                val part = Path()
                m.getSegment(0f, m.length * draw.value, part, true)
                drawPath(part, color, style = Stroke(width = 7f, cap = StrokeCap.Round))
                values.forEachIndexed { i, v ->
                    if (values.size == 1 || i * step <= size.width * draw.value + 1) drawCircle(color, radius = if (i == values.lastIndex) 11f else 7f, center = Offset(x0 + i * step, y(v.toFloat())))
                }
            }
            if (line != null) Text(if (unit == "stands") "Dashed line: usual for this age (CDC)" else "Dashed line: today's target", style = T.small.copy(fontSize = 14.sp))
        }
    }
}

// ----------------------------------------------------------------------------------------- family

@Composable
private fun FamilyTab(app: AppController) {
    val h = app.household ?: return
    val url = app.familyUrl()
    val df = remember { SimpleDateFormat("EEE h:mm a", Locale.US) }
    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Box(Modifier.size(320.dp).appear(0).clip(RoundedCornerShape(24.dp)).background(C.white).padding(22.dp), contentAlignment = Alignment.Center) {
            if (url != null) QrCode(url, Modifier.fillMaxSize()) else Text("Connecting…", style = T.heading.copy(color = C.bg))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (h.hasFamily) "Keep ${h.familyName} in the loop" else "Keep family in the loop", style = T.display, modifier = Modifier.appear(1))
            Text("Scan with a family member's phone. They get a push notification after each session, and an urgent alert if Steady thinks someone has fallen.", style = T.body.copy(color = C.muted), modifier = Modifier.appear(2))
            Text("Works with the free ntfy app or any browser. No account, and only people with this code can see the updates.", style = T.small, modifier = Modifier.appear(3))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.appear(4)) {
                TvButton("Send a test alert", { app.sendTestAlert() }, primary = true)
            }
            Spacer(Modifier.height(4.dp))
            Text("Recently sent", style = T.heading)
            val sent = h.sent.takeLast(4).reversed()
            if (sent.isEmpty()) Text("Nothing yet.", style = T.small)
            for ((i, n) in sent.withIndex()) {
                Row(Modifier.appear(5 + i), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(10.dp).clip(CircleShape).background(if (n.kind == "alert") C.red else if (n.kind == "resolved") C.green else C.blue))
                    Column {
                        Text(n.text, style = T.small.copy(color = C.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(df.format(Date(n.at)), style = T.small.copy(fontSize = 14.sp))
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------- settings

@Composable
private fun SettingsTab(app: AppController) {
    val h = app.household ?: return
    val cloud = BuildConfig.CLOUD_SERVER
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Settings", style = T.display, modifier = Modifier.appear(0))
        SettingRow("People", "${h.people.size} in ${h.name}", 1) {
            for (p in h.people) PersonChip(p, p.id == app.selectedId) { app.select(p) }
            TvButton("Add a person", { app.startSetup() }, icon = "+")
        }
        app.selected?.let { p ->
            Column(Modifier.fillMaxWidth().appear(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${p.name}'s health", style = T.heading)
                Text("Today's plan changes straight away: moves that aren't advised are left out and safer ones swapped in.", style = T.small)
                HealthChips(p.conditions) { app.setConditions(p.id, it) }
            }
        }
        SettingRow("Coach voice", if (app.voiceOn) "Spoken counts and cues are on" else "Muted", 2) {
            TvButton(if (app.voiceOn) "Turn off" else "Turn on", { app.toggleVoice() })
        }
        SettingRow("Steady server", app.serverUrl.removePrefix("https://").removePrefix("http://"), 3) {
            if (cloud.isNotBlank()) TvButton("Steady Cloud", { app.setServer(cloud) }, primary = app.serverUrl == cloud)
            TvButton("This computer", { app.setServer("http://10.0.2.2:8787") }, primary = app.serverUrl.contains("10.0.2.2"))
        }
        SettingRow(if (h.demo) "Demo household" else "Household data", if (h.demo) "You're exploring a demo. Set up your own anytime." else "Stored only on this TV.", 4) {
            if (h.demo) TvButton("Set up my household", { app.resetAll(); app.startSetup() }, primary = true)
            else TvButton("Explore the demo", { app.exploreDemo() })
            TvButton("Erase all data", { app.resetAll() })
        }
        Panel(Modifier.fillMaxWidth().appear(5)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("About Amazon Steady ${BuildConfig.VERSION_NAME}", style = T.heading)
                Text("Steady is a coaching and screening aid, not a medical device. It never gives dosing or diagnostic advice. If you feel dizzy, faint or have chest pain, stop and call 911.", style = T.small)
                Text("Pose tracking runs on your phone. Its camera picture is shown on this TV only while you allow it, and is never recorded or stored. Exercise history stays on this TV; coaching requests send only the numbers needed, and the server keeps nothing.", style = T.small)
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String, index: Int, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().appear(index), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(360.dp)) {
            Text(title, style = T.heading)
            Text(detail, style = T.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}
