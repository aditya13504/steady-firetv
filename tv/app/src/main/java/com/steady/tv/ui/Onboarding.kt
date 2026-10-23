package com.steady.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.steady.tv.AppController

@Composable
fun SplashScreen() {
    val draw = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(1_300, easing = M.emphasized))
        word.animateTo(1f, tween(450, easing = M.decel))
    }
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
        AmbientLight(Modifier.fillMaxSize(), strength = 0.16f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(Modifier.size(180.dp), progress = draw.value)
            Text(
                "steady", style = T.hero.copy(fontSize = 64.sp),
                modifier = Modifier.graphicsLayer { alpha = word.value; translationY = (1 - word.value) * 24f }
            )
            Text(
                "Strength and balance, at home", style = T.heading.copy(color = C.muted),
                modifier = Modifier.graphicsLayer { alpha = word.value }
            )
        }
    }
}

@Composable
fun WelcomeScreen(app: AppController) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        AmbientLight(Modifier.fillMaxSize())
        Row(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Wordmark(Modifier.appear(0))
                Text("Stay steady on your feet.", style = T.hero, modifier = Modifier.appear(1))
                Text(
                    "A coach on your TV for the strength and balance exercises that prevent falls. Your phone is the camera. Your video never leaves it.",
                    style = T.body.copy(color = C.muted), modifier = Modifier.appear(2)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.appear(3)) {
                    TvButton("Set up my household", { app.startSetup() }, Modifier.focusRequester(first), primary = true)
                    TvButton("Explore a demo household", { app.exploreDemo() })
                }
                Text("Takes a minute. Everything is stored on this TV.", style = T.small, modifier = Modifier.appear(4))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CoachFigure(com.steady.tv.pose.ExerciseId.ONE_LEG_STAND, Modifier.size(420.dp).appear(2))
            }
        }
    }
}

private val RELATIONS = listOf("son", "daughter", "partner", "friend", "carer")

@Composable
fun SetupScreen(app: AppController) {
    val adding = app.household?.let { !it.demo } == true
    var name by remember { mutableStateOf("") }
    var age by remember { mutableIntStateOf(75) }
    var female by remember { mutableStateOf<Boolean?>(null) }
    var goal by remember { mutableStateOf("") }
    var family by remember { mutableStateOf(if (adding) "" else "") }
    var relation by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        AmbientLight(Modifier.fillMaxSize(), strength = 0.12f)
        Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 36.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.width(300.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Wordmark()
                Text(if (adding) "Add a person" else "Who will exercise?", style = T.display)
                Text("Steady uses age and sex only to compare the chair stand check with CDC norms.", style = T.small)
                Text("Everything stays on this TV.", style = T.small.copy(color = C.green))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Field("First name") { TvTextField(name, { name = it }, "Maggie", Modifier.focusRequester(nameFocus)) }
                Field("Age") { AgeStepper(age) { age = it.coerceIn(50, 105) } }
                Field("Sex, for the CDC norms") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Choice("Woman", female == true) { female = true }
                        Choice("Man", female == false) { female = false }
                    }
                }
                Field("What would they like to do with more confidence? (optional)") { TvTextField(goal, { goal = it }, "Walk to the market without worrying about falling") }
                if (!adding || app.household?.hasFamily != true) {
                    Field("Family member who gets alerts (optional)") { TvTextField(family, { family = it }, "Daniel") }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (r in RELATIONS) Choice(r.replaceFirstChar { it.uppercase() }, relation == r) { relation = r }
                    }
                }
                error?.let { Text(it, style = T.label.copy(color = C.orange)) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) {
                    TvButton("Save and continue", {
                        when {
                            name.isBlank() -> error = "Add a first name to continue."
                            female == null -> error = "Choose woman or man, so the strength check uses the right norms."
                            else -> app.addPerson(name, age, female!!, goal, family.ifBlank { null }, relation)
                        }
                    }, primary = true)
                    TvButton("Back", { app.backToHome() })
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = T.small)
        content()
    }
}

@Composable
fun TvTextField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    BasicTextField(
        value = value, onValueChange = { onChange(it.take(60)) }, singleLine = true, interactionSource = source,
        textStyle = T.heading.copy(color = C.white), cursorBrush = SolidColor(C.orange),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(0.92f),
        decorationBox = { inner ->
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).background(C.squid).border(if (focused) 3.dp else 1.dp, if (focused) C.white else C.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, style = T.heading.copy(color = C.muted.copy(alpha = 0.6f)))
                inner()
            }
        }
    )
}

@Composable
private fun AgeStepper(age: Int, onChange: (Int) -> Unit) {
    Focusable(
        {}, Modifier.onPreviewKeyEvent {
            if (it.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            when (it.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { onChange(age - 1); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { onChange(age + 1); true }
                else -> false
            }
        }, shape = RoundedCornerShape(14.dp), scaleTo = 1.03f
    ) { focused ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Text("◀", style = T.heading.copy(color = if (focused) C.orange else C.muted))
            Text("$age", style = T.display.copy(fontSize = 34.sp), modifier = Modifier.width(64.dp))
            Text("▶", style = T.heading.copy(color = if (focused) C.orange else C.muted))
            Text(if (focused) "Use left and right to change" else "", style = T.small)
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Focusable(onClick, shape = RoundedCornerShape(999.dp), idle = if (selected) C.orange else C.squid, focusedColor = C.white, scaleTo = 1.06f) { focused ->
        Text(label, style = T.label.copy(color = if (focused || selected) C.onOrange else C.white), modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
    }
}
