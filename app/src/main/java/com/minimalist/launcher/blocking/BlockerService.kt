package com.minimalist.launcher.blocking

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.MainActivity
import com.minimalist.launcher.MinimalistApp
import com.minimalist.launcher.data.blocking.BlockNotice
import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.system.HomeRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches which app is in front. When one is blocked it goes home and the launcher shows the
 * block screen. Also measures screen time and opens for daily limits.
 */
class BlockerService : AccessibilityService() {

    private sealed interface Event {
        data class Foreground(val packageName: String) : Event
        data object Tick : Event
        data object ScreenOff : Event
    }

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = Channel<Event>(Channel.UNLIMITED)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> events.trySend(Event.ScreenOff)
                // After unlocking, whatever is in front starts a new session.
                Intent.ACTION_USER_PRESENT -> foregroundPackage()?.let { events.trySend(Event.Foreground(it)) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        container = (application as MinimalistApp).container
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { for (event in events) handle(event) }
        scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                events.trySend(Event.Tick)
            }
        }
        foregroundPackage()?.let { events.trySend(Event.Foreground(it)) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (!::container.isInitialized || container.protectedApps.isOverlay(packageName)) return
        events.trySend(Event.Foreground(packageName))
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        if (::container.isInitialized) {
            val usage = container.usage
            container.applicationScope.launch { usage.switchTo(null) }
        }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun handle(event: Event) {
        val usage = container.usage
        val checker = container.blockChecker
        when (event) {
            is Event.Foreground -> {
                val packageName = event.packageName
                if (packageName == usage.currentPackage) return
                usage.switchTo(packageName)
                if (checker.isProtected(packageName)) return
                when (val decision = checker.check(packageName, atOpen = true)) {
                    is BlockDecision.Blocked -> {
                        usage.recordBlockedAttempt(packageName)
                        sendHome(packageName, decision)
                    }
                    BlockDecision.Allowed -> usage.recordOpen(packageName)
                }
            }
            Event.Tick -> {
                val packageName = usage.currentPackage ?: return
                usage.flush()
                if (checker.isProtected(packageName)) return
                // A schedule started, a block was added, or the daily time ran out mid-session.
                val decision = checker.check(packageName, atOpen = false)
                if (decision is BlockDecision.Blocked) sendHome(packageName, decision)
            }
            Event.ScreenOff -> usage.switchTo(null)
        }
    }

    private suspend fun sendHome(packageName: String, decision: BlockDecision.Blocked) {
        container.blockNotice.value = BlockNotice(packageName, container.labelFor(packageName), decision)
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (!HomeRole.isDefaultHome(this)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        container.usage.switchTo(this.packageName)
    }

    private fun foregroundPackage(): String? =
        rootInActiveWindow?.packageName?.toString()?.takeUnless { container.protectedApps.isOverlay(it) }

    companion object {
        private const val TICK_MILLIS = 5_000L

        fun isEnabled(context: Context): Boolean =
            context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any {
                    val info = it.resolveInfo.serviceInfo
                    info.packageName == context.packageName && info.name == BlockerService::class.java.name
                }
    }
}
