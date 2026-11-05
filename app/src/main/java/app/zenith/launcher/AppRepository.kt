package app.zenith.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.window.SplashScreen
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
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
        // The app's own splash screen with its icon, as for a launch from the home app; a launch
        // Android does not count as one gets a plain colour splash, a blank screen.
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityOptions.makeBasic().setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON).toBundle()
        } else {
            null
        }
        runCatching { launcherApps.startMainActivity(ComponentName(app.packageName, app.className), info.user, bounds, options) }
    }

    fun openAppInfo(app: AppEntry) {
        val info = synchronized(infos) { infos[app.key] } ?: return
        runCatching { launcherApps.startAppDetailsActivity(ComponentName(app.packageName, app.className), info.user, null, null) }
    }
}

/**
 * Legacy (non-adaptive) icons are drawn as they are, square or shapeless, next to adaptive icons in
 * the system shape. Like Launcher3, place them on white inside an adaptive icon so Android masks
 * them to the same shape: scaled to 70% of the visible area, the layer being 1.5 times that area.
 */
private fun Drawable.inSystemShape(): Drawable =
    if (this is AdaptiveIconDrawable) this
    else AdaptiveIconDrawable(android.graphics.Color.WHITE.toDrawable(), InsetDrawable(this, LegacyIconInset))

private const val LegacyIconInset = (1f - 0.7f / 1.5f) / 2
