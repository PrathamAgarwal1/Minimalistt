package com.minimalist.launcher.ui.protection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.data.protection.ChangeRequest
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val READING_SECONDS = 10

/** Before a weakening change waits its cooldown, one of your letters is read in full. */
@Composable
fun ChangeConfirmScreen(request: ChangeRequest) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var wait by remember(request) { mutableIntStateOf(if (container.timeScale.value.testMode) 2 else READING_SECONDS) }
    LaunchedEffect(request) {
        while (wait > 0) {
            delay(1_000)
            wait--
        }
    }
    BackHandler { container.changes.dismiss() }
    val cooldown = DurationFormat.remaining(container.timeScale.value.cooldown.toMillis())

    Screen(
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Spacer(Modifier.height(48.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label(request.description, style = Type.title)
            Label("This loosens your protection, so it waits $cooldown before it applies.", style = Type.small)
            Spacer(Modifier.height(28.dp))
            val letter = request.letter
            if (letter != null) {
                val written = Instant.ofEpochMilli(letter.writtenAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMMM"))
                Label("A letter from you, $written", style = Type.label)
                Spacer(Modifier.height(8.dp))
                Label(letter.text, style = Type.body)
            } else {
                Label(
                    "You haven't written a letter to yourself yet. Write one while calm, in Settings, Letters to yourself: it'll be shown here next time.",
                    style = Type.body,
                )
            }
        }
        TextAction(
            text = if (wait > 0) "Queue it ($wait)" else "Queue it for $cooldown",
            enabled = wait == 0,
            onClick = { scope.launch { container.changes.confirm(request) } },
        )
        TextAction(text = "Keep things as they are", onClick = { container.changes.dismiss() }, style = Type.body.copy(color = Palette.Accent))
        Spacer(Modifier.height(12.dp))
    }
}
