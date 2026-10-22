package com.minimalist.launcher.data.blocking

/**
 * Teaching Quiet a part of an app (see [com.minimalist.launcher.domain.parts.PartLearning]):
 * what was on screen just after the app opened, and what was there when you left it.
 */
data class PartLesson(
    val partKey: String,
    val packageName: String,
    val firstSeenAt: Long? = null,
    val atOpen: Set<String> = emptySet(),
    val atLeave: Set<String> = emptySet(),
    /** Set once you've left the app: the ids kept, empty if none could be told apart. */
    val learned: List<String>? = null,
) {
    companion object {
        /** Everything seen this soon after the app first shows counts as its opening screen. */
        const val OPENING_MILLIS = 2_000L
    }
}
