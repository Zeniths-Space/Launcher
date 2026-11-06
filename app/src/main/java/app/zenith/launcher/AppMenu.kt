package app.zenith.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** One action in an app's menu; the menu closes before it runs. [danger] marks one that removes something. */
internal class MenuAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * An app's long-press menu: its icon, name and App info, then a row of [actions].
 * It opens above the app, or below when there's no room above.
 */
@Composable
internal fun AppMenu(app: AppEntry, icon: ImageBitmap?, repository: AppRepository, actions: List<MenuAction>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val top = WindowInsets.statusBars.getTop(density) + margin
    val position = remember(margin, top) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.center.x - popupContentSize.width / 2)
                    .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val above = anchorBounds.top - margin - popupContentSize.height
                val y = if (above >= top) above else (anchorBounds.bottom + margin).coerceAtMost(windowSize.height - popupContentSize.height - margin)
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(shown, enter = fadeIn(Motion.effects(150)) + scaleIn(Motion.spatial(250), initialScale = 0.9f)) {
            MenuCard(app, icon, actions, onDismiss, onAppInfo = { repository.openAppInfo(app) })
        }
    }
}

@Composable
private fun MenuCard(app: AppEntry, icon: ImageBitmap?, actions: List<MenuAction>, onDismiss: () -> Unit, onAppInfo: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val run: (() -> Unit) -> Unit = { onDismiss(); it() }
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            // Room for the shadow, which the popup's window would otherwise cut off.
            .padding(8.dp)
            .width(MenuWidth)
            .shadow(8.dp, shape)
            .background(if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerHigh, shape),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp)) {
                icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(36.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.titleMedium,
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
            Row(
                Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
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
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(if (action.danger) cs.errorContainer else ZenithColors.indicator, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The label below names it.
            Icon(action.icon, contentDescription = null, tint = if (action.danger) cs.onErrorContainer else ZenithColors.onIndicator)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val MenuWidth = 296.dp
