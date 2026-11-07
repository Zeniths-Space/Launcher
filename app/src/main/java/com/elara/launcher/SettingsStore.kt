package com.elara.launcher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** User settings. Defaults: swipe hint and app names shown; pure black off; swipe down opens notifications. */
data class LauncherSettings(
    val showHint: Boolean = true,
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
    /** Home screen and dock apps can't be moved, added or removed. */
    val locked: Boolean = false,
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
                showHint = p[SHOW_HINT] ?: true,
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
                locked = p[LOCKED] ?: false,
            )
        }

    suspend fun setShowHint(on: Boolean) = set(SHOW_HINT, on)
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
    suspend fun setLocked(on: Boolean) = set(LOCKED, on)

    /** Fills the dock once, the first time; later calls, or a dock the user has changed, are left alone. */
    suspend fun seedDock(keys: List<String>) = store.edit {
        if (it[DOCK_SEEDED] == true) return@edit
        it[DOCK] = keys.joinToString(DockSeparator)
        it[DOCK_SEEDED] = true
    }

    private suspend fun set(key: Preferences.Key<Boolean>, on: Boolean) {
        store.edit { it[key] = on }
    }

    private companion object {
        val SHOW_HINT = booleanPreferencesKey("show_hint")
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
        val LOCKED = booleanPreferencesKey("locked")
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
