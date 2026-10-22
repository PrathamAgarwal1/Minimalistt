package com.minimalist.launcher.data.stats

import com.minimalist.launcher.system.TrustedClock
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.db.FirstOpenEntity
import com.minimalist.launcher.domain.modes.LockKind
import com.minimalist.launcher.domain.stats.Csv
import com.minimalist.launcher.domain.stats.CsvTable
import com.minimalist.launcher.domain.stats.StatsMath
import com.minimalist.launcher.domain.stats.StatsSummary
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

class StatsRepository(database: AppDatabase, private val ownPackage: String) {

    private val dao = database.statsDao()

    /** Totals from [fromDay] to [toDay] (epoch days, inclusive). */
    suspend fun summary(fromDay: Long, toDay: Long): StatsSummary {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.ofEpochDay(fromDay).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.ofEpochDay(toDay + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = TrustedClock.nowMillis()
        val usage = dao.usage(fromDay, toDay, ownPackage)
        val putAways = dao.modes(LockKind.PutAway.name, from, to).map { it.startedAt to it.endedAt }
        return StatsSummary(
            screenTimeMillis = usage.sumOf { it.foregroundMs },
            opens = usage.sumOf { it.opens },
            urgesResisted = dao.resisted(from, to),
            blockedAttempts = usage.sumOf { it.blockedAttempts },
            putAwayMillis = StatsMath.overlapMillis(putAways, from, to, now),
            earnedMinutes = dao.earnings(fromDay, toDay).sumOf { it.minutes },
            urgentMessages = dao.urgent(from, to),
            perDay = (fromDay..toDay).map { day -> day to usage.filter { it.day == day }.sumOf { it.foregroundMs } },
            topApps = usage.groupBy { it.packageName }
                .map { (pkg, rows) -> pkg to rows.sumOf { it.foregroundMs } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(8),
        )
    }

    suspend fun firstOpens(fromDay: Long, toDay: Long): List<FirstOpenEntity> = dao.firstOpens(fromDay, toDay)

    /** Every table as CSV, zipped into [out]. */
    suspend fun export(out: OutputStream) {
        val tables = listOf(
            CsvTable("daily_usage", listOf("day", "date", "package", "foreground_ms", "opens", "blocked_attempts"),
                dao.allUsage().map { listOf(it.day, LocalDate.ofEpochDay(it.day), it.packageName, it.foregroundMs, it.opens, it.blockedAttempts) }),
            CsvTable("daily_earnings", listOf("day", "date", "steps", "focus_sessions", "minutes"),
                dao.allEarnings().map { listOf(it.day, LocalDate.ofEpochDay(it.day), it.steps, it.focusSessions, it.minutes) }),
            CsvTable("friction_log", listOf("id", "package", "started_at", "urge", "reason", "outcome", "session_minutes", "worth", "ended_at"),
                dao.allFriction().map { listOf(it.id, it.packageName, it.startedAt, it.urge, it.reason, it.outcome, it.sessionMinutes, it.worth, it.endedAt) }),
            CsvTable("modes", listOf("id", "kind", "started_at", "ended_at"),
                dao.allModes().map { listOf(it.id, it.kind, it.startedAt, it.endedAt) }),
            CsvTable("early_unlocks", listOf("id", "target", "kind", "reason", "problems", "started_at", "until"),
                dao.allUnlocks().map { listOf(it.id, it.target, it.kind, it.reason, it.problems, it.startedAt, it.untilMillis) }),
            CsvTable("urgent_messages", listOf("id", "vip", "sent_at", "mode"),
                dao.allUrgent().map { listOf(it.id, it.vipName, it.sentAt, it.mode) }),
            CsvTable("first_opens", listOf("day", "date", "at", "early"),
                dao.allFirstOpens().map { listOf(it.day, LocalDate.ofEpochDay(it.day), it.at, it.early) }),
            CsvTable("held_notifications", listOf("id", "package", "title", "text", "posted_at", "reason", "delivered"),
                dao.allHeld().map { listOf(it.id, it.packageName, it.title, it.text, it.postedAt, it.reason, it.delivered) }),
            CsvTable("reflections", listOf("day", "date", "went_well", "first_task"),
                dao.allReflections().map { listOf(it.day, LocalDate.ofEpochDay(it.day), it.wentWell, it.firstTask) }),
            CsvTable("habits", listOf("id", "title", "position"), dao.allHabits().map { listOf(it.id, it.title, it.position) }),
            CsvTable("habit_checks", listOf("day", "habit_id"), dao.allHabitChecks().map { listOf(it.day, it.habitId) }),
            CsvTable("pickups", listOf("at"), dao.allPickups().map { listOf(it.at) }),
            CsvTable("blocks", listOf("package", "until"), dao.allBlocks().map { listOf(it.packageName, it.untilMillis) }),
            CsvTable("schedules", listOf("id", "name", "start_minute", "end_minute", "days_mask", "packages", "enabled"),
                dao.allSchedules().map { listOf(it.id, it.name, it.startMinute, it.endMinute, it.daysMask, it.packages.replace('\n', ' '), it.enabled) }),
            CsvTable("limits", listOf("package", "max_minutes", "max_opens"), dao.allLimits().map { listOf(it.packageName, it.maxMinutes, it.maxOpens) }),
            CsvTable("friction_apps", listOf("package"), dao.allFrictionApps().map { listOf(it.packageName) }),
            CsvTable("blocked_websites", listOf("domain"), dao.allDomains().map { listOf(it.domain) }),
            CsvTable("vip_contacts", listOf("id", "name", "phone"), dao.allVips().map { listOf(it.id, it.name, it.phone) }),
            CsvTable("waiting_changes", listOf("id", "description", "queued_at", "applies_at"),
                dao.allPending().map { listOf(it.id, it.description, it.queuedAt, it.appliesAt) }),
            CsvTable("letters", listOf("id", "text", "written_at"), dao.allLetters().map { listOf(it.id, it.text, it.writtenAt) }),
        )
        Csv.writeZip(tables, out)
    }
}
