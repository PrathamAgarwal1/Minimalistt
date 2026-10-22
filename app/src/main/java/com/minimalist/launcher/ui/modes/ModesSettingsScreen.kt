package com.minimalist.launcher.ui.modes

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.domain.modes.DayModeConfig
import com.minimalist.launcher.modes.GuardService
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.blocking.BlockingText
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.LabelledAction
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.TwoLineRow
import com.minimalist.launcher.ui.components.pickTime
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.launch

/** Which app a mode picker is choosing. */
enum class ModeAppRole { Essential, Audio, Notes }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModesSettingsScreen(
    onBack: () -> Unit,
    onAddStudyHours: () -> Unit,
    onPickApp: (ModeAppRole) -> Unit,
) {
    val container = appContainer()
    val modes = container.modes
    val state by modes.state.collectAsStateWithLifecycle()
    val habits by remember { modes.habitsToday() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var newHabit by remember { mutableStateOf("") }
    var canTint by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var guardRunning by remember { mutableStateOf(GuardService.running) }
    LifecycleResumeEffect(Unit) {
        canTint = Settings.canDrawOverlays(context)
        guardRunning = GuardService.running
        onPauseOrDispose {}
    }
    val day = state.dayModes
    fun setDay(config: DayModeConfig) {
        scope.launch { modes.setDayModes(config) }
    }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Modes", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Put away by itself")
            Label("After unplugging from USB", style = Type.body)
            FlowRow {
                listOf(0, 15, 30, 60).forEach { minutes ->
                    Choice(if (minutes == 0) "Off" else BlockingText.minutes(minutes), selected = state.unplugMinutes == minutes, onClick = {
                        scope.launch { modes.setUnplugMinutes(minutes) }
                    })
                }
            }
            Label("After too many pickups in an hour", style = Type.body)
            FlowRow {
                listOf(0, 10, 15, 20, 30).forEach { count ->
                    Choice(if (count == 0) "Off" else "$count", selected = state.pickupThreshold == count, onClick = {
                        scope.launch { modes.setPickups(count, state.pickupMinutes) }
                    })
                }
            }
            if (state.pickupThreshold > 0) {
                FlowRow {
                    Label("for ", style = Type.small)
                    listOf(15, 30, 60).forEach { minutes ->
                        Choice(BlockingText.minutes(minutes), selected = state.pickupMinutes == minutes, onClick = {
                            scope.launch { modes.setPickups(state.pickupThreshold, minutes) }
                        })
                    }
                }
            }
            Label("During study hours", style = Type.body)
            state.studyWindows.forEachIndexed { index, window ->
                LabelledAction(
                    label = "${ScheduleRules.formatMinute(window.startMinute)}–${ScheduleRules.formatMinute(window.endMinute)} · ${BlockingText.days(window.days)}",
                    action = "Remove",
                    onAction = { scope.launch { modes.setStudyWindows(state.studyWindows.filterIndexed { i, _ -> i != index }) } },
                )
            }
            TextAction(text = "Add study hours", onClick = onAddStudyHours, style = Type.small)

            SectionHeader("Morning")
            TwoLineRow(
                title = "Morning mode",
                subtitle = if (day.morningEnabled) "Starts at your first unlock after waking" else "Off",
                action = if (day.morningEnabled) "Turn off" else "Turn on",
                onAction = { setDay(day.copy(morningEnabled = !day.morningEnabled)) },
            )
            Row {
                TextAction("Weekdays ${ScheduleRules.formatMinute(day.wakeWeekday)}", onClick = {
                    pickTime(context, day.wakeWeekday) { setDay(day.copy(wakeWeekday = it)) }
                })
                Spacer(Modifier.width(24.dp))
                TextAction("Weekends ${ScheduleRules.formatMinute(day.wakeWeekend)}", onClick = {
                    pickTime(context, day.wakeWeekend) { setDay(day.copy(wakeWeekend = it)) }
                })
            }
            FlowRow {
                Label("lasting ", style = Type.small)
                listOf(30, 45, 60, 90).forEach { minutes ->
                    Choice(BlockingText.minutes(minutes), selected = day.morningMinutes == minutes, onClick = {
                        setDay(day.copy(morningMinutes = minutes))
                    })
                }
            }
            Label("Morning habits", style = Type.body)
            habits.forEach { (habit, _) ->
                LabelledAction(label = habit.title, action = "Remove", onAction = { scope.launch { modes.deleteHabit(habit.id) } })
            }
            InputField(
                value = newHabit,
                onValueChange = { newHabit = it.take(60) },
                placeholder = "Add a habit, e.g. Drink water",
                style = Type.body,
                capitalization = KeyboardCapitalization.Sentences,
                onImeAction = {
                    val title = newHabit
                    newHabit = ""
                    scope.launch { modes.addHabit(title, habits.size) }
                },
            )

            SectionHeader("Night")
            TwoLineRow(
                title = "Night mode",
                subtitle = if (day.nightEnabled) "From bedtime until you wake" else "Off",
                action = if (day.nightEnabled) "Turn off" else "Turn on",
                onAction = { setDay(day.copy(nightEnabled = !day.nightEnabled)) },
            )
            Row {
                TextAction("Weeknights ${ScheduleRules.formatMinute(day.bedWeekday)}", onClick = {
                    pickTime(context, day.bedWeekday) { setDay(day.copy(bedWeekday = it)) }
                })
                Spacer(Modifier.width(24.dp))
                TextAction("Fri, Sat ${ScheduleRules.formatMinute(day.bedWeekend)}", onClick = {
                    pickTime(context, day.bedWeekend) { setDay(day.copy(bedWeekend = it)) }
                })
            }
            if (canTint) {
                Label("The screen gets a red tint at night.", style = Type.small)
            } else {
                Label("For the red tint at night, allow Minimalist to display over other apps.", style = Type.small)
                TextAction(text = "Allow display over apps", onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }, style = Type.small)
            }

            SectionHeader("Works during modes")
            Label("Calls, messages and the clock always work.", style = Type.small)
            Label("Essentials, e.g. UPI apps", style = Type.body)
            state.essentials.sortedBy { container.labelFor(it).lowercase() }.forEach { packageName ->
                LabelledAction(label = container.labelFor(packageName), action = "Remove", onAction = {
                    scope.launch { modes.setEssentials(state.essentials - packageName) }
                })
            }
            TextAction(text = "Add an essential", onClick = { onPickApp(ModeAppRole.Essential) }, style = Type.small)
            TwoLineRow(
                title = "Audio app (morning and night)",
                subtitle = state.audioApp?.let(container::labelFor) ?: "None",
                action = if (state.audioApp == null) "Choose" else "Clear",
                onAction = { if (state.audioApp == null) onPickApp(ModeAppRole.Audio) else scope.launch { modes.setAudioApp(null) } },
            )
            TwoLineRow(
                title = "Notes app (morning and night)",
                subtitle = state.notesApp?.let(container::labelFor) ?: "None",
                action = if (state.notesApp == null) "Choose" else "Clear",
                onAction = { if (state.notesApp == null) onPickApp(ModeAppRole.Notes) else scope.launch { modes.setNotesApp(null) } },
            )

            SectionHeader("Background")
            if (guardRunning) {
                Label("Running: modes start and end on time.", style = Type.body.copy(color = Palette.Accent))
            } else {
                Label("Not running. Modes still start from the home screen, but pickups and unplugging aren't noticed.", style = Type.small)
                TextAction(text = "Start it", onClick = {
                    GuardService.start(context)
                    guardRunning = true
                }, style = Type.small)
            }
            Label(
                "On ColorOS, also allow Minimalist to auto-launch and to run in the background (Settings, Battery), so it isn't stopped.",
                style = Type.small,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
