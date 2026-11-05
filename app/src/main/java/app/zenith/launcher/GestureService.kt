package app.zenith.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/** What a home screen gesture does. */
enum class GestureAction {
    None,
    Notifications,
    QuickSettings,
    LockScreen,
    ;

    /** Whether this Android version can do it. */
    val available: Boolean get() = this != LockScreen || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    companion object {
        /** A stored name back to its action; unknown or unavailable names give [default]. */
        fun parse(name: String?, default: GestureAction): GestureAction =
            entries.firstOrNull { it.name == name }?.takeIf { it.available } ?: default
    }
}

/**
 * Performs home screen gestures. The notification panel and quick settings open through
 * StatusBarManager's expand methods, as Niagara, Nova and Lawnchair do: hidden from the SDK
 * (@UnsupportedAppUsage, reached by reflection) but allowed, with the install-time
 * EXPAND_STATUS_BAR permission, so nothing has to be turned on. Locking the screen has no such
 * route, so it, and the panel if Android ever blocks those methods, go through this
 * accessibility service's global actions. The service asks for no events and cannot read window
 * content (see res/xml/gesture_service.xml), and runs only when the user turns it on.
 */
object Gestures {
    /** Performs [action]; false if it needs [GestureService] and the service is off. */
    fun perform(context: Context, action: GestureAction): Boolean = when (action) {
        GestureAction.Notifications -> expand(context, "expandNotificationsPanel") || GestureService.perform(action)
        GestureAction.QuickSettings -> expand(context, "expandSettingsPanel") || GestureService.perform(action)
        else -> GestureService.perform(action)
    }

    /** Whether [action] can only work with [GestureService] on. */
    fun needsService(context: Context, action: GestureAction): Boolean = when (action) {
        GestureAction.None -> false
        GestureAction.Notifications -> statusBarMethod(context, "expandNotificationsPanel") == null
        GestureAction.QuickSettings -> statusBarMethod(context, "expandSettingsPanel") == null
        GestureAction.LockScreen -> true
    }

    private fun expand(context: Context, name: String): Boolean {
        val bar = context.getSystemService(STATUS_BAR_SERVICE) ?: return false
        val method = statusBarMethod(context, name) ?: return false
        return runCatching { method.invoke(bar) }.isSuccess
    }

    // shortcut: hidden StatusBarManager methods by reflection; if a future Android blocks them, the accessibility fallback takes over.
    @SuppressLint("WrongConstant", "PrivateApi", "DiscouragedPrivateApi")
    private fun statusBarMethod(context: Context, name: String): Method? = runCatching {
        context.getSystemService(STATUS_BAR_SERVICE)?.javaClass?.getMethod(name)
    }.getOrNull()

    /** Context.STATUS_BAR_SERVICE, which is hidden from the SDK. */
    private const val STATUS_BAR_SERVICE = "statusbar"
}

/** The accessibility service behind gestures that have no other route (see [Gestures]). */
class GestureService : AccessibilityService() {

    override fun onServiceConnected() {
        current = WeakReference(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        current = null
        return super.onUnbind(intent)
    }

    // No event types are requested, so none arrive.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile private var current: WeakReference<GestureService>? = null

        /** Performs [action]; false if it needs the service and the service is off. */
        fun perform(action: GestureAction): Boolean {
            val global = when (action) {
                GestureAction.None -> return true
                GestureAction.Notifications -> GLOBAL_ACTION_NOTIFICATIONS
                GestureAction.QuickSettings -> GLOBAL_ACTION_QUICK_SETTINGS
                GestureAction.LockScreen -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else return true
            }
            return current?.get()?.performGlobalAction(global) ?: false
        }

        /** Whether the user has turned the service on in Android's accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == GestureService::class.java.name
            }
        }
    }
}
