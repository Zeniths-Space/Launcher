package app.zenith.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.view.View
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
    fun launch(app: AppEntry, bounds: Rect?, view: View? = null) {
        val info = synchronized(infos) { infos[app.key] } ?: return
        val options = if (bounds != null && view != null) {
            val origin = IntArray(2).also(view::getLocationInWindow)
            ActivityOptions.makeClipRevealAnimation(
                view, bounds.left - origin[0], bounds.top - origin[1], bounds.width(), bounds.height(),
            ).toBundle()
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
