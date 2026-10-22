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
import com.minimalist.launcher.data.earn.EarnRepository
import com.minimalist.launcher.data.friction.FrictionRepository
import com.minimalist.launcher.data.stats.StatsRepository
import com.minimalist.launcher.data.modes.ModesRepository
import com.minimalist.launcher.data.modes.modesDataStore
import com.minimalist.launcher.data.notifications.NotificationsRepository
import com.minimalist.launcher.data.protection.ChangeGate
import com.minimalist.launcher.data.protection.UnlockRepository
import com.minimalist.launcher.data.protection.UnlockRequest
import com.minimalist.launcher.data.protection.UnlockTarget
import com.minimalist.launcher.data.settings.SettingsRepository
import com.minimalist.launcher.data.settings.settingsDataStore
import com.minimalist.launcher.domain.LaunchableApp
import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.domain.blocking.BlockReason
import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.challenge.NightRules
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

    val earn = EarnRepository(appContext.modesDataStore, database, usage, applicationScope)

    val stats = StatsRepository(database, appContext.packageName)

    /** Challenge passes and the monthly emergency unlock. */
    val unlocks = UnlockRepository(database, applicationScope)

    /** True from the first problem of a challenge until it is solved or given up. */
    val challengeRunning = MutableStateFlow(false)

    /** The challenge on screen, if any. */
    val unlockRequest = MutableStateFlow<UnlockRequest?>(null)

    /** Set while Minimalist itself sends you into Android Settings to grant a missing permission. */
    @Volatile
    private var settingsPassUntil = 0L

    val blockChecker = BlockChecker(
        blocking = blocking,
        usage = usage,
        protectedApps = protectedApps,
        timeScale = timeScale,
        phoneState = ::phoneState,
        hasPass = { packageName ->
            (passes[packageName] ?: 0L) > System.currentTimeMillis() || unlocks.hasPass(UnlockTarget.App(packageName))
        },
        earnedLeft = { packageName -> earn.leftFor(packageName, timeScale.value) },
    )

    val coordinator = ModeCoordinator(this)

    val websites = WebsiteChecker(database, blockChecker, unlocks, applicationScope)

    val changes = ChangeGate(this)

    /** The mode the home screen shows, refreshed every few seconds. A pass or the emergency unlock hides it. */
    val lock: StateFlow<ActiveLock?> = combine(modes.state, ticker(LOCK_REFRESH_MILLIS)) { _, _ -> visibleLock() }
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
        return DayModes.activeLock(state.putAwayUntil, state.nightReleasedUntil, state.dayModes, ZonedDateTime.now())
    }

    /**
     * What still works during [lock]: calls, SMS, clock, the essentials you chose and, in the
     * morning and at night, one audio and one notes app. Android Settings is closed: the monthly
     * emergency unlock is the way out if something goes wrong. During a challenge only calls,
     * SMS and the clock work.
     */
    fun lockAllowedPackages(lock: ActiveLock): Set<String> {
        val state = modes.state.value
        return buildSet {
            addAll(protectedApps.packages)
            if (lock.kind == LockKind.Challenge) return@buildSet
            addAll(state.essentials)
            if (lock.kind != LockKind.PutAway) {
                state.audioApp?.let(::add)
                state.notesApp?.let(::add)
            }
        }
    }

    fun visibleLock(): ActiveLock? =
        currentLock()?.takeUnless { unlocks.hasPass(UnlockTarget.Lock) || emergencyActive() }

    /** The lock that limits apps right now: a running challenge, else a mode without a pass. */
    fun effectiveLock(): ActiveLock? {
        val now = System.currentTimeMillis()
        if (challengeRunning.value) return ActiveLock(LockKind.Challenge, now + CHALLENGE_LOCK_MILLIS)
        if (unlocks.hasPass(UnlockTarget.Lock, now)) return null
        return currentLock()
    }

    fun emergencyActive(): Boolean = unlocks.emergencyUntil() != null

    /** Whether there is anything to protect, so the Settings guard has reason to act. */
    fun protectionActive(): Boolean {
        if (emergencyActive()) return false
        val rules = blocking.rules.value
        val state = modes.state.value
        val now = System.currentTimeMillis()
        return currentLock() != null ||
            rules.blocks.values.any { it > now } ||
            rules.schedules.any { it.enabled } ||
            rules.limits.isNotEmpty() ||
            friction.apps.value.isNotEmpty() ||
            websites.customDomains.value.isNotEmpty() ||
            state.allowedBrowser != null ||
            state.dayModes.nightEnabled ||
            earn.state.value.apps.isNotEmpty()
    }

    /** Minimalist is sending you to a Settings page to switch something on. */
    fun grantSettingsPass() {
        settingsPassUntil = System.currentTimeMillis() + SETTINGS_PASS_MILLIS
    }

    fun hasSettingsPass(): Boolean = settingsPassUntil > System.currentTimeMillis()

    private fun phoneState(): PhoneState {
        if (emergencyActive()) return PhoneState()
        val lock = effectiveLock()
        val allowed = modes.state.value.allowedBrowser
        return PhoneState(
            lock = lock,
            lockAllowedPackages = lock?.let(::lockAllowedPackages).orEmpty(),
            otherBrowsers = if (allowed == null) emptySet() else browsers.packages - allowed,
            allowedBrowserLabel = allowed?.let(::labelFor).orEmpty(),
        )
    }

    /** The way in early from a block screen: a challenge sized to the time left. */
    fun requestUnlock(blocked: Notice.Blocked) {
        val primary = blocked.decision.primary
        val target = when (primary) {
            is BlockReason.Website -> UnlockTarget.Domain(blocked.label)
            is BlockReason.Locked -> UnlockTarget.Lock
            else -> UnlockTarget.App(blocked.packageName)
        }
        notice.value = null
        unlockRequest.value = UnlockRequest(
            target = target,
            label = blocked.label,
            timeLeftMillis = blocked.decision.untilMillis - System.currentTimeMillis(),
            openPackage = blocked.packageName,
        )
    }

    /**
     * The way past a mode early, from its lock screen. Night has its own curve: harder the later
     * it is, and in the last two hours before opening, solving it opens the phone for the morning.
     */
    fun requestUnlock(lock: ActiveLock) {
        applicationScope.launch(Dispatchers.Main.immediate) {
            val now = ZonedDateTime.now()
            val nowMillis = now.toInstant().toEpochMilli()
            val window = if (lock.kind == LockKind.Night) DayModes.night(modes.state.value.dayModes, now) else null
            val night = window?.let {
                val start = it.start.toInstant().toEpochMilli()
                NightRules.challenge(
                    sinceBedtimeMillis = nowMillis - start,
                    untilOpeningMillis = lock.untilMillis - nowMillis,
                    passesTonight = unlocks.nightPassesSince(start),
                    earlyUnlocksPastWeek = unlocks.earlyUnlocksPastWeek(),
                    scale = timeScale.value,
                )
            }
            unlockRequest.value = UnlockRequest(
                target = UnlockTarget.Lock,
                label = lock.kind.label,
                timeLeftMillis = lock.untilMillis - nowMillis,
                openPackage = null,
                night = night,
                nightOpensAt = window?.opens?.toInstant()?.toEpochMilli(),
            )
        }
    }

    /** Solved: grant the pass (the block underneath keeps its end time) and open the app. */
    fun finishUnlock(request: UnlockRequest, reason: String, problems: Int, passMinutes: Int) {
        applicationScope.launch(Dispatchers.Main.immediate) {
            val night = request.night
            val opensAt = request.nightOpensAt
            when {
                night != null && night.opensMorning && opensAt != null -> {
                    unlocks.grantChallengePass(
                        UnlockTarget.Lock, reason, problems,
                        Duration.ofMillis((opensAt - System.currentTimeMillis()).coerceAtLeast(0)),
                        kind = UnlockRepository.MORNING,
                    )
                    modes.releaseNight(opensAt, java.time.LocalDate.now().toEpochDay())
                }
                night != null -> unlocks.grantChallengePass(
                    UnlockTarget.Lock, reason, problems, timeScale.value.pass(night.passMinutes), kind = UnlockRepository.NIGHT,
                )
                else -> unlocks.grantChallengePass(request.target, reason, problems, timeScale.value.pass(passMinutes))
            }
            challengeRunning.value = false
            unlockRequest.value = null
            request.openPackage?.let(::launchPackage)
        }
    }

    fun abandonUnlock() {
        challengeRunning.value = false
        unlockRequest.value = null
    }

    /** Lifts everything for 15 minutes, once a calendar month. */
    suspend fun useEmergency(): Boolean {
        val used = unlocks.useEmergency(timeScale.value)
        if (used) {
            notice.value = null
            abandonUnlock()
        }
        return used
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

    /** Debug reset: every block, schedule, limit, friction app, mode, pass, queued change and today's counters. */
    suspend fun resetAll() {
        blocking.clearEverything()
        friction.clear()
        modes.endAllModes(DayModes.nightUntil(modes.state.value.dayModes, ZonedDateTime.now())?.toInstant()?.toEpochMilli())
        passes.clear()
        unlocks.endAllPasses()
        changes.clearPending()
        challengeRunning.value = false
        notice.value = null
    }

    private fun ticker(millis: Long) = flow {
        while (true) {
            emit(Unit)
            delay(millis)
        }
    }

    companion object {
        private const val CHALLENGE_LOCK_MILLIS = 3_600_000L
        private const val SETTINGS_PASS_MILLIS = 90_000L
        private const val LOCK_REFRESH_MILLIS = 3_000L
        private const val URGENT_PASS_MILLIS = 60_000L
    }
}
