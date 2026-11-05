package app.zenith.launcher

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

/** User settings. Defaults: clock, swipe hint and app names shown; pure black off; swipe down opens notifications. */
data class LauncherSettings(
    val showClock: Boolean = true,
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
) {
    val dockCapacity: Int get() = dockColumns * dockRows
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
                showClock = p[SHOW_CLOCK] ?: true,
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
            )
        }

    suspend fun setShowClock(on: Boolean) = set(SHOW_CLOCK, on)
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
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
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
    }
}

/** Dock rows and columns each range over these. */
val DockGridRange = 1..20

/** Dock icon sizes, in dp. */
val DockIconRange = 32..96

/** Space between dock rows, in dp. */
val DockRowGapRange = 0..40

// App keys contain "/" and "#", never a line break.
private const val DockSeparator = "\n"
