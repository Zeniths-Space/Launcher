package com.elara.launcher

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** The icons a space can show on its switch. */
enum class SpaceIcon { Home, Work, Night, School, Fitness, Travel, Focus, Weekend }

/**
 * A home setup of its own: pages (with widgets), dock and hidden apps. The active space's are the
 * ordinary home, dock and hidden settings; the others wait here in [layout].
 * The first space is the default, on whenever no other's schedule is.
 */
data class Space(
    val id: Long,
    val name: String,
    val icon: SpaceIcon = SpaceIcon.Home,
    /** Days the schedule is on, bit 0 Sunday to bit 6 Saturday; 0 for no schedule. */
    val days: Int = 0,
    /** Minutes after midnight; [to] before [from] runs past midnight into the next day. */
    val from: Int = 9 * 60,
    val to: Int = 17 * 60,
    /** The waiting layout; ignored for the active space, whose layout is live. */
    val layout: SpaceLayout = SpaceLayout(),
) {
    val scheduled get() = days != 0 && from != to
}

/** One space's stored home: the same text forms the home, folders, widgets, dock and hidden settings use. */
data class SpaceLayout(
    val home: String = "",
    val folders: String = "",
    val widgets: String = "",
    val dock: String = "",
    val hidden: String = "",
)

object Spaces {
    private fun dayBit(day: DayOfWeek) = 1 shl (day.value % 7) // Sunday 0 … Saturday 6

    /** Whether [space]'s schedule covers [now]; a window past midnight belongs to the day it starts. */
    fun covers(space: Space, now: LocalDateTime): Boolean {
        if (!space.scheduled) return false
        val minute = now.hour * 60 + now.minute
        return if (space.from < space.to) {
            space.days and dayBit(now.dayOfWeek) != 0 && minute >= space.from && minute < space.to
        } else {
            (space.days and dayBit(now.dayOfWeek) != 0 && minute >= space.from) ||
                (space.days and dayBit(now.dayOfWeek.minus(1)) != 0 && minute < space.to)
        }
    }

    /** The space the schedule wants at [now]: the first scheduled one covering it, else the default (first). */
    fun scheduled(spaces: List<Space>, now: LocalDateTime): Space? =
        spaces.drop(1).firstOrNull { covers(it, now) } ?: spaces.firstOrNull()

    /** The next minute any schedule starts or ends after [now], within a week; null with no schedules. */
    fun nextChange(spaces: List<Space>, now: LocalDateTime): LocalDateTime? {
        if (spaces.none { it.scheduled }) return null
        val start = now.truncatedTo(ChronoUnit.MINUTES)
        val wanted = scheduled(spaces, start)?.id
        // Minute steps over a week are 10,080 checks: cheap, and run once per visit home, not on a timer.
        var t = start.plusMinutes(1)
        val end = start.plusDays(7)
        while (t <= end) {
            if (scheduled(spaces, t)?.id != wanted) return t
            t = t.plusMinutes(1)
        }
        return null
    }

    /** Epoch ms of the next schedule change after [now], for holding a hand-picked space until then; never, without schedules. */
    fun holdUntil(spaces: List<Space>, now: LocalDateTime = LocalDateTime.now()): Long =
        nextChange(spaces, now)?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE

    /**
     * A hand-picked space's hold, [until] (epoch ms), after the schedules change from [old] to [new]:
     * the hold lasts until the schedule next wants another space, so it ends now (0) if the new
     * schedule already does, and otherwise at the new next change if that comes sooner.
     */
    fun rehold(until: Long, old: List<Space>, new: List<Space>, now: LocalDateTime = LocalDateTime.now()): Long {
        if (scheduled(old, now)?.id != scheduled(new, now)?.id) return 0
        return minOf(until, holdUntil(new, now))
    }

    /** "Mon–Fri · 9:00–17:00", "Every day · 22:00–7:00", or null without a schedule. */
    fun describe(space: Space, dayNames: List<String>, everyDay: String, weekdays: String, weekends: String): String? {
        if (!space.scheduled) return null
        val days = when (space.days) {
            0b1111111 -> everyDay
            0b0111110 -> weekdays
            0b1000001 -> weekends
            else -> (0..6).filter { space.days and (1 shl it) != 0 }.joinToString(", ") { dayNames[it] }
        }
        return "$days · ${time(space.from)}–${time(space.to)}"
    }

    fun time(minutes: Int) = "%d:%02d".format(minutes / 60, minutes % 60)

    // One space per line, its fields tab-separated and escaped as a backup's text is.
    fun format(spaces: List<Space>): String = spaces.joinToString("\n") { s ->
        listOf(
            s.id.toString(), s.name, s.icon.name, s.days.toString(), s.from.toString(), s.to.toString(),
            s.layout.home, s.layout.folders, s.layout.widgets, s.layout.dock, s.layout.hidden,
        ).joinToString("\t") { Backup.escape(it) }
    }

    fun parse(stored: String?): List<Space> = stored.orEmpty().lines().mapNotNull { line ->
        val f = line.split('\t').map { Backup.unescape(it) }
        if (f.size != 11) return@mapNotNull null
        Space(
            id = f[0].toLongOrNull() ?: return@mapNotNull null,
            name = f[1].ifBlank { return@mapNotNull null },
            icon = SpaceIcon.entries.firstOrNull { it.name == f[2] } ?: SpaceIcon.Home,
            days = f[3].toIntOrNull()?.and(0b1111111) ?: 0,
            from = f[4].toIntOrNull()?.coerceIn(0, 1439) ?: 540,
            to = f[5].toIntOrNull()?.coerceIn(0, 1439) ?: 1020,
            layout = SpaceLayout(f[6], f[7], f[8], f[9], f[10]),
        )
    }.distinctBy { it.id }
}
