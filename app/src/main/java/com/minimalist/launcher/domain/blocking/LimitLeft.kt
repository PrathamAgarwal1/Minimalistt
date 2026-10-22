package com.minimalist.launcher.domain.blocking

import java.time.Duration

/** The quiet hint beside a favourite with a daily limit: "12m left", "2 opens left", "used up". */
object LimitLeft {

    fun hint(limit: AppLimit?, usage: UsageToday, scale: TimeScale = TimeScale.REAL): String? {
        if (limit == null) return null
        val millisLeft = limit.maxMinutes?.let { minutes ->
            scale.dailyLimit(Duration.ofMinutes(minutes.toLong())).toMillis() - usage.foregroundMs
        }
        val opensLeft = limit.maxOpens?.let { it - usage.opens }
        if ((millisLeft != null && millisLeft <= 0) || (opensLeft != null && opensLeft <= 0)) return "used up"
        return listOfNotNull(
            millisLeft?.let { "${minutes(it)} left" },
            opensLeft?.let { "$it ${if (it == 1) "open" else "opens"} left" },
        ).joinToString(" · ")
    }

    /** Rounded up, so "1m" shows until the very end: "1h 05m", "12m". */
    private fun minutes(millis: Long): String {
        val total = (millis + 59_999) / 60_000
        return if (total >= 60) "${total / 60}h %02dm".format(total % 60) else "${total}m"
    }
}
