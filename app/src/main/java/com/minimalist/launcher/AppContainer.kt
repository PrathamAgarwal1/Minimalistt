package com.minimalist.launcher

import android.content.Context
import com.minimalist.launcher.data.apps.AppRepository
import com.minimalist.launcher.data.blocking.BlockChecker
import com.minimalist.launcher.data.blocking.BlockingRepository
import com.minimalist.launcher.data.blocking.Notice
import com.minimalist.launcher.data.blocking.ProtectedApps
import com.minimalist.launcher.data.blocking.UsageTracker
import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.data.friction.FrictionRepository
import com.minimalist.launcher.data.settings.SettingsRepository
import com.minimalist.launcher.data.settings.settingsDataStore
import com.minimalist.launcher.domain.LaunchableApp
import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.domain.blocking.TimeScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration

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

    val blockChecker = BlockChecker(blocking, usage, protectedApps, timeScale)

    /** What the launcher should show over everything else, if anything. */
    val notice = MutableStateFlow<Notice?>(null)

    init {
        apps.installedApps.filterNotNull().onEach { protectedApps.refresh() }.launchIn(applicationScope)
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

    /** Debug reset: every block, schedule, limit, friction app and today's counters. */
    suspend fun resetAll() {
        blocking.clearEverything()
        friction.clear()
        notice.value = null
    }
}
