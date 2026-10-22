package com.minimalist.launcher.domain.stats

/** The numbers on the stats screen for one stretch of days. */
data class StatsSummary(
    val screenTimeMillis: Long = 0,
    val opens: Int = 0,
    val urgesResisted: Int = 0,
    val blockedAttempts: Int = 0,
    val putAwayMillis: Long = 0,
    val earnedMinutes: Int = 0,
    val urgentMessages: Int = 0,
    /** Screen time per day, oldest first. */
    val perDay: List<Pair<Long, Long>> = emptyList(),
    /** Most-used apps by screen time. */
    val topApps: List<Pair<String, Long>> = emptyList(),
)

object StatsMath {

    /** Time spent in sessions that overlap [from, to), clipped to that range. Open sessions run to [now]. */
    fun overlapMillis(sessions: List<Pair<Long, Long?>>, from: Long, to: Long, now: Long): Long =
        sessions.sumOf { (start, end) ->
            val clippedEnd = minOf(end ?: now, to)
            val clippedStart = maxOf(start, from)
            (clippedEnd - clippedStart).coerceAtLeast(0)
        }
}
