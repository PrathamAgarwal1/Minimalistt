package com.minimalist.launcher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A timed block. One row per package, so clones and reinstalls share it. */
@Entity(tableName = "app_blocks")
data class AppBlockEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "until_millis") val untilMillis: Long,
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "end_minute") val endMinute: Int,
    /** Bit (dayOfWeek.value - 1) set for each day the schedule starts on. */
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    /** Package names, one per line. */
    @ColumnInfo(name = "packages") val packages: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
)

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "max_minutes") val maxMinutes: Int?,
    @ColumnInfo(name = "max_opens") val maxOpens: Int?,
)

/** Per-app totals for one local day (epoch day). */
@Entity(tableName = "daily_usage", primaryKeys = ["day", "package_name"])
data class DailyUsageEntity(
    @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long = 0,
    @ColumnInfo(name = "opens") val opens: Int = 0,
    @ColumnInfo(name = "blocked_attempts") val blockedAttempts: Int = 0,
)
