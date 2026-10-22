package com.minimalist.launcher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Every early way in: challenge passes and emergency unlocks. */
@Entity(tableName = "unlock_log", indices = [Index("until_millis"), Index("started_at")])
data class UnlockLogEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    /** "app:<package>", "domain:<domain>", "lock" or "all". */
    @ColumnInfo(name = "target") val target: String,
    /** "challenge" or "emergency". */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "reason") val reason: String?,
    @ColumnInfo(name = "problems") val problems: Int,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "until_millis") val untilMillis: Long,
)

/** A change that weakens protection, waiting out its cooldown. */
@Entity(tableName = "pending_changes", indices = [Index("applies_at")])
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "queued_at") val queuedAt: Long,
    @ColumnInfo(name = "applies_at") val appliesAt: Long,
)

/** Written while calm, read before weakening anything. */
@Entity(tableName = "letters")
data class LetterEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "written_at") val writtenAt: Long,
)
