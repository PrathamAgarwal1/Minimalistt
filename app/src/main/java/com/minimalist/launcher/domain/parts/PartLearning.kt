package com.minimalist.launcher.domain.parts

/**
 * Teaching Quiet what Reels or Shorts look like after an app update renames its screens: the
 * view ids on screen when you left (the feed) that weren't there when the app opened.
 */
object PartLearning {

    const val MAX_IDS = 6

    /** Only names like these are trusted, so learning can't block an ordinary screen. */
    private val FEED_WORDS = listOf("reel", "clip", "short")

    fun candidates(packageName: String, atOpen: Set<String>, atLeave: Set<String>): List<String> {
        val prefix = "$packageName:id/"
        return (atLeave - atOpen)
            .filter { it.startsWith(prefix) }
            .filter { id -> FEED_WORDS.any { it in id.removePrefix(prefix).lowercase() } }
            .sorted()
            .take(MAX_IDS)
    }
}
