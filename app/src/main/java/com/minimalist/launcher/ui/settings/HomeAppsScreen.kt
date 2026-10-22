package com.minimalist.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.FavouriteOrder
import com.minimalist.launcher.system.HomeRole
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.LabelledAction
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type

/** The Home app role, favourites and hidden apps. */
@Composable
fun HomeAppsScreen(
    onBack: () -> Unit,
    onAddFavourite: () -> Unit,
    viewModel: SettingsViewModel = appViewModel { SettingsViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isHome by remember { mutableStateOf(HomeRole.isDefaultHome(context)) }
    LifecycleResumeEffect(Unit) {
        isHome = HomeRole.isDefaultHome(context)
        onPauseOrDispose {}
    }
    val requestHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isHome = HomeRole.isDefaultHome(context)
    }
    fun openHomeSettings() {
        runCatching { context.startActivity(HomeRole.settingsIntent()) }
    }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Home screen", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Home app")
            if (isHome) {
                Label("This is your Home app.", style = Type.body.copy(color = Palette.Accent))
            } else {
                Label("Not your Home app yet.", style = Type.body)
                TextAction(
                    text = "Set as Home app",
                    onClick = {
                        runCatching { requestHome.launch(HomeRole.requestIntent(context)) }
                            .onFailure { openHomeSettings() }
                    },
                )
            }
            TextAction(text = "Open Home app settings", onClick = ::openHomeSettings, style = Type.small)

            SectionHeader("Favourites ${state.favourites.size}/${FavouriteOrder.MAX_FAVOURITES}")
            Label("Their order changes every day.", style = Type.small)
            state.favourites.forEach { app ->
                LabelledAction(label = app.label, action = "Remove", onAction = { viewModel.removeFavourite(app) })
            }
            TextAction(
                text = "Add a favourite",
                onClick = onAddFavourite,
                enabled = state.favourites.size < FavouriteOrder.MAX_FAVOURITES,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Hidden apps")
            if (state.hidden.isEmpty()) {
                Label("None. Long-press an app in search to hide it.", style = Type.small)
            }
            state.hidden.forEach { app ->
                LabelledAction(label = app.label, action = "Unhide", onAction = { viewModel.unhide(app) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
