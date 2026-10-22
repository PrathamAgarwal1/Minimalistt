package com.minimalist.launcher.ui.modes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.notifications.NotificationsRepository
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Shown once when the phone opens in the morning: one intention, the habits, last night's notifications. */
@Composable
fun MorningScreen() {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now().toEpochDay() }
    val intention by container.settings.intention.collectAsStateWithLifecycle(null)
    val habits by remember { container.modes.habitsToday() }.collectAsStateWithLifecycle(emptyList())
    val held by remember { container.notifications.held }.collectAsStateWithLifecycle(emptyList())
    val firstOpen by remember { container.modes.firstOpen(today) }.collectAsStateWithLifecycle(null)
    var nightStart by remember { mutableStateOf<Long?>(null) }
    var draft by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        nightStart = container.modes.lastNightStartedAt()
        if (draft == null) draft = container.modes.yesterdaysFirstTask().orEmpty()
    }
    LaunchedEffect(intention) {
        intention?.takeIf { it.epochDay == today }?.let { draft = it.text }
    }
    fun pin() {
        scope.launch { container.settings.setIntention(draft.orEmpty(), today) }
    }
    fun done() {
        if (!draft.isNullOrBlank()) pin()
        scope.launch { container.modes.setMorningScreenDay(null) }
    }
    BackHandler(onBack = ::done)

    val overnight = held.filter { it.reason == NotificationsRepository.MODE && it.postedAt >= (nightStart ?: Long.MAX_VALUE) }
    val byApp = overnight.groupingBy { container.labelFor(it.packageName) }.eachCount().entries.sortedByDescending { it.value }

    Screen(
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Spacer(Modifier.height(56.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label("Good morning", style = Type.title)
            firstOpen?.let { open ->
                val at = Instant.ofEpochMilli(open.at).atZone(ZoneId.systemDefault())
                Label(
                    "Phone opened at ${"%02d:%02d".format(at.hour, at.minute)}${if (open.early) ", early" else ""}",
                    style = Type.small,
                )
            }
            Spacer(Modifier.height(32.dp))
            Label("What's the one thing you'll do today?", style = Type.body)
            InputField(
                value = draft.orEmpty(),
                onValueChange = { draft = it.take(120) },
                placeholder = "One thing",
                style = Type.body.copy(color = Palette.Accent),
                capitalization = KeyboardCapitalization.Sentences,
                onImeAction = ::pin,
            )
            Label("It stays pinned on your home screen all day.", style = Type.small)

            if (habits.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Label("Habits", style = Type.label)
                habits.forEach { (habit, done) ->
                    TextAction(
                        text = habit.title,
                        onClick = { scope.launch { container.modes.setHabitDone(habit.id, !done) } },
                        style = Type.body.copy(color = if (done) Palette.Accent else Palette.Text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Label("While you were away", style = Type.label)
            if (overnight.isEmpty()) {
                Label("Nothing came in.", style = Type.small)
            } else {
                Label("${overnight.size} ${if (overnight.size == 1) "notification" else "notifications"}", style = Type.body)
                Label(byApp.joinToString(" · ") { "${it.key} ${it.value}" }, style = Type.small)
            }
        }
        TextAction(text = "Start the day", onClick = ::done, style = Type.body.copy(color = Palette.Accent))
        Spacer(Modifier.height(12.dp))
    }
}
