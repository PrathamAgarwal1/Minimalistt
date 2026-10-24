package com.minimalist.launcher.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.domain.AppSearch
import com.minimalist.launcher.domain.FavouriteOrder
import com.minimalist.launcher.domain.LaunchableApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(container: AppContainer) : ViewModel() {

    private val container = container
    private val apps = container.apps

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val favouriteKeys: StateFlow<Set<String>> = apps.prefs
        .map { prefs -> prefs.filter { it.favourite }.map { it.appKey }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val results: StateFlow<List<LaunchableApp>> = combine(
        _query,
        apps.installedApps.filterNotNull(),
        apps.prefs.map { prefs -> prefs.filter { it.hidden }.map { it.appKey }.toSet() },
    ) { query, installed, hidden -> AppSearch.search(query, installed, hidden) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
        _notice.value = null
    }

    fun reset() = setQuery("")

    fun launch(app: LaunchableApp) = container.open(app)

    fun openAppInfo(app: LaunchableApp) = apps.openAppInfo(app)

    /** Phone, messages, the clock and the like can never be blocked. */
    fun canBlock(app: LaunchableApp) = !container.blockChecker.isProtected(app.packageName)

    fun setFavourite(app: LaunchableApp, favourite: Boolean) {
        viewModelScope.launch {
            if (!apps.setFavourite(app, favourite)) {
                _notice.value = "Favourites are full (${FavouriteOrder.MAX_FAVOURITES}). Remove one first."
            }
        }
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
