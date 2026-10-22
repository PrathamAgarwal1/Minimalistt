package com.minimalist.launcher.data.protection

import com.minimalist.launcher.system.TrustedClock
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.db.UnlockLogEntity
import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.protection.EmergencyRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.ZoneId

/** What an early pass opens. */
sealed interface UnlockTarget {
    val key: String

    data class App(val packageName: String) : UnlockTarget {
        override val key get() = "app:$packageName"
    }

    data class Domain(val domain: String) : UnlockTarget {
        override val key get() = "domain:$domain"
    }

    /** Put away, morning or night mode. */
    data object Lock : UnlockTarget {
        override val key get() = "lock"
    }

    /** The emergency unlock: everything. */
    data object All : UnlockTarget {
        override val key get() = "all"
    }
}

/**
 * Temporary passes. A pass never changes the block underneath: when it ends, the block
 * carries on with its original end time.
 */
class UnlockRepository(database: AppDatabase, scope: CoroutineScope) {

    private val dao = database.protectionDao()

    /** Passes not yet over when the app started or since; filtered by time at each use. */
    private val passes: StateFlow<List<UnlockLogEntity>> = dao.observeActive(TrustedClock.nowMillis() - Duration.ofDays(1).toMillis())
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun hasPass(target: UnlockTarget, nowMillis: Long = TrustedClock.nowMillis()): Boolean =
        passes.value.any { (it.target == target.key || it.target == UnlockTarget.All.key) && it.untilMillis > nowMillis }

    fun emergencyUntil(nowMillis: Long = TrustedClock.nowMillis()): Long? =
        passes.value.firstOrNull { it.target == UnlockTarget.All.key && it.untilMillis > nowMillis }?.untilMillis

    fun passUntil(target: UnlockTarget, nowMillis: Long = TrustedClock.nowMillis()): Long? =
        passes.value.filter { it.target == target.key && it.untilMillis > nowMillis }.maxOfOrNull { it.untilMillis }

    /** Early unlocks of blocks and early mornings this week; night passes only count for their own night. */
    suspend fun earlyUnlocksPastWeek(nowMillis: Long = TrustedClock.nowMillis()): Int =
        dao.countSince(listOf(CHALLENGE, MORNING), nowMillis - Duration.ofDays(7).toMillis())

    /** A short pass that isn't an early unlock, e.g. WhatsApp for an urgent message. */
    suspend fun grantUrgentPass(packageName: String, length: Duration) {
        val now = TrustedClock.nowMillis()
        dao.insertUnlock(
            UnlockLogEntity(
                target = UnlockTarget.App(packageName).key,
                kind = URGENT,
                reason = null,
                problems = 0,
                startedAt = now,
                untilMillis = now + length.toMillis(),
            ),
        )
    }

    suspend fun nightPassesSince(sinceMillis: Long): Int = dao.countSince(listOf(NIGHT), sinceMillis)

    suspend fun grantChallengePass(target: UnlockTarget, reason: String, problems: Int, length: Duration, kind: String = CHALLENGE) {
        val now = TrustedClock.nowMillis()
        dao.insertUnlock(
            UnlockLogEntity(
                target = target.key,
                kind = kind,
                reason = reason.trim(),
                problems = problems,
                startedAt = now,
                untilMillis = now + length.toMillis(),
            ),
        )
    }

    /** Emergency unlocks used this calendar month (this hour in test mode). */
    suspend fun emergenciesUsed(scale: TimeScale): Int =
        EmergencyRules.usedThisPeriod(dao.emergencyTimes(), TrustedClock.nowMillis(), ZoneId.systemDefault(), scale)

    /**
     * Lifts everything for 15 minutes. Three a month are free; past that it takes [earned]
     * (a solved challenge). Returns false if it isn't allowed.
     */
    suspend fun useEmergency(scale: TimeScale, earned: Boolean = false): Boolean {
        if (!earned && EmergencyRules.freeLeft(emergenciesUsed(scale)) == 0) return false
        val now = TrustedClock.nowMillis()
        val length = scale.minutesAsSeconds(Duration.ofMinutes(EmergencyRules.MINUTES.toLong()))
        dao.insertUnlock(
            UnlockLogEntity(
                target = UnlockTarget.All.key,
                kind = EMERGENCY,
                reason = null,
                problems = 0,
                startedAt = now,
                untilMillis = now + length.toMillis(),
            ),
        )
        return true
    }

    suspend fun endAllPasses() = dao.endAllPasses(TrustedClock.nowMillis())

    companion object {
        const val CHALLENGE = "challenge"
        const val NIGHT = "night"
        const val MORNING = "morning"
        const val EMERGENCY = "emergency"
        const val URGENT = "urgent"
    }
}
