package com.minimalist.launcher.ui.protection

import android.content.Intent
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.protection.AdminReceiver
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.TwoLineRow
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Changes that loosen protection, waiting out their cooldown. */
@Composable
fun PendingChangesScreen(onBack: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val pending by remember { container.changes.pending }.collectAsStateWithLifecycle(emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            container.changes.applyDue()
            delay(1_000)
        }
    }
    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Waiting changes", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label("Anything that loosens protection waits here first. Cancel it if you've changed your mind.", style = Type.small)
            if (pending.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Label("Nothing waiting.", style = Type.body)
            }
            pending.forEach { change ->
                TwoLineRow(
                    title = change.description,
                    subtitle = "Applies in ${DurationFormat.remaining(change.appliesAt - now)}",
                    action = "Cancel",
                    onAction = { scope.launch { container.changes.cancel(change.id) } },
                )
            }
        }
    }
}

/** Notes to your future self, shown before you loosen anything. */
@Composable
fun LettersScreen(onBack: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val letters by remember { container.changes.letters }.collectAsStateWithLifecycle(emptyList())
    var draft by remember { mutableStateOf("") }
    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Letters to yourself", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label(
                "Write while calm. Before you loosen a block or a mode, one of these is shown in full.",
                style = Type.small,
            )
            Spacer(Modifier.height(12.dp))
            InputField(
                value = draft,
                onValueChange = { draft = it.take(2_000) },
                placeholder = "Dear me, when you want to undo this…",
                style = Type.body,
                capitalization = KeyboardCapitalization.Sentences,
                singleLine = false,
            )
            TextAction(text = "Save letter", enabled = draft.isNotBlank(), onClick = {
                val text = draft
                draft = ""
                scope.launch { container.changes.writeLetter(text) }
            })
            if (letters.isNotEmpty()) SectionHeader("Your letters")
            letters.forEach { letter ->
                TwoLineRow(
                    title = letter.text.lineSequence().first().take(60),
                    subtitle = "${letter.text.length} characters",
                    action = "Delete",
                    onAction = { scope.launch { container.changes.deleteLetter(letter.id) } },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Uninstall protection, the emergency unlock and what's guarded. */
@Composable
fun ProtectionScreen(onBack: () -> Unit, onOpenPending: () -> Unit, onOpenLetters: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    var admin by remember { mutableStateOf(AdminReceiver.isActive(context)) }
    LifecycleResumeEffect(Unit) {
        admin = AdminReceiver.isActive(context)
        onPauseOrDispose {}
    }
    val pending by remember { container.changes.pending }.collectAsStateWithLifecycle(emptyList())
    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Protection", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label(
                "Blocks can be extended, never shortened. The ways in early are unlock challenges (a short pass) " +
                    "and the monthly emergency unlock. Loosening anything waits ${DurationFormat.remaining(container.timeScale.value.cooldown.toMillis())}.",
                style = Type.small,
            )
            TextAction(text = "Waiting changes (${pending.size})", onClick = onOpenPending, modifier = Modifier.fillMaxWidth())
            TextAction(text = "Letters to yourself", onClick = onOpenLetters, modifier = Modifier.fillMaxWidth())

            SectionHeader("Uninstall protection")
            if (admin) {
                Label("On. Minimalist can't be uninstalled until this is switched off, and that page is closed while protection is on.", style = Type.body.copy(color = Palette.Accent))
            } else {
                Label("Off. Turning it on uses Device Admin with no other powers.", style = Type.body)
                TextAction(text = "Turn on", onClick = {
                    container.grantSettingsPass()
                    runCatching { context.startActivity(AdminReceiver.requestIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
            }

            SectionHeader("Android Settings")
            Label(
                "Wi-Fi, sound and the rest stay open. While anything is protected, these pages close: Minimalist's app info, " +
                    "accessibility and notification access, Device Admin, uninstalling, Date & time and Developer options. " +
                    "During modes and challenges all of Settings is closed.",
                style = Type.small,
            )

            SectionHeader("Emergency")
            EmergencyUnlockAction(style = Type.body)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Shown after the Settings guard closes a page. */
@Composable
fun SettingsClosedScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Screen(
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Label("That page is closed", style = Type.title)
            Spacer(Modifier.height(12.dp))
            Label(
                "It could switch your protection off. Wi-Fi, sound and the rest of Settings still work.",
                style = Type.small.copy(textAlign = TextAlign.Center),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmergencyUnlockAction(alignment = Alignment.Center)
            TextAction(text = "Back to home", onClick = onClose, style = Type.small, alignment = Alignment.Center)
        }
        Spacer(Modifier.height(12.dp))
    }
}
