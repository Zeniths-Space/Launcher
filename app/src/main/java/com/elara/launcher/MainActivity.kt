package com.elara.launcher

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The home screen. One instance (singleTask); pressing home while it shows closes the drawer. */
class MainActivity : ComponentActivity() {

    private val repository by lazy { AppRepository(this) }
    private val store by lazy { SettingsStore(this) }
    private var drawerOpen by mutableStateOf(false)
    // Checked on every return, for the setup's home app step: it changes in Android's settings.
    private var isDefaultHome by mutableStateOf(false)
    // Counts Home presses and leaving, which also close the home sheet.
    private var resets by mutableIntStateOf(0)
    // Counts each time the home screen comes into view (back from an app, after unlock), which it zooms in for.
    private var entrances by mutableIntStateOf(0)

    // Stopped because the screen went off: on unlock the home screen comes back as it was, like One UI.
    private var stoppedAsleep = false
    private val drawer = DrawerMotion()
    private val widgets = WidgetHost(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Home and drawer draw white over the dimmed wallpaper, so system bar icons stay light in light mode too.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        repository.onChanged = { reload() }
        repository.register()
        reload(wait = 0)
        // Widgets Android still holds but the home screen lost (say, the app was killed mid-add) are freed.
        lifecycleScope.launch { widgets.forgetAllBut(store.settings.first().widgets.mapTo(HashSet()) { it.id }) }
        setContent {
            val settings by store.settings.collectAsState(LauncherSettings())
            // Before any icon below is asked for, so none is drawn in the wrong shape first.
            repository.useIconShape(settings.iconShape)
            // The first time apps are known, fill the dock with the default Phone, Messages,
            // Browser and Camera apps (once: the store keeps it from happening again).
            val apps by repository.apps.collectAsState()
            LaunchedEffect(apps != null) {
                apps?.let { list -> store.seedDock(repository.defaultDock(list, this@MainActivity).map { it.key }) }
            }
            // The app being renamed, from any app's menu.
            var renaming by remember { mutableStateOf<AppEntry?>(null) }
            val focus = LocalFocusManager.current
            ZenithTheme(pureBlack = settings.pureBlack, fontVersion = settings.fontVersion) {
                CompositionLocalProvider(LocalRenameApp provides { renaming = it }) {
                    LauncherScreen(
                        repository = repository,
                        motion = drawer,
                        settings = settings,
                        drawerOpen = drawerOpen,
                        resets = resets,
                        entrances = entrances,
                        onDrawerOpenChange = { drawerOpen = it },
                        onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                        onSettings = { edit -> lifecycleScope.launch { store.edit() } },
                        onWallpaper = rememberWallpaperPicker(),
                        onGesture = ::onGesture,
                        onDockChange = { keys -> lifecycleScope.launch { store.setDock(keys) } },
                        onHomeChange = { pages -> lifecycleScope.launch { store.setHome(pages) } },
                        onLayoutChange = { pages, folders -> lifecycleScope.launch { store.setHomeLayout(pages, folders) } },
                        widgetHost = widgets,
                        onWidgetsChange = { list -> lifecycleScope.launch { store.setWidgets(list) } },
                    )
                }
                renaming?.let { app ->
                    RenameDialog(
                        current = app.label,
                        // The app's own name, as Android gives it, before any rename.
                        original = apps?.firstOrNull { it.key == app.key }?.label ?: app.label,
                        onSave = { name -> lifecycleScope.launch { store.setAppLabel(app.key, name) } },
                        // Focus would otherwise come back to the drawer's search field, keyboard and all.
                        onDismiss = { renaming = null; focus.clearFocus(force = true) },
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
        if (Gestures.perform(this, action)) return
        Toast.makeText(this, R.string.gesture_needs_service, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, SettingsActivity.PAGE_GESTURES))
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

    // Widgets update only while the home screen is started.
    override fun onStart() {
        super.onStart()
        widgets.startListening()
        repository.checkDate()
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
        stoppedAsleep = !getSystemService(PowerManager::class.java).isInteractive
        if (stoppedAsleep) return
        drawer.jump(0f)
        drawerOpen = false
        resets++
    }

    override fun onDestroy() {
        repository.unregister()
        super.onDestroy()
    }
}

private const val ReloadDelayMs = 300L
