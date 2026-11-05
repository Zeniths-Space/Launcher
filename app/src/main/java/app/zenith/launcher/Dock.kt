package app.zenith.launcher

import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The dock: up to [DockSize] apps in a row at the bottom of the home screen, icons only. Tap opens
 * an app. Long-press opens a menu (remove, app info); keep holding and drag sideways to move the
 * app, the others making room as it passes. Screen readers get Move left / Move right instead.
 */
@Composable
fun Dock(
    apps: List<AppEntry>,
    repository: AppRepository,
    showBar: Boolean,
    onReorder: (List<AppEntry>) -> Unit,
    onRemove: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return
    var slotWidth by remember { mutableFloatStateOf(1f) }
    // The app being dragged and how far; the others shift by a slot as it passes them.
    var dragging by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val targetOf = { if (dragging < 0) -1 else (dragging + (dragX / slotWidth).roundToInt()).coerceIn(0, apps.lastIndex) }
    val target = targetOf()
    val move = { from: Int, to: Int -> onReorder(apps.toMutableList().apply { add(to, removeAt(from)) }) }

    Row(
        modifier
            .fillMaxWidth()
            .then(if (showBar) Modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(32.dp)) else Modifier)
            .padding(vertical = 10.dp)
            .onSizeChanged { slotWidth = (it.width / apps.size.toFloat()).coerceAtLeast(1f) },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        apps.forEachIndexed { i, app ->
            // Where this app is drawn: following the finger, or a slot over to make room.
            val shift = when {
                i == dragging -> 0f
                dragging < 0 -> 0f
                i in (dragging + 1)..target -> -slotWidth
                i in target until dragging -> slotWidth
                else -> 0f
            }
            val animated by animateFloatAsState(shift, label = "dock shift")
            DockIcon(
                app = app,
                repository = repository,
                lifted = i == dragging,
                offsetX = { if (i == dragging) dragX else animated },
                canMoveLeft = i > 0,
                canMoveRight = i < apps.lastIndex,
                onMove = { by -> move(i, i + by) },
                onDragStart = {
                    dragging = i
                    dragX = 0f
                },
                onDrag = { dragX += it },
                onDragEnd = {
                    // Read now, not from this composition: the gesture outlives it.
                    val to = targetOf()
                    if (dragging >= 0 && to != dragging) move(dragging, to)
                    dragging = -1
                    dragX = 0f
                },
                onRemove = { onRemove(app) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockIcon(
    app: AppEntry,
    repository: AppRepository,
    lifted: Boolean,
    offsetX: () -> Float,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMove: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
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
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(IconSize)
                .onGloballyPositioned { coords[0] = it }
                .graphicsLayer {
                    translationX = offsetX()
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(app.key) { detectTapGestures(onTap = { repository.launch(app, bounds()) }) }
                .pointerInput(app.key) {
                    var travelled = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            travelled = 0f
                            menu = true
                            start()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            travelled += amount.x
                            // Moving it means rearranging, not choosing from the menu.
                            if (abs(travelled) > slop) menu = false
                            drag(amount.x)
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
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(IconSize)) }
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
