package com.minimalist.launcher.data.blocking

import com.minimalist.launcher.system.TrustedClock
import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.domain.blocking.BlockEngine
import com.minimalist.launcher.domain.blocking.TimeScale
import com.minimalist.launcher.domain.modes.ActiveLock
import kotlinx.coroutines.flow.StateFlow
import java.time.ZonedDateTime

/** What, beyond per-app rules, limits the phone right now. */
data class PhoneState(
    val lock: ActiveLock? = null,
    val lockAllowedPackages: Set<String> = emptySet(),
    val otherBrowsers: Set<String> = emptySet(),
    val allowedBrowserLabel: String = "",
)

/** The one place that answers "may this app be open right now?". */
class BlockChecker(
    private val blocking: BlockingRepository,
    private val usage: UsageTracker,
    private val protectedApps: ProtectedApps,
    private val timeScale: StateFlow<TimeScale>,
    private val phoneState: () -> PhoneState,
    private val hasPass: (String) -> Boolean,
    /** Earned time left for an earned-time app, or null for any other app. */
    private val earnedLeft: suspend (String) -> Long? = { null },
) {
    fun isProtected(packageName: String) = packageName in protectedApps.packages

    /** [includePhoneState] false asks about the app's own rules only, ignoring modes and browsers. */
    suspend fun check(packageName: String, atOpen: Boolean, includePhoneState: Boolean = true): BlockDecision {
        if (isProtected(packageName) || hasPass(packageName)) return BlockDecision.Allowed
        val now = TrustedClock.zonedNow()
        val phone = if (includePhoneState) phoneState() else PhoneState()
        return BlockEngine.evaluate(
            packageName = packageName,
            now = now,
            rules = blocking.rules.value,
            usage = usage.usageToday(packageName, now.toInstant().toEpochMilli()),
            protectedPackages = protectedApps.packages,
            scale = timeScale.value,
            atOpen = atOpen,
            lock = phone.lock,
            lockAllowedPackages = phone.lockAllowedPackages,
            otherBrowsers = phone.otherBrowsers,
            allowedBrowserLabel = phone.allowedBrowserLabel,
            earnedLeftMillis = earnedLeft(packageName),
        )
    }
}
