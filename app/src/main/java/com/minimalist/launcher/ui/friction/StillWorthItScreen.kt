package com.minimalist.launcher.ui.friction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.data.blocking.Notice
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type

/** After a long unbroken stretch in one app: rate it, then go back in or stop. */
@Composable
fun StillWorthItScreen(notice: Notice.StillWorthIt) {
    val container = appContainer()
    var worth by remember { mutableStateOf<Int?>(null) }
    BackHandler { container.answerStillWorthIt(notice, worth, keepGoing = false) }
    Screen(
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("${notice.minutes} minutes on ${notice.label}", style = Type.small)
            Spacer(Modifier.height(8.dp))
            Label("Still worth it?", style = Type.title.copy(textAlign = TextAlign.Center))
            Spacer(Modifier.height(24.dp))
            Row {
                (1..5).forEach { value ->
                    Choice(value.toString(), selected = worth == value, onClick = { worth = value })
                }
            }
            Label("1 not at all · 5 completely", style = Type.small)
        }
        Spacer(Modifier.weight(1f))
        TextAction(
            text = "Stop here",
            onClick = { container.answerStillWorthIt(notice, worth, keepGoing = false) },
            style = Type.body.copy(color = Palette.Accent),
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TextAction(
            text = "Back to ${notice.label}",
            onClick = { container.answerStillWorthIt(notice, worth, keepGoing = true) },
            style = Type.small,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }
}
