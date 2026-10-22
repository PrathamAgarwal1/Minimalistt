package com.minimalist.launcher.modes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.MainActivity
import com.minimalist.launcher.MinimalistApp
import com.minimalist.launcher.R
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.modes.ActiveLock
import com.minimalist.launcher.domain.modes.LockKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps modes on time while the launcher is in the background: counts pickups, notices
 * unplugging, runs study hours and batches, and lays the red tint over the screen at night.
 */
class GuardService : Service() {

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tint: View? = null
    private var lastStatus: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val coordinator = container.coordinator
            scope.launch {
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> coordinator.onUserPresent()
                    Intent.ACTION_POWER_DISCONNECTED -> coordinator.onPowerDisconnected()
                    else -> coordinator.tick()
                }
                refresh()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = (application as MinimalistApp).container
        createChannels(this)
        val notification = statusNotification(null)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(STATUS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(STATUS_ID, notification)
        }
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        running = true
        scope.launch {
            while (isActive) {
                container.coordinator.tick()
                refresh()
                delay(TICK_MILLIS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(receiver) }
        removeTint()
        scope.cancel()
        super.onDestroy()
    }

    private fun refresh() {
        val lock = container.currentLock()
        if (lock?.kind == LockKind.Night && Settings.canDrawOverlays(this)) showTint() else removeTint()
        val status = statusText(lock)
        if (status != lastStatus) {
            lastStatus = status
            getSystemService(NotificationManager::class.java).notify(STATUS_ID, statusNotification(lock))
        }
    }

    private fun statusText(lock: ActiveLock?): String =
        lock?.let { "${it.kind.label} · ${DurationFormat.remaining(it.untilMillis - System.currentTimeMillis())} left" }
            ?: "Keeping your modes on time"

    private fun statusNotification(lock: ActiveLock?): Notification =
        Notification.Builder(this, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Minimalist")
            .setContentText(statusText(lock))
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()

    private fun showTint() {
        if (tint != null) return
        val view = View(this).apply { setBackgroundColor(TINT_COLOR) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Below Android's limit for windows that touches pass through.
            alpha = 0.35f
        }
        runCatching { getSystemService(WindowManager::class.java).addView(view, params) }
            .onSuccess { tint = view }
    }

    private fun removeTint() {
        tint?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        tint = null
    }

    companion object {
        private const val TICK_MILLIS = 15_000L
        private const val STATUS_ID = 1
        private const val TINT_COLOR = 0xFF6B1A00.toInt()
        const val STATUS_CHANNEL = "status"
        const val SUMMARY_CHANNEL = "summaries"

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, GuardService::class.java)) }
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(STATUS_CHANNEL, "Status", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "The quiet note that keeps modes running on time."
                    setShowBadge(false)
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(SUMMARY_CHANNEL, "Summaries", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Held notifications, delivered together."
                },
            )
        }
    }
}

/** Starts the guard service after a reboot or an update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            GuardService.start(context)
        }
    }
}
