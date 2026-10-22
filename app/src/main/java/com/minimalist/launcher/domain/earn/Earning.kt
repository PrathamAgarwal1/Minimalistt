package com.minimalist.launcher.domain.earn

import com.minimalist.launcher.domain.blocking.TimeScale

/** How steps and focus sessions turn into minutes for the earned-time apps. */
data class EarnRules(
    val stepsPerUnit: Int = 1_000,
    val minutesPerSteps: Int = 10,
    val focusMinutes: Int = 25,
    val minutesPerFocus: Int = 5,
    /** Earned time every day starts with, before any walking or focus. */
    val freeMinutes: Int = 0,
    /** The most that can be earned in a day, free minutes included; 0 means no limit. */
    val maxPerDay: Int = 0,
)

/** The step counter counts from boot; this keeps today's total across readings and reboots. */
data class StepState(val day: Long, val stepsToday: Int, val lastReading: Long?)

object Earning {

    val STEP_OPTIONS = listOf(500, 1_000, 2_000)
    val STEP_MINUTE_OPTIONS = listOf(5, 10, 15)
    val FOCUS_OPTIONS = listOf(25, 45, 60)
    val FOCUS_MINUTE_OPTIONS = listOf(5, 10, 15)
    val FREE_OPTIONS = listOf(0, 10, 20, 30)
    val MAX_OPTIONS = listOf(0, 30, 60, 90)

    // What a custom value may be.
    val STEPS_RANGE = 100..20_000
    val MINUTES_RANGE = 1..120
    val FOCUS_RANGE = 5..240
    val FREE_RANGE = 0..120
    val MAX_RANGE = 0..600

    /** Keeps typed-in values sensible. */
    fun sanitized(rules: EarnRules) = rules.copy(
        stepsPerUnit = rules.stepsPerUnit.coerceIn(STEPS_RANGE),
        minutesPerSteps = rules.minutesPerSteps.coerceIn(MINUTES_RANGE),
        focusMinutes = rules.focusMinutes.coerceIn(FOCUS_RANGE),
        minutesPerFocus = rules.minutesPerFocus.coerceIn(MINUTES_RANGE),
        freeMinutes = rules.freeMinutes.coerceIn(FREE_RANGE),
        maxPerDay = rules.maxPerDay.coerceIn(MAX_RANGE),
    )

    /** Minutes earned today. In test mode steps are worth a hundred times more. */
    fun earnedMinutes(steps: Int, focusSessions: Int, rules: EarnRules, scale: TimeScale = TimeScale.REAL): Int {
        val perUnit = if (scale.testMode) (rules.stepsPerUnit / 100).coerceAtLeast(1) else rules.stepsPerUnit.coerceAtLeast(1)
        val earned = rules.freeMinutes.coerceAtLeast(0) +
            (steps.coerceAtLeast(0) / perUnit) * rules.minutesPerSteps +
            focusSessions.coerceAtLeast(0) * rules.minutesPerFocus
        return if (rules.maxPerDay > 0) earned.coerceAtMost(rules.maxPerDay) else earned
    }

    /** Put away sessions that lasted the focus length count as focus sessions (seconds in test mode). */
    fun focusSessions(sessions: List<Pair<Long, Long>>, rules: EarnRules, scale: TimeScale = TimeScale.REAL): Int {
        val needed = rules.focusMinutes * if (scale.testMode) 1_000L else 60_000L
        return sessions.count { (start, end) -> end - start >= needed }
    }

    /** What is left to spend, in milliseconds; earned minutes are scaled like other minutes in test mode. */
    fun leftMillis(earnedMinutes: Int, usedMillis: Long, scale: TimeScale = TimeScale.REAL): Long {
        val minute = if (scale.testMode) 1_000L else 60_000L
        return earnedMinutes * minute - usedMillis
    }

    /** Folds one step-counter reading into today's total. */
    fun afterReading(state: StepState, reading: Long, today: Long): StepState {
        val base = if (state.day == today) state else StepState(today, 0, state.lastReading)
        val last = base.lastReading
        val added = when {
            last == null -> 0L
            reading >= last -> reading - last
            else -> reading // The counter restarted after a reboot.
        }
        return base.copy(stepsToday = (base.stepsToday + added).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), lastReading = reading)
    }

    /** More generous rules weaken protection. */
    fun rulesWeaken(old: EarnRules, new: EarnRules): Boolean =
        new.stepsPerUnit < old.stepsPerUnit ||
            new.minutesPerSteps > old.minutesPerSteps ||
            new.focusMinutes < old.focusMinutes ||
            new.minutesPerFocus > old.minutesPerFocus ||
            new.freeMinutes > old.freeMinutes ||
            (old.maxPerDay > 0 && (new.maxPerDay == 0 || new.maxPerDay > old.maxPerDay))
}
