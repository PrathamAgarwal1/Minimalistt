package com.minimalist.launcher.domain.protection

import com.minimalist.launcher.domain.blocking.AppLimit
import com.minimalist.launcher.domain.blocking.Schedule
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.modes.DayModeConfig
import com.minimalist.launcher.domain.modes.StudyWindow
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant

/**
 * Whether a settings change weakens protection. Weakening changes wait out a cooldown;
 * strengthening and neutral ones apply at once. When unsure, a change counts as weakening.
 */
object Protection {

    fun cooldownEndsAt(queuedAtMillis: Long, scale: TimeScale): Long = queuedAtMillis + scale.cooldown.toMillis()

    fun limitWeakens(old: AppLimit?, new: AppLimit?): Boolean {
        if (old == null) return false
        return capLoosens(old.maxMinutes, new?.maxMinutes) || capLoosens(old.maxOpens, new?.maxOpens)
    }

    private fun capLoosens(old: Int?, new: Int?): Boolean = old != null && (new == null || new > old)

    fun scheduleWeakens(old: Schedule?, new: Schedule?): Boolean {
        if (old == null) return false
        if (new == null) return true
        if (old.enabled && !new.enabled) return true
        if ((old.packages - new.packages).isNotEmpty()) return true
        // The old windows must all still be covered.
        return !covered(new).containsAll(covered(old))
    }

    /** Every (day the window starts, minute of the week) a schedule covers, as a set of week minutes. */
    private fun covered(schedule: Schedule): Set<Int> {
        if (!schedule.enabled) return emptySet()
        val length = when {
            schedule.startMinute == schedule.endMinute -> ScheduleRules.MINUTES_PER_DAY
            schedule.endMinute > schedule.startMinute -> schedule.endMinute - schedule.startMinute
            else -> ScheduleRules.MINUTES_PER_DAY - schedule.startMinute + schedule.endMinute
        }
        val week = 7 * ScheduleRules.MINUTES_PER_DAY
        val start = if (schedule.startMinute == schedule.endMinute) 0 else schedule.startMinute
        return schedule.days.flatMap { day ->
            val dayStart = (day.value - 1) * ScheduleRules.MINUTES_PER_DAY + start
            (0 until length).map { (dayStart + it) % week }
        }.toSet()
    }

    /**
     * Turning a mode off, a later bedtime, an earlier wake or opening time, or a shorter
     * morning all weaken. Bedtimes count from noon, so 00:30 is later than 23:00.
     */
    fun dayModesWeaken(old: DayModeConfig, new: DayModeConfig): Boolean {
        if (old.nightEnabled && !new.nightEnabled) return true
        if (old.morningEnabled && !new.morningEnabled) return true
        if (old.nightEnabled) {
            if (fromNoon(new.bedWeekday) > fromNoon(old.bedWeekday)) return true
            if (fromNoon(new.bedWeekend) > fromNoon(old.bedWeekend)) return true
        }
        if (old.nightEnabled || old.morningEnabled) {
            if (new.wakeWeekday < old.wakeWeekday) return true
            if (new.wakeWeekend < old.wakeWeekend) return true
        }
        if (old.morningEnabled && new.morningMinutes < old.morningMinutes) return true
        return false
    }

    private fun fromNoon(minute: Int) = (minute - 12 * 60 + ScheduleRules.MINUTES_PER_DAY) % ScheduleRules.MINUTES_PER_DAY

    /** 0 means off. A shorter Put away after unplugging weakens. */
    fun unplugWeakens(oldMinutes: Int, newMinutes: Int): Boolean = oldMinutes > 0 && (newMinutes == 0 || newMinutes < oldMinutes)

    /** A higher pickup threshold, or a shorter Put away after it, weakens. */
    fun pickupsWeaken(oldThreshold: Int, oldMinutes: Int, newThreshold: Int, newMinutes: Int): Boolean =
        oldThreshold > 0 && (newThreshold == 0 || newThreshold > oldThreshold || newMinutes < oldMinutes)

    fun studyWindowsWeaken(old: List<StudyWindow>, new: List<StudyWindow>): Boolean = !new.toSet().containsAll(old)

    /** More apps allowed through modes. */
    fun allowedAppsWeaken(old: Set<String>, new: Set<String>): Boolean = (new - old).isNotEmpty()

    /** Setting or swapping the audio or notes app lets another app through; clearing it does not. */
    fun modeAppWeakens(old: String?, new: String?): Boolean = new != null && new != old

    fun batchingWeakens(oldEnabled: Boolean, oldTimes: List<Int>, newEnabled: Boolean, newTimes: List<Int>): Boolean =
        oldEnabled && (!newEnabled || (newTimes - oldTimes.toSet()).isNotEmpty())

    /** Choosing a browser strengthens; dropping or swapping it weakens. */
    fun browserWeakens(old: String?, new: String?): Boolean = old != null && new != old

    fun urgentWeakens(old: Int, new: Int): Boolean = new > old

    fun testModeWeakens(new: Boolean): Boolean = new
}

/** One emergency unlock per calendar month (per hour in test mode). */
object EmergencyRules {

    const val MINUTES = 15

    fun available(previousUnlocks: List<Long>, nowMillis: Long, zone: ZoneId, scale: TimeScale): Boolean {
        if (scale.testMode) return previousUnlocks.none { it > nowMillis - 3_600_000L }
        val month = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone))
        return previousUnlocks.none { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) == month }
    }
}
