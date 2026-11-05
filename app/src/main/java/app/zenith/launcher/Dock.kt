package app.zenith.launcher

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * menu (remove, app info); keep holding and drag to any slot: an empty one takes the app, a full
 * one swaps. Screen readers get Move left / Move right instead.
 */
@Composable
fun Dock(
    apps: List<AppEntry?>,
    repository: AppRepository,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    showBar: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.all { it == null }) return
    // The slot being dragged from and how far; the app in the slot under it moves over to swap.
    var dragging by remember { mutableIntStateOf(-1) }
    var drag by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .then(if (showBar) Modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(32.dp)) else Modifier),
    ) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val slot = width / columns
        // Icons shrink to fit narrow columns.
        val icon = minOf(iconSize, with(density) { (slot * 0.85f).toDp() })
        val iconPx = with(density) { icon.toPx() }
        val rowHeight = iconPx + with(density) { 20.dp.toPx() }
        fun center(index: Int) = Offset((index % columns + 0.5f) * slot, (index / columns + 0.5f) * rowHeight)
        // Read when the drag ends too, not only from this composition: the gesture outlives it.
        val targetOf = {
            if (dragging < 0) -1 else {
                val at = center(dragging) + drag
                apps.indices.minBy { (center(it) - at).getDistanceSquared() }
            }
        }
        val target = targetOf()

        Box(Modifier.fillMaxWidth().height(with(density) { (rows * rowHeight).toDp() })) {
            apps.forEachIndexed { i, app ->
                if (app == null) return@forEachIndexed
                // Keyed by app, so its gesture and animations follow it to a new slot.
                key(app.key) {
                    val home = center(i)
                    // The app under the dragged one shows in the dragged one's slot, where it will go.
                    val swapping = dragging >= 0 && i == target && i != dragging
                    val shift by animateOffsetAsState(if (swapping) center(dragging) - home else Offset.Zero, label = "dock shift")
                    DockIcon(
                        app = app,
                        repository = repository,
                        size = icon,
                        lifted = i == dragging,
                        offset = { if (i == dragging) drag else shift },
                        canMoveLeft = i > 0,
                        canMoveRight = i < apps.lastIndex,
                        onMove = { by -> onMove(i, i + by) },
                        onDragStart = {
                            dragging = i
                            drag = Offset.Zero
                        },
                        onDrag = { drag += it },
                        onDragEnd = {
                            val to = targetOf()
                            if (dragging >= 0 && to != dragging) onMove(dragging, to)
                            dragging = -1
                            drag = Offset.Zero
                        },
                        onRemove = { onRemove(app) },
                        modifier = Modifier.offset { IntOffset((home.x - iconPx / 2).roundToInt(), (home.y - iconPx / 2).roundToInt()) },
                    )
                }
            }
        }
    }
}

/** Dock edits on app keys by slot, "" for an empty slot. Trailing empty slots are dropped. */
object DockSlots {
    /** Moves the app in [from] to [to], swapping with any app there. */
    fun move(slots: List<String>, from: Int, to: Int): List<String> {
        val s = slots.toMutableList()
        while (s.size <= maxOf(from, to)) s += ""
        s[from] = s[to].also { s[to] = s[from] }
        return s.trimmed()
    }

    fun remove(slots: List<String>, key: String) = slots.map { if (it == key) "" else it }.trimmed()

    /** Puts [key] in the first empty slot of the first [capacity]; unchanged if there is none. */
    fun add(slots: List<String>, key: String, capacity: Int): List<String> {
        val at = (0 until capacity).firstOrNull { slots.getOrNull(it).isNullOrEmpty() } ?: return slots
        return move(slots + key, slots.size, at)
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
    onMove: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.key, sizePx) { value = repository.icon(app, sizePx) }
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
                .graphicsLayer {
                    offset().let {
                        translationX = it.x
                        translationY = it.y
                    }
                    scaleX = scale
                    scaleY = scale
                }
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
                },
        ) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dock_remove)) },
                onClick = {
                    menu = false
                    onRemove()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_info)) },
                onClick = {
                    menu = false
                    repository.openAppInfo(app)
                },
            )
        }
    }
}
