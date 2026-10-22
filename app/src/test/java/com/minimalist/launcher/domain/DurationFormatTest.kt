package com.minimalist.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    private val second = 1_000L
    private val minute = 60 * second
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun remaining() {
        assertEquals("40 s", DurationFormat.remaining(40 * second))
        assertEquals("1 s", DurationFormat.remaining(10))
        assertEquals("12 min", DurationFormat.remaining(12 * minute))
        assertEquals("2 h 15 min", DurationFormat.remaining(2 * hour + 15 * minute))
        assertEquals("3 h", DurationFormat.remaining(3 * hour))
        assertEquals("1 day", DurationFormat.remaining(day))
        assertEquals("3 days 4 h", DurationFormat.remaining(3 * day + 4 * hour + 5 * minute))
    }

    @Test
    fun short() {
        assertEquals("0m", DurationFormat.short(30 * second))
        assertEquals("45m", DurationFormat.short(45 * minute))
        assertEquals("1h 12m", DurationFormat.short(hour + 12 * minute))
    }
}
