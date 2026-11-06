package app.zenith.launcher

import android.app.WallpaperManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A settings category's colour: [container] behind its badge and value pills, [content] on it, and
 * [strong] for its sliders, switches and section titles. Fixed hues, so the pages stay colourful
 * on a grey wallpaper too.
 */
internal class Accent(val container: Color, val content: Color, val strong: Color, val onStrong: Color, val shape: Shape = ScallopShape)

/** Light and dark tones of one hue: a pastel and a deep shade, swapped between themes. */
private class Hue(val pastel: Color, val deep: Color, val darkPastel: Color, val darkDeep: Color) {
    fun accent(dark: Boolean, shape: Shape) =
        if (dark) Accent(darkPastel.copy(alpha = 0.32f), darkPastel, darkPastel, darkDeep, shape) else Accent(pastel, deep, deep, Color.White, shape)
}

private val Pink = Hue(Color(0xFFFFD9E2), Color(0xFF8E2451), Color(0xFFFFB0C8), Color(0xFF5E0B2C))
private val Blue = Hue(Color(0xFFD6E3FF), Color(0xFF1B4C9C), Color(0xFFAAC7FF), Color(0xFF0A2F6B))
private val Green = Hue(Color(0xFFC8F0C8), Color(0xFF1D5E2A), Color(0xFF9BD8A0), Color(0xFF003915))
private val Orange = Hue(Color(0xFFFFDDB8), Color(0xFF8A4B00), Color(0xFFFFB86E), Color(0xFF4A2800))
private val Purple = Hue(Color(0xFFEADDFF), Color(0xFF5B3D9E), Color(0xFFD0BCFF), Color(0xFF381E72))
private val Teal = Hue(Color(0xFFC2EEE9), Color(0xFF00574F), Color(0xFF8BD6CD), Color(0xFF003732))

private val Page.hue
    get() = when (this) {
        Page.Main, Page.Appearance -> Pink
        Page.Dock -> Blue
        Page.Home -> Green
        Page.Drawer -> Orange
        Page.Gestures -> Purple
        Page.About -> Teal
    }

internal val Page.accent: Accent
    @Composable @ReadOnlyComposable get() = hue.accent(LocalDark.current, shape)

/** Each category's badge outline: a different M3 Expressive shape per page. */
internal val Page.shape: Shape
    get() = when (this) {
        Page.Main, Page.Appearance -> ScallopShape
        Page.Dock -> RoundedCornerShape(14.dp)
        Page.Home -> polarShape(lobes = 4, depth = 0.22f, rotation = Math.PI / 4)
        Page.Drawer -> polarShape(lobes = 5, depth = 0.16f, rotation = -Math.PI / 2)
        Page.Gestures -> polarShape(lobes = 12, depth = 0.10f)
        Page.About -> CircleShape
    }

/** The accent of the page being drawn; null outside settings pages, where badges keep the theme's colours. */
internal val LocalAccent = staticCompositionLocalOf<Accent?> { null }

@Composable
internal fun AccentBadge(icon: ImageVector, accent: Accent, shape: Shape, size: Int = 44) {
    Box(Modifier.size(size.dp).background(accent.container, shape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = accent.content, modifier = Modifier.size((size * 0.48f).dp))
    }
}

/**
 * The top of the main page: a small picture of the home screen in the wallpaper's colours with the
 * dock's real icons, beside whether this is the home app and a way to change the wallpaper.
 */
@Composable
internal fun SettingsHero(
    repository: AppRepository,
    settings: LauncherSettings,
    isDefaultHome: Boolean,
    onChooseHome: () -> Unit,
    onWallpaper: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    // Opened from the launcher the list is already there; on its own, settings loads it once.
    LaunchedEffect(repository) { if (repository.apps.value == null) repository.load(context.packageName) }
    val apps by repository.apps.collectAsState()
    val dock = remember(apps, settings.dock, settings.labels) {
        val byKey = apps.orEmpty().associateBy { it.key }
        settings.dock.mapNotNull { byKey[it] }.take(4)
    }
    // The wallpaper's own colours (no permission needed), else the theme's.
    val wallpaper = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.let { c -> listOfNotNull(c.primaryColor, c.secondaryColor, c.tertiaryColor).map { Color(it.toArgb()) } }
        } else {
            null
        }
    }
    val gradient = wallpaper?.takeIf { it.size >= 2 } ?: listOf(cs.primaryContainer, cs.tertiaryContainer)
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // The little phone.
            Box(
                Modifier
                    .size(width = 92.dp, height = 168.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(gradient))
                    .clearAndSetSemantics {},
            ) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val sizePx = with(LocalDensity.current) { 16.dp.roundToPx() }
                    dock.forEach { app ->
                        val icon by produceState<ImageBitmap?>(repository.cachedIcon(app, sizePx), app.key) { value = repository.icon(app, sizePx) }
                        Box(Modifier.size(16.dp)) { icon?.let { Image(it, contentDescription = null) } }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 7.dp)
                        .size(width = 28.dp, height = 3.dp)
                        .background(Color.White.copy(alpha = 0.7f), CircleShape),
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                // Whether this is the home app; tapping goes to Android's home app setting.
                val status = if (isDefaultHome) Page.Home.accent else Page.Appearance.accent
                Surface(onClick = onChooseHome, shape = CircleShape, color = status.container) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isDefaultHome) Icons.Done else Icons.Home, contentDescription = null, tint = status.content, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (isDefaultHome) R.string.settings_hero_is_home else R.string.settings_hero_set_home),
                            style = MaterialTheme.typography.labelLarge,
                            color = status.content,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = onWallpaper) {
                    Icon(Icons.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_wallpaper))
                }
            }
        }
    }
}
