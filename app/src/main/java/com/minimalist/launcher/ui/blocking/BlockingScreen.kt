package com.minimalist.launcher.ui.blocking

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import com.minimalist.launcher.BuildConfig
import com.minimalist.launcher.blocking.BlockerService
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.TwoLineRow
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type

@Composable
fun BlockingScreen(
    onBack: () -> Unit,
    onBlockApp: () -> Unit,
    onEditSchedule: (Long?) -> Unit,
    onAddLimit: () -> Unit,
    onEditLimit: (String) -> Unit,
    viewModel: BlockingViewModel = appViewModel { BlockingViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var serviceOn by remember { mutableStateOf(BlockerService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        serviceOn = BlockerService.isEnabled(context)
        onPauseOrDispose {}
    }
    fun open(intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    // Straight to this service's own switch; the full accessibility list if the phone refuses.
    // The action is not in the public SDK, hence the string.
    fun openBlockerSettings() {
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, BlockerService::class.java).flattenToString())
        if (!open(details)) open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Blocking", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Blocking service")
            if (serviceOn) {
                Label("On. A blocked app sends you back home.", style = Type.body.copy(color = Palette.Accent))
            } else {
                Label("Off. Nothing is blocked until it's on. Two steps:", style = Type.body)
                Spacer(Modifier.height(8.dp))
                Label("1. On the App info page, tap the three dots at the top right and choose Allow restricted settings. If there are no three dots, skip this step.", style = Type.small)
                TextAction(
                    text = "Open App info",
                    onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
                )
                Label("2. Turn on Minimalist blocking, then come back here.", style = Type.small)
                TextAction(text = "Open Minimalist blocking", onClick = { openBlockerSettings() })
                Label("If that opens a list instead, look under Downloaded apps or Installed services.", style = Type.small)
            }
            Label("Phone, emergency calls, messages, clock and this launcher can never be blocked.", style = Type.small)

            SectionHeader("Blocked apps")
            if (state.blocks.isEmpty()) Label("None right now.", style = Type.small)
            state.blocks.forEach { row ->
                TwoLineRow(
                    title = row.label,
                    subtitle = "${DurationFormat.remaining(row.untilMillis - state.nowMillis)} left · ${BlockingText.until(row.untilMillis)}",
                    action = "Remove",
                    onAction = { viewModel.removeBlock(row.packageName) },
                )
            }
            TextAction(text = "Block an app", onClick = onBlockApp, modifier = Modifier.fillMaxWidth())

            SectionHeader("Schedules")
            if (state.schedules.isEmpty()) Label("None. For example 22:00–08:00 every night.", style = Type.small)
            state.schedules.forEach { row ->
                val schedule = row.schedule
                TwoLineRow(
                    title = schedule.name + if (schedule.enabled) "" else " (off)",
                    subtitle = listOf(
                        BlockingText.window(schedule),
                        BlockingText.days(schedule.days),
                        row.appLabels.joinToString(", ").ifEmpty { "no apps" },
                    ).joinToString(" · "),
                    action = if (schedule.enabled) "Turn off" else "Turn on",
                    onAction = { viewModel.setScheduleEnabled(schedule.id, !schedule.enabled) },
                    onClick = { onEditSchedule(schedule.id) },
                )
            }
            TextAction(text = "Add a schedule", onClick = { onEditSchedule(null) }, modifier = Modifier.fillMaxWidth())

            SectionHeader("Daily limits")
            if (state.limits.isEmpty()) Label("None. Cap time or opens per day.", style = Type.small)
            state.limits.forEach { row ->
                TwoLineRow(
                    title = row.label,
                    subtitle = BlockingText.limit(row.limit),
                    action = "Edit",
                    onAction = { onEditLimit(row.limit.packageName) },
                    onClick = { onEditLimit(row.limit.packageName) },
                )
            }
            TextAction(text = "Add a limit", onClick = onAddLimit, modifier = Modifier.fillMaxWidth())

            if (BuildConfig.DEBUG) {
                SectionHeader("Debug build")
                TwoLineRow(
                    title = "Test mode",
                    subtitle = if (state.testMode) {
                        "On: new blocks count hours as seconds, limits count minutes as seconds"
                    } else {
                        "Off: real durations"
                    },
                    action = if (state.testMode) "Turn off" else "Turn on",
                    onAction = { viewModel.setTestMode(!state.testMode) },
                )
                Label("Clear everything: adb shell am broadcast -a ${context.packageName}.DEBUG_RESET", style = Type.small)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
