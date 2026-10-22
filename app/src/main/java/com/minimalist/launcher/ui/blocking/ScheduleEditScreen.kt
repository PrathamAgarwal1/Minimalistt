package com.minimalist.launcher.ui.blocking

import android.app.TimePickerDialog
import android.text.format.DateFormat
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.domain.blocking.Schedule
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.ui.apps.PickerMode
import com.minimalist.launcher.ui.apps.rememberAppResults
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.LabelledAction
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Type
import java.time.DayOfWeek

private const val MAX_SUGGESTIONS = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditScreen(
    scheduleId: Long?,
    onBack: () -> Unit,
    viewModel: BlockingViewModel = appViewModel { BlockingViewModel(it) },
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(scheduleId == null) }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(22 * 60) }
    var end by remember { mutableStateOf(8 * 60) }
    var days by remember { mutableStateOf(DayOfWeek.entries.toSet()) }
    var packages by remember { mutableStateOf(emptySet<String>()) }
    var enabled by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(scheduleId) {
        if (scheduleId == null) return@LaunchedEffect
        viewModel.loadSchedule(scheduleId)?.let {
            name = it.name
            start = it.startMinute
            end = it.endMinute
            days = it.days
            packages = it.packages
            enabled = it.enabled
        }
        loaded = true
    }

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initial / 60,
            initial % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    val suggestions = rememberAppResults(query, PickerMode.Blockable, exclude = packages).take(MAX_SUGGESTIONS)

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle(if (scheduleId == null) "New schedule" else "Edit schedule", onBack)
        if (!loaded) return@Screen
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            InputField(
                value = name,
                onValueChange = { name = it.take(40) },
                placeholder = "Name, e.g. Night or Classes",
                style = Type.body,
                capitalization = KeyboardCapitalization.Sentences,
            )

            SectionHeader("Time")
            Row {
                TextAction(text = "From ${ScheduleRules.formatMinute(start)}", onClick = { pickTime(start) { start = it } }, style = Type.item)
                Spacer(Modifier.width(24.dp))
                TextAction(text = "to ${ScheduleRules.formatMinute(end)}", onClick = { pickTime(end) { end = it } }, style = Type.item)
            }
            Label(
                when {
                    start == end -> "Runs the whole day."
                    end < start -> "Runs overnight, into the next morning."
                    else -> "Runs the same day."
                },
                style = Type.small,
            )

            SectionHeader("Days it starts on")
            FlowRow {
                DayOfWeek.entries.forEach { day ->
                    Choice(
                        BlockingText.dayName(day),
                        selected = day in days,
                        onClick = { days = if (day in days) days - day else days + day },
                    )
                }
            }

            SectionHeader("Apps")
            if (packages.isEmpty()) Label("Add the apps this schedule blocks.", style = Type.small)
            packages.sortedBy { viewModel.labelFor(it).lowercase() }.forEach { packageName ->
                LabelledAction(label = viewModel.labelFor(packageName), action = "Remove", onAction = { packages = packages - packageName })
            }
            InputField(value = query, onValueChange = { query = it }, placeholder = "Add an app", style = Type.body)
            suggestions.forEach { app ->
                TextAction(
                    text = app.label,
                    onClick = {
                        packages = packages + app.packageName
                        query = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
            TextAction(
                text = "Save",
                enabled = days.isNotEmpty() && packages.isNotEmpty(),
                onClick = {
                    viewModel.saveSchedule(
                        Schedule(
                            id = scheduleId ?: 0,
                            name = name.trim().ifEmpty { "Schedule" },
                            startMinute = start,
                            endMinute = end,
                            days = days,
                            packages = packages,
                            enabled = enabled,
                        ),
                    )
                    onBack()
                },
            )
            if (scheduleId != null) {
                TextAction(
                    text = "Delete schedule",
                    onClick = {
                        viewModel.deleteSchedule(scheduleId)
                        onBack()
                    },
                    style = Type.small,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
