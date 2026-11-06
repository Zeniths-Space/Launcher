package app.zenith.launcher

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** A folder on the home screen: tap opens it; long-press for its menu (Remove unless locked). */
@Composable
internal fun FolderIcon(
    folder: HomeFolder,
    apps: Map<String, AppEntry>,
    repository: AppRepository,
    icon: Dp,
    showLabels: Boolean,
    menu: Boolean,
    onMenuDismiss: () -> Unit,
    canTap: () -> Boolean,
    canRemove: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val label = folder.name.ifEmpty { stringResource(R.string.folder_default) }
    val count = folder.apps.count { it in apps }
    val description = pluralStringResource(R.plurals.folder_description, count, label, count)
    Box(modifier) {
        FolderFace(
            folder,
            apps,
            repository,
            icon,
            showLabels,
            Modifier
                .fillMaxSize()
                // A release after a long-press opens nothing: that press was for the menu or a drag.
                .pointerInput(folder.id) { detectTapGestures(onTap = { if (canTap()) onOpen() }) }
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    onClick { onOpen(); true }
                },
        )
        MenuPopup(
            menu,
            label,
            icon = null,
            onInfo = null,
            actions = buildList {
                // Takes the folder and its apps off the home screen; they stay installed.
                if (canRemove) add(MenuAction(Icons.Uninstall, stringResource(R.string.folder_remove), danger = true, onClick = onRemove))
            },
            onDismiss = onMenuDismiss,
            hasIcon = false,
        )
    }
}

/** A folder's look: its first four apps, small, on a tinted tile, and its name under it. */
@Composable
internal fun FolderFace(folder: HomeFolder, apps: Map<String, AppEntry>, repository: AppRepository, icon: Dp, showLabels: Boolean, modifier: Modifier) {
    val members = folder.apps.mapNotNull(apps::get).take(4)
    val pad = icon * 0.14f
    val gap = icon * 0.06f
    val mini = (icon - pad * 2 - gap) / 2
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(icon)
                .clip(RoundedCornerShape(icon * 0.3f))
                .background(Color.White.copy(alpha = 0.25f))
                .padding(pad),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                members.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { AppIcon(it, repository, mini) } }
                }
            }
        }
        if (showLabels) {
            Spacer(Modifier.height(6.dp))
            Text(
                folder.name.ifEmpty { stringResource(R.string.folder_default) },
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

@Composable
private fun AppIcon(app: AppEntry, repository: AppRepository, size: Dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, app.key, px) { value = repository.icon(app, px) }
    Box(Modifier.size(size)) { bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
}

/**
 * An open folder: its name (tap to rename, unless locked) and its apps, in a card over the dimmed
 * home screen. Tap an app to open it; long-press for its menu, with Remove from folder. Back or a
 * tap outside closes it and [onClose] gets the name as typed.
 */
@Composable
internal fun FolderPopup(
    expanded: Boolean,
    folder: HomeFolder,
    apps: Map<String, AppEntry>,
    repository: AppRepository,
    locked: Boolean,
    onClose: (name: String) -> Unit,
    onTakeOut: (key: String) -> Unit,
) {
    // Stays composed after closing until the closing animation ends.
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val close = { onClose(name) }
    val shown by rememberTransition(state, label = "folder").animateFloat(
        transitionSpec = { if (targetState) spring(dampingRatio = 0.85f, stiffness = 500f) else tween(160, easing = FastOutLinearInEasing) },
        label = "shown",
    ) { if (it) 1f else 0f }
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(28.dp)
    val members = folder.apps.mapNotNull(apps::get)
    Popup(popupPositionProvider = FullWindow, onDismissRequest = close, properties = PopupProperties(focusable = expanded)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { close() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        val scale = 0.85f + 0.15f * shown
                        scaleX = scale
                        scaleY = scale
                    }
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 400.dp)
                    .shadow(8.dp, shape)
                    .clip(shape)
                    .background(if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerHigh)
                    // Taps on the card stay on it.
                    .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
                    .padding(horizontal = 12.dp, vertical = 20.dp),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(MaxNameLength) },
                    enabled = !locked,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = cs.onSurface, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.Center) {
                            if (name.isEmpty()) {
                                Text(stringResource(R.string.folder_default), style = MaterialTheme.typography.titleLarge, color = cs.onSurfaceVariant)
                            }
                            field()
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
                members.chunked(FolderColumns).forEach { row ->
                    Row {
                        row.forEach { app ->
                            FolderApp(
                                app,
                                repository,
                                locked,
                                onOpen = {
                                    repository.launch(app, null)
                                    close()
                                },
                                onTakeOut = { onTakeOut(app.key) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(FolderColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderApp(app: AppEntry, repository: AppRepository, locked: Boolean, onOpen: () -> Unit, onTakeOut: () -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    val px = with(LocalDensity.current) { FolderIconSize.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.key, px) { value = repository.icon(app, px) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(FolderIconSize)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        AppMenu(
            menu,
            app,
            repository,
            buildList {
                // Locked: the layout can't change.
                if (!locked) add(MenuAction(Icons.MoveOut, stringResource(R.string.folder_take_out), onClick = onTakeOut))
                if (repository.canUninstall(app)) {
                    add(MenuAction(Icons.Uninstall, stringResource(R.string.menu_uninstall), danger = true) { repository.uninstall(app) })
                }
            },
            onDismiss = { menu = false },
            icon = icon,
        )
    }
}

// The popup covers its whole window.
private val FullWindow = object : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

private const val FolderColumns = 4
private const val MaxNameLength = 40
private val FolderIconSize = 52.dp
