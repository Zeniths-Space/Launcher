package app.zenith.launcher

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.platform.LocalContext
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

/**
 * A folder on the home screen: tap opens it (a large one's apps open directly); long-press for its
 * menu (Customize and Remove unless locked). [tile] is the tile's size: the icon size, or more for
 * a large folder.
 */
@Composable
internal fun FolderIcon(
    folder: HomeFolder,
    apps: Map<String, AppEntry>,
    repository: AppRepository,
    tile: Dp,
    large: Boolean,
    showLabels: Boolean,
    menu: Boolean,
    onMenuDismiss: () -> Unit,
    canTap: () -> Boolean,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onCustomize: () -> Unit,
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
            tile,
            large,
            showLabels,
            onLaunch = { app -> if (canTap()) repository.launch(app, null) },
            modifier = Modifier
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
                if (canEdit) add(MenuAction(Icons.Palette, stringResource(R.string.folder_customize), onClick = onCustomize))
                // Takes the folder and its apps off the home screen; they stay installed.
                if (canEdit) add(MenuAction(Icons.Uninstall, stringResource(R.string.folder_remove), danger = true, onClick = onRemove))
            },
            onDismiss = onMenuDismiss,
            hasIcon = false,
        )
    }
}

/**
 * A folder's look: a [tile] in its colour with small icons of its first apps, or a folder symbol;
 * its name under it. A large folder's first apps are full-size and open on tap ([onLaunch]); with
 * more than four, the last place shows the rest, small.
 */
@Composable
internal fun FolderFace(
    folder: HomeFolder,
    apps: Map<String, AppEntry>,
    repository: AppRepository,
    tile: Dp,
    large: Boolean,
    showLabels: Boolean,
    modifier: Modifier,
    onLaunch: ((AppEntry) -> Unit)? = null,
) {
    val members = folder.apps.mapNotNull(apps::get)
    val pad = tile * (if (large) 0.1f else 0.14f)
    val gap = tile * 0.06f
    val inner = (tile - pad * 2 - gap) / 2
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(tile)
                .clip(RoundedCornerShape(tile * (if (large) 0.2f else 0.3f)))
                .background(folderColor(folder.color))
                .padding(pad),
            contentAlignment = Alignment.Center,
        ) {
            when {
                folder.style == FolderStyle.Symbol -> Icon(Icons.FolderFilled, contentDescription = null, tint = Color.White, modifier = Modifier.size(tile * 0.5f))
                large -> {
                    // Four places: apps, the last one showing the rest when there are more than four.
                    val shown = if (members.size > 4) members.take(3) else members.take(4)
                    val rest = if (members.size > 4) members.drop(3) else emptyList()
                    val places: List<@Composable () -> Unit> = shown.map { app ->
                        @Composable {
                            Box(Modifier.size(inner).pointerInput(app.key) { detectTapGestures(onTap = { onLaunch?.invoke(app) }) }) {
                                AppIcon(app, repository, inner)
                            }
                        }
                    } + if (rest.isEmpty()) emptyList() else listOf(@Composable { MiniGrid(rest, repository, inner) })
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        places.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { it() } } }
                    }
                }
                else -> MiniGrid(members, repository, tile - pad * 2)
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

// Up to four apps, small, two by two in [size].
@Composable
private fun MiniGrid(members: List<AppEntry>, repository: AppRepository, size: Dp) {
    val gap = size * 0.08f
    val mini = (size - gap) / 2
    Column(Modifier.size(size), verticalArrangement = Arrangement.spacedBy(gap)) {
        members.take(4).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { AppIcon(it, repository, mini) } }
        }
    }
}

/** Folder tile colours by [HomeFolder.color]: 0 the default tint, then a few solid ones. */
internal val FolderColors = listOf(
    Color.White.copy(alpha = 0.25f),
    Color(0xFF5B8DEF), Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFFFFB300),
    Color(0xFFFF7043), Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFF9575CD), Color(0xFF546E7A),
)

internal fun folderColor(index: Int) = FolderColors.getOrElse(index) { FolderColors[0] }

/**
 * A folder's Customize card: Small or Large (2 by 2), Apps or Folder style, and a colour. Each
 * choice applies at once through [onChange], which says whether it could (a large folder needs
 * free cells).
 */
@Composable
internal fun FolderCustomize(expanded: Boolean, folder: HomeFolder, onChange: (HomeFolder) -> Boolean, onDismiss: () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return
    val shown by rememberTransition(state, label = "customize").animateFloat(
        transitionSpec = { if (targetState) spring(dampingRatio = 0.85f, stiffness = 500f) else tween(160, easing = FastOutLinearInEasing) },
        label = "shown",
    ) { if (it) 1f else 0f }
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val noRoom = stringResource(R.string.folder_no_room)
    val colorNames = FolderColors.indices.map { stringResource(R.string.folder_color_n, it + 1) }
    val shape = RoundedCornerShape(28.dp)
    Popup(popupPositionProvider = FullWindow, onDismissRequest = onDismiss, properties = PopupProperties(focusable = expanded)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
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
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(20.dp),
            ) {
                Text(stringResource(R.string.folder_customize), style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                Spacer(Modifier.height(20.dp))
                Choice(
                    stringResource(R.string.folder_size),
                    listOf(stringResource(R.string.folder_size_small), stringResource(R.string.folder_size_large)),
                    selected = if (folder.large) 1 else 0,
                ) { i -> if (!onChange(folder.copy(large = i == 1))) Toast.makeText(context, noRoom, Toast.LENGTH_SHORT).show() }
                Spacer(Modifier.height(16.dp))
                Choice(
                    stringResource(R.string.folder_style),
                    listOf(stringResource(R.string.folder_style_apps), stringResource(R.string.folder_style_symbol)),
                    selected = folder.style.ordinal,
                ) { i -> onChange(folder.copy(style = FolderStyle.entries[i])) }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.folder_color), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FolderColors.forEachIndexed { i, color ->
                        val picked = i == folder.color
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(if (picked) 3.dp else 1.dp, if (picked) cs.primary else cs.outlineVariant, CircleShape)
                                .padding(if (picked) 5.dp else 0.dp)
                                .clip(CircleShape)
                                // The default tint is see-through: show it over a dark disc, as on the wallpaper.
                                .background(if (i == 0) Color(0xFF2A2A2A) else Color.Transparent)
                                .background(color)
                                .selectable(picked, role = Role.RadioButton) { onChange(folder.copy(color = i)) }
                                .semantics { contentDescription = colorNames[i] },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
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
