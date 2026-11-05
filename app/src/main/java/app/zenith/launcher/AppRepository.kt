package app.zenith.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
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
        val drawable = runCatching { info.getBadgedIcon(density) }.getOrNull()
            ?: return@withContext null
        val bitmap = createBitmap(sizePx, sizePx)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap().also { synchronized(infos) { icons[cacheKey] = it } }
    }

    /** Starts [app]; with its icon's window [bounds] and the [view] it sits in, the app's window grows out of the icon. */
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
    fun launchWithoutAnimation(app: AppEntry, bounds: Rect?, context: Context, plainSplash: Boolean): Boolean {
        val info = synchronized(infos) { infos[app.key] } ?: return false
        if (info.user != Process.myUserHandle()) return false
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        intent.sourceBounds = bounds
        // An app started by anything but the home app gets a plain colour splash with no icon,
        // which shows as a blank screen between the zoom and the app, so the icon is asked for;
        // apps that draw their own splash get the plain one, so two splashes do not stack.
        val style = if (plainSplash) SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR else SplashScreen.SPLASH_SCREEN_STYLE_ICON
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityOptions.makeBasic().setSplashScreenStyle(style).toBundle()
        } else {
            null
        }
        return runCatching { context.startActivity(intent, options) }.isSuccess
    }

    /** Whether [app] can be started by [launchWithoutAnimation]. */
    fun isOwnProfile(app: AppEntry): Boolean =
        synchronized(infos) { infos[app.key] }?.user == Process.myUserHandle()

    /**
     * What the app's splash screen shows: its background colour (splash background, else window
     * background) and, on Android 12 and later, its splash icon. The launcher draws these itself,
     * because starting an app without the system animation also skips the system splash screen.
     */
    fun splash(app: AppEntry, context: Context): AppSplash = runCatching {
        val activity = context.packageManager.getActivityInfo(ComponentName(app.packageName, app.className), 0)
        val theme = activity.themeResource.takeIf { it != 0 } ?: return AppSplash(null, null, false)
        val appContext = context.createPackageContext(app.packageName, 0).apply { setTheme(theme) }
        val s = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val attrs = if (s) {
            intArrayOf(android.R.attr.windowSplashScreenBackground, android.R.attr.windowBackground, android.R.attr.windowSplashScreenAnimatedIcon)
        } else {
            intArrayOf(android.R.attr.windowBackground)
        }
        val a = appContext.obtainStyledAttributes(attrs)
        try {
            val colors = if (s) 0..1 else 0..0
            var launchScreen = false
            val color = colors.firstNotNullOfOrNull { i ->
                val v = a.peekValue(i) ?: return@firstNotNullOfOrNull null
                val color = if (v.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                    v.data
                } else {
                    // A launch-screen drawable (Flutter's, Artha's) is a logo on a background: its
                    // corner is the colour Android's splash shows around the icon.
                    runCatching { a.getDrawable(i) }.getOrNull()?.let {
                        if (it !is ColorDrawable && i == colors.last) launchScreen = true
                        (it as? ColorDrawable)?.color ?: it.cornerColor()
                    }
                }
                // Unset splash colours read as transparent; fall through to the window background.
                color?.takeIf { android.graphics.Color.alpha(it) == 255 }
            }
            val iconIndex = attrs.indexOf(android.R.attr.windowSplashScreenAnimatedIcon)
            val icon = if (iconIndex >= 0) runCatching { a.getDrawable(iconIndex) }.getOrNull() else null
            // A splash icon set to draw nothing (Nekogram, Kontract, Samsung Settings), or no splash
            // icon but a launch-screen window background (Flutter apps such as Artha), means the
            // app shows its own splash, or none, once it starts.
            val blank = icon?.isBlank() == true
            AppSplash(color, icon.takeUnless { blank }, ownSplash = blank || (icon == null && launchScreen))
        } finally {
            a.recycle()
        }
    }.getOrElse { AppSplash(null, null, false) }

    fun openAppInfo(app: AppEntry) {
        val info = synchronized(infos) { infos[app.key] } ?: return
        runCatching { launcherApps.startAppDetailsActivity(ComponentName(app.packageName, app.className), info.user, null, null) }
    }
}

/** Whether [this] draws nothing, checked at its own size (fixed-size layers ignore smaller bounds). */
private fun Drawable.isBlank(): Boolean {
    val size = maxOf(intrinsicWidth, intrinsicHeight).coerceIn(32, 256)
    val bitmap = createBitmap(size, size)
    val old = copyBounds()
    setBounds(0, 0, size, size)
    draw(Canvas(bitmap))
    bounds = old
    val pixels = IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }
    bitmap.recycle()
    return pixels.none { android.graphics.Color.alpha(it) != 0 }
}

/**
 * An app's splash screen colour and icon, each null when the app does not set one, and whether it
 * hides the icon from Android's splash to show its own splash instead.
 */
data class AppSplash(val color: Int?, val icon: Drawable?, val ownSplash: Boolean)

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
