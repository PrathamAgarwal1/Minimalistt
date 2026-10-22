package com.minimalist.launcher.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimitLeftTest {

    private val min = 60_000L

    @Test
    fun noLimitNoHint() {
        assertNull(LimitLeft.hint(null, UsageToday(5 * min, 3)))
    }

    @Test
    fun minutesLeftRoundUp() {
        val limit = AppLimit("a", maxMinutes = 30, maxOpens = null)
        assertEquals("30m left", LimitLeft.hint(limit, UsageToday.NONE))
        assertEquals("18m left", LimitLeft.hint(limit, UsageToday(12 * min, 4)))
        assertEquals("1m left", LimitLeft.hint(limit, UsageToday(30 * min - 1_000, 4)))
        assertEquals("1h 30m left", LimitLeft.hint(AppLimit("a", 90, null), UsageToday.NONE))
    }

    @Test
    fun opensLeft() {
        assertEquals("1 open left", LimitLeft.hint(AppLimit("a", null, 3), UsageToday(0, 2)))
        assertEquals("20m left · 2 opens left", LimitLeft.hint(AppLimit("a", 30, 5), UsageToday(10 * min, 3)))
    }

    @Test
    fun eitherRunningOutIsUsedUp() {
        assertEquals("used up", LimitLeft.hint(AppLimit("a", 30, 5), UsageToday(30 * min, 1)))
        assertEquals("used up", LimitLeft.hint(AppLimit("a", 30, 5), UsageToday(0, 5)))
    }

    @Test
    fun testModeCountsMinutesAsSeconds() {
        assertEquals("1m left", LimitLeft.hint(AppLimit("a", 30, null), UsageToday.NONE, TimeScale(testMode = true)))
    }
}
