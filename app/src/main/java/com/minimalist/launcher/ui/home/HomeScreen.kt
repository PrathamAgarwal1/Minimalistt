package com.minimalist.launcher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.LaunchableApp
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.OptionsDialog
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.TextInputDialog
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import java.time.format.DateTimeFormatter

private const val SWIPE_UP_THRESHOLD_PX = 120f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onPutAway: () -> Unit,
    onUrgentMessage: () -> Unit,
    viewModel: HomeViewModel = appViewModel { HomeViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingIntention by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<LaunchableApp?>(null) }

    val timeFormat = remember(DateFormat.is24HourFormat(context)) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm")
    }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM") }

    Screen(
        modifier = Modifier.pointerInput(Unit) {
            var dragged = 0f
            detectVerticalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = { if (dragged < -SWIPE_UP_THRESHOLD_PX) onOpenSearch() },
            ) { _, amount -> dragged += amount }
        },
    ) {
        Spacer(Modifier.height(72.dp))
        Label(
            text = now.format(timeFormat),
            style = Type.clock,
            modifier = Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onOpenSettings,
            ),
        )
        val batteryText = battery?.let { " · ${it.percent}%" + if (it.charging) " charging" else "" }.orEmpty()
        Label(text = now.format(dateFormat) + batteryText, style = Type.small)

        Spacer(Modifier.height(20.dp))
        val intention = state.intention
        if (intention != null) {
            TextAction(text = intention, onClick = { editingIntention = true }, style = Type.small.copy(color = Palette.Text))
        } else if (state.loaded) {
            TextAction(
                text = "What's the one thing you'll do today?",
                onClick = { editingIntention = true },
                style = Type.small.copy(color = Palette.Faint),
            )
        }

        Spacer(Modifier.weight(1f))
        state.favourites.forEach { app ->
            TextAction(
                text = app.label,
                onClick = { viewModel.launch(app) },
                onLongClick = { optionsFor = app },
                style = Type.item,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.loaded && state.favourites.isEmpty()) {
            Label("Long-press an app in search to add it here.", style = Type.small)
        }
        Spacer(Modifier.weight(1.3f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextAction(text = "Put away", onClick = onPutAway, style = Type.small)
            TextAction(text = "Search", onClick = onOpenSearch, style = Type.small)
            TextAction(text = "Urgent message", onClick = onUrgentMessage, style = Type.small)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (editingIntention) {
        TextInputDialog(
            title = "What's the one thing you'll do today?",
            initial = state.intention.orEmpty(),
            placeholder = "One thing",
            onSave = viewModel::setIntention,
            onDismiss = { editingIntention = false },
        )
    }

    optionsFor?.let { app ->
        OptionsDialog(
            title = app.label,
            options = listOf(
                "Remove from favourites" to { viewModel.removeFavourite(app) },
                "Hide from search" to { viewModel.hide(app) },
                "App info" to { viewModel.openAppInfo(app) },
            ),
            onDismiss = { optionsFor = null },
        )
    }
}
