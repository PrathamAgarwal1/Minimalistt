package com.minimalist.launcher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.LaunchableApp
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.OptionsDialog
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.TextInputDialog
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import java.time.LocalDateTime
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
    val glance by viewModel.glance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingIntention by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<LaunchableApp?>(null) }

    val is24Hour = DateFormat.is24HourFormat(context)
    val timeFormat = remember(is24Hour) { DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm") }
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
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Label(text = now.format(dateFormat), style = Type.small)
            glanceText(glance, now, timeFormat)?.let {
                Spacer(Modifier.height(2.dp))
                Label(text = it, style = Type.small.copy(color = Palette.Faint))
            }

            Spacer(Modifier.height(16.dp))
            val intention = state.intention
            if (intention != null) {
                TextAction(
                    text = intention,
                    onClick = { editingIntention = true },
                    style = Type.small.copy(color = Palette.Text, textAlign = TextAlign.Center),
                    alignment = Alignment.Center,
                )
            } else if (state.loaded) {
                TextAction(
                    text = "What's the one thing you'll do today?",
                    onClick = { editingIntention = true },
                    style = Type.small.copy(color = Palette.Faint),
                    alignment = Alignment.Center,
                )
            }

            Spacer(Modifier.height(36.dp))
            state.favourites.forEach { app ->
                TextAction(
                    text = app.label,
                    onClick = { viewModel.launch(app) },
                    onLongClick = { optionsFor = app },
                    style = Type.item,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.loaded && state.favourites.isEmpty()) {
                Label("Long-press an app in search to add it here.", style = Type.small.copy(color = Palette.Faint))
            }
        }
        Spacer(Modifier.weight(1f))

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

/** "1h 12m today · alarm 7:00", leaving out whatever isn't known. */
private fun glanceText(glance: Glance, now: LocalDateTime, timeFormat: DateTimeFormatter): String? {
    val parts = buildList {
        glance.screenTimeMillis?.let { add("${DurationFormat.short(it)} today") }
        glance.nextAlarm?.let { alarm ->
            val withinADay = alarm.isBefore(now.plusHours(24))
            val day = if (withinADay) "" else alarm.format(DateTimeFormatter.ofPattern("EEE ")).lowercase()
            add("alarm $day${alarm.format(timeFormat)}")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
