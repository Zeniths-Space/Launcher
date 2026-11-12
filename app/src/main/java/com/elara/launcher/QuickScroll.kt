package com.elara.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quick scroll for an alphabetical list: a strip along the right edge. Touch it and slide to jump to
 * the apps under a letter; the letters show, with a bubble at the finger, only while the list
 * scrolls or the strip is touched, and fade a moment after. [initials] come from [initialsOf]; each
 * position is an item of [grid].
 */
@Composable
internal fun BoxScope.QuickScroll(initials: List<Pair<Char, Int>>, grid: LazyGridState, modifier: Modifier = Modifier) {
    if (initials.size < 2) return
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var touching by remember { mutableStateOf(false) }
    var picked by remember { mutableIntStateOf(-1) }
    var height by remember { mutableIntStateOf(0) }
    // The letter of the first app on screen, while the list scrolls by itself.
    val current by remember(initials) {
        derivedStateOf { initials.indexOfLast { it.second <= grid.firstVisibleItemIndex }.coerceAtLeast(0) }
    }
    val active = if (touching && picked >= 0) picked else current
    val busy = touching || grid.isScrollInProgress
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy) shown = true else { delay(1200); shown = false }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "quick scroll")
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(28.dp)
            .onSizeChanged { height = it.height }
            .pointerInput(initials) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun at(y: Float) {
                        val i = (y / size.height * initials.size).toInt().coerceIn(0, initials.size - 1)
                        if (i != picked) {
                            picked = i
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { grid.scrollToItem(initials[i].second) }
                        }
                    }
                    touching = true
                    at(down.position.y)
                    drag(down.id) { change -> change.consume(); at(change.position.y) }
                    touching = false
                    picked = -1
                }
            },
    ) {
        Column(Modifier.fillMaxHeight().alpha(alpha).padding(end = 6.dp).align(Alignment.CenterEnd)) {
            initials.forEachIndexed { i, (letter, _) ->
                Box(Modifier.weight(1f).width(22.dp), contentAlignment = Alignment.Center) {
                    Text(
                        letter.toString(),
                        fontSize = 11.sp,
                        fontWeight = if (i == active) FontWeight.Bold else FontWeight.Medium,
                        color = if (i == active) cs.primary else OnWallpaper,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (touching && picked >= 0) {
            val bubble = 64.dp
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-(44.dp.roundToPx()), ((picked + 0.5f) / initials.size * height - bubble.toPx() / 2).toInt().coerceIn(0, (height - bubble.roundToPx()).coerceAtLeast(0))) }
                    .requiredSize(bubble)
                    .clip(CircleShape)
                    .background(cs.primary),
                contentAlignment = Alignment.Center,
            ) { Text(initials[picked].first.toString(), fontSize = 30.sp, fontWeight = FontWeight.Medium, color = cs.onPrimary) }
        }
    }
}
