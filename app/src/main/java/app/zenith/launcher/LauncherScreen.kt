package app.zenith.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlin.math.abs
import androidx.compose.ui.layout.positionInParent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Drawer motion, measured from One UI Home on a Galaxy S24 Ultra (screen recording, 120 Hz):
 * the drawer follows the finger over half the screen height. Home content fades out over the
 * first 30% of the swipe; then the drawer fades in while rising 7% of the screen height, over a
 * wallpaper dimmed to about 60%. On release it settles in about 300 ms without overshoot.
 */
private const val DragFraction = 0.5f
private const val HomeFadeEnd = 0.3f
private const val DrawerRise = 0.07f
private const val WallpaperDim = 0.4f
/** Release speeds above this (in drawer heights per second) open or close regardless of position. */
private const val FlingSpeed = 1.5f
/** A swipe down on the home screen this far, or this fast, is the swipe-down gesture. */
private val SwipeDownDistance = 48.dp
private val SwipeDownSpeed = 600.dp

/** The current drag on the home screen: how far it has pulled down while the drawer was shut. */
private class HomePull {
    var distance = 0f
    var startedClosed = false
}
private val HomeLift = 16.dp
private val DrawerSpring = spring<Float>(dampingRatio = 1f, stiffness = 400f)

@Composable
internal fun LauncherScreen(
    repository: AppRepository,
    motion: DrawerMotion,
    settings: LauncherSettings,
    drawerOpen: Boolean,
    resets: Int,
    onDrawerOpenChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSettings: (suspend SettingsStore.() -> Unit) -> Unit,
    allSettings: @Composable (startPage: Page, onBack: () -> Unit) -> Unit,
    homeAppCard: @Composable () -> Unit,
    onGesture: (GestureAction) -> Unit,
    onDockChange: (List<String>) -> Unit,
    onHomeChange: (List<List<String>>) -> Unit,
) {
    val apps by repository.apps.collectAsState()
    val appsByKey = remember(apps) { apps.orEmpty().associateBy { it.key } }
    // Home pages by key; an app that is gone leaves its slot empty. Kept as stored until apps load.
    val homePages = remember(apps, settings.home) {
        if (apps == null) settings.home else settings.home.map { page -> page.map { if (it in appsByKey) it else "" } }
    }
    val homeKeys = remember(homePages) { homePages.flatten().filterTo(HashSet()) { it.isNotEmpty() } }
    // Dock keys by slot ("" empty); an app that is gone leaves its slot empty. Kept as stored until apps load.
    val dockKeys = remember(apps, settings.dock) {
        apps?.let { list -> list.mapTo(HashSet()) { it.key }.let { known -> settings.dock.map { if (it in known) it else "" } } } ?: settings.dock
    }
    val dockApps = remember(apps, dockKeys, settings.dockCapacity) {
        val byKey = apps.orEmpty().associateBy { it.key }
        List(settings.dockCapacity) { byKey[dockKeys.getOrNull(it)] }
    }
    // The home page shown, and whether the drawer was opened to pick an app for it.
    var homePage by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var height by remember { mutableIntStateOf(1) }
    val range = height * DragFraction
    val drawerShown by remember { derivedStateOf { motion.progress > 0f } }
    val dim = if (settings.pureBlack && isSystemInDarkTheme()) 1f else WallpaperDim

    val dragBy: (Float) -> Unit = { dy -> motion.drag(-dy / range) }
    val homePull = remember { HomePull() }
    val swipeDownDistance = with(LocalDensity.current) { SwipeDownDistance.toPx() }
    val swipeDownSpeed = with(LocalDensity.current) { SwipeDownSpeed.toPx() }
    val settle: (Float) -> Unit = { velocityY ->
        val speed = -velocityY / range
        val open = when {
            speed > FlingSpeed -> true
            speed < -FlingSpeed -> false
            else -> motion.progress > 0.5f
        }
        motion.settle(scope, if (open) 1f else 0f, speed)
        if (open != drawerOpen) onDrawerOpenChange(open)
    }
    // Back or the home button (a release has already started its own settle).
    LaunchedEffect(drawerOpen) {
        if (!drawerOpen) {
            keyboard?.hide()
            picking = false
        }
        motion.settle(scope, if (drawerOpen) 1f else 0f, null)
    }
    BackHandler(enabled = drawerOpen) { onDrawerOpenChange(false) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            // Dim drawn per frame, without recomposing.
            .drawBehind { drawRect(Color.Black, alpha = dim * motion.progress) }
            .draggable(
                rememberDraggableState { dy ->
                    if (homePull.startedClosed && motion.progress == 0f && dy > 0f) homePull.distance += dy
                    dragBy(dy)
                },
                Orientation.Vertical,
                onDragStarted = {
                    homePull.distance = 0f
                    homePull.startedClosed = motion.progress == 0f
                },
                onDragStopped = { velocityY ->
                    // A swipe down that started on the home screen is the swipe-down gesture.
                    if (homePull.startedClosed && motion.progress == 0f &&
                        (homePull.distance > swipeDownDistance || velocityY > swipeDownSpeed)
                    ) {
                        onGesture(settings.swipeDown)
                    }
                    settle(velocityY)
                },
            ),
    ) {
        HomeScreen(
            settings,
            resets = resets,
            allSettings = allSettings,
            homeAppCard = homeAppCard,
            onSettings = onSettings,
            onAddApp = {
                picking = true
                onDrawerOpenChange(true)
            },
            onAddPage = {
                onHomeChange(homePages.ifEmpty { listOf(emptyList()) } + listOf(emptyList()))
                homePage = homePages.size.coerceAtLeast(1)
            },
            // The page shown goes, with its apps (they stay installed); there is always one page.
            onRemovePage = if (homePages.size > 1) ({
                onHomeChange(homePages.filterIndexed { i, _ -> i != homePage })
                homePage = homePage.coerceAtMost(homePages.size - 2)
            }) else null,
            onDoubleTap = { onGesture(settings.doubleTap) },
            modifier = Modifier
                .then(if (drawerShown) Modifier.clearAndSetSemantics {} else Modifier)
                .graphicsLayer {
                    val gone = (motion.progress / HomeFadeEnd).coerceIn(0f, 1f)
                    alpha = 1f - gone
                    translationY = -gone * HomeLift.toPx()
                },
            grid = { onLongPressEmpty, modifier ->
                HomeGrid(
                    pages = homePages,
                    apps = appsByKey,
                    repository = repository,
                    columns = settings.homeColumns,
                    rows = settings.homeRows,
                    iconSize = settings.homeIconSize.dp,
                    showLabels = settings.homeLabels,
                    showDots = settings.homeDots,
                    locked = settings.locked,
                    page = homePage,
                    onPageChange = { homePage = it },
                    onPagesChange = onHomeChange,
                    onLongPressEmpty = onLongPressEmpty,
                    onDoubleTap = if (settings.doubleTap != GestureAction.None) ({ onGesture(settings.doubleTap) }) else null,
                    modifier = modifier,
                )
            },
            dock = {
                if (settings.showDock) {
                    Dock(
                        apps = dockApps,
                        repository = repository,
                        columns = settings.dockColumns,
                        rows = settings.dockRows,
                        iconSize = settings.dockIconSize.dp,
                        rowGap = settings.dockRowGap.dp,
                        showBar = settings.dockBar,
                        locked = settings.locked,
                        // Slots past a smaller grid stay as they were.
                        onArrange = { shown -> onDockChange((shown.map { it?.key.orEmpty() } + dockKeys.drop(shown.size)).dropLastWhile { it.isEmpty() }) },
                        onRemove = { app -> onDockChange(DockSlots.remove(dockKeys, app.key)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            },
        )
        // Always composed, parked off screen while closed: building it when a swipe starts would
        // stall the main thread mid-gesture (measured ~270 ms in a debug build) and drop the fling.
        AppDrawer(
                apps,
                repository,
                settings.showLabels,
                columns = settings.drawerColumns,
                iconSize = settings.drawerIconSize.dp,
                rowGap = settings.drawerRowGap.dp,
                sidePadding = settings.drawerPadding.dp,
                keepPosition = settings.drawerKeepPosition,
                dockKeys = dockKeys,
                dockCapacity = settings.dockCapacity,
                onDockChange = onDockChange,
                homeKeys = homeKeys,
                locked = settings.locked,
                onPick = if (picking) ({ app ->
                    if (app.key !in homeKeys) onHomeChange(HomePages.add(homePages, app.key, settings.homeCapacity, first = homePage))
                    onDrawerOpenChange(false)
                }) else null,
                onHomeToggle = { app ->
                    onHomeChange(if (app.key in homeKeys) HomePages.remove(homePages, app.key) else HomePages.add(homePages, app.key, settings.homeCapacity))
                },
                drawerOpen = drawerOpen,
                onPull = dragBy,
                onRelease = settle,
                isOpen = { motion.progress == 1f },
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .then(if (drawerShown) Modifier else Modifier.clearAndSetSemantics {})
                    .graphicsLayer {
                        val shown = ((motion.progress - HomeFadeEnd) / (1f - HomeFadeEnd)).coerceIn(0f, 1f)
                        alpha = shown
                        // Off screen while closed, so it takes no touches meant for the home screen.
                        translationY = if (motion.progress == 0f) 2 * size.height else (1f - shown) * DrawerRise * size.height
                    },
            )
    }
}

/**
 * The wallpaper with the time and date, the apps [grid], the [dock] and the swipe hint, top to
 * bottom; long-press or double-tap the wallpaper for the menu or gesture. (The swipe up is handled
 * by [LauncherScreen].) The grid reports long-presses on its empty slots, in window coordinates.
 */
@Composable
private fun HomeScreen(
    settings: LauncherSettings,
    resets: Int,
    allSettings: @Composable (startPage: Page, onBack: () -> Unit) -> Unit,
    homeAppCard: @Composable () -> Unit,
    onSettings: (suspend SettingsStore.() -> Unit) -> Unit,
    onAddApp: () -> Unit,
    onAddPage: () -> Unit,
    onRemovePage: (() -> Unit)?,
    onDoubleTap: () -> Unit,
    modifier: Modifier,
    grid: @Composable (onLongPressEmpty: (Offset) -> Unit, modifier: Modifier) -> Unit,
    dock: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(resets) { menu = false }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(settings.doubleTap) {
                // Double tap only listened for when it does something, so nothing waits on it otherwise.
                detectTapGestures(
                    onDoubleTap = if (settings.doubleTap != GestureAction.None) ({ _ -> onDoubleTap() }) else null,
                    onLongPress = { menu = true },
                )
            },
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (settings.showClock) Clock(Modifier.padding(start = 28.dp, top = 48.dp, bottom = 16.dp))
            grid({ menu = true }, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp))
            dock()
            if (settings.showHint) {
                Text(
                    stringResource(R.string.home_swipe_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnWallpaper.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 16.dp),
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
        // Last, so it sits over the grid and dock and takes their touches.
        if (menu) {
            HomeSheet(
                onDismiss = { menu = false },
                allSettings = allSettings,
                homeAppCard = homeAppCard,
                locked = settings.locked,
                onLockedChange = { on -> onSettings { setLocked(on) } },
                items = listOf(
                    MenuItem(Icons.Add, stringResource(R.string.home_menu_add_app), enabled = !settings.locked, onClick = onAddApp),
                    MenuItem(Icons.AddPage, stringResource(R.string.home_menu_add_page), enabled = !settings.locked, onClick = onAddPage),
                    MenuItem(Icons.Delete, stringResource(R.string.home_menu_remove_page), enabled = !settings.locked && onRemovePage != null) { onRemovePage?.invoke() },
                    // shortcut: no widget hosting yet, so the tile is shown but off; wire it to AppWidgetHost with the widgets work.
                    MenuItem(Icons.Widgets, stringResource(R.string.home_menu_widgets), enabled = false) {},
                ),
            )
        }
    }
}

private class MenuItem(val icon: ImageVector, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The home screen's long-press menu: a sheet from the bottom edge, drawn here rather than M3's
 * ModalBottomSheet, whose stops can't be set. It opens far enough to show the layout lock, a row of
 * actions (add app, add or remove page, widgets) and the first settings categories; pulled up, it
 * follows the finger and the same list goes on with the rest of the settings. A category slides its
 * page in over the sheet ([allSettings]); back returns to the sheet. Drag it down, tap outside or
 * press Back to close it.
 */
@Composable
private fun HomeSheet(
    onDismiss: () -> Unit,
    allSettings: @Composable (startPage: Page, onBack: () -> Unit) -> Unit,
    homeAppCard: @Composable () -> Unit,
    locked: Boolean,
    onLockedChange: (Boolean) -> Unit,
    items: List<MenuItem>,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<Page?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = constraints.maxHeight.toFloat()
        // How far the sheet's top sits below its fully pulled-up place, in px; starts off screen.
        var offset by remember { mutableFloatStateOf(screen) }
        var sheetHeight by remember { mutableIntStateOf(0) }
        // Bottom of the first categories in the list; opened, the sheet shows down to there and its bottom margin.
        var headBottom by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val margin = with(density) { 24.dp.toPx() } + WindowInsets.navigationBars.getBottom(density)
        val open = (sheetHeight - headBottom - margin).coerceIn(0f, sheetHeight.toFloat())
        val hidden = sheetHeight.toFloat()
        var settling by remember { mutableStateOf<Job?>(null) }
        val settle = { velocity: Float ->
            settling?.cancel()
            val target = when {
                velocity > FlingSpeedPx -> if (offset < open - 1f) open else hidden
                velocity < -FlingSpeedPx -> if (offset > open + 1f) open else 0f
                else -> listOf(0f, open, hidden).minBy { abs(it - offset) }
            }
            settling = scope.launch {
                animate(offset, target, velocity, SheetSpring) { v, _ -> offset = v }
                if (target == hidden) onDismiss()
            }
        }
        val close = { settle(FlingSpeedPx * 2) }
        // The scroll connection below is made once; these keep it on the latest sizes.
        val latestSettle by rememberUpdatedState(settle)
        // Rises from the bottom edge once measured.
        LaunchedEffect(sheetHeight > 0 && headBottom > 0) {
            if (sheetHeight > 0 && headBottom > 0) {
                offset = hidden
                settling = launch { animate(offset, open, 0f, SheetSpring) { v, _ -> offset = v } }
            }
        }
        BackHandler(enabled = page == null) { close() }
        // A category's page: back to the sheet (its own sub-pages go back first, registered later).
        BackHandler(enabled = page != null) { page = null }
        // Drags move the sheet first; once it is fully up, the list scrolls, and back down the same way.
        val drag = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y >= 0f || offset <= 0f) return Offset.Zero
                    settling?.cancel()
                    val used = maxOf(available.y, -offset)
                    offset += used
                    return Offset(0f, used)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                    settling?.cancel()
                    val used = minOf(available.y, sheetHeight - offset)
                    offset += used
                    return Offset(0f, used)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (offset <= 0f) return Velocity.Zero
                    latestSettle(available.y)
                    return available
                }
            }
        }

        // Scrim: darker as the sheet rises; a tap outside closes it.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (hidden > 0f) (1f - offset / hidden).coerceIn(0f, 1f) else 0f }
                .background(Color.Black.copy(alpha = 0.4f))
                .pointerInput(Unit) { detectTapGestures { close() } }
                // Takes drags too, so they don't reach the home screen's swipe up behind it.
                .draggable(rememberDraggableState {}, Orientation.Vertical),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = with(density) { screen.toDp() } - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .onSizeChanged { sheetHeight = it.height }
                .graphicsLayer { translationY = offset }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surface)
                // A list too short to scroll passes its drags on: these move the sheet.
                .draggable(
                    rememberDraggableState { dy ->
                        settling?.cancel()
                        offset = (offset + dy).coerceIn(0f, hidden)
                    },
                    Orientation.Vertical,
                    onDragStopped = { velocity -> settle(velocity) },
                )
                .nestedScroll(drag)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Drag handle.
            Box(
                Modifier
                    .padding(vertical = 14.dp)
                    .size(32.dp, 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
            )
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_menu_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    Text(
                        stringResource(if (locked) R.string.home_menu_locked else R.string.home_menu_pull),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                // Filled while locked, so the state reads at a glance.
                FilledIconToggleButton(checked = locked, onCheckedChange = onLockedChange) {
                    Icon(
                        if (locked) Icons.Lock else Icons.LockOpen,
                        contentDescription = stringResource(if (locked) R.string.home_menu_unlock else R.string.home_menu_lock),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .graphicsLayer { alpha = if (item.enabled) 1f else 0.38f }
                            .clickable(enabled = item.enabled, role = Role.Button) {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(horizontal = 4.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ScallopBadge(item.icon)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val categories = categoryTiles("") { page = it }
            SegmentedGroup(categories.take(4), Modifier.onGloballyPositioned { headBottom = (it.positionInParent().y + it.size.height).roundToInt() })
            // The rest of the settings, below where the sheet first stops.
            Spacer(Modifier.height(8.dp))
            SegmentedGroup(categories.drop(4))
            Spacer(Modifier.height(8.dp))
            homeAppCard()
        }
        // A category's page, slid in over the sheet.
        AnimatedContent(
            page,
            transitionSpec = {
                (slideInHorizontally(tween(300)) { it / 10 } + fadeIn(tween(150, delayMillis = 75)))
                    .togetherWith(slideOutHorizontally(tween(300)) { it / 10 } + fadeOut(tween(75)))
            },
            label = "sheet page",
        ) { p ->
            if (p != null) Box(Modifier.fillMaxSize().background(colors.surface)) { allSettings(p) { page = null } }
        }
    }
}

private val SheetSpring = spring<Float>(dampingRatio = 1f, stiffness = 400f)
private const val FlingSpeedPx = 1500f

/** White text with a soft shadow reads on any wallpaper. */
internal val OnWallpaper = Color.White
internal val WallpaperShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 8f)

/**
 * Time and date. Updated by the system's minute tick (and time or zone changes), only while the
 * home screen is composed: no timer of its own.
 */
@Composable
private fun Clock(modifier: Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                now = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts only; not exported to other apps.
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        now = System.currentTimeMillis()
        onDispose { context.unregisterReceiver(receiver) }
    }
    val date = Date(now)
    // The configuration's locale, so a language change redraws the date.
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Column(modifier) {
        Text(
            DateFormat.getTimeFormat(context).format(date),
            style = MaterialTheme.typography.displayLarge.copy(shadow = WallpaperShadow),
            color = OnWallpaper,
        )
        Text(
            DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), date).toString(),
            style = MaterialTheme.typography.titleMedium.copy(shadow = WallpaperShadow),
            color = OnWallpaper,
        )
    }
}

/**
 * Drawer position, 0 (home) to 1 (open). A drag moves it at once, so a release right after the
 * last move can never be overtaken by a late drag update; a settle springs it to its end.
 */
class DrawerMotion {
    var progress by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null
    private var target = 0f

    fun drag(delta: Float) {
        job?.cancel()
        progress = (progress + delta).coerceIn(0f, 1f)
    }

    /** Straight to [to], no animation: for changes made while the home screen is not visible. */
    fun jump(to: Float) {
        job?.cancel()
        target = to
        progress = to
    }

    /**
     * Springs to [to] from the current position in [scope] (a composition scope, which has the
     * frame clock); [speed] (progress per second) null keeps a settle already heading there.
     */
    fun settle(scope: CoroutineScope, to: Float, speed: Float?) {
        if (speed == null && job?.isActive == true && target == to) return
        if (speed == null && job?.isActive != true && progress == to) return
        job?.cancel()
        target = to
        job = scope.launch { animate(progress, to, speed ?: 0f, DrawerSpring) { v, _ -> progress = v } }
    }
}

/**
 * All apps, alphabetical, with a search field, over the dimmed wallpaper. Typing filters; the
 * keyboard's Enter opens the best match. Long-press an app for App info. Pulling down from the
 * top of the list moves the drawer with the finger, as does the swipe that opened it.
 */
@Composable
private fun AppDrawer(
    apps: List<AppEntry>?,
    repository: AppRepository,
    showLabels: Boolean,
    columns: Int,
    iconSize: Dp,
    rowGap: Dp,
    sidePadding: Dp,
    keepPosition: Boolean,
    dockKeys: List<String>,
    dockCapacity: Int,
    onDockChange: (List<String>) -> Unit,
    homeKeys: Set<String>,
    locked: Boolean,
    onPick: ((AppEntry) -> Unit)?,
    onHomeToggle: (AppEntry) -> Unit,
    drawerOpen: Boolean,
    onPull: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    isOpen: () -> Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) { apps?.let { searchApps(it, query) } }
    val grid = rememberLazyGridState()
    val keyboard = LocalSoftwareKeyboardController.current
    // At the top of the list, a pull down moves the drawer instead; once it moves, it takes the
    // gesture both ways until released.
    val pullToClose = remember(onPull, onRelease) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || isOpen()) return Offset.Zero
                onPull(available.y)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                onPull(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isOpen()) return Velocity.Zero
                onRelease(available.y)
                return available
            }
        }
    }
    // Android plays its own open animation; the icon's bounds tell it where the app came from.
    // Picking for the home screen, a tap adds the app instead.
    val open: (AppEntry, Rect?) -> Unit = { app, bounds ->
        keyboard?.hide()
        if (onPick != null) onPick(app) else repository.launch(app, bounds)
    }
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .nestedScroll(pullToClose),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query,
                onQuery = { query = it },
                onGo = { shown?.firstOrNull()?.let { open(it, null) } },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                keyboard?.hide()
                onOpenSettings()
            }) {
                Icon(Icons.Settings, contentDescription = stringResource(R.string.drawer_settings), tint = OnWallpaper)
            }
        }
        if (onPick != null) {
            Text(
                stringResource(R.string.drawer_pick),
                style = MaterialTheme.typography.labelLarge.copy(shadow = WallpaperShadow),
                color = OnWallpaper,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        when {
            shown == null -> Unit
            shown.isEmpty() -> Text(
                stringResource(R.string.drawer_no_results),
                style = MaterialTheme.typography.bodyMedium.copy(shadow = WallpaperShadow),
                color = OnWallpaper.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            )
            else -> LazyVerticalGrid(
                GridCells.Fixed(columns),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                // Extra room under the last row, so the end of the list clears the screen edge.
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                items(shown, key = { it.key }) { app ->
                    AppTile(
                        app,
                        repository,
                        showLabels,
                        iconSize,
                        inDock = app.key in dockKeys,
                        dockFull = !DockSlots.hasRoom(dockKeys, dockCapacity),
                        onDockToggle = {
                            onDockChange(if (app.key in dockKeys) DockSlots.remove(dockKeys, app.key) else DockSlots.add(dockKeys, app.key, dockCapacity))
                        },
                        onHome = app.key in homeKeys,
                        canEdit = !locked,
                        drawerOpen = drawerOpen,
                        onHomeToggle = { onHomeToggle(app) },
                        onOpen = open,
                    )
                }
            }
        }
    }
    // A new search starts at the top of the results.
    LaunchedEffect(query) { if (shown?.isNotEmpty() == true) grid.scrollToItem(0) }
    // Each opening starts fresh: no old search, list at the top.
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) return@LaunchedEffect
        query = ""
        if (!keepPosition) grid.scrollToItem(0)
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onGo: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val placeholder = stringResource(R.string.drawer_search)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnWallpaper),
        cursorBrush = SolidColor(OnWallpaper),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(OnWallpaper.copy(alpha = 0.14f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = OnWallpaper.copy(alpha = 0.7f))
                inner()
            }
        },
    )
}

/** What a drawer tile draws: the icon, and the name under it. Shared with the settings preview. */
@Composable
internal fun AppTileContent(
    icon: ImageBitmap?,
    label: String,
    showLabels: Boolean,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                // Shrinks to the column, staying square, when the icon is wider than it.
                .widthIn(max = iconSize)
                .aspectRatio(1f)
                .then(iconModifier),
        ) {
            icon?.let { Image(it, contentDescription = if (showLabels) null else label, modifier = Modifier.fillMaxSize()) }
        }
        if (showLabels) {
            Spacer(Modifier.height(6.dp))
            Text(
                label,
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

/** Icon and name. Tap opens the app (the launch animation grows from the tile); long-press for App info. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    repository: AppRepository,
    showLabels: Boolean,
    iconSize: Dp,
    inDock: Boolean,
    dockFull: Boolean,
    onDockToggle: () -> Unit,
    onHome: Boolean,
    canEdit: Boolean,
    drawerOpen: Boolean,
    onHomeToggle: () -> Unit,
    onOpen: (AppEntry, Rect?) -> Unit,
) {
    val sizePx = with(LocalDensity.current) { iconSize.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.key, sizePx) { value = repository.icon(app, sizePx) }
    // Read at tap time only: a state written on every layout would recompose each tile per frame
    // while the drawer moves.
    val iconCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val bounds = {
        iconCoords[0]?.takeIf { it.isAttached }?.boundsInWindow()?.let { r ->
            Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
        }
    }
    var menu by remember { mutableStateOf(false) }
    // The drawer stays composed when shut; its menu must not outlive it.
    LaunchedEffect(drawerOpen) { if (!drawerOpen) menu = false }
    Box {
        AppTileContent(
            icon,
            app.label,
            showLabels,
            iconSize,
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = { onOpen(app, bounds()) }, onLongClick = { menu = true }),
            iconModifier = Modifier.onGloballyPositioned { iconCoords[0] = it },
        )
        AppMenu(
            menu,
            app,
            repository,
            // Locked: the layout can't change, so no home or dock actions.
            buildList {
                if (canEdit) {
                    add(MenuAction(if (onHome) Icons.RemoveHome else Icons.AddHome, stringResource(if (onHome) R.string.menu_remove_home else R.string.menu_add_home), onClick = onHomeToggle))
                }
                if (canEdit && (inDock || !dockFull)) {
                    add(MenuAction(Icons.DockBottom, stringResource(if (inDock) R.string.menu_remove_dock else R.string.menu_add_dock), onClick = onDockToggle))
                }
                if (repository.canUninstall(app)) {
                    add(MenuAction(Icons.Uninstall, stringResource(R.string.menu_uninstall), danger = true) { repository.uninstall(app) })
                }
            },
            onDismiss = { menu = false },
            icon = icon,
        )
    }
}
