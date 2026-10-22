package com.minimalist.launcher.modes

import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.data.protection.UnlockTarget
import com.minimalist.launcher.domain.modes.DayModes
import com.minimalist.launcher.domain.modes.LockKind
import com.minimalist.launcher.domain.modes.PickupRules
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Starts and ends modes on time: study hours, unplugging, too many pickups, the bedtime
 * reminder and the morning screen once night ends. Every step is safe to repeat.
 */
class ModeCoordinator(private val container: AppContainer) {

    private val lock = Mutex()
    private val modes get() = container.modes

    /** Set by the guard service; posts the bedtime reminder. */
    var onBedtimeReminder: ((bedtimeMillis: Long) -> Unit)? = null

    suspend fun tick() = lock.withLock { tickLocked() }

    suspend fun onUserPresent() = lock.withLock {
        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()
        val state = modes.state.value
        val recent = modes.recordPickup(nowMillis)
            // Only pickups since the last Put away ended count towards the next one.
            .filter { it > (state.putAwayUntil ?: 0L) }
        if (container.currentLock() == null && PickupRules.tooMany(recent, nowMillis, state.pickupThreshold)) {
            putAwayFor(state.pickupMinutes, nowMillis)
        }
        recordFirstOpen(now)
        tickLocked()
    }

    suspend fun onPowerDisconnected() = lock.withLock {
        val minutes = modes.state.value.unplugMinutes
        if (minutes > 0) putAwayFor(minutes, System.currentTimeMillis())
        tickLocked()
    }

    /** The first unlock with the phone open (night over, or passed early) is today's first open. */
    private suspend fun recordFirstOpen(now: ZonedDateTime) {
        val lock = container.currentLock()
        val passed = container.unlocks.hasPass(UnlockTarget.Lock)
        if (lock?.kind == LockKind.Night && !passed) return
        val state = modes.state.value
        val nightNow = DayModes.night(state.dayModes, now)
        val early = (nightNow != null) ||
            (state.nightReleasedUntil != null && state.nightReleasedUntil > now.toInstant().toEpochMilli())
        modes.recordFirstOpen(now.toLocalDate().toEpochDay(), now.toInstant().toEpochMilli(), early)
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

        DayModes.reminderFor(state.dayModes, now)?.let { bedtime ->
            val bedMillis = bedtime.toInstant().toEpochMilli()
            if (state.lastReminderFor != bedMillis) {
                modes.setLastReminderFor(bedMillis)
                onBedtimeReminder?.invoke(bedMillis)
            }
        }

        container.changes.applyDue()
        val lock = container.currentLock()
        val ended = modes.syncModeLog(lock, nowMillis)
        // The phone has opened: show the morning screen once.
        if (ended == LockKind.Night.name) modes.setMorningScreenDay(now.toLocalDate().toEpochDay())
        container.notifications.onTick(lock, nowMillis)
        container.earn.saveToday(container.timeScale.value)
    }

    private suspend fun putAwayFor(minutes: Int, nowMillis: Long) {
        val length = container.timeScale.value.minutesAsSeconds(Duration.ofMinutes(minutes.toLong())).toMillis()
        modes.putAwayUntilAtLeast(nowMillis + length)
    }
}
