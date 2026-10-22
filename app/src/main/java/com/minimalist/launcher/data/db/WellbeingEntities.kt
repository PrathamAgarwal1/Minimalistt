package com.minimalist.launcher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Apps that open only after a countdown, a reason and a chosen session length. */
@Entity(tableName = "friction_apps")
data class FrictionAppEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
)

/** One pull towards a friction app: opened (with a session) or resisted. */
@Entity(tableName = "friction_log", indices = [Index("package_name"), Index("started_at")])
data class FrictionLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** 1–5: how strong the urge was. */
    @ColumnInfo(name = "urge") val urge: Int?,
    @ColumnInfo(name = "reason") val reason: String?,
    /** "opened" or "resisted". */
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "session_minutes") val sessionMinutes: Int?,
    /** 1–5: whether it was worth it, asked on leaving. */
    @ColumnInfo(name = "worth") val worth: Int?,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
)

/** Put away, morning and night sessions, for stats. */
@Entity(tableName = "mode_log", indices = [Index("started_at")])
data class ModeLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
)

/** Night reflection for one local day (epoch day). */
@Entity(tableName = "reflections")
data class ReflectionEntity(
    @PrimaryKey @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "went_well") val wentWell: String?,
    @ColumnInfo(name = "first_task") val firstTask: String?,
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "position") val position: Int,
)

@Entity(tableName = "habit_checks", primaryKeys = ["day", "habit_id"])
data class HabitCheckEntity(
    @ColumnInfo(name = "day") val day: Long,
    @ColumnInfo(name = "habit_id") val habitId: Long,
)

/** A notification taken out of the shade: from a blocked app, during a mode, or for a batch. */
@Entity(tableName = "held_notifications", indices = [Index("delivered"), Index("posted_at")])
data class HeldNotificationEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "text") val text: String?,
    @ColumnInfo(name = "posted_at") val postedAt: Long,
    /** "blocked", "mode" or "batch". */
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "delivered") val delivered: Boolean,
)

@Entity(tableName = "vip_contacts")
data class VipContactEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    /** International format without "+", as wa.me expects. */
    @ColumnInfo(name = "phone") val phone: String,
)

@Entity(tableName = "urgent_log", indices = [Index("sent_at")])
data class UrgentLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "vip_name") val vipName: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "mode") val mode: String?,
)

/** Custom website blocks, on top of the sites that belong to blocked apps. */
@Entity(tableName = "blocked_domains")
data class BlockedDomainEntity(
    @PrimaryKey @ColumnInfo(name = "domain") val domain: String,
)

/** One unlock of the phone. */
@Entity(tableName = "pickups")
data class PickupEntity(
    @PrimaryKey @ColumnInfo(name = "at") val at: Long,
)
