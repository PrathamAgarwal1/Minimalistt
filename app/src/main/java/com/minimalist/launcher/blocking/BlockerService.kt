package com.minimalist.launcher.blocking

import com.minimalist.launcher.system.TrustedClock
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.MainActivity
import com.minimalist.launcher.MinimalistApp
import com.minimalist.launcher.data.blocking.Notice
import com.minimalist.launcher.data.blocking.PartLesson
import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.domain.blocking.BlockReason
import com.minimalist.launcher.data.protection.UnlockTarget
import com.minimalist.launcher.domain.parts.AppParts
import com.minimalist.launcher.domain.friction.ScrollCheck
import com.minimalist.launcher.domain.websites.BrowserBars
import android.widget.Toast
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
        data class CheckSettings(val packageName: String) : Event
        data class CheckParts(val packageName: String) : Event
        data class LessonSnapshot(val packageName: String) : Event
    }

    private lateinit var container: AppContainer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private var addressCheck: Job? = null
    private var settingsCheck: Job? = null
    private var partsCheck: Job? = null
    private var lessonSnapshot: Job? = null
    private var lastPartBack = 0L
    /** When the app in front came to the front; the "Still worth it?" count starts here. */
    private var foregroundSince = 0L

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
        // Settings: look for pages that could switch protection off.
        if (packageName in SettingsGuard.PACKAGES) {
            settingsCheck?.cancel()
            settingsCheck = scope.launch {
                delay(SETTINGS_SETTLE_MILLIS)
                events.trySend(Event.CheckSettings(packageName))
            }
        }
        // Reels and Shorts: look for their screens inside otherwise allowed apps.
        // A playing video changes the screen constantly, so this checks at most every 350 ms
        // instead of waiting for it to settle.
        if (packageName in AppParts.PACKAGES && partsCheck?.isActive != true) {
            partsCheck = scope.launch {
                delay(PARTS_SETTLE_MILLIS)
                events.trySend(Event.CheckParts(packageName))
            }
        }
        // Teaching a part of an app: note what's on screen as you move around it.
        val lesson = container.partLesson.value
        if (lesson != null && lesson.learned == null && lesson.packageName == packageName && lessonSnapshot?.isActive != true) {
            lessonSnapshot = scope.launch {
                delay(PARTS_SETTLE_MILLIS)
                events.trySend(Event.LessonSnapshot(packageName))
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
                foregroundSince = TrustedClock.nowMillis()
                // Leaving the app being taught ends the lesson.
                container.partLesson.value?.let { lesson ->
                    if (lesson.learned == null && lesson.firstSeenAt != null && packageName != lesson.packageName) {
                        container.finishPartLesson(lesson)
                    }
                }
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
                if (session != null && session.endsAtMillis <= TrustedClock.nowMillis()) {
                    container.friction.endSession(packageName)
                    sendHome(Notice.WorthIt(session.logId, packageName, container.labelFor(packageName)))
                    return
                }
                // Unbroken use of one app: ask whether it's still worth it. Friction sessions
                // have their own end, and essentials are never asked about.
                val modes = container.modes.state.value
                if (session == null && packageName != this.packageName && packageName !in modes.essentials &&
                    ScrollCheck.isDue(foregroundSince, TrustedClock.nowMillis(), modes.scrollCheckMinutes, container.timeScale.value)
                ) {
                    sendHome(Notice.StillWorthIt(packageName, container.labelFor(packageName), modes.scrollCheckMinutes))
                }
            }
            Event.ScreenOff -> usage.switchTo(null)
            is Event.CheckSettings -> {
                if (usage.currentPackage != event.packageName) return
                if (!container.protectionActive() || container.hasSettingsPass()) return
                val root = rootInActiveWindow ?: return
                if (root.packageName?.toString() != event.packageName) return
                if (SettingsGuard.isGuarded(SettingsGuard.visibleText(root))) sendHome(Notice.SettingsClosed)
            }
            is Event.CheckParts -> {
                if (usage.currentPackage != event.packageName) return
                val blocked = container.modes.state.value.blockedParts
                if (blocked.isEmpty() || container.unlocks.hasPass(UnlockTarget.App(event.packageName))) return
                // Not while you're showing Quiet the part.
                if (container.partLesson.value?.let { it.learned == null && it.packageName == event.packageName } == true) return
                val root = rootInActiveWindow ?: return
                if (root.packageName?.toString() != event.packageName) return
                val learned = container.modes.state.value.learnedPartIds
                val ids = AppParts.ALL.filter { it.packageName == event.packageName }.flatMap { AppParts.idsFor(it, learned) }
                val visible = ids.filter { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }.toSet()
                val part = AppParts.showing(event.packageName, blocked, visible, learned) ?: return
                val now = TrustedClock.nowMillis()
                if (now - lastPartBack > PART_RETRY_MILLIS) {
                    // First a step back, which usually leaves Reels or Shorts and keeps you in the app.
                    lastPartBack = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Toast.makeText(this, "${part.label} are blocked", Toast.LENGTH_SHORT).show()
                } else {
                    val app = container.labelFor(event.packageName)
                    sendHome(
                        Notice.Blocked(event.packageName, app, BlockDecision.Blocked(listOf(BlockReason.Part(part.label, app)))),
                    )
                }
            }
            is Event.LessonSnapshot -> {
                val lesson = container.partLesson.value
                    ?.takeIf { it.learned == null && it.packageName == event.packageName } ?: return
                val root = rootInActiveWindow ?: return
                if (root.packageName?.toString() != event.packageName) return
                val ids = viewIds(root).takeIf { it.isNotEmpty() } ?: return
                val now = TrustedClock.nowMillis()
                val first = lesson.firstSeenAt ?: now
                val opening = now - first < PartLesson.OPENING_MILLIS
                container.partLesson.value = lesson.copy(
                    firstSeenAt = first,
                    atOpen = if (opening) lesson.atOpen + ids else lesson.atOpen,
                    atLeave = ids,
                )
            }
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

    /** Every view id on screen, read breadth-first and capped. */
    private fun viewIds(root: AccessibilityNodeInfo, limit: Int = 800): Set<String> {
        val ids = mutableSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val node = queue.removeFirst()
            seen++
            node.viewIdResourceName?.let(ids::add)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return ids
    }

    private suspend fun sendHome(notice: Notice) {
        // During a challenge just go back to it; it restarts on its own.
        if (!container.challengeRunning.value) container.notice.value = notice
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
        private const val SETTINGS_SETTLE_MILLIS = 300L
        private const val PARTS_SETTLE_MILLIS = 350L
        private const val PART_RETRY_MILLIS = 4_000L

        fun isEnabled(context: Context): Boolean =
            context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any {
                    val info = it.resolveInfo.serviceInfo
                    info.packageName == context.packageName && info.name == BlockerService::class.java.name
                }
    }
}
