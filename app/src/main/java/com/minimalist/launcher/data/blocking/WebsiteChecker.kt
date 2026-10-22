package com.minimalist.launcher.data.blocking

import com.minimalist.launcher.data.db.AppDatabase
import com.minimalist.launcher.domain.blocking.BlockDecision
import com.minimalist.launcher.domain.blocking.BlockReason
import com.minimalist.launcher.domain.websites.AppDomains
import com.minimalist.launcher.domain.websites.DomainMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Blocks a website while the app it belongs to is blocked, and custom domains always. */
class WebsiteChecker(
    database: AppDatabase,
    private val checker: BlockChecker,
    scope: CoroutineScope,
) {
    private val dao = database.domainsDao()

    val customDomains: StateFlow<Set<String>> = dao.observe()
        .map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** [addressBar] is what the browser shows; returns the block for it, if any. */
    suspend fun check(addressBar: String?): BlockDecision.Blocked? {
        val host = DomainMatcher.host(addressBar) ?: return null
        DomainMatcher.match(host, customDomains.value)?.let { domain ->
            return BlockDecision.Blocked(listOf(BlockReason.Website(domain, Long.MAX_VALUE)))
        }
        val candidates = AppDomains.MAP.filterValues { DomainMatcher.match(host, it) != null }.keys
        for (packageName in candidates) {
            val decision = checker.check(packageName, atOpen = false)
            if (decision is BlockDecision.Blocked) {
                val domain = DomainMatcher.match(host, AppDomains.MAP.getValue(packageName)) ?: host
                return BlockDecision.Blocked(listOf(BlockReason.Website(domain, decision.untilMillis)))
            }
        }
        return null
    }

    suspend fun add(input: String): Boolean {
        val domain = DomainMatcher.normalizeDomain(input) ?: return false
        dao.add(domain)
        return true
    }

    suspend fun remove(domain: String) = dao.remove(domain)
}
