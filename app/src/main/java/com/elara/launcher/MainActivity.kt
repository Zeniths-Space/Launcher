package com.elara.launcher

import java.time.LocalDateTime
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import android.provider.Settings
import android.net.Uri
import android.Manifest
import kotlinx.coroutines.Dispatchers
import androidx.activity.result.contract.ActivityResultContracts
import android.util.DisplayMetrics
import android.os.Handler
import android.os.Build
import android.content.ActivityNotFoundException
import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The home screen. One instance (singleTask); pressing home while it shows closes the drawer. */
class MainActivity : ComponentActivity() {

    private val repository by lazy { AppRepository(this) }
    private val store by lazy { SettingsStore(this) }
    private var drawerOpen by mutableStateOf(false)
    // Ignore the tail of a system Recents swipe when its window hands focus back to the launcher.
    private var suppressDrawerSwipe by mutableStateOf(false)
    private var suppressDrawerSwipeJob: Job? = null
    // Checked on every return, for the setup's home app step: it changes in Android's settings.
    private var isDefaultHome by mutableStateOf(false)
    // Counts Home presses and leaving, which also close the home sheet.
    private var resets by mutableIntStateOf(0)
    // Counts each time the home screen comes into view (back from an app, after unlock), which it zooms in for.
    private var entrances by mutableIntStateOf(0)

    // Stopped because the screen went off: on unlock the home screen comes back as it was, like One UI.
    private var stoppedAsleep = false
    // When the screen last went off. onStop can run just before or after isInteractive turns false, so it is not trusted alone.
    private var screenOffAt = Long.MIN_VALUE
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOffAt = SystemClock.elapsedRealtime()
        }
    }
    private val drawer = DrawerMotion()
    private val widgets = WidgetHost(this)
    // Whether the blur is on; while off, wallpaper changes are not looked at.
    private var glassEnabled = false
    // Whether the drawer already asked for wallpaper access (it asks once).
    private var glassAsked by mutableStateOf(true)
    // Below Android 11, reading the wallpaper takes the storage permission, asked in a dialog.
    private val storageConsent = registerForActivityResult(ActivityResultContracts.RequestPermission()) { checkGlass() }
    // The wallpaper changed (here or in another app): the glass copy may no longer match.
    private val wallpaperListener = WallpaperManager.OnColorsChangedListener { _, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) checkGlass()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        super.onCreate(savedInstanceState)
        // Home and drawer draw white over the dimmed wallpaper, so system bar icons stay light in light mode too.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        repository.onChanged = { reload() }
        repository.register()
        reload(wait = 0)
        repository.onLaunch = { app -> lifecycleScope.launch { store.countLaunch(app.key) } }
        glassAsked = GlassWallpaper.asked(this)
        if (Build.VERSION.SDK_INT >= 27) WallpaperManager.getInstance(this).addOnColorsChangedListener(wallpaperListener, Handler(mainLooper))
        // Widgets Android still holds but the home screen lost (say, the app was killed mid-add) are freed.
        lifecycleScope.launch {
            // Waiting spaces' widgets too: they come back when their space does.
            val s = store.settings.first()
            widgets.forgetAllBut(s.widgets.mapTo(HashSet()) { it.id } + s.spaces.flatMap { sp -> HomeWidgets.parse(sp.layout.widgets).map { it.id } })
        }
        setContent {
            val settings by store.settings.collectAsState(LauncherSettings())
            // Before any icon below is asked for, so none is drawn in the wrong shape first.
            repository.useIconShape(settings.iconShape)
            repository.useIconPack(settings.iconPack)
            repository.useAdaptIcons(settings.iconPackAdapt)
            repository.useAppIcons(settings.appIcons)
            // The first time apps are known, fill the dock with the default Phone, Messages,
            // Browser and Camera apps (once: the store keeps it from happening again).
            val apps by repository.apps.collectAsState()
            LaunchedEffect(apps != null) {
                apps?.let { list -> store.seedDock(repository.defaultDock(list, this@MainActivity).map { it.key }) }
            }
            // Spaces by schedule: checked on each visit home (entrances), then one wait for the next
            // change while the home screen is started. No alarms, nothing in the background.
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(settings.spaces, settings.activeSpace, settings.spacesBySchedule, settings.spaceManualUntil, entrances) {
                if (!settings.spacesBySchedule || settings.spaces.none { it.scheduled }) return@LaunchedEffect
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        if (store.applySchedule(LocalDateTime.now(), System.currentTimeMillis())) return@repeatOnLifecycle
                        val nowMs = System.currentTimeMillis()
                        val next = withContext(Dispatchers.Default) { Spaces.holdUntil(settings.spaces) }
                            .let { if (settings.spaceManualUntil > nowMs) minOf(it, settings.spaceManualUntil) else it }
                        if (next == Long.MAX_VALUE) return@repeatOnLifecycle
                        delay((next - nowMs).coerceAtLeast(1_000))
                    }
                }
            }
            // The app being renamed, from any app's menu.
            var editing by remember { mutableStateOf<AppEntry?>(null) }
            var changingIcon by remember { mutableStateOf<AppEntry?>(null) }
            val focus = LocalFocusManager.current
            val glassImage by GlassWallpaper.image.collectAsState()
            val glassStale by GlassWallpaper.stale.collectAsState()
            val glassOn = settings.drawerBlur
            // All the blur's work (reading, checking, the image in memory) happens only while it is on.
            // Until the store is read, settings are defaults (blur on): wait, so a blur that is off never runs.
            val settingsRead by remember { store.settings.map { true } }.collectAsState(false)
            LaunchedEffect(glassOn, settingsRead, settings.drawerBlurStyle) {
                if (!settingsRead) return@LaunchedEffect
                glassEnabled = glassOn
                if (glassOn) {
                    val (width, height) = screenSize()
                    withContext(Dispatchers.IO) { GlassWallpaper.load(this@MainActivity, width, height, settings.drawerBlurStyle) }
                } else {
                    GlassWallpaper.unload()
                }
            }
            ZenithTheme(fontVersion = settings.fontVersion) {
                CompositionLocalProvider(LocalEditApp provides { editing = it }) {
                    LauncherScreen(
                        repository = repository,
                        motion = drawer,
                        settings = settings,
                        drawerOpen = drawerOpen,
                        resets = resets,
                        entrances = entrances,
                        suppressDrawerSwipe = suppressDrawerSwipe,
                        onDrawerOpenChange = { drawerOpen = it },
                        onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                        onSettings = { edit -> lifecycleScope.launch { store.edit() } },
                        onWallpaper = rememberWallpaperPicker(),
                        onGesture = ::onGesture,
                        onDockChange = { keys -> lifecycleScope.launch { store.setDock(keys) } },
                        onHomeChange = { pages -> lifecycleScope.launch { store.setHome(pages) } },
                        onLayoutChange = { pages, folders -> lifecycleScope.launch { store.setHomeLayout(pages, folders) } },
                        widgetHost = widgets,
                        onWidgetsChange = { list -> lifecycleScope.launch { store.setWidgets(list) } },
                        onSwitchSpace = { id ->
                            lifecycleScope.launch {
                                val until = withContext(Dispatchers.Default) { Spaces.holdUntil(settings.spaces) }
                                store.switchSpace(id, manualUntil = until)
                            }
                        },
                        onNewSpace = {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, SettingsActivity.PAGE_SPACES))
                        },
                        glassImage = glassImage.takeIf { glassOn && !glassStale },
                        glassStyle = settings.drawerBlurStyle,
                        onUpdateGlass = if (glassOn && glassStale && !glassAsked) ::askWallpaperAccess else null,
                    )
                }
                editing?.let { app ->
                    EditAppSheet(
                        app,
                        // The app's own name, as Android gives it, before any rename.
                        original = apps?.firstOrNull { it.key == app.key }?.label ?: app.label,
                        repository = repository,
                        onRename = { name -> lifecycleScope.launch { store.setAppLabel(app.key, name) } },
                        // The picker opens over this sheet, which then shows the new icon.
                        onChangeIcon = { changingIcon = app },
                        // Focus would otherwise come back to the drawer's search field, keyboard and all.
                        onDismiss = { editing = null; focus.clearFocus(force = true) },
                    )
                }
                changingIcon?.let { app ->
                    IconPickerSheet(
                        app,
                        repository,
                        current = settings.appIcons[app.key],
                        onPick = { icon ->
                            // A gallery picture this replaces is no longer needed.
                            val old = settings.appIcons[app.key]
                            lifecycleScope.launch {
                                store.setAppIcon(app.key, icon)
                                withContext(Dispatchers.IO) { PickedIcons.delete(this@MainActivity, old) }
                            }
                        },
                        onDismiss = { changingIcon = null; focus.clearFocus(force = true) },
                    )
                }
                if (!settings.setupDone) {
                    SetupScreen(
                        isDefaultHome = isDefaultHome,
                        onChooseHome = { openSystem(android.provider.Settings.ACTION_HOME_SETTINGS) },
                        onWallpaper = rememberWallpaperPicker(),
                        onDone = { lifecycleScope.launch { store.setSetupDone() } },
                    )
                }
            }
        }
    }

    // Gestures that need the accessibility service point to where it is turned on.
    private fun onGesture(action: GestureAction) {
        Gestures.perform(this, action)
    }

    // A burst of package events (an app store updating many apps) makes one reload, not one each.
    private var reloading: Job? = null

    private fun reload(wait: Long = ReloadDelayMs) {
        reloading?.cancel()
        reloading = lifecycleScope.launch {
            delay(wait)
            repository.load(packageName)
        }
    }

    // Home pressed while already home: back to the home screen, like every launcher.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            drawerOpen = false
            resets++
        }
    }

    override fun onResume() {
        super.onResume()
        isDefaultHome = isDefaultHome(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            suppressDrawerSwipeJob?.cancel()
            suppressDrawerSwipe = true
            suppressDrawerSwipeJob = lifecycleScope.launch {
                delay(350)
                suppressDrawerSwipe = false
            }
        }
    }

    // Widgets update only while the home screen is started.
    override fun onStart() {
        super.onStart()
        widgets.startListening()
        // Back from Android's access screen, say: only worth a look while the copy is missing or out of date.
        if (GlassWallpaper.stale.value) checkGlass()
        if (stoppedAsleep) stoppedAsleep = false else entrances++
    }

    @Deprecated("Android delivers a widget's setup screen result only here.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.onConfigured(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    // Back from an app (or anywhere) to the home screen, like One UI: the drawer is already closed
    // when the home screen shows again, instead of animating shut over the app's closing animation.
    override fun onStop() {
        super.onStop()
        widgets.stopListening()
        stoppedAsleep = !getSystemService(PowerManager::class.java).isInteractive ||
            SystemClock.elapsedRealtime() - screenOffAt < 3000
        if (stoppedAsleep) return
        drawer.jump(0f)
        drawerOpen = false
        resets++
    }

    private fun checkGlass() {
        if (!glassEnabled) return
        val (width, height) = screenSize()
        lifecycleScope.launch(Dispatchers.IO) { GlassWallpaper.check(this@MainActivity, width, height) }
    }

    /** Asks, once, for the access that lets Elara read the wallpaper for the glass blur. */
    private fun askWallpaperAccess() {
        GlassWallpaper.setAsked(this)
        glassAsked = true
        if (Build.VERSION.SDK_INT < 30) {
            storageConsent.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }
        val app = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        try {
            startActivity(app)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** The full screen size in px, which the wallpaper fills. */
    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= 30) {
        windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        @Suppress("DEPRECATION")
        DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        repository.unregister()
        if (Build.VERSION.SDK_INT >= 27) WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperListener)
        super.onDestroy()
    }
}

private const val ReloadDelayMs = 300L
