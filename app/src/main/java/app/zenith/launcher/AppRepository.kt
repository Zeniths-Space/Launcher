package app.zenith.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.TypedValue
import android.window.SplashScreen
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * The launchable apps of every profile on the device, from LauncherApps (no package-query
 * permission needed). Refreshed only when Android reports a package change; never polled.
 * Icons are drawn once per app and size, on demand, and kept in memory.
 */
class AppRepository(context: Context) {
    private val pm = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val density = context.resources.displayMetrics.densityDpi

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** Null until the first load finishes. */
    val apps: StateFlow<List<AppEntry>?> = _apps

    private val infos = HashMap<String, LauncherActivityInfo>()
    private val icons = HashMap<String, ImageBitmap>()

    /** Any package change reloads the list (cheap: one LauncherApps call per profile). */
    var onChanged: () -> Unit = {}
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = onChanged()
        override fun onPackageAdded(packageName: String, user: UserHandle) = onChanged()
        override fun onPackageChanged(packageName: String, user: UserHandle) = onChanged()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChanged()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChanged()
    }

    fun register() = launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    fun unregister() = launcherApps.unregisterCallback(callback)

    suspend fun load(selfPackage: String) = withContext(Dispatchers.IO) {
        val found = HashMap<String, LauncherActivityInfo>()
        val entries = userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user)
                // This launcher does not list itself.
                .filter { it.componentName.packageName != selfPackage }
                .map { info ->
                    AppEntry(info.label.toString(), info.componentName.packageName, info.componentName.className, serial)
                        .also { found[it.key] = info }
                }
        }
        synchronized(infos) {
            infos.clear()
            infos.putAll(found)
            // Drop icons of apps that are gone or changed; changed apps redraw on next use.
            icons.keys.retainAll(found.keys)
        }
        _apps.value = sortApps(entries)
    }

    /** The app's icon at [sizePx], badged for work-profile apps. Drawn off the main thread. */
    suspend fun icon(app: AppEntry, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${app.key}@$sizePx"
        synchronized(infos) { icons[cacheKey] }?.let { return@withContext it }
        val info = synchronized(infos) { infos[app.key] } ?: return@withContext null
        val drawable = runCatching { pm.getUserBadgedIcon(info.getIcon(density).inSystemShape(), info.user) }.getOrNull()
            ?: return@withContext null
        val bitmap = createBitmap(sizePx, sizePx)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap().also { synchronized(infos) { icons[cacheKey] = it } }
    }

    /** Starts [app] with the system's own animation; [bounds] tell the app where it was opened from. */
    fun launch(app: AppEntry, bounds: Rect?) {
        val info = synchronized(infos) { infos[app.key] } ?: return
        runCatching { launcherApps.startMainActivity(ComponentName(app.packageName, app.className), info.user, bounds, null) }
    }

    /**
     * Starts [app] with no system animation, for when the launcher has just drawn its own zoom from
     * the icon. Only for apps of this profile (other profiles start through LauncherApps, which
     * cannot drop the animation). False if it could not be started this way.
     */
    fun launchWithoutAnimation(app: AppEntry, bounds: Rect?, context: Context): Boolean {
        val info = synchronized(infos) { infos[app.key] } ?: return false
        if (info.user != Process.myUserHandle()) return false
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        intent.sourceBounds = bounds
        // Each app's own splash screen, as Android shows it for launches from the home app: an app
        // started any other way gets a plain colour splash with no icon.
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityOptions.makeBasic().setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON).toBundle()
        } else {
            null
        }
        return runCatching { context.startActivity(intent, options) }.isSuccess
    }

    /** Whether [app] can be started by [launchWithoutAnimation]. */
    fun isOwnProfile(app: AppEntry): Boolean =
        synchronized(infos) { infos[app.key] }?.user == Process.myUserHandle()

    /**
     * The colour the app's splash screen opens with (its splash background, else its window
     * background), so the launcher's zoom ends on the colour Android's splash then shows. Null if
     * unknown.
     */
    fun windowColor(app: AppEntry, context: Context): Int? = runCatching {
        val activity = context.packageManager.getActivityInfo(ComponentName(app.packageName, app.className), 0)
        val theme = activity.themeResource.takeIf { it != 0 } ?: return null
        val appContext = context.createPackageContext(app.packageName, 0).apply { setTheme(theme) }
        val attrs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            intArrayOf(android.R.attr.windowSplashScreenBackground, android.R.attr.windowBackground)
        } else {
            intArrayOf(android.R.attr.windowBackground)
        }
        val a = appContext.obtainStyledAttributes(attrs)
        try {
            (0 until a.length()).firstNotNullOfOrNull { i ->
                val v = a.peekValue(i) ?: return@firstNotNullOfOrNull null
                val color = if (v.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                    v.data
                } else {
                    // A launch-screen drawable (Flutter's, Artha's) is a logo on a background: its
                    // corner is the colour around the logo.
                    runCatching { a.getDrawable(i) }.getOrNull()?.let { (it as? ColorDrawable)?.color ?: it.cornerColor() }
                }
                // Unset splash colours read as transparent; fall through to the window background.
                color?.takeIf { android.graphics.Color.alpha(it) == 255 }
            }
        } finally {
            a.recycle()
        }
    }.getOrNull()

    fun openAppInfo(app: AppEntry) {
        val info = synchronized(infos) { infos[app.key] } ?: return
        runCatching { launcherApps.startAppDetailsActivity(ComponentName(app.packageName, app.className), info.user, null, null) }
    }
}

/** The colour at the top-left corner of [this], drawn on a small bitmap. */
private fun Drawable.cornerColor(): Int {
    val size = 32
    val bitmap = createBitmap(size, size)
    val old = copyBounds()
    setBounds(0, 0, size, size)
    draw(Canvas(bitmap))
    bounds = old
    return bitmap.getPixel(0, 0).also { bitmap.recycle() }
}

/**
 * Legacy (non-adaptive) icons are drawn as they are, square or shapeless, next to adaptive icons in
 * the system shape, so wrap them in an adaptive icon and Android masks them to the same shape, as
 * Launcher3 does. An icon whose corners are filled (a square) fills the shape; any other sits at
 * 70% of the shape on its own dominant colour.
 */
private fun Drawable.inSystemShape(): Drawable {
    if (this is AdaptiveIconDrawable) return this
    val size = 32
    val bitmap = createBitmap(size, size)
    setBounds(0, 0, size, size)
    draw(Canvas(bitmap))
    val pixels = IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }
    bitmap.recycle()
    val corners = intArrayOf(0, size - 1, size * (size - 1), size * size - 1)
    val square = corners.all { android.graphics.Color.alpha(pixels[it]) == 255 }
    return if (square) {
        AdaptiveIconDrawable(ColorDrawable(pixels[0]), InsetDrawable(this, FullBleedInset))
    } else {
        AdaptiveIconDrawable(ColorDrawable(dominantColor(pixels)), InsetDrawable(this, LegacyIconInset))
    }
}

/** The visible part of an adaptive icon layer is its middle 72 of 108 units. */
private const val FullBleedInset = 18f / 108f
/** 70% of the visible area, the layer being 1.5 times that area. */
private const val LegacyIconInset = (1f - 0.7f / 1.5f) / 2

/**
 * The most common colour among [argb]'s opaque pixels, grouped in steps of 16 per channel; white
 * if none are opaque.
 */
internal fun dominantColor(argb: IntArray): Int {
    val counts = HashMap<Int, Int>()
    for (c in argb) {
        if (c ushr 24 < 200) continue
        val bucket = c and 0x00F0F0F0
        counts[bucket] = (counts[bucket] ?: 0) + 1
    }
    val best = counts.maxByOrNull { it.value }?.key ?: return 0xFFFFFFFF.toInt()
    // The middle of the bucket, opaque.
    return best or 0x00080808 or 0xFF000000.toInt()
}
