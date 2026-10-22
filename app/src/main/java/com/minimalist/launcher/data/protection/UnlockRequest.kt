package com.minimalist.launcher.data.protection

import com.minimalist.launcher.domain.challenge.ChallengePlan
import com.minimalist.launcher.domain.challenge.NightChallenge

/** Someone asked to get past a block early; the challenge screen takes it from here. */
data class UnlockRequest(
    val target: UnlockTarget,
    val label: String,
    val timeLeftMillis: Long,
    /** Opened once the pass is granted. */
    val openPackage: String?,
    /** Set for night mode: its own curve, a fixed pass, or opening the phone for the morning. */
    val night: NightChallenge? = null,
    val nightOpensAt: Long? = null,
    /** An emergency unlock beyond the free ones: this plan, then everything opens for 15 minutes. */
    val emergencyPlan: ChallengePlan? = null,
)
