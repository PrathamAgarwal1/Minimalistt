package com.minimalist.launcher.ui.block

import com.minimalist.launcher.system.TrustedClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.data.blocking.Notice
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.blocking.BlockReason
import com.minimalist.launcher.domain.blocking.ScheduleRules
import com.minimalist.launcher.ui.blocking.BlockingText
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.protection.EmergencyUnlockAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private const val URGE_WAVE_SECONDS = 60

/** Shown in place of a blocked app. Calm, never scolding. */
@Composable
fun BlockScreen(notice: Notice.Blocked, onClose: () -> Unit, onUrgeRidden: () -> Unit = {}, onUnlockEarly: () -> Unit = {}) {
    var now by remember { mutableLongStateOf(TrustedClock.nowMillis()) }
    var riding by remember(notice) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            now = TrustedClock.nowMillis()
            delay(1_000)
        }
    }
    BackHandler(onBack = onClose)

    val centered = Type.small.copy(textAlign = TextAlign.Center)
    // Swallow taps so nothing underneath the block screen can be pressed.
    Screen(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
        ),
    ) {
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (riding) {
                UrgeWave(appLabel = notice.label, onDone = onUrgeRidden)
            } else {
                Label(notice.label, style = Type.title)
                Spacer(Modifier.height(12.dp))
                Label(reasonText(notice.decision.primary), style = centered)
                Spacer(Modifier.height(4.dp))
                val left = notice.decision.untilMillis - now
                if (notice.decision.untilMillis == Long.MAX_VALUE) {
                    Unit
                } else if (left > 0) {
                    Label("${DurationFormat.remaining(left)} to go", style = Type.body.copy(color = Palette.Accent))
                } else {
                    Label("It's open again now.", style = Type.body.copy(color = Palette.Accent))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!riding) {
                TextAction(text = "Ride the urge wave · 60 s", onClick = { riding = true }, alignment = Alignment.Center)
                TextAction(text = "Solve problems to get in early", onClick = onUnlockEarly, style = Type.small, alignment = Alignment.Center)
            }
            TextAction(text = "Back to home", onClick = onClose, style = Type.small, alignment = Alignment.Center)
            EmergencyUnlockAction(style = Type.small.copy(color = Palette.Faint), alignment = Alignment.Center)
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** A minute of slow breathing: urges rise, peak and pass like a wave. */
@Composable
fun UrgeWave(appLabel: String, onDone: () -> Unit = {}) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (elapsed < URGE_WAVE_SECONDS) {
            delay(1_000)
            elapsed++
        }
        onDone()
    }
    val done = elapsed >= URGE_WAVE_SECONDS
    // 4 s in, 6 s out: a slow exhale settles the urge.
    val phase = when {
        done -> "The wave has passed."
        elapsed % 10 < 4 -> "Breathe in"
        else -> "Breathe out"
    }
    Crossfade(targetState = phase, animationSpec = tween(900), label = "breath") {
        Label(it, style = Type.title)
    }
    Spacer(Modifier.height(12.dp))
    Label(
        if (done) "You noticed the pull toward $appLabel and let it go." else "${URGE_WAVE_SECONDS - elapsed}",
        style = Type.small.copy(textAlign = TextAlign.Center),
    )
}

private fun reasonText(reason: BlockReason): String = when (reason) {
    is BlockReason.Timed -> "You chose to block this ${BlockingText.until(reason.untilMillis)}."
    is BlockReason.Scheduled -> {
        val end = Instant.ofEpochMilli(reason.untilMillis).atZone(ZoneId.systemDefault())
        "“${reason.scheduleName}” runs until ${ScheduleRules.formatMinute(end.hour * 60 + end.minute)}."
    }
    is BlockReason.TimeLimit -> "Today's ${BlockingText.minutes(reason.limitMinutes)} are used up. It opens again at midnight."
    is BlockReason.OpenLimit -> "You've opened it ${reason.maxOpens} ${if (reason.maxOpens == 1) "time" else "times"} today, your limit. It opens again at midnight."
    is BlockReason.Locked -> "${reason.kind.label} is on. Only calls, messages and your essentials work ${BlockingText.until(reason.untilMillis)}."
    is BlockReason.OtherBrowser -> "Only ${reason.allowedLabel} is allowed for browsing."
    is BlockReason.Part -> "${reason.label} are blocked. The rest of ${reason.appLabel} works."
    is BlockReason.EarnedUp -> "Today's earned time is used up. Walk, or finish a focus session, to earn more."
    is BlockReason.Website ->
        if (reason.untilMillis == Long.MAX_VALUE) {
            "You blocked this website."
        } else {
            "Blocked along with its app, ${BlockingText.until(reason.untilMillis)}."
        }
}
