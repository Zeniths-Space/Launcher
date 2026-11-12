package com.elara.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack, as listed in settings. */
internal class IconPackInfo(val packageName: String, val label: String)

/**
 * A loaded icon pack: which of its drawables stands for which app, from its appfilter.xml (the
 * format ADW, Nova and most other launchers read). Apps the pack has no icon for keep their own.
 */
internal class IconPack(val packageName: String, private val res: Resources, private val drawables: Map<String, String>) {
    val size get() = drawables.size

    /** Every icon the pack maps to some app, by drawable name, for picking one by hand. */
    // shortcut: icons listed only in the pack's drawable.xml (mapped to no app) are left out; read that file if users miss them.
    val names: List<String> by lazy { drawables.values.distinct().sorted() }

    /** The pack's icon for [component], or null when it has none. */
    fun icon(component: ComponentName, density: Int): Drawable? =
        drawables["${component.packageName}/${component.className}"]?.let { drawable(it, density) }

    /** The pack's drawable called [name], or null when it has none. */
    fun drawable(name: String, density: Int): Drawable? {
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: return null
        return runCatching { res.getDrawableForDensity(id, density, null) }.getOrNull()
    }
}

internal object IconPacks {
    /** The intents icon packs declare so launchers find them. */
    private val Actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
    )

    fun installed(pm: PackageManager): List<IconPackInfo> =
        Actions.flatMap { action -> pm.queryIntentActivities(Intent(action), 0) }
            .distinctBy { it.activityInfo.packageName }
            .map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }

    /** Reads [packageName]'s appfilter; null if it isn't installed or has none. Blocking: off the main thread. */
    fun load(context: Context, packageName: String): IconPack? {
        val pm = context.packageManager
        val res = runCatching { pm.getResourcesForApplication(packageName) }.getOrNull() ?: return null
        val parser: XmlPullParser = res.getIdentifier("appfilter", "xml", packageName).takeIf { it != 0 }?.let { res.getXml(it) }
            ?: runCatching {
                // Some packs keep it in assets instead of res/xml.
                val stream = context.createPackageContext(packageName, 0).assets.open("appfilter.xml")
                Xml.newPullParser().apply { setInput(stream, null) }
            }.getOrNull()
            ?: return null
        val map = HashMap<String, String>()
        runCatching {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                val component = componentKey(parser.getAttributeValue(null, "component")) ?: continue
                val drawable = parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() } ?: continue
                map.putIfAbsent(component, drawable)
            }
        }
        return IconPack(packageName, res, map).takeIf { map.isNotEmpty() }
    }

    /** [names] (drawable names) that hold every word of [query]; all of them for a blank one. */
    fun search(names: List<String>, query: String): List<String> {
        val words = query.lowercase().split(Regex("[\\s_]+")).filter { it.isNotEmpty() }
        return if (words.isEmpty()) names else names.filter { n -> words.all { it in n.lowercase() } }
    }

    /** "ComponentInfo{com.whatsapp/com.whatsapp.Main}" as "com.whatsapp/com.whatsapp.Main"; a ".Main" class is the package's. */
    fun componentKey(value: String?): String? {
        val inner = value?.trim()?.removePrefix("ComponentInfo{")?.removeSuffix("}") ?: return null
        val (pkg, cls) = inner.split('/', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() } ?: return null
        return "$pkg/${if (cls.startsWith('.')) pkg + cls else cls}"
    }
}
