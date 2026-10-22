package com.minimalist.launcher.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockingDao {

    @Query("SELECT * FROM app_blocks")
    fun observeBlocks(): Flow<List<AppBlockEntity>>

    @Query("SELECT * FROM app_blocks WHERE package_name = :packageName")
    suspend fun getBlock(packageName: String): AppBlockEntity?

    @Upsert
    suspend fun upsertBlock(block: AppBlockEntity)

    @Query("DELETE FROM app_blocks WHERE package_name = :packageName")
    suspend fun deleteBlock(packageName: String)

    @Query("DELETE FROM app_blocks WHERE until_millis <= :nowMillis")
    suspend fun deleteExpiredBlocks(nowMillis: Long)

    @Query("SELECT * FROM schedules ORDER BY start_minute, id")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getSchedule(id: Long): ScheduleEntity?

    @Upsert
    suspend fun upsertSchedule(schedule: ScheduleEntity)

    @Query("UPDATE schedules SET enabled = :enabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    @Query("SELECT * FROM app_limits")
    fun observeLimits(): Flow<List<AppLimitEntity>>

    @Upsert
    suspend fun upsertLimit(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE package_name = :packageName")
    suspend fun deleteLimit(packageName: String)

    @Query("DELETE FROM app_blocks")
    suspend fun clearBlocks()

    @Query("DELETE FROM schedules")
    suspend fun clearSchedules()

    @Query("DELETE FROM app_limits")
    suspend fun clearLimits()
}
