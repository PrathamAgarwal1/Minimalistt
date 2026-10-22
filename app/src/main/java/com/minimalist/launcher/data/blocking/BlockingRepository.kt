package com.minimalist.launcher.data.blocking

import androidx.room.withTransaction
import com.minimalist.launcher.data.db.AppBlockEntity
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.db.AppLimitEntity
import com.minimalist.launcher.data.db.ScheduleEntity
import com.minimalist.launcher.domain.blocking.AppLimit
import com.minimalist.launcher.domain.blocking.BlockEngine
import com.minimalist.launcher.domain.blocking.BlockRules
import com.minimalist.launcher.domain.blocking.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

class BlockingRepository(
    private val database: AppDatabase,
    scope: CoroutineScope,
) {
    private val dao = database.blockingDao()

    /** Every rule, always current. Read synchronously by the accessibility service. */
    val rules: StateFlow<BlockRules> = combine(dao.observeBlocks(), dao.observeSchedules(), dao.observeLimits()) { blocks, schedules, limits ->
        BlockRules(
            blocks = blocks.associate { it.packageName to it.untilMillis },
            schedules = schedules.map { it.toDomain() },
            limits = limits.associate { it.packageName to AppLimit(it.packageName, it.maxMinutes, it.maxOpens) },
        )
    }.stateIn(scope, SharingStarted.Eagerly, BlockRules.NONE)

    /** Adds [duration] to whatever is left of the app's block. Returns the new end time. */
    suspend fun extendBlock(packageName: String, duration: Duration, nowMillis: Long = System.currentTimeMillis()): Long =
        database.withTransaction {
            dao.deleteExpiredBlocks(nowMillis)
            val until = BlockEngine.extendedUntil(dao.getBlock(packageName)?.untilMillis, nowMillis, duration)
            dao.upsertBlock(AppBlockEntity(packageName, until))
            until
        }

    suspend fun removeBlock(packageName: String) = dao.deleteBlock(packageName)

    suspend fun schedule(id: Long): Schedule? = dao.getSchedule(id)?.toDomain()

    suspend fun saveSchedule(schedule: Schedule) = dao.upsertSchedule(schedule.toEntity())

    suspend fun setScheduleEnabled(id: Long, enabled: Boolean) = dao.setScheduleEnabled(id, enabled)

    suspend fun deleteSchedule(id: Long) = dao.deleteSchedule(id)

    suspend fun saveLimit(limit: AppLimit) {
        if (limit.maxMinutes == null && limit.maxOpens == null) {
            dao.deleteLimit(limit.packageName)
        } else {
            dao.upsertLimit(AppLimitEntity(limit.packageName, limit.maxMinutes, limit.maxOpens))
        }
    }

    suspend fun removeLimit(packageName: String) = dao.deleteLimit(packageName)

    /** Debug reset: every block, schedule and limit, plus today's counters. */
    suspend fun clearEverything() = database.withTransaction {
        dao.clearBlocks()
        dao.clearSchedules()
        dao.clearLimits()
        database.usageDao().clearDay(LocalDate.now().toEpochDay())
    }

    private fun ScheduleEntity.toDomain() = Schedule(
        id = id,
        name = name,
        startMinute = startMinute,
        endMinute = endMinute,
        days = DayOfWeek.entries.filter { daysMask and (1 shl (it.value - 1)) != 0 }.toSet(),
        packages = packages.split('\n').filter { it.isNotBlank() }.toSet(),
        enabled = enabled,
    )

    private fun Schedule.toEntity() = ScheduleEntity(
        id = id,
        name = name,
        startMinute = startMinute,
        endMinute = endMinute,
        daysMask = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
        packages = packages.sorted().joinToString("\n"),
        enabled = enabled,
    )
}
