package com.elara.launcher

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
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
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.abs
import androidx.compose.animation.core.animate
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Quick scroll for an alphabetical list. While the list scrolls, a thin scrollbar shows at the right
 * edge. Touch the edge and slide and the letters appear: the letter under the finger swells out with
 * a bubble beside it, and the list glides to that letter's first app. Both fade a moment after.
 * [initials] come from [initialsOf]; each position is an item of [grid], laid out in [columns] columns.
 */
@Composable
internal fun BoxScope.QuickScroll(initials: List<Pair<Char, Int>>, grid: LazyGridState, columns: Int, modifier: Modifier = Modifier) {
    if (initials.size < 2) return
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val cs = MaterialTheme.colorScheme
    val primary = cs.primary
    val onPrimary = cs.onPrimary
    val letterColor = OnWallpaper
    var touching by remember { mutableStateOf(false) }
    // Read only while drawing, so a drag redraws the strip without recomposing it.
    var fingerY by remember { mutableFloatStateOf(0f) }
    val swell = remember { Animatable(0f) }
    val busy = touching || grid.isScrollInProgress
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy) shown = true else { delay(1200); shown = false }
    }
    LaunchedEffect(touching) { swell.animateTo(if (touching) 1f else 0f, spring(dampingRatio = 1f, stiffness = 400f)) }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "quick scroll")
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(44.dp)
            .pointerInput(initials, columns) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val n = initials.size
                    val h = size.height.toFloat()
                    val info = grid.layoutInfo
                    val pitch = rowPitch(info)
                    val rows = (info.totalItemsCount + columns - 1) / columns
                    val viewport = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
                    val max = (rows * pitch - info.mainAxisItemSpacing - viewport).coerceAtLeast(0)
                    // Our own running position: the grid's lags a frame behind the touch events.
                    var pos = (grid.firstVisibleItemIndex / columns * pitch + grid.firstVisibleItemScrollOffset).toFloat()
                    var last = -1
                    var chase: Job? = null
                    fun at(y: Float) {
                        fingerY = y.coerceIn(0f, h)
                        val t = (y / h * n).coerceIn(0f, n - 0.001f)
                        val i = t.toInt()
                        if (i == last) return
                        last = i
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (pitch <= 0) return
                        // Glide to the first row of the letter, so its apps are what shows at the top.
                        val target = ((initials[i].second / columns) * pitch).coerceIn(0, max).toFloat()
                                                chase?.cancel()
                        chase = scope.launch {
                            animate(pos, target, animationSpec = spring(dampingRatio = 1f, stiffness = 500f)) { v, _ ->
                                grid.dispatchRawDelta(v - pos)
                                pos = v
                            }
                        }
                    }
                    touching = true
                    at(down.position.y)
                    drag(down.id) { change -> change.consume(); at(change.position.y) }
                    touching = false
                }
            }
            .drawBehind {
                val a = alpha
                if (a <= 0f) return@drawBehind
                val s = swell.value
                if (s < 0.01f) {
                    // Scrollbar: thumb size and place follow the scrolled share of the list.
                    val info = grid.layoutInfo
                    val pitch = rowPitch(info)
                    if (pitch <= 0) return@drawBehind
                    val content = ((info.totalItemsCount + columns - 1) / columns * pitch - info.mainAxisItemSpacing).toFloat()
                    val viewport = (info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding).toFloat()
                    if (content <= viewport) return@drawBehind
                    val thumb = (viewport / content * size.height).coerceAtLeast(48.dp.toPx())
                    val scrolled = grid.firstVisibleItemIndex / columns * pitch + grid.firstVisibleItemScrollOffset
                    val top = (scrolled / (content - viewport)).coerceIn(0f, 1f) * (size.height - thumb)
                    drawRoundRect(letterColor.copy(alpha = 0.6f * a), Offset(size.width - 7.dp.toPx(), top), Size(3.dp.toPx(), thumb), CornerRadius(1.5.dp.toPx()))
                    return@drawBehind
                }
                val n = initials.size
                val slot = size.height / n
                val reach = slot * 3.5f
                val big = 64.dp.toPx()
                val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, shadow = WallpaperShadow)
                val bold = style.copy(fontWeight = FontWeight.Bold)
                val activeNow = ((fingerY / slot).toInt()).coerceIn(0, n - 1)
                for (i in 0 until n) {
                    val cy = (i + 0.5f) * slot
                    val near = (1f - abs(cy - fingerY) / reach).coerceAtLeast(0f)
                    val w = near * near * s
                    val layout = measurer.measure(initials[i].first.toString(), if (i == activeNow) bold else style)
                    val cx = size.width - 14.dp.toPx() - w * 34.dp.toPx()
                    val k = 1f + 0.8f * w
                    withTransform({ scale(k, k, Offset(cx, cy)) }) {
                        drawText(layout, if (i == activeNow) primary else letterColor, Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f), alpha = a * s)
                    }
                }
                if (s > 0.01f) {
                    val cx = size.width - 14.dp.toPx() - 34.dp.toPx() - big
                    val cy = fingerY.coerceIn(big / 2, (size.height - big / 2).coerceAtLeast(big / 2))
                    drawCircle(Color.Black.copy(alpha = 0.25f * a * s), big / 2 + 2.dp.toPx() * s, Offset(cx, cy + 2.dp.toPx()))
                    drawCircle(primary.copy(alpha = a * s), big / 2 * (0.6f + 0.4f * s), Offset(cx, cy))
                    val layout = measurer.measure(initials[activeNow].first.toString(), TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium))
                    drawText(layout, onPrimary, Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f), alpha = a * s)
                }
            },
    )
}

/** Distance from one row's top to the next, read from where the rows actually sit. */
private fun rowPitch(info: LazyGridLayoutInfo): Int {
    val items = info.visibleItemsInfo
    val first = items.firstOrNull() ?: return 0
    val next = items.firstOrNull { it.offset.y != first.offset.y }
    return if (next != null) next.offset.y - first.offset.y else first.size.height + info.mainAxisItemSpacing
}
