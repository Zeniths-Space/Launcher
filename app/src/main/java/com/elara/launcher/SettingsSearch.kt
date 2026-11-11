package com.elara.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/** A page of Android's Settings app the search can open: "Wi-Fi" under "Connections". */
internal class SettingsPage(val label: String, val where: String, val action: String, val words: List<String>)

/**
 * Android's Settings pages, by name and by the words people search for. Android offers no way for
 * an app to search Settings, so this is a fixed list of the pages with a public way in; a page the
 * phone doesn't have (checked when it would show) is left out.
 */
internal object SettingsSearch {
    private fun p(label: String, where: String, action: String, vararg words: String) = SettingsPage(label, where, action, words.toList())

    val pages = listOf(
        p("Wi-Fi", "Connections", Settings.ACTION_WIFI_SETTINGS, "wifi", "wireless", "internet", "network"),
        p("Bluetooth", "Connections", Settings.ACTION_BLUETOOTH_SETTINGS, "pair", "headphones", "earbuds"),
        p("Airplane mode", "Connections", Settings.ACTION_AIRPLANE_MODE_SETTINGS, "flight", "aeroplane"),
        p("Mobile networks", "Connections", Settings.ACTION_DATA_ROAMING_SETTINGS, "mobile data", "roaming", "sim", "4g", "5g", "apn"),
        p("Data usage", "Connections", Settings.ACTION_DATA_USAGE_SETTINGS, "data saver", "mobile data"),
        p("Mobile hotspot", "Connections", "android.settings.TETHER_SETTINGS", "hotspot", "tethering", "share internet"),
        p("NFC", "Connections", Settings.ACTION_NFC_SETTINGS, "tap to pay", "contactless"),
        p("VPN", "Connections", Settings.ACTION_VPN_SETTINGS, "private network"),
        p("Cast", "Connections", Settings.ACTION_CAST_SETTINGS, "screen mirroring", "smart view", "mirror"),
        p("Display", "Display", Settings.ACTION_DISPLAY_SETTINGS, "brightness", "screen", "refresh rate", "font size", "screen timeout"),
        p("Dark mode", "Display", "android.settings.DARK_THEME_SETTINGS", "dark theme", "night mode"),
        p("Night light", "Display", Settings.ACTION_NIGHT_DISPLAY_SETTINGS, "eye comfort", "blue light", "night display"),
        p("Sounds and vibration", "Sound", Settings.ACTION_SOUND_SETTINGS, "volume", "ringtone", "vibration", "silent", "sound"),
        p("Do not disturb", "Notifications", "android.settings.ZEN_MODE_SETTINGS", "dnd", "quiet", "silence", "focus"),
        p("Notifications", "Notifications", "android.settings.NOTIFICATION_SETTINGS", "alerts"),
        p("Battery", "Battery", Intent.ACTION_POWER_USAGE_SUMMARY, "power", "battery usage", "charging"),
        p("Battery saver", "Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS, "power saving", "power saver"),
        p("Storage", "Device care", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "space", "memory", "free up"),
        p("Apps", "Apps", Settings.ACTION_APPLICATION_SETTINGS, "applications", "installed apps", "uninstall"),
        p("Default apps", "Apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, "default browser", "default sms", "default dialer"),
        p("Home app", "Apps", Settings.ACTION_HOME_SETTINGS, "launcher", "default launcher", "home screen"),
        p("Special app access", "Apps", Settings.ACTION_USAGE_ACCESS_SETTINGS, "usage access"),
        p("Location", "Privacy", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "gps", "location services"),
        p("Privacy", "Privacy", Settings.ACTION_PRIVACY_SETTINGS, "permissions", "permission manager"),
        p("Security", "Security", Settings.ACTION_SECURITY_SETTINGS, "screen lock", "lock screen", "password", "pin", "fingerprint", "face"),
        p("Accounts", "Accounts", Settings.ACTION_SYNC_SETTINGS, "sync", "google account", "backup"),
        p("Accessibility", "Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, "talkback", "screen reader", "magnification"),
        p("Language", "General management", Settings.ACTION_LOCALE_SETTINGS, "languages", "locale"),
        p("Keyboard", "General management", Settings.ACTION_INPUT_METHOD_SETTINGS, "input method", "on-screen keyboard"),
        p("Date and time", "General management", Settings.ACTION_DATE_SETTINGS, "time zone", "clock", "24-hour"),
        p("Developer options", "System", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "usb debugging", "wireless debugging", "adb"),
        p("About phone", "System", Settings.ACTION_DEVICE_INFO_SETTINGS, "model", "build number", "android version", "imei", "phone name"),
        p("Printing", "Connections", Settings.ACTION_PRINT_SETTINGS, "printer", "print"),
        p("Settings", "Android", Settings.ACTION_SETTINGS, "preferences", "system settings"),
    )

    /** Pages matching [query] by name first, then by a search word; up to [limit]. */
    fun find(query: String, limit: Int = 4): List<SettingsPage> {
        if (query.trim().length < 2) return emptyList()
        val byName = searchByLabel(pages, query) { it.label }
        val byWord = pages.filter { page -> page !in byName && page.words.any { searchByLabel(listOf(it), query) { w -> w }.isNotEmpty() } }
        return (byName + byWord).take(limit)
    }

    /** Whether [page] is a strong enough match for the top card: its name or a search word starts with [query]. */
    fun strong(page: SettingsPage, query: String): Boolean {
        val q = query.trim().lowercase()
        return (listOf(page.label) + page.words).any { it.lowercase().startsWith(q) }
    }

    fun intent(page: SettingsPage) = Intent(page.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private val present = HashMap<String, Boolean>()

    /** Whether this phone has the page: Samsung, for one, lacks some of Android's. Asked once per page. */
    fun exists(context: Context, page: SettingsPage): Boolean = present.getOrPut(page.action) {
        context.packageManager.resolveActivity(intent(page), PackageManager.MATCH_DEFAULT_ONLY) != null
    }

    fun open(context: Context, page: SettingsPage) {
        try {
            context.startActivity(intent(page))
        } catch (_: ActivityNotFoundException) {
            // Gone since it was checked: nothing to open.
        } catch (_: SecurityException) {
            // A page Android keeps for system apps.
        }
    }
}
