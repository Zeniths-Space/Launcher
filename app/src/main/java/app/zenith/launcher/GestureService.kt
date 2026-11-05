package app.zenith.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import java.lang.ref.WeakReference

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
 * Opens the notification panel and quick settings, and locks the screen, for the home screen's
 * gestures. Android offers these to a third-party launcher only through an accessibility service's
 * global actions (expanding the panel directly is a hidden API). The service asks for no events and
 * cannot read window content (see res/xml/gesture_service.xml): it only performs those actions, and
 * only when a gesture asks. It runs in the launcher's process; the user turns it on in Android's
 * accessibility settings.
 */
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
