package com.minimalist.launcher.ui.stats

import com.minimalist.launcher.system.TrustedClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minimalist.launcher.data.friction.FrictionRepository
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.friction.WantingLiking
import com.minimalist.launcher.domain.stats.StatsSummary
import com.minimalist.launcher.domain.stats.WeeklyReview
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Sunday evening: this week against last, the best and worst day, and one thing to change. */
@Composable
fun WeeklyReviewScreen(onClose: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val today = remember { TrustedClock.today().toEpochDay() }
    var thisWeek by remember { mutableStateOf<StatsSummary?>(null) }
    var lastWeek by remember { mutableStateOf<StatsSummary?>(null) }
    var earlyOpens by remember { mutableStateOf(0) }
    var earlyUnlocks by remember { mutableStateOf(0) }
    var blocked by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        thisWeek = container.stats.summary(today - 6, today)
        lastWeek = container.stats.summary(today - 13, today - 7)
        earlyOpens = container.stats.firstOpens(today - 6, today).count { it.early }
        earlyUnlocks = container.unlocks.earlyUnlocksPastWeek()
    }
    val feelings by remember { container.friction.pullsSince(FrictionRepository.daysAgo(6)).map(WantingLiking::summarize) }
        .collectAsStateWithLifecycle(emptyList())
    BackHandler(onBack = onClose)

    Screen(
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Spacer(Modifier.height(56.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Label("Your week", style = Type.title)
            Label("The last 7 days", style = Type.small)
            val now = thisWeek
            if (now != null) {
                Spacer(Modifier.height(16.dp))
                Label(DurationFormat.short(now.screenTimeMillis), style = Type.clock)
                val change = lastWeek?.let { WeeklyReview.change(it.screenTimeMillis, now.screenTimeMillis) }
                Label(
                    when {
                        change == null -> "on your phone"
                        change < 0 -> "on your phone, ${-change}% less than last week"
                        change > 0 -> "on your phone, $change% more than last week"
                        else -> "on your phone, the same as last week"
                    },
                    style = Type.small.copy(color = if (change != null && change < 0) Palette.Accent else Palette.Text),
                )

                val dayName = DateTimeFormatter.ofPattern("EEEE")
                val days = now.perDay.filter { it.first <= today }
                val best = days.minByOrNull { it.second }
                val worst = days.maxByOrNull { it.second }
                SectionHeader("Days")
                best?.let { Figure("Best, ${LocalDate.ofEpochDay(it.first).format(dayName)}", DurationFormat.short(it.second)) }
                worst?.takeIf { it != best }?.let {
                    Figure("Worst, ${LocalDate.ofEpochDay(it.first).format(dayName)}", DurationFormat.short(it.second))
                }

                SectionHeader("Holding back")
                Figure("Urges resisted", "${now.urgesResisted}")
                Figure("Blocked attempts", "${now.blockedAttempts}")
                Figure("In Put away", DurationFormat.short(now.putAwayMillis))
                Figure("Early unlocks", "$earlyUnlocks")
                Figure("Early mornings", "$earlyOpens")
            }

            val mismatch = feelings.firstOrNull { it.wantingOutpacesLiking }
            if (mismatch != null) {
                val label = container.labelFor(mismatch.packageName)
                SectionHeader("One thing to change")
                Label(
                    "You wanted $label a lot (urge %.1f) but rated it %.1f afterwards.".format(mismatch.averageUrge, mismatch.averageWorth),
                    style = Type.body,
                )
                if (blocked == mismatch.packageName) {
                    Label("Blocked for a week.", style = Type.small.copy(color = Palette.Accent))
                } else {
                    TextAction(text = "Block it for a week", onClick = {
                        val duration = container.timeScale.value.block(Duration.ofDays(7))
                        scope.launch { container.blocking.extendBlock(mismatch.packageName, duration) }
                        blocked = mismatch.packageName
                    })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        TextAction(text = "Done", onClick = onClose, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label, style = Type.body, modifier = Modifier.weight(1f))
        Label(value, style = Type.body)
    }
}
