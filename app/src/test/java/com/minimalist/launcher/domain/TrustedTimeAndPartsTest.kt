package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.blocking.TimeAnchor
import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.blocking.TrustedTime
import com.minimalist.launcher.domain.challenge.ChallengePlan
import com.minimalist.launcher.domain.parts.AppParts
import com.minimalist.launcher.domain.protection.EmergencyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TrustedTimeAndPartsTest {

    private val start = TimeAnchor(anchorWall = 1_000_000, anchorElapsed = 500, bootCount = 1)

    @Test
    fun followsRealTime() {
        val (now, _) = TrustedTime.read(start, wall = 1_060_000, elapsed = 60_500)
        assertEquals(1_060_000L, now)
    }

    @Test
    fun ignoresAClockSetForward() {
        val day = 86_400_000L
        val (now, anchor) = TrustedTime.read(start, wall = 1_060_000 + day, elapsed = 60_500)
        assertEquals(1_060_000L, now)
        assertEquals(start, anchor)
        // Later readings keep counting from real elapsed time, not the moved clock.
        assertEquals(1_120_000L, TrustedTime.read(anchor, wall = 1_120_000 + day, elapsed = 120_500).first)
    }

    @Test
    fun ignoresAClockSetBackToo() {
        assertEquals(1_060_000L, TrustedTime.read(start, wall = 0, elapsed = 60_500).first)
    }

    @Test
    fun followsSmallCorrections() {
        val (now, anchor) = TrustedTime.read(start, wall = 1_063_000, elapsed = 60_500)
        assertEquals(1_063_000L, now)
        assertEquals(1_063_000L, anchor.anchorWall)
    }

    @Test
    fun afterRebootTheClockCannotGoBack() {
        assertEquals(5_000L, TrustedTime.afterBoot(lastTrusted = 5_000, wall = 4_000, elapsed = 10, bootCount = 2).anchorWall)
        assertEquals(9_000L, TrustedTime.afterBoot(lastTrusted = 5_000, wall = 9_000, elapsed = 10, bootCount = 2).anchorWall)
    }

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(month: Int, day: Int) = ZonedDateTime.of(2026, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun threeFreeEmergenciesAMonth() {
        val now = at(10, 22)
        val used = EmergencyRules.usedThisPeriod(listOf(at(9, 30), at(10, 1), at(10, 5)), now, zone, TimeScale.REAL)
        assertEquals(2, used)
        assertEquals(1, EmergencyRules.freeLeft(used))
        assertEquals(0, EmergencyRules.freeLeft(4))
    }

    @Test
    fun extraEmergenciesCostAGrowingChallenge() {
        assertEquals(ChallengePlan(hard = 15, passage = true), EmergencyRules.extraPlan(3, TimeScale.REAL))
        assertEquals(ChallengePlan(hard = 19, passage = true), EmergencyRules.extraPlan(5, TimeScale.REAL))
        assertEquals(ChallengePlan(easy = 1), EmergencyRules.extraPlan(9, TimeScale(testMode = true)))
    }

    @Test
    fun partsShowOnlyWhenBlockedAndOnScreen() {
        val reels = setOf("com.instagram.android:id/clips_viewer_view_pager")
        assertEquals("instagram_reels", AppParts.showing("com.instagram.android", setOf("instagram_reels"), reels)?.key)
        assertNull(AppParts.showing("com.instagram.android", emptySet(), reels))
        assertNull(AppParts.showing("com.instagram.android", setOf("instagram_reels"), setOf("com.instagram.android:id/feed")))
        assertNull(AppParts.showing("com.google.android.youtube", setOf("instagram_reels"), reels))
    }

    @Test
    fun unblockingAPartWeakens() {
        assertTrue(AppParts.weakens(setOf("a", "b"), setOf("a")))
        assertFalse(AppParts.weakens(setOf("a"), setOf("a", "b")))
    }
}

class WeeklyReviewTest {

    private val zone = java.time.ZoneId.of("Asia/Kolkata")

    // 2026-10-25 is a Sunday.
    private fun at(day: Int, hour: Int) = java.time.ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone)

    @Test
    fun dueFromSundayEvening() {
        org.junit.Assert.assertEquals(at(18, 18), com.minimalist.launcher.domain.stats.WeeklyReview.dueSince(at(25, 17)))
        org.junit.Assert.assertEquals(at(25, 18), com.minimalist.launcher.domain.stats.WeeklyReview.dueSince(at(25, 18)))
        org.junit.Assert.assertEquals(at(25, 18), com.minimalist.launcher.domain.stats.WeeklyReview.dueSince(at(28, 9)))
    }

    @Test
    fun shownOncePerWeek() {
        val review = com.minimalist.launcher.domain.stats.WeeklyReview
        assertTrue(review.isDue(null, at(25, 19)))
        assertFalse(review.isDue(at(25, 19).toInstant().toEpochMilli(), at(26, 10)))
        assertTrue(review.isDue(at(25, 19).toInstant().toEpochMilli(), at(25, 19).plusWeeks(1)))
    }

    @Test
    fun weekOnWeekChange() {
        val review = com.minimalist.launcher.domain.stats.WeeklyReview
        assertEquals(-25, review.change(400, 300))
        assertEquals(50, review.change(200, 300))
        assertNull(review.change(0, 300))
    }
}
