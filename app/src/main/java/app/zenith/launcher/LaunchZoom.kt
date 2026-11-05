package app.zenith.launcher

import android.animation.ValueAnimator
import android.graphics.Rect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** An app being opened: where its icon was (window pixels), the icon, and the colour its window opens with. */
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
 * app's window colour grows from the icon to the full screen with the icon at its centre, then the
 * app starts with the system animation turned off and appears where the zoom ended.
 * Timing measured from One UI on a Galaxy S24 Ultra: about 200 ms, decelerating.
 */
private const val ZoomMillis = 220
private val ZoomEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EndCorner = 28.dp
private const val StartAppAfterMillis = 100L
/** The icon is gone by this point of the zoom, leaving the plain window colour, as in One UI. */
private const val IconFadeEnd = 0.5f

@Composable
fun LaunchZoom(launching: Launching, iconSizePx: Float, onStart: () -> Unit) = key(launching) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            // The app's first frame takes about 100 ms to arrive, so it is started part way in and
            // lands as the zoom finishes, instead of adding the whole zoom to the launch time.
            delay(StartAppAfterMillis)
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
        val corner = lerp(b.width() * 0.3f, EndCorner.toPx(), f)
        drawRoundRect(launching.color, Offset(left, top), Size(right - left, bottom - top), CornerRadius(corner))
        launching.icon?.let { icon ->
            val s = iconSizePx.roundToInt()
            val cx = (left + right) / 2
            val cy = (top + bottom) / 2
            drawImage(
                icon,
                dstOffset = IntOffset((cx - s / 2f).roundToInt(), (cy - s / 2f).roundToInt()),
                dstSize = IntSize(s, s),
                alpha = (1f - f / IconFadeEnd).coerceIn(0f, 1f),
            )
        }
    }
}
