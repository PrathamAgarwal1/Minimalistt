package com.minimalist.launcher.domain.notifications

import java.time.LocalDate
import java.time.ZonedDateTime

enum class Route { Show, HoldBlocked, HoldMode, HoldBatch }

object NotificationRouting {

    /**
     * Calls, SMS, alarms and other protected apps always show. VIP messages break through.
     * Blocked apps are held and saved; during a mode or batching, everything but essentials waits.
     */
    fun route(
        isProtectedApp: Boolean,
        isOngoingOrUrgentCategory: Boolean,
        isVip: Boolean,
        isEssential: Boolean,
        appBlocked: Boolean,
        lockActive: Boolean,
        batching: Boolean,
    ): Route = when {
        isProtectedApp || isOngoingOrUrgentCategory -> Route.Show
        isVip -> Route.Show
        appBlocked -> Route.HoldBlocked
        isEssential -> Route.Show
        lockActive -> Route.HoldMode
        batching -> Route.HoldBatch
        else -> Route.Show
    }
}

object BatchTimes {

    /** True when a batch time has passed since [lastDeliveredMillis]. */
    fun due(lastDeliveredMillis: Long?, now: ZonedDateTime, times: List<Int>): Boolean {
        val today = now.toLocalDate()
        val latest = listOf(today.minusDays(1), today)
            .flatMap { day -> times.map { at(day, it, now) } }
            .filter { !it.isAfter(now) }
            .maxOrNull() ?: return false
        return lastDeliveredMillis == null || lastDeliveredMillis < latest.toInstant().toEpochMilli()
    }

    private fun at(day: LocalDate, minute: Int, zoneOf: ZonedDateTime) = day.atStartOfDay(zoneOf.zone).plusMinutes(minute.toLong())
}

data class Vip(val id: Long, val name: String, val phone: String)

object VipMatcher {

    private val nonDigits = Regex("[^0-9]")

    /** Matches a WhatsApp notification title against VIPs by saved name, or by number for unsaved chats. */
    fun match(title: String?, vips: List<Vip>): Vip? {
        if (title.isNullOrBlank()) return null
        val name = normalize(title)
        vips.firstOrNull { normalize(it.name) == name }?.let { return it }
        val digits = title.replace(nonDigits, "")
        if (digits.length >= 8) {
            vips.firstOrNull { vip ->
                val phone = phoneDigits(vip.phone)
                phone.length >= 8 && (digits.endsWith(phone.takeLast(10)) || phone.endsWith(digits.takeLast(10)))
            }?.let { return it }
        }
        return null
    }

    /** "+91 98765-43210" and "0091…" become "919876543210", as wa.me wants. */
    fun phoneDigits(phone: String): String = phone.replace(nonDigits, "").removePrefix("00")

    private fun normalize(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")
}
