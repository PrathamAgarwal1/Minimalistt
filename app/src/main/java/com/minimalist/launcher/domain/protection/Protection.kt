package com.minimalist.launcher.domain.protection

import com.minimalist.launcher.domain.blocking.AppLimit
import com.minimalist.launcher.domain.blocking.Schedule
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.challenge.ChallengePlan
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
     * Turning night off, a later bedtime, an earlier opening time, a shorter wind-down, or
     * dropping grayscale or the red tint all weaken. Bedtimes count from noon, so 00:30 is
     * later than 23:00. The reminder is only a nudge, so changing it is neutral.
     */
    fun dayModesWeaken(old: DayModeConfig, new: DayModeConfig): Boolean {
        if (!old.nightEnabled) return false
        if (!new.nightEnabled) return true
        if (fromNoon(new.bedWeekday) > fromNoon(old.bedWeekday)) return true
        if (fromNoon(new.bedWeekend) > fromNoon(old.bedWeekend)) return true
        if (new.openWeekday < old.openWeekday) return true
        if (new.openWeekend < old.openWeekend) return true
        if (new.windDownMinutes < old.windDownMinutes) return true
        if (old.grayscale && !new.grayscale) return true
        if (old.redTint && !new.redTint) return true
        return false
    }

    /** A shorter first friction countdown weakens. */
    fun frictionBaseWeakens(oldSeconds: Int, newSeconds: Int): Boolean = newSeconds < oldSeconds

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

/**
 * Three emergency unlocks per calendar month (per hour in test mode) come free. Past that,
 * each one costs a hard challenge, two problems harder for every extra one already used.
 */
object EmergencyRules {

    const val MINUTES = 15
    const val FREE_PER_MONTH = 3

    fun usedThisPeriod(previousUnlocks: List<Long>, nowMillis: Long, zone: ZoneId, scale: TimeScale): Int {
        if (scale.testMode) return previousUnlocks.count { it > nowMillis - 3_600_000L }
        val month = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(zone))
        return previousUnlocks.count { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) == month }
    }

    fun freeLeft(used: Int): Int = (FREE_PER_MONTH - used).coerceAtLeast(0)

    /** The challenge for an emergency unlock beyond the free ones. */
    fun extraPlan(used: Int, scale: TimeScale): ChallengePlan {
        if (scale.testMode) return ChallengePlan(easy = 1)
        val extraAlready = (used - FREE_PER_MONTH).coerceAtLeast(0)
        return ChallengePlan(hard = 15, passage = true).plus(2 * extraAlready)
    }
}
