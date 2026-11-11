package com.elara.launcher

import androidx.compose.material3.AlertDialog
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.ModalBottomSheet
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
            // Before any icon below is asked for, so none is drawn in the wrong shape first.
            repository.useIconShape(settings.iconShape)
            repository.useIconPack(settings.iconPack)
            ZenithTheme(fontVersion = settings.fontVersion) {
                SettingsScreen(
                    settings = settings,
                    store = store,
                    repository = repository,
                    isDefaultHome = isDefaultHome,
                    gesturesOn = gesturesOn,
                    versionName = versionName,
                    startPage = when (intent.getStringExtra(EXTRA_PAGE)) {
                        PAGE_GESTURES -> Page.Gestures
                        PAGE_SPACES -> Page.Spaces
                        else -> Page.Main
                    },
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
        /** Opens settings on a page: [PAGE_GESTURES] or [PAGE_SPACES]. */
        const val EXTRA_PAGE = "page"
        const val PAGE_GESTURES = "gestures"
        const val PAGE_SPACES = "spaces"
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
internal enum class Page { Main, Appearance, Dock, Home, Drawer, Gestures, Spaces, Backup, About }

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
                            { IconShapeTile(settings, store) },
                            { IconPackTile(settings, store) },
                            { FontTile(settings, store) },
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
                        SectionHeader(stringResource(R.string.settings_look))
                        SegmentedGroup(
                            buildList {
                                add {
                                    SwitchTile(Icons.Label, stringResource(R.string.settings_labels), stringResource(R.string.settings_labels_sub), settings.showLabels) {
                                        save { store.setShowLabels(it) }
                                    }
                                }
                                add {
                                    SwitchTile(Icons.BlurOn, stringResource(R.string.settings_drawer_blur), stringResource(R.string.settings_drawer_blur_sub), settings.drawerBlur) {
                                        save { store.setDrawerBlur(it) }
                                    }
                                }
                                if (settings.drawerBlur) add { BlurStyleTile(settings.drawerBlurStyle) { style -> save { store.setDrawerBlurStyle(style) } } }
                            },
                        )
                        SectionHeader(stringResource(R.string.settings_behaviour))
                        SegmentedGroup(
                            listOf(
                                {
                                    SwitchTile(Icons.History, stringResource(R.string.settings_drawer_keep), stringResource(R.string.settings_drawer_keep_sub), settings.drawerKeepPosition) {
                                        save { store.setDrawerKeepPosition(it) }
                                    }
                                },
                                {
                                    SwitchTile(Icons.Person, stringResource(R.string.settings_drawer_contacts), stringResource(R.string.settings_drawer_contacts_sub), settings.drawerContacts) {
                                        save { store.setDrawerContacts(it) }
                                    }
                                },
                            ),
                        )
                        SectionHeader(stringResource(R.string.settings_layout))
                        SegmentedGroup(
                            listOf(
                                {
                                    SliderTile(Icons.Columns, stringResource(R.string.settings_drawer_columns), null, settings.drawerColumns, DrawerColumnRange) {
                                        save { store.setDrawerColumns(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.IconSize, stringResource(R.string.settings_drawer_icon_size), null, settings.drawerIconSize, DockIconRange, step = 4, format = R.string.settings_value_dp) {
                                        save { store.setDrawerIconSize(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.Rows, stringResource(R.string.settings_drawer_row_gap), null, settings.drawerRowGap, DrawerSpacingRange, step = 4, format = R.string.settings_value_dp) {
                                        save { store.setDrawerRowGap(it) }
                                    }
                                },
                                {
                                    SliderTile(Icons.SidePadding, stringResource(R.string.settings_drawer_padding), null, settings.drawerPadding, DrawerSpacingRange, step = 4, format = R.string.settings_value_dp) {
                                        save { store.setDrawerPadding(it) }
                                    }
                                },
                            ),
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
                            { it.label },
                        ) { save { store.setSwipeDown(it) } }
                        SectionHeader(stringResource(R.string.gesture_double_tap))
                        ChoiceGroup(
                            listOf(GestureAction.LockScreen, GestureAction.None).filter { it.available },
                            settings.doubleTap,
                            { it.label },
                        ) { save { store.setDoubleTap(it) } }
                    }
                    Page.Spaces -> SpacesPage(settings, store)
                    Page.Backup -> BackupPage(store)
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
        Page.Spaces -> R.string.settings_section_spaces
        Page.Backup -> R.string.settings_section_backup
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
private fun <T> ChoiceGroup(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
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
                        Text(stringResource(label(option)), style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
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
    { CategoryTile(Icons.Work, Page.Spaces, stringResource(R.string.settings_spaces_sub), onOpen) },
    { CategoryTile(Icons.Upload, Page.Backup, stringResource(R.string.settings_backup_sub), onOpen) },
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

/**
 * The spaces: each with its icon, name and schedule (the live one marked), a button for a new one,
 * and the switches for the schedule and the name shown on a change. Tap one to change it.
 */
@Composable
private fun SpacesPage(settings: LauncherSettings, store: SettingsStore) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Space?>(null) }
    var adding by remember { mutableStateOf(false) }
    val defaultName = stringResource(R.string.space_default_name)
    val dayNames = listOf(R.string.day_sun, R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat).map { stringResource(it) }
    val everyDay = stringResource(R.string.space_every_day)
    val weekdays = stringResource(R.string.space_weekdays)
    val weekends = stringResource(R.string.space_weekends)
    val rest = stringResource(R.string.space_rest_of_time)
    val unscheduled = stringResource(R.string.space_by_hand)
    // Before the first space exists, the home as it is shows as the default one.
    val spaces = settings.spaces.ifEmpty { listOf(Space(1, defaultName)) }
    val active = settings.activeSpace.takeIf { id -> spaces.any { it.id == id } } ?: spaces.first().id
    Text(
        stringResource(R.string.spaces_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = cs.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    SectionHeader(stringResource(R.string.spaces_yours))
    SegmentedGroup(
        spaces.mapIndexed { i, space ->
            @Composable {
                GroupTile(
                    badge = { ScallopBadge(space.icon.vector()) },
                    title = space.name,
                    subtitle = if (i == 0) rest else Spaces.describe(space, dayNames, everyDay, weekdays, weekends) ?: unscheduled,
                    onClick = { editing = space },
                    trailing = if (space.id == active) ({
                        Text(
                            stringResource(R.string.space_now),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF7C4DFF)).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }) else null,
                )
            }
        } + listOf<@Composable () -> Unit>({
            GroupTile(
                badge = { ScallopBadge(Icons.Add) },
                title = stringResource(R.string.space_new),
                subtitle = stringResource(R.string.space_new_sub),
                onClick = { adding = true },
            )
        }),
    )
    SectionHeader(stringResource(R.string.spaces_switching))
    SegmentedGroup(
        listOf(
            {
                SwitchTile(Icons.Clock, stringResource(R.string.spaces_by_schedule), stringResource(R.string.spaces_by_schedule_sub), settings.spacesBySchedule) {
                    scope.launch { store.setSpacesBySchedule(it) }
                }
            },
            {
                SwitchTile(Icons.Label, stringResource(R.string.spaces_show_name), stringResource(R.string.spaces_show_name_sub), settings.spacesShowName) {
                    scope.launch { store.setSpacesShowName(it) }
                }
            },
        ),
    )
    if (adding) {
        SpaceSheet(
            editing = null,
            isDefault = false,
            onSave = { space, copy -> scope.launch { store.addSpace(space, copy, defaultName) } },
            onDelete = null,
            onClose = { adding = false },
        )
    }
    editing?.let { space ->
        val isDefault = space.id == spaces.first().id
        SpaceSheet(
            editing = space,
            isDefault = isDefault,
            onSave = { changed, _ ->
                scope.launch {
                    // Renaming the default before any other space exists makes the list, with just it.
                    if (settings.spaces.isEmpty()) store.addFirstSpace(changed) else store.updateSpace(changed)
                }
            },
            onDelete = if (isDefault) null else ({ scope.launch { store.deleteSpace(space.id) } }),
            onClose = { editing = null },
        )
    }
}

/**
 * Back up the layout and settings to a file the user picks (Android's file picker: no storage
 * permission), or restore them from one after asking. The file stays wherever they put it.
 */
@Composable
private fun BackupPage(store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoreFrom by remember { mutableStateOf<android.net.Uri?>(null) }
    val toast = { id: Int -> Toast.makeText(context, id, Toast.LENGTH_SHORT).show() }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.backup().toByteArray()) } }.isSuccess
            }
            toast(if (ok) R.string.backup_saved else R.string.backup_failed)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreFrom = uri }
    SectionHeader(stringResource(R.string.backup_section))
    SegmentedGroup(
        listOf(
            {
                GroupTile(
                    badge = { ScallopBadge(Icons.Upload) },
                    title = stringResource(R.string.backup_save),
                    subtitle = stringResource(R.string.backup_save_sub),
                    onClick = { save.launch("Elara Launcher backup ${java.time.LocalDate.now()}.txt") },
                )
            },
            {
                GroupTile(
                    badge = { ScallopBadge(Icons.Download) },
                    title = stringResource(R.string.backup_restore),
                    subtitle = stringResource(R.string.backup_restore_sub),
                    onClick = { open.launch(arrayOf("text/plain", "application/octet-stream")) },
                )
            },
        ),
    )
    restoreFrom?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreFrom = null },
            icon = { Icon(Icons.Download, contentDescription = null) },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_body)) },
            confirmButton = {
                TextButton(onClick = {
                    restoreFrom = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            // A backup is small; anything over 1 MB isn't one.
                            val text = runCatching {
                                context.contentResolver.openInputStream(uri)!!.use { input ->
                                    // readNBytes needs API 33; this is the same for older phones.
                                    val buf = ByteArray(1_000_000)
                                    var n = 0
                                    while (n < buf.size) {
                                        val read = input.read(buf, n, buf.size - n)
                                        if (read < 0) break
                                        n += read
                                    }
                                    String(buf, 0, n)
                                }
                            }.getOrNull()
                            text != null && store.restore(text)
                        }
                        toast(if (ok) R.string.backup_restored else R.string.backup_not_backup)
                    }
                }) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { restoreFrom = null }) { Text(stringResource(R.string.rename_cancel)) } },
            containerColor = if (LocalDark.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
        )
    }
}

private const val SourceUrl = "https://github.com/Zeniths-Space/Launcher"
private const val GitHubUrl = "https://github.com/ashutoshchettri"
private const val WebsiteUrl = "https://aashutoshkc.com.np/"

/** A row on the main page that opens a category's page. */
@Composable
private fun CategoryTile(icon: ImageVector, page: Page, summary: String, onOpen: (Page) -> Unit) {
    GroupTile(badge = { AccentBadge(icon, page.accent, page.shape, size = 48) }, title = stringResource(page.title), subtitle = summary, onClick = { onOpen(page) })
}

/** The icon pack: a tile naming it, opening a sheet of the installed packs and the apps' own icons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPackTile(settings: LauncherSettings, store: SettingsStore) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var choosing by rememberSaveable { mutableStateOf(false) }
    // Read when settings open and each time the sheet does, so a pack installed meanwhile shows.
    val packs = remember(choosing) { IconPacks.installed(context.packageManager) }
    val none = stringResource(R.string.icon_pack_none)
    GroupTile(
        badge = { ScallopBadge(Icons.Widgets) },
        title = stringResource(R.string.settings_icon_pack),
        subtitle = packs.firstOrNull { it.packageName == settings.iconPack }?.label ?: none,
        onClick = { choosing = true },
    )
    if (!choosing) return
    ModalBottomSheet(
        onDismissRequest = { choosing = false },
        containerColor = if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerLow,
    ) {
        Text(
            stringResource(R.string.settings_icon_pack),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        val accent = LocalAccent.current
        val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
        Column(Modifier.selectableGroup().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            (listOf(IconPackInfo("", none)) + packs).forEach { pack ->
                val selected = pack.packageName == settings.iconPack
                val icon = remember(pack.packageName) {
                    runCatching { context.packageManager.getApplicationIcon(pack.packageName).toBitmap(iconPx, iconPx).asImageBitmap() }.getOrNull()
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .selectable(selected = selected, role = Role.RadioButton) {
                            scope.launch { store.setIconPack(pack.packageName) }
                            choosing = false
                        }
                        .background(if (selected) (accent?.container ?: cs.secondaryContainer) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (icon != null && pack.packageName.isNotEmpty()) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        else Icon(Icons.Apps, contentDescription = null, tint = accent?.strong ?: cs.primary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        pack.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) (accent?.content ?: cs.onSecondaryContainer) else cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Done, contentDescription = null, tint = accent?.content ?: cs.primary)
                }
            }
            if (packs.isEmpty()) {
                Text(
                    stringResource(R.string.icon_pack_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** The outline of every app icon: a sheet of shapes, each previewed with a real app's icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconShapeTile(settings: LauncherSettings, store: SettingsStore) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var choosing by rememberSaveable { mutableStateOf(false) }
    GroupTile(
        badge = { ScallopBadge(Icons.Apps) },
        title = stringResource(R.string.settings_icon_shape),
        subtitle = stringResource(settings.iconShape.label),
        onClick = { choosing = true },
    )
    if (!choosing) return
    ModalBottomSheet(
        onDismissRequest = { choosing = false },
        containerColor = if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerLow,
    ) {
        Text(
            stringResource(R.string.settings_icon_shape),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        val accent = LocalAccent.current
        Column(
            Modifier.selectableGroup().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            IconShape.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { shape ->
                        val selected = shape == settings.iconShape
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    scope.launch { store.setIconShape(shape) }
                                }
                                .background(if (selected) (accent?.container ?: cs.secondaryContainer) else Color.Transparent)
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // System has no shape of its own here: shown as the phone's mask would be, a plain tile.
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .background(accent?.strong ?: cs.primary, shape.shape ?: systemIconShape()),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(shape.label),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) (accent?.content ?: cs.onSecondaryContainer) else cs.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The phone's own icon mask (a 100 by 100 path from Android), scaled to fit. */
@Composable
private fun systemIconShape(): androidx.compose.ui.graphics.Shape = remember {
    val mask = android.graphics.drawable.AdaptiveIconDrawable(null, null).iconMask
    androidx.compose.foundation.shape.GenericShape { size, _ ->
        val path = android.graphics.Path(mask)
        path.transform(android.graphics.Matrix().apply { setScale(size.width / 100f, size.height / 100f) })
        addPath(path.asComposePath())
    }
}

/** The font for all the launcher's text: a sheet to choose the system font or a custom TTF or OTF file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontTile(settings: LauncherSettings, store: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var choosing by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = CustomFont.import(context, uri)
            if (name == null) Toast.makeText(context, R.string.settings_font_invalid, Toast.LENGTH_SHORT).show() else store.setFont(name)
        }
    }
    val custom = settings.fontVersion != 0L
    val customName = settings.fontName.ifBlank { stringResource(R.string.settings_font_custom) }
    GroupTile(
        badge = { ScallopBadge(Icons.Font) },
        title = stringResource(R.string.settings_font),
        subtitle = if (custom) customName else stringResource(R.string.settings_font_system),
        onClick = { choosing = true },
    )
    if (!choosing) return
    ModalBottomSheet(
        onDismissRequest = { choosing = false },
        containerColor = if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerLow,
    ) {
        Text(
            stringResource(R.string.settings_font),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        Column(Modifier.selectableGroup().padding(bottom = 24.dp)) {
            FontChoice(stringResource(R.string.settings_font_system), stringResource(R.string.settings_font_system_sub), selected = !custom) {
                choosing = false
                if (custom) scope.launch { CustomFont.clear(context); store.setFont(null) }
            }
            // Tapped again while chosen, it picks a different file.
            FontChoice(
                stringResource(R.string.settings_font_custom),
                if (custom) stringResource(R.string.settings_font_custom_current, customName) else stringResource(R.string.settings_font_custom_sub),
                selected = custom,
            ) {
                choosing = false
                pick.launch(CustomFont.MimeTypes)
            }
        }
    }
}

@Composable
private fun FontChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = LocalAccent.current?.strong ?: cs.secondary))
    }
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

/** How the wallpaper blurs behind the drawer: a tile showing the style, opening a sheet to choose another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlurStyleTile(style: BlurStyle, onSelect: (BlurStyle) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var choosing by rememberSaveable { mutableStateOf(false) }
    GroupTile(
        badge = { ScallopBadge(Icons.Palette) },
        title = stringResource(R.string.settings_blur_style),
        subtitle = stringResource(style.label),
        onClick = { choosing = true },
    )
    if (!choosing) return
    ModalBottomSheet(
        onDismissRequest = { choosing = false },
        containerColor = if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerLow,
    ) {
        Text(
            stringResource(R.string.settings_blur_style),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            ChoiceGroup(BlurStyle.entries, style, { it.label }) {
                choosing = false
                onSelect(it)
            }
        }
    }
}

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
            onValueChange = { shown = it },
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
