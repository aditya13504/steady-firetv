package com.steady.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * The one focus treatment used everywhere: the focused thing turns marigold and grows slightly,
 * so it is obvious from across the room which button OK will press.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    idle: Color = C.surface,
    selected: Boolean = false,
    content: @Composable (focused: Boolean) -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val bg by animateColorAsState(if (focused) C.sun else idle, label = "bg")
    val s by animateFloatAsState(if (focused) 1.04f else 1f, label = "scale")
    Box(
        modifier
            .scale(s)
            .clip(shape)
            .background(bg)
            .then(if (selected && !focused) Modifier.border(3.dp, C.sun, shape) else Modifier)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) { content(focused) }
}

@Composable
fun TvButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    Focusable(onClick, modifier, shape = RoundedCornerShape(999.dp), idle = if (primary) C.surfaceHi else C.surface) { focused ->
        Text(text, style = T.label.copy(color = if (focused) C.sunInk else C.cream, lineHeight = 28.sp), maxLines = 1, modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp))
    }
}

@Composable
fun Pill(text: String, dot: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(C.deep).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Text(text, style = T.small.copy(color = C.cream))
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = C.deep, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(color)) { content() }
}

/** Remote-control hints along the bottom edge, written as what each key does. */
@Composable
fun KeyHints(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((key, action) in hints) KeyHint(key, action)
    }
}

@Composable
private fun RowScope.KeyHint(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(key, style = T.small.copy(color = C.night), modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.muted).padding(horizontal = 10.dp, vertical = 2.dp))
        Text(action, style = T.small)
    }
}
