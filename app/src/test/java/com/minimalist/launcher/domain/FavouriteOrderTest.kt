package com.minimalist.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FavouriteOrderTest {

    private val six = setOf("a", "b", "c", "d", "e", "f")

    @Test
    fun emptyFavouritesGiveEmptyOrder() {
        assertTrue(FavouriteOrder.arrange(emptySet(), listOf("a"), 1, 2).isEmpty())
    }

    @Test
    fun sameDayKeepsOrder() {
        val order = listOf("c", "a", "f", "b", "e", "d")
        assertEquals(order, FavouriteOrder.arrange(six, order, previousDay = 10, today = 10, random = Random(1)))
    }

    @Test
    fun sameDayRemovalKeepsRemainingPositions() {
        val order = listOf("c", "a", "f", "b", "e", "d")
        val result = FavouriteOrder.arrange(six - "f", order, previousDay = 10, today = 10)
        assertEquals(listOf("c", "a", "b", "e", "d"), result)
    }

    @Test
    fun sameDayAdditionKeepsExistingRelativeOrder() {
        val order = listOf("c", "a", "b")
        repeat(20) { seed ->
            val result = FavouriteOrder.arrange(setOf("a", "b", "c", "z"), order, 10, 10, Random(seed))
            assertEquals(4, result.size)
            assertEquals(order, result - "z")
        }
    }

    @Test
    fun newDayMovesEveryFavourite() {
        var previous = listOf("a", "b", "c", "d", "e", "f")
        for (day in 1L..200L) {
            val next = FavouriteOrder.arrange(six, previous, day - 1, day, Random(day))
            assertEquals(six, next.toSet())
            next.indices.forEach { i -> assertNotEquals("day $day slot $i", previous[i], next[i]) }
            previous = next
        }
    }

    @Test
    fun twoFavouritesSwapEachDay() {
        val result = FavouriteOrder.arrange(setOf("a", "b"), listOf("a", "b"), 1, 2, Random(3))
        assertEquals(listOf("b", "a"), result)
    }

    @Test
    fun singleFavouriteStaysPut() {
        assertEquals(listOf("a"), FavouriteOrder.arrange(setOf("a"), listOf("a"), 1, 2))
    }

    @Test
    fun firstEverArrangementContainsAllFavourites() {
        val result = FavouriteOrder.arrange(six, emptyList(), Long.MIN_VALUE, 100, Random(7))
        assertEquals(six, result.toSet())
    }
}
