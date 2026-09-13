package fr.bonamy.sports.core

import kotlin.math.max

enum class ScheduleSection(val label: String) { CURRENT("Current"), UPCOMING("Upcoming"), UNKNOWN("Time unconfirmed"), CHANNELS("Channels"), EARLIER("Earlier") }

object Schedule {
    fun section(event: SportsEvent, sport: Sport, now: Long): ScheduleSection {
        if (event.isChannel) return ScheduleSection.CHANNELS
        val start = event.startsAt ?: return ScheduleSection.UNKNOWN
        if (start > now) return ScheduleSection.UPCOMING
        return if (now - start < sport.windowHours * 3_600_000) ScheduleSection.CURRENT else ScheduleSection.EARLIER
    }

    fun countdown(start: Long, now: Long): String {
        val minutes = max(1, (start - now + 59_999) / 60_000)
        return when {
            minutes >= 1440 -> "in ${minutes / 1440}d ${(minutes % 1440) / 60}h"
            minutes >= 60 -> "in ${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"
            else -> "in ${minutes}m"
        }
    }
}
