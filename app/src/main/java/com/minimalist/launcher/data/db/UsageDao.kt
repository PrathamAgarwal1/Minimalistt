package com.minimalist.launcher.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {

    @Query("SELECT * FROM daily_usage WHERE day = :day AND package_name = :packageName")
    suspend fun get(day: Long, packageName: String): DailyUsageEntity?

    @Upsert
    suspend fun upsert(usage: DailyUsageEntity)

    @Query("SELECT COALESCE(SUM(foreground_ms), 0) FROM daily_usage WHERE day = :day AND package_name != :excludedPackage")
    fun observeTotalForeground(day: Long, excludedPackage: String): Flow<Long>

    @Query("DELETE FROM daily_usage WHERE day = :day")
    suspend fun clearDay(day: Long)
}
