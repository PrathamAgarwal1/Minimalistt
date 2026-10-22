package com.minimalist.launcher.ui.earn

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.earn.EarnToday
import com.minimalist.launcher.data.protection.Change
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.earn.Earning
import com.minimalist.launcher.modes.GuardService
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.LabelledAction
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.delay

/** Apps that open only on time earned by walking or finishing focus sessions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarnScreen(onBack: () -> Unit, onAddApp: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val state by container.earn.state.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf<EarnToday?>(null) }
    var canCount by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }
    val askSteps = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canCount = granted
        if (granted) GuardService.startCountingSteps(context)
    }
    LaunchedEffect(state) {
        while (true) {
            today = container.earn.today(container.timeScale.value)
            delay(5_000)
        }
    }
    val rules = state.rules
    fun setRules(next: com.minimalist.launcher.domain.earn.EarnRules) = container.changes.submit(Change.SetEarnRules(next))

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Earned screen time", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label("The apps below open only while you have earned time. Walking and focus sessions earn it; using them spends it.", style = Type.small)

            today?.let { t ->
                SectionHeader("Today")
                Label("${t.steps} steps · ${t.focusSessions} focus ${if (t.focusSessions == 1) "session" else "sessions"}", style = Type.body)
                Label("Earned ${t.earnedMinutes} min · used ${DurationFormat.short(t.usedMillis)}", style = Type.small)
                Label(
                    if (t.leftMillis > 0) "${DurationFormat.remaining(t.leftMillis)} left" else "Nothing left to spend",
                    style = Type.body.copy(color = Palette.Accent),
                )
            }
            if (!canCount) {
                Label("Allow activity recognition so steps can be counted.", style = Type.small)
                TextAction(text = "Allow step counting", onClick = { askSteps.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, style = Type.small)
            }

            SectionHeader("Apps that need earned time")
            if (state.apps.isEmpty()) Label("None yet.", style = Type.small)
            state.apps.sortedBy { container.labelFor(it).lowercase() }.forEach { packageName ->
                LabelledAction(label = container.labelFor(packageName), action = "Remove", onAction = {
                    container.changes.submit(Change.RemoveEarnedApp(packageName))
                })
            }
            TextAction(text = "Add an app", onClick = onAddApp, modifier = Modifier.fillMaxWidth())

            SectionHeader("Walking")
            FlowRow {
                Earning.STEP_OPTIONS.forEach { steps ->
                    Choice("%,d steps".format(steps), selected = rules.stepsPerUnit == steps, onClick = { setRules(rules.copy(stepsPerUnit = steps)) })
                }
            }
            FlowRow {
                Label("earn ", style = Type.small)
                Earning.STEP_MINUTE_OPTIONS.forEach { minutes ->
                    Choice("$minutes min", selected = rules.minutesPerSteps == minutes, onClick = { setRules(rules.copy(minutesPerSteps = minutes)) })
                }
            }

            SectionHeader("Focus")
            Label("A Put away that runs its full length counts as a focus session.", style = Type.small)
            FlowRow {
                Earning.FOCUS_OPTIONS.forEach { minutes ->
                    Choice("$minutes min", selected = rules.focusMinutes == minutes, onClick = { setRules(rules.copy(focusMinutes = minutes)) })
                }
            }
            FlowRow {
                Label("earns ", style = Type.small)
                Earning.FOCUS_MINUTE_OPTIONS.forEach { minutes ->
                    Choice("$minutes min", selected = rules.minutesPerFocus == minutes, onClick = { setRules(rules.copy(minutesPerFocus = minutes)) })
                }
            }
            Label("Making earning easier waits out the cooldown; making it harder applies now.", style = Type.small)
            Spacer(Modifier.height(24.dp))
        }
    }
}
