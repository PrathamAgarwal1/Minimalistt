package com.minimalist.launcher.ui.home

import android.app.AlarmManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.blocking.BlockerService
import com.minimalist.launcher.data.settings.StoredFavouriteOrder
import com.minimalist.launcher.domain.FavouriteOrder
import com.minimalist.launcher.domain.LaunchableApp
import com.minimalist.launcher.domain.blocking.LimitLeft
import com.minimalist.launcher.system.BatteryState
import com.minimalist.launcher.system.batteryState
import com.minimalist.launcher.system.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class HomeState(
    val loaded: Boolean = false,
    val favourites: List<LaunchableApp> = emptyList(),
)

/** The quiet line under the date: screen time so far, the next alarm, the battery. */
data class Glance(
    val screenTimeMillis: Long? = null,
    val nextAlarm: LocalDateTime? = null,
    val battery: BatteryState? = null,
    /** Something is set to be blocked, but the blocking service is off. */
    val blockingOff: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val apps = container.apps
    private val settings = container.settings
    private val alarmManager = container.appContext.getSystemService(AlarmManager::class.java)

    val appearance = container.settings.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.minimalist.launcher.data.settings.Appearance())

    val now: StateFlow<LocalDateTime> = minuteTicker()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    private val today = now.map { it.toLocalDate().toEpochDay() }.distinctUntilChanged()

    val glance: StateFlow<Glance> = today
        .flatMapLatest { day -> container.usage.totalForegroundToday(container.appContext.packageName, day) }
        .combine(now) { screenTime, _ -> screenTime }
        .combine(batteryState(container.appContext)) { screenTime, battery ->
            Glance(
                screenTimeMillis = screenTime.takeIf { BlockerService.isEnabled(container.appContext) },
                nextAlarm = alarmManager.nextAlarmClock?.triggerTime?.let {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                },
                battery = battery,
                blockingOff = !BlockerService.isEnabled(container.appContext) && container.protectionActive(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Glance())

    val state: StateFlow<HomeState> = combine(
        apps.installedApps.filterNotNull(),
        apps.prefs,
        settings.favouriteOrder,
        today,
    ) { installed, prefs, stored, day ->
        val favouriteKeys = prefs.filter { it.favourite }.map { it.appKey }.toSet()
        val arranged = StoredFavouriteOrder(
            keys = FavouriteOrder.arrange(favouriteKeys, stored.keys, stored.epochDay, day),
            epochDay = day,
        )
        if (arranged != stored) settings.setFavouriteOrder(arranged)

        val byKey = installed.associateBy { it.key }
        HomeState(
            loaded = true,
            favourites = arranged.keys.mapNotNull(byKey::get),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** "12m left" beside favourites with a daily limit, refreshed every minute. */
    val limitHints: StateFlow<Map<String, String>> = combine(state, container.blocking.rules, now, container.timeScale) { home, rules, _, scale ->
        Triple(home.favourites, rules, scale)
    }.mapLatest { (favourites, rules, scale) ->
        favourites.mapNotNull { app ->
            val limit = rules.limits[app.packageName] ?: return@mapNotNull null
            LimitLeft.hint(limit, container.usage.usageToday(app.packageName), scale)?.let { app.packageName to it }
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun launch(app: LaunchableApp) = container.open(app)

    fun openAppInfo(app: LaunchableApp) = apps.openAppInfo(app)

    /** Phone, messages, the clock and the like can never be blocked. */
    fun canBlock(app: LaunchableApp) = !container.blockChecker.isProtected(app.packageName)

    fun removeFavourite(app: LaunchableApp) {
        viewModelScope.launch { apps.setFavourite(app, false) }
    }

    fun hide(app: LaunchableApp) {
        viewModelScope.launch { apps.setHidden(app.key, app.packageName, true) }
    }

    fun uninstallApp(app: LaunchableApp) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_DELETE,
            android.net.Uri.parse("package:${app.packageName}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        container.appContext.startActivity(intent)
    }
}
