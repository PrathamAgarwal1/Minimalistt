package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.modes.ActiveLock
import com.minimalist.launcher.domain.modes.DayModeConfig
import com.minimalist.launcher.domain.modes.DayModes
import com.minimalist.launcher.domain.modes.LockKind
import com.minimalist.launcher.domain.modes.PickupRules
import com.minimalist.launcher.domain.modes.StudyWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class DayModesTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    // 2026-10-22 is a Thursday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)
    private fun ms(time: ZonedDateTime) = time.toInstant().toEpochMilli()

    private val config = DayModeConfig(
        morningEnabled = true,
        wakeWeekday = 7 * 60,
        wakeWeekend = 9 * 60,
        morningMinutes = 60,
        nightEnabled = true,
        bedWeekday = 23 * 60,
        bedWeekend = 30, // 00:30
    )

    @Test
    fun weekdayNightRunsFromBedtimeToWake() {
        assertNull(DayModes.nightUntil(config, at(22, 22, 59)))
        assertEquals(at(23, 7), DayModes.nightUntil(config, at(22, 23)))
        assertEquals(at(23, 7), DayModes.nightUntil(config, at(23, 6, 59)))
        assertNull(DayModes.nightUntil(config, at(23, 7)))
    }

    @Test
    fun weekendBedtimeAfterMidnightAndLaterWake() {
        // Friday night: bedtime 00:30 on Saturday, Saturday wake 09:00.
        assertNull(DayModes.nightUntil(config, at(23, 23, 30)))
        assertNull(DayModes.nightUntil(config, at(24, 0, 15)))
        assertEquals(at(24, 9), DayModes.nightUntil(config, at(24, 0, 30)))
        assertEquals(at(24, 9), DayModes.nightUntil(config, at(24, 8, 59)))
    }

    @Test
    fun sundayNightUsesWeekdayBedtimeAndMondayWake() {
        assertEquals(at(26, 7), DayModes.nightUntil(config, at(25, 23, 10)))
    }

    @Test
    fun nightOffMeansNoNight() {
        assertNull(DayModes.nightUntil(config.copy(nightEnabled = false), at(22, 23, 30)))
    }

    @Test
    fun morningStartsOnFirstUnlockAfterWakeOncePerDay() {
        assertFalse(DayModes.shouldStartMorning(config, at(23, 6, 50), null)) // still night
        assertTrue(DayModes.shouldStartMorning(config, at(23, 7, 5), null))
        assertFalse(DayModes.shouldStartMorning(config, at(23, 7, 30), at(23, 0).toLocalDate().toEpochDay()))
        assertFalse(DayModes.shouldStartMorning(config, at(23, 13, 30), null)) // more than 6 h late
        assertFalse(DayModes.shouldStartMorning(config.copy(morningEnabled = false), at(23, 7, 5), null))
    }

    @Test
    fun putAwayBeatsNightBeatsMorning() {
        val nightTime = at(22, 23, 30)
        val putAwayEnd = ms(nightTime.plusMinutes(30))
        assertEquals(ActiveLock(LockKind.PutAway, putAwayEnd), DayModes.activeLock(putAwayEnd, null, config, nightTime))
        assertEquals(LockKind.Night, DayModes.activeLock(null, null, config, nightTime)?.kind)

        val morningStart = ms(at(23, 7, 5))
        val lock = DayModes.activeLock(null, morningStart, config, at(23, 7, 30))
        assertEquals(ActiveLock(LockKind.Morning, morningStart + 3_600_000), lock)
        assertNull(DayModes.activeLock(null, morningStart, config, at(23, 8, 6)))
    }

    @Test
    fun expiredPutAwayIsNoLock() {
        assertNull(DayModes.activeLock(ms(at(22, 12)), null, config, at(22, 12, 1)))
    }

    @Test
    fun testModeShortensMorning() {
        val start = ms(at(23, 7, 5))
        val lock = DayModes.activeLock(null, start, config, at(23, 7, 5).plusSeconds(30), TimeScale(testMode = true))
        assertEquals(start + 60_000, lock?.untilMillis)
    }

    @Test
    fun putAwayOnlyExtends() {
        val now = ms(at(22, 12))
        assertEquals(now + 900_000, DayModes.extendPutAway(null, now, Duration.ofMinutes(15)))
        assertEquals(now + 1_800_000, DayModes.extendPutAway(now + 900_000, now, Duration.ofMinutes(15)))
    }

    @Test
    fun studyWindowsRoundTripAndRun() {
        val windows = listOf(
            StudyWindow(9 * 60, 13 * 60, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            StudyWindow(18 * 60, 20 * 60, DayOfWeek.entries.toSet()),
        )
        val decoded = StudyWindow.decode(StudyWindow.encode(windows))
        assertEquals(windows, decoded)
        assertEquals(at(22, 13), decoded[0].activeUntil(at(22, 10)))
        assertNull(decoded[0].activeUntil(at(23, 10)))
        assertTrue(StudyWindow.decode("garbage;1-2").isEmpty())
    }

    @Test
    fun tooManyPickupsInAnHour() {
        val now = ms(at(22, 12))
        val recent = (1..10).map { now - it * 60_000L }
        assertTrue(PickupRules.tooMany(recent, now, 10))
        assertFalse(PickupRules.tooMany(recent, now, 11))
        assertFalse(PickupRules.tooMany(recent.map { it - 3_600_000L }, now, 1))
        assertFalse(PickupRules.tooMany(recent, now, 0))
    }
}
