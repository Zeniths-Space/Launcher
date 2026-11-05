package app.zenith.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** The home screen. One instance (singleTask); pressing home while it shows closes the drawer. */
class MainActivity : ComponentActivity() {

    private val repository by lazy { AppRepository(this) }
    private val store by lazy { SettingsStore(this) }
    private var drawerOpen by mutableStateOf(false)
    private val drawer = DrawerMotion()
    private val launch = LaunchState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository.onChanged = { reload() }
        repository.register()
        reload()
        setContent {
            val settings by store.settings.collectAsState(LauncherSettings())
            ZenithTheme(pureBlack = settings.pureBlack) {
                LauncherScreen(
                    repository = repository,
                    motion = drawer,
                    launch = launch,
                    settings = settings,
                    drawerOpen = drawerOpen,
                    onDrawerOpenChange = { drawerOpen = it },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                )
            }
        }
    }

    private fun reload() {
        lifecycleScope.launch { repository.load(packageName) }
    }

    // Home pressed while already home: back to the home screen, like every launcher.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) drawerOpen = false
    }

    // Back from an app (or anywhere) to the home screen, like One UI: the drawer is already closed
    // when the home screen shows again, instead of animating shut over the app's closing animation.
    override fun onStop() {
        super.onStop()
        drawer.jump(0f)
        drawerOpen = false
        launch.current = null
    }

    override fun onDestroy() {
        repository.unregister()
        super.onDestroy()
    }
}
