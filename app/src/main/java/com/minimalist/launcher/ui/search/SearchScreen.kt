package com.minimalist.launcher.ui.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.AppSearch
import com.minimalist.launcher.domain.LaunchableApp
import com.minimalist.launcher.ui.Destination
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.OptionsDialog
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type

/** Quiet's own pages, found by name like apps are. */
private val SHORTCUTS = listOf(
    "Quiet settings" to Destination.Settings,
    "Blocking" to Destination.Blocking,
    "Block an app" to Destination.BlockPicker,
    "Put away" to Destination.PutAway,
    "Night and wind-down" to Destination.Modes,
    "Notifications" to Destination.Notifications,
    "Websites" to Destination.Websites,
    "Stats" to Destination.Stats,
    "Weekly review" to Destination.WeeklyReview,
    "Earned screen time" to Destination.Earn,
    "Emergency unlock and letters" to Destination.Protection,
    "Appearance" to Destination.Appearance,
    "Home screen and favourites" to Destination.HomeApps,
    "Setup and status" to Destination.Setup,
)

@Composable
fun SearchScreen(
    onDone: () -> Unit,
    onOpen: (Destination) -> Unit,
    onBlock: (String) -> Unit,
    viewModel: SearchViewModel = appViewModel { SearchViewModel(it) },
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val favouriteKeys by viewModel.favouriteKeys.collectAsStateWithLifecycle()
    var optionsFor by remember { mutableStateOf<LaunchableApp?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { viewModel.reset() } }

    val normalized = AppSearch.normalize(query)
    val shortcuts = if (normalized.length < 3) emptyList() else SHORTCUTS.filter { (label, _) ->
        (AppSearch.score(AppSearch.normalize(label), normalized) ?: Int.MAX_VALUE) <= 1
    }

    fun launch(app: LaunchableApp) {
        viewModel.launch(app)
        onDone()
    }

    Screen {
        Spacer(Modifier.height(40.dp))
        InputField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = "Search",
            modifier = Modifier.focusRequester(focus),
            imeAction = ImeAction.Go,
            onImeAction = { results.firstOrNull()?.let(::launch) },
        )
        notice?.let { Label(it, style = Type.small) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(results, key = { it.key }) { app ->
                TextAction(
                    text = app.label,
                    onClick = { launch(app) },
                    onLongClick = { optionsFor = app },
                    style = Type.item,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(shortcuts, key = { "shortcut:" + it.first }) { (label, destination) ->
                TextAction(
                    text = label,
                    onClick = { onOpen(destination) },
                    style = Type.item.copy(color = Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    optionsFor?.let { app ->
        val isFavourite = app.key in favouriteKeys
        OptionsDialog(
            title = app.label,
            options = listOfNotNull(
                ("Block" to { onBlock(app.packageName) }).takeIf { viewModel.canBlock(app) },
                (if (isFavourite) "Remove from favourites" else "Add to favourites") to { viewModel.setFavourite(app, !isFavourite) },
                "Hide from search" to { viewModel.hide(app) },
                "App info" to { viewModel.openAppInfo(app) },
                "Uninstall" to { viewModel.uninstallApp(app) },
            ),
            onDismiss = { optionsFor = null },
        )
    }
}
