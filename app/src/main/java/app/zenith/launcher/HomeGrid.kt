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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/** A widget being dragged: where it was, where the finger started, and where it is now. */
private class WidgetDrag(val widget: HomeWidget, val start: Offset) {
    var at by mutableStateOf(start)

    /** The cells it would take if let go now. */
    fun target(cellW: Float, cellH: Float, columns: Int, rows: Int): HomeWidget {
        val d = at - start
        return widget.copy(
            col = (widget.col + d.x / cellW).roundToInt().coerceIn(0, columns - widget.w),
            row = (widget.row + d.y / cellH).roundToInt().coerceIn(0, rows - widget.h),
        )
    }
}

/** An app being dragged on the home screen: where it came from, and the finger's place in the grid. */
private class HomeDrag(val key: String, val fromPage: Int, val from: Int, at: Offset) {
    var at by mutableStateOf(at)
}

/**
 * The home screen's apps: pages of [columns] by [rows] slots, swiped sideways, with dots when there
 * is more than one (and [showDots]). Tap opens an app. Long-press opens its menu (remove, app info); keep holding
 * and drag to any slot, the others making room live ([HomePages.move]); hold at a side edge to go
 * to the page there, a new one past the last. Long-press on an empty slot is [onLongPressEmpty]
 * (window coordinates). Gestures are read here, not on the icons, so a drag survives its page
 * being swiped away.
 */
@Composable
fun HomeGrid(
    pages: List<List<String>>,
    widgets: List<HomeWidget>,
    apps: Map<String, AppEntry>,
    repository: AppRepository,
    widgetHost: WidgetHost,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    showLabels: Boolean,
    showDots: Boolean,
    locked: Boolean,
    page: Int,
    onPageChange: (Int) -> Unit,
    onPagesChange: (List<List<String>>) -> Unit,
    onWidgetsChange: (List<HomeWidget>) -> Unit,
    // A cell's size in pixels, which new widgets are sized by.
    onCellSize: (Size) -> Unit,
    onLongPressEmpty: (Offset) -> Unit,
    onDoubleTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val capacity = columns * rows
    // A change shows at once, not when the saved pages come back.
    var local by remember { mutableStateOf(pages) }
    LaunchedEffect(pages) { local = pages }
    var localWidgets by remember { mutableStateOf(widgets) }
    LaunchedEffect(widgets) { localWidgets = widgets }
    var drag by remember { mutableStateOf<HomeDrag?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var widgetDrag by remember { mutableStateOf<WidgetDrag?>(null) }
    var widgetMenuFor by remember { mutableStateOf<Int?>(null) }
    // The widget showing its resize frame.
    var resizing by remember { mutableStateOf<Int?>(null) }
    // Where a dropped widget was let go, so it glides from there to its cells.
    var widgetDropAt by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    // Where a dropped app was let go, so it glides from there into its slot.
    var dropAt by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    LaunchedEffect(dropAt) {
        delay(100)
        dropAt = null
    }
    val pager = rememberPagerState { maxOf(local.size, (localWidgets.maxOfOrNull { it.page } ?: -1) + 1, 1) + if (drag != null) 1 else 0 }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val slop = LocalViewConfiguration.current.touchSlop
    val edge = with(LocalDensity.current) { 32.dp.toPx() }
    val change by rememberUpdatedState(onPagesChange)
    val isLocked by rememberUpdatedState(locked)
    val pageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { pageChange(it) } }
    // A page asked for from outside (a page added or removed from the home menu), once it exists.
    LaunchedEffect(page, local.size) { if (page != pager.currentPage && page < pager.pageCount) pager.animateScrollToPage(page) }
    val known by rememberUpdatedState(apps)
    val empty by rememberUpdatedState(onLongPressEmpty)
    val window = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val commit = { to: List<List<String>> ->
        local = to
        change(to)
    }
    val widgetsChange by rememberUpdatedState(onWidgetsChange)
    val commitWidgets = { to: List<HomeWidget> ->
        localWidgets = to
        widgetsChange(to)
    }
    // App slots under widgets, which apps skip.
    val blockedOf = { p: Int -> HomeWidgets.blocked(localWidgets, p, columns, rows) }
    LaunchedEffect(pager.currentPage) { resizing = null }

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
            val cellSize by rememberUpdatedState(onCellSize)
            LaunchedEffect(cellW, cellH) { cellSize(Size(cellW, cellH)) }
            val labelPx = if (showLabels) with(density) { 26.dp.toPx() } else 0f
            // Icons shrink to fit small cells.
            val icon = minOf(iconSize, with(density) { (minOf(cellW * 0.8f, cellH - labelPx - 8.dp.toPx())).coerceAtLeast(16.dp.toPx()).toDp() })
            fun center(slot: Int) = Offset((slot % columns + 0.5f) * cell[0], (slot / columns + 0.5f) * cell[1])
            fun slotAt(at: Offset) = (at.y / cell[1]).toInt().coerceIn(0, rows - 1) * columns + (at.x / cell[0]).toInt().coerceIn(0, columns - 1)
            // The page shown, laid out as if the finger lifted now.
            val previewOf = { page: Int ->
                val d = drag
                val moved = d?.let { HomePages.move(local, it.fromPage, it.from, page, slotAt(it.at), capacity, blockedOf) }
                HomePages.slots(moved?.getOrNull(page) ?: local.getOrNull(page), capacity)
            }

            Box(
                Modifier
                    .fillMaxSize()
                    // A tap anywhere but the frame's handles puts the resize frame away.
                    .pointerInput(resizing != null) { if (resizing != null) detectTapGestures(onTap = { resizing = null }) }
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
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                travelled = Offset.Zero
                                resizing = null
                                val widget = localWidgets.firstOrNull { it.page == page && slot in it.slots(columns, rows) }
                                if (widget != null) {
                                    widgetMenuFor = widget.id
                                    // Locked: the menu only, no drag.
                                    if (!isLocked) widgetDrag = WidgetDrag(widget.fitted(columns, rows), at)
                                    return@detectDragGesturesAfterLongPress
                                }
                                val key = HomePages.slots(local.getOrNull(page), capacity).getOrNull(slot)?.takeIf { it in known }
                                if (key == null) {
                                    empty(at + (window[0]?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero))
                                } else {
                                    menuFor = key
                                    // Locked: the menu only, no drag.
                                    if (!isLocked) drag = HomeDrag(key, page, slot, center(slot))
                                }
                            },
                            onDrag = { change, amount ->
                                widgetDrag?.let { w ->
                                    change.consume()
                                    travelled += amount
                                    if (travelled.getDistance() > slop) widgetMenuFor = null
                                    w.at += amount
                                    return@detectDragGesturesAfterLongPress
                                }
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
                                widgetDrag?.let { w ->
                                    widgetDrag = null
                                    widgetDropAt = w.widget.id to (Offset(w.widget.col * cell[0], w.widget.row * cell[1]) + w.at - w.start)
                                    val to = w.target(cell[0], cell[1], columns, rows)
                                    if (to != w.widget && HomeWidgets.fits(to, local, localWidgets, columns, rows)) {
                                        commitWidgets(localWidgets.map { if (it.id == to.id) to else it })
                                    }
                                }
                                drag?.let { d ->
                                    drag = null
                                    dropAt = d.key to d.at
                                    val page = pager.currentPage
                                    val to = HomePages.move(local, d.fromPage, d.from, page, slotAt(d.at), capacity, blockedOf)
                                    if (to != null && to != local) commit(to)
                                }
                            },
                            onDragCancel = {
                                edgeJob?.cancel()
                                edgeJob = null
                                widgetDrag = null
                                drag?.let { dropAt = it.key to it.at }
                                drag = null
                            },
                        )
                    },
            ) {
                HorizontalPager(pager, userScrollEnabled = drag == null && widgetDrag == null && resizing == null, modifier = Modifier.fillMaxSize()) { page ->
                    val slots = if (drag != null && page == pager.currentPage) previewOf(page) else HomePages.slots(local.getOrNull(page), capacity)
                    Box(Modifier.fillMaxSize()) {
                        // Where a dragged widget would land, when it can.
                        widgetDrag?.takeIf { page == pager.currentPage }?.let { w ->
                            val to = w.target(cellW, cellH, columns, rows)
                            if (HomeWidgets.fits(to, local, localWidgets, columns, rows)) {
                                Box(
                                    Modifier
                                        .offset { Offset(to.col * cellW, to.row * cellH).round() }
                                        .size(with(density) { (to.w * cellW).toDp() }, with(density) { (to.h * cellH).toDp() })
                                        .padding(4.dp)
                                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                                )
                            }
                        }
                        localWidgets.filter { it.page == page }.forEach { stored ->
                            val w = stored.fitted(columns, rows)
                            key("widget", w.id) {
                                val home = Offset(w.col * cellW, w.row * cellH)
                                val position = remember { Animatable(home, Offset.VectorConverter) }
                                LaunchedEffect(home, widgetDropAt) {
                                    widgetDropAt?.takeIf { it.first == w.id }?.let { position.snapTo(it.second) }
                                    position.animateTo(home)
                                }
                                val dragged = widgetDrag?.takeIf { it.widget.id == w.id }
                                Box(
                                    Modifier
                                        .zIndex(if (dragged != null) 1f else 0f)
                                        .offset { (dragged?.let { home + it.at - it.start } ?: position.value).round() }
                                        .size(with(density) { (w.w * cellW).toDp() }, with(density) { (w.h * cellH).toDp() })
                                        .graphicsLayer {
                                            val scale = if (dragged != null) 1.04f else 1f
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                ) {
                                    HostedWidget(widgetHost, w.id, Modifier.fillMaxSize().padding(4.dp))
                                    WidgetMenu(
                                        expanded = widgetMenuFor == w.id,
                                        id = w.id,
                                        host = widgetHost,
                                        locked = locked,
                                        onResize = { resizing = w.id },
                                        onRemove = {
                                            commitWidgets(localWidgets.filter { it.id != w.id })
                                            widgetHost.remove(w.id)
                                        },
                                        onDismiss = { widgetMenuFor = null },
                                    )
                                }
                            }
                        }
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
                                    canMoveLeft = !locked && i > 0,
                                    canMoveRight = !locked && i < capacity - 1,
                                    canRemove = !locked,
                                    onMove = { by -> HomePages.move(local, page, i, page, i + by, capacity, blockedOf)?.let(commit) },
                                    onRemove = { commit(HomePages.remove(local, app.key)) },
                                    modifier = Modifier
                                        .size(with(density) { cellW.toDp() }, with(density) { cellH.toDp() })
                                        .offset { (position.value - Offset(cellW / 2, cellH / 2)).round() },
                                )
                            }
                        }
                    }
                }
                resizing?.let { id ->
                    localWidgets.firstOrNull { it.id == id && it.page == pager.currentPage }?.fitted(columns, rows)?.let { w ->
                        ResizeFrame(w, cellW, cellH, columns, rows, remember(w.id) { widgetHost.details(w.id) }) { to ->
                            if (to != w && HomeWidgets.fits(to, local, localWidgets, columns, rows)) commitWidgets(localWidgets.map { if (it.id == to.id) to else it })
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
        // When shown, always takes its space, so the grid keeps its size when a drag adds a page.
        if (showDots) Row(
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
    canRemove: Boolean,
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
                        if (canRemove) add(CustomAccessibilityAction(remove) { onRemove(); true })
                    }
                },
        )
        AppMenu(
            menu,
            app,
            repository,
            buildList {
                // Locked: the layout can't change, so no remove.
                if (canRemove) add(MenuAction(Icons.RemoveHome, stringResource(R.string.menu_remove_home), onClick = onRemove))
                if (repository.canUninstall(app)) {
                    add(MenuAction(Icons.Uninstall, stringResource(R.string.menu_uninstall), danger = true) { repository.uninstall(app) })
                }
            },
            onDismiss = onMenuDismiss,
        )
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
