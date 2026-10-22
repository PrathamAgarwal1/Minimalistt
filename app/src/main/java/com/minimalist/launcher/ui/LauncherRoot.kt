package com.minimalist.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.blocking.Notice
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
import com.minimalist.launcher.ui.placeholder.ComingLaterScreen
import com.minimalist.launcher.ui.search.SearchScreen
import com.minimalist.launcher.ui.settings.SettingsScreen
import com.minimalist.launcher.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
    data object PutAway : Destination { override val parent = Home }
    data object UrgentMessage : Destination { override val parent = Home }
}

/**
 * Plain state-based navigation. The Home button always returns here ([homePresses]), Back on
 * the home screen does nothing, and the block screen covers everything while it is up.
 */
@Composable
fun LauncherRoot(homePresses: Flow<Unit>) {
    val container = appContainer()
    var destination by remember { mutableStateOf<Destination>(Destination.Home) }
    val notice by container.notice.collectAsStateWithLifecycle()

    LaunchedEffect(homePresses) { homePresses.collect { destination = Destination.Home } }
    LaunchedEffect(notice) { if (notice != null) destination = Destination.Home }
    BackHandler { destination.parent?.let { destination = it } }

    Box {
        Crossfade(targetState = destination, animationSpec = tween(150), label = "screen") { screen ->
            DestinationContent(screen, navigate = { destination = it })
        }
        when (val current = notice) {
            is Notice.Blocked -> BlockScreen(
                notice = current,
                onClose = { container.notice.value = null },
                onUrgeRidden = { container.applicationScope.launch { container.friction.recordResisted(current.packageName, null, null) } },
            )
            is Notice.Friction -> FrictionScreen(current)
            is Notice.WorthIt -> WorthScreen(current)
            null -> Unit
        }
    }
}

@Composable
private fun DestinationContent(screen: Destination, navigate: (Destination) -> Unit) {
    val back = { screen.parent?.let(navigate) ?: Unit }
    when (screen) {
        Destination.Home -> HomeScreen(
            onOpenSearch = { navigate(Destination.Search) },
            onOpenSettings = { navigate(Destination.Settings) },
            onPutAway = { navigate(Destination.PutAway) },
            onUrgentMessage = { navigate(Destination.UrgentMessage) },
        )
        Destination.Search -> SearchScreen(
            onDone = { navigate(Destination.Home) },
            onOpenSettings = { navigate(Destination.Settings) },
        )
        Destination.Settings -> SettingsScreen(
            onBack = back,
            onAddFavourite = { navigate(Destination.FavouritePicker) },
            onOpenBlocking = { navigate(Destination.Blocking) },
        )
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
        Destination.PutAway -> ComingLaterScreen(
            title = "Put away",
            body = "Put away mode arrives in a later update.",
            onBack = back,
        )
        Destination.UrgentMessage -> ComingLaterScreen(
            title = "Urgent message",
            body = "Urgent WhatsApp messages arrive in a later update.",
            onBack = back,
        )
    }
}
