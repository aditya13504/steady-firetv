package com.steady.tv.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.steady.tv.AppController
import com.steady.tv.pose.Catalog
import com.steady.tv.pose.ChairStandNorms
import java.time.LocalTime
import kotlinx.coroutines.delay

private fun greeting(): String = when (LocalTime.now().hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun HomeScreen(app: AppController) {
    val house = app.household
    val profile = app.selected
    val plan = app.plan
    val start = remember { FocusRequester() }
    val scroll = rememberScrollState()
    LaunchedEffect(plan != null) {
        if (plan != null) {
            runCatching { start.requestFocus() }
            delay(80)
            scroll.scrollTo(0)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 48.dp, vertical = 27.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("${greeting()}, ${profile?.name ?: ""}", style = T.display)
                val reason = house?.likelyReason?.takeIf { it.isNotBlank() && house.likelyId == profile?.id }
                Text(reason?.let { "$it, so I picked them first." } ?: profile?.goal ?: "", style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val p = app.pairing
            when {
                app.offline -> Pill("Companion offline: demo person only", C.coral)
                p.phoneConnected -> Pill("Phone camera linked", C.sage)
                else -> Pill("No phone linked yet", C.muted)
            }
        }

        if (house != null && house.profiles.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Who's exercising?", style = T.small)
                for (p in house.profiles) {
                    Focusable({ app.select(p) }, shape = RoundedCornerShape(999.dp), selected = p.id == profile?.id) { focused ->
                        Text("${p.name}, ${p.age}", style = T.label.copy(color = if (focused) C.sunInk else C.cream), modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(296.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Panel(Modifier.weight(1.45f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today's session", style = T.small)
                    Text(plan?.let { "${it.minutes} minutes, ${it.items.size} exercises" } ?: "Planning…", style = T.title)
                    Text(
                        plan?.items?.joinToString("  ›  ") { Catalog.info(it.id).title } ?: "",
                        style = T.small.copy(color = C.cream), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(plan?.why ?: "", style = T.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvButton("Start session", { app.begin() }, Modifier.focusRequester(start), primary = true)
                        if (!app.pairing.phoneConnected) TvButton("Demo person", { app.begin(); app.startDemo() })
                    }
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${profile?.name ?: ""}'s progress", style = T.small)
                    val hist = profile?.history.orEmpty()
                    val sts = hist.mapNotNull { it.sitToStand }
                    val bal = hist.map { it.balanceSec }.filter { it > 0 }
                    ProgressLine("Sit to stand", sts.lastOrNull()?.let { "$it" } ?: "–", sts.firstOrNull()?.let { "from $it" } ?: "", sts)
                    ProgressLine("Balance hold", bal.lastOrNull()?.let { "$it s" } ?: "–", bal.firstOrNull()?.let { "from $it s" } ?: "", bal)
                    val chair = hist.lastOrNull { it.chairStand != null }?.chairStand
                    val norm = profile?.let { ChairStandNorms.threshold(it.age, it.female) }
                    if (chair != null && norm != null) {
                        Text(
                            "Chair stand check: $chair. Usual at ${profile.age}: $norm or more.",
                            style = T.small.copy(color = if (chair < norm) C.sun else C.sage)
                        )
                    }
                }
            }
        }

        Text("Exercises", style = T.small)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(150.dp).padding(bottom = 4.dp)) {
            items(Catalog.all) { info ->
                var focused by remember { mutableStateOf(false) }
                Focusable({ app.begin(info.id) }, Modifier.width(330.dp).fillMaxHeight().onFocusChanged { focused = it.isFocused }) { f ->
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoachFigure(info.id, Modifier.size(110.dp), playing = focused)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(info.title, style = T.label.copy(color = if (f) C.sunInk else C.cream))
                            Text(info.benefit, style = T.small.copy(color = if (f) C.sunInk else C.muted, fontSize = T.small.fontSize * 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(label: String, now: String, from: String, values: List<Int>) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = T.small)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(now, style = T.title)
                Text(from, style = T.small, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        Box(Modifier.width(150.dp).height(48.dp)) { Sparkline(values, Modifier.fillMaxSize()) }
    }
}
