package com.minimalist.launcher

import android.content.Context
import com.minimalist.launcher.data.apps.AppRepository
import com.minimalist.launcher.data.blocking.BlockChecker
import com.minimalist.launcher.data.blocking.BlockNotice
import com.minimalist.launcher.data.blocking.BlockingRepository
import com.minimalist.launcher.data.blocking.ProtectedApps
import com.minimalist.launcher.data.blocking.UsageTracker
import com.minimalist.launcher.data.db.AppDatabase
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

/** Process-wide singletons, built once in [MinimalistApp]. The accessibility service shares them. */
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

    val blockChecker = BlockChecker(blocking, usage, protectedApps, timeScale)

    /** The block screen the launcher should show, if any. */
    val blockNotice = MutableStateFlow<BlockNotice?>(null)

    init {
        apps.installedApps.filterNotNull().onEach { protectedApps.refresh() }.launchIn(applicationScope)
    }

    fun labelFor(packageName: String): String =
        apps.installedApps.value?.firstOrNull { it.packageName == packageName }?.label ?: packageName

    /** Opens [app] from the launcher, or shows the block screen instead of a flash of the app. */
    fun open(app: LaunchableApp) {
        applicationScope.launch(Dispatchers.Main.immediate) {
            when (val decision = blockChecker.check(app.packageName, atOpen = true)) {
                is BlockDecision.Blocked -> {
                    usage.recordBlockedAttempt(app.packageName)
                    blockNotice.value = BlockNotice(app.packageName, app.label, decision)
                }
                BlockDecision.Allowed -> apps.launch(app)
            }
        }
    }
}
