package com.minimalist.launcher.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class TimeScaleTest {

    private val test = TimeScale(testMode = true)

    @Test
    fun realScaleChangesNothing() {
        assertEquals(Duration.ofHours(5), TimeScale.REAL.block(Duration.ofHours(5)))
        assertEquals(Duration.ofMinutes(30), TimeScale.REAL.dailyLimit(Duration.ofMinutes(30)))
        assertEquals(Duration.ofHours(24), TimeScale.REAL.cooldown)
    }

    @Test
    fun testModeHoursBecomeSecondsAndMonthsBecomeMinutes() {
        assertEquals(Duration.ofSeconds(1), test.block(BlockUnit.Hours.duration))
        assertEquals(Duration.ofSeconds(24), test.block(BlockUnit.Days.duration))
        assertEquals(Duration.ofMinutes(12), test.block(BlockUnit.Months.duration))
    }

    @Test
    fun testModeShrinksLimitsAndCooldown() {
        assertEquals(Duration.ofSeconds(30), test.dailyLimit(Duration.ofMinutes(30)))
        assertEquals(Duration.ofMinutes(1), test.cooldown)
    }

    @Test
    fun unitLabels() {
        assertEquals("1 hour", BlockUnit.Hours.label(1))
        assertEquals("3 weeks", BlockUnit.Weeks.label(3))
    }
}
