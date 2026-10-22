package com.minimalist.launcher.data.friction

import com.minimalist.launcher.system.TrustedClock
import android.content.Context
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.db.FrictionLogEntity
import com.minimalist.launcher.domain.friction.RatedPull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/** A friction app opened for a chosen length of time. */
data class FrictionSession(val logId: Long, val packageName: String, val endsAtMillis: Long)

class FrictionRepository(context: Context, database: AppDatabase, scope: CoroutineScope) {

    private val dao = database.frictionDao()

    /** Kept on disk too, so ColorOS closing the app mid-session doesn't ask for friction again. */
    private val saved = context.getSharedPreferences("friction_sessions", Context.MODE_PRIVATE)
    private val sessions = ConcurrentHashMap<String, FrictionSession>().apply {
        saved.all.forEach { (packageName, value) ->
            val parts = (value as? String)?.split(',') ?: return@forEach
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
            val ends = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            put(packageName, FrictionSession(id, packageName, ends))
        }
    }

    private fun persist() {
        saved.edit().clear().apply {
            sessions.values.forEach { putString(it.packageName, "${it.logId},${it.endsAtMillis}") }
        }.apply()
    }

    val apps: StateFlow<Set<String>> = dao.observeApps()
        .map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** True when [packageName] is slowed down and has no session running. */
    fun needsFriction(packageName: String, nowMillis: Long = TrustedClock.nowMillis()): Boolean =
        packageName in apps.value && activeSession(packageName, nowMillis) == null

    fun activeSession(packageName: String, nowMillis: Long = TrustedClock.nowMillis()): FrictionSession? =
        sessions[packageName]?.takeIf { it.endsAtMillis > nowMillis }

    fun sessionFor(packageName: String): FrictionSession? = sessions[packageName]

    fun allSessions(): List<FrictionSession> = sessions.values.toList()

    suspend fun addApp(packageName: String) = dao.addApp(packageName)

    suspend fun removeApp(packageName: String) = dao.removeApp(packageName)

    suspend fun opensToday(packageName: String): Int = dao.opensSince(packageName, startOfToday())

    suspend fun startSession(packageName: String, urge: Int, reason: String, minutes: Int, lengthMillis: Long): FrictionSession {
        val now = TrustedClock.nowMillis()
        val id = dao.insertLog(
            FrictionLogEntity(
                packageName = packageName,
                startedAt = now,
                urge = urge,
                reason = reason.trim(),
                outcome = OPENED,
                sessionMinutes = minutes,
                worth = null,
                endedAt = null,
            ),
        )
        return FrictionSession(id, packageName, now + lengthMillis).also {
            sessions[packageName] = it
            persist()
        }
    }

    /** Ends and forgets the session; returns it so its worth can be asked. */
    suspend fun endSession(packageName: String): FrictionSession? {
        val session = sessions.remove(packageName) ?: return null
        persist()
        dao.endSession(session.logId, TrustedClock.nowMillis())
        return session
    }

    suspend fun recordResisted(packageName: String, urge: Int?, reason: String?) {
        dao.insertLog(
            FrictionLogEntity(
                packageName = packageName,
                startedAt = TrustedClock.nowMillis(),
                urge = urge,
                reason = reason?.trim()?.ifEmpty { null },
                outcome = RESISTED,
                sessionMinutes = null,
                worth = null,
                endedAt = null,
            ),
        )
    }

    /** An answer to "Still worth it?" after a long stretch in one app. */
    suspend fun recordScrollCheck(packageName: String, worth: Int?, minutes: Int) {
        val now = TrustedClock.nowMillis()
        dao.insertLog(
            FrictionLogEntity(
                packageName = packageName,
                startedAt = now,
                urge = null,
                reason = null,
                outcome = CHECKED,
                sessionMinutes = minutes,
                worth = worth?.coerceIn(1, 5),
                endedAt = now,
            ),
        )
    }

    suspend fun rateWorth(logId: Long, worth: Int) = dao.setWorth(logId, worth.coerceIn(1, 5))

    fun pullsSince(sinceMillis: Long): Flow<List<RatedPull>> = dao.observeSince(sinceMillis).map { log ->
        log.map { RatedPull(it.packageName, it.urge, it.worth, opened = it.outcome != RESISTED) }
    }

    fun resistedSince(sinceMillis: Long): Flow<Int> = dao.observeResistedSince(sinceMillis)

    suspend fun clear() {
        sessions.clear()
        persist()
        dao.clearApps()
    }

    companion object {
        const val OPENED = "opened"
        const val RESISTED = "resisted"
        /** A "Still worth it?" answer: time spent in an app, rated. */
        const val CHECKED = "checked"

        fun startOfToday(): Long = TrustedClock.today().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        fun daysAgo(days: Long): Long =
            TrustedClock.today().minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
