package com.minimalist.launcher.domain.blocking

import kotlin.math.abs

/**
 * A clock that moves only with real elapsed time, so setting the phone's clock forward can't
 * end a block, a pass or a cooldown early. [anchorWall] was the trusted time when the
 * monotonic clock read [anchorElapsed]; both reset on reboot.
 */
data class TimeAnchor(val anchorWall: Long, val anchorElapsed: Long, val bootCount: Int)

object TrustedTime {

    /** Small corrections (network time nudges) are followed; anything bigger is ignored. */
    const val TOLERANCE_MILLIS = 5_000L

    /** Trusted now, and the anchor to keep using. */
    fun read(anchor: TimeAnchor, wall: Long, elapsed: Long): Pair<Long, TimeAnchor> {
        val trusted = anchor.anchorWall + (elapsed - anchor.anchorElapsed)
        return if (abs(wall - trusted) <= TOLERANCE_MILLIS) {
            wall to anchor.copy(anchorWall = wall, anchorElapsed = elapsed)
        } else {
            trusted to anchor
        }
    }

    /**
     * After a reboot the monotonic clock starts again, so the wall clock is all there is.
     * It may not go back before the last trusted time seen.
     */
    fun afterBoot(lastTrusted: Long?, wall: Long, elapsed: Long, bootCount: Int): TimeAnchor =
        TimeAnchor(maxOf(wall, lastTrusted ?: 0L), elapsed, bootCount)
}
