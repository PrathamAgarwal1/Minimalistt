package com.minimalist.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationsDao {

    @Insert
    suspend fun hold(notification: HeldNotificationEntity): Long

    @Query("SELECT * FROM held_notifications WHERE delivered = 0 AND reason = :reason ORDER BY posted_at")
    suspend fun undelivered(reason: String): List<HeldNotificationEntity>

    @Query("UPDATE held_notifications SET delivered = 1 WHERE id IN (:ids)")
    suspend fun markDelivered(ids: List<Long>)

    @Query("SELECT * FROM held_notifications ORDER BY posted_at DESC LIMIT 300")
    fun observeRecent(): Flow<List<HeldNotificationEntity>>

    @Query("DELETE FROM held_notifications")
    suspend fun clearHeld()

    @Query("DELETE FROM held_notifications WHERE posted_at < :beforeMillis")
    suspend fun pruneHeld(beforeMillis: Long)

    @Query("SELECT * FROM vip_contacts ORDER BY name")
    fun observeVips(): Flow<List<VipContactEntity>>

    @Insert
    suspend fun insertVip(vip: VipContactEntity): Long

    @Query("DELETE FROM vip_contacts WHERE id = :id")
    suspend fun deleteVip(id: Long)

    @Insert
    suspend fun logUrgent(entry: UrgentLogEntity): Long

    @Query("SELECT COUNT(*) FROM urgent_log WHERE sent_at >= :sinceMillis")
    suspend fun urgentSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM urgent_log WHERE sent_at >= :sinceMillis")
    fun observeUrgentSince(sinceMillis: Long): Flow<Int>
}
