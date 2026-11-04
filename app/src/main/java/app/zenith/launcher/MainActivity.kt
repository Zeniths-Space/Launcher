package app.zenith.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** The home screen. One instance (singleTask); pressing home while it shows closes the drawer. */
class MainActivity : ComponentActivity() {

    private val repository by lazy { AppRepository(this) }
    private var drawerOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository.onChanged = { reload() }
        repository.register()
        reload()
        setContent {
            ZenithTheme {
                LauncherScreen(
                    repository = repository,
                    drawerOpen = drawerOpen,
                    onDrawerOpenChange = { drawerOpen = it },
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

    override fun onDestroy() {
        repository.unregister()
        super.onDestroy()
    }
}
