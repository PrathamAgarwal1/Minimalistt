package com.minimalist.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.minimalist.launcher.ui.home.HomeScreen
import com.minimalist.launcher.ui.placeholder.ComingLaterScreen
import com.minimalist.launcher.ui.search.SearchScreen
import com.minimalist.launcher.ui.settings.FavouritePickerScreen
import com.minimalist.launcher.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow

enum class Destination(val parent: Destination?) {
    Home(null),
    Search(Home),
    Settings(Home),
    FavouritePicker(Settings),
    PutAway(Home),
    UrgentMessage(Home),
}

/**
 * Plain state-based navigation. The Home button always returns here ([homePresses]), and Back
 * on the home screen does nothing.
 */
@Composable
fun LauncherRoot(homePresses: Flow<Unit>) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    LaunchedEffect(homePresses) { homePresses.collect { destination = Destination.Home } }
    BackHandler { destination.parent?.let { destination = it } }

    Crossfade(targetState = destination, animationSpec = tween(150), label = "screen") { screen ->
        when (screen) {
            Destination.Home -> HomeScreen(
                onOpenSearch = { destination = Destination.Search },
                onOpenSettings = { destination = Destination.Settings },
                onPutAway = { destination = Destination.PutAway },
                onUrgentMessage = { destination = Destination.UrgentMessage },
            )
            Destination.Search -> SearchScreen(
                onDone = { destination = Destination.Home },
                onOpenSettings = { destination = Destination.Settings },
            )
            Destination.Settings -> SettingsScreen(
                onBack = { destination = Destination.Home },
                onAddFavourite = { destination = Destination.FavouritePicker },
            )
            Destination.FavouritePicker -> FavouritePickerScreen(onBack = { destination = Destination.Settings })
            Destination.PutAway -> ComingLaterScreen(
                title = "Put away",
                body = "Put away mode arrives in a later update.",
                onBack = { destination = Destination.Home },
            )
            Destination.UrgentMessage -> ComingLaterScreen(
                title = "Urgent message",
                body = "Urgent WhatsApp messages arrive in a later update.",
                onBack = { destination = Destination.Home },
            )
        }
    }
}
