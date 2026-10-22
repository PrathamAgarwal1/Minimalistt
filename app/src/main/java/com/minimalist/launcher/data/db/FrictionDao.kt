package com.minimalist.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FrictionDao {

    @Query("SELECT package_name FROM friction_apps")
    fun observeApps(): Flow<List<String>>

    @Query("INSERT OR IGNORE INTO friction_apps (package_name) VALUES (:packageName)")
    suspend fun addApp(packageName: String)

    @Query("DELETE FROM friction_apps WHERE package_name = :packageName")
    suspend fun removeApp(packageName: String)

    @Insert
    suspend fun insertLog(entry: FrictionLogEntity): Long

    @Query("UPDATE friction_log SET ended_at = :endedAt WHERE id = :id AND ended_at IS NULL")
    suspend fun endSession(id: Long, endedAt: Long)

    @Query("UPDATE friction_log SET worth = :worth WHERE id = :id")
    suspend fun setWorth(id: Long, worth: Int)

    @Query("SELECT COUNT(*) FROM friction_log WHERE package_name = :packageName AND outcome = 'opened' AND started_at >= :sinceMillis")
    suspend fun opensSince(packageName: String, sinceMillis: Long): Int

    @Query("SELECT * FROM friction_log WHERE started_at >= :sinceMillis ORDER BY started_at")
    fun observeSince(sinceMillis: Long): Flow<List<FrictionLogEntity>>

    @Query("SELECT COUNT(*) FROM friction_log WHERE outcome = 'resisted' AND started_at >= :sinceMillis")
    fun observeResistedSince(sinceMillis: Long): Flow<Int>

    @Query("DELETE FROM friction_apps")
    suspend fun clearApps()
}
