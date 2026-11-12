package com.elara.launcher

import android.annotation.SuppressLint
import android.content.Context
import java.lang.reflect.Method

/** What a home screen gesture does. */
enum class GestureAction {
    None,
    Notifications,
    QuickSettings,
    ;

    companion object {
        /** A stored name back to its action; unknown names (an action since removed, as locking the screen) give [default]. */
        fun parse(name: String?, default: GestureAction): GestureAction = entries.firstOrNull { it.name == name } ?: default
    }
}

/**
 * Performs home screen gestures. The notification panel and quick settings open through
 * StatusBarManager's expand methods, as Niagara, Nova and Lawnchair do: hidden from the SDK
 * (@UnsupportedAppUsage, reached by reflection) but allowed, with the install-time
 * EXPAND_STATUS_BAR permission, so nothing has to be turned on. Locking the screen has no such
 * route: it needs an accessibility service, and Play Protect blocks the install of a sideloaded
 * app that declares one (found 2026-11-12), so Elara has neither.
 */
object Gestures {
    /** Performs [action]; false if this Android won't allow it. */
    fun perform(context: Context, action: GestureAction): Boolean = when (action) {
        GestureAction.None -> true
        GestureAction.Notifications -> expand(context, "expandNotificationsPanel")
        GestureAction.QuickSettings -> expand(context, "expandSettingsPanel")
    }

    private fun expand(context: Context, name: String): Boolean {
        val bar = context.getSystemService(STATUS_BAR_SERVICE) ?: return false
        val method = statusBarMethod(context, name) ?: return false
        return runCatching { method.invoke(bar) }.isSuccess
    }

    // shortcut: hidden StatusBarManager methods by reflection; if a future Android blocks them, the gesture does nothing.
    @SuppressLint("WrongConstant", "PrivateApi", "DiscouragedPrivateApi")
    private fun statusBarMethod(context: Context, name: String): Method? = runCatching {
        context.getSystemService(STATUS_BAR_SERVICE)?.javaClass?.getMethod(name)
    }.getOrNull()

    /** Context.STATUS_BAR_SERVICE, which is hidden from the SDK. */
    private const val STATUS_BAR_SERVICE = "statusbar"
}
