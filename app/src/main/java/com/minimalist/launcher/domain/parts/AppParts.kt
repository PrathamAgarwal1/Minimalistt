package com.minimalist.launcher.domain.parts

/**
 * The endless-feed corners of otherwise useful apps, recognised by the view ids on screen.
 * Blocking a part leaves the rest of the app working.
 */
data class AppPart(
    val key: String,
    val label: String,
    val packageName: String,
    val viewIds: List<String>,
)

object AppParts {

    val ALL = listOf(
        AppPart(
            key = "instagram_reels",
            label = "Instagram Reels",
            packageName = "com.instagram.android",
            viewIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_viewer_container",
                "com.instagram.android:id/clips_video_container",
            ),
        ),
        AppPart(
            key = "youtube_shorts",
            label = "YouTube Shorts",
            packageName = "com.google.android.youtube",
            viewIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_watch_player",
                "com.google.android.youtube:id/reel_progress_bar",
            ),
        ),
    )

    val PACKAGES: Set<String> = ALL.map { it.packageName }.toSet()

    /** The ids that mark [part]: the built-in ones plus any taught since (see [PartLearning]). */
    fun idsFor(part: AppPart, learned: Map<String, Set<String>> = emptyMap()): Set<String> =
        part.viewIds.toSet() + learned[part.key].orEmpty()

    /** The blocked part showing, given which of its view ids are on screen. */
    fun showing(
        packageName: String,
        blocked: Set<String>,
        visibleIds: Set<String>,
        learned: Map<String, Set<String>> = emptyMap(),
    ): AppPart? =
        ALL.firstOrNull { part ->
            part.packageName == packageName && part.key in blocked && idsFor(part, learned).any { it in visibleIds }
        }

    /** Unblocking a part weakens protection. */
    fun weakens(old: Set<String>, new: Set<String>): Boolean = (old - new).isNotEmpty()
}
