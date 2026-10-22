package com.minimalist.launcher.ui

import com.minimalist.launcher.system.TrustedClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.blocking.Notice
import com.minimalist.launcher.data.protection.Change
import com.minimalist.launcher.ui.protection.ChallengeScreen
import com.minimalist.launcher.ui.protection.ChangeConfirmScreen
import com.minimalist.launcher.ui.protection.LettersScreen
import com.minimalist.launcher.ui.protection.PendingChangesScreen
import com.minimalist.launcher.ui.protection.ProtectionScreen
import com.minimalist.launcher.ui.protection.SettingsClosedScreen
import com.minimalist.launcher.domain.FavouriteOrder
import com.minimalist.launcher.ui.apps.AppPickerScreen
import com.minimalist.launcher.ui.apps.PickerMode
import com.minimalist.launcher.ui.block.BlockScreen
import com.minimalist.launcher.ui.blocking.BlockDurationScreen
import com.minimalist.launcher.ui.blocking.BlockingScreen
import com.minimalist.launcher.ui.blocking.LimitEditScreen
import com.minimalist.launcher.ui.blocking.ScheduleEditScreen
import com.minimalist.launcher.ui.blocking.WantingLikingScreen
import com.minimalist.launcher.ui.friction.FrictionScreen
import com.minimalist.launcher.ui.friction.WorthScreen
import com.minimalist.launcher.ui.home.HomeScreen
import com.minimalist.launcher.MainActivity
import com.minimalist.launcher.ui.modes.LockScreen
import com.minimalist.launcher.ui.modes.MorningScreen
import com.minimalist.launcher.ui.modes.ModeAppRole
import com.minimalist.launcher.ui.modes.ModesSettingsScreen
import com.minimalist.launcher.ui.modes.PutAwayStartScreen
import com.minimalist.launcher.ui.modes.StudyWindowEditScreen
import com.minimalist.launcher.ui.notifications.HeldNotificationsScreen
import com.minimalist.launcher.ui.notifications.NotificationsSettingsScreen
import com.minimalist.launcher.ui.notifications.UrgentScreen
import com.minimalist.launcher.ui.websites.WebsitesScreen
import com.minimalist.launcher.ui.search.SearchScreen
import com.minimalist.launcher.ui.earn.EarnScreen
import com.minimalist.launcher.ui.settings.AppearanceScreen
import com.minimalist.launcher.ui.setup.SetupScreen
import com.minimalist.launcher.ui.stats.StatsScreen
import com.minimalist.launcher.ui.stats.WeeklyReviewScreen
import com.minimalist.launcher.domain.stats.WeeklyReview
import com.minimalist.launcher.ui.settings.SettingsScreen
import com.minimalist.launcher.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Stands in for the home screen while a mode runs. */
private data object LockedHome : Destination { override val parent = null }

sealed interface Destination {
    val parent: Destination?

    data object Home : Destination { override val parent = null }
    data object Search : Destination { override val parent = Home }
    data object Settings : Destination { override val parent = Home }
    data object FavouritePicker : Destination { override val parent = Settings }
    data object Blocking : Destination { override val parent = Settings }
    data object BlockPicker : Destination { override val parent = Blocking }
    data class BlockDuration(val packageName: String) : Destination { override val parent = BlockPicker }
    data object LimitPicker : Destination { override val parent = Blocking }
    data class LimitEdit(val packageName: String) : Destination { override val parent = Blocking }
    data class ScheduleEdit(val scheduleId: Long?) : Destination { override val parent = Blocking }
    data object FrictionPicker : Destination { override val parent = Blocking }
    data object WantingLiking : Destination { override val parent = Blocking }
    data object Modes : Destination { override val parent = Settings }
    data object StudyHours : Destination { override val parent = Modes }
    data class ModeAppPicker(val role: ModeAppRole) : Destination { override val parent = Modes }
    data object Notifications : Destination { override val parent = Settings }
    data object HeldNotifications : Destination { override val parent = Notifications }
    data object Websites : Destination { override val parent = Settings }
    data object Protection : Destination { override val parent = Settings }
    data object Appearance : Destination { override val parent = Settings }
    data object Earn : Destination { override val parent = Settings }
    data object EarnPicker : Destination { override val parent = Earn }
    data object Stats : Destination { override val parent = Settings }
    data object StatsWantingLiking : Destination { override val parent = Stats }
    data object WeeklyReview : Destination { override val parent = Stats }
    data object Setup : Destination { override val parent = Settings }
    data object PendingChanges : Destination { override val parent = Protection }
    data object Letters : Destination { override val parent = Protection }
    data object PutAway : Destination { override val parent = Home }
    data object UrgentMessage : Destination { override val parent = Home }
}

/**
 * Plain state-based navigation. The Home button always returns here ([homePresses]), Back on
 * the home screen does nothing, and the block screen covers everything while it is up.
 */
@Composable
fun LauncherRoot(homePresses: Flow<Unit>, openRequests: Flow<String>) {
    val container = appContainer()
    var destination by remember { mutableStateOf<Destination>(Destination.Home) }
    val notice by container.notice.collectAsStateWithLifecycle()
    val lock by container.lock.collectAsStateWithLifecycle()
    val changeRequest by container.changes.request.collectAsStateWithLifecycle()
    val unlockRequest by container.unlockRequest.collectAsStateWithLifecycle()
    val modesState by container.modes.state.collectAsStateWithLifecycle()
    val setupDone by container.settings.setupDone.collectAsStateWithLifecycle(true)

    LaunchedEffect(homePresses) { homePresses.collect { destination = Destination.Home } }
    LaunchedEffect(openRequests) {
        openRequests.collect {
            when (it) {
                MainActivity.OPEN_HELD -> destination = Destination.HeldNotifications
                MainActivity.OPEN_SETUP -> destination = Destination.Setup
            }
        }
    }
    LaunchedEffect(notice) { if (notice != null) destination = Destination.Home }
    BackHandler { destination.parent?.let { destination = it } }

    Box {
        // During a mode the home screen is the lock screen; only urgent messages lead anywhere.
        val current = lock
        val shown = if (current != null && destination != Destination.UrgentMessage) LockedHome else destination
        Crossfade(targetState = shown, animationSpec = tween(150), label = "screen") { screen ->
            if (screen == LockedHome && current != null) {
                LockScreen(current, onUrgent = { destination = Destination.UrgentMessage })
            } else {
                DestinationContent(screen, navigate = { destination = it })
            }
        }
        when (val current = notice) {
            is Notice.Blocked -> BlockScreen(
                notice = current,
                onClose = { container.notice.value = null },
                onUrgeRidden = { container.applicationScope.launch { container.friction.recordResisted(current.packageName, null, null) } },
                onUnlockEarly = { container.requestUnlock(current) },
            )
            is Notice.Friction -> FrictionScreen(current)
            is Notice.WorthIt -> WorthScreen(current)
            Notice.SettingsClosed -> SettingsClosedScreen(onClose = { container.notice.value = null })
            null -> Unit
        }
        // Once the phone opens after night: the morning screen, shown once.
        if (lock == null && notice == null && modesState.morningScreenDay == TrustedClock.today().toEpochDay()) {
            MorningScreen()
        }
        // Sunday evening, once: the week in review. Waits for locks, notices and the morning.
        val reviewScope = rememberCoroutineScope()
        // The first review waits for a week of history.
        LaunchedEffect(modesState.loaded, modesState.lastReviewAt) {
            if (modesState.loaded && modesState.lastReviewAt == null) container.modes.setLastReviewAt(TrustedClock.nowMillis())
        }
        val morningShowing = modesState.morningScreenDay == TrustedClock.today().toEpochDay()
        if (setupDone && lock == null && notice == null && !morningShowing && modesState.lastReviewAt != null &&
            WeeklyReview.isDue(modesState.lastReviewAt, TrustedClock.zonedNow())
        ) {
            WeeklyReviewScreen(onClose = {
                reviewScope.launch { container.modes.setLastReviewAt(TrustedClock.nowMillis()) }
            })
        }
        // First run: walk through every permission once.
        if (!setupDone && lock == null) {
            val scope = rememberCoroutineScope()
            SetupScreen(onBack = {}, onFinish = { scope.launch { container.settings.setSetupDone() } })
        }
        changeRequest?.let { ChangeConfirmScreen(it) }
        unlockRequest?.let { ChallengeScreen(it) }
    }
}

@Composable
private fun DestinationContent(screen: Destination, navigate: (Destination) -> Unit) {
    val back = { screen.parent?.let(navigate) ?: Unit }
    when (screen) {
        LockedHome -> Unit
        Destination.Home -> HomeScreen(
            onOpenSearch = { navigate(Destination.Search) },
            onOpenSettings = { navigate(Destination.Settings) },
            onPutAway = { navigate(Destination.PutAway) },
            onUrgentMessage = { navigate(Destination.UrgentMessage) },
            onOpenSetup = { navigate(Destination.Setup) },
            onBlockApp = { navigate(Destination.BlockPicker) },
            onOpenStats = { navigate(Destination.Stats) },
        )
        Destination.Search -> SearchScreen(
            onDone = { navigate(Destination.Home) },
            onOpen = navigate,
        )
        Destination.Settings -> SettingsScreen(
            onBack = back,
            onAddFavourite = { navigate(Destination.FavouritePicker) },
            onOpenBlocking = { navigate(Destination.Blocking) },
            onOpenModes = { navigate(Destination.Modes) },
            onOpenNotifications = { navigate(Destination.Notifications) },
            onOpenWebsites = { navigate(Destination.Websites) },
            onOpenProtection = { navigate(Destination.Protection) },
            onOpenAppearance = { navigate(Destination.Appearance) },
            onOpenEarn = { navigate(Destination.Earn) },
            onOpenStats = { navigate(Destination.Stats) },
            onOpenSetup = { navigate(Destination.Setup) },
            onOpenWeeklyReview = { navigate(Destination.WeeklyReview) },
        )
        Destination.Earn -> EarnScreen(onBack = back, onAddApp = { navigate(Destination.EarnPicker) })
        Destination.EarnPicker -> {
            val container = appContainer()
            AppPickerScreen(
                title = "Needs earned time",
                mode = PickerMode.Blockable,
                note = "It opens only while you have earned time left.",
                onBack = back,
                onPick = {
                    container.applicationScope.launch { container.earn.addApp(it.packageName) }
                    back()
                },
            )
        }
        Destination.Stats -> StatsScreen(
            onBack = back,
            onOpenWantingLiking = { navigate(Destination.StatsWantingLiking) },
            onOpenWeeklyReview = { navigate(Destination.WeeklyReview) },
        )
        Destination.WeeklyReview -> WeeklyReviewScreen(onClose = back)
        Destination.StatsWantingLiking -> WantingLikingScreen(onBack = back, onBlock = { navigate(Destination.BlockDuration(it)) })
        Destination.Setup -> SetupScreen(onBack = back)
        Destination.Appearance -> AppearanceScreen(onBack = back)
        Destination.Protection -> ProtectionScreen(
            onBack = back,
            onOpenPending = { navigate(Destination.PendingChanges) },
            onOpenLetters = { navigate(Destination.Letters) },
        )
        Destination.PendingChanges -> PendingChangesScreen(onBack = back)
        Destination.Letters -> LettersScreen(onBack = back)
        Destination.Modes -> ModesSettingsScreen(
            onBack = back,
            onAddStudyHours = { navigate(Destination.StudyHours) },
            onPickApp = { navigate(Destination.ModeAppPicker(it)) },
        )
        Destination.StudyHours -> StudyWindowEditScreen(onBack = back)
        is Destination.ModeAppPicker -> {
            val container = appContainer()
            val scope = rememberCoroutineScope()
            AppPickerScreen(
                title = when (screen.role) {
                    ModeAppRole.Essential -> "Add an essential"
                    ModeAppRole.Audio -> "Audio app"
                    ModeAppRole.Notes -> "Notes app"
                },
                mode = PickerMode.Blockable,
                note = when (screen.role) {
                    ModeAppRole.Essential -> "Works in every mode, e.g. a UPI app."
                    else -> "Works during night mode."
                },
                onBack = back,
                onPick = { app ->
                    val modes = container.modes
                    container.changes.submit(
                        when (screen.role) {
                            ModeAppRole.Essential -> Change.SetEssentials(modes.state.value.essentials + app.packageName)
                            ModeAppRole.Audio -> Change.SetAudioApp(app.packageName)
                            ModeAppRole.Notes -> Change.SetNotesApp(app.packageName)
                        },
                    )
                    back()
                },
            )
        }
        Destination.Notifications -> NotificationsSettingsScreen(onBack = back, onOpenHeld = { navigate(Destination.HeldNotifications) })
        Destination.HeldNotifications -> HeldNotificationsScreen(onBack = back)
        Destination.Websites -> WebsitesScreen(onBack = back)
        Destination.FavouritePicker -> {
            val settings = appViewModel { SettingsViewModel(it) }
            AppPickerScreen(
                title = "Add a favourite",
                mode = PickerMode.Favourite,
                note = "Up to ${FavouriteOrder.MAX_FAVOURITES}.",
                onBack = back,
                onPick = {
                    settings.addFavourite(it)
                    back()
                },
            )
        }
        Destination.Blocking -> BlockingScreen(
            onBack = back,
            onBlockApp = { navigate(Destination.BlockPicker) },
            onEditSchedule = { navigate(Destination.ScheduleEdit(it)) },
            onAddLimit = { navigate(Destination.LimitPicker) },
            onEditLimit = { navigate(Destination.LimitEdit(it)) },
            onAddFriction = { navigate(Destination.FrictionPicker) },
            onOpenWantingLiking = { navigate(Destination.WantingLiking) },
        )
        Destination.FrictionPicker -> {
            val container = appContainer()
            AppPickerScreen(
                title = "Slow down an app",
                mode = PickerMode.Blockable,
                note = "It opens only after a countdown, a reason and a chosen session length.",
                onBack = back,
                onPick = {
                    container.applicationScope.launch { container.friction.addApp(it.packageName) }
                    back()
                },
            )
        }
        Destination.WantingLiking -> WantingLikingScreen(
            onBack = back,
            onBlock = { navigate(Destination.BlockDuration(it)) },
        )
        Destination.BlockPicker -> AppPickerScreen(
            title = "Block an app",
            mode = PickerMode.Blockable,
            onBack = back,
            onPick = { navigate(Destination.BlockDuration(it.packageName)) },
        )
        is Destination.BlockDuration -> BlockDurationScreen(
            packageName = screen.packageName,
            onBack = back,
            onDone = { navigate(Destination.Blocking) },
        )
        Destination.LimitPicker -> AppPickerScreen(
            title = "Limit an app",
            mode = PickerMode.Blockable,
            onBack = back,
            onPick = { navigate(Destination.LimitEdit(it.packageName)) },
        )
        is Destination.LimitEdit -> LimitEditScreen(packageName = screen.packageName, onBack = { navigate(Destination.Blocking) })
        is Destination.ScheduleEdit -> ScheduleEditScreen(scheduleId = screen.scheduleId, onBack = back)
        Destination.PutAway -> PutAwayStartScreen(onBack = back)
        Destination.UrgentMessage -> UrgentScreen(onBack = back, onAddVips = { navigate(Destination.Notifications) })
    }
}
