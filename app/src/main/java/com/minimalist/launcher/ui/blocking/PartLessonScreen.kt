package com.minimalist.launcher.ui.blocking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.parts.AppParts
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type

/** Shows Quiet what Reels or Shorts look like now, when an app update has renamed them. */
@Composable
fun PartLessonScreen(partKey: String, onBack: () -> Unit) {
    val container = appContainer()
    val part = AppParts.ALL.firstOrNull { it.key == partKey } ?: return
    val lesson by container.partLesson.collectAsStateWithLifecycle()
    val modes by container.modes.state.collectAsStateWithLifecycle()
    val app = container.labelFor(part.packageName)
    val feed = part.label.removePrefix("$app ")
    DisposableEffect(Unit) { onDispose { container.partLesson.value = null } }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Teach ${part.label}", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label("If $feed stopped being blocked after a $app update, show Quiet what it looks like now.", style = Type.body)
            Spacer(Modifier.height(16.dp))
            Label("1. Tap Start. $app opens. If it opens on $feed, go to its home tab first.", style = Type.small)
            Label("2. Go to $feed and watch for a few seconds.", style = Type.small)
            Label("3. Press Home to come back here.", style = Type.small)
            Spacer(Modifier.height(16.dp))

            val current = lesson?.takeIf { it.partKey == partKey }
            val learned = current?.learned
            when {
                learned == null && current != null ->
                    Label("Watching $app. Go to $feed, then press Home.", style = Type.body.copy(color = Palette.Accent))
                learned != null && learned.isNotEmpty() ->
                    Label("Learned. $feed ${if (part.key in modes.blockedParts) "are" else "will be, once switched on,"} blocked again.", style = Type.body.copy(color = Palette.Accent))
                learned != null ->
                    Label(
                        "Couldn't tell which screen was $feed. Open $app on its home tab, go to $feed, wait a few seconds, then press Home.",
                        style = Type.body,
                    )
            }
            TextAction(
                text = when {
                    learned != null -> "Try again"
                    current != null -> "Start over"
                    else -> "Start"
                },
                onClick = { container.startPartLesson(part) },
            )

            val taught = modes.learnedPartIds[part.key].orEmpty()
            if (taught.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Label("Taught so far: ${taught.size} ${if (taught.size == 1) "screen name" else "screen names"}.", style = Type.small)
            }
        }
    }
}
