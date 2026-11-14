package com.elara.launcher

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * An app or folder being dragged on the home screen: where it came from, the finger's place in the
 * grid, the slot the others make room around ([target], which follows the finger after a short
 * rest), and the slot whose item it would drop into ([merge]), after a hover over that item.
 */
private class HomeDrag(val key: String, val fromPage: Int, val from: Int, at: Offset, val span: Int = 1) {
    val large get() = span > 1
    var at by mutableStateOf(at)
    // Moved past the touch slop: a drag, not just a press for the menu.
    var moved by mutableStateOf(false)
    var target by mutableIntStateOf(from)
    var merge by mutableStateOf<Int?>(null)
}

// How long the finger rests before the others make room, and over an app before it'd make a folder.
private const val ReflowDelay = 150L
private const val MergeDelay = 350L

// How much bigger a held app or folder is drawn.
private const val DragLift = 1.1f

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
    folders: List<HomeFolder>,
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
    // Pages and folders together, for changes that touch both (a folder made, an app taken out).
    onLayoutChange: (List<List<String>>, List<HomeFolder>) -> Unit,
    onWidgetsChange: (List<HomeWidget>) -> Unit,
    // Counts Home presses and leaving, which close an open folder and the resize frame.
    resets: Int,
    // A cell's size in pixels, which new widgets are sized by.
    onCellSize: (Size) -> Unit,
    onLongPressEmpty: (Offset) -> Unit,
    onDoubleTap: (() -> Unit)?,
    /** The blurred wallpaper, for an open folder's card to be glass over. */
    glass: androidx.compose.ui.graphics.ImageBitmap? = null,
    onFolderProgress: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val capacity = columns * rows
    // A change shows at once, not when the saved pages come back.
    var local by remember { mutableStateOf(pages) }
    LaunchedEffect(pages) { local = pages }
    var localFolders by remember { mutableStateOf(folders) }
    LaunchedEffect(folders) { localFolders = folders }
    var openFolder by remember { mutableStateOf<Int?>(null) }
    // The folder last opened, kept while it animates shut.
    var shownFolder by remember { mutableStateOf<Int?>(null) }
    // Once the closing animation (160 ms) is over the tile comes back.
    LaunchedEffect(openFolder) {
        if (openFolder == null && shownFolder != null) {
            kotlinx.coroutines.delay(200)
            shownFolder = null
        }
    }
    var folderOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
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
    // Slots of large folders on a page, which apps and widgets keep out of.
    val largeOf = { p: Int -> HomeFolders.largeCells(local, localFolders, p, columns, rows) }
    val blockedOf = { p: Int -> HomeWidgets.blocked(localWidgets, p, columns, rows) + largeOf(p) }
    // How many cells each way the item in a slot takes: a large folder's span, else 1.
    val spanAt = { p: Int, slot: Int ->
        HomeFolders.idOf(local.getOrNull(p)?.getOrNull(slot))?.let { id -> localFolders.firstOrNull { it.id == id } }
            ?.takeIf { HomeFolders.showsLarge(it, slot, columns, rows) }?.span ?: 1
    }
    val largeAt = { p: Int, slot: Int -> spanAt(p, slot) > 1 }
    // The dragged copy grows once it's moved, not on the press that opens the menu.
    val dragLift by animateFloatAsState(if (drag?.moved == true) DragLift else 1f, label = "drag lift")
    var customizing by remember { mutableStateOf<Int?>(null) }
    var shownCustomize by remember { mutableStateOf<Int?>(null) }
    var renamingFolder by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pager.currentPage) { resizing = null }
    LaunchedEffect(resets) {
        openFolder = null
        customizing = null
        resizing = null
    }
    val layoutChange by rememberUpdatedState(onLayoutChange)
    val commitLayout = { p: List<List<String>>, f: List<HomeFolder> ->
        local = p
        localFolders = f
        layoutChange(p, f)
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
            val cellSize by rememberUpdatedState(onCellSize)
            LaunchedEffect(cellW, cellH) { cellSize(Size(cellW, cellH)) }
            val labelPx = if (showLabels) with(density) { 26.dp.toPx() } else 0f
            // Icons shrink to fit small cells.
            val icon = minOf(iconSize, with(density) { (minOf(cellW * 0.8f, cellH - labelPx - 8.dp.toPx())).coerceAtLeast(16.dp.toPx()).toDp() })
            // A large folder's tile fills most of its cells.
            fun tileFor(span: Int) = if (span == 1) icon else with(density) { (minOf(span * cellW, span * cellH - labelPx) * 0.9f).coerceAtLeast(32.dp.toPx()).toDp() }
            // From a large folder's top-left cell's middle to its own middle.
            fun toMiddle(span: Int) = Offset(cell[0], cell[1]) * ((span - 1) / 2f)
            fun center(slot: Int) = Offset((slot % columns + 0.5f) * cell[0], (slot / columns + 0.5f) * cell[1])
            // Where a let-go item is, measured as its slot's position is: a large folder's middle, not its top-left cell's.
            fun dropPoint(d: HomeDrag) = d.at + toMiddle(d.span)
            fun slotAt(at: Offset) = (at.y / cell[1]).toInt().coerceIn(0, rows - 1) * columns + (at.x / cell[0]).toInt().coerceIn(0, columns - 1)
            // The page shown, laid out as if the finger lifted now.
            val previewOf = { page: Int ->
                val d = drag?.takeUnless { it.large }
                val moved = d?.let { HomePages.move(local, it.fromPage, it.from, page, it.target, capacity, blockedOf) }
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
                        // The slot under the finger and whether it's over an item's middle, and the wait on it.
                        var hover: Pair<Int, Boolean>? = null
                        var hoverJob: Job? = null
                        detectDragGesturesAfterLongPress(
                            onDragStart = { at ->
                                val page = pager.currentPage
                                val slot = HomeFolders.anchorAt(local, localFolders, pager.currentPage, slotAt(at), columns, rows)
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
                                val key = HomePages.slots(local.getOrNull(page), capacity).getOrNull(slot)?.takeIf { it in known || HomeFolders.isFolder(it) }
                                if (key == null) {
                                    empty(at + (window[0]?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero))
                                } else {
                                    menuFor = key
                                    // Locked: the menu only, no drag.
                                    if (!isLocked) drag = HomeDrag(key, page, slot, center(slot), span = spanAt(page, slot))
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
                                if (travelled.getDistance() > slop) {
                                    menuFor = null
                                    d.moved = true
                                }
                                d.at += amount
                                // Over the middle of another app or a folder: wait, then drop into it. Elsewhere:
                                // wait a moment less, then the others make room. Waiting first lets the finger
                                // pass over slots on its way without everything shuffling.
                                // A large folder drags alone: nothing makes room and nothing drops into anything.
                                if (!d.large) {
                                    val slot = slotAt(d.at)
                                    // A large folder takes drops anywhere over its middle.
                                    val anchor = HomeFolders.anchorAt(local, localFolders, pager.currentPage, slot, columns, rows)
                                    val span = spanAt(pager.currentPage, anchor)
                                    val big = span > 1
                                    val middle = if (big) center(anchor) + toMiddle(span) else center(slot)
                                    val reach = if (big) span * 0.35f else 0.3f
                                    val shown = previewOf(pager.currentPage)
                                    val onto = !isLocked && !HomeFolders.isFolder(d.key) &&
                                        kotlin.math.abs(d.at.x - middle.x) < cell[0] * reach && kotlin.math.abs(d.at.y - middle.y) < cell[1] * reach &&
                                        (big || shown.getOrNull(slot)?.let { it != d.key } == true)
                                    val into = if (big) anchor else slot
                                    if (hover != (into to onto)) {
                                        hover = into to onto
                                        hoverJob?.cancel()
                                        d.merge = null
                                        hoverJob = scope.launch {
                                            delay(if (onto) MergeDelay else ReflowDelay)
                                            if (onto) d.merge = into else d.target = slot
                                        }
                                    }
                                }
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
                                    if (to != w.widget && HomeWidgets.fits(to, local, localWidgets, columns, rows, largeOf)) {
                                        commitWidgets(localWidgets.map { if (it.id == to.id) to else it })
                                    }
                                }
                                hoverJob?.cancel()
                                hover = null
                                drag?.let { d ->
                                    drag = null
                                    dropAt = d.key to dropPoint(d)
                                    val page = pager.currentPage
                                    val into = d.merge
                                    if (d.large) {
                                        // Only onto free cells, the others staying put.
                                        val to = slotAt(d.at)
                                        val taken = HomeWidgets.blocked(localWidgets, page, columns, rows) + HomeFolders.largeCells(local, localFolders, page, columns, rows, except = d.key)
                                        if ((page != d.fromPage || to != d.from) && HomeFolders.fitsLarge(local, page, to, columns, rows, taken, d.key, d.span)) {
                                            commit(HomeFolders.place(local, d.fromPage, d.from, page, to))
                                        }
                                    } else if (into != null) {
                                        // Dropped into an app or folder, as the page shows it now.
                                        val shown = HomePages.move(local, d.fromPage, d.from, page, d.target, capacity, blockedOf) ?: local
                                        val at = shown.getOrNull(page)?.indexOf(d.key) ?: -1
                                        HomeFolders.merge(shown, localFolders, page, at, page, into)?.let { (p, f) -> commitLayout(p, f) }
                                    } else {
                                        val to = HomePages.move(local, d.fromPage, d.from, page, slotAt(d.at), capacity, blockedOf)
                                        if (to != null && to != local) commit(to)
                                    }
                                }
                            },
                            onDragCancel = {
                                edgeJob?.cancel()
                                edgeJob = null
                                hoverJob?.cancel()
                                hover = null
                                widgetDrag = null
                                drag?.let { dropAt = it.key to dropPoint(it) }
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
                            if (HomeWidgets.fits(to, local, localWidgets, columns, rows, largeOf)) {
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
                        // The app or folder a dragged app would drop into.
                        drag?.merge?.takeIf { page == pager.currentPage }?.let { i ->
                            val span = spanAt(page, i)
                            if (span > 1) {
                                Box(
                                    Modifier
                                        .offset { (center(i) - Offset(cellW / 2, cellH / 2)).round() }
                                        .size(with(density) { (span * cellW).toDp() }, with(density) { (span * cellH).toDp() })
                                        .padding(4.dp)
                                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                                )
                            } else {
                                val ring = icon * 1.35f
                                Box(
                                    Modifier
                                        .offset { (center(i) - Offset(ring.toPx() / 2, ring.toPx() / 2 + if (showLabels) 13.dp.toPx() else 0f)).round() }
                                        .size(ring)
                                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(ring * 0.3f)),
                                )
                            }
                        }
                        slots.take(capacity).forEachIndexed { i, k ->
                            if (k == null) return@forEachIndexed
                            // The one being dragged stays, unseen, so its menu shows while the finger is still down.
                            val hidden = k == drag?.key
                            val folder = HomeFolders.idOf(k)?.let { id -> localFolders.firstOrNull { it.id == id } }
                            val app = apps[k]
                            if (folder == null && app == null) return@forEachIndexed
                            // Keyed by app or folder, so its position follows it to a new slot.
                            val span = folder?.takeIf { HomeFolders.showsLarge(it, i, columns, rows) }?.span ?: 1
                            val large = span > 1
                            key(k) {
                                // A large folder's middle is the middle of its cells.
                                val home = center(i) + toMiddle(span)
                                // New here (dropped from another page): starts where it was let go.
                                val position = remember { Animatable(dropAt?.takeIf { it.first == k }?.second ?: home, Offset.VectorConverter) }
                                // While held it follows the finger, unseen and lifted like the dragged copy, so
                                // letting go only has to ease it into its slot and back to size.
                                val lift by animateFloatAsState(if (hidden && drag?.moved == true) DragLift else 1f, label = "lift")
                                LaunchedEffect(home, hidden) {
                                    if (hidden) {
                                        snapshotFlow { drag?.let(::dropPoint) }.collect { at -> at?.let { position.snapTo(it) } }
                                    } else {
                                        position.animateTo(home)
                                    }
                                }
                                if (folder != null) {
                                    FolderIcon(
                                        folder = folder,
                                        apps = apps,
                                        repository = repository,
                                        tile = tileFor(span),
                                        large = large,
                                        showLabels = showLabels,
                                        menu = menuFor == k,
                                        onMenuDismiss = { menuFor = null },
                                        canTap = { drag == null && menuFor == null },
                                        canEdit = !locked,
                                        onOpen = { from ->
                                            folderOrigin = from
                                            openFolder = folder.id
                                            shownFolder = folder.id
                                        },
                                        onRename = { renamingFolder = folder.id },
                                        onCustomize = {
                                            customizing = folder.id
                                            shownCustomize = folder.id
                                        },
                                        onRemove = { HomeFolders.removeFolder(local, localFolders, folder.id).let { (p, f) -> commitLayout(p, f) } },
                                        modifier = Modifier
                                            .size(with(density) { (span * cellW).toDp() }, with(density) { (span * cellH).toDp() })
                                            .offset { (position.value - Offset(span * cellW / 2, span * cellH / 2)).round() }
                                            .graphicsLayer {
                                                // An open folder is its own card: the tile goes, its icons grow out of the place it was.
                                                alpha = if (hidden || shownFolder == folder.id) 0f else 1f
                                                scaleX = lift
                                                scaleY = lift
                                            },
                                    )
                                } else if (app != null) HomeIcon(
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
                                        .offset { (position.value - Offset(cellW / 2, cellH / 2)).round() }
                                        .graphicsLayer {
                                            alpha = if (hidden) 0f else 1f
                                            scaleX = lift
                                            scaleY = lift
                                        },
                                )
                            }
                        }
                    }
                }
                resizing?.let { id ->
                    localWidgets.firstOrNull { it.id == id && it.page == pager.currentPage }?.fitted(columns, rows)?.let { w ->
                        ResizeFrame(w, cellW, cellH, columns, rows, remember(w.id) { widgetHost.details(w.id) }) { to ->
                            if (to != w && HomeWidgets.fits(to, local, localWidgets, columns, rows, largeOf)) commitWidgets(localWidgets.map { if (it.id == to.id) to else it })
                        }
                    }
                }
                // The dragged app or folder, over every page.
                drag?.let { d ->
                    HomeFolders.idOf(d.key)?.let { id -> localFolders.firstOrNull { it.id == id } }?.let { folder ->
                        val span = d.span
                        FolderFace(
                            folder,
                            apps,
                            repository,
                            tileFor(d.span),
                            d.large,
                            showLabels,
                            Modifier
                                .zIndex(1f)
                                .size(with(density) { (span * cellW).toDp() }, with(density) { (span * cellH).toDp() })
                                // A large one is held by its top-left cell.
                                .offset { (d.at - Offset(cellW / 2, cellH / 2)).round() }
                                .graphicsLayer {
                                    scaleX = dragLift
                                    scaleY = dragLift
                                },
                        )
                    }
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
                                    scaleX = dragLift
                                    scaleY = dragLift
                                },
                        )
                    }
                }
            }
        }
        shownFolder?.let { id -> localFolders.firstOrNull { it.id == id } }?.let { folder ->
            FolderPopup(
                expanded = openFolder == folder.id,
                origin = folderOrigin,
                folder = folder,
                apps = apps,
                repository = repository,
                locked = locked,
                glass = glass,
                onProgress = onFolderProgress,
                candidates = apps.values.filter { it.key !in folder.apps && it.key != Games.Key }.sortedBy { it.label.lowercase() },
                onAddApps = { keys ->
                    // Each app leaves its slot (or another folder) and joins this one.
                    var (p, f) = local to localFolders
                    keys.forEach { k -> HomeFolders.removeApp(p, f, k).let { (np, nf) -> p = np; f = nf } }
                    commitLayout(p, f.map { if (it.id == folder.id) it.copy(apps = it.apps + keys) else it })
                },
                onClose = { name ->
                    openFolder = null
                    val clean = HomeFolders.clean(name)
                    if (clean != folder.name) commitLayout(local, localFolders.map { if (it.id == folder.id) it.copy(name = clean) else it })
                },
                onTakeOut = { key ->
                    val (p, f) = HomeFolders.removeApp(local, localFolders, key)
                    commitLayout(HomePages.add(p, key, capacity, first = pager.currentPage, blocked = blockedOf), f)
                },
            )
        }
        renamingFolder?.let { id -> localFolders.firstOrNull { it.id == id } }?.let { folder ->
            val default = stringResource(R.string.folder_default)
            RenameDialog(
                current = folder.name.ifEmpty { default },
                original = default,
                onSave = { name ->
                    val clean = HomeFolders.clean(name)
                    if (clean != folder.name) commitLayout(local, localFolders.map { if (it.id == folder.id) it.copy(name = clean) else it })
                },
                onDismiss = { renamingFolder = null },
            )
        }
        shownCustomize?.let { id -> localFolders.firstOrNull { it.id == id } }?.let { folder ->
            FolderCustomize(
                expanded = customizing == folder.id,
                folder = folder,
                onChange = { changed ->
                    val key = HomeFolders.key(changed.id)
                    val page = local.indexOfFirst { key in it }
                    val slot = local.getOrNull(page)?.indexOf(key) ?: -1
                    val taken = HomeWidgets.blocked(localWidgets, page, columns, rows) + HomeFolders.largeCells(local, localFolders, page, columns, rows, except = key)
                    // Smaller always fits; bigger needs the cells free.
                    val fits = changed.span <= folder.span || (page >= 0 && HomeFolders.fitsLarge(local, page, slot, columns, rows, taken, key, changed.span))
                    if (fits) commitLayout(local, localFolders.map { if (it.id == changed.id) changed else it })
                    fits
                },
                onDismiss = { customizing = null },
            )
        }
        // When shown, always takes its space, so the grid keeps its size when a drag adds a page.
        if (showDots) Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (pager.pageCount > 1) {
                repeat(pager.pageCount) { p ->
                    // The page shown stretches into a pill.
                    val current = p == pager.currentPage
                    val width by animateDpAsState(if (current) 18.dp else 6.dp, Motion.bouncy(), label = "dot")
                    val alpha by animateFloatAsState(if (current) 1f else 0.4f, Motion.effects(), label = "dot alpha")
                    Box(Modifier.size(width, 6.dp).background(Color.White.copy(alpha = alpha), CircleShape))
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
                }
                .pressScale(),
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
    val bitmap by produceState(repository.cachedIcon(app, sizePx), app.key, sizePx, repository.iconEpoch) { value = repository.icon(app, sizePx) }
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
