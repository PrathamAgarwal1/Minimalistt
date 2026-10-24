package com.minimalist.launcher.ui.settings

import com.minimalist.launcher.system.TrustedClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.BuildConfig
import com.minimalist.launcher.blocking.BlockerService
import com.minimalist.launcher.data.settings.Appearance
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.domain.parts.AppParts
import com.minimalist.launcher.domain.protection.EmergencyRules
import com.minimalist.launcher.system.HomeRole
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.appViewModel
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.NavRow
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.TEXT_SCALES
import com.minimalist.launcher.ui.theme.Type

/** Every page, grouped, each saying in one line how things stand. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenHomeApps: () -> Unit,
    onOpenBlocking: () -> Unit,
    onOpenModes: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenWebsites: () -> Unit,
    onOpenProtection: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenEarn: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenWeeklyReview: () -> Unit,
    viewModel: SettingsViewModel = appViewModel { SettingsViewModel(it) },
) {
    val container = appContainer()
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rules by container.blocking.rules.collectAsStateWithLifecycle()
    val modes by container.modes.state.collectAsStateWithLifecycle()
    val earn by container.earn.state.collectAsStateWithLifecycle()
    val vips by container.notifications.vips.collectAsStateWithLifecycle()
    val sites by container.websites.customDomains.collectAsStateWithLifecycle()
    val look by container.settings.appearance.collectAsStateWithLifecycle(Appearance())
    val pending by container.changes.pending.collectAsStateWithLifecycle(emptyList())
    val screenTime by remember { container.usage.totalForegroundToday(context.packageName) }.collectAsStateWithLifecycle(0L)
    var serviceOn by remember { mutableStateOf(BlockerService.isEnabled(context)) }
    var isHome by remember { mutableStateOf(HomeRole.isDefaultHome(context)) }
    var emergenciesLeft by remember { mutableIntStateOf(-1) }
    LifecycleResumeEffect(Unit) {
        serviceOn = BlockerService.isEnabled(context)
        isHome = HomeRole.isDefaultHome(context)
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) {
        emergenciesLeft = EmergencyRules.freeLeft(container.unlocks.emergenciesUsed(container.timeScale.value))
    }
    val now = TrustedClock.nowMillis()

    Screen(settingsWallpaper = true) {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Settings", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Focus")
            NavRow(
                title = "Blocking",
                summary = if (!serviceOn) {
                    "Off. Tap to turn it on"
                } else {
                    val blocked = rules.blocks.count { it.value > now }
                    val parts = AppParts.ALL.filter { it.key in modes.blockedParts }.map { it.label }
                    listOfNotNull(
                        blocked.takeIf { it > 0 }?.let { "$it blocked" },
                        rules.schedules.count { it.enabled }.takeIf { it > 0 }?.let { "$it ${plural(it, "schedule")}" },
                        rules.limits.size.takeIf { it > 0 }?.let { "$it ${plural(it, "limit")}" },
                        parts.takeIf { it.isNotEmpty() }?.joinToString(" and ")?.let { "no $it" },
                    ).joinToString(" · ").ifEmpty { "Nothing blocked yet" }
                },
                summaryColor = if (serviceOn) Palette.Dim else Palette.Accent,
                onClick = onOpenBlocking,
            )
            NavRow(
                title = "Websites and browsers",
                summary = listOfNotNull(
                    sites.size.takeIf { it > 0 }?.let { "$it ${plural(it, "site")} of your own" },
                    modes.allowedBrowser?.let { "only ${container.labelFor(it)}" },
                ).joinToString(" · ").ifEmpty { "Blocked apps' sites are blocked too" },
                onClick = onOpenWebsites,
            )
            NavRow(
                title = "Earned screen time",
                summary = if (earn.apps.isEmpty()) "Off" else "${earn.apps.size} ${plural(earn.apps.size, "app")} on steps and focus",
                onClick = onOpenEarn,
            )

            SectionHeader("Modes")
            val days = modes.dayModes
            NavRow(
                title = "Put away, night and wind-down",
                summary = if (days.nightEnabled) {
                    "Night ${ScheduleRules.formatMinute(days.bedWeekday)} to ${ScheduleRules.formatMinute(days.openWeekday)}"
                } else {
                    "Night is off"
                },
                onClick = onOpenModes,
            )
            NavRow(
                title = "Notifications and VIPs",
                summary = listOfNotNull(
                    "${vips.size} ${plural(vips.size, "VIP")}",
                    modes.batchTimes.takeIf { modes.batchingEnabled }?.joinToString(", ") { ScheduleRules.formatMinute(it) }?.let { "batched at $it" },
                ).joinToString(" · "),
                onClick = onOpenNotifications,
            )

            SectionHeader("You")
            NavRow(title = "Stats and export", summary = "${DurationFormat.short(screenTime)} today", onClick = onOpenStats)
            NavRow(title = "Weekly review", summary = "Sundays from 18:00", onClick = onOpenWeeklyReview)
            NavRow(
                title = "Emergency unlock and letters",
                summary = listOfNotNull(
                    emergenciesLeft.takeIf { it >= 0 }?.let { "$it of ${EmergencyRules.FREE_PER_MONTH} free emergency unlocks left" },
                    pending.size.takeIf { it > 0 }?.let { "$it ${plural(it, "change")} waiting" },
                ).joinToString(" · ").ifEmpty { null },
                onClick = onOpenProtection,
            )

            SectionHeader("Phone")
            NavRow(
                title = "Home screen",
                summary = if (!isHome) {
                    "Not your Home app yet"
                } else {
                    "${state.favourites.size} ${plural(state.favourites.size, "favourite")} · ${state.hidden.size} hidden"
                },
                summaryColor = if (isHome) Palette.Dim else Palette.Accent,
                onClick = onOpenHomeApps,
            )
            NavRow(
                title = "Appearance",
                summary = listOf(
                    (TEXT_SCALES.firstOrNull { it.second == look.textScale }?.first ?: "Medium") + " text",
                    look.accent,
                    if (look.centred) "centred" else "left",
                ).joinToString(" · "),
                onClick = onOpenAppearance,
            )
            NavRow(title = "Setup and status", summary = "Permissions and ColorOS steps", onClick = onOpenSetup)

            Spacer(Modifier.height(32.dp))
            Label("Quiet ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " · testing build" else ""}", style = Type.small.copy(color = Palette.Faint))
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun plural(count: Int, word: String) = if (count == 1) word else word + "s"
