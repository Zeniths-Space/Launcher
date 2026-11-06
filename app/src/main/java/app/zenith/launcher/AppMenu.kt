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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
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

/** Opens the rename dialog for an app; null where apps can't be renamed. Set by the launcher, so every app menu gets Rename. */
internal val LocalRenameApp = staticCompositionLocalOf<((AppEntry) -> Unit)?> { null }

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
    val headerPx = with(LocalDensity.current) { HeaderIconSize.roundToPx() }
    val header by produceState(icon, app.key) { if (value == null) value = repository.icon(app, headerPx) }
    val rename = LocalRenameApp.current
    val all = if (rename == null) actions else listOf(MenuAction(Icons.Edit, stringResource(R.string.menu_rename)) { rename(app) }) + actions
    MenuPopup(expanded, app.label, header, onInfo = { repository.openAppInfo(app) }, all, onDismiss)
}

/**
 * The popup behind [AppMenu], also used for widgets: [icon] and [title] with an info button
 * ([onInfo]) on top, then a row of [actions].
 */
@Composable
internal fun MenuPopup(
    expanded: Boolean,
    title: String,
    icon: ImageBitmap?,
    onInfo: (() -> Unit)?,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    hasIcon: Boolean = true,
) {
    // Stays composed after closing until the closing animation ends.
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val top = WindowInsets.statusBars.getTop(density) + margin
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
            title,
            icon,
            hasIcon,
            actions,
            onDismiss,
            onAppInfo = onInfo,
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
private fun MenuCard(title: String, icon: ImageBitmap?, hasIcon: Boolean, actions: List<MenuAction>, onDismiss: () -> Unit, onAppInfo: (() -> Unit)?, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val run: (() -> Unit) -> Unit = { onDismiss(); it() }
    val shape = RoundedCornerShape(28.dp)
    val pureBlack = LocalPureBlack.current
    Column(
        modifier
            // Room for the shadow, which the popup's window would otherwise cut off.
            .padding(8.dp)
            .width(maxOf(MenuWidth, ActionWidth * actions.size + 16.dp))
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
            // Room kept while the icon loads, so the header doesn't jump; none for a menu without one.
            if (hasIcon) {
                Box(Modifier.size(HeaderIconSize)) {
                    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(HeaderIconSize)) }
                }
                Spacer(Modifier.width(14.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onAppInfo != null) {
                IconButton(onClick = { run(onAppInfo) }) {
                    Icon(Icons.InfoOutline, contentDescription = stringResource(R.string.app_info), tint = cs.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(48.dp))
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

/**
 * Asks for a new name for [current] (an app or folder). Saving an empty name gives back [original],
 * the name it has of its own, which the empty field shows.
 */
@Composable
internal fun RenameDialog(current: String, original: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = { onSave(name.text); onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Edit, contentDescription = null) },
        title = { Text(stringResource(R.string.rename_title, current), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.text.length <= MaxNameLength) name = it },
                label = { Text(stringResource(R.string.rename_name)) },
                placeholder = { Text(original) },
                supportingText = { Text(stringResource(R.string.rename_blank, original)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = save) { Text(stringResource(R.string.rename_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rename_cancel)) } },
        containerColor = if (LocalPureBlack.current) PureBlackCard else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
    )
}

private const val MaxNameLength = 40

private val MenuWidth = 264.dp
private val ActionWidth = 64.dp

/** The header on the pure black card: a step lighter than [PureBlackCard]. */
private val PureBlackHeader = Color(0xFF262626)
private val HeaderIconSize = 42.dp
