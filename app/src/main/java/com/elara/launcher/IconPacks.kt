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

    /** The pack's icon for [component], or null when it has none. */
    fun icon(component: ComponentName, density: Int): Drawable? {
        val name = drawables["${component.packageName}/${component.className}"] ?: return null
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

    /** "ComponentInfo{com.whatsapp/com.whatsapp.Main}" as "com.whatsapp/com.whatsapp.Main"; a ".Main" class is the package's. */
    fun componentKey(value: String?): String? {
        val inner = value?.trim()?.removePrefix("ComponentInfo{")?.removeSuffix("}") ?: return null
        val (pkg, cls) = inner.split('/', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() } ?: return null
        return "$pkg/${if (cls.startsWith('.')) pkg + cls else cls}"
    }
}
