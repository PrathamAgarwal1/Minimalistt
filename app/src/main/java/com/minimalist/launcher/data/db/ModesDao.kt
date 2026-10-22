package com.minimalist.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ModesDao {

    @Query("SELECT * FROM mode_log WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    suspend fun openMode(): ModeLogEntity?

    @Insert
    suspend fun insertMode(entry: ModeLogEntity): Long

    @Query("UPDATE mode_log SET ended_at = :endedAt WHERE id = :id")
    suspend fun endMode(id: Long, endedAt: Long)

    @Query("INSERT OR IGNORE INTO first_opens (day, at, early) VALUES (:day, :at, :early)")
    suspend fun recordFirstOpen(day: Long, at: Long, early: Boolean)

    @Query("SELECT * FROM first_opens WHERE day = :day")
    fun observeFirstOpen(day: Long): Flow<FirstOpenEntity?>

    @Query("SELECT * FROM mode_log WHERE kind = :kind ORDER BY started_at DESC LIMIT 1")
    suspend fun lastMode(kind: String): ModeLogEntity?

    @Query("INSERT OR IGNORE INTO pickups (at) VALUES (:at)")
    suspend fun insertPickup(at: Long)

    @Query("SELECT at FROM pickups WHERE at >= :sinceMillis")
    suspend fun pickupsSince(sinceMillis: Long): List<Long>

    @Query("DELETE FROM pickups WHERE at < :beforeMillis")
    suspend fun prunePickups(beforeMillis: Long)

    @Query("SELECT * FROM reflections WHERE day = :day")
    fun observeReflection(day: Long): Flow<ReflectionEntity?>

    @Query("SELECT * FROM reflections WHERE day = :day")
    suspend fun reflection(day: Long): ReflectionEntity?

    @Upsert
    suspend fun upsertReflection(reflection: ReflectionEntity)

    @Query("SELECT * FROM habits ORDER BY position, id")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Query("SELECT habit_id FROM habit_checks WHERE day = :day")
    fun observeChecks(day: Long): Flow<List<Long>>

    @Query("INSERT OR IGNORE INTO habit_checks (day, habit_id) VALUES (:day, :habitId)")
    suspend fun check(day: Long, habitId: Long)

    @Query("DELETE FROM habit_checks WHERE day = :day AND habit_id = :habitId")
    suspend fun uncheck(day: Long, habitId: Long)
}
