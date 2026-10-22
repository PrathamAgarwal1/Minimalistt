package com.minimalist.launcher.data.protection

/** Someone asked to get past a block early; the challenge screen takes it from here. */
data class UnlockRequest(
    val target: UnlockTarget,
    val label: String,
    val timeLeftMillis: Long,
    /** Opened once the pass is granted. */
    val openPackage: String?,
)
