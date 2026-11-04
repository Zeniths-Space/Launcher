package app.zenith.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import java.util.Date
import kotlin.math.roundToInt

/** Distance a vertical swipe must travel to open or close the drawer. */
private val SwipeThreshold = 72.dp

@Composable
fun LauncherScreen(repository: AppRepository, drawerOpen: Boolean, onDrawerOpenChange: (Boolean) -> Unit) {
    val apps by repository.apps.collectAsState()
    BackHandler(enabled = drawerOpen) { onDrawerOpenChange(false) }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(onOpenDrawer = { onDrawerOpenChange(true) })
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(Motion.spatial(350)) { it / 6 } + fadeIn(Motion.effects(200)),
            exit = slideOutVertically(Motion.effects(200)) { it / 6 } + fadeOut(Motion.effects(150)),
        ) {
            AppDrawer(apps, repository, onClose = { onDrawerOpenChange(false) })
        }
    }
}

/** The wallpaper with the time and date; swipe up anywhere for the app drawer. */
@Composable
private fun HomeScreen(onOpenDrawer: () -> Unit) {
    val threshold = with(LocalDensity.current) { SwipeThreshold.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var travelled = 0f
                detectVerticalDragGestures(
                    onDragStart = { travelled = 0f },
                    onVerticalDrag = { _, dy ->
                        travelled += dy
                        if (travelled < -threshold) {
                            travelled = 0f
                            onOpenDrawer()
                        }
                    },
                )
            }
            .safeDrawingPadding(),
    ) {
        Clock(Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 48.dp))
        Text(
            stringResource(R.string.home_swipe_hint),
            style = MaterialTheme.typography.labelMedium,
            color = OnWallpaper.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
    }
}

/** White text with a soft shadow reads on any wallpaper. */
private val OnWallpaper = androidx.compose.ui.graphics.Color.White
private val WallpaperShadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 8f)

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
 * All apps, alphabetical, with a search field. Typing filters; the keyboard's Enter opens the best
 * match. Long-press an app for App info. Back, home, or pulling down from the top closes it.
 */
@Composable
private fun AppDrawer(apps: List<AppEntry>?, repository: AppRepository, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) { apps?.let { searchApps(it, query) } }
    val grid = rememberLazyGridState()
    val keyboard = LocalSoftwareKeyboardController.current
    val threshold = with(LocalDensity.current) { SwipeThreshold.toPx() }
    // Pulling down while the grid is already at its top closes the drawer.
    val pullToClose = remember(onClose) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0) {
                    pulled += available.y
                    if (pulled > threshold) {
                        pulled = 0f
                        onClose()
                    }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    val open: (AppEntry, Rect?) -> Unit = { app, bounds ->
        keyboard?.hide()
        repository.launch(app, bounds)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.surface.copy(alpha = 0.98f))
            .safeDrawingPadding()
            .imePadding()
            .nestedScroll(pullToClose),
    ) {
        SearchField(
            query,
            onQuery = { query = it },
            onGo = { shown?.firstOrNull()?.let { open(it, null) } },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            shown == null -> Unit
            shown.isEmpty() -> Text(
                stringResource(R.string.drawer_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
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
                    AppTile(app, repository, onOpen = open)
                }
            }
        }
    }
    // A new search starts at the top of the results.
    LaunchedEffect(query) { if (shown?.isNotEmpty() == true) grid.scrollToItem(0) }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onGo: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val placeholder = stringResource(R.string.drawer_search)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                inner()
            }
        },
    )
}

/** Icon and name. Tap opens the app (the launch animation grows from the tile); long-press for App info. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: AppEntry, repository: AppRepository, onOpen: (AppEntry, Rect?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sizePx = with(LocalDensity.current) { IconSize.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.key, sizePx) { value = repository.icon(app, sizePx) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = { onOpen(app, bounds) }, onLongClick = { menu = true })
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(IconSize)
                    .onGloballyPositioned { c ->
                        val r = c.boundsInWindow()
                        bounds = Rect(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
                    },
            ) {
                icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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

private val IconSize = 52.dp
