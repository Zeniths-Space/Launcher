package com.elara.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.net.Uri
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.window.SplashScreen
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The launchable apps of every profile on the device, from LauncherApps (no package-query
 * permission needed). Refreshed only when Android reports a package change; never polled.
 * Icons are drawn once per app and size, on demand, and kept in memory.
 */
class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    // The window apps are launched from; the repository lives no longer than its activity.
    private val activity = context as? Activity
    private val pm = context.packageManager
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val density = context.resources.displayMetrics.densityDpi

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** Null until the first load finishes. */
    val apps: StateFlow<List<AppEntry>?> = _apps

    private val infos = HashMap<String, LauncherActivityInfo>()
    private val icons = HashMap<String, ImageBitmap>()

    /** The outline icons are drawn in. Set from settings; a change draws every icon again (see [iconEpoch]). */
    var iconShape by mutableStateOf(IconShape.System)
        private set

    /** Goes up whenever drawn icons go stale (a new shape, a new day for calendar icons). Callers key their icon state on it. */
    var iconEpoch by mutableIntStateOf(0)
        private set

    fun useIconShape(shape: IconShape) {
        if (shape == iconShape) return
        synchronized(infos) { icons.clear() }
        iconShape = shape
        iconEpoch++
    }

    // Calendar apps can show today's date on their icon (Launcher3's convention: the launcher activity's
    // meta-data "<package>.dynamic_icons" names an array of 31 icons, one per day of the month).
    private var day = LocalDate.now().dayOfMonth
    private val dynamicApps = HashSet<String>()

    /** Draws calendar icons again if the date moved on. Called on date and time-zone changes, and on each return to the home screen. */
    fun checkDate() {
        val today = LocalDate.now().dayOfMonth
        if (today == day) return
        day = today
        synchronized(infos) { icons.keys.removeAll { it.substringBeforeLast('@') in dynamicApps } }
        iconEpoch++
    }

    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = checkDate()
    }

    private fun dynamicIcon(info: LauncherActivityInfo, key: String): Drawable? = runCatching {
        val pkg = info.componentName.packageName
        val meta = pm.getActivityInfo(info.componentName, PackageManager.GET_META_DATA).metaData
        val arrayId = meta?.getInt("$pkg.dynamic_icons", 0) ?: 0
        if (arrayId == 0) return null
        val res = pm.getResourcesForApplication(info.applicationInfo)
        val array = res.obtainTypedArray(arrayId)
        val id = try { array.getResourceId(day - 1, 0) } finally { array.recycle() }
        if (id == 0) return null
        synchronized(infos) { dynamicApps.add(key) }
        res.getDrawableForDensity(id, density, null)
    }.getOrNull()

    /** Any package change reloads the list (cheap: one LauncherApps call per profile). */
    var onChanged: () -> Unit = {}
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(*packageNames)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed(*packageNames)
    }

    // Only the changed packages' icons are drawn again (an update may bring a new icon); the rest stay cached.
    // That waits for [load]: until it runs, [infos] still points at the old APK, which an update has removed,
    // and drawing from it gives Android's default icon.
    private val changedPackages = HashSet<String>()

    private fun changed(vararg packageNames: String) {
        synchronized(infos) { changedPackages.addAll(packageNames) }
        onChanged()
    }

    fun register() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val dateChanges = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(appContext, dateReceiver, dateChanges, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun unregister() {
        launcherApps.unregisterCallback(callback)
        appContext.unregisterReceiver(dateReceiver)
    }

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
            // Drop icons of apps that are gone, and of changed ones. Cache keys are "<app key>@<size>".
            icons.keys.retainAll { it.substringBeforeLast('@') in found }
            if (changedPackages.isNotEmpty()) {
                icons.keys.removeAll { key -> changedPackages.any { key.startsWith("$it/") } }
                changedPackages.clear()
                // Icons on screen ask again: changed apps get their new icon, the rest their cached one.
                iconEpoch++
            }
        }
        _apps.value = sortApps(entries)
    }

    /** The app's icon at [sizePx] if already drawn, so a new view can show it from its first frame. */
    fun cachedIcon(app: AppEntry, sizePx: Int): ImageBitmap? = synchronized(infos) { icons["${app.key}@$sizePx"] }

    /** The app's icon at [sizePx], badged for work-profile apps. Drawn off the main thread. */
    suspend fun icon(app: AppEntry, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${app.key}@$sizePx"
        synchronized(infos) { icons[cacheKey] }?.let { return@withContext it }
        val info = synchronized(infos) { infos[app.key] } ?: return@withContext null
        val shape = iconShape
        val drawable = runCatching {
            val icon = (dynamicIcon(info, app.key) ?: info.getIcon(density)).inSystemShape()
            val shaped = shape.shape?.let { icon.masked(it, sizePx).toDrawable(appContext.resources) } ?: icon
            pm.getUserBadgedIcon(shaped, info.user)
        }.getOrNull() ?: return@withContext null
        // Not cached if the shape changed while drawing, so an icon in the old shape never sticks.
        drawable.toImageBitmap(sizePx).also { if (shape == iconShape) synchronized(infos) { icons[cacheKey] = it } }
    }

    /** Apps that came with the device can't be uninstalled, only disabled from App info. */
    fun canUninstall(app: AppEntry): Boolean {
        val info = synchronized(infos) { infos[app.key] } ?: return false
        return info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    /** Android's own uninstall prompt, in the app's profile; nothing is removed until the user confirms. */
    fun uninstall(app: AppEntry) {
        val info = synchronized(infos) { infos[app.key] } ?: return
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, info.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    /**
     * Starts [app] growing out of its icon at [bounds] (window coordinates); without bounds, with the
     * system's plain open animation.
     */
    fun launch(app: AppEntry, bounds: Rect?) {
        val info = synchronized(infos) { infos[app.key] } ?: return
        val source = activity?.window?.decorView
        val options = if (source != null && bounds != null && !bounds.isEmpty) {
            // The decor view fills the window, so window coordinates are its own.
            // shortcut: One UI (SM-S928B, 2026-11-06) ignores this and crossfades; a true icon-to-app
            // animation there needs a system-only remote transition. makeCustomAnimation is ignored too
            // (tested 2026-11-07). Recheck on AOSP-based devices.
            ActivityOptions.makeScaleUpAnimation(source, bounds.left, bounds.top, bounds.width(), bounds.height())
        } else {
            ActivityOptions.makeBasic()
        }
        // The app's own splash screen with its icon, as for a launch from the home app; a launch
        // Android does not count as one gets a plain colour splash, a blank screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON)
        runCatching { launcherApps.startMainActivity(ComponentName(app.packageName, app.className), info.user, bounds, options.toBundle()) }
    }

    /**
     * The default Phone, Messages, Browser and Camera apps, as the system resolves their standard
     * intents, mapped to this profile's launcher entries; any without a default are skipped.
     */
    fun defaultDock(apps: List<AppEntry>, context: Context): List<AppEntry> {
        val pm = context.packageManager
        val serial = userManager.getSerialNumberForUser(Process.myUserHandle())
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, "smsto:".toUri()),
            Intent(Intent.ACTION_VIEW, "https://".toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        return intents.mapNotNull { intent ->
            // With no default set, Android resolves to its chooser ("android"): take the first match.
            val resolved = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
                ?.activityInfo?.packageName?.takeIf { it != "android" }
                ?: runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
                    ?.firstOrNull()?.activityInfo?.packageName
            apps.firstOrNull { it.packageName == resolved && it.userSerial == serial }
        }.distinct()
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
private fun Drawable.inSystemShape(): AdaptiveIconDrawable =
    if (this is AdaptiveIconDrawable) this
    else AdaptiveIconDrawable(android.graphics.Color.WHITE.toDrawable(), InsetDrawable(this, LegacyIconInset))

/**
 * The adaptive icon's two layers inside [shape] instead of the system mask. Layers are 1.5 times the
 * visible icon, as Android draws them, and the outline is antialiased (a clip path would not be).
 */
private fun AdaptiveIconDrawable.masked(shape: Shape, sizePx: Int): android.graphics.Bitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    val outline = shape.createOutline(Size(sizePx.toFloat(), sizePx.toFloat()), LayoutDirection.Ltr, Density(1f))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawPath(androidx.compose.ui.graphics.Path().apply { addOutline(outline) }.asAndroidPath(), paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    val layer = canvas.saveLayer(null, paint)
    val inset = sizePx / 4
    listOfNotNull(background, foreground).forEach {
        it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
        it.draw(canvas)
    }
    canvas.restoreToCount(layer)
    return bitmap
}

internal fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    // Kept on the GPU: no upload stall the first time the drawer or a page shows it.
    return bitmap.toHardware().asImageBitmap()
}

private const val LegacyIconInset = (1f - 0.7f / 1.5f) / 2

/** A GPU-resident copy, drawn without an upload; the original if the copy can't be made. */
internal fun android.graphics.Bitmap.toHardware(): android.graphics.Bitmap =
    copy(android.graphics.Bitmap.Config.HARDWARE, false)?.also { recycle() } ?: this
