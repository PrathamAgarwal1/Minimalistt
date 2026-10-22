package com.minimalist.launcher.ui.blocking

import com.minimalist.launcher.domain.blocking.AppLimit
import com.minimalist.launcher.domain.blocking.Schedule
import com.minimalist.launcher.domain.blocking.ScheduleRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal object BlockingText {

    private val weekdays = DayOfWeek.entries.take(5).toSet()
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)

    fun days(days: Set<DayOfWeek>): String = when (days) {
        DayOfWeek.entries.toSet() -> "Every day"
        weekdays -> "Weekdays"
        weekend -> "Weekends"
        emptySet<DayOfWeek>() -> "No days"
        else -> DayOfWeek.entries.filter { it in days }.joinToString(" ") { dayName(it) }
    }

    fun window(schedule: Schedule): String =
        if (schedule.startMinute == schedule.endMinute) {
            "All day"
        } else {
            "${ScheduleRules.formatMinute(schedule.startMinute)}–${ScheduleRules.formatMinute(schedule.endMinute)}"
        }

    fun limit(limit: AppLimit): String = listOfNotNull(
        limit.maxMinutes?.let { "${minutes(it)} a day" },
        limit.maxOpens?.let { "$it ${if (it == 1) "open" else "opens"} a day" },
    ).joinToString(" · ")

    fun minutes(total: Int): String = when {
        total < 60 -> "$total min"
        total % 60 == 0 -> "${total / 60} h"
        else -> "${total / 60} h ${total % 60} min"
    }

    /** "until 18:00" today, otherwise "until Sat 24 Oct, 18:00". */
    fun until(millis: Long): String {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        val clock = time.format(DateTimeFormatter.ofPattern("HH:mm"))
        return if (time.toLocalDate() == LocalDate.now(zone)) "until $clock" else "until ${time.format(DateTimeFormatter.ofPattern("EEE d MMM"))}, $clock"
    }
}
