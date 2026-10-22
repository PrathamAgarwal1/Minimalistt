package com.minimalist.launcher.domain.friction

import com.minimalist.launcher.domain.blocking.TimeScale
import java.time.Duration

/**
 * "Still worth it?": after this many unbroken minutes in one app, Quiet steps in and asks.
 * Leaving the app, or the screen going off, starts the count again.
 */
object ScrollCheck {

    const val DEFAULT_MINUTES = 20
    val OPTIONS = listOf(0, 10, 20, 30, 45)

    fun isDue(foregroundSinceMillis: Long, nowMillis: Long, minutes: Int, scale: TimeScale = TimeScale.REAL): Boolean =
        minutes > 0 && nowMillis - foregroundSinceMillis >= scale.minutesAsSeconds(Duration.ofMinutes(minutes.toLong())).toMillis()

    /** Switching it off, or asking less often, weakens protection. */
    fun weakens(old: Int, new: Int): Boolean = old > 0 && (new == 0 || new > old)
}
