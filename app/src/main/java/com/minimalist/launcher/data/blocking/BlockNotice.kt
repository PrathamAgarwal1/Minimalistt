package com.minimalist.launcher.data.blocking

import com.minimalist.launcher.domain.blocking.BlockDecision

/** Asks the launcher to show the block screen for an app that was just stopped. */
data class BlockNotice(
    val packageName: String,
    val label: String,
    val decision: BlockDecision.Blocked,
)
