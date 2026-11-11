package com.elara.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.net.Uri
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
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
/** An app's shortcut ("New event" in Calendar), as found for the drawer search. */
class AppShortcut(val label: String, val appLabel: String, val info: ShortcutInfo) {
    val key: String get() = "${info.`package`}/${info.id}#${info.userHandle.hashCode()}"
}

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

    /** Goes up whenever drawn icons go stale (a new shape, an updated app). Callers key their icon state on it. */
    var iconEpoch by mutableIntStateOf(0)
        private set

    /** The icon pack in use, loading or loaded; null for the apps' own icons. Icons wait for it, so none is drawn twice. */
    private var iconPack: Deferred<IconPack?>? = null
    private var iconPackName = ""
    private val packScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Uses the icon pack [packageName] ("" for none), loading it off the main thread; every icon is drawn again. */
    fun useIconPack(packageName: String, reload: Boolean = false) {
        if (packageName == iconPackName && !reload) return
        iconPackName = packageName
        iconPack = if (packageName.isEmpty()) null else packScope.async { IconPacks.load(appContext, packageName) }
        synchronized(infos) { icons.clear() }
        iconEpoch++
    }

    fun useIconShape(shape: IconShape) {
        if (shape == iconShape) return
        synchronized(infos) { icons.clear() }
        iconShape = shape
        iconEpoch++
    }

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
        // The icon pack itself updated or went away: read it again (gone, it gives no icons).
        if (iconPackName.isNotEmpty() && iconPackName in packageNames) useIconPack(iconPackName, reload = true)
        synchronized(infos) { changedPackages.addAll(packageNames) }
        onChanged()
    }

    fun register() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun unregister() {
        launcherApps.unregisterCallback(callback)
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
        val pack = iconPack
        val packIcon = pack?.await()?.icon(info.componentName, density)
        val drawable = runCatching {
            // A pack's icon is drawn as the pack made it, unless it's adaptive, which takes the shape like the rest.
            if (packIcon != null && packIcon !is AdaptiveIconDrawable) return@runCatching pm.getUserBadgedIcon(packIcon, info.user)
            // Icons are drawn as the app ships them; calendar dates and clock hands on icons are not shown (owner's choice, 2026-11-09).
            val icon = (packIcon ?: info.getIcon(density)).inSystemShape(appContext.resources)
            val shaped = shape.shape?.let { icon.masked(it, sizePx).toDrawable(appContext.resources) } ?: icon
            pm.getUserBadgedIcon(shaped, info.user)
        }.getOrNull() ?: return@withContext null
        // Not cached if the shape changed while drawing, so an icon in the old shape never sticks.
        drawable.toImageBitmap(sizePx).also { if (shape == iconShape && pack === iconPack) synchronized(infos) { icons[cacheKey] = it } }
    }

    /**
     * Every app's shortcuts (the actions its long-press menu offers), under the app's name in [apps].
     * Empty unless this is the home app, which alone may read them. Blocking work, off the main thread.
     */
    suspend fun shortcuts(apps: List<AppEntry>): List<AppShortcut> = withContext(Dispatchers.IO) {
        if (runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false) != true) return@withContext emptyList()
        val names = apps.associate { "${it.packageName}#${it.userSerial}" to it.label }
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC,
        )
        userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            // A paused work profile refuses: it just has none to show.
            runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty().mapNotNull { info ->
                val app = names["${info.`package`}#$serial"] ?: return@mapNotNull null
                val label = info.shortLabel?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                AppShortcut(label, app, info).takeIf { info.isEnabled }
            }
        }
    }

    /** [app]'s own shortcuts, in its order, for its long-press menu; empty unless this is the home app. */
    suspend fun shortcuts(app: AppEntry, limit: Int = 4): List<AppShortcut> = withContext(Dispatchers.IO) {
        val info = synchronized(infos) { infos[app.key] } ?: return@withContext emptyList()
        if (runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false) != true) return@withContext emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.packageName)
            .setActivity(info.componentName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        runCatching { launcherApps.getShortcuts(query, info.user) }.getOrNull().orEmpty()
            .filter { it.isEnabled && !it.shortLabel.isNullOrBlank() }
            // Manifest ones first, then dynamic, each by the app's rank, as other launchers list them.
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(limit)
            .map { AppShortcut(it.shortLabel.toString(), app.label, it) }
    }

    /** A shortcut's own icon, drawn off the main thread; null if it has none. */
    suspend fun shortcutIcon(shortcut: AppShortcut, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val d = runCatching { launcherApps.getShortcutIconDrawable(shortcut.info, density) }.getOrNull() ?: return@withContext null
        if (d is android.graphics.drawable.AdaptiveIconDrawable) return@withContext d.toImageBitmap(sizePx)
        // A plain icon (often a bare glyph, black or white) gets a margin, to sit on the menu's light badge.
        val inset = (sizePx * ShortcutGlyphInset).toInt()
        android.graphics.drawable.InsetDrawable(d, inset).toImageBitmap(sizePx)
    }

    /** Runs [shortcut]; nothing happens if its app took it away meanwhile. */
    fun startShortcut(shortcut: AppShortcut, bounds: Rect?) {
        runCatching { launcherApps.startShortcut(shortcut.info, bounds, null) }
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
 * the system shape, so they go inside an adaptive icon that Android masks to the same shape. One
 * that already fills a square (a full-bleed tile, as many older apps ship) is cropped to its tile and
 * fills the shape, as One UI does. Any other is placed on white like Launcher3: scaled to 70% of the
 * visible area, the layer being 1.5 times that area.
 */
private fun Drawable.inSystemShape(res: android.content.res.Resources): AdaptiveIconDrawable {
    if (this is AdaptiveIconDrawable) return this
    fullBleedTile(res)?.let { return AdaptiveIconDrawable(InsetDrawable(it, FullBleedInset), android.graphics.Color.TRANSPARENT.toDrawable()) }
    return AdaptiveIconDrawable(android.graphics.Color.WHITE.toDrawable(), InsetDrawable(this, LegacyIconInset))
}

/** This icon cropped to its tile, if it is a full-bleed square tile ([IconTiles.tileBox]); else null. */
private fun Drawable.fullBleedTile(res: android.content.res.Resources): Drawable? {
    val n = IconTiles.SampleSize
    val sample = createBitmap(n, n)
    setBounds(0, 0, n, n)
    draw(Canvas(sample))
    val pixels = IntArray(n * n)
    sample.getPixels(pixels, 0, n, 0, 0, n, n)
    sample.recycle()
    val box = IconTiles.tileBox(pixels, n) ?: return null
    // Drawn again, larger, with the tile filling the bitmap, so it stays sharp at the home screen's size.
    val out = 256
    val w = box[2] - box[0]
    val h = box[3] - box[1]
    val tile = createBitmap(out, out)
    setBounds(-box[0] * out / w, -box[1] * out / h, (n - box[0]) * out / w, (n - box[1]) * out / h)
    draw(Canvas(tile))
    setBounds(0, 0, n, n)
    return tile.toDrawable(res)
}

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

private const val ShortcutGlyphInset = 0.2f

// The tile a little past the visible area (2/3 of the layer), so its own rounded corners are cut away.
private const val FullBleedInset = (1f - 1.06f / 1.5f) / 2

private const val LegacyIconInset = (1f - 0.7f / 1.5f) / 2

/** A GPU-resident copy, drawn without an upload; the original if the copy can't be made. */
internal fun android.graphics.Bitmap.toHardware(): android.graphics.Bitmap =
    copy(android.graphics.Bitmap.Config.HARDWARE, false)?.also { recycle() } ?: this
