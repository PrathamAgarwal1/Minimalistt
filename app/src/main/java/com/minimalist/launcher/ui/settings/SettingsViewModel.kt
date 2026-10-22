package com.minimalist.launcher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.domain.LaunchableApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val favourites: List<LaunchableApp> = emptyList(),
    val hidden: List<LaunchableApp> = emptyList(),
)

class SettingsViewModel(container: AppContainer) : ViewModel() {

    private val apps = container.apps

    val state: StateFlow<SettingsState> = combine(apps.installedApps.filterNotNull(), apps.prefs) { installed, prefs ->
        val favourites = prefs.filter { it.favourite }.map { it.appKey }.toSet()
        val hidden = prefs.filter { it.hidden }.map { it.appKey }.toSet()
        SettingsState(
            favourites = installed.filter { it.key in favourites },
            hidden = installed.filter { it.key in hidden },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun addFavourite(app: LaunchableApp) {
        viewModelScope.launch { apps.setFavourite(app, true) }
    }

    fun removeFavourite(app: LaunchableApp) {
        viewModelScope.launch { apps.setFavourite(app, false) }
    }

    fun unhide(app: LaunchableApp) {
        viewModelScope.launch { apps.setHidden(app.key, app.packageName, false) }
    }
}
