package com.elara.launcher

import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.FlowRow
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.grid.GridItemSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.CompositingStrategy
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
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
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
/** Light caught by the glass: brightest at the top edge, fading down, a faint lift at the bottom. */
private val GlassSheen = Brush.verticalGradient(
    0f to Color.White.copy(alpha = 0.16f),
    0.3f to Color.White.copy(alpha = 0.04f),
    0.75f to Color.Transparent,
    1f to Color.White.copy(alpha = 0.06f),
)
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
/** Icon scale while its menu is open; the press shrink (0.88) brings it near 1 while still held. */
private const val MenuLift = 1.2f
private const val HomeSink = 0.06f
private const val HomeEnterScale = 0.9f
private val HomeEnterSpring = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)

/** An app dragged out of the drawer onto the home screen or dock, and where the finger is, in the window. */
private class DrawerDrag(val app: AppEntry, val icon: ImageBitmap?, at: Offset) {
    var at by mutableStateOf(at)
}
private val DrawerSpring = spring<Float>(dampingRatio = 1f, stiffness = 400f)

@Composable
internal fun LauncherScreen(
    repository: AppRepository,
    motion: DrawerMotion,
    settings: LauncherSettings,
    drawerOpen: Boolean,
    resets: Int,
    entrances: Int,
    onDrawerOpenChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onSettings: (suspend SettingsStore.() -> Unit) -> Unit,
    onWallpaper: () -> Unit,
    onGesture: (GestureAction) -> Unit,
    onDockChange: (List<String>) -> Unit,
    onHomeChange: (List<List<String>>) -> Unit,
    onLayoutChange: (List<List<String>>, List<HomeFolder>) -> Unit,
    widgetHost: WidgetHost,
    onWidgetsChange: (List<HomeWidget>) -> Unit,
    onSwitchSpace: (Long) -> Unit,
    onNewSpace: () -> Unit,
    /** The blurred wallpaper (see [GlassWallpaper]) to show behind the drawer as glass, or null for the plain dim. */
    glassImage: ImageBitmap? = null,
    /** How [glassImage] was made: sets the dim over it and whether it catches light. */
    glassStyle: BlurStyle = BlurStyle.Glass,
    /** Set when the glass blur needs a new picture of the wallpaper: the drawer offers it. */
    onUpdateGlass: (() -> Unit)? = null,
) {
    val loaded by repository.apps.collectAsState()
    // Every app under the name the user gave it, so the drawer, search, home and dock agree.
    val apps = remember(loaded, settings.labels) { loaded?.let { renameApps(it, settings.labels) } }
    val appsByKey = remember(apps) { apps.orEmpty().associateBy { it.key } }
    // Home pages by key; an app that is gone leaves its slot empty. Kept as stored until apps load.
    val homePages = remember(apps, settings.home) {
        if (apps == null) settings.home else settings.home.map { page -> page.map { if (it in appsByKey || HomeFolders.isFolder(it)) it else "" } }
    }
    // Apps on the home screen, in slots or in folders.
    val homeKeys = remember(homePages, settings.folders) {
        homePages.flatten().filterTo(HashSet()) { it.isNotEmpty() && !HomeFolders.isFolder(it) } + HomeFolders.apps(settings.folders)
    }
    // App slots under large folders on a page, which apps and widgets keep out of.
    val largeOf = { p: Int -> HomeFolders.largeCells(homePages, settings.folders, p, settings.homeColumns, settings.homeRows) }
    // App slots under widgets and large folders, which apps added from the drawer skip.
    val blocked = { p: Int -> HomeWidgets.blocked(settings.widgets, p, settings.homeColumns, settings.homeRows) + largeOf(p) }
    var cell by remember { mutableStateOf(Size.Zero) }
    var pickingWidget by remember { mutableStateOf(false) }
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
    val dim = when {
        // Glass wins: pure black only blacks out the plain drawer, with the blur off.
        glassImage != null -> glassStyle.dim
        settings.pureBlack && isSystemInDarkTheme() -> 1f
        else -> WallpaperDim
    }
    var drawerDrag by remember { mutableStateOf<DrawerDrag?>(null) }
    // Read when an app from the drawer is let go: the screen, the home grid and the dock's slot finder.
    val rootAt = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val gridAt = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val dockSlotAt = remember { arrayOfNulls<(Offset) -> Int?>(1) }
    // Below the home grid is the dock; anywhere above it, the slot under the finger on the page shown.
    val dropApp = { app: AppEntry, at: Offset ->
        val grid = gridAt[0]?.takeIf { it.isAttached }
        if (grid != null && cell != Size.Zero) {
            if (settings.showDock && at.y > grid.boundsInWindow().bottom) {
                onDockChange(DockSlots.insert(dockKeys, app.key, dockSlotAt[0]?.invoke(at) ?: 0, settings.dockCapacity))
            } else {
                val p = grid.windowToLocal(at)
                val slot = (p.y / cell.height).toInt().coerceIn(0, settings.homeRows - 1) * settings.homeColumns +
                    (p.x / cell.width).toInt().coerceIn(0, settings.homeColumns - 1)
                // Already on the home screen: it moves here.
                val (pages, folders) = HomeFolders.removeApp(homePages, settings.folders, app.key)
                val placed = HomePages.insert(pages, app.key, homePage, slot, settings.homeCapacity, blocked)
                    ?: HomePages.add(pages, app.key, settings.homeCapacity, first = homePage, blocked = blocked)
                onLayoutChange(placed, folders)
            }
        }
    }

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
    // The home screen settles in from slightly smaller as it comes back into view, under the app closing.
    val enter = remember { Animatable(1f) }
    LaunchedEffect(entrances) {
        enter.snapTo(HomeEnterScale)
        enter.animateTo(1f, HomeEnterSpring)
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .onGloballyPositioned { rootAt[0] = it }
            // Dim drawn per frame, without recomposing.
            .drawBehind {
                val shown = motion.progress
                // Shut drawer: nothing to draw. Redrawn only when the drawer moves, never while it sits still.
                if (shown == 0f) return@drawBehind
                if (glassImage != null) {
                    // Small and pre-blurred; stretched over the screen with smooth filtering.
                    drawImage(glassImage, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = shown, filterQuality = FilterQuality.Low)
                }
                drawRect(Color.Black, alpha = dim * shown)
                if (glassImage != null && glassStyle.sheen) drawRect(GlassSheen, alpha = shown)
            }
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
            onWallpaper = onWallpaper,
            onOpenSettings = onOpenSettings,
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
                // Its folders go with it.
                val kept = homePages.filterIndexed { i, _ -> i != homePage }
                val inSlots = kept.flatten().mapNotNullTo(HashSet(), HomeFolders::idOf)
                onLayoutChange(kept, settings.folders.filter { it.id in inSlots })
                settings.widgets.filter { it.page == homePage }.forEach { widgetHost.remove(it.id) }
                onWidgetsChange(HomeWidgets.removePage(settings.widgets, homePage))
                homePage = homePage.coerceAtMost(homePages.size - 2)
            }) else null,
            onAddWidget = { pickingWidget = true },
            onDoubleTap = { onGesture(settings.doubleTap) },
            onSwitchSpace = onSwitchSpace,
            onNewSpace = onNewSpace,
            modifier = Modifier
                .then(if (drawerShown) Modifier.clearAndSetSemantics {} else Modifier)
                .graphicsLayer {
                    val gone = (motion.progress / HomeFadeEnd).coerceIn(0f, 1f)
                    alpha = 1f - gone
                    // Alpha per draw instead of a full-screen offscreen layer each frame (too slow at 60 Hz).
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    translationY = -gone * HomeLift.toPx()
                    // Sinks back a little as the drawer rises over it.
                    val scale = enter.value * (1f - gone * HomeSink)
                    scaleX = scale
                    scaleY = scale
                },
            grid = { onLongPressEmpty, modifier ->
                HomeGrid(
                    pages = homePages,
                    folders = settings.folders,
                    widgets = settings.widgets,
                    apps = appsByKey,
                    repository = repository,
                    widgetHost = widgetHost,
                    columns = settings.homeColumns,
                    rows = settings.homeRows,
                    iconSize = settings.homeIconSize.dp,
                    showLabels = settings.homeLabels,
                    showDots = settings.homeDots,
                    locked = settings.locked,
                    page = homePage,
                    onPageChange = { homePage = it },
                    onPagesChange = onHomeChange,
                    onLayoutChange = onLayoutChange,
                    onWidgetsChange = onWidgetsChange,
                    resets = resets,
                    onCellSize = { cell = it },
                    onLongPressEmpty = onLongPressEmpty,
                    onDoubleTap = if (settings.doubleTap != GestureAction.None) ({ onGesture(settings.doubleTap) }) else null,
                    modifier = modifier.onGloballyPositioned { gridAt[0] = it },
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
                        columnGap = settings.dockColumnGap.dp,
                        showBar = settings.dockBar,
                        locked = settings.locked,
                        // Slots past a smaller grid stay as they were.
                        onArrange = { shown -> onDockChange((shown.map { it?.key.orEmpty() } + dockKeys.drop(shown.size)).dropLastWhile { it.isEmpty() }) },
                        onRemove = { app -> onDockChange(DockSlots.remove(dockKeys, app.key)) },
                        onDropTarget = { dockSlotAt[0] = it },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            },
        )
        // Always composed, parked off screen while closed: building it when a swipe starts would
        // stall the main thread mid-gesture (measured ~270 ms in a debug build) and drop the fling.
        AppDrawer(
                remember(apps, settings.hidden) { apps?.filter { it.key !in settings.hidden } },
                repository,
                settings.showLabels,
                columns = settings.drawerColumns,
                iconSize = settings.drawerIconSize.dp,
                rowGap = settings.drawerRowGap.dp,
                sidePadding = settings.drawerPadding.dp,
                keepPosition = settings.drawerKeepPosition,
                contactsOn = settings.drawerContacts,
                searchBelow = settings.drawerSearchBelow,
                onSearchBelow = { on -> onSettings { setDrawerSearchBelow(on) } },
                currencyRates = settings.currencyRates,
                onCurrencyRates = { stored -> onSettings { setCurrencyRates(stored) } },
                onContactsOff = { onSettings { setDrawerContacts(false) } },
                homeKeys = homeKeys,
                locked = settings.locked,
                onPick = if (picking) ({ app ->
                    if (app.key !in homeKeys) onHomeChange(HomePages.add(homePages, app.key, settings.homeCapacity, first = homePage, blocked = blocked))
                    onDrawerOpenChange(false)
                }) else null,
                onHide = { app -> onSettings { setHidden(app.key, true) } },
                hiddenApps = remember(apps, settings.hidden) { apps.orEmpty().filter { it.key in settings.hidden } },
                onUnhide = { app -> onSettings { setHidden(app.key, false) } },
                onHomeToggle = { app ->
                    if (app.key in homeKeys) {
                        HomeFolders.removeApp(homePages, settings.folders, app.key).let { (p, f) -> onLayoutChange(p, f) }
                    } else {
                        onHomeChange(HomePages.add(homePages, app.key, settings.homeCapacity, blocked = blocked))
                    }
                },
                drawerOpen = drawerOpen,
                onPull = dragBy,
                onRelease = settle,
                isOpen = { motion.progress == 1f },
                onOpenSettings = onOpenSettings,
                onUpdateGlass = onUpdateGlass,
                // Dragged out: the drawer goes, showing the home screen to drop onto.
                onDragOut = { d ->
                    keyboard?.hide()
                    drawerDrag = d
                    motion.settle(scope, 0f, null)
                },
                onDragEnd = { d, placed ->
                    drawerDrag = null
                    if (placed) dropApp(d.app, d.at)
                    onDrawerOpenChange(false)
                },
                modifier = Modifier
                    .then(if (drawerShown) Modifier else Modifier.clearAndSetSemantics {})
                    .graphicsLayer {
                        val shown = ((motion.progress - HomeFadeEnd) / (1f - HomeFadeEnd)).coerceIn(0f, 1f)
                        alpha = shown
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        // Off screen while closed, so it takes no touches meant for the home screen.
                        translationY = if (motion.progress == 0f) 2 * size.height else (1f - shown) * DrawerRise * size.height
                    },
            )
        SpaceBanner(settings, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp))
        // The app dragged from the drawer, under the finger.
        drawerDrag?.let { d ->
            val size = settings.homeIconSize.dp
            Box(
                Modifier
                    .offset {
                        val at = rootAt[0]?.takeIf { it.isAttached }?.windowToLocal(d.at) ?: d.at
                        val half = size.toPx() / 2
                        IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt())
                    }
                    .size(size)
                    .graphicsLayer {
                        scaleX = 1.1f
                        scaleY = 1.1f
                    },
            ) { d.icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
        }
        // A new widget's spot, once Android has bound it and its setup screen (if any) is done:
        // read then, after the picker has gone, so it must stay current.
        val place by rememberUpdatedState { id: Int, info: AppWidgetProviderInfo ->
            val (w, h) = widgetSpan(info, cell, settings.homeColumns, settings.homeRows)
            val spot = HomeWidgets.place(id, w, h, homePages, settings.widgets, settings.homeColumns, settings.homeRows, first = homePage, also = largeOf)
            onWidgetsChange(settings.widgets + spot)
            if (spot.page >= homePages.size) onHomeChange(homePages + List(spot.page + 1 - homePages.size) { emptyList() })
            homePage = spot.page
        }
        if (pickingWidget) {
            WidgetPicker(
                host = widgetHost,
                appLabel = { pkg -> apps?.firstOrNull { it.packageName == pkg }?.label },
                span = { widgetSpan(it, cell, settings.homeColumns, settings.homeRows) },
                onPick = { info ->
                    pickingWidget = false
                    widgetHost.add(info) { id -> place(id, info) }
                },
                onDismiss = { pickingWidget = false },
            )
        }
    }
}

/**
 * The wallpaper with the time and date, the apps [grid] and the [dock], top to bottom; long-press or double-tap the wallpaper for the menu or gesture. (The swipe up is handled
 * by [LauncherScreen].) The grid reports long-presses on its empty slots, in window coordinates.
 */
@Composable
private fun HomeScreen(
    settings: LauncherSettings,
    resets: Int,
    onWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onSettings: (suspend SettingsStore.() -> Unit) -> Unit,
    onAddApp: () -> Unit,
    onAddPage: () -> Unit,
    onRemovePage: (() -> Unit)?,
    onAddWidget: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwitchSpace: (Long) -> Unit,
    onNewSpace: () -> Unit,
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
            grid({ menu = true }, Modifier.weight(1f).fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp))
            dock()
            Spacer(Modifier.height(16.dp))
        }
        // Composed once, a frame after the home screen, and kept: composing it on the long-press
        // took a whole frame (~35 ms) and the sheet stuttered as it rose.
        var sheetComposed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { sheetComposed = true }
        // Last, so it sits over the grid and dock and takes their touches.
        if (sheetComposed || menu) {
            HomeSheet(
                visible = menu,
                onDismiss = { menu = false },
                onWallpaper = onWallpaper,
                onMoreSettings = onOpenSettings,
                locked = settings.locked,
                onLockedChange = { on -> onSettings { setLocked(on) } },
                settings = settings,
                onSwitchSpace = onSwitchSpace,
                onNewSpace = onNewSpace,
                items = listOf(
                    MenuItem(Icons.Add, stringResource(R.string.home_menu_add_app), enabled = !settings.locked, onClick = onAddApp),
                    MenuItem(Icons.AddPage, stringResource(R.string.home_menu_add_page), enabled = !settings.locked, onClick = onAddPage),
                    MenuItem(Icons.Delete, stringResource(R.string.home_menu_remove_page), enabled = !settings.locked && onRemovePage != null) { onRemovePage?.invoke() },
                    MenuItem(Icons.Widgets, stringResource(R.string.home_menu_widgets), enabled = !settings.locked, onClick = onAddWidget),
                ),
            )
        }
    }
}

private class MenuItem(val icon: ImageVector, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The home screen's long-press menu: a sheet from the bottom edge, drawn here rather than M3's
 * ModalBottomSheet so it can stay composed while hidden. It shows the layout lock, a row of actions
 * (add app, add or remove page, widgets), then Wallpaper and More settings. Drag it down, tap
 * outside or press Back to close it.
 */
@Composable
private fun HomeSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onWallpaper: () -> Unit,
    onMoreSettings: () -> Unit,
    locked: Boolean,
    onLockedChange: (Boolean) -> Unit,
    items: List<MenuItem>,
    settings: LauncherSettings,
    onSwitchSpace: (Long) -> Unit,
    onNewSpace: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = constraints.maxHeight.toFloat()
        // How far the sheet's top sits below its open place, in px; starts off screen.
        var offset by remember { mutableFloatStateOf(screen) }
        var sheetHeight by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val hidden = sheetHeight.toFloat()
        var settling by remember { mutableStateOf<Job?>(null) }
        val settle = { velocity: Float ->
            settling?.cancel()
            val target = if (velocity > FlingSpeedPx || (velocity > -FlingSpeedPx && offset > hidden / 2)) hidden else 0f
            settling = scope.launch {
                animate(offset, target, velocity, SheetSpring) { v, _ -> offset = v }
                if (target == hidden) onDismiss()
            }
        }
        val close = { settle(FlingSpeedPx * 2) }
        val scroll = rememberScrollState()
        // Rises from the bottom edge once shown and measured; hidden, it waits off screen, back at its top.
        LaunchedEffect(visible, sheetHeight > 0) {
            settling?.cancel()
            offset = hidden.takeIf { it > 0f } ?: screen
            if (!visible) {
                scroll.scrollTo(0)
            } else if (sheetHeight > 0) {
                settling = launch { animate(offset, 0f, 0f, SheetSpring) { v, _ -> offset = v } }
            }
        }
        BackHandler(enabled = visible) { close() }
        val latestSettle by rememberUpdatedState(settle)
        // The list takes every drag; what it can't scroll moves the sheet: down to close, then back up.
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

        // Scrim: darker as the sheet rises; a tap outside closes it. Only there while shown, so a hidden sheet takes no touches.
        if (visible) Box(
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
                // Hidden, it is kept off screen; nothing in it should be read out.
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                .heightIn(max = with(density) { screen.toDp() } - WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .onSizeChanged { sheetHeight = it.height }
                .graphicsLayer {
                    // Parked a whole screen down, not by its height: a parked sheet that grows (its Space row or line
                    // changing) or a home screen that shrinks (the drawer coming and going) would otherwise bring its top
                    // back over the dock, where it took the dock's taps and the swipe up. Closing animates while still
                    // visible, so this only moves it once it's away.
                    translationY = if (visible) offset else screen
                    alpha = if (visible) 1f else 0f
                }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surface)
                .nestedScroll(drag)
                // Only scrolls where the screen is too short for it, as in landscape.
                .verticalScroll(scroll)
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
                    if (locked) {
                        Text(stringResource(R.string.home_menu_locked), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                // Filled while locked, so the state reads at a glance.
                FilledIconToggleButton(checked = locked, onCheckedChange = onLockedChange) {
                    Icon(
                        if (locked) Icons.Lock else Icons.LockOpen,
                        contentDescription = stringResource(if (locked) R.string.home_menu_unlock else R.string.home_menu_lock),
                    )
                }
            }
            if (settings.spaces.size >= 2) SpaceRow(settings, visible, onSwitchSpace = onSwitchSpace, onNew = { onDismiss(); onNewSpace() })
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
            SegmentedGroup(
                listOf(
                    {
                        GroupTile(
                            badge = { ScallopBadge(Icons.Wallpaper) },
                            title = stringResource(R.string.settings_wallpaper),
                            subtitle = stringResource(R.string.settings_wallpaper_sub),
                            onClick = { onDismiss(); onWallpaper() },
                        )
                    },
                    {
                        GroupTile(
                            badge = { ScallopBadge(Icons.Settings) },
                            title = stringResource(R.string.home_menu_more_settings),
                            subtitle = stringResource(R.string.home_menu_more_settings_sub),
                            onClick = { onDismiss(); onMoreSettings() },
                        )
                    },
                ),
            )
        }
    }
}

/**
 * The spaces as pills, the live one filled, and "+" for a new one (in settings); under them, what
 * the schedule does next, or that a hand-picked space is being held.
 */
@Composable
private fun SpaceRow(settings: LauncherSettings, visible: Boolean, onSwitchSpace: (Long) -> Unit, onNew: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDark.current
    val field = if (dark) Color(0xFF141414) else Color(0xFFF2F0F7)
    val brand = Color(0xFF7C4DFF)
    val held = stringResource(R.string.space_held)
    val nextFormat = stringResource(R.string.space_next_switch)
    // Worked out when the sheet opens or the spaces change, not on every frame: the sheet stays
    // composed while hidden, so the time must be read again each time it shows.
    val line = remember(visible, settings.spaces, settings.activeSpace, settings.spacesBySchedule, settings.spaceManualUntil) {
        when {
            !settings.spacesBySchedule || settings.spaces.none { it.scheduled } -> null
            settings.spaceManualUntil > System.currentTimeMillis() -> held
            else -> java.time.LocalDateTime.now().let { now ->
                Spaces.nextChange(settings.spaces, now)?.let { at ->
                    Spaces.scheduled(settings.spaces, at)?.let { nextFormat.format(it.name, Spaces.time(at.hour * 60 + at.minute)) }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.space_switch_label),
            style = MaterialTheme.typography.titleSmall,
            color = if (dark) Color(0xFFB69CFF) else brand,
            modifier = Modifier.padding(start = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.spaces.forEach { space ->
                val on = space.id == settings.activeSpace
                Row(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (on) brand else field)
                        .selectable(selected = on, role = Role.RadioButton) { if (!on) onSwitchSpace(space.id) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(space.icon.vector(), contentDescription = null, tint = if (on) Color.White else if (dark) Color(0xFFB69CFF) else brand, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(space.name, style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else colors.onSurface)
                }
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(field).clickable(role = Role.Button, onClick = onNew),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Add, contentDescription = stringResource(R.string.space_new), tint = if (dark) Color(0xFFB69CFF) else brand) }
        }
        line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
    }
}

/** The live space's name, briefly, at the top of the home screen when it changes (if [show]). */
@Composable
private fun SpaceBanner(settings: LauncherSettings, modifier: Modifier) {
    var shown by remember { mutableStateOf<Space?>(null) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(settings.activeSpace) {
        // Not for the space the launcher starts in.
        if (first) { first = false; return@LaunchedEffect }
        if (!settings.spacesShowName) return@LaunchedEffect
        shown = settings.spaces.firstOrNull { it.id == settings.activeSpace } ?: return@LaunchedEffect
        delay(2_500)
        shown = null
    }
    val alpha by animateFloatAsState(if (shown != null) 1f else 0f, tween(220), label = "space banner")
    var last by remember { mutableStateOf<Space?>(null) }
    shown?.let { last = it }
    val space = last ?: return
    if (alpha == 0f) return
    val until = stringResource(R.string.space_until, Spaces.time(space.to)).takeIf { space.scheduled && Spaces.covers(space, java.time.LocalDateTime.now()) }
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .height(44.dp)
            .padding(start = 8.dp, end = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(Color(0xFF7C4DFF).copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(space.icon.vector(), contentDescription = null, tint = Color(0xFFB69CFF), modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(space.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
        until?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9C9CF))
        }
    }
}

private val SheetSpring = spring<Float>(dampingRatio = 1f, stiffness = 400f)
private const val FlingSpeedPx = 1500f

/** White text with a soft shadow reads on any wallpaper. */
internal val OnWallpaper = Color.White
internal val WallpaperShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 8f)

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
        // A fling past an end overshoots it; outside 0..1 a negative alpha wraps to opaque, flashing the blur over home.
        job = scope.launch { animate(progress, to, speed ?: 0f, DrawerSpring) { v, _ -> progress = v.coerceIn(0f, 1f) } }
    }
}

/**
 * All apps, alphabetical, under an Apps title with search and settings buttons, over the dimmed
 * wallpaper. Search swaps the title for a search field; back or its close button swaps it back. Typing filters; the
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
    contactsOn: Boolean,
    /** Search is a bar under the apps instead of a button beside the title. */
    searchBelow: Boolean,
    onSearchBelow: (Boolean) -> Unit,
    onContactsOff: () -> Unit,
    currencyRates: String,
    onCurrencyRates: (String) -> Unit,
    homeKeys: Set<String>,
    locked: Boolean,
    onPick: ((AppEntry) -> Unit)?,
    onHide: (AppEntry) -> Unit,
    hiddenApps: List<AppEntry>,
    onUnhide: (AppEntry) -> Unit,
    onHomeToggle: (AppEntry) -> Unit,
    drawerOpen: Boolean,
    onPull: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    isOpen: () -> Boolean,
    onOpenSettings: () -> Unit,
    onUpdateGlass: (() -> Unit)?,
    onDragOut: (DrawerDrag) -> Unit,
    onDragEnd: (DrawerDrag, placed: Boolean) -> Unit,
    modifier: Modifier,
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val openNow by rememberUpdatedState(drawerOpen)
    val isDrawerOpen = remember { { openNow } }
    // Closing the drawer (back, home) hides them again.
    LaunchedEffect(drawerOpen) { if (!drawerOpen) showHidden = false }
    if (showHidden) HiddenAppsSheet(hiddenApps, repository, onUnhide, onDismiss = { showHidden = false })
    val unlockHidden = rememberDeviceAuth(stringResource(R.string.settings_hidden)) { showHidden = true }
    val focusManager = LocalFocusManager.current
    val shown = remember(apps, query) { apps?.let { searchApps(it, query) } }
    val context = LocalContext.current
    val home = remember { homeCurrency(context) }
    val rates = remember(home, currencyRates) { CurrencyRates.parse(home, currencyRates) }
    val answer = remember(query, rates) { answerFor(query, rates) }
    var contactsAllowed by remember { mutableStateOf(ContactSearch.allowed(context)) }
    // Checked again each time search opens: Android settings can take it away meanwhile.
    LaunchedEffect(searching) { if (searching) contactsAllowed = ContactSearch.allowed(context) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsAllowed = granted
        // Refused: stop offering; Settings > App drawer turns it back on.
        if (!granted) onContactsOff()
    }
    val lookContacts = searching && contactsOn && answer == null && query.trim().length >= 2
    val contacts by produceState(emptyList<ContactHit>(), query, lookContacts, contactsAllowed) {
        value = if (lookContacts && contactsAllowed) withContext(Dispatchers.IO) { ContactSearch.find(context, query) } else emptyList()
    }
    val topContact = remember(contacts, shown) { ContactSearch.top(contacts, shown.orEmpty(), query) }
    val moreContacts = remember(contacts, topContact) { contacts.filter { it != topContact } }
    val offerContacts = lookContacts && !contactsAllowed
    // Pages this phone has; Settings itself shows as an app, so its page is left to it.
    val settingsFound = remember(query, answer) {
        if (!searching || answer != null) emptyList() else SettingsSearch.find(query).filter { SettingsSearch.exists(context, it) }
    }
    val topSetting = remember(settingsFound, topContact, shown) {
        settingsFound.firstOrNull()?.takeIf { page ->
            topContact == null && SettingsSearch.strong(page, query) && shown.orEmpty().none { it.label.lowercase().startsWith(query.trim().lowercase()) }
        }
    }
    val moreSettings = remember(settingsFound, topSetting) { settingsFound.filter { it != topSetting } }
    // Read once per search, not per letter; only the home app gets them.
    val allShortcuts by produceState(emptyList<AppShortcut>(), searching, apps) {
        value = if (searching && apps != null) repository.shortcuts(apps) else emptyList()
    }
    val shortcuts = remember(allShortcuts, query, answer) {
        if (answer == null && query.trim().length >= 2) searchByLabel(allShortcuts, query) { it.label }.take(5) else emptyList()
    }
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
        val closeSearch = {
            // Else focus moves on to the first app, which then shows a highlight.
            focusManager.clearFocus()
            keyboard?.hide()
            query = ""
            searching = false
        }
        // A sum's answer is copied, a top contact called; otherwise the best match opens.
        val go = {
            when {
                answer != null -> if (answer.plain.isNotEmpty()) copyAnswer(context, answer.plain)
                topContact != null -> ContactSearch.dial(context, topContact)
                topSetting != null -> SettingsSearch.open(context, topSetting)
                else -> shown?.firstOrNull()?.let { open(it, null) }
                    ?: shortcuts.firstOrNull()?.let { keyboard?.hide(); repository.startShortcut(it, null) }
            }
            Unit
        }
        // Back closes the search before the drawer.
        BackHandler(enabled = searching, onBack = closeSearch)
        // One height either way, so the apps don't jump when the search opens.
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            if (searching && !searchBelow) {
                SearchField(query, onQuery = { query = it }, onGo = go, modifier = Modifier.weight(1f))
                IconButton(onClick = closeSearch) {
                    Icon(Icons.Close, contentDescription = stringResource(R.string.drawer_search_close), tint = OnWallpaper)
                }
            } else {
                Text(
                    stringResource(R.string.drawer_title),
                    style = MaterialTheme.typography.headlineMedium.copy(shadow = WallpaperShadow),
                    color = OnWallpaper,
                    modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
                )
                if (!searchBelow) IconButton(onClick = { searching = true }) {
                    Icon(Icons.Search, contentDescription = stringResource(R.string.drawer_search), tint = OnWallpaper)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Settings, contentDescription = stringResource(R.string.drawer_settings), tint = OnWallpaper)
                }
                Box {
                    IconButton(onClick = { showMore = true }) {
                        Icon(Icons.MoreVert, contentDescription = stringResource(R.string.drawer_more), tint = OnWallpaper)
                    }
                    MenuPopup(
                        showMore,
                        title = null,
                        icon = null,
                        onInfo = null,
                        actions = listOf(
                            MenuAction(Icons.Hide, stringResource(R.string.settings_hidden), onClick = unlockHidden),
                            MenuAction(Icons.Search, stringResource(R.string.drawer_search_below), checked = searchBelow) {
                                closeSearch()
                                onSearchBelow(!searchBelow)
                            },
                        ),
                        onDismiss = { showMore = false },
                        hasIcon = false,
                    )
                }
            }
        }
        if (onUpdateGlass != null && !searching && onPick == null) GlassPrompt(onUpdateGlass)
        if (onPick != null) {
            Text(
                stringResource(R.string.drawer_pick),
                style = MaterialTheme.typography.labelLarge.copy(shadow = WallpaperShadow),
                color = OnWallpaper,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        if (searching && answer != null) AnswerCard(
            answer,
            onQuery = { query = it },
            onSetRate = { r -> r.rate?.let { onCurrencyRates(CurrencyRates.format(rates.with(r.from, r.to, it))) } },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        )
        val people = topContact != null || moreContacts.isNotEmpty() || shortcuts.isNotEmpty() || settingsFound.isNotEmpty()
        // Above the grid, so they don't take the drawer's row spacing.
        if (topContact != null) ContactCard(topContact, Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp))
        if (topSetting != null) SettingCard(topSetting, Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp))
        if (people && shown?.isNotEmpty() == true) ResultLabel(stringResource(R.string.results_apps), Modifier.padding(horizontal = 16.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            shown == null -> Unit
            shown.isEmpty() && !people && !offerContacts -> if (answer == null) Text(
                stringResource(R.string.drawer_no_results),
                style = MaterialTheme.typography.bodyMedium.copy(shadow = WallpaperShadow),
                color = OnWallpaper.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            )
            else -> LazyVerticalGrid(
                GridCells.Fixed(columns),
                state = grid,
                modifier = Modifier.fillMaxSize().then(if (searchBelow) Modifier.bottomFade(SearchFadeHeight) else Modifier),
                // Extra room under the last row, so the end of the list clears the screen edge (or the search bar).
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 8.dp, bottom = if (searchBelow) SearchFadeHeight else 48.dp),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                // Results other than apps sit 16dp in from the screen edge, like the answer card.
                val inset = Modifier.padding(horizontal = (16.dp - sidePadding).coerceAtLeast(0.dp))
                items(shown, key = { it.key }) { app ->
                    AppTile(
                        app,
                        repository,
                        showLabels,
                        iconSize,
                        onHome = app.key in homeKeys,
                        canEdit = !locked,
                        isDrawerOpen = isDrawerOpen,
                        onHomeToggle = { onHomeToggle(app) },
                        onHide = { onHide(app) },
                        onOpen = open,
                        onDragOut = onDragOut,
                        onDragEnd = onDragEnd,
                    )
                }
                // One item each, label and list together, so only one row gap comes before it.
                if (shortcuts.isNotEmpty()) item(key = "shortcuts", span = { GridItemSpan(maxLineSpan) }) {
                    Column(inset, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResultLabel(stringResource(R.string.results_shortcuts))
                        ShortcutList(shortcuts, repository, onStart = { keyboard?.hide() })
                    }
                }
                if (moreSettings.isNotEmpty()) item(key = "settings", span = { GridItemSpan(maxLineSpan) }) {
                    Column(inset, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResultLabel(stringResource(R.string.results_settings))
                        SettingList(moreSettings)
                    }
                }
                if (moreContacts.isNotEmpty()) item(key = "contacts", span = { GridItemSpan(maxLineSpan) }) {
                    Column(inset, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResultLabel(stringResource(R.string.results_contacts))
                        ContactList(moreContacts)
                    }
                }
                if (offerContacts) item(key = "contacts-offer", span = { GridItemSpan(maxLineSpan) }) {
                    ContactsOffer(
                        onAllow = { askContacts.launch(Manifest.permission.READ_CONTACTS) },
                        onDismiss = onContactsOff,
                        modifier = inset,
                    )
                }
            }
        }
        // Always there, in reach of the thumb; a tap on it starts the search.
        if (searchBelow) SearchField(
            query,
            onQuery = { query = it },
            onGo = go,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = SearchBarBottom),
            bar = true,
            onFocus = { searching = true },
            onClose = closeSearch.takeIf { searching },
        )
        }
    }
    // A new search starts at the top of the results.
    LaunchedEffect(query) { if (shown?.isNotEmpty() == true) grid.scrollToItem(0) }
    // Each opening starts fresh: no old search, list at the top.
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) return@LaunchedEffect
        query = ""
        searching = false
        // The bar below stays on screen, so it would keep the focus.
        focusManager.clearFocus()
        if (!keepPosition) grid.scrollToItem(0)
    }
}

/**
 * The drawer's search field. As a [bar] it is the One UI pill: always shown, centred, a search
 * icon at its left; it waits for a tap ([onFocus]) and holds its close button ([onClose]) while searching.
 */
@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier,
    bar: Boolean = false,
    onFocus: () -> Unit = {},
    onClose: (() -> Unit)? = null,
) {
    val dark = LocalDark.current
    // The pill is solid, so its text follows the theme, not the wallpaper.
    val text = if (!bar) OnWallpaper else if (dark) Color.White else Color(0xFF18181B)
    val hint = if (!bar) OnWallpaper.copy(alpha = 0.7f) else if (dark) Color(0xFFB4B4B4) else Color(0xFF6B6B76)
    val style = if (bar) MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyLarge
    val placeholder = stringResource(if (bar) R.string.drawer_search_bar else R.string.drawer_search)
    // Opened by the search button, so it takes the keyboard at once.
    val focus = remember { FocusRequester() }
    if (!bar) LaunchedEffect(Unit) { focus.requestFocus() }
    val shape = RoundedCornerShape(26.dp)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = style.copy(color = text),
        cursorBrush = SolidColor(text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .then(
                if (bar) Modifier
                    .width(SearchBarWidth)
                    .height(52.dp)
                    .clip(shape)
                    .background(if (dark) Color(0xF2212121) else Color(0xF2F7F7F7))
                    .border(1.dp, if (dark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f), shape)
                    .padding(start = 16.dp, end = 4.dp)
                else Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(shape)
                    .background(OnWallpaper.copy(alpha = 0.14f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bar) Icon(Icons.Search, contentDescription = null, tint = hint, modifier = Modifier.padding(end = 8.dp).size(24.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(placeholder, style = style, color = hint, maxLines = 1)
                    inner()
                }
                if (onClose != null) IconButton(onClick = onClose) {
                    Icon(Icons.Close, contentDescription = stringResource(R.string.drawer_search_close), tint = hint)
                }
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

/**
 * Icon and name. Tap opens the app (the launch animation grows from the tile); long-press for its
 * menu. Keep holding and drag (unless locked) to take it out of the drawer to the home screen or dock.
 */
@Composable
private fun AppTile(
    app: AppEntry,
    repository: AppRepository,
    showLabels: Boolean,
    iconSize: Dp,
    onHome: Boolean,
    canEdit: Boolean,
    /** Read by an open menu only: a plain flag would recompose every tile as the drawer opens or shuts. */
    isDrawerOpen: () -> Boolean,
    onHomeToggle: () -> Unit,
    onHide: () -> Unit,
    onOpen: (AppEntry, Rect?) -> Unit,
    onDragOut: (DrawerDrag) -> Unit,
    onDragEnd: (DrawerDrag, placed: Boolean) -> Unit,
) {
    val sizePx = with(LocalDensity.current) { iconSize.roundToPx() }
    val icon by produceState(repository.cachedIcon(app, sizePx), app.key, sizePx, repository.iconEpoch) { value = repository.icon(app, sizePx) }
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
    if (menu) LaunchedEffect(Unit) {
        snapshotFlow(isDrawerOpen).first { !it }
        menu = false
    }
    val tileCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // The gesture below is set up once per app; these keep it on the latest values.
    val editable by rememberUpdatedState(canEdit)
    val out by rememberUpdatedState(onDragOut)
    val end by rememberUpdatedState(onDragEnd)
    val tileIcon by rememberUpdatedState(icon)
    val haptics = LocalHapticFeedback.current
    val slop = LocalViewConfiguration.current.touchSlop
    // The icon whose menu is open pops up and stays raised, so it's clear which app the menu is for.
    val lift by animateFloatAsState(if (menu) MenuLift else 1f, Motion.bouncy(), label = "lift")
    Box {
        AppTileContent(
            icon,
            app.label,
            showLabels,
            iconSize,
            Modifier
                .onGloballyPositioned { tileCoords[0] = it }
                // A release after a long-press opens nothing: that press was for the menu or a drag.
                // No ripple: the press scale is the feedback.
                .clickable(interactionSource = null, indication = null) { if (!menu) onOpen(app, bounds()) }
                .semantics { onLongClick { menu = true; true } }
                .pointerInput(app.key) {
                    var travelled = Offset.Zero
                    var dragged: DrawerDrag? = null
                    // In the window: the drawer slides away under the finger, so local positions would jump.
                    fun window(at: Offset) = tileCoords[0]?.takeIf { it.isAttached }?.localToWindow(at) ?: at
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            travelled = Offset.Zero
                            dragged = null
                            menu = true
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            travelled += amount
                            // Locked: the menu only.
                            if (!editable || (dragged == null && travelled.getDistance() <= slop)) return@detectDragGesturesAfterLongPress
                            menu = false
                            val at = window(change.position)
                            dragged?.let { it.at = at } ?: DrawerDrag(app, tileIcon, at).also { dragged = it; out(it) }
                        },
                        onDragEnd = { dragged?.let { end(it, true) } },
                        onDragCancel = { dragged?.let { end(it, false) } },
                    )
                }
                .pressScale(),
            iconModifier = Modifier
                .onGloballyPositioned { iconCoords[0] = it }
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                },
        )
        AppMenu(
            menu,
            app,
            repository,
            // Locked: the layout can't change, so no home action. The dock is arranged by dragging, not from here.
            buildList {
                if (canEdit) {
                    add(MenuAction(if (onHome) Icons.RemoveHome else Icons.AddHome, stringResource(if (onHome) R.string.menu_remove_home else R.string.menu_add_home), onClick = onHomeToggle))
                }
                // Out of the drawer and search; Settings > App drawer brings it back.
                add(MenuAction(Icons.Hide, stringResource(R.string.menu_hide), onClick = onHide))
                if (repository.canUninstall(app)) {
                    add(MenuAction(Icons.Uninstall, stringResource(R.string.menu_uninstall), danger = true) { repository.uninstall(app) })
                }
            },
            onDismiss = { menu = false },
            icon = icon,
        )
    }
}

/** A frosted pill under the drawer's title: the wallpaper changed, so the glass blur needs a new picture of it. */
@Composable
private fun GlassPrompt(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.BlurOn, contentDescription = null, tint = OnWallpaper)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.glass_update), style = MaterialTheme.typography.bodyMedium, color = OnWallpaper)
    }
}

/** How far up from the drawer's bottom edge the apps dim behind the search bar; also the room under the last row. */
private val SearchFadeHeight = 100.dp
/** The search pill as One UI 8 draws it: 244dp wide, centred, 25dp above the screen's bottom edge. */
private val SearchBarWidth = 244.dp
private val SearchBarBottom = 25.dp

/**
 * Dims the bottom [height] of the content more the lower it is, so a bar can float over a list.
 * The curve is One UI 8's drawer, measured: a quick drop above the bar, then a slow one to nearly gone.
 */
private fun Modifier.bottomFade(height: Dp): Modifier = this
    // Its own buffer, so the fade cuts into the apps alone, not the wallpaper behind.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = height.toPx().coerceAtMost(size.height)
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black, 0.25f to Color.Black.copy(alpha = 0.6f), 0.85f to Color.Black.copy(alpha = 0.15f), 1f to Color.Black.copy(alpha = 0.1f),
                startY = size.height - h, endY = size.height,
            ),
            topLeft = Offset(0f, size.height - h),
            size = Size(size.width, h),
            blendMode = BlendMode.DstIn,
        )
    }
