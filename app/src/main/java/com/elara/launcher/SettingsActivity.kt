package com.elara.launcher

import androidx.compose.foundation.Image
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider

/** The launcher's settings: home app status, then one page each for appearance, dock, home screen, app drawer, gestures and about. */
class SettingsActivity : ComponentActivity() {

    private val store by lazy { SettingsStore(this) }
    private val repository by lazy { AppRepository(this) }
    private var isDefaultHome by mutableStateOf(false)
    private var gesturesOn by mutableStateOf(false)

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
                    repository = repository,
                    isDefaultHome = isDefaultHome,
                    gesturesOn = gesturesOn,
                    versionName = versionName,
                    startPage = if (intent.getStringExtra(EXTRA_PAGE) == PAGE_GESTURES) Page.Gestures else Page.Main,
                    onBack = ::finish,
                    onChooseHome = { openSystem(Settings.ACTION_HOME_SETTINGS) },
                    onWallpaper = rememberWallpaperPicker(),
                    onAccessibility = { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    onAppInfo = { openSystem(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)) },
                    onOpenLink = { url -> openSystem(Intent.ACTION_VIEW, url.toUri()) },
                )
            }
        }
    }

    // The home app can change in system settings while this screen is away, so check on every return.
    override fun onResume() {
        super.onResume()
        isDefaultHome = isDefaultHome(this)
        gesturesOn = GestureService.isEnabled(this)
    }

    companion object {
        /** Opens settings on a page: [PAGE_GESTURES]. */
        const val EXTRA_PAGE = "page"
        const val PAGE_GESTURES = "gestures"
    }
}

/** Opens a system settings screen, or says it isn't there (some devices leave screens out). */
fun Context.openSystem(action: String, data: Uri? = null) {
    try {
        startActivity(Intent(action, data))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }
}

/** Whether Android would open this app for the home button now. */
fun isDefaultHome(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName == context.packageName
}

/** The settings pages: a list of categories, each opening its own page. */
internal enum class Page { Main, Appearance, Dock, Home, Drawer, Gestures, About }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    settings: LauncherSettings,
    store: SettingsStore,
    repository: AppRepository,
    isDefaultHome: Boolean,
    gesturesOn: Boolean,
    versionName: String,
    startPage: Page,
    onBack: () -> Unit,
    onChooseHome: () -> Unit,
    onWallpaper: () -> Unit,
    onAccessibility: () -> Unit,
    onAppInfo: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val save: (suspend () -> Unit) -> Unit = { block -> scope.launch { block() } }
    var page by rememberSaveable { mutableStateOf(startPage) }
    val leave = { if (page == Page.Main) onBack() else page = Page.Main }
    BackHandler(enabled = page != Page.Main) { page = Page.Main }
    // Material's shared-axis X: a sub-page slides in from the end with its own title, back slides it out.
    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize().background(cs.surface),
        transitionSpec = {
            val forward = targetState != Page.Main
            val shift = { width: Int -> (if (forward) width else -width) / 10 }
            (slideInHorizontally(tween(300)) { shift(it) } + fadeIn(tween(150, delayMillis = 75)))
                .togetherWith(slideOutHorizontally(tween(300)) { -shift(it) } + fadeOut(tween(75)))
        },
        label = "settings page",
    ) { shown ->
        // Each page's own title bar, beside the back button, so the title slides with its page.
        val scroll = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        // What the drawer sliders show while dragging, before the stored values catch up.
        var columns by remember(settings.drawerColumns) { mutableIntStateOf(settings.drawerColumns) }
        var iconSize by remember(settings.drawerIconSize) { mutableIntStateOf(settings.drawerIconSize) }
        var rowGap by remember(settings.drawerRowGap) { mutableIntStateOf(settings.drawerRowGap) }
        var sidePadding by remember(settings.drawerPadding) { mutableIntStateOf(settings.drawerPadding) }
            Column(Modifier.fillMaxSize().background(cs.surface).nestedScroll(scroll.nestedScrollConnection)) {
            val title = @Composable { Text(stringResource(shown.title)) }
            val back = @Composable {
                IconButton(onClick = leave) {
                    Icon(Icons.Previous, contentDescription = stringResource(R.string.back))
                }
            }
            val barColors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surface)
            TopAppBar(
                title = title,
                navigationIcon = back,
                colors = barColors,
                scrollBehavior = scroll,
            )
            // Pinned above the list, so it stays in view while any slider is dragged.
            if (shown == Page.Drawer) DrawerPreview(repository, settings.hidden, settings.showLabels, columns, iconSize.dp, rowGap.dp, sidePadding.dp)
            CompositionLocalProvider(LocalAccent provides shown.accent) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            ) {
                when (shown) {
                    Page.Main -> {
                        SettingsHero(repository, settings, isDefaultHome, onChooseHome, onWallpaper)
                        Spacer(Modifier.height(20.dp))
                        val categories = categoryTiles(versionName) { page = it }
                        SegmentedGroup(categories.take(4))
                        Spacer(Modifier.height(16.dp))
                        SegmentedGroup(categories.drop(4))
                    }
                    Page.Appearance -> SegmentedGroup(
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
                    Page.Home -> {
                    SegmentedGroup(
                        listOf(
                            {
                                SwitchTile(Icons.Label, stringResource(R.string.settings_home_labels), stringResource(R.string.settings_home_labels_sub), settings.homeLabels) {
                                    save { store.setHomeLabels(it) }
                                }
                            },
                            {
                                SwitchTile(Icons.Dots, stringResource(R.string.settings_home_dots), stringResource(R.string.settings_home_dots_sub), settings.homeDots) {
                                    save { store.setHomeDots(it) }
                                }
                            },
                        ),
                        Modifier.padding(top = 8.dp),
                    )
                    SectionHeader(stringResource(R.string.settings_layout))
                    SegmentedGroup(
                        listOf(
                            {
                                SliderTile(Icons.Columns, stringResource(R.string.settings_home_columns), null, settings.homeColumns, DockGridRange) {
                                    save { store.setHomeColumns(it) }
                                }
                            },
                            {
                                SliderTile(Icons.Rows, stringResource(R.string.settings_home_rows), null, settings.homeRows, DockGridRange) {
                                    save { store.setHomeRows(it) }
                                }
                            },
                            {
                                SliderTile(Icons.IconSize, stringResource(R.string.settings_home_icon_size), null, settings.homeIconSize, DockIconRange, step = 4, format = R.string.settings_value_dp) {
                                    save { store.setHomeIconSize(it) }
                                }
                            },
                        ),
                    )
                    }
                    Page.Drawer -> {
                        SegmentedGroup(
                            listOf(
                                {
                                    SwitchTile(Icons.Label, stringResource(R.string.settings_labels), stringResource(R.string.settings_labels_sub), settings.showLabels) {
                                        save { store.setShowLabels(it) }
                                    }
                                },
                                {
                                    SwitchTile(Icons.History, stringResource(R.string.settings_drawer_keep), stringResource(R.string.settings_drawer_keep_sub), settings.drawerKeepPosition) {
                                        save { store.setDrawerKeepPosition(it) }
                                    }
                                },
                            ),
                            Modifier.padding(top = 16.dp),
                        )
                        SectionHeader(stringResource(R.string.settings_layout))
                        SegmentedGroup(
                            listOf(
                                {
                                    SliderTile(Icons.Columns, stringResource(R.string.settings_drawer_columns), null, settings.drawerColumns, DrawerColumnRange, onLive = { columns = it }) {
                                        save { store.setDrawerColumns(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.IconSize, stringResource(R.string.settings_drawer_icon_size), null, settings.drawerIconSize, DockIconRange, step = 4, format = R.string.settings_value_dp, onLive = { iconSize = it }) {
                                        save { store.setDrawerIconSize(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.Rows, stringResource(R.string.settings_drawer_row_gap), null, settings.drawerRowGap, DrawerSpacingRange, step = 4, format = R.string.settings_value_dp, onLive = { rowGap = it }) {
                                        save { store.setDrawerRowGap(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.SidePadding, stringResource(R.string.settings_drawer_padding), null, settings.drawerPadding, DrawerSpacingRange, step = 4, format = R.string.settings_value_dp, onLive = { sidePadding = it }) {
                                        save { store.setDrawerPadding(it) }
                                    }
                                },
                            ),
                            Modifier.padding(top = 16.dp),
                        )
                    }
                    Page.Dock -> {
                        SegmentedGroup(
                            listOf(
                                {
                                    SwitchTile(Icons.Dock, stringResource(R.string.settings_dock_show), stringResource(R.string.settings_dock_show_sub), settings.showDock) {
                                        save { store.setShowDock(it) }
                                    }
                                },
                                {
                                    SwitchTile(Icons.Apps, stringResource(R.string.settings_dock_bar), stringResource(R.string.settings_dock_bar_sub), settings.dockBar) {
                                        save { store.setDockBar(it) }
                                    }
                                },
                            ),
                            Modifier.padding(top = 8.dp),
                        )
                        SectionHeader(stringResource(R.string.settings_layout))
                        SegmentedGroup(
                            listOf(
                                {
                                    SliderTile(Icons.Columns, stringResource(R.string.settings_dock_columns), null, settings.dockColumns, DockGridRange) {
                                        save { store.setDockColumns(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.Rows, stringResource(R.string.settings_dock_rows), null, settings.dockRows, DockGridRange) {
                                        save { store.setDockRows(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.IconSize, stringResource(R.string.settings_dock_icon_size), null, settings.dockIconSize, DockIconRange, step = 4, format = R.string.settings_value_dp) {
                                        save { store.setDockIconSize(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.Rows, stringResource(R.string.settings_dock_row_gap), null, settings.dockRowGap, DockRowGapRange, step = 2, format = R.string.settings_value_dp) {
                                        save { store.setDockRowGap(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.Columns, stringResource(R.string.settings_dock_column_gap), null, settings.dockColumnGap, DockColumnGapRange, step = 2, format = R.string.settings_value_dp) {
                                        save { store.setDockColumnGap(it) }
                                    }
                                },
                            ),
                            Modifier.padding(top = 8.dp),
                        )
                        Text(
                            stringResource(R.string.settings_dock_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                    Page.Gestures -> {
                        // The service is only asked for when a chosen gesture needs it (locking).
                        val context = LocalContext.current
                        val needed = Gestures.needsService(context, settings.swipeDown) || Gestures.needsService(context, settings.doubleTap)
                        if (needed || gesturesOn) GesturesCard(gesturesOn, onAccessibility)
                        if (needed && !gesturesOn) {
                            Text(
                                stringResource(R.string.gesture_service_restricted),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button, onClick = onAppInfo)
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                        SectionHeader(stringResource(R.string.gesture_swipe_down))
                        ChoiceGroup(
                            listOf(GestureAction.Notifications, GestureAction.QuickSettings, GestureAction.None),
                            settings.swipeDown,
                        ) { save { store.setSwipeDown(it) } }
                        SectionHeader(stringResource(R.string.gesture_double_tap))
                        ChoiceGroup(
                            listOf(GestureAction.LockScreen, GestureAction.None).filter { it.available },
                            settings.doubleTap,
                        ) { save { store.setDoubleTap(it) } }
                    }
                    Page.About -> AboutPage(versionName, onAppInfo, onOpenLink)
                }
                Spacer(Modifier.height(24.dp))
            }
            }
            }
    }
}

private val Page.title
    get() = when (this) {
        Page.Main -> R.string.settings_title
        Page.Appearance -> R.string.settings_section_appearance
        Page.Dock -> R.string.settings_section_dock
        Page.Home -> R.string.settings_section_home
        Page.Drawer -> R.string.settings_section_drawer
        Page.Gestures -> R.string.settings_section_gestures
        Page.About -> R.string.settings_section_about
    }

/** Whether the gestures service is on, and the way to Android's accessibility settings. */
@Composable
private fun GesturesCard(on: Boolean, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (container, content) = if (on) cs.secondaryContainer to cs.onSecondaryContainer else cs.surfaceContainerHigh to cs.onSurface
    Surface(onClick = onOpen, shape = groupShape(0, 1), color = container, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ScallopBadge(
                if (on) Icons.Done else Icons.Gesture,
                container = if (on) cs.secondary else ZenithColors.indicator,
                content = if (on) cs.onSecondary else ZenithColors.onIndicator,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (on) R.string.gesture_service_on else R.string.gesture_service_off), style = MaterialTheme.typography.titleMedium, color = content)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(if (on) R.string.gesture_service_on_sub else R.string.gesture_service_off_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Next, contentDescription = null, tint = content.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
        }
    }
}

/** One choice of several, a row each, with a radio button; the whole row selects. */
@Composable
private fun ChoiceGroup(options: List<GestureAction>, selected: GestureAction, onSelect: (GestureAction) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.selectableGroup()) {
        SegmentedGroup(
            options.map { option ->
                {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(option.label), style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                        RadioButton(selected = option == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = LocalAccent.current?.strong ?: cs.secondary))
                    }
                }
            },
        )
    }
}

private val GestureAction.label
    get() = when (this) {
        GestureAction.None -> R.string.gesture_none
        GestureAction.Notifications -> R.string.gesture_notifications
        GestureAction.QuickSettings -> R.string.gesture_quick_settings
        GestureAction.LockScreen -> R.string.gesture_lock
    }

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = LocalAccent.current?.strong ?: MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 8.dp, top = 28.dp, bottom = 10.dp),
    )
}

/** The main page's categories, in order; the home sheet shows the first four. */
internal fun categoryTiles(versionName: String, onOpen: (Page) -> Unit): List<@Composable () -> Unit> = listOf(
    { CategoryTile(Icons.Wallpaper, Page.Appearance, stringResource(R.string.settings_appearance_sub), onOpen) },
    { CategoryTile(Icons.Dock, Page.Dock, stringResource(R.string.settings_dock_sub), onOpen) },
    { CategoryTile(Icons.Home, Page.Home, stringResource(R.string.settings_home_sub), onOpen) },
    { CategoryTile(Icons.Apps, Page.Drawer, stringResource(R.string.settings_drawer_sub), onOpen) },
    { CategoryTile(Icons.Gesture, Page.Gestures, stringResource(R.string.settings_gestures_sub), onOpen) },
    { CategoryTile(Icons.Info, Page.About, stringResource(R.string.settings_version, versionName), onOpen) },
)

/** The app with its icon and version, what it does and doesn't do, then who made it and where to find them. */
@Composable
private fun AboutPage(versionName: String, onAppInfo: () -> Unit, onOpenLink: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val iconPx = with(LocalDensity.current) { 88.dp.roundToPx() }
    val icon = remember(iconPx) { context.packageManager.getApplicationIcon(context.packageName).toBitmap(iconPx, iconPx).asImageBitmap() }
    val link = @Composable { Icon(Icons.OpenInNew, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
        Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
    SectionHeader(stringResource(R.string.about_section_app))
    SegmentedGroup(
        listOf(
            { GroupTile(badge = { ScallopBadge(Icons.Shield) }, title = stringResource(R.string.about_privacy), subtitle = stringResource(R.string.about_privacy_sub)) },
            {
                GroupTile(
                    badge = { ScallopBadge(Icons.Code) },
                    title = stringResource(R.string.about_source),
                    subtitle = stringResource(R.string.about_source_sub),
                    onClick = { onOpenLink(SourceUrl) },
                    trailing = link,
                )
            },
            { GroupTile(badge = { ScallopBadge(Icons.Info) }, title = stringResource(R.string.about_app_info), subtitle = stringResource(R.string.about_app_info_sub), onClick = onAppInfo) },
        ),
    )
    SectionHeader(stringResource(R.string.about_section_developer))
    SegmentedGroup(
        listOf(
            { GroupTile(badge = { ScallopBadge(Icons.Person) }, title = stringResource(R.string.about_developer), subtitle = stringResource(R.string.about_developer_sub)) },
            {
                GroupTile(
                    badge = { ScallopBadge(Icons.Code) },
                    title = stringResource(R.string.about_github),
                    subtitle = stringResource(R.string.about_github_sub),
                    onClick = { onOpenLink(GitHubUrl) },
                    trailing = link,
                )
            },
            {
                GroupTile(
                    badge = { ScallopBadge(Icons.Language) },
                    title = stringResource(R.string.about_website),
                    subtitle = stringResource(R.string.about_website_sub),
                    onClick = { onOpenLink(WebsiteUrl) },
                    trailing = link,
                )
            },
        ),
    )
}

private const val SourceUrl = "https://github.com/Zeniths-Space/Launcher"
private const val GitHubUrl = "https://github.com/ashutoshchettri"
private const val WebsiteUrl = "https://aashutoshkc.com.np/"

/** A row on the main page that opens a category's page. */
@Composable
private fun CategoryTile(icon: ImageVector, page: Page, summary: String, onOpen: (Page) -> Unit) {
    GroupTile(badge = { AccentBadge(icon, page.accent, page.shape, size = 48) }, title = stringResource(page.title), subtitle = summary, onClick = { onOpen(page) })
}

/* Grouped tiles, the same building blocks as Zenboard's settings. */

/** Rounded segments stacked with small gaps (M3 Expressive grouped list). */
@Composable
internal fun SegmentedGroup(segments: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        segments.forEachIndexed { i, segment ->
            // Pure black: a neutral grey, without the ladder's blue tint.
            Surface(
                color = if (LocalPureBlack.current) PureBlackCard else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = groupShape(i, segments.size),
                modifier = Modifier.fillMaxWidth(),
            ) { segment() }
        }
    }
}

@Composable
internal fun GroupTile(
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

/** The first drawer rows, drawn like the drawer at the screen's full width, so columns and side padding look as they will. */
@Composable
private fun DrawerPreview(repository: AppRepository, hidden: Set<String>, showLabels: Boolean, columns: Int, iconSize: Dp, rowGap: Dp, sidePadding: Dp) {
    val context = LocalContext.current
    val apps by repository.apps.collectAsState()
    // Opened from the launcher the list is already there; on its own, settings loads it once.
    LaunchedEffect(repository) { if (repository.apps.value == null) repository.load(context.packageName) }
    // One size for every slider position: the image scales, so dragging doesn't redraw icons.
    val sizePx = with(LocalDensity.current) { DockIconRange.last.dp.roundToPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .clipToBounds()
            // On the pure black page, black would vanish into it: the cards' grey sets it apart.
            .background(if (LocalPureBlack.current) PureBlackCard else Color.Black)
            .clearAndSetSemantics {}
            .padding(horizontal = sidePadding, vertical = 8.dp),
    ) {
        Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true), verticalArrangement = Arrangement.spacedBy(rowGap)) {
            apps.orEmpty().filter { it.key !in hidden }.take(columns * PreviewRows).chunked(columns).forEach { row ->
                Row {
                    row.forEach { app ->
                        val icon by produceState<ImageBitmap?>(null, app.key) { value = repository.icon(app, sizePx) }
                        AppTileContent(icon, app.label, showLabels, iconSize, Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val PreviewHeight = 200.dp
private const val PreviewRows = 3

/** A tile whose whole row toggles a switch (one touch target, announced as a switch). */
@Composable
private fun SwitchTile(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalAccent.current
    val on = accent?.strong ?: cs.secondary
    GroupTile(
        badge = { ScallopBadge(icon) },
        title = title,
        subtitle = subtitle,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedThumbColor = accent?.onStrong ?: cs.onSecondary, checkedTrackColor = on, checkedBorderColor = on),
            )
        },
    )
}

/** A tile with a slider over whole numbers in [range]; [onChange] runs once the finger lifts, not on every step. */
@Composable
private fun SliderTile(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    value: Int,
    range: IntRange,
    step: Int = 1,
    format: Int? = null,
    onLive: (Int) -> Unit = {},
    onChange: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalAccent.current
    val strong = accent?.strong ?: cs.secondary
    // Follows the finger; the stored value catches up when it lifts.
    var shown by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val label = format?.let { stringResource(it, shown.roundToInt()) } ?: shown.roundToInt().toString()
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScallopBadge(icon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            // The value in a pill of the page's colour.
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = accent?.content ?: cs.onSecondaryContainer,
                modifier = Modifier
                    .background(accent?.container ?: cs.secondaryContainer, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Slider(
            value = shown,
            onValueChange = {
                shown = it
                onLive(it.roundToInt())
            },
            onValueChangeFinished = { if (shown.roundToInt() != value) onChange(shown.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            colors = SliderDefaults.colors(
                thumbColor = strong,
                activeTrackColor = strong,
                activeTickColor = accent?.onStrong ?: cs.onSecondary,
                inactiveTrackColor = accent?.container ?: cs.secondaryContainer,
                inactiveTickColor = accent?.content ?: cs.onSecondaryContainer,
            ),
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
internal fun ScallopBadge(icon: ImageVector, container: Color? = null, content: Color? = null) {
    // On a settings page, the page's colour.
    val accent = LocalAccent.current
    Box(Modifier.size(44.dp).background(container ?: accent?.container ?: ZenithColors.indicator, accent?.shape ?: ScallopShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content ?: accent?.content ?: ZenithColors.onIndicator, modifier = Modifier.size(20.dp))
    }
}
