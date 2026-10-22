package com.minimalist.launcher.data.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.minimalist.launcher.MainActivity
import com.minimalist.launcher.R
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.db.HeldNotificationEntity
import com.minimalist.launcher.data.db.UrgentLogEntity
import com.minimalist.launcher.data.db.VipContactEntity
import com.minimalist.launcher.data.modes.ModesRepository
import com.minimalist.launcher.domain.modes.ActiveLock
import com.minimalist.launcher.domain.notifications.BatchTimes
import com.minimalist.launcher.domain.notifications.Vip
import com.minimalist.launcher.domain.notifications.VipMatcher
import com.minimalist.launcher.modes.GuardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The latest message from a VIP, with WhatsApp's own reply action so it can be answered here. */
data class VipMessage(
    val vip: Vip,
    val text: String,
    val atMillis: Long,
    val reply: Notification.Action?,
)

class NotificationsRepository(
    private val context: Context,
    database: AppDatabase,
    private val modes: ModesRepository,
    scope: CoroutineScope,
) {
    private val dao = database.notificationsDao()
    private val deliverLock = Mutex()

    val vips: StateFlow<List<Vip>> = dao.observeVips()
        .map { list -> list.map { Vip(it.id, it.name, it.phone) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _vipMessages = MutableStateFlow<Map<Long, VipMessage>>(emptyMap())
    val vipMessages: StateFlow<Map<Long, VipMessage>> = _vipMessages.asStateFlow()

    val held: Flow<List<HeldNotificationEntity>> = dao.observeRecent()

    fun matchVip(title: String?): Vip? = VipMatcher.match(title, vips.value)

    fun rememberVipMessage(vip: Vip, text: String, reply: Notification.Action?) {
        _vipMessages.value = _vipMessages.value + (vip.id to VipMessage(vip, text, System.currentTimeMillis(), reply))
    }

    suspend fun hold(packageName: String, title: String?, text: String?, reason: String) {
        dao.hold(
            HeldNotificationEntity(
                packageName = packageName,
                title = title,
                text = text,
                postedAt = System.currentTimeMillis(),
                reason = reason,
                // Blocked apps' notifications are only saved, never delivered later.
                delivered = reason == BLOCKED,
            ),
        )
    }

    /** Delivers what a finished mode held back, and batches when their time comes. */
    suspend fun onTick(lock: ActiveLock?, nowMillis: Long) = deliverLock.withLock {
        if (lock != null) return@withLock
        deliver(MODE, "While you were away")
        val state = modes.state.value
        if (state.batchingEnabled && BatchTimes.due(state.lastBatchAt, ZonedDateTime.now(), state.batchTimes)) {
            modes.setLastBatchAt(nowMillis)
            deliver(BATCH, "Your notifications")
        }
        dao.pruneHeld(nowMillis - Duration.ofDays(14).toMillis())
    }

    private suspend fun deliver(reason: String, title: String) {
        val items = dao.undelivered(reason)
        if (items.isEmpty()) return
        dao.markDelivered(items.map { it.id })
        postSummary(title, items)
    }

    private fun postSummary(title: String, items: List<HeldNotificationEntity>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        GuardService.createChannels(context)
        val labels = items.groupingBy { labelFor(it.packageName) }.eachCount()
        val line = labels.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${it.value}" }
        val style = Notification.InboxStyle().setBigContentTitle("$title: ${items.size}")
        items.takeLast(7).forEach { item ->
            style.addLine("${labelFor(item.packageName)}: ${listOfNotNull(item.title, item.text).joinToString(" — ")}")
        }
        val open = PendingIntent.getActivity(
            context,
            REQUEST_HELD,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_HELD),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, GuardService.SUMMARY_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$title: ${items.size}")
            .setContentText(line)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(SUMMARY_ID, notification)
    }

    suspend fun clearHeld() = dao.clearHeld()

    suspend fun addVip(name: String, phone: String) {
        val digits = VipMatcher.phoneDigits(phone)
        if (name.isNotBlank() && digits.length >= 8) dao.insertVip(VipContactEntity(name = name.trim(), phone = digits))
    }

    suspend fun removeVip(id: Long) = dao.deleteVip(id)

    // Urgent messages

    /** How many urgent messages are left in this mode session (or today, outside modes). */
    suspend fun urgentLeft(): Int {
        val since = modes.currentModeStartedAt() ?: startOfToday()
        return (modes.state.value.urgentPerSession - dao.urgentSince(since)).coerceAtLeast(0)
    }

    suspend fun logUrgent(vip: Vip, mode: String?) {
        dao.logUrgent(UrgentLogEntity(vipName = vip.name, sentAt = System.currentTimeMillis(), mode = mode))
    }

    fun urgentToday(): Flow<Int> = dao.observeUrgentSince(startOfToday())

    /** Answers through WhatsApp's own notification reply action, without opening WhatsApp. */
    fun sendQuickReply(message: VipMessage, text: String): Boolean {
        val action = message.reply ?: return false
        val inputs = action.remoteInputs ?: return false
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val fillIn = Intent()
        RemoteInput.addResultsToIntent(inputs, fillIn, results)
        return runCatching { action.actionIntent.send(context, 0, fillIn) }.isSuccess
    }

    var labelFor: (String) -> String = { it }

    companion object {
        const val BLOCKED = "blocked"
        const val MODE = "mode"
        const val BATCH = "batch"
        private const val SUMMARY_ID = 2
        private const val REQUEST_HELD = 10

        val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        private fun startOfToday() = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
