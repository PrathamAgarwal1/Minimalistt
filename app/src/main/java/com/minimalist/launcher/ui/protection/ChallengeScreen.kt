package com.minimalist.launcher.ui.protection

import android.os.PowerManager
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.minimalist.launcher.data.protection.UnlockRequest
import com.minimalist.launcher.data.protection.UnlockTarget
import com.minimalist.launcher.domain.DurationFormat
import com.minimalist.launcher.domain.challenge.ChallengePlan
import com.minimalist.launcher.domain.challenge.ChallengeRules
import com.minimalist.launcher.domain.challenge.ChallengeSession
import com.minimalist.launcher.domain.challenge.Problem
import com.minimalist.launcher.domain.friction.FrictionRules
import com.minimalist.launcher.ui.appContainer
import com.minimalist.launcher.ui.components.Choice
import com.minimalist.launcher.ui.components.InputField
import com.minimalist.launcher.ui.components.Label
import com.minimalist.launcher.ui.components.Screen
import com.minimalist.launcher.ui.components.SectionHeader
import com.minimalist.launcher.ui.components.TextAction
import com.minimalist.launcher.ui.theme.Palette
import com.minimalist.launcher.ui.theme.Type
import kotlinx.coroutines.delay

private const val SCREEN_OFF_GRACE_MILLIS = 30_000L
private const val MAX_TYPED_AT_ONCE = 3

/** Solve problems to get past a block early. There is no way to skip them. */
@Composable
fun ChallengeScreen(request: UnlockRequest) {
    val container = appContainer()
    val context = LocalContext.current
    var plan by remember(request) { mutableStateOf<ChallengePlan?>(null) }
    var recent by remember(request) { mutableIntStateOf(0) }
    var minutes by remember(request) { mutableIntStateOf(ChallengeRules.PASS_MINUTES[1]) }
    var reason by remember(request) { mutableStateOf("") }
    var session by remember(request) { mutableStateOf<ChallengeSession?>(null) }
    var note by remember(request) { mutableStateOf<String?>(null) }

    LaunchedEffect(request) {
        recent = container.unlocks.earlyUnlocksPastWeek()
        plan = request.night?.plan ?: ChallengeRules.forBlock(request.timeLeftMillis, recent, container.timeScale.value)
    }

    fun restart(message: String) {
        session = null
        container.challengeRunning.value = false
        note = message
    }

    // Leaving the screen (another app, home, or the screen off for over 30 s) starts it over.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stoppedAt = 0L
        var leftForAnotherApp = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stoppedAt = System.currentTimeMillis()
                    leftForAnotherApp = context.getSystemService(PowerManager::class.java).isInteractive
                }
                Lifecycle.Event.ON_START -> {
                    val away = System.currentTimeMillis() - stoppedAt
                    if (stoppedAt > 0 && session != null && (leftForAnotherApp || away > SCREEN_OFF_GRACE_MILLIS)) {
                        restart("You left the challenge, so it starts over with new problems.")
                    }
                    stoppedAt = 0
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler { container.abandonUnlock() }

    Screen(
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Spacer(Modifier.height(48.dp))
        val running = session
        if (running == null) {
            Intro(
                request = request,
                plan = plan,
                recent = recent,
                minutes = minutes,
                onMinutes = { minutes = it },
                reason = reason,
                onReason = { reason = it },
                note = note,
                onStart = {
                    val chosen = plan ?: return@Intro
                    note = null
                    session = ChallengeSession(chosen)
                    container.challengeRunning.value = true
                },
                onCancel = container::abandonUnlock,
            )
        } else {
            Running(
                session = running,
                onSolved = { container.finishUnlock(request, reason, running.total, minutes) },
                onGiveUp = container::abandonUnlock,
            )
        }
    }
}

@Composable
private fun Intro(
    request: UnlockRequest,
    plan: ChallengePlan?,
    recent: Int,
    minutes: Int,
    onMinutes: (Int) -> Unit,
    reason: String,
    onReason: (String) -> Unit,
    note: String?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val night = request.night
    val title = when {
        night?.opensMorning == true -> "Open your phone early"
        night != null -> "A pass through the night"
        else -> when (request.target) {
            is UnlockTarget.Lock -> "Get past ${request.label}"
            is UnlockTarget.Domain -> "Open ${request.label} early"
            else -> "Get into ${request.label} early"
        }
    }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Label(title, style = Type.title)
        if (request.timeLeftMillis in 1 until Long.MAX_VALUE / 2) {
            Label("${DurationFormat.remaining(request.timeLeftMillis)} left on it.", style = Type.small)
        }
        note?.let { Label(it, style = Type.small.copy(color = Palette.Accent)) }
        Spacer(Modifier.height(16.dp))
        if (plan != null) {
            Label("${plan.count} ${if (plan.count == 1) "problem" else "problems"}" + describe(plan), style = Type.body)
            if (night != null && !night.opensMorning) {
                Label("Harder the later it gets, and 2 more for each pass already used tonight.", style = Type.small)
            } else if (recent > 0) {
                Label("Includes ${recent * ChallengeRules.EXTRA_PER_RECENT_UNLOCK} extra for $recent early ${if (recent == 1) "unlock" else "unlocks"} this week.", style = Type.small)
            }
        }
        Label(
            "A wrong answer or running out of time adds one more. Leaving this screen starts it over. " +
                "Only calls, messages and the clock work while it runs.",
            style = Type.small,
        )
        SectionHeader("Pass")
        when {
            night?.opensMorning == true -> Label("Solving it opens your phone for the rest of the morning.", style = Type.body)
            night != null -> Label("${night.passMinutes} minutes, then night mode carries on.", style = Type.body)
            else -> {
                Row {
                    ChallengeRules.PASS_MINUTES.forEach { option ->
                        Choice("$option min", selected = option == minutes, onClick = { onMinutes(option) })
                    }
                }
                Label("When it ends, the block carries on as before.", style = Type.small)
            }
        }
        SectionHeader("Why")
        InputField(
            value = reason,
            onValueChange = { onReason(it.take(120)) },
            placeholder = "One line, kept in your log",
            style = Type.body,
            capitalization = KeyboardCapitalization.Sentences,
        )
        Spacer(Modifier.height(16.dp))
        TextAction(text = "Start", onClick = onStart, enabled = plan != null && FrictionRules.isValidReason(reason))
        TextAction(text = "Never mind", onClick = onCancel, style = Type.small)
    }
}

private fun describe(plan: ChallengePlan): String {
    val parts = listOfNotNull(
        plan.easy.takeIf { it > 0 }?.let { "$it easy" },
        plan.medium.takeIf { it > 0 }?.let { "$it medium" },
        plan.hard.takeIf { it > 0 }?.let { "$it hard" },
        if (plan.passage) "a long typing passage" else null,
    )
    return if (parts.isEmpty()) "" else ": " + parts.joinToString(", ")
}

@Composable
private fun Running(session: ChallengeSession, onSolved: () -> Unit, onGiveUp: () -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val problem = session.current

    LaunchedEffect(version) { if (session.isDone) onSolved() }
    if (problem == null) return

    var answer by remember(version) { mutableStateOf("") }
    var secondsLeft by remember(version) { mutableIntStateOf(problem.timeLimitSeconds) }
    LaunchedEffect(version) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        session.timeOut()
        feedback = "Time's up. One more problem added."
        version++
    }
    fun submit() {
        val correct = session.answer(answer)
        feedback = if (correct) null else "Not quite. One more problem added."
        version++
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(version) { runCatching { focus.requestFocus() } }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row {
            Label("Problem ${session.index + 1} of ${session.total}", style = Type.small, modifier = Modifier.weight(1f))
            Label("$secondsLeft s", style = Type.small.copy(color = if (secondsLeft <= 10) Palette.Accent else Palette.Dim))
        }
        feedback?.let { Label(it, style = Type.small.copy(color = Palette.Accent)) }
        Spacer(Modifier.height(24.dp))
        Label(problem.prompt, style = Type.title)
        if (problem is Problem.Typing) {
            Spacer(Modifier.height(8.dp))
            Label(problem.text, style = Type.body.copy(letterSpacing = 1.sp))
        }
        Spacer(Modifier.height(16.dp))
        CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar) {
            InputField(
                value = answer,
                onValueChange = { typed ->
                    // Pasting (or a keyboard's clipboard) arrives all at once; only typing counts.
                    if (problem is Problem.Typing && typed.length - answer.length > MAX_TYPED_AT_ONCE) {
                        feedback = "Only typed letters count, not pasted ones."
                    } else {
                        answer = typed
                    }
                },
                placeholder = if (problem is Problem.Typing) "Type it here" else "Answer",
                modifier = Modifier.focusRequester(focus),
                style = Type.item,
                imeAction = ImeAction.Done,
                onImeAction = { if (answer.isNotBlank()) submit() },
                keyboardType = if (problem is Problem.Numeric) KeyboardType.Number else KeyboardType.Text,
                singleLine = problem !is Problem.Typing || !problem.passage,
            )
        }
        TextAction(text = "Check", onClick = ::submit, enabled = answer.isNotBlank())
        Spacer(Modifier.height(24.dp))
        TextAction(text = "Give up", onClick = onGiveUp, style = Type.small)
    }
}

/** No copy and paste menu on challenge answers. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}
