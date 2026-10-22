package com.minimalist.launcher.domain.friction

import com.minimalist.launcher.domain.blocking.TimeScale

object FrictionRules {

    const val BASE_SECONDS = 5
    val BASE_OPTIONS = listOf(5, 10, 15, 30)
    const val MAX_SECONDS = 600
    val SESSION_MINUTES = listOf(5, 10, 15)

    /**
     * [baseSeconds] for the first open of the day, then twice as long each time (5, 10, 20,
     * 40…), capped at ten minutes. Wind-down doubles it again.
     */
    fun countdownSeconds(
        opensToday: Int,
        scale: TimeScale = TimeScale.REAL,
        baseSeconds: Int = BASE_SECONDS,
        windDown: Boolean = false,
    ): Int {
        val doubled = baseSeconds.toLong() shl opensToday.coerceIn(0, 16)
        val seconds = (doubled * if (windDown) 2 else 1).coerceAtMost(MAX_SECONDS.toLong()).toInt()
        return if (scale.testMode) (seconds / 5).coerceAtLeast(1) else seconds
    }

    fun isValidReason(reason: String) = reason.trim().length >= 3
}

/** One rated pull towards an app. */
data class RatedPull(val packageName: String, val urge: Int?, val worth: Int?, val opened: Boolean)

data class AppFeeling(
    val packageName: String,
    val opens: Int,
    val resisted: Int,
    val averageUrge: Double?,
    val averageWorth: Double?,
) {
    /** Wanted a lot, enjoyed little: the apps worth blocking. */
    val wantingOutpacesLiking: Boolean
        get() = opens >= 2 && averageUrge != null && averageWorth != null &&
            averageUrge >= 3.5 && averageWorth <= 2.5
}

object WantingLiking {

    fun summarize(pulls: List<RatedPull>): List<AppFeeling> = pulls
        .groupBy { it.packageName }
        .map { (packageName, entries) ->
            val opened = entries.filter { it.opened }
            AppFeeling(
                packageName = packageName,
                opens = opened.size,
                resisted = entries.size - opened.size,
                averageUrge = entries.mapNotNull { it.urge }.averageOrNull(),
                averageWorth = opened.mapNotNull { it.worth }.averageOrNull(),
            )
        }
        .sortedWith(
            compareByDescending<AppFeeling> { it.wantingOutpacesLiking }
                .thenByDescending { (it.averageUrge ?: 0.0) - (it.averageWorth ?: it.averageUrge ?: 0.0) }
                .thenByDescending { it.opens },
        )

    private fun List<Int>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
