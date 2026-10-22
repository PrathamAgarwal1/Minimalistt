package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.friction.ScrollCheck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollCheckTest {

    private val min = 60_000L

    @Test
    fun dueAfterTheChosenMinutes() {
        assertFalse(ScrollCheck.isDue(0, 20 * min - 1, 20))
        assertTrue(ScrollCheck.isDue(0, 20 * min, 20))
        assertFalse(ScrollCheck.isDue(0, 99 * min, 0))
    }

    @Test
    fun testModeCountsMinutesAsSeconds() {
        assertTrue(ScrollCheck.isDue(0, 20_000, 20, TimeScale(testMode = true)))
        assertFalse(ScrollCheck.isDue(0, 19_000, 20, TimeScale(testMode = true)))
    }

    @Test
    fun offOrLessOftenWeakens() {
        assertTrue(ScrollCheck.weakens(20, 0))
        assertTrue(ScrollCheck.weakens(20, 30))
        assertFalse(ScrollCheck.weakens(20, 10))
        assertFalse(ScrollCheck.weakens(0, 30))
    }
}
