package com.elara.launcher

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Opens the rename dialog for an app; null where apps can't be renamed. Set by the launcher, so every app menu gets Rename. */
internal val LocalRenameApp = staticCompositionLocalOf<((AppEntry) -> Unit)?> { null }

/** One action in an app's menu; the menu closes before it runs. [danger] marks one that removes something. */
internal class MenuAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * An app's long-press menu: its icon, name and App info, then a list of [actions]. It opens above
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
    val header by produceState(icon, app.key, repository.iconEpoch) { if (value == null) value = repository.icon(app, headerPx) }
    val rename = LocalRenameApp.current
    val all = if (rename == null) actions else listOf(MenuAction(Icons.Edit, stringResource(R.string.menu_rename)) { rename(app) }) + actions
    // Read each time the menu opens: apps change their shortcuts at will.
    val shortcuts by produceState(emptyList<AppShortcut>(), expanded, app.key) { if (expanded) value = repository.shortcuts(app) }
    MenuPopup(
        expanded, app.label, header, onInfo = { repository.openAppInfo(app) }, all, onDismiss,
        top = if (shortcuts.isEmpty()) null else ({ run ->
            // Side by side, up to four: the actions below stay a list, so the menu stays short.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                shortcuts.forEach { s -> ShortcutTile(s, repository, Modifier.weight(1f)) { run { repository.startShortcut(s, null) } } }
            }
            HorizontalDivider(Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 3.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }),
    )
}

/** One of an app's shortcuts in its menu: the shortcut's own icon, its name under it. */
@Composable
private fun ShortcutTile(shortcut: AppShortcut, repository: AppRepository, modifier: Modifier, onClick: () -> Unit) {
    val px = with(LocalDensity.current) { ShortcutIconSize.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, shortcut.key, px) { value = repository.shortcutIcon(shortcut, px) }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // A light badge: shortcut icons are drawn for light launchers, and many are bare black glyphs.
        Box(Modifier.size(ShortcutIconSize).clip(CookieShape).background(ShortcutBadge), contentAlignment = Alignment.Center) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(ShortcutIconSize)) }
        }
        Text(
            shortcut.label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The popup behind [AppMenu], also used for widgets: [icon] and [title] with an info button
 * ([onInfo]) on top, then a list of [actions].
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
    /** Rows above the actions (an app's shortcuts); `run` closes the menu, then runs one. */
    top: (@Composable (run: (() -> Unit) -> Unit) -> Unit)? = null,
) {
    // Stays composed after closing until the closing animation ends.
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return
    val density = LocalDensity.current
    // The launcher's own window (full screen), which the drawer's blurred wallpaper is stretched over.
    val screen = LocalView.current.rootView.let { IntSize(it.width, it.height) }
    val glass by GlassWallpaper.image.collectAsState()
    val margin = with(density) { 8.dp.roundToPx() }
    val topEdge = WindowInsets.statusBars.getTop(density) + margin
    // Where the app is, as a point in the menu; set when the menu is placed, read when it's drawn.
    val origin = remember { floatArrayOf(0.5f, 1f) }
    val position = remember(margin, topEdge) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.center.x - popupContentSize.width / 2)
                    .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val above = anchorBounds.top - margin - popupContentSize.height
                val y = if (above >= topEdge) above else (anchorBounds.bottom + margin).coerceAtMost(windowSize.height - popupContentSize.height - margin)
                origin[0] = ((anchorBounds.center.x - x).toFloat() / popupContentSize.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                origin[1] = if (above >= topEdge) 1f else 0f
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
            top,
            onDismiss,
            onAppInfo = onInfo,
            glass = glass,
            screen = screen,
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
private fun MenuCard(title: String, icon: ImageBitmap?, hasIcon: Boolean, actions: List<MenuAction>, top: (@Composable (run: (() -> Unit) -> Unit) -> Unit)?, onDismiss: () -> Unit, onAppInfo: (() -> Unit)?, glass: ImageBitmap?, screen: IntSize, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalDark.current
    // Where the card is on screen, so it shows the part of the wallpaper that is behind it.
    var onScreen by remember { mutableStateOf(Offset.Zero) }
    val blurPx = with(LocalDensity.current) { MenuBlur.toPx() }
    val run: (() -> Unit) -> Unit = { onDismiss(); it() }
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            // Room for the shadow, which the popup's window would otherwise cut off.
            .padding(10.dp)
            // As wide as its widest line (title row or action), within these bounds.
            .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
            .width(IntrinsicSize.Max)
            .shadow(12.dp, shape)
            .clip(shape)
            .onGloballyPositioned { onScreen = it.positionOnScreen() }
            // Frosted glass: the blurred wallpaper behind the card, blurred more, under a tint for the text.
            // Android can't blur across windows on every phone (not on One UI), so the menu draws it itself.
            // Without the glass copy (blur off in settings), AMOLED black; the edge keeps it apart from a black drawer.
            .then(
                if (glass == null || screen.width == 0) Modifier.background(if (dark) Color.Black else cs.surfaceContainerHigh)
                else Modifier.drawWithCache {
                    val layer = obtainGraphicsLayer()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) layer.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    onDrawBehind {
                        val sx = glass.width.toFloat() / screen.width
                        val sy = glass.height.toFloat() / screen.height
                        layer.record {
                            drawImage(
                                glass,
                                srcOffset = IntOffset((onScreen.x * sx).roundToInt(), (onScreen.y * sy).roundToInt()),
                                srcSize = IntSize((size.width * sx).roundToInt().coerceAtLeast(1), (size.height * sy).roundToInt().coerceAtLeast(1)),
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                filterQuality = FilterQuality.Low,
                            )
                        }
                        drawLayer(layer)
                        drawRect(if (dark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.55f))
                    }
                },
            )
            .then(if (dark) Modifier.border(1.dp, Raised, shape) else Modifier)
            .padding(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Room kept while the icon loads, so the header doesn't jump; none for a menu without one.
            if (hasIcon) {
                Box(Modifier.size(HeaderIconSize)) {
                    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(HeaderIconSize)) }
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onAppInfo != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (dark) Raised else RaisedLight)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.app_info)) { run(onAppInfo) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.InfoOutline, contentDescription = stringResource(R.string.app_info), tint = cs.primary, modifier = Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.height(40.dp))
            }
        }
        top?.invoke(run)
        actions.forEach { action -> ActionButton(action) { run(action.onClick) } }
    }
}

@Composable
private fun ActionButton(action: MenuAction, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint = if (!action.danger) cs.primary else if (LocalDark.current) DangerDark else cs.error
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A small cookie behind the icon, as in the Elara forms; the label beside it names it.
        Box(Modifier.size(RowIconSize).background(tint.copy(alpha = 0.22f), CookieShape), contentAlignment = Alignment.Center) {
            Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
            color = if (action.danger) tint else cs.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 10.dp),
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
        containerColor = if (LocalDark.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
    )
}

private const val MaxNameLength = 40

private val MenuMinWidth = 264.dp
private val MenuMaxWidth = 320.dp
/** Rows sized for easy reading and tapping. */
private val RowHeight = 50.dp
private val RowIconSize = 38.dp
private val ShortcutIconSize = 46.dp
/** Extra blur over the wallpaper copy, which some blur styles leave sharp in places. */
private val MenuBlur = 16.dp

/** Raised parts on the black menu (its edge, the info button), from the Elara form colours. */
private val Raised = Color(0xFF1F1F1F)
private val RaisedLight = Color(0xFFE6E2EF)
/** Red for removing actions on black. */
private val DangerDark = Color(0xFFFF8A8A)
/** Behind shortcut icons, which apps draw for light backgrounds. */
private val ShortcutBadge = Color(0xFFEDE7FA)
/** The Elara "cookie": a circle with 8 soft lobes. */
private val CookieShape = polarShape(lobes = 8, depth = 0.1f)
private val HeaderIconSize = 46.dp
