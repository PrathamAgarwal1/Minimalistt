package com.minimalist.launcher.data.blocking

import com.minimalist.launcher.system.TrustedClock
import androidx.room.withTransaction
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.db.DailyUsageEntity
import com.minimalist.launcher.domain.blocking.UsageToday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Counts foreground time and opens per app per day. The accessibility service reports what is
 * in front; everything else only reads.
 */
class UsageTracker(private val database: AppDatabase) {

    private val dao = database.usageDao()
    private val lock = Mutex()

    @Volatile
    var currentPackage: String? = null
        private set
    private var sessionStart = 0L

    /** [packageName] is now in front (null: screen off). Closes the previous session. */
    suspend fun switchTo(packageName: String?, nowMillis: Long = TrustedClock.nowMillis()) = lock.withLock {
        flushLocked(nowMillis)
        currentPackage = packageName
        sessionStart = nowMillis
    }

    /** Writes the running session so far, so limits and stats stay current during long sessions. */
    suspend fun flush(nowMillis: Long = TrustedClock.nowMillis()) = lock.withLock { flushLocked(nowMillis) }

    suspend fun recordOpen(packageName: String) = add(packageName, opens = 1)

    suspend fun recordBlockedAttempt(packageName: String) = add(packageName, blockedAttempts = 1)

    /** Today's totals for [packageName], including the unsaved part of a running session. */
    suspend fun usageToday(packageName: String, nowMillis: Long = TrustedClock.nowMillis()): UsageToday = lock.withLock {
        val stored = dao.get(today(), packageName)
        val running = if (currentPackage == packageName) (nowMillis - sessionStart).coerceAtLeast(0) else 0
        UsageToday(foregroundMs = (stored?.foregroundMs ?: 0) + running, opens = stored?.opens ?: 0)
    }

    fun totalForegroundToday(excludedPackage: String, day: Long = today()): Flow<Long> =
        dao.observeTotalForeground(day, excludedPackage)

    private suspend fun flushLocked(nowMillis: Long) {
        val packageName = currentPackage ?: return
        val elapsed = nowMillis - sessionStart
        sessionStart = nowMillis
        if (elapsed > 0) add(packageName, foregroundMs = elapsed)
    }

    private suspend fun add(packageName: String, foregroundMs: Long = 0, opens: Int = 0, blockedAttempts: Int = 0) {
        val day = today()
        database.withTransaction {
            val current = dao.get(day, packageName) ?: DailyUsageEntity(day, packageName)
            dao.upsert(
                current.copy(
                    foregroundMs = current.foregroundMs + foregroundMs,
                    opens = current.opens + opens,
                    blockedAttempts = current.blockedAttempts + blockedAttempts,
                ),
            )
        }
    }

    private fun today() = TrustedClock.today().toEpochDay()
}
