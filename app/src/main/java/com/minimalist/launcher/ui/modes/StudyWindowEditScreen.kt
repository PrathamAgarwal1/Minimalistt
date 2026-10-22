package com.minimalist.launcher.ui.modes

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.data.protection.Change
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.domain.modes.StudyWindow
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.blocking.BlockingText
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.pickTime
import com.minimalist.launcher.ui.theme.Type
import java.time.DayOfWeek

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyWindowEditScreen(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(13 * 60) }
    var days by remember { mutableStateOf(DayOfWeek.entries.take(5).toSet()) }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Study hours", onBack)
        Label("Put away starts by itself for the rest of the window.", style = Type.small)
        SectionHeader("Time")
        Row {
            TextAction("From ${ScheduleRules.formatMinute(start)}", onClick = { pickTime(context, start) { start = it } }, style = Type.item)
            Spacer(Modifier.width(24.dp))
            TextAction("to ${ScheduleRules.formatMinute(end)}", onClick = { pickTime(context, end) { end = it } }, style = Type.item)
        }
        SectionHeader("Days")
        FlowRow {
            DayOfWeek.entries.forEach { day ->
                Choice(BlockingText.dayName(day), selected = day in days, onClick = { days = if (day in days) days - day else days + day })
            }
        }
        Spacer(Modifier.height(24.dp))
        TextAction(
            text = "Save",
            enabled = days.isNotEmpty() && start != end,
            onClick = {
                container.changes.submit(Change.SetStudyWindows(container.modes.state.value.studyWindows + StudyWindow(start, end, days)))
                onBack()
            },
        )
    }
}
