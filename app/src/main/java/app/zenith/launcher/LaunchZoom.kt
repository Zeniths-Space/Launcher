package app.zenith.launcher

import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * An app being opened: where its icon was (window pixels), the icon, and the colour and icon its
 * splash screen shows.
 */
data class Launching(val app: AppEntry, val bounds: Rect, val icon: ImageBitmap?, val color: Color, val splashIcon: Drawable?)

/** The app launch in progress, if any. Cleared when the launcher leaves the screen. */
class LaunchState {
    var current by mutableStateOf<Launching?>(null)
}

/** Whether the user allows animations (Developer options and Remove animations set the scale to 0). */
fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

/*
 * One UI Home grows the app's window out of its icon. Android keeps that animation for the system's
 * own launcher, so this draws the same zoom inside the launcher instead: a rounded rectangle in the
 * app's splash colour grows from the icon to the full screen while the icon grows into the app's
 * splash icon. Only once it fills the screen does the app start, with the system animation turned
 * off, so Android's splash screen (requested with its icon) or the app's window replaces it at full
 * size. Starting during the zoom, as One UI's snap to full screen does, read as a stutter.
 * Measured from One UI on a Galaxy S24 Ultra (Play Store): the window grows steadily, about 55% at
 * 70 ms and 80% at 134 ms, then snaps to full when the app is ready (about 30 ms in when the app is
 * already running); the splash icon never fades.
 */
private const val ZoomMillis = 420
/** The shape of the One UI growth above (within 2% at 300 ms), stretched: 300 ms felt snappy. */
private val ZoomEasing = CubicBezierEasing(0.1f, 0.3f, 0.3f, 1f)
private val EndCorner = 28.dp
/**
 * Android's splash screen lays the icon out in a 108 dp box (an adaptive icon's size) and scales
 * that box to 192 dp when the icon has no background of its own, so fixed-size artwork grows too.
 */
private val SplashIconBox = 108.dp
private val SplashIconSize = 192.dp
/** The splash icon's artwork fills about two thirds of its box, so it starts larger than the tile. */
private const val SplashIconStartScale = 1.5f
/** Apps without a splash icon show their launcher icon, at the size Android gives it. */
private val LauncherIconSplashSize = 160.dp
/** The launcher icon hands over to the splash icon in the first part of the zoom, as in One UI. */
private const val IconSwapEnd = 0.35f

@Composable
fun LaunchZoom(launching: Launching, iconSizePx: Float, onStart: () -> Unit) = key(launching) {
    val t = remember { Animatable(0f) }
    val splashIcon = remember { launching.splashIcon?.mutate() }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(ZoomMillis, easing = ZoomEasing))
        // The finished zoom holds in the app's place until its window appears.
        onStart()
    }
    val b = launching.bounds
    Canvas(Modifier.fillMaxSize()) {
        val f = t.value
        val left = lerp(b.left.toFloat(), 0f, f)
        val top = lerp(b.top.toFloat(), 0f, f)
        val right = lerp(b.right.toFloat(), size.width, f)
        val bottom = lerp(b.bottom.toFloat(), size.height, f)
        // Starts as the icon's own rounded shape, ends as a rounded screen.
        val corner = lerp(b.width() * 0.3f, EndCorner.toPx(), f)
        drawRoundRect(launching.color, Offset(left, top), Size(right - left, bottom - top), CornerRadius(corner))
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        val swap = (f / IconSwapEnd).coerceIn(0f, 1f)
        val iconAlpha = if (splashIcon == null) 1f else 1f - swap
        val iconSize = if (splashIcon == null) lerp(iconSizePx, LauncherIconSplashSize.toPx(), f) else iconSizePx * (1f + f)
        launching.icon?.takeIf { iconAlpha > 0f }?.let { icon ->
            val s = iconSize.roundToInt()
            drawImage(
                icon,
                dstOffset = IntOffset((cx - s / 2f).roundToInt(), (cy - s / 2f).roundToInt()),
                dstSize = IntSize(s, s),
                alpha = iconAlpha,
            )
        }
        splashIcon?.let { d ->
            // shortcut: always the no-background size; apps that set windowSplashScreenIconBackgroundColor get 160 dp from Android.
            val box = SplashIconBox.toPx()
            val scale = lerp(iconSizePx * SplashIconStartScale, SplashIconSize.toPx(), f) / box
            d.setBounds(0, 0, box.roundToInt(), box.roundToInt())
            d.alpha = (swap * 255).roundToInt()
            drawIntoCanvas {
                val c = it.nativeCanvas
                c.save()
                c.translate(cx - box * scale / 2, cy - box * scale / 2)
                c.scale(scale, scale)
                d.draw(c)
                c.restore()
            }
        }
    }
}
