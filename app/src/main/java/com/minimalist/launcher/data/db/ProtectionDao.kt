package com.minimalist.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectionDao {

    @Insert
    suspend fun insertUnlock(entry: UnlockLogEntity): Long

    /** Passes that may still be running, newest first. */
    @Query("SELECT * FROM unlock_log WHERE until_millis > :nowMillis ORDER BY until_millis DESC")
    fun observeActive(nowMillis: Long): Flow<List<UnlockLogEntity>>

    @Query("SELECT COUNT(*) FROM unlock_log WHERE kind IN (:kinds) AND started_at >= :sinceMillis")
    suspend fun countSince(kinds: List<String>, sinceMillis: Long): Int

    @Query("SELECT started_at FROM unlock_log WHERE kind = 'emergency' ORDER BY started_at DESC LIMIT 50")
    suspend fun emergencyTimes(): List<Long>

    @Query("UPDATE unlock_log SET until_millis = :nowMillis WHERE until_millis > :nowMillis")
    suspend fun endAllPasses(nowMillis: Long)

    @Insert
    suspend fun insertPending(change: PendingChangeEntity): Long

    @Query("SELECT * FROM pending_changes ORDER BY applies_at")
    fun observePending(): Flow<List<PendingChangeEntity>>

    @Query("SELECT * FROM pending_changes WHERE applies_at <= :nowMillis ORDER BY applies_at")
    suspend fun duePending(nowMillis: Long): List<PendingChangeEntity>

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun deletePending(id: Long)

    @Query("DELETE FROM pending_changes")
    suspend fun clearPending()

    @Query("SELECT * FROM letters ORDER BY written_at DESC")
    fun observeLetters(): Flow<List<LetterEntity>>

    @Query("SELECT * FROM letters ORDER BY RANDOM() LIMIT 1")
    suspend fun randomLetter(): LetterEntity?

    @Insert
    suspend fun insertLetter(letter: LetterEntity): Long

    @Query("DELETE FROM letters WHERE id = :id")
    suspend fun deleteLetter(id: Long)
}
