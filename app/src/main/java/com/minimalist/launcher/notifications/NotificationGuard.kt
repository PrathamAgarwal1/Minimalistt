package com.minimalist.launcher.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.MinimalistApp
import com.minimalist.launcher.data.notifications.NotificationsRepository
import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.domain.notifications.NotificationRouting
import com.minimalist.launcher.domain.notifications.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Takes notifications from blocked apps out of the shade and saves them, holds the rest during
 * modes and batching, lets calls, SMS and VIPs through, and keeps VIP reply actions for quick replies.
 */
class NotificationGuard : NotificationListenerService() {

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = (application as MinimalistApp).container
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val notifications = container.notifications

        val vip = if (sbn.packageName in NotificationsRepository.WHATSAPP_PACKAGES) notifications.matchVip(title) else null
        if (vip != null && text != null) {
            notifications.rememberVipMessage(vip, text, notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() })
        }

        scope.launch {
            val packageName = sbn.packageName
            val lock = container.currentLock()
            val route = NotificationRouting.route(
                isProtectedApp = container.blockChecker.isProtected(packageName),
                isOngoingOrUrgentCategory = sbn.isOngoing || notification.category?.let { it in ALWAYS_SHOW } == true,
                isVip = vip != null,
                isEssential = lock != null && packageName in container.lockAllowedPackages(lock),
                appBlocked = container.blockChecker.check(packageName, atOpen = false, includePhoneState = false) is BlockDecision.Blocked,
                lockActive = lock != null,
                batching = container.modes.state.value.batchingEnabled,
            )
            val reason = when (route) {
                Route.Show -> return@launch
                Route.HoldBlocked -> NotificationsRepository.BLOCKED
                Route.HoldMode -> NotificationsRepository.MODE
                Route.HoldBatch -> NotificationsRepository.BATCH
            }
            cancelNotification(sbn.key)
            // Group summaries carry no message of their own.
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) notifications.hold(packageName, title, text, reason)
        }
    }

    companion object {
        private val ALWAYS_SHOW = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_STOPWATCH,
        )

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            val me = ComponentName(context, NotificationGuard::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
