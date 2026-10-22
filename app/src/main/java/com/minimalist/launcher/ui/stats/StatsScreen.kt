package com.minimalist.launcher.ui.stats

import com.minimalist.launcher.system.TrustedClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.db.FirstOpenEntity
import com.minimalist.launcher.data.friction.FrictionRepository
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.friction.WantingLiking
import com.minimalist.launcher.domain.stats.StatsSummary
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.ScreenTitle
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.components.TwoLineRow
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatsScreen(onBack: () -> Unit, onOpenWantingLiking: () -> Unit, onOpenWeeklyReview: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var week by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<StatsSummary?>(null) }
    var opens by remember { mutableStateOf<List<FirstOpenEntity>>(emptyList()) }
    var exported by remember { mutableStateOf<String?>(null) }
    val today = remember { TrustedClock.today().toEpochDay() }
    val fromDay = if (week) today - 6 else today

    LaunchedEffect(week) {
        summary = container.stats.summary(fromDay, today)
        opens = container.stats.firstOpens(fromDay, today)
    }
    val since = remember(week) { FrictionRepository.daysAgo(if (week) 6 else 0) }
    val feelings by remember(week) { container.friction.pullsSince(since).map(WantingLiking::summarize) }.collectAsStateWithLifecycle(emptyList())

    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exported = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { container.stats.export(it) } ?: error("no stream")
                }
            }.fold(onSuccess = { "Saved." }, onFailure = { "Couldn't save: ${it.message}" })
        }
    }

    Screen {
        Spacer(Modifier.height(40.dp))
        ScreenTitle("Stats", onBack)
        Row {
            Choice("Today", selected = !week, onClick = { week = false })
            Choice("Last 7 days", selected = week, onClick = { week = true })
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TextAction(text = "Weekly review", onClick = onOpenWeeklyReview, style = Type.small)
            val s = summary
            if (s != null) {
                Spacer(Modifier.height(8.dp))
                Label(DurationFormat.short(s.screenTimeMillis), style = Type.clock)
                Label("on your phone", style = Type.small)
                Spacer(Modifier.height(16.dp))
                Figure("Opens", "${s.opens}")
                Figure("Urges resisted", "${s.urgesResisted}")
                Figure("Blocked attempts", "${s.blockedAttempts}")
                Figure("In Put away", DurationFormat.short(s.putAwayMillis))
                Figure("Earned", "${s.earnedMinutes} min")
                Figure("Urgent messages", "${s.urgentMessages}")

                if (week) {
                    SectionHeader("Each day")
                    val most = s.perDay.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                    val dayName = DateTimeFormatter.ofPattern("EEE")
                    s.perDay.forEach { (day, millis) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp)) {
                            Label(LocalDate.ofEpochDay(day).format(dayName), style = Type.small, modifier = Modifier.width(44.dp))
                            Box(
                                Modifier
                                    .height(2.dp)
                                    .width((180 * millis / most).toInt().coerceAtLeast(1).dp)
                                    .background(if (day == today) Palette.Accent else Palette.Faint),
                            )
                            Spacer(Modifier.width(12.dp))
                            Label(DurationFormat.short(millis), style = Type.small)
                        }
                    }
                }

                if (s.topApps.isNotEmpty()) {
                    SectionHeader("Most used")
                    s.topApps.forEach { (pkg, millis) -> Figure(container.labelFor(pkg), DurationFormat.short(millis)) }
                }
            }

            if (opens.isNotEmpty()) {
                SectionHeader("First opened")
                val dayName = DateTimeFormatter.ofPattern("EEE d")
                opens.forEach { open ->
                    val at = Instant.ofEpochMilli(open.at).atZone(ZoneId.systemDefault())
                    Figure(LocalDate.ofEpochDay(open.day).format(dayName), "%02d:%02d".format(at.hour, at.minute) + if (open.early) ", early" else "")
                }
            }

            SectionHeader("Wanting vs liking")
            if (feelings.isEmpty()) Label("No ratings yet.", style = Type.small)
            feelings.take(6).forEach { feeling ->
                TwoLineRow(
                    title = container.labelFor(feeling.packageName),
                    subtitle = listOfNotNull(
                        feeling.averageUrge?.let { "urge %.1f".format(it) },
                        feeling.averageWorth?.let { "worth %.1f".format(it) },
                        "${feeling.opens} opens",
                    ).joinToString(" · "),
                )
            }
            TextAction(text = "All of wanting vs liking", onClick = onOpenWantingLiking, style = Type.small)

            SectionHeader("Export")
            Label("Everything Minimalist has recorded, as CSV files in one zip. It stays where you save it.", style = Type.small)
            TextAction(text = "Export as CSV", onClick = { saveZip.launch("minimalist-${TrustedClock.today()}.zip") })
            exported?.let { Label(it, style = Type.small.copy(color = Palette.Accent)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label, style = Type.body, modifier = Modifier.weight(1f))
        Label(value, style = Type.body)
    }
}
