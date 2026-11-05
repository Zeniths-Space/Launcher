package app.zenith.launcher

/**
 * Home screen pages: each a list of app keys by slot in reading order, "" for an empty slot.
 * Stored with slots split by line breaks and pages by "|", which neither package nor class names
 * can contain. Slots past a page's size are kept, so shrinking the grid and growing it back loses
 * nothing. Empty pages are kept, so a page added from the home menu stays until removed.
 */
object HomePages {
    fun parse(stored: String?): List<List<String>> =
        if (stored.isNullOrEmpty()) emptyList() else stored.split(PageSeparator).map { it.split(SlotSeparator).dropLastWhile(String::isEmpty) }

    fun format(pages: List<List<String>>): String =
        pages.map { it.dropLastWhile(String::isEmpty) }.joinToString(PageSeparator) { it.joinToString(SlotSeparator) }

    /** [page]'s slots with null for empty ones, at least [capacity] long. */
    fun slots(page: List<String>?, capacity: Int): List<String?> =
        List(maxOf(page.orEmpty().size, capacity)) { page?.getOrNull(it)?.ifEmpty { null } }

    /**
     * Moves the app in [fromPage]/[from] to [toPage]/[to], the others on that page making room as
     * in [DockSlots.place]. Onto another page it enters by the empty slot nearest [to]; null if
     * that page has none.
     */
    fun move(pages: List<List<String>>, fromPage: Int, from: Int, toPage: Int, to: Int, capacity: Int): List<List<String>>? {
        val all = MutableList(maxOf(pages.size, toPage + 1, fromPage + 1)) { slots(pages.getOrNull(it), capacity) }
        val key = all[fromPage].getOrNull(from) ?: return null
        if (fromPage == toPage) {
            all[toPage] = DockSlots.place(all[toPage], from, to)
        } else {
            val page = all[toPage].toMutableList()
            val gap = (0 until capacity).filter { page[it] == null }.minByOrNull { kotlin.math.abs(it - to) } ?: return null
            page[gap] = key
            all[toPage] = DockSlots.place(page, gap, to)
            all[fromPage] = all[fromPage].toMutableList().also { it[from] = null }
        }
        return all.map { page -> page.map { it.orEmpty() }.dropLastWhile(String::isEmpty) }
    }

    /** Puts [key] in the first empty slot of page [first] if it has one, else of any page, else on a new page at the end. */
    fun add(pages: List<List<String>>, key: String, capacity: Int, first: Int = 0): List<List<String>> {
        (listOf(first) + pages.indices).forEach { p ->
            val page = pages.getOrNull(p) ?: return@forEach
            val gap = (0 until capacity).firstOrNull { page.getOrNull(it).isNullOrEmpty() } ?: return@forEach
            return pages.toMutableList().also { it[p] = List(maxOf(page.size, gap + 1)) { i -> if (i == gap) key else page.getOrElse(i) { "" } } }
        }
        return pages + listOf(listOf(key))
    }

    fun remove(pages: List<List<String>>, key: String): List<List<String>> =
        pages.map { page -> page.map { if (it == key) "" else it }.dropLastWhile(String::isEmpty) }

    private const val PageSeparator = "|"
    private const val SlotSeparator = "\n"
}
