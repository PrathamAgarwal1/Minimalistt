package com.minimalist.launcher.domain

import kotlin.random.Random

/**
 * Favourites change position once a day to break muscle memory. Within a day the order is stable.
 */
object FavouriteOrder {

    const val MAX_FAVOURITES = 6
    private const val SHUFFLE_ATTEMPTS = 64

    /**
     * Returns the order to show on [today].
     *
     * - Same day as the stored order: keep every existing position; newly added favourites take
     *   random slots, removed ones drop out.
     * - New day: shuffle so that no favourite stays in the slot it had yesterday, when possible.
     */
    fun arrange(
        favourites: Set<String>,
        previousOrder: List<String>,
        previousDay: Long,
        today: Long,
        random: Random = Random.Default,
    ): List<String> {
        if (favourites.isEmpty()) return emptyList()
        val previous = previousOrder.filter { it in favourites }

        if (previousDay == today) {
            val order = previous.toMutableList()
            (favourites - previous.toSet()).sorted().forEach { order.add(random.nextInt(order.size + 1), it) }
            return order
        }

        val base = favourites.sorted()
        if (base.size < 2) return base
        repeat(SHUFFLE_ATTEMPTS) {
            val candidate = base.shuffled(random)
            if (candidate.indices.none { candidate[it] == previous.getOrNull(it) }) return candidate
        }
        // Practically unreachable; rotating by one still moves every item.
        return if (previous.size == base.size) previous.drop(1) + previous.first() else base.shuffled(random)
    }
}
