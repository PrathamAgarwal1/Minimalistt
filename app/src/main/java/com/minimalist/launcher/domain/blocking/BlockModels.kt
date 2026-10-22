package com.minimalist.launcher.domain.blocking

import com.minimalist.launcher.domain.modes.LockKind
import java.time.DayOfWeek

/** A recurring window, e.g. 22:00–08:00 every day, or class hours on weekdays. */
data class Schedule(
    val id: Long,
    val name: String,
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<DayOfWeek>,
    val packages: Set<String>,
    val enabled: Boolean,
)

/** Per-day caps for one app. Either cap may be absent. */
data class AppLimit(
    val packageName: String,
    val maxMinutes: Int?,
    val maxOpens: Int?,
)

/** Every rule that can block an app, keyed by package name so reinstalling changes nothing. */
data class BlockRules(
    val blocks: Map<String, Long>,
    val schedules: List<Schedule>,
    val limits: Map<String, AppLimit>,
) {
    companion object {
        val NONE = BlockRules(emptyMap(), emptyList(), emptyMap())
    }
}

/** What an app has used so far today. */
data class UsageToday(val foregroundMs: Long, val opens: Int) {
    companion object {
        val NONE = UsageToday(0, 0)
    }
}

sealed interface BlockReason {
    val untilMillis: Long

    data class Timed(override val untilMillis: Long) : BlockReason

    data class Scheduled(val scheduleName: String, override val untilMillis: Long) : BlockReason

    data class TimeLimit(val limitMinutes: Int, override val untilMillis: Long) : BlockReason

    data class OpenLimit(val maxOpens: Int, override val untilMillis: Long) : BlockReason

    /** Put away, morning or night: only essentials work. */
    data class Locked(val kind: LockKind, override val untilMillis: Long) : BlockReason

    /** Not the one browser allowed. Lasts until the setting changes. */
    data class OtherBrowser(val allowedLabel: String) : BlockReason {
        override val untilMillis: Long get() = Long.MAX_VALUE
    }

    /** An earned-time app with no earned time left today. */
    data class EarnedUp(override val untilMillis: Long) : BlockReason

    /** A website that belongs to a blocked app, or one added by hand. */
    data class Website(val domain: String, override val untilMillis: Long) : BlockReason
}

sealed interface BlockDecision {

    data object Allowed : BlockDecision

    data class Blocked(val reasons: List<BlockReason>) : BlockDecision {
        init {
            require(reasons.isNotEmpty())
        }

        /** The app opens again once every reason has ended. */
        val untilMillis: Long get() = reasons.maxOf { it.untilMillis }

        /** The reason that lasts longest, which is the one worth showing. */
        val primary: BlockReason get() = reasons.maxBy { it.untilMillis }
    }
}
