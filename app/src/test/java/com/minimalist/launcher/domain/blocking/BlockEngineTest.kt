package com.minimalist.launcher.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class BlockEngineTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 10, 23, 15, 0, 0, 0, zone)
    private val nowMs = now.toInstant().toEpochMilli()
    private val midnight = ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = Duration.ofHours(1).toMillis()

    private fun evaluate(
        rules: BlockRules,
        usage: UsageToday = UsageToday.NONE,
        packageName: String = "com.instagram.android",
        protected: Set<String> = emptySet(),
        scale: TimeScale = TimeScale.REAL,
        atOpen: Boolean = true,
    ) = BlockEngine.evaluate(packageName, now, rules, usage, protected, scale, atOpen)

    private fun blocked(decision: BlockDecision): BlockDecision.Blocked {
        assertTrue("expected blocked, was $decision", decision is BlockDecision.Blocked)
        return decision as BlockDecision.Blocked
    }

    @Test
    fun noRulesAllows() {
        assertEquals(BlockDecision.Allowed, evaluate(BlockRules.NONE))
    }

    @Test
    fun activeTimedBlockBlocksUntilItsEnd() {
        val rules = BlockRules.NONE.copy(blocks = mapOf("com.instagram.android" to nowMs + 2 * hour))
        val decision = blocked(evaluate(rules))
        assertEquals(nowMs + 2 * hour, decision.untilMillis)
        assertEquals(BlockReason.Timed(nowMs + 2 * hour), decision.primary)
    }

    @Test
    fun expiredBlockAllows() {
        val rules = BlockRules.NONE.copy(blocks = mapOf("com.instagram.android" to nowMs))
        assertEquals(BlockDecision.Allowed, evaluate(rules))
    }

    @Test
    fun blocksAreByPackageOnly() {
        val rules = BlockRules.NONE.copy(blocks = mapOf("com.instagram.android" to nowMs + hour))
        assertEquals(BlockDecision.Allowed, evaluate(rules, packageName = "com.whatsapp"))
    }

    @Test
    fun protectedAppsAreNeverBlocked() {
        val rules = BlockRules(
            blocks = mapOf("com.android.dialer" to nowMs + hour),
            schedules = listOf(Schedule(1, "All", 0, 0, DayOfWeek.entries.toSet(), setOf("com.android.dialer"), true)),
            limits = mapOf("com.android.dialer" to AppLimit("com.android.dialer", 0, 0)),
        )
        val decision = evaluate(rules, packageName = "com.android.dialer", protected = setOf("com.android.dialer"))
        assertEquals(BlockDecision.Allowed, decision)
    }

    @Test
    fun runningScheduleBlocksUntilItEnds() {
        val schedule = Schedule(1, "Study", 14 * 60, 17 * 60, setOf(DayOfWeek.FRIDAY), setOf("com.instagram.android"), true)
        val decision = blocked(evaluate(BlockRules.NONE.copy(schedules = listOf(schedule))))
        val fivePm = ZonedDateTime.of(2026, 10, 23, 17, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(BlockReason.Scheduled("Study", fivePm), decision.primary)
    }

    @Test
    fun scheduleForOtherAppsAllows() {
        val schedule = Schedule(1, "Study", 14 * 60, 17 * 60, setOf(DayOfWeek.FRIDAY), setOf("com.whatsapp"), true)
        assertEquals(BlockDecision.Allowed, evaluate(BlockRules.NONE.copy(schedules = listOf(schedule))))
    }

    @Test
    fun timeLimitBlocksOnceUsedUpUntilMidnight() {
        val rules = BlockRules.NONE.copy(limits = mapOf("com.instagram.android" to AppLimit("com.instagram.android", 30, null)))
        assertEquals(BlockDecision.Allowed, evaluate(rules, UsageToday(29 * 60_000L, 3)))
        val decision = blocked(evaluate(rules, UsageToday(30 * 60_000L, 3), atOpen = false))
        assertEquals(BlockReason.TimeLimit(30, midnight), decision.primary)
    }

    @Test
    fun opensLimitOnlyStopsNewOpens() {
        val rules = BlockRules.NONE.copy(limits = mapOf("com.instagram.android" to AppLimit("com.instagram.android", null, 5)))
        assertEquals(BlockDecision.Allowed, evaluate(rules, UsageToday(0, 4)))
        assertEquals(BlockReason.OpenLimit(5, midnight), blocked(evaluate(rules, UsageToday(0, 5))).primary)
        assertEquals(BlockDecision.Allowed, evaluate(rules, UsageToday(0, 5), atOpen = false))
    }

    @Test
    fun testModeTurnsLimitMinutesIntoSeconds() {
        val rules = BlockRules.NONE.copy(limits = mapOf("com.instagram.android" to AppLimit("com.instagram.android", 30, null)))
        val test = TimeScale(testMode = true)
        assertEquals(BlockDecision.Allowed, evaluate(rules, UsageToday(29_000, 0), scale = test))
        blocked(evaluate(rules, UsageToday(30_000, 0), scale = test))
    }

    @Test
    fun longestReasonWins() {
        val rules = BlockRules(
            blocks = mapOf("com.instagram.android" to nowMs + 48 * hour),
            schedules = emptyList(),
            limits = mapOf("com.instagram.android" to AppLimit("com.instagram.android", 10, null)),
        )
        val decision = blocked(evaluate(rules, UsageToday(11 * 60_000L, 0)))
        assertEquals(2, decision.reasons.size)
        assertEquals(nowMs + 48 * hour, decision.untilMillis)
        assertTrue(decision.primary is BlockReason.Timed)
    }

    @Test
    fun newBlocksAddToTimeLeft() {
        val day = Duration.ofDays(1)
        assertEquals(nowMs + day.toMillis(), BlockEngine.extendedUntil(null, nowMs, day))
        assertEquals(nowMs + day.toMillis(), BlockEngine.extendedUntil(nowMs - hour, nowMs, day))
        assertEquals(nowMs + 3 * hour + day.toMillis(), BlockEngine.extendedUntil(nowMs + 3 * hour, nowMs, day))
    }
}

class BlockEngineLockTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 10, 23, 15, 0, 0, 0, zone)
    private val later = now.plusHours(1).toInstant().toEpochMilli()

    private fun evaluate(packageName: String, lock: com.minimalist.launcher.domain.modes.ActiveLock?, allowed: Set<String> = emptySet(), browsers: Set<String> = emptySet()) =
        BlockEngine.evaluate(
            packageName, now, BlockRules.NONE, UsageToday.NONE, setOf("com.android.dialer"), TimeScale.REAL, true,
            lock = lock, lockAllowedPackages = allowed, otherBrowsers = browsers, allowedBrowserLabel = "Chrome",
        )

    @Test
    fun lockBlocksEverythingButEssentials() {
        val lock = com.minimalist.launcher.domain.modes.ActiveLock(com.minimalist.launcher.domain.modes.LockKind.PutAway, later)
        assertTrue(evaluate("com.instagram.android", lock) is BlockDecision.Blocked)
        assertEquals(BlockDecision.Allowed, evaluate("net.one97.paytm", lock, allowed = setOf("net.one97.paytm")))
        assertEquals(BlockDecision.Allowed, evaluate("com.android.dialer", lock))
    }

    @Test
    fun otherBrowsersAreBlockedWithNoEnd() {
        val decision = evaluate("org.mozilla.firefox", null, browsers = setOf("org.mozilla.firefox"))
        assertEquals(BlockReason.OtherBrowser("Chrome"), (decision as BlockDecision.Blocked).primary)
        assertEquals(Long.MAX_VALUE, decision.untilMillis)
        assertEquals(BlockDecision.Allowed, evaluate("com.android.chrome", null, browsers = setOf("org.mozilla.firefox")))
    }
}

class BlockEngineEarnedTest {

    private val now = ZonedDateTime.of(2026, 10, 23, 15, 0, 0, 0, ZoneId.of("Asia/Kolkata"))

    private fun evaluate(left: Long?) =
        BlockEngine.evaluate("com.netflix.mediaclient", now, BlockRules.NONE, UsageToday.NONE, emptySet(), TimeScale.REAL, true, earnedLeftMillis = left)

    @Test
    fun earnedAppsOpenOnlyWithTimeLeft() {
        assertEquals(BlockDecision.Allowed, evaluate(null))
        assertEquals(BlockDecision.Allowed, evaluate(60_000))
        assertTrue((evaluate(0) as BlockDecision.Blocked).primary is BlockReason.EarnedUp)
        assertTrue(evaluate(-5) is BlockDecision.Blocked)
    }
}
