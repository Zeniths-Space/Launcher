package app.zenith.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.UserManager
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.children
import kotlin.math.abs

/**
 * The home screen's widgets on Android's side: the host that keeps them updated (only while the
 * home screen is started), adding one (Android's permission prompt and the widget's own setup screen
 * when it has one), and the views that draw them. Create it as a property of the activity.
 */
class WidgetHost(private val activity: ComponentActivity) {
    val manager: AppWidgetManager by lazy { AppWidgetManager.getInstance(activity) }
    private val host by lazy { Host(activity) }
    private val views = HashMap<Int, WidgetView>()

    private class Pending(val id: Int, val info: AppWidgetProviderInfo, val onAdded: (Int) -> Unit)
    private var pending: Pending? = null

    // Registered at construction, as Android requires (before the activity starts).
    private val bind = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) configure() else finish(false)
    }

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    /** Every widget offered by the apps in each of the user's profiles. */
    fun providers(): List<AppWidgetProviderInfo> = activity.getSystemService(UserManager::class.java).userProfiles
        .flatMap { runCatching { manager.getInstalledProvidersForProfile(it) }.getOrNull().orEmpty() }

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    /** What the widget's menu and resize frame need to know; null if its app is gone. */
    fun details(id: Int): WidgetDetails? = info(id)?.let { info ->
        WidgetDetails(
            label = runCatching { info.loadLabel(activity.packageManager) }.getOrNull().orEmpty(),
            resizeWidth = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            resizeHeight = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
            minWidthPx = info.minResizeWidth.takeIf { it > 0 } ?: info.minWidth,
            minHeightPx = info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight,
        )
    }

    /** The widget's app icon at [sizePx], drawn off the main thread. */
    suspend fun icon(id: Int, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        info(id)?.let { info -> runCatching { info.loadIcon(activity, activity.resources.displayMetrics.densityDpi) }.getOrNull()?.toImageBitmap(sizePx) }
    }

    /** Binds a new widget for [info], asking the user if Android requires it, then runs its setup; [onAdded] once it's ready. */
    fun add(info: AppWidgetProviderInfo, onAdded: (Int) -> Unit) {
        val id = host.allocateAppWidgetId()
        pending = Pending(id, info, onAdded)
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configure()
        } else {
            bind.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
            )
        }
    }

    private fun configure() {
        val p = pending ?: return
        val optional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            p.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (p.info.configure == null || optional) return finish(true)
        // The result comes back through the activity's onActivityResult: see [onConfigured].
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, p.id, 0, ConfigureRequest, null) }
            .onFailure { finish(false) }
    }

    /** From the activity's onActivityResult; false if it wasn't the widget setup screen's result. */
    fun onConfigured(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != ConfigureRequest) return false
        finish(resultCode == Activity.RESULT_OK)
        return true
    }

    private fun finish(added: Boolean) {
        val p = pending ?: return
        pending = null
        if (added) p.onAdded(p.id) else host.deleteAppWidgetId(p.id)
    }

    fun remove(id: Int) {
        views.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    /** Frees widgets Android still holds for this launcher but the home screen no longer has. */
    fun forgetAllBut(ids: Set<Int>) {
        val held = runCatching { host.appWidgetIds }.getOrNull() ?: return
        held.filter { it !in ids }.forEach { runCatching { host.deleteAppWidgetId(it) } }
    }

    fun openAppInfo(id: Int) {
        val info = info(id) ?: return
        runCatching {
            activity.getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(ComponentName(info.provider.packageName, info.provider.className), info.profile, null, null)
        }
    }

    internal fun view(id: Int): WidgetView = views.getOrPut(id) { host.createView(activity, id, info(id)) as WidgetView }

    /** Tells the widget its size, in dp, so it can lay itself out for it. */
    internal fun resize(view: WidgetView, widthDp: Float, heightDp: Float) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
            }
        }
    }

    private class Host(context: Context) : AppWidgetHost(context, HostId) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = WidgetView(context)
    }

    private companion object {
        const val HostId = 1
        const val ConfigureRequest = 1001
    }
}

/** A widget's name, which ways it can be resized, and its smallest size in pixels. */
class WidgetDetails(val label: String, val resizeWidth: Boolean, val resizeHeight: Boolean, val minWidthPx: Int, val minHeightPx: Int)

/** A widget's long-press menu: its name and app info, then Resize and Remove unless the layout is locked. */
@Composable
internal fun WidgetMenu(expanded: Boolean, id: Int, host: WidgetHost, locked: Boolean, onResize: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    // Looked up when first opened, then kept for the closing animation.
    val details = remember(id) { mutableStateOf<WidgetDetails?>(null) }
    if (expanded && details.value == null) details.value = host.details(id)
    val iconPx = with(LocalDensity.current) { 42.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, id, expanded) { if (expanded && value == null) value = host.icon(id, iconPx) }
    val d = details.value
    MenuPopup(
        expanded,
        d?.label.orEmpty(),
        icon,
        onInfo = { host.openAppInfo(id) },
        actions = buildList {
            if (!locked && d != null && (d.resizeWidth || d.resizeHeight)) add(MenuAction(Icons.Resize, stringResource(R.string.widget_resize), onClick = onResize))
            if (!locked) add(MenuAction(Icons.Uninstall, stringResource(R.string.widget_remove), danger = true, onClick = onRemove))
        },
        onDismiss = onDismiss,
    )
}

/**
 * A frame round [widget] with a handle on each side it can be resized from; dragging one moves that
 * side, and letting go snaps it to the nearest cell and asks for the new size with [onResize].
 */
@Composable
internal fun ResizeFrame(widget: HomeWidget, cellW: Float, cellH: Float, columns: Int, rows: Int, details: WidgetDetails?, onResize: (HomeWidget) -> Unit) {
    val density = LocalDensity.current
    val minW = details?.let { ceil(it.minWidthPx / cellW).toInt() }?.coerceIn(1, columns) ?: 1
    val minH = details?.let { ceil(it.minHeightPx / cellH).toInt() }?.coerceIn(1, rows) ?: 1
    // The sides, in pixels, while a handle is dragged.
    var left by remember(widget, cellW) { mutableFloatStateOf(widget.col * cellW) }
    var top by remember(widget, cellH) { mutableFloatStateOf(widget.row * cellH) }
    var right by remember(widget, cellW) { mutableFloatStateOf((widget.col + widget.w) * cellW) }
    var bottom by remember(widget, cellH) { mutableFloatStateOf((widget.row + widget.h) * cellH) }
    val reset = {
        left = widget.col * cellW
        top = widget.row * cellH
        right = (widget.col + widget.w) * cellW
        bottom = (widget.row + widget.h) * cellH
    }
    val done = {
        val col = (left / cellW).roundToInt()
        val row = (top / cellH).roundToInt()
        val w = ((right / cellW).roundToInt() - col).coerceIn(minW, columns - col)
        val h = ((bottom / cellH).roundToInt() - row).coerceIn(minH, rows - row)
        reset()
        onResize(widget.copy(col = col, row = row, w = w, h = h))
    }
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { (right - left).toDp() }, with(density) { (bottom - top).toDp() })
            .padding(2.dp)
            .border(2.dp, Color.White, RoundedCornerShape(20.dp)),
    )
    val handlePx = with(density) { HandleSize.toPx() }

    @Composable
    fun Handle(x: () -> Float, y: () -> Float, onDrag: (Offset) -> Unit) {
        Box(
            Modifier
                .offset { IntOffset((x() - handlePx / 2).roundToInt(), (y() - handlePx / 2).roundToInt()) }
                .size(HandleSize)
                .pointerInput(widget, cellW, cellH) {
                    detectDragGestures(onDragEnd = { done() }, onDragCancel = { reset() }) { change, amount ->
                        change.consume()
                        onDrag(amount)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(16.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
        }
    }
    if (details?.resizeWidth != false) {
        Handle({ left }, { (top + bottom) / 2 }) { left = (left + it.x).coerceIn(0f, right - minW * cellW) }
        Handle({ right }, { (top + bottom) / 2 }) { right = (right + it.x).coerceIn(left + minW * cellW, columns * cellW) }
    }
    if (details?.resizeHeight != false) {
        Handle({ (left + right) / 2 }, { top }) { top = (top + it.y).coerceIn(0f, bottom - minH * cellH) }
        Handle({ (left + right) / 2 }, { bottom }) { bottom = (bottom + it.y).coerceIn(top + minH * cellH, rows * cellH) }
    }
}

// Big enough to grab; the dot drawn in it is smaller.
private val HandleSize = 40.dp

/**
 * A widget's view, which takes touches only as [HostedWidget] hands them over: Compose sees every
 * touch first, so a swipe that starts on a widget still turns the page or opens the drawer.
 */
internal class WidgetView(context: Context) : AppWidgetHostView(context) {
    var forwarding = false

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = forwarding && super.dispatchTouchEvent(ev)

    /** Whether it holds a list or other scrolling part, which then takes vertical swipes. */
    val scrolls: Boolean get() = scrolls(this)

    private fun scrolls(v: View): Boolean = v is AdapterView<*> || v is ScrollView || (v is ViewGroup && v.children.any(::scrolls))
}

/** Widget [id]'s view, sized to [modifier], told its size, and given its touches. */
@Composable
internal fun HostedWidget(host: WidgetHost, id: Int, modifier: Modifier) {
    val view = remember(id) { host.view(id) }
    BoxWithConstraints(modifier.pointerInput(view) { handTouches(view) }) {
        val width = maxWidth.value
        val height = maxHeight.value
        LaunchedEffect(view, width, height) { host.resize(view, width, height) }
        // A view has one parent: take it from where it was last shown (another page, before a recomposition).
        AndroidView(factory = { (view.parent as? ViewGroup)?.removeView(view); view }, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Hands each touch on the widget to its view without taking it from the home screen. A tap reaches
 * the widget; a hold is the home screen's (menu, move), so the widget gets a cancel; a swipe is the
 * home screen's (page, drawer), except an up or down swipe on a widget that scrolls, which the
 * widget keeps for itself.
 */
private suspend fun PointerInputScope.handTouches(view: WidgetView) {
    val slop = viewConfiguration.touchSlop
    val hold = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, at: Offset) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at.x, at.y, 0)
            view.forwarding = true
            view.dispatchTouchEvent(event)
            view.forwarding = false
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, down.position)
        var last = down.position
        // null while undecided; true when the widget scrolls; false when the home screen took it.
        var widgetsOwn: Boolean? = null
        var ended = false
        suspend fun AwaitPointerEventScope.follow() {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) {
                send(if (change == null) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, change?.position ?: last)
                ended = true
                return
            }
            last = change.position
            if (widgetsOwn == null && (change.position - down.position).getDistance() > slop) {
                val d = change.position - down.position
                widgetsOwn = abs(d.y) > abs(d.x) && view.scrolls
                if (widgetsOwn == false) {
                    send(MotionEvent.ACTION_CANCEL, change.position)
                    ended = true
                    return
                }
            }
            // A scroll the widget keeps: the home screen mustn't move with it.
            if (widgetsOwn == true) change.consume()
            send(MotionEvent.ACTION_MOVE, change.position)
        }
        withTimeoutOrNull(hold) { while (!ended) follow() }
        if (ended) return@awaitEachGesture
        if (widgetsOwn == true) {
            while (!ended) follow()
        } else {
            // Held still: the home screen's long-press.
            send(MotionEvent.ACTION_CANCEL, last)
        }
    }
}
