package com.elara.launcher

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * The dock: a fixed grid of [columns] by [rows] at the bottom of the home screen, icons only, each
 * app in its own slot ([apps] by slot, null for an empty one). Tap opens an app. Long-press opens a
 * menu (remove, app info); keep holding and drag to any slot. The other apps make room as the
 * finger moves (see [DockSlots.place]) and the layout shown when it lifts is the one saved.
 * Screen readers get Move left / Move right instead.
 */
@Composable
fun Dock(
    apps: List<AppEntry?>,
    repository: AppRepository,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    rowGap: Dp,
    columnGap: Dp,
    showBar: Boolean,
    locked: Boolean,
    onArrange: (List<AppEntry?>) -> Unit,
    onRemove: (AppEntry) -> Unit,
    // Given a way to find the slot nearest a window position, for apps dropped from the drawer.
    onDropTarget: ((window: Offset) -> Int?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // A change shows at once, not when the saved dock comes back; a new saved dock replaces it.
    var slots by remember(apps) { mutableStateOf(apps) }
    if (slots.all { it == null }) return
    // The slot being dragged from and how far.
    var dragging by remember { mutableIntStateOf(-1) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val arrange = { to: List<AppEntry?> ->
        slots = to
        onArrange(to)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .then(if (showBar) Modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(32.dp)) else Modifier),
    ) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val even = width / columns
        // Icons shrink to fit narrow columns.
        val icon = minOf(iconSize, with(density) { (even * 0.85f).toDp() })
        val iconPx = with(density) { icon.toPx() }
        // Columns [columnGap] apart, centred; never wider than spread evenly across the dock.
        val slot = minOf(even, iconPx + with(density) { columnGap.toPx() })
        val left = (width - slot * columns) / 2
        val rowHeight = iconPx + with(density) { rowGap.toPx() }
        fun center(index: Int) = Offset(left + (index % columns + 0.5f) * slot, (index / columns + 0.5f) * rowHeight)
        // The layout if the finger lifted now. Read when the drag ends too: the gesture outlives this composition.
        val previewOf = {
            if (dragging < 0) slots else {
                val at = center(dragging) + drag
                DockSlots.place(slots, dragging, slots.indices.minBy { (center(it) - at).getDistanceSquared() })
            }
        }
        val preview = previewOf()
        val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
        SideEffect {
            onDropTarget { at -> coords[0]?.takeIf { it.isAttached }?.windowToLocal(at)?.let { p -> slots.indices.minBy { (center(it) - p).getDistanceSquared() } } }
        }

        Box(Modifier.fillMaxWidth().height(with(density) { (rows * rowHeight).toDp() }).onGloballyPositioned { coords[0] = it }) {
            slots.forEachIndexed { i, app ->
                if (app == null) return@forEachIndexed
                // Keyed by app, so its gesture and position follow it to a new slot.
                key(app.key) {
                    val home = center(i)
                    // Where the app's centre is drawn: under the finger while held, else gliding to its
                    // slot in the preview, from wherever it is.
                    val position = remember { Animatable(home, Offset.VectorConverter) }
                    val held = i == dragging
                    val goal = center(preview.indexOf(app))
                    LaunchedEffect(held, goal) {
                        if (held) snapshotFlow { home + drag }.collect { position.snapTo(it) } else position.animateTo(goal)
                    }
                    DockIcon(
                        app = app,
                        repository = repository,
                        size = icon,
                        lifted = held,
                        offset = { position.value - home },
                        canMoveLeft = !locked && i > 0,
                        canMoveRight = !locked && i < slots.lastIndex,
                        canRemove = !locked,
                        onMove = { by -> arrange(DockSlots.place(slots, i, i + by)) },
                        onDragStart = {
                            // Locked: the menu opens, the app stays put.
                            if (locked) return@DockIcon
                            dragging = i
                            drag = Offset.Zero
                        },
                        onDrag = { if (dragging == i) drag += it },
                        onDragEnd = {
                            val to = previewOf()
                            // drag is left as is: zeroing it would snap the icon back to its old slot
                            // before the glide starts; the next drag zeroes it.
                            dragging = -1
                            if (to != slots) arrange(to)
                        },
                        onRemove = { onRemove(app) },
                        modifier = Modifier.offset { IntOffset((home.x - iconPx / 2).roundToInt(), (home.y - iconPx / 2).roundToInt()) },
                    )
                }
            }
        }
    }
}

/** Dock edits by slot. Keys use "" for an empty slot, and trailing empty slots are dropped. */
object DockSlots {
    /**
     * Moves the item in [from] to [to] (null is an empty slot). An empty [to] just takes it; a full
     * one pushes the items from [to] one slot back toward [from], up to the first gap that way, which
     * the slot left behind guarantees.
     */
    fun <T : Any> place(slots: List<T?>, from: Int, to: Int): List<T?> {
        if (from == to || to !in slots.indices) return slots
        val s = slots.toMutableList()
        val item = s[from]
        s[from] = null
        if (s[to] != null) {
            val step = if (from > to) 1 else -1
            var gap = to
            while (s[gap] != null) gap += step
            while (gap != to) {
                s[gap] = s[gap - step]
                gap -= step
            }
        }
        s[to] = item
        return s
    }

    fun remove(slots: List<String>, key: String) = slots.map { if (it == key) "" else it }.trimmed()

    /** Puts [key] in the first empty slot of the first [capacity]; unchanged if there is none. */
    fun add(slots: List<String>, key: String, capacity: Int): List<String> {
        val at = (0 until capacity).firstOrNull { slots.getOrNull(it).isNullOrEmpty() } ?: return slots
        return List(maxOf(slots.size, at + 1)) { if (it == at) key else slots.getOrElse(it) { "" } }
    }

    /**
     * Puts [key] in slot [to] of the first [capacity], the others making room as in [place]: from its
     * own slot if already there, else by the empty slot nearest [to]; unchanged if there is none.
     */
    fun insert(slots: List<String>, key: String, to: Int, capacity: Int): List<String> {
        val s = MutableList(capacity) { slots.getOrNull(it)?.ifEmpty { null } }
        val from = s.indexOf(key).takeIf { it >= 0 }
            ?: s.indices.filter { s[it] == null }.minByOrNull { kotlin.math.abs(it - to) } ?: return slots
        s[from] = key
        return (place(s, from, to.coerceIn(0, capacity - 1)).map { it.orEmpty() } + slots.drop(capacity).map { if (it == key) "" else it }).trimmed()
    }

    fun hasRoom(slots: List<String>, capacity: Int) = (0 until capacity).any { slots.getOrNull(it).isNullOrEmpty() }

    fun List<String>.trimmed() = dropLastWhile { it.isEmpty() }
}

@Composable
private fun DockIcon(
    app: AppEntry,
    repository: AppRepository,
    size: Dp,
    lifted: Boolean,
    offset: () -> Offset,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canRemove: Boolean,
    onMove: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState(repository.cachedIcon(app, sizePx), app.key, sizePx, repository.iconShape) { value = repository.icon(app, sizePx) }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val bounds = {
        coords[0]?.takeIf { it.isAttached }?.boundsInWindow()?.let { r ->
            Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
        }
    }
    var menu by remember { mutableStateOf(false) }
    // The gesture below is set up once per app; these keep it calling the latest callbacks.
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val haptics = LocalHapticFeedback.current
    val slop = LocalViewConfiguration.current.touchSlop
    val moveLeft = stringResource(R.string.dock_move_left)
    val moveRight = stringResource(R.string.dock_move_right)
    val scale by animateFloatAsState(if (lifted) 1.15f else 1f, label = "dock lift")
    // The dragged app is drawn over the others.
    Box(modifier.zIndex(if (lifted) 1f else 0f)) {
        Box(
            Modifier
                .size(size)
                .onGloballyPositioned { coords[0] = it }
                .pointerInput(app.key) { detectTapGestures(onTap = { repository.launch(app, bounds()) }) }
                .pointerInput(app.key) {
                    var travelled = Offset.Zero
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            travelled = Offset.Zero
                            menu = true
                            start()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            travelled += amount
                            // Moving it means rearranging, not choosing from the menu.
                            if (travelled.getDistance() > slop) menu = false
                            drag(amount)
                        },
                        onDragEnd = { end() },
                        onDragCancel = { end() },
                    )
                }
                .semantics {
                    contentDescription = app.label
                    role = Role.Button
                    onClick { repository.launch(app, bounds()); true }
                    customActions = buildList {
                        if (canMoveLeft) add(CustomAccessibilityAction(moveLeft) { onMove(-1); true })
                        if (canMoveRight) add(CustomAccessibilityAction(moveRight) { onMove(1); true })
                    }
                }
                // Last, so touches are measured from the slot: an icon moving under the finger would
                // otherwise eat into each drag step and fall behind it.
                .graphicsLayer {
                    offset().let {
                        translationX = it.x
                        translationY = it.y
                    }
                    scaleX = scale
                    scaleY = scale
                }
                .pressScale(),
        ) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
        }
        AppMenu(
            menu,
            app,
            repository,
            buildList {
                // Locked: the layout can't change, so no remove.
                if (canRemove) add(MenuAction(Icons.DockBottom, stringResource(R.string.menu_remove_dock), onClick = onRemove))
                if (repository.canUninstall(app)) {
                    add(MenuAction(Icons.Uninstall, stringResource(R.string.menu_uninstall), danger = true) { repository.uninstall(app) })
                }
            },
            onDismiss = { menu = false },
            icon = icon,
        )
    }
}
