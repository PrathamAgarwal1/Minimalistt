package com.minimalist.launcher.ui.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.protection.Change
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.notifications.NotificationGuard
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.TwoLineRow
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.launch

private val BATCH_OPTIONS = listOf(8 * 60, 10 * 60, 13 * 60, 17 * 60, 20 * 60, 22 * 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationsSettingsScreen(onBack: () -> Unit, onOpenHeld: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by container.modes.state.collectAsStateWithLifecycle()
    val vips by container.notifications.vips.collectAsStateWithLifecycle()
    var listening by remember { mutableStateOf(NotificationGuard.isEnabled(context)) }
    var canPost by remember { mutableStateOf(context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) }
    var vipName by remember { mutableStateOf("") }
    var vipPhone by remember { mutableStateOf("") }
    LifecycleResumeEffect(Unit) {
        listening = NotificationGuard.isEnabled(context)
        canPost = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    val askToPost = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canPost = it }
    fun open(intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Notifications", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Access")
            if (listening) {
                Label("On. Calls and SMS are never held or delayed.", style = Type.body.copy(color = Palette.Accent))
            } else {
                Label("Off. Blocked apps' notifications still show until it's on.", style = Type.body)
                TextAction(text = "Turn on notification access", onClick = {
                    container.grantSettingsPass()
                    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            ComponentName(context, NotificationGuard::class.java).flattenToString(),
                        )
                    if (!open(detail)) open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
                Label(
                    "If it says “Restricted setting”: open App info, tap the three dots, choose Allow restricted settings, then try again.",
                    style = Type.small,
                )
                TextAction(
                    text = "Open App info",
                    onClick = {
                        container.grantSettingsPass()
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    },
                    style = Type.small,
                )
            }
            if (!canPost) {
                Label("Allow Quiet to post notifications, so summaries can reach you.", style = Type.small)
                TextAction(text = "Allow notifications", onClick = { askToPost.launch(Manifest.permission.POST_NOTIFICATIONS) }, style = Type.small)
            }
            TextAction(text = "Held notifications", onClick = onOpenHeld, modifier = Modifier.fillMaxWidth())

            SectionHeader("Batching")
            TwoLineRow(
                title = "Deliver in batches",
                subtitle = if (state.batchingEnabled) {
                    "At " + state.batchTimes.joinToString(", ") { ScheduleRules.formatMinute(it) } + ", as one summary"
                } else {
                    "Off: notifications arrive as they come"
                },
                action = if (state.batchingEnabled) "Turn off" else "Turn on",
                onAction = { container.changes.submit(Change.SetBatching(!state.batchingEnabled, state.batchTimes)) },
            )
            if (state.batchingEnabled) {
                FlowRow {
                    BATCH_OPTIONS.forEach { minute ->
                        val selected = minute in state.batchTimes
                        Choice(ScheduleRules.formatMinute(minute), selected = selected, onClick = {
                            val times = if (selected) state.batchTimes - minute else state.batchTimes + minute
                            if (times.isNotEmpty()) container.changes.submit(Change.SetBatching(true, times))
                        })
                    }
                }
                Label("Essentials from Modes, VIPs, calls and SMS always come straight through.", style = Type.small)
            }

            SectionHeader("VIP contacts")
            Label("Their WhatsApp messages break through anything, and you can reply from the launcher.", style = Type.small)
            vips.forEach { vip ->
                TwoLineRow(title = vip.name, subtitle = "+${vip.phone}", action = "Remove", onAction = {
                    scope.launch { container.notifications.removeVip(vip.id) }
                })
            }
            InputField(
                value = vipName,
                onValueChange = { vipName = it.take(40) },
                placeholder = "Name, as saved in your contacts",
                style = Type.body,
                capitalization = KeyboardCapitalization.Words,
            )
            InputField(
                value = vipPhone,
                onValueChange = { vipPhone = it.take(20) },
                placeholder = "Number with country code, e.g. 91 98765 43210",
                style = Type.body,
                keyboardType = KeyboardType.Phone,
            )
            TextAction(
                text = "Add VIP",
                enabled = vipName.isNotBlank() && vipPhone.count(Char::isDigit) >= 8,
                onClick = {
                    val name = vipName
                    val phone = vipPhone
                    vipName = ""
                    vipPhone = ""
                    container.changes.submit(Change.AddVip(name, phone))
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
