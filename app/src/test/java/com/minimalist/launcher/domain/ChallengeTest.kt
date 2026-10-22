package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.challenge.ChallengePlan
import com.minimalist.launcher.domain.challenge.ChallengeRules
import com.minimalist.launcher.domain.challenge.ChallengeSession
import com.minimalist.launcher.domain.challenge.Difficulty
import com.minimalist.launcher.domain.challenge.Problem
import com.minimalist.launcher.domain.challenge.ProblemGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChallengeTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun blockTableScalesWithTimeLeft() {
        assertEquals(ChallengePlan(easy = 3), ChallengeRules.forBlock(29 * minute, 0))
        assertEquals(ChallengePlan(medium = 5), ChallengeRules.forBlock(30 * minute, 0))
        assertEquals(ChallengePlan(medium = 5), ChallengeRules.forBlock(2 * hour - 1, 0))
        assertEquals(ChallengePlan(medium = 5, hard = 3), ChallengeRules.forBlock(2 * hour, 0))
        assertEquals(8, ChallengeRules.forBlock(11 * hour, 0).count)
        assertEquals(ChallengePlan(hard = 12), ChallengeRules.forBlock(12 * hour, 0))
        assertEquals(ChallengePlan(hard = 15, passage = true), ChallengeRules.forBlock(3 * day, 0))
        assertEquals(ChallengePlan(hard = 15, passage = true), ChallengeRules.forBlock(Long.MAX_VALUE, 0))
    }

    @Test
    fun recentUnlocksAddTwoEach() {
        assertEquals(ChallengePlan(easy = 7), ChallengeRules.forBlock(10 * minute, 2))
        assertEquals(ChallengePlan(medium = 5, hard = 5), ChallengeRules.forBlock(3 * hour, 1))
        assertEquals(16, ChallengeRules.forBlock(4 * day, 0).count)
    }

    @Test
    fun testModeIsOneEasyProblem() {
        assertEquals(ChallengePlan(easy = 1), ChallengeRules.forBlock(10 * day, 5, TimeScale(testMode = true)))
    }

    @Test
    fun passageComesLast() {
        val problems = ChallengePlan(hard = 3, passage = true).generate(Random(1))
        assertEquals(4, problems.size)
        val last = problems.last() as Problem.Typing
        assertTrue(last.passage)
        assertTrue(last.text.length >= 200)
        assertEquals(300, last.timeLimitSeconds)
    }

    @Test
    fun numericAnswersAreChecked() {
        val p = Problem.Numeric("47 × 6", 282, Difficulty.Easy)
        assertTrue(p.isCorrect("282"))
        assertTrue(p.isCorrect(" 2 82 "))
        assertFalse(p.isCorrect("281"))
        assertFalse(p.isCorrect("abc"))
        assertTrue(Problem.Numeric("x", -7, Difficulty.Medium).isCorrect("−7"))
    }

    @Test
    fun typingMustMatchExactly() {
        val p = Problem.Typing("aB3#x", Difficulty.Hard)
        assertTrue(p.isCorrect("aB3#x"))
        assertFalse(p.isCorrect("ab3#x"))
        assertFalse(p.isCorrect("aB3#x "))
    }

    @Test
    fun generatedProblemsAreSolvable() {
        val random = Random(42)
        repeat(300) {
            for (difficulty in Difficulty.entries) {
                listOf(
                    ProblemGenerator.multiplication(difficulty, random),
                    ProblemGenerator.additionChain(difficulty, random),
                    ProblemGenerator.sequence(difficulty, random),
                    ProblemGenerator.equation(difficulty, random),
                ).forEach { problem ->
                    problem as Problem.Numeric
                    assertTrue(problem.prompt, problem.isCorrect(problem.answer.toString()))
                    assertEquals(difficulty.timeLimitSeconds, problem.timeLimitSeconds)
                }
            }
        }
    }

    @Test
    fun equationsHaveTheStatedSolution() {
        val random = Random(7)
        repeat(200) {
            val p = ProblemGenerator.equation(Difficulty.Hard, random) as Problem.Numeric
            // "ax + b = cx + d": check by substituting the answer.
            val numbers = Regex("−? ?\\d+").findAll(p.prompt.substringBefore('\n')).map { it.value.replace("− ", "-").replace(" ", "").toLong() }.toList()
            val (a, b, c, d) = numbers
            assertEquals(a * p.answer + b, c * p.answer + d)
        }
    }

    @Test
    fun timeLimitsByDifficulty() {
        assertEquals(60, Difficulty.Easy.timeLimitSeconds)
        assertEquals(90, Difficulty.Medium.timeLimitSeconds)
        assertEquals(120, Difficulty.Hard.timeLimitSeconds)
    }

    private fun answerFor(problem: Problem): String = when (problem) {
        is Problem.Numeric -> problem.answer.toString()
        is Problem.Typing -> problem.text
    }

    @Test
    fun wrongAnswersAddOneProblemCappedAtTen() {
        val session = ChallengeSession(ChallengePlan(easy = 3), Random(3))
        assertEquals(3, session.total)
        assertFalse(session.answer("definitely wrong"))
        assertEquals(4, session.total)
        session.timeOut()
        assertEquals(5, session.total)
        repeat(30) { if (!session.isDone) session.answer("nope") }
        assertEquals(13, session.total)
        assertTrue(session.isDone)
    }

    @Test
    fun rightAnswersFinish() {
        val session = ChallengeSession(ChallengePlan(medium = 2), Random(9))
        while (!session.isDone) assertTrue(session.answer(answerFor(session.current!!)))
        assertEquals(2, session.total)
        assertEquals(0, session.wrong)
    }
}
