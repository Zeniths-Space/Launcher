package app.zenith.launcher

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Switch
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
import androidx.compose.foundation.layout.offset
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
 * its name under it. A large folder shows its first apps three by three from the top-left, each
 * opening on tap ([onLaunch]); with more than nine, the last place shows the rest, small.
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
    val gap = tile * (if (large) 0.05f else 0.06f)
    val inner = (tile - pad * 2 - gap * (LargeColumns - 1)) / LargeColumns
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(tile)
                .clip(RoundedCornerShape(tile * (if (large) 0.2f else 0.3f)))
                .background(if (folder.transparent) Color.Transparent else folderColor(folder.color))
                .padding(pad),
            // A large folder's grid fills from the top-left; everything else sits in the middle.
            contentAlignment = if (large && folder.style == FolderStyle.Apps && folder.layout == FolderLayout.Grid) Alignment.TopStart else Alignment.Center,
        ) {
            when {
                folder.style == FolderStyle.Symbol -> Icon(Icons.FolderFilled, contentDescription = null, tint = Color.White, modifier = Modifier.size(tile * 0.5f))
                large && folder.layout == FolderLayout.Hexagon ->
                    Ring(members, repository, tile - pad * 2, around = 6, middle = true, iconShare = 0.29f, onLaunch = onLaunch)
                large && folder.layout == FolderLayout.Circle ->
                    // Spread evenly over however many there are, up to eight.
                    Ring(members, repository, tile - pad * 2, around = members.size.coerceIn(1, 8), middle = false, iconShare = 0.26f, onLaunch = onLaunch)
                large -> {
                    // Nine places: apps, the last one showing the rest when there are more than nine.
                    val places = LargeColumns * LargeColumns
                    val shown = if (members.size > places) members.take(places - 1) else members
                    val rest = if (members.size > places) members.drop(places - 1) else emptyList()
                    val items: List<@Composable () -> Unit> = shown.map { app ->
                        @Composable {
                            Box(Modifier.size(inner).pointerInput(app.key) { detectTapGestures(onTap = { onLaunch?.invoke(app) }) }) {
                                AppIcon(app, repository, inner)
                            }
                        }
                    } + if (rest.isEmpty()) emptyList() else listOf(@Composable { MiniGrid(rest, repository, inner) })
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        items.chunked(LargeColumns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { it() } } }
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

private const val LargeColumns = 3

/**
 * Apps evenly round a ring in [size], the first at the top, and the very first in the middle when
 * [middle]; icons take [iconShare] of [size], small enough that neighbours don't touch.
 */
@Composable
private fun Ring(members: List<AppEntry>, repository: AppRepository, size: Dp, around: Int, middle: Boolean, iconShare: Float, onLaunch: ((AppEntry) -> Unit)?) {
    val icon = size * iconShare
    val radius = (size - icon) / 2
    Box(Modifier.size(size)) {
        members.take(around + if (middle) 1 else 0).forEachIndexed { i, app ->
            val k = if (middle) i - 1 else i
            val angle = -PI / 2 + 2 * PI * k / around
            val dx = if (k < 0) 0f else cos(angle).toFloat()
            val dy = if (k < 0) 0f else sin(angle).toFloat()
            Box(
                Modifier
                    .offset(x = radius + radius * dx, y = radius + radius * dy)
                    .size(icon)
                    .pointerInput(app.key) { detectTapGestures(onTap = { onLaunch?.invoke(app) }) },
            ) { AppIcon(app, repository, icon) }
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
 * A folder's Customize card: Small, 2 by 2 or 3 by 3 cells, Apps or Folder style, and a colour. Each
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
                    listOf(stringResource(R.string.folder_size_small), stringResource(R.string.folder_size_2), stringResource(R.string.folder_size_3)),
                    selected = folder.span - 1,
                ) { i -> if (!onChange(folder.copy(span = i + 1))) Toast.makeText(context, noRoom, Toast.LENGTH_SHORT).show() }
                Spacer(Modifier.height(16.dp))
                Choice(
                    stringResource(R.string.folder_style),
                    listOf(stringResource(R.string.folder_style_apps), stringResource(R.string.folder_style_symbol)),
                    selected = folder.style.ordinal,
                ) { i -> onChange(folder.copy(style = FolderStyle.entries[i])) }
                // How a large folder's apps are laid out.
                if (folder.large && folder.style == FolderStyle.Apps) {
                    Spacer(Modifier.height(16.dp))
                    Choice(
                        stringResource(R.string.folder_layout),
                        listOf(stringResource(R.string.folder_layout_grid), stringResource(R.string.folder_layout_hexagon), stringResource(R.string.folder_layout_circle)),
                        selected = folder.layout.ordinal,
                    ) { i -> onChange(folder.copy(layout = FolderLayout.entries[i])) }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .toggleable(folder.transparent, role = Role.Switch) { onChange(folder.copy(transparent = it)) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.folder_transparent), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = folder.transparent, onCheckedChange = null)
                }
                Spacer(Modifier.height(12.dp))
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
    val bitmap by produceState(repository.cachedIcon(app, px), app.key, px) { value = repository.icon(app, px) }
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
    val icon by produceState(repository.cachedIcon(app, px), app.key, px) { value = repository.icon(app, px) }
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
