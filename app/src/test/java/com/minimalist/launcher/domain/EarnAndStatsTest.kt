package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.earn.EarnRules
import com.minimalist.launcher.domain.earn.Earning
import com.minimalist.launcher.domain.earn.StepState
import com.minimalist.launcher.domain.stats.Csv
import com.minimalist.launcher.domain.stats.CsvTable
import com.minimalist.launcher.domain.stats.StatsMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class EarnAndStatsTest {

    private val rules = EarnRules(stepsPerUnit = 1_000, minutesPerSteps = 10, focusMinutes = 25, minutesPerFocus = 5)

    @Test
    fun stepsAndFocusEarnMinutes() {
        assertEquals(0, Earning.earnedMinutes(999, 0, rules))
        assertEquals(10, Earning.earnedMinutes(1_999, 0, rules))
        assertEquals(45, Earning.earnedMinutes(3_500, 3, rules))
        assertEquals(100, Earning.earnedMinutes(100, 0, rules, TimeScale(testMode = true)))
    }

    @Test
    fun onlyLongEnoughPutAwaysCountAsFocus() {
        val minute = 60_000L
        val sessions = listOf(0L to 25 * minute, 0L to 24 * minute, 100L to 100L + 60 * minute)
        assertEquals(2, Earning.focusSessions(sessions, rules))
        assertEquals(3, Earning.focusSessions(listOf(0L to 30_000L, 0L to 25_000L, 0L to 26_000L), rules, TimeScale(testMode = true)))
    }

    @Test
    fun timeLeftToSpend() {
        assertEquals(10 * 60_000L - 90_000L, Earning.leftMillis(10, 90_000L))
        assertTrue(Earning.leftMillis(0, 1) < 0)
        assertEquals(10_000L - 3_000L, Earning.leftMillis(10, 3_000L, TimeScale(testMode = true)))
    }

    @Test
    fun stepCounterReadingsSurviveDaysAndReboots() {
        var state = StepState(day = 1, stepsToday = 0, lastReading = null)
        state = Earning.afterReading(state, 5_000, 1)
        assertEquals(0, state.stepsToday) // First reading only sets the baseline.
        state = Earning.afterReading(state, 5_800, 1)
        assertEquals(800, state.stepsToday)
        state = Earning.afterReading(state, 120, 1) // Rebooted: the counter started again.
        assertEquals(920, state.stepsToday)
        state = Earning.afterReading(state, 620, 2) // A new day.
        assertEquals(500, state.stepsToday)
        assertEquals(2L, state.day)
    }

    @Test
    fun generousRulesWeaken() {
        assertTrue(Earning.rulesWeaken(rules, rules.copy(stepsPerUnit = 500)))
        assertTrue(Earning.rulesWeaken(rules, rules.copy(minutesPerSteps = 15)))
        assertTrue(Earning.rulesWeaken(rules, rules.copy(focusMinutes = 15)))
        assertTrue(Earning.rulesWeaken(rules, rules.copy(minutesPerFocus = 10)))
        assertFalse(Earning.rulesWeaken(rules, rules.copy(stepsPerUnit = 2_000, minutesPerFocus = 5)))
    }

    @Test
    fun csvQuotesOnlyWhenNeeded() {
        assertEquals("plain", Csv.field("plain"))
        assertEquals("\"a,b\"", Csv.field("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.field("say \"hi\""))
        assertEquals("\"two\nlines\"", Csv.field("two\nlines"))
        assertEquals("", Csv.field(null))
        assertEquals("42", Csv.field(42))
        val table = CsvTable("t", listOf("a", "b"), listOf(listOf(1, "x,y"), listOf(null, true)))
        assertEquals("a,b\r\n1,\"x,y\"\r\n,true\r\n", Csv.render(table))
    }

    @Test
    fun zipHoldsOneCsvPerTable() {
        val out = ByteArrayOutputStream()
        Csv.writeZip(listOf(CsvTable("one", listOf("a"), emptyList()), CsvTable("two", listOf("b"), listOf(listOf(1)))), out)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) names += (zip.nextEntry ?: break).name
        }
        assertEquals(listOf("one.csv", "two.csv"), names)
    }

    @Test
    fun putAwayTimeIsClippedToTheRange() {
        val sessions = listOf(0L to 100L, 150L to null, 50L to 300L)
        // 50 from the first, none from the open one (it starts at the range end), 100 from the third.
        assertEquals(150L, StatsMath.overlapMillis(sessions, 50, 150, now = 200))
        assertEquals(100L, StatsMath.overlapMillis(listOf(150L to null), 0, 1_000, now = 250))
    }
}
