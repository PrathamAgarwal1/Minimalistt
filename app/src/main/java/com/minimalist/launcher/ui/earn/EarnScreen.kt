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
import androidx.compose.ui.text.input.KeyboardType
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
import com.minimalist.launcher.ui.components.TextInputDialog
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
    fun setRules(next: com.minimalist.launcher.domain.earn.EarnRules) = container.changes.submit(Change.SetEarnRules(Earning.sanitized(next)))

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
            Label("Every", style = Type.small)
            RuleRow(Earning.STEP_OPTIONS, rules.stepsPerUnit, { "%,d steps".format(it) }, Earning.STEPS_RANGE, "Steps") {
                setRules(rules.copy(stepsPerUnit = it))
            }
            Label("earns", style = Type.small)
            RuleRow(Earning.STEP_MINUTE_OPTIONS, rules.minutesPerSteps, { "$it min" }, Earning.MINUTES_RANGE, "Minutes") {
                setRules(rules.copy(minutesPerSteps = it))
            }

            SectionHeader("Focus")
            Label("A Put away that runs its full length counts as a focus session.", style = Type.small)
            Label("A session of", style = Type.small)
            RuleRow(Earning.FOCUS_OPTIONS, rules.focusMinutes, { "$it min" }, Earning.FOCUS_RANGE, "Minutes") {
                setRules(rules.copy(focusMinutes = it))
            }
            Label("earns", style = Type.small)
            RuleRow(Earning.FOCUS_MINUTE_OPTIONS, rules.minutesPerFocus, { "$it min" }, Earning.MINUTES_RANGE, "Minutes") {
                setRules(rules.copy(minutesPerFocus = it))
            }

            SectionHeader("Every day")
            Label("Start the day with", style = Type.small)
            RuleRow(Earning.FREE_OPTIONS, rules.freeMinutes, { if (it == 0) "Nothing" else "$it min" }, Earning.FREE_RANGE, "Minutes") {
                setRules(rules.copy(freeMinutes = it))
            }
            Label("Earn at most", style = Type.small)
            RuleRow(Earning.MAX_OPTIONS, rules.maxPerDay, { if (it == 0) "No limit" else "$it min" }, Earning.MAX_RANGE, "Minutes, 0 for no limit") {
                setRules(rules.copy(maxPerDay = it))
            }
            Label("Making earning easier waits out the cooldown; making it harder applies now.", style = Type.small)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The presets, plus Custom, which takes any number in [range]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleRow(
    options: List<Int>,
    value: Int,
    text: (Int) -> String,
    range: IntRange,
    unit: String,
    onPick: (Int) -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    FlowRow {
        options.forEach { option -> Choice(text(option), selected = value == option, onClick = { onPick(option) }) }
        Choice(if (value in options) "Custom" else "Custom: ${text(value)}", selected = value !in options, onClick = { asking = true })
    }
    if (asking) {
        TextInputDialog(
            title = "$unit, ${range.first} to ${"%,d".format(range.last)}",
            initial = value.toString(),
            placeholder = "${range.first}",
            onSave = { typed -> typed.filter(Char::isDigit).toIntOrNull()?.let { onPick(it.coerceIn(range)) } },
            onDismiss = { asking = false },
            keyboardType = KeyboardType.Number,
        )
    }
}
