package com.steady.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.steady.tv.Tab

/**
 * Fire TV focus: the focused item springs forward, gains a crisp white outline and a soft blue
 * glow, so it is obvious from across the room which thing OK will press.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    idle: Color = C.squid,
    focusedColor: Color = idle,
    selected: Boolean = false,
    scaleTo: Float = 1.06f,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val bg by animateColorAsState(if (focused) focusedColor else idle, label = "bg")
    val s by animateFloatAsState(if (focused) scaleTo else 1f, M.focusSpring(), label = "scale")
    val glow by animateDpAsState(if (focused) 22.dp else 0.dp, label = "glow")
    val borderColor = if (focused) C.white else if (selected) C.orange else Color.Transparent
    Box(
        modifier
            .scale(s)
            .shadow(glow, shape, ambientColor = C.blue, spotColor = C.blue)
            .clip(shape)
            .background(bg)
            .border(if (focused || selected) 3.dp else 0.dp, borderColor, shape)
            .onFocusChanged { if (it.isFocused) onFocus?.invoke() }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) { content(focused) }
}

/** Primary actions are Amazon orange; a focused button turns white, like Fire TV's own buttons. */
@Composable
fun TvButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, icon: String? = null) {
    Focusable(
        onClick, modifier, shape = RoundedCornerShape(999.dp),
        idle = if (primary) C.orange else C.squidHi, focusedColor = C.white, scaleTo = 1.08f
    ) { focused ->
        val color = if (focused || primary) C.onOrange else C.white
        Row(Modifier.padding(horizontal = 30.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Text(icon, style = T.label.copy(color = color))
            Text(text, style = T.label.copy(color = color), maxLines = 1)
        }
    }
}

@Composable
fun Pill(text: String, dot: Color, modifier: Modifier = Modifier, pulse: Boolean = false) {
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(C.ink.copy(alpha = 0.85f)).border(1.dp, C.line, RoundedCornerShape(999.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (pulse) PulseDot(dot) else Box(Modifier.size(11.dp).clip(CircleShape).background(dot))
        Text(text, style = T.small.copy(color = C.text), maxLines = 1)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = C.ink, radius: Dp = 22.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(color).border(1.dp, C.line.copy(alpha = 0.6f), RoundedCornerShape(radius))) { content() }
}

/** Remote-control hints along the bottom edge, written as what each key does. */
@Composable
fun KeyHints(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((key, action) in hints) KeyHint(key, action)
    }
}

@Composable
private fun RowScope.KeyHint(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(key, style = T.small.copy(color = C.white), modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(C.squidHi).padding(horizontal = 10.dp, vertical = 2.dp))
        Text(action, style = T.small)
    }
}

/** Steady wordmark: the balancing figure and the name. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LogoMark(Modifier.size(34.dp), progress = 1f)
        Text("steady", style = T.title.copy(fontSize = 26.sp, letterSpacing = (-0.5).sp))
    }
}

/** Fire TV style top navigation: focusing a tab shows it; the current tab is underlined in orange. */
@Composable
fun TopNav(current: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier, right: @Composable RowScope.() -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Wordmark()
        Box(Modifier.width(36.dp))
        for (tab in Tab.entries) {
            val selected = tab == current
            Focusable(
                { onTab(tab) }, shape = RoundedCornerShape(12.dp), idle = Color.Transparent, focusedColor = C.white, scaleTo = 1.04f,
                onFocus = { onTab(tab) }
            ) { focused ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tab.label, style = T.nav.copy(color = if (focused) C.onOrange else if (selected) C.white else C.muted))
                    Box(Modifier.padding(top = 4.dp).width(if (selected && !focused) 26.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(C.orange))
                }
            }
        }
        Box(Modifier.weight(1f))
        right()
    }
}

/** A short message that slides up from the bottom and leaves on its own. */
@Composable
fun BoxScope.Toast(text: String?) {
    AnimatedVisibility(
        visible = text != null,
        enter = slideInVertically(M.popSpring()) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
    ) {
        Row(
            Modifier.shadow(18.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(C.white).padding(horizontal = 26.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(C.orange))
            Text(text ?: "", style = T.label.copy(color = C.onOrange))
        }
    }
}
