package com.elara.launcher

/** How a folder's tile looks: small icons of its apps, or a folder symbol. */
enum class FolderStyle { Apps, Symbol }

/** How a large folder lays out its apps: rows of three, one in the middle with six round it, or a ring. */
enum class FolderLayout { Grid, Hexagon, Circle }

/**
 * A home screen folder: its [apps] by key, in order, under [name] ("" for the default name).
 * [span] is how many cells it takes each way (1, 2 or 3), its slot being the top-left one; [color] picks the tile colour
 * (0 the default tint).
 */
data class HomeFolder(
    val id: Int,
    val name: String,
    val apps: List<String>,
    val span: Int = 1,
    val style: FolderStyle = FolderStyle.Apps,
    val color: Int = 0,
    val layout: FolderLayout = FolderLayout.Grid,
    /** No tile behind the apps or symbol. */
    val transparent: Boolean = false,
) {
    val large: Boolean get() = span > 1
}

/**
 * Home screen folders. A folder sits in a home slot as [key] ("folder:<id>"); its apps are listed
 * here, not in the slots. Stored one per line as id, apps, looks ("span,style,color,layout,transparent") and name,
 * split by tabs (apps by [AppSeparator]); names lose tabs and line breaks. Lines from before looks
 * existed have no looks field.
 */
object HomeFolders {
    private const val Prefix = "folder:"
    private const val AppSeparator = "\u001F"

    fun key(id: Int) = "$Prefix$id"

    fun idOf(key: String?): Int? = key?.takeIf { it.startsWith(Prefix) }?.removePrefix(Prefix)?.toIntOrNull()

    fun isFolder(key: String?) = idOf(key) != null

    fun parse(stored: String?): List<HomeFolder> = stored.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t', limit = 4)
        val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val apps = parts.getOrNull(1).orEmpty().split(AppSeparator).filter { it.isNotEmpty() }
        if (parts.size < 4) return@mapNotNull HomeFolder(id, parts.getOrNull(2).orEmpty(), apps)
        val looks = parts[2].split(',')
        HomeFolder(
            id,
            parts[3],
            apps,
            // "0" was small and "1" large back when large meant 3 by 3; 2 and 3 are spans.
            span = when (val v = looks.getOrNull(0)) { "1" -> 3; else -> v?.toIntOrNull()?.coerceIn(1, 3) ?: 1 },
            style = FolderStyle.entries.firstOrNull { it.name == looks.getOrNull(1) } ?: FolderStyle.Apps,
            color = looks.getOrNull(2)?.toIntOrNull() ?: 0,
            layout = FolderLayout.entries.firstOrNull { it.name == looks.getOrNull(3) } ?: FolderLayout.Grid,
            transparent = looks.getOrNull(4) == "1",
        )
    }

    fun format(folders: List<HomeFolder>): String = folders.joinToString("\n") { f ->
        "${f.id}\t${f.apps.joinToString(AppSeparator)}\t${if (f.span == 1) 0 else f.span},${f.style.name},${f.color},${f.layout.name},${if (f.transparent) 1 else 0}\t${clean(f.name)}"
    }

    /** The slots of a large folder whose top-left slot is [slot]. */
    fun cells(slot: Int, columns: Int, span: Int): Set<Int> = (0 until span).flatMap { r -> (0 until span).map { c -> slot + r * columns + c } }.toSet()

    /** Whether the folder in [slot] shows large: asked to, and not in the last column or row. */
    fun showsLarge(folder: HomeFolder, slot: Int, columns: Int, rows: Int) =
        folder.large && slot % columns <= columns - folder.span && slot / columns <= rows - folder.span

    /** Slots taken by large folders on [page] (all of each), but for folder key [except]. */
    fun largeCells(pages: List<List<String>>, folders: List<HomeFolder>, page: Int, columns: Int, rows: Int, except: String? = null): Set<Int> {
        val byId = folders.associateBy { it.id }
        return pages.getOrNull(page).orEmpty().withIndex().filter { (slot, k) ->
            k != except && idOf(k)?.let(byId::get)?.let { showsLarge(it, slot, columns, rows) } == true
        }.flatMap { cells(it.index, columns, byId.getValue(idOf(it.value)!!).span) }.toSet()
    }

    /** The slot of the large folder on [page] covering [slot], or [slot] itself. */
    fun anchorAt(pages: List<List<String>>, folders: List<HomeFolder>, page: Int, slot: Int, columns: Int, rows: Int): Int {
        val byId = folders.associateBy { it.id }
        return pages.getOrNull(page).orEmpty().withIndex().firstOrNull { (at, k) ->
            idOf(k)?.let(byId::get)?.let { showsLarge(it, at, columns, rows) && slot in cells(at, columns, it.span) } == true
        }?.index ?: slot
    }

    /**
     * Whether folder [self] fits [span] by [span] cells with its top-left at [page]/[slot]: inside the grid, on
     * slots empty (or its own) and not in [blocked] (widgets, other large folders).
     */
    fun fitsLarge(pages: List<List<String>>, page: Int, slot: Int, columns: Int, rows: Int, blocked: Set<Int>, self: String, span: Int): Boolean {
        if (slot < 0 || slot % columns > columns - span || slot / columns > rows - span) return false
        val slots = pages.getOrNull(page).orEmpty()
        return cells(slot, columns, span).all { it !in blocked && slots.getOrNull(it).let { k -> k.isNullOrEmpty() || k == self } }
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

    /** The item in [fromPage]/[from] moved to [toPage]/[to], nothing else moving (for large folders, already checked to fit). */
    fun place(pages: List<List<String>>, fromPage: Int, from: Int, toPage: Int, to: Int): List<List<String>> {
        val key = pages.getOrNull(fromPage)?.getOrNull(from)?.takeIf { it.isNotEmpty() } ?: return pages
        val all = MutableList(maxOf(pages.size, toPage + 1)) { pages.getOrNull(it).orEmpty().toMutableList() }
        all[fromPage][from] = ""
        while (all[toPage].size <= to) all[toPage].add("")
        all[toPage][to] = key
        return trim(all)
    }

    private fun trim(pages: List<List<String>>) = pages.map { it.dropLastWhile(String::isEmpty) }
}
