package com.elara.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone

/** A contact found by the drawer search, with the number Go and Call dial. */
internal data class ContactHit(val id: Long, val lookupKey: String, val name: String, val number: String) {
    /** Up to two letters for the avatar: "Ram Sharma" is "RS". */
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "#" }
}

internal object ContactSearch {
    private const val Limit = 6

    fun allowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Contacts whose name (or number) matches [query], from Android's contacts, a contact once
     * with its default number first. Empty without permission, or for a query under two letters.
     * Blocking: call off the main thread.
     */
    fun find(context: Context, query: String): List<ContactHit> {
        val q = query.trim()
        if (q.length < 2 || !allowed(context)) return emptyList()
        val uri = Phone.CONTENT_FILTER_URI.buildUpon().appendPath(q)
            .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, "40").build()
        val projection = arrayOf(Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.IS_SUPER_PRIMARY)
        val rows = ArrayList<Row>()
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    rows += Row(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getInt(4) != 0)
                }
            }
        } catch (_: SecurityException) {
            // Revoked while the drawer was open.
            return emptyList()
        }
        return group(rows, q)
    }

    internal data class Row(val id: Long, val lookupKey: String, val name: String, val number: String, val primary: Boolean)

    /** One hit per contact (its default number, else its first), names starting with [query] first. */
    internal fun group(rows: List<Row>, query: String): List<ContactHit> {
        val q = query.trim().lowercase()
        return rows.filter { it.name.isNotBlank() && it.number.isNotBlank() }
            .groupBy { it.id }
            .map { (_, r) -> (r.firstOrNull { it.primary } ?: r.first()).let { ContactHit(it.id, it.lookupKey, it.name, it.number) } }
            .sortedBy { rank(it.name.lowercase(), q) }
            .take(Limit)
    }

    private fun rank(name: String, q: String) = when {
        name.startsWith(q) -> 0
        name.split(' ').any { it.startsWith(q) } -> 1
        else -> 2
    }

    /**
     * The contact to put on the top card: the first one whose name starts with a word of [query],
     * when no app name starts with it (an app the user is typing still comes first).
     */
    fun top(contacts: List<ContactHit>, apps: List<AppEntry>, query: String): ContactHit? {
        val q = query.trim().lowercase()
        if (apps.any { it.label.lowercase().startsWith(q) }) return null
        return contacts.firstOrNull { rank(it.name.lowercase(), q) < 2 }
    }

    // ACTION_DIAL opens the dialer with the number ready: no call permission, one tap to call.
    fun dial(context: Context, c: ContactHit) = start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", c.number, null)))

    fun message(context: Context, c: ContactHit) = start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", c.number, null)))

    fun open(context: Context, c: ContactHit) =
        start(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(c.id, c.lookupKey)))

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No dialer, messages or contacts app: nothing to open.
        }
    }
}
