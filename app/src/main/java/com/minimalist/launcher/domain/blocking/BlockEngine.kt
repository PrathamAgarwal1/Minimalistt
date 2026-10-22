package com.minimalist.launcher.domain.blocking

import com.minimalist.launcher.domain.modes.ActiveLock
import java.time.Duration
import java.time.ZonedDateTime

object BlockEngine {

    /**
     * Decides whether [packageName] may be in the foreground at [now].
     *
     * [atOpen] is true when the app is being opened rather than already in use: the opens limit
     * only stops a new open, never an app that is already open.
     */
    fun evaluate(
        packageName: String,
        now: ZonedDateTime,
        rules: BlockRules,
        usage: UsageToday,
        protectedPackages: Set<String>,
        scale: TimeScale,
        atOpen: Boolean,
        lock: ActiveLock? = null,
        lockAllowedPackages: Set<String> = emptySet(),
        otherBrowsers: Set<String> = emptySet(),
        allowedBrowserLabel: String = "",
    ): BlockDecision {
        if (packageName in protectedPackages) return BlockDecision.Allowed
        val nowMillis = now.toInstant().toEpochMilli()
        val reasons = mutableListOf<BlockReason>()

        if (lock != null && lock.untilMillis > nowMillis && packageName !in lockAllowedPackages) {
            reasons += BlockReason.Locked(lock.kind, lock.untilMillis)
        }
        if (packageName in otherBrowsers) reasons += BlockReason.OtherBrowser(allowedBrowserLabel)

        rules.blocks[packageName]?.takeIf { it > nowMillis }?.let { reasons += BlockReason.Timed(it) }

        rules.schedules
            .filter { packageName in it.packages }
            .forEach { schedule ->
                ScheduleRules.activeUntil(schedule, now)?.let {
                    reasons += BlockReason.Scheduled(schedule.name, it.toInstant().toEpochMilli())
                }
            }

        rules.limits[packageName]?.let { limit ->
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
            limit.maxMinutes?.let { minutes ->
                if (usage.foregroundMs >= scale.dailyLimit(Duration.ofMinutes(minutes.toLong())).toMillis()) {
                    reasons += BlockReason.TimeLimit(minutes, midnight)
                }
            }
            limit.maxOpens?.let { opens ->
                if (atOpen && usage.opens >= opens) reasons += BlockReason.OpenLimit(opens, midnight)
            }
        }

        return if (reasons.isEmpty()) BlockDecision.Allowed else BlockDecision.Blocked(reasons)
    }

    /** Blocks only ever grow: a new block adds to whatever time is left on the current one. */
    fun extendedUntil(currentUntilMillis: Long?, nowMillis: Long, duration: Duration): Long =
        maxOf(currentUntilMillis ?: 0L, nowMillis) + duration.toMillis()
}
