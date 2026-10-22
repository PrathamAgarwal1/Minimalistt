package com.minimalist.launcher.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleRulesTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    // 2026-10-23 is a Friday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    private fun schedule(start: String, end: String, days: Set<DayOfWeek> = DayOfWeek.entries.toSet(), enabled: Boolean = true): Schedule {
        fun minutes(t: String) = t.substringBefore(':').toInt() * 60 + t.substringAfter(':').toInt()
        return Schedule(1, "Test", minutes(start), minutes(end), days, setOf("app"), enabled)
    }

    @Test
    fun daytimeWindowIsActiveOnlyInsideIt() {
        val classes = schedule("09:00", "13:00", setOf(MONDAY, FRIDAY))
        assertNull(ScheduleRules.activeUntil(classes, at(23, 8, 59)))
        assertEquals(at(23, 13), ScheduleRules.activeUntil(classes, at(23, 9)))
        assertEquals(at(23, 13), ScheduleRules.activeUntil(classes, at(23, 12, 59)))
        assertNull(ScheduleRules.activeUntil(classes, at(23, 13)))
    }

    @Test
    fun daytimeWindowRespectsWeekdays() {
        val classes = schedule("09:00", "13:00", setOf(MONDAY, FRIDAY))
        assertNull(ScheduleRules.activeUntil(classes, at(24, 10))) // Saturday
    }

    @Test
    fun overnightWindowRunsPastMidnight() {
        val night = schedule("22:00", "08:00")
        assertNull(ScheduleRules.activeUntil(night, at(23, 21, 59)))
        assertEquals(at(24, 8), ScheduleRules.activeUntil(night, at(23, 22)))
        assertEquals(at(24, 8), ScheduleRules.activeUntil(night, at(24, 7, 59)))
        assertNull(ScheduleRules.activeUntil(night, at(24, 8)))
    }

    @Test
    fun overnightWindowBelongsToTheDayItStarts() {
        val fridayNight = schedule("22:00", "08:00", setOf(FRIDAY))
        assertEquals(at(24, 8), ScheduleRules.activeUntil(fridayNight, at(24, 2))) // Saturday 2am
        assertNull(ScheduleRules.activeUntil(fridayNight, at(23, 2))) // Friday 2am: Thursday night not chosen
        assertNull(ScheduleRules.activeUntil(fridayNight, at(24, 23))) // Saturday night
    }

    @Test
    fun overnightFromSundayIntoMonday() {
        val sundayNight = schedule("23:00", "06:00", setOf(SUNDAY))
        assertEquals(at(26, 6), ScheduleRules.activeUntil(sundayNight, at(26, 3))) // Monday 3am
    }

    @Test
    fun equalStartAndEndMeansAllDay() {
        val saturday = schedule("00:00", "00:00", setOf(SATURDAY))
        assertEquals(at(25, 0), ScheduleRules.activeUntil(saturday, at(24, 15)))
        assertNull(ScheduleRules.activeUntil(saturday, at(23, 15)))
    }

    @Test
    fun disabledOrDaylessSchedulesNeverRun() {
        assertNull(ScheduleRules.activeUntil(schedule("00:00", "23:59", enabled = false), at(23, 12)))
        assertNull(ScheduleRules.activeUntil(schedule("00:00", "23:59", days = emptySet()), at(23, 12)))
    }

    @Test
    fun formatsMinuteOfDay() {
        assertEquals("08:05", ScheduleRules.formatMinute(8 * 60 + 5))
        assertEquals("22:00", ScheduleRules.formatMinute(22 * 60))
    }
}
