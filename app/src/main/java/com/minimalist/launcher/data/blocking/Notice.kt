package com.minimalist.launcher.data.blocking

import com.minimalist.launcher.domain.blocking.BlockDecision

/** Something the launcher should put on screen over everything else. */
sealed interface Notice {

    /** An app (or a website, with [label] the domain) was stopped. */
    data class Blocked(
        val packageName: String,
        val label: String,
        val decision: BlockDecision.Blocked,
    ) : Notice

    /** A friction app wants to open: urge, reason, session length, countdown. */
    data class Friction(val packageName: String, val label: String) : Notice

    /** A Settings page that could switch protection off was closed. */
    data object SettingsClosed : Notice

    /** [minutes] of unbroken use of one app: still worth it? */
    data class StillWorthIt(val packageName: String, val label: String, val minutes: Int) : Notice

    /** A friction session just ended: was it worth it? */
    data class WorthIt(val logId: Long, val packageName: String, val label: String) : Notice
}
