package com.elara.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack, as listed in settings. */
internal class IconPackInfo(val packageName: String, val label: String)

/**
 * A loaded icon pack: which of its drawables stands for which app, from its appfilter.xml (the
 * format ADW, Nova and most other launchers read). Apps the pack has no icon for keep their own.
 */
internal class IconPack(
    val packageName: String,
    private val res: Resources,
    private val drawables: Map<String, String>,
    /** The drawable names of the pack's `iconback` images, put behind icons the pack doesn't cover. */
    private val backs: List<String> = emptyList(),
    /** How big the app's own icon is drawn on a back, 0 to 1 (the appfilter's `scale`). */
    val scale: Float = 0.7f,
) {
    val size get() = drawables.size

    /** Every icon the pack maps to some app, by drawable name, for picking one by hand. */
    // shortcut: icons listed only in the pack's drawable.xml (mapped to no app) are left out; read that file if users miss them.
    val names: List<String> by lazy { drawables.values.distinct().sorted() }

    /** The pack's icon for [component], or null when it has none. */
    fun icon(component: ComponentName, density: Int): Drawable? =
        drawables["${component.packageName}/${component.className}"]?.let { drawable(it, density) }

    /**
     * The back for [component]'s icon (the same one each time): one of the pack's `iconback` images,
     * or, when it has none that can be read, a plain tile in the colour most of its icons start with.
     * Null if neither is known.
     */
    fun back(component: ComponentName, density: Int): Drawable? =
        backs.takeIf { it.isNotEmpty() }?.let { drawable(it[Math.floorMod(component.packageName.hashCode(), it.size)], density) }
            ?: tileColor?.let { ColorDrawable(it) }

    /** The middle colour (by brightness) of the left edge of up to 60 of the pack's icons; null if too few are solid there. */
    private val tileColor: Int? by lazy {
        val density = res.displayMetrics.densityDpi
        val edge = names.take(60).mapNotNull { name ->
            runCatching {
                val bitmap = drawable(name, density)?.toBitmap(32, 32) ?: return@runCatching null
                bitmap.getPixel(5, 16).also { bitmap.recycle() }.takeIf { it ushr 24 == 255 }
            }.getOrNull()
        }
        if (edge.size < 8) null else edge.sortedBy { Color.luminance(it) }[edge.size / 2]
    }


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
        val backs = ArrayList<String>()
        var scale = 0.7f
        runCatching {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name == "iconback") {
                    for (i in 0 until parser.attributeCount) if (parser.getAttributeName(i).startsWith("img")) backs += parser.getAttributeValue(i)
                    continue
                }
                if (parser.name == "scale") { parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.let { scale = it.coerceIn(0.3f, 1f) }; continue }
                if (parser.name != "item") continue
                val component = componentKey(parser.getAttributeValue(null, "component")) ?: continue
                val drawable = parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() } ?: continue
                map.putIfAbsent(component, drawable)
            }
        }
        return IconPack(packageName, res, map, backs, scale).takeIf { map.isNotEmpty() }
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
