package com.minimalist.launcher.ui.setup

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.minimalist.launcher.blocking.BlockerService
import com.minimalist.launcher.modes.Grayscale
import com.minimalist.launcher.modes.GuardService
import com.minimalist.launcher.notifications.NotificationGuard
import com.minimalist.launcher.protection.AdminReceiver
import com.minimalist.launcher.system.HomeRole
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type

/** One thing to set up: why it's needed, whether it's done, and a button to fix it. */
private data class Step(
    val title: String,
    val why: String,
    /** Null when Android doesn't tell apps. */
    val done: Boolean?,
    val action: String? = null,
    val onFix: (() -> Unit)? = null,
    val note: String? = null,
)

/**
 * The first-run walkthrough and, later, the status screen: every permission and ColorOS
 * setting, whether it works, and a button for whatever doesn't. [onFinish] is set on first run.
 */
@Composable
fun SetupScreen(onBack: () -> Unit, onFinish: (() -> Unit)? = null) {
    val container = appContainer()
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val askHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) GuardService.startCountingSteps(context)
        refresh++
    }
    fun open(intent: Intent): Boolean {
        container.grantSettingsPass()
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
    fun appInfo() = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    // Reading the counter makes every resume recompute the statuses below.
    check(refresh >= 0)
    val blocking = BlockerService.isEnabled(context)
    val listening = NotificationGuard.isEnabled(context)
    val steps = listOf(
        Step(
            title = "Home app",
            why = "Quiet replaces your home screen.",
            done = HomeRole.isDefaultHome(context),
            action = "Set as Home app",
            onFix = {
                runCatching { askHome.launch(HomeRole.requestIntent(context)) }.onFailure { open(HomeRole.settingsIntent()) }
            },
            note = "Or Settings, Apps, Default apps, Home app.",
        ),
        Step(
            title = "Allow restricted settings",
            why = "Android 16 locks accessibility and notification access for sideloaded apps until you allow it.",
            done = if (blocking && listening) true else null,
            action = "Open App info",
            onFix = { appInfo() },
            note = "Tap the three dots at the top right, then Allow restricted settings. The option appears after you've been refused once.",
        ),
        Step(
            title = "Blocking",
            why = "Notices which app or website is open, so blocked ones send you home.",
            done = blocking,
            action = "Turn on",
            onFix = {
                val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, BlockerService::class.java).flattenToString())
                if (!open(details)) open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        ),
        Step(
            title = "Notification access",
            why = "Holds notifications from blocked apps and during modes, and lets VIPs through.",
            done = listening,
            action = "Turn on",
            onFix = {
                val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, NotificationGuard::class.java).flattenToString())
                if (!open(detail)) open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        ),
        Step(
            title = "Notifications",
            why = "For the summaries of held notifications and the bedtime reminder.",
            done = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
            action = "Allow",
            onFix = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
        ),
        Step(
            title = "Background service",
            why = "Keeps Put away, night and pickups on time.",
            done = GuardService.running,
            action = "Start",
            onFix = {
                GuardService.start(context)
                refresh++
            },
        ),
        Step(
            title = "Battery: allow background activity",
            why = "ColorOS stops apps it thinks are idle. This keeps Quiet running.",
            done = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            action = "Allow",
            onFix = {
                val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                if (!open(ask)) open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
            note = "Also App info, Battery: choose Allow background activity.",
        ),
        Step(
            title = "Auto-launch",
            why = "Lets ColorOS start Quiet after a restart.",
            done = null,
            action = "Open",
            onFix = { if (!openAutoLaunch(::open)) appInfo() },
            note = "Find Quiet and switch it on.",
        ),
        Step(
            title = "Lock in recent apps",
            why = "Stops the recent apps screen from clearing Quiet.",
            done = null,
            note = "Open recent apps, tap the three dots on Quiet (or hold it), and choose Lock.",
        ),
        Step(
            title = "Display over other apps",
            why = "For the red tint during wind-down and night.",
            done = Settings.canDrawOverlays(context),
            action = "Allow",
            onFix = { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) },
        ),
        Step(
            title = "Step counting",
            why = "Steps earn time for earned-time apps.",
            done = context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED,
            action = "Allow",
            onFix = { askPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
        ),
        Step(
            title = "Uninstall protection",
            why = "Device Admin, with no other powers, so Quiet can't be removed on a whim.",
            done = AdminReceiver.isActive(context),
            action = "Turn on",
            onFix = { open(AdminReceiver.requestIntent(context)) },
        ),
        Step(
            title = "Grayscale",
            why = "Wind-down and night turn the screen grey. Needs a one-time command from a computer.",
            done = Grayscale.canControl(context),
            note = Grayscale.GRANT_COMMAND,
        ),
    )
    val ready = steps.count { it.done == true }
    val known = steps.count { it.done != null }

    // As a first-run overlay it must not let taps through to the home screen below.
    val swallow = if (onFinish != null) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
    } else {
        Modifier
    }
    Screen(modifier = swallow) {
        Spacer(Modifier.height(40.dp))
        if (onFinish == null) {
            ScreenTitle("Setup and status", onBack)
        } else {
            Label("Set up Quiet", style = Type.title)
            Spacer(Modifier.height(4.dp))
        }
        Label("$ready of $known working. Everything is optional; each part says what it's for.", style = Type.small)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            steps.forEach { step -> StepRow(step) }
            SectionHeader("Sideloading")
            Label(
                "If Play Protect blocked the install (\"can request access to sensitive data\"), pause Play Protect while installing, " +
                    "or install from a computer with adb install -r. The README has both.",
                style = Type.small,
            )
            Spacer(Modifier.height(24.dp))
        }
        if (onFinish != null) {
            TextAction(text = "Done for now", onClick = onFinish, style = Type.body.copy(color = Palette.Accent))
            Label("You can come back any time: Settings, Setup and status.", style = Type.small)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StepRow(step: Step) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(step.title, style = Type.body, modifier = Modifier.weight(1f))
            Label(
                when (step.done) {
                    true -> "Working"
                    false -> "Off"
                    null -> "Check"
                },
                style = Type.small.copy(color = if (step.done == true) Palette.Accent else Palette.Dim),
            )
        }
        Label(step.why, style = Type.small)
        step.note?.takeIf { step.done != true }?.let { Label(it, style = Type.small.copy(color = Palette.Faint)) }
        if (step.done != true && step.onFix != null && step.action != null) {
            TextAction(text = step.action, onClick = step.onFix, style = Type.small)
        }
    }
}

/** ColorOS keeps auto-launch in its security centre, under a name that changes between versions. */
private fun openAutoLaunch(open: (Intent) -> Boolean): Boolean {
    val candidates = listOf(
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
    )
    return candidates.any { (pkg, cls) -> open(Intent().setComponent(ComponentName(pkg, cls))) }
}
