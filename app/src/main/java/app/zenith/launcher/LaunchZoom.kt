package app.zenith.launcher

import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** An app being opened: where its icon was (window pixels), the icon, and its splash colour. */
data class Launching(val app: AppEntry, val bounds: Rect, val icon: ImageBitmap?, val color: Color)

/** The app launch in progress, if any. Cleared when the launcher leaves the screen. */
class LaunchState {
    var current by mutableStateOf<Launching?>(null)
}

/** Whether the user allows animations (Developer options and Remove animations set the scale to 0). */
fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

/*
 * One UI Home grows the app's window out of its icon. Android keeps that animation for the system's
 * own launcher, so this draws the same zoom inside the launcher instead: a rounded rectangle in the
 * app's splash colour grows from the icon to the full screen while the icon fades into it. Just before
 * it fills the screen the app starts, with the system animation turned off; Android then shows
 * the app's own splash screen, whatever the app defines, so the launcher never draws a splash of
 * its own. (Drawing one, matched to each app's theme, stacked with apps that draw their own.)
 * Starting it earlier, as One UI snaps to full screen at about 80%, read as a stutter.
 * Measured from One UI on a Galaxy S24 Ultra (Play Store): the window grows steadily, about 55% at
 * 70 ms and 80% at 134 ms, then snaps to full when the app is ready (about 30 ms in when the app is
 * already running); the splash icon never fades.
 */
private const val ZoomMillis = 340
/**
 * Between 300 ms (felt snappy) and 420 ms (felt slow). Android's standard curve, not One UI's: that
 * one covers 30% in the first 35 ms, so the card leapt out of the icon and then crawled.
 */
private val ZoomEasing = FastOutSlowInEasing
/** Without a rounded-corner reading (before Android 12, or square screens) the zoom ends square. */
private val FallbackEndCorner = 0.dp
/**
 * Started this long before the zoom ends, about Android's delay before the app's splash shows, so
 * the splash replaces the card as it lands (98.5% grown) instead of after a still frame.
 */
private const val StartAppLeadMillis = 50L

@Composable
fun LaunchZoom(launching: Launching, iconSizePx: Float, onStart: () -> Unit) = key(launching) {
    val t = remember { Animatable(0f) }
    val view = LocalView.current
    // The card ends on the screen's own corner shape, so nothing shows round its corners when the
    // app's window, which fills the screen, replaces it.
    val endCorner = remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
        } else {
            null
        }
    }
    LaunchedEffect(Unit) {
        // If the app's window is slower than that, the finished zoom holds in its place.
        launch {
            delay(ZoomMillis - StartAppLeadMillis)
            onStart()
        }
        t.animateTo(1f, tween(ZoomMillis, easing = ZoomEasing))
    }
    val b = launching.bounds
    Canvas(Modifier.fillMaxSize()) {
        val f = t.value
        val left = lerp(b.left.toFloat(), 0f, f)
        val top = lerp(b.top.toFloat(), 0f, f)
        val right = lerp(b.right.toFloat(), size.width, f)
        val bottom = lerp(b.bottom.toFloat(), size.height, f)
        // Starts as the icon's own rounded shape, ends as a rounded screen.
        val corner = lerp(b.width() * 0.3f, endCorner ?: FallbackEndCorner.toPx(), f)
        drawRoundRect(launching.color, Offset(left, top), Size(right - left, bottom - top), CornerRadius(corner))
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        // Fades over the whole zoom: fading it early left an empty card travelling for 200 ms.
        val iconAlpha = 1f - f
        launching.icon?.takeIf { iconAlpha > 0f }?.let { icon ->
            // The icon keeps its size and fades in place at the centre of the growing card.
            val s = iconSizePx.roundToInt()
            drawImage(
                icon,
                dstOffset = IntOffset((cx - s / 2f).roundToInt(), (cy - s / 2f).roundToInt()),
                dstSize = IntSize(s, s),
                alpha = iconAlpha,
            )
        }
    }
}
