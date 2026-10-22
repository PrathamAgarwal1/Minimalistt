package com.minimalist.launcher.ui.modes

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.blocking.BlockingText
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.launch
import java.time.Duration

private val OPTIONS = listOf(15, 30, 60, 120)

@Composable
fun PutAwayStartScreen(onBack: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var custom by remember { mutableStateOf("45") }

    fun start(minutes: Int) {
        scope.launch {
            container.modes.putAway(container.timeScale.value.minutesAsSeconds(Duration.ofMinutes(minutes.toLong())))
            onBack()
        }
    }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Put away", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label(
                "Only calls, messages, the clock and your essentials work. It can be extended, never ended early. " +
                    "Notifications wait and arrive together at the end.",
                style = Type.small,
            )
            Spacer(Modifier.height(8.dp))
            OPTIONS.forEach { minutes ->
                TextAction(text = BlockingText.minutes(minutes), onClick = { start(minutes) }, style = Type.item, modifier = Modifier.fillMaxWidth())
            }
            SectionHeader("Custom")
            Row(verticalAlignment = Alignment.CenterVertically) {
                InputField(
                    value = custom,
                    onValueChange = { value -> custom = value.filter(Char::isDigit).take(3) },
                    placeholder = "0",
                    modifier = Modifier.width(64.dp),
                    style = Type.item,
                    keyboardType = KeyboardType.Number,
                )
                Label("minutes", style = Type.body)
            }
            val minutes = custom.toIntOrNull() ?: 0
            TextAction(text = "Put away for ${BlockingText.minutes(minutes.coerceAtLeast(1))}", onClick = { start(minutes) }, enabled = minutes > 0)
        }
    }
}
