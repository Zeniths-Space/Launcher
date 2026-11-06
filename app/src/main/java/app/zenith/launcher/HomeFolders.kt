package app.zenith.launcher

/** A home screen folder: its [apps] by key, in order, under [name] ("" for the default name). */
data class HomeFolder(val id: Int, val name: String, val apps: List<String>)

/**
 * Home screen folders. A folder sits in a home slot as [key] ("folder:<id>"); its apps are listed
 * here, not in the slots. Stored one per line as id, apps and name, split by tabs (apps by
 * [AppSeparator]); names lose tabs and line breaks.
 */
object HomeFolders {
    private const val Prefix = "folder:"
    private const val AppSeparator = "\u001F"

    fun key(id: Int) = "$Prefix$id"

    fun idOf(key: String?): Int? = key?.takeIf { it.startsWith(Prefix) }?.removePrefix(Prefix)?.toIntOrNull()

    fun isFolder(key: String?) = idOf(key) != null

    fun parse(stored: String?): List<HomeFolder> = stored.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t', limit = 3)
        val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val apps = parts.getOrNull(1).orEmpty().split(AppSeparator).filter { it.isNotEmpty() }
        HomeFolder(id, parts.getOrNull(2).orEmpty(), apps)
    }

    fun format(folders: List<HomeFolder>): String = folders.joinToString("\n") { f ->
        "${f.id}\t${f.apps.joinToString(AppSeparator)}\t${clean(f.name)}"
    }

    /** A name as it can be stored: one line, no tabs, trimmed. */
    fun clean(name: String) = name.replace(Regex("[\\t\\r\\n]"), " ").trim()

    /** Every app key in a folder. */
    fun apps(folders: List<HomeFolder>): Set<String> = folders.flatMapTo(HashSet()) { it.apps }

    /**
     * The app in [fromPage]/[from] dropped onto the item in [toPage]/[to]: into it if it's a
     * folder, else both into a new folder in that slot. Null if the dragged item isn't an app, or
     * there's nothing to drop onto.
     */
    fun merge(
        pages: List<List<String>>,
        folders: List<HomeFolder>,
        fromPage: Int,
        from: Int,
        toPage: Int,
        to: Int,
    ): Pair<List<List<String>>, List<HomeFolder>>? {
        val dragged = pages.getOrNull(fromPage)?.getOrNull(from)?.takeIf { it.isNotEmpty() && !isFolder(it) } ?: return null
        val target = pages.getOrNull(toPage)?.getOrNull(to)?.takeIf { it.isNotEmpty() && it != dragged } ?: return null
        val emptied = pages.mapIndexed { p, page -> if (p == fromPage) page.mapIndexed { i, k -> if (i == from) "" else k } else page }
        val intoId = idOf(target)
        if (intoId != null) {
            val into = folders.firstOrNull { it.id == intoId } ?: return null
            return trim(emptied) to folders.map { if (it.id == intoId) it.copy(apps = into.apps + dragged) else it }
        }
        val id = (folders.maxOfOrNull { it.id } ?: 0) + 1
        val placed = emptied.mapIndexed { p, page -> if (p == toPage) page.mapIndexed { i, k -> if (i == to) key(id) else k } else page }
        return trim(placed) to folders + HomeFolder(id, "", listOf(target, dragged))
    }

    /**
     * [app] taken off the home screen, from a slot or a folder. A folder left with one app becomes
     * that app, in the folder's slot; an empty one goes.
     */
    fun removeApp(pages: List<List<String>>, folders: List<HomeFolder>, app: String): Pair<List<List<String>>, List<HomeFolder>> {
        val fromSlots = HomePages.remove(pages, app)
        val changed = folders.map { if (app in it.apps) it.copy(apps = it.apps - app) else it }
        return dissolve(fromSlots, changed)
    }

    /** Folder [id] and its apps off the home screen (the apps stay installed). */
    fun removeFolder(pages: List<List<String>>, folders: List<HomeFolder>, id: Int): Pair<List<List<String>>, List<HomeFolder>> =
        HomePages.remove(pages, key(id)) to folders.filter { it.id != id }

    // Folders of one app become the app; folders of none, and ones in no slot, go.
    private fun dissolve(pages: List<List<String>>, folders: List<HomeFolder>): Pair<List<List<String>>, List<HomeFolder>> {
        val inSlots = pages.flatten().mapNotNullTo(HashSet(), ::idOf)
        val keep = folders.filter { it.id in inSlots && it.apps.size >= 2 }
        val gone = folders.filter { it !in keep }.associateBy { it.id }
        val out = pages.map { page -> page.map { k -> idOf(k)?.let { gone[it] }?.let { it.apps.singleOrNull().orEmpty() } ?: k } }
        return trim(out) to keep
    }

    private fun trim(pages: List<List<String>>) = pages.map { it.dropLastWhile(String::isEmpty) }
}
