package com.minimalist.launcher.data.protection

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
    private val passes: StateFlow<List<UnlockLogEntity>> = dao.observeActive(System.currentTimeMillis() - Duration.ofDays(1).toMillis())
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun hasPass(target: UnlockTarget, nowMillis: Long = System.currentTimeMillis()): Boolean =
        passes.value.any { (it.target == target.key || it.target == UnlockTarget.All.key) && it.untilMillis > nowMillis }

    fun emergencyUntil(nowMillis: Long = System.currentTimeMillis()): Long? =
        passes.value.firstOrNull { it.target == UnlockTarget.All.key && it.untilMillis > nowMillis }?.untilMillis

    fun passUntil(target: UnlockTarget, nowMillis: Long = System.currentTimeMillis()): Long? =
        passes.value.filter { it.target == target.key && it.untilMillis > nowMillis }.maxOfOrNull { it.untilMillis }

    suspend fun earlyUnlocksPastWeek(nowMillis: Long = System.currentTimeMillis()): Int =
        dao.countSince(CHALLENGE, nowMillis - Duration.ofDays(7).toMillis())

    suspend fun grantChallengePass(target: UnlockTarget, reason: String, problems: Int, length: Duration) {
        val now = System.currentTimeMillis()
        dao.insertUnlock(
            UnlockLogEntity(
                target = target.key,
                kind = CHALLENGE,
                reason = reason.trim(),
                problems = problems,
                startedAt = now,
                untilMillis = now + length.toMillis(),
            ),
        )
    }

    suspend fun emergencyAvailable(scale: TimeScale): Boolean =
        EmergencyRules.available(dao.emergencyTimes(), System.currentTimeMillis(), ZoneId.systemDefault(), scale)

    /** Lifts everything for 15 minutes, once a calendar month. Returns false if already used. */
    suspend fun useEmergency(scale: TimeScale): Boolean {
        if (!emergencyAvailable(scale)) return false
        val now = System.currentTimeMillis()
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

    suspend fun endAllPasses() = dao.endAllPasses(System.currentTimeMillis())

    companion object {
        const val CHALLENGE = "challenge"
        const val EMERGENCY = "emergency"
    }
}
