package com.minimalist.launcher.domain.blocking

import java.time.Duration

/**
 * Real durations, or the shrunken ones of debug test mode: hours become seconds (so a month
 * becomes 12 minutes), daily-limit minutes become seconds, and the 24-hour cooldown becomes
 * one minute. Release builds always use [REAL].
 */
data class TimeScale(val testMode: Boolean) {

    fun block(duration: Duration): Duration = if (testMode) duration.dividedBy(3_600) else duration

    fun dailyLimit(duration: Duration): Duration = if (testMode) duration.dividedBy(60) else duration

    val cooldown: Duration get() = if (testMode) Duration.ofMinutes(1) else Duration.ofHours(24)

    companion object {
        val REAL = TimeScale(testMode = false)
    }
}

enum class BlockUnit(val singular: String, val plural: String, val duration: Duration) {
    Hours("hour", "hours", Duration.ofHours(1)),
    Days("day", "days", Duration.ofDays(1)),
    Weeks("week", "weeks", Duration.ofDays(7)),
    Months("month", "months", Duration.ofDays(30)),
    ;

    fun label(amount: Int) = "$amount ${if (amount == 1) singular else plural}"
}
