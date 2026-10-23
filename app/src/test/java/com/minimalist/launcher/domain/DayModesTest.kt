package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.challenge.ChallengePlan
import com.minimalist.launcher.domain.challenge.ChallengeRules
import com.minimalist.launcher.domain.challenge.NightRules
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
        nightEnabled = true,
        bedWeekday = 23 * 60,
        bedWeekend = 30, // 00:30
        openWeekday = 8 * 60,
        openWeekend = 9 * 60,
        windDownMinutes = 60,
        reminderMinutes = 15,
    )

    @Test
    fun weekdayNightRunsFromBedtimeToOpening() {
        assertNull(DayModes.nightUntil(config, at(22, 22, 59)))
        assertEquals(at(23, 8), DayModes.nightUntil(config, at(22, 23)))
        assertEquals(at(23, 8), DayModes.nightUntil(config, at(23, 7, 59)))
        assertNull(DayModes.nightUntil(config, at(23, 8)))
    }

    @Test
    fun weekendBedtimeAfterMidnightAndLaterOpening() {
        // Friday night: bedtime 00:30 on Saturday, Saturday opens 09:00.
        assertNull(DayModes.nightUntil(config, at(24, 0, 15)))
        assertEquals(at(24, 9), DayModes.nightUntil(config, at(24, 0, 30)))
        assertEquals(at(24, 9), DayModes.nightUntil(config, at(24, 8, 59)))
    }

    @Test
    fun sundayNightUsesWeekdayBedtimeAndMondayOpening() {
        assertEquals(at(26, 8), DayModes.nightUntil(config, at(25, 23, 10)))
    }

    @Test
    fun nightOffMeansNoNight() {
        assertNull(DayModes.nightUntil(config.copy(nightEnabled = false), at(22, 23, 30)))
    }

    @Test
    fun windDownIsTheHourBeforeBedtime() {
        assertNull(DayModes.windDownUntil(config, at(22, 21, 59)))
        assertEquals(at(22, 23), DayModes.windDownUntil(config, at(22, 22)))
        assertEquals(at(22, 23), DayModes.windDownUntil(config, at(22, 22, 59)))
        assertNull(DayModes.windDownUntil(config, at(22, 23)))
        // Friday's bedtime is 00:30 on Saturday, so wind-down starts at 23:30 on Friday.
        assertEquals(at(24, 0, 30), DayModes.windDownUntil(config, at(23, 23, 45)))
        assertNull(DayModes.windDownUntil(config.copy(windDownMinutes = 0), at(22, 22, 30)))
    }

    @Test
    fun reminderBeforeBedtime() {
        assertNull(DayModes.reminderFor(config, at(22, 22, 44)))
        assertEquals(at(22, 23), DayModes.reminderFor(config, at(22, 22, 45)))
        assertNull(DayModes.reminderFor(config.copy(reminderMinutes = 0), at(22, 22, 50)))
    }

    @Test
    fun putAwayBeatsNight() {
        val nightTime = at(22, 23, 30)
        val putAwayEnd = ms(nightTime.plusMinutes(30))
        assertEquals(ActiveLock(LockKind.PutAway, putAwayEnd), DayModes.activeLock(putAwayEnd, null, null, config, nightTime))
        assertEquals(ActiveLock(LockKind.Night, ms(at(23, 8))), DayModes.activeLock(null, null, null, config, nightTime))
    }

    @Test
    fun aNightOpenedEarlyIsOver() {
        val opens = ms(at(23, 8))
        assertNull(DayModes.activeLock(null, null, opens, config, at(23, 6, 30)))
        // Releasing last night doesn't release tonight.
        assertEquals(LockKind.Night, DayModes.activeLock(null, null, opens, config, at(24, 1, 0))?.kind)
    }

    @Test
    fun expiredPutAwayIsNoLock() {
        assertNull(DayModes.activeLock(ms(at(22, 12)), null, null, config, at(22, 12, 1)))
    }

    @Test
    fun putAwayOnlyExtends() {
        val now = ms(at(22, 12))
        assertEquals(now + 900_000, DayModes.extendPutAway(null, now, Duration.ofMinutes(15)))
        assertEquals(now + 1_800_000, DayModes.extendPutAway(now + 900_000, now, Duration.ofMinutes(15)))
    }

    private val minute = 60_000L
    private val hour = 60 * minute

   @Test
    fun nightCurveGrowsWithHowLateItIs() {
        val longUntilOpening = 8 * hour
        fun night(sinceBedtime: Long, passes: Int = 0) = NightRules.challenge(sinceBedtime, longUntilOpening, passes, 0)
        assertEquals(ChallengePlan(easy = 3), night(10 * minute).plan)
        assertEquals(15, night(10 * minute).passMinutes)
        assertEquals(ChallengePlan(medium = 5), night(30 * minute).plan)
        assertEquals(10, night(45 * minute).passMinutes)
        assertEquals(ChallengePlan(medium = 5, hard = 3), night(90 * minute).plan)
        assertEquals(10, night(90 * minute).passMinutes)
        assertEquals(ChallengePlan(hard = 12), night(150 * minute).plan)
        assertEquals(5, night(150 * minute).passMinutes)
        assertEquals(ChallengePlan(hard = 15, passage = true), night(4 * hour).plan)
        assertFalse(night(4 * hour).opensMorning)
    }

   @Test
    fun passesTonightAddTwoEach() {
        assertEquals(ChallengePlan(easy = 7), NightRules.challenge(10 * minute, 8 * hour, 2, 0).plan)
    }

    @Test
    fun lastTwoHoursHandOverToTheMorning() {
        // 6 h after bedtime with 90 min to opening: the unlock table, by time until opening.
        val morning = NightRules.challenge(6 * hour, 90 * minute, passesTonight = 3, earlyUnlocksPastWeek = 1)
        assertTrue(morning.opensMorning)
        assertEquals(ChallengeRules.forBlock(90 * minute, 1), morning.plan)
        assertEquals(ChallengePlan(easy = 3), NightRules.challenge(7 * hour, 20 * minute, 0, 0).plan)
        assertFalse(NightRules.challenge(6 * hour, 2 * hour, 0, 0).opensMorning)
    }

    @Test
    fun testModeNightIsOneEasyProblemInSeconds() {
        val test = TimeScale(testMode = true)
        val night = NightRules.challenge(45_000, 10 * hour, 3, 0, test)
        assertEquals(ChallengePlan(easy = 1), night.plan)
        assertEquals(10, night.passMinutes)
        assertTrue(NightRules.challenge(45_000, 100_000, 0, 0, test).opensMorning)
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
