package app.zenith.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
fun LauncherScreen(
    repository: AppRepository,
    motion: DrawerMotion,
    settings: LauncherSettings,
    drawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onGesture: (GestureAction) -> Unit,
    onDockChange: (List<String>) -> Unit,
) {
    val apps by repository.apps.collectAsState()
    // Dock keys by slot ("" empty); an app that is gone leaves its slot empty. Kept as stored until apps load.
    val dockKeys = remember(apps, settings.dock) {
        apps?.let { list -> list.mapTo(HashSet()) { it.key }.let { known -> settings.dock.map { if (it in known) it else "" } } } ?: settings.dock
    }
    val dockApps = remember(apps, dockKeys, settings.dockCapacity) {
        val byKey = apps.orEmpty().associateBy { it.key }
        List(settings.dockCapacity) { byKey[dockKeys.getOrNull(it)] }
    }
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
        if (!drawerOpen) keyboard?.hide()
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
            onOpenSettings = onOpenSettings,
            onDoubleTap = { onGesture(settings.doubleTap) },
            modifier = Modifier.graphicsLayer {
                val gone = (motion.progress / HomeFadeEnd).coerceIn(0f, 1f)
                alpha = 1f - gone
                translationY = -gone * HomeLift.toPx()
            },
        )
        if (settings.showDock) {
            Dock(
                apps = dockApps,
                repository = repository,
                columns = settings.dockColumns,
                rows = settings.dockRows,
                iconSize = settings.dockIconSize.dp,
                rowGap = settings.dockRowGap.dp,
                showBar = settings.dockBar,
                // Slots past a smaller grid stay as they were.
                onArrange = { shown -> onDockChange((shown.map { it?.key.orEmpty() } + dockKeys.drop(shown.size)).dropLastWhile { it.isEmpty() }) },
                onRemove = { app -> onDockChange(DockSlots.remove(dockKeys, app.key)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (settings.showHint) 48.dp else 16.dp)
                    .then(if (drawerShown) Modifier.clearAndSetSemantics {} else Modifier)
                    .graphicsLayer {
                        val gone = (motion.progress / HomeFadeEnd).coerceIn(0f, 1f)
                        alpha = 1f - gone
                        translationY = -gone * HomeLift.toPx()
                    },
            )
        }
        // Always composed, parked off screen while closed: building it when a swipe starts would
        // stall the main thread mid-gesture (measured ~270 ms in a debug build) and drop the fling.
        AppDrawer(
                apps,
                repository,
                settings.showLabels,
                dockKeys = dockKeys,
                dockCapacity = settings.dockCapacity,
                onDockChange = onDockChange,
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

/** The wallpaper with the time and date; long-press for a menu. (The swipe up is handled by [LauncherScreen].) */
@Composable
private fun HomeScreen(settings: LauncherSettings, onOpenSettings: () -> Unit, onDoubleTap: () -> Unit, modifier: Modifier) {
    val pickWallpaper = rememberWallpaperPicker()
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(settings.doubleTap) {
                // Double tap only listened for when it does something, so nothing waits on it otherwise.
                detectTapGestures(
                    onDoubleTap = if (settings.doubleTap != GestureAction.None) ({ _ -> onDoubleTap() }) else null,
                    onLongPress = { menuAt = it },
                )
            },
    ) {
        menuAt?.let { at ->
            HomeMenu(
                at = at,
                onDismiss = { menuAt = null },
                items = listOf(
                    MenuItem(Icons.Wallpaper, stringResource(R.string.settings_wallpaper)) { pickWallpaper() },
                    MenuItem(Icons.Settings, stringResource(R.string.home_menu_settings)) { onOpenSettings() },
                ),
            )
        }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (settings.showClock) Clock(Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 48.dp))
            if (settings.showHint) {
                Text(
                    stringResource(R.string.home_swipe_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnWallpaper.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                )
            }
        }
    }
}

private class MenuItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * The home screen's long-press menu: an M3 Expressive card at the finger, with the scalloped
 * badges of the settings page, springing open from the finger. Kept on screen: it opens above the
 * finger near the bottom and shifts in from the sides.
 */
@Composable
private fun HomeMenu(at: Offset, onDismiss: () -> Unit, items: List<MenuItem>) {
    val margin = with(LocalDensity.current) { 16.dp.roundToPx() }
    // Where the finger sits inside the card, as a fraction, so the card grows out of the finger.
    var origin by remember { mutableStateOf(TransformOrigin(0f, 0f)) }
    val position = remember(at) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val w = popupContentSize.width
                val h = popupContentSize.height
                val x = at.x.roundToInt().coerceIn(margin, (windowSize.width - w - margin).coerceAtLeast(margin))
                val above = at.y + h > windowSize.height - margin
                val y = if (above) at.y.roundToInt() - h else at.y.roundToInt()
                origin = TransformOrigin(((at.x - x) / w.coerceAtLeast(1)).coerceIn(0f, 1f), if (above) 1f else 0f)
                return IntOffset(x, y.coerceAtLeast(margin))
            }
        }
    }
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) { open.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .graphicsLayer {
                    val v = open.value
                    scaleX = 0.8f + 0.2f * v
                    scaleY = 0.8f + 0.2f * v
                    alpha = v.coerceIn(0f, 1f)
                    transformOrigin = origin
                },
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                items.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(start = 12.dp, end = 24.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScallopBadge(item.icon)
                        Spacer(Modifier.width(14.dp))
                        Text(item.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/** White text with a soft shadow reads on any wallpaper. */
private val OnWallpaper = Color.White
private val WallpaperShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 8f)

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
    dockKeys: List<String>,
    dockCapacity: Int,
    onDockChange: (List<String>) -> Unit,
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
    val open: (AppEntry, Rect?) -> Unit = { app, bounds ->
        keyboard?.hide()
        repository.launch(app, bounds)
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
                GridCells.Adaptive(76.dp),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.key }) { app ->
                    AppTile(
                        app,
                        repository,
                        showLabels,
                        inDock = app.key in dockKeys,
                        dockFull = !DockSlots.hasRoom(dockKeys, dockCapacity),
                        onDockToggle = {
                            onDockChange(if (app.key in dockKeys) DockSlots.remove(dockKeys, app.key) else DockSlots.add(dockKeys, app.key, dockCapacity))
                        },
                        onOpen = open,
                    )
                }
            }
        }
    }
    // A new search starts at the top of the results.
    LaunchedEffect(query) { if (shown?.isNotEmpty() == true) grid.scrollToItem(0) }
    // Each opening starts fresh: no old search, list at the top.
    LaunchedEffect(drawerOpen) { if (!drawerOpen) query = "" }
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

/** Icon and name. Tap opens the app (the launch animation grows from the tile); long-press for App info. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    repository: AppRepository,
    showLabels: Boolean,
    inDock: Boolean,
    dockFull: Boolean,
    onDockToggle: () -> Unit,
    onOpen: (AppEntry, Rect?) -> Unit,
) {
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
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
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = { onOpen(app, bounds()) }, onLongClick = { menu = true })
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(IconSize)
                    .onGloballyPositioned { iconCoords[0] = it },
            ) {
                icon?.let { Image(it, contentDescription = if (showLabels) null else app.label, modifier = Modifier.fillMaxSize()) }
            }
            if (showLabels) {
                Spacer(Modifier.height(6.dp))
                Text(
                    app.label,
                    style = MaterialTheme.typography.labelMedium.copy(shadow = WallpaperShadow),
                    color = OnWallpaper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (inDock || !dockFull) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (inDock) R.string.dock_remove else R.string.dock_add)) },
                    onClick = {
                        menu = false
                        onDockToggle()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_info)) },
                onClick = {
                    menu = false
                    repository.openAppInfo(app)
                },
            )
        }
    }
}

internal val IconSize = 52.dp
