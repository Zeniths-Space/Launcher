package app.zenith.launcher

/**
 * A widget on the home screen: Android's widget [id], on [page], its top-left cell at [col]/[row],
 * [w] by [h] cells.
 */
data class HomeWidget(val id: Int, val page: Int, val col: Int, val row: Int, val w: Int, val h: Int) {
    /**
     * Moved and shrunk to fit a [columns] by [rows] grid, for a grid made smaller after it was placed.
     * shortcut: a smaller grid can make apps and widgets overlap until one is moved; reflow them if that bothers people.
     */
    fun fitted(columns: Int, rows: Int): HomeWidget {
        val fw = w.coerceIn(1, columns)
        val fh = h.coerceIn(1, rows)
        return copy(col = col.coerceIn(0, columns - fw), row = row.coerceIn(0, rows - fh), w = fw, h = fh)
    }

    /** The app slots (reading order) this widget covers. */
    fun slots(columns: Int, rows: Int): Set<Int> = fitted(columns, rows).run {
        (row until row + h).flatMap { r -> (col until col + w).map { c -> r * columns + c } }.toSet()
    }
}

/** Home screen widgets, stored as "id,page,col,row,w,h" lines. */
object HomeWidgets {
    fun parse(stored: String?): List<HomeWidget> = stored.orEmpty().lines().mapNotNull { line ->
        val n = line.split(',').map { it.toIntOrNull() ?: return@mapNotNull null }
        if (n.size != 6 || n[4] < 1 || n[5] < 1) null else HomeWidget(n[0], n[1], n[2], n[3], n[4], n[5])
    }

    fun format(widgets: List<HomeWidget>): String = widgets.joinToString("\n") { "${it.id},${it.page},${it.col},${it.row},${it.w},${it.h}" }

    /** App slots on [page] taken by widgets, but for widget [except]. */
    fun blocked(widgets: List<HomeWidget>, page: Int, columns: Int, rows: Int, except: Int? = null): Set<Int> =
        widgets.filter { it.page == page && it.id != except }.flatMap { it.slots(columns, rows) }.toSet()

    /**
     * Whether [widget] lies inside the grid on cells free of apps (in [pages]), of the other
     * widgets and of slots [also] taken on its page (large folders).
     */
    fun fits(
        widget: HomeWidget,
        pages: List<List<String>>,
        widgets: List<HomeWidget>,
        columns: Int,
        rows: Int,
        also: (page: Int) -> Set<Int> = { emptySet() },
    ): Boolean {
        if (widget.col < 0 || widget.row < 0 || widget.col + widget.w > columns || widget.row + widget.h > rows) return false
        val apps = HomePages.slots(pages.getOrNull(widget.page), columns * rows)
        val taken = blocked(widgets, widget.page, columns, rows, except = widget.id) + also(widget.page)
        return widget.slots(columns, rows).none { apps.getOrNull(it) != null || it in taken }
    }

    /**
     * Where a new [w] by [h] widget goes: the first free spot in reading order on page [first], else
     * on any page, else at the top of a new page after the last.
     */
    fun place(
        id: Int,
        w: Int,
        h: Int,
        pages: List<List<String>>,
        widgets: List<HomeWidget>,
        columns: Int,
        rows: Int,
        first: Int,
        also: (page: Int) -> Set<Int> = { emptySet() },
    ): HomeWidget {
        val fw = w.coerceIn(1, columns)
        val fh = h.coerceIn(1, rows)
        val count = maxOf(pages.size, (widgets.maxOfOrNull { it.page } ?: -1) + 1, 1)
        for (page in (listOf(first) + (0 until count)).distinct()) {
            for (row in 0..rows - fh) for (col in 0..columns - fw) {
                val spot = HomeWidget(id, page, col, row, fw, fh)
                if (fits(spot, pages, widgets, columns, rows, also)) return spot
            }
        }
        return HomeWidget(id, count, 0, 0, fw, fh)
    }

    /** Widgets after page [page] is removed: its own are dropped, later pages move up one. */
    fun removePage(widgets: List<HomeWidget>, page: Int): List<HomeWidget> =
        widgets.filter { it.page != page }.map { if (it.page > page) it.copy(page = it.page - 1) else it }
}
