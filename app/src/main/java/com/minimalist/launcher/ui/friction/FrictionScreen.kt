package com.minimalist.launcher.ui.friction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minimalist.launcher.data.blocking.Notice
import com.minimalist.launcher.domain.friction.FrictionRules
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.delay

private enum class Step { Urge, Reason, Session, Countdown }

private val URGE_WORDS = listOf("", "barely", "mild", "noticeable", "strong", "overwhelming")

/** The slow way into a friction app: how strong is the pull, why, for how long, then wait. */
@Composable
fun FrictionScreen(notice: Notice.Friction) {
    val container = appContainer()
    var step by remember(notice) { mutableStateOf(Step.Urge) }
    var urge by remember(notice) { mutableIntStateOf(0) }
    var reason by remember(notice) { mutableStateOf("") }
    var minutes by remember(notice) { mutableIntStateOf(FrictionRules.SESSION_MINUTES.first()) }
    var countdown by remember(notice) { mutableIntStateOf(-1) }

    fun notNow() = container.resistFriction(notice.packageName, urge.takeIf { it > 0 }, reason)

    LaunchedEffect(notice) {
        val opens = container.friction.opensToday(notice.packageName)
        countdown = FrictionRules.countdownSeconds(opens, container.timeScale.value)
    }
    BackHandler(onBack = ::notNow)

    Screen(
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Spacer(Modifier.weight(1f))
        Crossfade(targetState = step, animationSpec = tween(200), label = "friction") { current ->
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                when (current) {
                    Step.Urge -> {
                        Label("How strong is the pull to open ${notice.label}?", style = Type.title.copy(textAlign = TextAlign.Center))
                        Spacer(Modifier.height(24.dp))
                        Row {
                            (1..5).forEach { value ->
                                Choice(value.toString(), selected = value == urge, onClick = {
                                    urge = value
                                    step = Step.Reason
                                })
                            }
                        }
                        Label("1 barely · 5 overwhelming", style = Type.small)
                    }
                    Step.Reason -> {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        Label("Why are you opening ${notice.label}?", style = Type.title.copy(textAlign = TextAlign.Center))
                        Label("The pull feels ${URGE_WORDS[urge]}.", style = Type.small)
                        Spacer(Modifier.height(16.dp))
                        InputField(
                            value = reason,
                            onValueChange = { reason = it.take(120) },
                            placeholder = "To…",
                            modifier = Modifier.focusRequester(focus),
                            style = Type.body,
                            capitalization = KeyboardCapitalization.Sentences,
                            onImeAction = { if (FrictionRules.isValidReason(reason)) step = Step.Session },
                        )
                        TextAction(
                            text = "Continue",
                            onClick = { step = Step.Session },
                            enabled = FrictionRules.isValidReason(reason),
                            alignment = Alignment.Center,
                        )
                    }
                    Step.Session -> {
                        Label("For how long?", style = Type.title)
                        Label("${notice.label} closes when the time is up.", style = Type.small)
                        Spacer(Modifier.height(16.dp))
                        Row {
                            FrictionRules.SESSION_MINUTES.forEach { option ->
                                Choice("$option min", selected = option == minutes, onClick = {
                                    minutes = option
                                    step = Step.Countdown
                                })
                            }
                        }
                    }
                    Step.Countdown -> Countdown(
                        label = notice.label,
                        seconds = countdown,
                        onFinished = { container.openAfterFriction(notice.packageName, urge, reason, minutes) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextAction(
            text = "Not now",
            onClick = ::notNow,
            style = Type.small,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Countdown(label: String, seconds: Int, onFinished: () -> Unit) {
    var left by remember(seconds) { mutableIntStateOf(seconds) }
    LaunchedEffect(seconds) {
        if (seconds < 0) return@LaunchedEffect
        while (left > 0) {
            delay(1_000)
            left--
        }
        onFinished()
    }
    Label("Opening $label in", style = Type.small)
    Spacer(Modifier.height(8.dp))
    Label(if (left >= 0) "$left" else "", style = Type.clock)
    Spacer(Modifier.height(8.dp))
    Label("Each open today waits twice as long as the last.", style = Type.small.copy(color = Palette.Faint))
}
