package com.minimalist.launcher.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/** Totals for the stats screen, and every table for the CSV export. */
@Dao
interface StatsDao {

    @Upsert
    suspend fun upsertEarning(earning: DailyEarningEntity)

    @Query("SELECT * FROM daily_earnings WHERE day BETWEEN :fromDay AND :toDay")
    suspend fun earnings(fromDay: Long, toDay: Long): List<DailyEarningEntity>

    @Query("SELECT * FROM daily_usage WHERE day BETWEEN :fromDay AND :toDay AND package_name != :excludedPackage")
    suspend fun usage(fromDay: Long, toDay: Long, excludedPackage: String): List<DailyUsageEntity>

    @Query("SELECT COUNT(*) FROM friction_log WHERE outcome = 'resisted' AND started_at BETWEEN :from AND :to")
    suspend fun resisted(from: Long, to: Long): Int

    @Query("SELECT * FROM mode_log WHERE kind = :kind AND (ended_at IS NULL OR ended_at >= :from) AND started_at < :to")
    suspend fun modes(kind: String, from: Long, to: Long): List<ModeLogEntity>

    @Query("SELECT COUNT(*) FROM urgent_log WHERE sent_at BETWEEN :from AND :to")
    suspend fun urgent(from: Long, to: Long): Int

    @Query("SELECT * FROM first_opens WHERE day BETWEEN :fromDay AND :toDay ORDER BY day")
    suspend fun firstOpens(fromDay: Long, toDay: Long): List<FirstOpenEntity>

    // Everything, for the export.

    @Query("SELECT * FROM daily_usage ORDER BY day, package_name") suspend fun allUsage(): List<DailyUsageEntity>
    @Query("SELECT * FROM daily_earnings ORDER BY day") suspend fun allEarnings(): List<DailyEarningEntity>
    @Query("SELECT * FROM friction_log ORDER BY started_at") suspend fun allFriction(): List<FrictionLogEntity>
    @Query("SELECT * FROM mode_log ORDER BY started_at") suspend fun allModes(): List<ModeLogEntity>
    @Query("SELECT * FROM unlock_log ORDER BY started_at") suspend fun allUnlocks(): List<UnlockLogEntity>
    @Query("SELECT * FROM urgent_log ORDER BY sent_at") suspend fun allUrgent(): List<UrgentLogEntity>
    @Query("SELECT * FROM first_opens ORDER BY day") suspend fun allFirstOpens(): List<FirstOpenEntity>
    @Query("SELECT * FROM held_notifications ORDER BY posted_at") suspend fun allHeld(): List<HeldNotificationEntity>
    @Query("SELECT * FROM reflections ORDER BY day") suspend fun allReflections(): List<ReflectionEntity>
    @Query("SELECT * FROM habits ORDER BY position") suspend fun allHabits(): List<HabitEntity>
    @Query("SELECT * FROM habit_checks ORDER BY day") suspend fun allHabitChecks(): List<HabitCheckEntity>
    @Query("SELECT * FROM pickups ORDER BY at") suspend fun allPickups(): List<PickupEntity>
    @Query("SELECT * FROM app_blocks") suspend fun allBlocks(): List<AppBlockEntity>
    @Query("SELECT * FROM schedules") suspend fun allSchedules(): List<ScheduleEntity>
    @Query("SELECT * FROM app_limits") suspend fun allLimits(): List<AppLimitEntity>
    @Query("SELECT * FROM friction_apps") suspend fun allFrictionApps(): List<FrictionAppEntity>
    @Query("SELECT * FROM blocked_domains") suspend fun allDomains(): List<BlockedDomainEntity>
    @Query("SELECT * FROM vip_contacts") suspend fun allVips(): List<VipContactEntity>
    @Query("SELECT * FROM pending_changes ORDER BY applies_at") suspend fun allPending(): List<PendingChangeEntity>
    @Query("SELECT * FROM letters ORDER BY written_at") suspend fun allLetters(): List<LetterEntity>
}
