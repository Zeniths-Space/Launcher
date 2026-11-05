package app.zenith.launcher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
)

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
            )
        }

    suspend fun setShowClock(on: Boolean) = set(SHOW_CLOCK, on)
    suspend fun setShowHint(on: Boolean) = set(SHOW_HINT, on)
    suspend fun setShowLabels(on: Boolean) = set(SHOW_LABELS, on)
    suspend fun setPureBlack(on: Boolean) = set(PURE_BLACK, on)
    suspend fun setSwipeDown(action: GestureAction) = store.edit { it[SWIPE_DOWN] = action.name }
    suspend fun setDoubleTap(action: GestureAction) = store.edit { it[DOUBLE_TAP] = action.name }

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
    }
}
