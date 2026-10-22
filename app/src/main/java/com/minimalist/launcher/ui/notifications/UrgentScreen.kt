package com.minimalist.launcher.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.notifications.Vip
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.launch

/** Message a VIP on WhatsApp from anywhere, even in a mode; or answer their last message here. */
@Composable
fun UrgentScreen(onBack: () -> Unit, onAddVips: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val vips by container.notifications.vips.collectAsStateWithLifecycle()
    val messages by container.notifications.vipMessages.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Vip?>(null) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Urgent message", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            status?.let { Label(it, style = Type.small.copy(color = Palette.Accent)) }
            if (vips.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Label("No VIP contacts yet.", style = Type.body)
                TextAction(text = "Add VIPs in notification settings", onClick = onAddVips)
                return@Column
            }

            SectionHeader("To")
            vips.forEach { vip ->
                Choice(vip.name, selected = vip == selected, onClick = { selected = vip }, modifier = Modifier.fillMaxWidth())
            }
            val vip = selected
            if (vip != null) {
                val last = messages[vip.id]
                if (last != null) {
                    SectionHeader("Their last message")
                    Label(last.text, style = Type.body)
                }
                Spacer(Modifier.height(12.dp))
                InputField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    placeholder = "Message",
                    style = Type.body,
                    capitalization = KeyboardCapitalization.Sentences,
                    singleLine = false,
                )
                val canSend = text.isNotBlank()
                if (last?.reply != null) {
                    TextAction(text = "Reply here, without opening WhatsApp", enabled = canSend, onClick = {
                        scope.launch {
                            val sent = container.sendQuickReply(vip.id, text.trim())
                            status = if (sent) "Sent to ${vip.name}." else "Couldn't send. Try WhatsApp instead."
                            if (sent) text = ""
                        }
                    })
                }
                TextAction(text = "Open in WhatsApp for one minute", enabled = canSend, onClick = {
                    scope.launch {
                        val sent = container.sendUrgent(vip, text.trim())
                        status = if (sent) null else "Couldn't open WhatsApp. Is it installed?"
                        if (sent) text = ""
                    }
                })
                Label("WhatsApp opens on their chat with your message typed in. After a minute you return here.", style = Type.small)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
