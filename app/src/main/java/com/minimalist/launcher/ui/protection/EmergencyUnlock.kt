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

/** A quiet "Emergency unlock" action with its own confirmation. Works once a calendar month. */
@Composable
fun EmergencyUnlockAction(style: TextStyle = Type.small, alignment: Alignment = Alignment.CenterStart) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(asking) { available = container.unlocks.emergencyAvailable(container.timeScale.value) }

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
            when (available) {
                true -> {
                    Label(
                        "Lifts every block and mode for ${EmergencyRules.MINUTES} minutes. You get one each calendar month, and it's logged.",
                        style = Type.body,
                    )
                    TextAction(text = "Unlock for ${EmergencyRules.MINUTES} minutes", onClick = {
                        scope.launch {
                            container.useEmergency()
                            asking = false
                        }
                    })
                }
                false -> Label("This month's emergency unlock is already used. A new one comes on the 1st.", style = Type.body)
                null -> Unit
            }
            TextAction(text = "Cancel", onClick = { asking = false }, style = Type.small)
        }
    }
}
