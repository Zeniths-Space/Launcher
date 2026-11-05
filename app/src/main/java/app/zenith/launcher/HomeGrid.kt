package app.zenith.launcher

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** An app being dragged on the home screen: where it came from, and the finger's place in the grid. */
private class HomeDrag(val key: String, val fromPage: Int, val from: Int, at: Offset) {
    var at by mutableStateOf(at)
}

/**
 * The home screen's apps: pages of [columns] by [rows] slots, swiped sideways, with dots when there
 * is more than one. Tap opens an app. Long-press opens its menu (remove, app info); keep holding
 * and drag to any slot, the others making room live ([HomePages.move]); hold at a side edge to go
 * to the page there, a new one past the last. Long-press on an empty slot is [onLongPressEmpty]
 * (window coordinates). Gestures are read here, not on the icons, so a drag survives its page
 * being swiped away.
 */
@Composable
fun HomeGrid(
    pages: List<List<String>>,
    apps: Map<String, AppEntry>,
    repository: AppRepository,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    showLabels: Boolean,
    onPagesChange: (List<List<String>>) -> Unit,
    onLongPressEmpty: (Offset) -> Unit,
    onDoubleTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val capacity = columns * rows
    // A change shows at once, not when the saved pages come back.
    var local by remember { mutableStateOf(pages) }
    LaunchedEffect(pages) { local = pages }
    var drag by remember { mutableStateOf<HomeDrag?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    // Where a dropped app was let go, so it glides from there into its slot.
    var dropAt by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    LaunchedEffect(dropAt) {
        delay(100)
        dropAt = null
    }
    val pager = rememberPagerState { local.size.coerceAtLeast(1) + if (drag != null) 1 else 0 }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val slop = LocalViewConfiguration.current.touchSlop
    val edge = with(LocalDensity.current) { 32.dp.toPx() }
    val change by rememberUpdatedState(onPagesChange)
    val known by rememberUpdatedState(apps)
    val empty by rememberUpdatedState(onLongPressEmpty)
    val window = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val commit = { to: List<List<String>> ->
        local = to
        change(to)
    }

    Column(modifier) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { window[0] = it },
        ) {
            val density = LocalDensity.current
            val cellW = constraints.maxWidth / columns.toFloat()
            val cellH = constraints.maxHeight / rows.toFloat()
            // Read by the gesture, which must not restart (and drop a drag) when the cells change size.
            val cell = remember { FloatArray(2) }
            cell[0] = cellW
            cell[1] = cellH
            val labelPx = if (showLabels) with(density) { 26.dp.toPx() } else 0f
            // Icons shrink to fit small cells.
            val icon = minOf(iconSize, with(density) { (minOf(cellW * 0.8f, cellH - labelPx - 8.dp.toPx())).coerceAtLeast(16.dp.toPx()).toDp() })
            fun center(slot: Int) = Offset((slot % columns + 0.5f) * cell[0], (slot / columns + 0.5f) * cell[1])
            fun slotAt(at: Offset) = (at.y / cell[1]).toInt().coerceIn(0, rows - 1) * columns + (at.x / cell[0]).toInt().coerceIn(0, columns - 1)
            // The page shown, laid out as if the finger lifted now.
            val previewOf = { page: Int ->
                val d = drag
                val moved = d?.let { HomePages.move(local, it.fromPage, it.from, page, slotAt(it.at), capacity) }
                HomePages.slots(moved?.getOrNull(page) ?: local.getOrNull(page), capacity)
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(onDoubleTap != null) {
                        // Double tap only listened for when it does something; icon taps are taken first by the icons.
                        detectTapGestures(onDoubleTap = onDoubleTap?.let { { _: Offset -> it() } })
                    }
                    .pointerInput(columns, rows) {
                        var travelled = Offset.Zero
                        var edgeJob: Job? = null
                        detectDragGesturesAfterLongPress(
                            onDragStart = { at ->
                                val page = pager.currentPage
                                val slot = slotAt(at)
                                val key = HomePages.slots(local.getOrNull(page), capacity).getOrNull(slot)?.takeIf { it in known }
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                travelled = Offset.Zero
                                if (key == null) {
                                    empty(at + (window[0]?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero))
                                } else {
                                    menuFor = key
                                    drag = HomeDrag(key, page, slot, center(slot))
                                }
                            },
                            onDrag = { change, amount ->
                                val d = drag ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                travelled += amount
                                // Moving it means rearranging, not choosing from the menu.
                                if (travelled.getDistance() > slop) menuFor = null
                                d.at += amount
                                val toward = when {
                                    d.at.x < edge -> -1
                                    d.at.x > size.width - edge -> 1
                                    else -> 0
                                }
                                val next = pager.currentPage + toward
                                if (toward == 0 || next !in 0 until pager.pageCount) {
                                    edgeJob?.cancel()
                                    edgeJob = null
                                } else if (edgeJob == null) {
                                    // Held at the edge for a moment: go to the page there.
                                    edgeJob = scope.launch {
                                        delay(500)
                                        pager.animateScrollToPage(next)
                                        edgeJob = null
                                    }
                                }
                            },
                            onDragEnd = {
                                edgeJob?.cancel()
                                edgeJob = null
                                drag?.let { d ->
                                    drag = null
                                    dropAt = d.key to d.at
                                    val page = pager.currentPage
                                    val to = HomePages.move(local, d.fromPage, d.from, page, slotAt(d.at), capacity)
                                    if (to != null && to != local) commit(to)
                                }
                            },
                            onDragCancel = {
                                edgeJob?.cancel()
                                edgeJob = null
                                drag?.let { dropAt = it.key to it.at }
                                drag = null
                            },
                        )
                    },
            ) {
                HorizontalPager(pager, userScrollEnabled = drag == null, modifier = Modifier.fillMaxSize()) { page ->
                    val slots = if (drag != null && page == pager.currentPage) previewOf(page) else HomePages.slots(local.getOrNull(page), capacity)
                    Box(Modifier.fillMaxSize()) {
                        slots.take(capacity).forEachIndexed { i, k ->
                            val app = k?.let(apps::get) ?: return@forEachIndexed
                            if (app.key == drag?.key) return@forEachIndexed
                            // Keyed by app, so its position follows it to a new slot.
                            key(app.key) {
                                val home = center(i)
                                val position = remember { Animatable(dropAt?.takeIf { it.first == app.key }?.second ?: home, Offset.VectorConverter) }
                                LaunchedEffect(home) { position.animateTo(home) }
                                HomeIcon(
                                    app = app,
                                    repository = repository,
                                    icon = icon,
                                    showLabels = showLabels,
                                    menu = menuFor == app.key,
                                    onMenuDismiss = { menuFor = null },
                                    canTap = { drag == null && menuFor == null },
                                    canMoveLeft = i > 0,
                                    canMoveRight = i < capacity - 1,
                                    onMove = { by -> HomePages.move(local, page, i, page, i + by, capacity)?.let(commit) },
                                    onRemove = { commit(HomePages.remove(local, app.key)) },
                                    modifier = Modifier
                                        .size(with(density) { cellW.toDp() }, with(density) { cellH.toDp() })
                                        .offset { (position.value - Offset(cellW / 2, cellH / 2)).round() },
                                )
                            }
                        }
                    }
                }
                // The dragged app, over every page.
                drag?.let { d ->
                    apps[d.key]?.let { app ->
                        HomeIconFace(
                            app,
                            repository,
                            icon,
                            showLabels,
                            Modifier
                                .zIndex(1f)
                                .size(with(density) { cellW.toDp() }, with(density) { cellH.toDp() })
                                .offset { (d.at - Offset(cellW / 2, cellH / 2)).round() }
                                .graphicsLayer {
                                    scaleX = 1.1f
                                    scaleY = 1.1f
                                },
                        )
                    }
                }
            }
        }
        // Always takes its space, so the grid keeps its size when a drag adds a page.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (pager.pageCount > 1) {
                repeat(pager.pageCount) { p ->
                    Box(Modifier.size(6.dp).background(Color.White.copy(alpha = if (p == pager.currentPage) 1f else 0.4f), CircleShape))
                }
            }
        }
    }
}

private fun Offset.round() = IntOffset(x.roundToInt(), y.roundToInt())

@Composable
private fun HomeIcon(
    app: AppEntry,
    repository: AppRepository,
    icon: Dp,
    showLabels: Boolean,
    menu: Boolean,
    onMenuDismiss: () -> Unit,
    canTap: () -> Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val bounds = {
        coords[0]?.takeIf { it.isAttached }?.boundsInWindow()?.let { r ->
            Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
        }
    }
    val moveLeft = stringResource(R.string.dock_move_left)
    val moveRight = stringResource(R.string.dock_move_right)
    val remove = stringResource(R.string.home_remove)
    Box(modifier) {
        HomeIconFace(
            app,
            repository,
            icon,
            showLabels,
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords[0] = it }
                // A release after a long-press opens nothing: that press was for the menu or a drag.
                .pointerInput(app.key) { detectTapGestures(onTap = { if (canTap()) repository.launch(app, bounds()) }) }
                .semantics {
                    contentDescription = app.label
                    role = Role.Button
                    onClick { repository.launch(app, bounds()); true }
                    customActions = buildList {
                        if (canMoveLeft) add(CustomAccessibilityAction(moveLeft) { onMove(-1); true })
                        if (canMoveRight) add(CustomAccessibilityAction(moveRight) { onMove(1); true })
                        add(CustomAccessibilityAction(remove) { onRemove(); true })
                    }
                },
        )
        DropdownMenu(expanded = menu, onDismissRequest = onMenuDismiss) {
            DropdownMenuItem(
                text = { Text(remove) },
                onClick = {
                    onMenuDismiss()
                    onRemove()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_info)) },
                onClick = {
                    onMenuDismiss()
                    repository.openAppInfo(app)
                },
            )
        }
    }
}

/** Icon, and name under it, centred in its cell. */
@Composable
private fun HomeIconFace(app: AppEntry, repository: AppRepository, icon: Dp, showLabels: Boolean, modifier: Modifier) {
    val sizePx = with(LocalDensity.current) { icon.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, app.key, sizePx) { value = repository.icon(app, sizePx) }
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(icon)) { bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
        if (showLabels) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.labelMedium.copy(shadow = WallpaperShadow),
                color = OnWallpaper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
