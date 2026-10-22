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
import com.minimalist.launcher.data.blocking.Notice
import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.domain.blocking.BlockReason
import com.minimalist.launcher.domain.websites.BrowserBars
import com.minimalist.launcher.system.HomeRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
        data class CheckAddress(val packageName: String) : Event
    }

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private var addressCheck: Job? = null

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
        val packageName = event.packageName?.toString() ?: return
        if (!::container.isInitialized) return
        // Browsers: read the address bar shortly after the page settles.
        if (packageName in BrowserBars.VIEW_IDS) {
            addressCheck?.cancel()
            addressCheck = scope.launch {
                delay(ADDRESS_SETTLE_MILLIS)
                events.trySend(Event.CheckAddress(packageName))
            }
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (container.protectedApps.isOverlay(packageName)) return
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
                // Back home: any friction session is over, so ask whether it was worth it.
                if (packageName == this.packageName) endFrictionSessions()
                if (checker.isProtected(packageName)) return
                when (val decision = checker.check(packageName, atOpen = true)) {
                    is BlockDecision.Blocked -> {
                        usage.recordBlockedAttempt(packageName)
                        sendHome(Notice.Blocked(packageName, container.labelFor(packageName), decision))
                    }
                    BlockDecision.Allowed ->
                        if (container.friction.needsFriction(packageName)) {
                            sendHome(Notice.Friction(packageName, container.labelFor(packageName)))
                        } else {
                            usage.recordOpen(packageName)
                        }
                }
            }
            Event.Tick -> {
                val packageName = usage.currentPackage ?: return
                usage.flush()
                if (checker.isProtected(packageName)) return
                // A schedule started, a block was added, or the daily time ran out mid-session.
                val decision = checker.check(packageName, atOpen = false)
                if (decision is BlockDecision.Blocked) {
                    container.friction.endSession(packageName)
                    sendHome(Notice.Blocked(packageName, container.labelFor(packageName), decision))
                    return
                }
                // The chosen session length is up.
                val session = container.friction.sessionFor(packageName)
                if (session != null && session.endsAtMillis <= System.currentTimeMillis()) {
                    container.friction.endSession(packageName)
                    sendHome(Notice.WorthIt(session.logId, packageName, container.labelFor(packageName)))
                }
            }
            Event.ScreenOff -> usage.switchTo(null)
            is Event.CheckAddress -> {
                if (usage.currentPackage != event.packageName) return
                val blocked = container.websites.check(addressBarText(event.packageName)) ?: return
                val domain = (blocked.primary as? BlockReason.Website)?.domain ?: return
                sendHome(Notice.Blocked(event.packageName, domain, blocked))
            }
        }
    }

    private suspend fun endFrictionSessions() {
        val ended = container.friction.allSessions().mapNotNull { container.friction.endSession(it.packageName) }
        ended.maxByOrNull { it.endsAtMillis }?.let {
            container.notice.value = Notice.WorthIt(it.logId, it.packageName, container.labelFor(it.packageName))
        }
    }

    private suspend fun sendHome(notice: Notice) {
        container.notice.value = notice
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (!HomeRole.isDefaultHome(this)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        container.usage.switchTo(this.packageName)
    }

    /** The address bar's text, unless someone is typing in it. */
    private fun addressBarText(packageName: String): String? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != packageName) return null
        for (id in BrowserBars.VIEW_IDS[packageName].orEmpty()) {
            val node = root.findAccessibilityNodeInfosByViewId(id).firstOrNull() ?: continue
            if (node.isFocused) return null
            return node.text?.toString()
        }
        return null
    }

    private fun foregroundPackage(): String? =
        rootInActiveWindow?.packageName?.toString()?.takeUnless { container.protectedApps.isOverlay(it) }

    companion object {
        private const val TICK_MILLIS = 5_000L
        private const val ADDRESS_SETTLE_MILLIS = 400L

        fun isEnabled(context: Context): Boolean =
            context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any {
                    val info = it.resolveInfo.serviceInfo
                    info.packageName == context.packageName && info.name == BlockerService::class.java.name
                }
    }
}
