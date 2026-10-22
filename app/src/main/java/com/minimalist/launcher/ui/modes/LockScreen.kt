package com.minimalist.launcher.ui.modes

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.AppContainer
import com.minimalist.launcher.data.friction.FrictionRepository
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.friction.AppFeeling
import com.minimalist.launcher.domain.friction.WantingLiking
import com.minimalist.launcher.domain.modes.ActiveLock
import com.minimalist.launcher.domain.modes.LockKind
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.blocking.BlockingText
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.protection.EmergencyUnlockAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate

/** Night colours: dim red on black, easy on eyes adjusting to the dark. */
private object NightPalette {
    val Text = Color(0xFFB0574F)
    val Dim = Color(0xFF6E3A34)
}

/** What the home screen becomes while Put away, morning or night mode runs. */
@Composable
fun LockScreen(lock: ActiveLock, onUrgent: () -> Unit) {
    LockScreenContent(lock, onUrgent)
}

@Composable
private fun LockScreenContent(lock: ActiveLock, onUrgent: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val left = DurationFormat.remaining(lock.untilMillis - now)
    val until = BlockingText.until(lock.untilMillis)
    when (lock.kind) {
        LockKind.PutAway, LockKind.Challenge -> PutAwayLock(left, until, onUrgent)
        LockKind.Night -> NightLock(left, lock.untilMillis, onUrgent)
    }
}

@Composable
private fun PutAwayLock(left: String, until: String, onUrgent: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    fun extend(minutes: Long) {
        scope.launch { container.modes.putAway(container.timeScale.value.minutesAsSeconds(Duration.ofMinutes(minutes))) }
    }
    Screen {
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Label(left, style = Type.clock.copy(color = Palette.Faint))
            Label("Put away $until", style = Type.small.copy(color = Palette.Faint))
        }
        Spacer(Modifier.weight(1f))
        SpacedRow {
            TextAction("+15 min", onClick = { extend(15) }, style = Type.small.copy(color = Palette.Faint))
            TextAction("+30 min", onClick = { extend(30) }, style = Type.small.copy(color = Palette.Faint))
            TextAction("+1 h", onClick = { extend(60) }, style = Type.small.copy(color = Palette.Faint))
        }
        LockApps(LockKind.PutAway, Type.small.copy(color = Palette.Faint), onUrgent)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun NightLock(left: String, opensAtMillis: Long, onUrgent: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val day = remember { LocalDate.now().toEpochDay() }
    val since = remember { FrictionRepository.startOfToday() }
    val screenTime by remember { container.usage.totalForegroundToday(container.appContext.packageName) }.collectAsStateWithLifecycle(0L)
    val resisted by remember { container.friction.resistedSince(since) }.collectAsStateWithLifecycle(0)
    val feelings by remember { container.friction.pullsSince(since).map(WantingLiking::summarize) }.collectAsStateWithLifecycle(emptyList())
    val reflection by remember { container.modes.reflection(day) }.collectAsStateWithLifecycle(null)
    val firstOpen by remember { container.modes.firstOpen(day) }.collectAsStateWithLifecycle(null)
    var wentWell by remember { mutableStateOf<String?>(null) }
    var firstTask by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reflection) {
        if (wentWell == null) wentWell = reflection?.wentWell.orEmpty()
        if (firstTask == null) firstTask = reflection?.firstTask.orEmpty()
    }
    val text = Type.body.copy(color = NightPalette.Text)
    val dim = Type.small.copy(color = NightPalette.Dim)

    Screen {
        Spacer(Modifier.height(56.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label("Night", style = Type.title.copy(color = NightPalette.Text))
            val opens = java.time.Instant.ofEpochMilli(opensAtMillis).atZone(java.time.ZoneId.systemDefault())
            Label("Your phone opens at ${"%02d:%02d".format(opens.hour, opens.minute)}, in $left", style = dim)
            Spacer(Modifier.height(28.dp))
            Label("Today", style = Type.label.copy(color = NightPalette.Dim))
            Label("${DurationFormat.short(screenTime)} on your phone", style = text)
            Label("$resisted ${if (resisted == 1) "urge" else "urges"} resisted", style = text)
            firstOpen?.let { open ->
                val at = java.time.Instant.ofEpochMilli(open.at).atZone(java.time.ZoneId.systemDefault())
                Label("Opened at ${"%02d:%02d".format(at.hour, at.minute)}${if (open.early) ", early" else ""}", style = dim)
            }
            nightFeelings(container, feelings)?.let { Label(it, style = dim) }

            Spacer(Modifier.height(28.dp))
            Label("What went well?", style = text)
            InputField(
                value = wentWell.orEmpty(),
                onValueChange = { wentWell = it.take(200) },
                placeholder = "Something small counts",
                style = dim,
                capitalization = KeyboardCapitalization.Sentences,
            )
            Spacer(Modifier.height(12.dp))
            Label("What's tomorrow's first task?", style = text)
            InputField(
                value = firstTask.orEmpty(),
                onValueChange = { firstTask = it.take(120) },
                placeholder = "It becomes tomorrow morning's prompt",
                style = dim,
                capitalization = KeyboardCapitalization.Sentences,
            )
            val saved = reflection != null && reflection?.wentWell.orEmpty() == wentWell.orEmpty().trim() &&
                reflection?.firstTask.orEmpty() == firstTask.orEmpty().trim()
            TextAction(
                text = if (saved) "Saved. Sleep well." else "Save",
                onClick = { scope.launch { container.modes.saveReflection(day, wentWell, firstTask) } },
                style = text,
            )
        }
        LockApps(LockKind.Night, dim, onUrgent)
        Spacer(Modifier.height(12.dp))
    }
}

private fun nightFeelings(container: AppContainer, feelings: List<AppFeeling>): String? {
    val opens = feelings.sumOf { it.opens }
    if (opens == 0) return null
    val top = feelings.firstOrNull { it.wantingOutpacesLiking }?.let { " · ${container.labelFor(it.packageName)} pulled harder than it paid off" }
    val urge = feelings.mapNotNull { it.averageUrge }.takeIf { it.isNotEmpty() }?.average()
    val worth = feelings.mapNotNull { it.averageWorth }.takeIf { it.isNotEmpty() }?.average()
    return buildString {
        append("$opens slowed-down ${if (opens == 1) "open" else "opens"}")
        urge?.let { append(", urge %.1f".format(it)) }
        worth?.let { append(", worth %.1f".format(it)) }
        top?.let(::append)
    }
}

/** Calls, messages, essentials and (morning, night) the audio and notes apps, plus urgent messages. */
@Composable
private fun LockApps(kind: LockKind, style: TextStyle, onUrgent: () -> Unit) {
    val container = appContainer()
    val state by container.modes.state.collectAsStateWithLifecycle()
    val entries = buildList {
        container.protectedApps.defaultDialer()?.let { add("Phone" to it) }
        container.protectedApps.defaultSms()?.let { add("Messages" to it) }
        state.essentials.forEach { add(container.labelFor(it) to it) }
        if (kind != LockKind.PutAway) {
            state.audioApp?.let { add(container.labelFor(it) to it) }
            state.notesApp?.let { add(container.labelFor(it) to it) }
        }
    }.distinctBy { it.second }
    SpacedRow {
        entries.forEach { (label, packageName) ->
            TextAction(label, onClick = { container.launchPackage(packageName) }, style = style)
        }
        TextAction("Urgent message", onClick = onUrgent, style = style)
    }
    val lock = container.lock.collectAsStateWithLifecycle().value
    SpacedRow {
        lock?.let {
            val label = if (it.kind == LockKind.Night) "Solve problems to get through" else "Solve problems to get past"
            TextAction(label, onClick = { container.requestUnlock(it) }, style = style)
        }
        EmergencyUnlockAction(style = style)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpacedRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
    ) { content() }
}
