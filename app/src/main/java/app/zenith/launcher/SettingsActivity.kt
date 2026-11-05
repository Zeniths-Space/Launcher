package app.zenith.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** The launcher's settings: home app status, then one page each for style, home screen, app drawer and about. */
class SettingsActivity : ComponentActivity() {

    private val store by lazy { SettingsStore(this) }
    private var isDefaultHome by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            val settings by store.settings.collectAsState(LauncherSettings())
            ZenithTheme(pureBlack = settings.pureBlack) {
                SettingsScreen(
                    settings = settings,
                    store = store,
                    isDefaultHome = isDefaultHome,
                    versionName = versionName,
                    onBack = ::finish,
                    onChooseHome = { open(Intent(Settings.ACTION_HOME_SETTINGS)) },
                    onWallpaper = rememberWallpaperPicker(),
                )
            }
        }
    }

    // The home app can change in system settings while this screen is away, so check on every return.
    override fun onResume() {
        super.onResume()
        isDefaultHome = isDefaultHome(this)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Whether Android would open this app for the home button now. */
fun isDefaultHome(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName == context.packageName
}

/** The settings pages: a list of categories, each opening its own page. */
private enum class Page { Main, Style, Home, Drawer, About }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: LauncherSettings,
    store: SettingsStore,
    isDefaultHome: Boolean,
    versionName: String,
    onBack: () -> Unit,
    onChooseHome: () -> Unit,
    onWallpaper: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val save: (suspend () -> Unit) -> Unit = { block -> scope.launch { block() } }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var page by rememberSaveable { mutableStateOf(Page.Main) }
    BackHandler(enabled = page != Page.Main) { page = Page.Main }
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page.title)) },
                navigationIcon = {
                    IconButton(onClick = { if (page == Page.Main) onBack() else page = Page.Main }) {
                        Icon(Icons.Previous, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surfaceContainer),
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        // Material's shared-axis X: a sub-page slides in from the end, back slides it out.
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != Page.Main
                val shift = { width: Int -> (if (forward) width else -width) / 10 }
                (slideInHorizontally(tween(300)) { shift(it) } + fadeIn(tween(150, delayMillis = 75)))
                    .togetherWith(slideOutHorizontally(tween(300)) { -shift(it) } + fadeOut(tween(75)))
            },
            label = "settings page",
        ) { shown ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            ) {
                when (shown) {
                    Page.Main -> {
                        HomeAppCard(isDefaultHome, onChooseHome)
                        Spacer(Modifier.height(24.dp))
                        SegmentedGroup(
                            listOf(
                                { CategoryTile(Icons.Wallpaper, Page.Style, stringResource(R.string.settings_style_sub)) { page = it } },
                                { CategoryTile(Icons.Home, Page.Home, stringResource(R.string.settings_home_sub)) { page = it } },
                                { CategoryTile(Icons.Apps, Page.Drawer, stringResource(R.string.settings_drawer_sub)) { page = it } },
                                { CategoryTile(Icons.Info, Page.About, stringResource(R.string.settings_version, versionName)) { page = it } },
                            ),
                        )
                    }
                    Page.Style -> SegmentedGroup(
                        listOf(
                            {
                                GroupTile(
                                    badge = { ScallopBadge(Icons.Wallpaper) },
                                    title = stringResource(R.string.settings_wallpaper),
                                    subtitle = stringResource(R.string.settings_wallpaper_sub),
                                    onClick = onWallpaper,
                                )
                            },
                            {
                                SwitchTile(Icons.DarkMode, stringResource(R.string.settings_pure_black), stringResource(R.string.settings_pure_black_sub), settings.pureBlack) {
                                    save { store.setPureBlack(it) }
                                }
                            },
                        ),
                        Modifier.padding(top = 8.dp),
                    )
                    Page.Home -> SegmentedGroup(
                        listOf(
                            {
                                SwitchTile(Icons.Clock, stringResource(R.string.settings_clock), stringResource(R.string.settings_clock_sub), settings.showClock) {
                                    save { store.setShowClock(it) }
                                }
                            },
                            {
                                SwitchTile(Icons.SwipeUp, stringResource(R.string.settings_hint), stringResource(R.string.settings_hint_sub), settings.showHint) {
                                    save { store.setShowHint(it) }
                                }
                            },
                        ),
                        Modifier.padding(top = 8.dp),
                    )
                    Page.Drawer -> SegmentedGroup(
                        listOf(
                            {
                                SwitchTile(Icons.Label, stringResource(R.string.settings_labels), stringResource(R.string.settings_labels_sub), settings.showLabels) {
                                    save { store.setShowLabels(it) }
                                }
                            },
                        ),
                        Modifier.padding(top = 8.dp),
                    )
                    Page.About -> SegmentedGroup(
                        listOf(
                            {
                                GroupTile(
                                    badge = { ScallopBadge(Icons.Info) },
                                    title = stringResource(R.string.app_name),
                                    subtitle = stringResource(R.string.settings_version, versionName),
                                )
                            },
                        ),
                        Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private val Page.title
    get() = when (this) {
        Page.Main -> R.string.settings_title
        Page.Style -> R.string.settings_section_style
        Page.Home -> R.string.settings_section_home
        Page.Drawer -> R.string.settings_section_drawer
        Page.About -> R.string.settings_section_about
    }

/** A row on the main page that opens a category's page. */
@Composable
private fun CategoryTile(icon: ImageVector, page: Page, summary: String, onOpen: (Page) -> Unit) {
    GroupTile(badge = { ScallopBadge(icon) }, title = stringResource(page.title), subtitle = summary, onClick = { onOpen(page) })
}

/** The first thing on the page: whether this is the home app, and the way to change it. */
@Composable
private fun HomeAppCard(isDefault: Boolean, onChoose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (container, content) = if (isDefault) cs.secondaryContainer to cs.onSecondaryContainer else cs.surfaceContainerHigh to cs.onSurface
    Surface(
        onClick = onChoose,
        shape = groupShape(0, 1),
        color = container,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(if (isDefault) cs.secondary else ZenithColors.indicator, ScallopShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isDefault) Icons.Done else Icons.Home,
                    contentDescription = null,
                    tint = if (isDefault) cs.onSecondary else ZenithColors.onIndicator,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (isDefault) R.string.settings_home_is_default else R.string.settings_home_not_default),
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(if (isDefault) R.string.settings_home_change else R.string.settings_home_choose),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Next, contentDescription = null, tint = content.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
        }
    }
}

/* Grouped tiles, the same building blocks as Zenboard's settings. */

/** Rounded segments stacked with small gaps (M3 Expressive grouped list). */
@Composable
private fun SegmentedGroup(segments: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        segments.forEachIndexed { i, segment ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = groupShape(i, segments.size),
                modifier = Modifier.fillMaxWidth(),
            ) { segment() }
        }
    }
}

@Composable
private fun GroupTile(
    badge: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badge()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        when {
            trailing != null -> {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
            onClick != null -> {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Next, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A tile whose whole row toggles a switch (one touch target, announced as a switch). */
@Composable
private fun SwitchTile(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    GroupTile(
        badge = { ScallopBadge(icon) },
        title = title,
        subtitle = subtitle,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedThumbColor = cs.onSecondary, checkedTrackColor = cs.secondary, checkedBorderColor = cs.secondary),
            )
        },
    )
}

@Composable
internal fun ScallopBadge(icon: ImageVector, container: Color = ZenithColors.indicator, content: Color = ZenithColors.onIndicator) {
    Box(Modifier.size(44.dp).background(container, ScallopShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}
