package com.minimalist.launcher.modes

import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.domain.modes.DayModes
import com.minimalist.launcher.domain.modes.PickupRules
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Starts and ends modes on time: study hours, unplugging, too many pickups, the first unlock of
 * the morning. Runs from the guard service; every step is safe to repeat.
 */
class ModeCoordinator(private val container: AppContainer) {

    private val lock = Mutex()
    private val modes get() = container.modes

    suspend fun tick() = lock.withLock { tickLocked() }

    suspend fun onUserPresent() = lock.withLock {
        val nowMillis = System.currentTimeMillis()
        val state = modes.state.value
        val recent = modes.recordPickup(nowMillis)
            // Only pickups since the last Put away ended count towards the next one.
            .filter { it > (state.putAwayUntil ?: 0L) }
        if (container.currentLock() == null && PickupRules.tooMany(recent, nowMillis, state.pickupThreshold)) {
            putAwayFor(state.pickupMinutes, nowMillis)
        }
        maybeStartMorningLocked()
        tickLocked()
    }

    suspend fun onPowerDisconnected() = lock.withLock {
        val minutes = modes.state.value.unplugMinutes
        if (minutes > 0) putAwayFor(minutes, System.currentTimeMillis())
        tickLocked()
    }

    /** Also called when the launcher comes to the front, in case the guard service isn't running. */
    suspend fun maybeStartMorning() = lock.withLock { maybeStartMorningLocked() }

    private suspend fun maybeStartMorningLocked() {
        val state = modes.state.value
        val now = ZonedDateTime.now()
        if (container.currentLock() != null) return
        if (DayModes.shouldStartMorning(state.dayModes, now, state.lastMorningDay)) {
            modes.startMorning(now.toInstant().toEpochMilli(), now.toLocalDate().toEpochDay())
        }
    }

    private suspend fun tickLocked() {
        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()
        val state = modes.state.value

        // Study hours start Put away for the rest of the window, once per window.
        state.studyWindows.mapNotNull { it.activeUntil(now) }.maxOrNull()?.let { end ->
            val endMillis = end.toInstant().toEpochMilli()
            if ((state.studyHandledUntil ?: 0L) < endMillis) {
                modes.putAwayUntilAtLeast(endMillis)
                modes.setStudyHandledUntil(endMillis)
            }
        }

        val lock = container.currentLock()
        modes.syncModeLog(lock, nowMillis)
        container.notifications.onTick(lock, nowMillis)
    }

    private suspend fun putAwayFor(minutes: Int, nowMillis: Long) {
        val length = container.timeScale.value.minutesAsSeconds(Duration.ofMinutes(minutes.toLong())).toMillis()
        modes.putAwayUntilAtLeast(nowMillis + length)
    }
}
