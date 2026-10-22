package com.minimalist.launcher.ui.notifications

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.notifications.NotificationsRepository
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.TwoLineRow
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HeldNotificationsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val held by remember { container.notifications.held }.collectAsStateWithLifecycle(emptyList())
    val time = remember { DateTimeFormatter.ofPattern("EEE HH:mm") }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Held notifications", onBack)
        if (held.isEmpty()) {
            Label("Nothing held.", style = Type.body)
        } else {
            TextAction(text = "Clear all", onClick = { scope.launch { container.notifications.clearHeld() } }, style = Type.small)
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(held, key = { it.id }) { item ->
                val why = when (item.reason) {
                    NotificationsRepository.BLOCKED -> "blocked"
                    NotificationsRepository.MODE -> "held in a mode"
                    else -> "batched"
                }
                val at = Instant.ofEpochMilli(item.postedAt).atZone(ZoneId.systemDefault()).format(time)
                TwoLineRow(
                    title = "${container.labelFor(item.packageName)}: ${item.title.orEmpty()}",
                    subtitle = listOfNotNull(item.text, "$at · $why").joinToString("\n"),
                )
            }
        }
    }
}
