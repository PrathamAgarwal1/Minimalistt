package com.minimalist.launcher.ui.apps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.domain.AppSearch
import com.minimalist.launcher.domain.LaunchableApp
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AppListViewModel(container: AppContainer) : ViewModel() {

    private val checker = container.blockChecker

    val installed: StateFlow<List<LaunchableApp>> = container.apps.installedApps.filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hiddenKeys: StateFlow<Set<String>> = container.apps.prefs
        .map { prefs -> prefs.filter { it.hidden }.map { it.appKey }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val favouriteKeys: StateFlow<Set<String>> = container.apps.prefs
        .map { prefs -> prefs.filter { it.favourite }.map { it.appKey }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun isProtected(packageName: String) = checker.isProtected(packageName)
}

/** Which apps a picker offers. Blocking pickers want one entry per package, hidden apps included. */
enum class PickerMode { Favourite, Blockable }

@Composable
fun rememberAppResults(query: String, mode: PickerMode, exclude: Set<String> = emptySet()): List<LaunchableApp> {
    val viewModel = appViewModel { AppListViewModel(it) }
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenKeys.collectAsStateWithLifecycle()
    val favourites by viewModel.favouriteKeys.collectAsStateWithLifecycle()
    return remember(query, installed, hidden, favourites, mode, exclude) {
        when (mode) {
            PickerMode.Favourite -> AppSearch.search(query, installed, hidden + favourites)
            PickerMode.Blockable -> AppSearch.search(
                query,
                installed.filter { it.packageName !in exclude && !viewModel.isProtected(it.packageName) }.distinctBy { it.packageName },
                emptySet(),
            )
        }
    }
}

/** Pick an app by typing, same as search: there is never a full list. */
@Composable
fun AppPickerScreen(
    title: String,
    mode: PickerMode,
    onBack: () -> Unit,
    onPick: (LaunchableApp) -> Unit,
    note: String? = null,
) {
    var query by remember { mutableStateOf("") }
    val results = rememberAppResults(query, mode)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle(title, onBack)
        note?.let { Label(it, style = Type.small) }
        InputField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Type an app name",
            modifier = Modifier.focusRequester(focus),
        )
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(results, key = { it.key }) { app ->
                TextAction(text = app.label, onClick = { onPick(app) }, style = Type.item, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
