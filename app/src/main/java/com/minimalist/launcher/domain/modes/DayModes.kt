package com.minimalist.launcher.domain.modes

import com.minimalist.launcher.domain.blocking.Schedule
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.domain.blocking.TimeScale
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** The phone-wide locks. Only essentials (and for morning and night, one audio and one notes app) work. */
enum class LockKind(val label: String) {
    PutAway("Put away"),
    Morning("Morning"),
    Night("Night"),
}

data class ActiveLock(val kind: LockKind, val untilMillis: Long)

data class DayModeConfig(
    val morningEnabled: Boolean = false,
    val wakeWeekday: Int = 7 * 60,
    val wakeWeekend: Int = 8 * 60 + 30,
    val morningMinutes: Int = 60,
    val nightEnabled: Boolean = false,
    val bedWeekday: Int = 23 * 60,
    val bedWeekend: Int = 23 * 60 + 30,
)

object DayModes {

    private const val NOON = 12 * 60
    private val weekendMornings = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val weekendNights = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

    /** Saturday and Sunday mornings use the weekend wake time. */
    fun wakeMinute(config: DayModeConfig, date: LocalDate) =
        if (date.dayOfWeek in weekendMornings) config.wakeWeekend else config.wakeWeekday

    /** Friday and Saturday nights use the weekend bedtime. */
    fun bedMinute(config: DayModeConfig, date: LocalDate) =
        if (date.dayOfWeek in weekendNights) config.bedWeekend else config.bedWeekday

    /**
     * If night mode is running at [now], when it ends (the next wake time). A bedtime before
     * noon is read as after midnight, so 00:30 means the small hours of the next day.
     */
    fun nightUntil(config: DayModeConfig, now: ZonedDateTime): ZonedDateTime? {
        if (!config.nightEnabled) return null
        val today = now.toLocalDate()
        for (night in listOf(today.minusDays(1), today)) {
            val morning = night.plusDays(1)
            val bed = bedMinute(config, night)
            val start = if (bed < NOON) at(morning, bed, now) else at(night, bed, now)
            val end = at(morning, wakeMinute(config, morning), now)
            if (!now.isBefore(start) && now.isBefore(end)) return end
        }
        return null
    }

    /** Morning mode starts with the first unlock after wake time, at most six hours late, once a day. */
    fun shouldStartMorning(config: DayModeConfig, now: ZonedDateTime, lastMorningDay: Long?): Boolean {
        if (!config.morningEnabled) return false
        val today = now.toLocalDate()
        if (lastMorningDay == today.toEpochDay()) return false
        val wake = at(today, wakeMinute(config, today), now)
        return !now.isBefore(wake) && now.isBefore(wake.plusHours(6)) && nightUntil(config, now) == null
    }

    /** Put away wins over night, night over morning. */
    fun activeLock(
        putAwayUntil: Long?,
        morningStartedAt: Long?,
        config: DayModeConfig,
        now: ZonedDateTime,
        scale: TimeScale = TimeScale.REAL,
    ): ActiveLock? {
        val nowMillis = now.toInstant().toEpochMilli()
        putAwayUntil?.takeIf { it > nowMillis }?.let { return ActiveLock(LockKind.PutAway, it) }
        nightUntil(config, now)?.let { return ActiveLock(LockKind.Night, it.toInstant().toEpochMilli()) }
        if (config.morningEnabled && morningStartedAt != null) {
            val end = morningStartedAt + scale.dailyLimit(Duration.ofMinutes(config.morningMinutes.toLong())).toMillis()
            if (nowMillis in morningStartedAt until end) return ActiveLock(LockKind.Morning, end)
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
