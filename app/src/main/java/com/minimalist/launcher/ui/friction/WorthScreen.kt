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
import androidx.compose.runtime.remember
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
import com.minimalist.launcher.ui.theme.Type

/** Asked when a friction session ends. Feeds the wanting vs liking view. */
@Composable
fun WorthScreen(notice: Notice.WorthIt) {
    val container = appContainer()
    BackHandler { container.rateWorth(notice.logId, null) }
    Screen(
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("Was that time on ${notice.label} worth it?", style = Type.title.copy(textAlign = TextAlign.Center))
            Spacer(Modifier.height(24.dp))
            Row {
                (1..5).forEach { value ->
                    Choice(value.toString(), selected = false, onClick = { container.rateWorth(notice.logId, value) })
                }
            }
            Label("1 not at all · 5 completely", style = Type.small)
        }
        Spacer(Modifier.weight(1f))
        TextAction(
            text = "Skip",
            onClick = { container.rateWorth(notice.logId, null) },
            style = Type.small,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }
}
