package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.blocking.AppLimit
import com.minimalist.launcher.domain.blocking.Schedule
import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.modes.DayModeConfig
import com.minimalist.launcher.domain.modes.StudyWindow
import com.minimalist.launcher.domain.protection.Protection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ProtectionTest {


    @Test
    fun limits() {
        val old = AppLimit("x", 30, 5)
        assertFalse(Protection.limitWeakens(null, old))
        assertFalse(Protection.limitWeakens(old, AppLimit("x", 15, 3)))
        assertTrue(Protection.limitWeakens(old, AppLimit("x", 45, 5)))
        assertTrue(Protection.limitWeakens(old, AppLimit("x", 30, null)))
        assertTrue(Protection.limitWeakens(old, null))
        assertFalse(Protection.limitWeakens(AppLimit("x", null, 5), AppLimit("x", 60, 5)))
    }

    private val weekdays = DayOfWeek.entries.take(5).toSet()
    private fun schedule(start: Int, end: Int, days: Set<DayOfWeek> = weekdays, apps: Set<String> = setOf("a", "b"), enabled: Boolean = true) =
        Schedule(1, "S", start * 60, end * 60, days, apps, enabled)

    @Test
    fun schedules() {
        val night = schedule(22, 8)
        assertFalse(Protection.scheduleWeakens(null, night))
        assertTrue(Protection.scheduleWeakens(night, null))
        assertTrue(Protection.scheduleWeakens(night, night.copy(enabled = false)))
        assertFalse(Protection.scheduleWeakens(night.copy(enabled = false), night))
        assertTrue(Protection.scheduleWeakens(night, night.copy(packages = setOf("a"))))
        assertFalse(Protection.scheduleWeakens(night, night.copy(packages = setOf("a", "b", "c"))))
        assertTrue(Protection.scheduleWeakens(night, night.copy(days = setOf(DayOfWeek.MONDAY))))
        assertFalse(Protection.scheduleWeakens(night, night.copy(days = DayOfWeek.entries.toSet())))
        assertFalse(Protection.scheduleWeakens(night, schedule(21, 9)))
        assertTrue(Protection.scheduleWeakens(night, schedule(23, 8)))
        assertTrue(Protection.scheduleWeakens(night, schedule(22, 7)))
        assertFalse(Protection.scheduleWeakens(night, schedule(0, 0, days = DayOfWeek.entries.toSet())))
        // Friday night runs into Saturday, which "all day on weekdays" does not cover.
        assertTrue(Protection.scheduleWeakens(night, schedule(0, 0)))
    }

    @Test
    fun dayModes() {
        val old = DayModeConfig(nightEnabled = true, bedWeekday = 23 * 60, bedWeekend = 23 * 60, openWeekday = 8 * 60, openWeekend = 9 * 60)
        assertTrue(Protection.dayModesWeaken(old, old.copy(nightEnabled = false)))
        assertTrue(Protection.dayModesWeaken(old, old.copy(bedWeekday = 30))) // 00:30 is later
        assertFalse(Protection.dayModesWeaken(old, old.copy(bedWeekday = 22 * 60)))
        assertTrue(Protection.dayModesWeaken(old, old.copy(openWeekday = 7 * 60)))
        assertFalse(Protection.dayModesWeaken(old, old.copy(openWeekend = 10 * 60)))
        assertTrue(Protection.dayModesWeaken(old, old.copy(windDownMinutes = 30)))
        assertFalse(Protection.dayModesWeaken(old, old.copy(windDownMinutes = 90)))
        assertTrue(Protection.dayModesWeaken(old, old.copy(grayscale = false)))
        assertTrue(Protection.dayModesWeaken(old, old.copy(redTint = false)))
        assertFalse(Protection.dayModesWeaken(old, old.copy(reminderMinutes = 0)))
        assertFalse(Protection.dayModesWeaken(old.copy(nightEnabled = false), old))
        assertTrue(Protection.frictionBaseWeakens(10, 5))
        assertFalse(Protection.frictionBaseWeakens(5, 10))
    }

    @Test
    fun automaticPutAwayAndAllowances() {
        assertTrue(Protection.unplugWeakens(30, 0))
        assertTrue(Protection.unplugWeakens(30, 15))
        assertFalse(Protection.unplugWeakens(0, 15))
        assertTrue(Protection.pickupsWeaken(10, 30, 20, 30))
        assertTrue(Protection.pickupsWeaken(10, 30, 10, 15))
        assertFalse(Protection.pickupsWeaken(20, 30, 10, 60))
        val window = StudyWindow(540, 780, setOf(DayOfWeek.MONDAY))
        assertTrue(Protection.studyWindowsWeaken(listOf(window), emptyList()))
        assertFalse(Protection.studyWindowsWeaken(emptyList(), listOf(window)))
        assertTrue(Protection.allowedAppsWeaken(setOf("a"), setOf("a", "b")))
        assertFalse(Protection.allowedAppsWeaken(setOf("a", "b"), setOf("a")))
        assertTrue(Protection.modeAppWeakens(null, "spotify"))
        assertFalse(Protection.modeAppWeakens("spotify", null))
        assertTrue(Protection.browserWeakens("chrome", "firefox"))
        assertTrue(Protection.browserWeakens("chrome", null))
        assertFalse(Protection.browserWeakens(null, "chrome"))
        assertTrue(Protection.batchingWeakens(true, listOf(780), false, listOf(780)))
        assertTrue(Protection.batchingWeakens(true, listOf(780), true, listOf(780, 600)))
        assertFalse(Protection.batchingWeakens(true, listOf(780, 1200), true, listOf(780)))
        assertTrue(Protection.testModeWeakens(true))
        assertFalse(Protection.testModeWeakens(false))
    }
}
