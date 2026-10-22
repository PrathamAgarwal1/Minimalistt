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

    /** The blocked part showing, given which of its view ids are on screen. */
    fun showing(packageName: String, blocked: Set<String>, visibleIds: Set<String>): AppPart? =
        ALL.firstOrNull { part ->
            part.packageName == packageName && part.key in blocked && part.viewIds.any { it in visibleIds }
        }

    /** Unblocking a part weakens protection. */
    fun weakens(old: Set<String>, new: Set<String>): Boolean = (old - new).isNotEmpty()
}
