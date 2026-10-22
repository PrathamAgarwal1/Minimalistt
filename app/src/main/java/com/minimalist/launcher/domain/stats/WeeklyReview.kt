package com.minimalist.launcher.domain.stats

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object WeeklyReview {

    const val HOUR = 18

    /** The most recent Sunday 18:00 at or before [now]: the review is due if it hasn't been shown since. */
    fun dueSince(now: ZonedDateTime): ZonedDateTime {
        val sunday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val thisWeek = sunday.atStartOfDay(now.zone).plusHours(HOUR.toLong())
        return if (now.isBefore(thisWeek)) thisWeek.minusWeeks(1) else thisWeek
    }

    fun isDue(lastShownMillis: Long?, now: ZonedDateTime): Boolean =
        (lastShownMillis ?: 0L) < dueSince(now).toInstant().toEpochMilli()

    /** Percent change from [previous] to [current], or null without a previous week. */
    fun change(previous: Long, current: Long): Int? =
        if (previous <= 0) null else (((current - previous) * 100.0) / previous).toInt()
}
