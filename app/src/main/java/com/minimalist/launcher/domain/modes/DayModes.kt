package com.minimalist.launcher.domain.modes

import com.minimalist.launcher.domain.blocking.Schedule
import com.minimalist.launcher.domain.blocking.ScheduleRules
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** The phone-wide locks. Only essentials (and at night, one audio and one notes app) work. */
enum class LockKind(val label: String) {
    PutAway("Put away"),
    Night("Night"),

    /** While an unlock challenge runs: only calls, SMS and the clock, so no calculator or browser. */
    Challenge("A challenge"),
}

data class ActiveLock(val kind: LockKind, val untilMillis: Long)

/**
 * Night runs from bedtime until the phone opens. The hour (or chosen length) before bedtime is
 * wind-down: grayscale, red tint and slower friction.
 */
data class DayModeConfig(
    val nightEnabled: Boolean = false,
    val bedWeekday: Int = 23 * 60,
    val bedWeekend: Int = 23 * 60 + 30,
    val openWeekday: Int = 8 * 60,
    val openWeekend: Int = 9 * 60,
    /** 0 turns wind-down off. */
    val windDownMinutes: Int = 60,
    val grayscale: Boolean = true,
    val redTint: Boolean = true,
    /** Minutes before bedtime for the reminder; 0 is off. */
    val reminderMinutes: Int = 15,
)

/** The night that is running: when it started and when the phone opens. */
data class NightWindow(val start: ZonedDateTime, val opens: ZonedDateTime)

object DayModes {

    private const val NOON = 12 * 60
    private val weekendMornings = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val weekendNights = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

    /** Saturday and Sunday mornings use the weekend opening time. */
    fun openMinute(config: DayModeConfig, date: LocalDate) =
        if (date.dayOfWeek in weekendMornings) config.openWeekend else config.openWeekday

    /** Friday and Saturday nights use the weekend bedtime. */
    fun bedMinute(config: DayModeConfig, date: LocalDate) =
        if (date.dayOfWeek in weekendNights) config.bedWeekend else config.bedWeekday

    /** Bedtime for the night that starts on [night]. A bedtime before noon falls after midnight. */
    fun bedtime(config: DayModeConfig, night: LocalDate, zoneOf: ZonedDateTime): ZonedDateTime {
        val bed = bedMinute(config, night)
        return if (bed < NOON) at(night.plusDays(1), bed, zoneOf) else at(night, bed, zoneOf)
    }

    /** The night running at [now], if any. */
    fun night(config: DayModeConfig, now: ZonedDateTime): NightWindow? {
        if (!config.nightEnabled) return null
        val today = now.toLocalDate()
        for (night in listOf(today.minusDays(1), today)) {
            val start = bedtime(config, night, now)
            val morning = night.plusDays(1)
            val opens = at(morning, openMinute(config, morning), now)
            if (!now.isBefore(start) && now.isBefore(opens)) return NightWindow(start, opens)
        }
        return null
    }

    /** If night mode is running at [now], when the phone opens. */
    fun nightUntil(config: DayModeConfig, now: ZonedDateTime): ZonedDateTime? = night(config, now)?.opens

    /** If wind-down is running at [now], the bedtime it leads to. */
    fun windDownUntil(config: DayModeConfig, now: ZonedDateTime): ZonedDateTime? {
        if (!config.nightEnabled || config.windDownMinutes <= 0) return null
        val today = now.toLocalDate()
        return listOf(today.minusDays(1), today, today.plusDays(1))
            .map { bedtime(config, it, now) }
            .firstOrNull { bed -> now.isBefore(bed) && !now.isBefore(bed.minusMinutes(config.windDownMinutes.toLong())) }
    }

    /** The bedtime coming up within the reminder window, if any. */
    fun reminderFor(config: DayModeConfig, now: ZonedDateTime): ZonedDateTime? {
        if (!config.nightEnabled || config.reminderMinutes <= 0) return null
        val today = now.toLocalDate()
        return listOf(today.minusDays(1), today, today.plusDays(1))
            .map { bedtime(config, it, now) }
            .firstOrNull { bed -> now.isBefore(bed) && !now.isBefore(bed.minusMinutes(config.reminderMinutes.toLong())) }
    }

    /**
     * Put away wins over night. A night already opened early by a morning challenge
     * ([nightReleasedUntil] equal to its opening time) is over.
     */
    fun activeLock(
        putAwayUntil: Long?,
        nightReleasedUntil: Long?,
        config: DayModeConfig,
        now: ZonedDateTime,
    ): ActiveLock? {
        val nowMillis = now.toInstant().toEpochMilli()
        putAwayUntil?.takeIf { it > nowMillis }?.let { return ActiveLock(LockKind.PutAway, it) }
        night(config, now)?.let { window ->
            val opens = window.opens.toInstant().toEpochMilli()
            if (nightReleasedUntil != opens) return ActiveLock(LockKind.Night, opens)
        }
        return null
    }

    /** Put away can only grow: extending adds to the time left. */
    fun extendPutAway(currentUntil: Long?, nowMillis: Long, duration: Duration): Long =
        maxOf(currentUntil ?: 0L, nowMillis) + duration.toMillis()

    private fun at(date: LocalDate, minuteOfDay: Int, zoneOf: ZonedDateTime) =
        date.atStartOfDay(zoneOf.zone).plusMinutes(minuteOfDay.toLong())
}

/** Study hours that start Put away by themselves. */
data class StudyWindow(val startMinute: Int, val endMinute: Int, val days: Set<DayOfWeek>) {

    fun activeUntil(now: ZonedDateTime): ZonedDateTime? =
        ScheduleRules.activeUntil(Schedule(0, "Study", startMinute, endMinute, days, emptySet(), true), now)

    companion object {
        /** "540-780-31;..." : start, end, day bitmask. */
        fun encode(windows: List<StudyWindow>): String = windows.joinToString(";") { window ->
            val mask = window.days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }
            "${window.startMinute}-${window.endMinute}-$mask"
        }

        fun decode(text: String?): List<StudyWindow> = text.orEmpty().split(';').mapNotNull { part ->
            val numbers = part.split('-').mapNotNull { it.toIntOrNull() }
            if (numbers.size != 3) return@mapNotNull null
            val days = DayOfWeek.entries.filter { numbers[2] and (1 shl (it.value - 1)) != 0 }.toSet()
            StudyWindow(numbers[0], numbers[1], days)
        }
    }
}

object PickupRules {

    /** True when [threshold] or more unlocks happened in the hour before [nowMillis]. 0 means off. */
    fun tooMany(pickupTimes: List<Long>, nowMillis: Long, threshold: Int): Boolean =
        threshold > 0 && pickupTimes.count { it in (nowMillis - 3_600_000L + 1)..nowMillis } >= threshold
}
