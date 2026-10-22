package com.minimalist.launcher.ui.protection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.minimalist.launcher.domain.protection.EmergencyRules
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Spacing
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.launch

/** "Emergency unlock": three a month come free; past that, a hard challenge for each one. */
@Composable
fun EmergencyUnlockAction(style: TextStyle = Type.small, alignment: Alignment = Alignment.CenterStart) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var freeLeft by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(asking) { freeLeft = EmergencyRules.freeLeft(container.unlocks.emergenciesUsed(container.timeScale.value)) }

    TextAction(text = "Emergency unlock", onClick = { asking = true }, style = style, alignment = alignment)
    if (!asking) return
    Dialog(onDismissRequest = { asking = false }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Background)
                .border(1.dp, Palette.Line)
                .padding(horizontal = Spacing.gutter, vertical = 20.dp),
        ) {
            Label("Emergency unlock", style = Type.title)
            Spacer(Modifier.height(8.dp))
            Label("Lifts every block and mode for ${EmergencyRules.MINUTES} minutes. It's logged.", style = Type.body)
            when (val left = freeLeft) {
                null -> Unit
                0 -> {
                    Label("This month's ${EmergencyRules.FREE_PER_MONTH} free ones are used. Another takes a hard challenge.", style = Type.small)
                    TextAction(text = "Solve problems for another", onClick = {
                        asking = false
                        scope.launch { container.requestEmergencyChallenge() }
                    })
                }
                else -> {
                    Label("$left of ${EmergencyRules.FREE_PER_MONTH} free left this month.", style = Type.small)
                    TextAction(text = "Unlock for ${EmergencyRules.MINUTES} minutes", onClick = {
                        scope.launch {
                            container.useEmergency()
                            asking = false
                        }
                    })
                }
            }
            TextAction(text = "Cancel", onClick = { asking = false }, style = Type.small)
        }
    }
}
