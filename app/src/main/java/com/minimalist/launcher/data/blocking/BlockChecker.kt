package com.minimalist.launcher.data.blocking

import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.domain.blocking.BlockEngine
import com.minimalist.launcher.domain.blocking.TimeScale
import kotlinx.coroutines.flow.StateFlow
import java.time.ZonedDateTime

/** The one place that answers "may this app be open right now?". */
class BlockChecker(
    private val blocking: BlockingRepository,
    private val usage: UsageTracker,
    private val protectedApps: ProtectedApps,
    private val timeScale: StateFlow<TimeScale>,
) {
    fun isProtected(packageName: String) = packageName in protectedApps.packages

    suspend fun check(packageName: String, atOpen: Boolean): BlockDecision {
        if (isProtected(packageName)) return BlockDecision.Allowed
        val now = ZonedDateTime.now()
        return BlockEngine.evaluate(
            packageName = packageName,
            now = now,
            rules = blocking.rules.value,
            usage = usage.usageToday(packageName, now.toInstant().toEpochMilli()),
            protectedPackages = protectedApps.packages,
            scale = timeScale.value,
            atOpen = atOpen,
        )
    }
}
