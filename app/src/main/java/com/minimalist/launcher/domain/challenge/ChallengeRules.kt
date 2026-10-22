package com.minimalist.launcher.domain.challenge

import com.minimalist.launcher.domain.blocking.TimeScale
import kotlin.random.Random

/** How many problems, of which difficulty, a challenge asks for. */
data class ChallengePlan(
    val easy: Int = 0,
    val medium: Int = 0,
    val hard: Int = 0,
    val passage: Boolean = false,
) {
    val count: Int get() = easy + medium + hard + if (passage) 1 else 0

    fun generate(random: Random): List<Problem> = buildList {
        repeat(easy) { add(ProblemGenerator.generate(Difficulty.Easy, random)) }
        repeat(medium) { add(ProblemGenerator.generate(Difficulty.Medium, random)) }
        repeat(hard) { add(ProblemGenerator.generate(Difficulty.Hard, random)) }
        shuffle(random)
        if (passage) add(ProblemGenerator.passage(random))
    }

    /** Adds [extra] problems at the hardest difficulty this plan already uses. */
    fun plus(extra: Int): ChallengePlan = when {
        extra <= 0 -> this
        hard > 0 -> copy(hard = hard + extra)
        medium > 0 -> copy(medium = medium + extra)
        else -> copy(easy = easy + extra)
    }
}

object ChallengeRules {

    const val EXTRA_PER_RECENT_UNLOCK = 2
    const val MAX_EXTRA_FOR_WRONG = 10
    val PASS_MINUTES = listOf(5, 15, 30)

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /**
     * Getting into a blocked app, site or Put away early. Harder the more time is left on the
     * block, plus two problems for each early unlock in the past seven days.
     */
    fun forBlock(timeLeftMillis: Long, earlyUnlocksPastWeek: Int, scale: TimeScale = TimeScale.REAL): ChallengePlan {
        if (scale.testMode) return ChallengePlan(easy = 1)
        val left = timeLeftMillis.coerceAtLeast(0)
        val base = when {
            left < 30 * MINUTE -> ChallengePlan(easy = 3)
            left < 2 * HOUR -> ChallengePlan(medium = 5)
            left < 12 * HOUR -> ChallengePlan(medium = 5, hard = 3)
            left < 3 * DAY -> ChallengePlan(hard = 12)
            else -> ChallengePlan(hard = 15, passage = true)
        }
        return base.plus(EXTRA_PER_RECENT_UNLOCK * earlyUnlocksPastWeek.coerceAtLeast(0))
    }
}

/**
 * A challenge in progress. A wrong answer or a timeout moves on and adds one more problem
 * (at most ten extra). Leaving the screen starts a new session.
 */
class ChallengeSession(plan: ChallengePlan, private val random: Random = Random.Default) {

    private val problems = plan.generate(random).toMutableList()
    var index = 0
        private set
    var wrong = 0
        private set
    private var extraAdded = 0

    val total: Int get() = problems.size
    val current: Problem? get() = problems.getOrNull(index)
    val isDone: Boolean get() = index >= problems.size

    /** True if correct. Either way the session moves to the next problem. */
    fun answer(text: String): Boolean {
        val problem = current ?: return false
        val correct = problem.isCorrect(text)
        if (!correct) miss(problem)
        index++
        return correct
    }

    fun timeOut() {
        current?.let(::miss)
        index++
    }

    private fun miss(problem: Problem) {
        wrong++
        if (extraAdded < ChallengeRules.MAX_EXTRA_FOR_WRONG) {
            extraAdded++
            val replacement = if (problem is Problem.Typing && problem.passage) {
                ProblemGenerator.generate(Difficulty.Hard, random)
            } else {
                ProblemGenerator.generate(problem.difficulty, random)
            }
            problems.add(replacement)
        }
    }
}

/** What getting past night mode costs right now. */
data class NightChallenge(
    val plan: ChallengePlan,
    /** Pass length in minutes; ignored when [opensMorning]. */
    val passMinutes: Int,
    /** In the last two hours before opening, solving it opens the phone for the morning. */
    val opensMorning: Boolean,
)

object NightRules {

    const val MORNING_HANDOVER_MINUTES = 120L

    /**
     * Harder the later it is: 3 easy right after bedtime, up to 15 hard plus a passage after
     * three hours. Each pass already used tonight adds two. In the last two hours before
     * opening the unlock table takes over, by time left until opening, plus two for each
     * early unlock or opening this week. In test mode minutes count as seconds.
     */
    fun challenge(
        sinceBedtimeMillis: Long,
        untilOpeningMillis: Long,
        passesTonight: Int,
        earlyUnlocksPastWeek: Int,
        scale: TimeScale = TimeScale.REAL,
    ): NightChallenge {
        val unit = if (scale.testMode) 1_000L else 60_000L
        val sinceBedtime = sinceBedtimeMillis.coerceAtLeast(0) / unit
        val untilOpening = untilOpeningMillis.coerceAtLeast(0) / unit
        if (untilOpening < MORNING_HANDOVER_MINUTES) {
            // The unlock table thinks in real time left; in test mode, scale it back up.
            val left = if (scale.testMode) untilOpening * 60_000L else untilOpeningMillis
            return NightChallenge(ChallengeRules.forBlock(left, earlyUnlocksPastWeek, scale), 0, opensMorning = true)
        }
        if (scale.testMode) return NightChallenge(ChallengePlan(easy = 1), passFor(sinceBedtime), opensMorning = false)
        val base = when {
            sinceBedtime < 30 -> ChallengePlan(easy = 3)
            sinceBedtime < 60 -> ChallengePlan(medium = 5)
            sinceBedtime < 120 -> ChallengePlan(medium = 5, hard = 3)
            sinceBedtime < 180 -> ChallengePlan(hard = 12)
            else -> ChallengePlan(hard = 15, passage = true)
        }
        return NightChallenge(
            plan = base.plus(ChallengeRules.EXTRA_PER_RECENT_UNLOCK * passesTonight.coerceAtLeast(0)),
            passMinutes = passFor(sinceBedtime),
            opensMorning = false,
        )
    }

    private fun passFor(minutesSinceBedtime: Long): Int = when {
        minutesSinceBedtime < 30 -> 15
        minutesSinceBedtime < 120 -> 10
        else -> 5
    }
}
