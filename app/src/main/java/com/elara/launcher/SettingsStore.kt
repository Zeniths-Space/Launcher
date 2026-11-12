package com.elara.launcher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** User settings. Defaults: app names shown; pure black off; swipe down opens notifications. */
data class LauncherSettings(
    val showLabels: Boolean = true,
    /** Near-black drawer and settings in dark mode (AMOLED). */
    val pureBlack: Boolean = false,
    /** Swiping down on the home screen. */
    val swipeDown: GestureAction = GestureAction.Notifications,
    /** Double-tapping the home screen. */
    val doubleTap: GestureAction = GestureAction.None,
    val showDock: Boolean = true,
    /** A bar behind the dock icons; off shows the icons alone over the wallpaper. */
    val dockBar: Boolean = false,
    /** Dock apps by [AppEntry.key] and slot in reading order, "" for an empty slot. Only the first [dockCapacity] slots show. */
    val dock: List<String> = emptyList(),
    val dockColumns: Int = 5,
    val dockRows: Int = 1,
    /** Dock icon size in dp; icons shrink below it when the columns are too narrow. */
    val dockIconSize: Int = 52,
    /** Space between dock rows in dp, also above the first and below the last (half each). */
    val dockRowGap: Int = 20,
    /** Space between dock icons side to side in dp; at most the even spread across the dock. */
    val dockColumnGap: Int = 28,
    /** Home screen pages, each app keys by slot in reading order, "" for an empty slot. */
    val home: List<List<String>> = emptyList(),
    /** Folders on the home screen, each in a slot of [home] by [HomeFolders.key]. */
    val folders: List<HomeFolder> = emptyList(),
    /** Names the user gave apps, by [AppEntry.key]; an app not here has its own name. */
    val labels: Map<String, String> = emptyMap(),
    /** Icons the user picked for single apps, by [AppEntry.key]: "icon pack package/drawable name". */
    val appIcons: Map<String, String> = emptyMap(),
    /** Apps kept out of the drawer and its search, by [AppEntry.key]. Home screen and dock copies stay. */
    val hidden: Set<String> = emptySet(),
    /** Widgets on the home screen pages. */
    val widgets: List<HomeWidget> = emptyList(),
    val homeColumns: Int = 4,
    val homeRows: Int = 5,
    val homeIconSize: Int = 52,
    val homeLabels: Boolean = true,
    /** Dots under the home screen pages showing which one is shown. */
    val homeDots: Boolean = true,
    val drawerColumns: Int = 4,
    val drawerIconSize: Int = 52,
    /** Space between app drawer rows in dp. */
    val drawerRowGap: Int = 12,
    /** Space between the app drawer grid and the screen's left and right edges in dp. */
    val drawerPadding: Int = 8,
    /** The app drawer opens where it was left instead of at the top. */
    val drawerKeepPosition: Boolean = false,
    /** Drawer search also finds contacts (once Android allows it). */
    val drawerContacts: Boolean = true,
    /** Search is a bar at the bottom of the drawer, in reach of the thumb, instead of a button at the top. */
    val drawerSearchBelow: Boolean = false,
    /** The drawer's apps are in pages swiped sideways instead of one list scrolled up and down. */
    val drawerHorizontal: Boolean = false,
    /** The drawer lists the most opened apps first instead of by name. */
    val drawerSortFrequent: Boolean = false,
    /** How often each app was opened from Elara, by [AppEntry.key]; counted only while [drawerSortFrequent] is on. */
    val launches: Map<String, Int> = emptyMap(),
    /** Exchange rates the user set for search, as [CurrencyRates.format] writes them. */
    val currencyRates: String = "",
    /** The wallpaper blurs behind the open drawer, as glass, where Android supports it. */
    val drawerBlur: Boolean = true,
    /** How the wallpaper blurs behind the drawer. */
    val drawerBlurStyle: BlurStyle = BlurStyle.Glass,
    /** Home screen and dock apps can't be moved, added or removed. */
    val locked: Boolean = false,
    /** The first-run setup was finished. True until the store is read, so it never flashes over a set-up home screen. */
    val setupDone: Boolean = true,
    /** When the current custom font was picked (see [CustomFont]); 0 is the system font. */
    val fontVersion: Long = 0,
    /** The picked font's file name, for settings to show. */
    val fontName: String = "",
    /** The outline every app icon is drawn in. */
    val iconShape: IconShape = IconShape.System,
    /** The icon pack's package, or "" for the apps' own icons. */
    val iconPack: String = "",
    /** Home setups to switch between; empty until the user makes one. The first is the default. */
    val spaces: List<Space> = emptyList(),
    /** The id of the space whose layout is the live home, dock and hidden apps. */
    val activeSpace: Long = 0,
    val spacesBySchedule: Boolean = true,
    val spacesShowName: Boolean = true,
    /** A space picked by hand stays until this time (epoch ms), when the schedule takes over again. */
    val spaceManualUntil: Long = 0,
) {
    val dockCapacity: Int get() = dockColumns * dockRows
    val homeCapacity: Int get() = homeColumns * homeRows
}

// One store per process, shared by the home screen and the settings screen.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<LauncherSettings> = store.data
        // A damaged file reads as defaults instead of crashing the home screen.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            LauncherSettings(
                showLabels = p[SHOW_LABELS] ?: true,
                pureBlack = p[PURE_BLACK] ?: false,
                swipeDown = GestureAction.parse(p[SWIPE_DOWN], GestureAction.Notifications),
                doubleTap = GestureAction.parse(p[DOUBLE_TAP], GestureAction.None),
                showDock = p[SHOW_DOCK] ?: true,
                dockBar = p[DOCK_BAR] ?: false,
                dock = p[DOCK]?.split(DockSeparator)?.dropLastWhile { it.isEmpty() }.orEmpty(),
                dockColumns = (p[DOCK_COLUMNS] ?: 5).coerceIn(DockGridRange),
                dockRows = (p[DOCK_ROWS] ?: 1).coerceIn(DockGridRange),
                dockIconSize = (p[DOCK_ICON_SIZE] ?: 52).coerceIn(DockIconRange),
                dockRowGap = (p[DOCK_ROW_GAP] ?: 20).coerceIn(DockRowGapRange),
                dockColumnGap = (p[DOCK_COLUMN_GAP] ?: 28).coerceIn(DockColumnGapRange),
                home = HomePages.parse(p[HOME]),
                folders = HomeFolders.parse(p[HOME_FOLDERS]),
                widgets = HomeWidgets.parse(p[HOME_WIDGETS]),
                labels = AppLabels.parse(p[APP_LABELS]),
                appIcons = AppLabels.parse(p[APP_ICONS]),
                hidden = p[HIDDEN_APPS]?.split(DockSeparator)?.filterTo(HashSet()) { it.isNotEmpty() }.orEmpty(),
                homeColumns = (p[HOME_COLUMNS] ?: 4).coerceIn(DockGridRange),
                homeRows = (p[HOME_ROWS] ?: 5).coerceIn(DockGridRange),
                homeIconSize = (p[HOME_ICON_SIZE] ?: 52).coerceIn(DockIconRange),
                homeLabels = p[HOME_LABELS] ?: true,
                homeDots = p[HOME_DOTS] ?: true,
                drawerColumns = (p[DRAWER_COLUMNS] ?: 4).coerceIn(DrawerColumnRange),
                drawerIconSize = (p[DRAWER_ICON_SIZE] ?: 52).coerceIn(DockIconRange),
                drawerRowGap = (p[DRAWER_ROW_GAP] ?: 12).coerceIn(DrawerSpacingRange),
                drawerPadding = (p[DRAWER_PADDING] ?: 8).coerceIn(DrawerSpacingRange),
                drawerKeepPosition = p[DRAWER_KEEP_POSITION] ?: false,
                drawerContacts = p[DRAWER_CONTACTS] ?: true,
                drawerSearchBelow = p[DRAWER_SEARCH_BELOW] ?: false,
                drawerSortFrequent = p[DRAWER_SORT_FREQUENT] ?: false,
                drawerHorizontal = p[DRAWER_HORIZONTAL] ?: false,
                launches = parseLaunches(p[APP_LAUNCHES]),
                currencyRates = p[CURRENCY_RATES].orEmpty(),
                drawerBlur = p[DRAWER_BLUR] ?: true,
                drawerBlurStyle = BlurStyle.parse(p[DRAWER_BLUR_STYLE]),
                locked = p[LOCKED] ?: false,
                setupDone = p[SETUP_DONE] ?: false,
                fontVersion = p[FONT_VERSION] ?: 0L,
                fontName = p[FONT_NAME].orEmpty(),
                iconShape = IconShape.parse(p[ICON_SHAPE]),
                iconPack = p[ICON_PACK].orEmpty(),
                spaces = Spaces.parse(p[SPACES]),
                activeSpace = p[ACTIVE_SPACE] ?: 0,
                spacesBySchedule = p[SPACES_SCHEDULE] ?: true,
                spacesShowName = p[SPACES_SHOW_NAME] ?: true,
                spaceManualUntil = p[SPACE_MANUAL_UNTIL] ?: 0,
            )
        }

    suspend fun setShowLabels(on: Boolean) = set(SHOW_LABELS, on)
    suspend fun setPureBlack(on: Boolean) = set(PURE_BLACK, on)
    suspend fun setSwipeDown(action: GestureAction) = store.edit { it[SWIPE_DOWN] = action.name }
    suspend fun setDoubleTap(action: GestureAction) = store.edit { it[DOUBLE_TAP] = action.name }
    suspend fun setShowDock(on: Boolean) = set(SHOW_DOCK, on)
    suspend fun setDockBar(on: Boolean) = set(DOCK_BAR, on)
    // Slots past the dock's size are kept, so making the grid smaller and back loses nothing.
    suspend fun setDock(keys: List<String>) = store.edit { it[DOCK] = keys.joinToString(DockSeparator) }
    suspend fun setDockColumns(n: Int) = store.edit { it[DOCK_COLUMNS] = n.coerceIn(DockGridRange) }
    suspend fun setDockRows(n: Int) = store.edit { it[DOCK_ROWS] = n.coerceIn(DockGridRange) }
    suspend fun setDockIconSize(dp: Int) = store.edit { it[DOCK_ICON_SIZE] = dp.coerceIn(DockIconRange) }
    suspend fun setDockRowGap(dp: Int) = store.edit { it[DOCK_ROW_GAP] = dp.coerceIn(DockRowGapRange) }
    suspend fun setDockColumnGap(dp: Int) = store.edit { it[DOCK_COLUMN_GAP] = dp.coerceIn(DockColumnGapRange) }
    suspend fun setHome(pages: List<List<String>>) = store.edit { it[HOME] = HomePages.format(pages) }
    /** Pages and folders together, so a folder and its slot never disagree. */
    suspend fun setHomeLayout(pages: List<List<String>>, folders: List<HomeFolder>) = store.edit {
        it[HOME] = HomePages.format(pages)
        it[HOME_FOLDERS] = HomeFolders.format(folders)
    }
    suspend fun setFolders(folders: List<HomeFolder>) = store.edit { it[HOME_FOLDERS] = HomeFolders.format(folders) }
    /** Names [key] [label]; a blank one gives the app back its own name. */
    suspend fun setAppLabel(key: String, label: String) = store.edit {
        val labels = AppLabels.parse(it[APP_LABELS]).toMutableMap()
        val clean = AppLabels.clean(label)
        if (clean.isEmpty()) labels.remove(key) else labels[key] = clean
        it[APP_LABELS] = AppLabels.format(labels)
    }
    /** Gives [key] the [icon] ("pack package/drawable name"); null gives the app back its usual icon. */
    suspend fun setAppIcon(key: String, icon: String?) = store.edit {
        val icons = AppLabels.parse(it[APP_ICONS]).toMutableMap()
        if (icon == null) icons.remove(key) else icons[key] = icon
        it[APP_ICONS] = AppLabels.format(icons)
    }
    suspend fun setHidden(key: String, hidden: Boolean) = store.edit {
        val keys = it[HIDDEN_APPS]?.split(DockSeparator)?.filterTo(LinkedHashSet()) { k -> k.isNotEmpty() } ?: LinkedHashSet()
        if (hidden) keys += key else keys -= key
        it[HIDDEN_APPS] = keys.joinToString(DockSeparator)
    }
    suspend fun setWidgets(widgets: List<HomeWidget>) = store.edit { it[HOME_WIDGETS] = HomeWidgets.format(widgets) }
    suspend fun setHomeColumns(n: Int) = store.edit { it[HOME_COLUMNS] = n.coerceIn(DockGridRange) }
    suspend fun setHomeRows(n: Int) = store.edit { it[HOME_ROWS] = n.coerceIn(DockGridRange) }
    suspend fun setHomeIconSize(dp: Int) = store.edit { it[HOME_ICON_SIZE] = dp.coerceIn(DockIconRange) }
    suspend fun setHomeLabels(on: Boolean) = set(HOME_LABELS, on)
    suspend fun setHomeDots(on: Boolean) = set(HOME_DOTS, on)
    suspend fun setDrawerColumns(n: Int) = store.edit { it[DRAWER_COLUMNS] = n.coerceIn(DrawerColumnRange) }
    suspend fun setDrawerIconSize(dp: Int) = store.edit { it[DRAWER_ICON_SIZE] = dp.coerceIn(DockIconRange) }
    suspend fun setDrawerRowGap(dp: Int) = store.edit { it[DRAWER_ROW_GAP] = dp.coerceIn(DrawerSpacingRange) }
    suspend fun setDrawerPadding(dp: Int) = store.edit { it[DRAWER_PADDING] = dp.coerceIn(DrawerSpacingRange) }
    suspend fun setDrawerKeepPosition(on: Boolean) = set(DRAWER_KEEP_POSITION, on)
    suspend fun setDrawerContacts(on: Boolean) = set(DRAWER_CONTACTS, on)
    suspend fun setDrawerSearchBelow(on: Boolean) = set(DRAWER_SEARCH_BELOW, on)
    suspend fun setDrawerSortFrequent(on: Boolean) = set(DRAWER_SORT_FREQUENT, on)
    suspend fun setDrawerHorizontal(on: Boolean) = set(DRAWER_HORIZONTAL, on)
    /** One more opening of [key], kept only while the drawer sorts by it. */
    // shortcut: plain totals, never aged; add decay if old favourites stick at the top.
    suspend fun countLaunch(key: String) = store.edit {
        if (it[DRAWER_SORT_FREQUENT] != true) return@edit
        val counts = parseLaunches(it[APP_LAUNCHES]).toMutableMap()
        counts[key] = (counts[key] ?: 0) + 1
        it[APP_LAUNCHES] = AppLabels.format(counts.mapValues { c -> c.value.toString() })
    }
    suspend fun setCurrencyRates(stored: String) = store.edit { it[CURRENCY_RATES] = stored }
    suspend fun setDrawerBlur(on: Boolean) = set(DRAWER_BLUR, on)
    suspend fun setDrawerBlurStyle(style: BlurStyle) = store.edit { it[DRAWER_BLUR_STYLE] = style.name }
    suspend fun setLocked(on: Boolean) = set(LOCKED, on)
    suspend fun setSetupDone() = set(SETUP_DONE, true)
    suspend fun setIconShape(shape: IconShape) = store.edit { it[ICON_SHAPE] = shape.name }
    suspend fun setIconPack(packageName: String) = store.edit { it[ICON_PACK] = packageName }
    suspend fun setSpacesBySchedule(on: Boolean) = store.edit { it[SPACES_SCHEDULE] = on; it.remove(SPACE_MANUAL_UNTIL) }
    suspend fun setSpacesShowName(on: Boolean) = set(SPACES_SHOW_NAME, on)

    private fun MutablePreferences.liveLayout() = SpaceLayout(
        home = this[HOME].orEmpty(),
        folders = this[HOME_FOLDERS].orEmpty(),
        widgets = this[HOME_WIDGETS].orEmpty(),
        dock = this[DOCK].orEmpty(),
        hidden = this[HIDDEN_APPS].orEmpty(),
    )

    private fun MutablePreferences.putLive(layout: SpaceLayout) {
        this[HOME] = layout.home
        this[HOME_FOLDERS] = layout.folders
        this[HOME_WIDGETS] = layout.widgets
        this[DOCK] = layout.dock
        this[HIDDEN_APPS] = layout.hidden
    }

    /**
     * Adds a space. The first one also makes the current home the default space, [defaultName].
     * [copy] starts it as a copy of the active space's pages; otherwise with empty pages. Either way
     * it keeps the dock and hidden apps, and no widgets: a widget belongs to one page only.
     */
    suspend fun addSpace(space: Space, copy: Boolean, defaultName: String) = store.edit { p ->
        var spaces = Spaces.parse(p[SPACES])
        if (spaces.isEmpty()) {
            spaces = listOf(Space(1, defaultName, SpaceIcon.Home))
            p[ACTIVE_SPACE] = 1
        }
        val live = p.liveLayout()
        val layout = if (copy) live.copy(widgets = "") else SpaceLayout(dock = live.dock, hidden = live.hidden)
        val id = (spaces.maxOf { it.id }) + 1
        val updated = spaces + space.copy(id = id, layout = layout)
        p[SPACES] = Spaces.format(updated)
        p.rehold(spaces, updated)
    }

    /** Saves the default space alone (renamed before any other exists), making the current home it. */
    suspend fun addFirstSpace(space: Space) = store.edit { p ->
        if (Spaces.parse(p[SPACES]).isNotEmpty()) return@edit
        p[SPACES] = Spaces.format(listOf(space.copy(id = 1, days = 0, layout = SpaceLayout())))
        p[ACTIVE_SPACE] = 1
    }

    /** Changes a space's name, icon or schedule; its layout stays. */
    suspend fun updateSpace(space: Space) = store.edit { p ->
        val spaces = Spaces.parse(p[SPACES])
        val updated = spaces.map { if (it.id == space.id) space.copy(layout = it.layout) else it }
        p[SPACES] = Spaces.format(updated)
        p.rehold(spaces, updated)
    }

    /**
     * Removes a space (never the default). Removing the active one switches to the default first. With
     * only the default left, spaces end and its layout simply stays the home. Its widgets are freed on
     * the next start ([AppWidgets.forgetAllBut]).
     */
    suspend fun deleteSpace(id: Long) = store.edit { p ->
        val spaces = Spaces.parse(p[SPACES])
        if (spaces.firstOrNull()?.id == id || spaces.none { it.id == id }) return@edit
        if (p[ACTIVE_SPACE] == id) switchIn(p, spaces, spaces.first().id)
        val left = Spaces.parse(p[SPACES]).filter { it.id != id }
        if (left.size <= 1) {
            p.remove(SPACES)
            p.remove(ACTIVE_SPACE)
            p.remove(SPACE_MANUAL_UNTIL)
        } else {
            p[SPACES] = Spaces.format(left)
            p.rehold(spaces, left)
        }
    }

    // A schedule edit can bring the next change sooner, or make it now: shorten the hold to match.
    private fun MutablePreferences.rehold(old: List<Space>, new: List<Space>) {
        val until = this[SPACE_MANUAL_UNTIL] ?: return
        val next = Spaces.rehold(until, old, new)
        if (next <= System.currentTimeMillis()) remove(SPACE_MANUAL_UNTIL) else this[SPACE_MANUAL_UNTIL] = next
    }

    /**
     * Makes [id] the live space in one write: the active space's home, dock and hidden apps are kept
     * in it, and [id]'s come out. [manualUntil] (epoch ms) holds a hand-picked space until then; 0 for
     * a switch the schedule made.
     */
    suspend fun switchSpace(id: Long, manualUntil: Long) = store.edit { p ->
        val spaces = Spaces.parse(p[SPACES])
        if (spaces.none { it.id == id }) return@edit
        switchIn(p, spaces, id)
        if (manualUntil > 0) p[SPACE_MANUAL_UNTIL] = manualUntil else p.remove(SPACE_MANUAL_UNTIL)
    }

    private fun switchIn(p: MutablePreferences, spaces: List<Space>, id: Long) {
        val active = p[ACTIVE_SPACE] ?: spaces.first().id
        if (active == id) return
        val live = p.liveLayout()
        val target = spaces.first { it.id == id }
        p.putLive(target.layout)
        p[SPACES] = Spaces.format(spaces.map { if (it.id == active) it.copy(layout = live) else if (it.id == id) it.copy(layout = SpaceLayout()) else it })
        p[ACTIVE_SPACE] = id
    }

    /**
     * Switches to the space the schedule wants at [now], unless the schedule is off, a hand-picked
     * space is still being held, or it already is the live one. Returns whether it switched.
     */
    suspend fun applySchedule(now: java.time.LocalDateTime, nowMs: Long): Boolean {
        val s = settings.first()
        if (!s.spacesBySchedule || s.spaces.size < 2 || s.spaces.none { it.scheduled } || nowMs < s.spaceManualUntil) return false
        val want = Spaces.scheduled(s.spaces, now) ?: return false
        if (want.id == s.activeSpace) return false
        switchSpace(want.id, manualUntil = 0)
        return true
    }
    /** A new custom font was copied in as [name]; null goes back to the system font. */
    suspend fun setFont(name: String?) = store.edit {
        if (name == null) {
            it.remove(FONT_VERSION)
            it.remove(FONT_NAME)
        } else {
            it[FONT_VERSION] = System.currentTimeMillis()
            it[FONT_NAME] = name
        }
    }

    /** Fills the dock once, the first time; later calls, or a dock the user has changed, are left alone. */
    suspend fun seedDock(keys: List<String>) = store.edit {
        if (it[DOCK_SEEDED] == true) return@edit
        it[DOCK] = keys.joinToString(DockSeparator)
        it[DOCK_SEEDED] = true
    }

    private suspend fun set(key: Preferences.Key<Boolean>, on: Boolean) {
        store.edit { it[key] = on }
    }

    /** Everything a layout backup holds ([Backup]): the home screen, dock, folders, names, hidden apps and settings. */
    suspend fun backup(): String {
        val names = BackedUp.mapTo(HashSet()) { it.name }
        val prefs = store.data.first().asMap()
        val values = prefs.entries.filter { it.key.name in names }.associate { it.key.name to it.value }.toMutableMap()
        // Widget ids belong to this install, so waiting spaces go without theirs, as the live home does.
        (values[SPACES.name] as? String)?.let { values[SPACES.name] = Spaces.format(Spaces.parse(it).map { s -> s.copy(layout = s.layout.copy(widgets = "")) }) }
        return Backup.encode(values)
    }

    /**
     * Replaces the layout and settings with a [backup]'s; widgets, the custom font and first-run
     * state stay as they are. False when the text isn't a backup (nothing changes then).
     */
    suspend fun restore(backup: String): Boolean {
        val values = Backup.decode(backup) ?: return false
        store.edit { prefs ->
            BackedUp.forEach { prefs.remove(it) }
            // Only a value of the key's own type; anything else in the file is ignored.
            BackedBoolean.forEach { k -> (values[k.name] as? Boolean)?.let { prefs[k] = it } }
            BackedInt.forEach { k -> (values[k.name] as? Int)?.let { prefs[k] = it } }
            BackedLong.forEach { k -> (values[k.name] as? Long)?.let { prefs[k] = it } }
            BackedString.forEach { k -> (values[k.name] as? String)?.let { prefs[k] = it } }
        }
        return true
    }

    private companion object {
        val SHOW_LABELS = booleanPreferencesKey("show_labels")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val SWIPE_DOWN = stringPreferencesKey("swipe_down")
        val DOUBLE_TAP = stringPreferencesKey("double_tap")
        val SHOW_DOCK = booleanPreferencesKey("show_dock")
        val DOCK_BAR = booleanPreferencesKey("dock_bar")
        val DOCK = stringPreferencesKey("dock")
        val DOCK_SEEDED = booleanPreferencesKey("dock_seeded")
        val DOCK_COLUMNS = intPreferencesKey("dock_columns")
        val DOCK_ROWS = intPreferencesKey("dock_rows")
        val DOCK_ICON_SIZE = intPreferencesKey("dock_icon_size")
        val DOCK_ROW_GAP = intPreferencesKey("dock_row_gap")
        val DOCK_COLUMN_GAP = intPreferencesKey("dock_column_gap")
        val HOME = stringPreferencesKey("home")
        val HOME_WIDGETS = stringPreferencesKey("home_widgets")
        val HOME_FOLDERS = stringPreferencesKey("home_folders")
        val APP_LABELS = stringPreferencesKey("app_labels")
        val APP_ICONS = stringPreferencesKey("app_icons")
        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val HOME_COLUMNS = intPreferencesKey("home_columns")
        val HOME_ROWS = intPreferencesKey("home_rows")
        val HOME_ICON_SIZE = intPreferencesKey("home_icon_size")
        val HOME_LABELS = booleanPreferencesKey("home_labels")
        val HOME_DOTS = booleanPreferencesKey("home_dots")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val DRAWER_ICON_SIZE = intPreferencesKey("drawer_icon_size")
        val DRAWER_ROW_GAP = intPreferencesKey("drawer_row_gap")
        val DRAWER_PADDING = intPreferencesKey("drawer_padding")
        val DRAWER_KEEP_POSITION = booleanPreferencesKey("drawer_keep_position")
        val DRAWER_CONTACTS = booleanPreferencesKey("drawer_contacts")
        val DRAWER_SEARCH_BELOW = booleanPreferencesKey("drawer_search_below")
        val DRAWER_SORT_FREQUENT = booleanPreferencesKey("drawer_sort_frequent")
        val DRAWER_HORIZONTAL = booleanPreferencesKey("drawer_horizontal")
        // Not in backups: it is a record of use, not a setting.
        val APP_LAUNCHES = stringPreferencesKey("app_launches")
        /** Stored like app names ([AppLabels]): key, tab, count. */
        fun parseLaunches(stored: String?): Map<String, Int> = AppLabels.parse(stored).mapNotNull { (k, v) -> v.toIntOrNull()?.let { k to it } }.toMap()
        val CURRENCY_RATES = stringPreferencesKey("currency_rates")
        val DRAWER_BLUR = booleanPreferencesKey("drawer_blur")
        val DRAWER_BLUR_STYLE = stringPreferencesKey("drawer_blur_style")
        val LOCKED = booleanPreferencesKey("locked")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val FONT_VERSION = longPreferencesKey("font_version")
        val FONT_NAME = stringPreferencesKey("font_name")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val SPACES = stringPreferencesKey("spaces")
        val ACTIVE_SPACE = longPreferencesKey("active_space")
        val SPACES_SCHEDULE = booleanPreferencesKey("spaces_schedule")
        val SPACES_SHOW_NAME = booleanPreferencesKey("spaces_show_name")
        val SPACE_MANUAL_UNTIL = longPreferencesKey("space_manual_until")

        // Keys a backup holds, by type. Not widgets (their ids belong to this install), the font (a file) or first-run state.
        val BackedBoolean: List<Preferences.Key<Boolean>> = listOf(SHOW_LABELS, PURE_BLACK, SHOW_DOCK, DOCK_BAR, DOCK_SEEDED, HOME_LABELS, HOME_DOTS, DRAWER_KEEP_POSITION, DRAWER_CONTACTS, DRAWER_SEARCH_BELOW, DRAWER_SORT_FREQUENT, DRAWER_HORIZONTAL, DRAWER_BLUR, LOCKED, SPACES_SCHEDULE, SPACES_SHOW_NAME)
        val BackedInt: List<Preferences.Key<Int>> = listOf(DOCK_COLUMNS, DOCK_ROWS, DOCK_ICON_SIZE, DOCK_ROW_GAP, DOCK_COLUMN_GAP, HOME_COLUMNS, HOME_ROWS, HOME_ICON_SIZE, DRAWER_COLUMNS, DRAWER_ICON_SIZE, DRAWER_ROW_GAP, DRAWER_PADDING)
        val BackedLong: List<Preferences.Key<Long>> = listOf(ACTIVE_SPACE)
        val BackedString: List<Preferences.Key<String>> = listOf(SWIPE_DOWN, DOUBLE_TAP, DOCK, HOME, HOME_FOLDERS, APP_LABELS, APP_ICONS, HIDDEN_APPS, CURRENCY_RATES, DRAWER_BLUR_STYLE, ICON_SHAPE, ICON_PACK, SPACES)
        val BackedUp: List<Preferences.Key<*>> = BackedBoolean + BackedInt + BackedLong + BackedString
    }
}

/** Dock rows and columns each range over these. */
val DockGridRange = 1..20

/** Dock icon sizes, in dp. */
val DockIconRange = 32..96

/** App drawer columns. */
val DrawerColumnRange = 1..10

/** App drawer row spacing and side padding, in dp. */
val DrawerSpacingRange = 0..96

/** Space between dock rows, in dp. */
val DockRowGapRange = 0..40

/** Space between dock columns, in dp. */
val DockColumnGapRange = 0..64

// App keys contain "/" and "#", never a line break.
private const val DockSeparator = "\n"
