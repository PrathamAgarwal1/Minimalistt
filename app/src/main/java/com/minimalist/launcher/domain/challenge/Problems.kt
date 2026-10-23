package com.minimalist.launcher.domain.challenge

import kotlin.random.Random

enum class Difficulty(val timeLimitSeconds: Int) {
    Easy(60),
    Medium(90),
    Hard(120),
}

/** One question in an unlock challenge. Everything is generated on the phone, never fetched. */
sealed interface Problem {
    val prompt: String
    val difficulty: Difficulty

    /** Long passages get more time than the usual limit. */
    val timeLimitSeconds: Int get() = difficulty.timeLimitSeconds

    fun isCorrect(answer: String): Boolean

    /** A number to work out: arithmetic, the next term of a sequence, or x in an equation. */
    data class Numeric(override val prompt: String, val answer: Long, override val difficulty: Difficulty) : Problem {
        override fun isCorrect(answer: String): Boolean =
            answer.replace(" ", "").replace(",", "").replace('−', '-').toLongOrNull() == this.answer
    }

    /** Type the text exactly, letter for letter. */
    data class Typing(val text: String, override val difficulty: Difficulty, val passage: Boolean = false) : Problem {
        override val prompt: String get() = "Type this exactly:"
        override val timeLimitSeconds: Int get() = if (passage) 300 else difficulty.timeLimitSeconds
        override fun isCorrect(answer: String): Boolean = answer == text
    }
}

object ProblemGenerator {

    fun generate(difficulty: Difficulty, random: Random): Problem = when (random.nextInt(5)) {
        0 -> multiplication(difficulty, random)
        1 -> additionChain(difficulty, random)
        2 -> sequence(difficulty, random)
        3 -> equation(difficulty, random)
        else -> typing(difficulty, random)
    }

    fun multiplication(difficulty: Difficulty, random: Random): Problem {
        val (a, b) = when (difficulty) {
            Difficulty.Easy -> random.nextInt(2, 12) to random.nextInt(2, 10)
            Difficulty.Medium -> random.nextInt(12, 50) to random.nextInt(3, 12)
            Difficulty.Hard -> random.nextInt(15, 99) to random.nextInt(11, 20)
        }
        return Problem.Numeric("$a × $b", a.toLong() * b, difficulty)
    }

    fun additionChain(difficulty: Difficulty, random: Random): Problem {
        val (count, range, subtract) = when (difficulty) {
            Difficulty.Easy -> Triple(3, 10..50, false)
            Difficulty.Medium -> Triple(4, 10..99, true)
            Difficulty.Hard -> Triple(5, 50..199, true)
        }
        var total = 0L
        val text = StringBuilder()
        repeat(count) { i ->
            val n = random.nextInt(range.first, range.last + 1)
            val minus = subtract && i > 0 && random.nextInt(3) == 0
            total += if (minus) -n else n
            text.append(if (i == 0) "$n" else if (minus) " − $n" else " + $n")
        }
        return Problem.Numeric(text.toString(), total, difficulty)
    }

    fun sequence(difficulty: Difficulty, random: Random): Problem {
        val terms: List<Long> = when (difficulty) {
            Difficulty.Easy -> {
                val start = random.nextLong(1, 30)
                val step = random.nextLong(2, 12)
                (0 until 6).map { start + it * step }
            }
            Difficulty.Medium -> when (random.nextInt(2)) {
                0 -> {
                    val start = random.nextLong(1, 6)
                    val ratio = random.nextLong(2, 4)
                    (0 until 6).runningFold(start) { acc, _ -> acc * ratio }.take(6)
                }
                else -> {
                    // Steps that alternate between two numbers.
                    val start = random.nextLong(1, 30)
                    val a = random.nextLong(2, 10)
                    val b = random.nextLong(11, 20)
                    (1 until 6).runningFold(start) { acc, i -> acc + if (i % 2 == 1) a else b }
                }
            }
            Difficulty.Hard -> when (random.nextInt(3)) {
                0 -> {
                    // Steps that grow: 3, 5, 9, 15, 23 (second differences constant).
                    val start = random.nextLong(1, 20)
                    val step = random.nextLong(1, 6)
                    val grow = random.nextLong(2, 6)
                    (0 until 6).runningFold(start to step) { (value, d), _ -> (value + d) to (d + grow) }.map { it.first }.take(6)
                }
                1 -> {
                    // Each term is k × previous + c.
                    val start = random.nextLong(1, 6)
                    val k = random.nextLong(2, 4)
                    val c = random.nextLong(1, 6)
                    (0 until 6).runningFold(start) { acc, _ -> acc * k + c }.take(6)
                }
                else -> {
                    // Each term is the sum of the two before.
                    val first = random.nextLong(1, 10)
                    val second = random.nextLong(first, first + 10)
                    val list = mutableListOf(first, second)
                    while (list.size < 6) list += list[list.size - 1] + list[list.size - 2]
                    list
                }
            }
        }
        val shown = terms.dropLast(1).joinToString(", ")
        return Problem.Numeric("What comes next?\n$shown, …", terms.last(), difficulty)
    }

    fun equation(difficulty: Difficulty, random: Random): Problem {
        val x = random.nextLong(-12, 25).let { if (difficulty == Difficulty.Easy) kotlin.math.abs(it) + 1 else it }
        return when (difficulty) {
            Difficulty.Easy -> {
                val a = random.nextLong(3, 50)
                Problem.Numeric("x + $a = ${x + a}\nx = ?", x, difficulty)
            }
            Difficulty.Medium -> {
                val a = random.nextLong(2, 10)
                val b = random.nextLong(-30, 31)
                Problem.Numeric("${a}x ${signed(b)} = ${a * x + b}\nx = ?", x, difficulty)
            }
            Difficulty.Hard -> {
                val a = random.nextLong(4, 13)
                val c = random.nextLong(1, a - 1)
                val b = random.nextLong(-40, 41)
                val d = (a - c) * x + b
                Problem.Numeric("${a}x ${signed(b)} = ${c}x ${signed(d)}\nx = ?", x, difficulty)
            }
        }
    }

    fun typing(difficulty: Difficulty, random: Random): Problem {
        val (length, alphabet) = when (difficulty) {
            Difficulty.Easy -> 6 to LOWER
            Difficulty.Medium -> 10 to LOWER + DIGITS
            Difficulty.Hard -> 12 to LOWER + UPPER + DIGITS
        }
        return Problem.Typing((1..length).map { alphabet[random.nextInt(alphabet.length)] }.joinToString(""), difficulty)
    }

    /** About 50 characters of random everyday words. */
    fun passage(random: Random): Problem {
        val text = StringBuilder()
        while (text.length < 50) {
            if (text.isNotEmpty()) text.append(' ')
            text.append(WORDS[random.nextInt(WORDS.size)])
        }
        return Problem.Typing(text.toString(), Difficulty.Hard, passage = true)
    }

    private fun signed(n: Long) = if (n < 0) "− ${-n}" else "+ $n"

    private const val LOWER = "abcdefghijkmnpqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "#%&*+=?@"

    private val WORDS = (
        "river stone window quiet garden yellow morning simple careful paper honest letter bridge " +
            "silver patient orange evening market travel winter little moment pencil gentle cloud " +
            "harbour candle wooden basket summer forest planet thunder kitchen ladder mirror pocket " +
            "steady breeze meadow lantern copper velvet island marble tunnel feather rocket saddle"
        ).split(' ')
}
