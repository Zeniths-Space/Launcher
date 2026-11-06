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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** One action in the bottom row of an app's menu; the menu closes before it runs. */
internal class MenuAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * An app's long-press menu: its name and App info, the app's own shortcuts, then a row of
 * [actions]. It opens above the icon, or below when there's no room above.
 */
@Composable
internal fun AppMenu(app: AppEntry, repository: AppRepository, actions: List<MenuAction>, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val iconPx = with(density) { ShortcutIconSize.roundToPx() }
    val shortcuts by produceState(emptyList<AppShortcut>(), app.key) { value = repository.shortcuts(app, iconPx) }
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
            MenuCard(app, shortcuts, actions, onDismiss, onAppInfo = { repository.openAppInfo(app) }, onShortcut = { repository.startShortcut(it, null) })
        }
    }
}

@Composable
private fun MenuCard(
    app: AppEntry,
    shortcuts: List<AppShortcut>,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onShortcut: (AppShortcut) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val run: (() -> Unit) -> Unit = { onDismiss(); it() }
    Column(
        Modifier
            .width(MenuWidth)
            .clip(RoundedCornerShape(28.dp))
            .background(if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { run(onAppInfo) }) {
                Icon(Icons.Info, contentDescription = stringResource(R.string.app_info), tint = cs.onSurfaceVariant)
            }
        }
        shortcuts.forEach { shortcut ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { run { onShortcut(shortcut) } }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(ShortcutIconSize)) {
                    shortcut.icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(16.dp))
                Text(shortcut.label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(if (shortcuts.isEmpty()) 4.dp else 8.dp))
        if (actions.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(cs.onSurface.copy(alpha = 0.05f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                actions.forEach { action ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { run(action.onClick) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // The label below names it.
                        Icon(action.icon, contentDescription = null, tint = cs.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(action.label, style = MaterialTheme.typography.labelMedium, color = cs.onSurface, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}

private val MenuWidth = 280.dp
private val ShortcutIconSize = 32.dp
