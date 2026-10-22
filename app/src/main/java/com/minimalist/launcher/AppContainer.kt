package com.minimalist.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.minimalist.launcher.data.apps.AppRepository
import com.minimalist.launcher.data.blocking.BlockChecker
import com.minimalist.launcher.data.blocking.Browsers
import com.minimalist.launcher.data.blocking.PhoneState
import com.minimalist.launcher.data.blocking.BlockingRepository
import com.minimalist.launcher.data.blocking.Notice
import com.minimalist.launcher.data.blocking.ProtectedApps
import com.minimalist.launcher.data.blocking.UsageTracker
import com.minimalist.launcher.data.blocking.WebsiteChecker
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.friction.FrictionRepository
import com.minimalist.launcher.data.modes.ModesRepository
import com.minimalist.launcher.data.modes.modesDataStore
import com.minimalist.launcher.data.notifications.NotificationsRepository
import com.minimalist.launcher.data.settings.SettingsRepository
import com.minimalist.launcher.data.settings.settingsDataStore
import com.minimalist.launcher.domain.LaunchableApp
import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.modes.ActiveLock
import com.minimalist.launcher.domain.modes.DayModes
import com.minimalist.launcher.domain.modes.LockKind
import com.minimalist.launcher.domain.notifications.Vip
import com.minimalist.launcher.modes.ModeCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/** Process-wide singletons, built once in [MinimalistApp]. The services share them. */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = AppDatabase.build(appContext)

    val settings = SettingsRepository(appContext.settingsDataStore)

    val apps = AppRepository(appContext, database.appPrefsDao(), applicationScope)

    /** Test mode only ever applies to debug builds. */
    val timeScale: StateFlow<TimeScale> = settings.testMode
        .map { TimeScale(testMode = BuildConfig.DEBUG && it) }
        .stateIn(applicationScope, SharingStarted.Eagerly, TimeScale.REAL)

    val blocking = BlockingRepository(database, applicationScope)

    val usage = UsageTracker(database)

    val protectedApps = ProtectedApps(appContext)

    val friction = FrictionRepository(database, applicationScope)

    val modes = ModesRepository(appContext.modesDataStore, database, applicationScope)

    val notifications = NotificationsRepository(appContext, database, modes, applicationScope).also {
        it.labelFor = ::labelFor
    }

    val browsers = Browsers(appContext)

    /** Short-lived permissions to open an app whatever blocks it, e.g. WhatsApp for an urgent message. */
    private val passes = ConcurrentHashMap<String, Long>()

    val blockChecker = BlockChecker(
        blocking = blocking,
        usage = usage,
        protectedApps = protectedApps,
        timeScale = timeScale,
        phoneState = ::phoneState,
        hasPass = { packageName -> (passes[packageName] ?: 0L) > System.currentTimeMillis() },
    )

    val coordinator = ModeCoordinator(this)

    val websites = WebsiteChecker(database, blockChecker, applicationScope)

    /** The mode running now, refreshed every few seconds. */
    val lock: StateFlow<ActiveLock?> = combine(modes.state, ticker(LOCK_REFRESH_MILLIS)) { _, _ -> currentLock() }
        .distinctUntilChanged()
        .stateIn(applicationScope, SharingStarted.Eagerly, null)

    /** What the launcher should show over everything else, if anything. */
    val notice = MutableStateFlow<Notice?>(null)

    init {
        apps.installedApps.filterNotNull().onEach {
            protectedApps.refresh()
            browsers.refresh()
        }.launchIn(applicationScope)
    }

    fun currentLock(): ActiveLock? {
        val state = modes.state.value
        return DayModes.activeLock(state.putAwayUntil, state.morningStartedAt, state.dayModes, ZonedDateTime.now(), timeScale.value)
    }

    /**
     * What still works during [lock]: calls, SMS, clock, the essentials you chose and, in the
     * morning and at night, one audio and one notes app. Android Settings stays reachable until
     * the emergency unlock exists, so a mode can never lock you out of the phone.
     */
    fun lockAllowedPackages(lock: ActiveLock): Set<String> {
        val state = modes.state.value
        return buildSet {
            addAll(protectedApps.packages)
            addAll(state.essentials)
            add(ANDROID_SETTINGS)
            if (lock.kind != LockKind.PutAway) {
                state.audioApp?.let(::add)
                state.notesApp?.let(::add)
            }
        }
    }

    private fun phoneState(): PhoneState {
        val lock = currentLock()
        val allowed = modes.state.value.allowedBrowser
        return PhoneState(
            lock = lock,
            lockAllowedPackages = lock?.let(::lockAllowedPackages).orEmpty(),
            otherBrowsers = if (allowed == null) emptySet() else browsers.packages - allowed,
            allowedBrowserLabel = allowed?.let(::labelFor).orEmpty(),
        )
    }

    fun grantPass(packageName: String, millis: Long) {
        passes[packageName] = System.currentTimeMillis() + millis
    }

    /**
     * Opens WhatsApp on [vip]'s chat with [text] typed in, past any block or mode, for one minute.
     * Returns false when this session's urgent messages are used up.
     */
    suspend fun sendUrgent(vip: Vip, text: String): Boolean {
        if (notifications.urgentLeft() <= 0) return false
        val whatsapp = NotificationsRepository.WHATSAPP_PACKAGES.firstOrNull { packageName ->
            apps.installedApps.value?.any { it.packageName == packageName } == true
        }
        val uri = Uri.parse("https://wa.me/${vip.phone}?text=${Uri.encode(text)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        whatsapp?.let {
            intent.setPackage(it)
            grantPass(it, URGENT_PASS_MILLIS)
        }
        notifications.logUrgent(vip, currentLock()?.kind?.name)
        return runCatching { appContext.startActivity(intent) }.isSuccess
    }

    /** Replies through WhatsApp's notification without opening it. Counts as an urgent message. */
    suspend fun sendQuickReply(vipId: Long, text: String): Boolean {
        val message = notifications.vipMessages.value[vipId] ?: return false
        if (notifications.urgentLeft() <= 0) return false
        val sent = notifications.sendQuickReply(message, text)
        if (sent) notifications.logUrgent(message.vip, currentLock()?.kind?.name)
        return sent
    }

    fun labelFor(packageName: String): String =
        apps.installedApps.value?.firstOrNull { it.packageName == packageName }?.label ?: packageName

    /** Opens [app] from the launcher, going through blocks and friction first. */
    fun open(app: LaunchableApp) {
        applicationScope.launch(Dispatchers.Main.immediate) {
            when (val decision = blockChecker.check(app.packageName, atOpen = true)) {
                is BlockDecision.Blocked -> {
                    usage.recordBlockedAttempt(app.packageName)
                    notice.value = Notice.Blocked(app.packageName, app.label, decision)
                }
                BlockDecision.Allowed ->
                    if (friction.needsFriction(app.packageName)) {
                        notice.value = Notice.Friction(app.packageName, app.label)
                    } else {
                        apps.launch(app)
                    }
            }
        }
    }

    /** Opens the main launcher entry of [packageName], bypassing every gate. */
    fun launchPackage(packageName: String): Boolean {
        val app = apps.installedApps.value?.firstOrNull { it.packageName == packageName } ?: return false
        return apps.launch(app)
    }

    /** The friction flow is done: start the session and open the app. */
    fun openAfterFriction(packageName: String, urge: Int, reason: String, minutes: Int) {
        applicationScope.launch(Dispatchers.Main.immediate) {
            val length = timeScale.value.dailyLimit(Duration.ofMinutes(minutes.toLong())).toMillis()
            friction.startSession(packageName, urge, reason, minutes, length)
            notice.value = null
            launchPackage(packageName)
        }
    }

    fun resistFriction(packageName: String, urge: Int?, reason: String?) {
        notice.value = null
        applicationScope.launch { friction.recordResisted(packageName, urge, reason) }
    }

    fun rateWorth(logId: Long, worth: Int?) {
        notice.value = null
        if (worth != null) applicationScope.launch { friction.rateWorth(logId, worth) }
    }

    /** Debug reset: every block, schedule, limit, friction app, mode and today's counters. */
    suspend fun resetAll() {
        blocking.clearEverything()
        friction.clear()
        modes.endAllModes()
        passes.clear()
        notice.value = null
    }

    private fun ticker(millis: Long) = flow {
        while (true) {
            emit(Unit)
            delay(millis)
        }
    }

    companion object {
        const val ANDROID_SETTINGS = "com.android.settings"
        private const val LOCK_REFRESH_MILLIS = 3_000L
        private const val URGENT_PASS_MILLIS = 60_000L
    }
}
