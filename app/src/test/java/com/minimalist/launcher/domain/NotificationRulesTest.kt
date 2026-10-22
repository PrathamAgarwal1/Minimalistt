package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.notifications.BatchTimes
import com.minimalist.launcher.domain.notifications.NotificationRouting
import com.minimalist.launcher.domain.notifications.Route
import com.minimalist.launcher.domain.notifications.Vip
import com.minimalist.launcher.domain.notifications.VipMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationRulesTest {

    private fun route(
        protected: Boolean = false,
        urgent: Boolean = false,
        vip: Boolean = false,
        essential: Boolean = false,
        blocked: Boolean = false,
        lock: Boolean = false,
        batching: Boolean = false,
    ) = NotificationRouting.route(protected, urgent, vip, essential, blocked, lock, batching)

    @Test
    fun callsAndSmsAlwaysShow() {
        assertEquals(Route.Show, route(protected = true, blocked = true, lock = true, batching = true))
        assertEquals(Route.Show, route(urgent = true, lock = true))
    }

    @Test
    fun vipsBreakThrough() {
        assertEquals(Route.Show, route(vip = true, blocked = true, lock = true, batching = true))
    }

    @Test
    fun blockedAppsAreHeldEvenIfEssential() {
        assertEquals(Route.HoldBlocked, route(blocked = true, essential = true))
    }

    @Test
    fun modesHoldAllButEssentials() {
        assertEquals(Route.HoldMode, route(lock = true, batching = true))
        assertEquals(Route.Show, route(lock = true, essential = true))
    }

    @Test
    fun batchingHoldsTheRest() {
        assertEquals(Route.HoldBatch, route(batching = true))
        assertEquals(Route.Show, route())
    }

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)
    private val times = listOf(13 * 60, 20 * 60)

    @Test
    fun batchDueOncePerTime() {
        val lastAtOne = at(22, 13, 0, ).toInstant().toEpochMilli()
        assertFalse(BatchTimes.due(lastAtOne, at(22, 19, 59), times))
        assertTrue(BatchTimes.due(lastAtOne, at(22, 20, 0), times))
        assertFalse(BatchTimes.due(at(22, 20, 1).toInstant().toEpochMilli(), at(22, 23), times))
        // Overnight: yesterday's 20:00 counts until today's 13:00.
        assertTrue(BatchTimes.due(at(21, 19).toInstant().toEpochMilli(), at(22, 9), times))
        assertFalse(BatchTimes.due(at(22, 9).toInstant().toEpochMilli(), at(22, 10), times))
        assertFalse(BatchTimes.due(null, at(22, 10), emptyList()))
    }

    private val vips = listOf(Vip(1, "Mum", "+91 98765 43210"), Vip(2, "Riya Sharma", "919812345678"))

    @Test
    fun vipByNameOrNumber() {
        assertEquals(1L, VipMatcher.match("mum", vips)?.id)
        assertEquals(2L, VipMatcher.match(" Riya  Sharma ", vips)?.id)
        assertEquals(1L, VipMatcher.match("+91 98765 43210", vips)?.id)
        assertNull(VipMatcher.match("Mummy's group", vips))
        assertNull(VipMatcher.match(null, vips))
    }

    @Test
    fun phoneDigitsForWaMe() {
        assertEquals("919876543210", VipMatcher.phoneDigits("+91 98765-43210"))
        assertEquals("919876543210", VipMatcher.phoneDigits("0091 9876543210"))
    }
}
