package com.minimalist.launcher.domain.blocking

import java.time.LocalDate
import java.time.ZonedDateTime

object ScheduleRules {

    const val MINUTES_PER_DAY = 24 * 60

    /**
     * If [schedule] is running at [now], when it ends; otherwise null.
     *
     * A window whose end is before its start runs overnight and belongs to the day it starts on:
     * "22:00–08:00 on Friday" covers Friday night into Saturday morning. Equal start and end
     * means the whole day.
     */
    fun activeUntil(schedule: Schedule, now: ZonedDateTime): ZonedDateTime? {
        if (!schedule.enabled || schedule.days.isEmpty()) return null
        val minute = now.hour * 60 + now.minute
        val today = now.toLocalDate()
        val start = schedule.startMinute
        val end = schedule.endMinute
        val runsToday = now.dayOfWeek in schedule.days
        val ranYesterday = now.dayOfWeek.minus(1) in schedule.days

        fun at(date: LocalDate, minuteOfDay: Int) = date.atStartOfDay(now.zone).plusMinutes(minuteOfDay.toLong())

        return when {
            start == end -> if (runsToday) at(today.plusDays(1), 0) else null
            start < end -> if (runsToday && minute >= start && minute < end) at(today, end) else null
            runsToday && minute >= start -> at(today.plusDays(1), end)
            ranYesterday && minute < end -> at(today, end)
            else -> null
        }
    }

    fun formatMinute(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}
