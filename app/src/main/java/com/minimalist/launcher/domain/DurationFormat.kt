package com.minimalist.launcher.domain

object DurationFormat {

    /** "3 days 4 h", "2 h 15 min", "12 min", "40 s". */
    fun remaining(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0) + 999) / 1_000
        val days = totalSeconds / 86_400
        val hours = totalSeconds % 86_400 / 3_600
        val minutes = totalSeconds % 3_600 / 60
        val seconds = totalSeconds % 60
        return when {
            days > 0 -> if (hours > 0) "$days ${if (days == 1L) "day" else "days"} $hours h" else "$days ${if (days == 1L) "day" else "days"}"
            hours > 0 -> if (minutes > 0) "$hours h $minutes min" else "$hours h"
            minutes > 0 -> "$minutes min"
            else -> "$seconds s"
        }
    }

    /** Compact screen-time figure: "1h 12m", "45m", "0m". */
    fun short(millis: Long): String {
        val totalMinutes = millis.coerceAtLeast(0) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
