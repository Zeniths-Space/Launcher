package app.zenith.launcher

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
 * A folder's Customize sheet, from the bottom edge (swipe down to close): Small, 2 by 2 or 3 by 3 cells, Apps or Folder style, and a colour. Each
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
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    // How far the sheet is pulled down; past a quarter of its height, or flung, it closes.
    var pull by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded) { if (expanded) pull = 0f }
    Popup(popupPositionProvider = FullWindow, onDismissRequest = onDismiss, properties = PopupProperties(focusable = expanded)) {
        Box(
            Modifier
                .fillMaxSize()
                // Light, so the folder stays in view and each change shows on it at once.
                .graphicsLayer { alpha = shown.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.2f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .onSizeChanged { sheetHeight = it.height }
                    .graphicsLayer { translationY = pull + (1f - shown) * size.height }
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .shadow(8.dp, shape)
                    .clip(shape)
                    .background(if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerHigh)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .draggable(
                        rememberDraggableState { pull = (pull + it).coerceAtLeast(0f) },
                        Orientation.Vertical,
                        onDragStopped = { velocity -> if (pull > sheetHeight / 4f || velocity > 1500f) onDismiss() else pull = 0f },
                    )
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 14.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(cs.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                )
                Text(stringResource(R.string.folder_customize), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
                Text(folder.name.ifEmpty { stringResource(R.string.folder_default) }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Section(stringResource(R.string.folder_size))
                Tiles(
                    listOf(stringResource(R.string.folder_size_small), stringResource(R.string.folder_size_2), stringResource(R.string.folder_size_3)),
                    selected = folder.span - 1,
                    onSelect = { i -> if (!onChange(folder.copy(span = i + 1))) Toast.makeText(context, noRoom, Toast.LENGTH_SHORT).show() },
                ) { i, tint -> Canvas(Modifier.size(20.dp)) { sizeGlyph(i + 1, tint) } }
                Section(stringResource(R.string.folder_style))
                Tiles(
                    listOf(stringResource(R.string.folder_style_apps), stringResource(R.string.folder_style_symbol)),
                    selected = folder.style.ordinal,
                    onSelect = { i -> onChange(folder.copy(style = FolderStyle.entries[i])) },
                ) { i, tint ->
                    if (i == 0) Canvas(Modifier.size(20.dp)) { appsGlyph(tint) }
                    else Icon(Icons.FolderFilled, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                // How a large folder's apps are laid out.
                if (folder.large && folder.style == FolderStyle.Apps) {
                    Section(stringResource(R.string.folder_layout))
                    Tiles(
                        listOf(stringResource(R.string.folder_layout_grid), stringResource(R.string.folder_layout_hexagon), stringResource(R.string.folder_layout_circle)),
                        selected = folder.layout.ordinal,
                        onSelect = { i -> onChange(folder.copy(layout = FolderLayout.entries[i])) },
                    ) { i, tint -> Canvas(Modifier.size(20.dp)) { layoutGlyph(FolderLayout.entries[i], tint) } }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(tileColor())
                        .toggleable(folder.transparent, role = Role.Switch) { onChange(folder.copy(transparent = it)) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.folder_transparent), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
                    Switch(checked = folder.transparent, onCheckedChange = null)
                }
                Section(stringResource(R.string.folder_color))
                // Ten colours: two rows of five, each swatch as wide as its share of the row.
                FolderColors.indices.chunked(5).forEachIndexed { r, row ->
                    if (r > 0) Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { i ->
                            val picked = i == folder.color
                            val corner by animateDpAsState(if (picked) 14.dp else 28.dp, label = "swatch")
                            val swatch = RoundedCornerShape(corner)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(swatch)
                                    // The default tint is see-through: show it over a dark disc, as on the wallpaper.
                                    .background(if (i == 0) Color(0xFF2A2A2A) else Color.Transparent)
                                    .background(FolderColors[i])
                                    .border(if (picked) 3.dp else 0.dp, if (picked) cs.primary else Color.Transparent, swatch)
                                    .selectable(picked, role = Role.RadioButton) { onChange(folder.copy(color = i)) }
                                    .semantics { contentDescription = colorNames[i] },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (picked) Icon(Icons.Done, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun tileColor() = if (LocalPureBlack.current) Color(0xFF262626) else MaterialTheme.colorScheme.surfaceContainerHighest

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

/** One choice of a few, as tiles with a small picture beside each label; the picked tile fills and rounds out. */
@Composable
private fun Tiles(options: List<String>, selected: Int, onSelect: (Int) -> Unit, glyph: @Composable (index: Int, tint: Color) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val idle = tileColor()
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val picked = i == selected
            val background by animateColorAsState(if (picked) cs.primaryContainer else idle, label = "tile")
            val content by animateColorAsState(if (picked) cs.onPrimaryContainer else cs.onSurfaceVariant, label = "tileContent")
            val corner by animateDpAsState(if (picked) 28.dp else 16.dp, label = "tileCorner")
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(background)
                    .selectable(picked, role = Role.RadioButton) { onSelect(i) }
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                glyph(i, content)
                Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A 3 by 3 grid of cells, the top-left [span] by [span] of them filled: the folder's share of the grid. */
private fun DrawScope.sizeGlyph(span: Int, tint: Color) {
    val gap = size.minDimension * 0.1f
    val cell = (size.minDimension - gap * 2) / 3
    for (r in 0 until 3) for (c in 0 until 3) {
        drawRoundRect(
            if (r < span && c < span) tint else tint.copy(alpha = 0.25f),
            topLeft = Offset(c * (cell + gap), r * (cell + gap)),
            size = Size(cell, cell),
            cornerRadius = CornerRadius(cell * 0.3f),
        )
    }
}

/** Four small app squares on a tile. */
private fun DrawScope.appsGlyph(tint: Color) {
    val s = size.minDimension
    drawRoundRect(tint.copy(alpha = 0.25f), cornerRadius = CornerRadius(s * 0.28f))
    val cell = s * 0.28f
    val gap = s * 0.1f
    val start = (s - cell * 2 - gap) / 2
    for (r in 0 until 2) for (c in 0 until 2) {
        drawRoundRect(tint, topLeft = Offset(start + c * (cell + gap), start + r * (cell + gap)), size = Size(cell, cell), cornerRadius = CornerRadius(cell * 0.3f))
    }
}

/** Dots placed as the [layout] places apps. */
private fun DrawScope.layoutGlyph(layout: FolderLayout, tint: Color) {
    val s = size.minDimension
    val dot = s * 0.09f
    val mid = Offset(s / 2, s / 2)
    fun ring(count: Int, radius: Float, start: Double) = repeat(count) { k ->
        val a = start + 2 * PI / count * k
        drawCircle(tint, dot, mid + Offset((cos(a) * radius).toFloat(), (sin(a) * radius).toFloat()))
    }
    when (layout) {
        FolderLayout.Grid -> for (r in 0 until 3) for (c in 0 until 3) drawCircle(tint, dot, Offset(s * (0.18f + 0.32f * c), s * (0.18f + 0.32f * r)))
        FolderLayout.Hexagon -> { drawCircle(tint, dot, mid); ring(6, s * 0.36f, 0.0) }
        FolderLayout.Circle -> ring(8, s * 0.4f, -PI / 2)
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
