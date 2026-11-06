package app.zenith.launcher

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** One action in an app's menu; the menu closes before it runs. [danger] marks one that removes something. */
internal class MenuAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * An app's long-press menu: its icon, name and App info, then a row of [actions]. It opens above
 * the app, or below when there's no room above, growing out of the app and shrinking back into it.
 * [icon] is the app's icon if the caller has it; otherwise the menu draws its own.
 */
@Composable
internal fun AppMenu(
    expanded: Boolean,
    app: AppEntry,
    repository: AppRepository,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    icon: ImageBitmap? = null,
) {
    // Stays composed after closing until the closing animation ends.
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val top = WindowInsets.statusBars.getTop(density) + margin
    val headerPx = with(density) { HeaderIconSize.roundToPx() }
    val header by produceState(icon, app.key) { if (value == null) value = repository.icon(app, headerPx) }
    // Where the app is, as a point in the menu; set when the menu is placed, read when it's drawn.
    val origin = remember { floatArrayOf(0.5f, 1f) }
    val position = remember(margin, top) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.center.x - popupContentSize.width / 2)
                    .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val above = anchorBounds.top - margin - popupContentSize.height
                val y = if (above >= top) above else (anchorBounds.bottom + margin).coerceAtMost(windowSize.height - popupContentSize.height - margin)
                origin[0] = ((anchorBounds.center.x - x).toFloat() / popupContentSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                origin[1] = if (above >= top) 1f else 0f
                return IntOffset(x, y)
            }
        }
    }
    val transition = rememberTransition(state, label = "app menu")
    val shown by transition.animateFloat(
        transitionSpec = { if (targetState) spring(dampingRatio = 0.8f, stiffness = 500f) else tween(160, easing = FastOutLinearInEasing) },
        label = "shown",
    ) { if (it) 1f else 0f }
    // Not focusable while closing, so the taps that follow reach the screen.
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = expanded)) {
        MenuCard(
            app,
            header,
            actions,
            onDismiss,
            onAppInfo = { repository.openAppInfo(app) },
            modifier = Modifier.graphicsLayer {
                alpha = shown.coerceIn(0f, 1f)
                val scale = 0.8f + 0.2f * shown
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(origin[0], origin[1])
            },
        )
    }
}

@Composable
private fun MenuCard(app: AppEntry, icon: ImageBitmap?, actions: List<MenuAction>, onDismiss: () -> Unit, onAppInfo: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val run: (() -> Unit) -> Unit = { onDismiss(); it() }
    val shape = RoundedCornerShape(28.dp)
    val pureBlack = LocalPureBlack.current
    Column(
        modifier
            // Room for the shadow, which the popup's window would otherwise cut off.
            .padding(8.dp)
            .width(MenuWidth)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(if (pureBlack) PureBlackCard else cs.surfaceContainerHigh),
    ) {
        // The app's own section, a step lighter than the actions below it.
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (pureBlack) PureBlackHeader else cs.surfaceContainerHighest)
                .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(HeaderIconSize)) {
                icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(HeaderIconSize)) }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { run(onAppInfo) }) {
                Icon(Icons.InfoOutline, contentDescription = stringResource(R.string.app_info), tint = cs.onSurfaceVariant)
            }
        }
        if (actions.isNotEmpty()) {
            HorizontalDivider(color = cs.outlineVariant)
            Row(
                Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                actions.forEach { action -> ActionButton(action) { run(action.onClick) } }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionButton(action: MenuAction, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The label below names it.
        Icon(action.icon, contentDescription = null, tint = if (action.danger) cs.error else cs.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val MenuWidth = 264.dp

/** The header on the pure black card: a step lighter than [PureBlackCard]. */
private val PureBlackHeader = Color(0xFF262626)
private val HeaderIconSize = 42.dp
