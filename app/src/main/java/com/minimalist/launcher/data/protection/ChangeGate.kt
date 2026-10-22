package com.minimalist.launcher.data.protection

import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.data.db.LetterEntity
import com.minimalist.launcher.data.db.PendingChangeEntity
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.domain.earn.Earning
import com.minimalist.launcher.domain.protection.Protection
import com.minimalist.launcher.ui.blocking.BlockingText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A weakening change waiting for its letter to be read before it joins the queue. */
data class ChangeRequest(val change: Change, val description: String, val letter: LetterEntity?)

/**
 * Every settings change passes through here. Strengthening and neutral ones apply at once;
 * weakening ones show a letter, then wait out the cooldown (24 hours, a minute in test mode).
 */
class ChangeGate(private val container: AppContainer) {

    private val dao = container.database.protectionDao()
    private val applyLock = Mutex()

    private val _request = MutableStateFlow<ChangeRequest?>(null)
    val request = _request.asStateFlow()

    val pending: Flow<List<PendingChangeEntity>> = dao.observePending()
    val letters: Flow<List<LetterEntity>> = dao.observeLetters()

    fun submit(change: Change) {
        container.applicationScope.launch {
            if (weakens(change)) {
                _request.value = ChangeRequest(change, describe(change), dao.randomLetter())
            } else {
                apply(change)
            }
        }
    }

    /** The letter was read: start the cooldown. */
    suspend fun confirm(request: ChangeRequest) {
        val now = System.currentTimeMillis()
        dao.insertPending(
            PendingChangeEntity(
                description = request.description,
                payload = ChangeCodec.encode(request.change),
                queuedAt = now,
                appliesAt = Protection.cooldownEndsAt(now, container.timeScale.value),
            ),
        )
        _request.value = null
    }

    fun dismiss() {
        _request.value = null
    }

    suspend fun cancel(id: Long) = dao.deletePending(id)

    /** Applies every queued change whose cooldown is over. Safe to call often. */
    suspend fun applyDue() = applyLock.withLock {
        dao.duePending(System.currentTimeMillis()).forEach { pending ->
            ChangeCodec.decode(pending.payload)?.let { apply(it) }
            dao.deletePending(pending.id)
        }
    }

    suspend fun clearPending() = dao.clearPending()

    suspend fun writeLetter(text: String) {
        if (text.isNotBlank()) dao.insertLetter(LetterEntity(text = text.trim(), writtenAt = System.currentTimeMillis()))
    }

    suspend fun deleteLetter(id: Long) = dao.deleteLetter(id)

    private suspend fun weakens(change: Change): Boolean {
        val rules = container.blocking.rules.value
        val modes = container.modes.state.value
        return when (change) {
            is Change.RemoveBlock -> (rules.blocks[change.packageName] ?: 0L) > System.currentTimeMillis()
            is Change.SaveLimit -> Protection.limitWeakens(rules.limits[change.limit.packageName], change.limit.takeIf { it.maxMinutes != null || it.maxOpens != null })
            is Change.RemoveLimit -> rules.limits.containsKey(change.packageName)
            is Change.SaveSchedule -> Protection.scheduleWeakens(rules.schedules.firstOrNull { it.id == change.schedule.id && change.schedule.id != 0L }, change.schedule)
            is Change.SetScheduleEnabled -> !change.enabled && rules.schedules.any { it.id == change.id && it.enabled }
            is Change.DeleteSchedule -> rules.schedules.any { it.id == change.id && it.enabled }
            is Change.RemoveFriction -> change.packageName in container.friction.apps.value
            is Change.SetDayModes -> Protection.dayModesWeaken(modes.dayModes, change.config)
            is Change.SetUnplug -> Protection.unplugWeakens(modes.unplugMinutes, change.minutes)
            is Change.SetPickups -> Protection.pickupsWeaken(modes.pickupThreshold, modes.pickupMinutes, change.threshold, change.minutes)
            is Change.SetStudyWindows -> Protection.studyWindowsWeaken(modes.studyWindows, change.windows)
            is Change.SetEssentials -> Protection.allowedAppsWeaken(modes.essentials, change.packages)
            is Change.SetAudioApp -> Protection.modeAppWeakens(modes.audioApp, change.packageName)
            is Change.SetNotesApp -> Protection.modeAppWeakens(modes.notesApp, change.packageName)
            is Change.AddVip -> true
            is Change.SetBatching -> Protection.batchingWeakens(modes.batchingEnabled, modes.batchTimes, change.enabled, change.times)
            is Change.SetUrgentPerSession -> Protection.urgentWeakens(modes.urgentPerSession, change.count)
            is Change.SetAllowedBrowser -> Protection.browserWeakens(modes.allowedBrowser, change.packageName)
            is Change.RemoveDomain -> change.domain in container.websites.customDomains.value
            is Change.SetTestMode -> Protection.testModeWeakens(change.enabled)
            is Change.SetFrictionBase -> Protection.frictionBaseWeakens(modes.frictionBaseSeconds, change.seconds)
            is Change.SetEarnRules -> Earning.rulesWeaken(container.earn.state.value.rules, change.rules)
            is Change.RemoveEarnedApp -> change.packageName in container.earn.state.value.apps
        }
    }

    private suspend fun apply(change: Change) {
        val modes = container.modes
        when (change) {
            is Change.RemoveBlock -> container.blocking.removeBlock(change.packageName)
            is Change.SaveLimit -> container.blocking.saveLimit(change.limit)
            is Change.RemoveLimit -> container.blocking.removeLimit(change.packageName)
            is Change.SaveSchedule -> container.blocking.saveSchedule(change.schedule)
            is Change.SetScheduleEnabled -> container.blocking.setScheduleEnabled(change.id, change.enabled)
            is Change.DeleteSchedule -> container.blocking.deleteSchedule(change.id)
            is Change.RemoveFriction -> container.friction.removeApp(change.packageName)
            is Change.SetDayModes -> modes.setDayModes(change.config)
            is Change.SetUnplug -> modes.setUnplugMinutes(change.minutes)
            is Change.SetPickups -> modes.setPickups(change.threshold, change.minutes)
            is Change.SetStudyWindows -> modes.setStudyWindows(change.windows)
            is Change.SetEssentials -> modes.setEssentials(change.packages)
            is Change.SetAudioApp -> modes.setAudioApp(change.packageName)
            is Change.SetNotesApp -> modes.setNotesApp(change.packageName)
            is Change.AddVip -> container.notifications.addVip(change.name, change.phone)
            is Change.SetBatching -> modes.setBatching(change.enabled, change.times)
            is Change.SetUrgentPerSession -> modes.setUrgentPerSession(change.count)
            is Change.SetAllowedBrowser -> modes.setAllowedBrowser(change.packageName)
            is Change.RemoveDomain -> container.websites.remove(change.domain)
            is Change.SetTestMode -> container.settings.setTestMode(change.enabled)
            is Change.SetFrictionBase -> modes.setFrictionBase(change.seconds)
            is Change.SetEarnRules -> container.earn.setRules(change.rules)
            is Change.RemoveEarnedApp -> container.earn.removeApp(change.packageName)
        }
    }

    private fun describe(change: Change): String {
        val label = container::labelFor
        return when (change) {
            is Change.RemoveBlock -> "Remove the block on ${label(change.packageName)}"
            is Change.SaveLimit -> "Loosen the daily limit on ${label(change.limit.packageName)}" +
                (BlockingText.limit(change.limit).takeIf { it.isNotEmpty() }?.let { " to $it" } ?: "")
            is Change.RemoveLimit -> "Remove the daily limit on ${label(change.packageName)}"
            is Change.SaveSchedule -> "Loosen the schedule “${change.schedule.name}”"
            is Change.SetScheduleEnabled -> "Turn off a schedule"
            is Change.DeleteSchedule -> "Delete a schedule"
            is Change.RemoveFriction -> "Stop slowing down ${label(change.packageName)}"
            is Change.SetDayModes -> if (change.config.nightEnabled) "Loosen night mode" else "Turn off night mode"
            is Change.SetUnplug -> if (change.minutes == 0) {
                "Stop Put away after unplugging"
            } else {
                "Shorten Put away after unplugging to ${BlockingText.minutes(change.minutes)}"
            }
            is Change.SetPickups -> "Loosen Put away after pickups"
            is Change.SetStudyWindows -> "Remove study hours"
            is Change.SetEssentials -> "Let ${(change.packages - container.modes.state.value.essentials).joinToString { label(it) }} work in modes"
            is Change.SetAudioApp -> "Let ${change.packageName?.let(label)} play at night"
            is Change.SetNotesApp -> "Let ${change.packageName?.let(label)} work at night"
            is Change.AddVip -> "Add ${change.name} as a VIP"
            is Change.SetBatching -> if (change.enabled) {
                "Deliver notifications at ${change.times.joinToString { ScheduleRules.formatMinute(it) }}"
            } else {
                "Stop batching notifications"
            }
            is Change.SetUrgentPerSession -> "Allow ${change.count} urgent messages per session"
            is Change.SetAllowedBrowser -> change.packageName?.let { "Switch the allowed browser to ${label(it)}" } ?: "Allow every browser"
            is Change.RemoveDomain -> "Unblock ${change.domain}"
            is Change.SetTestMode -> "Turn on test mode"
            is Change.SetFrictionBase -> "Start friction countdowns at ${change.seconds} s"
            is Change.SetEarnRules -> "Make earning screen time easier"
            is Change.RemoveEarnedApp -> "Let ${label(change.packageName)} open without earned time"
        }
    }
}
