package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.friction.FrictionRules
import com.minimalist.launcher.domain.friction.RatedPull
import com.minimalist.launcher.domain.friction.WantingLiking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrictionRulesTest {

    @Test
    fun countdownDoublesWithEachOpen() {
        assertEquals(listOf(5, 10, 20, 40, 80, 160, 320, 600), (0..7).map { FrictionRules.countdownSeconds(it) })
        assertEquals(600, FrictionRules.countdownSeconds(100))
    }

    @Test
    fun customStartAndWindDown() {
        assertEquals(15, FrictionRules.countdownSeconds(0, baseSeconds = 15))
        assertEquals(30, FrictionRules.countdownSeconds(1, baseSeconds = 15))
        assertEquals(10, FrictionRules.countdownSeconds(0, windDown = true))
        assertEquals(80, FrictionRules.countdownSeconds(3, windDown = true))
    }

    @Test
    fun testModeShortensCountdown() {
        val test = TimeScale(testMode = true)
        assertEquals(1, FrictionRules.countdownSeconds(0, test))
        assertEquals(8, FrictionRules.countdownSeconds(3, test))
    }

    @Test
    fun reasonNeedsAFewLetters() {
        assertFalse(FrictionRules.isValidReason("  a "))
        assertTrue(FrictionRules.isValidReason("reply to mum"))
    }

    @Test
    fun highWantingLowLikingIsFlaggedFirst() {
        val pulls = listOf(
            RatedPull("insta", urge = 5, worth = 1, opened = true),
            RatedPull("insta", urge = 4, worth = 2, opened = true),
            RatedPull("insta", urge = 4, worth = null, opened = false),
            RatedPull("maps", urge = 2, worth = 5, opened = true),
            RatedPull("maps", urge = 1, worth = 4, opened = true),
        )
        val summary = WantingLiking.summarize(pulls)
        assertEquals("insta", summary.first().packageName)
        assertTrue(summary.first().wantingOutpacesLiking)
        assertEquals(2, summary.first().opens)
        assertEquals(1, summary.first().resisted)
        assertEquals(1.5, summary.first().averageWorth!!, 0.001)
        assertFalse(summary.last().wantingOutpacesLiking)
    }

    @Test
    fun oneOpenIsNotEnoughToFlag() {
        val summary = WantingLiking.summarize(listOf(RatedPull("insta", 5, 1, opened = true)))
        assertFalse(summary.single().wantingOutpacesLiking)
    }

    @Test
    fun unratedAppsHaveNoAverages() {
        val feeling = WantingLiking.summarize(listOf(RatedPull("x", null, null, opened = true))).single()
        assertNull(feeling.averageUrge)
        assertNull(feeling.averageWorth)
    }
}
