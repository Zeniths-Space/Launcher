package app.zenith.launcher

import android.app.WallpaperManager
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Places a picked photo ([getIntent] data) as the wallpaper: full screen, drag to move it, pinch to
 * zoom, with the dock drawn over it as on the home screen (the grid button hides it). Apply asks
 * for home screen, lock screen or both, then sets the part on screen. Android 9+ only (ImageDecoder).
 */
@RequiresApi(Build.VERSION_CODES.P)
class WallpaperActivity : ComponentActivity() {

    private val store by lazy { SettingsStore(this) }
    private val repository by lazy { AppRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent.data ?: return finish()
        lifecycleScope.launch { repository.load(packageName) }
        setContent {
            val settings by store.settings.collectAsState(LauncherSettings())
            ZenithTheme(pureBlack = settings.pureBlack) {
                WallpaperScreen(uri, settings, repository, onBack = ::finish, onApply = ::apply)
            }
        }
    }

    private fun apply(photo: android.graphics.Bitmap, crop: List<Int>, which: Int) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    WallpaperManager.getInstance(this@WallpaperActivity).setBitmap(photo, Rect(crop[0], crop[1], crop[2], crop[3]), true, which)
                    true
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false // Wallpaper changes blocked, e.g. by a device policy.
                }
            }
            Toast.makeText(this@WallpaperActivity, if (ok) R.string.wallpaper_set else R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
            if (ok) finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun WallpaperScreen(
    uri: Uri,
    settings: LauncherSettings,
    repository: AppRepository,
    onBack: () -> Unit,
    onApply: (android.graphics.Bitmap, List<Int>, Int) -> Unit,
) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(IntSize.Zero) }
    // The photo, decoded upright at about screen size once the screen size is known; null while loading.
    val photo by produceState<Result<android.graphics.Bitmap>?>(null, uri, screen) {
        if (screen == IntSize.Zero) return@produceState
        value = withContext(Dispatchers.IO) { runCatching { decode(context, uri, screen) } }
    }
    val image = remember(photo) { photo?.getOrNull()?.asImageBitmap() }
    val fit = remember(image, screen) { image?.let { WallpaperFit(it.width, it.height, screen.width, screen.height) } }
    var scale by remember(fit) { mutableFloatStateOf(fit?.cover ?: 1f) }
    var x by remember(fit) { mutableFloatStateOf(fit?.centred(scale)?.first ?: 0f) }
    var y by remember(fit) { mutableFloatStateOf(fit?.centred(scale)?.second ?: 0f) }
    var overlay by remember { mutableStateOf(true) }
    var choosing by remember { mutableStateOf(false) }
    if (photo?.isFailure == true) {
        LaunchedEffect(Unit) {
            Toast.makeText(context, R.string.wallpaper_failed, Toast.LENGTH_SHORT).show()
            onBack()
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { screen = it }) {
        if (image == null || fit == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image,
                    dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                    dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
                )
            }
            if (overlay) HomeOverlay(settings, repository)
            // Over the overlay, so the dock takes no touches: drag moves, pinch zooms around the fingers.
            Box(
                Modifier.fillMaxSize().pointerInput(fit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val to = fit.clampScale(scale * zoom)
                        val k = to / scale
                        val (cx, cy) = fit.clamp(centroid.x - (centroid.x - x) * k + pan.x, centroid.y - (centroid.y - y) * k + pan.y, to)
                        scale = to
                        x = cx
                        y = cy
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundButton(Icons.Previous, stringResource(R.string.back), onBack)
            if (fit != null) {
                RoundButton(Icons.Resize, stringResource(R.string.wallpaper_fit)) {
                    scale = fit.cover
                    fit.centred(scale).let { (cx, cy) -> x = cx; y = cy }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Apps, stringResource(if (overlay) R.string.wallpaper_hide_home else R.string.wallpaper_show_home)) { overlay = !overlay }
            Spacer(Modifier.size(12.dp))
            Button(onClick = { choosing = true }, enabled = fit != null, modifier = Modifier.weight(1f).height(56.dp)) {
                Text(stringResource(R.string.wallpaper_apply))
            }
        }
    }

    if (choosing && fit != null) {
        val bitmap = photo?.getOrNull() ?: return
        val set = { which: Int ->
            choosing = false
            onApply(bitmap, fit.visible(x, y, scale), which)
        }
        ModalBottomSheet(onDismissRequest = { choosing = false }) {
            Text(
                stringResource(R.string.wallpaper_where),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Column(Modifier.padding(bottom = 24.dp)) {
                Where(Icons.Home, stringResource(R.string.wallpaper_home)) { set(WallpaperManager.FLAG_SYSTEM) }
                Where(Icons.Lock, stringResource(R.string.wallpaper_lock)) { set(WallpaperManager.FLAG_LOCK) }
                Where(Icons.Wallpaper, stringResource(R.string.wallpaper_both)) { set(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }
            }
        }
    }
}

/** The dock and swipe hint where the home screen draws them, faded, so the photo can be placed around them. */
@Composable
private fun HomeOverlay(settings: LauncherSettings, repository: AppRepository) {
    val loaded by repository.apps.collectAsState()
    val dockApps = remember(loaded, settings.dock, settings.labels, settings.dockCapacity) {
        val byKey = loaded?.let { renameApps(it, settings.labels) }.orEmpty().associateBy { it.key }
        List(settings.dockCapacity) { byKey[settings.dock.getOrNull(it)] }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().alpha(0.85f)) {
        Spacer(Modifier.weight(1f))
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
                locked = true,
                onArrange = {},
                onRemove = {},
            )
        }
        // Room for the Apply row, which sits where the swipe hint would.
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun Where(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decode(context: android.content.Context, uri: Uri, screen: IntSize): android.graphics.Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetSampleSize(WallpaperFit.sampleSize(info.size.width, info.size.height, screen.width, screen.height))
    }
