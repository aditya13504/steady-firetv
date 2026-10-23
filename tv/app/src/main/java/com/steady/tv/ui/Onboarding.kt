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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.steady.tv.AppController
import com.steady.tv.data.Condition

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
                "amazon", style = T.heading.copy(color = C.orange, fontSize = 28.sp, letterSpacing = 1.sp),
                modifier = Modifier.graphicsLayer { alpha = word.value; translationY = (1 - word.value) * 16f }
            )
            Text(
                "steady", style = T.hero.copy(fontSize = 64.sp, lineHeight = 66.sp),
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
            Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Wordmark(Modifier.appear(0))
                Text("Stay steady\non your feet.", style = T.hero, modifier = Modifier.appear(1))
                Text(
                    "A coach on your TV for the strength and balance exercises that prevent falls. Your phone is the camera, and nothing is ever recorded.",
                    style = T.body.copy(color = C.muted), modifier = Modifier.appear(2)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.appear(3)) {
                    TvButton("Set up my household", { app.startSetup() }, Modifier.focusRequester(first), primary = true)
                    TvButton("Try the demo", { app.exploreDemo() })
                }
                Text("Takes a minute. Everything is stored on this TV.", style = T.small, modifier = Modifier.appear(4))
            }
            Box(Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
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
    var conditions by remember { mutableStateOf(setOf<Condition>()) }
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
                Field("Anything Steady should plan around? Choose any that apply (optional)") {
                    HealthChips(conditions) { conditions = it }
                }
                Text("Steady leaves out moves that aren't advised for these, swaps in safer ones and starts gentler. Check with your doctor or physio before starting.", style = T.small.copy(fontSize = 16.sp))
                Field("What would they like to do with more confidence? (optional)") { TvTextField(goal, { goal = it }, "Walk to the market without worrying about falling") }
                if (!adding || app.household?.hasFamily != true) {
                    Field("Family member who gets alerts (optional)") { TvTextField(family, { family = it }, "Daniel") }
                    for (row in RELATIONS.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (r in row) Choice(r.replaceFirstChar { it.uppercase() }, relation == r) { relation = r }
                    }
                }
                error?.let { Text(it, style = T.label.copy(color = C.orange)) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) {
                    TvButton("Save and continue", {
                        when {
                            name.isBlank() -> error = "Add a first name to continue."
                            female == null -> error = "Choose woman or man, so the strength check uses the right norms."
                            else -> app.addPerson(name, age, female!!, goal, family.ifBlank { null }, relation, conditions)
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
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value, onValueChange = { onChange(it.take(60)) }, singleLine = true, interactionSource = source,
        textStyle = T.heading.copy(color = C.white), cursorBrush = SolidColor(C.orange),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { focus.moveFocus(FocusDirection.Down) }),
        // A text field keeps D-pad up/down to itself on TV; hand them back so the remote can move between fields.
        modifier = modifier.fillMaxWidth(0.92f).onPreviewKeyEvent {
            if (it.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            when (it.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> focus.moveFocus(FocusDirection.Down)
                KeyEvent.KEYCODE_DPAD_UP -> focus.moveFocus(FocusDirection.Up)
                else -> false
            }
        },
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

/** Multi-select health conditions, three to a row. */
@Composable
fun HealthChips(selected: Set<Condition>, onChange: (Set<Condition>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in Condition.entries.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (c in row) Choice(c.label, c in selected) { onChange(if (c in selected) selected - c else selected + c) }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    // A selected chip stays orange with a check even while focused, so the choice is visible under the focus ring.
    Focusable(onClick, shape = RoundedCornerShape(999.dp), idle = if (selected) C.orange else C.squid, focusedColor = if (selected) C.orange else C.white, scaleTo = 1.06f) { focused ->
        Text(if (selected) "✓  $label" else label, style = T.label.copy(color = if (focused || selected) C.onOrange else C.white), modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
    }
}
